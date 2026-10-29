package com.gms.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Quy tắc ngày bắt đầu / hết hạn của gói Membership mua online (DBR-52, GBR-03).
 *
 * <p>
 * Gói được cấp khi IPN về với ngày bắt đầu mặc định theo DBR-52 (ngày thanh
 * toán hoặc ngay sau gói cũ). Sau đó khách được đổi một lần sang ngày khác
 * trong khoảng hôm nay đến hôm nay + 7 ngày, miễn là gói chưa bắt đầu dùng.</p>
 *
 * <p>
 * Chỉ tính toán thuần trên LocalDate, không đọc đồng hồ và không gọi DB, nên
 * kiểm thử được mà không cần server. Người gọi truyền vào "hôm nay" lấy từ
 * DateTimeUtil.today().</p>
 *
 * <ul>
 * <li>Khách được chọn ngày bắt đầu từ hôm nay đến hôm nay + 7 ngày.</li>
 * <li>Nếu đang có gói còn hạn hoặc đang bảo lưu, gói mới chỉ được bắt đầu từ
 * ngày hôm sau của ngày hết hạn gói đó (DBR-52).</li>
 * <li>Ngày hết hạn = ngày bắt đầu + số tháng của gói, cộng theo lịch tháng
 * (cuối tháng thì lùi về ngày cuối tháng), không phải 30 ngày.</li>
 * </ul>
 */
public final class PackagePeriod {

    /**
     * Số ngày tối đa được hẹn trước, tính từ hôm nay.
     */
    public static final int MAX_DAYS_AHEAD = 7;

    private PackagePeriod() {
    }

    /**
     * Ngày sớm nhất được bắt đầu: hôm nay, hoặc ngày sau khi gói hiện tại hết
     * hạn nếu gói đó còn hiệu lực.
     *
     * @param today hôm nay theo giờ phòng gym
     * @param lastMembershipEnd ngày hết hạn gói Membership cuối cùng còn
     * Active/Frozen, null nếu khách không có gói như vậy
     */
    public static LocalDate earliestStart(LocalDate today, LocalDate lastMembershipEnd) {
        if (lastMembershipEnd == null) {
            return today;
        }
        LocalDate next = lastMembershipEnd.plusDays(1);
        return next.isAfter(today) ? next : today;
    }

    /**
     * Ngày muộn nhất được hẹn: hôm nay + 7 ngày.
     */
    public static LocalDate latestStart(LocalDate today) {
        return today.plusDays(MAX_DAYS_AHEAD);
    }

    /**
     * Các ngày khách được bấm chọn, theo thứ tự tăng dần. Rỗng khi gói hiện tại
     * còn hiệu lực quá xa nên gói mới buộc phải nối sau, không còn ngày để
     * chọn.
     */
    public static List<LocalDate> options(LocalDate today, LocalDate lastMembershipEnd) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate last = latestStart(today);
        for (LocalDate d = earliestStart(today, lastMembershipEnd); !d.isAfter(last); d = d.plusDays(1)) {
            days.add(d);
        }
        return days;
    }

    /**
     * Ngày chọn có hợp lệ không (nằm trong khoảng được chọn).
     */
    public static boolean isSelectable(LocalDate chosen, LocalDate today, LocalDate lastMembershipEnd) {
        return chosen != null
                && !chosen.isBefore(earliestStart(today, lastMembershipEnd))
                && !chosen.isAfter(latestStart(today));
    }

    /**
     * Ngày hết hạn theo lịch tháng. Cùng công thức với bước cấp gói trong IPN.
     *
     * @throws IllegalArgumentException khi số tháng không dương
     */
    public static LocalDate endDate(LocalDate start, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("Số tháng phải lớn hơn 0");
        }
        return DateTimeUtil.calculateEndDateByMonths(start, months);
    }

    /**
     * Ngày bắt đầu thực tế khi mở cổng thanh toán: giữ ngày khách đã chọn,
     * trừ khi gói khác của khách vừa kéo dài hạn nên phải nối sau gói đó.
     * Không bao giờ lùi ngày về trước ngày đã chọn.
     */
    public static LocalDate effectiveStart(LocalDate chosen, LocalDate lastMembershipEnd) {
        if (lastMembershipEnd == null) {
            return chosen;
        }
        LocalDate next = lastMembershipEnd.plusDays(1);
        return next.isAfter(chosen) ? next : chosen;
    }

    /**
     * Lý do khách không còn đổi được ngày bắt đầu của gói đã mua; null nếu còn đổi được.
     *
     * @param currentStart ngày bắt đầu hiện tại của gói
     * @param checkedIn gói đã có lượt check-in
     * @param changesDone số lần khách đã đổi ngày (tối đa 1)
     * @param hasLaterMembership có gói khác nối tiếp sau gói này
     */
    public static String lockReason(LocalDate today, LocalDate currentStart, boolean checkedIn,
            int changesDone, boolean hasLaterMembership) {
        if (changesDone >= 1) {
            return "Bạn đã chọn ngày bắt đầu một lần, không đổi lại được.";
        }
        if (checkedIn || currentStart.isBefore(today)) {
            return "Gói đã bắt đầu sử dụng nên không đổi được ngày bắt đầu.";
        }
        if (hasLaterMembership) {
            return "Bạn đã có gói nối tiếp sau gói này nên không đổi được ngày bắt đầu.";
        }
        return null;
    }

    /**
     * Các ngày được chọn cho gói đã mua: trong khoảng hôm nay đến hôm nay + 7 ngày, không trước gói khác
     * còn hạn (DBR-52) và không sớm hơn ngày bắt đầu hiện tại, vì đây chỉ dời ngày bắt đầu về sau.
     */
    public static List<LocalDate> optionsForPurchased(LocalDate today, LocalDate currentStart,
            LocalDate otherMembershipEnd) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day : options(today, otherMembershipEnd)) {
            if (!day.isBefore(currentStart)) {
                days.add(day);
            }
        }
        return days;
    }

    /**
     * Số tháng của gói suy ra từ ngày bắt đầu và hết hạn đã lưu (thử 1..36 tháng cho đến khi đúng công thức
     * endDate). Dùng để xem trước ngày hết hạn của từng ngày khách chọn mà không cần đọc lại gói.
     *
     * @return số tháng, hoặc 0 nếu không khớp công thức nào
     */
    public static int monthsBetween(LocalDate start, LocalDate end) {
        for (int months = 1; months <= 36; months++) {
            if (endDate(start, months).equals(end)) {
                return months;
            }
        }
        return 0;
    }

    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Ngày thanh toán dùng làm ngày bắt đầu mặc định. Lấy theo giờ VNPay ghi
     * nhận thanh toán (vnp_PayDate, giờ Việt Nam) để IPN về sau nửa đêm không
     * làm ngày bắt đầu nhảy sang hôm sau. Giá trị thiếu, sai định dạng, ở
     * tương lai hoặc cũ hơn 1 ngày so với giờ xử lý thì dùng giờ xử lý.
     *
     * @param vnpPayDate chuỗi yyyyMMddHHmmss từ VNPay, có thể null
     * @param now giờ server khi xử lý IPN
     */
    public static LocalDate paymentDay(String vnpPayDate, LocalDateTime now) {
        if (vnpPayDate == null || !vnpPayDate.matches("[0-9]{14}")) {
            return now.toLocalDate();
        }
        try {
            LocalDateTime paid = LocalDateTime.parse(vnpPayDate, VNPAY_TIME);
            if (paid.isAfter(now) || paid.isBefore(now.minusDays(1))) {
                return now.toLocalDate();
            }
            return paid.toLocalDate();
        } catch (DateTimeParseException e) {
            return now.toLocalDate();
        }
    }
}
