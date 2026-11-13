# Decisions Log — Nhật ký quyết định (ADR ngắn)

Ghi mọi quyết định kỹ thuật không hiển nhiên ngay khi quyết (xem CLAUDE.md
mục "Agent không đứng chờ"). Định dạng ngắn — không phải ADR đầy đủ (ADR đầy
đủ cho quyết định kiến trúc lớn nằm ở `docs/adr/`). Mục có cờ
`NEEDS-PO-REVIEW` cần PO xác nhận lại; nếu PO không phản hồi trong 1 sprint,
coi như chấp nhận default.

Định dạng mỗi mục:
```
## D-NNN — <tên quyết định> (YYYY-MM-DD, <agent>) [NEEDS-PO-REVIEW nếu có]
Quyết định: <...>
Lý do: <...>
Hệ quả: <...>
```

---

## D-001 — Dựng skeleton scrum process trước khi có code sản phẩm (2026-11-12, orchestrator)
Quyết định: Tạo CLAUDE.md, 8 agent definition, docs/{vision,backlog,roadmap,
process,questions,decisions,daily-report-prompt}.md, docs/adr/, docs/ux/,
docs/sprints/sprint-1.md, docs/standup/_template.md trước khi viết bất kỳ
code sản phẩm nào.
Lý do: Yêu cầu gốc của PO — skeleton trước, code sau; đảm bảo agent có
khung làm việc nhất quán từ đầu Sprint 1.
Hệ quả: Không có code sản phẩm nào tồn tại trước Sprint 1; mọi task Sprint 1
bắt đầu từ `docs/sprints/sprint-1.md`.

## D-002 — Để trống nội dung 7 ADR, chỉ tạo danh sách + template (2026-11-12, orchestrator) [NEEDS-PO-REVIEW]
Quyết định: `docs/adr/README.md` chỉ liệt kê 7 ADR cần viết, không tự viết
nội dung quyết định (vd chọn Drools hay JsonLogic).
Lý do: Yêu cầu gốc nói rõ "chỉ tạo template và danh sách, chưa viết nội
dung ADR" — quyết định kỹ thuật sâu thuộc về architect ở Sprint 1, không
phải lúc dựng skeleton.
Hệ quả: S1-T1 (architect) phải hoàn thành 7 ADR trong 2 ngày đầu Sprint 1
trước khi các task phụ thuộc (S1-T3, S1-T6, S1-T7) có thể bắt đầu đầy đủ.

## D-003 — Domain pack thứ hai: đề xuất SaaS, chưa chốt (2026-11-12, orchestrator) [SUPERSEDED by D-004]
Quyết định: Đề xuất chọn SaaS làm domain pack thứ hai ở Sprint 4 (xem
Q-004 trong `docs/questions.md`), nhưng để PO/architect xác nhận lại đầu
Sprint 4, không chốt cứng bây giờ.
Lý do: Đề bài chỉ gợi ý "thiết bị công nghiệp hoặc SaaS", chưa chốt; chọn
sớm quá khi chưa cần có thể sai nếu có thông tin mới (vd design partner).
Hệ quả: Thay bằng D-004 sau khi PO trả lời Q-004 — không build domain
pack ngành thật ở Sprint 4.

## D-004 — Sprint 4 validate bằng domain pack tổng hợp (synthetic), không chọn ngành thật (2026-11-12, orchestrator, theo trả lời PO cho Q-004)
Quyết định: Sprint 4 (E7-01) không build SaaS hay thiết bị công nghiệp làm
domain pack thứ hai thật. Thay vào đó dựng 1 domain pack **tối giản/tổng
hợp** (vài entity/constraint/rule giả định, không gắn ngành cụ thể) chỉ để
kiểm chứng: dựng được bằng cấu hình, không sửa lõi. Domain pack ngành thật
thứ hai sẽ làm sau, khi có khách hàng thật yêu cầu cụ thể.
Lý do: PO xác nhận domain pack thật sẽ được cấu hình khi có khách hàng;
mục tiêu hiện tại là đảm bảo metamodel/engine **generic nhất có thể**, nên
phép-thử phù hợp là 1 pack tổng hợp trung lập ngành, tránh tốn công thiết
kế nghiệp vụ cho một ngành chưa có khách thật.
Hệ quả: `docs/backlog.md` Epic 7 (E7-01) và `docs/roadmap.md` Sprint 4 cập
nhật theo hướng này. Architect chịu trách nhiệm đảm bảo metamodel (ADR-001)
không ngầm giả định cấu trúc của automotive hay bất kỳ ngành cụ thể nào.

## D-005 — Catalog import: match tên cột trực tiếp, không cần UI mapping linh hoạt ở MVP (2026-11-12, orchestrator, theo trả lời PO cho Q-001)
Quyết định: E1-04 (catalog import CSV/Excel) chỉ cần đối chiếu **tên cột
trong file = tên attribute/column đã định nghĩa** trong entity definition.
Sai tên cột → báo lỗi dòng/cột cụ thể, không tự suy đoán mapping.
Lý do: PO xác nhận cách nhập là "cột trong file phải match với column
trong bảng" — không yêu cầu UI mapping kéo-thả cho MVP.
Hệ quả: Giảm phạm vi kỹ thuật của import framework ở Sprint 2 (E1-04); vẫn
giữ thiết kế generic (core không biết tên cột cụ thể của automotive, chỉ
biết "khớp tên attribute theo entity definition đang cấu hình").

## D-006 — ADR-001: Metamodel EAV có kiểu + domain pack nạp qua pack.yaml (2026-11-12, architect) [PARTIALLY SUPERSEDED by D-013: pack.yaml nay nằm trong thư mục config, không phải module Java riêng]
Quyết định: Metamodel generic gồm EntityDefinition/AttributeDefinition/
RelationshipDefinition/ConstraintDefinition; dữ liệu instance lưu EAV có
kiểu (`entity_instance` + `entity_instance_attribute_value`, giá trị theo
cột đúng type). Domain pack = file `pack.yaml` nạp qua `PackLoaderService`
(core), tag mọi dòng metamodel bằng `pack_id`+`pack_version`.
Lý do: Đảm bảo generic tuyệt đối theo D-004 (tự kiểm bằng cách tưởng tượng
pack tổng hợp Sprint 4 dùng nguyên schema, không cần sửa core); EAV có
kiểu giữ được khả năng validate/query mà JSONB tự do không có.
Hệ quả: core không có bảng "part/vehicle"; mọi domain keyword chỉ tồn tại
dưới dạng dữ liệu. Chi tiết: `docs/adr/0001-metamodel-and-domain-pack.md`.

## D-007 — ADR-002: Rule engine = JSON DSL tự viết (không Drools/CEL) (2026-11-12, architect) [PARTIALLY SUPERSEDED by D-014: thêm Groovy script như loại rule thứ hai, song song JSON DSL]
Quyết định: Dùng JSON DSL tự viết (condition vay mượn ngữ pháp JsonLogic,
không add dependency), action là tập generic hữu hạn do core định nghĩa;
RuleEngine chạy tuần tự theo (stage, priority, rule.id), không forward-
chaining.
Lý do: Drools quá nặng/khó đảm bảo thứ tự xác định cho pipeline tuần tự;
CEL là expression tự do khó dựng UI form builder cho Pricing Admin
non-engineer (E5-01); JSON DSL dễ validate, dễ audit, đúng CLAUDE.md mục 7
("rule lưu dạng dữ liệu").
Hệ quả: Core sở hữu RuleEngine + cú pháp DSL; pack chỉ cung cấp dữ liệu
rule dùng action/condition có sẵn, không thêm action type mới trong core.
Chi tiết: `docs/adr/0002-rule-constraint-engine.md`.

## D-008 — ADR-003: Catalog/fitment/price list/quote dùng lại metamodel generic, không bảng riêng theo domain (2026-11-12, architect)
Quyết định: Catalog = `entity_instance`; fitment = `constraint_instance`
loại "compatibility"; supersession/kit = `relationship_instance` loại
"supersedes"/"contains". `attribute_def.code` (bất biến) dùng làm header
cột import (khớp D-005); `attribute_def.external_code_hint` ghi chú
ACES/PIES tương lai (không map thật, theo Q-001). Price list/quote là
bảng quan hệ thường (không EAV) vì là khái niệm core chính thức.
Lý do: Tái dùng nguyên ADR-001, không thêm khái niệm core mới cho
automotive; price list/quote không cần generic hoá thêm vì CLAUDE.md đã
liệt kê là khái niệm lõi.
Hệ quả: Không bảng nào tên "part/vehicle/fitment/supersession/kit" trong
core. Chi tiết: `docs/adr/0003-catalog-fitment-pricelist-quote-data-model.md`.

## D-009 — ADR-004: Pricing deterministic qua stage pipeline cố định + snapshot rule/price list trên quote_version (2026-11-12, architect)
Quyết định: BigDecimal scale nội bộ 6, round HALF_UP về scale 2 chỉ ở bước
cuối/bước đánh dấu "rounded"; pipeline stage cố định generic (`base` →
`segment-adjustment` → `quantity-adjustment` → `discount` → `override` →
`guardrail`); mỗi `quote_version` snapshot `rule_set_version_id` +
`price_list_version_id` để replay đúng dù rule gốc đổi sau.
Lý do: Tránh rounding error tích luỹ; tránh quote cũ "trôi" kết quả khi
rule thay đổi — đáp ứng yêu cầu audit/replay của CLAUDE.md mục 7.
Hệ quả: Tên stage là generic, domain pack chỉ gắn rule vào stage có sẵn.
Chi tiết: `docs/adr/0004-deterministic-pricing-and-waterfall.md`.

## D-010 — ADR-005: Modular monolith bằng Gradle + ArchUnit 2 lớp (module graph + string-literal scan) (2026-11-12, architect) [SUPERSEDED by D-013: bỏ module pack-*, bỏ ArchUnit quét keyword]
Quyết định: Module hoá `core-metamodel, core-constraint-engine,
core-pricing, core-catalog, core-quote, core-security, core-api,
pack-automotive, pack-synthetic, app`. Chọn Gradle (Kotlin DSL) thay vì
Maven (khác mặc định ngầm định, không cần PO vì CLAUDE.md không ép cụ
thể). ArchUnit chặn: core phụ thuộc pack, domain keyword trong
class/field/string-literal của core, pack phụ thuộc pack khác, chỉ `app`
phụ thuộc mọi module.
Lý do: Gradle `api`/`implementation` + convention plugin quản lý nhiều
module gọn hơn khi số pack tăng; ArchUnit 2 lớp bắt cả vi phạm "mềm" (string
literal) mà module graph không chặn được.
Hệ quả: Đây chính là cơ chế thực thi ranh giới core/pack bằng công cụ,
không chỉ mô tả bằng văn bản. Chi tiết:
`docs/adr/0005-modular-monolith-structure.md`.

## D-011 — ADR-006: RBAC 3 role generic qua Spring Security method security + Keycloak OIDC (2026-11-12, architect)
Quyết định: Giữ mặc định OIDC/Keycloak; 3 realm role map 1:1
`ROLE_SALES/ROLE_SALES_MANAGER/ROLE_PRICING_ADMIN`; enforce ở service layer
(`@PreAuthorize`), không chỉ controller; React dùng claim chỉ để UX, backend
luôn validate lại độc lập.
Lý do: Role là khái niệm chung mọi CPQ deployment, không gắn automotive;
không cần thêm hạ tầng (API gateway) ở quy mô MVP.
Hệ quả: Role/permission model nằm ở core-security, pack không định nghĩa
role riêng. Chi tiết: `docs/adr/0006-auth-rbac.md`.

## D-012 — ADR-007: Single-tenant per cluster, chọn pack active qua config lúc deploy, nâng cấp per-customer độc lập (2026-11-12, architect)
Quyết định: 1 image chứa mọi pack đã build, chọn pack active qua
`cpq.active-packs` lúc deploy (không đổi runtime); mỗi khách 1 Helm
release + `values-<customer>.yaml` riêng pin version; nâng cấp từng khách
độc lập qua PR đổi version, không nâng đồng loạt.
Lý do: Đúng CLAUDE.md mục 8 (không multi-tenant runtime switching); tránh
rủi ro 1 version lỗi ảnh hưởng mọi khách cùng lúc.
Hệ quả: Việc chọn pack là cấu hình, core không phụ thuộc ngược vào pack cụ
thể nào. Chi tiết:
`docs/adr/0007-single-tenant-deploy-config-per-customer.md`.

## D-013 — PO override: bỏ tách module Java core/pack, bỏ ArchUnit chặn domain keyword (2026-11-12, PO)
Quyết định: Không tách `core-*`/`pack-*` thành module Gradle riêng, không
dùng ArchUnit quét từ khóa domain (`part/fitment/vehicle/...`) hay chặn
dependency core→pack như D-010 đề ra. Domain (automotive là domain đầu
tiên có thật) vẫn được build làm **dữ liệu/config thật** (entity/attribute/
relationship/constraint/rule definitions trong `pack.yaml`) để MVP chạy
end-to-end — chỉ đổi cơ chế packaging (file config trong `app`, không phải
module Java `pack-automotive`) và cơ chế xác minh tính generic (review diff
ở Sprint 4 thay ArchUnit từ Sprint 1).
Lý do (PO): Với metamodel EAV (ADR-001), domain knowledge đã luôn là dữ
liệu — không có package/class nào mang tên domain để ArchUnit quét hay
chặn, nên tách module + gate CI riêng cho việc này là phức tạp hoá không
cần thiết. Domain khác automotive (SaaS, thiết bị công nghiệp...) sẽ chỉ là
cấu hình được tạo riêng cho từng khách hàng thật khi triển khai, không phải
sản phẩm kỹ thuật của 4 sprint này.
Hệ quả: Sửa ADR-001 (pack.yaml trong thư mục config, không module), ADR-005
(bỏ module `pack-*` + ArchUnit 2 lớp, giữ module hoá nội bộ engine +
ArchUnit vệ sinh layering nhẹ), ADR-007 (wording). Sửa CLAUDE.md mục "Ranh
giới Core vs Domain Pack" → đổi tên thành "Ranh giới Core vs Domain (nguyên
tắc thiết kế, không phải module code)". Sửa `docs/sprints/sprint-1.md`
(S1-T3, S1-T5), `docs/backlog.md` (E1-01), `docs/process.md`,
`.claude/agents/{backend-dev,architect,qa-engineer,devops}.md` để bỏ nhắc
"module pack riêng"/"ArchUnit chặn keyword bắt buộc CI", thay bằng "domain =
config, xác minh bằng review diff ở Sprint 4".

## D-014 — PO override: rule engine hybrid — Groovy (code rule) + JSON DSL (visual rule builder) (2026-11-12, PO)
Quyết định: Thêm `rule_type: SCRIPT` (Groovy, chạy sandbox, có timeout) làm
loại rule thứ hai song song `rule_type: DSL` (JSON DSL, D-007) trong cùng
`RuleEngine`. JSON DSL là target output của visual rule builder (non-
engineer, E5-01); Groovy dành cho rule do dev/Pricing Admin kỹ thuật viết
trực tiếp, biểu đạt được logic phức tạp hơn DSL. Cả hai cùng sinh waterfall
step theo đúng pipeline stage cố định (ADR-004), thứ tự xác định không phụ
thuộc loại rule.
Lý do (PO): JSON DSL một mình không đủ linh hoạt cho rule nâng cao; Groovy
một mình không phù hợp cho UI kéo-thả non-engineer — cần cả hai, mỗi loại
phục vụ 1 nhóm người dùng khác nhau (Pricing Admin kỹ thuật vs non-
technical qua visual builder).
Hệ quả: Sửa ADR-002 (quyết định + hệ quả, thêm rủi ro sandbox Groovy).
Thêm yêu cầu sandbox/deterministic cho Script vào CLAUDE.md mục 7. Thêm
rủi ro mới cần qa-engineer/code-reviewer kiểm (sandbox escape, non-
determinism trong script) — chưa có task cụ thể trong sprint-1, sẽ thêm khi
viết task S2 (pricing engine) chi tiết.

## D-015 — S1-T3: repo skeleton Gradle 8 module + Java 21 qua toolchain auto-provisioning (2026-11-13, backend-dev)
Quyết định: Dựng skeleton Gradle multi-project (`pricing-cpq`) đúng 8 module
ADR-005 (`core-metamodel, core-constraint-engine, core-pricing, core-catalog,
core-quote, core-security, core-api, app`), không có module `pack-*`
(D-013). Convention plugin `cpq.java-conventions` (trong `build-logic`) áp
Java 21 toolchain + Spring Boot 3.3.5 BOM (`io.spring.dependency-management`)
cho mọi module; chỉ `app` bật `bootJar`/Flyway/Postgres driver, các
`core-*` còn lại đứng độc lập (chưa cần phụ thuộc lẫn nhau ở giai đoạn
skeleton). `org.gradle.toolchains.foojay-resolver-convention` phải dùng
version **1.0.0** (không phải 0.8.0 như gợi ý ban đầu) — bản 0.8.0 và 0.10.0
đều lỗi `NoSuchFieldError: IBM_SEMERU` khi chạy trên Gradle 9.8.2 (máy có
sẵn), do xung đột với class toolchain nội bộ của bản Gradle này; 1.0.0 chạy
được, tự tải JDK 21 qua mạng (xác nhận compile/test chạy bằng
"Java 21.0.13" trong output test, không phải JDK 17 Corretto có sẵn trên
máy) — không cần nhánh fallback JDK 17.
Lý do: Theo đúng yêu cầu CLAUDE.md/ADR-005 (Java 21) mà không cần cài JDK 21
thủ công; thử tải toolchain trước khi coi là "sandbox chặn mạng" theo chỉ
dẫn task — mạng thực tế hoạt động được.
Hệ quả: `ModuleLayeringArchTest` (ArchUnit, trong `app/src/test/java/com/
cpq/app/arch/`) viết theo TDD — xác minh bằng cách tạm cho `core-quote` phụ
thuộc `core-catalog` (thêm dependency + 1 class implement `ModuleMarker`
của catalog), chạy test thấy `coreModulesMustNotDependOnEachOther()` fail
đúng như kỳ vọng, sau đó revert lại thấy pass. `ApplicationContextLoadsIT`
(Testcontainers Postgres) xác nhận Flyway chạy `V1__init.sql` thật trên
Postgres container, không cần Postgres local. `./gradlew build` pass đầy
đủ (44 task, `BUILD SUCCESSFUL`). Không có hệ quả tới ranh giới core/
domain (không file nào liên quan domain automotive).
