package com.gms.service;

import com.gms.dao.PackageDAO;
import com.gms.dto.ManagerPackageRowView;
import com.gms.dto.PackageFormInput;
import com.gms.dto.ServiceResult;
import com.gms.entity.Package;
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
}
