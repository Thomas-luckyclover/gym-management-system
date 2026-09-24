/**
 * Entity — POJO ánh xạ 1-1 với bảng SQL Server (Model trong MVC). Tên class số ít, khớp tên bảng số nhiều
 * (bảng Customers → class Customer — NamingConvention.md Mục 1).
 *
 * <p>Quy tắc: chỉ field + constructor + getter/setter; setter chỉ gán, không validate (codeImplementationBackend.md Mục 7);
 * field bất biến (Id, mã hệ thống, thời điểm sự kiện — DBR-09, DBR-16) khai báo final, không setter.
 * Ngày dùng LocalDate, giờ LocalTime, thời điểm LocalDateTime, tiền dùng long. Trạng thái dùng enum lồng trong entity
 * (VD Order.Status) với giá trị tiếng Anh khớp DB (GBR-01).</p>
 *
 * <p>Entity theo database/1_Init_Schema.sql (30 bảng):</p>
 * <pre>
 * Zone A  Identity      Person, Customer, Employee, Account, VerificationCode
 * Zone B  Sales/Access  Package, Voucher, Order, Transaction, Shift, Subscription, FreezeRecord, CheckIn,
 *                       CustomerDeletionRequest
 * Zone C  Scheduling    TimeSlot, Session, SessionSwapLog
 * Zone D  HR/Facility   LeaveRequest, Facility, MaintenanceRequest, StaffAttendance (chưa dùng)
 * Zone E  PT content    PrivateNote, TrainingPlan
 * Zone F  Communication Feedback, BlogPost, Notification, NotificationRecipient
 * Zone G  Config/Audit  SystemConfig, OperatingHourException, AuditLog
 * </pre>
 */
package com.gms.entity;
