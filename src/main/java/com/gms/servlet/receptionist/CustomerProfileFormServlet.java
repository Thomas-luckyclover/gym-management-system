package com.gms.servlet.receptionist;

import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDeletionService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import com.gms.dto.CustomerProfileDetailView;

/** BF-07: chi tiết hồ sơ; dữ liệu và quyền thao tác được Service chuẩn bị. */
@jakarta.servlet.annotation.MultipartConfig(maxFileSize = 5242880, maxRequestSize = 6291456)
@WebServlet(urlPatterns = {"/receptionist/customers/form", "/receptionist/customers/create"})
public class CustomerProfileFormServlet extends HttpServlet {

    private final CustomerDeletionService service = new CustomerDeletionService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        if ("/receptionist/customers/create".equals(request.getServletPath())
                || "create".equals(request.getParameter("mode"))) {
            request.setAttribute("activeNav", "customers");
            request.setAttribute("editMode", false);
            RequestUtil.forward(request, response, "receptionist/customer-profile-editor");
            return;
        }
        showPage(request, response, RequestUtil.getIntParam(request, "id", 0));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (!RequestUtil.isValidCsrfToken(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        int accountId = (Integer) request.getSession(false).getAttribute(SessionKey.ACCOUNT_ID);
        int customerId = RequestUtil.getIntParam(request, "customerId", 0);
        if ("save".equals(request.getParameter("action"))) {
            saveProfile(request, response, accountId, customerId);
            return;
        }

        if (!"delete".equals(request.getParameter("action")) || customerId <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        String reason = RequestUtil.getTrimmedParam(request, "reason");
        ServiceResult<String> result = service.requestOrDeleteCustomer(accountId, customerId, reason);
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getData());
            RequestUtil.redirect(request, response, "/receptionist/customers");
            return;
        }
        request.setAttribute("actionError", result.getMessage());
        request.setAttribute("errors", result.getFieldErrors());
        request.setAttribute("reason", reason);
        showPage(request, response, customerId);
    }

    private void showPage(HttpServletRequest request, HttpServletResponse response, int customerId) throws ServletException, IOException {
        int accountId = (Integer) request.getSession(false).getAttribute(SessionKey.ACCOUNT_ID);
        ServiceResult<CustomerProfileDetailView> result = service.getProfileDetail(accountId, customerId, false);
        if (!result.isSuccess()) {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            RequestUtil.redirect(request, response, "/receptionist/customers");
            return;
        }
        request.setAttribute("detail", result.getData());
        if ("edit".equals(request.getParameter("mode")) && !result.getData().getProfile().isInactive()) {
            if (result.getData().getProfile().isOwnProfile()) {
                RequestUtil.setFlashToast(request, "error", "Hãy nhờ nhân viên khác sửa hồ sơ của bạn.");
                RequestUtil.redirect(request, response, "/receptionist/customers");
                return;
            }
            request.setAttribute("customer", result.getData().getCustomer());
            request.setAttribute("person", result.getData().getPerson());
            request.setAttribute("editMode", true);
            request.setAttribute("activeNav", "customers");
            RequestUtil.forward(request, response, "receptionist/customer-profile-editor");
            return;
        }
        request.setAttribute("managerView", false);
        request.setAttribute("listPath", "/receptionist/customers");
        request.setAttribute("activeNav", "customers");
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        RequestUtil.forward(request, response, "receptionist/customer-form");
    }
    /** Nhận form, kiểm tra ảnh rồi gọi Service; lỗi giữ lại nội dung đã nhập. */
    private void saveProfile(HttpServletRequest request, HttpServletResponse response, int accountId, int customerId)
            throws ServletException, IOException {
        String date = RequestUtil.getTrimmedParam(request, "dateOfBirth");
        java.time.LocalDate dob = null;
        try { dob = java.time.LocalDate.parse(date == null ? "" : date); }
        catch (java.time.format.DateTimeParseException ignored) { }
        var input = new com.gms.dto.CounterCustomerCreateInput(
                RequestUtil.getTrimmedParam(request, "fullName"), RequestUtil.getTrimmedParam(request, "phone"), dob,
                RequestUtil.getTrimmedParam(request, "customerType"), RequestUtil.getTrimmedParam(request, "guardianName"),
                RequestUtil.getTrimmedParam(request, "guardianRelationship"), RequestUtil.getTrimmedParam(request, "guardianPhone"),
                RequestUtil.getTrimmedParam(request, "email"));
        String phone2 = RequestUtil.getTrimmedParam(request, "guardianPhone2");
        java.nio.file.Path uploaded = null;
        String photoPath = null;
        ServiceResult<?> result;
        try {
            var part = request.getPart("checkInPhoto");
            if (part != null && part.getSize() > 0) {
                java.awt.image.BufferedImage image;
                try (var stream = part.getInputStream()) { image = javax.imageio.ImageIO.read(stream); }
                if (image == null || image.getWidth() > 6000 || image.getHeight() > 6000)
                    throw new IllegalArgumentException("Ảnh không hợp lệ hoặc kích thước quá lớn.");
                String directory = getServletContext().getRealPath("/uploads/checkin");
                if (directory == null) throw new IOException("Không tìm thấy thư mục lưu ảnh.");
                var folder = java.nio.file.Path.of(directory);
                java.nio.file.Files.createDirectories(folder);
                String filename = "profile-" + java.util.UUID.randomUUID() + ".png";
                uploaded = folder.resolve(filename);
                javax.imageio.ImageIO.write(image, "png", uploaded.toFile());
                photoPath = "/uploads/checkin/" + filename;
            }
            if (customerId > 0) {
                result = new com.gms.service.CustomerProfileService().update(accountId, customerId, input, phone2, photoPath, "true".equals(request.getParameter("identityVerified")));
            } else {
                String type = input.getCustomerType();
                if (!"Self".equals(type) && !"Dependent".equals(type))
                    throw new IllegalArgumentException("Loại hồ sơ không hợp lệ.");
                result = new com.gms.service.CounterSaleService().quickCreateCustomer(accountId, input, phone2, photoPath);
            }
        } catch (IllegalArgumentException e) {
            result = ServiceResult.fail(e.getMessage());
        } catch (IOException e) {
            if (uploaded != null) java.nio.file.Files.deleteIfExists(uploaded);
            throw e;
        }
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
            RequestUtil.redirect(request, response, "/receptionist/customers");
            return;
        }
        if (uploaded != null) java.nio.file.Files.deleteIfExists(uploaded);
        request.setAttribute("saveError", result.getMessage());
        request.setAttribute("errors", result.getFieldErrors());
        request.setAttribute("submitted", true);
        request.setAttribute("editMode", customerId > 0);
        request.setAttribute("activeNav", "customers");
        RequestUtil.forward(request, response, "receptionist/customer-profile-editor");
    }

}
