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
 * MÀN QUÊN MẬT KHẨU — /forgot-password, 3 bước trên CÙNG 1 trang JSP:
 *   bước "email"  nhập email → gửi mã 6 số
 *   bước "code"   nhập mã (đồng hồ hết hạn + nút Gửi lại mã)
 *   bước "reset"  đặt mật khẩu mới
 * Đang ở bước nào = xem session (resolveStep):
 *   có RECOVERY_ACCOUNT_ID → "reset" · có RECOVERY_EMAIL → "code" · không có gì → "email"
 * GET  → doGet:  vẽ bước hiện tại (forgot-password.jsp)
 * POST → doPost: action = send | resend | verify | reset → PasswordRecoveryService → redirect về GET (PRG)
 */
@WebServlet("/forgot-password")
public class ForgotPasswordServlet extends HttpServlet {
    // File JSP: /WEB-INF/jsp/auth/forgot-password.jsp
    private static final String VIEW = "auth/forgot-password";
    // Tên 3 bước — JSP đọc qua attribute "recoveryStep" để biết vẽ form nào.
    private static final String STEP_EMAIL = "email";
    private static final String STEP_CODE = "code";
    private static final String STEP_RESET = "reset";

    private final PasswordRecoveryService passwordRecoveryService = new PasswordRecoveryService();

    // GET /forgot-password[?restart=1]: vẽ bước hiện tại.
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();

        // B1: link "Dùng email khác" (?restart=1) → xóa dữ liệu khôi phục trong session → quay lại bước email
        if ("1".equals(request.getParameter("restart"))) {
            clearRecovery(session);
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B2: vẽ bước hiện tại (showCurrentStep → forwardStep → JSP)
        showCurrentStep(request, response, session);
    }

    // POST /forgot-password: 4 nút của 3 bước, phân theo ô ẩn action.
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();

        String action = RequestUtil.getTrimmedParam(request, "action");

        // send = "Gửi mã xác thực" · resend = "Gửi lại mã" · verify = "Xác nhận" mã · reset = "Đặt mật khẩu mới"
        switch (action == null ? "" : action) {
            case "send" -> handleSend(request, response, session);
            case "resend" -> handleResend(request, response, session);
            case "verify" -> handleVerify(request, response, session);
            case "reset" -> handleReset(request, response, session);
            default -> RequestUtil.redirect(request, response, "/forgot-password");
        }
    }

    // action=send — bấm "Gửi mã xác thực" (bước email).
    private void handleSend(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        String email = RequestUtil.getTrimmedParam(request, "email");

        // B1: PasswordRecoveryService.requestCode: kiểm email → AccountDAO.findByEmail → gửi mail mã 6 số
        //     VerificationCodes: thêm 1 dòng mã mới (Purpose = Recovery)
        ServiceResult<Void> result = passwordRecoveryService.requestCode(email);

        // B2: lỗi (email trống / sai định dạng / không có tài khoản / bị khóa / gửi quá nhiều) → vẽ lại bước email kèm lỗi
        if (!result.isSuccess()) {
            request.setAttribute("email", email);
            forwardStep(request, response, STEP_EMAIL, result);
            return;
        }

        // B3: thành công → lưu email vào session (RECOVERY_EMAIL) → lần GET sau tự sang bước "code"
        clearRecovery(session);
        session.setAttribute(SessionKey.RECOVERY_EMAIL, email);

        // B4: toast MSG42 "Đã gửi mã tới …" + redirect về GET
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    // action=resend — bấm "Gửi lại mã" (bước code).
    private void handleResend(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws IOException {
        // B1: lấy email đang khôi phục từ session (không có → bỏ qua, quay về trang)
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);

        if (email != null) {
            // B2: gửi mã mới (cùng hàm với handleSend) → toast MSG45 "Đã gửi lại mã" hoặc câu lỗi đầu tiên
            //     (VD bấm sớm quá 60 giây → MSG70, gửi quá nhiều lần → khóa MSG61)
            ServiceResult<Void> result = passwordRecoveryService.requestCode(email);
            if (result.isSuccess()) {
                RequestUtil.setFlashToast(request, "success", Messages.MSG45_CODE_RESENT);
            } else {
                RequestUtil.setFlashToast(request, "error", firstError(result));
            }
        }
        // B3: redirect về GET (vẽ lại bước code với đồng hồ mới)
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    // action=verify — bấm "Xác nhận" mã 6 số (bước code).
    private void handleVerify(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: chưa qua bước email (session không có email) → về đầu
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);
        if (email == null) {
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        String code = RequestUtil.getTrimmedParam(request, "code");

        // B2: PasswordRecoveryService.verifyCode → VerificationCodeService.verifyCode: so mã mới nhất của email
        //     sai → tăng số lần sai (sai quá số lần → khóa) · đúng → VerificationCodes: đánh dấu đã dùng
        ServiceResult<VerificationCode> result = passwordRecoveryService.verifyCode(email, code);

        // B3: sai / hết hạn / bị khóa → vẽ lại bước code kèm lỗi
        if (!result.isSuccess()) {
            forwardStep(request, response, STEP_CODE, result);
            return;
        }

        // B4: đúng → lưu AccountId + hạn của mã vào session → lần GET sau sang bước "reset"
        //     (hạn đặt mật khẩu = hạn của mã, quá hạn thì phải làm lại từ đầu)
        VerificationCode usedCode = result.getData();
        session.setAttribute(SessionKey.RECOVERY_ACCOUNT_ID, usedCode.getAccountId());
        session.setAttribute(SessionKey.RECOVERY_DEADLINE, usedCode.getExpiresAt());
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    // action=reset — bấm "Đặt mật khẩu mới" (bước reset).
    private void handleReset(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: chưa xác thực mã (session không có AccountId) → về đầu
        Integer accountId = (Integer) session.getAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B2: quá hạn của mã → xóa session khôi phục + toast MSG44 "Mã đã hết hạn" → về bước email
        if (isResetExpired(session)) {
            clearRecovery(session);
            RequestUtil.setFlashToast(request, "error", Messages.MSG44_CODE_EXPIRED);
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B3: PasswordRecoveryService.resetPassword: kiểm mật khẩu → lưu hash mới → đăng xuất mọi thiết bị
        //     Accounts: PasswordHash mới, số lần đăng nhập sai = 0 · AuditLogs: thêm 1 dòng
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        ServiceResult<Void> result = passwordRecoveryService.resetPassword(accountId, password, confirmPassword);

        // B4: lỗi ở ô nhập (mật khẩu yếu / nhập lại không khớp) → vẽ lại bước reset
        //     lỗi khác (tài khoản bị khóa, DB lỗi) → xóa session khôi phục, về bước email
        if (!result.isSuccess()) {
            if (result.hasFieldErrors()) {
                forwardStep(request, response, STEP_RESET, result);
            } else {
                clearRecovery(session);
                forwardStep(request, response, STEP_EMAIL, result);
            }
            return;
        }

        // B5: thành công → xóa session khôi phục (session có thể đã bị hủy ở B3 vì "đăng xuất mọi thiết bị" → bỏ qua lỗi)
        try {
            clearRecovery(session);
        } catch (IllegalStateException sessionAlreadyInvalidated) {
        }

        // B6: toast MSG95 "Đặt lại mật khẩu thành công" → về /login
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/login");
    }

    /**
     * HÀM PHỤ (doGet) — tìm bước hiện tại rồi vẽ; đang ở bước reset mà quá hạn → báo MSG44, quay về bước email.
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
     * HÀM PHỤ (doGet + các handle khi lỗi) — đưa dữ liệu sang JSP rồi forward:
     *   recoveryStep = "email" | "code" | "reset" (JSP chọn form) · recoveryEmail = email đang khôi phục
     *   fieldErrors = lỗi từng ô (Map: email / code / password / confirmPassword) · errorMessage = lỗi chung (banner đỏ)
     *   bước code thêm: đồng hồ + gợi ý số lần (setCodeTimers)
     */
    private void forwardStep(HttpServletRequest request, HttpServletResponse response,
            String step, ServiceResult<?> failedResult) throws ServletException, IOException {
        String email = (String) request.getSession().getAttribute(SessionKey.RECOVERY_EMAIL);

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
     * HÀM PHỤ (forwardStep, bước code) — số liệu cho đồng hồ và gợi ý dưới ô mã:
     *   codeSecondsLeft   ← mã mới nhất (VerificationCodeDAO) → đồng hồ "Mã hết hạn sau mm:ss"
     *   resendSecondsLeft ← lúc gửi + 60 giây                  → nút "Gửi lại mã (còn 45s)"
     *   attemptHint       ← VerificationAttemptInfo.attemptsLeft → "Còn 3 lần nhập" (lần cuối: chữ vàng MSG63)
     *   resendHint        ← VerificationAttemptInfo.resendsLeft  → "Còn 2 lần gửi lại" (hết lượt: chữ vàng MSG65)
     */
    private void setCodeTimers(HttpServletRequest request, String email) {
        long codeSecondsLeft = 0;
        long resendSecondsLeft = 0;
        try {
            VerificationCode latestCode = passwordRecoveryService.findLatestRecoveryCode(email);
            if (latestCode != null) {
                LocalDateTime now = DateTimeUtil.now();
                codeSecondsLeft = secondsUntil(now, latestCode.getExpiresAt());
                resendSecondsLeft = secondsUntil(now, latestCode.getSentAt().plusSeconds(AppConstants.CODE_RESEND_GAP_SECONDS));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        request.setAttribute("codeSecondsLeft", codeSecondsLeft);
        request.setAttribute("resendSecondsLeft", resendSecondsLeft);

        ServiceResult<VerificationAttemptInfo> attemptResult = passwordRecoveryService.getRecoveryAttemptInfo(email);
        if (!attemptResult.isSuccess() || attemptResult.getData().isLocked()) {
            return;
        }
        VerificationAttemptInfo info = attemptResult.getData();

        if (info.getAttemptsLeft() > 0) {
            boolean isLastAttempt = info.getAttemptsLeft() == 1;

            request.setAttribute("attemptHint", isLastAttempt
                    ? String.format(Messages.MSG63_CODE_LAST_ATTEMPT, info.getLockMinutes())
                    : String.format(Messages.MSG62_CODE_ATTEMPTS_LEFT, info.getAttemptsLeft()));
            request.setAttribute("attemptHintWarn", isLastAttempt);
        }

        boolean isOutOfResends = info.getResendsLeft() == 0;

        request.setAttribute("resendHint", isOutOfResends
                ? String.format(Messages.MSG65_CODE_RESEND_WILL_LOCK, info.getLockMinutes())
                : String.format(Messages.MSG64_CODE_RESENDS_LEFT, info.getResendsLeft()));
        request.setAttribute("resendHintWarn", isOutOfResends);
    }

    /**
     * HÀM PHỤ — bước hiện tại theo session: có AccountId → reset · có email → code · không → email.
     */
    private String resolveStep(HttpSession session) {
        if (session.getAttribute(SessionKey.RECOVERY_ACCOUNT_ID) != null) {
            return STEP_RESET;
        }
        if (session.getAttribute(SessionKey.RECOVERY_EMAIL) != null) {
            return STEP_CODE;
        }
        return STEP_EMAIL;
    }

    /**
     * HÀM PHỤ — đã quá hạn đặt mật khẩu chưa (hạn = hạn của mã vừa xác thực); không có hạn → coi như quá.
     */
    private boolean isResetExpired(HttpSession session) {
        LocalDateTime deadline = (LocalDateTime) session.getAttribute(SessionKey.RECOVERY_DEADLINE);
        return deadline == null || !DateTimeUtil.now().isBefore(deadline);
    }

    /**
     * HÀM PHỤ — xóa 3 khóa khôi phục trong session (email, AccountId, hạn) → quay về bước email.
     */
    private void clearRecovery(HttpSession session) {
        session.removeAttribute(SessionKey.RECOVERY_EMAIL);
        session.removeAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        session.removeAttribute(SessionKey.RECOVERY_DEADLINE);
    }

    /**
     * HÀM PHỤ — số giây từ now tới until, âm thì trả 0.
     */
    private long secondsUntil(LocalDateTime now, LocalDateTime until) {
        return Math.max(0, Duration.between(now, until).getSeconds());
    }

    /**
     * HÀM PHỤ (handleResend) — câu lỗi để hiện toast: lỗi ô đầu tiên, không có thì câu lỗi chung.
     */
    private String firstError(ServiceResult<?> result) {
        if (result.hasFieldErrors()) {
            return result.getFieldErrors().values().iterator().next();
        }
        return result.getMessage();
    }
}
