/* ============================================================================
   GMS — FILE: 0_Reset_Database.sql  ·  v25
   XÓA SẠCH database GMS để chạy lại 1_Init_Schema.sql + 2_Seed_Data.sql.
   CHỈ dùng trên máy dev. Đóng mọi kết nối khác (Tomcat, tab SSMS khác) trước khi chạy.
   ============================================================================ */
USE master;
GO

IF DB_ID('GMS') IS NOT NULL
BEGIN
    ALTER DATABASE GMS SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE GMS;
END
GO

PRINT N'>>> Đã xóa database GMS. Chạy tiếp 1_Init_Schema.sql rồi 2_Seed_Data.sql.';
GO
