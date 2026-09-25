package com.gms.servlet.auth;

import com.gms.constant.Messages;
import com.gms.dto.ServiceResult;
import com.gms.service.RegistrationService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import com.gms.util.ValidationUtil;
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

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String step = req.getParameter("step");
        String phone = req.getParameter("phone");

        if ("2".equals(step) && ValidationUtil.isValidPhone(phone)) {
            req.setAttribute("step", "2");
            req.setAttribute("phone", phone);
        } else {
            req.setAttribute("step", "1");
        }

        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp")
                .forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String step = req.getParameter("step");

        if ("1".equals(step)) {
            handlePhoneStep(req, resp);
            return;
        }

        handleInformationStep(req, resp);
    }

    private void handlePhoneStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");

        if (!ValidationUtil.isValidPhone(phone)) {
            req.setAttribute("step", "1");
            req.setAttribute("phone", phone);
            req.setAttribute("errorMessage", Messages.MSG13_INVALID_PHONE);

            req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp")
                    .forward(req, resp);
            return;
        }

        req.setAttribute("step", "2");
        req.setAttribute("phone", phone.trim());

        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp")
                .forward(req, resp);
    }

    private void handleInformationStep(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String phone = req.getParameter("phone");
        String fullName = req.getParameter("fullName");
        String dobString = req.getParameter("dateOfBirth");
        String email = req.getParameter("email");
        String password = req.getParameter("password");
        String confirmPassword = req.getParameter("confirmPassword");

        LocalDate dateOfBirth = DateTimeUtil.parseHtmlDate(dobString);

        ServiceResult<Void> result = registrationService.register(
                fullName,
                dateOfBirth,
                phone,
                email,
                password,
                confirmPassword
        );

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(req, "success", result.getMessage());
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        req.setAttribute("step", "2");
        req.setAttribute("errorMessage", result.getMessage());
        req.setAttribute("phone", phone);
        req.setAttribute("fullName", fullName);
        req.setAttribute("dateOfBirth", dobString);
        req.setAttribute("email", email);

        req.getRequestDispatcher("/WEB-INF/jsp/auth/register.jsp")
                .forward(req, resp);
    }
}