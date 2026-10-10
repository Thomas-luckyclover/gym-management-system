package com.gms.service;

import com.gms.dto.PtCommissionSummaryView;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Kiểm tra mẫu số và tỷ lệ của khối kết quả tháng, không cần database. */
class PtCommissionSummaryServiceTest {

    private final PtDashboardService service = new PtDashboardService();

    @Test
    void includesCancelledAndMembershipReasonsInResolvedRates() {
        // DAO giả định trả 8 + 2 + 1 + 1 + 2 = 14 buổi đã có kết quả.
        PtCommissionSummaryView summary = new PtCommissionSummaryView(
                8, 2, 1, 1, 2, 1_610_000L,
                3, 300_000L, 5, 500_000L);

        service.prepareCommissionSummary(summary);

        assertEquals(14, summary.getResolvedSessionCount());
        assertEquals(57.14, summary.getCheckedInRatePercent(), 0.001);
        assertEquals(14.29, summary.getNoCheckInRatePercent(), 0.001);
        assertEquals(7.14, summary.getNoMembershipRatePercent(), 0.001);
        assertEquals(7.14, summary.getMembershipFrozenRatePercent(), 0.001);
        assertEquals(14.29, summary.getCancelledRatePercent(), 0.001);
        assertEquals(1_610_000L, summary.getFinalizedCommissionAmount());
    }

    @Test
    void emptyMonthHasZeroRatesWithoutDivisionByZero() {
        PtCommissionSummaryView summary = new PtCommissionSummaryView(
                0, 0, 0, 0, 0, 0L,
                0, 0L, 0, 0L);

        service.prepareCommissionSummary(summary);

        assertEquals(0, summary.getResolvedSessionCount());
        assertEquals(0.0, summary.getCheckedInRatePercent());
        assertEquals(0.0, summary.getCancelledRatePercent());
    }
}
