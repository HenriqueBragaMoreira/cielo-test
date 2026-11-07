import { expect, test } from "@playwright/test"

test("duplo clique no envio cria uma única operação de portabilidade", async ({
  page,
}) => {
  const idsRespondidos: Promise<string>[] = []
  page.on("response", (resposta) => {
    if (
      resposta.request().method() === "POST" &&
      new URL(resposta.url()).pathname === "/api/operacoes-portabilidade"
    ) {
      idsRespondidos.push(
        resposta.json().then((operacao: { id: string }) => operacao.id)
      )
    }
  })

  await page.goto("/")
  await page.getByLabel("Identificador do beneficiário").fill("BEN001")
  await page
    .getByRole("checkbox", {
      name: "O beneficiário autoriza a consulta da agenda e a portabilidade das parcelas",
    })
    .check()
  await page.getByRole("button", { name: "Registrar autorização" }).click()
  await expect(page).toHaveURL("/beneficiarios/BEN001/agenda")

  // só a P002, para não disputar a P001 com o cenário de confirmação do BEN001
  await page
    .getByRole("checkbox", { name: "Selecionar a parcela P002" })
    .check()
  await page.getByLabel("Banco").fill("001")
  await page.getByLabel("Agência").fill("1234")
  await page.getByLabel("Conta").fill("56789")
  await page.getByLabel("Dígito").fill("0")
  await page.getByRole("button", { name: "Enviar portabilidade" }).dblclick()

  await expect(page).toHaveURL(/\/operacoes-portabilidade\/[0-9a-f-]{36}$/)
  // espera algum segundo envio ainda em andamento terminar
  await page.waitForLoadState("networkidle")
  const id = new URL(page.url()).pathname.split("/").pop()
  expect(idsRespondidos.length).toBeGreaterThan(0)
  expect(new Set(await Promise.all(idsRespondidos))).toEqual(new Set([id]))
})
