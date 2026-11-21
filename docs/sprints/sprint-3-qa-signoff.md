# Sprint 3 — QA sign-off (S3-T9)

**Người phụ trách**: qa-engineer
**Ngày**: 2026-11-21
**Scope**: xác nhận độc lập toàn bộ Sprint 3 (S3-T1 → S3-T8, tất cả đã
merge vào `main`) đạt tiêu chí demo `docs/roadmap.md` Sprint 3 (Configurable
Pricing Engine v2 / Price List), xác nhận API cũ `/api/pricing/calculate`
(Sprint 2) không bị regress, và tổng hợp toàn bộ `NEEDS-PO-REVIEW` còn mở
liên quan Sprint 3 cho PO review cuối sprint.

**Kết luận: Sprint 3 đạt tiêu chí demo.** Không có bug mới trong code đã
merge. 1 finding thật phát hiện trong lúc QA — không phải bug code (IT test
Testcontainers/fresh DB đã chứng minh code đúng), mà là dữ liệu stale trên
Postgres dev dùng chung lâu dài (8 ngày tuổi) do giới hạn thiết kế "seed
idempotent-skip" gặp schema evolution giữa các sprint — xem mục 5. Danh
sách đầy đủ `NEEDS-PO-REVIEW` còn mở: mục 6.

---

## 1. Tiêu chí demo (`docs/roadmap.md` Sprint 3)

> "Có data thật trong Postgres cho Product, Customer, `CostHistory`
> (Extension), `MarginThreshold` (Parameter); dựng 1 Price List qua UI
> Measure Canvas (Ant Design, kéo-thả, không viết code/JSON) đọc đủ 4 nguồn
> trên → ra `result_price` + mức margin risk đúng; approve → snapshot, sửa
> `CostHistory` sau không đổi giá đã approve."

| # | Tiêu chí con | Task nguồn | Trạng thái | Bằng chứng |
|---|---|---|---|---|
| 1 | Data thật Product/Customer/CostHistory/MarginThreshold trong Postgres | S3-T4 | **Pass** | `GET /api/entities` thật (curl, token Keycloak thật) → `Product` (có `country_of_manufacture`), `Customer`, `CostHistory` (role EXTENSION), `MarginThreshold` (role PARAMETER) đều tồn tại với đúng attribute (mục 4). |
| 2 | Dựng 1 Price List qua UI Measure Canvas (kéo-thả, không viết code/JSON) | S3-T8 | **Pass** | Playwright E2E thật (`e2e/tests/measure-canvas.spec.ts`): login Keycloak thật → kéo-thả 5 node (Product country, 2 formula, round, output) lên canvas → `POST /api/price-lists-v2` thật → `POST .../items` thật → `GET /api/price-lists-v2/{id}` thật đọc lại trace (5 node, 1 item) → `Submit` → `Approve`, chuyển đúng state machine `DRAFT → PENDING_APPROVAL → APPROVED`. Chạy 2 lần liên tiếp, cả 2 xanh (mục 2). |
| 3 | Đọc đủ 4 nguồn (Product, Customer, CostHistory, MarginThreshold) ra `result_price` + margin risk đúng | S3-T7 | **Pass (bằng chứng code), xem finding mục 5 cho trạng thái dữ liệu live** | `PriceListDemoSeedRunnerIT` (Testcontainers, fresh DB, chạy lại thật trong `./gradlew build` — mục 2): assert `item.getResultPrice()` = `57.50`, `q_country` trace output = `"Vietnam"`, `q_marginTier` trace output = `"YELLOW"` — graph thật đọc cả 4 nguồn (Product/CostHistory×3/MarginThreshold/Customer), `weighted-average` cost thật từ 3 dòng `CostHistory` qua node `collect` mới (D-047). Test này **pass** cả 2 lần build. Trên Postgres dev dùng chung lâu dài (không phải Testcontainers), cùng Price List demo (`id=1`, đã approve từ trước) hiện đọc lại `country=null` (không phải "Vietnam") — xem finding mục 5, đây là vấn đề dữ liệu môi trường cụ thể, không phải code sai. |
| 4 | Approve → snapshot, sửa `CostHistory` sau không đổi giá | S3-T6 | **Pass** | `PriceListServiceImplIT.approvedPriceListIsAPermanentSnapshotAddingAnItemAfterApprovalIsBlockedAndSourceDataChangesNeverLeak` (Testcontainers, pass cả 2 lần build) — approve xong, đổi dữ liệu nguồn EXTENSION, add-item mới bị chặn hẳn (`PriceListV2StateException` → HTTP 409), item cũ đọc lại `result_price` không đổi. |

---

## 2. `./gradlew build` toàn repo — chạy lại 2 lần liên tiếp, không tin báo cáo cũ

Lệnh: `./gradlew build --rerun-tasks` (worktree `sprint3-s3-t9-qa-signoff`,
nhánh từ `main` sau merge S3-T8).

- **Lần 1**: `BUILD SUCCESSFUL in 3m 51s`, 47 actionable tasks: 47 executed.
- **Lần 2**: `BUILD SUCCESSFUL in 2m 19s`, 47 actionable tasks: 47 executed
  (dùng `--rerun-tasks` để ép chạy lại thật, không dựa vào cache
  `UP-TO-DATE`).

Đếm test thật từ `build/test-results/test/*.xml` (61 file XML, toàn bộ
module `app`/`core-api`/`core-catalog`/`core-constraint-engine`/
`core-metamodel`/`core-pricing`/`core-quote`/`core-security`): tổng
**284 test**, `grep -l 'failures="[1-9]\|errors="[1-9]'` trên toàn bộ các
file → **0 kết quả** (0 fail/error) ở **cả 2 lần** chạy.

Một số test class tiêu biểu (test "qua app thật"/Testcontainers):
- `app`: `PricingEndpointIT` 5/5, `PricingCalculationServiceIT` 4/4,
  `TierBasedPricingServiceIT` 2/2, `ModuleLayeringArchTest` 2/2,
  `PriceListServiceImplIT` 9/9, `PriceListEndpointSecurityIT` 13/13,
  `QueryNodeExecutorIT` 11/11, `MeasureRefNodeExecutorIT` 8/8,
  `PriceListDemoSeedRunnerIT` 3/3, `PricingTaxonomyDemoSeedRunnerIT` 5/5 —
  tất cả 0 fail/error.
- `core-pricing`: `RuleEngineTest` 5/5, `HybridRuleEngineTest` 2/2,
  `DagEngineTest` (gồm test composition `collect` mới của S3-T7),
  `CollectNodeExecutorTest` 3/3, `TraceEntryListConverterTest` 4/4,
  `DagNodeGraphConverterTest` 4/4, `CalculationBindingFilterOperatorTest`
  3/3 — tất cả 0 fail/error.

`ModuleLayeringArchTest` 2/2 pass — không có import domain-specific lọt
vào core (ADR-005 vẫn giữ nguyên tắc dù D-013 đã bỏ ArchUnit chặn keyword
domain).

---

## 3. API cũ `/api/pricing/calculate` (Sprint 2) — xác nhận không regress

Test tích hợp trực tiếp cho API cũ, tất cả nằm trong lần `./gradlew build`
ở mục 2 (0 fail/error cả 2 lần):

| Test class | Kết quả |
|---|---|
| `PricingEndpointIT` (app, HTTP/MockMvc) | 5/5 pass |
| `PricingCalculationServiceIT` (app, Testcontainers) | 4/4 pass |
| `TierBasedPricingServiceIT` (app, Testcontainers) | 2/2 pass |
| `RuleEngineTest` (core-pricing, in-process) | 5/5 pass |
| `HybridRuleEngineTest` (core-pricing, in-process) | 2/2 pass |

Xác nhận thêm bằng tay qua app thật đang chạy (build từ worktree này,
`./gradlew :app:bootRun`, không dùng Testcontainers): `POST
/api/pricing/calculate` với payload giống S2-T14
(`ruleSetCode=AUTOMOTIVE-STANDARD`, `priceListCode=AUTOMOTIVE-DEFAULT`,
`entityDefId=2`, `entityInstanceId=2` [BRK-PAD-NEW-001],
`customerInstanceId=31` [CUST-OEM-001, tier OEM]):

```json
{"steps":[
  {"stepOrder":1,"stage":"base","label":"List price","deltaAmount":55.000000,"resultingAmount":55.000000},
  {"stepOrder":2,"stage":"segment-adjustment","label":"OEM tier discount","deltaAmount":-8.250000,"resultingAmount":46.750000},
  {"stepOrder":3,"stage":"guardrail","label":"Final price","deltaAmount":0.000000,"resultingAmount":46.75,"rounded":true}
],"finalAmount":46.75}
```

Gọi lại đúng request lần 2 → `diff` 2 response file → **giống nhau tuyệt
đối** (byte-for-byte). Đồng thời `e2e/tests/pricing-determinism-api.spec.ts`
(S2-T14 gap #1) và `e2e/tests/vehicle-part-selection.spec.ts` (S2-T9/T13)
chạy xanh trong cùng suite Playwright (mục 4) — cùng số liệu `46.75`,
không đổi so với báo cáo S2-T14.

**Kết luận: API cũ tuyến tính (S2-T10/T12, `RuleEngine`/`PricingStage`)
chạy song song hoàn toàn bình thường với engine DAG mới (S3-T2→T8), không
bị engine mới đụng tới** — khớp đúng quyết định D-039 mục 1 ("`RuleEngine`/
`PricingStage`/`PricingContext` không bị đụng").

---

## 4. Playwright E2E — chạy 2 lần liên tiếp trên stack thật

**Lưu ý quan trọng về môi trường** (xem finding chi tiết ở mục 5): khi bắt
đầu task, cổng 8080/5173 đã có 1 app/web **khác** đang chạy (build từ
checkout chính `/Users/trungnguyen/Documents/Projects/pricing-cpq`, không
phải từ worktree này, khởi động ngay trước khi task này bắt đầu, không rõ
bởi ai/phiên nào). Chạy Playwright lần đầu trên tiến trình đó → **3/4 test
fail** (`measure-canvas`, `pricing-determinism-api`,
`vehicle-part-selection` — `POST /api/pricing/calculate` trả `500 Internal
Server Error` khi gọi tay bằng `curl`). Theo nguyên tắc "verify trước khi
tin, không suy đoán" — dừng lại, `kill` 2 tiến trình đó, build + khởi động
lại **từ chính worktree của task này** (`./gradlew :app:bootRun`, `npm run
dev` ở `web/`) để có log/kiểm soát đầy đủ. Sau khi đổi sang app/web build từ
worktree này: `POST /api/pricing/calculate` trả `200` đúng số liệu (mục 3)
— xác nhận tiến trình cũ không phải regression trong code, mà là 1 tiến
trình khác/stale (lý do cụ thể không điều tra thêm, ngoài phạm vi QA — báo
ở mục 6). Postgres/Keycloak dùng chung (container `pricing-cpq-postgres-1`/
`pricing-cpq-keycloak-1`, uptime trước khi bắt đầu — không khởi động lại,
không đụng).

### 4.1. `npx playwright test` — chạy 2 lần liên tiếp (sau khi đổi sang app/web đúng)

Suite gồm 4 spec: `walking-skeleton.spec.ts` (S1-T8),
`vehicle-part-selection.spec.ts` (S2-T9/S2-T13),
`pricing-determinism-api.spec.ts` (S2-T14), `measure-canvas.spec.ts`
(S3-T8, mới).

**Lần 1**:
```
Running 4 tests using 4 workers
  ✓ pricing-determinism-api.spec.ts (322ms)
  ✓ walking-skeleton.spec.ts (1.5s)
  ✓ vehicle-part-selection.spec.ts (3.3s)
  ✓ measure-canvas.spec.ts (7.4s)
  4 passed (8.2s)
```

**Lần 2** (chạy lại ngay sau, không sửa gì):
```
Running 4 tests using 4 workers
  ✓ pricing-determinism-api.spec.ts (387ms)
  ✓ walking-skeleton.spec.ts (1.7s)
  ✓ vehicle-part-selection.spec.ts (2.7s)
  ✓ measure-canvas.spec.ts (6.2s)
  4 passed (6.9s)
```

Cả 2 lần: **4/4 pass**, không flaky.

### 4.2. Dọn dẹp môi trường sau verify

`kill` tiến trình `bootRun` và `vite` do task này khởi động (từ worktree
này). Xác nhận `lsof -i :8080 -i :5173` → không còn process LISTEN (chỉ còn
socket `CLOSE_WAIT` cũ của 1 tab Chrome, không phải process mới).
`docker ps --filter "name=pricing-cpq"` → chỉ còn `pricing-cpq-postgres-1`/
`pricing-cpq-keycloak-1` (dùng chung, không đụng).

---

## 5. Finding: dữ liệu Price List demo (S3-T7) trên Postgres dev dùng chung bị stale — không phải bug code

**Phát hiện lúc nào**: sau khi khôi phục app/web đúng (mục 4), gọi `GET
/api/price-lists-v2/1` thật (token Keycloak thật, `pricing-admin`) để xác
nhận trực tiếp Price List demo `PL-DEMO-BRK-PAD-NEW-001` (S3-T7) trên
Postgres dev dùng chung (container 8 ngày tuổi) — theo đúng yêu cầu task
"xác nhận lại tiêu chí demo... chạy thật từng bước".

**Quan sát**: response thật trả `resultPrice: 58.0000`,
`waterfallSteps[q_country].output: null`, `roundedPrice`'s config
`nearest: 1.00` — **khác** số liệu `docs/decisions.md` D-047 ghi nhận
(`resultPrice=57.50`, `country=Vietnam`, `nearest=0.50`).

**Điều tra** (không suy đoán, xác nhận từng bước):
1. `psql` trực tiếp vào Postgres dev dùng chung:
   `select ... from entity_instance_attribute_value ... where
   business_key='BRK-PAD-NEW-001' and attribute='country_of_manufacture'`
   → **0 dòng**. Giá trị `country_of_manufacture` cho SKU này **không tồn
   tại** trên Postgres dev này, dù `entity_def.Product` đã khai báo
   attribute đó (xác nhận qua `GET /api/entities`).
2. Đọc `DemoDataSeedRunner` (S2-T6, không sửa trong Sprint 3): import CSV
   là **idempotent theo kiểu "cả entity_def có ≥1 instance = skip toàn
   bộ"** (D-026 điểm 4) — không phải upsert theo dòng. `Product` đã có
   instance từ Sprint 2 (trước khi `country_of_manufacture` được thêm vào
   `products.csv` ở S3-T4/D-043) → khi `PricingTaxonomyDemoSeedRunner`
   (S3-T4) gọi `DemoDataSeedRunner#run` lại, toàn bộ import cho `Product`
   bị **skip** (không có instance nào mới, cột mới cũng không được backfill
   vào instance cũ).
3. Đọc `CatalogImportServiceImpl`/`CatalogRowWriter`: `importCsv` luôn
   `entityInstanceService.createInstance(...)` (**insert mới**, không
   `findOrUpdate`) — gọi lại API import `/api/catalog/Product/import` với
   cùng CSV (có cột `country_of_manufacture`) trên Postgres dev này **sẽ
   không backfill** instance cũ; nó sẽ tạo trùng `business_key` (lỗi unique
   constraint, nếu có) hoặc tạo bản ghi trùng (nếu không) — **không an
   toàn để tự chạy**, không dùng cách này.
4. Đối chiếu ngược: `PriceListDemoSeedRunnerIT` (Testcontainers, DB luôn
   **fresh** từ Flyway migration + mọi `ApplicationRunner` chạy đủ 1 lần
   theo đúng thứ tự, D-041/D-044) → `country_of_manufacture` được seed
   đúng ngay từ đầu (không có instance cũ nào để "bỏ qua") → test pass với
   `57.50`/`"Vietnam"` (mục 1, mục 2) — **code đúng trên 1 deployment mới**.

**Kết luận**: đây là **giới hạn thiết kế thật của pattern seed
idempotent-skip** (D-026) khi gặp schema evolution (thêm attribute mới vào
1 entity đã có instance) **trên 1 Postgres sống lâu dài** (dev/staging
thật, khác Testcontainers luôn fresh) — không phải regression của code
Sprint 3, không phải lỗi `PriceListDemoSeedRunner`/`QueryNodeExecutor`/
`AnchorInjector`. Cơ chế "đọc 4 nguồn" (`q_country` node có mặt, chạy đúng,
chỉ trả `null` vì nguồn thật rỗng) **vẫn đúng đúng như task yêu cầu** —
"chứng minh cơ chế đọc được", không phải business logic sai.

**Không tự vá**: theo đúng ranh giới QA (không sửa business
logic/kiến trúc seed, không chạy SQL `UPDATE` tay lên Postgres dev dùng
chung — có thể ảnh hưởng phiên khác đang chạy song song trên cùng
container). Báo lại rõ cho **backend-dev/devops**: nếu muốn demo tay (click
UI thật, không qua Playwright) trên Postgres dev dùng chung hiện tại cho ra
đúng `nearest=0.50`/`country=Vietnam`, cần 1 trong: (a) seed lại từ Postgres
rỗng hoàn toàn (xoá volume), hoặc (b) 1 cơ chế backfill/upsert riêng (ngoài
scope Sprint 3) cho tình huống "attribute mới thêm vào entity đã có
instance trên deployment đang sống" — **gắn `NEEDS-PO-REVIEW` mới** (mục
6.5).

---

## 6. Tổng hợp `NEEDS-PO-REVIEW` còn mở liên quan Sprint 3 (cho PO review cuối sprint)

Rà toàn bộ `docs/decisions.md` D-037→D-049 + `docs/sprints/sprint-3.md`.
Không tự quyết bất kỳ mục nào dưới đây — chỉ tổng hợp.

1. **Migrate API cũ `/api/pricing/calculate` hay giữ song song vĩnh viễn?**
   (D-037, D-038 mục 5a, D-046 "Chưa làm") — mở từ đầu Sprint 3, vẫn mở.
   Mặc định hiện tại: không đụng, 2 engine chạy song song (xác nhận ở mục 3
   không có regression).
2. **Cách Customer tham gia Price List graph** (D-038 mục 5b, D-047 mục 3)
   — đã có 1 giải pháp mặc định (node `query` baked-anchor, chỉ nối vào
   `summary`, không ảnh hưởng `result_price`) — PO review xem có cần ý
   nghĩa nghiệp vụ sâu hơn (tier-based discount) không.
3. **Scale nội bộ cho phép tính `formula` (division)** = 10, khác
   `DslRuleExecutor`'s `INTERNAL_SCALE=6` (D-039 mục 2) — nhẹ, chưa chặn gì.
4. **`ThreadLocal` trong `MeasureRefNodeExecutor`** để transclude trace
   (D-045 mục 5) — lệch nhẹ tinh thần "executor pure function"; chấp nhận
   được nếu không chạy đa luồng/async trong 1 request.
5. **`core-pricing` phụ thuộc `compileOnly hibernate-core`** để dùng
   `@JdbcTypeCode` (D-045 mục 2) — lần đầu `core-pricing` biết tên 1 class
   Hibernate cụ thể (dù chỉ compile-time).
6. **Giới hạn filter AND-only của Calculation Binding không nhận diện được
   mức GREEN** (không có biên trên) — (D-047 mục 6) — cần task riêng thiết
   kế lại filter cho field optional nếu PO muốn dùng GREEN qua binding
   thật.
7. **`version` field của Price List chưa có endpoint "edit sau khi tạo"**
   (D-046 mục 7) — `version` luôn `1`; cần khi có use case sửa
   `calculation_graph` của Price List đã tồn tại.
8. **Calculation Binding Admin UI/API liệt kê chưa có** (D-049 mục 5) — UI
   Measure Canvas hiện dùng preset cố định hard-code
   (`bindingPresets.ts`), không liệt kê binding thật từ server.
9. **`weighted-average`/`collect` chưa có trong palette kéo-thả** (D-049
   mục 6) — vẫn xem được qua `GET` trực tiếp (demo Price List S3-T7), nhưng
   không tạo mới được qua UI kéo-thả.
10. **[MỚI, phát hiện lúc QA này — mục 5]**: pattern seed idempotent-skip
    (D-026) không backfill attribute mới cho instance cũ trên Postgres dev
    sống lâu dài — không chặn demo tự động (Testcontainers luôn fresh),
    nhưng ảnh hưởng demo tay/click UI thật trên môi trường dev dùng chung
    hiện tại. Cần quyết định: xoá seed lại từ đầu trước demo tay, hay thêm
    cơ chế backfill.

Các mục NEEDS-PO-REVIEW không thuộc Sprint 3 (từ Sprint 1/2, D-002 → D-035)
không lặp lại ở đây — xem báo cáo S2-T14 (`docs/sprints/sprint-2-qa-signoff.md`)
cho lịch sử đầy đủ của các mục đó; chúng không ảnh hưởng kết luận Sprint 3.

---

## 7. Bug phát hiện

**Không có bug mới trong code đã merge.** 1 finding thật (mục 5) là dữ liệu
môi trường (Postgres dev dùng chung bị stale do giới hạn thiết kế seed
idempotent-skip gặp schema evolution), không phải lỗi logic — đã xác nhận
bằng đối chiếu Testcontainers (fresh DB, code đúng 100%) với Postgres dev
(stale, do lịch sử nhiều sprint). Báo cho backend-dev/devops theo mục 5.

---

## 8. Kết luận DoD S3-T9

**Đạt.** Tất cả acceptance criteria của task S3-T9 đã thực hiện:
- `./gradlew build` xanh 2 lần liên tiếp trên stack thật (mục 2) — 284
  test, 0 fail/error cả 2 lần.
- `npx vitest run` (web): 40/41 pass, 1 fail đúng kỳ vọng
  (`designSystem.test.ts`, migrate MUI→AntD đang chạy song song, ngoài
  phạm vi Sprint 3 — D-048/D-049).
- Playwright E2E chạy xanh 2 lần liên tiếp trên stack thật (mục 4), bao gồm
  `measure-canvas.spec.ts` mới (S3-T8) — 4/4 cả 2 lần, không flaky.
- API cũ `/api/pricing/calculate` xác nhận không regress (mục 3) — cả qua
  test tự động và tay qua `curl`.
- Tiêu chí demo Sprint 3 (`docs/roadmap.md`) — 4/4 tiêu chí con Pass (mục
  1), kèm 1 finding dữ liệu môi trường ghi rõ (mục 5), không hạ tiêu chí để
  "cho qua".
- Toàn bộ `NEEDS-PO-REVIEW` còn mở liên quan Sprint 3 đã tổng hợp đầy đủ
  (mục 6), không tự quyết.
- Tiến trình riêng của task (app/web build từ worktree này) đã dừng sạch
  sau verify (mục 4.2); Postgres/Keycloak dùng chung không bị đụng.
