# Wireframe 02 — Configurator (generic)

> Low-fi. Tham chiếu: `docs/backlog.md` Epic 2 (E2-01, E2-02), Epic 1
> (E1-02 constraint-as-data). Toàn bộ màn hình **[core]** — fitment chỉ là
> **một loại constraint cụ thể** hiển thị qua component generic, không
> hardcode khái niệm "xe"/"fitment" trong UI lõi.

## 1. Mục đích màn hình

Cho phép người dùng chọn 1 entity chính (vd Part trong pack automotive,
nhưng core chỉ biết "entity instance") và các entity/attribute liên quan,
rồi validate toàn bộ lựa chọn qua constraint engine generic (E2-01). Kết
quả hợp lệ → có thể thêm vào quote; vi phạm constraint → báo lỗi rõ theo
từng constraint, không phải lỗi 500.

## 2. Layout / thành phần chính

```
┌─────────────────────────────────────────────────────────────────────┐
│ [core] Tóm tắt entity đã chọn                                        │
│  Tên entity: <từ attribute "name">   ID: <entity instance id>        │
│  Thuộc tính: attribute 1 = giá trị | attribute 2 = giá trị | ...      │
│  (danh sách attribute render động theo entity definition, không       │
│   hardcode "part number"/"OEM" — đó là giá trị do pack cấu hình)       │
│                                              [ ← Quay lại chọn ]      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] Entity / attribute liên quan (relationship_def)               │
│  Loại quan hệ A: [ chọn entity liên quan ▾ ]  (vd bundle component)   │
│  Loại quan hệ B: [ chọn entity liên quan ▾ ]  (vd accessory)          │
│  Số lượng (quantity): [ 1 ▾ ]                                         │
│  Thuộc tính cấu hình khác (nếu entity definition có attribute cho     │
│  phép chọn, vd màu/variant): [ dropdown/input động ]                  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] Bảng kiểm tra ràng buộc (constraint validation panel)         │
│ ─────────────────────────────────────────────────────────────────── │
│  ✔ Ràng buộc "compatibility": <label từ constraint definition>        │
│     — ĐẠT  (vd: entity tương thích với context đã chọn ở màn trước)   │
│  ✔ Ràng buộc "required-quantity": MOQ thỏa điều kiện — ĐẠT            │
│  ✘ Ràng buộc "<loại khác>": <message lỗi cụ thể> — KHÔNG ĐẠT          │
│  (mỗi dòng generic: tên loại constraint + label + trạng thái + msg;   │
│   "compatibility" chỉ là 1 trong nhiều loại, không ưu tiên hiển thị   │
│   riêng cho fitment)                                                  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] Xem trước giá                                                  │
│  Giá tạm tính: 1.250.000 ₫      [ Xem chi tiết waterfall → ]          │
└─────────────────────────────────────────────────────────────────────┘

                [ Thêm vào quote ]   (disable nếu còn constraint KHÔNG ĐẠT)
```

### Chi tiết thành phần

- **Tóm tắt entity đã chọn `[core]`**: hiển thị attribute của entity
  instance hiện tại, render động theo entity definition (E1-01) — không
  giả định tên field cụ thể nào.
- **Entity/attribute liên quan `[core]`**: UI chọn các entity khác theo
  `relationship_def` đã khai báo trong metamodel; với automotive có thể
  là bundle/kit component (E1-03 "kit/bundle"), nhưng component này không
  biết trước loại quan hệ cụ thể là gì, chỉ render theo config.
- **Bảng kiểm tra ràng buộc `[core]`**: hiển thị generic mọi constraint
  được constraint engine (E1-02) trả về cho combination hiện tại, gồm
  loại constraint, label (text do domain pack cấu hình), trạng thái
  (đạt/không đạt/cảnh báo), message lỗi nếu có. Constraint "compatibility"
  (vd fitment theo xe ở automotive) chỉ là **1 dòng trong bảng này**, same
  cấu trúc như mọi loại constraint khác — đây là điểm phải tuân thủ ranh
  giới core/pack trong `CLAUDE.md`.
- **Xem trước giá `[core]`**: số tạm tính, link sang màn `03`.
- **Nút "Thêm vào quote" `[core]`**: bị disable khi có constraint ở trạng
  thái KHÔNG ĐẠT (theo E2-01 "chọn combination vi phạm constraint → báo
  lỗi rõ").

## 3. Luồng điều hướng

- **Đến từ**: `01-vehicle-part-selection.md` (bấm "Chọn part này"), mang
  theo entity id đã chọn + context tùy chọn (vd vehicle id, chỉ dùng để
  constraint "compatibility" đánh giá — màn hình configurator tự nó không
  yêu cầu context này phải tồn tại, để domain pack khác không có "xe" vẫn
  dùng được màn hình này mà không qua màn 01).
- **Đi tới**:
  - "Xem chi tiết waterfall" → `03-pricing-waterfall.md`.
  - "Thêm vào quote" → tạo/thêm line item vào quote, chuyển sang
    `04-quote-approval.md` (hoặc hiện toast + ở lại màn hình để thêm tiếp
    entity khác, tuỳ luồng — mặc định: chuyển sang quote để Sales rep xác
    nhận line item vừa thêm).
  - "Quay lại chọn" → về `01-vehicle-part-selection.md`.

## 4. Trạng thái edge case

- **Loading**: sau mỗi thay đổi lựa chọn (entity liên quan, quantity,
  attribute) → bảng kiểm tra ràng buộc hiện trạng thái "Đang kiểm tra ràng
  buộc..." (spinner nhỏ trong panel, không che toàn màn hình, các phần
  khác vẫn tương tác được trừ nút "Thêm vào quote" bị disable tạm trong
  lúc chờ).
- **Empty — chưa có entity liên quan nào được cấu hình** cho một loại
  quan hệ: dropdown hiển thị "Chưa có entity liên quan nào được cấu hình
  cho loại quan hệ này" thay vì danh sách trống im lặng.
- **Error — vi phạm constraint**: dòng tương ứng trong bảng kiểm tra đổi
  màu cảnh báo (✘), hiển thị message rõ ràng theo constraint definition
  (vd "Entity đã chọn không tương thích với context hiện tại"), nút "Thêm
  vào quote" disable, không có lỗi hệ thống (không phải lỗi 500 — theo
  acceptance criteria E2-01).
- **Error — lỗi hệ thống khi gọi API validate constraint**: banner lỗi
  riêng (khác với lỗi nghiệp vụ ở trên) + nút "Thử lại", giữ nguyên lựa
  chọn hiện tại của người dùng (không mất input).
- **Success**: khi mọi constraint ĐẠT → có thể hiển thị dòng tổng kết
  "Tất cả ràng buộc hợp lệ" phía trên nút "Thêm vào quote" để người dùng
  yên tâm submit.
