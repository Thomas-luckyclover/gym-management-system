# 01 · Kiến trúc tổng thể GMS (tập trung Phase 1 — Authentication)

> Mục tiêu file: đọc xong bạn **vẽ lại được hệ thống** và **giải thích được vì sao** nó được thiết kế như vậy.
> Mọi tên class / đường dẫn trong file đều có thật trên nhánh `develop`.

---

## 1. GMS là gì, Phase 1 gồm những gì

**GMS (Gym Management System — Super Gym)**: web quản lý phòng gym cho 6 vai trò: Guest, Customer, PT, Receptionist, Manager, Admin.

**Phase 1** = màn SRS **#1–7**:

| # | Màn | URL | Use case | Người code |
|---|---|---|---|---|
| 1 | Sign In | `/login` | UC-01 (+ UC-02 Logout `/logout`) | Nhân (lenhan24) |
| 2 | Register Account | `/register` | UC-05, UC-IDENT, UC-Claim | Hoàng (MuscleCodder06) |
| 3 | Forgot Password | `/forgot-password` (+ `/set-password?token=`) | UC-03 (+ CBR-24, CBR-31) | **Minh (Leader)** |
| 4 | Account Settings | `/account` | UC-04 | Vũ (Dang Vu) + Penguin336 |
| 5 | Home | `/` | UC-06 | Minh |
| 6–7 | Blog List / Blog Details | `/blog`, `/blog/detail?id=` | UC-06 | TV2 / Penguin336 |

"Authentication" trong tài liệu này = màn **#1–4** + Email Gateway + phiên/phân quyền.

---

## 2. Công nghệ (tech stack)

| Thành phần | Công nghệ | Phiên bản (pom.xml) | Vì sao chọn |
|---|---|---|---|
| Ngôn ngữ | Java | JDK 17 | LTS, có text block `"""` dùng trong DAO |
| Web container | Apache Tomcat | 10.1 | Tomcat 10 dùng namespace `jakarta.*` (Jakarta EE 10) |
| Web API | Jakarta Servlet / JSP / JSTL | 6.0 / 3.0 | Môn SWP391 yêu cầu Servlet/JSP thuần, không framework |
| Database | Microsoft SQL Server | driver `mssql-jdbc 13.4.0.jre11` | Môn học dùng SQL Server |
| Băm mật khẩu | jBCrypt | 0.4 (`org.mindrot.jbcrypt`) | Chuẩn BCrypt, không phải tự viết thuật toán |
| Gửi email | Angus Mail (Jakarta Mail) | 2.0.3 | Bản cài đặt chính thức của Jakarta Mail |
| JSON (AJAX) | Gson | 2.11.0 | Trả `JsonResponse` cho request AJAX |
| Excel | Apache POI | 5.3.0 | Xuất báo cáo (phase sau) |
| Test | JUnit Jupiter | 5.11.0 | `AuthServiceTest`, `BlogServiceTest`, `UtilTest`, `VnPayClientTest` |
| Build | Maven (war) | — | `mvn clean package` → file `.war` deploy lên Tomcat |

---

## 3. Kiến trúc 4 khối (khớp sơ đồ draw.io)

```
① CLIENT TIER        Trình duyệt: HTML do JSP render + CSS (tokens.css, common.css, pages/*) + JS (common.js, pages/*)
        │ HTTP: form POST/GET · AJAX (header X-Requested-With) · redirect
② APPLICATION TIER   Tomcat 10.1: Filter → Servlet → Service → DAO (+ Util, Listener, Job, Integration)
        │ JDBC (PreparedStatement)                    │ SMTP 587 STARTTLS         │ HTTPS (redirect + IPN)
③ DATA TIER          SQL Server "GMS"            ④ EXTERNAL: Gmail SMTP            ④ EXTERNAL: VNPay (phase sau)
```

- Đây là kiến trúc **Client–Server 3 tầng (3-tier)**, bên trong tầng ứng dụng tổ chức theo **Layered Architecture + MVC**.
- **Khác với ảnh mẫu (React SPA + Spring Boot + JWT)**: GMS render HTML ở server bằng **JSP** (Server-Side Rendering), dùng **HttpSession + cookie JSESSIONID** chứ không dùng JWT, và **Filter** thay cho "JWT Security Filter". Khi vẽ sơ đồ nộp thầy cô, phải vẽ đúng stack thật này.

---

## 4. Các tầng trong Application Tier — ai làm gì, ai CẤM làm gì

| Tầng | Package | Việc được làm | Việc CẤM làm | Ví dụ Phase 1 |
|---|---|---|---|---|
| **Filter** | `com.gms.filter` | Chặn/cho qua request trước khi tới Servlet: UTF-8, đăng nhập chưa, đúng vai trò không | Gọi `getParameter()` (xem mục 6), chứa nghiệp vụ | `CharacterEncodingFilter`, `AuthFilter`, `RoleAreaFilter` + 5 lớp con |
| **Controller (Servlet)** | `com.gms.servlet.<vai trò>` | Đọc tham số, lấy dữ liệu session, gọi Service, chọn `forward` JSP hay `redirect` | Viết SQL, viết `if` nghiệp vụ | `LoginServlet`, `ForgotPasswordServlet` |
| **Service** | `com.gms.service` | **Toàn bộ luật nghiệp vụ + validate server**, mở transaction, gọi DAO/Integration, trả `ServiceResult<T>` | Đụng tới `HttpServletRequest` | `AuthService`, `PasswordRecoveryService`, `VerificationCodeService` |
| **DAO** | `com.gms.dao` | Đúng 1 việc: chạy SQL bằng `PreparedStatement`, map `ResultSet` → Entity | Quyết định đúng/sai, luật | `AccountDAO`, `VerificationCodeDAO` |
| **Entity** | `com.gms.entity` | Ánh xạ 1 bảng DB (Account, Person, VerificationCode…) | Logic | `VerificationCode.Purpose` (enum SignUp/Claim/Recovery/EmailChange/PasswordSetup) |
| **DTO** | `com.gms.dto` | Gói dữ liệu truyền giữa tầng / ra JSP | — | `ServiceResult<T>`, `LoginResult`, `JsonResponse`, `AccountSettingsView` |
| **Util** | `com.gms.util` | Hàm tiện ích tĩnh, không trạng thái | Nghiệp vụ | `PasswordUtil`, `SecureCodeUtil`, `ValidationUtil`, `RequestUtil`, `DBContext`, `AppConfig`, `DateTimeUtil` |
| **Constant** | `com.gms.constant` | Hằng: thông báo, key session, vai trò, giới hạn | — | `Messages` (MSG01–75, khớp SRS III.5), `SessionKey`, `Role`, `AppConstants` |
| **Listener** | `com.gms.listener` | Nghe sự kiện của Tomcat (session tạo/hủy, gắn attribute) | — | `SessionRegistryListener` |
| **Integration** | `com.gms.integration` | Nói chuyện với hệ thống ngoài | Nghiệp vụ | `EmailGateway` (+ `EmailSendException`), `VnPayClient` |
| **Job** | `com.gms.job` | Việc chạy ngầm theo giờ (JOB-01…07) | — | `BackgroundJobScheduler` (`ScheduledExecutorService`) |
| **View** | `webapp/WEB-INF/jsp/**` | Hiển thị dữ liệu Servlet đặt vào `request` | Truy vấn DB, logic | `auth/login.jsp`, `auth/forgot-password.jsp` |

> Câu "chốt" với giáo viên: **"Servlet mỏng, Service dày, DAO câm."** — Servlet chỉ điều phối, Service quyết định, DAO chỉ biết SQL.

### 4.1 `ServiceResult<T>` — "phong bì kết quả" dùng thống nhất

```java
ServiceResult.ok(data)                    // thành công, có dữ liệu
ServiceResult.ok(data, "Mã đã được gửi…") // thành công + câu toast
ServiceResult.fail("Hệ thống đang bận…")  // lỗi chung → hiện ở đầu form / toast
ServiceResult.failField("code", "Mã không đúng…")  // lỗi DƯỚI 1 Ô cụ thể (GBR-17)
ServiceResult.failFields(map)              // nhiều ô lỗi cùng lúc
```
Servlet đọc `isSuccess()`, `getMessage()`, `getFieldErrors()`, `hasFieldErrors()` rồi đẩy sang JSP (`request.setAttribute("fieldErrors", …)`).
Lợi ích: mọi Service trả **cùng 1 kiểu**, Servlet không cần `try/catch` nghiệp vụ, JSP biết chính xác ô nào đỏ.

---

## 5. Vòng đời 1 request — ví dụ POST `/login`

```
Trình duyệt: form login.jsp  →  POST /gms/login  (identifier, password, returnUrl)
   │
   ├─1─ CharacterEncodingFilter   request/response = UTF-8  (để "Nguyễn" không thành "Nguyá»…n")
   ├─2─ AuthFilter                "/login" thuộc PUBLIC_EXACT_PATHS → cho qua
   │                              (RoleAreaFilter không áp dụng: URL không bắt đầu /admin, /customer…)
   ├─3─ LoginServlet.doPost       đọc tham số → authService.login(identifier, password)
   ├─4─ AuthService.login         B1…B8 (xem 02 mục A) → AccountDAO … → PasswordUtil.checkPassword
   ├─5─ LoginServlet              ✔ thành công: hủy session cũ → tạo session mới → lưu accountId/role/displayName
   │                                           → setMaxInactiveInterval(7200) → redirect returnUrl hoặc trang chủ vai trò
   │                              ✘ thất bại : setAttribute(errorMessage, fieldErrors, loginStep) → forward login.jsp
   └─6─ Trình duyệt               nhận 302 → GET trang đích (đi lại qua Filter lần nữa: lần này đã có session)
```

### 5.1 `forward` vs `redirect` và mẫu PRG

| | `RequestUtil.forward(req, resp, "auth/login")` | `RequestUtil.redirect(req, resp, "/login")` |
|---|---|---|
| Là gì | Server chuyển nội bộ sang JSP, **cùng 1 request** | Server trả **302**, trình duyệt gửi **request mới** (GET) |
| URL trên thanh địa chỉ | Không đổi | Đổi sang URL mới |
| Dùng khi | **Hiện lỗi** form, giữ dữ liệu đã nhập (GBR-22) | **Sau POST thành công** |
| Dữ liệu mang theo | `request.setAttribute(...)` | Phải để trong **session** (flash toast) |

**PRG (Post/Redirect/Get):** POST thành công → redirect → GET. Bấm F5 ở trang kết quả chỉ GET lại, **không gửi form lần 2** (không gửi 2 mã OTP, không đổi mật khẩu 2 lần).
**Flash toast:** `RequestUtil.setFlashToast(req, "success", msg)` lưu `Map{type,message}` vào session key `flashToast`; trang sau (`flash-toast.jspf`) đọc, hiện toast, rồi xóa → "sống đúng 1 lần".

---

## 6. Filter chain & phân quyền

```
Mọi URL ──► CharacterEncodingFilter (/*)
        ──► AuthFilter (/*)
              ├ URL công khai?  PUBLIC_EXACT_PATHS  = "", "/", /login, /register, /forgot-password, /set-password, /favicon.ico
              │                 PUBLIC_PATH_PREFIXES = /css/, /js/, /images/, /blog, /payment/vnpay-return, /api/v1/commerce/payment/webhook/
              │                 → cho qua
              ├ Đã đăng nhập? (session có "accountId") → cho qua
              ├ Chưa + AJAX   → JSON 401 + MSG06 (common.js hiện hộp thoại rồi về /login)
              └ Chưa + thường → 302 /login?returnUrl=<trang đang mở>  (chỉ nhớ với GET)
        ──► RoleAreaFilter (lớp cha abstract) — 5 lớp con:
              /admin/*  AdminAreaFilter   /manager/* ManagerAreaFilter   /pt/* PtAreaFilter
              /receptionist/* ReceptionistAreaFilter   /customer/* CustomerAreaFilter
              session.role == getAllowedRole()?  ✔ cho qua  ✘ 403 (AJAX: JSON 403 MSG07; thường: error/403.jsp)
```

Ba điểm "ăn điểm" khi giải thích:
1. **`@WebFilter` không đảm bảo thứ tự chạy.** Vì vậy (a) `RoleAreaFilter` **tự kiểm tra đăng nhập** (chưa đăng nhập thì chuyển cho AuthFilter xử lý), không phụ thuộc AuthFilter chạy trước; (b) quy ước **Filter không gọi `getParameter()`** — encoding chỉ cần đặt trước lần đọc tham số đầu tiên, mà chỉ Servlet đọc, Servlet luôn chạy sau mọi Filter.
2. **Template Method pattern:** `RoleAreaFilter.doFilter()` viết sẵn toàn bộ thuật toán, lớp con chỉ override `getAllowedRole()` — thêm khu vực mới chỉ cần 1 lớp 5 dòng.
3. **`returnUrl` chống Open Redirect:** `RequestUtil.isSafeReturnUrl` chỉ nhận đường dẫn **nội bộ**: bắt đầu `/`, **không** bắt đầu `//` (URL "protocol-relative" sẽ nhảy sang domain khác), **không** chứa `\`.

⚠ Còn thiếu: filter cho `/staff/*` (khu dùng chung nhiều vai trò) — xem `05` mục lỗ hổng.

---

## 7. Session — "thẻ ra vào" của người dùng

| Key (`SessionKey`) | Kiểu | Ghi khi | Dùng cho |
|---|---|---|---|
| `accountId` | Integer | Đăng nhập thành công | **Dấu hiệu đã đăng nhập** (AuthFilter), lấy tài khoản "của chính mình" (CBR-91) |
| `role` | String `Admin/Manager/PT/Receptionist/Customer` | Đăng nhập thành công | RoleAreaFilter, chọn layout |
| `displayName` | String | Đăng nhập thành công | Header |
| `flashToast` | Map | Trước mỗi redirect có thông báo | Toast 1 lần |
| `recoveryEmail` | String | Quên MK bước 1 xong | Bước 2, 3 |
| `recoveryAccountId` | Integer | Quên MK bước 2 xong (mã đúng) | Bước 3 |
| `recoveryDeadline` | LocalDateTime | Quên MK bước 2 xong | Hạn làm bước 3 = hạn của mã |

Nguyên tắc: **không bao giờ** lưu object `Account` (chứa `PasswordHash`) vào session. Timeout 7200 giây (2 giờ).

**Session fixation** (kẻ xấu cài sẵn 1 JSESSIONID cho nạn nhân, chờ nạn nhân đăng nhập rồi dùng lại): `LoginServlet` **hủy session cũ, tạo session mới** ngay khi đăng nhập thành công → JSESSIONID đổi.

**Đăng xuất "mọi thiết bị":** Servlet API **không có** hàm "tìm tất cả session của user X". Dự án tự giữ bảng trong RAM:
```
SessionRegistryListener  (@WebListener, implements HttpSessionListener + HttpSessionAttributeListener)
  Map<Integer accountId, Set<HttpSession>>  (ConcurrentHashMap — an toàn đa luồng)
  attributeAdded("accountId")   → thêm session vào tập của tài khoản
  attributeRemoved / Replaced   → gỡ / chuyển
  sessionDestroyed              → gỡ khỏi mọi tập
  invalidateSessionsOfAccount(accountId, keepSessionId)  → invalidate() từng phiên, chừa phiên keep (null = hủy hết)
```
Đây là **Observer pattern**: Tomcat "phát sự kiện", listener "nghe" và cập nhật sổ. Dữ liệu trong RAM là đủ vì Tomcat khởi động lại thì mọi session cũng mất theo.

---

## 8. Dữ liệu của Authentication

```
Persons (1) ──── (0..1) Accounts (1) ──── (*) VerificationCodes   [AccountId NULL được: khách đang đăng ký]
   │  PersonId, FullName, DateOfBirth, Phone, Email, Avatar, IsReleased
   ├── (0..1) Customers  (CustomerCode KH-nnnn, CustomerType Self|Dependent, Status New|Active|Frozen|Expired|Inactive,
   │                      GuardianPhone, GuardianPhone2)
   └── (0..1) Employees  (EmployeeCode NV-nnnn, Position Admin|Manager|PT|Receptionist, Status Working|Resigned)
SystemConfigs (ConfigKey P-01…P-20, ConfigValue)        AuditLogs (chưa dùng ở Phase 1)
```

| Thiết kế | Ý nghĩa | Luật |
|---|---|---|
| **Tách Person / Account** | Hồ sơ người và tài khoản đăng nhập là 2 thứ; hồ sơ có thể **không có** tài khoản (trẻ em, khách vãng lai) | CBR-17, CBR-18, CBR-21 |
| **1 Person = 1 Account** (`UQ_Accounts_PersonId`) | Người vừa là PT vừa là khách dùng chung 1 tài khoản (vấn đề D1.2 chờ chốt) | UC-01 |
| SĐT/Email nằm trên **Persons** | Dùng chung cho mọi vai trò | DBR-22, 23, 27 |
| `IsReleased = 1` | Hồ sơ đã Inactive → **nhả** SĐT/email cho người khác dùng ngay | DBR-28, CBR-23 |
| Unique index **có điều kiện** `UX_Persons_Phone_Active ... WHERE IsReleased = 0` | Chỉ cấm trùng giữa các hồ sơ đang hoạt động; tranh chấp đồng thời do DB chặn | DBR-22, GBR-13 |
| `Accounts.PasswordHash` NULL được | Tài khoản Lễ tân tạo, khách chưa đặt mật khẩu → MSG38 | CBR-31 |
| `Accounts.Status` (Active/Locked/Inactive) **khác** `Accounts.LockedUntil` | Khóa vĩnh viễn (Admin) ≠ khóa tạm (sai 5 lần) — khóa tạm **không đổi Status** | BR-01, CBR-96 |
| `VerificationCodes` 1 bảng cho cả **OTP và link** | `CK_VerificationCodes_CodeOrToken`: PasswordSetup → có `TokenHash`, `Code` NULL, bắt buộc `AccountId`; loại khác → có `Code`, `TokenHash` NULL | CBR-94, CBR-31 |
| `TokenHash CHAR(64)` | SHA-256 hex của token — **không lưu token thật** | Bảo mật |

---

## 9. Ba loại "cấu hình" — đừng nhầm

| Loại | Nơi lưu | Ai sửa | Ví dụ | Đọc bằng |
|---|---|---|---|---|
| **Cấu hình triển khai / bí mật** | `src/main/resources/application.properties` (**gitignore**) | Người cài máy | `db.url`, `db.password`, `mail.username`, `mail.app-password`, VNPay key | `AppConfig.get("…")` |
| **Tham số nghiệp vụ P-01…P-20** | Bảng `SystemConfigs` | Manager (màn 4.1.2) | P-15 = 15, P-17 = 5, P-18 = 5 | `SystemConfigService.getInt("P-15")` |
| **Hằng số cố định theo thiết kế** | `AppConstants.java` | Dev | 60 giây gửi lại mã, 8–64 ký tự mật khẩu | `AppConstants.CODE_RESEND_GAP_SECONDS` |

Vì sao tách: bí mật **không được lên git**; tham số nghiệp vụ Manager phải sửa được **không cần build lại** (GBR-04); thứ "not configurable" thì để hằng cho rõ.
⚠ Trên `develop` hiện tại file mẫu `application.properties.example` **đã bị xóa lần nữa** (commit `83b706b`) — người mới clone sẽ không biết điền gì. Cần khôi phục lần 3.

---

## 10. Xử lý lỗi — đi từ dưới lên

```
DAO           ném SQLException (không tự bắt)
Integration   EmailGateway gói mọi lỗi SMTP thành EmailSendException (checked → Service BẮT BUỘC xử lý)
Service       catch SQLException / EmailSendException → e.printStackTrace() (log cho dev)
              → ServiceResult.fail(MSG04 "Hệ thống đang bận…")  (không lộ chi tiết kỹ thuật cho người dùng)
Servlet       chỉ đọc ServiceResult, không try/catch nghiệp vụ
web.xml       404 / 403 / 500 / java.lang.Throwable → WEB-INF/jsp/error/*.jsp  (không bao giờ lộ stack trace)
```
Lỗi cấu hình (thiếu tham số P-xx, thiếu `application.properties`) → `IllegalStateException` (runtime) → trang 500: đây là **lỗi của người cài**, không phải lỗi người dùng nhập.

---

## 11. Design pattern đang dùng (hay bị hỏi)

| Pattern | Ở đâu | Nói 1 câu |
|---|---|---|
| **MVC** | JSP (View) · Servlet (Controller) · Service+DAO+Entity (Model) | Tách hiển thị, điều phối, nghiệp vụ |
| **Layered architecture** | Filter → Servlet → Service → DAO | Mỗi tầng chỉ gọi tầng ngay dưới |
| **DAO** | `*DAO` | Gói toàn bộ SQL của 1 bảng vào 1 lớp |
| **DTO / Result object** | `ServiceResult<T>`, `LoginResult` | Truyền kết quả thống nhất giữa tầng |
| **Template Method** | `RoleAreaFilter.getAllowedRole()` | Lớp cha viết thuật toán, lớp con điền 1 chi tiết |
| **Observer** | `SessionRegistryListener`, `BackgroundJobScheduler` (`ServletContextListener`) | Tomcat phát sự kiện, lớp nghe tự phản ứng |
| **Gateway** | `EmailGateway`, `VnPayClient` | 1 cửa duy nhất nói chuyện với hệ thống ngoài; đổi nhà cung cấp chỉ sửa 1 lớp |
| **Post/Redirect/Get** | Mọi POST thành công | Chống gửi form 2 lần |
| **Static utility** | `PasswordUtil`, `SecureCodeUtil`… (`final class` + constructor `private`) | Hàm thuần, không trạng thái, không cho `new` |
| **Dependency injection thủ công** | `AuthService(AccountDAO)`, `LoginServlet(AuthService)` | Constructor thứ 2 để unit test truyền DAO giả |

---

## 12. Quyết định kiến trúc & lý do (bảng "vì sao")

| Quyết định | Lý do |
|---|---|
| Servlet/JSP thuần, không Spring | Yêu cầu môn SWP391; hiểu tận gốc HTTP/session |
| Annotation `@WebServlet/@WebFilter/@WebListener`, `web.xml` chỉ khai báo trang lỗi | Ít cấu hình, không conflict khi 5 người cùng sửa 1 file XML |
| Servlet chia package con theo vai trò (`servlet/auth`, `guest`, `customer`, `pt`…) | ~70 servlet; khớp luôn Filter phân quyền theo URL |
| Session + cookie thay vì JWT | App render phía server (JSP), cùng domain → session đơn giản, thu hồi phiên được ngay (JWT thì khó thu hồi) |
| `DriverManager` qua `DBContext`, chưa có connection pool | Đơn giản cho đồ án; đổi sang pool chỉ sửa 1 file |
| Job ngầm bằng `ScheduledExecutorService` của JDK | Không thêm thư viện (Quartz) |
| Gửi email **đồng bộ** trong request, timeout 10 s | Đơn giản; người dùng thấy ngay gửi được hay lỗi. Nhược: request chờ 1–5 s |
| Mọi thông báo lấy từ `Messages` (Java) và `MESSAGES` (common.js) | 1 nguồn câu chữ theo SRS III.5, không ai tự bịa câu |
| Kiểm tra 2 lần: trình duyệt (`form[data-validate]`) + server (`ValidationUtil`) | Trình duyệt cho trải nghiệm nhanh; **server quyết định** vì người dùng sửa được JS (GBR-13) |

---

## 13. Frontend Phase 1 (đủ để trả lời, không cần sâu)

- `css/tokens.css` (biến màu, bo góc) → `common.css` → `pages/auth/*.css`. Chỉ dùng `var(--…)`.
- `js/common.js`: validate theo thuộc tính `data-rule="email|phone|password|verificationCode|fullName|dateOfBirth"`, `data-match`, nút con mắt `data-password-toggle` (GBR-30), toast (GBR-34), khóa nút khi gửi `setButtonLoading` (GBR-22), đếm ngược mm:ss.
- Đồng hồ OTP: **server tính** số giây (`codeSecondsLeft`, `resendSecondsLeft` trong `ForgotPasswordServlet.setCodeTimers`), JS chỉ đếm lùi → không phụ thuộc đồng hồ máy khách (GBR-27).
- Cache busting: `common.js?v=N` — sửa file thì tăng N.
- JSP dùng `<c:out>` cho mọi dữ liệu người dùng nhập (chống XSS).

---

## 14. Ngoài Phase 1 nhưng có trên sơ đồ

- **VNPay** (API-01 thanh toán, API-02 IPN webhook): chữ ký **HMAC-SHA512**; chỉ IPN (server-to-server) mới được đánh dấu "đã thanh toán" — trang return chỉ hiển thị.
- **Background jobs** JOB-01…07 (`BackgroundJobScheduler`): hằng ngày (hết hạn gói, mở bảo lưu, hết hạn vé ngày, tự đóng buổi PT) và mỗi phút (hủy đơn quá hạn, hết hạn giao dịch, tự từ chối đơn nghỉ).
