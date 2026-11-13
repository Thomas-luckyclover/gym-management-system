package com.gms.servlet.receptionist;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.dto.ShiftDetailView;
import com.gms.service.ShiftService;
import com.gms.util.PaginationUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Controller màn hình Ca làm việc và Đối soát tiền mặt của Lễ tân (Màn #39 - UC-25, Business Flow BF-06).
 *
 * <p>Mapping URL: {@code @WebServlet("/receptionist/shift")}</p>
 *
 * <p>Triển khai toàn diện theo sơ đồ Business Flow BF-06 trong SRS:</p>
 * <ul>
 *   <li><b>Trigger & Main Flow:</b> Lễ tân mở ca trực đúng giờ (openShift: Shift -> Open, UC-25).</li>
 *   <li><b>Alternative Flow A1:</b> Lễ tân xác nhận đã nhận tiền chuyển khoản ngân hàng (confirmTransfer, CBR-77, CBR-105).</li>
 *   <li><b>Alternative Flow A2:</b> Chốt ca khi có chênh lệch tiền mặt thực tế vs sổ sách, yêu cầu lý do (closeShift, BR-46, DBR-55).</li>
 *   <li><b>Exception Flow E1:</b> Chặn chốt ca và cảnh báo khi còn đơn hàng quầy đang Pending (CBR-78, MSG87).</li>
 *   <li><b>Exception Flow E2:</b> Hủy đơn hàng lập nhầm tại quầy trong ca mở, hoàn tiền mặt và phục hồi quyền lợi (cancelOrder, CBR-11).</li>
 * </ul>
 *
 * <p>GET: Lấy thông tin ca hiện tại, 4 thẻ KPI, danh sách giao dịch có phân trang và bộ lọc.</p>
 * <p>POST: Xử lý các thao tác mở ca, chốt ca, xác nhận chuyển khoản và hủy đơn quầy theo mô hình PRG (Post/Redirect/Get).</p>
 *
 * @author Antigravity
 */
@WebServlet("/receptionist/shift")
public class ShiftServlet extends HttpServlet {

    public static final String VIEW_NAME = "receptionist/shift";

    public static final String ACTION_OPEN_SHIFT = "openShift";
    public static final String ACTION_CLOSE_SHIFT = "closeShift";
    public static final String ACTION_CONFIRM_TRANSFER = "confirmTransfer";
    public static final String ACTION_CANCEL_ORDER = "cancelOrder";

    /**
     * Tiện ích chuẩn hóa chuỗi tiền mặt đếm được (loại bỏ dấu phân cách . , khoảng trắng).
     *
     * @param rawCash chuỗi thô người dùng nhập
     * @return chuỗi chỉ chứa số, hoặc "0" nếu rỗng
     */
    // Lọc bỏ dấu chấm/phẩy do JS format (VD: "2.600.000" -> "2600000") để Long.parseLong không bị lỗi NumberFormatException.
    public static String cleanNumericCash(String rawCash) {
        if (rawCash == null) return "0";
        String cleanNum = rawCash.replaceAll("[^0-9]", "");
        return cleanNum.isEmpty() ? "0" : cleanNum;
    }

    private final ShiftService shiftService = new ShiftService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        // B1: Đọc các tham số bộ lọc và phân trang từ request
        String query = RequestUtil.getTrimmedParam(request, "q");
        String method = RequestUtil.getTrimmedParam(request, "method");
        String status = RequestUtil.getTrimmedParam(request, "status");
        int shiftId = RequestUtil.getIntParam(request, "shiftId", 0);

        int page = PaginationUtil.getPageNumber(request);
        int pageSize = AppConstants.DEFAULT_PAGE_SIZE;

        // B2: Gọi Service để nạp toàn bộ dữ liệu ca làm việc
        ServiceResult<ShiftDetailView> result = shiftService.getShiftDetail(
                accountId, shiftId, query, method, status, page, pageSize);

        if (result.isSuccess()) {
            request.setAttribute("detail", result.getData());
        } else {
            request.setAttribute("errorMessage", result.getMessage());
        }

        request.setAttribute("activeNav", "shifts");
        RequestUtil.forward(request, response, VIEW_NAME);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionKey.ACCOUNT_ID) == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // CSRF: token sai / thiếu → chặn (form: toast + quay lại; AJAX: JSON 403).
        if (RequestUtil.rejectInvalidCsrf(request, response, "/receptionist/shift")) {
            return;
        }

        int accountId = (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
        String action = RequestUtil.getTrimmedParam(request, "action");
        int shiftId = RequestUtil.getIntParam(request, "shiftId", 0);

        switch (action != null ? action : "") {
            case ACTION_OPEN_SHIFT:
                handleOpenShift(request, response, accountId, shiftId);
                return;
            case ACTION_CLOSE_SHIFT:
                handleCloseShift(request, response, accountId, shiftId);
                return;
            case ACTION_CONFIRM_TRANSFER:
                handleConfirmTransfer(request, response, accountId, shiftId);
                return;
            case ACTION_CANCEL_ORDER:
                handleCancelOrder(request, response, accountId, shiftId);
                return;
            default:
                RequestUtil.setFlashToast(request, "error", Messages.MSG04_SYSTEM_BUSY);
                redirectToShift(request, response, shiftId);
        }
    }

    /**
     * Xử lý mở ca làm việc mới.
     */
    private void handleOpenShift(HttpServletRequest request, HttpServletResponse response,
                                 int accountId, int shiftId) throws IOException {
        if (shiftId <= 0) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG271_SHIFT_INVALID);
            redirectToShift(request, response, 0);
            return;
        }

        ServiceResult<Void> result = shiftService.openShift(accountId, shiftId);
        finishAction(request, response, shiftId, result, Messages.MSG272_SHIFT_OPENED);
    }

    /**
     * Xử lý chốt ca làm việc và đối soát tiền mặt.
     */
    private void handleCloseShift(HttpServletRequest request, HttpServletResponse response,
                                  int accountId, int shiftId) throws IOException {
        if (shiftId <= 0) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG271_SHIFT_INVALID);
            redirectToShift(request, response, 0);
            return;
        }

        String countedCashStr = RequestUtil.getTrimmedParam(request, "countedCash");
        String varianceReason = RequestUtil.getTrimmedParam(request, "varianceReason");

        long countedCash;
        try {
            countedCash = Long.parseLong(cleanNumericCash(countedCashStr));
        } catch (NumberFormatException e) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG273_COUNTED_CASH_INVALID);
            redirectToShift(request, response, shiftId);
            return;
        }

        ServiceResult<Void> result = shiftService.closeShift(accountId, shiftId, countedCash, varianceReason);
        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", Messages.MSG274_SHIFT_CLOSED);
            redirectToShift(request, response, 0);//cố tình truyền shiftId=0 để quay về trang ưu tiên hiển thị
        } else {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
            redirectToShift(request, response, shiftId);
        }
    }

    /**
     * Xử lý Lễ tân xác nhận đã nhận tiền chuyển khoản cho đơn hàng quầy (CBR-77).
     * Điều hướng trực tiếp sang trang Bán gói tập (?order=...) để Lễ tân chốt xác nhận thu tiền.
     */
    private void handleConfirmTransfer(HttpServletRequest request, HttpServletResponse response,
                                       int accountId, int shiftId) throws IOException {
        int orderId = RequestUtil.getIntParam(request, "orderId", 0);
        if (orderId > 0) {
            RequestUtil.redirect(request, response, "/receptionist/pt-sales?order=" + orderId);
        } else {
            RequestUtil.setFlashToast(request, "error", Messages.MSG275_ORDER_INVALID);
            redirectToShift(request, response, shiftId);
        }
    }

    /**
     * Xử lý Lễ tân hủy đơn hàng tại quầy (CBR-11, CBR-115).
     */
    private void handleCancelOrder(HttpServletRequest request, HttpServletResponse response,
                                   int accountId, int shiftId) throws IOException {
        int orderId = RequestUtil.getIntParam(request, "orderId", 0);
        String cancelReason = RequestUtil.getTrimmedParam(request, "cancelReason");

        if (orderId <= 0) {
            RequestUtil.setFlashToast(request, "error", Messages.MSG275_ORDER_INVALID);
            redirectToShift(request, response, shiftId);
            return;
        }

        ServiceResult<Void> result = shiftService.cancelOrder(accountId, orderId, cancelReason);
        finishAction(request, response, shiftId, result, Messages.MSG277_COUNTER_ORDER_CANCELLED);
    }

    private void finishAction(HttpServletRequest request, HttpServletResponse response,
                              int shiftId, ServiceResult<Void> result, String successMessage) throws IOException {
        RequestUtil.setFlashToast(request,
                result.isSuccess() ? "success" : "error",
                result.isSuccess() ? successMessage : result.getMessage());
        redirectToShift(request, response, shiftId);
    }

    private void redirectToShift(HttpServletRequest request, HttpServletResponse response, int shiftId)
            throws IOException {//điều hướng trình quay trở lại đúng ca mà họ thao tác
        String path = shiftId > 0 ? "/receptionist/shift?shiftId=" + shiftId : "/receptionist/shift";
        RequestUtil.redirect(request, response, path);
    }
}
