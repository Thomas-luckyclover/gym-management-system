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
 * Luồng step (2 nhánh tách riêng):
 *
 *   [Nhánh đăng ký mới]
 *   GET  /register                → step "phone" (bước 1)
 *   POST step=phone               → tra SĐT → chuyển sang step phù hợp
 *   POST step=form                → validate + gửi OTP → chuyển sang step "otp"
 *   POST step=otp                 → xác minh mã → tạo tài khoản → step "success"
 *   POST step=resend              → xin lại mã → forward lại step "otp"
 *
 *   [Nhánh Claim — kích hoạt hồ sơ quầy]
 *   POST step=phone               → tra SĐT → PROFILE_CAN_CLAIM → step "claim-email"
 *   POST step=claim-email         → so khớp email (CBR-25) → gửi OTP → step "claim-otp"
 *   POST step=claim-otp           → xác minh mã → step "claim-password"
 *   POST step=claim-password      → validate password → tạo account → step "success"
 *   POST step=claim-resend        → xin lại mã → forward lại step "claim-otp"
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
            req.setAttribute("step", "claim-email");
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
            // ===== Nhánh Claim =====
            case "claim-email":
                handleClaimEmailStep(req, resp);
                break;
            case "claim-otp":
                handleClaimOtpStep(req, resp);
                break;
            case "claim-password":
                handleClaimPasswordStep(req, resp);
                break;
            case "claim-resend":
                handleClaimResendStep(req, resp);
                break;
            default:
                resp.sendRedirect(req.getContextPath() + "/register");
        }
    }

    // ===== Bước 1: Tra trùng Số Điện Thoại =====

    /**
     * Tra SĐT trong DB, điều hướng ra đúng nhánh theo kết quả.
     * Các nhánh có thể: form, claim-email, blocked-account, blocked-no-email.
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
                // CBR-26: SĐT đã có tài khoản → DỪNG NGAY TẠI MÀN 1
                req.setAttribute("step", "blocked-account");
                req.setAttribute("phone", trimmedPhone);
                break;

            case PROFILE_NO_EMAIL:
                // CBR-27: hồ sơ tại quầy chưa có email → DỪNG NGAY TẠI MÀN 1
                req.setAttribute("step", "blocked-no-email");
                req.setAttribute("phone", trimmedPhone);
                break;

            case PROFILE_CAN_CLAIM:
                // Hồ sơ có email, khách cần nhập đúng email → vào nhánh CLAIM riêng
                req.setAttribute("step", "claim-email");
                req.setAttribute("phone", trimmedPhone);
                req.setAttribute("maskedEmail", checkResult.getMaskedEmail());
                break;

            default:
                // NEW_PHONE: số điện thoại chưa có hồ sơ → đăng ký mới
                req.setAttribute("step", "form");
                req.setAttribute("phone", trimmedPhone);
                req.setAttribute("registerMode", "new");
        }

        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
    }

    // ========================================================================================
    // ===== NHÁNH ĐĂNG KÝ MỚI (registerMode = "new") =====
    // ========================================================================================

    // ===== Bước 2: Validate form + Gửi OTP =====

    /**
     * Validate toàn bộ dữ liệu form đăng ký mới, nếu hợp lệ gửi OTP rồi chuyển sang step "otp".
     */
    private void handleFormStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String email = req.getParameter("email");
        String fullName = req.getParameter("fullName");
        String dobString = req.getParameter("dateOfBirth");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");
        String registerMode = "new";

        LocalDate dateOfBirth = DateTimeUtil.parseHtmlDate(dobString);

        // B1: validate form — nếu lỗi forward lại bước "form" với dữ liệu cũ
        ServiceResult<Void> validationResult = registrationService.validateRegistrationForm(
                fullName, dateOfBirth, phone, email, password, confirmPassword, registerMode);

        if (!validationResult.isSuccess()) {
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
        ServiceResult<Void> sendResult =
                verificationCodeService.sendCode(email.trim(), VerificationCode.Purpose.SIGN_UP, null);

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

    // ===== Bước 3: Xác minh mã OTP (đăng ký mới) =====

    /**
     * Xác minh mã OTP khách gõ. Đúng mã → tạo tài khoản → step "success".
     */
    private void handleOtpStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String email = req.getParameter("email");
        String fullName = req.getParameter("fullName");
        String dobString = req.getParameter("dateOfBirth");
        String registerMode = "new";
        String inputCode = req.getParameter("code");
        String password = req.getParameter("password");

        // B1: gọi service xác minh mã
        ServiceResult<VerificationCode> verifyResult =
                verificationCodeService.verifyCode(email, VerificationCode.Purpose.SIGN_UP, inputCode);

        if (!verifyResult.isSuccess()) {
            // Mã sai hoặc bị khóa → forward lại OTP step, hiện lỗi
            req.setAttribute("fieldErrors", verifyResult.getFieldErrors());
            forwardToOtpStep(req, resp, phone, email, fullName, dobString,
                    registerMode, password, verifyResult.getMessage());
            return;
        }

        // B2: mã đúng → tạo tài khoản
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
        req.setAttribute("registerMode", "new");
        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
    }

    // ===== Xin lại mã OTP (đăng ký mới) =====

    /**
     * Gửi lại mã OTP (khách bấm "Gửi lại mã"). Kiểm tra 60s và 5 lần/giờ trong VerificationCodeService.
     */
    private void handleResendStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String email = req.getParameter("email");
        String fullName = req.getParameter("fullName");
        String dobString = req.getParameter("dateOfBirth");
        String registerMode = "new";
        String password = req.getParameter("password");

        // B1: gửi lại mã — nếu lỗi (quá 60s, quá 5 lần...) trả về lỗi ở OTP step
        ServiceResult<Void> sendResult =
                verificationCodeService.sendCode(email, VerificationCode.Purpose.SIGN_UP, null);

        String errorForOtp = sendResult.isSuccess() ? null : sendResult.getMessage();
        if (sendResult.isSuccess()) {
            req.setAttribute("successMessage", Messages.MSG45_CODE_RESENT);
        }
        forwardToOtpStep(req, resp, phone, email, fullName, dobString,
                registerMode, password, errorForOtp);
    }

    // ========================================================================================
    // ===== NHÁNH CLAIM — KÍCH HOẠT HỒ SƠ QUẦY (UC-Claim) =====
    // ========================================================================================

    // ===== Claim bước 1: Nhập email (chỉ 1 field email) =====

    /**
     * Khách nhập email → so khớp với hồ sơ (CBR-25).
     * Khớp → gửi mã xác thực tới email TRÊN HỒ SƠ → chuyển sang claim-otp.
     */
    private void handleClaimEmailStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String email = req.getParameter("email");

        // B1: validate & so khớp email
        ServiceResult<Void> result = registrationService.validateClaimEmail(phone, email);

        if (!result.isSuccess()) {
            String msg = result.getMessage();
            if (msg != null && msg.startsWith("EMAIL_MISMATCH:")) {
                // Email không khớp → hiện màn blocked-email-mismatch
                String maskedEmail = msg.substring("EMAIL_MISMATCH:".length());
                req.setAttribute("step", "blocked-email-mismatch");
                req.setAttribute("phone", phone);
                req.setAttribute("maskedEmail", maskedEmail);
                req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
                return;
            }

            // Lỗi validate định dạng email → quay lại claim-email
            req.setAttribute("step", "claim-email");
            req.setAttribute("phone", phone);
            req.setAttribute("email", email);
            req.setAttribute("errorMessage", result.getMessage());
            req.setAttribute("fieldErrors", result.getFieldErrors());
            req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
            return;
        }

        // B2: email khớp → gửi mã xác thực tới email TRÊN HỒ SƠ (không phải input)
        ServiceResult<Void> sendResult =
                verificationCodeService.sendCode(email.trim(), VerificationCode.Purpose.CLAIM, null);

        if (!sendResult.isSuccess()) {
            req.setAttribute("step", "claim-email");
            req.setAttribute("phone", phone);
            req.setAttribute("email", email);
            req.setAttribute("errorMessage", sendResult.getMessage());
            req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
            return;
        }

        // B3: gửi xong → hiện màn nhập mã
        forwardToClaimOtpStep(req, resp, phone, email.trim(), null);
    }

    // ===== Claim bước 2: Xác minh mã OTP =====

    /**
     * Xác minh mã OTP. Đúng mã → chuyển sang claim-password để nhập mật khẩu.
     */
    private void handleClaimOtpStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String email = req.getParameter("email");
        String inputCode = req.getParameter("code");

        // B1: gọi service xác minh mã
        ServiceResult<VerificationCode> verifyResult =
                verificationCodeService.verifyCode(email, VerificationCode.Purpose.CLAIM, inputCode);

        if (!verifyResult.isSuccess()) {
            req.setAttribute("fieldErrors", verifyResult.getFieldErrors());
            forwardToClaimOtpStep(req, resp, phone, email, verifyResult.getMessage());
            return;
        }

        // B2: mã đúng → chuyển sang bước nhập mật khẩu
        // Lấy thông tin hồ sơ (họ tên, ngày sinh) để hiển thị read-only
        RegistrationService.PhoneCheckResult checkResult = registrationService.checkPhone(phone);
        req.setAttribute("step", "claim-password");
        req.setAttribute("phone", phone);
        req.setAttribute("email", email);
        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
    }

    // ===== Claim bước 3: Nhập mật khẩu + Tạo account =====

    /**
     * Validate mật khẩu → tạo account gắn vào hồ sơ có sẵn.
     * KHÔNG đổi Customer.status (CBR-01).
     */
    private void handleClaimPasswordStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String email = req.getParameter("email");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");

        // B1: validate mật khẩu
        ServiceResult<Void> validateResult =
                registrationService.validateClaimPassword(password, confirmPassword);

        if (!validateResult.isSuccess()) {
            req.setAttribute("step", "claim-password");
            req.setAttribute("phone", phone);
            req.setAttribute("email", email);
            req.setAttribute("errorMessage", validateResult.getMessage());
            req.setAttribute("fieldErrors", validateResult.getFieldErrors());
            req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
            return;
        }

        // B2: tạo account (KHÔNG đổi Customer.status — CBR-01)
        ServiceResult<Void> claimResult = registrationService.claimAccount(phone, password);

        if (!claimResult.isSuccess()) {
            req.setAttribute("step", "claim-password");
            req.setAttribute("phone", phone);
            req.setAttribute("email", email);
            req.setAttribute("errorMessage", claimResult.getMessage());
            req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
            return;
        }

        // B3: thành công
        req.setAttribute("step", "success");
        req.setAttribute("registerMode", "linked");
        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
    }

    // ===== Claim: Xin lại mã OTP =====

    private void handleClaimResendStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String email = req.getParameter("email");

        ServiceResult<Void> sendResult =
                verificationCodeService.sendCode(email, VerificationCode.Purpose.CLAIM, null);

        String errorForOtp = sendResult.isSuccess() ? null : sendResult.getMessage();
        if (sendResult.isSuccess()) {
            req.setAttribute("successMessage", Messages.MSG45_CODE_RESENT);
        }
        forwardToClaimOtpStep(req, resp, phone, email, errorForOtp);
    }

    // ========================================================================================
    // ===== HÀM HỖ TRỢ =====
    // ========================================================================================

    // ===== Forward sang màn OTP (đăng ký mới) =====

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

    // ===== Forward sang màn OTP (Claim) =====

    /**
     * Set attributes và forward vào JSP step "claim-otp".
     */
    private void forwardToClaimOtpStep(HttpServletRequest req, HttpServletResponse resp,
                                        String phone, String email, String errorMessage)
            throws ServletException, IOException {

        LocalDateTime expiresAt = DateTimeUtil.now().plusMinutes(15);
        LocalDateTime resendUnlockAt = DateTimeUtil.now().plusSeconds(60);

        req.setAttribute("step", "claim-otp");
        req.setAttribute("phone", phone);
        req.setAttribute("email", email);
        req.setAttribute("maskedEmail", maskEmail(email));
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