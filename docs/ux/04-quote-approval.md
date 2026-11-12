# Wireframe 04 — Quote + Phê duyệt

> Low-fi. Tham chiếu: `docs/backlog.md` Epic 4 (E4-01, E4-02; E4-03 export
> PDF chỉ là nút thoát luồng, KHÔNG thiết kế màn hình PDF ở đây — ngoài
> phạm vi 4 wireframe này). Toàn bộ màn hình **[core]**.

## 1. Mục đích màn hình

Một màn hình, hai góc nhìn theo role (RBAC — E5-02):

- **Sales rep**: xây/sửa quote gồm nhiều line item, xem lịch sử version,
  gửi phê duyệt khi cần.
- **Sales Manager**: xem quote đang "Pending Approval", xem lý do + price
  waterfall liên quan, duyệt hoặc từ chối kèm lý do.

## 2. Layout / thành phần chính

```
┌─────────────────────────────────────────────────────────────────────┐
│ [core] Header quote                                                   │
│  Quote #Q-2026-0042   Khách: Acme Co.   Trạng thái: [Pending Approval]│
│  Phiên bản: [ v3 ▾ ]  (đang xem v3 / 3)   Tạo bởi: ... Cập nhật: ...  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] Danh sách line item                                            │
│ ─────────────────────────────────────────────────────────────────── │
│ # │ Entity          │ SL │ Đơn giá (final) │ Thành tiền │ Hành động  │
│ 1 │ <entity A>      │ 12 │ 1.200.000 ₫ 🔗  │ 14.400.000 │ [Sửa][Xóa] │
│ 2 │ <entity B>      │ 5  │   900.000 ₫ 🔗  │  4.500.000 │ [Sửa][Xóa] │
│                                                                        │
│ 🔗 = bấm vào đơn giá → mở `03-pricing-waterfall.md` (chế độ snapshot) │
│                                            [ + Thêm line item ]       │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] Tổng kết quote                                                 │
│  Tạm tính: 18.900.000 ₫   Tổng chiết khấu: 1.700.000 ₫                │
│  Tổng cộng: 18.900.000 ₫   Margin trung bình: 12%  (ngưỡng: 15%)      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] Khối phê duyệt (hiện khi status = Pending Approval)            │
│  Lý do cần phê duyệt:                                                 │
│   • Margin trung bình (12%) dưới ngưỡng tối thiểu (15%)               │
│   • Line #2: chiết khấu 18% vượt ngưỡng cho phép (15%)                │
│  Người duyệt: Sales Manager — <tên>                                   │
│                                                                        │
│  -- Góc nhìn Sales rep (role Sales) --                                │
│   Trạng thái: "Đang chờ Sales Manager duyệt" (read-only, không sửa     │
│   được quote cho tới khi có kết quả duyệt)                            │
│                                                                        │
│  -- Góc nhìn Sales Manager (role Sales Manager) --                    │
│   Ghi chú (tuỳ chọn): [ ................................. ]          │
│   [ Duyệt (Approve) ]     [ Từ chối (Reject) ]                       │
│   (Từ chối bắt buộc nhập Lý do trước khi submit)                      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] Lịch sử phê duyệt / version (audit, read-only)                │
│  v1 → v2: sửa SL line #1 (bởi Sales rep, 10/11/2026)                  │
│  v2: Gửi phê duyệt (bởi Sales rep, 11/11/2026)                        │
│  v2: Từ chối — lý do "thiếu chứng từ hợp đồng" (Sales Manager,        │
│       11/11/2026)                                                     │
│  v2 → v3: sửa line #2, gửi lại phê duyệt                              │
└─────────────────────────────────────────────────────────────────────┘

   [ Gửi phê duyệt ]  (chỉ hiện khi Draft + vượt ngưỡng)
   [ Xuất PDF ]  (chỉ enable khi status = Approved hoặc không cần duyệt;
                  bấm vào thoát khỏi phạm vi 4 wireframe này — E4-03,
                  không thiết kế màn PDF ở đây)
```

### Chi tiết thành phần

- **Header `[core]`**: trạng thái quote (Draft / Pending Approval /
  Approved / Rejected / Sent), bộ chọn phiên bản — xem lại version cũ
  không mất dữ liệu (E4-01 acceptance criteria).
- **Danh sách line item `[core]`**: mỗi dòng là 1 entity instance đã cấu
  hình ở màn `02`, đơn giá final lấy từ waterfall đã snapshot (màn `03`).
  Sửa 1 line → tạo version mới (E4-01: "sửa quote tạo version mới").
- **Khối phê duyệt `[core]`**: logic hiển thị theo role (RBAC, E5-02):
  Sales rep chỉ xem trạng thái (read-only), Sales Manager mới thấy nút
  Duyệt/Từ chối. Lý do cần phê duyệt liệt kê rõ nguồn gốc (ngưỡng
  discount/margin nào bị vượt — theo E4-02 & E3-03), không chỉ ghi
  "vượt ngưỡng" chung.
- **Lịch sử phê duyệt/version `[core]`**: log mọi thay đổi version + mọi
  hành động duyệt/từ chối kèm lý do (liên quan audit log E5-03), append-
  only, không sửa/xoá được.
- **Nút "Xuất PDF" `[core]`**: chỉ là điểm thoát luồng (E4-03), KHÔNG
  thiết kế chi tiết màn PDF ở wireframe này (ngoài phạm vi 4 màn hình yêu
  cầu).

## 3. Luồng điều hướng

- **Đến từ**:
  - `02-configurator.md` (bấm "Thêm vào quote" → tạo quote mới dạng Draft
    nếu chưa có, hoặc thêm line vào quote Draft đang mở).
  - Màn hình danh sách quote (ngoài phạm vi 4 wireframe này) — Sales
    Manager mở 1 quote đang "Pending Approval" từ danh sách của họ.
- **Đi tới**:
  - Bấm "Sửa" trên 1 line → quay lại `02-configurator.md` với dữ liệu line
    đó để chỉnh.
  - Bấm "+ Thêm line item" → `01-vehicle-part-selection.md` (bắt đầu lại
    luồng chọn entity mới).
  - Bấm vào đơn giá (🔗) của 1 line → `03-pricing-waterfall.md` (chế độ
    snapshot, read-only).
  - "Xuất PDF" → thoát khỏi phạm vi các wireframe này (màn hình PDF không
    thuộc Sprint 1 — ngoài 4 màn yêu cầu).

## 4. Trạng thái edge case

- **Loading**: skeleton khi load quote/version/line item lần đầu, hoặc khi
  chuyển version qua dropdown.
- **Empty — quote mới chưa có line item nào**: thay bảng line item bằng
  "Chưa có line item nào — thêm part để bắt đầu" + CTA "[ Thêm part ]" →
  `01-vehicle-part-selection.md`.
- **Empty — chưa có hoạt động phê duyệt nào** (quote v1, chưa gửi duyệt):
  khối lịch sử hiển thị "Chưa có hoạt động phê duyệt nào".
- **Error — tải quote/version lỗi**: banner lỗi + nút "Thử lại", không
  render bảng line item rỗng gây hiểu lầm là quote trống thật.
- **Error — hành động duyệt/từ chối lỗi (network/API)**: inline error
  ngay cạnh nút Duyệt/Từ chối, giữ nguyên input ghi chú/lý do đã nhập,
  cho phép thử lại.
- **Validation — Từ chối thiếu lý do**: chặn submit, báo lỗi inline "Cần
  nhập lý do từ chối" ngay dưới ô nhập.
- **RBAC — role không phù hợp**: Sales rep không thấy nút Duyệt/Từ chối
  (ẩn hoàn toàn, không chỉ disable); role khác Sales/Sales Manager truy
  cập trực tiếp URL → chặn ở tầng API (403), màn hình hiển thị thông báo
  không có quyền truy cập.
- **Trạng thái "Đang chờ duyệt" cho Sales rep**: toàn bộ nút sửa/xóa line
  item bị disable kèm tooltip "Quote đang chờ phê duyệt, không thể sửa"
  (tránh vừa sửa vừa chờ duyệt gây version không khớp lý do duyệt).
