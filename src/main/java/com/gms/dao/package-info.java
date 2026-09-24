/**
 * DAO — truy vấn SQL Server, mỗi bảng 1 class hậu tố DAO (VD CustomerDAO).
 *
 * <p>Quy tắc bắt buộc:</p>
 * <ul>
 *   <li>Chỉ SQL, không nghiệp vụ, không gọi ValidationUtil.</li>
 *   <li>Luôn PreparedStatement, không nối chuỗi SQL; keyword SQL viết hoa, mỗi mệnh đề 1 dòng.</li>
 *   <li>Không tự catch — khai báo throws SQLException cho Service xử lý; null chỉ có nghĩa "không tìm thấy".</li>
 *   <li>Method dùng trong transaction nhận Connection từ Service truyền vào (Mục 13).</li>
 *   <li>Phân trang bằng OFFSET ? ROWS FETCH NEXT ? ROWS ONLY; tìm kiếm bằng COLLATE Vietnamese_CI_AI + SqlUtil.toContainsPattern.</li>
 *   <li>Map ResultSet → Entity bằng 1 method private mapResultSetToXxx(ResultSet) đặt cuối class.</li>
 * </ul>
 *
 * <p>DAO dự kiến: PersonDAO, CustomerDAO, EmployeeDAO, AccountDAO, VerificationCodeDAO, PackageDAO, VoucherDAO, OrderDAO,
 * TransactionDAO, ShiftDAO, SubscriptionDAO, FreezeRecordDAO, CheckInDAO, CustomerDeletionRequestDAO, TimeSlotDAO,
 * SessionDAO, SessionSwapLogDAO, LeaveRequestDAO, FacilityDAO, MaintenanceRequestDAO, PrivateNoteDAO, TrainingPlanDAO,
 * FeedbackDAO, BlogPostDAO, NotificationDAO, NotificationRecipientDAO, SystemConfigDAO, OperatingHourExceptionDAO,
 * AuditLogDAO, ReportDAO (truy vấn tổng hợp cho báo cáo, không gắn 1 bảng).</p>
 */
package com.gms.dao;
