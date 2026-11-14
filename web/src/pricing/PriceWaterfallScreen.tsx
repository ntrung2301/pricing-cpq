import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import FormControl from '@mui/material/FormControl'
import InputLabel from '@mui/material/InputLabel'
import MenuItem from '@mui/material/MenuItem'
import Select, { type SelectChangeEvent } from '@mui/material/Select'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import Paper from '@mui/material/Paper'
import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
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

  function handleCustomerChange(event: SelectChangeEvent<number | ''>) {
    const value = event.target.value
    setSelectedCustomerId(value === '' ? null : Number(value))
  }

  return (
    <Stack spacing={2} sx={{ mt: 2 }}>
      <Typography variant="h6" component="h2">
        Giá + Waterfall: {product.displayLabel ?? product.businessKey}
      </Typography>

      {customersQuery.isError && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => void customersQuery.refetch()}>
              Thử lại
            </Button>
          }
        >
          {(customersQuery.error as Error).message}
        </Alert>
      )}

      {customersQuery.data && (
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <FormControl sx={{ minWidth: 280 }} size="small">
            <InputLabel id="customer-select-label">Khách hàng</InputLabel>
            <Select
              labelId="customer-select-label"
              label="Khách hàng"
              value={selectedCustomerId ?? ''}
              onChange={handleCustomerChange}
            >
              {customersQuery.data.content.map((customer) => (
                <MenuItem key={customer.id} value={customer.id}>
                  {customerLabel(customer)}
                </MenuItem>
              ))}
            </Select>
          </FormControl>

          <TextField
            label="Số lượng"
            type="number"
            size="small"
            value={quantityInput}
            onChange={(e) => setQuantityInput(e.target.value)}
            sx={{ width: 120 }}
            slotProps={{ htmlInput: { min: 1 } }}
          />

          <TextField
            label="Giá hợp đồng riêng"
            type="number"
            size="small"
            value={contractOverrideInput}
            onChange={(e) => setContractOverrideInput(e.target.value)}
            sx={{ width: 200 }}
            helperText="Tuỳ chọn"
          />
        </Stack>
      )}

      {selectedCustomerId === null && (
        <Typography variant="body2" color="text.secondary">
          Chọn khách hàng để tính giá.
        </Typography>
      )}

      {priceQuery.isLoading && selectedCustomerId !== null && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, py: 2 }}>
          <CircularProgress size={20} aria-label="Loading price calculation" />
          <Typography variant="body2">Đang tính giá...</Typography>
        </Box>
      )}

      {priceQuery.isError && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => void priceQuery.refetch()}>
              Thử lại
            </Button>
          }
        >
          {(priceQuery.error as Error).message}
        </Alert>
      )}

      {priceQuery.data && (
        <Box>
          <TableContainer component={Paper}>
            <Table aria-label="price waterfall">
              <TableHead>
                <TableRow>
                  <TableCell>Bước</TableCell>
                  <TableCell align="right">Điều chỉnh</TableCell>
                  <TableCell align="right">Sau bước này</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {priceQuery.data.steps.map((step) => (
                  <TableRow key={step.stepOrder}>
                    <TableCell>{step.label}</TableCell>
                    <TableCell align="right">{step.deltaAmount}</TableCell>
                    <TableCell align="right">{step.resultingAmount}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <Typography variant="subtitle1" sx={{ mt: 1 }}>
            Giá cuối: {priceQuery.data.finalAmount}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Số lượng × Giá cuối = Tổng dòng: {quantity} × {priceQuery.data.finalAmount} ={' '}
            {quantity * priceQuery.data.finalAmount}
          </Typography>
        </Box>
      )}
    </Stack>
  )
}
