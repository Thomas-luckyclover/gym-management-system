package com.gms.integration;

import com.gms.util.AppConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;

/**
 * Cổng gửi email — SRS 6.3 "Email Delivery (API-03)", actor "Email Gateway" trên Context Diagram.
 *
 * ===== TỔNG QUAN LUỒNG =====
 * GMS ──(SMTP, cổng 587, STARTTLS)──> smtp.gmail.com ──> hộp thư người nhận
 *
 * Dùng cho: mã xác thực đăng ký (UC-05), kích hoạt hồ sơ có sẵn (UC-Claim), khôi phục mật khẩu (UC-03),
 * link đặt mật khẩu cho tài khoản Lễ tân tạo tại quầy, báo cho email cũ khi đổi email (UC-04).
 * Hệ thống KHÔNG gửi SMS (GBR-15).
 *
 * ===== CẤU HÌNH (application.properties) =====
 * mail.smtp.host, mail.smtp.port, mail.username (địa chỉ Gmail gửi đi), mail.app-password (App Password 16 ký tự),
 * mail.from-name (tên hiển thị người gửi).
 * App Password KHÔNG phải mật khẩu Gmail thường — xem docs/integration-guide.md mục Email.
 *
 * ===== XỬ LÝ LỖI =====
 * Mọi lỗi gửi được gói thành EmailSendException (checked) để Service bắt buộc xử lý.
 * Service phải gửi email TRƯỚC khi commit dữ liệu (VD tạo tài khoản), gửi lỗi thì rollback.
 *
 * Được gọi từ Service, không gọi thẳng từ Servlet (agents.md 6.2 — tách logic tích hợp khỏi nghiệp vụ).
 */
public class EmailGateway {

    /** Thời gian chờ tối đa khi kết nối/gửi tới SMTP (ms) — tránh treo request nếu Gmail không phản hồi. */
    private static final String SMTP_TIMEOUT_MS = "10000";

    // ===== Các email nghiệp vụ =====

    /**
     * Gửi mã xác thực 6 số (đăng ký, kích hoạt hồ sơ, khôi phục mật khẩu, đổi email).
     * @param toEmail email người nhận
     * @param code mã 6 số (sinh bằng SecureCodeUtil.generateVerificationCode())
     * @param validMinutes số phút mã còn hiệu lực (P-15, đọc từ SystemConfig)
     * @param purpose mục đích hiển thị trong email, VD "đăng ký tài khoản", "khôi phục mật khẩu"
     * @throws EmailSendException gửi thất bại
     */
    public void sendVerificationCode(String toEmail, String code, int validMinutes, String purpose)
            throws EmailSendException {
        String subject = "[Super Gym] Mã xác thực " + purpose;
        String body = "<p>Xin chào,</p>"
                + "<p>Mã xác thực để " + purpose + " của bạn là:</p>"
                + "<p style=\"font-size:24px;font-weight:700;letter-spacing:4px\">" + code + "</p>"
                + "<p>Mã có hiệu lực trong " + validMinutes + " phút và chỉ dùng được 1 lần.</p>"
                + "<p>Nếu bạn không yêu cầu mã này, hãy bỏ qua email.</p>";
        sendHtmlEmail(toEmail, subject, body);
    }

    /**
     * Gửi link đặt mật khẩu cho tài khoản do Lễ tân tạo tại quầy (MSG38).
     * @param toEmail email người nhận
     * @param setPasswordLink link đầy đủ, VD https://.../gms/set-password?token=...
     * @param validMinutes số phút link còn hiệu lực (P-15)
     * @throws EmailSendException gửi thất bại
     */
    public void sendPasswordSetupLink(String toEmail, String setPasswordLink, int validMinutes)
            throws EmailSendException {
        String subject = "[Super Gym] Đặt mật khẩu cho tài khoản của bạn";
        String body = "<p>Xin chào,</p>"
                + "<p>Tài khoản Super Gym của bạn đã được tạo tại quầy. Bấm vào link dưới đây để đặt mật khẩu:</p>"
                + "<p><a href=\"" + setPasswordLink + "\">Đặt mật khẩu</a></p>"
                + "<p>Link có hiệu lực trong " + validMinutes + " phút.</p>";
        sendHtmlEmail(toEmail, subject, body);
    }

    /**
     * Gửi thông báo ngắn (VD báo cho email cũ khi đổi email đăng nhập — UC-04, MSG56).
     * @param toEmail email người nhận
     * @param subject tiêu đề (không cần tiền tố [Super Gym], tự thêm)
     * @param plainMessage nội dung 1 đoạn văn bản
     * @throws EmailSendException gửi thất bại
     */
    public void sendNotice(String toEmail, String subject, String plainMessage) throws EmailSendException {
        String fullSubject = "[Super Gym] " + subject;
        String body = "<p>" + plainMessage + "</p>";
        sendHtmlEmail(toEmail, fullSubject, body);
    }

    // ===== Phần kỹ thuật gửi SMTP =====

    /**
     * Gửi 1 email HTML qua SMTP.
     * @param toEmail email người nhận
     * @param subject tiêu đề
     * @param htmlBody nội dung HTML
     * @throws EmailSendException gửi thất bại
     */
    private void sendHtmlEmail(String toEmail, String subject, String htmlBody) throws EmailSendException {
        // B1: đọc cấu hình SMTP từ application.properties
        String host = AppConfig.get("mail.smtp.host");
        String port = AppConfig.get("mail.smtp.port");
        String username = AppConfig.get("mail.username");
        String appPassword = AppConfig.get("mail.app-password");
        String fromName = AppConfig.get("mail.from-name", "Super Gym");

        // B2: bật xác thực + STARTTLS (Gmail cổng 587 bắt buộc mã hóa), đặt timeout
        Properties smtpProperties = new Properties();
        smtpProperties.put("mail.smtp.host", host);
        smtpProperties.put("mail.smtp.port", port);
        smtpProperties.put("mail.smtp.auth", "true");
        smtpProperties.put("mail.smtp.starttls.enable", "true");
        smtpProperties.put("mail.smtp.connectiontimeout", SMTP_TIMEOUT_MS);
        smtpProperties.put("mail.smtp.timeout", SMTP_TIMEOUT_MS);
        smtpProperties.put("mail.smtp.writetimeout", SMTP_TIMEOUT_MS);

        // B3: tạo phiên SMTP, đăng nhập bằng địa chỉ Gmail + App Password
        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, appPassword);
            }
        };
        Session mailSession = Session.getInstance(smtpProperties, authenticator);

        try {
            // B4: dựng nội dung email — ép UTF-8 cho cả tiêu đề và thân để không lỗi tiếng Việt
            MimeMessage message = new MimeMessage(mailSession);
            message.setFrom(new InternetAddress(username, fromName, "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject, "UTF-8");
            message.setContent(htmlBody, "text/html; charset=UTF-8");
            message.setSentDate(new Date());

            // B5: gửi — hàm này chặn tới khi Gmail nhận xong (thường 1–3 giây)
            Transport.send(message);

        } catch (MessagingException | UnsupportedEncodingException e) {
            // B6: log lỗi thật cho dev, ném lỗi gói lại cho Service quyết định báo gì với người dùng
            e.printStackTrace();
            throw new EmailSendException("Gửi email tới " + toEmail + " thất bại", e);
        }
    }
}
