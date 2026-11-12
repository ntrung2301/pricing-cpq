# ADR-005: Cấu trúc module modular monolith

- **Trạng thái**: Accepted
- **Ngày**: 2026-11-12
- **Người quyết**: architect

## Ngữ cảnh

S1-T3 (repo skeleton) cần cấu trúc module cụ thể để backend-dev dựng ngay,
và cần cơ chế chặn vi phạm ranh giới core/pack **bằng công cụ**, không chỉ
bằng review bằng mắt — CLAUDE.md yêu cầu ArchUnit test bắt buộc trong CI.

## Lựa chọn đã xét

1. **Single module, tách bằng package Java thuần, không enforce ở build
   tool** — nhanh dựng, nhưng không gì cản 1 class trong package `core.*`
   import trực tiếp class trong package `pack.*` trước khi ArchUnit chạy
   (chỉ fail ở test, không fail ở compile) — chậm phát hiện vi phạm (biết
   lúc chạy test thay vì lúc viết code). Chấp nhận 1 phần (vẫn cần, xem
   quyết định), nhưng không đủ — kết hợp thêm module thật.
2. **Multi-module Gradle, dependency giữa module khai báo qua
   `project(":core-xxx")`** — module `pack-automotive` build.gradle.kts
   không khai báo dependency tới `core-*` nào chứa domain logic khác pack
   cần, và ngược lại core module không thể khai báo dependency tới
   `pack-*` (nếu ai cố thêm, build.gradle fail review/khó qua PR vì hiện
   rõ trong diff) — enforce sớm hơn (lúc build), ArchUnit làm lớp thứ 2 bắt
   các vi phạm "mềm" hơn (reflection, string literal chứa domain keyword
   trong code core). Chọn kết hợp cả hai lớp.
3. **Maven vs Gradle** — CLAUDE.md chỉ nói "Maven/Gradle" không chốt.
   Chọn **Gradle** (Kotlin DSL) vì: convention plugin
   (`buildSrc`/`build-logic`) áp dụng chung cấu hình (Java 21, Spring Boot
   plugin, checkstyle) cho nhiều module gọn hơn Maven parent/BOM khi số
   module tăng (core nhiều module + 2+ pack); `api`/`implementation`
   configuration của Gradle phân biệt rõ "expose cho module khác" hay
   "chỉ dùng nội bộ" — hữu ích để core module không vô tình leak internal
   type ra pack. Đây là lựa chọn khác mặc định ngầm định (CLAUDE.md không
   ép Maven hay Gradle, nên không cần PO duyệt, nhưng ghi lại lý do theo
   yêu cầu "khác mặc định phải có ADR" để minh bạch).

## Quyết định

Cấu trúc module (Gradle multi-project, root `pricing-cpq`):

- `core-metamodel` — EntityDefinition/AttributeDefinition/
  RelationshipDefinition/ConstraintDefinition, PackLoaderService (ADR-001).
- `core-constraint-engine` — constraint type registry (`compatibility`,
  `range`, `required-if`...), SPI để pack thêm constraint type mới (E6-02).
- `core-pricing` — RuleEngine, JSON DSL, price waterfall (ADR-002, ADR-004),
  SPI pricing function (E6-01).
- `core-catalog` — import framework CSV/Excel generic (E1-04), SPI connector
  (E6-03).
- `core-quote` — quote/quote_version/quote_line, workflow phê duyệt (E4-*).
- `core-security` — RBAC/OIDC wiring dùng chung (ADR-006).
- `core-api` — REST controller, OpenAPI, tổng hợp các core module thành
  API; không chứa business logic.
- `pack-automotive` — `pack.yaml` + SPI implementation nếu cần (ví dụ
  pricing function tính core charge).
- `pack-synthetic` — pack tổng hợp Sprint 4 (D-004), cùng cấu trúc.
- `app` — Spring Boot application module (composition root), module **duy
  nhất** được phép dependency tới mọi module khác (`core-*` và `pack-*`).

ArchUnit rule (viết ở `core-api` hoặc module test riêng `arch-test`, chạy
trong CI theo S1-T5):

1. Không class nào trong package `com.cpq.core..` phụ thuộc (import) vào
   package `com.cpq.pack..` (`noClasses().that().resideInAPackage("com.cpq.core..").should().dependOnClassesThat().resideInAPackage("com.cpq.pack..")`).
2. Không class/field/method/string-literal-constant trong
   `com.cpq.core..` chứa từ khóa domain cấm (`part`, `fitment`, `vin`,
   `supersession`, `oem`, `aftermarket`, `vehicle`, `make`, `model`, `year`
   — so khớp không phân biệt hoa thường trên simple name và literal string)
   — custom `ArchCondition` vì ArchUnit không có rule built-in cho string
   literal, cần tự viết quét bytecode constant pool.
3. `pack-*` chỉ được phụ thuộc `core-*`, không phụ thuộc `pack-*` khác
   (automotive không phụ thuộc pack-synthetic và ngược lại).
4. Chỉ `app` được phép có dependency graph tới tất cả module còn lại; mọi
   module khác chỉ phụ thuộc module nó thật sự cần (kiểm bằng Gradle
   `dependencies` task hoặc ArchUnit `SlicesRuleDefinition`).

## Hệ quả

- Tác động tới: S1-T3, S1-T5 (CI gate), toàn bộ backend-dev về sau.
- Rủi ro đã biết: tự viết ArchCondition quét string literal tốn thời gian
  ban đầu hơn dùng rule có sẵn; chấp nhận vì đây là rule quan trọng nhất
  của dự án (CLAUDE.md gọi là "bất khả xâm phạm").
- Ảnh hưởng ranh giới core/pack: **Có, trực tiếp** — ADR này **là** cơ chế
  thực thi ranh giới core/pack bằng công cụ (build-time qua Gradle module
  graph + test-time qua ArchUnit), không chỉ mô tả ranh giới bằng văn bản.
