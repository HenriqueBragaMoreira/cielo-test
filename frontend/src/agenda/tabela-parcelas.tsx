import { cn } from "cn"

import { Checkbox } from "@/components/ui/checkbox"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { formatarData, formatarMoeda } from "@/lib/formato"

import type { Parcela } from "./api"

const cabecalho = "h-9 text-xs font-bold text-muted-foreground"

/** A tabela é o controle acessível da seleção; a régua da agenda só a espelha. */
export function TabelaParcelas({
  parcelas,
  selecionadas,
  onSelecionadasChange,
}: {
  parcelas: Parcela[]
  selecionadas: string[]
  onSelecionadasChange: (selecionadas: string[]) => void
}) {
  return (
    <Table className="mt-4">
      <TableHeader>
        <TableRow className="hover:bg-transparent">
          <TableHead className={cn(cabecalho, "w-9")}>
            <span className="sr-only">Selecionar</span>
          </TableHead>
          <TableHead className={cabecalho}>Parcela</TableHead>
          <TableHead className={cabecalho}>Convênio</TableHead>
          <TableHead className={cabecalho}>Data de desconto</TableHead>
          <TableHead className={cn(cabecalho, "text-right")}>Valor</TableHead>
          <TableHead className={cabecalho}>Situação</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {parcelas.map((parcela) => {
          const selecionada = selecionadas.includes(parcela.parcelaId)
          return (
            <TableRow
              key={parcela.parcelaId}
              data-state={selecionada ? "selected" : undefined}
              className={cn(
                "align-top data-[state=selected]:bg-accent [&>td]:py-3",
                !parcela.elegivel && "text-muted-foreground"
              )}
            >
              <TableCell>
                <Checkbox
                  aria-label={`Selecionar a parcela ${parcela.parcelaId}`}
                  disabled={!parcela.elegivel}
                  checked={selecionada}
                  onCheckedChange={(marcada) =>
                    onSelecionadasChange(
                      marcada
                        ? [...selecionadas, parcela.parcelaId]
                        : selecionadas.filter((id) => id !== parcela.parcelaId)
                    )
                  }
                />
              </TableCell>
              <TableCell className="whitespace-normal">
                <span className="block font-semibold">{parcela.parcelaId}</span>
                {parcela.motivoInelegibilidade && (
                  <span className="mt-0.5 block max-w-80 min-w-48 text-xs">
                    {parcela.motivoInelegibilidade}
                  </span>
                )}
              </TableCell>
              <TableCell>{parcela.convenioId}</TableCell>
              <TableCell>{formatarData(parcela.dataDesconto)}</TableCell>
              <TableCell className="text-right font-semibold">
                {formatarMoeda(parcela.valor)}
              </TableCell>
              <TableCell>
                {statusLegivel(parcela.status)} ·{" "}
                {parcela.elegivel ? "elegível" : "inelegível"}
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )
}

// `ABERTA` → `Aberta`: a Central não documenta a lista de status, então nenhum mapa fixo
function statusLegivel(status: string) {
  return status.charAt(0) + status.slice(1).toLowerCase()
}
