package com.gms.dto;

import java.time.LocalTime;
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

    // Service: cùng DTO dự báo với API preview; JSP hiện trong chú thích nút i.
    private final StaffingPreview staffing;

    // Service: nhóm lịch sử đầy đủ, kể cả phần liên quan nằm ngoài giới hạn 10 dòng mới nhất.
    private final List<LeaveRequestGroupView> requestGroups;

    public StaffLeaveFormView(
            int employeeId,
            String employeeCode,
            String fullName,
            String position,
            LocalTime submissionCutoffTime,
            StaffingPreview staffing,
            List<LeaveRequestGroupView> requestGroups
    ) {
        this.employeeId = employeeId;
        this.employeeCode = employeeCode;
        this.fullName = fullName;
        this.position = position;
        this.submissionCutoffTime = submissionCutoffTime;
        this.staffing = staffing;
        // Tạo danh sách chỉ đọc: JSP được xem nhưng không thêm/xóa nhóm trong dữ liệu form.
        this.requestGroups = List.copyOf(requestGroups);
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

    public StaffingPreview getStaffing() {
        return staffing;
    }

    public List<LeaveRequestGroupView> getRequestGroups() {
        return requestGroups;
    }
}
