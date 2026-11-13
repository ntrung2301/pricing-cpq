import { expect, test } from '@playwright/test'

// S1-T8 Definition of Done, verbatim: "Chạy local, login -> thấy dữ liệu
// thật từ Postgres trên UI, không mock." This test exercises the REAL
// stack end to end — real Keycloak login form (Authorization Code +
// PKCE, client `cpq-web`), real backend `GET /api/entities` call, real
// Postgres row (the `SampleWidget` entity definition seeded by
// app/src/main/resources/db/migration/V3__seed_sample_entity_definition.sql).
// Nothing in this test is mocked.
test('login via Keycloak and see the real seeded SampleWidget entity from Postgres', async ({
  page,
}) => {
  await page.goto('/')

  // Unauthenticated state: real app redirects to the real Keycloak login page.
  const loginButton = page.getByRole('button', { name: /login with keycloak/i })
  await expect(loginButton).toBeVisible({ timeout: 15_000 })
  await loginButton.click()

  // Real Keycloak hosted login form (realm `cpq`, client `cpq-web`).
  await page.waitForURL(/realms\/cpq\/protocol\/openid-connect\/auth/, { timeout: 15_000 })
  await page.locator('#username').fill('pricing-admin@example.com')
  await page.locator('#password').fill('pricing-admin-local-dev')
  await page.locator('#kc-login').click()

  // Redirected back to the real app, now authenticated.
  await page.waitForURL(/localhost:5173\/?/, { timeout: 15_000 })
  await expect(page.getByRole('heading', { name: /entity definitions/i })).toBeVisible({
    timeout: 15_000,
  })

  // The real, Postgres-backed data from GET /api/entities — not mocked.
  const sampleWidgetRow = page.getByRole('row', { name: /SampleWidget/ })
  await expect(sampleWidgetRow).toBeVisible({ timeout: 15_000 })
  await expect(sampleWidgetRow).toContainText('Sample Widget')
})
