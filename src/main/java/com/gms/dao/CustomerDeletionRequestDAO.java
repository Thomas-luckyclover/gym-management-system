package com.gms.dao;

import com.gms.dto.CustomerDeletionReviewView;
import com.gms.entity.CustomerDeletionRequest;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.util.Collections;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Xử lý truy vấn dữ liệu bảng CustomerDeletionRequests (BF-07, UC-16, UC-17).
 * Cung cấp các thao tác CRUD, khóa dòng chống tranh chấp (concurrency safe)
 * và phân trang danh sách yêu cầu xóa hồ sơ cho Quản lý & Lễ tân.
 */
public class CustomerDeletionRequestDAO {

    // Cùng một bộ cột cho chi tiết, danh sách chờ duyệt và lịch sử của lễ tân.
    private static final String REVIEW_SELECT = """
            SELECT cdr.CustomerDeletionRequestId, cdr.CustomerId, cdr.RequestedByEmployeeId,
                   cdr.ReviewedByEmployeeId, cdr.Reason, cdr.Status, cdr.RejectReason,
                   cdr.RequestedAt, cdr.ReviewedAt, c.CustomerCode, c.CustomerType, c.JoinedAt,
                   cp.FullName AS CustomerFullName, cp.Phone AS CustomerPhone, cp.Email AS CustomerEmail,
                   reqP.FullName AS RequesterName,
                   revP.FullName AS ReviewerName,
                   CASE WHEN cp.PersonId = (SELECT PersonId FROM Accounts WHERE AccountId = ?)
                        THEN 1 ELSE 0 END AS IsOwnProfile
            FROM CustomerDeletionRequests cdr
            JOIN Customers c ON c.CustomerId = cdr.CustomerId
            JOIN Persons cp ON cp.PersonId = c.PersonId
            JOIN Employees reqE ON reqE.EmployeeId = cdr.RequestedByEmployeeId
            JOIN Persons reqP ON reqP.PersonId = reqE.PersonId
            LEFT JOIN Employees revE ON revE.EmployeeId = cdr.ReviewedByEmployeeId
            LEFT JOIN Persons revP ON revP.PersonId = revE.PersonId
            """;

    // ===== Ghi dữ liệu & Cập nhật trạng thái =====

    /**
     * Thêm mới một yêu cầu xóa hồ sơ trong transaction dùng chung Connection.
     *
     * @param connection Connection tham gia transaction
     * @param deletionRequest  đối tượng CustomerDeletionRequest cần chèn
     * @return mã CustomerDeletionRequestId tự sinh trong CSDL
     * @throws SQLException khi vi phạm ràng buộc hoặc lỗi kết nối CSDL
     */
    public int insert(Connection connection, CustomerDeletionRequest deletionRequest) throws SQLException {
        // Chỉ tạo yêu cầu mới Pending; reviewer và reviewedAt để database lưu NULL.
        String sql = """
                INSERT INTO CustomerDeletionRequests (CustomerId, RequestedByEmployeeId, Reason, Status, RequestedAt)
                VALUES (?, ?, ?, 'Pending', ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, deletionRequest.getCustomerId());
            statement.setInt(2, deletionRequest.getRequestedByEmployeeId());
            statement.setString(3, deletionRequest.getReason());
            statement.setTimestamp(4, Timestamp.valueOf(DateTimeUtil.now()));
            statement.executeUpdate();
            try (ResultSet resultSet = statement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    int requestId = resultSet.getInt(1);
                    deletionRequest.setCustomerDeletionRequestId(requestId);
                    return requestId;
                }
            }
        }
        throw new SQLException("Không nhận được mã yêu cầu xóa vừa tạo.");
    }

    /**
     * Phê duyệt yêu cầu xóa hồ sơ (chỉ cập nhật khi Status còn là Pending).
     *
     * @param connection      Connection tham gia transaction
     * @param requestId mã yêu cầu xóa
     * @param managerEmployeeId mã nhân viên Quản lý thực hiện duyệt
     * @return true nếu cập nhật thành công 1 dòng, false nếu đã bị người khác xử lý
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public boolean approve(Connection connection, int requestId, int managerEmployeeId) throws SQLException {
        String sql = "UPDATE CustomerDeletionRequests "
                + "SET Status = 'Approved', ReviewedByEmployeeId = ?, ReviewedAt = ? "
                + "WHERE CustomerDeletionRequestId = ? AND Status = 'Pending'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, managerEmployeeId);
            statement.setTimestamp(2, Timestamp.valueOf(DateTimeUtil.now()));
            statement.setInt(3, requestId);
            int rows = statement.executeUpdate();
            return rows > 0;
        }
    }

    /**
     * Từ chối yêu cầu xóa hồ sơ (bắt buộc kèm lý do từ chối 5-500 ký tự).
     *
     * @param connection         Connection tham gia transaction
     * @param requestId    mã yêu cầu xóa
     * @param managerEmployeeId mã nhân viên Quản lý thực hiện từ chối
     * @param rejectReason lý do từ chối
     * @return true nếu cập nhật thành công 1 dòng, false nếu đã bị người khác xử lý
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public boolean reject(Connection connection, int requestId, int managerEmployeeId, String rejectReason) throws SQLException {
        String sql = "UPDATE CustomerDeletionRequests "
                + "SET Status = 'Rejected', ReviewedByEmployeeId = ?, RejectReason = ?, ReviewedAt = ? "
                + "WHERE CustomerDeletionRequestId = ? AND Status = 'Pending'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, managerEmployeeId);
            statement.setString(2, rejectReason);
            statement.setTimestamp(3, Timestamp.valueOf(DateTimeUtil.now()));
            statement.setInt(4, requestId);
            int rows = statement.executeUpdate();
            return rows > 0;
        }
    }

    // ===== Truy vấn đơn lẻ & Kiểm tra trạng thái =====

    /**
     * Tìm yêu cầu xóa theo ID, hỗ trợ khóa dòng (UPDLOCK, ROWLOCK) chống tranh chấp đồng thời.
     *
     * @param connection   Connection tham gia transaction
     * @param id     mã yêu cầu xóa
     * @param lockRow true nếu cần khóa dòng chống người khác duyệt cùng lúc
     * @return CustomerDeletionRequest hoặc null nếu không tìm thấy
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public CustomerDeletionRequest findById(Connection connection, int id, boolean lockRow) throws SQLException {
        String lockHint = lockRow ? " WITH (UPDLOCK, ROWLOCK) " : " ";
        String sql = "SELECT CustomerDeletionRequestId, CustomerId, RequestedByEmployeeId, ReviewedByEmployeeId, "
                + "Reason, Status, RejectReason, RequestedAt, ReviewedAt "
                + "FROM CustomerDeletionRequests" + lockHint
                + "WHERE CustomerDeletionRequestId = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToEntity(resultSet);
                }
            }
        }
        return null;
    }

    /**
     * Kiểm tra khách hàng có yêu cầu xóa đang Pending không.
     *
     * @param connection       Connection tham gia transaction
     * @param customerId mã khách hàng
     * @return true nếu đang có yêu cầu Pending
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public boolean hasPendingRequest(Connection connection, int customerId) throws SQLException {
        String sql = "SELECT 1 FROM CustomerDeletionRequests WHERE CustomerId = ? AND Status = 'Pending'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * Tìm yêu cầu xóa đang Pending của một khách hàng (nếu có).
     */
    public CustomerDeletionRequest findPendingByCustomerId(Connection connection, int customerId) throws SQLException {
        String sql = "SELECT CustomerDeletionRequestId, CustomerId, RequestedByEmployeeId, ReviewedByEmployeeId, "
                + "Reason, Status, RejectReason, RequestedAt, ReviewedAt "
                + "FROM CustomerDeletionRequests "
                + "WHERE CustomerId = ? AND Status = 'Pending'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToEntity(resultSet);
                }
            }
        }
        return null;
    }

    /**
     * Đếm tổng số yêu cầu xóa đang ở trạng thái Pending (phục vụ badge hiển thị trên menu Quản lý).
     */
    public int countPending() throws SQLException {
        String sql = "SELECT COUNT(*) FROM CustomerDeletionRequests WHERE Status = 'Pending'";
        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        }
        return 0;
    }

    // ===== Ánh xạ ResultSet =====

    private CustomerDeletionRequest mapResultSetToEntity(ResultSet resultSet) throws SQLException {
        int id = resultSet.getInt("CustomerDeletionRequestId");
        int customerId = resultSet.getInt("CustomerId");
        int requesterId = resultSet.getInt("RequestedByEmployeeId");

        int reviewerIdValue = resultSet.getInt("ReviewedByEmployeeId");
        Integer reviewerId = resultSet.wasNull() ? null : reviewerIdValue;

        String reason = resultSet.getString("Reason");
        String statusValue = resultSet.getString("Status");
        CustomerDeletionRequest.Status status = CustomerDeletionRequest.Status.fromDbValue(statusValue);
        String rejectReason = resultSet.getString("RejectReason");

        Timestamp requestedTimestamp = resultSet.getTimestamp("RequestedAt");
        LocalDateTime requestedAt = requestedTimestamp != null ? requestedTimestamp.toLocalDateTime() : null;

        Timestamp reviewedTimestamp = resultSet.getTimestamp("ReviewedAt");
        LocalDateTime reviewedAt = reviewedTimestamp != null ? reviewedTimestamp.toLocalDateTime() : null;

        return new CustomerDeletionRequest(id, customerId, requesterId, reviewerId, reason, status, rejectReason, requestedAt, reviewedAt);
    }

    /** Đọc thông tin một yêu cầu; Service tải riêng gói và buổi PT khi mở chi tiết. */
    public CustomerDeletionReviewView findReviewDetail(int requestId, int accountId) throws SQLException {
        String sql = REVIEW_SELECT + " WHERE cdr.CustomerDeletionRequestId = ?";
        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            statement.setInt(2, requestId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return readReviewView(resultSet);
                }
            }
        }
        return null;
    }

    /** Một truy vấn cho cả trang chờ duyệt; không đọc danh sách gói/buổi theo từng dòng. */
    public List<CustomerDeletionReviewView> findPendingViews(int accountId, int offset, int limit) throws SQLException {
        String sql = REVIEW_SELECT + """
                WHERE cdr.Status = 'Pending'
                ORDER BY cdr.RequestedAt ASC, cdr.CustomerDeletionRequestId ASC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
        List<CustomerDeletionReviewView> requests = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            statement.setInt(2, offset);
            statement.setInt(3, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(readReviewView(resultSet));
                }
            }
        }
        return requests;
    }

    /** Lịch sử chỉ lấy yêu cầu do tài khoản lễ tân hiện tại gửi. */
    public List<CustomerDeletionReviewView> findByRequesterAccountViews(int accountId, int offset, int limit) throws SQLException {
        String sql = REVIEW_SELECT + """
                WHERE reqE.PersonId = (SELECT PersonId FROM Accounts WHERE AccountId = ?)
                ORDER BY cdr.RequestedAt DESC, cdr.CustomerDeletionRequestId DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """;
        List<CustomerDeletionReviewView> requests = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            statement.setInt(2, accountId);
            statement.setInt(3, offset);
            statement.setInt(4, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(readReviewView(resultSet));
                }
            }
        }
        return requests;
    }

    /** Đếm cùng điều kiện với lịch sử để phân trang không lẫn yêu cầu của lễ tân khác. */
    public int countByRequesterAccountId(int accountId) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM CustomerDeletionRequests request
                JOIN Employees employee ON employee.EmployeeId = request.RequestedByEmployeeId
                JOIN Accounts account ON account.PersonId = employee.PersonId
                WHERE account.AccountId = ?
                """;
        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    /** Ba truy vấn trên dùng chung cách đọc một dòng, tránh lặp constructor dài. */
    private CustomerDeletionReviewView readReviewView(ResultSet resultSet) throws SQLException {
        Timestamp joinedAt = resultSet.getTimestamp("JoinedAt");
        LocalDateTime customerJoinedAt = null;
        if (joinedAt != null) {
            customerJoinedAt = joinedAt.toLocalDateTime();
        }
        return new CustomerDeletionReviewView(
                mapResultSetToEntity(resultSet),
                resultSet.getInt("CustomerId"), resultSet.getString("CustomerCode"),
                resultSet.getString("CustomerFullName"), resultSet.getString("CustomerPhone"),
                resultSet.getString("CustomerEmail"), resultSet.getString("CustomerType"), customerJoinedAt,
                resultSet.getString("RequesterName"),
                resultSet.getString("ReviewerName"), Collections.emptyList(), Collections.emptyList(),
                resultSet.getBoolean("IsOwnProfile"));
    }
}
