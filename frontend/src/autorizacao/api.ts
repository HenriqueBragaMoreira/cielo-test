export type Autorizacao = {
  beneficiarioId: string
  autorizacaoId: string
  autorizadaEm: string
}

/** `nova` distingue a autorização registrada agora (201) da que já existia (200). */
export async function registrarAutorizacao(beneficiarioId: string) {
  const resposta = await fetch("/api/autorizacoes", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ beneficiarioId }),
  })
  if (!resposta.ok) {
    throw new Error(`Falha ao registrar a autorização (${resposta.status})`)
  }
  const autorizacao: Autorizacao = await resposta.json()
  return { autorizacao, nova: resposta.status === 201 }
}
