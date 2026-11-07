package com.gms.dto;

/**
 * Dữ liệu 1 thẻ PT hiển thị ở khối "Đội ngũ huấn luyện viên" trên trang chủ (SRS 2.1.1, luật mục 2.6).
 *
 * Vì sao cần class riêng mà không dùng thẳng entity Employee?
 * - Họ tên nằm ở bảng Persons, số buổi đã dạy đếm từ bảng Sessions → Employee không chứa đủ.
 * - Trang chủ là trang công khai, KHÔNG được lộ SĐT/email → class này cố ý không có 2 trường đó.
 *
 * Ví dụ 1 thẻ: Đinh Công Sơn | Strength & Conditioning | 8 năm | 1.400 buổi → avatar hiện "CS".
 *
 * @author Le Cong Minh
 */
public class PTCardView {

    private final int employeeId;
    private final String fullName;
    private final String specialty;
    private final Integer yearsOfExperience;
    private final int completedSessionCount;

    /**
     * @param employeeId Id nhân viên (bảng Employees)
     * @param fullName họ tên (bảng Persons)
     * @param specialty chuyên môn; có thể null nếu Manager chưa nhập
     * @param yearsOfExperience số năm kinh nghiệm; có thể null nếu Manager chưa nhập
     * @param completedSessionCount số buổi PT đã dạy xong (Sessions.Status = 'CheckedIn')
     */
    public PTCardView(int employeeId, String fullName, String specialty,
            Integer yearsOfExperience, int completedSessionCount) {
        this.employeeId = employeeId;
        this.fullName = fullName;
        this.specialty = specialty;
        this.yearsOfExperience = yearsOfExperience;
        this.completedSessionCount = completedSessionCount;
    }

    public int getEmployeeId() {
        return employeeId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getSpecialty() {
        return specialty;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public int getCompletedSessionCount() {
        return completedSessionCount;
    }

    @Override
    public String toString() {
        return "PtCardView{employeeId=" + employeeId + ", fullName=" + fullName
                + ", completedSessionCount=" + completedSessionCount + "}";
    }
}
