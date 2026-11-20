# ADR-009: Entity taxonomy mở rộng, execution engine dạng DAG + Measure, và module Price List

- **Trạng thái**: Accepted
- **Ngày**: 2026-11-20
- **Người quyết**: PO (Trung), qua thảo luận trực tiếp với agent — không phải
  architect spike độc lập như ADR-001..007; ghi "Người quyết: PO" vì mọi
  lựa chọn thiết kế trong ADR này đến từ quyết định trực tiếp của PO trong
  phiên làm việc, agent chỉ tổng hợp/đề xuất kỹ thuật khi PO yêu cầu.

## Ngữ cảnh

Sau khi Sprint 2 hoàn tất (automotive MVP: metamodel, catalog, configurator,
pricing engine tuyến tính 6-stage cố định — ADR-004 — hybrid DSL/Groovy —
D-014), PO xác định lại mục tiêu sản phẩm rộng hơn phạm vi automotive MVP
ban đầu: xây 1 platform tính giá tuỳ biến được cho nhiều mô hình kinh doanh
(SME, không nhắm enterprise), lấy cảm hứng từ pattern thật của enterprise
pricing (SAP condition technique, Pricefx Price List/Rebate Agreement) nhưng
đơn giản hoá.

Khi đối chiếu với ví dụ pricing logic cấp "Level 4 — Complex Enterprise
Pricing" (PO cung cấp) — weighted average cost 12 tháng, giá bán trung bình
sản phẩm tương đương, agreement resolution, customer hierarchy discount,
volume discount theo doanh số năm, currency conversion, margin
validation, rounding theo quốc gia — rõ ràng pipeline tuyến tính 6-stage cố
định của ADR-004 không đủ biểu đạt (không có khái niệm query/filter/
aggregate/join nhiều nguồn dữ liệu). Nhưng cố nhồi hết logic này vào 1 bộ
node visual sẽ dần biến thành 1 ngôn ngữ lập trình thị giác đầy đủ (cần
loop, biến mutable, join, aggregate, error handling...) — rủi ro kỹ thuật/
bảo trì lớn, và quay lại đúng vấn đề PO muốn tránh với Groovy (chỉ khác là
visual thay vì text, vẫn cần "SDK" để business user hiểu).

Cần 1 kiến trúc trung gian: đủ mạnh cho logic phức tạp, vẫn generic/data-
driven (CLAUDE.md mục 7 — rule lưu dạng dữ liệu), vẫn kéo-thả được cho
business user (không cần biết code/SDK), và vẫn deterministic/audit được
tuyệt đối (không loop/trạng thái mutable không kiểm soát được).

PO yêu cầu làm Price List trước (module pricing đầu tiên, đơn giản nhất,
dùng để validate kiến trúc trước khi làm Promotion/Agreement và Quote-time
calculation sau).

## Lựa chọn đã xét

1. **Giữ nguyên pipeline tuyến tính 6-stage cố định (ADR-004 nguyên bản)**
   — đơn giản, đã chạy được ở Sprint 2, nhưng tên/thứ tự stage cố định
   trong Java enum (`PricingStage`), không tuỳ biến được theo từng loại
   pricing logic/ngành khác nhau; không biểu đạt được multi-source lookup/
   aggregate cần cho Level 4. Loại làm kiến trúc lâu dài, nhưng nguyên tắc
   audit/snapshot/BigDecimal/rounding của ADR-004 vẫn giữ nguyên.
2. **Pipeline tuyến tính nhưng stage trở thành data (danh sách tên/thứ tự
   configurable, không phải enum)** — linh hoạt hơn bước 1, nhưng vẫn chỉ
   là 1 danh sách tuyến tính 1 chiều, không biểu đạt được nhánh/multi-
   source cần cho Level 4 (vd 1 kết quả cần join dữ liệu từ 2 nguồn trước
   khi tính tiếp). Loại làm giải pháp cuối.
3. **Imperative visual programming language đầy đủ** (loop, biến mutable,
   arbitrary control flow, giống Node-RED/n8n tổng quát) — đủ mạnh cho mọi
   case, nhưng tự xây 1 ngôn ngữ lập trình + runtime riêng là rủi ro kỹ
   thuật/bảo trì rất lớn, khó đảm bảo deterministic (loop không bị treo,
   không side-effect ẩn), và không giải quyết được lo ngại "business user
   vẫn cần hiểu khái niệm lập trình" — chỉ đổi text thành visual. Loại.
4. **Dataflow Graph (DAG)**: node có input/output tường minh, không loop/
   biến mutable, thực thi theo topological order (thứ tự tự suy ra từ
   input reference, không hardcode); "Measure" = subgraph có tên, định
   nghĩa 1 lần, tham chiếu lại nhiều nơi (tương tự DAX measure trong Power
   BI, tham số hoá theo "context" hiện tại, không hardcode 1 instance cụ
   thể). Đủ mạnh cho toàn bộ ví dụ Level 4 (query/filter/aggregate/
   currency-conversion/validation/rounding đều biểu diễn được bằng node,
   không cần loop vì bản chất đều là biến đổi dữ liệu, không phải thuật
   toán lặp) — ngoại trừ "customer hierarchy walk", xử lý bằng 1 node
   chuyên biệt có giới hạn độ sâu cố định (không phải loop chung), tái
   dùng `relationship_instance` đã có sẵn (ADR-003), không cần khái niệm
   core mới. Chọn — đã verify bằng 2 POC độc lập trong phiên làm việc
   (DAG thuần + DAG có Measure tái dùng, xem file POC đã chạy thật, không
   nằm trong repo chính vì là throwaway spike).

## Quyết định

### 1. Entity taxonomy — amend ADR-001

- **`Product`, `Customer`**: 2 entity **cố định, độc lập, luôn tồn tại**
  trong mọi deployment — khác với mọi entity khác (vẫn hoàn toàn pack-
  configurable). Ngang hàng với "price list"/"quote" đã là khái niệm core
  chính thức theo CLAUDE.md ("Lõi chỉ biết: entity, attribute,
  relationship, constraint, rule, price list, quote") — core biết **có
  đúng 2 anchor này**, nhưng không hardcode nghĩa business của chúng (core
  không biết "Product" là phụ tùng ô tô hay license SaaS).
- **`ProductExtension`, `CustomerExtension`**: **2 category**, không phải
  2 entity cụ thể — mỗi category chứa N entity con pack tự định nghĩa (vd
  `ProductCost`, `ProductPriceByChannel`). Business key của entity con =
  key của anchor (sku/customerId) + chiều phụ do entity con tự khai báo
  (vd `validFrom`, `city`).
- **`Parameter`**: entity độc lập khác (vd `Vehicle`), không gắn anchor
  nào, tự khai báo key riêng (user tự define business key, không bị ép
  theo quy ước sku/customerId).

### 2. Execution engine — Dataflow Graph (DAG), supersede phần "pipeline
cố định" của ADR-004

- Node = data thuần: `id`, `type`, `config`, `inputs` (map tên input → id
  node khác, hoặc literal). Lưu dạng JSON/DB row, không phải Java class
  riêng cho từng loại tính toán cụ thể.
- Engine (generic, cố định, không đổi khi graph đổi): topological sort
  theo input reference → thực thi tuần tự → sinh **Calculation Trace**
  đầy đủ (input + output mọi node) — đây là "price waterfall" tổng quát
  hoá của ADR-004, không còn giới hạn 6 stage cố định.
- **Không loop, không biến mutable** — giữ deterministic tuyệt đối (yêu
  cầu CLAUDE.md mục 7) và audit được từng bước; tránh tự xây ngôn ngữ lập
  trình đầy đủ (rủi ro đã loại ở lựa chọn 3).
- Node type ban đầu, tối thiểu đủ cho Price List — **mở rộng dần theo nhu
  cầu thật, không làm hết 1 lần**: `query` (đọc Product/Customer/
  ProductExtension/CustomerExtension/Parameter qua Calculation Binding,
  xem mục 3), `formula` (biểu thức số học, xem mục 4), `weighted-average`,
  `round`, `output`. Node phức tạp hơn (`join`, `hierarchy-walk` — dùng
  `relationship_instance` có sẵn, giới hạn độ sâu cố định, không phải
  loop chung —, `currency-conversion`, `agreement-resolution`) **thêm khi
  Promotion/Agreement hoặc Quote-time calculation cần thật**, không thiết
  kế trước khi có nhu cầu cụ thể.
- **Measure**: subgraph có tên (`measure_definition: code, name,
  graph, result_node_id`), định nghĩa 1 lần, tham chiếu lại nhiều nơi qua
  node type `measure-ref`. Khi chạy, trace của measure được transclude đầy
  đủ vào trace của nơi gọi — **không bao giờ là hộp đen**, dù tái dùng ở
  nhiều Price List/Promotion/Quote khác nhau. Measure phải tham số hoá
  theo **context hiện tại** (anchor instance đang tính, tương đương
  "filter context" của DAX) — không hardcode 1 instance cụ thể trong định
  nghĩa measure.

### 3. Calculation Binding — cách node `query` lấy dữ liệu từ Extension/
Parameter, và cách xử lý nhiều/không dòng khớp

Pipeline tường minh, user tự cấu hình qua UI (có preview/test khi đang
build): **Filter (0..N điều kiện) → Sort (0..N field, có thứ tự) → Take
First (boolean, mặc định `false`)**.

- `ProductExtension`/`CustomerExtension`: tự động khớp theo key của anchor
  đang tính (sku/customerId) — không cần filter tay phần này.
- 0 dòng sau filter → giá trị = `null` (formula/node sau tự xử lý, không
  có policy "missing" riêng).
- ≥2 dòng sau filter+sort:
  - `take_first=true` (user tường minh bật) → lấy dòng đầu sau sort.
  - `take_first=false` (mặc định) → **throw lỗi rõ ràng, chặn tính**.
    **Không có fallback ngầm tự chọn thay user** — nhất quán với D-005
    (catalog import: sai tên cột báo lỗi, không tự đoán mapping). Kiểm tra
    này chạy **cả lúc runtime thật**, không chỉ lúc preview config — dữ
    liệu nguồn đổi sau khiến 1 binding từng đúng ra ambiguous phải báo lỗi
    ngay lúc tính giá đó, không lặng lẽ tính sai.

### 4. Node `formula` — mở rộng `JsonLogicEvaluator` đã build (ADR-002),
không cần Groovy cho business logic thông thường

`JsonLogicEvaluator` (core-pricing/dsl, hiện chỉ dùng cho **điều kiện**,
trả boolean) được dùng thêm để tính **giá trị số** (JsonLogic đã hỗ trợ sẵn
`+ - * /`, không phải công nghệ mới). Business user không viết JSON thô —
UI thực tế là 1 "formula builder" (chọn field, chọn phép toán, chọn hằng
số/field khác), tự sinh JSON phía sau. **Groovy (D-014/D-023, sandbox đã
build) không bị loại bỏ nhưng thu hẹp vai trò**: chỉ còn là lối thoát hiếm
cho Pricing Admin kỹ thuật khi logic vượt quá khả năng biểu đạt của
formula/node có sẵn — không phải con đường chính, và **Price List cost-
plus-margin không cần Groovy** với cách này.

### 5. Price List module — amend ADR-003 phần "Price list"

**`price_list` (header)**:
```
id, code, name, currency
status                  -- DRAFT -> PENDING_APPROVAL -> APPROVED -> ACTIVE -> EXPIRED (nhánh REJECTED)
valid_from, valid_to
calculation_rule_set_id -- FK tới 1 DAG (graph của node)
version                 -- approve xong khoá, sửa tiếp tạo version mới (giống quote_version)
created_by, submitted_at, approved_by, approved_at
```

**`price_list_item` (line)**:
```
id, price_list_id, entity_instance_id   -- Product instance (sku)
result_price           -- tính qua DAG, không ai sửa tay được
manual_override_price   -- nullable — field DUY NHẤT cho phép sửa tay;
                         -- mọi field khác (bao gồm result_price, waterfall_steps) cố định
waterfall_steps JSONB   -- gộp trực tiếp vào đây, KHÔNG tách bảng con riêng
                         -- (price_list_item_waterfall_step bị bỏ) — mất khả
                         -- năng query SQL xuyên nhiều item theo từng step
                         -- riêng lẻ, đổi lại đơn giản hơn; use case đó hiếm
valid_from, valid_to
```

**Sau `APPROVED`: snapshot vĩnh viễn, không tự tính lại** dù dữ liệu nguồn
(binding) đổi sau — đúng nguyên tắc audit/replay đã chốt ở ADR-004 (snapshot
`rule_set_version_id`/`price_list_version_id` trên `quote_version`), áp
dụng tương tự cho `price_list` ở version của chính nó.

## Hệ quả

- **Tác động tới**: ADR-001 (thêm entity taxonomy mục 1), ADR-002 (thêm
  action `formula` dùng `JsonLogicEvaluator` cho giá trị, không chỉ điều
  kiện), ADR-003 (schema `price_list`/`price_list_item` đổi theo mục 5),
  ADR-004 (**phần "pipeline stage cố định" bị supersede bởi DAG** — các
  nguyên tắc còn lại của ADR-004: BigDecimal scale 6→2, snapshot/replay,
  round-trip delta=final vẫn giữ nguyên, áp dụng lại cho DAG trace).
- **Rework đáng kể lên code Sprint 2 đã merge**: `PricingStage` enum,
  `RuleEngine` tuyến tính 6-stage, bảng `price_list`/`price_list_item`
  hiện tại (S2-T10/S2-T12) sẽ cần viết lại/mở rộng lớn khi implement DAG
  engine thật. Tái dùng được: `JsonLogicEvaluator` (core-pricing/dsl),
  `BigDecimal` discipline, Groovy sandbox (`GroovyRuleCompiler`/
  `ForbiddenConstructCustomizer`/`GroovyRuleExecutor`, vai trò thu hẹp
  theo mục 4).
- **Chưa thiết kế, để sau khi Price List validate xong kiến trúc**:
  module Promotion/Agreement (chỉ có Price List ở ADR này), node type
  phức tạp (`join`/`hierarchy-walk`/`currency-conversion`/`agreement-
  resolution`), cách Quote-time calculation tổng hợp nhiều "Pricing
  Element Type" (Price List + Promotion/Agreement) cùng lúc.
- **NEEDS-PO-REVIEW**: thứ tự migrate — API `/api/pricing/calculate` hiện
  tại (S2-T10/S2-T12, dùng `RuleEngine` tuyến tính) có tiếp tục chạy song
  song trong lúc Price List module mới (DAG) được xây, hay dừng/thay ngay?
  ADR này **không quyết** việc này — cần 1 quyết định sequencing riêng khi
  bắt đầu implement, PO xác nhận trước khi backend-dev bắt tay.
- **Rủi ro đã biết**: DAG + Measure + Binding là kiến trúc phức tạp hơn
  đáng kể so với `RuleEngine` tuyến tính Sprint 2 — khối lượng implement
  lớn hơn 1 task/sprint thông thường, nên làm POC/spike kỹ (đã làm 1 phần
  trong phiên thảo luận) trước khi viết plan triển khai đầy đủ.
- **Ảnh hưởng ranh giới core/pack**: Không thay đổi nguyên tắc — DAG
  engine + node type là generic tuyệt đối (không biết "cost"/"SKU"/
  "margin" nghĩa là gì, chỉ biết "node type X nhận input gì, trả output
  gì"). `Product`/`Customer` là 2 khái niệm core chính thức **mới** (ngang
  "price list"/"quote"), nhưng core không hardcode nghĩa business của
  chúng — pack tự định nghĩa attribute cụ thể (sku là gì, customerId là
  gì) như mọi entity khác.
