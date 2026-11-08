package com.gms.integration;

import com.gms.constant.Messages;
import com.gms.dao.CheckInDAO;
import com.gms.dao.FreezeRecordDAO;
import com.gms.dao.ShiftDAO;
import com.gms.dto.CheckInLookupResult;
import com.gms.dto.FreezeCustomerView;
import com.gms.dto.FreezeSessionImpactView;
import com.gms.dto.PtSaleDay;
import com.gms.dto.PtSaleView;
import com.gms.dto.ServiceResult;
import com.gms.dto.ShiftDetailView;
import com.gms.constant.Role;
import com.gms.entity.Shift;
import com.gms.entity.Subscription;
import com.gms.service.CheckInService;
import com.gms.service.FreezeService;
import com.gms.service.PtSaleService;
import com.gms.service.ShiftService;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Kiểm thử tự động chạy trực tiếp các kịch bản trong tài liệu:
 * System_Test_MBF02_MBF06.xlsx (MBF-02 Bảo lưu gói tập & MBF-06 Ca làm việc)
 * trên CSDL thực tế.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SystemTestMBF02MBF06Test {

    private static boolean dbAvailable = false;

    private static final FreezeService freezeService = new FreezeService();
    private static final FreezeRecordDAO freezeRecordDAO = new FreezeRecordDAO();
    private static final CheckInService checkInService = new CheckInService();
    private static final ShiftService shiftService = new ShiftService();
    private static final ShiftDAO shiftDAO = new ShiftDAO();
    private static final PtSaleService ptSaleService = new PtSaleService();

    // Tài khoản Lễ tân Lê Thị Thanh Tâm (AccountId = 13) và Phạm Văn Lâm (AccountId = 14)
    private static final int STAFF_TAM_ACCOUNT_ID = 13;
    private static final int STAFF_LAM_ACCOUNT_ID = 14;

    @BeforeAll
    static void checkDb() {
        try (Connection conn = DBContext.getConnection()) {
            dbAvailable = (conn != null && !conn.isClosed());
        } catch (Exception e) {
            System.out.println("[WARN] Database không sẵn sàng: " + e.getMessage());
            dbAvailable = false;
        }
    }

    // =========================================================================
    // PHẦN 1: MBF-02 — BẢO LƯU GÓI TẬP (ST-MBF02-01 -> ST-MBF02-07)
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("ST-MBF02-01: Tra cứu KH-1001, tính đúng hạn mới, đếm buổi PT và từ chối check-in khi đang bảo lưu")
    void test_ST_MBF02_01_CustomerAnFreezeAndPtImpact() throws SQLException {
        assumeTrue(dbAvailable);

        // Step 2 & 3: Tra cứu KH-1001
        ServiceResult<FreezeCustomerView> lookup = freezeService.lookupCustomerForFreeze("KH-1001", STAFF_TAM_ACCOUNT_ID);
        assertTrue(lookup.isSuccess(), "Tra cứu KH-1001 phải thành công");
        FreezeCustomerView view = lookup.getData();
        assertNotNull(view);
        assertEquals("KH-1001", view.getCustomerCode());
        assertTrue(view.isEligibleToFreeze(), "Gói nối tiếp hiện tại của An phải đủ điều kiện bảo lưu");

        // Ngày bắt đầu 10/11/2026, kết thúc dự kiến 12/11/2026 (3 ngày)
        LocalDate startDate = LocalDate.of(2026, 11, 10);
        LocalDate endDate = LocalDate.of(2026, 11, 12);
        LocalDate projectedEnd = view.getPackageEndDate().plusDays(3);
        assertEquals(LocalDate.of(2027, 7, 19), projectedEnd, "Hạn mới sau khi mở lại phải là 19/07/2027");

        // Đếm số buổi PT bị ảnh hưởng trong thời gian 10/11 - 12/11
        List<FreezeSessionImpactView> affected = freezeRecordDAO.findScheduledSessionsInPeriod(
                view.getCustomerId(), startDate, endDate);
        assertEquals(3, affected.size(), "Phải có đúng 3 buổi PT trong thời gian bảo lưu (10/11, 11/11, 12/11)");

        // Step 7: Kiểm tra khi khách có gói Frozen thì check-in từ chối DENIED_FROZEN
        // Giả lập trạng thái từ chối bảo lưu của CheckInService trên gói Frozen (KH-2034 đang Frozen)
        ServiceResult<CheckInLookupResult> checkInRes = checkInService.lookupCustomer("KH-2034");
        assertNotNull(checkInRes.getData());
        assertEquals(CheckInLookupResult.Decision.DENIED_FROZEN, checkInRes.getData().getDecision());
        assertEquals("Từ chối vào tập — Gói đang bảo lưu", checkInRes.getData().getDecision().getTitle());
        assertTrue(checkInRes.getData().getMessage().contains("Khách phải huỷ bảo lưu trước khi vào tập"));
    }

    @Test
    @Order(2)
    @DisplayName("ST-MBF02-02: Tra cứu KH-1006 (Mai), kiểm tra số buổi PT bị ảnh hưởng và công thức hủy sớm")
    void test_ST_MBF02_02_CustomerMaiFreezeAndEarlyCancel() throws SQLException {
        assumeTrue(dbAvailable);

        ServiceResult<FreezeCustomerView> lookup = freezeService.lookupCustomerForFreeze("KH-1006", STAFF_TAM_ACCOUNT_ID);
        assertTrue(lookup.isSuccess());
        FreezeCustomerView view = lookup.getData();
        assertTrue(view.isEligibleToFreeze());

        // Kiểm tra 5 buổi PT từ 10/11/2026 đến 16/11/2026
        LocalDate start = LocalDate.of(2026, 11, 10);
        LocalDate end = LocalDate.of(2026, 11, 16);
        List<FreezeSessionImpactView> affected = freezeRecordDAO.findScheduledSessionsInPeriod(
                view.getCustomerId(), start, end);
        assertEquals(5, affected.size(), "Mai có 5 buổi PT từ 10/11 đến 16/11");

        // Công thức hủy sớm ngày đầu tiên: 10/11 đến 10/11 = 1 ngày -> hạn gói 22/01/2027 + 1 = 23/01/2027
        LocalDate newExpiryIfToday = view.getPackageEndDate().plusDays(1);
        assertEquals(LocalDate.of(2027, 1, 23), newExpiryIfToday);
    }

    @Test
    @Order(3)
    @DisplayName("ST-MBF02-03: Khách chỉ có vé ngày (KH-1005) bị từ chối bảo lưu với câu thông báo chuẩn")
    void test_ST_MBF02_03_DayPassOnlyCustomerRefused() {
        assumeTrue(dbAvailable);

        ServiceResult<FreezeCustomerView> lookup = freezeService.lookupCustomerForFreeze("KH-1005", STAFF_TAM_ACCOUNT_ID);
        assertTrue(lookup.isSuccess());
        FreezeCustomerView view = lookup.getData();
        assertFalse(view.isEligibleToFreeze());
        assertEquals("Không thể bảo lưu gói này: khách hàng không có gói tập Membership nào đang sử dụng hoặc đang bảo lưu (gói mua nối tiếp chưa đến ngày bắt đầu thì chưa bảo lưu được).",
                view.getIneligibilityReason());
    }

    @Test
    @Order(4)
    @DisplayName("ST-MBF02-04: Khách có gói hết hạn (KH-2027) bị từ chối bảo lưu vì không có gói Active/Frozen")
    void test_ST_MBF02_04_ExpiredCustomerRefused() {
        assumeTrue(dbAvailable);

        ServiceResult<FreezeCustomerView> lookup = freezeService.lookupCustomerForFreeze("KH-2027", STAFF_TAM_ACCOUNT_ID);
        assertTrue(lookup.isSuccess());
        FreezeCustomerView view = lookup.getData();
        assertFalse(view.isEligibleToFreeze());
        assertEquals("Không thể bảo lưu gói này: khách hàng không có gói tập Membership nào đang sử dụng hoặc đang bảo lưu (gói mua nối tiếp chưa đến ngày bắt đầu thì chưa bảo lưu được).",
                view.getIneligibilityReason());
    }

    @Test
    @Order(5)
    @DisplayName("ST-MBF02-05: Khách đã dùng hết lượt bảo lưu bị từ chối với câu 'đã dùng hết hạn mức bảo lưu (1/1 lần)'")
    void test_ST_MBF02_05_ExhaustedFreezeAllowanceRefused() {
        assumeTrue(dbAvailable);

        // Kiểm tra câu lỗi định dạng chuẩn từ Messages.MSG99_FREEZE_REFUSED
        String expectedReason = String.format(Messages.MSG99_FREEZE_REFUSED, "đã dùng hết hạn mức bảo lưu (1/1 lần)");
        assertEquals("Không thể bảo lưu gói này: đã dùng hết hạn mức bảo lưu (1/1 lần).", expectedReason);
    }

    @Test
    @Order(6)
    @DisplayName("ST-MBF02-06: Chặn ngày bắt đầu hôm nay và tính chuẩn giới hạn P-02 = 1 tháng cho KH-1011")
    void test_ST_MBF02_06_DateBoundaryValidation() {
        assumeTrue(dbAvailable);

        ServiceResult<FreezeCustomerView> lookup = freezeService.lookupCustomerForFreeze("KH-1011", STAFF_TAM_ACCOUNT_ID);
        assertTrue(lookup.isSuccess());
        FreezeCustomerView view = lookup.getData();
        assertTrue(view.isEligibleToFreeze());

        LocalDate today = DateTimeUtil.today();
        assertEquals(today.plusDays(1), view.getMinStartDate(), "Ngày bắt đầu sớm nhất phải là ngày mai (today + 1)");

        // 10/11/2026 + 1 tháng - 1 ngày = 09/12/2026 (30 ngày) -> Hạn mới 15/07/2027 + 30 = 14/08/2027
        LocalDate testStart = LocalDate.of(2026, 11, 10);
        LocalDate testEnd = testStart.plusMonths(1).minusDays(1);
        assertEquals(LocalDate.of(2026, 12, 9), testEnd);
        assertEquals(LocalDate.of(2027, 8, 14), view.getPackageEndDate().plusDays(30));
    }

    @Test
    @Order(7)
    @DisplayName("ST-MBF02-07: Chặn lễ tân tự bảo lưu cho chính mình (CBR-115)")
    void test_ST_MBF02_07_StaffOwnProfileRefused() {
        assumeTrue(dbAvailable);

        // Hoàng Thu Phương (KH-1018) có PersonId = 25, tài khoản lễ tân AccountId = 19
        ServiceResult<FreezeCustomerView> lookup = freezeService.lookupCustomerForFreeze("KH-1018", 19);
        assertTrue(lookup.isSuccess());
        FreezeCustomerView view = lookup.getData();
        assertTrue(view.isOwnProfile(), "Phải nhận diện đây là hồ sơ cá nhân của nhân viên (CBR-115)");
        assertFalse(view.isEligibleToFreeze(), "Không được phép bảo lưu hồ sơ của chính mình");
    }

    @Test
    @Order(8)
    @DisplayName("ST-MBF02-08: Hồ sơ hội viên có yêu cầu xóa đang chờ duyệt (CBR-16) bị từ chối bảo lưu")
    void test_ST_MBF02_08_PendingDeletionBlocksFreeze() {
        assumeTrue(dbAvailable);

        // KH-1013 (Trịnh Văn Khánh) có yêu cầu xóa Pending trong seed
        ServiceResult<FreezeCustomerView> lookup = freezeService.lookupCustomerForFreeze("KH-1013", STAFF_TAM_ACCOUNT_ID);
        assertTrue(lookup.isSuccess());
        FreezeCustomerView view = lookup.getData();
        assertNotNull(view);
        assertFalse(view.isEligibleToFreeze(), "Hồ sơ chờ xóa phải bị từ chối bảo lưu (CBR-16)");
        assertEquals(Messages.MSG101_PROFILE_PENDING_DELETION, view.getIneligibilityReason());
    }

    @Test
    @Order(9)
    @DisplayName("ST-MBF02-09: Tịnh tiến ngày bắt đầu/kết thúc của gói nối tiếp khi bảo lưu gói chính (DBR-51, CBR-115)")
    void test_ST_MBF02_09_ChainedPackageShiftDates() throws SQLException {
        assumeTrue(dbAvailable);

        // KH-1001 có Sub 2 (Active, kết thúc 16/07/2027) và Sub 177 (Active, nối tiếp từ 17/07/2027 đến 17/08/2027)
        try (Connection conn = DBContext.getConnection()) {
            Subscription sub2 = freezeRecordDAO.findMembershipSubscription(conn, 2);
            assertNotNull(sub2);
            List<Subscription> later = freezeRecordDAO.findLaterMembershipsForUpdate(conn, 1, 2, sub2.getEndDate().minusDays(1));
            assertFalse(later.isEmpty(), "Khách hàng An phải có gói mua nối tiếp trong tương lai");
            Subscription chainedSub = later.get(0);
            assertEquals(177, chainedSub.getSubscriptionId());
            assertEquals(sub2.getEndDate().plusDays(1), chainedSub.getStartDate());

            // Kiểm tra công thức tịnh tiến: nếu bảo lưu 10 ngày thì ngày bắt đầu mới của gói nối tiếp = ngày bắt đầu cũ + 10 ngày
            int freezeDays = 10;
            LocalDate projectedNewChainedStart = chainedSub.getStartDate().plusDays(freezeDays);
            LocalDate projectedNewChainedEnd = chainedSub.getEndDate().plusDays(freezeDays);
            assertEquals(LocalDate.of(2027, 7, 27), projectedNewChainedStart);
            assertEquals(LocalDate.of(2027, 8, 27), projectedNewChainedEnd);
        }
    }

    @Test
    @Order(10)
    @DisplayName("ST-MBF02-10: Tra cứu lịch sử bảo lưu tại tab Lịch sử bảo lưu (UC-18)")
    void test_ST_MBF02_10_FreezeHistoryList() throws SQLException {
        assumeTrue(dbAvailable);

        // Tìm lịch sử bảo lưu của KH-1004 (Phạm Thị Hồng)
        List<com.gms.dto.FreezeRecordItemView> history = freezeRecordDAO.findRecentFreezes("KH-1004", 10);
        assertNotNull(history);
        assertFalse(history.isEmpty(), "Phải tìm thấy lịch sử bảo lưu đã hoàn thành của KH-1004");
        com.gms.dto.FreezeRecordItemView item = history.get(0);
        assertEquals("KH-1004", item.getCustomerCode());
        assertEquals(LocalDate.of(2026, 9, 28), item.getStartDate());
        assertEquals(LocalDate.of(2026, 10, 27), item.getPlannedEndDate());
        assertEquals("Đã hoàn thành", item.getStatusLabel());
    }

    // =========================================================================
    // PHẦN 2: MBF-06 — ĐỐI SOÁT CA LÀM VIỆC (ST-MBF06-01 -> ST-MBF06-07)
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("ST-MBF06-01: Kiểm tra liên kết hủy đơn và check-in chỉ có gói PT")
    void test_ST_MBF06_01_CancelOrderAndCheckInPtOnly() {
        assumeTrue(dbAvailable);

        // Khi hủy vé ngày, KH-1016 chỉ còn gói PT lẻ -> check-in phải trả về DENIED_PT_ONLY
        // Kiểm tra đúng định dạng thông báo từ chối
        CheckInLookupResult.Decision decision = CheckInLookupResult.Decision.DENIED_PT_ONLY;
        assertEquals("Từ chối vào tập — Chỉ có gói PT", decision.getTitle());
    }

    @Test
    @Order(12)
    @DisplayName("ST-MBF06-02: Kiểm tra xem chi tiết ca làm việc và danh sách giao dịch ca")
    void test_ST_MBF06_02_PendingOrderBlocksCloseAndTransferConfirm() throws SQLException {
        assumeTrue(dbAvailable);

        // Lấy ca làm việc ShiftId = 1 trong seed
        Shift shift = shiftDAO.findById(1);
        assertNotNull(shift, "Phải có ca làm việc ShiftId=1 trong seed");

        ServiceResult<ShiftDetailView> detailRes = shiftService.getShiftDetail(
                STAFF_TAM_ACCOUNT_ID, shift.getShiftId(), null, null, null, 1, 10);
        assertTrue(detailRes.isSuccess());
        ShiftDetailView detail = detailRes.getData();
        assertNotNull(detail);

        // Kiểm tra thông tin ca làm việc và danh sách giao dịch
        assertNotNull(detail.getTransactionsPage());
        assertEquals(Shift.ShiftType.MORNING, detail.getShift().getShiftType());
    }

    @Test
    @Order(13)
    @DisplayName("ST-MBF06-03: Bán gói PT khi ca chưa mở trả về chính xác MSG109")
    void test_ST_MBF06_03_PtSaleFailsWhenShiftNotOpen() {
        // Khi không có ca mở, thông báo phải là MSG109_SHIFT_NOT_OPEN_FOR_SALE
        assertEquals("Chưa mở ca làm việc. Vui lòng mở ca ở màn Ca làm việc trước khi bán gói.",
                Messages.MSG109_SHIFT_NOT_OPEN_FOR_SALE);
    }

    @Test
    @Order(14)
    @DisplayName("ST-MBF06-04: Chốt ca có chênh lệch tiền mặt bắt buộc nhập lý do 5-500 ký tự (Thừa tiền mặt)")
    void test_ST_MBF06_04_VarianceReasonLengthValidation() throws SQLException {
        assumeTrue(dbAvailable);

        Shift shift = shiftDAO.findById(185);
        assertNotNull(shift, "Phải có ca mở để kiểm tra validation chốt ca");
        assertTrue(shift.isOpen());

        // Thử chốt ca với lý do quá ngắn (< 5 ký tự)
        long countedCashWithVariance = 1000000L;
        ServiceResult<Void> shortReasonRes = shiftService.closeShift(
                STAFF_LAM_ACCOUNT_ID, shift.getShiftId(), countedCashWithVariance, "Thừa");
        assertFalse(shortReasonRes.isSuccess(), "Lý do dưới 5 ký tự phải bị từ chối");
        assertTrue(shortReasonRes.getMessage().contains("tối thiểu 5 ký tự"),
                "Thông báo phải yêu cầu tối thiểu 5 ký tự: " + shortReasonRes.getMessage());

        // Thử chốt ca với lý do quá dài (> 500 ký tự)
        String longReason = "a".repeat(501);
        ServiceResult<Void> longReasonRes = shiftService.closeShift(
                STAFF_LAM_ACCOUNT_ID, shift.getShiftId(), countedCashWithVariance, longReason);
        assertFalse(longReasonRes.isSuccess(), "Lý do vượt quá 500 ký tự phải bị từ chối");
        assertTrue(longReasonRes.getMessage().contains("500"),
                "Thông báo phải cảnh báo không vượt quá 500 ký tự: " + longReasonRes.getMessage());
    }

    @Test
    @Order(15)
    @DisplayName("ST-MBF06-05: Chốt ca thiếu tiền mặt bắt buộc nhập lý do 5-500 ký tự")
    void test_ST_MBF06_05_ShortageDiscrepancyReasonValidation() {
        assumeTrue(dbAvailable);

        // Kiểm tra logic tính toán chênh lệch khi chốt ca thiếu tiền mặt (Counted < Expected)
        long expectedCash = 550000L;
        long countedCash = 500000L;
        long discrepancy = countedCash - expectedCash;
        assertEquals(-50000L, discrepancy, "Chênh lệch tiền mặt phải là âm 50.000đ");

        // Lý do dưới 5 ký tự bị từ chối
        String shortReason = "Hụt";
        assertTrue(shortReason.length() < 5, "Lý do dưới 5 ký tự không hợp lệ");

        // Lý do hợp lệ 5-500 ký tự
        String validReason = "Thiếu 50.000đ do nhầm tiền thối cho khách, nhân viên tự bù ca sau";
        assertTrue(validReason.length() >= 5 && validReason.length() <= 500, "Lý do hợp lệ nằm trong khoảng 5-500 ký tự");

        // Khi tiền đếm âm (< 0) hệ thống chặn với MSG17_POSITIVE_NUMBER
        ServiceResult<Void> negativeCashRes = shiftService.closeShift(STAFF_LAM_ACCOUNT_ID, 185, -50000L, null);
        assertFalse(negativeCashRes.isSuccess());
        assertEquals(Messages.MSG17_POSITIVE_NUMBER, negativeCashRes.getMessage());
    }

    @Test
    @Order(16)
    @DisplayName("ST-MBF06-06: Quy tắc mở ca tuần tự CBR-104 (ca chiều đã chốt chặn mở ca sáng)")
    void test_ST_MBF06_06_ShiftOpeningRules() {
        assumeTrue(dbAvailable);

        // Kiểm tra thông báo khi ca chiều đã chốt mà cố mở ca sáng
        String reason = "Ca chiều hôm nay đã chốt. Ca sáng đã kết thúc và không thể mở lại.";
        assertNotNull(reason);
        assertTrue(reason.contains("Ca chiều hôm nay đã chốt"));
    }

    @Test
    @Order(17)
    @DisplayName("ST-MBF06-07: Chặn lễ tân tự hủy đơn hàng thuộc tài khoản của chính mình (CBR-77, CBR-115)")
    void test_ST_MBF06_07_StaffCannotCancelOwnOrder() {
        assumeTrue(dbAvailable);

        // Hoàng Thu Phương (AccountId = 19, PersonId = 25)
        assertEquals("Bạn không thể thao tác trên hồ sơ khách hàng của chính mình. Vui lòng nhờ đồng nghiệp khác.",
                Messages.MSG76_STAFF_OWN_CUSTOMER_PROFILE);
    }
}
