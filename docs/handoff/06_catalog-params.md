# 06: Tham số, giờ mở cửa, gói, voucher (UC-09, 10, 11, 12)

## Tham số (UC-09)
`SystemConfigs` P-01…P-21. Màn sửa **không** hiện P-16 (cấu hình triển khai). Đổi tham số chỉ áp cho bản ghi tạo sau (GBR-04): đơn lưu VAT, hạn thanh toán; gói lưu `MaxSwapsSnapshot`, `MaxFreezesSnapshot`.
Định dạng: P-04 `1D,1M,3M,9M,12M` · P-21 `1M,2M,3M` · P-02 số tháng · P-12 / P-20 `HH:mm` · P-19 `HH:mm-HH:mm`.

## Giờ mở cửa (UC-11, BR-12, CBR-56, CBR-57, DBR-12)
1. B1: ngày ngoại lệ ≥ hôm nay, mỗi ngày 1 dòng
2. B2: nghỉ cả ngày → mọi buổi PT `Scheduled` hôm đó `Cancelled` với `CancelType = GymClosed`, không trừ buổi, slot về `Free`, thông báo khách + PT (CBR-57)
3. B3: gói PT bán sau ngày khai báo thì không sinh buổi ngày đó (CBR-56)

## Gói (UC-10, DBR-11, 15, 19, 21, CBR-09, 12, 97)
- Gói tập: thời hạn chọn từ P-04 → lưu `DurationUnit` + `DurationValue`.
- Gói PT: thời hạn chọn từ P-21, `EstimatedSessionCount` chỉ để hiển thị.
- Tên duy nhất giữa gói đang bán. Ngừng bán = `IsActive = 0`, đơn cũ không đổi (CBR-97).
- Đổi giá không áp ngược (CBR-09), ghi audit.

## Voucher (UC-12, CBR-73→76, CBR-98, DBR-10, 14)
- Phạm vi `MembershipAndDayPass` / `MembershipOnly`.
- `MaxUsage` ≥ `UsedCount`.
- Đã dùng thì chỉ tắt, không xóa (CBR-98).
- Mã không sửa sau lần dùng đầu (DBR-10).

## Case test
- Gói "12 Tháng" trùng tên (1 bán, 1 ngừng).
- Gói 6 tháng đã ngừng nhưng khách Hồng vẫn đang dùng.
- Voucher đủ 6 trạng thái.
- Ngày 20/10 nghỉ, 11/10 giờ đặc biệt.
