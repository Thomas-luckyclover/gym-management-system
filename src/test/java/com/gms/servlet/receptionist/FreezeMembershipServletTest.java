package com.gms.servlet.receptionist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test kiểm thử các hằng số hành động và ánh xạ view trong FreezeMembershipServlet (Màn #34 - UC-18, BF-09).
 */
class FreezeMembershipServletTest {

    @Test
    @DisplayName("FreezeMembershipServlet: Kiểm tra các hằng số view và action")
    void testActionConstants() {
        assertEquals("receptionist/freeze-membership", FreezeMembershipServlet.VIEW_NAME);
        assertEquals("applyFreeze", FreezeMembershipServlet.ACTION_APPLY_FREEZE);
        assertEquals("cancelFreezeEarly", FreezeMembershipServlet.ACTION_CANCEL_FREEZE_EARLY);
        assertEquals("previewSessions", FreezeMembershipServlet.ACTION_PREVIEW_SESSIONS);
    }
}
