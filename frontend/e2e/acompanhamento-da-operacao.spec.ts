import { expect, type Page, test } from "@playwright/test"

// a Central simulada decide cerca de 5 s depois do envio
const ESPERA_PELA_DECISAO = 15_000

test("BEN001: envia a operação e acompanha até a confirmação", async ({
  page,
}) => {
  await autorizarESelecionar(page, "BEN001", ["P001", "P002"])
  await informarContaEEnviar(page)

  await expect(page).toHaveURL(/\/operacoes-portabilidade\/[0-9a-f-]{36}$/)
  await expect(
    page.getByText("Aguardando a decisão da Central de Convênios…")
  ).toBeVisible()

  await expect(page.getByRole("status")).toContainText("Operação confirmada", {
    timeout: ESPERA_PELA_DECISAO,
  })
  await expect(
    page.getByRole("list", { name: "Linha do tempo" }).getByRole("listitem")
  ).toContainText(["Solicitada", "Enviada", "Confirmada"])

  // a operação aparece no histórico com o status final
  const operacao = new URL(page.url()).pathname
  await page
    .getByRole("banner")
    .getByRole("link", { name: "Histórico" })
    .click()
  await expect(page).toHaveURL("/historico")
  const linha = page
    .getByRole("row")
    .filter({ has: page.locator(`a[href="${operacao}"]`) })
  await expect(linha).toContainText("BEN001")
  await expect(linha).toContainText("Confirmada")
})

test("BEN002: envia a operação e acompanha até a rejeição, com o motivo", async ({
  page,
}) => {
  await autorizarESelecionar(page, "BEN002", ["P010"])
  await informarContaEEnviar(page)

  await expect(
    page.getByText("Aguardando a decisão da Central de Convênios…")
  ).toBeVisible()

  const status = page.getByRole("status")
  await expect(status).toContainText("Operação rejeitada", {
    timeout: ESPERA_PELA_DECISAO,
  })
  await expect(status).toContainText(
    "Motivo: Beneficiário atingiu o limite de margem consignável"
  )
  await expect(
    page.getByRole("list", { name: "Linha do tempo" }).getByRole("listitem")
  ).toContainText(["Solicitada", "Enviada", "Rejeitada"])
})

async function autorizarESelecionar(
  page: Page,
  beneficiarioId: string,
  parcelaIds: string[]
) {
  await page.goto("/")
  await page.getByLabel("Identificador do beneficiário").fill(beneficiarioId)
  await page
    .getByRole("checkbox", {
      name: "O beneficiário autoriza a consulta da agenda e a portabilidade das parcelas",
    })
    .check()
  await page.getByRole("button", { name: "Registrar autorização" }).click()
  await expect(page).toHaveURL(`/beneficiarios/${beneficiarioId}/agenda`)

  for (const parcelaId of parcelaIds) {
    await page
      .getByRole("checkbox", { name: `Selecionar a parcela ${parcelaId}` })
      .check()
  }
}

async function informarContaEEnviar(page: Page) {
  await page.getByLabel("Banco").fill("001")
  await page.getByLabel("Agência").fill("1234")
  await page.getByLabel("Conta", { exact: true }).fill("56789")
  await page.getByLabel("Dígito").fill("0")
  await page.getByRole("button", { name: "Enviar portabilidade" }).click()
}
