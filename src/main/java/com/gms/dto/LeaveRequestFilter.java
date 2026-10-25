package com.gms.dto;

import com.gms.entity.LeaveRequest;
import java.time.LocalDate;

/**
 * Bộ lọc danh sách nghỉ phép: Service chuẩn hóa tham số URL, DAO áp dụng vào SQL.
 * Khoảng ngày bị đảo được Service báo lỗi trước khi truy vấn.
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

    /** Service gọi sau khi chuẩn hóa tham số URL; constructor chỉ nhận dữ liệu. */
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

