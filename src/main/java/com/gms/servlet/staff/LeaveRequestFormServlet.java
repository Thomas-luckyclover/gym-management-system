package com.gms.servlet.staff;

import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.dto.StaffLeaveFormView;
import com.gms.service.LeaveRequestService;
import com.gms.util.RequestUtil;
import com.gms.entity.LeaveRequest;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Controller nhân viên (PT và Lễ tân) nộp đơn xin nghỉ phép (màn #25, UC-14).
 * Một Servlet khai báo 2 URL theo tài liệu docs/project-structure.md.
 *
 * <p>GET  /pt/leave-requests + /receptionist/leave-requests — hiển thị form nộp đơn.
 * <p>POST /pt/leave-requests + /receptionist/leave-requests — tiếp nhận và xử lý đơn nộp.
 */
@WebServlet({"/pt/leave-requests", "/receptionist/leave-requests"})
public class LeaveRequestFormServlet extends HttpServlet {

    private final LeaveRequestService leaveRequestService;

    public LeaveRequestFormServlet() {
        this.leaveRequestService = new LeaveRequestService();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {
        // B1: lấy accountId từ session đăng nhập
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // B2: gọi Service lấy dữ liệu khởi tạo cho form nộp đơn
        ServiceResult<StaffLeaveFormView> result = leaveRequestService.getStaffLeaveForm(accountId);

        // B3: chuyển dữ liệu sang JSP
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(session));
        request.setAttribute("activeNav", "leaves");
        if (!result.isSuccess()) {
            request.setAttribute("formError", result.getMessage());
        } else {
            request.setAttribute("formView", result.getData());
        }

        RequestUtil.forward(request, response, "staff/leave-request-form");
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        // B1: lấy accountId từ session
        HttpSession session = request.getSession(false);
        Integer accountId = session == null
                ? null
                : (Integer) session.getAttribute(SessionKey.ACCOUNT_ID);

        if (accountId == null) {
            RequestUtil.redirect(request, response, "/login");
            return;
        }

        // B1.1: kiểm tra CSRF token chống giả mạo request
        if (!RequestUtil.isValidCsrfToken(request)) {
            RequestUtil.setFlashToast(request, "danger",
                    "Phiên làm việc không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.");
            RequestUtil.redirect(request, response, RequestUtil.getPathWithinApp(request));
            return;
        }

        // B2: đọc và parse các tham số gửi từ form
        String leaveTypeStr = RequestUtil.getTrimmedParam(request, "leaveType");
        String leaveDateStr = RequestUtil.getTrimmedParam(request, "leaveDate");
        String shiftTypeStr = RequestUtil.getTrimmedParam(request, "shiftType");
        String reason = RequestUtil.getTrimmedParam(request, "reason");

        LeaveRequest.LeaveType leaveType = LeaveRequest.LeaveType.fromDbValue(leaveTypeStr);
        LeaveRequest.ShiftType shiftType = LeaveRequest.ShiftType.fromDbValue(shiftTypeStr);

        LocalDate leaveDate = null;
        if (leaveDateStr != null && !leaveDateStr.isBlank()) {
            try {
                leaveDate = LocalDate.parse(leaveDateStr);
            } catch (Exception ignored) {
                // leaveDate null sẽ được Service báo lỗi
            }
        }

        // B3: gọi Service nộp đơn
        ServiceResult<Integer> submitResult = leaveRequestService.submitLeaveRequest(
                accountId, leaveType, leaveDate, shiftType, reason);

        // B4: nếu thất bại, nạp lại form kèm thông báo lỗi và giữ lại giá trị đã nhập
        if (!submitResult.isSuccess()) {
            ServiceResult<StaffLeaveFormView> formResult = leaveRequestService.getStaffLeaveForm(accountId);
            if (formResult.isSuccess()) {
                request.setAttribute("formView", formResult.getData());
            }

            request.setAttribute("submitError", submitResult.getMessage());
            request.setAttribute("fieldErrors", submitResult.getFieldErrors());
            request.setAttribute("paramLeaveType", leaveTypeStr);
            request.setAttribute("paramLeaveDate", leaveDateStr);
            request.setAttribute("paramShiftType", shiftTypeStr);
            request.setAttribute("paramReason", reason);
            request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(session));
            request.setAttribute("activeNav", "leaves");

            RequestUtil.forward(request, response, "staff/leave-request-form");
            return;
        }

        // B5: nếu thành công, gắn flash toast và redirect (PRG pattern) về lại trang hiện tại
        RequestUtil.setFlashToast(request, "success",
                "Đã nộp đơn xin nghỉ phép thành công. Đơn đang chờ Quản lý duyệt.");
        RequestUtil.redirect(request, response, RequestUtil.getPathWithinApp(request));
    }
}
