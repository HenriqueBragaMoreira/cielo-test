import { screen, within } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { afterEach, describe, expect, it, vi } from "vitest"

import { renderApp } from "@/test/render-app"
import { server } from "@/test/server"

import type { Operacao } from "./api"

const id = "8d0e7a52-3c1b-4f7e-9a39-2f4f0c1d9b11"
const url = `/api/operacoes-portabilidade/${id}`

const pendente: Operacao = {
  id,
  beneficiarioId: "BEN001",
  status: "PENDENTE",
  protocolo: "prot-123",
  motivoRejeicao: null,
  parcelas: [
    { parcelaId: "P001", valor: 150.5 },
    { parcelaId: "P002", valor: 300 },
  ],
  contaDestino: {
    banco: "001",
    agencia: "1234",
    conta: "56789",
    digito: "0",
  },
  eventos: [
    { tipo: "SOLICITADA", dataHora: "2026-11-06T13:45:00Z", detalhe: null },
    { tipo: "ENVIADA", dataHora: "2026-11-06T13:45:01Z", detalhe: null },
  ],
}

const confirmada: Operacao = {
  ...pendente,
  status: "CONFIRMADA",
  eventos: [
    ...pendente.eventos,
    { tipo: "CONFIRMADA", dataHora: "2026-11-06T13:45:07Z", detalhe: null },
  ],
}

const motivo = "Beneficiário atingiu o limite de margem consignável"
const rejeitada: Operacao = {
  ...pendente,
  status: "REJEITADA",
  motivoRejeicao: motivo,
  eventos: [
    ...pendente.eventos,
    { tipo: "REJEITADA", dataHora: "2026-11-06T13:45:07Z", detalhe: motivo },
  ],
}

/** Como o backend responde: `Retry-After` só enquanto a operação está PENDENTE. */
function responder(operacao: Operacao, retryAfter = "2") {
  return HttpResponse.json(operacao, {
    headers:
      operacao.status === "PENDENTE" ? { "Retry-After": retryAfter } : {},
  })
}

/** Responde cada consulta com a próxima operação da lista (a última se repete) e conta as consultas. */
function consultasRespondidasCom(...operacoes: Operacao[]) {
  const contador = { consultas: 0 }
  server.use(
    http.get(url, () => {
      const operacao =
        operacoes[Math.min(contador.consultas, operacoes.length - 1)]
      contador.consultas++
      return responder(operacao)
    })
  )
  return contador
}

function linhaDoTempo() {
  return within(
    screen.getByRole("list", { name: "Linha do tempo" })
  ).getAllByRole("listitem")
}

describe("tela da operação", () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it("abre pelo endereço da operação, com o total das parcelas", async () => {
    server.use(http.get(url, () => responder(pendente)))
    renderApp(`/operacoes-portabilidade/${id}`)

    expect(await screen.findByText("prot-123")).toBeVisible()
    expect(screen.getByText("PENDENTE")).toBeVisible()
    expect(screen.getByText("BEN001")).toBeVisible()
    expect(screen.getByRole("row", { name: /Total/ })).toHaveTextContent(
      /R\$\s450,50/
    )
  })

  it("mostra a falha no envio, com as parcelas liberadas, e a falha na linha do tempo", async () => {
    const operacao: Operacao = {
      id,
      beneficiarioId: "BEN001",
      status: "FALHA_ENVIO",
      protocolo: null,
      motivoRejeicao: null,
      parcelas: [{ parcelaId: "P001", valor: 150.5 }],
      contaDestino: {
        banco: "001",
        agencia: "1234",
        conta: "56789",
        digito: "0",
      },
      eventos: [
        { tipo: "SOLICITADA", dataHora: "2026-11-06T13:45:00Z", detalhe: null },
        { tipo: "FALHA_ENVIO", dataHora: "2026-11-06T13:45:05Z", detalhe: null },
      ],
    }
    server.use(
      http.get(`/api/operacoes-portabilidade/${id}`, () =>
        HttpResponse.json(operacao)
      )
    )
    renderApp(`/operacoes-portabilidade/${id}`)

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Falha no envioA Central de Convênios não respondeu e a operação não foi enviada. As parcelas ficaram liberadas."
    )
    const linhaDoTempo = within(
      screen.getByRole("list", { name: "Linha do tempo" })
    ).getAllByRole("listitem")
    expect(linhaDoTempo).toHaveLength(2)
    expect(linhaDoTempo[0]).toHaveTextContent("Solicitada06/11/2026 às 10:45")
    expect(linhaDoTempo[1]).toHaveTextContent(
      "Falha no envio06/11/2026 às 10:45"
    )
  })

  it("mostra a mensagem quando a operação não existe", async () => {
    server.use(
      http.get(url, () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Operação não encontrada",
            status: 404,
            detail: `A operação de portabilidade ${id} não existe.`,
          },
          {
            status: 404,
            headers: { "Content-Type": "application/problem+json" },
          }
        )
      )
    )
    renderApp(`/operacoes-portabilidade/${id}`)

    expect(await screen.findByRole("alert")).toHaveTextContent(
      `A operação de portabilidade ${id} não existe.`
    )
  })

  it("consulta no intervalo do Retry-After enquanto pendente e para no estado final", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const contador = { consultas: 0 }
    server.use(
      http.get(url, () => {
        contador.consultas++
        return contador.consultas < 3
          ? responder(pendente, "3")
          : responder(confirmada)
      })
    )
    renderApp(`/operacoes-portabilidade/${id}`)

    expect(
      await screen.findByText("Aguardando a decisão da Central de Convênios…")
    ).toBeVisible()
    expect(contador.consultas).toBe(1)

    await vi.advanceTimersByTimeAsync(2900)
    expect(contador.consultas).toBe(1)
    await vi.advanceTimersByTimeAsync(100)
    expect(contador.consultas).toBe(2)
    await vi.advanceTimersByTimeAsync(3000)

    expect(await screen.findByText("Operação confirmada")).toBeVisible()
    expect(contador.consultas).toBe(3)
    expect(
      screen.queryByText("Aguardando a decisão da Central de Convênios…")
    ).not.toBeInTheDocument()

    await vi.advanceTimersByTimeAsync(30_000)
    expect(contador.consultas).toBe(3)
  })

  it("para de consultar ao sair da tela da operação", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const contador = consultasRespondidasCom(pendente)
    const { router } = renderApp(`/operacoes-portabilidade/${id}`)
    await screen.findByText("Aguardando a decisão da Central de Convênios…")

    await router.navigate({ to: "/" })
    await vi.advanceTimersByTimeAsync(10_000)

    expect(contador.consultas).toBe(1)
  })

  it("mostra a confirmação e o evento final na linha do tempo", async () => {
    consultasRespondidasCom(confirmada)
    renderApp(`/operacoes-portabilidade/${id}`)

    expect(await screen.findByRole("status")).toHaveTextContent(
      "Operação confirmada"
    )
    expect(
      screen.queryByRole("button", { name: "Atualizar" })
    ).not.toBeInTheDocument()
    const eventos = linhaDoTempo()
    expect(eventos).toHaveLength(3)
    expect(eventos[2]).toHaveTextContent("Confirmada06/11/2026 às 10:45")
  })

  it("mostra a rejeição com o motivo e o evento final na linha do tempo", async () => {
    consultasRespondidasCom(rejeitada)
    renderApp(`/operacoes-portabilidade/${id}`)

    const status = await screen.findByRole("status")
    expect(status).toHaveTextContent("Operação rejeitada")
    expect(status).toHaveTextContent(`Motivo: ${motivo}`)
    const eventos = linhaDoTempo()
    expect(eventos).toHaveLength(3)
    expect(eventos[2]).toHaveTextContent("Rejeitada06/11/2026 às 10:45")
  })

  it("o botão Atualizar consulta sob demanda", async () => {
    const contador = consultasRespondidasCom(pendente, confirmada)
    const { user } = renderApp(`/operacoes-portabilidade/${id}`)
    await screen.findByText("Aguardando a decisão da Central de Convênios…")

    await user.click(screen.getByRole("button", { name: "Atualizar" }))

    expect(await screen.findByText("Operação confirmada")).toBeVisible()
    expect(contador.consultas).toBe(2)
    expect(linhaDoTempo()).toHaveLength(3)
  })
})
