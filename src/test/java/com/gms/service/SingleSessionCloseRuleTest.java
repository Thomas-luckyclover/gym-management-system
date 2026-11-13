package com.gms.service;

import com.gms.entity.Package;
import com.gms.entity.Session;
import com.gms.entity.Subscription;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JOB-04 chốt gói buổi lẻ (SingleSession) theo kết quả buổi — AutoCloseSessionService.singleSessionStatusAfter:
 * CheckedIn → Used, Missed → Expired, buổi bị hủy (GymClosed) → giữ gói. Gói PT nhiều buổi không đổi.
 */
class SingleSessionCloseRuleTest {

    /** Buổi lẻ đã check-in → gói Used. */
    @Test
    void checkedInMakesPackageUsed() {
        assertEquals(Subscription.Status.USED, AutoCloseSessionService.singleSessionStatusAfter(
                Package.Type.SINGLE_SESSION, Session.Status.CHECKED_IN));
    }

    /** Buổi lẻ vắng → gói Expired. */
    @Test
    void missedMakesPackageExpired() {
        assertEquals(Subscription.Status.EXPIRED, AutoCloseSessionService.singleSessionStatusAfter(
                Package.Type.SINGLE_SESSION, Session.Status.MISSED));
    }

    /** Buổi lẻ bị hủy vì phòng nghỉ → gói giữ nguyên. */
    @Test
    void cancelledKeepsPackage() {
        assertNull(AutoCloseSessionService.singleSessionStatusAfter(
                Package.Type.SINGLE_SESSION, Session.Status.CANCELLED));
    }

    /** Gói PT nhiều buổi → JOB-04 không đổi gói (JOB-02 lo hết hạn). */
    @Test
    void ptPackageUnchanged() {
        assertNull(AutoCloseSessionService.singleSessionStatusAfter(
                Package.Type.PT_SINGLE, Session.Status.CHECKED_IN));
        assertNull(AutoCloseSessionService.singleSessionStatusAfter(
                Package.Type.PT_COUPLE, Session.Status.MISSED));
    }
}
