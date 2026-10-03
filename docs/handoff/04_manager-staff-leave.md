# 04: Hồ sơ nhân viên + nghỉ phép (UC-13, UC-14, UC-15)

**Màn:** #23 Staff List · #24 Staff Details · #25 Leave Request Form (`staff`) · #26 / #27 Leave Request List / Approval.

## Hồ sơ nhân viên (UC-13)
Manager sửa: họ tên, ngày sinh, chuyên môn, kinh nghiệm, ngày vào làm, hoa hồng, tình trạng làm việc (CBR-108). **Không** sửa vị trí (CBR-109).
Họ tên / ngày sinh nằm trên Person → đổi là đổi cho mọi vai trò, ghi audit (CBR-108, CBR-112).

### Luồng cho nghỉ việc (CBR-86, BR-07, BR-13)
1. B1: chặn nếu là Admin đang hoạt động cuối cùng (BR-07)
2. B2: `Employees.Status = Resigned`, `DeletedAt`, `ResignationReason` ≥ 5 ký tự
3. B3: còn hồ sơ khách chưa Inactive? Còn → TK giữ `Active`. Hết → `Accounts.Status = Inactive` + `Persons.IsReleased = 1` (DBR-28)
4. B4: `SessionRegistryListener.invalidateSessionsOfAccount(accountId, null)`
5. B5: audit

## Nộp đơn nghỉ (UC-14)
1. B1: hạn nộp trước P-12 của ngày hôm trước (CBR-84)
2. B2: PT còn buổi `Scheduled` trong ca nghỉ → chặn, liệt kê buổi kèm link màn xử lý buổi (CBR-83)
3. B3: không trùng đơn chưa bị từ chối cùng ngày + ca (unique trong DB)

## Duyệt đơn (UC-15)
1. B1: duyệt trước P-20 ngày hôm trước (CBR-85)
2. B2: số người còn lại của ca (DBR-60) không dưới P-13 (PT) / P-14 (Lễ tân) (BR-14, CBR-88, CBR-90)
3. B3: từ chối bắt buộc lý do (GBR-09)
4. B4: audit

Job: nhắc Manager 1 lần (`ReminderSentAt`), quá hạn thì tự từ chối với `ReviewedByEmployeeId = NULL` + thông báo cho nhân viên (CBR-85).
⚠️ Rule nghỉ phép của Manager vẫn **chờ nhóm chốt**.

## Case test
- Đơn Pending của Trang (10/10) và Long (12/10).
- Đơn bị từ chối vì định biên của David.
- Đơn bị từ chối tự động của Lâm.
- Thử cho Admin `dang.nguyen` nghỉ → bị chặn.
