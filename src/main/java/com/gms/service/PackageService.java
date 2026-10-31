package com.gms.service;

import com.gms.dao.PackageDAO;
import com.gms.dto.ManagerPackageRowView;
import com.gms.dto.PackageFormInput;
import com.gms.dto.ServiceResult;
import com.gms.entity.Package;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service quản lý danh mục gói tập và gói PT dành cho Quản lý (#17, #18, #19, #20, UC-10).
 * Đảm bảo các ràng buộc nghiệp vụ DBR-06, DBR-11, DBR-15, P-04, P-21 và kiểm soát đồng thời (MSG22).
 */
public class PackageService {

    public static final Set<Integer> ALLOWED_MEMBERSHIP_MONTHS = Set.of(1, 3, 6, 12, 24, 36);
    public static final Set<Integer> ALLOWED_PT_MONTHS = Set.of(1, 3, 6, 12);

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

    // ===== Validate nghiệp vụ cho Gói Hội viên & Vé ngày (DBR-06, DBR-11, DBR-15, P-04) =====

    /**
     * Kiểm tra tính hợp lệ của biểu mẫu gói hội viên / vé ngày.
     *
     * @param input dữ liệu biểu mẫu
     * @param conn kết nối cơ sở dữ liệu để kiểm tra duy nhất tên gói
     * @return Map chứa các lỗi theo từng ô nhập (rỗng nếu dữ liệu hợp lệ)
     * @throws SQLException lỗi truy vấn DB
     */
    public Map<String, String> validateMembershipForm(PackageFormInput input, Connection conn) throws SQLException {
        Map<String, String> errors = new LinkedHashMap<>();

        validateCommonFields(input, errors, conn);

        String type = input.getType();
        if (type == null || type.isBlank()) {
            errors.put("type", "Vui lòng chọn loại gói dịch vụ.");
            return errors;
        }

        if (!"Membership".equalsIgnoreCase(type) && !"DayPass".equalsIgnoreCase(type)) {
            errors.put("type", "Loại gói không hợp lệ. Chỉ chấp nhận Gói hội viên hoặc Vé ngày.");
            return errors;
        }

        Integer durationVal = input.parseDurationValue();
        String durationUnit = input.getDurationUnit();

        if ("DayPass".equalsIgnoreCase(type)) {
            if (!"Day".equalsIgnoreCase(durationUnit) || durationVal == null || durationVal != 1) {
                errors.put("durationValue", "Vé ngày bắt buộc có thời hạn là 1 ngày (P-04).");
            }
        } else {
            // Membership
            if (!"Month".equalsIgnoreCase(durationUnit)) {
                errors.put("durationUnit", "Gói hội viên bắt buộc tính thời hạn theo tháng.");
            }
            if (durationVal == null || !ALLOWED_MEMBERSHIP_MONTHS.contains(durationVal)) {
                errors.put("durationValue", "Thời hạn gói hội viên chỉ chấp nhận: 1, 3, 6, 12, 24 hoặc 36 tháng (P-04).");
            }
        }

        return errors;
    }

    /**
     * Kiểm tra các trường chung: Tên gói, Giá, Mô tả, Quyền lợi (DBR-06, DBR-11, DBR-15).
     */
    protected void validateCommonFields(PackageFormInput input, Map<String, String> errors, Connection conn) throws SQLException {
        // 1. Tên gói (DBR-06)
        String name = input.getName();
        if (name == null || name.isBlank()) {
            errors.put("name", "Vui lòng nhập tên gói dịch vụ.");
        } else if (name.length() < 3 || name.length() > 50) {
            errors.put("name", "Tên gói phải từ 3 đến 50 ký tự (DBR-06).");
        } else if (input.isActive()) {
            // Kiểm tra trùng tên với gói đang bán khác
            boolean exists = packageDAO.existsActiveByName(conn, name, input.getPackageId());
            if (exists) {
                errors.put("name", "Tên gói này đang được sử dụng bởi một gói khác đang bán.");
            }
        }

        // 2. Giá gói (DBR-11)
        String priceStr = input.getPrice();
        if (priceStr == null || priceStr.isBlank()) {
            errors.put("price", "Vui lòng nhập giá gói dịch vụ.");
        } else {
            try {
                BigDecimal price = input.parsePrice();
                if (price == null) {
                    errors.put("price", "Giá gói không đúng định dạng số.");
                } else {
                    long priceVal = price.longValue();
                    if (priceVal < 10_000 || priceVal > 500_000_000) {
                        errors.put("price", "Giá gói phải từ 10.000 ₫ đến 500.000.000 ₫ (DBR-11).");
                    } else if (priceVal % 1_000 != 0) {
                        errors.put("price", "Giá gói phải là bội số của 1.000 ₫.");
                    }
                }
            } catch (Exception e) {
                errors.put("price", "Giá gói không đúng định dạng số.");
            }
        }

        // 3. Mô tả (tối đa 255 ký tự)
        String desc = input.getDescription();
        if (desc != null && desc.length() > 255) {
            errors.put("description", "Mô tả không được vượt quá 255 ký tự.");
        }

        // 4. Quyền lợi (DBR-15)
        String benefits = input.getBenefits();
        if (benefits != null && !benefits.isBlank()) {
            if (benefits.length() > 1000) {
                errors.put("benefits", "Tổng độ dài các dòng quyền lợi không được vượt quá 1000 ký tự (DBR-15).");
            } else {
                List<String> lines = input.getBenefitLines();
                if (lines.size() > 10) {
                    errors.put("benefits", "Tối đa 10 dòng quyền lợi (DBR-15). Hiện có " + lines.size() + " dòng.");
                } else {
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (line.length() < 3 || line.length() > 100) {
                            errors.put("benefits", "Dòng quyền lợi thứ " + (i + 1) + " phải từ 3 đến 100 ký tự (DBR-15).");
                            break;
                        }
                    }
                }
            }
        }
    }
}
