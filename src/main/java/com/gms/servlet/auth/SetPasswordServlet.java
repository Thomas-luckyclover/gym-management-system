package com.gms.servlet.auth;

import com.gms.dto.ServiceResult;
import com.gms.entity.VerificationCode;
import com.gms.service.PasswordRecoveryService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Đặt mật khẩu lần đầu qua link email (/set-password?token=..., CBR-24, CBR-31, MSG38).
 *
 * GET: kiểm tra link → còn hiệu lực thì hiện form, không thì hiện thông báo + nút Quên mật khẩu.
 * POST: kiểm tra lại link ở server, lưu mật khẩu, đánh dấu link đã dùng → về /login.
 *
 * @author Le Cong Minh
 */
@WebServlet("/set-password")
public class SetPasswordServlet extends HttpServlet {

    private static final String VIEW = "auth/set-password";

    private final PasswordRecoveryService passwordRecoveryService = new PasswordRecoveryService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = RequestUtil.getTrimmedParam(request, "token");
        ServiceResult<VerificationCode> linkResult = passwordRecoveryService.checkSetupLink(token);

        if (linkResult.isSuccess()) {
            request.setAttribute("token", token);
        } else {
            request.setAttribute("linkError", linkResult.getMessage());
        }
        RequestUtil.forward(request, response, VIEW);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = RequestUtil.getTrimmedParam(request, "token");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");

        ServiceResult<Void> result = passwordRecoveryService.setPasswordByLink(token, password, confirmPassword);

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // Lỗi ô mật khẩu → giữ form; lỗi link/tài khoản → hiện trạng thái link hỏng
        if (result.hasFieldErrors()) {
            request.setAttribute("token", token);
            request.setAttribute("fieldErrors", result.getFieldErrors());
        } else {
            request.setAttribute("linkError", result.getMessage());
        }
        RequestUtil.forward(request, response, VIEW);
    }
}
