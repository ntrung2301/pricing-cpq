# ADR-002: Engine rule/constraint

- **Trạng thái**: Accepted
- **Ngày**: 2026-11-12
- **Người quyết**: architect

## Ngữ cảnh

CLAUDE.md chưa chốt engine rule/constraint (để lại cho architect quyết bằng
ADR). Yêu cầu cụ thể của dự án: (1) pricing phải **deterministic** — cùng
input luôn ra cùng output, không phụ thuộc thứ tự nạp rule không xác định;
(2) **kiểm toán được** — mỗi bước áp rule phải giải thích được trong price
waterfall (ADR-004), không chỉ ra kết quả cuối; (3) **non-engineer sửa được
dần** — Pricing Admin (E5-01) cần sửa rule qua UI form, không sửa code hay
file DRL/script tay; (4) **đơn giản vận hành** — team Java/Spring nhỏ, không
muốn thêm 1 server/runtime riêng chỉ để chạy rule.

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

Chọn **JSON DSL tự viết**, không dùng Drools/CEL làm ngôn ngữ rule chính.
Cấu trúc 1 rule: `{id, stage, priority, conditions: [...], actions: [...]}`
trong đó `conditions` dùng lại ngữ pháp operator kiểu JsonLogic (`==, !=,
>, >=, <, <=, in, and, or, not`) — triển khai nội bộ bằng 1 evaluator nhỏ,
không add dependency `json-logic-java`, để tránh rủi ro maintain lib bên
thứ ba non-official cho 1 phần nhỏ (tận dụng ý tưởng/ngữ pháp, không tận
dụng code). `actions` là tập hữu hạn generic do core định nghĩa (ví dụ
`set-amount`, `apply-percent-discount`, `add-fixed-adjustment`,
`flag-for-approval`) — domain pack không thêm action type mới trong core,
chỉ dùng action có sẵn với tham số khác nhau; nếu cần phép tính đặc thù
domain, dùng SPI pricing function (E6-01), không mở rộng DSL action.
`RuleEngine` (core) chạy **tuần tự, không forward-chaining**: lọc rule áp
dụng được (match entity scope + conditions đúng), sort theo
`(stage order cố định, priority, rule.id)` để đảm bảo tie-break xác định,
rồi áp lần lượt — mỗi lần áp sinh 1 waterfall step (ADR-004).

## Hệ quả

- Tác động tới: E3-01, E3-02, E3-03, E3-04, E5-01, ADR-004.
- Rủi ro đã biết: tự viết engine nghĩa là tự chịu trách nhiệm test kỹ
  (TDD bắt buộc) cho evaluator điều kiện và action executor; nếu sau này
  cần rule phức tạp hơn (nhiều rule phụ thuộc chéo nhau) có thể phải xét
  lại — ghi `NEEDS-PO-REVIEW` nếu phát sinh ở Sprint 3/4 khi viết rule thật
  cho E3-04 (tier automotive) mà DSL không đủ biểu đạt.
- Ảnh hưởng ranh giới core/pack: **Không với core** — `RuleEngine`, cú pháp
  DSL, danh sách action type generic đều ở core. Domain pack (automotive,
  hoặc pack tổng hợp D-004) chỉ cung cấp **dữ liệu rule** (`rule_sets` khai
  báo trong `pack.yaml`, ADR-001) dùng action/condition generic có sẵn;
  pack không cần thêm action/operator mới cho các rule mô tả trong backlog
  (E3-04 tier, volume break, contract override) — nếu phát hiện cần, đó là
  tín hiệu phải báo PO theo CLAUDE.md, không tự thêm âm thầm vào core.
