# Open Questions — Sổ câu hỏi mở cho PO

Mỗi câu hỏi: id, trạng thái (`Open` / `Answered` / `Blocked-on-this`), người
hỏi, ngày hỏi, phương án đề xuất (nếu agent đã tự chọn default để không
đứng chờ), hạn cần quyết, việc bị chặn nếu không trả lời. Khi PO trả lời,
cập nhật trạng thái → `Answered`, ghi câu trả lời, và nếu default đã dùng
khác câu trả lời thật → ghi task cần sửa lại.

---

### Q-001 — Nguồn dữ liệu fitment (ACES/PIES hay tự nhập)?
- **Trạng thái**: Open
- **Người hỏi**: orchestrator (dựng skeleton)
- **Ngày hỏi**: 2026-11-12
- **Phương án đề xuất**: Sprint 1-2 dùng dữ liệu fitment nhập tay/CSV tối
  giản, thiết kế schema để map ACES/PIES sau (không import chuẩn ACES/PIES
  thật trong MVP).
- **Hạn cần quyết**: Trước S1-T7 (data model) chốt chi tiết field — nếu
  chưa trả lời, backend-dev dùng phương án đề xuất.
- **Bị chặn nếu không trả lời**: Không chặn Sprint 1 (dùng default); có thể
  cần sửa schema nếu câu trả lời khác default đáng kể.

### Q-002 — Quy mô catalog dự kiến (số part, số fitment)?
- **Trạng thái**: Open
- **Người hỏi**: orchestrator (dựng skeleton)
- **Ngày hỏi**: 2026-11-12
- **Phương án đề xuất**: Giả định vài nghìn part, vài chục nghìn fitment
  combination cho MVP demo; không tối ưu cho triệu-dòng ở MVP.
- **Hạn cần quyết**: Trước Sprint 4 (test hiệu năng) — ảnh hưởng mức độ cần
  tối ưu index/pagination.
- **Bị chặn nếu không trả lời**: Không chặn Sprint 1-3; Sprint 4 test hiệu
  năng sẽ dùng giả định này nếu chưa có câu trả lời.

### Q-003 — Đã có design partner / khách hàng đầu tiên chưa?
- **Trạng thái**: Open
- **Người hỏi**: orchestrator (dựng skeleton)
- **Ngày hỏi**: 2026-11-12
- **Phương án đề xuất**: Không có — xây theo backlog tổng quát, dùng dữ
  liệu demo tự tạo.
- **Hạn cần quyết**: Trước Sprint 3 (ảnh hưởng độ ưu tiên tính năng theo
  nhu cầu thật của 1 khách cụ thể, nếu có).
- **Bị chặn nếu không trả lời**: Không chặn tiến độ kỹ thuật; có thể lệch
  ưu tiên tính năng nếu có khách thật với yêu cầu riêng.

### Q-004 — Domain pack thứ hai ở Sprint 4: thiết bị công nghiệp hay SaaS?
- **Trạng thái**: Open
- **Người hỏi**: orchestrator (dựng skeleton)
- **Ngày hỏi**: 2026-11-12
- **Phương án đề xuất**: SaaS (ít thuộc tính vật lý hơn thiết bị công
  nghiệp, nên là bài test "đơn giản hơn" tốt hơn cho mục tiêu validate kiến
  trúc). Architect xác nhận lại đầu Sprint 4.
- **Hạn cần quyết**: Trước khi bắt đầu Sprint 4.
- **Bị chặn nếu không trả lời**: Chặn task E7-01 — cần biết domain để viết
  entity/constraint cụ thể.

### Q-005 — Cấu hình scheduled task báo cáo sáng (giờ, nội dung)?
- **Trạng thái**: Open
- **Người hỏi**: orchestrator (dựng skeleton)
- **Ngày hỏi**: 2026-11-12
- **Phương án đề xuất**: Xem đề xuất trong phần tổng kết của orchestrator
  (giờ + nội dung) — chưa tự tạo scheduled task, chờ PO duyệt.
- **Hạn cần quyết**: Trước khi scheduled task được tạo thật.
- **Bị chặn nếu không trả lời**: Chặn việc tạo scheduled task; không chặn
  việc viết `docs/daily-report-prompt.md` (đã có nội dung đề xuất sẵn).
