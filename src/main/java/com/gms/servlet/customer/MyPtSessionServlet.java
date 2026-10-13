package com.gms.servlet.customer;

import com.gms.constant.SessionKey;
import com.gms.dto.CustomerDashboardView;
import com.gms.dto.MyPtHistorySessionView;
import com.gms.dto.MyPtPackageView;
import com.gms.dto.MyPtSessionFilter;
import com.gms.dto.MyPtSessionPage;
import com.gms.dto.MyPtUpcomingSessionView;
import com.gms.dto.ServiceResult;
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
    private final CustomerDashboardService dashboardService = new CustomerDashboardService();
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

        // Lấy giờ VN đúng một lần cho cả request: hai khối Sắp tới/Lịch sử chia nhóm cùng thời điểm.
        LocalDateTime now = DateTimeUtil.now();
        // B3.1: đọc bộ lọc từ URL. Servlet chỉ lấy chuỗi thô; Service kiểm tra và đổi sang kiểu đúng.
        // Bộ lọc chung (type, dateFrom, dateTo) áp dụng hai khối; historyStatus chỉ thu hẹp Lịch sử.
        ServiceResult<MyPtSessionFilter> filterResult = sessionService.parseFilter(
                request.getParameter("type"), request.getParameter("dateFrom"),
                request.getParameter("dateTo"), request.getParameter("historyStatus"));
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
}
