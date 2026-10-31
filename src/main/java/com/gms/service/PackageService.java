package com.gms.service;

import com.gms.dao.PackageDAO;
import com.gms.dto.ManagerPackageRowView;
import com.gms.dto.PackageFormInput;
import com.gms.dto.ServiceResult;
import com.gms.entity.Package;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Service quản lý danh mục gói tập và gói PT dành cho Quản lý (#17, #18, #19, #20, UC-10).
 * Đảm bảo các ràng buộc nghiệp vụ DBR-06, DBR-11, DBR-15, P-04, P-21 và kiểm soát đồng thời (MSG22).
 */
public class PackageService {

    private final PackageDAO packageDAO;
    private final AuditLogService auditLogService;

    public PackageService() {
        this(new PackageDAO(), new AuditLogService());
    }

    public PackageService(PackageDAO packageDAO, AuditLogService auditLogService) {
        this.packageDAO = packageDAO;
        this.auditLogService = auditLogService;
    }

    public PackageDAO getPackageDAO() {
        return packageDAO;
    }

    public AuditLogService getAuditLogService() {
        return auditLogService;
    }

    /**
     * Lấy danh sách gói tập hội viên & vé ngày cho màn hình #17.
     *
     * @param keyword từ khóa tìm kiếm (tên gói)
     * @param status trạng thái lọc ("all", "active", "inactive")
     * @return danh sách hiển thị
     */
    public ServiceResult<List<ManagerPackageRowView>> getMembershipPackages(String keyword, String status) {
        try {
            List<ManagerPackageRowView> list = packageDAO.findManagerPackages("MEMBERSHIP", keyword, status, null);
            return ServiceResult.ok(list);
        } catch (SQLException e) {
            return ServiceResult.fail("Không thể tải danh sách gói hội viên: " + e.getMessage());
        }
    }

    /**
     * Lấy danh sách gói PT (PT 1:1, PT 1:2, Buổi PT lẻ) cho màn hình #19.
     *
     * @param keyword từ khóa tìm kiếm
     * @param status trạng thái lọc ("all", "active", "inactive")
     * @param ptType phân loại gói PT ("ALL", "PTSingle", "PTCouple", "SingleSession")
     * @return danh sách hiển thị
     */
    public ServiceResult<List<ManagerPackageRowView>> getPtPackages(String keyword, String status, String ptType) {
        try {
            List<ManagerPackageRowView> list = packageDAO.findManagerPackages("PT", keyword, status, ptType);
            return ServiceResult.ok(list);
        } catch (SQLException e) {
            return ServiceResult.fail("Không thể tải danh sách gói PT: " + e.getMessage());
        }
    }

    /**
     * Lấy chi tiết gói tập để đưa vào form chỉnh sửa (#18, #20).
     *
     * @param packageId ID gói tập cần tải
     * @return đối tượng Package kèm RowVer
     */
    public ServiceResult<Package> getPackageForEdit(int packageId) {
        try {
            Package pkg = packageDAO.findById(packageId);
            if (pkg == null) {
                return ServiceResult.fail("Gói dịch vụ không tồn tại hoặc đã bị xóa.");
            }
            return ServiceResult.ok(pkg);
        } catch (SQLException e) {
            return ServiceResult.fail("Không thể tải thông tin gói dịch vụ: " + e.getMessage());
        }
    }

    /**
     * Đếm số lượt đăng ký đang chạy hoặc tạm dừng của gói tập.
     */
    public int countRunningOrders(int packageId) {
        try {
            return packageDAO.countRunningOrders(packageId);
        } catch (SQLException e) {
            return 0;
        }
    }
}
