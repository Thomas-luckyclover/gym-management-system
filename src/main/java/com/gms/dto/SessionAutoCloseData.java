package com.gms.dto;

import com.gms.entity.Package;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO của JOB-04 — DAO TẠO: AutoCloseSessionDAO.findDataForClosing (1 dòng SQL → 1 đối tượng).
 * Không in ra màn hình: AutoCloseSessionService.evaluate đọc để quyết định kết quả chốt buổi.
 */
public class SessionAutoCloseData {
    // ===== THÔNG TIN BUỔI =====
    private final int sessionId;

    private final LocalDate sessionDate;

    private final Package.Type ptSubscriptionType;

    // ===== NGƯỜI ĐỨNG TÊN — quyền vào gym NGÀY TẬP (evaluate B3) =====
    // holderHasMembership ← cột HolderHasMembership (Subscriptions Membership phủ ngày tập)
    // holderHasDayPass    ← cột HolderHasDayPass (vé ngày đúng ngày tập)
    // holderFrozen        ← cột HolderFrozen (FreezeRecords phủ ngày tập)
    private final boolean holderHasMembership;

    private final boolean holderHasDayPass;

    private final boolean holderFrozen;

    // ===== NGƯỜI TẬP CÙNG (chỉ gói PTCouple) — cùng 3 ý như trên =====
    private final boolean partnerHasMembership;

    private final boolean partnerHasDayPass;

    private final boolean partnerFrozen;

    // ===== KẾT QUẢ ĐI TẬP + GIỜ MỞ CỬA =====
    // hasCheckInOnSessionDate ← có dòng CheckIns trong ngày tập (evaluate B7)
    // gymClosedAllDay / gymOpenTime / gymCloseTime ← OperatingHourExceptions, không có thì P-19 (evaluate B1)
    // slotStartTime / slotEndTime ← giờ của khung TimeSlots (so với giờ mở cửa)
    private final boolean hasCheckInOnSessionDate;

    private final boolean gymClosedAllDay;
    private final LocalTime slotStartTime;
    private final LocalTime slotEndTime;
    private final LocalTime gymOpenTime;
    private final LocalTime gymCloseTime;

    public SessionAutoCloseData(int sessionId, LocalDate sessionDate, Package.Type ptSubscriptionType,
            boolean holderHasMembership, boolean holderHasDayPass, boolean holderFrozen,
            boolean partnerHasMembership, boolean partnerHasDayPass, boolean partnerFrozen,
            boolean hasCheckInOnSessionDate, boolean gymClosedAllDay,
            LocalTime slotStartTime, LocalTime slotEndTime,
            LocalTime gymOpenTime, LocalTime gymCloseTime) {
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
        this.gymClosedAllDay = gymClosedAllDay;
        this.slotStartTime = slotStartTime;
        this.slotEndTime = slotEndTime;
        this.gymOpenTime = gymOpenTime;
        this.gymCloseTime = gymCloseTime;
    }

    public int getSessionId() {
        return sessionId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public Package.Type getPtSubscriptionType() {
        return ptSubscriptionType;
    }

    public boolean hasHolderMembership() {
        return holderHasMembership;
    }

    public boolean hasHolderDayPass() {
        return holderHasDayPass;
    }

    public boolean isHolderFrozen() {
        return holderFrozen;
    }

    public boolean hasPartnerMembership() {
        return partnerHasMembership;
    }

    public boolean hasPartnerDayPass() {
        return partnerHasDayPass;
    }

    public boolean isPartnerFrozen() {
        return partnerFrozen;
    }

    public boolean hasCheckInOnSessionDate() {
        return hasCheckInOnSessionDate;
    }

    public boolean isGymClosedAllDay() { return gymClosedAllDay; }

    public LocalTime getSlotStartTime() { return slotStartTime; }
    public LocalTime getSlotEndTime() { return slotEndTime; }

    public LocalTime getGymOpenTime() { return gymOpenTime; }
    public LocalTime getGymCloseTime() { return gymCloseTime; }
}
