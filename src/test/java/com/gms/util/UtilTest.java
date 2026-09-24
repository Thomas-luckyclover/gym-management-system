package com.gms.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Kiểm tra các hàm thuần trong util/ bám đúng luật SRS. Chạy: mvn test
 * Ai sửa ValidationUtil/MoneyUtil/DateTimeUtil phải chạy lại và thêm test cho trường hợp mới.
 */
class UtilTest {

    // ===== ValidationUtil =====

    @Test
    void phoneMustBeTenDigitsStartingWithZero() {
        assertTrue(ValidationUtil.isValidPhone("0912345678"));
        assertFalse(ValidationUtil.isValidPhone("912345678"));
        assertFalse(ValidationUtil.isValidPhone("09123456789"));
        assertFalse(ValidationUtil.isValidPhone("0912 345 678"));
        assertTrue(ValidationUtil.hasValidPhonePrefix("0912345678", List.of("091", "090")));
        assertFalse(ValidationUtil.hasValidPhonePrefix("0112345678", List.of("091", "090")));
    }

    @Test
    void passwordNeedsLetterDigitAndLength() {
        assertTrue(ValidationUtil.isValidPassword("abc12345"));
        assertFalse(ValidationUtil.isValidPassword("abcdefgh"));
        assertFalse(ValidationUtil.isValidPassword("12345678"));
        assertFalse(ValidationUtil.isValidPassword("ab1"));
    }

    @Test
    void fullNameAcceptsVietnameseLetters() {
        assertTrue(ValidationUtil.isValidFullName("Nguyễn Thị Ánh-Dương"));
        assertTrue(ValidationUtil.isValidFullName("O'Neil Trần"));
        assertFalse(ValidationUtil.isValidFullName("Nguyễn Văn 2"));
        assertFalse(ValidationUtil.isValidFullName("A"));
        assertFalse(ValidationUtil.isValidFullName("' - '"));
    }

    @Test
    void dateOfBirthNotInFutureAndAgeAtMostHundred() {
        LocalDate today = LocalDate.of(2026, 9, 24);
        assertTrue(ValidationUtil.isValidDateOfBirth(LocalDate.of(2000, 1, 1), today));
        assertFalse(ValidationUtil.isValidDateOfBirth(LocalDate.of(2026, 9, 25), today));
        assertFalse(ValidationUtil.isValidDateOfBirth(LocalDate.of(1900, 1, 1), today));
    }

    @Test
    void otherFormats() {
        assertTrue(ValidationUtil.isValidEmail("an.nguyen@gmail.com"));
        assertFalse(ValidationUtil.isValidEmail("an.nguyen@gmail"));
        assertTrue(ValidationUtil.isValidVerificationCode("048213"));
        assertFalse(ValidationUtil.isValidVerificationCode("48213"));
        assertTrue(ValidationUtil.isValidVoucherCode("WELCOME10"));
        assertFalse(ValidationUtil.isValidVoucherCode("welcome10"));
        assertTrue(ValidationUtil.isValidReason("Khách đổi ý"));
        assertFalse(ValidationUtil.isValidReason("abc"));
        assertTrue(ValidationUtil.isValidImageFile("image/png", 1024));
        assertFalse(ValidationUtil.isValidImageFile("image/gif", 1024));
    }

    // ===== MoneyUtil =====

    @Test
    void moneyFormatAndVatBreakdown() {
        assertEquals("1.250.000đ", MoneyUtil.formatMoney(1_250_000));
        assertEquals("0đ", MoneyUtil.formatMoney(0));
        // DBR-57: 1.100.000 gồm VAT 10% → VAT = 1.100.000 × 10 / 110 = 100.000
        assertEquals(100_000, MoneyUtil.calculateVatAmount(1_100_000, 10));
        assertEquals(1_000_000, MoneyUtil.calculatePreTaxAmount(1_100_000, 100_000));
        assertEquals(399_000, MoneyUtil.calculatePercentDiscount(3_990_000, 10));
    }

    // ===== DateTimeUtil =====

    @Test
    void inclusiveEndDateFollowsGbr03() {
        assertEquals(LocalDate.of(2026, 10, 5), DateTimeUtil.calculateEndDateByMonths(LocalDate.of(2026, 9, 5), 1));
        assertEquals(LocalDate.of(2027, 2, 28), DateTimeUtil.calculateEndDateByMonths(LocalDate.of(2027, 1, 31), 1));
        assertEquals("05/09/2026", DateTimeUtil.formatDate(LocalDate.of(2026, 9, 5)));
        assertEquals(LocalDate.of(2026, 9, 5), DateTimeUtil.parseHtmlDate("2026-09-05"));
        assertEquals(null, DateTimeUtil.parseHtmlDate("05/09/2026"));
    }

    // ===== SqlUtil =====

    @Test
    void likePatternEscapesWildcards() {
        assertEquals("%nguyen%", SqlUtil.toContainsPattern("  nguyen "));
        assertEquals("%50\\%%", SqlUtil.toContainsPattern("50%"));
        assertEquals("%", SqlUtil.toContainsPattern(""));
    }

    // ===== SecureCodeUtil =====

    @Test
    void verificationCodeIsSixDigits() {
        for (int i = 0; i < 200; i++) {
            String code = SecureCodeUtil.generateVerificationCode();
            assertTrue(ValidationUtil.isValidVerificationCode(code));
        }
    }

    @Test
    void tokenHashMatchesSeedData() {
        // Khớp TokenHash của tài khoản 113 trong database/4_Phase1_Seed.sql
        assertEquals("f33b3f90a86e54ffdb250cb87b3786c9a09ed291abe9f94a62fa395a419c3085",
                SecureCodeUtil.sha256Hex("setup-nhung-phase1-2026"));
        assertEquals(64, SecureCodeUtil.sha256Hex(SecureCodeUtil.generateUrlToken()).length());
    }
}
