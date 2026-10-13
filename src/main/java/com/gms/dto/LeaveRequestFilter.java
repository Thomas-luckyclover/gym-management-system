package com.gms.dto;

import com.gms.entity.LeaveRequest;

/**
 * Điều kiện lọc ĐÃ HỢP LỆ cho danh sách đơn nghỉ phép của Manager (màn #26, UC-15).
 * Chỉ Service tạo sau khi kiểm tra tham số URL; dữ liệu thô người dùng nhập giữ ở ${param.*}.
 * Field null nghĩa là không lọc theo điều kiện đó.
 */
public class LeaveRequestFilter {

    // Tab trạng thái trên màn: Service đổi từ tham số URL bằng LeaveRequest.Status.fromDbValue.
    // null = tab "Tất cả"; mặc định của màn là PENDING nên Service truyền PENDING khi URL trống.
    private final LeaveRequest.Status status;

    // Từ khóa tên nhân viên đã trim; DAO ghép thành mẫu LIKE không phân biệt hoa thường và dấu.
    // null hoặc rỗng = không tìm theo tên.
    private final String keyword;

    /** Service gọi sau khi đã kiểm tra hợp lệ; không tự validate trong constructor. */
    public LeaveRequestFilter(LeaveRequest.Status status, String keyword) {
        this.status = status;
        this.keyword = keyword;
    }

    public LeaveRequest.Status getStatus() {
        return status;
    }

    public String getKeyword() {
        return keyword;
    }
}
