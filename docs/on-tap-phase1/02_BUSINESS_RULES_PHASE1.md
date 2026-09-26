# 02 · Business Rules Phase 1 — xếp theo LUỒNG, không theo mã

> Bộ luật gốc (v24) có ~280 luật chia theo loại (GBR/CBR/DBR/BR). Học theo loại rất dễ quên.
> File này **xếp lại theo đúng thứ tự người dùng đi qua màn hình**, mỗi luồng có: sơ đồ quyết định → bảng luật → trạng thái code → mẹo nhớ.
>
> Ký hiệu trạng thái code (trên `develop`): ✅ đúng luật · 🟡 làm một phần / lệch nhẹ · 🔴 sai luật hoặc chưa làm · 🆕 luật đề xuất bổ sung.

## Bản đồ 4 loại luật (để trả lời "GBR khác CBR thế nào?")

| Loại | Định nghĩa trong bộ luật | Ví dụ Phase 1 |
|---|---|---|
| **GBR** — General | Áp cho toàn hệ thống / hầu hết màn | GBR-13 kiểm tra lại ở server, GBR-15 mã chỉ gửi email |
| **CBR** — Common | Dùng chung cho **≥ 2 use case** | CBR-94 luật mã OTP (UC-03, 04, 05, Claim) |
| **BR** — Business | Thuộc đúng **1 use case** | BR-01 khóa đăng nhập (chỉ UC-01) |
| **DBR** — Data | Luật dữ liệu ERD không thể hiện được (định dạng, duy nhất có điều kiện, trạng thái) | DBR-02 mật khẩu 8–64, DBR-28 nhả SĐT/email |
| **P-xx** | Con số của luật, Manager cấu hình | P-15 = 15 phút |

Câu thần chú: **"G toàn cục – C dùng chung – B một ca – D dữ liệu – P con số."**

---

## A. ĐĂNG NHẬP `/login` (UC-01) và ĐĂNG XUẤT (UC-02)

### A.1 Sơ đồ quyết định (đúng thứ tự trong `AuthService.login`)

```
Nhập identifier + password
 B1  trống cả 2 → lỗi dưới cả 2 ô · trống identifier → MSG39 · trống password → MSG09 "Mật khẩu là bắt buộc."
 B2  identifier là email hợp lệ?  → AccountDAO.findByEmail   (JOIN Persons, IsReleased = 0)
     là SĐT hợp lệ (0 + 9 số)?    → AccountDAO.findByPhone
     không phải cả 2 / không tìm thấy / hồ sơ không có tài khoản → MSG34 (câu chung, KHÔNG nói "không tồn tại")
 B3  Status = Inactive | Locked                         → MSG37 "Tài khoản này đã ngừng hoạt động…"
 B4  LockedUntil > bây giờ                              → MSG35 "Tài khoản bị khóa 5 phút… Thử lại sau n phút."
     LockedUntil ≤ bây giờ (hết khóa)                   → reset FailedLoginCount = 0, LockedUntil = NULL (BR-02), đi tiếp
 B5  PasswordHash NULL                                  → MSG38 (panel "Chưa thể đăng nhập" + nút Đặt mật khẩu)
 B6  Xác định vai trò (Employees Working → Admin/Manager/PT/Receptionist; không thì Customer)
 B7  BCrypt sai:  NHÂN VIÊN → MSG34, không đếm, không khóa
                  KHÁCH     → FailedLoginCount + 1 → đủ 5 → LockedUntil = now + 5' → MSG35 · chưa đủ → MSG33 "còn n lần"
 B8  Đúng        → reset bộ đếm (nếu có) · LastLoginAt = now · LoginServlet tạo session MỚI → redirect
```

### A.2 Bảng luật

| Mã | Luật (tóm tắt) | Trạng thái code |
|---|---|---|
| UC-01 | Đăng nhập bằng **email hoặc SĐT** + mật khẩu, vào khu của vai trò | ✅ |
| UC-01 | SĐT vừa của khách vừa của nhân viên → **chỉ đăng nhập bằng email** | 🔴 chưa xử lý (DB 1 Person = 1 Account; Person có cả 2 hồ sơ luôn vào vai trò nhân viên) — chờ chốt L15/D1.2 |
| BR-01 | **Khách** sai 5 lần liên tiếp (P-17) → khóa 5 phút (P-18). **Nhân viên không bao giờ tự khóa** (máy quầy dùng chung, khóa nhầm là dừng cả ca) | 🟡 đúng logic, nhưng 5/5 **viết cứng** trong `AuthService` thay vì đọc P-17/P-18 |
| BR-02 | Hết khóa → tự mở, bộ đếm về 0; đăng nhập đúng cũng về 0 | ✅ |
| CBR-96 | Tài khoản chỉ bị chặn ở 3 trường hợp: nhân viên nghỉ (Inactive), Admin khóa (Locked), khách khóa tạm (BR-01) | ✅ |
| CBR-31 | Tài khoản Lễ tân tạo chưa có mật khẩu → không đăng nhập được, phải đặt qua link | ✅ MSG38 |
| CBR-21 / CBR-17 | Dependent / hồ sơ không tài khoản → không đăng nhập | ✅ (không tìm thấy account → MSG34) |
| DBR-28 | Hồ sơ đã nhả (IsReleased = 1) không đăng nhập được | ✅ `IsReleased = 0` trong SQL |
| GBR-27 | Khóa tạm hiện đồng hồ **mm:ss** | 🟡 server trả "n phút" (làm tròn lên), JS đếm từ n×60 giây — không chính xác tới giây |
| GBR-31 | Hết phiên → đăng nhập lại → quay về đúng màn (`returnUrl`) | ✅ + chống open redirect |
| GBR-30 | Ô mật khẩu có nút con mắt | ✅ |
| Session | Lưu `accountId`, `role`, `displayName`; timeout 7200 s; **đổi session id** | ✅ |
| UC-02 | Đăng xuất: hủy session → toast MSG40 → `/login` | ✅ (⚠ nhận cả GET — xem 05) |

Mẹo nhớ thứ tự: **"Trống – Tìm – Trạng thái – Tạm khóa – Trống hash – Thử mật khẩu – Thành công"** (T-T-T-T-T-T-T, 7 chữ T).

---

## B. ĐĂNG KÝ `/register` (UC-05 + UC-IDENT + UC-Claim)

### B.1 Cây quyết định theo SĐT (UC-IDENT)

```
Bước 1: nhập SĐT (10 số, bắt đầu 0) ──► tìm Person đang hoạt động có Persons.Phone = SĐT
│
├─ KHÔNG có (hoặc chỉ khớp hồ sơ đã nhả — CBR-23 / chỉ khớp số GIÁM HỘ — CBR-28)
│     → Bước 2 nhập email, mật khẩu, họ tên, ngày sinh
│     → email trùng tài khoản khác? MSG12
│     → [LUẬT] gửi mã SignUp tới email → đúng mã → tạo Person + Customer(Self, New, KH-nnnn) + Account (1 transaction) → MSG54
│
└─ CÓ hồ sơ Self đang hoạt động
      ├─ đã có tài khoản                  → MSG51 "…đã có tài khoản. Vui lòng đăng nhập hoặc dùng Quên mật khẩu." (CBR-26)
      ├─ chưa có tài khoản, KHÔNG có email → MSG53 mời ra quầy (CBR-27)
      ├─ chưa có tài khoản, email KHÁC     → MSG52 kèm email che "ye***@gmail.com" (CBR-25)
      └─ chưa có tài khoản, email KHỚP     → [LUẬT] gửi mã Claim tới email HỒ SƠ → đúng mã + mật khẩu
                                            → gắn Account vào Person cũ, KHÔNG tạo hồ sơ mới (CBR-20, UC-Claim)
```

### B.2 Bảng luật

| Mã | Luật | Trạng thái code |
|---|---|---|
| UC-05 | Khách tự đăng ký online; SĐT/email kiểm tra trùng | ✅ |
| SRS 1.2 | **2 bước**: kiểm SĐT → nhập thông tin | 🟡 có 2 bước giao diện, nhưng bước 1 chỉ kiểm **định dạng**; mọi nhánh UC-IDENT chạy ở bước 2 |
| CBR-20 | Nhánh kích hoạt phải **gửi mã tới email hồ sơ** trước khi gắn tài khoản | 🔴 **đang gắn tài khoản KHÔNG cần mã** → ai biết SĐT + email của người khác là chiếm được hồ sơ |
| UC-05 / API-03 | Tài khoản mới cũng phải xác minh mã email; **gửi email lỗi → không tạo gì** | 🔴 chưa có bước OTP |
| DBR-34 / CBR-01 | Hồ sơ mới trạng thái **New**; chỉ thành Active khi **thanh toán gói đầu tiên** | 🟡 tạo mới đúng New; nhưng nhánh Claim đang **đổi New → Active** (sai) |
| CBR-25/26/27 | 3 nhánh dừng (email khác / đã có TK / không email) | ✅ |
| CBR-28 | Số giám hộ của Dependent không chặn, không kích hoạt | ✅ (`findByPhone` chỉ xét `Persons.Phone`; Dependent có `Phone` NULL) |
| CBR-23, DBR-28 | Chỉ khớp hồ sơ Inactive → tạo mới bình thường | ✅ |
| DBR-23 / DBR-29 | Email bắt buộc với người có tài khoản, không trùng hồ sơ đang hoạt động | ✅ `existsByEmail` |
| DBR-09 | Mã khách `KH-nnnn` do hệ thống sinh, không đổi | ✅ `generateNextCustomerCode` |
| DBR-01/02/04, DBR-17/18 | Họ tên 2–50 chữ; mật khẩu 8–64 chữ+số; ngày sinh không tương lai, ≤ 100 tuổi; SĐT 10 số bắt đầu 0; email name@domain ≤ 100 | ✅ (⚠ đầu số nhà mạng P-16 chưa kiểm) |
| P-05 | Dưới 10 tuổi **vẫn đăng ký được** (chỉ bị chặn mua/đặt PT về sau — CBR-13/14/15) | ✅ |
| CBR-95 | Tài khoản khách chỉ có 2 nguồn: tự đăng ký hoặc Lễ tân tạo; Admin không tạo TK khách | ✅ (Admin tạo TK: phase sau) |

Mẹo nhớ 4 nhánh SĐT trùng: **"Có TK → Đăng nhập · Không email → Ra quầy · Email khác → Che email · Email khớp → Gửi mã"**.

---

## C. BỘ MÁY MÃ OTP DÙNG CHUNG — `VerificationCodeService` (CBR-94)

Dùng cho 4 mục đích: `SignUp`, `Claim`, `Recovery`, `EmailChange` (+ `PasswordSetup` là **link**, không phải mã).

| Luật | Giá trị | Nguồn | Trong code |
|---|---|---|---|
| Mã | Đúng **6 chữ số**, sinh ngẫu nhiên an toàn, **dùng 1 lần** | DBR-03 | `SecureCodeUtil.generateVerificationCode()` · `markUsed` |
| Hạn | **15 phút**, có đồng hồ mm:ss | P-15, BR-04, GBR-27 | `ExpiresAt = SentAt + P-15` |
| Nhập sai | **5 lần** (P-17) → **hủy mã + khóa xác thực email 5 phút** (P-18): không nhập mã, không xin mã mới → MSG61 | CBR-94 | `increaseFailedAttempts` + `lockCode` |
| Xin lại mã | Sau **60 giây** kể từ lần trước (không cấu hình) | CBR-94 | `CODE_RESEND_GAP_SECONDS` |
| Tần suất | **Lần xin thứ 5 trong 1 giờ** → khóa 5 phút như trên | CBR-94 | `countCodesSentSince(email, now − 60')` + 1 ≥ 5 |
| Mã mới | Luôn **hủy mã cũ** — chỉ mã **mới nhất** theo (Email, Purpose) có hiệu lực | CBR-94 | `findLatestByEmailAndPurpose` (`TOP 1 … ORDER BY SentAt DESC`) |
| Kênh | Chỉ **email**, không SMS | GBR-15 | `EmailGateway` |

Thứ tự kiểm tra khi **xin mã**: `Đang khóa? → Chưa đủ 60s? → Lần thứ 5/giờ? → Sinh mã → GỬI MAIL → ghi DB`.
Thứ tự kiểm tra khi **nhập mã**: `Đúng 6 số? → Đang khóa? → Mã mới nhất còn dùng được (chưa dùng / chưa hủy / chưa hết hạn)? → So mã (sai: +1, đủ 5 khóa) → Đúng: đánh dấu UsedAt`.

Mẹo nhớ: **"5 – 5 – 15 – 60 – 5/giờ"** = sai 5 lần · khóa 5 phút · sống 15 phút · chờ 60 giây · xin tối đa 4 lần/giờ.

---

## D. QUÊN MẬT KHẨU `/forgot-password` (UC-03) — 3 bước, 1 trang

```
Bước 1  Email ──► trống MSG09 · sai định dạng / nhập SĐT MSG11 (BR-03: chỉ qua email)
                  không có tài khoản với email MSG41 · Status ≠ Active (Admin khóa / nghỉ việc) MSG37
                  → gửi mã Recovery (luật mục C) → toast MSG42 → sang bước 2
Bước 2  Mã 6 số ─► sai MSG43 · hết hạn / đã dùng / đã hủy MSG44 · sai lần 5 MSG61 · "Gửi lại mã" MSG45
                  đúng → mở bước 3, hạn làm bước 3 = hạn của mã
Bước 3  Mật khẩu mới + nhập lại ─► 8–64 chữ+số (DBR-02) · khớp (MSG47) · quá hạn → MSG44 về bước 1
                  → lưu hash BCrypt + MỞ KHÓA TẠM (bộ đếm về 0) trong 1 transaction
                  → ĐĂNG XUẤT MỌI PHIÊN (CBR-93) → toast MSG49 → /login
```

| Mã | Luật | Trạng thái |
|---|---|---|
| BR-03 | Khôi phục **chỉ qua email đã đăng ký**, không qua SĐT | ✅ |
| BR-04 | Mã khôi phục hết hạn sau 15 phút | ✅ |
| CBR-94 | Luật mã (mục C) | ✅ |
| CBR-93 | Đổi/đặt lại mật khẩu thành công → đăng xuất **mọi** phiên trước đó | ✅ `invalidateSessionsOfAccount(id, null)` |
| UC-03 | Áp dụng mọi vai trò (khách + nhân viên) | ✅ |
| CBR-31 | Tài khoản chưa có mật khẩu (link hết hạn) cũng dùng luồng này | ✅ |
| Chốt #3 | Admin khóa / nhân viên nghỉ → **chặn** (MSG37), khớp màn đăng nhập | ✅ |
| Chốt #4 | Khách đang khóa tạm 5 phút → **cho** khôi phục; đặt lại xong tự mở khóa | ✅ `resetFailedLogin` |
| Chốt #2 | Email không tồn tại → MSG41 (chấp nhận rủi ro dò email) | ✅ (ghi nhận rủi ro — 05) |
| GBR-07/08 | Ghi AuditLog "đặt lại mật khẩu qua email" | 🔴 TODO, chưa có `AuditLogDAO` |

---

## E. ĐẶT MẬT KHẨU QUA LINK `/set-password?token=…` (CBR-24, CBR-31)

```
[Lễ tân tạo tài khoản tại quầy — TV4, CHƯA CODE]
  token = SecureCodeUtil.generateUrlToken()   (32 byte ngẫu nhiên → 43 ký tự Base64URL)
  INSERT VerificationCodes(Purpose=PasswordSetup, TokenHash = sha256Hex(token), Code NULL, ExpiresAt = now + P-15)
  EmailGateway.sendPasswordSetupLink(email, ".../set-password?token=" + token, 15)
[Khách mở link]   GET  → băm token → tìm TokenHash → không có / đã dùng / hết hạn → "Link không còn hiệu lực" + nút Quên mật khẩu
[Khách gửi form]  POST → KIỂM TRA LẠI link → validate mật khẩu → Status Active? → lưu hash + mở khóa → UsedAt = now → /login
```

| Mã | Luật | Trạng thái |
|---|---|---|
| CBR-24 | Khách mua gói ≥ 1 tháng tại quầy → Lễ tân tạo TK bằng email hồ sơ → khách nhận email đặt mật khẩu | 🟡 phía khách ✅ · phía Lễ tân ⏳ (TV4) |
| CBR-31 | Chưa đặt mật khẩu → không đăng nhập (MSG38); link hết hạn P-15 → dùng Quên mật khẩu | ✅ |
| Bảo mật | DB chỉ lưu SHA-256; link dùng 1 lần; kiểm tra lại khi POST | ✅ |

---

## F. CÀI ĐẶT TÀI KHOẢN `/account` (UC-04) — 3 tab + ĐỀ XUẤT BỔ SUNG

### F.1 Ai được sửa gì (đã gộp luật hiện có + đề xuất 🆕)

| Trường | Khách (Customer) | Nhân viên (Admin/Manager/PT/Lễ tân) | Ai sửa nếu không được tự sửa | Luật |
|---|---|---|---|---|
| Ảnh đại diện | ✏ Tự sửa | ✏ Tự sửa | — | CBR-30, 🆕 CBR-108, 🆕 DBR-65 |
| Email | ✏ Tự sửa **có mã** (mã gửi **email mới**) | ✏ 🆕 Tự sửa **có mã** | — | BR-05, CBR-29, 🆕 CBR-110/111 |
| Số điện thoại | ✏ Tự sửa **có mã** (mã gửi **email hiện tại**) | ✏ 🆕 Tự sửa **có mã** | — | SRS 1.4.1, 🆕 CBR-110 |
| Mật khẩu | ✏ Tự đổi | ✏ Tự đổi | — | UC-04, CBR-93 |
| Họ tên, ngày sinh | 👁 Chỉ xem | 👁 Chỉ xem | Khách: **Lễ tân** (UC-16) · NV: **Manager** (UC-13) | CBR-30, 🆕 CBR-108 |
| Ảnh check-in, thông tin giám hộ | 👁 Chỉ xem | — | Lễ tân | CBR-30, CBR-38 |
| Mã NV, chức vụ, chuyên môn, kinh nghiệm, ngày vào làm, % hoa hồng, trạng thái làm việc | — | 👁 Chỉ xem | Manager (UC-13) | 🆕 CBR-108 |
| Vai trò, trạng thái tài khoản (Active/Locked) | 👁 | 👁 **Không bao giờ sửa ở đây** | Admin (UC-07) | 🆕 CBR-109 |
| Tab Hồ sơ phụ thuộc | Có (chỉ xem) | **Không hiện** | — | 🆕 CBR-112 |

### F.2 Luật đổi email / SĐT (gộp BR-05, CBR-29 + đề xuất)

```
Đổi EMAIL:  nhập email mới → hợp lệ? trùng TK đang hoạt động khác? (🆕 DBR-64) → gửi mã EmailChange tới EMAIL MỚI (MSG55)
            → đúng mã → KIỂM TRÙNG LẠI (🆕 CBR-110) → lưu → báo EMAIL CŨ + đăng xuất MỌI phiên (CBR-29, 🆕 CBR-111) → MSG56
Đổi SĐT:    nhập SĐT mới → hợp lệ? trùng? → gửi mã tới EMAIL HIỆN TẠI → đúng mã → KIỂM TRÙNG LẠI → lưu → MSG57
Ghi log:    3 thao tác email / SĐT / mật khẩu → AuditLog thành câu đọc được, KHÔNG ghi mật khẩu & mã (🆕 CBR-113, GBR-07/08)
```

### F.3 Tab Mật khẩu

Mật khẩu hiện tại **phải đúng** → mật khẩu mới 8–64 chữ+số, **khác mật khẩu hiện tại** (MSG48) → nhập lại khớp (MSG47) → lưu BCrypt → **đăng xuất các phiên KHÁC, giữ phiên hiện tại** (CBR-93) → MSG49.

### F.4 Tab Hồ sơ phụ thuộc (chỉ khách)

Danh sách hồ sơ `Dependent` có `GuardianPhone` hoặc `GuardianPhone2` = SĐT người đang đăng nhập: họ tên, tuổi (nhãn **dưới 10 tuổi** không đặt PT — P-05), mã KH, gói hiện tại + hạn. Người giám hộ **không sửa được gì**.

### F.5 Trạng thái code tab Cài đặt tài khoản

| Hạng mục | Trạng thái |
|---|---|
| Tab Hồ sơ theo vai trò | 🟡 chạy thật cho **Admin**; Manager có fragment nhưng nút "Cập nhật hồ sơ" (`action=updateProfile`) chưa được servlet xử lý; **PT, Lễ tân, Khách** đang hiện "Hồ sơ của vai trò này đang chờ tích hợp" (`profile-customer.jspf` có sẵn nhưng chưa include) |
| Đổi mật khẩu (đúng thứ tự B1–B8, giữ phiên hiện tại) | ✅ logic, dùng chung 5 vai trò · 🟡 câu thông báo **viết cứng** trong Service ("Đổi mật khẩu thành công." thay vì MSG49, "Mật khẩu hiện tại không đúng." …) |
| Đổi ảnh đại diện | 🟡 kiểm tra theo `contentType` do trình duyệt gửi + tối đa **5 MB**; chưa kiểm 200–2000 px, chưa cắt vuông 256×256 (DBR-65/SRS: 2 MB) |
| Đổi email / SĐT | 🔴 `ContactChangeService` đã viết nhưng **chưa có servlet** gọi (`/account/update-profile`, `/account/verify-change` → 404) |
| Nhân viên đổi email/SĐT | 🔴 giao diện nhân viên đang chỉ đọc email/SĐT (theo luật cũ); đề xuất CBR-108/110 cần mở ra |
| Tab phụ thuộc chỉ cho khách | ✅ `hasDependentsTab = role == CUSTOMER` và chặn `?tab=dependents` với vai trò khác (CBR-112 đã đúng) |
| Tab phụ thuộc có dữ liệu | 🔴 chưa truy vấn → luôn rỗng |
| AuditLog | 🔴 chưa có |

### F.6 🆕 Nhận xét BA cho bộ đề xuất CBR-108…113, DBR-64, DBR-65

| Đề xuất | Đánh giá | Việc kéo theo khi chốt |
|---|---|---|
| **CBR-108** NV chỉ sửa avatar, email, SĐT, mật khẩu | Hợp lý, đối xứng với CBR-30 của khách | ⚠ **Mâu thuẫn Permission Matrix 4.4-A**: dòng "Staff can edit basic information in their own profile" (PT Restr.[8], Receptionist Restr.[9]) → phải sửa thành chỉ avatar/email/SĐT/mật khẩu; dòng "Person — Edit Avatar/Email/Phone" hiện Admin/Manager/PT = **No** → đổi thành Restr. (own account). Sửa mô tả UC-04 ("A customer may change…" → "A user may change…") |
| **CBR-109** Vai trò, trạng thái TK không sửa ở Account Settings | Khớp UC-07, BR-06/07/08 | Không phát sinh; chỉ cần UI hiển thị xám + gợi ý "do Admin thay đổi" (GBR-25) |
| **CBR-110** Đổi email → mã tới email mới; đổi SĐT → mã tới **email hiện tại**; kiểm trùng **2 lần** | Chốt được **mâu thuẫn #9** (BR-05 nói "mã gửi tới email mới" cho cả SĐT) | Sửa câu BR-05 cho khớp, hoặc thay BR-05 bằng CBR-110. Code phải **lưu giá trị mới chờ xác minh trong session** (xem 05 — lỗ hổng "đổi giá trị sau khi có mã") |
| **CBR-111** NV đổi email → báo email cũ + đăng xuất mọi phiên | Đối xứng CBR-29 | Code hiện giữ lại phiên hiện tại (`keepSessionId`) — luật nói "every session" → chốt: giữ phiên đang thao tác hay không? (MSG56 nói "đăng xuất mọi phiên") |
| **CBR-112** Tab phụ thuộc chỉ cho vai trò Customer | Đúng; code đã làm | Nếu sau này 1 người vừa PT vừa khách (L15) thì tab hiện theo **vai trò đang đăng nhập** |
| **CBR-113** Ghi AuditLog cho email/SĐT/mật khẩu, không ghi mật khẩu & mã | Đúng GBR-07/08, DBR-02 | Cần `AuditLogService.log(...)`; bảng `AuditLogs` đã có (Action, EntityName, FieldChanged, OldValue, NewValue). Với mật khẩu: OldValue/NewValue để trống |
| **DBR-64** Email của TK không trùng TK đang hoạt động khác (khách + NV) | Mở rộng DBR-23 (chỉ nói Customer.Email) sang mọi tài khoản | DB đã có sẵn: kiểm trên `Persons.Email` với `IsReleased = 0` (dùng chung khách + NV) → nên thêm **unique index có điều kiện cho Email** giống Phone (GBR-13: DB chặn tranh chấp) |
| **DBR-65** Avatar JPG/PNG ≤ 2 MB, 200–2000 px, cắt giữa, lưu 256×256 | Chốt được **mâu thuẫn #3** (SRS 2 MB vs DBR-08 5 MB) | Sửa DBR-08 thành "ảnh check-in, ảnh bìa blog" (bỏ avatar) để 2 luật không đá nhau; code đổi 5 MB → 2 MB riêng avatar, đọc kích thước bằng `ImageIO`, resize 256×256 |

---

## G. PHIÊN & PHÂN QUYỀN (áp mọi màn đã đăng nhập)

| Mã | Luật | Trạng thái |
|---|---|---|
| GBR-10 | Mỗi vai trò chỉ làm việc được phép (ma trận 4.4) | 🟡 5 khu có filter; `/staff/*` chưa có |
| GBR-31 | Hết phiên → đăng nhập lại, quay về đúng màn | ✅ |
| CBR-91 | Mỗi người chỉ sửa hồ sơ của chính mình | ✅ `accountId` lấy từ **session**, không từ form |
| CBR-93 | Đổi mật khẩu → đăng xuất phiên cũ | ✅ |
| CBR-86 | NV nghỉ việc → khóa TK + đăng xuất ngay mọi phiên | ⏳ UC-07/13 (phase sau) — hàm `invalidateSessionsOfAccount` đã sẵn |

---

## H. LUẬT DỮ LIỆU — bảng kiểm tra 2 lần (trình duyệt + server)

| Trường | Luật | Lỗi | Server | Trình duyệt |
|---|---|---|---|---|
| Email hoặc SĐT (login) | Bắt buộc, ≤ 100; có `@` → email, không → 10 số bắt đầu 0 | MSG39 | `isValidEmail`/`isValidPhone` | `required` |
| Mật khẩu (login) | Bắt buộc, **không kiểm định dạng** — chỉ so hash | MSG09, MSG33/34 | `PasswordUtil.checkPassword` | `required` |
| SĐT | `^0\d{9}$` + đầu số P-16 (chưa kiểm) | MSG13 · trùng MSG14 | `isValidPhone` | `data-rule="phone"` |
| Email | `name@domain`, ≤ 100 | MSG11 · trùng MSG12 | `isValidEmail` | `data-rule="email"` |
| Mật khẩu mới | 8–64, ≥ 1 chữ (`\p{L}`) + ≥ 1 số; khác cũ khi đổi | `EXTRA_PASSWORD_RULE`, MSG48 | `isValidPassword` | `data-rule="password"` |
| Nhập lại mật khẩu | Giống hệt | MSG47 | `equals` | `data-match` |
| Họ tên | 2–50, chữ có dấu, khoảng trắng, `'`, `-` | `EXTRA_FULL_NAME_RULE` | `isValidFullName` | `data-rule="fullName"` |
| Ngày sinh | Không tương lai, ≤ 100 tuổi | MSG15, `EXTRA_DOB_TOO_OLD` | Service | `data-rule="dateOfBirth"` |
| Mã xác thực | `^\d{6}$` | `EXTRA_CODE_FORMAT` | `isValidVerificationCode` | `data-rule="verificationCode"` |

Nguyên tắc: **trim** mọi chuỗi trước khi kiểm/lưu — **trừ mật khẩu** (giữ nguyên từng ký tự). Mật khẩu **không bao giờ** in log, gửi email, trả về giao diện.

---

## I. LUẬT HÀNH VI MÀN HÌNH (GBR) dùng trong Phase 1

GBR-01 tiếng Việt, dd/mm/yyyy, HH:mm, UTC+7 · GBR-13 server kiểm lại · GBR-15 chỉ email · GBR-17 lỗi đỏ dưới ô, kiểm khi rời ô + khi gửi · GBR-22 khóa nút khi gửi, lỗi giữ dữ liệu · GBR-24 rời form chưa lưu phải xác nhận · GBR-25 ô không sửa được thì xám + gợi ý ai sửa · GBR-27 đồng hồ mm:ss · GBR-30 nút con mắt · GBR-31 quay về đúng màn · GBR-34 toast 3–5 s, toast lỗi ở lại.

---

## J. THÔNG BÁO — nhóm theo luồng (đọc 1 lần là nhớ)

| Luồng | Mã |
|---|---|
| Đăng nhập | MSG33 (còn n lần) · MSG34 (chung, NV) · MSG35 (khóa tạm) · MSG37 (ngừng hoạt động) · MSG38 (chưa có mật khẩu) · MSG39 (nhập email/SĐT) · MSG40 (đã đăng xuất) |
| Đăng ký | MSG50 (SĐT hợp lệ) · MSG51 (đã có TK) · MSG52 (email khác, che) · MSG53 (hồ sơ không email) · MSG54 (đã tạo TK) · MSG12/13/14 (trùng/sai) |
| Mã OTP | MSG42 (đã gửi, hiệu lực n phút) · MSG43 (sai mã) · MSG44 (hết hạn) · MSG45 (đã gửi mã mới) · MSG61 (khóa xác thực) · `EXTRA_CODE_FORMAT` · `EXTRA_CODE_RESEND_TOO_SOON` |
| Mật khẩu | MSG46 (quá ngắn) · MSG47 (không khớp) · MSG48 (trùng cũ) · MSG49 (đã đổi, đăng xuất phiên) · `EXTRA_PASSWORD_RULE` · `EXTRA_SETUP_LINK_INVALID` · `EXTRA_PASSWORD_SET` |
| Tài khoản | MSG55 (mã tới email mới) · MSG56 (đã đổi email, báo email cũ) · MSG57 (đã đổi SĐT) · MSG58 (đã đổi ảnh) · MSG59 (đăng xuất thiết bị khác) · MSG60 (do quầy lễ tân cập nhật) |
| Hệ thống | MSG04 (hệ thống bận) · MSG06 (hết phiên — AJAX 401) · MSG07 (không có quyền — AJAX 403) · MSG09 (%s là bắt buộc) |

---

## K. MÂU THUẪN TÀI LIỆU & QUYẾT ĐỊNH ĐÃ CHỐT (BA phải thuộc)

| # | Chủ đề | Nguồn A | Nguồn B | Đang dùng |
|---|---|---|---|---|
| 1 | Độ dài mật khẩu đăng ký | SRS 1.2.2: **6–64** | DBR-02, SRS 1.3.3/1.4.2, prototype: **8–64** chữ+số | **8–64** → sửa SRS 1.2.2 |
| 2 | Số bước đăng ký | SRS: 2 bước | Prototype: 1 form | 2 bước giao diện, UC-IDENT chạy đủ ở server |
| 3 | Dung lượng avatar | SRS 1.4.1: 2 MB | DBR-08: 5 MB | Đề xuất DBR-65 chốt **2 MB** cho avatar |
| 4 | Người vừa khách vừa NV | SRS 3.2: TK thuộc khách **hoặc** NV | DB: 1 Person = 1 Account | Chờ chốt (hỏi chọn vai trò khi đăng nhập) |
| 5 | Mã khách ví dụ | SRS 1.4.3: `SG-0242` | DBR-09, DB: `KH-nnnn` | **KH-nnnn** → sửa SRS |
| 6 | Đầu số P-16 | "Deployment configuration" | Seed lưu trong SystemConfigs | Chờ chốt 1 nơi |
| 9 | Đổi SĐT gửi mã tới đâu | BR-05: email **mới** | SRS 1.4.1: email **hiện tại** | Đề xuất CBR-110 chốt: email hiện tại |
| 10 | Màn đổi mật khẩu | SRS 1.4.2 ghi "đang lỗi màn" | — | Nhắc người viết SRS sửa |
| — | Hạn link thanh toán | P: 5 phút | JOB-06: 10 phút | `PAYMENT_LINK_VALID_MINUTES = 5` |
| — | Timezone | SRS: "Asia/Ha_Noi" (không tồn tại) | IANA | `Asia/Ho_Chi_Minh` |
| — | Chốt nhóm | Email không tồn tại ở Quên MK → MSG41 · Admin khóa → chặn khôi phục · Khóa tạm → cho khôi phục | | |
