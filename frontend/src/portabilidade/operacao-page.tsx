import { useQuery } from "@tanstack/react-query"
import { useParams } from "@tanstack/react-router"

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

/** Passo 4: a operação como está gravada, com a linha do tempo. */
export function OperacaoPage() {
  const { operacaoId } = useParams({
    from: "/operacoes-portabilidade/$operacaoId",
  })
  const operacao = useQuery({
    queryKey: ["operacao", operacaoId],
    queryFn: () => consultarOperacao(operacaoId),
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
        {operacao.data && <DetalheOperacao operacao={operacao.data} />}
      </CardContent>
    </Card>
  )
}

function DetalheOperacao({ operacao }: { operacao: Operacao }) {
  const { banco, agencia, conta, digito } = operacao.contaDestino
  const total = operacao.parcelas.reduce((soma, p) => soma + p.valor, 0)
  return (
    <div className="flex flex-col gap-6">
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
    </div>
  )
}
