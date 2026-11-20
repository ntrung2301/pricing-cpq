# Roadmap — 5 Sprint (1 tuần/sprint, mở rộng từ 4 theo D-037/ADR-009)

Mục tiêu tổng: MVP chạy được end-to-end cho automotive — import catalog →
chọn xe/part → cấu hình → tính giá (waterfall) → tạo quote → phê duyệt →
PDF — **cộng thêm** (từ Sprint 3, theo ADR-009) 1 pricing engine tuỳ biến
được (DAG + Measure + Price List) phục vụ mục tiêu sản phẩm rộng hơn (SME
pricing platform, không chỉ automotive). Chi tiết task từng sprint:
`docs/sprints/sprint-N.md` (sprint-1/2 có sẵn; sprint-3/4/5 viết ở đầu
sprint tương ứng, dựa trên backlog). Sprint 3 gốc ("Hoàn thiện MVP") và
Sprint 4 gốc ("Harden") đã dời thành Sprint 4/5 — xem ghi chú ở Sprint 3
dưới.

---

## Sprint 1 — Nền móng

**Mục tiêu**: Có nền để code — kiến trúc đã quyết (ADR), UX chính đã có
wireframe, repo/CI/auth/data model cơ bản chạy được, walking skeleton
end-to-end (dù rỗng/giả lập) chạy từ UI → API → DB.

**Task đầu tiên (không phụ thuộc gì)**:
1. ADR spike (architect) — 7 ADR nền tảng, xem `docs/adr/README.md`.
2. Wireframe 4 màn hình chính (designer), xem `docs/ux/README.md`.

**Deliver sau đó (phụ thuộc 1-2 khi cần)**:
- Repo skeleton Java (Spring Boot, modular monolith) + React (Vite+TS).
- CI (GitHub Actions): build + test + ArchUnit gate.
- Auth: Keycloak chạy local (docker-compose), OIDC login cơ bản.
- Data model khởi điểm: entity/attribute/relationship/constraint tables
  (theo ADR metamodel).
- Walking skeleton: 1 API thật (vd "list entities") + 1 màn hình React gọi
  API đó, deploy local chạy được đầu-cuối.

**Tiêu chí demo**: Login qua Keycloak → màn hình rỗng gọi API lõi trả dữ
liệu thật từ Postgres → CI xanh có ArchUnit gate chạy (dù rule còn ít).

**Rủi ro**: Chọn sai rule engine/PDF lib ảnh hưởng Sprint 2-3 (giảm bằng ADR
spike kỹ ngay đầu). Wireframe trễ làm frontend-dev chờ (giảm bằng để backend
làm trước các phần không cần UI).

---

## Sprint 2 — Core loop

**Mục tiêu**: Metamodel, catalog, configurator, pricing engine chạy được
theo lát cắt dọc (vertical slice) — mỗi phần đi từ data model → API → UI tối
thiểu, không làm ngang hết lớp này mới sang lớp khác.

**Deliver** (map Epic 1, 2, 3 trong backlog; thứ tự theo lát cắt — master
data trước, xem `docs/sprints/sprint-2.md`):
- E1-01 (metamodel: entity_instance + key/label attribute + cap 20, D-019)
  [core]
- E1-03 (entity automotive Product/Vehicle, key=sku) [pack:automotive]
- E1-05 (entity automotive Customer, key=customer_id, D-019) [pack:automotive]
- E1-04 (catalog import CSV cho Product + Customer) [core]
- E1-02 (constraint-as-data, fitment) [core+pack]
- E2-01, E2-02 (configurator + luồng chọn xe/part) [core+pack]
- E3-01, E3-02 (rule engine + price waterfall) [core]
- E3-04 (phân tầng khách automotive, phụ thuộc E1-05) [pack:automotive]

**Tiêu chí demo**: Import master data Product + Customer mẫu → chọn xe →
thấy part tương thích → chọn part → thấy giá tính ra kèm waterfall đúng
theo tier khách demo.

**Rủi ro**: Rule engine chọn ở Sprint 1 không đủ linh hoạt cho tier+volume
break+contract override cùng lúc — nếu lộ ra, quay lại ADR, không vá tạm.

---

## Sprint 3 — Configurable Pricing Engine v2 (Price List)

**Thay đổi phạm vi (2026-11-20, theo D-037/ADR-009)**: Sau Sprint 2, PO mở
rộng mục tiêu sản phẩm (SME pricing platform tuỳ biến, không chỉ automotive
MVP ban đầu). Sprint 3 **không còn** là "Hoàn thiện MVP" như kế hoạch gốc —
nội dung đó dời xuống Sprint 4 (xem dưới). Sprint 3 mới tập trung dựng lại
phần lõi pricing engine theo ADR-009: entity taxonomy mở rộng (Anchor/
Extension/Parameter), DAG + Measure execution engine, module Price List.

**Mục tiêu**: Entity taxonomy v2 chạy được (Product/Customer là anchor cố
định, Extension/Parameter configurable), DAG engine thay thế pipeline
tuyến tính cũ, 1 Price List thật tính được `result_price` từ ≥4 nguồn dữ
liệu khác nhau (Product, Customer, 1 Extension, 1 Parameter), UI Measure
Canvas (Ant Design) dựng được Price List đó không cần code.

**Deliver** (map Epic 8 trong backlog, chi tiết task ở
`docs/sprints/sprint-3.md`):
- E8-01 (entity taxonomy v2: role Anchor/Extension/Parameter, bootstrap
  Product/Customer, composite key) [core]
- E8-02 (seed demo: `CostHistory` Extension, `MarginThreshold` Parameter,
  mở rộng Product) [pack:automotive]
- E8-03 (DAG execution engine + Calculation Binding) [core]
- E8-04 (Measure — subgraph tái dùng) [core]
- E8-05 (module Price List: schema, API, approve/snapshot) [core]
- E8-06 (Measure Canvas UI, Ant Design) [frontend]
- E8-07 (seed Price List mẫu dùng đủ 4 nguồn) [pack:automotive]

**Tiêu chí demo**: Có data thật trong Postgres cho Product, Customer,
`CostHistory` (Extension), `MarginThreshold` (Parameter); dựng 1 Price List
qua UI Measure Canvas (Ant Design, kéo-thả, không viết code/JSON) đọc đủ 4
nguồn trên → ra `result_price` + mức margin risk đúng; approve → snapshot,
sửa `CostHistory` sau không đổi giá đã approve.

**Rủi ro**: Đây là kiến trúc phức tạp hơn đáng kể so với `RuleEngine`
tuyến tính Sprint 2 (xem ADR-009 mục "Rủi ro đã biết") — có thể không gọn
trong 1 tuần như các sprint trước; theo dõi sát, báo PO sớm nếu cần kéo
dài thay vì cắt xén chất lượng kiến trúc (CLAUDE.md mục 6, không vá tạm).
Rework code Sprint 2 (`PricingStage`, `RuleEngine`, `price_list` cũ) —
chấp nhận, đã ghi nhận trong ADR-009.

---

## Sprint 4 — Hoàn thiện MVP (dời từ Sprint 3 gốc)

**Mục tiêu**: Luồng end-to-end đầy đủ cho automotive, có admin và audit —
nội dung giữ nguyên như kế hoạch Sprint 3 gốc, chỉ dời số sprint.

**Deliver** (map Epic 3 còn lại, 4, 5):
- E3-03 (margin guardrail — đã generalize 1 phần qua `MarginThreshold`
  Parameter ở Epic 8, rà lại scope còn thiếu gì cho quote workflow) [core]
- E4-01, E4-02, E4-03 (quote, workflow phê duyệt, PDF) [core]
- E5-01, E5-02, E5-03, E5-04 (admin UI rule/giá, RBAC, audit log, import UI)
  [core]

**Tiêu chí demo**: Toàn bộ luồng MVP chạy từ UI: import → chọn xe/part →
giá → tạo quote → vượt ngưỡng → Sales Manager duyệt → xuất PDF. Pricing
Admin sửa 1 rule qua UI → giá ở quote mới thay đổi đúng, không cần deploy.

**Rủi ro**: Workflow phê duyệt + PDF thường bị đánh giá thấp effort — theo
dõi sát giữa sprint, cắt bớt phạm vi PDF (style tối giản) nếu cần, không cắt
phần phê duyệt (ảnh hưởng tiêu chí "kiểm toán được").

---

## Sprint 5 — Harden & ra mắt (dời từ Sprint 4 gốc)

**Mục tiêu**: MVP đủ chất lượng thương mại tối thiểu + validate kiến trúc
bằng domain pack thứ hai.

**Deliver**:
- QA: test đầy đủ acceptance criteria còn thiếu, regression toàn bộ.
- Security: rà OWASP cơ bản (qua skill `security-review`), RBAC/audit kiểm
  tra lại.
- Hiệu năng: kiểm tra pricing engine + import với catalog kích thước thực tế
  (xem Open Questions: quy mô catalog).
- DevOps: Helm chart hoàn chỉnh, chiến lược nâng cấp/migration nhiều
  cluster, observability, backup/restore (kế hoạch + cấu hình tối thiểu).
- Tài liệu: hướng dẫn vận hành/triển khai tối thiểu cho khách mới.
- **E7-01**: dựng domain pack **tổng hợp/trung lập ngành** thứ hai (theo
  D-004 — domain pack ngành thật sẽ cấu hình sau, khi có khách hàng cụ thể;
  Sprint 4 chỉ cần chứng minh metamodel đủ generic). Nếu phải sửa lõi vượt
  một extension point hợp lý → ghi nhận vào `docs/decisions.md` là ranh
  giới sai, báo PO ngay, không chờ cuối sprint.

**Tiêu chí demo**: MVP automotive chạy ổn định qua regression suite; domain
pack tổng hợp thứ hai dựng xong luồng tối thiểu, có kết luận rõ "cấu hình
đủ" hay "phải sửa lõi — ghi nhận ở đâu".

**Rủi ro**: Domain pack thứ hai lộ ra lỗ hổng kiến trúc sát ngày ra mắt —
đây là mục đích của việc làm nó ở Sprint 4 (phát hiện sớm hơn launch thật),
không phải lý do trì hoãn MVP.

---

## Theo dõi thay đổi roadmap

Mọi thay đổi phạm vi sprint (thêm/bớt story) phải đồng thời cập nhật
`docs/backlog.md` và ghi lý do vào `docs/decisions.md` nếu ảnh hưởng tiêu
chí thành công trong `docs/vision.md`.
