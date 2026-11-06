package com.gms.job;

import java.sql.SQLException;

/**
// Đây là 1 interface, tức là 1 bản hợp đồng - contract. Class nào muốn làm JOB chạy ngầm sẽ bắt buộc có 2 hàm này. File này ko có code chạy, chhir khai báo
// Interface: Abstraction: Chỉ cho biết làm gì, giấu làm như nào + Polymorphism: Cùng 1 lợi gọi, mỗi class chạy 1 kiểu
// BackgroundJob là interface thể hiện trừu tượng hóa: Scheduler chỉ biết mọi job có getName() và run(), không cần biết chi tiết từng job. Nhờ đó dùng được đa hình: một vòng lặp job.run() chạy được mọi job, thêm job mới không phải sửa Scheduler.
*/
public interface BackgroundJob {

    /**
     * @return mã + tên job để ghi log, VD "JOB-05 Cancel overdue online orders"
     */
    String getName();

    /**
     * Chạy 1 lượt job.
     * @throws SQLException khi không kết nối được DB — scheduler sẽ thử lại theo SRS
     */
    void run() throws SQLException;
}
