import { defineConfig, devices } from "@playwright/test"

// Roda contra a stack do compose já de pé: docker compose up --build -d --wait (ADR-0010)
export default defineConfig({
  testDir: "./e2e",
  forbidOnly: !!process.env.CI,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: "http://localhost:8080",
    trace: "retain-on-failure",
  },
  // Um banco só para todos os specs, e uma parcela confirmada nunca mais é elegível (trava de parcela, ADR-0005).
  // Com só 3 parcelas no simulador, a ordem evita disputa: a agenda é lida antes de qualquer operação, e o duplo
  // clique porta a P010 do BEN002 depois que a rejeição a liberou.
  projects: [
    {
      name: "agenda",
      testMatch: "autorizacao-e-agenda.spec.ts",
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "acompanhamento",
      testMatch: "acompanhamento-da-operacao.spec.ts",
      dependencies: ["agenda"],
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "duplo-clique",
      testMatch: "envio-duplo-clique.spec.ts",
      dependencies: ["acompanhamento"],
      use: { ...devices["Desktop Chrome"] },
    },
  ],
})
