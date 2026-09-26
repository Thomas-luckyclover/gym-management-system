# Phase 1: Chia việc chi tiết

> Đặt file này vào `docs/phase-1/`. Rule từng màn hình xem `01_Phase1_Rules.md`, kịch bản test xem `02_Phase1_Test_Scenarios.md`. **Mật khẩu đã chốt: 8–64 ký tự, có ít nhất 1 chữ cái và 1 chữ số.** Code (`AppConstants`, `ValidationUtil`, `common.js`) đã để sẵn, **không ai tự đổi**.

## 0. Bảng tổng

| Người | Nhánh | Việc được giao | Phần dùng chung phải làm trước (người khác đang chờ) |
| :---- | :---- | :---- | :---- |
| **Lê Công Minh** (leader) | `dev_mb1_MinhLC` | Homepage · Recover Password · PT Account Settings | Mã xác thực + gửi email · đọc tham số `SystemConfigs` · `Employee` · `Package` · header/footer trang khách |
| **Phan Khắc Huy Hoàng** | `dev_mb2_HoangPKH` | Sign up · Receptionist Account Settings · Blog List | `Person` · `Customer` |
| **Lê Thị Thanh Nhàn** | `dev_mb3_NhanLTT` | Sign in · Manager Account Settings | `Account` · đăng nhập/đăng xuất (**cả nhóm cần để test**) |
| **Nguyễn Tuấn Minh** | `dev_mb4_MinhNT` | Customer Account Settings · Blog Detail | `BlogPost` (DAO dùng chung cho Home + Blog List + Blog Detail) · đổi email/SĐT |
| **Nguyễn Đăng Vũ** | `dev_mb5_VuND` | Admin Account Settings | Khung trang `/account` dùng chung cho 5 vai trò + tab Mật khẩu |

Vì sao Vũ nhận khung `/account`: SRS chỉ có **1 màn hình** Account Settings (`/account`) cho mọi vai trò. Nếu 5 người cùng sửa 1 servlet thì lúc gộp code sẽ xung đột liên tục. Vũ chỉ có 1 việc nên nhận phần khung. 4 người còn lại mỗi người chỉ làm **phần riêng của vai trò mình** (xem mục 3).

---

## 1. Luật "mỗi file 1 chủ"

- File nào ghi tên ai ở bảng dưới thì **chỉ người đó sửa**.  
- Cần thêm hàm vào file của người khác: nhắn chủ file. Nếu gấp thì tự thêm **đúng 1 hàm**, ghi rõ trong Pull Request để chủ file duyệt.  
- File dùng chung (`util/`, `filter/`, `constant/`, `dto/`, `tokens.css`, `common.css`, `common.js`, `pom.xml`, `database/`): **chỉ leader sửa**.  
- Mỗi Pull Request nên nhỏ: phần entity + DAO gửi trước, màn hình gửi sau.

---

## 2. Chi tiết từng người

### 2.1 Lê Thị Thanh Nhàn: Sign in + Manager Account Settings

**Ưu tiên số 1 của cả nhóm:** chưa đăng nhập được thì không ai test được trang cần đăng nhập.

| Loại | File |
| :---- | :---- |
| Entity | `entity/Account.java` |
| DAO | `dao/AccountDAO.java`: `findByEmail`, `findByPhone`, `findById`, `updatePasswordHash`, `increaseFailedLogin`, `resetFailedLogin`, `lockUntil`, `updateLastLogin` |
| Service | `service/AuthService.java`: `login(identifier, password)` trả `ServiceResult` |
| Servlet | `servlet/auth/LoginServlet.java` (`/login`), `servlet/auth/LogoutServlet.java` (`/logout`) |
| Giao diện | `jsp/auth/login.jsp`, `css/pages/auth/login.css`, `js/pages/auth/login.js` |
| Manager | `jsp/auth/account/profile-manager.jspf` + `jsp/common/layout-manager.jspf` (sidebar Manager lấy từ prototype) |

Rule: mục 2.1 (đăng nhập bằng email hoặc SĐT, khóa tạm sau N lần sai, tài khoản chưa kích hoạt, `returnUrl`, về trang chủ đúng vai trò bằng `Role.getHomePath()`). Cần của người khác: `SystemConfigService` (Minh LC) để đọc số lần sai và thời gian khóa. Trong lúc chờ, tạm dùng hằng số, có hàm thì đổi.

### 2.2 Phan Khắc Huy Hoàng: Sign up + Receptionist Account Settings + Blog List

| Loại | File |
| :---- | :---- |
| Entity | `entity/Person.java`, `entity/Customer.java` |
| DAO | `dao/PersonDAO.java`, `dao/CustomerDAO.java`: `insert`, `findById`, `findByPersonId`, `existsByPhone`, `existsByEmail` |
| Service | `service/RegistrationService.java` |
| Servlet | `servlet/auth/RegisterServlet.java` (`/register`) |
| Giao diện | `jsp/auth/register.jsp`, `css/pages/auth/register.css`, `js/pages/auth/register.js` |
| Receptionist | `jsp/auth/account/profile-receptionist.jspf` + `jsp/common/layout-receptionist.jspf` |
| Blog List | `servlet/guest/BlogListServlet.java` (`/blog`), `jsp/guest/blog-list.jsp`, css/js cùng tên |

Rule: 2.2 (trùng SĐT/email, họ tên, ngày sinh), 2.3 (mã xác thực), 2.7 (6 bài/trang, tìm không phân biệt dấu dùng `SqlUtil`, lọc danh mục). Cần của người khác: `VerificationCodeService` (Minh LC) để gửi mã khi đăng ký, `BlogPostDAO` (Tuấn Minh) cho Blog List. **Làm `Person` và `Customer` trước tiên**, vì Tuấn Minh làm Customer Account Settings cần dùng.

### 2.3 Lê Công Minh (leader): Homepage + Recover Password + PT Account Settings

| Loại | File |
| :---- | :---- |
| Dùng chung (làm đầu tiên) | `entity/VerificationCode.java`, `dao/VerificationCodeDAO.java`, `service/VerificationCodeService.java` (`createCode`, `verifyCode`, `canResend`, `createSetupToken`, `findValidToken` dùng `SecureCodeUtil.sha256Hex`); `dao/SystemConfigDAO.java` + `service/SystemConfigService.java` |
| Entity/DAO | `entity/Employee.java`, `dao/EmployeeDAO.java` (`findWorkingPts`, `findByPersonId`), `entity/Package.java`, `dao/PackageDAO.java` (`findActiveForHomepage`) |
| Recover | `servlet/auth/ForgotPasswordServlet.java` (`/forgot-password`), `servlet/auth/SetPasswordServlet.java` (`/set-password`), jsp/css/js cùng tên |
| Homepage | `servlet/guest/HomeServlet.java` (`/`), `jsp/guest/home.jsp`, `jsp/common/guest-header.jspf`, `jsp/common/guest-footer.jspf` (trang khách, đăng nhập, đăng ký, blog cùng dùng) |
| PT | `jsp/auth/account/profile-pt.jspf` (hiện chuyên môn, số năm kinh nghiệm) + `jsp/common/layout-pt.jspf` |

Rule: 2.4, 2.4b (quên mật khẩu 3 bước, đặt mật khẩu xong đăng xuất mọi phiên), 2.6 (trang chủ). Kiêm: duyệt Pull Request đụng file dùng chung.

### 2.4 Nguyễn Tuấn Minh: Customer Account Settings + Blog Detail

| Loại | File |
| :---- | :---- |
| Entity/DAO (làm đầu tiên) | `entity/BlogPost.java`, `dao/BlogPostDAO.java`: `findActivePage(keyword, category, page)`, `countActive(keyword, category)`, `findActiveById`, `findRelated(category, excludeId, 3)`, `findLatest(3)` |
| Service | `service/BlogService.java` (tính thời gian đọc, lấy tóm tắt từ nội dung, ảnh mặc định theo danh mục) |
| Blog Detail | `servlet/guest/BlogDetailServlet.java` (`/blog/detail`), `jsp/guest/blog-detail.jsp`, css/js cùng tên |
| Customer | `jsp/auth/account/profile-customer.jspf`, `jsp/auth/account/tab-dependents.jspf`, `jsp/common/layout-customer.jspf` |
| Đổi email/SĐT | `service/ContactChangeService.java` (5 vai trò đều dùng) |

Rule: 2.5 (khách chỉ sửa ảnh, email, SĐT; đổi email phải xác minh mã, báo email cũ, đăng xuất mọi phiên; tab hồ sơ phụ thuộc chỉ xem), 2.7 (chi tiết, gợi ý 3 bài, bài đã gỡ không lộ nội dung). **Làm `BlogPostDAO` đầy đủ 5 hàm ngay từ đầu**, vì Hoàng (Blog List) và Minh LC (Homepage) cùng dùng. Cần của người khác: `CustomerDAO` (Hoàng), `VerificationCodeService` (Minh LC).

### 2.5 Nguyễn Đăng Vũ: Admin Account Settings + khung `/account`

| Loại | File |
| :---- | :---- |
| Servlet | `servlet/auth/AccountSettingsServlet.java` (`/account`): đọc vai trò từ session, gọi service lấy dữ liệu, chọn đúng file `profile-<vai trò>.jspf` và layout |
| Service | `service/AccountSettingsService.java`: `changePassword` (đúng mật khẩu cũ, mới khác cũ, đăng xuất **các phiên khác** qua `SessionRegistryListener`), `updateAvatar` |
| Giao diện | `jsp/auth/account-settings.jsp` (khung 3 tab), `jsp/auth/account/tab-password.jspf`, `css/pages/auth/account-settings.css`, `js/pages/auth/account-settings.js` |
| Admin | `jsp/auth/account/profile-admin.jspf` + `jsp/common/layout-admin.jspf` |

Rule: 2.5 (tab Mật khẩu, CBR-91 chỉ sửa hồ sơ của mình). **Gửi khung `/account` sớm**, kể cả khi tab Hồ sơ còn trống, để 4 người kia có chỗ gắn phần của mình.

---

## 3. Account Settings ghép lại thế nào

```
/account  →  AccountSettingsServlet (Vũ)
               │ vai trò = CUSTOMER?  → layout-customer + profile-customer + tab-dependents (Tuấn Minh)
               │ vai trò = PT?        → layout-pt       + profile-pt        (Minh LC)
               │ vai trò = MANAGER?   → layout-manager  + profile-manager   (Nhàn)
               │ vai trò = RECEPTIONIST → layout-receptionist + profile-receptionist (Hoàng)
               │ vai trò = ADMIN?     → layout-admin    + profile-admin     (Vũ)
               └ tab Mật khẩu: dùng chung (Vũ) · đổi email/SĐT: dùng chung (Tuấn Minh)
```

Mỗi người làm phần của vai trò mình, gồm:

1. **Sidebar/layout của vai trò đó**, lấy từ prototype. Phase sau mọi màn hình của vai trò đó đều dùng lại nên phải làm cẩn thận.  
2. **Tab Hồ sơ**: thông tin nào được sửa, thông tin nào chỉ xem.  
3. Đúng theme: `theme-customer`, `theme-pt`… (có sẵn trong `tokens.css`).  
4. Tự test bằng tài khoản mẫu của vai trò đó trong database seed.

---

## 4. Thứ tự làm (để không ai phải chờ ai)

| Đợt | Ai | Gửi Pull Request gì | Mở khóa cho |
| :---- | :---- | :---- | :---- |
| **1** (1–2 ngày đầu) | Nhàn | `Account` + `AccountDAO` + Login/Logout | Cả nhóm test được trang cần đăng nhập |
|  | Minh LC | `VerificationCode` + `SystemConfig` + service | Hoàng (đăng ký), Tuấn Minh (đổi email), Nhàn (số lần khóa) |
|  | Hoàng | `Person` + `Customer` + DAO | Tuấn Minh |
|  | Tuấn Minh | `BlogPost` + `BlogPostDAO` | Hoàng (Blog List), Minh LC (Homepage) |
|  | Vũ | Khung `/account` + tab Mật khẩu | 4 người làm Account Settings |
| **2** | Tất cả | Màn hình chính: Sign up, Recover Password, Homepage, Blog List, Blog Detail, Customer Settings |  |
| **3** | Tất cả | Account Settings của 4 vai trò nhân viên + sửa lỗi theo kịch bản test |  |

Trong lúc chờ phần của người khác, cứ làm giao diện JSP/CSS trước, phần đó không phụ thuộc ai.

---

## 5. Checklist trước khi tạo Pull Request

- [ ] Đã `git pull origin develop` về nhánh mình và chạy lại được.  
- [ ] Đã test đủ các dòng trong `02_Phase1_Test_Scenarios.md` của màn hình mình.  
- [ ] Không sửa file của người khác (ngoại trừ đã báo).  
- [ ] Không commit `application.properties`, không commit thư mục `target/`.  
- [ ] Không in mật khẩu, mã xác thực ra log hay console.  
- [ ] Chữ hiển thị dùng `c:out`, riêng nội dung blog đã lọc HTML khi lưu.  
- [ ] Pull Request: **base = `develop`**, mô tả ngắn đã làm gì, ảnh chụp màn hình.
