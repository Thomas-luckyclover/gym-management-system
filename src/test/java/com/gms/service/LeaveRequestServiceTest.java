package com.gms.service;

import com.gms.entity.LeaveRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử đơn vị cho LeaveRequestService (BF-05, UC-14, UC-15, JOB-07).
 */
class LeaveRequestServiceTest {

    private final LeaveRequestService service = new LeaveRequestService();

    @Test
    void serviceInstanceIsNotNull() {
        assertNotNull(service);
    }
}
