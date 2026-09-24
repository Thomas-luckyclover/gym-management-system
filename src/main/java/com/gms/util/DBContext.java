package com.gms.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Cấp Connection tới SQL Server cho DAO và Service (khi mở transaction).
 *
 * Cách dùng (luôn try-with-resources để connection tự đóng — codeImplementationBackend.md Mục 13):
 *
 *   try (Connection conn = DBContext.getConnection();
 *        PreparedStatement stmt = conn.prepareStatement(sql)) {
 *       ...
 *   }
 *
 * Thông tin kết nối đọc từ application.properties (db.url, db.username, db.password).
 */
public final class DBContext {

    private static final String DRIVER_CLASS = "com.microsoft.sqlserver.jdbc.SQLServerDriver";

    private DBContext() {
    }

    /**
     * Mở 1 connection mới. Người gọi chịu trách nhiệm đóng (try-with-resources).
     * @return Connection đang mở
     * @throws SQLException khi không kết nối được — DAO ném tiếp lên, Service bắt và báo lỗi thân thiện
     */
    public static Connection getConnection() throws SQLException {
        // B1: nạp driver (Tomcat đôi khi không tự nạp driver nằm trong WEB-INF/lib)
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Không tìm thấy driver SQL Server, kiểm tra dependency mssql-jdbc trong pom.xml", e);
        }

        // B2: đọc cấu hình kết nối
        String url = AppConfig.get("db.url");
        String username = AppConfig.get("db.username");
        String password = AppConfig.get("db.password");

        // B3: mở kết nối
        return DriverManager.getConnection(url, username, password);
    }
}
