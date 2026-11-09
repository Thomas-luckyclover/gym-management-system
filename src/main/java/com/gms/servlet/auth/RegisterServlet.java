package com.gms.servlet.auth;

import com.gms.constant.Messages;
import com.gms.dto.ServiceResult;
import com.gms.entity.VerificationCode;
import com.gms.service.RegistrationService;
import com.gms.service.VerificationCodeService;
import com.gms.util.DateTimeUtil;
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
import java.util.Map;

/**
 * Điều phối luồng đăng ký tài khoản khách hàng và kích hoạt hồ sơ quầy (URL /register).
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private static final String VIEW = "/WEB-INF/jsp/auth/register.jsp";
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final RegistrationService registrationService;
    private final VerificationCodeService verificationCodeService;

    public RegisterServlet() {
        this(new RegistrationService(), new VerificationCodeService());
    }

    public RegisterServlet(RegistrationService registrationService,
                           VerificationCodeService verificationCodeService) {
        this.registrationService = registrationService;
        this.verificationCodeService = verificationCodeService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String stepParam = req.getParameter("step");
        String phone = req.getParameter("phone");

        if ("2".equals(stepParam) && ValidationUtil.isValidPhone(phone)) {
            req.setAttribute("step", "claim-email");
            req.setAttribute("phone", phone.trim());
        } else {
            req.setAttribute("step", "phone");
        }
        forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String step = req.getParameter("step");
        switch (step != null ? step : "") {
            case "phone" -> handlePhone(req, resp);
            case "form" -> handleForm(req, resp);
            case "otp" -> handleOtp(req, resp);
            case "resend" -> handleResend(req, resp, VerificationCode.Purpose.SIGN_UP);
            case "claim-email" -> handleClaimEmail(req, resp);
            case "claim-otp" -> handleClaimOtp(req, resp);
            case "claim-password" -> handleClaimPassword(req, resp);
            case "claim-resend" -> handleResend(req, resp, VerificationCode.Purpose.CLAIM);
            default -> resp.sendRedirect(req.getContextPath() + "/register");
        }
    }

    // ===== Bước 1: Tra số điện thoại =====

    private void handlePhone(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String phone = getTrimmed(req, "phone");
        if (!ValidationUtil.isValidPhone(phone)) {
            req.setAttribute("step", "phone");
            req.setAttribute("phone", phone);
            req.setAttribute("fieldErrors", Map.of("phone", Messages.MSG13_INVALID_PHONE));
            forward(req, resp);
            return;
        }

        RegistrationService.PhoneCheckResult checkResult = registrationService.checkPhone(phone);
        switch (checkResult.getOutcome()) {
            case HAS_ACCOUNT -> {
                req.setAttribute("step", "blocked-account");
                req.setAttribute("phone", phone);
                req.setAttribute("blockedMessage", Messages.MSG51_PHONE_HAS_ACCOUNT);
            }
            case PROFILE_NO_EMAIL -> {
                req.setAttribute("step", "blocked-no-email");
                req.setAttribute("phone", phone);
                req.setAttribute("blockedMessage", Messages.MSG53_PROFILE_NO_EMAIL);
            }
            case PROFILE_CAN_CLAIM -> {
                req.setAttribute("step", "claim-email");
                req.setAttribute("phone", phone);
                req.setAttribute("maskedEmail", checkResult.getMaskedEmail());
            }
            default -> {
                req.setAttribute("step", "form");
                req.setAttribute("phone", phone);
                req.setAttribute("registerMode", "new");
            }
        }
        forward(req, resp);
    }

    // ===== Nhánh Đăng ký mới =====

    private void handleForm(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String phone = getTrimmed(req, "phone");
        String email = getTrimmed(req, "email");
        String fullName = getTrimmed(req, "fullName");
        String dob = getTrimmed(req, "dateOfBirth");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");

        LocalDate dateOfBirth = DateTimeUtil.parseHtmlDate(dob);
        ServiceResult<Void> validation = registrationService.validateRegistrationForm(
                fullName, dateOfBirth, phone, email, password, confirmPassword, "new");

        if (!validation.isSuccess()) {
            populateFormFields(req, phone, email, fullName, dob);
            req.setAttribute("errorMessage", validation.getMessage());
            req.setAttribute("fieldErrors", validation.getFieldErrors());
            forward(req, resp);
            return;
        }

        ServiceResult<Void> sendResult = verificationCodeService.sendCode(email, VerificationCode.Purpose.SIGN_UP, null);
        if (!sendResult.isSuccess()) {
            populateFormFields(req, phone, email, fullName, dob);
            req.setAttribute("errorMessage", sendResult.getMessage());
            forward(req, resp);
            return;
        }

        showOtpStep(req, resp, phone, email, fullName, dob, password, null);
    }

    private void handleOtp(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String phone = getTrimmed(req, "phone");
        String email = getTrimmed(req, "email");
        String fullName = getTrimmed(req, "fullName");
        String dob = getTrimmed(req, "dateOfBirth");
        String password = req.getParameter("password");
        String code = getTrimmed(req, "code");

        ServiceResult<VerificationCode> verify = verificationCodeService.verifyCode(
                email, VerificationCode.Purpose.SIGN_UP, code);
        if (!verify.isSuccess()) {
            req.setAttribute("fieldErrors", verify.getFieldErrors());
            showOtpStep(req, resp, phone, email, fullName, dob, password, verify.getMessage());
            return;
        }

        LocalDate dateOfBirth = DateTimeUtil.parseHtmlDate(dob);
        ServiceResult<Void> registerResult = registrationService.register(
                fullName, dateOfBirth, phone, email, password, password);
        if (!registerResult.isSuccess()) {
            req.setAttribute("fieldErrors", registerResult.getFieldErrors());
            showOtpStep(req, resp, phone, email, fullName, dob, password, registerResult.getMessage());
            return;
        }

        req.setAttribute("step", "success");
        req.setAttribute("registerMode", "new");
        forward(req, resp);
    }

    // ===== Nhánh Claim (Kích hoạt hồ sơ có sẵn) =====

    private void handleClaimEmail(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String phone = getTrimmed(req, "phone");
        String email = getTrimmed(req, "email");

        ServiceResult<Void> result = registrationService.validateClaimEmail(phone, email);
        if (!result.isSuccess()) {
            String msg = result.getMessage();
            if (msg != null && msg.startsWith("EMAIL_MISMATCH:")) {
                String maskedEmail = msg.substring("EMAIL_MISMATCH:".length());
                req.setAttribute("step", "blocked-email-mismatch");
                req.setAttribute("phone", phone);
                req.setAttribute("maskedEmail", maskedEmail);
                req.setAttribute("blockedMessage", String.format(Messages.MSG52_PHONE_DIFFERENT_EMAIL, maskedEmail));
                forward(req, resp);
                return;
            }
            req.setAttribute("step", "claim-email");
            req.setAttribute("phone", phone);
            req.setAttribute("email", email);
            req.setAttribute("errorMessage", result.getMessage());
            req.setAttribute("fieldErrors", result.getFieldErrors());
            forward(req, resp);
            return;
        }

        ServiceResult<Void> sendResult = verificationCodeService.sendCode(email, VerificationCode.Purpose.CLAIM, null);
        if (!sendResult.isSuccess()) {
            req.setAttribute("step", "claim-email");
            req.setAttribute("phone", phone);
            req.setAttribute("email", email);
            req.setAttribute("errorMessage", sendResult.getMessage());
            forward(req, resp);
            return;
        }

        showClaimOtpStep(req, resp, phone, email, null);
    }

    private void handleClaimOtp(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String phone = getTrimmed(req, "phone");
        String email = getTrimmed(req, "email");
        String code = getTrimmed(req, "code");

        ServiceResult<VerificationCode> verify = verificationCodeService.verifyCode(
                email, VerificationCode.Purpose.CLAIM, code);
        if (!verify.isSuccess()) {
            req.setAttribute("fieldErrors", verify.getFieldErrors());
            showClaimOtpStep(req, resp, phone, email, verify.getMessage());
            return;
        }

        req.setAttribute("step", "claim-password");
        req.setAttribute("phone", phone);
        req.setAttribute("email", email);
        forward(req, resp);
    }

    private void handleClaimPassword(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String phone = getTrimmed(req, "phone");
        String email = getTrimmed(req, "email");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");

        ServiceResult<Void> validate = registrationService.validateClaimPassword(password, confirmPassword);
        if (!validate.isSuccess()) {
            req.setAttribute("step", "claim-password");
            req.setAttribute("phone", phone);
            req.setAttribute("email", email);
            req.setAttribute("errorMessage", validate.getMessage());
            req.setAttribute("fieldErrors", validate.getFieldErrors());
            forward(req, resp);
            return;
        }

        ServiceResult<Void> claim = registrationService.claimAccount(phone, password);
        if (!claim.isSuccess()) {
            req.setAttribute("step", "claim-password");
            req.setAttribute("phone", phone);
            req.setAttribute("email", email);
            req.setAttribute("errorMessage", claim.getMessage());
            forward(req, resp);
            return;
        }

        req.setAttribute("step", "success");
        req.setAttribute("registerMode", "linked");
        forward(req, resp);
    }

    // ===== Gửi lại mã OTP (dùng chung cho cả 2 nhánh) =====

    private void handleResend(HttpServletRequest req, HttpServletResponse resp, VerificationCode.Purpose purpose)
            throws ServletException, IOException {
        String phone = getTrimmed(req, "phone");
        String email = getTrimmed(req, "email");
        boolean isClaim = purpose == VerificationCode.Purpose.CLAIM;

        ServiceResult<Void> sendResult = verificationCodeService.sendCode(email, purpose, null);
        if (sendResult.isSuccess()) {
            req.setAttribute("successMessage", Messages.MSG45_CODE_RESENT);
        }
        String error = sendResult.isSuccess() ? null : sendResult.getMessage();

        if (isClaim) {
            showClaimOtpStep(req, resp, phone, email, error);
        } else {
            showOtpStep(req, resp, phone, email, req.getParameter("fullName"),
                    req.getParameter("dateOfBirth"), req.getParameter("password"), error);
        }
    }

    // ===== Tiện ích render bước =====

    private void showOtpStep(HttpServletRequest req, HttpServletResponse resp,
                             String phone, String email, String fullName, String dob,
                             String password, String errorMessage) throws ServletException, IOException {
        setOtpTimers(req, email);
        req.setAttribute("step", "otp");
        req.setAttribute("phone", phone);
        req.setAttribute("email", email);
        req.setAttribute("fullName", fullName);
        req.setAttribute("dateOfBirth", dob);
        req.setAttribute("registerMode", "new");
        req.setAttribute("password", password);
        if (errorMessage != null) {
            req.setAttribute("errorMessage", errorMessage);
        }
        forward(req, resp);
    }

    private void showClaimOtpStep(HttpServletRequest req, HttpServletResponse resp,
                                  String phone, String email, String errorMessage)
            throws ServletException, IOException {
        setOtpTimers(req, email);
        req.setAttribute("step", "claim-otp");
        req.setAttribute("phone", phone);
        req.setAttribute("email", email);
        if (errorMessage != null) {
            req.setAttribute("errorMessage", errorMessage);
        }
        forward(req, resp);
    }

    private void setOtpTimers(HttpServletRequest req, String email) {
        LocalDateTime now = DateTimeUtil.now();
        req.setAttribute("maskedEmail", maskEmail(email));
        req.setAttribute("otpExpiresAt", now.plusMinutes(15).format(ISO_FORMATTER));
        req.setAttribute("resendUnlockAt", now.plusSeconds(60).format(ISO_FORMATTER));
    }

    private void populateFormFields(HttpServletRequest req, String phone, String email, String fullName, String dob) {
        req.setAttribute("step", "form");
        req.setAttribute("phone", phone);
        req.setAttribute("email", email);
        req.setAttribute("fullName", fullName);
        req.setAttribute("dateOfBirth", dob);
        req.setAttribute("registerMode", "new");
    }

    private void forward(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.getRequestDispatcher(VIEW).forward(req, resp);
    }

    private String getTrimmed(HttpServletRequest req, String paramName) {
        String val = req.getParameter(paramName);
        return val != null ? val.trim() : null;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        return (name.length() <= 2 ? name.charAt(0) : name.substring(0, 2)) + "***" + domain;
    }
}
