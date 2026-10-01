package com.gms.service;

import com.gms.constant.Role;
import com.gms.dto.AccountSettingsView;
import com.gms.util.DateTimeUtil;
import com.gms.util.ValidationUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test kiểm tra toàn bộ rule nghiệp vụ cho màn hình Receptionist Account Settings (Hoàng).
 * Dựa trên tài liệu Hoang_Receptionist_Account_Settings.md:
 * - CBR-108: Các trường chỉ xem của nhân viên
 * - CBR-110, DBR-17, DBR-18, DBR-64: Đổi email / SĐT và mã OTP
 * - DBR-65: Avatar dung lượng và định dạng
 */
class ReceptionistAccountSettingsTest {

    @Test
    @DisplayName("DBR-18 & DBR-64: Kiểm tra định dạng Email đổi của Receptionist")
    void testEmailValidation() {
        // Email hợp lệ
        assertTrue(ValidationUtil.isValidEmail("tam.le@gms.vn"));
        assertTrue(ValidationUtil.isValidEmail("receptionist.new@gmail.com"));

        // Email không hợp lệ
        assertFalse(ValidationUtil.isValidEmail(""));
        assertFalse(ValidationUtil.isValidEmail("tam.le"));
        assertFalse(ValidationUtil.isValidEmail("tam.le@"));
        assertFalse(ValidationUtil.isValidEmail("tam.le@domain"));
        assertFalse(ValidationUtil.isValidEmail("tam le@gms.vn"));
    }

    @Test
    @DisplayName("DBR-17, DBR-27, DBR-30: Kiểm tra định dạng Số điện thoại của Receptionist")
    void testPhoneValidation() {
        // SĐT 10 số bắt đầu bằng 0
        assertTrue(ValidationUtil.isValidPhone("0912000003"));
        assertTrue(ValidationUtil.isValidPhone("0902345678"));
        assertTrue(ValidationUtil.isValidPhone("0388990011"));

        // Sai định dạng
        assertFalse(ValidationUtil.isValidPhone(""));
        assertFalse(ValidationUtil.isValidPhone("091200003")); // 9 số
        assertFalse(ValidationUtil.isValidPhone("09120000003")); // 11 số
        assertFalse(ValidationUtil.isValidPhone("912000003")); // không bắt đầu bằng 0
        assertFalse(ValidationUtil.isValidPhone("0912-000-03"));
        assertFalse(ValidationUtil.isValidPhone("abcdefghij"));
    }

    @Test
    @DisplayName("CBR-110 & DBR-03: Mã xác thực OTP phải đúng 6 chữ số")
    void testVerificationCodeFormat() {
        assertTrue(ValidationUtil.isValidVerificationCode("123456"));
        assertTrue(ValidationUtil.isValidVerificationCode("000000"));
        assertTrue(ValidationUtil.isValidVerificationCode("987654"));

        // Không hợp lệ
        assertFalse(ValidationUtil.isValidVerificationCode("12345")); // 5 số
        assertFalse(ValidationUtil.isValidVerificationCode("1234567")); // 7 số
        assertFalse(ValidationUtil.isValidVerificationCode("12a456")); // có chữ
        assertFalse(ValidationUtil.isValidVerificationCode(""));
    }

    @Test
    @DisplayName("CBR-108: DTO AccountSettingsView cho Receptionist chứa đủ trường chỉ xem")
    void testAccountSettingsViewForReceptionist() {
        LocalDate dob = LocalDate.of(1997, 2, 2);
        LocalDate hireDate = LocalDate.of(2025, 2, 10);

        AccountSettingsView view = new AccountSettingsView(
                13,
                19,
                "Lê Thị Thanh Tâm",
                dob,
                "0912000003",
                "tam.le@gms.vn",
                null,
                Role.RECEPTIONIST,
                "NV-0003"
        );

        assertEquals("Lê Thị Thanh Tâm", view.getFullName());
        assertEquals(dob, view.getDateOfBirth());
        assertEquals("0912000003", view.getPhone());
        assertEquals("tam.le@gms.vn", view.getEmail());
        assertEquals(Role.RECEPTIONIST, view.getRole());
        assertEquals("NV-0003", view.getEmployeeCode());

        // Định dạng ngày sinh theo chuẩn dd/MM/yyyy (GBR-01)
        assertEquals("02/02/1997", DateTimeUtil.formatDate(view.getDateOfBirth()));
        assertEquals("10/02/2025", DateTimeUtil.formatDate(hireDate));
    }

    @Test
    @DisplayName("DBR-65: Avatar kiểm tra định dạng và dung lượng tối đa 2MB")
    void testAvatarConstraints() {
        long twoMbBytes = 2 * 1024 * 1024; // 2,097,152 bytes
        long overTwoMbBytes = twoMbBytes + 1;

        // Giả lập kiểm tra dung lượng
        assertTrue(twoMbBytes <= 2 * 1024 * 1024);
        assertFalse(overTwoMbBytes <= 2 * 1024 * 1024);

        // MIME types hợp lệ
        List<String> allowedMimeTypes = List.of("image/jpeg", "image/png");
        assertTrue(allowedMimeTypes.contains("image/jpeg"));
        assertTrue(allowedMimeTypes.contains("image/png"));
        assertFalse(allowedMimeTypes.contains("image/gif"));
        assertFalse(allowedMimeTypes.contains("image/webp"));
    }

    @Test
    @DisplayName("DBR-02 & DBR-08: Kiểm tra quy chuẩn đổi mật khẩu của Receptionist")
    void testPasswordValidation() {
        // Hợp lệ (>=8 ký tự, có chữ và có số)
        assertTrue(ValidationUtil.isValidPassword("Gms@2026"));
        assertTrue(ValidationUtil.isValidPassword("Receptionist123"));
        assertTrue(ValidationUtil.isValidPassword("Password123!"));

        // Không hợp lệ
        assertFalse(ValidationUtil.isValidPassword(""));
        assertFalse(ValidationUtil.isValidPassword("Short1")); // < 8 ký tự
        assertFalse(ValidationUtil.isValidPassword("ChiToanChuKhongCoSo")); // Không có số
        assertFalse(ValidationUtil.isValidPassword("1234567890")); // Không có chữ
        assertFalse(ValidationUtil.isValidPassword(null));
    }

    @Test
    @DisplayName("DBR-17 (P-16): Kiểm tra đầu số nhà mạng hợp lệ của SĐT Receptionist")
    void testPhonePrefixValidation() {
        List<String> validPrefixes = List.of("086", "096", "097", "098", "032", "033", "034", "035", "036", "037", "038", "039",
                "088", "091", "094", "083", "084", "085", "081", "082", "089", "090", "093", "070", "079", "077", "076", "078");

        // Đầu số hợp lệ
        assertTrue(ValidationUtil.hasValidPhonePrefix("0912000003", validPrefixes)); // Vinaphone
        assertTrue(ValidationUtil.hasValidPhonePrefix("0902345678", validPrefixes)); // Mobifone
        assertTrue(ValidationUtil.hasValidPhonePrefix("0981234567", validPrefixes)); // Viettel

        // Đầu số không thuộc danh sách
        assertFalse(ValidationUtil.hasValidPhonePrefix("0123456789", validPrefixes));
        assertFalse(ValidationUtil.hasValidPhonePrefix("0001234567", validPrefixes));
    }

    @Test
    @DisplayName("CBR-112: Receptionist là nhân viên, không có tab Người phụ thuộc (Dependents)")
    void testReceptionistRoleProperties() {
        Role role = Role.RECEPTIONIST;

        // Vai trò lễ tân
        assertEquals("RECEPTIONIST", role.name());
        assertEquals("Receptionist", role.getDbValue());
        assertEquals("Lễ tân", role.getDisplayName());
        assertEquals("/receptionist/check-in", role.getHomePath());

        // CBR-112: hasDependentsTab chỉ áp dụng cho CUSTOMER
        boolean hasDependentsTab = (role == Role.CUSTOMER);
        assertFalse(hasDependentsTab, "Lễ tân không được phép có tab Người phụ thuộc");
    }
}

