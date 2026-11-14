# Sprint 2 — QA sign-off (S2-T14)

**Người phụ trách**: qa-engineer
**Ngày**: 2026-11-14
**Scope**: xác nhận độc lập toàn bộ Sprint 2 (S2-T1 → S2-T13 + 3 prereq)
đạt tiêu chí demo `docs/roadmap.md` Sprint 2, lấp 2 khoảng trống cụ thể
(determinism HTTP-level, sandbox Groovy) do task S2-T14 chỉ định.
Quyết định không hiển nhiên (có thêm test determinism HTTP-level, không
thêm test sandbox ở app thật): D-035 (`docs/decisions.md`).

**Kết luận: Sprint 2 đạt tiêu chí demo.** Không có bug mới phát hiện. 1
gap thật được lấp bằng 1 test mới (xem mục "Gap #1"); 1 gap được đánh giá
và chấp nhận với lý do ghi rõ (xem mục "Gap #2").

---

## 1. Tiêu chí demo (`docs/roadmap.md` Sprint 2)

> "Import master data Product + Customer mẫu → chọn xe → thấy part tương
> thích → chọn part → thấy giá tính ra kèm waterfall đúng theo tier khách
> demo."

| # | Tiêu chí con | Task nguồn | Trạng thái | Bằng chứng |
|---|---|---|---|---|
| 1 | Import master data Product + Customer mẫu | S2-T5/S2-T6 | **Pass** | `DemoDataSeedRunnerIT` 2/2 (app build, Testcontainers thật); xác nhận lại qua `GET /api/catalog/Product/instances`/`GET /api/catalog/Customer/instances` thật (curl, xem mục 3) — 30 Product/8 Customer đã tồn tại khi app chạy thật (idempotent restart, log `DemoDataSeedRunner`/`AutomotivePricingSeedRunner` "already present — skipping seed") |
| 2 | Chọn xe → thấy part tương thích | S2-T7/S2-T8/S2-T9 | **Pass** | Playwright E2E thật (`e2e/tests/vehicle-part-selection.spec.ts`): login Keycloak thật → chọn "Toyota Camry 2018" thật → "Tìm part tương thích" → đúng 4 Product thật đã seed fitment (`BRK-PAD-NEW-001`/`BRK-ROTOR-NEW-001`/`OIL-FLT-NEW-010`/`AIR-FLT-001`) hiện ra, `BRK-PAD-OLD-001`/`SPK-PLUG-001` (fitment xe khác) không xuất hiện — chạy lại 2 lần liên tiếp trên stack thật, cả 2 lần xanh (xem mục 3) |
| 3 | Chọn part → thấy giá + waterfall | S2-T9/S2-T13 | **Pass** | Cùng spec, tiếp tục: "Chọn part này" (`BRK-PAD-NEW-001`) → chọn Customer thật "Acme Motors OEM Division" (`CUST-OEM-001`) → bảng waterfall thật hiện "List price" 55.00 → "OEM tier discount" -8.25 → "Giá cuối: 46.75" — số liệu xác nhận độc lập qua `curl` thật (mục 3), khớp đúng UI |
| 4 | Giá đúng theo tier khách demo | S2-T12 | **Pass** | `TierBasedPricingServiceIT` 2/2 (Testcontainers thật): tier khác nhau → final amount khác nhau (OEM 85.00 vs Dealer 100.00 trên 1 fixture khác); contract override thắng tier discount, label chứa "contract override". Xác nhận độc lập thêm qua `curl` thật: Customer OEM (`CUST-OEM-001`, tier=OEM) trên `AUTOMOTIVE-STANDARD` → đúng rule "OEM tier discount" -15% áp dụng (55.00 → 46.75) |

Toàn bộ 4 tiêu chí con: **Pass**.

---

## 2. `./gradlew build` toàn repo (chạy lại độc lập, không tin báo cáo cũ)

Lệnh: `./gradlew build` (từ worktree `sprint2-s2-t14-qa-signoff`).

Kết quả: `BUILD SUCCESSFUL in 4m 51s`, 47 actionable tasks, 0 fail.

Đếm test thật từ `build/test-results/test/*.xml` (36 file XML, tổng cộng
qua tất cả module `app`/`core-api`/`core-catalog`/`core-constraint-engine`/
`core-metamodel`/`core-pricing`/`core-quote`/`core-security`):
không có file nào có `failures` hoặc `errors` > 0 (xác nhận bằng
`grep -l 'failures="[1-9]' build/test-results/test/*.xml` trên toàn repo
→ không có kết quả, nghĩa là 0 failure trên 0 file nào).

Một số test class tiêu biểu (test ở mức "qua app thật"/Testcontainers,
không chỉ unit):
- `app`: `PricingEndpointIT` 5/5, `PricingCalculationServiceIT` 4/4,
  `TierBasedPricingServiceIT` 2/2, `ConfiguratorEndpointIT` 9/9,
  `CatalogInstanceEndpointIT` 7/7, `AutomotivePricingSeedRunnerIT` 4/4,
  `AutomotiveFitmentSeedRunnerIT` 4/4, `ModuleLayeringArchTest` 2/2 — tất
  cả 0 fail/error.
- `core-pricing`: `RuleEngineTest` 5/5, `HybridRuleEngineTest` 2/2,
  `script.GroovyRuleCompilerTest` 11/11, `script.GroovyRuleExecutorTest`
  6/6, `dsl.DslRuleExecutorTest` 8/8 — tất cả 0 fail/error.

---

## 3. Playwright E2E — chạy 2 lần liên tiếp trên stack thật

Stack: `docker compose` Postgres + Keycloak **đã chạy sẵn** (dùng chung,
container `pricing-cpq-postgres-1`/`pricing-cpq-keycloak-1`, uptime 23h
khi bắt đầu task — không khởi động lại, không tạo container riêng).
Khởi động riêng từ worktree này: `./gradlew :app:bootRun` (port 8080),
`npm run dev` ở `web/` (port 5173). Log khởi động xác nhận seed data
idempotent đã có sẵn (`DemoDataSeedRunner`/`AutomotivePricingSeedRunner`/
`AutomotiveFitmentSeedRunner` — "already present — skipping seed").

### 3.1. Xác nhận tay bằng `curl` trước khi tin bất kỳ assertion nào

1. Lấy access token thật (Resource Owner Password Credentials, client
   `cpq-backend`, user `pricing-admin@example.com`):
   ```
   curl -s -X POST http://localhost:8089/realms/cpq/protocol/openid-connect/token \
     -d grant_type=password -d client_id=cpq-backend \
     -d username=pricing-admin@example.com -d password=pricing-admin-local-dev
   ```
   → token hợp lệ (JWT, `token_type=Bearer`).
2. `GET /api/entities` → `Product` có `id=2` (khớp D-034).
3. `GET /api/catalog/Product/instances?search=BRK-PAD-NEW-001` →
   `id=2`, `businessKey=BRK-PAD-NEW-001` (khớp D-034).
4. `GET /api/catalog/Customer/instances?search=CUST-OEM-001` →
   `id=31`, tier `OEM` (khớp D-034).
5. `POST /api/pricing/calculate` với
   `{"ruleSetCode":"AUTOMOTIVE-STANDARD","priceListCode":"AUTOMOTIVE-DEFAULT","entityDefId":2,"entityInstanceId":2,"quantity":1,"attributes":{},"customerInstanceId":31,"contractOverridePrice":null}`
   → response:
   ```json
   {"steps":[
     {"stepOrder":1,"stage":"base","ruleId":null,"label":"List price","deltaAmount":55.000000,"resultingAmount":55.000000,"rounded":false},
     {"stepOrder":2,"stage":"segment-adjustment","ruleId":1,"label":"OEM tier discount","deltaAmount":-8.250000,"resultingAmount":46.750000,"rounded":false},
     {"stepOrder":3,"stage":"guardrail","ruleId":null,"label":"Final price","deltaAmount":0.000000,"resultingAmount":46.75,"rounded":true}
   ],"finalAmount":46.75}
   ```
6. Gọi lại **đúng request trên lần 2** → `diff` 2 file response → **giống
   nhau tuyệt đối (byte-for-byte)**. Đây là bằng chứng tay cho Gap #1
   trước khi dựa vào test tự động.

### 3.2. `npx playwright test` — chạy 2 lần liên tiếp

Suite gồm 3 spec: `walking-skeleton.spec.ts` (S1-T8),
`vehicle-part-selection.spec.ts` (S2-T9/S2-T13), và spec mới
`pricing-determinism-api.spec.ts` (S2-T14 Gap #1, xem mục 4).

**Lần 1**:
```
Running 3 tests using 3 workers
  ✓ pricing-determinism-api.spec.ts (217ms)
  ✓ walking-skeleton.spec.ts (1.2s)
  ✓ vehicle-part-selection.spec.ts (2.5s)
  3 passed (3.4s)
```

**Lần 2** (chạy lại ngay sau, không sửa gì):
```
Running 3 tests using 3 workers
  ✓ pricing-determinism-api.spec.ts (287ms)
  ✓ walking-skeleton.spec.ts (1.0s)
  ✓ vehicle-part-selection.spec.ts (2.4s)
  3 passed (3.0s)
```

Cả 2 lần: **3/3 pass**, không flaky.

### 3.3. Dọn dẹp môi trường sau verify

`kill` tiến trình `bootRun` (pid con Tomcat 77583) và `vite` (pid con
77622) do task này khởi động. Xác nhận:
```
lsof -i :8080 -i :5173   → rỗng (exit code 1, không có process)
docker ps --filter "name=pricing-cpq"  → chỉ còn pricing-cpq-postgres-1/
                                          pricing-cpq-keycloak-1 (dùng chung,
                                          không đụng tới)
```

---

## 4. Gap #1 — Determinism ở mức HTTP thật (đã lấp)

**Đánh giá trước khi quyết định**: đọc kỹ 3 test đã có —
`RuleEngineTest.sameInputAlwaysProducesSameOutputAcrossTenRuns` +
`HybridRuleEngineTest.sameHybridRuleSetAndContextProduceIdenticalResultAcrossTenReruns`
(in-process `RuleEngine`, không qua Spring/HTTP/JSON) và
`PricingCalculationServiceIT.sameRequestProducesIdenticalResponseAcrossTenCalls`
(service layer, Testcontainers Postgres thật, nhưng gọi trực tiếp
`PricingCalculationService.calculate(...)` trong Java, không qua
servlet/JSON). Cả 3 đều nghiêm ngặt và đều pass khi chạy lại
(`./gradlew :core-pricing:test` cho 2 class đầu — pass; test class thứ 3
đã pass trong `./gradlew build` ở mục 2). Nhưng **không có test nào gọi
`POST /api/pricing/calculate` qua một socket HTTP thật tới một tiến trình
app đã khởi động sẵn** — tầng Jackson serialize `BigDecimal` qua
`WaterfallStepSummary`/`PriceCalculationResponse` và tầng Spring MVC
dispatch thật chưa từng được test determinism.

**Quyết định**: thêm 1 test mới, không bỏ qua (chi tiết + lý do đầy đủ:
D-035). File mới: `e2e/tests/pricing-determinism-api.spec.ts` (Playwright
`request` fixture — gọi HTTP thuần, không cần browser):
1. Lấy access token Keycloak thật (ROPC, client `cpq-backend`).
2. Resolve `entityDefId`/`entityInstanceId`/`customerInstanceId` thật qua
   API (không hardcode id).
3. Gọi `POST /api/pricing/calculate` 2 lần liên tiếp với cùng request lên
   app thật đang chạy (`./gradlew :app:bootRun`, cổng 8080 — không phải
   Testcontainers/MockMvc tự spin context riêng).
4. `expect(second).toStrictEqual(first)` — so sánh toàn bộ response, từng
   `steps[*].deltaAmount`/`resultingAmount`/`rounded` + `finalAmount`.
5. Assert thêm giá trị tuyệt đối đã biết đúng (`finalAmount === 46.75`) —
   phòng trường hợp 1 regression deterministic nhưng sai vẫn bị bắt, không
   chỉ "2 lần giống nhau".

Kết quả: pass cả khi chạy riêng (`npx playwright test
pricing-determinism-api`) và trong suite đầy đủ 2 lần liên tiếp (mục 3.2).

---

## 5. Gap #2 — Sandbox Groovy ở mức "qua app thật" (đã đánh giá, không lấp bằng E2E)

**Hiện trạng xác nhận**: rà soát `core-api`/`app` — không có API public nào
cho phép submit/lưu rule Groovy tuỳ ý lúc runtime. Rule Groovy hiện chỉ
được tạo qua `AutomotivePricingSeedRunner` (seed code cố định trong repo)
hoặc test. Admin UI quản lý rule (E5-01) là Sprint 3, chưa làm.

**Quyết định**: không viết test E2E giả lập 1 API submit-rule không tồn
tại (sẽ không kiểm tra được gì thật — chỉ là test tự chế ra bề mặt tấn
công giả). Thay vào đó, xác nhận lại độ nghiêm ngặt của test unit/
integration đã có ở `core-pricing` bằng cách **đọc kỹ + chạy lại thật**
(không suy đoán từ báo cáo cũ):

- `GroovyRuleCompilerTest` (11 test, compile-time gate — D-014/CLAUDE.md
  mục 7): chặn `import java.io.File` + fully-qualified
  `new java.io.File(...)` (không cần import mới chặn được —
  `ForbiddenConstructCustomizer` bắt theo AST thật, không chỉ theo tên
  import), `System.exit`, `System.currentTimeMillis`, `new Date()`,
  `new Random().nextInt()` (seeded hay không đều chặn), `Math.random`,
  `Thread.sleep`, `Class.forName(...).getMethod(...)` (reflection),
  `new ProcessBuilder(...)`. Chạy lại: `./gradlew :core-pricing:test
  --tests "com.cpq.core.pricing.script.GroovyRuleCompilerTest"` →
  **11/11 pass**.
- `GroovyRuleExecutorTest` (6 test): script độc hại (`System.exit(1)`) bị
  reject **trước khi chạy** (không phải chạy rồi mới bắt lỗi — an toàn cho
  cả JVM test), vòng lặp vô hạn (`while(true){x=x+1}`) bị timeout chặn,
  trả về lỗi `RuleExecutionException` trong <5s, không treo caller. Chạy
  lại: **6/6 pass**.

Cả 2 class này cũng nằm trong lần `./gradlew build` toàn repo ở mục 2 (0
fail/error).

**Kết luận ghi vào báo cáo** (đúng yêu cầu task, nguyên văn ý): chưa có bề
mặt API thật để test ở mức E2E vì chưa có rule-authoring endpoint (Sprint
3/E5-01) — rủi ro chấp nhận được ở MVP vì chỉ Pricing Admin kỹ thuật viết
Script rule qua seed/config, không phải input từ user cuối. Khi E5-01
(admin UI quản lý rule) được làm ở Sprint 3, task QA tương ứng **phải**
thêm lại test sandbox ở mức E2E thật qua endpoint đó — ghi nhận là việc
còn thiếu cho Sprint 3, không phải "đã xong vĩnh viễn".

---

## 6. Regression / layering

- `ModuleLayeringArchTest` 2/2 pass (trong `./gradlew build` mục 2) — core
  modules vẫn free of cycles (ADR-005), không có import domain-specific
  lọt vào core.
- Không có task Sprint 2 nào bị regress bởi task khác: toàn bộ test cũ
  Sprint 1 (`EntitiesEndpointIT`, `PingEndpointSecurityIT`,
  `CorsConfigurationIT`, `KeycloakRealmRoleConverterTest`) vẫn 0 fail
  trong lần build này.

---

## 7. Bug phát hiện

Không phát hiện bug mới trong quá trình QA sign-off này. 2 khoảng trống
(Gap #1/#2) không phải bug — là thiếu bằng chứng test ở 1 tầng cụ thể
(Gap #1, đã lấp) hoặc thiếu bề mặt API chưa tới sprint (Gap #2, chấp nhận
được, ghi nợ kỹ thuật cho Sprint 3).

---

## 8. Kết luận DoD S2-T14

**Đạt.** Tất cả acceptance criteria của task S2-T14 đã thực hiện:
- Playwright E2E chạy xanh 2 lần liên tiếp trên stack thật (mục 3.2).
- Test determinism bổ sung ở mức HTTP thật, không chỉ unit test
  `RuleEngine` (mục 4).
- Test sandbox Groovy đã có được xác nhận lại nghiêm ngặt (mục 5); không
  bịa API giả để test ở mức E2E (lý do ghi rõ).
- Tiêu chí demo Sprint 2 (`docs/roadmap.md`) — 4/4 tiêu chí con Pass (mục
  1).
- Mọi tiến trình riêng của task đã dừng sạch sau verify (mục 3.3).
