package com.gms.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CheckIn DTO Tests (CheckInLogItem & CheckInLookupResult)")
class CheckInDTOTest {

    @Test
    @DisplayName("CheckInLogItem correctly stores and retrieves attendance record fields")
    void testCheckInLogItemGetters() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 14, 30);
        CheckInLogItem item = new CheckInLogItem(
                101,
                "Nguyen Van A",
                "CUST-001",
                now,
                "Gold Membership 12M",
                true
        );

        assertEquals(101, item.getCheckInId());
        assertEquals("Nguyen Van A", item.getFullName());
        assertEquals("CUST-001", item.getCustomerCode());
        assertEquals(now, item.getCheckedInAt());
        assertEquals("Gold Membership 12M", item.getPackageName());
        assertTrue(item.isPhotoVerified());
    }

    @Test
    @DisplayName("CheckInLookupResult.notFound factory creates properly formatted denied result")
    void testCheckInLookupResultNotFound() {
        CheckInLookupResult result = CheckInLookupResult.notFound("Không tìm thấy hội viên");

        assertNotNull(result);
        assertEquals(CheckInLookupResult.Decision.NOT_FOUND, result.getDecision());
        assertEquals("danger", result.getDecision().getAlertType());
        assertEquals("Không tìm thấy hồ sơ", result.getDecision().getTitle());
        assertEquals("Không tìm thấy hội viên", result.getMessage());
        assertFalse(result.isCanCheckIn());
    }

    @Test
    @DisplayName("CheckInLookupResult Decision enum correctly maps alertType adhering to GBR-28")
    void testDecisionAlertTypesAdheringToGBR28() {
        // ok alerts
        assertEquals("ok", CheckInLookupResult.Decision.ELIGIBLE.getAlertType());
        assertEquals("ok", CheckInLookupResult.Decision.ELIGIBLE_DAYPASS.getAlertType());

        // warn alerts
        assertEquals("warn", CheckInLookupResult.Decision.WARN_MISSING_PHOTO.getAlertType());

        // danger alerts
        assertEquals("danger", CheckInLookupResult.Decision.DENIED_FROZEN.getAlertType());
        assertEquals("danger", CheckInLookupResult.Decision.DENIED_EXPIRED.getAlertType());
        assertEquals("danger", CheckInLookupResult.Decision.DENIED_DAYPASS_USED.getAlertType());
        assertEquals("danger", CheckInLookupResult.Decision.DENIED_PT_ONLY.getAlertType());
        assertEquals("danger", CheckInLookupResult.Decision.DENIED_NO_PACKAGE.getAlertType());
        assertEquals("danger", CheckInLookupResult.Decision.DENIED_DELETION_PENDING.getAlertType());
        assertEquals("danger", CheckInLookupResult.Decision.NOT_FOUND.getAlertType());
    }

    @Test
    @DisplayName("CheckInLookupResult stores customer and subscription state correctly")
    void testCheckInLookupResultState() {
        CheckInLookupResult result = new CheckInLookupResult();
        result.setCustomerId(10);
        result.setPersonId(20);
        result.setCustomerCode("CUST-010");
        result.setFullName("Le Thi B");
        result.setPhone("0912345678");
        result.setAvatar("/uploads/avatar.jpg");
        result.setCheckInPhoto("/uploads/checkin.jpg");
        result.setCustomerStatus("Active");
        result.setHasPendingDeletion(false);

        result.setSubscriptionId(55);
        result.setSubscriptionType("Membership");
        result.setPackageName("Silver 3M");
        result.setStartDate(LocalDate.of(2026, 10, 1));
        result.setEndDate(LocalDate.of(2027, 1, 1));
        result.setSubscriptionStatus("Active");
        result.setFreezeEndDate(null);

        result.setHasPtPackage(true);
        result.setPtPackageName("PT Personal 10 Sessions");
        result.setPtRemainingSessions(8);
        result.setPtTotalSessions(10);

        result.setDecision(CheckInLookupResult.Decision.ELIGIBLE);
        result.setMessage("Hội viên hợp lệ");
        result.setCanCheckIn(true);
        result.setRequiresPhoto(false);

        assertEquals(10, result.getCustomerId());
        assertEquals(20, result.getPersonId());
        assertEquals("CUST-010", result.getCustomerCode());
        assertEquals("Le Thi B", result.getFullName());
        assertEquals("0912345678", result.getPhone());
        assertEquals("/uploads/avatar.jpg", result.getAvatar());
        assertEquals("/uploads/checkin.jpg", result.getCheckInPhoto());
        assertEquals("Active", result.getCustomerStatus());
        assertFalse(result.isHasPendingDeletion());

        assertEquals(55, result.getSubscriptionId());
        assertEquals("Membership", result.getSubscriptionType());
        assertEquals("Silver 3M", result.getPackageName());
        assertEquals(LocalDate.of(2026, 10, 1), result.getStartDate());
        assertEquals(LocalDate.of(2027, 1, 1), result.getEndDate());
        assertEquals("Active", result.getSubscriptionStatus());
        assertNull(result.getFreezeEndDate());

        assertTrue(result.isHasPtPackage());
        assertEquals("PT Personal 10 Sessions", result.getPtPackageName());
        assertEquals(8, result.getPtRemainingSessions());
        assertEquals(10, result.getPtTotalSessions());

        assertEquals(CheckInLookupResult.Decision.ELIGIBLE, result.getDecision());
        assertEquals("Hội viên hợp lệ", result.getMessage());
        assertTrue(result.isCanCheckIn());
        assertFalse(result.isRequiresPhoto());
    }
}
