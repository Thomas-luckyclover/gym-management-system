# 07: Bán tại quầy, mua online, voucher, VNPay, ca (UC-20, 22, 23, 24, 25, 26, UC-PAY)

## Bảng / cột
- `Orders`: snapshot tên / mô tả / **quyền lợi** / giá / VAT; `PaymentDueAt` chỉ đơn online
- `Transactions`: `ReceivedByEmployeeId`, `NeedsRefund`, `RefundedAt` / `By` / `Note`
- `Subscriptions`, `Shifts`, `Vouchers`

## Lập đơn (chung)
1. B1: chụp snapshot gói (DBR-50) + VAT P-10; tính `VatAmount` (DBR-57)
2. B2: voucher hợp lệ (CBR-73→76); `UsedCount + 1` (BR-43)
3. B3: online → `PaymentDueAt = now + P-09`; đơn cũ cùng gói đang Pending → hủy `ReplacedByNewOrder` (CBR-71)
4. B4: tại quầy → `ShiftId` = ca đang mở, người lập = Lễ tân (CBR-105); chặn bán cho chính mình (CBR-115)

## Thanh toán (UC-PAY, DBR-41, 42, CBR-69→72, BR-80)
- **Online:** mỗi lần thử 1 giao dịch, link hết hạn 5 phút. Failed / Expired thì đơn vẫn Pending (CBR-69).
- **Success:**
  - Đơn còn Pending và đúng số tiền → `Paid` → tạo `Subscriptions` (BR-80).
  - Đơn đã hủy hoặc sai số tiền → giao dịch vẫn Success, `NeedsRefund = 1`, đơn `Cancelled InvalidPayment` (CBR-72).
- **Tại quầy:** tiền mặt / chuyển khoản. Chuyển khoản thì Lễ tân xác nhận tiền đã về (CBR-77), lưu `ReceivedByEmployeeId`.

## Tạo quyền dùng khi Paid
- **Gói tập:** đang có gói còn hạn / bảo lưu → bắt đầu ngày sau ngày hết hạn, ngược lại từ ngày thanh toán (DBR-52). `EndDate = DATEADD(MONTH, n, Start)` (GBR-03). `MaxFreezesSnapshot = P-01`.
- **Gói ngày:** `Start = End = hôm nay` (CBR-34).
- **Khách New / Expired → Active** (CBR-01, CBR-02).

## Hủy (DBR-40, 43, CBR-11, BR-44)
- Job quá hạn → `PaymentTimeout` + giao dịch Pending → `Expired` (CBR-70).
- Lễ tân hủy đơn Paid trong cùng ca, trước khi chốt ca, có lý do → `CancelledByReceptionist`, hoàn tiền mặt, quyền dùng `Cancelled` (CBR-11).
- Có voucher → trả 1 lượt (BR-44).

## Ca (UC-25, CBR-78, 104, 105, BR-46, DBR-55)
1. B1: mở ca
2. B2: chốt ca chỉ khi không còn đơn tại quầy Pending của ca đó
3. B3: `ExpectedCash` = tổng giao dịch tiền mặt Success của đơn chưa hủy trong ca
4. B4: nhập tiền đếm; lệch thì bắt buộc lý do

## Màn Manager #41 (UC-26)
Lọc + xuất Excel; danh sách cần hoàn tiền (`NeedsRefund = 1 AND RefundedAt IS NULL`) có nút "Đã hoàn" ghi `RefundedAt` / `By` / `Note` + audit (BR-47).

## Case test
- Ca sáng hôm nay đang mở, còn đơn Pending của Toàn.
- Đơn Lan bị Lễ tân hủy.
- Ngọc: đơn online còn hạn + đơn quá hạn.
- Bích: đơn cần hoàn tiền.
- Bảo: đơn đã hoàn tiền.
- Tháng 9 đủ ca, có 2 ca lệch tiền.
