import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation } from "@tanstack/react-query"
import { useNavigate } from "@tanstack/react-router"
import { CircleAlertIcon, Loader2Icon } from "lucide-react"
import { Controller, useForm } from "react-hook-form"
import { z } from "zod"

import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"

import { registrarAutorizacao } from "./api"

const schema = z.object({
  beneficiarioId: z.string().trim().min(1),
  aceite: z.boolean().refine(Boolean),
})

type Formulario = z.input<typeof schema>
type DadosValidados = z.output<typeof schema>

export function AutorizacaoPage() {
  const form = useForm<Formulario, unknown, DadosValidados>({
    resolver: zodResolver(schema),
    mode: "onChange",
    defaultValues: { beneficiarioId: "", aceite: false },
  })
  const navigate = useNavigate()
  const registro = useMutation({
    mutationFn: (dados: DadosValidados) =>
      registrarAutorizacao(dados.beneficiarioId),
    onSuccess: (registroAutorizacao) =>
      navigate({
        to: "/beneficiarios/$beneficiarioId/agenda",
        params: {
          beneficiarioId: registroAutorizacao.autorizacao.beneficiarioId,
        },
        state: { registroAutorizacao },
      }),
  })

  return (
    <section
      aria-labelledby="titulo-autorizacao"
      className="mx-auto flex w-full max-w-md flex-col gap-6 pt-6 sm:pt-14"
    >
      <div className="flex flex-col gap-2">
        <h1
          id="titulo-autorizacao"
          className="text-[1.75rem] leading-tight font-extrabold tracking-[-0.02em] text-balance"
        >
          Autorização do beneficiário
        </h1>
        <p className="text-sm text-muted-foreground">
          Registre a autorização na Central de Convênios para consultar a agenda
          de descontos.
        </p>
      </div>
      <Card>
        <CardContent>
          <form
            aria-busy={registro.isPending}
            className="flex flex-col gap-6 py-1"
            onSubmit={form.handleSubmit((dados) => registro.mutate(dados))}
          >
            <div className="flex flex-col gap-2">
              <Label htmlFor="beneficiarioId">
                Identificador do beneficiário
              </Label>
              <Input
                id="beneficiarioId"
                autoComplete="off"
                spellCheck={false}
                className="h-11"
                {...form.register("beneficiarioId")}
              />
            </div>
            <Controller
              control={form.control}
              name="aceite"
              render={({ field }) => (
                <div className="flex items-start gap-3">
                  <Checkbox
                    id="aceite"
                    checked={field.value}
                    onCheckedChange={field.onChange}
                    onBlur={field.onBlur}
                  />
                  <Label htmlFor="aceite" className="leading-snug font-normal">
                    O beneficiário autoriza a consulta da agenda e a
                    portabilidade das parcelas
                  </Label>
                </div>
              )}
            />
            <Button
              type="submit"
              size="lg"
              disabled={!form.formState.isValid || registro.isPending}
            >
              {registro.isPending && (
                <Loader2Icon aria-hidden className="animate-spin" />
              )}
              Registrar autorização
            </Button>
          </form>
        </CardContent>
      </Card>
      {registro.error && (
        <div
          role="alert"
          className="flex items-start gap-3 rounded-lg bg-destructive/8 p-4 text-sm text-destructive"
        >
          <CircleAlertIcon aria-hidden className="mt-0.5 size-4 shrink-0" />
          <div className="flex flex-col gap-1">
            <p className="font-bold">
              Não foi possível registrar a autorização.
            </p>
            <p>{registro.error.message}</p>
          </div>
        </div>
      )}
    </section>
  )
}
