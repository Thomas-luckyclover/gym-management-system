-- DB-00: kiểm tra đúng danh tính; không lấy Email/PasswordHash.
SELECT e.EmployeeId,e.EmployeeCode,p.FullName,e.Position,e.Status AS EmployeeStatus,
       a.AccountId,a.Status AS AccountStatus
FROM Employees e JOIN Persons p ON p.PersonId=e.PersonId
JOIN Accounts a ON a.PersonId=e.PersonId
WHERE e.EmployeeCode IN ('NV-0003','NV-0014')
   OR (p.FullName=N'Trần Quốc Việt' AND e.Position='Manager');
-- Kỳ vọng: Tâm NV-0003 Receptionist, Tú NV-0014 PT và đúng 1 Manager Việt;
-- EmployeeStatus=Working, AccountStatus=Active. Nếu không khớp: ghi Blocked,
-- không gán mã/ID giả. Dùng AccountId/EmployeeId thực tế từ kết quả này.
-- Kiểm tra giờ môi trường; ngày/giờ trên UI theo UTC+7:
SELECT SYSDATETIME() AS SqlServerNow;
-- Đối chiếu P-12/P-23 và định biên trên màn tham số; không giả định giá trị cấu hình.

GO

-- DB-01 | IT-LR-01 | CHỈ SELECT; không tự sửa dữ liệu.
-- Chạy trước gửi (kỳ vọng 0 dòng marker), ngay sau gửi (lưu ID), sau quyết định.
DECLARE @Tag nvarchar(50)=N'[IT-LR-01]';
DECLARE @Start datetime2='2026-11-06T14:00:00';
DECLARE @End datetime2='2026-11-06T14:15:00';
SELECT lr.LeaveRequestId,e.EmployeeCode,p.FullName,lr.LeaveGroupId,
       lr.LeaveType,lr.FromDate,lr.ToDate,lr.ShiftType,lr.Days,
       lr.Reason,lr.Status,lr.SubmittedAt,lr.ReviewedAt,
       lr.ReviewedByEmployeeId,rp.FullName AS ReviewerName,lr.RejectReason
FROM LeaveRequests lr JOIN Employees e ON e.EmployeeId=lr.EmployeeId
JOIN Persons p ON p.PersonId=e.PersonId
LEFT JOIN Employees re ON re.EmployeeId=lr.ReviewedByEmployeeId
LEFT JOIN Persons rp ON rp.PersonId=re.PersonId
WHERE LEFT(lr.Reason,LEN(@Tag))=@Tag ORDER BY lr.FromDate,lr.LeaveRequestId;

-- Với đơn bị rút: audit Nộp còn marker trong Reason nên vẫn tìm được EntityId.
WITH CaseIds AS (
 SELECT LeaveRequestId AS Id FROM LeaveRequests WHERE LEFT(Reason,LEN(@Tag))=@Tag
 UNION
 SELECT EntityId FROM AuditLogs WHERE EntityName='LeaveRequests'
   AND LEFT(Reason,LEN(@Tag))=@Tag AND CreatedAt>=@Start AND CreatedAt<@End
)
SELECT a.AuditLogId,a.EntityId,a.AccountId,a.ActingRole,a.Action,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN CaseIds c ON c.Id=a.EntityId
WHERE a.EntityName='LeaveRequests' AND a.CreatedAt>=@Start AND a.CreatedAt<@End
ORDER BY a.CreatedAt,a.AuditLogId;
-- Nếu case yêu cầu 0 dòng sau DELETE, đối chiếu bằng ID đã lưu ngay sau gửi,
-- không dùng chỉ bộ lọc Reason để kết luận chưa từng có bản ghi.

GO

-- DB-02 | IT-LR-02 | CHỈ SELECT; không tự sửa dữ liệu.
-- Chạy trước gửi (kỳ vọng 0 dòng marker), ngay sau gửi (lưu ID), sau quyết định.
DECLARE @Tag nvarchar(50)=N'[IT-LR-02]';
DECLARE @Start datetime2='2026-11-06T14:15:00';
DECLARE @End datetime2='2026-11-06T14:30:00';
SELECT lr.LeaveRequestId,e.EmployeeCode,p.FullName,lr.LeaveGroupId,
       lr.LeaveType,lr.FromDate,lr.ToDate,lr.ShiftType,lr.Days,
       lr.Reason,lr.Status,lr.SubmittedAt,lr.ReviewedAt,
       lr.ReviewedByEmployeeId,rp.FullName AS ReviewerName,lr.RejectReason
FROM LeaveRequests lr JOIN Employees e ON e.EmployeeId=lr.EmployeeId
JOIN Persons p ON p.PersonId=e.PersonId
LEFT JOIN Employees re ON re.EmployeeId=lr.ReviewedByEmployeeId
LEFT JOIN Persons rp ON rp.PersonId=re.PersonId
WHERE LEFT(lr.Reason,LEN(@Tag))=@Tag ORDER BY lr.FromDate,lr.LeaveRequestId;

-- Với đơn bị rút: audit Nộp còn marker trong Reason nên vẫn tìm được EntityId.
WITH CaseIds AS (
 SELECT LeaveRequestId AS Id FROM LeaveRequests WHERE LEFT(Reason,LEN(@Tag))=@Tag
 UNION
 SELECT EntityId FROM AuditLogs WHERE EntityName='LeaveRequests'
   AND LEFT(Reason,LEN(@Tag))=@Tag AND CreatedAt>=@Start AND CreatedAt<@End
)
SELECT a.AuditLogId,a.EntityId,a.AccountId,a.ActingRole,a.Action,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN CaseIds c ON c.Id=a.EntityId
WHERE a.EntityName='LeaveRequests' AND a.CreatedAt>=@Start AND a.CreatedAt<@End
ORDER BY a.CreatedAt,a.AuditLogId;
-- Nếu case yêu cầu 0 dòng sau DELETE, đối chiếu bằng ID đã lưu ngay sau gửi,
-- không dùng chỉ bộ lọc Reason để kết luận chưa từng có bản ghi.

GO

-- DB-03 | IT-LR-03 | CHỈ SELECT; không tự sửa dữ liệu.
-- Chạy trước gửi (kỳ vọng 0 dòng marker), ngay sau gửi (lưu ID), sau quyết định.
DECLARE @Tag nvarchar(50)=N'[IT-LR-03]';
DECLARE @Start datetime2='2026-11-06T14:30:00';
DECLARE @End datetime2='2026-11-06T14:45:00';
SELECT lr.LeaveRequestId,e.EmployeeCode,p.FullName,lr.LeaveGroupId,
       lr.LeaveType,lr.FromDate,lr.ToDate,lr.ShiftType,lr.Days,
       lr.Reason,lr.Status,lr.SubmittedAt,lr.ReviewedAt,
       lr.ReviewedByEmployeeId,rp.FullName AS ReviewerName,lr.RejectReason
FROM LeaveRequests lr JOIN Employees e ON e.EmployeeId=lr.EmployeeId
JOIN Persons p ON p.PersonId=e.PersonId
LEFT JOIN Employees re ON re.EmployeeId=lr.ReviewedByEmployeeId
LEFT JOIN Persons rp ON rp.PersonId=re.PersonId
WHERE LEFT(lr.Reason,LEN(@Tag))=@Tag ORDER BY lr.FromDate,lr.LeaveRequestId;

-- Với đơn bị rút: audit Nộp còn marker trong Reason nên vẫn tìm được EntityId.
WITH CaseIds AS (
 SELECT LeaveRequestId AS Id FROM LeaveRequests WHERE LEFT(Reason,LEN(@Tag))=@Tag
 UNION
 SELECT EntityId FROM AuditLogs WHERE EntityName='LeaveRequests'
   AND LEFT(Reason,LEN(@Tag))=@Tag AND CreatedAt>=@Start AND CreatedAt<@End
)
SELECT a.AuditLogId,a.EntityId,a.AccountId,a.ActingRole,a.Action,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN CaseIds c ON c.Id=a.EntityId
WHERE a.EntityName='LeaveRequests' AND a.CreatedAt>=@Start AND a.CreatedAt<@End
ORDER BY a.CreatedAt,a.AuditLogId;
-- Nếu case yêu cầu 0 dòng sau DELETE, đối chiếu bằng ID đã lưu ngay sau gửi,
-- không dùng chỉ bộ lọc Reason để kết luận chưa từng có bản ghi.

GO

-- DB-04 | IT-LR-04 | CHỈ SELECT; không tự sửa dữ liệu.
-- Chạy trước gửi (kỳ vọng 0 dòng marker), ngay sau gửi (lưu ID), sau quyết định.
DECLARE @Tag nvarchar(50)=N'[IT-LR-04]';
DECLARE @Start datetime2='2026-11-06T15:00:00';
DECLARE @End datetime2='2026-11-06T15:15:00';
SELECT lr.LeaveRequestId,e.EmployeeCode,p.FullName,lr.LeaveGroupId,
       lr.LeaveType,lr.FromDate,lr.ToDate,lr.ShiftType,lr.Days,
       lr.Reason,lr.Status,lr.SubmittedAt,lr.ReviewedAt,
       lr.ReviewedByEmployeeId,rp.FullName AS ReviewerName,lr.RejectReason
FROM LeaveRequests lr JOIN Employees e ON e.EmployeeId=lr.EmployeeId
JOIN Persons p ON p.PersonId=e.PersonId
LEFT JOIN Employees re ON re.EmployeeId=lr.ReviewedByEmployeeId
LEFT JOIN Persons rp ON rp.PersonId=re.PersonId
WHERE LEFT(lr.Reason,LEN(@Tag))=@Tag ORDER BY lr.FromDate,lr.LeaveRequestId;

-- Với đơn bị rút: audit Nộp còn marker trong Reason nên vẫn tìm được EntityId.
WITH CaseIds AS (
 SELECT LeaveRequestId AS Id FROM LeaveRequests WHERE LEFT(Reason,LEN(@Tag))=@Tag
 UNION
 SELECT EntityId FROM AuditLogs WHERE EntityName='LeaveRequests'
   AND LEFT(Reason,LEN(@Tag))=@Tag AND CreatedAt>=@Start AND CreatedAt<@End
)
SELECT a.AuditLogId,a.EntityId,a.AccountId,a.ActingRole,a.Action,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN CaseIds c ON c.Id=a.EntityId
WHERE a.EntityName='LeaveRequests' AND a.CreatedAt>=@Start AND a.CreatedAt<@End
ORDER BY a.CreatedAt,a.AuditLogId;
-- Nếu case yêu cầu 0 dòng sau DELETE, đối chiếu bằng ID đã lưu ngay sau gửi,
-- không dùng chỉ bộ lọc Reason để kết luận chưa từng có bản ghi.

GO

-- DB-05 | IT-LR-05 | CHỈ SELECT; không tự sửa dữ liệu.
-- Chạy trước gửi (kỳ vọng 0 dòng marker), ngay sau gửi (lưu ID), sau quyết định.
DECLARE @Tag nvarchar(50)=N'[IT-LR-05]';
DECLARE @Start datetime2='2026-11-06T15:30:00';
DECLARE @End datetime2='2026-11-06T15:45:00';
SELECT lr.LeaveRequestId,e.EmployeeCode,p.FullName,lr.LeaveGroupId,
       lr.LeaveType,lr.FromDate,lr.ToDate,lr.ShiftType,lr.Days,
       lr.Reason,lr.Status,lr.SubmittedAt,lr.ReviewedAt,
       lr.ReviewedByEmployeeId,rp.FullName AS ReviewerName,lr.RejectReason
FROM LeaveRequests lr JOIN Employees e ON e.EmployeeId=lr.EmployeeId
JOIN Persons p ON p.PersonId=e.PersonId
LEFT JOIN Employees re ON re.EmployeeId=lr.ReviewedByEmployeeId
LEFT JOIN Persons rp ON rp.PersonId=re.PersonId
WHERE LEFT(lr.Reason,LEN(@Tag))=@Tag ORDER BY lr.FromDate,lr.LeaveRequestId;

-- Với đơn bị rút: audit Nộp còn marker trong Reason nên vẫn tìm được EntityId.
WITH CaseIds AS (
 SELECT LeaveRequestId AS Id FROM LeaveRequests WHERE LEFT(Reason,LEN(@Tag))=@Tag
 UNION
 SELECT EntityId FROM AuditLogs WHERE EntityName='LeaveRequests'
   AND LEFT(Reason,LEN(@Tag))=@Tag AND CreatedAt>=@Start AND CreatedAt<@End
)
SELECT a.AuditLogId,a.EntityId,a.AccountId,a.ActingRole,a.Action,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN CaseIds c ON c.Id=a.EntityId
WHERE a.EntityName='LeaveRequests' AND a.CreatedAt>=@Start AND a.CreatedAt<@End
ORDER BY a.CreatedAt,a.AuditLogId;
-- Nếu case yêu cầu 0 dòng sau DELETE, đối chiếu bằng ID đã lưu ngay sau gửi,
-- không dùng chỉ bộ lọc Reason để kết luận chưa từng có bản ghi.

GO

-- DB-06 | IT-LR-06 | CHỈ SELECT; không tự sửa dữ liệu.
-- Chạy trước gửi (kỳ vọng 0 dòng marker), ngay sau gửi (lưu ID), sau quyết định.
DECLARE @Tag nvarchar(50)=N'[IT-LR-06]';
DECLARE @Start datetime2='2026-11-06T16:00:00';
DECLARE @End datetime2='2026-11-06T16:15:00';
SELECT lr.LeaveRequestId,e.EmployeeCode,p.FullName,lr.LeaveGroupId,
       lr.LeaveType,lr.FromDate,lr.ToDate,lr.ShiftType,lr.Days,
       lr.Reason,lr.Status,lr.SubmittedAt,lr.ReviewedAt,
       lr.ReviewedByEmployeeId,rp.FullName AS ReviewerName,lr.RejectReason
FROM LeaveRequests lr JOIN Employees e ON e.EmployeeId=lr.EmployeeId
JOIN Persons p ON p.PersonId=e.PersonId
LEFT JOIN Employees re ON re.EmployeeId=lr.ReviewedByEmployeeId
LEFT JOIN Persons rp ON rp.PersonId=re.PersonId
WHERE LEFT(lr.Reason,LEN(@Tag))=@Tag ORDER BY lr.FromDate,lr.LeaveRequestId;

-- Với đơn bị rút: audit Nộp còn marker trong Reason nên vẫn tìm được EntityId.
WITH CaseIds AS (
 SELECT LeaveRequestId AS Id FROM LeaveRequests WHERE LEFT(Reason,LEN(@Tag))=@Tag
 UNION
 SELECT EntityId FROM AuditLogs WHERE EntityName='LeaveRequests'
   AND LEFT(Reason,LEN(@Tag))=@Tag AND CreatedAt>=@Start AND CreatedAt<@End
)
SELECT a.AuditLogId,a.EntityId,a.AccountId,a.ActingRole,a.Action,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN CaseIds c ON c.Id=a.EntityId
WHERE a.EntityName='LeaveRequests' AND a.CreatedAt>=@Start AND a.CreatedAt<@End
ORDER BY a.CreatedAt,a.AuditLogId;
-- Nếu case yêu cầu 0 dòng sau DELETE, đối chiếu bằng ID đã lưu ngay sau gửi,
-- không dùng chỉ bộ lọc Reason để kết luận chưa từng có bản ghi.

GO

-- DB-07 | IT-LR-07 | CHỈ SELECT; không tự sửa dữ liệu.
-- Chạy trước gửi (kỳ vọng 0 dòng marker), ngay sau gửi (lưu ID), sau quyết định.
DECLARE @Tag nvarchar(50)=N'[IT-LR-07]';
DECLARE @Start datetime2='2026-11-06T16:30:00';
DECLARE @End datetime2='2026-11-06T16:45:00';
SELECT lr.LeaveRequestId,e.EmployeeCode,p.FullName,lr.LeaveGroupId,
       lr.LeaveType,lr.FromDate,lr.ToDate,lr.ShiftType,lr.Days,
       lr.Reason,lr.Status,lr.SubmittedAt,lr.ReviewedAt,
       lr.ReviewedByEmployeeId,rp.FullName AS ReviewerName,lr.RejectReason
FROM LeaveRequests lr JOIN Employees e ON e.EmployeeId=lr.EmployeeId
JOIN Persons p ON p.PersonId=e.PersonId
LEFT JOIN Employees re ON re.EmployeeId=lr.ReviewedByEmployeeId
LEFT JOIN Persons rp ON rp.PersonId=re.PersonId
WHERE LEFT(lr.Reason,LEN(@Tag))=@Tag ORDER BY lr.FromDate,lr.LeaveRequestId;

-- Với đơn bị rút: audit Nộp còn marker trong Reason nên vẫn tìm được EntityId.
WITH CaseIds AS (
 SELECT LeaveRequestId AS Id FROM LeaveRequests WHERE LEFT(Reason,LEN(@Tag))=@Tag
 UNION
 SELECT EntityId FROM AuditLogs WHERE EntityName='LeaveRequests'
   AND LEFT(Reason,LEN(@Tag))=@Tag AND CreatedAt>=@Start AND CreatedAt<@End
)
SELECT a.AuditLogId,a.EntityId,a.AccountId,a.ActingRole,a.Action,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN CaseIds c ON c.Id=a.EntityId
WHERE a.EntityName='LeaveRequests' AND a.CreatedAt>=@Start AND a.CreatedAt<@End
ORDER BY a.CreatedAt,a.AuditLogId;
-- Nếu case yêu cầu 0 dòng sau DELETE, đối chiếu bằng ID đã lưu ngay sau gửi,
-- không dùng chỉ bộ lọc Reason để kết luận chưa từng có bản ghi.

GO

-- DB-08 | IT-LR-08 | CHỈ SELECT; không tự sửa dữ liệu.
-- Chạy trước gửi (kỳ vọng 0 dòng marker), ngay sau gửi (lưu ID), sau quyết định.
DECLARE @Tag nvarchar(50)=N'[IT-LR-08]';
DECLARE @Start datetime2='2026-11-06T17:00:00';
DECLARE @End datetime2='2026-11-06T17:15:00';
SELECT lr.LeaveRequestId,e.EmployeeCode,p.FullName,lr.LeaveGroupId,
       lr.LeaveType,lr.FromDate,lr.ToDate,lr.ShiftType,lr.Days,
       lr.Reason,lr.Status,lr.SubmittedAt,lr.ReviewedAt,
       lr.ReviewedByEmployeeId,rp.FullName AS ReviewerName,lr.RejectReason
FROM LeaveRequests lr JOIN Employees e ON e.EmployeeId=lr.EmployeeId
JOIN Persons p ON p.PersonId=e.PersonId
LEFT JOIN Employees re ON re.EmployeeId=lr.ReviewedByEmployeeId
LEFT JOIN Persons rp ON rp.PersonId=re.PersonId
WHERE LEFT(lr.Reason,LEN(@Tag))=@Tag ORDER BY lr.FromDate,lr.LeaveRequestId;

-- Với đơn bị rút: audit Nộp còn marker trong Reason nên vẫn tìm được EntityId.
WITH CaseIds AS (
 SELECT LeaveRequestId AS Id FROM LeaveRequests WHERE LEFT(Reason,LEN(@Tag))=@Tag
 UNION
 SELECT EntityId FROM AuditLogs WHERE EntityName='LeaveRequests'
   AND LEFT(Reason,LEN(@Tag))=@Tag AND CreatedAt>=@Start AND CreatedAt<@End
)
SELECT a.AuditLogId,a.EntityId,a.AccountId,a.ActingRole,a.Action,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN CaseIds c ON c.Id=a.EntityId
WHERE a.EntityName='LeaveRequests' AND a.CreatedAt>=@Start AND a.CreatedAt<@End
ORDER BY a.CreatedAt,a.AuditLogId;
-- Nếu case yêu cầu 0 dòng sau DELETE, đối chiếu bằng ID đã lưu ngay sau gửi,
-- không dùng chỉ bộ lọc Reason để kết luận chưa từng có bản ghi.

GO

-- DB-QUOTA: chạy trước/sau IT-LR-03 và IT-LR-08.
-- @Quota phải lấy đúng P-23 hiện hành của môi trường test, không dùng giá trị ví dụ.
-- Truy vấn trả UsedDays/PendingDays; Available=P-23-UsedDays-PendingDays.
SELECT e.EmployeeCode,
 ISNULL(SUM(CASE WHEN lr.Status='Approved' THEN lr.Days ELSE 0 END),0) AS UsedDays,
 ISNULL(SUM(CASE WHEN lr.Status='Pending' THEN lr.Days ELSE 0 END),0) AS PendingDays
FROM Employees e LEFT JOIN LeaveRequests lr ON lr.EmployeeId=e.EmployeeId
 AND lr.LeaveType='Annual' AND YEAR(lr.FromDate)=2026 AND lr.Status IN ('Approved','Pending')
WHERE e.EmployeeCode='NV-0014' GROUP BY e.EmployeeCode;

GO

-- Customer Management: đối soát CHỈ ĐỌC, không kết nối/tự sửa database.
-- DB-CM-01 | IT-CM-01 | chạy trước/sau từng bước được chỉ định.
DECLARE @Phone varchar(10)='0909070101';
DECLARE @Email nvarchar(254)=N'qa.cm01@example.com';
DECLARE @CustomerId int=NULL; -- điền ID thật đã lưu ngay sau lookup/tạo, không dùng ID ví dụ.
DECLARE @Start datetime2='2026-11-07T00:00:00';
DECLARE @End datetime2='2026-11-08T00:00:00';
-- Chạy riêng SELECT này khi cần kiểm tra Person bị lưu dở (SMTP failure).
SELECT p.PersonId,p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,
       a.AccountId,a.Status AS AccountStatus
FROM Persons p LEFT JOIN Accounts a ON a.PersonId=p.PersonId
WHERE p.Phone=@Phone OR (@Email IS NOT NULL AND p.Email=@Email);
-- Dependent: không có Phone cá nhân, lookup bằng GuardianPhone; ghi lại CustomerId.
SELECT c.CustomerId,c.PersonId,c.CustomerCode,c.CustomerType,c.Status,c.JoinedAt,c.DeletedAt,
       c.CheckInPhoto,c.GuardianName,c.GuardianRelationship,c.GuardianPhone,c.GuardianPhone2,
       p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,a.AccountId,a.Status AS AccountStatus
FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
LEFT JOIN Accounts a ON a.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone));
-- Yêu cầu xóa: RequestId và CustomerId là hai khóa khác nhau.
SELECT d.CustomerDeletionRequestId,d.CustomerId,d.RequestedByEmployeeId,
       d.ReviewedByEmployeeId,d.Status,d.Reason,d.RejectReason,d.RequestedAt,d.ReviewedAt
FROM CustomerDeletionRequests d JOIN Customers c ON c.CustomerId=d.CustomerId
JOIN Persons p ON p.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
ORDER BY d.CustomerDeletionRequestId;
-- Audit đối soát bằng EntityId và loại entity; không chỉ đếm tổng số log.
WITH Targets AS (
 SELECT c.CustomerId,c.PersonId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
 WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
    OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
), RelatedIds AS (
 SELECT 'Customers' AS EntityName,CustomerId AS EntityId FROM Targets
 UNION SELECT 'CustomerDeletionRequests',d.CustomerDeletionRequestId
 FROM CustomerDeletionRequests d JOIN Targets t ON t.CustomerId=d.CustomerId
)
SELECT a.AuditLogId,a.AccountId,a.ActingRole,a.Action,a.EntityName,a.EntityId,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN RelatedIds t ON t.EntityName=a.EntityName AND t.EntityId=a.EntityId
WHERE a.CreatedAt>=@Start AND a.CreatedAt<@End ORDER BY a.AuditLogId;
-- Với gói/buổi/giao dịch: so sánh snapshot ID và trạng thái đã chụp trước test.
-- Kết quả session/SMTP phải kèm bằng chứng UI/mailbox, không kết luận chỉ từ SQL.

GO

-- DB-CM-02 | IT-CM-02 | chạy trước/sau từng bước được chỉ định.
DECLARE @Phone varchar(10)='0909070102';
DECLARE @Email nvarchar(254)=NULL;
DECLARE @CustomerId int=NULL; -- điền ID thật đã lưu ngay sau lookup/tạo, không dùng ID ví dụ.
DECLARE @Start datetime2='2026-11-07T00:00:00';
DECLARE @End datetime2='2026-11-08T00:00:00';
-- Chạy riêng SELECT này khi cần kiểm tra Person bị lưu dở (SMTP failure).
SELECT p.PersonId,p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,
       a.AccountId,a.Status AS AccountStatus
FROM Persons p LEFT JOIN Accounts a ON a.PersonId=p.PersonId
WHERE p.Phone=@Phone OR (@Email IS NOT NULL AND p.Email=@Email);
-- Dependent: không có Phone cá nhân, lookup bằng GuardianPhone; ghi lại CustomerId.
SELECT c.CustomerId,c.PersonId,c.CustomerCode,c.CustomerType,c.Status,c.JoinedAt,c.DeletedAt,
       c.CheckInPhoto,c.GuardianName,c.GuardianRelationship,c.GuardianPhone,c.GuardianPhone2,
       p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,a.AccountId,a.Status AS AccountStatus
FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
LEFT JOIN Accounts a ON a.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone));
-- Yêu cầu xóa: RequestId và CustomerId là hai khóa khác nhau.
SELECT d.CustomerDeletionRequestId,d.CustomerId,d.RequestedByEmployeeId,
       d.ReviewedByEmployeeId,d.Status,d.Reason,d.RejectReason,d.RequestedAt,d.ReviewedAt
FROM CustomerDeletionRequests d JOIN Customers c ON c.CustomerId=d.CustomerId
JOIN Persons p ON p.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
ORDER BY d.CustomerDeletionRequestId;
-- Audit đối soát bằng EntityId và loại entity; không chỉ đếm tổng số log.
WITH Targets AS (
 SELECT c.CustomerId,c.PersonId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
 WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
    OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
), RelatedIds AS (
 SELECT 'Customers' AS EntityName,CustomerId AS EntityId FROM Targets
 UNION SELECT 'CustomerDeletionRequests',d.CustomerDeletionRequestId
 FROM CustomerDeletionRequests d JOIN Targets t ON t.CustomerId=d.CustomerId
)
SELECT a.AuditLogId,a.AccountId,a.ActingRole,a.Action,a.EntityName,a.EntityId,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN RelatedIds t ON t.EntityName=a.EntityName AND t.EntityId=a.EntityId
WHERE a.CreatedAt>=@Start AND a.CreatedAt<@End ORDER BY a.AuditLogId;
-- Với gói/buổi/giao dịch: so sánh snapshot ID và trạng thái đã chụp trước test.
-- Kết quả session/SMTP phải kèm bằng chứng UI/mailbox, không kết luận chỉ từ SQL.

GO

-- DB-CM-03 | IT-CM-03 | chạy trước/sau từng bước được chỉ định.
DECLARE @Phone varchar(10)='0909070103';
DECLARE @Email nvarchar(254)=NULL;
DECLARE @CustomerId int=NULL; -- điền ID thật đã lưu ngay sau lookup/tạo, không dùng ID ví dụ.
DECLARE @Start datetime2='2026-11-07T00:00:00';
DECLARE @End datetime2='2026-11-08T00:00:00';
-- Chạy riêng SELECT này khi cần kiểm tra Person bị lưu dở (SMTP failure).
SELECT p.PersonId,p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,
       a.AccountId,a.Status AS AccountStatus
FROM Persons p LEFT JOIN Accounts a ON a.PersonId=p.PersonId
WHERE p.Phone=@Phone OR (@Email IS NOT NULL AND p.Email=@Email);
-- Dependent: không có Phone cá nhân, lookup bằng GuardianPhone; ghi lại CustomerId.
SELECT c.CustomerId,c.PersonId,c.CustomerCode,c.CustomerType,c.Status,c.JoinedAt,c.DeletedAt,
       c.CheckInPhoto,c.GuardianName,c.GuardianRelationship,c.GuardianPhone,c.GuardianPhone2,
       p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,a.AccountId,a.Status AS AccountStatus
FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
LEFT JOIN Accounts a ON a.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone));
-- Yêu cầu xóa: RequestId và CustomerId là hai khóa khác nhau.
SELECT d.CustomerDeletionRequestId,d.CustomerId,d.RequestedByEmployeeId,
       d.ReviewedByEmployeeId,d.Status,d.Reason,d.RejectReason,d.RequestedAt,d.ReviewedAt
FROM CustomerDeletionRequests d JOIN Customers c ON c.CustomerId=d.CustomerId
JOIN Persons p ON p.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
ORDER BY d.CustomerDeletionRequestId;
-- Audit đối soát bằng EntityId và loại entity; không chỉ đếm tổng số log.
WITH Targets AS (
 SELECT c.CustomerId,c.PersonId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
 WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
    OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
), RelatedIds AS (
 SELECT 'Customers' AS EntityName,CustomerId AS EntityId FROM Targets
 UNION SELECT 'CustomerDeletionRequests',d.CustomerDeletionRequestId
 FROM CustomerDeletionRequests d JOIN Targets t ON t.CustomerId=d.CustomerId
)
SELECT a.AuditLogId,a.AccountId,a.ActingRole,a.Action,a.EntityName,a.EntityId,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN RelatedIds t ON t.EntityName=a.EntityName AND t.EntityId=a.EntityId
WHERE a.CreatedAt>=@Start AND a.CreatedAt<@End ORDER BY a.AuditLogId;
-- Với gói/buổi/giao dịch: so sánh snapshot ID và trạng thái đã chụp trước test.
-- Kết quả session/SMTP phải kèm bằng chứng UI/mailbox, không kết luận chỉ từ SQL.

GO

-- DB-CM-04 | IT-CM-04 | chạy trước/sau từng bước được chỉ định.
DECLARE @Phone varchar(10)='0909070104';
DECLARE @Email nvarchar(254)=N'qa.cm04.new@example.com';
DECLARE @CustomerId int=NULL; -- điền ID thật đã lưu ngay sau lookup/tạo, không dùng ID ví dụ.
DECLARE @Start datetime2='2026-11-07T00:00:00';
DECLARE @End datetime2='2026-11-08T00:00:00';
-- Chạy riêng SELECT này khi cần kiểm tra Person bị lưu dở (SMTP failure).
SELECT p.PersonId,p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,
       a.AccountId,a.Status AS AccountStatus
FROM Persons p LEFT JOIN Accounts a ON a.PersonId=p.PersonId
WHERE p.Phone=@Phone OR (@Email IS NOT NULL AND p.Email=@Email);
-- Dependent: không có Phone cá nhân, lookup bằng GuardianPhone; ghi lại CustomerId.
SELECT c.CustomerId,c.PersonId,c.CustomerCode,c.CustomerType,c.Status,c.JoinedAt,c.DeletedAt,
       c.CheckInPhoto,c.GuardianName,c.GuardianRelationship,c.GuardianPhone,c.GuardianPhone2,
       p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,a.AccountId,a.Status AS AccountStatus
FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
LEFT JOIN Accounts a ON a.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone));
-- Yêu cầu xóa: RequestId và CustomerId là hai khóa khác nhau.
SELECT d.CustomerDeletionRequestId,d.CustomerId,d.RequestedByEmployeeId,
       d.ReviewedByEmployeeId,d.Status,d.Reason,d.RejectReason,d.RequestedAt,d.ReviewedAt
FROM CustomerDeletionRequests d JOIN Customers c ON c.CustomerId=d.CustomerId
JOIN Persons p ON p.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
ORDER BY d.CustomerDeletionRequestId;
-- Audit đối soát bằng EntityId và loại entity; không chỉ đếm tổng số log.
WITH Targets AS (
 SELECT c.CustomerId,c.PersonId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
 WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
    OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
), RelatedIds AS (
 SELECT 'Customers' AS EntityName,CustomerId AS EntityId FROM Targets
 UNION SELECT 'CustomerDeletionRequests',d.CustomerDeletionRequestId
 FROM CustomerDeletionRequests d JOIN Targets t ON t.CustomerId=d.CustomerId
)
SELECT a.AuditLogId,a.AccountId,a.ActingRole,a.Action,a.EntityName,a.EntityId,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN RelatedIds t ON t.EntityName=a.EntityName AND t.EntityId=a.EntityId
WHERE a.CreatedAt>=@Start AND a.CreatedAt<@End ORDER BY a.AuditLogId;
-- Với gói/buổi/giao dịch: so sánh snapshot ID và trạng thái đã chụp trước test.
-- Kết quả session/SMTP phải kèm bằng chứng UI/mailbox, không kết luận chỉ từ SQL.

GO

-- DB-CM-10 | IT-CM-10 | chạy trước/sau từng bước được chỉ định.
DECLARE @Phone varchar(10)='0909070110';
DECLARE @Email nvarchar(254)=N'qa.cm10@example.com';
DECLARE @CustomerId int=NULL; -- điền ID thật đã lưu ngay sau lookup/tạo, không dùng ID ví dụ.
DECLARE @Start datetime2='2026-11-07T00:00:00';
DECLARE @End datetime2='2026-11-08T00:00:00';
-- Chạy riêng SELECT này khi cần kiểm tra Person bị lưu dở (SMTP failure).
SELECT p.PersonId,p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,
       a.AccountId,a.Status AS AccountStatus
FROM Persons p LEFT JOIN Accounts a ON a.PersonId=p.PersonId
WHERE p.Phone=@Phone OR (@Email IS NOT NULL AND p.Email=@Email);
-- Dependent: không có Phone cá nhân, lookup bằng GuardianPhone; ghi lại CustomerId.
SELECT c.CustomerId,c.PersonId,c.CustomerCode,c.CustomerType,c.Status,c.JoinedAt,c.DeletedAt,
       c.CheckInPhoto,c.GuardianName,c.GuardianRelationship,c.GuardianPhone,c.GuardianPhone2,
       p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,a.AccountId,a.Status AS AccountStatus
FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
LEFT JOIN Accounts a ON a.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone));
-- Yêu cầu xóa: RequestId và CustomerId là hai khóa khác nhau.
SELECT d.CustomerDeletionRequestId,d.CustomerId,d.RequestedByEmployeeId,
       d.ReviewedByEmployeeId,d.Status,d.Reason,d.RejectReason,d.RequestedAt,d.ReviewedAt
FROM CustomerDeletionRequests d JOIN Customers c ON c.CustomerId=d.CustomerId
JOIN Persons p ON p.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
ORDER BY d.CustomerDeletionRequestId;
-- Audit đối soát bằng EntityId và loại entity; không chỉ đếm tổng số log.
WITH Targets AS (
 SELECT c.CustomerId,c.PersonId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
 WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
    OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
), RelatedIds AS (
 SELECT 'Customers' AS EntityName,CustomerId AS EntityId FROM Targets
 UNION SELECT 'CustomerDeletionRequests',d.CustomerDeletionRequestId
 FROM CustomerDeletionRequests d JOIN Targets t ON t.CustomerId=d.CustomerId
)
SELECT a.AuditLogId,a.AccountId,a.ActingRole,a.Action,a.EntityName,a.EntityId,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN RelatedIds t ON t.EntityName=a.EntityName AND t.EntityId=a.EntityId
WHERE a.CreatedAt>=@Start AND a.CreatedAt<@End ORDER BY a.AuditLogId;
-- Với gói/buổi/giao dịch: so sánh snapshot ID và trạng thái đã chụp trước test.
-- Kết quả session/SMTP phải kèm bằng chứng UI/mailbox, không kết luận chỉ từ SQL.

GO

-- DB-CM-05 | IT-CM-05 | chạy trước/sau từng bước được chỉ định.
DECLARE @Phone varchar(10)='0909070105';
DECLARE @Email nvarchar(254)=NULL;
DECLARE @CustomerId int=NULL; -- điền ID thật đã lưu ngay sau lookup/tạo, không dùng ID ví dụ.
DECLARE @Start datetime2='2026-11-07T00:00:00';
DECLARE @End datetime2='2026-11-08T00:00:00';
-- Chạy riêng SELECT này khi cần kiểm tra Person bị lưu dở (SMTP failure).
SELECT p.PersonId,p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,
       a.AccountId,a.Status AS AccountStatus
FROM Persons p LEFT JOIN Accounts a ON a.PersonId=p.PersonId
WHERE p.Phone=@Phone OR (@Email IS NOT NULL AND p.Email=@Email);
-- Dependent: không có Phone cá nhân, lookup bằng GuardianPhone; ghi lại CustomerId.
SELECT c.CustomerId,c.PersonId,c.CustomerCode,c.CustomerType,c.Status,c.JoinedAt,c.DeletedAt,
       c.CheckInPhoto,c.GuardianName,c.GuardianRelationship,c.GuardianPhone,c.GuardianPhone2,
       p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,a.AccountId,a.Status AS AccountStatus
FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
LEFT JOIN Accounts a ON a.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone));
-- Yêu cầu xóa: RequestId và CustomerId là hai khóa khác nhau.
SELECT d.CustomerDeletionRequestId,d.CustomerId,d.RequestedByEmployeeId,
       d.ReviewedByEmployeeId,d.Status,d.Reason,d.RejectReason,d.RequestedAt,d.ReviewedAt
FROM CustomerDeletionRequests d JOIN Customers c ON c.CustomerId=d.CustomerId
JOIN Persons p ON p.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
ORDER BY d.CustomerDeletionRequestId;
-- Audit đối soát bằng EntityId và loại entity; không chỉ đếm tổng số log.
WITH Targets AS (
 SELECT c.CustomerId,c.PersonId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
 WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
    OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
), RelatedIds AS (
 SELECT 'Customers' AS EntityName,CustomerId AS EntityId FROM Targets
 UNION SELECT 'CustomerDeletionRequests',d.CustomerDeletionRequestId
 FROM CustomerDeletionRequests d JOIN Targets t ON t.CustomerId=d.CustomerId
)
SELECT a.AuditLogId,a.AccountId,a.ActingRole,a.Action,a.EntityName,a.EntityId,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN RelatedIds t ON t.EntityName=a.EntityName AND t.EntityId=a.EntityId
WHERE a.CreatedAt>=@Start AND a.CreatedAt<@End ORDER BY a.AuditLogId;
-- Với gói/buổi/giao dịch: so sánh snapshot ID và trạng thái đã chụp trước test.
-- Kết quả session/SMTP phải kèm bằng chứng UI/mailbox, không kết luận chỉ từ SQL.

GO

-- DB-CM-06 | IT-CM-06 | chạy trước/sau từng bước được chỉ định.
DECLARE @Phone varchar(10)='0909070106';
DECLARE @Email nvarchar(254)=NULL;
DECLARE @CustomerId int=NULL; -- điền ID thật đã lưu ngay sau lookup/tạo, không dùng ID ví dụ.
DECLARE @Start datetime2='2026-11-07T00:00:00';
DECLARE @End datetime2='2026-11-08T00:00:00';
-- Chạy riêng SELECT này khi cần kiểm tra Person bị lưu dở (SMTP failure).
SELECT p.PersonId,p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,
       a.AccountId,a.Status AS AccountStatus
FROM Persons p LEFT JOIN Accounts a ON a.PersonId=p.PersonId
WHERE p.Phone=@Phone OR (@Email IS NOT NULL AND p.Email=@Email);
-- Dependent: không có Phone cá nhân, lookup bằng GuardianPhone; ghi lại CustomerId.
SELECT c.CustomerId,c.PersonId,c.CustomerCode,c.CustomerType,c.Status,c.JoinedAt,c.DeletedAt,
       c.CheckInPhoto,c.GuardianName,c.GuardianRelationship,c.GuardianPhone,c.GuardianPhone2,
       p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,a.AccountId,a.Status AS AccountStatus
FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
LEFT JOIN Accounts a ON a.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone));
-- Yêu cầu xóa: RequestId và CustomerId là hai khóa khác nhau.
SELECT d.CustomerDeletionRequestId,d.CustomerId,d.RequestedByEmployeeId,
       d.ReviewedByEmployeeId,d.Status,d.Reason,d.RejectReason,d.RequestedAt,d.ReviewedAt
FROM CustomerDeletionRequests d JOIN Customers c ON c.CustomerId=d.CustomerId
JOIN Persons p ON p.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
ORDER BY d.CustomerDeletionRequestId;
-- Audit đối soát bằng EntityId và loại entity; không chỉ đếm tổng số log.
WITH Targets AS (
 SELECT c.CustomerId,c.PersonId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
 WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
    OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
), RelatedIds AS (
 SELECT 'Customers' AS EntityName,CustomerId AS EntityId FROM Targets
 UNION SELECT 'CustomerDeletionRequests',d.CustomerDeletionRequestId
 FROM CustomerDeletionRequests d JOIN Targets t ON t.CustomerId=d.CustomerId
)
SELECT a.AuditLogId,a.AccountId,a.ActingRole,a.Action,a.EntityName,a.EntityId,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN RelatedIds t ON t.EntityName=a.EntityName AND t.EntityId=a.EntityId
WHERE a.CreatedAt>=@Start AND a.CreatedAt<@End ORDER BY a.AuditLogId;
-- Với gói/buổi/giao dịch: so sánh snapshot ID và trạng thái đã chụp trước test.
-- Kết quả session/SMTP phải kèm bằng chứng UI/mailbox, không kết luận chỉ từ SQL.

GO

-- DB-CM-07 | IT-CM-07 | chạy trước/sau từng bước được chỉ định.
DECLARE @Phone varchar(10)='0909070107';
DECLARE @Email nvarchar(254)=NULL;
DECLARE @CustomerId int=NULL; -- điền ID thật đã lưu ngay sau lookup/tạo, không dùng ID ví dụ.
DECLARE @Start datetime2='2026-11-07T00:00:00';
DECLARE @End datetime2='2026-11-08T00:00:00';
-- Chạy riêng SELECT này khi cần kiểm tra Person bị lưu dở (SMTP failure).
SELECT p.PersonId,p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,
       a.AccountId,a.Status AS AccountStatus
FROM Persons p LEFT JOIN Accounts a ON a.PersonId=p.PersonId
WHERE p.Phone=@Phone OR (@Email IS NOT NULL AND p.Email=@Email);
-- Dependent: không có Phone cá nhân, lookup bằng GuardianPhone; ghi lại CustomerId.
SELECT c.CustomerId,c.PersonId,c.CustomerCode,c.CustomerType,c.Status,c.JoinedAt,c.DeletedAt,
       c.CheckInPhoto,c.GuardianName,c.GuardianRelationship,c.GuardianPhone,c.GuardianPhone2,
       p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,a.AccountId,a.Status AS AccountStatus
FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
LEFT JOIN Accounts a ON a.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone));
-- Yêu cầu xóa: RequestId và CustomerId là hai khóa khác nhau.
SELECT d.CustomerDeletionRequestId,d.CustomerId,d.RequestedByEmployeeId,
       d.ReviewedByEmployeeId,d.Status,d.Reason,d.RejectReason,d.RequestedAt,d.ReviewedAt
FROM CustomerDeletionRequests d JOIN Customers c ON c.CustomerId=d.CustomerId
JOIN Persons p ON p.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
ORDER BY d.CustomerDeletionRequestId;
-- Audit đối soát bằng EntityId và loại entity; không chỉ đếm tổng số log.
WITH Targets AS (
 SELECT c.CustomerId,c.PersonId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
 WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
    OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
), RelatedIds AS (
 SELECT 'Customers' AS EntityName,CustomerId AS EntityId FROM Targets
 UNION SELECT 'CustomerDeletionRequests',d.CustomerDeletionRequestId
 FROM CustomerDeletionRequests d JOIN Targets t ON t.CustomerId=d.CustomerId
)
SELECT a.AuditLogId,a.AccountId,a.ActingRole,a.Action,a.EntityName,a.EntityId,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN RelatedIds t ON t.EntityName=a.EntityName AND t.EntityId=a.EntityId
WHERE a.CreatedAt>=@Start AND a.CreatedAt<@End ORDER BY a.AuditLogId;
-- Với gói/buổi/giao dịch: so sánh snapshot ID và trạng thái đã chụp trước test.
-- Kết quả session/SMTP phải kèm bằng chứng UI/mailbox, không kết luận chỉ từ SQL.

GO

-- DB-CM-08 | IT-CM-08 | chạy trước/sau từng bước được chỉ định.
DECLARE @Phone varchar(10)='0909070108';
DECLARE @Email nvarchar(254)=NULL;
DECLARE @CustomerId int=NULL; -- điền ID thật đã lưu ngay sau lookup/tạo, không dùng ID ví dụ.
DECLARE @Start datetime2='2026-11-07T00:00:00';
DECLARE @End datetime2='2026-11-08T00:00:00';
-- Chạy riêng SELECT này khi cần kiểm tra Person bị lưu dở (SMTP failure).
SELECT p.PersonId,p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,
       a.AccountId,a.Status AS AccountStatus
FROM Persons p LEFT JOIN Accounts a ON a.PersonId=p.PersonId
WHERE p.Phone=@Phone OR (@Email IS NOT NULL AND p.Email=@Email);
-- Dependent: không có Phone cá nhân, lookup bằng GuardianPhone; ghi lại CustomerId.
SELECT c.CustomerId,c.PersonId,c.CustomerCode,c.CustomerType,c.Status,c.JoinedAt,c.DeletedAt,
       c.CheckInPhoto,c.GuardianName,c.GuardianRelationship,c.GuardianPhone,c.GuardianPhone2,
       p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,a.AccountId,a.Status AS AccountStatus
FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
LEFT JOIN Accounts a ON a.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone));
-- Yêu cầu xóa: RequestId và CustomerId là hai khóa khác nhau.
SELECT d.CustomerDeletionRequestId,d.CustomerId,d.RequestedByEmployeeId,
       d.ReviewedByEmployeeId,d.Status,d.Reason,d.RejectReason,d.RequestedAt,d.ReviewedAt
FROM CustomerDeletionRequests d JOIN Customers c ON c.CustomerId=d.CustomerId
JOIN Persons p ON p.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
ORDER BY d.CustomerDeletionRequestId;
-- Audit đối soát bằng EntityId và loại entity; không chỉ đếm tổng số log.
WITH Targets AS (
 SELECT c.CustomerId,c.PersonId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
 WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
    OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
), RelatedIds AS (
 SELECT 'Customers' AS EntityName,CustomerId AS EntityId FROM Targets
 UNION SELECT 'CustomerDeletionRequests',d.CustomerDeletionRequestId
 FROM CustomerDeletionRequests d JOIN Targets t ON t.CustomerId=d.CustomerId
)
SELECT a.AuditLogId,a.AccountId,a.ActingRole,a.Action,a.EntityName,a.EntityId,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN RelatedIds t ON t.EntityName=a.EntityName AND t.EntityId=a.EntityId
WHERE a.CreatedAt>=@Start AND a.CreatedAt<@End ORDER BY a.AuditLogId;
-- Với gói/buổi/giao dịch: so sánh snapshot ID và trạng thái đã chụp trước test.
-- Kết quả session/SMTP phải kèm bằng chứng UI/mailbox, không kết luận chỉ từ SQL.

GO

-- DB-CM-09 | IT-CM-09 | chạy trước/sau từng bước được chỉ định.
DECLARE @Phone varchar(10)='0909070109';
DECLARE @Email nvarchar(254)=NULL;
DECLARE @CustomerId int=NULL; -- điền ID thật đã lưu ngay sau lookup/tạo, không dùng ID ví dụ.
DECLARE @Start datetime2='2026-11-07T00:00:00';
DECLARE @End datetime2='2026-11-08T00:00:00';
-- Chạy riêng SELECT này khi cần kiểm tra Person bị lưu dở (SMTP failure).
SELECT p.PersonId,p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,
       a.AccountId,a.Status AS AccountStatus
FROM Persons p LEFT JOIN Accounts a ON a.PersonId=p.PersonId
WHERE p.Phone=@Phone OR (@Email IS NOT NULL AND p.Email=@Email);
-- Dependent: không có Phone cá nhân, lookup bằng GuardianPhone; ghi lại CustomerId.
SELECT c.CustomerId,c.PersonId,c.CustomerCode,c.CustomerType,c.Status,c.JoinedAt,c.DeletedAt,
       c.CheckInPhoto,c.GuardianName,c.GuardianRelationship,c.GuardianPhone,c.GuardianPhone2,
       p.FullName,p.DateOfBirth,p.Phone,p.Email,p.IsReleased,a.AccountId,a.Status AS AccountStatus
FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
LEFT JOIN Accounts a ON a.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone));
-- Yêu cầu xóa: RequestId và CustomerId là hai khóa khác nhau.
SELECT d.CustomerDeletionRequestId,d.CustomerId,d.RequestedByEmployeeId,
       d.ReviewedByEmployeeId,d.Status,d.Reason,d.RejectReason,d.RequestedAt,d.ReviewedAt
FROM CustomerDeletionRequests d JOIN Customers c ON c.CustomerId=d.CustomerId
JOIN Persons p ON p.PersonId=c.PersonId
WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
   OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
ORDER BY d.CustomerDeletionRequestId;
-- Audit đối soát bằng EntityId và loại entity; không chỉ đếm tổng số log.
WITH Targets AS (
 SELECT c.CustomerId,c.PersonId FROM Customers c JOIN Persons p ON p.PersonId=c.PersonId
 WHERE (@CustomerId IS NOT NULL AND c.CustomerId=@CustomerId)
    OR (@CustomerId IS NULL AND (p.Phone=@Phone OR c.GuardianPhone=@Phone))
), RelatedIds AS (
 SELECT 'Customers' AS EntityName,CustomerId AS EntityId FROM Targets
 UNION SELECT 'CustomerDeletionRequests',d.CustomerDeletionRequestId
 FROM CustomerDeletionRequests d JOIN Targets t ON t.CustomerId=d.CustomerId
)
SELECT a.AuditLogId,a.AccountId,a.ActingRole,a.Action,a.EntityName,a.EntityId,
       a.FieldChanged,a.OldValue,a.NewValue,a.Reason,a.CreatedAt
FROM AuditLogs a JOIN RelatedIds t ON t.EntityName=a.EntityName AND t.EntityId=a.EntityId
WHERE a.CreatedAt>=@Start AND a.CreatedAt<@End ORDER BY a.AuditLogId;
-- Với gói/buổi/giao dịch: so sánh snapshot ID và trạng thái đã chụp trước test.
-- Kết quả session/SMTP phải kèm bằng chứng UI/mailbox, không kết luận chỉ từ SQL.

GO

