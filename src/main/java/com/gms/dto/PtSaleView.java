package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DỮ LIỆU CẢ MÀN BÁN GÓI PT (/receptionist/pt-sales) — JSP gọi là biến "sale".
 *
 *   AI TẠO?   PtSaleService (KHÔNG có constructor đầy đủ: Service tạo rỗng bằng newView rồi SET dần qua từng bước).
 *             DAO không tạo object này — DAO chỉ trả PtSaleCustomer / PackageRow / PtRow / HeldOrder, Service chép sang đây.
 *   AI ĐỌC?   PtSaleServlet.showPage đặt request.setAttribute("sale", view) → pt-sales.jsp + các file _pt-sale-*.jspf.
 *
 *   Mỗi field ghi:  hàm Service SET  →  chỗ IN ra màn (hoặc "không in": chỉ để dựng link / ô ẩn / tính toán).
 */
public class PtSaleView {

    // ===== A. Lựa chọn trên URL — newView đọc chữ thô rồi set; JSP in lại vào ô ẩn / link để không mất lựa chọn =====
    private Integer customerId;      // newView / loadHeld        → ô ẩn "customer" (form Giữ lịch), link "Đổi người tập cùng"   không in chữ
    private Integer partnerId;       // newView                   → ô ẩn "partner"                                              không in chữ
    private Integer packageId;       // newView / loadHeld        → radio gói đang chọn (checked), ô ẩn "package"               không in chữ
    private Integer ptId;            // newView / loadHeld        → radio PT đang chọn (checked), ô ẩn "pt"                     không in chữ
    private LocalDate startDate;     // newView (trống = ngày mai) / loadHeld (ngày khung giữ đầu) → ô "Ngày bắt đầu kỳ", "Kỳ"   IN
    private LocalTime[] weekTimes = new LocalTime[5];   // newView (t1..t5) / loadHeld: [0] = Thứ 2 … [4] = Thứ 6; null = Nghỉ   không in

    // ===== B. Khách (cột phải "Đơn bán") — loadHolder / loadPartner / loadHeld =====
    private String searchQuery;      // newView: chữ ?q= khi tắt JS               → ô tìm khách                                IN
    private List<PtSaleCustomer> searchResults = new ArrayList<>();  // loadHolder (tắt JS) → danh sách kết quả                    IN
    private PtSaleCustomer holder;   // loadHolder / loadHeld     → thẻ "Người đứng tên" (_sale-customer.jspf)                  IN
    private PtSaleCustomer partner;  // loadPartner / loadHeld    → thẻ "Người tập cùng" (gói đôi)                              IN
    private String holderError;      // loadHolder                → chữ đỏ dưới ô tìm khách                                     IN
    private String partnerError;     // loadPartner               → chữ dưới ô người tập cùng                                   IN

    // ===== C. Gói, PT, ngày — loadPackage / loadPt (hoặc loadHeld khi đang giữ) =====
    private List<PtSaleOption> packages = new ArrayList<>();  // loadPackage → các ô chọn gói                                    IN
    private List<PtSaleOption> pts = new ArrayList<>();       // loadPt      → các ô chọn PT (vòng tròn chữ tắt + tên)           IN
    private boolean couple;          // loadPackage: gói PTCouple → hiện ô "Người tập cùng"                                     không in chữ
    private String packageName;      // loadPackage / loadHeld    → "Gói" ở Đơn bán                                             IN
    private long price;              // loadPackage / loadHeld (giá chép trong đơn) → "Phải thu", "Đã nhận đủ …"               IN
    private int months;              // loadPackage / loadHeld    → "· 2 tháng" cạnh tiêu đề Gói & PT                           IN
    private String ptName;           // loadPt / loadHeld         → "PT" ở Đơn bán, hộp vàng "… chưa công bố khung rảnh"        IN
    private int commissionRate;      // loadPt: % hoa hồng PT     → hiện không in (pay đọc lại từ PtSaleDAO.findPt)             không in
    private LocalDate endDate;       // loadPt / loadHeld: start + số tháng → "Kỳ 03/11/2026 – 02/01/2027"; có endDate = xong bước 1  IN
    private LocalDate minStart;      // newView: hôm nay          → min của ô ngày + câu lỗi ngày                               IN
    private LocalDate maxStart;      // newView: hôm nay + 30     → max của ô ngày + câu lỗi ngày                               IN
    private String choiceError;      // loadPackage / loadPt      → chữ đỏ trong thẻ Gói & PT                                   IN

    // ===== D. Lịch tập — loadPlan (xem: planJson; giữ: days) =====
    private String planJson;         // loadPlan (lock = false): PtSalePlan → JSON, nhúng vào <script id="sale-plan-data">, pt-sale-plan.js vẽ   IN (qua JS)
    private List<PtSaleDay> days = new ArrayList<>();   // loadPlan (lock = true) / loadHeld → lưới "N buổi tập · Đang giữ"     IN
    private int sessionCount;        // loadPlan / loadHeld       → "24 buổi" ở tiêu đề lưới và "Buổi" ở Đơn bán                IN
    private int skipCount;           // (chưa hàm nào set, luôn 0) → dòng "N ngày không có buổi"                                IN nếu > 0
    private long sessionValue;       // loadPlan / loadHeld: giá ÷ số buổi → "… /buổi"                                          IN
    private String planError;        // loadPlan                  → chữ trong thẻ Lịch tập khi không dựng được lịch             IN
    private List<Integer> unpublishedWeekdays = new ArrayList<>();  // loadPlan: Thứ PT chưa công bố (1 = T2 … 5 = T6) → qua getUnpublishedText   IN

    // ===== E. Đơn đang giữ — loadHeld (trang ?order=) và loadPendingHolds (danh sách đầu màn) =====
    private Integer heldOrderId;     // loadHeld: ≠ null nghĩa là đang ở bước 4 (isHeld) → ô ẩn "order" của form thu tiền / hủy  không in
    private String heldCode;         // loadHeld                  → mã đơn "DH-2026-0012" ở Đơn bán                             IN
    private long holdSeconds;        // loadHeld: hạn − bây giờ   → đồng hồ "Giữ chỗ còn mm:ss" (common.js đếm)                 IN
    private List<PendingHold> pendingHolds = new ArrayList<>();  // loadPendingHolds → khung "Đơn đang giữ chỗ" + nút "Tiếp tục thu tiền"   IN

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
    public List<Integer> getUnpublishedWeekdays() { return unpublishedWeekdays; }
    public void setUnpublishedWeekdays(List<Integer> unpublishedWeekdays) { this.unpublishedWeekdays = unpublishedWeekdays; }
    /** "Thứ 3, Thứ 5" — cho hộp nhắc trên JSP; rỗng = PT đã công bố đủ T2–T6. */
    public String getUnpublishedText() {
        StringBuilder text = new StringBuilder();
        for (int weekday : unpublishedWeekdays) {
            text.append(text.length() == 0 ? "" : ", ").append("Thứ ").append(weekday + 1);
        }
        return text.toString();
    }
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
    public List<PendingHold> getPendingHolds() { return pendingHolds; }
    public void setPendingHolds(List<PendingHold> pendingHolds) { this.pendingHolds = pendingHolds; }

    /**
     * 1 đơn tại quầy đang giữ chỗ — 1 dòng trong khung "Đơn đang giữ chỗ" ở đầu màn.
     * Service tạo bằng constructor (loadPendingHolds) từ PtSaleDAO.findActiveHoldOrders.
     *   orderId → link ?order=  ·  orderCode → IN mã đơn  ·  secondsLeft → IN "còn mm:ss"
     */
    public static class PendingHold {
        private final int orderId;
        private final String orderCode;
        private final long secondsLeft;   // số giây giữ chỗ còn lại (đồng hồ đếm ngược)

        public PendingHold(int orderId, String orderCode, long secondsLeft) {
            this.orderId = orderId;
            this.orderCode = orderCode;
            this.secondsLeft = secondsLeft;
        }

        public int getOrderId() { return orderId; }
        public String getOrderCode() { return orderCode; }
        public long getSecondsLeft() { return secondsLeft; }
    }
}
