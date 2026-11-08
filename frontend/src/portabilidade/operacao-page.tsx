import { useQuery } from "@tanstack/react-query"
import { useParams } from "@tanstack/react-router"
import {
  CircleAlertIcon,
  CircleCheckIcon,
  CircleXIcon,
  LoaderCircleIcon,
  TriangleAlertIcon,
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { formatarDataHora, formatarMoeda } from "@/lib/formato"

import { consultarOperacao, type Operacao, type TipoEvento } from "./api"

const nomeDoEvento: Record<TipoEvento, string> = {
  SOLICITADA: "Solicitada",
  ENVIADA: "Enviada",
  FALHA_ENVIO: "Falha no envio",
  CONFIRMADA: "Confirmada",
  REJEITADA: "Rejeitada",
}

/**
 * Passo 4: a operação com a linha do tempo, acompanhada sozinha até a confirmação ou a rejeição (ADR-0008). O polling
 * segue o `Retry-After` e para no estado final; ao sair da tela, o `AbortSignal` cancela a consulta em andamento.
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
    <Card>
      <CardHeader>
        <CardTitle role="heading" aria-level={2}>
          Operação de portabilidade
        </CardTitle>
      </CardHeader>
      <CardContent aria-busy={operacao.isPending}>
        {operacao.isPending && (
          <p className="text-sm text-muted-foreground">Carregando operação…</p>
        )}
        {operacao.error && (
          <div
            role="alert"
            className="flex flex-col items-start gap-3 rounded-lg border border-destructive/50 p-3 text-sm text-destructive"
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
          <div className="flex flex-col gap-6">
            <AvisoAtualizacao operacao={operacao.data.operacao} />
            <SituacaoOperacao
              operacao={operacao.data.operacao}
              atualizando={operacao.isFetching}
              onAtualizar={() => operacao.refetch()}
            />
            <DetalheOperacao operacao={operacao.data.operacao} />
          </div>
        )}
      </CardContent>
    </Card>
  )
}

/** A última consulta não trouxe o status atual da Central; o polling segue o `Retry-After`, ausente se o protocolo não é mais reconhecido. */
function AvisoAtualizacao({ operacao }: { operacao: Operacao }) {
  if (!operacao.avisoAtualizacao) return null
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-lg border border-amber-600/50 p-3 text-sm text-amber-700"
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

/** A espera pela Central, com o "Atualizar" sob demanda, ou a decisão dela. */
function SituacaoOperacao({
  operacao,
  atualizando,
  onAtualizar,
}: {
  operacao: Operacao
  atualizando: boolean
  onAtualizar: () => void
}) {
  switch (operacao.status) {
    case "PENDENTE":
      return (
        <div
          role="status"
          className="flex items-center justify-between gap-3 rounded-lg border p-3 text-sm"
        >
          <span className="flex items-center gap-2">
            <LoaderCircleIcon aria-hidden className="size-4 animate-spin" />
            Aguardando a decisão da Central de Convênios…
          </span>
          <Button
            variant="outline"
            disabled={atualizando}
            onClick={onAtualizar}
          >
            Atualizar
          </Button>
        </div>
      )
    case "CONFIRMADA":
      return (
        <div
          role="status"
          className="flex items-start gap-2 rounded-lg border border-emerald-600/50 p-3 text-sm text-emerald-700"
        >
          <CircleCheckIcon aria-hidden className="mt-0.5 size-4 shrink-0" />
          <div>
            <p className="font-medium">Operação confirmada</p>
            <p>As parcelas passaram para a nova credora.</p>
          </div>
        </div>
      )
    case "REJEITADA":
      return (
        <div
          role="status"
          className="flex items-start gap-2 rounded-lg border border-destructive/50 p-3 text-sm text-destructive"
        >
          <CircleXIcon aria-hidden className="mt-0.5 size-4 shrink-0" />
          <div>
            <p className="font-medium">Operação rejeitada</p>
            {operacao.motivoRejeicao && (
              <p>{`Motivo: ${operacao.motivoRejeicao}`}</p>
            )}
          </div>
        </div>
      )
    case "FALHA_ENVIO":
      return (
        <div
          role="status"
          className="flex items-start gap-2 rounded-lg border border-destructive/50 p-3 text-sm text-destructive"
        >
          <CircleAlertIcon aria-hidden className="mt-0.5 size-4 shrink-0" />
          <div>
            <p className="font-medium">Falha no envio</p>
            <p>
              A Central de Convênios não respondeu e a operação não foi enviada.
              As parcelas ficaram liberadas.
            </p>
          </div>
        </div>
      )
  }
}

function DetalheOperacao({ operacao }: { operacao: Operacao }) {
  const { banco, agencia, conta, digito } = operacao.contaDestino
  const total = operacao.parcelas.reduce((soma, p) => soma + p.valor, 0)
  return (
    <>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-muted-foreground">Status</dt>
        <dd className="font-medium">{operacao.status}</dd>
        <dt className="text-muted-foreground">Protocolo</dt>
        <dd>{operacao.protocolo ?? "—"}</dd>
        <dt className="text-muted-foreground">Beneficiário</dt>
        <dd>{operacao.beneficiarioId}</dd>
        <dt className="text-muted-foreground">Conta de destino</dt>
        <dd>{`Banco ${banco} · Agência ${agencia} · Conta ${conta}-${digito}`}</dd>
      </dl>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Parcela</TableHead>
            <TableHead className="text-right">Valor</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {operacao.parcelas.map((parcela) => (
            <TableRow key={parcela.parcelaId}>
              <TableCell>{parcela.parcelaId}</TableCell>
              <TableCell className="text-right tabular-nums">
                {formatarMoeda(parcela.valor)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
        <TableFooter>
          <TableRow>
            <TableCell>Total</TableCell>
            <TableCell className="text-right tabular-nums">
              {formatarMoeda(total)}
            </TableCell>
          </TableRow>
        </TableFooter>
      </Table>
      <section className="flex flex-col gap-2">
        <h3 className="text-sm font-medium">Linha do tempo</h3>
        <ol aria-label="Linha do tempo" className="flex flex-col gap-2 text-sm">
          {operacao.eventos.map((evento) => (
            <li
              key={`${evento.tipo}-${evento.dataHora}`}
              className="flex justify-between gap-4 border-l-2 pl-3"
            >
              <span>{nomeDoEvento[evento.tipo]}</span>
              <time
                dateTime={evento.dataHora}
                className="text-muted-foreground tabular-nums"
              >
                {formatarDataHora(evento.dataHora)}
              </time>
            </li>
          ))}
        </ol>
      </section>
    </>
  )
}
