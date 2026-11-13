# ADR-003: Mô hình dữ liệu catalog, fitment, price list, quote + version

- **Trạng thái**: Accepted
- **Ngày**: 2026-11-12
- **Người quyết**: architect

## Ngữ cảnh

Cần 1 mô hình dữ liệu cụ thể cho: catalog (part/entity instance), fitment
(constraint instance loại "compatibility"), price list, và quote có version
— dùng chung cho ADR-001 (metamodel) và ADR-002 (rule engine). Theo D-004,
mô hình phải đủ generic để pack tổng hợp Sprint 4 dùng được nguyên schema
này. Theo D-005, import catalog chỉ match tên cột = tên attribute, nên
`attribute_def` cần 1 field "code" ổn định dùng làm header cột, tách khỏi
label hiển thị UI. Theo Q-001/Q-002: fitment nhập tay/CSV, vài nghìn part —
không cần tối ưu ACES/PIES thật nhưng thiết kế để map được sau.

## Lựa chọn đã xét

1. **Bảng riêng cho từng domain concept** (`part`, `vehicle`, `fitment`,
   `supersession`, `kit`) — rõ ràng, dễ query, nhưng đặt tên bảng/cột theo
   domain keyword ngay trong core → vi phạm trực tiếp ranh giới (CLAUDE.md
   cấm từ khóa `part/fitment/vehicle/supersession` trong core). Loại.
2. **Toàn bộ dùng `entity_instance` generic (ADR-001) cho catalog; fitment/
   supersession/kit là instance của `relationship_def`/`constraint_def`
   generic, không có bảng riêng** — đúng ranh giới, tái dùng y nguyên
   metamodel đã quyết ở ADR-001, không thêm khái niệm core mới. Chọn.
3. **Price list/quote là bảng riêng, không qua EAV** — vì price list, rule
   set, quote là khái niệm **core chính thức** (CLAUDE.md: "Lõi chỉ biết:
   entity, attribute, relationship, constraint, rule, price list, quote"),
   không cần generic hoá thêm 1 lớp nữa; mô hình quan hệ thường (bảng +
   cột cố định) cho các khái niệm này là hợp lý và dễ audit/query hơn EAV.
   Chọn.

## Quyết định

- **Catalog**: mỗi "Part" (automotive) hay "Widget" (pack tổng hợp) là 1
  `entity_instance` thuộc `entity_def` do pack định nghĩa (ADR-001); giá
  trị field nằm trong `entity_instance_attribute_value`. Core không có
  bảng `part`.
- **Fitment**: là 1 `constraint_instance` của `constraint_def` loại
  `compatibility` (đã có generic ở core theo E1-02) — ví dụ instance nói
  "entity_instance X (Part) tương thích entity_instance Y (Vehicle) nếu
  điều kiện Z" (điều kiện dùng cùng cú pháp JSON DSL của ADR-002). Core chỉ
  biết "compatibility constraint giữa 2 entity instance theo điều kiện
  data", không biết đó là "xe".
- **Supersession**: `relationship_def` loại tự tham chiếu (từ `Part` tới
  `Part` cùng entity_def), code ví dụ `"supersedes"`, do pack automotive
  khai báo trong `pack.yaml`; core chỉ cung cấp bảng `relationship_instance`
  generic (from_instance_id, to_instance_id, relationship_def_id,
  attributes JSON cho ví dụ `effective_date`).
- **Kit/bundle**: cùng cơ chế — `relationship_def` loại `"contains"`,
  không bảng riêng.
- **Import**: `attribute_def` có cột `code` (bất biến, dùng làm header cột
  CSV/Excel phải khớp chính xác — theo D-005) tách khỏi `label` (hiển thị
  UI, có thể đổi/đa ngôn ngữ). `import_job` (entity_def_id, file_name,
  status, started_at) + `import_job_row_error` (job_id, row_number,
  column_name, message) ghi lỗi dòng/cột cụ thể, không tự đoán mapping.
  Ghi chú ACES/PIES: `attribute_def` có cột `external_code_hint` (nullable,
  free text, ví dụ `"ACES:PartTerminologyID"`) chỉ để ghi chú tương ứng
  tương lai — không có logic map thật trong MVP, đúng Q-001.
- **Price list**: `price_list` (code, name, currency) + `price_list_item`
  (price_list_id, entity_def_id, entity_instance_id nullable — nullable
  nghĩa là áp theo điều kiện/segment thay vì 1 instance cụ thể, amount
  `NUMERIC(19,4)`, valid_from/valid_to).
- **Quote**: `quote` (id, code, status) → `quote_version` (quote_id,
  version_no, created_at, rule_set snapshot id, price_list snapshot id —
  xem ADR-004 lý do snapshot) → `quote_line` (quote_version_id,
  entity_instance_id, quantity `NUMERIC`, unit_price `NUMERIC(19,4)`,
  final_amount `NUMERIC(19,4)`) → `quote_line_waterfall_step` (ADR-004).
  Sửa quote tạo `quote_version` mới, version cũ giữ nguyên (immutable) để
  xem lại không mất dữ liệu (E4-01).

## Hệ quả

- Tác động tới: E1-03, E1-04, E2-02, E3-01, E3-04, E4-01, ADR-001, ADR-004.
- Rủi ro đã biết: query catalog phức tạp (nhiều điều kiện attribute) sẽ
  cần join nhiều dòng `entity_instance_attribute_value` — chấp nhận ở quy
  mô vài nghìn part/vài chục nghìn fitment (Q-002); nếu chậm, thêm
  materialized view/index theo `attribute_def_id` phổ biến, không đổi mô
  hình gốc.
- Ảnh hưởng ranh giới core/pack: **Không** — không bảng nào mang tên
  `part/vehicle/fitment/supersession/kit`; mọi khái niệm automotive là
  dòng dữ liệu (`entity_def`, `relationship_def`, `constraint_def`,
  `constraint_instance`) do pack automotive nạp qua `pack.yaml`. Pack tổng
  hợp Sprint 4 (D-004) dùng nguyên schema này không cần thêm bảng — đây là
  cách tự kiểm tra ADR-003 trước khi Sprint 4 thực thi.
