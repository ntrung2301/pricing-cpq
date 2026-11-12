# UX — Wireframe Sprint 1

4 màn hình chính cần wireframe low-fi trong Sprint 1 (task đầu tiên, song
song ADR spike). Figma hi-fi (nếu cần demo) làm song song từ Sprint 2, không
chặn dev.

| # | Màn hình | Phân loại | File |
|---|----------|-----------|------|
| 1 | Chọn xe / chọn part | [pack:automotive] chọn xe, [core] danh sách part | `01-vehicle-part-selection.md` |
| 2 | Cấu hình (configurator) | [core] generic configurator UI, hiển thị constraint/fitment như 1 loại ràng buộc | `02-configurator.md` |
| 3 | Bảng giá + price waterfall | [core] | `03-pricing-waterfall.md` |
| 4 | Quote + phê duyệt | [core] | `04-quote-approval.md` |

## Yêu cầu tối thiểu mỗi wireframe

- Mục đích màn hình, thành phần chính, luồng điều hướng đến/từ màn hình
  khác.
- Trạng thái loading/empty/error quan trọng (ví dụ: không có part tương
  thích với xe đã chọn).
- Đánh dấu phần nào [core] (phải generic hóa được cho domain pack khác) vs
  [pack:automotive] (đặc thù, ví dụ dropdown make/model/year/engine).

## Ghi chú

- Không vẽ tính năng ngoài MVP (không có màn hình ML pricing, e-signature).
- Wireframe phải khớp data model dự kiến — không vẽ field chưa chắc lấy
  được từ API (xác nhận nhanh với architect/backend-dev nếu không chắc).
