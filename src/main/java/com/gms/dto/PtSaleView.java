package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DỮ LIỆU MÀN BÁN GÓI PT (/receptionist/pt-sales), biến JSP "sale".
 *
 *   Ai tạo?   PtSaleService.prepare (GET) — đi lần lượt 4 bước, bước sau chỉ có khi bước trước xong:
 *     1. Khách            holder (+ partner nếu gói đôi). Tìm bằng ô gợi ý; không JS thì searchResults.
 *     2. Gói, PT, ngày     packages / pts → packageName, ptName, startDate, endDate
 *     3. Lịch tập          planJson (PtSalePlan: từng ngày × từng giờ có tập được không) → pt-sale-plan.js ghép lịch;
 *                          khi Giữ lịch: days = các buổi đã kiểm tra lại
 *     4. Giữ chỗ + thu tiền   heldOrderId / heldCode / holdSeconds khi đã bấm "Giữ lịch 15 phút"
 */
public class PtSaleView {

    // ===== Lựa chọn trên URL (giữ lại để dựng link / ô ẩn) =====
    private Integer customerId;
    private Integer partnerId;
    private Integer packageId;
    private Integer ptId;
    private LocalDate startDate;
    private LocalTime[] weekTimes = new LocalTime[5];   // lịch mẫu: [0] = Thứ 2 … [4] = Thứ 6; null = Nghỉ

    // ===== Bước 1 =====
    private String searchQuery;                          // chữ gõ khi tìm không dùng gợi ý (tắt JS)
    private List<PtSaleCustomer> searchResults = new ArrayList<>();
    private PtSaleCustomer holder;
    private PtSaleCustomer partner;
    private String holderError;
    private String partnerError;

    // ===== Bước 2 =====
    private List<PtSaleOption> packages = new ArrayList<>();
    private List<PtSaleOption> pts = new ArrayList<>();
    private boolean couple;
    private String packageName;
    private long price;
    private int months;
    private String ptName;
    private int commissionRate;
    private LocalDate endDate;
    private LocalDate minStart;
    private LocalDate maxStart;
    private String choiceError;

    // ===== Bước 3 =====
    private String planJson;                             // PtSalePlan dạng JSON (Gson đã thoát < > &, nhúng vào <script> an toàn)
    private List<PtSaleDay> days = new ArrayList<>();
    private int sessionCount;
    private int skipCount;
    private long sessionValue;
    private String planError;

    // ===== Bước 4: đơn đang giữ =====
    private Integer heldOrderId;
    private String heldCode;
    private long holdSeconds;                            // số giây giữ chỗ còn lại (đồng hồ đếm ngược)

    /** Đủ để bấm "Giữ lịch 15 phút". */
    public boolean isReady() {
        return heldOrderId == null && sessionCount > 0 && planError == null;
    }

    /** Đang giữ chỗ (bước 4). */
    public boolean isHeld() {
        return heldOrderId != null;
    }

    /** Giờ đã chọn của thứ i (1 = Thứ 2) dạng "18:00"; chưa chọn → "". JSP dùng cho ô ẩn t1..t5. */
    public String timeOf(int weekday) {
        LocalTime time = weekTimes[weekday - 1];
        return time == null ? "" : time.toString();
    }

    /** 5 giờ đã chọn dạng chuỗi (JSP lặp để in ô ẩn / link). */
    public List<String> getWeekTimeTexts() {
        List<String> texts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            texts.add(timeOf(i));
        }
        return texts;
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

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }
    public Integer getPartnerId() { return partnerId; }
    public void setPartnerId(Integer partnerId) { this.partnerId = partnerId; }
    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }
    public Integer getPtId() { return ptId; }
    public void setPtId(Integer ptId) { this.ptId = ptId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalTime[] getWeekTimes() { return weekTimes; }
    public void setWeekTimes(LocalTime[] weekTimes) { this.weekTimes = weekTimes; }
    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }
    public List<PtSaleCustomer> getSearchResults() { return searchResults; }
    public void setSearchResults(List<PtSaleCustomer> searchResults) { this.searchResults = searchResults; }
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
    public String getPlanJson() { return planJson; }
    public void setPlanJson(String planJson) { this.planJson = planJson; }
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
    public Integer getHeldOrderId() { return heldOrderId; }
    public void setHeldOrderId(Integer heldOrderId) { this.heldOrderId = heldOrderId; }
    public String getHeldCode() { return heldCode; }
    public void setHeldCode(String heldCode) { this.heldCode = heldCode; }
    public long getHoldSeconds() { return holdSeconds; }
    public void setHoldSeconds(long holdSeconds) { this.holdSeconds = holdSeconds; }
}
