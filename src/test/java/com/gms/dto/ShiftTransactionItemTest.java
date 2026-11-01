package com.gms.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test kiểm thử ShiftTransactionItem DTO (Màn #39 - UC-25).
 */
class ShiftTransactionItemTest {

    @Test
    @DisplayName("ShiftTransactionItem: Trạng thái Paid với tiền mặt")
    void testPaidCashItem() {
        LocalDateTime now = LocalDateTime.now();
        ShiftTransactionItem item = new ShiftTransactionItem(
                1, "DH-2026-0001", "Trần Văn A", "0912345678", "Gói 1 Tháng",
                now, "Cash", 800000L, "Paid", null, null, null,
                true, false);

        assertTrue(item.isPaid());
        assertFalse(item.isPending());
        assertFalse(item.isCancelled());
        assertTrue(item.isCash());
        assertFalse(item.isTransfer());
        assertEquals("Đã thanh toán", item.getStatusDisplayName());
        assertEquals("Tiền mặt", item.getPaymentMethodDisplayName());
        assertEquals("badge-ok", item.getStatusBadgeClass());
        assertTrue(item.isCanCancel());
        assertFalse(item.isCanConfirmTransfer());
    }

    @Test
    @DisplayName("ShiftTransactionItem: Trạng thái Pending với chuyển khoản")
    void testPendingTransferItem() {
        LocalDateTime now = LocalDateTime.now();
        ShiftTransactionItem item = new ShiftTransactionItem(
                2, "DH-2026-0002", "Lê Thị B", "0987654321", "Gói 3 Tháng",
                now, "Transfer", 2100000L, "Pending", null, null, null,
                true, true);

        assertFalse(item.isPaid());
        assertTrue(item.isPending());
        assertFalse(item.isCancelled());
        assertFalse(item.isCash());
        assertTrue(item.isTransfer());
        assertEquals("Chờ xác nhận", item.getStatusDisplayName());
        assertEquals("Chuyển khoản", item.getPaymentMethodDisplayName());
        assertEquals("badge-warn", item.getStatusBadgeClass());
        assertTrue(item.isCanConfirmTransfer());
    }

    @Test
    @DisplayName("ShiftTransactionItem: Trạng thái Cancelled do Lễ tân hủy tại quầy (CBR-11)")
    void testCancelledByReceptionistItem() {
        LocalDateTime now = LocalDateTime.now();
        ShiftTransactionItem item = new ShiftTransactionItem(
                3, "DH-2026-0003", "Hoàng Văn C", "0900112233", "Vé Ngày",
                now, "Cash", 100000L, "Cancelled", "CancelledByReceptionist", "Khách chọn nhầm ngày", now,
                false, false);

        assertTrue(item.isCancelled());
        assertEquals("Đã hủy", item.getStatusDisplayName());
        assertEquals("badge-danger", item.getStatusBadgeClass());
        assertTrue(item.isCancelledByReceptionist());
        assertEquals("Khách chọn nhầm ngày", item.getCancelReason());
        assertFalse(item.isCanCancel());
    }
}
