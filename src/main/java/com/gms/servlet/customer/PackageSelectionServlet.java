package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.PackageSearchCriteria;
import com.gms.dto.PackageSelectionView;
import com.gms.dto.ServiceResult;
import com.gms.service.CustomerDashboardService;
import com.gms.service.PackageSelectionService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Controller màn Mua gói của Customer (SRS 7.1.1, UC-22) — URL /customer/packages.
 *
 * <p>URL /customer/packages/purchase là địa chỉ cũ mà menu bên trái và dashboard đang trỏ tới;
 * giữ lại để các link đó không bị 404 cho tới khi nhóm thống nhất một địa chỉ.</p>
 *
 * <p>Chỉ điều phối: đọc tham số tìm kiếm/lọc/sắp xếp, gọi Service, forward JSP.
 * Phân quyền vai trò Customer do CustomerAreaFilter đảm nhiệm.</p>
 */
@WebServlet(urlPatterns = {"/customer/packages", "/customer/packages/purchase"})
public class PackageSelectionServlet extends HttpServlet {

    private static final String VIEW = "customer/package-selection";

    /** Thư mục ảnh gói trong webapp; Service chọn ảnh theo tên file có trong thư mục này. */
    private static final String PACKAGE_IMAGE_DIRECTORY = "/images/packages/";

    private final PackageSelectionService packageSelectionService = new PackageSelectionService();
    private final CustomerDashboardService dashboardService = new CustomerDashboardService();

    // ===== GET: mở màn Mua gói =====

    /**
     * GET /customer/packages?keyword=&type=&sort=&minPrice=&maxPrice=&duration=&buy=&page= —
     * hiện danh sách gói đã tìm kiếm / lọc / sắp xếp.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: lấy tài khoản đang đăng nhập; hết phiên thì về trang đăng nhập rồi quay lại màn này
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/customer/packages");
            return;
        }

        // B2: thông tin khách cho thanh trên cùng (cùng nguồn với Customer Dashboard)
        ServiceResult<CustomerDashboardView> dashboardResult = dashboardService.getDashboard(accountId);
        if (dashboardResult.isSuccess()) {
            request.setAttribute("dashboard", dashboardResult.getData());
        } else {
            request.setAttribute("dashboardError", dashboardResult.getMessage());
        }

        // B3: đọc tham số lọc từ URL; Service tự chuẩn hóa giá trị lạ
        PackageSearchCriteria criteria = readCriteria(request);

        // B4: gọi Service lấy dữ liệu màn hình
        Set<String> imageFileNames = readPackageImageFileNames();
        ServiceResult<PackageSelectionView> result = packageSelectionService.getPackageSelection(
                criteria, imageFileNames);

        // B5: thành công → packageSelection; lỗi → selectionError (JSP hiện banner, không giả làm "không có gói")
        if (result.isSuccess()) {
            request.setAttribute("packageSelection", result.getData());
        } else {
            request.setAttribute("selectionError", result.getMessage());
        }

        request.setAttribute("activeSidebar", "buy-package");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page package-selection-page");
        RequestUtil.forward(request, response, VIEW);
    }

    // ===== Hàm phụ =====

    /**
     * Gom tham số tìm kiếm / lọc từ URL. Chỉ đổi kiểu (chữ → số), không kiểm tra hợp lệ:
     * Service chuẩn hóa mọi giá trị sai.
     */
    private PackageSearchCriteria readCriteria(HttpServletRequest request) {
        String keyword = RequestUtil.getTrimmedParam(request, "keyword");
        String typeFilter = request.getParameter("type");
        String sortKey = request.getParameter("sort");
        int page = parseInt(request.getParameter("page"), 1);
        Integer minPrice = parseNullableInt(request.getParameter("minPrice"));
        Integer maxPrice = parseNullableInt(request.getParameter("maxPrice"));

        // duration và buy có thể gửi nhiều giá trị (nhiều checkbox cùng name)
        Set<String> durationKeys = new HashSet<>();
        String[] durationValues = request.getParameterValues("duration");
        if (durationValues != null) {
            for (String durationValue : durationValues) {
                durationKeys.add(durationValue);
            }
        }

        boolean buyOnline = false;
        boolean buyCounterOnly = false;
        String[] buyValues = request.getParameterValues("buy");
        if (buyValues != null) {
            for (String buyValue : buyValues) {
                if (PackageSelectionService.BUY_ONLINE.equals(buyValue)) {
                    buyOnline = true;
                }
                if (PackageSelectionService.BUY_COUNTER.equals(buyValue)) {
                    buyCounterOnly = true;
                }
            }
        }

        return new PackageSearchCriteria(keyword, typeFilter, sortKey, page, minPrice, maxPrice,
                durationKeys, buyOnline, buyCounterOnly);
    }

    /** Đổi chữ sang số; thiếu hoặc không phải số thì trả về giá trị mặc định. */
    private int parseInt(String rawValue, int defaultValue) {
        Integer parsed = parseNullableInt(rawValue);
        return parsed == null ? defaultValue : parsed;
    }

    /** Đổi chữ sang số; thiếu, rỗng hoặc không phải số thì trả null (nghĩa là "không lọc"). */
    private Integer parseNullableInt(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(rawValue.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Liệt kê tên các file đang có trong images/packages (VD "membership.svg", "default.svg").
     * Dùng getResourcePaths nên chạy được cả khi ứng dụng được đóng gói WAR chưa giải nén.
     *
     * @return tập tên file; rỗng nếu thư mục chưa tồn tại
     */
    private Set<String> readPackageImageFileNames() {
        // B1: lấy các đường dẫn con của thư mục, dạng "/images/packages/membership.svg"
        Set<String> resourcePaths = getServletContext().getResourcePaths(PACKAGE_IMAGE_DIRECTORY);
        Set<String> fileNames = new HashSet<>();
        if (resourcePaths == null) {
            return fileNames;
        }

        // B2: cắt bỏ phần thư mục, chỉ giữ tên file; bỏ qua thư mục con (đường dẫn kết thúc bằng "/")
        for (String resourcePath : resourcePaths) {
            boolean isDirectory = resourcePath.endsWith("/");
            if (isDirectory) {
                continue;
            }
            String fileName = resourcePath.substring(PACKAGE_IMAGE_DIRECTORY.length());
            fileNames.add(fileName);
        }
        return fileNames;
    }
}
