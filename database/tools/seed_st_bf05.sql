/* ============================================================================
   Dữ liệu chạy System Test BF-05 (SWP391_G5_ST_v1.1, sheet PT Package Life Cycle): các việc Lễ tân và PT
   đã làm ngày Thứ Sáu 06/11/2026 (ST-MBF05-01…05, 10, 11, 12), thông báo nhắc buổi đêm 19/10 (ST-MBF05-09)
   và khách đang bảo lưu theo ngày nạp (ST-MBF05-16).
   Mã đơn DH-2026-0181…0185. Buổi đã qua được chốt ở khối cuối file.
   Khối này do seeddata.py chép nguyên văn từ tools/seed_st_bf05.sql. ID tra theo mã khách / email / PT + ngày + giờ,
   không ghi cứng, nên seed đổi ID ở chỗ khác vẫn chạy; số buổi sai so với lúc test thì dừng nạp (THROW).
   ============================================================================ */

-- Người và gói dùng trong khối
DECLARE @cLinh INT = (SELECT CustomerId FROM Customers WHERE CustomerCode = 'KH-2013');
DECLARE @cKhoa INT = (SELECT CustomerId FROM Customers WHERE CustomerCode = 'KH-2007');
DECLARE @cTuan INT = (SELECT CustomerId FROM Customers WHERE CustomerCode = 'KH-2008');
DECLARE @cMichael INT = (SELECT CustomerId FROM Customers WHERE CustomerCode = 'KH-1011');
DECLARE @cHoSon INT = (SELECT CustomerId FROM Customers WHERE CustomerCode = 'KH-2015');
DECLARE @cHuynhSon INT = (SELECT CustomerId FROM Customers WHERE CustomerCode = 'KH-2019');
DECLARE @cVoTam INT = (SELECT CustomerId FROM Customers WHERE CustomerCode = 'KH-2029');
DECLARE @cMai INT = (SELECT CustomerId FROM Customers WHERE CustomerCode = 'KH-1006');
DECLARE @cAn INT = (SELECT CustomerId FROM Customers WHERE CustomerCode = 'KH-1001');

DECLARE @ptSon INT = 5, @ptTrang INT = 6, @ptDavid INT = 7, @recTam INT = 3;
DECLARE @pkPt1 INT = 8, @pkCouple1 INT = 11;

DECLARE @aLinh INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'linh.huynh213@gmail.com');
DECLARE @aKhoa INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'khoa.ly207@gmail.com');
DECLARE @aTuan INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'tuan.vo208@gmail.com');
DECLARE @aMichael INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'michael.johnson@outlook.com');
DECLARE @aHoSon INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'son.ho215@gmail.com');
DECLARE @aHuynhSon INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'son.huynh219@gmail.com');
DECLARE @aMai INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'mai.do97@gmail.com');
DECLARE @aAn INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'an.nguyen95@gmail.com');
DECLARE @aSon INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'son.dinh@gms.vn');
DECLARE @aTrang INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'trang.nguyen@gms.vn');
DECLARE @aDavid INT = (SELECT a.AccountId FROM Accounts a JOIN Persons p ON p.PersonId = a.PersonId WHERE p.Email = 'david.miller@gms.vn');

IF @cLinh IS NULL OR @cKhoa IS NULL OR @cTuan IS NULL OR @cMichael IS NULL OR @cHoSon IS NULL OR @cHuynhSon IS NULL
   OR @cVoTam IS NULL OR @cMai IS NULL OR @cAn IS NULL OR @aLinh IS NULL OR @aKhoa IS NULL OR @aTuan IS NULL
   OR @aMichael IS NULL OR @aHoSon IS NULL OR @aHuynhSon IS NULL OR @aMai IS NULL OR @aAn IS NULL
   OR @aSon IS NULL OR @aTrang IS NULL OR @aDavid IS NULL
    THROW 50101, N'ST BF-05: thiếu khách hoặc tài khoản dùng trong khối', 1;

-- Ca sáng 06/11 của Lễ tân Tâm (đã chốt): tiền mặt = 13.200.000 + 3 × 8.800.000
INSERT INTO Shifts (WorkDate, ShiftType, OpenedByEmployeeId, ClosedByEmployeeId, Status, OpenedAt, ClosedAt, ExpectedCash, CountedCash, VarianceReason)
VALUES ('2026-11-06', 'Morning', @recTam, @recTam, 'Closed', '2026-11-06 05:30:00', '2026-11-06 13:00:00', 39600000, 39600000, NULL);
DECLARE @shift0611 INT = SCOPE_IDENTITY();

-- 5 đơn gói PT bán tại quầy ngày 06/11
DECLARE @bf05Orders TABLE (OrderCode VARCHAR(20), Holder INT, Partner INT, PackageId INT, CreatedAt DATETIME2(0), Method VARCHAR(15), GatewayRef VARCHAR(50));
INSERT INTO @bf05Orders VALUES
 ('DH-2026-0181', @cLinh,     NULL,  @pkPt1,     '2026-11-06 08:10:00', 'Transfer', 'FT26110001'),  -- ST-03
 ('DH-2026-0182', @cKhoa,     @cTuan, @pkCouple1, '2026-11-06 08:40:00', 'Cash',     NULL),          -- ST-02
 ('DH-2026-0183', @cMichael,  NULL,  @pkPt1,     '2026-11-06 09:10:00', 'Cash',     NULL),          -- ST-01
 ('DH-2026-0184', @cHoSon,    NULL,  @pkPt1,     '2026-11-06 09:40:00', 'Cash',     NULL),          -- ST-04
 ('DH-2026-0185', @cHuynhSon, NULL,  @pkPt1,     '2026-11-06 10:10:00', 'Cash',     NULL);          -- ST-05

INSERT INTO Orders (OrderCode, HolderCustomerId, PartnerCustomerId, PackageId, VoucherId, CreatedByEmployeeId, ShiftId, Channel,
                    PackageNameSnapshot, PackageDescriptionSnapshot, PackageBenefitsSnapshot, UnitPriceSnapshot, DiscountAmount,
                    VatRateSnapshot, VatAmount, Status, PaymentDueAt, CreatedAt, PaidAt, CancelType, CancelledAt, CancelReason)
SELECT b.OrderCode, b.Holder, b.Partner, b.PackageId, NULL, @recTam, @shift0611, 'Counter',
       pk.PackageName, pk.Description, pk.Benefits, pk.Price, 0,
       10, pk.Price * 10 / 110, 'Paid', NULL, b.CreatedAt, DATEADD(MINUTE, 2, b.CreatedAt), NULL, NULL, NULL
FROM @bf05Orders b JOIN Packages pk ON pk.PackageId = b.PackageId
ORDER BY b.OrderCode;

INSERT INTO Transactions (OrderId, Amount, PaymentMethod, GatewayReference, ReceivedByEmployeeId, Status, FailureReason, NeedsRefund,
                          RefundedAt, RefundedByEmployeeId, RefundNote, CreatedAt, ExpiresAt, CompletedAt)
SELECT o.OrderId, o.UnitPriceSnapshot, b.Method, b.GatewayRef, @recTam, 'Success', NULL, 0,
       NULL, NULL, NULL, o.PaidAt, NULL, o.PaidAt
FROM @bf05Orders b JOIN Orders o ON o.OrderCode = b.OrderCode
ORDER BY o.OrderId;

DECLARE @o181 INT = (SELECT OrderId FROM Orders WHERE OrderCode = 'DH-2026-0181');
DECLARE @o182 INT = (SELECT OrderId FROM Orders WHERE OrderCode = 'DH-2026-0182');
DECLARE @o183 INT = (SELECT OrderId FROM Orders WHERE OrderCode = 'DH-2026-0183');
DECLARE @o184 INT = (SELECT OrderId FROM Orders WHERE OrderCode = 'DH-2026-0184');
DECLARE @o185 INT = (SELECT OrderId FROM Orders WHERE OrderCode = 'DH-2026-0185');

INSERT INTO Subscriptions (OrderId, CustomerId, SubscriptionType, PtEmployeeId, StartDate, EndDate, FixedStartTime, GeneratedSessionCount, FreezeUsed, MaxFreezesSnapshot, Status) VALUES
 (@o181, @cLinh,     'PTSingle', @ptDavid, '2026-11-09', '2026-12-09', '10:00:00', 23, 0, NULL, 'Active'),
 (@o182, @cKhoa,     'PTCouple', @ptTrang, '2026-11-06', '2026-12-06', '16:00:00', 21, 0, NULL, 'Active'),
 (@o183, @cMichael,  'PTSingle', @ptSon,   '2026-11-06', '2026-12-06', '20:00:00', 21, 0, NULL, 'Active'),
 (@o184, @cHoSon,    'PTSingle', @ptSon,   '2026-11-16', '2026-12-16', '13:00:00', 23, 0, NULL, 'Active'),
 (@o185, @cHuynhSon, 'PTSingle', @ptTrang, '2026-11-16', '2026-12-16', '17:30:00', 22, 0, NULL, 'Active');

DECLARE @s181 INT = (SELECT SubscriptionId FROM Subscriptions WHERE OrderId = @o181);
DECLARE @s182 INT = (SELECT SubscriptionId FROM Subscriptions WHERE OrderId = @o182);
DECLARE @s183 INT = (SELECT SubscriptionId FROM Subscriptions WHERE OrderId = @o183);
DECLARE @s184 INT = (SELECT SubscriptionId FROM Subscriptions WHERE OrderId = @o184);
DECLARE @s185 INT = (SELECT SubscriptionId FROM Subscriptions WHERE OrderId = @o185);

-- ST-04: 13:00 PT Sơn chưa mở → hệ thống tạo khung khi giữ lịch (Thứ Hai–Thứ Sáu, 16/11–16/12)
;WITH d AS (
    SELECT CAST('2026-11-16' AS DATE) AS SlotDate
    UNION ALL
    SELECT DATEADD(DAY, 1, SlotDate) FROM d WHERE SlotDate < '2026-12-16'
)
INSERT INTO TimeSlots (PtEmployeeId, SlotDate, StartTime, EndTime, Status, HeldByOrderId, HoldExpiresAt, CreatedAt)
SELECT @ptSon, SlotDate, '13:00:00', '14:30:00', 'Free', NULL, NULL, '2026-11-06 09:40:00'
FROM d WHERE DATEDIFF(DAY, '19000101', SlotDate) % 7 < 5;   -- 01/01/1900 là Thứ Hai → 0..4 = Thứ Hai..Thứ Sáu

-- Khung giờ của từng gói: khung Free của PT, Thứ Hai–Thứ Sáu trong kỳ, đúng giờ cố định
DECLARE @bf05Slots TABLE (SubscriptionId INT, TimeSlotId INT, PtEmployeeId INT, SessionValue INT, CommissionAmount INT);
INSERT INTO @bf05Slots
SELECT s.SubscriptionId, ts.TimeSlotId, s.PtEmployeeId, v.SessionValue, v.CommissionAmount
FROM Subscriptions s
JOIN (VALUES (@s181, 382609, 229565),   -- 8.800.000 / 23, David 60%
             (@s182, 628571, 408571),   -- 13.200.000 / 21, Trang 65%
             (@s183, 419048, 293334),   -- 8.800.000 / 21, Sơn 70%
             (@s184, 382609, 267826),   -- 8.800.000 / 23, Sơn 70%
             (@s185, 400000, 260000))   -- 8.800.000 / 22, Trang 65%
     v (SubscriptionId, SessionValue, CommissionAmount) ON v.SubscriptionId = s.SubscriptionId
JOIN TimeSlots ts ON ts.PtEmployeeId = s.PtEmployeeId AND ts.Status = 'Free'
                 AND ts.SlotDate BETWEEN s.StartDate AND s.EndDate
                 AND DATEDIFF(DAY, '19000101', ts.SlotDate) % 7 < 5
                 AND (   (s.SubscriptionId <> @s185 AND ts.StartTime = s.FixedStartTime)
                      -- ST-05: tuần 1 sửa riêng: Thứ Tư 18/11 tập 08:00, Thứ Năm 19/11 không tập
                      OR (s.SubscriptionId = @s185 AND ts.StartTime = s.FixedStartTime AND ts.SlotDate NOT IN ('2026-11-18', '2026-11-19'))
                      OR (s.SubscriptionId = @s185 AND ts.StartTime = '08:00:00' AND ts.SlotDate = '2026-11-18'));

IF EXISTS (SELECT 1 FROM Subscriptions s
           WHERE s.SubscriptionId IN (@s181, @s182, @s183, @s184, @s185)
             AND s.GeneratedSessionCount <> (SELECT COUNT(*) FROM @bf05Slots b WHERE b.SubscriptionId = s.SubscriptionId))
    THROW 50102, N'ST BF-05: số khung trống không khớp số buổi lúc bán (23, 21, 21, 23, 22)', 1;

UPDATE TimeSlots SET Status = 'Booked' WHERE TimeSlotId IN (SELECT TimeSlotId FROM @bf05Slots);
INSERT INTO Sessions (SubscriptionId, TimeSlotId, PtEmployeeId, Status, SessionValue, CommissionAmount)
SELECT SubscriptionId, TimeSlotId, PtEmployeeId, 'Scheduled', SessionValue, CommissionAmount FROM @bf05Slots ORDER BY SubscriptionId, TimeSlotId;

-- ST-01, ST-02: Michael và Tuấn check-in ngày 06/11 (bằng gói thành viên đang dùng)
INSERT INTO CheckIns (SubscriptionId, VerifiedByEmployeeId, CheckedInAt, PhotoVerified)
SELECT SubscriptionId, @recTam, x.At, 1
FROM (VALUES (@cMichael, CAST('2026-11-06 11:50:00' AS DATETIME2(0))), (@cTuan, CAST('2026-11-06 12:20:00' AS DATETIME2(0)))) x (CustomerId, At)
CROSS APPLY (SELECT TOP 1 s.SubscriptionId FROM Subscriptions s
             WHERE s.CustomerId = x.CustomerId AND s.SubscriptionType = 'Membership'
               AND '2026-11-06' BETWEEN s.StartDate AND s.EndDate ORDER BY s.SubscriptionId) m;

-- ST-16: khách Võ Minh Tâm (KH-2029) đang bảo lưu gói thành viên (lập phiếu @Today − 4, 10:00). Kỳ bảo lưu tính theo NGÀY NẠP
-- (@Today − 3 … @Today + 26, 30 ngày, đúng giới hạn P-02 = 1 tháng) nên nạp ngày nào khách cũng đang bảo lưu đúng hôm đó.
DECLARE @subVoTam INT = (SELECT TOP 1 SubscriptionId FROM Subscriptions
                         WHERE CustomerId = @cVoTam AND SubscriptionType = 'Membership' AND Status = 'Active' ORDER BY EndDate DESC);
INSERT INTO FreezeRecords (SubscriptionId, HandledByEmployeeId, StartDate, PlannedEndDate, ActualEndDate, Reason, CreatedAt)
VALUES (@subVoTam, @recTam, DATEADD(DAY, -3, @Today), DATEADD(DAY, 26, @Today), NULL, N'Khách về quê chăm người nhà',
        DATEADD(MINUTE, -5160, CAST(@Today AS DATETIME2(0))));
UPDATE Subscriptions SET Status = 'Frozen', FreezeUsed = 1 WHERE SubscriptionId = @subVoTam;
UPDATE Customers SET Status = 'Frozen' WHERE CustomerId = @cVoTam;

-- Buổi của Mai (gói PT 2 Tháng từ 09/11) và của An (gói PT từ 26/10) mà PT Sơn xử lý ngày 06/11
DECLARE @sessMai16 INT, @slotMai16 INT, @sessMai17 INT, @slotMai17 INT, @sessAn16 INT, @slotAn16 INT;
SELECT @sessMai16 = se.SessionId, @slotMai16 = se.TimeSlotId FROM Sessions se JOIN TimeSlots ts ON ts.TimeSlotId = se.TimeSlotId
JOIN Subscriptions s ON s.SubscriptionId = se.SubscriptionId
WHERE s.CustomerId = @cMai AND s.SubscriptionType = 'PTSingle' AND s.StartDate = '2026-11-09' AND ts.SlotDate = '2026-11-16' AND ts.StartTime = '18:00:00';
SELECT @sessMai17 = se.SessionId, @slotMai17 = se.TimeSlotId FROM Sessions se JOIN TimeSlots ts ON ts.TimeSlotId = se.TimeSlotId
JOIN Subscriptions s ON s.SubscriptionId = se.SubscriptionId
WHERE s.CustomerId = @cMai AND s.SubscriptionType = 'PTSingle' AND s.StartDate = '2026-11-09' AND ts.SlotDate = '2026-11-17' AND ts.StartTime = '18:00:00';
SELECT @sessAn16 = se.SessionId, @slotAn16 = se.TimeSlotId FROM Sessions se JOIN TimeSlots ts ON ts.TimeSlotId = se.TimeSlotId
JOIN Subscriptions s ON s.SubscriptionId = se.SubscriptionId
WHERE s.CustomerId = @cAn AND s.SubscriptionType = 'PTSingle' AND s.StartDate = '2026-10-26' AND ts.SlotDate = '2026-11-16' AND ts.StartTime = '07:00:00';
DECLARE @slotSon1711 INT = (SELECT TimeSlotId FROM TimeSlots WHERE PtEmployeeId = @ptSon AND SlotDate = '2026-11-17' AND StartTime = '11:00:00' AND Status = 'Free');
DECLARE @slotDavid1611 INT = (SELECT TimeSlotId FROM TimeSlots WHERE PtEmployeeId = @ptDavid AND SlotDate = '2026-11-16' AND StartTime = '07:00:00' AND Status = 'Free');
IF @sessMai16 IS NULL OR @sessMai17 IS NULL OR @sessAn16 IS NULL OR @slotSon1711 IS NULL OR @slotDavid1611 IS NULL
    THROW 50103, N'ST BF-05: thiếu buổi hoặc khung trống cho ST-10, 11, 12', 1;

-- ST-10: PT Sơn dời buổi 16/11 18:00 của Mai sang 17/11 11:00
UPDATE Sessions SET TimeSlotId = @slotSon1711 WHERE SessionId = @sessMai16;
UPDATE TimeSlots SET Status = 'Booked' WHERE TimeSlotId = @slotSon1711;
UPDATE TimeSlots SET Status = 'Free' WHERE TimeSlotId = @slotMai16;
-- ST-11: PT Sơn chuyển buổi 16/11 07:00 của An cho PT David (hoa hồng theo tỷ lệ của David)
UPDATE Sessions SET TimeSlotId = @slotDavid1611, PtEmployeeId = @ptDavid, CommissionAmount = 220000 WHERE SessionId = @sessAn16;
UPDATE TimeSlots SET Status = 'Booked' WHERE TimeSlotId = @slotDavid1611;
UPDATE TimeSlots SET Status = 'Free' WHERE TimeSlotId = @slotAn16;
-- ST-12: PT Sơn hủy buổi 17/11 18:00 của Mai, khách đồng ý nhận lại tiền mặt
UPDATE Sessions SET Status = 'Cancelled', CancelType = 'ByPt', CancelReason = N'PT bị ốm, đã hoàn tiền mặt cho khách',
                    CancelledAt = '2026-11-06 14:20:00', CancelledByAccountId = @aSon, RefundAgreed = 1
WHERE SessionId = @sessMai17;
UPDATE TimeSlots SET Status = 'Free' WHERE TimeSlotId = @slotMai17;

INSERT INTO SessionMoveLogs (SessionId, MoveType, OldTimeSlotId, NewTimeSlotId, OldPtEmployeeId, NewPtEmployeeId, RequestedByAccountId, Reason, MovedAt) VALUES
 (@sessMai16, 'PtReschedule', @slotMai16, @slotSon1711, @ptSon, @ptSon, @aSon, N'PT bận họp buổi tối, dời sang buổi sáng', '2026-11-06 14:00:00'),
 (@sessAn16,  'PtTransfer',   @slotAn16,  @slotDavid1611, @ptSon, @ptDavid, @aSon, NULL, '2026-11-06 14:10:00');

-- Thông báo gửi ra từ các việc trên (mỗi thông báo một người nhận, gói đôi gửi cả hai khách)
DECLARE @bf05Notes TABLE (Seq INT, Title NVARCHAR(200), Content NVARCHAR(1000), SentAt DATETIME2(0), Acc1 INT, Acc2 INT);
INSERT INTO @bf05Notes VALUES
 (1,  N'Gói PT mới đã kích hoạt', N'Gói PT 1 Tháng với David Miller: 23 buổi, 09/11/2026 – 09/12/2026. Mã đơn DH-2026-0181.', '2026-11-06 08:12:00', @aLinh, NULL),
 (2,  N'Bạn có khách gói PT mới', N'Huỳnh Thị Linh mua Gói PT 1 Tháng: 23 buổi, 09/11/2026 – 09/12/2026. Xem trên Lịch dạy.', '2026-11-06 08:12:00', @aDavid, NULL),
 (3,  N'Gói PT mới đã kích hoạt', N'Gói PT Couple 1 Tháng với Nguyễn Thị Kiều Trang: 21 buổi, 06/11/2026 – 06/12/2026. Mã đơn DH-2026-0182.', '2026-11-06 08:42:00', @aKhoa, @aTuan),
 (4,  N'Bạn có khách gói PT mới', N'Lý Hữu Khoa mua Gói PT Couple 1 Tháng: 21 buổi, 06/11/2026 – 06/12/2026. Xem trên Lịch dạy.', '2026-11-06 08:42:00', @aTrang, NULL),
 (5,  N'Gói PT mới đã kích hoạt', N'Gói PT 1 Tháng với Đinh Công Sơn: 21 buổi, 06/11/2026 – 06/12/2026. Mã đơn DH-2026-0183.', '2026-11-06 09:12:00', @aMichael, NULL),
 (6,  N'Bạn có khách gói PT mới', N'Michael Johnson mua Gói PT 1 Tháng: 21 buổi, 06/11/2026 – 06/12/2026. Xem trên Lịch dạy.', '2026-11-06 09:12:00', @aSon, NULL),
 (7,  N'Gói PT mới đã kích hoạt', N'Gói PT 1 Tháng với Đinh Công Sơn: 23 buổi, 16/11/2026 – 16/12/2026. Mã đơn DH-2026-0184.', '2026-11-06 09:42:00', @aHoSon, NULL),
 (8,  N'Bạn có khách gói PT mới', N'Hồ Đức Sơn mua Gói PT 1 Tháng: 23 buổi, 16/11/2026 – 16/12/2026. Xem trên Lịch dạy.', '2026-11-06 09:42:00', @aSon, NULL),
 (9,  N'Gói PT mới đã kích hoạt', N'Gói PT 1 Tháng với Nguyễn Thị Kiều Trang: 22 buổi, 16/11/2026 – 16/12/2026. Mã đơn DH-2026-0185.', '2026-11-06 10:12:00', @aHuynhSon, NULL),
 (10, N'Bạn có khách gói PT mới', N'Huỳnh Xuân Sơn mua Gói PT 1 Tháng: 22 buổi, 16/11/2026 – 16/12/2026. Xem trên Lịch dạy.', '2026-11-06 10:12:00', @aTrang, NULL),
 (11, N'PT dời buổi tập', N'Buổi PT 16/11/2026 18:00–19:30 được Đinh Công Sơn dời sang Thứ 3 17/11/2026 11:00–12:30. Ghi chú: PT bận họp buổi tối, dời sang buổi sáng', '2026-11-06 14:00:00', @aMai, NULL),
 (12, N'Đổi PT dạy buổi tập', N'Buổi PT 16/11/2026 07:00–08:30 sẽ do David Miller dạy thay Đinh Công Sơn. Giờ tập giữ nguyên.', '2026-11-06 14:10:00', @aAn, NULL),
 (13, N'Bạn nhận dạy thay 1 buổi', N'Đinh Công Sơn chuyển cho bạn buổi 16/11/2026 07:00–08:30 với khách Nguyễn Văn An.', '2026-11-06 14:10:00', @aDavid, NULL),
 (14, N'PT hủy buổi tập', N'Buổi PT 17/11/2026 18:00–19:30 đã bị Đinh Công Sơn hủy. Lý do: PT bị ốm, đã hoàn tiền mặt cho khách. Khoản hoàn tiền mặt thực hiện như bạn đã thỏa thuận với PT.', '2026-11-06 14:20:00', @aMai, NULL),
 -- ST-09: JOB-08 chạy 00:05 ngày 20/10 nhắc buổi 21/10 vì hôm đó Mai chưa có gói thành viên
 (15, N'Nhắc buổi PT ngày 21/10/2026', N'Ngày mai 21/10/2026 lúc 18:00 bạn có buổi PT với Đinh Công Sơn nhưng chưa có membership hoặc vé ngày hợp lệ. Hãy mua gói thành viên hoặc vé ngày trước giờ tập để buổi không bị tính vắng.', '2026-10-20 00:05:00', @aMai, NULL);

DECLARE @seq INT = 1, @noteId INT;
WHILE @seq <= 15
BEGIN
    INSERT INTO Notifications (SentByEmployeeId, Title, Content, Audience, ScheduledAt, SentAt, CreatedAt)
    SELECT NULL, Title, Content, 'Individual', NULL, SentAt, SentAt FROM @bf05Notes WHERE Seq = @seq;
    SET @noteId = SCOPE_IDENTITY();
    INSERT INTO NotificationRecipients (NotificationId, AccountId, ReadAt)
    SELECT @noteId, a.AccountId, NULL
    FROM @bf05Notes n CROSS APPLY (VALUES (n.Acc1), (n.Acc2)) a (AccountId)
    WHERE n.Seq = @seq AND a.AccountId IS NOT NULL;
    SET @seq = @seq + 1;
END;
