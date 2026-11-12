# Process — Quy trình Sprint

## Lịch sprint (1 tuần)

- **Thứ Hai — Planning**: product-manager + architect rà `docs/backlog.md`,
  chốt `docs/sprints/sprint-N.md` (task độc lập, có người phụ trách +
  phụ thuộc). Mọi task phải đạt Definition of Ready trước khi giao.
- **Thứ Ba–Thứ Năm — Build**: agent nhận task, làm theo TDD, PR nhỏ,
  code-reviewer review liên tục (không chờ cuối sprint mới review).
- **Thứ Sáu — Demo / Review / Retro**:
  - **Demo**: chạy thật tiêu chí demo của sprint (xem `docs/roadmap.md`),
    không demo bằng slide/mock.
  - **Review**: PO duyệt kết quả so với Definition of Done của sprint; mở
    `docs/questions.md` chưa trả lời, PO trả lời hoặc dời hạn.
  - **Retro**: ghi 1-2 điều cải thiện cho process (vào `docs/process.md`
    nếu thay đổi quy tắc, hoặc `docs/decisions.md` nếu là quyết định kỹ
    thuật).

## Definition of Ready (task vào sprint)

Một task được coi là "ready" khi:
- Có mô tả rõ, acceptance criteria cụ thể (không mơ hồ).
- Phụ thuộc (nếu có) đã được xác định rõ trong `docs/sprints/sprint-N.md`.
- Không vi phạm ranh giới core/pack theo thiết kế đã biết (nếu task mới lộ
  ra vi phạm, quay lại architect trước khi code).
- Có agent phù hợp nhận (đúng vai trò trong `.claude/agents/`).

## Definition of Done (task)

- Test thật pass (TDD), verify bằng lệnh chạy thật
  (`superpowers:verification-before-completion`), không suy đoán.
- Code review xong (ít nhất 1 lượt code-reviewer), finding mức
  Cần-sửa-trước-khi-merge đã xử lý.
- Không vi phạm ranh giới core/pack (ArchUnit pass nếu áp dụng).
- Quyết định không hiển nhiên đã ghi vào `docs/decisions.md`.
- PR mở, chưa tự merge vào `main`.

## Definition of Done (sprint)

- Mọi deliverable trong `docs/roadmap.md` cho sprint đó đạt tiêu chí demo.
- `docs/standup/` có báo cáo đầy đủ các ngày trong sprint.
- Open Questions ảnh hưởng sprint tiếp theo đã được PO trả lời hoặc có
  default rõ ràng ghi trong `docs/decisions.md`.

## Agent không đứng chờ

Agent **không dừng công việc** chỉ vì thiếu câu trả lời PO, trừ khi việc
tiếp theo *thực sự không thể làm* nếu thiếu câu trả lời đó. Quy trình:

1. Có lựa chọn mặc định hợp lý? → Chọn, ghi vào `docs/decisions.md` với cờ
   `NEEDS-PO-REVIEW`, tiếp tục làm.
2. Không có lựa chọn hợp lý nào (mọi hướng đều rủi ro cao hoặc tốn công sửa
   lại lớn)? → Ghi câu hỏi vào `docs/questions.md` (kèm phương án đề xuất
   nếu có), báo rõ trong standup hôm đó, **chuyển sang task khác** không
   phụ thuộc câu hỏi này trong lúc chờ.
3. PO trả lời (ở review cuối sprint hoặc bất kỳ lúc nào) → cập nhật trạng
   thái câu hỏi, và nếu default đã dùng khác câu trả lời thật → tạo task
   sửa lại, ghi rõ trong sprint hiện tại hoặc tiếp theo.

## Chạy song song bằng git worktrees

Khi sprint có ≥2 task độc lập (không phụ thuộc nhau theo
`docs/sprints/sprint-N.md`), dùng `superpowers:using-git-worktrees` để mỗi
agent/subagent làm trên worktree riêng, tránh xung đột file, merge vào
nhánh tích hợp của sprint trước khi mở PR vào `main`. Task có phụ thuộc rõ
(vd S1-T6 phụ thuộc S1-T3) không chạy song song với task nó phụ thuộc.
