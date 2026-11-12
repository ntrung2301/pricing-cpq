# ADR-006: OIDC/Keycloak và mô hình RBAC

- **Trạng thái**: Accepted
- **Ngày**: 2026-11-12
- **Người quyết**: architect

## Ngữ cảnh

S1-T6 cần cấu hình Keycloak + Spring Security cụ thể; E5-02 cần RBAC cho 3
role (Sales, Sales Manager, Pricing Admin) đúng theo từng chức năng. Theo
CLAUDE.md mục 8, hệ thống single-tenant per deployment — không cần tenant
isolation trong model quyền.

## Lựa chọn đã xét

1. **Tự viết auth (JWT tự ký, user/role trong DB app)** — khác mặc định
   CLAUDE.md (OIDC + Keycloak đã là default), không có lý do kỹ thuật nào
   đủ mạnh để đổi (Keycloak miễn phí, hỗ trợ realm/role/login UI sẵn,
   giảm việc tự xây). Loại, giữ mặc định.
2. **RBAC bằng role tĩnh trong JWT claim, enforce ở API Gateway/Ingress
   (ví dụ Kong, Istio)** — thêm 1 thành phần hạ tầng mới cho dự án ở quy mô
   hiện tại (modular monolith, chưa cần gateway riêng) — không cần ở MVP,
   lại phải enforce lại business rule phức tạp hơn role đơn giản (ví dụ
   ngưỡng phê duyệt E4-02 cần biết dữ liệu quote, gateway không biết). Loại.
3. **RBAC bằng Spring Security method security (`@PreAuthorize`) ở service
   layer, dùng role từ JWT claim do Keycloak phát, kiểm tra ở cả API
   (resource server) và lặp lại ở UI (ẩn/hiện) chỉ để UX, không phải
   security boundary thật** — tận dụng đúng stack mặc định, không thêm hạ
   tầng mới, backend luôn là nguồn sự thật. Chọn.

## Quyết định

Spring Security `oauth2ResourceServer` (JWT) xác thực token do Keycloak
phát; Keycloak realm riêng cho deployment (single-tenant, mục 8 — không có
khái niệm tenant claim). 3 realm role ánh xạ 1:1 sang authority Spring:
`ROLE_SALES`, `ROLE_SALES_MANAGER`, `ROLE_PRICING_ADMIN`. Các role này là
khái niệm **generic của core** (CLAUDE.md liệt kê "quote", và backlog
E5-02 định nghĩa đúng 3 role này cho mọi domain pack — không phải khái
niệm riêng của automotive) nên định nghĩa role, enum, `@PreAuthorize` nằm
ở `core-security`/`core-api`, không ở pack. Enforce ở **method layer**
(service, không chỉ controller) để chặn cả khi có đường gọi nội bộ khác
HTTP. React dùng claim từ JWT (qua context/hook) để ẩn/hiện UI theo role —
chỉ là UX, backend luôn validate lại độc lập (defense in depth, không tin
tưởng client). Local dev: docker-compose Keycloak, realm export JSON
import sẵn 3 role + user mẫu mỗi role, dùng cho S1-T6/S1-T8.

Audit log (E5-03) lấy principal từ `SecurityContext` (sub/claim từ JWT),
không cần bảng user riêng trong app DB cho MVP (Keycloak là nguồn sự thật
về user).

## Hệ quả

- Tác động tới: S1-T6, S1-T8, E5-02, E5-03, Epic 4 (workflow phê duyệt cần
  biết role Sales Manager).
- Rủi ro đã biết: nếu sau này cần quyền chi tiết hơn role tĩnh (ví dụ
  phê duyệt theo hạn mức cá nhân, không chỉ theo role) sẽ cần thêm bảng
  permission/policy trong `core-security` — chưa cần ở MVP, ghi nhận để
  không thiết kế role cứng không mở rộng được (dùng Spring Security
  `GrantedAuthority` interface, không hardcode 3 role trong logic nếu có
  thể tránh).
- Ảnh hưởng ranh giới core/pack: **Không** — role Sales/Sales
  Manager/Pricing Admin là vai trò chung của mọi CPQ deployment (không
  gắn automotive); domain pack không định nghĩa role riêng, không cần mở
  rộng model quyền để chạy automotive hay pack tổng hợp Sprint 4.
