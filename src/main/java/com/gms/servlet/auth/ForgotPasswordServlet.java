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
 * Trang Quên mật khẩu (URL /forgot-password) — gồm 3 bước trong 1 trang.
 *
 * <p>Bước hiện tại quyết định theo session: nhập email → nhập mã 6 số → đặt mật khẩu mới.</p>
 * <p>Servlet chỉ điều phối form (action = send/resend/verify/reset) rồi gọi PasswordRecoveryService.</p>
 */
@WebServlet("/forgot-password")
public class ForgotPasswordServlet extends HttpServlet {

    private static final String VIEW = "auth/forgot-password";
    private static final String STEP_EMAIL = "email";
    private static final String STEP_CODE = "code";
    private static final String STEP_RESET = "reset";

    private final PasswordRecoveryService passwordRecoveryService = new PasswordRecoveryService();

    /**
     * GET /forgot-password — mở trang ở bước hiện tại (theo session). ?restart=1 để bắt đầu lại từ bước 1.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();

        // TH1: khách bấm "Dùng email khác" (?restart=1) → xóa tiến độ, quay về bước nhập email
        if ("1".equals(request.getParameter("restart"))) {
            clearRecovery(session);
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // TH2: khách vừa bấm Đặt mật khẩu ở màn Đăng nhập (MSG38, LoginServlet để email trong session)
        //     → lấy email đó ra điền sẵn vào ô, rồi XÓA khỏi session ngay (chỉ dùng 1 lần: bấm F5 là ô trống lại).
        //     Đồng thời bỏ tiến độ cũ để chắc chắn đang ở bước "email".
        //     KHÔNG tự gửi mã: khách phải tự bấm "Gửi mã" → luật chống gửi liên tục (60 giây, 5 lần/giờ) vẫn áp dụng.
        String prefillEmail = (String) session.getAttribute(SessionKey.RECOVERY_PREFILL_EMAIL);
        if (prefillEmail != null) {
            session.removeAttribute(SessionKey.RECOVERY_PREFILL_EMAIL);
            clearRecovery(session);
            request.setAttribute("email", prefillEmail);
        }

        // B3: hiện đúng bước theo tiến độ đang lưu trong session
        showCurrentStep(request, response, session);
    }

    /**
     * POST /forgot-password — nhận form của 1 trong 3 bước, điều phối theo ô hidden "action".
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        String action = RequestUtil.getTrimmedParam(request, "action");

        // Rẽ theo nút khách bấm; action lạ (bị sửa tay) → quay về trang như GET
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

    /**
     * Khách bấm "Gửi mã xác thực" ở bước 1: nhờ Service kiểm tra email + gửi mã, lưu email vào session rồi chuyển sang bước 2.
     */
    private void handleSend(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: đọc ô email, nhờ Service kiểm tra + gửi mã
        String email = RequestUtil.getTrimmedParam(request, "email");
        ServiceResult<Void> result = passwordRecoveryService.requestCode(email);

        // B2: không gửi được → hiện lại bước 1, giữ nguyên chữ khách đã gõ, kèm lỗi
        if (!result.isSuccess()) {
            request.setAttribute("email", email);
            forwardStep(request, response, STEP_EMAIL, result);
            return;
        }

        // B3: gửi được → xóa tiến độ cũ (nếu có), lưu email này vào session = đang ở bước 2
        clearRecovery(session);
        session.setAttribute(SessionKey.RECOVERY_EMAIL, email);

        // B4: toast "Mã đã được gửi tới …" rồi redirect → GET sẽ hiện bước 2
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    // ===== Bước 2: gửi lại mã / nhập mã =====

    /**
     * Khách bấm "Gửi lại mã" ở bước 2: lấy email từ session, nhờ Service gửi lại rồi báo kết quả bằng toast.
     */
    private void handleResend(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws IOException {
        // B1: lấy email đang khôi phục từ session. Không có (session hết hạn) → chỉ quay về trang
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);
        if (email != null) {
            // B2: gửi lại mã; thành công → toast xanh MSG45, thất bại (chưa đủ 60 giây, bị khóa...) → toast đỏ
            ServiceResult<Void> result = passwordRecoveryService.requestCode(email);
            String toastType = result.isSuccess() ? "success" : "error";
            String toastMessage = result.isSuccess() ? Messages.MSG45_CODE_RESENT : firstError(result);
            RequestUtil.setFlashToast(request, toastType, toastMessage);
        }
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    /**
     * Khách gõ mã 6 số ở bước 2: Service xác minh, đúng thì ghi accountId + deadline vào session rồi chuyển sang bước 3.
     */
    private void handleVerify(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: chưa qua bước 1 (hoặc session hết hạn) → quay về đầu
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);
        if (email == null) {
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B2: kiểm tra mã với email TRONG SESSION (không tin email từ form)
        String code = RequestUtil.getTrimmedParam(request, "code");
        ServiceResult<VerificationCode> result = passwordRecoveryService.verifyCode(email, code);

        // B3: sai / hết hạn / bị khóa → hiện lại bước 2 kèm lỗi
        if (!result.isSuccess()) {
            forwardStep(request, response, STEP_CODE, result);
            return;
        }

        // B4: đúng mã → ghi vào session "đã xác minh tài khoản X", hạn làm bước 3 = hạn của mã vừa dùng.
        //     VD mã gửi 10:00 hết hạn 10:15 → phải đặt mật khẩu mới trước 10:15
        VerificationCode usedCode = result.getData();
        session.setAttribute(SessionKey.RECOVERY_ACCOUNT_ID, usedCode.getAccountId());
        session.setAttribute(SessionKey.RECOVERY_DEADLINE, usedCode.getExpiresAt());
        RequestUtil.redirect(request, response, "/forgot-password");
    }

    // ===== Bước 3: đặt mật khẩu mới =====

    /**
     * Khách bấm "Đặt mật khẩu mới" ở bước 3: kiểm tra 2 ô mật khẩu rồi nhờ Service lưu, thành công thì đăng xuất mọi phiên của tài khoản rồi về trang đăng nhập.
     */
    private void handleReset(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: chưa xác minh mã (VD gửi form bằng tay khi mới ở bước 1) → quay về đầu
        Integer accountId = (Integer) session.getAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B2: quá hạn bước 3 (để trang mở quá lâu) → xóa tiến độ, toast MSG44, làm lại từ đầu
        if (isResetExpired(session)) {
            clearRecovery(session);
            RequestUtil.setFlashToast(request, "error", Messages.MSG44_CODE_EXPIRED);
            RequestUtil.redirect(request, response, "/forgot-password");
            return;
        }

        // B3: nhờ Service kiểm tra 2 ô mật khẩu rồi lưu
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        ServiceResult<Void> result = passwordRecoveryService.resetPassword(accountId, password, confirmPassword);

        // B4: lỗi ô mật khẩu → giữ bước 3 để gõ lại.
        //     Lỗi khác (tài khoản vừa bị khóa, lỗi hệ thống) → bỏ tiến độ, về bước 1 kèm câu lỗi
        if (!result.isSuccess()) {
            if (result.hasFieldErrors()) {
                forwardStep(request, response, STEP_RESET, result);
            } else {
                clearRecovery(session);
                forwardStep(request, response, STEP_EMAIL, result);
            }
            return;
        }

        // B5: thành công. Service đã đăng xuất MỌI phiên của tài khoản — nếu khách đang đăng nhập đúng tài khoản
        //     này trên trình duyệt hiện tại thì session hiện tại cũng đã bị hủy → xóa tiến độ sẽ ném lỗi, bỏ qua
        try {
            clearRecovery(session);
        } catch (IllegalStateException sessionAlreadyInvalidated) {
            // Session đã bị hủy, không còn gì để xóa
        }

        // B6: toast MSG49 rồi về trang đăng nhập (setFlashToast tự tạo session mới nếu session cũ đã hủy)
        RequestUtil.setFlashToast(request, "success", result.getMessage());
        RequestUtil.redirect(request, response, "/login");
    }

    // ===== Hiển thị =====

    /**
     * Đọc bước hiện tại từ session rồi forward vào JSP để hiện đúng màn tương ứng (nếu bước 3 đã quá hạn thì đẩy về bước 1).
     */
    private void showCurrentStep(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws ServletException, IOException {
        // B1: đọc bước từ session
        String step = resolveStep(session);

        // B2: đang ở bước 3 mà đã quá hạn → bỏ tiến độ, hiện bước 1 kèm MSG44
        if (STEP_RESET.equals(step) && isResetExpired(session)) {
            clearRecovery(session);
            request.setAttribute("errorMessage", Messages.MSG44_CODE_EXPIRED);
            step = STEP_EMAIL;
        }

        // B3: hiện trang ở bước đó
        forwardStep(request, response, step, null);
    }

    /**
     * Gom dữ liệu cho JSP (lỗi, đồng hồ, gợi ý số lượt) rồi forward sang view ở bước chỉ định.
     */
    private void forwardStep(HttpServletRequest request, HttpServletResponse response,
            String step, ServiceResult<?> failedResult) throws ServletException, IOException {
        HttpSession session = request.getSession();
        String email = (String) session.getAttribute(SessionKey.RECOVERY_EMAIL);

        // B1: có lỗi → lỗi gắn ô thì hiện dưới ô (fieldErrors), lỗi chung thì hiện khung đỏ trên cùng (errorMessage)
        if (failedResult != null) {
            request.setAttribute("fieldErrors", failedResult.getFieldErrors());
            if (!failedResult.hasFieldErrors()) {
                request.setAttribute("errorMessage", failedResult.getMessage());
            }
        }

        // B2: riêng bước 2 cần thêm đồng hồ + gợi ý số lượt còn lại
        if (STEP_CODE.equals(step)) {
            setCodeTimers(request, email);
        }

        // B3: cho JSP biết đang ở bước nào, email nào, rồi hiện trang
        request.setAttribute("recoveryStep", step);
        request.setAttribute("recoveryEmail", email);
        RequestUtil.forward(request, response, VIEW);
    }

    /**
     * Chuẩn bị dữ liệu riêng cho bước 2: 2 đồng hồ đếm ngược (GBR-27) và 2 dòng gợi ý số lượt còn lại (MSG62–MSG65).
     * Số giây do server tính rồi đưa xuống, JS chỉ đếm lùi (không tin đồng hồ máy khách).
     */
    private void setCodeTimers(HttpServletRequest request, String email) {
        // B1: tính 2 đồng hồ từ mã gửi gần nhất.
        //     codeSecondsLeft   = còn bao nhiêu giây mã hết hạn (VD 899 = 14:59)
        //     resendSecondsLeft = còn bao nhiêu giây mới được bấm "Gửi lại mã" (VD 59)
        //     Lỗi DB → để 0 (đồng hồ hiện hết hạn), không làm hỏng trang
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

        // B2: hỏi Service còn mấy lượt. Lỗi hoặc email đang bị khóa → không hiện gợi ý (lúc khóa đã có MSG61)
        ServiceResult<VerificationAttemptInfo> attemptResult = passwordRecoveryService.getRecoveryAttemptInfo(email);
        if (!attemptResult.isSuccess() || attemptResult.getData().isLocked()) {
            return;
        }
        VerificationAttemptInfo info = attemptResult.getData();

        // B3: gợi ý dưới ô nhập mã — CHỈ khi mã còn dùng được (còn > 0 lần).
        //     Mã đã hết hạn thì còn 0 lần: không hiện gì (đồng hồ đã báo "Mã đã hết hạn"), tránh báo nhầm "sắp bị khóa".
        //     Còn ≥ 2 lần → MSG62 "Bạn còn n lần nhập mã."
        //     Còn đúng 1 lần → MSG63 "Nhập sai thêm 1 lần … sẽ bị khóa 5 phút." (màu cảnh báo)
        if (info.getAttemptsLeft() > 0) {
            boolean isLastAttempt = info.getAttemptsLeft() == 1;
            request.setAttribute("attemptHint", isLastAttempt
                    ? String.format(Messages.MSG63_CODE_LAST_ATTEMPT, info.getLockMinutes())
                    : String.format(Messages.MSG62_CODE_ATTEMPTS_LEFT, info.getAttemptsLeft()));
            request.setAttribute("attemptHintWarn", isLastAttempt);
        }

        // B4: gợi ý dưới nút "Gửi lại mã".
        //     Còn ≥ 1 lần → MSG64 "Còn n lần gửi lại mã trong 60 phút."
        //     Còn 0 lần   → MSG65 "Đã hết lượt gửi lại. Gửi thêm sẽ khóa…" (màu cảnh báo)
        boolean isOutOfResends = info.getResendsLeft() == 0;
        request.setAttribute("resendHint", isOutOfResends
                ? String.format(Messages.MSG65_CODE_RESEND_WILL_LOCK, info.getLockMinutes())
                : String.format(Messages.MSG64_CODE_RESENDS_LEFT, info.getResendsLeft()));
        request.setAttribute("resendHintWarn", isOutOfResends);
    }

    // ===== Hàm phụ =====

    /**
     * Suy ra bước hiện tại từ session: đã xác minh mã → "reset"; đã gửi mã → "code"; còn lại → "email".
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
     * Kiểm tra bước 3 đã quá hạn chưa (so với deadline = hạn của mã đã xác minh).
     */
    private boolean isResetExpired(HttpSession session) {
        LocalDateTime deadline = (LocalDateTime) session.getAttribute(SessionKey.RECOVERY_DEADLINE);
        return deadline == null || !DateTimeUtil.now().isBefore(deadline);
    }

    /**
     * Xóa toàn bộ tiến độ Quên mật khẩu trong session (email, tài khoản đã xác minh, deadline) → đưa khách về bước 1.
     */
    private void clearRecovery(HttpSession session) {
        session.removeAttribute(SessionKey.RECOVERY_EMAIL);
        session.removeAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        session.removeAttribute(SessionKey.RECOVERY_DEADLINE);
    }

    /**
     * Số giây từ "bây giờ" tới 1 mốc; đã qua mốc thì trả 0 (không bao giờ âm).
     */
    private long secondsUntil(LocalDateTime now, LocalDateTime until) {
        return Math.max(0, Duration.between(now, until).getSeconds());
    }

    /**
     * Lấy 1 câu lỗi để hiện trong toast: ưu tiên lỗi gắn ô đầu tiên, không có thì lấy lỗi chung.
     */
    private String firstError(ServiceResult<?> result) {
        if (result.hasFieldErrors()) {
            return result.getFieldErrors().values().iterator().next();
        }
        return result.getMessage();
    }
}
