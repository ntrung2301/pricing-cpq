import { expect, test, type Page } from '@playwright/test'

// S3-T8 (docs/sprints/sprint-3.md, backlog E8-06) Done-khi, verbatim:
// "Dựng 1 Price List thật qua UI thật (không mock), xem trace, approve —
// Playwright E2E thật, không mock." Exercises the real stack end to end:
// real Keycloak login (same flow as e2e/tests/vehicle-part-selection.spec.ts),
// the standalone `measure-canvas.html` entry (docs/decisions.md D-049),
// and the real S3-T6/S3-T8 `/api/price-lists-v2/...` backend — nothing
// here is mocked.
//
// Builds a brand-new Price List by dragging palette chips onto the
// canvas (a Data source node + a 2-step formula/round numeric chain + an
// Output node), creates it, adds the real seeded Product
// "OIL-FLT-NEW-010" (PricingTaxonomyDemoSeedRunner, S3-T4/S3-T7) as an
// item, reads its real Calculation Trace back via the new
// `GET /api/price-lists-v2/{id}` endpoint, submits, and approves.
//
// Expected numbers (plain arithmetic, verified against RoundNodeExecutor's
// CEILING-to-nearest-multiple rule, core-pricing D-039): base cost literal
// 50 -> markup x1.3 = 65 -> round to nearest 1 = 65 (already a whole
// number, so rounding is a no-op here — chosen deliberately so the
// expected value is unambiguous).

async function dragPaletteChipToCanvas(page: Page, chipLabel: string) {
  const chip = page.getByRole('button', { name: `Drag ${chipLabel}` })
  const canvas = page.getByTestId('measure-canvas-flow')
  await chip.dragTo(canvas)
}

test('build a new Price List via drag-and-drop, add a real Product, view the real trace, submit and approve', async ({
  page,
}) => {
  await page.goto('/measure-canvas.html')

  const loginButton = page.getByRole('button', { name: /login with keycloak/i })
  await expect(loginButton).toBeVisible({ timeout: 15_000 })
  await loginButton.click()

  await page.waitForURL(/realms\/cpq\/protocol\/openid-connect\/auth/, { timeout: 15_000 })
  await page.locator('#username').fill('pricing-admin@example.com')
  await page.locator('#password').fill('pricing-admin-local-dev')
  await page.locator('#kc-login').click()

  await page.waitForURL(/localhost:5173\/measure-canvas\.html/, { timeout: 15_000 })
  await expect(page.getByRole('heading', { name: 'Measure Canvas' })).toBeVisible({ timeout: 15_000 })

  // --- Context switcher: pick a real seeded Product (S2-T6/S3-T4). The
  // Select shows the Product's displayLabel, not its raw SKU/businessKey
  // (EntityInstanceSummary, web/src/catalog/types.ts) — "Oil Filter
  // Spin-On" is SKU OIL-FLT-NEW-010 per DemoDataSeedRunner's seed data.
  // `.pressSequentially` (real keystrokes), not `.fill` — antd's Select
  // search box is a React-controlled input; a raw `.fill()` DOM mutation
  // fights the controlled re-render and the dropdown never settles.
  await page.getByLabel(/context.*product/i).click()
  await page.getByLabel(/context.*product/i).pressSequentially('Oil Filter Spin-On')
  await page.getByRole('option', { name: 'Oil Filter Spin-On', exact: true }).click()

  // --- Drop a Data source node (query, PRODUCT) ---
  await dragPaletteChipToCanvas(page, 'Product — Country of Manufacture')
  await expect(page.getByText('Configure source node')).toBeVisible()
  await page.getByLabel('Label').fill('Country')
  await page.getByRole('button', { name: 'OK' }).click()
  await expect(page.getByText('Country', { exact: true })).toBeVisible()

  // --- Drop a Formula node: base cost = 0 + 50 (both literals) ---
  await dragPaletteChipToCanvas(page, 'formula')
  await expect(page.getByText('Configure formula node')).toBeVisible()
  await page.getByLabel('Label').fill('Base cost')
  const operandBGroup = page.locator('.ant-form-item', { hasText: 'Operand B' })
  await operandBGroup.getByRole('spinbutton').fill('50')
  await page.getByRole('button', { name: 'OK' }).click()
  await expect(page.getByText('Base cost', { exact: true })).toBeVisible()

  // --- Drop a second Formula node: markup = Base cost * 1.3 ---
  await dragPaletteChipToCanvas(page, 'formula')
  await expect(page.getByText('Configure formula node')).toBeVisible()
  await page.getByLabel('Label').fill('Markup')
  // antd's Radio.Button visually hides the real <input type="radio">
  // (0x0 box, styled via the sibling <span>) — Playwright's actionability
  // check on the role=radio element itself therefore never settles; click
  // the visible <label> text instead (same element a real user clicks).
  const operatorGroup = page.locator('.ant-form-item', { hasText: 'Operator' })
  await operatorGroup.getByText('×', { exact: true }).click()
  const operandAGroup = page.locator('.ant-form-item', { hasText: 'Operand A' })
  await operandAGroup.getByText('Node', { exact: true }).click()
  await operandAGroup.getByRole('combobox').click()
  await page.getByRole('option', { name: 'Base cost' }).click()
  const operandBGroup2 = page.locator('.ant-form-item', { hasText: 'Operand B' })
  await operandBGroup2.getByRole('spinbutton').fill('1.3')
  await page.getByRole('button', { name: 'OK' }).click()
  await expect(page.getByText('Markup', { exact: true })).toBeVisible()

  // --- Drop a Round node: round(Markup, nearest 1) ---
  await dragPaletteChipToCanvas(page, 'round')
  await expect(page.getByText('Configure round node')).toBeVisible()
  await page.getByLabel('Label').fill('Rounded price')
  await page.getByLabel('Nearest').fill('1')
  await page.getByLabel('Input node').click()
  await page.getByRole('option', { name: 'Markup' }).click()
  await page.getByRole('button', { name: 'OK' }).click()
  await expect(page.getByText('Rounded price', { exact: true })).toBeVisible()

  // --- Drop an Output node grouping Country + Rounded price for display ---
  await dragPaletteChipToCanvas(page, 'output')
  await expect(page.getByText('Configure output node')).toBeVisible()
  await page.getByLabel('Label').fill('Summary')
  await page.getByLabel('Fields to display').click()
  await page.getByRole('option', { name: 'Country' }).click()
  await page.getByRole('option', { name: 'Rounded price' }).click()
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'OK' }).click()
  await expect(page.getByText('Summary', { exact: true })).toBeVisible()

  // --- Create the Price List for real (POST /api/price-lists-v2) ---
  const code = `PLV2_E2E_${Date.now()}`
  await page.getByLabel('Code').fill(code)
  await page.getByLabel('Name').fill('E2E Measure Canvas Test')
  await page.getByRole('button', { name: 'Tạo Price List' }).click()
  await expect(page.getByText('DRAFT', { exact: true })).toBeVisible({ timeout: 15_000 })

  // --- Add the real Product as an item (POST .../items), then read the
  // real trace back (GET /api/price-lists-v2/{id}, this task's new
  // endpoint) ---
  await page.getByRole('button', { name: 'Thêm Product vào Price List' }).click()
  await expect(page.getByTestId('trace-result-price')).toHaveText('65', { timeout: 15_000 })
  await expect(page.getByTestId('trace-loaded-via-get')).toContainText('1 item(s)')
  await expect(page.getByTestId('trace-loaded-via-get')).toContainText('5 graph node(s)')

  // Calculation Trace panel shows every node that ran, not just the final
  // number (ADR-009 mục 2's "không bao giờ là hộp đen").
  await expect(page.getByTestId(/trace-row-f_/).first()).toBeVisible()
  await expect(page.getByTestId(/trace-row-r_/).first()).toBeVisible()

  // --- Expand/collapse sync between the canvas node and the trace row ---
  const roundNodeCard = page.locator('[data-testid^="canvas-node-r_"]').first()
  await roundNodeCard.click()
  await expect(roundNodeCard.locator('pre')).toBeVisible()

  // --- Submit then approve (real state machine transitions) ---
  await page.getByRole('button', { name: 'Submit' }).click()
  await expect(page.getByText('PENDING_APPROVAL', { exact: true })).toBeVisible({ timeout: 15_000 })

  await page.getByRole('button', { name: 'Approve' }).click()
  await expect(page.getByText('APPROVED', { exact: true })).toBeVisible({ timeout: 15_000 })
})
