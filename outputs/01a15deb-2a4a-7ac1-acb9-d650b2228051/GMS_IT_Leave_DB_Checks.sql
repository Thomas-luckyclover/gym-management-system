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
