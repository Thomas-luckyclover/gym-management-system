package com.gms.servlet.staff;

import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
import com.gms.dto.ServiceResult;
import com.gms.dto.StaffingPreview;
import com.gms.service.StaffLeaveService;
import com.gms.util.JsonUtil;
import com.gms.util.RequestUtil;
import com.gms.util.DateTimeUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.time.LocalDate;

/**
 * API JSON trả dự báo quân số theo ngày và ca nghỉ đang chọn (màn #25, SRS 5.2.1).
 *
 * <p>GET /pt/leave-requests/preview?date=yyyy-MM-dd&shift=Morning|Afternoon|FullDay
 * <p>GET /receptionist/leave-requests/preview?date=...&shift=...
 */
@WebServlet({"/pt/leave-requests/preview", "/receptionist/leave-requests/preview"})
public class LeaveRequestPreviewServlet extends HttpServlet {

    private final StaffLeaveService staffLeaveService;

    public LeaveRequestPreviewServlet() {
        this.staffLeaveService = new StaffLeaveService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // B1: lấy accountId từ session (giống LeaveRequestFormServlet)
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            JsonUtil.writeJson(response, JsonResponse.error("Phiên đăng nhập đã hết hạn."));
            return;
        }

        // B2: đọc ngày và ca; ngày sai định dạng thì coi như null để Service báo lỗi
        LocalDate leaveDate = DateTimeUtil.parseHtmlDate(RequestUtil.getTrimmedParam(request, "date"));
        String shiftType = RequestUtil.getTrimmedParam(request, "shift");

        // B3: trả JSON
        ServiceResult<StaffingPreview> result = staffLeaveService.previewStaffing(accountId, leaveDate, shiftType);
        JsonUtil.writeJson(response, JsonResponse.from(result));
    }
}
