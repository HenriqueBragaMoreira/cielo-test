import { useQuery } from "@tanstack/react-query"
import { Link, useParams } from "@tanstack/react-router"
import { cn } from "cn"
import { LoaderCircleIcon, TriangleAlertIcon } from "lucide-react"

import { Button, buttonVariants } from "@/components/ui/button"
import { tituloSecao } from "@/lib/estilos"
import { formatarDataHora, formatarMoeda } from "@/lib/formato"
import { LinkVoltar } from "@/shell/link-voltar"

import { consultarOperacao, type Operacao, type TipoEvento } from "./api"
import { estadoDaOperacao } from "./estado-operacao"

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

/** O estado em palavras no painel de situação. */
const tituloDaSituacao: Record<Operacao["status"], string> = {
  PENDENTE: "Aguardando a decisão da Central de Convênios…",
  CONFIRMADA: "Operação confirmada",
  REJEITADA: "Operação rejeitada",
  FALHA_ENVIO: "Falha no envio",
}

/** Passo 3: polling pelo `Retry-After` até o estado final (ADR-0008). */
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
      <div className="flex flex-col gap-4">
        {operacao.data && (
          <LinkVoltar
            to="/beneficiarios/$beneficiarioId/agenda"
            params={{
              beneficiarioId: operacao.data.operacao.beneficiarioId,
            }}
          >
            {`Agenda do ${operacao.data.operacao.beneficiarioId}`}
          </LinkVoltar>
        )}
        <h1
          id="titulo-operacao"
          className="text-[1.75rem] leading-tight font-extrabold tracking-[-0.02em]"
        >
          Operação de portabilidade
        </h1>
      </div>
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

/** A última consulta não trouxe o status atual da Central (ADR-0007). */
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
  const pendente = operacao.status === "PENDENTE"
  const { superficie, tom } = estadoDaOperacao[operacao.status]
  const Icone = pendente
    ? LoaderCircleIcon
    : estadoDaOperacao[operacao.status].Icone
  const titulo = tituloDaSituacao[operacao.status]
  const parcelasLiberadas =
    operacao.status === "REJEITADA" || operacao.status === "FALHA_ENVIO"

  return (
    // estado à esquerda, ações à direita: o painel tem a altura do conteúdo; num cartão estreito as ações descem
    <div
      className={cn(
        "flex flex-wrap items-center justify-between gap-x-8 gap-y-5 rounded-xl p-5 ring-1 sm:px-7 sm:py-6",
        superficie
      )}
    >
      <div
        role="status"
        className="flex min-w-0 flex-1 basis-80 items-start gap-4"
      >
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
      <div className="flex flex-wrap items-center gap-3 sm:pl-14 md:pl-0">
        {pendente ? (
          <>
            {consultandoSozinha && (
              <p className="flex items-center gap-2 text-sm text-muted-foreground">
                <span
                  aria-hidden
                  className="size-2 rounded-full bg-warning motion-safe:animate-pulse"
                />
                Consultando a Central automaticamente
              </p>
            )}
            <Button
              variant="outline"
              disabled={atualizando}
              onClick={onAtualizar}
            >
              Atualizar
            </Button>
          </>
        ) : (
          <>
            {parcelasLiberadas && (
              <Link
                to="/beneficiarios/$beneficiarioId/agenda"
                params={{ beneficiarioId: operacao.beneficiarioId }}
                className={buttonVariants()}
              >
                Voltar à agenda
              </Link>
            )}
            <Link
              to="/"
              className={buttonVariants({
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

/** Na gramática da régua da agenda, mas a distância é a ordem: os eventos acontecem a segundos uns dos outros. */
function LinhaDoTempo({ operacao }: { operacao: Operacao }) {
  const { eventos } = operacao
  if (!eventos.length) return null
  const primeiro = formatarDataHora(eventos[0].dataHora)
  const ultimo = formatarDataHora(eventos.at(-1)!.dataHora)
  // cinco eventos, com o nome e a data e hora inteiros, cabem lado a lado num cartão de 42rem
  const deitada = eventos.length <= 5
  return (
    <section
      aria-labelledby="titulo-linha-do-tempo"
      className="@container rounded-xl bg-card px-6 py-6 ring-1 ring-border sm:px-7"
    >
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id="titulo-linha-do-tempo" className={tituloSecao}>
          Linha do tempo
        </h2>
        <p className="text-xs font-bold text-muted-foreground">
          {primeiro === ultimo ? primeiro : `${primeiro} — ${ultimo}`}
        </p>
      </div>
      <ol
        aria-label="Linha do tempo"
        className={cn(
          "mt-5 ml-2 flex flex-col gap-4 border-l-2 border-foreground py-1",
          deitada &&
            "@2xl:ml-0 @2xl:flex-row @2xl:gap-0 @2xl:border-b-2 @2xl:border-l-0 @2xl:py-0"
        )}
      >
        {eventos.map((evento) => (
          // de pé: ponto, haste e rótulo da esquerda para a direita; deitada: rótulo, haste e ponto de cima para baixo
          <li
            key={`${evento.tipo}-${evento.dataHora}`}
            className={cn(
              "flex flex-row-reverse items-center justify-end",
              deitada &&
                "@2xl:min-w-0 @2xl:flex-1 @2xl:flex-col @2xl:items-start @2xl:gap-1"
            )}
          >
            <div
              className={cn("ml-2 flex flex-col gap-1", deitada && "@2xl:ml-0")}
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
            </div>
            <span
              aria-hidden
              className={cn(
                "h-[3px] w-5 shrink-0 rounded-full",
                deitada && "@2xl:mt-2 @2xl:ml-[7.5px] @2xl:h-8 @2xl:w-[3px]",
                marcaDoEvento[evento.tipo]
              )}
            />
            <span
              aria-hidden
              className={cn(
                // o centro do ponto fica sobre o eixo
                "-ml-[10px] size-[18px] shrink-0 rounded-full border-[3px] border-card",
                deitada && "@2xl:-mb-[10px] @2xl:ml-0",
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
      <h2 id="titulo-resumo-operacao" className={tituloSecao}>
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
      {/* rótulo acima do valor: o protocolo cabe numa linha e o termo do glossário não espreme a coluna */}
      <dl className="flex flex-col gap-3 border-t pt-5 text-sm [&_dd]:font-semibold [&_dt]:text-muted-foreground">
        <div>
          <dt>Beneficiário</dt>
          <dd>{operacao.beneficiarioId}</dd>
        </div>
        <div>
          <dt>Protocolo</dt>
          <dd className="break-all">{operacao.protocolo ?? "—"}</dd>
        </div>
        <div>
          <dt>Conta de recebimento de destino</dt>
          <dd>{`Banco ${banco} · Agência ${agencia} · Conta ${conta}-${digito}`}</dd>
        </div>
      </dl>
    </section>
  )
}
