# File notes — mâu thuẫn tài liệu & quyết định đã chốt

Theo agents.md Phần 3, 4, 7.3: mọi chỗ SRS/SDS/prototype/chat nói khác nhau được ghi ở đây, kèm trạng thái.
Khi nhóm chốt xong 1 mục → đổi trạng thái thành **ĐÃ CHỐT**, ghi ngày + phương án, và sửa lại tài liệu gốc cho khớp.

## A. Mâu thuẫn đang CHỜ NHÓM CHỐT (phát hiện 24/09/2026)

| # | Chủ đề | Nguồn 1 | Nguồn 2 | Đề xuất | Tạm thời trong code |
|---|---|---|---|---|---|
| 1 | Độ dài mật khẩu | SRS 1.2.2: **6–64** ký tự | DBR-02 + prototype `dang-ky.js`: **8–64**, có chữ và số | Chốt 8–64 có chữ + số (an toàn hơn, 2/3 nguồn đồng ý) → sửa SRS 1.2.2 | `PASSWORD_MIN_LENGTH = 8` ở `AppConstants.java` và `common.js` |
| 2 | Hạn nộp đơn nghỉ phép | P-12: **15:00** ngày hôm trước | Prototype `pt/nghi-phep.html`: "trước **23:59** ngày hôm trước" | Theo SRS (15:00, Manager cấu hình được) → sửa câu gợi ý trên prototype | Đọc P-12 từ SystemConfig, không hard-code |
| 3 | Thời hạn link thanh toán VNPay | System Parameters: **5 phút** (không cấu hình) | JOB-06: "mặc định **10 phút** (DC-12)" | Chốt 1 con số, sửa lại bên còn lại | `AppConstants.PAYMENT_LINK_VALID_MINUTES = 5` |
| 4 | Đăng ký tài khoản | SRS 1.2: **2 bước** (kiểm tra SĐT → nhập thông tin) | Prototype (PROTOTYPE_CHANGES #2): **gộp 1 form** | Chọn 1 và cập nhật bên còn lại; nếu gộp form thì vẫn phải chạy UC-IDENT trước khi tạo tài khoản | Chưa code màn này |
| 5 | Bảng Person | DBR-22, 23, 27, 28, 30 nói SĐT/email "lưu trên **Person**" | SRS 3.2 không có entity Person | **Đã rõ theo DB:** `1_Init_Schema.sql` có bảng `Persons` (khớp DBR) → cần sửa SRS 3.2 cho khớp (thêm Person, bỏ CustomerPhone) | Code theo DB |
| 6 | Ngôn ngữ thông báo | SRS III.5: câu MSG viết **tiếng Anh** | GBR-01: giao diện **tiếng Việt** | Giữ mã MSG, hiển thị bản dịch tiếng Việt | Đã dịch trong `Messages.java` + `common.js` — nhờ nhóm duyệt câu chữ |
| 7 | Xuất file giao dịch | SRS 7.4.2: xuất **Excel** | SRS 12.1: xuất **CSV** | Giữ đúng từng màn: 7.4.2 xuất .xlsx (Apache POI đã có trong pom), 12.1 xuất CSV | — |
| 8 | Mã OTP khi đăng ký | Account giữ "verification code, code expiry" | Lúc đăng ký **chưa có Account** để lưu mã | **Đã giải quyết trong DB:** bảng `VerificationCodes` (AccountId cho phép NULL) → sửa SRS 3.2 mục Account | Code theo DB |
| 9 | Múi giờ | SRS 5.0 ghi "Asia/**Ha_Noi**" | ID này không tồn tại trong Java/IANA | Dùng `Asia/Ho_Chi_Minh` (cùng UTC+7) → sửa SRS | `DateTimeUtil.GYM_ZONE` |
| 10 | SRS còn thiếu mã thông báo | DBR-01, 02, 03, 04 có luật | III.5 không có câu báo lỗi tương ứng | Bổ sung mã MSG vào SRS | Tạm dùng `EXTRA_*` trong `Messages.java`/`common.js` |

## D. Rà soát database `1_Init_Schema.sql` + `2_Seed_Data.sql` (24/09/2026) — CHỜ NHÓM CHỐT

Theo agents.md Phần 5: **chưa ai sửa schema**. Trước khi sửa: copy schema hiện tại vào Notion → nhóm chốt phương án → Leader viết file `database/migrations/NNN_...sql`.

### D1. Ảnh hưởng ngay tới việc code (ưu tiên cao)

| # | Vấn đề | Căn cứ | Đề xuất |
|---|---|---|---|
| D1.1 | Hash mật khẩu trong seed là chuỗi giả → không đăng nhập được | codeImplementationBackend Mục 11, BCrypt | **Đã có** `database/3_Dev_Passwords.sql` (chỉ đổi dữ liệu mẫu, mật khẩu `Gms@2026`) |
| D1.2 | 1 Account gắn 1 Person, nên người vừa là PT vừa là khách (Person 22) dùng **1 tài khoản cho 2 vai trò** | SRS 3.2 Account: "Customer **hoặc** Employee (never both)"; session hiện lưu 1 vai trò | Chọn 1: (a) giữ DB, màn đăng nhập hỏi "vào với vai trò nào" khi Person có cả 2 hồ sơ; (b) tách tài khoản theo vai trò. Em nghiêng (a) — khớp DBR-30 |
| D1.3 | Thời hạn gói lưu bằng **ngày** (`DurationDays` 30/90/180/365) | GBR-03 + DBR-19 tính theo **tháng** (05/09 → 05/10; 31/01 → 28/02) | Đổi sang `DurationValue` + `DurationUnit` (Day/Month), hoặc `DurationMonths` + DayPass riêng |

### D2. Thiếu cột so với SRS/prototype

**24/09/2026 — Leader chốt và đã áp dụng 4 mục** (Employees ×2, VerificationCodes, LeaveRequests): sửa `1_Init_Schema.sql`, `2_Seed_Data.sql`, thêm `database/migrations/001_pt_profile_setup_token_leave_reminder.sql`. Còn lại MaintenanceRequests, Notifications, Orders — chờ chốt khi làm phase tương ứng.

| Bảng | Thiếu | Màn / luật cần |
|---|---|---|
| MaintenanceRequests | `RequestCode` (BT-xxxx), `Priority` (Urgent/Normal/Low), `WarningTagPlaced`, `AcceptanceNote` | SRS 8.2.1.1, 8.2.1.2; prototype `lt/bao-tri` |
| Notifications | `Audience` (All customers / Active members / PT / Receptionists / All staff), `ScheduledAt` | SRS 11.2.1 — gửi hẹn giờ còn cần thêm 1 job, SRS 5.3 chưa có |
| Orders | `PackageBenefitsSnapshot` | DBR-50 (giá + mô tả + **quyền lợi** copy vào đơn) |
| ~~Employees~~ ✅ | ~~`Specialty`, `YearsOfExperience`~~ — **đã thêm** (migration 001) | SRS 2.1.1 danh sách PT trang chủ; prototype `kh/dat-buoi-le` lọc theo chuyên môn |
| ~~Employees~~ ✅ | ~~`ResignationReason`~~ — **đã thêm**, bắt buộc khi Resigned (migration 001) | SRS 5.1.2 "Reason — required" |
| ~~VerificationCodes~~ ✅ | ~~Purpose `PasswordSetup` + `TokenHash`~~ — **đã thêm**, `Code` cho phép NULL (migration 001) | MSG38, P-15 — tài khoản Lễ tân tạo tại quầy |
| ~~LeaveRequests~~ ✅ | ~~`ReminderSentAt`~~ — **đã thêm** (migration 001) | JOB-07 nhắc Manager — không có cột này job chạy mỗi phút sẽ nhắc lặp liên tục |

### D3. Dữ liệu mẫu lệch luật (sửa trong `2_Seed_Data.sql`, không đụng schema)

- Khách 9, 10, 14 để `Active` nhưng chưa có gói nào Paid → phải là `New` (DBR-34, DBR-48).
- `Subscriptions.EndDate` tính lẫn 3 cách: theo tháng (Sub 1, 2, 3, 6, 7, 8, 12), theo ngày (Sub 4, 15), lệch 1 ngày (Sub 10, 11). Chốt D1.3 rồi tính lại.
- Sub 4 đang bảo lưu nhưng đã cộng sẵn ngày bảo lưu vào EndDate → khi JOB-01 mở lại sẽ cộng lần 2 (DBR-51 chỉ cộng khi kết thúc bảo lưu).
- `P-16 = '03,05,07,08,09'` (2 chữ số) trong khi code kiểm tra đầu số 3 chữ số; SRS còn ghi P-16 là cấu hình triển khai, không phải tham số Manager sửa → chốt 1 nơi lưu.
- `P-04 = '1,30,90,180,365'` (ngày, có gói 6 tháng) vs SRS P-04 "1 ngày, 1, 3, **9**, 12 tháng" vs form 4.2.2 ví dụ "6 tháng" vs prototype có "Gói 9 Tháng" → SRS tự mâu thuẫn, cần chốt danh sách.
- Đơn 17: buổi PT lẻ bán **tại quầy**, trong khi UC-37 ghi buổi lẻ chỉ đặt online.
- Gói PT sinh 22 buổi (`GeneratedSessionCount = 22`) nhưng seed chỉ tạo 6 buổi → số buổi còn lại hiển thị sẽ sai khi test.
- Seed cố định "hôm nay = 23/09/2026": chạy vào ngày khác thì đơn Pending, slot Held, khóa tạm đều đã quá hạn → JOB-05/06 sẽ hủy ngay khi app khởi động. Chấp nhận, hoặc viết script dời mốc ngày.

### D4. Ảnh (DBR-08)

- Cột đủ: `Persons.Avatar`, `Customers.CheckInPhoto`, `BlogPosts.CoverImage` (`StaffAttendances.CheckInPhoto` cho chấm công — chưa làm). PT ở trang chủ dùng chữ cái đầu tên, không cần ảnh (SRS 2.1.1).
- **Chưa có file ảnh thật:** seed trỏ `/uploads/checkin/kh-1001.jpg`, `/uploads/blog/squat.jpg`… nhưng không có file → cần: thư mục upload ngoài project (`app.upload-dir`), 1 Servlet trả ảnh có phân quyền (ảnh check-in chỉ nhân viên xem, ảnh blog công khai), ảnh bìa mặc định theo category (SRS 2.1.1) đặt trong `webapp/images/`, avatar mặc định = chữ cái đầu tên.

### D5. Nhỏ
- PK `CustomerDeletionRequests.DeletionRequestId` chưa đúng quy tắc `<TênBảngSốÍt>Id` (NamingConvention Mục 2) → `CustomerDeletionRequestId`.
- Tiền dùng `INT` (tối đa ~2,1 tỷ đ) — đủ cho gym; Java vẫn đọc bằng `long` (`rs.getLong`).
- `TimeSlots` chỉ chặn trùng giờ bắt đầu; chồng giờ (09:00 và 09:30) phải chặn ở Service (DBR-33).

## B. Quyết định kỹ thuật đã áp dụng (Leader, 24/09/2026)

| Quyết định | Lý do |
|---|---|
| Package gốc `com.gms` (thay cho ví dụ `com.project` trong tài liệu quy tắc) | Tên thật của dự án |
| Tomcat **10.1** + Java **17** + Jakarta EE 10 | Tomcat 10 dùng `jakarta.*`; 10.1 là bản đang được hỗ trợ |
| Servlet chia package con theo vai trò (auth, guest, customer, pt, receptionist, staff, manager, admin, payment) | ~70 Servlet — đúng ngoại lệ "1 layer quá nhiều class" trong agents.md Phần 2; khớp luôn với Filter phân quyền theo URL |
| Mỗi Filter vai trò tự kiểm tra cả đăng nhập | `@WebFilter` không đảm bảo thứ tự chạy, không được dùng web.xml để xếp thứ tự |
| Đăng xuất mọi thiết bị bằng `SessionRegistryListener` (RAM) | Không phải sửa database |
| Job chạy ngầm bằng `ScheduledExecutorService` của JDK | Không thêm thư viện (Quartz) |
| Kết nối DB bằng `DriverManager` qua `DBContext` | Đơn giản, đủ cho quy mô đồ án; đổi sang connection pool sau chỉ sửa 1 file |
| Bo góc md/lg giảm 19% (`--radius-reduce-ratio: 0.81`) | Yêu cầu của Leader trong chat |
| Khu Manager/Admin giữ bo góc nhỏ 6px + font Work Sans như mockup | Giữ đúng prototype từng vai trò |

## C. File tài liệu được `agents.md` nhắc tới nhưng chưa có trong repo

`frontend-design-pipeline-SWP391-v4.md`, `shared-library-guide.md` — nếu nhóm còn dùng, thêm vào thư mục gốc.
Hướng dẫn dùng tokens/common hiện có: `docs/frontend-shared-usage.md`.
