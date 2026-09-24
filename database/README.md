# database/

Script SQL Server dùng chung của nhóm (T-SQL). Chạy theo đúng thứ tự trên database rỗng:

| File | Nội dung |
|---|---|
| `1_Init_Schema.sql` | Tạo database `GMS` + 30 bảng, khóa, CHECK, index (Leader giữ bản chuẩn) |
| `2_Seed_Data.sql` | Dữ liệu mẫu phủ mọi trạng thái; mốc thời gian giả định "hôm nay" = 23/09/2026 |
| `3_Dev_Passwords.sql` | Đặt mật khẩu chung `Gms@2026` cho tài khoản mẫu (chỉ dùng máy dev) |
| `4_Phase1_Seed.sql` | Dữ liệu test Phase 1 (đăng nhập, đăng ký, mã xác thực, tài khoản, blog) — kịch bản ở `docs/phase-1/` |
| `migrations/001_pt_profile_setup_token_leave_reminder.sql` | **Chỉ chạy nếu DB tạo từ schema cũ (trước 24/09/2026)**: chuyên môn/kinh nghiệm PT, lý do nghỉ việc, link đặt mật khẩu, nhắc duyệt nghỉ phép. Cài mới thì không cần |
| `migrations/NNN_mo-ta.sql` | Mỗi lần sửa schema (sau khi cả nhóm thống nhất — agents.md Phần 5) là 1 file mới, đánh số tăng dần |

Tài khoản test (mật khẩu `Gms@2026`): Admin `dang.nguyen@gms.vn` · Manager `viet.tran@gms.vn` ·
Lễ tân `tam.le@gms.vn` · PT `son.dinh@gms.vn` · Khách `an.nguyen95@gmail.com`.

Quy tắc: không ai tự sửa `1_Init_Schema.sql` trên máy mình rồi commit. Muốn đổi schema → báo nhóm, copy schema cũ vào Notion,
thống nhất phương án, Leader viết file migration, mỗi người chạy file đó trên database local.
Các điểm schema đang chờ nhóm chốt: `docs/notes.md` mục D.
