# GMS — Gym Management System (Super Gym) · SWP391 · G5

Hệ thống quản lý phòng gym: khách vãng lai, hội viên, PT, lễ tân, quản lý, quản trị; thanh toán VNPay, gửi mã qua email.

**Stack:** Java 17 · Jakarta Servlet 6 / JSP 3.1 / JSTL 3 · Apache Tomcat 10.1 · SQL Server · Maven · HTML/CSS/JS thuần.

## Cài đặt lần đầu (mỗi thành viên)

1. Cài **JDK 17**, **Apache Tomcat 10.1.x**, **SQL Server 2019+** (+ SSMS), IDE (NetBeans / IntelliJ / VS Code + Maven).
2. Clone repo, mở như project Maven — IDE tự tải thư viện theo `pom.xml` (không add .jar bằng tay).
3. Tạo database: chạy `database/01_schema.sql` rồi `database/02_seed.sql` trong SSMS.
4. Cấu hình: copy `src/main/resources/application.properties.example` → `application.properties` (cùng thư mục), sửa `db.password`…
   File này **không commit** (đã nằm trong `.gitignore`).
5. Build & chạy test: `mvn clean package` → ra `target/gms.war`.
6. Deploy lên Tomcat 10.1 (IDE: Run on Server) → mở `http://localhost:8080/gms/`.

> Dùng Tomcat 10.0.x? Đổi `jakarta.servlet-api` trong `pom.xml` về `5.0.0`. Tomcat 9 trở xuống **không chạy được** (khác package javax/jakarta).

## Đọc gì trước khi code

| Tài liệu | Nội dung |
|---|---|
| `agents.md` | Bối cảnh + toàn bộ quy tắc (AI đọc file này đầu tiên) |
| `docs/project-structure.md` | File nào đặt ở đâu; bảng 67 màn → Servlet → URL → JSP |
| `docs/team-plan.md` | Ai làm gì, quy trình Git, checklist review |
| `docs/notes.md` | Mâu thuẫn tài liệu đang chờ chốt + quyết định kỹ thuật |
| `docs/frontend-shared-usage.md` | Cách dùng tokens.css / common.css / common.js |
| `docs/integration-guide.md` | VNPay + Email: luồng, lỗi, cần chuẩn bị gì |
| `NamingConvention.md`, `codeImplementationBackend.md`, `codeImplementationFrontend.md` | Quy tắc đặt tên & viết code |

## Có sẵn để dùng ngay

- **Java:** `DBContext`, `ServiceResult`, `PageResult`, `JsonResponse`, `PasswordUtil` (BCrypt), `ValidationUtil`, `DateTimeUtil`,
  `MoneyUtil`, `JsonUtil`, `RequestUtil`, `PaginationUtil`, `SqlUtil`, `SecureCodeUtil`, `Messages` (MSG01–61 tiếng Việt),
  `Role`, `SessionKey`, Filter đăng nhập + 5 Filter phân quyền, `SessionRegistryListener`, scheduler + 7 job khung,
  `VnPayClient`, `EmailGateway`, trang lỗi 403/404/500, hàm EL `gms:formatMoney/formatDate/...`.
- **Frontend:** `tokens.css` (6 theme theo vai trò), `common.css`, `common.js` (toast, hộp xác nhận, kiểm tra form,
  AJAX, đếm ngược, định dạng tiền/ngày, phân trang), `head.jspf`, `flash-toast.jspf`.
- **Test:** `mvn test` — kiểm tra ValidationUtil, MoneyUtil, DateTimeUtil, SqlUtil và ký số VNPay.
