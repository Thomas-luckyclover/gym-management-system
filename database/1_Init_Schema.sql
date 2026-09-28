/* ============================================================================
   GMS — Gym Management System
   FILE   : 1_Init_Schema.sql
   MỤC ĐÍCH: Tạo toàn bộ schema cho SQL Server, chuẩn 3NF, bám sát ERD v25
             (30 bảng nghiệp vụ, 7 cluster A–G).
   CẬP NHẬT 24/09/2026 (Leader chốt, xem migrations/001): Employees thêm Specialty,
             YearsOfExperience, ResignationReason · VerificationCodes thêm PasswordSetup
             + TokenHash (Code cho phép NULL) · LeaveRequests thêm ReminderSentAt.
   QUY ƯỚC: - Tên bảng: PascalCase, số nhiều           (Customers, Orders)
            - Tên cột : PascalCase                     (CustomerCode, CreatedAt)
            - Khóa chính: <TênBảngSốÍt>Id              (CustomerId, OrderId)
            - Khóa ngoại: trùng tên cột PK bảng gốc, thêm tiền tố vai trò nếu
              một bảng trỏ nhiều lần tới cùng bảng cha (HolderCustomerId…)
            - Mọi CHECK/DEFAULT đều bám Business Rule, chặn dữ liệu rác từ DB
            - DEFAULT khai báo NGAY TRONG định nghĩa cột:
                Col TYPE NOT NULL CONSTRAINT DF_Bang_Cot DEFAULT (giá_trị)
              (cú pháp "CONSTRAINT ... DEFAULT ... FOR Cot" chỉ dùng được trong
               ALTER TABLE, KHÔNG dùng được bên trong CREATE TABLE)
   THỨ TỰ : Tạo bảng cha trước, bảng con sau. Index gom ở cuối mỗi cluster.
   ============================================================================ */

IF DB_ID('GMS') IS NULL
    CREATE DATABASE GMS;
GO

USE GMS;
GO

/* ============================================================================
   CLUSTER A — DANH TÍNH & CON NGƯỜI
   Person là con người thật; Customer và Employee là HAI VAI TRÒ của người đó.
   Account là thông tin đăng nhập, gắn đúng 1 Person (không phải ai cũng có).
   ============================================================================ */

-- Persons: phần chung của cả khách và nhân viên (họ tên, ngày sinh, liên hệ)
CREATE TABLE Persons (
    PersonId        INT IDENTITY(1,1) NOT NULL,
    FullName        NVARCHAR(50)  NOT NULL,   -- 2–50 ký tự, cho phép dấu tiếng Việt
    DateOfBirth     DATE          NOT NULL,   -- dùng để kiểm tra tuổi tối thiểu dịch vụ PT (P-05)
    Phone           VARCHAR(10)   NULL,       -- NULL với hồ sơ Dependent (dùng số giám hộ)
    Email           VARCHAR(100)  NULL,       -- bắt buộc với người có tài khoản
    Avatar          NVARCHAR(255) NULL,       -- chỉ để hiển thị, KHÔNG dùng xác minh danh tính
    IsReleased      BIT           NOT NULL CONSTRAINT DF_Persons_IsReleased DEFAULT (0),   -- 1 = mọi vai trò đã Inactive → nhả SĐT/email cho người khác
    CreatedAt       DATETIME2(0)  NOT NULL CONSTRAINT DF_Persons_CreatedAt  DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_Persons PRIMARY KEY (PersonId),
    -- SĐT Việt Nam: đúng 10 số, bắt đầu bằng 0
    CONSTRAINT CK_Persons_Phone  CHECK (Phone IS NULL OR (LEN(Phone) = 10 AND Phone LIKE '0[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]')),
    CONSTRAINT CK_Persons_Email  CHECK (Email IS NULL OR Email LIKE '%_@_%._%'),
    CONSTRAINT CK_Persons_FullName CHECK (LEN(LTRIM(RTRIM(FullName))) >= 2),
    CONSTRAINT CK_Persons_Dob    CHECK (DateOfBirth < CAST(SYSDATETIME() AS DATE)
                                        AND DATEDIFF(YEAR, DateOfBirth, SYSDATETIME()) <= 100)
);
GO

-- Duy nhất SĐT / email nhưng CHỈ giữa những người chưa bị nhả (IsReleased = 0).
-- Dùng filtered unique index vì hồ sơ đã xóa phải trả lại SĐT/email cho người khác dùng.
CREATE UNIQUE INDEX UX_Persons_Phone_Active ON Persons(Phone) WHERE Phone IS NOT NULL AND IsReleased = 0;
CREATE UNIQUE INDEX UX_Persons_Email_Active ON Persons(Email) WHERE Email IS NOT NULL AND IsReleased = 0;
GO

-- Customers: vai trò KHÁCH của một Person
CREATE TABLE Customers (
    CustomerId           INT IDENTITY(1,1) NOT NULL,
    PersonId             INT           NOT NULL,   -- 1 Person có tối đa 1 hồ sơ khách
    CustomerCode         VARCHAR(12)   NOT NULL,   -- KH-nnnn, hệ thống sinh, không bao giờ đổi
    CheckInPhoto         NVARCHAR(255) NULL,       -- Lễ tân chụp tại quầy, dùng đối chiếu khi check-in
    CustomerType         VARCHAR(10)   NOT NULL,   -- Self | Dependent (không đổi được sau khi tạo)
    GuardianName         NVARCHAR(50)  NULL,       -- 4 cột giám hộ chỉ dùng cho hồ sơ Dependent
    GuardianRelationship NVARCHAR(30)  NULL,
    GuardianPhone        VARCHAR(10)   NULL,       -- được phép trùng nhau (anh em chung số bố mẹ)
    GuardianPhone2       VARCHAR(10)   NULL,
    Status               VARCHAR(10)   NOT NULL CONSTRAINT DF_Customers_Status   DEFAULT ('New'),   -- New → Active → Frozen/Expired → Inactive
    JoinedAt             DATETIME2(0)  NOT NULL CONSTRAINT DF_Customers_JoinedAt DEFAULT (SYSDATETIME()),
    DeletedAt            DATETIME2(0)  NULL,       -- xóa mềm: chỉ set giờ, không xóa dòng

    CONSTRAINT PK_Customers PRIMARY KEY (CustomerId),
    CONSTRAINT UQ_Customers_PersonId     UNIQUE (PersonId),
    CONSTRAINT UQ_Customers_CustomerCode UNIQUE (CustomerCode),
    CONSTRAINT FK_Customers_Persons FOREIGN KEY (PersonId) REFERENCES Persons(PersonId),
    CONSTRAINT CK_Customers_Code CHECK (CustomerCode LIKE 'KH-[0-9][0-9][0-9][0-9]'),
    CONSTRAINT CK_Customers_Type   CHECK (CustomerType IN ('Self','Dependent')),
    CONSTRAINT CK_Customers_Status CHECK (Status IN ('New','Active','Frozen','Expired','Inactive')),
    -- Hồ sơ Dependent bắt buộc có người giám hộ (tên + quan hệ + ít nhất 1 số)
    CONSTRAINT CK_Customers_Guardian CHECK (
        (CustomerType = 'Self')
        OR (CustomerType = 'Dependent' AND GuardianName IS NOT NULL
            AND GuardianRelationship IS NOT NULL AND GuardianPhone IS NOT NULL)
    ),
    -- Hồ sơ Inactive bắt buộc có thời điểm xóa, và ngược lại
    CONSTRAINT CK_Customers_Deleted CHECK (
        (Status = 'Inactive' AND DeletedAt IS NOT NULL) OR (Status <> 'Inactive' AND DeletedAt IS NULL)
    )
);
GO

-- Employees: vai trò NHÂN VIÊN của một Person
CREATE TABLE Employees (
    EmployeeId     INT IDENTITY(1,1) NOT NULL,
    PersonId       INT          NOT NULL,   -- 1 Person có tối đa 1 hồ sơ nhân viên
    EmployeeCode   VARCHAR(12)  NOT NULL,   -- NV-nnnn
    Position       VARCHAR(15)  NOT NULL,   -- Admin | Manager | PT | Receptionist
    HireDate       DATE         NOT NULL,
    CommissionRate TINYINT      NULL,       -- % hoa hồng, CHỈ PT mới có giá trị
    Specialty         NVARCHAR(100) NULL,   -- chuyên môn PT, hiện ở trang chủ + lọc khi đặt buổi lẻ (SRS 2.1.1); chỉ PT có
    YearsOfExperience TINYINT       NULL,   -- số năm kinh nghiệm PT, chỉ để quảng bá trên trang chủ (SRS 2.1.1)
    Status         VARCHAR(10)  NOT NULL CONSTRAINT DF_Employees_Status DEFAULT ('Working'),   -- Working | Resigned
    DeletedAt      DATETIME2(0) NULL,       -- thời điểm nghỉ việc
    ResignationReason NVARCHAR(500) NULL,   -- bắt buộc khi Resigned (SRS 5.1.2, GBR-09: lý do lưu cùng bản ghi)

    CONSTRAINT PK_Employees PRIMARY KEY (EmployeeId),
    CONSTRAINT UQ_Employees_PersonId     UNIQUE (PersonId),
    CONSTRAINT UQ_Employees_EmployeeCode UNIQUE (EmployeeCode),
    CONSTRAINT FK_Employees_Persons FOREIGN KEY (PersonId) REFERENCES Persons(PersonId),
    CONSTRAINT CK_Employees_Code CHECK (EmployeeCode LIKE 'NV-[0-9][0-9][0-9][0-9]'),
    CONSTRAINT CK_Employees_Position CHECK (Position IN ('Admin','Manager','PT','Receptionist')),
    CONSTRAINT CK_Employees_Status   CHECK (Status IN ('Working','Resigned')),
    CONSTRAINT CK_Employees_Rate     CHECK (CommissionRate IS NULL OR CommissionRate BETWEEN 0 AND 100),
    -- Chỉ PT mới có tỷ lệ hoa hồng; vị trí khác phải để NULL
    CONSTRAINT CK_Employees_RateByPosition CHECK (
        (Position = 'PT' AND CommissionRate IS NOT NULL) OR (Position <> 'PT' AND CommissionRate IS NULL)
    ),
    -- Chuyên môn và kinh nghiệm chỉ dành cho PT
    CONSTRAINT CK_Employees_PtProfile CHECK (Position = 'PT' OR (Specialty IS NULL AND YearsOfExperience IS NULL)),
    CONSTRAINT CK_Employees_Experience CHECK (YearsOfExperience IS NULL OR YearsOfExperience <= 60),
    -- Nghỉ việc bắt buộc có lý do 5–500 ký tự (GBR-09, DBR-05)
    CONSTRAINT CK_Employees_Resignation CHECK (
        Status <> 'Resigned' OR (ResignationReason IS NOT NULL AND LEN(LTRIM(RTRIM(ResignationReason))) >= 5)
    )
);
GO

-- Accounts: thông tin đăng nhập, gắn đúng 1 Person. Nhiều người KHÔNG có tài khoản
-- (khách vãng lai, hồ sơ Dependent) nên tách bảng thay vì gộp vào Persons.
CREATE TABLE Accounts (
    AccountId        INT IDENTITY(1,1) NOT NULL,
    PersonId         INT           NOT NULL,
    PasswordHash     VARCHAR(255)  NULL,      -- NULL = tài khoản Lễ tân tạo, khách chưa đặt mật khẩu
    Status           VARCHAR(10)   NOT NULL CONSTRAINT DF_Accounts_Status    DEFAULT ('Active'),   -- Active | Locked (Admin khóa) | Inactive
    FailedLoginCount TINYINT       NOT NULL CONSTRAINT DF_Accounts_Failed    DEFAULT (0),          -- đếm số lần sai liên tiếp, đủ P-17 thì khóa tạm
    LockedUntil      DATETIME2(0)  NULL,      -- khóa TẠM do sai mật khẩu, KHÔNG đổi cột Status
    LastLoginAt      DATETIME2(0)  NULL,
    CreatedAt        DATETIME2(0)  NOT NULL CONSTRAINT DF_Accounts_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_Accounts PRIMARY KEY (AccountId),
    CONSTRAINT UQ_Accounts_PersonId UNIQUE (PersonId),
    CONSTRAINT FK_Accounts_Persons FOREIGN KEY (PersonId) REFERENCES Persons(PersonId),
    CONSTRAINT CK_Accounts_Status CHECK (Status IN ('Active','Locked','Inactive'))
);
GO

-- VerificationCodes: mã 6 số gửi qua email, HOẶC link đặt mật khẩu (PasswordSetup).
--   (PasswordSetup/TokenHash: không còn dùng từ 27/09, giữ để tương thích)
-- Tách bảng vì lúc GỬI mã cho người tự đăng ký thì CHƯA có Account nào để lưu.
CREATE TABLE VerificationCodes (
    VerificationCodeId INT IDENTITY(1,1) NOT NULL,
    AccountId          INT          NULL,   -- NULL khi đăng ký mới (chưa có tài khoản)
    Email              VARCHAR(100) NOT NULL,
    Purpose            VARCHAR(15)  NOT NULL, -- SignUp | Claim | Recovery | EmailChange | PasswordSetup
    Code               CHAR(6)      NULL,     -- mã 6 số; NULL với PasswordSetup (dùng link)
    TokenHash          CHAR(64)     NULL,     -- SHA-256 (hex thường) của token trong link đặt mật khẩu; KHÔNG lưu token thật
    SentAt             DATETIME2(0) NOT NULL,
    ExpiresAt          DATETIME2(0) NOT NULL, -- SentAt + P-15 (mặc định 15 phút)
    FailedAttempts     TINYINT      NOT NULL CONSTRAINT DF_VerificationCodes_Failed DEFAULT (0), -- sai đủ P-17 lần thì hủy mã + khóa email P-18 phút
    LockedUntil        DATETIME2(0) NULL,
    UsedAt             DATETIME2(0) NULL,

    CONSTRAINT PK_VerificationCodes PRIMARY KEY (VerificationCodeId),
    CONSTRAINT FK_VerificationCodes_Accounts FOREIGN KEY (AccountId) REFERENCES Accounts(AccountId),
    CONSTRAINT CK_VerificationCodes_Purpose CHECK (Purpose IN ('SignUp','Claim','Recovery','EmailChange','PasswordSetup')),
    CONSTRAINT CK_VerificationCodes_Code    CHECK (Code NOT LIKE '%[^0-9]%'),
    CONSTRAINT CK_VerificationCodes_Expiry  CHECK (ExpiresAt > SentAt),
    -- PasswordSetup (link cho tài khoản Lễ tân tạo — CBR-24, CBR-31) dùng token và phải gắn tài khoản;
    -- các mục đích khác dùng mã 6 số
    CONSTRAINT CK_VerificationCodes_CodeOrToken CHECK (
        (Purpose = 'PasswordSetup' AND TokenHash IS NOT NULL AND Code IS NULL AND AccountId IS NOT NULL)
        OR (Purpose <> 'PasswordSetup' AND Code IS NOT NULL AND TokenHash IS NULL)
    )
);
GO

-- Mở link đặt mật khẩu: tra đúng 1 dòng theo mã băm của token
CREATE UNIQUE INDEX UX_VerificationCodes_TokenHash ON VerificationCodes(TokenHash) WHERE TokenHash IS NOT NULL;
GO

-- Index cluster A
-- Màn Lễ tân tra khách bằng SĐT ở mọi luồng (check-in, bán hàng, tạo hồ sơ) → cột lọc nóng nhất
CREATE INDEX IX_Persons_Phone ON Persons(Phone);
-- Đăng nhập tra theo email; đăng ký tra trùng email
CREATE INDEX IX_Persons_Email ON Persons(Email);
-- Màn danh sách khách của Lễ tân lọc theo trạng thái hồ sơ
CREATE INDEX IX_Customers_Status ON Customers(Status);
-- Danh sách nhân viên lọc theo vị trí + tình trạng làm việc (màn Staff List)
CREATE INDEX IX_Employees_PositionStatus ON Employees(Position, Status);
-- Job mở khóa tài khoản hết hạn quét theo LockedUntil
CREATE INDEX IX_Accounts_LockedUntil ON Accounts(LockedUntil) WHERE LockedUntil IS NOT NULL;
-- Tra mã mới nhất theo email + mục đích khi người dùng nhập mã
CREATE INDEX IX_VerificationCodes_EmailPurpose ON VerificationCodes(Email, Purpose, SentAt DESC);
GO

/* ============================================================================
   CLUSTER B — BÁN HÀNG, THANH TOÁN, QUYỀN DÙNG
   Chuỗi tiền: Packages → Orders → Transactions → (Paid) → Subscriptions
   ============================================================================ */

-- Packages: danh mục gói bán ra. Gói PT bán theo KỲ HẠN, số buổi chỉ là ước tính.
CREATE TABLE Packages (
    PackageId             INT IDENTITY(1,1) NOT NULL,
    PackageName           NVARCHAR(100) NOT NULL,
    Description           NVARCHAR(500) NULL,
    Benefits              NVARCHAR(1000) NULL,  -- mỗi dòng 1 quyền lợi, hiển thị dạng gạch đầu dòng
    PackageType           VARCHAR(15)   NOT NULL, -- Membership | DayPass | PTSingle | PTCouple | SingleSession
    DurationDays          SMALLINT      NULL,     -- thời hạn; DayPass = 1; SingleSession = NULL
    EstimatedSessionCount TINYINT       NULL,     -- CHỈ ước tính cho gói PT, không phải hạn mức
    Price                 INT           NOT NULL, -- VND, đã gồm VAT
    IsActive              BIT           NOT NULL CONSTRAINT DF_Packages_IsActive  DEFAULT (1),
    CreatedAt             DATETIME2(0)  NOT NULL CONSTRAINT DF_Packages_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_Packages PRIMARY KEY (PackageId),
    CONSTRAINT UQ_Packages_Name UNIQUE (PackageName),
    CONSTRAINT CK_Packages_Type  CHECK (PackageType IN ('Membership','DayPass','PTSingle','PTCouple','SingleSession')),
    CONSTRAINT CK_Packages_Name CHECK (LEN(LTRIM(RTRIM(PackageName))) >= 3),
    CONSTRAINT CK_Packages_Price CHECK (Price > 0),
    -- Gói PT bắt buộc có số buổi ước tính để tính giá trị buổi; gói tập thì không
    CONSTRAINT CK_Packages_EstSession CHECK (
        (PackageType IN ('PTSingle','PTCouple','SingleSession') AND EstimatedSessionCount > 0)
        OR (PackageType IN ('Membership','DayPass') AND EstimatedSessionCount IS NULL)
    ),
    -- Gói có thời hạn thì DurationDays > 0; buổi lẻ không có thời hạn
    CONSTRAINT CK_Packages_Duration CHECK (
        (PackageType = 'SingleSession' AND DurationDays IS NULL)
        OR (PackageType <> 'SingleSession' AND DurationDays > 0)
    )
);
GO

-- Vouchers: mã giảm giá. KHÔNG bao giờ áp cho gói PT và buổi lẻ.
CREATE TABLE Vouchers (
    VoucherId           INT IDENTITY(1,1) NOT NULL,
    VoucherCode         VARCHAR(20)  NOT NULL,
    DiscountType        VARCHAR(10)  NOT NULL, -- Percent | Amount
    DiscountValue       INT          NOT NULL,
    StartDate           DATE         NOT NULL,
    EndDate             DATE         NOT NULL,
    MaxUsage            INT          NOT NULL, -- tổng lượt dùng của cả voucher
    MaxUsagePerCustomer TINYINT      NULL,     -- NULL = không giới hạn theo khách
    UsedCount           INT          NOT NULL CONSTRAINT DF_Vouchers_UsedCount     DEFAULT (0), -- +1 khi áp, -1 khi gỡ hoặc đơn bị hủy
    MinOrderValue       INT          NOT NULL CONSTRAINT DF_Vouchers_MinOrderValue DEFAULT (0), -- so với giá TRƯỚC giảm
    ApplicableScope     VARCHAR(25)  NOT NULL, -- MembershipAndDayPass | MembershipOnly
    IsActive            BIT          NOT NULL CONSTRAINT DF_Vouchers_IsActive      DEFAULT (1),

    CONSTRAINT PK_Vouchers PRIMARY KEY (VoucherId),
    CONSTRAINT UQ_Vouchers_Code UNIQUE (VoucherCode),
    CONSTRAINT CK_Vouchers_Code  CHECK (LEN(VoucherCode) BETWEEN 4 AND 20 AND VoucherCode NOT LIKE '%[^A-Z0-9]%'),
    CONSTRAINT CK_Vouchers_MaxUsage CHECK (MaxUsage > 0 AND (MaxUsagePerCustomer IS NULL OR MaxUsagePerCustomer > 0)),
    CONSTRAINT CK_Vouchers_Type  CHECK (DiscountType IN ('Percent','Amount')),
    CONSTRAINT CK_Vouchers_Scope CHECK (ApplicableScope IN ('MembershipAndDayPass','MembershipOnly')),
    CONSTRAINT CK_Vouchers_Dates CHECK (EndDate >= StartDate),
    CONSTRAINT CK_Vouchers_Used  CHECK (UsedCount >= 0 AND UsedCount <= MaxUsage),
    -- Giảm theo % thì 1–100; giảm số tiền thì > 0
    CONSTRAINT CK_Vouchers_Value CHECK (
        (DiscountType = 'Percent' AND DiscountValue BETWEEN 1 AND 100)
        OR (DiscountType = 'Amount' AND DiscountValue > 0)
    )
);
GO

-- Shifts: 2 ca mỗi ngày mở cửa, sinh tự động từ giờ mở cửa. Dùng để đối soát tiền mặt.
CREATE TABLE Shifts (
    ShiftId            INT IDENTITY(1,1) NOT NULL,
    WorkDate           DATE          NOT NULL,
    ShiftType          VARCHAR(10)   NOT NULL, -- Morning | Afternoon
    OpenedByEmployeeId INT           NULL,     -- Lễ tân mở ca
    ClosedByEmployeeId INT           NULL,
    Status             VARCHAR(10)   NOT NULL CONSTRAINT DF_Shifts_Status DEFAULT ('NotOpened'), -- NotOpened | Open | Closed
    OpenedAt           DATETIME2(0)  NULL,
    ClosedAt           DATETIME2(0)  NULL,
    ExpectedCash       INT           NULL,     -- tổng giao dịch tiền mặt Success của ca
    CountedCash        INT           NULL,     -- tiền đếm thực tế khi chốt ca
    VarianceReason     NVARCHAR(500) NULL,     -- bắt buộc khi đếm lệch với kỳ vọng

    CONSTRAINT PK_Shifts PRIMARY KEY (ShiftId),
    CONSTRAINT UQ_Shifts_DateType UNIQUE (WorkDate, ShiftType), -- mỗi ngày đúng 2 ca
    CONSTRAINT FK_Shifts_OpenedBy FOREIGN KEY (OpenedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Shifts_ClosedBy FOREIGN KEY (ClosedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Shifts_Type   CHECK (ShiftType IN ('Morning','Afternoon')),
    CONSTRAINT CK_Shifts_Status CHECK (Status IN ('NotOpened','Open','Closed')),
    -- Ca đã chốt bắt buộc có tiền đếm; lệch tiền thì bắt buộc có lý do
    CONSTRAINT CK_Shifts_Closed CHECK (
        Status <> 'Closed'
        OR (CountedCash IS NOT NULL AND ClosedAt IS NOT NULL AND ClosedByEmployeeId IS NOT NULL
            AND (CountedCash = ExpectedCash OR VarianceReason IS NOT NULL))
    )
);
GO

-- Orders: một lần mua một gói. Snapshot giá/mô tả/VAT tại thời điểm mua để
-- việc đổi giá trong danh mục về sau KHÔNG làm sai đơn cũ.
CREATE TABLE Orders (
    OrderId                    INT IDENTITY(1,1) NOT NULL,
    OrderCode                  VARCHAR(20)   NOT NULL,  -- DH-yyyy-nnnn
    HolderCustomerId           INT           NOT NULL,  -- người đứng tên mua
    PartnerCustomerId          INT           NULL,      -- người đi kèm, chỉ gói couple
    PackageId                  INT           NOT NULL,
    VoucherId                  INT           NULL,      -- tối đa 1 voucher mỗi đơn
    CreatedByEmployeeId        INT           NULL,      -- NULL với đơn online
    ShiftId                    INT           NULL,      -- đơn tại quầy thuộc ca nào
    Channel                    VARCHAR(10)   NOT NULL,  -- Counter | Online
    PackageNameSnapshot        NVARCHAR(100) NOT NULL,
    PackageDescriptionSnapshot NVARCHAR(500) NULL,
    UnitPriceSnapshot          INT           NOT NULL,  -- giá tại thời điểm mua, đã gồm VAT
    DiscountAmount             INT           NOT NULL CONSTRAINT DF_Orders_Discount  DEFAULT (0),
    VatRateSnapshot            TINYINT       NOT NULL,  -- % VAT tại thời điểm mua
    VatAmount                  INT           NOT NULL,  -- phần VAT nằm TRONG số phải trả
    Status                     VARCHAR(10)   NOT NULL CONSTRAINT DF_Orders_Status    DEFAULT ('Pending'), -- Pending | Paid | Cancelled
    PaymentDueAt               DATETIME2(0)  NULL,      -- hạn thanh toán, đồng thời là hạn giữ slot
    CreatedAt                  DATETIME2(0)  NOT NULL CONSTRAINT DF_Orders_CreatedAt DEFAULT (SYSDATETIME()),
    PaidAt                     DATETIME2(0)  NULL,
    CancelType                 VARCHAR(25)   NULL,      -- PaymentTimeout | ReplacedByNewOrder | InvalidPayment | CancelledByReceptionist
    CancelledAt                DATETIME2(0)  NULL,
    CancelReason               NVARCHAR(500) NULL,

    CONSTRAINT PK_Orders PRIMARY KEY (OrderId),
    CONSTRAINT UQ_Orders_OrderCode UNIQUE (OrderCode),
    CONSTRAINT FK_Orders_Holder    FOREIGN KEY (HolderCustomerId)    REFERENCES Customers(CustomerId),
    CONSTRAINT FK_Orders_Partner   FOREIGN KEY (PartnerCustomerId)   REFERENCES Customers(CustomerId),
    CONSTRAINT FK_Orders_Packages  FOREIGN KEY (PackageId)           REFERENCES Packages(PackageId),
    CONSTRAINT FK_Orders_Vouchers  FOREIGN KEY (VoucherId)           REFERENCES Vouchers(VoucherId),
    CONSTRAINT FK_Orders_CreatedBy FOREIGN KEY (CreatedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Orders_Shifts    FOREIGN KEY (ShiftId)             REFERENCES Shifts(ShiftId),
    CONSTRAINT CK_Orders_Channel CHECK (Channel IN ('Counter','Online')),
    CONSTRAINT CK_Orders_Status  CHECK (Status IN ('Pending','Paid','Cancelled')),
    CONSTRAINT CK_Orders_CancelType CHECK (CancelType IS NULL OR CancelType IN
        ('PaymentTimeout','ReplacedByNewOrder','InvalidPayment','CancelledByReceptionist')),
    CONSTRAINT CK_Orders_Code  CHECK (OrderCode LIKE 'DH-[0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9]'),
    CONSTRAINT CK_Orders_Price CHECK (UnitPriceSnapshot > 0),
    CONSTRAINT CK_Orders_Vat   CHECK (VatRateSnapshot BETWEEN 0 AND 20 AND VatAmount >= 0),
    CONSTRAINT CK_Orders_Discount CHECK (DiscountAmount >= 0 AND DiscountAmount <= UnitPriceSnapshot),
    CONSTRAINT CK_Orders_Partner  CHECK (PartnerCustomerId IS NULL OR PartnerCustomerId <> HolderCustomerId),
    -- Đơn tại quầy luôn có người lập và thuộc một ca; đơn online thì không
    CONSTRAINT CK_Orders_CounterFields CHECK (
        (Channel = 'Counter' AND CreatedByEmployeeId IS NOT NULL AND ShiftId IS NOT NULL)
        OR (Channel = 'Online' AND CreatedByEmployeeId IS NULL AND ShiftId IS NULL)
    ),
    -- Đơn đã hủy bắt buộc ghi loại hủy và thời điểm hủy
    CONSTRAINT CK_Orders_Cancelled CHECK (
        (Status = 'Cancelled' AND CancelType IS NOT NULL AND CancelledAt IS NOT NULL)
        OR (Status <> 'Cancelled' AND CancelType IS NULL AND CancelledAt IS NULL)
    ),
    CONSTRAINT CK_Orders_Paid CHECK ((Status = 'Paid' AND PaidAt IS NOT NULL) OR (Status <> 'Paid' AND PaidAt IS NULL))
);
GO

-- Transactions: MỖI LẦN THỬ thanh toán là 1 dòng. Một đơn online có thể thử
-- nhiều lần (lỗi, hết hạn link) nên tách khỏi Orders.
CREATE TABLE Transactions (
    TransactionId    INT IDENTITY(1,1) NOT NULL,
    OrderId          INT           NOT NULL,
    Amount           INT           NOT NULL,  -- snapshot số phải trả tại lúc tạo giao dịch
    PaymentMethod    VARCHAR(15)   NOT NULL,  -- Cash | Transfer | OnlineGateway
    GatewayReference VARCHAR(50)   NULL,      -- mã tham chiếu VNPay, mỗi lần thử một mã
    Status           VARCHAR(10)   NOT NULL CONSTRAINT DF_Transactions_Status      DEFAULT ('Pending'), -- Pending | Success | Failed | Expired
    FailureReason    NVARCHAR(255) NULL,
    NeedsRefund      BIT           NOT NULL CONSTRAINT DF_Transactions_NeedsRefund DEFAULT (0),         -- tiền về khi đơn đã hủy/sai số tiền → Manager hoàn tay
    CreatedAt        DATETIME2(0)  NOT NULL CONSTRAINT DF_Transactions_CreatedAt   DEFAULT (SYSDATETIME()),
    ExpiresAt        DATETIME2(0)  NULL,      -- hạn của link thanh toán (5 phút), không vượt hạn đơn
    CompletedAt      DATETIME2(0)  NULL,

    CONSTRAINT PK_Transactions PRIMARY KEY (TransactionId),
    CONSTRAINT FK_Transactions_Orders FOREIGN KEY (OrderId) REFERENCES Orders(OrderId),
    CONSTRAINT CK_Transactions_Method CHECK (PaymentMethod IN ('Cash','Transfer','OnlineGateway')),
    CONSTRAINT CK_Transactions_Status CHECK (Status IN ('Pending','Success','Failed','Expired')),
    CONSTRAINT CK_Transactions_Amount CHECK (Amount > 0)
);
GO

-- Mỗi đơn tối đa 1 giao dịch Success và tối đa 1 giao dịch Pending tại một thời điểm.
-- Dùng filtered unique index để DB tự chặn, không phụ thuộc code.
CREATE UNIQUE INDEX UX_Transactions_OneSuccessPerOrder ON Transactions(OrderId) WHERE Status = 'Success';
CREATE UNIQUE INDEX UX_Transactions_OnePendingPerOrder ON Transactions(OrderId) WHERE Status = 'Pending';
GO

-- Subscriptions: QUYỀN DÙNG sinh ra khi đơn được thanh toán. Đây mới là thứ
-- check-in và buổi tập tiêu thụ, không phải Orders.
CREATE TABLE Subscriptions (
    SubscriptionId        INT IDENTITY(1,1) NOT NULL,
    OrderId               INT          NOT NULL,  -- 1 đơn sinh tối đa 1 quyền dùng
    CustomerId            INT          NOT NULL,  -- người đứng tên
    SubscriptionType      VARCHAR(15)  NOT NULL,  -- Membership | DayPass | PTSingle | PTCouple | SingleSession
    PtEmployeeId          INT          NULL,      -- PT phụ trách, chỉ gói PT mới có
    StartDate             DATE         NOT NULL,
    EndDate               DATE         NOT NULL,  -- NGÀY CUỐI CÙNG còn dùng được (tính cả ngày này)
    GeneratedSessionCount TINYINT      NULL,      -- số buổi thực sinh, CHỐT khi đơn Paid
    SwapsUsed             TINYINT      NOT NULL CONSTRAINT DF_Subscriptions_Swaps  DEFAULT (0),        -- đã swap mấy lần, trần là P-08
    FreezeUsed            TINYINT      NOT NULL CONSTRAINT DF_Subscriptions_Freeze DEFAULT (0),        -- đã bảo lưu mấy lần, trần là P-01
    Status                VARCHAR(10)  NOT NULL CONSTRAINT DF_Subscriptions_Status DEFAULT ('Active'), -- Active | Frozen | Used | Expired | Completed | Cancelled

    CONSTRAINT PK_Subscriptions PRIMARY KEY (SubscriptionId),
    CONSTRAINT UQ_Subscriptions_OrderId UNIQUE (OrderId),
    CONSTRAINT FK_Subscriptions_Orders    FOREIGN KEY (OrderId)      REFERENCES Orders(OrderId),
    CONSTRAINT FK_Subscriptions_Customers FOREIGN KEY (CustomerId)   REFERENCES Customers(CustomerId),
    CONSTRAINT FK_Subscriptions_Pt        FOREIGN KEY (PtEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Subscriptions_Type   CHECK (SubscriptionType IN ('Membership','DayPass','PTSingle','PTCouple','SingleSession')),
    CONSTRAINT CK_Subscriptions_Status CHECK (Status IN ('Active','Frozen','Used','Expired','Completed','Cancelled')),
    CONSTRAINT CK_Subscriptions_Dates  CHECK (EndDate >= StartDate),
    -- Gói PT bắt buộc có PT phụ trách; gói tập / gói ngày thì không
    CONSTRAINT CK_Subscriptions_Pt CHECK (
        (SubscriptionType IN ('PTSingle','PTCouple','SingleSession') AND PtEmployeeId IS NOT NULL)
        OR (SubscriptionType IN ('Membership','DayPass') AND PtEmployeeId IS NULL)
    )
);
GO

-- FreezeRecords: mỗi lần bảo lưu gói tập. Chỉ gói Membership mới bảo lưu được.
CREATE TABLE FreezeRecords (
    FreezeRecordId      INT IDENTITY(1,1) NOT NULL,
    SubscriptionId      INT           NOT NULL,
    HandledByEmployeeId INT           NOT NULL,  -- Lễ tân xử lý
    StartDate           DATE          NOT NULL,
    PlannedEndDate      DATE          NOT NULL,  -- tối đa P-02 ngày
    ActualEndDate       DATE          NULL,      -- NULL = đang bảo lưu; set khi mở lại hoặc hủy sớm
    Reason              NVARCHAR(500) NOT NULL,
    CreatedAt           DATETIME2(0)  NOT NULL CONSTRAINT DF_FreezeRecords_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_FreezeRecords PRIMARY KEY (FreezeRecordId),
    CONSTRAINT FK_FreezeRecords_Subscriptions FOREIGN KEY (SubscriptionId)      REFERENCES Subscriptions(SubscriptionId),
    CONSTRAINT FK_FreezeRecords_Employees     FOREIGN KEY (HandledByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_FreezeRecords_Reason CHECK (LEN(LTRIM(RTRIM(Reason))) >= 5),
    CONSTRAINT CK_FreezeRecords_Dates  CHECK (PlannedEndDate >= StartDate),
    CONSTRAINT CK_FreezeRecords_Actual CHECK (ActualEndDate IS NULL OR ActualEndDate >= StartDate)
);
GO

-- CheckIns: mỗi lượt khách vào phòng tập. Gắn với Subscription để biết vào bằng gói nào.
CREATE TABLE CheckIns (
    CheckInId            INT IDENTITY(1,1) NOT NULL,
    SubscriptionId       INT          NOT NULL, -- chỉ gói tập / gói ngày mới sinh check-in
    VerifiedByEmployeeId INT          NOT NULL, -- Lễ tân đối chiếu ảnh và xác nhận
    CheckedInAt          DATETIME2(0) NOT NULL CONSTRAINT DF_CheckIns_CheckedInAt   DEFAULT (SYSDATETIME()),
    PhotoVerified        BIT          NOT NULL CONSTRAINT DF_CheckIns_PhotoVerified DEFAULT (0), -- 0 với gói ngày (không cần ảnh)

    CONSTRAINT PK_CheckIns PRIMARY KEY (CheckInId),
    CONSTRAINT FK_CheckIns_Subscriptions FOREIGN KEY (SubscriptionId)       REFERENCES Subscriptions(SubscriptionId),
    CONSTRAINT FK_CheckIns_Employees     FOREIGN KEY (VerifiedByEmployeeId) REFERENCES Employees(EmployeeId)
);
GO

-- CustomerDeletionRequests: yêu cầu xóa hồ sơ khách. Còn gói đang chạy thì phải chờ Manager duyệt.
CREATE TABLE CustomerDeletionRequests (
    DeletionRequestId      INT IDENTITY(1,1) NOT NULL,
    CustomerId             INT           NOT NULL,
    RequestedByEmployeeId  INT           NOT NULL, -- Lễ tân gửi yêu cầu
    ReviewedByEmployeeId   INT           NULL,     -- Manager duyệt / từ chối
    Reason                 NVARCHAR(500) NOT NULL,
    Status                 VARCHAR(10)   NOT NULL CONSTRAINT DF_DeletionRequests_Status      DEFAULT ('Pending'), -- Pending | Approved | Rejected
    RejectReason           NVARCHAR(500) NULL,
    RequestedAt            DATETIME2(0)  NOT NULL CONSTRAINT DF_DeletionRequests_RequestedAt DEFAULT (SYSDATETIME()),
    ReviewedAt             DATETIME2(0)  NULL,

    CONSTRAINT PK_CustomerDeletionRequests PRIMARY KEY (DeletionRequestId),
    CONSTRAINT FK_DeletionRequests_Customers  FOREIGN KEY (CustomerId)            REFERENCES Customers(CustomerId),
    CONSTRAINT FK_DeletionRequests_Requester  FOREIGN KEY (RequestedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_DeletionRequests_Reviewer   FOREIGN KEY (ReviewedByEmployeeId)  REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_DeletionRequests_Reason CHECK (LEN(LTRIM(RTRIM(Reason))) >= 5),
    CONSTRAINT CK_DeletionRequests_Status CHECK (Status IN ('Pending','Approved','Rejected')),
    -- Từ chối bắt buộc có lý do (luật chung: mọi hành động tiêu cực phải có lý do)
    CONSTRAINT CK_DeletionRequests_Reject CHECK (Status <> 'Rejected' OR RejectReason IS NOT NULL)
);
GO

-- Index cluster B
-- Job hủy đơn quá hạn quét đơn online đang Pending theo hạn thanh toán → chạy mỗi phút
CREATE INDEX IX_Orders_StatusDue ON Orders(Status, PaymentDueAt) WHERE Status = 'Pending';
-- Màn lịch sử giao dịch của khách lọc theo người mua, sắp xếp mới nhất trước
CREATE INDEX IX_Orders_Holder ON Orders(HolderCustomerId, CreatedAt DESC);
-- Chốt ca cần liệt kê đơn của ca đó và chặn khi còn đơn Pending
CREATE INDEX IX_Orders_Shift ON Orders(ShiftId) WHERE ShiftId IS NOT NULL;
-- Danh sách giao dịch cần hoàn tiền tay của Manager
CREATE INDEX IX_Transactions_NeedsRefund ON Transactions(NeedsRefund, CreatedAt DESC) WHERE NeedsRefund = 1;
-- Check-in tra nhanh gói còn hiệu lực của khách theo ngày
CREATE INDEX IX_Subscriptions_CustomerStatus ON Subscriptions(CustomerId, Status, EndDate);
-- Job hết hạn gói quét theo ngày kết thúc
CREATE INDEX IX_Subscriptions_EndDate ON Subscriptions(EndDate, Status);
-- Job mở lại bảo lưu quét các bản ghi chưa có ngày kết thúc thực tế
CREATE INDEX IX_FreezeRecords_Open ON FreezeRecords(PlannedEndDate) WHERE ActualEndDate IS NULL;
-- Báo cáo lượt check-in theo khoảng ngày
CREATE INDEX IX_CheckIns_CheckedInAt ON CheckIns(CheckedInAt);
-- Hàng chờ duyệt xóa hồ sơ của Manager
CREATE INDEX IX_DeletionRequests_Status ON CustomerDeletionRequests(Status, RequestedAt);
GO

/* ============================================================================
   CLUSTER C — LỊCH PT & BUỔI TẬP
   TimeSlot có 3 trạng thái Free → Held → Booked; Held xảy ra NGAY khi lập đơn.
   ============================================================================ */

CREATE TABLE TimeSlots (
    TimeSlotId    INT IDENTITY(1,1) NOT NULL,
    PtEmployeeId  INT          NOT NULL,  -- PT công bố khung giờ rảnh này
    SlotDate      DATE         NOT NULL,
    StartTime     TIME(0)      NOT NULL,  -- giờ bắt đầu theo lưới 15 phút
    EndTime       TIME(0)      NOT NULL,  -- luôn = StartTime + 90 phút (P-06)
    Status        VARCHAR(10)  NOT NULL CONSTRAINT DF_TimeSlots_Status    DEFAULT ('Free'), -- Free | Held | Booked
    HeldByOrderId INT          NULL,      -- đơn đang giữ chỗ (chỉ khi Status = Held)
    HoldExpiresAt DATETIME2(0) NULL,      -- = hạn thanh toán của đơn giữ chỗ
    CreatedAt     DATETIME2(0) NOT NULL CONSTRAINT DF_TimeSlots_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_TimeSlots PRIMARY KEY (TimeSlotId),
    -- Một PT không thể có 2 khung giờ trùng giờ bắt đầu trong cùng ngày
    CONSTRAINT UQ_TimeSlots_PtDateStart UNIQUE (PtEmployeeId, SlotDate, StartTime),
    CONSTRAINT FK_TimeSlots_Employees FOREIGN KEY (PtEmployeeId)  REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_TimeSlots_Orders    FOREIGN KEY (HeldByOrderId) REFERENCES Orders(OrderId),
    CONSTRAINT CK_TimeSlots_Status CHECK (Status IN ('Free','Held','Booked')),
    CONSTRAINT CK_TimeSlots_Grid     CHECK (DATEPART(MINUTE, StartTime) IN (0,15,30,45) AND DATEPART(SECOND, StartTime) = 0),
    CONSTRAINT CK_TimeSlots_Duration CHECK (DATEDIFF(MINUTE, StartTime, EndTime) = 90),
    CONSTRAINT CK_TimeSlots_Time   CHECK (EndTime > StartTime),
    -- Chỉ slot đang Held mới được gắn đơn giữ chỗ và hạn giữ
    CONSTRAINT CK_TimeSlots_Hold CHECK (
        (Status = 'Held' AND HeldByOrderId IS NOT NULL AND HoldExpiresAt IS NOT NULL)
        OR (Status <> 'Held' AND HeldByOrderId IS NULL AND HoldExpiresAt IS NULL)
    )
);
GO

CREATE TABLE Sessions (
    SessionId             INT IDENTITY(1,1) NOT NULL,
    SubscriptionId        INT           NOT NULL, -- buổi sinh ra từ gói nào
    TimeSlotId            INT           NOT NULL, -- đang chiếm khung giờ nào
    PtEmployeeId          INT           NOT NULL, -- PT DẠY buổi này; đổi khi chuyển PT → hoa hồng đi theo
    Status                VARCHAR(10)   NOT NULL CONSTRAINT DF_Sessions_Status       DEFAULT ('Scheduled'), -- Scheduled | Completed | Missed | Cancelled
    SessionValue          INT           NOT NULL, -- (giá − giảm) / số buổi thực sinh, chốt khi Paid
    CommissionAmount      INT           NOT NULL, -- tỷ lệ hoa hồng PT × SessionValue
    AttendanceMarkedAt    DATETIME2(0)  NULL,
    IsAutoClosed          BIT           NOT NULL CONSTRAINT DF_Sessions_AutoClosed   DEFAULT (0), -- 1 = job tự đóng sau cửa sổ điểm danh (P-07)
    MissedReason          NVARCHAR(255) NULL,     -- no check-in | no membership | membership frozen
    CancelReason          NVARCHAR(500) NULL,
    CancelledAt           DATETIME2(0)  NULL,
    CancelledByAccountId  INT           NULL,     -- tài khoản bấm hủy (khách tự hủy)
    RefundAgreed          BIT           NOT NULL CONSTRAINT DF_Sessions_RefundAgreed DEFAULT (0), -- PT hủy: khách đã đồng ý nhận tiền mặt
    ResolutionType        VARCHAR(20)   NULL,     -- Reschedule | Transfer | Cancel | CustomerRequest

    CONSTRAINT PK_Sessions PRIMARY KEY (SessionId),
    CONSTRAINT FK_Sessions_Subscriptions FOREIGN KEY (SubscriptionId)       REFERENCES Subscriptions(SubscriptionId),
    CONSTRAINT FK_Sessions_TimeSlots     FOREIGN KEY (TimeSlotId)           REFERENCES TimeSlots(TimeSlotId),
    CONSTRAINT FK_Sessions_Employees     FOREIGN KEY (PtEmployeeId)         REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Sessions_Accounts      FOREIGN KEY (CancelledByAccountId) REFERENCES Accounts(AccountId),
    CONSTRAINT CK_Sessions_Status     CHECK (Status IN ('Scheduled','Completed','Missed','Cancelled')),
    CONSTRAINT CK_Sessions_Resolution CHECK (ResolutionType IS NULL OR ResolutionType IN ('Reschedule','Transfer','Cancel','CustomerRequest')),
    CONSTRAINT CK_Sessions_Value      CHECK (SessionValue >= 0 AND CommissionAmount >= 0),
    -- Buổi bị hủy bắt buộc có lý do và thời điểm hủy
    CONSTRAINT CK_Sessions_Cancelled CHECK (
        (Status = 'Cancelled' AND CancelReason IS NOT NULL AND CancelledAt IS NOT NULL)
        OR (Status <> 'Cancelled')
    ),
    CONSTRAINT CK_Sessions_Missed CHECK (Status <> 'Missed' OR MissedReason IS NOT NULL)
);
GO

-- Một khung giờ chỉ được có TỐI ĐA 1 buổi còn hiệu lực. Buổi đã hủy hoặc đã
-- Missed thì slot được nhả, nên slot có thể xuất hiện lại ở buổi khác về sau.
CREATE UNIQUE INDEX UX_Sessions_OneActivePerSlot ON Sessions(TimeSlotId) WHERE Status = 'Scheduled';
GO

CREATE TABLE SessionSwapLogs (
    SessionSwapLogId       INT IDENTITY(1,1) NOT NULL,
    SessionId              INT          NOT NULL,
    OldTimeSlotId          INT          NOT NULL,
    NewTimeSlotId          INT          NOT NULL,
    RequestedByAccountId   INT          NULL,   -- NULL khi PT đổi hộ khách không có tài khoản
    SwappedAt              DATETIME2(0) NOT NULL CONSTRAINT DF_SwapLogs_SwappedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_SessionSwapLogs PRIMARY KEY (SessionSwapLogId),
    CONSTRAINT FK_SwapLogs_Sessions  FOREIGN KEY (SessionId)            REFERENCES Sessions(SessionId),
    CONSTRAINT FK_SwapLogs_OldSlot   FOREIGN KEY (OldTimeSlotId)        REFERENCES TimeSlots(TimeSlotId),
    CONSTRAINT FK_SwapLogs_NewSlot   FOREIGN KEY (NewTimeSlotId)        REFERENCES TimeSlots(TimeSlotId),
    CONSTRAINT FK_SwapLogs_Accounts  FOREIGN KEY (RequestedByAccountId) REFERENCES Accounts(AccountId),
    CONSTRAINT CK_SwapLogs_Slots CHECK (OldTimeSlotId <> NewTimeSlotId)
);
GO

-- Index cluster C
-- Màn đặt buổi và bán gói PT luôn hỏi: PT này ngày này còn slot Free nào
CREATE INDEX IX_TimeSlots_PtDateStatus ON TimeSlots(PtEmployeeId, SlotDate, Status);
-- Job nhả slot quá hạn giữ chỗ quét theo HoldExpiresAt
CREATE INDEX IX_TimeSlots_HoldExpires ON TimeSlots(HoldExpiresAt) WHERE Status = 'Held';
-- Màn điểm danh của PT lọc buổi theo PT + trạng thái
CREATE INDEX IX_Sessions_PtStatus ON Sessions(PtEmployeeId, Status);
-- Màn lịch của khách liệt kê buổi theo gói
CREATE INDEX IX_Sessions_Subscription ON Sessions(SubscriptionId, Status);
GO

/* ============================================================================
   CLUSTER D — NHÂN SỰ & CƠ SỞ VẬT CHẤT
   ============================================================================ */

CREATE TABLE LeaveRequests (
    LeaveRequestId       INT IDENTITY(1,1) NOT NULL,
    EmployeeId           INT           NOT NULL, -- người nộp đơn (PT hoặc Lễ tân)
    ReviewedByEmployeeId INT           NULL,     -- Manager duyệt; NULL khi hệ thống tự từ chối
    LeaveType            VARCHAR(10)   NOT NULL, -- Annual | Sick | Unpaid
    LeaveDate            DATE          NOT NULL,
    ShiftType            VARCHAR(10)   NOT NULL, -- Morning | Afternoon | FullDay
    Reason               NVARCHAR(500) NOT NULL,
    Status               VARCHAR(10)   NOT NULL CONSTRAINT DF_LeaveRequests_Status      DEFAULT ('Pending'), -- Pending | Approved | Rejected
    RejectReason         NVARCHAR(500) NULL,
    SubmittedAt          DATETIME2(0)  NOT NULL CONSTRAINT DF_LeaveRequests_SubmittedAt DEFAULT (SYSDATETIME()), -- phải trước giờ chốt nộp (P-12) của ngày hôm trước
    ReviewedAt           DATETIME2(0)  NULL,
    ReminderSentAt       DATETIME2(0)  NULL,     -- JOB-07 đã nhắc Manager lúc nào; NULL = chưa nhắc → job chỉ nhắc đúng 1 lần

    CONSTRAINT PK_LeaveRequests PRIMARY KEY (LeaveRequestId),
    CONSTRAINT FK_LeaveRequests_Employee FOREIGN KEY (EmployeeId)           REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_LeaveRequests_Reviewer FOREIGN KEY (ReviewedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_LeaveRequests_Type   CHECK (LeaveType IN ('Annual','Sick','Unpaid')),
    CONSTRAINT CK_LeaveRequests_Shift  CHECK (ShiftType IN ('Morning','Afternoon','FullDay')),
    CONSTRAINT CK_LeaveRequests_Reason CHECK (LEN(LTRIM(RTRIM(Reason))) >= 5),
    CONSTRAINT CK_LeaveRequests_Status CHECK (Status IN ('Pending','Approved','Rejected')),
    CONSTRAINT CK_LeaveRequests_Reject CHECK (Status <> 'Rejected' OR RejectReason IS NOT NULL)
);
GO

CREATE TABLE Facilities (
    FacilityId   INT IDENTITY(1,1) NOT NULL,
    FacilityName NVARCHAR(100) NOT NULL,
    Category     NVARCHAR(50)  NOT NULL,  -- Cardio | FreeWeights | Machines | Amenities
    Location     NVARCHAR(100) NULL,
    Quantity     SMALLINT      NOT NULL CONSTRAINT DF_Facilities_Quantity DEFAULT (1),
    Status       VARCHAR(20)   NOT NULL CONSTRAINT DF_Facilities_Status   DEFAULT ('Available'), -- Available | UnderMaintenance | Retired

    CONSTRAINT PK_Facilities PRIMARY KEY (FacilityId),
    CONSTRAINT CK_Facilities_Quantity CHECK (Quantity >= 1),
    CONSTRAINT CK_Facilities_Name CHECK (LEN(LTRIM(RTRIM(FacilityName))) >= 3),
    CONSTRAINT CK_Facilities_Status   CHECK (Status IN ('Available','UnderMaintenance','Retired'))
);
GO

CREATE TABLE MaintenanceRequests (
    MaintenanceRequestId  INT IDENTITY(1,1) NOT NULL,
    FacilityId            INT           NOT NULL,
    ReportedByEmployeeId  INT           NOT NULL, -- Lễ tân báo hỏng
    ReviewedByEmployeeId  INT           NULL,     -- Manager duyệt / từ chối
    ClosedByEmployeeId    INT           NULL,     -- Lễ tân đóng sau khi kiểm tra đã sửa xong
    Description           NVARCHAR(500) NOT NULL,
    Status                VARCHAR(12)   NOT NULL CONSTRAINT DF_Maintenance_Status     DEFAULT ('Open'), -- Open → InProgress → Closed, hoặc Open → Rejected
    RejectReason          NVARCHAR(500) NULL,
    ReportedAt            DATETIME2(0)  NOT NULL CONSTRAINT DF_Maintenance_ReportedAt DEFAULT (SYSDATETIME()),
    ReviewedAt            DATETIME2(0)  NULL,
    ClosedAt              DATETIME2(0)  NULL,

    CONSTRAINT PK_MaintenanceRequests PRIMARY KEY (MaintenanceRequestId),
    CONSTRAINT FK_Maintenance_Facilities FOREIGN KEY (FacilityId)           REFERENCES Facilities(FacilityId),
    CONSTRAINT FK_Maintenance_Reporter   FOREIGN KEY (ReportedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Maintenance_Reviewer   FOREIGN KEY (ReviewedByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_Maintenance_Closer     FOREIGN KEY (ClosedByEmployeeId)   REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Maintenance_Description CHECK (LEN(LTRIM(RTRIM(Description))) >= 10),
    CONSTRAINT CK_Maintenance_Status CHECK (Status IN ('Open','InProgress','Rejected','Closed')),
    CONSTRAINT CK_Maintenance_Reject CHECK (Status <> 'Rejected' OR RejectReason IS NOT NULL),
    CONSTRAINT CK_Maintenance_Closed CHECK (Status <> 'Closed' OR (ClosedByEmployeeId IS NOT NULL AND ClosedAt IS NOT NULL))
);
GO

-- StaffAttendances: chấm công nhân viên. CHƯA làm ở phiên bản này (limitation),
-- mô hình hóa sẵn để sau này thêm mà không phải sửa bảng cũ.
-- Cố ý TÁCH khỏi CheckIns vì check-in khách gắn với gói tập, chấm công gắn với ca làm.
CREATE TABLE StaffAttendances (
    StaffAttendanceId INT IDENTITY(1,1) NOT NULL,
    EmployeeId        INT           NOT NULL,
    WorkDate          DATE          NOT NULL,
    ShiftType         VARCHAR(10)   NOT NULL, -- Morning | Afternoon
    CheckInAt         DATETIME2(0)  NULL,
    CheckInPhoto      NVARCHAR(255) NULL,     -- ảnh chụp lúc vào ca, KHÔNG dùng cho check-in hội viên
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
-- Hàng chờ duyệt của Manager, và job tự từ chối đơn quá hạn duyệt
CREATE INDEX IX_LeaveRequests_StatusDate ON LeaveRequests(Status, LeaveDate);
-- Kiểm tra định biên: đếm người đã được duyệt nghỉ theo ngày + ca
CREATE INDEX IX_LeaveRequests_DateShift ON LeaveRequests(LeaveDate, ShiftType, Status);
-- Hàng chờ duyệt bảo trì của Manager
CREATE INDEX IX_Maintenance_Status ON MaintenanceRequests(Status, ReportedAt);
-- Bảng lương / báo cáo công theo tháng của từng nhân viên
CREATE INDEX IX_StaffAttendances_EmpDate ON StaffAttendances(EmployeeId, WorkDate);
GO

/* ============================================================================
   CLUSTER E — NỘI DUNG GIỮA PT VÀ KHÁCH
   ============================================================================ */

CREATE TABLE PrivateNotes (
    PrivateNoteId INT IDENTITY(1,1) NOT NULL,
    PtEmployeeId  INT            NOT NULL,
    CustomerId    INT            NOT NULL,
    Content       NVARCHAR(2000) NOT NULL,
    UpdatedAt     DATETIME2(0)   NOT NULL CONSTRAINT DF_PrivateNotes_UpdatedAt DEFAULT (SYSDATETIME()),
    DeletedAt     DATETIME2(0)   NULL,   -- xóa mềm: ẩn vĩnh viễn, PT viết được bản mới

    CONSTRAINT PK_PrivateNotes PRIMARY KEY (PrivateNoteId),
    CONSTRAINT FK_PrivateNotes_Employees FOREIGN KEY (PtEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_PrivateNotes_Customers FOREIGN KEY (CustomerId)   REFERENCES Customers(CustomerId)
);
GO

-- Mỗi cặp (PT, khách) chỉ có TỐI ĐA 1 ghi chú đang dùng; bản đã xóa không tính
CREATE UNIQUE INDEX UX_PrivateNotes_ActivePerPair ON PrivateNotes(PtEmployeeId, CustomerId) WHERE DeletedAt IS NULL;
GO

CREATE TABLE TrainingPlans (
    TrainingPlanId INT IDENTITY(1,1) NOT NULL,
    PtEmployeeId   INT             NOT NULL,
    CustomerId     INT             NOT NULL,
    PlanType       VARCHAR(10)     NOT NULL, -- Workout | Meal
    Title          NVARCHAR(200)   NOT NULL,
    Content        NVARCHAR(MAX)   NOT NULL,
    IsPublished    BIT             NOT NULL CONSTRAINT DF_TrainingPlans_IsPublished DEFAULT (0), -- khách chỉ thấy bản đã xuất bản
    UpdatedAt      DATETIME2(0)    NOT NULL CONSTRAINT DF_TrainingPlans_UpdatedAt   DEFAULT (SYSDATETIME()),
    DeletedAt      DATETIME2(0)    NULL,

    CONSTRAINT PK_TrainingPlans PRIMARY KEY (TrainingPlanId),
    CONSTRAINT FK_TrainingPlans_Employees FOREIGN KEY (PtEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT FK_TrainingPlans_Customers FOREIGN KEY (CustomerId)   REFERENCES Customers(CustomerId),
    CONSTRAINT CK_TrainingPlans_Type CHECK (PlanType IN ('Workout','Meal'))
);
GO

-- Mỗi bộ (PT, khách, loại) chỉ có tối đa 1 bản đang dùng
CREATE UNIQUE INDEX UX_TrainingPlans_ActivePerTriple ON TrainingPlans(PtEmployeeId, CustomerId, PlanType) WHERE DeletedAt IS NULL;
GO

/* ============================================================================
   CLUSTER F — GIAO TIẾP & NỘI DUNG CÔNG KHAI
   ============================================================================ */

CREATE TABLE Feedbacks (
    FeedbackId       INT IDENTITY(1,1) NOT NULL,
    CustomerId       INT            NOT NULL,
    Content          NVARCHAR(1000) NOT NULL,
    SubmittedAt      DATETIME2(0)   NOT NULL CONSTRAINT DF_Feedbacks_SubmittedAt DEFAULT (SYSDATETIME()),
    IsRead           BIT            NOT NULL CONSTRAINT DF_Feedbacks_IsRead      DEFAULT (0), -- chỉ Manager đọc được
    ReadAt           DATETIME2(0)   NULL,
    ReadByEmployeeId INT            NULL,

    CONSTRAINT PK_Feedbacks PRIMARY KEY (FeedbackId),
    CONSTRAINT FK_Feedbacks_Customers FOREIGN KEY (CustomerId)       REFERENCES Customers(CustomerId),
    CONSTRAINT FK_Feedbacks_Employees FOREIGN KEY (ReadByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Feedbacks_Content CHECK (LEN(LTRIM(RTRIM(Content))) BETWEEN 10 AND 1000),
    CONSTRAINT CK_Feedbacks_Read CHECK ((IsRead = 1 AND ReadAt IS NOT NULL) OR (IsRead = 0 AND ReadAt IS NULL))
);
GO

CREATE TABLE BlogPosts (
    BlogPostId       INT IDENTITY(1,1) NOT NULL,
    AuthorEmployeeId INT            NOT NULL, -- Manager viết
    Title            NVARCHAR(200)  NOT NULL,
    Category         NVARCHAR(30)   NOT NULL, -- Training | Nutrition | News
    Excerpt          NVARCHAR(300)  NULL,
    Content          NVARCHAR(MAX)  NOT NULL,
    CoverImage       NVARCHAR(255)  NULL,
    Status           VARCHAR(10)    NOT NULL CONSTRAINT DF_BlogPosts_Status DEFAULT ('Inactive'), -- Active (hiện trên web) | Inactive (gỡ xuống)
    PublishedAt      DATETIME2(0)   NULL,

    CONSTRAINT PK_BlogPosts PRIMARY KEY (BlogPostId),
    CONSTRAINT FK_BlogPosts_Employees FOREIGN KEY (AuthorEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_BlogPosts_Title CHECK (LEN(LTRIM(RTRIM(Title))) >= 5),
    CONSTRAINT CK_BlogPosts_Status CHECK (Status IN ('Active','Inactive')),
    CONSTRAINT CK_BlogPosts_Published CHECK (Status <> 'Active' OR PublishedAt IS NOT NULL)
);
GO

CREATE TABLE Notifications (
    NotificationId    INT IDENTITY(1,1) NOT NULL,
    SentByEmployeeId  INT           NULL,     -- NULL = hệ thống tự gửi
    Title             NVARCHAR(100) NOT NULL,
    Content           NVARCHAR(500) NOT NULL,
    TargetType        VARCHAR(10)   NOT NULL, -- All | Role | Customer
    SentAt            DATETIME2(0)  NOT NULL CONSTRAINT DF_Notifications_SentAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_Notifications PRIMARY KEY (NotificationId),
    CONSTRAINT FK_Notifications_Employees FOREIGN KEY (SentByEmployeeId) REFERENCES Employees(EmployeeId),
    CONSTRAINT CK_Notifications_Text CHECK (LEN(LTRIM(RTRIM(Title))) >= 5 AND LEN(LTRIM(RTRIM(Content))) >= 10),
    CONSTRAINT CK_Notifications_Target CHECK (TargetType IN ('All','Role','Customer'))
);
GO

-- NotificationRecipients: giải quan hệ nhiều–nhiều giữa Notification và Account,
-- đồng thời lưu trạng thái đã đọc của từng người.
CREATE TABLE NotificationRecipients (
    NotificationRecipientId INT IDENTITY(1,1) NOT NULL,
    NotificationId          INT          NOT NULL,
    AccountId               INT          NOT NULL, -- chỉ người CÓ tài khoản mới nhận được
    ReadAt                  DATETIME2(0) NULL,

    CONSTRAINT PK_NotificationRecipients PRIMARY KEY (NotificationRecipientId),
    CONSTRAINT UQ_NotificationRecipients UNIQUE (NotificationId, AccountId),
    CONSTRAINT FK_NotifRecipients_Notifications FOREIGN KEY (NotificationId) REFERENCES Notifications(NotificationId),
    CONSTRAINT FK_NotifRecipients_Accounts      FOREIGN KEY (AccountId)      REFERENCES Accounts(AccountId)
);
GO

-- Index cluster F
-- Màn Feedback List của Manager mặc định lọc "chưa đọc", sắp xếp mới nhất trước
CREATE INDEX IX_Feedbacks_IsRead ON Feedbacks(IsRead, SubmittedAt DESC);
-- Trang blog công khai chỉ lấy bài Active, mới nhất trước
CREATE INDEX IX_BlogPosts_StatusPublished ON BlogPosts(Status, PublishedAt DESC);
-- Chuông thông báo đếm số chưa đọc của từng tài khoản
CREATE INDEX IX_NotifRecipients_AccountUnread ON NotificationRecipients(AccountId) WHERE ReadAt IS NULL;
GO

/* ============================================================================
   CLUSTER G — CẤU HÌNH & LƯU VẾT
   ============================================================================ */

-- SystemConfigs: nơi lưu 20 tham số P-01…P-20 dưới dạng key–value
CREATE TABLE SystemConfigs (
    SystemConfigId      INT IDENTITY(1,1) NOT NULL,
    ConfigKey           VARCHAR(50)   NOT NULL,  -- P-01, P-02, ... P-20
    ConfigValue         NVARCHAR(200) NOT NULL,
    Description         NVARCHAR(255) NOT NULL,  -- giải thích tham số dùng ở đâu
    UpdatedAt           DATETIME2(0)  NOT NULL CONSTRAINT DF_SystemConfigs_UpdatedAt DEFAULT (SYSDATETIME()),
    UpdatedByEmployeeId INT           NULL,

    CONSTRAINT PK_SystemConfigs PRIMARY KEY (SystemConfigId),
    CONSTRAINT UQ_SystemConfigs_Key UNIQUE (ConfigKey),
    CONSTRAINT FK_SystemConfigs_Employees FOREIGN KEY (UpdatedByEmployeeId) REFERENCES Employees(EmployeeId)
);
GO

-- OperatingHourExceptions: ngày có giờ mở cửa khác mặc định, hoặc nghỉ cả ngày.
-- Ngày nghỉ ở đây khiến hệ thống KHÔNG sinh buổi PT vào ngày đó.
CREATE TABLE OperatingHourExceptions (
    OperatingHourExceptionId INT IDENTITY(1,1) NOT NULL,
    ApplyDate                DATE          NOT NULL,
    OpenTime                 TIME(0)       NULL,
    CloseTime                TIME(0)       NULL,
    IsClosedAllDay           BIT           NOT NULL CONSTRAINT DF_OperatingHourExceptions_Closed DEFAULT (0),
    Note                     NVARCHAR(200) NULL,
    SetByEmployeeId          INT           NOT NULL,

    CONSTRAINT PK_OperatingHourExceptions PRIMARY KEY (OperatingHourExceptionId),
    CONSTRAINT UQ_OperatingHourExceptions_Date UNIQUE (ApplyDate), -- mỗi ngày tối đa 1 ngoại lệ
    CONSTRAINT FK_OperatingHourExceptions_Employees FOREIGN KEY (SetByEmployeeId) REFERENCES Employees(EmployeeId),
    -- Nghỉ cả ngày thì bỏ trống giờ; mở cửa thì phải có đủ giờ và giờ đóng sau giờ mở
    CONSTRAINT CK_OperatingHourExceptions_Time CHECK (
        (IsClosedAllDay = 1 AND OpenTime IS NULL AND CloseTime IS NULL)
        OR (IsClosedAllDay = 0 AND OpenTime IS NOT NULL AND CloseTime IS NOT NULL AND CloseTime > OpenTime)
    )
);
GO

-- AuditLogs: chỉ ghi thêm, không bao giờ sửa hay xóa. Chỉ Admin xem được.
CREATE TABLE AuditLogs (
    AuditLogId   INT IDENTITY(1,1) NOT NULL,
    AccountId    INT           NULL,      -- NULL = hành động do hệ thống (job) thực hiện
    Action       NVARCHAR(100) NOT NULL,
    EntityName   NVARCHAR(50)  NOT NULL,
    EntityId     INT           NULL,
    FieldChanged NVARCHAR(50)  NULL,
    OldValue     NVARCHAR(255) NULL,
    NewValue     NVARCHAR(255) NULL,
    Reason       NVARCHAR(500) NULL,      -- bắt buộc với hành động từ chối / hủy / xóa
    CreatedAt    DATETIME2(0)  NOT NULL CONSTRAINT DF_AuditLogs_CreatedAt DEFAULT (SYSDATETIME()),

    CONSTRAINT PK_AuditLogs PRIMARY KEY (AuditLogId),
    CONSTRAINT FK_AuditLogs_Accounts FOREIGN KEY (AccountId) REFERENCES Accounts(AccountId)
);
GO

-- Index cluster G
-- Màn Audit Log lọc theo khoảng ngày (mặc định tháng hiện tại), mới nhất trước
CREATE INDEX IX_AuditLogs_CreatedAt ON AuditLogs(CreatedAt DESC);
-- Lọc theo loại đối tượng bị tác động (Account, Customer, Package...)
CREATE INDEX IX_AuditLogs_Entity ON AuditLogs(EntityName, EntityId);
GO

PRINT N'>>> Tạo schema GMS thành công: 30 bảng nghiệp vụ.';
GO
