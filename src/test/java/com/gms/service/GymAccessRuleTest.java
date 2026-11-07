package com.gms.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Luật "vào gym được" (CBR-117, chốt 06/11/2026) mà JOB-04 dùng — AutoCloseSessionService.canUseGym:
 * (gói thành viên phủ ngày VÀ không bảo lưu) HOẶC vé ngày phủ ngày. Bảo lưu không chặn vé ngày.
 */
class GymAccessRuleTest {

    /** Gói thành viên bình thường → vào được. */
    @Test
    void membershipNotFrozen() {
        assertTrue(AutoCloseSessionService.canUseGym(true, false, false));
    }

    /** Gói thành viên đang bảo lưu, không vé ngày → không vào được (buổi PT chốt Missed / MembershipFrozen). */
    @Test
    void frozenWithoutDayPass() {
        assertFalse(AutoCloseSessionService.canUseGym(true, false, true));
    }

    /** Đang bảo lưu nhưng mua vé ngày → vào được (buổi PT xét check-in như thường). */
    @Test
    void frozenWithDayPass() {
        assertTrue(AutoCloseSessionService.canUseGym(true, true, true));
    }

    /** Chỉ có vé ngày → vào được (CBR-63). */
    @Test
    void dayPassOnly() {
        assertTrue(AutoCloseSessionService.canUseGym(false, true, false));
    }

    /** Không gói nào → không vào được (Missed / NoMembership). */
    @Test
    void nothing() {
        assertFalse(AutoCloseSessionService.canUseGym(false, false, false));
    }
}
