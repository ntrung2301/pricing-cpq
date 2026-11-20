# Backlog — Epics & User Stories

Nhãn: `[core]` = thuộc lõi generic, không chứa kiến thức domain.
`[pack:automotive]` = thuộc domain pack automotive.
Ước lượng: S (<1 ngày) / M (1-2 ngày) / L (3+ ngày, nên tách nhỏ nếu rơi vào
1 sprint).

Story ở đây là khung ban đầu cho Sprint 1-3; chi tiết hoá thêm khi vào từng
sprint (`docs/sprints/sprint-N.md`). Story ID: `EPIC-N`.

---

## Epic 1 — Metamodel & Catalog [core]

### E1-01 [core] (L) Định nghĩa metamodel động
- **Mô tả**: Entity, Attribute, Relationship, Constraint là dữ liệu, không
  phải class Java cứng. Domain pack định nghĩa entity (ví dụ "Part") bằng
  config, không bằng code. Bao gồm `entity_instance` +
  `entity_instance_attribute_value` (EAV có kiểu, ADR-001) — chưa có bảng
  instance thật nào từ Sprint 1, chỉ có bảng định nghĩa.
- **Acceptance criteria**:
  - Tạo được 1 entity mới (ví dụ "TestWidget") hoàn toàn qua config (file
    `pack.yaml`), không sửa code Java nào trong engine, hiển thị được qua
    API.
  - Test tích hợp xác nhận: thêm entity mới không cần thêm/sửa class Java
    nào (theo D-013, không còn ArchUnit quét keyword domain — xác minh
    bằng test tích hợp + review diff, xem `docs/adr/0005-modular-monolith-structure.md`).
  - (D-019) `entity_def` khai báo được `key_attribute_def_id` +
    `label_attribute_def_id`; `entity_instance` có cột denormalize
    `business_key`/`display_label` tự đồng bộ khi ghi giá trị attribute
    tương ứng, unique theo `entity_def_id + pack_id + business_key`.
  - (D-019) `PackLoaderService` từ chối nạp `pack.yaml` nếu 1 entity_def có
    quá 20 attribute_def.

### E1-02 [core] (M) Constraint-as-data, bao gồm constraint loại "compatibility"
- **Mô tả**: Constraint engine generic; "fitment theo xe" sẽ là 1 instance
  của constraint loại compatibility, định nghĩa trong domain pack.
- **Acceptance criteria**:
  - Core có constraint type "compatibility" generic (A tương thích B theo
    điều kiện X), không biết gì về "xe" hay "part".
  - Domain pack automotive định nghĩa fitment dùng constraint này, không
    thêm constraint type mới trong core.

### E1-03 [pack:automotive] (M) Entity automotive: Part, Vehicle, Fitment
- **Mô tả**: Định nghĩa entity Part (key=`sku`, label=`name`, +
  OEM/aftermarket, supersession, kit/bundle, core charge, MOQ, đơn vị đóng
  gói — tối đa 20 attribute theo D-019) và Vehicle (make/model/year/engine)
  qua metamodel, thiết kế để map được ACES/PIES sau này.
- **Acceptance criteria**:
  - Import 1 part mẫu với đủ field trên, query lại đúng theo `sku`
    (business key).
  - Supersession: part A superseded by part B, query ra được chuỗi
    supersession (tham chiếu theo `business_key`, không phải id nội bộ).
  - Field mapping có ghi chú tương ứng ACES/PIES (chưa cần map thật).

### E1-05 [pack:automotive] (M) Entity automotive: Customer
- **Mô tả**: Định nghĩa entity Customer qua metamodel (key=`customer_id`,
  label=`name`, + tier OEM/Distributor/Dealer/Workshop, mã hợp đồng, thông
  tin liên hệ — tối đa 20 attribute theo D-019). Tách riêng khỏi E3-04 vì
  entity definition là việc của Epic 1 (master data), còn E3-04 chỉ là rule
  giá áp theo tier của Customer đã có ở đây.
- **Acceptance criteria**:
  - Import 1 customer mẫu mỗi tier, query lại đúng theo `customer_id`
    (business key).
  - Đổi tier của 1 customer qua update instance (không phải entity_def) →
    query lại thấy tier mới, không ảnh hưởng customer khác.

### E1-04 [core] (M) Catalog import framework (CSV/Excel)
- **Mô tả**: Core cung cấp khung import generic: tên cột trong file
  CSV/Excel phải **match đúng tên** attribute đã định nghĩa trong entity
  definition hiện hành (không cần UI mapping kéo-thả cho MVP — quyết định
  D-005). Domain pack chỉ cung cấp entity definition (tên attribute), core
  không biết tên cột cụ thể.
- **Acceptance criteria**:
  - Import CSV có cột khớp đúng tên attribute → tạo đúng entity instance.
  - Cột sai tên/thiếu cột bắt buộc → báo lỗi rõ theo dòng/cột, không crash
    import, không tự đoán mapping.

---

## Epic 2 — Configurator [core + pack]

### E2-01 [core] (M) Configurator engine: chọn entity + validate constraint
- **Mô tả**: Luồng chọn 1 entity (vd Part) và các entity liên quan, validate
  qua constraint engine (E1-02), generic.
- **Acceptance criteria**:
  - Chọn combination hợp lệ → pass; chọn combination vi phạm constraint →
    báo lỗi rõ, không phải lỗi 500.

### E2-02 [pack:automotive] (M) Luồng chọn xe → part tương thích
- **Mô tả**: UI/API chọn make/model/year/engine, lọc part theo fitment
  constraint.
- **Acceptance criteria**:
  - Chọn xe → chỉ hiện part có fitment khớp.
  - Part có supersession → gợi ý part thay thế khi part cũ bị chọn.

---

## Epic 3 — Pricing Engine [core + pack]

### E3-01 [core] (L) Price list + rule engine (tier/volume/thời hạn/chiết khấu)
- **Mô tả**: Rule lưu dạng dữ liệu (JSON/DSL — chốt bằng ADR), áp theo
  khách/tier/số lượng/thời hạn. Dùng BigDecimal.
- **Acceptance criteria**:
  - Thay đổi rule (vd volume break) qua config, không deploy lại code, kết
    quả tính giá thay đổi đúng.
  - Test: cùng input → luôn cùng output (deterministic).

### E3-02 [core] (M) Price waterfall giải thích được
- **Mô tả**: API tính giá trả về từng bước điều chỉnh (list price → tier
  adjustment → volume break → discount → final), không chỉ số cuối.
- **Acceptance criteria**:
  - Waterfall hiển thị đủ bước, tổng các bước = giá cuối (round-trip kiểm
    chứng được bằng test).

### E3-03 [core] (M) Margin guardrail
- **Mô tả**: Rule chặn/cảnh báo khi giá dưới margin tối thiểu (margin
  guardrail là khái niệm generic, ngưỡng là config).
- **Acceptance criteria**:
  - Giá dưới ngưỡng margin → quote line bị flag, cần phê duyệt (liên kết
    Epic 4 workflow).

### E3-04 [pack:automotive] (M) Phân tầng khách (OEM/Distributor/Dealer/Workshop) + giá hợp đồng riêng
- **Mô tả**: Rule giá áp theo attribute `tier` của entity Customer (đã định
  nghĩa ở E1-05, không định nghĩa lại ở đây), map vào rule engine generic
  của E3-01; giá riêng theo hợp đồng override tier mặc định.
- **Phụ thuộc**: E1-05 (entity Customer phải tồn tại trước khi viết rule
  theo tier).
- **Acceptance criteria**:
  - Cùng part, khách khác tier → giá khác đúng theo rule.
  - Khách có hợp đồng riêng → giá hợp đồng override tier, có ghi trong
    waterfall là "contract override".

---

## Epic 4 — Quote [core]

### E4-01 [core] (M) Tạo quote, line items, version
- **Acceptance criteria**: Tạo quote nhiều line item, sửa quote tạo version
  mới, xem lại version cũ không mất dữ liệu.

### E4-02 [core] (M) Workflow phê duyệt theo ngưỡng chiết khấu/margin
- **Acceptance criteria**: Vượt ngưỡng (config) → quote chuyển trạng thái
  "pending approval", Sales Manager duyệt/từ chối, log lý do.

### E4-03 [core] (M) Xuất PDF
- **Acceptance criteria**: Quote đã duyệt (hoặc không cần duyệt) xuất PDF có
  đủ line item, giá, tổng; PDF tạo lại từ cùng version → nội dung giống
  nhau (deterministic).

---

## Epic 5 — Admin & Security [core]

### E5-01 [core] (M) Quản lý rule/bảng giá qua UI
- **Acceptance criteria**: Pricing Admin tạo/sửa rule qua UI, có validate,
  không cần sửa file config tay bằng SSH.

### E5-02 [core] (M) RBAC (Sales, Sales Manager, Pricing Admin) + OIDC (Keycloak)
- **Acceptance criteria**: Mỗi role thấy đúng chức năng cho phép; đăng nhập
  qua Keycloak; test theo từng role.

### E5-03 [core] (S) Audit log
- **Acceptance criteria**: Mọi thay đổi rule/giá/user/approve có log: ai,
  khi nào, thay đổi gì, truy vấn lại được.

### E5-04 [core] (M) Import user/catalog qua UI (CSV/Excel)
- **Acceptance criteria**: Pricing Admin import catalog qua UI, thấy lỗi
  dòng cụ thể nếu sai.

---

## Epic 6 — Extension Points [core]

### E6-01 [core] (M) SPI cho custom pricing function
- **Acceptance criteria**: Thêm 1 hàm tính giá tùy chỉnh qua plugin, không
  sửa core, có test minh họa.

### E6-02 [core] (S) SPI cho constraint type mới
- **Acceptance criteria**: Thêm 1 constraint type mới qua plugin (không
  phải "compatibility"), không sửa core.

### E6-03 [core] (S) SPI cho connector import/export
- **Acceptance criteria**: Thêm connector export mới qua plugin, core không
  cần biết format cụ thể.

---

## Epic 7 — Domain Pack thứ hai tổng hợp (Validate kiến trúc) [pack:synthetic]

### E7-01 [pack:synthetic] (L) Dựng domain pack tổng hợp thứ hai (Sprint 4)
- **Mô tả**: Theo D-004 — domain pack thật (ngành cụ thể) sẽ được cấu hình
  khi có khách hàng thật, nên Sprint 4 **không** chọn một ngành thật
  (SaaS/thiết bị công nghiệp). Thay vào đó dựng 1 domain pack **tổng hợp,
  trung lập ngành** (entity/attribute/constraint/rule giả định, đơn giản
  hơn automotive) chỉ để chứng minh metamodel đủ generic.
- **Acceptance criteria**:
  - Dựng được luồng tối thiểu (catalog → configure → price → quote) cho
    domain tổng hợp chỉ bằng cấu hình + (nếu cần) extension point đã có,
    không thêm entity/constraint type mới vào core.
  - Nếu phải sửa lõi: ghi rõ chỗ nào, tại sao, vào `docs/decisions.md` —
    đây là tín hiệu ranh giới core/pack sai, không phải lỗi nhỏ.
  - Không ảnh hưởng/regress domain pack automotive đã có.

---

## Epic 8 — Configurable Pricing Engine v2: Entity taxonomy, DAG + Measure, Price List [core]

Theo ADR-009 (`docs/adr/0009-configurable-pricing-dag-and-price-list.md`,
D-037) — PO mở rộng phạm vi sản phẩm sau Sprint 2 (SME pricing platform
tuỳ biến, không chỉ automotive). **Supersede 1 phần Epic 3** (pipeline
6-stage cố định của E3-01/E3-02 → DAG; E3-03 margin guardrail → generalize
thành Parameter `MarginThreshold` + validation node, không còn 1 ngưỡng
cứng). Epic 4/5 (Quote, Admin) **dời sau Epic 8**, không còn là Sprint 3.

### E8-01 [core] (L) Entity taxonomy v2: role Anchor/Extension/Parameter
- **Mô tả**: `entity_def` thêm cột `role` (`ANCHOR` | `EXTENSION` |
  `PARAMETER`) và `extends_entity_def_id` (nullable, chỉ dùng khi
  `EXTENSION` — trỏ tới entity_def Anchor mà nó mở rộng). `Product` và
  `Customer` là **đúng 2 entity_def role=ANCHOR, luôn tồn tại** trong mọi
  deployment — do 1 `CoreAnchorBootstrapService` mới (chạy TRƯỚC
  `PackLoaderService`) tự tạo shell (`code`, `role`, `version=1`, chưa có
  attribute) nếu chưa có, **không phải do pack.yaml khai báo tạo entity
  mới**. Pack (automotive) vẫn là nơi duy nhất thêm attribute cụ thể
  (`sku`, `name`...) + chỉ định `key_attribute`/`label_attribute` cho 2
  entity này, qua đúng cơ chế upsert-by-code đã có (D-019/D-020), không
  cần sửa logic đó.
  - **Composite key cho Extension**: `entity_def_dimension_attribute`
    (bảng mới: `entity_def_id, attribute_def_id, position`) — danh sách
    attribute "chiều phụ" (vd `period`, `city`) của 1 entity Extension.
    `entity_instance` thêm cột `anchor_instance_id` (nullable FK tới
    `entity_instance` khác — chỉ set khi entity có role=EXTENSION, trỏ
    tới dòng Product/Customer nó mở rộng). `business_key` của 1 Extension
    instance = ghép `business_key` của anchor instance + giá trị các
    dimension attribute — vẫn qua đúng cơ chế đồng bộ tự động (D-019),
    tổng quát hoá cho trường hợp nhiều-field.
  - `pack.yaml` (`EntityDescriptor`) thêm field `role`, `extends`,
    `dimension_attributes[]` (chỉ dùng khi `role: EXTENSION`).
- **Acceptance criteria**:
  - App khởi động lần đầu (chưa pack nào chạy) → `entity_def` đã có
    `Product`/`Customer` với `role=ANCHOR`, 0 attribute.
  - Pack automotive nạp xong → 2 entity trên có đủ attribute + key/label,
    **không có class Java nào hardcode field "sku"/"customerId"** (chỉ
    core biết "đây là 2 anchor", không biết nội dung business của chúng —
    test tích hợp xác nhận bằng cách nạp pack tổng hợp khác tên attribute,
    vẫn chạy được).
  - Tạo 1 entity `role=EXTENSION, extends=Product, dimension_attributes=[period]`
    qua pack.yaml → tạo được instance với `anchor_instance_id` đúng +
    `business_key` ghép đúng sku+period, không trùng giữa 2 Product khác
    nhau cùng period.

### E8-02 [pack:automotive] (M) Dữ liệu demo: CostHistory (Extension), MarginThreshold (Parameter), mở rộng Product
- **Mô tả**: Khai báo qua `pack.yaml` (thuần data, không code — D-013):
  - `CostHistory` (role=EXTENSION, extends=Product, dimension
    `period`): attribute `period` (DATE/STRING), `cost` (DECIMAL),
    `quantity` (NUMBER) — đúng dữ liệu đã dùng ở POC DAG (weighted avg
    cost).
  - `MarginThreshold` (role=PARAMETER, key=`level`): attribute `level`
    (STRING: `CRITICAL`/`RED`/`YELLOW`/`GREEN`), `min_margin_pct`,
    `max_margin_pct` (DECIMAL, `max` nullable cho mức cao nhất) — ví dụ
    PO cho: <15% critical, <20% red, <30% yellow.
  - `Product` thêm attribute `country_of_manufacture` (STRING) — dùng cho
    node Round-rule theo quốc gia (đã minh hoạ ở mockup Measure Canvas).
- **Acceptance criteria**: Seed thật (không phải test fixture) — ít nhất 1
  SKU có ≥3 dòng `CostHistory` (nhiều `period` khác nhau), đủ 4 mức
  `MarginThreshold`, `country_of_manufacture` có giá trị cho mọi Product
  demo.

### E8-03 [core] (L) DAG execution engine
- **Mô tả**: Thay `RuleEngine`/`PricingStage` tuyến tính (ADR-004) bằng
  engine chạy theo graph (ADR-009 mục 2): node = data (`id, type, config,
  inputs`), topological sort theo input reference, sinh Calculation Trace
  đầy đủ. Node type tối thiểu: `query` (đọc Anchor/Extension/Parameter qua
  Calculation Binding, mục E8-03b), `formula` (số học qua
  `JsonLogicEvaluator` mở rộng — tái dùng, không viết lại), `weighted-
  average`, `round`, `output`. **Không loop, không biến mutable.**
- **E8-03b — Calculation Binding**: `calculation_binding` +
  `calculation_binding_filter` + `calculation_binding_sort` (ADR-009 mục
  3) — filter/sort/take-first user tự cấu hình, không có fallback ngầm;
  ≥2 dòng mà `take_first=false` → lỗi rõ, chặn tính (runtime lẫn lúc
  preview).
- **Acceptance criteria**: Test tích hợp dựng lại đúng graph đã verify ở
  POC (weighted avg cost → margin → round) chạy qua engine thật, không
  mock; test binding ambiguous (≥2 dòng, không bật take_first) → lỗi rõ,
  không tự chọn.

### E8-04 [core] (M) Measure — subgraph tái dùng
- **Mô tả**: `measure_definition` (`code, name, graph, result_node_id`) +
  node type `measure-ref`. Transclude trace đầy đủ khi gọi lại (không hộp
  đen). Tham số hoá theo context hiện tại (anchor instance đang tính),
  không hardcode 1 instance cụ thể trong định nghĩa.
- **Acceptance criteria**: 1 measure (`weightedAvgCost12M` kiểu) được 2
  graph khác nhau tham chiếu → ra đúng giá trị mỗi lần, trace hiện đủ
  bước bên trong measure ở cả 2 nơi gọi.

### E8-05 [core] (L) Module Price List
- **Mô tả**: Schema theo ADR-009 mục 5 (`price_list` header có
  `status`/`version`/`calculation_rule_set_id`; `price_list_item` có
  `result_price`/`manual_override_price`/`waterfall_steps` JSONB, không
  tách bảng con). API tạo Price List, thêm Product vào, tính qua DAG,
  submit, approve (snapshot vĩnh viễn sau approve).
- **Acceptance criteria**: Tạo Price List dùng `calculation_rule_set_id`
  đọc **cả 4 nguồn** (Product, Customer, `CostHistory` Extension,
  `MarginThreshold` Parameter — xem E8-07 cho graph cụ thể) → ra
  `result_price` đúng, approve xong sửa `CostHistory` nguồn không làm đổi
  `result_price` đã approve (snapshot).

### E8-06 [frontend] (M) Measure Canvas UI
- **Mô tả**: UI kéo-thả xây/xem DAG, theo đúng mockup đã polish (xem
  artifact "Measure Canvas") — context switcher, palette Data
  source/Measure, canvas + Calculation Trace panel đồng bộ expand/
  collapse. **Dùng Ant Design** (theo D-036 — Ant Design đã thay MUI cho
  `web/`, áp dụng luôn cho UI mới này, không dùng lại MUI).
- **Acceptance criteria**: Dựng 1 Price List qua UI thật (không mock), xem
  trace, approve — đúng luồng đã minh hoạ ở mockup.

### E8-07 [pack:automotive] (M) Seed demo tổng hợp — Price List mẫu dùng đủ 4 nguồn
- **Mô tả**: 1 `price_list` thật, `calculation_rule_set_id` trỏ tới graph
  đọc đủ 4 nguồn: `Product` (attribute riêng, vd `country_of_manufacture`
  cho rule làm tròn), `CostHistory` Extension (weighted avg cost),
  `MarginThreshold` Parameter (map margin% ra mức cảnh báo), và `Customer`
  (1 giá trị tham chiếu/thông tin — **PO xác nhận cách dùng cụ thể lúc
  implement**, vì Price List về nguyên tắc không có 1 customer context
  cụ thể — xem NEEDS-PO-REVIEW ở `docs/sprints/sprint-3.md`).
- **Acceptance criteria**: Chạy thật (không test fixture) ra `result_price`
  + mức margin risk, dữ liệu là seed thật trong Postgres, không mock.

---

## Theo dõi

Backlog này map trực tiếp vào `docs/roadmap.md` (sprint nào làm story nào)
và `docs/sprints/sprint-N.md` (chi tiết task). Story mới hoặc đổi scope →
cập nhật cả 3 file.
