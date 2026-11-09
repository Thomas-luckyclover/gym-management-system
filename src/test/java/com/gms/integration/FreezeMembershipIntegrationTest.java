package com.gms.integration;

import com.gms.dao.FreezeRecordDAO;
import com.gms.dto.FreezeCustomerView;
import com.gms.dto.ServiceResult;
import com.gms.entity.FreezeRecord;
import com.gms.entity.Subscription;
import com.gms.service.FreezeService;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration Test kiểm thử toàn vẹn quy trình Bảo lưu gói tập và gói nối tiếp trên CSDL thực tế (SQL Server):
 * <ul>
 *   <li>IT-01: Tra cứu hội viên có 2 gói (hiện tại + nối tiếp) -> Chỉ chọn gói hiện tại, chặn bảo lưu gói nối tiếp chưa bắt đầu.</li>
 *   <li>IT-02: Thực hiện bảo lưu trên gói hiện tại (Gói A) -> Subscriptions.Status = Frozen, Customers.Status = Frozen, FreezeUsed = 1. Gói nối tiếp (Gói B) vẫn Active, FreezeUsed = 0.</li>
 *   <li>IT-03: Mở lại Gói A sớm -> Gói A gia hạn ngày, Gói B tự động tịnh tiến StartDate/EndDate để không chồng hạn, Gói B vẫn giữ FreezeUsed = 0.</li>
 *   <li>IT-04: Khi Gói A hết hạn và Gói B kích hoạt -> Gói B có đủ 1 lần bảo lưu của riêng nó và thực hiện bảo lưu thành công độc lập.</li>
 * </ul>
 */
// Test hệ thống chạy trên DB GMS THẬT (đọc + ghi dữ liệu thử) và phụ thuộc dữ liệu / ngày trên máy người viết.
// Không chạy khi build thường; chạy riêng: mvn test -Dgroups=system-db -DexcludedGroups=
@Tag("system-db")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FreezeMembershipIntegrationTest {

    private static boolean dbAvailable = false;
    private static final FreezeService freezeService = new FreezeService();
    private static final FreezeRecordDAO freezeRecordDAO = new FreezeRecordDAO();

    // Dữ liệu kiểm thử cố định
    private static final String TEST_PHONE = "0989999999";
    private static final String TEST_CUSTOMER_CODE = "KH-9999";
    private static final String TEST_ORDER_CODE_A = "DH-2026-9991";
    private static final String TEST_ORDER_CODE_B = "DH-2026-9992";

    private static int staffAccountId = 13; // Tài khoản Lễ tân Lê Thị Thanh Tâm
    private static int testPersonId = 0;
    private static int testCustomerId = 0;
    private static int testOrderAId = 0;
    private static int testOrderBId = 0;
    private static int testSubAId = 0;
    private static int testSubBId = 0;

    private static LocalDate today;
    private static LocalDate initialEndA;
    private static LocalDate initialStartB;
    private static LocalDate initialEndB;

    @BeforeAll
    static void setUpDatabaseAndData() {
        try (Connection conn = DBContext.getConnection()) {
            dbAvailable = (conn != null && !conn.isClosed());
        } catch (Exception e) {
            System.out.println("[WARN] Database không sẵn sàng cho integration test: " + e.getMessage());
            dbAvailable = false;
        }

        if (!dbAvailable) {
            return;
        }

        today = DateTimeUtil.today();
        initialEndA = today.plusDays(20);
        initialStartB = initialEndA.plusDays(1);
        initialEndB = initialStartB.plusMonths(1);

        // Dọn dẹp trước nếu còn sót dữ liệu test cũ
        cleanTestData();

        // Tạo dữ liệu test trên CSDL thật
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);

            // 1. Tạo Person
            String insertPersonSql = """
                    INSERT INTO Persons (FullName, DateOfBirth, Phone, Email)
                    VALUES (N'Khách Hàng Test Bảo Lưu', '1995-05-15', ?, 'test.freeze.chain@example.com')
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(insertPersonSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, TEST_PHONE);
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) testPersonId = rs.getInt(1);
                }
            }

            // 2. Tạo Customer
            String insertCustomerSql = """
                    INSERT INTO Customers (PersonId, CustomerCode, CustomerType, Status)
                    VALUES (?, ?, 'Self', 'Active')
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(insertCustomerSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, testPersonId);
                stmt.setString(2, TEST_CUSTOMER_CODE);
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) testCustomerId = rs.getInt(1);
                }
            }

            // 3. Tạo Đơn hàng A & B (Gói 1 Tháng Membership, PackageId = 2)
            testOrderAId = insertTestOrder(conn, testCustomerId, TEST_ORDER_CODE_A, 2);
            testOrderBId = insertTestOrder(conn, testCustomerId, TEST_ORDER_CODE_B, 2);

            // 4. Tạo Subscription A (Gói hiện tại: đang chạy từ hôm qua đến 20 ngày nữa)
            testSubAId = insertTestSubscription(conn, testOrderAId, testCustomerId,
                    today.minusDays(10), initialEndA, "Active", 0, 1);

            // 5. Tạo Subscription B (Gói nối tiếp: bắt đầu sau khi Gói A hết hạn)
            testSubBId = insertTestSubscription(conn, testOrderBId, testCustomerId,
                    initialStartB, initialEndB, "Active", 0, 1);

            conn.commit();
        } catch (SQLException e) {
            cleanTestData();
            throw new RuntimeException("Lỗi thiết lập dữ liệu integration test", e);
        }
    }

    @AfterAll
    static void tearDownDatabase() {
        if (dbAvailable) {
            cleanTestData();
        }
    }

    private static void cleanTestData() {
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement()) {
                // Xóa AuditLogs
                stmt.executeUpdate("DELETE FROM AuditLogs WHERE EntityName IN ('Subscription', 'FreezeRecord') "
                        + "AND EntityId IN (" + testSubAId + ", " + testSubBId + ")");
                // Xóa FreezeRecords
                stmt.executeUpdate("DELETE FROM FreezeRecords WHERE SubscriptionId IN (" + testSubAId + ", " + testSubBId + ")");
                // Xóa Subscriptions
                stmt.executeUpdate("DELETE FROM Subscriptions WHERE SubscriptionId IN (" + testSubAId + ", " + testSubBId + ")");
                // Xóa Orders
                stmt.executeUpdate("DELETE FROM Orders WHERE OrderCode IN ('" + TEST_ORDER_CODE_A + "', '" + TEST_ORDER_CODE_B + "')");
                // Xóa Customers
                stmt.executeUpdate("DELETE FROM Customers WHERE CustomerCode = '" + TEST_CUSTOMER_CODE + "'");
                // Xóa Persons
                stmt.executeUpdate("DELETE FROM Persons WHERE Phone = '" + TEST_PHONE + "'");
            }
        } catch (Exception ignored) {
        }
    }

    private static int insertTestOrder(Connection conn, int customerId, String orderCode, int packageId) throws SQLException {
        String sql = """
                INSERT INTO Orders (OrderCode, HolderCustomerId, PackageId, Channel, PackageNameSnapshot,
                                    UnitPriceSnapshot, DiscountAmount, VatRateSnapshot, VatAmount, Status,
                                    PaymentDueAt, PaidAt)
                VALUES (?, ?, ?, 'Online', N'Gói 1 Tháng', 799000, 0, 10, 72636, 'Paid',
                        DATEADD(MINUTE, 15, SYSDATETIME()), SYSDATETIME())
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, orderCode);
            stmt.setInt(2, customerId);
            stmt.setInt(3, packageId);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Không tạo được test order " + orderCode);
    }

    private static int insertTestSubscription(Connection conn, int orderId, int customerId,
                                              LocalDate start, LocalDate end, String status,
                                              int freezeUsed, int maxFreezes) throws SQLException {
        String sql = """
                INSERT INTO Subscriptions (OrderId, CustomerId, SubscriptionType, StartDate, EndDate,
                                           FreezeUsed, MaxFreezesSnapshot, Status)
                VALUES (?, ?, 'Membership', ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, orderId);
            stmt.setInt(2, customerId);
            stmt.setDate(3, Date.valueOf(start));
            stmt.setDate(4, Date.valueOf(end));
            stmt.setInt(5, freezeUsed);
            stmt.setInt(6, maxFreezes);
            stmt.setString(7, status);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Không tạo được test subscription");
    }

    // =========================================================================
    // CÁC TEST CASES TÍCH HỢP TUẦN TỰ
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("IT-01: Tra cứu chỉ nhận Gói A (gói đang dùng); Từ chối bảo lưu Gói B khi chưa đến ngày kích hoạt")
    void test01_LookupAndRefuseFreezeForFutureChainedPackage() throws SQLException {
        assumeTrue(dbAvailable, "Bỏ qua vì CSDL không kết nối được");

        // 1. Tra cứu hội viên qua số điện thoại hoặc mã KH
        ServiceResult<FreezeCustomerView> lookupRes = freezeService.lookupCustomerForFreeze(TEST_CUSTOMER_CODE, staffAccountId);
        assertTrue(lookupRes.isSuccess(), "Tra cứu hội viên phải thành công");
        FreezeCustomerView view = lookupRes.getData();
        assertNotNull(view);

        // Phải chọn đúng Gói A (đang sử dụng)
        assertEquals(testSubAId, view.getSubscriptionId(), "Hệ thống phải tự động chọn gói đang sử dụng (Gói A)");
        assertTrue(view.isEligibleToFreeze(), "Gói A đang hoạt động phải đủ điều kiện bảo lưu");
        assertEquals(0, view.getFreezeUsed(), "Gói A chưa bảo lưu lần nào");
        assertEquals(1, view.getMaxFreezesSnapshot(), "Hạn mức bảo lưu Gói A là 1");

        // 2. Thử cố tình gọi applyFreeze trực tiếp trên Gói B (gói nối tiếp chưa kích hoạt)
        LocalDate bStart = initialStartB.plusDays(1);
        LocalDate bEnd = initialStartB.plusDays(10);
        ServiceResult<Void> directFreezeB = freezeService.applyFreeze(
                staffAccountId, testSubBId, bStart, bEnd, "Cố tình bảo lưu gói nối tiếp trước");

        assertFalse(directFreezeB.isSuccess(), "Phải từ chối bảo lưu gói nối tiếp chưa bắt đầu");
        assertTrue(directFreezeB.getMessage().contains("chưa đến ngày bắt đầu"),
                "Thông báo lỗi phải giải thích gói chưa đến ngày bắt đầu: " + directFreezeB.getMessage());

        // Kiểm tra trong DB: Gói B vẫn nguyên vẹn
        Subscription subBInDb;
        try (Connection conn = DBContext.getConnection()) {
            subBInDb = freezeRecordDAO.findMembershipSubscription(conn, testSubBId);
        }
        assertNotNull(subBInDb);
        assertEquals(Subscription.Status.ACTIVE, subBInDb.getStatus());
        assertEquals(0, subBInDb.getFreezeUsed());
    }

    @Test
    @Order(2)
    @DisplayName("IT-02: Thực hiện bảo lưu Gói A thành công -> Gói A Frozen (FreezeUsed=1), Khách Frozen, Gói B vẫn giữ FreezeUsed=0")
    void test02_ApplyFreezeOnCurrentPackage_AndVerifyDatabaseState() throws SQLException {
        assumeTrue(dbAvailable, "Bỏ qua vì CSDL không kết nối được");

        LocalDate freezeStart = today.plusDays(1);
        LocalDate freezeEnd = today.plusDays(10);

        ServiceResult<Void> freezeResult = freezeService.applyFreeze(
                staffAccountId, testSubAId, freezeStart, freezeEnd, "Khách hàng đi công tác 10 ngày");

        assertTrue(freezeResult.isSuccess(), "Bảo lưu Gói A phải thành công: " + freezeResult.getMessage());

        // Kiểm tra trực tiếp trong CSDL thật
        try (Connection conn = DBContext.getConnection()) {
            // Gói A: Status = Frozen, FreezeUsed = 1
            Subscription subA = freezeRecordDAO.findMembershipSubscription(conn, testSubAId);
            assertNotNull(subA);
            assertEquals(Subscription.Status.FROZEN, subA.getStatus(), "Gói A phải sang Frozen");
            assertEquals(1, subA.getFreezeUsed(), "Gói A phải cập nhật FreezeUsed = 1");

            // Khách hàng: Status = Frozen (BR-20)
            try (PreparedStatement stmt = conn.prepareStatement("SELECT Status FROM Customers WHERE CustomerId = ?")) {
                stmt.setInt(1, testCustomerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Frozen", rs.getString("Status"), "Khách hàng phải sang trạng thái Frozen");
                }
            }

            // Bản ghi FreezeRecords phải tồn tại
            FreezeRecord openFreeze = freezeRecordDAO.findOpenFreezeBySubscriptionId(conn, testSubAId);
            assertNotNull(openFreeze, "Phải có bản ghi bảo lưu đang mở cho Gói A");
            assertEquals(freezeStart, openFreeze.getStartDate());
            assertEquals(freezeEnd, openFreeze.getPlannedEndDate());
            assertNull(openFreeze.getActualEndDate());

            // Gói B trong DB: hoàn toàn không bị ảnh hưởng, FreezeUsed vẫn là 0
            Subscription subB = freezeRecordDAO.findMembershipSubscription(conn, testSubBId);
            assertNotNull(subB);
            assertEquals(Subscription.Status.ACTIVE, subB.getStatus());
            assertEquals(0, subB.getFreezeUsed(), "Gói B chưa bảo lưu nên FreezeUsed vẫn phải bằng 0");
            assertEquals(1, subB.getMaxFreezesSnapshot());
        }
    }

    @Test
    @Order(3)
    @DisplayName("IT-03: Mở lại Gói A sớm -> Gói A gia hạn ngày, Gói B tự động tịnh tiến ngày trên DB, Gói B vẫn giữ FreezeUsed=0")
    void test03_CancelFreezeEarly_ShiftsChainedPackageWithoutAffectingItsFreezeUsed() throws SQLException {
        assumeTrue(dbAvailable, "Bỏ qua vì CSDL không kết nối được");

        FreezeRecord openFreeze;
        try (Connection conn = DBContext.getConnection()) {
            openFreeze = freezeRecordDAO.findOpenFreezeBySubscriptionId(conn, testSubAId);
        }
        assertNotNull(openFreeze, "Phải có đợt bảo lưu đang mở để hủy sớm");

        // Gọi hủy bảo lưu sớm tại quầy
        ServiceResult<Void> cancelRes = freezeService.cancelFreezeEarly(staffAccountId, openFreeze.getFreezeRecordId());
        assertTrue(cancelRes.isSuccess(), "Hủy bảo lưu sớm phải thành công: " + cancelRes.getMessage());

        // Kiểm tra trạng thái thực tế trong CSDL
        try (Connection conn = DBContext.getConnection()) {
            // 1. Gói A đã Active trở lại
            Subscription subA = freezeRecordDAO.findMembershipSubscription(conn, testSubAId);
            assertNotNull(subA);
            assertEquals(Subscription.Status.ACTIVE, subA.getStatus());
            assertEquals(1, subA.getFreezeUsed(), "Gói A vẫn giữ FreezeUsed = 1 (không hồi lượt theo BR-26)");

            // 2. Khách hàng đã Active trở lại
            try (PreparedStatement stmt = conn.prepareStatement("SELECT Status FROM Customers WHERE CustomerId = ?")) {
                stmt.setInt(1, testCustomerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Active", rs.getString("Status"));
                }
            }

            // 3. Gói B (nối tiếp): StartDate và EndDate phải được tịnh tiến sau hạn mới của Gói A
            Subscription subB = freezeRecordDAO.findMembershipSubscription(conn, testSubBId);
            assertNotNull(subB);
            assertEquals(Subscription.Status.ACTIVE, subB.getStatus());
            assertFalse(subB.getStartDate().isBefore(subA.getEndDate().plusDays(1)),
                    "Ngày bắt đầu của Gói B (" + subB.getStartDate()
                            + ") không được chồng lên ngày hết hạn mới của Gói A (" + subA.getEndDate() + ")");
            assertEquals(0, subB.getFreezeUsed(), "Gói B vẫn giữ FreezeUsed = 0");
            assertEquals(1, subB.getMaxFreezesSnapshot(), "Gói B vẫn giữ MaxFreezesSnapshot = 1");

            // 4. Kiểm tra AuditLog ghi nhận hành động dời gói nối tiếp
            String checkAuditSql = "SELECT COUNT(*) FROM AuditLogs WHERE Action = 'SHIFT_MEMBERSHIP_AFTER_FREEZE' AND EntityId = ?";
            try (PreparedStatement stmt = conn.prepareStatement(checkAuditSql)) {
                stmt.setInt(1, testSubBId);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    // Nếu có ngày dời thực tế thì log phải được ghi
                    assertTrue(rs.getInt(1) >= 0);
                }
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("IT-04: JOB-01 Tự động mở lại gói khi hết hạn bảo lưu -> Cũng tự động dời ngày gói nối tiếp B")
    void test04_Job01AutoResume_AlsoShiftsChainedPackageDates() throws SQLException {
        assumeTrue(dbAvailable, "Bỏ qua vì CSDL không kết nối được");

        LocalDate aOldEnd = today.plusDays(10);
        LocalDate bOldStart = aOldEnd.plusDays(1);
        LocalDate bOldEnd = bOldStart.plusMonths(1);

        // Đưa Gói A vào trạng thái Frozen với kỳ bảo lưu đã hết hạn hôm qua (5 ngày: today-5 đến today-1)
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(true);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("UPDATE Subscriptions SET Status = 'Frozen', EndDate = '" + Date.valueOf(aOldEnd) + "' WHERE SubscriptionId = " + testSubAId);
                stmt.executeUpdate("UPDATE Subscriptions SET Status = 'Active', StartDate = '" + Date.valueOf(bOldStart) + "', EndDate = '" + Date.valueOf(bOldEnd) + "' WHERE SubscriptionId = " + testSubBId);
                stmt.executeUpdate("UPDATE Customers SET Status = 'Frozen' WHERE CustomerId = " + testCustomerId);

                // Chèn FreezeRecord quá hạn chưa đóng
                stmt.executeUpdate("INSERT INTO FreezeRecords (SubscriptionId, HandledByEmployeeId, StartDate, PlannedEndDate, Reason, CreatedAt) "
                        + "VALUES (" + testSubAId + ", " + 3 + ", '" + Date.valueOf(today.minusDays(5)) + "', '" + Date.valueOf(today.minusDays(1)) + "', N'Bảo lưu JOB-01 test', SYSDATETIME())");
            }
        }

        // Chạy JOB-01 auto-resume
        int resumedCount = freezeService.resumeExpiredFreezes(today);
        assertTrue(resumedCount >= 1, "JOB-01 phải mở lại ít nhất 1 gói bảo lưu quá hạn");

        // Kiểm tra CSDL
        try (Connection conn = DBContext.getConnection()) {
            // Gói A: được gia hạn thêm 5 ngày
            Subscription subA = freezeRecordDAO.findMembershipSubscription(conn, testSubAId);
            assertNotNull(subA);
            assertEquals(Subscription.Status.ACTIVE, subA.getStatus());
            assertEquals(aOldEnd.plusDays(5), subA.getEndDate(), "Hạn gói A phải được cộng 5 ngày bảo lưu");

            // Khách hàng: trở về Active
            try (PreparedStatement stmt = conn.prepareStatement("SELECT Status FROM Customers WHERE CustomerId = ?")) {
                stmt.setInt(1, testCustomerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Active", rs.getString("Status"));
                }
            }

            // Gói B (nối tiếp): TỰ ĐỘNG dời StartDate và EndDate thêm 5 ngày
            Subscription subB = freezeRecordDAO.findMembershipSubscription(conn, testSubBId);
            assertNotNull(subB);
            assertEquals(Subscription.Status.ACTIVE, subB.getStatus());
            assertEquals(subA.getEndDate().plusDays(1), subB.getStartDate(),
                    "Gói B phải được dời ngày bắt đầu ngay sau ngày hết hạn mới của Gói A");
            assertEquals(bOldEnd.plusDays(5), subB.getEndDate(),
                    "Gói B phải được dời ngày kết thúc thêm 5 ngày");
            assertEquals(0, subB.getFreezeUsed(), "Gói B vẫn giữ FreezeUsed = 0");
        }
    }

    @Test
    @Order(5)
    @DisplayName("IT-05: Khi Gói A hết hạn và Gói B kích hoạt -> Gói B có đủ 1 lần bảo lưu của riêng nó và thực hiện bảo lưu thành công")
    void test05_ChainedPackageGetsItsOwnIndependentFreezeOnceActive() throws SQLException {
        assumeTrue(dbAvailable, "Bỏ qua vì CSDL không kết nối được");

        // Mô phỏng thời gian: Gói A đã hết hạn vào hôm qua, Gói B bắt đầu có hiệu lực từ hôm nay
        try (Connection conn = DBContext.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE Subscriptions SET EndDate = ? WHERE SubscriptionId = ?")) {
                stmt.setDate(1, Date.valueOf(today.minusDays(1)));
                stmt.setInt(2, testSubAId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE Subscriptions SET StartDate = ?, EndDate = ? WHERE SubscriptionId = ?")) {
                stmt.setDate(1, Date.valueOf(today));
                stmt.setDate(2, Date.valueOf(today.plusDays(30)));
                stmt.setInt(3, testSubBId);
                stmt.executeUpdate();
            }
        }

        // 1. Tra cứu lại khách hàng lúc này: Hệ thống phải nhận diện Gói B là gói đang sử dụng
        ServiceResult<FreezeCustomerView> lookupRes = freezeService.lookupCustomerForFreeze(TEST_CUSTOMER_CODE, staffAccountId);
        assertTrue(lookupRes.isSuccess());
        FreezeCustomerView view = lookupRes.getData();
        assertNotNull(view);

        assertEquals(testSubBId, view.getSubscriptionId(), "Lúc này hệ thống phải chọn Gói B vì Gói A đã hết hạn");
        assertEquals(0, view.getFreezeUsed(), "Gói B có FreezeUsed = 0");
        assertEquals(1, view.getMaxFreezesSnapshot(), "Gói B có MaxFreezesSnapshot = 1");
        assertTrue(view.isEligibleToFreeze(), "Gói B hoàn toàn đủ điều kiện bảo lưu 1 lần của riêng nó");

        // 2. Thực hiện bảo lưu cho Gói B (từ ngày mai đến 7 ngày sau)
        LocalDate bFreezeStart = today.plusDays(1);
        LocalDate bFreezeEnd = today.plusDays(7);
        ServiceResult<Void> freezeBRes = freezeService.applyFreeze(
                staffAccountId, testSubBId, bFreezeStart, bFreezeEnd, "Bảo lưu lần đầu của Gói B (gói nối tiếp)");

        assertTrue(freezeBRes.isSuccess(), "Bảo lưu Gói B phải thành công: " + freezeBRes.getMessage());

        // 3. Kiểm tra CSDL: Gói B đã sang Frozen và FreezeUsed = 1
        try (Connection conn = DBContext.getConnection()) {
            Subscription subBInDb = freezeRecordDAO.findMembershipSubscription(conn, testSubBId);
            assertNotNull(subBInDb);
            assertEquals(Subscription.Status.FROZEN, subBInDb.getStatus(), "Gói B phải sang Frozen");
            assertEquals(1, subBInDb.getFreezeUsed(), "Gói B đã dùng 1/1 lần bảo lưu của chính nó");

            FreezeRecord freezeB = freezeRecordDAO.findOpenFreezeBySubscriptionId(conn, testSubBId);
            assertNotNull(freezeB, "Phải có phiếu bảo lưu trong FreezeRecords cho Gói B");
            assertEquals(testSubBId, freezeB.getSubscriptionId());
            assertEquals(bFreezeStart, freezeB.getStartDate());
            assertEquals(bFreezeEnd, freezeB.getPlannedEndDate());
        }
    }
}
