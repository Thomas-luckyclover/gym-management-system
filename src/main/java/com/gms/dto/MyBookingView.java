package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DỮ LIỆU MÀN "GÓI CỦA TÔI" (/customer/bookings).
 *
 *   Ai tạo?   MyBookingService.getMyBookings (B1 new MyBookingView, các bước sau điền dần).
 *   Ai gửi?   MyBookingServlet.loadBookings → request.setAttribute("booking", view).
 *   Ai đọc?   my-bookings.jsp viết ${booking.xxx}; 2 jspf nhúng trong đó:
 *               _booking-package.jspf  nhúng ở my-bookings.jsp dòng 134 (tab Đang tập) và 142 (tab Đã kết thúc)
 *               _booking-groups.jspf   nhúng ở my-bookings.jsp dòng 504 (mục Cần để ý)
 *
 * Các field chia 3 nhóm:
 *   NHÓM 1. IN RA MÀN — dữ liệu DAO đọc từ DB (Service chỉ chia / chọn / gắn chữ, không tự tính ra)
 *   NHÓM 2. KHÔNG IN RA MÀN — chỉ dùng bên trong (Service tính)
 *   NHÓM 3. IN RA MÀN — Service tự tính (đếm, chọn, ghép chữ) từ dữ liệu DAO đã đọc
 *
 * Số dòng dưới đây là số dòng trong my-bookings.jsp nếu không ghi tên file khác.
 */
public class MyBookingView {

    // =================================================================================================================
    // NHÓM 1. IN RA MÀN — DAO ĐỌC TỪ DB
    // =================================================================================================================

    /**
     * Các gói PT ĐANG TẬP (cột trái, tab "Đang tập").
     *   Hiện:  số trên tab "Đang tập 1" (dòng 121); mỗi gói 1 thẻ "Gói PT 2 Tháng / PT Sơn · còn 10 buổi"
     *          (lặp ở dòng 133, thẻ vẽ ở _booking-package.jspf dòng 11–14); rỗng → "Bạn không có gói PT nào đang tập." (dòng 136)
     *   DAO:   MyPtSessionDAO.findMyPackages — bảng Subscriptions + Orders + Employees + Persons + Customers
     *          (+ đếm Sessions/TimeSlots để ra số buổi đã qua).
     *   Service: getMyBookings B2 chỉ CHIA: Subscriptions.Status = Active → vào list này; fillPackage gắn chữ loại gói, link Zalo.
     */
    private final List<MyPtPackageView> activePackages = new ArrayList<>();

    /**
     * Các gói PT ĐÃ KẾT THÚC (cột trái, tab "Đã kết thúc").
     *   Hiện:  số trên tab "Đã kết thúc 0" (dòng 124); thẻ gói lặp ở dòng 141 (vẽ ở _booking-package.jspf dòng 11–14);
     *          rỗng → "Chưa có gói nào kết thúc." (dòng 144)
     *   DAO:   như activePackages (cùng 1 câu SQL findMyPackages).
     *   Service: getMyBookings B2 — Status khác Active → vào list này.
     */
    private final List<MyPtPackageView> endedPackages = new ArrayList<>();

    /**
     * Gói đang xem ở cột phải. null = đang xem nhóm "Buổi lẻ" hoặc khách chưa có gì.
     *   Hiện:  JSP đặt sp = booking.selectedPackage (dòng 46), rồi dùng sp:
     *            dòng 229        tên gói to "Gói PT 2 Tháng"
     *            dòng 236–237    "PT cá nhân · 07/09/2026 – 07/11/2026"
     *            dòng 325–332    hộp lời mời: "hết hạn ngày …", "Muốn tập tiếp với PT …?"
     *            dòng 380        ô "hôm nay" trong lịch cố định chỉ tô khi gói còn tập (sp.active)
     *          _booking-package.jspf dòng 11: thẻ gói đang xem có viền cam (aria-current="page")
     *   DAO:   1 phần tử của activePackages / endedPackages (findMyPackages).
     *   Service: getMyBookings B4 — pickPackage CHỌN gói theo ?package= trên URL.
     */
    private MyPtPackageView selectedPackage;

    /**
     * Danh sách buổi của thứ đang xem ở cột phải.
     *   Hiện:  đang xem gói PT  → dải ô các buổi, mỗi buổi 1 ô màu (lặp ở dòng 430–440)
     *          đang xem buổi lẻ → danh sách "Thứ 7, 12/09/2026 · 09:00 – 10:30 · PT Sơn · ● Đã check-in" (lặp ở dòng 471)
     *   DAO:   MyPtSessionDAO.findSessionsOfPackage (gói PT) hoặc findSingleSessions (buổi lẻ)
     *          — bảng Sessions + TimeSlots + Subscriptions + Orders + Persons.
     *   Service: B5 fillDisplay gắn nhãn trạng thái ("Sắp tới", "Vắng"…) cho từng buổi;
     *            B6 markWeeks đánh dấu vạch tuần (gói PT) hoặc orderSingles xếp lại thứ tự (buổi lẻ).
     */
    private List<MyPtSessionRow> sessions = new ArrayList<>();

    /**
     * Giờ mở cửa, VD "05:30 – 22:00". null nếu không có hộp lời mời ra quầy.
     *   Hiện:  dòng 329, 333 — câu "Mở cửa 05:30 – 22:00." trong hộp lời mời gia hạn / đăng ký lại.
     *   DAO:   SystemConfigService.getString("P-19") → SystemConfigDAO — bảng SystemConfigs, ConfigKey = 'P-19'.
     *   Service: B7 fillCounterInfo chỉ đổi "05:30-22:00" thành "05:30 – 22:00", và chỉ điền khi lời mời bảo khách ra quầy.
     */
    private String openingHours;


    // =================================================================================================================
    // NHÓM 2. KHÔNG IN RA MÀN — SERVICE TÍNH, CHỈ DÙNG BÊN TRONG
    // =================================================================================================================

    /**
     * Còn bao nhiêu ngày nữa gói đang xem hết hạn. VD hôm nay 25/10, hết hạn 07/11 → 13.
     *   Hiện:  KHÔNG. Không dòng JSP nào đọc.
     *   Tính:  Service B6 fillPackageDetail = số ngày từ hôm nay tới Subscriptions.EndDate (lấy từ selectedPackage).
     *   Dùng:  Service B7 chooseCta: còn ≤ 14 ngày (RENEW_DAYS_LEFT) → hiện lời mời "gia hạn tại quầy".
     */
    private long daysLeft;

    /**
     * Câu tóm tắt lịch cố định, VD "T2 – T6 lúc 18:00". null nếu không thứ nào có giờ cố định.
     *   Hiện:  KHÔNG thấy bằng mắt. Dòng 378 đặt vào aria-label của 5 ô T2–T6 → chỉ trình đọc màn hình (cho người khiếm thị) đọc.
     *   Tính:  Service applyWeekTemplate → summarize.
     */
    private String templateSummary;


    // =================================================================================================================
    // NHÓM 3. IN RA MÀN — SERVICE TỰ TÍNH TỪ DỮ LIỆU DAO ĐÃ ĐỌC
    // =================================================================================================================

    // ---------- Cột trái ----------

    /**
     * Tổng số buổi lẻ, VD 5.
     *   Hiện:  số trên tab "Buổi lẻ 5" (dòng 127); có > 0 → thẻ "Tất cả buổi lẻ / 5 buổi · mỗi buổi đặt riêng" (dòng 150–153).
     *          Cũng dùng trong isNoData() bên dưới.
     *   Tính:  Service B3 = singles.size() (singles ← DAO findSingleSessions).
     */
    private int singleSessionCount;

    // ---------- Đầu cột phải ----------

    /**
     * true = khách đang xem nhóm "Buổi lẻ" (URL ?package=single).
     *   Hiện:  dòng 229 tiêu đề thành "Buổi lẻ"; dòng 151 thẻ "Tất cả buổi lẻ" có viền cam;
     *          dòng 234 ẩn dòng loại gói; dòng 361 chọn vẽ "Lịch cố định" (false) hay "Các buổi lẻ của bạn" (true).
     *   Tính:  Service B4 — pickPackage trả null và khách có buổi lẻ.
     */
    private boolean singleSelected;

    /**
     * "Buổi tới" = buổi Scheduled đầu tiên chưa kết thúc. null nếu không còn buổi nào.
     *   Hiện:  JSP đặt f = booking.focusSession (dòng 47), rồi:
     *            dòng 253–257   chữ to "Thứ 2, 26/10 · 18:00 – 19:30" + nhãn vàng "Cần Membership/DayPass" nếu có
     *            dòng 266–274   link "Xem trên lịch tập ›" mở đúng tuần + đúng buổi
     *   Tính:  Service B5 countSessions CHỌN 1 buổi trong sessions (buổi đó do DAO đọc).
     */
    private MyPtSessionRow focusSession;

    /**
     * Mã của "Buổi tới" (Sessions.SessionId), VD 107.
     *   Hiện:  không in chữ; dòng 438 so với từng ô → ô của buổi tới có viền đậm (class is-next).
     *   Tính:  Service B5 countSessions, cùng lúc với focusSession.
     */
    private Integer focusSessionId;

    /**
     * Còn mấy ngày tới "Buổi tới". 0 = hôm nay, 1 = ngày mai.
     *   Hiện:  dòng 251 — "Buổi tới · Hôm nay" (0) hoặc "Buổi tới · Ngày mai" (1); số khác thì không thêm chữ.
     *   Tính:  Service B5 countSessions = số ngày từ hôm nay tới TimeSlots.SlotDate của buổi tới.
     */
    private long daysUntilFocus;

    // ---------- Hộp lời mời (CTA): tối đa 1 hộp ----------

    /**
     * Loại lời mời: buyMembership | renewAtCounter | rejoin | ptPackage | null (không hiện hộp).
     *   Hiện:  JSP đặt cta = booking.ctaKind (dòng 48); dòng 304–355 vẽ hộp: icon, tiêu đề, nút
     *          ("Mua gói thành viên" hoặc "Gọi lễ tân").
     *   Tính:  Service B7 chooseCta, theo thứ tự ưu tiên ghi trong MyBookingService.
     */
    private String ctaKind;

    /**
     * Số buổi lẻ trong 30 ngày gần nhất (không tính buổi bị hủy), VD 3.
     *   Hiện:  dòng 338 — "Bạn đã đặt 3 buổi lẻ trong 30 ngày. Gói PT xếp sẵn lịch cố định…" (chỉ khi ctaKind = ptPackage).
     *   Tính:  Service B3 countRecentSingles. Đạt ≥ 3 buổi (SINGLE_SUGGEST_COUNT) thì chooseCta chọn ptPackage.
     */
    private int recentSingleCount;

    /**
     * Số hotline để ĐỌC, VD "1900 6868".
     *   Hiện:  dòng 350 — nút "Gọi lễ tân 1900 6868".
     *   Tính:  Service B7 fillCounterInfo lấy từ hằng số AppConstants.GYM_HOTLINE (không phải DB).
     */
    private String hotline;

    /**
     * Số hotline để BẤM GỌI, VD "19006868" (không dấu cách).
     *   Hiện:  dòng 349 — href="tel:19006868" của nút "Gọi lễ tân".
     *   Tính:  Service B7 fillCounterInfo lấy từ AppConstants.GYM_HOTLINE_TEL.
     */
    private String hotlineTel;

    // ---------- Lịch cố định T2–T6 (chỉ khi xem gói PT) ----------

    /**
     * 5 ô T2 → T6, mỗi ô 1 WeekdaySlot.
     *   Hiện:  dòng 379–391 — mỗi ô "T2 / 18:00 / 9 buổi"; giờ khác thì "giờ khác · 2 buổi";
     *          không cố định "~ không cố định"; không có buổi "— không có buổi".
     *   Tính:  Service applyWeekTemplate: mỗi thứ lấy giờ có nhiều buổi nhất trong sessions.
     */
    private List<WeekdaySlot> weekTemplate = new ArrayList<>();

    /**
     * Số buổi rơi đúng lịch cố định, VD 44.
     *   Hiện:  dòng 376 — "44/45 buổi theo lịch này" (44 = templateCount, 45 = totalCount).
     *   Tính:  Service applyWeekTemplate B5.
     */
    private int templateCount;

    /**
     * Thứ của hôm nay: 1 = T2 … 7 = CN.
     *   Hiện:  không in chữ; dòng 380 — ô của hôm nay trong 5 ô T2–T6 có gạch cam dưới (class is-today).
     *   Tính:  Service B1 = now.getDayOfWeek().
     */
    private int todayWeekday;

    /**
     * "Buổi khác giờ": các buổi lệch lịch cố định.
     *   Hiện:  dòng 399–411 — tiêu đề "Buổi khác giờ 2", mỗi dòng
     *          "Thứ 7, 12/09/2026 · 09:00 – 10:30 · Bạn đổi buổi · thay buổi T6 11/09 · ● Đã check-in".
     *   Tính:  Service applyWeekTemplate CHỌN các buổi lệch trong sessions;
     *          câu "Bạn đổi buổi · …" (moveNote) Service ghép từ MyBookingDAO.findMoves — bảng SessionMoveLogs.
     */
    private List<MyPtSessionRow> offPattern = new ArrayList<>();

    // ---------- Chú thích dải ô (5 số đếm) ----------
    // Tổng = Đã check-in + Vắng + Đã hủy + Sắp tới + Đang cập nhật. Tất cả do Service B5 countSessions đếm trong sessions.

    /**
     * Tổng số buổi.
     *   Hiện:  dòng 376 "…/45 buổi theo lịch này"; dòng 423 có > 0 mới vẽ dải ô; dòng 468 "12 buổi" (khi xem buổi lẻ).
     */
    private int totalCount;

    /** Số buổi Đã check-in. Hiện: dòng 447 — "■ Đã check-in 13" (chỉ hiện khi > 0). */
    private int checkedInCount;

    /** Số buổi Vắng. Hiện: dòng 448 — "× Vắng 6" (chỉ hiện khi > 0). */
    private int missedCount;

    /** Số buổi Đã hủy. Hiện: dòng 449 — "⊘ Đã hủy 1" (chỉ hiện khi > 0). */
    private int cancelledCount;

    /** Số buổi Đang cập nhật (đã hết giờ, chờ JOB-04 chốt). Hiện: dòng 450 — "⬚ Đang cập nhật 15" (chỉ hiện khi > 0). */
    private int pendingCount;

    /**
     * Số buổi còn lại = Hôm nay + Sắp tới + Cần chú ý.
     *   Hiện:  dòng 451 — "□ Sắp tới 10"; dòng 325 — "Gói còn 3 buổi, hết hạn ngày …" (hộp gia hạn).
     *   Dùng:  chooseCta: còn ≤ 3 buổi (RENEW_SESSIONS_LEFT) → lời mời gia hạn.
     */
    private int remainingCount;

    // ---------- Cần để ý ----------

    /**
     * Buổi SẮP TỚI mà khách chưa có Membership/DayPass (còn kịp mua).
     *   Hiện:  dòng 495–499 — mục thu gọn "▸ CẦN ĐỂ Ý ……… 10 buổi"; rỗng → ẩn cả mục.
     *   Tính:  Service B5 buildWatchList lọc trong sessions.
     */
    private List<MyPtSessionRow> watchList = new ArrayList<>();

    /**
     * watchList gom theo cùng lý do.
     *   Hiện:  dòng 503 đặt groups = booking.watchGroups → _booking-groups.jspf dòng 10–24
     *          (nhúng ở my-bookings.jsp dòng 504): mỗi nhóm "● Cần chú ý · 10 buổi · <lý do>" + hàng chip ngày "T2 26/10".
     *   Tính:  Service B5 groupByReason.
     */
    private List<SessionGroup> watchGroups = new ArrayList<>();


    // =================================================================================================================
    // HÀM TÍNH SẴN CHO JSP
    // =================================================================================================================

    /**
     * Khách chưa có gói PT và chưa có buổi lẻ nào?
     *   Hiện:  dòng 90 — true thì cả màn chỉ còn "Bạn chưa có gói PT hoặc buổi lẻ" + nút "Đặt buổi lẻ".
     *   (Không đặt tên isEmpty vì "empty" là từ khóa trong EL.)
     */
    public boolean isNoData() {
        return activePackages.isEmpty() && endedPackages.isEmpty() && singleSessionCount == 0;
    }


    // =================================================================================================================
    // GETTER / SETTER — chỉ đọc và gán, không có nghiệp vụ. Xếp theo đúng thứ tự 3 nhóm ở trên.
    // =================================================================================================================

    // ----- Nhóm 1 -----
    public List<MyPtPackageView> getActivePackages() { return activePackages; }
    public List<MyPtPackageView> getEndedPackages() { return endedPackages; }

    public MyPtPackageView getSelectedPackage() { return selectedPackage; }
    public void setSelectedPackage(MyPtPackageView selectedPackage) { this.selectedPackage = selectedPackage; }

    public List<MyPtSessionRow> getSessions() { return sessions; }
    public void setSessions(List<MyPtSessionRow> sessions) { this.sessions = sessions; }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) { this.openingHours = openingHours; }

    // ----- Nhóm 2 -----
    public long getDaysLeft() { return daysLeft; }
    public void setDaysLeft(long daysLeft) { this.daysLeft = daysLeft; }

    public String getTemplateSummary() { return templateSummary; }
    public void setTemplateSummary(String templateSummary) { this.templateSummary = templateSummary; }

    // ----- Nhóm 3: cột trái + đầu cột phải -----
    public int getSingleSessionCount() { return singleSessionCount; }
    public void setSingleSessionCount(int singleSessionCount) { this.singleSessionCount = singleSessionCount; }

    public boolean isSingleSelected() { return singleSelected; }
    public void setSingleSelected(boolean singleSelected) { this.singleSelected = singleSelected; }

    public MyPtSessionRow getFocusSession() { return focusSession; }
    public void setFocusSession(MyPtSessionRow focusSession) { this.focusSession = focusSession; }

    public Integer getFocusSessionId() { return focusSessionId; }
    public void setFocusSessionId(Integer focusSessionId) { this.focusSessionId = focusSessionId; }

    public long getDaysUntilFocus() { return daysUntilFocus; }
    public void setDaysUntilFocus(long daysUntilFocus) { this.daysUntilFocus = daysUntilFocus; }

    // ----- Nhóm 3: hộp lời mời -----
    public String getCtaKind() { return ctaKind; }
    public void setCtaKind(String ctaKind) { this.ctaKind = ctaKind; }

    public int getRecentSingleCount() { return recentSingleCount; }
    public void setRecentSingleCount(int recentSingleCount) { this.recentSingleCount = recentSingleCount; }

    public String getHotline() { return hotline; }
    public void setHotline(String hotline) { this.hotline = hotline; }

    public String getHotlineTel() { return hotlineTel; }
    public void setHotlineTel(String hotlineTel) { this.hotlineTel = hotlineTel; }

    // ----- Nhóm 3: lịch cố định -----
    public List<WeekdaySlot> getWeekTemplate() { return weekTemplate; }
    public void setWeekTemplate(List<WeekdaySlot> weekTemplate) { this.weekTemplate = weekTemplate; }

    public int getTemplateCount() { return templateCount; }
    public void setTemplateCount(int templateCount) { this.templateCount = templateCount; }

    public int getTodayWeekday() { return todayWeekday; }
    public void setTodayWeekday(int todayWeekday) { this.todayWeekday = todayWeekday; }

    public List<MyPtSessionRow> getOffPattern() { return offPattern; }
    public void setOffPattern(List<MyPtSessionRow> offPattern) { this.offPattern = offPattern; }

    // ----- Nhóm 3: số đếm -----
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getCheckedInCount() { return checkedInCount; }
    public void setCheckedInCount(int checkedInCount) { this.checkedInCount = checkedInCount; }

    public int getMissedCount() { return missedCount; }
    public void setMissedCount(int missedCount) { this.missedCount = missedCount; }

    public int getCancelledCount() { return cancelledCount; }
    public void setCancelledCount(int cancelledCount) { this.cancelledCount = cancelledCount; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getRemainingCount() { return remainingCount; }
    public void setRemainingCount(int remainingCount) { this.remainingCount = remainingCount; }

    // ----- Nhóm 3: cần để ý -----
    public List<MyPtSessionRow> getWatchList() { return watchList; }
    public void setWatchList(List<MyPtSessionRow> watchList) { this.watchList = watchList; }

    public List<SessionGroup> getWatchGroups() { return watchGroups; }
    public void setWatchGroups(List<SessionGroup> watchGroups) { this.watchGroups = watchGroups; }
}
