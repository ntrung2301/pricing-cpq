import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import FormControl from '@mui/material/FormControl'
import InputLabel from '@mui/material/InputLabel'
import MenuItem from '@mui/material/MenuItem'
import Select, { type SelectChangeEvent } from '@mui/material/Select'
import Stack from '@mui/material/Stack'
import Typography from '@mui/material/Typography'
import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { fetchInstances, fetchSupersededBy } from '../catalog/catalogApi'
import { InstanceTable } from '../catalog/InstanceTable'
import type { EntityInstanceSummary } from '../catalog/types'
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
    <Typography variant="body2" color="warning.main" component="div">
      ⚠ Đã bị supersede bởi {data.map((superseding) => superseding.displayLabel).join(', ')}
    </Typography>
  )
}

/**
 * S2-T9 (docs/sprints/sprint-2.md) screen: "chọn xe -> part tương thích",
 * per wireframe docs/ux/01-vehicle-part-selection.md — simplified per
 * docs/decisions.md (1 Vehicle dropdown instead of 4 dependent dropdowns;
 * no router hookup yet, S2-T13 will extend this same component).
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

  function handleVehicleChange(event: SelectChangeEvent<number | ''>) {
    const value = event.target.value
    setSelectedVehicleId(value === '' ? null : Number(value))
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
    <Stack spacing={3}>
      <Box>
        <Typography variant="h6" component="h2" gutterBottom>
          Chọn xe
        </Typography>

        {vehiclesQuery.isLoading && (
          <Box sx={{ display: 'flex', py: 2 }}>
            <CircularProgress size={24} aria-label="Loading vehicles" />
          </Box>
        )}

        {vehiclesQuery.isError && (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={() => void vehiclesQuery.refetch()}>
                Thử lại
              </Button>
            }
          >
            {(vehiclesQuery.error as Error).message}
          </Alert>
        )}

        {vehiclesQuery.data && (
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
            <FormControl sx={{ minWidth: 280 }} size="small">
              <InputLabel id="vehicle-select-label">Chọn xe</InputLabel>
              <Select
                labelId="vehicle-select-label"
                label="Chọn xe"
                value={selectedVehicleId ?? ''}
                onChange={handleVehicleChange}
              >
                {vehiclesQuery.data.content.map((vehicle) => (
                  <MenuItem key={vehicle.id} value={vehicle.id}>
                    {vehicleLabel(vehicle)}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Button
              variant="contained"
              disabled={selectedVehicleId === null}
              onClick={() => setFilterActive(true)}
            >
              Tìm part tương thích
            </Button>
          </Stack>
        )}
      </Box>

      <Box>
        <Typography variant="h6" component="h2" gutterBottom>
          Danh sách Part
        </Typography>

        {!filterActive && (
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            Chọn xe ở trên để lọc nhanh hơn.
          </Typography>
        )}

        {productsLoading && (
          <Box sx={{ display: 'flex', py: 2 }}>
            <CircularProgress size={24} aria-label="Loading products" />
          </Box>
        )}

        {!productsLoading && productsError && (
          <Alert
            severity="error"
            action={
              <Button color="inherit" size="small" onClick={retryProducts}>
                Thử lại
              </Button>
            }
          >
            {(productsError as Error).message}
          </Alert>
        )}

        {!productsLoading && !productsError && filterActive && displayedProducts?.length === 0 && (
          <Alert
            severity="info"
            action={
              <Button color="inherit" size="small" onClick={clearFilter}>
                Xem toàn bộ catalog
              </Button>
            }
          >
            Không có part tương thích với xe đã chọn. Có thể nới điều kiện, xem toàn bộ catalog,
            hoặc liên hệ Pricing Admin nếu nghi thiếu dữ liệu fitment.
          </Alert>
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
          <Alert severity="success" sx={{ mt: 2 }}>
            Đã chọn: {selectedProduct.displayLabel}
          </Alert>
        )}
      </Box>
    </Stack>
  )
}
