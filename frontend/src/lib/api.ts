/** Erro de uma chamada à `/api`; `message` é o `detail` do `problem+json`, já legível para o usuário. */
export class ErroApi extends Error {
  readonly status: number

  constructor(status: number, mensagem: string) {
    super(mensagem)
    this.name = "ErroApi"
    this.status = status
  }
}

const MENSAGEM_GENERICA =
  "Não foi possível concluir a requisição. Tente novamente mais tarde."

/** `fetch` para a `/api` que lança `ErroApi` (status `0` sem resposta) em vez de devolver uma resposta de erro. */
export async function buscarApi(url: string, init?: RequestInit) {
  const resposta = await fetch(url, init).catch(() => {
    throw new ErroApi(0, MENSAGEM_GENERICA)
  })
  if (!resposta.ok) {
    await lancarErroApi(resposta)
  }
  return resposta
}

/** Lança um `ErroApi` a partir de uma resposta de erro, lida como `problem+json` quando o backend a enviou. */
async function lancarErroApi(resposta: Response): Promise<never> {
  // sem problem+json legível (ex.: o proxy com o backend fora do ar), cai na mensagem genérica
  const problema: { detail?: string } = resposta.headers
    .get("Content-Type")
    ?.includes("application/problem+json")
    ? await resposta.json().catch(() => ({}))
    : {}
  throw new ErroApi(resposta.status, problema.detail ?? MENSAGEM_GENERICA)
}
