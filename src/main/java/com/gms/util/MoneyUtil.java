package com.gms.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Xử lý tiền theo GBR-02, GBR-29, DBR-06, DBR-57.
 *
 * Quy tắc cho cả nhóm:
 * - Tiền luôn là số nguyên VND, kiểu Java là long, kiểu SQL Server là BIGINT hoặc DECIMAL(18,0) — cấm double/float.
 * - Giá trong catalogue ĐÃ gồm VAT; VAT chỉ hiển thị dạng "trong đó VAT x%", không cộng thêm.
 * - Trong JSP dùng hàm EL: ${gms:formatMoney(order.amountPayable)} → "1.250.000đ".
 */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    /**
     * GBR-29: định dạng tiền có dấu chấm phân cách hàng nghìn và hậu tố "đ".
     * @param amount số tiền VND
     * @return chuỗi dạng "1.250.000đ"
     */
    public static String formatMoney(long amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
        return formatter.format(amount) + "đ";
    }

    /**
     * DBR-57: tách tiền VAT ra khỏi số tiền phải trả (giá đã gồm VAT).
     * Công thức: VAT = Số tiền phải trả × VAT% ÷ (100 + VAT%), làm tròn tới đồng.
     * Kết quả được lưu vào đơn ngay lúc tạo đơn, không tính lại sau.
     * @param amountPayable số tiền phải trả = giá lúc mua − giảm giá
     * @param vatRate thuế suất % (0–20), copy từ SystemConfig vào đơn
     * @return số tiền VAT (đồng)
     */
    public static long calculateVatAmount(long amountPayable, int vatRate) {
        BigDecimal amount = BigDecimal.valueOf(amountPayable);
        BigDecimal rate = BigDecimal.valueOf(vatRate);
        BigDecimal divisor = BigDecimal.valueOf(100L + vatRate);
        BigDecimal vatAmount = amount.multiply(rate).divide(divisor, 0, RoundingMode.HALF_UP);
        return vatAmount.longValueExact();
    }

    /**
     * DBR-57: tiền trước thuế = số tiền phải trả − tiền VAT.
     * @param amountPayable số tiền phải trả
     * @param vatAmount tiền VAT đã tính bằng calculateVatAmount
     * @return tiền trước thuế
     */
    public static long calculatePreTaxAmount(long amountPayable, long vatAmount) {
        return amountPayable - vatAmount;
    }

    /**
     * Tính số tiền giảm theo phần trăm, làm tròn tới đồng, không vượt giá gốc (DBR-06).
     * @param price giá trước giảm
     * @param percent phần trăm giảm (1–100)
     * @return số tiền được giảm
     */
    public static long calculatePercentDiscount(long price, int percent) {
        BigDecimal priceValue = BigDecimal.valueOf(price);
        BigDecimal percentValue = BigDecimal.valueOf(percent);
        BigDecimal discount = priceValue.multiply(percentValue).divide(BigDecimal.valueOf(100L), 0, RoundingMode.HALF_UP);
        long discountAmount = discount.longValueExact();
        return Math.min(discountAmount, price);
    }
}
