package com.gms.integration;

/**
 * Lỗi khi gửi email qua SMTP thất bại (sai app password, mất mạng, Gmail từ chối...).
 *
 * Là checked exception để Service BẮT BUỘC phải xử lý: theo SRS API-03, gửi mã thất bại thì
 * KHÔNG tạo tài khoản và báo người dùng thử lại (Messages.MSG04_SYSTEM_BUSY).
 */
public class EmailSendException extends Exception {

    private static final long serialVersionUID = 1L;

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
