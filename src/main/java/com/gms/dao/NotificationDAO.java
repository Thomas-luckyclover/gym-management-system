package com.gms.dao;

import com.gms.constant.Role;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.entity.Notification;
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
 * DAO quản lý thao tác dữ liệu cho bảng {@code Notifications} và {@code NotificationRecipients}.
 *
 * <p><b>Cách hoạt động:</b> Cung cấp các truy vấn tạo thông báo hệ thống, tìm tài khoản người nhận
 * đang hoạt động theo vai trò/nhân viên, đếm và tải danh sách thông báo trên chuông cũng như cập nhật trạng thái đã đọc.</p>
 */
public class NotificationDAO {

    /**
     * Độ dài tối đa của cột {@code Notifications.Title} trong CSDL (NVARCHAR(100)).
     */
    public static final int TITLE_MAX_LENGTH = 100;

    /**
     * Độ dài tối đa của cột {@code Notifications.Content} trong CSDL (NVARCHAR(500)).
     */
    public static final int CONTENT_MAX_LENGTH = 500;

    // ===== Tạo notification hệ thống =====

    /**
     * Tạo một bản ghi thông báo hệ thống tự động (không gắn nhân viên gửi) trong transaction.
     *
     * <p><b>Cách hoạt động:</b> Cắt gọn an toàn {@code title} (<= 100 ký tự) và {@code content} (<= 500 ký tự)
     * khớp độ dài cột CSDL, chèn bản ghi mới vào {@code Notifications} với {@code SentByEmployeeId = NULL},
     * {@code SentAt = SYSDATETIME()}, bật {@code RETURN_GENERATED_KEYS} để lấy và trả về {@code NotificationId}.</p>
     *
     * @param connection kết nối CSDL đang mở transaction
     * @param title      tiêu đề thông báo
     * @param content    nội dung thông báo
     * @return ID thông báo vừa được tạo
     * @throws SQLException khi thêm bản ghi thất bại hoặc không lấy được khóa tự tăng
     */
    public int insertSystemNotification(
            Connection connection,
            String title,
            String content
    ) throws SQLException {

        String sql = "INSERT INTO Notifications "
                + "(SentByEmployeeId, Title, Content, Audience, ScheduledAt, SentAt) "
                + "VALUES (NULL, ?, ?, ?, NULL, SYSDATETIME())";

        String safeTitle = safeTruncate(title, TITLE_MAX_LENGTH);
        String safeContent = safeTruncate(content, CONTENT_MAX_LENGTH);

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            statement.setString(1, safeTitle);
            statement.setString(2, safeContent);
            statement.setString(
                    3,
                    Notification.Audience.INDIVIDUAL.getDbValue()
            );

            statement.executeUpdate();

            try (ResultSet resultSet =
                         statement.getGeneratedKeys()) {

                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }

        throw new SQLException(
                "Không nhận được NotificationId."
        );
    }

    /**
     * Cắt gọn chuỗi an toàn theo độ dài tối đa của cột {@code Notifications}, giữ dấu {@code ...} khi bị cắt.
     */
    public static String safeTruncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (maxLen <= 0) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        if (maxLen <= 3) {
            return trimmed.substring(0, maxLen);
        }
        String prefix = trimmed.substring(0, maxLen - 3).stripTrailing();
        if (prefix.isEmpty()) {
            return trimmed.substring(0, maxLen);
        }
        return prefix + "...";
    }

    /**
     * Gán một tài khoản vào danh sách người nhận của một thông báo với trạng thái chưa đọc.
     *
     * <p><b>Cách hoạt động:</b> Thực thi {@code INSERT} vào {@code NotificationRecipients}
     * gồm {@code NotificationId}, {@code AccountId} và {@code ReadAt = NULL} trong transaction hiện tại.</p>
     *
     * @param connection     kết nối CSDL đang mở transaction
     * @param notificationId ID thông báo nguồn
     * @param accountId      ID tài khoản nhận thông báo
     * @throws SQLException khi lưu thất bại
     */
    public void insertRecipient(
            Connection connection,
            int notificationId,
            int accountId
    ) throws SQLException {

        String sql =
                "INSERT INTO NotificationRecipients "
                        + "(NotificationId, AccountId, ReadAt) "
                        + "VALUES (?, ?, NULL)";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, notificationId);
            statement.setInt(2, accountId);

            statement.executeUpdate();
        }
    }

    // ===== Tìm người nhận =====

    /**
     * Tìm danh sách {@code AccountId} đang hoạt động theo vai trò nhận thông báo.
     *
     * <p><b>Cách hoạt động:</b> Với {@code CUSTOMER}, truy vấn bảng {@code Customers} JOIN {@code Accounts}
     * lọc khách hàng chưa khóa và tài khoản {@code Active}; với vai trò nhân sự ({@code MANAGER}, {@code RECEPTIONIST}, ...),
     * truy vấn {@code Employees} JOIN {@code Accounts} lọc nhân viên đang làm việc ({@code WORKING}) và tài khoản {@code Active}.</p>
     *
     * @param connection kết nối CSDL đang mở transaction
     * @param role       vai trò cần tìm người nhận
     * @return danh sách {@code AccountId} hợp lệ
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public List<Integer> findActiveAccountIdsByRole(
            Connection connection,
            Role role
    ) throws SQLException {

        String sql;
        if (role == Role.CUSTOMER) {
            sql = "SELECT DISTINCT a.AccountId "
                    + "FROM Customers c "
                    + "JOIN Accounts a "
                    + "ON a.PersonId = c.PersonId "
                    + "WHERE c.Status <> 'Inactive' "
                    + "AND a.Role = ? "
                    + "AND a.Status = ?";
        } else {
            sql = "SELECT DISTINCT a.AccountId "
                    + "FROM Employees e "
                    + "JOIN Accounts a "
                    + "ON a.PersonId = e.PersonId "
                    + "WHERE e.Position = ? "
                    + "AND e.Status = ? "
                    + "AND a.Status = ?";
        }

        List<Integer> accountIds =
                new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            if (role == Role.CUSTOMER) {
                statement.setString(
                        1,
                        role.getDbValue()
                );
                statement.setString(
                        2,
                        Account.Status.ACTIVE.getDbValue()
                );
            } else {
                statement.setString(
                        1,
                        role.getDbValue()
                );
                statement.setString(
                        2,
                        Employee.Status.WORKING.getDbValue()
                );
                statement.setString(
                        3,
                        Account.Status.ACTIVE.getDbValue()
                );
            }

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    accountIds.add(
                            resultSet.getInt("AccountId")
                    );
                }
            }
        }

        return accountIds;
    }

    /**
     * Tìm {@code AccountId} đang hoạt động ứng với một {@code EmployeeId} cụ thể.
     *
     * <p><b>Cách hoạt động:</b> Nối {@code Employees} với {@code Accounts} theo {@code PersonId},
     * lọc theo {@code EmployeeId} và tài khoản trạng thái {@code Active}; trả về {@code null} nếu không tìm thấy.</p>
     *
     * @param connection kết nối CSDL đang mở transaction
     * @param employeeId ID nhân viên nhận thông báo
     * @return {@code AccountId} hoạt động hoặc {@code null}
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public Integer findActiveAccountIdByEmployeeId(
            Connection connection,
            int employeeId
    ) throws SQLException {

        String sql =
                "SELECT a.AccountId "
                        + "FROM Employees e "
                        + "JOIN Accounts a "
                        + "ON a.PersonId = e.PersonId "
                        + "WHERE e.EmployeeId = ? "
                        + "AND a.Status = ?";

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, employeeId);

            statement.setString(
                    2,
                    Account.Status.ACTIVE.getDbValue()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getInt("AccountId");
                }
            }
        }

        return null;
    }

    // ===== Chuông thông báo =====

    /**
     * Đếm số lượng thông báo chưa đọc hiện có của một tài khoản.
     *
     * <p><b>Cách hoạt động:</b> Thực thi {@code COUNT(*)} trên {@code NotificationRecipients} JOIN {@code Notifications}
     * với điều kiện {@code ReadAt IS NULL} và đã phát hành ({@code SentAt IS NOT NULL}).</p>
     *
     * @param accountId ID tài khoản cần đếm
     * @return số thông báo chưa đọc
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public int countUnread(int accountId)
            throws SQLException {

        String sql =
                "SELECT COUNT(*) "
                        + "FROM NotificationRecipients nr "
                        + "JOIN Notifications n "
                        + "ON n.NotificationId = nr.NotificationId "
                        + "WHERE nr.AccountId = ? "
                        + "AND nr.ReadAt IS NULL "
                        + "AND n.SentAt IS NOT NULL";

        try (Connection connection =
                     DBContext.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, accountId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }

        return 0;
    }

    /**
     * Lấy danh sách thông báo mới nhất (kèm thông tin người gửi) cho chuông thông báo hoặc trang xem tất cả.
     *
     * <p><b>Cách hoạt động:</b> Nối {@code NotificationRecipients}, {@code Notifications} và {@code Employees}/{@code Persons}
     * (LEFT JOIN để giữ cả thông báo hệ thống có {@code SentByEmployeeId = NULL}), sắp xếp theo {@code SentAt DESC},
     * giới hạn số dòng qua {@code statement.setMaxRows(limit)} khi {@code limit > 0} và ánh xạ bằng {@link #mapBellRow}.</p>
     *
     * @param accountId ID tài khoản cần tải thông báo
     * @param limit     số lượng tối đa cần lấy (<= 0 để lấy toàn bộ)
     * @return danh sách {@link NotificationBellRow} mới nhất
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public List<NotificationBellRow> findLatestForAccount(
            int accountId,
            int limit
    ) throws SQLException {

        String sql =
                "SELECT "
                        + "n.NotificationId, "
                        + "n.SentByEmployeeId, "
                        + "n.Title, "
                        + "n.Content, "
                        + "n.Audience, "
                        + "n.ScheduledAt, "
                        + "n.SentAt, "
                        + "n.CreatedAt, "
                        + "nr.ReadAt, "
                        + "p.FullName AS SenderName, "
                        + "e.EmployeeCode AS SenderCode, "
                        + "e.Position AS SenderPosition "
                        + "FROM NotificationRecipients nr "
                        + "JOIN Notifications n "
                        + "ON n.NotificationId = nr.NotificationId "
                        + "LEFT JOIN Employees e "
                        + "ON e.EmployeeId = n.SentByEmployeeId "
                        + "LEFT JOIN Persons p "
                        + "ON p.PersonId = e.PersonId "
                        + "WHERE nr.AccountId = ? "
                        + "AND n.SentAt IS NOT NULL "
                        + "ORDER BY n.SentAt DESC, "
                        + "n.NotificationId DESC";

        List<NotificationBellRow> rows =
                new ArrayList<>();

        try (Connection connection =
                     DBContext.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, accountId);
            if (limit > 0) {
                statement.setMaxRows(limit);
            }

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    rows.add(
                            mapBellRow(resultSet)
                    );
                }
            }
        }

        return rows;
    }

    // ===== Đã đọc =====

    /**
     * Đánh dấu một thông báo cụ thể là đã đọc đối với tài khoản.
     *
     * <p><b>Cách hoạt động:</b> Cập nhật {@code nr.ReadAt = COALESCE(nr.ReadAt, SYSDATETIME())} (giữ nguyên
     * thời điểm đọc cũ nếu đã đọc trước đó) trên dòng khớp {@code AccountId} và {@code NotificationId}.</p>
     *
     * @param accountId      ID tài khoản người nhận
     * @param notificationId ID thông báo cần đánh dấu đã đọc
     * @return {@code true} nếu cập nhật đúng 1 bản ghi, ngược lại {@code false}
     * @throws SQLException khi cập nhật CSDL lỗi
     */
    public boolean markRead(
            int accountId,
            int notificationId
    ) throws SQLException {

        String sql =
                "UPDATE nr "
                        + "SET nr.ReadAt = "
                        + "COALESCE(nr.ReadAt, SYSDATETIME()) "
                        + "FROM NotificationRecipients nr "
                        + "JOIN Notifications n "
                        + "ON n.NotificationId = nr.NotificationId "
                        + "WHERE nr.AccountId = ? "
                        + "AND nr.NotificationId = ? "
                        + "AND n.SentAt IS NOT NULL";

        try (Connection connection =
                     DBContext.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, accountId);
            statement.setInt(2, notificationId);

            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Đánh dấu toàn bộ thông báo chưa đọc của tài khoản thành đã đọc.
     *
     * <p><b>Cách hoạt động:</b> Cập nhật hàng loạt {@code nr.ReadAt = SYSDATETIME()} cho tất cả bản ghi
     * thuộc {@code accountId} có {@code ReadAt IS NULL} và {@code SentAt IS NOT NULL}.</p>
     *
     * @param accountId ID tài khoản
     * @return số lượng thông báo vừa chuyển sang đã đọc
     * @throws SQLException khi cập nhật CSDL lỗi
     */
    public int markAllRead(int accountId)
            throws SQLException {

        String sql =
                "UPDATE nr "
                        + "SET nr.ReadAt = SYSDATETIME() "
                        + "FROM NotificationRecipients nr "
                        + "JOIN Notifications n "
                        + "ON n.NotificationId = nr.NotificationId "
                        + "WHERE nr.AccountId = ? "
                        + "AND nr.ReadAt IS NULL "
                        + "AND n.SentAt IS NOT NULL";

        try (Connection connection =
                     DBContext.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, accountId);

            return statement.executeUpdate();
        }
    }

    /**
     * Tự động đánh dấu đã đọc các thông báo chưa đọc theo nhóm từ khóa chủ đề khi người dùng mở màn hình tương ứng.
     *
     * <p><b>Cách hoạt động:</b> Ghép động điều kiện {@code (n.Title LIKE ? OR n.Content LIKE ?)} nối bằng {@code OR}
     * cho từng từ khóa và cập nhật {@code nr.ReadAt = SYSDATETIME()} cho các bản ghi đang có {@code ReadAt IS NULL}.</p>
     *
     * @param accountId ID tài khoản nhận thông báo
     * @param keywords  danh sách từ khóa chủ đề (ví dụ: {@code "Bảo trì"}, {@code "nghỉ phép"})
     * @return số lượng thông báo đã được đánh dấu đọc
     * @throws SQLException nếu xảy ra lỗi truy vấn CSDL
     */
    public int markReadByKeywords(
            int accountId,
            String... keywords
    ) throws SQLException {

        if (accountId <= 0 || keywords == null || keywords.length == 0) {
            return 0;
        }

        StringBuilder sql = new StringBuilder(
                "UPDATE nr "
                        + "SET nr.ReadAt = SYSDATETIME() "
                        + "FROM NotificationRecipients nr "
                        + "JOIN Notifications n "
                        + "ON n.NotificationId = nr.NotificationId "
                        + "WHERE nr.AccountId = ? "
                        + "AND nr.ReadAt IS NULL "
                        + "AND n.SentAt IS NOT NULL AND ("
        );

        for (int i = 0; i < keywords.length; i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("n.Title LIKE ? OR n.Content LIKE ?");
        }
        sql.append(")");

        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {

            statement.setInt(1, accountId);
            int paramIndex = 2;
            for (String keyword : keywords) {
                String pattern = "%" + (keyword == null ? "" : keyword.trim()) + "%";
                statement.setString(paramIndex++, pattern);
                statement.setString(paramIndex++, pattern);
            }

            return statement.executeUpdate();
        }
    }

    // ===== Ánh xạ dữ liệu =====

    /**
     * Ánh xạ một dòng {@code ResultSet} thành DTO {@link NotificationBellRow}.
     *
     * <p><b>Cách hoạt động:</b> Tạo entity {@link Notification} từ các cột thông báo (xử lý nullable
     * {@code SentByEmployeeId}), quy đổi các mốc thời gian và gói kèm {@code readAt} cùng thông tin người gửi.</p>
     */
    private NotificationBellRow mapBellRow(
            ResultSet resultSet
    ) throws SQLException {

        Notification notification =
                new Notification();

        notification.setNotificationId(
                resultSet.getInt("NotificationId")
        );

        int sentByEmployeeId =
                resultSet.getInt("SentByEmployeeId");

        notification.setSentByEmployeeId(
                resultSet.wasNull()
                        ? null
                        : sentByEmployeeId
        );

        notification.setTitle(
                resultSet.getString("Title")
        );

        notification.setContent(
                resultSet.getString("Content")
        );

        notification.setAudience(
                Notification.Audience.fromDbValue(
                        resultSet.getString("Audience")
                )
        );

        notification.setScheduledAt(
                toLocalDateTime(
                        resultSet.getTimestamp("ScheduledAt")
                )
        );

        notification.setSentAt(
                toLocalDateTime(
                        resultSet.getTimestamp("SentAt")
                )
        );

        notification.setCreatedAt(
                toLocalDateTime(
                        resultSet.getTimestamp("CreatedAt")
                )
        );

        LocalDateTime readAt =
                toLocalDateTime(
                        resultSet.getTimestamp("ReadAt")
                );

        String senderName =
                resultSet.getString("SenderName");

        String senderCode =
                resultSet.getString("SenderCode");

        String senderPosition =
                resultSet.getString("SenderPosition");

        return new NotificationBellRow(
                notification,
                readAt,
                senderName,
                senderCode,
                senderPosition
        );
    }

    /**
     * Chuyển đổi {@link Timestamp} từ SQL sang {@link LocalDateTime} an toàn với giá trị {@code null}.
     */
    private LocalDateTime toLocalDateTime(
            Timestamp timestamp
    ) {

        return timestamp == null
                ? null
                : timestamp.toLocalDateTime();
    }

    /**
     * DTO chứa bản ghi thông báo kèm trạng thái đã đọc và thông tin người gửi lấy từ truy vấn chuông.
     */
    public static class NotificationBellRow {

        private final Notification notification;
        private final LocalDateTime readAt;
        private final String senderName;
        private final String senderCode;
        private final String senderPosition;

        /**
         * Khởi tạo {@code NotificationBellRow} với đầy đủ thông báo, thời gian đọc và thông tin người gửi.
         */
        public NotificationBellRow(
                Notification notification,
                LocalDateTime readAt,
                String senderName,
                String senderCode,
                String senderPosition
        ) {
            this.notification = notification;
            this.readAt = readAt;
            this.senderName = senderName;
            this.senderCode = senderCode;
            this.senderPosition = senderPosition;
        }

        /** Trả về entity {@link Notification}. */
        public Notification getNotification() {
            return notification;
        }

        /** Trả về thời điểm người dùng đã đọc thông báo (hoặc {@code null} nếu chưa đọc). */
        public LocalDateTime getReadAt() {
            return readAt;
        }

        /** Kiểm tra thông báo đã được đọc hay chưa ({@code readAt != null}). */
        public boolean isRead() {
            return readAt != null;
        }

        /** Trả về họ tên nhân viên gửi thông báo (hoặc {@code null} nếu là thông báo hệ thống). */
        public String getSenderName() {
            return senderName;
        }

        /** Trả về mã nhân viên người gửi. */
        public String getSenderCode() {
            return senderCode;
        }

        /** Trả về chức vụ người gửi. */
        public String getSenderPosition() {
            return senderPosition;
        }
    }
}
