/** Erro de uma chamada à `/api`; `message` é o `detail` do `problem+json`, já legível para o usuário. */
export class ErroApi extends Error {
  readonly status: number

  constructor(status: number, mensagem: string) {
    super(mensagem)
    this.name = "ErroApi"
    this.status = status
  }
}

/** Lança um `ErroApi` a partir de uma resposta de erro, lida como `problem+json` quando o backend a enviou. */
export async function lancarErroApi(resposta: Response): Promise<never> {
  // sem problem+json (ex.: o proxy com o backend fora do ar), cai na mensagem genérica
  const problema = resposta.headers
    .get("Content-Type")
    ?.includes("application/problem+json")
    ? ((await resposta.json()) as { detail?: string })
    : {}
  throw new ErroApi(
    resposta.status,
    problema.detail ??
      "Não foi possível concluir a requisição. Tente novamente mais tarde."
  )
}
