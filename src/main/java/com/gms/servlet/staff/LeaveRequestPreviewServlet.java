package com.gms.servlet.staff;

import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
import com.gms.dto.ServiceResult;
import com.gms.dto.LeaveSubmissionPreview;
import com.gms.entity.LeaveRequest;
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
 * API JSON trả phương án Annual/Unpaid, số buổi PT trùng và dự báo ngày đầu (màn #25).
 *
 * <p>GET /pt/leave-requests/preview?date=yyyy-MM-dd&toDate=yyyy-MM-dd&shift=FullDay&leaveType=Annual
 * <p>GET /receptionist/leave-requests/preview dùng cùng tham số, chỉ đọc nhân viên từ session.
 */
@WebServlet({"/pt/leave-requests/preview", "/receptionist/leave-requests/preview"})
public class LeaveRequestPreviewServlet extends HttpServlet {

    private final StaffLeaveService staffLeaveService;

    public LeaveRequestPreviewServlet() {
        this.staffLeaveService = new StaffLeaveService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
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
        String toDateValue = RequestUtil.getTrimmedParam(request, "toDate");
        LocalDate toDate = leaveDate;
        if (toDateValue != null && !toDateValue.isBlank()) {
            toDate = DateTimeUtil.parseHtmlDate(toDateValue);
        }
        LeaveRequest.LeaveType type = LeaveRequest.LeaveType.fromDbValue(RequestUtil.getTrimmedParam(request, "leaveType"));
        ServiceResult<LeaveSubmissionPreview> result = staffLeaveService.previewSubmission(accountId, type,
                leaveDate, toDate, LeaveRequest.ShiftType.fromDbValue(shiftType));
        JsonUtil.writeJson(response, JsonResponse.from(result));
    }
}
