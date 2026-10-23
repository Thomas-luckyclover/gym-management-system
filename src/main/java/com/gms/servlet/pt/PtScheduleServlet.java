package com.gms.servlet.pt;

import com.gms.constant.SessionKey;
import com.gms.constant.Role;
import com.gms.constant.Messages;
import com.gms.dto.JsonResponse;
import com.gms.dto.PtScheduleWeek;
import com.gms.dto.ServiceResult;
import com.gms.service.PtScheduleService;
import com.gms.util.DateTimeUtil;
import com.gms.util.RequestUtil;
import com.gms.util.JsonUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** GET tải lịch tuần/danh sách; POST gọi Service công bố các khung nháp. */
@WebServlet("/pt/schedule")
public class PtScheduleServlet extends HttpServlet {

    private static final String VIEW = "pt/schedule"; // RequestUtil.forward ghép thành /WEB-INF/jsp/pt/schedule.jsp.
    private static final String PAGE_URL = "/pt/schedule"; // URL trình duyệt, dùng để quay lại sau đăng nhập.
    private final PtScheduleService scheduleService = new PtScheduleService(); // Service dựng tuần từ dữ liệu DAO.

    // ================================================================================================================
    // 1. MỞ MÀN — doGet gọi loadWeek; các hàm phụ nằm ngay dưới, không truy vấn DB trong Servlet
    // ================================================================================================================

    /** Tài khoản trong session → ServiceResult tuần lịch → JSP; bước này chỉ đọc dữ liệu. */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // B1: chỉ lấy tài khoản từ session; chưa đăng nhập thì quay về sau đăng nhập.
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login?returnUrl=" + PAGE_URL);
            return;
        }

        // B2: Service trả week hoặc scheduleError; JSP phân biệt tải lỗi với tuần chưa có khung.
        loadWeek(request, accountId);
        loadView(request);
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        request.setAttribute("draftAccountId", accountId);
        request.setAttribute("activeNav", "schedule");
        request.setAttribute("pageTitle", "Lịch dạy");

        // B3: mở JSP trong WEB-INF; quyền PT đã được PtAreaFilter kiểm tra.
        RequestUtil.forward(request, response, VIEW);
    }

    /** Mã tài khoản từ session đăng nhập; null nếu chưa có session hoặc tài khoản. */
    private Integer getAccountId(HttpServletRequest request) {
        // false: chỉ đọc session đang có, không tạo session mới cho người chưa đăng nhập.
        HttpSession session = request.getSession(false);
        return session == null ? null : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);
    }

    /** Ngày trên URL + tài khoản + giờ VN → week cho JSP; lỗi không biến thành lịch trống. */
    private void loadWeek(HttpServletRequest request, int accountId) {
        // B1: lấy giờ Việt Nam một lần; today luôn là hôm nay, không phải ngày trên URL.
        LocalDateTime now = DateTimeUtil.now();
        LocalDate today = now.toLocalDate();

        // B2: ngày bất kỳ xác định tuần cần xem; now dùng để tính nhãn và quyền ở thời điểm hiện tại.
        LocalDate date = PtScheduleService.parseDate(request.getParameter("date"), today);
        request.setAttribute("today", today);
        request.setAttribute("viewDate", date); // Link chuyển view giữ ngày đang xem.
        ServiceResult<PtScheduleWeek> result = scheduleService.getWeek(accountId, date, now);

        // B3: DTO chứa đủ mốc tuần, cấu hình và 7 ngày; không tính lại hoặc đọc cấu hình lần nữa.
        if (result.isSuccess()) {
            request.setAttribute("week", result.getData());
        } else {
            // Lỗi DB hoặc không có hồ sơ PT: JSP hiện thông báo, không bật chọn khung.
            request.setAttribute("scheduleError", result.getMessage());
        }
    }

    /** Chỉ nhận list hoặc week; thiếu/sai tham số thì dùng lịch tuần. */
    private void loadView(HttpServletRequest request) {
        // B1: không đưa nguyên tham số URL vào class CSS hoặc đường dẫn JSP.
        String view = "week";
        if ("list".equals(request.getParameter("view"))) {
            view = "list";
        }

        // B2: JSP dùng cùng một bộ khung; CSS đổi cách xếp theo view đã chuẩn hóa.
        request.setAttribute("view", view);
    }

    // ================================================================================================================
    // 2. CÔNG BỐ — nhận các cặp slotDate/startTime, không nhận mã PT hoặc EndTime từ client
    // ================================================================================================================

    /** POST cùng URL: kiểm tra session/CSRF rồi trả JsonResponse cho nút Công bố. */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // B1: hết phiên/sai vai trò không được ghi, kể cả khi gọi POST trực tiếp.
        Integer accountId = getAccountId(request);
        if (accountId == null) {
            JsonUtil.writeJson(response, 401, JsonResponse.error(Messages.MSG06_SESSION_EXPIRED));
            return;
        }
        if (!Role.PT.getDbValue().equals(request.getSession(false).getAttribute(SessionKey.ROLE))
                || !RequestUtil.isValidCsrfToken(request)) {
            JsonUtil.writeJson(response, 403, JsonResponse.error(Messages.MSG07_NO_PERMISSION));
            return;
        }
        // B2: mảng giữ từng cặp theo thứ tự gửi; Service kiểm tra lại mọi giá trị.
        Integer duration = RequestUtil.getPositiveIntParam(request, "duration");
        ServiceResult<Integer> result = scheduleService.publish(accountId,
                request.getParameterValues("slotDate"), request.getParameterValues("startTime"),
                duration == null ? 0 : duration, DateTimeUtil.now());
        if (result.isSuccess()) RequestUtil.setFlashToast(request, "success", result.getMessage());
        JsonUtil.writeJson(response, JsonResponse.from(result));
    }
}
