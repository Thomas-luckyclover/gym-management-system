# Ôn tập Phase 1 — Authentication · Bắt đầu ở đây

> Bộ tài liệu này giúp bạn nắm **kiến trúc + business rule + code Authentication** của GMS trong 2 ngày,
> để tự tin review/bảo vệ trước giáo viên — **không cần đọc hiểu từng dòng code**.
> Viết theo code thật trên nhánh `develop` (commit `1f25647`, 26/09/2026), SRS G5 (bản trên Drive) và bộ luật v24 (GBR/CBR/DBR/BR/P-xx).

---

## 0. Đọc theo thứ tự này

| # | File | Học gì | Thời lượng |
|---|---|---|---|
| 0 | `00_BAT_DAU_O_DAY.md` (file này) | Bức tranh 1 trang + lộ trình 2 ngày | 20 phút |
| 1 | `01_KIEN_TRUC_TONG_THE.md` | Hệ thống gồm những khối nào, 1 request đi qua đâu, vì sao thiết kế như vậy | 2 giờ |
| 2 | `02_BUSINESS_RULES_PHASE1.md` | Toàn bộ luật Phase 1 **xếp theo luồng màn hình**, có mẹo nhớ | 3 giờ |
| 2b | `02b_DECISION_TABLES.md` ⭐ | **12 decision table**: đăng nhập, đăng ký, xin/nhập mã, quên MK, link, đổi MK, đổi email/SĐT, truy cập URL — mỗi dòng 1 trường hợp + ví dụ seed | 2 giờ (học cái này trước nếu đang quá tải) |
| 3 | `03_DEEP_DIVE_EMAIL_BCRYPT_RECOVERY.md` | Hàm nào gọi hàm nào: Email · BCrypt · Quên mật khẩu · Đặt mật khẩu qua link | 3 giờ |
| 3b | `03b_TRUY_VET_MAN_HINH_HAM_RULE.md` ⭐ | **Đúng yêu cầu của thầy**: Màn → nút → Servlet → Service (các bước) → DAO → bảng DB → Rule → Use case → màn tiếp theo; + ma trận truy vết ngược Rule→hàm, UC→màn | 2 giờ |
| 4 | `04_KICH_BAN_TEST_AUTH.md` | ~150 ca test toàn bộ Authentication, có dữ liệu seed + câu SQL kiểm tra | làm tay 3–4 giờ |
| 5 | `05_HOI_DAP_BAO_VE.md` | Câu hỏi khó giáo viên hay hỏi + câu trả lời mẫu + danh sách lỗ hổng/việc còn thiếu | 2 giờ |
| 7 | `07_DONG_BO_MESSAGE_SRS_CODE.md` 🆕 | Bảng MSG SRS ↔ code: MSG62–65 đã code, MSG66–77 cần thêm vào SRS, danh sách đổi tên `EXTRA_*` | 20 phút |
| 6 | `so-do/GMS_Project_Architecture.drawio` + `so-do/GHI_CHU_SO_DO.md` | Sơ đồ kiến trúc kiểu vẽ tay (mở bằng draw.io) + lời thuyết trình từng khối | 1 giờ |

---

## 1. Lộ trình 2 ngày (chống burnout)

Nguyên tắc: **học theo luồng, không học theo file**. Mỗi buổi chỉ giữ 1 câu hỏi trong đầu.

### Ngày 1 — "Hệ thống trông như thế nào?"

| Buổi | Việc | Kết quả phải đạt |
|---|---|---|
| Sáng 1 (1,5h) | Đọc `01` mục 1–4. Mở `so-do/*.drawio`, tự chỉ tay vào từng khối nói 1 câu | Vẽ lại được sơ đồ 4 tầng trên giấy trong 3 phút |
| Sáng 2 (1,5h) | Đọc `01` mục 5–8 (vòng đời request, filter, session, PRG) | Kể lại được "bấm Đăng nhập thì chuyện gì xảy ra" |
| Chiều 1 (2h) | Đọc `02` phần A–C (Đăng nhập, Đăng ký, Mã OTP) | Thuộc bảng "5 con số": 5 lần · 5 phút · 15 phút · 60 giây · 5 lần/giờ |
| Chiều 2 (1,5h) | Đọc `02` phần D–G (Quên mật khẩu, Link, Cài đặt TK, đề xuất CBR-108…113) | Trả lời được "khách được sửa gì, nhân viên được sửa gì" |
| Tối (1h) | Tự làm **Phiếu tự kiểm tra 1** cuối file này | ≥ 8/10 câu |

### Ngày 2 — "Code chạy như thế nào? Có đúng luật không?"

| Buổi | Việc | Kết quả phải đạt |
|---|---|---|
| Sáng 1 (2h) | Đọc `03` phần 1 (Email) + phần 2 (BCrypt) | Kể được 5 bước `EmailGateway.send()` và vì sao BCrypt "chậm có chủ đích" |
| Sáng 2 (1,5h) | Đọc `03` phần 3–4 (Quên mật khẩu 3 bước + Set password) | Vẽ lại được chuỗi gọi hàm của bước 2 (verify) |
| Chiều 1 (2–3h) | Chạy `04`: làm tay nhóm ca 🔥 (ưu tiên cao) | Ghi kết quả thật vào cột KQ |
| Chiều 2 (1,5h) | Đọc `05` — tập trả lời thành tiếng | Trả lời trơn 10 câu khó nhất |
| Tối (30p) | Làm **Phiếu tự kiểm tra 2** | ≥ 8/10 câu |

Mẹo chống quá tải: khi thấy 1 hàm dài, **chỉ đọc các dòng comment `// B1`, `// B2`…** — cả nhóm đã viết code theo kiểu "bước đánh số", đọc comment là đủ hiểu luồng.

---

## 2. Bức tranh 1 trang (học thuộc trang này trước)

```
                ┌────────────── TRÌNH DUYỆT (JSP đã render + CSS + JS) ──────────────┐
                │  login.jsp · register.jsp · forgot-password.jsp · set-password.jsp │
                │  account-settings.jsp   ·   common.js (validate, toast, đếm ngược) │
                └───────────────────────────────┬────────────────────────────────────┘
                                                │ HTTP (form POST / GET, AJAX JSON)
┌───────────────────────────────── TOMCAT 10.1 · JDK 17 · Jakarta EE 10 ───────────────────────────────┐
│ FILTER  CharacterEncodingFilter (UTF-8) → AuthFilter (đăng nhập chưa?) → RoleAreaFilter (đúng vai?) │
│ SERVLET LoginServlet · LogoutServlet · RegisterServlet · ForgotPasswordServlet · SetPasswordServlet │
│         AccountSettingsServlet         ← "Controller": đọc form, gọi Service, chọn JSP / redirect   │
│ SERVICE AuthService · RegistrationService · PasswordRecoveryService · VerificationCodeService       │
│         AccountSettingsService · ContactChangeService · SystemConfigService   ← MỌI LUẬT NẰM Ở ĐÂY  │
│ DAO     AccountDAO · PersonDAO · CustomerDAO · VerificationCodeDAO · SystemConfigDAO  ← CHỈ SQL      │
│ UTIL    PasswordUtil (BCrypt) · SecureCodeUtil (SecureRandom, SHA-256) · ValidationUtil · RequestUtil │
│ LISTENER SessionRegistryListener (AccountId → các phiên đang mở)                                    │
│ INTEGRATION EmailGateway ───────── SMTP 587 + STARTTLS ─────────► smtp.gmail.com ──► hộp thư khách   │
└──────────────────────────────────────────────┬──────────────────────────────────────────────────────┘
                                               │ JDBC (PreparedStatement)
                          SQL Server "GMS": Persons · Accounts · Customers · Employees
                                            VerificationCodes · SystemConfigs · AuditLogs
```

**5 câu nói trong 30 giây với giáo viên:**
1. GMS là web **MVC nhiều tầng** chạy trên Tomcat: Servlet (Controller) → Service (nghiệp vụ) → DAO (SQL) → SQL Server; JSP là View.
2. **Mọi luật nằm ở Service**, Servlet chỉ điều phối, DAO chỉ SQL — trả kết quả thống nhất bằng `ServiceResult<T>`.
3. Bảo mật tài khoản: mật khẩu **BCrypt** (có salt, 2^10 vòng), mã OTP 6 số sinh bằng **SecureRandom**, link đặt mật khẩu chỉ lưu **SHA-256 của token**.
4. Chống dò/brute-force: khóa khách sau **5 lần sai / 5 phút**, OTP **hết hạn 15 phút**, sai 5 lần hủy mã + khóa email, gửi lại sau **60 giây**, lần xin thứ 5 trong 1 giờ bị khóa.
5. Phiên đăng nhập: đổi session id khi login (chống **session fixation**), timeout 2 giờ, đổi mật khẩu → đăng xuất các phiên khác qua **SessionRegistryListener**.

---

## 3. Bảng "5 con số" và "3 chiếc khóa" — thuộc lòng

| Con số | Ý nghĩa | Nguồn luật | Trong code |
|---|---|---|---|
| **5 lần** | Sai mật khẩu (khách) / sai mã OTP → khóa | P-17, BR-01, CBR-94 | Login: hằng `MAX_FAILED_ATTEMPTS` (⚠ viết cứng) · OTP: đọc `P-17` |
| **5 phút** | Thời gian khóa tạm | P-18 | Login: hằng `LOCK_MINUTES` (⚠ viết cứng) · OTP: đọc `P-18` |
| **15 phút** | Hạn mã OTP và link đặt mật khẩu | P-15, BR-04 | `SystemConfigService.getInt("P-15")` |
| **60 giây** | Khoảng cách tối thiểu giữa 2 lần xin mã | "Not configurable" | `AppConstants.CODE_RESEND_GAP_SECONDS` |
| **5 lần / 60 phút** | Lần xin mã thứ 5 trong 1 giờ → khóa 5 phút | CBR-94 | `countCodesSentSince` + `AppConstants.CODE_REQUEST_WINDOW_MINUTES` |
| **8–64** | Độ dài mật khẩu, có ≥1 chữ + ≥1 số | DBR-02 | `ValidationUtil.isValidPassword` |
| **7200 s** | Session timeout (2 giờ) | — | `SessionKey.SESSION_TIMEOUT_SECONDS` |

| Chiếc khóa | Nằm ở cột | Ai khóa | Ai/khi nào mở |
|---|---|---|---|
| Khóa **vĩnh viễn** (Admin khóa / nghỉ việc) | `Accounts.Status` = `Locked` / `Inactive` | Admin (UC-07) | Chỉ Admin — **không** mở bằng Quên mật khẩu |
| Khóa **tạm** đăng nhập (khách sai 5 lần) | `Accounts.LockedUntil` | Hệ thống (BR-01) | Tự hết sau 5 phút · đặt lại mật khẩu · Admin mở sớm (BR-09) |
| Khóa **xác thực email** (OTP) | `VerificationCodes.LockedUntil` | Hệ thống (CBR-94) | Tự hết sau 5 phút |

---

## 4. Phiếu tự kiểm tra 1 (tối ngày 1)

1. Kể 4 tầng chính của backend theo đúng thứ tự request đi qua.
2. Vì sao Servlet không được chứa `if` nghiệp vụ?
3. `ServiceResult.failField("code", …)` khác `ServiceResult.fail(…)` ở chỗ nào, ảnh hưởng gì tới JSP?
4. Nhân viên sai mật khẩu 10 lần có bị khóa không? Vì sao SRS quy định vậy?
5. Khách đang bị khóa tạm 5 phút có dùng Quên mật khẩu được không? Tài khoản Admin khóa thì sao?
6. Đăng ký bằng SĐT đã có hồ sơ (chưa có tài khoản) nhưng nhập email khác thì hệ thống báo gì?
7. Mã OTP sai lần thứ 5 → chuyện gì xảy ra với mã và với email?
8. Vì sao DB chỉ lưu SHA-256 của token link đặt mật khẩu?
9. Đổi mật khẩu trong Cài đặt tài khoản: phiên nào bị đăng xuất, phiên nào giữ lại?
10. CBR-112 nói gì? Code đã làm chưa?

## 5. Phiếu tự kiểm tra 2 (tối ngày 2)

1. Kể chuỗi gọi hàm khi bấm "Gửi mã" ở Quên mật khẩu (từ Servlet tới Gmail).
2. Vì sao `VerificationCodeService.sendCode` **gửi email trước rồi mới INSERT**?
3. `BCrypt.gensalt(10)` — số 10 nghĩa là gì? Cùng mật khẩu băm 2 lần có ra cùng chuỗi không?
4. `checkpw` làm sao biết salt khi DB không có cột salt?
5. Vì sao bước 2 Quên mật khẩu lấy email **từ session** mà không lấy từ form?
6. Hạn làm bước 3 (đặt mật khẩu mới) tính thế nào?
7. Sau khi đặt lại mật khẩu, ai đăng xuất các phiên cũ? Bằng cách nào khi Servlet API không có hàm "tìm session theo user"?
8. Chế độ `[DEV EMAIL]` bật khi nào?
9. Nêu 3 chỗ code đang **lệch luật** mà bạn biết (và kế hoạch sửa).
10. Nêu 2 lỗ hổng bảo mật còn tồn tại và cách giảm thiểu.

Đáp án nằm rải trong các file 01–05 (tìm theo từ khóa).
