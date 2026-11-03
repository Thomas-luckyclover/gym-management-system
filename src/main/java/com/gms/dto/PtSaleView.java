package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * CẢ MÀN BÁN GÓI PT = 4 khối + khung "Đơn đang giữ chỗ". Mỗi khối 1 DTO riêng, đúng 1 vùng trên màn:
 *
 *   ┌──────────────────────────────┬──────────────────────────┐
 *   │ choice  "Gói & PT"           │ buyer   "Khách mua gói"  │
 *   │ plan    "Lịch tập"           │ choice  tóm tắt Đơn bán  │
 *   │                              │ heldOrder đồng hồ + thu tiền (?order=)
 *   └──────────────────────────────┴──────────────────────────┘
 *
 * Service tạo: PtSaleService.prepare (xem) / hold (giữ lịch, đọc lại để kiểm).
 * Servlet: PtSaleServlet.showPage → request.setAttribute("sale", view) → JSP đọc sale.choice.xxx, sale.buyer.xxx …
 */
public class PtSaleView {

    // ← khối "Gói & PT" → _pt-sale-step1.jspf + tóm tắt Đơn bán
    private final PtSaleChoiceBlock choice = new PtSaleChoiceBlock();

    // ← khối "Khách mua gói" → _pt-sale-customer-box.jspf
    private final PtSaleBuyerBlock buyer = new PtSaleBuyerBlock();

    // ← khối "Lịch tập" (+ lưới đang giữ) → _pt-sale-plan.jspf, _pt-sale-held.jspf
    private final PtSalePlanBlock plan = new PtSalePlanBlock();

    // ← khối "Đơn đang giữ" (chỉ trang ?order=) → đồng hồ + form thu tiền
    private final PtSaleHeldBlock heldOrder = new PtSaleHeldBlock();

    // ← loadPendingHolds → khung "Đơn đang giữ chỗ" đầu màn (_pt-sale-holds.jspf)
    private List<PtSalePendingHold> pendingHolds = new ArrayList<>();


    /** Đang ở trang ?order= (đã giữ chỗ) → JSP ẩn bước 1–2, hiện đồng hồ + Thu tiền. */
    public boolean isHeld() {
        return heldOrder.getOrderId() != null;
    }

    /** Đủ để Giữ lịch: chưa giữ, có ≥ 1 buổi, lịch không lỗi (PtSaleService.firstProblem dùng). */
    public boolean isReady() {
        return !isHeld() && plan.getSessionCount() > 0 && plan.getPlanError() == null;
    }


    public PtSaleChoiceBlock getChoice() { return choice; }
    public PtSaleBuyerBlock getBuyer() { return buyer; }
    public PtSalePlanBlock getPlan() { return plan; }
    public PtSaleHeldBlock getHeldOrder() { return heldOrder; }

    public List<PtSalePendingHold> getPendingHolds() { return pendingHolds; }
    public void setPendingHolds(List<PtSalePendingHold> pendingHolds) { this.pendingHolds = pendingHolds; }
}
