package com.gms.dto;

/**
 * 1 thẻ gói trên màn Mua gói (Customer) = thẻ giá dùng chung + phần "mua được không / mua ở đâu".
 *
 * Khối màn hình: lưới thẻ gói và popup chi tiết của màn Mua gói.
 * Nơi tạo: PackageSelectionService.buildSelection(...), từ 1 PackageCardView (thẻ giá đã tính giá/tháng, % tiết kiệm, nhãn).
 * Nơi dùng: package-selection.jsp, đọc qua ${option.card.pkg.packageName}, ${option.purchasableOnline}...
 *
 * Ví dụ Gói 3 Tháng: purchasableOnline = true, typeLabel = "Gói tập", durationLabel = "3 tháng",
 * channelLabel = "Thanh toán trực tuyến hoặc tại quầy", imagePath = "/images/packages/membership.svg".
 * Ví dụ PT 1 Tháng: purchasableOnline = false, channelLabel = "Chỉ mua tại quầy".
 *
 * @author ALOHA
 */
public class PackageOptionView {

    // Service: PackageCardView do Service dựng sẵn (giá/tháng, % tiết kiệm, nhãn Tiết kiệm hơn / Mới ra mắt).
    // JSP lấy gói gốc qua ${option.card.pkg} và các con số qua ${option.card.monthlyPrice}...
    private final PackageCardView card;

    // Service: true khi loại gói là Membership hoặc DayPass (UC-22 chỉ bán 2 loại này online).
    // JSP dùng để quyết định có vẽ nút "Mua ngay" hay không; không in true/false ra trang.
    private final boolean purchasableOnline;

    // Service: giá nếu mua lẻ từng tháng (giá gói 1 tháng × số tháng), JSP gạch ngang; null nếu gói không rẻ hơn mua lẻ.
    private final Long originalPrice;

    // Service: số tiền tiết kiệm = originalPrice − giá gói; null khi originalPrice là null.
    private final Long savedAmount;

    // Service: nhãn loại gói, VD "Gói tập", "Vé ngày", "Gói PT". JSP in ở góc thẻ.
    private final String typeLabel;

    // Service: thời hạn dạng chữ, VD "3 tháng", "1 ngày", "1 buổi". JSP in cạnh giá.
    private final String durationLabel;

    // Service: câu cố định cho biết kênh mua, VD "Thanh toán trực tuyến hoặc tại quầy". JSP in trên thẻ để
    // điện thoại (không có hover) vẫn thấy được.
    private final String channelLabel;

    // Service: câu giải thích đầy đủ cách mua gói này (có số phút giữ đơn P-09 hoặc giờ mở cửa).
    // JSP in trong tooltip của nút và trong popup chi tiết.
    private final String purchaseHint;

    // Service: đường dẫn ảnh gói tính từ context path, VD "/images/packages/membership.svg".
    // null khi thư mục ảnh chưa có file nào; JSP khi đó chỉ hiện khung màu.
    private final String imagePath;

    /**
     * @param card thẻ giá đã tính sẵn
     * @param purchasableOnline true nếu khách tự mua online được
     * @param originalPrice giá mua lẻ từng tháng hoặc null
     * @param savedAmount số tiền tiết kiệm hoặc null
     * @param typeLabel nhãn loại gói
     * @param durationLabel thời hạn dạng chữ
     * @param channelLabel câu cố định về kênh mua
     * @param purchaseHint câu giải thích cách mua
     * @param imagePath đường dẫn ảnh hoặc null
     */
    public PackageOptionView(PackageCardView card, boolean purchasableOnline,
            Long originalPrice, Long savedAmount, String typeLabel, String durationLabel, String channelLabel, String purchaseHint, String imagePath) {
        this.card = card;
        this.purchasableOnline = purchasableOnline;
        this.originalPrice = originalPrice;
        this.savedAmount = savedAmount;
        this.typeLabel = typeLabel;
        this.durationLabel = durationLabel;
        this.channelLabel = channelLabel;
        this.purchaseHint = purchaseHint;
        this.imagePath = imagePath;
    }

    /** JSP: ${option.card.pkg.price}, ${option.card.badge}... */
    public PackageCardView getCard() {
        return card;
    }

    /** JSP: ${option.purchasableOnline} — chọn nút Mua ngay hay chỉ nút Xem chi tiết. */
    public boolean isPurchasableOnline() {
        return purchasableOnline;
    }

    /** JSP: ${option.originalPrice} — giá gạch ngang. */
    public Long getOriginalPrice() {
        return originalPrice;
    }

    /** JSP: ${option.savedAmount} — "Tiết kiệm x đ". */
    public Long getSavedAmount() {
        return savedAmount;
    }

    /** JSP: ${option.typeLabel} */
    public String getTypeLabel() {
        return typeLabel;
    }

    /** JSP: ${option.durationLabel} */
    public String getDurationLabel() {
        return durationLabel;
    }

    /** JSP: ${option.channelLabel} */
    public String getChannelLabel() {
        return channelLabel;
    }

    /** JSP: ${option.purchaseHint} — tooltip và đoạn hướng dẫn trong popup. */
    public String getPurchaseHint() {
        return purchaseHint;
    }

    /** JSP: ${option.imagePath} — ghép sau ${pageContext.request.contextPath}. */
    public String getImagePath() {
        return imagePath;
    }
}
