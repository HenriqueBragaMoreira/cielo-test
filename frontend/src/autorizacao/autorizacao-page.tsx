import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation } from "@tanstack/react-query"
import { useNavigate } from "@tanstack/react-router"
import { Controller, useForm } from "react-hook-form"
import { z } from "zod"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
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
    <Card>
      <CardHeader>
        <CardTitle>Autorização do beneficiário</CardTitle>
      </CardHeader>
      <CardContent>
        <form
          className="flex flex-col gap-4"
          onSubmit={form.handleSubmit((dados) => registro.mutate(dados))}
        >
          <div className="flex flex-col gap-2">
            <Label htmlFor="beneficiarioId">
              Identificador do beneficiário
            </Label>
            <Input
              id="beneficiarioId"
              autoComplete="off"
              {...form.register("beneficiarioId")}
            />
          </div>
          <Controller
            control={form.control}
            name="aceite"
            render={({ field }) => (
              <div className="flex items-center gap-2">
                <Checkbox
                  id="aceite"
                  checked={field.value}
                  onCheckedChange={field.onChange}
                  onBlur={field.onBlur}
                />
                <Label htmlFor="aceite">
                  O beneficiário autoriza a consulta da agenda e a portabilidade
                  das parcelas
                </Label>
              </div>
            )}
          />
          <Button
            type="submit"
            disabled={!form.formState.isValid || registro.isPending}
          >
            Registrar autorização
          </Button>
        </form>
        {registro.error && (
          <p
            role="alert"
            className="mt-4 rounded-lg border border-destructive/50 p-3 text-sm text-destructive"
          >
            {registro.error.message}
          </p>
        )}
      </CardContent>
    </Card>
  )
}
