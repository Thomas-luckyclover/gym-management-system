package com.gms.servlet.auth;

import com.gms.constant.Messages;
import com.gms.dto.ServiceResult;
import com.gms.entity.VerificationCode;
import com.gms.service.RegistrationService;
import com.gms.service.VerificationCodeService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import com.gms.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller xử lý luồng đăng ký tài khoản khách hàng (/register).
 *
 * Luồng step:
 *   GET  /register                → step "phone" (bước 1)
 *   POST step=phone               → tra SĐT → chuyển sang step phù hợp
 *   POST step=form                → validate + gửi OTP → chuyển sang step "otp"
 *   POST step=otp                 → xác minh mã → tạo tài khoản → step "success"
 *   POST step=resend              → xin lại mã → forward lại step "otp"
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    // DateTimeFormatter để chuyển LocalDateTime → chuỗi ISO cho JS countdown
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final RegistrationService registrationService;
    private final VerificationCodeService verificationCodeService;

    public RegisterServlet() {
        this.registrationService = new RegistrationService();
        this.verificationCodeService = new VerificationCodeService();
    }

    public RegisterServlet(RegistrationService registrationService,
                           VerificationCodeService verificationCodeService) {
        this.registrationService = registrationService;
        this.verificationCodeService = verificationCodeService;
    }

    // ===== GET: Hiển thị bước 1 (nhập SĐT) =====

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Hỗ trợ GET step=2&phone=... từ nhánh "Thử email khác" (blocked-email-mismatch)
        String stepParam = req.getParameter("step");
        String phone = req.getParameter("phone");

        if ("2".equals(stepParam) && ValidationUtil.isValidPhone(phone)) {
            req.setAttribute("step", "form");
            req.setAttribute("phone", phone.trim());
        } else {
            req.setAttribute("step", "phone");
        }

        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
    }

    // ===== POST: Điều phối theo giá trị hidden field "step" =====

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String step = req.getParameter("step");

        // B1: điều phối sang đúng handler theo step
        switch (step != null ? step : "") {
            case "phone":
                handlePhoneStep(req, resp);
                break;
            case "form":
                handleFormStep(req, resp);
                break;
            case "otp":
                handleOtpStep(req, resp);
                break;
            case "resend":
                handleResendStep(req, resp);
                break;
            default:
                resp.sendRedirect(req.getContextPath() + "/register");
        }
    }

    // ===== Bước 1: Tra trùng Số Điện Thoại =====

    /**
     * Tra SĐT trong DB, điều hướng ra đúng nhánh theo kết quả.
     * Các nhánh có thể: form, blocked-account, blocked-email-mismatch, blocked-no-email.
     */
    private void handlePhoneStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");

        // B1: kiểm tra định dạng SĐT trước khi tra DB
        if (!ValidationUtil.isValidPhone(phone)) {
            req.setAttribute("step", "phone");
            req.setAttribute("phone", phone);
            req.setAttribute("fieldErrors",
                    java.util.Map.of("phone", Messages.MSG13_INVALID_PHONE));
            req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
            return;
        }

        String trimmedPhone = phone.trim();

        // B2: hỏi RegistrationService kết quả tra SĐT
        RegistrationService.PhoneCheckResult checkResult =
                registrationService.checkPhone(trimmedPhone);

        // B3: chuyển sang step tương ứng kết quả tra
        switch (checkResult.getOutcome()) {
            case HAS_ACCOUNT:
                // CBR-26: SĐT đã có tài khoản
                req.setAttribute("step", "blocked-account");
                req.setAttribute("phone", trimmedPhone);
                break;

            case PROFILE_NO_EMAIL:
                // CBR-27: hồ sơ tại quầy chưa có email
                req.setAttribute("step", "blocked-no-email");
                req.setAttribute("phone", trimmedPhone);
                break;

            case PROFILE_EMAIL_MISMATCH:
                // CBR-25: hồ sơ có email nhưng cần khớp → hiện email đã che
                req.setAttribute("step", "blocked-email-mismatch");
                req.setAttribute("phone", trimmedPhone);
                req.setAttribute("maskedEmail", checkResult.getMaskedEmail());
                break;

            case PROFILE_CAN_CLAIM:
                // Hồ sơ có email, khách cần nhập đúng email để kích hoạt → vào bước form
                req.setAttribute("step", "form");
                req.setAttribute("phone", trimmedPhone);
                req.setAttribute("registerMode", "linked");
                break;

            default:
                // NEW_PHONE: số điện thoại chưa có hồ sơ → đăng ký mới
                req.setAttribute("step", "form");
                req.setAttribute("phone", trimmedPhone);
                req.setAttribute("registerMode", "new");
        }

        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
    }

    // ===== Bước 2: Validate form + Gửi OTP =====

    /**
     * Validate toàn bộ dữ liệu form, nếu hợp lệ gửi OTP rồi chuyển sang step "otp".
     */
    private void handleFormStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String email = req.getParameter("email");
        String fullName = req.getParameter("fullName");
        String dobString = req.getParameter("dateOfBirth");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");
        String registerMode = req.getParameter("registerMode");

        LocalDate dateOfBirth = DateTimeUtil.parseHtmlDate(dobString);

        // B1: validate form — nếu lỗi forward lại bước "form" với dữ liệu cũ
        ServiceResult<Void> validationResult = registrationService.validateRegistrationForm(
                fullName, dateOfBirth, phone, email, password, confirmPassword, registerMode);

        if (!validationResult.isSuccess()) {
            String msg = validationResult.getMessage();
            if (msg != null && msg.startsWith("EMAIL_MISMATCH:")) {
                String maskedEmail = msg.substring("EMAIL_MISMATCH:".length());
                req.setAttribute("step", "blocked-email-mismatch");
                req.setAttribute("phone", phone);
                req.setAttribute("maskedEmail", maskedEmail);
                req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
                return;
            }

            req.setAttribute("step", "form");
            req.setAttribute("phone", phone);
            req.setAttribute("email", email);
            req.setAttribute("fullName", fullName);
            req.setAttribute("dateOfBirth", dobString);
            req.setAttribute("registerMode", registerMode);
            req.setAttribute("errorMessage", validationResult.getMessage());
            req.setAttribute("fieldErrors", validationResult.getFieldErrors());
            req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
            return;
        }

        // B2: gửi OTP tới email
        VerificationCode.Purpose purpose = "linked".equals(registerMode)
                ? VerificationCode.Purpose.CLAIM
                : VerificationCode.Purpose.SIGN_UP;

        ServiceResult<Void> sendResult =
                verificationCodeService.sendCode(email.trim(), purpose, null);

        if (!sendResult.isSuccess()) {
            req.setAttribute("step", "form");
            req.setAttribute("phone", phone);
            req.setAttribute("email", email);
            req.setAttribute("fullName", fullName);
            req.setAttribute("dateOfBirth", dobString);
            req.setAttribute("registerMode", registerMode);
            req.setAttribute("errorMessage", sendResult.getMessage());
            req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
            return;
        }

        // B3: gửi xong → hiện màn OTP
        forwardToOtpStep(req, resp, phone, email.trim(), fullName, dobString,
                registerMode, password, null);
    }

    // ===== Bước 3: Xác minh mã OTP =====

    /**
     * Xác minh mã OTP khách gõ. Đúng mã → tạo tài khoản → step "success".
     */
    private void handleOtpStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String email = req.getParameter("email");
        String fullName = req.getParameter("fullName");
        String dobString = req.getParameter("dateOfBirth");
        String registerMode = req.getParameter("registerMode");
        String inputCode = req.getParameter("code");
        String password = req.getParameter("password");

        VerificationCode.Purpose purpose = "linked".equals(registerMode)
                ? VerificationCode.Purpose.CLAIM
                : VerificationCode.Purpose.SIGN_UP;

        // B1: gọi service xác minh mã
        ServiceResult<VerificationCode> verifyResult =
                verificationCodeService.verifyCode(email, purpose, inputCode);

        if (!verifyResult.isSuccess()) {
            // Mã sai hoặc bị khóa → forward lại OTP step, hiện lỗi
            req.setAttribute("fieldErrors", verifyResult.getFieldErrors());
            forwardToOtpStep(req, resp, phone, email, fullName, dobString,
                    registerMode, password, verifyResult.getMessage());
            return;
        }

        // B2: mã đúng → tạo tài khoản hoặc kích hoạt hồ sơ
        LocalDate dateOfBirth = DateTimeUtil.parseHtmlDate(dobString);

        ServiceResult<Void> registerResult = registrationService.register(
                fullName, dateOfBirth, phone, email, password, password);

        if (!registerResult.isSuccess()) {
            req.setAttribute("errorMessage", registerResult.getMessage());
            req.setAttribute("fieldErrors", registerResult.getFieldErrors());
            forwardToOtpStep(req, resp, phone, email, fullName, dobString,
                    registerMode, password, registerResult.getMessage());
            return;
        }

        // B3: thành công → hiện màn success
        req.setAttribute("step", "success");
        req.setAttribute("registerMode", registerMode != null ? registerMode : "new");
        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
    }

    // ===== Xin lại mã OTP =====

    /**
     * Gửi lại mã OTP (khách bấm "Gửi lại mã"). Kiểm tra 60s và 5 lần/giờ trong VerificationCodeService.
     */
    private void handleResendStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String email = req.getParameter("email");
        String fullName = req.getParameter("fullName");
        String dobString = req.getParameter("dateOfBirth");
        String registerMode = req.getParameter("registerMode");
        String password = req.getParameter("password");

        VerificationCode.Purpose purpose = "linked".equals(registerMode)
                ? VerificationCode.Purpose.CLAIM
                : VerificationCode.Purpose.SIGN_UP;

        // B1: gửi lại mã — nếu lỗi (quá 60s, quá 5 lần...) trả về lỗi ở OTP step
        ServiceResult<Void> sendResult =
                verificationCodeService.sendCode(email, purpose, null);

        String errorForOtp = sendResult.isSuccess() ? null : sendResult.getMessage();
        if (sendResult.isSuccess()) {
            req.setAttribute("successMessage", "Đã gửi lại mã xác thực tới " + maskEmail(email) + ".");
        }
        forwardToOtpStep(req, resp, phone, email, fullName, dobString,
                registerMode, password, errorForOtp);
    }

    // ===== Hàm hỗ trợ: forward sang màn OTP với đủ attributes =====

    /**
     * Set attributes và forward vào JSP step "otp".
     *
     * @param errorMessage null nếu không có lỗi, khác null để hiện banner lỗi
     */
    private void forwardToOtpStep(HttpServletRequest req, HttpServletResponse resp,
                                   String phone, String email, String fullName,
                                   String dobString, String registerMode,
                                   String password, String errorMessage)
            throws ServletException, IOException {

        // Tính thời điểm hết hạn OTP để JS countdown (15 phút từ bây giờ, tạm dùng cố định)
        LocalDateTime expiresAt = DateTimeUtil.now().plusMinutes(15);

        // Tính thời điểm được phép xin lại mã (60 giây từ bây giờ)
        LocalDateTime resendUnlockAt = DateTimeUtil.now().plusSeconds(60);

        req.setAttribute("step", "otp");
        req.setAttribute("phone", phone);
        req.setAttribute("email", email);
        req.setAttribute("maskedEmail", maskEmail(email));
        req.setAttribute("fullName", fullName);
        req.setAttribute("dateOfBirth", dobString);
        req.setAttribute("registerMode", registerMode);
        req.setAttribute("password", password);
        req.setAttribute("otpExpiresAt", expiresAt.format(ISO_FORMATTER));
        req.setAttribute("resendUnlockAt", resendUnlockAt.format(ISO_FORMATTER));

        if (errorMessage != null) {
            req.setAttribute("errorMessage", errorMessage);
        }

        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
    }

    /** Che email: ab***@domain.com */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String namePart = email.substring(0, atIndex);
        String domainPart = email.substring(atIndex);

        if (namePart.length() <= 2) {
            return namePart.charAt(0) + "***" + domainPart;
        }
        return namePart.substring(0, 2) + "***" + domainPart;
    }
}