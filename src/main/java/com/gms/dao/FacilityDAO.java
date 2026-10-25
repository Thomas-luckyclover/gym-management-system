package com.gms.dao;

import com.gms.entity.EquipmentItem;
import com.gms.entity.Facility;
import com.gms.util.DBContext;
import com.gms.util.PaginationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object: Quản lý truy vấn danh mục dòng máy (Facilities) và tính toán số lượng cá thể con.
 */
public class FacilityDAO {

    private final EquipmentItemDAO equipmentItemDAO = new EquipmentItemDAO();

    /**
     * Lấy danh sách các dòng máy khả dụng (chưa ngừng sử dụng hoàn toàn) để lễ tân chọn.
     */
    public List<Facility> findSelectableFacilities() throws SQLException {
        String sql = """
            SELECT f.FacilityId, f.FacilityName, f.Category, f.Location,
                   ISNULL(ei_stats.TotalCount, 0) AS TotalQuantity,
                   ISNULL(ei_stats.AvailableCount, 0) AS AvailableQuantity,
                   ISNULL(mr_stats.PendingCount, 0) AS PendingQuantity,
                   CASE 
                       WHEN ISNULL(ei_stats.TotalCount, 0) = 0 THEN 'Retired'
                       WHEN ISNULL(ei_stats.AvailableCount, 0) > 0 THEN 'Available'
                       ELSE 'UnderMaintenance'
                   END AS ComputedStatus
            FROM Facilities f
            LEFT JOIN (
                SELECT FacilityId,
                       COUNT(*) AS TotalCount,
                       SUM(CASE WHEN Status = 'Available' THEN 1 ELSE 0 END) AS AvailableCount
                FROM EquipmentItems
                WHERE Status <> 'Retired'
                GROUP BY FacilityId
            ) ei_stats ON ei_stats.FacilityId = f.FacilityId
            LEFT JOIN (
                SELECT ei.FacilityId, COUNT(DISTINCT mr.MaintenanceRequestId) AS PendingCount
                FROM MaintenanceRequests mr
                JOIN EquipmentItems ei ON ei.EquipmentItemId = mr.EquipmentItemId
                WHERE mr.Status IN ('Open', 'InProgress')
                GROUP BY ei.FacilityId
            ) mr_stats ON mr_stats.FacilityId = f.FacilityId
            WHERE ISNULL(ei_stats.TotalCount, 0) > 0
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
     * Đếm tổng số dòng máy theo từ khóa tìm kiếm.
     */
    public long countCatalog(String keyword) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM Facilities f WHERE 1 = 1 ");
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
            SELECT f.FacilityId, f.FacilityName, f.Category, f.Location,
                   ISNULL(ei_stats.TotalCount, 0) AS TotalQuantity,
                   ISNULL(ei_stats.AvailableCount, 0) AS AvailableQuantity,
                   ISNULL(mr_stats.PendingCount, 0) AS PendingQuantity,
                   CASE 
                       WHEN ISNULL(ei_stats.TotalCount, 0) = 0 THEN 'Retired'
                       WHEN ISNULL(ei_stats.AvailableCount, 0) > 0 THEN 'Available'
                       ELSE 'UnderMaintenance'
                   END AS ComputedStatus
            FROM Facilities f
            LEFT JOIN (
                SELECT FacilityId,
                       COUNT(*) AS TotalCount,
                       SUM(CASE WHEN Status = 'Available' THEN 1 ELSE 0 END) AS AvailableCount
                FROM EquipmentItems
                WHERE Status <> 'Retired'
                GROUP BY FacilityId
            ) ei_stats ON ei_stats.FacilityId = f.FacilityId
            LEFT JOIN (
                SELECT ei.FacilityId, COUNT(DISTINCT mr.MaintenanceRequestId) AS PendingCount
                FROM MaintenanceRequests mr
                JOIN EquipmentItems ei ON ei.EquipmentItemId = mr.EquipmentItemId
                WHERE mr.Status IN ('Open', 'InProgress')
                GROUP BY ei.FacilityId
            ) mr_stats ON mr_stats.FacilityId = f.FacilityId
            WHERE 1 = 1 
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
            SELECT f.FacilityId, f.FacilityName, f.Category, f.Location,
                   ISNULL(ei_stats.TotalCount, 0) AS TotalQuantity,
                   ISNULL(ei_stats.AvailableCount, 0) AS AvailableQuantity,
                   ISNULL(mr_stats.PendingCount, 0) AS PendingQuantity,
                   CASE 
                       WHEN ISNULL(ei_stats.TotalCount, 0) = 0 THEN 'Retired'
                       WHEN ISNULL(ei_stats.AvailableCount, 0) > 0 THEN 'Available'
                       ELSE 'UnderMaintenance'
                   END AS ComputedStatus
            FROM Facilities f
            LEFT JOIN (
                SELECT FacilityId,
                       COUNT(*) AS TotalCount,
                       SUM(CASE WHEN Status = 'Available' THEN 1 ELSE 0 END) AS AvailableCount
                FROM EquipmentItems
                WHERE Status <> 'Retired'
                GROUP BY FacilityId
            ) ei_stats ON ei_stats.FacilityId = f.FacilityId
            LEFT JOIN (
                SELECT ei.FacilityId, COUNT(DISTINCT mr.MaintenanceRequestId) AS PendingCount
                FROM MaintenanceRequests mr
                JOIN EquipmentItems ei ON ei.EquipmentItemId = mr.EquipmentItemId
                WHERE mr.Status IN ('Open', 'InProgress')
                GROUP BY ei.FacilityId
            ) mr_stats ON mr_stats.FacilityId = f.FacilityId
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
     * Phương thức tương thích ngược khi cập nhật trạng thái dòng máy.
     */
    public boolean updateStatus(Connection connection, int facilityId, Facility.Status status) throws SQLException {
        return true;
    }

    private Facility mapResultSetToFacility(ResultSet resultSet) throws SQLException {
        int totalQuantity = resultSet.getInt("TotalQuantity");
        int pendingQuantity = resultSet.getInt("PendingQuantity");
        int availableQuantity = resultSet.getInt("AvailableQuantity");

        Facility.Status status = Facility.Status.fromDbValue(resultSet.getString("ComputedStatus"));
        if (status == null) {
            status = availableQuantity > 0 ? Facility.Status.AVAILABLE : Facility.Status.UNDER_MAINTENANCE;
        }

        return new Facility(
                resultSet.getInt("FacilityId"),
                resultSet.getString("FacilityName"),
                Facility.Category.fromDbValue(resultSet.getString("Category")),
                resultSet.getString("Location"),
                totalQuantity,
                status,
                pendingQuantity,
                availableQuantity
        );
    }
}
