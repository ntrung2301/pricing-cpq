# Sprint 1 — Nền móng

Mục tiêu & tiêu chí demo: xem `docs/roadmap.md`. Mỗi task dưới đây độc lập
đủ để giao cho 1 subagent/worktree riêng; "Phụ thuộc" liệt kê task phải
xong trước (hoặc "không" nếu có thể chạy ngay).

Quy tắc nhận việc: agent chọn task `pending` không có phụ thuộc chưa xong,
tự nhận (ghi tên mình vào "Người phụ trách"), cập nhật trạng thái khi xong.
Dùng git worktree riêng cho task chạy song song (`superpowers:using-git-worktrees`).

---

### S1-T1 — ADR spike (7 ADR nền tảng)
- **Người phụ trách**: architect
- **Phụ thuộc**: không
- **Việc làm**: Viết nội dung 7 ADR theo danh sách `docs/adr/README.md`
  (ADR-001 → ADR-007), dùng `_template.md`. Mỗi ADR phải có Decision rõ,
  không để "TBD".
- **Done khi**: 7 file ADR có trạng thái Accepted (hoặc Proposed + lý do
  chờ PO nếu ảnh hưởng cost/lịch trình), ghi tóm tắt vào `docs/decisions.md`.

### S1-T2 — Wireframe 4 màn hình chính
- **Người phụ trách**: designer
- **Phụ thuộc**: không (nên tham khảo nhanh ADR-003 data model nếu T1 đã có
  draft, nhưng không bắt buộc chờ)
- **Việc làm**: Tạo 4 file wireframe theo `docs/ux/README.md`.
- **Done khi**: 4 file có đủ mục đích/thành phần/luồng/edge case, phân loại
  core vs pack rõ.

### S1-T3 — Repo skeleton Java (Spring Boot, modular monolith)
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S1-T1 (cần ADR-005 modular monolith structure, ADR-001
  metamodel để đặt tên module đúng)
- **Việc làm**: Tạo module Maven/Gradle theo cấu trúc ADR-005 (vd
  `core-metamodel`, `core-pricing`, `core-quote`, `pack-automotive`), cấu
  hình Spring Boot 3, Java 21, Flyway, kết nối Postgres local.
- **Done khi**: `./build` chạy pass với project rỗng nhưng có module boundary
  đúng; có 1 ArchUnit test placeholder xác nhận boundary.

### S1-T4 — Repo skeleton React (Vite + TS)
- **Người phụ trách**: frontend-dev
- **Phụ thuộc**: không
- **Việc làm**: Khởi tạo Vite + React + TypeScript + TanStack Query +
  component library đã chốt (theo ADR nếu có, mặc định tạm MUI nếu ADR
  chưa xong — ghi `NEEDS-PO-REVIEW` nếu đổi sau).
- **Done khi**: `npm run dev` chạy, `npm run test` (Vitest) pass với test
  mẫu, lint pass.

### S1-T5 — CI pipeline (GitHub Actions) + ArchUnit gate
- **Người phụ trách**: devops
- **Phụ thuộc**: S1-T3, S1-T4 (cần có gì để build)
- **Việc làm**: Workflow build+test cho cả backend (JUnit+ArchUnit+
  Testcontainers) và frontend (Vitest). ArchUnit fail = CI fail.
- **Done khi**: PR mẫu chạy CI xanh, và 1 PR cố ý vi phạm boundary (test thử)
  làm CI đỏ đúng như kỳ vọng.

### S1-T6 — Auth: Keycloak local + OIDC login cơ bản
- **Người phụ trách**: devops (hạ tầng) + backend-dev (Spring Security
  wiring)
- **Phụ thuộc**: S1-T1 (ADR-006), S1-T3
- **Việc làm**: docker-compose Keycloak, realm với 3 role (Sales, Sales
  Manager, Pricing Admin), Spring Security OIDC resource server config.
- **Done khi**: Login qua Keycloak thành công, API bảo vệ trả 401/403 đúng
  theo role.

### S1-T7 — Data model khởi điểm (entity/attribute/relationship/constraint)
- **Người phụ trách**: backend-dev
- **Phụ thuộc**: S1-T1 (ADR-001, ADR-003), S1-T3
- **Việc làm**: Flyway migration cho bảng metamodel generic (entity_def,
  attribute_def, relationship_def, constraint_def) + JPA entity tương ứng.
  Không có bảng nào tên "part"/"vehicle" ở lớp core.
- **Done khi**: Migration chạy pass trên Testcontainers Postgres, test tạo
  1 entity definition generic qua API/service.

### S1-T8 — Walking skeleton end-to-end
- **Người phụ trách**: backend-dev + frontend-dev (phối hợp)
- **Phụ thuộc**: S1-T4, S1-T6, S1-T7
- **Việc làm**: 1 API thật (`GET /api/entities` trả entity definitions từ
  S1-T7) + 1 màn hình React gọi API đó sau khi login qua Keycloak.
- **Done khi**: Chạy local, login → thấy dữ liệu thật từ Postgres trên UI,
  không mock.

---

## Tổng hợp phụ thuộc

```
S1-T1 (architect)  ──┬──> S1-T3 (backend) ──┬──> S1-T6 (devops+backend) ──┐
S1-T2 (designer)     │                       ├──> S1-T7 (backend) ────────┼──> S1-T8
                      │                       │                           │
S1-T4 (frontend) ─────┴───────────────────────┴───────────────────────────┘
S1-T3 + S1-T4 ──> S1-T5 (devops CI)
```

Task không phụ thuộc gì (bắt đầu ngay, chạy song song): S1-T1, S1-T2, S1-T4.
