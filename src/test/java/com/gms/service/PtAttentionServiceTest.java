package com.gms.service;

import com.gms.dto.PtAttentionView;
import com.gms.dto.PtClientPackageView;
import com.gms.entity.Package;
import com.gms.dao.PtDashboardDAO;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra cảnh báo theo khách/gói/ngày, gom trùng và xét người còn lại của gói đôi. */
class PtAttentionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final LocalDate SESSION_DATE = TODAY.plusDays(5);
    private final PtDashboardService service = new PtDashboardService();

    @Test
    void closedDayCardKeepsExactSessionAndRescheduleWarning() {
        PtDashboardDAO.ClosedDaySession row = new PtDashboardDAO.ClosedDaySession(
                274, "Đỗ Thị Mai", 11, LocalDate.of(2026, 10, 23), LocalTime.of(18, 0),
                true, LocalTime.of(19, 0), null, null);

        PtAttentionView item = PtDashboardService.buildClosedDayItem(row);

        assertEquals("closedDay", item.getKind());
        assertEquals(274, item.getSessionId());
        assertEquals("Đỗ Thị Mai", item.getCustomerName());
        assertEquals(11, item.getPtSubscriptionId());
        assertEquals(row.slotDate(), item.getSessionDate());
        assertEquals("Ngày phòng nghỉ — cần dời", item.getAttentionTitle());
        assertEquals("Buổi 23/10/2026 18:00 rơi vào ngày phòng nghỉ. Dời buổi trước ngày này; nếu không, hệ thống sẽ hủy và không tính hoa hồng.", item.getAttentionMessage());
    }

    @Test
    void outsideOpeningHoursCardShowsSessionAndGymHours() {
        PtDashboardDAO.ClosedDaySession row = new PtDashboardDAO.ClosedDaySession(
                274, "Đỗ Thị Mai", 11, LocalDate.of(2026, 10, 23), LocalTime.of(20, 0),
                false, LocalTime.of(21, 0), LocalTime.of(8, 0), LocalTime.of(12, 0));

        PtAttentionView item = PtDashboardService.buildClosedDayItem(row);

        assertEquals("closedDay", item.getKind());
        assertEquals(274, item.getSessionId());
        assertEquals("Ngoài giờ mở cửa — cần dời", item.getAttentionTitle());
        assertEquals("Buổi 23/10/2026 20:00–21:00 nằm ngoài giờ mở cửa (08:00–12:00). Dời buổi trước ngày này; nếu không, hệ thống sẽ hủy và không tính hoa hồng.", item.getAttentionMessage());
    }

    @Test
    void unknownOpeningHoursCardAsksToCheck() {
        PtDashboardDAO.ClosedDaySession row = new PtDashboardDAO.ClosedDaySession(
                274, "Đỗ Thị Mai", 11, LocalDate.of(2026, 10, 23), LocalTime.of(20, 0),
                false, LocalTime.of(21, 0), null, null);

        PtAttentionView item = PtDashboardService.buildClosedDayItem(row);

        assertEquals("closedDay", item.getKind());
        assertEquals("Chưa rõ giờ mở cửa — cần kiểm tra", item.getAttentionTitle());
        assertEquals("Buổi 23/10/2026 20:00 chưa xác định được giờ mở cửa ngày này. Hệ thống sẽ hủy buổi nếu chưa xử lý.", item.getAttentionMessage());
    }

    @Test
    void validMembershipWithoutFreezeCreatesNoAttention() throws SQLException {
        PtClientPackageView valid = row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE, true, false);

        List<PtAttentionView> items = service.buildAttentionItems(List.of(valid));

        assertTrue(items.isEmpty());
    }

    @Test
    void noMembershipAsksForValidMembership() throws SQLException {
        PtClientPackageView missing = row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE, false, false);

        PtAttentionView item = service.buildAttentionItems(List.of(missing)).get(0);

        assertEquals("membership", item.getKind());
        assertEquals("Cần membership hợp lệ cho buổi ngày 12/10/2026.", item.getAttentionMessage());
    }

    @Test
    void freezeTakesPriorityEvenWhenMembershipDoesNotCoverDate() throws SQLException {
        PtClientPackageView frozen = row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE, false, true);

        PtAttentionView item = service.buildAttentionItems(List.of(frozen)).get(0);

        assertEquals("Membership đang bảo lưu", item.getAttentionTitle());
        assertEquals("frozen", item.getKind());
    }

    @Test
    void fiveUncoveredDatesBecomeOneAttentionForEarliestDate() throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_SINGLE, TODAY.plusDays(9), false, false),
                row(1, 101, Package.Type.PT_SINGLE, TODAY.plusDays(7), false, false),
                row(1, 101, Package.Type.PT_SINGLE, TODAY.plusDays(5), false, false),
                row(1, 101, Package.Type.PT_SINGLE, TODAY.plusDays(6), false, false),
                row(1, 101, Package.Type.PT_SINGLE, TODAY.plusDays(8), false, false));

        List<PtAttentionView> items = service.buildAttentionItems(rows);

        // 5 ngày, cùng 1 khách + 1 gói + 1 loại vấn đề => 1 cảnh báo; ngày gần nhất = today + 5.
        int expectedCount = 1;
        LocalDate expectedDate = TODAY.plusDays(5);
        assertEquals(expectedCount, items.size());
        assertEquals(expectedDate, items.get(0).getSessionDate());
    }

    @Test
    void sameCustomerWithTwoPackagesKeepsTwoAttentions() throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE, false, false),
                row(1, 102, Package.Type.PT_SINGLE, SESSION_DATE, false, false));

        // 1 khách có vấn đề trên 2 gói => 1 cảnh báo/gói × 2 gói = 2.
        int expectedCount = 1 * 2;
        assertEquals(expectedCount, service.buildAttentionItems(rows).size());
    }

    @Test
    void twoProblemTypesForSamePackageAreNotMerged() throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE, false, false),
                row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE.plusDays(1), true, true));

        List<PtAttentionView> items = service.buildAttentionItems(rows);

        // 1 thiếu membership + 1 bảo lưu = 2 loại cảnh báo khác nhau.
        assertEquals(1 + 1, items.size());
        assertEquals("Cần membership", items.get(0).getAttentionTitle());
        assertEquals("Membership đang bảo lưu", items.get(1).getAttentionTitle());
    }

    @Test
    void customersWithSameNameAreGroupedById() throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE, false, false),
                row(2, 101, Package.Type.PT_SINGLE, SESSION_DATE, false, false));

        assertEquals(2, service.buildAttentionItems(rows).size());
    }

    @Test
    void frozenCoupleCustomerDoesNotBlockValidPartnerInMessage() throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_COUPLE, SESSION_DATE, true, true),
                row(2, 101, Package.Type.PT_COUPLE, SESSION_DATE, true, false));

        List<PtAttentionView> items = service.buildAttentionItems(rows);

        assertEquals(1, items.size());
        assertTrue(items.get(0).getAttentionMessage().contains("Khách trùng tên vẫn dùng được gym"));
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, false", "true, false"})
    void bothUnusableCoupleCustomersAreExplained(boolean firstFrozen,
            boolean secondFrozen) throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_COUPLE, SESSION_DATE, firstFrozen, firstFrozen),
                row(2, 101, Package.Type.PT_COUPLE, SESSION_DATE, secondFrozen, secondFrozen));

        List<PtAttentionView> items = service.buildAttentionItems(rows);

        // Cả 2 người có vấn đề => 1 dòng/người × 2 người = 2, không suy ra PT phải đổi Status.
        assertEquals(1 * 2, items.size());
        for (PtAttentionView item : items) {
            assertTrue(item.getAttentionMessage().contains("cũng không dùng được gym"));
        }
    }

    @Test
    void partnerMustBelongToSamePackageAndDate() {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_COUPLE, SESSION_DATE, true, true),
                row(2, 101, Package.Type.PT_COUPLE, SESSION_DATE.plusDays(1), true, false),
                row(3, 102, Package.Type.PT_COUPLE, SESSION_DATE, true, false));

        assertThrows(SQLException.class, () -> service.buildAttentionItems(rows));
    }

    @Test
    void partnerIsCheckedOnSelectedEarliestDate() throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_COUPLE, SESSION_DATE.plusDays(2), false, false),
                row(2, 101, Package.Type.PT_COUPLE, SESSION_DATE.plusDays(2), false, false),
                row(1, 101, Package.Type.PT_COUPLE, SESSION_DATE, false, false),
                row(2, 101, Package.Type.PT_COUPLE, SESSION_DATE, true, false));

        List<PtAttentionView> items = service.buildAttentionItems(rows);

        assertEquals(SESSION_DATE, items.get(0).getSessionDate());
        assertTrue(items.get(0).getAttentionMessage().contains("vẫn dùng được gym"));
    }

    /** Dữ liệu mẫu giống kết quả DAO; cố ý cho khách trùng tên để kiểm tra gom bằng mã. */
    private PtClientPackageView row(int customerId, int subscriptionId, Package.Type type,
            LocalDate sessionDate, boolean hasMembership, boolean frozen) {
        return new PtClientPackageView(
                customerId,
                "Khách trùng tên",
                subscriptionId,
                type,
                sessionDate,
                hasMembership,
                frozen);
    }
}
