package com.gms.dao;

import com.gms.dto.PtSaleCustomer;
import com.gms.dto.PtScheduleItem;
import com.gms.util.TimeRange;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO MÀN BÁN GÓI PT TẠI QUẦY (/receptionist/pt-sales). Mọi hàm nhận connection của Service.
 *
 * PHẦN 1. ĐỌC — PtSaleService.prepare (xem trước) và PtSaleService.sell (kiểm tra lại) đều gọi
 *   findReceptionist   tài khoản đăng nhập → mã lễ tân
 *   findCustomer       khách theo mã KH-xxxx hoặc SĐT, kèm gói thành viên hôm nay
 *   findPtPackages / findPackage   gói PT đang bán
 *   findWorkingPts / findPt        PT đang làm việc
 *   findPtSlots        mọi khung của PT trong kỳ (lock = true khi bán: không ai chen vào)
 *   findCustomerBusy   giờ các buổi Scheduled khác của khách (+ người tập cùng)
 *
 * PHẦN 2. GHI — chỉ trong transaction của PtSaleService.sell
 *   lockPt, findOrOpenShift, nextOrderCode, insertOrder, insertTransaction, insertSubscription,
 *   bookFreeSlot, insertBookedSlot, insertSession, findCustomerAccounts
 */
public class PtSaleDAO {

    /** Một gói PT đang bán (bảng Packages). */
    public static class PackageRow {
        public int packageId;
        public String name;
        public String description;
        public String benefits;
        public String type;          // PTSingle | PTCouple
        public int months;           // DurationValue (đơn vị Month)
        public long price;           // đã gồm VAT
    }

    /** Một PT đang làm việc (bảng Employees + Persons). */
    public static class PtRow {
        public int employeeId;
        public int personId;
        public String fullName;
        public int commissionRate;
    }


    // ================================================================================================================
    // PHẦN 1. ĐỌC
    // ================================================================================================================

    /** Tài khoản → [EmployeeId, PersonId] của Lễ tân đang làm việc; không phải → null. */
    public int[] findReceptionist(Connection connection, int accountId) throws SQLException {
        String sql = """
                SELECT e.EmployeeId, e.PersonId
                FROM Accounts a
                JOIN Employees e ON e.PersonId = a.PersonId
                WHERE a.AccountId = ? AND a.Status = 'Active'
                  AND e.Position = 'Receptionist' AND e.Status = 'Working'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? new int[]{rs.getInt("EmployeeId"), rs.getInt("PersonId")} : null;
            }
        }
    }

    /**
     * Khách theo mã KH-xxxx (không phân biệt hoa thường) hoặc số điện thoại. Hồ sơ đã xóa không tìm thấy.
     * Kèm 2 cột tính sẵn cho NGÀY today:
     *   MembershipEnd  ngày cuối của gói thành viên Active/Frozen phủ hôm nay (không có → NULL)
     *   IsFrozen       hôm nay đang trong đợt bảo lưu (CBR-59: bảo lưu thì không tính là dùng được)
     */
    public PtSaleCustomer findCustomer(Connection connection, String keyword, LocalDate today) throws SQLException {
        String sql = """
                SELECT TOP 1 c.CustomerId, c.PersonId, c.CustomerCode, c.CustomerType,
                       p.FullName, p.Phone, p.DateOfBirth,
                       (SELECT MAX(m.EndDate) FROM Subscriptions m
                        WHERE m.CustomerId = c.CustomerId AND m.SubscriptionType = 'Membership'
                          AND m.Status IN ('Active', 'Frozen') AND ? BETWEEN m.StartDate AND m.EndDate) AS MembershipEnd,
                       CASE WHEN EXISTS (SELECT 1 FROM Subscriptions m
                                         JOIN FreezeRecords f ON f.SubscriptionId = m.SubscriptionId
                                         WHERE m.CustomerId = c.CustomerId AND m.SubscriptionType = 'Membership'
                                           AND ? BETWEEN f.StartDate AND COALESCE(f.ActualEndDate, f.PlannedEndDate))
                            THEN 1 ELSE 0 END AS IsFrozen
                FROM Customers c
                JOIN Persons p ON p.PersonId = c.PersonId
                WHERE c.DeletedAt IS NULL
                  AND (UPPER(c.CustomerCode) = UPPER(?) OR p.Phone = ?)
                ORDER BY c.CustomerId
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(today));
            statement.setDate(2, Date.valueOf(today));
            statement.setString(3, keyword);
            statement.setString(4, keyword);

            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                PtSaleCustomer row = new PtSaleCustomer();
                row.setCustomerId(rs.getInt("CustomerId"));
                row.setPersonId(rs.getInt("PersonId"));
                row.setCustomerCode(rs.getString("CustomerCode"));
                row.setCustomerType(rs.getString("CustomerType"));
                row.setFullName(rs.getString("FullName"));
                row.setPhone(rs.getString("Phone"));
                row.setDateOfBirth(rs.getDate("DateOfBirth").toLocalDate());
                Date end = rs.getDate("MembershipEnd");
                row.setMembershipEnd(end == null ? null : end.toLocalDate());
                row.setFrozen(rs.getBoolean("IsFrozen"));
                return row;
            }
        }
    }

    /** Các gói PT đang bán (gói 1 người + gói đôi), rẻ trước. */
    public List<PackageRow> findPtPackages(Connection connection) throws SQLException {
        String sql = """
                SELECT PackageId, PackageName, Description, Benefits, PackageType, DurationValue, Price
                FROM Packages
                WHERE IsActive = 1 AND PackageType IN ('PTSingle', 'PTCouple')
                ORDER BY PackageType DESC, DurationValue, Price
                """;
        List<PackageRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(readPackage(rs));
            }
        }
        return rows;
    }

    /** 1 gói PT đang bán theo mã; không có / đã ngừng bán / không phải gói PT → null. */
    public PackageRow findPackage(Connection connection, int packageId) throws SQLException {
        String sql = """
                SELECT PackageId, PackageName, Description, Benefits, PackageType, DurationValue, Price
                FROM Packages
                WHERE PackageId = ? AND IsActive = 1 AND PackageType IN ('PTSingle', 'PTCouple')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, packageId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? readPackage(rs) : null;
            }
        }
    }

    private PackageRow readPackage(ResultSet rs) throws SQLException {
        PackageRow row = new PackageRow();
        row.packageId = rs.getInt("PackageId");
        row.name = rs.getString("PackageName");
        row.description = rs.getString("Description");
        row.benefits = rs.getString("Benefits");
        row.type = rs.getString("PackageType");
        row.months = rs.getInt("DurationValue");
        row.price = rs.getLong("Price");
        return row;
    }

    /** PT đang làm việc VÀ có tài khoản Active (PT bị khóa tài khoản không xem được lịch để dạy). */
    public List<PtRow> findWorkingPts(Connection connection) throws SQLException {
        List<PtRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(PT_SQL + " ORDER BY p.FullName");
                ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(readPt(rs));
            }
        }
        return rows;
    }

    /** 1 PT theo mã, cùng điều kiện với findWorkingPts; không hợp lệ → null. */
    public PtRow findPt(Connection connection, int employeeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PT_SQL + " AND e.EmployeeId = ?")) {
            statement.setInt(1, employeeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? readPt(rs) : null;
            }
        }
    }

    private static final String PT_SQL = """
            SELECT e.EmployeeId, e.PersonId, p.FullName, e.CommissionRate
            FROM Employees e
            JOIN Persons p   ON p.PersonId = e.PersonId
            JOIN Accounts a  ON a.PersonId = e.PersonId AND a.Status = 'Active'
            WHERE e.Position = 'PT' AND e.Status = 'Working'
            """;

    private PtRow readPt(ResultSet rs) throws SQLException {
        PtRow row = new PtRow();
        row.employeeId = rs.getInt("EmployeeId");
        row.personId = rs.getInt("PersonId");
        row.fullName = rs.getString("FullName");
        row.commissionRate = rs.getInt("CommissionRate");
        return row;
    }

    /**
     * MỌI khung của PT từ fromDate đến toDate, kể cả Withdrawn. kind = Status trong DB, nhưng khung đã có
     * buổi Scheduled luôn coi là 'Booked'. lock = true (lúc bán): UPDLOCK + HOLDLOCK giữ cả khoảng ngày,
     * không ai thêm / giữ / đặt khung của PT này cho tới khi bán xong.
     */
    public List<PtScheduleItem> findPtSlots(Connection connection, int ptId, LocalDate fromDate, LocalDate toDate,
            boolean lock) throws SQLException {
        String sql = """
                SELECT t.TimeSlotId, t.SlotDate, t.StartTime, t.EndTime,
                       CASE WHEN EXISTS (SELECT 1 FROM Sessions s WHERE s.TimeSlotId = t.TimeSlotId
                                         AND s.Status = 'Scheduled') THEN 'Booked' ELSE t.Status END AS Status
                FROM TimeSlots t %s
                WHERE t.PtEmployeeId = ? AND t.SlotDate BETWEEN ? AND ?
                ORDER BY t.SlotDate, t.StartTime
                """.formatted(lock ? "WITH (UPDLOCK, HOLDLOCK)" : "");

        List<PtScheduleItem> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptId);
            statement.setDate(2, Date.valueOf(fromDate));
            statement.setDate(3, Date.valueOf(toDate));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    PtScheduleItem row = new PtScheduleItem();
                    row.setTimeSlotId(rs.getInt("TimeSlotId"));
                    row.setSlotDate(rs.getDate("SlotDate").toLocalDate());
                    row.setStartTime(rs.getTime("StartTime").toLocalTime());
                    row.setEndTime(rs.getTime("EndTime").toLocalTime());
                    row.setKind(rs.getString("Status"));   // Free | Held | Booked | Withdrawn
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * Giờ các buổi Scheduled của khách (đứng tên hoặc tập cùng trong gói đôi), với BẤT KỲ PT nào, trong kỳ.
     * Gói 1 người: truyền partnerId = customerId.
     */
    public List<TimeRange> findCustomerBusy(Connection connection, int customerId, int partnerId,
            LocalDate fromDate, LocalDate toDate) throws SQLException {
        String sql = """
                SELECT t.SlotDate, t.StartTime, t.EndTime
                FROM Sessions s
                JOIN TimeSlots t       ON t.TimeSlotId = s.TimeSlotId
                JOIN Subscriptions sub ON sub.SubscriptionId = s.SubscriptionId
                JOIN Orders o          ON o.OrderId = sub.OrderId
                WHERE s.Status = 'Scheduled' AND t.SlotDate BETWEEN ? AND ?
                  AND (sub.CustomerId IN (?, ?)
                       OR (sub.SubscriptionType = 'PTCouple' AND o.PartnerCustomerId IN (?, ?)))
                """;
        List<TimeRange> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(fromDate));
            statement.setDate(2, Date.valueOf(toDate));
            statement.setInt(3, customerId);
            statement.setInt(4, partnerId);
            statement.setInt(5, customerId);
            statement.setInt(6, partnerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new TimeRange(rs.getDate("SlotDate").toLocalDate(),
                            rs.getTime("StartTime").toLocalTime(), rs.getTime("EndTime").toLocalTime()));
                }
            }
        }
        return rows;
    }


    // ================================================================================================================
    // PHẦN 2. GHI — trong transaction, KHÔNG tự commit
    // ================================================================================================================

    /** Khóa dòng PT: hai lễ tân bán cùng PT, hoặc PT đang Công bố / xử lý buổi, phải xếp hàng. */
    public void lockPt(Connection connection, int ptId) throws SQLException {
        String sql = "SELECT EmployeeId FROM Employees WITH (UPDLOCK, HOLDLOCK) WHERE EmployeeId = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
            }
        }
    }

    /**
     * Ca đang dùng để ghi đơn tại quầy (đơn Counter bắt buộc có ShiftId — CK_Orders_ChannelFields).
     *   Chưa có dòng ca      → tạo ca Open do lễ tân này mở.
     *   Có nhưng NotOpened   → mở ca (Open) bằng lễ tân này.
     *   Open                 → dùng luôn.
     *   Closed               → null: Service báo "Ca đã chốt".
     */
    public Integer findOrOpenShift(Connection connection, LocalDate workDate, String shiftType, int employeeId,
            LocalDateTime now) throws SQLException {
        String findSql = """
                SELECT ShiftId, Status FROM Shifts WITH (UPDLOCK, HOLDLOCK)
                WHERE WorkDate = ? AND ShiftType = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(findSql)) {
            statement.setDate(1, Date.valueOf(workDate));
            statement.setString(2, shiftType);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    int shiftId = rs.getInt("ShiftId");
                    String status = rs.getString("Status");
                    if ("Closed".equals(status)) {
                        return null;
                    }
                    if ("NotOpened".equals(status)) {
                        openShift(connection, shiftId, employeeId, now);
                    }
                    return shiftId;
                }
            }
        }

        String insertSql = """
                INSERT INTO Shifts (WorkDate, ShiftType, OpenedByEmployeeId, Status, OpenedAt)
                VALUES (?, ?, ?, 'Open', ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setDate(1, Date.valueOf(workDate));
            statement.setString(2, shiftType);
            statement.setInt(3, employeeId);
            statement.setTimestamp(4, Timestamp.valueOf(now.withNano(0)));
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    private void openShift(Connection connection, int shiftId, int employeeId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE Shifts SET Status = 'Open', OpenedByEmployeeId = ?, OpenedAt = ? WHERE ShiftId = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, employeeId);
            statement.setTimestamp(2, Timestamp.valueOf(now.withNano(0)));
            statement.setInt(3, shiftId);
            statement.executeUpdate();
        }
    }

    /**
     * Mã đơn kế tiếp DH-yyyy-nnnn của năm. UPDLOCK + HOLDLOCK: hai đơn tạo cùng lúc không lấy trùng số
     * (người sau chờ người trước commit).
     */
    public String nextOrderCode(Connection connection, int year) throws SQLException {
        String prefix = "DH-" + year + "-";
        String sql = "SELECT MAX(OrderCode) FROM Orders WITH (UPDLOCK, HOLDLOCK) WHERE OrderCode LIKE ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, prefix + "%");
            try (ResultSet rs = statement.executeQuery()) {
                int next = 1;
                if (rs.next() && rs.getString(1) != null) {
                    next = Integer.parseInt(rs.getString(1).substring(prefix.length())) + 1;
                }
                if (next > 9999) {
                    throw new SQLException("Hết dải mã đơn của năm " + year);
                }
                return prefix + String.format("%04d", next);
            }
        }
    }

    /**
     * Đơn tại quầy ĐÃ THANH TOÁN ngay (bán gói PT thu tiền tại chỗ). Không voucher (CBR-76), không hạn online.
     * Chép tên, mô tả, quyền lợi, giá của gói vào đơn (DBR-50).
     */
    public int insertOrder(Connection connection, String orderCode, int holderId, Integer partnerId, PackageRow pkg,
            int receptionistId, int shiftId, int vatRate, long vatAmount, LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO Orders (OrderCode, HolderCustomerId, PartnerCustomerId, PackageId, VoucherId,
                                    CreatedByEmployeeId, ShiftId, Channel, PackageNameSnapshot,
                                    PackageDescriptionSnapshot, PackageBenefitsSnapshot, UnitPriceSnapshot,
                                    DiscountAmount, VatRateSnapshot, VatAmount, Status, PaymentDueAt, CreatedAt, PaidAt)
                VALUES (?, ?, ?, ?, NULL, ?, ?, 'Counter', ?, ?, ?, ?, 0, ?, ?, 'Paid', NULL, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Timestamp at = Timestamp.valueOf(now.withNano(0));
            statement.setString(1, orderCode);
            statement.setInt(2, holderId);
            if (partnerId == null) {
                statement.setNull(3, Types.INTEGER);
            } else {
                statement.setInt(3, partnerId);
            }
            statement.setInt(4, pkg.packageId);
            statement.setInt(5, receptionistId);
            statement.setInt(6, shiftId);
            statement.setNString(7, pkg.name);
            statement.setNString(8, pkg.description);
            statement.setNString(9, pkg.benefits);
            statement.setLong(10, pkg.price);
            statement.setInt(11, vatRate);
            statement.setLong(12, vatAmount);
            statement.setTimestamp(13, at);
            statement.setTimestamp(14, at);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    /** Giao dịch Success: tiền mặt hoặc chuyển khoản, lễ tân là người nhận (CBR-77). */
    public void insertTransaction(Connection connection, int orderId, long amount, String method, String reference,
            int receptionistId, LocalDateTime now) throws SQLException {
        String sql = """
                INSERT INTO Transactions (OrderId, Amount, PaymentMethod, GatewayReference, ReceivedByEmployeeId,
                                          Status, CreatedAt, CompletedAt)
                VALUES (?, ?, ?, ?, ?, 'Success', ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp at = Timestamp.valueOf(now.withNano(0));
            statement.setInt(1, orderId);
            statement.setLong(2, amount);
            statement.setString(3, method);
            if (reference == null) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, reference);
            }
            statement.setInt(5, receptionistId);
            statement.setTimestamp(6, at);
            statement.setTimestamp(7, at);
            statement.executeUpdate();
        }
    }

    /** Gói PT của khách: khung giờ cố định, số buổi đã sinh, trần đổi buổi (P-08 × số tháng, chốt lúc bán). */
    public int insertSubscription(Connection connection, int orderId, int customerId, String type, int ptId,
            LocalDate start, LocalDate end, LocalTime fixedTime, int sessionCount, int maxSwaps) throws SQLException {
        String sql = """
                INSERT INTO Subscriptions (OrderId, CustomerId, SubscriptionType, PtEmployeeId, StartDate, EndDate,
                                           FixedStartTime, GeneratedSessionCount, MaxSwapsSnapshot, Status)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS time), ?, ?, 'Active')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, orderId);
            statement.setInt(2, customerId);
            statement.setString(3, type);
            statement.setInt(4, ptId);
            statement.setDate(5, Date.valueOf(start));
            statement.setDate(6, Date.valueOf(end));
            statement.setString(7, fixedTime.toString());
            statement.setInt(8, sessionCount);
            statement.setInt(9, maxSwaps);
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    /** Khung PT đã công bố: Free → Booked. false = khung vừa bị người khác lấy. */
    public boolean bookFreeSlot(Connection connection, int slotId, int ptId) throws SQLException {
        String sql = """
                UPDATE TimeSlots SET Status = 'Booked'
                WHERE TimeSlotId = ? AND PtEmployeeId = ? AND Status = 'Free' AND HeldByOrderId IS NULL
                  AND NOT EXISTS (SELECT 1 FROM Sessions s WHERE s.TimeSlotId = TimeSlots.TimeSlotId AND s.Status = 'Scheduled')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, slotId);
            statement.setInt(2, ptId);
            return statement.executeUpdate() == 1;
        }
    }

    /** Ngày PT chưa có khung giờ đó: tạo khung mới ở trạng thái Booked, trả mã khung. */
    public int insertBookedSlot(Connection connection, int ptId, LocalDate date, LocalTime start, LocalTime end)
            throws SQLException {
        String sql = """
                INSERT INTO TimeSlots (PtEmployeeId, SlotDate, StartTime, EndTime, Status)
                VALUES (?, ?, CAST(? AS time), CAST(? AS time), 'Booked')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, ptId);
            statement.setDate(2, Date.valueOf(date));
            statement.setString(3, start.toString());
            statement.setString(4, end.toString());
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    /** 1 buổi Scheduled: giá trị buổi (DBR-59) + hoa hồng theo tỷ lệ PT (DBR-58). */
    public void insertSession(Connection connection, int subscriptionId, int slotId, int ptId, long value,
            long commission) throws SQLException {
        String sql = """
                INSERT INTO Sessions (SubscriptionId, TimeSlotId, PtEmployeeId, Status, SessionValue, CommissionAmount)
                VALUES (?, ?, ?, 'Scheduled', ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, subscriptionId);
            statement.setInt(2, slotId);
            statement.setInt(3, ptId);
            statement.setLong(4, value);
            statement.setLong(5, commission);
            statement.executeUpdate();
        }
    }

    /** Tài khoản Active của khách + người tập cùng (để gửi thông báo). Hồ sơ không có tài khoản → bỏ qua. */
    public List<Integer> findCustomerAccounts(Connection connection, int customerId, int partnerId) throws SQLException {
        String sql = """
                SELECT DISTINCT a.AccountId
                FROM Customers c
                JOIN Accounts a ON a.PersonId = c.PersonId AND a.Status = 'Active'
                WHERE c.CustomerId IN (?, ?)
                """;
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setInt(2, partnerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("AccountId"));
                }
            }
        }
        return ids;
    }

    private int generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.getGeneratedKeys()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("Không nhận được mã vừa tạo");
    }
}
