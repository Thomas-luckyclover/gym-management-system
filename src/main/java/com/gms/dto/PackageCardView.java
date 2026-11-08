package com.gms.dto;

import com.gms.entity.Package;

/**
 * 1 thẻ giá trên trang chủ = 1 gói + các con số đã tính sẵn (luật 2.6).
 * Service tính, JSP chỉ việc in ra — JSP không làm phép tính nào.
 *
 * Ví dụ Gói 3 Tháng: months = 3, monthlyPrice = 730.000, savingPercent = 9, badge = "Phổ biến nhất".
 *
 * @author Le Cong Minh
 */
public class PackageCardView {

    private final Package pkg;
    private final int months;
    private final Long monthlyPrice;
    private final Integer savingPercent;

    /** Nhãn gắn SAU khi đã tính xong mọi gói (phải so sánh cả danh sách mới biết gói nào tiết kiệm nhất). */
    private String badge;

    /**
     * @param pkg gói gốc lấy từ DB
     * @param months số tháng (90 ngày → 3); 0 với gói ngày và buổi PT lẻ
     * @param monthlyPrice giá mỗi tháng; null nếu không cần hiện (gói ≤ 1 tháng)
     * @param savingPercent % tiết kiệm so với gói 1 tháng; null nếu không tiết kiệm
     */
    public PackageCardView(Package pkg, int months, Long monthlyPrice, Integer savingPercent) {
        this.pkg = pkg;
        this.months = months;
        this.monthlyPrice = monthlyPrice;
        this.savingPercent = savingPercent;
    }

    /** JSP: ${card.pkg.packageName}, ${card.pkg.price} */
    public Package getPkg() {
        return pkg;
    }

    public int getMonths() {
        return months;
    }

    public Long getMonthlyPrice() {
        return monthlyPrice;
    }

    public Integer getSavingPercent() {
        return savingPercent;
    }

    /** "Phổ biến nhất" / "Tiết kiệm nhất"; null nếu không có nhãn. */
    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }
}
