package com.gms.dto;

import com.gms.util.DateTimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TransactionHistoryItem {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DISPLAY_MONTH =
            DateTimeFormatter.ofPattern("MM/yyyy");

    private final int transactionId;
    private final String orderCode;
    private final String packageName;
    private final long amount;
    private final String paymentMethod;
    private final String status;
    private final String gatewayReference;
    private final LocalDateTime createdAt;
    private final LocalDateTime completedAt;
    private final boolean needsRefund;
    private final LocalDateTime refundedAt;
    private final Integer subscriptionId;
    private final String packageType;
    private final long originalPrice;
    private final long discountAmount;
    private final String voucherCode;
    private final int vatRate;
    private final long vatAmount;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String durationUnit;
    private final Integer durationValue;

    public TransactionHistoryItem(
            int transactionId,
            String orderCode,
            String packageName,
            long amount,
            String paymentMethod,
            String status,
            String gatewayReference,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            boolean needsRefund,
            LocalDateTime refundedAt,
            Integer subscriptionId,
            String packageType,
            long originalPrice,
            long discountAmount,
            String voucherCode,
            int vatRate,
            long vatAmount,
            LocalDate startDate,
            LocalDate endDate,
            String durationUnit,
            Integer durationValue) {

        this.transactionId = transactionId;
        this.orderCode = orderCode;
        this.packageName = packageName;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.gatewayReference = gatewayReference;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.needsRefund = needsRefund;
        this.refundedAt = refundedAt;
        this.subscriptionId = subscriptionId;
        this.packageType = packageType;
        this.originalPrice = originalPrice;
        this.discountAmount = discountAmount;
        this.voucherCode = voucherCode;
        this.vatRate = vatRate;
        this.vatAmount = vatAmount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.durationUnit = durationUnit;
        this.durationValue = durationValue;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getPackageName() {
        return packageName;
    }

    public long getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public Integer getSubscriptionId() {
        return subscriptionId;
    }

    // GETTER TỰ TÍNH — không có field/setter "onlineGateway"; = paymentMethod == "OnlineGateway"; JSP: customer/transaction-history.jsp:212
    public boolean isOnlineGateway() {
        return "OnlineGateway".equals(paymentMethod);
    }

    // GETTER TỰ TÍNH — không có field/setter "canViewPackage"; = có subscriptionId, giao dịch Success và chưa/không hoàn tiền; JSP: customer/transaction-history.jsp:198
    public boolean isCanViewPackage() {
        return subscriptionId != null
                && "Success".equals(status)
                && !needsRefund
                && refundedAt == null;
    }

    /** Chỉ có 2 hình thức hiển thị cho khách: VNPay (online) hoặc Tại quầy (tiền mặt/chuyển khoản do lễ tân thu). */
    // GETTER TỰ TÍNH — không có field/setter "paymentMethodLabel"; = OnlineGateway → "VNPay", khác → "Tại quầy"; JSP: customer/transaction-history.jsp:118 …
    public String getPaymentMethodLabel() {
        return "OnlineGateway".equals(paymentMethod) ? "VNPay" : "Tại quầy";
    }

    // GETTER TỰ TÍNH — không có field/setter "createdAtText"; = createdAt định dạng ngày giờ; JSP: customer/transaction-history.jsp:173
    public String getCreatedAtText() {
        return formatTime(createdAt);
    }

    // GETTER TỰ TÍNH — không có field/setter "completedAtText"; = completedAt định dạng ngày giờ; JSP: customer/transaction-history.jsp:179
    public String getCompletedAtText() {
        return formatTime(completedAt);
    }

    // ===== Dùng cho giao diện danh sách + popup chi tiết =====

    public long getOriginalPrice() {
        return originalPrice;
    }

    public long getDiscountAmount() {
        return discountAmount;
    }

    /** % VAT lúc mua (đã nằm trong giá, không cộng thêm). */
    public int getVatRate() {
        return vatRate;
    }

    public long getVatAmount() {
        return vatAmount;
    }

    // GETTER TỰ TÍNH — không có field/setter "hasCompleted"; = completedAt khác null; JSP: customer/transaction-history.jsp:175
    public boolean isHasCompleted() {
        return completedAt != null;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    /** Đơn có giảm giá thì popup hiện thêm dòng Giảm giá. */
    // GETTER TỰ TÍNH — không có field/setter "hasDiscount"; = discountAmount > 0; JSP: customer/transaction-history.jsp:157
    public boolean isHasDiscount() {
        return discountAmount > 0;
    }

    /** Tên màu/ảnh của banner popup (khớp data-tone ở trang Mua gói). */
    // GETTER TỰ TÍNH — không có field/setter "tone"; = packageType đổi sang tên màu (day-pass, pt-single…), mặc định membership; JSP: customer/transaction-history.jsp:130
    public String getTone() {
        if (packageType == null) {
            return "membership";
        }
        switch (packageType) {
            case "DayPass":       return "day-pass";
            case "PTSingle":      return "pt-single";
            case "PTCouple":      return "pt-couple";
            case "SingleSession": return "single-session";
            default:              return "membership";
        }
    }

    /** Trạng thái để tô màu: đã/đang chờ hoàn tiền thì là "Refunded", còn lại theo Status. */
    // GETTER TỰ TÍNH — không có field/setter "displayStatus"; = có hoàn tiền → Refunded; Success/Failed/Expired giữ nguyên; khác → Pending; JSP: customer/transaction-history.jsp:105 …
    public String getDisplayStatus() {
        if (refundedAt != null || needsRefund) {
            return "Refunded";
        }
        if ("Success".equals(status) || "Failed".equals(status)
                || "Expired".equals(status)) {
            return status;
        }
        return "Pending";
    }

    /** Nhãn ngắn của trạng thái. */
    // GETTER TỰ TÍNH — không có field/setter "shortStatusLabel"; = nhãn ngắn theo hoàn tiền rồi displayStatus; JSP: customer/transaction-history.jsp:122 …
    public String getShortStatusLabel() {
        if (refundedAt != null) {
            return "Đã hoàn tiền";
        }
        if (needsRefund) {
            return "Chờ hoàn tiền";
        }
        switch (getDisplayStatus()) {
            case "Success": return "Thành công";
            case "Failed":  return "Thất bại";
            case "Expired": return "Hết hạn";
            default:        return "Đang chờ";
        }
    }

    /** Dòng "Hoàn tiền" trong popup; null thì JSP không in. */
    // GETTER TỰ TÍNH — không có field/setter "refundRowText"; = ngày hoàn tiền; chờ hoàn → "Đang chờ hoàn tiền"; không có → null; JSP: customer/transaction-history.jsp:184 …
    public String getRefundRowText() {
        if (refundedAt != null) {
            return refundedAt.format(DISPLAY_DATE);
        }
        return needsRefund ? "Đang chờ hoàn tiền" : null;
    }

    /** Thất bại hoặc hết hạn (chưa hoàn tiền) thì cho mua lại. */
    // GETTER TỰ TÍNH — không có field/setter "canBuyAgain"; = giao dịch Failed/Expired và không hoàn tiền; JSP: customer/transaction-history.jsp:206
    public boolean isCanBuyAgain() {
        return ("Failed".equals(status) || "Expired".equals(status))
                && refundedAt == null && !needsRefund;
    }

    // GETTER TỰ TÍNH — không có field/setter "createdDateText"; = createdAt chỉ phần ngày, null → "—"; JSP: customer/transaction-history.jsp:116
    public String getCreatedDateText() {
        return createdAt == null ? "—" : createdAt.format(DISPLAY_DATE);
    }

    /** Tiêu đề nhóm: "Tháng 10/2026". */
    // GETTER TỰ TÍNH — không có field/setter "monthLabel"; = "Tháng …" của createdAt, null → ""; JSP: customer/transaction-history.jsp:91 …
    public String getMonthLabel() {
        return createdAt == null ? "" : "Tháng " + createdAt.format(DISPLAY_MONTH);
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "—" : value.format(DISPLAY_TIME);
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getDurationUnit() {
        return durationUnit;
    }

    public Integer getDurationValue() {
        return durationValue;
    }

    /** Có thông tin thời hạn gói (giao dịch thành công và đã được cấp ngày). */
    // GETTER TỰ TÍNH — không có field/setter "startText"; = startDate dạng dd/MM/yyyy, null → ""; JSP: customer/transaction-history.jsp:147
    public String getStartText() {
        return startDate == null ? "" : DateTimeUtil.formatDate(startDate);
    }

    // GETTER TỰ TÍNH — không có field/setter "endText"; = endDate dạng dd/MM/yyyy, null → ""; JSP: customer/transaction-history.jsp:149
    public String getEndText() {
        return endDate == null ? "" : DateTimeUtil.formatDate(endDate);
    }

    /** Chuỗi dải ngày hiệu lực: Từ dd/MM/yyyy đến dd/MM/yyyy */
    // GETTER TỰ TÍNH — không có field/setter "fullValidityText"; = "Từ … đến …", cùng ngày → "Ngày …", thiếu ngày → ""; JSP: customer/transaction-history.jsp:111
    public String getFullValidityText() {
        if (startDate == null || endDate == null) {
            return "";
        }
        if (startDate.equals(endDate)) {
            return "Ngày " + DateTimeUtil.formatDate(startDate);
        }
        return "Từ " + DateTimeUtil.formatDate(startDate) + " đến " + DateTimeUtil.formatDate(endDate);
    }

 // GETTER TỰ TÍNH — không có field/setter "hasValidity"; = giao dịch Success và có đủ startDate, endDate; JSP: customer/transaction-history.jsp:109 …
 public boolean isHasValidity() {
        return "Success".equals(status) && startDate != null && endDate != null;
    }

    /** Chuỗi thời hạn gói dạng: 3 tháng, 1 ngày */
    // GETTER TỰ TÍNH — không có field/setter "durationText"; = "N ngày" hoặc "N tháng", không có → ""; JSP: customer/transaction-history.jsp:143 …
    public String getDurationText() {
        if (durationValue == null || durationValue <= 0) {
            return "";
        }
        return "Days".equalsIgnoreCase(durationUnit) ? durationValue + " ngày" : durationValue + " tháng";
    }
}
