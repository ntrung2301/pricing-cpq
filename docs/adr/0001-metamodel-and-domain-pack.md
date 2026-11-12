# ADR-001: Metamodel động và cơ chế domain pack

- **Trạng thái**: Accepted
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
module pack, core chỉ có `PackLoaderService` đọc file theo schema chung;
(b) pack là dữ liệu seed trực tiếp trong DB qua migration riêng của module
pack. Chọn (a) vì YAML dễ diff/review trong PR, git-track được, và tách
biệt rõ "core biết schema của pack descriptor" khỏi "core biết nội dung
domain" — core không đọc file YAML của automotive, chỉ đọc theo cấu trúc
generic `pack.yaml`.

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

Domain pack = 1 file `pack.yaml` (đóng gói trong module pack, ví dụ
`pack-automotive/src/main/resources/pack.yaml`) khai báo: `pack_id`,
`version`, `entities[]`, `relationships[]`, `constraint_instances[]`,
`rule_sets[]`, `import_mappings[]`, `ui_labels[]`. `PackLoaderService`
(thuộc core, chỉ biết cấu trúc generic của `pack.yaml`) nạp lúc khởi động
(classpath scan `packs/*/pack.yaml`), upsert vào bảng metamodel, mỗi dòng
gắn `pack_id` + `pack_version` để biết nguồn gốc và cho phép nhiều pack
cùng tồn tại. Nâng version pack = tăng `version`, loader diff và chặn thay
đổi phá vỡ dữ liệu hiện có (ví dụ xoá attribute đang có giá trị) trừ khi có
cờ `allow-breaking` tường minh — phục vụ yêu cầu audit ở CLAUDE.md mục 7.

## Hệ quả

- Tác động tới: Epic 1 (E1-01, E1-02), S1-T3, S1-T7, toàn bộ pack automotive
  và pack tổng hợp Sprint 4.
- Rủi ro đã biết: EAV có kiểu phức tạp hơn 1 bảng JSONB khi query nhiều
  điều kiện (join nhiều dòng attribute); chấp nhận vì quy mô vài nghìn part
  (Q-002) không cần tối ưu early, và query phức tạp có thể bọc qua view/
  index theo attribute_def_id phổ biến sau.
- Ảnh hưởng ranh giới core/pack: **Không** — core chỉ có 4 bảng định nghĩa
  generic + 2 bảng instance generic + `PackLoaderService` đọc schema
  `pack.yaml` chung. Domain pack tự kiểm chứng (D-004): một pack tổng hợp
  ("Widget", "ServicePlan" — không liên quan automotive) nạp được qua đúng
  cơ chế này mà không cần thêm bảng hay sửa `PackLoaderService`. Việc ACES/
  PIES (Q-001) sau này chỉ là thêm `attribute_def`/`relationship_def` +
  `import_mappings` mới trong pack automotive, không đụng core.
