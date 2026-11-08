import { useQuery } from "@tanstack/react-query"
import { Link, useLocation, useParams } from "@tanstack/react-router"
import { CalendarX2Icon } from "lucide-react"

import { ConfirmacaoAutorizacao } from "@/autorizacao/confirmacao-autorizacao"
import { Button, buttonVariants } from "@/components/ui/button"
import { ErroApi } from "@/lib/api"
import { EnvioOperacaoForm } from "@/portabilidade/envio-operacao-form"

import { consultarAgenda } from "./api"
import { CartaoAgenda, PainelResumo, Workspace } from "./workspace"

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

  const cabecalho = (
    <div className="flex flex-wrap items-baseline gap-x-6 gap-y-2">
      <h1 className="flex items-baseline gap-3">
        <span className="text-sm font-semibold text-muted-foreground">
          Beneficiário
        </span>{" "}
        <span className="text-[1.75rem] leading-tight font-extrabold tracking-[-0.02em]">
          {beneficiarioId}
        </span>
      </h1>
      {registro && <ConfirmacaoAutorizacao registro={registro} />}
    </div>
  )

  if (agenda.data?.length) {
    return (
      <EnvioOperacaoForm
        beneficiarioId={beneficiarioId}
        parcelas={agenda.data}
        cabecalho={cabecalho}
      />
    )
  }

  return (
    <Workspace
      cabecalho={cabecalho}
      agenda={
        <CartaoAgenda aria-busy={agenda.isPending}>
          {agenda.isPending && <AgendaCarregando />}
          {agenda.error && (
            <div
              role="alert"
              className="mt-4 flex flex-col items-start gap-3 rounded-md border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive"
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
            <div className="flex flex-col items-center gap-3 py-14 text-center">
              <CalendarX2Icon
                aria-hidden
                className="size-7 text-muted-foreground"
                strokeWidth={1.75}
              />
              <p className="font-semibold">
                O beneficiário não tem parcelas na agenda.
              </p>
            </div>
          )}
        </CartaoAgenda>
      }
      resumo={agenda.isPending && <ResumoCarregando />}
    />
  )
}

const barra = "rounded-sm bg-muted motion-safe:animate-pulse"

/** Linhas no formato da tabela, para a tela não "pular" quando a agenda chega. */
function AgendaCarregando() {
  return (
    <>
      <p className="mt-4 text-sm text-muted-foreground">Carregando agenda…</p>
      <div aria-hidden className="mt-4 flex flex-col">
        {[0, 1, 2].map((linha) => (
          <div key={linha} className="flex h-12 items-center gap-5 border-b">
            <div className={`${barra} size-5`} />
            <div className={`${barra} h-3 w-14`} />
            <div className={`${barra} h-3 w-16`} />
            <div className={`${barra} h-3 w-20`} />
            <div className={`${barra} ml-auto h-3 w-20`} />
            <div className={`${barra} h-3 w-24`} />
          </div>
        ))}
      </div>
    </>
  )
}

function ResumoCarregando() {
  return (
    <PainelResumo aria-busy>
      <div aria-hidden className="flex flex-col gap-5">
        <div className={`${barra} h-11 w-48`} />
        <div className={`${barra} h-3 w-full`} />
        <div className="grid grid-cols-2 gap-3">
          {[0, 1, 2, 3].map((campo) => (
            <div key={campo} className={`${barra} h-10`} />
          ))}
        </div>
        <div className={`${barra} h-12 rounded-full`} />
      </div>
    </PainelResumo>
  )
}
