# database/: v25 (03/10/2026)

Chạy đúng thứ tự trên SQL Server (SSMS hoặc sqlcmd):

| File | Nội dung |
|---|---|
| `0_Reset_Database.sql` | Xóa sạch DB `GMS` (tắt Tomcat trước) |
| `1_Init_Schema.sql` | 31 bảng (thêm BodyMeasurements 25/10), khóa, CHECK, filtered index, khớp rule v25 |
| `2_Seed_Data.sql` | Seed đủ case. Lịch sử quanh mốc 03/10/2026; dữ liệu "sống" tính theo giờ chạy. Mật khẩu `Gms@2026` |
| `tools/` | Generator seed + bộ kiểm thử (xem `tools/README.md`) |

- Tài khoản test + danh mục case: `docs/database/SEED_CASES.md`
- Thay đổi so với schema cũ + lý do theo rule: `docs/database/SCHEMA_CHANGES_v25.md`
- `3_Dev_Passwords.sql`, `4_Phase1_Seed.sql`, `migrations/` đã gộp vào 2 file trên → **đã xóa**.

Đổi schema về sau: theo `agents.md` Phần 5 (báo nhóm → chốt → sửa `1_Init_Schema.sql` + `tools/seeddata.py` → chạy `tools/runtest.py`, `negtest.py`, `bizcheck.py` → commit chung).
