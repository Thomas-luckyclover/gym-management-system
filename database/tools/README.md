# database/tools — sinh & kiểm thử seed

| Lệnh (chạy trong thư mục này) | Làm gì |
|---|---|
| `pip install python-dateutil sqlglot` | Cài 1 lần |
| `python seeddata.py > ../2_Seed_Data.sql` | Sinh lại seed sau khi sửa `seeddata.py` |
| `python runtest.py` | Nạp schema + seed vào SQLite với 9 thời điểm chạy khác nhau, kiểm FK / CHECK / UNIQUE |
| `python negtest.py` | Thử chèn 18 loại dữ liệu sai, CHECK phải chặn hết |
| `python bizcheck.py` | 21 kiểm tra nghiệp vụ chéo bảng (CBR-36, 58, 86, 115; DBR-22, 33, 55…) |

`seedgen.py` = khung (đơn, giao dịch, ca, lịch PT, check-in, tự assert rule) · `seeddata.py` = dữ liệu.
SQLite chỉ dùng để kiểm; DB thật vẫn là SQL Server.
