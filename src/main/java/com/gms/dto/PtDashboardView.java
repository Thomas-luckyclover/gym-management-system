package com.gms.dto;

import com.gms.util.MoneyUtil;
import java.util.List;

/**
 * Toàn bộ dữ liệu của trang PT Dashboard (#49, UC-32) — Service gom 1 lần, JSP chỉ in ra.
 *
 * Bố cục theo prototype pt/trang-chu.html, nghiệp vụ theo SRS 9.1.2 + "05_Dashboard_Rules.md":
 *   Đầu trang   : lời chào + thời tiết + tóm tắt hôm nay
 *   Cột chính   : Buổi cần điểm danh (P-07) · Hoa hồng theo kỳ · Buổi sắp tới (lọc ngày) · Banner khách thiếu gói tập
 *   Cột phụ     : Khách phụ trách · Tháng · Tuần · Lịch rảnh — tất cả theo ngày trong ô "Từ ngày"
 *
 * @author Le Cong Minh
 */
public class PtDashboardView {

    /** 1 lựa chọn trong ô "Kỳ", VD value "2026-09" / "2026-Q3", label "Tháng 09/2026" / "Quý III/2026". */
    public static class PeriodOption {

        private final String value;
        private final String label;

        public PeriodOption(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }
    }

    // ===== Đầu trang =====
    private String employeeCode;
    private String fullName;
    private String initials;
    private int commissionRate;
    private String greeting;
    private String todayLabel;
    private WeatherView weather;
    private int todaySessionCount;
    private int todayWeekFreeSlots;

    // ===== Buổi cần điểm danh =====
    private int attendanceWindowHours;
    private List<PtSessionView> attendanceSessions;

    // ===== Hoa hồng theo kỳ =====
    private String periodValue;
    private String periodNote;
    private List<PeriodOption> periodOptions;
    private List<CommissionLine> commissionLines;
    private long commissionTotal;
    private int commissionSessionCount;
    private int completedCount;
    private int missedCount;
    private int cancelledPaidCount;
    private int cancelledUnpaidCount;
    private String chartTitle;
    private List<ChartBar> chartBars;

    // ===== Buổi sắp tới =====
    private String fromDateValue;
    private String fromDateLabel;
    private boolean fromToday;
    private String dateError;
    private int rangeDays;
    private List<PtSessionView> upcomingSessions;

    // ===== Banner khách thiếu gói tập (CBR-66) =====
    private int missingMembershipCustomerCount;
    private String missingMembershipFirstDate;

    // ===== Cột phụ (theo ngày đang lọc) =====
    private List<PtClientView> clients;
    private int clientsEndingSoon;
    private String weekLabel;
    private int weekScheduled;
    private int weekTaught;
    private int weekMissed;
    private int weekCancelled;
    private Integer weekFreeSlots;
    private String monthLabel;
    private int monthEarnedCount;
    private long monthEarnedAmount;
    private int monthPendingCount;
    private long monthPendingAmount;
    private int monthExpectedCount;
    private long monthExpectedAmount;

    // ---------- Đầu trang ----------

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    /** @return họ tên đầy đủ, VD "Đinh Công Sơn" — dùng ở lời chào (tên người nước ngoài như "David Miller" không tách được tên gọi). */
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /** @return 2 chữ cái viết tắt cho avatar, VD "ĐS" (chữ đầu của họ + chữ đầu của tên). */
    public String getInitials() {
        return initials;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    /** @return tỷ lệ hoa hồng của PT (%), VD 70 — Employees.CommissionRate. */
    public int getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(int commissionRate) {
        this.commissionRate = commissionRate;
    }

    /** @return VD "Chào buổi chiều". */
    public String getGreeting() {
        return greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    /** @return VD "Thứ 4, 30/09/2026". */
    public String getTodayLabel() {
        return todayLabel;
    }

    public void setTodayLabel(String todayLabel) {
        this.todayLabel = todayLabel;
    }

    /** @return thẻ thời tiết, hoặc null khi không lấy được (JSP chỉ hiện lời chào). */
    public WeatherView getWeather() {
        return weather;
    }

    public void setWeather(WeatherView weather) {
        this.weather = weather;
    }

    /** @return số buổi của PT trong hôm nay (mọi trạng thái) — hiện ở lời chào. */
    public int getTodaySessionCount() {
        return todaySessionCount;
    }

    public void setTodaySessionCount(int todaySessionCount) {
        this.todaySessionCount = todaySessionCount;
    }

    /** @return slot trống từ hôm nay tới Chủ nhật tuần này — chip trên thẻ chào (luôn theo hôm nay). */
    public int getTodayWeekFreeSlots() {
        return todayWeekFreeSlots;
    }

    public void setTodayWeekFreeSlots(int todayWeekFreeSlots) {
        this.todayWeekFreeSlots = todayWeekFreeSlots;
    }

    // ---------- Buổi cần điểm danh ----------

    /** @return P-07 (giờ), VD 24. */
    public int getAttendanceWindowHours() {
        return attendanceWindowHours;
    }

    public void setAttendanceWindowHours(int attendanceWindowHours) {
        this.attendanceWindowHours = attendanceWindowHours;
    }

    /** @return buổi Scheduled đã tới giờ bắt đầu và còn trong cửa sổ điểm danh (BR-54). */
    public List<PtSessionView> getAttendanceSessions() {
        return attendanceSessions;
    }

    public void setAttendanceSessions(List<PtSessionView> attendanceSessions) {
        this.attendanceSessions = attendanceSessions;
    }

    // ---------- Hoa hồng ----------

    /** @return kỳ đang xem, VD "2026-09" hoặc "2026-Q3" — khớp value trong ô chọn kỳ. */
    public String getPeriodValue() {
        return periodValue;
    }

    public void setPeriodValue(String periodValue) {
        this.periodValue = periodValue;
    }

    /** @return VD "Tính tới hết 30/09 · 4 buổi" / "Đã chốt · 12 buổi" / "Tháng 07 – 09 · 16 buổi". */
    public String getPeriodNote() {
        return periodNote;
    }

    public void setPeriodNote(String periodNote) {
        this.periodNote = periodNote;
    }

    public List<PeriodOption> getPeriodOptions() {
        return periodOptions;
    }

    public void setPeriodOptions(List<PeriodOption> periodOptions) {
        this.periodOptions = periodOptions;
    }

    /** @return mỗi nguồn buổi 1 dòng (chỉ những nguồn có buổi). */
    public List<CommissionLine> getCommissionLines() {
        return commissionLines;
    }

    public void setCommissionLines(List<CommissionLine> commissionLines) {
        this.commissionLines = commissionLines;
    }

    public long getCommissionTotal() {
        return commissionTotal;
    }

    public void setCommissionTotal(long commissionTotal) {
        this.commissionTotal = commissionTotal;
    }

    /** @return VD "1.120.000đ". */
    public String getCommissionTotalText() {
        return MoneyUtil.formatMoney(commissionTotal);
    }

    /** @return số buổi được tính hoa hồng trong kỳ. */
    public int getCommissionSessionCount() {
        return commissionSessionCount;
    }

    public void setCommissionSessionCount(int commissionSessionCount) {
        this.commissionSessionCount = commissionSessionCount;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(int completedCount) {
        this.completedCount = completedCount;
    }

    public int getMissedCount() {
        return missedCount;
    }

    public void setMissedCount(int missedCount) {
        this.missedCount = missedCount;
    }

    /** @return số buổi hủy VẪN tính hoa hồng (PT hủy đã trả tiền mặt, khách hủy — CBR-81, CBR-82). */
    public int getCancelledPaidCount() {
        return cancelledPaidCount;
    }

    public void setCancelledPaidCount(int cancelledPaidCount) {
        this.cancelledPaidCount = cancelledPaidCount;
    }

    /** @return số buổi hủy KHÔNG tính hoa hồng (phòng đóng cửa CBR-57, gói PT hết hạn CBR-55). */
    public int getCancelledUnpaidCount() {
        return cancelledUnpaidCount;
    }

    public void setCancelledUnpaidCount(int cancelledUnpaidCount) {
        this.cancelledUnpaidCount = cancelledUnpaidCount;
    }

    /** @return VD "Theo tuần" hoặc "Theo tháng". */
    public String getChartTitle() {
        return chartTitle;
    }

    public void setChartTitle(String chartTitle) {
        this.chartTitle = chartTitle;
    }

    public List<ChartBar> getChartBars() {
        return chartBars;
    }

    public void setChartBars(List<ChartBar> chartBars) {
        this.chartBars = chartBars;
    }

    // ---------- Buổi sắp tới ----------

    /** @return ngày bắt đầu của khối Buổi sắp tới, dạng yyyy-MM-dd (value của input type="date"). */
    public String getFromDateValue() {
        return fromDateValue;
    }

    public void setFromDateValue(String fromDateValue) {
        this.fromDateValue = fromDateValue;
    }

    /** @return VD "Hôm nay, 30/09/2026" hoặc "Thứ 4, 23/09/2026". */
    public String getFromDateLabel() {
        return fromDateLabel;
    }

    public void setFromDateLabel(String fromDateLabel) {
        this.fromDateLabel = fromDateLabel;
    }

    /** @return true nếu ngày bắt đầu là hôm nay (ẩn nút "Hôm nay"). */
    public boolean isFromToday() {
        return fromToday;
    }

    public void setFromToday(boolean fromToday) {
        this.fromToday = fromToday;
    }

    /** @return lỗi ô ngày (để trống — MSG09), null nếu không lỗi. */
    public String getDateError() {
        return dateError;
    }

    public void setDateError(String dateError) {
        this.dateError = dateError;
    }

    /** @return số ngày tính từ ngày bắt đầu: 1 (chỉ ngày đó) / 3 / 7 / 30. */
    public int getRangeDays() {
        return rangeDays;
    }

    public void setRangeDays(int rangeDays) {
        this.rangeDays = rangeDays;
    }

    /** @return mọi buổi (đủ 4 trạng thái) trong khoảng đã lọc, sớm nhất lên đầu. */
    public List<PtSessionView> getUpcomingSessions() {
        return upcomingSessions;
    }

    public void setUpcomingSessions(List<PtSessionView> upcomingSessions) {
        this.upcomingSessions = upcomingSessions;
    }

    // ---------- Banner khách thiếu gói tập ----------

    /** @return số khách có buổi Scheduled trong 30 ngày tới rơi vào ngày không có gói tập. */
    public int getMissingMembershipCustomerCount() {
        return missingMembershipCustomerCount;
    }

    public void setMissingMembershipCustomerCount(int missingMembershipCustomerCount) {
        this.missingMembershipCustomerCount = missingMembershipCustomerCount;
    }

    /** @return ngày của buổi thiếu gói tập gần nhất, VD "02/10". */
    public String getMissingMembershipFirstDate() {
        return missingMembershipFirstDate;
    }

    public void setMissingMembershipFirstDate(String missingMembershipFirstDate) {
        this.missingMembershipFirstDate = missingMembershipFirstDate;
    }

    // ---------- Cột phụ ----------

    /** @return khách được giao cho PT vào ngày bắt đầu đang lọc (GBR-10). */
    public List<PtClientView> getClients() {
        return clients;
    }

    public void setClients(List<PtClientView> clients) {
        this.clients = clients;
    }

    /** @return số khách có gói PT hết hạn trong 7 ngày kể từ ngày đang lọc. */
    public int getClientsEndingSoon() {
        return clientsEndingSoon;
    }

    public void setClientsEndingSoon(int clientsEndingSoon) {
        this.clientsEndingSoon = clientsEndingSoon;
    }

    // ---------- Ô "Tuần" (tuần chứa ngày đang lọc) ----------

    /** @return VD "Tuần này · 28/09 – 04/10" hoặc "Tuần 21/09 – 27/09". */
    public String getWeekLabel() {
        return weekLabel;
    }

    public void setWeekLabel(String weekLabel) {
        this.weekLabel = weekLabel;
    }

    public int getWeekScheduled() {
        return weekScheduled;
    }

    public void setWeekScheduled(int weekScheduled) {
        this.weekScheduled = weekScheduled;
    }

    public int getWeekTaught() {
        return weekTaught;
    }

    public void setWeekTaught(int weekTaught) {
        this.weekTaught = weekTaught;
    }

    public int getWeekMissed() {
        return weekMissed;
    }

    public void setWeekMissed(int weekMissed) {
        this.weekMissed = weekMissed;
    }

    public int getWeekCancelled() {
        return weekCancelled;
    }

    public void setWeekCancelled(int weekCancelled) {
        this.weekCancelled = weekCancelled;
    }

    /** @return slot Free trong tuần, tính từ hôm nay trở đi; null nếu tuần đã qua (JSP ẩn dòng). */
    public Integer getWeekFreeSlots() {
        return weekFreeSlots;
    }

    public void setWeekFreeSlots(Integer weekFreeSlots) {
        this.weekFreeSlots = weekFreeSlots;
    }

    // ---------- Ô "Tháng" (tháng chứa ngày đang lọc) ----------

    /** @return VD "Tháng này · 09/2026" hoặc "Tháng 08/2026". */
    public String getMonthLabel() {
        return monthLabel;
    }

    public void setMonthLabel(String monthLabel) {
        this.monthLabel = monthLabel;
    }

    /** @return số buổi đã được tính hoa hồng trong tháng. */
    public int getMonthEarnedCount() {
        return monthEarnedCount;
    }

    public void setMonthEarnedCount(int monthEarnedCount) {
        this.monthEarnedCount = monthEarnedCount;
    }

    public void setMonthEarnedAmount(long monthEarnedAmount) {
        this.monthEarnedAmount = monthEarnedAmount;
    }

    /** @return hoa hồng đã chốt, VD "1.120.000đ". */
    public String getMonthEarnedText() {
        return MoneyUtil.formatMoney(monthEarnedAmount);
    }

    /** @return số buổi đã qua ngày nhưng vẫn Scheduled (chờ điểm danh / JOB-04). */
    public int getMonthPendingCount() {
        return monthPendingCount;
    }

    public void setMonthPendingCount(int monthPendingCount) {
        this.monthPendingCount = monthPendingCount;
    }

    public void setMonthPendingAmount(long monthPendingAmount) {
        this.monthPendingAmount = monthPendingAmount;
    }

    /** @return hoa hồng chờ chốt, VD "270.000đ". */
    public String getMonthPendingText() {
        return MoneyUtil.formatMoney(monthPendingAmount);
    }

    /** @return số buổi Scheduled từ hôm nay trở đi trong tháng. */
    public int getMonthExpectedCount() {
        return monthExpectedCount;
    }

    public void setMonthExpectedCount(int monthExpectedCount) {
        this.monthExpectedCount = monthExpectedCount;
    }

    public void setMonthExpectedAmount(long monthExpectedAmount) {
        this.monthExpectedAmount = monthExpectedAmount;
    }

    /** @return hoa hồng dự kiến, VD "560.000đ". */
    public String getMonthExpectedText() {
        return MoneyUtil.formatMoney(monthExpectedAmount);
    }
}
