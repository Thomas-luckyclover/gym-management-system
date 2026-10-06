# AGENTS.md — Bối Cảnh Dự Án & Quy Tắc Làm Việc Cho AI

Tài liệu này đặt ở thư mục gốc (root) của dự án, để AI đọc và nắm bối cảnh trước khi hỗ trợ code, tránh phải giải thích lại từ đầu mỗi lần prompt.

> ⚠️ **LƯU Ý CỰC KỲ QUAN TRỌNG:** Dù các thành viên khác trong nhóm có thể không dùng hết, không đọc hết, hoặc không hiểu vì sao có từng quy tắc trong file này, AI **vẫn bắt buộc áp dụng đầy đủ 100% nội dung dưới đây** mỗi khi hỗ trợ code (vibecode) cho user hiện tại — **không được tự ý bỏ qua, rút gọn, nới lỏng, hay coi nhẹ bất kỳ quy tắc nào** chỉ vì cho rằng người khác không theo hoặc không cần đến mức đó.

## Phần 1: Bối Cảnh Team & Tech Stack

- **Môi trường chốt:** **Java 17** · **Apache Tomcat 10.1** (Jakarta EE 10: Servlet 6.0, JSP 3.1, JSTL 3.0) · **SQL Server** · Maven.
  - Tomcat 10 dùng package **`jakarta.*`** — tuyệt đối không import `javax.servlet.*` (code mẫu cũ trên mạng hay dùng `javax`, copy vào sẽ lỗi).
  - Khai báo JSTL: `<%@ taglib prefix="c" uri="jakarta.tags.core" %>` (không dùng URI cũ `http://java.sun.com/jsp/jstl/core`).
  - Package gốc của dự án là **`com.gms`** — mọi ví dụ `com.project` trong các file quy tắc được hiểu là `com.gms`.
- **Backend:** Java, chạy trên nền **JSP / Servlet**, tuân thủ nghiêm ngặt mô hình **MVC (Model - View - Controller)**:
  - **Model:** chứa business logic + DAO (truy vấn database). Không đặt logic nghiệp vụ ở nơi khác.
  - **View:** JSP, chỉ đảm nhiệm hiển thị dữ liệu. Không viết business logic hay query trong JSP.
  - **Controller:** Servlet, tiếp nhận request, gọi Model xử lý, điều hướng tới View. Không viết SQL trực tiếp trong Servlet.
- **Quản lý dependency:** Maven — `pom.xml` là nguồn chuẩn cho toàn bộ thư viện/dependency của dự án. Khi thêm/sửa dependency phải cập nhật đúng `pom.xml`.
  Thư viện đã có (không thêm thư viện khác làm cùng việc): Servlet API 6.0, JSTL 3.0, mssql-jdbc, jBCrypt, Gson (JSON cho AJAX),
  Angus Mail (gửi email SMTP), Apache POI (xuất Excel), JUnit 5 (test). VNPay không cần thư viện (HMAC-SHA512 có sẵn trong JDK).
  Job chạy ngầm dùng `ScheduledExecutorService` của JDK — không thêm Quartz.
- **Database:** SQL / **SQL Server**. Mọi câu truy vấn, tối ưu hóa, thiết kế bảng đều phải tuân theo chuẩn cú pháp T-SQL của SQL Server.
- **Frontend:** JavaScript, CSS — giữ nguyên theo cấu trúc hiện tại của dự án. **Toàn bộ CSS bắt buộc dùng Design Tokens từ `tokens.css` (CSS Variables), cấm hard-code màu/spacing/font/radius/shadow trực tiếp** — chi tiết đầy đủ ở `frontend-design-pipeline-SWP391-v4.md` (Nguyên tắc nền, mục 9-10) và `shared-library-guide.md`.
  - File thực tế trong repo: `webapp/css/tokens.css` (lớp 1) → `webapp/css/common.css` (lớp 2) → `webapp/css/pages/<vai-trò>/<màn>.css` (lớp 3); JS dùng chung `webapp/js/common.js`. Cách dùng: `docs/frontend-shared-usage.md`.
  - Theme theo vai trò gắn bằng 1 class trên `<body>`: `theme-guest`, `theme-guest-dark`, `theme-member`, `theme-staff`, `theme-manager`, `theme-admin`. Không tự định nghĩa lại màu vai trò trong CSS trang.
  - Mọi trang include `WEB-INF/jsp/common/head.jspf` (trong `<head>`) và `WEB-INF/jsp/common/flash-toast.jspf` (đầu `<body>`).
- **Bảo mật:** Mật khẩu người dùng bắt buộc phải hash bằng **BCrypt** trước khi lưu vào database. Tuyệt đối không lưu plaintext, không dùng MD5/SHA thường (không có salt) để thay thế.

## Phần 2: Cấu Trúc Package (Package Structure)

Bảng đầy đủ "file nào đặt ở đâu" + 67 màn → Servlet → URL → JSP: **`docs/project-structure.md`** (AI tra bảng này trước khi tạo file mới).

- **Chia package rõ ràng theo layer**, không gộp chung, không đặt sai chỗ:
  ```
  src/main/java/com/gms/
   ├── constant/     → Role, SessionKey, Messages (MSG01…61), AppConstants — hằng số dùng chung
   ├── entity/       → các class Entity (POJO ánh xạ bảng DB)
   ├── dao/          → các class DAO (CRUD, truy vấn SQL Server)
   ├── dto/          → ServiceResult, PageResult, JsonResponse, view object gộp nhiều bảng cho 1 màn
   ├── service/      → business logic, validate, gọi DAO, transaction, ghi AuditLog
   ├── servlet/      → Controller, chia package con theo VAI TRÒ = khu vực URL (xem bên dưới)
   ├── filter/       → CharacterEncodingFilter, AuthFilter, RoleAreaFilter + 5 Filter theo khu vực
   ├── listener/     → SessionRegistryListener (đăng xuất mọi thiết bị)
   ├── job/          → BackgroundJobScheduler + JOB-01…07 (chỉ gọi Service)
   ├── integration/  → VnPayClient, EmailGateway (API bên thứ 3 — Phần 7)
   └── util/         → class dùng chung (ValidationUtil, PasswordUtil, DBContext, DateTimeUtil, MoneyUtil...)
  ```
- **Package con của `servlet/`** — ngoại lệ hợp lệ của quy tắc "không chia quá sâu" vì layer này có ~70 class, và mỗi package con khớp đúng 1 Filter phân quyền theo URL:
  `auth` (đăng nhập, đăng ký, tài khoản — mọi vai trò) · `guest` (trang chủ, blog) · `customer` (`/customer/*`) · `pt` (`/pt/*`) · `receptionist` (`/receptionist/*`) · `staff` (màn PT + Lễ tân dùng chung, 1 Servlet khai báo 2 URL) · `manager` (`/manager/*`) · `admin` (`/admin/*`) · `payment` (VNPay IPN + trang kết quả).
  Các layer khác (`entity`, `dao`, `service`) để **phẳng**, không chia package con.
- **Không chia package quá sâu/quá nhỏ** kiểu tách vụn từng chức năng thành 1 package riêng (VD: không cần `service/user/login/`, `service/user/register/` tách lẻ) — mức chia hợp lý là theo **layer** là đủ cho quy mô dự án này.
- **Tài nguyên tĩnh (ảnh, css, js) không nằm trong package Java** — đặt trong `webapp/`, tách theo loại, rồi theo vai trò:
  ```
  src/main/webapp/
   ├── css/tokens.css, css/common.css, css/pages/<vai-trò>/<màn>.css
   ├── js/common.js, js/pages/<vai-trò>/<màn>.js
   ├── images/
   └── WEB-INF/
        ├── web.xml               → CHỈ error-page 403/404/500
        ├── gms-functions.tld     → hàm EL gms:formatMoney / formatDate / formatTime / formatDateTime
        └── jsp/
             ├── common/          → head.jspf, flash-toast.jspf, header/sidebar/footer từng vai trò
             ├── error/           → 403.jsp, 404.jsp, 500.jsp
             └── auth/ guest/ customer/ pt/ receptionist/ staff/ manager/ admin/
  ```
  `<vai-trò>` dùng đúng 1 bộ tên ở mọi nơi (package Servlet, thư mục JSP, CSS, JS). VD màn Đặt buổi lẻ: `servlet/customer/BookSingleSessionServlet.java` → `jsp/customer/book-session.jsp` → `css/pages/customer/book-session.css` → `js/pages/customer/book-session.js`.
- **Cấu hình theo máy:** `src/main/resources/application.properties` (copy từ `.example`, không commit) — chỉ chứa thông tin triển khai (DB, SMTP, VNPay). Tham số nghiệp vụ P-01…P-21 nằm trong bảng SystemConfigs.
- **Xếp đúng chỗ:** mỗi class/file phải nằm đúng package/thư mục theo vai trò của nó (Phần 8 sẽ nhắc lại nguyên tắc này) — không tiện đâu để đó, không để Entity lẫn trong `servlet/`, không để ảnh lẫn trong `css/`.

## Phần 3: Tài Liệu Tham Chiếu Bắt Buộc

Khi thực hiện bất kỳ nhiệm vụ nào, AI phải đối chiếu và bám sát các tài liệu sau:

- **SRS (Software Requirements Specification):** xây dựng **trước khi code**. Là nguồn chuẩn cho yêu cầu nghiệp vụ, use case, luồng chức năng, ràng buộc phi chức năng.
- **SDS (Software Design Specification):** xây dựng **song song với quá trình code**. Mô tả thiết kế kỹ thuật: class diagram, sequence diagram, database schema, kiến trúc chi tiết.
- **File notes (`docs/notes.md`):** ghi chú phát sinh trong quá trình làm việc, dùng để trace lại các quyết định, thay đổi thực tế so với thiết kế ban đầu. Mục A của file liệt kê các mâu thuẫn tài liệu **đang chờ nhóm chốt** — code phần liên quan phải kiểm tra mục này trước.
- **Rule & tham số SRS:** `GMS_Rules_and_Parameters` **v25** (GBR, CBR, DBR, BR, P-01…P-21) — khi code nghiệp vụ phải dẫn đúng mã luật (VD `// B2: hủy đơn quá hạn (CBR-70)`) để trace.
- **Hồ sơ module `docs/handoff/`:** mỗi module có file liệt kê file cần sửa, rule gắn từng bước, luồng B1…Bn và case test trong seed — **đọc file module trước khi code module đó**.
- **Database:** `database/1_Init_Schema.sql` (schema v25), `database/2_Seed_Data.sql` (sinh từ `database/tools/seeddata.py`), danh mục case + tài khoản test `docs/database/SEED_CASES.md`, thay đổi schema `docs/database/SCHEMA_CHANGES_v25.md`.
- **Tài liệu dự án khác:** `docs/project-structure.md` (vị trí file, bảng màn hình), `docs/team-plan.md` (phân công, Git, checklist review), `docs/integration-guide.md` (VNPay/Email), `docs/frontend-shared-usage.md` (tokens/common).
- **`NamingConvention.md`:** quy tắc đặt tên biến/class/method/bảng/cột — file riêng, bắt buộc tuân thủ khi đặt tên.
- **`codeImplementationBackend.md`:** quy tắc viết code Backend (Java: Entity/DAO/Service/Servlet/Utility) — comment, thụt lề, code style, tối ưu, cấu trúc setter... — file riêng, bắt buộc tuân thủ khi implement.
- **`codeImplementationFrontend.md`:** quy tắc viết code Frontend (JSP/HTML/CSS/JavaScript) — file riêng, bắt buộc tuân thủ khi implement phần hiển thị/tương tác.

AI **bắt buộc** dựa vào các nguồn trên để trace và thực hiện nhiệm vụ, không tự suy diễn ngoài phạm vi tài liệu khi chưa được xác nhận.

## Phần 4: Xử Lý Mâu Thuẫn Giữa Các Tài Liệu

Trong quá trình code, nếu phát hiện SRS, SDS, hoặc file notes có đoạn:
- Mâu thuẫn với nhau (VD: SRS mô tả một luồng, SDS thiết kế lại theo cách khác), hoặc
- Mâu thuẫn với yêu cầu mới nhất trong chat

thì AI **không được tự ý chọn một bên rồi âm thầm làm theo**. AI phải:
1. Nêu rõ mâu thuẫn cụ thể — trích dẫn đoạn nào ở tài liệu nào nói gì.
2. Đề xuất hướng xử lý hợp lý.
3. Chờ user xác nhận trước khi code tiếp phần liên quan.

## Phần 5: Sửa Đổi Database Trong Quá Trình Làm (Siêu Quan Trọng)

Team không ngại sửa database trong quá trình làm (dù luôn cố gắng thiết kế tốt ngay từ đầu) — nhưng **việc sửa database không được tự ý thực hiện ngay lúc phát hiện vấn đề**, vì đây là thứ ảnh hưởng đến toàn bộ nhóm, không riêng 1 người.

**Điều kiện để coi là "phải sửa database":** phát hiện điểm bất hợp lý *thật sự* đáng lo ngại (không phải tiểu tiết có thể bỏ qua), và đã **trace đầy đủ** SRS/SDS/logic code liên quan để chắc chắn đây không phải do hiểu sai — mà là bản thân thiết kế database có vấn đề.

**Khi rơi vào điều kiện trên, AI bắt buộc làm theo đúng thứ tự sau:**

1. **Báo ngay cho user** — không tự sửa database ngay tại thời điểm phát hiện, dù đang code dở phần liên quan.
2. Trình bày rõ: bảng/cột nào có vấn đề, vấn đề cụ thể là gì, đã trace SRS/SDS/code nào để kết luận đây là lỗi thiết kế (không phải hiểu nhầm).
3. **Nhắc user copy lại database hiện tại (hoặc đoạn schema cần sửa) vào Notion trước** khi thực hiện bất kỳ thay đổi nào — để nhóm có bản lưu đối chiếu, tránh mất dấu vết nếu sửa sai hoặc cần rollback.
4. Đề xuất phương án sửa (ảnh hưởng bảng nào, có cần migrate dữ liệu cũ không).
5. **Chờ cả nhóm bàn bạc và thống nhất** — mỗi người tự sửa database local của mình sau khi đã thống nhất phương án, AI không tự sửa schema ngay trên code lúc phát hiện vấn đề.

**Tuyệt đối không:** vừa phát hiện vấn đề database vừa lẳng lặng sửa luôn schema + code cùng lúc mà chưa qua bước báo + chờ user xác nhận ở trên.

## Phần 6: Mức Độ Ưu Tiên Nguồn Thông Tin (Information Priority)

Khi có xung đột thông tin, đối chiếu theo thứ tự ưu tiên giảm dần:

1. **[Ưu tiên 1 - Cao nhất] Chat trực tiếp với user:** Mọi yêu cầu, phản hồi, thay đổi vừa trao đổi trong chat hiện tại là CHÂN LÝ, luôn ghi đè lên tài liệu nếu có xung đột.
2. **[Ưu tiên 2] SRS:** Dùng làm cơ sở cho luồng nghiệp vụ, yêu cầu hệ thống chưa được nhắc đến trong chat.
3. **[Ưu tiên 3] SDS + File notes:** Thiết kế kỹ thuật chi tiết, quyết định phát sinh trong quá trình code, dùng khi SRS chưa mô tả đủ chi tiết kỹ thuật.
4. **[Ưu tiên 4] Nguồn khác:** Codebase cũ, quy chuẩn chung — chỉ dùng khi cả 3 nguồn trên đều không đề cập.

## Phần 7: Pháp Lý & Tích Hợp API (Legal & API/Integration)

### 6.1. Vấn đề pháp lý liên quan dự án (nếu có)

- Nếu dự án có phần liên quan đến **pháp lý** (VD: chính sách bảo mật dữ liệu người dùng, điều khoản sử dụng, quyền riêng tư, quy định lưu trữ dữ liệu...), phải **bám sát đúng theo SRS** — không tự suy diễn hay tự thêm quy định pháp lý ngoài phạm vi SRS mô tả.
- Đây là phần **nhạy cảm**, sai sót có thể ảnh hưởng thật đến dự án — nếu SRS mô tả không rõ hoặc thiếu, áp dụng quy trình ở Mục 6.3 bên dưới (không tự quyết).

### 6.2. Tích hợp API / Integration

- Mọi điểm tích hợp API (gọi API bên thứ 3, expose API cho hệ thống khác, tích hợp thanh toán, gửi email/SMS...) phải:
  - **Tổ chức rõ ràng:** tách riêng logic gọi API ra khỏi Service nghiệp vụ thông thường (VD: package/class riêng như `integration/PaymentGatewayClient.java`), không viết lẫn vào Service xử lý nghiệp vụ khác.
  - **Code dễ hiểu:** đặt tên rõ mục đích, tách từng bước gọi API (chuẩn bị request → gửi → xử lý response → xử lý lỗi) theo đúng khung chương trình từng bước đã quy định trong `codeImplementationBackend.md`.
  - **Bám sát đúng SRS** về: endpoint, định dạng request/response, cơ chế xác thực (API key, OAuth...), luồng xử lý lỗi — không tự chế định dạng khác với SRS.

### 6.3. Khi SRS chưa có hoặc còn thiếu (trường hợp code trước, viết SRS sau)

Nếu đến lúc cần làm phần pháp lý hoặc API/Integration mà **SRS chưa có hoặc mô tả chưa đủ**:

1. **Không tự ý chọn đại 1 cách làm rồi code luôn.** AI phải áp dụng cách làm đúng kỹ thuật, an toàn, chuẩn mực nhất theo thông lệ chung (best practice) làm phương án đề xuất.
2. **Bắt buộc thảo luận với user trước khi code** — trình bày rõ: SRS đang thiếu gì, phương án AI đề xuất là gì, vì sao chọn phương án đó — chờ user xác nhận rồi mới thực hiện.
3. Sau khi user xác nhận, ghi lại quyết định này vào file notes (theo Phần 3) để làm căn cứ, tránh phải hỏi lại lần sau.

### 6.4. Sau khi hoàn thành API/Integration — bắt buộc bàn giao 2 thứ

1. **Bản giải thích cho user** (ngôn ngữ dễ hiểu, không thuần kỹ thuật): API này làm gì, gọi ra sao, luồng dữ liệu đi thế nào, các trường hợp lỗi xử lý ra sao.
2. **Comment chi tiết trong code** tại phần API/Integration — đây là **ngoại lệ** so với quy tắc "không giải thích logic phức tạp" ở `codeImplementationBackend.md` Mục 1: phần tích hợp API luôn cần comment giải thích rõ từng bước xử lý, vì đây là phần dễ lỗi, khó debug, và thường liên quan đến bên thứ 3 ngoài tầm kiểm soát của team.

### 6.5. Khi AI cần user hỗ trợ trong lúc làm API/Integration — báo ngay, gấp, cụ thể

Vì user chưa có kinh nghiệm về API, nên **AI không được tự âm thầm tìm cách lách qua** khi thiếu thứ gì đó cần thiết để tích hợp — phải dừng lại và báo ngay lập tức, càng rõ càng tốt. Các trường hợp bắt buộc báo:

- **Thiếu API key / secret key / token** để gọi dịch vụ bên thứ 3.
- **Cần tài khoản trên dịch vụ bên thứ 3** (VD: tài khoản cổng thanh toán, tài khoản gửi email/SMS) mà user chưa đăng ký.
- **Cần quyền/cấu hình trên dashboard bên thứ 3** (VD: bật sandbox mode, whitelist domain/IP, cấu hình webhook URL).
- **Thiếu tài liệu/thông tin kỹ thuật của bên thứ 3** (VD: link API docs cụ thể user đang dùng, vì mỗi dịch vụ có docs khác nhau).
- **Không chắc môi trường test hay thật (sandbox/production)** đang cần dùng cái nào.

**Cách báo — bắt buộc đủ 4 ý sau, không báo chung chung:**
1. **Đang thiếu chính xác cái gì** (tên cụ thể: API key, tài khoản dịch vụ nào, quyền gì...).
2. **Vì sao cần** (để làm bước nào trong luồng tích hợp).
3. **Lấy/tạo ở đâu** (nếu AI biết — VD: "vào trang dashboard của [dịch vụ] → mục API Keys để lấy").
4. **Mức độ gấp** — nếu thiếu cái này thì AI **không thể code tiếp phần liên quan**, phải nêu rõ để user ưu tiên xử lý ngay, tránh để cả task bị đứng lại mà user không biết vì sao.

## Phần 8: Nguyên Tắc Code Bắt Buộc

- Tuân thủ mô hình MVC nghiêm ngặt (xem Phần 1).
- Đặt file/class đúng package theo layer, đúng cấu trúc đã quy định ở Phần 2 — không đặt sai chỗ, không gộp lẫn giữa các layer.
- Đặt tên biến, class, method, bảng/cột database theo đúng `NamingConvention.md`.
- Mật khẩu luôn hash bằng BCrypt trước khi lưu DB.
- Câu lệnh SQL viết đúng chuẩn cú pháp SQL Server (T-SQL).
- Khi sửa/thêm chức năng, kiểm tra chức năng đó có được mô tả trong SRS/SDS không — nếu có sai khác, áp dụng Phần 4.
- Nếu phát hiện database cần sửa (sau khi đã trace đủ SRS/SDS/code): **không tự sửa ngay** — báo user theo đúng quy trình Phần 5.
- Khi viết code (comment, format, style, tối ưu): tuân thủ đúng `codeImplementationBackend.md` (Backend) / `codeImplementationFrontend.md` (Frontend), không tự ý áp dụng style khác.

## Phần 9: Quy Trình AI Tự Xử Lý Yêu Cầu (Internal Task Framework)

File này AI đọc mỗi khi bắt đầu làm việc trong dự án (vibecode) — **coder không cần đọc/viết prompt theo cấu trúc này**. Vì user thường chat ngắn gọn, không viết prompt dài dòng đầy đủ, nên với **mọi yêu cầu nhận được** (kể cả 1 câu ngắn), AI tự áp dụng 5 bước sau để xử lý nội bộ trước khi code/trả lời, không cần đợi user cung cấp đủ thông tin theo khuôn mẫu:

### Bước 1: Tự xác định vai trò & mục tiêu của yêu cầu
Trước khi làm, AI tự xác định: yêu cầu này thuộc tầng nào (Model/View/Controller/DB), mục tiêu cụ thể là gì — dù user chỉ nói ngắn gọn (VD: "sửa lỗi login" → tự hiểu đây là việc ở Controller + Service liên quan xác thực).

### Bước 2: Tự thu thập ngữ cảnh trước khi trả lời
AI tự rà lại: lịch sử chat gần nhất, SRS/SDS/file notes liên quan, code hiện có trong repo — không hỏi lại user những gì đã có sẵn trong các nguồn này.

### Bước 3: Tự lập kế hoạch thực thi, đối chiếu Phần 4 & Phần 6
Trước khi code, AI tự rà: có mâu thuẫn giữa SRS/SDS/notes/chat không (Phần 4) → nếu có, dừng lại báo rõ. Nguồn nào ưu tiên hơn khi thiếu thông tin (Phần 6).

### Bước 4: Tự đối chiếu định dạng/mẫu đã có
Nếu SRS/SDS/codebase đã có mẫu tương tự (class, DAO, API response...), AI bám theo mẫu đó để đồng bộ, thay vì tự sáng tạo cấu trúc mới không cần thiết.

### Bước 5: Tự rà lại ràng buộc cốt lõi trước khi đưa kết quả
Trước khi trả lời/code xong, AI tự kiểm tra lại: đúng mô hình MVC chưa, đúng `NamingConvention.md`, `codeImplementationBackend.md`/`codeImplementationFrontend.md` chưa, có bỏ sót mâu thuẫn nào cần báo không — rồi mới đưa ra kết quả cuối.

## Phần 10: Quy Ước Commit Message (Git)

**Trigger:** khi user gõ **"tôi muốn commit"**.

**AI phải làm theo đúng thứ tự:**
1. Xác định phạm vi cần soát: tính từ lần user gõ **"tôi muốn commit" gần nhất trước đó** (nếu có) cho đến tin nhắn "tôi muốn commit" mới nhất vừa gõ. Nếu đây là lần đầu tiên trong cuộc trò chuyện, tính từ đầu đoạn chat.
2. Lướt lại toàn bộ nội dung đã thực hiện trong phạm vi đó — những gì đã code/sửa/thêm/xóa, đối chiếu file nào đã đổi.
3. Viết commit message theo đúng format:
   ```
   <loại>: <mô tả ngắn gọn, rõ ràng>
   ```
   Các `<loại>` (type) thường dùng:
   - `feat`: thêm chức năng mới
   - `fix`: sửa lỗi
   - `refactor`: đổi cấu trúc code, không đổi hành vi
   - `style`: chỉnh format/style, không ảnh hưởng logic
   - `docs`: sửa tài liệu (agents.md, README...)
   - `test`: thêm/sửa test
   - `chore`: việc lặt vặt khác (cập nhật `pom.xml`, cấu hình...)

**Ví dụ:**
```
feat: Add student registration form validation
fix: Correct average score calculation in StudentService
refactor: Extract ValidationUtil from Servlet into util package
```

- Nếu trong phạm vi soát có **nhiều loại thay đổi khác nhau** (VD: vừa thêm chức năng vừa sửa lỗi), AI đề xuất tách thành nhiều commit riêng (nhiều dòng message), thay vì gộp chung 1 dòng mô tả mơ hồ.
- Mô tả phải **cụ thể, ngắn gọn** — không viết chung chung kiểu `fix: sửa lỗi` mà phải nói rõ sửa gì (`fix: Xử lý chia cho 0 khi danh sách sinh viên rỗng`).

## Phần 11: Lưu Ý Cuối (Ghi Nhớ Cốt Lõi)

- Chat với user > SRS > SDS/File notes > Nguồn khác.
- Mô hình MVC nghiêm ngặt, JSP/Servlet, Maven (pom.xml), SQL Server.
- Package chia rõ theo layer (entity/dao/service/servlet/util), không quá sâu/vụn, tài nguyên tĩnh (ảnh/css/js) đặt đúng chỗ trong `webapp/`.
- Mật khẩu luôn BCrypt.
- Phát hiện mâu thuẫn giữa SRS/SDS/notes/chat → nêu rõ, không tự quyết.
- **Sửa database giữa chừng (siêu quan trọng):** phát hiện vấn đề thật sự đáng lo + đã trace đủ SRS/SDS/code → báo ngay cho user, nhắc copy schema/database vào Notion trước, đề xuất phương án, chờ cả nhóm bàn bạc thống nhất rồi mỗi người mới tự sửa database local — **không tự sửa schema/code ngay lúc phát hiện**.
- Đặt tên theo `NamingConvention.md`; enum DB ↔ Java theo mục 2b.
- **Comment chỉ là khung** (`codeImplementationBackend.md` Mục 1): Javadoc 1 câu, `// ===== nhóm =====`, `// Bn: …` 1 dòng; cấm kể lịch sử, ví dụ, giải thích cú pháp.
- **Code theo đợt (Phần 14):** không bao giờ sinh nguyên tính năng / nguyên file trong 1 lần.
- Khi viết/sửa code thực tế (comment, thụt lề, code style, tối ưu ở đâu/dễ hiểu ở đâu): bắt buộc tuân thủ `codeImplementationBackend.md` (Backend) / `codeImplementationFrontend.md` (Frontend) — không tự suy diễn cách trình bày khác.
- Pháp lý & API/Integration: bắt buộc bám sát SRS; nếu SRS thiếu → đề xuất phương án chuẩn nhất + thảo luận với user trước khi code; API/Integration xong phải có bản giải thích cho user + comment chi tiết trong code (ngoại lệ so với quy tắc comment thông thường).
- Khi làm API/Integration mà thiếu key/tài khoản/quyền/tài liệu bên thứ 3: báo ngay, cụ thể (thiếu gì — vì sao cần — lấy ở đâu — mức độ gấp), không tự lách qua hay đoán đại.
- User gõ "tôi muốn commit" → soát nội dung từ lần "tôi muốn commit" gần nhất đến hiện tại, viết commit message dạng `<loại>: <mô tả ngắn gọn>` (feat/fix/refactor/style/docs/test/chore), tách nhiều commit nếu có nhiều loại thay đổi khác nhau.
- **Môi trường:** Java 17 · Tomcat 10.1 · `jakarta.*` (không `javax.*`) · package gốc `com.gms` · Servlet chia package con theo vai trò.
- **Dùng lại trước khi viết mới:** tra Phần 12 (util Java + `common.js`); file dùng chung chỉ Leader sửa; quy ước kỹ thuật ở Phần 13; mâu thuẫn đang chờ chốt ở `docs/notes.md`.
- **Frontend — Design Tokens (siêu quan trọng):** mọi CSS (kể cả `common.css` và CSS riêng từng màn) bắt buộc dùng biến từ `tokens.css` (`var(--color-primary)`, `var(--space-3)`...) — **cấm hard-code** mã màu/px/font trực tiếp dưới mọi hình thức. Chi tiết đầy đủ (Rule Layout Header/Sidebar/Footer, cấu trúc CSS 3 lớp, Mega Prompt Ảnh→JSP/CSS/JS) ở `frontend-design-pipeline-SWP391-v4.md` và `shared-library-guide.md`.

## Phần 12: Thư Viện Dùng Chung — Dùng Lại, Không Viết Lại

Trước khi viết bất kỳ hàm tiện ích nào, AI **bắt buộc** kiểm tra đã có sẵn chưa. Có rồi thì dùng, không viết bản thứ 2.

| Cần | Dùng (Backend) | Dùng (Frontend — `common.js`) |
|---|---|---|
| Kết nối DB | `DBContext.getConnection()` | — |
| Kết quả Service / 1 trang danh sách / JSON AJAX | `ServiceResult`, `PageResult`, `JsonResponse` + `JsonUtil.writeJson` | `sendAjaxRequest`, `getJson`, `postData`, `submitFormAjax` |
| Hash mật khẩu | `PasswordUtil.hashPassword / checkPassword` | — |
| Kiểm tra dữ liệu (DBR) | `ValidationUtil.*` | `isValid*`, `form[data-validate]` + `data-rule` |
| Câu thông báo MSG01…61 | `Messages.MSGxx_...` | `MESSAGES.MSGxx_...` + `formatMessage` |
| Ngày giờ (UTC+7, dd/MM/yyyy) | `DateTimeUtil.now() / today() / format*` — cấm `LocalDate.now()` trực tiếp | `formatDate`, `formatTime`, `formatDateTime` |
| Tiền, VAT (long, không double) | `MoneyUtil.formatMoney / calculateVatAmount` · JSP: `${gms:formatMoney(x)}` | `formatMoney` |
| Tham số request, forward/redirect, toast sau redirect | `RequestUtil.*`, `PaginationUtil.*` | `showFlashToastFromPage` (tự chạy) |
| Tìm kiếm không dấu | `SqlUtil.toContainsPattern` + `COLLATE Vietnamese_CI_AI` | `normalizeSearchText` |
| Mã OTP / token link | `SecureCodeUtil.*` | — |
| Đăng xuất mọi thiết bị | `SessionRegistryListener.invalidateSessionsOfAccount` | — |
| Toast, hộp xác nhận, đếm ngược, tab, nút con mắt, cảnh báo chưa lưu | — | `showToast`, `openConfirmDialog` / `data-confirm-message`, `data-countdown-seconds`, `bindTabs`, `data-password-toggle`, `data-unsaved-warning` |

**File dùng chung chỉ Leader sửa:** `pom.xml`, `agents.md`, `tokens.css`, `common.css`, `common.js`, `util/*`, `filter/*`, `constant/*`, `dto/*`, `web.xml`, `jsp/common/*`, `database/1_Init_Schema.sql`, `database/tools/*`. Khi hỗ trợ thành viên khác mà cần sửa các file này, AI phải **nói rõ đây là file dùng chung** và đề xuất báo Leader thay vì sửa thẳng.

## Phần 13: Quy Ước Kỹ Thuật Đã Chốt

- **Session:** chỉ lưu `SessionKey.ACCOUNT_ID`, `SessionKey.ROLE` (vai trò ĐANG DÙNG, giá trị `Role.getDbValue()`), `SessionKey.AVAILABLE_ROLES`, `SessionKey.DISPLAY_NAME`, và 3 key `PENDING_CONTACT_*` khi đổi email/SĐT; timeout `SessionKey.SESSION_TIMEOUT_SECONDS` (7200). Không tự gõ chuỗi tên attribute.
- **1 người nhiều vai trò (DBR-65, CBR-113):** 1 Person = 1 tài khoản; ≥ 2 vai trò → `/select-role`. Mọi phân quyền theo `SessionKey.ROLE`, **không** theo việc Person có hồ sơ gì. Nhân viên không được thao tác trên hồ sơ khách của chính mình → gọi `SelfServiceGuard` (CBR-115).
- **Filter:** không gọi `request.getParameter()` trong Filter (để UTF-8 luôn được đặt trước). Filter vai trò tự kiểm tra cả đăng nhập vì `@WebFilter` không đảm bảo thứ tự.
- **Post/Redirect/Get:** POST thành công → `RequestUtil.setFlashToast(...)` + `RequestUtil.redirect(...)`; POST lỗi → set lại dữ liệu đã nhập + `errors` (map từ `result.getFieldErrors()`) vào request rồi forward lại form (GBR-22 giữ dữ liệu người dùng).
- **AJAX:** Servlet trả đúng khuôn `JsonResponse` (`success`, `message`, `data`, `errors`); dữ liệu gửi lên dạng form nên Servlet đọc bằng `getParameter()`; có upload file thì Servlet thêm `@MultipartConfig`.
- **Tham số nghiệp vụ P-01…P-21:** luôn đọc qua `SystemConfigService`, không hard-code số (20 phút, 5 lần, 15:00, 70%...). Đơn hàng lưu bản sao giá trị tại thời điểm tạo (GBR-04, DBR-50).
- **Tiền:** Java `long` (đọc bằng `rs.getLong`), SQL theo schema hiện tại là `INT`; không dùng `double`/`float`.
- **Ngày giờ:** Java `LocalDate`/`LocalTime`/`LocalDateTime`, SQL `DATE`/`TIME`/`DATETIME2`; JSON gửi dạng ISO, trình duyệt tự định dạng.
- **Trạng thái / giá trị liệt kê:** theo đúng `NamingConvention.md` mục 2b (enum `UPPER_SNAKE("DbValue")` + `fromDbValue`, cấm `name()`, cấm so chuỗi). Bảng giá trị chuẩn nằm ở đó. Hiển thị tiếng Việt; màu badge theo GBR-28 (`badge-success/warning/danger/neutral`).
- **Mọi thao tác nhạy cảm** (đổi giá, thao tác tài khoản, xóa, duyệt/từ chối) gọi `AuditLogService` (GBR-07, GBR-08), ghi kèm vai trò đang dùng (`ActingRole` — CBR-112); thao tác tiêu cực bắt buộc lý do 5–500 ký tự (GBR-09).
- **Job chạy ngầm:** chỉ gọi Service, chọn bản ghi theo trạng thái, mỗi bản ghi 1 transaction, ghi AuditLog với người thực hiện = System (SRS 5.0).

## Phần 14: Code Theo Đợt Như Người Thật (Wave Coding) — BẮT BUỘC

**Vì sao:** sinh nguyên file / nguyên tính năng một lần thì người học không theo kịp, không kiểm soát được, review rất khó, sai một chỗ là sai dây chuyền.
AI phải code giống một lập trình viên thật: **dựng khung khắp các file trước, rồi đi qua lại giữa các file, mỗi lần vài dòng, đợt nào cũng compile được.**

### 14.1. Trước khi viết dòng code đầu tiên
1. Đọc `docs/handoff/<module>.md` (nếu có) và các rule liên quan.
2. In **bảng kế hoạch**: file nào (tạo mới / sửa), method nào, rule nào, chia sẵn thành các đợt. Chờ user gõ **"ok"** hoặc **"tiếp"**.

### 14.2. Các đợt (hết mỗi đợt dừng lại, user gõ "tiếp" mới làm đợt sau)

| Đợt | Làm gì | Giới hạn |
|---|---|---|
| **0 — Khung** | Tạo / mở mọi file cần đụng: class, chữ ký method (thân `throw new UnsupportedOperationException("TODO")`), Javadoc 1 câu, các dòng `// B1: … // B2: …` theo luồng trong handoff. **Chưa viết logic.** Phải compile được. | Mỗi method chỉ chữ ký + khung bước |
| **1 — DAO** | Điền **từng câu SQL**, mỗi lượt 1 method. | ≤ 40 dòng / file / lượt |
| **2 — Service** | Điền từng bước `Bn` theo thứ tự; cần DAO chưa có thì quay lại DAO viết đúng method đó rồi quay về. | ≤ 40 dòng / file / lượt |
| **3 — Servlet** | doGet / doPost nối Service ↔ JSP, PRG, AJAX. | |
| **4 — JSP / JS / CSS** | Khối giao diện theo thứ tự trên → dưới của màn. | |
| **5 — Kiểm** | Chạy theo case trong `docs/database/SEED_CASES.md` + JUnit cho Service; liệt kê case đã thử. | |

### 14.3. Trong mỗi lượt
- **Viết chỗ gọi trước, rồi mới viết hàm được gọi** (lần theo luồng như người thật). Nhảy giữa các file, không viết trọn 1 file rồi mới sang file khác.
- Hết lượt: liệt kê ngắn **file + method đã đụng + rule đã áp**, nhắc compile. Không dán lại cả file nếu chỉ sửa vài dòng.
- Không trộn: vừa đổi tên hàng loạt vừa thêm logic mới trong cùng lượt.
- Gặp mâu thuẫn rule / DB → dừng theo Phần 4 / Phần 5, không tự đoán.
- User gõ **"làm hết luôn"** thì được gộp các đợt còn lại của **đúng module đó**, nhưng câu trả lời vẫn giữ thứ tự Đợt 0 → 5.

### 14.4. Commit gợi ý
Mỗi đợt 1 commit (`feat(<module>): đợt 1 DAO ...`) để dễ quay lại.

## Phần 15: Quyết Định Đã Chốt v25 (03/10/2026)

- Mô hình danh tính: 1 Person = 1 SĐT + 1 email + 1 tài khoản; Customer / Employee là vai trò; ≥ 2 vai trò → chọn vai trò (DBR-65, CBR-113).
- Đăng ký online bằng SĐT đã có tài khoản (kể cả nhân viên) → chỉ báo "đã có tài khoản"; gắn vai trò mới vào Person cũ chỉ làm tại quầy / Admin (CBR-26, CBR-114).
- Chỉ Admin đổi vị trí nhân viên và khóa / mở tài khoản (CBR-109).
- Tài khoản Lễ tân tạo không có mật khẩu, không gửi link; khách đặt qua Quên mật khẩu (CBR-24, CBR-31).
- Schema v25 + seed v25 thay toàn bộ dữ liệu cũ (`docs/database/`).

