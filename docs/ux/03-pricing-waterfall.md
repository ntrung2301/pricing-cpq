# Wireframe 03 — Bảng giá + Price Waterfall

> Low-fi. Tham chiếu: `docs/backlog.md` Epic 3 (E3-01, E3-02, E3-03,
> E3-04). Toàn bộ màn hình **[core]** — các bước waterfall là khái niệm
> generic của rule engine; dữ liệu cụ thể (tên tier, số tiền hợp đồng...)
> do domain pack/khách hàng cấu hình nhưng cấu trúc hiển thị không đổi
> giữa domain pack.

## 1. Mục đích màn hình

Hiển thị đầy đủ, minh bạch, có thể kiểm toán từng bước tính giá — từ list
price đến giá cuối — đúng theo CLAUDE.md mục 7 ("Mọi phép tính giá phải
tạo ra price waterfall giải thích được"). Dùng để Sales rep hiểu vì sao ra
giá đó, và để Sales Manager (màn `04`) ra quyết định duyệt nhanh dựa trên
waterfall này (`docs/vision.md`).

## 2. Layout / thành phần chính

```
┌─────────────────────────────────────────────────────────────────────┐
│ [core] Header                                                         │
│  Entity: <tên entity instance>   SL: 12   Khách: Acme Co.             │
│  Tier khách hàng: <tên tier>   (giá trị tier cụ thể do pack cấu hình, │
│                                 khái niệm "tier" là [core])            │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] Price Waterfall                                                │
│ ─────────────────────────────────────────────────────────────────── │
│  1. List price                         1.500.000 ₫                   │
│  2. Tier adjustment      (-10%)          -150.000 ₫  → 1.350.000 ₫   │
│  3. Volume break         (qty ≥ 10, -5%)  -67.500 ₫  → 1.282.500 ₫   │
│  4. Discount             (chiết khấu thủ công -3%,                    │
│                            lý do: "khách lâu năm")  -38.475 ₫         │
│                                                     → 1.244.025 ₫    │
│  5. Contract override    (giá hợp đồng riêng áp dụng,                │
│                            thay thế bước trên)        = 1.200.000 ₫  │
│  ───────────────────────────────────────────────────────────────    │
│  Giá cuối (final price / đơn vị)                    1.200.000 ₫      │
│  Số lượng × Giá cuối = Tổng dòng                    14.400.000 ₫      │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [core] ⚠ Cảnh báo margin guardrail (hiển thị nếu vi phạm)            │
│  "Giá cuối dưới ngưỡng margin tối thiểu (config: 15%) — cần phê      │
│   duyệt trước khi gửi khách."                                        │
└─────────────────────────────────────────────────────────────────────┘

                [ ← Quay lại cấu hình ]   [ Áp dụng vào quote ]
```

### Chi tiết thành phần

- **Header `[core]`**: entity, số lượng, khách hàng, tier khách hàng.
  Khái niệm "tier" là generic (E3-01); giá trị cụ thể của tier (vd
  OEM/Distributor/Dealer/Workshop — E3-04) là dữ liệu do pack automotive
  cấu hình, màn hình chỉ hiển thị tên tier như một string.
- **Bảng Waterfall `[core]`**: mỗi dòng = 1 bước điều chỉnh theo đúng thứ
  tự acceptance criteria E3-02 (list price → tier adjustment → volume
  break → discount → contract override → final), kèm số tiền/phần trăm
  điều chỉnh, running subtotal, và lý do (nếu có, vd discount reason
  code). Tổng các bước phải khớp giá cuối — round-trip kiểm chứng được
  (yêu cầu E3-02), nên UI hiển thị subtotal sau mỗi bước để người dùng tự
  verify bằng mắt.
  - Bước "Contract override" `[core]`: khái niệm generic "có giá override
    ghi đè các bước phía trên" — việc khách có "hợp đồng riêng" là dữ
    liệu pack automotive (E3-04), nhưng waterfall core phải có slot cho
    loại bước này để hiển thị đúng khi áp dụng.
- **Cảnh báo margin guardrail `[core]`**: hiển thị khi giá cuối dưới
  ngưỡng config (E3-03), không block xem giá nhưng sẽ kéo theo workflow
  phê duyệt ở màn `04`.
- **Tiền luôn hiển thị theo BigDecimal/định dạng tiền tệ chuẩn**, không
  hiển thị số thực dạng float có sai số (nhất quán với CLAUDE.md mục 7).

## 3. Luồng điều hướng

- **Đến từ**:
  - `02-configurator.md` (link "Xem chi tiết waterfall").
  - `04-quote-approval.md` (bấm vào giá của 1 line item để xem breakdown
    — khi xem từ đây, waterfall là **bản chụp (snapshot) của version quote
    đó**, không tính lại real-time).
- **Đi tới**:
  - "Áp dụng vào quote" → quay về/tạo line item trong
    `04-quote-approval.md` với waterfall này được lưu làm snapshot.
  - "Quay lại cấu hình" → `02-configurator.md`, giữ nguyên lựa chọn.

## 4. Trạng thái edge case

- **Loading**: "Đang tính giá..." (spinner thay bảng waterfall) mỗi khi
  số lượng, khách hàng, hoặc entity/attribute thay đổi và cần gọi lại rule
  engine.
- **Empty — không có bảng giá áp dụng**: nếu không tìm được price list
  phù hợp với entity/khách hàng hiện tại → thay bảng waterfall bằng thông
  báo "Không tìm thấy bảng giá áp dụng cho entity/khách hàng này. Liên hệ
  Pricing Admin để kiểm tra cấu hình." — nút "Áp dụng vào quote" disable.
- **Error — lỗi tính giá**: lỗi rule engine (vd rule cấu hình sai, thiếu
  dữ liệu bắt buộc) → banner lỗi rõ ràng, không hiển thị số giá sai/one-off,
  nút "Áp dụng vào quote" disable, có nút "Thử lại".
- **Warning — dưới ngưỡng margin**: banner cảnh báo (không chặn xem, có
  chặn gửi khách ở bước sau cho tới khi được duyệt — xem `04`).
- **Read-only / snapshot**: khi mở từ quote đã có (version cũ hoặc version
  hiện tại đã lưu), waterfall hiển thị dưới dạng chỉ-đọc kèm ghi chú "Ảnh
  chụp lúc tạo/lưu quote — không tính lại theo giá hiện tại", ẩn nút "Áp
  dụng vào quote", chỉ còn nút "Quay lại quote".
