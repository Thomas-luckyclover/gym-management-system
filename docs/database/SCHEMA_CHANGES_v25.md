# Schema v25: thay đổi so với bản cũ, kèm lý do theo rule

Áp dụng bằng cách cài lại từ đầu: `0_Reset_Database.sql` → `1_Init_Schema.sql` → `2_Seed_Data.sql`.
**Không có migration cộng dồn.** Các file `migrations/001`, `002` đã được gộp vào schema.

## 1. Đổi cấu trúc (code phải sửa theo)

| # | Bảng | Thay đổi | Rule | Lỗi nếu giữ bản cũ | Code bị ảnh hưởng |
|---|---|---|---|---|---|
| 1 | Packages | `DurationDays` → `DurationUnit` (`Day`/`Month`) + `DurationValue` | GBR-03, DBR-19 | Gói 1 tháng mua ngày 31/01 ra 02/03 thay vì 28/02 | `Package`, `PackageDAO`, `HomeService` ✅ đã sửa |
| 2 | Packages | Tên chỉ duy nhất giữa gói **đang bán** (filtered unique) | DBR-11 | Không tạo lại được gói trùng tên gói đã ngừng | — |
| 3 | VerificationCodes | Bỏ `TokenHash` và Purpose `PasswordSetup`; thêm `PhoneChange` | CBR-31, CBR-110 | Mã đổi SĐT và mã đổi email lẫn nhau | `VerificationCode`, `VerificationCodeDAO`, `ContactChangeService` ✅ |
| 4 | Sessions | Bỏ `ResolutionType`; thêm `CancelType` (`ByPt`/`ByCustomer`/`GymClosed`/`PackageExpired`) | CBR-55, 57, 80→82 | Không phân biệt được buổi hủy do đóng cửa hay gói hết hạn → hoa hồng sai | `SessionDAO`, `ReportDAO`, `ManagerReportDAO`, `PtDashboardService` ✅ |
| 5 | Sessions | `MissedReason` thành mã tiếng Anh (`NoCheckIn`/`NoMembership`/`MembershipFrozen`/`MarkedAbsent`) | GBR-01 | Câu tiếng Việt tự do, không lọc / thống kê được | `PtSessionView.getMissedReasonText()` ✅ |
| 6 | SessionSwapLogs → **SessionMoveLogs** | Ghi MỌI lần dời buổi: `MoveType`, PT cũ / PT mới, lý do | BR-62, 64, 65, 68, 73 | Mất lịch sử khi chuyển PT; không đếm đúng lượt swap | Chưa có code dùng |
| 7 | TrainingPlans | Bỏ `IsPublished`; thêm `PublishedTitle`, `PublishedContent`, `PublishedAt` | BR-60 | PT sửa nháp thì khách mất bản đã xuất bản | Chưa có code dùng |
| 8 | Notifications | `TargetType` → `Audience` (6 giá trị) + `ScheduledAt`; `SentAt` NULL = chưa gửi | SRS 11.2.1 | Không gửi theo nhóm hoặc hẹn giờ được | Chưa có code dùng |
| 9 | CustomerDeletionRequests | PK `DeletionRequestId` → `CustomerDeletionRequestId` | NamingConvention | — | Chưa có code dùng |
| 10 | Customers, Employees | `UNIQUE(PersonId)` → filtered unique (chỉ tính hồ sơ còn hiệu lực) | DBR-65 | Nhân viên bị xóa hồ sơ khách không tạo lại được; không tuyển lại được | `CustomerDAO`, `EmployeeDAO` ✅ |

## 2. Thêm cột (chỉ thêm, code cũ không vỡ)

| Bảng | Cột mới | Rule |
|---|---|---|
| Orders | `PackageBenefitsSnapshot` | DBR-50 |
| Transactions | `ReceivedByEmployeeId`, `RefundedAt`, `RefundedByEmployeeId`, `RefundNote` | CBR-77, BR-47 |
| Subscriptions | `FixedStartTime` (gói PT), `MaxSwapsSnapshot` (P-08 lúc bán), `MaxFreezesSnapshot` (P-01 lúc bán) | CBR-43, GBR-04 |
| MaintenanceRequests | `RequestCode` BT-nnnn, `Priority`, `WarningTagPlaced`, `SetUnderMaintenance`, `AcceptanceNote` | CBR-101, SRS 8.2.1 |
| AuditLogs | `ActingRole` (vai trò đang dùng khi thao tác) | CBR-112, CBR-113 |
| TimeSlots | Status `Withdrawn` (PT gỡ slot trống thay vì xóa) | GBR-05 |
| Vouchers, PrivateNotes, TrainingPlans, BlogPosts, OperatingHourExceptions, Notifications | `CreatedAt` / `UpdatedAt` | GBR-08 |
| Persons, Customers, Employees, Packages, Vouchers, Facilities, PrivateNotes, TrainingPlans, BlogPosts, SystemConfigs | `RowVer ROWVERSION` | GBR-32 (lưu khi bản ghi đã bị người khác sửa → từ chối) |

## 3. Nới / siết ràng buộc

| Bảng | Thay đổi | Rule |
|---|---|---|
| TimeSlots | Bỏ CHECK ghi cứng 90 phút (dùng P-06) | DBR-20, GBR-04 |
| TimeSlots | Slot `Held` không bắt buộc `HoldExpiresAt` (đơn tại quầy không có hạn) | DBR-36, CBR-78 |
| Orders | Online bắt buộc `PaymentDueAt`; tại quầy thì NULL; Lễ tân hủy bắt buộc lý do ≥ 5 ký tự; chỉ có giảm giá khi có voucher | CBR-70, CBR-11, GBR-09 |
| Orders | `PaymentTimeout` / `ReplacedByNewOrder` chỉ áp cho đơn online | CBR-70, CBR-71 |
| Subscriptions | Trạng thái theo loại gói (Frozen chỉ gói tập, Used chỉ gói ngày…); gói ngày và buổi lẻ có StartDate = EndDate | BR-21, BR-29 |
| Sessions | Missed bắt buộc có mã lý do; Cancelled bắt buộc có loại hủy; PT hủy bắt buộc lý do + tick hoàn tiền | DBR-37, BR-66 |
| Customers | Self không được có thông tin giám hộ; số giám hộ đúng định dạng và 2 số không trùng nhau | DBR-24, DBR-25 |
| Mọi cột lý do | Bắt buộc ≥ 5 ký tự khi có (đã chặn bẫy `NULL` làm CHECK luôn đúng) | DBR-05, GBR-09 |
| FreezeRecords, CustomerDeletionRequests, LeaveRequests | Tối đa 1 bản ghi đang mở (filtered unique) | BR-24, CBR-16 |
| Shifts, LeaveRequests, MaintenanceRequests, Feedbacks | CHECK theo đúng luồng trạng thái (ai duyệt, khi nào) | DBR-44→46 |

## 4. Không đổi
`Persons`, `Accounts` vẫn như cũ (mô hình v25: 1 Person = 1 SĐT + 1 email + 1 tài khoản dùng cho mọi vai trò).

## 5. Tham số
`P-02` lưu `1` (tháng) · `P-04` = `1D,1M,3M,9M,12M` · **mới** `P-21` = `1M,2M,3M` (thời hạn gói PT) · `P-16` sửa thành danh sách đầu số 3 chữ số.
