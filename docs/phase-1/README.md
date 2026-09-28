# Phase 1 — Xác thực · Cài đặt tài khoản · Trang chủ · Blog

Gói tài liệu cho **màn SRS #1–7**: Sign In, Register, Forgot Password, Account Settings, Home, Blog List, Blog Details.

| File | Dùng để |
|---|---|
| `01_Phase1_Rules.md` | **Đọc đầu tiên.** Luật tóm tắt tiếng Việt: bảng kiểm tra mật khẩu/ký tự/độ dài từng ô, luật từng màn, thứ tự xử lý đăng nhập, các nhánh đăng ký, mã xác thực, tham số, mâu thuẫn cần chốt |
| `02_Phase1_Test_Scenarios.md` | Kịch bản test: nhập gì → kết quả đúng là gì, gắn với dữ liệu mẫu |
| `03_Phase1_Rule_Source.md` | Nguyên văn tiếng Anh các rule GBR/CBR/BR/DBR và tham số dùng trong Phase 1 (để trace) |
| `1_Init_Schema.sql`, `2_Seed_Data.sql` | Bản 24/09/2026 đã gồm các cột mới (chuyên môn PT, lý do nghỉ việc, link đặt mật khẩu, nhắc duyệt nghỉ phép) |
| `migrations/001_...sql` | Chỉ chạy nếu DB đã tạo từ schema cũ |
| `3_Dev_Passwords.sql` | Đặt mật khẩu thật `Gms@2026` cho tài khoản mẫu gốc (hash trong seed gốc là chuỗi giả) |
| `4_Phase1_Seed.sql` | Dữ liệu test bổ sung cho Phase 1 (ID ≥ 101, chạy lại được) |

## Thứ tự chạy database

```
1_Init_Schema.sql → 2_Seed_Data.sql → 3_Dev_Passwords.sql → 4_Phase1_Seed.sql
```
Dùng `1_Init_Schema.sql` + `2_Seed_Data.sql` **bản 24/09/2026** (có trong thư mục `database/` của dự án).
Nếu database đã tạo từ bản cũ: chạy `database/migrations/001_pt_profile_setup_token_leave_reminder.sql` trước file 4.
Mật khẩu mọi tài khoản test: **`Gms@2026`**. Tình huống khóa tạm / mã xác thực tính theo giờ chạy file 4 — hết hạn thì chạy lại file 4.

## Dữ liệu `4_Phase1_Seed.sql` phủ những gì

- **Đăng nhập:** tài khoản bị Admin khóa, đang khóa tạm, đã sai 4 lần (sai thêm 1 là khóa), khóa đã hết hạn, nhân viên sai nhiều lần không bị khóa, tài khoản Inactive, chưa đặt mật khẩu, hồ sơ không có tài khoản, hồ sơ đã xóa (email đã nhả).
- **Đăng ký:** SĐT mới, SĐT đã có tài khoản, hồ sơ có sẵn chờ kích hoạt (đúng/sai email), hồ sơ không email, SĐT của hồ sơ đã xóa, số chỉ là số giám hộ.
- **Mã xác thực:** còn hiệu lực, hết hạn, sai 5 lần bị khóa, đã xin 4 lần/giờ, vừa xin chưa đủ 60 giây, mã đổi email, mã đăng ký, mã kích hoạt.
- **Cài đặt tài khoản:** khách có ảnh / không ảnh, 2 hồ sơ phụ thuộc (dưới và trên 10 tuổi, giám hộ chính và giám hộ thứ 2, có gói / chưa gói).
- **Blog:** 14 bài đang hiện (3 trang), đủ 3 danh mục, bài không ảnh bìa, bài không tóm tắt, bài dài, bài có định dạng, bài đã gỡ.
- **Sửa dữ liệu gốc lệch luật:** khách 9, 10, 14 chưa có gói → trạng thái New (notes D3).

Không đổi schema. Hai cột Phase 1 cần (link đặt mật khẩu, chuyên môn PT) đã được thêm ở migration 001 ngày 24/09/2026.
