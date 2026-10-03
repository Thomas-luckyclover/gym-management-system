# 03: Tài khoản hệ thống, mở khóa, audit log (UC-07, UC-08)

**Màn:** #8 User List · #9 Add User Account · #10 User Details · #11 Temporarily Locked Customers · #12 Unlock · #13 Audit Log (package `servlet/admin`).

## Bảng / cột
`Persons`, `Employees`, `Accounts`, `Customers` (chỉ đọc để biết Person có vai trò khách), `AuditLogs`

## File
- **DAO:** `AccountDAO` (thêm `lock`, `unlock`, `setInactive`, `listUsers`), `EmployeeDAO` (`insert`, `updatePosition`), `PersonDAO.findActiveByPhone` (dùng lại), `AuditLogDAO.search`
- **Service:** `StaffAccountService` (mới), `AuditLogService`
- **Servlet / JSP:** 6 màn ở trên

## Luồng tạo tài khoản nhân viên (CBR-95, CBR-114, DBR-65, BR-08)
1. B1: kiểm dữ liệu (DBR-01, 04, 17, 18). SĐT bắt buộc (DBR-22).
2. B2: tạo Admin chỉ khi người thao tác là Admin (BR-08).
3. B3: tra SĐT trong Person còn hoạt động.
   - Không có → tạo Person mới.
   - Có, và Person đã có Employee `Working` → báo trùng.
   - Có, chưa có Employee → **gắn vào Person đó**: lấy họ tên / ngày sinh của Person, không nhập lại; Admin xác nhận đã kiểm giấy tờ (CBR-114).
4. B4: tạo `Employees` (mã NV-nnnn tự sinh — DBR-09; PT có hoa hồng mặc định P-11).
5. B5: Person đã có TK → **không** tạo TK mới, TK đó phục vụ thêm vai trò nhân viên. Chưa có → tạo TK với email của Person (bắt buộc email — DBR-23).
6. B6: đăng xuất mọi phiên của TK (vai trò mới có hiệu lực ở lần đăng nhập sau) + audit.

## Luồng khóa / mở khóa / đổi vị trí (CBR-96, CBR-109, BR-06, 07, 09, 10, CBR-87)
- **Khóa:** không khóa Admin đang hoạt động cuối cùng (BR-07). `Status = Locked`, đăng xuất mọi phiên, audit kèm lý do (GBR-09).
- **Mở khóa (Admin khóa / khóa tạm):** lý do 5–500 ký tự, reset `FailedLoginCount`, xóa `LockedUntil`, audit (BR-10).
- **Đổi vị trí:** chỉ Admin (CBR-109). Không còn việc dở ở vị trí cũ: PT không còn buổi `Scheduled`, slot tương lai, gói PT đang chạy; Lễ tân không có ca `Open` (CBR-87). Lên / xuống Admin theo BR-07, BR-08.

## Audit Log (UC-08)
Chỉ Admin xem (CBR-103). Lọc theo khoảng ngày (mặc định tháng này), `EntityName`, người làm, `ActingRole`. Câu hiển thị theo GBR-08.

## Case test
- Admin `dang.nguyen@gms.vn` là Admin cuối → không khóa / hạ quyền được.
- Mở khóa `kim.vo@gmail.com` (khóa tạm), `tu.phan@gms.vn` (Admin khóa).
- Tạo nhân viên với SĐT `0901234506` (khách Mai) → gắn vào Person 6, không tạo TK mới.
