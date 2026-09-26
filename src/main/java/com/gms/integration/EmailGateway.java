package com.gms.integration;

import com.gms.util.AppConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
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
 * ===== CẤU HÌNH (application.properties — không commit) =====
 * mail.smtp.host, mail.smtp.port, mail.username (địa chỉ Gmail gửi đi), mail.app-password (App Password 16 ký tự),
 * mail.from-name (tên hiển thị người gửi).
 * App Password KHÔNG phải mật khẩu Gmail thường — xem docs/integration-guide.md mục Email.
 * Máy chưa điền App Password (còn "xxxx…") → chế độ dev: in email ra console Tomcat thay vì gửi.
 *
 * ===== CÁCH TỔ CHỨC =====
 * 1. Các email nghiệp vụ (public): chỉ soạn nội dung — 1 bản HTML + 1 bản chữ thường.
 * 2. Khung chung: bọc nội dung HTML trong giao diện Super Gym (logo chữ, chân thư).
 * 3. Phần kỹ thuật SMTP: đọc cấu hình, tạo phiên, gửi thư 2 phần (multipart/alternative — ít bị vào Spam hơn).
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

    private static final String SUBJECT_PREFIX = "[Super Gym] ";

    // ===== 1. Các email nghiệp vụ =====

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
        String subject = SUBJECT_PREFIX + "Mã xác thực " + purpose;

        String html = "<p>Xin chào,</p>"
                + "<p>Mã xác thực để " + escapeHtml(purpose) + " của bạn là:</p>"
                + "<p style=\"margin:20px 0;font-size:30px;font-weight:800;letter-spacing:8px;color:#0D0D0D;"
                + "background:#D4FF3F;display:inline-block;padding:10px 18px;border-radius:10px\">"
                + escapeHtml(code) + "</p>"
                + "<p>Mã có hiệu lực trong <b>" + validMinutes + " phút</b> và chỉ dùng được 1 lần.</p>"
                + "<p style=\"color:#6B6960\">Nếu bạn không yêu cầu mã này, hãy bỏ qua email. Không chia sẻ mã cho bất kỳ ai.</p>";

        String text = "Xin chào,\n\n"
                + "Mã xác thực để " + purpose + " của bạn là: " + code + "\n"
                + "Mã có hiệu lực trong " + validMinutes + " phút và chỉ dùng được 1 lần.\n\n"
                + "Nếu bạn không yêu cầu mã này, hãy bỏ qua email. Không chia sẻ mã cho bất kỳ ai.";

        send(toEmail, subject, html, text);
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
        String subject = SUBJECT_PREFIX + "Đặt mật khẩu cho tài khoản của bạn";
        String safeLink = escapeHtml(setPasswordLink);

        String html = "<p>Xin chào,</p>"
                + "<p>Tài khoản Super Gym của bạn đã được tạo tại quầy. Bấm nút dưới đây để đặt mật khẩu:</p>"
                + "<p style=\"margin:20px 0\"><a href=\"" + safeLink + "\" style=\"background:#D4FF3F;color:#0D0D0D;"
                + "padding:12px 22px;border-radius:999px;font-weight:700;text-decoration:none\">Đặt mật khẩu</a></p>"
                + "<p>Link có hiệu lực trong <b>" + validMinutes + " phút</b> và chỉ dùng được 1 lần.</p>"
                + "<p style=\"color:#6B6960;word-break:break-all\">Nếu nút không bấm được, mở link sau: " + safeLink + "</p>";

        String text = "Xin chào,\n\n"
                + "Tài khoản Super Gym của bạn đã được tạo tại quầy. Mở link sau để đặt mật khẩu:\n"
                + setPasswordLink + "\n\n"
                + "Link có hiệu lực trong " + validMinutes + " phút và chỉ dùng được 1 lần.";

        send(toEmail, subject, html, text);
    }

    /**
     * Gửi thông báo ngắn (VD báo cho email cũ khi đổi email đăng nhập — UC-04, MSG56).
     * @param toEmail email người nhận
     * @param subject tiêu đề (không cần tiền tố [Super Gym], tự thêm)
     * @param plainMessage nội dung 1 đoạn văn bản
     * @throws EmailSendException gửi thất bại
     */
    public void sendNotice(String toEmail, String subject, String plainMessage) throws EmailSendException {
        String html = "<p>" + escapeHtml(plainMessage) + "</p>";
        send(toEmail, SUBJECT_PREFIX + subject, html, plainMessage);
    }

    // ===== 2. Khung giao diện chung =====

    /**
     * Bọc nội dung vào khung thư Super Gym. Chỉ dùng style inline — Gmail bỏ thẻ &lt;style&gt;.
     */
    private String wrapInLayout(String contentHtml) {
        return "<div style=\"background:#F3F1EC;padding:24px 12px;font-family:Arial,Helvetica,sans-serif\">"
                + "<div style=\"max-width:520px;margin:0 auto;background:#FFFFFF;border-radius:14px;overflow:hidden\">"
                + "<div style=\"background:#0D0D0D;padding:18px 24px;color:#D4FF3F;font-size:20px;font-weight:800;"
                + "letter-spacing:1px\">SUPER GYM</div>"
                + "<div style=\"padding:24px;color:#23221F;font-size:15px;line-height:1.6\">" + contentHtml + "</div>"
                + "<div style=\"padding:14px 24px;border-top:1px solid #E7E5DF;color:#8F8B81;font-size:12px\">"
                + "Email tự động từ hệ thống Super Gym, vui lòng không trả lời thư này.</div>"
                + "</div></div>";
    }

    // ===== 3. Phần kỹ thuật gửi SMTP =====

    /**
     * Gửi 1 email gồm 2 phần: chữ thường (cho trình đọc thư cũ) + HTML (bản hiển thị chính).
     * @param toEmail email người nhận
     * @param subject tiêu đề đầy đủ
     * @param contentHtml nội dung HTML (chưa bọc khung)
     * @param contentText nội dung chữ thường tương ứng
     * @throws EmailSendException gửi thất bại
     */
    private void send(String toEmail, String subject, String contentHtml, String contentText)
            throws EmailSendException {
        // B1: đọc cấu hình SMTP từ application.properties
        String username = AppConfig.get("mail.username");
        String appPassword = AppConfig.get("mail.app-password");
        String fromName = AppConfig.get("mail.from-name", "Super Gym");

        // B2: máy dev chưa điền App Password → in ra console thay vì gửi, để cả nhóm vẫn test được
        if (isMailNotConfigured(appPassword)) {
            System.out.println("[DEV EMAIL] To: " + toEmail + " | Subject: " + subject + "\n" + contentText);
            return;
        }

        try {
            // B3: dựng thư — ép UTF-8 cho tiêu đề và cả 2 phần nội dung để không lỗi tiếng Việt
            MimeMessage message = new MimeMessage(createMailSession(username, appPassword));
            message.setFrom(new InternetAddress(username, fromName, "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject, "UTF-8");
            message.setSentDate(new Date());

            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(contentText, "UTF-8");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(wrapInLayout(contentHtml), "text/html; charset=UTF-8");

            // "alternative": trình đọc thư chọn phần tốt nhất nó hiển thị được — phần HTML phải đứng SAU
            MimeMultipart body = new MimeMultipart("alternative");
            body.addBodyPart(textPart);
            body.addBodyPart(htmlPart);
            message.setContent(body);

            // B4: gửi — chặn tới khi Gmail nhận xong (thường 1–5 giây), tối đa SMTP_TIMEOUT_MS
            Transport.send(message);

        } catch (MessagingException | UnsupportedEncodingException e) {
            // B5: log lỗi thật cho dev, ném lỗi gói lại cho Service quyết định báo gì với người dùng
            e.printStackTrace();
            throw new EmailSendException("Gửi email tới " + toEmail + " thất bại", e);
        }
    }

    /**
     * Tạo phiên SMTP đăng nhập bằng địa chỉ Gmail + App Password.
     */
    private Session createMailSession(String username, String appPassword) {
        String host = AppConfig.get("mail.smtp.host");

        Properties smtp = new Properties();
        smtp.put("mail.smtp.host", host);
        smtp.put("mail.smtp.port", AppConfig.get("mail.smtp.port"));
        smtp.put("mail.smtp.auth", "true");
        // Gmail cổng 587 bắt buộc nâng lên TLS; tin chứng chỉ của đúng host này (tránh lỗi PKIX trên
        // máy có phần mềm diệt virus chặn kết nối), chỉ dùng TLS 1.2 trở lên
        smtp.put("mail.smtp.starttls.enable", "true");
        smtp.put("mail.smtp.starttls.required", "true");
        smtp.put("mail.smtp.ssl.trust", host);
        smtp.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        smtp.put("mail.smtp.connectiontimeout", SMTP_TIMEOUT_MS);
        smtp.put("mail.smtp.timeout", SMTP_TIMEOUT_MS);
        smtp.put("mail.smtp.writetimeout", SMTP_TIMEOUT_MS);

        return Session.getInstance(smtp, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, appPassword);
            }
        });
    }

    private boolean isMailNotConfigured(String appPassword) {
        return appPassword == null || appPassword.isBlank() || appPassword.startsWith("xxxx");
    }

    /** Chặn chèn HTML khi đưa chuỗi vào thân thư. */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
