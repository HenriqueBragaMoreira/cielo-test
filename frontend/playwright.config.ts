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
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
})
