# docs/handoff: hồ sơ giao việc cho AI viết lại code theo v25

## Cách dùng (copy prompt này cho AI)

```
Đọc agents.md (Phần 14 code theo đợt, Phần 15 quyết định v25), NamingConvention.md mục 2b (enum),
codeImplementationBackend.md Mục 1 (comment khung), rồi đọc docs/handoff/<file module>.md.
Lập bảng kế hoạch theo đợt cho module này, chờ tôi gõ "tiếp".
```

## Danh sách module

| File | Module | UC | Mức độ |
|---|---|---|---|
| `01_auth-login-role.md` | Đăng nhập, chọn vai trò, đăng ký, quên MK | 01, 02, 03, 05 | ✅ Đã code. Còn rút comment về dạng khung |
| `02_account-settings.md` | Cài đặt tài khoản | 04 | ✅ Đã sửa lỗ hổng. Còn audit `ActingRole`, tab theo vai trò |
| `03_admin-accounts.md` | Tài khoản hệ thống, mở khóa, audit log | 07, 08 | 🔴 Viết mới |
| `04_manager-staff-leave.md` | Hồ sơ nhân viên, nghỉ phép | 13, 14, 15 | 🔴 Viết mới |
| `05_customer-profile.md` | Hồ sơ khách, xóa hồ sơ | 16, 17, IDENT | 🔴 Viết mới |
| `06_catalog-params.md` | Tham số, giờ mở cửa, gói, voucher | 09, 10, 11, 12 | 🟠 Có `PackageDAO` (đã đổi cột thời hạn) |
| `07_sales-payment-shift.md` | Bán tại quầy, mua online, voucher, VNPay, ca | 20, 22–26, PAY | 🔴 Viết mới |
| `08_pt-package-sale.md` | Bán gói PT | 21 | 🔴 Viết mới |
| `09_pt-schedule-sessions.md` | Lịch PT, điểm danh, dời / chuyển / hủy, swap, buổi lẻ | 30–39 | 🟠 Có `PtDashboard` (đã đổi CancelType) |
| `10_freeze-checkin.md` | Bảo lưu, check-in | 18, 19 | 🟠 Có `CheckInService` |
| `11_content-feedback-notify.md` | Ghi chú, giáo án, feedback, thông báo, blog | 33, 34, 40, 42–45, 06 | 🟠 Có Blog |
| `12_reports-jobs.md` | Báo cáo Manager / Admin, job chạy ngầm | 46, 47, jobs | 🟠 Có Report DAO (đã đổi CancelType) |

## Quy ước chung cho mọi module
- Enum đúng bảng ở `NamingConvention.md` 2b. Tham số luôn đọc qua `SystemConfigService` (P-01…P-21).
- Nhân viên thao tác trên hồ sơ khách → gọi `SelfServiceGuard` trước khi ghi (CBR-115).
- Thao tác nhạy cảm → `AuditLogService` kèm `ActingRole` (CBR-112).
- Test bằng tài khoản và case ở `docs/database/SEED_CASES.md`.
