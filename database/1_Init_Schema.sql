/* ============================================================================
   GMS — Gym Management System
   FILE    : 1_Init_Schema.sql  ·  bản v26 (06/10/2026)
   MỤC ĐÍCH: Tạo toàn bộ schema SQL Server, chuẩn 3NF, khớp thay đổi nghiệp vụ v26
             (GBR / CBR / DBR / BR / P-01…P-22). 30 bảng, 7 cluster A–G.
   CHẠY    : Trên SQL Server 2019+. Muốn làm lại từ đầu: chạy 0_Reset_Database.sql trước.
             Thứ tự: 0_Reset_Database.sql → 1_Init_Schema.sql → 2_Seed_Data.sql
   v26    : JOB-04 chốt tự động, CheckedIn/Used, đánh giá PT theo tháng (DBR-66/67).
   ĐÃ GỘP  : migrations/001 (chuyên môn PT, lý do nghỉ việc, nhắc duyệt nghỉ phép)
             migrations/002 (DBR-65: 1 Person tối đa 1 hồ sơ khách + 1 hồ sơ NV còn hiệu lực)
             và toàn bộ thay đổi v25 — xem docs/database/SCHEMA_CHANGES_v25.md.
   QUY ƯỚC : - Tên bảng PascalCase số nhiều; cột PascalCase; PK = <TênBảngSốÍt>Id
             - Giá trị liệt kê lưu tiếng Anh, giao diện hiển thị tiếng Việt (GBR-01)
             - Mọi CHECK/DEFAULT bám rule; rule cần đọc nhiều bảng thì kiểm ở Service (GBR-13)
             - DEFAULT khai báo trong định nghĩa cột: Col TYPE NOT NULL CONSTRAINT DF_x DEFAULT (v)
             - RowVer ROWVERSION ở các bảng có màn sửa: lưu khi bản ghi đã đổi thì từ chối (GBR-32)
             - Không xóa vật lý bản ghi nghiệp vụ; "xóa" = chuyển trạng thái (GBR-05)
   ============================================================================ */

IF DB_ID('GMS') IS NULL
    CREATE DATABASE GMS;
GO

USE GMS;
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;   -- bắt buộc để tạo filtered index (sqlcmd mặc định OFF)
GO

/* ============================================================================
   CLUSTER A — DANH TÍNH & CON NGƯỜI
   Person = con người thật (họ tên, ngày sinh, SĐT, email, avatar).
   Customer và Employee = HAI VAI TRÒ của Person (DBR-65).
   Account = 1 tài khoản đăng nhập / Person, dùng cho MỌI vai trò chưa Inactive;
   người có ≥ 2 vai trò chọn vai trò sau khi đăng nhập (CBR-113).
   ============================================================================ */

-- Persons: phần chung của khách và nhân viên
CREATE TABLE Persons (
    PersonId    INT IDENTITY(1,1) NOT NULL,
    FullName    NVARCHAR(50)  NOT NULL,   -- DBR-01: 2–50 ký tự, chữ có dấu, khoảng trắng, ' và -
    DateOfBirth DATE          NOT NULL,   -- DBR-04; dùng cho tuổi tối thiểu PT (P-05)
    Phone       VARCHAR(10)   NULL,       -- DBR-22: bắt buộc trừ hồ sơ Dependent (DBR-24, DBR-49)
    Email       VARCHAR(100)  NULL,       -- DBR-23: bắt buộc khi có tài khoản
    Avatar      NVARCHAR(255) NULL,       -- DBR-64: chỉ hiển thị, không dùng check-in (CBR-37)
    IsReleased  BIT           NOT NULL CONSTRAINT DF_Persons_IsReleased DEFAULT (0), -- 1 = mọi vai trò Inactive → nhả SĐT + email (DBR-28)
    CreatedAt   DATETIME2(0)  NOT NULL CONSTRAINT DF_Persons_CreatedAt  DEFAULT (SYSDATETIME()),
    RowVer      ROWVERSION,

    CONSTRAINT PK_Persons PRIMARY KEY (PersonId),
    CONSTRAINT CK_Persons_Phone    CHECK (Phone IS NULL OR Phone LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'), -- DBR-17 (đầu số nhà mạng: P-16, kiểm ở Service)
    CONSTRAINT CK_Persons_Email    CHECK (Email IS NULL OR Email LIKE '%_@_%._%'),   -- DBR-18
    CONSTRAINT CK_Persons_FullName CHECK (LEN(LTRIM(RTRIM(FullName))) >= 2),
    CONSTRAINT CK_Persons_Dob      CHECK (DateOfBirth >= '1900-01-01')               -- "không ở tương lai, ≤ 100 tuổi" phụ thuộc ngày hiện tại → kiểm ở Service
);
GO

-- DBR-22, DBR-23: SĐT / email duy nhất giữa các Person CÒN HOẠT ĐỘNG (chưa nhả)
CREATE UNIQUE INDEX UX_Persons_Phone_Active ON Persons(Phone) WHERE Phone IS NOT NULL AND IsReleased = 0;
CREATE UNIQUE INDEX UX_Persons_Email_Active ON Persons(Email) WHERE Email IS NOT NULL AND IsReleased = 0;
GO

-- Customers: vai trò KHÁCH của một Person
CREATE TABLE Customers (
    CustomerId           INT IDENTITY(1,1) NOT NULL,
    PersonId             INT           NOT NULL,
    CustomerCode         VARCHAR(12)   NOT NULL,   -- KH-nnnn, hệ thống sinh, không bao giờ đổi (DBR-09)
    CheckInPhoto         NVARCHAR(255) NULL,       -- Lễ tân chụp tại quầy (CBR-35, CBR-38)
    CustomerType         VARCHAR(10)   NOT NULL,   -- Self | Dependent — không đổi được sau khi tạo (BR-18)
    GuardianName         NVARCHAR(50)  NULL,       -- 4 cột giám hộ: CHỈ hồ sơ Dependent (DBR-25)
    GuardianRelationship NVARCHAR(30)  NULL,
    GuardianPhone        VARCHAR(10)   NULL,       -- được trùng giữa các hồ sơ (anh em chung số bố mẹ — DBR-24)
    GuardianPhone2       VARCHAR(10)   NULL,
    Status               VARCHAR(10)   NOT NULL CONSTRAINT DF_Customers_Status   DEFAULT ('New'), -- DBR-34
    JoinedAt             DATETIME2(0)  NOT NULL CONSTRAINT DF_Customers_JoinedAt DEFAULT (SYSDATETIME()),
    DeletedAt            DATETIME2(0)  NULL,       -- xóa mềm (GBR-05)
    RowVer               ROWVERSION,

    CONSTRAINT PK_Customers PRIMARY KEY (CustomerId),
    CONSTRAINT UQ_Customers_CustomerCode UNIQUE (CustomerCode),
    CONSTRAINT FK_Customers_Persons FOREIGN KEY (PersonId) REFERENCES Persons(PersonId),
    CONSTRAINT CK_Customers_Code   CHECK (CustomerCode LIKE 'KH-[0-9][0-9][0-9][0-9]'),
    CONSTRAINT CK_Customers_Type   CHECK (CustomerType IN ('Self','Dependent')),
    CONSTRAINT CK_Customers_Status CHECK (Status IN ('New','Active','Frozen','Expired','Inactive')),
    -- Self: không có giám hộ. Dependent: bắt buộc tên + quan hệ + ít nhất 1 số (DBR-25)
    CONSTRAINT CK_Customers_Guardian CHECK (
        (CustomerType = 'Self' AND GuardianName IS NULL AND GuardianRelationship IS NULL
            AND GuardianPhone IS NULL AND GuardianPhone2 IS NULL)
        OR (CustomerType = 'Dependent' AND GuardianName IS NOT NULL
            AND GuardianRelationship IS NOT NULL AND GuardianPhone IS NOT NULL)
    ),
    CONSTRAINT CK_Customers_GuardianPhone CHECK (
        (GuardianPhone  IS NULL OR GuardianPhone  LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')
        AND (GuardianPhone2 IS NULL OR GuardianPhone2 LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')
        AND (GuardianPhone2 IS NULL OR GuardianPhone2 <> GuardianPhone)
    ),
    -- Inactive là trạng thái cuối, bắt buộc có thời điểm xóa (DBR-35)
    CONSTRAINT CK_Customers_Deleted CHECK (
        (Status = 'Inactive' AND DeletedAt IS NOT NULL) OR (Status <> 'Inactive' AND DeletedAt IS NULL)
    )
);
GO

-- Employees: vai trò NHÂN VIÊN của một Person
CREATE TABLE Employees (
    EmployeeId        INT IDENTITY(1,1) NOT NULL,
    PersonId          INT           NOT NULL,
    EmployeeCode      VARCHAR(12)   NOT NULL,   -- NV-nnnn (DBR-09)
    Position          VARCHAR(15)   NOT NULL,   -- Admin | Manager | PT | Receptionist — chỉ Admin đổi (CBR-109)
    HireDate          DATE          NOT NULL,
    CommissionRate    TINYINT       NULL,       -- % hoa hồng, CHỈ PT (DBR-07)
    Specialty         NVARCHAR(100) NULL,       -- chỉ PT, hiện trang chủ
    YearsOfExperience TINYINT       NULL,       -- chỉ PT
    Status            VARCHAR(10)   NOT NULL CONSTRAINT DF_Employees_Status DEFAULT ('Working'), -- Working | Resigned (Resigned = Inactive — DBR-65)
    DeletedAt         DATETIME2(0)  NULL,       -- thời điểm nghỉ việc
    ResignationReason NVARCHAR(500) NULL,       -- GBR-09
    RowVer            ROWVERSION,

    CONSTRAINT PK_Employees PRIMARY KEY (EmployeeId),
    CONSTRAINT UQ_Employees_EmployeeCode UNIQUE (EmployeeCode),
    CONSTRAINT FK_Employees_Persons FOREIGN KEY (PersonId) REFERENCES Persons(PersonId),
    CONSTRAINT CK_Employees_Code     CHECK (EmployeeCode LIKE 'NV-[0-9][0-9][0-9][0-9]'),
    CONSTRAINT CK_Employees_Position CHECK (Position IN ('Admin','Manager','PT','Receptionist')),
    CONSTRAINT CK_Employees_Status   CHECK (Status IN ('Working','Resigned')),
    CONSTRAINT CK_Employees_Rate     CHECK (CommissionRate IS NULL OR CommissionRate BETWEEN 0 AND 100),
    CONSTRAINT CK_Employees_RateByPosition CHECK (
        (Position = 'PT' AND CommissionRate IS NOT NULL) OR (Position <> 'PT' AND CommissionRate IS NULL)
    ),
    CONSTRAINT CK_Employees_PtProfile  CHECK (Position = 'PT' OR (Specialty IS NULL AND YearsOfExperience IS NULL)),
    CONSTRAINT CK_Employees_Experience CHECK (YearsOfExperience IS NULL OR YearsOfExperience <= 60),
    -- Resigned: bắt buộc thời điểm + lý do 5–500 ký tự; Working: không có
    CONSTRAINT CK_Employees_Resignation CHECK (
        (Status = 'Resigned' AND DeletedAt IS NOT NULL AND ResignationReason IS NOT NULL
            AND LEN(LTRIM(RTRIM(ResignationReason))) >= 5)
        OR (Status = 'Working' AND DeletedAt IS NULL AND ResignationReason IS NULL)
    )
);
GO

-- DBR-65: mỗi Person tối đa 1 hồ sơ khách chưa Inactive và 1 hồ sơ nhân viên đang Working.
-- Hồ sơ cũ Inactive / Resigned không chặn tạo hồ sơ mới trên cùng Person (CBR-114).
CREATE UNIQUE INDEX UX_Customers_PersonId_Active  ON Customers(PersonId) WHERE Status <> 'Inactive';
CREATE UNIQUE INDEX UX_Employees_PersonId_Working ON Employees(PersonId) WHERE Status = 'Working';
GO

-- Accounts: 1 tài khoản / Person, phục vụ mọi vai trò chưa Inactive (DBR-65, CBR-113)
CREATE TABLE Accounts (
    AccountId        INT IDENTITY(1,1) NOT NULL,
    PersonId         INT           NOT NULL,
    PasswordHash     VARCHAR(255)  NULL,      -- NULL = Lễ tân tạo, khách chưa đặt mật khẩu (CBR-24, CBR-31)
    Status           VARCHAR(10)   NOT NULL CONSTRAINT DF_Accounts_Status    DEFAULT ('Active'), -- Active | Locked (Admin khóa) | Inactive (mọi vai trò Inactive — CBR-86, cuối)
    FailedLoginCount TINYINT       NOT NULL CONSTRAINT DF_Accounts_Failed    DEFAULT (0),        -- BR-01, BR-02
    LockedUntil      DATETIME2(0)  NULL,      -- khóa TẠM do sai mật khẩu, không đổi Status
    LastLoginAt      DATETIME2(0)  NULL,
    CreatedAt        DATETIME2(0)  NOT NULL CONSTRAINT DF_Accounts_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_Accounts PRIMARY KEY (AccountId),
    CONSTRAINT UQ_Accounts_PersonId UNIQUE (PersonId),
    CONSTRAINT FK_Accounts_Persons FOREIGN KEY (PersonId) REFERENCES Persons(PersonId),
    CONSTRAINT CK_Accounts_Status CHECK (Status IN ('Active','Locked','Inactive'))
);
GO

-- VerificationCodes: mã 6 số gửi qua email (GBR-15). Tách bảng vì lúc đăng ký CHƯA có Account.
-- v25: bỏ PasswordSetup + TokenHash (luồng link đã bỏ — CBR-31); thêm PhoneChange (CBR-110).
CREATE TABLE VerificationCodes (
    VerificationCodeId INT IDENTITY(1,1) NOT NULL,
    AccountId          INT          NULL,     -- NULL khi đăng ký / kích hoạt (chưa có tài khoản)
    Email              VARCHAR(100) NOT NULL, -- email NHẬN mã
    Purpose            VARCHAR(15)  NOT NULL, -- SignUp | Claim | Recovery | EmailChange | PhoneChange
    Code               CHAR(6)      NOT NULL, -- DBR-03
    SentAt             DATETIME2(0) NOT NULL,
    ExpiresAt          DATETIME2(0) NOT NULL, -- SentAt + P-15
    FailedAttempts     TINYINT      NOT NULL CONSTRAINT DF_VerificationCodes_Failed DEFAULT (0), -- CBR-94: đủ P-17 → hủy mã + khóa P-18
    LockedUntil        DATETIME2(0) NULL,
    UsedAt             DATETIME2(0) NULL,

    CONSTRAINT PK_VerificationCodes PRIMARY KEY (VerificationCodeId),
    CONSTRAINT FK_VerificationCodes_Accounts FOREIGN KEY (AccountId) REFERENCES Accounts(AccountId),
    CONSTRAINT CK_VerificationCodes_Purpose CHECK (Purpose IN ('SignUp','Claim','Recovery','EmailChange','PhoneChange')),
    CONSTRAINT CK_VerificationCodes_Code    CHECK (Code LIKE '[0-9][0-9][0-9][0-9][0-9][0-9]'),
    CONSTRAINT CK_VerificationCodes_Expiry  CHECK (ExpiresAt > SentAt),
    -- Mã cho tài khoản đã có thì phải gắn tài khoản
    CONSTRAINT CK_VerificationCodes_Account CHECK (
        (Purpose IN ('Recovery','EmailChange','PhoneChange') AND AccountId IS NOT NULL)
        OR (Purpose IN ('SignUp','Claim') AND AccountId IS NULL)
    )
);
GO

-- Index cluster A
CREATE INDEX IX_Persons_Phone ON Persons(Phone);                       -- Lễ tân tra SĐT ở mọi luồng
CREATE INDEX IX_Persons_Email ON Persons(Email);                       -- đăng nhập / tra trùng email
CREATE INDEX IX_Customers_Status ON Customers(Status);
CREATE INDEX IX_Customers_GuardianPhone ON Customers(GuardianPhone) WHERE GuardianPhone IS NOT NULL; -- tra hồ sơ Dependent theo số giám hộ (CBR-33)
CREATE INDEX IX_Employees_PositionStatus ON Employees(Position, Status);
CREATE INDEX IX_Accounts_LockedUntil ON Accounts(LockedUntil) WHERE LockedUntil IS NOT NULL;
CREATE INDEX IX_VerificationCodes_EmailPurpose ON VerificationCodes(Email, Purpose, SentAt DESC);
GO

/* ============================================================================
   CLUSTER B — BÁN HÀNG, THANH TOÁN, QUYỀN DÙNG
   Packages → Orders → Transactions → (Paid) → Subscriptions
   ============================================================================ */

-- Packages: danh mục gói. Thời hạn lưu theo ĐƠN VỊ (ngày / tháng) vì GBR-03 tính theo tháng lịch:
-- 31/01 + 1 tháng = 28/02 (hoặc 29/02), không phải +30 ngày.
CREATE TABLE Packages (
    PackageId             INT IDENTITY(1,1) NOT NULL,
    PackageName           NVARCHAR(100)  NOT NULL, -- DBR-11: 3–100 ký tự, duy nhất giữa gói đang bán
    Description           NVARCHAR(500)  NULL,
    Benefits              NVARCHAR(1000) NULL,     -- DBR-15: tối đa 10 dòng, mỗi dòng 3–100 ký tự (kiểm ở Service)
    PackageType           VARCHAR(15)    NOT NULL, -- Membership | DayPass | PTSingle | PTCouple | SingleSession
    DurationUnit          VARCHAR(5)     NULL,     -- Day | Month (DBR-19)
    DurationValue         TINYINT        NULL,     -- gói ngày = 1 Day; gói tập: tháng theo P-04; gói PT: tháng theo P-21
    EstimatedSessionCount TINYINT        NULL,     -- CHỈ để hiển thị "khoảng n buổi" cho gói PT (DBR-53)
    Price                 INT            NOT NULL, -- VND, đã gồm VAT (GBR-02)
    IsActive              BIT            NOT NULL CONSTRAINT DF_Packages_IsActive  DEFAULT (1), -- 0 = ngừng bán (CBR-97)
    CreatedAt             DATETIME2(0)   NOT NULL CONSTRAINT DF_Packages_CreatedAt DEFAULT (SYSDATETIME()),
    RowVer                ROWVERSION,

    CONSTRAINT PK_Packages PRIMARY KEY (PackageId),
    CONSTRAINT CK_Packages_Type  CHECK (PackageType IN ('Membership','DayPass','PTSingle','PTCouple','SingleSession')),
    CONSTRAINT CK_Packages_Name  CHECK (LEN(LTRIM(RTRIM(PackageName))) >= 3),
    CONSTRAINT CK_Packages_Price CHECK (Price > 0),
    CONSTRAINT CK_Packages_Duration CHECK (
        (PackageType = 'DayPass'       AND DurationUnit IS NOT NULL AND DurationValue IS NOT NULL AND DurationUnit = 'Day' AND DurationValue = 1)
        OR (PackageType = 'Membership' AND DurationUnit IS NOT NULL AND DurationValue IS NOT NULL AND DurationUnit = 'Month' AND DurationValue BETWEEN 1 AND 36)
        OR (PackageType IN ('PTSingle','PTCouple') AND DurationUnit IS NOT NULL AND DurationValue IS NOT NULL AND DurationUnit = 'Month' AND DurationValue BETWEEN 1 AND 12)
        OR (PackageType = 'SingleSession' AND DurationUnit IS NULL AND DurationValue IS NULL)
    ),
    CONSTRAINT CK_Packages_EstSession CHECK (
        (PackageType IN ('PTSingle','PTCouple') AND EstimatedSessionCount IS NOT NULL AND EstimatedSessionCount > 0)
        OR (PackageType NOT IN ('PTSingle','PTCouple') AND EstimatedSessionCount IS NULL)
    )
);
GO

-- DBR-11: tên chỉ cần duy nhất giữa các gói ĐANG BÁN — gói đã ngừng có thể trùng tên gói mới
CREATE UNIQUE INDEX UX_Packages_Name_Active ON Packages(PackageName) WHERE IsActive = 1;
GO

-- Vouchers: mã giảm giá — KHÔNG áp cho gói PT và buổi lẻ (CBR-76)
CREATE TABLE Vouchers (
    VoucherId           INT IDENTITY(1,1) NOT NULL,
    VoucherCode         VARCHAR(20)  NOT NULL, -- DBR-10
    DiscountType        VARCHAR(10)  NOT NULL, -- Percent | Amount
    DiscountValue       INT          NOT NULL,
    StartDate           DATE         NOT NULL,
    EndDate             DATE         NOT NULL, -- ngày cuối còn dùng (GBR-03)
    MaxUsage            INT          NOT NULL, -- tổng lượt
    MaxUsagePerCustomer TINYINT      NULL,     -- NULL = không giới hạn (CBR-74)
    UsedCount           INT          NOT NULL CONSTRAINT DF_Vouchers_UsedCount     DEFAULT (0), -- BR-43 → 45
    MinOrderValue       INT          NOT NULL CONSTRAINT DF_Vouchers_MinOrderValue DEFAULT (0), -- so với giá TRƯỚC giảm (CBR-73)
    ApplicableScope     VARCHAR(25)  NOT NULL CONSTRAINT DF_Vouchers_Scope         DEFAULT ('MembershipAndDayPass'), -- CBR-76
    IsActive            BIT          NOT NULL CONSTRAINT DF_Vouchers_IsActive      DEFAULT (1), -- dùng rồi chỉ tắt, không xóa (CBR-98)
    CreatedAt           DATETIME2(0) NOT NULL CONSTRAINT DF_Vouchers_CreatedAt     DEFAULT (SYSDATETIME()),
    RowVer              ROWVERSION,

    CONSTRAINT PK_Vouchers PRIMARY KEY (VoucherId),
    CONSTRAINT UQ_Vouchers_Code UNIQUE (VoucherCode),
    CONSTRAINT CK_Vouchers_Code     CHECK (LEN(VoucherCode) BETWEEN 4 AND 20 AND VoucherCode NOT LIKE '%[^A-Z0-9]%'),
    CONSTRAINT CK_Vouchers_MaxUsage CHECK (MaxUsage > 0 AND (MaxUsagePerCustomer IS NULL OR MaxUsagePerCustomer > 0)),
    CONSTRAINT CK_Vouchers_Type     CHECK (DiscountType IN ('Percent','Amount')),
    CONSTRAINT CK_Vouchers_Scope    CHECK (ApplicableScope IN ('MembershipAndDayPass','MembershipOnly')),
    CONSTRAINT CK_Vouchers_Dates    CHECK (EndDate >= StartDate),
    CONSTRAINT CK_Vouchers_Used     CHECK (UsedCount >= 0 AND UsedCount <= MaxUsage), -- DBR-14
    CONSTRAINT CK_Vouchers_MinOrder CHECK (MinOrderValue >= 0),
    CONSTRAINT CK_Vouchers_Value    CHECK (
        (DiscountType = 'Percent' AND DiscountValue BETWEEN 1 AND 100)
        OR (DiscountType = 'Amount' AND DiscountValue > 0)
    )
);
GO

-- Shifts: 2 ca / ngày mở cửa, sinh tự động (CBR-104). Đối soát tiền mặt (BR-46, DBR-55).
CREATE TABLE Shifts (
    ShiftId            INT IDENTITY(1,1) NOT NULL,
    WorkDate           DATE          NOT NULL,
    ShiftType          VARCHAR(10)   NOT NULL, -- Morning | Afternoon
    OpenedByEmployeeId INT           NULL,
    ClosedByEmployeeId INT           NULL,
    Status             VARCHAR(10)   NOT NULL CONSTRAINT DF_Shifts_Status DEFAULT ('NotOpened'), -- NotOpened | Open | Closed
    OpenedAt           DATETIME2(0)  NULL,
    ClosedAt           DATETIME2(0)  NULL,
    ExpectedCash       INT           NULL,     -- DBR-55, chốt lúc đóng ca
    CountedCash        INT           NULL,
    VarianceReason     NVARCHAR(500) NULL,     -- bắt buộc khi lệch (BR-46)

    CONSTRAINT PK_Shifts PRIMARY KEY (ShiftId),
    CONSTRAINT UQ_Shifts_DateType UNIQUE (WorkDate, ShiftType),
    CONSTRAINT FK_Shifts_OpenedBy FOREIGN KEY (OpenedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Shifts_ClosedBy FOREIGN KEY (ClosedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Shifts_Type   CHECK (ShiftType IN ('Morning','Afternoon')),
    CONSTRAINT CK_Shifts_Status CHECK (Status IN ('NotOpened','Open','Closed')),
    CONSTRAINT CK_Shifts_Opened CHECK (
        (Status = 'NotOpened' AND OpenedByEmployeeId IS NULL AND OpenedAt IS NULL)
        OR (Status <> 'NotOpened' AND OpenedByEmployeeId IS NOT NULL AND OpenedAt IS NOT NULL)
    ),
    CONSTRAINT CK_Shifts_Closed CHECK (
        (Status = 'Closed' AND ClosedByEmployeeId IS NOT NULL AND ClosedAt IS NOT NULL
            AND ExpectedCash IS NOT NULL AND CountedCash IS NOT NULL
            AND (CountedCash = ExpectedCash OR (VarianceReason IS NOT NULL AND LEN(LTRIM(RTRIM(VarianceReason))) >= 5)))
        OR (Status <> 'Closed' AND ClosedByEmployeeId IS NULL AND ClosedAt IS NULL AND CountedCash IS NULL)
    ),
    CONSTRAINT CK_Shifts_Cash CHECK ((ExpectedCash IS NULL OR ExpectedCash >= 0) AND (CountedCash IS NULL OR CountedCash >= 0))
);
GO

-- Orders: 1 lần mua 1 gói. Snapshot giá / mô tả / quyền lợi / VAT lúc mua (DBR-50, DBR-57, CBR-09).
CREATE TABLE Orders (
    OrderId                    INT IDENTITY(1,1) NOT NULL,
    OrderCode                  VARCHAR(20)    NOT NULL,  -- DH-yyyy-nnnn
    HolderCustomerId           INT            NOT NULL,  -- người đứng tên
    PartnerCustomerId          INT            NULL,      -- người đi kèm, CHỈ gói couple
    PackageId                  INT            NOT NULL,
    VoucherId                  INT            NULL,      -- tối đa 1 voucher (BR-42)
    CreatedByEmployeeId        INT            NULL,      -- NULL với đơn online
    ShiftId                    INT            NULL,      -- đơn tại quầy thuộc ca nào (CBR-105)
    Channel                    VARCHAR(10)    NOT NULL,  -- Counter | Online
    PackageNameSnapshot        NVARCHAR(100)  NOT NULL,
    PackageDescriptionSnapshot NVARCHAR(500)  NULL,
    PackageBenefitsSnapshot    NVARCHAR(1000) NULL,      -- v25: DBR-50 chép cả quyền lợi
    UnitPriceSnapshot          INT            NOT NULL,  -- giá lúc mua, đã gồm VAT
    DiscountAmount             INT            NOT NULL CONSTRAINT DF_Orders_Discount  DEFAULT (0),
    VatRateSnapshot            TINYINT        NOT NULL,  -- P-10 lúc mua
    VatAmount                  INT            NOT NULL,  -- DBR-57: (giá − giảm) × VAT ÷ (100 + VAT), làm tròn đồng
    Status                     VARCHAR(10)    NOT NULL CONSTRAINT DF_Orders_Status    DEFAULT ('Pending'), -- DBR-39, DBR-40
    PaymentDueAt               DATETIME2(0)   NULL,      -- online: CreatedAt + P-09, cũng là hạn giữ slot (CBR-40). Tại quầy: NULL
    CreatedAt                  DATETIME2(0)   NOT NULL CONSTRAINT DF_Orders_CreatedAt DEFAULT (SYSDATETIME()),
    PaidAt                     DATETIME2(0)   NULL,
    CancelType                 VARCHAR(25)    NULL,      -- DBR-43
    CancelledAt                DATETIME2(0)   NULL,
    CancelReason               NVARCHAR(500)  NULL,

    CONSTRAINT PK_Orders PRIMARY KEY (OrderId),
    CONSTRAINT UQ_Orders_OrderCode UNIQUE (OrderCode),
    CONSTRAINT FK_Orders_Holder    FOREIGN KEY (HolderCustomerId)    REFERENCES Customers(CustomerId),
    CONSTRAINT FK_Orders_Partner   FOREIGN KEY (PartnerCustomerId)   REFERENCES Customers(CustomerId),
    CONSTRAINT FK_Orders_Packages  FOREIGN KEY (PackageId)           REFERENCES Packages(PackageId),
    CONSTRAINT FK_Orders_Vouchers  FOREIGN KEY (VoucherId)           REFERENCES Vouchers(VoucherId),
    CONSTRAINT FK_Orders_CreatedBy FOREIGN KEY (CreatedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Orders_Shifts    FOREIGN KEY (ShiftId)             REFERENCES Shifts(ShiftId),
    CONSTRAINT CK_Orders_Code      CHECK (OrderCode LIKE 'DH-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]'),
    CONSTRAINT CK_Orders_Channel   CHECK (Channel IN ('Counter','Online')),
    CONSTRAINT CK_Orders_Status    CHECK (Status IN ('Pending','Paid','Cancelled')),
    CONSTRAINT CK_Orders_CancelType CHECK (CancelType IS NULL OR CancelType IN
        ('PaymentTimeout','ReplacedByNewOrder','InvalidPayment','CancelledByReceptionist')),
    CONSTRAINT CK_Orders_Price     CHECK (UnitPriceSnapshot > 0),
    CONSTRAINT CK_Orders_Vat       CHECK (VatRateSnapshot BETWEEN 0 AND 20 AND VatAmount >= 0),
    CONSTRAINT CK_Orders_Discount  CHECK (DiscountAmount >= 0 AND DiscountAmount <= UnitPriceSnapshot), -- DBR-06
    CONSTRAINT CK_Orders_VoucherDiscount CHECK (VoucherId IS NOT NULL OR DiscountAmount = 0),         -- chỉ voucher mới giảm giá
    CONSTRAINT CK_Orders_Partner   CHECK (PartnerCustomerId IS NULL OR PartnerCustomerId <> HolderCustomerId),
    -- Tại quầy: có người lập + ca, không có hạn online. Online: ngược lại, bắt buộc có hạn (CBR-70)
    CONSTRAINT CK_Orders_ChannelFields CHECK (
        (Channel = 'Counter' AND CreatedByEmployeeId IS NOT NULL AND ShiftId IS NOT NULL AND PaymentDueAt IS NULL)
        OR (Channel = 'Online' AND CreatedByEmployeeId IS NULL AND ShiftId IS NULL AND PaymentDueAt IS NOT NULL)
    ),
    CONSTRAINT CK_Orders_Cancelled CHECK (
        (Status = 'Cancelled' AND CancelType IS NOT NULL AND CancelledAt IS NOT NULL)
        OR (Status <> 'Cancelled' AND CancelType IS NULL AND CancelledAt IS NULL AND CancelReason IS NULL)
    ),
    -- Lễ tân hủy phải ghi lý do ≥ 5 ký tự (GBR-09); chỉ đơn tại quầy (CBR-11, CBR-78)
    CONSTRAINT CK_Orders_ReceptionistCancel CHECK (
        CancelType IS NULL OR CancelType <> 'CancelledByReceptionist'
        OR (Channel = 'Counter' AND CancelReason IS NOT NULL AND LEN(LTRIM(RTRIM(CancelReason))) >= 5)
    ),
    -- Hủy loại "quá hạn" / "bị thay thế" chỉ có ở đơn online (CBR-70, CBR-71)
    CONSTRAINT CK_Orders_OnlineCancel CHECK (
        CancelType IS NULL OR CancelType NOT IN ('PaymentTimeout','ReplacedByNewOrder') OR Channel = 'Online'
    ),
    -- Paid → Cancelled chỉ theo CBR-11, nên đơn đã hủy vẫn có thể có PaidAt
    CONSTRAINT CK_Orders_Paid CHECK (
        (Status = 'Paid' AND PaidAt IS NOT NULL)
        OR (Status = 'Pending' AND PaidAt IS NULL)
        OR (Status = 'Cancelled' AND (PaidAt IS NULL OR CancelType = 'CancelledByReceptionist'))
    )
);
GO

-- Transactions: MỖI LẦN THỬ thanh toán là 1 dòng (DBR-41, DBR-42)
CREATE TABLE Transactions (
    TransactionId        INT IDENTITY(1,1) NOT NULL,
    OrderId              INT           NOT NULL,
    Amount               INT           NOT NULL,  -- số phải trả lúc tạo giao dịch
    PaymentMethod        VARCHAR(15)   NOT NULL,  -- Cash | Transfer | OnlineGateway
    GatewayReference     VARCHAR(50)   NULL,      -- mã VNPay / mã chuyển khoản
    ReceivedByEmployeeId INT           NULL,      -- v25: Lễ tân nhận tiền / xác nhận chuyển khoản (CBR-77)
    Status               VARCHAR(10)   NOT NULL CONSTRAINT DF_Transactions_Status      DEFAULT ('Pending'),
    FailureReason        NVARCHAR(255) NULL,
    NeedsRefund          BIT           NOT NULL CONSTRAINT DF_Transactions_NeedsRefund DEFAULT (0), -- CBR-72 → danh sách hoàn tiền của Manager
    RefundedAt           DATETIME2(0)  NULL,      -- v25: Manager đánh dấu đã hoàn ngoài hệ thống (BR-47)
    RefundedByEmployeeId INT           NULL,
    RefundNote           NVARCHAR(500) NULL,
    CreatedAt            DATETIME2(0)  NOT NULL CONSTRAINT DF_Transactions_CreatedAt   DEFAULT (SYSDATETIME()),
    ExpiresAt            DATETIME2(0)  NULL,      -- hạn link thanh toán (5 phút, không vượt hạn đơn)
    CompletedAt          DATETIME2(0)  NULL,

    CONSTRAINT PK_Transactions PRIMARY KEY (TransactionId),
    CONSTRAINT FK_Transactions_Orders     FOREIGN KEY (OrderId)              REFERENCES Orders(OrderId),
    CONSTRAINT FK_Transactions_Receiver   FOREIGN KEY (ReceivedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Transactions_Refunder   FOREIGN KEY (RefundedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Transactions_Method CHECK (PaymentMethod IN ('Cash','Transfer','OnlineGateway')),
    CONSTRAINT CK_Transactions_Status CHECK (Status IN ('Pending','Success','Failed','Expired')),
    CONSTRAINT CK_Transactions_Amount CHECK (Amount > 0),
    -- Tiền mặt / chuyển khoản tại quầy thành công phải có người nhận; cổng online thì không
    CONSTRAINT CK_Transactions_Receiver CHECK (
        (PaymentMethod = 'OnlineGateway' AND ReceivedByEmployeeId IS NULL)
        OR (PaymentMethod <> 'OnlineGateway' AND (Status <> 'Success' OR ReceivedByEmployeeId IS NOT NULL))
    ),
    CONSTRAINT CK_Transactions_Online CHECK (PaymentMethod <> 'OnlineGateway' OR ExpiresAt IS NOT NULL),
    CONSTRAINT CK_Transactions_Completed CHECK (
        (Status IN ('Success','Failed') AND CompletedAt IS NOT NULL) OR (Status IN ('Pending','Expired') AND CompletedAt IS NULL)
    ),
    -- Chỉ giao dịch Success mới cần hoàn; đã hoàn thì phải có người + thời điểm
    CONSTRAINT CK_Transactions_Refund CHECK (
        (NeedsRefund = 0 AND RefundedAt IS NULL AND RefundedByEmployeeId IS NULL AND RefundNote IS NULL)
        OR (NeedsRefund = 1 AND Status = 'Success'
            AND ((RefundedAt IS NULL AND RefundedByEmployeeId IS NULL)
                 OR (RefundedAt IS NOT NULL AND RefundedByEmployeeId IS NOT NULL)))
    )
);
GO

-- DBR-42: mỗi đơn tối đa 1 giao dịch Success và 1 giao dịch Pending
CREATE UNIQUE INDEX UX_Transactions_OneSuccessPerOrder ON Transactions(OrderId) WHERE Status = 'Success';
CREATE UNIQUE INDEX UX_Transactions_OnePendingPerOrder ON Transactions(OrderId) WHERE Status = 'Pending';
GO

-- Subscriptions: QUYỀN DÙNG sinh khi đơn Paid. Check-in và buổi tập tiêu thụ cái này, không phải Orders.
--   Membership   : Active | Frozen | Expired | Cancelled   (StartDate có thể ở tương lai — DBR-52)
--   DayPass      : Active | Used | Expired | Cancelled
--   PTSingle/Couple: Active | Expired | Cancelled          (CBR-55)
--   SingleSession: Active | Used | Expired | Cancelled
CREATE TABLE Subscriptions (
    SubscriptionId        INT IDENTITY(1,1) NOT NULL,
    OrderId               INT          NOT NULL,
    CustomerId            INT          NOT NULL,  -- người đứng tên (couple: người đi kèm nằm ở Orders.PartnerCustomerId)
    SubscriptionType      VARCHAR(15)  NOT NULL,
    PtEmployeeId          INT          NULL,      -- PT phụ trách, chỉ gói PT / buổi lẻ
    StartDate             DATE         NOT NULL,
    EndDate               DATE         NOT NULL,  -- NGÀY CUỐI còn dùng (GBR-03)
    FixedStartTime        TIME(0)      NULL,      -- v25: khung giờ cố định của gói PT (CBR-43, CBR-52)
    GeneratedSessionCount TINYINT      NULL,      -- số buổi thực sinh, chốt khi Paid (BR-40, DBR-53)
    SwapsUsed             TINYINT      NOT NULL CONSTRAINT DF_Subscriptions_Swaps  DEFAULT (0),
    MaxSwapsSnapshot      TINYINT      NULL,      -- hạn mức đổi buổi = P-08 × số tháng của gói, chốt lúc bán — đổi P-08 không áp ngược (BR-73, GBR-04)
    FreezeUsed            TINYINT      NOT NULL CONSTRAINT DF_Subscriptions_Freeze DEFAULT (0),
    MaxFreezesSnapshot    TINYINT      NULL,      -- v25: P-01 lúc bán (GBR-04)
    Status                VARCHAR(10)  NOT NULL CONSTRAINT DF_Subscriptions_Status DEFAULT ('Active'),

    CONSTRAINT PK_Subscriptions PRIMARY KEY (SubscriptionId),
    CONSTRAINT UQ_Subscriptions_OrderId UNIQUE (OrderId),
    CONSTRAINT FK_Subscriptions_Orders    FOREIGN KEY (OrderId)      REFERENCES Orders(OrderId),
    CONSTRAINT FK_Subscriptions_Customers FOREIGN KEY (CustomerId)   REFERENCES Customers(CustomerId),
    CONSTRAINT FK_Subscriptions_Pt        FOREIGN KEY (PtEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Subscriptions_Type   CHECK (SubscriptionType IN ('Membership','DayPass','PTSingle','PTCouple','SingleSession')),
    CONSTRAINT CK_Subscriptions_Status CHECK (Status IN ('Active','Frozen','Used','Expired','Cancelled')),
    CONSTRAINT CK_Subscriptions_StatusByType CHECK (
        (Status IN ('Active','Expired','Cancelled'))
        OR (Status = 'Frozen'    AND SubscriptionType = 'Membership')      -- BR-21
        OR (Status = 'Used' AND SubscriptionType IN ('DayPass','SingleSession')) -- BR-29
    ),
    CONSTRAINT CK_Subscriptions_Dates CHECK (EndDate >= StartDate),
    CONSTRAINT CK_Subscriptions_OneDay CHECK (SubscriptionType NOT IN ('DayPass','SingleSession') OR EndDate = StartDate),
    CONSTRAINT CK_Subscriptions_Pt CHECK (
        (SubscriptionType IN ('PTSingle','PTCouple','SingleSession') AND PtEmployeeId IS NOT NULL)
        OR (SubscriptionType IN ('Membership','DayPass') AND PtEmployeeId IS NULL)
    ),
    -- Gói PT: có khung giờ cố định, trần swap, số buổi. Buổi lẻ: đúng 1 buổi. Gói tập: trần bảo lưu.
    CONSTRAINT CK_Subscriptions_PtFields CHECK (
        (SubscriptionType IN ('PTSingle','PTCouple') AND FixedStartTime IS NOT NULL
            AND MaxSwapsSnapshot IS NOT NULL AND GeneratedSessionCount IS NOT NULL)
        OR (SubscriptionType = 'SingleSession' AND FixedStartTime IS NULL AND MaxSwapsSnapshot IS NULL
            AND GeneratedSessionCount IS NOT NULL AND GeneratedSessionCount = 1)
        OR (SubscriptionType IN ('Membership','DayPass') AND FixedStartTime IS NULL AND MaxSwapsSnapshot IS NULL
            AND GeneratedSessionCount IS NULL)
    ),
    CONSTRAINT CK_Subscriptions_Freeze CHECK (
        (SubscriptionType = 'Membership' AND MaxFreezesSnapshot IS NOT NULL AND FreezeUsed <= MaxFreezesSnapshot)
        OR (SubscriptionType <> 'Membership' AND MaxFreezesSnapshot IS NULL AND FreezeUsed = 0)
    ),
    CONSTRAINT CK_Subscriptions_Swaps CHECK (
        SwapsUsed = 0 OR (MaxSwapsSnapshot IS NOT NULL AND SwapsUsed <= MaxSwapsSnapshot)    -- BR-73, BR-74
    )
);
GO

-- FreezeRecords: mỗi lần bảo lưu gói tập (UC-18)
CREATE TABLE FreezeRecords (
    FreezeRecordId      INT IDENTITY(1,1) NOT NULL,
    SubscriptionId      INT           NOT NULL,
    HandledByEmployeeId INT           NOT NULL,
    StartDate           DATE          NOT NULL,  -- sớm nhất là ngày mai (DBR-13)
    PlannedEndDate      DATE          NOT NULL,  -- tối đa P-02 và số ngày gói còn lại
    ActualEndDate       DATE          NULL,      -- NULL = đang bảo lưu; < PlannedEndDate = hủy sớm (BR-26)
    Reason              NVARCHAR(500) NOT NULL,
    CreatedAt           DATETIME2(0)  NOT NULL CONSTRAINT DF_FreezeRecords_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_FreezeRecords PRIMARY KEY (FreezeRecordId),
    CONSTRAINT FK_FreezeRecords_Subscriptions FOREIGN KEY (SubscriptionId)      REFERENCES Subscriptions(SubscriptionId),
    CONSTRAINT FK_FreezeRecords_Employees     FOREIGN KEY (HandledByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_FreezeRecords_Reason CHECK (LEN(LTRIM(RTRIM(Reason))) >= 5),
    CONSTRAINT CK_FreezeRecords_Dates  CHECK (PlannedEndDate >= StartDate),
    CONSTRAINT CK_FreezeRecords_Actual CHECK (ActualEndDate IS NULL OR (ActualEndDate >= StartDate AND ActualEndDate <= PlannedEndDate))
);
GO

-- Mỗi gói tối đa 1 lần bảo lưu đang chạy
CREATE UNIQUE INDEX UX_FreezeRecords_OneOpen ON FreezeRecords(SubscriptionId) WHERE ActualEndDate IS NULL;
GO

-- CheckIns: mỗi lượt khách vào phòng tập — chỉ bằng gói tập hoặc gói ngày (CBR-58)
CREATE TABLE CheckIns (
    CheckInId            INT IDENTITY(1,1) NOT NULL,
    SubscriptionId       INT          NOT NULL,
    VerifiedByEmployeeId INT          NOT NULL,
    CheckedInAt          DATETIME2(0) NOT NULL CONSTRAINT DF_CheckIns_CheckedInAt   DEFAULT (SYSDATETIME()),
    PhotoVerified        BIT          NOT NULL CONSTRAINT DF_CheckIns_PhotoVerified DEFAULT (0), -- 0 với gói ngày (BR-30)

    CONSTRAINT PK_CheckIns PRIMARY KEY (CheckInId),
    CONSTRAINT FK_CheckIns_Subscriptions FOREIGN KEY (SubscriptionId)       REFERENCES Subscriptions(SubscriptionId),
    CONSTRAINT FK_CheckIns_Employees     FOREIGN KEY (VerifiedByEmployeeId) REFERENCES Employees(EmployeeId)
);
GO

-- Gói ngày chỉ 1 lượt (CBR-36): mỗi Subscription gói ngày có tối đa 1 check-in → kiểm ở Service
-- (filtered index không đọc được loại gói ở bảng khác).

-- CustomerDeletionRequests: yêu cầu xóa hồ sơ còn gói → chờ Manager duyệt (CBR-16, BR-16)
CREATE TABLE CustomerDeletionRequests (
    CustomerDeletionRequestId INT IDENTITY(1,1) NOT NULL,
    CustomerId                INT           NOT NULL,
    RequestedByEmployeeId     INT           NOT NULL,
    ReviewedByEmployeeId      INT           NULL,
    Reason                    NVARCHAR(500) NOT NULL,  -- BR-17
    Status                    VARCHAR(10)   NOT NULL CONSTRAINT DF_DeletionRequests_Status      DEFAULT ('Pending'),
    RejectReason              NVARCHAR(500) NULL,
    RequestedAt               DATETIME2(0)  NOT NULL CONSTRAINT DF_DeletionRequests_RequestedAt DEFAULT (SYSDATETIME()),
    ReviewedAt                DATETIME2(0)  NULL,

    CONSTRAINT PK_CustomerDeletionRequests PRIMARY KEY (CustomerDeletionRequestId),
    CONSTRAINT FK_DeletionRequests_Customers FOREIGN KEY (CustomerId)            REFERENCES Customers(CustomerId),
    CONSTRAINT FK_DeletionRequests_Requester FOREIGN KEY (RequestedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_DeletionRequests_Reviewer  FOREIGN KEY (ReviewedByEmployeeId)  REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_DeletionRequests_Reason CHECK (LEN(LTRIM(RTRIM(Reason))) >= 5),
    CONSTRAINT CK_DeletionRequests_Status CHECK (Status IN ('Pending','Approved','Rejected')),
    CONSTRAINT CK_DeletionRequests_Review CHECK (
        (Status = 'Pending' AND ReviewedByEmployeeId IS NULL AND ReviewedAt IS NULL AND RejectReason IS NULL)
        OR (Status = 'Approved' AND ReviewedByEmployeeId IS NOT NULL AND ReviewedAt IS NOT NULL AND RejectReason IS NULL)
        OR (Status = 'Rejected' AND ReviewedByEmployeeId IS NOT NULL AND ReviewedAt IS NOT NULL
            AND RejectReason IS NOT NULL AND LEN(LTRIM(RTRIM(RejectReason))) >= 5)
    )
);
GO

-- Mỗi hồ sơ tối đa 1 yêu cầu xóa đang chờ
CREATE UNIQUE INDEX UX_DeletionRequests_OnePending ON CustomerDeletionRequests(CustomerId) WHERE Status = 'Pending';
GO

-- Index cluster B
CREATE INDEX IX_Orders_StatusDue ON Orders(Status, PaymentDueAt) WHERE Status = 'Pending'; -- job hủy đơn quá hạn (CBR-70)
CREATE INDEX IX_Orders_Holder    ON Orders(HolderCustomerId, CreatedAt DESC);
CREATE INDEX IX_Orders_Partner   ON Orders(PartnerCustomerId) WHERE PartnerCustomerId IS NOT NULL;
CREATE INDEX IX_Orders_Shift     ON Orders(ShiftId) WHERE ShiftId IS NOT NULL;              -- chốt ca (CBR-78)
CREATE INDEX IX_Transactions_RefundOpen ON Transactions(CreatedAt DESC) WHERE NeedsRefund = 1 AND RefundedAt IS NULL; -- danh sách cần hoàn (BR-47)
CREATE INDEX IX_Subscriptions_CustomerStatus ON Subscriptions(CustomerId, Status, EndDate);
CREATE INDEX IX_Subscriptions_EndDate ON Subscriptions(EndDate, Status);                    -- job hết hạn gói (GBR-14)
CREATE INDEX IX_FreezeRecords_Open ON FreezeRecords(PlannedEndDate) WHERE ActualEndDate IS NULL; -- job mở lại bảo lưu
CREATE INDEX IX_CheckIns_CheckedInAt ON CheckIns(CheckedInAt);
CREATE INDEX IX_CheckIns_Subscription ON CheckIns(SubscriptionId, CheckedInAt);
CREATE INDEX IX_DeletionRequests_Status ON CustomerDeletionRequests(Status, RequestedAt);
GO

/* ============================================================================
   CLUSTER C — LỊCH PT & BUỔI TẬP
   TimeSlot: Free → Held (lập đơn) → Booked (Paid); hủy đơn → Free (DBR-36).
   ============================================================================ */

CREATE TABLE TimeSlots (
    TimeSlotId    INT IDENTITY(1,1) NOT NULL,
    PtEmployeeId  INT          NOT NULL,
    SlotDate      DATE         NOT NULL,
    StartTime     TIME(0)      NOT NULL,  -- lưới 15 phút (DBR-12)
    EndTime       TIME(0)      NOT NULL,  -- = StartTime + P-06 phút, tính ở Service (v25: không ghi cứng 90 trong CHECK)
    Status        VARCHAR(10)  NOT NULL CONSTRAINT DF_TimeSlots_Status    DEFAULT ('Free'), -- Free | Held | Booked | Withdrawn
    HeldByOrderId INT          NULL,      -- đơn đang giữ (chỉ khi Held)
    HoldExpiresAt DATETIME2(0) NULL,      -- = hạn đơn online (CBR-40). Đơn tại quầy: NULL, giữ tới khi trả tiền / hủy (CBR-78)
    CreatedAt     DATETIME2(0) NOT NULL CONSTRAINT DF_TimeSlots_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_TimeSlots PRIMARY KEY (TimeSlotId),
    CONSTRAINT UQ_TimeSlots_PtDateStart UNIQUE (PtEmployeeId, SlotDate, StartTime),
    CONSTRAINT FK_TimeSlots_Employees FOREIGN KEY (PtEmployeeId)  REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_TimeSlots_Orders    FOREIGN KEY (HeldByOrderId) REFERENCES Orders(OrderId),
    CONSTRAINT CK_TimeSlots_Status CHECK (Status IN ('Free','Held','Booked','Withdrawn')), -- Withdrawn = PT gỡ slot trống (GBR-05)
    CONSTRAINT CK_TimeSlots_Grid   CHECK (DATEPART(MINUTE, StartTime) IN (0,15,30,45) AND DATEPART(SECOND, StartTime) = 0),
    CONSTRAINT CK_TimeSlots_Time   CHECK (EndTime > StartTime),
    CONSTRAINT CK_TimeSlots_Hold CHECK (
        (Status = 'Held' AND HeldByOrderId IS NOT NULL)
        OR (Status <> 'Held' AND HeldByOrderId IS NULL AND HoldExpiresAt IS NULL)
    )
);
GO

CREATE INDEX IX_TimeSlots_SlotDate ON TimeSlots(SlotDate) INCLUDE (PtEmployeeId);
GO

CREATE TABLE Sessions (
    SessionId            INT IDENTITY(1,1) NOT NULL,
    SubscriptionId       INT           NOT NULL,
    TimeSlotId           INT           NOT NULL,
    PtEmployeeId         INT           NOT NULL, -- PT DẠY buổi này; đổi khi chuyển PT → hoa hồng theo PT mới (BR-68)
    Status               VARCHAR(10)   NOT NULL CONSTRAINT DF_Sessions_Status       DEFAULT ('Scheduled'), -- DBR-37
    SessionValue         INT           NOT NULL, -- DBR-59, chốt khi Paid
    CommissionAmount     INT           NOT NULL, -- DBR-58 = tỷ lệ PT dạy × SessionValue
    AttendanceMarkedAt   DATETIME2(0)  NULL,     -- JOB-04 ghi lúc 00:05 hôm sau
    IsAutoClosed         BIT           NOT NULL CONSTRAINT DF_Sessions_AutoClosed   DEFAULT (0), -- JOB-04 ghi lúc 00:05 hôm sau, không còn PT điểm danh
    MissedReason         VARCHAR(20)   NULL,     -- v26 mã tiếng Anh (GBR-01): NoCheckIn | NoMembership | MembershipFrozen
    CancelType           VARCHAR(20)   NULL,     -- ByPt (BR-66) | ByCustomer (BR-79) | GymClosed (JOB-04: ngày nghỉ đã qua, chưa dời; notes 10/10) | PackageExpired (CBR-55)
    CancelReason         NVARCHAR(500) NULL,
    CancelledAt          DATETIME2(0)  NULL,
    CancelledByAccountId INT           NULL,     -- tài khoản bấm hủy (PT hoặc khách)
    RefundAgreed         BIT           NOT NULL CONSTRAINT DF_Sessions_RefundAgreed DEFAULT (0), -- BR-66: khách đồng ý nhận tiền mặt

    CONSTRAINT PK_Sessions PRIMARY KEY (SessionId),
    CONSTRAINT FK_Sessions_Subscriptions FOREIGN KEY (SubscriptionId)       REFERENCES Subscriptions(SubscriptionId),
    CONSTRAINT FK_Sessions_TimeSlots     FOREIGN KEY (TimeSlotId)           REFERENCES TimeSlots(TimeSlotId),
    CONSTRAINT FK_Sessions_Employees     FOREIGN KEY (PtEmployeeId)         REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Sessions_Accounts      FOREIGN KEY (CancelledByAccountId) REFERENCES Accounts(AccountId),
    CONSTRAINT CK_Sessions_Status     CHECK (Status IN ('Scheduled','CheckedIn','Missed','Cancelled')),
    CONSTRAINT CK_Sessions_Value      CHECK (SessionValue >= 0 AND CommissionAmount >= 0 AND CommissionAmount <= SessionValue),
    CONSTRAINT CK_Sessions_MissedReason CHECK (
        (Status = 'Missed' AND MissedReason IS NOT NULL AND MissedReason IN ('NoCheckIn','NoMembership','MembershipFrozen'))
        OR (Status <> 'Missed' AND MissedReason IS NULL)
    ),
    CONSTRAINT CK_Sessions_Cancel CHECK (
        (Status = 'Cancelled' AND CancelType IS NOT NULL AND CancelType IN ('ByPt','ByCustomer','GymClosed','PackageExpired') AND CancelledAt IS NOT NULL)
        OR (Status <> 'Cancelled' AND CancelType IS NULL AND CancelledAt IS NULL AND CancelReason IS NULL
            AND CancelledByAccountId IS NULL AND RefundAgreed = 0)
    ),
    -- BR-66: PT hủy phải có lý do + tick "khách đồng ý nhận tiền mặt"
    CONSTRAINT CK_Sessions_PtCancel CHECK (
        CancelType IS NULL OR CancelType <> 'ByPt'
        OR (RefundAgreed = 1 AND CancelReason IS NOT NULL AND LEN(LTRIM(RTRIM(CancelReason))) >= 5 AND CancelledByAccountId IS NOT NULL)
    ),
    CONSTRAINT CK_Sessions_CustomerCancel CHECK (
        CancelType IS NULL OR CancelType <> 'ByCustomer' OR (CancelledByAccountId IS NOT NULL AND RefundAgreed = 0)
    ),
    CONSTRAINT CK_Sessions_SystemCancel CHECK (
        CancelType IS NULL OR CancelType NOT IN ('GymClosed','PackageExpired') OR (CancelledByAccountId IS NULL AND RefundAgreed = 0)
    ),
    CONSTRAINT CK_Sessions_Attendance CHECK (Status <> 'Scheduled' OR (AttendanceMarkedAt IS NULL AND IsAutoClosed = 0))
);
GO

-- DBR-32: mỗi slot tối đa 1 buổi đang Scheduled (buổi Missed / Cancelled đã nhả slot)
CREATE UNIQUE INDEX UX_Sessions_OneActivePerSlot ON Sessions(TimeSlotId) WHERE Status = 'Scheduled';
GO

CREATE INDEX IX_Sessions_Scheduled ON Sessions(TimeSlotId) INCLUDE (SubscriptionId, PtEmployeeId) WHERE Status = 'Scheduled';
GO

-- SessionMoveLogs (v25, thay SessionSwapLogs): MỌI lần dời buổi, để giữ lịch sử PT cũ / slot cũ
--   CustomerSwap   : khách tự swap (UC-38) — tính vào P-08
--   PtOnBehalfSwap : PT dời theo yêu cầu khách không có tài khoản (BR-62) — tính vào P-08
--   PtReschedule   : PT bận, dời sang slot khác của chính mình (BR-64) — không tính
--   PtTransfer     : chuyển sang PT khác (BR-65, BR-68) — không tính
CREATE TABLE SessionMoveLogs (
    SessionMoveLogId     INT IDENTITY(1,1) NOT NULL,
    SessionId            INT           NOT NULL,
    MoveType             VARCHAR(15)   NOT NULL,
    OldTimeSlotId        INT           NOT NULL,
    NewTimeSlotId        INT           NOT NULL,
    OldPtEmployeeId      INT           NOT NULL,
    NewPtEmployeeId      INT           NOT NULL,
    RequestedByAccountId INT           NOT NULL, -- khách (CustomerSwap) hoặc PT thao tác
    Reason               NVARCHAR(500) NULL,
    MovedAt              DATETIME2(0)  NOT NULL CONSTRAINT DF_MoveLogs_MovedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_SessionMoveLogs PRIMARY KEY (SessionMoveLogId),
    CONSTRAINT FK_MoveLogs_Sessions FOREIGN KEY (SessionId)            REFERENCES Sessions(SessionId),
    CONSTRAINT FK_MoveLogs_OldSlot  FOREIGN KEY (OldTimeSlotId)        REFERENCES TimeSlots(TimeSlotId),
    CONSTRAINT FK_MoveLogs_NewSlot  FOREIGN KEY (NewTimeSlotId)        REFERENCES TimeSlots(TimeSlotId),
    CONSTRAINT FK_MoveLogs_OldPt    FOREIGN KEY (OldPtEmployeeId)      REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_MoveLogs_NewPt    FOREIGN KEY (NewPtEmployeeId)      REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_MoveLogs_Accounts FOREIGN KEY (RequestedByAccountId) REFERENCES Accounts(AccountId),
    CONSTRAINT CK_MoveLogs_Type  CHECK (MoveType IN ('CustomerSwap','PtOnBehalfSwap','PtReschedule','PtTransfer')),
    CONSTRAINT CK_MoveLogs_Slots CHECK (OldTimeSlotId <> NewTimeSlotId),
    CONSTRAINT CK_MoveLogs_Pt CHECK (
        (MoveType = 'PtTransfer' AND NewPtEmployeeId <> OldPtEmployeeId)
        OR (MoveType <> 'PtTransfer' AND NewPtEmployeeId = OldPtEmployeeId)
    )
);
GO

-- Index cluster C
CREATE INDEX IX_TimeSlots_PtDateStatus ON TimeSlots(PtEmployeeId, SlotDate, Status);
CREATE INDEX IX_TimeSlots_HoldExpires  ON TimeSlots(HoldExpiresAt) WHERE Status = 'Held';
CREATE INDEX IX_Sessions_PtStatus      ON Sessions(PtEmployeeId, Status);
CREATE INDEX IX_Sessions_Subscription  ON Sessions(SubscriptionId, Status);
CREATE INDEX IX_MoveLogs_Session       ON SessionMoveLogs(SessionId, MovedAt);
GO

/* ============================================================================
   CLUSTER D — NHÂN SỰ & CƠ SỞ VẬT CHẤT
   ============================================================================ */

CREATE TABLE LeaveRequests (
    LeaveRequestId       INT IDENTITY(1,1) NOT NULL,
    EmployeeId           INT           NOT NULL,
    ReviewedByEmployeeId INT           NULL,
    LeaveGroupId         UNIQUEIDENTIFIER NULL, -- Hai phần Annual/Unpaid cùng đợt; đơn độc lập/đơn cũ NULL.
    AssignedToEmployeeId INT           NULL,     -- Người được giao duyệt (NULL = Chưa giao, Quản lý chung)
    LeaveType            VARCHAR(10)   NOT NULL, -- Annual | Sick | Unpaid
    FromDate             DATE          NOT NULL,
    ToDate               DATE          NOT NULL,
    ShiftType            VARCHAR(10)   NOT NULL, -- Morning | Afternoon | FullDay
    Days                 DECIMAL(4,1)  NOT NULL,
    Reason               NVARCHAR(500) NOT NULL,
    Status               VARCHAR(10)   NOT NULL CONSTRAINT DF_LeaveRequests_Status      DEFAULT ('Pending'), -- DBR-44
    RejectReason         NVARCHAR(500) NULL,
    SubmittedAt          DATETIME2(0)  NOT NULL CONSTRAINT DF_LeaveRequests_SubmittedAt DEFAULT (SYSDATETIME()),
    ReviewedAt           DATETIME2(0)  NULL,
    ReminderSentAt       DATETIME2(0)  NULL,

    CONSTRAINT PK_LeaveRequests PRIMARY KEY (LeaveRequestId),
    CONSTRAINT FK_LeaveRequests_Employee FOREIGN KEY (EmployeeId)           REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_LeaveRequests_Reviewer FOREIGN KEY (ReviewedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_LeaveRequests_Assignee FOREIGN KEY (AssignedToEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_LeaveRequests_Type   CHECK (LeaveType IN ('Annual','Sick','Unpaid')),
    CONSTRAINT CK_LeaveRequests_Shift  CHECK (ShiftType IN ('Morning','Afternoon','FullDay')),
    CONSTRAINT CK_LeaveRequests_Reason CHECK (LEN(LTRIM(RTRIM(Reason))) >= 5),
    CONSTRAINT CK_LeaveRequests_Status CHECK (Status IN ('Pending','Approved','Rejected')),
    CONSTRAINT CK_LeaveRequests_Review CHECK (
        (Status = 'Pending'  AND ReviewedByEmployeeId IS NULL AND ReviewedAt IS NULL AND RejectReason IS NULL)
        OR (Status = 'Approved' AND ReviewedByEmployeeId IS NOT NULL AND ReviewedAt IS NOT NULL AND RejectReason IS NULL)
        OR (Status = 'Rejected' AND ReviewedAt IS NOT NULL AND RejectReason IS NOT NULL AND LEN(LTRIM(RTRIM(RejectReason))) >= 5)
    ),
    CONSTRAINT CK_LeaveRequests_Range CHECK (ToDate >= FromDate AND DATEDIFF(DAY, FromDate, ToDate) < 31),
    CONSTRAINT CK_LeaveRequests_Year  CHECK (YEAR(FromDate) = YEAR(ToDate)),
    CONSTRAINT CK_LeaveRequests_Days  CHECK (Days > 0),
    CONSTRAINT CK_LeaveRequests_Half  CHECK (ShiftType = 'FullDay' OR FromDate = ToDate)
);
GO

-- Bảng lưu lịch sử chuyển người duyệt (BF-05)
CREATE TABLE LeaveRequestTransfers (
    TransferId     INT IDENTITY(1,1) NOT NULL,
    LeaveRequestId INT           NOT NULL,
    FromEmployeeId INT           NOT NULL,
    ToEmployeeId   INT           NOT NULL,
    Note           NVARCHAR(200) NULL,
    CreatedAt      DATETIME2(0)  NOT NULL CONSTRAINT DF_LRT_CreatedAt DEFAULT (SYSDATETIME()),
    CONSTRAINT PK_LeaveRequestTransfers PRIMARY KEY (TransferId),
    CONSTRAINT FK_LRT_Request FOREIGN KEY (LeaveRequestId) REFERENCES LeaveRequests(LeaveRequestId),
    CONSTRAINT FK_LRT_From    FOREIGN KEY (FromEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_LRT_To      FOREIGN KEY (ToEmployeeId)   REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_LRT_Diff    CHECK (FromEmployeeId <> ToEmployeeId)
);
GO
CREATE INDEX IX_LRT_Request ON LeaveRequestTransfers(LeaveRequestId, CreatedAt);
GO

CREATE TABLE Facilities (
    FacilityId   INT IDENTITY(1,1) NOT NULL,
    FacilityName NVARCHAR(100) NOT NULL,
    Category     VARCHAR(15)   NOT NULL,  -- Cardio | FreeWeights | Machines | Amenities
    Location     NVARCHAR(100) NULL,
    RowVer       ROWVERSION,

    CONSTRAINT PK_Facilities PRIMARY KEY (FacilityId),
    CONSTRAINT CK_Facilities_Name     CHECK (LEN(LTRIM(RTRIM(FacilityName))) >= 3),
    CONSTRAINT CK_Facilities_Category CHECK (Category IN ('Cardio','FreeWeights','Machines','Amenities'))
);
GO

-- EquipmentItems: từng thiết bị cá thể vật lý (TM-001, CP-001...)
CREATE TABLE EquipmentItems (
    EquipmentItemId INT IDENTITY(1,1) NOT NULL,
    FacilityId      INT           NOT NULL,
    ItemCode        VARCHAR(20)   NOT NULL,
    Status          VARCHAR(20)   NOT NULL CONSTRAINT DF_EquipmentItems_Status DEFAULT ('Available'),
    RowVer          ROWVERSION,

    CONSTRAINT PK_EquipmentItems PRIMARY KEY (EquipmentItemId),
    CONSTRAINT UQ_EquipmentItems_ItemCode UNIQUE (ItemCode),
    CONSTRAINT FK_EquipmentItems_Facilities FOREIGN KEY (FacilityId) REFERENCES Facilities(FacilityId),
    CONSTRAINT CK_EquipmentItems_Status CHECK (Status IN ('Available','UnderMaintenance','Broken','Retired'))
);
GO

-- MaintenanceRequests: Open → InProgress → Closed, hoặc Open → Rejected (DBR-46)
CREATE TABLE MaintenanceRequests (
    MaintenanceRequestId INT IDENTITY(1,1) NOT NULL,
    RequestCode          VARCHAR(12)   NOT NULL,  -- v25: BT-nnnn (SRS 8.2.1)
    EquipmentItemId      INT           NOT NULL,
    ReportedByEmployeeId INT           NOT NULL,  -- Lễ tân báo hỏng
    ReviewedByEmployeeId INT           NULL,      -- Manager duyệt / từ chối (BR-49)
    ClosedByEmployeeId   INT           NULL,      -- Lễ tân đóng (BR-48)
    Priority             VARCHAR(10)   NOT NULL CONSTRAINT DF_Maintenance_Priority   DEFAULT ('Normal'), -- Urgent | Normal | Low
    IncidentType         VARCHAR(30)   NOT NULL CONSTRAINT DF_Maintenance_IncidentType DEFAULT ('NotWorking'), -- NotWorking | StrangeNoise | BrokenOrWorn | SafetyHazard | Hygiene
    DetectedBy           VARCHAR(30)   NOT NULL CONSTRAINT DF_Maintenance_DetectedBy DEFAULT ('Receptionist'), -- Receptionist | Customer | Technician | PT
    WarningTagPlaced     BIT           NOT NULL CONSTRAINT DF_Maintenance_WarningTag DEFAULT (0),        -- đã treo biển cảnh báo
    Description          NVARCHAR(500) NOT NULL,  -- DBR-15: 10–500
    AttachedImages       NVARCHAR(1000) NULL,     -- đường dẫn ảnh/video đính kèm
    Status               VARCHAR(12)   NOT NULL CONSTRAINT DF_Maintenance_Status     DEFAULT ('Open'),
    SetUnderMaintenance  BIT           NULL,      -- Manager có chuyển riêng thiết bị sang Under maintenance không (CBR-101)
    RejectReason         NVARCHAR(500) NULL,
    AcceptanceNote       NVARCHAR(500) NULL,      -- ghi chú nghiệm thu khi đóng
    ReportedAt           DATETIME2(0)  NOT NULL CONSTRAINT DF_Maintenance_ReportedAt DEFAULT (SYSDATETIME()),
    ReviewedAt           DATETIME2(0)  NULL,
    ClosedAt             DATETIME2(0)  NULL,

    CONSTRAINT PK_MaintenanceRequests PRIMARY KEY (MaintenanceRequestId),
    CONSTRAINT UQ_MaintenanceRequests_Code UNIQUE (RequestCode),
    CONSTRAINT FK_Maintenance_EquipmentItem FOREIGN KEY (EquipmentItemId)     REFERENCES EquipmentItems(EquipmentItemId),
    CONSTRAINT FK_Maintenance_Reporter      FOREIGN KEY (ReportedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Maintenance_Reviewer      FOREIGN KEY (ReviewedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Maintenance_Closer        FOREIGN KEY (ClosedByEmployeeId)   REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Maintenance_Code          CHECK (RequestCode LIKE 'BT-[0-9][0-9][0-9][0-9]'),
    CONSTRAINT CK_Maintenance_Priority      CHECK (Priority IN ('Urgent','Normal','Low')),
    CONSTRAINT CK_Maintenance_IncidentType  CHECK (IncidentType IN ('NotWorking','StrangeNoise','BrokenOrWorn','SafetyHazard','Hygiene')),
    CONSTRAINT CK_Maintenance_DetectedBy    CHECK (DetectedBy IN ('Receptionist','Customer','Technician','PT')),
    CONSTRAINT CK_Maintenance_Description   CHECK (LEN(LTRIM(RTRIM(Description))) BETWEEN 10 AND 500),
    CONSTRAINT CK_Maintenance_Status        CHECK (Status IN ('Open','InProgress','Rejected','Closed')),
    CONSTRAINT CK_Maintenance_Flow CHECK (
        (Status = 'Open' AND ReviewedByEmployeeId IS NULL AND ReviewedAt IS NULL AND SetUnderMaintenance IS NULL
            AND ClosedByEmployeeId IS NULL AND RejectReason IS NULL)
        OR (Status = 'InProgress' AND ReviewedByEmployeeId IS NOT NULL AND ReviewedAt IS NOT NULL
            AND SetUnderMaintenance IS NOT NULL AND ClosedByEmployeeId IS NULL AND RejectReason IS NULL)
        OR (Status = 'Rejected' AND ReviewedByEmployeeId IS NOT NULL AND ReviewedAt IS NOT NULL
            AND SetUnderMaintenance IS NULL AND ClosedByEmployeeId IS NULL AND RejectReason IS NOT NULL AND LEN(LTRIM(RTRIM(RejectReason))) >= 5)
        OR (Status = 'Closed' AND ReviewedByEmployeeId IS NOT NULL AND ReviewedAt IS NOT NULL
            AND SetUnderMaintenance IS NOT NULL AND ClosedByEmployeeId IS NOT NULL AND ClosedAt IS NOT NULL
            AND RejectReason IS NULL)
    ),
    CONSTRAINT CK_Maintenance_ClosedAt CHECK (Status = 'Closed' OR ClosedAt IS NULL)
);
GO

-- StaffAttendances: chấm công — CHƯA làm ở phiên bản này, mô hình hóa sẵn (DBR-63)
CREATE TABLE StaffAttendances (
    StaffAttendanceId INT IDENTITY(1,1) NOT NULL,
    EmployeeId        INT           NOT NULL,
    WorkDate          DATE          NOT NULL,
    ShiftType         VARCHAR(10)   NOT NULL,
    CheckInAt         DATETIME2(0)  NULL,
    CheckInPhoto      NVARCHAR(255) NULL,     -- KHÔNG dùng cho check-in hội viên
    CheckOutAt        DATETIME2(0)  NULL,
    Status            VARCHAR(10)   NOT NULL, -- Present | Late | Absent | OnLeave
    Note              NVARCHAR(255) NULL,

    CONSTRAINT PK_StaffAttendances PRIMARY KEY (StaffAttendanceId),
    CONSTRAINT UQ_StaffAttendances_EmpDateShift UNIQUE (EmployeeId, WorkDate, ShiftType),
    CONSTRAINT FK_StaffAttendances_Employees FOREIGN KEY (EmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_StaffAttendances_Shift  CHECK (ShiftType IN ('Morning','Afternoon')),
    CONSTRAINT CK_StaffAttendances_Status CHECK (Status IN ('Present','Late','Absent','OnLeave')),
    CONSTRAINT CK_StaffAttendances_Times  CHECK (CheckOutAt IS NULL OR CheckInAt IS NULL OR CheckOutAt > CheckInAt)
);
GO

-- Index cluster D
CREATE INDEX IX_LeaveRequests_StatusDate  ON LeaveRequests(Status, FromDate);        -- hàng chờ duyệt + job tự từ chối
CREATE INDEX IX_LeaveRequests_Group ON LeaveRequests(LeaveGroupId, EmployeeId) WHERE LeaveGroupId IS NOT NULL;
CREATE INDEX IX_LeaveRequests_DateShift   ON LeaveRequests(FromDate, ToDate, ShiftType, Status); -- định biên (DBR-60)
CREATE INDEX IX_EquipmentItems_Facility   ON EquipmentItems(FacilityId, Status);
CREATE INDEX IX_Maintenance_Status        ON MaintenanceRequests(Status, ReportedAt);
CREATE INDEX IX_Maintenance_EquipmentItem ON MaintenanceRequests(EquipmentItemId, Status);
CREATE INDEX IX_StaffAttendances_EmpDate  ON StaffAttendances(EmployeeId, WorkDate);
GO

/* ============================================================================
   CLUSTER E — NỘI DUNG GIỮA PT VÀ KHÁCH
   ============================================================================ */

CREATE TABLE PrivateNotes (
    PrivateNoteId INT IDENTITY(1,1) NOT NULL,
    PtEmployeeId  INT            NOT NULL,  -- chỉ PT viết mới thấy (CBR-92)
    CustomerId    INT            NOT NULL,
    Content       NVARCHAR(2000) NOT NULL,  -- DBR-15: 1–2000
    CreatedAt     DATETIME2(0)   NOT NULL CONSTRAINT DF_PrivateNotes_CreatedAt DEFAULT (SYSDATETIME()),
    UpdatedAt     DATETIME2(0)   NOT NULL CONSTRAINT DF_PrivateNotes_UpdatedAt DEFAULT (SYSDATETIME()),
    DeletedAt     DATETIME2(0)   NULL,      -- BR-59 xóa mềm
    RowVer        ROWVERSION,

    CONSTRAINT PK_PrivateNotes PRIMARY KEY (PrivateNoteId),
    CONSTRAINT FK_PrivateNotes_Employees FOREIGN KEY (PtEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_PrivateNotes_Customers FOREIGN KEY (CustomerId)   REFERENCES Customers(CustomerId),
    CONSTRAINT CK_PrivateNotes_Content CHECK (LEN(LTRIM(RTRIM(Content))) >= 1)
);
GO

CREATE UNIQUE INDEX UX_PrivateNotes_ActivePerPair ON PrivateNotes(PtEmployeeId, CustomerId) WHERE DeletedAt IS NULL; -- DBR-61
GO

-- TrainingPlans: bản NHÁP (Title/Content) tách khỏi bản ĐÃ XUẤT BẢN (Published*) — BR-60:
-- PT sửa nháp thoải mái, khách vẫn thấy bản xuất bản gần nhất cho tới khi PT xuất bản lại.
CREATE TABLE TrainingPlans (
    TrainingPlanId   INT IDENTITY(1,1) NOT NULL,
    PtEmployeeId     INT            NOT NULL,
    CustomerId       INT            NOT NULL,
    PlanType         VARCHAR(10)    NOT NULL, -- Workout | Meal
    Title            NVARCHAR(200)  NOT NULL,
    Content          NVARCHAR(MAX)  NOT NULL, -- DBR-15: ≤ 10 000 ký tự
    PublishedTitle   NVARCHAR(200)  NULL,
    PublishedContent NVARCHAR(MAX)  NULL,
    PublishedAt      DATETIME2(0)   NULL,     -- NULL = chưa xuất bản lần nào, khách chưa thấy
    CreatedAt        DATETIME2(0)   NOT NULL CONSTRAINT DF_TrainingPlans_CreatedAt DEFAULT (SYSDATETIME()),
    UpdatedAt        DATETIME2(0)   NOT NULL CONSTRAINT DF_TrainingPlans_UpdatedAt DEFAULT (SYSDATETIME()),
    DeletedAt        DATETIME2(0)   NULL,     -- BR-61: xóa → khách mất ngay
    RowVer           ROWVERSION,

    CONSTRAINT PK_TrainingPlans PRIMARY KEY (TrainingPlanId),
    CONSTRAINT FK_TrainingPlans_Employees FOREIGN KEY (PtEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_TrainingPlans_Customers FOREIGN KEY (CustomerId)   REFERENCES Customers(CustomerId),
    CONSTRAINT CK_TrainingPlans_Type    CHECK (PlanType IN ('Workout','Meal')),
    CONSTRAINT CK_TrainingPlans_Length  CHECK (LEN(Content) <= 10000 AND (PublishedContent IS NULL OR LEN(PublishedContent) <= 10000)),
    CONSTRAINT CK_TrainingPlans_Published CHECK (
        (PublishedAt IS NULL AND PublishedTitle IS NULL AND PublishedContent IS NULL)
        OR (PublishedAt IS NOT NULL AND PublishedTitle IS NOT NULL AND PublishedContent IS NOT NULL)
    )
);
GO

CREATE UNIQUE INDEX UX_TrainingPlans_ActivePerTriple ON TrainingPlans(PtEmployeeId, CustomerId, PlanType) WHERE DeletedAt IS NULL; -- DBR-62
GO

/* ============================================================================
   CLUSTER F — GIAO TIẾP & NỘI DUNG CÔNG KHAI
   ============================================================================ */

CREATE TABLE Feedbacks (
    FeedbackId       INT IDENTITY(1,1) NOT NULL,
    CustomerId       INT            NOT NULL,
    Content          NVARCHAR(1000) NOT NULL,  -- DBR-15: 10–1000
    SubmittedAt      DATETIME2(0)   NOT NULL CONSTRAINT DF_Feedbacks_SubmittedAt DEFAULT (SYSDATETIME()),
    IsRead           BIT            NOT NULL CONSTRAINT DF_Feedbacks_IsRead      DEFAULT (0), -- CBR-107
    ReadAt           DATETIME2(0)   NULL,
    ReadByEmployeeId INT            NULL,      -- chỉ Manager (CBR-106)

    RatedPtEmployeeId INT          NULL,      -- DBR-66: ba cột đánh giá cùng NULL hoặc cùng có giá trị
    Rating           TINYINT       NULL,      -- 1–5 sao
    RatingMonth      DATE          NULL,      -- ngày 1 tháng theo giờ VN (DBR-67)
    UpdatedAt        DATETIME2(0)  NULL,      -- khác NULL khi sửa phiếu

    CONSTRAINT PK_Feedbacks PRIMARY KEY (FeedbackId),
    CONSTRAINT FK_Feedbacks_Customers FOREIGN KEY (CustomerId)       REFERENCES Customers(CustomerId),
    CONSTRAINT FK_Feedbacks_Employees FOREIGN KEY (ReadByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Feedbacks_RatedPt FOREIGN KEY (RatedPtEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Feedbacks_Rating CHECK (
        (RatedPtEmployeeId IS NULL AND Rating IS NULL AND RatingMonth IS NULL)
        OR (RatedPtEmployeeId IS NOT NULL AND Rating IS NOT NULL AND Rating BETWEEN 1 AND 5
            AND RatingMonth IS NOT NULL AND DAY(RatingMonth) = 1)
    ),
    CONSTRAINT CK_Feedbacks_Content CHECK (LEN(LTRIM(RTRIM(Content))) BETWEEN 10 AND 1000),
    CONSTRAINT CK_Feedbacks_Read CHECK (
        (IsRead = 1 AND ReadAt IS NOT NULL AND ReadByEmployeeId IS NOT NULL)
        OR (IsRead = 0 AND ReadAt IS NULL AND ReadByEmployeeId IS NULL)
    )
);
GO

CREATE UNIQUE INDEX UX_Feedbacks_RatingPerMonth ON Feedbacks(CustomerId, RatedPtEmployeeId, RatingMonth) WHERE RatedPtEmployeeId IS NOT NULL;
CREATE INDEX IX_Feedbacks_RatedPt ON Feedbacks(RatedPtEmployeeId, RatingMonth) INCLUDE (Rating, CustomerId, UpdatedAt, SubmittedAt) WHERE RatedPtEmployeeId IS NOT NULL;
GO

CREATE TABLE BlogPosts (
    BlogPostId       INT IDENTITY(1,1) NOT NULL,
    AuthorEmployeeId INT            NOT NULL,
    Title            NVARCHAR(200)  NOT NULL, -- DBR-15: 5–200
    Category         VARCHAR(15)    NOT NULL, -- Training | Nutrition | News
    Excerpt          NVARCHAR(300)  NULL,     -- NULL → thẻ bài lấy phần đầu nội dung
    Content          NVARCHAR(MAX)  NOT NULL, -- DBR-15: ≤ 20 000
    CoverImage       NVARCHAR(255)  NULL,     -- NULL → ảnh mặc định theo danh mục
    Status           VARCHAR(10)    NOT NULL CONSTRAINT DF_BlogPosts_Status    DEFAULT ('Inactive'), -- Active | Inactive (CBR-100)
    PublishedAt      DATETIME2(0)   NULL,
    CreatedAt        DATETIME2(0)   NOT NULL CONSTRAINT DF_BlogPosts_CreatedAt DEFAULT (SYSDATETIME()),
    UpdatedAt        DATETIME2(0)   NOT NULL CONSTRAINT DF_BlogPosts_UpdatedAt DEFAULT (SYSDATETIME()),
    RowVer           ROWVERSION,

    CONSTRAINT PK_BlogPosts PRIMARY KEY (BlogPostId),
    CONSTRAINT FK_BlogPosts_Employees FOREIGN KEY (AuthorEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_BlogPosts_Title    CHECK (LEN(LTRIM(RTRIM(Title))) BETWEEN 5 AND 200),
    CONSTRAINT CK_BlogPosts_Category CHECK (Category IN ('Training','Nutrition','News')),
    CONSTRAINT CK_BlogPosts_Content  CHECK (LEN(Content) <= 20000),
    CONSTRAINT CK_BlogPosts_Status   CHECK (Status IN ('Active','Inactive')),
    CONSTRAINT CK_BlogPosts_Published CHECK (Status <> 'Active' OR PublishedAt IS NOT NULL)
);
GO

-- Notifications (v25): đối tượng nhận theo SRS 11.2.1 + gửi hẹn giờ
CREATE TABLE Notifications (
    NotificationId   INT IDENTITY(1,1) NOT NULL,
    SentByEmployeeId INT           NULL,      -- NULL = hệ thống tự gửi (CBR-46, CBR-57, CBR-85)
    Title            NVARCHAR(100) NOT NULL,  -- DBR-15: 5–100
    Content          NVARCHAR(500) NOT NULL,  -- DBR-15: 10–500
    Audience         VARCHAR(15)   NOT NULL,  -- AllCustomers | ActiveMembers | PTs | Receptionists | AllStaff | Individual
    ScheduledAt      DATETIME2(0)  NULL,      -- hẹn giờ gửi; NULL = gửi ngay
    SentAt           DATETIME2(0)  NULL,      -- NULL = chưa tới giờ gửi
    CreatedAt        DATETIME2(0)  NOT NULL CONSTRAINT DF_Notifications_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_Notifications PRIMARY KEY (NotificationId),
    CONSTRAINT FK_Notifications_Employees FOREIGN KEY (SentByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Notifications_Text CHECK (LEN(LTRIM(RTRIM(Title))) BETWEEN 5 AND 100 AND LEN(LTRIM(RTRIM(Content))) BETWEEN 10 AND 500),
    CONSTRAINT CK_Notifications_Audience CHECK (Audience IN ('AllCustomers','ActiveMembers','PTs','Receptionists','AllStaff','Individual')),
    CONSTRAINT CK_Notifications_When CHECK (SentAt IS NOT NULL OR ScheduledAt IS NOT NULL),
    CONSTRAINT CK_Notifications_System CHECK (SentByEmployeeId IS NOT NULL OR Audience = 'Individual')
);
GO

-- Chỉ người CÓ tài khoản mới nhận (GBR-15); trạng thái đã đọc từng người (GBR-34)
CREATE TABLE NotificationRecipients (
    NotificationRecipientId INT IDENTITY(1,1) NOT NULL,
    NotificationId          INT          NOT NULL,
    AccountId               INT          NOT NULL,
    ReadAt                  DATETIME2(0) NULL,

    CONSTRAINT PK_NotificationRecipients PRIMARY KEY (NotificationRecipientId),
    CONSTRAINT UQ_NotificationRecipients UNIQUE (NotificationId, AccountId),
    CONSTRAINT FK_NotifRecipients_Notifications FOREIGN KEY (NotificationId) REFERENCES Notifications(NotificationId),
    CONSTRAINT FK_NotifRecipients_Accounts      FOREIGN KEY (AccountId)      REFERENCES Accounts(AccountId)
);
GO

-- Index cluster F
CREATE INDEX IX_Feedbacks_IsRead ON Feedbacks(IsRead, SubmittedAt DESC);
CREATE INDEX IX_BlogPosts_StatusPublished ON BlogPosts(Status, PublishedAt DESC);
CREATE INDEX IX_Notifications_Scheduled ON Notifications(ScheduledAt) WHERE SentAt IS NULL; -- job gửi hẹn giờ
CREATE INDEX IX_NotifRecipients_AccountUnread ON NotificationRecipients(AccountId) WHERE ReadAt IS NULL;
GO

/* ============================================================================
   CLUSTER G — CẤU HÌNH & LƯU VẾT
   ============================================================================ */

-- SystemConfigs: P-01…P-22 dạng key–value (GBR-04)
CREATE TABLE SystemConfigs (
    SystemConfigId      INT IDENTITY(1,1) NOT NULL,
    ConfigKey           VARCHAR(10)   NOT NULL,  -- P-01 … P-22
    ConfigValue         NVARCHAR(200) NOT NULL,
    Description         NVARCHAR(255) NOT NULL,
    UpdatedAt           DATETIME2(0)  NOT NULL CONSTRAINT DF_SystemConfigs_UpdatedAt DEFAULT (SYSDATETIME()),
    UpdatedByEmployeeId INT           NULL,
    RowVer              ROWVERSION,

    CONSTRAINT PK_SystemConfigs PRIMARY KEY (SystemConfigId),
    CONSTRAINT UQ_SystemConfigs_Key UNIQUE (ConfigKey),
    CONSTRAINT FK_SystemConfigs_Employees FOREIGN KEY (UpdatedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_SystemConfigs_Key CHECK (ConfigKey LIKE 'P-[0-9][0-9]')
);
GO

-- OperatingHourExceptions: Manager khai báo giờ khác mặc định/nghỉ cả ngày (BR-12).
-- Quyết định 10/10: gói PT vẫn sinh buổi ngày nghỉ để PT dời; buổi lẻ không được đặt ngày nghỉ.
CREATE TABLE OperatingHourExceptions (
    OperatingHourExceptionId INT IDENTITY(1,1) NOT NULL,
    ApplyDate                DATE          NOT NULL,  -- hôm nay trở đi lúc khai báo (DBR-12)
    OpenTime                 TIME(0)       NULL,
    CloseTime                TIME(0)       NULL,
    IsClosedAllDay           BIT           NOT NULL CONSTRAINT DF_OperatingHourExceptions_Closed    DEFAULT (0),
    Note                     NVARCHAR(200) NULL,
    SetByEmployeeId          INT           NOT NULL,
    CreatedAt                DATETIME2(0)  NOT NULL CONSTRAINT DF_OperatingHourExceptions_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_OperatingHourExceptions PRIMARY KEY (OperatingHourExceptionId),
    CONSTRAINT UQ_OperatingHourExceptions_Date UNIQUE (ApplyDate),
    CONSTRAINT FK_OperatingHourExceptions_Employees FOREIGN KEY (SetByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_OperatingHourExceptions_Time CHECK (
        (IsClosedAllDay = 1 AND OpenTime IS NULL AND CloseTime IS NULL)
        OR (IsClosedAllDay = 0 AND OpenTime IS NOT NULL AND CloseTime IS NOT NULL AND CloseTime > OpenTime)
    )
);
GO

-- AuditLogs: chỉ ghi thêm, không sửa / xóa; chỉ Admin xem (CBR-103, GBR-07, GBR-08)
CREATE TABLE AuditLogs (
    AuditLogId   INT IDENTITY(1,1) NOT NULL,
    AccountId    INT            NULL,      -- NULL = hệ thống (job)
    ActingRole   VARCHAR(15)    NULL,      -- v25: vai trò đang dùng khi thao tác (CBR-112, CBR-113)
    Action       NVARCHAR(100)  NOT NULL,
    EntityName   NVARCHAR(50)   NOT NULL,
    EntityId     INT            NULL,
    FieldChanged NVARCHAR(50)   NULL,
    OldValue     NVARCHAR(1000) NULL,      -- KHÔNG BAO GIỜ ghi mật khẩu / mã xác thực (DBR-02)
    NewValue     NVARCHAR(1000) NULL,
    Reason       NVARCHAR(500)  NULL,      -- bắt buộc với từ chối / hủy / xóa / mở khóa (GBR-09, BR-10)
    CreatedAt    DATETIME2(0)   NOT NULL CONSTRAINT DF_AuditLogs_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_AuditLogs PRIMARY KEY (AuditLogId),
    CONSTRAINT FK_AuditLogs_Accounts FOREIGN KEY (AccountId) REFERENCES Accounts(AccountId),
    CONSTRAINT CK_AuditLogs_Role CHECK (ActingRole IS NULL OR ActingRole IN ('Admin','Manager','PT','Receptionist','Customer')),
    CONSTRAINT CK_AuditLogs_System CHECK (AccountId IS NOT NULL OR ActingRole IS NULL)
);
GO

-- Index cluster G
CREATE INDEX IX_AuditLogs_CreatedAt ON AuditLogs(CreatedAt DESC);
CREATE INDEX IX_AuditLogs_Entity    ON AuditLogs(EntityName, EntityId);
CREATE INDEX IX_AuditLogs_Account   ON AuditLogs(AccountId, CreatedAt DESC);
GO

-- BodyMeasurements: khách tự ghi chỉ số cơ thể (đo tại phòng hoặc ở nhà) để xem thay đổi. Chỉ khách xem (25/10/2026).
-- BMI KHÔNG lưu: Service tính = cân nặng ÷ (chiều cao m)². Mỗi khách 1 dòng / ngày: lưu lại cùng ngày = sửa dòng đó.
CREATE TABLE BodyMeasurements (
    BodyMeasurementId INT IDENTITY(1,1) NOT NULL,
    CustomerId        INT            NOT NULL,
    MeasuredOn        DATE           NOT NULL,   -- không ở tương lai (kiểm ở Service)
    WeightKg          DECIMAL(4,1)   NOT NULL,   -- 20.0–300.0
    HeightCm          DECIMAL(4,1)   NOT NULL,   -- 100.0–250.0
    BodyFatPercent    DECIMAL(3,1)   NULL,       -- 3.0–70.0, không bắt buộc
    MusclePercent     DECIMAL(3,1)   NULL,       -- tỷ lệ cơ 10.0–70.0, không bắt buộc (máy InBody)
    WaistCm           DECIMAL(4,1)   NULL,       -- 40.0–200.0, không bắt buộc
    Note              NVARCHAR(200)  NULL,
    CreatedAt         DATETIME2(0)   NOT NULL CONSTRAINT DF_BodyMeasurements_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_BodyMeasurements PRIMARY KEY (BodyMeasurementId),
    CONSTRAINT UQ_BodyMeasurements_Day UNIQUE (CustomerId, MeasuredOn),
    CONSTRAINT FK_BodyMeasurements_Customers FOREIGN KEY (CustomerId) REFERENCES Customers(CustomerId),
    CONSTRAINT CK_BodyMeasurements_Weight CHECK (WeightKg BETWEEN 20 AND 300),
    CONSTRAINT CK_BodyMeasurements_Height CHECK (HeightCm BETWEEN 100 AND 250),
    CONSTRAINT CK_BodyMeasurements_Fat    CHECK (BodyFatPercent IS NULL OR BodyFatPercent BETWEEN 3 AND 70),
    CONSTRAINT CK_BodyMeasurements_Muscle CHECK (MusclePercent IS NULL OR MusclePercent BETWEEN 10 AND 70),
    CONSTRAINT CK_BodyMeasurements_Waist  CHECK (WaistCm IS NULL OR WaistCm BETWEEN 40 AND 200)
);
GO

PRINT N'>>> Tạo schema GMS v26 thành công: 31 bảng nghiệp vụ.';
GO
