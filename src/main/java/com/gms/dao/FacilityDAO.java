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
 * Data Access Object: Truy vấn dữ liệu dòng máy (Facilities) và tổng hợp trạng thái các cá thể con.
 */
public class FacilityDAO {

    private final EquipmentItemDAO equipmentItemDAO = new EquipmentItemDAO();

    /**
     * Đếm tổng số dòng máy đang hoạt động theo từ khóa và khu vực cho danh mục của Lễ tân.
     * Hàm nối động điều kiện lọc từ khóa/khu vực trên bảng Facilities (chỉ lấy IsActive = 1) rồi thực thi SELECT COUNT(*).
     */
    public long countCatalog(String keyword, String location) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM Facilities f WHERE f.IsActive = 1 ");
        List<Object> parameters = new ArrayList<>();
        appendCatalogFilters(sql, parameters, keyword, location);

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

    /**
     * Lấy danh sách dòng máy phân trang (chỉ IsActive = 1) kèm thống kê khả dụng và danh sách cá thể con.
     * Hàm thống kê số máy Available/đang bảo trì từ EquipmentItems và MaintenanceRequests, sắp xếp ưu tiên dòng máy còn máy sẵn sàng, phân trang bằng OFFSET/FETCH rồi nạp map cá thể con một lần để tránh N+1 query.
     */
    public List<Facility> findCatalogPage(String keyword, String location, int page, int pageSize) throws SQLException {
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
        appendCatalogFilters(sql, parameters, keyword, location);

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

        // Bổ sung danh sách các cá thể máy con chưa thanh lý cho từng dòng máy (tránh N+1 bằng map)
        if (!facilities.isEmpty()) {
            Map<Integer, List<EquipmentItem>> itemsMap = equipmentItemDAO.findAllGroupedByFacility(false);
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
     * Lấy toàn bộ danh sách dòng máy (bao gồm cả dòng máy đang ẩn) phục vụ màn hình Quản lý cơ sở vật chất của Manager.
     * Hàm tổng hợp số lượng khả dụng/đang bảo trì từ bảng EquipmentItems và MaintenanceRequests, sắp xếp dòng máy đang hoạt động lên trước rồi gắn danh sách cá thể con cho từng dòng máy.
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
            Map<Integer, List<EquipmentItem>> itemsMap = equipmentItemDAO.findAllGroupedByFacility(true);
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
     * Thêm mới một dòng máy (nhóm cơ sở vật chất) vào CSDL.
     * Hàm INSERT dữ liệu vào bảng Facilities và đọc khóa chính vừa tự tăng qua getGeneratedKeys() để trả về ID mới.
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
     * Cập nhật thông tin một dòng máy trong CSDL theo FacilityId.
     * Hàm thực thi câu lệnh UPDATE trên bảng Facilities và trả về true nếu có ít nhất 1 dòng bị ảnh hưởng.
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
     * Bật hoặc tắt trạng thái hiển thị của một dòng máy (IsActive = 1 là hiển thị, 0 là ẩn).
     * Hàm chỉ cập nhật cột IsActive theo FacilityId bằng PreparedStatement và trả về true nếu cập nhật thành công.
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

    /**
     * Nối điều kiện lọc theo khu vực và từ khóa (tên dòng máy, vị trí, danh mục tiếng Anh/Việt) vào câu SQL danh mục.
     * Hàm dùng collation không phân biệt dấu/hoa thường (CI_AI) kết hợp tra cứu mã Category khớp từ khóa rồi đẩy giá trị vào danh sách tham số.
     */
    private void appendCatalogFilters(StringBuilder sql, List<Object> parameters, String keyword, String location) {
        if (location != null && !location.isBlank()) {
            sql.append(" AND f.Location = ? ");
            parameters.add(location.trim());
        }
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

    /**
     * Gán lần lượt danh sách tham số động vào PreparedStatement theo đúng kiểu dữ liệu (Integer hoặc String).
     */
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
     * Tra cứu thông tin chi tiết một dòng máy theo ID trên kết nối có sẵn (dùng trong transaction).
     * Hàm join bảng thống kê các cá thể con chưa Retired và đơn bảo trì đang mở để tính ComputedStatus rồi ánh xạ thành Facility.
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
     * Lấy thông tin chi tiết một dòng máy theo ID (tự mở và đóng connection riêng).
     * Hàm ủy quyền cho findById(Connection, int) trong khối try-with-resources của DBContext.
     */
    public Facility findById(int facilityId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findById(conn, facilityId);
        }
    }

    /**
     * Lấy danh sách các khu vực/vị trí không trùng lặp từ bảng Facilities để hiển thị thẻ lọc nhanh.
     * Hàm lọc các dòng có Location khác null/rỗng rồi sắp xếp theo thứ tự bảng chữ cái.
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

    /**
     * Ánh xạ một dòng dữ liệu ResultSet thành đối tượng Facility kèm số liệu tồn/bảo trì và trạng thái tính toán.
     */
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
