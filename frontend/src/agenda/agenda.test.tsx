import { screen, within } from "@testing-library/react"
import { delay, http, HttpResponse } from "msw"
import { describe, expect, it } from "vitest"

import { renderApp } from "@/test/render-app"
import { server } from "@/test/server"

const agendaBen001 = "/api/beneficiarios/BEN001/agenda"

describe("consultar a agenda", () => {
  it("mostra cada parcela com o valor em reais e a data no formato brasileiro", async () => {
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
    expect(within(linha).getByRole("cell", { name: "CONV01" })).toBeVisible()
    expect(
      within(linha).getByRole("cell", { name: /^R\$\s1\.500,50$/ })
    ).toBeVisible()
    expect(
      within(linha).getByRole("cell", { name: "10/01/2026" })
    ).toBeVisible()
    expect(within(linha).getByRole("cell", { name: "ABERTA" })).toBeVisible()
    expect(within(linha).getByRole("cell", { name: "Sim" })).toBeVisible()
  })

  it("mostra o motivo de uma parcela inelegível", async () => {
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
            motivoInelegibilidade:
              "A parcela está com status QUITADA; só parcelas ABERTA podem ser portadas.",
          },
        ])
      )
    )
    renderApp("/beneficiarios/BEN001/agenda")

    const linha = await screen.findByRole("row", { name: /P002/ })
    expect(
      within(linha).getByRole("cell", {
        name: "Não: A parcela está com status QUITADA; só parcelas ABERTA podem ser portadas.",
      })
    ).toBeVisible()
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
          "O beneficiário BEN001 não tem autorização registrada. Registre a autorização antes de consultar a agenda."
        )
      )
    )
    const { user, router } = renderApp("/beneficiarios/BEN001/agenda")

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "O beneficiário BEN001 não tem autorização registrada. Registre a autorização antes de consultar a agenda."
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

  it("mostra o indicador de carregamento durante a consulta", async () => {
    server.use(http.get(agendaBen001, () => delay("infinite")))
    renderApp("/beneficiarios/BEN001/agenda")

    expect(await screen.findByText("Carregando agenda…")).toBeVisible()
  })
})

function problema(status: number, title: string, detail: string) {
  return HttpResponse.json(
    { type: "about:blank", title, status, detail },
    { status, headers: { "Content-Type": "application/problem+json" } }
  )
}
