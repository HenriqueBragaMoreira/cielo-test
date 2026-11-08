import { useQuery } from "@tanstack/react-query"
import { Link, useParams } from "@tanstack/react-router"
import { cn } from "cn"
import {
  CircleAlertIcon,
  CircleCheckIcon,
  CircleXIcon,
  LoaderCircleIcon,
  TriangleAlertIcon,
  type LucideIcon,
} from "lucide-react"

import { Button, buttonVariants } from "@/components/ui/button"
import { formatarDataHora, formatarMoeda } from "@/lib/formato"

import { consultarOperacao, type Operacao, type TipoEvento } from "./api"

const nomeDoEvento: Record<TipoEvento, string> = {
  SOLICITADA: "Solicitada",
  ENVIADA: "Enviada",
  FALHA_ENVIO: "Falha no envio",
  CONFIRMADA: "Confirmada",
  REJEITADA: "Rejeitada",
}

/** Cor de estado da marca de cada evento na régua; os eventos de passagem ficam na tinta. */
const marcaDoEvento: Record<TipoEvento, string> = {
  SOLICITADA: "bg-foreground",
  ENVIADA: "bg-foreground",
  FALHA_ENVIO: "bg-destructive",
  CONFIRMADA: "bg-success",
  REJEITADA: "bg-destructive",
}

type Status = Operacao["status"]

/** Palavras, ícone e cor de cada estado: o estado nunca fica só na cor. */
const situacao: Record<
  Status,
  { titulo: string; Icone: LucideIcon; superficie: string; tom: string }
> = {
  PENDENTE: {
    titulo: "Aguardando a decisão da Central de Convênios…",
    Icone: LoaderCircleIcon,
    superficie: "bg-accent ring-primary/25",
    tom: "text-primary",
  },
  CONFIRMADA: {
    titulo: "Operação confirmada",
    Icone: CircleCheckIcon,
    superficie:
      "bg-[color-mix(in_srgb,var(--success)_7%,white)] ring-success/30",
    tom: "text-success",
  },
  REJEITADA: {
    titulo: "Operação rejeitada",
    Icone: CircleXIcon,
    superficie:
      "bg-[color-mix(in_srgb,var(--destructive)_5%,white)] ring-destructive/30",
    tom: "text-destructive",
  },
  FALHA_ENVIO: {
    titulo: "Falha no envio",
    Icone: CircleAlertIcon,
    superficie:
      "bg-[color-mix(in_srgb,var(--destructive)_5%,white)] ring-destructive/30",
    tom: "text-destructive",
  },
}

/**
 * Passo 3: a operação acompanhada sozinha até a confirmação ou a rejeição (ADR-0008). O polling segue o
 * `Retry-After` e para no estado final; ao sair da tela, o `AbortSignal` cancela a consulta em andamento.
 */
export function OperacaoPage() {
  const { operacaoId } = useParams({
    from: "/operacoes-portabilidade/$operacaoId",
  })
  const operacao = useQuery({
    queryKey: ["operacao", operacaoId],
    queryFn: ({ signal }) => consultarOperacao(operacaoId, signal),
    refetchInterval: (query) => query.state.data?.repetirEmMs ?? false,
    retry: false,
  })

  return (
    <section
      aria-labelledby="titulo-operacao"
      aria-busy={operacao.isPending}
      className="flex flex-col gap-6"
    >
      <h1
        id="titulo-operacao"
        className="text-[1.75rem] leading-tight font-extrabold tracking-[-0.02em]"
      >
        Operação de portabilidade
      </h1>
      {operacao.isPending && (
        <p className="text-sm text-muted-foreground">Carregando operação…</p>
      )}
      {operacao.error && (
        <div
          role="alert"
          className="flex flex-col items-start gap-3 rounded-xl bg-card p-5 text-sm text-destructive ring-1 ring-destructive/30"
        >
          <p>{operacao.error.message}</p>
          <Button
            variant="outline"
            disabled={operacao.isFetching}
            onClick={() => operacao.refetch()}
          >
            Tentar novamente
          </Button>
        </div>
      )}
      {operacao.data && (
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_400px]">
          <div className="flex min-w-0 flex-col gap-6">
            <AvisoAtualizacao operacao={operacao.data.operacao} />
            <PainelSituacao
              operacao={operacao.data.operacao}
              consultandoSozinha={operacao.data.repetirEmMs !== false}
              atualizando={operacao.isFetching}
              onAtualizar={() => operacao.refetch()}
            />
            <LinhaDoTempo operacao={operacao.data.operacao} />
          </div>
          <DetalheOperacao operacao={operacao.data.operacao} />
        </div>
      )}
    </section>
  )
}

/** A última consulta não trouxe o status atual da Central; o polling segue o `Retry-After`, ausente se o protocolo não é mais reconhecido. */
function AvisoAtualizacao({ operacao }: { operacao: Operacao }) {
  if (!operacao.avisoAtualizacao) return null
  return (
    <div
      role="alert"
      className="flex items-start gap-3 rounded-xl bg-[color-mix(in_srgb,var(--warning)_7%,white)] p-4 text-sm text-warning ring-1 ring-warning/30"
    >
      <TriangleAlertIcon aria-hidden className="mt-0.5 size-4 shrink-0" />
      <p>
        {operacao.avisoAtualizacao === "FALHA_NA_ATUALIZACAO"
          ? "Não foi possível atualizar o status da operação; tentando novamente."
          : `A Central de Convênios não reconhece mais o protocolo ${operacao.protocolo}. A operação continua pendente, mas o status pode não ser atualizado.`}
      </p>
    </div>
  )
}

/**
 * O elemento dominante da tela: o estado em palavras, ícone e cor, e o que o operador pode fazer agora. O `status`
 * é o mesmo nó em todos os estados, para o leitor de tela anunciar a decisão quando ela chega.
 */
function PainelSituacao({
  operacao,
  consultandoSozinha,
  atualizando,
  onAtualizar,
}: {
  operacao: Operacao
  consultandoSozinha: boolean
  atualizando: boolean
  onAtualizar: () => void
}) {
  const { titulo, Icone, superficie, tom } = situacao[operacao.status]
  const pendente = operacao.status === "PENDENTE"
  const parcelasLiberadas =
    operacao.status === "REJEITADA" || operacao.status === "FALHA_ENVIO"

  return (
    <div className={cn("rounded-xl p-6 ring-1 sm:p-8", superficie)}>
      <div role="status" className="flex items-start gap-4">
        <Icone
          aria-hidden
          className={cn(
            "size-9 shrink-0 sm:size-10",
            tom,
            pendente && "motion-safe:animate-spin"
          )}
        />
        <div className="flex min-w-0 flex-col gap-2">
          <p
            className={cn(
              "text-2xl leading-tight font-extrabold tracking-[-0.02em] text-balance sm:text-[2rem]",
              tom
            )}
          >
            {titulo}
          </p>
          {operacao.status === "CONFIRMADA" && (
            <p className="text-base">
              As parcelas passaram para a nova credora.
            </p>
          )}
          {operacao.status === "REJEITADA" && operacao.motivoRejeicao && (
            <p className="text-base">
              <span className="text-muted-foreground">Motivo: </span>
              <span className="text-lg font-bold">
                {operacao.motivoRejeicao}
              </span>
            </p>
          )}
          {operacao.status === "FALHA_ENVIO" && (
            <p className="text-base">
              A Central de Convênios não respondeu e a operação não foi enviada.{" "}
              <strong className="font-bold">
                As parcelas ficaram liberadas.
              </strong>
            </p>
          )}
        </div>
      </div>
      <div className="mt-6 flex flex-wrap items-center gap-x-5 gap-y-3 sm:pl-14">
        {pendente ? (
          <>
            <Button
              variant="outline"
              disabled={atualizando}
              onClick={onAtualizar}
            >
              Atualizar
            </Button>
            {consultandoSozinha && (
              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                <span
                  aria-hidden
                  className="size-2 rounded-full bg-primary motion-safe:animate-pulse"
                />
                Consultando a Central automaticamente
              </p>
            )}
          </>
        ) : (
          <>
            {parcelasLiberadas && (
              <Link
                to="/beneficiarios/$beneficiarioId/agenda"
                params={{ beneficiarioId: operacao.beneficiarioId }}
                className={buttonVariants({ size: "lg" })}
              >
                Voltar à agenda
              </Link>
            )}
            <Link
              to="/"
              className={buttonVariants({
                size: "lg",
                variant: parcelasLiberadas ? "outline" : "default",
              })}
            >
              Nova portabilidade
            </Link>
          </>
        )}
      </div>
    </div>
  )
}

/**
 * Os eventos na gramática da régua da agenda: um eixo, uma marca por evento com o nome e a data e hora. Os eventos
 * de uma operação acontecem a segundos uns dos outros, então aqui a distância é a ordem, não a duração.
 */
function LinhaDoTempo({ operacao }: { operacao: Operacao }) {
  const primeiro = formatarDataHora(operacao.eventos[0].dataHora)
  const ultimo = formatarDataHora(operacao.eventos.at(-1)!.dataHora)
  return (
    <section
      aria-labelledby="titulo-linha-do-tempo"
      className="rounded-xl bg-card px-6 py-6 ring-1 ring-border sm:px-7"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 text-xs font-bold text-muted-foreground">
        <h2 id="titulo-linha-do-tempo" className="tracking-[0.08em] uppercase">
          Linha do tempo
        </h2>
        <p>{primeiro === ultimo ? primeiro : `${primeiro} — ${ultimo}`}</p>
      </div>
      <ol
        aria-label="Linha do tempo"
        className="mt-5 flex border-b-2 border-foreground"
      >
        {operacao.eventos.map((evento) => (
          <li
            key={`${evento.tipo}-${evento.dataHora}`}
            className="flex min-w-0 flex-1 flex-col items-start justify-end gap-1"
          >
            <span className="text-[0.9375rem] font-extrabold">
              {nomeDoEvento[evento.tipo]}
            </span>
            <time
              dateTime={evento.dataHora}
              className="text-xs text-muted-foreground"
            >
              {formatarDataHora(evento.dataHora)}
            </time>
            <span
              aria-hidden
              className={cn(
                "mt-2 ml-[7.5px] h-8 w-[3px] rounded-full",
                marcaDoEvento[evento.tipo]
              )}
            />
            <span
              aria-hidden
              className={cn(
                "-mb-[10px] size-[18px] rounded-full border-[3px] border-card",
                marcaDoEvento[evento.tipo]
              )}
            />
          </li>
        ))}
      </ol>
    </section>
  )
}

/** O pedido como foi feito, sem repetir o estado, que o painel já mostra. */
function DetalheOperacao({ operacao }: { operacao: Operacao }) {
  const { banco, agencia, conta, digito } = operacao.contaDestino
  const total = operacao.parcelas.reduce((soma, p) => soma + p.valor, 0)
  return (
    <section
      aria-labelledby="titulo-resumo-operacao"
      className="flex flex-col gap-5 rounded-xl bg-card p-7 ring-1 ring-border"
    >
      <h2
        id="titulo-resumo-operacao"
        className="text-xs font-bold tracking-[0.08em] text-muted-foreground uppercase"
      >
        Resumo da operação
      </h2>
      <div>
        <p className="text-sm text-muted-foreground">Total da operação</p>
        <p className="text-[2.75rem] leading-tight font-extrabold tracking-[-0.02em]">
          {formatarMoeda(total)}
        </p>
      </div>
      <ul aria-label="Parcelas" className="flex flex-col text-sm">
        {operacao.parcelas.map((parcela) => (
          <li
            key={parcela.parcelaId}
            className="flex justify-between gap-4 border-t border-muted py-2.5"
          >
            <span>{parcela.parcelaId}</span>
            <span className="font-semibold">
              {formatarMoeda(parcela.valor)}
            </span>
          </li>
        ))}
      </ul>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2.5 border-t pt-5 text-sm">
        <dt className="text-muted-foreground">Protocolo</dt>
        <dd className="font-semibold break-all">{operacao.protocolo ?? "—"}</dd>
        <dt className="text-muted-foreground">Beneficiário</dt>
        <dd className="font-semibold">{operacao.beneficiarioId}</dd>
        <dt className="text-muted-foreground">Conta de destino</dt>
        <dd className="font-semibold">{`Banco ${banco} · Agência ${agencia} · Conta ${conta}-${digito}`}</dd>
      </dl>
    </section>
  )
}
