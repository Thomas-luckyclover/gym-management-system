package com.gms.dao;

import com.gms.dto.DependentCustomerRow;
import com.gms.entity.Customer;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Xử lý truy vấn dữ liệu bảng Customers.
 */
public class CustomerDAO {

    // ===== Thêm mới =====

    /**
     * Thêm mới một Customer trong transaction dùng chung Connection.
     * @param conn Connection tham gia transaction
     * @param customer đối tượng Customer cần chèn
     * @return CustomerId tự sinh trong database
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public int insert(Connection conn, Customer customer) throws SQLException {
        String sql = "INSERT INTO Customers (PersonId, CustomerCode, CheckInPhoto, CustomerType, "
                + "GuardianName, GuardianRelationship, GuardianPhone, GuardianPhone2, Status, JoinedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // B1: chuẩn bị PreparedStatement với cờ lấy generated keys
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, customer.getPersonId());
            stmt.setString(2, customer.getCustomerCode());
            stmt.setString(3, customer.getCheckInPhoto());
            stmt.setString(4, customer.getCustomerType());
            stmt.setString(5, customer.getGuardianName());
            stmt.setString(6, customer.getGuardianRelationship());
            stmt.setString(7, customer.getGuardianPhone());
            stmt.setString(8, customer.getGuardianPhone2());
            stmt.setString(9, customer.getStatus());

            Timestamp joinedAtTimestamp = customer.getJoinedAt() != null
                    ? Timestamp.valueOf(customer.getJoinedAt())
                    : new Timestamp(System.currentTimeMillis());
            stmt.setTimestamp(10, joinedAtTimestamp);

            // B2: thực thi câu lệnh SQL
            stmt.executeUpdate();

            // B3: đọc CustomerId tự sinh
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int generatedId = rs.getInt(1);
                    customer.setCustomerId(generatedId);
                    return generatedId;
                }
            }
        }
        throw new SQLException("Thêm Customer thất bại, không nhận được CustomerId");
    }

    /**
     * Thêm mới một Customer dùng Connection riêng lẻ ngoài transaction.
     * @param customer đối tượng Customer cần chèn
     * @return CustomerId tự sinh trong database
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public int insert(Customer customer) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return insert(conn, customer);
        }
    }

    // ===== Truy vấn tìm kiếm =====

    /**
     * Tìm Customer theo CustomerId.
     * @param customerId khóa chính của Customer
     * @return đối tượng Customer hoặc null nếu không tồn tại
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public Customer findById(int customerId) throws SQLException {
        String sql = "SELECT CustomerId, PersonId, CustomerCode, CheckInPhoto, CustomerType, "
                + "GuardianName, GuardianRelationship, GuardianPhone, GuardianPhone2, Status, JoinedAt, DeletedAt "
                + "FROM Customers WHERE CustomerId = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, customerId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCustomer(rs);
                }
            }
        }
        return null;
    }

    /**
     * Tìm Customer theo PersonId.
     * @param personId ID của Person sở hữu hồ sơ khách
     * @return đối tượng Customer hoặc null nếu không tồn tại
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public Customer findByPersonId(int personId) throws SQLException {
        String sql = "SELECT CustomerId, PersonId, CustomerCode, CheckInPhoto, CustomerType, "
                + "GuardianName, GuardianRelationship, GuardianPhone, GuardianPhone2, Status, JoinedAt, DeletedAt "
                + "FROM Customers WHERE PersonId = ? "
                // ưu tiên hồ sơ còn hiệu lực, mới nhất (DBR-65)
                + "ORDER BY CASE WHEN Status = 'Inactive' THEN 1 ELSE 0 END, CustomerId DESC";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, personId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToCustomer(rs);
                }
            }
        }
        return null;
    }

    // ===== Hồ sơ phụ thuộc (CBR-33) =====

    /**
     * Lấy các hồ sơ Dependent theo SĐT người giám hộ.
     * @param guardianPhone SĐT giám hộ chính hoặc giám hộ thứ 2
     * @return danh sách dòng dữ liệu thô, rỗng nếu không có hồ sơ nào
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public List<DependentCustomerRow> findDependentsByGuardianPhone(String guardianPhone) throws SQLException {
        // B1: dựng SQL Dependent kèm gói tập còn hiệu lực (CBR-33)
        List<DependentCustomerRow> rows = new ArrayList<>();

        String sql = "SELECT c.CustomerCode, p.FullName, p.DateOfBirth, s.PackageName, s.EndDate "
                + "FROM Customers c JOIN Persons p ON p.PersonId = c.PersonId "
                + "OUTER APPLY (SELECT TOP 1 o.PackageNameSnapshot AS PackageName, sub.EndDate "
                + "FROM Subscriptions sub JOIN Orders o ON o.OrderId = sub.OrderId "
                + "WHERE sub.CustomerId = c.CustomerId AND sub.SubscriptionType = 'Membership' "
                + "AND sub.Status IN ('Active', 'Frozen') AND sub.EndDate >= ? "
                + "ORDER BY sub.EndDate DESC) s "
                + "WHERE c.CustomerType = 'Dependent' AND c.Status <> 'Inactive' "
                + "AND (c.GuardianPhone = ? OR c.GuardianPhone2 = ?) "
                + "ORDER BY c.CustomerId";

        // B2: mở kết nối, gán ngày hôm nay và SĐT giám hộ
        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(DateTimeUtil.today()));
            stmt.setString(2, guardianPhone);
            stmt.setString(3, guardianPhone);

            // B3: ánh xạ từng dòng sang DependentCustomerRow
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapResultSetToDependentRow(rs));
                }
            }
        }

        // B4: trả danh sách, rỗng nếu không có hồ sơ phụ thuộc
        return rows;
    }

    // ===== Sinh mã khách hàng tự động (DBR-09) =====

    /**
     * Sinh mã khách hàng tiếp theo theo định dạng KH-nnnn.
     * @param conn Connection tham gia transaction
     * @return mã khách hàng mới, ví dụ KH-1001
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public String generateNextCustomerCode(Connection conn) throws SQLException {
        String sql = "SELECT MAX(CustomerId) FROM Customers";

        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            int nextNumber = 1;
            if (rs.next()) {
                int maxId = rs.getInt(1);
                nextNumber = maxId + 1;
            }
            return String.format("KH-%04d", nextNumber);
        }
    }

    // ===== Hàm hỗ trợ ánh xạ =====

    private DependentCustomerRow mapResultSetToDependentRow(ResultSet rs) throws SQLException {
        LocalDate dateOfBirth = rs.getDate("DateOfBirth").toLocalDate();

        LocalDate packageEndDate = null;
        Date endDate = rs.getDate("EndDate");
        if (endDate != null) {
            packageEndDate = endDate.toLocalDate();
        }

        return new DependentCustomerRow(
                rs.getString("FullName"),
                rs.getString("CustomerCode"),
                dateOfBirth,
                rs.getString("PackageName"),
                packageEndDate);
    }

    private Customer mapResultSetToCustomer(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setCustomerId(rs.getInt("CustomerId"));
        customer.setPersonId(rs.getInt("PersonId"));
        customer.setCustomerCode(rs.getString("CustomerCode"));
        customer.setCheckInPhoto(rs.getString("CheckInPhoto"));
        customer.setCustomerType(rs.getString("CustomerType"));
        customer.setGuardianName(rs.getString("GuardianName"));
        customer.setGuardianRelationship(rs.getString("GuardianRelationship"));
        customer.setGuardianPhone(rs.getString("GuardianPhone"));
        customer.setGuardianPhone2(rs.getString("GuardianPhone2"));
        customer.setStatus(rs.getString("Status"));

        Timestamp joinedAt = rs.getTimestamp("JoinedAt");
        if (joinedAt != null) {
            customer.setJoinedAt(joinedAt.toLocalDateTime());
        }

        Timestamp deletedAt = rs.getTimestamp("DeletedAt");
        if (deletedAt != null) {
            customer.setDeletedAt(deletedAt.toLocalDateTime());
        }

        return customer;
    }
}