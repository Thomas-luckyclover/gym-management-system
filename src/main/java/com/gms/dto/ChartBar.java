package com.gms.dto;

import com.gms.util.MoneyUtil;

/**
 * 1 cột của biểu đồ hoa hồng trên PT Dashboard (theo tuần nếu kỳ là tháng, theo tháng nếu kỳ là quý).
 *
 * Ai tạo: PtDashboardService. Ai dùng: pt/dashboard.jsp (vẽ bằng CSS, chiều cao = heightPercent%).
 *
 * VD: label "08–14", amount 560_000, heightPercent 67.
 *
 * @author Le Cong Minh
 */
public class ChartBar {

    private final String label;
    private long amount;
    private int heightPercent;

    /**
     * @param label nhãn dưới cột, VD "08–14" (tuần) hoặc "T08" (tháng).
     */
    public ChartBar(String label) {
        this.label = label;
    }

    public void add(long value) {
        amount += value;
    }

    public String getLabel() {
        return label;
    }

    public long getAmount() {
        return amount;
    }

    /** @return VD "560.000đ" — hiện khi rê chuột lên cột. */
    public String getAmountText() {
        return MoneyUtil.formatMoney(amount);
    }

    /** @return chiều cao cột so với cột cao nhất (0–100). */
    public int getHeightPercent() {
        return heightPercent;
    }

    public void setHeightPercent(int heightPercent) {
        this.heightPercent = heightPercent;
    }
}
