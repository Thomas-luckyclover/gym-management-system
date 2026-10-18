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
    private final int employeeId;

    // DAO: Employees.EmployeeCode (ví dụ NV-0005).
    private final String employeeCode;

    // DAO: Persons.FullName của nhân viên.
    private final String fullName;

    // DAO: Employees.Position ("PT" hoặc "Receptionist").
    private final String position;

    // SystemConfigs: tham số P-12 (mặc định 15:00).
    private final LocalTime submissionCutoffTime;

    // SystemConfigs: tham số P-13 (số PT tối thiểu mỗi ca, mặc định 2).
    private final int minPtPerShift;

    // SystemConfigs: tham số P-14 (số Lễ tân tối thiểu mỗi ca, mặc định 1).
    private final int minReceptionistPerShift;

    // Dự báo số PT còn trực ca nếu đơn nghỉ được duyệt (SRS 5.2.1 Staffing preview).
    private final int remainingPt;

    // Dự báo số Lễ tân còn trực ca nếu đơn nghỉ được duyệt (SRS 5.2.1 Staffing preview).
    private final int remainingReceptionist;

    // DAO: danh sách các đơn xin nghỉ gần đây của nhân viên này.
    private final List<LeaveRequest> myRequests;

    public StaffLeaveFormView(
            int employeeId,
            String employeeCode,
            String fullName,
            String position,
            LocalTime submissionCutoffTime,
            List<LeaveRequest> myRequests
    ) {
        this(employeeId, employeeCode, fullName, position, submissionCutoffTime, 2, 1, 2, 1, myRequests);
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
        this(employeeId, employeeCode, fullName, position, submissionCutoffTime,
                minPtPerShift, minReceptionistPerShift, minPtPerShift, minReceptionistPerShift, myRequests);
    }

    public StaffLeaveFormView(
            int employeeId,
            String employeeCode,
            String fullName,
            String position,
            LocalTime submissionCutoffTime,
            int minPtPerShift,
            int minReceptionistPerShift,
            int remainingPt,
            int remainingReceptionist,
            List<LeaveRequest> myRequests
    ) {
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.position = position;
        this.submissionCutoffTime = submissionCutoffTime;
        this.minPtPerShift = minPtPerShift;
        this.minReceptionistPerShift = minReceptionistPerShift;
        this.remainingPt = remainingPt;
        this.remainingReceptionist = remainingReceptionist;
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

    public int getRemainingPt() {
        return remainingPt;
    }

    public int getRemainingReceptionist() {
        return remainingReceptionist;
    }

    public List<LeaveRequest> getMyRequests() {
        return myRequests;
    }
}
