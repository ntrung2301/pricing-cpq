# Wireframe 01 — Chọn xe / Chọn part

> Low-fi (mức mô tả cấu trúc). Không phải mockup hi-fi. Tham chiếu:
> `docs/backlog.md` Epic 1 (E1-03, E1-04), Epic 2 (E2-02), `docs/vision.md`
> (Sales rep).

## 1. Mục đích màn hình

Màn hình đầu luồng của Sales rep: chọn xe của khách (thông tin đặc thù
automotive) để lọc ra danh sách part tương thích, rồi chọn 1 part để đưa
vào cấu hình (màn hình 02). Phần lọc theo xe là **[pack:automotive]**; bản
thân danh sách/part catalog là component **[core]** generic, hiển thị cột
theo attribute đã định nghĩa trong entity definition hiện hành (không phải
cột cứng của riêng automotive).

## 2. Layout / thành phần chính

```
┌─────────────────────────────────────────────────────────────────────┐
│ [pack:automotive] Bộ chọn xe                                         │
│  Hãng (Make)   ▾   Dòng (Model) ▾   Năm (Year) ▾   Động cơ (Engine) ▾│
│  [ Tìm part tương thích ]        [ Bỏ qua chọn xe – xem toàn bộ catalog ]│
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] Thanh tìm kiếm / lọc theo attribute                            │
│  🔎 [ tìm theo part number / tên... ]   Lọc: [OEM/Aftermarket ▾] ...  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] Bảng danh sách Part (generic data table, cột = attribute của  │
│         entity definition đang active, không hardcode)               │
│ ─────────────────────────────────────────────────────────────────── │
│ Part Number │ Tên      │ OEM/Aftermarket │ Đơn vị đóng gói │ MOQ │...│
│ P-10023     │ Lọc dầu  │ OEM             │ Cái             │ 1   │...│
│ P-10024     │ Lọc dầu  │ Aftermarket     │ Hộp 12          │ 12  │   │
│             │          │  ⚠ [pack:automotive] đã bị supersede bởi    │
│             │          │     P-10099 (xem gợi ý thay thế)             │
│ ...                                                                   │
│ ◂ 1 2 3 ... 48 ▸   (phân trang, không virtualized — quy mô vài nghìn │
│                     part theo Q-002, search + filter là đủ)          │
└─────────────────────────────────────────────────────────────────────┘
          [ Chọn part này → ]  (trên mỗi dòng, dẫn sang màn 02)
```

### Chi tiết thành phần

- **Bộ chọn xe `[pack:automotive]`**: 4 dropdown phụ thuộc tuần tự
  (Model chỉ load sau khi chọn Make; Engine chỉ load sau khi chọn Year).
  Đây là UI cụ thể của domain pack automotive, map vào entity `Vehicle`
  (E1-03). Nút "Tìm part tương thích" gọi API lọc part theo constraint
  loại `compatibility` (E1-02/E2-02) với xe đã chọn.
- **Nút "Bỏ qua chọn xe – xem toàn bộ catalog" `[core]`**: vì core không
  bắt buộc phải có khái niệm "xe", list part generic phải duyệt được độc
  lập (quan trọng để domain pack khác không có "vehicle" vẫn dùng được
  cùng component danh sách).
- **Bảng danh sách Part `[core]`**: generic data table component, cột
  hiển thị = danh sách attribute của entity definition hiện hành (lấy từ
  metamodel, E1-01). Với pack automotive, entity `Part` có attribute: part
  number, tên, OEM/aftermarket, supersession, kit/bundle, core charge,
  MOQ, đơn vị đóng gói (theo E1-03) — nhưng component bảng không biết đây
  là "part", chỉ render attribute list được cấu hình.
- **Badge supersession `[pack:automotive]`**: hiển thị qua component
  generic "related item hint" `[core]` (một kiểu hiển thị relationship
  giữa 2 entity instance), nhưng nội dung/văn bản ("đã bị supersede bởi")
  và việc có tồn tại hay không là dữ liệu + cấu hình của pack automotive
  (E2-02 "Part có supersession → gợi ý part thay thế").
- **Tìm kiếm & lọc theo attribute `[core]`**: input free-text + dropdown
  lọc theo attribute tùy chọn, generic, không phụ thuộc domain.
- **Phân trang `[core]`**: trang cố định (vd 20-50 dòng/trang), đủ cho quy
  mô vài nghìn part/catalog (theo Q-002 trong `docs/questions.md`) — không
  cần virtualized list.

## 3. Luồng điều hướng

- **Đến từ**: màn hình chủ (dashboard/home) của Sales rep — điểm bắt đầu
  luồng chính "import catalog → chọn xe + part → cấu hình → tính giá →
  tạo quote → phê duyệt → PDF" (`docs/vision.md`).
- **Đi tới**: chọn 1 dòng part ("Chọn part này") → màn hình
  `02-configurator.md`, mang theo `part/entity id` đã chọn + (nếu có)
  context xe đã chọn (dùng để pre-fill/hiển thị kết quả constraint
  compatibility ở màn 02, không bắt buộc).

## 4. Trạng thái edge case

- **Loading**:
  - Khi đổi Make/Model/Year → dropdown kế tiếp hiện skeleton/spinner nhỏ
    trong lúc load option.
  - Khi bấm "Tìm part tương thích" hoặc gõ tìm kiếm → bảng hiện skeleton
    rows, giữ nguyên bộ chọn xe.
- **Empty — không có part tương thích với xe đã chọn** (edge case bắt
  buộc theo `docs/ux/README.md`): hiển thị khối thông báo thay bảng:
  "Không có part tương thích với xe đã chọn." + gợi ý: nới điều kiện (vd
  bỏ Engine), hoặc dùng nút "Bỏ qua chọn xe – xem toàn bộ catalog", hoặc
  liên hệ Pricing Admin nếu nghi thiếu dữ liệu fitment `[pack:automotive]`.
- **Empty — chưa chọn xe lần đầu vào màn hình**: bảng mặc định hiển thị
  toàn bộ catalog (không ép buộc phải chọn xe trước, vì core không coi xe
  là bắt buộc) kèm dòng gợi ý nhẹ "Chọn xe ở trên để lọc nhanh hơn".
- **Empty — tìm kiếm không ra kết quả**: "Không tìm thấy part khớp từ khóa
  tìm kiếm" (khác với trường hợp empty do xe, để Sales rep biết nguyên
  nhân).
- **Error**: lỗi gọi API load danh sách Make/Model/Year hoặc load danh
  sách Part → banner lỗi inline trong từng khối tương ứng + nút "Thử
  lại", không làm crash toàn trang.
