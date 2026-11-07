import { buscarApi } from "@/lib/api"

export type ContaDestino = {
  banco: string
  agencia: string
  conta: string
  digito: string
}

export type TipoEvento =
  "SOLICITADA" | "ENVIADA" | "FALHA_ENVIO" | "CONFIRMADA" | "REJEITADA"

export type Operacao = {
  id: string
  beneficiarioId: string
  status: "PENDENTE" | "CONFIRMADA" | "REJEITADA" | "FALHA_ENVIO"
  protocolo: string | null
  motivoRejeicao: string | null
  parcelas: { parcelaId: string; valor: number }[]
  contaDestino: ContaDestino
  /** Na ordem em que aconteceram; `dataHora` é um instante ISO. */
  eventos: { tipo: TipoEvento; dataHora: string; detalhe: string | null }[]
  /** Por que esta consulta não trouxe o status atual da Central; a operação continua PENDENTE (ADR-0007). */
  avisoAtualizacao: "FALHA_NA_ATUALIZACAO" | "PROTOCOLO_NAO_RECONHECIDO" | null
}

export type PedidoOperacao = {
  beneficiarioId: string
  parcelaIds: string[]
  contaDestino: ContaDestino
}

/** `idempotencyKey` identifica a intenção: reenviar a mesma chave nunca cria uma segunda operação (ADR-0005). */
export async function enviarOperacao(
  idempotencyKey: string,
  pedido: PedidoOperacao
) {
  const resposta = await buscarApi("/api/operacoes-portabilidade", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Idempotency-Key": idempotencyKey,
    },
    body: JSON.stringify(pedido),
  })
  const operacao: Operacao = await resposta.json()
  return operacao
}

/**
 * A cada consulta de uma operação PENDENTE o backend pergunta à Central pela decisão (ADR-0008). `repetirEmMs` vem do
 * `Retry-After`, que o backend só envia enquanto a operação está PENDENTE; sem ele (`false`), não há o que esperar.
 */
export async function consultarOperacao(id: string, signal?: AbortSignal) {
  const resposta = await buscarApi(
    `/api/operacoes-portabilidade/${encodeURIComponent(id)}`,
    { signal }
  )
  const operacao: Operacao = await resposta.json()
  const repetirEmMs: number | false =
    Number(resposta.headers.get("Retry-After")) * 1000 || false
  return { operacao, repetirEmMs }
}
