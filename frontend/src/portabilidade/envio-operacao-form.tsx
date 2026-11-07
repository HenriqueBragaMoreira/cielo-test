import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Link, useNavigate } from "@tanstack/react-router"
import { useEffect, useRef, type ComponentProps } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { z } from "zod"

import type { Parcela } from "@/agenda/api"
import { Button, buttonVariants } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
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

/** A agenda com a seleção das parcelas, o total e a conta de recebimento de destino (passo 3). */
export function EnvioOperacaoForm({
  beneficiarioId,
  parcelas,
}: {
  beneficiarioId: string
  parcelas: Parcela[]
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
  const total = parcelas
    .filter((parcela) => selecionadas.includes(parcela.parcelaId))
    .reduce((soma, parcela) => soma + parcela.valor, 0)
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
    <form
      className="flex flex-col gap-4"
      noValidate
      onSubmit={form.handleSubmit((dados) => envio.mutate(dados))}
    >
      <Controller
        control={form.control}
        name="parcelaIds"
        render={({ field }) => (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>
                  <span className="sr-only">Selecionar</span>
                </TableHead>
                <TableHead>Parcela</TableHead>
                <TableHead>Convênio</TableHead>
                <TableHead className="text-right">Valor</TableHead>
                <TableHead>Data de desconto</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Elegível</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {parcelas.map((parcela) => (
                <TableRow key={parcela.parcelaId}>
                  <TableCell>
                    <Checkbox
                      aria-label={`Selecionar a parcela ${parcela.parcelaId}`}
                      disabled={!parcela.elegivel}
                      checked={field.value.includes(parcela.parcelaId)}
                      onCheckedChange={(marcada) =>
                        field.onChange(
                          marcada
                            ? [...field.value, parcela.parcelaId]
                            : field.value.filter(
                                (id) => id !== parcela.parcelaId
                              )
                        )
                      }
                    />
                  </TableCell>
                  <TableCell>{parcela.parcelaId}</TableCell>
                  <TableCell>{parcela.convenioId}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatarMoeda(parcela.valor)}
                  </TableCell>
                  <TableCell>{formatarData(parcela.dataDesconto)}</TableCell>
                  <TableCell>{parcela.status}</TableCell>
                  <TableCell className="whitespace-normal">
                    {parcela.elegivel
                      ? "Sim"
                      : `Não: ${parcela.motivoInelegibilidade}`}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      />
      <p className="text-sm font-medium tabular-nums">
        Total selecionado: {formatarMoeda(total)}
      </p>
      {erros.parcelaIds && (
        <p className="text-sm text-destructive">{erros.parcelaIds.message}</p>
      )}
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-2 text-sm font-medium">
          Conta de recebimento de destino
        </legend>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
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
      <Button type="submit" disabled={envio.isPending}>
        Enviar portabilidade
      </Button>
      {envio.error && (
        <div
          role="alert"
          className="flex flex-col items-start gap-3 rounded-lg border border-destructive/50 p-3 text-sm text-destructive"
        >
          <p>{envio.error.message}</p>
          {(podeTentarDeNovo || erroEnvio?.id) && (
            <div className="flex flex-wrap gap-2">
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
