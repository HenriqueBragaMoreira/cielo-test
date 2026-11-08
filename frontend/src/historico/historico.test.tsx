import { screen, within } from "@testing-library/react"
import { http, HttpResponse } from "msw"
import { describe, expect, it } from "vitest"

import { renderApp } from "@/test/render-app"
import { server } from "@/test/server"

import type { ResumoOperacao } from "./api"

const confirmada: ResumoOperacao = {
  id: "8d0e7a52-3c1b-4f7e-9a39-2f4f0c1d9b11",
  beneficiarioId: "BEN001",
  status: "CONFIRMADA",
  quantidadeParcelas: 2,
  valorTotal: 450.5,
  criadaEm: "2026-11-06T13:45:00Z",
  atualizadaEm: "2026-11-06T13:45:07Z",
}

const pendente: ResumoOperacao = {
  id: "1f2e3d4c-5b6a-4789-8a9b-0c1d2e3f4a5b",
  beneficiarioId: "BEN002",
  status: "PENDENTE",
  quantidadeParcelas: 1,
  valorTotal: 1500,
  criadaEm: "2026-11-06T12:30:00Z",
  atualizadaEm: "2026-11-06T12:30:01Z",
}

function historicoResponde(...operacoes: ResumoOperacao[]) {
  server.use(
    http.get("/api/operacoes-portabilidade", () => HttpResponse.json(operacoes))
  )
}

// Todo teste aqui termina sem requisição em voo: uma requisição pendente (ex.: `delay("infinite")`) ou abortada
// no fim de um teste vaza para o seguinte, e no CI (Node 24) a requisição do teste seguinte escapa do MSW.
const naoEncontrado = () =>
  HttpResponse.json(
    { status: 404, detail: "Fora deste teste." },
    {
      status: 404,
      headers: { "Content-Type": "application/problem+json" },
    }
  )

describe("link do histórico na barra de topo", () => {
  it.each([
    "/",
    "/beneficiarios/BEN001/agenda",
    `/operacoes-portabilidade/${confirmada.id}`,
  ])("aparece em %s e abre o histórico", async (path) => {
    // o handler mais recente vence: o histórico responde a lista, o resto da /api responde 404 na hora
    server.use(http.get("/api/*", naoEncontrado))
    historicoResponde()
    const { user, router } = renderApp(path)

    const topo = await screen.findByRole("banner")
    await user.click(within(topo).getByRole("link", { name: "Histórico" }))

    expect(router.state.location.pathname).toBe("/historico")
    await screen.findByText("Nenhuma operação registrada ainda.")
  })

  it("marca o histórico como página atual, sem stepper", async () => {
    historicoResponde()
    renderApp("/historico")

    const topo = await screen.findByRole("banner")
    expect(
      within(topo).getByRole("link", { name: "Histórico" })
    ).toHaveAttribute("aria-current", "page")
    expect(
      screen.queryByRole("list", { name: "Etapas da portabilidade" })
    ).not.toBeInTheDocument()
    await screen.findByText("Nenhuma operação registrada ainda.")
  })
})

describe("histórico de operações", () => {
  it("lista as operações com data, beneficiário, parcelas, total e status", async () => {
    historicoResponde(confirmada, pendente)
    renderApp("/historico")

    expect(
      await screen.findByRole("heading", { name: "Histórico de operações" })
    ).toBeVisible()
    await screen.findByRole("cell", { name: "BEN001" })
    expect(
      screen.getByText(/as pendentes se atualizam quando você abre a operação/i)
    ).toBeVisible()
    const [, primeira, segunda] = screen.getAllByRole("row")
    expect(
      within(primeira)
        .getAllByRole("cell")
        .map((c) => c.textContent)
    ).toEqual(["06/11/2026 às 10:45", "BEN001", "2", "R$ 450,50", "Confirmada"])
    expect(
      within(segunda)
        .getAllByRole("cell")
        .map((c) => c.textContent)
    ).toEqual(["06/11/2026 às 09:30", "BEN002", "1", "R$ 1.500,00", "Pendente"])
  })

  it("sai do histórico para uma nova portabilidade pelo cabeçalho ou pela marca", async () => {
    historicoResponde(confirmada)
    const { user, router } = renderApp("/historico")
    await screen.findByRole("cell", { name: "BEN001" })

    await user.click(screen.getByRole("link", { name: "Nova portabilidade" }))
    expect(router.state.location.pathname).toBe("/")

    await router.navigate({ to: "/historico" })
    await screen.findByRole("cell", { name: "BEN001" })
    await user.click(
      within(screen.getByRole("banner")).getByRole("link", {
        name: /Portabilidade de convênios/,
      })
    )
    expect(router.state.location.pathname).toBe("/")
  })

  it("leva à operação a partir da linha", async () => {
    historicoResponde(confirmada, pendente)
    const { user, router } = renderApp("/historico")
    server.use(http.get("/api/operacoes-portabilidade/:id", naoEncontrado))

    await user.click(
      await screen.findByRole("link", { name: /06\/11\/2026 às 09:30/ })
    )

    expect(router.state.location.pathname).toBe(
      `/operacoes-portabilidade/${pendente.id}`
    )
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Fora deste teste."
    )
  })

  it("sem operações, oferece começar uma nova portabilidade", async () => {
    historicoResponde()
    const { user, router } = renderApp("/historico")

    expect(
      await screen.findByText("Nenhuma operação registrada ainda.")
    ).toBeVisible()
    await user.click(screen.getByRole("link", { name: "Nova portabilidade" }))

    expect(router.state.location.pathname).toBe("/")
  })

  it("quando a lista não carrega, mostra o erro e tenta novamente", async () => {
    server.use(
      http.get("/api/operacoes-portabilidade", () => HttpResponse.error(), {
        once: true,
      })
    )
    const { user } = renderApp("/historico")

    const alerta = await screen.findByRole("alert")
    expect(alerta).toHaveTextContent(
      "Não foi possível concluir a requisição. Tente novamente mais tarde."
    )
    historicoResponde(confirmada)
    await user.click(
      within(alerta).getByRole("button", { name: "Tentar novamente" })
    )

    expect(await screen.findByRole("cell", { name: "BEN001" })).toBeVisible()
  })
})
