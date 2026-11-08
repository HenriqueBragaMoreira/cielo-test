import { useQuery } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { cn } from "cn"

import { Button, buttonVariants } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { soNaTelaLarga } from "@/lib/estilos"
import { formatarDataHora, formatarMoeda } from "@/lib/formato"
import { estadoDaOperacao } from "@/portabilidade/estado-operacao"

import { listarOperacoes, type ResumoOperacao } from "./api"

/** Histórico de operações (ADR-0013): uma consulta só, sem polling; quem atualiza uma pendente é a tela dela. */
export function HistoricoPage() {
  const historico = useQuery({
    queryKey: ["historico"],
    queryFn: listarOperacoes,
    retry: false,
  })

  return (
    <section
      aria-labelledby="titulo-historico"
      className="flex w-full max-w-4xl flex-col gap-6"
    >
      <div className="flex flex-col gap-2">
        <h1
          id="titulo-historico"
          className="text-[1.75rem] leading-tight font-extrabold tracking-[-0.02em] text-balance"
        >
          Histórico de operações
        </h1>
        <p className="text-sm text-muted-foreground">
          As 50 operações mais recentes, da mais nova para a mais antiga. As
          pendentes se atualizam quando você abre a operação.
        </p>
      </div>
      <Card className="py-0" aria-busy={historico.isPending}>
        {historico.error ? (
          <div
            role="alert"
            className="flex flex-col items-start gap-3 p-5 text-sm text-destructive"
          >
            <p>{historico.error.message}</p>
            <Button
              variant="outline"
              disabled={historico.isFetching}
              onClick={() => historico.refetch()}
            >
              Tentar novamente
            </Button>
          </div>
        ) : historico.data?.length === 0 ? (
          <div className="flex flex-col items-start gap-1 p-5">
            <p className="font-bold">Nenhuma operação registrada ainda.</p>
            <p className="text-muted-foreground">
              As operações aparecem aqui assim que são registradas.
            </p>
            <Link to="/" className={cn(buttonVariants(), "mt-3")}>
              Nova portabilidade
            </Link>
          </div>
        ) : (
          <TabelaHistorico operacoes={historico.data} />
        )}
      </Card>
    </section>
  )
}

/** Sem `operacoes`, as linhas são o esqueleto do carregamento, no formato da tabela final. */
function TabelaHistorico({ operacoes }: { operacoes?: ResumoOperacao[] }) {
  const celula = "h-12 px-4 first:pl-5 last:pr-5"
  const cabecalho = cn(celula, "text-xs font-bold text-muted-foreground")
  return (
    <Table>
      {!operacoes && (
        <caption className="sr-only">Carregando histórico…</caption>
      )}
      <TableHeader className="bg-muted/60">
        <TableRow className="hover:bg-transparent">
          <TableHead className={cabecalho}>Criada em</TableHead>
          <TableHead className={cabecalho}>Beneficiário</TableHead>
          <TableHead className={cn(cabecalho, soNaTelaLarga, "text-right")}>
            Parcelas
          </TableHead>
          <TableHead className={cn(cabecalho, soNaTelaLarga, "text-right")}>
            Total
          </TableHead>
          <TableHead className={cabecalho}>Status</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {operacoes
          ? operacoes.map((operacao) => (
              <LinhaOperacao
                key={operacao.id}
                operacao={operacao}
                celula={celula}
              />
            ))
          : [0, 1, 2].map((i) => (
              <TableRow key={i} aria-hidden className="hover:bg-transparent">
                {[36, 20, 8, 24, 24].map((largura, coluna) => (
                  <TableCell
                    key={coluna}
                    className={cn(
                      celula,
                      (coluna === 2 || coluna === 3) && soNaTelaLarga
                    )}
                  >
                    <span
                      className="block h-3 animate-pulse rounded-full bg-muted"
                      style={{ width: `${largura * 0.25}rem` }}
                    />
                  </TableCell>
                ))}
              </TableRow>
            ))}
      </TableBody>
    </Table>
  )
}

function LinhaOperacao({
  operacao,
  celula,
}: {
  operacao: ResumoOperacao
  celula: string
}) {
  const { rotulo, Icone, tom, veu } = estadoDaOperacao[operacao.status]
  return (
    // a linha inteira leva à operação: o link da data se estende sobre ela
    <TableRow className="relative has-[a:focus-visible]:bg-muted/50">
      <TableCell
        className={cn(celula, "max-sm:py-2.5 max-sm:whitespace-normal")}
      >
        <Link
          to="/operacoes-portabilidade/$operacaoId"
          params={{ operacaoId: operacao.id }}
          className="font-bold text-foreground after:absolute after:inset-0 hover:text-primary"
        >
          {formatarDataHora(operacao.criadaEm)}
        </Link>
      </TableCell>
      <TableCell className={celula}>{operacao.beneficiarioId}</TableCell>
      <TableCell className={cn(celula, soNaTelaLarga, "text-right")}>
        {operacao.quantidadeParcelas}
      </TableCell>
      <TableCell className={cn(celula, soNaTelaLarga, "text-right font-bold")}>
        {formatarMoeda(operacao.valorTotal)}
      </TableCell>
      <TableCell className={celula}>
        <span
          className={cn(
            "inline-flex items-center gap-1.5 rounded-full py-0.5 pr-2.5 pl-2 text-xs font-bold",
            veu,
            tom
          )}
        >
          <Icone aria-hidden className="size-3.5" />
          {rotulo}
        </span>
      </TableCell>
    </TableRow>
  )
}
