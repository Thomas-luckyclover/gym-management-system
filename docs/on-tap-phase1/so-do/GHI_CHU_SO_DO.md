# Ghi chú sơ đồ kiến trúc — `GMS_Project_Architecture.drawio`

## 1. Mở & xuất file

| Việc | Cách làm |
|---|---|
| Mở | https://app.diagrams.net → **File › Open from › Device** → chọn `GMS_Project_Architecture.drawio`. Hoặc cài draw.io Desktop, hoặc VS Code extension **"Draw.io Integration"** (mở thẳng file). |
| 2 trang | Thanh tab dưới cùng: **1. Project Architecture** (nộp vào báo cáo mục I.1) · **2. Phase 1 — Authentication components** (dùng khi thầy hỏi sâu phần Authentication). |
| Hiệu ứng vẽ tay | Mọi khối đã bật `sketch=1` + font **Architects Daughter** (Google Fonts). Nếu máy offline, font tự rơi về font mặc định — vẫn giữ nét vẽ tay. Tắt/bật: chọn khối › Format panel › Style › tick **Sketch**. |
| Xuất ảnh cho báo cáo | **File › Export as › PNG** → Zoom 200%, tick *Transparent background* = bỏ, *Border* = 10 → chèn vào Word. Hoặc **Export as › PDF** (chọn trang). |
| Chỉnh | Kéo khối thoải mái — đường nối tự bám. Muốn đổi màu cả nhóm: chọn nhiều khối › Fill. |
| `preview_bo_cuc.png` | Ảnh xem trước **bố cục** (tự render đơn giản, không có nét vẽ tay, đường nối vẽ thẳng qua khối) — chỉ để xem nhanh trên điện thoại. Bản chuẩn là file `.drawio`. |

Khác với ảnh mẫu (React + Spring Boot + JWT + PostgreSQL): sơ đồ này vẽ **đúng stack thật của GMS** — JSP render phía server, Servlet/Filter trên Tomcat, HttpSession thay JWT, SQL Server, Gmail SMTP, VNPay. Đừng copy nguyên ảnh mẫu, thầy sẽ hỏi "React ở đâu trong code?".

---

## 2. Đoạn văn tiếng Anh — dán vào báo cáo "I. High Level Design › 1. Software Architecture"

> GMS follows a **three-tier client–server architecture**. Inside the application tier the code is organised as a **layered MVC** stack running on Apache Tomcat 10.1 (Jakarta EE 10, Java 17): *Filter → Controller (Servlet) → Service → Repository (DAO) → Database*, with JSP as the view. Pages are rendered on the server; the browser holds only a session cookie.

| Component | Description |
|---|---|
| **Client Tier – Web Browser** | Displays HTML rendered by JSP (Server-Side Rendering). Static assets: `tokens.css`, `common.css`, page CSS; `common.js` provides client-side validation (`data-rule`), toasts, mm:ss countdowns and the password eye icon; page scripts (e.g. `forgot-password.js`) run countdowns. Requests are normal form GET/POST and AJAX `fetch` calls returning JSON. Only the `JSESSIONID` cookie identifies the user. All user data is printed with JSTL `<c:out>` to prevent XSS. |
| **Filter Chain** | Runs before every servlet. `CharacterEncodingFilter` forces UTF-8; `AuthFilter` lets public URLs through and redirects anonymous users to `/login?returnUrl=…` (JSON 401 for AJAX); `RoleAreaFilter` and its five subclasses (`/admin`, `/manager`, `/pt`, `/receptionist`, `/customer`) return 403 when the signed-in role does not match (Template Method pattern). |
| **Controller Layer (Servlets)** | Annotated with `@WebServlet`, grouped by role (`servlet/auth`, `guest`, `customer`, …). A servlet only reads parameters, takes the account id from the session, calls a service and chooses *forward to JSP* (on validation errors) or *redirect* (Post/Redirect/Get with a flash toast). It contains no business rules and no SQL. |
| **Service Layer** | Holds every business rule and server-side validation (GBR-13) and controls transactions. Every method returns `ServiceResult<T>` (success flag, message, data, per-field errors). Authentication services: `AuthService`, `RegistrationService`, `PasswordRecoveryService`, `VerificationCodeService`, `AccountSettingsService`, `ContactChangeService`, `SystemConfigService` (reads parameters P-01…P-20). |
| **Repository Layer (DAO)** | One DAO per table (`AccountDAO`, `PersonDAO`, `CustomerDAO`, `VerificationCodeDAO`, `SystemConfigDAO`, …). Executes SQL only through JDBC `PreparedStatement` (SQL-injection safe) and maps `ResultSet` rows to entities; it makes no decisions. |
| **Model Layer** | Entities mirror tables (`Account`, `Person`, `Customer`, `VerificationCode` …); DTOs carry results between layers (`ServiceResult`, `LoginResult`, `JsonResponse`, view models). |
| **Utilities & Constants** | `PasswordUtil` (BCrypt, cost 10), `SecureCodeUtil` (SecureRandom 6-digit codes, URL tokens, SHA-256), `ValidationUtil`, `RequestUtil`, `DateTimeUtil` (UTC+7), `DBContext`, `AppConfig`; `Messages` (MSG01–MSG61), `SessionKey`, `Role`, `AppConstants`. |
| **HttpSession & SessionRegistryListener** | The session stores only `accountId`, `role`, `displayName` and short-lived flow data (2-hour timeout); the id is regenerated at login to prevent session fixation. `SessionRegistryListener` (Observer) keeps *account → open sessions* in memory so the system can sign out other sessions after a password or email change (CBR-93, CBR-29). |
| **Background Jobs** | `BackgroundJobScheduler` (`ScheduledExecutorService`, started by a `ServletContextListener`) runs JOB-01…JOB-07: daily package expiry / freeze resume / day-pass expiry / session auto-close, and per-minute order and transaction expiry and leave auto-rejection. |
| **Integration Layer** | Gateway classes isolate third-party systems. `EmailGateway` (API-03) builds multipart text+HTML mail and sends it; failures are wrapped in `EmailSendException`. `VnPayClient` (API-01/02) signs payment URLs and verifies IPN callbacks with HMAC-SHA512. |
| **application.properties** | Machine-specific settings and secrets (database, Gmail App Password, VNPay keys); excluded from Git. Business parameters live in the `SystemConfigs` table instead so the Manager can change them without redeploying. |
| **External – Gmail SMTP Server** | `smtp.gmail.com:587`, STARTTLS required, TLS 1.2/1.3, authenticated with an App Password; delivers verification codes (valid 15 minutes) and password-setup links to the **user mailbox**. The system never sends SMS (GBR-15). |
| **External – VNPay Payment Gateway** | Hosts the card/QR payment page. The official result arrives through the server-to-server **IPN webhook**; only the IPN may mark an order as paid. |
| **Data Tier – SQL Server** | Database `GMS`. Key tables for authentication: `Persons`, `Accounts`, `Customers`, `Employees`, `VerificationCodes`, `SystemConfigs`, `AuditLogs`. Filtered unique indexes (`IsReleased = 0`) and CHECK constraints enforce data rules (DBR-22, DBR-28) even under concurrent requests. |

**Communication summary:** Browser ⇄ Tomcat over HTTP(S) (form posts, AJAX/JSON, redirects); Servlet → Service → DAO by in-process method calls; DAO → SQL Server via JDBC; EmailGateway → Gmail via SMTP/STARTTLS; VnPayClient → VNPay by signed redirect, VNPay → GMS by IPN webhook.

---

## 3. Lời thuyết trình tiếng Việt (chỉ tay theo sơ đồ, ~2 phút)

1. **Trên cùng — Client Tier:** "Người dùng 6 vai trò dùng trình duyệt. Trang HTML do **JSP render sẵn ở server**, trình duyệt chỉ giữ cookie JSESSIONID. `common.js` kiểm tra dữ liệu sớm cho nhanh, nhưng **server mới là nơi quyết định**."
2. **Mũi tên xuống — Filter:** "Mọi request đi qua 3 lớp filter: ép UTF-8, kiểm đăng nhập, kiểm đúng vai trò. Chưa đăng nhập thì về `/login` kèm `returnUrl`; sai vai trò thì 403."
3. **Controller → Service → DAO:** "Servlet mỏng chỉ điều phối; **toàn bộ luật nằm ở Service** và trả `ServiceResult`; DAO chỉ chạy SQL bằng PreparedStatement rồi map sang Entity ở Model Layer."
4. **Bên trái — Utilities / Constants / Listener:** "BCrypt, SecureRandom, SHA-256 gom trong Util. `SessionRegistryListener` theo dõi các phiên để khi đổi mật khẩu thì đăng xuất các thiết bị khác."
5. **Bên phải — Integration & External:** "Service gọi `EmailGateway` → Gmail SMTP cổng 587 STARTTLS → hộp thư người dùng; người dùng đọc mã, quay lại trình duyệt nhập (mũi tên đứt). VNPay dùng chữ ký HMAC, kết quả chính thức về qua IPN."
6. **Dưới cùng — Data Tier:** "SQL Server với các bảng Persons/Accounts/VerificationCodes…; unique index có điều kiện chặn trùng SĐT ngay cả khi 2 request tới cùng lúc."
7. **Trang 2 (nếu thầy hỏi sâu):** "Đây là 5 cột: màn → servlet → service → DAO/util → bảng. VD Quên mật khẩu: `ForgotPasswordServlet` → `PasswordRecoveryService` → `VerificationCodeService` → `VerificationCodeDAO` + `EmailGateway`; khung nét đứt đỏ là phần đã viết nhưng chưa nối (đổi email/SĐT)."

## 4. Câu hỏi hay gặp về sơ đồ

| Hỏi | Đáp |
|---|---|
| Tại sao không có API Gateway / REST API? | App SSR bằng JSP; chỉ vài chỗ AJAX trả JSON trực tiếp từ Servlet — không cần tầng API riêng. |
| Tầng nào xử lý bảo mật? | Filter (đăng nhập/vai trò), Service (luật khóa, OTP), Util (BCrypt, SecureRandom, SHA-256), DAO (PreparedStatement), JSP (`<c:out>`), DB (constraint). "Phòng thủ nhiều lớp". |
| Sao Background Jobs nằm trong Tomcat? | Chạy bằng `ScheduledExecutorService` khởi động cùng ứng dụng — không cần server/thư viện riêng. |
| Email gửi đồng bộ hay bất đồng bộ? | Đồng bộ trong request (timeout 10 s) để biết ngay thành/bại; nhược điểm chậm 1–5 s — hướng cải tiến: hàng đợi gửi mail. |
