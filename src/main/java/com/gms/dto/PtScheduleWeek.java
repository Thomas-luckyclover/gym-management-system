package com.gms.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Một tuần của màn Lịch dạy PT: tuần → days (7 ngày) → items (các khung giờ).
 * PtScheduleService sẽ tạo DTO này sau khi đọc dữ liệu từ PtScheduleDAO ở các bước sau.
 * Servlet chuyển DTO sang JSP; JSP đọc getter bằng EL, ví dụ ${week.weekStart}, ${week.days}.
 * Hiện chỉ khai báo dữ liệu; DTO không tự tính tuần, tải lịch hay kiểm tra nghiệp vụ.
 */
public class PtScheduleWeek {

    // ---------------- Các mốc tuần — Service tính từ ngày đang xem trên URL. ----------------
    private LocalDate weekStart; // Service: Thứ 2 của tuần đang xem → đầu khoảng tuần trên thanh chọn tuần.
    private LocalDate weekEnd; // Service: weekStart + 6 ngày → Chủ nhật, cuối khoảng tuần đang xem.
    private LocalDate prevWeek; // Service: weekStart - 7 ngày → tham số date của link tuần trước.
    private LocalDate nextWeek; // Service: weekStart + 7 ngày → tham số date của link tuần sau.
    private boolean currentWeek; // Service: so tuần đang xem với tuần chứa hôm nay → ẩn/hiện link Tuần này.

    // ---------------- Cấu hình trục giờ và thời lượng — Service chuẩn bị cho JSP vẽ lịch. ----------------
    private int axisStartHour; // Service: giờ đầu trục lịch → mốc tính vị trí các khung, ví dụ 5 ứng với 05:00.
    private int axisEndHour; // Service: giờ cuối trục lịch → giới hạn các hàng giờ JSP cần vẽ.
    private int sessionMinutes; // Service: SystemConfigs P-06 → thời lượng mỗi khung, tính bằng phút.

    // ---------------- Dữ liệu các ngày — không dùng PtScheduleDayView của Dashboard. ----------------
    private List<PtScheduleDay> days = List.of(); // Service: dựng đủ 7 ngày từ weekStart → JSP lặp cột ngày, kể cả ngày không có khung.

    // ---------------- Getter / Setter: chỉ đọc hoặc gán, không xử lý nghiệp vụ ----------------

    /** Đọc weekStart: Service: Thứ 2 của tuần đang xem → đầu khoảng tuần trên thanh chọn tuần. */
    public LocalDate getWeekStart() {
        return weekStart;
    }

    /** Gán weekStart đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    /** Đọc weekEnd: Service: weekStart + 6 ngày → Chủ nhật, cuối khoảng tuần đang xem. */
    public LocalDate getWeekEnd() {
        return weekEnd;
    }

    /** Gán weekEnd đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setWeekEnd(LocalDate weekEnd) {
        this.weekEnd = weekEnd;
    }

    /** Đọc prevWeek: Service: weekStart - 7 ngày → tham số date của link tuần trước. */
    public LocalDate getPrevWeek() {
        return prevWeek;
    }

    /** Gán prevWeek đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setPrevWeek(LocalDate prevWeek) {
        this.prevWeek = prevWeek;
    }

    /** Đọc nextWeek: Service: weekStart + 7 ngày → tham số date của link tuần sau. */
    public LocalDate getNextWeek() {
        return nextWeek;
    }

    /** Gán nextWeek đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setNextWeek(LocalDate nextWeek) {
        this.nextWeek = nextWeek;
    }

    /** Đọc currentWeek: Service: so tuần đang xem với tuần chứa hôm nay → ẩn/hiện link Tuần này. */
    public boolean isCurrentWeek() {
        return currentWeek;
    }

    /** Gán currentWeek đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setCurrentWeek(boolean currentWeek) {
        this.currentWeek = currentWeek;
    }

    /** Đọc axisStartHour: Service: giờ đầu trục lịch → mốc tính vị trí các khung, ví dụ 5 ứng với 05:00. */
    public int getAxisStartHour() {
        return axisStartHour;
    }

    /** Gán axisStartHour đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setAxisStartHour(int axisStartHour) {
        this.axisStartHour = axisStartHour;
    }

    /** Đọc axisEndHour: Service: giờ cuối trục lịch → giới hạn các hàng giờ JSP cần vẽ. */
    public int getAxisEndHour() {
        return axisEndHour;
    }

    /** Gán axisEndHour đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setAxisEndHour(int axisEndHour) {
        this.axisEndHour = axisEndHour;
    }

    /** Đọc sessionMinutes: Service: SystemConfigs P-06 → thời lượng mỗi khung, tính bằng phút. */
    public int getSessionMinutes() {
        return sessionMinutes;
    }

    /** Gán sessionMinutes đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setSessionMinutes(int sessionMinutes) {
        this.sessionMinutes = sessionMinutes;
    }

    /** Đọc days: Service: dựng đủ 7 ngày từ weekStart → JSP lặp cột ngày, kể cả ngày không có khung. */
    public List<PtScheduleDay> getDays() {
        return days;
    }

    /** Gán days đã được DAO/Service chuẩn bị; không tính lại hoặc kiểm tra tại DTO. */
    public void setDays(List<PtScheduleDay> days) {
        this.days = days;
    }
}
