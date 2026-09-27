# 05 · Hỏi đáp bảo vệ — câu thầy cô hay "vặn" + danh sách lỗ hổng/việc còn thiếu

> Cách dùng: che cột trả lời, tự nói thành tiếng trong 30 giây, rồi so. Câu trả lời mẫu cố ý **ngắn – có từ khóa – có ví dụ**.
> Nguyên tắc khi bị hỏi cái chưa làm: **thừa nhận + nêu rủi ro + nêu cách sửa + ai/khi nào**. Thầy chấm cao người biết điểm yếu của hệ thống mình.

---

## A. Kiến trúc

| # | Câu hỏi | Trả lời mẫu |
|---|---|---|
| A1 | Hệ thống của em dùng kiến trúc gì? | Client–Server 3 tầng; tầng ứng dụng theo **MVC + Layered**: Filter → Servlet (Controller) → Service (nghiệp vụ) → DAO (SQL) → SQL Server; View là JSP. Tích hợp ngoài qua Gateway: Gmail SMTP, VNPay. |
| A2 | Vì sao tách Service với DAO, gộp lại cho nhanh? | Tách trách nhiệm: DAO chỉ SQL nên đổi DB/viết lại query không ảnh hưởng luật; Service giữ luật nên test được bằng DAO giả (`AuthServiceTest` truyền `AccountDAO` giả qua constructor). |
| A3 | Servlet của em có chứa nghiệp vụ không? | Không. VD `ForgotPasswordServlet` chỉ đọc tham số, lấy email/accountId **từ session**, gọi `PasswordRecoveryService`, rồi forward/redirect. Mọi `if` luật nằm ở Service. |
| A4 | `ServiceResult` để làm gì? | Kiểu trả về thống nhất: thành công/thất bại + câu thông báo + dữ liệu + **lỗi theo từng ô** → JSP biết ô nào đỏ (GBR-17). |
| A5 | Vì sao không dùng JWT như ảnh mẫu? | App render ở server bằng JSP, cùng domain → **HttpSession + cookie** là đủ, và **thu hồi phiên ngay được** (đổi MK → đăng xuất phiên khác). JWT stateless khó thu hồi trước hạn. |
| A6 | Filter chạy theo thứ tự nào? | `@WebFilter` **không đảm bảo thứ tự**. Nên `RoleAreaFilter` tự kiểm đăng nhập, và quy ước Filter không gọi `getParameter()` để encoding UTF-8 luôn được đặt trước lần đọc tham số đầu tiên. |
| A7 | Kể 3 design pattern trong phần em làm. | **Gateway** (`EmailGateway`), **Template Method** (`RoleAreaFilter.getAllowedRole`), **Observer** (`SessionRegistryListener`); ngoài ra DAO, DTO, PRG. |
| A8 | Cấu hình để ở đâu? | Bí mật (DB, App Password) ở `application.properties` — **gitignore**; tham số nghiệp vụ P-xx ở bảng `SystemConfigs` (Manager sửa, không cần build lại — GBR-04); hằng cố định ở `AppConstants`. |
| A9 | Kết nối DB thế nào, có pool không? | `DBContext.getConnection()` qua `DriverManager`, mỗi DAO mở/đóng bằng try-with-resources. Chưa có pool — đủ cho đồ án; đổi sang pool (HikariCP / Tomcat JDBC pool) chỉ sửa 1 file. |
| A10 | Transaction dùng ở đâu? | Đăng ký (Person → Customer → Account), gắn TK khi Claim, đặt lại MK (`updatePasswordHash` + `resetFailedLogin`). `setAutoCommit(false)` → `commit()` / lỗi `rollback()`. |
| A11 | Lỗi hệ thống hiển thị ra sao? | DAO ném `SQLException` → Service bắt, log, trả MSG04; lỗi không bắt được → `web.xml` chuyển trang 500 tự thiết kế, **không lộ stack trace**. |
| A12 | Vì sao dùng `@WebServlet` mà không khai báo `web.xml`? | 5 người cùng sửa 1 file XML sẽ conflict liên tục; annotation đặt ngay trên class, dễ tìm. `web.xml` chỉ còn khai báo trang lỗi. |

## B. Đăng nhập & phiên

| # | Câu hỏi | Trả lời mẫu |
|---|---|---|
| B1 | Vì sao sai email và sai mật khẩu báo cùng 1 câu? | Chống **account enumeration** — không cho kẻ xấu dò email nào có tài khoản. |
| B2 | Vì sao nhân viên không bị khóa? | BR-01: máy quầy dùng chung; khóa nhầm là cả ca không thu tiền/check-in được. Khách thì khóa 5 phút sau 5 lần sai. |
| B3 | Khóa tạm khác Admin khóa thế nào trong DB? | Khóa tạm = cột `LockedUntil` (tự hết); Admin khóa = `Status = Locked` (chỉ Admin mở). Khóa tạm **không đổi Status**. |
| B4 | Session fixation là gì, em chống ra sao? | Kẻ xấu cài sẵn JSESSIONID cho nạn nhân rồi chờ nạn nhân đăng nhập. `LoginServlet` **invalidate session cũ, tạo session mới** khi đăng nhập thành công → ID đổi. |
| B5 | Lưu gì trong session? | Chỉ `accountId`, `role`, `displayName` (+ key tạm của Quên MK, flash toast). **Không** lưu object Account có hash. Timeout 7200 s. |
| B6 | Đăng nhập xong quay về trang cũ thế nào, có an toàn không? | `AuthFilter` gắn `returnUrl`; `isSafeReturnUrl` chỉ nhận đường dẫn nội bộ: bắt đầu `/`, không `//`, không `\` → chống **open redirect**. |
| B7 | Làm sao đăng xuất "mọi thiết bị" khi Servlet API không có hàm tìm session theo user? | `SessionRegistryListener` nghe sự kiện gắn attribute `accountId` và hủy session, giữ `Map<accountId, Set<HttpSession>>` trong RAM; `invalidateSessionsOfAccount(id, keep)` hủy từng phiên. |
| B8 | Tomcat restart thì map đó mất, có sao không? | Không — restart thì mọi session cũng mất theo, nên không cần lưu DB. |

## C. Mật khẩu, BCrypt, mã OTP, link

| # | Câu hỏi | Trả lời mẫu |
|---|---|---|
| C1 | Vì sao BCrypt mà không MD5/SHA-256? | BCrypt **chậm có chủ đích** (cost 10 = 2^10 vòng, ~0,1 s) + **salt ngẫu nhiên** nằm trong chuỗi hash → brute-force và rainbow table rất tốn kém. MD5/SHA nhanh, GPU thử hàng tỷ lần/giây. |
| C2 | Salt lưu ở đâu? | Ngay trong chuỗi hash 60 ký tự `$2a$10$<22 ký tự salt><31 ký tự hash>` → không cần cột salt. |
| C3 | Kiểm tra MK thế nào khi không giải mã được? | `BCrypt.checkpw(raw, hash)` đọc salt + cost từ hash, băm lại raw rồi so. |
| C4 | Sao biết MK mới trùng MK cũ? | `checkPassword(newPassword, oldHash)` — khớp là trùng (MSG48). |
| C5 | Mã OTP 6 số chỉ 1 triệu khả năng, có dễ đoán không? | An toàn nhờ **giới hạn**: sai 5 lần hủy mã + khóa email 5'; mã sống 15'; gửi lại sau 60 s; xin tối đa 4 lần/giờ; sinh bằng `SecureRandom`. Xác suất đoán trúng trong 5 lần ≈ 5/1.000.000. |
| C6 | Vì sao `SecureRandom` mà không `Random`? | `Random` là thuật toán tất định, biết vài giá trị là đoán được giá trị sau; `SecureRandom` lấy entropy từ hệ điều hành. |
| C7 | Vì sao gửi email trước rồi mới ghi DB? | Gửi lỗi → không để dòng mã "ma" khiến người dùng bị bắt chờ 60 s cho mã chưa từng nhận; cũng là tinh thần API-03 "gửi lỗi thì không tạo gì". |
| C8 | Vì sao lấy email ở bước 2 Quên MK từ session? | Nếu tin form, kẻ xấu xin mã cho email mình rồi đổi sang email nạn nhân lúc xác minh. Session do server giữ. |
| C9 | Link đặt mật khẩu an toàn thế nào? | Token 32 byte ngẫu nhiên (43 ký tự Base64URL); DB chỉ lưu **SHA-256(token)**; dùng 1 lần (`UsedAt`); hạn 15'; **kiểm tra lại khi POST**. Lộ DB cũng không dựng lại link. |
| C10 | Sao token dùng SHA-256 mà MK lại dùng BCrypt? | Token vốn ngẫu nhiên 256 bit, không thể đoán → chỉ cần băm nhanh. MK do người đặt, entropy thấp → phải băm chậm. |
| C11 | Đặt lại MK xong chuyện gì xảy ra với phiên? | `invalidateSessionsOfAccount(id, null)` hủy **tất cả** phiên (CBR-93). Đổi MK trong Account Settings thì **giữ** phiên hiện tại. |
| C12 | Khách đang bị khóa 5' có Quên MK được không? | Được (chốt #4), đặt lại xong `resetFailedLogin` mở khóa luôn. Admin khóa thì chặn (MSG37). |

## D. Email

| # | Câu hỏi | Trả lời mẫu |
|---|---|---|
| D1 | Gửi mail bằng gì, cổng nào? | Jakarta Mail (Angus Mail 2.0.3) qua `smtp.gmail.com:587` **STARTTLS bắt buộc**, TLS 1.2/1.3, đăng nhập bằng **App Password**, timeout 10 s. |
| D2 | App Password là gì? | Mật khẩu 16 ký tự Google cấp riêng cho ứng dụng khi bật xác minh 2 bước; thu hồi được riêng, không lộ mật khẩu Gmail thật. |
| D3 | Không có Gmail thì nhóm test thế nào? | Chế độ DEV: App Password để `xxxx…` → email in ra console `[DEV EMAIL]`; hoặc SELECT mã trong `VerificationCodes`. |
| D4 | Vì sao thư có 2 phần text + HTML? | `multipart/alternative`: trình đọc thư chọn bản hiển thị được; ít bị Spam. HTML dùng style inline vì Gmail bỏ `<style>`. |
| D5 | Gửi email lỗi thì người dùng thấy gì? | `EmailGateway` ném `EmailSendException` → `VerificationCodeService` trả **MSG04**; DB không ghi mã. |

## E. Business rule & BA

| # | Câu hỏi | Trả lời mẫu |
|---|---|---|
| E1 | GBR, CBR, BR, DBR khác nhau thế nào? | GBR toàn hệ thống · CBR dùng chung ≥ 2 UC · BR thuộc 1 UC · DBR luật dữ liệu ERD không thể hiện · P-xx là con số của luật. |
| E2 | UC-IDENT và UC-Claim là gì? | UC-IDENT: kiểm trùng SĐT/email để rẽ nhánh đăng ký (gọi từ UC-05). UC-Claim: hồ sơ Lễ tân tạo sẵn, khách tự kích hoạt TK online bằng mã gửi tới email hồ sơ → không tạo hồ sơ trùng (GBR-12). |
| E3 | Đăng ký bằng SĐT đã có hồ sơ thì sao? | 4 nhánh: có TK → MSG51 · không email → MSG53 · email khác → MSG52 (che email) · email khớp → gửi mã Claim. |
| E4 | Tại sao 1 người vừa PT vừa khách là vấn đề? | DB 1 Person = 1 Account nhưng session chỉ giữ 1 vai trò; SRS nói TK thuộc khách **hoặc** NV. Đang chờ chốt: hỏi chọn vai trò khi đăng nhập. |
| E5 | Mâu thuẫn tài liệu em xử lý thế nào? | Ghi vào `docs/notes.md` + mục K của file 02: nguồn A, nguồn B, đề xuất, "tạm dùng" trong code; chốt xong sửa lại tài liệu gốc. VD mật khẩu 6–64 (SRS 1.2.2) vs 8–64 (DBR-02) → chốt 8–64. |
| E6 | Đề xuất CBR-108…113 để giải quyết gì? | Luật cũ chỉ nói khách được sửa gì (CBR-30); nhân viên chưa có luật. CBR-108/109 đối xứng cho NV; CBR-110 chốt mâu thuẫn #9 (đổi SĐT gửi mã về email hiện tại) và kiểm trùng 2 lần; CBR-111 báo email cũ khi NV đổi email; CBR-112 tab phụ thuộc chỉ khách; CBR-113 audit không ghi MK/mã; DBR-64 email duy nhất mọi TK; DBR-65 chốt avatar 2 MB (mâu thuẫn #3). Kéo theo: sửa Permission Matrix 4.4-A (dòng staff edit basic info, dòng Person edit avatar/email/phone), sửa BR-05, sửa DBR-08. |

---

## F. Lỗ hổng & việc còn thiếu (nói trước khi bị hỏi — ghi điểm)

| # | Mức | Vấn đề | Ở đâu | Rủi ro | Cách sửa | Gợi ý người làm |
|---|---|---|---|---|---|---|
| F1 | ✅ Đã sửa (`6f2511e`) | Kích hoạt hồ sơ (Claim) **không cần mã** | `RegistrationService.handleExistingPhone` → `claimExistingAccount` | Ai biết SĐT + email người khác là chiếm hồ sơ + gói tập của họ | Thêm bước OTP (Purpose Claim, gửi tới **email hồ sơ**), lưu "đơn chờ" trong session (MK đã hash), xác nhận mới tạo TK | Hoàng / Minh |
| F2 | ✅ Đã sửa (`6f2511e`) | Đăng ký mới **không xác minh email** | `RegistrationService.register` | Tạo TK bằng email người khác; vi phạm UC-05/API-03 | OTP SignUp như F1; kiểm trùng lại lúc xác nhận | Hoàng |
| F3 | ✅ Đã sửa (`6f2511e`) | Claim đổi Customer **New → Active** | `updateCustomerStatusToActive` | Sai DBR-34/CBR-01 (Active chỉ khi đã thanh toán) → sai check-in, báo cáo | Bỏ câu UPDATE | Hoàng |
| F4 | 🟠 | Đổi email/SĐT chưa nối servlet; khi nối phải **lưu giá trị mới chờ xác minh trong session** | `ContactChangeService.verifyAndApply(newValue)` nhận `newValue` từ ngoài | Nếu lấy từ form: xin mã cho email X rồi lưu email Y chưa xác minh | Servlet `/account/update-profile`, `/account/verify-change`; newValue lấy từ session; kiểm trùng lần 2 (CBR-110) | Vũ / Penguin336 |
| F5 | 🟠 | Tab Hồ sơ của PT, Lễ tân, Khách chưa tích hợp; tab phụ thuộc chưa có dữ liệu | `account-settings.jsp`, `configureRoleView` | Demo #4 thiếu | Include fragment còn lại; query Dependent theo `GuardianPhone/GuardianPhone2` | Vũ |
| F6 | 🟡 | P-17/P-18 viết cứng ở đăng nhập; `LocalDateTime.now()` thay vì `DateTimeUtil.now()` | `AuthService` | Manager đổi tham số không có tác dụng (GBR-04); server khác múi giờ → khóa lệch giờ | Đọc `SystemConfigService`; dùng `DateTimeUtil.now()` (UTC+7) | Nhân |
| F7 | ✅ Đã sửa (MSG35 dạng mm:ss) | Đồng hồ khóa đăng nhập làm tròn phút | `AuthService` B4 + `LoginServlet.extractLockSecondsLeft` | Lệch GBR-27 (mm:ss) | Trả số giây còn lại trong `LoginResult`/attribute thay vì parse câu thông báo | Nhân |
| F8 | 🟡 | Khác biệt MSG33 vs MSG34 lộ email là **khách có TK** | `AuthService` | Dò được email khách (MSG33 có "còn n lần") | Chấp nhận theo SRS, hoặc khi không tìm thấy TK vẫn trả câu dạng MSG33 | Nhóm chốt |
| F9 | 🟡 | MSG41 ở Quên MK lộ email có TK | `requestCode` B2 | Dò email | Đã chốt giữ theo luật — nêu được phương án "Nếu email tồn tại, mã đã được gửi" | — |
| F10 | 🟡 | Chưa có **CSRF token** cho form POST; `/logout` nhận cả GET | Toàn hệ thống | Trang lạ có thể gửi form thay người dùng | Token ngẫu nhiên trong session + hidden input, kiểm ở 1 Filter; `/logout` chỉ POST | Minh |
| F11 | 🟡 | Chưa có AuditLog (GBR-07, CBR-113) | `PasswordRecoveryService` (TODO), đổi email/SĐT | Không truy vết thao tác nhạy cảm | `AuditLogService.log(accountId, action, entity, field, old, new, reason)`; MK/mã để trống | Minh |
| F12 | 🟡 | Thiếu filter `/staff/*` | `filter/` | Khu dùng chung chưa phân quyền | `StaffAreaFilter` cho phép PT + Receptionist | Minh |
| F13 | 🟡 | Avatar: tin `contentType` trình duyệt, 5 MB, chưa kiểm px/cắt; lưu trong `getRealPath` (mất khi redeploy) | `AccountSettingsService.updateAvatar` | File giả ảnh; lệch DBR-65; mất ảnh khi deploy lại | Đọc bằng `ImageIO` (null = không phải ảnh), 2 MB, 200–2000 px, resize 256×256; thư mục upload ngoài webapp (`app.upload-dir`) | Vũ |
| F14 | 🟡 | Câu thông báo đổi MK viết cứng | `AccountSettingsService.changePassword` | Lệch `Messages` | Dùng MSG47/48/49, MSG66_PASSWORD_RULE | Vũ |
| F15 | 🟢 | `application.properties.example` bị xóa **lần 3** trên develop (commit `83b706b`) | `src/main/resources/` | Người mới không biết cấu hình | Khôi phục + nhắc nhóm không `git add -A` | Minh |
| F16 | 🟢 | Thiếu khóa `mail.app-password` → 500 thay vì chế độ DEV | `EmailGateway.send` B1 | Khó chịu khi cài máy mới | `AppConfig.get("mail.app-password", "")` | Minh |
| F17 | 🟢 | Link đặt MK: `markUsed` ngoài transaction | `setPasswordByLink` | 2 tab gửi cùng lúc có thể cùng qua | `UPDATE … WHERE UsedAt IS NULL` trong cùng TX, kiểm số dòng | Minh |
| F18 | 🟢 | Chưa có rate limit theo IP; chạy HTTP ở local | Hạ tầng | Brute-force nhiều TK; nghe lén khi deploy | Rate limit ở Filter; bật HTTPS + cookie `Secure`, `HttpOnly`, `SameSite` khi deploy | — |
| F19 | ✅ Đã sửa (`checkPhone` ở bước 1) | Bước 1 đăng ký chỉ kiểm định dạng SĐT, các nhánh UC-IDENT chạy ở bước 2 | `RegisterServlet.handlePhoneStep` | Khác SRS 1.2.1 (khách nhập hết thông tin mới biết SĐT đã có TK) | Gọi `PersonDAO.findByPhone` ngay ở bước 1 để rẽ nhánh sớm | Hoàng |
| F20 | 🟢 | Đầu số nhà mạng P-16 chưa kiểm | `ValidationUtil.hasValidPhonePrefix` chưa được gọi | SĐT `0123…` vẫn hợp lệ | Chốt nơi lưu P-16 rồi gọi | Nhóm |
| F21 | ✅ Đã làm | Màn nhập mã không cho biết còn mấy lần | `VerificationCodeService.getAttemptInfo` → `ForgotPasswordServlet.setAttemptHints` → JSP | Người dùng bị khóa bất ngờ | Hiện MSG62–65 (đã có trong SRS III.5) | Minh |
| F22 | ✅ Đã làm | Message code lệch SRS: 7 hằng `EXTRA_*` + nhiều câu viết cứng; MSG35 hiện "n phút" thay vì mm:ss | `Messages.java`, các Service/Servlet | Tài liệu và code không khớp | Đổi thành MSG66–75, bổ sung vào SRS (xem `07_DONG_BO_MESSAGE_SRS_CODE.md`); MSG35 dạng `04:32` | Minh |
| F23 | 🔴 Cao | Bước `claim-password` **không kiểm tra đã xác minh mã** | `RegisterServlet.handleClaimPasswordStep` lấy `phone` từ ô ẩn rồi gọi `claimAccount` | POST thẳng `step=claim-password` + SĐT người khác là chiếm hồ sơ | Sau `verifyCode` thành công, lưu `claimVerifiedPhone` vào **session**; bước đặt MK chỉ đọc SĐT từ session, dùng xong xóa | Hoàng |
| F24 | 🟠 | Mật khẩu gốc nằm trong `<input type="hidden" name="password">` ở trang nhập mã SignUp | `register.jsp` (bước `otp`) | Lộ MK qua View Source / cache trình duyệt; client sửa được giữa chừng | Lưu "đơn đăng ký chờ" trong session (MK đã BCrypt) thay vì ô ẩn | Hoàng |

---

## G. Kịch bản thuyết trình 3 phút (Authentication)

1. **(20 s) Phạm vi:** "Phase 1 gồm 4 màn xác thực #1–4, 7 use case: UC-01…05, UC-IDENT, UC-Claim, và tích hợp Email API-03."
2. **(40 s) Kiến trúc:** chỉ vào sơ đồ: Client (JSP/CSS/JS) → Filter → Servlet → Service → DAO → SQL Server; EmailGateway ra Gmail SMTP. "Servlet mỏng, Service dày, DAO chỉ SQL."
3. **(40 s) Bảo mật:** BCrypt cost 10; OTP SecureRandom + 5 lần/5 phút/15 phút/60 giây; token link chỉ lưu SHA-256; session fixation; open redirect; PreparedStatement chống SQL injection; `<c:out>` chống XSS.
4. **(40 s) Demo Quên mật khẩu:** gửi mã (chỉ console DEV / Gmail thật) → nhập sai 1 lần (MSG43) → nhập đúng → đặt MK → tab kia bị đăng xuất.
5. **(40 s) Điểm còn thiếu + kế hoạch:** F1–F2 (OTP đăng ký — bảo mật), F4–F5 (Account Settings), F11 (AuditLog); các đề xuất CBR-108…113 đã chuẩn bị để chốt với nhóm.
