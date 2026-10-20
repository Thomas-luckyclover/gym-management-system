package com.gms.dao;

import com.gms.dto.CustomerDeletionReviewView;
import com.gms.entity.CustomerDeletionRequest;
import com.gms.util.DBContext;
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

    // ===== Ghi dữ liệu & Cập nhật trạng thái =====

    /**
     * Thêm mới một yêu cầu xóa hồ sơ trong transaction dùng chung Connection.
     *
     * @param conn Connection tham gia transaction
     * @param req  đối tượng CustomerDeletionRequest cần chèn
     * @return mã CustomerDeletionRequestId tự sinh trong CSDL
     * @throws SQLException khi vi phạm ràng buộc hoặc lỗi kết nối CSDL
     */
    public int insert(Connection conn, CustomerDeletionRequest req) throws SQLException {
        String sql = "INSERT INTO CustomerDeletionRequests "
                + "(CustomerId, RequestedByEmployeeId, ReviewedByEmployeeId, Reason, Status, RejectReason, RequestedAt, ReviewedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        // B1: chuẩn bị câu lệnh với cờ lấy generated keys
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, req.getCustomerId());
            stmt.setInt(2, req.getRequestedByEmployeeId());

            if (req.getReviewedByEmployeeId() != null) {
                stmt.setInt(3, req.getReviewedByEmployeeId());
            } else {
                stmt.setNull(3, java.sql.Types.INTEGER);
            }

            stmt.setString(4, req.getReason());
            stmt.setString(5, req.getStatus() != null ? req.getStatus().getDbValue() : CustomerDeletionRequest.Status.PENDING.getDbValue());
            stmt.setString(6, req.getRejectReason());

            Timestamp reqAt = req.getRequestedAt() != null
                    ? Timestamp.valueOf(req.getRequestedAt())
                    : new Timestamp(System.currentTimeMillis());
            stmt.setTimestamp(7, reqAt);

            if (req.getReviewedAt() != null) {
                stmt.setTimestamp(8, Timestamp.valueOf(req.getReviewedAt()));
            } else {
                stmt.setNull(8, java.sql.Types.TIMESTAMP);
            }

            // B2: thực thi và lấy khóa chính tự sinh
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int generatedId = rs.getInt(1);
                    req.setCustomerDeletionRequestId(generatedId);
                    return generatedId;
                }
            }
        }
        throw new SQLException("Thêm CustomerDeletionRequest thất bại, không nhận được CustomerDeletionRequestId");
    }

    /**
     * Phê duyệt yêu cầu xóa hồ sơ (chỉ cập nhật khi Status còn là Pending).
     *
     * @param conn      Connection tham gia transaction
     * @param requestId mã yêu cầu xóa
     * @param managerEmployeeId mã nhân viên Quản lý thực hiện duyệt
     * @return true nếu cập nhật thành công 1 dòng, false nếu đã bị người khác xử lý
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public boolean approve(Connection conn, int requestId, int managerEmployeeId) throws SQLException {
        String sql = "UPDATE CustomerDeletionRequests "
                + "SET Status = 'Approved', ReviewedByEmployeeId = ?, ReviewedAt = SYSDATETIME() "
                + "WHERE CustomerDeletionRequestId = ? AND Status = 'Pending'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, managerEmployeeId);
            stmt.setInt(2, requestId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        }
    }

    /**
     * Từ chối yêu cầu xóa hồ sơ (bắt buộc kèm lý do từ chối 5-500 ký tự).
     *
     * @param conn         Connection tham gia transaction
     * @param requestId    mã yêu cầu xóa
     * @param managerEmployeeId mã nhân viên Quản lý thực hiện từ chối
     * @param rejectReason lý do từ chối
     * @return true nếu cập nhật thành công 1 dòng, false nếu đã bị người khác xử lý
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public boolean reject(Connection conn, int requestId, int managerEmployeeId, String rejectReason) throws SQLException {
        String sql = "UPDATE CustomerDeletionRequests "
                + "SET Status = 'Rejected', ReviewedByEmployeeId = ?, RejectReason = ?, ReviewedAt = SYSDATETIME() "
                + "WHERE CustomerDeletionRequestId = ? AND Status = 'Pending'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, managerEmployeeId);
            stmt.setString(2, rejectReason);
            stmt.setInt(3, requestId);
            int rows = stmt.executeUpdate();
            return rows > 0;
        }
    }

    // ===== Truy vấn đơn lẻ & Kiểm tra trạng thái =====

    /**
     * Tìm yêu cầu xóa theo ID, hỗ trợ khóa dòng (UPDLOCK, ROWLOCK) chống tranh chấp đồng thời.
     *
     * @param conn   Connection tham gia transaction
     * @param id     mã yêu cầu xóa
     * @param lockRow true nếu cần khóa dòng chống người khác duyệt cùng lúc
     * @return CustomerDeletionRequest hoặc null nếu không tìm thấy
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public CustomerDeletionRequest findById(Connection conn, int id, boolean lockRow) throws SQLException {
        String lockHint = lockRow ? " WITH (UPDLOCK, ROWLOCK) " : " ";
        String sql = "SELECT CustomerDeletionRequestId, CustomerId, RequestedByEmployeeId, ReviewedByEmployeeId, "
                + "Reason, Status, RejectReason, RequestedAt, ReviewedAt "
                + "FROM CustomerDeletionRequests" + lockHint
                + "WHERE CustomerDeletionRequestId = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToEntity(rs);
                }
            }
        }
        return null;
    }

    /**
     * Tìm yêu cầu xóa theo ID dùng Connection riêng (chế độ chỉ đọc).
     */
    public CustomerDeletionRequest findById(int id) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findById(conn, id, false);
        }
    }

    /**
     * Kiểm tra khách hàng có yêu cầu xóa đang Pending không.
     *
     * @param conn       Connection tham gia transaction
     * @param customerId mã khách hàng
     * @return true nếu đang có yêu cầu Pending
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public boolean hasPendingRequest(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT 1 FROM CustomerDeletionRequests WHERE CustomerId = ? AND Status = 'Pending'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Tìm yêu cầu xóa đang Pending của một khách hàng (nếu có).
     */
    public CustomerDeletionRequest findPendingByCustomerId(Connection conn, int customerId) throws SQLException {
        String sql = "SELECT CustomerDeletionRequestId, CustomerId, RequestedByEmployeeId, ReviewedByEmployeeId, "
                + "Reason, Status, RejectReason, RequestedAt, ReviewedAt "
                + "FROM CustomerDeletionRequests "
                + "WHERE CustomerId = ? AND Status = 'Pending'";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToEntity(rs);
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
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Danh sách các yêu cầu xóa do một nhân viên Lễ tân cụ thể gửi (Q8 - Tab Yêu cầu của tôi).
     */
    public List<CustomerDeletionRequest> findByRequester(int employeeId) throws SQLException {
        List<CustomerDeletionRequest> list = new ArrayList<>();
        String sql = "SELECT CustomerDeletionRequestId, CustomerId, RequestedByEmployeeId, ReviewedByEmployeeId, "
                + "Reason, Status, RejectReason, RequestedAt, ReviewedAt "
                + "FROM CustomerDeletionRequests "
                + "WHERE RequestedByEmployeeId = ? "
                + "ORDER BY RequestedAt DESC";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, employeeId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToEntity(rs));
                }
            }
        }
        return list;
    }

    // ===== Ánh xạ ResultSet =====

    private CustomerDeletionRequest mapResultSetToEntity(ResultSet rs) throws SQLException {
        int id = rs.getInt("CustomerDeletionRequestId");
        int custId = rs.getInt("CustomerId");
        int reqEmpId = rs.getInt("RequestedByEmployeeId");

        int revEmpIdVal = rs.getInt("ReviewedByEmployeeId");
        Integer revEmpId = rs.wasNull() ? null : revEmpIdVal;

        String reason = rs.getString("Reason");
        String statusStr = rs.getString("Status");
        CustomerDeletionRequest.Status status = CustomerDeletionRequest.Status.fromDbValue(statusStr);
        String rejectReason = rs.getString("RejectReason");

        Timestamp reqTs = rs.getTimestamp("RequestedAt");
        LocalDateTime reqAt = reqTs != null ? reqTs.toLocalDateTime() : null;

        Timestamp revTs = rs.getTimestamp("ReviewedAt");
        LocalDateTime revAt = revTs != null ? revTs.toLocalDateTime() : null;

        return new CustomerDeletionRequest(id, custId, reqEmpId, revEmpId, reason, status, rejectReason, reqAt, revAt);
    }

    /**
     * Lấy chi tiết một yêu cầu xóa để Quản lý duyệt (#32).
     *
     * @param requestId mã yêu cầu xóa
     * @param currentManagerAccountId mã tài khoản Quản lý đang duyệt (để kiểm tra CBR-115)
     * @return CustomerDeletionReviewView hoặc null nếu không tồn tại
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public CustomerDeletionReviewView findReviewDetail(int requestId, int currentManagerAccountId) throws SQLException {
        String sql = """
            SELECT cdr.CustomerDeletionRequestId, cdr.CustomerId, cdr.RequestedByEmployeeId, cdr.ReviewedByEmployeeId,
                   cdr.Reason, cdr.Status, cdr.RejectReason, cdr.RequestedAt, cdr.ReviewedAt,
                   c.CustomerCode, c.CustomerType, c.JoinedAt,
                   cp.FullName AS CustomerFullName, cp.Phone AS CustomerPhone, cp.Email AS CustomerEmail, cp.Avatar AS CustomerAvatar,
                   reqP.FullName AS RequesterName, reqE.EmployeeCode AS RequesterCode,
                   revP.FullName AS ReviewerName,
                   CASE WHEN EXISTS (
                       SELECT 1 FROM Accounts a
                       JOIN Customers c2 ON c2.PersonId = a.PersonId
                       WHERE a.AccountId = ? AND c2.CustomerId = cdr.CustomerId
                   ) THEN 1 ELSE 0 END AS IsOwnProfile
            FROM CustomerDeletionRequests cdr
            JOIN Customers c ON c.CustomerId = cdr.CustomerId
            JOIN Persons cp ON cp.PersonId = c.PersonId
            JOIN Employees reqE ON reqE.EmployeeId = cdr.RequestedByEmployeeId
            JOIN Persons reqP ON reqP.PersonId = reqE.PersonId
            LEFT JOIN Employees revE ON revE.EmployeeId = cdr.ReviewedByEmployeeId
            LEFT JOIN Persons revP ON revP.PersonId = revE.PersonId
            WHERE cdr.CustomerDeletionRequestId = ?
            """;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, currentManagerAccountId);
            stmt.setInt(2, requestId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    CustomerDeletionRequest req = mapResultSetToEntity(rs);
                    int customerId = rs.getInt("CustomerId");
                    String customerCode = rs.getString("CustomerCode");
                    String customerFullName = rs.getString("CustomerFullName");
                    String customerPhone = rs.getString("CustomerPhone");
                    String customerEmail = rs.getString("CustomerEmail");
                    String customerAvatar = rs.getString("CustomerAvatar");
                    String customerType = rs.getString("CustomerType");
                    Timestamp joinedAtTs = rs.getTimestamp("JoinedAt");
                    LocalDateTime joinedAt = joinedAtTs != null ? joinedAtTs.toLocalDateTime() : null;
                    String requesterName = rs.getString("RequesterName");
                    String requesterCode = rs.getString("RequesterCode");
                    String reviewerName = rs.getString("ReviewerName");
                    boolean ownProfile = rs.getInt("IsOwnProfile") == 1;

                    CustomerDAO customerDAO = new CustomerDAO();
                    List<CustomerDAO.ActiveSubscriptionSummary> activePackages =
                            customerDAO.findActiveSubscriptionsByCustomer(conn, customerId);
                    List<CustomerDAO.ScheduledSessionSummary> scheduledSessions =
                            customerDAO.findScheduledSessionsByCustomer(conn, customerId);

                    return new CustomerDeletionReviewView(
                            req,
                            customerId,
                            customerCode,
                            customerFullName,
                            customerPhone,
                            customerEmail,
                            customerAvatar,
                            customerType,
                            joinedAt,
                            requesterName,
                            requesterCode,
                            reviewerName,
                            activePackages,
                            scheduledSessions,
                            ownProfile
                    );
                }
            }
        }
        return null;
    }

    /**
     * Lấy danh sách các yêu cầu xóa Pending để hiển thị trên màn hình Quản lý duyệt (#32).
     */
    public List<CustomerDeletionReviewView> findPendingViews(int currentManagerAccountId, int offset, int limit) throws SQLException {
        List<CustomerDeletionReviewView> list = new ArrayList<>();
        String sql = """
            SELECT cdr.CustomerDeletionRequestId, cdr.CustomerId, cdr.RequestedByEmployeeId, cdr.ReviewedByEmployeeId,
                   cdr.Reason, cdr.Status, cdr.RejectReason, cdr.RequestedAt, cdr.ReviewedAt,
                   c.CustomerCode, c.CustomerType, c.JoinedAt,
                   cp.FullName AS CustomerFullName, cp.Phone AS CustomerPhone, cp.Email AS CustomerEmail, cp.Avatar AS CustomerAvatar,
                   reqP.FullName AS RequesterName, reqE.EmployeeCode AS RequesterCode,
                   revP.FullName AS ReviewerName,
                   CASE WHEN EXISTS (
                       SELECT 1 FROM Accounts a
                       JOIN Customers c2 ON c2.PersonId = a.PersonId
                       WHERE a.AccountId = ? AND c2.CustomerId = cdr.CustomerId
                   ) THEN 1 ELSE 0 END AS IsOwnProfile
            FROM CustomerDeletionRequests cdr
            JOIN Customers c ON c.CustomerId = cdr.CustomerId
            JOIN Persons cp ON cp.PersonId = c.PersonId
            JOIN Employees reqE ON reqE.EmployeeId = cdr.RequestedByEmployeeId
            JOIN Persons reqP ON reqP.PersonId = reqE.PersonId
            LEFT JOIN Employees revE ON revE.EmployeeId = cdr.ReviewedByEmployeeId
            LEFT JOIN Persons revP ON revP.PersonId = revE.PersonId
            WHERE cdr.Status = 'Pending'
            ORDER BY cdr.RequestedAt ASC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, currentManagerAccountId);
            stmt.setInt(2, offset);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                CustomerDAO customerDAO = new CustomerDAO();
                while (rs.next()) {
                    CustomerDeletionRequest req = mapResultSetToEntity(rs);
                    int customerId = rs.getInt("CustomerId");
                    String customerCode = rs.getString("CustomerCode");
                    String customerFullName = rs.getString("CustomerFullName");
                    String customerPhone = rs.getString("CustomerPhone");
                    String customerEmail = rs.getString("CustomerEmail");
                    String customerAvatar = rs.getString("CustomerAvatar");
                    String customerType = rs.getString("CustomerType");
                    Timestamp joinedAtTs = rs.getTimestamp("JoinedAt");
                    LocalDateTime joinedAt = joinedAtTs != null ? joinedAtTs.toLocalDateTime() : null;
                    String requesterName = rs.getString("RequesterName");
                    String requesterCode = rs.getString("RequesterCode");
                    String reviewerName = rs.getString("ReviewerName");
                    boolean ownProfile = rs.getInt("IsOwnProfile") == 1;

                    List<CustomerDAO.ActiveSubscriptionSummary> activePackages =
                            customerDAO.findActiveSubscriptionsByCustomer(conn, customerId);
                    List<CustomerDAO.ScheduledSessionSummary> scheduledSessions =
                            customerDAO.findScheduledSessionsByCustomer(conn, customerId);

                    list.add(new CustomerDeletionReviewView(
                            req,
                            customerId,
                            customerCode,
                            customerFullName,
                            customerPhone,
                            customerEmail,
                            customerAvatar,
                            customerType,
                            joinedAt,
                            requesterName,
                            requesterCode,
                            reviewerName,
                            activePackages,
                            scheduledSessions,
                            ownProfile
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Lấy danh sách các yêu cầu xóa do Lễ tân (theo AccountId) đã gửi (Q8 - Tab Yêu cầu của tôi trên #28).
     */
    public List<CustomerDeletionReviewView> findByRequesterAccountViews(int receptionistAccountId, int offset, int limit) throws SQLException {
        List<CustomerDeletionReviewView> list = new ArrayList<>();
        String sql = """
            SELECT cdr.CustomerDeletionRequestId, cdr.CustomerId, cdr.RequestedByEmployeeId, cdr.ReviewedByEmployeeId,
                   cdr.Reason, cdr.Status, cdr.RejectReason, cdr.RequestedAt, cdr.ReviewedAt,
                   c.CustomerCode, c.CustomerType, c.JoinedAt,
                   cp.FullName AS CustomerFullName, cp.Phone AS CustomerPhone, cp.Email AS CustomerEmail, cp.Avatar AS CustomerAvatar,
                   reqP.FullName AS RequesterName, reqE.EmployeeCode AS RequesterCode,
                   revP.FullName AS ReviewerName,
                   0 AS IsOwnProfile
            FROM CustomerDeletionRequests cdr
            JOIN Customers c ON c.CustomerId = cdr.CustomerId
            JOIN Persons cp ON cp.PersonId = c.PersonId
            JOIN Employees reqE ON reqE.EmployeeId = cdr.RequestedByEmployeeId
            JOIN Persons reqP ON reqP.PersonId = reqE.PersonId
            JOIN Accounts reqA ON reqA.PersonId = reqE.PersonId
            LEFT JOIN Employees revE ON revE.EmployeeId = cdr.ReviewedByEmployeeId
            LEFT JOIN Persons revP ON revP.PersonId = revE.PersonId
            WHERE reqA.AccountId = ?
            ORDER BY cdr.RequestedAt DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, receptionistAccountId);
            stmt.setInt(2, offset);
            stmt.setInt(3, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                CustomerDAO customerDAO = new CustomerDAO();
                while (rs.next()) {
                    CustomerDeletionRequest req = mapResultSetToEntity(rs);
                    int customerId = rs.getInt("CustomerId");
                    String customerCode = rs.getString("CustomerCode");
                    String customerFullName = rs.getString("CustomerFullName");
                    String customerPhone = rs.getString("CustomerPhone");
                    String customerEmail = rs.getString("CustomerEmail");
                    String customerAvatar = rs.getString("CustomerAvatar");
                    String customerType = rs.getString("CustomerType");
                    Timestamp joinedAtTs = rs.getTimestamp("JoinedAt");
                    LocalDateTime joinedAt = joinedAtTs != null ? joinedAtTs.toLocalDateTime() : null;
                    String requesterName = rs.getString("RequesterName");
                    String requesterCode = rs.getString("RequesterCode");
                    String reviewerName = rs.getString("ReviewerName");

                    List<CustomerDAO.ActiveSubscriptionSummary> activePackages =
                            customerDAO.findActiveSubscriptionsByCustomer(conn, customerId);
                    List<CustomerDAO.ScheduledSessionSummary> scheduledSessions =
                            customerDAO.findScheduledSessionsByCustomer(conn, customerId);

                    list.add(new CustomerDeletionReviewView(
                            req,
                            customerId,
                            customerCode,
                            customerFullName,
                            customerPhone,
                            customerEmail,
                            customerAvatar,
                            customerType,
                            joinedAt,
                            requesterName,
                            requesterCode,
                            reviewerName,
                            activePackages,
                            scheduledSessions,
                            false
                    ));
                }
            }
        }
        return list;
    }

    /**
     * Đếm tổng số lượng yêu cầu xóa do tài khoản Lễ tân đã gửi.
     */
    public int countByRequesterAccountId(int receptionistAccountId) throws SQLException {
        String sql = """
            SELECT COUNT(*)
            FROM CustomerDeletionRequests cdr
            JOIN Employees reqE ON reqE.EmployeeId = cdr.RequestedByEmployeeId
            JOIN Accounts reqA ON reqA.PersonId = reqE.PersonId
            WHERE reqA.AccountId = ?
            """;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, receptionistAccountId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }
}
