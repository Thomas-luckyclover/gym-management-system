package com.gms.dao;

import com.gms.constant.Role;
import com.gms.dto.AdminDashboardView;
import com.gms.dto.AdminDashboardView.PackageRevenueRow;
import com.gms.dto.AdminDashboardView.PtCommissionRow;
import com.gms.dto.AdminDashboardView.SensitiveActionRow;
import com.gms.dto.AdminDashboardView.SensitiveActorRow;
import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO phục vụ báo cáo tổng hợp vận hành, doanh thu và kiểm toán cho Admin Dashboard (UC-44).
 *
 * <p><b>Cách hoạt động:</b> Mở một kết nối CSDL dùng chung cho toàn bộ các truy vấn trong cùng kỳ báo cáo
 * nửa mở {@code [fromDate, toDate + 1 ngày)} để bảo đảm số liệu nhất quán, tối ưu kết nối và quy đổi
 * các mã thao tác/vai trò thô sang nhãn tiếng Việt chuẩn cho tầng trình bày.</p>
 */
public class ReportDAO {

    /**
     * Điều kiện lọc chung cho 2 bảng thao tác nhạy cảm (GBR-07):
     * lấy mọi dòng nhật ký trong {@code AuditLogs}, chỉ loại trừ 2 sự kiện đếm đăng nhập thường
     * ({@code SignIn}, {@code SignInFailed}) vốn đã hiển thị ở thẻ Hoạt động đăng nhập.
     */
    private static final String SENSITIVE_AUDIT_CONDITION =
            "Action NOT IN ('SignIn', 'SignInFailed')";

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("AccountLocked", "Khóa tạm tài khoản (sai mật khẩu)"),
            Map.entry("UPDATE_PRICE", "Đổi giá gói dịch vụ"),
            Map.entry("ACTIVATE", "Mở bán lại gói dịch vụ"),
            Map.entry("DEACTIVATE", "Ngừng bán gói"),
            Map.entry("FREEZE_MEMBERSHIP", "Bảo lưu gói tập"),
            Map.entry("CANCEL_FREEZE_EARLY", "Hủy bảo lưu sớm"),
            Map.entry("SHIFT_MEMBERSHIP_AFTER_FREEZE", "Dời hạn gói nối tiếp sau bảo lưu"),
            Map.entry("JOB_RESUME_FREEZE", "Tự động mở lại gói hết hạn bảo lưu"),
            Map.entry("JOB-01", "Tự động mở lại gói hết hạn bảo lưu"),
            Map.entry("JOB-02", "Tự động đóng gói tập hết hạn"),
            Map.entry("JOB-03", "Tự động đóng vé ngày hết hạn"),
            Map.entry("JOB-04", "Tự động chốt buổi PT cuối ngày"),
            Map.entry("JOB-05", "Tự động hủy đơn quá hạn thanh toán"),
            Map.entry("JOB-06", "Tự động đóng giao dịch quá hạn")
    );

    /**
     * Mở kết nối tới SQL Server. Tách thành {@code protected} để dễ kiểm thử hoặc mở rộng.
     */
    protected Connection openConnection() throws SQLException {
        return DBContext.getConnection();
    }

    /**
     * Tải toàn bộ dữ liệu Admin Dashboard trên cùng một {@link Connection} cho khoảng ngày đã chọn.
     *
     * <p><b>Cách hoạt động:</b> Lần lượt chạy 5 truy vấn vận hành/doanh thu và 5 truy vấn audit trên cùng kết nối,
     * ánh xạ mã thao tác và vai trò sang tiếng Việt rồi gói vào {@link AdminDashboardView}.</p>
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate   ngày kết thúc, tính cả ngày này
     * @return dữ liệu tổng hợp báo cáo Admin
     * @throws SQLException khi truy vấn CSDL gặp lỗi
     */
    public AdminDashboardView findAdminDashboard(LocalDate fromDate, LocalDate toDate) throws SQLException {
        try (Connection conn = openConnection()) {
            int checkInCount = countCheckIns(conn, fromDate, toDate);
            long totalRevenue = sumRevenue(conn, fromDate, toDate);
            List<PackageRevenueRow> revenueByPackage = findRevenueByPackage(conn, fromDate, toDate);
            long[] dayPass = getDayPassSummary(conn, fromDate, toDate);
            List<PtCommissionRow> ptCommissions = findPtCommissionSummary(conn, fromDate, toDate);

            Map<String, Integer> loginActions = countLoginAuditActions(conn, fromDate, toDate);
            int lockedAccountCount = countLockedAccounts(conn, fromDate, toDate);
            int accountAuditChangeCount = countAccountAuditChanges(conn, fromDate, toDate);
            List<SensitiveActionRow> sensitiveActions = findSensitiveActions(conn, fromDate, toDate);
            List<SensitiveActorRow> sensitiveActors = findSensitiveActors(conn, fromDate, toDate);

            return new AdminDashboardView(
                    fromDate,
                    toDate,
                    totalRevenue,
                    checkInCount,
                    (int) dayPass[0],
                    dayPass[1],
                    revenueByPackage,
                    ptCommissions,
                    loginActions.getOrDefault("SignIn", 0),
                    loginActions.getOrDefault("SignInFailed", 0),
                    loginActions.getOrDefault("AccountLocked", 0),
                    lockedAccountCount,
                    accountAuditChangeCount,
                    sensitiveActions,
                    sensitiveActors
            );
        }
    }

    /** Đếm tổng lượt check-in trong khoảng {@code [fromDate, toDate + 1)}. */
    private int countCheckIns(Connection conn, LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS Total
                FROM CheckIns
                WHERE CheckedInAt >= ?
                  AND CheckedInAt < DATEADD(DAY, 1, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDateRangeParams(stmt, fromDate, toDate);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("Total") : 0;
            }
        }
    }

    /** Tính tổng doanh thu thực thu (gồm VAT) từ giao dịch Success của đơn chưa bị hủy (DBR-56). */
    private long sumRevenue(Connection conn, LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(CAST(t.Amount AS BIGINT)), 0) AS TotalRevenue
                FROM Transactions t
                JOIN Orders o
                    ON t.OrderId = o.OrderId
                WHERE t.Status = 'Success'
                  AND o.Status <> 'Cancelled'
                  AND t.CompletedAt >= ?
                  AND t.CompletedAt < DATEADD(DAY, 1, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDateRangeParams(stmt, fromDate, toDate);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong("TotalRevenue") : 0L;
            }
        }
    }

    /** Tổng hợp số giao dịch, doanh thu và tiền VAT theo từng tên gói dịch vụ (DBR-57). */
    private List<PackageRevenueRow> findRevenueByPackage(Connection conn, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT
                    o.PackageNameSnapshot AS PackageName,
                    COUNT(t.TransactionId) AS TransactionCount,
                    COALESCE(SUM(CAST(t.Amount AS BIGINT)), 0) AS Revenue,
                    COALESCE(SUM(CAST(o.VatAmount AS BIGINT)), 0) AS VatAmount
                FROM Transactions t
                JOIN Orders o
                    ON t.OrderId = o.OrderId
                WHERE t.Status = 'Success'
                  AND o.Status <> 'Cancelled'
                  AND t.CompletedAt >= ?
                  AND t.CompletedAt < DATEADD(DAY, 1, ?)
                GROUP BY o.PackageNameSnapshot
                ORDER BY Revenue DESC, o.PackageNameSnapshot
                """;
        List<PackageRevenueRow> rows = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDateRangeParams(stmt, fromDate, toDate);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new PackageRevenueRow(
                            rs.getString("PackageName"),
                            rs.getInt("TransactionCount"),
                            rs.getLong("Revenue"),
                            rs.getLong("VatAmount")
                    ));
                }
            }
        }
        return rows;
    }

    /** Tổng hợp số lượt và doanh thu từ giao dịch vé ngày thành công ({@code PackageType = 'DayPass'}). */
    private long[] getDayPassSummary(Connection conn, LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT
                    COUNT(t.TransactionId) AS TotalCount,
                    COALESCE(SUM(CAST(t.Amount AS BIGINT)), 0) AS Revenue
                FROM Transactions t
                JOIN Orders o
                    ON t.OrderId = o.OrderId
                JOIN Packages p
                    ON o.PackageId = p.PackageId
                WHERE t.Status = 'Success'
                  AND o.Status <> 'Cancelled'
                  AND p.PackageType = 'DayPass'
                  AND t.CompletedAt >= ?
                  AND t.CompletedAt < DATEADD(DAY, 1, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDateRangeParams(stmt, fromDate, toDate);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new long[] { rs.getLong("TotalCount"), rs.getLong("Revenue") };
                }
            }
        }
        return new long[] { 0L, 0L };
    }

    /** Tổng hợp số buổi tính thưởng và tổng hoa hồng theo từng PT (DBR-58, CBR-80..82). */
    private List<PtCommissionRow> findPtCommissionSummary(Connection conn, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT
                    e.EmployeeCode,
                    p.FullName,
                    COUNT(s.SessionId) AS SessionCount,
                    COALESCE(SUM(CAST(s.CommissionAmount AS BIGINT)), 0) AS CommissionAmount
                FROM Sessions s
                JOIN TimeSlots ts
                    ON s.TimeSlotId = ts.TimeSlotId
                JOIN Employees e
                    ON s.PtEmployeeId = e.EmployeeId
                JOIN Persons p
                    ON e.PersonId = p.PersonId
                WHERE ts.SlotDate BETWEEN ? AND ?
                  AND (
                        s.Status IN ('CheckedIn', 'Missed')
                        OR (
                            s.Status = 'Cancelled'
                            AND s.CancelType IN ('ByPt', 'ByCustomer')
                        )
                      )
                GROUP BY e.EmployeeCode, p.FullName
                ORDER BY CommissionAmount DESC, p.FullName
                """;
        List<PtCommissionRow> rows = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDateRangeParams(stmt, fromDate, toDate);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rows.add(new PtCommissionRow(
                            rs.getString("EmployeeCode"),
                            rs.getString("FullName"),
                            rs.getInt("SessionCount"),
                            rs.getLong("CommissionAmount")
                    ));
                }
            }
        }
        return rows;
    }

    /** Đếm 3 nhóm sự kiện đăng nhập ({@code SignIn}, {@code SignInFailed}, {@code AccountLocked}). */
    private Map<String, Integer> countLoginAuditActions(Connection conn, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT Action, COUNT(*) AS Total
                FROM AuditLogs
                WHERE CreatedAt >= ?
                  AND CreatedAt < DATEADD(DAY, 1, ?)
                  AND Action IN ('SignIn', 'SignInFailed', 'AccountLocked')
                GROUP BY Action
                """;
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("SignIn", 0);
        counts.put("SignInFailed", 0);
        counts.put("AccountLocked", 0);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDateRangeParams(stmt, fromDate, toDate);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString("Action"), rs.getInt("Total"));
                }
            }
        }
        return counts;
    }

    /** Đếm số tài khoản khác nhau bị khóa tạm hoặc bị Admin khóa trong kỳ. */
    private int countLockedAccounts(Connection conn, LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT COUNT(DISTINCT COALESCE(EntityId, AccountId)) AS Total
                FROM AuditLogs
                WHERE CreatedAt >= ?
                  AND CreatedAt < DATEADD(DAY, 1, ?)
                  AND (
                        Action = 'AccountLocked'
                        OR (
                            EntityName IN ('Account', 'Accounts')
                            AND FieldChanged = 'Status'
                            AND NewValue = 'Locked'
                        )
                      )
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDateRangeParams(stmt, fromDate, toDate);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("Total") : 0;
            }
        }
    }

    /** Đếm số bản ghi AuditLogs tác động vào dữ liệu tài khoản (loại trừ log đăng nhập thường). */
    private int countAccountAuditChanges(Connection conn, LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS Total
                FROM AuditLogs
                WHERE CreatedAt >= ?
                  AND CreatedAt < DATEADD(DAY, 1, ?)
                  AND EntityName IN ('Account', 'Accounts')
                  AND Action NOT IN ('SignIn', 'SignInFailed')
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDateRangeParams(stmt, fromDate, toDate);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("Total") : 0;
            }
        }
    }

    /** Thống kê và gộp số lần thực hiện theo từng loại thao tác nhạy cảm (GBR-07). */
    private List<SensitiveActionRow> findSensitiveActions(Connection conn, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT Action, EntityName, COUNT(*) AS Total
                FROM AuditLogs
                WHERE CreatedAt >= ?
                  AND CreatedAt < DATEADD(DAY, 1, ?)
                  AND %s
                GROUP BY Action, EntityName
                """.formatted(SENSITIVE_AUDIT_CONDITION);

        Map<String, Integer> mergedByLabel = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDateRangeParams(stmt, fromDate, toDate);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String actionLabel = resolveActionLabel(
                            rs.getString("Action"),
                            rs.getString("EntityName")
                    );
                    mergedByLabel.merge(actionLabel, rs.getInt("Total"), Integer::sum);
                }
            }
        }

        List<SensitiveActionRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : mergedByLabel.entrySet()) {
            rows.add(new SensitiveActionRow(entry.getKey(), entry.getValue()));
        }
        rows.sort(Comparator.comparingInt(SensitiveActionRow::getTotal).reversed()
                .thenComparing(SensitiveActionRow::getActionLabel));
        return rows;
    }

    /**
     * Thống kê người thực hiện thao tác nhạy cảm dựa trên {@code ActingRole} (CBR-112).
     *
     * <p>Dùng subquery {@code TOP 1} khi fallback về {@code Employees} để không bao giờ nhân đôi dòng
     * với nhân viên có nhiều lịch sử công tác (DBR-65).</p>
     */
    private List<SensitiveActorRow> findSensitiveActors(Connection conn, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT
                    src.AccountId,
                    src.ActorName,
                    src.RawRole,
                    COUNT(*) AS Total
                FROM (
                    SELECT
                        al.AccountId,
                        COALESCE(p.FullName, N'Hệ thống') AS ActorName,
                        COALESCE(
                            al.ActingRole,
                            (
                                SELECT TOP 1 e.Position
                                FROM Employees e
                                WHERE e.PersonId = p.PersonId
                                  AND e.Status = 'Working'
                                ORDER BY e.EmployeeId DESC
                            ),
                            (
                                SELECT TOP 1 ep.Position
                                FROM Employees ep
                                WHERE ep.PersonId = p.PersonId
                                ORDER BY ep.EmployeeId DESC
                            ),
                            CASE
                                WHEN EXISTS (SELECT 1 FROM Customers c WHERE c.PersonId = p.PersonId) THEN 'Customer'
                                ELSE 'System'
                            END
                        ) AS RawRole
                    FROM AuditLogs al
                    LEFT JOIN Accounts a
                        ON al.AccountId = a.AccountId
                    LEFT JOIN Persons p
                        ON a.PersonId = p.PersonId
                    WHERE al.CreatedAt >= ?
                      AND al.CreatedAt < DATEADD(DAY, 1, ?)
                      AND %s
                ) src
                GROUP BY src.AccountId, src.ActorName, src.RawRole
                """.formatted(SENSITIVE_AUDIT_CONDITION);

        Map<ActorKey, Integer> mergedByActor = new LinkedHashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            setDateRangeParams(stmt, fromDate, toDate);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Integer accountId = rs.getObject("AccountId", Integer.class);
                    String actorName = rs.getString("ActorName");
                    String roleLabel = resolveRoleLabel(rs.getString("RawRole"));
                    ActorKey key = new ActorKey(accountId, actorName, roleLabel);
                    mergedByActor.merge(key, rs.getInt("Total"), Integer::sum);
                }
            }
        }

        List<SensitiveActorRow> rows = new ArrayList<>();
        for (Map.Entry<ActorKey, Integer> entry : mergedByActor.entrySet()) {
            rows.add(new SensitiveActorRow(
                    entry.getKey().actorName(),
                    entry.getKey().roleLabel(),
                    entry.getValue()
            ));
        }
        rows.sort(Comparator.comparingInt(SensitiveActorRow::getTotal).reversed()
                .thenComparing(SensitiveActorRow::getActorName));
        return rows;
    }

    private static void setDateRangeParams(PreparedStatement stmt, LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        stmt.setDate(1, Date.valueOf(fromDate));
        stmt.setDate(2, Date.valueOf(toDate));
    }

    /**
     * Quy đổi các mã thao tác kỹ thuật còn sót trong DB sang nhãn tiếng Việt đồng nhất (GBR-01).
     */
    static String resolveActionLabel(String rawAction, String entityName) {
        if (rawAction == null || rawAction.isBlank()) {
            return "Thao tác khác";
        }
        String trimmed = rawAction.trim();
        if ("CREATE".equals(trimmed) && ("Packages".equals(entityName) || "Package".equals(entityName))) {
            return "Tạo mới gói dịch vụ";
        }
        if ("UPDATE".equals(trimmed) && ("Packages".equals(entityName) || "Package".equals(entityName))) {
            return "Cập nhật thông tin gói dịch vụ";
        }
        String mapped = ACTION_LABELS.getOrDefault(trimmed, trimmed);
        return mapped.replaceFirst("\\s*\\(JOB-[^)]*\\)$", "").trim();
    }

    /**
     * Quy đổi mã vai trò trong DB sang tên hiển thị tiếng Việt theo {@link Role} (GBR-01).
     */
    static String resolveRoleLabel(String rawRole) {
        if (rawRole == null || rawRole.isBlank() || "System".equalsIgnoreCase(rawRole.trim())) {
            return "Hệ thống";
        }
        Role role = Role.fromDbValue(rawRole.trim());
        return role != null ? role.getDisplayName() : rawRole.trim();
    }

    private record ActorKey(Integer accountId, String actorName, String roleLabel) {
    }
}
