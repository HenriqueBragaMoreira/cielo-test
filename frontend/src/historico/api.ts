import { buscarApi } from "@/lib/api"
import type { Operacao } from "@/portabilidade/api"

/** Uma linha do histórico; `criadaEm` e `atualizadaEm` são instantes ISO. */
export type ResumoOperacao = {
  id: string
  beneficiarioId: string
  status: Operacao["status"]
  quantidadeParcelas: number
  valorTotal: number
  criadaEm: string
  atualizadaEm: string
}

/** As 50 operações mais recentes, como estão no nosso banco: o backend não consulta a Central aqui (ADR-0013). */
export async function listarOperacoes() {
  const resposta = await buscarApi("/api/operacoes-portabilidade")
  const operacoes: ResumoOperacao[] = await resposta.json()
  return operacoes
}
