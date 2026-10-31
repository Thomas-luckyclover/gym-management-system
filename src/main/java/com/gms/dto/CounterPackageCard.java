package com.gms.dto;

import com.gms.entity.Package;
import java.util.List;

/**
 * MỘT THẺ GÓI trên màn "Chọn gói cho khách" (/receptionist/sales), SRS #37.
 *
 * Ai tạo: CounterSaleService.buildCard từ một dòng Packages mà CounterSaleDAO đọc.
 * Ai dùng: counter-sale-packages.jsp (vẽ thẻ), counter-sale-customers.jsp (cột "Tóm tắt đơn"),
 *          counter-sale-next.jsp (xác nhận đơn hàng và tính ngày hiệu lực).
 * Chỉ đọc: tạo bằng constructor, không có setter.
 */
public class CounterPackageCard {

    // DAO: Packages.PackageId, khóa chính của gói tập.
    // Ý nghĩa: định danh duy nhất của gói trong cơ sở dữ liệu.
    // Cách dùng: gắn vào ?package=… trên URL và data-package-id của thẻ; không in thành chữ.
    private final int packageId;

    // DAO: Packages.PackageName, ví dụ "Gói 3 Tháng".
    // Ý nghĩa: tên thương mại của gói tập.
    // Cách dùng: in to ở dải đầu thẻ và ở cột Tóm tắt đơn hàng.
    private final String packageName;

    // DAO: Packages.PackageType đã đổi sang enum.
    // Ý nghĩa: phân loại gói (Tập tự do, Buổi PT kèm, Vé ngày).
    // Cách dùng: JSP không in; Service dùng để chọn nhãn và luật nghiệp vụ.
    private final Package.Type packageType;

    // Service: nhãn nhỏ viết hoa ở dải đầu thẻ, ví dụ "GÓI TẬP", "VÉ NGÀY".
    // Ý nghĩa: phân biệt nhanh nhóm gói trên giao diện.
    // Cách dùng: in dạng badge nhỏ ở góc trên thẻ gói.
    private final String typeLabel;

    // DAO: Packages.Price, đã gồm VAT, đơn vị đồng.
    // Ý nghĩa: giá bán niêm yết của gói dịch vụ.
    // Cách dùng: JSP in bằng gms:formatMoney ở thẻ gói và bill thanh toán.
    private final long price;

    // SystemConfigs: P-10 lúc dựng thẻ, ví dụ 8.
    // Ý nghĩa: phần trăm thuế giá trị gia tăng áp dụng cho gói.
    // Cách dùng: hiện trong "VAT 8% (đã gồm)" ở cột Tóm tắt đơn.
    private final int vatRate;

    // Service: MoneyUtil.calculateVatAmount(price, vatRate), tiền VAT nằm sẵn trong giá.
    // Ý nghĩa: số tiền thuế tương ứng đã nằm trong giá niêm yết.
    // Cách dùng: hiện ở dòng "Thuế VAT ... (đã gồm)" trong hóa đơn tóm tắt.
    private final long vatAmount;

    // Service: price trừ vatAmount, tức giá trước VAT.
    // Ý nghĩa: doanh thu thuần trước thuế của gói.
    // Cách dùng: hiện ở dòng "Trước VAT" trong hóa đơn tóm tắt.
    private final long preTaxAmount;

    // Service: thời hạn viết sẵn, ví dụ "3 tháng", "1 ngày", "1 buổi".
    // Ý nghĩa: thời lượng sử dụng của gói.
    // Cách dùng: in ở dòng "Thời hạn: ..." trên thẻ gói và hóa đơn.
    private final String durationText;

    // DAO: Packages.DurationValue khi DurationUnit = 'Month', ngược lại là 0 (vé ngày / buổi lẻ).
    // Ý nghĩa: số tháng thời hạn của gói để tính toán ngày hết hạn theo lịch.
    // Cách dùng: JavaScript và Service dùng tính Subscriptions.EndDate = StartDate + durationMonths.
    private final int durationMonths;

    // Service: giá mỗi tháng = làm tròn(price / số tháng), chỉ có với gói tập từ 2 tháng.
    // Ý nghĩa: chi phí trung bình mỗi tháng để khách dễ so sánh.
    // Cách dùng: in chữ nhỏ bên dưới giá gói; 0 = không hiện dòng này.
    private final long monthlyPrice;

    // Service: % rẻ hơn so với mua lẻ gói 1 tháng, chỉ tính khi gói từ 3 tháng.
    // Ý nghĩa: mức tiết kiệm kích thích khách mua gói dài hạn.
    // Cách dùng: in badge nổi bật "-13%" ở góc thẻ; 0 = không hiện nhãn.
    private final int savingPercent;

    // DAO: Packages.Benefits tách theo dòng (tối đa 10 dòng).
    // Ý nghĩa: các đặc quyền và tiện ích khách nhận được khi mua gói.
    // Cách dùng: JSP lặp in danh sách tích xanh ở mục "QUYỀN LỢI".
    private final List<String> benefitLines;

    // Service: các dòng "QUY ĐỊNH" theo loại gói (bảo lưu, voucher, chụp ảnh...).
    // Ý nghĩa: quy chế vận hành và ràng buộc đối với gói này.
    // Cách dùng: JSP lặp in danh sách quy định bên dưới thẻ gói.
    private final List<String> ruleLines;

    // Service: true nếu quầy bán được gói này ngay (buổi PT lẻ chưa bán tại quầy thì false).
    // Ý nghĩa: xác định nút "Chọn gói này" có được bấm hay bị khóa.
    // Cách dùng: JSP dùng để vô hiệu hóa nút submit và hiện tooltip.
    private final boolean isContinueAllowed;

    // Service: lý do khóa nút, hiện ở tooltip khi isContinueAllowed = false.
    // Ý nghĩa: giải thích vì sao gói tập chưa được hỗ trợ bán tại quầy.
    // Cách dùng: gắn vào thuộc tính title của nút bấm bị khóa; gói bán được thì để chuỗi rỗng.
    private final String blockedReason;

    public CounterPackageCard(int packageId, String packageName, Package.Type packageType, String typeLabel,
            long price, int vatRate, long vatAmount, long preTaxAmount,
            String durationText, int durationMonths, long monthlyPrice, int savingPercent,
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
        this.durationMonths = durationMonths;
        this.monthlyPrice = monthlyPrice;
        this.savingPercent = savingPercent;
        this.benefitLines = List.copyOf(benefitLines);
        this.ruleLines = List.copyOf(ruleLines);
        this.isContinueAllowed = isContinueAllowed;
        this.blockedReason = blockedReason;
    }

    // Mã gói, gắn vào URL ?package=... và thuộc tính data-package-id trên giao diện.
    public int getPackageId() {
        return packageId;
    }

    // Tên gói in ở dải đầu thẻ gói và trong hóa đơn tóm tắt.
    public String getPackageName() {
        return packageName;
    }

    // Loại gói (enum), CounterSaleService dùng để phân loại vào 3 tab giao diện.
    public Package.Type getPackageType() {
        return packageType;
    }

    // Nhãn loại gói ("GÓI TẬP", "VÉ NGÀY"), in ở badge góc trên thẻ gói.
    public String getTypeLabel() {
        return typeLabel;
    }

    // Giá đã gồm VAT (đồng), in to ở thẻ gói và dòng Tổng tiền hóa đơn.
    public long getPrice() {
        return price;
    }

    // Thuế suất % lúc dựng thẻ, in ở dòng "VAT ...% (đã gồm)" trên hóa đơn.
    public int getVatRate() {
        return vatRate;
    }

    // Tiền VAT đã gồm trong giá (đồng), in ở dòng chi tiết thuế trên hóa đơn.
    public long getVatAmount() {
        return vatAmount;
    }

    // Giá trước VAT (đồng), in ở dòng "Trước VAT" trên hóa đơn.
    public long getPreTaxAmount() {
        return preTaxAmount;
    }

    // Thời hạn viết sẵn ("1 tháng", "1 ngày"), in ở thẻ gói và hóa đơn.
    public String getDurationText() {
        return durationText;
    }

    // Số tháng của gói (0 với vé ngày / buổi lẻ), JS và Service dùng tính ngày hết hạn.
    public int getDurationMonths() {
        return durationMonths;
    }

    // Giá mỗi tháng (đồng), in ở dòng phụ bên dưới giá gói (0 là không hiện).
    public long getMonthlyPrice() {
        return monthlyPrice;
    }

    // % tiết kiệm so với gói 1 tháng, in badge "-13%" (0 là không hiện nhãn).
    public int getSavingPercent() {
        return savingPercent;
    }

    // Danh sách các dòng quyền lợi của gói, JSP lặp in tích xanh.
    public List<String> getBenefitLines() {
        return benefitLines;
    }

    // Danh sách các dòng quy định đi kèm gói, JSP lặp in điều khoản.
    public List<String> getRuleLines() {
        return ruleLines;
    }

    // Quyền được bấm tiếp tục với gói này tại quầy (false thì vô hiệu hóa nút).
    public boolean isContinueAllowed() {
        return isContinueAllowed;
    }

    // Lý do không cho chọn gói tại quầy, hiện ở tooltip của nút bị khóa.
    public String getBlockedReason() {
        return blockedReason;
    }
}
