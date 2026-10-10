# Seed v25: danh mục case + tài khoản test

## Ngày phòng nghỉ — cập nhật 10/10/2026

- Gói PT sinh đủ mọi ngày T2–T6 trong kỳ, kể cả ngày phòng nghỉ đã khai báo. PT cần dời buổi qua `PtReschedule`, không tiêu lượt swap.
- Mai KH-1006: gói 07/09–07/11/2026 có **45 buổi**; ngày **20/10/2026 giữ Scheduled**, slot Booked để thử cảnh báo và dời buổi.
- Ngày nghỉ đã qua mốc lịch sử của seed mà buổi chưa dời: `Cancelled / GymClosed` lúc 00:05 ngày hôm sau, không có người hủy, không hoàn tiền và không tính hoa hồng. Ngày nghỉ tương lai không hủy sẵn.
- Buổi lẻ không được sinh vào ngày phòng nghỉ. Seed mô phỏng chính sách mới; JOB-04 và cảnh báo runtime còn cần sửa theo `docs/notes.md`.

## Cách chạy (SSMS hoặc sqlcmd)

1. Chạy `database/0_Reset_Database.sql` để xóa DB cũ (nhớ tắt Tomcat trước).
2. Chạy `database/1_Init_Schema.sql`.
3. Chạy `database/2_Seed_Data.sql`.

Tất cả mật khẩu là **`Gms@2026`**, trừ tài khoản ghi "—" (Lễ tân tạo, chưa đặt mật khẩu → đặt qua Quên mật khẩu).

**Mốc dữ liệu là Thứ Bảy 03/10/2026.**
- Lịch sử từ tháng 6 tới tháng 11/2026 có ngày cố định.
- Buổi PT trước 05/10 đã có kết quả; từ 05/10 trở đi là `Scheduled`.
- Dữ liệu **sống** tính theo giờ chạy script, nên chạy lại lúc nào cũng test được:
  - mã xác thực, khóa tạm 5 phút
  - đơn online còn hạn, slot đang giữ chỗ
  - ca sáng hôm nay đang mở
  - gói ngày và buổi PT lẻ hôm nay, đơn Pending tại quầy

Muốn đổi dữ liệu: sửa `database/tools/seeddata.py` rồi chạy `python database/tools/seeddata.py > database/2_Seed_Data.sql`.
Generator tự kiểm rule lúc sinh. `tools/runtest.py`, `tools/negtest.py` và `tools/bizcheck.py` kiểm FK/CHECK và nghiệp vụ chéo bảng.

## 1. Tài khoản đăng nhập (dàn nhân vật chính + Phase 1)

| AccId | Họ tên | Email | SĐT | Mật khẩu | TK | Vai trò | Dùng để test |
|---|---|---|---|---|---|---|---|
| 1 | Nguyễn Văn An | `an.nguyen95@gmail.com` | `0901234501` | Gms@2026 | Active | Customer KH-1001 [Active] | khách chính: gia hạn trước, bảo lưu cũ, giám hộ 2 bé |
| 2 | Trần Thị Bích | `bich.tran@gmail.com` | `0901234502` | Gms@2026 | Active | Customer KH-1002 [Active] | gói hết hạn đúng ngày mốc, đơn cần hoàn tiền |
| 3 | Lê Minh Quân | `quan.le92@gmail.com` | `0901234503` | Gms@2026 | Active | Customer KH-1003 [Expired] | đang sai 2 lần, chưa khóa |
| 4 | Phạm Thị Hồng | `hong.pham@yahoo.com` | `0901234504` | Gms@2026 | Active | Customer KH-1004 [Frozen] | khóa tạm ĐÃ HẾT → đăng nhập đúng sẽ reset (BR-02) |
| 5 | Đỗ Thị Mai | `mai.do97@gmail.com` | `0901234506` | Gms@2026 | Active | Customer KH-1006 [Active] | gói PT 2 tháng: swap, hủy, chuyển PT |
| 6 | Vũ Quốc Hưng | `hung.vu@gmail.com` | `0901234507` | Gms@2026 | Active | Customer KH-1007 [Active] | gói couple — người đứng tên |
| 7 | Bùi Thị Thu Hà | `ha.bui96@gmail.com` | `0901234508` | Gms@2026 | Active | Customer KH-1008 [Expired] | gói couple — người đi kèm, gói tập đã hết |
| 8 | Michael Johnson | `michael.johnson@outlook.com` | `0901234511` | Gms@2026 | Active | Customer KH-1011 [Active] | khách nước ngoài, gói 12 tháng giá cũ |
| 9 | Trịnh Văn Khánh | `khanh.trinh@gmail.com` | `0901234513` | — | Active | Customer KH-1013 [Active] | Lễ tân tạo (CBR-24) → MSG38, đặt MK qua Quên mật khẩu |
| 10 | Lý Thị Ngọc | `ngoc.ly99@gmail.com` | `0901234514` | Gms@2026 | Active | Customer KH-1014 [New] | đơn online quá hạn + đơn online đang chờ |
| 11 | Nguyễn Hải Đăng | `dang.nguyen@gms.vn` | `0912000001` | Gms@2026 | Active | Admin | Admin đang hoạt động duy nhất (BR-07) |
| 12 | Trần Quốc Việt | `viet.tran@gms.vn` | `0912000002` | Gms@2026 | Active | Manager | Manager |
| 13 | Lê Thị Thanh Tâm | `tam.le@gms.vn` | `0912000003` | Gms@2026 | Active | Receptionist | nhân viên sai 3 lần, KHÔNG bao giờ tự khóa (BR-01) |
| 14 | Phạm Văn Lâm | `lam.pham@gms.vn` | `0912000004` | Gms@2026 | Active | Receptionist | Lễ tân ca chiều |
| 15 | Đinh Công Sơn | `son.dinh@gms.vn` | `0912000005` | Gms@2026 | Active | PT | PT |
| 16 | Nguyễn Thị Kiều Trang | `trang.nguyen@gms.vn` | `0912000006` | Gms@2026 | Active | PT, Customer KH-1017 [Active] | PT + Khách → màn chọn vai trò |
| 17 | David Miller | `david.miller@gms.vn` | `0912000007` | Gms@2026 | Active | PT | PT nước ngoài |
| 18 | Hồ Văn Nam | `nam.ho@gms.vn` | `0912000008` | Gms@2026 | Inactive | PT (đã nghỉ) | nghỉ việc, hết vai trò → Inactive |
| 19 | Hoàng Thu Phương | `phuong.hoang@gms.vn` | `0912000009` | Gms@2026 | Active | Receptionist, Customer KH-1018 [Active] | Lễ tân + Khách → màn chọn vai trò |
| 20 | Đặng Quốc Bảo | `bao.dang@gmail.com` | `0912000010` | Gms@2026 | Active | PT (đã nghỉ), Customer KH-1019 [Active] | PT đã nghỉ, vẫn là khách → TK Active, chỉ vai trò Khách |
| 21 | Mai Thị Hoa | `hoa.mai@gms.vn` | `0912000011` | Gms@2026 | Active | Receptionist, Customer (đã xóa) KH-1020 [Inactive] | hồ sơ khách đã xóa, vẫn là Lễ tân |
| 22 | Lý Hoàng Long | `long.ly@gms.vn` | `0912000012` | Gms@2026 | Active | PT (đã nghỉ), PT | 1 TK xuyên 2 lần làm việc |
| 23 | Phan Thanh Tú | `tu.phan@gms.vn` | `0912000014` | Gms@2026 | Locked | PT | Admin khóa → MSG37 |
| 24 | Bùi Đức Anh | `anh.bui@gms.vn` | `0912000015` | Gms@2026 | Inactive | Admin (đã nghỉ) | Admin cũ đã nghỉ |
| 101 | Lê Hoàng Phúc | `phuc.le@gmail.com` | `0935000101` | Gms@2026 | Locked | Customer KH-1101 [New] | Admin khóa → MSG37 |
| 102 | Võ Thị Kim | `kim.vo@gmail.com` | `0935000102` | Gms@2026 | Active | Customer KH-1102 [New] | khóa tạm 5 phút tính từ lúc chạy script |
| 107 | Mai Thanh Tùng | `tung.mai@gmail.com` | `0935000107` | Gms@2026 | Inactive | Customer (đã xóa) KH-1107 [Inactive] | hồ sơ đã xóa → SĐT/email đã nhả |
| 108 | Lương Thu Thảo | `thao.luong@gmail.com` | `0935000108` | Gms@2026 | Active | Customer KH-1108 [New] | sai thêm 1 lần là bị khóa tạm |
| 109 | Ngô Đức Mạnh | `manh.ngo@gmail.com` | `0935000109` | Gms@2026 | Active | Customer KH-1109 [New] | đã xin 4 mã trong 1 giờ |
| 110 | Đinh Văn Khoa | `khoa.dinh@gmail.com` | `0935000110` | Gms@2026 | Active | Customer KH-1110 [New] | vừa xin mã < 60 giây |
| 111 | Tạ Thanh Bình | `binh.ta@gmail.com` | `0935000111` | Gms@2026 | Active | Customer KH-1111 [New] | sai mã 5 lần → email bị khóa xác thực |
| 112 | Cao Thị Quỳnh | `quynh.cao@gmail.com` | `0935000112` | Gms@2026 | Active | Customer KH-1112 [New] | mã khôi phục đã hết hạn |
| 113 | Hồ Thị Nhung | `nhung.ho@gmail.com` | `0935000113` | — | Active | Customer KH-1113 [New] | Lễ tân tạo, chưa đặt MK → MSG38 |
| 25 | Phan Văn Toàn | `toan.phan@gmail.com` | `0901234515` | — | Active | Customer KH-1015 [New] | Lễ tân tạo khi bán gói 1 tháng (CBR-24) |

Ngoài ra còn **40 khách thường** (PersonId 201–240, mã KH-2001…KH-2040). Khoảng 5/6 số đó có tài khoản, email dạng `<tên>.<họ><id>@gmail.com`; tra trong bảng `Persons`.

## 2. Case theo module

### Đăng nhập / vai trò (UC-01, CBR-113, BR-01)
| Case | Đăng nhập bằng | Kỳ vọng |
|---|---|---|
| 1 người 2 vai trò (PT + Khách) | `trang.nguyen@gms.vn` hoặc `0912000006` | Màn chọn vai trò; đổi vai trò không cần đăng nhập lại |
| Lễ tân + Khách | `phuong.hoang@gms.vn` | Màn chọn vai trò; ở vai trò Lễ tân không bán / sửa được hồ sơ khách của chính mình (CBR-115) |
| PT đã nghỉ, vẫn là khách | `bao.dang@gmail.com` | Vào thẳng khu Khách (CBR-86) |
| Lễ tân có hồ sơ khách đã xóa | `hoa.mai@gms.vn` | Vào thẳng khu Lễ tân |
| PT tuyển lại (2 dòng Employees) | `long.ly@gms.vn` | Vào khu PT bằng hồ sơ NV-0013 |
| Admin khóa | `tu.phan@gms.vn`, `phuc.le@gmail.com` | MSG37 |
| Hết vai trò → Inactive | `nam.ho@gms.vn`, `anh.bui@gms.vn` | MSG37 |
| Khóa tạm 5 phút (sống) | `kim.vo@gmail.com` | MSG35 + đếm ngược |
| Khóa tạm đã hết | `hong.pham@yahoo.com` | Đăng nhập đúng → reset bộ đếm (BR-02) |
| Sai thêm 1 lần là khóa | `thao.luong@gmail.com` (đang sai 4 lần) | Lần sai thứ 5 → khóa 5 phút |
| Nhân viên không bao giờ tự khóa | `tam.le@gms.vn` (đang sai 3 lần) | Sai mãi vẫn chỉ MSG34 |
| Chưa có mật khẩu | `khanh.trinh@gmail.com`, `nhung.ho@gmail.com`, `toan.phan@gmail.com` | MSG38 → Quên mật khẩu |

### Đăng ký / kích hoạt (UC-05, CBR-19→28)
| Case | Nhập | Kỳ vọng |
|---|---|---|
| SĐT của nhân viên | `0912000005` | MSG51 "đã có tài khoản", không lộ là của ai (CBR-26) |
| SĐT khách đã có TK | `0901234501` | MSG51 |
| Hồ sơ quầy có email, chưa có TK | `0935000105` + `yen.doan@gmail.com`, mã `445566` | Kích hoạt (CBR-20) |
| Như trên, email khác | `0935000105` + email khác | MSG52 email che (CBR-25) |
| Hồ sơ chưa có email | `0901234532` (Lực) | MSG53 ra quầy (CBR-27) |
| SĐT đã nhả | `0901234512`, `0935000107` | Đăng ký mới bình thường (CBR-23) |
| Số giám hộ | `0935000999` | Đăng ký mới (CBR-28) |
| Mã đăng ký sống | `khach.moi@gmail.com` mã `112233` | |

### Quên mật khẩu / mã xác thực (UC-03, CBR-94)
| Email | Tình huống |
|---|---|
| `thao.luong@gmail.com` | Mã `246810` còn hiệu lực |
| `manh.ngo@gmail.com` | Đã xin 4 mã/giờ → lần 5 khóa |
| `khoa.dinh@gmail.com` | Vừa gửi → chờ 60 giây |
| `binh.ta@gmail.com` | Sai 5 lần → khóa 5 phút |
| `quynh.cao@gmail.com` | Mã hết hạn → MSG44 |

### Account Settings (UC-04, CBR-108→112)
- An (`an.nguyen95@gmail.com`) đang đổi email sang `an.nguyen.moi@gmail.com`, mã `135790`.
- PT Sơn đổi SĐT: mã `864200` gửi về email hiện tại (CBR-110).
- Trang ở vai trò Khách thì thấy tab hồ sơ phụ thuộc; ở vai trò PT thì không (CBR-111).
- An là giám hộ chính của Gia Huy (8 tuổi, có gói tập) và giám hộ thứ 2 của Minh Châu → tab hồ sơ phụ thuộc có 2 dòng.

### Khách / gói tập / check-in (UC-16→20)
| Khách | Trạng thái | Case |
|---|---|---|
| An KH-1001 | Active | Hủy bảo lưu sớm, hạn +9 ngày (DBR-51); đã gia hạn trước gói 9 tháng bắt đầu 16/10 (DBR-52); có ngày vào 2 lượt (BR-34) |
| Bích KH-1002 | Active | Gói hết hạn **đúng 03/10** → job hết hạn; đơn online thanh toán lần 1 Failed, lần 2 Success |
| Quân KH-1003 | Expired | Gói tập hết trước gói PT |
| Hồng KH-1004 | Frozen | Đang bảo lưu 28/09–27/10, đã hết lượt (BR-24); mua gói 6 tháng nay đã ngừng bán (CBR-97) |
| Dũng KH-1005 | Active | Vãng lai, gói ngày đã check-in hôm nay → không vào lại được (BR-29) |
| Hạnh KH-1016 | Active | Gói ngày chưa check-in + buổi PT lẻ 20:00 hôm nay (CBR-54) |
| Mai KH-1006 | Active | Gói tập hết 04/10 trước gói PT → buổi sau đó gắn nhãn "Không có gói tập" (CBR-65/66) |
| Hà KH-1008 | Expired | Còn buổi couple dù gói tập hết (CBR-63/67) |
| Gia Bảo KH-1009 | Active | Dependent 9 tuổi, 2 số giám hộ; không mua được gói PT (CBR-13) |
| Gia Huy KH-1103 | Active | Dependent 8 tuổi |
| Khánh KH-1013 | Active | Yêu cầu xóa Pending vì còn gói (CBR-16) |
| Linh KH-1012 / Hoa KH-1020 | Inactive | Đã xóa (Approved) |
| Ngọc KH-1014 | New | Đơn online quá hạn (voucher được trả lượt — BR-44) + đơn online đang chờ |
| Toàn KH-1015 | New | Đơn tại quầy Pending → ca sáng hôm nay không chốt được (CBR-78) |
| Lan KH-1010 | New | Gói ngày hôm nay bị Lễ tân hủy sau khi đã thu tiền (CBR-11) |
| Michael KH-1011 | Active | Gói "12 Tháng" giá cũ (gói đã ngừng, trùng tên gói mới — DBR-11), voucher hè |

### PT / lịch / buổi tập (UC-21, 30→39)
| Gói | PT | Case |
|---|---|---|
| Mai: PT 2 tháng 18:00 | Sơn | Swap sang T7 12/09 (1/6 lượt — gói 2 tháng × 3, P-08); vắng (PT bấm Absent); khách tự hủy 22/09; chuyển sang PT Long 29/09 (hoa hồng theo PT mới); 18/09 bị hủy vì phòng nghỉ khai báo SAU khi bán |
| Hưng + Hà: couple 2 tháng 19:30 | Trang | PT hủy có hoàn tiền mặt (BR-66); PT dời lịch (BR-64); 02/09 nghỉ lễ vẫn sinh buổi; chưa dời và đã qua ngày → GymClosed |
| Hồng: PT 2 tháng 17:00 | David | Bảo lưu 28/09–27/10: buổi đã qua ngày → JOB-04 chốt Missed `MembershipFrozen`; buổi chưa tới ngày giữ `Scheduled`, slot `Booked` (quyết định 09/10/2026) |
| Quân: PT 1 tháng 07:00 (T7–T8) | Sơn | Missed `NoMembership` sau khi gói tập hết; buổi ngày cuối Cancelled `PackageExpired` (CBR-55) |
| Trang (khách): PT 1 tháng 12:00 | David | PT là khách của PT khác (CBR-59, CBR-115) |
| Lực: PT 1 tháng 06:30 | Sơn | Khách không tài khoản, PT dời hộ (`PtOnBehalfSwap`, BR-62) |
| An: đơn buổi lẻ sống | David | Slot ngày mai 14:00 `Held` (hạn = hạn đơn); đơn cũ bị thay (ReplacedByNewOrder) → slot 15:30 về Free |

Lịch trống công bố: 5 PT có slot từ 05/10 tới 31/10, mỗi ngày 2–6 khung, có cả T7/CN.
- 11/10 là ngày giờ đặc biệt 07:00–18:00 → chỉ có slot trong khung đó.
- 20/10 nghỉ → không có slot.
- 1 slot `Withdrawn` (PT gỡ).

### Thanh toán / ca / voucher (UC-20, 22→26, PAY)
- Ca: tháng 9 đủ 2 ca mỗi ngày mở cửa, `ExpectedCash` đúng DBR-55. Có 2 ca lệch tiền kèm lý do. 02/09 và 18/09 nghỉ nên không có ca.
- Đơn hủy đủ 4 loại:
  - `PaymentTimeout` (Ngọc)
  - `ReplacedByNewOrder` (An, sống)
  - `InvalidPayment` chưa hoàn (Bích) và đã hoàn (Bảo — BR-47)
  - `CancelledByReceptionist` (Lan, sống)
- Voucher:
  - đang chạy `WELCOME10`
  - hết hạn `SUMMER200K`
  - hết lượt `FULLBOOKED`
  - đã tắt nhưng đã dùng `OLDCAMPAIGN`
  - chưa tới ngày `TET2027`
  - không giới hạn / khách `DAYPASS20K`

### Nhân sự / thiết bị / nội dung
- Đơn nghỉ đủ trạng thái:
  - Approved
  - Rejected bởi Manager vì định biên
  - Rejected tự động quá hạn (có nhắc)
  - Pending tương lai
- Bảo trì đủ 4 trạng thái. Có case duyệt nhưng bỏ tick "Under maintenance" (CBR-101), và case đóng có ghi chú nghiệm thu.
- Ghi chú riêng / giáo án:
  - bản đã xóa rồi viết lại (BR-59)
  - nháp mới hơn bản đã xuất bản (BR-60)
  - chưa xuất bản
  - đã xóa
- Thông báo: đủ 6 đối tượng, có 1 thông báo hẹn giờ chưa gửi, có thông báo hệ thống gửi cá nhân.
- 16 bài blog (14 Active), 14 feedback (đọc / chưa đọc), 18 dòng audit log có `ActingRole`.
