import { screen, within } from "@testing-library/react"
import { delay, http, HttpResponse } from "msw"
import { afterEach, describe, expect, it, vi } from "vitest"

import { renderApp } from "@/test/render-app"
import { server } from "@/test/server"

const agendaBen001 = "/api/beneficiarios/BEN001/agenda"

describe("consultar a agenda", () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it("mostra o beneficiário no cabeçalho do workspace", async () => {
    server.use(http.get(agendaBen001, () => HttpResponse.json([])))
    renderApp("/beneficiarios/BEN001/agenda")

    expect(
      await screen.findByRole("heading", {
        level: 1,
        name: "Beneficiário BEN001",
      })
    ).toBeVisible()
  })

  it("Trocar beneficiário volta para a autorização", async () => {
    server.use(http.get(agendaBen001, () => HttpResponse.json([])))
    const { user, router } = renderApp("/beneficiarios/BEN001/agenda")
    await screen.findByText("O beneficiário não tem parcelas na agenda.")

    await user.click(screen.getByRole("link", { name: "Trocar beneficiário" }))

    expect(router.state.location.pathname).toBe("/")
  })

  it("mostra cada parcela com convênio, data de desconto, valor e a situação em linguagem legível", async () => {
    server.use(
      http.get(agendaBen001, () =>
        HttpResponse.json([
          {
            parcelaId: "P001",
            convenioId: "CONV01",
            valor: 1500.5,
            dataDesconto: "2026-01-10",
            status: "ABERTA",
            elegivel: true,
            motivoInelegibilidade: null,
          },
        ])
      )
    )
    renderApp("/beneficiarios/BEN001/agenda")

    const linha = await screen.findByRole("row", { name: /P001/ })
    expect(
      screen.getAllByRole("columnheader").map((coluna) => coluna.textContent)
    ).toEqual([
      "Selecionar",
      "Parcela",
      "Convênio",
      "Data de desconto",
      "Valor",
      "Situação",
    ])
    expect(within(linha).getByRole("cell", { name: "CONV01" })).toBeVisible()
    expect(
      within(linha).getByRole("cell", { name: "10/01/2026" })
    ).toBeVisible()
    expect(
      within(linha).getByRole("cell", { name: /^R\$\s1\.500,50$/ })
    ).toBeVisible()
    expect(
      within(linha).getByRole("cell", { name: "Aberta · elegível" })
    ).toBeVisible()
    expect(linha).not.toHaveTextContent("ABERTA")
  })

  it("mostra a parcela inelegível em cinza, com o motivo abaixo do id", async () => {
    const motivo =
      "A parcela está com status QUITADA; só parcelas ABERTA podem ser portadas."
    server.use(
      http.get(agendaBen001, () =>
        HttpResponse.json([
          {
            parcelaId: "P002",
            convenioId: "CONV02",
            valor: 300,
            dataDesconto: "2026-02-15",
            status: "QUITADA",
            elegivel: false,
            motivoInelegibilidade: motivo,
          },
        ])
      )
    )
    renderApp("/beneficiarios/BEN001/agenda")

    const linha = await screen.findByRole("row", { name: /P002/ })
    expect(linha).toHaveClass("text-muted-foreground")
    expect(
      within(linha).getByRole("cell", { name: "Quitada · inelegível" })
    ).toBeVisible()
    const selecao = within(linha).getByRole("checkbox", {
      name: "Selecionar a parcela P002",
    })
    expect(selecao).toHaveAttribute("aria-disabled", "true")
    expect(selecao).toHaveAccessibleDescription(motivo)
    // o motivo vem na linha logo abaixo da parcela, também em cinza
    const linhaDoMotivo = linha.nextElementSibling
    expect(linhaDoMotivo).toHaveTextContent(motivo)
    expect(linhaDoMotivo).toHaveClass("text-muted-foreground")
  })

  it("mostra a régua da agenda, oculta para leitores de tela, com o intervalo nas palavras-guia e cada parcela com valor, data e id", async () => {
    // só o relógio: o eixo começa no mês de hoje
    vi.useFakeTimers({ toFake: ["Date"] })
    vi.setSystemTime(new Date("2025-11-08T12:00:00-03:00"))
    server.use(
      http.get(agendaBen001, () =>
        HttpResponse.json([
          {
            parcelaId: "P001",
            convenioId: "CONV01",
            valor: 150,
            dataDesconto: "2026-01-10",
            status: "ABERTA",
            elegivel: true,
            motivoInelegibilidade: null,
          },
          {
            parcelaId: "P002",
            convenioId: "CONV01",
            valor: 300,
            dataDesconto: "2026-02-10",
            status: "QUITADA",
            elegivel: false,
            motivoInelegibilidade: "Quitada.",
          },
        ])
      )
    )
    renderApp("/beneficiarios/BEN001/agenda")
    await screen.findByRole("row", { name: /P001/ })

    const agenda = screen.getByRole("region", { name: "Agenda de descontos" })
    expect(within(agenda).getByText("nov 2025 — mar 2026")).toBeVisible()
    const regua = within(agenda).getByTestId("regua-agenda")
    expect(regua).toHaveAttribute("aria-hidden", "true")
    expect(within(regua).getByText("hoje")).toBeInTheDocument()
    expect(within(regua).getByText("jan 2026")).toBeInTheDocument()
    expect(within(regua).getByText("10/01 · P001")).toBeInTheDocument()
    expect(within(regua).getByText(/^R\$\s150,00$/)).toBeInTheDocument()
    expect(
      within(regua).getByText("10/02 · P002").closest("[data-estado]")
    ).toHaveAttribute("data-estado", "inelegivel")
  })

  it("mostra uma mensagem própria para a agenda vazia", async () => {
    server.use(http.get(agendaBen001, () => HttpResponse.json([])))
    renderApp("/beneficiarios/BEN001/agenda")

    expect(
      await screen.findByText("O beneficiário não tem parcelas na agenda.")
    ).toBeVisible()
    expect(screen.queryByRole("table")).not.toBeInTheDocument()
  })

  it("orienta a registrar a autorização quando o beneficiário não autorizou", async () => {
    server.use(
      http.get(agendaBen001, () =>
        problema(
          403,
          "Beneficiário sem autorização",
          "O beneficiário BEN001 não tem autorização registrada. Registre a autorização antes de consultar a agenda ou portar parcelas."
        )
      )
    )
    const { user, router } = renderApp("/beneficiarios/BEN001/agenda")

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "O beneficiário BEN001 não tem autorização registrada. Registre a autorização antes de consultar a agenda ou portar parcelas."
    )
    await user.click(
      screen.getByRole("link", { name: "Registrar a autorização" })
    )
    expect(
      await screen.findByLabelText("Identificador do beneficiário")
    ).toBeVisible()
    expect(router.state.location.pathname).toBe("/")
  })

  it("oferece tentar novamente quando a Central de Convênios não responde", async () => {
    server.use(
      http.get(
        agendaBen001,
        () =>
          problema(
            503,
            "Central de Convênios indisponível",
            "A Central de Convênios não respondeu. Tente novamente mais tarde."
          ),
        { once: true }
      ),
      http.get(agendaBen001, () => HttpResponse.json([]))
    )
    const { user } = renderApp("/beneficiarios/BEN001/agenda")

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A Central de Convênios não respondeu. Tente novamente mais tarde."
    )
    await user.click(screen.getByRole("button", { name: "Tentar novamente" }))

    expect(
      await screen.findByText("O beneficiário não tem parcelas na agenda.")
    ).toBeVisible()
    expect(screen.queryByRole("alert")).not.toBeInTheDocument()
  })

  it("mostra o indicador de carregamento com o formato do workspace", async () => {
    server.use(http.get(agendaBen001, () => delay("infinite")))
    renderApp("/beneficiarios/BEN001/agenda")

    expect(await screen.findByText("Carregando agenda…")).toBeVisible()
    expect(
      screen.getByRole("region", { name: "Agenda de descontos" })
    ).toHaveAttribute("aria-busy", "true")
    expect(
      screen.getByRole("complementary", { name: "Resumo da operação" })
    ).toHaveAttribute("aria-busy", "true")
    expect(
      screen.getByRole("heading", { level: 1, name: "Beneficiário BEN001" })
    ).toBeVisible()
  })
})

function problema(status: number, title: string, detail: string) {
  return HttpResponse.json(
    { type: "about:blank", title, status, detail },
    { status, headers: { "Content-Type": "application/problem+json" } }
  )
}
