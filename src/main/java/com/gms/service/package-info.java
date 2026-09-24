/**
 * Service — toàn bộ nghiệp vụ: validate (qua util/ValidationUtil), gọi DAO, mở transaction, ghi AuditLog.
 * Mọi method public trả ServiceResult; bắt SQLException cụ thể, e.printStackTrace(), trả Messages.MSG04_SYSTEM_BUSY.
 *
 * <p>Service dự kiến (xếp phẳng theo module, người phụ trách theo docs/team-plan.md):</p>
 * <pre>
 * TV1  AuthService, RegistrationService, VerificationCodeService, PasswordRecoveryService, AccountSettingsService,
 *      OrderService, PaymentService, TransactionService
 * TV2  SystemAccountService, AuditLogService, BlogService, NotificationService, FeedbackService, SystemReportService
 * TV3  SystemConfigService, OperatingHoursService, PackageService, VoucherService, EmployeeService,
 *      LeaveRequestService, ReportService
 * TV4  CustomerService, CustomerDeletionService, CheckInService, FreezeService, ShiftService, FacilityService,
 *      MaintenanceRequestService
 * TV5  TimeSlotService, SessionService, PtSubscriptionService, PrivateNoteService, TrainingPlanService, DashboardService
 * </pre>
 *
 * <p>Dùng chung, ai cũng gọi: AuditLogService.log(...) cho mọi thao tác nhạy cảm (GBR-07, GBR-08),
 * SystemConfigService.getInt("P-09") để đọc tham số P-01…P-20 (không hard-code).</p>
 */
package com.gms.service;
