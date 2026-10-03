# 09: Lịch PT, điểm danh, xử lý buổi, swap, buổi lẻ (UC-30→39)

**Màn:**
- PT: #49 Dashboard, #54 / #55 Time slots, #56 Attendance, #57 Clients
- Khách: #50 My Bookings, #51 My PT Sessions, #52 Book Single Session, #53 Swap Session

## Bảng / cột
- `TimeSlots`: Status `Free` / `Held` / `Booked` / `Withdrawn`
- `Sessions`: `MissedReason`, `CancelType`, `RefundAgreed`
- `SessionMoveLogs`: `MoveType`, PT cũ / mới
- `Subscriptions`: `SwapsUsed`, `MaxSwapsSnapshot`

## Enum (NamingConvention 2b)
- `MissedReason`: `NoCheckIn`, `NoMembership`, `MembershipFrozen`, `MarkedAbsent`
- `CancelType`: `ByPt`, `ByCustomer`, `GymClosed`, `PackageExpired`
- `MoveType`: `CustomerSwap`, `PtOnBehalfSwap`, `PtReschedule`, `PtTransfer`

## UC-30 công bố slot (DBR-12, 20, 33, BR-50, 51, GBR-11)
Lưới 15 phút; `EndTime = Start + P-06`; không chồng giờ với slot khác của PT (kiểm ở Service); nằm trong giờ mở cửa của ngày. Gỡ slot trống → `Withdrawn` (không xóa).

## UC-31 điểm danh (BR-52→56, CBR-42, CBR-64, CBR-80)
- PT bấm Vắng trong cửa sổ từ giờ bắt đầu tới hết P-07 giờ sau khi kết thúc → `Missed MarkedAbsent`. Couple: chỉ khi cả 2 vắng.
- Job hết cửa sổ: có check-in trong ngày → `Completed` (`IsAutoClosed = 1`); không → `Missed NoCheckIn`. Ngày không có gói tập → `Missed NoMembership` (ưu tiên).

## UC-35 PT không dạy được (BR-62→71, CBR-46, 61)
Chỉ PT phụ trách, buổi còn `Scheduled`, chưa bắt đầu.
- **Dời (`PtReschedule`):** slot khác của chính PT, trong kỳ gói. Không trừ buổi, không tính swap.
- **Chuyển (`PtTransfer`):** slot của PT khác. `Sessions.PtEmployeeId` đổi; tính lại `CommissionAmount` theo tỷ lệ PT mới (BR-68).
- **Hủy (`ByPt`):** lý do + tick khách đồng ý nhận tiền mặt. Vẫn trừ buổi, vẫn tính hoa hồng (BR-66, 71, CBR-81).
- **Dời hộ khách không TK (`PtOnBehalfSwap`):** tính vào P-08 (BR-62).

Mọi lần dời / chuyển ghi `SessionMoveLogs` + thông báo khách (couple: cả 2) và PT mới (CBR-46). Ngày đích phải có gói tập còn hạn (CBR-61).

## UC-38 swap (BR-73→78, CBR-39, 44, 45)
- Chỉ T7 / CN, slot PT đã công bố, trong kỳ gói, buổi chưa bắt đầu.
- `SwapsUsed < MaxSwapsSnapshot`.
- Khách chọn trong tuần hiện tại (từ Thứ 7 mở tuần sau).
- Couple: ai swap cũng được.

## UC-37 buổi lẻ (CBR-15, 39, 40, 41, 53, 60, BR-72)
1. B1: canUseService
2. B2: ngày có gói tập hoặc gói ngày hôm nay
3. B3: đủ 10 tuổi
4. B4: lập đơn → slot `Held` với `HoldExpiresAt = PaymentDueAt`
5. B5: 1 khách chỉ 1 đơn buổi lẻ Pending (đơn cũ → `ReplacedByNewOrder`)
6. B6: Paid → `Booked` + Session
7. B7: tại quầy chỉ bán cho hôm nay (CBR-54)

## UC-39 khách hủy (BR-79, CBR-82)
`Cancelled ByCustomer`, vẫn trừ buổi, PT vẫn có hoa hồng.

## Bảo lưu và gói hết hạn
- Bảo lưu (CBR-62, 67, 68): buổi trong kỳ bảo lưu → `Missed MembershipFrozen` ngay khi lưu, slot về `Free`. Couple: chỉ khi người kia cũng không có gói.
- Gói PT hết hạn (CBR-55): buổi `Scheduled` còn lại → `Cancelled PackageExpired`.

## Case test
- Mai: đủ các loại dời / hủy / chuyển / swap.
- Couple Hưng – Hà.
- Hồng: Missed do bảo lưu.
- Quân: NoMembership + PackageExpired.
- Lực: PT dời hộ.
- An: slot Held ngày mai.
- Hạnh: buổi lẻ 20:00 hôm nay.
- Lịch trống tới 31/10.
