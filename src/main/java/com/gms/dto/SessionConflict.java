package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/** Dữ liệu BF-05: DAO đọc, Service xử lý, JSP hiển thị. */
public class SessionConflict {
    // Sessions.SessionId: mã buổi cần PT xử lý.
    private final int sessionId;
    // TimeSlots.SlotDate/StartTime/EndTime: ngày và giờ thực tế của buổi Scheduled.
    private final LocalDate date;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public SessionConflict(int sessionId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        this.sessionId = sessionId;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public int getSessionId() {
        return sessionId;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

}
