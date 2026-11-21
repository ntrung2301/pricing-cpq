import { Alert, Input, Spin } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Button, Field, Select } from '../ui'
import { fetchInstances } from '../catalog/catalogApi'
import type { EntityInstanceSummary } from '../catalog/types'
import { fetchEntities } from '../entities/entitiesApi'
import { calculatePrice } from './pricingApi'

interface PriceWaterfallScreenProps {
  /** Real OIDC access token from the authenticated session (react-oidc-context). */
  accessToken: string
  /** The Product the Sales rep chose on the S2-T9 screen ("Chọn part này"). */
  product: EntityInstanceSummary
}

// [pack:automotive] This screen (docs/ux/03-pricing-waterfall.md) wires
// literal entity_def/price_list/rule_set codes the same way
// VehiclePartSelectionScreen (S2-T9, docs/decisions.md D-032 point 4)
// does — data references into pack.yaml-declared config (D-031's seed
// data), not hardcoded domain display strings in the generic waterfall
// table rendering below (which only knows "stepOrder/label/deltaAmount/
// resultingAmount", per core-api's WaterfallStepSummary).
const PRODUCT_ENTITY_DEF_CODE = 'Product'
const CUSTOMER_ENTITY_DEF_CODE = 'Customer'
const RULE_SET_CODE = 'AUTOMOTIVE-STANDARD'
const PRICE_LIST_CODE = 'AUTOMOTIVE-DEFAULT'

function customerLabel(customer: EntityInstanceSummary): string {
  return customer.displayLabel ?? customer.businessKey ?? `#${customer.id}`
}

/**
 * S2-T13 (docs/sprints/sprint-2.md) screen: "giá + waterfall", nối tiếp
 * từ "Chọn part này" của S2-T9 — per wireframe
 * docs/ux/03-pricing-waterfall.md, simplified per docs/decisions.md (no
 * quote/approval screen, single generic error banner instead of
 * distinguishing "no price list" vs other failures).
 *
 * Migrated from MUI to Ant Design + web/design.md tokens (D-050) — logic
 * and API calls unchanged. The waterfall grid itself stays a plain
 * semantic `<table>` (not the shared Ant `DataTable`) so it keeps a real
 * `role="table"` with an accessible name of "price waterfall", matching
 * the existing Playwright assertions — see docs/decisions.md D-050.
 */
export function PriceWaterfallScreen({ accessToken, product }: PriceWaterfallScreenProps) {
  const [selectedCustomerId, setSelectedCustomerId] = useState<number | null>(null)
  const [quantityInput, setQuantityInput] = useState('1')
  const [contractOverrideInput, setContractOverrideInput] = useState('')

  const entityDefsQuery = useQuery({
    queryKey: ['entities', accessToken],
    queryFn: () => fetchEntities(accessToken),
  })

  const customersQuery = useQuery({
    queryKey: ['catalog', CUSTOMER_ENTITY_DEF_CODE, accessToken],
    queryFn: () => fetchInstances(accessToken, CUSTOMER_ENTITY_DEF_CODE, { size: 100 }),
  })

  const productEntityDefId = useMemo(
    () => entityDefsQuery.data?.find((def) => def.code === PRODUCT_ENTITY_DEF_CODE)?.id ?? null,
    [entityDefsQuery.data],
  )

  const quantity = Number(quantityInput)
  const contractOverridePrice = contractOverrideInput.trim() === '' ? null : Number(contractOverrideInput)

  const priceQuery = useQuery({
    queryKey: [
      'pricing',
      'calculate',
      product.id,
      productEntityDefId,
      selectedCustomerId,
      quantity,
      contractOverridePrice,
      accessToken,
    ],
    queryFn: () =>
      calculatePrice(accessToken, {
        ruleSetCode: RULE_SET_CODE,
        priceListCode: PRICE_LIST_CODE,
        entityDefId: productEntityDefId as number,
        entityInstanceId: product.id,
        quantity,
        attributes: {},
        customerInstanceId: selectedCustomerId,
        contractOverridePrice,
      }),
    enabled: productEntityDefId !== null && selectedCustomerId !== null && quantity > 0,
  })

  function handleCustomerChange(value: number | undefined) {
    setSelectedCustomerId(value ?? null)
  }

  return (
    <div className="stack" style={{ marginTop: 8 }}>
      <h3>Giá + Waterfall: {product.displayLabel ?? product.businessKey}</h3>

      {customersQuery.isError && (
        <Alert
          type="error"
          message={(customersQuery.error as Error).message}
          action={
            <Button size="small" variant="quiet" onClick={() => void customersQuery.refetch()}>
              Thử lại
            </Button>
          }
        />
      )}

      {customersQuery.data && (
        <div className="inline-stack">
          <Field label="Khách hàng" style={{ minWidth: 280 }}>
            {(props) => (
              <Select
                {...props}
                showSearch
                virtual={false}
                optionFilterProp="label"
                style={{ width: 280 }}
                value={selectedCustomerId ?? undefined}
                onChange={handleCustomerChange}
                options={customersQuery.data!.content.map((customer) => ({
                  value: customer.id,
                  label: customerLabel(customer),
                }))}
              />
            )}
          </Field>

          <Field label="Số lượng" style={{ width: 120 }}>
            {(props) => (
              <Input
                {...props}
                type="number"
                min={1}
                value={quantityInput}
                onChange={(e) => setQuantityInput(e.target.value)}
              />
            )}
          </Field>

          <Field label="Giá hợp đồng riêng" hint="Tuỳ chọn" style={{ width: 200 }}>
            {(props) => (
              <Input
                {...props}
                type="number"
                value={contractOverrideInput}
                onChange={(e) => setContractOverrideInput(e.target.value)}
              />
            )}
          </Field>
        </div>
      )}

      {selectedCustomerId === null && <p style={{ color: 'var(--slate)' }}>Chọn khách hàng để tính giá.</p>}

      {priceQuery.isLoading && selectedCustomerId !== null && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 0' }}>
          <Spin size="small" aria-label="Loading price calculation" />
          <span>Đang tính giá...</span>
        </div>
      )}

      {priceQuery.isError && (
        <Alert
          type="error"
          message={(priceQuery.error as Error).message}
          action={
            <Button size="small" variant="quiet" onClick={() => void priceQuery.refetch()}>
              Thử lại
            </Button>
          }
        />
      )}

      {priceQuery.data && (
        <div>
          <table className="waterfall-table" aria-label="price waterfall">
            <thead>
              <tr>
                <th>Bước</th>
                <th className="numeric">Điều chỉnh</th>
                <th className="numeric">Sau bước này</th>
              </tr>
            </thead>
            <tbody>
              {priceQuery.data.steps.map((step) => (
                <tr key={step.stepOrder}>
                  <td>{step.label}</td>
                  <td className="numeric">{step.deltaAmount}</td>
                  <td className="numeric">{step.resultingAmount}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p style={{ fontWeight: 650, marginTop: 8 }}>Giá cuối: {priceQuery.data.finalAmount}</p>
          <p style={{ color: 'var(--slate)' }}>
            Số lượng × Giá cuối = Tổng dòng: {quantity} × {priceQuery.data.finalAmount} ={' '}
            {quantity * priceQuery.data.finalAmount}
          </p>
        </div>
      )}
    </div>
  )
}
