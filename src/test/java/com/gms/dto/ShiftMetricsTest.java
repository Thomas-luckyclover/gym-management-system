package com.gms.dto;

import com.gms.entity.Shift;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test kiểm thử các DTO liên quan đến Ca làm việc (Màn #39 - UC-25).
 * Kiểm thử tính đúng đắn của ShiftMetrics và ShiftDetailView.
 */
class ShiftMetricsTest {

    @Test
    @DisplayName("ShiftMetrics: Tính toán đúng các chỉ số KPI và cờ hasPendingOrders")
    void testShiftMetrics_Calculations() {
        ShiftMetrics metrics = new ShiftMetrics(2000000L, 2, 3000000L, 3, 5000000L, 5, 1);

        assertEquals(2000000L, metrics.getCashTotal());
        assertEquals(2, metrics.getCashCount());
        assertEquals(3000000L, metrics.getBankTotal());
        assertEquals(3, metrics.getBankCount());
        assertEquals(5000000L, metrics.getTotalRevenue());
        assertEquals(5, metrics.getTotalValidCount());
        assertEquals(1, metrics.getPendingCount());

        assertTrue(metrics.hasPendingOrders(), "Phải báo còn đơn Pending khi pendingCount > 0");
        assertTrue(metrics.hasTransactions(), "Phải báo đã có giao dịch");
        assertEquals(40.0, metrics.getCashPercentage(), 0.01);
        assertEquals(60.0, metrics.getBankPercentage(), 0.01);
    }

    @Test
    @DisplayName("ShiftMetrics: Xử lý an toàn khi ca chưa có giao dịch nào (zero revenue)")
    void testShiftMetrics_EmptyMetrics() {
        ShiftMetrics empty = new ShiftMetrics(0L, 0, 0L, 0, 0L, 0, 0);

        assertFalse(empty.hasPendingOrders());
        assertFalse(empty.hasTransactions());
        assertEquals(0.0, empty.getCashPercentage(), 0.01);
        assertEquals(0.0, empty.getBankPercentage(), 0.01);
    }

    @Test
    @DisplayName("ShiftDetailView: Kiểm tra cờ hasPendingOrders và canOpen")
    void testShiftDetailView_Flags() {
        Shift shift = new Shift(1, LocalDate.now(), Shift.ShiftType.MORNING,
                null, null, Shift.Status.NOT_OPENED, null, null, null, null, null);
        ShiftMetrics metrics = new ShiftMetrics(1000000L, 1, 0L, 0, 1000000L, 1, 0);
        PageResult<ShiftTransactionItem> page = new PageResult<>(Collections.emptyList(), 1, 10, 0);

        ShiftDetailView view = new ShiftDetailView(shift, "Nguyễn Văn A", null,
                metrics, page, Collections.emptyList(), List.of(shift),
                null, "all", "all", true, null);

        assertSame(shift, view.getShift());
        assertEquals("Nguyễn Văn A", view.getOpenedByEmployeeName());
        assertNull(view.getClosedByEmployeeName());
        assertTrue(view.isCanOpen());
        assertNull(view.getShiftNotOpenedReason());
        assertFalse(view.hasPendingOrders());
    }
}
