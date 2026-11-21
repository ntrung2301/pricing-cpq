export type ProductStatus = 'Active' | 'Draft' | 'Discontinued'

type ProductSeed = [
  name: string,
  category: string,
  brand: string,
  type: 'OEM' | 'Aftermarket',
  status: ProductStatus,
  listPrice: number,
  unitCost: number,
  stock: number,
  minimumOrder: number,
  unit: 'Each' | 'Set' | 'Pack',
  leadTimeDays: number,
  warehouse: string,
]

const seeds: ProductSeed[] = [
  ['Ceramic Front Brake Pads', 'Braking', 'Northline', 'Aftermarket', 'Active', 58.00, 31.50, 148, 4, 'Set', 3, 'West'],
  ['Ceramic Rear Brake Pads', 'Braking', 'Northline', 'Aftermarket', 'Active', 49.50, 27.25, 97, 4, 'Set', 3, 'West'],
  ['Vented Front Brake Rotor', 'Braking', 'Apex Motion', 'OEM', 'Active', 112.00, 68.40, 62, 2, 'Each', 5, 'Central'],
  ['Solid Rear Brake Rotor', 'Braking', 'Apex Motion', 'OEM', 'Active', 86.00, 54.10, 45, 2, 'Each', 5, 'Central'],
  ['Brake Caliper Repair Kit', 'Braking', 'Northline', 'Aftermarket', 'Draft', 28.00, 14.70, 0, 1, 'Set', 8, 'West'],
  ['Spin-On Oil Filter', 'Filtration', 'Clearflow', 'Aftermarket', 'Active', 12.90, 5.80, 420, 12, 'Each', 2, 'East'],
  ['Cartridge Oil Filter', 'Filtration', 'Clearflow', 'OEM', 'Active', 16.50, 8.25, 270, 6, 'Each', 2, 'East'],
  ['Engine Air Filter', 'Filtration', 'Clearflow', 'OEM', 'Active', 24.00, 13.50, 186, 6, 'Each', 3, 'East'],
  ['Carbon Cabin Filter', 'Filtration', 'Clearflow', 'Aftermarket', 'Active', 31.50, 17.40, 112, 4, 'Each', 3, 'East'],
  ['Inline Fuel Filter', 'Filtration', 'Clearflow', 'OEM', 'Discontinued', 22.00, 12.80, 0, 4, 'Each', 0, 'East'],
  ['130A Alternator', 'Electrical', 'Voltline', 'Aftermarket', 'Active', 189.00, 118.00, 32, 1, 'Each', 5, 'Central'],
  ['120A Alternator', 'Electrical', 'Voltline', 'OEM', 'Discontinued', 172.00, 110.00, 0, 1, 'Each', 0, 'Central'],
  ['Compact Starter Motor', 'Electrical', 'Voltline', 'OEM', 'Active', 221.00, 144.00, 25, 1, 'Each', 6, 'Central'],
  ['Front Wheel Speed Sensor', 'Electrical', 'Voltline', 'Aftermarket', 'Active', 47.00, 26.50, 80, 2, 'Each', 4, 'West'],
  ['Ignition Coil Pack', 'Electrical', 'Voltline', 'OEM', 'Draft', 78.00, 45.20, 0, 2, 'Each', 10, 'Central'],
  ['Iridium Spark Plugs', 'Ignition', 'Roadcraft', 'OEM', 'Active', 54.00, 29.80, 154, 4, 'Pack', 3, 'East'],
  ['Platinum Spark Plugs', 'Ignition', 'Roadcraft', 'Aftermarket', 'Active', 39.00, 21.25, 168, 4, 'Pack', 3, 'East'],
  ['Silicone Ignition Wire Set', 'Ignition', 'Roadcraft', 'Aftermarket', 'Active', 68.00, 38.40, 44, 1, 'Set', 5, 'West'],
  ['Thermostat Assembly', 'Cooling', 'ThermaWorks', 'OEM', 'Active', 51.00, 30.10, 72, 2, 'Each', 4, 'Central'],
  ['Water Pump Assembly', 'Cooling', 'ThermaWorks', 'OEM', 'Active', 126.00, 78.50, 36, 1, 'Each', 7, 'Central'],
  ['Upper Radiator Hose', 'Cooling', 'ThermaWorks', 'Aftermarket', 'Active', 36.00, 19.75, 53, 2, 'Each', 4, 'West'],
  ['Radiator Fan Module', 'Cooling', 'ThermaWorks', 'Aftermarket', 'Draft', 155.00, 92.00, 0, 1, 'Each', 12, 'Central'],
  ['Front Stabilizer Link', 'Suspension', 'Apex Motion', 'Aftermarket', 'Active', 35.00, 18.80, 104, 2, 'Each', 4, 'West'],
  ['Upper Control Arm', 'Suspension', 'Apex Motion', 'OEM', 'Active', 143.00, 86.50, 31, 1, 'Each', 6, 'Central'],
  ['Rear Shock Absorber Pair', 'Suspension', 'Roadcraft', 'Aftermarket', 'Active', 158.00, 92.75, 18, 1, 'Set', 8, 'West'],
]

export const mockProducts = seeds.map(([
  name, category, brand, type, status, listPrice, unitCost, stock, minimumOrder, unit, leadTimeDays, warehouse,
], index) => ({
  sku: `PRD-${String(index + 1).padStart(3, '0')}`,
  name,
  category,
  brand,
  type,
  status,
  listPrice,
  unitCost,
  margin: (listPrice - unitCost) / listPrice,
  stock,
  minimumOrder,
  unit,
  leadTimeDays,
  warehouse,
  updated: `2026-11-${String(17 - Math.floor(index / 2)).padStart(2, '0')}`,
}))

export type Product = (typeof mockProducts)[number]

export function parseProductImport(text: string): Product[] {
  const data: unknown = JSON.parse(text)
  if (!Array.isArray(data) || data.length === 0) {
    throw new Error('Choose a JSON file containing a nonempty list of products.')
  }

  const skus = new Set<string>()
  return data.map((entry: unknown, index) => {
    if (!entry || typeof entry !== 'object') {
      throw new Error(`Product ${index + 1} must be an object.`)
    }

    const values = entry as Record<string, unknown>
    const template = mockProducts[0]
    const product = Object.fromEntries(Object.entries(template).filter(([field]) => field !== "margin").map(([field, sample]) => {
      const value = values[field]
      if (
        typeof value !== typeof sample ||
        (typeof value === 'string' && !value.trim()) ||
        (typeof value === 'number' && (!Number.isFinite(value) || value < 0))
      ) {
        throw new Error(`Product ${index + 1} has an invalid ${field}.`)
      }
      return [field, value]
    })) as Omit<Product, "margin">

    if (
      !['OEM', 'Aftermarket'].includes(product.type) ||
      !['Active', 'Draft', 'Discontinued'].includes(product.status) ||
      !['Each', 'Set', 'Pack'].includes(product.unit) ||
      !/^\d{4}-\d{2}-\d{2}$/.test(product.updated)
    ) {
      throw new Error(`Product ${index + 1} has an invalid type, status, unit, or update date.`)
    }
    if (skus.has(product.sku)) throw new Error(`Duplicate SKU ${product.sku}.`)
    skus.add(product.sku)

    return {
      ...product,
      margin: product.listPrice ? (product.listPrice - product.unitCost) / product.listPrice : 0,
    }
  })
}
