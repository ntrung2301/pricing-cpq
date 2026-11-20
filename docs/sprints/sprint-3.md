# Sprint 3 — Configurable Pricing Engine v2 (Price List)

Mục tiêu & tiêu chí demo: xem `docs/roadmap.md`. Chi tiết thiết kế đầy đủ:
`docs/adr/0009-configurable-pricing-dag-and-price-list.md` (ADR-009) +
`docs/decisions.md` D-037. Backlog: `docs/backlog.md` Epic 8.

Quy tắc nhận việc giống Sprint 1/2: agent chọn task `pending` không có phụ
thuộc chưa xong, tự nhận, cập nhật trạng thái khi xong. Worktree riêng cho
task chạy song song (`superpowers:using-git-worktrees`).

**Lưu ý quan trọng trước khi bắt đầu** (PO đã xác nhận):
- Frontend mới (S3-T8) dùng **Ant Design** (D-036 — đã thay MUI), không
  quay lại MUI dù các màn hình S2 cũ (`EntitiesList`, `VehiclePartSelectionScreen`,
  `PriceWaterfallScreen`) đang được migrate song song (không phải việc của
  Sprint 3, đang chạy ở phiên khác — S3-T8 chỉ cần build mới theo Ant
  Design, không cần tự migrate lại các màn cũ).
- **NEEDS-PO-REVIEW đang mở** (ADR-009): có migrate `/api/pricing/calculate`
  (S2-T10/T12, `RuleEngine` tuyến tính) ngay trong sprint này hay để chạy
  song song? → Mặc định Sprint 3 **không đụng** API cũ, xây engine mới
  (DAG) độc lập dưới route mới (`/api/pricing-v2/...` hoặc tên khác do
  backend-dev quyết, ghi vào decisions.md) — tránh block S3 chờ quyết định
  migrate. Quyết định migrate thật (xoá/giữ engine cũ) để task riêng sau
  Sprint 3.
- **NEEDS-PO-REVIEW đang mở thứ 2** (backlog E8-07): cách Customer tham
  gia vào Price List graph (Price List về nguyên tắc không có 1 customer
  context cụ thể). S3-T7 tự quyết 1 cách hợp lý (ví dụ: 1 node tham chiếu
  thông tin — không ảnh hưởng `result_price` — để chứng minh cơ chế đọc
  được Customer, không bắt buộc có ý nghĩa business sâu), ghi rõ lý do vào
  decisions.md, PO review lại sau.

---

### S3-T1 — Entity taxonomy v2: role Anchor/Extension/Parameter
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: không
- **Trạng thái**: Done (2026-11-20)
- **Việc làm**: Theo backlog E8-01 + ADR-009 mục 1. Migration thêm
  `entity_def.role`, `entity_def.extends_entity_def_id`, bảng
  `entity_def_dimension_attribute`, `entity_instance.anchor_instance_id`.
  `CoreAnchorBootstrapService` (`ApplicationRunner`, chạy trước
  `PackLoaderService` — dùng `@Order`) tạo shell `Product`/`Customer`
  (`role=ANCHOR`) nếu chưa có. `PackLoaderService`/`EntityDescriptor` thêm
  parse `role`/`extends`/`dimension_attributes[]` từ `pack.yaml`. Đồng bộ
  `business_key` cho Extension = ghép business_key anchor + giá trị
  dimension attribute.
- **Done khi**: Done-khi của E8-01 (xem backlog.md) — bootstrap tạo shell
  đúng, pack nạp thêm attribute không cần sửa Java, tạo Extension instance
  có `anchor_instance_id` + `business_key` ghép đúng, không trùng giữa 2
  Product khác nhau cùng dimension value.
- **Tóm tắt kết quả**: Migration `V9__entity_taxonomy_v2.sql` (thêm
  `entity_def.role`/`extends_entity_def_id`, bảng
  `entity_def_dimension_attribute`, `entity_instance.anchor_instance_id`,
  backfill `role` cho dữ liệu Sprint 1/2 đã seed). `core-metamodel` thêm
  `EntityRole`, `EntityDefinitionDimensionAttribute`. `app` thêm
  `CoreAnchorBootstrapService` (`@Order(1)`, trước `PackLoaderService`
  `@Order(2)`); `PackLoaderService`/`EntityDescriptor` parse
  `role`/`extends`/`dimension_attributes[]`, bỏ qua (log warn) nếu pack cố
  set `role` cho `Product`/`Customer`; `EntityInstanceService` thêm
  overload `createInstance(..., anchorInstanceId)` + composite
  `business_key` cho Extension (`"<anchor>|<dim1>|<dim2>..."`). Chi tiết
  quyết định: `docs/decisions.md` D-040 (+ D-041 cho phát hiện
  `ApplicationRunner` tự chạy dưới `@SpringBootTest`, đính chính D-020).

### S3-T2 — DAG execution engine (core, chưa đụng entity taxonomy)
- **Người phụ trách**: backend-dev
- **Trạng thái**: Done (2026-11-20)
- **Phụ thuộc**: không (độc lập với S3-T1 — node generic chưa cần đọc
  Product/Customer/Extension/Parameter)
- **Việc làm**: Theo ADR-009 mục 2. Node = data (`id, type, config,
  inputs`). Topological sort theo input reference (tái dùng thuật toán đã
  verify ở POC trong phiên thiết kế — Kahn's algorithm). Node type generic
  không cần entity: `formula` (mở rộng `JsonLogicEvaluator` core-pricing
  đã có — ADR-002/S2-T10 — để tính giá trị số, không chỉ điều kiện boolean),
  `weighted-average`, `round`, `output`. Sinh Calculation Trace đầy đủ
  (input+output mọi node). **Không loop, không biến mutable** — validate
  graph là DAG thật (phát hiện cycle → lỗi rõ, không treo).
- **Done khi**: Test dựng lại đúng graph đã verify ở POC thiết kế (query
  giả định → weighted-average → formula → round → output) chạy qua engine
  thật, cùng input luôn cùng output (chạy lại nhiều lần so sánh). Graph có
  cycle → lỗi rõ, không crash/treo.
- **Tóm tắt kết quả**: Implement ở `core-pricing` package mới
  `com.cpq.core.pricing.dag` — `DagNode` (record, immutable), interface
  `DagNodeExecutor` + registry hữu hạn `DagNodeExecutors.standard()`
  (`formula`/`weighted-average`/`round`/`output`), `DagEngine` (Kahn's
  algorithm, phát hiện cycle rõ ràng, sinh `TraceEntry` đầy đủ mọi node).
  `JsonLogicEvaluator` (dsl) mở rộng thêm `+ - * /` + method public
  `evaluateNumeric` — tái dùng nguyên parser/dispatch cũ, không viết lại.
  Chi tiết quyết định: `docs/decisions.md` D-039. Chưa làm node `query`/
  `measure-ref` (S3-T3/S3-T5, khác task). `RuleEngine`/`PricingStage`
  Sprint 2 không bị đụng.

### S3-T3 — Calculation Binding + node `query`
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S3-T1, S3-T2
- **Việc làm**: Theo ADR-009 mục 3. `calculation_binding` +
  `calculation_binding_filter` + `calculation_binding_sort`. Node `query`
  đọc Product/Customer (attribute trực tiếp, không cần filter) hoặc
  Extension/Parameter (tự động khớp anchor nếu Extension, áp filter→sort→
  take-first user cấu hình). 0 dòng → null. ≥2 dòng mà `take_first=false`
  → throw lỗi rõ, chặn tính — **cả lúc runtime, không chỉ lúc test/preview
  config**.
- **Done khi**: Test binding đọc đúng Extension/Parameter/Product/Customer
  (dùng entity giả định, chưa cần `CostHistory`/`MarginThreshold` thật —
  đó là S3-T4); test ≥2 dòng ambiguous không `take_first` → lỗi rõ; dữ
  liệu nguồn đổi sau khiến binding từng đúng ra ambiguous → lỗi ngay lúc
  tính lại, không lặng lẽ sai.

### S3-T4 — Seed demo: CostHistory, MarginThreshold, Product mở rộng
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S3-T1
- **Việc làm**: Theo backlog E8-02. Thêm vào `pack.yaml` automotive (thuần
  data): `Product` thêm attribute `country_of_manufacture`; entity mới
  `CostHistory` (`role: EXTENSION`, `extends: Product`, dimension
  `period`) + `MarginThreshold` (`role: PARAMETER`, key `level`,
  `CRITICAL`/`RED`/`YELLOW`/`GREEN` với `min_margin_pct`/`max_margin_pct`).
  Seed thật (ApplicationRunner, giống `DemoDataSeedRunner`/
  `AutomotiveFitmentSeedRunner` đã có — không viết lại logic, tái dùng
  `CatalogImportService` nếu hợp, hoặc service mới nếu format khác) — ít
  nhất 1 SKU có ≥3 dòng `CostHistory`.
- **Done khi**: Done-khi của E8-02 — chạy app thật, query ra đủ dữ liệu
  không phải test fixture.

### S3-T5 — Measure: subgraph tái dùng
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S3-T2, S3-T3
- **Việc làm**: Theo ADR-009 mục 2 (Measure) + backlog E8-04.
  `measure_definition` (`code, name, graph, result_node_id`) + node type
  `measure-ref` (transclude trace đầy đủ khi gọi, tham số hoá theo context
  anchor hiện tại — không hardcode 1 instance cụ thể, giống POC đã verify).
- **Done khi**: Done-khi của E8-04 — 1 measure, 2 graph khác nhau tham
  chiếu, ra đúng giá trị + trace đầy đủ ở cả 2 nơi.

### S3-T6 — Module Price List: schema + API
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S3-T3, S3-T5
- **Việc làm**: Theo ADR-009 mục 5 + backlog E8-05. `price_list`/
  `price_list_item` theo schema đã chốt (`status`/`version`/
  `calculation_rule_set_id`; `result_price`/`manual_override_price`/
  `waterfall_steps` JSONB, không tách bảng con). API: tạo Price List, thêm
  Product, tính qua DAG, submit, approve (snapshot vĩnh viễn — approve
  xong không tính lại dù binding nguồn đổi).
- **Done khi**: Done-khi của E8-05 — approve xong sửa dữ liệu nguồn không
  đổi `result_price` đã approve; `manual_override_price` là field DUY
  NHẤT sửa tay được.

### S3-T7 — Seed Price List demo dùng đủ 4 nguồn
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S3-T4, S3-T6
- **Việc làm**: Theo backlog E8-07. 1 `price_list` thật, graph đọc
  `Product` (country_of_manufacture → rule round), `CostHistory`
  (weighted avg cost), `MarginThreshold` (map margin% ra mức risk), và
  `Customer` (tự quyết cách dùng hợp lý, ghi lý do — xem lưu ý
  NEEDS-PO-REVIEW ở đầu file).
- **Done khi**: Chạy thật (không test fixture) ra `result_price` + mức
  margin risk từ data Postgres thật.

### S3-T8 — Measure Canvas UI (Ant Design)
- **Người phụ trách**: frontend-dev
- **Phụ thuộc**: S3-T6
- **Việc làm**: Theo backlog E8-06 — dựng lại đúng mockup đã polish
  (artifact "Measure Canvas": context switcher, palette Data source/
  Measure kéo-thả, canvas + Calculation Trace đồng bộ expand/collapse) với
  component Ant Design thật (`ConfigProvider`, theo D-036), gọi API thật từ
  S3-T6 — không mock.
- **Done khi**: Dựng 1 Price List thật qua UI (dữ liệu S3-T7), xem trace,
  approve — Playwright E2E thật, không mock, theo mẫu `e2e/tests/` đã có.

### S3-T9 — QA: sign-off Sprint 3
- **Người phụ trách**: qa-engineer
- **Phụ thuộc**: S3-T7, S3-T8
- **Việc làm**: Theo mẫu S2-T14. Xác nhận lại tiêu chí demo Sprint 3
  (`docs/roadmap.md`) chạy thật 2 lần liên tiếp; xác nhận rõ API
  `/api/pricing/calculate` cũ (Sprint 2) còn hoạt động, không bị regress
  bởi engine mới chạy song song.
- **Done khi**: Suite chạy xanh 2 lần liên tiếp trên stack thật, báo cáo
  sign-off (`docs/sprints/sprint-3-qa-signoff.md`, theo mẫu Sprint 2).

---

## Tổng hợp phụ thuộc

```
S3-T1 (entity taxonomy) ──┬──> S3-T3 (binding + query) ──┬──> S3-T5 (measure) ──> S3-T6 (Price List) ──┬──> S3-T7 (seed PL) ──┐
S3-T2 (DAG engine) ───────┘                               │                                              │                    │
S3-T1 ────────────────────────────────────────────────────┴──> S3-T4 (seed data) ──────────────────────> S3-T7 ──────────────┤
                                                                                     S3-T6 ──> S3-T8 (UI Ant Design) ──────────┤
                                                                            S3-T7 + S3-T8 ──> S3-T9 (QA)
```

Task không phụ thuộc gì (bắt đầu ngay, chạy song song): S3-T1, S3-T2.
