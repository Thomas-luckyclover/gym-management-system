# Cấu trúc dự án — Gym Management System (GMS)

Tài liệu gốc về "file nào nằm ở đâu". Muốn thêm class/file mới → tra bảng ở đây trước, đặt đúng chỗ (agents.md Phần 2).

## 1. Cây thư mục

```
gms/
├── pom.xml                          Toàn bộ thư viện (Leader quản lý)
├── agents.md                        Bối cảnh + quy tắc cho AI (đọc đầu tiên)
├── NamingConvention.md              Quy tắc đặt tên
├── codeImplementationBackend.md     Quy tắc viết Java
├── codeImplementationFrontend.md    Quy tắc viết JSP/CSS/JS
├── README.md                        Cài đặt & chạy dự án
├── database/                        Script SQL Server dùng chung (schema, seed, migrations)
├── docs/
│   ├── project-structure.md         (file này)
│   ├── team-plan.md                 Chia việc 5 người, quy trình Git
│   ├── integration-guide.md         VNPay + Email: giải thích dễ hiểu, cần chuẩn bị gì
│   └── frontend-shared-usage.md     Cách dùng tokens.css / common.css / common.js
└── src/
    ├── main/
    │   ├── java/com/gms/
    │   │   ├── constant/            Role, SessionKey, Messages (MSG01…61), AppConstants
    │   │   ├── entity/              POJO ánh xạ bảng (Customer, Order, Session…)
    │   │   ├── dao/                 SQL (PreparedStatement), mỗi bảng 1 DAO
    │   │   ├── dto/                 ServiceResult, PageResult, JsonResponse, view object
    │   │   ├── service/             Nghiệp vụ, validate, transaction, audit log
    │   │   ├── servlet/             Controller, chia theo vai trò = khu vực URL
    │   │   │   ├── auth/            /login /register /forgot-password /account …
    │   │   │   ├── guest/           / (trang chủ), /blog
    │   │   │   ├── customer/        /customer/*
    │   │   │   ├── pt/              /pt/*
    │   │   │   ├── receptionist/    /receptionist/*
    │   │   │   ├── staff/           màn PT + Lễ tân dùng chung (1 Servlet, 2 URL)
    │   │   │   ├── manager/         /manager/*
    │   │   │   ├── admin/           /admin/*
    │   │   │   └── payment/         VNPay IPN + trang kết quả
    │   │   ├── filter/              CharacterEncoding, Auth, 5 Filter phân quyền theo khu vực
    │   │   ├── listener/            SessionRegistryListener (đăng xuất mọi thiết bị)
    │   │   ├── job/                 Scheduler + JOB-01…07
    │   │   ├── integration/         VnPayClient, EmailGateway
    │   │   └── util/                AppConfig, DBContext, PasswordUtil, ValidationUtil,
    │   │                            DateTimeUtil, MoneyUtil, JsonUtil, RequestUtil,
    │   │                            PaginationUtil, SqlUtil, SecureCodeUtil
    │   ├── resources/
    │   │   └── application.properties.example   → copy thành application.properties (không commit)
    │   └── webapp/
    │       ├── css/
    │       │   ├── tokens.css       Lớp 1: design token (màu, khoảng cách, chữ, bo góc…)
    │       │   ├── common.css       Lớp 2: nền tảng + thành phần dùng chung
    │       │   └── pages/<vai-trò>/<màn>.css   Lớp 3: CSS riêng từng màn
    │       ├── js/
    │       │   ├── common.js        JS dùng chung (toast, dialog, validate, AJAX, format…)
    │       │   └── pages/<vai-trò>/<màn>.js    JS riêng từng màn
    │       ├── images/
    │       └── WEB-INF/
    │           ├── web.xml          CHỈ error-page 403/404/500
    │           ├── gms-functions.tld   Hàm EL: formatMoney, formatDate, formatTime, formatDateTime
    │           └── jsp/
    │               ├── common/      head.jspf, flash-toast.jspf (+ header/sidebar/footer theo vai trò)
    │               ├── error/       403.jsp, 404.jsp, 500.jsp
    │               └── auth/ guest/ customer/ pt/ receptionist/ staff/ manager/ admin/
    └── test/
        ├── java/com/gms/            UtilTest, VnPayClientTest (chạy: mvn test)
        └── resources/application.properties   cấu hình giả cho test
```

Vai trò `<vai-trò>` dùng cùng 1 bộ tên ở mọi nơi: `auth`, `guest`, `customer`, `pt`, `receptionist`, `staff`, `manager`, `admin`.
Ví dụ màn **Đặt buổi lẻ**: `servlet/customer/BookSingleSessionServlet.java` → `WEB-INF/jsp/customer/book-session.jsp`
→ `css/pages/customer/book-session.css` → `js/pages/customer/book-session.js`.

## 2. Luồng 1 request (MVC)

```
Trình duyệt ─► Filter (UTF-8 → đã đăng nhập? → đúng vai trò?)
           ─► Servlet (đọc tham số bằng RequestUtil)
           ─► Service (ValidationUtil → DAO → ServiceResult)
           ─► Servlet: forward JSP  |  redirect + setFlashToast  |  JsonUtil.writeJson
           ─► JSP (JSTL + c:out, tokens.css/common.css/common.js)
```

## 3. Bảng màn hình → Servlet → URL → JSP (67 màn SRS 5.1 + 5 điểm kỹ thuật)

Quy ước URL: danh sách `/<khu>/<đối-tượng-số-nhiều>`, form thêm/sửa `…/form?id=`, chi tiết `…/detail?id=`,
duyệt `…/review?id=`. Tên JSP kebab-case. Cột "Phụ trách" là gợi ý — xem docs/team-plan.md.

| # SRS | Màn hình | Servlet (package `servlet.<pkg>`) | URL | JSP (`WEB-INF/jsp/…`) | UC / ghi chú | Phụ trách |
|---|---|---|---|---|---|---|
| 1 | Sign In | `auth.LoginServlet` | `/login` | `auth/login.jsp` | UC-01 | TV1 |
| - | Logout (POST) | `auth.LogoutServlet` | `/logout` | - | UC-02 | TV1 |
| 2 | Register Account | `auth.RegisterServlet` | `/register` | `auth/register.jsp` | UC-05, UC-Claim, UC-IDENT | TV1 |
| 3 | Forgot Password | `auth.ForgotPasswordServlet` | `/forgot-password` | `auth/forgot-password.jsp` | UC-03 | TV1 |
| 4 | Account Settings | `auth.AccountSettingsServlet` | `/account` | `auth/account-settings.jsp` | UC-04, mọi vai trò | TV1 |
| 5 | Home | `guest.HomeServlet` | `"" (trang gốc)` | `guest/home.jsp` | UC-06 | TV2 |
| 6 | Blog List | `guest.BlogListServlet` | `/blog` | `guest/blog-list.jsp` | UC-06 | TV2 |
| 7 | Blog Details | `guest.BlogDetailServlet` | `/blog/detail` | `guest/blog-detail.jsp` | UC-06 | TV2 |
| 48 | Customer Dashboard | `customer.CustomerDashboardServlet` | `/customer/dashboard` | `customer/dashboard.jsp` | UC-41 | TV5 |
| 35 | Package Selection | `customer.PackageSelectionServlet` | `/customer/packages` | `customer/package-selection.jsp` | UC-22 | TV1 |
| 36 | Checkout / Payment | `customer.CheckoutServlet` | `/customer/checkout` | `customer/checkout.jsp` | UC-22, UC-23, UC-PAY | TV1 |
| 40 | Personal Transaction History | `customer.TransactionHistoryServlet` | `/customer/transactions` | `customer/transaction-history.jsp` | UC-24 | TV1 |
| 50 | My Bookings | `customer.MyBookingServlet` | `/customer/bookings` | `customer/my-bookings.jsp` | UC-36, UC-39 | TV5 |
| 51 | My PT Sessions | `customer.MyPtSessionServlet` | `/customer/pt-sessions` | `customer/my-pt-sessions.jsp` | UC-36, UC-41 | TV5 |
| 52 | Book Single Session | `customer.BookSingleSessionServlet` | `/customer/book-session` | `customer/book-session.jsp` | UC-37 | TV5 |
| 53 | Swap Session | `customer.SwapSessionServlet` | `/customer/swap-session` | `customer/swap-session.jsp` | UC-38 | TV5 |
| 60 | My Plan | `customer.MyPlanServlet` | `/customer/plans` | `customer/my-plan.jsp` | UC-40 | TV5 |
| 61 | Submit Feedback | `customer.SubmitFeedbackServlet` | `/customer/feedback` | `customer/submit-feedback.jsp` | UC-42 | TV2 |
| 49 | PT Dashboard | `pt.PtDashboardServlet` | `/pt/dashboard` | `pt/dashboard.jsp` | UC-32 | TV5 |
| 54 | Available Time Slots | `pt.TimeSlotListServlet` | `/pt/time-slots` | `pt/time-slot-list.jsp` | UC-30 | TV5 |
| 55 | Add Time Slot | `pt.TimeSlotAddServlet` | `/pt/time-slots/add` | `pt/time-slot-add.jsp` | UC-30 | TV5 |
| 56 | Session Attendance | `pt.SessionAttendanceServlet` | `/pt/sessions/attendance` | `pt/session-attendance.jsp` | UC-31, UC-35 | TV5 |
| 57 | Client List | `pt.ClientListServlet` | `/pt/clients` | `pt/client-list.jsp` | UC-32 | TV5 |
| 58 | Private Client Notes | `pt.PrivateNoteServlet` | `/pt/clients/notes` | `pt/private-note.jsp` | UC-33 | TV5 |
| 59 | Workout & Diet Plan Editor | `pt.TrainingPlanEditorServlet` | `/pt/clients/plans` | `pt/training-plan-editor.jsp` | UC-34 | TV5 |
| 33 | Member Check-in | `receptionist.CheckInServlet` | `/receptionist/check-in` | `receptionist/check-in.jsp` | UC-19; trang chính của Lễ tân | TV4 |
| 28 | Customer Profile List | `receptionist.CustomerProfileListServlet` | `/receptionist/customers` | `receptionist/customer-list.jsp` | UC-16 | TV4 |
| 29 | Customer Profile Form | `receptionist.CustomerProfileFormServlet` | `/receptionist/customers/form` | `receptionist/customer-form.jsp` | UC-16 | TV4 |
| 34 | Freeze Membership | `receptionist.FreezeMembershipServlet` | `/receptionist/freeze` | `receptionist/freeze-membership.jsp` | UC-18 | TV4 |
| 37 | In-person Package Sale | `receptionist.CounterSaleServlet` | `/receptionist/sales` | `receptionist/counter-sale.jsp` | UC-20, UC-23 | TV4 |
| 38 | PT Package Sale | `receptionist.PtPackageSaleServlet` | `/receptionist/pt-package-sales` | `receptionist/pt-package-sale.jsp` | UC-21 | TV5 |
| 39 | Shift & Cash Reconciliation | `receptionist.ShiftServlet` | `/receptionist/shift` | `receptionist/shift.jsp` | UC-25 | TV4 |
| 42 | Facility Catalog View | `receptionist.FacilityCatalogServlet` | `/receptionist/facilities` | `receptionist/facility-catalog.jsp` | UC-27 (xem) | TV4 |
| 45 | Maintenance Request Form & My Requests | `receptionist.MaintenanceRequestServlet` | `/receptionist/maintenance-requests` | `receptionist/maintenance-requests.jsp` | UC-28 | TV4 |
| 25 | Leave Request Form | `staff.LeaveRequestFormServlet` | `/pt/leave-requests + /receptionist/leave-requests` | `staff/leave-request-form.jsp` | UC-14; 1 Servlet khai báo 2 URL | TV3 |
| 66 | Operational & Revenue Report | `manager.ManagerDashboardServlet` | `/manager/dashboard` | `manager/dashboard.jsp` | UC-46, xuất CSV | TV3 |
| 14 | Operating Parameters | `manager.ParameterListServlet` | `/manager/parameters` | `manager/parameter-list.jsp` | UC-09 | TV3 |
| 15 | Edit Parameter | `manager.ParameterEditServlet` | `/manager/parameters/edit` | `manager/parameter-edit.jsp` | UC-09 | TV3 |
| 16 | Operating Hours | `manager.OperatingHoursServlet` | `/manager/operating-hours` | `manager/operating-hours.jsp` | UC-11 | TV3 |
| 17 | Membership Package List | `manager.MembershipPackageListServlet` | `/manager/membership-packages` | `manager/membership-package-list.jsp` | UC-10 | TV3 |
| 18 | Membership Package Form | `manager.MembershipPackageFormServlet` | `/manager/membership-packages/form` | `manager/membership-package-form.jsp` | UC-10 | TV3 |
| 19 | PT Package List | `manager.PtPackageListServlet` | `/manager/pt-packages` | `manager/pt-package-list.jsp` | UC-10 | TV3 |
| 20 | PT Package Form | `manager.PtPackageFormServlet` | `/manager/pt-packages/form` | `manager/pt-package-form.jsp` | UC-10 | TV3 |
| 21 | Voucher List | `manager.VoucherListServlet` | `/manager/vouchers` | `manager/voucher-list.jsp` | UC-12 | TV3 |
| 22 | Voucher Form | `manager.VoucherFormServlet` | `/manager/vouchers/form` | `manager/voucher-form.jsp` | UC-12 | TV3 |
| 23 | Staff List | `manager.StaffListServlet` | `/manager/staff` | `manager/staff-list.jsp` | UC-13 | TV3 |
| 24 | Staff Details | `manager.StaffDetailServlet` | `/manager/staff/detail` | `manager/staff-detail.jsp` | UC-13 | TV3 |
| 26 | Leave Request List | `manager.LeaveRequestListServlet` | `/manager/leave-requests` | `manager/leave-request-list.jsp` | UC-15 | TV3 |
| 27 | Leave Request Approval | `manager.LeaveRequestReviewServlet` | `/manager/leave-requests/review` | `manager/leave-request-review.jsp` | UC-15 | TV3 |
| 30 | Customer List (Manager) | `manager.MemberListServlet` | `/manager/members` | `manager/member-list.jsp` | UC-41 (xem) | TV4 |
| 31 | Customer Details (Manager) | `manager.MemberDetailServlet` | `/manager/members/detail` | `manager/member-detail.jsp` | chỉ xem | TV4 |
| 32 | Member Deletion Approval | `manager.MemberDeletionReviewServlet` | `/manager/member-deletions/review` | `manager/member-deletion-review.jsp` | UC-17 | TV4 |
| 41 | System-wide Transactions | `manager.SystemTransactionServlet` | `/manager/transactions` | `manager/transaction-list.jsp` | UC-26, xuất Excel | TV1 |
| 43 | Facility List | `manager.FacilityListServlet` | `/manager/facilities` | `manager/facility-list.jsp` | UC-27 | TV4 |
| 44 | Facility Form | `manager.FacilityFormServlet` | `/manager/facilities/form` | `manager/facility-form.jsp` | UC-27 | TV4 |
| 46 | Maintenance Request List | `manager.MaintenanceRequestListServlet` | `/manager/maintenance-requests` | `manager/maintenance-request-list.jsp` | UC-29 | TV4 |
| 47 | Maintenance Request Approval | `manager.MaintenanceRequestReviewServlet` | `/manager/maintenance-requests/review` | `manager/maintenance-request-review.jsp` | UC-29 | TV4 |
| 62 | Feedback List | `manager.FeedbackListServlet` | `/manager/feedback` | `manager/feedback-list.jsp` | UC-43 | TV2 |
| 63 | Blog Management | `manager.BlogPostListServlet` | `/manager/blog-posts` | `manager/blog-post-list.jsp` | UC-45 | TV2 |
| 64 | Blog Post Editor | `manager.BlogPostEditorServlet` | `/manager/blog-posts/editor` | `manager/blog-post-editor.jsp` | UC-45 | TV2 |
| 65 | Send Notification | `manager.SendNotificationServlet` | `/manager/notifications/send` | `manager/send-notification.jsp` | UC-44 | TV2 |
| 67 | System Operational Report | `admin.AdminDashboardServlet` | `/admin/dashboard` | `admin/dashboard.jsp` | UC-47 | TV2 |
| 8 | User List | `admin.UserListServlet` | `/admin/users` | `admin/user-list.jsp` | UC-07 | TV2 |
| 9 | Add User Account | `admin.UserCreateServlet` | `/admin/users/create` | `admin/user-create.jsp` | UC-07 | TV2 |
| 10 | User Details | `admin.UserDetailServlet` | `/admin/users/detail` | `admin/user-detail.jsp` | UC-07 | TV2 |
| 11 | Temporarily Locked Customers | `admin.LockedCustomerListServlet` | `/admin/locked-customers` | `admin/locked-customer-list.jsp` | UC-07 | TV2 |
| 12 | Unlock Customer Account | `admin.LockedCustomerDetailServlet` | `/admin/locked-customers/detail` | `admin/locked-customer-detail.jsp` | UC-07 | TV2 |
| 13 | Audit Log | `admin.AuditLogServlet` | `/admin/audit-log` | `admin/audit-log.jsp` | UC-08 | TV2 |
| - | VNPay IPN webhook (API-02) | `payment.VnPayIpnServlet` | `/api/v1/commerce/payment/webhook/vnpay` | - (trả JSON) | server-to-server, công khai, bắt buộc kiểm chữ ký | TV1 |
| - | Trang kết quả sau VNPay | `payment.VnPayReturnServlet` | `/payment/vnpay-return` | `customer/payment-result.jsp` | CHỈ hiển thị, không cập nhật đơn | TV1 |

## 4. Model dự kiến (theo SRS 3.2 — chờ script DB chính thức để chốt tên cột)

| Zone | Entity | DAO | Service chính |
|---|---|---|---|
| A Identity | Person, Customer, Employee, Account, VerificationCode | PersonDAO, CustomerDAO, EmployeeDAO, AccountDAO, VerificationCodeDAO | AuthService, RegistrationService, CustomerService, EmployeeService, SystemAccountService |
| B Sales/Access | Package, Voucher, Order, Transaction, Shift, Subscription, FreezeRecord, CheckIn, CustomerDeletionRequest | 1 DAO / entity | PackageService, VoucherService, OrderService, PaymentService, TransactionService, ShiftService, FreezeService, CheckInService, CustomerDeletionService |
| C Scheduling | TimeSlot, Session, SessionSwapLog | 1 DAO / entity | TimeSlotService, SessionService, PtSubscriptionService |
| D HR/Facility | LeaveRequest, Facility, MaintenanceRequest | 1 DAO / entity | LeaveRequestService, FacilityService, MaintenanceRequestService |
| E PT content | PrivateNote, TrainingPlan | 1 DAO / entity | PrivateNoteService, TrainingPlanService |
| F Communication | Feedback, BlogPost, Notification, NotificationRecipient | 1 DAO / entity | FeedbackService, BlogService, NotificationService |
| G Config/Audit | SystemConfig, OperatingHourException, AuditLog | 1 DAO / entity | SystemConfigService, OperatingHoursService, AuditLogService |
| Báo cáo | — | ReportDAO | ReportService, SystemReportService, DashboardService |

`SystemConfigService` và `AuditLogService` là 2 service **mọi người đều gọi** → làm sớm nhất (tuần 1).
