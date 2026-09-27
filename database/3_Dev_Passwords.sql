/* ============================================================================
   GMS — FILE: 3_Dev_Passwords.sql
   MỤC ĐÍCH: Đặt MỘT mật khẩu chung cho mọi tài khoản mẫu để cả nhóm đăng nhập test được.
   LÝ DO   : PasswordHash trong 2_Seed_Data.sql là chuỗi giả ('$2a$10$hashAn000...'), không phải
             hash BCrypt thật → PasswordUtil.checkPassword() luôn trả false → không ai đăng nhập được.
   CHỈ ĐỔI DỮ LIỆU MẪU, KHÔNG ĐỔI SCHEMA. Chỉ chạy trên database dev/local, TUYỆT ĐỐI không chạy trên môi trường thật.

   Mật khẩu chung : Gms@2026   (8 ký tự, có chữ + số — đúng DBR-02)
   Hash dưới đây  : BCrypt tiền tố $2a$, độ khó 10 — đúng định dạng jBCrypt 0.4 đọc được
                    (bản $2b$ do một số công cụ khác sinh ra sẽ bị jBCrypt 0.4 báo lỗi).
   Tài khoản 9 giữ NULL có chủ đích: minh họa Lễ tân tạo, khách chưa đặt mật khẩu → MSG38 → dùng Quên mật khẩu.
   Chạy SAU 2_Seed_Data.sql.
   ============================================================================ */

USE GMS;
GO

UPDATE Accounts
SET PasswordHash = '$2a$10$o22fNyHpwvbuAj8q.LKCI.KMakVydfKblTjnBh7rgiUTrt55ZnXmu'
WHERE PasswordHash IS NOT NULL;
GO

PRINT N'>>> Đã đặt mật khẩu Gms@2026 cho mọi tài khoản mẫu (trừ tài khoản chưa đặt mật khẩu).';
GO
