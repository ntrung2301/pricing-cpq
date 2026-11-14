import { expect, test } from '@playwright/test'

// S2-T9 (docs/sprints/sprint-2.md) Definition of Done, verbatim: "Chạy
// local, chọn xe thật từ dữ liệu S2-T6/S2-T7 -> thấy đúng part tương
// thích trên UI, không mock." Exercises the REAL stack end to end: real
// Keycloak login (same flow as e2e/tests/walking-skeleton.spec.ts), real
// `GET /api/catalog/Vehicle/instances` + `GET /api/catalog/Product/instances`
// + `GET /api/configurator/compatible` calls against the real Postgres
// data seeded by `DemoDataSeedRunner` (S2-T6) + `AutomotiveFitmentSeedRunner`
// (S2-T7). Nothing in this test is mocked.
//
// Fitment fixture (AutomotiveFitmentSeedRunner.FITMENT_PLAN, vehicles.csv
// row 0 = "Toyota Camry 2018"): compatible with
// BRK-PAD-NEW-001/BRK-ROTOR-NEW-001/OIL-FLT-NEW-010/AIR-FLT-001 only —
// BRK-PAD-OLD-001 (Silverado's fitment, row 3) must NOT show up for Camry.
//
// Extended for S2-T13 (docs/sprints/sprint-2.md): continues the same
// journey past "Chọn part này" into the real price waterfall —
// `AutomotivePricingSeedRunner` (D-031) seeds price_list_item amount =
// `50.00 + 5.00 * index` for each Product (CSV row order); BRK-PAD-NEW-001
// is CSV row index 1, so list price = 55.00. Customer CUST-OEM-001 (tier
// OEM) has rule_set `AUTOMOTIVE-STANDARD`'s "OEM tier discount" (-15%):
// 55.00 - 8.25 = 46.75 final — verified live against the real backend
// (`POST /api/pricing/calculate`) before writing this assertion, not
// guessed.
test('login, choose a real seeded Vehicle, find compatible Products, pick a Customer, and see the real price waterfall', async ({
  page,
}) => {
  await page.goto('/')

  const loginButton = page.getByRole('button', { name: /login with keycloak/i })
  await expect(loginButton).toBeVisible({ timeout: 15_000 })
  await loginButton.click()

  await page.waitForURL(/realms\/cpq\/protocol\/openid-connect\/auth/, { timeout: 15_000 })
  await page.locator('#username').fill('pricing-admin@example.com')
  await page.locator('#password').fill('pricing-admin-local-dev')
  await page.locator('#kc-login').click()

  await page.waitForURL(/localhost:5173\/?/, { timeout: 15_000 })
  await expect(page.getByRole('heading', { name: /chọn xe \/ chọn part/i })).toBeVisible({
    timeout: 15_000,
  })

  // Default state, no vehicle chosen yet: the full real Product catalog
  // (30 seeded rows, S2-T6) is shown, including a part that only ever
  // appears via "Bỏ qua chọn xe" / default view, never via the Camry
  // fitment filter below. `.first()` because the generic attribute table
  // (docs/decisions.md) also renders the raw `sku` attribute column, so
  // the SKU text legitimately appears twice in the same row (businessKey
  // column + attribute column) — not a bug, just redundant real data.
  await expect(page.getByText('SPK-PLUG-001', { exact: true }).first()).toBeVisible({
    timeout: 15_000,
  })

  // Pick the real seeded Camry (vehicles.csv row 0) from the dropdown.
  await page.getByRole('combobox', { name: /chọn xe/i }).click()
  await page.getByRole('option', { name: /toyota camry 2018/i }).click()

  await page.getByRole('button', { name: /tìm part tương thích/i }).click()

  // Real fitment data for the Camry (AutomotiveFitmentSeedRunner.FITMENT_PLAN[0]).
  await expect(page.getByText('BRK-PAD-NEW-001', { exact: true }).first()).toBeVisible({
    timeout: 15_000,
  })
  await expect(page.getByText('BRK-ROTOR-NEW-001', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('OIL-FLT-NEW-010', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('AIR-FLT-001', { exact: true }).first()).toBeVisible()

  // Not compatible with the Camry (that's the Silverado's fitment, row 3) —
  // proves this is real filtered fitment data, not the unfiltered catalog.
  await expect(page.getByText('BRK-PAD-OLD-001', { exact: true })).toHaveCount(0)
  await expect(page.getByText('SPK-PLUG-001', { exact: true })).toHaveCount(0)

  // "Chọn part này" on a real compatible part highlights it + confirms.
  const brakeRow = page.locator('tr').filter({ hasText: 'BRK-PAD-NEW-001' })
  await brakeRow.getByRole('button', { name: /chọn part này/i }).click()
  await expect(page.getByText(/đã chọn:/i)).toBeVisible()

  // S2-T13: the price waterfall screen mounts right below, nối tiếp "Chọn
  // part này" (no separate route). Pick the real seeded OEM customer.
  await expect(page.getByRole('combobox', { name: /khách hàng/i })).toBeVisible({
    timeout: 15_000,
  })
  await page.getByRole('combobox', { name: /khách hàng/i }).click()
  await page.getByRole('option', { name: /acme motors oem division/i }).click()

  // Real waterfall from the real backend (POST /api/pricing/calculate):
  // list price 55.00 -> OEM tier discount -8.25 -> final 46.75.
  await expect(page.getByRole('table', { name: /price waterfall/i })).toBeVisible({
    timeout: 15_000,
  })
  await expect(page.getByText('List price')).toBeVisible()
  await expect(page.getByText(/oem tier discount/i)).toBeVisible()
  const waterfallTable = page.getByRole('table', { name: /price waterfall/i })
  await expect(waterfallTable).toContainText('55')
  await expect(waterfallTable).toContainText('46.75')
  await expect(page.getByText(/giá cuối: 46\.75/i)).toBeVisible()
})
