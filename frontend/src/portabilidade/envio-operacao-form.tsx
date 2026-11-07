import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation } from "@tanstack/react-query"
import { useNavigate } from "@tanstack/react-router"
import { useState, type ComponentProps } from "react"
import { Controller, useForm, useWatch } from "react-hook-form"
import { z } from "zod"

import type { Parcela } from "@/agenda/api"
import { Button } from "@/components/ui/button"
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
  // gerada ao abrir o formulário (ADR-0005)
  const [idempotencyKey] = useState(() => crypto.randomUUID())
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
  const selecionadas = useWatch({ control: form.control, name: "parcelaIds" })
  const total = parcelas
    .filter((parcela) => selecionadas.includes(parcela.parcelaId))
    .reduce((soma, parcela) => soma + parcela.valor, 0)
  const navigate = useNavigate()
  const envio = useMutation({
    mutationFn: ({ parcelaIds, ...contaDestino }: Formulario) =>
      enviarOperacao(idempotencyKey, {
        beneficiarioId,
        parcelaIds,
        contaDestino,
      }),
    onSuccess: (operacao) =>
      navigate({
        to: "/operacoes-portabilidade/$operacaoId",
        params: { operacaoId: operacao.id },
      }),
  })
  const erros = form.formState.errors

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
        <p
          role="alert"
          className="rounded-lg border border-destructive/50 p-3 text-sm text-destructive"
        >
          {envio.error.message}
        </p>
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
