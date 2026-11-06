package com.gms.servlet.pt;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ô chọn tháng của khối hoa hồng: ?month= sai hoặc ngoài 12 tháng gần nhất → tháng hiện tại. */
class PtDashboardServletTest {
    private static final YearMonth CURRENT = YearMonth.of(2026, 11);

    @Test
    void missingOrInvalidMonthFallsBackToCurrent() {
        assertEquals(CURRENT, PtDashboardServlet.resolveMonth(null, CURRENT));
        assertEquals(CURRENT, PtDashboardServlet.resolveMonth("", CURRENT));
        assertEquals(CURRENT, PtDashboardServlet.resolveMonth("abc", CURRENT));
    }

    @Test
    void futureMonthFallsBackToCurrent() {
        assertEquals(CURRENT, PtDashboardServlet.resolveMonth("2026-12", CURRENT));
    }

    @Test
    void monthOlderThanElevenMonthsFallsBackToCurrent() {
        assertEquals(CURRENT, PtDashboardServlet.resolveMonth("2025-10", CURRENT));
        assertEquals(CURRENT, PtDashboardServlet.resolveMonth("2025-11", CURRENT));
    }

    @Test
    void monthInRangeIsKept() {
        assertEquals(YearMonth.of(2026, 8), PtDashboardServlet.resolveMonth("2026-08", CURRENT));
        assertEquals(YearMonth.of(2025, 12), PtDashboardServlet.resolveMonth("2025-12", CURRENT));
    }

    @Test
    void optionsListTwelveMonthsNewestFirst() {
        List<PtDashboardServlet.MonthOption> options = PtDashboardServlet.buildMonthOptions(CURRENT);

        assertEquals(12, options.size());
        assertEquals(new PtDashboardServlet.MonthOption("2026-11", "11/2026"), options.get(0));
        assertEquals(new PtDashboardServlet.MonthOption("2025-12", "12/2025"), options.get(11));
    }
}
