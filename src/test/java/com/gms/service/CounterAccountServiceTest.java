package com.gms.service;

import com.gms.util.ValidationUtil;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mật khẩu tạm gửi cho tài khoản tạo tại quầy (thay CBR-31) + cách che email báo cho lễ tân. */
class CounterAccountServiceTest {

    /** Mỗi mật khẩu tạm đủ DBR-02 (8–64 ký tự, có chữ + số) và không có ký tự dễ nhầm 0 O o 1 l I. */
    @RepeatedTest(200)
    void tempPasswordPassesPasswordRuleAndAvoidsLookAlikes() {
        String password = CounterAccountService.generateTempPassword(new SecureRandom());

        assertEquals(CounterAccountService.TEMP_PASSWORD_LENGTH, password.length());
        assertTrue(ValidationUtil.isValidPassword(password), password);
        assertFalse(password.matches(".*[0Oo1lI].*"), password);
    }

    /** Cùng seed → cùng kết quả (hàm chỉ phụ thuộc Random truyền vào); khác lượt → hầu như không trùng. */
    @Test
    void tempPasswordDependsOnlyOnRandom() {
        assertEquals(CounterAccountService.generateTempPassword(new Random(7)),
                CounterAccountService.generateTempPassword(new Random(7)));

        Set<String> seen = new HashSet<>();
        Random random = new SecureRandom();
        for (int i = 0; i < 500; i++) {
            seen.add(CounterAccountService.generateTempPassword(random));
        }
        assertEquals(500, seen.size());
    }

    @ParameterizedTest
    @CsvSource({
        "an.nguyen95@gmail.com, an***@gmail.com",
        "ab@gmail.com,          a***@gmail.com",
        "x@gms.vn,              x***@gms.vn",
        "khong-co-a-cong,       ***"
    })
    void maskEmailKeepsOnlyFirstLettersAndDomain(String email, String expected) {
        assertEquals(expected, CounterAccountService.maskEmail(email));
    }
}
