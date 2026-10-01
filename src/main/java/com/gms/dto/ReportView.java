package com.gms.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu màn Báo cáo vận hành & doanh thu của Manager (SRS 12.1.1, UC-46).
 * Là "ảnh chụp" tại thời điểm xem (generatedAt), không cập nhật thời gian thực.
 * Tiền luôn là long (đồng) — GBR-02. Implements Serializable vì được giữ trong HttpSession để xuất CSV đúng số đang xem.
 */
public class ReportView implements Serializable {

    private static final long serialVersionUID = 1L;

    private final LocalDate from;
    private final LocalDate to;
    private final LocalDateTime generatedAt;

    // ----- thẻ tổng quan -----
    private long totalRevenue;          // DBR-56, đã gồm VAT
    private long totalVat;              // tổng VAT nằm trong doanh thu
    private long totalCommission;       // tổng hoa hồng PT
    private int activeMembers;          // khách đang Active (ảnh chụp lúc xem, không theo khoảng ngày)
    private int newMembers;             // khách mới vào trong khoảng ngày (đã rời trạng thái New)
    private int runningPackages;        // gói tập Membership đang Active (không gồm vé ngày)
    private long dayPassVisits;         // lượt check-in bằng vé 1 ngày
    private long dayPassRevenue;        // doanh thu vé 1 ngày
    private long checkInCount;          // tổng lượt check-in (gồm cả vé 1 ngày)

    // ----- bảng và biểu đồ -----
    private List<PackageRow> packages = new ArrayList<>();
    private List<PtRow> ptRows = new ArrayList<>();
    private List<Bucket> buckets = new ArrayList<>();
    private List<Tick> ticks = new ArrayList<>();
    private boolean isMonthlyChart;       // true khi khoảng ngày dài → gộp cột theo tháng

    public ReportView(LocalDate from, LocalDate to, LocalDateTime generatedAt) {
        this.from = from;
        this.to = to;
        this.generatedAt = generatedAt;
    }

    // ===== Dòng bảng / cột biểu đồ =====

    /** 1 dòng "Doanh thu theo gói". */
    public static class PackageRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int packageId;
        private final String packageName;
        private final String packageType;
        private final long orderCount;
        private final long revenue;
        private final long vat;

        public PackageRow(int packageId, String packageName, String packageType,
                          long orderCount, long revenue, long vat) {
            this.packageId = packageId;
            this.packageName = packageName;
            this.packageType = packageType;
            this.orderCount = orderCount;
            this.revenue = revenue;
            this.vat = vat;
        }

        public int getPackageId() { return packageId; }
        public String getPackageName() { return packageName; }
        public String getPackageType() { return packageType; }
        public long getOrderCount() { return orderCount; }
        public long getRevenue() { return revenue; }
        public long getVat() { return vat; }
    }

    /** 1 dòng "Hoa hồng PT". Hủy = khách tự hủy + PT hủy. */
    public static class PtRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private final int employeeId;
        private final String fullName;
        private final int commissionRate;
        private final long completed;
        private final long missed;
        private final long cancelled;
        private final long commission;

        public PtRow(int employeeId, String fullName, int commissionRate,
                     long completed, long missed, long cancelled, long commission) {
            this.employeeId = employeeId;
            this.fullName = fullName;
            this.commissionRate = commissionRate;
            this.completed = completed;
            this.missed = missed;
            this.cancelled = cancelled;
            this.commission = commission;
        }

        public int getEmployeeId() { return employeeId; }
        public String getFullName() { return fullName; }
        public int getCommissionRate() { return commissionRate; }
        public long getCompleted() { return completed; }
        public long getMissed() { return missed; }
        public long getCancelled() { return cancelled; }
        public long getCommission() { return commission; }
    }

    /** 1 cột biểu đồ (1 ngày, hoặc 1 tháng khi khoảng ngày dài). */
    public static class Bucket implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String label;        // nhãn ngắn dưới cột, VD "5/9" hoặc "09/2026"
        private final String fullLabel;    // nhãn đầy đủ cho tooltip và CSV, VD "05/09/2026"
        private final long amount;
        private double percent;        // chiều cao cột, % so với trục Y
        private boolean isLabelVisible;     // chỉ hiện nhãn ở một số cột cho khỏi chen chúc
        private boolean isLast;

        public Bucket(String label, String fullLabel, long amount) {
            this.label = label;
            this.fullLabel = fullLabel;
            this.amount = amount;
        }

        public String getLabel() { return label; }
        public String getFullLabel() { return fullLabel; }
        public long getAmount() { return amount; }
        public double getPercent() { return percent; }
        public boolean isLabelVisible() { return isLabelVisible; }
        public boolean isLast() { return isLast; }
        public void setPercent(double percent) { this.percent = percent; }
        public void setLabelVisible(boolean isLabelVisible) { this.isLabelVisible = isLabelVisible; }
        public void setLast(boolean isLast) { this.isLast = isLast; }
    }

    /** 1 vạch kẻ ngang của biểu đồ. */
    public static class Tick implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String label;
        private final double percent;

        public Tick(String label, double percent) {
            this.label = label;
            this.percent = percent;
        }

        public String getLabel() { return label; }
        public double getPercent() { return percent; }
    }

    // ===== Chuỗi hiển thị sẵn cho thẻ tổng quan (VD 246300000 → "246,3 tr") =====

    public String getTotalRevenueText() { return formatMillionsText(totalRevenue); }
    public String getTotalCommissionText() { return formatMillionsText(totalCommission); }
    public String getDayPassRevenueText() { return formatMillionsText(dayPassRevenue); }

    /** Đổi số đồng sang "x,y tr" (1 chữ số thập phân, dấu phẩy kiểu Việt). */
    private static String formatMillionsText(long amount) {
        long tenths = Math.round(amount / 100_000.0);
        return (tenths / 10) + "," + (tenths % 10) + " tr";
    }

    /** Không có gì để hiển thị trong khoảng ngày (không doanh thu, check-in, hoa hồng). */
    public boolean isEmptyInRange() {
        return totalRevenue == 0 && checkInCount == 0 && ptRows.isEmpty();
    }

    // ===== Getter / setter =====

    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public long getTotalRevenue() { return totalRevenue; }
    public long getTotalVat() { return totalVat; }
    public long getTotalCommission() { return totalCommission; }
    public int getActiveMembers() { return activeMembers; }
    public int getNewMembers() { return newMembers; }
    public int getRunningPackages() { return runningPackages; }
    public long getDayPassVisits() { return dayPassVisits; }
    public long getDayPassRevenue() { return dayPassRevenue; }
    public long getCheckInCount() { return checkInCount; }
    public List<PackageRow> getPackages() { return packages; }
    public List<PtRow> getPtRows() { return ptRows; }
    public List<Bucket> getBuckets() { return buckets; }
    public List<Tick> getTicks() { return ticks; }
    public boolean isMonthlyChart() { return isMonthlyChart; }

    public void setTotalRevenue(long totalRevenue) { this.totalRevenue = totalRevenue; }
    public void setTotalVat(long totalVat) { this.totalVat = totalVat; }
    public void setTotalCommission(long totalCommission) { this.totalCommission = totalCommission; }
    public void setActiveMembers(int activeMembers) { this.activeMembers = activeMembers; }
    public void setNewMembers(int newMembers) { this.newMembers = newMembers; }
    public void setRunningPackages(int runningPackages) { this.runningPackages = runningPackages; }
    public void setDayPassVisits(long dayPassVisits) { this.dayPassVisits = dayPassVisits; }
    public void setDayPassRevenue(long dayPassRevenue) { this.dayPassRevenue = dayPassRevenue; }
    public void setCheckInCount(long checkInCount) { this.checkInCount = checkInCount; }
    public void setPackages(List<PackageRow> packages) { this.packages = packages; }
    public void setPtRows(List<PtRow> ptRows) { this.ptRows = ptRows; }
    public void setBuckets(List<Bucket> buckets) { this.buckets = buckets; }
    public void setTicks(List<Tick> ticks) { this.ticks = ticks; }
    public void setMonthlyChart(boolean isMonthlyChart) { this.isMonthlyChart = isMonthlyChart; }
}