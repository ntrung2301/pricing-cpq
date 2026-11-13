# Roadmap — 4 Sprint (1 tuần/sprint)

Mục tiêu tổng: MVP chạy được end-to-end cho automotive — import catalog →
chọn xe/part → cấu hình → tính giá (waterfall) → tạo quote → phê duyệt →
PDF. Chi tiết task từng sprint: `docs/sprints/sprint-N.md` (sprint-1 có sẵn
trong skeleton; sprint-2/3/4 viết ở đầu sprint tương ứng, dựa trên backlog).

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

## Sprint 3 — Hoàn thiện MVP

**Mục tiêu**: Luồng end-to-end đầy đủ cho automotive, có admin và audit.

**Deliver** (map Epic 3 còn lại, 4, 5):
- E3-03 (margin guardrail) [core]
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

## Sprint 4 — Harden & ra mắt

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
