# ADR-005: Cấu trúc module modular monolith

- **Trạng thái**: Accepted (revised 2026-11-12 theo PO override, xem D-013)
- **Ngày**: 2026-11-12
- **Người quyết**: architect

## Ngữ cảnh

S1-T3 (repo skeleton) cần cấu trúc module cụ thể để backend-dev dựng ngay.

**Cập nhật theo PO (D-013)**: bản ADR gốc dùng multi-module Gradle để tách
cứng `core-*` (engine) khỏi `pack-automotive`/`pack-synthetic`, cộng ArchUnit
2 lớp (module graph + quét string-literal chặn từ khóa domain trong code
lõi) làm cơ chế "bất khả xâm phạm" thực thi ranh giới core/pack. PO yêu cầu
bỏ cách này: không cần module Java riêng cho từng domain, không cần ArchUnit
quét keyword — vì với metamodel EAV (ADR-001), domain knowledge đã luôn là
**dữ liệu** (file `pack.yaml` trong thư mục config), không phải code Java,
nên không có gì để "quét keyword" hay "chặn dependency" ở tầng pack nữa.
Phần dưới đây viết lại theo hướng đó; vẫn giữ multi-module Gradle, nhưng chỉ
để tổ chức các tiểu hệ thống **bên trong engine** (không phải core-vs-pack).

## Lựa chọn đã xét

1. **Single module duy nhất (`app`), không chia gì thêm** — đơn giản nhất,
   nhưng 1 file Gradle build to dần, khó review theo tiểu hệ thống
   (metamodel/pricing/quote lẫn vào nhau trong 1 source set), khó bắt lỗi 1
   tiểu hệ thống vô tình phụ thuộc ngược 1 tiểu hệ thống khác (ví dụ
   `quote` gọi thẳng internal class của `pricing` thay qua interface công
   khai). Loại — vẫn muốn modular hoá nội bộ cho dễ maintain, dù không cần
   enforce ranh giới domain nữa.
2. **Multi-module Gradle theo tiểu hệ thống engine** (`core-metamodel`,
   `core-constraint-engine`, `core-pricing`, `core-catalog`, `core-quote`,
   `core-security`, `core-api`, `app`) — giữ lợi ích tổ chức code theo
   tiểu hệ thống, review theo diff module rõ ràng, nhưng **không** còn
   module `pack-*` nào — domain (automotive, synthetic...) không phải code,
   chỉ là file `pack.yaml` nằm trong `app/src/main/resources/config/packs/`
   (ADR-001). Chọn.
3. **Maven vs Gradle** — CLAUDE.md chỉ nói "Maven/Gradle" không chốt. Giữ
   nguyên lựa chọn **Gradle** (Kotlin DSL) từ bản gốc: convention plugin
   (`buildSrc`/`build-logic`) áp dụng chung cấu hình (Java 21, Spring Boot
   plugin, checkstyle) cho nhiều module gọn hơn Maven parent/BOM; `api`/
   `implementation` configuration phân biệt rõ "expose cho module khác" hay
   "chỉ dùng nội bộ". Lý do chọn khác mặc định ngầm định vẫn đúng dù không
   còn pack module — vẫn có nhiều module `core-*`.

## Quyết định

Cấu trúc module (Gradle multi-project, root `pricing-cpq`) — **không có
module `pack-*` nào**:

- `core-metamodel` — EntityDefinition/AttributeDefinition/
  RelationshipDefinition/ConstraintDefinition (ADR-001).
- `core-constraint-engine` — constraint type registry (`compatibility`,
  `range`, `required-if`...), SPI để thêm constraint type mới (E6-02).
- `core-pricing` — RuleEngine (DSL + Groovy script, ADR-002), price
  waterfall (ADR-004), SPI pricing function (E6-01).
- `core-catalog` — import framework CSV/Excel generic (E1-04), SPI
  connector (E6-03).
- `core-quote` — quote/quote_version/quote_line, workflow phê duyệt (E4-*).
- `core-security` — RBAC/OIDC wiring dùng chung (ADR-006).
- `core-api` — REST controller, OpenAPI, tổng hợp các `core-*` module
  thành API; không chứa business logic.
- `app` — Spring Boot application module (composition root); module **duy
  nhất** phụ thuộc mọi `core-*`; chứa `PackLoaderService` và
  `src/main/resources/config/packs/<pack-id>/pack.yaml` cho từng domain
  (automotive, và domain tổng hợp Sprint 4 — chỉ là thêm 1 thư mục file,
  không phải module Gradle mới).

ArchUnit (viết ở `app` hoặc module test riêng `arch-test`, chạy trong CI
theo S1-T5) — giữ ở mức **vệ sinh module thông thường**, không còn mục
đích "chặn domain keyword":

1. Chỉ `app` được phép có dependency tới tất cả `core-*`; mỗi `core-*`
   module khác chỉ phụ thuộc `core-*` nó thật sự cần (ví dụ `core-quote`
   không cần phụ thuộc `core-catalog`) — bắt bằng Gradle module graph +
   ArchUnit `SlicesRuleDefinition` nếu cần kiểm thêm ở mức package.
2. `core-api` không chứa business logic (chỉ class `@RestController`/DTO,
   không có `@Service` xử lý logic định giá/constraint) — vệ sinh layering
   thông thường, không liên quan domain.

**Cách xác minh tính generic thay cho ArchUnit keyword-scan**: khi làm
Sprint 4 (domain tổng hợp, D-004), yêu cầu là `git diff` giữa trước/sau khi
thêm domain đó **không có file nào trong `core-*` hoặc `app` (trừ thư mục
`config/packs/`) bị sửa** — review bằng mắt (code-reviewer + PO) tại thời
điểm đó, không phải gate CI tự động chạy từ Sprint 1. Nếu buộc phải sửa →
ghi vào `docs/decisions.md`, báo PO (xem CLAUDE.md phần "Ranh giới").

## Hệ quả

- Tác động tới: S1-T3, S1-T5 (CI gate — nhẹ hơn bản gốc), toàn bộ
  backend-dev về sau, Sprint 4 (cách xác minh E7-01 đổi từ ArchUnit sang
  review diff).
- Rủi ro đã biết: không còn gate CI tự động chặn domain logic lọt vào
  `core-*`/`app` ngoài `config/packs/` — rủi ro bị đẩy sang code review
  (con người) thay vì công cụ; chấp nhận vì PO đánh giá domain luôn là data
  (EAV) nên rủi ro thực tế thấp, và review Sprint 4 vẫn là lưới chặn cuối.
- Ảnh hưởng ranh giới core/pack: Thay đổi so với bản gốc — không còn cơ chế
  build-time/test-time tự động; thay bằng (a) thiết kế data-driven (ADR-001/
  002/003/004 không đổi phần cốt lõi), (b) review diff tại Sprint 4 làm
  phép-thử cuối cho tính generic.
