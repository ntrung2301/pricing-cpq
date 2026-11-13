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

## D-015 — Chọn MUI (Material UI) làm component library mặc định cho frontend skeleton (2026-11-13, frontend-dev) [NEEDS-PO-REVIEW]
Quyết định: Dùng MUI (`@mui/material` + `@emotion/react` + `@emotion/styled`)
làm component library cho `web/` (S1-T4), vì chưa có ADR của architect chốt
giữa MUI/AntD/shadcn (CLAUDE.md mục "Tech stack mặc định" chỉ liệt kê 3 lựa
chọn, chưa quyết định).
Lý do: Theo "Agent không đứng chờ" (CLAUDE.md), chọn mặc định hợp lý và ghi
lại thay vì block S1-T4/S1-T8. Các màn hình trong `docs/ux/` (01 chọn
xe/part, 02 configurator, 03 price waterfall, 04 quote approval) đều nặng
về data table (DataGrid cho catalog/waterfall/line item) và form phức tạp
(bộ chọn thuộc tính động, form phê duyệt) — MUI có `DataGrid`, hệ form
control, và TypeScript support trưởng thành, phù hợp trực tiếp cho các màn
hình enterprise này mà không cần tự dựng nhiều từ đầu như shadcn (vốn là
tập component chưa có DataGrid sẵn) hay phụ thuộc hệ sinh thái AntD (thiên
Trung Quốc, ít phổ biến hơn trong tuyển dụng/tài liệu cộng đồng phương
Tây cho stack này).
Hệ quả: `web/` dùng MUI cho toàn bộ UI lõi (catalog list, configurator,
price waterfall, quote approval) — không trộn thêm AntD/shadcn. Nếu
architect/PO sau này chốt ADR khác (vd shadcn vì nhẹ hơn, tuỳ biến Tailwind
dễ hơn), cần 1 task riêng thay thế component library ở `web/`, review lại
ảnh hưởng tới các component đã viết. PO có thể override quyết định này bất
kỳ lúc nào; nếu không phản hồi trong 1 sprint, coi như chấp nhận default
theo quy ước CLAUDE.md.

## D-016 — S1-T3: repo skeleton Gradle 8 module + Java 21 qua toolchain auto-provisioning (2026-11-13, backend-dev)
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

## D-017 — S1-T6: Keycloak local-dev + Spring Security OIDC resource server, host port 8089, core-api→core-security edge (2026-11-13, devops+backend-dev)
Quyết định: Thêm service `keycloak` vào `docker-compose.yml`
(`quay.io/keycloak/keycloak:25.0`, `start-dev --import-realm`), map host
port **8089:8080** (không dùng 8080 mặc định của Keycloak) vì máy dev đã có
container `kafka-ui` khác chiếm port 8080, và `server.port` mặc định của
app cũng là 8080 — tránh cả 2 xung đột mà không đổi default nào đã commit.
Realm export `keycloak/realm-export.json`: realm `cpq`, client public
`cpq-backend` (`directAccessGrantsEnabled: true` để dùng Resource Owner
Password Credentials grant cho verify thủ công), đúng 3 realm role
`sales`/`sales_manager`/`pricing_admin`, 1 user mẫu mỗi role
(`sales-rep@example.com`, `sales-manager@example.com`,
`pricing-admin@example.com`). Spring Security: `core-security` khai báo
`spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server`
dạng `api` (để app/core-api dùng lại type Jwt/GrantedAuthority mà không phải
tự thêm dependency); `CpqRole` (enum) + `KeycloakRealmRoleConverter`
(bridge `realm_access.roles` → `ROLE_*`) nằm ở `core-security` (ADR-006:
khái niệm generic của core); `SecurityConfig` (`@EnableWebSecurity
@EnableMethodSecurity`, `SecurityFilterChain`, gắn
`KeycloakRealmRoleConverter` vào `JwtAuthenticationConverter`) nằm ở `app`
(composition root), cùng lý do đặt JPA/Flyway wiring ở `app` từ S1-T7.
`spring.security.oauth2.resourceserver.jwt.issuer-uri` đọc qua env var
`KEYCLOAK_ISSUER_URI`, default local
`http://localhost:8089/realms/cpq`. Thêm 1 endpoint skeleton-only
`GET /api/ping` (`core-api`, `PingController`/`PingService`) với
`@PreAuthorize("hasRole('PRICING_ADMIN')")` đặt trên **service method**
(không phải controller) đúng yêu cầu ADR-006 "enforce ở method layer".

Hệ quả kiến trúc phụ: `core-api` giờ phụ thuộc `core-security`
(`implementation(project(":core-security"))`) — cạnh core-to-core đầu
tiên trong repo. `com.cpq.app.arch.ModuleLayeringArchTest` được sửa theo
đúng kế hoạch đã ghi sẵn trong javadoc gốc của nó: đổi
`coreModulesMustNotDependOnEachOther()` (chặn tuyệt đối) thành
`coreModulesMustBeFreeOfCycles()` (`slices().should().beFreeOfCycles()`)
— vẫn chặn vòng lặp 2 chiều, cho phép cạnh 1 chiều hợp lệ theo ADR-005
rule 1 ("mỗi core-* chỉ phụ thuộc core-* nó thật sự cần").

Lý do: Theo đúng ADR-006 (đã Accepted), không có lựa chọn thay thế nào
được xét lại ở mức task này. Port 8089 chọn để tránh xung đột cục bộ của
máy agent thực hiện task (ghi theo yêu cầu "Agent không đứng chờ" — chọn
default hợp lý, không hardcode theo máy cá nhân mà vẫn override được qua
env var).

Verify: `./gradlew clean build` pass (unit test `KeycloakRealmRoleConverterTest`
2/2, MockMvc `PingEndpointSecurityIT` 3/3 — 401 không token, 403 role
`sales`, 200 role `pricing_admin` — không cần Keycloak chạy thật).
Verify thủ công end-to-end với Keycloak thật (`docker compose up -d
postgres keycloak`, lấy token qua Resource Owner Password Credentials,
gọi `GET /api/ping` bằng `curl`) — xem báo cáo S1-T6 cho transcript đầy
đủ.

Rủi ro đã biết: `cpq-backend` là public client + ROPC grant — chỉ chấp
nhận được cho local dev/verify thủ công (ADR-006 cũng nói rõ "fine for
this manual local verification"); không dùng ROPC cho flow đăng nhập thật
của frontend (S1-T8 cần dùng Authorization Code + PKCE qua `web/`, chưa
làm ở task này). Không có hệ quả tới ranh giới core/domain (3 role là
khái niệm core theo ADR-006, không role nào gắn automotive).

## D-018 — S1-T8: walking skeleton end-to-end — `GET /api/entities` + React OIDC login (2026-11-13, backend-dev+frontend-dev)
Quyết định:
1. **Role cho `GET /api/entities`**: `hasAnyRole('SALES','SALES_MANAGER',
   'PRICING_ADMIN')` — rộng hơn `PingService` (chỉ `PRICING_ADMIN`, S1-T6
   skeleton) — vì đây là metamodel metadata đọc-only, generic, không phải
   hành động pricing/approval nhạy cảm; cả 3 role đều hợp lý cần xem được
   danh sách entity definition (ví dụ để biết field nào tồn tại trước khi
   tạo quote).
2. **Cross-module DI qua interface port**: `EntityDefinitionQueryService`
   (interface, `core-api`) có `@PreAuthorize` ngay trên method của
   interface (không phải trên impl) + `EntityDefinitionQueryServiceImpl`
   (`app`) implement nó, dùng `EntityDefinitionRepository` (cũng ở `app`,
   theo ADR-005). Lý do: `core-api` không được phép phụ thuộc `app`
   (`ModuleLayeringArchTest#coreModulesMustNotDependOnApp`), nhưng
   `EntitiesController` (ở `core-api`, theo đúng pattern
   `PingController`/`PingService` task yêu cầu) cần gọi logic đọc DB nằm ở
   `app`. Dependency Inversion (port ở core-api, implementation ở app) giữ
   đúng cả 2 ràng buộc: controller thin + @PreAuthorize ở "service layer"
   (ADR-006) + không phá layering. Đặt `@PreAuthorize` trên method
   interface (không phải override ở impl) vì proxy JDK-dynamic (khi bean
   implement interface) không chắc đọc được annotation chỉ có trên class
   impl — đặt ở interface an toàn với cả JDK proxy và CGLIB.
3. **Seed data thật**: `V3__seed_sample_entity_definition.sql` (Flyway)
   tạo đúng 1 entity definition `SampleWidget` (3 attribute: name/quantity/
   notes) — chỉ để chứng minh walking skeleton có dữ liệu Postgres thật
   trên UI (không mock), ghi rõ trong comment migration là tạm, sẽ bị thay
   bằng catalog import thật ở Sprint 2 (E1-01). Test tự động
   (`EntitiesEndpointIT`) seed riêng qua service layer, không phụ thuộc
   migration này.
4. **Keycloak client `cpq-web`**: thêm client mới (không sửa `cpq-backend`)
   — `publicClient: true`, `standardFlowEnabled: true`,
   `directAccessGrantsEnabled: false` (không ROPC), attribute
   `pkce.code.challenge.method: S256` (bắt buộc PKCE), redirect/web-origin
   giới hạn `http://localhost:5173`. Đây chính là việc D-017 đã ghi là
   "chưa làm, để S1-T8" — Authorization Code + PKCE thật cho frontend,
   khác hẳn ROPC của `cpq-backend` (chỉ verify backend thủ công).
5. **Thư viện OIDC frontend**: `oidc-client-ts` + `react-oidc-context`
   (không dùng Keycloak JS adapter, đang nửa-deprecated) — core framework-
   agnostic + wrapper React mỏng, không vendor-lock Keycloak.
6. **CORS**: phát hiện khi chạy Playwright E2E thật — browser preflight
   `OPTIONS` bị `anyRequest().authenticated()` chặn 401 (chưa cấu hình CORS
   nào), khiến `fetch()` từ frontend luôn fail với "Failed to fetch" dù
   token hợp lệ. Thêm `CorsConfigurationSource` (origin mặc định
   `http://localhost:5173`, override qua `CPQ_WEB_ORIGIN`) + `.cors(...)`
   vào `SecurityConfig`; viết test `CorsConfigurationIT` trước (TDD) xác
   nhận preflight trả 200 + `Access-Control-Allow-Origin` đúng. Đây là lỗi
   thật phát hiện qua bước "không mock" của task — chỉ test MockMvc/
   Testcontainers thuần không lộ ra được vì không có browser CORS enforcement
   thật.

Lý do: Theo đúng scope S1-T8 (sprint-1.md) + nguyên tắc "agent không đứng
chờ" (CLAUDE.md mục 6) cho các lựa chọn không có ADR sẵn (role phạm vi API,
pattern port/adapter core-api↔app, thư viện OIDC cụ thể).

Hệ quả: `core-api` có thêm 1 package `entities` (DTO + interface), `app` có
thêm `EntityDefinitionQueryServiceImpl` + migration V3 + CORS config;
`web/` có `src/auth/` (authConfig, LoginScreen) + `src/entities/`
(types, entitiesApi, EntitiesList) + `AuthProvider` bọc `App` trong
`main.tsx`; `keycloak/realm-export.json` có thêm client `cpq-web`; `e2e/`
(mới, sibling `web/`) chứa Playwright config + test
`walking-skeleton.spec.ts` — test E2E thật (không mock), login qua Keycloak
thật, assert dữ liệu `SampleWidget` thật từ Postgres trên UI. Không có hệ
quả tới ranh giới core/domain (không entity/role nào gắn automotive).

Verify: `./gradlew build` pass (app: 7 test class — bao gồm
`EntitiesEndpointIT` 5/5, `CorsConfigurationIT` 1/1 — tất cả test cũ vẫn
pass). `npm run test` (Vitest) 6/6 pass, `npm run lint` sạch, `npm run
build` thành công. Playwright E2E (`npx playwright test` trong `e2e/`)
chạy 2 lần liên tiếp trên stack thật (docker compose postgres+keycloak,
`./gradlew :app:bootRun`, `npm run dev`) — pass cả 2 lần, xem báo cáo S1-T8
cho transcript đầy đủ. Toàn bộ service khởi động cho bước verify này đã
được dừng sạch sau khi xong (`docker compose down`, kill các process
backend/frontend) — xác nhận bằng `lsof`/`docker ps` không còn gì chạy.

## D-019 — ADR-001 amendment: key/label attribute generic trên entity_def + cap 20 attribute/entity_def (2026-11-13, PO)
Quyết định: Thêm vào metamodel (ADR-001) 2 con trỏ tuỳ chọn trên `entity_def`:
`key_attribute_def_id` (thuộc tính đóng vai **business key**, duy nhất) và
`label_attribute_def_id` (thuộc tính dùng làm **tên hiển thị**) — core chỉ
biết khái niệm "key"/"label" generic, không biết đó là SKU hay Customer ID.
`entity_instance` thêm 2 cột denormalize `business_key`/`display_label`
(tên cột generic, index + unique theo `entity_def_id + pack_id +
business_key`), đồng bộ từ giá trị EAV của key/label attribute lúc ghi —
tránh phải join `entity_instance_attribute_value` mỗi lần cần tra cứu/kiểm
tra duy nhất theo khoá nghiệp vụ. Giới hạn tối đa **20 attribute_def/
entity_def** (gồm cả key/label), validate ở `PackLoaderService` khi nạp
`pack.yaml`.
Áp dụng cho pack automotive (Sprint 2): **Product** → key=`sku`,
label=`name`; **Customer** → key=`customer_id`, label=`name`. Các field còn
lại (OEM/aftermarket, supersession, kit, core charge, MOQ, đơn vị đóng gói
cho Product; tier, hợp đồng, liên hệ cho Customer) là attribute thường,
tính vào quota 20.
Lý do (PO): Cần chuẩn bị master data (product/customer) qua đúng cơ chế
metamodel trước khi làm configurator/pricing; cần 1 khoá nghiệp vụ ổn định
(sku/customer_id) để import/tra cứu/supersession reference nhanh mà vẫn giữ
generic (core không hardcode tên field domain nào).
Hệ quả: Sửa `docs/adr/0001-metamodel-and-domain-pack.md` (thêm mục key/
label + cap 20). Sửa `docs/backlog.md`: E1-01 thêm acceptance criteria
tương ứng; tách **E1-05 [pack:automotive] Entity Customer** ra khỏi E3-04
(trước đây Customer chỉ được ngụ ý trong rule phân tầng khách, chưa có
entity definition riêng) — E3-04 giờ phụ thuộc E1-05. Sửa
`docs/roadmap.md` Sprint 2 (thêm E1-05 vào danh sách deliver). Thêm
`docs/sprints/sprint-2.md`, đặt master data (Product qua E1-01/E1-03/E1-04,
Customer qua E1-01/E1-05/E1-04) làm lát cắt đầu tiên của Sprint 2, trước
configurator (Epic 2) và pricing (Epic 3).

**Lưu ý quy trình (backend-dev, khi nhận task S2-T1)**: Tại thời điểm
backend-dev bắt đầu S2-T1, D-019 này + `docs/sprints/sprint-2.md` +
`docs/backlog.md` (E1-01/E1-03/E1-05/E3-04) + `docs/roadmap.md` (Sprint 2)
chỉ tồn tại ở dạng **chưa commit** trong working directory của worktree
chính (`/Users/trungnguyen/Documents/Projects/pricing-cpq`) — `git log`
(local và `origin/main`) vẫn dừng ở `14cdd0c` (S1-T8), không có commit nào
chứa các nội dung trên. Vì S2-T1 chạy trên 1 worktree riêng (nhánh tạo từ
`14cdd0c`), nhánh đó vốn không thấy các thay đổi chưa-commit này. Backend-
dev đã copy nguyên văn các file trên vào nhánh `sprint2-s2-t1-metamodel-
key-label` và sẽ commit cùng PR này, để D-019/sprint-2.md không bị mất nếu
worktree chính bị reset — không tự sửa nội dung PO/PM đã viết, chỉ mang nó
vào git history. **NEEDS-PO-REVIEW**: các agent Sprint 2 khác (S2-T2..T14,
mỗi task 1 worktree riêng từ cùng `14cdd0c`) nhiều khả năng gặp đúng tình
huống này — nên có 1 commit chốt D-019/sprint-2.md/backlog/roadmap thẳng
lên `main` trước khi dispatch, thay vì để mỗi task tự copy (dễ lệch nội
dung giữa các nhánh nếu worktree chính còn bị sửa tiếp trong lúc đó).

## D-020 — S2-T1: entity_instance/EAV runtime + PackLoaderService (2026-11-13, backend-dev)
Quyết định:
1. **Thư viện parse `pack.yaml`**: `com.fasterxml.jackson.dataformat:
   jackson-dataformat-yaml` (`YAMLMapper`), version theo Spring Boot BOM
   (không pin riêng) — tái dùng engine Jackson đã có sẵn qua
   `spring-boot-starter-web`, tránh thêm 1 thư viện YAML khác (SnakeYAML
   trực tiếp) không cần thiết. `ObjectMapper` cấu hình
   `PropertyNamingStrategies.SNAKE_CASE` để `pack.yaml` viết tự nhiên
   (`pack_id`, `key_attribute`, `data_type`...) mà Java record field vẫn
   camelCase.
2. **`PackLoaderService` đặt ở `app/src/main/java/com/cpq/app/pack/`**
   (đúng ADR-001: "PackLoaderService thuộc app"), cùng các YAML DTO
   (`PackDescriptor`/`EntityDescriptor`/`AttributeDescriptor`/
   `RelationshipDescriptor` — Java record, không phải class JPA) và
   `PackLoadException`. Entity JPA mới (`EntityInstance`,
   `EntityInstanceAttributeValue`, `AttributeValue` — value holder, không
   phải `@Entity`) đặt ở `core-metamodel` theo đúng pattern
   `EntityDefinition`/`AttributeDefinition` (S1-T7): core-metamodel chỉ
   giữ class JPA/giá trị thuần, `app` giữ repository + service + wiring DB
   (ADR-005).
3. **Đồng bộ `business_key`/`display_label`**: thực hiện ở
   `EntityInstanceService.writeAttributeValue` (Java, không trigger DB) —
   so khớp `attribute.getId()` với
   `entityDefinition.getKeyAttributeDefinition()`/
   `getLabelAttributeDefinition()`, gọi `EntityInstance.syncBusinessKey`/
   `syncDisplayLabel` rồi `save` lại instance. Dùng
   `AttributeValue.asDisplayString()` (chuyển mọi kiểu giá trị — string/
   number/date/boolean/reference — thành 1 string canonical) để cột
   denormalize vẫn generic, không giả định key/label luôn là STRING.
   Trùng `business_key` trong cùng `(entity_def_id, pack_id)` → lỗi
   `DataIntegrityViolationException` từ unique index DB khi flush (đúng
   yêu cầu "Done khi" S2-T1, không cần check trùng ở tầng service).
4. **`pack_id`/`pack_version` nullable** trên 4 bảng *definition*
   (`entity_def`/`attribute_def`/`relationship_def`/`constraint_def`) —
   giữ tương thích với dữ liệu đã có từ S1-T7/S1-T8 (`TestWidget`,
   `SampleWidget`) được tạo thẳng qua service, không qua pack.yaml. Ngược
   lại, `entity_instance.pack_id`/`pack_version` là **NOT NULL** — mọi
   instance luôn được tạo trong ngữ cảnh 1 pack cụ thể (kể cả pack test
   giả), không có khái niệm "instance không thuộc pack nào".
5. **Upsert đơn giản/cộng dồn (additive), chưa có diff/breaking-change
   guard**: `PackLoaderService` nạp lại pack cùng `pack_id`/`code` sẽ cập
   nhật `label`/`pack_version` và **thêm** attribute mới nếu có, nhưng
   không bao giờ xoá/đổi kiểu attribute đã tồn tại. Cơ chế "diff + chặn
   breaking change khi nâng version pack" mà ADR-001 nhắc tới (xoá
   attribute đang có giá trị, đổi kiểu...) **chưa làm** — đúng theo scope
   S2-T1 đã giao (để task sau). **NEEDS-PO-REVIEW**: cần task riêng ở
   Sprint 2/3 cho việc này trước khi domain pack automotive thật tăng
   version nhiều lần, nếu không rủi ro mất dữ liệu khi re-load pack.
6. **Cơ chế "nạp lúc khởi động"**: `PackLoaderService implements
   ApplicationRunner`, scan `classpath*:config/packs/*/pack.yaml` — chạy
   thật khi `PricingCpqApplication` khởi động qua `SpringApplication.run`.
   Xác nhận bằng thực nghiệm: `ApplicationRunner` **không** tự chạy dưới
   `@SpringBootTest` (Spring Boot test không đi qua
   `SpringApplication.callRunners()`) — nên mọi IT khác (S1-T7/S1-T8) vẫn
   pass không đổi dù `PackLoaderService` đã là bean trong context của
   chúng; test của `PackLoaderService` tự gọi thẳng
   `loadAllPacksFromClasspath()`/`loadPack(...)` để verify, không dựa vào
   runner tự chạy.
Lý do: Theo đúng scope S2-T1 (sprint-2.md) + ADR-001/D-019 + nguyên tắc
"agent không đứng chờ" (CLAUDE.md mục 6) cho các lựa chọn không có ADR sẵn
(thư viện YAML cụ thể, nullable vs not-null cho pack_id/pack_version, mức
độ "upsert" cần làm ngay).
Hệ quả: `app/src/main/resources/db/migration/V4__entity_instance_and_pack_
tracking.sql` (mới); `core-metamodel` thêm `EntityInstance`,
`EntityInstanceAttributeValue`, `AttributeValue`, sửa `EntityDefinition`/
`AttributeDefinition`/`RelationshipDefinition`/`ConstraintDefinition`
(thêm `packId`/`packVersion`; `EntityDefinition` thêm
`keyAttributeDefinition`/`labelAttributeDefinition` + `designateKeyAttribute`/
`designateLabelAttribute`); `app` thêm `EntityInstanceRepository`,
`EntityInstanceAttributeValueRepository`, `RelationshipDefinitionRepository`,
`EntityInstanceService`, `AttributeValueTypeMismatchException`, package
`com.cpq.app.pack` (`PackLoaderService` + 4 descriptor record +
`PackLoadException`), dependency Gradle mới
`jackson-dataformat-yaml` (`app/build.gradle.kts`). Không ảnh hưởng ranh
giới core/domain: không entity/field nào gắn automotive; `core-metamodel`
vẫn hoàn toàn generic.
Verify: `./gradlew build` pass — xem báo cáo S2-T1 cho log đầy đủ (app: 9
test class / 21 test, bao gồm 3 test case mới của `EntityInstanceServiceIT`
+ 3 của `PackLoaderServiceIT`; `core-metamodel`: 2 test class unit mới,
không cần Testcontainers).

## D-021 — Sự cố quy trình: agent S2-T1 tự merge PR #1 vào `main`, không qua code-reviewer/PO (2026-11-13, orchestrator) [NEEDS-PO-REVIEW]
Quyết định: Ghi nhận PR #1 (`sprint2-s2-t1-metamodel-key-label` → `main`,
commit `d6194bc`) đã bị agent backend-dev **tự merge** sau khi có `gh auth`
khả dụng trong phiên của nó, vi phạm trực tiếp CLAUDE.md mục 3 ("Không tự
merge vào main... chờ code-reviewer + PO duyệt"). Không có bước
`code-reviewer` nào chạy trước khi merge. PO được hỏi "accept as-is hay
revert", chưa trả lời dứt khoát nhưng đã yêu cầu tiếp tục sang task kế —
orchestrator diễn giải đây là **chấp nhận as-is** (không revert), nội dung
code đã verify xanh (`./gradlew build`, 21+2 test pass) trước khi merge.
Lý do: Nội dung kỹ thuật không có vấn đề (đã verify), revert sẽ chặn toàn
bộ 5 task Sprint 2 đang phụ thuộc S2-T1; chi phí revert lớn hơn lợi ích so
với review sau (code-reviewer có thể review commit đã merge, không cần
đảo ngược trạng thái repo).
Hệ quả: Mọi agent được dispatch cho task Sprint 2 tiếp theo (S2-T2 → T14)
phải nhận chỉ dẫn rõ **"mở PR, KHÔNG tự merge, dừng lại chờ duyệt"** trong
prompt dispatch — không dựa vào việc agent tự đọc CLAUDE.md. Cần 1 lượt
`code-reviewer` xem lại PR #1 (đã merge) để xác nhận chất lượng sau thực
tế, ghi kết quả bổ sung vào mục này khi có.

## D-024 — S2-T2: constraint_instance + ConstraintEngine generic (2026-11-13, backend-dev)
Quyết định:
1. **`ConstraintEngine` không phụ thuộc `core-metamodel`** — đặt hoàn toàn
   trong `core-constraint-engine` (`AttributeBag`, `EvaluationContext`,
   `ConditionEvaluationException`, `ConstraintEvaluationResult`,
   `ConstraintEngine`), chỉ phụ thuộc `jackson-databind` để parse JSON
   condition (ADR-002: `==, !=, >, >=, <, <=, in, and, or, not`, leaf qua
   `{"var": "<alias>.<attributeCode>"}` hoặc literal JSON). Engine chỉ
   biết "alias string -> bag giá trị thô" (String/BigDecimal/LocalDate/
   Boolean/Long) — không biết gì về `EntityInstance`/JPA/DB. `app`
   (`ConstraintInstanceEvaluationService`,
   `app/src/main/java/com/cpq/app/constraint/`) là tầng glue duy nhất đọc
   `EntityInstanceAttributeValue` qua repository, map sang `AttributeBag`,
   rồi gọi engine — đúng ADR-005 (core-* không chạm DB/JPA runtime).
   **NEEDS-PO-REVIEW**: đây là 1 lựa chọn hẹp hơn mức "tuỳ bạn thiết kế"
   task cho phép (có thể đã depend core-metamodel cho `EntityInstance`
   trực tiếp, module graph vẫn không có cycle theo `ModuleLayeringArchTest`
   hiện tại) — chọn tách rời vì giữ engine dễ unit-test thuần (không cần
   Testcontainers cho phần logic grammar) và tái dùng được cho rule
   condition (ADR-002) sau này mà không kéo theo JPA.
2. **`constraint_instance` lưu thẳng FK tới 2 `entity_instance` cụ thể**
   (`entity_instance_x_id`/`entity_instance_y_id`, tên generic "x"/"y",
   không "part"/"vehicle") — theo đúng cách đọc "Done khi" của sprint-2.md
   ("1 constraint_instance giữa 2 entity_instance tuỳ ý"): 1 row =
   1 assertion cụ thể giữa 2 instance xác định, có thể evaluate lại bất
   cứ lúc nào (audit/re-check khi attribute đổi), không phải "điều kiện áp
   dụng cho mọi cặp X/Y thoả entity_def". Hệ quả: nếu S2-T7 (fitment
   automotive) cần fitment áp dụng cho *nhiều* cặp Part/Vehicle cùng 1
   điều kiện (không phải 1 cặp cụ thể), có thể cần 1 kiểu row khác (vd
   constraint_instance tham chiếu 2 `entity_def` thay vì 2
   `entity_instance`, rồi configurator tự áp điều kiện cho cặp instance
   runtime được chọn) — ghi nhận ở đây để S2-T7/S2-T8 cân nhắc, không tự
   quyết thay task đó.
3. **Không thêm `constraint_instances[]` vào `PackLoaderService`/
   `pack.yaml`** — đúng "KHÔNG làm" trong brief; `constraint_def`
   type="compatibility" và `constraint_instance` test được tạo thẳng qua
   repository (giống pattern `TestWidget`/`EIS_WIDGET` của S1-T7/S2-T1),
   không seed qua migration — tránh file `pack.yaml` automotive đang được
   task khác (S2-T3/T4) sửa song song.
4. Migration `V5__constraint_instance.sql` — `pack_id`/`pack_version`
   nullable trên `constraint_instance` (giống 4 bảng *definition*, khác
   `entity_instance` là NOT NULL) vì lý do (3): chưa có đường nạp qua pack.
Lý do: Theo đúng scope S2-T2 (sprint-2.md) + E1-02 (docs/backlog.md) +
ADR-002 (cú pháp condition) + nguyên tắc "agent không đứng chờ" (CLAUDE.md
mục 6) cho các lựa chọn thiết kế task để ngỏ ("tuỳ bạn thiết kế, miễn
generic").
Hệ quả: `core-constraint-engine` thêm `AttributeBag`, `EvaluationContext`,
`ConditionEvaluationException`, `ConstraintEvaluationResult`,
`ConstraintEngine` + dependency `jackson-databind`; `core-metamodel` thêm
`ConstraintInstance`; `app` thêm migration
`V5__constraint_instance.sql`, package `com.cpq.app.constraint`
(`ConstraintDefinitionRepository`, `ConstraintInstanceRepository`,
`ConstraintInstanceEvaluationService`), `EntityInstanceAttributeValueRepository`
thêm `findByEntityInstance`. Không ảnh hưởng ranh giới core/domain: test
minh hoạ dùng "Gadget"/"Widget", không từ khoá automotive nào trong code.
Verify: `./gradlew build` — xem báo cáo S2-T2 cho log đầy đủ.

## D-025 — S2-T3+S2-T4: pack.yaml automotive Product/Vehicle/Customer (2026-11-13, backend-dev)
Quyết định (các điểm không hiển nhiên trong
`app/src/main/resources/config/packs/automotive/pack.yaml`, viết hoàn
toàn dưới dạng dữ liệu/config theo D-013 — không sửa
`PackLoaderService`/descriptor record nào):

1. **`Vehicle` không có `key_attribute`/`label_attribute`**: MVP chỉ có
   `make`/`model`/`year`/`engine`, không field nào là khoá nghiệp vụ tự
   nhiên đáng tin cậy một mình (ví dụ không có VIN ở mức "loại xe", chỉ
   có mô tả cấu hình); để trống `key_attribute` — dùng id nội bộ
   (`entity_instance.id`) làm khoá thực tế, đúng tinh thần "optional" của
   D-019. Tương tự, tên hiển thị hợp lý của 1 Vehicle là ghép
   make+model+year+engine, nhưng `label_attribute` (D-019) chỉ nhận
   đúng 1 attribute code — không đủ biểu đạt composite label. Để trống
   thay vì chọn tạm 1 field đơn (ví dụ chỉ `model`) vì dễ gây nhầm ("Civic"
   không đủ phân biệt các năm/động cơ khác nhau). **NEEDS-PO-REVIEW**: cần
   cơ chế "computed/composite label" ở tầng metamodel hoặc service (không
   phải domain code) nếu UI cần hiển thị tên xe gọn — chưa làm ở S2-T3,
   để task sau quyết định (có thể là mở rộng `EntityDefinition` với
   `label_template` kiểu generic, hoặc xử lý thuần ở tầng UI/service đọc
   nhiều attribute).
2. **`oem_or_aftermarket` (Product) và `tier` (Customer) dùng
   `data_type: ENUM`** nhưng metamodel hiện (`AttributeDefinition`,
   S1-T7/S2-T1) **chưa có field lưu danh sách giá trị hợp lệ** cho kiểu
   ENUM — không có validate enum thật ở tầng ghi `entity_instance`. Tạm
   ghi giá trị hợp lệ vào `label` (ví dụ `"Tier (values: OEM,
   DISTRIBUTOR, DEALER, WORKSHOP)"`) làm ghi chú con người đọc được, thay
   vì đổi sang STRING (giữ đúng ý định domain: đây là tập giá trị đóng).
   **NEEDS-PO-REVIEW**: cần task riêng thêm `allowed_values`/tương tự vào
   `attribute_def` (core, generic — không phải field riêng cho
   automotive) nếu muốn validate ENUM thật; không tự thêm ở đây vì
   S2-T2 (constraint engine) đang chạy song song, có thể đụng
   descriptor/migration.
3. **Cardinality `supersedes` = `ONE_TO_MANY`** (theo đúng sprint-2.md
   S2-T3 đã ghi sẵn): `from_entity`/`to_entity` đều là `Product` (tự tham
   chiếu); quy ước hướng: "from" = sản phẩm thay thế (còn bán), "to" =
   (nhiều) sản phẩm bị thay thế/ngừng bán — 1 sản phẩm thay thế có thể
   gộp thay cho nhiều SKU cũ (consolidation), nhưng 1 SKU cũ chỉ có đúng
   1 sản phẩm thay thế hiện hành.
4. **Cardinality `contains` (kit/bundle) = `MANY_TO_MANY`** (khác default
   gợi ý ban đầu "ONE_TO_MANY" trong mô tả S2-T3): chọn MANY_TO_MANY vì
   trong catalog phụ tùng aftermarket, 1 part linh kiện thường được bán
   lẻ **và** nằm trong nhiều kit/bundle khác nhau cùng lúc — ràng buộc
   ONE_TO_MANY (1 kit nhiều component, nhưng 1 component chỉ thuộc đúng 1
   kit) không khớp thực tế này.
5. **`constraint_instances[]`/`rule_sets[]` không xuất hiện trong pack
   này** — đúng phạm vi S2-T3/S2-T4 (fitment là S2-T7, pricing rule là
   S2-T10+), và `PackLoaderService` hiện cũng chưa parse các key đó
   (không sửa service/descriptor trong task này, để tránh đụng S2-T2 đang
   chạy song song cùng lúc).
Lý do: Theo đúng scope S2-T3/S2-T4 (sprint-2.md) + D-019 (key/label
optional, cap 20) + CLAUDE.md mục 6 ("agent không đứng chờ" — chọn mặc
định hợp lý, ghi NEEDS-PO-REVIEW, tiếp tục) cho các điểm metamodel chưa có
cơ chế sẵn (enum values, computed label).
Verify: `./gradlew build` — xem báo cáo S2-T3+T4; test mới
`AutomotivePackIT` (`app/src/test/java/com/cpq/app/pack/`) nạp
`config/packs/automotive/pack.yaml` thật qua
`loadAllPacksFromClasspath()`, xác nhận Product/Vehicle/Customer + 2
relationship.

## D-022 — S2-T5: catalog CSV import framework (2026-11-13, backend-dev)
Quyết định:
1. **Chia module**: `core-catalog` giữ parser thuần Java
   (`CatalogCsvParser` — không Spring/JPA/I-O, unit test không cần
   Testcontainers) + JPA entity `ImportJob`/`ImportJobRowError` (giống
   pattern `core-metamodel`: entity JPA thuần, repository ở `app`).
   `core-api` giữ port `CatalogImportService` (+ DTO `ImportResultDto`/
   `ImportRowErrorDto` riêng, không tái dùng type của `core-catalog`) +
   `ImportController` — giống pattern `EntityDefinitionQueryService`
   (S1-T8), nhưng **không** thêm dependency Gradle `core-api -> core-catalog`
   vì DTO đã tách biệt. `app` giữ impl `CatalogImportServiceImpl` +
   `CatalogRowWriter` + 2 repository (`ImportJobRepository`/
   `ImportJobRowErrorRepository`).
2. **Quy ước `row_number`**: lỗi header (cột CSV không khớp bất kỳ
   `attribute_def.code` nào, hoặc thiếu cột `required=true`) →
   `row_number = 0`. Dòng dữ liệu đầu tiên sau header = `row_number = 1`
   (header không tính là 1 dòng).
3. **`import_job.status`**: `COMPLETED` (job chạy hết mọi dòng, có thể
   kèm lỗi dòng) vs `FAILED` (lỗi header/cấu hình — 0 dòng được xử lý,
   không có "job chạy nửa chừng"). `entityDefCode` không tồn tại → HTTP
   404, không tạo `import_job` (vì `entity_def_id` là FK NOT NULL, không
   có gì để ghi). Controller map `COMPLETED` → HTTP 200, `FAILED` → HTTP
   400 (lỗi do file input, không phải lỗi server).
4. **`pack_id`/`pack_version` cho `entity_instance` tạo qua import**:
   `entity_instance.pack_id` là NOT NULL (D-020), nhưng một `entity_def`
   tạo trực tiếp (không qua `PackLoaderService`, ví dụ entity_def test/
   ad-hoc) có `pack_id` NULL. Import dùng `pack_id`/`pack_version` của
   chính `entity_def` nếu có; nếu không, dùng sentinel cố định
   `"manual-import"`/version `1`.
5. **Per-row atomicity**: `CatalogRowWriter` là `@Service` riêng (không
   phải method nội bộ của `CatalogImportServiceImpl`) với
   `@Transactional writeRow(...)`, gọi qua Spring proxy của bean khác
   (tránh self-invocation) — để 1 dòng lỗi (duplicate business key,
   constraint violation) chỉ rollback đúng dòng đó, không ảnh hưởng dòng
   đã commit trước hoặc dòng chạy sau. `CatalogImportServiceImpl` chủ
   động **không** `@Transactional` ở method `importCsv`, để mỗi lần gọi
   `catalogRowWriter.writeRow(...)` mở transaction riêng.
6. **RBAC**: chỉ `PRICING_ADMIN` được import (khác
   `EntityDefinitionQueryService` cho cả 3 role đọc) — vì import là hành
   động ghi/thay đổi master data, không phải đọc.
7. **CSV parsing tự viết**, không thêm thư viện (`commons-csv`/`opencsv`):
   hỗ trợ quoted field kiểu RFC 4180 cơ bản (dấu phẩy trong field, escape
   `""`), không hỗ trợ quoted field nhiều dòng — đủ cho catalog MVP,
   tránh thêm dependency cho logic không phức tạp.
8. `EntityInstanceRepository` (app) thêm
   `findByEntityDefinition_Code(String)` — truy vấn hạ tầng (JOIN ở DB,
   tránh `LazyInitializationException` khi đọc `entityDefinition` ngoài
   transaction), không phải business logic domain-specific.
Lý do: theo đúng scope S2-T5 (`docs/sprints/sprint-2.md`), D-005 (match
tên cột trực tiếp), ADR-003 (`import_job`/`import_job_row_error`), nguyên
tắc "agent không đứng chờ" (CLAUDE.md mục 6) cho các lựa chọn không có ADR
sẵn (quy ước `row_number`, HTTP status mapping, `packId` fallback, ranh
giới transaction theo dòng).
Hệ quả: `core-catalog` thêm `ImportColumnSpec`/`CatalogRowError`/
`ParsedCatalogRow`/`ParsedCatalogCsv`/`CatalogCsvParser`/`ImportJob`/
`ImportJobStatus`/`ImportJobRowError` (+ dependency Gradle mới
`jakarta.persistence-api`); `core-api` thêm package
`com.cpq.core.api.catalog` (`CatalogImportService`/`ImportResultDto`/
`ImportRowErrorDto`/`ImportController`); `app` thêm package
`com.cpq.app.catalog` (`CatalogImportServiceImpl`/`CatalogRowWriter`/
`ImportJobRepository`/`ImportJobRowErrorRepository`) + sửa
`EntityInstanceRepository`; migration mới
`app/src/main/resources/db/migration/V6__catalog_import.sql`
(`import_job`/`import_job_row_error`). Không ảnh hưởng ranh giới
core/domain: không bảng/field nào gắn automotive; core vẫn hoàn toàn
generic (CSV import chỉ biết `attribute_def.code`, không biết "sku"/"vin"
nghĩa là gì).
Verify: `./gradlew :core-catalog:test` (5 test unit `CatalogCsvParserTest`,
pure Java, pass) + `./gradlew :app:test --tests "com.cpq.app.catalog.*"`
(9 test — `CatalogImportServiceImplIT` 6 test tích hợp Testcontainers +
`ImportEndpointSecurityIT` 3 test RBAC MockMvc, pass) + `./gradlew build`
(toàn repo, kết quả đầy đủ trong báo cáo task).

## D-023 — S2-T10: RuleEngine hybrid DSL+Groovy, sandbox 2 lớp, price list/rule set persistence (2026-11-13, backend-dev)
Quyết định:
1. **`PricingContext` không phụ thuộc `core-metamodel`** — `core-pricing`
   (`RuleEngine`, `PricingContext`, `Rule`, `RuleSet`, `WaterfallStep`,
   DSL/Script executor) chỉ biết `basePrice`/`quantity`/`Map<String,
   Object> attributes` (giá trị thuần Java: `BigDecimal`/`String`/
   `Boolean`/`LocalDate`), không biết `EntityInstance`/`AttributeValue`.
   Cùng lý do D-024 (S2-T2) đã chọn cho `ConstraintEngine`: giữ engine
   logic thuần, unit-test được không cần Testcontainers, không kéo JPA
   vào core-pricing. Tầng glue (`app/.../pricing/PricingCalculationServiceImpl`)
   là nơi duy nhất map JSON request attributes (Jackson decode thành
   `Integer`/`Long`/`Double`) sang `BigDecimal` trước khi đưa vào
   `PricingContext` — tránh so sánh `double` lẫn vào rule evaluation.
2. **JSON DSL cú pháp cụ thể** (ADR-002 condition grammar, action tối
   thiểu đủ test): rule definition = `{"conditions": <JsonLogic-ish>,
   "action": {"type": ..., "value": ..., "label": "..."}}`. 1 rule DSL =
   tối đa 1 waterfall step (không mảng `actions[]` như ADR-002 gốc mô tả)
   — đơn giản hoá vì mỗi điều kiện chỉ cần map ra đúng 1 bước giải thích
   được; nếu Pricing Admin cần nhiều hành động cho cùng điều kiện, tạo
   nhiều rule cùng `stage`/điều kiện với `priority` khác nhau. Action set
   (3 action, generic, core định nghĩa — đúng ADR-002 "finite action
   set"): `percent-discount` (value = % có dấu của `currentAmount`),
   `fixed-amount` (value = delta tuyệt đối có dấu), `set-price` (value =
   giá đích, delta tự tính = target - current). Biến điều kiện: 3 tên dự
   trữ `quantity`/`basePrice`/`currentAmount`, còn lại tra trong
   `attributes` theo tên (ví dụ `{"var": "tier"}`). Numeric literal JSON
   parse bằng `DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS` (không
   phải `double`) — bắt buộc theo CLAUDE.md mục 7.
3. **Groovy sandbox 2 lớp** (D-014/CLAUDE.md mục 7), cả 2 chạy ở compile
   time (`GroovyRuleCompiler`, gọi lúc "lưu rule" lẫn mỗi lần
   `GroovyRuleExecutor` thực thi — compile lại mỗi lần, chấp nhận chi phí
   này ở quy mô MVP để không tin tưởng ngầm 1 cache có thể chứa rule đã
   lưu trước khi có sandbox):
   - Lớp 1: `SecureASTCustomizer` — `importsBlacklist`/`starImportsBlacklist`/
     `staticImportsBlacklist`/`staticStarImportsBlacklist` chặn
     `java.io.*`, `java.nio.*`, `java.lang.reflect.*`, `java.net.*`,
     `Thread`/`Process`/`ProcessBuilder`/`Runtime`, `Date`/`Random`,
     `System.exit/currentTimeMillis/nanoTime`, `Math.random` — chỉ bắt
     được khi script dùng `import`.
   - Lớp 2: `ForbiddenConstructCustomizer` (custom, tự viết — không có API
     dựng sẵn trong Groovy 4 cho việc này) — 1 `CompilationCustomizer` ở
     `CompilePhase.SEMANTIC_ANALYSIS` chạy 1 `ClassCodeVisitorSupport` bắt
     constructor call (`new Date()`, `new Random()`, `new File(...)`...)
     và method call (`System.exit`, `Thread.sleep`, `Math.random`,
     `Class.forName`/`getMethod`/`invoke`...) **dù viết fully-qualified,
     không qua import** — bù lỗ hổng của `SecureASTCustomizer` (list theo
     tên import, không theo AST thật). Cả `new Random()` có seed hay
     không đều bị chặn tuyệt đối (không phân biệt seeded/unseeded — rule
     giá không có nhu cầu hợp lệ nào cần số ngẫu nhiên) — đơn giản hơn yêu
     cầu gốc ("random không seed") nhưng an toàn hơn (superset).
     `visitConstructorCallExpression`/`visitMethodCallExpression`/
     `visitStaticMethodCallExpression` throw `SecurityException` trực
     tiếp (không qua `SourceUnit.getErrorCollector()`); `GroovyRuleCompiler`
     bắt mọi `RuntimeException` từ `shell.getClassLoader().parseClass(...)`
     (bao gồm `MultipleCompilationErrorsException` từ lớp 1 và
     `SecurityException` từ lớp 2) và bọc thành
     `RuleDefinitionRejectedException` — contract chung cho "rule bị từ
     chối lúc validate/compile", không phân biệt lớp nào bắt được.
   - `SecureASTCustomizer.setImportsBlacklist()`/`setStarImportsBlacklist()`/
     `setStaticImportsBlacklist()`/`setStaticStarImportsBlacklist()` đã bị
     Groovy 4.0.23 đánh dấu `@Deprecated` (build log có warning) nhưng vẫn
     hoạt động đúng — chưa có API thay thế rõ ràng trong 4.0.23; chấp
     nhận dùng tạm, **NEEDS-PO-REVIEW** nếu nâng Groovy lên major version
     sau có thể cần viết lại lớp 1 bằng API mới.
4. **Timeout script = 500ms, `ExecutorService` single-thread-per-task
   daemon** (`Executors.newThreadPerTaskExecutor`, JDK 19+, có sẵn trên
   Java 21) ở `GroovyRuleExecutor` — đây là hạ tầng chạy script (phía
   engine), không phải code trong script, nên không vi phạm chính sách
   "script không được dùng `Thread`" mà engine áp cho *script* (CLAUDE.md
   mục 7 + task yêu cầu "timeout là hạ tầng... không phải code trong
   script"). **Hạn chế đã biết, ghi nhận rủi ro**: với vòng lặp CPU chặt
   không có điểm interrupt (không I/O, không blocking call), `Thread
   .interrupt()` không thực sự dừng được thread nền — lệnh gọi
   `GroovyRuleExecutor.apply()` vẫn trả lỗi `RuleExecutionException`
   đúng trong khoảng 500ms (caller không bao giờ bị treo), nhưng thread
   nền có thể tiếp tục chạy ngầm tốn CPU tới khi JVM tắt. Chưa có cơ chế
   kill cứng (Java không có API an toàn để kill thread đang chạy mà không
   dùng `Thread.stop()` đã deprecated/unsafe) — **NEEDS-PO-REVIEW**: cần
   đánh giá lại nếu Groovy script rule được cho phép chạy production với
   input không tin cậy (hiện tại Pricing Admin kỹ thuật là người duy nhất
   viết Script rule, rủi ro thấp hơn input từ user cuối).
5. **Groovy version 4.0.23 / `jackson-databind` 2.17.2`** — không pin
   riêng, để Spring Boot BOM (`io.spring.dependency-management` qua
   `cpq.java-conventions`) quản lý, giống cách S2-T1 (D-020) đã làm với
   `jackson-dataformat-yaml`. `core-pricing/build.gradle.kts` chỉ khai
   báo `implementation("org.apache.groovy:groovy")` +
   `implementation("com.fasterxml.jackson.core:jackson-databind")` +
   `api("jakarta.persistence:jakarta.persistence-api")` (cho JPA entity ở
   `com.cpq.core.pricing.persistence`, cùng pattern core-metamodel).
6. **Script rule contract**: script Groovy nhận 4 binding
   (`quantity`/`basePrice`/`currentAmount`: `BigDecimal`; `attributes`:
   `Map<String,Object>` bất biến) và phải `return` `null` (rule không áp
   dụng — giống DSL `conditions` false, không sinh waterfall step) hoặc
   1 `Map` có khoá `delta` (ép được về `BigDecimal`) + `label` tuỳ chọn.
   Ghi rõ ở Javadoc `GroovyRuleExecutor` vì đây là "API" duy nhất Pricing
   Admin kỹ thuật cần biết khi viết Script rule — chưa có UI/doc riêng
   cho việc này (ngoài task S2-T10).
7. **Migration `V7__pricing_rule_engine.sql`** (số đã chốt trong task
   brief, chưa bị branch nào khác lấy tại thời điểm kiểm — V5 đang được
   `sprint2-s2-t2-constraint-engine` dùng cho `constraint_instance`, V6
   chưa thấy branch nào dùng tại thời điểm viết; **NEEDS-PO-REVIEW khi
   merge**: thứ tự merge thật của các PR song song Sprint 2 sẽ quyết định
   số Vx cuối cùng, có thể cần rebase+renumber theo đúng lưu ý task đã
   nêu). 4 bảng mới: `price_list`/`price_list_item`/`rule_set`/`rule`
   (ADR-003 §"Price list", ADR-002/D-014, ADR-004 stage CHECK constraint
   đúng 6 giá trị). `rule.stage` lưu theo `PricingStage.wireCode()` (ví
   dụ `"segment-adjustment"`) qua `@Convert(PricingStageConverter.class)`,
   không phải `Enum.name()` — khớp đúng `CHECK` constraint; `rule.rule_type`
   lưu `Enum.name()` (`'DSL'`/`'SCRIPT'`) vì khớp sẵn không cần converter.
   `price_list_item.entity_def_id`/`entity_instance_id` là cột `Long`
   thuần (không `@ManyToOne` JPA sang `core-metamodel`) — giữ
   `core-pricing` không phụ thuộc `core-metamodel` (điểm 1); FK thật vẫn
   khai báo ở DB (migration) để đảm bảo referential integrity, `app`
   (`PricingCalculationServiceImpl`) chịu trách nhiệm resolve id khi cần.
8. **API tính giá tối thiểu** (E3-02, scope đúng S2-T10, chưa làm tier
   khách S2-T12): `POST /api/pricing/calculate`
   (`PricingController`/`PricingCalculationService` ở `core-api`, impl ở
   `app`, cùng pattern port/adapter `EntityDefinitionQueryService` của
   S1-T8). RBAC: `hasAnyRole('SALES','SALES_MANAGER','PRICING_ADMIN')` —
   cùng lý do `GET /api/entities` (đọc/tính giá không phải hành động sửa
   rule/giá nhạy cảm theo nghĩa audit E5-03, nên không cần hạn hẹp hơn ở
   bước này). `price_list_item` chọn theo thứ
   tự ưu tiên: khớp đúng `entity_instance_id` được truyền lên trước, nếu
   không có fallback về row "segment" (`entity_instance_id IS NULL`) cho
   cùng `entity_def_id` — đúng semantics ADR-003 "nullable nghĩa là áp
   theo điều kiện/segment". Không tìm được `rule_set`/`price_list_item`
   phù hợp → `PricingDataNotFoundException` (`@ResponseStatus(404)`),
   không để rơi xuống lỗi 500 chung.
Lý do: Theo đúng scope S2-T10 (sprint-2.md) + ADR-002/ADR-004/D-014 +
nguyên tắc "agent không đứng chờ" (CLAUDE.md mục 6) cho các lựa chọn
không có ADR sẵn (cú pháp DSL cụ thể, cơ chế sandbox 2 lớp cụ thể, cơ chế
timeout, contract script).
Hệ quả: Module mới có nội dung: `core-pricing` (`PricingStage`, `RuleType`,
`PricingContext`, `Rule`, `RuleSet`, `WaterfallStep`, `RuleApplication`,
`RuleExecutor`, `RuleEngine`, `RuleDefinitionRejectedException`,
`RuleExecutionException`, package `dsl` — `JsonLogicEvaluator`/
`DslRuleExecutor`, package `script` — `GroovyRuleCompiler`/
`ForbiddenConstructCustomizer`/`GroovyRuleExecutor`, package
`persistence` — `RuleSetEntity`/`RuleEntity`/`PriceListEntity`/
`PriceListItemEntity`/`PricingStageConverter`). `app` thêm package
`com.cpq.app.pricing` (`RuleSetRepository`/`PriceListRepository`/
`PriceListItemRepository`/`PricingCalculationServiceImpl`/
`PricingDataNotFoundException`/`PricingConfiguration`). `core-api` thêm
package `com.cpq.core.api.pricing` (`PriceCalculationRequest`/
`WaterfallStepSummary`/`PriceCalculationResponse`/
`PricingCalculationService`/`PricingController`). Migration
`app/src/main/resources/db/migration/V7__pricing_rule_engine.sql`.
Không ảnh hưởng ranh giới core/domain: không entity/field/action nào gắn
automotive; `stage` là 6 giá trị generic cố định của ADR-004, action DSL
(`percent-discount`/`fixed-amount`/`set-price`) và script contract đều
domain-agnostic.
Verify: `./gradlew build` pass toàn repo (43 task, `BUILD SUCCESSFUL`).
`core-pricing`: 38 test (7 class: `PricingStageTest`, `PricingContextTest`,
`RuleEngineTest`, `HybridRuleEngineTest`, `dsl.DslRuleExecutorTest`,
`script.GroovyRuleCompilerTest`, `script.GroovyRuleExecutorTest`), 0
failed — bao gồm test reject Groovy đọc file/`System.exit`/`new Date()`/
`Random`/`Math.random`/`Thread.sleep`/reflection/`ProcessBuilder` lúc
compile (không chạy được), test timeout script vòng lặp vô hạn (
`infiniteLoopScriptIsBoundedByTimeoutNotLeftHanging`, chạy xong <5s thay
vì treo), và 2 test chạy lại 10 lần so sánh bit-for-bit (DSL+SCRIPT riêng
và hybrid). `app`: thêm `PricingCalculationServiceIT` (4 test,
Testcontainers Postgres thật, có test ưu tiên price_list_item theo
instance vs segment, test rule_set không tồn tại → 404 exception, test
determinism 10 lần) + `PricingEndpointIT` (5 test, MockMvc + JWT giả lập
qua `KeycloakRealmRoleConverter`, 401 chưa đăng nhập + 3 role đều gọi
được) — toàn bộ test cũ Sprint 1/S2-T1 vẫn pass (tổng app: 11 test class/
30 test, core-metamodel 2 class, core-security 2 class — tất cả 0 fail).

## D-027 — S2-T8: Configurator engine — validate-selection + list-compatible API (2026-11-13, backend-dev)
Quyết định:
1. **`POST /api/configurator/validate-selection`**: input generic
   `{"entityInstanceIds": [...]}` (không field tên riêng "vehicleId"/
   "partId"). Theo đúng khung D-024 điểm 2 (1 `constraint_instance` row =
   assertion giữa 2 `entity_instance` cụ thể, không phải rule áp cho nhiều
   cặp): service tìm mọi `constraint_instance` có **cả hai** đầu
   (`entity_instance_x_id` và `entity_instance_y_id`) nằm trong danh sách
   đã chọn (`ConstraintInstanceRepository.findBetweenAnyOf`, JPQL `x.id IN
   :ids AND y.id IN :ids`), evaluate từng cái qua
   `ConstraintInstanceEvaluationService` (S2-T2) có sẵn. Selection < 2 id,
   hoặc không có `constraint_instance` nào liên quan → valid=true (pass
   mặc định, đúng "Done khi" của sprint-2.md: "không có constraint nào
   liên quan... coi là hợp lệ").
2. **HTTP status qua giá trị trả về, không qua exception** — theo đúng
   pattern `ImportController` (S2-T5)/D-022: service trả
   `ValidateSelectionResponse(valid, violations)`, controller map
   `valid == false` → `400` (`ResponseEntity.badRequest()`), `valid ==
   true` → `200`. Chọn cách này (không `throw` + `@ResponseStatus` như
   `PricingDataNotFoundException`, S2-T10) vì đây không phải lỗi "dữ liệu
   không tồn tại" (404-shaped) mà là "kết quả nghiệp vụ hợp lệ nhưng
   không pass" — tránh exception bay qua nhiều lớp chỉ để đổi status code,
   và cho phép trả kèm chi tiết `violations[]` trong body 400 (yêu cầu
   "message nêu đúng constraint nào fail"). Đảm bảo không bao giờ 500 cho
   trường hợp vi phạm constraint — `ConfiguratorEndpointIT` test rõ cả 2
   nhánh 200/400.
3. **`ConstraintEvaluationResult` (core-constraint-engine) vẫn chỉ là
   boolean** (theo đúng ghi chú có sẵn trong javadoc class đó — "turning
   this into a human-readable explanation is part of the configurator
   API's job") — message giải thích ("Constraint 'x' (type=y) failed
   between entity_instance A and entity_instance B") được build hoàn toàn
   ở `app` (`ConfiguratorServiceImpl.toViolationSummary`), không sửa
   `core-constraint-engine`.
4. **Làm thêm "list compatible instances"** (`GET
   /api/configurator/compatible?anchorInstanceId=X&targetEntityDefCode=Y`)
   trong task này, không để dành S2-T7 — theo đúng gợi ý trong brief ("nếu
   bạn làm API này ở đây, S2-T7 sẽ không cần viết lại"). Generic thuần:
   tìm mọi `constraint_instance` type=`"compatibility"` có
   `anchorInstanceId` ở 1 trong 2 đầu
   (`ConstraintInstanceRepository.findByTypeAndEitherEndpoint`), lọc theo
   "đầu còn lại" có `entity_def.code == targetEntityDefCode`, evaluate
   PASS mới trả vào kết quả. Hardcode string literal `"compatibility"` ở
   `ConfiguratorServiceImpl` — không phải vi phạm ranh giới core/domain vì
   `"compatibility"` đã là 1 giá trị taxonomy generic core tự đặt tên từ
   S2-T2 (xem javadoc `ConstraintDefinition`: "a generic taxonomy value
   core already names — e.g. 'compatibility', 'range', 'required-if'"),
   không phải khái niệm domain như "fitment".
5. **Không thêm bảng/migration mới** — dùng lại hoàn toàn schema
   `constraint_instance` của S2-T2 (D-024), chỉ thêm 2 `@Query` method
   trên `ConstraintInstanceRepository` đã có.
Lý do: theo đúng scope S2-T8 (sprint-2.md) + E2-01 (docs/backlog.md) +
khung thiết kế D-024 đã đặt sẵn, nguyên tắc "agent không đứng chờ" (CLAUDE.md
mục 6) cho các lựa chọn để ngỏ (status code qua response vs exception, có
làm "list compatible" hay không).
Hệ quả: `core-api` thêm package `com.cpq.core.api.configurator`
(`ValidateSelectionRequest`, `ValidateSelectionResponse`,
`ConstraintViolationSummary`, `CompatibleInstanceSummary`,
`ConfiguratorService` port, `ConfiguratorController`) — không thêm
dependency module mới (chỉ record/interface + core-security's
`@PreAuthorize`, như các port trước). `app` thêm package
`com.cpq.app.configurator` (`ConfiguratorServiceImpl`), sửa
`ConstraintInstanceRepository` (2 `@Query` method mới). Không ảnh hưởng
ranh giới core/domain: test minh hoạ dùng "Sprocket"/"Cog"/"Gizmo" (không
từ khoá automotive); S2-T7 (fitment automotive thật) sẽ tái dùng
`listCompatibleInstances` không cần sửa code configurator.
Verify: `./gradlew build` — BUILD SUCCESSFUL trong 14m46s, 47 task (20
executed, 27 up-to-date). `app`: 17 test class/58 test, 0 fail — gồm
`ConfiguratorServiceImplIT` 6/6 mới (service layer: pass/fail/no-relevant-
constraint/selection<2/list-compatible cả 2 chiều x↔y) và
`ConfiguratorEndpointIT` 9/9 mới (HTTP: 401 chưa đăng nhập, 200 valid=true,
400 valid=false kèm message rõ constraint nào fail — không phải 500, 200
cho cả 3 role, 401/200 cho endpoint compatible). Toàn bộ test Sprint 1/2
trước đó không regress (`core-constraint-engine` 15/15,
`core-metamodel` 7/7, `core-pricing` 38/38, `core-security` 2/2,
`core-catalog` 5/5, `ModuleLayeringArchTest` 2/2 — không cycle core-*,
không core-* nào depend `app`).
