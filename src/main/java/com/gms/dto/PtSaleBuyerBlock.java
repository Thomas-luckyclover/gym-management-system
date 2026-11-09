package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * KHỐI "KHÁCH MUA GÓI" (cột "Đơn bán"): ô tìm khách, thẻ người đứng tên, thẻ người tập cùng (gói đôi).
 * Service điền: PtSaleService.newView (mã khách trên URL) → loadHolder → loadPartner.
 * JSP đọc: _pt-sale-customer-box.jspf (sale.buyer.xxx) → _sale-customer.jspf vẽ 1 thẻ khách.
 */
public class PtSaleBuyerBlock {

    // ===== ĐANG CHỌN (đọc từ URL ?customer=&partner=&q=) =====

    // ← ?customer= → ô ẩn "customer" của các form, link "Đổi khách"
    private Integer customerId;

    // ← ?partner= → ô ẩn "partner"
    private Integer partnerId;

    // ← ?q= (chỉ khi tắt JS, Enter ở ô tìm) → chữ trong ô tìm
    private String searchQuery;


    // ===== KẾT QUẢ =====

    // ← loadHolder ← PtSaleDAO.searchCustomers (khi có q) → danh sách kết quả, mỗi dòng "Mua được" / lý do đỏ
    private List<PtSaleCustomer> searchResults = new ArrayList<>();

    // ← loadHolder ← PtSaleDAO.findCustomer → thẻ "Người đứng tên" (blockReason ≠ null = không bán được, viền đỏ)
    private PtSaleCustomer holder;

    // ← loadPartner ← PtSaleDAO.findCustomer → thẻ "Người tập cùng" (chỉ gói đôi)
    private PtSaleCustomer partner;

    // ← loadHolder: "Không tìm thấy khách này. Tìm lại." → khung đỏ dưới ô tìm
    private String holderError;

    // ← loadPartner: chưa chọn người tập cùng / trùng người đứng tên… → dòng nhắc dưới ô tìm người tập cùng
    private String partnerError;


    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public Integer getPartnerId() { return partnerId; }
    public void setPartnerId(Integer partnerId) { this.partnerId = partnerId; }

    public String getSearchQuery() { return searchQuery; }
    public void setSearchQuery(String searchQuery) { this.searchQuery = searchQuery; }

    public List<PtSaleCustomer> getSearchResults() { return searchResults; }
    public void setSearchResults(List<PtSaleCustomer> searchResults) { this.searchResults = searchResults; }

    public PtSaleCustomer getHolder() { return holder; }
    public void setHolder(PtSaleCustomer holder) { this.holder = holder; }

    public PtSaleCustomer getPartner() { return partner; }
    public void setPartner(PtSaleCustomer partner) { this.partner = partner; }

    public String getHolderError() { return holderError; }
    public void setHolderError(String holderError) { this.holderError = holderError; }

    public String getPartnerError() { return partnerError; }
    public void setPartnerError(String partnerError) { this.partnerError = partnerError; }
}
