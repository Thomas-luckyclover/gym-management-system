package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.PackageDAO;
import com.gms.dto.ManagerPackageRowView;
import com.gms.dto.PackageFormInput;
import com.gms.dto.ServiceResult;
import com.gms.entity.Package;
import com.gms.util.DBContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
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

    protected Connection getConnection() throws SQLException {
        return DBContext.getConnection();
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

    // ===== Thêm mới gói dịch vụ (Commit 15) =====

    /**
     * Tạo mới gói hội viên / vé ngày (#18).
     */
    public ServiceResult<Integer> createMembershipPackage(int accountId, PackageFormInput input) {
        return createPackageInternal(accountId, input, false);
    }

    /**
     * Tạo mới gói PT (#20).
     */
    public ServiceResult<Integer> createPtPackage(int accountId, PackageFormInput input) {
        return createPackageInternal(accountId, input, true);
    }

    private ServiceResult<Integer> createPackageInternal(int accountId, PackageFormInput input, boolean isPt) {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, String> errors = isPt ? validatePtForm(input, conn) : validateMembershipForm(input, conn);
                if (!errors.isEmpty()) {
                    conn.rollback();
                    return ServiceResult.failFields(errors);
                }

                Package pkg = mapInputToPackage(input);
                int newId = packageDAO.insert(conn, pkg);

                // Ghi nhận AuditLog tạo gói
                auditLogService.logInTransaction(
                        conn,
                        accountId,
                        Role.MANAGER,
                        "CREATE",
                        "Packages",
                        newId,
                        null,
                        null,
                        pkg.getPackageName(),
                        isPt ? "Tạo mới gói PT" : "Tạo mới gói hội viên"
                );

                conn.commit();
                return ServiceResult.ok(newId, "Thêm gói dịch vụ thành công.");
            } catch (SQLException e) {
                conn.rollback();
                return ServiceResult.fail("Lỗi khi thêm gói dịch vụ: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return ServiceResult.fail("Lỗi kết nối cơ sở dữ liệu: " + e.getMessage());
        }
    }

    // ===== Chỉnh sửa gói dịch vụ (Commit 16) =====

    /**
     * Cập nhật thông tin gói hội viên / vé ngày (#18).
     */
    public ServiceResult<Integer> updateMembershipPackage(int accountId, PackageFormInput input) {
        return updatePackageInternal(accountId, input, false);
    }

    /**
     * Cập nhật thông tin gói PT / buổi lẻ (#20).
     */
    public ServiceResult<Integer> updatePtPackage(int accountId, PackageFormInput input) {
        return updatePackageInternal(accountId, input, true);
    }

    private ServiceResult<Integer> updatePackageInternal(int accountId, PackageFormInput input, boolean isPt) {
        if (input.getPackageId() == null || input.getPackageId() <= 0) {
            return ServiceResult.fail("Mã gói dịch vụ không hợp lệ.");
        }
        if (input.getRowVersion() == null || input.getRowVersion().isBlank()) {
            return ServiceResult.fail("Thiếu thông tin phiên bản dữ liệu (RowVer).");
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                Map<String, String> errors = isPt ? validatePtForm(input, conn) : validateMembershipForm(input, conn);
                if (!errors.isEmpty()) {
                    conn.rollback();
                    return ServiceResult.failFields(errors);
                }

                Package oldPkg = packageDAO.findById(conn, input.getPackageId());
                if (oldPkg == null) {
                    conn.rollback();
                    return ServiceResult.fail("Gói dịch vụ không tồn tại hoặc đã bị xóa.");
                }

                Package pkg = mapInputToPackage(input);
                boolean updated = packageDAO.update(conn, pkg);

                if (!updated) {
                    conn.rollback();
                    return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
                }

                // Ghi nhận AuditLog: Nếu đổi giá → ghi log giá, nếu đổi trường khác → ghi log cập nhật
                if (oldPkg.getPrice() != pkg.getPrice()) {
                    auditLogService.logInTransaction(
                            conn,
                            accountId,
                            Role.MANAGER,
                            "UPDATE_PRICE",
                            "Packages",
                            pkg.getPackageId(),
                            "Price",
                            String.valueOf(oldPkg.getPrice()),
                            String.valueOf(pkg.getPrice()),
                            "Quản lý điều chỉnh giá gói"
                    );
                }

                auditLogService.logInTransaction(
                        conn,
                        accountId,
                        Role.MANAGER,
                        "UPDATE",
                        "Packages",
                        pkg.getPackageId(),
                        null,
                        oldPkg.getPackageName(),
                        pkg.getPackageName(),
                        "Quản lý cập nhật thông tin gói"
                );

                conn.commit();
                return ServiceResult.ok(pkg.getPackageId(), Messages.MSG23_SAVED);
            } catch (SQLException e) {
                conn.rollback();
                return ServiceResult.fail("Lỗi khi cập nhật gói dịch vụ: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return ServiceResult.fail("Lỗi kết nối cơ sở dữ liệu: " + e.getMessage());
        }
    }

    // ===== Bật/Tắt trạng thái kinh doanh của gói (Commit 17) =====

    /**
     * Thay đổi trạng thái kinh doanh của gói (Mở bán / Ngừng bán) kèm RowVer.
     *
     * @param accountId tài khoản Quản lý thực hiện
     * @param packageId ID gói dịch vụ
     * @param targetActive trạng thái mong muốn (true = mở bán, false = ngừng bán)
     * @param rowVerBase64 chuỗi RowVer Base64 cũ để kiểm soát đồng thời
     * @param reason lý do thay đổi trạng thái
     * @return ServiceResult thành công hoặc thất bại
     */
    public ServiceResult<Void> togglePackageStatus(int accountId, int packageId, boolean targetActive, String rowVerBase64, String reason) {
        if (packageId <= 0) {
            return ServiceResult.fail("Mã gói dịch vụ không hợp lệ.");
        }
        if (rowVerBase64 == null || rowVerBase64.isBlank()) {
            return ServiceResult.fail("Thiếu thông tin phiên bản dữ liệu (RowVer).");
        }

        byte[] expectedRowVer;
        try {
            expectedRowVer = Base64.getDecoder().decode(rowVerBase64.trim());
        } catch (IllegalArgumentException e) {
            return ServiceResult.fail("Phiên bản dữ liệu không hợp lệ.");
        }

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                Package pkg = packageDAO.findById(conn, packageId);
                if (pkg == null) {
                    conn.rollback();
                    return ServiceResult.fail("Gói dịch vụ không tồn tại hoặc đã bị xóa.");
                }

                if (pkg.isActive() == targetActive) {
                    conn.rollback();
                    return ServiceResult.ok(null, "Trạng thái gói không thay đổi.");
                }

                if (targetActive) {
                    // Mở bán lại: kiểm tra trùng tên với gói khác đang bán
                    boolean nameDuplicate = packageDAO.existsActiveByName(conn, pkg.getPackageName(), packageId);
                    if (nameDuplicate) {
                        conn.rollback();
                        return ServiceResult.fail("Không thể mở bán lại: Hiện đang có một gói khác cùng tên đang bán.");
                    }

                    // Kiểm tra thời hạn có còn hợp lệ theo quy định hiện hành P-04 / P-21 hay không
                    if (!isDurationValidForReactivation(pkg)) {
                        conn.rollback();
                        return ServiceResult.fail("Không thể mở bán lại: Thời hạn gói (" + pkg.getDurationValue() + " " + pkg.getDurationUnit() + ") không nằm trong danh mục thời hạn cho phép hiện tại.");
                    }
                }

                boolean updated = packageDAO.updateActiveStatus(conn, packageId, targetActive, expectedRowVer);
                if (!updated) {
                    conn.rollback();
                    return ServiceResult.fail(Messages.MSG22_CONCURRENT_EDIT);
                }

                // Ghi AuditLog
                String action = targetActive ? "ACTIVATE" : "DEACTIVATE";
                String defaultNote = targetActive ? "Quản lý mở bán lại gói" : "Quản lý ngừng bán gói";
                auditLogService.logInTransaction(
                        conn,
                        accountId,
                        Role.MANAGER,
                        action,
                        "Packages",
                        packageId,
                        "IsActive",
                        String.valueOf(!targetActive),
                        String.valueOf(targetActive),
                        reason != null && !reason.isBlank() ? reason : defaultNote
                );

                conn.commit();
                return ServiceResult.ok(null, targetActive ? "Đã mở bán lại gói dịch vụ." : "Đã ngừng bán gói dịch vụ.");
            } catch (SQLException e) {
                conn.rollback();
                return ServiceResult.fail("Lỗi cập nhật trạng thái gói: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return ServiceResult.fail("Lỗi kết nối cơ sở dữ liệu: " + e.getMessage());
        }
    }

    private boolean isDurationValidForReactivation(Package pkg) {
        if (pkg.getPackageType() == null) {
            return false;
        }
        return switch (pkg.getPackageType()) {
            case MEMBERSHIP -> "Month".equalsIgnoreCase(pkg.getDurationUnit())
                    && pkg.getDurationValue() != null
                    && ALLOWED_MEMBERSHIP_MONTHS.contains(pkg.getDurationValue());
            case DAY_PASS -> "Day".equalsIgnoreCase(pkg.getDurationUnit())
                    && pkg.getDurationValue() != null
                    && pkg.getDurationValue() == 1;
            case PT_SINGLE, PT_COUPLE -> "Month".equalsIgnoreCase(pkg.getDurationUnit())
                    && pkg.getDurationValue() != null
                    && ALLOWED_PT_MONTHS.contains(pkg.getDurationValue());
            case SINGLE_SESSION -> pkg.getDurationUnit() == null && pkg.getDurationValue() == null;
        };
    }

    // ===== Validate nghiệp vụ cho Gói Hội viên & Vé ngày (DBR-06, DBR-11, DBR-15, P-04) =====

    /**
     * Kiểm tra tính hợp lệ của biểu mẫu gói hội viên / vé ngày.
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

    // ===== Validate nghiệp vụ cho Gói PT & Buổi lẻ (DBR-06, DBR-11, DBR-15, P-21, CK_Packages_EstSession) =====

    /**
     * Kiểm tra tính hợp lệ của biểu mẫu gói PT / buổi lẻ.
     */
    public Map<String, String> validatePtForm(PackageFormInput input, Connection conn) throws SQLException {
        Map<String, String> errors = new LinkedHashMap<>();

        validateCommonFields(input, errors, conn);

        String type = input.getType();
        if (type == null || type.isBlank()) {
            errors.put("type", "Vui lòng chọn loại gói PT.");
            return errors;
        }

        boolean isNew = (input.getPackageId() == null || input.getPackageId() == 0);

        if (isNew) {
            // Thêm mới: chỉ cho phép tạo PTSingle hoặc PTCouple
            if (!"PTSingle".equalsIgnoreCase(type) && !"PTCouple".equalsIgnoreCase(type)) {
                errors.put("type", "Chỉ được phép tạo mới gói PT 1:1 hoặc PT 1:2.");
                return errors;
            }
        } else {
            // Chỉnh sửa: chấp nhận PTSingle, PTCouple hoặc SingleSession
            if (!"PTSingle".equalsIgnoreCase(type) && !"PTCouple".equalsIgnoreCase(type) && !"SingleSession".equalsIgnoreCase(type)) {
                errors.put("type", "Loại gói PT không hợp lệ.");
                return errors;
            }
        }

        if ("SingleSession".equalsIgnoreCase(type)) {
            // Buổi PT lẻ: durationUnit/durationValue và estimatedSessionCount phải để trống
            if (input.getDurationUnit() != null && !input.getDurationUnit().isBlank()) {
                input.setDurationUnit(null);
            }
            if (input.getDurationValue() != null && !input.getDurationValue().isBlank()) {
                input.setDurationValue(null);
            }
            if (input.getEstimatedSessionCount() != null && !input.getEstimatedSessionCount().isBlank()) {
                input.setEstimatedSessionCount(null);
            }
        } else {
            // PTSingle hoặc PTCouple: thời hạn theo P-21 (1, 3, 6, 12 tháng)
            String durationUnit = input.getDurationUnit();
            if (!"Month".equalsIgnoreCase(durationUnit)) {
                errors.put("durationUnit", "Gói PT bắt buộc tính thời hạn theo tháng.");
            }

            Integer durationVal = input.parseDurationValue();
            if (durationVal == null || !ALLOWED_PT_MONTHS.contains(durationVal)) {
                errors.put("durationValue", "Thời hạn gói PT chỉ chấp nhận: 1, 3, 6 hoặc 12 tháng (P-21).");
            }

            // Số buổi tập ước tính: 1 - 255 buổi (DB TINYINT, CK_Packages_EstSession)
            String sessionStr = input.getEstimatedSessionCount();
            if (sessionStr == null || sessionStr.isBlank()) {
                errors.put("estimatedSessionCount", "Vui lòng nhập số buổi tập ước tính cho gói PT.");
            } else {
                try {
                    int sessions = Integer.parseInt(sessionStr.trim());
                    if (sessions < 1 || sessions > 255) {
                        errors.put("estimatedSessionCount", "Số buổi tập ước tính phải từ 1 đến 255 buổi.");
                    }
                } catch (NumberFormatException e) {
                    errors.put("estimatedSessionCount", "Số buổi tập ước tính phải là số nguyên hợp lệ.");
                }
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

    private Package mapInputToPackage(PackageFormInput input) {
        Package pkg = new Package(input.getPackageId() != null ? input.getPackageId() : 0);
        pkg.setPackageName(input.getName());
        pkg.setDescription(input.getDescription());
        pkg.setBenefits(input.getBenefits());
        pkg.setPackageType(Package.Type.fromDbValue(input.getType()));
        pkg.setDurationUnit(input.getDurationUnit());
        pkg.setDurationValue(input.parseDurationValue());
        pkg.setEstimatedSessionCount(input.parseEstimatedSessionCount());
        BigDecimal price = input.parsePrice();
        pkg.setPrice(price != null ? price.intValue() : 0);
        pkg.setActive(input.isActive());
        pkg.setUnlocksExerciseLibrary(input.isUnlocksExerciseLibrary());
        pkg.setRowVersion(input.decodeRowVersion());
        return pkg;
    }
}
