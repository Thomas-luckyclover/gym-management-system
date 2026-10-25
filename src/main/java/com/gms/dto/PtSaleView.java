package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DỮ LIỆU MÀN BÁN GÓI PT (/receptionist/pt-sales), biến JSP "sale".
 *
 *   Ai tạo?   PtSaleService.prepare — mỗi lần Lễ tân bấm "Tiếp" / đổi lựa chọn (GET).
 *   Đi theo 4 bước; bước sau chỉ có dữ liệu khi bước trước đã xong:
 *     1. Khách          holder (+ partner nếu gói đôi)
 *     2. Gói, PT, ngày   packages / pts, packageName, ptName, startDate, endDate
 *     3. Giờ cố định     times
 *     4. Lịch dự kiến    days, sessionCount, sessionValue → nút "Xác nhận đã thu tiền"
 *   Câu lỗi của bước nào nằm ở biến lỗi của bước đó (holderError, partnerError, choiceError, planError).
 */
public class PtSaleView {

    // ===== Chữ Lễ tân đã nhập / chọn (giữ lại trên form) =====
    private String query;              // ô tìm người đứng tên, VD "KH-0001"
    private String partnerQuery;       // ô tìm người tập cùng (gói đôi)
    private Integer packageId;
    private Integer ptId;
    private LocalDate startDate;
    private LocalTime fixedTime;       // giờ cố định, VD 18:00

    // ===== Bước 1. Khách =====
    private PtSaleCustomer holder;
    private PtSaleCustomer partner;
    private String holderError;
    private String partnerError;

    // ===== Bước 2. Gói + PT + ngày bắt đầu =====
    private List<PtSaleOption> packages = new ArrayList<>();
    private List<PtSaleOption> pts = new ArrayList<>();
    private boolean couple;            // gói đang chọn là gói đôi
    private String packageName;
    private long price;                // giá đã gồm VAT
    private int months;
    private String ptName;
    private int commissionRate;
    private LocalDate endDate;         // = startDate + số tháng (GBR-03)
    private LocalDate minStart;        // giới hạn ô ngày bắt đầu: hôm nay
    private LocalDate maxStart;        //                            hôm nay + 30 ngày
    private String choiceError;

    // ===== Bước 3. Giờ cố định =====
    private List<PtSaleOption> times = new ArrayList<>();

    // ===== Bước 4. Lịch dự kiến =====
    private List<PtSaleDay> days = new ArrayList<>();
    private int sessionCount;          // số ngày có buổi
    private int skipCount;
    private long sessionValue;         // giá ÷ số buổi (DBR-59)
    private String planError;

    /** Đủ dữ liệu để hiện nút "Xác nhận đã thu tiền". */
    public boolean isReady() {
        return sessionCount > 0 && planError == null;
    }

    /** Số ngày cần ghi chú dưới lịch: không có buổi, hoặc có buổi nhưng PT cần dời. */
    public int getNoteCount() {
        int count = 0;
        for (PtSaleDay day : days) {
            if (!day.isSession() || day.getWarning() != null) {
                count++;
            }
        }
        return count;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getPartnerQuery() { return partnerQuery; }
    public void setPartnerQuery(String partnerQuery) { this.partnerQuery = partnerQuery; }

    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }

    public Integer getPtId() { return ptId; }
    public void setPtId(Integer ptId) { this.ptId = ptId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalTime getFixedTime() { return fixedTime; }
    public void setFixedTime(LocalTime fixedTime) { this.fixedTime = fixedTime; }

    public PtSaleCustomer getHolder() { return holder; }
    public void setHolder(PtSaleCustomer holder) { this.holder = holder; }

    public PtSaleCustomer getPartner() { return partner; }
    public void setPartner(PtSaleCustomer partner) { this.partner = partner; }

    public String getHolderError() { return holderError; }
    public void setHolderError(String holderError) { this.holderError = holderError; }

    public String getPartnerError() { return partnerError; }
    public void setPartnerError(String partnerError) { this.partnerError = partnerError; }

    public List<PtSaleOption> getPackages() { return packages; }
    public void setPackages(List<PtSaleOption> packages) { this.packages = packages; }

    public List<PtSaleOption> getPts() { return pts; }
    public void setPts(List<PtSaleOption> pts) { this.pts = pts; }

    public boolean isCouple() { return couple; }
    public void setCouple(boolean couple) { this.couple = couple; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public int getMonths() { return months; }
    public void setMonths(int months) { this.months = months; }

    public String getPtName() { return ptName; }
    public void setPtName(String ptName) { this.ptName = ptName; }

    public int getCommissionRate() { return commissionRate; }
    public void setCommissionRate(int commissionRate) { this.commissionRate = commissionRate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public LocalDate getMinStart() { return minStart; }
    public void setMinStart(LocalDate minStart) { this.minStart = minStart; }

    public LocalDate getMaxStart() { return maxStart; }
    public void setMaxStart(LocalDate maxStart) { this.maxStart = maxStart; }

    public String getChoiceError() { return choiceError; }
    public void setChoiceError(String choiceError) { this.choiceError = choiceError; }

    public List<PtSaleOption> getTimes() { return times; }
    public void setTimes(List<PtSaleOption> times) { this.times = times; }

    public List<PtSaleDay> getDays() { return days; }
    public void setDays(List<PtSaleDay> days) { this.days = days; }

    public int getSessionCount() { return sessionCount; }
    public void setSessionCount(int sessionCount) { this.sessionCount = sessionCount; }

    public int getSkipCount() { return skipCount; }
    public void setSkipCount(int skipCount) { this.skipCount = skipCount; }

    public long getSessionValue() { return sessionValue; }
    public void setSessionValue(long sessionValue) { this.sessionValue = sessionValue; }

    public String getPlanError() { return planError; }
    public void setPlanError(String planError) { this.planError = planError; }
}
