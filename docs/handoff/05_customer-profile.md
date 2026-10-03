# 05: Hồ sơ khách + xóa hồ sơ (UC-16, UC-17, UC-IDENT)

**Màn:**
- #28 Customer Profile List, #29 Customer Profile Form (Lễ tân)
- #30 / #31 Customer List / Detail (Manager, chỉ xem)
- #32 Member Deletion Approval (Manager)

## Bảng / cột
`Persons`, `Customers` (CustomerType, giám hộ, CheckInPhoto, Status), `Accounts`, `CustomerDeletionRequests`, `Subscriptions` (để biết còn gói chạy không)

## Tạo hồ sơ (UC-16, CBR-19, 23, 24, 31, DBR-22→26, DBR-31, DBR-65)
1. B1: chọn loại `Self` / `Dependent` (không đổi được về sau — BR-18)
2. B2 (Self): tra SĐT trong Person còn hoạt động.
   - Đã có hồ sơ khách chưa Inactive → mở hồ sơ đó (CBR-19).
   - Có Person nhưng chưa có hồ sơ khách (VD nhân viên) → kiểm giấy tờ, tạo Customer trên Person đó, **không nhập lại họ tên / ngày sinh** (CBR-114).
   - Không có → tạo Person mới (CBR-23).
3. B3 (Dependent): Person không có SĐT; bắt buộc tên giám hộ + quan hệ + 1–2 số giám hộ (DBR-24, 25)
4. B4: sinh mã KH-nnnn (DBR-09)
5. B5: khách mua gói ≥ 1 tháng → tạo TK không mật khẩu bằng email của Person, không gửi mail (CBR-24, CBR-31). Person đã có TK (nhân viên) → không tạo thêm.
6. B6: chụp ảnh check-in khi mua gói đầu tiên trừ gói ngày (CBR-35)
7. B7: chặn nếu người thao tác chính là Person của hồ sơ (CBR-115 → `SelfServiceGuard`)

## Sửa hồ sơ (CBR-30, BR-19, CBR-04)
- Lễ tân sửa họ tên, ngày sinh, ảnh check-in, giám hộ.
- Đổi SĐT / email của hồ sơ có TK: khách phải có mặt, đối chiếu ảnh check-in; chưa có ảnh thì kiểm giấy tờ + chụp ngay (BR-19). Không cần mã.
- Hồ sơ `Inactive`: không ai sửa được, Lễ tân không thấy (CBR-04).

## Xóa hồ sơ (UC-16, UC-17, CBR-16, BR-15→17, CBR-86)
1. B1: lý do bắt buộc (BR-17)
2. B2: không còn gói chạy / buổi chưa dùng → xóa ngay (BR-15). Còn → tạo `CustomerDeletionRequests` Pending (CBR-16, BR-16)
3. B3: Manager duyệt / từ chối (từ chối cần lý do)
4. B4: khi xóa: `Status = Inactive`, `DeletedAt`; Person hết vai trò → `IsReleased = 1`; TK hết vai trò → `Inactive`; đăng xuất mọi phiên (CBR-86, DBR-28)
5. B5: audit

## Case test
- Khánh: yêu cầu xóa đang Pending.
- Linh, Hoa: đã xóa.
- Gia Bảo: 2 số giám hộ.
- Tạo hồ sơ khách bằng SĐT `0912000004` (Lễ tân Lâm) → gắn vào Person 20.
- Phương (Lễ tân + khách) thử sửa hồ sơ của mình → bị chặn.
