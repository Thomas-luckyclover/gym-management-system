# 08: Bán gói PT tại quầy (UC-21) · màn #38 `receptionist.PtPackageSaleServlet`

## Bảng / cột
- `Orders` (`PartnerCustomerId` cho couple)
- `Subscriptions`: `PtEmployeeId`, `FixedStartTime`, `GeneratedSessionCount`, `MaxSwapsSnapshot`
- `TimeSlots` (Held → Booked), `Sessions` (`SessionValue`, `CommissionAmount`), `OperatingHourExceptions`

## Luồng
1. B1: khách (couple: cả 2) có gói tập **còn hạn** lúc lập đơn; gói ngày / bảo lưu không tính (CBR-59, BR-37)
2. B2: đủ 10 tuổi (P-05; CBR-13, 14)
3. B3: PT khác Person của khách (CBR-115, CBR-59)
4. B4: chọn PT, gói (thời hạn P-21), ngày bắt đầu, khung giờ cố định thuộc slot PT đã công bố (CBR-52, BR-39)
5. B5: `EndDate = Start + n tháng` (BR-40, GBR-03). Sinh danh sách T2–T6 trong kỳ, bỏ ngày nghỉ đã khai báo (CBR-56, BR-35)
6. B6: ngày nào khung cố định đã bị chiếm → Lễ tân chọn slot khác cùng PT; không có thì ngày đó không có buổi (BR-41)
7. B7: cảnh báo số buổi / ngày nằm ngoài hạn gói tập của khách, vẫn cho bán (CBR-65)
8. B8: lập đơn Pending, giữ mọi slot (`Held`, `HoldExpiresAt = NULL` vì đơn tại quầy — DBR-36)
9. B9: không có voucher (CBR-76); thanh toán tiền mặt / chuyển khoản (CBR-49)
10. B10: Paid → `GeneratedSessionCount` = số buổi; `SessionValue = (giá − giảm) / số buổi` (DBR-59); `CommissionAmount = tỷ lệ PT × giá trị buổi` (DBR-58); tạo `Sessions` `Scheduled`, slot `Booked` (CBR-50); `MaxSwapsSnapshot = P-08`
11. B11: hủy đơn → slot về `Free`

## Case test
- Bán cho Gia Bảo (9 tuổi) → chặn.
- Bán cho Hà (gói tập hết) → chặn.
- Bán cho Mai thêm gói → cảnh báo CBR-65.
- Trang (khách) chọn PT Trang → chặn.
- Ngày 20/10 phải bị bỏ.
