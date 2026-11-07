import { screen } from "@testing-library/react"
import { delay, http, HttpResponse } from "msw"
import { describe, expect, it } from "vitest"

import { renderApp } from "@/test/render-app"
import { server } from "@/test/server"

describe("registrar a autorização", () => {
  it("só habilita o botão com o identificador preenchido e o aceite marcado", async () => {
    const { user } = renderApp()
    const botao = await screen.findByRole("button", {
      name: "Registrar autorização",
    })
    const aceite = screen.getByRole("checkbox", {
      name: "O beneficiário autoriza a consulta da agenda e a portabilidade das parcelas",
    })
    expect(botao).toBeDisabled()

    await user.type(
      screen.getByLabelText("Identificador do beneficiário"),
      "BEN001"
    )
    expect(botao).toBeDisabled()

    await user.click(aceite)
    expect(botao).toBeEnabled()

    await user.clear(screen.getByLabelText("Identificador do beneficiário"))
    expect(botao).toBeDisabled()
  })

  it("desabilita o botão durante o envio", async () => {
    server.use(http.post("/api/autorizacoes", () => delay("infinite")))
    const { user } = renderApp()

    await preencherEEnviar(user, "BEN001")

    expect(
      screen.getByRole("button", { name: "Registrar autorização" })
    ).toBeDisabled()
  })

  it("confirma a autorização nova com a data/hora", async () => {
    server.use(
      http.post("/api/autorizacoes", async ({ request }) => {
        const { beneficiarioId } = (await request.json()) as {
          beneficiarioId: string
        }
        return HttpResponse.json(
          {
            beneficiarioId,
            autorizacaoId: "aut-123",
            autorizadaEm: "2026-11-06T13:45:00Z",
          },
          { status: 201 }
        )
      })
    )
    const { user } = renderApp()

    await preencherEEnviar(user, "BEN001")

    const confirmacao = await screen.findByRole("status")
    expect(confirmacao).toHaveTextContent(
      "Autorização do beneficiário BEN001 registrada em 06/11/2026 às 10:45."
    )
    expect(confirmacao).toHaveTextContent("Identificador na Central: aut-123")
  })

  it("leva para a agenda do beneficiário depois de registrar a autorização", async () => {
    server.use(
      http.post("/api/autorizacoes", () =>
        HttpResponse.json(
          {
            beneficiarioId: "BEN001",
            autorizacaoId: "aut-123",
            autorizadaEm: "2026-11-06T13:45:00Z",
          },
          { status: 201 }
        )
      ),
      http.get("/api/beneficiarios/BEN001/agenda", () => HttpResponse.json([]))
    )
    const { user, router } = renderApp()

    await preencherEEnviar(user, "BEN001")

    expect(
      await screen.findByRole("heading", {
        name: "Agenda do beneficiário BEN001",
      })
    ).toBeInTheDocument()
    expect(router.state.location.pathname).toBe("/beneficiarios/BEN001/agenda")
  })

  it("mostra a autorização existente como já registrada", async () => {
    server.use(
      http.post("/api/autorizacoes", () =>
        HttpResponse.json(
          {
            beneficiarioId: "BEN001",
            autorizacaoId: "aut-123",
            autorizadaEm: "2026-11-01T12:00:00Z",
          },
          { status: 200 }
        )
      )
    )
    const { user } = renderApp()

    await preencherEEnviar(user, "BEN001")

    expect(await screen.findByRole("status")).toHaveTextContent(
      "O beneficiário BEN001 já tinha uma autorização registrada em 01/11/2026 às 09:00."
    )
  })

  it("mostra a mensagem de Central indisponível e mantém o formulário preenchido", async () => {
    server.use(
      http.post("/api/autorizacoes", () =>
        HttpResponse.json(
          {
            type: "about:blank",
            title: "Central de Convênios indisponível",
            status: 503,
            detail:
              "A Central de Convênios não respondeu. Tente novamente mais tarde.",
          },
          {
            status: 503,
            headers: { "Content-Type": "application/problem+json" },
          }
        )
      )
    )
    const { user } = renderApp()

    await preencherEEnviar(user, "BEN001")

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A Central de Convênios não respondeu. Tente novamente mais tarde."
    )
    expect(screen.getByLabelText("Identificador do beneficiário")).toHaveValue(
      "BEN001"
    )
    expect(
      screen.getByRole("checkbox", {
        name: "O beneficiário autoriza a consulta da agenda e a portabilidade das parcelas",
      })
    ).toBeChecked()
    expect(
      screen.getByRole("button", { name: "Registrar autorização" })
    ).toBeEnabled()
  })

  it("mostra uma mensagem genérica quando o erro não vem como problem+json", async () => {
    server.use(
      http.post(
        "/api/autorizacoes",
        () => new HttpResponse("<html>Bad Gateway</html>", { status: 502 })
      )
    )
    const { user } = renderApp()

    await preencherEEnviar(user, "BEN001")

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Não foi possível concluir a requisição. Tente novamente mais tarde."
    )
  })
})

async function preencherEEnviar(
  user: ReturnType<typeof renderApp>["user"],
  beneficiarioId: string
) {
  await user.type(
    await screen.findByLabelText("Identificador do beneficiário"),
    beneficiarioId
  )
  await user.click(
    screen.getByRole("checkbox", {
      name: "O beneficiário autoriza a consulta da agenda e a portabilidade das parcelas",
    })
  )
  await user.click(
    screen.getByRole("button", { name: "Registrar autorização" })
  )
}
