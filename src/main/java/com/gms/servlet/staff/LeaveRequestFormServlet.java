package com.gms.servlet.staff;

import com.gms.constant.Messages;
import com.gms.constant.SessionKey;
import com.gms.dto.ServiceResult;
import com.gms.dto.StaffLeaveFormView;
import com.gms.dto.LeaveSubmissionPreview;
import com.gms.service.StaffLeaveService;
import com.gms.util.RequestUtil;
import com.gms.util.DateTimeUtil;
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
 * Một Servlet khai báo 2 URL.
 *
 * <p>GET  /pt/leave-requests + /receptionist/leave-requests — hiển thị form nộp đơn.
 * <p>POST /pt/leave-requests + /receptionist/leave-requests — tiếp nhận và xử lý đơn nộp.
 */
@WebServlet({"/pt/leave-requests", "/receptionist/leave-requests"})
public class LeaveRequestFormServlet extends HttpServlet {

    private final StaffLeaveService staffLeaveService;

    public LeaveRequestFormServlet() {
        this.staffLeaveService = new StaffLeaveService();
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

        showForm(request, response, accountId);
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
                    Messages.MSG214_FORM_SESSION_EXPIRED);
            RequestUtil.redirect(request, response, RequestUtil.getPathWithinApp(request));
            return;
        }

        // B1.2: kiểm tra nếu là hành động rút đơn (action=withdraw)
        String action = RequestUtil.getTrimmedParam(request, "action");
        if ("withdraw".equalsIgnoreCase(action)) {
            int leaveRequestId = RequestUtil.getIntParam(request, "leaveRequestId", 0);
            ServiceResult<Void> withdrawResult = staffLeaveService.withdrawRequest(accountId, leaveRequestId);
            if (withdrawResult.isSuccess()) {
                RequestUtil.setFlashToast(request, "success",
                        Messages.MSG215_LEAVE_WITHDRAWN);
            } else {
                RequestUtil.setFlashToast(request, "danger", withdrawResult.getMessage());
            }
            int historyPage = Math.max(1, RequestUtil.getIntParam(request, "page", 1));
            RequestUtil.redirect(request, response,
                    RequestUtil.getPathWithinApp(request) + "?tab=history&page=" + historyPage);
            return;
        }

        // B2: đọc và parse các tham số gửi từ form
        String leaveTypeStr = RequestUtil.getTrimmedParam(request, "leaveType");
        String leaveDateStr = RequestUtil.getTrimmedParam(request, "leaveDate");
        String toDateStr = RequestUtil.getTrimmedParam(request, "toDate");
        String shiftTypeStr = RequestUtil.getTrimmedParam(request, "shiftType");
        String reason = RequestUtil.getTrimmedParam(request, "reason");

        LeaveRequest.LeaveType leaveType = LeaveRequest.LeaveType.fromDbValue(leaveTypeStr);
        LeaveRequest.ShiftType shiftType = LeaveRequest.ShiftType.fromDbValue(shiftTypeStr);

        LocalDate leaveDate = DateTimeUtil.parseHtmlDate(leaveDateStr);
        LocalDate toDate = leaveDate;
        if (toDateStr != null && !toDateStr.isBlank()) {
            toDate = DateTimeUtil.parseHtmlDate(toDateStr);
        }

        // Giữ các ô đã nhập khi xem dự kiến hoặc khi nộp bị lỗi.
        request.setAttribute("paramLeaveType", leaveTypeStr);
        request.setAttribute("paramLeaveDate", leaveDateStr);
        request.setAttribute("paramToDate", toDateStr);
        request.setAttribute("paramShiftType", shiftTypeStr);
        request.setAttribute("paramReason", reason);
        String selection = leaveTypeStr + "/" + leaveDate + "/" + toDate + "/" + shiftTypeStr;
        request.setAttribute("previewSelection", selection);

        // Xem dự kiến chỉ tính toán và forward về JSP, không gọi hàm lưu đơn.
        if ("preview".equals(action)) {
            ServiceResult<LeaveSubmissionPreview> previewResult = staffLeaveService.previewSubmission(
                    accountId, leaveType, leaveDate, toDate, shiftType);
            if (previewResult.isSuccess()) {
                setPreview(request, previewResult.getData());
            } else {
                request.setAttribute("submitError", previewResult.getMessage());
                request.setAttribute("fieldErrors", previewResult.getFieldErrors());
            }
            showForm(request, response, accountId);
            return;
        }

        // B3: gọi Service nộp đơn
        ServiceResult<Integer> submitResult;
        if ("true".equals(request.getParameter("acceptUnpaid"))
                && !selection.equals(request.getParameter("previewSelection"))) {
            submitResult = ServiceResult.failField("acceptUnpaid",
                    "Ngày, ca hoặc loại nghỉ đã thay đổi. Vui lòng xem dự kiến và xác nhận lại.");
        } else {
            submitResult = staffLeaveService.submitLeaveRequest(
                accountId, leaveType, leaveDate, toDate, shiftType, reason,
                "true".equals(request.getParameter("acceptUnpaid")),
                RequestUtil.getIntParam(request, "expectedAnnualDays", -1),
                RequestUtil.getIntParam(request, "expectedUnpaidDays", -1));
        }

        // B4: nếu thất bại, nạp lại form kèm thông báo lỗi và giữ lại giá trị đã nhập
        if (!submitResult.isSuccess()) {
            // Tính lại dự kiến mới; checkbox luôn bỏ tích để người gửi xác nhận lại.
            ServiceResult<LeaveSubmissionPreview> previewResult = staffLeaveService.previewSubmission(
                    accountId, leaveType, leaveDate, toDate, shiftType);
            if (previewResult.isSuccess()) {
                setPreview(request, previewResult.getData());
            }

            request.setAttribute("submitError", submitResult.getMessage());

            request.setAttribute("fieldErrors", submitResult.getFieldErrors());
            showForm(request, response, accountId);
            return;
        }

        // B5: nộp thành công thì mở tab Đơn đã nộp để xem kết quả.
        RequestUtil.setFlashToast(request, "success",
                Messages.MSG216_LEAVE_SUBMITTED);
        RequestUtil.redirect(request, response, RequestUtil.getPathWithinApp(request) + "?tab=history");
    }

    /** Nạp thông tin nhân viên, lịch sử và token trước khi hiển thị JSP. */
    private void showForm(HttpServletRequest request, HttpServletResponse response, int accountId)
            throws ServletException, IOException {
        int page = RequestUtil.getIntParam(request, "page", 1);
        ServiceResult<StaffLeaveFormView> result = staffLeaveService.getStaffLeaveForm(accountId, page, 10);
        if (result.isSuccess()) {
            request.setAttribute("formView", result.getData());
        } else {
            request.setAttribute("formError", result.getMessage());
        }
        request.setAttribute("csrfToken", RequestUtil.getOrCreateCsrfToken(request.getSession(false)));
        request.setAttribute("activeNav", "leaves");
        RequestUtil.forward(request, response, "staff/leave-request-form");
    }

    /** Chuẩn bị ngày của từng phần để JSP in trực tiếp, không cần JS tạo HTML. */
    private void setPreview(HttpServletRequest request, LeaveSubmissionPreview preview) {
        request.setAttribute("preview", preview);
        request.setAttribute("previewPartCount",
                (preview.getAnnualDays() > 0 ? 1 : 0) + (preview.getUnpaidDays() > 0 ? 1 : 0));
        if (preview.getAnnualDays() > 0) {
            request.setAttribute("annualToDate", preview.getFromDate().plusDays(preview.getAnnualDays() - 1L));
        }
        request.setAttribute("unpaidFromDate", preview.getFromDate().plusDays(preview.getAnnualDays()));
    }
}
