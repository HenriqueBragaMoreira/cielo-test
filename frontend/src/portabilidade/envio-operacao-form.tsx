import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Link, useNavigate } from "@tanstack/react-router"
import { useEffect, useRef, type ComponentProps, type ReactNode } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { z } from "zod"

import type { Parcela } from "@/agenda/api"
import { TabelaParcelas } from "@/agenda/tabela-parcelas"
import { CartaoAgenda, PainelResumo, Workspace } from "@/agenda/workspace"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ErroApi } from "@/lib/api"
import { formatarData, formatarMoeda } from "@/lib/formato"

import { enviarOperacao } from "./api"

const schema = z.object({
  parcelaIds: z.array(z.string()).min(1, "Selecione ao menos uma parcela."),
  banco: z.string().regex(/^\d{3}$/, "Informe o banco com 3 dígitos."),
  agencia: z.string().regex(/^\d{4}$/, "Informe a agência com 4 dígitos."),
  conta: z.string().regex(/^\d{1,20}$/, "Informe a conta só com números."),
  digito: z.string().regex(/^\d$/, "Informe o dígito da conta."),
})

type Formulario = z.infer<typeof schema>

/**
 * O workspace da agenda (passo 2): um único formulário, distribuído entre a tabela de parcelas, à esquerda, e o
 * resumo com o total, a conta de recebimento de destino e o envio, à direita.
 */
export function EnvioOperacaoForm({
  beneficiarioId,
  parcelas,
  cabecalho,
}: {
  beneficiarioId: string
  parcelas: Parcela[]
  cabecalho: ReactNode
}) {
  // uma chave por intenção: reutilizada em qualquer reenvio, renovada só quando o pedido muda (ADR-0005)
  const intencao = useRef<{ chave: string; pedido: string } | null>(null)
  const form = useForm<Formulario>({
    resolver: zodResolver(schema),
    defaultValues: {
      parcelaIds: [],
      banco: "",
      agencia: "",
      conta: "",
      digito: "",
    },
  })
  // a agenda atualizada pode tornar inelegível, ou tirar dela, uma parcela já selecionada: ela sai da seleção
  useEffect(() => {
    const selecao = form.getValues("parcelaIds")
    const elegiveis = selecao.filter((id) =>
      parcelas.some((parcela) => parcela.parcelaId === id && parcela.elegivel)
    )
    if (elegiveis.length !== selecao.length) {
      form.setValue("parcelaIds", elegiveis)
    }
  }, [form, parcelas])
  const selecionadas = useWatch({ control: form.control, name: "parcelaIds" })
  const escolhidas = parcelas.filter((parcela) =>
    selecionadas.includes(parcela.parcelaId)
  )
  const total = escolhidas.reduce((soma, parcela) => soma + parcela.valor, 0)
  // `AAAA-MM-DD` ordena como texto
  const datas = escolhidas.map((parcela) => parcela.dataDesconto).sort()
  const convenios = [
    ...new Set(escolhidas.map((parcela) => parcela.convenioId)),
  ].join(", ")
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const envio = useMutation({
    mutationFn: ({ parcelaIds, ...contaDestino }: Formulario) => {
      const pedido = { beneficiarioId, parcelaIds, contaDestino }
      // a mesma seleção em outra ordem é a mesma intenção
      const assinatura = JSON.stringify({
        ...pedido,
        parcelaIds: [...parcelaIds].sort(),
      })
      if (intencao.current?.pedido !== assinatura) {
        intencao.current = { chave: crypto.randomUUID(), pedido: assinatura }
      }
      return enviarOperacao(intencao.current.chave, pedido)
    },
    onError: (erro) => {
      // 422: o pedido não será aceito como está; o próximo envio é uma nova intenção
      if (erro instanceof ErroApi && erro.status === 422) {
        intencao.current = null
      }
      // 409 e 422: a agenda atualizada mostra as parcelas recusadas como inelegíveis
      if (
        erro instanceof ErroApi &&
        (erro.status === 409 || erro.status === 422)
      ) {
        void queryClient.invalidateQueries({
          queryKey: ["agenda", beneficiarioId],
        })
      }
    },
    onSuccess: (operacao) =>
      navigate({
        to: "/operacoes-portabilidade/$operacaoId",
        params: { operacaoId: operacao.id },
      }),
  })
  const erros = form.formState.errors
  const erroEnvio = envio.error instanceof ErroApi ? envio.error : undefined
  // sem resposta ou Central indisponível: reenviar o mesmo pedido, com a mesma chave, pode dar certo (ADR-0005)
  const podeTentarDeNovo = erroEnvio?.status === 0 || erroEnvio?.status === 503

  return (
    <Workspace
      cabecalho={cabecalho}
      agenda={
        <CartaoAgenda>
          {/* régua da agenda (#28): espelha `parcelas` e a seleção `selecionadas` */}
          <Controller
            control={form.control}
            name="parcelaIds"
            render={({ field }) => (
              <TabelaParcelas
                parcelas={parcelas}
                selecionadas={field.value}
                onSelecionadasChange={field.onChange}
              />
            )}
          />
        </CartaoAgenda>
      }
      resumo={
        <PainelResumo>
          <div role="status" className="flex flex-col gap-3">
            <div>
              <p className="text-sm text-muted-foreground">Total a portar</p>
              <p className="text-[2.75rem] leading-tight font-extrabold tracking-[-0.02em] break-words">
                {formatarMoeda(total)}
              </p>
            </div>
            <p className="flex flex-wrap justify-between gap-x-4 gap-y-1 border-t pt-3 text-sm">
              {escolhidas.length === 0 ? (
                <span className="text-muted-foreground">
                  Nenhuma parcela selecionada
                </span>
              ) : (
                <>
                  <span className="font-semibold">
                    {escolhidas.length}{" "}
                    {escolhidas.length === 1 ? "parcela" : "parcelas"} ·{" "}
                    {convenios}
                  </span>
                  <span>
                    {datas[0] === datas.at(-1)
                      ? formatarData(datas[0])
                      : `${formatarData(datas[0])} → ${formatarData(datas.at(-1)!)}`}
                  </span>
                </>
              )}
            </p>
          </div>
          {erros.parcelaIds && (
            <p role="alert" className="-mt-2 text-sm text-destructive">
              {erros.parcelaIds.message}
            </p>
          )}
          <form
            className="flex flex-col gap-5"
            noValidate
            aria-busy={envio.isPending}
            onSubmit={form.handleSubmit((dados) => envio.mutate(dados))}
          >
            <fieldset className="flex flex-col gap-3 border-t pt-4">
              <legend className="contents text-sm font-bold">
                Conta de recebimento de destino
              </legend>
              <div className="grid grid-cols-2 gap-x-3 gap-y-4">
                <Campo
                  id="banco"
                  label="Banco"
                  maxLength={3}
                  erro={erros.banco?.message}
                  {...form.register("banco")}
                />
                <Campo
                  id="agencia"
                  label="Agência"
                  maxLength={4}
                  erro={erros.agencia?.message}
                  {...form.register("agencia")}
                />
                <Campo
                  id="conta"
                  label="Conta"
                  maxLength={20}
                  erro={erros.conta?.message}
                  {...form.register("conta")}
                />
                <Campo
                  id="digito"
                  label="Dígito"
                  maxLength={1}
                  erro={erros.digito?.message}
                  {...form.register("digito")}
                />
              </div>
            </fieldset>
            <div className="flex flex-col gap-2">
              <Button
                type="submit"
                size="lg"
                className="w-full"
                disabled={envio.isPending}
              >
                Enviar portabilidade
              </Button>
              <p className="text-center text-xs text-muted-foreground">
                Reenviar o mesmo pedido não cria outra operação.
              </p>
            </div>
            {envio.error && (
              <div
                role="alert"
                className="flex flex-col items-start gap-3 rounded-md border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive"
              >
                <p>{envio.error.message}</p>
                {(podeTentarDeNovo || erroEnvio?.id) && (
                  <div className="flex flex-wrap items-center gap-2">
                    {podeTentarDeNovo && envio.variables && (
                      <Button
                        type="button"
                        variant="outline"
                        disabled={envio.isPending}
                        onClick={() => envio.mutate(envio.variables)}
                      >
                        Tentar novamente
                      </Button>
                    )}
                    {erroEnvio?.id && (
                      <Link
                        to="/operacoes-portabilidade/$operacaoId"
                        params={{ operacaoId: erroEnvio.id }}
                        className={buttonVariants({ variant: "link" })}
                      >
                        Ver a operação
                      </Link>
                    )}
                  </div>
                )}
              </div>
            )}
          </form>
        </PainelResumo>
      }
    />
  )
}

function Campo({
  id,
  label,
  erro,
  ...props
}: { id: string; label: string; erro?: string } & ComponentProps<
  typeof Input
>) {
  return (
    <div className="flex flex-col gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input
        id={id}
        inputMode="numeric"
        autoComplete="off"
        aria-invalid={!!erro}
        aria-describedby={erro ? `${id}-erro` : undefined}
        {...props}
      />
      {erro && (
        <p id={`${id}-erro`} className="text-xs text-destructive">
          {erro}
        </p>
      )}
    </div>
  )
}
