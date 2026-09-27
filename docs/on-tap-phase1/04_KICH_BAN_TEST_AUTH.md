# 04 · Kịch bản test toàn bộ luồng Authentication

> Mỗi ca test ghi rõ: **Tiền điều kiện · Thao tác · Dữ liệu · Kết quả mong đợi (theo LUẬT) · Luật · KQ**.
> Chỗ nào code hiện tại **khác luật**, ghi `⚠ Code hiện tại:` → ca đó **dự kiến FAIL** = bug cần báo, không phải bạn test sai.
> Ưu tiên: 🔥 bắt buộc chạy trước demo · ⭐ nên chạy · (trống) chạy khi còn thời gian.
> Cột **KQ**: điền `PASS` / `FAIL` / `BLOCK` (chưa có chức năng) sau khi chạy.

---

## 0. Chuẩn bị

1. Chạy lần lượt trong SSMS: `1_Init_Schema.sql` → `2_Seed_Data.sql` → `3_Dev_Passwords.sql` → `4_Phase1_Seed.sql`.
2. **Mật khẩu mọi tài khoản mẫu: `Gms@2026`.**
3. Ca có ⏱ phụ thuộc giờ chạy file 4 (khóa tạm, hạn mã ~15 phút) → quá giờ thì **chạy lại file 4** (file tự xóa mọi dòng Id ≥ 101 rồi chèn lại).
4. `application.properties`: để `mail.app-password=xxxx…` → **chế độ DEV**, mã in ra console Tomcat dạng `[DEV EMAIL] To: … Subject: …`. Hoặc xem mã bằng SQL bên dưới.
5. Dùng 2 trình duyệt (VD Chrome + Edge, hoặc 1 tab ẩn danh) cho các ca "phiên khác".
6. Mở DevTools (F12) → tab Application → Cookies để xem `JSESSIONID`; tab Network để xem mã 302/401/403.

### Câu SQL hay dùng khi test

```sql
-- Mã OTP mới nhất của 1 email
SELECT TOP 1 VerificationCodeId, Purpose, Code, SentAt, ExpiresAt, FailedAttempts, LockedUntil, UsedAt
FROM VerificationCodes WHERE Email = 'thao.luong@gmail.com' ORDER BY SentAt DESC;

-- Trạng thái đăng nhập của 1 tài khoản
SELECT a.AccountId, p.Email, a.Status, a.FailedLoginCount, a.LockedUntil, a.LastLoginAt, LEFT(a.PasswordHash, 7) AS HashPrefix
FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'quan.le92@gmail.com';

-- Khách vừa đăng ký
SELECT TOP 3 p.PersonId, p.FullName, p.Phone, p.Email, c.CustomerCode, c.CustomerType, c.Status, a.AccountId
FROM Persons p LEFT JOIN Customers c ON c.PersonId = p.PersonId LEFT JOIN Accounts a ON a.PersonId = p.PersonId
ORDER BY p.PersonId DESC;

-- Mở khóa thủ công để test lại
UPDATE Accounts SET FailedLoginCount = 0, LockedUntil = NULL WHERE AccountId = <id>;
UPDATE VerificationCodes SET LockedUntil = NULL WHERE Email = '<email>';
```

### Tài khoản nhanh

| Vai trò | Email | SĐT | Trang chủ sau đăng nhập |
|---|---|---|---|
| Admin | dang.nguyen@gms.vn | 0912000001 | `/admin/dashboard` |
| Manager | viet.tran@gms.vn | 0912000002 | `/manager/dashboard` |
| Lễ tân | tam.le@gms.vn | 0912000003 | `/receptionist/check-in` |
| PT | son.dinh@gms.vn | 0912000005 | `/pt/dashboard` |
| Khách (có 2 con) | an.nguyen95@gmail.com | 0901234501 | `/customer/dashboard` |

Các dashboard chưa code → trang **404** là đúng ở thời điểm này (quan trọng là URL đúng).

---

## 1. ĐĂNG NHẬP — `/login` (UC-01)

| ID | Ưu tiên | Tiền điều kiện / Thao tác | Dữ liệu | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|---|
| LG-01 | 🔥 | Đăng nhập bằng email | `an.nguyen95@gmail.com` / `Gms@2026` | Chuyển `/customer/dashboard`; `Accounts.LastLoginAt` = giờ hiện tại | UC-01 | |
| LG-02 | 🔥 | Đăng nhập bằng SĐT | `0901234501` / `Gms@2026` | Như LG-01 | UC-01 | |
| LG-03 | ⭐ | Email có khoảng trắng 2 đầu | `  an.nguyen95@gmail.com  ` | Đăng nhập được (identifier được trim) | Trim | |
| LG-04 | ⭐ | Email viết HOA | `AN.NGUYEN95@GMAIL.COM` | Đăng nhập được (SQL Server so sánh không phân biệt hoa thường) | — | |
| LG-05 | 🔥 | Admin | `dang.nguyen@gms.vn` | → `/admin/dashboard` | Role | |
| LG-06 | ⭐ | Manager / Lễ tân / PT | 3 email NV ở bảng trên | Đúng trang chủ từng vai trò | Role | |
| LG-07 | 🔥 | Bỏ trống cả 2 ô, bấm Đăng nhập | — | Trình duyệt chặn; nếu tắt JS: "Nhập email hoặc số điện thoại." + "Mật khẩu là bắt buộc." dưới 2 ô | MSG39, MSG09 | |
| LG-08 | ⭐ | Chỉ trống email/SĐT | — | MSG39 | MSG39 | |
| LG-09 | ⭐ | Chỉ trống mật khẩu | — | "Mật khẩu là bắt buộc." (không vỡ font) | MSG09 | |
| LG-10 | 🔥 | Email không tồn tại | `khongtontai@gmail.com` / bất kỳ | MSG34 "Email, số điện thoại hoặc mật khẩu không đúng. Vui lòng kiểm tra lại." — **không** nói "không tồn tại" | Chống dò TK | |
| LG-11 | ⭐ | Chuỗi không phải email, không phải SĐT | `abc` / bất kỳ | MSG34 | — | |
| LG-12 | 🔥 | Khách sai mật khẩu (đã sai sẵn 2 lần) | `quan.le92@gmail.com` / `sai123456` | "…Bạn còn **2** lần thử."; `FailedLoginCount` 2 → 3 | MSG33, BR-01 | |
| LG-13 | 🔥 | Khách sai lần thứ 5 (đã sai sẵn 4) | `thao.luong@gmail.com` / sai | MSG35 "Tài khoản bị khóa 5 phút…"; `LockedUntil` = now + 5' | BR-01 | |
| LG-14 | 🔥 | Ngay sau LG-13 nhập **đúng** mật khẩu | `thao.luong@gmail.com` / `Gms@2026` | Vẫn MSG35 + đồng hồ đếm ngược | BR-01, GBR-27 | |
| LG-15 | ⭐ ⏱ | Tài khoản đang khóa sẵn | `kim.vo@gmail.com` / `Gms@2026` | MSG35 "…Thử lại sau **mm:ss**" (VD `04:32`) + panel khóa + đồng hồ chạy lùi đúng từng giây | MSG35, GBR-27 | |
| LG-16 | 🔥 | Khóa đã hết hạn | `hong.pham@yahoo.com` / `Gms@2026` | Đăng nhập được; `FailedLoginCount = 0`, `LockedUntil = NULL` | BR-02 | |
| LG-17 | ⭐ | Khóa hết hạn + nhập SAI | `hong.pham@yahoo.com` / sai | Bộ đếm reset về 0 rồi +1 → "còn **4** lần thử" | BR-02 | |
| LG-18 | 🔥 | Lễ tân sai mật khẩu 6 lần liên tiếp | `tam.le@gms.vn` / sai ×6 | Lần nào cũng MSG34, **không bao giờ khóa**; `FailedLoginCount` không đổi | BR-01 | |
| LG-19 | ⭐ | Sau LG-18 nhập đúng | `tam.le@gms.vn` / `Gms@2026` | Vào `/receptionist/check-in` ngay | BR-01 | |
| LG-20 | 🔥 | Tài khoản Admin khóa | `phuc.le@gmail.com` / `Gms@2026` | MSG37 "Tài khoản này đã ngừng hoạt động…" | CBR-96 | |
| LG-21 | ⭐ | Nhân viên đã nghỉ | `nam.ho@gms.vn` / `Gms@2026` | MSG37 | CBR-96 | |
| LG-22 | ⭐ | Hồ sơ đã xóa (email đã nhả) | `tung.mai@gmail.com` / `Gms@2026` | MSG34 | DBR-28 | |
| LG-23 | 🔥 | TK Lễ tân tạo, chưa có mật khẩu | `khanh.trinh@gmail.com` / bất kỳ | Panel "Chưa thể đăng nhập" + MSG38 + nút "Đặt mật khẩu" | MSG38, CBR-31 | |
| LG-24 | ⭐ | Hồ sơ có, không có tài khoản | `lan.dang85@gmail.com` / bất kỳ | MSG34 (như sai) | CBR-17 | |
| LG-25 | ⭐ | Người vừa PT vừa khách | `trang.nguyen@gms.vn` / `Gms@2026` | Luật: hỏi chọn vai trò / chỉ email (chờ chốt). ⚠ Code hiện tại: vào luôn khu PT | UC-01, L15 | |
| LG-26 | 🔥 | Mật khẩu có khoảng trắng cuối | `an.nguyen95@gmail.com` / `Gms@2026 ` | MSG33 (mật khẩu **không** được trim) | DBR-02 | |
| LG-27 | ⭐ | Nút con mắt | bấm icon ở ô mật khẩu | Hiện/ẩn mật khẩu, bấm 1 lần là đổi | GBR-30 | |
| LG-28 | ⭐ | Đã đăng nhập mà mở lại `/login` | — | Tự chuyển về trang chủ của vai trò | — | |
| LG-29 | 🔥 | Nhấn F5 ở trang đích sau khi đăng nhập | — | Không hỏi "gửi lại biểu mẫu" (PRG) | PRG | |
| LG-30 | ⭐ | Sai mật khẩu xong | — | Ô email **giữ nguyên** giá trị, ô mật khẩu trống | GBR-22 | |

## 2. ĐĂNG XUẤT — `/logout` (UC-02)

| ID | Ưu tiên | Thao tác | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| LO-01 | 🔥 | Đang đăng nhập → bấm Đăng xuất | Về `/login`, toast "Đã đăng xuất." | MSG40 | |
| LO-02 | 🔥 | Sau LO-01 mở `/account` | Bị đưa về `/login?returnUrl=%2Faccount` | UC-02 | |
| LO-03 | ⭐ | Sau LO-01 bấm nút Back của trình duyệt rồi thao tác | Không thao tác được (về đăng nhập) | — | |
| LO-04 | | Gõ thẳng `/logout` (GET) trên thanh địa chỉ | Hiện tại: đăng xuất luôn. Ghi nhận: nên chỉ nhận POST (chống CSRF đăng xuất) | Bảo mật | |

## 3. PHIÊN & PHÂN QUYỀN (Filter, Session)

| ID | Ưu tiên | Thao tác | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| SS-01 | 🔥 | Chưa đăng nhập mở `/account` | 302 → `/login?returnUrl=%2Faccount` | GBR-31 | |
| SS-02 | 🔥 | Từ SS-01 đăng nhập An | Quay lại đúng `/account` | GBR-31 | |
| SS-03 | ⭐ | Chưa đăng nhập mở `/customer/dashboard?tab=x` → đăng nhập | Quay lại đúng URL **kèm query** | GBR-31 | |
| SS-04 | 🔥 | Khách mở `/manager/dashboard` | Trang 403 tự thiết kế | GBR-10 | |
| SS-05 | 🔥 | Admin mở `/customer/dashboard` | 403 | GBR-10 | |
| SS-06 | ⭐ | PT mở `/receptionist/check-in` | 403 | GBR-10 | |
| SS-07 | ⭐ | Chưa đăng nhập mở `/`, `/blog`, `/register`, `/forgot-password`, `/css/common.css` | Mở được bình thường | Whitelist | |
| SS-08 | 🔥 | `/login?returnUrl=https://evil.com` rồi đăng nhập | Về trang chủ vai trò, **không** sang evil.com | Open redirect | |
| SS-09 | 🔥 | `/login?returnUrl=//evil.com` | Như SS-08 | Open redirect | |
| SS-10 | ⭐ | `/login?returnUrl=/%5Cevil.com` (`/\evil.com`) | Như SS-08 | Open redirect | |
| SS-11 | 🔥 | Ghi JSESSIONID trước đăng nhập → đăng nhập → xem lại | 2 giá trị **khác nhau** | Session fixation | |
| SS-12 | ⭐ | Đăng nhập, để yên > 2 giờ (hoặc tạm sửa timeout) rồi thao tác | Bị yêu cầu đăng nhập lại | Timeout 7200 s | |
| SS-13 | ⭐ | Console trình duyệt khi chưa đăng nhập: `fetch('/gms/account',{headers:{'X-Requested-With':'XMLHttpRequest'}})` | HTTP 401, JSON có MSG06 | AJAX 401 | |
| SS-14 | | Khách gọi AJAX tới `/manager/...` | HTTP 403, JSON MSG07 | AJAX 403 | |
| SS-15 | | Mở `/staff/...` với vai trò không liên quan | Luật: 403. ⚠ Code hiện tại: chưa có filter `/staff/*` | GBR-10 | |
| SS-16 | ⭐ | Mở URL không tồn tại `/abcxyz` | Trang 404 tự thiết kế | — | |

## 4. ĐĂNG KÝ — `/register` (UC-05, UC-IDENT, UC-Claim)

### 4.1 Kiểm tra dữ liệu (DBR)

| ID | Ưu tiên | Dữ liệu (các ô khác hợp lệ) | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| RG-01 | 🔥 | Bước 1: SĐT `090123450` (9 số) | MSG13 | DBR-17 | |
| RG-02 | ⭐ | SĐT `1901234501` / `09012345ab` / `+84901234501` | MSG13 | DBR-17 | |
| RG-03 | 🔥 | SĐT hợp lệ `0987000001` | Sang bước 2, SĐT hiện chỉ đọc | SRS 1.2.1 | |
| RG-04 | ⭐ | Bước 2: sửa ô SĐT ẩn thành số sai (DevTools) rồi gửi | MSG13 (server kiểm lại) | GBR-13 | |
| RG-05 | 🔥 | Họ tên `A` | Lỗi họ tên 2–50 | DBR-01 | |
| RG-06 | ⭐ | Họ tên `Nguyễn Văn 2` / `@@@` | `MSG67_FULL_NAME_RULE` | DBR-01 | |
| RG-07 | ⭐ | Họ tên `Nguyễn Thị O'Neil-Hà` | Hợp lệ | DBR-01 | |
| RG-08 | ⭐ | Họ tên 51 ký tự | Lỗi 2–50 | DBR-01 | |
| RG-09 | 🔥 | Ngày sinh = ngày mai | MSG15 | DBR-04 | |
| RG-10 | ⭐ | Ngày sinh năm 1900 | `MSG69_DOB_TOO_OLD` | DBR-04 | |
| RG-11 | ⭐ | Ngày sinh 2018 (8 tuổi) | **Vẫn đăng ký được** | P-05 | |
| RG-12 | 🔥 | Email `abc@` / `abc.gmail.com` | MSG11 | DBR-18 | |
| RG-13 | | Email 101 ký tự | MSG11 hoặc MSG10 | DBR-18 | |
| RG-14 | 🔥 | Mật khẩu `abcdefgh` (không số) | `MSG66_PASSWORD_RULE` | DBR-02 | |
| RG-15 | 🔥 | Mật khẩu `12345678` (không chữ) | `MSG66_PASSWORD_RULE` | DBR-02 | |
| RG-16 | ⭐ | Mật khẩu `abc123` (6 ký tự) | `MSG66_PASSWORD_RULE` (SRS 1.2.2 ghi 6–64 nhưng nhóm chốt 8–64) | DBR-02, mâu thuẫn #1 | |
| RG-17 | ⭐ | Mật khẩu 65 ký tự | `MSG66_PASSWORD_RULE` | DBR-02 | |
| RG-18 | ⭐ | Mật khẩu `Mậtkhẩu1` (chữ có dấu + số) | Hợp lệ (`\p{L}` nhận chữ có dấu) | DBR-02 | |
| RG-19 | 🔥 | Nhập lại khác | MSG47 | — | |
| RG-20 | ⭐ | Gửi lỗi | Các ô (trừ mật khẩu) **giữ giá trị** | GBR-22 | |

### 4.2 Nhánh UC-IDENT

| ID | Ưu tiên | Dữ liệu | Kết quả mong đợi (LUẬT) | Luật | KQ |
|---|---|---|---|---|---|
| RG-21 | 🔥 | `0987000001` + `khach.moi@gmail.com` + thông tin hợp lệ | Gửi mã SignUp (seed `112233` ⏱) → nhập đúng → tạo Person + Customer (Self, **New**, `KH-nnnn`) + Account → `/login` + MSG54. ✅ Code đã có bước mã (commit `6f2511e`) | UC-05, API-03 | |
| RG-22 | 🔥 | Như RG-21 nhưng **nhập sai mã** | MSG43, ở lại bước nhập mã, không tạo tài khoản | UC-05 | |
| RG-23 | 🔥 | `0901234501` (An đã có TK) | MSG51 "…đã có tài khoản. Vui lòng đăng nhập hoặc dùng Quên mật khẩu." | CBR-26 | |
| RG-24 | 🔥 | `0935000105` + `yen.doan@gmail.com` | Gửi mã **Claim** tới `yen.doan@…` (seed `445566`) → đúng → gắn TK vào hồ sơ **KH-1105** có sẵn, không tạo Person mới. Màn hiện hồ sơ readonly (họ tên, ngày sinh) rồi mới đặt mật khẩu | CBR-20 | |
| RG-25 | 🔥 | Như RG-24 nhưng không nhập mã / nhập sai | **Không** được tạo tài khoản. ✅ qua giao diện; xem thêm RG-32 (đi tắt) | CBR-20 | |
| RG-26 | ⭐ | Sau RG-24 kiểm tra `Customers.Status` của KH-1105 | Vẫn **New** ✅ (đã bỏ `updateCustomerStatusToActive`) | DBR-34, CBR-01 | |
| RG-27 | 🔥 | `0935000105` + `yen.khac@gmail.com` | MSG52 với email che `ye***@gmail.com` | CBR-25 | |
| RG-28 | 🔥 | `0901234505` (hồ sơ không email) | MSG53 mời ra quầy | CBR-27 | |
| RG-29 | ⭐ | `0901234510` + `lan.dang85@gmail.com` | Nhánh kích hoạt (như RG-24) | CBR-20 | |
| RG-30 | ⭐ | `0901234512` (SĐT của hồ sơ đã xóa) + email mới | Tạo mới bình thường | CBR-23 | |
| RG-31 | ⭐ | `0935000999` (chỉ là số giám hộ) + email mới | Tạo mới bình thường | CBR-28 | |
| RG-32 | 🔥 | Dùng DevTools/Postman POST thẳng `/register` với `step=claim-password`, `phone=0935000105`, mật khẩu hợp lệ — **không** qua bước mã | Phải từ chối (MSG06/MSG44), không tạo tài khoản. ⚠ Code hiện tại: **lọt** (F23) | CBR-20 | |
| RG-33 | ⭐ | Ở bước nhập mã SignUp, View Source trang | **Không** được thấy mật khẩu vừa nhập. ⚠ Code: có trong `<input type="hidden" name="password">` (F24) | GBR-08 | |
| RG-32 | 🔥 | SĐT mới + `bich.tran@gmail.com` | MSG12 | DBR-23 | |
| RG-33 | ⭐ | SĐT mới + `linh.truong93@gmail.com` (email đã nhả) | Dùng được | DBR-28 | |
| RG-34 | ⭐ | Sau RG-21 đăng nhập bằng TK mới | Đăng nhập được, vào `/customer/dashboard` | — | |
| RG-35 | ⭐ | Sau RG-21 xem `Accounts.PasswordHash` | Bắt đầu `$2a$10$`, dài 60 | BCrypt | |
| RG-36 | | Đăng ký 2 lần liên tiếp cùng SĐT (2 tab bấm gần như cùng lúc) | Chỉ 1 hồ sơ được tạo (unique index chặn), tab kia MSG04/MSG14 | GBR-13 | |
| RG-37 | | Gửi mã đăng ký khi Gmail lỗi (App Password sai) | Luật: MSG04, **không** tạo gì. ⚠ Code: BLOCK | API-03 | |

## 5. MÃ XÁC THỰC DÙNG CHUNG (CBR-94) — test qua màn Quên mật khẩu

| ID | Ưu tiên | Tiền điều kiện / Thao tác | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| OT-01 | 🔥 | Xin mã cho `an.nguyen95@gmail.com` | Toast MSG42 "Mã đã được gửi tới an…@… hiệu lực 15 phút."; DB có dòng Recovery mới, `ExpiresAt = SentAt + 15'`, `Code` 6 số | P-15 | |
| OT-02 | ⭐ | Xem console Tomcat (chế độ DEV) | Có `[DEV EMAIL] To: an.nguyen95@gmail.com | Subject: [Super Gym] Mã xác thực khôi phục mật khẩu` + mã | Dev mode | |
| OT-03 | 🔥 ⏱ | `khoa.dinh@gmail.com` → bấm Gửi lại mã ngay | Nút mờ, đếm ngược tới đủ 60 s; nếu ép gửi: "Vui lòng chờ n giây nữa…" | CBR-94 | |
| OT-04 | ⭐ | Chờ đủ 60 s bấm Gửi lại | MSG45; mã mới khác mã cũ | CBR-94 | |
| OT-05 | 🔥 | Sau OT-04 nhập **mã cũ** | MSG43 (chỉ mã mới nhất có hiệu lực) | CBR-94 | |
| OT-06 | 🔥 ⏱ | `manh.ngo@gmail.com` (đã xin 4 lần/giờ) → xin thêm | MSG61 "…khóa 5 phút. Thử lại sau 05:00" | CBR-94 | |
| OT-07 | 🔥 ⏱ | `binh.ta@gmail.com` (đã sai 5) → nhập mã bất kỳ / xin mã mới | MSG61 cả 2 thao tác | CBR-94 | |
| OT-08 | 🔥 | Nhập sai mã 4 lần | Mỗi lần MSG43; `FailedAttempts` tăng 1→4 | CBR-94 | |
| OT-09 | 🔥 | Sai lần thứ 5 | MSG61; `LockedUntil` = now + 5' trên dòng mã | CBR-94 | |
| OT-10 | ⭐ | Sau OT-09, trong 5 phút, nhập **đúng** mã | Vẫn MSG61 | CBR-94 | |
| OT-11 | ⭐ | Sau khi hết 5 phút khóa, nhập mã cũ đúng | MSG44 (mã đã bị hủy) → phải Gửi lại mã | CBR-94 | |
| OT-12 | 🔥 | Nhập `12ab56` / `12345` / `1234567` | "Mã xác thực gồm đúng 6 chữ số." (không tính là 1 lần sai) | DBR-03 | |
| OT-13 | ⭐ | Mã có số 0 đầu (VD `048213`) | Nhập đúng được chấp nhận (không bị mất số 0) | DBR-03 | |
| OT-14 | ⭐ | Đồng hồ "Mã hết hạn sau mm:ss" | Chạy lùi, tải lại trang vẫn đúng số còn lại (server tính) | GBR-27 | |
| OT-15 | 🔥 | `thao.luong@gmail.com` → Gửi mã → bước 2 | "Bạn còn **5** lần nhập mã." + "Còn **2** lần gửi lại mã trong 60 phút." (1 mã seed + 1 mã vừa gửi = 2 mã) | MSG62, MSG64 | |
| OT-16 | 🔥 | Nhập sai liên tiếp | Số giảm 5 → 4 → 3 → 2; còn 1 → **MSG63** màu cảnh báo; sai lần 5 → MSG61, **2 dòng gợi ý biến mất** | MSG62, MSG63, CBR-94 | |
| OT-17 | ⭐ | Gửi lại mã (chờ đủ 60 giây mỗi lần) | Số lần gửi lại giảm; tới 0 → **MSG65** màu cảnh báo; bấm tiếp → MSG61 | MSG64, MSG65, CBR-94 | |
| OT-18 | ⭐ | `binh.ta@gmail.com` (đang khóa) | Chỉ thấy MSG61, **không** có dòng gợi ý | MSG61 | |
| OT-19 | ⭐ | Nhập sai 2 lần → Gửi lại mã | Số lần nhập mã quay về **5** (mã mới) | CBR-94 | |

## 6. QUÊN MẬT KHẨU — `/forgot-password` (UC-03)

| ID | Ưu tiên | Thao tác / Dữ liệu | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| FP-01 | 🔥 ⏱ | `thao.luong@gmail.com` → mã `246810` → MK mới `NewPass123` ×2 | Về `/login` + MSG49; đăng nhập bằng MK mới được; `FailedLoginCount` 4 → **0**, `LockedUntil` NULL | UC-03 | |
| FP-02 | 🔥 | Sau FP-01 đăng nhập bằng MK **cũ** `Gms@2026` | MSG33 | UC-03 | |
| FP-03 | 🔥 | Bước 1 để trống | "Email là bắt buộc." | MSG09 | |
| FP-04 | 🔥 | Bước 1 nhập SĐT `0901234501` | MSG11 (chỉ khôi phục qua email) | BR-03 | |
| FP-05 | 🔥 | `khongtontai@gmail.com` | MSG41 | MSG41 | |
| FP-06 | 🔥 | `phuc.le@gmail.com` (Admin khóa) | MSG37, **không** gửi mã | Chốt #3 | |
| FP-07 | ⭐ | `nam.ho@gms.vn` (nghỉ việc) | MSG37 | Chốt #3 | |
| FP-08 | 🔥 ⏱ | `kim.vo@gmail.com` (đang khóa tạm) → khôi phục đến hết | Thành công; đăng nhập ngay được (khóa tạm đã mở) | Chốt #4 | |
| FP-09 | ⭐ | `khanh.trinh@gmail.com` (chưa có MK) | Khôi phục bình thường → đặt được MK → đăng nhập được | CBR-31 | |
| FP-10 | ⭐ | Nhân viên `son.dinh@gms.vn` | Khôi phục được (áp dụng mọi vai trò) | UC-03 | |
| FP-11 | 🔥 | `quynh.cao@gmail.com` nhập `999000` | MSG44 | BR-04 | |
| FP-12 | 🔥 | Nhập sai mã | MSG43 dưới ô, giữ ở bước 2 | MSG43 | |
| FP-13 | ⭐ | Dùng lại mã vừa dùng thành công (làm lại luồng cho cùng email, nhập mã cũ) | MSG44 | DBR-03 | |
| FP-14 | 🔥 | Bước 3 MK `abcdefgh` | `MSG66_PASSWORD_RULE` | DBR-02 | |
| FP-15 | 🔥 | Bước 3 nhập lại không khớp | MSG47 | MSG47 | |
| FP-16 | ⭐ | Bước 3 để trống | "Mật khẩu mới là bắt buộc." | MSG09 | |
| FP-17 | 🔥 | Đang đăng nhập An ở trình duyệt B → ở trình duyệt A đặt lại MK của An | Trình duyệt B thao tác tiếp → bị về `/login` | CBR-93 | |
| FP-18 | ⭐ | Đang đăng nhập An **trên chính trình duyệt đang khôi phục** → đặt lại MK của An | Không lỗi 500; về `/login` + MSG49; phải đăng nhập lại | CBR-93 | |
| FP-19 | 🔥 | Chưa qua bước 2, dùng DevTools/Postman gửi POST `action=reset` | Bị đưa về bước 1, **không** đổi MK | Bảo mật | |
| FP-20 | 🔥 | Bước 2 sửa HTML thêm ô `email` khác rồi gửi `verify` | Server bỏ qua, dùng email trong session | Bảo mật | |
| FP-21 | ⭐ | Xác minh mã xong, chờ quá hạn mã (≤15') rồi mới đặt MK | MSG44, về bước 1 | BR-04 | |
| FP-22 | ⭐ | Bấm "Dùng email khác" ở bước 2 | Về bước 1, trống | UX | |
| FP-23 | ⭐ | Bước 2 bấm F5 | Vẫn ở bước 2, đồng hồ đúng | PRG | |
| FP-24 | ⭐ | Bước 1 nhập email có khoảng trắng 2 đầu | Chấp nhận (trim) | Trim | |
| FP-25 | ⭐ | Gửi mail thật (đã cấu hình App Password), gửi tới **email của bạn** (tạo TK test bằng email thật) | Thư vào Inbox, tiêu đề `[Super Gym] Mã xác thực khôi phục mật khẩu`, khung SUPER GYM, tiếng Việt đúng | API-03 | |
| FP-26 | | App Password sai → xin mã | MSG04; `VerificationCodes` **không** có dòng mới | API-03 | |
| FP-27 | | Sau FP-01 kiểm tra AuditLogs | Luật: có câu "… đặt lại mật khẩu qua email …". ⚠ Code: chưa ghi (TODO) | GBR-07 | |

## 7. TÀI KHOẢN CHƯA CÓ MẬT KHẨU — Đăng nhập → Quên mật khẩu (CBR-24, CBR-31)

Luồng link `/set-password` đã bỏ 27/09. ⚠ Gmail thật đang bật: với email seed **dừng ở bước nhập email, KHÔNG bấm Gửi mã**.

| ID | Ưu tiên | Thao tác / Dữ liệu | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| SP-01 | 🔥 | Đăng nhập `khanh.trinh@gmail.com` / bất kỳ | Panel "Chưa thể đăng nhập" + MSG38 "Tài khoản này chưa có mật khẩu. Hãy dùng Quên mật khẩu để đặt mật khẩu." | MSG38, CBR-31 | |
| SP-02 | 🔥 | Tiếp SP-01, bấm **Đặt mật khẩu** | `/forgot-password` bước 1, ô email = `khanh.trinh@gmail.com`, **chưa gửi mã** | CBR-31 | |
| SP-03 | 🔥 | Đăng nhập `0901234513` (SĐT Khánh) → **Đặt mật khẩu** | MSG38; sang Quên MK với ô email **TRỐNG** (không lộ email gắn SĐT) | CBR-31, bảo mật | |
| SP-04 | ⭐ | `nhung.ho@gmail.com` → Đặt mật khẩu | Như SP-02 với email Nhung | CBR-31 | |
| SP-05 | ⭐ | Sau SP-02 bấm **F5** | Ô email **trống lại** (điền sẵn chỉ 1 lần) | Bảo mật | |
| SP-06 | ⭐ | Sau SP-02 xem thanh địa chỉ | Chỉ `/forgot-password`, **không có email trên URL** | Bảo mật | |
| SP-07 | ⭐ | Tài khoản test **của chính bạn** có `PasswordHash` NULL → SP-02 → Gửi mã → nhập mã → đặt MK | `/login` + MSG49; đăng nhập bằng MK mới được | CBR-24, CBR-31 | |
| SP-08 | ⭐ | Mở `/set-password` hoặc `/set-password?token=abc` | **404** (trang đã bỏ) | — | |

## 8. CÀI ĐẶT TÀI KHOẢN — `/account` (UC-04)

### 8.1 Hiển thị theo vai trò

> ⚠ Trên `develop` (26/09): tab Hồ sơ chỉ chạy thật với **Admin** (Manager một phần); PT / Lễ tân / Khách hiện khung "đang chờ tích hợp" → các ca AC-01…AC-03, AC-30 sẽ **BLOCK** tới khi merge `profile-customer.jspf`. Tab Mật khẩu chạy cho cả 5 vai trò.

| ID | Ưu tiên | Đăng nhập | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| AC-01 | 🔥 | An | Tab Hồ sơ: họ tên, ngày sinh **chỉ đọc** + gợi ý "do quầy lễ tân cập nhật" | CBR-30, MSG60, GBR-25 | |
| AC-02 | ⭐ | An (không ảnh) | Vòng tròn chữ cái đầu tên | SRS 1.4.1 | |
| AC-03 | ⭐ | Kim (có ảnh) | Hiện ảnh `/uploads/avatar/p-102.jpg` (file phải có sẵn) | CBR-37 | |
| AC-04 | 🔥 | An | Có 3 tab: Hồ sơ · Mật khẩu · Hồ sơ phụ thuộc | SRS 1.4 | |
| AC-05 | 🔥 | Lễ tân Tâm / Admin / PT | **Không** có tab Hồ sơ phụ thuộc | CBR-112 | |
| AC-06 | ⭐ | Lễ tân mở `/account?tab=dependents` | Vẫn hiện tab Hồ sơ (không lọt vào tab phụ thuộc) | CBR-112 | |
| AC-07 | ⭐ | Admin | Tên, mã NV, chức vụ… chỉ đọc; đổi được ảnh + MK (🆕 CBR-108: thêm email, SĐT) | CBR-108/109 | |
| AC-08 | ⭐ | Bất kỳ NV | Vai trò, trạng thái TK chỉ đọc, không có ô sửa | 🆕 CBR-109 | |

### 8.2 Đổi mật khẩu

| ID | Ưu tiên | Thao tác | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| AC-10 | 🔥 | MK hiện tại sai | "Mật khẩu hiện tại không đúng." | UC-04 | |
| AC-11 | 🔥 | MK mới = `Gms@2026` | MSG48 "Mật khẩu mới phải khác mật khẩu hiện tại." | MSG48 | |
| AC-12 | ⭐ | MK mới `abcdefgh` | Lỗi luật 8–64 chữ+số | DBR-02 | |
| AC-13 | ⭐ | Nhập lại không khớp | MSG47 | MSG47 | |
| AC-14 | 🔥 | Đăng nhập An ở 2 trình duyệt; đổi MK ở trình duyệt A | A **vẫn đăng nhập**; B bị đưa về `/login` khi thao tác | CBR-93 | |
| AC-15 | ⭐ | Sau AC-14 đăng nhập bằng MK mới / cũ | Mới được, cũ MSG33 | — | |
| AC-16 | | Câu thông báo thành công | Luật: MSG49. ⚠ Code: "Đổi mật khẩu thành công." (viết cứng) | Messages | |

### 8.3 Ảnh đại diện

| ID | Ưu tiên | Thao tác | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| AC-20 | 🔥 | Upload JPG 500×500, 300 KB | MSG58; ảnh mới hiện; file cũ bị xóa khỏi thư mục upload | UC-04 | |
| AC-21 | ⭐ | Upload PNG | Thành công | DBR-65 | |
| AC-22 | 🔥 | Upload `.gif` / `.pdf` | MSG20 | DBR-65 | |
| AC-23 | 🔥 | Upload JPG 3 MB | Luật (SRS/DBR-65): MSG20 (> 2 MB). ⚠ Code: nhận tới 5 MB | DBR-65 | |
| AC-24 | ⭐ | Upload JPG 100×100 | Luật: MSG20 (< 200 px). ⚠ Code: chưa kiểm kích thước | DBR-65 | |
| AC-25 | ⭐ | Upload JPG 1200×600 | Luật: cắt giữa thành vuông, lưu 256×256. ⚠ Code: lưu nguyên ảnh | DBR-65 | |
| AC-26 | | Đổi đuôi `virus.exe` → `virus.jpg` rồi upload | Luật an toàn: từ chối (không phải ảnh thật). ⚠ Code: tin `contentType` của trình duyệt | Bảo mật | |

### 8.4 Hồ sơ phụ thuộc

| ID | Ưu tiên | Đăng nhập | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| AC-30 | 🔥 | An → tab Hồ sơ phụ thuộc | **Nguyễn Gia Huy** (8 tuổi, nhãn dưới 10, Gói 3 Tháng tới 01/12/2026) + **Nguyễn Minh Châu** (14 tuổi, chưa có gói — An là giám hộ 2). ⚠ Code: chưa nạp dữ liệu → rỗng | SRS 1.4.3 | |
| AC-31 | ⭐ | Bích (không con) | Trạng thái rỗng có câu thông báo | GBR-21 | |
| AC-32 | ⭐ | An | Không có nút sửa nào trên hồ sơ con | SRS 1.4.3 | |

### 8.5 Đổi email / SĐT (BR-05, CBR-29, 🆕 CBR-110/111/113) — hiện BLOCK (servlet chưa có)

| ID | Ưu tiên | Thao tác | Kết quả mong đợi | Luật | KQ |
|---|---|---|---|---|---|
| AC-40 | 🔥 | An đổi email → `an.nguyen.moi@gmail.com` → mã `135790` ⏱ | MSG55 → đúng mã → MSG56; email cũ nhận thư báo; các phiên khác bị đăng xuất | CBR-29 | |
| AC-41 | 🔥 | Đổi email sang `bich.tran@gmail.com` | MSG12, không gửi mã | DBR-23/64 | |
| AC-42 | ⭐ | Đổi email sai định dạng | MSG11 | DBR-18 | |
| AC-43 | ⭐ | Nhập sai mã 5 lần | MSG61 | CBR-94 | |
| AC-44 | 🔥 | Đổi SĐT thành `0901234502` | MSG14 | DBR-22 | |
| AC-45 | ⭐ | Đổi SĐT hợp lệ → mã gửi tới **email hiện tại** → đúng | MSG57 | CBR-110 | |
| AC-46 | 🔥 | Xin mã cho email X, lúc xác nhận sửa ô email thành Y (DevTools) | Luật an toàn: **không** được lưu Y (giá trị mới phải lấy từ session) | Bảo mật | |
| AC-47 | ⭐ | Giữa lúc xin mã và xác nhận, người khác lấy mất email/SĐT đó | Báo trùng, không lưu | 🆕 CBR-110 | |
| AC-48 | ⭐ | Nhân viên (Lễ tân) đổi email của mình | Như AC-40 | 🆕 CBR-108/111 | |
| AC-49 | ⭐ | Sau AC-40/45 xem AuditLogs | Có câu đọc được, **không** chứa mã OTP | 🆕 CBR-113 | |

## 9. EMAIL GATEWAY

| ID | Ưu tiên | Thao tác | Kết quả mong đợi | KQ |
|---|---|---|---|---|
| EM-01 | 🔥 | `mail.app-password=xxxx…` → xin mã | Không gửi thật; console có `[DEV EMAIL]` + mã | |
| EM-02 | 🔥 | App Password thật → xin mã tới email của bạn | Thư tới trong < 10 s; người gửi "Super Gym" | |
| EM-03 | ⭐ | Mở thư trên Gmail web + điện thoại | Có khung SUPER GYM, mã nổi bật; tiếng Việt không lỗi | |
| EM-04 | ⭐ | Xem "Hiển thị bản gốc" (Show original) của thư | `Content-Type: multipart/alternative`, có phần `text/plain` và `text/html`; header có TLS | |
| EM-05 | ⭐ | App Password sai | MSG04; console có `AuthenticationFailedException` | |
| EM-06 | | Rút mạng rồi xin mã | Tối đa ~10 s thì MSG04, trang không treo | |
| EM-07 | | Xóa hẳn dòng `mail.app-password` | Mong đợi: vào chế độ DEV. ⚠ Code: `AppConfig.get` ném lỗi → trang 500 | |

## 10. BẢO MẬT & KỸ THUẬT CHUNG

| ID | Ưu tiên | Kiểm tra | Kết quả mong đợi | KQ |
|---|---|---|---|---|
| SC-01 | 🔥 | `SELECT PasswordHash FROM Accounts` | Dạng `$2a$10$…`, 60 ký tự, **không** có mật khẩu gốc | |
| SC-02 | ⭐ | 2 tài khoản cùng MK `Gms@2026` | 2 hash **khác nhau** (salt) | |
| SC-03 | 🔥 | Đăng nhập identifier `' OR 1=1 --` | MSG34, không lỗi SQL (PreparedStatement) | |
| SC-04 | ⭐ | Email `a@b.com'; DROP TABLE Accounts;--` ở Quên MK | MSG11, bảng còn nguyên | |
| SC-05 | 🔥 | Họ tên `<script>alert(1)</script>` ở đăng ký | Bị chặn bởi luật họ tên; ở ô khác nếu hiển thị lại thì hiện dạng chữ (`<c:out>`), **không** chạy script | |
| SC-06 | ⭐ | `returnUrl=javascript:alert(1)` | Bị bỏ qua (không bắt đầu `/`) | |
| SC-07 | 🔥 | `git check-ignore -v src/main/resources/application.properties` | File bị ignore | |
| SC-08 | ⭐ | `mvn clean package` | BUILD SUCCESS, test JUnit pass | |
| SC-09 | 🔥 | Gây lỗi 500 (VD tắt SQL Server rồi đăng nhập) | Trang 500 tự thiết kế hoặc MSG04, **không** lộ stack trace | |
| SC-10 | ⭐ | Nhập tiếng Việt có dấu ở họ tên, submit lỗi | Giá trị trả lại không vỡ font | |
| SC-11 | ⭐ | Thông báo lỗi JS tiếng Việt trên Windows | Không vỡ font (`là` không thành `lÄ‚`) | |
| SC-12 | | Brute-force 1 email khách bằng script 20 lần | Từ lần 5 bị khóa 5 phút (BR-01). Ghi nhận: chưa có giới hạn theo IP | |
| SC-13 | | Form POST từ trang web khác (CSRF) | Ghi nhận: chưa có CSRF token (rủi ro đã biết) | |
| SC-14 | ⭐ | Log/console sau cả buổi test | Không có mật khẩu người dùng nào bị in ra (chỉ có mã OTP ở chế độ DEV) | |

## 11. Unit test có sẵn (chạy `mvn test`)

| Lớp | Kiểm tra gì |
|---|---|
| `AuthServiceTest` | Các nhánh `login()` với DAO giả (constructor `AuthService(AccountDAO)`) |
| `UtilTest` | `ValidationUtil`, `PasswordUtil`, `SecureCodeUtil`… |
| `BlogServiceTest`, `VnPayClientTest` | Blog, chữ ký HMAC VNPay |

Gợi ý bổ sung unit test (ăn điểm): `VerificationCodeServiceTest` cho 5 dòng DT-3 + 9 dòng DT-4 (dùng DAO giả giống `AuthServiceTest`).

---

## 12. Mẫu báo cáo bug (khi ca FAIL)

```
[BUG-xx] <Tên ngắn>
Màn / URL:        /register
Ca test:          RG-25
Bước tái hiện:    1) … 2) … 3) …
Dữ liệu:          0935000105 / yen.doan@gmail.com
Mong đợi:         Không tạo tài khoản khi chưa nhập đúng mã (CBR-20)
Thực tế:          Tạo tài khoản ngay, gắn vào KH-1105
Mức độ:           Cao (bảo mật)
Người phụ trách:  Hoàng
```
