package com.gms.service;

import com.gms.dto.PtAttentionView;
import com.gms.dto.PtClientPackageView;
import com.gms.entity.Package;
import java.sql.SQLException;
import java.time.LocalDate;
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
    void validMembershipWithoutFreezeCreatesNoAttention() throws SQLException {
        PtClientPackageView valid = row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE, true, false);

        List<PtAttentionView> items = service.buildAttentionItems(List.of(valid), TODAY);

        assertTrue(items.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "2026-10-06, Đã hết hạn",
        "2026-10-10, Không bao phủ ngày tập",
        "2026-10-07, Không bao phủ ngày tập"
    })
    void missingCoverageIsExplainedRelativeToToday(LocalDate membershipEndDate,
            String expectedLabel) throws SQLException {
        PtClientPackageView missing = new PtClientPackageView(1, "Khách A", 101,
                Package.Type.PT_SINGLE, TODAY.plusDays(20), 6, SESSION_DATE,
                membershipEndDate, false, false, null);

        PtAttentionView item = service.buildAttentionItems(List.of(missing), TODAY).get(0);

        assertEquals("Cần membership", item.getAttentionTitle());
        assertEquals(expectedLabel, item.getMembershipStatusLabel());
        assertEquals("Cần membership hợp lệ cho buổi ngày 12/10/2026.", item.getAttentionMessage());
        assertEquals(membershipEndDate, item.getMembershipEndDate());
    }

    @Test
    void noMembershipHasNoFakeExpiryDate() throws SQLException {
        PtClientPackageView missing = row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE, false, false);

        PtAttentionView item = service.buildAttentionItems(List.of(missing), TODAY).get(0);

        assertEquals("Chưa có membership phù hợp", item.getMembershipStatusLabel());
        assertNull(item.getMembershipEndDate());
        assertNull(item.getFreezeEndDate());
    }

    @Test
    void freezeTakesPriorityEvenWhenMembershipDoesNotCoverDate() throws SQLException {
        PtClientPackageView frozen = row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE, false, true);

        PtAttentionView item = service.buildAttentionItems(List.of(frozen), TODAY).get(0);

        assertEquals("Membership đang bảo lưu", item.getAttentionTitle());
        assertEquals("Đang bảo lưu", item.getMembershipStatusLabel());
        assertEquals(TODAY.plusDays(10), item.getFreezeEndDate());
    }

    @Test
    void fiveUncoveredDatesBecomeOneAttentionForEarliestDate() throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_SINGLE, TODAY.plusDays(9), false, false),
                row(1, 101, Package.Type.PT_SINGLE, TODAY.plusDays(7), false, false),
                row(1, 101, Package.Type.PT_SINGLE, TODAY.plusDays(5), false, false),
                row(1, 101, Package.Type.PT_SINGLE, TODAY.plusDays(6), false, false),
                row(1, 101, Package.Type.PT_SINGLE, TODAY.plusDays(8), false, false));

        List<PtAttentionView> items = service.buildAttentionItems(rows, TODAY);

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
        assertEquals(expectedCount, service.buildAttentionItems(rows, TODAY).size());
    }

    @Test
    void twoProblemTypesForSamePackageAreNotMerged() throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE, false, false),
                row(1, 101, Package.Type.PT_SINGLE, SESSION_DATE.plusDays(1), true, true));

        List<PtAttentionView> items = service.buildAttentionItems(rows, TODAY);

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

        assertEquals(2, service.buildAttentionItems(rows, TODAY).size());
    }

    @Test
    void frozenCoupleCustomerDoesNotBlockValidPartnerInMessage() throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_COUPLE, SESSION_DATE, true, true),
                row(2, 101, Package.Type.PT_COUPLE, SESSION_DATE, true, false));

        List<PtAttentionView> items = service.buildAttentionItems(rows, TODAY);

        assertEquals(1, items.size());
        assertEquals("PT đôi", items.get(0).getPtSubscriptionTypeLabel());
        assertTrue(items.get(0).getAttentionMessage().contains("Khách trùng tên vẫn dùng được gym"));
    }

    @ParameterizedTest
    @CsvSource({"true, true", "false, false", "true, false"})
    void bothUnusableCoupleCustomersAreExplained(boolean firstFrozen,
            boolean secondFrozen) throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_COUPLE, SESSION_DATE, firstFrozen, firstFrozen),
                row(2, 101, Package.Type.PT_COUPLE, SESSION_DATE, secondFrozen, secondFrozen));

        List<PtAttentionView> items = service.buildAttentionItems(rows, TODAY);

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

        assertThrows(SQLException.class, () -> service.buildAttentionItems(rows, TODAY));
    }

    @Test
    void partnerIsCheckedOnSelectedEarliestDate() throws SQLException {
        List<PtClientPackageView> rows = List.of(
                row(1, 101, Package.Type.PT_COUPLE, SESSION_DATE.plusDays(2), false, false),
                row(2, 101, Package.Type.PT_COUPLE, SESSION_DATE.plusDays(2), false, false),
                row(1, 101, Package.Type.PT_COUPLE, SESSION_DATE, false, false),
                row(2, 101, Package.Type.PT_COUPLE, SESSION_DATE, true, false));

        List<PtAttentionView> items = service.buildAttentionItems(rows, TODAY);

        assertEquals(SESSION_DATE, items.get(0).getSessionDate());
        assertTrue(items.get(0).getAttentionMessage().contains("vẫn dùng được gym"));
    }

    /** Dữ liệu mẫu giống kết quả DAO; cố ý cho khách trùng tên để kiểm tra gom bằng mã. */
    private PtClientPackageView row(int customerId, int subscriptionId, Package.Type type,
            LocalDate sessionDate, boolean hasMembership, boolean frozen) {
        return new PtClientPackageView(customerId, "Khách trùng tên", subscriptionId, type,
                TODAY.plusDays(20), 6, sessionDate, hasMembership ? TODAY.plusDays(30) : null,
                hasMembership, frozen, frozen ? TODAY.plusDays(10) : null);
    }
}
