package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * KHỐI "CHUYỂN PT" của hộp Xử lý buổi: các PT khác đang trống ĐÚNG ngày giờ của buổi.
 * Service điền: PtSessionHandleService.getHandleForm B5 (PtSessionHandleDAO.findTransferSlots, lọc whyNotAllowed).
 * JSP đọc: _schedule-handle-transfer.jspf (handle.transfer.slots) và _schedule-handle.jspf (chữ "N PT trống").
 */
public class PtHandleTransferBlock {

    // ← PT nhận thay được: radio timeSlotId = slot.timeSlotId · tên slot.ptName
    private List<PtHandleSlot> slots = new ArrayList<>();


    public List<PtHandleSlot> getSlots() { return slots; }
    public void setSlots(List<PtHandleSlot> slots) { this.slots = slots; }
}
