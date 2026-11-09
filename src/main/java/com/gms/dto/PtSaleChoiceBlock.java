package com.gms.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * KHỐI "GÓI & PT" (bước 1, cột trái) + phần tóm tắt gói / PT / kỳ ở cột "Đơn bán".
 * Service điền: PtSaleService.newView (mã đã chọn, ngày) → loadPackage → loadPt.
 * JSP đọc: _pt-sale-step1.jspf (sale.choice.xxx) và pt-sales.jsp (tóm tắt Đơn bán).
 */
public class PtSaleChoiceBlock {

    // ===== ĐANG CHỌN (đọc từ URL ?package=&pt=&start=) =====

    // ← ?package= → radio gói được tích sẵn
    private Integer packageId;

    // ← ?pt= → radio PT được tích sẵn
    private Integer ptId;

    // ← ?start= (trống = ngày mai) → ô "Ngày bắt đầu kỳ"
    private LocalDate startDate;


    // ===== DANH SÁCH ĐỂ CHỌN =====

    // ← loadPackage ← PtSaleDAO.findPackages → mỗi gói 1 ô (tên, giá · số tháng · 1/2 người)
    private List<PtSaleOption> packages = new ArrayList<>();

    // ← loadPt ← PtSaleDAO.findPts → mỗi PT 1 nút (avatar chữ cái + tên). Chưa chọn gói → rỗng (ẩn khối PT)
    private List<PtSaleOption> pts = new ArrayList<>();

    // ← newView: hôm nay → min của ô ngày
    private LocalDate minStart;

    // ← newView: hôm nay + START_DAYS_AHEAD ngày → max của ô ngày
    private LocalDate maxStart;

    // ← loadPackage / loadPt: gói ngừng bán, PT không nhận gói, ngày ngoài khoảng → dòng đỏ dưới khối
    private String choiceError;


    // ===== GÓI + PT ĐÃ CHỌN (tóm tắt ở cột "Đơn bán") =====

    // ← loadPackage: gói PTCouple → hiện ô tìm "Người tập cùng"
    private boolean couple;

    // ← loadPackage → dòng "Gói"
    private String packageName;

    // ← loadPackage (giá đã gồm VAT) → "Phải thu", giá 1 buổi
    private long price;

    // ← loadPackage → fillVatPreview: P-08 hiện tại (đơn chưa có, số tạm; đọc lỗi = null) → dòng "Thuế VAT x%"
    private Integer vatRate;

    // ← fillVatPreview = vatAmount(giá, P-08), cùng hàm lúc hold lưu vào đơn → dòng "Thuế VAT x%"
    private Long vatAmount;

    // ← fillVatPreview = giá − vatAmount → dòng "Trước thuế VAT"
    private Long preTaxAmount;

    // ← loadPackage → "… · N tháng", tính ngày hết kỳ
    private int months;

    // ← loadPt → dòng "PT", câu nhắc "PT chưa công bố…"
    private String ptName;

    // ← loadPt → (không in) hoa hồng PT
    private int commissionRate;

    // ← startDate + months → dòng "Kỳ: dd/MM – dd/MM", hết kỳ của lịch tập
    private LocalDate endDate;


    public Integer getPackageId() { return packageId; }
    public void setPackageId(Integer packageId) { this.packageId = packageId; }

    public Integer getPtId() { return ptId; }
    public void setPtId(Integer ptId) { this.ptId = ptId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public List<PtSaleOption> getPackages() { return packages; }
    public void setPackages(List<PtSaleOption> packages) { this.packages = packages; }

    public List<PtSaleOption> getPts() { return pts; }
    public void setPts(List<PtSaleOption> pts) { this.pts = pts; }

    public LocalDate getMinStart() { return minStart; }
    public void setMinStart(LocalDate minStart) { this.minStart = minStart; }

    public LocalDate getMaxStart() { return maxStart; }
    public void setMaxStart(LocalDate maxStart) { this.maxStart = maxStart; }

    public String getChoiceError() { return choiceError; }
    public void setChoiceError(String choiceError) { this.choiceError = choiceError; }

    public boolean isCouple() { return couple; }
    public void setCouple(boolean couple) { this.couple = couple; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public Integer getVatRate() { return vatRate; }
    public void setVatRate(Integer vatRate) { this.vatRate = vatRate; }

    public Long getVatAmount() { return vatAmount; }
    public void setVatAmount(Long vatAmount) { this.vatAmount = vatAmount; }

    public Long getPreTaxAmount() { return preTaxAmount; }
    public void setPreTaxAmount(Long preTaxAmount) { this.preTaxAmount = preTaxAmount; }

    public int getMonths() { return months; }
    public void setMonths(int months) { this.months = months; }

    public String getPtName() { return ptName; }
    public void setPtName(String ptName) { this.ptName = ptName; }

    public int getCommissionRate() { return commissionRate; }
    public void setCommissionRate(int commissionRate) { this.commissionRate = commissionRate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
