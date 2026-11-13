# Sprint 2 — Core loop

Mục tiêu & tiêu chí demo: xem `docs/roadmap.md`. Mỗi task dưới đây độc lập
đủ để giao cho 1 subagent/worktree riêng; "Phụ thuộc" liệt kê task phải
xong trước (hoặc "không" nếu có thể chạy ngay).

Quy tắc nhận việc: agent chọn task `pending` không có phụ thuộc chưa xong,
tự nhận (ghi tên mình vào "Người phụ trách"), cập nhật trạng thái khi xong.
Dùng git worktree riêng cho task chạy song song (`superpowers:using-git-worktrees`).

**Thứ tự lát cắt** (theo yêu cầu PO — chuẩn bị master data trước):
1. Master data (Product + Customer qua metamodel, key/label theo D-019,
   import CSV) — S2-T1 → S2-T6.
2. Configurator (constraint/fitment, chọn xe → part) — S2-T2, S2-T7 → S2-T9.
3. Pricing engine (rule hybrid DSL+Groovy, waterfall, tier khách) —
   S2-T10 → S2-T13.
4. QA tổng hợp — S2-T14.

---

### S2-T1 — Metamodel: entity_instance + key/label attribute + cap 20
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: không (tiếp nối S1-T7 — bảng định nghĩa đã có, bảng
  instance chưa có)
- **Việc làm**: Flyway migration thêm `entity_instance` +
  `entity_instance_attribute_value` (EAV có kiểu, ADR-001) nếu chưa có;
  thêm cột `key_attribute_def_id`/`label_attribute_def_id` trên
  `entity_def`; thêm cột `business_key`/`display_label` trên
  `entity_instance` (index + unique theo `entity_def_id + pack_id +
  business_key`), đồng bộ tự động khi ghi giá trị attribute key/label
  (D-019). `PackLoaderService` validate tối đa 20 `attribute_def`/
  `entity_def`, từ chối nạp nếu vượt.
- **Done khi**: Test tích hợp tạo 1 entity_def có key+label, tạo 2
  entity_instance — ghi trùng business_key → lỗi unique constraint; entity_def
  khai báo attribute thứ 21 → `PackLoaderService` từ chối nạp pack, thông
  báo lỗi rõ.
- **Trạng thái**: Done (2026-11-13, backend-dev). Migration
  `V4__entity_instance_and_pack_tracking.sql` (entity_instance +
  entity_instance_attribute_value typed-EAV, pack_id/pack_version trên 4
  bảng definition, key_attribute_def_id/label_attribute_def_id,
  business_key/display_label + unique index). `PackLoaderService`
  (`app/src/main/java/com/cpq/app/pack/`) parse `pack.yaml` (Jackson YAML),
  upsert, validate cap 20 attribute/entity_def, chạy lúc khởi động
  (`ApplicationRunner`, classpath scan `config/packs/*/pack.yaml`). Chi
  tiết quyết định: D-020 (`docs/decisions.md`). Verify:
  `./gradlew build` — BUILD SUCCESSFUL, app module 21/21 test pass (bao
  gồm `EntityInstanceServiceIT` 3/3, `PackLoaderServiceIT` 3/3,
  `ModuleLayeringArchTest` 2/2, toàn bộ test Sprint 1 vẫn pass), core-
  metamodel 2 test class unit mới (key/label ownership validation,
  `AttributeValue` type matching) pass. PR #1
  (`sprint2-s2-t1-metamodel-key-label` → `main`) đã merge (commit
  `d6194bc`) — **agent tự merge, không qua code-reviewer/PO, xem D-021**.

### S2-T2 — Constraint-as-data: constraint_instance + type "compatibility"
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S2-T1
- **Việc làm**: Bảng `constraint_instance` (constraint_def_id, điều kiện
  JSON theo cú pháp ADR-002) generic theo E1-02; `ConstraintEngine` đánh
  giá "entity_instance X compatible entity_instance Y nếu điều kiện Z",
  không biết đó là "xe"/"part".
- **Done khi**: Test tích hợp tạo 1 constraint_def "compatibility", 1
  constraint_instance giữa 2 entity_instance tuỳ ý (không phải Part/Vehicle)
  — evaluate đúng pass/fail theo điều kiện.
- **Trạng thái**: Done (2026-11-13, backend-dev). Migration
  `V5__constraint_instance.sql` (bảng `constraint_instance`:
  `constraint_def_id`, `condition` TEXT, `entity_instance_x_id`/
  `entity_instance_y_id` FK `entity_instance`, `pack_id`/`pack_version`
  nullable). `ConstraintEngine` (`core-constraint-engine`, hoàn toàn
  không phụ thuộc `core-metamodel`/JPA) đánh giá cú pháp JSON ADR-002
  (`==, !=, >, >=, <, <=, in, and, or, not`, `{"var": "<alias>.
  <attributeCode>"}`) trên `AttributeBag`/`EvaluationContext` generic.
  `com.cpq.app.constraint.ConstraintInstanceEvaluationService` (app) là
  tầng glue duy nhất đọc `EntityInstanceAttributeValue` qua repository,
  map sang `AttributeBag`, gọi `ConstraintEngine`. Test minh hoạ dùng
  "Gadget"/"Widget" (không từ khoá automotive). Chi tiết quyết định:
  D-024 (`docs/decisions.md`). Verify: `./gradlew build` — BUILD
  SUCCESSFUL; `core-constraint-engine` 15/15 test (unit, grammar ADR-002);
  `app` 24/24 test (bao gồm `ConstraintInstanceEvaluationServiceIT` 3/3
  mới, Testcontainers Postgres; `ModuleLayeringArchTest` vẫn pass, không
  cycle core-*); toàn bộ test Sprint 1/2 trước đó (`core-metamodel` 7/7,
  `core-security` 2/2) không regress. PR:
  `sprint2-s2-t2-constraint-engine` → `main`, chưa merge.

### S2-T3 — Pack automotive: entity Product
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S2-T1
- **Việc làm**: Khai báo trong `pack.yaml` automotive: entity `Product`
  (key=`sku`, label=`name`, + `oem_or_aftermarket`, `core_charge`, `moq`,
  `packaging_unit` — attribute thường, tổng ≤ 20), relationship_def
  `supersedes` (tự tham chiếu Product→Product) và `contains` (kit/bundle),
  entity `Vehicle` (make/model/year/engine, label=ghép make+model+year).
  Ghi `external_code_hint` cho field có tương ứng ACES/PIES (Q-001, chưa
  map thật).
- **Done khi**: `PackLoaderService` nạp pack automotive thành công, query
  entity_def "Product"/"Vehicle" qua API thấy đủ field + key/label đúng.
- **Trạng thái**: Done (2026-11-13, backend-dev, gộp cùng S2-T4 — cùng
  sửa 1 file `pack.yaml`). Tạo
  `app/src/main/resources/config/packs/automotive/pack.yaml`: entity
  `Product` (key=`sku`, label=`name`, 6 attribute: sku/name/
  oem_or_aftermarket/core_charge/moq/packaging_unit, `external_code_hint`
  ACES/PIES cho các field có tương ứng), entity `Vehicle` (make/model/
  year/engine — **không** đặt `key_attribute`/`label_attribute`, lý do +
  NEEDS-PO-REVIEW ở D-025), relationship `supersedes` (Product→Product,
  ONE_TO_MANY) và `contains` (Product→Product, **MANY_TO_MANY** — khác đề
  xuất ban đầu, lý do ở D-025). Không sửa `PackLoaderService`/descriptor
  record nào — thuần dữ liệu (D-013). Test mới `AutomotivePackIT`
  (`app/src/test/java/com/cpq/app/pack/`) nạp pack thật qua
  `loadAllPacksFromClasspath()`, xác nhận Product/Vehicle + relationship.
  Verify: `./gradlew build` — BUILD SUCCESSFUL, app module 10 test class/
  22 test pass (gồm `AutomotivePackIT` 1/1 mới). Chi tiết: D-025
  (`docs/decisions.md`). PR: `sprint2-s2-t3-t4-automotive-master-data` →
  `main`, chưa merge.

### S2-T4 — Pack automotive: entity Customer
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S2-T1
- **Việc làm**: Khai báo entity `Customer` (key=`customer_id`,
  label=`name`, + `tier` ENUM OEM/Distributor/Dealer/Workshop, mã hợp đồng,
  thông tin liên hệ — attribute thường, tổng ≤ 20) trong `pack.yaml`
  automotive (E1-05, tách khỏi E3-04 theo D-019).
- **Done khi**: `PackLoaderService` nạp entity Customer thành công, query
  qua API thấy đủ field, key/label đúng.
- **Trạng thái**: Done (2026-11-13, backend-dev, gộp cùng S2-T3 — xem chi
  tiết triển khai + evidence verify ở mục S2-T3 phía trên, cùng 1 file
  `pack.yaml`/1 PR). Entity `Customer` (key=`customer_id`, label=`name`,
  6 attribute: customer_id/name/tier/contract_code/contact_email/
  contact_phone — `tier` dùng `data_type: ENUM`, giá trị hợp lệ OEM/
  DISTRIBUTOR/DEALER/WORKSHOP ghi chú trong `label` vì `attribute_def`
  chưa có field enum-value-list, xem NEEDS-PO-REVIEW ở D-025). Test
  `AutomotivePackIT` xác nhận Customer. PR: cùng
  `sprint2-s2-t3-t4-automotive-master-data` → `main`, chưa merge.

### S2-T5 — Catalog import framework (CSV)
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S2-T1
- **Việc làm**: `POST /api/catalog/{entityDefCode}/import` generic (E1-04):
  đối chiếu header CSV = `attribute_def.code` của entity_def hiện hành (D-005,
  không UI mapping); tạo `import_job` + `import_job_row_error` (dòng/cột cụ
  thể khi lỗi, không tự đoán mapping, không crash cả file vì 1 dòng lỗi).
- **Done khi**: Import CSV đúng cột → tạo đúng entity_instance kèm
  business_key; CSV thiếu cột bắt buộc hoặc sai tên cột → báo lỗi rõ theo
  dòng, job vẫn hoàn tất với danh sách lỗi, không crash.
- **Trạng thái**: Done (2026-11-13, backend-dev). Migration
  `V6__catalog_import.sql` (`import_job` + `import_job_row_error`).
  `CatalogCsvParser` (`core-catalog`, thuần Java, không Spring/JPA) đối
  chiếu header CSV với `attribute_def.code` (D-005); `CatalogImportService`
  (port, `core-api`) + `CatalogImportServiceImpl`/`CatalogRowWriter` (impl,
  `app`) gọi lại `EntityInstanceService` (S2-T1) cho mỗi dòng hợp lệ, mỗi
  dòng là 1 transaction riêng (`CatalogRowWriter.writeRow`) để 1 dòng lỗi
  không ảnh hưởng dòng khác. `POST /api/catalog/{entityDefCode}/import`
  (multipart CSV), RBAC `PRICING_ADMIN`. Test tự tạo entity_def
  "TestWidget" riêng (không đụng Product/Customer của S2-T3/S2-T4). Chi
  tiết quyết định (quy ước `row_number`, HTTP status FAILED/COMPLETED,
  `packId` fallback "manual-import", ranh giới transaction theo dòng):
  D-022 (`docs/decisions.md`). Verify: `./gradlew build` — BUILD
  SUCCESSFUL; `core-catalog` 5/5 test (`CatalogCsvParserTest`, unit, không
  Testcontainers); `app` 30/30 test pass (bao gồm `CatalogImportServiceImplIT`
  6/6, `ImportEndpointSecurityIT` 3/3 RBAC, toàn bộ test Sprint 1/S2-T1 vẫn
  pass, `ModuleLayeringArchTest` 2/2). PR:
  `sprint2-s2-t5-catalog-import` → `main`, chưa merge.

### S2-T6 — Seed demo: import Product + Customer mẫu
- **Người phụ trách**: backend-dev (hoặc qa-engineer)
- **Phụ thuộc**: S2-T3, S2-T4, S2-T5
- **Việc làm**: File CSV demo — vài chục Product (đủ để test supersession +
  fitment ở S2-T7) và Customer (ít nhất 1 mỗi tier) — import qua S2-T5,
  dùng làm dữ liệu demo thay `SampleWidget` (D-018 mục 3, nay bỏ).
- **Done khi**: Dữ liệu import thấy được qua API, dùng được cho S2-T9/S2-T12
  demo, không còn phụ thuộc `SampleWidget`.

### S2-T7 — Fitment automotive: compatibility + supersession/kit
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S2-T2, S2-T3
- **Việc làm**: Tạo `constraint_instance` loại compatibility giữa Product
  và Vehicle theo điều kiện fitment (vd match make/model/year/engine);
  `relationship_instance` loại `supersedes`/`contains` cho Product. Dùng
  schema sẵn (ADR-003), không thêm bảng.
- **Done khi**: Query "part tương thích với xe X" trả đúng danh sách; part
  bị supersede → API gợi ý part thay thế.

### S2-T8 — Configurator engine: chọn entity + validate constraint
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S2-T2
- **Việc làm**: API chọn 1 entity_instance + các entity liên quan (E2-01),
  validate qua ConstraintEngine (S2-T2), generic — không biết "xe"/"part".
- **Done khi**: Chọn combination hợp lệ → pass; vi phạm constraint → lỗi rõ
  (400 có message), không phải lỗi 500.

### S2-T9 — UI: luồng chọn xe → part tương thích
- **Người phụ trách**: frontend-dev
- **Phụ thuộc**: S2-T6, S2-T7, S2-T8
- **Việc làm**: Màn hình chọn make/model/year/engine → hiện part tương
  thích (DataGrid MUI theo D-015); part có supersession → gợi ý part thay
  thế khi chọn part cũ (E2-02, theo wireframe `docs/ux/`).
- **Done khi**: Chạy local, chọn xe thật từ dữ liệu S2-T6 → thấy đúng part
  tương thích trên UI, không mock.

### S2-T10 — Rule engine hybrid: JSON DSL + Groovy sandbox
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S2-T1
- **Việc làm**: Bảng `rule_set`/`rule` (rule_type DSL|SCRIPT theo D-014),
  `price_list`/`price_list_item` (ADR-003). `RuleEngine` chạy tuần tự theo
  (stage, priority, rule.id) trên pipeline cố định (ADR-004:
  base→segment-adjustment→quantity-adjustment→discount→override→guardrail).
  DSL executor theo cú pháp ADR-002. Groovy executor **sandbox**: dùng
  `CompilerConfiguration` + `SecureASTCustomizer` chặn import I/O/
  reflection/`System`/thread, timeout thực thi cố định (vd 500ms), không
  cho truy cập giờ hệ thống/random không seed trong script (CLAUDE.md mục
  7) — vi phạm → reject rule lúc lưu, không lúc runtime.
- **Done khi**: Test: 1 rule DSL + 1 rule SCRIPT cùng chạy trên 1 input →
  sinh đúng thứ tự waterfall step, cùng input luôn cùng output
  (deterministic, chạy lại 10 lần so sánh bằng nhau). Test riêng: script
  cố ý đọc file/System.exit/`new Date()` → bị chặn lúc validate/compile,
  không chạy được.
- **Trạng thái**: Done (2026-11-13, backend-dev). Migration
  `V7__pricing_rule_engine.sql` (`price_list`/`price_list_item`/
  `rule_set`/`rule`). `core-pricing` có `RuleEngine` generic (pipeline cố
  định ADR-004) + `com.cpq.core.pricing.dsl.DslRuleExecutor` (JSON DSL,
  action `percent-discount`/`fixed-amount`/`set-price`) +
  `com.cpq.core.pricing.script.GroovyRuleExecutor` (sandbox 2 lớp:
  `SecureASTCustomizer` + `ForbiddenConstructCustomizer` tự viết, timeout
  500ms qua `ExecutorService` thread-per-task daemon). API tối thiểu
  `POST /api/pricing/calculate` (`core-api`/`app`, cùng pattern port S1-
  T8). Chi tiết quyết định: D-023 (`docs/decisions.md`). Verify:
  `./gradlew build` — BUILD SUCCESSFUL; `core-pricing` 38 test (7 class)
  0 fail, bao gồm test reject Groovy đọc file/`System.exit`/`new Date()`/
  `Random`/`Math.random`/`Thread.sleep`/reflection lúc compile, test
  timeout vòng lặp vô hạn, 2 test chạy lại 10 lần kết quả giống nhau bit-
  for-bit (DSL+SCRIPT riêng và hybrid); `app` thêm `PricingCalculationServiceIT`
  (4 test, Testcontainers) + `PricingEndpointIT` (5 test, MockMvc+JWT) —
  toàn bộ test Sprint 1/S2-T1 vẫn pass. PR:
  `sprint2-s2-t10-rule-engine-hybrid` → `main`, chưa merge.

### S2-T11 — Price waterfall API
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S2-T10
- **Việc làm**: API tính giá trả danh sách bước điều chỉnh (E3-02): list
  price → mỗi stage → final price, dùng `BigDecimal` scale 6 nội bộ, round
  HALF_UP scale 2 chỉ ở bước cuối (D-009).
- **Done khi**: Test round-trip: tổng các bước waterfall = giá cuối, cho cả
  rule DSL và SCRIPT.
- **Trạng thái**: Done — superseded, implement trong S2-T10 (xem D-023 mục
  8), không cần task riêng.

### S2-T12 — Phân tầng khách automotive + giá hợp đồng riêng
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S2-T4, S2-T11
- **Việc làm**: Rule (DSL hoặc SCRIPT) đọc attribute `tier` của Customer
  (S2-T4) ở stage `segment-adjustment`; giá hợp đồng riêng override tier ở
  stage `override`, ghi rõ "contract override" trong waterfall step
  (E3-04).
- **Done khi**: Cùng Product, Customer khác tier → giá khác đúng theo rule;
  Customer có hợp đồng riêng → giá hợp đồng override tier, thấy đúng nhãn
  trong waterfall.

### S2-T13 — UI tối thiểu: giá + waterfall
- **Người phụ trách**: frontend-dev
- **Phụ thuộc**: S2-T9, S2-T11
- **Việc làm**: Màn hình hiện giá cuối + bảng waterfall cho 1 part đã chọn
  (theo wireframe màn hình 03), chọn Customer demo để thấy giá đổi theo
  tier.
- **Done khi**: Chạy local, chọn xe → part → customer → thấy giá + waterfall
  thật, không mock.

### S2-T14 — QA: integration/E2E theo tiêu chí demo Sprint 2
- **Người phụ trách**: qa-engineer
- **Phụ thuộc**: S2-T9, S2-T12, S2-T13
- **Việc làm**: Test E2E (Playwright, theo mẫu S1-T8) đúng tiêu chí demo
  `docs/roadmap.md` Sprint 2: import Product+Customer → chọn xe → part
  tương thích → giá + waterfall đúng theo tier. Thêm test xác nhận
  deterministic (chạy lại cùng input nhiều lần) và test sandbox Groovy
  (script độc hại bị chặn, không crash app).
- **Done khi**: Suite chạy xanh 2 lần liên tiếp trên stack thật (không
  mock), transcript đính kèm báo cáo.

---

## Tổng hợp phụ thuộc

```
S2-T1 (metamodel) ──┬──> S2-T2 (constraint) ──┬──> S2-T7 (fitment) ──┐
                     │                          │                     │
                     ├──> S2-T3 (Product) ──────┘                     │
                     ├──> S2-T4 (Customer) ─────────────┐             │
                     ├──> S2-T5 (import) ──> S2-T6 (seed)┘            │
                     └──> S2-T10 (rule engine) ──> S2-T11 (waterfall) │
                                                                       │
S2-T2 ──> S2-T8 (configurator) ──────────────────────────────────────┤
S2-T6 + S2-T7 + S2-T8 ──> S2-T9 (UI chọn xe/part) ────────────────────┤
S2-T4 + S2-T11 ──> S2-T12 (tier/contract) ────────────────────────────┤
S2-T9 + S2-T11 ──> S2-T13 (UI giá/waterfall) ──────────────────────────┤
S2-T9 + S2-T12 + S2-T13 ──> S2-T14 (QA E2E)
```

Task không phụ thuộc gì (bắt đầu ngay, chạy song song): S2-T1.
Sau S2-T1 xong, S2-T2/S2-T3/S2-T4/S2-T5/S2-T10 chạy song song được (5
worktree).
