package com.gms.servlet.auth;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.dto.VerificationAttemptInfo;
import com.gms.entity.VerificationCode;
import com.gms.service.PasswordRecoveryService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Quên mật khẩu (#3 Forgot Password, UC-03) — 1 trang, 3 bước: email → mã → mật khẩu mới.
 *
 * Tiến độ lưu trong session (SessionKey.RECOVERY_*), nên không thể nhảy thẳng sang bước sau:
 *   chưa có RECOVERY_EMAIL      → bước "email"
 *   có email, chưa xác minh mã  → bước "code"
 *   đã xác minh (còn hạn)       → bước "reset"
 *
 * POST theo tham số action: send | resend | verify | reset. Thành công thì redirect (Post/Redirect/Get).
 * GET ?restart=1 → xóa tiến độ, làm lại từ bước nhập email.
 *
 * @author Le Cong Minh
 */
@WebServlet("/forgot-password")
public class ForgotPasswordServlet extends HttpServlet {

    private static final String VIEW = "auth/forgot-password";
    private static final String STEP_EMAIL = "email";
    private static final String STEP_CODE = "code";
    private static final String STEP_RESET = "reset";

    private final PasswordRecoveryService passwordRecoveryService = new PasswordRecoveryService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();

        // B1: bấm "Dùng email khác" → làm lại từ đầu
        if ("1".equals(request.getParameter("restart"))) {
            clearRecovery(session);
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B2: hiện đúng bước theo tiến độ trong session
        showCurrentStep(request, response, session);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        String action = RequestUtil.getTrimmedParam(request, "action");

        switch (action == null ? "" : action) {
            case "send":
                handleSend(request, response, session);
                break;
            case "resend":
                handleResend(request, response, session);
                break;
            case "verify":
                handleVerify(request, response, session);
                break;
            case "reset":
                handleReset(request, response, session);
                break;
            default:
                RequestUtil.redirect(request, response, "/forgot-password");
        }
    }

    // ===== Bước 1: nhập email → gửi mã =====

    private void handleSend(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        String email = RequestUtil.getTrimmedParam(request, "email");
        ServiceResult<Void> result = passwordRecoveryService.requestCode(email);

        if (!result.isSuccess()) {
            request.setAttribute("email", email);
            forwardStep(request, response, STEP_EMAIL, result);
            return;
        }

        // Bắt đầu tiến độ mới cho email này
        clearRecovery(session);
        session.setAttribute(SessionKey.RECOVERY_EMAIL, email);
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    // ===== Bước 2: gửi lại mã / nhập mã =====

    private void handleResend(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws IOException {
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);
        if (email != null) {
            ServiceResult<Void> result = passwordRecoveryService.requestCode(email);
            String toastType = result.isSuccess() ? "success" : "error";
            String toastMessage = result.isSuccess() ? Messages.MSG45_CODE_RESENT : firstError(result);
            RequestUtil.setFlashToast(request, toastType, toastMessage);
        }
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    private void handleVerify(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);
        if (email == null) {
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        String code = RequestUtil.getTrimmedParam(request, "code");
        ServiceResult<VerificationCode> result = passwordRecoveryService.verifyCode(email, code);

        if (!result.isSuccess()) {
            forwardStep(request, response, STEP_CODE, result);
            return;
        }

        // Mã đúng → mở bước 3, hạn làm bước 3 = hạn của mã
        VerificationCode usedCode = result.getData();
        session.setAttribute(SessionKey.RECOVERY_ACCOUNT_ID, usedCode.getAccountId());
        session.setAttribute(SessionKey.RECOVERY_DEADLINE, usedCode.getExpiresAt());
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    // ===== Bước 3: đặt mật khẩu mới =====

    private void handleReset(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        Integer accountId = (Integer) session.getAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }
        if (isResetExpired(session)) {
            clearRecovery(session);
            RequestUtil.setFlashToast(request, "error", Messages.MSG44_CODE_EXPIRED);
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        ServiceResult<Void> result = passwordRecoveryService.resetPassword(accountId, password, confirmPassword);

        if (!result.isSuccess()) {
            if (result.hasFieldErrors()) {
                forwardStep(request, response, STEP_RESET, result);
            } else {
                clearRecovery(session);
                forwardStep(request, response, STEP_EMAIL, result);
            }
            return;
        }

        // Thành công: service đã đăng xuất mọi phiên của tài khoản — phiên hiện tại có thể đã bị hủy
        try {
            clearRecovery(session);
        } catch (IllegalStateException sessionAlreadyInvalidated) {
            // Người dùng đang đăng nhập đúng tài khoản này trên trình duyệt hiện tại — phiên đã bị hủy, bỏ qua
        }
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/login");
    }

    // ===== Hiển thị =====

    /**
     * Chọn bước theo session rồi forward JSP.
     */
    private void showCurrentStep(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        String step = resolveStep(session);
        if (STEP_RESET.equals(step) && isResetExpired(session)) {
            clearRecovery(session);
            request.setAttribute("errorMessage", Messages.MSG44_CODE_EXPIRED);
            step = STEP_EMAIL;
        }
        forwardStep(request, response, step, null);
    }

    /**
     * Forward JSP ở 1 bước, kèm lỗi (nếu có) và đồng hồ của bước nhập mã.
     */
    private void forwardStep(HttpServletRequest request, HttpServletResponse response,
            String step, ServiceResult<?> failedResult) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);

        if (failedResult != null) {
            request.setAttribute("fieldErrors", failedResult.getFieldErrors());
            if (!failedResult.hasFieldErrors()) {
                request.setAttribute("errorMessage", failedResult.getMessage());
            }
        }
        if (STEP_CODE.equals(step)) {
            setCodeTimers(request, email);
        }

        request.setAttribute("recoveryStep", step);
        request.setAttribute("recoveryEmail", email);
        RequestUtil.forward(request, response, VIEW);
    }

    /**
     * Tính 2 đồng hồ cho bước nhập mã (GBR-27): số giây còn lại của mã và số giây chờ trước khi được "Gửi lại mã".
     * Server tính, JSP chỉ hiển thị (không dựa vào đồng hồ máy khách).
     */
    private void setCodeTimers(HttpServletRequest request, String email) {
        long codeSecondsLeft = 0;
        long resendSecondsLeft = 0;
        try {
            VerificationCode latestCode = passwordRecoveryService.findLatestRecoveryCode(email);
            if (latestCode != null) {
                LocalDateTime now = DateTimeUtil.now();
                codeSecondsLeft = secondsUntil(now, latestCode.getExpiresAt());
                LocalDateTime resendAllowedAt = latestCode.getSentAt().plusSeconds(AppConstants.CODE_RESEND_GAP_SECONDS);
                resendSecondsLeft = secondsUntil(now, resendAllowedAt);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        request.setAttribute("codeSecondsLeft", codeSecondsLeft);
        request.setAttribute("resendSecondsLeft", resendSecondsLeft);
        setAttemptHints(request, email);
    }

    /**
     * Dòng gợi ý số lần còn lại (MSG62–MSG65). Email đang bị khóa thì không hiện — chỉ để MSG61.
     */
    private void setAttemptHints(HttpServletRequest request, String email) {
        ServiceResult<VerificationAttemptInfo> result = passwordRecoveryService.getRecoveryAttemptInfo(email);
        if (!result.isSuccess() || result.getData().isLocked()) {
            return;
        }
        VerificationAttemptInfo info = result.getData();

        if (info.getAttemptsLeft() > 0) {
            boolean isLastAttempt = info.getAttemptsLeft() == 1;
            String attemptHint = isLastAttempt
                    ? String.format(Messages.MSG63_CODE_LAST_ATTEMPT, info.getLockMinutes())
                    : String.format(Messages.MSG62_CODE_ATTEMPTS_LEFT, info.getAttemptsLeft());
            request.setAttribute("attemptHint", attemptHint);
            request.setAttribute("attemptHintWarn", isLastAttempt);
        }

        boolean isNoResendLeft = info.getResendsLeft() == 0;
        String resendHint = isNoResendLeft
                ? String.format(Messages.MSG65_CODE_RESEND_WILL_LOCK, info.getLockMinutes())
                : String.format(Messages.MSG64_CODE_RESENDS_LEFT, info.getResendsLeft());
        request.setAttribute("resendHint", resendHint);
        request.setAttribute("resendHintWarn", isNoResendLeft);
    }

    // ===== Hàm phụ =====

    private String resolveStep(HttpSession session) {
        if (session.getAttribute(SessionKey.RECOVERY_ACCOUNT_ID) != null) {
            return STEP_RESET;
        }
        if (session.getAttribute(SessionKey.RECOVERY_EMAIL) != null) {
            return STEP_CODE;
        }
        return STEP_EMAIL;
    }

    private boolean isResetExpired(HttpSession session) {
        LocalDateTime deadline = (LocalDateTime) session.getAttribute(SessionKey.RECOVERY_DEADLINE);
        return deadline == null || !DateTimeUtil.now().isBefore(deadline);
    }

    private void clearRecovery(HttpSession session) {
        session.removeAttribute(SessionKey.RECOVERY_EMAIL);
        session.removeAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        session.removeAttribute(SessionKey.RECOVERY_DEADLINE);
    }

    private long secondsUntil(LocalDateTime now, LocalDateTime until) {
        return Math.max(0, Duration.between(now, until).getSeconds());
    }

    /** Lỗi chung hoặc lỗi dưới ô đầu tiên — dùng làm nội dung toast. */
    private String firstError(ServiceResult<?> result) {
        if (result.hasFieldErrors()) {
            return result.getFieldErrors().values().iterator().next();
        }
        return result.getMessage();
    }
}
