package com.gms.servlet.customer;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.MyPtHistorySessionView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionFilter;
import com.gms.dto.MyPtSessionPage;
import com.gms.dto.MyPtUpcomingSessionView;
import com.gms.dto.ServiceResult;
import com.gms.dto.JsonResponse;
import com.gms.service.PtSessionSwapService;
import com.gms.util.JsonUtil;
import com.gms.service.CustomerDashboardService;
import com.gms.service.MyPtSessionService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/** Điều phối màn Lịch tập của tôi: gộp My Bookings và My PT Sessions. */
@WebServlet("/customer/pt-sessions")
public class MyPtSessionServlet extends HttpServlet {

    private static final String VIEW = "customer/my-pt-sessions";

    /** Tham số hành động JSP gửi lên (name="action"). */
    private static final String PARAM_ACTION = "action";

    /** Tham số mã buổi tập JSP gửi lên (name="sessionId"). */
    private static final String PARAM_SESSION_ID = "sessionId";

    /** Giá trị hành động hủy buổi tập của khách. */
    private static final String ACTION_CANCEL = "cancel";

    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
    private final PtSessionSwapService swapService = new PtSessionSwapService();
    private final MyPtSessionService sessionService = new MyPtSessionService(); // Chuẩn bị gói PT của khách đăng nhập.

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: lấy account hiện tại; CustomerAreaFilter đã kiểm tra quyền truy cập.
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/customer/pt-sessions");
            return;
        }

        // AJAX chỉ đọc slot cho dialog, không ghi DB và không tải lại cả trang.
        if ("swapOptions".equals(request.getParameter(PARAM_ACTION))) {
            Integer sessionId = RequestUtil.getPositiveIntParam(request, PARAM_SESSION_ID);
            if (sessionId == null) {
                JsonUtil.writeJson(response, JsonResponse.error(Messages.MSG108_INVALID_OPTION));
            } else {
                JsonUtil.writeJson(response, JsonResponse.from(swapService.getOptions(accountId, sessionId)));
            }
            return;
        }

        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(session));

        // B2: lấy thông tin header dùng chung với Customer Dashboard.
        ServiceResult<CustomerDashboardView> dashboardResult =
                dashboardService.getDashboard(accountId);
        if (dashboardResult.isSuccess()) {
            request.setAttribute("dashboard", dashboardResult.getData());
        } else {
            request.setAttribute("dashboardError", dashboardResult.getMessage());
        }

        // B3: lấy gói khách đã mua có Subscriptions.Status = Active, không phải Packages.Status.
        // Truyền accountId từ session; Service tự xác định customerId và quyền xem gói đôi.
        ServiceResult<List<MyPtPackageView>> ptPackagesResult =
                sessionService.getPtPackages(accountId);
        if (ptPackagesResult.isSuccess()) {
            // Danh sách rỗng vẫn là tải thành công; JSP mới hiện thông báo chưa có gói phù hợp.
            request.setAttribute("ptPackages", ptPackagesResult.getData());
        } else {
            // Lỗi tải tách khỏi trường hợp không có gói, không thay bằng danh sách rỗng.
            request.setAttribute("ptPackagesError", ptPackagesResult.getMessage());
        }

        // Bộ lọc cần cả gói cũ để khách tra cứu Lịch sử; không đổi danh sách thẻ gói Active.
        ServiceResult<List<MyPtPackageView>> packageOptionsResult =
                sessionService.getPtPackageOptions(accountId);
        if (packageOptionsResult.isSuccess()) {
            request.setAttribute("ptPackageOptions", packageOptionsResult.getData());
        } else {
            request.setAttribute("ptPackageOptionsError", packageOptionsResult.getMessage());
        }

        // Lấy giờ VN đúng một lần cho cả request: hai khối Sắp tới/Lịch sử chia nhóm cùng thời điểm.
        LocalDateTime now = DateTimeUtil.now();
        // B3.1: đọc bộ lọc từ URL. Servlet chỉ lấy chuỗi thô; Service kiểm tra và đổi sang kiểu đúng.
        // Bộ lọc chung (type, dateFrom, dateTo) áp dụng hai khối; historyStatus chỉ thu hẹp Lịch sử.
        ServiceResult<MyPtSessionFilter> filterResult = sessionService.parseFilter(
                request.getParameter("type"), request.getParameter("dateFrom"),
                request.getParameter("dateTo"), request.getParameter("historyStatus"),
                request.getParameter("subscriptionId"),
                packageOptionsResult.isSuccess() ? packageOptionsResult.getData() : null);
        MyPtSessionFilter filter;
        if (filterResult.isSuccess()) {
            filter = filterResult.getData();
        } else {
            // Có ô sai: không áp dụng một phần, giữ danh sách mặc định và báo lỗi tại từng ô.
            // Giá trị khách đã nhập vẫn còn trong ${param.*} để JSP điền lại vào ô.
            filter = MyPtSessionFilter.none();
            request.setAttribute("filterErrors", filterResult.getFieldErrors());
            request.setAttribute("filterNotApplied", true);
        }
        // JSP đọc sessionFilter để chọn câu rỗng phù hợp khi đang lọc.
        request.setAttribute("sessionFilter", filter);

        // B3.2: mỗi khối có số trang riêng; Service đổi chuỗi URL thành số nguyên dương.
        // Service giới hạn số trang theo dữ liệu sau lọc và lấy tối đa 8 ngày có buổi.
        int upcomingPageNumber = sessionService.parsePageNumber(request.getParameter("upcomingPage"));
        int historyPageNumber = sessionService.parsePageNumber(request.getParameter("historyPage"));
        request.setAttribute("upcomingPageNumber", upcomingPageNumber);
        request.setAttribute("historyPageNumber", historyPageNumber);

        // B4: lấy Scheduled chưa kết thúc; Service bổ sung cảnh báo và nhãn Đang diễn ra.
        ServiceResult<MyPtSessionPage<MyPtUpcomingSessionView>> upcomingResult =
                sessionService.getUpcomingSessionsPage(accountId, filter, now, upcomingPageNumber);
        if (upcomingResult.isSuccess()) {
            // Gom theo ngày để JSP hiển thị đầy đủ buổi; thứ tự ngày gần nhất trước.
            request.setAttribute("upcomingDays", upcomingResult.getData().getDays());
            request.setAttribute("upcomingPage", upcomingResult.getData());
        } else {
            request.setAttribute("upcomingSessionsError", upcomingResult.getMessage());
        }

        // B5: lấy lịch sử gồm CheckedIn, Missed, Cancelled và Scheduled đã qua giờ kết thúc.
        // Chỉ đọc kết quả đã lưu; Service gắn nhãn, giải thích, hint cho từng dòng.
        ServiceResult<MyPtSessionPage<MyPtHistorySessionView>> historyResult =
                sessionService.getHistorySessionsPage(accountId, filter, now, historyPageNumber);
        if (historyResult.isSuccess()) {
            // Rỗng vẫn là tải thành công; gom theo ngày, ngày mới nhất trước như thứ tự DAO.
            request.setAttribute("historyDays", historyResult.getData().getDays());
            request.setAttribute("historyPage", historyResult.getData());
        } else {
            // Lỗi tải tách khỏi trường hợp rỗng, không thay bằng danh sách rỗng.
            request.setAttribute("historySessionsError", historyResult.getMessage());
        }

        // Ngày hôm nay giờ VN: JSP dùng để tô nổi bật và hiện nhãn Hôm nay.
        request.setAttribute("todayDate", now.toLocalDate());

        // B6: chọn mục lịch và dùng màu chung của Customer Zone.
        request.setAttribute("activeSidebar", "pt-sessions");
        request.setAttribute("themeClass", "theme-pt customer-dashboard-page my-pt-sessions-page");

        // B7: chuyển dữ liệu header, gói PT, lịch sắp tới, lịch sử sang JSP.
        RequestUtil.forward(request, response, VIEW);
    }

    /**
     * Xử lý hành động của khách trên màn Lịch tập (hiện tại: hủy buổi tập).
     * <p>Luồng khớp doGet: lấy accountId từ session, parse sessionId an toàn, gọi Service,
     * redirect về trang danh sách với toast (Post/Redirect/Get) để F5 không resubmit.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // B1: lấy account hiện tại; CustomerAreaFilter đã kiểm tra quyền truy cập.
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=/customer/pt-sessions");
            return;
        }

        // B2: đọc hành động và mã buổi tập từ form. Hai bước tách rời để dễ mở rộng sau này.
        String action = request.getParameter(PARAM_ACTION);

        // B3: parse sessionId an toàn — số nguyên dương; null/0/âm thì fail thân thiện.
        //    Không cố tìm buổi trước khi parse: tránh để lộ sự tồn tại của buổi lạ.
        Integer sessionId = RequestUtil.getPositiveIntParam(request, PARAM_SESSION_ID);
        if (sessionId == null) {
            // Redirect chứ không forward: đổi URL để không lộ sessionId lạ và F5 không resubmit.
            RequestUtil.setFlashToast(request, "error", Messages.MSG08_NOT_FOUND.formatted("buổi tập"));
            RequestUtil.redirect(request, response, "/customer/pt-sessions");
            return;
        }

        // B4: phân nhánh theo action. Mỗi nhánh redirect về trang danh sách để áp dụng Post/Redirect/Get.
        if (ACTION_CANCEL.equals(action)) {
            handleCancel(request, response, accountId, sessionId);
        } else if ("swap".equals(action)) {
            if (!RequestUtil.isValidCsrfToken(request)) {
                RequestUtil.setFlashToast(request, "error", Messages.MSG07_NO_PERMISSION);
                RequestUtil.redirect(request, response, "/customer/pt-sessions");
                return;
            }
            Integer targetId = RequestUtil.getPositiveIntParam(request, "targetTimeSlotId");
            Integer originalId = RequestUtil.getPositiveIntParam(request, "originalTimeSlotId");
            ServiceResult<Void> result = targetId == null || originalId == null
                    ? ServiceResult.fail(Messages.MSG108_INVALID_OPTION)
                    : swapService.swapSession(accountId, sessionId, targetId, originalId);
            RequestUtil.setFlashToast(request, result.isSuccess() ? "success" : "error", result.getMessage());
            RequestUtil.redirect(request, response, "/customer/pt-sessions?view=upcoming");
        } else {
            // Action lạ/hết hỗ trợ: không thực hiện, báo lỗi thân thiện.
            RequestUtil.setFlashToast(request, "error", Messages.MSG108_INVALID_OPTION);
            RequestUtil.redirect(request, response, "/customer/pt-sessions");
        }
    }

    /**
     * Hủy buổi tập PT: ủy quyền cho Service, dùng toast để phản hồi.
     * Service kiểm tra quyền sở hữu, trạng thái buổi (chỉ hủy được Scheduled), chính sách hủy.
     */
    private void handleCancel(HttpServletRequest request, HttpServletResponse response,
                              int accountId, int sessionId) throws IOException {
        ServiceResult<Void> result = sessionService.cancelPtSession(accountId, sessionId);

        if (result.isSuccess()) {
            RequestUtil.setFlashToast(request, "success", result.getMessage());
        } else {
            RequestUtil.setFlashToast(request, "error", result.getMessage());
        }
        RequestUtil.redirect(request, response, "/customer/pt-sessions");
    }
}
