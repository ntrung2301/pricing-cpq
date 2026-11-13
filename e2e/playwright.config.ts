import { defineConfig, devices } from '@playwright/test'

// S1-T8 walking-skeleton E2E proof: a real, no-mock login -> real-data
// test against the actual stack (Keycloak + Postgres + Spring Boot app +
// Vite dev server), started manually before running this suite (see
// docs/decisions.md). Deliberately NOT using Playwright's `webServer`
// auto-start here, because the backend/Keycloak/Postgres lifecycle is
// documented and run as explicit steps in this task's report, not
// something to hide inside the test runner.
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
