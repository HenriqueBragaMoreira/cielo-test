import { describe, expect, it } from "vitest"

import { montarEixo, niveisDosRotulos, rotuloNaTelaEstreita } from "./regua"

// de 01/11/2025 a 01/03/2026 são 120 dias: novembro 30, dezembro 31, janeiro 31, fevereiro 28
const percentual = (dias: number) => (dias / 120) * 100

describe("eixo da régua da agenda", () => {
  it("posiciona cada data em proporção aos dias decorridos desde o início do eixo", () => {
    const eixo = montarEixo(["2026-01-10", "2026-02-10"], "2025-11-08")

    expect(eixo.posicao("2025-11-01")).toBe(0)
    expect(eixo.posicao("2026-01-10")).toBeCloseTo(percentual(70))
    expect(eixo.posicao("2026-02-10")).toBeCloseTo(percentual(101))
    expect(eixo.posicao("2026-03-01")).toBe(100)
  })

  it("vai do mês de hoje até o mês seguinte à última parcela, e as palavras-guia nomeiam os dois", () => {
    const eixo = montarEixo(["2026-02-10", "2026-01-10"], "2025-11-08")

    expect(eixo.palavrasGuia).toBe("nov 2025 — mar 2026")
  })

  it("começa no mês da primeira parcela quando ela é anterior a hoje", () => {
    const eixo = montarEixo(["2026-12-01", "2026-09-15"], "2026-11-08")

    expect(eixo.palavrasGuia).toBe("set 2026 — jan 2027")
  })

  it("se estende até o mês seguinte a hoje quando todas as parcelas já passaram, para hoje caber no eixo", () => {
    const eixo = montarEixo(["2026-09-15"], "2026-11-08")

    expect(eixo.palavrasGuia).toBe("set 2026 — dez 2026")
  })

  it("marca hoje na sua posição proporcional", () => {
    const eixo = montarEixo(["2026-02-10"], "2025-11-08")

    expect(eixo.hoje).toBeCloseTo(percentual(7))
  })

  it("marca o início de cada mês, com o ano só na virada, e os dias 10 e 20 de cada mês", () => {
    const eixo = montarEixo(["2026-02-10"], "2025-11-08")

    expect(eixo.meses.map(({ rotulo }) => rotulo)).toEqual([
      "nov",
      "dez",
      "jan 2026",
      "fev",
      "mar",
    ])
    expect(eixo.meses.map(({ posicao }) => posicao)).toEqual([
      0,
      percentual(30),
      percentual(61),
      percentual(92),
      100,
    ])
    expect(eixo.dias).toEqual([9, 19, 39, 49, 70, 80, 101, 111].map(percentual))
  })
})

describe("níveis dos rótulos das parcelas na régua", () => {
  it("sobe o rótulo de uma parcela próxima demais da anterior, alternando os níveis, na ordem recebida", () => {
    expect(niveisDosRotulos([60, 10, 20, 75, 70])).toEqual([0, 0, 1, 0, 1])
  })

  it("mantém no chão as parcelas distantes entre si", () => {
    expect(niveisDosRotulos([10, 40, 70])).toEqual([0, 0, 0])
  })
})

describe("rótulos de mês da régua numa tela estreita", () => {
  const visiveis = (total: number) =>
    Array.from({ length: total }, (_, indice) => indice).filter((indice) =>
      rotuloNaTelaEstreita(indice, total)
    )

  it("mostra todos os meses de um eixo curto", () => {
    expect(visiveis(6)).toEqual([0, 1, 2, 3, 4, 5])
  })

  it("num eixo longo mostra um mês sim, outro não, e sempre o último, sem o vizinho dele", () => {
    expect(visiveis(12)).toEqual([0, 2, 4, 6, 8, 11])
    expect(visiveis(9)).toEqual([0, 2, 4, 6, 8])
  })
})
