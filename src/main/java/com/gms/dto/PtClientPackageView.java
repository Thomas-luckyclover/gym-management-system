package com.gms.dto;

import com.gms.entity.Package;
import java.time.LocalDate;

/**
 * Dữ liệu gói PT và membership của một khách tại ngày tập đang được kiểm tra.
 * PtDashboardDAO.findClientPackages(...) tạo DTO từ khách, gói PT, buổi tập, membership và kỳ bảo lưu.
 * Một object ứng với một khách + một gói PT + một ngày tập.
 * Gói đôi có dữ liệu riêng của từng người, dùng chung ptSubscriptionId và sessionDate.
 * Phạm vi kiểm tra membership/bảo lưu: 14 ngày, từ hôm nay đến hôm nay + 13 ngày theo giờ VN.
 * Phạm vi này riêng với khối Lịch 7 ngày trên dashboard.
 * PtDashboardService đọc DTO này để quyết định có tạo PtAttentionView hay không.
 * DTO không chứa tiêu đề cảnh báo và không tự quyết định mốc gói sắp hết.
 */
public class PtClientPackageView {

    // ===== 1. FIELD: PtDashboardDAO đọc hoặc tổng hợp trong truy vấn, gán qua constructor =====

    // ----- 1.1. Khách và gói PT -----

    // PtDashboardDAO: Customers.CustomerId của khách đang xét, là holder hoặc partner của gói đôi.
    // PtDashboardService dùng để biết cảnh báo thuộc khách nào; không in mã lên màn hình.
    private final int customerId;

    // PtDashboardDAO: Persons.FullName qua Customers.PersonId của customerId bên trên.
    // JSP đọc qua DTO cảnh báo và in tên bằng c:out, ví dụ “Vũ Hoài Nam”.
    private final String customerName;

    // PtDashboardDAO: Subscriptions.SubscriptionId của gói PT đang xét, không phải Packages.PackageId.
    // PtDashboardService dùng để phân biệt khi một khách có nhiều gói PT và ghép hai người của gói đôi.
    // Không in mã thành chữ trên màn hình.
    private final int ptSubscriptionId;

    // PtDashboardDAO: Subscriptions.SubscriptionType của gói PT, đổi bằng Package.Type.fromDbValue.
    // PtDashboardService phân biệt PT_SINGLE / PT_COUPLE và chuẩn bị nhãn “PT cá nhân” / “PT đôi”.
    // JSP hiện nhãn Service tạo, không in tên enum.
    private final Package.Type ptSubscriptionType;

    // PtDashboardDAO: Subscriptions.EndDate của ptSubscriptionId, là ngày cuối gói PT còn hiệu lực.
    // JSP dùng gms:formatDate để hiện “Gói PT: đến 25/10/2026”.
    private final LocalDate ptEndDate;

    // PtDashboardDAO: đếm các dòng Sessions bằng COUNT(*), không đọc từ một cột lưu sẵn số buổi còn lại.
    // Lọc Sessions.SubscriptionId = ptSubscriptionId và Sessions.Status = Scheduled.
    // Nối Sessions.TimeSlotId = TimeSlots.TimeSlotId để lấy TimeSlots.SlotDate và TimeSlots.StartTime.
    // Chỉ đếm khi SlotDate + StartTime sau now, với now do PtDashboardService truyền theo giờ VN.
    // Đếm trong truy vấn riêng theo gói, không giới hạn phạm vi cảnh báo 14 ngày và không nhân dòng khi JOIN khách gói đôi.
    // Không lấy Subscriptions.GeneratedSessionCount trừ số buổi đã chốt.
    // JSP hiện “còn 6 buổi trong lịch”; hai người gói đôi cùng có số buổi của gói đó.
    private final int scheduledSessionCount;

    // ----- 1.2. Ngày tập và dữ liệu membership của chính khách này -----

    // PtDashboardDAO: TimeSlots.SlotDate của buổi đang xét trong phạm vi cảnh báo 14 ngày.
    // PtDashboardService truyền khoảng từ today đến today.plusDays(13), tính cả hai ngày biên, theo giờ VN.
    // PtDashboardService kiểm tra membership/bảo lưu theo ngày này, không chỉ so sánh hai hạn gói.
    // JSP có thể hiện trong lời nhắc, ví dụ “Cần membership cho buổi 12/10/2026”.
    private final LocalDate sessionDate;

    // PtDashboardDAO: đọc Subscriptions.EndDate của membership được chọn cho sessionDate.
    // Lọc Subscriptions.CustomerId = customerId và Subscriptions.SubscriptionType = Membership.
    // Ưu tiên Subscriptions.Status Active/Frozen, có Subscriptions.StartDate <= sessionDate <= Subscriptions.EndDate.
    // Nếu không có gói bao phủ, lấy hạn gần nhất đã qua của membership Active/Frozen/Expired; không tính DayPass.
    // Null khi không có membership thuộc hai trường hợp trên.
    // JSP hiện dd/MM/yyyy; Service chọn câu “còn hạn”, “đã hết hạn” hoặc “chưa có gói phù hợp”.
    private final LocalDate membershipEndDate;

    // PtDashboardDAO: EXISTS trên Subscriptions, lọc CustomerId = customerId, SubscriptionType = Membership.
    // Kiểm tra Subscriptions.Status Active/Frozen và Subscriptions.StartDate <= sessionDate <= Subscriptions.EndDate.
    // Xét cả gói đã mua nối tiếp; không dùng membershipEndDate đơn lẻ để kết luận đủ gói.
    // Không in true/false; Service dùng cùng frozenOnSessionDate để chọn cảnh báo và tình trạng membership.
    private final boolean hasMembershipOnSessionDate;

    // PtDashboardDAO: EXISTS trên FreezeRecords, nối FreezeRecords.SubscriptionId = Subscriptions.SubscriptionId.
    // Lọc Subscriptions.CustomerId = customerId và Subscriptions.SubscriptionType = Membership.
    // Kiểm tra FreezeRecords.StartDate <= sessionDate <= COALESCE(FreezeRecords.ActualEndDate, FreezeRecords.PlannedEndDate).
    // Không in true/false; Service phân biệt bảo lưu với thiếu/hết membership.
    private final boolean frozenOnSessionDate;

    // PtDashboardDAO: MAX(COALESCE(FreezeRecords.ActualEndDate, FreezeRecords.PlannedEndDate)).
    // Nối FreezeRecords.SubscriptionId = Subscriptions.SubscriptionId, lọc membership của customerId.
    // Chỉ lấy kỳ bảo lưu có FreezeRecords.StartDate <= sessionDate <= ngày kết thúc tính bằng COALESCE trên.
    // Null khi không có kỳ bảo lưu bao phủ ngày này.
    // JSP dùng gms:formatDate để hiện “Bảo lưu đến 15/10/2026”.
    private final LocalDate freezeEndDate;

    // ===== 2. CONSTRUCTOR: PtDashboardDAO truyền dữ liệu của một khách, một gói PT, một ngày tập =====

    /**
     * Nhận dữ liệu đã đọc từ một dòng truy vấn; Service sẽ đọc qua getter.
     * @param customerId mã khách đang xét
     * @param customerName tên khách đang xét
     * @param ptSubscriptionId mã gói PT khách đã mua
     * @param ptSubscriptionType loại gói PT cá nhân hoặc đôi
     * @param ptEndDate ngày cuối gói PT còn hiệu lực
     * @param scheduledSessionCount số buổi Scheduled chưa bắt đầu của cả gói
     * @param sessionDate ngày tập trong phạm vi cảnh báo 14 ngày, dùng kiểm tra membership và bảo lưu
     * @param membershipEndDate hạn membership được chọn, có thể null
     * @param hasMembershipOnSessionDate có membership Active/Frozen bao phủ ngày tập hay không
     * @param frozenOnSessionDate có kỳ bảo lưu bao phủ ngày tập hay không
     * @param freezeEndDate ngày cuối kỳ bảo lưu bao phủ ngày tập, có thể null
     */
    public PtClientPackageView(int customerId, String customerName, int ptSubscriptionId,
            Package.Type ptSubscriptionType, LocalDate ptEndDate, int scheduledSessionCount,
            LocalDate sessionDate, LocalDate membershipEndDate, boolean hasMembershipOnSessionDate,
            boolean frozenOnSessionDate, LocalDate freezeEndDate) {
        // B1: nhận thông tin khách và gói PT.
        this.customerId = customerId;
        this.customerName = customerName;
        this.ptSubscriptionId = ptSubscriptionId;
        this.ptSubscriptionType = ptSubscriptionType;
        this.ptEndDate = ptEndDate;
        this.scheduledSessionCount = scheduledSessionCount;

        // B2: nhận ngày tập và tình trạng membership/bảo lưu tại ngày đó.
        this.sessionDate = sessionDate;
        this.membershipEndDate = membershipEndDate;
        this.hasMembershipOnSessionDate = hasMembershipOnSessionDate;
        this.frozenOnSessionDate = frozenOnSessionDate;
        this.freezeEndDate = freezeEndDate;
    }

    // ===== 3. GETTER: cùng thứ tự với field; DTO dữ liệu gốc không có setter =====

    // ----- 3.1. Khách và gói PT, tương ứng nhóm 1.1 -----

    /** Mã khách để Service nhận biết người có cảnh báo. */
    public int getCustomerId() {
        return customerId;
    }

    /** Service lấy tên này truyền vào PtAttentionView.customerName để JSP đọc trực tiếp. */
    public String getCustomerName() {
        return customerName;
    }

    /** Mã gói PT để phân biệt các gói và ghép dữ liệu của hai người gói đôi. */
    public int getPtSubscriptionId() {
        return ptSubscriptionId;
    }

    /** Loại gói để Service phân biệt cá nhân/đôi và chọn nhãn hiển thị. */
    public Package.Type getPtSubscriptionType() {
        return ptSubscriptionType;
    }

    /** Hạn gói PT để JSP định dạng dd/MM/yyyy. */
    public LocalDate getPtEndDate() {
        return ptEndDate;
    }

    /** Số buổi còn trong lịch của cả gói để JSP in cạnh hạn gói PT. */
    public int getScheduledSessionCount() {
        return scheduledSessionCount;
    }

    // ----- 3.2. Ngày tập và membership, tương ứng nhóm 1.2 -----

    /** Ngày tập làm mốc kiểm tra đủ membership và bảo lưu. */
    public LocalDate getSessionDate() {
        return sessionDate;
    }

    /** Hạn membership được chọn, null nếu không có gói phù hợp để hiển thị. */
    public LocalDate getMembershipEndDate() {
        return membershipEndDate;
    }

    /** Service kiểm tra membership có bao phủ ngày tập, chưa xét bảo lưu. */
    public boolean isHasMembershipOnSessionDate() {
        return hasMembershipOnSessionDate;
    }

    /** Service kiểm tra khách có đang bảo lưu vào ngày tập hay không. */
    public boolean isFrozenOnSessionDate() {
        return frozenOnSessionDate;
    }

    /** Ngày cuối kỳ bảo lưu để JSP hiện sau chữ “Bảo lưu đến”. */
    public LocalDate getFreezeEndDate() {
        return freezeEndDate;
    }
}
