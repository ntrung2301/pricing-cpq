# ADR-001: Metamodel động và cơ chế domain pack

- **Trạng thái**: Accepted (revised 2026-11-12 theo PO override, xem D-013;
  amended 2026-11-13 theo D-019 — thêm key/label attribute generic + cap 20
  attribute/entity_def)
- **Ngày**: 2026-11-12
- **Người quyết**: architect

## Ngữ cảnh

Core phải generic tuyệt đối (không biết "Part", "Vehicle", "fitment"...);
mọi domain pack (automotive là pack đầu, pack tổng hợp D-004 là pack thứ hai)
phải định nghĩa entity/attribute/relationship/constraint bằng **dữ liệu**,
không bằng class Java. Cần chốt ngay đầu Sprint 1 vì S1-T3 (repo skeleton),
S1-T7 (data model) phụ thuộc trực tiếp vào quyết định này. Theo D-004, không
được thiết kế ngầm theo cấu trúc automotive — phải tự kiểm bằng cách tưởng
tượng domain pack tổng hợp Sprint 4 (ví dụ: "Widget", "ServicePlan") nạp vào
mà không cần sửa lõi.

## Lựa chọn đã xét

1. **Entity cứng bằng JPA class per domain** — nhanh để code lúc đầu, nhưng
   thêm domain mới = thêm code + build + deploy core → vi phạm trực tiếp
   ranh giới core/pack. Loại.
2. **EAV thuần, giá trị lưu JSONB tự do (schema-less hoàn toàn)** — rất
   generic, nhưng mất khả năng validate theo type, khó query/filter theo
   attribute cụ thể (ví dụ lọc theo "part_number"), khó viết ArchUnit kiểm
   tra vì mọi domain knowledge trốn trong JSON string không thể phân tích
   tĩnh. Loại làm mô hình chính, nhưng giữ lại làm 1 attribute type
   ("JSON") cho các field thực sự tự do.
3. **EAV có kiểu (typed) — entity_instance + entity_instance_attribute_value
   với cột giá trị theo type (value_string/value_number/value_date/
   value_boolean/value_ref_instance_id)** — generic, vẫn query được theo
   attribute cụ thể, validate được theo `attribute_def.data_type`, và mọi
   domain keyword chỉ tồn tại dưới dạng **dữ liệu** (tên entity/attribute),
   không phải tên bảng/cột/class → ArchUnit chỉ cần quét package/class/
   string literal trong code, không cần quét dữ liệu runtime. Chọn.
4. **Compile domain pack thành class Java (code generation)** — vẫn ràng
   buộc pack vào build/deploy của core, trái với "pack = config, không
   fork code". Loại.

Với cơ chế nạp pack: xét (a) pack là file cấu hình (YAML) đóng gói trong
module Java riêng (`pack-automotive`), core chỉ có `PackLoaderService` đọc
file theo schema chung; (b) pack là dữ liệu seed trực tiếp trong DB qua
migration riêng; (c) pack là file cấu hình (YAML) đặt trong **thư mục
config** (không phải module Java riêng, không compile riêng), nạp lúc
khởi động bởi `PackLoaderService` của app. Chọn (c) — **đã sửa sau khi PO
yêu cầu bỏ tách module code core/pack (D-013)**: so với (a), (c) vẫn giữ
được lý do chọn YAML ban đầu (dễ diff/review trong PR, git-track được,
"core biết schema pack descriptor chung" khỏi "core biết nội dung domain")
nhưng không cần build/compile riêng 1 module Gradle cho mỗi pack — pack chỉ
là tập file YAML/JSON trong `app/src/main/resources/config/packs/<pack-id>/
pack.yaml` (hoặc mount từ ngoài lúc deploy nếu cần đổi không build lại).
So với (b): vẫn chọn file hơn seed DB trực tiếp vì git-diff được trong PR.

## Quyết định

Metamodel = 4 khái niệm generic lưu dưới dạng dữ liệu trong core:
**EntityDefinition** (code, label, version), **AttributeDefinition** (code,
label, data_type: STRING/NUMBER/DECIMAL/BOOLEAN/DATE/ENUM/REFERENCE/JSON,
required, entity_def_id), **RelationshipDefinition** (code, from/to
entity_def, cardinality, ví dụ "supersedes", "contains" — core không biết
đây dùng cho gì), **ConstraintDefinition** (code/type, ví dụ
"compatibility", "range", "required-if" — theo E1-02). Dữ liệu instance của
entity lưu theo mô hình EAV có kiểu: bảng `entity_instance` +
`entity_instance_attribute_value` (giá trị theo cột đúng type, validate
theo `attribute_def.data_type` lúc viết).

**Key/label attribute + cap 20 (D-019, 2026-11-13)**: `entity_def` có thêm 2
con trỏ tuỳ chọn tới `attribute_def` của chính nó — `key_attribute_def_id`
(thuộc tính đóng vai **business key**, giá trị duy nhất trong phạm vi
`entity_def_id + pack_id`) và `label_attribute_def_id` (thuộc tính dùng làm
**tên hiển thị**). Core chỉ biết khái niệm "key"/"label" generic — không
biết đó là SKU hay Customer ID, pack tự quyết attribute nào giữ vai trò
này. `entity_instance` có thêm 2 cột denormalize `business_key` và
`display_label` (tên cột generic, có index + unique constraint theo
`entity_def_id + pack_id + business_key`), được đồng bộ từ giá trị EAV của
key/label attribute mỗi khi ghi `entity_instance_attribute_value` — tránh
phải join EAV mỗi lần cần tra cứu/validate duy nhất theo khoá nghiệp vụ
(ví dụ import đối chiếu theo SKU, supersession reference theo Part key).
Mỗi `entity_def` tối đa **20 `attribute_def`** (gồm cả key/label nếu có),
`PackLoaderService` validate khi nạp `pack.yaml`, từ chối nạp nếu vượt.

Domain pack = 1 file `pack.yaml` (đặt trong thư mục config, ví dụ
`app/src/main/resources/config/packs/automotive/pack.yaml` — **không phải
module Java riêng**) khai báo: `pack_id`, `version`, `entities[]`,
`relationships[]`, `constraint_instances[]`, `rule_sets[]`,
`import_mappings[]`, `ui_labels[]`. `PackLoaderService` (thuộc app, chỉ
biết cấu trúc generic của `pack.yaml`) nạp lúc khởi động (classpath scan
`config/packs/*/pack.yaml`), upsert vào bảng metamodel, mỗi dòng gắn
`pack_id` + `pack_version` để biết nguồn gốc và cho phép nhiều pack cùng
tồn tại. Nâng version pack = tăng `version`, loader diff và chặn thay đổi
phá vỡ dữ liệu hiện có (ví dụ xoá attribute đang có giá trị) trừ khi có cờ
`allow-breaking` tường minh — phục vụ yêu cầu audit ở CLAUDE.md mục 7.

## Hệ quả

- Tác động tới: Epic 1 (E1-01, E1-02), S1-T3, S1-T7, toàn bộ pack automotive
  và pack tổng hợp Sprint 4.
- Rủi ro đã biết: EAV có kiểu phức tạp hơn 1 bảng JSONB khi query nhiều
  điều kiện (join nhiều dòng attribute); chấp nhận vì quy mô vài nghìn part
  (Q-002) không cần tối ưu early, và query phức tạp có thể bọc qua view/
  index theo attribute_def_id phổ biến sau.
- Ảnh hưởng ranh giới core/pack: **Không** — engine (metamodel + 2 bảng
  định nghĩa generic + 2 bảng instance generic + `PackLoaderService`) không
  chứa khái niệm domain nào; không có module Java riêng cho pack (D-013) —
  pack chỉ là file config. Domain tự kiểm chứng (D-004): một domain tổng
  hợp ("Widget", "ServicePlan" — không liên quan automotive) nạp được qua
  đúng cơ chế này mà không cần thêm bảng, sửa `PackLoaderService`, hay thêm
  bất kỳ file Java nào — chỉ thêm 1 file `pack.yaml` mới. Việc ACES/PIES
  (Q-001) sau này chỉ là thêm `attribute_def`/`relationship_def` +
  `import_mappings` mới trong file config automotive, không đụng engine.
  Key/label + cap 20 (D-019) không đổi kết luận này — "key"/"label" là
  khái niệm metamodel generic, pack tổng hợp Sprint 4 chỉ cần khai báo
  `key_attribute_def_id`/`label_attribute_def_id` trong `pack.yaml` của nó
  như mọi pack khác, không cần sửa engine.
