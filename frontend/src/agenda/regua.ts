/** `AAAA-MM-DD` → dias desde a época, em UTC, para a distância entre datas não depender do fuso nem do horário de verão. */
function dia(data: string) {
  const [ano, mes, d] = data.split("-").map(Number)
  return Date.UTC(ano, mes - 1, d) / 86_400_000
}

/** Primeiro dia do mês da data, deslocado `meses` meses. */
function inicioDoMes(data: string, meses = 0) {
  const [ano, mes] = data.split("-").map(Number)
  const alvo = new Date(Date.UTC(ano, mes - 1 + meses, 1))
  return alvo.toISOString().slice(0, 10)
}

const nomesDosMeses = [
  "jan",
  "fev",
  "mar",
  "abr",
  "mai",
  "jun",
  "jul",
  "ago",
  "set",
  "out",
  "nov",
  "dez",
]

/** `2025-11-01` → `nov 2025` */
function nomeDoMes(data: string) {
  const [ano, mes] = data.split("-").map(Number)
  return `${nomesDosMeses[mes - 1]} ${ano}`
}

export type Eixo = ReturnType<typeof montarEixo>

/**
 * O eixo da régua da agenda: do mês de hoje (ou do mês da primeira parcela, se for anterior) até o mês seguinte à
 * última parcela. A posição de cada data é o percentual dos dias decorridos, então distância é duração.
 */
export function montarEixo(datas: string[], hoje: string) {
  // `AAAA-MM-DD` ordena como texto
  const ordenadas = [...datas].sort()
  const inicio = inicioDoMes([ordenadas[0], hoje].sort()[0])
  // hoje fica sempre no eixo, mesmo quando todas as parcelas já passaram
  const fim = inicioDoMes([ordenadas.at(-1)!, hoje].sort()[1], 1)
  const total = dia(fim) - dia(inicio)
  const posicao = (data: string) => ((dia(data) - dia(inicio)) / total) * 100
  const meses = []
  const dias = []
  for (let mes = inicio; mes <= fim; mes = inicioDoMes(mes, 1)) {
    const numero = Number(mes.slice(5, 7))
    meses.push({
      rotulo: numero === 1 ? nomeDoMes(mes) : nomesDosMeses[numero - 1],
      posicao: posicao(mes),
    })
    if (mes < fim) {
      dias.push(
        posicao(`${mes.slice(0, 8)}10`),
        posicao(`${mes.slice(0, 8)}20`)
      )
    }
  }
  return {
    posicao,
    hoje: posicao(hoje),
    meses,
    dias,
    palavrasGuia: `${nomeDoMes(inicio)} — ${nomeDoMes(fim)}`,
  }
}

/** Distância, em % do eixo, abaixo da qual os rótulos de duas parcelas vizinhas se tocam numa tela estreita. */
const distanciaMinima = 25

/**
 * O nível de cada rótulo (0 junto ao eixo, 1 acima), na ordem recebida: a parcela perto demais da anterior no eixo
 * troca de nível, para os rótulos não se sobreporem.
 */
export function niveisDosRotulos(posicoes: number[]) {
  const ordem = posicoes.map((_, indice) => indice)
  ordem.sort((a, b) => posicoes[a] - posicoes[b])
  const niveis: number[] = []
  ordem.forEach((indice, i) => {
    const anterior = ordem[i - 1]
    niveis[indice] =
      i > 0 && posicoes[indice] - posicoes[anterior] < distanciaMinima
        ? 1 - niveis[anterior]
        : 0
  })
  return niveis
}

/** Até 6 meses, todo rótulo de mês cabe numa tela estreita; acima disso, um mês sim, outro não, e sempre o último. */
export function rotuloNaTelaEstreita(indice: number, total: number) {
  if (total <= 6 || indice === total - 1) return true
  return indice % 2 === 0 && indice !== total - 2
}
