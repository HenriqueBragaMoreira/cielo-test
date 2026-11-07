import { useQuery } from "@tanstack/react-query"
import { Link, useLocation, useParams } from "@tanstack/react-router"

import { ConfirmacaoAutorizacao } from "@/autorizacao/confirmacao-autorizacao"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ErroApi } from "@/lib/api"
import { EnvioOperacaoForm } from "@/portabilidade/envio-operacao-form"

import { consultarAgenda } from "./api"

export function AgendaPage() {
  const { beneficiarioId } = useParams({
    from: "/beneficiarios/$beneficiarioId/agenda",
  })
  // presente só quando se chega aqui logo depois de registrar a autorização
  const registro = useLocation({
    select: (local) => local.state.registroAutorizacao,
  })
  const agenda = useQuery({
    queryKey: ["agenda", beneficiarioId],
    queryFn: () => consultarAgenda(beneficiarioId),
    // sem retentativa automática: 403 não muda sozinho, e o 503 tem o botão de tentar novamente
    retry: false,
  })

  return (
    <>
      {registro && <ConfirmacaoAutorizacao registro={registro} />}
      <Card>
        <CardHeader>
          <CardTitle role="heading" aria-level={2}>
            Agenda do beneficiário {beneficiarioId}
          </CardTitle>
        </CardHeader>
        <CardContent aria-busy={agenda.isPending}>
          {agenda.isPending && (
            <p className="text-sm text-muted-foreground">Carregando agenda…</p>
          )}
          {agenda.error && (
            <div
              role="alert"
              className="flex flex-col items-start gap-3 rounded-lg border border-destructive/50 p-3 text-sm text-destructive"
            >
              <p>{agenda.error.message}</p>
              {agenda.error instanceof ErroApi &&
              agenda.error.status === 403 ? (
                <Link to="/" className={buttonVariants()}>
                  Registrar a autorização
                </Link>
              ) : (
                <Button
                  variant="outline"
                  disabled={agenda.isFetching}
                  onClick={() => agenda.refetch()}
                >
                  Tentar novamente
                </Button>
              )}
            </div>
          )}
          {agenda.data?.length === 0 && (
            <p className="text-sm text-muted-foreground">
              O beneficiário não tem parcelas na agenda.
            </p>
          )}
          {!!agenda.data?.length && (
            <EnvioOperacaoForm
              beneficiarioId={beneficiarioId}
              parcelas={agenda.data}
            />
          )}
        </CardContent>
      </Card>
    </>
  )
}
