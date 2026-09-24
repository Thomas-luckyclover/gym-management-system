package com.gms.servlet.auth;

import com.gms.dto.ServiceResult;
import com.gms.service.RegistrationService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;

/**
 * Controller xử lý luồng đăng ký tài khoản khách hàng (/register).
 */
@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private final RegistrationService registrationService;

    public RegisterServlet() {
        this.registrationService = new RegistrationService();
    }

    public RegisterServlet(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    // ===== Xử lý GET (Hiển thị trang đăng ký) =====

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Chuyển hướng tới giao diện JSP đăng ký
        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
    }

    // ===== Xử lý POST (Tiếp nhận form đăng ký) =====

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // B1: Đọc dữ liệu từ request
        String fullName = req.getParameter("fullName");
        String dobString = req.getParameter("dateOfBirth");
        String phone = req.getParameter("phone");
        String email = req.getParameter("email");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");

        LocalDate dateOfBirth = DateTimeUtil.parseHtmlDate(dobString);

        // B2: Gọi RegistrationService xử lý nghiệp vụ
        ServiceResult<Void> result = registrationService.register(fullName, dateOfBirth, phone, email, password, confirmPassword);

        // B3: Xử lý kết quả trả về
        boolean isSuccess = result.isSuccess();
        if (isSuccess) {
            // Đăng ký thành công -> set flash message và chuyển hướng về trang đăng nhập (PRG pattern)
            RequestUtil.setFlashToast(req, "success", result.getMessage());
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        // B4: Thất bại -> giữ lại dữ liệu đã nhập và hiển thị lỗi trên form
        req.setAttribute("errorMessage", result.getMessage());
        req.setAttribute("fullName", fullName);
        req.setAttribute("dateOfBirth", dobString);
        req.setAttribute("phone", phone);
        req.setAttribute("email", email);

        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp").forward(req, resp);
    }
}