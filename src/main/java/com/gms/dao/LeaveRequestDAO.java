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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Lấy một trang đơn nghỉ phép cho màn danh sách của Manager (màn #26).
     * Đơn Pending xếp trước, trong cùng nhóm xếp theo ngày nghỉ gần nhất (hạn duyệt
     * gần nhất) rồi đến thời điểm nộp.
     *
     * @param filter   bộ lọc đã hợp lệ (trạng thái, từ khóa tên)
     * @param offset   số dòng bỏ qua, tính từ (page - 1) * pageSize
     * @param pageSize số dòng tối đa mỗi trang
     * @return danh sách dòng của trang; rỗng (không phải null) nếu không có
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public List<LeaveRequestRow> findManagerPage(
            LeaveRequestFilter filter, int offset, int pageSize)
            throws SQLException {

        // B1: nối thêm Employees/Persons của người duyệt bằng LEFT JOIN
        // vì đơn Pending hoặc tự từ chối không có người duyệt
        String sql = "SELECT lr.LeaveRequestId, lr.EmployeeId, "
                + "lr.ReviewedByEmployeeId, lr.LeaveType, lr.LeaveDate, "
                + "lr.ShiftType, lr.Reason, lr.Status, lr.RejectReason, "
                + "lr.SubmittedAt, lr.ReviewedAt, lr.ReminderSentAt, "
                + "e.EmployeeCode, e.Position, "
                + "p.FullName AS EmployeeName, p.Avatar, "
                + "rp.FullName AS ReviewerName "
                + "FROM LeaveRequests lr "
                + "JOIN Employees e ON e.EmployeeId = lr.EmployeeId "
                + "JOIN Persons p ON p.PersonId = e.PersonId "
                + "LEFT JOIN Employees re ON re.EmployeeId = lr.ReviewedByEmployeeId "
                + "LEFT JOIN Persons rp ON rp.PersonId = re.PersonId "
                + "WHERE (? IS NULL OR lr.Status = ?) "
                + "AND p.FullName COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\\' "
                // Pending lên đầu, sau đó ngày nghỉ gần nhất, rồi đến giờ nộp
                + "ORDER BY CASE WHEN lr.Status = 'Pending' THEN 0 ELSE 1 END, "
                + "lr.LeaveDate, lr.SubmittedAt "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        String statusValue = filter.getStatus() == null
                ? null : filter.getStatus().getDbValue();

        List<LeaveRequestRow> rows = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql)) {

            // B2: truyền tham số theo đúng thứ tự dấu ?
            statement.setString(1, statusValue);
            statement.setString(2, statusValue);
            statement.setString(3,
                    SqlUtil.toContainsPattern(filter.getKeyword()));
            statement.setInt(4, offset);
            statement.setInt(5, pageSize);

            // B3: mỗi dòng đổi thành entity đơn + thông tin nhân viên
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new LeaveRequestRow(
                            mapResultSetToLeaveRequest(resultSet),
                            resultSet.getString("EmployeeCode"),
                            resultSet.getString("EmployeeName"),
                            resultSet.getString("Position"),
                            resultSet.getString("Avatar"),
                            resultSet.getString("ReviewerName")));
                }
            }
        }

        return rows;
    }

    /**
     * Đếm số đơn theo từng trạng thái để hiện số trên các tab của màn #26.
     *
     * @return map có đủ 3 khóa Pending, Approved, Rejected (trạng thái không có đơn thì giá trị 0);
     *         số tab "Tất cả" do Service cộng từ 3 giá trị này
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public Map<String, Integer> getStatusCounts() throws SQLException {
        // B1: khởi tạo sẵn 3 trạng thái = 0, vì GROUP BY không trả dòng cho trạng thái rỗng
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(LeaveRequest.Status.PENDING.getDbValue(), 0);
        counts.put(LeaveRequest.Status.APPROVED.getDbValue(), 0);
        counts.put(LeaveRequest.Status.REJECTED.getDbValue(), 0);

        String sql = "SELECT Status, COUNT(*) AS Total "
                + "FROM LeaveRequests "
                + "GROUP BY Status";

        // B2: ghi đè số thật lên các khóa đã khởi tạo
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                counts.put(resultSet.getString("Status"),
                        resultSet.getInt("Total"));
            }
        }

        return counts;
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

    /**
     * Một dòng danh sách đơn nghỉ phép của Manager do DAO tạo: gồm entity đơn
     * và thông tin nhân viên lấy từ Employees/Persons. Service đổi sang
     * LeaveRequestItemView (thêm hạn duyệt, đếm ngược) trước khi đưa ra JSP.
     */
    public static class LeaveRequestRow {

        // DAO: toàn bộ cột LeaveRequests của đơn này.
        private final LeaveRequest leaveRequest;

        // DAO: Employees.EmployeeCode, ví dụ NV-0005.
        private final String employeeCode;

        // DAO: Persons.FullName của nhân viên nộp đơn.
        private final String fullName;

        // DAO: Employees.Position (PT hoặc Receptionist).
        private final String position;

        // DAO: Persons.Avatar; NULL thì JSP hiện chữ viết tắt.
        private final String avatarUrl;

        // DAO: Persons.FullName của người duyệt; NULL khi Pending hoặc tự từ chối.
        private final String reviewerName;

        public LeaveRequestRow(
                LeaveRequest leaveRequest,
                String employeeCode,
                String fullName,
                String position,
                String avatarUrl,
                String reviewerName
        ) {
            this.leaveRequest = leaveRequest;
            this.employeeCode = employeeCode;
            this.fullName = fullName;
            this.position = position;
            this.avatarUrl = avatarUrl;
            this.reviewerName = reviewerName;
        }

        public LeaveRequest getLeaveRequest() {
            return leaveRequest;
        }

        public String getEmployeeCode() {
            return employeeCode;
        }

        public String getFullName() {
            return fullName;
        }

        public String getPosition() {
            return position;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public String getReviewerName() {
            return reviewerName;
        }
    }
}
