import { Alert, Spin } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { Button, Field, Select } from '../ui'
import { fetchInstances, fetchSupersededBy } from '../catalog/catalogApi'
import { InstanceTable } from '../catalog/InstanceTable'
import type { EntityInstanceSummary } from '../catalog/types'
import { PriceWaterfallScreen } from '../pricing/PriceWaterfallScreen'
import { fetchCompatibleInstances } from './configuratorApi'

interface VehiclePartSelectionScreenProps {
  /** Real OIDC access token from the authenticated session (react-oidc-context). */
  accessToken: string
}

// [pack:automotive] This screen (docs/ux/01-vehicle-part-selection.md) is
// inherently automotive-flavoured — "pick a vehicle, filter compatible
// parts" only makes sense for a domain pack that models vehicles/parts.
// It composes the generic [core] InstanceTable (web/src/catalog/
// InstanceTable.tsx), which has no idea these entity_def codes mean
// "vehicle"/"part" — same split the backend already draws (e.g.
// AutomotiveFitmentSeedRunner wires the literal SKUs/entity_def codes
// while ConstraintEngine/ConfiguratorService stay generic). Wiring the
// literal entity_def codes "Vehicle"/"Product" here is a data reference
// (which pack.yaml-declared entity_def this screen's flow targets), not a
// hardcoded domain *display string* — see docs/decisions.md for this
// task's decision record.
const VEHICLE_ENTITY_DEF_CODE = 'Vehicle'
const PRODUCT_ENTITY_DEF_CODE = 'Product'

function vehicleLabel(vehicle: EntityInstanceSummary): string {
  const { make, model, year } = vehicle.attributes
  return [make, model, year].filter((part) => part !== undefined && part !== null).join(' ')
}

/** Small isolated query so one row's supersession lookup failing/loading never blocks the table. */
function SupersessionHint({ accessToken, instanceId }: { accessToken: string; instanceId: number }) {
  const { data } = useQuery({
    queryKey: ['catalog', 'superseded-by', instanceId, accessToken],
    queryFn: () => fetchSupersededBy(accessToken, instanceId),
  })

  if (!data || data.length === 0) {
    return null
  }

  return (
    <div style={{ color: '#a9254a', fontSize: 13 }}>
      ⚠ Đã bị supersede bởi {data.map((superseding) => superseding.displayLabel).join(', ')}
    </div>
  )
}

/**
 * S2-T9 (docs/sprints/sprint-2.md) screen: "chọn xe -> part tương thích",
 * per wireframe docs/ux/01-vehicle-part-selection.md — simplified per
 * docs/decisions.md (1 Vehicle dropdown instead of 4 dependent dropdowns;
 * no router hookup yet, S2-T13 will extend this same component).
 *
 * Migrated from MUI to Ant Design + web/design.md tokens (D-050) — logic,
 * API calls, and query behavior unchanged, only the JSX.
 */
export function VehiclePartSelectionScreen({ accessToken }: VehiclePartSelectionScreenProps) {
  const [selectedVehicleId, setSelectedVehicleId] = useState<number | null>(null)
  const [filterActive, setFilterActive] = useState(false)
  const [selectedProduct, setSelectedProduct] = useState<EntityInstanceSummary | null>(null)

  const vehiclesQuery = useQuery({
    queryKey: ['catalog', VEHICLE_ENTITY_DEF_CODE, accessToken],
    queryFn: () => fetchInstances(accessToken, VEHICLE_ENTITY_DEF_CODE, { size: 100 }),
  })

  const productsQuery = useQuery({
    queryKey: ['catalog', PRODUCT_ENTITY_DEF_CODE, accessToken],
    queryFn: () => fetchInstances(accessToken, PRODUCT_ENTITY_DEF_CODE, { size: 100 }),
  })

  const compatibleQuery = useQuery({
    queryKey: ['configurator', 'compatible', selectedVehicleId, accessToken],
    queryFn: () =>
      fetchCompatibleInstances(accessToken, selectedVehicleId as number, PRODUCT_ENTITY_DEF_CODE),
    enabled: filterActive && selectedVehicleId !== null,
  })

  function handleVehicleChange(value: number | undefined) {
    setSelectedVehicleId(value ?? null)
    setFilterActive(false)
    setSelectedProduct(null)
  }

  function clearFilter() {
    setFilterActive(false)
  }

  const displayedProducts = useMemo<EntityInstanceSummary[] | null>(() => {
    if (!filterActive) {
      return productsQuery.data?.content ?? null
    }
    if (!productsQuery.data || !compatibleQuery.data) {
      return null
    }
    const compatibleIds = new Set(compatibleQuery.data.map((c) => c.entityInstanceId))
    return productsQuery.data.content.filter((product) => compatibleIds.has(product.id))
  }, [filterActive, productsQuery.data, compatibleQuery.data])

  const productsLoading = filterActive
    ? productsQuery.isLoading || compatibleQuery.isLoading
    : productsQuery.isLoading
  const productsError = filterActive ? productsQuery.error ?? compatibleQuery.error : productsQuery.error

  function retryProducts() {
    void productsQuery.refetch()
    if (filterActive) {
      void compatibleQuery.refetch()
    }
  }

  return (
    <div className="stack">
      <div>
        <h3>Chọn xe</h3>

        {vehiclesQuery.isLoading && (
          <div style={{ display: 'flex', padding: '8px 0' }}>
            <Spin size="small" aria-label="Loading vehicles" />
          </div>
        )}

        {vehiclesQuery.isError && (
          <Alert
            type="error"
            message={(vehiclesQuery.error as Error).message}
            action={
              <Button size="small" variant="quiet" onClick={() => void vehiclesQuery.refetch()}>
                Thử lại
              </Button>
            }
          />
        )}

        {vehiclesQuery.data && (
          <div className="inline-stack">
            <Field label="Chọn xe" style={{ minWidth: 280 }}>
              {(props) => (
                <Select
                  {...props}
                  showSearch
                  virtual={false}
                  optionFilterProp="label"
                  style={{ width: 280 }}
                  value={selectedVehicleId ?? undefined}
                  onChange={handleVehicleChange}
                  options={vehiclesQuery.data!.content.map((vehicle) => ({
                    value: vehicle.id,
                    label: vehicleLabel(vehicle),
                  }))}
                />
              )}
            </Field>
            <Button
              variant="primary"
              disabled={selectedVehicleId === null}
              onClick={() => setFilterActive(true)}
            >
              Tìm part tương thích
            </Button>
          </div>
        )}
      </div>

      <div>
        <h3>Danh sách Part</h3>

        {!filterActive && <p style={{ color: 'var(--slate)', marginTop: 0 }}>Chọn xe ở trên để lọc nhanh hơn.</p>}

        {productsLoading && (
          <div style={{ display: 'flex', padding: '8px 0' }}>
            <Spin size="small" aria-label="Loading products" />
          </div>
        )}

        {!productsLoading && productsError && (
          <Alert
            type="error"
            message={(productsError as Error).message}
            action={
              <Button size="small" variant="quiet" onClick={retryProducts}>
                Thử lại
              </Button>
            }
          />
        )}

        {!productsLoading && !productsError && filterActive && displayedProducts?.length === 0 && (
          <Alert
            type="info"
            message="Không có part tương thích với xe đã chọn. Có thể nới điều kiện, xem toàn bộ catalog, hoặc liên hệ Pricing Admin nếu nghi thiếu dữ liệu fitment."
            action={
              <Button size="small" variant="quiet" onClick={clearFilter}>
                Xem toàn bộ catalog
              </Button>
            }
          />
        )}

        {!productsLoading &&
          !productsError &&
          displayedProducts &&
          (!filterActive || displayedProducts.length > 0) && (
            <InstanceTable
              rows={displayedProducts}
              selectedRowId={selectedProduct?.id ?? null}
              onSelectRow={setSelectedProduct}
              selectActionLabel="Chọn part này"
              renderRowFooter={(row) => (
                <SupersessionHint accessToken={accessToken} instanceId={row.id} />
              )}
            />
          )}

        {selectedProduct && (
          <Alert type="success" style={{ marginTop: 16 }} message={`Đã chọn: ${selectedProduct.displayLabel}`} />
        )}
      </div>

      {/* S2-T13: nối tiếp "Chọn part này" ở trên — màn giá/waterfall
          cho đúng Product đã chọn, không cần router riêng (docs/decisions.md). */}
      {selectedProduct && (
        <PriceWaterfallScreen accessToken={accessToken} product={selectedProduct} />
      )}
    </div>
  )
}
