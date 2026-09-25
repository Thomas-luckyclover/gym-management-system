package com.gms.dao;

import com.gms.entity.Person;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * Xử lý truy vấn dữ liệu bảng Persons.
 */
public class PersonDAO {

    // ===== Thêm mới =====

    /**
     * Thêm mới một Person trong transaction dùng chung Connection.
     * @param conn Connection tham gia transaction
     * @param person đối tượng Person cần chèn
     * @return PersonId tự sinh trong database
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public int insert(Connection conn, Person person) throws SQLException {
        String sql = "INSERT INTO Persons (FullName, DateOfBirth, Phone, Email, Avatar, IsReleased, CreatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        // B1: chuẩn bị PreparedStatement với cờ lấy generated keys
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, person.getFullName());
            stmt.setDate(2, Date.valueOf(person.getDateOfBirth()));
            stmt.setString(3, person.getPhone());
            stmt.setString(4, person.getEmail());
            stmt.setString(5, person.getAvatar());
            stmt.setBoolean(6, person.isReleased());

            Timestamp createdAtTimestamp = person.getCreatedAt() != null
                    ? Timestamp.valueOf(person.getCreatedAt())
                    : new Timestamp(System.currentTimeMillis());
            stmt.setTimestamp(7, createdAtTimestamp);

            // B2: thực thi câu lệnh SQL
            stmt.executeUpdate();

            // B3: đọc PersonId tự sinh
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int generatedId = rs.getInt(1);
                    person.setPersonId(generatedId);
                    return generatedId;
                }
            }
        }
        throw new SQLException("Thêm Person thất bại, không nhận được PersonId");
    }

    /**
     * Thêm mới một Person dùng Connection riêng lẻ ngoài transaction.
     * @param person đối tượng Person cần chèn
     * @return PersonId tự sinh trong database
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public int insert(Person person) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return insert(conn, person);
        }
    }

    // ===== Cập nhật =====

    /**
     * Cập nhật thông tin cơ bản của Person (họ tên, ngày sinh, avatar) trong transaction.
     * @param conn Connection tham gia transaction
     * @param person đối tượng Person chứa dữ liệu mới
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public void updateProfile(Connection conn, Person person) throws SQLException {
        String sql = "UPDATE Persons SET FullName = ?, DateOfBirth = ?, Avatar = ? WHERE PersonId = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, person.getFullName());
            stmt.setDate(2, Date.valueOf(person.getDateOfBirth()));
            stmt.setString(3, person.getAvatar());
            stmt.setInt(4, person.getPersonId());

            stmt.executeUpdate();
        }
    }

    // ===== Truy vấn tìm kiếm =====

    /**
     * Tìm Person theo PersonId.
     * @param personId khóa chính của Person
     * @return đối tượng Person hoặc null nếu không tồn tại
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public Person findById(int personId) throws SQLException {
        String sql = "SELECT PersonId, FullName, DateOfBirth, Phone, Email, Avatar, IsReleased, CreatedAt "
                + "FROM Persons WHERE PersonId = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, personId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPerson(rs);
                }
            }
        }
        return null;
    }

    /**
     * Tìm Person đang hoạt động (IsReleased = 0) theo số điện thoại.
     * @param phone số điện thoại 10 chữ số
     * @return đối tượng Person hoặc null nếu không tìm thấy
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public Person findByPhone(String phone) throws SQLException {
        String sql = "SELECT PersonId, FullName, DateOfBirth, Phone, Email, Avatar, IsReleased, CreatedAt "
                + "FROM Persons WHERE Phone = ? AND IsReleased = 0";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, phone);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPerson(rs);
                }
            }
        }
        return null;
    }

    /**
     * Tìm Person đang hoạt động (IsReleased = 0) theo địa chỉ email.
     * @param email địa chỉ email
     * @return đối tượng Person hoặc null nếu không tìm thấy
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public Person findByEmail(String email) throws SQLException {
        String sql = "SELECT PersonId, FullName, DateOfBirth, Phone, Email, Avatar, IsReleased, CreatedAt "
                + "FROM Persons WHERE Email = ? AND IsReleased = 0";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPerson(rs);
                }
            }
        }
        return null;
    }

    // ===== Kiểm tra trùng lặp dữ liệu =====

    /**
     * Kiểm tra số điện thoại có đang tồn tại ở hồ sơ active không (DBR-22).
     * @param phone số điện thoại cần kiểm tra
     * @return true nếu đã có người khác đang sử dụng
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public boolean existsByPhone(String phone) throws SQLException {
        String sql = "SELECT 1 FROM Persons WHERE Phone = ? AND IsReleased = 0";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, phone);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Kiểm tra email có đang tồn tại ở hồ sơ active không (DBR-23).
     * @param email địa chỉ email cần kiểm tra
     * @return true nếu đã có người khác đang sử dụng
     * @throws SQLException khi truy vấn gặp lỗi
     */
    public boolean existsByEmail(String email) throws SQLException {
        String sql = "SELECT 1 FROM Persons WHERE Email = ? AND IsReleased = 0";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ===== Hàm hỗ trợ ánh xạ =====

    private Person mapResultSetToPerson(ResultSet rs) throws SQLException {
        Person person = new Person();
        person.setPersonId(rs.getInt("PersonId"));
        person.setFullName(rs.getString("FullName"));

        Date dob = rs.getDate("DateOfBirth");
        if (dob != null) {
            person.setDateOfBirth(dob.toLocalDate());
        }

        person.setPhone(rs.getString("Phone"));
        person.setEmail(rs.getString("Email"));
        person.setAvatar(rs.getString("Avatar"));
        person.setReleased(rs.getBoolean("IsReleased"));

        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) {
            person.setCreatedAt(createdAt.toLocalDateTime());
        }

        return person;
    }
    /**
     * Cập nhật đường dẫn ảnh đại diện của hồ sơ.
     *
     * @param personId mã hồ sơ
     * @param avatarPath đường dẫn ảnh đại diện
     * @return true nếu cập nhật thành công
     * @throws SQLException khi truy vấn database lỗi
     */
    public boolean updateAvatar(int personId, String avatarPath) throws SQLException {
        String sql = "UPDATE Persons SET Avatar = ? WHERE PersonId = ?";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, avatarPath);
            stmt.setInt(2, personId);

            return stmt.executeUpdate() == 1;
        }
    }
}
