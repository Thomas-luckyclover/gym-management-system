/* ============================================================================
   GMS — Gym Management System
   FILE   : 2_Seed_Data.sql
   MỤC ĐÍCH: Nạp dữ liệu mẫu bám sát nghiệp vụ, phủ MỌI trạng thái và các luồng
             lỗi để team test được toàn bộ logic mà không phải tự tạo tay.
   MỐC THỜI GIAN GIẢ ĐỊNH: hôm nay là 23/09/2026 (thứ Tư).
   THỨ TỰ : Bảng cha trước, bảng con sau. Dùng IDENTITY_INSERT để ID cố định,
            nhờ vậy khóa ngoại trong file này luôn khớp và dễ đọc khi debug.
   LƯU Ý  : Chạy file này SAU 1_Init_Schema.sql, trên database rỗng.
   CẬP NHẬT 24/09/2026: thêm chuyên môn/kinh nghiệm PT, lý do nghỉ việc, link đặt mật khẩu
            (VerificationCodes 5), thời điểm nhắc duyệt nghỉ phép — khớp migrations/001.
   ============================================================================ */

USE GMS;
GO

SET NOCOUNT ON;
GO

/* ============================================================================
   1. PERSONS — 24 người: 16 khách, 8 nhân viên (2 người nước ngoài ≈ 8%)
   Person 22 (PT Kiều Trang) vừa là nhân viên vừa là khách → minh họa 2 vai trò.
   Person 9 (bé Gia Bảo) không có SĐT riêng → hồ sơ Dependent.
   Person 12 đã bị xóa hồ sơ nên IsReleased = 1: SĐT và email được nhả ra.
   ============================================================================ */
SET IDENTITY_INSERT Persons ON;
INSERT INTO Persons (PersonId, FullName, DateOfBirth, Phone, Email, Avatar, IsReleased, CreatedAt) VALUES
 (1 , N'Nguyễn Văn An'        , '1995-03-12', '0901234501', 'an.nguyen95@gmail.com'      , NULL, 0, '2026-06-28 09:10:00'),
 (2 , N'Trần Thị Bích'        , '1998-07-24', '0901234502', 'bich.tran@gmail.com'        , NULL, 0, '2026-08-30 20:05:00'),
 (3 , N'Lê Minh Quân'         , '1992-11-05', '0901234503', 'quan.le92@gmail.com'        , NULL, 0, '2026-06-09 18:40:00'),
 (4 , N'Phạm Thị Hồng'        , '1990-01-30', '0901234504', 'hong.pham@yahoo.com'        , NULL, 0, '2026-07-29 10:15:00'),
 (5 , N'Hoàng Văn Dũng'       , '2000-05-18', '0901234505', NULL                         , NULL, 0, '2026-09-23 07:45:00'),
 (6 , N'Đỗ Thị Mai'           , '1997-09-09', '0901234506', 'mai.do97@gmail.com'         , NULL, 0, '2026-09-05 08:30:00'),
 (7 , N'Vũ Quốc Hưng'         , '1994-02-14', '0901234507', 'hung.vu@gmail.com'          , NULL, 0, '2026-08-20 17:20:00'),
 (8 , N'Bùi Thị Thu Hà'       , '1996-12-01', '0901234508', 'ha.bui96@gmail.com'         , NULL, 0, '2026-08-20 17:25:00'),
 (9 , N'Ngô Gia Bảo'          , '2017-04-22', NULL        , NULL                         , NULL, 0, '2026-09-02 15:00:00'),
 (10, N'Đặng Thị Lan'         , '1985-08-08', '0901234510', 'lan.dang85@gmail.com'       , NULL, 0, '2026-09-02 15:05:00'),
 (11, N'Michael Johnson'      , '1989-06-17', '0901234511', 'michael.johnson@outlook.com', NULL, 0, '2026-07-15 11:00:00'),
 (12, N'Trương Mỹ Linh'       , '1993-10-11', '0901234512', 'linh.truong93@gmail.com'    , NULL, 1, '2026-03-01 09:00:00'),
 (13, N'Trịnh Văn Khánh'      , '1991-04-03', '0901234513', 'khanh.trinh@gmail.com'      , NULL, 0, '2026-09-01 13:30:00'),
 (14, N'Lý Thị Ngọc'          , '1999-01-25', '0901234514', 'ngoc.ly99@gmail.com'        , NULL, 0, '2026-09-20 21:10:00'),
 (15, N'Phan Văn Toàn'        , '2001-07-07', '0901234515', 'toan.phan@gmail.com'        , NULL, 0, '2026-09-22 19:00:00'),
 (16, N'Nguyễn Thị Hạnh'      , '1993-03-19', '0901234516', NULL                         , NULL, 0, '2026-09-23 08:05:00'),
 -- Nhân viên
 (17, N'Nguyễn Hải Đăng'      , '1988-05-20', '0912000001', 'dang.nguyen@gms.vn'         , NULL, 0, '2025-01-05 08:00:00'),
 (18, N'Trần Quốc Việt'       , '1986-09-14', '0912000002', 'viet.tran@gms.vn'           , NULL, 0, '2025-01-05 08:00:00'),
 (19, N'Lê Thị Thanh Tâm'     , '1997-02-02', '0912000003', 'tam.le@gms.vn'              , NULL, 0, '2025-02-10 08:00:00'),
 (20, N'Phạm Văn Lâm'         , '1996-11-28', '0912000004', 'lam.pham@gms.vn'            , NULL, 0, '2025-06-01 08:00:00'),
 (21, N'Đinh Công Sơn'        , '1994-08-16', '0912000005', 'son.dinh@gms.vn'            , NULL, 0, '2025-03-15 08:00:00'),
 (22, N'Nguyễn Thị Kiều Trang', '1995-12-09', '0912000006', 'trang.nguyen@gms.vn'        , NULL, 0, '2025-04-01 08:00:00'),
 (23, N'David Miller'         , '1990-03-27', '0912000007', 'david.miller@gms.vn'        , NULL, 0, '2025-09-01 08:00:00'),
 (24, N'Hồ Văn Nam'           , '1992-06-30', '0912000008', 'nam.ho@gms.vn'              , NULL, 0, '2025-05-01 08:00:00');
SET IDENTITY_INSERT Persons OFF;
GO

/* ============================================================================
   2. CUSTOMERS — 17 hồ sơ khách, phủ đủ 5 trạng thái
   CustomerId 9 là hồ sơ Dependent (trẻ em, dùng số của mẹ là Person 10).
   CustomerId 17 là PT Kiều Trang trong vai trò khách.
   ============================================================================ */
SET IDENTITY_INSERT Customers ON;
INSERT INTO Customers (CustomerId, PersonId, CustomerCode, CheckInPhoto, CustomerType,
                       GuardianName, GuardianRelationship, GuardianPhone, GuardianPhone2, Status, JoinedAt, DeletedAt) VALUES
 (1 , 1 , 'KH-1001', '/uploads/checkin/kh-1001.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-06-28 09:15:00', NULL),
 (2 , 2 , 'KH-1002', '/uploads/checkin/kh-1002.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-08-30 20:10:00', NULL),
 (3 , 3 , 'KH-1003', '/uploads/checkin/kh-1003.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Expired' , '2026-06-09 18:45:00', NULL),
 (4 , 4 , 'KH-1004', '/uploads/checkin/kh-1004.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Frozen'  , '2026-07-29 10:20:00', NULL),
 (5 , 5 , 'KH-1005', NULL                          , 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-09-23 07:50:00', NULL), -- khách vãng lai mua gói ngày, chưa có ảnh
 (6 , 6 , 'KH-1006', '/uploads/checkin/kh-1006.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-09-05 08:35:00', NULL),
 (7 , 7 , 'KH-1007', '/uploads/checkin/kh-1007.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-08-20 17:30:00', NULL),
 (8 , 8 , 'KH-1008', '/uploads/checkin/kh-1008.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-08-20 17:35:00', NULL),
 (9 , 9 , 'KH-1009', '/uploads/checkin/kh-1009.jpg', 'Dependent', N'Đặng Thị Lan', N'Mẹ', '0901234510', '0901234507', 'Active', '2026-09-02 15:10:00', NULL),
 (10, 10, 'KH-1010', '/uploads/checkin/kh-1010.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-09-02 15:15:00', NULL),
 (11, 11, 'KH-1011', '/uploads/checkin/kh-1011.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-07-15 11:05:00', NULL),
 (12, 12, 'KH-1012', '/uploads/checkin/kh-1012.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Inactive', '2026-03-01 09:05:00', '2026-09-15 16:00:00'), -- đã xóa mềm
 (13, 13, 'KH-1013', '/uploads/checkin/kh-1013.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-09-01 13:35:00', NULL), -- đang có yêu cầu xóa chờ duyệt
 (14, 14, 'KH-1014', NULL                          , 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-09-20 21:15:00', NULL),
 (15, 15, 'KH-1015', NULL                          , 'Self'     , NULL, NULL, NULL, NULL, 'New'     , '2026-09-22 19:05:00', NULL), -- đăng ký nhưng chưa mua gì
 (16, 16, 'KH-1016', NULL                          , 'Self'     , NULL, NULL, NULL, NULL, 'Active'  , '2026-09-23 08:10:00', NULL),
 (17, 22, 'KH-1017', '/uploads/checkin/kh-1017.jpg', 'Self'     , NULL, NULL, NULL, NULL, 'New'     , '2026-09-10 12:00:00', NULL); -- PT Kiều Trang trong vai trò khách
SET IDENTITY_INSERT Customers OFF;
GO

/* ============================================================================
   3. EMPLOYEES — 8 nhân viên, đủ 4 vị trí, có 1 người đã nghỉ việc
   ============================================================================ */
SET IDENTITY_INSERT Employees ON;
INSERT INTO Employees (EmployeeId, PersonId, EmployeeCode, Position, HireDate, CommissionRate,
                       Specialty, YearsOfExperience, Status, DeletedAt, ResignationReason) VALUES
 (1, 17, 'NV-0001', 'Admin'       , '2025-01-05', NULL, NULL                             , NULL, 'Working' , NULL, NULL),
 (2, 18, 'NV-0002', 'Manager'     , '2025-01-05', NULL, NULL                             , NULL, 'Working' , NULL, NULL),
 (3, 19, 'NV-0003', 'Receptionist', '2025-02-10', NULL, NULL                             , NULL, 'Working' , NULL, NULL),
 (4, 20, 'NV-0004', 'Receptionist', '2025-06-01', NULL, NULL                             , NULL, 'Working' , NULL, NULL),
 (5, 21, 'NV-0005', 'PT'          , '2025-03-15', 70  , N'Strength & Conditioning'       , 8   , 'Working' , NULL, NULL),
 (6, 22, 'NV-0006', 'PT'          , '2025-04-01', 65  , N'Giảm mỡ · Thể hình nữ'         , 6   , 'Working' , NULL, NULL),
 (7, 23, 'NV-0007', 'PT'          , '2025-09-01', 60  , N'Tăng cơ · Powerlifting'        , 9   , 'Working' , NULL, NULL),
 (8, 24, 'NV-0008', 'PT'          , '2025-05-01', 70  , N'Yoga & Phục hồi chấn thương'   , 5   , 'Resigned', '2026-08-31 18:00:00',
  N'Chuyển về quê làm việc, đã bàn giao khách cho PT khác'); -- đã nghỉ việc, giữ lịch sử
SET IDENTITY_INSERT Employees OFF;
GO

/* ============================================================================
   4. ACCOUNTS — 18 tài khoản, phủ đủ 3 trạng thái + khóa tạm + chưa đặt mật khẩu
   ============================================================================ */
SET IDENTITY_INSERT Accounts ON;
INSERT INTO Accounts (AccountId, PersonId, PasswordHash, Status, FailedLoginCount, LockedUntil, LastLoginAt, CreatedAt) VALUES
 (1 , 1 , '$2a$10$hashAn0000000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-22 18:30:00', '2026-06-28 09:20:00'),
 (2 , 2 , '$2a$10$hashBich000000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-23 07:10:00', '2026-08-30 20:15:00'),
 (3 , 3 , '$2a$10$hashQuan000000000000000000000000000000000000000000000', 'Active'  , 2, NULL                 , '2026-09-18 19:00:00', '2026-06-09 18:50:00'), -- đang sai 2 lần, chưa bị khóa
 (4 , 4 , '$2a$10$hashHong000000000000000000000000000000000000000000000', 'Active'  , 5, '2026-09-23 09:05:00', '2026-09-21 08:00:00', '2026-07-29 10:25:00'), -- KHÓA TẠM do sai 5 lần
 (5 , 6 , '$2a$10$hashMai0000000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-22 20:00:00', '2026-09-05 08:40:00'),
 (6 , 7 , '$2a$10$hashHung000000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-21 07:30:00', '2026-08-20 17:40:00'),
 (7 , 8 , '$2a$10$hashHaBui00000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-20 17:45:00', '2026-08-20 17:45:00'),
 (8 , 11, '$2a$10$hashMichael000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-19 12:00:00', '2026-07-15 11:10:00'),
 (9 , 13, NULL                                                          , 'Active'  , 0, NULL                 , NULL                 , '2026-09-01 13:40:00'), -- Lễ tân tạo, khách CHƯA đặt mật khẩu
 (10, 14, '$2a$10$hashNgoc000000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-23 08:50:00', '2026-09-20 21:20:00'),
 (11, 17, '$2a$10$hashAdmin00000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-23 07:00:00', '2025-01-05 08:05:00'),
 (12, 18, '$2a$10$hashManager000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-23 07:20:00', '2025-01-05 08:05:00'),
 (13, 19, '$2a$10$hashTam0000000000000000000000000000000000000000000000', 'Active'  , 3, NULL                 , '2026-09-23 05:35:00', '2025-02-10 08:05:00'), -- nhân viên KHÔNG bao giờ bị tự khóa
 (14, 20, '$2a$10$hashLam0000000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-22 13:40:00', '2025-06-01 08:05:00'),
 (15, 21, '$2a$10$hashSon0000000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-23 06:05:00', '2025-03-15 08:05:00'),
 (16, 22, '$2a$10$hashTrang00000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-23 06:10:00', '2025-04-01 08:05:00'), -- 1 tài khoản cho cả 2 vai trò
 (17, 23, '$2a$10$hashDavid00000000000000000000000000000000000000000000', 'Active'  , 0, NULL                 , '2026-09-22 16:00:00', '2025-09-01 08:05:00'),
 (18, 24, '$2a$10$hashNam0000000000000000000000000000000000000000000000', 'Inactive', 0, NULL                 , '2026-08-31 17:50:00', '2025-05-01 08:05:00'); -- nghỉ việc → khóa vĩnh viễn
SET IDENTITY_INSERT Accounts OFF;
GO

/* ============================================================================
   5. VERIFICATION CODES — phủ 4 tình huống: đã dùng, hết hạn, còn hiệu lực, bị khóa
   ============================================================================ */
SET IDENTITY_INSERT VerificationCodes ON;
INSERT INTO VerificationCodes (VerificationCodeId, AccountId, Email, Purpose, Code, TokenHash, SentAt, ExpiresAt, FailedAttempts, LockedUntil, UsedAt) VALUES
 (1, 10  , 'ngoc.ly99@gmail.com'   , 'SignUp'       , '481920', NULL, '2026-09-20 21:15:00', '2026-09-20 21:30:00', 0, NULL                 , '2026-09-20 21:18:00'),
 (2, NULL, 'toan.phan@gmail.com'   , 'SignUp'       , '730154', NULL, '2026-09-22 18:50:00', '2026-09-22 19:05:00', 1, NULL                 , NULL),                -- hết hạn, không ai dùng
 (3, 2   , 'bich.tran@gmail.com'   , 'EmailChange'  , '204877', NULL, '2026-09-23 08:40:00', '2026-09-23 08:55:00', 0, NULL                 , NULL),                -- đang còn hiệu lực
 (4, 3   , 'quan.le92@gmail.com'   , 'Recovery'     , '915003', NULL, '2026-09-23 08:20:00', '2026-09-23 08:35:00', 5, '2026-09-23 08:40:00', NULL);                -- sai 5 lần → khóa 5 phút
SET IDENTITY_INSERT VerificationCodes OFF;
GO

/* ============================================================================
   6. PACKAGES — 8 gói: 4 gói tập, 1 gói ngày, 2 gói PT theo kỳ hạn, 1 buổi lẻ
   Gói 5 để Inactive: ngừng bán nhưng đơn cũ vẫn chạy bình thường.
   ============================================================================ */
SET IDENTITY_INSERT Packages ON;
INSERT INTO Packages (PackageId, PackageName, Description, Benefits, PackageType, DurationDays, EstimatedSessionCount, Price, IsActive, CreatedAt) VALUES
 (1, N'Gói Ngày'          , N'Vé vào tập một lượt trong ngày.'          , N'Vào tập 1 lượt trong ngày' + CHAR(10) + N'Dùng đủ thiết bị khu chung', 'DayPass'      , 1  , NULL, 120000 , 1, '2025-01-10 09:00:00'),
 (2, N'Gói 1 Tháng'       , N'Bắt đầu xây thói quen tập luyện.'         , N'Tập không giới hạn giờ' + CHAR(10) + N'Miễn phí nước uống'           , 'Membership'   , 30 , NULL, 799000 , 1, '2025-01-10 09:00:00'),
 (3, N'Gói 3 Tháng'       , N'Đủ thời gian thấy chuyển biến thể lực.'   , N'Tập không giới hạn giờ' + CHAR(10) + N'Miễn phí nước uống' + CHAR(10) + N'Tặng 1 buổi đo InBody', 'Membership', 90 , NULL, 2190000, 1, '2025-01-10 09:00:00'),
 (4, N'Gói 6 Tháng'       , N'Cân bằng giữa cam kết và chi phí.'        , N'Tập không giới hạn giờ' + CHAR(10) + N'Tặng 2 buổi đo InBody'         , 'Membership'   , 180, NULL, 3990000, 1, '2025-01-10 09:00:00'),
 (5, N'Gói 12 Tháng'      , N'Mức giá tốt nhất cho người tập lâu dài.'  , N'Tập không giới hạn giờ' + CHAR(10) + N'Tặng 4 buổi đo InBody'         , 'Membership'   , 365, NULL, 6990000, 0, '2025-01-10 09:00:00'), -- ngừng bán
 (6, N'Gói PT 1 Tháng'    , N'Gói PT theo kỳ hạn, tập T2–T6.'           , N'Huấn luyện viên riêng' + CHAR(10) + N'Giáo án và thực đơn cá nhân hóa', 'PTSingle'    , 30 , 22  , 8800000, 1, '2025-02-01 09:00:00'),
 (7, N'Gói PT Couple 1 Tháng', N'Hai người tập chung một huấn luyện viên.', N'Chung hạn mức buổi cho 2 người' + CHAR(10) + N'Giáo án riêng cho từng người', 'PTCouple', 30 , 22  , 13200000, 1, '2025-02-01 09:00:00'),
 (8, N'Buổi PT Lẻ'        , N'Một buổi tập cùng huấn luyện viên.'       , N'Không ràng buộc kỳ hạn'                                              , 'SingleSession', NULL, 1  , 450000 , 1, '2025-02-01 09:00:00');
SET IDENTITY_INSERT Packages OFF;
GO

/* ============================================================================
   7. VOUCHERS — 4 mã: đang chạy, đã hết hạn, đã dùng hết lượt, đã tắt
   ============================================================================ */
SET IDENTITY_INSERT Vouchers ON;
INSERT INTO Vouchers (VoucherId, VoucherCode, DiscountType, DiscountValue, StartDate, EndDate,
                      MaxUsage, MaxUsagePerCustomer, UsedCount, MinOrderValue, ApplicableScope, IsActive) VALUES
 (1, 'WELCOME10'  , 'Percent', 10    , '2026-09-01', '2026-12-31', 100, 1   , 1  , 500000 , 'MembershipAndDayPass', 1),
 (2, 'SUMMER200K' , 'Amount' , 200000, '2026-06-01', '2026-08-31', 50 , 1   , 43 , 2000000, 'MembershipOnly'      , 1), -- đã hết hạn theo ngày
 (3, 'FULLBOOKED' , 'Amount' , 100000, '2026-09-01', '2026-10-31', 10 , 1   , 10 , 700000 , 'MembershipOnly'      , 1), -- hết lượt
 (4, 'OLDCAMPAIGN', 'Percent', 15    , '2026-01-01', '2026-03-31', 200, NULL, 87 , 0      , 'MembershipAndDayPass', 0); -- đã tắt
SET IDENTITY_INSERT Vouchers OFF;
GO

/* ============================================================================
   8. SHIFTS — 6 ca: đã chốt cân bằng, đã chốt lệch tiền, đang mở, chưa mở
   ============================================================================ */
SET IDENTITY_INSERT Shifts ON;
INSERT INTO Shifts (ShiftId, WorkDate, ShiftType, OpenedByEmployeeId, ClosedByEmployeeId, Status, OpenedAt, ClosedAt, ExpectedCash, CountedCash, VarianceReason) VALUES
 (1, '2026-09-21', 'Morning'  , 3, 3, 'Closed'   , '2026-09-21 05:30:00', '2026-09-21 13:00:00', 0      , 0      , NULL),
 (2, '2026-09-21', 'Afternoon', 4, 4, 'Closed'   , '2026-09-21 13:00:00', '2026-09-21 22:00:00', 0      , 50000  , N'Thừa 50.000đ chưa rõ nguồn, đã lập biên bản bàn giao'),
 (3, '2026-09-22', 'Morning'  , 3, 3, 'Closed'   , '2026-09-22 05:30:00', '2026-09-22 13:00:00', 0      , 0      , NULL),
 (4, '2026-09-22', 'Afternoon', 4, 4, 'Closed'   , '2026-09-22 13:00:00', '2026-09-22 22:00:00', 0      , 0      , NULL),
 (5, '2026-09-23', 'Morning'  , 3, NULL, 'Open'  , '2026-09-23 05:30:00', NULL, NULL, NULL, NULL), -- ca đang mở, còn 1 đơn Pending nên chưa chốt được
 (6, '2026-09-23', 'Afternoon', NULL, NULL, 'NotOpened', NULL, NULL, NULL, NULL, NULL),
 -- Ca lịch sử, giữ lại để các đơn tại quầy trong quá khứ luôn thuộc về một ca
 (7 , '2026-06-28', 'Morning'  , 3, 3, 'Closed', '2026-06-28 05:30:00', '2026-06-28 13:00:00', 2190000, 2190000, NULL),
 (8 , '2026-06-09', 'Afternoon', 4, 4, 'Closed', '2026-06-09 13:00:00', '2026-06-09 22:00:00', 799000 , 799000 , NULL),
 (9 , '2026-07-29', 'Morning'  , 4, 4, 'Closed', '2026-07-29 05:30:00', '2026-07-29 13:00:00', 0      , 0      , NULL),
 (10, '2026-09-05', 'Morning'  , 3, 3, 'Closed', '2026-09-05 05:30:00', '2026-09-05 13:00:00', 799000 , 799000 , NULL),
 (11, '2026-08-20', 'Afternoon', 4, 4, 'Closed', '2026-08-20 13:00:00', '2026-08-20 22:00:00', 4380000, 4380000, NULL),
 (12, '2026-08-21', 'Morning'  , 4, 4, 'Closed', '2026-08-21 05:30:00', '2026-08-21 13:00:00', 0      , 0      , NULL),
 (13, '2026-09-01', 'Afternoon', 3, 3, 'Closed', '2026-09-01 13:00:00', '2026-09-01 22:00:00', 799000 , 799000 , NULL),
 (14, '2026-03-01', 'Morning'  , 4, 4, 'Closed', '2026-03-01 05:30:00', '2026-03-01 13:00:00', 799000 , 799000 , NULL);
SET IDENTITY_INSERT Shifts OFF;
GO

/* ============================================================================
   9. ORDERS — 20 đơn, phủ: tại quầy / online, Paid / Pending / 4 loại hủy
   VAT 10% đã nằm TRONG giá; VatAmount = phải trả × 10 ÷ 110.
   ============================================================================ */
SET IDENTITY_INSERT Orders ON;
INSERT INTO Orders (OrderId, OrderCode, HolderCustomerId, PartnerCustomerId, PackageId, VoucherId,
                    CreatedByEmployeeId, ShiftId, Channel, PackageNameSnapshot, PackageDescriptionSnapshot,
                    UnitPriceSnapshot, DiscountAmount, VatRateSnapshot, VatAmount, Status,
                    PaymentDueAt, CreatedAt, PaidAt, CancelType, CancelledAt, CancelReason) VALUES
 -- Gói tập bán tại quầy, đã thanh toán
 (1 , 'DH-2026-0001', 1 , NULL, 3, NULL, 3, 7, 'Counter', N'Gói 3 Tháng', N'Đủ thời gian thấy chuyển biến thể lực.', 2190000, 0     , 10, 199091, 'Paid', NULL, '2026-06-28 09:25:00', '2026-06-28 09:30:00', NULL, NULL, NULL),
 -- Gói tập mua online có voucher 10%
 (2 , 'DH-2026-0002', 2 , NULL, 2, 1   , NULL, NULL, 'Online', N'Gói 1 Tháng', N'Bắt đầu xây thói quen tập luyện.', 799000 , 79900 , 10, 65373 , 'Paid', '2026-08-31 20:40:00', '2026-08-31 20:20:00', '2026-08-31 20:31:00', NULL, NULL, NULL),
 (3 , 'DH-2026-0003', 3 , NULL, 2, NULL, 3, 8, 'Counter', N'Gói 1 Tháng', N'Bắt đầu xây thói quen tập luyện.', 799000 , 0     , 10, 72636 , 'Paid', NULL, '2026-06-09 18:55:00', '2026-06-09 19:00:00', NULL, NULL, NULL),
 (4 , 'DH-2026-0004', 4 , NULL, 4, NULL, 4, 9, 'Counter', N'Gói 6 Tháng', N'Cân bằng giữa cam kết và chi phí.', 3990000, 0     , 10, 362727, 'Paid', NULL, '2026-07-29 10:30:00', '2026-07-29 10:35:00', NULL, NULL, NULL),
 -- Khách vãng lai mua gói ngày
 (5 , 'DH-2026-0005', 5 , NULL, 1, NULL, 3, 5, 'Counter', N'Gói Ngày', N'Vé vào tập một lượt trong ngày.', 120000 , 0     , 10, 10909 , 'Paid', NULL, '2026-09-23 07:50:00', '2026-09-23 07:52:00', NULL, NULL, NULL),
 -- Nền gói tập cho khách mua gói PT
 (6 , 'DH-2026-0006', 6 , NULL, 2, NULL, 3, 10, 'Counter', N'Gói 1 Tháng', N'Bắt đầu xây thói quen tập luyện.', 799000 , 0     , 10, 72636 , 'Paid', NULL, '2026-09-05 08:40:00', '2026-09-05 08:45:00', NULL, NULL, NULL),
 -- Gói PT đơn, bán tại quầy, tiền mặt
 (7 , 'DH-2026-0007', 6 , NULL, 6, NULL, 3, 10, 'Counter', N'Gói PT 1 Tháng', N'Gói PT theo kỳ hạn, tập T2–T6.', 8800000, 0     , 10, 800000, 'Paid', NULL, '2026-09-05 09:00:00', '2026-09-05 09:20:00', NULL, NULL, NULL),
 (8 , 'DH-2026-0008', 7 , NULL, 3, NULL, 4, 11, 'Counter', N'Gói 3 Tháng', N'Đủ thời gian thấy chuyển biến thể lực.', 2190000, 0, 10, 199091, 'Paid', NULL, '2026-08-20 17:40:00', '2026-08-20 17:45:00', NULL, NULL, NULL),
 (9 , 'DH-2026-0009', 8 , NULL, 3, NULL, 4, 11, 'Counter', N'Gói 3 Tháng', N'Đủ thời gian thấy chuyển biến thể lực.', 2190000, 0, 10, 199091, 'Paid', NULL, '2026-08-20 17:50:00', '2026-08-20 17:55:00', NULL, NULL, NULL),
 -- Gói PT couple: 1 người đứng tên, 1 người đi kèm
 (10, 'DH-2026-0010', 7 , 8   , 7, NULL, 4, 12, 'Counter', N'Gói PT Couple 1 Tháng', N'Hai người tập chung một huấn luyện viên.', 13200000, 0, 10, 1200000, 'Paid', NULL, '2026-08-21 09:00:00', '2026-08-21 09:25:00', NULL, NULL, NULL),
 -- Khách nước ngoài mua gói 12 tháng online (gói nay đã ngừng bán nhưng đơn cũ vẫn chạy)
 (11, 'DH-2026-0011', 11, NULL, 5, NULL, NULL, NULL, 'Online', N'Gói 12 Tháng', N'Mức giá tốt nhất cho người tập lâu dài.', 6990000, 0, 10, 635455, 'Paid', '2026-07-15 11:25:00', '2026-07-15 11:05:00', '2026-07-15 11:12:00', NULL, NULL, NULL),
 -- Đơn online đang chờ thanh toán, còn hạn
 (12, 'DH-2026-0012', 14, NULL, 2, NULL, NULL, NULL, 'Online', N'Gói 1 Tháng', N'Bắt đầu xây thói quen tập luyện.', 799000, 0, 10, 72636, 'Pending', '2026-09-23 09:15:00', '2026-09-23 08:55:00', NULL, NULL, NULL, NULL),
 -- Hủy do quá hạn thanh toán
 (13, 'DH-2026-0013', 14, NULL, 2, NULL, NULL, NULL, 'Online', N'Gói 1 Tháng', N'Bắt đầu xây thói quen tập luyện.', 799000, 0, 10, 72636, 'Cancelled', '2026-09-20 21:45:00', '2026-09-20 21:25:00', NULL, 'PaymentTimeout', '2026-09-20 21:45:00', N'Quá hạn thanh toán 20 phút'),
 -- Tiền về sau khi đơn đã hủy → giao dịch bị gắn cờ hoàn tiền tay
 (14, 'DH-2026-0014', 2 , NULL, 4, NULL, NULL, NULL, 'Online', N'Gói 6 Tháng', N'Cân bằng giữa cam kết và chi phí.', 3990000, 0, 10, 362727, 'Cancelled', '2026-09-19 10:20:00', '2026-09-19 10:00:00', NULL, 'InvalidPayment', '2026-09-19 10:21:00', N'Tiền về sau khi đơn đã hết hạn thanh toán'),
 -- Khách đang có yêu cầu xóa hồ sơ chờ duyệt
 (15, 'DH-2026-0015', 13, NULL, 2, NULL, 3, 13, 'Counter', N'Gói 1 Tháng', N'Bắt đầu xây thói quen tập luyện.', 799000, 0, 10, 72636, 'Paid', NULL, '2026-09-01 13:40:00', '2026-09-01 13:45:00', NULL, NULL, NULL),
 -- Khách vãng lai mua gói ngày rồi mua luôn buổi PT lẻ trong ngày
 (16, 'DH-2026-0016', 16, NULL, 1, NULL, 3, 5, 'Counter', N'Gói Ngày', N'Vé vào tập một lượt trong ngày.', 120000, 0, 10, 10909, 'Paid', NULL, '2026-09-23 08:10:00', '2026-09-23 08:12:00', NULL, NULL, NULL),
 (17, 'DH-2026-0017', 16, NULL, 8, NULL, 3, 5, 'Counter', N'Buổi PT Lẻ', N'Một buổi tập cùng huấn luyện viên.', 450000, 0, 10, 40909, 'Paid', NULL, '2026-09-23 08:15:00', '2026-09-23 08:18:00', NULL, NULL, NULL),
 -- Đơn cũ của khách đã bị xóa hồ sơ
 (18, 'DH-2026-0018', 12, NULL, 2, NULL, 4, 14, 'Counter', N'Gói 1 Tháng', N'Bắt đầu xây thói quen tập luyện.', 799000, 0, 10, 72636, 'Paid', NULL, '2026-03-01 09:10:00', '2026-03-01 09:15:00', NULL, NULL, NULL),
 -- Đơn tại quầy đang Pending trong ca đang mở → chặn chốt ca
 (19, 'DH-2026-0019', 15, NULL, 2, NULL, 3, 5, 'Counter', N'Gói 1 Tháng', N'Bắt đầu xây thói quen tập luyện.', 799000, 0, 10, 72636, 'Pending', '2026-09-23 09:20:00', '2026-09-23 09:00:00', NULL, NULL, NULL, NULL),
 -- Đơn buổi lẻ online đang giữ chỗ một khung giờ
 (20, 'DH-2026-0020', 14, NULL, 8, NULL, NULL, NULL, 'Online', N'Buổi PT Lẻ', N'Một buổi tập cùng huấn luyện viên.', 450000, 0, 10, 40909, 'Pending', '2026-09-23 09:18:00', '2026-09-23 08:58:00', NULL, NULL, NULL, NULL);
SET IDENTITY_INSERT Orders OFF;
GO

/* ============================================================================
   10. TRANSACTIONS — mỗi lần THỬ thanh toán là 1 dòng
   Đơn 2 có 1 lần Failed rồi mới Success → minh họa luồng thanh toán lại.
   ============================================================================ */
SET IDENTITY_INSERT Transactions ON;
INSERT INTO Transactions (TransactionId, OrderId, Amount, PaymentMethod, GatewayReference, Status, FailureReason, NeedsRefund, CreatedAt, ExpiresAt, CompletedAt) VALUES
 (1 , 1 , 2190000, 'Cash'         , NULL              , 'Success', NULL                        , 0, '2026-06-28 09:30:00', NULL                 , '2026-06-28 09:30:00'),
 (2 , 2 , 719100 , 'OnlineGateway', 'VNP-20260831-001', 'Failed' , N'Thẻ bị từ chối'           , 0, '2026-08-31 20:22:00', '2026-08-31 20:27:00', '2026-08-31 20:24:00'),
 (3 , 2 , 719100 , 'OnlineGateway', 'VNP-20260831-002', 'Success', NULL                        , 0, '2026-08-31 20:28:00', '2026-08-31 20:33:00', '2026-08-31 20:31:00'),
 (4 , 3 , 799000 , 'Cash'         , NULL              , 'Success', NULL                        , 0, '2026-06-09 19:00:00', NULL                 , '2026-06-09 19:00:00'),
 (5 , 4 , 3990000, 'Transfer'     , 'BANK-20260729-77', 'Success', NULL                        , 0, '2026-07-29 10:35:00', NULL                 , '2026-07-29 10:35:00'),
 (6 , 5 , 120000 , 'Cash'         , NULL              , 'Success', NULL                        , 0, '2026-09-23 07:52:00', NULL                 , '2026-09-23 07:52:00'),
 (7 , 6 , 799000 , 'Cash'         , NULL              , 'Success', NULL                        , 0, '2026-09-05 08:45:00', NULL                 , '2026-09-05 08:45:00'),
 (8 , 7 , 8800000, 'Transfer'     , 'BANK-20260905-12', 'Success', NULL                        , 0, '2026-09-05 09:20:00', NULL                 , '2026-09-05 09:20:00'),
 (9 , 8 , 2190000, 'Cash'         , NULL              , 'Success', NULL                        , 0, '2026-08-20 17:45:00', NULL                 , '2026-08-20 17:45:00'),
 (10, 9 , 2190000, 'Cash'         , NULL              , 'Success', NULL                        , 0, '2026-08-20 17:55:00', NULL                 , '2026-08-20 17:55:00'),
 (11, 10, 13200000,'Transfer'     , 'BANK-20260821-05', 'Success', NULL                        , 0, '2026-08-21 09:25:00', NULL                 , '2026-08-21 09:25:00'),
 (12, 11, 6990000, 'OnlineGateway', 'VNP-20260715-018', 'Success', NULL                        , 0, '2026-07-15 11:07:00', '2026-07-15 11:12:00', '2026-07-15 11:12:00'),
 (13, 12, 799000 , 'OnlineGateway', 'VNP-20260923-101', 'Pending', NULL                        , 0, '2026-09-23 09:05:00', '2026-09-23 09:10:00', NULL),
 (14, 13, 799000 , 'OnlineGateway', 'VNP-20260920-088', 'Expired', N'Hết hạn link thanh toán'  , 0, '2026-09-20 21:30:00', '2026-09-20 21:35:00', NULL),
 (15, 14, 3990000, 'OnlineGateway', 'VNP-20260919-044', 'Success', NULL                        , 1, '2026-09-19 10:15:00', '2026-09-19 10:20:00', '2026-09-19 10:22:00'), -- tiền về muộn → cần hoàn tay
 (16, 15, 799000 , 'Cash'         , NULL              , 'Success', NULL                        , 0, '2026-09-01 13:45:00', NULL                 , '2026-09-01 13:45:00'),
 (17, 16, 120000 , 'Cash'         , NULL              , 'Success', NULL                        , 0, '2026-09-23 08:12:00', NULL                 , '2026-09-23 08:12:00'),
 (18, 17, 450000 , 'Cash'         , NULL              , 'Success', NULL                        , 0, '2026-09-23 08:18:00', NULL                 , '2026-09-23 08:18:00'),
 (19, 18, 799000 , 'Cash'         , NULL              , 'Success', NULL                        , 0, '2026-03-01 09:15:00', NULL                 , '2026-03-01 09:15:00'),
 (20, 20, 450000 , 'OnlineGateway', 'VNP-20260923-102', 'Pending', NULL                        , 0, '2026-09-23 09:00:00', '2026-09-23 09:05:00', NULL);
SET IDENTITY_INSERT Transactions OFF;
GO

/* ============================================================================
   11. SUBSCRIPTIONS — quyền dùng sinh ra khi đơn Paid
   EndDate là NGÀY CUỐI CÙNG còn dùng được (tính cả ngày đó).
   ============================================================================ */
SET IDENTITY_INSERT Subscriptions ON;
INSERT INTO Subscriptions (SubscriptionId, OrderId, CustomerId, SubscriptionType, PtEmployeeId,
                           StartDate, EndDate, GeneratedSessionCount, SwapsUsed, FreezeUsed, Status) VALUES
 (1 , 1 , 1 , 'Membership'   , NULL, '2026-06-28', '2026-10-07', NULL, 0, 1, 'Active'),  -- đã bảo lưu 9 ngày, hạn được cộng thêm
 (2 , 2 , 2 , 'Membership'   , NULL, '2026-08-31', '2026-09-30', NULL, 0, 0, 'Active'),
 (3 , 3 , 3 , 'Membership'   , NULL, '2026-06-09', '2026-07-09', NULL, 0, 0, 'Expired'), -- khách thành Expired
 (4 , 4 , 4 , 'Membership'   , NULL, '2026-07-29', '2027-01-25', NULL, 0, 1, 'Frozen'),  -- đang bảo lưu, hạn đã cộng thêm
 (5 , 5 , 5 , 'DayPass'      , NULL, '2026-09-23', '2026-09-23', NULL, 0, 0, 'Used'),    -- đã check-in nên tiêu thụ xong
 (6 , 6 , 6 , 'Membership'   , NULL, '2026-09-05', '2026-10-05', NULL, 0, 0, 'Active'),
 (7 , 7 , 6 , 'PTSingle'     , 5   , '2026-09-07', '2026-10-07', 22  , 1, 0, 'Active'),  -- gói PT theo kỳ hạn, đã swap 1 lần
 (8 , 8 , 7 , 'Membership'   , NULL, '2026-08-20', '2026-11-20', NULL, 0, 0, 'Active'),
 (9 , 9 , 8 , 'Membership'   , NULL, '2026-08-20', '2026-11-20', NULL, 0, 0, 'Active'),
 (10, 10, 7 , 'PTCouple'     , 6   , '2026-08-24', '2026-09-23', 22  , 0, 0, 'Active'),  -- hôm nay là ngày cuối
 (11, 11, 11, 'Membership'   , NULL, '2026-07-15', '2027-07-14', NULL, 0, 0, 'Active'),
 (12, 15, 13, 'Membership'   , NULL, '2026-09-01', '2026-10-01', NULL, 0, 0, 'Active'),
 (13, 16, 16, 'DayPass'      , NULL, '2026-09-23', '2026-09-23', NULL, 0, 0, 'Active'),  -- chưa check-in
 (14, 17, 16, 'SingleSession', 7   , '2026-09-23', '2026-09-23', 1   , 0, 0, 'Active'),
 (15, 18, 12, 'Membership'   , NULL, '2026-03-01', '2026-03-31', NULL, 0, 0, 'Expired');
SET IDENTITY_INSERT Subscriptions OFF;
GO

/* ============================================================================
   12. FREEZE RECORDS — 1 lần đang bảo lưu, 1 lần đã kết thúc sớm
   ============================================================================ */
SET IDENTITY_INSERT FreezeRecords ON;
INSERT INTO FreezeRecords (FreezeRecordId, SubscriptionId, HandledByEmployeeId, StartDate, PlannedEndDate, ActualEndDate, Reason, CreatedAt) VALUES
 (1, 4, 3, '2026-09-15', '2026-10-14', NULL        , N'Khách đi công tác nước ngoài 1 tháng', '2026-09-14 16:30:00'), -- đang bảo lưu
 (2, 1, 4, '2026-08-01', '2026-08-31', '2026-08-10', N'Khách bị chấn thương gối'            , '2026-07-31 11:00:00'); -- hủy sớm: chỉ cộng 9 ngày thực tế
SET IDENTITY_INSERT FreezeRecords OFF;
GO

/* ============================================================================
   13. CHECK-INS — gói tập vào nhiều lượt/ngày được; gói ngày chỉ 1 lượt
   ============================================================================ */
SET IDENTITY_INSERT CheckIns ON;
INSERT INTO CheckIns (CheckInId, SubscriptionId, VerifiedByEmployeeId, CheckedInAt, PhotoVerified) VALUES
 (1 , 1 , 3, '2026-09-21 06:15:00', 1),
 (2 , 1 , 4, '2026-09-21 18:40:00', 1), -- cùng ngày vào lần 2: hợp lệ với gói tập
 (3 , 1 , 3, '2026-09-23 06:05:00', 1),
 (4 , 2 , 3, '2026-09-22 07:10:00', 1),
 (5 , 2 , 3, '2026-09-23 07:05:00', 1),
 (6 , 5 , 3, '2026-09-23 07:55:00', 0), -- gói ngày: không cần ảnh, dùng xong hết hiệu lực
 (7 , 6 , 3, '2026-09-21 08:00:00', 1),
 (8 , 6 , 4, '2026-09-22 17:30:00', 1),
 (9 , 8 , 4, '2026-09-22 18:00:00', 1),
 (10, 9 , 4, '2026-09-22 18:02:00', 1),
 (11, 11, 3, '2026-09-19 12:30:00', 1);
SET IDENTITY_INSERT CheckIns OFF;
GO

/* ============================================================================
   14. CUSTOMER DELETION REQUESTS — đủ 3 trạng thái
   ============================================================================ */
SET IDENTITY_INSERT CustomerDeletionRequests ON;
INSERT INTO CustomerDeletionRequests (DeletionRequestId, CustomerId, RequestedByEmployeeId, ReviewedByEmployeeId,
                                      Reason, Status, RejectReason, RequestedAt, ReviewedAt) VALUES
 (1, 13, 3, NULL, N'Khách yêu cầu xóa toàn bộ dữ liệu cá nhân', 'Pending' , NULL, '2026-09-22 15:00:00', NULL),
 (2, 12, 4, 2   , N'Khách chuyển vào Nam, không tập nữa'      , 'Approved', NULL, '2026-09-14 10:00:00', '2026-09-15 16:00:00'),
 (3, 1 , 3, 2   , N'Khách nhờ xóa hộ qua điện thoại'          , 'Rejected', N'Chưa xác minh được danh tính người yêu cầu, mời khách tới quầy', '2026-09-10 09:00:00', '2026-09-10 14:20:00');
SET IDENTITY_INSERT CustomerDeletionRequests OFF;
GO

/* ============================================================================
   15. TIME SLOTS — khung giờ PT công bố. Mỗi slot dài 90 phút.
   Phủ đủ 3 trạng thái: Free (còn trống), Held (đang giữ chỗ), Booked (đã có buổi).
   ============================================================================ */
SET IDENTITY_INSERT TimeSlots ON;
INSERT INTO TimeSlots (TimeSlotId, PtEmployeeId, SlotDate, StartTime, EndTime, Status, HeldByOrderId, HoldExpiresAt, CreatedAt) VALUES
 -- PT Sơn (Emp 5) — khung 18:00 cố định của gói PT khách Đỗ Thị Mai
 (1 , 5, '2026-09-07', '18:00', '19:30', 'Booked', NULL, NULL, '2026-09-01 20:00:00'),
 (2 , 5, '2026-09-08', '18:00', '19:30', 'Booked', NULL, NULL, '2026-09-01 20:00:00'),
 (3 , 5, '2026-09-09', '18:00', '19:30', 'Free'  , NULL, NULL, '2026-09-01 20:00:00'), -- buổi đã bị hủy nên slot trả về Free
 (4 , 5, '2026-09-10', '18:00', '19:30', 'Booked', NULL, NULL, '2026-09-01 20:00:00'),
 (5 , 5, '2026-09-11', '18:00', '19:30', 'Free'  , NULL, NULL, '2026-09-01 20:00:00'), -- buổi đã swap sang T7 nên slot trống lại
 (6 , 5, '2026-09-12', '09:00', '10:30', 'Booked', NULL, NULL, '2026-09-01 20:00:00'), -- slot T7 nhận buổi swap
 (7 , 5, '2026-09-24', '18:00', '19:30', 'Booked', NULL, NULL, '2026-09-01 20:00:00'),
 (8 , 5, '2026-09-25', '18:00', '19:30', 'Booked', NULL, NULL, '2026-09-01 20:00:00'),
 (9 , 5, '2026-09-23', '06:00', '07:30', 'Free'  , NULL, NULL, '2026-09-15 20:00:00'),
 -- PT Kiều Trang (Emp 6) — gói couple khung 19:30
 (10, 6, '2026-09-21', '19:30', '21:00', 'Booked', NULL, NULL, '2026-08-15 10:00:00'),
 (11, 6, '2026-09-22', '19:30', '21:00', 'Booked', NULL, NULL, '2026-08-15 10:00:00'),
 (12, 6, '2026-09-23', '19:30', '21:00', 'Booked', NULL, NULL, '2026-08-15 10:00:00'),
 (13, 6, '2026-09-24', '19:30', '21:00', 'Free'  , NULL, NULL, '2026-08-15 10:00:00'),
 -- PT David (Emp 7) — buổi lẻ hôm nay và slot đang bị giữ chỗ bởi đơn online
 (14, 7, '2026-09-23', '10:00', '11:30', 'Booked', NULL, NULL, '2026-09-18 09:00:00'),
 (15, 7, '2026-09-23', '14:00', '15:30', 'Held'  , 20  , '2026-09-23 09:18:00', '2026-09-18 09:00:00'), -- giữ chỗ cho đơn 20 đang chờ thanh toán
 (16, 7, '2026-09-24', '10:00', '11:30', 'Free'  , NULL, NULL, '2026-09-18 09:00:00'),
 (17, 7, '2026-09-25', '10:00', '11:30', 'Free'  , NULL, NULL, '2026-09-18 09:00:00'),
 -- PT đã nghỉ việc (Emp 8) — slot cũ giữ lại cho lịch sử
 (18, 8, '2026-08-20', '17:00', '18:30', 'Free'  , NULL, NULL, '2026-08-01 08:00:00');
SET IDENTITY_INSERT TimeSlots OFF;
GO

/* ============================================================================
   16. SESSIONS — phủ đủ 4 trạng thái và mọi lý do Missed
   Giá trị buổi gói PT đơn : 8.800.000 / 22 = 400.000 → hoa hồng 70% = 280.000
   Giá trị buổi gói couple : 13.200.000 / 22 = 600.000 → hoa hồng 65% = 390.000
   Buổi lẻ                 : 450.000 → hoa hồng 60% = 270.000
   ============================================================================ */
SET IDENTITY_INSERT Sessions ON;
INSERT INTO Sessions (SessionId, SubscriptionId, TimeSlotId, PtEmployeeId, Status, SessionValue, CommissionAmount,
                      AttendanceMarkedAt, IsAutoClosed, MissedReason, CancelReason, CancelledAt, CancelledByAccountId,
                      RefundAgreed, ResolutionType) VALUES
 -- Gói PT đơn của khách Đỗ Thị Mai (Sub 7)
 (1, 7 , 1 , 5, 'Completed', 400000, 280000, '2026-09-07 19:35:00', 0, NULL, NULL, NULL, NULL, 0, NULL),
 (2, 7 , 2 , 5, 'Completed', 400000, 280000, NULL                 , 1, NULL, NULL, NULL, NULL, 0, NULL), -- job tự đóng vì khách đã check-in
 (3, 7 , 4 , 5, 'Missed'   , 400000, 280000, NULL                 , 1, N'Khách không check-in trong ngày tập', NULL, NULL, NULL, 0, NULL),
 (4, 7 , 6 , 5, 'Completed', 400000, 280000, '2026-09-12 10:35:00', 0, NULL, NULL, NULL, NULL, 0, NULL), -- buổi đã swap sang Thứ 7
 (5, 7 , 7 , 5, 'Scheduled', 400000, 280000, NULL                 , 0, NULL, NULL, NULL, NULL, 0, NULL),
 (6, 7 , 8 , 5, 'Scheduled', 400000, 280000, NULL                 , 0, NULL, NULL, NULL, NULL, 0, NULL),
 -- Gói couple (Sub 10): PT hủy 1 buổi và đã trả tiền mặt cho khách ngoài hệ thống
 (7, 10, 10, 6, 'Completed', 600000, 390000, '2026-09-21 21:05:00', 0, NULL, NULL, NULL, NULL, 0, NULL),
 (8, 10, 11, 6, 'Cancelled', 600000, 390000, NULL                 , 0, NULL, N'PT bận việc gia đình, đã hoàn tiền mặt cho khách', '2026-09-20 15:00:00', NULL, 1, 'Cancel'),
 (9, 10, 12, 6, 'Scheduled', 600000, 390000, NULL                 , 0, NULL, NULL, NULL, NULL, 0, NULL),
 -- Buổi lẻ hôm nay của khách vãng lai (Sub 14)
 (10, 14, 14, 7, 'Scheduled', 450000, 270000, NULL, 0, NULL, NULL, NULL, NULL, 0, NULL);
SET IDENTITY_INSERT Sessions OFF;
GO

-- Buổi bị hủy ở trên vẫn để slot 11 là Booked cho đúng lịch sử? Không: slot đã nhả.
UPDATE TimeSlots SET Status = 'Free' WHERE TimeSlotId = 11;  -- buổi 8 bị PT hủy → khung giờ trả về Free
GO

/* ============================================================================
   17. SESSION SWAP LOGS — khách đổi buổi sang cuối tuần
   ============================================================================ */
SET IDENTITY_INSERT SessionSwapLogs ON;
INSERT INTO SessionSwapLogs (SessionSwapLogId, SessionId, OldTimeSlotId, NewTimeSlotId, RequestedByAccountId, SwappedAt) VALUES
 (1, 4, 5, 6, 5, '2026-09-10 21:00:00'); -- khách Đỗ Thị Mai (Account 5) đổi buổi T6 sang T7
SET IDENTITY_INSERT SessionSwapLogs OFF;
GO

/* ============================================================================
   18. LEAVE REQUESTS — đủ 3 trạng thái, có cả đơn bị hệ thống tự từ chối
   ============================================================================ */
SET IDENTITY_INSERT LeaveRequests ON;
INSERT INTO LeaveRequests (LeaveRequestId, EmployeeId, ReviewedByEmployeeId, LeaveType, LeaveDate, ShiftType,
                           Reason, Status, RejectReason, SubmittedAt, ReviewedAt, ReminderSentAt) VALUES
 (1, 5, 2   , 'Annual', '2026-09-18', 'Morning'  , N'Về quê giỗ ông'                 , 'Approved', NULL, '2026-09-17 10:00:00', '2026-09-17 15:30:00', NULL),
 (2, 6, NULL, 'Sick'  , '2026-09-26', 'FullDay'  , N'Bị cúm, có giấy khám'           , 'Pending' , NULL, '2026-09-23 08:00:00', NULL                 , NULL), -- chưa nhắc: JOB-07 sẽ nhắc 1 lần trước 20:00 ngày 25/09
 (3, 7, 2   , 'Unpaid', '2026-09-20', 'Afternoon', N'Có việc cá nhân'                , 'Rejected', N'Ca chiều chỉ còn 2 PT, duyệt sẽ thiếu người', '2026-09-19 09:00:00', '2026-09-19 17:00:00', NULL),
 (4, 4, NULL, 'Annual', '2026-09-16', 'Morning'  , N'Đưa con đi khám'                , 'Rejected', N'Quá hạn duyệt', '2026-09-15 14:00:00', '2026-09-15 20:00:00', '2026-09-15 19:00:00'), -- đã nhắc lúc 19:00 nhưng Manager không duyệt → hệ thống tự từ chối
 (5, 3, 2   , 'Annual', '2026-09-30', 'Afternoon', N'Đi đám cưới bạn'                , 'Approved', NULL, '2026-09-22 09:30:00', '2026-09-22 11:00:00', NULL);
SET IDENTITY_INSERT LeaveRequests OFF;
GO

/* ============================================================================
   19. FACILITIES — đủ 3 tình trạng thiết bị
   ============================================================================ */
SET IDENTITY_INSERT Facilities ON;
INSERT INTO Facilities (FacilityId, FacilityName, Category, Location, Quantity, Status) VALUES
 (1, N'Máy chạy bộ Technogym'   , N'Cardio'      , N'Khu cardio tầng 1', 8 , 'Available'),
 (2, N'Xe đạp tập'              , N'Cardio'      , N'Khu cardio tầng 1', 6 , 'Available'),
 (3, N'Giàn tạ đa năng'         , N'Machines'    , N'Khu tạ tầng 2'    , 3 , 'Available'),
 (4, N'Bộ tạ đơn 2–40kg'        , N'FreeWeights' , N'Khu tạ tầng 2'    , 20, 'Available'),
 (5, N'Máy ép ngực'             , N'Machines'    , N'Khu tạ tầng 2'    , 2 , 'UnderMaintenance'), -- đang có yêu cầu sửa
 (6, N'Ghế đẩy vai'             , N'Machines'    , N'Khu tạ tầng 2'    , 2 , 'Available'),
 (7, N'Tủ khóa cá nhân'         , N'Amenities'   , N'Phòng thay đồ'    , 60, 'Available'),
 (8, N'Máy leo cầu thang cũ'    , N'Cardio'      , N'Kho'              , 1 , 'Retired');
SET IDENTITY_INSERT Facilities OFF;
GO

/* ============================================================================
   20. MAINTENANCE REQUESTS — đủ 4 trạng thái
   ============================================================================ */
SET IDENTITY_INSERT MaintenanceRequests ON;
INSERT INTO MaintenanceRequests (MaintenanceRequestId, FacilityId, ReportedByEmployeeId, ReviewedByEmployeeId, ClosedByEmployeeId,
                                 Description, Status, RejectReason, ReportedAt, ReviewedAt, ClosedAt) VALUES
 (1, 5, 3, 2   , NULL, N'Máy ép ngực kêu to khi đẩy, nghi hỏng bạc đạn', 'InProgress', NULL, '2026-09-20 09:00:00', '2026-09-20 11:00:00', NULL),
 (2, 1, 4, 2   , 3   , N'Máy chạy số 3 bị giật khi tăng tốc'           , 'Closed'    , NULL, '2026-09-10 08:00:00', '2026-09-10 10:00:00', '2026-09-14 16:00:00'),
 (3, 4, 3, 2   , NULL, N'Xin mua thêm tạ đơn 45kg'                     , 'Rejected'  , N'Ngoài phạm vi bảo trì, chuyển sang kế hoạch mua sắm quý sau', '2026-09-12 14:00:00', '2026-09-12 17:30:00', NULL),
 (4, 2, 3, NULL, NULL, N'Xe đạp số 2 kẹt bàn đạp'                      , 'Open'      , NULL, '2026-09-23 08:30:00', NULL, NULL),
 (5, 7, 4, NULL, NULL, N'Tủ khóa số 12 hỏng chốt'                      , 'Open'      , NULL, '2026-09-22 20:00:00', NULL, NULL);
SET IDENTITY_INSERT MaintenanceRequests OFF;
GO

/* ============================================================================
   21. STAFF ATTENDANCES — chấm công (mô hình hóa sẵn, phiên bản này chưa dùng)
   ============================================================================ */
SET IDENTITY_INSERT StaffAttendances ON;
INSERT INTO StaffAttendances (StaffAttendanceId, EmployeeId, WorkDate, ShiftType, CheckInAt, CheckInPhoto, CheckOutAt, Status, Note) VALUES
 (1, 3, '2026-09-22', 'Morning'  , '2026-09-22 05:25:00', '/uploads/attendance/nv-0003-0922.jpg', '2026-09-22 13:05:00', 'Present', NULL),
 (2, 4, '2026-09-22', 'Afternoon', '2026-09-22 13:15:00', '/uploads/attendance/nv-0004-0922.jpg', '2026-09-22 22:05:00', 'Late'   , N'Đi muộn 15 phút do tắc đường'),
 (3, 5, '2026-09-22', 'Afternoon', '2026-09-22 13:00:00', '/uploads/attendance/nv-0005-0922.jpg', '2026-09-22 21:30:00', 'Present', NULL),
 (4, 6, '2026-09-22', 'Afternoon', '2026-09-22 12:55:00', '/uploads/attendance/nv-0006-0922.jpg', '2026-09-22 21:35:00', 'Present', NULL),
 (5, 5, '2026-09-18', 'Morning'  , NULL                 , NULL                                  , NULL                 , 'OnLeave', N'Nghỉ phép đã được duyệt'),
 (6, 3, '2026-09-23', 'Morning'  , '2026-09-23 05:28:00', '/uploads/attendance/nv-0003-0923.jpg', NULL                 , 'Present', N'Đang trong ca');
SET IDENTITY_INSERT StaffAttendances OFF;
GO

/* ============================================================================
   22. PRIVATE NOTES — mỗi cặp (PT, khách) tối đa 1 ghi chú đang dùng
   ============================================================================ */
SET IDENTITY_INSERT PrivateNotes ON;
INSERT INTO PrivateNotes (PrivateNoteId, PtEmployeeId, CustomerId, Content, UpdatedAt, DeletedAt) VALUES
 (1, 5, 6, N'Khách đau vai phải, tránh bài đẩy vai qua đầu. Ưu tiên tập chân và lưng.', '2026-09-08 20:00:00', NULL),
 (2, 6, 7, N'Khách tiến bộ nhanh, có thể tăng tạ 5kg mỗi 2 tuần.'                      , '2026-09-15 21:30:00', NULL),
 (3, 6, 8, N'Ghi chú cũ đã lỗi thời, viết lại sau.'                                    , '2026-08-25 21:00:00', '2026-09-01 09:00:00'); -- đã xóa mềm → PT viết được bản mới
SET IDENTITY_INSERT PrivateNotes OFF;
GO

/* ============================================================================
   23. TRAINING PLANS — có bản đã xuất bản, bản nháp và bản đã xóa
   ============================================================================ */
SET IDENTITY_INSERT TrainingPlans ON;
INSERT INTO TrainingPlans (TrainingPlanId, PtEmployeeId, CustomerId, PlanType, Title, Content, IsPublished, UpdatedAt, DeletedAt) VALUES
 (1, 5, 6, 'Workout', N'Giáo án 4 tuần — tăng cơ nền', N'Tuần 1: full body 3 buổi. Tuần 2–4: đẩy/kéo/chân.', 1, '2026-09-08 21:00:00', NULL),
 (2, 5, 6, 'Meal'   , N'Thực đơn 4 tuần — thâm hụt nhẹ', N'Bữa sáng: yến mạch + trứng. Bữa trưa: ức gà + cơm gạo lứt.', 0, '2026-09-09 21:00:00', NULL), -- nháp, khách chưa thấy
 (3, 6, 7, 'Workout', N'Giáo án couple — 6 tuần'      , N'Tập theo cặp, luân phiên đẩy và kéo.'            , 1, '2026-08-26 20:00:00', NULL),
 (4, 6, 8, 'Workout', N'Giáo án cũ'                   , N'Nội dung cũ, đã thay bằng bản mới.'              , 0, '2026-08-20 20:00:00', '2026-08-26 19:00:00');
SET IDENTITY_INSERT TrainingPlans OFF;
GO

/* ============================================================================
   24. FEEDBACKS — chưa đọc và đã đọc
   ============================================================================ */
SET IDENTITY_INSERT Feedbacks ON;
INSERT INTO Feedbacks (FeedbackId, CustomerId, Content, SubmittedAt, IsRead, ReadAt, ReadByEmployeeId) VALUES
 (1, 1 , N'Phòng tắm hết nước nóng vào buổi tối, mong phòng gym kiểm tra.'          , '2026-09-22 21:10:00', 0, NULL                 , NULL),
 (2, 2 , N'Nhân viên lễ tân rất nhiệt tình, cảm ơn phòng gym.'                      , '2026-09-21 19:00:00', 1, '2026-09-22 09:00:00', 2),
 (3, 6 , N'Đề xuất mở thêm lớp yoga buổi sáng.'                                     , '2026-09-20 07:30:00', 1, '2026-09-20 15:00:00', 2),
 (4, 11, N'The air conditioning in the weights area is too cold in the morning.'    , '2026-09-19 08:15:00', 0, NULL                 , NULL),
 (5, 7 , N'Máy chạy số 3 chạy giật, đã báo lễ tân nhưng ghi nhận lại cho chắc.'     , '2026-09-09 20:00:00', 1, '2026-09-10 08:30:00', 2);
SET IDENTITY_INSERT Feedbacks OFF;
GO

/* ============================================================================
   25. BLOG POSTS — 3 bài đang hiện trên web, 1 bài đã gỡ xuống
   ============================================================================ */
SET IDENTITY_INSERT BlogPosts ON;
INSERT INTO BlogPosts (BlogPostId, AuthorEmployeeId, Title, Category, Excerpt, Content, CoverImage, Status, PublishedAt) VALUES
 (1, 2, N'Ba lỗi squat phổ biến'        , N'Training' , N'Squat sai tư thế dễ gây đau gối và lưng dưới.', N'Lỗi 1: gối đổ vào trong...', '/images/blog/squat.jpg'   , 'Active'  , '2026-09-01 10:00:00'),
 (2, 2, N'Protein bao nhiêu là đủ?'     , N'Nutrition', N'Người tập tạ cần bao nhiêu gram protein mỗi ngày.', N'Khuyến nghị 1.6–2.2g/kg...', '/images/blog/protein.jpg' , 'Active'  , '2026-09-08 10:00:00'),
 (3, 2, N'Lịch nghỉ lễ Quốc khánh'      , N'News'     , N'Phòng gym nghỉ ngày 02/09.'                    , N'Phòng gym nghỉ cả ngày 02/09...', '/images/blog/news.jpg'   , 'Active'  , '2026-08-25 09:00:00'),
 (4, 2, N'Khuyến mãi tháng 6 (đã kết thúc)', N'News'  , N'Chương trình đã kết thúc.'                     , N'Nội dung khuyến mãi cũ...', '/images/blog/promo.jpg'  , 'Inactive', '2026-06-01 09:00:00');
SET IDENTITY_INSERT BlogPosts OFF;
GO

/* ============================================================================
   26. NOTIFICATIONS + RECIPIENTS — chỉ người CÓ tài khoản mới nhận được
   ============================================================================ */
SET IDENTITY_INSERT Notifications ON;
INSERT INTO Notifications (NotificationId, SentByEmployeeId, Title, Content, TargetType, SentAt) VALUES
 (1, 2   , N'Lịch nghỉ lễ'          , N'Phòng gym nghỉ cả ngày 02/09, mong khách sắp xếp lịch tập.', 'All'     , '2026-08-25 09:05:00'),
 (2, 2   , N'Họp nhân sự cuối tháng', N'Toàn bộ PT và lễ tân họp lúc 21:30 ngày 30/09.'            , 'Role'    , '2026-09-22 10:00:00'),
 (3, NULL, N'Buổi tập bị dời'       , N'Buổi PT ngày 22/09 đã được dời theo thỏa thuận với PT.'     , 'Customer', '2026-09-20 15:01:00'); -- hệ thống tự gửi
SET IDENTITY_INSERT Notifications OFF;
GO

SET IDENTITY_INSERT NotificationRecipients ON;
INSERT INTO NotificationRecipients (NotificationRecipientId, NotificationId, AccountId, ReadAt) VALUES
 (1, 1, 1 , '2026-08-25 19:00:00'),
 (2, 1, 2 , NULL),
 (3, 1, 8 , '2026-08-26 07:30:00'),
 (4, 2, 15, '2026-09-22 12:00:00'),
 (5, 2, 16, NULL),
 (6, 3, 6 , '2026-09-20 16:00:00'),
 (7, 3, 7 , NULL);
SET IDENTITY_INSERT NotificationRecipients OFF;
GO

/* ============================================================================
   27. SYSTEM CONFIGS — 20 tham số P-01…P-20 với giá trị mặc định đã chốt
   ============================================================================ */
SET IDENTITY_INSERT SystemConfigs ON;
INSERT INTO SystemConfigs (SystemConfigId, ConfigKey, ConfigValue, Description, UpdatedAt, UpdatedByEmployeeId) VALUES
 (1 , 'P-01', '1'          , N'Số lần bảo lưu tối đa cho mỗi gói tập'                     , '2025-01-10 09:00:00', 2),
 (2 , 'P-02', '30'         , N'Số ngày bảo lưu tối đa mỗi lần'                            , '2025-01-10 09:00:00', 2),
 (3 , 'P-03', '0'          , N'Phí bảo lưu (VND), 0 = miễn phí'                           , '2025-01-10 09:00:00', 2),
 (4 , 'P-04', '1,30,90,180,365', N'Danh sách thời hạn gói tập (ngày)'                     , '2025-01-10 09:00:00', 2),
 (5 , 'P-05', '10'         , N'Tuổi tối thiểu được dùng dịch vụ PT'                       , '2025-01-10 09:00:00', 2),
 (6 , 'P-06', '90'         , N'Độ dài một buổi PT (phút)'                                 , '2025-01-10 09:00:00', 2),
 (7 , 'P-07', '24'         , N'Cửa sổ điểm danh sau khi buổi kết thúc (giờ)'              , '2025-01-10 09:00:00', 2),
 (8 , 'P-08', '3'          , N'Số lần swap tối đa mỗi gói PT'                             , '2025-01-10 09:00:00', 2),
 (9 , 'P-09', '20'         , N'Hạn thanh toán đơn online, cũng là thời gian giữ slot (phút)', '2025-01-10 09:00:00', 2),
 (10, 'P-10', '10'         , N'Thuế VAT (%) đã nằm trong giá bán'                         , '2025-01-10 09:00:00', 2),
 (11, 'P-11', '70'         , N'Tỷ lệ hoa hồng mặc định cho PT mới (%)'                    , '2025-01-10 09:00:00', 2),
 (12, 'P-12', '15:00'      , N'Giờ chốt nộp đơn nghỉ của ngày hôm trước'                  , '2025-01-10 09:00:00', 2),
 (13, 'P-13', '2'          , N'Số PT tối thiểu còn lại mỗi ca'                            , '2025-01-10 09:00:00', 2),
 (14, 'P-14', '1'          , N'Số lễ tân tối thiểu còn lại mỗi ca'                        , '2025-01-10 09:00:00', 2),
 (15, 'P-15', '15'         , N'Hiệu lực mã xác thực / mã khôi phục (phút)'               , '2025-01-10 09:00:00', 2),
 (16, 'P-16', '03,05,07,08,09', N'Đầu số di động hợp lệ'                                  , '2025-01-10 09:00:00', 2),
 (17, 'P-17', '5'          , N'Số lần sai trước khi khóa tạm (đăng nhập và mã xác thực)'  , '2025-01-10 09:00:00', 2),
 (18, 'P-18', '5'          , N'Thời gian khóa tạm (phút)'                                 , '2025-01-10 09:00:00', 2),
 (19, 'P-19', '05:30-22:00', N'Giờ mở cửa mặc định hằng ngày'                             , '2025-01-10 09:00:00', 2),
 (20, 'P-20', '20:00'      , N'Giờ chốt duyệt đơn nghỉ của ngày hôm trước'                , '2025-01-10 09:00:00', 2);
SET IDENTITY_INSERT SystemConfigs OFF;
GO

/* ============================================================================
   28. OPERATING HOUR EXCEPTIONS — ngày nghỉ và ngày mở cửa khác thường
   Ngày nghỉ khiến hệ thống KHÔNG sinh buổi PT vào ngày đó.
   ============================================================================ */
SET IDENTITY_INSERT OperatingHourExceptions ON;
INSERT INTO OperatingHourExceptions (OperatingHourExceptionId, ApplyDate, OpenTime, CloseTime, IsClosedAllDay, Note, SetByEmployeeId) VALUES
 (1, '2026-09-02', NULL   , NULL   , 1, N'Nghỉ lễ Quốc khánh'              , 2),
 (2, '2026-09-27', '07:00', '18:00', 0, N'Bảo trì hệ thống điện, mở muộn'  , 2),
 (3, '2027-01-28', NULL   , NULL   , 1, N'Nghỉ Tết Nguyên đán'             , 2);
SET IDENTITY_INSERT OperatingHourExceptions OFF;
GO

/* ============================================================================
   29. AUDIT LOGS — hành động nhạy cảm, viết thành câu đọc được
   AccountId NULL = do hệ thống (job) thực hiện.
   ============================================================================ */
SET IDENTITY_INSERT AuditLogs ON;
INSERT INTO AuditLogs (AuditLogId, AccountId, Action, EntityName, EntityId, FieldChanged, OldValue, NewValue, Reason, CreatedAt) VALUES
 (1, 12  , N'Đổi giá gói dịch vụ'        , 'Packages'                , 2 , 'Price'      , '749000'   , '799000'   , N'Điều chỉnh giá theo bảng giá quý IV', '2026-09-01 09:30:00'),
 (2, 13  , N'Đổi số điện thoại khách'    , 'Persons'                 , 3 , 'Phone'      , '0901234599', '0901234503', N'Khách đổi số, đã đối chiếu ảnh check-in', '2026-09-05 10:15:00'),
 (3, 12  , N'Từ chối đơn nghỉ phép'      , 'LeaveRequests'           , 3 , 'Status'     , 'Pending'  , 'Rejected' , N'Ca chiều chỉ còn 2 PT, duyệt sẽ thiếu người', '2026-09-19 17:00:00'),
 (4, NULL, N'Tự từ chối đơn nghỉ quá hạn', 'LeaveRequests'           , 4 , 'Status'     , 'Pending'  , 'Rejected' , N'Quá hạn duyệt 20:00 ngày hôm trước', '2026-09-15 20:00:00'),
 (5, 12  , N'Duyệt xóa hồ sơ khách'      , 'Customers'               , 12, 'Status'     , 'Active'   , 'Inactive' , N'Khách chuyển vào Nam, không tập nữa', '2026-09-15 16:00:00'),
 (6, NULL, N'Hủy đơn quá hạn thanh toán' , 'Orders'                  , 13, 'Status'     , 'Pending'  , 'Cancelled', N'Quá hạn thanh toán 20 phút', '2026-09-20 21:45:00'),
 (7, 11  , N'Vô hiệu hóa tài khoản'      , 'Accounts'                , 18, 'Status'     , 'Active'   , 'Inactive' , N'Nhân viên nghỉ việc từ 31/08/2026', '2026-08-31 18:00:00'),
 (8, 13  , N'Bảo lưu gói tập'            , 'Subscriptions'           , 4 , 'Status'     , 'Active'   , 'Frozen'   , N'Khách đi công tác nước ngoài 1 tháng', '2026-09-14 16:30:00');
SET IDENTITY_INSERT AuditLogs OFF;
GO

/* ============================================================================
   KIỂM TRA NHANH SAU KHI NẠP
   ============================================================================ */
PRINT N'>>> Nạp dữ liệu mẫu thành công. Số dòng theo bảng:';
SELECT 'Persons' AS TableName, COUNT(*) AS Rows FROM Persons
UNION ALL SELECT 'Customers'               , COUNT(*) FROM Customers
UNION ALL SELECT 'Employees'               , COUNT(*) FROM Employees
UNION ALL SELECT 'Accounts'                , COUNT(*) FROM Accounts
UNION ALL SELECT 'VerificationCodes'       , COUNT(*) FROM VerificationCodes
UNION ALL SELECT 'Packages'                , COUNT(*) FROM Packages
UNION ALL SELECT 'Vouchers'                , COUNT(*) FROM Vouchers
UNION ALL SELECT 'Shifts'                  , COUNT(*) FROM Shifts
UNION ALL SELECT 'Orders'                  , COUNT(*) FROM Orders
UNION ALL SELECT 'Transactions'            , COUNT(*) FROM Transactions
UNION ALL SELECT 'Subscriptions'           , COUNT(*) FROM Subscriptions
UNION ALL SELECT 'FreezeRecords'           , COUNT(*) FROM FreezeRecords
UNION ALL SELECT 'CheckIns'                , COUNT(*) FROM CheckIns
UNION ALL SELECT 'CustomerDeletionRequests', COUNT(*) FROM CustomerDeletionRequests
UNION ALL SELECT 'TimeSlots'               , COUNT(*) FROM TimeSlots
UNION ALL SELECT 'Sessions'                , COUNT(*) FROM Sessions
UNION ALL SELECT 'SessionSwapLogs'         , COUNT(*) FROM SessionSwapLogs
UNION ALL SELECT 'LeaveRequests'           , COUNT(*) FROM LeaveRequests
UNION ALL SELECT 'Facilities'              , COUNT(*) FROM Facilities
UNION ALL SELECT 'MaintenanceRequests'     , COUNT(*) FROM MaintenanceRequests
UNION ALL SELECT 'StaffAttendances'        , COUNT(*) FROM StaffAttendances
UNION ALL SELECT 'PrivateNotes'            , COUNT(*) FROM PrivateNotes
UNION ALL SELECT 'TrainingPlans'           , COUNT(*) FROM TrainingPlans
UNION ALL SELECT 'Feedbacks'               , COUNT(*) FROM Feedbacks
UNION ALL SELECT 'BlogPosts'               , COUNT(*) FROM BlogPosts
UNION ALL SELECT 'Notifications'           , COUNT(*) FROM Notifications
UNION ALL SELECT 'NotificationRecipients'  , COUNT(*) FROM NotificationRecipients
UNION ALL SELECT 'SystemConfigs'           , COUNT(*) FROM SystemConfigs
UNION ALL SELECT 'OperatingHourExceptions' , COUNT(*) FROM OperatingHourExceptions
UNION ALL SELECT 'AuditLogs'               , COUNT(*) FROM AuditLogs;
GO
