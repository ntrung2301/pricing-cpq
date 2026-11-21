import { Descriptions, Drawer, Grid, type TableColumnType } from "antd";
import { useMemo, useState, type ReactNode } from "react";
import {
  DataPage,
  DataTable,
  DataTransferActions,
  StatusPill,
  columnControls,
} from "../ui";
import { mockProducts, parseProductImport, type Product } from "./mockProducts";
import { ColumnsMenu } from "./ColumnsMenu";

const currency = new Intl.NumberFormat("en-US", {
  style: "currency",
  currency: "USD",
});
const percent = new Intl.NumberFormat("en-US", {
  style: "percent",
  maximumFractionDigits: 1,
});
const statusTones = {
  Active: "success",
  Draft: "neutral",
  Discontinued: "danger",
} as const;

interface ProductField {
  key: keyof Product;
  label: string;
  filter?: boolean;
  width: number;
  numeric?: boolean;
  detail?: (product: Product) => ReactNode;
}

const fields: ProductField[] = [
  { key: "sku", label: "SKU", width: 100 },
  { key: "name", label: "Product", width: 208 },
  { key: "category", label: "Category", width: 106, filter: true },
  { key: "brand", label: "Brand", width: 120, filter: true },
  { key: "type", label: "Type", width: 116, filter: true },
  {
    key: "status",
    label: "Status",
    width: 110,
    filter: true,
    detail: ({ status }) => (
      <StatusPill tone={statusTones[status]}>{status}</StatusPill>
    ),
  },
  {
    key: "listPrice",
    label: "List price",
    width: 94,
    numeric: true,
    detail: ({ listPrice }) => currency.format(listPrice),
  },
  {
    key: "unitCost",
    label: "Unit cost",
    width: 98,
    numeric: true,
    detail: ({ unitCost }) => currency.format(unitCost),
  },
  {
    key: "margin",
    label: "Margin",
    width: 82,
    numeric: true,
    detail: ({ margin }) => percent.format(margin),
  },
  { key: "stock", label: "Stock", width: 74, numeric: true },
  { key: "minimumOrder", label: "MOQ", width: 68, numeric: true },
  { key: "unit", label: "Unit", width: 85, filter: true },
  {
    key: "leadTimeDays",
    label: "Lead time",
    width: 100,
    numeric: true,
    detail: ({ leadTimeDays }) => (leadTimeDays ? `${leadTimeDays} d` : "—"),
  },
  { key: "warehouse", label: "Warehouse", width: 108, filter: true },
  { key: "updated", label: "Updated", width: 95 },
];

const selectionWidth = 36
const compactPinnedWidths: Partial<Record<keyof Product, number>> = { sku: 72, name: 92 }

const pinnedFields = fields.filter((field) => field.key in compactPinnedWidths)
const defaultEditableFields = fields
  .filter((field) => !(field.key in compactPinnedWidths))
  .map((field) => ({ ...field, visible: true }))

export function ProductsPage() {
  const [rows, setRows] = useState(mockProducts);
  const [editableFields, setEditableFields] = useState(defaultEditableFields);
  const [tableVersion, setTableVersion] = useState(0);
  const [activeProduct, setActiveProduct] = useState<Product | null>(null);
  const { sm } = Grid.useBreakpoint();

  const columns = useMemo(
    () =>
      [...pinnedFields, ...editableFields.filter((field) => field.visible)].map(
        (field): TableColumnType<Product> => ({
          title: field.label,
          dataIndex: field.key,
          width: sm ? field.width : compactPinnedWidths[field.key] ?? field.width,
          fixed: field.key in compactPinnedWidths ? "start" : undefined,
          ellipsis: true,
          align: field.numeric ? "right" : undefined,
          className: field.numeric ? "numeric-cell" : undefined,
          ...columnControls(
            rows,
            (product) => product[field.key],
            field.filter
          ),
          render: (_, product) =>
            field.key === "name" ? (
              <button
                type="button"
                className="product-name-action"
                onClick={() => setActiveProduct(product)}
              >
                {product.name}
              </button>
            ) : (
              field.detail?.(product) ?? product[field.key]
            ),
        })
      ),
    [rows, sm, editableFields]
  );

  return (
    <DataPage
      fit
      title="Products"
      actions={
        <DataTransferActions
          filename="sample-products.json"
          data={rows}
          onReload={() => setTableVersion((version) => version + 1)}
          onImport={async (file) => {
            const imported = parseProductImport(await file.text());
            setRows((current) => {
              const products = new Map(
                current.map((product) => [product.sku, product])
              );
              for (const product of imported)
                products.set(product.sku, product);
              return [...products.values()];
            });
            return `Imported ${imported.length} sample ${
              imported.length === 1 ? "product" : "products"
            }.`;
          }}
        />
      }
    >
      <DataTable
        key={tableVersion}
        aria-label="Products"
        rowKey="sku"
        columns={columns}
        dataSource={rows}
        pageSize={25}
        scrollRows
        pinnedSelectionWidth={selectionWidth}
        scroll={{ x: columns.reduce((width, column) => width + Number(column.width), selectionWidth) }}
        tableLayout="fixed"
        filters={
          <ColumnsMenu
            columns={editableFields}
            onChange={setEditableFields}
            onReset={() => setEditableFields(defaultEditableFields)}
          />
        }
        getRowLabel={(product) => product.sku}
        search={{
          label: "Search products",
          getText: (product) =>
            [
              product.sku,
              product.name,
              product.category,
              product.brand,
              product.type,
              product.status,
              product.warehouse,
            ].join(" "),
        }}
      />

      <Drawer
        title={activeProduct?.name}
        open={activeProduct !== null}
        onClose={() => setActiveProduct(null)}
        size="min(400px, 100vw)"
      >
        {activeProduct && (
          <Descriptions
            column={1}
            size="small"
            items={fields.map((field) => ({
              key: field.key,
              label: field.label,
              children:
                field.detail?.(activeProduct) ?? activeProduct[field.key],
            }))}
          />
        )}
      </Drawer>
    </DataPage>
  );
}
