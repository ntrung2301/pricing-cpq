# Vision — CPQ Pricing Platform

## Tầm nhìn

Xây một nền tảng CPQ (Configure-Price-Quote) thương mại, kiến trúc **lõi
generic + domain pack**, cho phép triển khai nhanh vào nhiều ngành B2B khác
nhau mà không phải viết lại engine. Lấy cảm hứng từ Pricefx nhưng tối giản
hơn: metamodel động, pricing deterministic/kiểm toán được, domain pack là
cấu hình chứ không phải fork code.

## Người dùng mục tiêu

- **Sales rep**: chọn/cấu hình sản phẩm theo khách, nhận giá đã tính sẵn
  theo rule, tạo quote, gửi phê duyệt khi vượt ngưỡng.
- **Sales Manager**: duyệt quote vượt ngưỡng chiết khấu/margin, xem lý do
  (price waterfall) để quyết định nhanh.
- **Pricing Admin**: quản lý bảng giá, rule chiết khấu/tier, import catalog,
  theo dõi audit log.
- (Gián tiếp) **Implementation/Solution team**: người cấu hình domain pack
  mới cho khách hàng kế tiếp — vision này đặc biệt phục vụ họ vì "pack thứ
  hai dễ làm" là tiêu chí sống còn của kiến trúc.

## Vấn đề cần giải quyết

- Báo giá B2B phức tạp (fitment theo xe, tier khách, volume break, hợp
  đồng riêng) hiện làm tay bằng Excel → chậm, sai, không audit được, không
  nhất quán giữa sales rep.
- Công cụ CPQ thương mại hiện có (Pricefx, v.v.) đắt, khó tùy biến sâu, hoặc
  phải tùy biến bằng cách sửa code lõi của vendor.
- Cần một hệ thống: (a) chạy được ngay cho automotive parts, (b) chứng minh
  được rằng thêm domain thứ hai không cần đào lại lõi.

## Tiêu chí thành công (MVP, cuối Sprint 4)

1. Luồng end-to-end chạy thật: import catalog → chọn xe + part → cấu hình →
   tính giá (đúng, deterministic, có waterfall) → tạo quote → phê duyệt theo
   ngưỡng → xuất PDF.
2. Package lõi không chứa từ khóa automotive nào (xác nhận bằng ArchUnit
   trong CI, không bằng mắt).
3. Domain pack thứ hai (đơn giản hơn automotive) dựng được ở Sprint 4 chủ
   yếu bằng cấu hình; nếu phải sửa lõi nhiều hơn một extension point hợp lý
   → ghi nhận là tín hiệu thất bại của ranh giới thiết kế, không che giấu.
4. RBAC + OIDC (Keycloak) + audit log hoạt động cho 3 role (Sales, Sales
   Manager, Pricing Admin).
5. Helm chart deploy được một cluster single-tenant từ đầu, có chiến lược
   nâng cấp nhiều cluster bằng văn bản.

## Ngoài phạm vi MVP

Tối ưu giá bằng ML, tích hợp CRM/ERP thật (chỉ interface/mock), đa tiền tệ
phức tạp, e-signature, multi-tenant runtime.
