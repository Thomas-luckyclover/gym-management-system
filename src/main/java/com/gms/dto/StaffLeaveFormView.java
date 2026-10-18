package com.gms.dto;

import com.gms.entity.LeaveRequest;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/**
 * DTO dữ liệu khởi tạo cho màn hình nộp đơn nghỉ phép của nhân viên (màn #25, UC-14).
 * Service tạo từ Employee, LeaveRequestDAO và SystemConfigService; Servlet chuyển sang JSP.
 */
public class StaffLeaveFormView {

    // DAO: Employees.EmployeeId của nhân viên đang đăng nhập.
    // JSP dùng để xác định nhân viên nộp đơn.
    private final int employeeId;

    // DAO: Employees.EmployeeCode (ví dụ NV-0118).
    // JSP hiển thị ở góc thông tin cá nhân.
    private final String employeeCode;

    // DAO: Persons.FullName của nhân viên.
    // JSP hiển thị tên nhân viên trên header/topbar.
    private final String fullName;

    // DAO: Employees.Position ("PT" hoặc "Receptionist").
    // JSP dùng để quyết định nhãn hiển thị và layout phù hợp.
    private final String position;

    // SystemConfigs: tham số P-12 (mặc định 15:00).
    // JSP/JS dùng để hiển thị thông tin hạn chốt và validate client-side.
    private final LocalTime submissionCutoffTime;

    // SystemConfigs: tham số P-13 (số PT tối thiểu mỗi ca, mặc định 2).
    private final int minPtPerShift;

    // SystemConfigs: tham số P-14 (số Lễ tân tối thiểu mỗi ca, mặc định 1).
    private final int minReceptionistPerShift;

    // DAO: danh sách các đơn xin nghỉ gần đây của nhân viên này.
    // JSP lặp để hiển thị bảng "Đơn đã nộp" kèm badge trạng thái.
    private final List<LeaveRequest> myRequests;

    public StaffLeaveFormView(
            int employeeId,
            String employeeCode,
            String fullName,
            String position,
            LocalTime submissionCutoffTime,
            List<LeaveRequest> myRequests
    ) {
        this(employeeId, employeeCode, fullName, position, submissionCutoffTime, 2, 1, myRequests);
    }

    public StaffLeaveFormView(
            int employeeId,
            String employeeCode,
            String fullName,
            String position,
            LocalTime submissionCutoffTime,
            int minPtPerShift,
            int minReceptionistPerShift,
            List<LeaveRequest> myRequests
    ) {
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.position = position;
        this.submissionCutoffTime = submissionCutoffTime;
        this.minPtPerShift = minPtPerShift;
        this.minReceptionistPerShift = minReceptionistPerShift;
        this.myRequests = myRequests == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(myRequests);
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPosition() {
        return position;
    }

    public LocalTime getSubmissionCutoffTime() {
        return submissionCutoffTime;
    }

    public int getMinPtPerShift() {
        return minPtPerShift;
    }

    public int getMinReceptionistPerShift() {
        return minReceptionistPerShift;
    }

    public List<LeaveRequest> getMyRequests() {
        return myRequests;
    }
}
