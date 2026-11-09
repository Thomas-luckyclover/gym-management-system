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
 * CỔNG GỬI EMAIL — mọi chỗ trong app cần gửi thư đều đi qua class này (API-03, Gmail SMTP cổng 587 + TLS).
 *
 * VAI TRÒ: app đóng vai "người gửi thư", đăng nhập vào máy chủ thư của Gmail rồi nhờ Gmail chuyển thư đi.
 *
 * AI GỌI?
 *   sendVerificationCode   ← VerificationCodeService.sendCode   mã 6 số: đăng ký, kích hoạt hồ sơ, QUÊN MẬT KHẨU, đổi email / SĐT
 *   sendTemporaryPassword  ← CounterAccountService             mật khẩu tạm khi Lễ tân tạo tài khoản KHÁCH tại quầy
 *   sendNotice             ← đổi email (báo cho email cũ)        1 đoạn thông báo ngắn
 *
 * BỐ CỤC FILE
 *   1. Email nghiệp vụ (public)  — chỉ SOẠN nội dung (tiêu đề, bản HTML, bản chữ thường) rồi gọi send(...)
 *   2. Khung giao diện           — wrapInLayout: bọc thư bằng header "SUPER GYM"
 *   3. Kỹ thuật gửi SMTP          — send (HÀM CHÍNH gửi thật) → createMailSession, sendWithRetry, isMailNotConfigured, escapeHtml
 *
 * CẤU HÌNH (src/main/resources/application.properties — file bị gitignore, KHÔNG đưa lên GitHub)
 *   mail.username       địa chỉ Gmail gửi đi
 *   mail.app-password   App Password 16 ký tự (Gmail không cho dùng mật khẩu thường; phải bật xác minh 2 bước rồi tạo)
 *   mail.from-name      tên hiện ở ô "Người gửi" (mặc định "Super Gym")
 *   mail.smtp.host / mail.smtp.port   smtp.gmail.com / 587
 *
 * CHẾ ĐỘ DEV: chưa điền App Password (trống hoặc còn "xxxx…" như file mẫu) → KHÔNG gửi, in thư ra console Tomcat
 *   (dòng "[DEV EMAIL] …"). Nhờ vậy cả nhóm test được Quên mật khẩu / Đăng ký mà không cần tài khoản Gmail.
 *
 * LỖI ĐI VỀ ĐÂU: Gmail lỗi → MessagingException → class này ném EmailSendException
 *   → Service bắt và báo MSG04 "Hệ thống đang bận" (VerificationCodeService gửi mail TRƯỚC rồi mới ghi DB,
 *   nên gửi lỗi thì DB không có dòng mã nào mà khách không nhận được).
 */
public class EmailGateway {

    /** Chờ tối đa 10 giây (ms) cho mỗi việc kết nối / đọc / ghi tới Gmail — Gmail không trả lời thì báo lỗi, không treo trang. */
    private static final String SMTP_TIMEOUT_MS = "10000";

    /** Mọi tiêu đề thư đều bắt đầu bằng "[Super Gym] " để khách nhận ra ngay. */
    private static final String SUBJECT_PREFIX = "[Super Gym] ";

    // ================================================================================================================
    // 1. EMAIL NGHIỆP VỤ — mỗi hàm chỉ soạn nội dung (tiêu đề + bản HTML + bản chữ thường), rồi gọi send(...) để gửi
    // ================================================================================================================

    /**
     * HÀM CHÍNH — gửi mã xác thực 6 số.
     *   AI GỌI   VerificationCodeService.sendCode (B6), ngay TRƯỚC khi ghi mã vào bảng VerificationCodes
     *   VÀO      toEmail = người nhận · code = mã 6 số, VD "246810" · validMinutes = hạn mã (P-13 = 15)
     *            · purpose = cụm chữ mục đích, VD "khôi phục mật khẩu" (VerificationCodeService.getPurposeLabel)
     *   RA       không trả gì; gửi lỗi → ném EmailSendException
     *   VD       tiêu đề "[Super Gym] Mã xác thực khôi phục mật khẩu", thân thư có ô mã cam to "246810"
     */
    public void sendVerificationCode(String toEmail, String code, int validMinutes, String purpose)
            throws EmailSendException {
        // B1: tiêu đề thư
        String subject = SUBJECT_PREFIX + "Mã xác thực " + purpose;

        // B2: bản HTML (hiện trên Gmail) — ô mã nền cam cháy, đồng bộ màu với webapp/css/tokens.css.
        //     Mọi chuỗi ghép vào đều qua escapeHtml để không ai chèn được thẻ HTML lạ
        String html = "<p>Xin chào,</p>"
                + "<p>Mã xác thực để " + escapeHtml(purpose) + " của bạn là:</p>"
                + "<p style=\"margin:20px 0;font-family:'Courier New',monospace;font-size:30px;font-weight:800;letter-spacing:8px;color:#080F1B;"
                + "background:#FF4F2B;display:inline-block;padding:10px 18px;border-radius:6px\">"
                + escapeHtml(code) + "</p>"
                + "<p>Mã có hiệu lực trong <b>" + validMinutes + " phút</b> và chỉ dùng được 1 lần.</p>"
                + "<p style=\"color:#4C6670\">Nếu bạn không yêu cầu mã này, hãy bỏ qua email. Không chia sẻ mã cho bất kỳ ai.</p>";

        // B3: bản chữ thường cùng nội dung (cho trình đọc thư không hiện được HTML)
        String text = "Xin chào,\n\n"
                + "Mã xác thực để " + purpose + " của bạn là: " + code + "\n"
                + "Mã có hiệu lực trong " + validMinutes + " phút và chỉ dùng được 1 lần.\n\n"
                + "Nếu bạn không yêu cầu mã này, hãy bỏ qua email. Không chia sẻ mã cho bất kỳ ai.";

        // B4: gửi đi
        send(toEmail, subject, html, text);
    }

    /**
     * HÀM CHÍNH — gửi mật khẩu tạm khi Lễ tân tạo tài khoản cho KHÁCH tại quầy (CBR-31, từ 31/10/2026).
     *   AI GỌI   CounterAccountService (bán gói / tạo hồ sơ tại quầy)
     *   VÀO      toEmail = email trên hồ sơ khách · fullName = họ tên (in lời chào) · customerCode = mã KH-nnnn
     *            (khách đối chiếu với lễ tân) · tempPassword = mật khẩu tạm dạng thô — CHỈ có trong thư này,
     *            DB chỉ lưu bản băm BCrypt
     *   RA       không trả gì; gửi lỗi → ném EmailSendException → CounterSaleService rollback cả giao dịch
     */
    public void sendTemporaryPassword(String toEmail, String fullName, String customerCode, String tempPassword)
            throws EmailSendException {
        // B1: tiêu đề + bản HTML (ô mật khẩu cùng kiểu ô mã xác thực); mọi chuỗi ghép vào đều escape
        String subject = SUBJECT_PREFIX + "Tài khoản và mật khẩu tạm của bạn";
        String html = "<p>Xin chào " + escapeHtml(fullName) + ",</p>"
                + "<p>Lễ tân Super Gym đã tạo hồ sơ <b>" + escapeHtml(customerCode) + "</b> và tài khoản đăng nhập cho bạn.</p>"
                + "<p>Đăng nhập bằng <b>email này</b> hoặc <b>số điện thoại</b> trên hồ sơ, với mật khẩu tạm:</p>"
                + "<p style=\"margin:20px 0;font-family:'Courier New',monospace;font-size:26px;font-weight:800;letter-spacing:4px;color:#080F1B;"
                + "background:#FF4F2B;display:inline-block;padding:10px 18px;border-radius:6px\">"
                + escapeHtml(tempPassword) + "</p>"
                + "<p>Sau khi đăng nhập, hãy vào <b>Tài khoản → Mật khẩu</b> để đổi sang mật khẩu của riêng bạn.</p>"
                + "<p style=\"color:#4C6670\">Nếu bạn không đăng ký tại Super Gym, hãy bỏ qua email này hoặc báo cho lễ tân.</p>";

        // B2: bản chữ thường cùng nội dung
        String text = "Xin chào " + fullName + ",\n\n"
                + "Lễ tân Super Gym đã tạo hồ sơ " + customerCode + " và tài khoản đăng nhập cho bạn.\n"
                + "Đăng nhập bằng email này hoặc số điện thoại trên hồ sơ, mật khẩu tạm: " + tempPassword + "\n\n"
                + "Sau khi đăng nhập, hãy vào Tài khoản → Mật khẩu để đổi sang mật khẩu của riêng bạn.\n"
                + "Nếu bạn không đăng ký tại Super Gym, hãy bỏ qua email này hoặc báo cho lễ tân.";

        // B3: gửi đi
        send(toEmail, subject, html, text);
    }

    /**
     * HÀM CHÍNH — gửi 1 thông báo ngắn, 1 đoạn (VD báo cho email CŨ khi tài khoản vừa đổi sang email khác).
     *   VÀO      toEmail · subject = tiêu đề KHÔNG kèm "[Super Gym]" (hàm tự thêm) · plainMessage = nội dung chữ thường
     *   RA       không trả gì; gửi lỗi → ném EmailSendException
     */
    public void sendNotice(String toEmail, String subject, String plainMessage) throws EmailSendException {
        // Bản HTML chỉ là 1 đoạn <p> đã escape; bản chữ thường giữ nguyên
        String html = "<p>" + escapeHtml(plainMessage) + "</p>";
        send(toEmail, SUBJECT_PREFIX + subject, html, plainMessage);
    }

    // ================================================================================================================
    // 2. KHUNG GIAO DIỆN CHUNG
    // ================================================================================================================

    /**
     * HÀM PHỤ (send B4) — bọc ruột thư bằng khung nhận diện Super Gym:
     *   nền be → thẻ trắng → header navy chữ cam "SUPER GYM" → ruột thư → chân thư "Email tự động…".
     *   Dùng style inline (không dùng file CSS) vì trình đọc thư không tải CSS của website → màu luôn hiện đúng.
     *
     * @param contentHtml ruột thư đã soạn (các đoạn &lt;p&gt;…), VD đoạn chứa mã 6 số
     * @return toàn bộ HTML của thư, sẵn sàng để gửi
     */
    private String wrapInLayout(String contentHtml) {
        return "<div style=\"background:#F5F2EB;padding:24px 12px;font-family:Arial,Helvetica,sans-serif\">"
                + "<div style=\"max-width:520px;margin:0 auto;background:#FFFDF8;border:1px solid #D9DEDE;border-radius:8px;overflow:hidden\">"
                + "<div style=\"background:#0C2337;padding:18px 24px;border-bottom:3px solid #FF4F2B;color:#FF4F2B;font-size:20px;font-weight:800;"
                + "letter-spacing:1px\">SUPER GYM</div>"
                + "<div style=\"padding:24px;color:#16272E;font-size:15px;line-height:1.6\">" + contentHtml + "</div>"
                + "<div style=\"padding:14px 24px;border-top:1px solid #D9DEDE;color:#4C6670;font-size:12px\">"
                + "Email tự động từ hệ thống Super Gym, vui lòng không trả lời thư này.</div>"
                + "</div></div>";
    }

    // ================================================================================================================
    // 3. KỸ THUẬT GỬI SMTP — send là hàm duy nhất thật sự gửi thư; các hàm còn lại là hàm phụ của nó
    // ================================================================================================================

    /**
     * HÀM CHÍNH (gửi thật) — mọi email nghiệp vụ ở nhóm 1 đều gọi hàm này.
     *   VÀO      toEmail = người nhận · subject = tiêu đề đầy đủ (đã có "[Super Gym]")
     *            · contentHtml = ruột thư HTML CHƯA bọc khung (hàm tự bọc) · contentText = cùng nội dung, chữ thường
     *   RA       không trả gì; chế độ dev → in ra console rồi thoát
     *   LỖI      Gmail từ chối / mất mạng / quá 10 giây (cả 2 lần thử) → in lỗi gốc ra console → ném EmailSendException
     *   BƯỚC     B1 đọc cấu hình → B2 chế độ dev? → B3 phần đầu thư → B4 2 phần nội dung → B5 ghép "alternative"
     *            → B6 gửi (thử lại 1 lần) → B7 lỗi thì đổi sang EmailSendException
     */
    private void send(String toEmail, String subject, String contentHtml, String contentText)
            throws EmailSendException {
        // B1: đọc cấu hình trong application.properties: địa chỉ Gmail gửi đi, App Password 16 ký tự, tên hiển thị
        String username = AppConfig.get("mail.username");
        String appPassword = AppConfig.get("mail.app-password");
        String fromName = AppConfig.get("mail.from-name", "Super Gym");

        // B2: CHẾ ĐỘ DEV — máy chưa điền App Password → in thư ra console Tomcat (dòng "[DEV EMAIL]") thay vì gửi.
        //     Dev mở console đọc mã 6 số rồi nhập vào màn hình như bình thường.
        if (isMailNotConfigured(appPassword)) {
            System.out.println("[DEV EMAIL] To: " + toEmail + " | Subject: " + subject + "\n" + contentText);
            return;
        }

        try {
            // B3: phần đầu thư — người gửi hiện "Super Gym <địa chỉ Gmail>", người nhận, tiêu đề, ngày gửi.
            //     Ép UTF-8 để tiếng Việt không lỗi font. Phiên kết nối Gmail do createMailSession dựng.
            MimeMessage message = new MimeMessage(createMailSession(username, appPassword));
            message.setFrom(new InternetAddress(username, fromName, "UTF-8"));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject, "UTF-8");
            message.setSentDate(new Date());

            // B4: 2 phần nội dung — bản chữ thường, và bản HTML đã bọc khung SUPER GYM (wrapInLayout)
            // Không phải “2 đối tượng giống nhau”. Đó là cùng một nội dung viết ở 2 định dạng: một bản chữ thường, một bản HTML có màu và khung. Email cho phép gửi kèm cả hai, và trình đọc thư chỉ hiện một bản phù hợp với nó.
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(contentText, "UTF-8");
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(wrapInLayout(contentHtml), "text/html; charset=UTF-8");

            // B5: ghép kiểu "alternative" = 2 bản CÙNG 1 nội dung, trình đọc thư tự chọn bản đẹp nhất nó hiện được.
            //     Bản HTML phải đứng SAU bản chữ thường (quy ước chuẩn email: bản ưu tiên đặt cuối)
            // Trình đọc thư (email client) là phần mềm hoặc trang web dùng để mở và đọc email. Nó nhận thư từ máy chủ rồi hiển thị cho người dùng xem.
            MimeMultipart body = new MimeMultipart("alternative");
            body.addBodyPart(textPart);
            body.addBodyPart(htmlPart);
            message.setContent(body);

            // B6: gửi và đứng chờ tới khi Gmail nhận xong (thường 1–5 giây). Rớt kết nối thì thử lại 1 lần
            //     (lần gửi đầu sau khi Tomcat vừa khởi động hay mạng chập chờn đôi khi bị rớt)
            sendWithRetry(message);

        } catch (MessagingException | UnsupportedEncodingException e) {
            // B7: in lỗi thật cho dev xem, rồi ném EmailSendException để Service quyết định báo gì với khách (MSG04)
            e.printStackTrace();

            throw new EmailSendException("Gửi email tới " + toEmail + " thất bại", e);
        }
    }

    /**
     * HÀM PHỤ (send B3) — dựng "phiên" kết nối tới máy chủ Gmail và cách đăng nhập.
     *   Cổng 587 + bắt buộc nâng lên TLS (mã hóa) · chỉ TLS 1.2 / 1.3 · timeout 10 giây cho kết nối / đọc / ghi.
     *
     * @param username    địa chỉ Gmail gửi đi, VD "luckyclover898989@gmail.com"
     * @param appPassword App Password 16 ký tự viết liền (KHÔNG phải mật khẩu Gmail)
     * @return phiên đã cấu hình sẵn để dựng thư và gửi
     */
    private Session createMailSession(String username, String appPassword) {
        String host = AppConfig.get("mail.smtp.host");

        // Máy chủ + cổng, bắt buộc đăng nhập
        Properties smtp = new Properties();
        smtp.put("mail.smtp.host", host);
        smtp.put("mail.smtp.port", AppConfig.get("mail.smtp.port"));
        smtp.put("mail.smtp.auth", "true");
        // Gmail cổng 587 bắt buộc STARTTLS (nâng kết nối thường lên kết nối mã hóa).
        // ssl.trust = tin chứng chỉ của ĐÚNG host này (tránh lỗi PKIX trên máy có phần mềm diệt virus chen vào kết nối)
        smtp.put("mail.smtp.starttls.enable", "true");
        smtp.put("mail.smtp.starttls.required", "true");
        smtp.put("mail.smtp.ssl.trust", host);
        smtp.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        // Chờ tối đa 10 giây cho mỗi việc (kết nối / đọc / ghi) — Gmail không trả lời thì báo lỗi, không treo trang
        smtp.put("mail.smtp.connectiontimeout", SMTP_TIMEOUT_MS);
        smtp.put("mail.smtp.timeout", SMTP_TIMEOUT_MS);
        smtp.put("mail.smtp.writetimeout", SMTP_TIMEOUT_MS);

        // Khi Gmail hỏi "đăng nhập bằng gì?" thì đưa địa chỉ Gmail + App Password (lớp ẩn danh ghi đè 1 hàm)
        return Session.getInstance(smtp, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, appPassword);
            }
        });
    }

    /**
     * HÀM PHỤ (send B6) — gửi thư; rớt kết nối / hết giờ chờ thì thử lại thêm ĐÚNG 1 lần.
     *   Sai App Password (AuthenticationFailedException) → ném luôn, vì gửi lại cũng vô ích.
     *   Tệ nhất (Gmail treo cả 2 lần) người dùng chờ khoảng 20 giây rồi nhận MSG04.
     *
     * @param message thư đã dựng xong (người gửi, người nhận, tiêu đề, nội dung)
     * @throws MessagingException khi cả 2 lần đều thất bại, hoặc Gmail từ chối đăng nhập
     */
    private void sendWithRetry(MimeMessage message) throws MessagingException {
        try {
            Transport.send(message);                    // lần 1
        } catch (AuthenticationFailedException wrongPassword) {
            throw wrongPassword;                        // sai App Password → không thử lại
        } catch (MessagingException firstFailure) {
            System.err.println("[EMAIL] Gửi lần 1 thất bại, thử lại: " + firstFailure.getMessage());
            Transport.send(message);                    // lần 2 — lỗi nữa thì ném lên send (B7)
        }
    }

    /**
     * HÀM PHỤ (send B2) — máy này đã điền App Password thật chưa?
     *
     * @param appPassword giá trị dòng mail.app-password trong application.properties
     * @return true = CHƯA điền (trống, hoặc còn "xxxx…" như file mẫu) → chế độ dev in ra console;
     *         false = đã điền → gửi mail thật
     */
    private boolean isMailNotConfigured(String appPassword) {
        return appPassword == null || appPassword.isBlank() || appPassword.startsWith("xxxx");
    }

    /**
     * HÀM PHỤ (nhóm 1) — đổi ký tự đặc biệt của HTML sang dạng an toàn trước khi ghép vào bản HTML của thư,
     * để không ai chèn được thẻ HTML / script vào email (VD khách đặt tên "&lt;b&gt;abc&lt;/b&gt;").
     *   &amp; → &amp;amp; · &lt; → &amp;lt; · &gt; → &amp;gt; · " → &amp;quot; · ' → &amp;#39;
     *   (đổi &amp; TRƯỚC, nếu không các chuỗi "&amp;lt;" vừa tạo sẽ bị đổi thêm lần nữa)
     *
     * @param value chuỗi cần đưa vào thư; null được coi như chuỗi rỗng
     * @return chuỗi đã đổi — trong thư hiện đúng chữ gốc, không bị hiểu thành thẻ HTML
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