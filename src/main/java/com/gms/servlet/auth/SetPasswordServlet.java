package com.gms.servlet.auth;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.dto.SetupLinkCheck;
import com.gms.service.PasswordRecoveryService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Trang đặt mật khẩu LẦN ĐẦU qua link email — địa chỉ /set-password?token=... (CBR-24, CBR-31).
 *
 * Dành cho tài khoản do Lễ tân tạo tại quầy: tài khoản chưa có mật khẩu, khách nhận email có link,
 * bấm link → mở trang này → đặt mật khẩu → đăng nhập.
 *
 * Link có 4 trạng thái (Service xác định, Servlet chỉ rẽ nhánh theo đó):
 *   VALID     link còn dùng được       → hiện form đặt mật khẩu
 *   EXPIRED   quá 15 phút chưa dùng    → chuyển sang /forgot-password, ô email điền sẵn, KHÔNG tự gửi mã
 *   USED      đã đặt mật khẩu rồi      → về /login, báo "Bạn đã đặt mật khẩu…"
 *   NOT_FOUND link bịa / thiếu token   → ở lại trang, báo chung "Link không còn hiệu lực" (không tiết lộ gì thêm)
 * Cả khi MỞ link (GET) lẫn khi BẤM LƯU (POST) đều kiểm tra như trên.
 *
 * @author Le Cong Minh
 */
@WebServlet("/set-password")
public class SetPasswordServlet extends HttpServlet {

    private static final String VIEW = "auth/set-password";

    private final PasswordRecoveryService passwordRecoveryService = new PasswordRecoveryService();

    /**
     * Khách bấm link trong email.
     *
     * @param request  URL có "?token=...", VD /set-password?token=setup-nhung-phase1-2026.
     * @param response hiện form, hoặc chuyển trang / báo lỗi tùy trạng thái link.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: đọc token trên URL; link không dùng được thì handleUnusableLink đã tự xử lý xong → dừng
        String token = RequestUtil.getTrimmedParam(request, "token");
        if (handleUnusableLink(request, response, token)) {
            return;
        }

        // B2: link còn dùng được → hiện form, gắn token vào ô ẩn để lúc bấm Lưu gửi lại
        request.setAttribute("token", token);
        RequestUtil.forward(request, response, VIEW);
    }

    /**
     * Khách gõ mật khẩu 2 lần rồi bấm "Đặt mật khẩu".
     *
     * @param request  form có ô ẩn "token", ô "password", ô "confirmPassword".
     * @param response thành công → redirect /login; lỗi ô → giữ form; link hỏng → chuyển trang như lúc mở link.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: kiểm tra link LẠI LẦN NỮA — khách có thể để form mở quá 15 phút, hoặc đã đặt mật khẩu ở tab khác
        String token = RequestUtil.getTrimmedParam(request, "token");
        if (handleUnusableLink(request, response, token)) {
            return;
        }

        // B2: nhờ Service kiểm tra 2 ô mật khẩu, lưu mật khẩu, đánh dấu link đã dùng
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        ServiceResult<Void> result = passwordRecoveryService.setPasswordByLink(token, password, confirmPassword);

        // B3: thành công → toast "Đã đặt mật khẩu. Vui lòng đăng nhập." rồi về trang đăng nhập
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // B4: thất bại.
        //     Lỗi ô mật khẩu (sai luật, không khớp) → hiện lại form với token cũ, chữ đỏ dưới ô.
        //     Lỗi khác (tài khoản bị khóa MSG37, lỗi hệ thống) → hiện khung báo lỗi thay cho form
        if (result.hasFieldErrors()) {
            request.setAttribute("token", token);
            request.setAttribute("fieldErrors", result.getFieldErrors());
        } else {
            request.setAttribute("linkError", result.getMessage());
        }
        RequestUtil.forward(request, response, VIEW);
    }

    /**
     * Kiểm tra link; nếu link KHÔNG dùng được thì xử lý luôn (chuyển trang hoặc hiện lỗi).
     *
     * @param request  nơi đặt linkError / nơi lấy session.
     * @param response dùng để redirect hoặc hiện trang lỗi.
     * @param token    chuỗi sau "?token=" (hoặc ô ẩn token của form), có thể rỗng.
     * @return true  = link không dùng được và hàm ĐÃ trả trang cho khách → người gọi phải dừng lại (return);
     *         false = link còn dùng được → người gọi làm tiếp (hiện form / lưu mật khẩu).
     */
    private boolean handleUnusableLink(HttpServletRequest request, HttpServletResponse response, String token)
            throws ServletException, IOException {
        // B1: hỏi Service trạng thái link. Lỗi hệ thống → hiện khung lỗi "Hệ thống đang bận"
        ServiceResult<SetupLinkCheck> result = passwordRecoveryService.checkSetupLink(token);
        if (!result.isSuccess()) {
            request.setAttribute("linkError", result.getMessage());
            RequestUtil.forward(request, response, VIEW);
            return true;
        }

        // B2: rẽ nhánh theo trạng thái
        SetupLinkCheck check = result.getData();
        switch (check.getStatus()) {
            case VALID:
                // Link dùng được → để người gọi làm tiếp
                return false;
            case EXPIRED:
                // Hết hạn → đặt email của link vào session (KHÔNG đưa lên URL để khỏi nằm trong lịch sử trình duyệt),
                // trang Quên mật khẩu sẽ lấy ra điền sẵn 1 lần. Kèm toast giải thích lý do chuyển trang
                request.getSession().setAttribute(SessionKey.RECOVERY_PREFILL_EMAIL, check.getLink().getEmail());
                RequestUtil.setFlashToast(request, "warning", Messages.MSG76_SETUP_LINK_EXPIRED);
                RequestUtil.redirect(request, response, "/forgot-password");
                return true;
            case USED:
                // Đã đặt mật khẩu bằng link này rồi → mời đăng nhập
                RequestUtil.setFlashToast(request, "info", Messages.MSG77_SETUP_LINK_USED);
                RequestUtil.redirect(request, response, "/login");
                return true;
            default:
                // NOT_FOUND (link bịa, thiếu token) → báo chung, không cho biết thêm gì
                request.setAttribute("linkError", Messages.MSG71_SETUP_LINK_INVALID);
                RequestUtil.forward(request, response, VIEW);
                return true;
        }
    }
}
