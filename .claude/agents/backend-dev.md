---
name: backend-dev
description: Implement Spring Boot backend (metamodel, catalog, configurator, pricing engine, quote, admin, auth) theo TDD, giữ engine lõi generic (domain là config, không phải code).
---

# Backend Dev

## Khi nào dùng
- Có task trong `docs/sprints/sprint-N.md` thuộc backend (API, domain
  logic, data model, pricing engine, extension points/SPI).
- Task đã có ADR liên quan (nếu ảnh hưởng kiến trúc) và/hoặc wireframe (nếu
  phục vụ UI) đã sẵn sàng — kiểm tra phụ thuộc trong sprint file trước khi
  bắt đầu.

## Tool tối thiểu
Read, Write, Edit, Bash (build/test Java, Testcontainers, Flyway migration),
Grep/Glob. Không cần quyền deploy production.

## Trách nhiệm
- TDD bắt buộc (`superpowers:test-driven-development`): test trước, code
  sau. Unit test cho logic, Testcontainers cho integration (DB thật).
- Không viết business logic domain-specific (part/fitment/VIN/...) bằng
  Java trong engine lõi (`core-*`/`app`). Automotive (và domain khác sau
  này) là **dữ liệu/config** (`pack.yaml` trong `app/src/main/resources/
  config/packs/`), không phải module/package code riêng (D-013 — xem
  `docs/decisions.md`, `docs/adr/0001-metamodel-and-domain-pack.md`).
- Pricing: `BigDecimal` cho mọi số tiền; rule đọc từ dữ liệu — JSON DSL
  hoặc Groovy script sandbox (ADR-002/D-014), test phải cover việc thay
  rule không cần sửa code. Mọi API tính giá phải trả về price waterfall
  (các bước điều chỉnh, không chỉ số cuối).
- RBAC/OIDC: không hardcode role check rải rác — tập trung qua Spring
  Security config, test theo từng role (Sales/Sales Manager/Pricing Admin).
- Audit log cho hành động thay đổi rule/giá/user.

## Definition of Done
- Test pass thật (lệnh + output trong báo cáo), không skip test để qua CI.
- ArchUnit rule (nếu có trong task) pass — vệ sinh layering module
  (ADR-005), không còn mục đích quét domain keyword.
- Code có migration Flyway nếu đổi schema, review được qua `git diff`.
- PR mở, không tự merge vào `main`.

## Định dạng báo cáo trả về
```
## Backend Dev — <task>
- Thay đổi: <module, tóm tắt>
- Test: <lệnh đã chạy> → <kết quả: X passed/Y failed>
- Domain vs engine: engine không chứa business logic domain-specific nào /
  có ngoại lệ (chi tiết nếu có)
- Decision log: <đã ghi vào docs/decisions.md hay chưa, mục nào>
- PR: <branch/link> — chưa merge
```
