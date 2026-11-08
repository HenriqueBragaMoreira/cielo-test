import { useQuery } from "@tanstack/react-query"
import { Link, useLocation, useParams } from "@tanstack/react-router"
import { useState } from "react"

import { ConfirmacaoAutorizacao } from "@/autorizacao/confirmacao-autorizacao"
import { Button, buttonVariants } from "@/components/ui/button"
import { ErroApi } from "@/lib/api"
import { EnvioOperacaoForm } from "@/portabilidade/envio-operacao-form"
import { LinkVoltar } from "@/shell/link-voltar"

import { consultarAgenda } from "./api"
import { AgendaVazia, CartaoAgenda, PainelResumo, Workspace } from "./workspace"

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
    <div className="flex flex-col gap-4">
      <LinkVoltar to="/">Trocar beneficiário</LinkVoltar>
      <h1 className="text-[1.75rem] leading-tight tracking-[-0.02em]">
        <span className="font-normal text-muted-foreground">Beneficiário</span>{" "}
        <span className="font-extrabold">{beneficiarioId}</span>
      </h1>
      {registro && <ConfirmacaoAutorizacao registro={registro} />}
    </div>
  )

  // depois que a agenda trouxe parcelas, o formulário fica montado mesmo que uma atualização falhe ou venha vazia
  // (409/422): a conta digitada e a mensagem do envio continuam à vista
  const [beneficiarioComParcelas, setBeneficiarioComParcelas] =
    useState<string>()
  if (agenda.data?.length && beneficiarioComParcelas !== beneficiarioId) {
    setBeneficiarioComParcelas(beneficiarioId)
  }

  const erroAgenda = agenda.error && (
    <div
      role="alert"
      className="mt-4 flex flex-col items-start gap-3 rounded-md border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive"
    >
      <p>{agenda.error.message}</p>
      {agenda.error instanceof ErroApi && agenda.error.status === 403 ? (
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
  )

  if (agenda.data?.length || beneficiarioComParcelas === beneficiarioId) {
    return (
      <EnvioOperacaoForm
        beneficiarioId={beneficiarioId}
        parcelas={agenda.data ?? []}
        cabecalho={cabecalho}
        erroAgenda={erroAgenda}
      />
    )
  }

  return (
    <Workspace
      cabecalho={cabecalho}
      agenda={
        <CartaoAgenda aria-busy={agenda.isPending}>
          {agenda.isPending && <AgendaCarregando />}
          {erroAgenda}
          {agenda.data?.length === 0 && <AgendaVazia />}
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
