package com.gms.util;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import javax.sql.DataSource;

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
 *
 * ===== Connection pool =====
 * Mở 1 kết nối SQL Server mới tốn khá nhiều thời gian (TCP + đăng nhập). Thay vì mở mới mỗi request,
 * DBContext giữ sẵn 1 "hồ" (pool) kết nối đang mở:
 *   - getConnection()  → MƯỢN 1 kết nối rảnh trong pool (hết thì mở thêm, tối đa db.pool.max-total)
 *   - conn.close()     → TRẢ kết nối về pool (không đóng thật). Pool tự rollback phần dở dang
 *                         và bật lại autoCommit, nên transaction của request trước không lọt sang request sau.
 * Code DAO/Service KHÔNG phải sửa gì: vẫn try-with-resources như cũ.
 *
 * Pool dùng là Apache Commons DBCP2 có sẵn trong Tomcat (file lib/tomcat-dbcp.jar) → không thêm dependency.
 * Vì jar đó chỉ có lúc chạy trong Tomcat, DBContext gọi nó qua reflection.
 * Chạy ngoài Tomcat (mvn test) không có jar → tự quay về DriverManager như trước.
 *
 * Tại sao không khai báo <Resource> trong META-INF/context.xml? Vì context.xml có commit lên Git,
 * còn mật khẩu DB mỗi máy một khác và chỉ được nằm trong application.properties (không commit).
 */
public final class DBContext {

    private static final String DRIVER_CLASS = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String TOMCAT_POOL_CLASS = "org.apache.tomcat.dbcp.dbcp2.BasicDataSource";

    // Giá trị mặc định, có thể ghi đè trong application.properties (không bắt buộc)
    private static final int DEFAULT_MAX_TOTAL = 20;      // tối đa 20 kết nối mở cùng lúc
    private static final int DEFAULT_MIN_IDLE = 2;        // luôn giữ sẵn 2 kết nối rảnh
    private static final long DEFAULT_MAX_WAIT_MS = 10000; // hết kết nối rảnh → chờ tối đa 10 giây rồi báo lỗi

    // null = chưa tạo; volatile để các luồng request thấy ngay pool vừa tạo
    private static volatile DataSource pool;
    private static volatile boolean poolUnavailable;

    private DBContext() {
    }

    /**
     * Mượn 1 connection. Người gọi chịu trách nhiệm đóng (try-with-resources) để trả về pool.
     * @return Connection đang mở
     * @throws SQLException khi không kết nối được — DAO ném tiếp lên, Service bắt và báo lỗi thân thiện
     */
    public static Connection getConnection() throws SQLException {
        // B1: chạy trong Tomcat → lấy từ pool
        DataSource dataSource = getPool();
        if (dataSource != null) {
            return dataSource.getConnection();
        }

        // B2: không có pool (chạy unit test ngoài Tomcat) → mở kết nối trực tiếp như cũ
        loadDriver();
        String url = AppConfig.get("db.url");
        String username = AppConfig.get("db.username");
        String password = AppConfig.get("db.password");
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * Đóng toàn bộ kết nối trong pool. Gọi 1 lần khi app dừng (DataSourceLifecycleListener).
     */
    public static synchronized void closePool() {
        DataSource current = pool;
        pool = null;
        if (current == null) {
            return;
        }
        try {
            current.getClass().getMethod("close").invoke(current);
        } catch (ReflectiveOperationException e) {
            System.err.println("[DBContext] Không đóng được connection pool: " + e);
        }
    }

    // Tạo pool đúng 1 lần (double-checked locking): nhiều request đầu tiên đến cùng lúc cũng chỉ tạo 1 pool
    private static DataSource getPool() {
        DataSource current = pool;
        if (current != null || poolUnavailable) {
            return current;
        }
        synchronized (DBContext.class) {
            if (pool == null && !poolUnavailable) {
                pool = createTomcatPool();
                poolUnavailable = pool == null;
            }
            return pool;
        }
    }

    private static DataSource createTomcatPool() {
        // B1: có jar pool của Tomcat không? Không có = đang chạy ngoài Tomcat
        Class<?> poolClass;
        try {
            poolClass = Class.forName(TOMCAT_POOL_CLASS);
        } catch (ClassNotFoundException e) {
            return null;
        }

        try {
            // B2: tạo pool và gán cấu hình đọc từ application.properties
            DataSource dataSource = (DataSource) poolClass.getDeclaredConstructor().newInstance();
            call(dataSource, "setDriverClassName", String.class, DRIVER_CLASS);
            // Driver mssql-jdbc nằm trong WEB-INF/lib → chỉ classloader của app thấy được
            call(dataSource, "setDriverClassLoader", ClassLoader.class, DBContext.class.getClassLoader());
            call(dataSource, "setUrl", String.class, AppConfig.get("db.url"));
            call(dataSource, "setUsername", String.class, AppConfig.get("db.username"));
            call(dataSource, "setPassword", String.class, AppConfig.get("db.password"));

            int maxTotal = Integer.parseInt(AppConfig.get("db.pool.max-total", String.valueOf(DEFAULT_MAX_TOTAL)));
            int minIdle = Integer.parseInt(AppConfig.get("db.pool.min-idle", String.valueOf(DEFAULT_MIN_IDLE)));
            long maxWaitMs = Long.parseLong(AppConfig.get("db.pool.max-wait-ms", String.valueOf(DEFAULT_MAX_WAIT_MS)));
            call(dataSource, "setMaxTotal", int.class, maxTotal);
            call(dataSource, "setMaxIdle", int.class, maxTotal);
            call(dataSource, "setMinIdle", int.class, minIdle);
            call(dataSource, "setMaxWaitMillis", long.class, maxWaitMs);

            // B3: SQL Server khởi động lại / mạng rớt → kết nối cũ trong pool chết.
            // Kiểm tra trước khi đưa ra để request không nhận phải kết nối hỏng.
            call(dataSource, "setValidationQuery", String.class, "SELECT 1");
            call(dataSource, "setTestOnBorrow", boolean.class, true);

            System.out.println("[DBContext] Dùng connection pool của Tomcat (tối đa " + maxTotal + " kết nối)");
            return dataSource;

        } catch (ReflectiveOperationException | RuntimeException e) {
            // Không tạo được pool (bản Tomcat khác tên hàm, db.pool.* gõ sai...) → KHÔNG làm sập app:
            // in cảnh báo rồi quay về mở kết nối trực tiếp như trước.
            System.err.println("[DBContext] Không tạo được connection pool, dùng kết nối trực tiếp: " + e);
            return null;
        }
    }

    private static void call(Object target, String methodName, Class<?> paramType, Object value)
            throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, paramType);
        method.invoke(target, value);
    }

    private static void loadDriver() throws SQLException {
        // Tomcat đôi khi không tự nạp driver nằm trong WEB-INF/lib
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Không tìm thấy driver SQL Server, kiểm tra dependency mssql-jdbc trong pom.xml", e);
        }
    }
}
