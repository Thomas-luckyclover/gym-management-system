package com.gms.servlet.manager;

import com.gms.constant.SessionKey;
import com.gms.dto.PackageFormInput;
import com.gms.dto.ServiceResult;
import com.gms.entity.Package;
import com.gms.service.PackageService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Base64;

/**
 * Controller màn hình Biểu mẫu thêm mới / Chỉnh sửa gói hội viên & vé ngày (#18 - UC-10).
 * Quản lý thời hạn P-04, phân quyền thư viện bài tập, kiểm soát đồng thời (MSG22) và cảnh báo hợp đồng đang chạy.
 */
@WebServlet(urlPatterns = {"/manager/membership-packages/form"})
public class MembershipPackageFormServlet extends HttpServlet {

    private final PackageService packageService = new PackageService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        int packageId = RequestUtil.getIntParam(request, "id", 0);
        PackageFormInput form = new PackageFormInput();
        int runningOrdersCount = 0;

        if (packageId > 0) {
            // Chế độ chỉnh sửa gói
            ServiceResult<Package> pkgResult = packageService.getPackageForEdit(packageId);
            if (!pkgResult.isSuccess() || pkgResult.getData() == null) {
                RequestUtil.setFlashToast(request, "danger", pkgResult.getMessage());
                response.sendRedirect(request.getContextPath() + "/manager/membership-packages");
                return;
            }

            Package pkg = pkgResult.getData();
            form.setPackageId(pkg.getPackageId());
            form.setName(pkg.getPackageName());
            form.setType(pkg.getPackageType() != null ? pkg.getPackageType().getDbValue() : "Membership");
            form.setPrice(String.valueOf(pkg.getPrice()));
            form.setDurationUnit(pkg.getDurationUnit());
            form.setDurationValue(pkg.getDurationValue() != null ? String.valueOf(pkg.getDurationValue()) : null);
            form.setUnlocksExerciseLibrary(pkg.isUnlocksExerciseLibrary());
            form.setDescription(pkg.getDescription());
            form.setBenefits(pkg.getBenefits());
            form.setActive(pkg.isActive());
            if (pkg.getRowVersion() != null) {
                form.setRowVersion(Base64.getEncoder().encodeToString(pkg.getRowVersion()));
            }

            runningOrdersCount = packageService.countRunningOrders(packageId);
        } else {
            // Chế độ tạo mới
            form.setType("Membership");
            form.setDurationUnit("Month");
            form.setDurationValue("1");
            form.setActive(true);
        }

        prepareFormView(request, session, form, runningOrdersCount);
        request.getRequestDispatcher("/WEB-INF/jsp/manager/membership-package-form.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        if (!RequestUtil.isValidCsrfToken(request)) {
            RequestUtil.setFlashToast(request, "danger", "Phiên làm việc đã hết hạn hoặc mã bảo mật CSRF không hợp lệ.");
            response.sendRedirect(request.getContextPath() + "/manager/membership-packages");
            return;
        }

        int accountId = (int) session.getAttribute(SessionKey.ACCOUNT_ID);
        PackageFormInput form = extractFormInput(request);

        ServiceResult<Integer> result;
        if (form.getPackageId() == null || form.getPackageId() == 0) {
            result = packageService.createMembershipPackage(accountId, form);
        } else {
            result = packageService.updateMembershipPackage(accountId, form);
        }

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
            response.sendRedirect(request.getContextPath() + "/manager/membership-packages");
        } else {
            request.setAttribute("errorMessage", result.getMessage());
            request.setAttribute("fieldErrors", result.getFieldErrors());
            int runningOrders = (form.getPackageId() != null && form.getPackageId() > 0)
                    ? packageService.countRunningOrders(form.getPackageId()) : 0;
            prepareFormView(request, session, form, runningOrders);
            request.getRequestDispatcher("/WEB-INF/jsp/manager/membership-package-form.jsp")
                    .forward(request, response);
        }
    }

    private PackageFormInput extractFormInput(HttpServletRequest request) {
        PackageFormInput form = new PackageFormInput();
        int packageId = RequestUtil.getIntParam(request, "packageId", 0);
        form.setPackageId(packageId > 0 ? packageId : null);
        form.setName(RequestUtil.getTrimmedParam(request, "name"));
        form.setType(RequestUtil.getTrimmedParam(request, "type"));
        form.setPrice(RequestUtil.getTrimmedParam(request, "price"));
        form.setDurationUnit(RequestUtil.getTrimmedParam(request, "durationUnit"));
        form.setDurationValue(RequestUtil.getTrimmedParam(request, "durationValue"));
        form.setUnlocksExerciseLibrary("true".equalsIgnoreCase(request.getParameter("unlocksExerciseLibrary"))
                || "on".equalsIgnoreCase(request.getParameter("unlocksExerciseLibrary")));
        form.setDescription(RequestUtil.getTrimmedParam(request, "description"));
        form.setBenefits(RequestUtil.getTrimmedParam(request, "benefits"));
        form.setActive("true".equalsIgnoreCase(request.getParameter("active"))
                || "on".equalsIgnoreCase(request.getParameter("active")));
        form.setRowVersion(RequestUtil.getTrimmedParam(request, "rowVersion"));
        return form;
    }

    private void prepareFormView(HttpServletRequest request, HttpSession session, PackageFormInput form, int runningOrdersCount) {
        String csrfToken = RequestUtil.getOrCreateCsrfToken(session);
        request.setAttribute("csrfToken", csrfToken);
        request.setAttribute("form", form);
        request.setAttribute("runningOrdersCount", runningOrdersCount);
        request.setAttribute("allowedMonths", PackageService.ALLOWED_MEMBERSHIP_MONTHS);
        request.setAttribute("activeNav", "packages");
        boolean isEdit = (form.getPackageId() != null && form.getPackageId() > 0);
        request.setAttribute("isEdit", isEdit);
        request.setAttribute("pageTitle", isEdit
                ? "Chỉnh sửa gói hội viên · Super Gym Quản lý"
                : "Thêm mới gói hội viên · Super Gym Quản lý");
    }
}
