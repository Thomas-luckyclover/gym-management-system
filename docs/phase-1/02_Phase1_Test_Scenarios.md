# Phase 1 — Kịch bản test theo dữ liệu mẫu

Chạy lần lượt: `1_Init_Schema.sql` → `2_Seed_Data.sql` → `3_Dev_Passwords.sql` → `4_Phase1_Seed.sql`.
**Mật khẩu mọi tài khoản: `Gms@2026`.**
Các tình huống có dấu ⏱ tính theo giờ chạy `4_Phase1_Seed.sql` (khóa tạm, mã xác thực) — hết giờ thì chạy lại file 4.

**Mẹo khi chưa cấu hình Gmail:** mỗi lần bấm "Gửi mã", app sinh mã mới (mã cũ bị hủy). Xem mã vừa sinh bằng:
```sql
SELECT TOP 1 Code, ExpiresAt FROM VerificationCodes WHERE Email = 'email-đang-test' ORDER BY SentAt DESC;
```
Mã có sẵn trong seed dùng để test thẳng bước nhập mã mà không cần gửi mail.

## 1. Tài khoản đăng nhập nhanh theo vai trò

| Vai trò | Email | SĐT | Vào trang |
|---|---|---|---|
| Admin | dang.nguyen@gms.vn | 0912000001 | `/admin/dashboard` |
| Manager | viet.tran@gms.vn | 0912000002 | `/manager/dashboard` |
| Lễ tân | tam.le@gms.vn | 0912000003 | `/receptionist/check-in` |
| PT | son.dinh@gms.vn | 0912000005 | `/pt/dashboard` |
| Khách (có 2 con phụ thuộc) | an.nguyen95@gmail.com | 0901234501 | `/customer/dashboard` |

## 2. Đăng nhập — `/login`

| # | Nhập | Kết quả đúng | Luật |
|---|---|---|---|
| L01 | `an.nguyen95@gmail.com` / `Gms@2026` | Vào khu Hội viên | UC-01 |
| L02 | `0901234501` / `Gms@2026` | Vào được bằng SĐT | UC-01 |
| L03 | để trống ô email/SĐT | "Nhập email hoặc số điện thoại." | MSG39 |
| L04 | `quan.le92@gmail.com` / sai mật khẩu | "…Bạn còn **2** lần thử." (đã sai sẵn 2 lần) | MSG33, BR-01 |
| L05 | `thao.luong@gmail.com` / sai mật khẩu 1 lần | Bị khóa 5 phút ngay (đã sai sẵn 4 lần) | MSG35, BR-01 |
| L06 ⏱ | `kim.vo@gmail.com` / `Gms@2026` (đúng mật khẩu) | Vẫn bị chặn, hiện thời gian còn lại mm:ss | MSG35 |
| L07 | `hong.pham@yahoo.com` / `Gms@2026` | Đăng nhập được (khóa đã hết hạn), bộ đếm về 0 | BR-02 |
| L08 | `tam.le@gms.vn` (Lễ tân) / sai mật khẩu nhiều lần | Luôn "Vui lòng kiểm tra lại", **không bao giờ khóa** | MSG34, BR-01 |
| L09 | `phuc.le@gmail.com` / `Gms@2026` | "Tài khoản này đã ngừng hoạt động…" (Admin khóa) | MSG37, CBR-96 |
| L10 | `nam.ho@gms.vn` / `Gms@2026` | Như trên (nhân viên đã nghỉ) | MSG37 |
| L11 | `tung.mai@gmail.com` / `Gms@2026` | Không đăng nhập được (hồ sơ đã xóa, email đã nhả) | DBR-28 |
| L12 | `khanh.trinh@gmail.com` / bất kỳ | "Tài khoản này chưa có mật khẩu…" | MSG38, CBR-31 |
| L13 | `lan.dang85@gmail.com` / bất kỳ | Như sai mật khẩu (hồ sơ có, **không có tài khoản**) | CBR-17 |
| L14 | `khongtontai@gmail.com` / bất kỳ | Như sai mật khẩu — **không** nói "email không tồn tại" | MSG33 |
| L15 | `trang.nguyen@gms.vn` (vừa PT vừa khách) | Chờ chốt: hỏi chọn vai trò (notes D1.2) | UC-01 |
| L16 | Mở `/customer/dashboard` khi chưa đăng nhập → đăng nhập | Quay lại đúng `/customer/dashboard` | GBR-31 |
| L17 | Khách mở `/manager/dashboard` | Trang 403 | GBR-10 |
| L18 ⏱ | Mở `/set-password?token=setup-nhung-phase1-2026`, đặt mật khẩu mới | Đặt được → đăng nhập `nhung.ho@gmail.com` bằng mật khẩu vừa đặt | CBR-24 |
| L19 ⏱ | Mở lại đúng link L18 lần 2 | Link không còn hiệu lực (đã dùng) | CBR-31 |
| L20 | Mở `/set-password?token=setup-khanh-expired-2026` | Link hết hạn → hướng dẫn dùng Quên mật khẩu | CBR-31 |
| L21 | `nhung.ho@gmail.com` đăng nhập **trước** khi đặt mật khẩu | "Tài khoản này chưa có mật khẩu…" | MSG38 |

## 3. Đăng ký — `/register`

| # | SĐT / email nhập | Kết quả đúng | Luật |
|---|---|---|---|
| R01 | `0987000001` + email mới `khach.moi@gmail.com` | Gửi mã → nhập `112233` ⏱ → tạo tài khoản, hồ sơ New | UC-05, MSG54 |
| R02 | `0901234501` (An đã có tài khoản) | Dừng: "Số điện thoại này đã có tài khoản…" | CBR-26, MSG51 |
| R03 | `0935000105` + `yen.doan@gmail.com` (đúng email hồ sơ) | Nhánh kích hoạt: mã `445566` ⏱ → gắn tài khoản vào hồ sơ KH-1105, **không tạo hồ sơ mới** | CBR-20 |
| R04 | `0935000105` + email khác | "…đã đăng ký với email khác (y***@gmail.com)…" | CBR-25, MSG52 |
| R05 | `0901234505` (Hoàng Văn Dũng — hồ sơ không có email) | "Hồ sơ của bạn chưa có email…" mời ra quầy | CBR-27, MSG53 |
| R06 | `0901234510` + `lan.dang85@gmail.com` | Nhánh kích hoạt (hồ sơ có email, chưa có tài khoản) | CBR-20 |
| R07 | `0901234512` (số của hồ sơ đã xóa) | Tạo mới bình thường | CBR-23 |
| R08 | `0935000999` (chỉ là số giám hộ của bé Bảo Ngọc) | Tạo mới bình thường, không bị chặn | CBR-28 |
| R09 | SĐT mới + email `bich.tran@gmail.com` | "Email này đã được dùng cho tài khoản khác." | DBR-23, MSG12 |
| R10 | SĐT mới + email `linh.truong93@gmail.com` (email đã nhả) | Được dùng | DBR-28 |
| R11 | `090123450` (9 số) · `1901234501` · `09012345ab` | "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0." | DBR-17, MSG13 |
| R12 | Mật khẩu `abcdefgh` · `12345678` · `abc123` | Lỗi luật mật khẩu (thiếu số / thiếu chữ / quá ngắn) | DBR-02 |
| R13 | Họ tên `Nguyễn Văn 2` · `A` · `@@@` | Lỗi họ tên | DBR-01 |
| R14 | Ngày sinh ngày mai · năm 1900 | MSG15 · lỗi tuổi quá 100 | DBR-04 |
| R15 | Ngày sinh 2018 (8 tuổi) | Vẫn đăng ký được (chỉ bị chặn PT về sau) | P-05 |

## 4. Quên mật khẩu & mã xác thực — `/forgot-password`

| # | Email | Kết quả đúng | Luật |
|---|---|---|---|
| F01 ⏱ | `thao.luong@gmail.com`, nhập `246810` | Sang bước đặt mật khẩu mới | UC-03 |
| F02 ⏱ | `thao.luong@gmail.com`, nhập `000000` | "Mã không đúng…" | MSG43 |
| F03 | `quynh.cao@gmail.com`, nhập `999000` | "Mã đã hết hạn…" | MSG44 |
| F04 ⏱ | `binh.ta@gmail.com` (nhập mã hoặc xin mã mới) | Bị khóa xác thực 5 phút | CBR-94, MSG61 |
| F05 ⏱ | `manh.ngo@gmail.com`, bấm Gửi lại mã | Lần xin thứ 5 trong 1 giờ → khóa 5 phút | CBR-94, MSG61 |
| F06 ⏱ | `khoa.dinh@gmail.com`, bấm Gửi lại mã ngay | Nút khóa, đếm ngược tới đủ 60 giây | CBR-94 |
| F07 | `khongtontai@gmail.com` | "Không tìm thấy tài khoản nào với email này." | MSG41 |
| F08 | Nhập SĐT thay vì email | Không chấp nhận (chỉ khôi phục qua email) | BR-03 |
| F09 | Đặt mật khẩu mới thành công khi đang đăng nhập ở tab khác | Tab kia bị đăng xuất | CBR-93, MSG49 |
| F10 | `khanh.trinh@gmail.com` (chưa có mật khẩu) | Khôi phục bình thường → đặt được mật khẩu | CBR-31 |

## 5. Cài đặt tài khoản — `/account`

| # | Đăng nhập | Thao tác | Kết quả đúng | Luật |
|---|---|---|---|---|
| A01 | An | Mở tab Hồ sơ | Họ tên, ngày sinh **chỉ đọc**, có gợi ý "do quầy lễ tân cập nhật" | CBR-30, MSG60 |
| A02 | An | Không có ảnh đại diện | Hiện vòng tròn chữ cái đầu tên | SRS 1.4.1 |
| A03 | Kim (sau khi hết khóa) | Có ảnh đại diện | Hiện ảnh `/uploads/avatar/p-102.jpg` (file cần tự đặt vào thư mục upload) | CBR-37 |
| A04 ⏱ | An | Đổi email → nhập `135790` | Đổi thành `an.nguyen.moi@gmail.com`, báo email cũ, đăng xuất mọi phiên | CBR-29, MSG56 |
| A05 | An | Đổi SĐT thành `0901234502` | "Số điện thoại này đã thuộc về một hồ sơ khác." | DBR-22, MSG14 |
| A06 | An | Đổi mật khẩu, mật khẩu mới = `Gms@2026` | "Mật khẩu mới phải khác mật khẩu hiện tại." | MSG48 |
| A07 | An | Đổi mật khẩu đúng | Giữ phiên hiện tại, đăng xuất phiên khác | CBR-93, MSG49 |
| A08 | An | Tab Hồ sơ phụ thuộc | 2 bé: **Nguyễn Gia Huy** (8 tuổi, nhãn dưới 10 tuổi, Gói 3 Tháng tới 01/12/2026) và **Nguyễn Minh Châu** (14 tuổi, chưa có gói — An là giám hộ thứ 2) | SRS 1.4.3 |
| A09 | Bích (không có con) | Tab Hồ sơ phụ thuộc | Trạng thái rỗng | GBR-21 |
| A10 | Lễ tân Tâm | Mở `/account` | Không có tab Hồ sơ phụ thuộc | SRS 1.4 |

## 6. Trang chủ — `/`

| # | Kiểm tra | Kết quả đúng theo seed |
|---|---|---|
| H01 | Số PT | **3** (Sơn, Kiều Trang, David — Hồ Văn Nam đã nghỉ không tính) |
| H02 | Giờ mở cửa | 05:30 – 22:00 (P-19) |
| H03 | Bảng giá gói tập | Gói Ngày, 1, 3, 6 tháng — **không có Gói 12 Tháng** (đã ngừng bán) |
| H04 | Tiết kiệm so với gói 1 tháng | 3 tháng: 2.190.000 so với 3 × 799.000 → tiết kiệm ≈ 9% · 6 tháng ≈ 17% |
| H05 | Bảng giá PT | PT 1 Tháng (1 người, tại quầy) · PT Couple (2 người, tại quầy) · Buổi PT Lẻ (đặt online) |
| H06 | Bài viết mới | 3 bài mới nhất: "5 bài khởi động…" (12/09 08:00), "Protein bao nhiêu là đủ?" (08/09 10:00), "Ăn gì trước và sau…" (08/09 08:00) |
| H07 | Danh sách PT | 3 PT: Đinh Công Sơn — Strength & Conditioning (8 năm) · Nguyễn Thị Kiều Trang — Giảm mỡ · Thể hình nữ (6 năm) · David Miller — Tăng cơ · Powerlifting (9 năm). Không hiện SĐT/email; PT Hồ Văn Nam đã nghỉ không hiện |

## 7. Blog — `/blog`

| # | Thao tác | Kết quả đúng theo seed |
|---|---|---|
| B01 | Mở `/blog` | 14 bài, 3 trang (6 bài/trang), mới nhất trước |
| B02 | Lọc "Tin tức" | 4 bài: khai trương khu giãn cơ, lịch nghỉ Quốc khánh, ứng dụng đặt lịch, mở rộng giờ cuối tuần |
| B03 | Tìm `squat` / `SQUAT` / `sqat` | 1 bài / 1 bài / "Không tìm thấy kết quả…" (MSG01) |
| B04 | Tìm `ngu du giac` (không dấu) | Ra "Ngủ đủ giấc quan trọng thế nào…" (GBR-19) |
| B05 | Lọc "Dinh dưỡng" rồi tìm `protein` | 1 bài; đổi từ khóa → về trang 1 |
| B06 | Bài "Tập lưng đúng cách…" | Thẻ bài lấy **đầu nội dung** làm tóm tắt (bài không có tóm tắt) |
| B07 | Bài "Super Gym khai trương…", "Cardio hay tạ nặng…" | **Ảnh mặc định** theo danh mục (không có ảnh bìa) |
| B08 | Mở chi tiết "5 bài khởi động…" | Thời gian đọc > 1 phút; có 3 bài gợi ý cùng danh mục Tập luyện |
| B09 | Mở chi tiết "Ăn gì trước và sau…" | Hiển thị đúng tiêu đề phụ, danh sách, chữ đậm, link |
| B10 | Mở `/blog/detail?id=4` (bài đã gỡ) và `id=9999` | Không lộ nội dung, chỉ thông báo + link về danh sách |
| B11 | Mở `/blog` khi chưa đăng nhập | Xem được bình thường (trang công khai) |
