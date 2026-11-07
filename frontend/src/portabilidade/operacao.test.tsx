import { screen, within } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { describe, expect, it } from "vitest"

import { renderApp } from "@/test/render-app"
import { server } from "@/test/server"

import type { Operacao } from "./api"

const id = "8d0e7a52-3c1b-4f7e-9a39-2f4f0c1d9b11"

describe("tela da operação", () => {
  it("abre pelo endereço da operação, com o total das parcelas", async () => {
    const operacao: Operacao = {
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
      ],
    }
    server.use(
      http.get(`/api/operacoes-portabilidade/${id}`, () =>
        HttpResponse.json(operacao)
      )
    )
    renderApp(`/operacoes-portabilidade/${id}`)

    expect(await screen.findByText("prot-123")).toBeVisible()
    expect(screen.getByText("PENDENTE")).toBeVisible()
    expect(screen.getByText("BEN001")).toBeVisible()
    expect(screen.getByRole("row", { name: /Total/ })).toHaveTextContent(
      /R\$\s450,50/
    )
  })

  it("mostra a falha no envio na linha do tempo", async () => {
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

    const linhaDoTempo = within(
      await screen.findByRole("list", { name: "Linha do tempo" })
    ).getAllByRole("listitem")
    expect(linhaDoTempo).toHaveLength(2)
    expect(linhaDoTempo[0]).toHaveTextContent("Solicitada06/11/2026 às 10:45")
    expect(linhaDoTempo[1]).toHaveTextContent(
      "Falha no envio06/11/2026 às 10:45"
    )
  })

  it("mostra a mensagem quando a operação não existe", async () => {
    server.use(
      http.get(`/api/operacoes-portabilidade/${id}`, () =>
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
})
