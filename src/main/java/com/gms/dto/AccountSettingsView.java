package com.gms.dto;

import com.gms.constant.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Dữ liệu hiển thị trên màn Account Settings.
 *
 * CBR-108: với Staff (Admin/Manager/PT/Receptionist), các field từ Employee
 * (position, specialty, yearsOfExperience, hireDate, commissionRate,
 * employmentStatus)
 * chỉ để HIỂN THỊ READ-ONLY — không có setter, không được sửa qua màn này (chỉ
 * Manager sửa qua UC-13).
 * Với Customer, các field này luôn null (không có Employee).
 */
public class AccountSettingsView {

    private final int accountId;
    private final int personId;
    private final String fullName;
    private final LocalDate dateOfBirth;
    private final String phone;
    private final String email;
    private final String avatar;
    private final Role role;
    private final String employeeCode;
    private final LocalDate hireDate;
    private final String employmentStatus;

    // Service lấy Employees.Position; JSP hiển thị chức vụ thực tế, không suy từ role.
    private final Role position;
    // Employees.Specialty; chuyên môn chỉ hiển thị trong Profile PT, null hiện dấu —.
    private final String specialty;
    // Employees.YearsOfExperience; số năm kinh nghiệm PT, giữ null khác với 0 năm.
    private final Integer yearsOfExperience;
    // Employees.CommissionRate; phần trăm hoa hồng PT, chỉ đọc trên giao diện.
    private final Integer commissionRate;
    // Customers.CustomerCode của chính Person đăng nhập; dùng ở header và thông tin khách.
    private final String customerCode;
    // Customers.CustomerType; loại hồ sơ khách, không lấy loại của hồ sơ được giám hộ.
    private final String customerType;
    // Customers.Status; trạng thái hội viên hiện có, không tính lại từ gói tập.
    private final String membershipStatus;
    // Customers.JoinedAt; ngày tham gia của hồ sơ khách được chọn, JSP định dạng ngày.
    private final LocalDateTime joinedAt;

    public AccountSettingsView(
            int accountId,
            int personId,
            String fullName,
            LocalDate dateOfBirth,
            String phone,
            String email,
            String avatar,
            Role role,
            String employeeCode,
            LocalDate hireDate,
            String employmentStatus

    ) {

        this(accountId, personId, fullName, dateOfBirth, phone, email, avatar, role,
                employeeCode, hireDate, employmentStatus,
                null, null, null, null, null, null, null, null);
    }

    /**
     * Service ghép Person và hồ sơ theo vai trò đang hoạt động để Servlet đưa lên Profile.
     * Các trường thuộc vai trò khác hoặc chưa có dữ liệu được giữ null.
     */
    public AccountSettingsView(
            int accountId, int personId, String fullName, LocalDate dateOfBirth,
            String phone, String email, String avatar, Role role,
            String employeeCode, LocalDate hireDate, String employmentStatus,
            Role position, String specialty, Integer yearsOfExperience, Integer commissionRate,
            String customerCode, String customerType, String membershipStatus, LocalDateTime joinedAt) {

        this.accountId = accountId;
        this.personId = personId;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.phone = phone;
        this.email = email;
        this.avatar = avatar;
        this.role = role;
        this.employeeCode = employeeCode;
        this.hireDate = hireDate;
        this.employmentStatus = employmentStatus;
        this.position = position;
        this.specialty = specialty;
        this.yearsOfExperience = yearsOfExperience;
        this.commissionRate = commissionRate;
        this.customerCode = customerCode;
        this.customerType = customerType;
        this.membershipStatus = membershipStatus;
        this.joinedAt = joinedAt;
    }

    public int getAccountId() {
        return accountId;
    }

    public int getPersonId() {
        return personId;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatar() {
        return avatar;
    }

    public Role getRole() {
        return role;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    /** Chức vụ thực tế từ hồ sơ nhân viên, chỉ dùng để hiển thị. */
    public Role getPosition() {
        return position;
    }

    /** Chuyên môn PT, null khi chưa khai báo. */
    public String getSpecialty() {
        return specialty;
    }

    /** Số năm kinh nghiệm; 0 là giá trị hợp lệ. */
    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    /** Hoa hồng theo đơn vị phần trăm, không có setter. */
    public Integer getCommissionRate() {
        return commissionRate;
    }

    /** Mã hồ sơ khách của chính Person đăng nhập. */
    public String getCustomerCode() {
        return customerCode;
    }

    /** Loại khách theo dữ liệu đã lưu. */
    public String getCustomerType() {
        return customerType;
    }

    /** Trạng thái hội viên từ Customers.Status. */
    public String getMembershipStatus() {
        return membershipStatus;
    }

    /** Thời điểm tham gia của hồ sơ khách. */
    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    /**
     * Lấy 1 chữ cái đầu của tên (từ cuối cùng trong họ và tên) để làm avatar fallback khi chưa có ảnh.
     * Quy tắc chính thức toàn hệ thống:
     * - "Nguyễn Hải Đăng" → "Đ"
     * - "Nguyễn Đăng Vũ" → "V"
     * - "Trần Minh Anh" → "A"
     * - "Lê Hoàng Nam" → "N"
     * - "Vũ" → "V"
     * Giữ nguyên dấu tiếng Việt (không chuyển Đ thành D).
     * Xử lý an toàn khi null, rỗng hoặc nhiều khoảng trắng (trả về "—").
     */
    public String getInitials() {
        if (fullName == null || fullName.isBlank()) {
            return "—";
        }
        String trimmed = fullName.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 0) {
            return "—";
        }
        String lastName = parts[parts.length - 1];
        if (lastName.isEmpty()) {
            return "—";
        }
        return lastName.substring(0, 1).toUpperCase(Locale.forLanguageTag("vi-VN"));
    }
}
