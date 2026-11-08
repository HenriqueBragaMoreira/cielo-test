import { screen, within } from "@testing-library/react"
import { delay, http } from "msw"
import { describe, expect, it } from "vitest"

import { renderApp } from "@/test/render-app"
import { server } from "@/test/server"

const operacao = "/operacoes-portabilidade/8d0e7a52-3c1b-4f7e-9a39-2f4f0c1d9b11"

async function passos() {
  const stepper = await screen.findByRole("list", {
    name: "Etapas da portabilidade",
  })
  return within(stepper).getAllByRole("listitem")
}

describe("shell do app", () => {
  it.each(["/", "/beneficiarios/BEN001/agenda", operacao])(
    "mostra a marca, o produto e o aviso de avaliação em %s",
    async (path) => {
      server.use(http.get("/api/*", () => delay("infinite")))
      renderApp(path)

      const topo = await screen.findByRole("banner")
      expect(within(topo).getByRole("img", { name: "Cielo" })).toBeVisible()
      expect(topo).toHaveTextContent("Portabilidade de convênios")
      expect(screen.getByRole("contentinfo")).toHaveTextContent(
        "Projeto de avaliação técnica, sem vínculo oficial com a Cielo."
      )
    }
  )
})

describe("stepper derivado da rota", () => {
  it("marca a autorização como passo atual na tela inicial", async () => {
    renderApp("/")

    const [autorizacao, parcelas, acompanhamento] = await passos()
    expect(autorizacao).toHaveAttribute("aria-current", "step")
    expect(autorizacao).toHaveTextContent("1Autorização")
    expect(parcelas).not.toHaveAttribute("aria-current")
    expect(parcelas).toHaveTextContent("2Parcelas e conta")
    expect(acompanhamento).toHaveTextContent("3Acompanhamento")
    expect(screen.queryByText(/concluíd/)).not.toBeInTheDocument()
  })

  it("marca parcelas e conta como atual e a autorização como concluída na agenda", async () => {
    server.use(
      http.get("/api/beneficiarios/BEN001/agenda", () => delay("infinite"))
    )
    renderApp("/beneficiarios/BEN001/agenda")

    const [autorizacao, parcelas, acompanhamento] = await passos()
    expect(autorizacao).toHaveTextContent("Autorização (concluída)")
    expect(autorizacao).not.toHaveAttribute("aria-current")
    expect(parcelas).toHaveAttribute("aria-current", "step")
    expect(acompanhamento).not.toHaveTextContent("concluída")
  })

  it("marca o acompanhamento como atual e os passos anteriores como concluídos na operação", async () => {
    server.use(
      http.get("/api/operacoes-portabilidade/:id", () => delay("infinite"))
    )
    renderApp(operacao)

    const [autorizacao, parcelas, acompanhamento] = await passos()
    expect(autorizacao).toHaveTextContent("Autorização (concluída)")
    expect(parcelas).toHaveTextContent("Parcelas e conta (concluída)")
    expect(acompanhamento).toHaveAttribute("aria-current", "step")
  })
})
