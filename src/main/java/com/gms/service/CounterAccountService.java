package com.gms.service;

import com.gms.dao.AccountDAO;
import com.gms.integration.EmailGateway;
import com.gms.integration.EmailSendException;
import com.gms.util.PasswordUtil;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Random;

/**
 * Tạo TÀI KHOẢN ĐĂNG NHẬP cho hồ sơ khách mà Lễ tân vừa tạo tại quầy, rồi gửi MẬT KHẨU TẠM qua email.
 *
 * Thay luật cũ CBR-31 (tài khoản tại quầy không có mật khẩu, không gửi email, khách phải qua Quên mật khẩu) —
 * nhóm chốt ngày 31/10/2026 (docs/notes.md). Khách đăng nhập bằng email / SĐT + mật khẩu tạm, đổi mật khẩu sau
 * ở Tài khoản → Mật khẩu (không ép đổi, không thêm cột DB).
 *
 * AI GỌI: CounterSaleService.quickCreateCustomer, TRONG transaction đang tạo Person + Customer.
 * Gửi email TRƯỚC khi transaction commit: gửi lỗi → người gọi rollback → không lưu gì (giống quy tắc gửi mã xác thực).
 */
public class CounterAccountService {

    /** Độ dài mật khẩu tạm: đủ DBR-02 (8–64 ký tự, có chữ + số). */
    static final int TEMP_PASSWORD_LENGTH = 10;

    // Bỏ các ký tự dễ đọc nhầm khi khách gõ lại từ email: 0/O/o, 1/l/I.
    private static final String LETTERS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";

    private final AccountDAO accountDAO = new AccountDAO();
    private final EmailGateway emailGateway = new EmailGateway();
    private final Random random = new SecureRandom();

    /**
     * Tạo tài khoản Active cho 1 Person + gửi mật khẩu tạm.
     *
     * @param conn         connection của transaction tạo hồ sơ (người gọi commit / rollback)
     * @param personId     Persons.PersonId vừa tạo (hồ sơ Self, đã có email)
     * @param email        email nhận mật khẩu tạm, đã kiểm tra định dạng + không trùng ở người gọi
     * @param fullName     họ tên khách, in lời chào trong email
     * @param customerCode mã KH-nnnn, in trong email để khách đối chiếu với lễ tân
     * @return AccountId vừa tạo
     * @throws SQLException       lỗi ghi Accounts → người gọi rollback
     * @throws EmailSendException gửi email lỗi / quá 10 giây → người gọi rollback, báo MSG04
     */
    public int createWithTempPassword(Connection conn, int personId, String email, String fullName, String customerCode)
            throws SQLException, EmailSendException {
        // B1: sinh mật khẩu tạm, chỉ lưu bản băm BCrypt (không lưu / không log mật khẩu thô)
        String tempPassword = generateTempPassword(random);
        int accountId = accountDAO.insert(conn, personId, PasswordUtil.hashPassword(tempPassword));

        // B2: gửi email; lỗi thì ném ra để người gọi rollback cả hồ sơ lẫn tài khoản
        emailGateway.sendTemporaryPassword(email, fullName, customerCode, tempPassword);
        return accountId;
    }

    /**
     * Sinh mật khẩu tạm ngẫu nhiên: 10 ký tự, chắc chắn có ít nhất 1 chữ cái và 1 chữ số (DBR-02).
     * VD "kT4mpX8waQ". Tách riêng + nhận Random để test được.
     */
    static String generateTempPassword(Random random) {
        StringBuilder password = new StringBuilder(TEMP_PASSWORD_LENGTH);
        // B1: 1 chữ số + 1 chữ cái bắt buộc, phần còn lại lấy chung 2 bộ ký tự
        password.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        password.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
        String all = LETTERS + DIGITS;
        while (password.length() < TEMP_PASSWORD_LENGTH) {
            password.append(all.charAt(random.nextInt(all.length())));
        }
        // B2: xáo trộn để chữ số bắt buộc không luôn đứng đầu
        for (int i = password.length() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char swap = password.charAt(i);
            password.setCharAt(i, password.charAt(j));
            password.setCharAt(j, swap);
        }
        return password.toString();
    }

    /**
     * Che email để báo cho lễ tân mà không lộ đầy đủ địa chỉ trên màn hình quầy.
     * VD "an.nguyen95@gmail.com" → "an***@gmail.com".
     */
    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String keep = name.length() <= 2 ? name.substring(0, 1) : name.substring(0, 2);
        return keep + "***" + email.substring(at);
    }
}
