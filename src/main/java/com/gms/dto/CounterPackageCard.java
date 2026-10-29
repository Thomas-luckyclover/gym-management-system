package com.gms.dto;

import com.gms.entity.Package;
import java.util.List;

/**
 * MỘT THẺ GÓI trên màn "Chọn gói cho khách" (/receptionist/sales), SRS #37.
 *
 * Ai tạo: CounterSaleService.buildCard từ một dòng Packages mà CounterSaleDAO đọc.
 * Ai dùng: counter-sale-packages.jsp (vẽ thẻ), counter-sale-customers.jsp (cột "Tóm tắt đơn").
 * Chỉ đọc: tạo bằng constructor, không có setter.
 */
public class CounterPackageCard {

    // DAO: Packages.PackageId. Gắn vào ?package=… trên URL và data-package-id của thẻ; không in thành chữ.
    private final int packageId;

    // DAO: Packages.PackageName, ví dụ "Gói 3 Tháng". In to ở dải đầu thẻ và ở "Đang chọn".
    private final String packageName;

    // DAO: Packages.PackageType đã đổi sang enum. JSP không in; Service dùng để chọn nhãn và luật.
    private final Package.Type packageType;

    // Service: nhãn nhỏ viết hoa ở dải đầu thẻ, ví dụ "GÓI TẬP", "VÉ NGÀY".
    private final String typeLabel;

    // DAO: Packages.Price, đã gồm VAT, đơn vị đồng. JSP in bằng gms:formatMoney.
    private final long price;

    // SystemConfigs P-10 lúc dựng thẻ, ví dụ 8. Hiện trong "VAT 8% (đã gồm)" ở cột Tóm tắt đơn.
    private final int vatRate;

    // Service: MoneyUtil.calculateVatAmount(price, vatRate), tiền VAT nằm sẵn trong giá. Hiện ở dòng "VAT … (đã gồm)".
    private final long vatAmount;

    // Service: price trừ vatAmount, tức giá trước VAT. Hiện ở dòng "Trước VAT".
    private final long preTaxAmount;

    // Service: thời hạn viết sẵn, ví dụ "3 tháng", "1 ngày", "1 buổi". Không hiện số ngày.
    private final String durationText;

    // Service: giá mỗi tháng = làm tròn(price / số tháng), chỉ có với gói tập từ 2 tháng. 0 = không hiện dòng này.
    private final long monthlyPrice;

    // Service: % rẻ hơn so với mua lẻ gói 1 tháng, chỉ tính khi gói từ 3 tháng. 0 = không hiện nhãn "-13%".
    private final int savingPercent;

    // DAO: Packages.Benefits tách theo dòng (tối đa 10 dòng). JSP in mục "QUYỀN LỢI".
    private final List<String> benefitLines;

    // Service: các dòng "QUY ĐỊNH" theo loại gói (bảo lưu, voucher, chụp ảnh...). Số liệu đọc từ SystemConfigs.
    private final List<String> ruleLines;

    // Service: true nếu quầy bán được gói này ngay. Buổi PT lẻ chưa có màn bán tại quầy nên là false.
    private final boolean isContinueAllowed;

    // Service: lý do khóa nút, hiện ở tooltip khi isContinueAllowed = false. Gói bán được thì để chuỗi rỗng.
    private final String blockedReason;

    public CounterPackageCard(int packageId, String packageName, Package.Type packageType, String typeLabel,
            long price, int vatRate, long vatAmount, long preTaxAmount,
            String durationText, long monthlyPrice, int savingPercent,
            List<String> benefitLines, List<String> ruleLines,
            boolean isContinueAllowed, String blockedReason) {
        this.packageId = packageId;
        this.packageName = packageName;
        this.packageType = packageType;
        this.typeLabel = typeLabel;
        this.price = price;
        this.vatRate = vatRate;
        this.vatAmount = vatAmount;
        this.preTaxAmount = preTaxAmount;
        this.durationText = durationText;
        this.monthlyPrice = monthlyPrice;
        this.savingPercent = savingPercent;
        this.benefitLines = List.copyOf(benefitLines);
        this.ruleLines = List.copyOf(ruleLines);
        this.isContinueAllowed = isContinueAllowed;
        this.blockedReason = blockedReason;
    }

    // Mã gói, gắn vào URL và data-package-id.
    public int getPackageId() {
        return packageId;
    }

    // Tên gói in ở dải đầu thẻ.
    public String getPackageName() {
        return packageName;
    }

    // Loại gói (enum), dùng trong Service.
    public Package.Type getPackageType() {
        return packageType;
    }

    // Nhãn loại gói ở dải đầu thẻ.
    public String getTypeLabel() {
        return typeLabel;
    }

    // Giá đã gồm VAT.
    public long getPrice() {
        return price;
    }

    // Thuế suất % lúc dựng thẻ.
    public int getVatRate() {
        return vatRate;
    }

    // Tiền VAT đã gồm trong giá.
    public long getVatAmount() {
        return vatAmount;
    }

    // Giá trước VAT.
    public long getPreTaxAmount() {
        return preTaxAmount;
    }

    // Thời hạn viết sẵn.
    public String getDurationText() {
        return durationText;
    }

    // Giá mỗi tháng; 0 là không hiện.
    public long getMonthlyPrice() {
        return monthlyPrice;
    }

    // % tiết kiệm; 0 là không hiện nhãn.
    public int getSavingPercent() {
        return savingPercent;
    }

    // Danh sách quyền lợi.
    public List<String> getBenefitLines() {
        return benefitLines;
    }

    // Danh sách quy định.
    public List<String> getRuleLines() {
        return ruleLines;
    }

    // JSP đọc ${card.continueAllowed}: false thì thẻ bị mờ, không chọn được.
    public boolean isContinueAllowed() {
        return isContinueAllowed;
    }

    // Lý do khóa, hiện ở tooltip.
    public String getBlockedReason() {
        return blockedReason;
    }
}