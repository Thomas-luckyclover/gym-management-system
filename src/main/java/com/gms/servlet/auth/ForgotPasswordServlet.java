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
 * SERVLET MÀN QUÊN MẬT KHẨU — /forgot-password (UC-03). 3 bước trên CÙNG 1 trang forgot-password.jsp:
 *   bước "email"  nhập email → gửi mã 6 số
 *   bước "code"   nhập mã (đồng hồ hết hạn + nút Gửi lại mã)
 *   bước "reset"  đặt mật khẩu mới
 *
 * VIỆC CỦA SERVLET: đọc tham số → gọi PasswordRecoveryService → lưu bước vào session → forward JSP hoặc redirect (PRG).
 *   Servlet KHÔNG kiểm luật (định dạng email, số lần sai, độ mạnh mật khẩu): Service làm hết.
 * ĐANG Ở BƯỚC NÀO = xem session (resolveStep): có RECOVERY_ACCOUNT_ID → "reset" · có RECOVERY_EMAIL → "code" · không có gì → "email".
 *
 *   URL / nút                                   Servlet gọi                                   Kết quả
 *   GET  /forgot-password                        doGet → showCurrentStep → forwardStep         forward forgot-password.jsp (recoveryStep)
 *   GET  /forgot-password?restart=1              doGet → clearRecovery                         redirect về bước email ("Dùng email khác")
 *   POST action=send     "Gửi mã xác thực"       handleSend → requestCode                      toast MSG42 + redirect / lỗi ô email
 *   POST action=resend   "Gửi lại mã"            handleResend → requestCode                    toast MSG45 / câu lỗi + redirect
 *   POST action=verify   "Xác nhận" mã           handleVerify → verifyCode                     redirect sang bước reset / lỗi ô mã
 *   POST action=reset    "Đặt mật khẩu mới"      handleReset → resetPassword                   toast MSG95 + redirect /login
 *
 * BỐ CỤC FILE — mỗi HÀM CHÍNH đứng trước, các hàm phụ của nó nằm ngay bên dưới:
 *   HÀM CHÍNH 1  doGet    vẽ bước hiện tại       → showCurrentStep, resolveStep, isResetExpired, forwardStep, setCodeTimers, secondsUntil
 *   HÀM CHÍNH 2  doPost   4 nút của 3 bước       → handleSend, handleResend, firstError, handleVerify, handleReset
 *   DÙNG CHUNG            clearRecovery
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



    // ============================================================================================================
    // HÀM CHÍNH 1. doGet — vẽ bước hiện tại (GET /forgot-password[?restart=1])
    //   Ai gọi: trình duyệt (link "Quên mật khẩu?" ở màn đăng nhập, và mọi redirect sau POST)
    //   Hàm phụ ngay dưới: showCurrentStep → resolveStep · isResetExpired → forwardStep → setCodeTimers → secondsUntil
    // ============================================================================================================

    /**
     * HÀM CHÍNH 1 — vẽ bước hiện tại (email / mã / mật khẩu mới).
     *   Ai gọi: trình duyệt mở /forgot-password, và mọi redirect sau POST
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();

        // B1: bấm "Dùng email khác" → xóa dữ liệu cũ, về bước email
        if ("1".equals(request.getParameter("restart"))) {
            clearRecovery(session);
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B2: vẽ bước hiện tại
        showCurrentStep(request, response, session);
    }

    /** HÀM PHỤ (doGet B2) — tìm bước hiện tại rồi vẽ; đang ở bước reset mà quá hạn → báo MSG44, quay về bước email. */
    private void showCurrentStep(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        String step = resolveStep(session);

        if (STEP_RESET.equals(step) && isResetExpired(session)) {
            clearRecovery(session);
            request.setAttribute("errorMessage", Messages.MSG44_CODE_EXPIRED);
            step = STEP_EMAIL;
        }

        forwardStep(
                request,
                response,
                step,
                null);
    }

    /** HÀM PHỤ (showCurrentStep) — bước hiện tại theo session: có AccountId → reset · có email → code · không → email. */
    private String resolveStep(HttpSession session) {
        if (session.getAttribute(SessionKey.RECOVERY_ACCOUNT_ID) != null) {
            return STEP_RESET;
        }
        if (session.getAttribute(SessionKey.RECOVERY_EMAIL) != null) {
            return STEP_CODE;
        }
        return STEP_EMAIL;
    }

    /** HÀM PHỤ (showCurrentStep, handleReset B2) — đã quá hạn đặt mật khẩu chưa (hạn = hạn của mã vừa xác thực); không có hạn → coi như quá. */
    private boolean isResetExpired(HttpSession session) {
        LocalDateTime deadline = (LocalDateTime) session.getAttribute(SessionKey.RECOVERY_DEADLINE);
        return deadline == null || !DateTimeUtil.now().isBefore(deadline);
    }

    /**
     * HÀM PHỤ (showCurrentStep; handleSend / handleVerify / handleReset khi lỗi) — đưa dữ liệu sang JSP rồi forward:
     *   recoveryStep = "email" | "code" | "reset" (JSP chọn form) · recoveryEmail = email đang khôi phục
     *   fieldErrors = lỗi từng ô (Map: email / code / password / confirmPassword) · errorMessage = lỗi chung (banner đỏ)
     *   bước code thêm: đồng hồ + gợi ý số lần (setCodeTimers)
     */
    private void forwardStep(
            HttpServletRequest request,
            HttpServletResponse response,
            String step,
            ServiceResult<?> failedResult) throws ServletException, IOException {
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
     * HÀM PHỤ (forwardStep, chỉ bước code) — số liệu cho đồng hồ và gợi ý dưới ô mã:
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

    /** HÀM PHỤ (setCodeTimers) — số giây từ now tới until, âm thì trả 0. */
    private long secondsUntil(LocalDateTime now, LocalDateTime until) {
        return Math.max(0, Duration.between(now, until).getSeconds());
    }



    // ============================================================================================================
    // HÀM CHÍNH 2. doPost — 4 nút: send · resend · verify · reset (ô ẩn "action" trong form JSP)
    //   Ai gọi: form của forgot-password.jsp  ·  Gọi xuống: PasswordRecoveryService
    //   Hàm phụ ngay dưới, theo thứ tự nút: handleSend · handleResend → firstError · handleVerify · handleReset
    // ============================================================================================================

    /**
     * HÀM CHÍNH 2 — 4 nút của màn, chia theo ô ẩn "action".
     *   Ai gọi: form trong forgot-password.jsp
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();

        String action = RequestUtil.getTrimmedParam(request, "action");

        // B1: chuyển cho đúng hàm theo nút đã bấm
        switch (action == null ? "" : action) {
            case "send" -> handleSend(request, response, session);
            case "resend" -> handleResend(request, response, session);
            case "verify" -> handleVerify(request, response, session);
            case "reset" -> handleReset(request, response, session);
            default -> RequestUtil.redirect(request, response, "/forgot-password");
        }
    }

    /**
     * HÀM PHỤ (doPost, action=send) — bấm "Gửi mã xác thực" (bước email).
     *   Thành công → lưu RECOVERY_EMAIL vào session → lần GET sau sang bước "code". Lỗi → vẽ lại bước email.
     */
    private void handleSend(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        String email = RequestUtil.getTrimmedParam(request, "email");

        // B1: gửi mã tới email
        ServiceResult<Void> result = passwordRecoveryService.requestCode(email);

        // B2: lỗi → vẽ lại bước email kèm lỗi
        if (!result.isSuccess()) {
            request.setAttribute("email", email);
            forwardStep(
                    request,
                    response,
                    STEP_EMAIL,
                    result);
            return;
        }

        // B3: nhớ email trong session → lần mở sau sang bước nhập mã
        clearRecovery(session);
        session.setAttribute(SessionKey.RECOVERY_EMAIL, email);

        // B4: báo đã gửi, về trang
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    /** HÀM PHỤ (doPost, action=resend) — bấm "Gửi lại mã" (bước code): gửi mã mới cho email trong session, báo bằng toast. */
    private void handleResend(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws IOException {
        // B1: lấy email đang khôi phục
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);

        if (email != null) {
            // B2: gửi mã mới, báo kết quả bằng toast
            ServiceResult<Void> result = passwordRecoveryService.requestCode(email);
            if (result.isSuccess()) {
                RequestUtil.setFlashToast(request, "success", Messages.MSG45_CODE_RESENT);
            } else {
                RequestUtil.setFlashToast(request, "error", firstError(result));
            }
        }

        // B3: về trang (đồng hồ chạy lại)
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    /** HÀM PHỤ (handleResend) — câu lỗi để hiện toast: lỗi ô đầu tiên, không có thì câu lỗi chung. */
    private String firstError(ServiceResult<?> result) {
        if (result.hasFieldErrors()) {
            return result.getFieldErrors().values().iterator().next();
        }
        return result.getMessage();
    }

    /**
     * HÀM PHỤ (doPost, action=verify) — bấm "Xác nhận" mã 6 số (bước code).
     *   Đúng → lưu RECOVERY_ACCOUNT_ID + RECOVERY_DEADLINE vào session → lần GET sau sang bước "reset". Sai → vẽ lại bước code.
     */
    private void handleVerify(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: chưa qua bước email → về đầu
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);
        if (email == null) {
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        String code = RequestUtil.getTrimmedParam(request, "code");

        // B2: kiểm mã
        ServiceResult<VerificationCode> result = passwordRecoveryService.verifyCode(email, code);

        // B3: sai / hết hạn → vẽ lại bước nhập mã
        if (!result.isSuccess()) {
            forwardStep(
                    request,
                    response,
                    STEP_CODE,
                    result);
            return;
        }

        // B4: đúng → nhớ tài khoản + hạn trong session → sang bước đặt mật khẩu
        VerificationCode usedCode = result.getData();
        session.setAttribute(SessionKey.RECOVERY_ACCOUNT_ID, usedCode.getAccountId());
        session.setAttribute(SessionKey.RECOVERY_DEADLINE, usedCode.getExpiresAt());
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    /**
     * HÀM PHỤ (doPost, action=reset) — bấm "Đặt mật khẩu mới" (bước reset).
     *   Thành công → xóa session khôi phục → toast MSG95 → /login. Lỗi ô nhập → vẽ lại bước reset; lỗi khác → về bước email.
     */
    private void handleReset(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: chưa xác thực mã → về đầu
        Integer accountId = (Integer) session.getAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B2: quá hạn → báo mã hết hạn, về bước email
        if (isResetExpired(session)) {
            clearRecovery(session);
            RequestUtil.setFlashToast(request, "error", Messages.MSG44_CODE_EXPIRED);
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B3: đặt mật khẩu mới
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        ServiceResult<Void> result = passwordRecoveryService.resetPassword(accountId, password, confirmPassword);

        // B4: lỗi ô nhập → vẽ lại bước này · lỗi khác → về bước email
        if (!result.isSuccess()) {
            if (result.hasFieldErrors()) {
                forwardStep(
                        request,
                        response,
                        STEP_RESET,
                        result);
            } else {
                clearRecovery(session);
                forwardStep(
                        request,
                        response,
                        STEP_EMAIL,
                        result);
            }
            return;
        }

        // B5: xóa dữ liệu khôi phục (session có thể đã bị hủy ở B3 → bỏ qua)
        try {
            clearRecovery(session);
        } catch (IllegalStateException sessionAlreadyInvalidated) {
        }

        // B6: báo thành công, sang trang đăng nhập
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/login");
    }



    // ============================================================================================================
    // DÙNG CHUNG — xóa dữ liệu khôi phục trong session (doGet ?restart=1, handleSend, handleReset, showCurrentStep)
    // ============================================================================================================

    /** HÀM PHỤ (dùng chung) — xóa 3 khóa khôi phục trong session (email, AccountId, hạn) → lần GET sau về bước email. */
    private void clearRecovery(HttpSession session) {
        session.removeAttribute(SessionKey.RECOVERY_EMAIL);
        session.removeAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        session.removeAttribute(SessionKey.RECOVERY_DEADLINE);
    }
}
