# 03b · Truy vết Màn hình → Hàm → Bước → Rule → Use case (theo yêu cầu của thầy)

> Thầy yêu cầu: **"đi từ màn xuống: màn nào → hàm nào → có những bước nào → liên quan rule nào, use case nào"**.
> File này làm đúng việc đó cho 5 màn Authentication + Đăng xuất + Filter. Mỗi màn gồm:
> **(A) Thẻ màn** (URL, UC, actor, người code) → **(B) Vào từ / Đi tới** → **(C) Bảng truy vết từng thao tác** → **(D) Chuỗi gọi hàm có đánh số bước**.
> Cuối file có **Ma trận truy vết ngược** (Rule → hàm, UC → màn) để thầy hỏi ngược "rule X nằm ở đâu" là chỉ được ngay.
>
> Chi tiết sâu hơn cho Email / BCrypt / Quên mật khẩu / Link: xem `03_DEEP_DIVE_…`. Các trường hợp: xem `02b_DECISION_TABLES.md`.

---

## 0. Sơ đồ luồng màn hình (Screen Flow) — Authentication

```
                 ┌───────────────────────────── Header công khai (guest-header.jspf) ─────────────────────────────┐
                 │  "Đăng nhập" → #1        "Đăng ký" → #2        (đã đăng nhập) "Đăng xuất" → POST /logout        │
                 └──────────────────────────────────────────────────────────────────────────────────────────────┘
 #5 Home "/" ──► #1 SIGN IN "/login" ◄───────────────────────────────── toast MSG54 ─── #2 REGISTER "/register"
                   │  │  │   ▲  ▲                                                        (bước 1 SĐT → bước 2 thông tin)
                   │  │  │   │  └──── toast MSG49 ──── #3 FORGOT PASSWORD "/forgot-password" (email → mã → mật khẩu mới)
                   │  │  │   │                               ▲
                   │  │  │   └─ toast "Đã đặt mật khẩu" ── SET PASSWORD "/set-password?token=" ◄── link trong EMAIL (Lễ tân tạo TK)
                   │  │  └── "Quên mật khẩu?" / nút ở panel khóa / nút "Đặt mật khẩu" (MSG38) ──► #3
                   │  └───── "Đăng ký" ──► #2
                   ▼ đăng nhập thành công (redirect returnUrl hoặc trang chủ vai trò)
   /admin/dashboard · /manager/dashboard · /pt/dashboard · /receptionist/check-in · /customer/dashboard
                   │
                   ▼ menu tài khoản
   #4 ACCOUNT SETTINGS "/account"  (tab Hồ sơ · tab Mật khẩu · tab Hồ sơ phụ thuộc [chỉ khách])
                   │
                   ▼ "Đăng xuất"  → POST /logout → #1 + toast MSG40
```

---

## MÀN #1 — SIGN IN

**(A) Thẻ màn**

| Mục | Nội dung |
|---|---|
| URL | `/login` — `LoginServlet` (`@WebServlet("/login")`) |
| View | `WEB-INF/jsp/auth/login.jsp` + `css/pages/auth/login.css` + `js/pages/auth/login.js` |
| Use case | **UC-01 Login** (actor: System User = Customer, PT, Receptionist, Manager, Admin) |
| SRS | II.1.1 Sign In · Screen List #1 |
| Rule chính | BR-01, BR-02, CBR-96, CBR-31, CBR-21, DBR-28, GBR-10, GBR-27, GBR-30, GBR-31 |
| Người code | Nhân (lenhan24) |

**(B) Vào từ / Đi tới**: vào từ Header, `/` (#5), AuthFilter (khi chưa đăng nhập, kèm `returnUrl`), sau #2/#3/Set password (kèm toast). Đi tới: trang chủ vai trò / `returnUrl` · #2 · #3.

**(C) Bảng truy vết**

| # | Thao tác trên màn | HTTP | Servlet · hàm | Service · hàm (bước) | DAO / Util | Bảng DB | Rule | UC | Thông báo | Màn tiếp |
|---|---|---|---|---|---|---|---|---|---|---|
| 1.1 | Mở trang | GET `/login[?returnUrl=]` | `doGet` | — | `RequestUtil.isSafeReturnUrl` | — | GBR-31 | UC-01 | — | #1 (đã đăng nhập → trang chủ vai trò) |
| 1.2 | Bấm **Đăng nhập** | POST `/login` (identifier, password, returnUrl) | `doPost` → `resolveLoginStep`, `extractLockSecondsLeft` | `AuthService.login` B1–B8 (mục D) | `AccountDAO.findByEmail/findByPhone/resetFailedLogin/increaseFailedLogin/lockUntil/updateLastLogin`, `PasswordUtil.checkPassword`, `ValidationUtil` | Accounts, Persons, Employees, Customers | BR-01, BR-02, CBR-96, CBR-31, DBR-28 | UC-01 | MSG09, 33, 34, 35, 37, 38, 39 | ✔ trang chủ vai trò · ✘ #1 (panel form / khóa / chưa có MK) |
| 1.3 | Nút con mắt | (JS) | — | — | `common.js` `data-password-toggle` | — | GBR-30 | UC-01 | — | — |
| 1.4 | "Quên mật khẩu?" / nút panel khóa / "Đặt mật khẩu" (MSG38) | GET `/forgot-password` | → #3 | | | | CBR-31 | UC-03 | | #3 |
| 1.5 | "Đăng ký" | GET `/register` | → #2 | | | | | UC-05 | | #2 |

**(D) Chuỗi gọi hàm — POST `/login`**

```
LoginServlet.doPost
 ├─ identifier = RequestUtil.getTrimmedParam("identifier") · password = getParameter("password") (KHÔNG trim) · returnUrl
 ├─► AuthService.login(identifier, password)
 │    B1  Trống? → failFields / failField (MSG39, MSG09)                                        [GBR-17]
 │    B2  ValidationUtil.isValidEmail → AccountDAO.findByEmail(conn, id)                          [UC-01]
 │        ValidationUtil.isValidPhone → AccountDAO.findByPhone(conn, id)   (SQL: IsReleased = 0)   [DBR-28]
 │        null → fail(MSG34)                                                                       [chống dò TK, CBR-17/21]
 │    B3  Status INACTIVE/LOCKED → fail(MSG37)                                                     [CBR-96]
 │    B4  LockedUntil > now → fail(MSG35) · ≤ now → AccountDAO.resetFailedLogin                   [BR-01, BR-02]
 │    B5  PasswordHash rỗng → fail(MSG38)                                                          [CBR-31]
 │        fetchPersonProfile(conn, personId)  → SQL Persons LEFT JOIN Employees(Working) LEFT JOIN Customers → Role
 │    B6  PasswordUtil.checkPassword(password, hash)                                               [DBR-02 BCrypt]
 │    B7  sai: NV → fail(MSG34) · khách → AccountDAO.increaseFailedLogin → (≥5) AccountDAO.lockUntil → MSG35 / MSG33   [BR-01]
 │    B8  đúng: AccountDAO.resetFailedLogin (nếu cần) → AccountDAO.updateLastLogin → ok(LoginResult{accountId, role, homePath…})
 ├─ ✔ oldSession.invalidate() → request.getSession(true)                                          [chống session fixation]
 │     setAttribute(accountId, role, displayName) → setMaxInactiveInterval(7200)
 │        └─(Tomcat tự gọi) SessionRegistryListener.attributeAdded → registerSession(accountId, session)
 │     target = isSafeReturnUrl(returnUrl) ? returnUrl : role.getHomePath() → RequestUtil.redirect   [GBR-31]
 │     (AJAX → JsonUtil.writeJson(JsonResponse.from(result)))
 └─ ✘ setAttribute(identifier, errorMessage, fieldErrors, loginStep = resolveLoginStep(msg), lockSecondsLeft) → forward login.jsp
```

---

## ĐĂNG XUẤT (UC-02) — nút trên header / menu

| # | Thao tác | HTTP | Servlet · hàm | Bước | Rule | UC | Thông báo | Màn tiếp |
|---|---|---|---|---|---|---|---|---|
| 2.1 | Bấm **Đăng xuất** | POST `/logout` (GET cũng nhận) | `LogoutServlet.processLogout` | B1 `getSession(false)` → `invalidate()` (Tomcat gọi `SessionRegistryListener.sessionDestroyed`) · B2 AJAX → JSON MSG40 · B3 `setFlashToast("info", MSG40)` → redirect `/login` | — | UC-02 | MSG40 | #1 |

---

## MÀN #2 — REGISTER ACCOUNT

**(A) Thẻ màn**

| Mục | Nội dung |
|---|---|
| URL | `/register` — `RegisterServlet` |
| View | `auth/register.jsp` (2 form: `step=1`, `step=2`) + `register.css` + `register.js` |
| Use case | **UC-05 Register Account** (Guest) — gọi **UC-IDENT**; nhánh **UC-Claim** |
| SRS | II.1.2 (1.2.1 Phone Check, 1.2.2 Account details) · BF-04 Customer Onboarding |
| Rule chính | CBR-17, 18, 20, 23, 25, 26, 27, 28, 95 · DBR-01, 02, 04, 09, 17, 18, 22, 23, 28, 29, 34 · P-05 · GBR-13, 15 · API-03 |
| Người code | Hoàng (MuscleCodder06) |

**(C) Bảng truy vết**

| # | Thao tác | HTTP | Servlet · hàm | Service · hàm | DAO / Util | Bảng DB | Rule | UC | Thông báo | Màn tiếp |
|---|---|---|---|---|---|---|---|---|---|---|
| 2.1 | Mở trang | GET `/register` | `doGet` (step 1; hoặc `?step=2&phone=` hợp lệ → step 2) | — | `ValidationUtil.isValidPhone` | — | — | UC-05 | — | #2 bước 1 |
| 2.2 | Bước 1: nhập SĐT → **Tiếp tục** | POST `step=1` | `handlePhoneStep` | — (chỉ kiểm định dạng) | `ValidationUtil.isValidPhone` | — | DBR-17 | UC-05, UC-IDENT | MSG13 | #2 bước 2 (SĐT chỉ đọc) |
| 2.3 | Bước 2: nhập email, MK, họ tên, ngày sinh → **Đăng ký** | POST `step=2` | `handleInformationStep` → `DateTimeUtil.parseHtmlDate` | `RegistrationService.register` (mục D) | `PersonDAO.findByPhone/existsByEmail/insert`, `CustomerDAO.generateNextCustomerCode/insert`, SQL Accounts, `PasswordUtil.hashPassword` | Persons, Customers, Accounts | xem D | UC-05, UC-IDENT, UC-Claim | MSG11–15, 47, 51–54, MSG66–74 | ✔ #1 + MSG54 · ✘ #2 bước 2 (giữ dữ liệu) |
| 2.4 | (LUẬT, chưa code) Nhập mã xác thực | POST (bước 3) | — | `VerificationCodeService.verifyCode(SIGN_UP/CLAIM)` | VerificationCodeDAO | VerificationCodes | CBR-20, CBR-94, API-03 | UC-05, UC-Claim | MSG42–45, 61 | #1 |
| 2.5 | "Đã có tài khoản? Đăng nhập" | GET `/login` | | | | | | | | #1 |

**(D) Chuỗi gọi hàm — POST `/register` step 2**

```
RegisterServlet.handleInformationStep
 ├─ đọc phone, fullName, dateOfBirth (parseHtmlDate), email, password, confirmPassword
 ├─► RegistrationService.register(...)
 │    B1  trim họ tên / SĐT / email (không trim mật khẩu)
 │    B2  validateInput: họ tên 2–50 + ký tự (DBR-01) → ngày sinh có/không tương lai/≤100 (DBR-04)
 │                       → SĐT (DBR-17) → email + ≤100 (DBR-18) → mật khẩu 8–64 chữ+số (DBR-02) → khớp (MSG47)
 │    B3  PersonDAO.findByPhone(phone)  [Persons WHERE Phone=? AND IsReleased=0]                   [UC-IDENT, CBR-23, CBR-28]
 │        có → handleExistingPhone:
 │             hasAccountByPersonId → MSG51                                                         [CBR-26]
 │             hồ sơ không email      → MSG53                                                         [CBR-27]
 │             email khác             → MSG52 (maskEmail)                                             [CBR-25]
 │             email khớp             → claimExistingAccount: hashPassword → TX{ insertAccount, updateCustomerStatusToActive }
 │                                      ⚠ thiếu OTP (CBR-20) · ⚠ đổi New→Active (sai DBR-34/CBR-01)   [UC-Claim]
 │    B4  PersonDAO.existsByEmail(email) → MSG12                                                    [DBR-23, DBR-29]
 │    B5  createNewCustomerTransaction: hashPassword → TX{ PersonDAO.insert → CustomerDAO.generateNextCustomerCode
 │                                      → CustomerDAO.insert(Self, New) → insertAccount(Active) } → commit / rollback
 │        ⚠ thiếu OTP SignUp (UC-05, API-03)                                                         [DBR-09, DBR-34, CBR-95]
 ├─ ✔ setFlashToast(success, MSG54) → redirect /login
 └─ ✘ setAttribute(step=2, errorMessage, phone, fullName, dateOfBirth, email) → forward register.jsp   [GBR-22]
```

---

> **⚠ Cập nhật 27/09:** bảng và chuỗi gọi hàm của Màn #2 ở trên mô tả bản **cũ** (trước commit `6f2511e`). Bản mới của `RegisterServlet` điều phối theo `step`:
> `phone` → `checkPhone` (MSG51 `blocked-account` / MSG53 `blocked-no-email` / `claim-email` / `form`) ·
> nhánh mới `form` → `sendCode(SIGN_UP)` → `otp` → `verifyCode` → `register` → `success` ·
> nhánh Claim `claim-email` (khác email → MSG52) → `sendCode(CLAIM)` → `claim-otp` → `verifyCode` → `claim-password` → `claimAccount` (giữ Customer New) → `success`.
> Lỗ hổng còn lại: `claim-password` không kiểm tra đã xác minh mã (F23); mật khẩu nằm trong ô ẩn HTML (F24).

---

## MÀN #3 — FORGOT PASSWORD

**(A) Thẻ màn**

| Mục | Nội dung |
|---|---|
| URL | `/forgot-password` — `ForgotPasswordServlet` (1 trang, 3 bước theo session) |
| View | `auth/forgot-password.jsp` (4 form: `action=send / verify / resend / reset`) + `forgot-password.css` + `forgot-password.js` (đồng hồ) |
| Use case | **UC-03 Recover Password** (System User, Email Gateway) |
| SRS | II.1.3 (1.3.1 Enter Email, 1.3.2 Enter Recovery Code, 1.3.3 Set New Password) · API-03 |
| Rule chính | BR-03, BR-04, CBR-93, CBR-94, CBR-31, CBR-96 · DBR-02, DBR-03, DBR-18 · P-15, P-17, P-18 · GBR-15, GBR-27 · Chốt #2, #3, #4 |
| Người code | **Minh (Leader)** |

**(C) Bảng truy vết**

| # | Thao tác | HTTP | Servlet · hàm | Service · hàm | DAO / Util / Integration | Bảng | Rule | UC | Thông báo | Màn tiếp |
|---|---|---|---|---|---|---|---|---|---|---|
| 3.1 | Mở trang | GET | `doGet` → `showCurrentStep` → `resolveStep` → `forwardStep` (+ `setCodeTimers`) | `findLatestRecoveryCode` | `VerificationCodeDAO.findLatestByEmailAndPurpose` | VerificationCodes | GBR-27 | UC-03 | (MSG44 nếu quá hạn bước 3) | bước theo session |
| 3.2 | B1: nhập email → **Gửi mã** | POST `action=send` | `handleSend` → `clearRecovery` | `PasswordRecoveryService.requestCode` → `VerificationCodeService.sendCode` | `AccountDAO.findByEmail`, `SystemConfigService.getInt(P-15/17/18)`, `VerificationCodeDAO.findLockedUntil / findLatest… / countCodesSentSince / lockCode / insert`, `SecureCodeUtil.generateVerificationCode`, **`EmailGateway.sendVerificationCode`** | Accounts, Persons, SystemConfigs, VerificationCodes | BR-03, CBR-96, CBR-94, P-15/17/18, GBR-15, API-03, chốt #2/#3/#4 | UC-03 | MSG09, 11, 41, 37, 42, 61, 04, MSG70_CODE_RESEND_TOO_SOON | bước 2 (toast MSG42) |
| 3.3 | B2: **Gửi lại mã** | POST `action=resend` | `handleResend` → `firstError` | `requestCode` (email từ **session**) | như 3.2 | như 3.2 | CBR-94 (60 s, 5 lần/giờ, mã mới hủy mã cũ) | UC-03 | MSG45 / lỗi | bước 2 |
| 3.4 | B2: nhập mã → **Xác nhận** | POST `action=verify` | `handleVerify` | `PasswordRecoveryService.verifyCode` → `VerificationCodeService.verifyCode` | `ValidationUtil.isValidVerificationCode`, `VerificationCodeDAO.findLockedUntil / findLatest… / increaseFailedAttempts / lockCode / markUsed` | VerificationCodes | DBR-03, BR-04, CBR-94 | UC-03 | MSG68_CODE_FORMAT, MSG43, 44, 61 | bước 3 (session: accountId, deadline) |
| 3.5 | B2: "Dùng email khác" | GET `?restart=1` | `doGet` → `clearRecovery` | — | — | — | — | UC-03 | — | bước 1 |
| 3.6 | B3: mật khẩu mới ×2 → **Đổi mật khẩu** | POST `action=reset` | `handleReset` → `isResetExpired` | `PasswordRecoveryService.resetPassword` → `validateNewPassword` → `savePasswordAndUnlock` | `AccountDAO.findById / updatePasswordHash / resetFailedLogin` (1 TX), `PasswordUtil.hashPassword`, **`SessionRegistryListener.invalidateSessionsOfAccount(id, null)`** | Accounts | DBR-02, CBR-93, BR-04, chốt #4, GBR-07 (TODO) | UC-03 | MSG09, MSG66_PASSWORD_RULE, MSG47, 37, 44, **49** | #1 + MSG49 |
| 3.7 | "Quay lại đăng nhập" | GET `/login` | | | | | | | | #1 |

**(D)** Chuỗi gọi hàm đầy đủ từng bước: **`03_DEEP_DIVE` Phần 3** (3.3 send · 3.4 resend · 3.5 verify · 3.6 reset).

---

## MÀN (phụ) — SET PASSWORD qua link email

| Mục | Nội dung |
|---|---|
| URL | `/set-password?token=` — `SetPasswordServlet` · View `auth/set-password.jsp` |
| Use case | Phần của **UC-16/UC-20** (Lễ tân tạo TK — CBR-24) và **UC-01** (MSG38), **UC-03** (link hết hạn → Quên MK) |
| Rule | CBR-24, CBR-31, P-15, DBR-02, GBR-15 |
| Người code | Minh |

| # | Thao tác | HTTP | Servlet · hàm | Service · hàm | DAO / Util | Bảng | Rule | Thông báo | Màn tiếp |
|---|---|---|---|---|---|---|---|---|---|
| 4.1 | Bấm link trong email | GET `?token=` | `doGet` → `handleUnusableLink` | `checkSetupLink` → `VerificationCodeService.inspectSetupLink` (B1 rỗng · B2 `sha256Hex` + `findByTokenHash` · B3 đã dùng → hết hạn · B4 VALID) → `SetupLinkCheck` | `SecureCodeUtil.sha256Hex`, `VerificationCodeDAO.findByTokenHash` | VerificationCodes | CBR-31, P-15 | — (VALID) | form đặt mật khẩu |
| 4.1a | Link **hết hạn** | GET hoặc POST | `handleUnusableLink` (EXPIRED) | (như 4.1) | session `RECOVERY_PREFILL_EMAIL` | VerificationCodes | CBR-31 | MSG76_SETUP_LINK_EXPIRED | **#3** (ô email điền sẵn, chưa gửi mã) |
| 4.1b | Link **đã dùng** | GET hoặc POST | `handleUnusableLink` (USED) | (như 4.1) | | VerificationCodes | CBR-31 | MSG77_SETUP_LINK_USED | **#1** |
| 4.1c | Link **bịa / không có token** | GET hoặc POST | `handleUnusableLink` (NOT_FOUND) | (như 4.1) | | VerificationCodes | Bảo mật | MSG71_SETUP_LINK_INVALID | ở lại, trạng thái link hỏng |
| 4.2 | Nhập MK ×2 → **Đặt mật khẩu** | POST (token, password, confirmPassword) | `doPost` | `setPasswordByLink` (B1 kiểm lại link · B2 `validateNewPassword` · B3 `findById` Active · B4 `savePasswordAndUnlock` + `markUsed`) | `AccountDAO`, `PasswordUtil`, `VerificationCodeDAO.markUsed` | Accounts, VerificationCodes | CBR-24, CBR-31, DBR-02 | MSG72_PASSWORD_SET, MSG37, MSG47 | #1 |
| 4.3 | 4.1c → nút **Quên mật khẩu** | GET `/forgot-password` | | | | | CBR-31 | | #3 |
| 4.4 | Vào #3 từ 4.1a | GET `/forgot-password` | `ForgotPasswordServlet.doGet` (đọc + xóa `RECOVERY_PREFILL_EMAIL`, bỏ tiến độ cũ, `request.email`) | | | | CBR-31 | toast từ 4.1a | #3 bước 1 |

---

## MÀN #4 — ACCOUNT SETTINGS

**(A) Thẻ màn**

| Mục | Nội dung |
|---|---|
| URL | `/account[?tab=profile|password|dependents]` — `AccountSettingsServlet` (`@MultipartConfig`) |
| View | `auth/account-settings.jsp` + fragment theo vai trò `auth/account/profile-*.jspf`, `tab-password.jspf`, `tab-dependents.jspf` |
| Use case | **UC-04 Manage Personal Account** (System User) |
| SRS | II.1.4 (1.4.1 Profile, 1.4.2 Password, 1.4.3 Dependent Profiles) |
| Rule chính | CBR-29, 30, 37, 38, 91, 93 · BR-05 · DBR-02, 08, 17, 18, 22, 23 · GBR-24, 25 · 🆕 CBR-108…113, DBR-64, DBR-65 |
| Người code | Vũ (Dang Vu) + Penguin336 |
| ⚠ Trạng thái trên `develop` | Tab Hồ sơ chạy thật cho **Admin**; Manager có fragment nhưng nút "Cập nhật hồ sơ" (`action=updateProfile`) chưa được servlet xử lý; **PT, Lễ tân, Khách** đang hiện "Hồ sơ của vai trò này đang chờ tích hợp" (`profile-customer.jspf` có sẵn nhưng chưa được include, và nó gửi tới `/account/update-profile`, `/account/verify-change`, `/account/change-password` — **chưa có servlet**). Tab phụ thuộc: khung "đang chờ tích hợp". |

**(C) Bảng truy vết**

| # | Thao tác | HTTP | Servlet · hàm | Service · hàm | DAO / Util | Bảng | Rule | UC | Thông báo | Màn tiếp |
|---|---|---|---|---|---|---|---|---|---|---|
| 5.1 | Mở trang / đổi tab | GET `/account?tab=` | `doGet` → `renderAccountSettings` → `configureRoleView` (chọn layout + fragment theo vai trò, `hasDependentsTab = role==CUSTOMER`) → `resolveActiveTab` | `AccountSettingsService.getAccountSettings` | `AccountDAO.findById`, `PersonDAO.findById` | Accounts, Persons (+ Employees/Customers) | CBR-91 (accountId từ session), CBR-30, 🆕 CBR-108/109/112, GBR-25 | UC-04 | MSG08, MSG04 | #4 |
| 5.2 | Tab Mật khẩu → **Đổi mật khẩu** | POST `action=changePassword` | `handleChangePassword` (lấy `accountId` + `session.getId()`) | `AccountSettingsService.changePassword` B1–B8 (mục D) | `AccountDAO.findById / updatePasswordHash`, `PasswordUtil.checkPassword ×2 / hashPassword`, `SessionRegistryListener.invalidateSessionsOfAccount(id, currentSessionId)` | Accounts | DBR-02, MSG48, CBR-93 | UC-04 | câu viết cứng (≈ MSG47/48/49) | #4 + toast |
| 5.3 | Tab Hồ sơ → chọn ảnh → **Cập nhật ảnh** | POST multipart `action=updateAvatar` | `handleUpdateAvatar` (`request.getPart`) | `AccountSettingsService.updateAvatar` (kiểm loại + dung lượng → tạo tên `avatar-<personId>-<uuid>` → chống path traversal → `Files.copy` → `PersonDAO.updateAvatar` → xóa ảnh cũ) | `ValidationUtil.isValidImageFile`, `PersonDAO.updateAvatar`, `java.nio.file.Files` | Persons.Avatar | DBR-08 / 🆕 DBR-65, CBR-37 | UC-04 | MSG20, MSG58 | #4 |
| 5.4 | (LUẬT, servlet chưa có) Đổi **email** → nhập mã | POST (dự kiến `/account/update-profile`, `/account/verify-change`) | — | `ContactChangeService.requestEmailChange` → `VerificationCodeService.sendCode(EMAIL_CHANGE, email MỚI)` → `verifyAndApply` → `applyEmailChange` (`PersonDAO.updateEmail` → `invalidateSessionsOfAccount` → `EmailGateway.sendNotice(email CŨ)`) | `PersonDAO.existsByEmail / findById / updateEmail`, `EmailGateway` | Persons, VerificationCodes | BR-05, CBR-29, CBR-94, DBR-23, 🆕 CBR-110/111/113, DBR-64 | UC-04 | MSG11, 12, 55, 43, 44, 61, 56 | #4 |
| 5.5 | (LUẬT, servlet chưa có) Đổi **SĐT** → nhập mã | như trên | — | `requestPhoneChange` → `sendCode(EMAIL_CHANGE, email HIỆN TẠI)` → `verifyAndApply` → `applyPhoneChange` (`PersonDAO.updatePhone`) | `PersonDAO.existsByPhone / updatePhone` | Persons | SRS 1.4.1, DBR-22, 🆕 CBR-110 | UC-04 | MSG13, 14, 57 | #4 |
| 5.6 | Họ tên / ngày sinh (chỉ đọc) | — | — | — | — | — | CBR-30, 🆕 CBR-108, GBR-25 | UC-04 (UC-16/UC-13 mới sửa được) | MSG60 | — |
| 5.7 | Tab **Hồ sơ phụ thuộc** (chỉ khách) | GET `?tab=dependents` | `resolveActiveTab` chặn vai trò khác | (chưa có) truy vấn Dependent theo `GuardianPhone/GuardianPhone2` | (chưa có) | Customers, Persons, Subscriptions | 🆕 CBR-112, CBR-21, P-05 | UC-04 | GBR-21 (rỗng) | #4 |
| 5.8 | **Đăng xuất** (menu) | POST `/logout` | `LogoutServlet` | — | — | — | — | UC-02 | MSG40 | #1 |

**(D) Chuỗi gọi hàm — POST `action=changePassword`**

```
AccountSettingsServlet.handleChangePassword
 ├─ session = getSession(false); accountId = session.accountId  (không có → redirect /login)      [CBR-91]
 ├─► AccountSettingsService.changePassword(accountId, current, new, confirm, session.getId())
 │    B1  current trống → failField("currentPassword")
 │        AccountDAO.findById(accountId)
 │    B2  PasswordUtil.checkPassword(current, hash) sai → failField("currentPassword", "…không đúng")
 │    B3  new trống / !ValidationUtil.isValidPassword(new) → failField("newPassword")               [DBR-02]
 │    B4  PasswordUtil.checkPassword(new, hash) đúng → failField("newPassword", "…phải khác…")       [MSG48]
 │    B5  confirm trống · B6 confirm ≠ new → failField("confirmPassword")                            [MSG47]
 │    B7  PasswordUtil.hashPassword(new) → AccountDAO.updatePasswordHash                             [BCrypt]
 │    B8  SessionRegistryListener.invalidateSessionsOfAccount(accountId, currentSessionId)           [CBR-93 — giữ phiên này]
 ├─ ✔ setFlashToast(success, "Đổi mật khẩu thành công.") → redirect /account?tab=password
 └─ ✘ setAttribute(errors, formMessage, activeTab=password) → renderAccountSettings (forward)
```

---

## FILTER — chạy trước MỌI màn

| # | Tình huống | Thành phần · hàm | Bước | Rule | UC | Kết quả |
|---|---|---|---|---|---|---|
| F.1 | Mọi request | `CharacterEncodingFilter.doFilter` | set UTF-8 request/response | GBR-01 | — | — |
| F.2 | Mở trang cần đăng nhập khi chưa đăng nhập | `AuthFilter.doFilter` → `isPublicPath` → `buildLoginRedirectUrl` | B1 công khai? B2 có `accountId`? B3 AJAX → 401 · B4 → `/login?returnUrl=` | GBR-31 | UC-01 | #1 |
| F.3 | Sai khu vai trò | `RoleAreaFilter.doFilter` (+ `getAllowedRole()` của 5 lớp con) | B1 chưa đăng nhập → chuyển AuthFilter · B2 so role · B3 cho qua · B4 403 | GBR-10 | tất cả | error/403.jsp |

---

## MA TRẬN TRUY VẾT NGƯỢC

### A. Use case → Màn → Class

| UC | Tên | Màn | Servlet | Service chính |
|---|---|---|---|---|
| UC-01 | Login | #1 | `LoginServlet` | `AuthService` |
| UC-02 | Logout | header/menu | `LogoutServlet` | — |
| UC-03 | Recover Password | #3 (+ link từ #1, Set password) | `ForgotPasswordServlet` | `PasswordRecoveryService`, `VerificationCodeService` |
| UC-04 | Manage Personal Account | #4 | `AccountSettingsServlet` | `AccountSettingsService`, `ContactChangeService` |
| UC-05 | Register Account | #2 | `RegisterServlet` | `RegistrationService` |
| UC-IDENT | Check Phone/Email & Identify | #2 bước 1–2 | `RegisterServlet` | `RegistrationService.register` B3–B4 |
| UC-Claim | Claim Existing Profile | #2 nhánh email khớp | `RegisterServlet` | `RegistrationService.claimExistingAccount` (+ OTP: chưa) |
| CBR-24 (UC-16/20) | TK Lễ tân tạo → đặt MK | Set password | `SetPasswordServlet` | `PasswordRecoveryService.setPasswordByLink` |

### B. Rule → Nơi thực thi trong code

| Rule | Nội dung ngắn | Hàm thực thi | Trạng thái |
|---|---|---|---|
| BR-01 | Khách sai 5 → khóa 5'; NV không khóa | `AuthService.login` B7 + `AccountDAO.increaseFailedLogin/lockUntil` | ✅ (5/5 viết cứng) |
| BR-02 | Hết khóa / đúng MK → bộ đếm 0 | `AuthService.login` B4, B8 + `AccountDAO.resetFailedLogin` | ✅ |
| BR-03 | Khôi phục chỉ qua email | `PasswordRecoveryService.requestCode` B1 | ✅ |
| BR-04 | Mã hết hạn 15' | `VerificationCodeService.sendCode` B7 (`ExpiresAt`), `verifyCode` B4; `ForgotPasswordServlet.isResetExpired` | ✅ |
| BR-05 | Đổi email/SĐT phải có mã | `ContactChangeService` | 🔴 chưa nối servlet |
| CBR-17/18/21 | Hồ sơ ≠ tài khoản; Dependent không TK | Schema + `AccountDAO.findBy*` (JOIN Accounts) | ✅ |
| CBR-20 | Kích hoạt hồ sơ cần mã tới email hồ sơ | `RegistrationService.handleExistingPhone` | 🔴 thiếu mã |
| CBR-23 / DBR-28 | Hồ sơ đã nhả không chặn | SQL `IsReleased = 0` trong `PersonDAO`, `AccountDAO` | ✅ |
| CBR-24 / CBR-31 | Link đặt MK, chưa có MK không đăng nhập | `SetPasswordServlet` (`handleUnusableLink`), `inspectSetupLink`, `AuthService` B5 | ✅ (phía khách) |
| CBR-25/26/27 | 3 nhánh dừng đăng ký | `RegistrationService.handleExistingPhone` | ✅ |
| CBR-28 | Số giám hộ không chặn | `PersonDAO.findByPhone` chỉ xét `Persons.Phone` | ✅ |
| CBR-29 | Đổi email → báo email cũ + đăng xuất | `ContactChangeService.applyEmailChange` | 🔴 chưa nối |
| CBR-30 / 🆕 CBR-108 | Chỉ sửa ảnh, email, SĐT, MK | JSP fragment theo vai trò (chỉ đọc) | 🟡 |
| CBR-91 | Chỉ sửa TK của mình | `accountId` lấy từ session ở mọi Servlet | ✅ |
| CBR-93 | Đổi MK → đăng xuất phiên cũ | `SessionRegistryListener.invalidateSessionsOfAccount` (gọi từ `resetPassword`, `changePassword`) | ✅ |
| CBR-94 | Luật mã OTP | `VerificationCodeService.sendCode`, `verifyCode` | ✅ |
| CBR-96 | 3 trường hợp chặn TK | `AuthService` B3–B4, `requestCode` B3 | ✅ |
| 🆕 CBR-109 | Không sửa vai trò/trạng thái ở Account Settings | Không có form sửa | ✅ |
| 🆕 CBR-112 | Tab phụ thuộc chỉ khách | `configureRoleView` + `resolveActiveTab` | ✅ |
| 🆕 CBR-113 / GBR-07 | AuditLog email/SĐT/MK | — | 🔴 chưa có `AuditLogService` |
| DBR-02 | MK 8–64 chữ+số, BCrypt | `ValidationUtil.isValidPassword`, `PasswordUtil` | ✅ |
| DBR-03 | Mã 6 số, 1 lần | `isValidVerificationCode`, `markUsed` | ✅ |
| DBR-17/18 | SĐT, email | `ValidationUtil.isValidPhone/isValidEmail` | ✅ (thiếu P-16) |
| DBR-22/23/🆕64 | Không trùng SĐT/email | `PersonDAO.existsBy*` + unique index SĐT | 🟡 (email chưa có unique index) |
| DBR-34 | Khách mới = New | `CustomerDAO.insert(…,"New")` | 🟡 (Claim đổi Active) |
| 🆕 DBR-65 | Avatar ≤ 2 MB, 200–2000 px, 256×256 | `AccountSettingsService.updateAvatar` | 🟡 (5 MB, chưa kiểm px/cắt) |
| GBR-10 | Phân quyền | `RoleAreaFilter` + 5 lớp con | 🟡 (thiếu `/staff/*`) |
| GBR-13 | Server kiểm lại | Mọi Service + `ValidationUtil` | ✅ |
| GBR-15 | Mã chỉ qua email | `EmailGateway` | ✅ |
| GBR-27 | Đồng hồ mm:ss | `ForgotPasswordServlet.setCodeTimers` + `forgot-password.js`; login `AuthService.formatMinutesSeconds` + `extractLockSecondsLeft` | ✅ |
| GBR-31 | Quay về đúng màn | `AuthFilter.buildLoginRedirectUrl` + `RequestUtil.isSafeReturnUrl` | ✅ |

### C. Bảng DB → ai đọc/ghi

| Bảng | Đọc bởi | Ghi bởi |
|---|---|---|
| `Persons` | `AccountDAO.findBy*` (JOIN), `PersonDAO.findByPhone/findById/existsBy*`, `AuthService.fetchPersonProfile` | `PersonDAO.insert` (đăng ký), `updateAvatar`, `updateEmail`/`updatePhone` (chưa nối) |
| `Accounts` | `AccountDAO.findByEmail/findByPhone/findById` | `AuthService` (FailedLoginCount, LockedUntil, LastLoginAt), `RegistrationService.insertAccount`, `savePasswordAndUnlock`, `changePassword` |
| `Customers` | `fetchPersonProfile` (xác định vai trò) | `CustomerDAO.insert`, `updateCustomerStatusToActive` (⚠) |
| `Employees` | `fetchPersonProfile` (Position, Status Working) | — (UC-13, phase sau) |
| `VerificationCodes` | `VerificationCodeDAO.find*`, `countCodesSentSince` | `insert`, `increaseFailedAttempts`, `lockCode`, `markUsed` |
| `SystemConfigs` | `SystemConfigService.getInt` (P-15, P-17, P-18) | — (Manager, UC-09) |
| `AuditLogs` | — | — (chưa dùng — CBR-113) |
