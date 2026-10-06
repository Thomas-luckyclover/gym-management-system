/* ============================================================================
   GMS — 3_Queries.sql · v26 (06/10/2026) · SQL Server 2019+
   Chạy 0 → 1 → 2 trước. File 3 gồm các khối độc lập, ngăn bằng GO.
   A: Kiểm tra sau khi dựng DB (chỉ SELECT và test luôn ROLLBACK).
   B1…B7: Truy vấn nghiệp vụ. Đổi @param ở đầu đúng khối cần dùng.
   C: Dọn P-07 trên DB cũ sau khi code đã bỏ tham số này.
   Các lệnh ghi B1/B4/C mặc định tắt bằng cờ; chỉ bật khối muốn chạy.
   Ngày nghiệp vụ = UTC+7; timestamp ghi theo SYSDATETIME() như yêu cầu.
   ============================================================================ */
USE GMS;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

/* ============================================================================
   A. KIỂM TRA SAU KHI DỰNG DATABASE
   ============================================================================ */
-- A1. Đủ bốn trạng thái: Scheduled, CheckedIn, Missed, Cancelled.
SELECT DISTINCT Status FROM Sessions ORDER BY Status;
-- A2. NoCheckIn, NoMembership, MembershipFrozen, NULL.
SELECT DISTINCT MissedReason FROM Sessions ORDER BY MissedReason;
-- A3. Không còn giá trị Completed; Used chỉ DayPass và SingleSession.
SELECT DISTINCT SubscriptionType, Status FROM Subscriptions ORDER BY SubscriptionType, Status;
-- A4. Chỉ P-21 và P-22; không có P-07.
SELECT ConfigKey, ConfigValue FROM SystemConfigs WHERE ConfigKey IN ('P-07','P-21','P-22');
GO

-- A5. Test âm tính: mỗi case luôn ROLLBACK, kiểm đúng lỗi mong đợi.
-- FAIL sẽ THROW sau khi chạy hết case; không coi lỗi FK hoặc lỗi SQL là pass.
DECLARE @Tests TABLE (Id INT PRIMARY KEY, Name NVARCHAR(100), SqlText NVARCHAR(MAX), ExpectedConstraint SYSNAME);
INSERT INTO @Tests VALUES
 (1,N'Rating thiếu PT',N'INSERT INTO Feedbacks(CustomerId,Content,Rating) VALUES(1,N''Nội dung test đủ dài'',3)',N'CK_Feedbacks_Rating'),
 (2,N'Rating=6',N'INSERT INTO Feedbacks(CustomerId,Content,RatedPtEmployeeId,Rating,RatingMonth) VALUES(1,N''Nội dung test đủ dài'',5,6,''2099-01-01'')',N'CK_Feedbacks_Rating'),
 (3,N'RatingMonth không phải ngày 1',N'INSERT INTO Feedbacks(CustomerId,Content,RatedPtEmployeeId,Rating,RatingMonth) VALUES(1,N''Nội dung test đủ dài'',5,3,''2026-09-02'')',N'CK_Feedbacks_Rating'),
 (4,N'Trùng khách/PT/tháng',N'INSERT INTO Feedbacks(CustomerId,Content,RatedPtEmployeeId,Rating,RatingMonth) SELECT TOP(1) CustomerId,N''Nội dung test đủ dài'',RatedPtEmployeeId,Rating,RatingMonth FROM Feedbacks WHERE RatedPtEmployeeId IS NOT NULL',N'UX_Feedbacks_RatingPerMonth'),
 (5,N'Sessions Completed',N'UPDATE Sessions SET Status=''Completed'' WHERE SessionId=(SELECT MIN(SessionId) FROM Sessions WHERE Status=''CheckedIn'')',N'CK_Sessions_Status'),
 (6,N'Missed MarkedAbsent',N'UPDATE Sessions SET MissedReason=''MarkedAbsent'' WHERE SessionId=(SELECT MIN(SessionId) FROM Sessions WHERE Status=''Missed'')',N'CK_Sessions_MissedReason'),
 (7,N'Membership Used',N'UPDATE Subscriptions SET Status=''Used'' WHERE SubscriptionId=(SELECT MIN(SubscriptionId) FROM Subscriptions WHERE SubscriptionType=''Membership'')',N'CK_Subscriptions_StatusByType'),
 (8,N'PT/tháng có giá trị nhưng Rating NULL',N'INSERT INTO Feedbacks(CustomerId,Content,RatedPtEmployeeId,RatingMonth) VALUES(1,N''Nội dung test đủ dài'',5,''2099-01-01'')',N'CK_Feedbacks_Rating');
DECLARE @Name NVARCHAR(100), @Sql NVARCHAR(MAX), @Constraint SYSNAME, @Failures INT=0;
DECLARE NegativeTests CURSOR LOCAL FAST_FORWARD FOR SELECT Name,SqlText,ExpectedConstraint FROM @Tests ORDER BY Id;
OPEN NegativeTests;
FETCH NEXT FROM NegativeTests INTO @Name,@Sql,@Constraint;
WHILE @@FETCH_STATUS=0
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION;
        EXEC sys.sp_executesql @Sql;
        ROLLBACK TRANSACTION;
        PRINT N'FAIL: lọt dữ liệu sai — '+@Name;
        SET @Failures+=1;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        IF ERROR_NUMBER() IN (547,2601,2627) AND CHARINDEX(@Constraint,ERROR_MESSAGE())>0
            PRINT N'OK: bị chặn đúng — '+@Name;
        ELSE
        BEGIN
            PRINT N'FAIL: lỗi khác — '+@Name+N': '+ERROR_MESSAGE();
            SET @Failures+=1;
        END
    END CATCH;
    FETCH NEXT FROM NegativeTests INTO @Name,@Sql,@Constraint;
END;
CLOSE NegativeTests;
DEALLOCATE NegativeTests;
IF @Failures>0 THROW 51000,N'Test âm tính thất bại; xem dòng FAIL phía trên.',1;
GO

/* ============================================================================
   B. TRUY VẤN NGHIỆP VỤ CHO ỨNG DỤNG
   ============================================================================ */
-- B1. JOB-04: xem trước và chốt từng buổi quá ngày VN.
-- @ExecuteJob=0: chỉ xem; =1: chạy UPDATE. Mỗi buổi 1 transaction, lỗi riêng.
DECLARE @ExecuteJob BIT=0;
DECLARE @Today DATE=CAST(DATEADD(HOUR,7,SYSUTCDATETIME()) AS DATE);
SELECT s.SessionId,s.TimeSlotId,sub.SubscriptionId,t.SlotDate,
       CASE WHEN f.Unavailable=1 THEN 'Missed'
            WHEN f.HasCheckIn=1 THEN 'CheckedIn' ELSE 'Missed' END AS NewStatus,
       CASE WHEN f.Unavailable=1 AND f.HasFrozen=1 THEN 'MembershipFrozen'
            WHEN f.Unavailable=1 THEN 'NoMembership'
            WHEN f.HasCheckIn=1 THEN NULL ELSE 'NoCheckIn' END AS NewMissedReason
FROM Sessions s
JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId
JOIN Subscriptions sub ON sub.SubscriptionId=s.SubscriptionId
JOIN Orders o ON o.OrderId=sub.OrderId
CROSS APPLY (
    SELECT CASE WHEN EXISTS(SELECT 1 FROM Subscriptions m JOIN FreezeRecords fr ON fr.SubscriptionId=m.SubscriptionId
                       WHERE m.CustomerId=sub.CustomerId AND m.SubscriptionType='Membership'
                         AND t.SlotDate BETWEEN fr.StartDate AND COALESCE(fr.ActualEndDate,fr.PlannedEndDate)) THEN 1 ELSE 0 END AS HolderFrozen,
           CASE WHEN EXISTS(SELECT 1 FROM Subscriptions m WHERE m.CustomerId=sub.CustomerId
                       AND m.SubscriptionType='Membership' AND m.Status IN ('Active','Frozen')
                       AND t.SlotDate BETWEEN m.StartDate AND m.EndDate) THEN 1 ELSE 0 END AS HolderMembership,
           CASE WHEN EXISTS(SELECT 1 FROM Subscriptions m JOIN FreezeRecords fr ON fr.SubscriptionId=m.SubscriptionId
                       WHERE m.CustomerId=o.PartnerCustomerId AND m.SubscriptionType='Membership'
                         AND t.SlotDate BETWEEN fr.StartDate AND COALESCE(fr.ActualEndDate,fr.PlannedEndDate)) THEN 1 ELSE 0 END AS PartnerFrozen,
           CASE WHEN EXISTS(SELECT 1 FROM Subscriptions m WHERE m.CustomerId=o.PartnerCustomerId
                       AND m.SubscriptionType='Membership' AND m.Status IN ('Active','Frozen')
                       AND t.SlotDate BETWEEN m.StartDate AND m.EndDate) THEN 1 ELSE 0 END AS PartnerMembership
) m
CROSS APPLY (
    SELECT CASE WHEN (m.HolderFrozen=1 OR m.HolderMembership=0)
                     AND (sub.SubscriptionType<>'PTCouple' OR m.PartnerFrozen=1 OR m.PartnerMembership=0)
                THEN 1 ELSE 0 END AS Unavailable,
           CASE WHEN m.HolderFrozen=1 OR (sub.SubscriptionType='PTCouple' AND m.PartnerFrozen=1) THEN 1 ELSE 0 END AS HasFrozen,
           CASE WHEN EXISTS(SELECT 1 FROM CheckIns ci JOIN Subscriptions gym ON gym.SubscriptionId=ci.SubscriptionId
                       WHERE gym.SubscriptionType IN ('Membership','DayPass')
                         AND (gym.CustomerId=sub.CustomerId OR (sub.SubscriptionType='PTCouple' AND gym.CustomerId=o.PartnerCustomerId))
                         AND ci.CheckedInAt>=CAST(t.SlotDate AS DATETIME2)
                         AND ci.CheckedInAt<DATEADD(DAY,1,CAST(t.SlotDate AS DATETIME2))) THEN 1 ELSE 0 END AS HasCheckIn
) f
WHERE s.Status='Scheduled' AND t.SlotDate<@Today
ORDER BY s.SessionId;

IF @ExecuteJob=1
BEGIN
    DECLARE @SessionId INT, @TimeSlotId INT, @NewStatus VARCHAR(10), @NewReason VARCHAR(20);
    DECLARE ClosingSessions CURSOR LOCAL FAST_FORWARD FOR
        SELECT s.SessionId FROM Sessions s JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId
        WHERE s.Status='Scheduled' AND t.SlotDate<@Today ORDER BY s.SessionId;
    OPEN ClosingSessions;
    FETCH NEXT FROM ClosingSessions INTO @SessionId;
    WHILE @@FETCH_STATUS=0
    BEGIN
        BEGIN TRY
            BEGIN TRANSACTION;
            SET @NewStatus=NULL;
            SET @NewReason=NULL;
SELECT @TimeSlotId=s.TimeSlotId, @NewStatus=
       CASE WHEN f.Unavailable=1 THEN 'Missed'
            WHEN f.HasCheckIn=1 THEN 'CheckedIn' ELSE 'Missed' END , @NewReason=
       CASE WHEN f.Unavailable=1 AND f.HasFrozen=1 THEN 'MembershipFrozen'
            WHEN f.Unavailable=1 THEN 'NoMembership'
            WHEN f.HasCheckIn=1 THEN NULL ELSE 'NoCheckIn' END
FROM Sessions s WITH (UPDLOCK,HOLDLOCK)
JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId
JOIN Subscriptions sub ON sub.SubscriptionId=s.SubscriptionId
JOIN Orders o ON o.OrderId=sub.OrderId
CROSS APPLY (
    SELECT CASE WHEN EXISTS(SELECT 1 FROM Subscriptions m JOIN FreezeRecords fr ON fr.SubscriptionId=m.SubscriptionId
                       WHERE m.CustomerId=sub.CustomerId AND m.SubscriptionType='Membership'
                         AND t.SlotDate BETWEEN fr.StartDate AND COALESCE(fr.ActualEndDate,fr.PlannedEndDate)) THEN 1 ELSE 0 END AS HolderFrozen,
           CASE WHEN EXISTS(SELECT 1 FROM Subscriptions m WHERE m.CustomerId=sub.CustomerId
                       AND m.SubscriptionType='Membership' AND m.Status IN ('Active','Frozen')
                       AND t.SlotDate BETWEEN m.StartDate AND m.EndDate) THEN 1 ELSE 0 END AS HolderMembership,
           CASE WHEN EXISTS(SELECT 1 FROM Subscriptions m JOIN FreezeRecords fr ON fr.SubscriptionId=m.SubscriptionId
                       WHERE m.CustomerId=o.PartnerCustomerId AND m.SubscriptionType='Membership'
                         AND t.SlotDate BETWEEN fr.StartDate AND COALESCE(fr.ActualEndDate,fr.PlannedEndDate)) THEN 1 ELSE 0 END AS PartnerFrozen,
           CASE WHEN EXISTS(SELECT 1 FROM Subscriptions m WHERE m.CustomerId=o.PartnerCustomerId
                       AND m.SubscriptionType='Membership' AND m.Status IN ('Active','Frozen')
                       AND t.SlotDate BETWEEN m.StartDate AND m.EndDate) THEN 1 ELSE 0 END AS PartnerMembership
) m
CROSS APPLY (
    SELECT CASE WHEN (m.HolderFrozen=1 OR m.HolderMembership=0)
                     AND (sub.SubscriptionType<>'PTCouple' OR m.PartnerFrozen=1 OR m.PartnerMembership=0)
                THEN 1 ELSE 0 END AS Unavailable,
           CASE WHEN m.HolderFrozen=1 OR (sub.SubscriptionType='PTCouple' AND m.PartnerFrozen=1) THEN 1 ELSE 0 END AS HasFrozen,
           CASE WHEN EXISTS(SELECT 1 FROM CheckIns ci JOIN Subscriptions gym ON gym.SubscriptionId=ci.SubscriptionId
                       WHERE gym.SubscriptionType IN ('Membership','DayPass')
                         AND (gym.CustomerId=sub.CustomerId OR (sub.SubscriptionType='PTCouple' AND gym.CustomerId=o.PartnerCustomerId))
                         AND ci.CheckedInAt>=CAST(t.SlotDate AS DATETIME2)
                         AND ci.CheckedInAt<DATEADD(DAY,1,CAST(t.SlotDate AS DATETIME2))) THEN 1 ELSE 0 END AS HasCheckIn
) f
WHERE s.Status='Scheduled' AND t.SlotDate<@Today AND s.SessionId=@SessionId;
            IF @NewStatus IS NOT NULL
            BEGIN
                UPDATE Sessions
                SET Status=@NewStatus,MissedReason=@NewReason,IsAutoClosed=1,AttendanceMarkedAt=SYSDATETIME()
                WHERE SessionId=@SessionId AND Status='Scheduled';
                IF @@ROWCOUNT=1
                BEGIN
                    IF @NewStatus='Missed'
                        UPDATE TimeSlots SET Status='Free',HeldByOrderId=NULL,HoldExpiresAt=NULL
                        WHERE TimeSlotId=@TimeSlotId AND Status='Booked'
                          AND NOT EXISTS(SELECT 1 FROM Sessions WHERE TimeSlotId=@TimeSlotId AND Status IN ('Scheduled','CheckedIn'));
                    INSERT INTO AuditLogs(AccountId,ActingRole,Action,EntityName,EntityId,FieldChanged,OldValue,NewValue,Reason)
                    VALUES(NULL,NULL,N'JOB-04',N'Sessions',@SessionId,N'Status',N'Scheduled',@NewStatus,@NewReason);
                END;
            END;
            COMMIT TRANSACTION;
        END TRY
        BEGIN CATCH
            IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
            PRINT N'JOB-04 lỗi buổi '+CAST(@SessionId AS NVARCHAR(20))+N': '+ERROR_MESSAGE();
        END CATCH;
        FETCH NEXT FROM ClosingSessions INTO @SessionId;
    END;
    CLOSE ClosingSessions;
    DEALLOCATE ClosingSessions;
END;
GO

-- B1b. QUY ƯỚC ĐỀ XUẤT, SRS CHƯA QUY ĐỊNH: SingleSession → Used sau CheckedIn/Missed.
-- Câu này độc lập và mặc định tắt; JOB-04 Java chưa áp dụng quy ước này.
DECLARE @ApplyProposedSingleSessionUsed BIT=0;
IF @ApplyProposedSingleSessionUsed=1
    UPDATE sub SET Status='Used'
    FROM Subscriptions sub
    WHERE sub.SubscriptionType='SingleSession' AND sub.Status='Active'
      AND EXISTS(SELECT 1 FROM Sessions s WHERE s.SubscriptionId=sub.SubscriptionId AND s.Status IN ('CheckedIn','Missed'));
GO

-- B2. Báo cáo hoa hồng PT theo ngày buổi; @ToDate tính cả ngày cuối.
-- Số vắng chỉ NoCheckIn; Missed khác và hủy tách riêng. Hoa hồng dùng bản chốt.
DECLARE @FromDate DATE='2026-09-01', @ToDate DATE='2026-09-30', @PtEmployeeId INT=NULL;
SELECT e.EmployeeId,p.FullName,
       SUM(CASE WHEN s.Status='CheckedIn' THEN 1 ELSE 0 END) AS CheckedIn,
       SUM(CASE WHEN s.Status='Missed' AND s.MissedReason='NoCheckIn' THEN 1 ELSE 0 END) AS Absent,
       SUM(CASE WHEN s.Status='Missed' AND s.MissedReason IN ('MembershipFrozen','NoMembership') THEN 1 ELSE 0 END) AS OtherMissed,
       SUM(CASE WHEN s.Status='Cancelled' AND s.CancelType IN ('ByPt','ByCustomer') THEN 1 ELSE 0 END) AS Cancelled,
       CAST(100.0*SUM(CASE WHEN s.Status='Missed' AND s.MissedReason='NoCheckIn' THEN 1 ELSE 0 END)
           /NULLIF(SUM(CASE WHEN s.Status='CheckedIn' OR (s.Status='Missed' AND s.MissedReason='NoCheckIn') THEN 1 ELSE 0 END),0)
           AS DECIMAL(5,1)) AS AbsencePercent,
       SUM(CASE WHEN s.Status IN ('CheckedIn','Missed') OR (s.Status='Cancelled' AND s.CancelType IN ('ByPt','ByCustomer'))
                THEN CAST(s.CommissionAmount AS BIGINT) ELSE 0 END) AS CommissionAmount
FROM Employees e JOIN Persons p ON p.PersonId=e.PersonId
LEFT JOIN (Sessions s JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId)
    ON s.PtEmployeeId=e.EmployeeId AND t.SlotDate BETWEEN @FromDate AND @ToDate
WHERE e.Position='PT' AND (@PtEmployeeId IS NULL OR e.EmployeeId=@PtEmployeeId)
GROUP BY e.EmployeeId,p.FullName;
GO

-- B3. Điểm PT: mỗi khách chỉ tính phiếu mới nhất trong khoảng RatingMonth.
-- @FromMonth/@ToMonth là tháng đánh giá, ngày 1, tính cả tháng cuối.
-- PT 5 tháng 09/2026: 3 khách, 2 phiếu thấp; PT 6: 2 khách, Chưa đủ.
DECLARE @FromMonth DATE='2026-09-01', @ToMonth DATE='2026-09-01', @PtEmployeeId INT=NULL;
DECLARE @MinimumCustomers INT=(SELECT TRY_CONVERT(INT,ConfigValue) FROM SystemConfigs WHERE ConfigKey='P-22');
IF @MinimumCustomers IS NULL OR @MinimumCustomers<1 THROW 51001,N'Thiếu hoặc sai P-22.',1;
;WITH Latest AS (
    SELECT f.*,ROW_NUMBER() OVER(PARTITION BY CustomerId,RatedPtEmployeeId
        ORDER BY RatingMonth DESC,COALESCE(UpdatedAt,SubmittedAt) DESC,FeedbackId DESC) AS rn
    FROM Feedbacks f WHERE RatedPtEmployeeId IS NOT NULL AND RatingMonth BETWEEN @FromMonth AND @ToMonth
), Scores AS (
    SELECT RatedPtEmployeeId,COUNT(*) AS CustomerCount,
           CAST(ROUND(AVG(CAST(Rating AS DECIMAL(10,2))),1) AS DECIMAL(3,1)) AS AverageRating,
           SUM(CASE WHEN Rating IN (1,2) THEN 1 ELSE 0 END) AS LowRatingCount
    FROM Latest WHERE rn=1 GROUP BY RatedPtEmployeeId
)
SELECT e.EmployeeId,p.FullName,COALESCE(s.CustomerCount,0) AS CustomerCount,
       CASE WHEN s.CustomerCount>=@MinimumCustomers THEN s.AverageRating END AS AverageRating,
       COALESCE(s.LowRatingCount,0) AS LowRatingCount,
       CAST(CASE WHEN COALESCE(s.CustomerCount,0)<@MinimumCustomers THEN 1 ELSE 0 END AS BIT) AS IsInsufficient,
       CASE WHEN COALESCE(s.CustomerCount,0)<@MinimumCustomers THEN N'Chưa đủ' ELSE N'Đủ' END AS RatingVisibility
FROM Employees e JOIN Persons p ON p.PersonId=e.PersonId
LEFT JOIN Scores s ON s.RatedPtEmployeeId=e.EmployeeId
WHERE e.Position='PT' AND (@PtEmployeeId IS NULL OR e.EmployeeId=@PtEmployeeId);
GO

-- B4. Upsert đánh giá: @CustomerId là khách đã xác thực, không lấy tùy ý từ form.
-- @ExecuteRating=0: không ghi. =1: kiểm MSG21/MSG106 rồi ghi; trùng 2601/2627 → UPDATE.
DECLARE @ExecuteRating BIT=0, @CustomerId INT=NULL, @RatedPtEmployeeId INT=NULL, @Rating TINYINT=NULL;
DECLARE @Content NVARCHAR(1000)=NULL;
DECLARE @RatingMonth DATE=DATEFROMPARTS(YEAR(DATEADD(HOUR,7,SYSUTCDATETIME())),MONTH(DATEADD(HOUR,7,SYSUTCDATETIME())),1);
IF @ExecuteRating=1
BEGIN
    IF @RatedPtEmployeeId IS NOT NULL AND (@Rating IS NULL OR @Rating NOT BETWEEN 1 AND 5)
        THROW 51021,N'MSG21: Please select rating.',1;
    IF @RatedPtEmployeeId IS NULL AND @Rating IS NOT NULL
        THROW 51021,N'MSG21: Please select PT.',1;
    IF @Content IS NULL OR LEN(LTRIM(RTRIM(@Content))) NOT BETWEEN 10 AND 1000
        THROW 51022,N'Nội dung phải dài 10–1000 ký tự.',1;
    DECLARE @Eligible BIT=0, @Edited BIT=0;
    IF @RatedPtEmployeeId IS NOT NULL
    BEGIN
        IF EXISTS(SELECT 1 FROM Sessions s JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId
            JOIN Subscriptions sub ON sub.SubscriptionId=s.SubscriptionId JOIN Orders o ON o.OrderId=sub.OrderId
            JOIN Employees e ON e.EmployeeId=s.PtEmployeeId
            WHERE s.PtEmployeeId=@RatedPtEmployeeId AND e.Position='PT'
              AND (sub.CustomerId=@CustomerId OR (sub.SubscriptionType='PTCouple' AND o.PartnerCustomerId=@CustomerId))
              AND s.Status IN ('CheckedIn','Missed') AND t.SlotDate>=@RatingMonth AND t.SlotDate<DATEADD(MONTH,1,@RatingMonth))
            SET @Eligible=1;
        IF @Eligible=0 THROW 51106,N'MSG106: PT chưa có buổi CheckedIn/Missed của khách trong tháng.',1;
    END;
    BEGIN TRY
        BEGIN TRANSACTION;
        UPDATE Feedbacks SET Rating=@Rating,Content=LTRIM(RTRIM(@Content)),UpdatedAt=SYSDATETIME(),
            IsRead=0,ReadAt=NULL,ReadByEmployeeId=NULL
        WHERE CustomerId=@CustomerId AND RatedPtEmployeeId=@RatedPtEmployeeId AND RatingMonth=@RatingMonth;
        IF @@ROWCOUNT=1 SET @Edited=1;
        ELSE
            INSERT INTO Feedbacks(CustomerId,Content,RatedPtEmployeeId,Rating,RatingMonth)
            VALUES(@CustomerId,LTRIM(RTRIM(@Content)),@RatedPtEmployeeId,@Rating,CASE WHEN @RatedPtEmployeeId IS NULL THEN NULL ELSE @RatingMonth END);
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
        IF ERROR_NUMBER() IN (2601,2627) AND @RatedPtEmployeeId IS NOT NULL
        BEGIN
            BEGIN TRY
                BEGIN TRANSACTION;
                UPDATE Feedbacks SET Rating=@Rating,Content=LTRIM(RTRIM(@Content)),UpdatedAt=SYSDATETIME(),
                    IsRead=0,ReadAt=NULL,ReadByEmployeeId=NULL
                WHERE CustomerId=@CustomerId AND RatedPtEmployeeId=@RatedPtEmployeeId AND RatingMonth=@RatingMonth;
                IF @@ROWCOUNT<>1 THROW 51107,N'Không tìm thấy phiếu để cập nhật sau đua request.',1;
                SET @Edited=1;
                COMMIT TRANSACTION;
            END TRY
            BEGIN CATCH
                IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
                THROW;
            END CATCH;
        END
        ELSE THROW;
    END CATCH;
    SELECT CASE WHEN @Edited=1 THEN 'MSG107' ELSE 'MSG23' END AS MessageCode;
END;
GO

-- B5. Dropdown PT được chấm trong tháng VN; gói đôi xét cả holder/partner.
DECLARE @CustomerId INT=1;
DECLARE @RatingMonth DATE=DATEFROMPARTS(YEAR(DATEADD(HOUR,7,SYSUTCDATETIME())),MONTH(DATEADD(HOUR,7,SYSUTCDATETIME())),1);
SELECT DISTINCT e.EmployeeId,p.FullName
FROM Sessions s JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId
JOIN Subscriptions sub ON sub.SubscriptionId=s.SubscriptionId JOIN Orders o ON o.OrderId=sub.OrderId
JOIN Employees e ON e.EmployeeId=s.PtEmployeeId JOIN Persons p ON p.PersonId=e.PersonId
WHERE e.Position='PT' AND s.Status IN ('CheckedIn','Missed')
  AND (sub.CustomerId=@CustomerId OR (sub.SubscriptionType='PTCouple' AND o.PartnerCustomerId=@CustomerId))
  AND t.SlotDate>=@RatingMonth AND t.SlotDate<DATEADD(MONTH,1,@RatingMonth)
ORDER BY p.FullName;
GO

-- B6. Manager Feedback List: NULL bỏ qua bộ lọc; ngày lọc theo SubmittedAt.
-- @Keyword là văn bản literal; escape ký tự LIKE, tìm tiếng Việt không dấu.
DECLARE @IsRead BIT=NULL, @FromDate DATE=NULL, @ToDate DATE=NULL,
        @Keyword NVARCHAR(100)=NULL, @RatedPtEmployeeId INT=NULL, @Rating TINYINT=NULL;
DECLARE @Pattern NVARCHAR(406)=N'%'+REPLACE(REPLACE(REPLACE(REPLACE(@Keyword,N'~',N'~~'),N'%',N'~%'),N'_',N'~_'),N'[',N'~[')+N'%';
SELECT f.FeedbackId,f.CustomerId,cp.FullName AS CustomerName,f.Content,f.SubmittedAt,
       f.IsRead,f.ReadAt,f.ReadByEmployeeId,f.RatedPtEmployeeId,pp.FullName AS PtName,
       f.Rating,f.RatingMonth,f.UpdatedAt,CAST(CASE WHEN f.UpdatedAt IS NOT NULL THEN 1 ELSE 0 END AS BIT) AS Edited
FROM Feedbacks f JOIN Customers c ON c.CustomerId=f.CustomerId JOIN Persons cp ON cp.PersonId=c.PersonId
LEFT JOIN Employees pt ON pt.EmployeeId=f.RatedPtEmployeeId LEFT JOIN Persons pp ON pp.PersonId=pt.PersonId
WHERE (@IsRead IS NULL OR f.IsRead=@IsRead)
  AND (@FromDate IS NULL OR f.SubmittedAt>=CAST(@FromDate AS DATETIME2))
  AND (@ToDate IS NULL OR f.SubmittedAt<DATEADD(DAY,1,CAST(@ToDate AS DATETIME2)))
  AND (@Keyword IS NULL OR f.Content COLLATE Vietnamese_CI_AI LIKE @Pattern ESCAPE N'~'
       OR cp.FullName COLLATE Vietnamese_CI_AI LIKE @Pattern ESCAPE N'~'
       OR pp.FullName COLLATE Vietnamese_CI_AI LIKE @Pattern ESCAPE N'~')
  AND (@RatedPtEmployeeId IS NULL OR f.RatedPtEmployeeId=@RatedPtEmployeeId)
  AND (@Rating IS NULL OR f.Rating=@Rating)
ORDER BY f.SubmittedAt DESC,f.FeedbackId DESC;
GO

-- B7. Dashboard PT: ba nhóm không hở, ranh giới chính xác SlotDate+EndTime.
-- Hết giờ < @NowVN → Chờ chốt; bằng giờ kết thúc vẫn Dự kiến.
DECLARE @PtEmployeeId INT=5;
DECLARE @NowVN DATETIME2(0)=DATEADD(HOUR,7,SYSUTCDATETIME());
DECLARE @Month DATE=DATEFROMPARTS(YEAR(@NowVN),MONTH(@NowVN),1);
;WITH Monthly AS (
    SELECT s.SessionId,s.CommissionAmount,
           CASE WHEN s.Status IN ('CheckedIn','Missed') OR (s.Status='Cancelled' AND s.CancelType IN ('ByPt','ByCustomer')) THEN N'Đã chốt'
                WHEN DATEADD(SECOND,DATEDIFF(SECOND,CAST('00:00:00' AS TIME),t.EndTime),CAST(t.SlotDate AS DATETIME2))<@NowVN THEN N'Chờ hệ thống chốt'
                ELSE N'Dự kiến' END AS GroupName
    FROM Sessions s JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId
    WHERE s.PtEmployeeId=@PtEmployeeId AND t.SlotDate>=@Month AND t.SlotDate<DATEADD(MONTH,1,@Month)
      AND NOT(s.Status='Cancelled' AND s.CancelType IN ('GymClosed','PackageExpired'))
), Groups AS (
    SELECT N'Đã chốt' AS GroupName UNION ALL SELECT N'Chờ hệ thống chốt' UNION ALL SELECT N'Dự kiến'
)
SELECT g.GroupName,COUNT(m.SessionId) AS SessionCount,COALESCE(SUM(CAST(m.CommissionAmount AS BIGINT)),0) AS CommissionAmount
FROM Groups g LEFT JOIN Monthly m ON m.GroupName=g.GroupName GROUP BY g.GroupName;
-- B7b. Kiểm tổng ba nhóm = mọi buổi tháng trừ hai loại hủy hệ thống; mong đợi Difference=0.
;WITH Counts AS (
    SELECT SUM(CASE WHEN s.Status IN ('CheckedIn','Missed') OR (s.Status='Cancelled' AND s.CancelType IN ('ByPt','ByCustomer')) THEN 1 ELSE 0 END) AS Closed,
           SUM(CASE WHEN s.Status='Scheduled' AND DATEADD(SECOND,DATEDIFF(SECOND,CAST('00:00:00' AS TIME),t.EndTime),CAST(t.SlotDate AS DATETIME2))<@NowVN THEN 1 ELSE 0 END) AS Pending,
           SUM(CASE WHEN s.Status='Scheduled' AND DATEADD(SECOND,DATEDIFF(SECOND,CAST('00:00:00' AS TIME),t.EndTime),CAST(t.SlotDate AS DATETIME2))>=@NowVN THEN 1 ELSE 0 END) AS Expected,
           COUNT(*) AS Total
    FROM Sessions s JOIN TimeSlots t ON t.TimeSlotId=s.TimeSlotId
    WHERE s.PtEmployeeId=@PtEmployeeId AND t.SlotDate>=@Month AND t.SlotDate<DATEADD(MONTH,1,@Month)
      AND NOT(s.Status='Cancelled' AND s.CancelType IN ('GymClosed','PackageExpired'))
)
SELECT COALESCE(Closed,0) AS Closed,COALESCE(Pending,0) AS Pending,COALESCE(Expected,0) AS Expected,
       Total,COALESCE(Closed,0)+COALESCE(Pending,0)+COALESCE(Expected,0)-Total AS Difference FROM Counts;
GO

/* ============================================================================
   C. DỌN DẸP THAM SỐ CŨ — CHỈ CHẠY SAU KHI CODE KHÔNG CÒN ĐỌC P-07
   Seed v26 đã bỏ P-07. Khối này dành riêng cho database cũ.
   ============================================================================ */
DECLARE @CodeNoLongerReadsP07 BIT=0;
IF @CodeNoLongerReadsP07=1
    DELETE FROM SystemConfigs WHERE ConfigKey='P-07';
GO
