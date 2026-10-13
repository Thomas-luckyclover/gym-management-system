package com.gms.integration;

import com.gms.util.AppConfig;
import jakarta.mail.AuthenticationFailedException;
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
 * Cổng gửi email qua Gmail SMTP (cổng 587, STARTTLS).
 * Dùng cho mã xác thực, thông báo đổi email, khôi phục mật khẩu. Nếu chưa cấu hình App Password thì in ra console (chế độ dev).
 */
public class EmailGateway {

    /** Thời gian chờ tối đa khi kết nối/gửi tới SMTP (ms) — tránh treo request nếu Gmail không phản hồi. */
    private static final String SMTP_TIMEOUT_MS = "10000";

    private static final String SUBJECT_PREFIX = "[Super Gym] ";

    // ===== 1. Các email nghiệp vụ =====

    /**
     * Gửi email chứa mã xác thực 6 số (dùng cho đăng ký, khôi phục mật khẩu, đổi email...).
     */
    public void sendVerificationCode(String toEmail, String code, int validMinutes, String purpose)
            throws EmailSendException {
        // B1: tiêu đề thư
        String subject = SUBJECT_PREFIX + "Mã xác thực " + purpose;

        // B2: nội dung dạng HTML (bản hiện chính trên Gmail) — mã nằm trong ô màu chanh, chữ to.
        //     Mọi chuỗi đưa vào đều qua escapeHtml để không bị chèn mã HTML lạ
        String html = "<p>Xin chào,</p>"
                + "<p>Mã xác thực để " + escapeHtml(purpose) + " của bạn là:</p>"
                + "<p style=\"margin:20px 0;font-size:30px;font-weight:800;letter-spacing:8px;color:#0D0D0D;"
                + "background:#D4FF3F;display:inline-block;padding:10px 18px;border-radius:10px\">"
                + escapeHtml(code) + "</p>"
                + "<p>Mã có hiệu lực trong <b>" + validMinutes + " phút</b> và chỉ dùng được 1 lần.</p>"
                + "<p style=\"color:#6B6960\">Nếu bạn không yêu cầu mã này, hãy bỏ qua email. Không chia sẻ mã cho bất kỳ ai.</p>";

        // B3: cùng nội dung nhưng chữ thường (cho trình đọc thư không hiện được HTML)
        String text = "Xin chào,\n\n"
                + "Mã xác thực để " + purpose + " của bạn là: " + code + "\n"
                + "Mã có hiệu lực trong " + validMinutes + " phút và chỉ dùng được 1 lần.\n\n"
                + "Nếu bạn không yêu cầu mã này, hãy bỏ qua email. Không chia sẻ mã cho bất kỳ ai.";

        // B4: gửi đi
        send(toEmail, subject, html, text);
    }

    /**
     * Gửi thông báo ngắn 1 đoạn (VD báo cho email cũ khi bị đổi).
     */
    public void sendNotice(String toEmail, String subject, String plainMessage) throws EmailSendException {
        // Nội dung HTML chỉ là 1 đoạn <p>, đã escape; bản chữ thường giữ nguyên
        String html = "<p>" + escapeHtml(plainMessage) + "</p>";
        send(toEmail, SUBJECT_PREFIX + subject, html, plainMessage);
    }

    // ===== 2. Khung giao diện chung =====

    /**
     * Bọc nội dung thư vào khung chung của Super Gym: thanh đen chữ "SUPER GYM" ở trên, chân thư ở dưới.
     * Chỉ dùng style viết thẳng trong thẻ (inline) vì Gmail xóa thẻ &lt;style&gt;.
     *
     * @param contentHtml phần ruột thư đã soạn (các đoạn &lt;p&gt;...), VD đoạn chứa mã 6 số.
     * @return toàn bộ HTML của thư, sẵn sàng để gửi.
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
     * Gửi thật 1 email qua Gmail (hoặc in ra console nếu máy chưa cấu hình Gmail).
     * Thư gồm 2 phần cùng nội dung: chữ thường + HTML; trình đọc thư tự chọn phần hiển thị được.
     *
     * @param toEmail     email người nhận, VD "thao.luong@gmail.com".
     * @param subject     tiêu đề đầy đủ (đã có "[Super Gym]"), VD "[Super Gym] Mã xác thực khôi phục mật khẩu".
     * @param contentHtml ruột thư dạng HTML, CHƯA bọc khung (hàm tự bọc bằng wrapInLayout).
     * @param contentText cùng nội dung ở dạng chữ thường.
     * @throws EmailSendException khi Gmail từ chối / mất mạng / quá 10 giây — lỗi gốc đã in ra console.
     */
    private void send(String toEmail, String subject, String contentHtml, String contentText)
            throws EmailSendException {
        // B1: đọc cấu hình từ application.properties: địa chỉ Gmail gửi đi, App Password 16 ký tự, tên hiển thị
        String username = AppConfig.get("mail.username");
        String appPassword = AppConfig.get("mail.app-password");
        String fromName = AppConfig.get("mail.from-name", "Super Gym");

        // B2: CHẾ ĐỘ DEV — máy chưa điền App Password (trống hoặc còn "xxxx…" như file mẫu)
        //     → in thư ra console Tomcat (dòng bắt đầu "[DEV EMAIL]") thay vì gửi, để ai cũng test được
        if (isMailNotConfigured(appPassword)) {
            System.out.println("[DEV EMAIL] To: " + toEmail + " | Subject: " + subject + "\n" + contentText);
            return;
        }

        try {
            // B3: dựng phần đầu thư: người gửi hiện "Super Gym <địa chỉ Gmail>", người nhận, tiêu đề, ngày gửi.
            //     Ép UTF-8 để tiếng Việt không lỗi font
            MimeMessage message = new MimeMessage(createMailSession(username, appPassword));
            message.setFrom(new InternetAddress(username, fromName, "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject, "UTF-8");
            message.setSentDate(new Date());

            // B4: dựng 2 phần nội dung — chữ thường, và HTML đã bọc khung SUPER GYM
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(contentText, "UTF-8");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(wrapInLayout(contentHtml), "text/html; charset=UTF-8");

            // B5: ghép 2 phần kiểu "alternative" = cùng 1 nội dung, trình đọc thư chọn bản đẹp nhất nó hiện được.
            //     Phần HTML phải đứng SAU phần chữ thường (quy ước của chuẩn email)
            MimeMultipart body = new MimeMultipart("alternative");
            body.addBodyPart(textPart);
            body.addBodyPart(htmlPart);
            message.setContent(body);

            // B6: gửi — đứng chờ tới khi Gmail nhận xong (thường 1–5 giây). Lần gửi đầu sau khi Tomcat vừa
            //     khởi động hay mạng chập chờn đôi khi rớt kết nối → tự thử lại 1 lần trước khi báo lỗi cho khách
            sendWithRetry(message);

        } catch (MessagingException | UnsupportedEncodingException e) {
            // B7: in lỗi thật cho dev xem, rồi ném EmailSendException để Service quyết định báo gì với khách
            e.printStackTrace(); // B7: in lỗi thật cho dev xem, rồi ném EmailSendException để Service quyết định báo gì với khách

            throw new EmailSendException("Gửi email tới " + toEmail + " thất bại", e);
        }
    }

    /**
     * Tạo "phiên" kết nối tới máy chủ Gmail, đăng nhập bằng địa chỉ Gmail + App Password.
     *
     * @param username    địa chỉ Gmail gửi đi, VD "luckyclover898989@gmail.com".
     * @param appPassword App Password 16 ký tự viết liền (KHÔNG phải mật khẩu Gmail).
     * @return phiên đã cấu hình sẵn (cổng 587, bắt buộc mã hóa TLS, timeout 10 giây) để dựng thư và gửi.
     */
    private Session createMailSession(String username, String appPassword) {
        String host = AppConfig.get("mail.smtp.host");

        // Cấu hình kết nối: máy chủ + cổng, bắt buộc đăng nhập
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
        // Chờ tối đa 10 giây cho mỗi việc (kết nối / đọc / ghi) — Gmail không trả lời thì báo lỗi, không treo trang
        smtp.put("mail.smtp.connectiontimeout", SMTP_TIMEOUT_MS);
        smtp.put("mail.smtp.timeout", SMTP_TIMEOUT_MS);
        smtp.put("mail.smtp.writetimeout", SMTP_TIMEOUT_MS);

        // Khi Gmail hỏi "đăng nhập bằng gì?" thì đưa địa chỉ Gmail + App Password
        return Session.getInstance(smtp, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, appPassword);
            }
        });
    }

    /**
     * Gửi thư, rớt kết nối / hết giờ chờ thì thử lại thêm đúng 1 lần.
     * Sai App Password thì gửi lại cũng vô ích → báo lỗi luôn.
     *
     * @param message thư đã dựng xong (người gửi, người nhận, tiêu đề, nội dung).
     * @throws MessagingException khi cả 2 lần đều thất bại, hoặc Gmail từ chối đăng nhập.
     */
    private void sendWithRetry(MimeMessage message) throws MessagingException {
        try {
            Transport.send(message);
        } catch (AuthenticationFailedException wrongPassword) {
            throw wrongPassword;
        } catch (MessagingException firstFailure) {
            System.err.println("[EMAIL] Gửi lần 1 thất bại, thử lại: " + firstFailure.getMessage());
            Transport.send(message);
        }
    }

    /**
     * Kiểm tra máy này đã điền App Password thật chưa.
     *
     * @param appPassword giá trị dòng mail.app-password trong application.properties.
     * @return true nếu CHƯA điền (trống, hoặc còn "xxxx…" như file mẫu) → dùng chế độ dev in ra console;
     *         false nếu đã điền → gửi mail thật.
     */
    private boolean isMailNotConfigured(String appPassword) {
        return appPassword == null || appPassword.isBlank() || appPassword.startsWith("xxxx");
    }

    /**
     * Đổi các ký tự đặc biệt của HTML thành dạng an toàn trước khi ghép chuỗi vào thân thư,
     * để không ai chèn được thẻ HTML / script vào email.
     *
     * @param value chuỗi cần đưa vào thư, VD {@code <b>abc</b>}; null được coi như chuỗi rỗng.
     * @return chuỗi đã đổi, VD {@code &lt;b&gt;abc&lt;/b&gt;} — trong thư hiện đúng chữ {@code <b>abc</b>}, không bị in đậm.
     */
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
