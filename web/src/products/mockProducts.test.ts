import { expect, it } from 'vitest'
import { mockProducts, parseProductImport } from './mockProducts'

it('round-trips exported sample data and recomputes margin on import', () => {
  expect(parseProductImport(JSON.stringify(mockProducts))).toEqual(mockProducts)

  const imported = parseProductImport(JSON.stringify([{ ...mockProducts[0], unitCost: 0, margin: 0 }]))
  expect(imported[0].margin).toBe(1)
})

it('rejects invalid product rows and duplicate SKUs', () => {
  expect(() => parseProductImport(JSON.stringify([mockProducts[0], { sku: 'NEW' }]))).toThrow('Product 2')
  expect(() => parseProductImport(JSON.stringify([mockProducts[0], mockProducts[0]]))).toThrow('Duplicate SKU')
})
