package com.gms.dto;

import com.gms.constant.AppConstants;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO 1 TUẦN của màn Lịch dạy — Service TẠO: PtScheduleService.getWeek (buildEmptyWeek → fillWeek).
 * Servlet: PtScheduleServlet.loadWeek → request "week" → schedule.jsp + _schedule-toolbar / _schedule-grid / _schedule-day / _schedule-slot / _schedule-pending.
 * Hàm tính sẵn: getShiftSplitHour() = 13 (mốc chia Ca sáng / Ca chiều trên trục giờ).
 */
public class PtScheduleWeek {
    // ===== IN RA MÀN =====
    // ← thứ Hai của tuần đang xem → "dd/MM – …" trên thanh tuần, khóa nháp (data-draft-key)
    // Lấy từ URL ?date= → quay về thứ Hai
    private LocalDate weekStart;
    // ← Chủ nhật → "… – dd/MM/yyyy"
    // weekEnd = weekStart + 6
    private LocalDate weekEnd;
    
    
    // prevWeek + nextWeek = weekStart + hoặc - 7
    // currentWeek: So weekStart với đồng hồ hệ thống
    // ← weekStart − 1 tuần → link "‹" (?date=)
    private LocalDate prevWeek;
    // ← weekStart + 1 tuần → link "›", nút "Chép sang tuần sau" (data-next-url)
    private LocalDate nextWeek;
    // ← tuần chứa hôm nay → ẩn nút "Tuần này" khi đang ở tuần này
    private boolean currentWeek;
    
    
    // Lấy từ SystemConfigs: Giờ đóng mở cửa mặc định or ngoại lệ: P-17
    // ← giờ mở cửa sớm nhất trong tuần (P-17 / ngày ngoại lệ / khung có sẵn) → hàng giờ đầu của trục
    private int axisStartHour;
    // ← giờ đóng cửa muộn nhất (làm tròn lên) → hàng giờ cuối
    private int axisEndHour;
    
    
    // ← SystemConfigs P-06 (thời lượng 1 buổi) → "Kết thúc … · N phút", ô ẩn duration của form Công bố
    // Đọc từ SystemConfigs: P-06
    private int sessionMinutes;
    
    
    // ← 7 PtScheduleDay (T2 → CN) → 7 cột ngày
    private List<PtScheduleDay> days = List.of();

    public LocalDate getWeekStart() { return weekStart; }
    public void setWeekStart(LocalDate weekStart) { this.weekStart = weekStart; }

    public LocalDate getWeekEnd() { return weekEnd; }
    public void setWeekEnd(LocalDate weekEnd) { this.weekEnd = weekEnd; }

    public LocalDate getPrevWeek() { return prevWeek; }
    public void setPrevWeek(LocalDate prevWeek) { this.prevWeek = prevWeek; }

    public LocalDate getNextWeek() { return nextWeek; }
    public void setNextWeek(LocalDate nextWeek) { this.nextWeek = nextWeek; }

    public boolean isCurrentWeek() { return currentWeek; }
    public void setCurrentWeek(boolean currentWeek) { this.currentWeek = currentWeek; }

    public int getAxisStartHour() { return axisStartHour; }
    public void setAxisStartHour(int axisStartHour) { this.axisStartHour = axisStartHour; }

    public int getAxisEndHour() { return axisEndHour; }
    public void setAxisEndHour(int axisEndHour) { this.axisEndHour = axisEndHour; }

    // GETTER TỰ TÍNH — không có field/setter "shiftSplitHour"; = giờ của AppConstants.AFTERNOON_SHIFT_START; JSP: pt/_schedule-grid.jspf:15 …
    public int getShiftSplitHour() { return AppConstants.AFTERNOON_SHIFT_START.getHour(); }

    public int getSessionMinutes() { return sessionMinutes; }
    public void setSessionMinutes(int sessionMinutes) { this.sessionMinutes = sessionMinutes; }

    public List<PtScheduleDay> getDays() { return days; }
    public void setDays(List<PtScheduleDay> days) { this.days = days; }
}
