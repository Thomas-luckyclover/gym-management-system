package com.gms.dto;

import java.time.LocalDate;

/**
 * Một dòng cảnh báo trong khối “Cần chú ý” trên PTDashboard, do PtDashboardService tạo.
 * Service đọc PtClientPackageView của DAO, lấy các giá trị cần hiển thị và chuẩn bị nhãn/lời nhắc.
 * DTO này giữ các field trực tiếp, không chứa object PtClientPackageView bên trong.
 * Servlet sẽ truyền List<PtAttentionView> bằng attribute "attentionItems" để JSP lặp từng item.
 * Cảnh báo membership/bảo lưu xét 14 ngày; Service gom cảnh báo trùng và giữ buổi gần nhất có vấn đề.
 */
public class PtAttentionView {

    // ===== 1. FIELD: PtDashboardService truyền đủ giá trị vào constructor =====

    // ----- 1.0. Mã để nút "Nhắc khách" gửi lên (Service kiểm tra lại, không tin form) -----
    private final int customerId;          // PtClientPackageView.getCustomerId()
    private final int ptSubscriptionId;    // PtClientPackageView.getPtSubscriptionId()
    private boolean reminded;              // hôm nay PT đã nhắc khách này chưa → nút thành "Đã nhắc hôm nay"

    // Service chọn membership/frozen theo frozenOnSessionDate, hoặc closedDay từ lịch nghỉ.
    // JSP chọn màu và nút xử lý; closedDay không gửi nhắc khách.
    private final String kind;

    // DAO đọc Sessions.SessionId của buổi ngày nghỉ; null với hai loại cảnh báo cũ.
    // JSP gắn data-session-id để hộp #ptSessionDialog nhận đúng buổi cần dời.
    private final Integer sessionId;

    // ----- 1.1. Service lấy nguyên giá trị từ DTO do DAO tạo -----

    // PtDashboardService: lấy từ PtClientPackageView.getCustomerName().
    // JSP đọc item.customerName, in bằng c:out, ví dụ “Nguyễn Văn A”.
    private final String customerName;

    // PtDashboardService: lấy từ PtClientPackageView.getPtEndDate().
    // JSP đọc item.ptEndDate, dùng gms:formatDate để in “Gói PT: đến 25/10/2026”.
    private final LocalDate ptEndDate;

    // PtDashboardService: lấy từ PtClientPackageView.getScheduledSessionCount().
    // JSP đọc item.scheduledSessionCount, in “Còn 6 buổi trong lịch”.
    private final int scheduledSessionCount;

    // PtDashboardService: lấy từ PtClientPackageView.getSessionDate() của buổi gây ra cảnh báo.
    // Sau khi gom cảnh báo trùng, giữ ngày gần nhất có vấn đề trong phạm vi 14 ngày.
    // JSP đọc item.sessionDate, định dạng dd/MM/yyyy nếu cần hiện ngày tập riêng.
    private final LocalDate sessionDate;

    // PtDashboardService: lấy từ PtClientPackageView.getMembershipEndDate(), có thể null.
    // JSP đọc item.membershipEndDate, dùng gms:formatDate để in hạn membership như “06/10/2026”.
    // Null thì không in ngày; tình trạng được giải thích bằng membershipStatusLabel.
    private final LocalDate membershipEndDate;

    // PtDashboardService: lấy từ PtClientPackageView.getFreezeEndDate(), có thể null.
    // JSP đọc item.freezeEndDate, dùng gms:formatDate để in “Bảo lưu đến 15/10/2026”.
    // Null thì không hiện dòng hạn bảo lưu.
    private final LocalDate freezeEndDate;

    // ----- 1.2. Service đổi dữ liệu gốc thành nhãn tiếng Việt -----

    // PtDashboardService: chọn từ PtClientPackageView.getPtSubscriptionType().
    // JSP đọc item.ptSubscriptionTypeLabel, in “PT cá nhân” hoặc “PT đôi”.
    private final String ptSubscriptionTypeLabel;

    // PtDashboardService: xét PtClientPackageView.getMembershipEndDate(),
    // isHasMembershipOnSessionDate(), isFrozenOnSessionDate(), getSessionDate() và ngày hiện tại giờ VN.
    // JSP đọc item.membershipStatusLabel, in “Đã hết hạn”, “Không bao phủ ngày tập” hoặc “Đang bảo lưu”.
    private final String membershipStatusLabel;

    // ----- 1.3. Service chọn cảnh báo và viết lời nhắc -----

    // PtDashboardService: chọn từ kết quả kiểm tra membership/bảo lưu hoặc hạn/số buổi của gói PT.
    // JSP đọc item.attentionTitle, in “Cần membership”, “Membership đang bảo lưu” hoặc “Gói PT sắp hết”.
    private final String attentionTitle;

    // PtDashboardService: viết từ loại cảnh báo và dữ liệu của PtClientPackageView.
    // Ngày trong câu được Service định dạng bằng DateTimeUtil.formatDate, theo dd/MM/yyyy.
    // JSP đọc item.attentionMessage, in lời nhắc như “Cần membership hợp lệ cho buổi ngày 12/10/2026.”
    private final String attentionMessage;

    // ===== 2. CONSTRUCTOR: nhận giá trị trực tiếp, cùng thứ tự với các field =====

    /**
     * Nhận dữ liệu hiển thị mà Service đã lấy/chọn từ PtClientPackageView.
     * @param customerName tên khách của cảnh báo
     * @param ptEndDate hạn gói PT
     * @param scheduledSessionCount số buổi chưa bắt đầu còn trong lịch của gói
     * @param sessionDate ngày buổi gần nhất gây ra cảnh báo
     * @param membershipEndDate hạn membership, có thể null
     * @param freezeEndDate hạn bảo lưu, có thể null
     * @param ptSubscriptionTypeLabel nhãn loại gói PT
     * @param membershipStatusLabel nhãn tình trạng membership
     * @param attentionTitle tiêu đề cảnh báo
     * @param attentionMessage lời nhắc cụ thể
     */
    public PtAttentionView(int customerId, int ptSubscriptionId, String customerName, LocalDate ptEndDate, int scheduledSessionCount,
            LocalDate sessionDate, LocalDate membershipEndDate, LocalDate freezeEndDate,
            String ptSubscriptionTypeLabel, String membershipStatusLabel,
            String attentionTitle, String attentionMessage) {
        this(customerId, ptSubscriptionId, customerName, ptEndDate, scheduledSessionCount,
                sessionDate, membershipEndDate, freezeEndDate, ptSubscriptionTypeLabel,
                membershipStatusLabel, attentionTitle, attentionMessage,
                freezeEndDate == null ? "membership" : "frozen", null);
    }

    /** Service truyền thêm loại cảnh báo và mã buổi; các field cũ giữ nguyên. */
    public PtAttentionView(int customerId, int ptSubscriptionId, String customerName, LocalDate ptEndDate,
            int scheduledSessionCount, LocalDate sessionDate, LocalDate membershipEndDate,
            LocalDate freezeEndDate, String ptSubscriptionTypeLabel, String membershipStatusLabel,
            String attentionTitle, String attentionMessage, String kind, Integer sessionId) {
        this.kind = kind;
        this.sessionId = sessionId;
        // B1: nhận các giá trị Service lấy từ DTO của DAO.
        this.customerId = customerId;
        this.ptSubscriptionId = ptSubscriptionId;
        this.customerName = customerName;
        this.ptEndDate = ptEndDate;
        this.scheduledSessionCount = scheduledSessionCount;
        this.sessionDate = sessionDate;
        this.membershipEndDate = membershipEndDate;
        this.freezeEndDate = freezeEndDate;

        // B2: nhận nhãn tiếng Việt do Service chuẩn bị.
        this.ptSubscriptionTypeLabel = ptSubscriptionTypeLabel;
        this.membershipStatusLabel = membershipStatusLabel;

        // B3: nhận tiêu đề và lời nhắc của một cảnh báo.
        this.attentionTitle = attentionTitle;
        this.attentionMessage = attentionMessage;
    }

    // ===== 3. GETTER: cùng thứ tự với field, JSP đọc trực tiếp từ item =====

    public int getCustomerId() { return customerId; }
    /** JSP phân biệt membership, frozen và closedDay. */
    public String getKind() { return kind; }

    /** Mã buổi để mở hộp dời; hai loại cảnh báo cũ nhận null. */
    public Integer getSessionId() { return sessionId; }
    public int getPtSubscriptionId() { return ptSubscriptionId; }
    public boolean isReminded() { return reminded; }
    public void setReminded(boolean reminded) { this.reminded = reminded; }
    // Không có setter vì Service truyền đủ dữ liệu khi tạo DTO.

    // ----- 3.1. Giá trị lấy từ DTO của DAO, tương ứng nhóm 1.1 -----

    /** JSP đọc item.customerName để in tên khách bằng c:out. */
    public String getCustomerName() {
        return customerName;
    }

    /** JSP đọc item.ptEndDate để in hạn gói PT. */
    public LocalDate getPtEndDate() {
        return ptEndDate;
    }

    /** JSP đọc item.scheduledSessionCount để in số buổi còn trong lịch. */
    public int getScheduledSessionCount() {
        return scheduledSessionCount;
    }

    /** JSP đọc item.sessionDate để in ngày buổi cần chú ý. */
    public LocalDate getSessionDate() {
        return sessionDate;
    }

    /** JSP đọc item.membershipEndDate; null thì không in ngày. */
    public LocalDate getMembershipEndDate() {
        return membershipEndDate;
    }

    /** JSP đọc item.freezeEndDate; null thì không hiện dòng hạn bảo lưu. */
    public LocalDate getFreezeEndDate() {
        return freezeEndDate;
    }

    // ----- 3.2. Nhãn tiếng Việt, tương ứng nhóm 1.2 -----

    /** JSP đọc item.ptSubscriptionTypeLabel để in loại gói PT. */
    public String getPtSubscriptionTypeLabel() {
        return ptSubscriptionTypeLabel;
    }

    /** JSP đọc item.membershipStatusLabel để in tình trạng membership. */
    public String getMembershipStatusLabel() {
        return membershipStatusLabel;
    }

    // ----- 3.3. Nội dung cảnh báo, tương ứng nhóm 1.3 -----

    /** JSP đọc item.attentionTitle để in tiêu đề cảnh báo. */
    public String getAttentionTitle() {
        return attentionTitle;
    }

    /** JSP đọc item.attentionMessage để in lời nhắc dưới thông tin gói. */
    public String getAttentionMessage() {
        return attentionMessage;
    }
}
