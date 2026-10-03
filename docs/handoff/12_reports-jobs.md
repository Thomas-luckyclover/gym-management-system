# 12: Báo cáo + job chạy ngầm (UC-46, UC-47, GBR-14)

## Hoa hồng PT (DBR-58, CBR-80→82, BR-68)
Tính khi `Status IN ('Completed','Missed')` hoặc `Status = 'Cancelled' AND CancelType IN ('ByPt','ByCustomer')`. **Không** tính `GymClosed`, `PackageExpired`.
Hoa hồng thuộc `Sessions.PtEmployeeId` (PT dạy thật — chuyển PT thì theo PT mới). ✅ Đã sửa `ReportDAO`, `ManagerReportDAO`, `PtDashboardService`.

## Doanh thu (DBR-56, GBR-16)
Tổng giao dịch Success của đơn **chưa hủy**. Hoàn tiền ngoài hệ thống không trừ.

## Audit (CBR-103)
Chỉ Admin. Có cột `ActingRole`.

## Job (`job/*`, mỗi bản ghi 1 transaction, audit với người làm = System)

| Job | Việc | Rule |
|---|---|---|
| CancelOverdueOnlineOrders | Đơn online Pending quá `PaymentDueAt` → `PaymentTimeout`, giao dịch Pending → `Expired`, slot `Held` → `Free`, trả voucher | CBR-70, BR-44 |
| ExpireMembershipPackages | `EndDate < hôm nay` → `Expired`; khách → `Expired` nếu không còn gói phủ hôm nay. Gói PT hết hạn → buổi `Scheduled` còn lại `Cancelled PackageExpired` | CBR-03, CBR-55 |
| ExpireUnusedDayPasses | Gói ngày qua ngày chưa dùng → `Expired` | BR-32 |
| ResumeExpiredFreezes | Tới `PlannedEndDate` → `ActualEndDate`, cộng ngày, khách `Active` | BR-25, DBR-51 |
| AutoCloseUnmarkedSessions | Hết cửa sổ P-07 → `Completed` / `Missed NoCheckIn` / `Missed NoMembership` | CBR-42, CBR-64 |
| AutoRejectOverdueLeaveRequests | Nhắc 1 lần, quá P-20 thì từ chối + thông báo | CBR-85 |
| **Mới:** SendScheduledNotifications | `ScheduledAt <= now AND SentAt IS NULL` → gửi | UC-44 |

## Case test
Seed có buổi `Scheduled` trong tuần hiện tại / đã qua (khi chạy sau 05/10) → job tự đóng. Gói của Bích hết ngày 03/10 → job hết hạn.
