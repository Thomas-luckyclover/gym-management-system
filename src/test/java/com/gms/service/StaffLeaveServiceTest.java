package com.gms.service;

import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.LeaveRequestDAO;
import com.gms.dao.PersonDAO;
import com.gms.entity.Account;
import com.gms.entity.Employee;
import com.gms.util.DateTimeUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Dự báo khi mở form phải giống API sau khi người dùng chọn lại ngày/ca. */
class StaffLeaveServiceTest {

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"PT", "RECEPTIONIST"})
    void formAndPreviewOnlyDeductTheApplicantsPosition(Role role) throws Exception {
        LeaveRequestDAO dao = mock(LeaveRequestDAO.class);
        AccountDAO accounts = mock(AccountDAO.class);
        EmployeeDAO employees = mock(EmployeeDAO.class);
        SystemConfigService configs = mock(SystemConfigService.class);
        Employee employee = mock(Employee.class);
        when(employee.getEmployeeId()).thenReturn(50);
        when(employee.getPosition()).thenReturn(role);
        when(accounts.findById(10)).thenReturn(new Account(44, null, Account.Status.ACTIVE));
        when(employees.findByPersonId(44)).thenReturn(employee);
        when(configs.getString(SystemConfigService.LEAVE_SUBMIT_DEADLINE)).thenReturn("15:00");
        when(configs.getInt(SystemConfigService.MIN_PT_PER_SHIFT)).thenReturn(2);
        when(configs.getInt(SystemConfigService.MIN_RECEPTIONIST_PER_SHIFT)).thenReturn(1);
        var tomorrow = DateTimeUtil.today().plusDays(1);
        when(dao.countOnDutyAfterApproval(tomorrow, "FullDay", "PT", 0)).thenReturn(5);
        when(dao.countOnDutyAfterApproval(tomorrow, "FullDay", "Receptionist", 0)).thenReturn(3);
        StaffLeaveService service = new StaffLeaveService(dao, accounts, employees, mock(PersonDAO.class), configs,
                mock(AuditLogService.class), mock(NotificationService.class));

        var formResult = service.getStaffLeaveForm(10);
        var previewResult = service.previewStaffing(10, tomorrow, "FullDay");
        assertTrue(formResult.isSuccess());
        assertTrue(previewResult.isSuccess());
        var form = formResult.getData();
        var preview = previewResult.getData();
        assertEquals(form.getRemainingPt(), preview.getRemainingPt());
        assertEquals(form.getRemainingReceptionist(), preview.getRemainingReceptionist());
        assertEquals(role == Role.PT ? 5 : 6, preview.getRemainingPt());
        assertEquals(role == Role.RECEPTIONIST ? 3 : 4, preview.getRemainingReceptionist());
        assertEquals(2, preview.getMinPt());
        assertEquals(1, preview.getMinReceptionist());
    }
}
