package com.gms.servlet.auth;

import com.gms.constant.Messages;
import com.gms.dto.JsonResponse;
import com.gms.util.JsonUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Servlet đăng xuất hệ thống (/logout).
 *
 * <p>Quy trình:</p>
 * <ul>
 *   <li>Hủy phiên đăng nhập HttpSession hiện tại (invalidate).</li>
 *   <li>Gắn thông báo toast MSG40_SIGNED_OUT ("Đã đăng xuất.") sang lần redirect tiếp theo.</li>
 *   <li>Chuyển hướng về trang đăng nhập (/login).</li>
 * </ul>
 */
@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processLogout(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processLogout(request, response);
    }

    private void processLogout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        if (RequestUtil.isAjaxRequest(request)) {
            JsonUtil.writeJson(response, JsonResponse.success(Messages.MSG40_SIGNED_OUT));
            return;
        }

        RequestUtil.setFlashToast(request, "info", Messages.MSG40_SIGNED_OUT);
        RequestUtil.redirect(request, response, "/login");
    }
}
