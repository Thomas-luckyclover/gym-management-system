package com.gms.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Thẻ tài khoản: mã hiển thị theo vai trò (mã gốc trong DB giữ nguyên), chữ vai trò, chữ viết tắt. */
class UserBadgeServiceTest {

    @Test
    void displayCodeSwapsPrefixByRoleAndKeepsNumber() {
        assertEquals("PT-0005", UserBadgeService.displayCode("PT", "NV-0005"));
        assertEquals("LT-0003", UserBadgeService.displayCode("Receptionist", "NV-0003"));
        assertEquals("QL-0002", UserBadgeService.displayCode("Manager", "NV-0002"));
        assertEquals("QT-0001", UserBadgeService.displayCode("Admin", "NV-0001"));
        assertEquals("KH-1006", UserBadgeService.displayCode("Customer", "KH-1006"));
        assertNull(UserBadgeService.displayCode("PT", null));
    }

    @Test
    void roleLabelAndInitials() {
        assertEquals("Lễ tân", UserBadgeService.roleLabel("Receptionist"));
        assertEquals("Huấn luyện viên", UserBadgeService.roleLabel("PT"));
        assertEquals("LT", UserBadgeService.initials("Lê Thị Thanh Tâm"));
        assertEquals("Đ", UserBadgeService.initials("Đạt"));
        assertEquals("?", UserBadgeService.initials(" "));
    }
}
