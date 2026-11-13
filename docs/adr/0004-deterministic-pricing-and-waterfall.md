# ADR-004: Tính giá deterministic và lưu price waterfall

- **Trạng thái**: Accepted
- **Ngày**: 2026-11-12
- **Người quyết**: architect

## Ngữ cảnh

CLAUDE.md mục 7 bắt buộc: tiền dùng `BigDecimal`, mọi phép tính giá tạo ra
"price waterfall" giải thích được và lưu lại để audit/replay. Cần chốt thứ
tự áp rule cố định (không phụ thuộc thứ tự nạp DB) và cách tránh quote cũ
"trôi" kết quả khi rule thay đổi sau này.

## Lựa chọn đã xét

1. **Tính giá "live" mỗi lần xem quote (luôn dùng rule/price list hiện
   tại)** — đơn giản, nhưng quote cũ sẽ cho giá khác nếu rule đổi sau đó →
   vi phạm "audit/replay" (không tái tạo được giá lúc tạo quote). Loại.
2. **Snapshot rule_set + price_list version ngay tại thời điểm tạo
   `quote_version`, lưu id snapshot trên `quote_version`, không tính lại
   từ rule "hiện tại" khi xem lại** — đảm bảo replay đúng y nguyên dù rule
   gốc đổi sau. Chọn.
3. **Rounding ở mỗi bước (per-step)** — dễ hiểu từng dòng nhưng rounding
   error tích lũy qua nhiều bước, 2 cách tính khác thứ tự có thể lệch vài
   đơn vị nhỏ nhất → không deterministic theo nghĩa "mọi cách tính cho
   cùng kết quả". Loại làm mặc định.
4. **Rounding chỉ ở biên: giá hiển thị (unit price) và tổng cuối dòng quote
   (final_amount); các bước trung gian giữ scale nội bộ cao hơn (scale 6)
   rồi mới round về scale tiền tệ (2) ở bước cuối** — giảm sai số tích
   luỹ, mỗi bước vẫn lưu giá trị đầy đủ để audit (không mất độ chính xác
   khi hiển thị lại). Chọn.

## Quyết định

Mọi số tiền dùng `BigDecimal`, scale nội bộ tối thiểu 6 chữ số, chỉ
`setScale(2, RoundingMode.HALF_UP)` tại bước cuối cùng của mỗi quote line
(và tại bất kỳ bước nào tự đánh dấu `rounded=true`, ví dụ bước hiển thị
unit price cho sales rep). Thứ tự áp rule là 1 pipeline **stage cố định**
do core định nghĩa — generic, không gắn domain: `base` → `segment-
adjustment` → `quantity-adjustment` → `discount` → `override` → `guardrail`
(ví dụ: tier khách E3-04 map vào stage `segment-adjustment`, volume break
E3-01 map vào `quantity-adjustment`, contract override E3-04 map vào
`override`, margin guardrail E3-03 map vào `guardrail`). Trong mỗi stage,
rule áp theo `priority` rồi `rule.id` làm tie-break — đảm bảo thứ tự xác
định tuyệt đối không phụ thuộc thứ tự đọc từ DB. Mỗi lần 1 rule được áp
(hoặc stage không có rule nào match) tạo 1 dòng `quote_line_waterfall_step`
(step_order, stage, rule_id nullable, label, delta_amount, resulting_amount,
rounded boolean). Tổng `delta_amount` các step = `final_amount` (test
round-trip bắt buộc theo E3-02). Khi tạo `quote_version`, hệ thống chụp
snapshot: `rule_set_version_id` + `price_list_version_id` gắn cố định vào
version đó; xem lại quote cũ luôn dùng snapshot, không query rule "hiện
tại" — đảm bảo replay đúng.

## Hệ quả

- Tác động tới: E3-01, E3-02, E3-03, E3-04, ADR-002, ADR-003 (bảng
  `quote_version`, `quote_line_waterfall_step`).
- Rủi ro đã biết: snapshot rule_set/price_list nghĩa là cần versioning rõ
  ràng cho `rule_set` và `price_list` (mỗi lần Pricing Admin sửa rule tạo
  version mới, không sửa in-place) — việc này phải làm rõ khi backend-dev
  cài S1-T7/Sprint 2, ghi chú lại cho backend-dev.
- Ảnh hưởng ranh giới core/pack: **Không** — tên stage (`base`,
  `segment-adjustment`, `quantity-adjustment`, `discount`, `override`,
  `guardrail`) là khái niệm generic của core, không phải "tier
  OEM/Distributor". Domain pack (automotive hay pack tổng hợp) chỉ gắn
  rule cụ thể của mình vào 1 trong các stage có sẵn; không pack nào cần
  thêm stage mới để chạy được backlog hiện tại — nếu phát sinh nhu cầu đó,
  phải báo PO (CLAUDE.md mục 6), không tự thêm.
