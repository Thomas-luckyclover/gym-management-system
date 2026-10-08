/* ============================================================================
   GMS — FILE: 2_Seed_Data.sql  ·  v26
   SINH TỰ ĐỘNG bởi database/tools/seeddata.py — muốn sửa dữ liệu thì sửa file .py rồi sinh lại.
   v26    : CheckedIn/Used, tự chốt JOB-04, đánh giá PT theo tháng, P-22 thay P-07.
   CHẠY   : 0_Reset_Database.sql → 1_Init_Schema.sql → 2_Seed_Data.sql (database rỗng).
   MỐC    : lịch sử cố định quanh Thứ Bảy 03/10/2026 (tháng 6 → tháng 11/2026).
            Dữ liệu "sống" (mã xác thực, khóa tạm 5 phút, đơn online còn hạn, slot đang giữ,
            ca sáng đang mở, gói ngày + buổi lẻ hôm nay) tính theo GIỜ CHẠY SCRIPT (@Now, @Today)
            → chạy lại file lúc nào cũng test được các luồng đó.
   MẬT KHẨU: mọi tài khoản = Gms@2026 (trừ tài khoản chưa đặt mật khẩu).
   DANH MỤC CASE + TÀI KHOẢN TEST: docs/database/SEED_CASES.md
   ============================================================================ */
