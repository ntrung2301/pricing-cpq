# ADR — Danh sách cần viết trong 2 ngày đầu Sprint 1

Dùng `_template.md` cho mỗi ADR. Đây chỉ là danh sách + mục đích từng ADR —
**chưa viết nội dung quyết định** ở bước skeleton này; architect viết khi
vào Sprint 1.

| # | File | Quyết định cần chốt |
|---|------|----------------------|
| ADR-001 | `0001-metamodel-and-domain-pack.md` | Mô hình metamodel (entity/attribute/relationship/constraint-as-data) và cơ chế domain pack nạp vào lõi (config format, loading, versioning). |
| ADR-002 | `0002-rule-constraint-engine.md` | Engine rule/constraint: JSON DSL tự viết vs JsonLogic/CEL vs Drools — đánh đổi hiệu năng, khả năng audit, độ phức tạp vận hành. |
| ADR-003 | `0003-catalog-fitment-pricelist-quote-data-model.md` | Mô hình dữ liệu cho catalog, fitment (constraint instance), price list, quote + version. |
| ADR-004 | `0004-deterministic-pricing-and-waterfall.md` | Cách tính giá deterministic (BigDecimal, thứ tự áp rule) và lưu trữ price waterfall để audit/replay. |
| ADR-005 | `0005-modular-monolith-structure.md` | Cấu trúc module trong modular monolith (core modules, pack modules, boundary enforcement). |
| ADR-006 | `0006-auth-rbac.md` | OIDC/Keycloak integration, mô hình RBAC (Sales/Sales Manager/Pricing Admin), cách enforce ở API/UI. |
| ADR-007 | `0007-single-tenant-deploy-config-per-customer.md` | Mô hình deploy single-tenant per cluster, cách config-as-code theo khách (domain pack + override), Helm chart strategy. |

Mỗi ADR phải nêu rõ **hệ quả tới ranh giới core/pack** (xem CLAUDE.md) và,
nếu chọn khác tech stack mặc định, lý do rõ ràng.

Thư viện PDF (OpenPDF vs JasperReports) chốt bằng ADR riêng khi vào Sprint 3
(gần lúc cần, không cần quyết sớm ở Sprint 1) — thêm `ADR-008` khi đó.
