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
 * QUÊN MẬT KHẨU — /forgot-password (màn #3, UC-03). 3 bước trong CÙNG 1 trang:
 *
 *   Bước 1 "email"  khách nhập email      → POST action=send    → handleSend
 *   Bước 2 "code"   khách nhập mã 6 số    → POST action=verify  → handleVerify   (+ action=resend → handleResend)
 *   Bước 3 "reset"  khách đặt mật khẩu mới → POST action=reset   → handleReset
 *
 * ĐANG Ở BƯỚC NÀO? Không đọc từ URL, mà suy ra từ SESSION (resolveStep):
 *   có RECOVERY_ACCOUNT_ID  → bước 3 (đã nhập đúng mã)
 *   có RECOVERY_EMAIL       → bước 2 (đã gửi mã)
 *   không có gì             → bước 1
 * → Khách không thể nhảy thẳng sang bước 3 bằng cách sửa URL / form.
 *
 * Mọi POST xong đều redirect về GET /forgot-password (PRG: F5 không gửi lại form).
 * Servlet chỉ điều phối; luật nghiệp vụ nằm ở PasswordRecoveryService.
 *
 *   NHÓM 1. MỞ TRANG        doGet, doPost
 *   NHÓM 2. 4 NÚT BẤM       handleSend, handleResend, handleVerify, handleReset
 *   NHÓM 3. HIỆN TRANG      showCurrentStep, forwardStep, setCodeTimers
 *   NHÓM 4. DÙNG CHUNG      resolveStep, isResetExpired, clearRecovery, secondsUntil, firstError
 */
@WebServlet("/forgot-password")
public class ForgotPasswordServlet extends HttpServlet {

    private static final String VIEW = "auth/forgot-password";
    private static final String STEP_EMAIL = "email";
    private static final String STEP_CODE = "code";
    private static final String STEP_RESET = "reset";

    private final PasswordRecoveryService passwordRecoveryService = new PasswordRecoveryService();


    // =================================================================================================
    // NHÓM 1. MỞ TRANG
    // =================================================================================================

    /**
     * HÀM CHÍNH — trình duyệt gọi khi mở /forgot-password, và sau MỌI lần POST (redirect về đây).
     * ?restart=1 = khách bấm "Dùng email khác" → làm lại từ bước 1.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();

        // B1: "Dùng email khác" → xóa tiến độ, quay về bước 1.
        //     ?restart=1 ← link "Dùng email khác" forgot-password.jsp dòng 138
        if ("1".equals(request.getParameter("restart"))) {
            clearRecovery(session);
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B2: hiện đúng bước theo session.
        showCurrentStep(request, response, session);
    }

    /**
     * HÀM CHÍNH — trình duyệt gọi khi khách bấm 1 trong 4 nút. Ô ẩn "action" cho biết nút nào.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        // ô ẩn "action" của 4 form: send dòng 77, verify dòng 110, resend dòng 128, reset dòng 153 (forgot-password.jsp)
        String action = RequestUtil.getTrimmedParam(request, "action");

        // B1: rẽ theo nút. action lạ (bị sửa tay) → quay về trang như GET.
        switch (action == null ? "" : action) {
            case "send" -> handleSend(request, response, session);
            case "resend" -> handleResend(request, response, session);
            case "verify" -> handleVerify(request, response, session);
            case "reset" -> handleReset(request, response, session);
            default -> RequestUtil.redirect(request, response, "/forgot-password");
        }
    }


    // =================================================================================================
    // NHÓM 2. 4 NÚT BẤM (doPost gọi ở B1)
    // =================================================================================================

    /**
     * HÀM CHÍNH của nút "Gửi mã xác thực" (bước 1, action=send).
     */
    private void handleSend(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: Service kiểm tra email + gửi mã. ô "email" ← forgot-password.jsp dòng 80
        String email = RequestUtil.getTrimmedParam(request, "email");
        ServiceResult<Void> result = passwordRecoveryService.requestCode(email);

        // B2: thất bại → hiện lại bước 1, giữ chữ khách đã gõ, kèm lỗi.
        if (!result.isSuccess()) {
            request.setAttribute("email", email);   // → forgot-password.jsp dòng 81 giữ chữ khách đã gõ
            forwardStep(request, response, STEP_EMAIL, result);
            return;
        }

        // B3: thành công → lưu email vào session = từ giờ đang ở bước 2.
        clearRecovery(session);
        session.setAttribute(SessionKey.RECOVERY_EMAIL, email);

        // B4: toast "Mã đã được gửi tới …" rồi redirect → GET hiện bước 2.
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    /**
     * HÀM CHÍNH của nút "Gửi lại mã" (bước 2, action=resend). Kết quả báo bằng toast.
     */
    private void handleResend(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws IOException {
        // B1: email lấy từ SESSION (không tin form). Không có (session hết hạn) → chỉ quay về trang.
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);

        if (email != null) {
            // B2: gửi lại. Thành công → toast xanh MSG45; thất bại (chưa đủ 60 giây, đang bị khóa…) → toast đỏ.
            ServiceResult<Void> result = passwordRecoveryService.requestCode(email);
            if (result.isSuccess()) {
                RequestUtil.setFlashToast(request, "success", Messages.MSG45_CODE_RESENT);
            } else {
                RequestUtil.setFlashToast(request, "error", firstError(result));
            }
        }
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    /**
     * HÀM CHÍNH của nút "Xác nhận" (bước 2, action=verify).
     */
    private void handleVerify(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: chưa qua bước 1 (hoặc session hết hạn) → về đầu.
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);
        if (email == null) {
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B2: kiểm tra mã với email TRONG SESSION (không lấy email từ form → không đổi được sang email người khác).
        String code = RequestUtil.getTrimmedParam(request, "code");   // ← ô mã 6 số forgot-password.jsp dòng 113
        ServiceResult<VerificationCode> result = passwordRecoveryService.verifyCode(email, code);

        // B3: sai / hết hạn / bị khóa → hiện lại bước 2 kèm lỗi.
        if (!result.isSuccess()) {
            forwardStep(request, response, STEP_CODE, result);
            return;
        }

        // B4: đúng → ghi "đã xác minh tài khoản X" vào session (= sang bước 3).
        //     Hạn làm bước 3 = hạn của mã vừa dùng. Ví dụ mã gửi 10:00, hết hạn 10:15 → phải đặt mật khẩu trước 10:15.
        VerificationCode usedCode = result.getData();
        session.setAttribute(SessionKey.RECOVERY_ACCOUNT_ID, usedCode.getAccountId());
        session.setAttribute(SessionKey.RECOVERY_DEADLINE, usedCode.getExpiresAt());
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    /**
     * HÀM CHÍNH của nút "Đặt mật khẩu mới" (bước 3, action=reset).
     */
    private void handleReset(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: chưa xác minh mã (vd gửi form bằng tay khi mới ở bước 1) → về đầu.
        Integer accountId = (Integer) session.getAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B2: để trang mở quá lâu, quá hạn bước 3 → xóa tiến độ, toast MSG44, làm lại từ đầu.
        if (isResetExpired(session)) {
            clearRecovery(session);
            RequestUtil.setFlashToast(request, "error", Messages.MSG44_CODE_EXPIRED);
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B3: Service kiểm tra 2 ô mật khẩu rồi lưu (accountId lấy từ SESSION, không từ form).
        String password = request.getParameter("password");                 // ← forgot-password.jsp dòng 157
        String confirmPassword = request.getParameter("confirmPassword");   // ← forgot-password.jsp dòng 169
        ServiceResult<Void> result = passwordRecoveryService.resetPassword(accountId, password, confirmPassword);

        // B4: thất bại.
        //     Lỗi gắn ô (mật khẩu sai luật / không khớp) → giữ bước 3 để gõ lại.
        //     Lỗi chung (tài khoản vừa bị khóa, lỗi hệ thống) → bỏ tiến độ, về bước 1 kèm câu lỗi.
        if (!result.isSuccess()) {
            if (result.hasFieldErrors()) {
                forwardStep(request, response, STEP_RESET, result);
            } else {
                clearRecovery(session);
                forwardStep(request, response, STEP_EMAIL, result);
            }
            return;
        }

        // B5: thành công. Service đã đăng xuất MỌI phiên của tài khoản này. Nếu khách đang đăng nhập đúng tài khoản đó
        //     trên trình duyệt này thì session hiện tại cũng đã bị hủy → removeAttribute ném IllegalStateException → bỏ qua.
        try {
            clearRecovery(session);
        } catch (IllegalStateException sessionAlreadyInvalidated) {
            // Session đã bị hủy, không còn gì để xóa.
        }

        // B6: toast MSG49 rồi về Đăng nhập (setFlashToast tự tạo session mới nếu session cũ đã hủy).
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/login");
    }


    // =================================================================================================
    // NHÓM 3. HIỆN TRANG
    // =================================================================================================

    /**
     * HÀM PHỤ — gọi ở B3 của doGet. Đọc bước từ session rồi hiện trang.
     */
    private void showCurrentStep(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: bước hiện tại.
        String step = resolveStep(session);

        // B2: đang ở bước 3 mà đã quá hạn → bỏ tiến độ, hiện bước 1 kèm MSG44.
        if (STEP_RESET.equals(step) && isResetExpired(session)) {
            clearRecovery(session);
            request.setAttribute("errorMessage", Messages.MSG44_CODE_EXPIRED);   // → forgot-password.jsp dòng 62–65
            step = STEP_EMAIL;
        }

        // B3: hiện trang.
        forwardStep(request, response, step, null);
    }

    /**
     * HÀM PHỤ DÙNG CHUNG của nhóm 2 + 3 — gọi ở: B3 showCurrentStep, B2 handleSend, B3 handleVerify, B4 handleReset.
     * Đặt lỗi (nếu có) + dữ liệu của bước rồi forward sang JSP.
     */
    private void forwardStep(HttpServletRequest request, HttpServletResponse response,
            String step, ServiceResult<?> failedResult) throws ServletException, IOException {
        String email = (String) request.getSession().getAttribute(SessionKey.RECOVERY_EMAIL);

        // B1: lỗi gắn ô → hiện chữ đỏ dưới ô (fieldErrors). Lỗi chung → khung đỏ trên cùng (errorMessage).
        if (failedResult != null) {
            // → forgot-password.jsp dòng 84 (email), 117 (code), 163 (password), 173 (confirmPassword)
            request.setAttribute("fieldErrors", failedResult.getFieldErrors());
            if (!failedResult.hasFieldErrors()) {
                request.setAttribute("errorMessage", failedResult.getMessage());   // → forgot-password.jsp dòng 62–65
            }
        }

        // B2: bước 2 cần thêm 2 đồng hồ + gợi ý số lượt còn lại.
        if (STEP_CODE.equals(step)) {
            setCodeTimers(request, email);
        }

        // B3: cho JSP biết bước nào, email nào.
        request.setAttribute("recoveryStep", step);     // → forgot-password.jsp dòng 40 (c:set step) → chọn khối dòng 73 (email) / 95 (code) / 149 (reset)
        request.setAttribute("recoveryEmail", email);   // → forgot-password.jsp dòng 116 "Đã gửi tới …"
        RequestUtil.forward(request, response, VIEW);
    }

    /**
     * HÀM PHỤ — gọi ở B2 của forwardStep (chỉ bước 2).
     * 2 đồng hồ đếm ngược (GBR-27) + 2 dòng gợi ý số lượt còn lại (MSG62–MSG65).
     * Số giây do SERVER tính rồi đưa xuống; JS chỉ đếm lùi (không tin đồng hồ máy khách).
     */
    private void setCodeTimers(HttpServletRequest request, String email) {
        // B1: 2 đồng hồ từ mã gửi gần nhất.
        //     codeSecondsLeft   = còn bao nhiêu giây thì mã hết hạn       (vd 899 → "14:59")
        //     resendSecondsLeft = còn bao nhiêu giây mới được "Gửi lại mã" (vd 59)
        //     Lỗi DB → để 0 (đồng hồ hiện "hết hạn"), không làm hỏng trang.
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
        request.setAttribute("codeSecondsLeft", codeSecondsLeft);       // → forgot-password.jsp dòng 99, 103 (đồng hồ mã)
        request.setAttribute("resendSecondsLeft", resendSecondsLeft);   // → forgot-password.jsp dòng 130 (nút Gửi lại mã)

        // B2: còn mấy lượt? Lỗi, hoặc email đang bị khóa (lúc đó đã có MSG61) → không hiện gợi ý.
        ServiceResult<VerificationAttemptInfo> attemptResult = passwordRecoveryService.getRecoveryAttemptInfo(email);
        if (!attemptResult.isSuccess() || attemptResult.getData().isLocked()) {
            return;
        }
        VerificationAttemptInfo info = attemptResult.getData();

        // B3: gợi ý dưới ô mã — CHỈ khi mã còn dùng được (còn > 0 lần).
        //     còn ≥ 2 lần → MSG62 "Bạn còn n lần nhập mã."
        //     còn đúng 1  → MSG63 "Nhập sai thêm 1 lần … sẽ bị khóa 5 phút." (màu cảnh báo)
        if (info.getAttemptsLeft() > 0) {
            boolean isLastAttempt = info.getAttemptsLeft() == 1;
            // → forgot-password.jsp dòng 119–120 dòng gợi ý dưới ô mã
            request.setAttribute("attemptHint", isLastAttempt
                    ? String.format(Messages.MSG63_CODE_LAST_ATTEMPT, info.getLockMinutes())
                    : String.format(Messages.MSG62_CODE_ATTEMPTS_LEFT, info.getAttemptsLeft()));
            request.setAttribute("attemptHintWarn", isLastAttempt);
        }

        // B4: gợi ý dưới nút "Gửi lại mã".
        //     còn ≥ 1 lần → MSG64 "Còn n lần gửi lại mã trong 60 phút."
        //     còn 0 lần   → MSG65 "Đã hết lượt gửi lại. Gửi thêm sẽ khóa…" (màu cảnh báo)
        boolean isOutOfResends = info.getResendsLeft() == 0;
        // → forgot-password.jsp dòng 134–135 dòng gợi ý dưới nút Gửi lại mã
        request.setAttribute("resendHint", isOutOfResends
                ? String.format(Messages.MSG65_CODE_RESEND_WILL_LOCK, info.getLockMinutes())
                : String.format(Messages.MSG64_CODE_RESENDS_LEFT, info.getResendsLeft()));
        request.setAttribute("resendHintWarn", isOutOfResends);
    }


    // =================================================================================================
    // NHÓM 4. DÙNG CHUNG
    // =================================================================================================

    /**
     * HÀM PHỤ DÙNG CHUNG — gọi ở B1 của showCurrentStep. Bước hiện tại suy ra từ session (xem đầu file).
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
     * HÀM PHỤ DÙNG CHUNG — gọi ở B2 của showCurrentStep và B2 của handleReset.
     * Bước 3 quá hạn chưa? (deadline = hạn của mã đã xác minh; thiếu deadline cũng coi là quá hạn.)
     */
    private boolean isResetExpired(HttpSession session) {
        LocalDateTime deadline = (LocalDateTime) session.getAttribute(SessionKey.RECOVERY_DEADLINE);
        return deadline == null || !DateTimeUtil.now().isBefore(deadline);
    }

    /**
     * HÀM PHỤ DÙNG CHUNG — gọi ở doGet (restart, prefill), handleSend, handleReset, showCurrentStep.
     * Xóa toàn bộ tiến độ trong session → lần mở trang tới là bước 1.
     */
    private void clearRecovery(HttpSession session) {
        session.removeAttribute(SessionKey.RECOVERY_EMAIL);
        session.removeAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        session.removeAttribute(SessionKey.RECOVERY_DEADLINE);
    }

    /**
     * HÀM PHỤ DÙNG CHUNG — gọi ở B1 của setCodeTimers. Số giây từ now tới mốc; đã qua mốc thì 0 (không bao giờ âm).
     */
    private long secondsUntil(LocalDateTime now, LocalDateTime until) {
        return Math.max(0, Duration.between(now, until).getSeconds());
    }

    /**
     * HÀM PHỤ DÙNG CHUNG — gọi ở B2 của handleResend. 1 câu lỗi cho toast: ưu tiên lỗi gắn ô, không có thì lỗi chung.
     */
    private String firstError(ServiceResult<?> result) {
        if (result.hasFieldErrors()) {
            return result.getFieldErrors().values().iterator().next();
        }
        return result.getMessage();
    }
}
