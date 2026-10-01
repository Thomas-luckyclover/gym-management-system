package com.gms.dao;

import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object cho báo cáo tổng hợp của Manager và Admin.
 * Chỉ chứa truy vấn SQL, không chứa logic nghiệp vụ hoặc xử lý hiển thị.
 */
public class ReportDAO {

    /**
     * Đếm tổng lượt check-in trong khoảng ngày được chọn.
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return tổng số lượt check-in
     * @throws SQLException khi truy vấn database lỗi
     */
    public int countCheckIns(LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS Total
                FROM CheckIns
                WHERE CheckedInAt >= ?
                  AND CheckedInAt < DATEADD(DAY, 1, ?)
                """;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fromDate));
            stmt.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Total");
                }
            }
        }

        return 0;
    }

    /**
     * Tính tổng doanh thu từ giao dịch Success của đơn chưa bị hủy.
     * Doanh thu được lọc theo thời điểm giao dịch hoàn tất.
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return tổng doanh thu VND
     * @throws SQLException khi truy vấn database lỗi
     */
    public long sumRevenue(LocalDate fromDate, LocalDate toDate) throws SQLException {
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

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fromDate));
            stmt.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("TotalRevenue");
                }
            }
        }

        return 0L;
    }

    /**
     * Lấy doanh thu theo từng gói, gồm tổng doanh thu và phần VAT đã chốt trên đơn.
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return danh sách doanh thu theo gói
     * @throws SQLException khi truy vấn database lỗi
     */
    public List<Map<String, Object>> findRevenueByPackage(LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT
                    o.PackageNameSnapshot,
                    p.PackageType,
                    COUNT(t.TransactionId) AS TransactionCount,
                    SUM(CAST(t.Amount AS BIGINT)) AS Revenue,
                    SUM(CAST(o.VatAmount AS BIGINT)) AS VatAmount
                FROM Transactions t
                JOIN Orders o
                    ON t.OrderId = o.OrderId
                JOIN Packages p
                    ON o.PackageId = p.PackageId
                WHERE t.Status = 'Success'
                  AND o.Status <> 'Cancelled'
                  AND t.CompletedAt >= ?
                  AND t.CompletedAt < DATEADD(DAY, 1, ?)
                GROUP BY o.PackageNameSnapshot, p.PackageType
                ORDER BY Revenue DESC, o.PackageNameSnapshot
                """;

        List<Map<String, Object>> revenueByPackage = new ArrayList<>();

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fromDate));
            stmt.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("packageName", rs.getString("PackageNameSnapshot"));
                    row.put("packageType", rs.getString("PackageType"));
                    row.put("transactionCount", rs.getInt("TransactionCount"));
                    row.put("revenue", rs.getLong("Revenue"));
                    row.put("vatAmount", rs.getLong("VatAmount"));
                    revenueByPackage.add(row);
                }
            }
        }

        return revenueByPackage;
    }

    /**
     * Tổng hợp số lượt và doanh thu vé ngày trong khoảng báo cáo.
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return map gồm count, revenue và vatAmount
     * @throws SQLException khi truy vấn database lỗi
     */
    public Map<String, Object> getDayPassSummary(LocalDate fromDate, LocalDate toDate)
            throws SQLException {
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

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", 0);
        summary.put("revenue", 0L);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fromDate));
            stmt.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    summary.put("count", rs.getInt("TotalCount"));
                    summary.put("revenue", rs.getLong("Revenue"));
                }
            }
        }

        return summary;
    }

    /**
     * Tổng hợp hoa hồng theo từng PT theo ngày diễn ra buổi tập.
     * Chỉ tính các trạng thái được rule báo cáo cho phép.
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return danh sách hoa hồng theo PT
     * @throws SQLException khi truy vấn database lỗi
     */
    public List<Map<String, Object>> findPtCommissionSummary(LocalDate fromDate, LocalDate toDate)
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
                        s.Status IN ('Completed', 'Missed')
                        OR (
                            s.Status = 'Cancelled'
                            AND s.ResolutionType IN ('Cancel', 'CustomerRequest')
                        )
                      )
                GROUP BY e.EmployeeCode, p.FullName
                ORDER BY CommissionAmount DESC, p.FullName
                """;

        List<Map<String, Object>> ptCommissions = new ArrayList<>();

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fromDate));
            stmt.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("employeeCode", rs.getString("EmployeeCode"));
                    row.put("fullName", rs.getString("FullName"));
                    row.put("sessionCount", rs.getInt("SessionCount"));
                    row.put("commissionAmount", rs.getLong("CommissionAmount"));
                    ptCommissions.add(row);
                }
            }
        }

        return ptCommissions;
    }

    /**
     * Đếm ba loại hoạt động đăng nhập được chốt cho Admin Dashboard.
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return map Action và số lần xuất hiện
     * @throws SQLException khi truy vấn database lỗi
     */
    public Map<String, Integer> countLoginAuditActions(LocalDate fromDate, LocalDate toDate)
            throws SQLException {
        String sql = """
                SELECT Action, COUNT(*) AS Total
                FROM AuditLogs
                WHERE CreatedAt >= ?
                  AND CreatedAt < DATEADD(DAY, 1, ?)
                  AND Action IN ('SignIn', 'SignInFailed', 'AccountLocked')
                GROUP BY Action
                """;

        Map<String, Integer> loginActions = new LinkedHashMap<>();
        loginActions.put("SignIn", 0);
        loginActions.put("SignInFailed", 0);
        loginActions.put("AccountLocked", 0);

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fromDate));
            stmt.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    loginActions.put(rs.getString("Action"), rs.getInt("Total"));
                }
            }
        }

        return loginActions;
    }

    /**
     * Đếm số tài khoản bị khóa trong kỳ từ lịch sử Audit Log.
     * Bao gồm khóa tạm do đăng nhập sai và khóa trực tiếp bởi Admin.
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return số tài khoản khác nhau bị khóa trong kỳ
     * @throws SQLException khi truy vấn database lỗi
     */
    public int countLockedAccounts(LocalDate fromDate, LocalDate toDate) throws SQLException {
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

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fromDate));
            stmt.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Total");
                }
            }
        }

        return 0;
    }

    /**
     * Đếm Audit Log tác động tới Account trong kỳ.
     * Chấp nhận cả tên EntityName hiện hành "Account" và dữ liệu seed cũ "Accounts".
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return số bản ghi Audit Log của Account
     * @throws SQLException khi truy vấn database lỗi
     */
    public int countAccountAuditChanges(LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS Total
                FROM AuditLogs
                WHERE CreatedAt >= ?
                  AND CreatedAt < DATEADD(DAY, 1, ?)
                  AND EntityName IN ('Account', 'Accounts')
                """;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fromDate));
            stmt.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Total");
                }
            }
        }

        return 0;
    }

    /**
     * Nhóm các thao tác nhạy cảm theo Action theo GBR-07.
     * Chỉ gồm: đổi giá, thao tác tài khoản, xóa, duyệt và từ chối.
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return map Action và số lần xuất hiện
     * @throws SQLException khi truy vấn database lỗi
     */
    public Map<String, Integer> countSensitiveAuditLogsByAction(
            LocalDate fromDate,
            LocalDate toDate
    ) throws SQLException {
        String sql = """
                SELECT Action, COUNT(*) AS Total
                FROM AuditLogs
                WHERE CreatedAt >= ?
                  AND CreatedAt < DATEADD(DAY, 1, ?)
                  AND (
                        FieldChanged = 'Price'
                        OR EntityName IN ('Account', 'Accounts')
                        OR (
                            EntityName IN ('Person', 'Persons')
                            AND FieldChanged IN ('Email', 'Phone')
                        )
                        OR Action LIKE N'%tài khoản%'
                        OR Action LIKE N'%xóa%'
                        OR Action LIKE N'%duyệt%'
                        OR Action LIKE N'%từ chối%'
                      )
                GROUP BY Action
                ORDER BY Total DESC, Action
                """;

        Map<String, Integer> auditLogsByAction = new LinkedHashMap<>();

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fromDate));
            stmt.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    auditLogsByAction.put(rs.getString("Action"), rs.getInt("Total"));
                }
            }
        }

        return auditLogsByAction;
    }

    /**
     * Nhóm cùng tập thao tác nhạy cảm GBR-07 theo người thực hiện.
     * AccountId null được hiển thị là Hệ thống.
     *
     * @param fromDate ngày bắt đầu, tính cả ngày này
     * @param toDate ngày kết thúc, tính cả ngày này
     * @return danh sách người thực hiện và số thao tác nhạy cảm
     * @throws SQLException khi truy vấn database lỗi
     */
    public List<Map<String, Object>> findSensitiveAuditActors(
            LocalDate fromDate,
            LocalDate toDate
    ) throws SQLException {
        String sql = """
                SELECT
                    al.AccountId,
                    COALESCE(p.FullName, N'Hệ thống') AS ActorName,
                    COALESCE(
                        e.Position,
                        CASE WHEN c.CustomerId IS NOT NULL THEN 'Customer' ELSE 'System' END
                    ) AS ActorRole,
                    COUNT(*) AS Total
                FROM AuditLogs al
                LEFT JOIN Accounts a
                    ON al.AccountId = a.AccountId
                LEFT JOIN Persons p
                    ON a.PersonId = p.PersonId
                LEFT JOIN Employees e
                    ON p.PersonId = e.PersonId
                LEFT JOIN Customers c
                    ON p.PersonId = c.PersonId
                WHERE al.CreatedAt >= ?
                  AND al.CreatedAt < DATEADD(DAY, 1, ?)
                  AND (
                        al.FieldChanged = 'Price'
                        OR al.EntityName IN ('Account', 'Accounts')
                        OR (
                            al.EntityName IN ('Person', 'Persons')
                            AND al.FieldChanged IN ('Email', 'Phone')
                        )
                        OR al.Action LIKE N'%tài khoản%'
                        OR al.Action LIKE N'%xóa%'
                        OR al.Action LIKE N'%duyệt%'
                        OR al.Action LIKE N'%từ chối%'
                      )
                GROUP BY al.AccountId, p.FullName, e.Position, c.CustomerId
                ORDER BY Total DESC, ActorName
                """;

        List<Map<String, Object>> auditActors = new ArrayList<>();

        try (Connection conn = DBContext.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDate(1, Date.valueOf(fromDate));
            stmt.setDate(2, Date.valueOf(toDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Object accountId = rs.getObject("AccountId");
                    row.put("accountId", accountId);
                    row.put("actorName", rs.getString("ActorName"));
                    row.put("actorRole", rs.getString("ActorRole"));
                    row.put("total", rs.getInt("Total"));
                    auditActors.add(row);
                }
            }
        }

        return auditActors;
    }
}