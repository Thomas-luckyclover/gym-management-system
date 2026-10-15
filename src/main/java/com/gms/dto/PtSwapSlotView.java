package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** DAO đọc slot công bố; Service lọc điều kiện và Servlet trả JSON cho hộp đổi buổi. */
public class PtSwapSlotView {
    private final int timeSlotId;
    private final LocalDate slotDate;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public PtSwapSlotView(int timeSlotId, LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        this.timeSlotId = timeSlotId;
        this.slotDate = slotDate;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getTimeSlotId() { return timeSlotId; }
    public LocalDate getSlotDate() { return slotDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}
