import { useQuery } from "@tanstack/react-query"
import { Link, useLocation, useParams } from "@tanstack/react-router"

import { ConfirmacaoAutorizacao } from "@/autorizacao/confirmacao-autorizacao"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { ErroApi } from "@/lib/api"
import { formatarData, formatarMoeda } from "@/lib/formato"

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
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Parcela</TableHead>
                  <TableHead>Convênio</TableHead>
                  <TableHead className="text-right">Valor</TableHead>
                  <TableHead>Data de desconto</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Elegível</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {agenda.data.map((parcela) => (
                  <TableRow key={parcela.parcelaId}>
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
        </CardContent>
      </Card>
    </>
  )
}
