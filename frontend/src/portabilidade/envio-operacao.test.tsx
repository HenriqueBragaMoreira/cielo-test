import { screen, within } from "@testing-library/react"
import { delay, http, HttpResponse } from "msw"
import { describe, expect, it, vi } from "vitest"

import { renderApp } from "@/test/render-app"
import { server } from "@/test/server"

import type { Operacao } from "./api"

const agendaBen001 = "/api/beneficiarios/BEN001/agenda"
const operacoes = "/api/operacoes-portabilidade"

const agenda = [
  {
    parcelaId: "P001",
    convenioId: "CONV01",
    valor: 150.5,
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
    status: "ABERTA",
    elegivel: true,
    motivoInelegibilidade: null,
  },
  {
    parcelaId: "P003",
    convenioId: "CONV02",
    valor: 99,
    dataDesconto: "2026-03-10",
    status: "QUITADA",
    elegivel: false,
    motivoInelegibilidade:
      "A parcela está com status QUITADA; só parcelas ABERTA podem ser portadas.",
  },
]

const operacaoPendente: Operacao = {
  id: "8d0e7a52-3c1b-4f7e-9a39-2f4f0c1d9b11",
  beneficiarioId: "BEN001",
  status: "PENDENTE",
  protocolo: "prot-123",
  motivoRejeicao: null,
  parcelas: [
    { parcelaId: "P001", valor: 150.5 },
    { parcelaId: "P002", valor: 300 },
  ],
  contaDestino: { banco: "001", agencia: "1234", conta: "56789", digito: "0" },
  eventos: [
    { tipo: "SOLICITADA", dataHora: "2026-11-06T13:45:00Z", detalhe: null },
    { tipo: "ENVIADA", dataHora: "2026-11-06T13:46:00Z", detalhe: null },
  ],
  avisoAtualizacao: null,
}

describe("enviar uma operação de portabilidade a partir da agenda", () => {
  it("soma o valor das parcelas selecionadas", async () => {
    server.use(http.get(agendaBen001, () => HttpResponse.json(agenda)))
    const { user } = renderApp("/beneficiarios/BEN001/agenda")

    const total = await screen.findByText(/^Total selecionado:/)
    expect(total).toHaveTextContent(/^Total selecionado: R\$\s0,00$/)

    await user.click(selecao("P001"))
    await user.click(selecao("P002"))
    expect(total).toHaveTextContent(/^Total selecionado: R\$\s450,50$/)

    await user.click(selecao("P001"))
    expect(total).toHaveTextContent(/^Total selecionado: R\$\s300,00$/)
  })

  it("desabilita a seleção das parcelas inelegíveis e mostra o motivo", async () => {
    server.use(http.get(agendaBen001, () => HttpResponse.json(agenda)))
    renderApp("/beneficiarios/BEN001/agenda")

    const linha = await screen.findByRole("row", { name: /P003/ })
    expect(selecao("P003")).toHaveAttribute("aria-disabled", "true")
    expect(selecao("P001")).not.toHaveAttribute("aria-disabled", "true")
    expect(linha).toHaveTextContent(
      "Não: A parcela está com status QUITADA; só parcelas ABERTA podem ser portadas."
    )
  })

  it("desabilita a parcela que já está em operação ativa e mostra o motivo", async () => {
    const motivo =
      "A parcela já está numa operação de portabilidade pendente ou confirmada."
    server.use(
      http.get(agendaBen001, () =>
        HttpResponse.json([
          agenda[0],
          { ...agenda[1], elegivel: false, motivoInelegibilidade: motivo },
        ])
      )
    )
    renderApp("/beneficiarios/BEN001/agenda")

    const linha = await screen.findByRole("row", { name: /P002/ })
    expect(selecao("P002")).toHaveAttribute("aria-disabled", "true")
    expect(selecao("P001")).not.toHaveAttribute("aria-disabled", "true")
    expect(linha).toHaveTextContent(`ABERTANão: ${motivo}`)
  })

  it("valida a conta e exige uma parcela antes de enviar", async () => {
    const envio = vi.fn()
    server.use(
      http.get(agendaBen001, () => HttpResponse.json(agenda)),
      http.post(operacoes, () => {
        envio()
        return delay("infinite")
      })
    )
    const { user } = renderApp("/beneficiarios/BEN001/agenda")

    await user.type(await screen.findByLabelText("Banco"), "12")
    await user.type(screen.getByLabelText("Agência"), "12a4")
    await user.type(screen.getByLabelText("Conta"), "56-789")
    await user.click(botaoEnviar())

    expect(
      await screen.findByText("Selecione ao menos uma parcela.")
    ).toBeVisible()
    expect(screen.getByText("Informe o banco com 3 dígitos.")).toBeVisible()
    expect(screen.getByText("Informe a agência com 4 dígitos.")).toBeVisible()
    expect(screen.getByText("Informe a conta só com números.")).toBeVisible()
    expect(screen.getByText("Informe o dígito da conta.")).toBeVisible()
    expect(screen.getByLabelText("Banco")).toHaveAttribute(
      "aria-invalid",
      "true"
    )
    expect(envio).not.toHaveBeenCalled()

    await user.click(selecao("P001"))
    expect(
      screen.queryByText("Selecione ao menos uma parcela.")
    ).not.toBeInTheDocument()
  })

  it("desabilita o botão durante o envio", async () => {
    server.use(
      http.get(agendaBen001, () => HttpResponse.json(agenda)),
      http.post(operacoes, () => delay("infinite"))
    )
    const { user } = renderApp("/beneficiarios/BEN001/agenda")

    await selecionarPreencherEEnviar(user)

    await vi.waitFor(() => expect(botaoEnviar()).toBeDisabled())
  })

  it("envia o pedido com a chave de idempotência e mostra a operação pendente", async () => {
    let enviado: unknown
    let chave: string | null = null
    server.use(
      http.get(agendaBen001, () => HttpResponse.json(agenda)),
      http.post(operacoes, async ({ request }) => {
        enviado = await request.json()
        chave = request.headers.get("Idempotency-Key")
        return HttpResponse.json(operacaoPendente, { status: 202 })
      }),
      http.get(`${operacoes}/${operacaoPendente.id}`, () =>
        HttpResponse.json(operacaoPendente)
      )
    )
    const { user, router } = renderApp("/beneficiarios/BEN001/agenda")

    await selecionarPreencherEEnviar(user)

    expect(
      await screen.findByRole("heading", { name: "Operação de portabilidade" })
    ).toBeVisible()
    expect(router.state.location.pathname).toBe(
      `/operacoes-portabilidade/${operacaoPendente.id}`
    )
    expect(enviado).toEqual({
      beneficiarioId: "BEN001",
      parcelaIds: ["P001", "P002"],
      contaDestino: {
        banco: "001",
        agencia: "1234",
        conta: "56789",
        digito: "0",
      },
    })
    expect(chave).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
    )

    expect(
      await screen.findByText("Aguardando a decisão da Central de Convênios…")
    ).toBeVisible()
    expect(screen.getByText("prot-123")).toBeVisible()
    expect(
      screen.getByText("Banco 001 · Agência 1234 · Conta 56789-0")
    ).toBeVisible()
    const parcelas = screen.getByRole("list", { name: "Parcelas" })
    expect(parcelas).toHaveTextContent(/P001R\$\s150,50/)
    expect(parcelas).toHaveTextContent(/P002R\$\s300,00/)
    const linhaDoTempo = within(
      screen.getByRole("list", { name: "Linha do tempo" })
    ).getAllByRole("listitem")
    expect(linhaDoTempo).toHaveLength(2)
    expect(linhaDoTempo[0]).toHaveTextContent("Solicitada06/11/2026 às 10:45")
    expect(linhaDoTempo[1]).toHaveTextContent("Enviada06/11/2026 às 10:46")
  })

  it("mostra a mensagem do erro do envio e mantém o formulário preenchido", async () => {
    server.use(
      http.get(agendaBen001, () => HttpResponse.json(agenda)),
      http.post(operacoes, () =>
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
    const { user } = renderApp("/beneficiarios/BEN001/agenda")

    await selecionarPreencherEEnviar(user)

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "A Central de Convênios não respondeu. Tente novamente mais tarde."
    )
    expect(screen.getByLabelText("Banco")).toHaveValue("001")
    expect(selecao("P001")).toBeChecked()
    expect(botaoEnviar()).toBeEnabled()
  })

  it('FALHA_ENVIO mostra a mensagem e "Tentar novamente", que reenvia com a mesma Idempotency-Key', async () => {
    const detalhe =
      "A Central de Convênios não respondeu e a operação ficou registrada como falha no envio. Tente novamente."
    const chaves: (string | null)[] = []
    server.use(
      http.get(agendaBen001, () => HttpResponse.json(agenda)),
      http.post(operacoes, ({ request }) => {
        chaves.push(request.headers.get("Idempotency-Key"))
        return chaves.length === 1
          ? HttpResponse.json(
              {
                type: "about:blank",
                title: "Central de Convênios indisponível",
                status: 503,
                detail: detalhe,
                id: operacaoPendente.id,
              },
              {
                status: 503,
                headers: { "Content-Type": "application/problem+json" },
              }
            )
          : HttpResponse.json(operacaoPendente, { status: 202 })
      }),
      http.get(`${operacoes}/${operacaoPendente.id}`, () =>
        HttpResponse.json(operacaoPendente)
      )
    )
    const { user, router } = renderApp("/beneficiarios/BEN001/agenda")

    await selecionarPreencherEEnviar(user)

    const alerta = await screen.findByRole("alert")
    expect(alerta).toHaveTextContent(detalhe)
    expect(
      within(alerta).getByRole("link", { name: "Ver a operação" })
    ).toHaveAttribute("href", `/operacoes-portabilidade/${operacaoPendente.id}`)

    await user.click(
      within(alerta).getByRole("button", { name: "Tentar novamente" })
    )

    expect(
      await screen.findByRole("heading", { name: "Operação de portabilidade" })
    ).toBeVisible()
    expect(router.state.location.pathname).toBe(
      `/operacoes-portabilidade/${operacaoPendente.id}`
    )
    expect(chaves).toHaveLength(2)
    expect(chaves[1]).toBe(chaves[0])
  })

  it("reutiliza a mesma Idempotency-Key ao reenviar e gera outra quando a seleção ou a conta mudam", async () => {
    const chaves = capturarChavesComFalhaDeConexao()
    const { user } = renderApp("/beneficiarios/BEN001/agenda")

    await selecionarPreencherEEnviar(user)
    await screen.findByRole("alert")
    await user.click(botaoEnviar())
    await vi.waitFor(() => expect(chaves).toHaveLength(2))
    expect(chaves[1]).toBe(chaves[0])

    await user.click(selecao("P002"))
    await user.click(botaoEnviar())
    await vi.waitFor(() => expect(chaves).toHaveLength(3))
    expect(chaves[2]).not.toBe(chaves[1])

    await user.click(botaoEnviar())
    await vi.waitFor(() => expect(chaves).toHaveLength(4))
    expect(chaves[3]).toBe(chaves[2])

    await user.clear(screen.getByLabelText("Conta"))
    await user.type(screen.getByLabelText("Conta"), "11111")
    await user.click(botaoEnviar())
    await vi.waitFor(() => expect(chaves).toHaveLength(5))
    expect(chaves[4]).not.toBe(chaves[3])
  })

  it("mostra uma mensagem clara para o 422 e usa uma chave nova no envio seguinte", async () => {
    const detalhe =
      "Este pedido de portabilidade difere do que já foi enviado com a mesma chave de idempotência (8d0e7a52-3c1b-4f7e-9a39-2f4f0c1d9b11). Confira as parcelas e a conta e envie novamente."
    const chaves: (string | null)[] = []
    server.use(
      http.get(agendaBen001, () => HttpResponse.json(agenda)),
      http.post(operacoes, ({ request }) => {
        chaves.push(request.headers.get("Idempotency-Key"))
        return HttpResponse.json(
          {
            type: "about:blank",
            title: "Chave de idempotência já usada",
            status: 422,
            detail: detalhe,
          },
          {
            status: 422,
            headers: { "Content-Type": "application/problem+json" },
          }
        )
      })
    )
    const { user } = renderApp("/beneficiarios/BEN001/agenda")

    await selecionarPreencherEEnviar(user)

    expect(await screen.findByRole("alert")).toHaveTextContent(detalhe)
    await user.click(botaoEnviar())
    await vi.waitFor(() => expect(chaves).toHaveLength(2))
    expect(chaves[1]).not.toBe(chaves[0])
  })

  it.each([
    {
      caso: "409 de parcela em outra operação",
      status: 409,
      title: "Parcela em outra operação",
      detail:
        "As parcelas [P001] já estão numa operação de portabilidade pendente ou confirmada. Retire-as da seleção e envie novamente.",
    },
    {
      caso: "422 de parcela inelegível",
      status: 422,
      title: "Parcela inelegível",
      detail:
        "Estas parcelas não podem mais ser portadas: P002 (A parcela está com status QUITADA; só parcelas ABERTA podem ser portadas.). Atualize a agenda e envie novamente.",
    },
    {
      caso: "422 de parcela fora da agenda",
      status: 422,
      title: "Parcela fora da agenda",
      detail: "As parcelas [P002] não estão na agenda do beneficiário BEN001.",
    },
  ])(
    'mostra uma mensagem clara para o $caso, sem oferecer "Tentar novamente"',
    async ({ status, title, detail }) => {
      server.use(
        http.get(agendaBen001, () => HttpResponse.json(agenda)),
        http.post(operacoes, () =>
          HttpResponse.json(
            { type: "about:blank", title, status, detail },
            {
              status,
              headers: { "Content-Type": "application/problem+json" },
            }
          )
        )
      )
      const { user } = renderApp("/beneficiarios/BEN001/agenda")

      await selecionarPreencherEEnviar(user)

      const alerta = await screen.findByRole("alert")
      expect(alerta).toHaveTextContent(detail)
      expect(
        within(alerta).queryByRole("button", { name: "Tentar novamente" })
      ).not.toBeInTheDocument()
      expect(selecao("P001")).toBeChecked()
      expect(botaoEnviar()).toBeEnabled()
    }
  )

  it.each([
    {
      caso: "fica inelegível",
      status: 409,
      title: "Parcela em outra operação",
      agendaAtualizada: [
        agenda[0],
        {
          ...agenda[1],
          elegivel: false,
          motivoInelegibilidade:
            "A parcela já está numa operação de portabilidade pendente ou confirmada.",
        },
      ],
    },
    {
      caso: "sai da agenda",
      status: 422,
      title: "Parcela fora da agenda",
      agendaAtualizada: [agenda[0]],
    },
  ])(
    "depois do $status, atualiza a agenda e tira da seleção e do total a parcela que $caso",
    async ({ status, title, agendaAtualizada }) => {
      const enviados: unknown[] = []
      server.use(
        http.get(agendaBen001, () =>
          HttpResponse.json(enviados.length ? agendaAtualizada : agenda)
        ),
        http.post(operacoes, async ({ request }) => {
          enviados.push(await request.json())
          return HttpResponse.json(
            { type: "about:blank", title, status, detail: title },
            {
              status,
              headers: { "Content-Type": "application/problem+json" },
            }
          )
        })
      )
      const { user } = renderApp("/beneficiarios/BEN001/agenda")

      await selecionarPreencherEEnviar(user)

      await vi.waitFor(() =>
        expect(screen.getByText(/^Total selecionado:/)).toHaveTextContent(
          /^Total selecionado: R\$\s150,50$/
        )
      )
      expect(
        screen.queryByRole("checkbox", { name: /P002/, checked: true })
      ).not.toBeInTheDocument()
      await user.click(botaoEnviar())
      await vi.waitFor(() => expect(enviados).toHaveLength(2))
      expect(enviados[1]).toMatchObject({ parcelaIds: ["P001"] })
    }
  )
})

/** Toda tentativa de envio falha na conexão; devolve as `Idempotency-Key` recebidas, na ordem. */
function capturarChavesComFalhaDeConexao() {
  const chaves: (string | null)[] = []
  server.use(
    http.get(agendaBen001, () => HttpResponse.json(agenda)),
    http.post(operacoes, ({ request }) => {
      chaves.push(request.headers.get("Idempotency-Key"))
      return HttpResponse.error()
    })
  )
  return chaves
}

function selecao(parcelaId: string) {
  return screen.getByRole("checkbox", {
    name: `Selecionar a parcela ${parcelaId}`,
  })
}

function botaoEnviar() {
  return screen.getByRole("button", { name: "Enviar portabilidade" })
}

async function selecionarPreencherEEnviar(
  user: ReturnType<typeof renderApp>["user"]
) {
  await screen.findByRole("row", { name: /P001/ })
  await user.click(selecao("P001"))
  await user.click(selecao("P002"))
  await user.type(screen.getByLabelText("Banco"), "001")
  await user.type(screen.getByLabelText("Agência"), "1234")
  await user.type(screen.getByLabelText("Conta"), "56789")
  await user.type(screen.getByLabelText("Dígito"), "0")
  await user.click(botaoEnviar())
}
