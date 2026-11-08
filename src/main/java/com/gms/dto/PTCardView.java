package com.gms.dto;

/**
 * Dữ liệu 1 thẻ PT hiển thị ở khối "Đội ngũ huấn luyện viên" trên trang chủ (SRS 2.1.1, luật mục 2.6).
 *
 * Vì sao cần class riêng mà không dùng thẳng entity Employee?
 * - Họ tên nằm ở bảng Persons → Employee không chứa đủ.
 * - Trang chủ là trang công khai, KHÔNG được lộ SĐT/email → class này cố ý không có 2 trường đó.
 *
 * Ví dụ 1 thẻ: Đinh Công Sơn | Strength & Conditioning | 8 năm kinh nghiệm (số to trên thẻ).
 *
 * @author Le Cong Minh
 */
public class PTCardView {

    private final int employeeId;
    private final String fullName;
    private final String specialty;
    private final Integer yearsOfExperience;

    /**
     * @param employeeId Id nhân viên (bảng Employees)
     * @param fullName họ tên (bảng Persons)
     * @param specialty chuyên môn; có thể null nếu Manager chưa nhập
     * @param yearsOfExperience số năm kinh nghiệm; có thể null nếu Manager chưa nhập
     */
    public PTCardView(int employeeId, String fullName, String specialty, Integer yearsOfExperience) {
        this.employeeId = employeeId;
        this.fullName = fullName;
        this.specialty = specialty;
        this.yearsOfExperience = yearsOfExperience;
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

    @Override
    public String toString() {
        return "PtCardView{employeeId=" + employeeId + ", fullName=" + fullName
                + ", yearsOfExperience=" + yearsOfExperience + "}";
    }
}
