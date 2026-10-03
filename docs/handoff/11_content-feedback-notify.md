# 11: Ghi chú, giáo án, feedback, thông báo, blog (UC-33, 34, 40, 42→45, 06)

## Ghi chú riêng (UC-33, BR-58, 59, CBR-92, DBR-61)
Chỉ PT viết mới thấy, kể cả khi khách đổi PT. 1 ghi chú đang dùng / (PT, khách). Xóa = `DeletedAt`, sau đó viết được bản mới. Chặn PT ghi chú cho chính mình (CBR-115).

## Giáo án / thực đơn (UC-34, UC-40, BR-60, 61, DBR-62)
- `Title` / `Content` là bản **nháp**.
- **Xuất bản** = chép sang `PublishedTitle` / `PublishedContent` / `PublishedAt`.
- Khách (#60 My Plan) chỉ đọc bản `Published*`.
- Xóa → `DeletedAt`, khách mất ngay.
- Nội dung ≤ 10 000 ký tự.

## Feedback (UC-42, 43, CBR-106, 107)
Khách gửi 10–1000 ký tự. Chỉ Manager đọc; đánh dấu đã đọc ghi `ReadAt` + `ReadByEmployeeId`.

## Thông báo (UC-44, CBR-46, GBR-15, 34)
- `Audience`: `AllCustomers` / `ActiveMembers` / `PTs` / `Receptionists` / `AllStaff`; hệ thống gửi cá nhân dùng `Individual`.
- Hẹn giờ: `ScheduledAt`, `SentAt = NULL` → job gửi khi tới giờ: tạo `NotificationRecipients` cho TK **Active** thuộc nhóm lúc gửi, rồi set `SentAt`.
- Chỉ người có tài khoản nhận.

## Blog (UC-45, 06, CBR-100, DBR-15)
`Active` hiện ngay, `Inactive` gỡ ngay. Không ảnh → ảnh mặc định theo `Category` (`Training` / `Nutrition` / `News`). Không tóm tắt → lấy đầu nội dung.

## Case test
- Giáo án của Mai có nháp mới hơn bản xuất bản.
- Ghi chú đã xóa rồi viết lại.
- 1 thông báo hẹn giờ 10/10.
- 14 bài blog Active.
