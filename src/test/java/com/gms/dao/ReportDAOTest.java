package com.gms.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportDAOTest {

    @Test
    @DisplayName("Quy đổi mã Action kỹ thuật sang nhãn tiếng Việt đồng nhất (GBR-01)")
    void resolveActionLabel_MapsTechnicalCodesToVietnamese() {
        assertEquals("Tạo mới gói dịch vụ", ReportDAO.resolveActionLabel("CREATE", "Packages"));
        assertEquals("Cập nhật thông tin gói dịch vụ", ReportDAO.resolveActionLabel("UPDATE", "Packages"));
        assertEquals("Đổi giá gói dịch vụ", ReportDAO.resolveActionLabel("UPDATE_PRICE", "Packages"));
        assertEquals("Mở bán lại gói dịch vụ", ReportDAO.resolveActionLabel("ACTIVATE", "Packages"));
        assertEquals("Ngừng bán gói", ReportDAO.resolveActionLabel("DEACTIVATE", "Packages"));
        assertEquals("Khóa tạm tài khoản (sai mật khẩu)", ReportDAO.resolveActionLabel("AccountLocked", "Account"));
        assertEquals("Bảo lưu gói tập", ReportDAO.resolveActionLabel("FREEZE_MEMBERSHIP", "Membership"));
        assertEquals("Tự động chốt buổi PT cuối ngày", ReportDAO.resolveActionLabel("JOB-04", "Sessions"));
        assertEquals("Tự động chốt buổi PT cuối ngày",
                ReportDAO.resolveActionLabel("Tự động chốt buổi PT cuối ngày (JOB-04)", "Sessions"));
        assertEquals("Tự động đóng gói tập hết hạn", ReportDAO.resolveActionLabel("JOB-02", "Subscriptions"));
        assertEquals("Duyệt yêu cầu bảo trì", ReportDAO.resolveActionLabel("Duyệt yêu cầu bảo trì", "Maintenance"));
        assertEquals("Thao tác khác", ReportDAO.resolveActionLabel(null, "Account"));
    }

    @Test
    @DisplayName("Quy đổi ActingRole DB sang tên vai trò tiếng Việt (GBR-01)")
    void resolveRoleLabel_MapsDbRolesToVietnamese() {
        assertEquals("Quản trị viên", ReportDAO.resolveRoleLabel("Admin"));
        assertEquals("Quản lý", ReportDAO.resolveRoleLabel("Manager"));
        assertEquals("Lễ tân", ReportDAO.resolveRoleLabel("Receptionist"));
        assertEquals("Huấn luyện viên", ReportDAO.resolveRoleLabel("PT"));
        assertEquals("Hội viên", ReportDAO.resolveRoleLabel("Customer"));
        assertEquals("Hệ thống", ReportDAO.resolveRoleLabel(null));
        assertEquals("Hệ thống", ReportDAO.resolveRoleLabel("System"));
    }
}

