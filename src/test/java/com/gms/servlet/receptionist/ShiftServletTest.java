package com.gms.servlet.receptionist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test kiểm thử các hằng số và hàm tiện ích trong ShiftServlet (Màn #39 - UC-25).
 */
class ShiftServletTest {

    @Test
    @DisplayName("ShiftServlet: Kiểm tra các hằng số hành động và view")
    void testActionConstants() {
        assertEquals("receptionist/shift", ShiftServlet.VIEW_NAME);
        assertEquals("openShift", ShiftServlet.ACTION_OPEN_SHIFT);
        assertEquals("closeShift", ShiftServlet.ACTION_CLOSE_SHIFT);
        assertEquals("confirmTransfer", ShiftServlet.ACTION_CONFIRM_TRANSFER);
        assertEquals("cancelOrder", ShiftServlet.ACTION_CANCEL_ORDER);
    }

    @Test
    @DisplayName("ShiftServlet.cleanNumericCash: Loại bỏ phân cách phần nghìn và ký tự lạ")
    void testCleanNumericCash() {
        assertEquals("2600000", ShiftServlet.cleanNumericCash("2.600.000"));
        assertEquals("1500000", ShiftServlet.cleanNumericCash("1,500,000"));
        assertEquals("500000", ShiftServlet.cleanNumericCash(" 500.000 đ "));
        assertEquals("0", ShiftServlet.cleanNumericCash(""));
        assertEquals("0", ShiftServlet.cleanNumericCash(null));
        assertEquals("0", ShiftServlet.cleanNumericCash("abc"));
        assertEquals("12345", ShiftServlet.cleanNumericCash("12345"));
    }
}
