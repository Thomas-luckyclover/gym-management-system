package com.gms.servlet.receptionist;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dao.CustomerDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.Customer;
import com.gms.entity.Person;
import com.gms.service.CustomerDeletionService;
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
 * Controller xem thông tin hồ sơ và thực hiện xóa hồ sơ khách hàng tại quầy Lễ tân (Màn #29, UC-16, BF-07).
 */
@WebServlet("/receptionist/customers/form")
public class CustomerProfileFormServlet extends HttpServlet {

    private static final String VIEW_NAME = "receptionist/customer-form";
    private static final String ACTION_DELETE = "delete";

    private final CustomerDAO customerDAO;
    private final PersonDAO personDAO;
    private final CustomerDeletionService customerDeletionService;

    public CustomerProfileFormServlet() {
        this.customerDAO = new CustomerDAO();
        this.personDAO = new PersonDAO();
        this.customerDeletionService = new CustomerDeletionService();
    }

    CustomerProfileFormServlet(CustomerDAO customerDAO, PersonDAO personDAO, CustomerDeletionService customerDeletionService) {
        this.customerDAO = customerDAO;
        this.personDAO = personDAO;
        this.customerDeletionService = customerDeletionService;
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
            RequestUtil.redirect(request, response, "/receptionist/customers");
            return;
        }

        try {
            Customer customer = customerDAO.findById(customerId);
            if (customer == null) {
                RequestUtil.setFlashToast(request, "error", String.format(Messages.MSG08_NOT_FOUND, "hồ sơ khách hàng"));
                RequestUtil.redirect(request, response, "/receptionist/customers");
                return;
            }

            // CBR-04: Lễ tân không được xem hồ sơ Inactive
            if ("Inactive".equalsIgnoreCase(customer.getStatus())) {
                RequestUtil.setFlashToast(request, "error", "Hồ sơ này đã ngừng hoạt động (Inactive).");
                RequestUtil.redirect(request, response, "/receptionist/customers");
                return;
            }

            Person person = personDAO.findById(customer.getPersonId());

            // Lấy danh sách gói tập và buổi PT đang hoạt động để cảnh báo trước khi xóa
            List<CustomerDAO.ActiveSubscriptionSummary> activePackages = Collections.emptyList();
            List<CustomerDAO.ScheduledSessionSummary> scheduledSessions = Collections.emptyList();
            try (Connection conn = DBContext.getConnection()) {
                activePackages = customerDAO.findActiveSubscriptionsByCustomer(conn, customerId);
                scheduledSessions = customerDAO.findScheduledSessionsByCustomer(conn, customerId);
            }

            request.setAttribute("customer", customer);
            request.setAttribute("person", person);
            request.setAttribute("activePackages", activePackages);
            request.setAttribute("scheduledSessions", scheduledSessions);
            request.setAttribute("hasActiveAssets", !activePackages.isEmpty() || !scheduledSessions.isEmpty());
            request.setAttribute("activeNav", "customers");

            RequestUtil.forward(request, response, VIEW_NAME);

        } catch (SQLException e) {
            e.printStackTrace();
            RequestUtil.setFlashToast(request, "error", Messages.MSG04_SYSTEM_BUSY);
            RequestUtil.redirect(request, response, "/receptionist/customers");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        String action = RequestUtil.getTrimmedParam(request, "action");

        if (ACTION_DELETE.equalsIgnoreCase(action)) {
            handleDeleteCustomer(request, response, accountId);
            return;
        }

        RequestUtil.setFlashToast(request, "error", Messages.MSG04_SYSTEM_BUSY);
        RequestUtil.redirect(request, response, "/receptionist/customers");
    }

    private void handleDeleteCustomer(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws IOException {

        int customerId = RequestUtil.getIntParam(request, "customerId", 0);
        String reason = RequestUtil.getTrimmedParam(request, "reason");

        if (customerId <= 0) {
            RequestUtil.setFlashToast(request, "error", "Không xác định được hồ sơ khách hàng.");
            RequestUtil.redirect(request, response, "/receptionist/customers");
            return;
        }

        ServiceResult<String> result = customerDeletionService.requestOrDeleteCustomer(accountId, customerId, reason);

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getData());
            RequestUtil.redirect(request, response, "/receptionist/customers");
        } else {
            String errorMsg = result.getMessage();
            if (result.getFieldErrors() != null && result.getFieldErrors().containsKey("reason")) {
                errorMsg = result.getFieldErrors().get("reason");
            }
            RequestUtil.setFlashToast(request, "error", errorMsg);
            RequestUtil.redirect(request, response, "/receptionist/customers/form?id=" + customerId);
        }
    }
}
