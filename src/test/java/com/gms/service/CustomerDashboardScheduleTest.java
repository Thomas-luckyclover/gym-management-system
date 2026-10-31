package com.gms.service;

import com.gms.dto.CustomerDashboardView.ScheduleDay;
import com.gms.dto.CustomerDashboardView.UpcomingSessionRow;
import com.gms.dto.CustomerDashboardView.TrainerContact;
import com.gms.dto.CustomerDashboardView.SubscriptionRow;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerDashboardScheduleTest {

    @Test
    void groupsPackagesByTrainerIdRatherThanDisplayName() {
        SubscriptionRow firstPackage = org.mockito.Mockito.mock(SubscriptionRow.class);
        SubscriptionRow secondPackage = org.mockito.Mockito.mock(SubscriptionRow.class);
        SubscriptionRow otherPackage = org.mockito.Mockito.mock(SubscriptionRow.class);
        org.mockito.Mockito.when(firstPackage.getSubscriptionId()).thenReturn(10);
        org.mockito.Mockito.when(secondPackage.getSubscriptionId()).thenReturn(11);
        org.mockito.Mockito.when(otherPackage.getSubscriptionId()).thenReturn(12);
        List<TrainerContact> trainerRows = List.of(
                new TrainerContact(1, "PT trùng tên", "0901234567", 10),
                new TrainerContact(1, "PT trùng tên", "0901234567", 11),
                new TrainerContact(2, "PT trùng tên", null, 12));

        List<TrainerContact> trainers = new CustomerDashboardService().groupTrainerPackages(
                trainerRows, List.of(firstPackage, secondPackage, otherPackage));

        assertEquals(2, trainers.size());
        assertEquals(List.of(firstPackage, secondPackage), trainers.get(0).getPackages());
        assertEquals(List.of(otherPackage), trainers.get(1).getPackages());
        assertNull(trainers.get(1).getPhoneUrl());
        assertNull(trainers.get(1).getZaloUrl());
    }

    @Test
    void groupsAllSessionsAcrossMonthAndKeepsEmptyDays() {
        LocalDate today = LocalDate.of(2026, 10, 31);
        List<UpcomingSessionRow> sessions = new ArrayList<>();
        // Hơn 5 buổi cùng ngày không được làm mất ngày trống hoặc buổi ở cuối tuần.
        for (int sessionId = 1; sessionId <= 6; sessionId++) {
            sessions.add(new UpcomingSessionRow(sessionId, today.plusDays(1),
                    LocalTime.of(7, 0), LocalTime.of(8, 30), "PT", "Gói PT", false));
        }
        sessions.add(new UpcomingSessionRow(7, today.plusDays(6),
                LocalTime.of(9, 0), LocalTime.of(10, 30), "PT khác", "Buổi lẻ", true));
        List<ScheduleDay> days = new CustomerDashboardService().buildScheduleDays(today, sessions);

        assertEquals(7, days.size());
        assertTrue(days.get(0).isToday());
        assertTrue(days.get(0).getSessions().isEmpty());
        assertEquals(LocalDate.of(2026, 11, 1), days.get(1).getDate());
        assertEquals(6, days.get(1).getSessions().size());
        assertFalse(days.get(6).isToday());
        assertEquals(1, days.get(6).getSessions().size());
        assertTrue(days.get(6).getSessions().get(0).isMissingMembership());
    }

    @Test
    void noBookingsStillHasSevenSelectableDays() {
        List<ScheduleDay> days = new CustomerDashboardService()
                .buildScheduleDays(LocalDate.of(2026, 12, 30), List.of());
        assertEquals(LocalDate.of(2027, 1, 5), days.get(6).getDate());
        for (ScheduleDay day : days) {
            assertTrue(day.getSessions().isEmpty());
        }
    }
}
