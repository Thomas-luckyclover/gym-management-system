# HƯỚNG DẪN SỬA LỖI CHỨC NĂNG "ĐĂNG KÝ TÀI KHOẢN" (Register Account)

> Đây là tài liệu **SỬA CODE HIỆN CÓ**, không phải viết mới từ đầu. Chỉ patch đúng phần sai; không đổi kiến trúc, không refactor những gì đang đúng.
> Nguồn đối chiếu: `SRS - G5.docx` (UC-05, UC-IDENT, UC-Claim, CBR-17→28, CBR-94, CBR-01). Mọi rule trích lại đúng ID để so ngược bản gốc.

---

## 0. Tóm tắt 2 lỗi đang có trong code

| # | Lỗi hiện tại | Nằm ở đâu (logic) | Rule bị vi phạm |
|---|---|---|---|
| 1 | Bước 1 chỉ validate **định dạng** SĐT (đủ 10 số) rồi cho qua Bước 2 luôn, **không query DB** để kiểm tra hồ sơ đã tồn tại. Việc kiểm tra trùng chỉ chạy ở bước submit cuối cùng → bắt người dùng gõ hết form rồi mới báo MSG51/MSG53. | Hàm xử lý Bước 1 (ví dụ đang tên `handlePhoneStep` hay tương đương) | CBR-26, CBR-27 |
| 2 | Nhánh kích hoạt hồ sơ có sẵn (Claim) đang dùng **chung 1 form** với đăng ký mới (hỏi cả họ tên, ngày sinh dù đã có sẵn), **không gửi mã xác thực**, so khớp email đúng là **tạo tài khoản ngay** (hàm kiểu `claimExistingAccount`), và **đổi `Customer.status` từ New → Active** sau khi tạo tài khoản. | Hàm xử lý claim (`claimExistingAccount` hoặc tương đương) | CBR-20, CBR-25, CBR-94, CBR-01 |

---

## 1. Fix #1 — Bước 1 phải tra DB ngay, không được "cho qua"

### Hành vi sai hiện tại
Nhập SĐT → chỉ check đủ 10 số → luôn luôn chuyển sang Bước 2 (form đầy đủ) bất kể SĐT đó thuộc về ai. Lỗi chỉ xuất hiện sau khi bấm "Đăng ký" ở cuối.

### Hành vi đúng phải sửa thành
Ngay khi submit Bước 1 (sau khi validate định dạng đủ 10 số, đầu số hợp lệ), **gọi query tìm profile Self theo SĐT** và rẽ nhánh **ngay lập tức**, trước khi hiển thị bất kỳ field nào khác:

```
validate format SĐT (10 số, bắt đầu 0, đầu số hợp lệ) → sai → MSG13

profile = findSelfProfileByPhone(phone)   // CHỈ tìm trong profile Self, bỏ qua SĐT giám hộ (CBR-28)

if profile == null OR profile.status == "Inactive":     // CBR-23: Inactive coi như không tồn tại
    → chuyển Màn 2 (Đăng ký mới), toast MSG50

else if profile.hasAccount():
    → DỪNG NGAY TẠI MÀN 1, hiện MSG51 inline
    → KHÔNG chuyển màn, KHÔNG render field nào khác          (CBR-26)

else if profile.email is empty:
    → DỪNG NGAY TẠI MÀN 1, hiện MSG53 inline
    → KHÔNG chuyển màn                                        (CBR-27)

else:  // có profile, chưa có account, có email
    → chuyển Màn 2 – NHÁNH CLAIM (xem Fix #2)
```

### Việc cần sửa cụ thể trong code
- Chuyển lời gọi query DB từ chỗ đang nằm ở bước submit cuối **lên đầu, ngay trong hàm xử lý submit của Bước 1**.
- Xoá bỏ hành vi "luôn return sang Bước 2 form đầy đủ" mặc định — thay bằng rẽ 3 nhánh như trên.
- Đảm bảo khi rơi vào case MSG51 hoặc MSG53, **UI không render tiếp bất kỳ input nào khác** ngoài ô SĐT đã nhập + dòng lỗi.
- Query trùng SĐT phải lọc `customer_type = Self`; bỏ qua toàn bộ SĐT giám hộ (guardian) gắn trên Dependent profile (CBR-28) — nếu code hiện tại đang query chung cả bảng `CustomerPhone` không phân biệt loại, phải thêm điều kiện lọc này.

---

## 2. Fix #2 — Nhánh Claim: tách form, thêm bước gửi mã, không đổi status

### Hành vi sai hiện tại
1. Dùng chung form đăng ký mới: hỏi cả họ tên, ngày sinh (dữ liệu này đã có sẵn trên hồ sơ, hỏi xong rồi bỏ, không dùng vào đâu).
2. Không gửi mã xác thực — khi email nhập vào khớp với email trên hồ sơ, code gọi thẳng hàm tạo tài khoản (`claimExistingAccount`) và set mật khẩu luôn từ form.
3. Sau khi tạo tài khoản, code đang set `Customer.status = Active`.

### Hành vi đúng phải sửa thành

**a) Form Claim chỉ có 1 field: email.**
Xoá field họ tên và ngày sinh khỏi form này (không phải xoá khỏi form đăng ký mới — 2 form giờ phải là 2 UI khác nhau, rẽ theo kết quả Bước 1).

**b) So khớp email — không khớp thì dừng, không gửi gì:**
```
if normalize(emailInput) != normalize(profile.email):     // CBR-25
    → MSG52 (kèm email che một phần), dừng tại đây
    → KHÔNG gửi mã, KHÔNG gọi hàm tạo account
```

**c) Khớp thì phải gửi mã và bắt xác thực đúng mã TRƯỚC KHI tạo account** — đây là chỗ đang thiếu hoàn toàn, phải thêm mới:
```
if normalize(emailInput) == normalize(profile.email):
    sendVerificationCode(to = profile.email)   // gửi tới email TRÊN HỒ SƠ, không phải input
    → chuyển màn nhập mã (6 số)
```
Áp dụng đúng CBR-94 cho bước nhập mã này (nếu logic rate-limit/khoá 5 lần sai đã có sẵn cho Forgot Password / đăng ký mới thì **tái sử dụng lại, không viết mới**):
- Sai tối đa 5 lần (P-17) → khoá xác thực email đó 5 phút (P-18) → MSG61.
- Gửi lại mã: cách nhau ≥ 60 giây, tối đa 5 lần/giờ, lần thứ 5 cũng khoá 5 phút.

**d) Chỉ sau khi mã đúng mới cho nhập mật khẩu và tạo account:**
```
onCodeVerified():
    hiển thị màn xác nhận: full_name, date_of_birth (READ-ONLY, lấy từ profile có sẵn — không hỏi lại)
    hỏi: New password + Confirm password
    submit → createAccount(profile, passwordHash, status = "Active")
```

**e) KHÔNG được đổi `Customer.status`** ở bất kỳ đâu trong luồng claim này:
```
// XOÁ dòng đang set:
profile.status = "Active"   // ❌ XOÁ dòng này, vi phạm CBR-01

// Giữ nguyên nguyên trạng:
// profile.status không đổi — chỉ đổi khi khách thanh toán gói (nằm ở luồng khác, không đụng vào đây)
```

### Việc cần sửa cụ thể trong code
- Tách hàm claim hiện tại (đang làm tất cả trong 1 bước: nhận input → so khớp → tạo account) thành **3 hàm riêng biệt theo đúng thứ tự bắt buộc**: `verifyClaimEmail()` → `sendVerificationCode()` + `verifyCode()` → `setPasswordAndAttachAccount()`. Không được gọi tắt từ bước (1) thẳng đến bước (3).
- Xoá field `fullName`, `dateOfBirth` khỏi payload/form của bước claim.
- Xoá dòng code set `status = "Active"` (hoặc tương đương) trong hàm tạo account của nhánh claim.
- Đảm bảo mật khẩu chỉ được thu thập ở bước cuối (sau verify mã), không thu ở bước nhập email.

---

## 3. Checklist test lại sau khi sửa (map đúng 3 case đã fail)

| Người test | SĐT | Kỳ vọng sau khi sửa |
|---|---|---|
| An — đã có tài khoản | 0901234501 | Nhập SĐT xong → dừng ngay, hiện MSG51. Không thấy field nào khác. |
| Dũng — hồ sơ không có email | 0901234505 | Nhập SĐT xong → dừng ngay, hiện MSG53. Không thấy field nào khác. |
| Yến — hồ sơ chưa có tài khoản, có email | 0935000105 | Nhập SĐT → chỉ hiện form 1 field email → nhập đúng email → nhận mã qua email trên hồ sơ → nhập đúng mã → hiện họ tên/ngày sinh readonly + ô đặt mật khẩu → submit xong → tài khoản tạo thành công, **`Customer.status` giữ nguyên `New`** (kiểm tra bằng query DB trước/sau, phải bằng nhau). |

Thêm 2 case nên test kèm vì cùng nhóm rule:
- SĐT chỉ trùng với profile đang **Inactive** → phải cho đăng ký mới bình thường (không bị chặn) — CBR-23.
- SĐT trùng với **SĐT giám hộ** trên 1 Dependent profile (không phải SĐT chính chủ) → phải cho đăng ký mới bình thường, không bị coi là trùng — CBR-28.

---

## 4. Không đụng vào

- Logic đăng ký mới hoàn toàn (nhánh không có profile trùng) — nếu đang chạy đúng thì giữ nguyên, không sửa.
- UC-16 (lễ tân tạo hồ sơ tại quầy) — khác luồng, không liên quan đến 2 lỗi này.
- Cơ chế lock 5 lần sai / P-17 / P-18 nếu đã implement đúng ở nơi khác (Forgot Password) — chỉ cần **tái sử dụng**, không viết lại.
