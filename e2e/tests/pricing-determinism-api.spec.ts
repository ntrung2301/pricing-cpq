import { expect, test } from '@playwright/test'

// S2-T14 (docs/sprints/sprint-2.md) QA sign-off, gap #1: core-pricing
// (S2-T10/D-023) already has unit tests that run the in-process
// `RuleEngine` 10 times and assert bit-for-bit identical output
// (`RuleEngineTest.sameInputAlwaysProducesSameOutputAcrossTenRuns`,
// `HybridRuleEngineTest.sameHybridRuleSetAndContextProduceIdenticalResultAcrossTenReruns`),
// and `app`'s `PricingCalculationServiceIT.sameRequestProducesIdenticalResponseAcrossTenCalls`
// repeats that at the service layer with a real Postgres (Testcontainers).
// None of those go over a real HTTP boundary against the actually-running
// app (`./gradlew :app:bootRun`) — this test closes that gap: it gets a
// real Keycloak access token (ROPC against the `cpq-backend` client, same
// mechanism documented in D-017/D-023) and calls `POST
// /api/pricing/calculate` twice over a real socket, asserting the two
// responses are identical (every `steps[*].deltaAmount`/`resultingAmount`
// and `finalAmount`), not just structurally similar.
//
// Uses the real seeded automotive data (D-031/D-034, same fixture as
// e2e/tests/vehicle-part-selection.spec.ts's pricing assertion):
// `BRK-PAD-NEW-001` (list price 55.00) priced for Customer `CUST-OEM-001`
// (tier OEM, -15%) under `ruleSetCode=AUTOMOTIVE-STANDARD`/
// `priceListCode=AUTOMOTIVE-DEFAULT` -> final 46.75. The exact numbers are
// asserted too (not just "both calls equal") so a regression that is
// deterministic but wrong would still be caught.
//
// Run against the real stack (same manual startup as the other specs in
// this directory — see docs/decisions.md): `docker compose up -d postgres
// keycloak`, `./gradlew :app:bootRun`, then `npx playwright test
// pricing-determinism-api`.

const API_BASE_URL = process.env.E2E_API_BASE_URL ?? 'http://localhost:8080'
const KEYCLOAK_BASE_URL = process.env.E2E_KEYCLOAK_BASE_URL ?? 'http://localhost:8089'

test('POST /api/pricing/calculate called twice with the same request against the real running app returns identical waterfalls', async ({
  request,
}) => {
  // 1. Real Keycloak token, Resource Owner Password Credentials grant
  // against the `cpq-backend` client (directAccessGrantsEnabled=true,
  // documented for manual backend verification in D-017/D-023) — not
  // mocked, not a test JWT builder.
  const tokenResponse = await request.post(
    `${KEYCLOAK_BASE_URL}/realms/cpq/protocol/openid-connect/token`,
    {
      form: {
        grant_type: 'password',
        client_id: 'cpq-backend',
        username: 'pricing-admin@example.com',
        password: 'pricing-admin-local-dev',
      },
    },
  )
  expect(tokenResponse.ok(), 'Keycloak ROPC token request must succeed').toBeTruthy()
  const { access_token: accessToken } = await tokenResponse.json()

  const authHeaders = { Authorization: `Bearer ${accessToken}` }

  // 2. Resolve real ids the same way the real UI does (D-033/D-030): the
  // Product entity_def id, the BRK-PAD-NEW-001 instance id, and the
  // CUST-OEM-001 Customer instance id. No hardcoded ids — if seed data
  // ever changes shape, this test re-resolves them live.
  const entitiesResponse = await request.get(`${API_BASE_URL}/api/entities`, {
    headers: authHeaders,
  })
  expect(entitiesResponse.ok()).toBeTruthy()
  const entityDefs: Array<{ id: number; code: string }> = await entitiesResponse.json()
  const productDef = entityDefs.find((e) => e.code === 'Product')
  expect(productDef, 'Product entity_def must exist (S2-T3 seed)').toBeTruthy()

  const productInstanceResponse = await request.get(
    `${API_BASE_URL}/api/catalog/Product/instances?search=BRK-PAD-NEW-001`,
    { headers: authHeaders },
  )
  expect(productInstanceResponse.ok()).toBeTruthy()
  const productPage = await productInstanceResponse.json()
  const productInstance = productPage.content.find(
    (i: { businessKey: string }) => i.businessKey === 'BRK-PAD-NEW-001',
  )
  expect(productInstance, 'BRK-PAD-NEW-001 instance must exist (S2-T6 seed)').toBeTruthy()

  const customerInstanceResponse = await request.get(
    `${API_BASE_URL}/api/catalog/Customer/instances?search=CUST-OEM-001`,
    { headers: authHeaders },
  )
  expect(customerInstanceResponse.ok()).toBeTruthy()
  const customerPage = await customerInstanceResponse.json()
  const customerInstance = customerPage.content.find(
    (i: { businessKey: string }) => i.businessKey === 'CUST-OEM-001',
  )
  expect(customerInstance, 'CUST-OEM-001 instance must exist (S2-T6 seed)').toBeTruthy()

  // 3. Same request, fired twice at the real running app over a real
  // HTTP connection.
  const requestBody = {
    ruleSetCode: 'AUTOMOTIVE-STANDARD',
    priceListCode: 'AUTOMOTIVE-DEFAULT',
    entityDefId: productDef!.id,
    entityInstanceId: productInstance.id,
    quantity: 1,
    attributes: {},
    customerInstanceId: customerInstance.id,
    contractOverridePrice: null,
  }

  const firstResponse = await request.post(`${API_BASE_URL}/api/pricing/calculate`, {
    headers: authHeaders,
    data: requestBody,
  })
  expect(firstResponse.ok(), 'first /api/pricing/calculate call must succeed').toBeTruthy()
  const first = await firstResponse.json()

  const secondResponse = await request.post(`${API_BASE_URL}/api/pricing/calculate`, {
    headers: authHeaders,
    data: requestBody,
  })
  expect(secondResponse.ok(), 'second /api/pricing/calculate call must succeed').toBeTruthy()
  const second = await secondResponse.json()

  // Known-correct numbers (verified live via curl before writing this
  // assertion, same fixture as the S2-T13 Playwright spec): 55.00 list
  // price, OEM tier -15% => -8.25, final 46.75.
  expect(first.finalAmount).toBe(46.75)
  expect(second.finalAmount).toBe(46.75)

  // The actual determinism assertion: every field of every waterfall
  // step, and the final amount, byte-for-byte identical across the two
  // real HTTP calls.
  expect(second).toStrictEqual(first)
})
