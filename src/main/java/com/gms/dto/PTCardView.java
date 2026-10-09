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

    /**
     * Chữ cái viết tắt hiện trong ô tròn avatar (luật 2.6: dùng chữ cái đầu tên, KHÔNG dùng ảnh thật).
     * Lấy chữ đầu của 2 từ CUỐI trong họ tên — giống prototype index.html.
     *
     * Ví dụ: "Đinh Công Sơn"          → "CS"
     *        "Nguyễn Thị Kiều Trang"  → "KT"
     *        "David Miller"           → "DM"
     *        "Sơn"   (chỉ 1 từ)       → "S"
     *
     * Trong JSP gọi: ${pt.initials}
     */
    public String getInitials() {
        // B1: tên rỗng thì không có gì để lấy
        if (fullName == null || fullName.trim().isEmpty()) {
            return "";
        }

        // B2: tách họ tên thành các từ theo khoảng trắng (\\s+ = 1 hoặc nhiều khoảng trắng liền nhau)
        String[] words = fullName.trim().split("\\s+");

        // B3: chỉ có 1 từ → lấy 1 chữ
        String lastWord = words[words.length - 1];
        if (words.length == 1) {
            return lastWord.substring(0, 1).toUpperCase();
        }

        // B4: từ 2 từ trở lên → chữ đầu của từ áp chót + chữ đầu của từ cuối
        String secondLastWord = words[words.length - 2];
        String initials = secondLastWord.substring(0, 1) + lastWord.substring(0, 1);
        return initials.toUpperCase();
    }

    @Override
    public String toString() {
        return "PtCardView{employeeId=" + employeeId + ", fullName=" + fullName
                + ", completedSessionCount=" + completedSessionCount + "}";
    }
}
