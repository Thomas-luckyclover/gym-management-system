/*
 * Đối chiếu hoa hồng tháng của một PT, không cập nhật dữ liệu ứng dụng.
 * Chọn đúng database GMS trong SSMS và sửa @PtEmployeeId trước khi chạy.
 * Tiền lấy từ Sessions.CommissionAmount đã lưu, không nhân lại CommissionRate hiện tại.
 * Tháng tính theo TimeSlots.SlotDate; PT hưởng theo Sessions.PtEmployeeId.
 */
SET NOCOUNT ON;

DECLARE @PtEmployeeId INT = 5;
DECLARE @Now DATETIME2 = CAST(SWITCHOFFSET(SYSDATETIMEOFFSET(), '+07:00') AS DATETIME2);
DECLARE @MonthStart DATE = DATEFROMPARTS(YEAR(@Now), MONTH(@Now), 1);
DECLARE @NextMonthStart DATE = DATEADD(MONTH, 1, @MonthStart);

-- B1: lưu kết quả đọc vào biến bảng dùng riêng trong lượt chạy, không tạo/sửa bảng ứng dụng.
DECLARE @SessionMoney TABLE (
    SessionId INT,
    SlotDate DATE,
    StartTime TIME,
    EndTime TIME,
    Status VARCHAR(10),
    MissedReason VARCHAR(20),
    CancelType VARCHAR(20),
    SessionValue BIGINT,
    StoredCommissionAmount BIGINT,
    MoneyGroup VARCHAR(20),
    CountedCommissionAmount BIGINT
);

-- B2: mỗi SessionId chỉ có một dòng vì TimeSlotId nối tới khóa chính TimeSlots.
WITH MonthlySessions AS (
    SELECT s.SessionId, t.SlotDate, t.StartTime, t.EndTime,
           s.Status, s.MissedReason, s.CancelType,
           CAST(s.SessionValue AS BIGINT) AS SessionValue,
           CAST(s.CommissionAmount AS BIGINT) AS StoredCommissionAmount,
           DATEADD(SECOND,
               DATEDIFF(SECOND, CAST('00:00:00' AS TIME), t.EndTime),
               CAST(t.SlotDate AS DATETIME2)) AS EndsAt
    FROM Sessions s
    JOIN TimeSlots t ON t.TimeSlotId = s.TimeSlotId
    WHERE s.PtEmployeeId = @PtEmployeeId
      AND t.SlotDate >= @MonthStart
      AND t.SlotDate < @NextMonthStart
),
ClassifiedSessions AS (
    SELECT *,
           CASE
               WHEN Status IN ('CheckedIn', 'Missed')
                 OR (Status = 'Cancelled' AND CancelType IN ('ByPt', 'ByCustomer'))
                   THEN 'Finalized'
               WHEN Status = 'Scheduled' AND EndsAt <= @Now THEN 'Pending'
               WHEN Status = 'Scheduled' AND EndsAt > @Now THEN 'Projected'
               ELSE 'Excluded'
           END AS MoneyGroup
    FROM MonthlySessions
)
INSERT INTO @SessionMoney
SELECT SessionId, SlotDate, StartTime, EndTime, Status, MissedReason, CancelType,
       SessionValue, StoredCommissionAmount, MoneyGroup,
       CASE WHEN MoneyGroup = 'Excluded' THEN CAST(0 AS BIGINT)
            ELSE StoredCommissionAmount END
FROM ClassifiedSessions;

-- B3: bảng chi tiết để biết từng đồng nằm ở buổi nào và nhóm nào.
-- Excluded giữ tiền gốc để đối chiếu, nhưng CountedCommissionAmount phải bằng 0.
SELECT SessionId, SlotDate, StartTime, EndTime, Status, MissedReason, CancelType,
       SessionValue, StoredCommissionAmount, MoneyGroup, CountedCommissionAmount
FROM @SessionMoney
ORDER BY SlotDate, StartTime, SessionId;

-- B4: tổng hợp ba nhóm; thêm Excluded để thấy tiền đã loại, không cộng vào thu nhập.
-- Dùng BIGINT trước SUM để tránh tràn INT khi nhiều buổi có số tiền lớn.
SELECT g.MoneyGroup, COUNT(m.SessionId) AS SessionCount,
       COALESCE(SUM(m.StoredCommissionAmount), 0) AS StoredCommissionTotal,
       COALESCE(SUM(m.CountedCommissionAmount), 0) AS CountedCommissionTotal
FROM (VALUES ('Finalized'), ('Pending'), ('Projected'), ('Excluded')) g(MoneyGroup)
LEFT JOIN @SessionMoney m ON m.MoneyGroup = g.MoneyGroup
GROUP BY g.MoneyGroup
ORDER BY CASE g.MoneyGroup WHEN 'Finalized' THEN 1 WHEN 'Pending' THEN 2
                          WHEN 'Projected' THEN 3 ELSE 4 END;

-- B5: các dòng sau phải rỗng. Có dòng nghĩa là cần kiểm tra dữ liệu trước khi dùng thống kê.
SELECT SessionId, COUNT(*) AS DuplicateCount
FROM @SessionMoney
GROUP BY SessionId
HAVING COUNT(*) > 1;

SELECT SessionId, SessionValue, StoredCommissionAmount
FROM @SessionMoney
WHERE StoredCommissionAmount < 0 OR StoredCommissionAmount > SessionValue;
