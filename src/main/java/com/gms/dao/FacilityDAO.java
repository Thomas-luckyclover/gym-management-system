package com.gms.dao;

import com.gms.entity.EquipmentItem;
import com.gms.entity.Facility;
import com.gms.util.DBContext;
import com.gms.util.PaginationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object: Quản lý truy vấn danh mục dòng máy (Facilities) và tính toán số lượng cá thể con.
 */
public class FacilityDAO {

    private final EquipmentItemDAO equipmentItemDAO = new EquipmentItemDAO();

    /**
     * Lấy danh sách các dòng máy khả dụng (chưa ngừng sử dụng hoàn toàn và IsActive = 1) để lễ tân chọn.
     */
    public List<Facility> findSelectableFacilities() throws SQLException {
        String sql = """
            SELECT f.FacilityId, f.FacilityName, f.Category, f.Location, f.ImageUrl, f.UnitLabel, f.IsActive,
                   ISNULL(ei_stats.TotalCount, 0) AS TotalQuantity,
                   ISNULL(ei_stats.AvailableCount, 0) AS AvailableQuantity,
                   ISNULL(ei_stats.MaintenanceCount, 0) AS PendingQuantity,
                   CASE 
                       WHEN f.IsActive = 0 OR ISNULL(ei_stats.TotalCount, 0) = 0 THEN 'Retired'
                       WHEN ISNULL(ei_stats.AvailableCount, 0) > 0 THEN 'Available'
                       ELSE 'UnderMaintenance'
                   END AS ComputedStatus
            FROM Facilities f
            LEFT JOIN (
                SELECT ei.FacilityId,
                       COUNT(*) AS TotalCount,
                       SUM(CASE WHEN ei.Status = 'Available' AND active_mr.EquipmentItemId IS NULL THEN 1 ELSE 0 END) AS AvailableCount,
                       SUM(CASE WHEN ei.Status = 'UnderMaintenance' OR active_mr.EquipmentItemId IS NOT NULL THEN 1 ELSE 0 END) AS MaintenanceCount
                FROM EquipmentItems ei
                LEFT JOIN (
                    SELECT DISTINCT EquipmentItemId
                    FROM MaintenanceRequests
                    WHERE Status IN ('Open', 'InProgress') AND EquipmentItemId IS NOT NULL
                ) active_mr ON active_mr.EquipmentItemId = ei.EquipmentItemId
                WHERE ei.Status <> 'Retired'
                GROUP BY ei.FacilityId
            ) ei_stats ON ei_stats.FacilityId = f.FacilityId
            WHERE f.IsActive = 1 AND ISNULL(ei_stats.TotalCount, 0) > 0
            ORDER BY f.FacilityName, f.FacilityId
            """;

        List<Facility> facilities = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                facilities.add(mapResultSetToFacility(resultSet));
            }
        }
        return facilities;
    }

    /**
     * Đếm tổng số dòng máy theo từ khóa tìm kiếm (dành cho lễ tân: chỉ IsActive = 1).
     */
    public long countCatalog(String keyword) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM Facilities f WHERE f.IsActive = 1 ");
        List<Object> parameters = new ArrayList<>();
        appendCatalogFilters(sql, parameters, keyword);

        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setCatalogParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }
        return 0;
    }

    public long countCatalog(String keyword, Facility.Category category, Facility.Status status) throws SQLException {
        return countCatalog(keyword);
    }

    /**
     * Lấy danh sách dòng máy phân trang kèm danh sách các cá thể máy con.
     */
    public List<Facility> findCatalogPage(String keyword, int page, int pageSize) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT f.FacilityId, f.FacilityName, f.Category, f.Location, f.ImageUrl, f.UnitLabel, f.IsActive,
                   ISNULL(ei_stats.TotalCount, 0) AS TotalQuantity,
                   ISNULL(ei_stats.AvailableCount, 0) AS AvailableQuantity,
                   ISNULL(ei_stats.MaintenanceCount, 0) AS PendingQuantity,
                   CASE 
                       WHEN f.IsActive = 0 OR ISNULL(ei_stats.TotalCount, 0) = 0 THEN 'Retired'
                       WHEN ISNULL(ei_stats.AvailableCount, 0) > 0 THEN 'Available'
                       ELSE 'UnderMaintenance'
                   END AS ComputedStatus
            FROM Facilities f
            LEFT JOIN (
                SELECT ei.FacilityId,
                       COUNT(*) AS TotalCount,
                       SUM(CASE WHEN ei.Status = 'Available' AND active_mr.EquipmentItemId IS NULL THEN 1 ELSE 0 END) AS AvailableCount,
                       SUM(CASE WHEN ei.Status = 'UnderMaintenance' OR active_mr.EquipmentItemId IS NOT NULL THEN 1 ELSE 0 END) AS MaintenanceCount
                FROM EquipmentItems ei
                LEFT JOIN (
                    SELECT DISTINCT EquipmentItemId
                    FROM MaintenanceRequests
                    WHERE Status IN ('Open', 'InProgress') AND EquipmentItemId IS NOT NULL
                ) active_mr ON active_mr.EquipmentItemId = ei.EquipmentItemId
                WHERE ei.Status <> 'Retired'
                GROUP BY ei.FacilityId
            ) ei_stats ON ei_stats.FacilityId = f.FacilityId
            WHERE f.IsActive = 1 
            """);

        List<Object> parameters = new ArrayList<>();
        appendCatalogFilters(sql, parameters, keyword);

        sql.append("""
            ORDER BY 
                CASE 
                    WHEN ISNULL(ei_stats.AvailableCount, 0) > 0 THEN 0
                    WHEN ISNULL(ei_stats.TotalCount, 0) > 0 THEN 1
                    ELSE 2 
                END,
                f.FacilityName, f.FacilityId
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """);

        parameters.add(PaginationUtil.calculateOffset(page, pageSize));
        parameters.add(pageSize);

        List<Facility> facilities = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            setCatalogParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    facilities.add(mapResultSetToFacility(resultSet));
                }
            }
        }

        // Bổ sung danh sách các cá thể máy con cho từng dòng máy (tránh N+1 bằng map)
        if (!facilities.isEmpty()) {
            Map<Integer, List<EquipmentItem>> itemsMap = equipmentItemDAO.findAllGroupedByFacility();
            for (Facility f : facilities) {
                List<EquipmentItem> items = itemsMap.get(f.getFacilityId());
                if (items != null) {
                    f.setItems(items);
                }
            }
        }

        return facilities;
    }

    public List<Facility> findCatalogPage(String keyword, Facility.Category category, Facility.Status status, int page, int pageSize) throws SQLException {
        return findCatalogPage(keyword, page, pageSize);
    }

    /**
     * Lấy toàn bộ danh sách dòng máy (bao gồm cả ẩn/ngừng hoạt động) phục vụ màn hình Quản lý của Manager.
     */
    public List<Facility> findAllForManagement() throws SQLException {
        String sql = """
            SELECT f.FacilityId, f.FacilityName, f.Category, f.Location, f.ImageUrl, f.UnitLabel, f.IsActive,
                   ISNULL(ei_stats.TotalCount, 0) AS TotalQuantity,
                   ISNULL(ei_stats.AvailableCount, 0) AS AvailableQuantity,
                   ISNULL(ei_stats.MaintenanceCount, 0) AS PendingQuantity,
                   CASE 
                       WHEN f.IsActive = 0 OR ISNULL(ei_stats.TotalCount, 0) = 0 THEN 'Retired'
                       WHEN ISNULL(ei_stats.AvailableCount, 0) > 0 THEN 'Available'
                       ELSE 'UnderMaintenance'
                   END AS ComputedStatus
            FROM Facilities f
            LEFT JOIN (
                SELECT ei.FacilityId,
                       COUNT(*) AS TotalCount,
                       SUM(CASE WHEN ei.Status = 'Available' AND active_mr.EquipmentItemId IS NULL THEN 1 ELSE 0 END) AS AvailableCount,
                       SUM(CASE WHEN ei.Status = 'UnderMaintenance' OR active_mr.EquipmentItemId IS NOT NULL THEN 1 ELSE 0 END) AS MaintenanceCount
                FROM EquipmentItems ei
                LEFT JOIN (
                    SELECT DISTINCT EquipmentItemId
                    FROM MaintenanceRequests
                    WHERE Status IN ('Open', 'InProgress') AND EquipmentItemId IS NOT NULL
                ) active_mr ON active_mr.EquipmentItemId = ei.EquipmentItemId
                WHERE ei.Status <> 'Retired'
                GROUP BY ei.FacilityId
            ) ei_stats ON ei_stats.FacilityId = f.FacilityId
            ORDER BY f.IsActive DESC, f.FacilityName, f.FacilityId
            """;

        List<Facility> facilities = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                facilities.add(mapResultSetToFacility(resultSet));
            }
        }

        if (!facilities.isEmpty()) {
            Map<Integer, List<EquipmentItem>> itemsMap = equipmentItemDAO.findAllGroupedByFacility();
            for (Facility f : facilities) {
                List<EquipmentItem> items = itemsMap.get(f.getFacilityId());
                if (items != null) {
                    f.setItems(items);
                }
            }
        }
        return facilities;
    }

    /**
     * Cập nhật trạng thái Facility (hỗ trợ tương thích ngược).
     */
    public boolean updateStatus(Connection connection, int facilityId, Facility.Status status) throws SQLException {
        return true;
    }

    /**
     * Thêm mới một nhóm máy / cơ sở vật chất (Manager).
     */
    public int create(Facility facility) throws SQLException {
        String sql = """
            INSERT INTO Facilities (FacilityName, Category, Location, ImageUrl, UnitLabel, IsActive)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection connection = DBContext.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, facility.getFacilityName());
            ps.setString(2, facility.getCategory() != null ? facility.getCategory().getDbValue() : Facility.Category.MACHINES.getDbValue());
            ps.setString(3, facility.getLocation());
            ps.setString(4, facility.getImageUrl());
            ps.setString(5, facility.getUnitLabel() != null && !facility.getUnitLabel().isBlank() ? facility.getUnitLabel() : "máy");
            ps.setBoolean(6, facility.isActive());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Cập nhật thông tin một nhóm máy (Manager).
     */
    public boolean update(Facility facility) throws SQLException {
        String sql = """
            UPDATE Facilities
            SET FacilityName = ?, Category = ?, Location = ?, ImageUrl = ?, UnitLabel = ?, IsActive = ?
            WHERE FacilityId = ?
            """;

        try (Connection connection = DBContext.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, facility.getFacilityName());
            ps.setString(2, facility.getCategory() != null ? facility.getCategory().getDbValue() : Facility.Category.MACHINES.getDbValue());
            ps.setString(3, facility.getLocation());
            ps.setString(4, facility.getImageUrl());
            ps.setString(5, facility.getUnitLabel() != null && !facility.getUnitLabel().isBlank() ? facility.getUnitLabel() : "máy");
            ps.setBoolean(6, facility.isActive());
            ps.setInt(7, facility.getFacilityId());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Ẩn hoặc Hiện một nhóm máy (IsActive: 1 = hoạt động, 0 = ẩn/tạm dừng).
     */
    public boolean toggleActive(int facilityId, boolean isActive) throws SQLException {
        String sql = "UPDATE Facilities SET IsActive = ? WHERE FacilityId = ?";
        try (Connection connection = DBContext.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, isActive);
            ps.setInt(2, facilityId);
            return ps.executeUpdate() > 0;
        }
    }

    private void appendCatalogFilters(StringBuilder sql, List<Object> parameters, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            String searchPattern = "%" + trimmed + "%";

            List<Facility.Category> matchedCategories = Facility.Category.matchByKeyword(trimmed);
            sql.append("""
                AND (
                    f.FacilityName COLLATE Latin1_General_100_CI_AI LIKE ?
                    OR f.Location COLLATE Latin1_General_100_CI_AI LIKE ?
                    OR f.Category COLLATE Latin1_General_100_CI_AI LIKE ?
                    OR (CASE f.Category 
                        WHEN 'Cardio' THEN N'Cardio' 
                        WHEN 'FreeWeights' THEN N'Tạ tự do' 
                        WHEN 'Machines' THEN N'Máy tập' 
                        WHEN 'Amenities' THEN N'Tiện ích' 
                        ELSE f.Category END) COLLATE Latin1_General_100_CI_AI LIKE ?
                """);

            parameters.add(searchPattern);
            parameters.add(searchPattern);
            parameters.add(searchPattern);
            parameters.add(searchPattern);

            if (!matchedCategories.isEmpty()) {
                sql.append(" OR f.Category IN (");
                for (int i = 0; i < matchedCategories.size(); i++) {
                    if (i > 0) sql.append(", ");
                    sql.append("?");
                    parameters.add(matchedCategories.get(i).getDbValue());
                }
                sql.append(")");
            }

            sql.append(") ");
        }
    }

    private void setCatalogParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            Object value = parameters.get(i);
            if (value instanceof Integer intVal) {
                statement.setInt(i + 1, intVal);
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
    }

    /**
     * Tra cứu 1 dòng máy theo ID.
     */
    public Facility findById(Connection connection, int facilityId) throws SQLException {
        String sql = """
            SELECT f.FacilityId, f.FacilityName, f.Category, f.Location, f.ImageUrl, f.UnitLabel, f.IsActive,
                   ISNULL(ei_stats.TotalCount, 0) AS TotalQuantity,
                   ISNULL(ei_stats.AvailableCount, 0) AS AvailableQuantity,
                   ISNULL(ei_stats.MaintenanceCount, 0) AS PendingQuantity,
                   CASE 
                       WHEN f.IsActive = 0 OR ISNULL(ei_stats.TotalCount, 0) = 0 THEN 'Retired'
                       WHEN ISNULL(ei_stats.AvailableCount, 0) > 0 THEN 'Available'
                       ELSE 'UnderMaintenance'
                   END AS ComputedStatus
            FROM Facilities f
            LEFT JOIN (
                SELECT ei.FacilityId,
                       COUNT(*) AS TotalCount,
                       SUM(CASE WHEN ei.Status = 'Available' AND active_mr.EquipmentItemId IS NULL THEN 1 ELSE 0 END) AS AvailableCount,
                       SUM(CASE WHEN ei.Status = 'UnderMaintenance' OR active_mr.EquipmentItemId IS NOT NULL THEN 1 ELSE 0 END) AS MaintenanceCount
                FROM EquipmentItems ei
                LEFT JOIN (
                    SELECT DISTINCT EquipmentItemId
                    FROM MaintenanceRequests
                    WHERE Status IN ('Open', 'InProgress') AND EquipmentItemId IS NOT NULL
                ) active_mr ON active_mr.EquipmentItemId = ei.EquipmentItemId
                WHERE ei.Status <> 'Retired'
                GROUP BY ei.FacilityId
            ) ei_stats ON ei_stats.FacilityId = f.FacilityId
            WHERE f.FacilityId = ?
            """;

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, facilityId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToFacility(resultSet);
                }
            }
        }
        return null;
    }

    /**
     * Lấy thông tin một dòng máy theo ID (tự quản lý connection).
     */
    public Facility findById(int facilityId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findById(conn, facilityId);
        }
    }

    /**
     * Lấy danh sách các vị trí / khu vực duy nhất đang có trong hệ thống để phục vụ lọc thẻ tag.
     */
    public List<String> findDistinctLocations() throws SQLException {
        String sql = "SELECT DISTINCT Location FROM Facilities WHERE Location IS NOT NULL AND LTRIM(RTRIM(Location)) <> '' ORDER BY Location";
        List<String> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(rs.getString("Location"));
            }
        }
        return list;
    }

    private Facility mapResultSetToFacility(ResultSet resultSet) throws SQLException {
        int totalQuantity = resultSet.getInt("TotalQuantity");
        int pendingQuantity = resultSet.getInt("PendingQuantity");
        int availableQuantity = resultSet.getInt("AvailableQuantity");

        Facility.Status status = Facility.Status.fromDbValue(resultSet.getString("ComputedStatus"));
        if (status == null) {
            status = availableQuantity > 0 ? Facility.Status.AVAILABLE : Facility.Status.UNDER_MAINTENANCE;
        }

        Facility facility = new Facility(
                resultSet.getInt("FacilityId"),
                resultSet.getString("FacilityName"),
                Facility.Category.fromDbValue(resultSet.getString("Category")),
                resultSet.getString("Location"),
                totalQuantity,
                status,
                pendingQuantity,
                availableQuantity
        );
        facility.setImageUrl(resultSet.getString("ImageUrl"));
        facility.setUnitLabel(resultSet.getString("UnitLabel"));
        facility.setActive(resultSet.getBoolean("IsActive"));
        return facility;
    }
}
