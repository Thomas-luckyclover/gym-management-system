# Auth v25: code đã sửa theo bộ rule chốt 03/10/2026

**Mô hình đã chốt:** 1 Person = 1 SĐT, 1 email, 1 tài khoản. Tài khoản phục vụ **mọi vai trò chưa Inactive**
(khách và/hoặc nhân viên, DBR-65). Người có từ 2 vai trò trở lên thì đăng nhập xong phải **chọn vai trò**,
và đổi vai trò không cần đăng nhập lại (CBR-113). Nhân viên **không được tự xử lý hồ sơ khách của mình** (CBR-115).

## 1. Đã sửa

| File | Sửa gì | Rule | Lỗi cũ |
|---|---|---|---|
| `AuthService` | Đọc **tất cả** vai trò còn hiệu lực (Employee `Working`, Customer khác `Inactive`). Không còn vai trò nào thì báo MSG37. Tài khoản có bất kỳ vai trò nhân viên nào thì không tự khóa. | DBR-65, CBR-86, CBR-96, BR-01 | Có Employee Working thì luôn bị coi là nhân viên. Không có vai trò nào thì tự gán `CUSTOMER`, nên nhân viên đã nghỉ vào được khu khách. Hồ sơ khách đã xóa vẫn đăng nhập được. |
| `LoginResult` | Thêm `availableRoles`, `isRoleChoiceRequired()` | CBR-113 | |
| `LoginServlet` | Có từ 2 vai trò: **chưa** đặt `ROLE`, chuyển sang `/select-role` | CBR-113 | |
| `SelectRoleServlet` + `auth/select-role.jsp` (mới) | Màn chọn / đổi vai trò. POST thì server kiểm tra lại vai trò từ DB. | CBR-113, GBR-13 | |
| `AuthFilter` | Đã đăng nhập mà chưa chọn vai trò thì chỉ được vào `/select-role` và `/logout` | CBR-113 | |
| 5 layout (admin, manager, pt, receptionist, customer) | Thêm nút "Đổi vai trò" khi `sessionScope.availableRoles` có dấu phẩy | CBR-113 | |
| `PersonDAO.findByPhone` | Tra **mọi** Person còn hoạt động, không chỉ khách Self | DBR-22 | SĐT của nhân viên bị coi là số mới, INSERT Person trùng số, vỡ unique index, báo "Hệ thống bận". |
| `RegistrationService` | Số đã có tài khoản (kể cả nhân viên) thì dừng, mời đăng nhập. Không có hồ sơ Self còn hiệu lực thì mời ra quầy. | CBR-26, CBR-114 | |
| `AccountSettingsServlet` | Loại thông tin, giá trị mới và email nhận mã được **lưu trong session** lúc gửi mã; lúc xác minh chỉ đọc từ session | CBR-110, GBR-13 | Đọc từ hidden input, nên xin mã cho email A rồi gửi kèm email B là đổi được sang B mà B chưa được xác minh. |
| `ContactChangeService.verifyAndApply` | Kiểm tra trùng **lần 2** ngay trước khi lưu | CBR-110 | Người khác lấy email/SĐT trong 15 phút chờ mã thì vỡ unique index, báo MSG04. |
| `CustomerDAO` / `EmployeeDAO.findByPersonId` | Ưu tiên dòng còn hiệu lực, mới nhất | DBR-65 | |
| `SelfServiceGuard` (mới) | `isOwnCustomerProfile(staffAccountId, customerId)`, `isSamePerson(employeeId, customerId)` | CBR-115, CBR-59 | |
| `1_Init_Schema.sql` + `migrations/002_identity_one_account_v25.sql` | `UNIQUE(PersonId)` của Customers / Employees đổi thành filtered unique (chỉ tính dòng còn hiệu lực) | DBR-65, CBR-114 | Nhân viên bị xóa hồ sơ khách thì không bao giờ tạo lại được hồ sơ khách. Nhân viên nghỉ rồi được tuyển lại cũng bị chặn. |

## 2. Việc của các UC sau (chưa có màn nên chưa code)

- **UC-13 (cho nghỉ việc) và UC-16/17 (xóa hồ sơ khách):** gọi `SessionRegistryListener.invalidateSessionsOfAccount(accountId, null)`. Nếu Person không còn vai trò nào thì đặt `Accounts.Status = 'Inactive'` và `Persons.IsReleased = 1` (CBR-86, DBR-28).
- **UC-13:** Manager không được cho Admin cuối cùng nghỉ việc (BR-07). Manager không sửa position (CBR-108, CBR-109).
- **UC-07 (Admin tạo nhân viên):** SĐT trùng Person còn hoạt động thì gắn Employee vào Person đó. Nếu Person đã có tài khoản thì **không** tạo tài khoản thứ 2 (CBR-95, CBR-114).
- **UC-16 (Lễ tân tạo hồ sơ):** SĐT trùng nhân viên thì tạo Customer trên Person đó. Nếu Person đã có tài khoản thì không tạo thêm (CBR-19, CBR-24).
- **UC-16, 17, 18, 20, 21, 23, 31, 33, 34, 35, 37:** gọi `SelfServiceGuard` trước khi ghi DB (CBR-115).

## 3. Kịch bản test nhanh (seed hiện có)

| # | Thao tác | Kỳ vọng |
|---|---|---|
| 1 | Đăng nhập `trang.nguyen@gms.vn` hoặc `0912000006` (Person 22, PT + khách) | Vào `/select-role`, thấy 2 lựa chọn |
| 2 | Chọn Hội viên, sau đó gõ `/pt/dashboard` | 403 |
| 3 | Bấm "Đổi vai trò", chọn Huấn luyện viên | Vào `/pt/dashboard`, không phải đăng nhập lại |
| 4 | Đang ở `/select-role`, sửa form gửi `role=Admin` | Báo không có quyền, quay lại màn chọn |
| 5 | Person 22 sai mật khẩu 5 lần | **Không** bị khóa tạm (BR-01: có vai trò nhân viên) |
| 6 | Đăng ký online bằng `0912000006` | Báo "Số điện thoại này đã có tài khoản…" (MSG51), không lộ là của ai |
| 7 | Account Settings: xin mã đổi email sang A, dùng DevTools sửa hidden input thành B rồi nhập mã | Lưu **A**, không phải B |
| 8 | Tài khoản 1 vai trò (VD Admin) đăng nhập | Vào thẳng trang chủ vai trò, không qua màn chọn |
