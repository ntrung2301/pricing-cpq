# CLAUDE.md — CPQ Pricing Platform

## Bối cảnh

Đang xây một **CPQ (Configure-Price-Quote) Pricing Application** thương mại,
lấy cảm hứng từ Pricefx, cho sales B2B. Kiến trúc: **lõi generic + domain
pack**. Lõi không biết gì về domain cụ thể; mọi kiến thức nghiệp vụ
(automotive, SaaS, thiết bị công nghiệp...) nằm trong domain pack dưới dạng
cấu hình/dữ liệu. Automotive là pack đầu tiên (MVP). 4 sprint, 1 tuần/sprint.

Product Owner (PO) = người dùng (Trung). PO duyệt cuối mỗi sprint và trả lời
Open Questions. Agents **không chờ PO** để tiếp tục làm — xem mục "Agent
không đứng chờ" trong `docs/process.md`.

Tài liệu & báo cáo: **tiếng Việt**. Code, tên file, tên biến, commit message,
PR title: **tiếng Anh**.

## Ranh giới Core vs Domain (nguyên tắc thiết kế, không phải module code) [D-013]

- **Không còn tách core/pack bằng module/package Java riêng, không còn
  ArchUnit chặn từ khóa domain.** Quyết định ban đầu (module `pack-*` +
  ArchUnit quét keyword) đã bị PO override ở D-013 — xem `docs/decisions.md`.
  Lý do: với metamodel EAV có kiểu (ADR-001), domain knowledge tự nhiên đã
  là **dữ liệu** (entity/attribute/relationship/constraint definitions),
  không phải code Java — nên không có package `pack.*` nào tồn tại để cấm.
- Nguyên tắc vẫn giữ (chỉ đổi cơ chế thực thi): engine lõi (metamodel,
  constraint engine, pricing/rule engine, quote, admin) phải luôn generic —
  không viết business logic Java riêng cho automotive hay bất kỳ ngành nào.
  "Fitment theo xe" vẫn chỉ là **một loại constraint cụ thể**
  (`compatibility`), không phải khái niệm lõi.
- Automotive là **domain đầu tiên có thật**, dựng làm dữ liệu/config cấu
  hình thật (entity/attribute/relationship/constraint/rule definitions,
  import mapping) để MVP chạy end-to-end và để validate engine — không phải
  module code riêng (không có `pack-automotive` trong cấu trúc Gradle).
  Domain khác (khách hàng thật sau này) cũng chỉ là thêm cấu hình, không
  thêm code.
- **Cách xác minh tính generic**: Sprint 4 phải thêm được 1 domain tổng hợp
  (synthetic, D-004) chỉ bằng dữ liệu/config, **không sửa bất kỳ file Java
  nào trong engine lõi** — xác minh bằng diff code = rỗng ngoài file
  config/seed data mới, không phải bằng ArchUnit. Nếu phải sửa code lõi →
  tín hiệu ranh giới thiết kế sai, ghi nhận vào `docs/decisions.md` và báo
  PO, không âm thầm vá.

## Quy tắc làm việc chung

1. **TDD**: viết test trước, thấy test fail, rồi mới viết code làm test pass
   (xem skill `superpowers:test-driven-development`). Không viết code sản
   phẩm mà không có test tương ứng.
2. **Verify trước khi báo xong**: chạy test/build/lint thật, đọc output,
   không suy đoán hoặc tự nhận "done" (xem skill
   `superpowers:verification-before-completion`). Báo cáo phải kèm bằng
   chứng (lệnh đã chạy + kết quả).
3. **Không tự merge vào `main`**. Mọi việc làm trên branch riêng
   (`feature/<sprint>-<task-id>-<slug>`), mở PR, chờ `code-reviewer` + PO
   (cho các quyết định lớn) duyệt. Dùng `superpowers:finishing-a-development-branch`
   khi xong.
3b. **Chạy song song bằng git worktrees** khi nhiều task độc lập cùng sprint
   (xem `superpowers:using-git-worktrees` và `docs/process.md`).
4. **Commit nhỏ, thường xuyên**, message tiếng Anh, dạng
   `<type>(<scope>): <summary>` (ví dụ `feat(pricing): add volume break rule`).
   Không gộp nhiều việc không liên quan vào 1 commit.
5. **Mọi quyết định kỹ thuật không hiển nhiên** → ghi vào `docs/decisions.md`
   (ADR ngắn) ngay khi quyết, không để cuối sprint mới ghi.
6. **Agent không đứng chờ** PO: nếu có lựa chọn hợp lý mặc định, chọn, ghi lý
   do vào `docs/decisions.md`, gắn cờ `NEEDS-PO-REVIEW`, và tiếp tục. Chỉ dừng
   hẳn khi việc tiếp theo *thực sự* không thể làm nếu thiếu câu trả lời PO —
   khi đó ghi vào `docs/questions.md` và báo trong standup.
7. **Pricing phải deterministic, kiểm toán được, test được.** Tiền luôn dùng
   `BigDecimal` (không `double`/`float`). Rule (giá, chiết khấu, ràng buộc)
   lưu dạng dữ liệu, không hard-code trong Java/TS đã compile sẵn — gồm cả
   rule JSON DSL (do visual rule builder sinh ra) và rule Groovy script
   (nhập/sửa qua UI cho rule nâng cao — xem ADR-002), cả hai đều lưu là text
   trong DB/config, không phải class Java cố định. Groovy phải chạy sandbox
   (không I/O, không reflection thoát sandbox, có timeout) và vẫn phải sinh
   ra đúng 1 danh sách waterfall step xác định — không được dùng nguồn
   non-deterministic (giờ hệ thống, random không seed) trong rule. Mọi phép
   tính giá phải tạo ra "price waterfall" giải thích được (list price → các
   bước điều chỉnh → final price), lưu lại được để audit.
8. **Single-tenant per deployment.** Không viết code tenant isolation. Cấu
   hình theo từng khách = config-as-code (domain pack + override), không phải
   multi-tenant runtime switching.

## Agents & phối hợp

Danh sách agent: `.claude/agents/{product-manager, architect, designer,
backend-dev, frontend-dev, qa-engineer, code-reviewer, devops}.md`. Mỗi agent
có file định nghĩa riêng: khi nào dùng, tool tối thiểu, trách nhiệm, Definition
of Done, định dạng báo cáo.

Quy trình phối hợp cơ bản:
- `product-manager` giữ `docs/backlog.md`, `docs/vision.md`, trả lời câu hỏi
  nghiệp vụ cấp 1 (nếu không chắc → đẩy vào `docs/questions.md` cho PO).
- `architect` viết ADR (`docs/adr/`), xác nhận/điều chỉnh tech stack, review
  thiết kế lớn trước khi backend-dev/frontend-dev code.
- `designer` làm wireframe (`docs/ux/`) trước khi frontend-dev code UI.
- `backend-dev` / `frontend-dev` implement theo task trong
  `docs/sprints/sprint-N.md`, theo TDD.
- `qa-engineer` viết/chạy test tích hợp, E2E, kiểm tra acceptance criteria.
- `code-reviewer` review mọi PR trước khi coi là "có thể merge" (merge thật
  vẫn cần PO cho các quyết định lớn ảnh hưởng kiến trúc/ranh giới core-pack).
- `devops` giữ CI, Helm chart, môi trường, observability, backup/restore.

Task luôn tham chiếu `docs/sprints/sprint-N.md` (có người phụ trách + phụ
thuộc rõ ràng) để agent/subagent có thể nhận việc độc lập.

## Tech stack mặc định (Architect xác nhận bằng ADR, không tự đổi ngoài quy trình)

Java 21 + Spring Boot 3 (modular monolith) · PostgreSQL + Flyway · Spring
Security (OIDC) + Keycloak · OpenAPI · React + TypeScript + Vite + TanStack
Query + 1 component library (MUI/AntD/shadcn) · JUnit 5 + Testcontainers,
Vitest, Playwright, ArchUnit · Docker, Kubernetes + Helm, GitHub Actions.
Rule/constraint engine và thư viện PDF: Architect so sánh và chốt bằng ADR
(xem `docs/adr/`), chưa chốt ở skeleton này.

## Tài liệu tham chiếu

`docs/vision.md` · `docs/backlog.md` · `docs/roadmap.md` · `docs/process.md`
· `docs/adr/` · `docs/ux/` · `docs/sprints/` · `docs/standup/` ·
`docs/questions.md` · `docs/decisions.md` · `docs/daily-report-prompt.md`
