package com.gms.dto;

import com.gms.entity.LeaveRequest;
import java.time.LocalDate;

/**
 * Điều kiện lọc ĐÃ HỢP LỆ cho danh sách đơn nghỉ phép của Manager (màn #26, UC-15).
 * Chỉ Service tạo sau khi kiểm tra tham số URL; dữ liệu thô người dùng nhập giữ ở ${param.*}.
 * Field null nghĩa là không lọc theo điều kiện đó.
 */
public class LeaveRequestFilter {

    // Tab trạng thái trên màn: Service đổi từ tham số URL bằng LeaveRequest.Status.fromDbValue.
    // null = tab Tất cả; không có tham số trạng thái thì Service chọn Chờ duyệt.
    private final LeaveRequest.Status status;

    // Từ khóa tên nhân viên đã trim; DAO ghép thành mẫu LIKE không phân biệt hoa thường và dấu.
    // null hoặc rỗng = không tìm theo tên.
    private final String keyword;

    // Bộ lọc mở rộng (BF-05): Chức vụ (PT/Receptionist), Loại nghỉ.
    private final String position;
    private final LeaveRequest.LeaveType leaveType;
    // Service đọc hai ô ngày gửi đơn; DAO so với SubmittedAt, tính trọn hai ngày biên.
    // JSP hiện lại lựa chọn này; null ở một đầu nghĩa là không giới hạn đầu đó.
    private final LocalDate fromDate;
    private final LocalDate toDate;

    /** Service gọi sau khi đã kiểm tra hợp lệ; không tự validate trong constructor. */
    public LeaveRequestFilter(LeaveRequest.Status status, String keyword) {
        this(status, keyword, null, null, null, null);
    }

    public LeaveRequestFilter(
            LeaveRequest.Status status,
            String keyword,
            String position,
            LeaveRequest.LeaveType leaveType,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        this.status = status;
        this.keyword = keyword;
        this.position = position;
        this.leaveType = leaveType;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public LeaveRequest.Status getStatus() {
        return status;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getPosition() {
        return position;
    }

    public LeaveRequest.LeaveType getLeaveType() {
        return leaveType;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }
}

