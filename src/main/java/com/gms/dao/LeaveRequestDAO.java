package com.gms.dao;

import com.gms.dto.LeaveRequestFilter;
import com.gms.entity.LeaveRequest;
import com.gms.util.DBContext;
import com.gms.util.SqlUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** Truy vấn bảng LeaveRequests (BF-05, UC-14, UC-15, JOB-07). */
public class LeaveRequestDAO {

    /** Danh sách cột LeaveRequests dùng chung cho mọi câu SELECT có map sang entity. */
    private static final String SELECT_COLUMNS
            = "SELECT LeaveRequestId, EmployeeId, ReviewedByEmployeeId, LeaveType, "
            + "LeaveDate, ShiftType, Reason, Status, RejectReason, "
            + "SubmittedAt, ReviewedAt, ReminderSentAt "
            + "FROM LeaveRequests ";

    /**
     * Tìm một đơn nghỉ phép theo mã đơn.
     *
     * @param leaveRequestId mã đơn LeaveRequests.LeaveRequestId
     * @return entity LeaveRequest, hoặc null nếu không tìm thấy
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public LeaveRequest findById(int leaveRequestId) throws SQLException {
        // B1: câu SQL chỉ lấy đúng 1 đơn theo khóa chính
        String sql = SELECT_COLUMNS
                + "WHERE LeaveRequestId = ?";

        // B2: mở kết nối, truyền tham số và đọc kết quả
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, leaveRequestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToLeaveRequest(resultSet);
                }
            }
        }

        // B3: không có dòng nào khớp
        return null;
    }

    /**
     * Đếm số đơn khớp bộ lọc của Manager (màn #26) để tính tổng số trang.
     *
     * @param filter bộ lọc đã hợp lệ; status null = mọi trạng thái, keyword rỗng = không tìm tên
     * @return tổng số đơn khớp, 0 nếu không có
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public int countByFilter(LeaveRequestFilter filter) throws SQLException {
        // B1: nối Employees và Persons vì tên nhân viên nằm ở Persons.FullName
        String sql = "SELECT COUNT(*) "
                + "FROM LeaveRequests lr "
                + "JOIN Employees e ON e.EmployeeId = lr.EmployeeId "
                + "JOIN Persons p ON p.PersonId = e.PersonId "
                // status null thì điều kiện đầu luôn đúng (không lọc theo tab)
                + "WHERE (? IS NULL OR lr.Status = ?) "
                + "AND p.FullName COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\\'";

        // B2: đổi status sang giá trị DB; null giữ nguyên để bỏ lọc
        String statusValue = filter.getStatus() == null
                ? null : filter.getStatus().getDbValue();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setString(1, statusValue);
            statement.setString(2, statusValue);
            statement.setString(3,
                    SqlUtil.toContainsPattern(filter.getKeyword()));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }

        return 0;
    }

    // ===== Map ResultSet → Entity =====

    /** Đổi 1 dòng ResultSet thành LeaveRequest; cột nullable được đọc riêng để không mất NULL. */
    private LeaveRequest mapResultSetToLeaveRequest(ResultSet resultSet)
            throws SQLException {

        LeaveRequest leaveRequest = new LeaveRequest();

        leaveRequest.setLeaveRequestId(resultSet.getInt("LeaveRequestId"));
        leaveRequest.setEmployeeId(resultSet.getInt("EmployeeId"));

        // ReviewedByEmployeeId NULL khi đơn Pending hoặc hệ thống tự từ chối (CBR-85)
        int reviewerId = resultSet.getInt("ReviewedByEmployeeId");
        leaveRequest.setReviewedByEmployeeId(
                resultSet.wasNull() ? null : reviewerId);

        leaveRequest.setLeaveType(LeaveRequest.LeaveType
                .fromDbValue(resultSet.getString("LeaveType")));
        leaveRequest.setLeaveDate(
                resultSet.getDate("LeaveDate").toLocalDate());
        leaveRequest.setShiftType(LeaveRequest.ShiftType
                .fromDbValue(resultSet.getString("ShiftType")));
        leaveRequest.setReason(resultSet.getString("Reason"));
        leaveRequest.setStatus(LeaveRequest.Status
                .fromDbValue(resultSet.getString("Status")));
        leaveRequest.setRejectReason(resultSet.getString("RejectReason"));

        leaveRequest.setSubmittedAt(
                toLocalDateTime(resultSet.getTimestamp("SubmittedAt")));
        leaveRequest.setReviewedAt(
                toLocalDateTime(resultSet.getTimestamp("ReviewedAt")));
        leaveRequest.setReminderSentAt(
                toLocalDateTime(resultSet.getTimestamp("ReminderSentAt")));

        return leaveRequest;
    }

    /** Timestamp có thể NULL (ReviewedAt, ReminderSentAt) nên đổi có kiểm tra null. */
    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
