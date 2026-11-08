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
 * DAO MÀN BÁN GÓI PT TẠI QUẦY (/receptionist/pt-sales). Chỉ PtSaleService gọi.
 *
 * QUY ƯỚC: mọi hàm NHẬN connection của Service, không tự mở / commit / đóng.
 *   → Service quyết định khi nào là 1 transaction (hold, pay, release đều là 1 transaction).
 * KHÓA: hàm có "WITH (UPDLOCK, HOLDLOCK)" giữ dòng tới hết transaction → người thứ 2 phải chờ:
 *   lockPt (dòng PT) · findPtSlots(lock = true) (khung của PT trong kỳ) · nextOrderCode (mã đơn) · findHeldOrder(lock = true).
 *
 * BỐ CỤC: xếp theo HÀM CHÍNH của Service dùng tới (search / prepare → hold → pay → release).
 *   Cuối mỗi hàm ghi: bảng nào, đọc (R) / thêm (C) / sửa (U) / xóa (D).
 */
public class PtSaleDAO {


    // ============================================================================================================
    // KIỂU DỮ LIỆU DAO TRẢ VỀ (Service đọc field trực tiếp: pkg.price, pt.fullName, held.slots …)
    // ============================================================================================================

    /**
     * 1 gói PT đang bán — DAO tạo bằng constructor trong readPackage (findPtPackages, findPackage).
     * Service đọc: loadPackage (ô chọn gói, giá, số tháng), hold (chép vào đơn), pay (số tháng → ngày kết thúc).
     */
    public static class PackageRow {
        public final int packageId;        // Packages.PackageId → value ô chọn gói
        public final String name;          // Packages.PackageName → IN tên gói; chép vào Orders.PackageNameSnapshot
        public final String description;   // Packages.Description → chép vào đơn (không in)
        public final String benefits;      // Packages.Benefits → chép vào đơn (không in)
        public final String type;          // Packages.PackageType: PTSingle | PTCouple → "1 người" / "Gói đôi"
        public final int months;           // Packages.DurationValue (đơn vị Month) → IN "· 2 tháng"
        public final long price;           // Packages.Price, đã gồm VAT → IN "Phải thu"

        public PackageRow(int packageId, String name, String description, String benefits, String type, int months,
                long price) {
            this.packageId = packageId;
            this.name = name;
            this.description = description;
            this.benefits = benefits;
            this.type = type;
            this.months = months;
            this.price = price;
        }
    }

    /**
     * 1 PT đang làm việc — DAO tạo bằng constructor trong readPt (findWorkingPts, findPt).
     * Service đọc: loadPt (ô chọn PT, chặn PT là chính khách), pay (hoa hồng), notifySale (tên PT).
     */
    public static class PtRow {
        public final int employeeId;       // Employees.EmployeeId → value ô chọn PT (?pt=)
        public final int personId;         // Employees.PersonId → so với khách (PT không tự bán cho mình)
        public final String fullName;      // Persons.FullName → IN tên PT, chữ tắt "ĐS"
        public final int commissionRate;   // Employees.CommissionRate (%) → Sessions.CommissionAmount khi thu tiền
        public final String specialty;     // Employees.Specialty (có thể null) → IN chữ nhỏ dưới tên PT
        public final int yearsOfExperience; // Employees.YearsOfExperience (null → 0) → IN chữ nhỏ dưới tên PT

        public PtRow(
                int employeeId,
                int personId,
                String fullName,
                int commissionRate,
                String specialty,
                int yearsOfExperience) {
            this.employeeId = employeeId;
            this.personId = personId;
            this.fullName = fullName;
            this.commissionRate = commissionRate;
            this.specialty = specialty;
            this.yearsOfExperience = yearsOfExperience;
        }
    }

    /** Một đơn đang giữ chỗ (đọc từ Orders + khung Held của đơn). */
    public static class HeldOrder {
        public int orderId;
        public String orderCode;
        public String status;           // Pending | Paid | Cancelled
        public int holderId;
        public Integer partnerId;
        public int packageId;
        public long price;
        public int vatRate;             // Orders.VatRateSnapshot (chỉ findHeldOrder đọc)
        public long vatAmount;          // Orders.VatAmount (chỉ findHeldOrder đọc)
        public LocalDateTime createdAt;
        public LocalDateTime expiresAt; // hạn giữ sớm nhất trong các khung (null = không còn khung nào giữ)
        public int ptId;
        public List<PtScheduleItem> slots = new ArrayList<>();   // khung đang giữ, theo ngày giờ
    }


    // ============================================================================================================
    // PHỤC VỤ HÀM CHÍNH search + prepare (bước Khách) — ĐỌC, không khóa
    //   findReceptionist · searchCustomers · findCustomer (+ readCustomer, MEMBERSHIP_COLUMNS, escapeLike)
    // ============================================================================================================

    /**
     * [search, prepare, hold, pay, release] Accounts + Employees: R 1 dòng.
     * Tài khoản → [EmployeeId, PersonId] của Lễ tân đang làm việc; không phải → null.
     */
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
     * [search; prepare khi tắt JS] Customers + Persons (+ Subscriptions, FreezeRecords): R mọi dòng khớp.
     * Ô tìm khách kiểu Google: mọi khách khớp MỘT trong 3 cách (không phân biệt hoa thường, dấu):
     *   tên chứa các từ gõ theo đúng thứ tự ("mai", "do mai" đều khớp "Đỗ Thị Mai")
     *   ·  SĐT / SĐT giám hộ chứa dãy số  ·  mã KH chứa chữ gõ.
     * Latin1_General_CI_AI (không phải Vietnamese_CI_AI): bản tiếng Việt coi ă, ê, đ… là chữ riêng → "van" không khớp "Văn".
     * Xếp: khớp đúng mã / SĐT trước, rồi tên bắt đầu bằng chữ gõ, rồi theo tên.
     * digits = chỉ phần số trong chữ gõ ("090 123" → "090123"); không có số → không xét SĐT.
     */
    public List<PtSaleCustomer> searchCustomers(Connection connection, String text, String digits, LocalDate today)
            throws SQLException {
        String sql = "SELECT c.CustomerId, c.PersonId, c.CustomerCode, p.FullName, p.Phone, p.DateOfBirth, "
                + MEMBERSHIP_COLUMNS
                + """
                 FROM Customers c JOIN Persons p ON p.PersonId = c.PersonId
                 WHERE c.DeletedAt IS NULL
                   AND (p.FullName COLLATE Latin1_General_CI_AI LIKE ? COLLATE Latin1_General_CI_AI
                        OR c.CustomerCode LIKE ?
                        OR (? <> '' AND (p.Phone LIKE ? OR c.GuardianPhone LIKE ? OR c.GuardianPhone2 LIKE ?)))
                 ORDER BY CASE WHEN c.CustomerCode = ? OR p.Phone = ? THEN 0
                               WHEN p.FullName COLLATE Latin1_General_CI_AI LIKE ? COLLATE Latin1_General_CI_AI THEN 1
                               ELSE 2 END,
                          p.FullName
                """;
        String like = "%" + escapeLike(text) + "%";
        String nameLike = "%" + String.join("%", escapeLike(text).split("\\s+")) + "%";   // "do mai" → %do%mai%
        String digitsLike = "%" + digits + "%";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(today));
            statement.setDate(2, Date.valueOf(today));
            statement.setNString(3, nameLike);
            statement.setString(4, like);
            statement.setString(5, digits);
            statement.setString(6, digitsLike);
            statement.setString(7, digitsLike);
            statement.setString(8, digitsLike);
            statement.setString(9, text.toUpperCase());
            statement.setString(10, digits);
            statement.setNString(11, escapeLike(text) + "%");
            List<PtSaleCustomer> rows = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(readCustomer(rs));
                }
            }
            return rows;
        }
    }

    /** Ký tự đặc biệt của LIKE (% _ [) được hiểu là chữ thường, không phải ký tự đại diện. */
    private static String escapeLike(String text) {
        return text.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
    }

    /**
     * [prepare → loadHolder / loadPartner / loadHeld; pay → notifySale] Customers + Persons: R 1 dòng.
     * 1 khách theo mã CustomerId (ô gợi ý đã chọn → ?customer=12). Hồ sơ đã xóa → null.
     * Kèm MembershipEnd (gói thành viên Active/Frozen phủ hôm nay) và IsFrozen (hôm nay đang bảo lưu).
     */
    public PtSaleCustomer findCustomer(Connection connection, int customerId, LocalDate today) throws SQLException {
        String sql = "SELECT c.CustomerId, c.PersonId, c.CustomerCode, p.FullName, p.Phone, p.DateOfBirth, "
                + MEMBERSHIP_COLUMNS
                + " FROM Customers c JOIN Persons p ON p.PersonId = c.PersonId"
                + " WHERE c.DeletedAt IS NULL AND c.CustomerId = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(today));
            statement.setDate(2, Date.valueOf(today));
            statement.setInt(3, customerId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? readCustomer(rs) : null;
            }
        }
    }

    /** 2 cột tính sẵn cho NGÀY ? (dùng chung findCustomer + searchCustomers). */
    private static final String MEMBERSHIP_COLUMNS = """
            (SELECT MAX(m.EndDate) FROM Subscriptions m
             WHERE m.CustomerId = c.CustomerId AND m.SubscriptionType = 'Membership'
               AND m.Status IN ('Active', 'Frozen') AND ? BETWEEN m.StartDate AND m.EndDate) AS MembershipEnd,
            CASE WHEN EXISTS (SELECT 1 FROM Subscriptions m
                              JOIN FreezeRecords f ON f.SubscriptionId = m.SubscriptionId
                              WHERE m.CustomerId = c.CustomerId AND m.SubscriptionType = 'Membership'
                                AND ? BETWEEN f.StartDate AND COALESCE(f.ActualEndDate, f.PlannedEndDate))
                 THEN 1 ELSE 0 END AS IsFrozen
            """;

    /** HÀM PHỤ (findCustomer, searchCustomers) — 1 dòng kết quả → PtSaleCustomer (constructor). blockReason để Service điền. */
    private PtSaleCustomer readCustomer(ResultSet rs) throws SQLException {
        Date end = rs.getDate("MembershipEnd");
        return new PtSaleCustomer(
                rs.getInt("CustomerId"),
                rs.getInt("PersonId"),
                rs.getString("CustomerCode"),
                rs.getString("FullName"),
                rs.getString("Phone"),
                rs.getDate("DateOfBirth").toLocalDate(),
                end == null ? null : end.toLocalDate(),
                rs.getBoolean("IsFrozen"));
    }


    // ============================================================================================================
    // PHỤC VỤ prepare (bước Gói & PT + Lịch tập) — ĐỌC
    //   findPtPackages · findPackage · findWorkingPts · findPt · findPtSlots (lock khi hold) · findCustomerBusy
    //   findActiveHoldOrders (danh sách "Đơn đang giữ chỗ") · findHeldOrder (trang ?order=; lock khi pay / release)
    // ============================================================================================================

    /**
     * [prepare → loadPackage] Packages: R nhiều dòng.
     * Các gói PT đang bán (gói 1 người + gói đôi), rẻ trước.
     */
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

    /**
     * [prepare, hold, pay] Packages: R 1 dòng.
     * 1 gói PT đang bán theo mã; không có / đã ngừng bán / không phải gói PT → null.
     */
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

    /** HÀM PHỤ (findPtPackages, findPackage) — 1 dòng Packages → PackageRow. */
    private PackageRow readPackage(ResultSet rs) throws SQLException {
        return new PackageRow(
                rs.getInt("PackageId"),
                rs.getString("PackageName"),
                rs.getString("Description"),
                rs.getString("Benefits"),
                rs.getString("PackageType"),
                rs.getInt("DurationValue"),
                rs.getLong("Price"));
    }

    /**
     * [prepare → loadPt] Employees + Persons + Accounts: R nhiều dòng.
     * PT đang làm việc VÀ có tài khoản Active (PT bị khóa tài khoản không xem được lịch để dạy).
     */
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

    /**
     * [prepare → loadPt / loadHeld; pay] Employees + Persons + Accounts: R 1 dòng.
     * 1 PT theo mã, cùng điều kiện với findWorkingPts; không hợp lệ → null.
     */
    public PtRow findPt(Connection connection, int employeeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PT_SQL + " AND e.EmployeeId = ?")) {
            statement.setInt(1, employeeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? readPt(rs) : null;
            }
        }
    }

    private static final String PT_SQL = """
            SELECT e.EmployeeId, e.PersonId, p.FullName, e.CommissionRate, e.Specialty, e.YearsOfExperience
            FROM Employees e
            JOIN Persons p   ON p.PersonId = e.PersonId
            JOIN Accounts a  ON a.PersonId = e.PersonId AND a.Status = 'Active'
            WHERE e.Position = 'PT' AND e.Status = 'Working'
            """;

    /** HÀM PHỤ (findWorkingPts, findPt) — 1 dòng Employees + Persons → PtRow. */
    private PtRow readPt(ResultSet rs) throws SQLException {
        return new PtRow(
                rs.getInt("EmployeeId"),
                rs.getInt("PersonId"),
                rs.getString("FullName"),
                rs.getInt("CommissionRate"),
                rs.getString("Specialty"),
                rs.getInt("YearsOfExperience"));
    }

    /**
     * [prepare / hold → loadPlan] TimeSlots (+ Sessions): R nhiều dòng; lock = true khi hold.
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
     * [prepare / hold → loadPlan] Sessions + TimeSlots + Subscriptions + Orders: R nhiều dòng.
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


    /**
     * [prepare → loadPendingHolds] Orders + TimeSlots: R nhiều dòng.
     * Các đơn tại quầy còn Pending có khung Held CHƯA quá hạn.
     * Dùng cho màn Bán gói PT hiện danh sách "Đơn đang giữ" để lễ tân tìm lại sau khi mất URL ?order= (bấm Back).
     */
    public List<HeldOrder> findActiveHoldOrders(Connection connection, LocalDateTime now) throws SQLException {
        String sql = """
                SELECT o.OrderId, o.OrderCode, o.CreatedAt, MIN(t.HoldExpiresAt) AS ExpiresAt
                FROM Orders o
                JOIN TimeSlots t ON t.HeldByOrderId = o.OrderId AND t.Status = 'Held'
                WHERE o.Status = 'Pending' AND o.Channel = 'Counter' AND t.HoldExpiresAt >= ?
                GROUP BY o.OrderId, o.OrderCode, o.CreatedAt
                ORDER BY o.CreatedAt DESC
                """;
        List<HeldOrder> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now.withNano(0)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    HeldOrder held = new HeldOrder();
                    held.orderId = rs.getInt("OrderId");
                    held.orderCode = rs.getString("OrderCode");
                    held.createdAt = rs.getTimestamp("CreatedAt").toLocalDateTime();
                    held.expiresAt = rs.getTimestamp("ExpiresAt").toLocalDateTime();
                    rows.add(held);
                }
            }
        }
        return rows;
    }

    /**
     * [prepare → loadHeld (lock = false); pay, release, releaseExpired (lock = true)] Orders + Packages + TimeSlots: R.
     * Đơn tại quầy theo mã + mọi khung đang Held cho đơn đó. lock = true (khi thu tiền / hủy): khóa đơn và khung.
     * Không phải đơn tại quầy gói PT → null.
     * Cột Orders đọc: mã, trạng thái, khách, gói, UnitPriceSnapshot, VatRateSnapshot, VatAmount, CreatedAt.
     */
    
    // findHeldOrder chạy 2 câu SQL, Khi lock = true, cả hai câu được thêm WITH (UPDLOCK, HOLDLOCK). Khi false thì chuỗi này rỗng, đọc bình thường.
    // UPDLOCK: khóa dòng đang đọc để chuẩn bị sửa. Người khác vẫn đọc xem được, nhưng không ai khóa hay sửa được dòng đó cho tới khi transaction của mình xong.
    // HOLDLOCK: giữ khóa tới hết transaction thay vì nhả ngay sau khi đọc.

    
    public HeldOrder findHeldOrder(Connection connection, int orderId, boolean lock) throws SQLException {
        String hint = lock ? "WITH (UPDLOCK, HOLDLOCK)" : "";
        
        // Câu 1: đọc đơn: chỉ lấy đơn bán tại quầy (Counter) của gói PT, gồm khách, người tập cùng, gói, giá. Không thấy đơn thì trả null.
        // Câu 1 này đọc Order và ktra đơn còn PENDING ko 
        String orderSql = """
                SELECT o.OrderId, o.OrderCode, o.Status, o.HolderCustomerId, o.PartnerCustomerId, o.PackageId,
                       o.UnitPriceSnapshot, o.VatRateSnapshot, o.VatAmount, o.CreatedAt
                FROM Orders o %s
                JOIN Packages p ON p.PackageId = o.PackageId AND p.PackageType IN ('PTSingle', 'PTCouple')
                WHERE o.OrderId = ? AND o.Channel = 'Counter'
                """.formatted(hint);
        HeldOrder held = null;
        try (PreparedStatement statement = connection.prepareStatement(orderSql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    held = new HeldOrder();
                    held.orderId = rs.getInt("OrderId");
                    held.orderCode = rs.getString("OrderCode");
                    held.status = rs.getString("Status");
                    held.holderId = rs.getInt("HolderCustomerId");
                    int partner = rs.getInt("PartnerCustomerId");
                    held.partnerId = rs.wasNull() ? null : partner;
                    held.packageId = rs.getInt("PackageId");
                    held.price = rs.getLong("UnitPriceSnapshot");
                    held.vatRate = rs.getInt("VatRateSnapshot");
                    held.vatAmount = rs.getLong("VatAmount");
                    held.createdAt = rs.getTimestamp("CreatedAt").toLocalDateTime();
                }
            }
        }
        if (held == null) {
            return null;
        }

        
        // Câu 2 đọc các khung giờ đang giữ của đơn đó (Status = 'Held'), sắp theo ngày giờ. Từ đó lấy PT, các ngày + bắt đầu kết thúc + hạn giữ đơn
        // Câu 2 để biết đơn đang giữ những buổi nào
        String slotSql = """
                SELECT TimeSlotId, PtEmployeeId, SlotDate, StartTime, EndTime, HoldExpiresAt
                FROM TimeSlots %s
                WHERE HeldByOrderId = ? AND Status = 'Held'
                ORDER BY SlotDate, StartTime
                """.formatted(hint);
        try (PreparedStatement statement = connection.prepareStatement(slotSql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    PtScheduleItem slot = new PtScheduleItem();
                    slot.setTimeSlotId(rs.getInt("TimeSlotId"));
                    slot.setSlotDate(rs.getDate("SlotDate").toLocalDate());
                    slot.setStartTime(rs.getTime("StartTime").toLocalTime());
                    slot.setEndTime(rs.getTime("EndTime").toLocalTime());
                    slot.setKind("Held");
                    held.slots.add(slot);
                    held.ptId = rs.getInt("PtEmployeeId");
                    LocalDateTime expires = rs.getTimestamp("HoldExpiresAt").toLocalDateTime();
                    
                    // Khung đầu tiên thì lấy luôn hạn của nó, đề phòng sau này các khung có hạn khác nhau
                    held.expiresAt = held.expiresAt == null || expires.isBefore(held.expiresAt) ? expires : held.expiresAt; 
                }
            }
        }
        return held;
    }


    // ============================================================================================================
    // PHỤC VỤ HÀM CHÍNH hold ("Giữ lịch 15 phút") — GHI, trong transaction của Service, KHÔNG tự commit
    //   lockPt → nextOrderCode → insertPendingOrder → holdFreeSlot / insertHeldSlot
    // ============================================================================================================

    /**
     * [hold] Employees: khóa 1 dòng (UPDLOCK, HOLDLOCK), không đổi dữ liệu.
     * Khóa dòng PT: hai lễ tân bán cùng PT, hoặc PT đang Công bố / xử lý buổi, phải xếp hàng.
     */
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
     * [hold] Orders: R MAX(OrderCode) có khóa.
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
     * [hold] Orders: C 1 dòng (Status Pending, Channel Counter, ShiftId = ca đang mở).
     * Đơn tại quầy CHỜ THANH TOÁN (Pending): giữ chỗ trước, thu tiền sau. Không voucher (CBR-76).
     * Đơn tại quầy không có hạn online (PaymentDueAt NULL — CHECK); hạn 15 phút nằm ở TimeSlots.HoldExpiresAt.
     * Chép tên, mô tả, quyền lợi, giá của gói vào đơn (DBR-50).
     */
    public int insertPendingOrder(Connection connection, String orderCode, int holderId, Integer partnerId,
            PackageRow pkg, int receptionistId, int shiftId, int vatRate, long vatAmount, LocalDateTime now)
            throws SQLException {
        String sql = """
                INSERT INTO Orders (OrderCode, HolderCustomerId, PartnerCustomerId, PackageId, VoucherId,
                                    CreatedByEmployeeId, ShiftId, Channel, PackageNameSnapshot,
                                    PackageDescriptionSnapshot, PackageBenefitsSnapshot, UnitPriceSnapshot,
                                    DiscountAmount, VatRateSnapshot, VatAmount, Status, PaymentDueAt, CreatedAt)
                VALUES (?, ?, ?, ?, NULL, ?, ?, 'Counter', ?, ?, ?, ?, 0, ?, ?, 'Pending', NULL, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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
            statement.setTimestamp(13, Timestamp.valueOf(now.withNano(0)));
            statement.executeUpdate();
    /** [insertPendingOrder, insertSubscription] đọc khóa IDENTITY vừa sinh. */
            return generatedId(statement);
        }
    }

    /**
     * [hold, buổi "book"] TimeSlots: U 1 dòng Free → Held.
     * Khung PT đã công bố: Free → Held cho đơn này tới hạn. false = khung vừa bị người khác lấy.
     */
    public boolean holdFreeSlot(Connection connection, int slotId, int ptId, int orderId, LocalDateTime expiresAt)
            throws SQLException {
        String sql = """
                UPDATE TimeSlots SET Status = 'Held', HeldByOrderId = ?, HoldExpiresAt = ?
                WHERE TimeSlotId = ? AND PtEmployeeId = ? AND Status = 'Free' AND HeldByOrderId IS NULL
                  AND NOT EXISTS (SELECT 1 FROM Sessions s WHERE s.TimeSlotId = TimeSlots.TimeSlotId AND s.Status = 'Scheduled')
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            statement.setTimestamp(2, Timestamp.valueOf(expiresAt.withNano(0)));
            statement.setInt(3, slotId);
            statement.setInt(4, ptId);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * [hold, buổi "create"] TimeSlots: C 1 dòng Held.
     * Ngày PT chưa có khung giờ đó: tạo khung mới ở trạng thái Held cho đơn này (hủy giữ chỗ thì xóa khung này).
     */
    public void insertHeldSlot(Connection connection, int ptId, LocalDate date, LocalTime start, LocalTime end,
            int orderId, LocalDateTime expiresAt, LocalDateTime now) throws SQLException {
        // DBR-33: chặn chồng giờ NGAY TRONG SQL (lớp cuối — Service có thể lọt do race hay khung Withdrawn).
        // Công thức nửa mở [start, end): khung cũ trùng khi cũ.StartTime < mới.EndTime AND cũ.EndTime > mới.StartTime.
        // INSERT ... SELECT ... WHERE NOT EXISTS: nếu trùng thì SELECT trả 0 dòng → không ghi gì → ta báo lỗi.
        String sql = """
                INSERT INTO TimeSlots (PtEmployeeId, SlotDate, StartTime, EndTime, Status, HeldByOrderId, HoldExpiresAt, CreatedAt)
                SELECT ?, ?, CAST(? AS time), CAST(? AS time), 'Held', ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM TimeSlots t
                    WHERE t.PtEmployeeId = ? AND t.SlotDate = ? AND t.Status <> 'Withdrawn'
                      AND t.StartTime < CAST(? AS time) AND t.EndTime > CAST(? AS time)
                )
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, ptId);
            statement.setDate(2, Date.valueOf(date));
            statement.setString(3, start.toString());
            statement.setString(4, end.toString());
            statement.setInt(5, orderId);
            statement.setTimestamp(6, Timestamp.valueOf(expiresAt.withNano(0)));
            statement.setTimestamp(7, Timestamp.valueOf(now.withNano(0)));
            // Các dấu ? trong NOT EXISTS (theo đúng thứ tự xuất hiện trong SQL):
            statement.setInt(8, ptId);                                   // t.PtEmployeeId = ?
            statement.setDate(9, Date.valueOf(date));                    // t.SlotDate = ?
            statement.setString(10, end.toString());                     // t.StartTime < @end
            statement.setString(11, start.toString());                   // t.EndTime  > @start
            int inserted = statement.executeUpdate();
            if (inserted == 0) {
                // IllegalArgumentException (không phải SQLException) để Service trả đúng câu lỗi cho lễ tân.
                throw new IllegalArgumentException("Khung giờ này trùng với khung giờ đã có của PT ("
                        + start + "–" + end + ").");
            }
        }
    }


    // ============================================================================================================
    // PHỤC VỤ HÀM CHÍNH pay ("Xác nhận đã thu tiền") — GHI, trong transaction của Service
    //   markOrderPaid → insertTransaction → insertSubscription → bookHeldSlot + insertSession → findCustomerAccounts (thông báo)
    // ============================================================================================================

    /**
     * [pay] Orders: U 1 dòng Pending → Paid.
     * Đơn Pending → Paid. false = đơn không còn Pending (đã thu hoặc đã hủy).
     */
    public boolean markOrderPaid(Connection connection, int orderId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE Orders SET Status = 'Paid', PaidAt = ? WHERE OrderId = ? AND Status = 'Pending'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now.withNano(0)));
            statement.setInt(2, orderId);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * [pay] Transactions: C 1 dòng Success.
     * Giao dịch Success: tiền mặt hoặc chuyển khoản, lễ tân là người nhận (CBR-77).
     */
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

    /**
     * [pay] Subscriptions: C 1 dòng Active.
     * Gói PT của khách: khung giờ cố định, số buổi đã sinh.
     */
    public int insertSubscription(Connection connection, int orderId, int customerId, String type, int ptId,
            LocalDate start, LocalDate end, LocalTime fixedTime, int sessionCount) throws SQLException {
        String sql = """
                INSERT INTO Subscriptions (OrderId, CustomerId, SubscriptionType, PtEmployeeId, StartDate, EndDate,
                                           FixedStartTime, GeneratedSessionCount, Status)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS time), ?, 'Active')
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
            statement.executeUpdate();
            return generatedId(statement);
        }
    }

    /**
     * [pay] TimeSlots: U 1 dòng Held → Booked.
     * Khung đang giữ cho đơn → Booked, bỏ thông tin giữ.
     */
    public boolean bookHeldSlot(Connection connection, int slotId, int orderId) throws SQLException {
        String sql = """
                UPDATE TimeSlots SET Status = 'Booked', HeldByOrderId = NULL, HoldExpiresAt = NULL
                WHERE TimeSlotId = ? AND HeldByOrderId = ? AND Status = 'Held'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, slotId);
            statement.setInt(2, orderId);
            return statement.executeUpdate() == 1;
        }
    }

    /**
     * [pay] Sessions: C 1 dòng Scheduled.
     * 1 buổi Scheduled: giá trị buổi (DBR-59) + hoa hồng theo tỷ lệ PT (DBR-58).
     */
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

    /**
     * [pay → notifySale] Customers + Accounts: R tối đa 2 dòng.
     * Tài khoản Active của khách + người tập cùng (để gửi thông báo). Hồ sơ không có tài khoản → bỏ qua.
     */
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


    // ============================================================================================================
    // PHỤC VỤ HÀM CHÍNH release ("Hủy giữ chỗ") + releaseExpired (nhả đơn quá 15 phút)
    //   findExpiredHoldOrders · releaseHold
    // ============================================================================================================

    /**
     * [releaseExpired] Orders + TimeSlots: R nhiều dòng.
     * Đơn tại quầy còn Pending có khung giữ đã quá hạn (để Service tự nhả).
     */
    public List<Integer> findExpiredHoldOrders(Connection connection, LocalDateTime now) throws SQLException {
        String sql = """
                SELECT DISTINCT o.OrderId
                FROM Orders o
                JOIN TimeSlots t ON t.HeldByOrderId = o.OrderId AND t.Status = 'Held'
                WHERE o.Status = 'Pending' AND o.Channel = 'Counter' AND t.HoldExpiresAt < ?
                """;
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now.withNano(0)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("OrderId"));
                }
            }
        }
        return ids;
    }

    /**
     * [release, releaseExpired] Orders: U → Cancelled · TimeSlots: D khung do lần giữ tạo, U còn lại → Free.
     * Hủy giữ chỗ: đơn → Cancelled với cancelType do Service truyền vào —
     *   lễ tân bấm Hủy → CancelledByReceptionist (lý do ≥ 5 ký tự); quá 15 phút → PaymentTimeout (BR-93),
     * khung do lần giữ này TẠO RA (CreatedAt ≥ lúc tạo đơn) → xóa; khung PT đã công bố từ trước → về Free.
     */
    public void releaseHold(Connection connection, int orderId, LocalDateTime orderCreatedAt, String cancelType,
            String reason, LocalDateTime now) throws SQLException {
        // Câu 1: Hủy đơn
        String cancelSql = """
                UPDATE Orders SET Status = 'Cancelled', CancelType = ?, CancelledAt = ?, CancelReason = ?
                WHERE OrderId = ? AND Status = 'Pending'
                """;
        try (PreparedStatement statement = connection.prepareStatement(cancelSql)) {
            statement.setString(1, cancelType);
            statement.setTimestamp(2, Timestamp.valueOf(now.withNano(0)));
            statement.setNString(3, reason);
            statement.setInt(4, orderId);
            statement.executeUpdate();
        }
        // Câu 2: Xóa đơn do lần giữ này tạo ra, Khung tạo ra (lễ tân thêm + confirm vs PT) => Xóa
        String deleteSql = "DELETE FROM TimeSlots WHERE HeldByOrderId = ? AND Status = 'Held' AND CreatedAt >= ?";
        try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
            statement.setInt(1, orderId);
            statement.setTimestamp(2, Timestamp.valueOf(orderCreatedAt.withNano(0))); // withNano(0) = xóa phần lẻ của giây, chỉ giữ tới giây chẵn 10:00:00.617482300  →  withNano(0)  →  10:00:00, nếu ko có nana Java sẽ làm tròn sai CreateAt
            statement.executeUpdate();
        }
        // Trả các khung còn lại về Free: Các khung khác trả về Free
        String freeSql = "UPDATE TimeSlots SET Status = 'Free', HeldByOrderId = NULL, HoldExpiresAt = NULL WHERE HeldByOrderId = ? AND Status = 'Held'";
        try (PreparedStatement statement = connection.prepareStatement(freeSql)) {
            statement.setInt(1, orderId);
            statement.executeUpdate();
        }
    }


    // ============================================================================================================
    // DÙNG CHUNG
    // ============================================================================================================

    private int generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.getGeneratedKeys()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        throw new SQLException("Không nhận được mã vừa tạo");
    }
}



/*
 * ============================================================================================================
 * PHỤ LỤC — BẢN ĐỒ DTO MÀN BÁN GÓI PT: FIELD NÀO · HÀM NÀO TẠO · ĐI QUA ĐÂU · HIỆN Ở ĐÂU
 * ============================================================================================================
 *
 * A. CÂY GỌI HÀM — ai tạo PtSaleView
 *
 *   (1) XEM TRANG CHỌN   GET /receptionist/pt-sales            (không ?order=)
 *       showPage → prepare → releaseExpired → findReceptionist → newView → build(lock = false) → loadPendingHolds
 *                                                                build = loadHolder → loadPackage → [loadPartner] → loadPt → loadPlan
 *                                                                loadPlan(false) = B1–B2 → B3 → buildPlan → B5 → planJson
 *   (2) TRANG ĐANG GIỮ   GET /receptionist/pt-sales?order=<id>
 *       showPage → prepare → releaseExpired → findReceptionist → newView → loadHeld          (KHÔNG build, KHÔNG pendingHolds)
 *   (3) GIỮ LỊCH         POST action=hold
 *       hold → releaseExpired → [transaction] requireReceptionist → newView → lockPt → build(lock = true) → firstProblem …
 *                                            loadPlan(true) = B1–B2 → B6 → checkRequested → B8 → planBlock.days
 *       (view này KHÔNG đưa ra JSP — chỉ để kiểm rồi ghi DB. Lỗi → Servlet gọi lại (1) để vẽ trang.)
 *
 *   JSP đọc tất cả qua biến "sale" = PtSaleView (showPage: request.setAttribute("sale", view)).
 *
 * ------------------------------------------------------------------------------------------------------------
 * B. PtSaleView (sale) — gốc, chứa 4 khối + danh sách đơn đang giữ
 * ------------------------------------------------------------------------------------------------------------
 *   choice        PtSaleChoiceBlock    new sẵn trong PtSaleView      → mục C
 *   buyer         PtSaleBuyerBlock     new sẵn                       → mục D
 *   plan          PtSalePlanBlock      new sẵn                       → mục E
 *   heldOrder     PtSaleHeldBlock      new sẵn                       → mục F
 *   pendingHolds  List<PtSalePendingHold>   prepare → loadPendingHolds ← DAO findActiveHoldOrders       → mục G
 *                                      CHỈ (1). (2) return trước nên luôn rỗng.        → _pt-sale-holds.jspf
 *   held          (getter isHeld, KHÔNG phải field) = heldOrder.orderId != null       → JSP sale.held: chọn nhánh trang
 *   ready         (getter isReady)  = !held && plan.sessionCount > 0 && plan.planError == null
 *
 * ------------------------------------------------------------------------------------------------------------
 * C. PtSaleChoiceBlock (sale.choice) — khối "Gói & PT" + tóm tắt Đơn bán
 * ------------------------------------------------------------------------------------------------------------
 *   field            (1) xem / (3) giữ lịch                         (2) đang giữ                  hiện ở
 *   packageId        newView ← ?package=                             loadHeld ← đơn                radio gói tích sẵn, ô ẩn form Giữ lịch
 *   ptId             newView ← ?pt=                                  loadHeld ← khung giữ          radio PT tích sẵn, ô ẩn
 *   startDate        newView ← ?start= (trống = ngày mai)            prepare xóa nếu URL thiếu start
 *                                                                    → loadHeld = ngày khung đầu   ô "Ngày bắt đầu kỳ", ô ẩn start
 *   minStart         newView = hôm nay                               (giữ từ newView)              min ô ngày
 *   maxStart         newView = hôm nay + START_DAYS_AHEAD (30)       (giữ từ newView)              max ô ngày
 *   packages         build → loadPackage ← DAO findPtPackages        —                             các ô gói (List<PtSaleOption>, mục H)
 *   pts              build → loadPt ← DAO findWorkingPts             —                             các nút PT (rỗng khi chưa có gói hợp lệ → ẩn)
 *   choiceError      loadPackage (gói ngừng bán) · loadPt (PT sai, ngày ngoài khoảng)   —           chữ đỏ khối Gói & PT; firstProblem
 *   couple           loadPackage = PackageType == 'PTCouple'         —                             c:if hiện ô "Người tập cùng", customerReady
 *   packageName      loadPackage ← DAO findPackage                   loadHeld ← findPackage        Đơn bán "Gói"
 *   price            loadPackage ← giá gói hiện tại                  loadHeld ← đơn (snapshot)     "Phải thu", sessionValue
 *   months           loadPackage                                     loadHeld                      "· N tháng", tính endDate
 *   ptName           loadPt ← DAO findPt                             loadHeld ← findPt             Đơn bán "PT", hộp nhắc PT chưa công bố
 *   commissionRate   loadPt                                          —                             không in (hoa hồng)
 *   endDate          loadPt = calculateEndDateByMonths(start, months)   loadHeld (cùng công thức)  "Kỳ", done2 (JSP), kỳ của loadPlan
 *
 * ------------------------------------------------------------------------------------------------------------
 * D. PtSaleBuyerBlock (sale.buyer) — khối "Khách mua gói"
 * ------------------------------------------------------------------------------------------------------------
 *   customerId       newView ← ?customer=                            loadHeld ← đơn                ô ẩn customer, link Đổi khách
 *   partnerId        newView ← ?partner=                             —                             ô ẩn partner
 *   searchQuery      newView ← ?q= (chỉ khi gửi form tìm, tắt JS)    —                             chữ trong ô tìm
 *   searchResults    build → loadHolder ← DAO searchCustomers (+ whyCannotBuy từng dòng)            danh sách kết quả dự phòng
 *   holder           build → loadHolder ← DAO findCustomer (+ whyCannotBuy)   loadHeld ← findCustomer (KHÔNG chạy whyCannotBuy)
 *                                                                                                  thẻ "Người đứng tên", customerReady
 *   partner          build → loadPartner ← findCustomer (+ whyCannotBuy)      loadHeld (nếu đơn có người tập cùng)
 *                                                                                                  thẻ "Người tập cùng"
 *   holderError      loadHolder: không tìm thấy khách / tìm không ra ai      —                     chữ đỏ dưới ô tìm; firstProblem
 *   partnerError     loadPartner: chưa chọn / trùng người đứng tên           —                     dòng dưới ô tìm người tập cùng; firstProblem
 *
 * ------------------------------------------------------------------------------------------------------------
 * E. PtSalePlanBlock (sale.plan) — khối "Lịch tập" + lưới đang giữ
 * ------------------------------------------------------------------------------------------------------------
 *   weekTimes        newView ← ?t1..t5                               loadHeld ← giờ khung giữ theo thứ   loadPlan B3/B6 đọc
 *   unpublishedWeekdays   loadPlan B2 ← unpublishedWeekdays(slots)   —                             getter unpublishedText → hộp nhắc vàng
 *   planJson         (1) loadPlan B5d = JsonUtil.toJson(PtSalePlan)   —   ((3): luôn null)         <script id="sale-plan-data"> (mục J)
 *   planError        (3) loadPlan B7a / B7b (checkRequested ném lỗi)   —                           firstProblem; JSP khi planJson rỗng
 *   days             (3) loadPlan B8 ← checkRequested → checkDay     loadHeld: mỗi khung = 1 PtSaleDay "book"   hold() ghi DB · _pt-sale-held.jspf
 *   sessionCount     (3) loadPlan B8 = days.size()                   loadHeld = days.size()        "N buổi tập", MSG307, ô Buổi
 *   sessionValue     (3) loadPlan B8 = sessionValue(price, n)        loadHeld (cùng công thức)     ô Buổi "x đ/buổi"
 *   noteCount        (getter) đếm days không có buổi / có warning    —                             _pt-sale-held.jspf ghi chú dưới lưới
 *   ※ (1) xem: days / sessionCount / sessionValue = rỗng / 0 → số buổi trên màn do JS tự đếm từ planJson.
 *
 * ------------------------------------------------------------------------------------------------------------
 * F. PtSaleHeldBlock (sale.heldOrder) — CHỈ trang (2), các trang khác null / 0
 * ------------------------------------------------------------------------------------------------------------
 *   orderId          loadHeld ← tham số orderId (?order=)   → isHeld() = true · ô ẩn "order" form Thu tiền / Hủy giữ
 *   orderCode        loadHeld ← DAO findHeldOrder          → mã đơn cột phải, hộp xác nhận hủy
 *   holdSeconds      loadHeld = held.expiresAt − now (≥ 0) → data-countdown-seconds, common.js bindCountdowns
 *
 * ------------------------------------------------------------------------------------------------------------
 * G. PtSalePendingHold (phần tử sale.pendingHolds) — tạo bằng constructor trong loadPendingHolds
 * ------------------------------------------------------------------------------------------------------------
 *   orderId · orderCode       ← DAO findActiveHoldOrders                    → link "Tiếp tục thu tiền" ?order=, mã đơn
 *   secondsLeft               = MIN(HoldExpiresAt) − now (≥ 0)              → "còn mm:ss"
 *
 * ------------------------------------------------------------------------------------------------------------
 * H. PtSaleOption (phần tử choice.packages / choice.pts) — tạo bằng constructor, field final, KHÔNG có setter
 * ------------------------------------------------------------------------------------------------------------
 *                    gói (loadPackage)                              PT (loadPt)
 *   value            PackageId                                      EmployeeId                → value radio (?package= / ?pt=)
 *   label            tên gói                                        họ tên PT                 → chữ đậm
 *   note             money(price) + " · N tháng · " + loại          initials(fullName) "ĐS"   → dòng nhỏ / avatar
 *   (bản máy bạn có thêm field thứ 4 cho PT ← ptDetail(row): "chuyên môn · N năm kinh nghiệm")
 *   Ô nào được tích: JSP so o.value eq sale.choice.packageId / ptId.
 *
 * ------------------------------------------------------------------------------------------------------------
 * I. PtSaleCustomer (buyer.holder / partner / searchResults[] · JSON gợi ý) — DAO tạo, Service chỉ điền blockReason
 * ------------------------------------------------------------------------------------------------------------
 *   customerId · personId · fullName · customerCode · phone · dateOfBirth · membershipEnd · frozen
 *                    ← PtSaleDAO.searchCustomers / findCustomer (constructor, final)
 *   blockReason      ← whyCannotBuy (4 luật: chính lễ tân · đủ tuổi P-05 · có gói thành viên hôm nay · không bảo lưu)
 *                      gọi ở: search (AJAX) · loadHolder · loadPartner.   loadHeld KHÔNG gọi → trang đang giữ luôn null.
 *   Hiện ở: JSON gợi ý (PtSaleServlet.writeSuggestions: id, name, code, phone, ok, note) · _sale-customer.jspf (biến cust).
 *
 * ------------------------------------------------------------------------------------------------------------
 * J. PtSalePlan (CHỈ để đổi thành planJson ở (1)) — loadPlan B4 buildPlan tạo, B5 điền thêm, B5d toJson
 * ------------------------------------------------------------------------------------------------------------
 *   start · end · price · minutes   buildPlan                  → JS: price (giá/buổi ô Đơn bán); minutes, start, end JS không đọc
 *   times            buildPlan ← candidates (= published)      → nút giờ mỗi dòng Lịch mẫu
 *   days[]           buildPlan: mỗi ngày T2–T6 trong kỳ         → JS vẽ lịch, đếm buổi
 *     .date .weekday .label                                    → ô ngày, gom tuần, "Tối đa N buổi"
 *     .options[]     mỗi giờ của ngày → checkDay
 *        .time .end            giờ bắt đầu / kết thúc          → "07:00 – 08:00"
 *        .ok                   checkDay.isSession()            → xếp được không
 *        .reason .warning      whySkip / warningFor            → "Còn x/y buổi · PT có khách", ô vàng
 *        .fresh                checkDay action == "create"     → "PT chưa mở", ô xác nhận PT (needConfirm)
 *   unpublished      B5 ← unpublishedWeekdays                  → nhãn "PT chưa công bố" trên dòng thứ
 *   published        B5 ← formatTimes(published)               → giờ ngoài danh sách = "PT chưa mở"
 *   open · close     B5 ← giờ mở cửa / đóng cửa − thời lượng   → min / max ô "+ Thêm giờ"
 *   pattern[5]       B5a ← weekTimes (chỉ giờ dùng được)       → nút tích sẵn Lịch mẫu
 *   custom[5]        B5a ← weekdayExtra (giờ tự thêm hợp lệ)   → thêm nút giờ đúng thứ đó
 *   requested[]      B5b ← parseSessions(input.sessions)       → JS khôi phục buổi sửa riêng
 *   notice           B5c ← giờ tự nhập sai                     → <p> đỏ đầu khung Lịch tập
 *   ⚠ PtSalePlan.days[] (mọi ngày × mọi giờ) KHÁC PtSalePlanBlock.days (các buổi đã chốt, mục E).
 *
 * ------------------------------------------------------------------------------------------------------------
 * K. PtSaleDay (phần tử plan.days) — 1 ngày + 1 giờ = 1 buổi (mỗi ngày tối đa 1 buổi)
 * ------------------------------------------------------------------------------------------------------------
 *   Tạo ở 2 nơi:
 *     checkDay (constructor) ← checkRequested (hold, (3)) · ← buildPlan (xem, (1): đổi ngay thành PtSalePlan.Option rồi bỏ)
 *     loadHeld (constructor) — khung đang giữ, action = "book", reason / warning = null
 *   date · dayLabel · startTime · endTime                      → ô lưới, ghi chú; INSERT khung mới (create)
 *   action           checkDay: "book" (có khung Free đúng giờ) | "create" (PT chưa mở) | "skip" (không xếp được)
 *                    → hold: book = holdFreeSlot · create = insertHeldSlot; countNewSlots đếm create
 *   slotId           checkDay: TimeSlotId khung Free (chỉ book)  → holdFreeSlot UPDATE khung này
 *   reason           whySkip (chỉ skip)                          → ô xám + ghi chú
 *   warning          warningFor (phòng nghỉ / PT có đơn nghỉ)    → ô vàng + ghi chú
 *   session          (getter isSession) = action != "skip"
 * ============================================================================================================
 */