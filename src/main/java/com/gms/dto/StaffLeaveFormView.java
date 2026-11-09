package com.gms.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * DTO dữ liệu khởi tạo cho màn hình nộp đơn nghỉ phép của nhân viên (màn #25, UC-14).
 * Service tạo từ Employee, LeaveRequestDAO và SystemConfigService; Servlet chuyển sang JSP.
 */
public class StaffLeaveFormView {

    // DAO: Employees.EmployeeCode (ví dụ NV-0005).
    private final String employeeCode;

    // DAO: Persons.FullName của nhân viên.
    private final String fullName;

    // DAO: Employees.Position ("PT" hoặc "Receptionist").
    private final String position;

    // SystemConfigs: tham số P-10 (mặc định 15:00).
    private final LocalTime submissionCutoffTime;

    // Service: cùng DTO dự báo với API preview; JSP hiện trong chú thích nút i.
    private final StaffingPreview staffing;

    // Một trang lịch sử, gồm cả Pending/Approved/Rejected; giữ đủ các phần cùng đợt.
    private final PageResult<LeaveRequestGroupView> historyPage;

    public StaffLeaveFormView(
            String employeeCode,
            String fullName,
            String position,
            LocalTime submissionCutoffTime,
            StaffingPreview staffing,
            PageResult<LeaveRequestGroupView> historyPage
    ) {
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.position = position;
        this.submissionCutoffTime = submissionCutoffTime;
        this.staffing = staffing;
        this.historyPage = historyPage;
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

    public StaffingPreview getStaffing() {
        return staffing;
    }

    public List<LeaveRequestGroupView> getRequestGroups() {
        return historyPage.getItems();
    }

    public PageResult<LeaveRequestGroupView> getHistoryPage() {
        return historyPage;
    }
}
