package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * MỘT KHUNG MỚI PT muốn công bố (chưa có trong DB nên chưa có TimeSlotId).
 *
 *   Ai tạo?   PtScheduleService.buildDrafts, từ ngày + giờ bắt đầu trình duyệt gửi lên.
 *   Ai dùng?  PtScheduleService.validateDrafts kiểm tra; PtScheduleDAO.insertOrReopenSlot ghi DB.
 *
 * Giờ kết thúc KHÔNG lấy từ trình duyệt: Service tự tính = giờ bắt đầu + P-06.
 * Ví dụ: 27/10, 12:30 và P-06 = 90 phút → endTime = 14:00.
 *   "· IN" cuối mỗi field = JSP Lịch dạy có in field này; "· không in" = chỉ dùng để tính / dựng link.
 */
public class PtSlotDraft {

    private final LocalDate slotDate;    // ngày của khung   · không in (JSP không đọc object này, chỉ Service dùng)
    private final LocalTime startTime;   // giờ bắt đầu, đúng mốc 15 phút   · không in (JSP không đọc object này, chỉ Service dùng)
    private final LocalTime endTime;     // giờ kết thúc do Service tính   · không in (JSP không đọc object này, chỉ Service dùng)

    public PtSlotDraft(LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalDate getSlotDate() { return slotDate; }

    public LocalTime getStartTime() { return startTime; }

    public LocalTime getEndTime() { return endTime; }
}
