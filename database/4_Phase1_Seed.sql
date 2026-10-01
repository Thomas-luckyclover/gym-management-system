/* ============================================================================
   GMS — Gym Management System
   FILE   : 4_Phase1_Seed.sql
   MỤC ĐÍCH: Bổ sung dữ liệu test cho PHASE 1 (Đăng nhập, Đăng ký, Quên mật khẩu,
             Cài đặt tài khoản, Trang chủ, Blog) — mỗi dòng phục vụ 1 tình huống
             test cụ thể, tra ở 02_Phase1_Test_Scenarios.md.
   THỨ TỰ : Chạy SAU 1_Init_Schema.sql → 2_Seed_Data.sql → 3_Dev_Passwords.sql.
   ID     : Mọi dòng thêm mới dùng ID từ 101 trở lên, không đụng dữ liệu gốc.
   CHẠY LẠI: Được. Đầu file xóa các dòng ID ≥ 101 rồi nạp lại, và các mốc thời gian
             nhạy cảm (khóa tạm, mã xác thực) tính theo GIỜ LÚC CHẠY SCRIPT —
             muốn test lại tình huống "đang khóa 5 phút" thì chạy lại file này.
             (Nếu app đã ghi AuditLog/NotificationRecipients trỏ tới tài khoản ≥ 101
              thì phải xóa các dòng đó trước.)
   KHÔNG ĐỔI SCHEMA. Chỉ dùng cho database dev/local.
   Mật khẩu mọi tài khoản mới: Gms@2026 (cùng hash với 3_Dev_Passwords.sql).
   YÊU CẦU schema cài mới từ 1_Init_Schema.sql bản 24/09/2026 trở đi.
   ============================================================================ */

USE GMS;
GO

SET NOCOUNT ON;
GO

/* ============================================================================
   0. DỌN DỮ LIỆU PHASE 1 CŨ (con trước, cha sau)
   ============================================================================ */
DELETE FROM BlogPosts         WHERE BlogPostId         >= 101;
DELETE FROM Transactions      WHERE OrderId            >= 101;
DELETE FROM Subscriptions     WHERE OrderId            >= 101;
DELETE FROM Orders            WHERE OrderId            >= 101;
DELETE FROM VerificationCodes WHERE VerificationCodeId >= 101;
DELETE FROM Accounts          WHERE AccountId          >= 101;
DELETE FROM Customers         WHERE CustomerId         >= 101;
DELETE FROM Persons           WHERE PersonId           >= 101;
GO

/* ============================================================================
   1. SỬA DỮ LIỆU GỐC LỆCH LUẬT (docs/notes.md mục D3) — chỉ đổi dữ liệu, không đổi schema
   Khách 9, 10, 14 chưa có gói nào đã thanh toán → trạng thái đúng là New (DBR-34, DBR-48).
   ============================================================================ */
UPDATE Customers SET Status = 'New' WHERE CustomerId IN (9, 10, 14) AND Status = 'Active';
GO

/* ============================================================================
   2. PERSONS — 12 người phục vụ các nhánh xác thực
   ============================================================================ */
SET IDENTITY_INSERT Persons ON;
INSERT INTO Persons (PersonId, FullName, DateOfBirth, Phone, Email, Avatar, IsReleased, CreatedAt) VALUES
 -- Đăng nhập
 (101, N'Lê Hoàng Phúc'   , '1994-04-18', '0935000101', 'phuc.le@gmail.com'    , NULL                        , 0, '2026-08-01 10:00:00'), -- tài khoản bị Admin khóa
 (102, N'Võ Thị Kim'      , '1999-10-02', '0935000102', 'kim.vo@gmail.com'     , '/uploads/avatar/p-102.jpg' , 0, '2026-08-05 10:00:00'), -- đang khóa tạm (tính theo giờ chạy script), có ảnh đại diện
 (107, N'Mai Thanh Tùng'  , '1990-12-12', '0935000107', 'tung.mai@gmail.com'   , NULL                        , 1, '2026-05-01 10:00:00'), -- hồ sơ đã xóa → SĐT/email đã nhả
 -- Hồ sơ phụ thuộc (con của khách Nguyễn Văn An — Person 1, SĐT 0901234501, có tài khoản)
 (103, N'Nguyễn Gia Huy'  , '2018-05-10', NULL        , NULL                   , NULL                        , 0, '2026-09-01 13:50:00'), -- 8 tuổi (< P-05), có gói tập
 (104, N'Nguyễn Minh Châu', '2012-02-14', NULL        , NULL                   , NULL                        , 0, '2026-09-01 13:52:00'), -- 14 tuổi, chưa có gói; An là giám hộ thứ 2
 (106, N'Kiều Bảo Ngọc'   , '2019-08-20', NULL        , NULL                   , NULL                        , 0, '2026-09-10 09:00:00'), -- số giám hộ KHÔNG thuộc người nào trong hệ thống
 -- Đăng ký / kích hoạt hồ sơ có sẵn
 (105, N'Đoàn Thị Yến'    , '1996-06-06', '0935000105', 'yen.doan@gmail.com'   , NULL                        , 0, '2026-09-12 16:00:00'), -- hồ sơ Lễ tân tạo, có email, CHƯA có tài khoản → nhánh Claim
 -- Quên mật khẩu / mã xác thực
 (108, N'Lương Thu Thảo'  , '1997-03-21', '0935000108', 'thao.luong@gmail.com' , NULL                        , 0, '2026-08-10 10:00:00'), -- có mã khôi phục còn hiệu lực
 (109, N'Ngô Đức Mạnh'    , '1993-09-09', '0935000109', 'manh.ngo@gmail.com'   , NULL                        , 0, '2026-08-11 10:00:00'), -- đã xin 4 mã trong 1 giờ
 (110, N'Đinh Văn Khoa'   , '1995-01-15', '0935000110', 'khoa.dinh@gmail.com'  , NULL                        , 0, '2026-08-12 10:00:00'), -- vừa xin mã, chưa đủ 60 giây
 (111, N'Tạ Thanh Bình'   , '1998-11-30', '0935000111', 'binh.ta@gmail.com'    , NULL                        , 0, '2026-08-13 10:00:00'), -- nhập sai mã 5 lần → email bị khóa xác thực
 (112, N'Cao Thị Quỳnh'   , '2000-07-07', '0935000112', 'quynh.cao@gmail.com'  , NULL                        , 0, '2026-08-14 10:00:00'), -- mã khôi phục đã hết hạn
 -- Tài khoản Lễ tân tạo tại quầy, chưa đặt mật khẩu (CBR-24)
 (113, N'Hồ Thị Nhung'    , '1994-02-02', '0935000113', 'nhung.ho@gmail.com'   , NULL                        , 0, '2026-09-20 09:00:00'); -- Lễ tân vừa tạo tài khoản, khách chưa đặt mật khẩu
SET IDENTITY_INSERT Persons OFF;
GO

/* ============================================================================
   3. CUSTOMERS
   ============================================================================ */
SET IDENTITY_INSERT Customers ON;
INSERT INTO Customers (CustomerId, PersonId, CustomerCode, CheckInPhoto, CustomerType,
                       GuardianName, GuardianRelationship, GuardianPhone, GuardianPhone2, Status, JoinedAt, DeletedAt) VALUES
 (101, 101, 'KH-1101', NULL                          , 'Self'     , NULL               , NULL  , NULL        , NULL        , 'New'     , '2026-08-01 10:05:00', NULL),
 (102, 102, 'KH-1102', NULL                          , 'Self'     , NULL               , NULL  , NULL        , NULL        , 'New'     , '2026-08-05 10:05:00', NULL),
 -- Giám hộ chính là An (GuardianPhone = SĐT của An) → hiện ở tab Hồ sơ phụ thuộc của An
 (103, 103, 'KH-1103', '/uploads/checkin/kh-1103.jpg', 'Dependent', N'Nguyễn Văn An'   , N'Bố' , '0901234501', NULL        , 'Active'  , '2026-09-01 13:55:00', NULL),
 -- An là giám hộ THỨ 2 (GuardianPhone2) → vẫn phải hiện ở tab của An
 (104, 104, 'KH-1104', NULL                          , 'Dependent', N'Trần Thị Hoa'    , N'Mẹ' , '0909111222', '0901234501', 'New'     , '2026-09-01 13:57:00', NULL),
 (105, 105, 'KH-1105', '/uploads/checkin/kh-1105.jpg', 'Self'     , NULL               , NULL  , NULL        , NULL        , 'New'     , '2026-09-12 16:05:00', NULL),
 -- Số giám hộ 0935000999 không thuộc Person nào → ai đăng ký bằng số này vẫn tạo mới bình thường (CBR-28)
 (106, 106, 'KH-1106', NULL                          , 'Dependent', N'Kiều Văn Hải'    , N'Bố' , '0935000999', NULL        , 'New'     , '2026-09-10 09:05:00', NULL),
 (107, 107, 'KH-1107', NULL                          , 'Self'     , NULL               , NULL  , NULL        , NULL        , 'Inactive', '2026-05-01 10:05:00', '2026-09-01 09:00:00'),
 (108, 108, 'KH-1108', NULL                          , 'Self'     , NULL               , NULL  , NULL        , NULL        , 'New'     , '2026-08-10 10:05:00', NULL),
 (109, 109, 'KH-1109', NULL                          , 'Self'     , NULL               , NULL  , NULL        , NULL        , 'New'     , '2026-08-11 10:05:00', NULL),
 (110, 110, 'KH-1110', NULL                          , 'Self'     , NULL               , NULL  , NULL        , NULL        , 'New'     , '2026-08-12 10:05:00', NULL),
 (111, 111, 'KH-1111', NULL                          , 'Self'     , NULL               , NULL  , NULL        , NULL        , 'New'     , '2026-08-13 10:05:00', NULL),
 (112, 112, 'KH-1112', NULL                          , 'Self'     , NULL               , NULL  , NULL        , NULL        , 'New'     , '2026-08-14 10:05:00', NULL),
 (113, 113, 'KH-1113', '/uploads/checkin/kh-1113.jpg', 'Self'     , NULL               , NULL  , NULL        , NULL        , 'New'     , '2026-09-20 09:05:00', NULL);
SET IDENTITY_INSERT Customers OFF;
GO

/* ============================================================================
   4. ACCOUNTS — mật khẩu Gms@2026; mốc khóa tạm tính theo giờ chạy script
   Person 103, 104, 106 (Dependent) và 105 (chờ kích hoạt) cố ý KHÔNG có tài khoản.
   ============================================================================ */
DECLARE @Now  DATETIME2(0) = SYSDATETIME();
DECLARE @Hash VARCHAR(255) = '$2a$10$o22fNyHpwvbuAj8q.LKCI.KMakVydfKblTjnBh7rgiUTrt55ZnXmu';

SET IDENTITY_INSERT Accounts ON;
INSERT INTO Accounts (AccountId, PersonId, PasswordHash, Status, FailedLoginCount, LockedUntil, LastLoginAt, CreatedAt) VALUES
 (101, 101, @Hash, 'Locked'  , 0, NULL                       , '2026-09-10 20:00:00', '2026-08-01 10:10:00'), -- Admin khóa → MSG37
 (102, 102, @Hash, 'Active'  , 5, DATEADD(MINUTE, 5, @Now)   , '2026-09-20 08:00:00', '2026-08-05 10:10:00'), -- khóa tạm 5 phút kể từ lúc chạy → MSG35
 (107, 107, @Hash, 'Inactive', 0, NULL                       , '2026-08-30 18:00:00', '2026-05-01 10:10:00'), -- hồ sơ đã xóa → tài khoản Inactive
 (108, 108, @Hash, 'Active'  , 4, NULL                       , '2026-09-18 08:00:00', '2026-08-10 10:10:00'), -- đã sai 4 lần: sai thêm 1 lần là bị khóa
 (109, 109, @Hash, 'Active'  , 0, NULL                       , '2026-09-18 08:00:00', '2026-08-11 10:10:00'),
 (110, 110, @Hash, 'Active'  , 0, NULL                       , '2026-09-18 08:00:00', '2026-08-12 10:10:00'),
 (111, 111, @Hash, 'Active'  , 0, NULL                       , '2026-09-18 08:00:00', '2026-08-13 10:10:00'),
 (112, 112, @Hash, 'Active'  , 0, NULL                       , '2026-09-18 08:00:00', '2026-08-14 10:10:00'),
 (113, 113, NULL , 'Active'  , 0, NULL                       , NULL                 , '2026-09-20 09:10:00'); -- chưa đặt mật khẩu → MSG38, đặt qua Quên mật khẩu (CBR-31)
SET IDENTITY_INSERT Accounts OFF;

/* ============================================================================
   5. VERIFICATION CODES — tính theo giờ chạy script để luôn test được
   Luật: chỉ mã MỚI NHẤT theo (Email, Purpose) có hiệu lực; hạn 15 phút (P-15);
         sai 5 lần → khóa 5 phút (P-17, P-18); cách nhau ≥ 60 giây; xin lần 5 trong 1 giờ → khóa.
   ============================================================================ */
SET IDENTITY_INSERT VerificationCodes ON;
INSERT INTO VerificationCodes (VerificationCodeId, AccountId, Email, Purpose, Code, SentAt, ExpiresAt, FailedAttempts, LockedUntil, UsedAt) VALUES
 -- Thảo: mã khôi phục còn hiệu lực ~13 phút → nhập 246810 là đúng, số khác → MSG43
 (101, 108 , 'thao.luong@gmail.com' , 'Recovery'   , '246810', DATEADD(MINUTE, -2 , @Now), DATEADD(MINUTE, 13, @Now), 0, NULL                     , NULL),
 -- Mạnh: đã xin 4 mã trong 60 phút gần nhất → lần xin thứ 5 phải bị khóa 5 phút (MSG61)
 (102, 109 , 'manh.ngo@gmail.com'   , 'Recovery'   , '100001', DATEADD(MINUTE, -50, @Now), DATEADD(MINUTE, -35, @Now), 0, NULL                     , NULL),
 (103, 109 , 'manh.ngo@gmail.com'   , 'Recovery'   , '100002', DATEADD(MINUTE, -40, @Now), DATEADD(MINUTE, -25, @Now), 0, NULL                     , NULL),
 (104, 109 , 'manh.ngo@gmail.com'   , 'Recovery'   , '100003', DATEADD(MINUTE, -30, @Now), DATEADD(MINUTE, -15, @Now), 0, NULL                     , NULL),
 (105, 109 , 'manh.ngo@gmail.com'   , 'Recovery'   , '100004', DATEADD(MINUTE, -5 , @Now), DATEADD(MINUTE, 10, @Now) , 0, NULL                     , NULL),
 -- Khoa: vừa xin mã cách đây 10 giây → bấm "Gửi lại mã" phải chờ đủ 60 giây
 (106, 110 , 'khoa.dinh@gmail.com'  , 'Recovery'   , '555111', DATEADD(SECOND, -10, @Now), DATEADD(MINUTE, 15, @Now), 0, NULL                     , NULL),
 -- Bình: đã sai 5 lần → mã bị hủy, email khóa xác thực 5 phút (MSG61)
 (107, 111 , 'binh.ta@gmail.com'    , 'Recovery'   , '777222', DATEADD(MINUTE, -3 , @Now), DATEADD(MINUTE, 12, @Now), 5, DATEADD(MINUTE, 5, @Now), NULL),
 -- Quỳnh: mã đã quá hạn → MSG44, phải bấm Gửi lại mã
 (108, 112 , 'quynh.cao@gmail.com'  , 'Recovery'   , '999000', DATEADD(MINUTE, -30, @Now), DATEADD(MINUTE, -15, @Now), 0, NULL                     , NULL),
 -- An (tài khoản 1): đang đổi email sang địa chỉ mới, mã 135790 còn hiệu lực
 (109, 1   , 'an.nguyen.moi@gmail.com', 'EmailChange', '135790', DATEADD(MINUTE, -1 , @Now), DATEADD(MINUTE, 14, @Now), 0, NULL                     , NULL),
 -- Đăng ký mới (chưa có tài khoản → AccountId NULL): email khach.moi@gmail.com, mã 112233
 (110, NULL, 'khach.moi@gmail.com'  , 'SignUp'     , '112233', DATEADD(MINUTE, -1 , @Now), DATEADD(MINUTE, 14, @Now), 0, NULL                     , NULL),
 -- Kích hoạt hồ sơ có sẵn của Yến (Person 105): mã 445566 gửi tới email trên hồ sơ
 (111, NULL, 'yen.doan@gmail.com'   , 'Claim'      , '445566', DATEADD(MINUTE, -1 , @Now), DATEADD(MINUTE, 14, @Now), 0, NULL                     , NULL);
SET IDENTITY_INSERT VerificationCodes OFF;
GO

/* ============================================================================
   6. GÓI TẬP CHO HỒ SƠ PHỤ THUỘC (Gia Huy) — để tab Hồ sơ phụ thuộc có cột "Gói hiện tại"
   Bán tại quầy ngày 01/09/2026, chuyển khoản (không làm lệch tiền mặt của ca 13).
   VAT 10% nằm trong giá: 2.190.000 × 10 ÷ 110 = 199.091
   ============================================================================ */
SET IDENTITY_INSERT Orders ON;
INSERT INTO Orders (OrderId, OrderCode, HolderCustomerId, PartnerCustomerId, PackageId, VoucherId,
                    CreatedByEmployeeId, ShiftId, Channel, PackageNameSnapshot, PackageDescriptionSnapshot,
                    UnitPriceSnapshot, DiscountAmount, VatRateSnapshot, VatAmount, Status,
                    PaymentDueAt, CreatedAt, PaidAt, CancelType, CancelledAt, CancelReason) VALUES
 (101, 'DH-2026-0101', 103, NULL, 3, NULL, 3, 13, 'Counter', N'Gói 3 Tháng', N'Đủ thời gian thấy chuyển biến thể lực.',
  2190000, 0, 10, 199091, 'Paid', NULL, '2026-09-01 13:58:00', '2026-09-01 14:00:00', NULL, NULL, NULL);
SET IDENTITY_INSERT Orders OFF;
GO

SET IDENTITY_INSERT Transactions ON;
INSERT INTO Transactions (TransactionId, OrderId, Amount, PaymentMethod, GatewayReference, Status, FailureReason, NeedsRefund, CreatedAt, ExpiresAt, CompletedAt) VALUES
 (101, 101, 2190000, 'Transfer', 'BANK-20260901-31', 'Success', NULL, 0, '2026-09-01 14:00:00', NULL, '2026-09-01 14:00:00');
SET IDENTITY_INSERT Transactions OFF;
GO

-- Kết thúc tính theo tháng (GBR-03): 01/09/2026 + 3 tháng = 01/12/2026
SET IDENTITY_INSERT Subscriptions ON;
INSERT INTO Subscriptions (SubscriptionId, OrderId, CustomerId, SubscriptionType, PtEmployeeId,
                           StartDate, EndDate, GeneratedSessionCount, SwapsUsed, FreezeUsed, Status) VALUES
 (101, 101, 103, 'Membership', NULL, '2026-09-01', '2026-12-01', NULL, 0, 0, 'Active');
SET IDENTITY_INSERT Subscriptions OFF;
GO

/* ============================================================================
   7. BLOG POSTS — 11 bài lấy từ prototype (trừ 2 bài trùng chủ đề với seed gốc)
   Sau file này có 14 bài Active → 3 trang (6 bài/trang), đủ test phân trang, lọc, tìm kiếm.
   Phủ các trường hợp: có/không ảnh bìa (ảnh mặc định theo danh mục), có/không tóm tắt
   (lấy đầu nội dung), bài dài (thời gian đọc), nội dung có định dạng (heading, list, link, đậm).
   Danh mục lưu tiếng Anh: Training = Tập luyện · Nutrition = Dinh dưỡng · News = Tin tức.
   ============================================================================ */
DECLARE @LongBody NVARCHAR(MAX) = REPLICATE(CAST(
    N'<p>Mỗi buổi tập nên bắt đầu bằng 5–10 phút làm nóng toàn thân, sau đó là các động tác khởi động riêng cho nhóm cơ sắp tập. Tăng dần biên độ và tốc độ, không kéo giãn tĩnh quá lâu trước khi nâng tạ nặng.</p>'
    AS NVARCHAR(MAX)), 25);

SET IDENTITY_INSERT BlogPosts ON;
INSERT INTO BlogPosts (BlogPostId, AuthorEmployeeId, Title, Category, Excerpt, Content, CoverImage, Status, PublishedAt) VALUES
 (101, 2, N'5 bài khởi động giúp giảm chấn thương trước buổi tập nặng', N'Training',
  N'Bỏ qua khởi động là nguyên nhân phổ biến gây chấn thương vai và gối. Đây là 5 động tác 8 phút nên làm trước mọi buổi tập.',
  N'<h2>Vì sao phải khởi động</h2>' + @LongBody,
  '/images/blog/khoi-dong.jpg', 'Active', '2026-09-12 08:00:00'), -- bài dài: thời gian đọc > 1 phút

 (102, 2, N'Ăn gì trước và sau buổi tập để tối ưu phục hồi cơ bắp', N'Nutrition',
  N'Thời điểm và loại thực phẩm nạp vào quanh buổi tập ảnh hưởng trực tiếp đến hiệu suất và tốc độ phục hồi.',
  N'<h2>Trước buổi tập</h2><p>Ăn bữa có tinh bột và đạm vừa phải trước 60–90 phút.</p><h2>Sau buổi tập</h2><ul><li><strong>Đạm:</strong> 20–40g</li><li><strong>Tinh bột:</strong> bù năng lượng</li></ul><p>Xem thêm <a href="https://www.who.int">khuyến nghị dinh dưỡng</a>.</p>',
  '/images/blog/an-gi.jpg', 'Active', '2026-09-08 08:00:00'), -- nội dung có định dạng

 (103, 2, N'Super Gym khai trương khu giãn cơ & phục hồi tại chi nhánh trung tâm', N'News',
  N'Khu vực foam roller, máy nén khí phục hồi và ghế massage chính thức mở cửa phục vụ hội viên từ tuần này.',
  N'<p>Khu giãn cơ mở cửa từ 06:00 đến 22:00, miễn phí cho hội viên đang có gói tập.</p>',
  NULL, 'Active', '2026-09-01 08:00:00'), -- không có ảnh bìa → dùng ảnh mặc định danh mục News

 (104, 2, N'Ngủ đủ giấc quan trọng thế nào với người tập gym?', N'Nutrition',
  N'Tập nặng nhưng ngủ ít có thể khiến mọi nỗ lực trong phòng gym gần như không mang lại hiệu quả.',
  N'<p>Giấc ngủ là lúc cơ thể tiết hormone tăng trưởng và sửa chữa sợi cơ. Người tập nên ngủ 7–9 tiếng mỗi đêm.</p>',
  '/images/blog/ngu-du-giac.jpg', 'Active', '2026-08-02 08:00:00'),

 (105, 2, N'Hướng dẫn chọn gói tập phù hợp với mục tiêu của bạn', N'Training',
  N'Gói 1 tháng hay gói 12 tháng — nên bắt đầu từ đâu tuỳ theo mục tiêu và tần suất tập của bạn.',
  N'<p>Người mới nên thử gói 1 tháng để tạo thói quen, sau đó chuyển sang gói dài hạn để tiết kiệm chi phí.</p>',
  '/images/blog/chon-goi.jpg', 'Active', '2026-07-27 08:00:00'),

 (106, 2, N'Cardio hay tạ nặng trước? Thứ tự nào tối ưu hơn cho mục tiêu giảm mỡ', N'Training',
  N'Thứ tự bài tập trong cùng một buổi ảnh hưởng đến hiệu suất từng phần — dưới đây là cách sắp xếp hợp lý.',
  N'<p>Ưu tiên bài tạ khi còn sung sức, cardio cường độ vừa đặt ở cuối buổi.</p>',
  NULL, 'Active', '2026-07-20 08:00:00'), -- không có ảnh bìa → ảnh mặc định Training

 (107, 2, N'Creatine có thực sự cần thiết? Giải đáp từ góc nhìn khoa học', N'Nutrition',
  N'Là một trong những thực phẩm bổ sung được nghiên cứu nhiều nhất, creatine có đáng để bổ sung vào chế độ tập của bạn?',
  N'<p>Creatine monohydrate 3–5g mỗi ngày an toàn với người khỏe mạnh và giúp tăng sức mạnh ở các bài ngắn, nặng.</p>',
  '/images/blog/creatine.jpg', 'Active', '2026-07-14 08:00:00'),

 (108, 2, N'Super Gym ra mắt ứng dụng đặt lịch PT mới trên điện thoại', N'News',
  N'Hội viên có thể xem khung giờ rảnh của PT, đặt buổi lẻ và theo dõi giáo án ngay trên ứng dụng.',
  N'<p>Ứng dụng hỗ trợ đặt buổi lẻ, xem lịch tập và nhận thông báo khi PT dời lịch.</p>',
  '/images/blog/app-pt.jpg', 'Active', '2026-07-05 08:00:00'),

 (109, 2, N'Tập lưng đúng cách: 4 bài kéo giúp cải thiện tư thế dân văn phòng', N'Training',
  NULL,
  N'<p>Ngồi nhiều khiến vai khòm về trước. Bốn bài kéo gồm lat pulldown, seated row, face pull và band pull-apart giúp cân bằng lại nhóm cơ lưng trên.</p>',
  '/images/blog/tap-lung.jpg', 'Active', '2026-06-28 08:00:00'), -- KHÔNG có tóm tắt → thẻ bài lấy phần đầu nội dung

 (110, 2, N'Nhịn ăn gián đoạn có phù hợp với người tập gym?', N'Nutrition',
  N'IF giúp một số người kiểm soát calo dễ hơn, nhưng không phải là lựa chọn tối ưu cho mọi mục tiêu tập luyện.',
  N'<p>Nếu mục tiêu là tăng cơ, hãy đảm bảo đủ đạm trong khung giờ ăn và không tập nặng khi đang đói lâu.</p>',
  '/images/blog/nhin-an.jpg', 'Active', '2026-06-20 08:00:00'),

 (111, 2, N'Super Gym mở rộng giờ hoạt động cuối tuần từ tháng sau', N'News',
  N'Từ tháng sau, phòng tập mở cửa sớm hơn vào Thứ 7 và Chủ Nhật để phục vụ nhu cầu tập cuối tuần.',
  N'<p>Giờ mở cửa cuối tuần áp dụng theo lịch mới, hội viên theo dõi thông báo trên trang chủ.</p>',
  '/images/blog/gio-tap.jpg', 'Active', '2026-06-12 08:00:00');
SET IDENTITY_INSERT BlogPosts OFF;
GO

/* ============================================================================
   KIỂM TRA NHANH
   ============================================================================ */
PRINT N'>>> Nạp dữ liệu Phase 1 thành công.';
SELECT 'Persons >= 101' AS Nhom, COUNT(*) AS SoDong FROM Persons WHERE PersonId >= 101
UNION ALL SELECT 'Accounts >= 101'         , COUNT(*) FROM Accounts          WHERE AccountId >= 101
UNION ALL SELECT 'VerificationCodes >= 101', COUNT(*) FROM VerificationCodes WHERE VerificationCodeId >= 101
UNION ALL SELECT 'BlogPosts Active'      , COUNT(*) FROM BlogPosts         WHERE Status = 'Active';
GO
