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
 * Trang Quên mật khẩu (màn #3, UC-03) — địa chỉ /forgot-password.
 *
 * 1 trang nhưng có 3 bước. Bước nào đang hiện là do SESSION quyết định (không phải do URL),
 * nên khách không thể gõ thẳng URL để nhảy sang bước sau:
 *   Session chưa có recoveryEmail                → bước "email"  (nhập email)
 *   Có recoveryEmail, chưa có recoveryAccountId  → bước "code"   (nhập mã 6 số)
 *   Có recoveryAccountId (còn hạn)               → bước "reset"  (đặt mật khẩu mới)
 *
 * Form gửi lên (POST) luôn kèm ô ẩn "action" để biết khách bấm nút nào:
 *   send = "Gửi mã xác thực" · resend = "Gửi lại mã" · verify = "Xác nhận" (mã) · reset = "Đặt mật khẩu mới"
 * Làm xong 1 bước thì redirect về GET /forgot-password (mẫu Post/Redirect/Get: bấm F5 không gửi lại form).
 *
 * Servlet chỉ điều phối: đọc form → gọi PasswordRecoveryService → chọn trang. Không tự quyết luật nghiệp vụ.
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

    /**
     * Mở trang Quên mật khẩu (gõ URL, bấm link "Quên mật khẩu?", hoặc sau mỗi lần redirect).
     *
     * @param request  có thể có "?restart=1" (khách bấm "Dùng email khác").
     * @param response dùng để redirect hoặc trả trang JSP.
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
     * Nhận form của cả 3 bước. Ô ẩn "action" cho biết khách bấm nút nào.
     *
     * @param request  form gửi lên, luôn có ô "action" (send / resend / verify / reset) + các ô của bước đó.
     * @param response dùng để redirect hoặc trả lại trang kèm lỗi.
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
     * Khách bấm "Gửi mã xác thực" ở bước 1.
     *
     * @param request  form có ô "email", VD "thao.luong@gmail.com".
     * @param response redirect sang bước 2 nếu gửi được; không thì trả lại bước 1 kèm lỗi.
     * @param session  nơi lưu tiến độ — gửi được thì ghi recoveryEmail.
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
     * Khách bấm "Gửi lại mã" ở bước 2. Gửi được hay không đều báo bằng toast rồi quay lại bước 2.
     *
     * @param request  form chỉ có action=resend (email lấy từ session, không lấy từ form).
     * @param response luôn redirect về /forgot-password.
     * @param session  đọc recoveryEmail.
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
     * Khách gõ mã 6 số rồi bấm "Xác nhận" ở bước 2.
     *
     * @param request  form có ô "code", VD "246810".
     * @param response redirect sang bước 3 nếu đúng; không thì trả lại bước 2 kèm lỗi.
     * @param session  đọc recoveryEmail; mã đúng thì ghi recoveryAccountId + recoveryDeadline.
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
     * Khách bấm "Đặt mật khẩu mới" ở bước 3.
     *
     * @param request  form có ô "password" và "confirmPassword".
     * @param response thành công → redirect /login; lỗi ô → trả lại bước 3; lỗi khác → về bước 1.
     * @param session  đọc recoveryAccountId + recoveryDeadline; thành công thì xóa tiến độ.
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
     * Xác định bước đang ở (theo session) rồi hiện trang.
     *
     * @param request  nơi đặt dữ liệu cho JSP.
     * @param response dùng để trả trang JSP.
     * @param session  nơi đọc tiến độ.
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
     * Đặt đủ dữ liệu cho JSP rồi hiện trang ở 1 bước.
     *
     * @param request      nơi đặt dữ liệu: recoveryStep, recoveryEmail, lỗi, đồng hồ, gợi ý số lượt.
     * @param response     dùng để trả trang JSP.
     * @param step         bước cần hiện: "email" / "code" / "reset".
     * @param failedResult kết quả thất bại cần hiện lỗi; truyền null khi chỉ hiện trang, không có lỗi.
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
     * Số giây do SERVER tính rồi đưa xuống, JS chỉ đếm lùi (không tin đồng hồ máy khách).
     *
     * @param request nơi đặt: codeSecondsLeft, resendSecondsLeft, attemptHint(+Warn), resendHint(+Warn).
     * @param email   email đang khôi phục, VD "thao.luong@gmail.com".
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
     * Đọc session để biết khách đang ở bước nào.
     *
     * @param session session của khách.
     * @return "reset" nếu đã xác minh mã; "code" nếu đã gửi mã; còn lại "email".
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
     * Kiểm tra bước 3 đã quá hạn chưa (hạn = hạn của mã đã xác minh).
     *
     * @param session session của khách.
     * @return true nếu không có hạn hoặc bây giờ đã qua hạn; false nếu vẫn còn thời gian.
     */
    private boolean isResetExpired(HttpSession session) {
        LocalDateTime deadline = (LocalDateTime) session.getAttribute(SessionKey.RECOVERY_DEADLINE);
        return deadline == null || !DateTimeUtil.now().isBefore(deadline);
    }

    /**
     * Xóa toàn bộ tiến độ Quên mật khẩu trong session (email, tài khoản đã xác minh, hạn bước 3) → về bước 1.
     *
     * @param session session của khách.
     */
    private void clearRecovery(HttpSession session) {
        session.removeAttribute(SessionKey.RECOVERY_EMAIL);
        session.removeAttribute(SessionKey.RECOVERY_ACCOUNT_ID);
        session.removeAttribute(SessionKey.RECOVERY_DEADLINE);
    }

    /**
     * Số giây từ "bây giờ" tới 1 mốc; đã qua mốc thì trả 0 (không bao giờ âm).
     *
     * @param now   giờ hiện tại.
     * @param until mốc cần tính, VD giờ hết hạn của mã.
     * @return số giây còn lại, VD 899.
     */
    private long secondsUntil(LocalDateTime now, LocalDateTime until) {
        return Math.max(0, Duration.between(now, until).getSeconds());
    }

    /**
     * Lấy 1 câu lỗi để hiện trong toast: ưu tiên lỗi gắn ô đầu tiên, không có thì lấy lỗi chung.
     *
     * @param result kết quả thất bại.
     * @return câu lỗi, VD "Vui lòng chờ 16 giây nữa rồi bấm Gửi lại mã."
     */
    private String firstError(ServiceResult<?> result) {
        if (result.hasFieldErrors()) {
            return result.getFieldErrors().values().iterator().next();
        }
        return result.getMessage();
    }
}
