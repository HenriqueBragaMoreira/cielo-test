import { expect, test } from "@playwright/test"

test("autoriza o BEN001 e vê as 2 parcelas dele na agenda", async ({
  page,
}) => {
  await page.goto("/")

  await page.getByLabel("Identificador do beneficiário").fill("BEN001")
  await page
    .getByRole("checkbox", {
      name: "O beneficiário autoriza a consulta da agenda e a portabilidade das parcelas",
    })
    .check()
  await page.getByRole("button", { name: "Registrar autorização" }).click()

  await expect(page).toHaveURL("/beneficiarios/BEN001/agenda")
  await expect(
    page.getByRole("heading", { name: "Beneficiário BEN001" })
  ).toBeVisible()
  const linhas = page.getByRole("table").getByRole("row")
  await expect(linhas).toHaveCount(3) // cabeçalho + 2 parcelas
  await expect(page.getByRole("row", { name: /P001/ })).toContainText(
    /CONV01.*10\/01\/2026.*R\$\s150,00.*Aberta · elegível/
  )
  await expect(page.getByRole("row", { name: /P002/ })).toContainText(
    /CONV01.*10\/02\/2026.*R\$\s150,00.*Aberta · elegível/
  )
})
