# Phase 1 — Rule nghiệp vụ: Xác thực · Cài đặt tài khoản · Trang chủ · Blog

Phạm vi: **màn SRS #1–7** — Sign In, Register Account, Forgot Password, Account Settings, Home, Blog List, Blog Details.
Use case: UC-01 Login · UC-02 Logout · UC-03 Recover Password · UC-04 Manage Personal Account · UC-05 Register ·
UC-Claim (kích hoạt hồ sơ có sẵn) · UC-IDENT (kiểm tra trùng SĐT/email) · UC-06 Xem dịch vụ, giá, blog.

Nguồn: `GMS_Rules_and_Parameters` (GBR/CBR/DBR/BR v24, System Parameters), `SRS - G5` mục II.1–II.2 và III.5, prototype GUEST GYM,
database `1_Init_Schema.sql`. Mã luật giữ nguyên để trace; nội dung đã tóm tắt tiếng Việt.
Chỗ các nguồn nói khác nhau → **mục 7**, chưa chốt thì code theo cột "Tạm dùng".

---

## 1. Bảng kiểm tra dữ liệu (mật khẩu, ký tự, độ dài)

Mọi luật ở đây kiểm tra **2 lần**: trình duyệt (`common.js`, báo sớm) và server (`ValidationUtil`, quyết định cuối — GBR-13).
Kiểm tra khi rời ô và khi bấm gửi; lỗi hiện chữ đỏ dưới ô (GBR-17).

| Trường | Luật | Lỗi hiển thị | Server (`ValidationUtil`) | Trình duyệt (`common.js`) |
|---|---|---|---|---|
| **Email hoặc SĐT** (đăng nhập) | Bắt buộc, tối đa 100 ký tự. Có `@` → kiểm tra dạng email; không có → phải đúng 10 số, bắt đầu 0 | Trống: MSG39 · Sai dạng: MSG11 / MSG13 | `isValidEmail` / `isValidPhone` | `required` + tự kiểm tra trong JS màn đăng nhập |
| **Mật khẩu** (đăng nhập) | Bắt buộc, che ký tự, có nút con mắt (GBR-30). **Không kiểm tra định dạng**, chỉ so với hash | Trống: MSG09 · Sai: MSG33 / MSG34 | `PasswordUtil.checkPassword` | `required` + `data-password-toggle` |
| **Số điện thoại** | Đúng 10 chữ số, bắt đầu `0`, đầu số nhà mạng hợp lệ (P-16); không trùng hồ sơ Self đang hoạt động | MSG13 · Trùng: MSG14 | `isValidPhone` + `hasValidPhonePrefix` | `data-rule="phone"` |
| **Email** | Bắt buộc với người có tài khoản, dạng `name@domain`, tối đa 100 ký tự, không trùng tài khoản đang hoạt động | MSG11 · Trùng: MSG12 | `isValidEmail` | `data-rule="email"` `maxlength="100"` |
| **Mật khẩu mới** (đăng ký, đặt lại, đổi) | **8–64 ký tự, có ít nhất 1 chữ cái và 1 chữ số** (DBR-02); khi đổi phải khác mật khẩu hiện tại | Ngắn: MSG46 · Thiếu chữ/số: `EXTRA_PASSWORD_RULE` · Trùng cũ: MSG48 | `isValidPassword` + `checkPassword(new, oldHash)` | `data-rule="password"` |
| **Nhập lại mật khẩu** | Bắt buộc, giống hệt ô mật khẩu mới | MSG47 | so sánh chuỗi | `data-match="password"` |
| **Họ tên** | 2–50 ký tự sau khi trim; chỉ chữ cái (có dấu tiếng Việt), khoảng trắng, dấu nháy `'`, gạch nối `-`; không số, không ký hiệu (DBR-01) | MSG09 / MSG19 / `EXTRA_FULL_NAME_RULE` | `isValidFullName` | `data-rule="fullName"` `maxlength="50"` |
| **Ngày sinh** | Bắt buộc, không ở tương lai, tuổi ≤ 100 (DBR-04). Khách **không tự sửa** sau đăng ký (CBR-30) | MSG15 / `EXTRA_DOB_TOO_OLD` · Khách bấm sửa: MSG60 | `isValidDateOfBirth` | `data-rule="dateOfBirth"` |
| **Mã xác thực / mã khôi phục** | Đúng 6 chữ số, dùng 1 lần (DBR-03), hiệu lực 15 phút (P-15) | Sai dạng: `EXTRA_CODE_FORMAT` · Sai mã: MSG43 · Hết hạn: MSG44 | `isValidVerificationCode` | `data-rule="verificationCode"` `inputmode="numeric"` `maxlength="6"` |
| **Ảnh đại diện** | JPG/PNG; 200×200 → 2000×2000 px; cắt giữa thành hình vuông, lưu 256×256; **chỉ để hiển thị, không dùng check-in** (CBR-37) | MSG20 | `isValidImageFile` + đọc kích thước bằng `ImageIO` | `isValidImageFile(file)` |
| **Ô tìm kiếm blog** | Không bắt buộc, tối đa 100 ký tự; trim, không phân biệt hoa thường và dấu (GBR-19) | — | `SqlUtil.toContainsPattern` + `COLLATE Vietnamese_CI_AI` | `maxlength="100"` |

Luật chung áp cho mọi chuỗi: **trim 2 đầu** trước khi kiểm tra và lưu (trừ mật khẩu — giữ nguyên từng ký tự).
Mật khẩu **không bao giờ** in ra log, gửi email hay trả về giao diện (DBR-02); lưu bằng BCrypt (`PasswordUtil.hashPassword`).

---

## 2. Luật theo màn hình

### 2.1 Đăng nhập — `/login` (UC-01)

| Mã | Luật |
|---|---|
| UC-01 | Đăng nhập bằng **email hoặc số điện thoại** + mật khẩu, vào đúng khu của vai trò (`Role.getHomePath()`) hoặc quay lại `returnUrl` (GBR-31). |
| UC-01 | Nếu 1 số điện thoại vừa là khách vừa là nhân viên → **chỉ được đăng nhập bằng email** (xem mục 7, dòng 4). |
| BR-01 | **Tài khoản khách** sai mật khẩu **5 lần liên tiếp** (P-17) → khóa tạm **5 phút** (P-18). **Tài khoản nhân viên không bao giờ tự khóa** (máy quầy dùng chung). |
| BR-02 | Hết thời gian khóa → tự mở, bộ đếm về 0. Đăng nhập đúng cũng đưa bộ đếm về 0. |
| CBR-96 | Tài khoản chỉ bị chặn trong 3 trường hợp: nhân viên đã nghỉ (Inactive), Admin khóa (Locked), khách đang khóa tạm (BR-01). |
| CBR-24 | Lễ tân tạo hồ sơ cho khách mua gói từ 1 tháng trở lên thì tạo luôn tài khoản đăng nhập bằng email của hồ sơ. Tài khoản tạo ra **chưa có mật khẩu và không gửi email**; khách tự đặt mật khẩu sau qua Quên mật khẩu (CBR-31). |
| CBR-31 | Tài khoản Lễ tân tạo (CBR-24) **không đăng nhập được** tới khi khách đặt mật khẩu qua **Quên mật khẩu** (UC-03). Trước đó, mọi lần đăng nhập đều báo MSG38 và chỉ sang Quên mật khẩu. |
| CBR-21 | Hồ sơ Dependent (trẻ em) **không có tài khoản**. |
| Session | Đăng nhập thành công: lưu `accountId`, `role`, `displayName` vào session, timeout 7200 giây, cập nhật `Accounts.LastLoginAt`. |

**Thứ tự kiểm tra ở `AuthService.login` (khớp đúng từng câu thông báo):**
```
B1 identifier trống → MSG39; mật khẩu trống → MSG09
B2 tìm Person theo email (có '@') hoặc Phone, chỉ lấy Person IsReleased = 0 → không có: MSG33/MSG34 (không nói rõ "không tồn tại")
B3 lấy Account theo PersonId → không có account (khách vãng lai, Dependent): như sai mật khẩu
B4 Account.Status = 'Inactive' hoặc 'Locked' → MSG37
B5 khách + LockedUntil > bây giờ → MSG35 (kèm mm:ss còn lại)
   khách + LockedUntil ≤ bây giờ (đã hết khóa) → reset FailedLoginCount = 0, LockedUntil = NULL (BR-02), đi tiếp
B6 PasswordHash NULL → MSG38
B7 sai mật khẩu:
     khách   → FailedLoginCount + 1; đủ P-17 → LockedUntil = now + P-18 phút → MSG35; chưa đủ → MSG33 (còn n lần)
     nhân viên → MSG34 (không đếm, không khóa)
B8 đúng → FailedLoginCount = 0, LastLoginAt = now, tạo session (đổi session id chống session fixation), ghi AuditLog nếu cần
```

### 2.2 Đăng ký — `/register` (UC-05, UC-IDENT, UC-Claim)

SRS chia **2 bước**: kiểm tra SĐT → nhập thông tin. Kết quả bước kiểm tra SĐT quyết định nhánh:

| SĐT nhập vào khớp với… | Kết quả | Mã luật / thông báo |
|---|---|---|
| Không khớp hồ sơ nào | Sang bước nhập thông tin → gửi mã tới email → nhập đúng mã → tạo Person + Customer (Self, New) + Account | UC-05 · MSG50, MSG42, MSG54 |
| Chỉ khớp hồ sơ **Inactive** (đã nhả số) | Tạo mới bình thường | CBR-23, DBR-28 |
| Hồ sơ Self đang hoạt động, **chưa có tài khoản**, **có email**, khách nhập **đúng** email đó | Gửi mã tới email của hồ sơ → đúng mã + đặt mật khẩu → **gắn tài khoản vào hồ sơ cũ**, không tạo hồ sơ mới | CBR-20 (UC-Claim) |
| Như trên nhưng khách nhập **email khác** | Không kích hoạt; báo SĐT đã đăng ký với email khác (che email) | CBR-25 · MSG52 (`maskEmail`) |
| Hồ sơ Self đang hoạt động, chưa có tài khoản, **không có email** | Không kích hoạt online; mời ra quầy | CBR-27 · MSG53 |
| Hồ sơ **đã có tài khoản** | Dừng; mời Đăng nhập / Quên mật khẩu | CBR-26 · MSG51 |
| Chỉ trùng **số giám hộ** của hồ sơ Dependent | Bỏ qua — người giám hộ đăng ký như khách mới | CBR-28 |

Luật khác của đăng ký:
- Email không được trùng tài khoản đang hoạt động (DBR-23, MSG12); gửi mã trước khi tạo tài khoản; **gửi email lỗi → không tạo tài khoản** (API-03).
- Ngày sinh dưới 10 tuổi (P-05) vẫn đăng ký được, chỉ bị chặn mua gói PT / đặt buổi PT về sau (CBR-13…15). Prototype hiện gợi ý dưới ô ngày sinh.
- Mã khách `KH-nnnn` do hệ thống sinh, không bao giờ đổi (DBR-09). Hồ sơ mới trạng thái **New** (DBR-34).
- Tài khoản khách chỉ sinh từ 2 nguồn: tự đăng ký online, hoặc Lễ tân tạo tại quầy. Admin không tạo tài khoản khách (CBR-95).

### 2.3 Mã xác thực — dùng chung cho Đăng ký, Kích hoạt, Quên mật khẩu, Đổi email (CBR-94)

| Luật | Giá trị |
|---|---|
| Hiệu lực mã | 15 phút (P-15), có đồng hồ mm:ss (GBR-27) |
| Nhập sai | Tối đa 5 lần (P-17); lần sai thứ 5 → **hủy mã + khóa xác thực email đó 5 phút** (P-18) — trong lúc khóa không nhập mã, không xin mã mới → MSG61 |
| Xin mã mới | Chỉ sau **60 giây** kể từ lần trước (cố định, không cấu hình); **lần xin thứ 5 trong 1 giờ** → khóa 5 phút như trên |
| Mã mới | Luôn **hủy mã cũ** — chỉ mã mới nhất theo (Email, Purpose) có hiệu lực |
| Kênh gửi | Chỉ email, không SMS (GBR-15) |

Bảng lưu: `VerificationCodes` (Purpose `SignUp` / `Claim` / `Recovery` / `EmailChange`). Số lần xin trong 1 giờ = đếm dòng cùng Email có `SentAt` trong 60 phút gần nhất.

### 2.4 Quên mật khẩu — `/forgot-password` (UC-03), 3 bước

| Bước | Luật |
|---|---|
| 1. Nhập email | Chỉ khôi phục qua **email đã đăng ký**, không qua SĐT (BR-03). Không có tài khoản với email này → MSG41. |
| 2. Nhập mã | 6 số, hạn 15 phút (BR-04), các luật mục 2.3. |
| 3. Đặt mật khẩu mới | 8–64, có chữ + số, nhập lại khớp. Thành công → **đăng xuất mọi phiên** (CBR-93) → MSG49 → về trang đăng nhập. Nếu tài khoản đang khóa tạm → mở khóa, bộ đếm về 0. |

Áp dụng cho mọi vai trò (khách và nhân viên). Tài khoản chưa có mật khẩu (Lễ tân tạo tại quầy) đặt mật khẩu lần đầu bằng chính luồng này (CBR-31). Đăng nhập bằng email gặp MSG38 → bấm "Đặt mật khẩu" → sang đây, ô email điền sẵn; đăng nhập bằng SĐT thì ô email để trống.

### 2.5 Cài đặt tài khoản — `/account` (UC-04), 3 tab

**Tab Hồ sơ**
| Mã | Luật |
|---|---|
| CBR-30 | Khách **chỉ tự sửa**: ảnh đại diện, email, SĐT, mật khẩu. Họ tên, ngày sinh, ảnh check-in, thông tin giám hộ → **chỉ đọc**, gợi ý "do quầy lễ tân cập nhật" (GBR-25, MSG60). |
| BR-05 | Đổi email hoặc SĐT phải xác minh bằng mã gửi tới **email mới** (đổi SĐT: mã gửi tới email hiện tại theo SRS 1.4.1); giá trị mới vẫn qua kiểm tra trùng (DBR-22, DBR-23). |
| CBR-29 | Đổi email xong → **báo cho email cũ** + **đăng xuất mọi phiên** → MSG56. |
| CBR-37 / CBR-38 | Ảnh đại diện chỉ hiển thị; ảnh check-in thuộc hồ sơ, chỉ Lễ tân chụp, khách không sửa/xóa. Không có ảnh → hiển thị chữ cái đầu tên. |
| CBR-91 | Mỗi người chỉ sửa hồ sơ của chính mình. |

**Tab Mật khẩu** — nhập mật khẩu hiện tại (phải đúng), mật khẩu mới (8–64, chữ + số, **khác mật khẩu hiện tại** — MSG48), nhập lại. Thành công → đăng xuất **các phiên khác**, giữ phiên hiện tại (CBR-93, `SessionRegistryListener.invalidateSessionsOfAccount(id, currentSessionId)`) → MSG49.

**Tab Hồ sơ phụ thuộc** (chỉ khách) — chỉ xem danh sách hồ sơ Dependent mà người này là giám hộ: họ tên, tuổi (tính từ ngày sinh, **dưới 10 tuổi** gắn nhãn không đặt được PT), mã khách, gói hiện tại + hạn. Người giám hộ **không sửa được gì** trên hồ sơ con.
Cách tìm (theo schema hiện tại, không có khóa ngoại giám hộ): `Customers.CustomerType = 'Dependent'` và (`GuardianPhone` hoặc `GuardianPhone2`) = SĐT của người đang đăng nhập.

### 2.6 Trang chủ — `/` (UC-06, SRS 2.1.1)

Chỉ hiển thị, không có ô nhập; khách chưa đăng nhập xem được.

| Khối | Luật lấy dữ liệu |
|---|---|
| Số PT | Đếm thật `Employees` có `Position = 'PT'` và `Status = 'Working'`. |
| Giờ mở cửa | Lấy từ P-19 (và ngoại lệ trong `OperatingHourExceptions`); hiện cả ở footer. |
| Bảng giá gói tập | `Packages` loại Membership/DayPass, `IsActive = 1`, sắp **ngắn → dài hạn**; không có gói → hiện thông báo. Tự tính "giá tương đương mỗi tháng" (gói > 1 tháng) và "tiết kiệm" so với gói 1 tháng. Nhãn nổi bật "Phổ biến nhất" / "Tiết kiệm nhất": mỗi nhãn gắn đúng 1 gói. |
| Bảng giá PT | `Packages` loại PTSingle/PTCouple/SingleSession đang bán; số người (1 hoặc 2); cách mua: gói PT dài hạn đăng ký **tại quầy**, chỉ **buổi lẻ** đặt online sau khi đăng nhập. |
| Danh sách PT | Chỉ PT đang làm; avatar = **chữ cái đầu tên** (không dùng ảnh thật), họ tên, chuyên môn (`Employees.Specialty`), kinh nghiệm (`YearsOfExperience` năm + số buổi Completed đếm từ `Sessions`; số liệu quảng bá). **Không hiện SĐT/email.** |
| Bài viết mới | Tối đa **3** bài Active mới nhất; không có bài → ẩn cả khối. |
| Nút mua gói | Chưa đăng nhập → sang đăng ký/đăng nhập rồi quay lại mua đúng gói đã chọn (dùng `returnUrl`). |

### 2.7 Blog — `/blog`, `/blog/detail?id=` (UC-06, SRS 2.2)

| Mã / mục | Luật |
|---|---|
| CBR-100 | Chỉ bài **Active** hiện trên trang công khai; chuyển Inactive → **biến mất ngay**. |
| 2.2.1 | Tìm theo **tiêu đề và đoạn tóm tắt**, không phân biệt hoa thường/dấu; lọc 1 danh mục: Tất cả / Tập luyện / Dinh dưỡng / Tin tức (DB lưu `Training` / `Nutrition` / `News`); 2 điều kiện kết hợp AND. |
| 2.2.1 | **6 bài/trang** (khác mặc định 10 dòng của GBR-18), mới nhất trước; hiện tổng số bài khớp; đổi từ khóa/danh mục → về trang 1; không có kết quả → MSG01. |
| Thẻ bài | Tiêu đề 5–200 ký tự (tối đa 2 dòng), tóm tắt ≤ 300 ký tự (tối đa 3 dòng) — **không có tóm tắt thì lấy phần đầu nội dung**; ngày đăng dd/mm/yyyy; **không có ảnh bìa → ảnh mặc định theo danh mục**. |
| 2.2.2 | Chi tiết: ảnh bìa lớn, danh mục, tiêu đề, ngày đăng, **thời gian đọc** (tự tính từ độ dài nội dung), nội dung định dạng (≤ 20.000 ký tự). |
| 2.2.2 | Gợi ý tối đa **3 bài cùng danh mục**, trừ bài đang xem, mới nhất trước; không có → ẩn khối. |
| 2.2.2 | Bài đã gỡ hoặc không tồn tại → **không lộ nội dung**, chỉ hiện thông báo ngắn + link về danh sách (prototype: về `/blog` kèm toast "Bài viết này không còn hiển thị"). |

**Lưu ý bảo mật:** nội dung blog là HTML do Manager soạn, hiển thị có định dạng nên **không dùng `c:out`** được → phải **lọc HTML khi lưu** (chỉ cho phép thẻ p, h2–h4, ul/ol/li, strong/em, a, img; bỏ `<script>`, thuộc tính `on*`, `javascript:`). Tiêu đề, tóm tắt, danh mục vẫn dùng `c:out`.
Thời gian đọc gợi ý: số từ ÷ 200, làm tròn lên, tối thiểu 1 phút.

---

## 3. Luật chung áp dụng cho Phase 1 (GBR)

| Mã | Tóm tắt | Áp vào |
|---|---|---|
| GBR-01 | Giao diện tiếng Việt, ngày dd/mm/yyyy, giờ HH:mm, múi giờ UTC+7; giá trị trạng thái lưu tiếng Anh, hiển thị tiếng Việt | Mọi màn |
| GBR-07/08 | Thao tác nhạy cảm ghi AuditLog thành câu đọc được | Đổi email, đổi SĐT, đặt lại mật khẩu |
| GBR-10 | Chỉ làm được việc vai trò cho phép | Filter |
| GBR-12 | Mỗi người đúng 1 hồ sơ khách đang hoạt động | Đăng ký, kích hoạt |
| GBR-13 | Mọi luật kiểm lại ở server; tranh chấp giải bằng ràng buộc DB | Trùng SĐT/email (unique index) |
| GBR-15 | Mã và link chỉ gửi email, không SMS | Đăng ký, khôi phục |
| GBR-17 | Ô bắt buộc có dấu *, kiểm tra khi rời ô và khi gửi, lỗi đỏ dưới ô | Mọi form |
| GBR-19 | Tìm kiếm trim, không phân biệt hoa thường và dấu | Blog |
| GBR-21 | Danh sách rỗng có câu thông báo | Blog |
| GBR-22 | Khóa nút gửi khi đang xử lý; lỗi thì giữ nguyên dữ liệu đã nhập | Mọi form |
| GBR-24 | Rời form chưa lưu phải xác nhận | Tab Hồ sơ |
| GBR-25 | Ô không được sửa: nền xám + gợi ý ai sửa được | Tab Hồ sơ |
| GBR-27 | Trạng thái có hạn hiện đồng hồ mm:ss, về 0 thì làm mới | Mã xác thực, khóa tạm |
| GBR-30 | Ô mật khẩu che ký tự, có nút hiện/ẩn | Mọi ô mật khẩu |
| GBR-31 | Hết phiên → đăng nhập lại và quay về đúng màn | AuthFilter, `returnUrl` |
| GBR-34 | Toast tự ẩn 3–5 giây, toast lỗi ở lại tới khi đóng | Mọi màn |

## 4. Tham số liên quan (bảng `SystemConfigs`)

| Mã | Ý nghĩa | Giá trị seed | Dùng ở |
|---|---|---|---|
| P-05 | Tuổi tối thiểu dùng PT | 10 | Nhãn "dưới 10 tuổi" ở đăng ký, tab Hồ sơ phụ thuộc |
| P-15 | Hiệu lực mã xác thực / mã khôi phục (phút) | 15 | Mọi mã xác thực |
| P-16 | Đầu số di động hợp lệ | `03,05,07,08,09` | Kiểm tra SĐT (xem mục 7, dòng 6) |
| P-17 | Số lần sai trước khi khóa tạm | 5 | Đăng nhập khách, nhập mã |
| P-18 | Thời gian khóa tạm (phút) | 5 | Đăng nhập khách, nhập mã |
| P-19 | Giờ mở cửa mặc định | `05:30-22:00` | Trang chủ, footer |
| (cố định) | Khoảng cách tối thiểu giữa 2 lần xin mã | 60 giây | `AppConstants.CODE_RESEND_GAP_SECONDS` |

Đọc qua `SystemConfigService`, không hard-code (agents.md Phần 13).

## 5. Thông báo dùng trong Phase 1

Đã có sẵn tiếng Việt trong `constant/Messages.java` và `js/common.js` (`MESSAGES`):
MSG01, MSG04–MSG07, MSG09–MSG15, MSG19, MSG20, MSG23, MSG33–MSG61, cùng `EXTRA_PASSWORD_RULE`, `EXTRA_FULL_NAME_RULE`,
`EXTRA_CODE_FORMAT`, `EXTRA_DOB_TOO_OLD` (luật DBR có nhưng SRS chưa có mã).
Quy tắc: **không tự viết câu thông báo mới** trong Service/Servlet nếu đã có mã.
Đăng nhập sai **không bao giờ** nói rõ "email không tồn tại" hay "sai mật khẩu" riêng — luôn dùng MSG33/MSG34 để không lộ tài khoản nào có thật.

---

## 6. Dữ liệu cần có trong DB cho Phase 1 (đối chiếu schema)

| Nhu cầu | Bảng / cột | Tình trạng |
|---|---|---|
| Email, SĐT, họ tên, ngày sinh, ảnh đại diện | `Persons` | ✅ |
| Mã khách, loại Self/Dependent, giám hộ, trạng thái | `Customers` | ✅ |
| Hash mật khẩu, trạng thái, đếm sai, khóa tạm | `Accounts` | ✅ (seed gốc có hash giả → chạy `3_Dev_Passwords.sql`) |
| Mã xác thực + khóa theo email | `VerificationCodes` | ✅ |
| ~~Link đặt mật khẩu~~ cho tài khoản Lễ tân tạo | `VerificationCodes` (Purpose `PasswordSetup`, `TokenHash`) | ⚪ Bỏ luồng từ 27/09 — cột giữ lại, không dùng (xem `docs/notes.md` D6) |
| Gói, giá, quyền lợi | `Packages` | ✅ |
| **Chuyên môn, kinh nghiệm PT** ở trang chủ | `Employees.Specialty`, `Employees.YearsOfExperience` | ✅ đã thêm 24/09 (migration 001) |
| Bài viết, danh mục, tóm tắt, ảnh bìa | `BlogPosts` | ✅ |
| Giờ mở cửa | `SystemConfigs` P-19 + `OperatingHourExceptions` | ✅ |
| FAQ trang chủ | — | Nội dung tĩnh trong JSP (SRS không yêu cầu quản lý) |

---

## 7. Mâu thuẫn trong phạm vi Phase 1 — cần chốt

| # | Chủ đề | Nguồn A | Nguồn B | Tạm dùng |
|---|---|---|---|---|
| 1 | Độ dài mật khẩu đăng ký | SRS 1.2.2: 6–64 | DBR-02, SRS 1.3.3, 1.4.2, prototype: 8–64 có chữ + số | **8–64 có chữ + số** |
| 2 | Số bước đăng ký | SRS 1.2: 2 bước (kiểm SĐT → nhập thông tin) | Prototype: gộp 1 form | Làm 1 form như prototype nhưng **server vẫn chạy đủ nhánh UC-IDENT** (mục 2.2) trước khi gửi mã |
| 3 | Dung lượng ảnh đại diện | SRS 1.4.1: 2 MB | DBR-08: 5 MB | **2 MB** cho avatar (chặt hơn, riêng màn này), 5 MB cho ảnh khác |
| 4 | 1 người vừa khách vừa nhân viên | SRS 3.2: tài khoản thuộc khách **hoặc** nhân viên; UC-01: trùng SĐT phải dùng email | DB: 1 Person = 1 Account cho cả 2 vai trò (Person 22) | Đăng nhập xong nếu Person có cả 2 hồ sơ → hỏi chọn vai trò (chờ chốt, notes D1.2) |
| 5 | Mã khách ví dụ | SRS 1.4.3: `SG-0242` | DBR-09, DB: `KH-nnnn` | **KH-nnnn** |
| 6 | Đầu số điện thoại | P-16 là "cấu hình triển khai" (SRS) | Seed lưu `03,05,07,08,09` (2 số) trong SystemConfigs; `application.properties` mẫu lưu 3 số | Chốt 1 nơi; tạm kiểm tra đúng 10 số bắt đầu 0 |
| 7 | Giờ mở cửa | Prototype trang chủ: 06:00–22:00 | Seed P-19: 05:30–22:00 | **Theo DB** |
| 8 | Giá gói PT trang chủ | Prototype: PT 1 tháng 4.500.000đ, buổi lẻ 550.000đ, tên PT Hoàng Long… | Seed: 8.800.000đ, 450.000đ, PT Đinh Công Sơn… | **Theo DB** (trang chủ query DB, prototype chỉ là mẫu) |
| 9 | Đổi SĐT gửi mã tới đâu | BR-05: mã gửi tới **email mới** (câu gộp email + SĐT) | SRS 1.4.1: đổi SĐT gửi mã tới **email hiện tại** | Đổi email → email mới; đổi SĐT → email hiện tại |
| 10 | Màn đổi mật khẩu | SRS 1.4.2 ghi chú "đang lỗi màn" | — | Code theo bảng trường hiện có; nhắc người viết SRS sửa |
