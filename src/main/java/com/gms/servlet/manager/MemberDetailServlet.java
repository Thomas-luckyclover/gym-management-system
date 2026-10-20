package com.gms.servlet.manager;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dao.CustomerDAO;
import com.gms.dao.CustomerDeletionRequestDAO;
import com.gms.dao.PersonDAO;
import com.gms.entity.Customer;
import com.gms.entity.CustomerDeletionRequest;
import com.gms.entity.Person;
import com.gms.util.DBContext;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Controller xem chi tiết hồ sơ hội viên cho Quản lý (Màn #31, UC-41, BF-07).
 * Hiển thị đầy đủ thông tin định danh, trạng thái Inactive (nếu đã bị xóa), gói tập, và nút duyệt xóa nếu có yêu cầu Pending.
 */
@WebServlet("/manager/members/detail")
public class MemberDetailServlet extends HttpServlet {

    private static final String VIEW_NAME = "manager/member-detail";

    private final CustomerDAO customerDAO;
    private final PersonDAO personDAO;
    private final CustomerDeletionRequestDAO customerDeletionRequestDAO;

    public MemberDetailServlet() {
        this.customerDAO = new CustomerDAO();
        this.personDAO = new PersonDAO();
        this.customerDeletionRequestDAO = new CustomerDeletionRequestDAO();
    }

    MemberDetailServlet(CustomerDAO customerDAO, PersonDAO personDAO, CustomerDeletionRequestDAO customerDeletionRequestDAO) {
        this.customerDAO = customerDAO;
        this.personDAO = personDAO;
        this.customerDeletionRequestDAO = customerDeletionRequestDAO;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        int customerId = RequestUtil.getIntParam(request, "id", 0);
        if (customerId <= 0) {
            RequestUtil.redirect(request, response, "/manager/members");
            return;
        }

        try (Connection conn = DBContext.getConnection()) {
            Customer customer = customerDAO.findById(customerId);
            if (customer == null) {
                RequestUtil.setFlashToast(request, "error", String.format(Messages.MSG08_NOT_FOUND, "hội viên"));
                RequestUtil.redirect(request, response, "/manager/members");
                return;
            }

            Person person = personDAO.findById(customer.getPersonId());
            CustomerDeletionRequest pendingRequest = customerDeletionRequestDAO.findPendingByCustomerId(conn, customerId);

            List<CustomerDAO.ActiveSubscriptionSummary> activePackages =
                    customerDAO.findActiveSubscriptionsByCustomer(conn, customerId);
            List<CustomerDAO.ScheduledSessionSummary> scheduledSessions =
                    customerDAO.findScheduledSessionsByCustomer(conn, customerId);

            request.setAttribute("customer", customer);
            request.setAttribute("person", person);
            request.setAttribute("pendingRequest", pendingRequest);
            request.setAttribute("activePackages", activePackages != null ? activePackages : Collections.emptyList());
            request.setAttribute("scheduledSessions", scheduledSessions != null ? scheduledSessions : Collections.emptyList());
            request.setAttribute("activeNav", "members");

            RequestUtil.forward(request, response, VIEW_NAME);

        } catch (SQLException e) {
            e.printStackTrace();
            RequestUtil.setFlashToast(request, "error", Messages.MSG04_SYSTEM_BUSY);
            RequestUtil.redirect(request, response, "/manager/members");
        }
    }
}
