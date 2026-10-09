package com.gms.dto;

import com.gms.entity.Package;
import java.time.LocalDate;

/**
 * Dữ liệu của một buổi JOB-04. DAO tạo, Service đọc để quyết định kết quả.
 * Mỗi buổi có một DTO; gói đôi mang điều kiện của cả hai khách.
 * DTO không tự quyết định trạng thái và không đưa sang JSP.
 */
public class SessionAutoCloseData {

    // ===== 1. Buổi tập =====

    // DAO: Sessions.SessionId; Service dùng để cập nhật đúng buổi và ghi AuditLog.
    private final int sessionId;

    // DAO: TimeSlots.SlotDate; mọi điều kiện bên dưới đều xét theo ngày tập này.
    private final LocalDate sessionDate;

    // DAO: Subscriptions.SubscriptionType; Service dùng để phân biệt buổi một khách và gói đôi.
    private final Package.Type ptSubscriptionType;

    // ===== 2. Khách đứng tên gói (holder) =====

    // DAO: membership của Subscriptions.CustomerId bao phủ sessionDate; chưa tính DayPass.
    private final boolean holderHasMembership;

    // DAO: DayPass của Subscriptions.CustomerId có ngày hiệu lực bằng sessionDate.
    private final boolean holderHasDayPass;

    // DAO: FreezeRecords của holder bao phủ sessionDate; ngày kết thúc ưu tiên ActualEndDate.
    private final boolean holderFrozen;

    // ===== 3. Người tập cùng (partner), chỉ xét khi ptSubscriptionType là PT_COUPLE =====

    // DAO: membership của Orders.PartnerCustomerId bao phủ sessionDate; false khi không có partner.
    private final boolean partnerHasMembership;

    // DAO: DayPass của Orders.PartnerCustomerId có ngày hiệu lực bằng sessionDate; false nếu không có partner.
    private final boolean partnerHasDayPass;

    // DAO: FreezeRecords của partner bao phủ sessionDate; false khi không có partner.
    private final boolean partnerFrozen;

    // ===== 4. Khách có vào gym trong ngày tập =====

    // DAO: CheckIns trong sessionDate của holder hoặc partner; Service dùng sau bước xét quyền vào gym.
    private final boolean hasCheckInOnSessionDate;


    /** DAO truyền dữ liệu của một buổi; constructor chỉ gán, không tính trạng thái. */
    public SessionAutoCloseData(int sessionId, LocalDate sessionDate, Package.Type ptSubscriptionType,
            boolean holderHasMembership, boolean holderHasDayPass, boolean holderFrozen,
            boolean partnerHasMembership, boolean partnerHasDayPass, boolean partnerFrozen,
            boolean hasCheckInOnSessionDate) {
        this.sessionId = sessionId;
        this.sessionDate = sessionDate;
        this.ptSubscriptionType = ptSubscriptionType;

        this.holderHasMembership = holderHasMembership;
        this.holderHasDayPass = holderHasDayPass;
        this.holderFrozen = holderFrozen;

        this.partnerHasMembership = partnerHasMembership;
        this.partnerHasDayPass = partnerHasDayPass;
        this.partnerFrozen = partnerFrozen;

        this.hasCheckInOnSessionDate = hasCheckInOnSessionDate;
    }


    // ===== 5. Getter: Service đọc theo cùng thứ tự với các nhóm field =====

    /** Mã buổi dùng để cập nhật Sessions và ghi AuditLog. */
    public int getSessionId() {
        return sessionId;
    }

    /** Ngày tập dùng để xét gói, bảo lưu và check-in. */
    public LocalDate getSessionDate() {
        return sessionDate;
    }

    /** Loại gói PT; PT_COUPLE cần xét thêm partner. */
    public Package.Type getPtSubscriptionType() {
        return ptSubscriptionType;
    }

    /** Holder có membership bao phủ ngày tập, chưa xét bảo lưu. */
    public boolean hasHolderMembership() {
        return holderHasMembership;
    }

    /** Holder có DayPass đúng ngày tập; Service quyết định có áp dụng cho loại buổi này không. */
    public boolean hasHolderDayPass() {
        return holderHasDayPass;
    }

    /** Holder có bảo lưu bao phủ ngày tập. */
    public boolean isHolderFrozen() {
        return holderFrozen;
    }

    /** Partner có membership bao phủ ngày tập; chỉ dùng cho gói đôi. */
    public boolean hasPartnerMembership() {
        return partnerHasMembership;
    }

    /** Partner có DayPass đúng ngày tập; Service chỉ xét khi là gói đôi. */
    public boolean hasPartnerDayPass() {
        return partnerHasDayPass;
    }

    /** Partner có bảo lưu bao phủ ngày tập; chỉ dùng cho gói đôi. */
    public boolean isPartnerFrozen() {
        return partnerFrozen;
    }

    /** Có lượt vào gym cùng ngày của holder hoặc partner. */
    public boolean hasCheckInOnSessionDate() {
        return hasCheckInOnSessionDate;
    }
}
