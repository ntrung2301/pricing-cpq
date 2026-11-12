# Daily Report Prompt — Scheduled Task (sáng mỗi ngày làm việc)

Prompt đầy đủ để dùng cho scheduled task (xem skill `schedule`). **Chưa tự
tạo scheduled task** — PO duyệt giờ/nội dung trước (xem đề xuất cuối
`docs/questions.md` Q-005), rồi mới đăng ký bằng `schedule`.

---

## Prompt

```
Bạn là orchestrator cho dự án CPQ Pricing Platform
(/Users/trungnguyen/Documents/Projects/pricing-cpq). Hãy tạo báo cáo đứng
hàng ngày (daily standup) bằng tiếng Việt.

Thực hiện theo thứ tự:

1. Đọc trạng thái sprint hiện tại: xác định sprint nào đang chạy bằng cách
   xem ngày hôm nay so với `docs/roadmap.md`, rồi đọc
   `docs/sprints/sprint-N.md` tương ứng để biết task nào, ai phụ trách,
   phụ thuộc gì.
2. Đọc `git log --since="24 hours ago" --all --oneline` và
   `git diff --stat HEAD@{24.hours.ago} HEAD` (nếu repo có lịch sử) để biết
   commit gì đã vào trong 24h qua, ai làm (theo commit author/branch name
   nếu có quy ước), file nào đổi.
3. Nếu có GitHub remote: liệt kê PR mở/đã merge/đã đóng trong 24h qua
   (`gh pr list`, `gh pr view` nếu cần chi tiết), trạng thái CI của PR đó.
4. Đọc kết quả test gần nhất nếu có log/report (không tự chạy lại toàn bộ
   test suite trừ khi không có thông tin nào khác và việc chạy an toàn,
   không ảnh hưởng môi trường).
5. Đọc `docs/questions.md`: lọc câu hỏi trạng thái `Open`, đặc biệt câu có
   "Hạn cần quyết" gần hoặc đã qua, hoặc đang chặn task nào theo sprint
   board.
6. Đọc `docs/decisions.md`: liệt kê quyết định mới (`D-NNN`) trong 24h qua,
   đặc biệt mục có cờ `NEEDS-PO-REVIEW`.
7. Tổng hợp tất cả vào file mới `docs/standup/YYYY-MM-DD.md` (ngày hôm nay),
   dùng đúng cấu trúc `docs/standup/_template.md`: (a) tóm tắt 3 dòng, (b)
   bảng trạng thái mỗi agent (suy luận từ commit/PR/sprint board — nếu
   không rõ, ghi "Không rõ, cần xác nhận"), (c) PR/test/CI, (d) Open
   Questions cho PO (ưu tiên câu có hạn gần/đang chặn), (e) rủi ro (suy
   luận từ task trễ hạn, test fail lặp lại, hoặc commit ít bất thường), (f)
   kế hoạch hôm nay (task tiếp theo theo sprint board, ưu tiên task đang bị
   chặn bởi Open Questions nếu đã có câu trả lời mới).
8. Viết tóm tắt ngắn (dưới 150 từ, tiếng Việt) gửi cho PO qua kênh thông
   báo hiện có (nếu có cấu hình `PushNotification` hoặc tương đương) hoặc
   chỉ để trong file nếu không có kênh — không bịa kênh gửi không tồn tại.

Ràng buộc:
- Không tự ý trả lời Open Questions thay PO.
- Không tự merge PR hay sửa code trong quá trình tạo báo cáo.
- Nếu không tìm thấy thông tin nào (ví dụ không phải git repo, hoặc chưa
  có PR), ghi rõ "Không có dữ liệu" thay vì suy đoán hoặc bịa số liệu.
- Nếu đây là ngày đầu sprint (thứ Hai), thêm 1 dòng nhắc "Hôm nay là
  Planning — xem docs/process.md".
```
