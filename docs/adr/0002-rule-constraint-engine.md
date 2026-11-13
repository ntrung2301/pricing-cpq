# ADR-002: Engine rule/constraint

- **Trạng thái**: Accepted (revised 2026-11-12 theo PO override, xem D-014)
- **Ngày**: 2026-11-12
- **Người quyết**: architect (quyết định rule engine chính do PO chỉ đạo trực
  tiếp — xem D-014 trong `docs/decisions.md`)

## Ngữ cảnh

CLAUDE.md chưa chốt engine rule/constraint (để lại cho architect quyết bằng
ADR). Yêu cầu cụ thể của dự án: (1) pricing phải **deterministic** — cùng
input luôn ra cùng output, không phụ thuộc thứ tự nạp rule không xác định;
(2) **kiểm toán được** — mỗi bước áp rule phải giải thích được trong price
waterfall (ADR-004), không chỉ ra kết quả cuối; (3) **non-engineer sửa được
dần** — Pricing Admin (E5-01) cần sửa rule qua UI form, không sửa code hay
file DRL/script tay; (4) **đơn giản vận hành** — team Java/Spring nhỏ, không
muốn thêm 1 server/runtime riêng chỉ để chạy rule.

**Cập nhật theo PO (D-014)**: bản ADR gốc chỉ chọn JSON DSL tự viết, loại
Groovy/code-based rule. PO yêu cầu rõ: cần **cả hai** — Groovy cho rule do
dev/Pricing Admin kỹ thuật viết trực tiếp (code rule, mạnh/linh hoạt hơn),
và JSON DSL cho rule do **visual rule builder** (UI kéo-thả cho non-engineer,
E5-01) sinh ra. Phần dưới đây viết lại Quyết định theo hướng hybrid; phần
"Lựa chọn đã xét" gốc (loại Drools/CEL/JsonLogic làm ngôn ngữ rule core) vẫn
giữ nguyên giá trị — PO không yêu cầu đổi phần đó.

## Lựa chọn đã xét

1. **Drools (RETE)** — mạnh cho forward-chaining/inference phức tạp nhiều
   rule tương tác, nhưng: rule viết bằng DRL (text DSL riêng, khó validate
   bằng JSON Schema cho UI form builder), cần KieBase/KieSession (thêm
   runtime, thêm learning curve), rule chain qua RETE **khó đảm bảo thứ tự
   xác định tuyệt đối** khi nhiều rule cùng salience/agenda-group — đi
   ngược yêu cầu "deterministic, giải thích từng bước" vì forward-chaining
   không tự nhiên sinh ra 1 danh sách bước tuyến tính. Overkill: pricing ở
   đây là 1 pipeline tuần tự (list price → tier → volume → discount →
   override → guardrail), không cần suy luận đa chiều. Loại.
2. **CEL (Common Expression Language, cel-java)** — expression mạnh, nhanh,
   được Google dùng rộng, phù hợp cho *điều kiện* boolean/tính toán đơn.
   Nhưng CEL là **chuỗi biểu thức tự do** (ví dụ
   `qty > 100 && customer.tier == "dealer"`) — khó dựng UI form builder có
   validate tĩnh cho non-engineer (E5-01 yêu cầu "có validate, không cần
   sửa file tay"), và không có khái niệm "danh sách bước điều chỉnh giá"
   sẵn — vẫn phải tự xây tầng waterfall bên trên. Thêm 1 dependency ngoài
   ecosystem Spring quen thuộc. Loại làm ngôn ngữ rule chính.
3. **JsonLogic** — biểu diễn điều kiện bằng JSON (`{"and": [...]}`), rất
   hợp để non-engineer/UI form sinh ra (JSON tree dễ validate bằng schema,
   dễ render ngược lại thành form), cộng đồng Java không chính thức
   (`json-logic-java` không phải thư viện core Anthropic/Spring-first,
   phải tự maintain fork nếu cần mở rộng operator). Không có khái niệm
   "action"/"waterfall step" — chỉ giải quyết phần điều kiện, không giải
   quyết phần "làm gì khi điều kiện đúng" và tuần tự áp dụng.
4. **JSON DSL tự viết (custom), vay mượn ngữ pháp operator của JsonLogic
   cho phần condition** — tự kiểm soát hoàn toàn schema, không phụ thuộc
   lib ngoài còn non-mature, dễ validate bằng JSON Schema cho UI form (E5-
   01), dễ lưu/diff/version như dữ liệu thuần (đúng CLAUDE.md mục 7: "rule
   lưu dạng dữ liệu, không hard-code"), và tự nhiên sinh ra danh sách bước
   tuần tự cho waterfall vì DSL định nghĩa luôn thứ tự (`stage` +
   `priority`). Chi phí: phải tự viết engine (nhưng engine ở đây đơn giản —
   không forward-chaining, chỉ lặp tuần tự qua rule đã sort).

## Quyết định

Chọn mô hình **hybrid 2 loại rule cùng chạy qua 1 `RuleEngine`**, không chọn
độc quyền 1 ngôn ngữ:

1. **`rule_type: DSL`** — JSON DSL tự viết như bản gốc:
   `{id, stage, priority, conditions: [...], actions: [...]}`, `conditions`
   dùng ngữ pháp kiểu JsonLogic (`==, !=, >, >=, <, <=, in, and, or, not`),
   `actions` là tập hữu hạn generic do core định nghĩa (`set-amount`,
   `apply-percent-discount`, `add-fixed-adjustment`, `flag-for-approval`...).
   Đây là **target output của visual rule builder** (E5-01): non-engineer
   kéo-thả trên UI, UI sinh JSON DSL, validate bằng JSON Schema trước khi
   lưu. Phù hợp nhất cho rule đơn giản, dạng điều kiện → hành động cố định.
2. **`rule_type: SCRIPT`** — Groovy script, dành cho rule phức tạp hơn JSON
   DSL biểu đạt được (nhiều điều kiện lồng, tính toán nhiều bước, logic
   domain-specific mà không muốn mở action type mới trong core). Script
   nhận 1 context generic (`RuleContext`: entity instance, customer, số
   lượng, ngày hiệu lực — core định nghĩa, không biết nội dung domain) và
   phải trả về 1 danh sách waterfall step (amount + label + metadata), dùng
   đúng kiểu dữ liệu mà action DSL cũng sinh ra — core xử lý kết quả của cả
   2 loại rule giống nhau sau bước này. Chạy trong **sandbox bắt buộc**:
   `GroovyShell` + `SecureASTCustomizer` (cấm import ngoài whitelist, cấm
   gọi file/network/reflection, cấm khai báo class/closure vượt độ sâu cho
   phép), compile-time check qua `CompilerConfiguration`, có timeout thực
   thi (ví dụ 200ms) để 1 script lỗi vô hạn lặp không treo request tính giá.
   Script **không được** dùng nguồn non-deterministic (giờ hệ thống hiện
   tại, `Random` không seed, gọi HTTP) — review bắt buộc kiểm tra điều này
   (code-reviewer + có thể thêm static check đơn giản sau).
3. Cả 2 loại rule cùng nằm trong 1 `rule_set` (ADR-001/pack.yaml), cùng được
   `RuleEngine` lọc theo entity scope, sort theo
   `(stage order cố định, priority, rule.id)` — thứ tự xác định không phụ
   thuộc rule là DSL hay Script — rồi áp tuần tự, mỗi lần áp sinh 1 waterfall
   step (ADR-004). Việc chọn DSL hay Script cho 1 rule cụ thể là quyết định
   của Pricing Admin lúc tạo rule, không phải giới hạn kỹ thuật theo domain.
4. Nếu cần phép tính vượt cả 2 (ví dụ cần gọi service ngoài) → vẫn dùng SPI
   pricing function (E6-01), không mở rộng DSL action hay nới sandbox Groovy.

## Hệ quả

- Tác động tới: E3-01, E3-02, E3-03, E3-04, E5-01, ADR-004, CLAUDE.md mục 7
  (cập nhật yêu cầu sandbox/deterministic cho Script).
- Rủi ro đã biết: (1) tự viết DSL evaluator vẫn cần TDD chặt như bản gốc;
  (2) **thêm rủi ro mới với Groovy**: sandbox cấu hình sai = lỗ hổng chạy
  code tuỳ ý (nghiêm trọng) — cần test riêng cho chính sách sandbox (thử
  escape sandbox trong test) trước khi cho Pricing Admin dùng Script trong
  production; (3) Script không deterministic (vô tình dùng giờ hệ thống) là
  lỗi khó phát hiện tĩnh — cần test runtime (chạy 2 lần cùng input, so kết
  quả) như một phần Definition of Done của mọi rule Script; (4) Script lỗi
  biên dịch/throw exception lúc tính giá cần được bắt và biến thành lỗi rõ
  ràng (không để quote tính giá sai thầm lặng).
- Ảnh hưởng ranh giới core/pack: **Không với engine lõi** — `RuleEngine`,
  cú pháp DSL, `RuleContext` cho Script, danh sách action type generic đều ở
  engine lõi. Domain (automotive, hoặc domain tổng hợp D-004) chỉ cung cấp
  **dữ liệu rule** (`rule_sets`, gồm cả rule DSL và rule Script dạng text)
  trong config pack — không cần thêm action/operator mới trong engine cho
  các rule mô tả trong backlog (E3-04 tier, volume break, contract
  override); nếu phát hiện cần, đó là tín hiệu phải báo PO, không tự thêm
  âm thầm vào engine lõi.
