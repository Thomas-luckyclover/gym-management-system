package com.gms.dto;

import com.gms.util.MoneyUtil;

/**
 * 1 dòng bảng "Hoa hồng" trên PT Dashboard: 1 nguồn buổi, bao nhiêu buổi, bao nhiêu tiền.
 *
 * Ai tạo: PtDashboardService (cộng từ các SessionStatRow được tính hoa hồng).
 * Ai dùng: pt/dashboard.jsp.
 *
 * VD: label "Gói PT cá nhân", sessionCount 4, totalAmount 1_120_000 → hoa hồng TB 1 buổi 280.000đ.
 *
 * @author Le Cong Minh
 */
public class CommissionLine {

    private final String label;
    private int sessionCount;
    private long totalAmount;

    /**
     * @param label tên nguồn buổi, VD "Gói PT Couple".
     */
    public CommissionLine(String label) {
        this.label = label;
    }

    /** Cộng thêm 1 buổi vào dòng này. */
    public void add(long commissionAmount) {
        sessionCount++;
        totalAmount += commissionAmount;
    }

    public String getLabel() {
        return label;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    /** @return VD "1.120.000đ". */
    public String getTotalText() {
        return MoneyUtil.formatMoney(totalAmount);
    }

    /**
     * @return hoa hồng TRUNG BÌNH 1 buổi, VD "280.000đ". Mỗi buổi có giá trị riêng (DBR-59: số buổi thực sinh
     *         của từng đơn khác nhau), nên cột này là trung bình, không phải 1 mức cố định.
     */
    public String getAverageText() {
        return MoneyUtil.formatMoney(sessionCount == 0 ? 0 : totalAmount / sessionCount);
    }
}
