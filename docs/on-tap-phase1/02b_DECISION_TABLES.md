# 02b · Decision Tables — Authentication (học bằng bảng, không cần đọc code)

> Cách đọc: **mỗi dòng = 1 trường hợp (rule)**. Các cột bên trái là **điều kiện**, cột bên phải là **kết quả**.
> `Y` = đúng · `N` = sai · `—` = không quan tâm (không cần xét, vì đã rẽ nhánh trước đó).
> Thứ tự cột = **thứ tự server kiểm tra** → gặp điều kiện "chặn" đầu tiên là dừng. Học thuộc thứ tự cột là thuộc luôn code.
> Cột "Ví dụ seed" dùng tài khoản trong `4_Phase1_Seed.sql` (mật khẩu đúng `Gms@2026`) để bấm thử.

Mẹo học 1 bảng trong 5 phút: **(1)** đọc tên các cột điều kiện từ trái sang phải thành 1 câu → **(2)** che cột kết quả, nhìn từng dòng đoán kết quả → **(3)** mở ra so.

---

## DT-1 · Đăng nhập `/login` (UC-01, BR-01, BR-02, CBR-96, CBR-31)

### Dạng 1 — mỗi dòng 1 trường hợp

| # | Ô trống? | Tìm thấy tài khoản? (email/SĐT, hồ sơ chưa nhả) | Status = Active? | Đang khóa tạm (LockedUntil > now)? | Đã có mật khẩu? | Vai trò | Mật khẩu đúng? | Sai lần thứ mấy | **Kết quả** | Ví dụ seed |
|---|---|---|---|---|---|---|---|---|---|---|
| R1 | Trống cả 2 | — | — | — | — | — | — | — | MSG39 dưới ô 1 + "Mật khẩu là bắt buộc." dưới ô 2 | bấm luôn |
| R2 | Trống email/SĐT | — | — | — | — | — | — | — | **MSG39** "Nhập email hoặc số điện thoại." | |
| R3 | Trống mật khẩu | — | — | — | — | — | — | — | **MSG09** "Mật khẩu là bắt buộc." | |
| R4 | N | **N** | — | — | — | — | — | — | **MSG34** (câu chung — không lộ "không tồn tại") | `khongtontai@gmail.com` · `lan.dang85@gmail.com` (có hồ sơ, không TK) · `tung.mai@gmail.com` (đã nhả) |
| R5 | N | Y | **N** (Locked/Inactive) | — | — | — | — | — | **MSG37** "…đã ngừng hoạt động…" | `phuc.le@gmail.com` (Admin khóa) · `nam.ho@gms.vn` (nghỉ việc) |
| R6 | N | Y | Y | **Y** | — | — | — | — | **MSG35** "Tài khoản bị khóa 5 phút… Thử lại sau mm:ss." (kể cả nhập ĐÚNG mật khẩu) | `kim.vo@gmail.com` ⏱ |
| R7 | N | Y | Y | Hết hạn khóa | … | … | … | … | Tự mở: FailedLoginCount = 0, LockedUntil = NULL → xét tiếp như R8–R13 (BR-02) | `hong.pham@yahoo.com` |
| R8 | N | Y | Y | N | **N** (hash NULL) | — | — | — | **MSG38** + panel "Đặt mật khẩu" | `khanh.trinh@gmail.com` · `nhung.ho@gmail.com` |
| R9 | N | Y | Y | N | Y | **Nhân viên** | N | bất kỳ | **MSG34**, không đếm, **không bao giờ khóa** | `tam.le@gms.vn` sai 10 lần |
| R10 | N | Y | Y | N | Y | **Khách** | N | 1–3 | FailedLoginCount +1 → **MSG33** "…Bạn còn **(5 − n)** lần thử." | `quan.le92@gmail.com` (đã sai 2 → "còn 2") |
| R11 | N | Y | Y | N | Y | Khách | N | **4** | +1 → "còn 1 lần" | |
| R12 | N | Y | Y | N | Y | Khách | N | **5** | LockedUntil = now + 5' → **MSG35** | `thao.luong@gmail.com` (đã sai 4) sai thêm 1 |
| R13 | N | Y | Y | N | Y | bất kỳ | **Y** | — | Reset bộ đếm, LastLoginAt = now, **session mới**, redirect `returnUrl` an toàn hoặc trang chủ vai trò | `an.nguyen95@gmail.com` → `/customer/dashboard` |

### Dạng 2 — bảng quyết định "cổ điển" (điều kiện là hàng, rule là cột) — dùng khi thầy yêu cầu đúng mẫu SE

| | R4 | R5 | R6 | R8 | R9 | R10 | R12 | R13 |
|---|---|---|---|---|---|---|---|---|
| **C1** Tài khoản tồn tại | N | Y | Y | Y | Y | Y | Y | Y |
| **C2** Status = Active | — | N | Y | Y | Y | Y | Y | Y |
| **C3** Đang khóa tạm | — | — | Y | N | N | N | N | N |
| **C4** Có mật khẩu | — | — | — | N | Y | Y | Y | Y |
| **C5** Là khách hàng | — | — | — | — | N | Y | Y | — |
| **C6** Mật khẩu đúng | — | — | — | — | N | N | N | Y |
| **C7** Lần sai mới ≥ 5 | — | — | — | — | — | N | Y | — |
| A1 Báo MSG34 | X | | | | X | | | |
| A2 Báo MSG37 | | X | | | | | | |
| A3 Báo MSG35 | | | X | | | | X | |
| A4 Báo MSG38 | | | | X | | | | |
| A5 Tăng FailedLoginCount | | | | | | X | X | |
| A6 Báo MSG33 (còn n lần) | | | | | | X | | |
| A7 Ghi LockedUntil = now+5' | | | | | | | X | |
| A8 Tạo session + redirect | | | | | | | | X |

**Trường hợp đặc biệt (chưa chốt):** SĐT vừa của khách vừa của nhân viên → luật UC-01 bắt dùng email; code hiện đăng nhập luôn với vai trò nhân viên (L15).

---

## DT-2 · Đăng ký `/register` — nhánh theo SĐT (UC-IDENT, UC-Claim)

| # | SĐT khớp hồ sơ **Self đang hoạt động**? | Hồ sơ đó đã có tài khoản? | Hồ sơ có email? | Email nhập **trùng** email hồ sơ? | Email nhập đã thuộc người khác? | **Kết quả theo LUẬT** | **Code hiện tại** | Ví dụ seed |
|---|---|---|---|---|---|---|---|---|
| G1 | N (không khớp gì) | — | — | — | **Y** | **MSG12** "Email này đã được dùng…" | ✅ | SĐT mới + `bich.tran@gmail.com` |
| G2 | N | — | — | — | N | Gửi mã **SignUp** → đúng mã → tạo Person + Customer (Self, **New**, KH-nnnn) + Account → **MSG54** | ✅ (commit `6f2511e`: bước `otp`) | `0987000001` + `khach.moi@gmail.com` (mã `112233`) |
| G3 | N (chỉ khớp hồ sơ **đã nhả**) | — | — | — | N | Như G2 (CBR-23, DBR-28) | ✅ như G2 | `0901234512` |
| G4 | N (chỉ khớp **số giám hộ** của Dependent) | — | — | — | N | Như G2 (CBR-28) | ✅ như G2 | `0935000999` |
| G5 | **Y** | **Y** | — | — | — | **MSG51** mời Đăng nhập / Quên mật khẩu (CBR-26) | ✅ | `0901234501` |
| G6 | Y | N | **N** | — | — | **MSG53** mời ra quầy (CBR-27) | ✅ | `0901234505` |
| G7 | Y | N | Y | **N** | — | **MSG52** "…email khác (ye***@gmail.com)…" (CBR-25) | ✅ | `0935000105` + email khác |
| G8 | Y | N | Y | **Y** | — | Gửi mã **Claim** tới **email hồ sơ** → đúng mã + mật khẩu → gắn Account vào hồ sơ cũ, **giữ Status New** (CBR-20) | 🟡 có mã Claim + giữ New, nhưng POST thẳng `step=claim-password` vẫn lọt (F23) | `0935000105` + `yen.doan@gmail.com` (mã `445566`) |

### DT-2b · Đăng ký — kiểm tra dữ liệu (chạy TRƯỚC DT-2, theo thứ tự)

| # | Họ tên 2–50 | Họ tên chỉ chữ/space/'/- | Có ngày sinh | Không ở tương lai | ≤ 100 tuổi | SĐT `0`+9 số | Email hợp lệ ≤100 | Mật khẩu 8–64 chữ+số | Nhập lại khớp | **Kết quả** | Ví dụ |
|---|---|---|---|---|---|---|---|---|---|---|---|
| V1 | N | — | — | — | — | — | — | — | — | "Họ và tên là bắt buộc." | `A` |
| V2 | Y | N | — | — | — | — | — | — | — | `MSG67_FULL_NAME_RULE` | `Nguyễn Văn 2`, `@@@` |
| V3 | Y | Y | N | — | — | — | — | — | — | "Ngày sinh là bắt buộc." | |
| V4 | Y | Y | Y | N | — | — | — | — | — | **MSG15** | ngày mai |
| V5 | Y | Y | Y | Y | N | — | — | — | — | `MSG69_DOB_TOO_OLD` | 1900 |
| V6 | Y | Y | Y | Y | Y | N | — | — | — | **MSG13** | `090123450`, `1901234501`, `09012345ab` |
| V7 | … | … | … | … | … | Y | N | — | — | **MSG11** | `abc@`, SĐT ở ô email |
| V8 | … | … | … | … | … | Y | Y | N | — | `MSG66_PASSWORD_RULE` | `abcdefgh`, `12345678`, `abc123` |
| V9 | … | … | … | … | … | Y | Y | Y | N | **MSG47** | |
| V10 | Y | Y | Y | Y | Y | Y | Y | Y | Y | → sang DT-2 | 8 tuổi (2018) vẫn **được** đăng ký |

---

## DT-3 · Xin mã OTP — `VerificationCodeService.sendCode` (CBR-94) — dùng cho Quên MK, Đăng ký, Kích hoạt, Đổi email

| # | Email đang bị khóa xác thực? | Mã cùng mục đích gửi < 60 giây trước? | Số mã đã gửi trong 60' + 1 ≥ 5? | Gửi email thành công? | **Kết quả** | Ghi DB | Gợi ý hiển thị (dưới nút Gửi lại mã) | Ví dụ seed |
|---|---|---|---|---|---|---|---|---|
| S1 | **Y** | — | — | — | **MSG61** "…khóa 5 phút. Thử lại sau mm:ss" | — | Ẩn | `binh.ta@gmail.com` ⏱ |
| S2 | N | **Y** | — | — | "Vui lòng chờ **n** giây nữa rồi bấm Gửi lại mã." | — | Giữ như trước khi bấm | `khoa.dinh@gmail.com` ⏱ |
| S3 | N | N | **Y** (đây là lần thứ 5) | — | Khóa email 5 phút → **MSG61** | `LockedUntil` vào dòng mã mới nhất | Ẩn (đang khóa) | `manh.ngo@gmail.com` ⏱ |
| S4 | N | N | N | **N** | **MSG04** "Hệ thống đang bận…" | **Không ghi gì** (gửi trước, ghi sau) | Giữ như trước khi bấm | sai App Password |
| S5 | N | N | N | Y | **MSG42** "Mã đã được gửi tới … hiệu lực 15 phút." (resend: MSG45) | INSERT dòng mã mới → mã cũ mất hiệu lực | Còn ≥ 1 lượt: **MSG64** "Còn n lần gửi lại mã trong 60 phút." · Hết lượt (đã 4 mã/giờ): **MSG65** màu cảnh báo | `thao.luong@gmail.com` (chờ > 60 s) |

Số lượt gửi lại = `(P-17 − 1) − số mã đã gửi trong 60'` → sau lần gửi thứ 4 còn 0 (MSG65); bấm lần thứ 5 mới khóa (S3).

## DT-4 · Nhập mã OTP — `VerificationCodeService.verifyCode`

| # | Đúng 6 chữ số? | Email đang khóa? | Có mã mới nhất? | Mã đã dùng? | Mã đã bị hủy (sai ≥ 5)? | Mã hết hạn? | Mã khớp? | Lần sai mới ≥ 5? | **Kết quả** | Gợi ý hiển thị (dưới ô nhập mã) | Ví dụ seed |
|---|---|---|---|---|---|---|---|---|---|---|---|
| O1 | **N** | — | — | — | — | — | — | — | `MSG68_CODE_FORMAT` dưới ô (không hỏi DB) | Không đổi (không tính 1 lần sai) | `12ab`, `12345` |
| O2 | Y | **Y** | — | — | — | — | — | — | **MSG61** | Ẩn | `binh.ta@gmail.com` |
| O3 | Y | N | **N** | — | — | — | — | — | **MSG44** | — | email chưa từng xin mã |
| O4 | Y | N | Y | **Y** | — | — | — | — | **MSG44** (mã dùng 1 lần) | — | nhập lại mã vừa dùng |
| O5 | Y | N | Y | N | **Y** | — | — | — | **MSG44** | — | |
| O6 | Y | N | Y | N | N | **Y** | — | — | **MSG44** | — | `quynh.cao@gmail.com` + `999000` |
| O7 | Y | N | Y | N | N | N | **N** | N | FailedAttempts +1 → **MSG43** | Còn ≥ 2: **MSG62** "Bạn còn n lần nhập mã." · còn 1: **MSG63** màu cảnh báo | `thao…` + `000000` |
| O8 | Y | N | Y | N | N | N | N | **Y** | +1, khóa email 5' → **MSG61** | Ẩn (đang khóa) | sai lần thứ 5 |
| O9 | Y | N | Y | N | N | N | **Y** | — | `UsedAt = now` → **thành công** (trả AccountId) | — (sang bước 3) | `thao…` + `246810` |

Số lần nhập còn lại = `P-17 − FailedAttempts` của mã mới nhất → mã vừa gửi hiện "Bạn còn 5 lần nhập mã."; gửi lại mã mới thì về lại 5.

### DT-3/DT-4 bổ sung · Dòng gợi ý "còn mấy lần" trên màn nhập mã (MSG62–65, `getAttemptInfo`)

| # | Email đang khóa? | Lần nhập còn lại N | Lần gửi lại còn lại M | **Dòng dưới ô mã** | **Dòng dưới nút Gửi lại** |
|---|---|---|---|---|---|
| H1 | **Y** | — | — | *(ẩn, chỉ MSG61)* | *(ẩn)* |
| H2 | N | N ≥ 2 | — | **MSG62** "Bạn còn N lần nhập mã." | — |
| H3 | N | **N = 1** | — | **MSG63** "Nhập sai thêm 1 lần… khóa 5 phút." (màu cảnh báo) | — |
| H4 | N | N = 0 (mã hết hạn/đã dùng/không có) | — | *(ẩn)* | — |
| H5 | N | — | M ≥ 1 | — | **MSG64** "Còn M lần gửi lại mã trong 60 phút." |
| H6 | N | — | **M = 0** | — | **MSG65** "Đã hết lượt gửi lại. Gửi thêm sẽ khóa…" (màu cảnh báo) |

N = 5 − `FailedAttempts` của mã mới nhất · M = 4 − số mã đã gửi trong 60 phút. Ví dụ: vừa xin mã ở bước 1 (1 mã) → "Bạn còn 5 lần nhập mã" + "Còn 3 lần gửi lại mã".

Mẹo: O3–O6 **cùng 1 câu MSG44** "Mã đã hết hạn. Bấm Gửi lại mã" — người dùng chỉ cần biết "mã này không dùng được nữa".

---

## DT-5 · Quên mật khẩu — Bước 1 nhập email (`requestCode`)

| # | Email trống? | Đúng định dạng email? | Có tài khoản với email? | Status = Active? | Đang khóa tạm đăng nhập? | Chưa có mật khẩu? | **Kết quả** | Ví dụ |
|---|---|---|---|---|---|---|---|---|
| F1 | **Y** | — | — | — | — | — | "Email là bắt buộc." | |
| F2 | N | **N** | — | — | — | — | **MSG11** (nhập SĐT cũng vào đây — BR-03) | `0901234501` |
| F3 | N | Y | **N** | — | — | — | **MSG41** "Không tìm thấy tài khoản nào với email này." | `khongtontai@gmail.com` |
| F4 | N | Y | Y | **N** | — | — | **MSG37**, không gửi mã (chốt #3) | `phuc.le@gmail.com` |
| F5 | N | Y | Y | Y | **Y** | — | **Vẫn gửi mã** → DT-3 (chốt #4: đặt lại xong tự mở khóa) | `kim.vo@gmail.com` |
| F6 | N | Y | Y | Y | — | **Y** | **Vẫn gửi mã** → DT-3 (CBR-31) | `khanh.trinh@gmail.com` |
| F7 | N | Y | Y | Y | N | N | Gửi mã → DT-3 | `an.nguyen95@gmail.com` |

## DT-6 · Quên mật khẩu — Bước 3 đặt mật khẩu mới (`handleReset` + `resetPassword`)

| # | Đã xác minh mã (session có accountId)? | Còn trong hạn (≤ ExpiresAt của mã)? | Mật khẩu trống? | 8–64 chữ+số? | Nhập lại khớp? | TK còn Active? | **Kết quả** |
|---|---|---|---|---|---|---|---|
| P1 | **N** (POST thẳng `action=reset`) | — | — | — | — | — | Đưa về bước 1 (chặn nhảy bước) |
| P2 | Y | **N** | — | — | — | — | Xóa tiến độ, **MSG44**, làm lại |
| P3 | Y | Y | **Y** | — | — | — | "Mật khẩu mới là bắt buộc." (giữ bước 3) |
| P4 | Y | Y | N | **N** | — | — | `MSG66_PASSWORD_RULE` (giữ bước 3) |
| P5 | Y | Y | N | Y | **N** | — | **MSG47** (giữ bước 3) |
| P6 | Y | Y | N | Y | Y | **N** (vừa bị Admin khóa) | **MSG37**, về bước 1 |
| P7 | Y | Y | N | Y | Y | Y | Lưu hash + mở khóa tạm (1 transaction) → **đăng xuất MỌI phiên** → `/login` + **MSG49** |

---

## DT-7 · Tài khoản chưa có mật khẩu đặt mật khẩu lần đầu (CBR-24, CBR-31) — qua Đăng nhập → Quên mật khẩu

Luồng link `/set-password` **đã bỏ 27/09**. Tài khoản Lễ tân tạo (PasswordHash NULL) đi qua màn Đăng nhập rồi sang Quên mật khẩu.

| # | Khách gõ gì ở ô Email/SĐT (màn Đăng nhập) | Kết quả ở `/login` | Bấm **Đặt mật khẩu** → `/forgot-password` | Ví dụ seed |
|---|---|---|---|---|
| K1 | **Email** của tài khoản chưa có MK | **MSG38** + panel "Chưa thể đăng nhập" | Bước 1, **ô email điền sẵn** email vừa gõ (session `RECOVERY_PREFILL_EMAIL`, đọc 1 lần). **Không tự gửi mã** | `khanh.trinh@gmail.com` · `nhung.ho@gmail.com` |
| K2 | **SĐT** của tài khoản chưa có MK | **MSG38** + panel | Bước 1, **ô email TRỐNG** | `0901234513` (Khánh) |
| K3 | (tiếp K1/K2) khách bấm Gửi mã → nhập mã → đặt MK | — | Đi đúng DT-5 → DT-4 → DT-6 → `/login` + MSG49 | |

Ghi chú:
- K2 cố ý **không** điền email: nếu điền, ai biết SĐT của khách cũng thấy được email gắn với SĐT đó.
- Email điền sẵn đi qua **session**, không đặt lên URL; F5 lại trang Quên MK là ô trống.
- Quên mật khẩu không kiểm tra PasswordHash → tài khoản NULL vẫn nhận mã bình thường (F10 trong `docs/phase-1/02_Phase1_Test_Scenarios.md`).

---

## DT-8 · Đổi mật khẩu trong `/account` (tab Mật khẩu)

| # | MK hiện tại trống? | MK hiện tại đúng? | MK mới trống? | MK mới 8–64 chữ+số? | MK mới trùng MK hiện tại? | Nhập lại trống? | Nhập lại khớp? | **Kết quả** |
|---|---|---|---|---|---|---|---|---|
| C1 | **Y** | — | — | — | — | — | — | "Vui lòng nhập mật khẩu hiện tại." |
| C2 | N | **N** | — | — | — | — | — | "Mật khẩu hiện tại không đúng." |
| C3 | N | Y | **Y** | — | — | — | — | "Vui lòng nhập mật khẩu mới." |
| C4 | N | Y | N | **N** | — | — | — | "Mật khẩu phải từ 8–64 ký tự…" |
| C5 | N | Y | N | Y | **Y** | — | — | **MSG48** "Mật khẩu mới phải khác mật khẩu hiện tại." |
| C6 | N | Y | N | Y | N | **Y** | — | "Vui lòng xác nhận mật khẩu mới." |
| C7 | N | Y | N | Y | N | N | **N** | **MSG47** |
| C8 | N | Y | N | Y | N | N | Y | Lưu hash → **đăng xuất các phiên KHÁC, giữ phiên này** → toast thành công |

Để ý: kiểm **mật khẩu hiện tại TRƯỚC** mới kiểm mật khẩu mới — không cho kẻ cầm máy người khác "thử" luật mật khẩu mới.

---

## DT-9 · Đổi email / SĐT trong `/account` (BR-05, CBR-29 + đề xuất 🆕 CBR-110/111) — *theo luật, servlet chưa nối*

| # | Loại đổi | Giá trị mới hợp lệ? | Trùng TK/hồ sơ đang hoạt động khác? | Mã gửi tới | Mã đúng (DT-4 O9)? | Kiểm trùng lần 2 lúc lưu còn OK? | **Kết quả** |
|---|---|---|---|---|---|---|---|
| E1 | Email | **N** | — | — | — | — | MSG11 |
| E2 | Email | Y | **Y** | — | — | — | MSG12 |
| E3 | Email | Y | N | **Email MỚI** (MSG55) | **N** | — | MSG43 / MSG44 / MSG61 (DT-4) |
| E4 | Email | Y | N | Email mới | Y | **N** (người khác vừa lấy) | MSG12, không lưu |
| E5 | Email | Y | N | Email mới | Y | Y | Lưu → báo **email cũ** → đăng xuất mọi phiên → **MSG56** (+ AuditLog 🆕 CBR-113) |
| E6 | SĐT | **N** | — | — | — | — | MSG13 |
| E7 | SĐT | Y | **Y** | — | — | — | MSG14 |
| E8 | SĐT | Y | N | **Email HIỆN TẠI** | Y | Y | Lưu → **MSG57** (+ AuditLog) |

Áp dụng: khách (hiện hành) **và nhân viên** (🆕 CBR-108/110/111).

---

## DT-10 · Mở một URL — AuthFilter + RoleAreaFilter (GBR-10, GBR-31)

| # | URL công khai? (`/`, `/login`, `/register`, `/forgot-password`, `/css/`, `/js/`, `/images/`, `/blog`…) | Đã đăng nhập? | Request AJAX? | Thuộc khu vai trò (`/admin/*`…`/customer/*`)? | Vai trò khớp khu? | **Kết quả** | Ví dụ |
|---|---|---|---|---|---|---|---|
| U1 | **Y** | — | — | — | — | Cho qua | `/blog` khi chưa đăng nhập |
| U2 | N | **N** | **N** | — | — | 302 → `/login?returnUrl=<trang>` (chỉ GET mới nhớ trang) | `/account` |
| U3 | N | N | **Y** | — | — | JSON **401** + MSG06 | fetch khi hết phiên |
| U4 | N | Y | — | **N** | — | Cho qua | `/account` đã đăng nhập |
| U5 | N | Y | N | Y | **N** | Trang **403** | Khách mở `/manager/dashboard` |
| U6 | N | Y | Y | Y | N | JSON **403** + MSG07 | |
| U7 | N | Y | — | Y | **Y** | Cho qua (trang chưa làm → 404 là đúng lúc này) | Admin mở `/admin/dashboard` |

### DT-10b · `returnUrl` sau đăng nhập (chống Open Redirect)

| # | Có returnUrl? | Bắt đầu bằng `/`? | Bắt đầu bằng `//`? | Chứa `\`? | **Đi tới** |
|---|---|---|---|---|---|
| RU1 | N | — | — | — | Trang chủ vai trò |
| RU2 | Y | **N** (`https://evil.com`) | — | — | Trang chủ vai trò |
| RU3 | Y | Y | **Y** (`//evil.com`) | — | Trang chủ vai trò |
| RU4 | Y | Y | N | **Y** (`/\evil.com`) | Trang chủ vai trò |
| RU5 | Y | Y | N | N (`/customer/dashboard`) | **returnUrl** |

---

## DT-11 · Đăng xuất phiên — ai bị "đá" ra (CBR-93, CBR-29)

| Sự kiện | Phiên đang thao tác | Các phiên khác của cùng TK | Hàm |
|---|---|---|---|
| Đăng xuất (UC-02) | Hủy | Giữ | `session.invalidate()` |
| Đặt lại MK qua Quên MK | Hủy (nếu cùng TK) | **Hủy** | `invalidateSessionsOfAccount(id, null)` |
| Đổi MK trong `/account` | **Giữ** | **Hủy** | `invalidateSessionsOfAccount(id, session.getId())` |
| Đổi email (CBR-29 / 🆕 CBR-111) | Luật: hủy "every session" · Code: **giữ** → cần chốt | **Hủy** | `invalidateSessionsOfAccount(id, currentSessionId)` |
| Đổi SĐT | Giữ | Giữ | — |
| Đặt MK qua link | (chưa đăng nhập) | — | — |
| NV nghỉ / Admin khóa (CBR-86 — phase sau) | Hủy | **Hủy** | `invalidateSessionsOfAccount(id, null)` |

---

## DT-12 · Tổng hợp "ba chiếc khóa" gặp nhau thì sao?

| Tình huống | Đăng nhập | Quên mật khẩu |
|---|---|---|
| Status = **Locked** (Admin khóa) | MSG37 | MSG37 (chặn) |
| Status = **Inactive** (nghỉ / ngừng) | MSG37 | MSG37 (chặn) |
| **LockedUntil** > now (khách sai 5 lần) | MSG35 | **Cho phép**, xong tự mở khóa |
| **VerificationCodes.LockedUntil** > now (sai mã 5 lần / xin 5 lần) | Không ảnh hưởng | MSG61 (không xin/nhập mã được) |
| PasswordHash **NULL** | MSG38 | **Cho phép** (đặt được MK) |
