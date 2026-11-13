import { expect, test } from '@playwright/test'

// S1-T8 Definition of Done, verbatim: "Chạy local, login -> thấy dữ liệu
// thật từ Postgres trên UI, không mock." This test exercises the REAL
// stack end to end — real Keycloak login form (Authorization Code +
// PKCE, client `cpq-web`). Nothing in this test is mocked.
//
// Updated for S2-T9 (docs/decisions.md): the authenticated home screen
// is now `VehiclePartSelectionScreen` (real `GET
// /api/catalog/{entityDefCode}/instances` data, D-030) — it replaced the
// original S1-T8 "Entity definitions" screen (`GET /api/entities`
// metadata listing), which is no longer mounted in `App.tsx`. This test
// now only proves the login flow itself still works end to end; the
// deeper "real catalog/fitment data on screen" proof now lives in
// e2e/tests/vehicle-part-selection.spec.ts.
test('login via Keycloak and land on the authenticated vehicle/part selection screen', async ({
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
  await expect(page.getByRole('heading', { name: /chọn xe \/ chọn part/i })).toBeVisible({
    timeout: 15_000,
  })
})
