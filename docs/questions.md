# Open Questions — Sổ câu hỏi mở cho PO

Mỗi câu hỏi: id, trạng thái (`Open` / `Answered` / `Blocked-on-this`), người
hỏi, ngày hỏi, phương án đề xuất (nếu agent đã tự chọn default để không
đứng chờ), hạn cần quyết, việc bị chặn nếu không trả lời. Khi PO trả lời,
cập nhật trạng thái → `Answered`, ghi câu trả lời, và nếu default đã dùng
khác câu trả lời thật → ghi task cần sửa lại.

---

### Q-001 — Nguồn dữ liệu fitment (ACES/PIES hay tự nhập)?
- **Trạng thái**: Answered (2026-11-12)
- **Người hỏi**: orchestrator (dựng skeleton)
- **Ngày hỏi**: 2026-11-12
- **Câu trả lời (PO)**: Nhập từ Excel/CSV; cột trong file phải **match
  đúng tên** với column/attribute đã định nghĩa trong bảng (không cần UI
  mapping linh hoạt kiểu kéo-thả cho MVP — tên cột sai thì báo lỗi, không
  tự đoán).
- **Tác động**: E1-04 (`docs/backlog.md`) cập nhật: import framework core
  chỉ cần match tên cột ↔ tên attribute của entity definition, không cần
  xây UI mapping cột tuỳ ý ở MVP. Field fitment vẫn tự nhập/CSV (không
  chuẩn ACES/PIES thật), nhưng schema vẫn thiết kế để map ACES/PIES sau.

### Q-002 — Quy mô catalog dự kiến (số part, số fitment)?
- **Trạng thái**: Answered (2026-11-12)
- **Người hỏi**: orchestrator (dựng skeleton)
- **Ngày hỏi**: 2026-11-12
- **Câu trả lời (PO)**: Vài nghìn part cho hiện tại (khớp với giả định mặc
  định đã đề xuất).
- **Tác động**: Không cần tối ưu cho quy mô triệu-dòng ở MVP/Sprint 4; giữ
  giả định "vài nghìn part, vài chục nghìn fitment combination" trong kế
  hoạch test hiệu năng Sprint 4.

### Q-003 — Đã có design partner / khách hàng đầu tiên chưa?
- **Trạng thái**: Answered (2026-11-12)
- **Người hỏi**: orchestrator (dựng skeleton)
- **Ngày hỏi**: 2026-11-12
- **Câu trả lời (PO)**: Chưa có.
- **Tác động**: Xây theo backlog tổng quát, dùng dữ liệu demo tự tạo; nhất
  quán với D-004 (domain pack thứ hai cũng chỉ là pack tổng hợp vì chưa có
  khách thật để cấu hình theo). Hỏi lại trước Sprint 3 nếu tình hình đổi
  (có khách/design partner xuất hiện) — khi đó cần ưu tiên lại backlog
  theo yêu cầu thật của khách.

### Q-004 — Domain pack thứ hai ở Sprint 4: thiết bị công nghiệp hay SaaS?
- **Trạng thái**: Answered (2026-11-12)
- **Người hỏi**: orchestrator (dựng skeleton)
- **Ngày hỏi**: 2026-11-12
- **Câu trả lời (PO)**: Không chốt ngành cụ thể bây giờ — domain pack thật
  thứ hai sẽ được cấu hình **khi có khách hàng thật**. Yêu cầu là schema/
  metamodel phải được xây **generic nhất có thể** ngay từ đầu, không thiết
  kế ngầm theo một ngành giả định.
- **Tác động**: Đổi cách làm E7-01 (`docs/backlog.md`) và Sprint 4
  (`docs/roadmap.md`) — Sprint 4 không build một domain pack ngành thật
  (SaaS/thiết bị công nghiệp), mà dùng **1 domain pack tổng hợp/tối giản
  (synthetic)** chỉ để kiểm chứng "cấu hình đủ, không cần sửa lõi". Domain
  pack thật thứ hai sẽ làm sau, khi có khách hàng cụ thể yêu cầu. Xem
  D-004 trong `docs/decisions.md`.

### Q-005 — Cấu hình scheduled task báo cáo sáng (giờ, nội dung)?
- **Trạng thái**: Answered (2026-11-12)
- **Người hỏi**: orchestrator (dựng skeleton)
- **Ngày hỏi**: 2026-11-12
- **Câu trả lời (PO)**: Đồng ý với đề xuất — 8:00 sáng Thứ Hai–Thứ Sáu
  (giờ VN), nội dung theo `docs/daily-report-prompt.md`.
- **Tác động**: Đăng ký scheduled task thật qua skill `schedule`.
