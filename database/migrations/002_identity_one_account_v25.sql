/* ============================================================================
   GMS — Migration 002 (03/10/2026) — chốt mô hình danh tính v25
   Rule: DBR-65 (1 Person: tối đa 1 hồ sơ khách chưa Inactive + 1 hồ sơ nhân viên Working,
         1 tài khoản phục vụ mọi vai trò), CBR-113 (chọn vai trò khi đăng nhập), CBR-114.
   Thay đổi: UNIQUE(PersonId) trên Customers / Employees → filtered unique index.
   Không đổi Persons / Accounts: email, SĐT, avatar vẫn nằm trên Persons; UQ_Accounts_PersonId giữ nguyên.
   Chạy được nhiều lần (có kiểm tra tồn tại).
   ============================================================================ */
USE GMS;
GO

IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'UQ_Customers_PersonId')
    ALTER TABLE Customers DROP CONSTRAINT UQ_Customers_PersonId;
IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'UQ_Employees_PersonId')
    ALTER TABLE Employees DROP CONSTRAINT UQ_Employees_PersonId;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_Customers_PersonId_Active')
    CREATE UNIQUE INDEX UX_Customers_PersonId_Active ON Customers(PersonId) WHERE Status <> 'Inactive';
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_Employees_PersonId_Working')
    CREATE UNIQUE INDEX UX_Employees_PersonId_Working ON Employees(PersonId) WHERE Status = 'Working';
GO
