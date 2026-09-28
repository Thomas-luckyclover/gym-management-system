# 07 · Đồng bộ System Message giữa SRS III.5 và code

> Mục tiêu: **1 câu thông báo = 1 mã MSG**, có cả trong SRS III.5 lẫn `Messages.java`. Không còn câu viết cứng trong Servlet/Service.
> Trạng thái 27/09/2026, nhánh `dev_mb1_MinhLC`.

---

## 1. Kết quả đối chiếu (trước khi sửa)

| Chiều lệch | Có những gì | Đã xử lý |
|---|---|---|
| SRS có, code **chưa có** | MSG62–65 (số lần nhập / gửi lại mã còn lại) | ✅ Thêm vào `Messages.java` và hiển thị ở `/forgot-password` bước 2 |
| Code có, SRS **chưa có** | 7 hằng `EXTRA_*` + các câu viết cứng trong `AccountSettingsService`, `RegistrationService`, `RegisterServlet`, `BlogService`, `BlogDetailServlet` | ✅ Gom thành **MSG66–73** (mục 3); câu trùng nghĩa với MSG có sẵn thì dùng lại MSG đó (mục 2) |
| Cả hai có nhưng **khác định dạng** | MSG35: SRS "Thử lại sau `{mm:ss}`", code "Thử lại sau n phút" | ✅ Code hiện `04:32` (`AuthService.formatMinutesSeconds`) |

---

## 2. Câu viết cứng → dùng lại MSG đã có trong SRS

| Chỗ trong code | Câu cũ (viết cứng) | Giờ dùng |
|---|---|---|
| `AccountSettingsService` | "Vui lòng nhập mật khẩu hiện tại." | `MSG09_REQUIRED` + "Mật khẩu hiện tại" |
| `AccountSettingsService` | "Vui lòng nhập mật khẩu mới." / "…nhập lại mật khẩu mới." | `MSG09_REQUIRED` |
| `AccountSettingsService` | "Không tìm thấy tài khoản." | `MSG08_NOT_FOUND` + "tài khoản" |
| `AccountSettingsService` | "Mật khẩu mới phải khác mật khẩu hiện tại." | `MSG48_PASSWORD_SAME_AS_OLD` |
| `AccountSettingsService` | "Mật khẩu nhập lại không khớp." | `MSG47_PASSWORD_MISMATCH` |
| `AccountSettingsService` | câu lỗi hệ thống | `MSG04_SYSTEM_BUSY` |
| `AccountSettingsServlet` | "Đổi mật khẩu thành công." | `MSG49_PASSWORD_CHANGED` |
| `RegistrationService` (2 chỗ) | "Vui lòng nhập lại mật khẩu." | `MSG09_REQUIRED` + "Nhập lại mật khẩu" |
| `RegisterServlet` (2 chỗ) | "Đã gửi lại mã xác thực tới …" | `MSG45_CODE_RESENT` |

---

## 3. Các dòng cần THÊM vào bảng SRS III.5 (copy vào Drive)

**Đặt vào mục nào:** không tạo mục mới, chèn từng dòng vào mục có sẵn theo **nghĩa** của câu, giữ nguyên số MSG (code đang dùng đúng số này):
- **5.1 Hệ thống, danh sách, quyền truy cập:** MSG73 (bài viết không còn hiển thị, cùng loại với MSG08 "không tìm thấy").
- **5.2 Kiểm tra dữ liệu nhập:** MSG66, 67, 68, 69, 72 (sai định dạng theo DBR, cùng loại với MSG09/11/13).
- **5.4 Sign-in, password, personal account:** MSG70, 71 (cùng chỗ với MSG62–65).

Quy ước placeholder giống các dòng cũ của SRS: `{n}` số, `{min}`/`{max}` giới hạn, `{P-15}`, `{P-18}`… = lấy giá trị từ bảng System Parameters (P-15 = 15 phút hiệu lực mã, P-18 = 5 phút khóa).

| # | Mã | Mục SRS | Hiển thị | Ngữ cảnh (màn / khi nào) | Nội dung (VI) | Nội dung (EN) | Hằng trong code |
|---|---|---|---|---|---|---|---|
| 66 | MSG66 | **5.2** | Inline dưới ô Mật khẩu | Đăng ký, Kích hoạt, Quên MK bước 3, Đổi MK — mật khẩu sai DBR-02 | Mật khẩu phải dài {min}–{max} ký tự, gồm cả chữ và số. | Password must be {min}–{max} characters and contain both letters and digits. | `MSG66_PASSWORD_RULE` |
| 67 | MSG67 | **5.2** | Inline dưới ô Họ tên | Đăng ký — họ tên có ký tự lạ (DBR-01) | Họ tên chỉ gồm chữ cái, khoảng trắng, dấu nháy đơn hoặc gạch nối. | Full name may contain only letters, spaces, apostrophes or hyphens. | `MSG67_FULL_NAME_RULE` |
| 68 | MSG68 | **5.2** | Inline dưới ô Mã | Mọi màn nhập mã — không đúng 6 chữ số (DBR-03); **không** tính là 1 lần sai | Mã xác thực gồm đúng 6 chữ số. | The verification code must be exactly 6 digits. | `MSG68_CODE_FORMAT` |
| 69 | MSG69 | **5.2** | Inline dưới ô Ngày sinh | Đăng ký — tuổi vượt giới hạn (DBR-04) | Ngày sinh không hợp lệ (tuổi không quá {n}). | Invalid date of birth (age must not exceed {n}). | `MSG69_DOB_TOO_OLD` |
| 70 | MSG70 | **5.4** | Toast lỗi | Bấm Gửi lại mã khi chưa đủ 60 giây (CBR-94) | Vui lòng chờ {n} giây nữa rồi bấm Gửi lại mã. | Please wait {n} more seconds before resending the code. | `MSG70_CODE_RESEND_TOO_SOON` |
| 71 | MSG71 | **5.4** | Inline dưới ô Mật khẩu hiện tại | Đổi MK (UC-04) — mật khẩu hiện tại sai | Mật khẩu hiện tại không đúng. | The current password is incorrect. | `MSG71_CURRENT_PASSWORD_WRONG` |
| 72 | MSG72 | **5.2** | Inline dưới ô Họ tên | Đăng ký — độ dài họ tên sai (DBR-01) | Họ và tên phải từ {min} đến {max} ký tự. | Full name must be {min}–{max} characters. | `MSG72_FULL_NAME_LENGTH` |
| 73 | MSG73 | **5.1** | Toast cảnh báo, về danh sách Blog | Mở bài viết đã ẩn/xóa/không tồn tại (CBR-100) | Bài viết này không còn hiển thị. | This post is no longer available. | `MSG73_POST_UNAVAILABLE` |

---

## 4. Việc nhỏ cần sửa trong SRS

1. **MSG62–65** trên Drive đang ghi `%d` (cú pháp Java). Đổi thành placeholder của SRS cho thống nhất:
   - MSG62 "Bạn còn **{n}** lần nhập mã."
   - MSG63 "Nhập sai thêm 1 lần, xác thực cho email này sẽ bị khóa **{P-18}** phút."
   - MSG64 "Còn **{n}** lần gửi lại mã trong 60 phút."
   - MSG65 "Đã hết lượt gửi lại. Gửi thêm sẽ khóa xác thực **{P-18}** phút."
2. **MSG35** giữ đúng `{mm:ss}`, code đã theo.
3. **MSG38** đổi nội dung (bỏ luồng link 27/09): "Tài khoản này chưa có mật khẩu. Hãy dùng Quên mật khẩu để đặt mật khẩu." / EN "This account has no password yet. Use Forgot Password to set one."
4. Mục 1.3.2 (màn Quên mật khẩu, bước nhập mã): thêm 1 dòng "Dưới ô mã hiện MSG62/MSG63; dưới nút Gửi lại mã hiện MSG64/MSG65; khi email bị khóa chỉ hiện MSG61."

---

## 5. Cách code dùng message (để trả lời khi thầy hỏi)

- **Server:** mọi câu nằm trong `com.gms.constant.Messages`. Câu có số thì dùng `String.format(Messages.MSGxx, …)`. Số lấy từ `AppConstants` hoặc `SystemConfigService` (các tham số P-01…P-20), không gõ tay.
- **Client:** `js/common.js` có object `MESSAGES`. Đây chỉ là **tập con** các câu JS cần để báo lỗi tức thì (MSG09, MSG11, MSG13, MSG47, MSG66–69…). Tên khóa giống hệt bên Java, nên đổi câu thì sửa cả 2 nơi.
- **Kiểm tra nhanh còn câu viết cứng không:**
  `grep -rn 'fail("' src/main/java` và `grep -rn 'setFlashToast(request, "[a-z]*", "' src/main/java` → không được ra câu tiếng Việt nào.

## 6. Lưu ý cho cả nhóm

Các hằng `EXTRA_*` **đã đổi tên** thành `MSG66–70`. Nhánh nào còn dùng tên cũ sẽ lỗi biên dịch khi merge. Cách sửa: tìm `EXTRA_` rồi thay theo bảng dưới.

**27/09 — bỏ luồng link đặt mật khẩu, dồn số:** xóa `MSG71_SETUP_LINK_INVALID`, `MSG72_PASSWORD_SET`, `MSG76_SETUP_LINK_EXPIRED`, `MSG77_SETUP_LINK_USED` (và `EXTRA_SETUP_LINK_INVALID` / `EXTRA_PASSWORD_SET` cũ). Dồn số (tên hằng giữ phần chữ, chỉ đổi số): 73 → `MSG71_CURRENT_PASSWORD_WRONG`, 74 → `MSG72_FULL_NAME_LENGTH`, 75 → `MSG73_POST_UNAVAILABLE`.

| Tên cũ | Tên mới |
|---|---|
| `EXTRA_PASSWORD_RULE` | `MSG66_PASSWORD_RULE` |
| `EXTRA_FULL_NAME_RULE` | `MSG67_FULL_NAME_RULE` |
| `EXTRA_CODE_FORMAT` | `MSG68_CODE_FORMAT` |
| `EXTRA_DOB_TOO_OLD` | `MSG69_DOB_TOO_OLD` |
| `EXTRA_CODE_RESEND_TOO_SOON` | `MSG70_CODE_RESEND_TOO_SOON` |
