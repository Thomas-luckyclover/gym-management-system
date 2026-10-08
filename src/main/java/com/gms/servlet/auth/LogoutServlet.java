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
 * Servlet đăng xuất hệ thống (URL /logout).
 *
 * <p>Hủy session hiện tại rồi chuyển về /login, kèm thông báo toast "Đã đăng xuất".</p>
 */
@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    /**
     * GET /logout — đăng xuất (vì link/button đăng xuất thường là GET).
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processLogout(request, response);
    }

    /**
     * POST /logout — đăng xuất từ form/nút bấm.
     */
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
