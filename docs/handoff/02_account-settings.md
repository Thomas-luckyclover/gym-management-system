# 02: Cài đặt tài khoản (UC-04), mọi vai trò

**Trạng thái:** 🟢 chạy được. Đã sửa: giá trị chờ đổi lưu ở session, kiểm trùng lần 2 khi lưu, mã `PhoneChange` riêng.

**Việc còn lại:**
1. Ghi audit kèm `ActingRole` = `SessionKey.ROLE` (CBR-112). Sửa `AuditLogDAO.insert` + `AuditLogService.log` để thêm tham số.
2. Tab hồ sơ phụ thuộc chỉ hiện khi vai trò đang dùng là `Customer` (CBR-111). Kiểm ở **Servlet**, không chỉ ẩn ở JSP.
3. Vai trò nhân viên: chỉ sửa avatar / email / SĐT / mật khẩu. Họ tên, ngày sinh, vị trí… chỉ xem (CBR-108, CBR-109).
4. Rút comment dài trong `AccountSettingsServlet`, `AccountSettingsService`, `ContactChangeService` về dạng khung.

## Bảng / cột
`Persons` (FullName, DateOfBirth, Phone, Email, Avatar) · `Accounts` (PasswordHash) · `VerificationCodes` (Purpose `EmailChange`, `PhoneChange`) · `AuditLogs` (`ActingRole`)

## Luồng đổi email / SĐT (CBR-110)
1. B1: kiểm định dạng (DBR-17, DBR-18)
2. B2: kiểm trùng với Person còn hoạt động (DBR-22, DBR-23)
3. B3: gửi mã: email mới → tới email mới; SĐT mới → tới email **hiện tại** (CBR-94 giới hạn gửi)
4. B4: lưu `PENDING_CONTACT_FIELD/VALUE/VERIFY_EMAIL` vào session (GBR-13)
5. B5: nhập mã → xác minh đúng `Purpose`
6. B6: kiểm trùng **lần 2**
7. B7: lưu lên Person (áp cho mọi vai trò)
8. B8: email: báo email cũ + đăng xuất phiên khác (CBR-29); ghi audit (CBR-112)

## Luồng đổi mật khẩu (DBR-02, CBR-93)
Đúng MK cũ → MK mới hợp lệ và khác MK cũ → lưu hash → đăng xuất mọi phiên **khác** → audit (không ghi MK).

## Luồng avatar (DBR-64)
JPG/PNG ≤ 2 MB, 200–2000 px → cắt giữa → 256×256 → `Persons.Avatar` (dùng chung mọi vai trò).

## Case test
- An: mã đổi email `135790`.
- Sơn: mã đổi SĐT `864200`.
- Trang: tab hồ sơ phụ thuộc theo vai trò.
- Thử sửa hidden input → vẫn lưu đúng giá trị đã gửi mã.
