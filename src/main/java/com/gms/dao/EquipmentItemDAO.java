package com.gms.dao;

import com.gms.entity.EquipmentItem;
import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object: Truy vấn và cập nhật dữ liệu từng cá thể thiết bị vật lý (EquipmentItems).
 */
public class EquipmentItemDAO {

    /**
     * Lấy toàn bộ cá thể máy thuộc một dòng máy (bao gồm cả máy đã Retired) cho màn hình Quản lý.
     * Hàm join bảng Facilities và MaintenanceRequests để kèm thông tin phiếu bảo trì đang mở, đồng thời đẩy các máy đã thanh lý xuống cuối danh sách.
     */
    public List<EquipmentItem> findByFacilityIdForManagement(int facilityId) throws SQLException {
        String sql = """
            SELECT ei.EquipmentItemId, ei.FacilityId, ei.ItemCode, ei.ItemLabel, ei.PlacementNote, ei.Status,
                   f.FacilityName, f.Location,
                   mr.RequestCode AS ActiveRequestCode, mr.MaintenanceRequestId AS ActiveRequestId
            FROM EquipmentItems ei
            JOIN Facilities f ON f.FacilityId = ei.FacilityId
            LEFT JOIN MaintenanceRequests mr ON mr.EquipmentItemId = ei.EquipmentItemId AND mr.Status IN ('Open', 'InProgress')
            WHERE ei.FacilityId = ?
            ORDER BY 
                CASE WHEN ei.Status = 'Retired' THEN 1 ELSE 0 END,
                ei.ItemCode
            """;

        List<EquipmentItem> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facilityId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToItem(rs));
                }
            }
        }
        return list;
    }

    /**
     * Lấy tất cả cá thể máy thuộc các dòng máy đang hiển thị (chưa Retired) để Lễ tân chọn khi báo hỏng.
     * Hàm lọc Facilities.IsActive = 1 và Status khác Retired, join thêm phiếu bảo trì đang mở để cảnh báo trên giao diện nếu máy đang vướng đơn.
     */
    public List<EquipmentItem> findAllSelectable() throws SQLException {
        String sql = """
            SELECT ei.EquipmentItemId, ei.FacilityId, ei.ItemCode, ei.ItemLabel, ei.PlacementNote, ei.Status,
                   f.FacilityName, f.Location,
                   mr.RequestCode AS ActiveRequestCode, mr.MaintenanceRequestId AS ActiveRequestId
            FROM EquipmentItems ei
            JOIN Facilities f ON f.FacilityId = ei.FacilityId
            LEFT JOIN MaintenanceRequests mr ON mr.EquipmentItemId = ei.EquipmentItemId AND mr.Status IN ('Open', 'InProgress')
            WHERE ei.Status <> ? AND f.IsActive = 1
            ORDER BY f.FacilityName, ei.ItemCode
            """;

        List<EquipmentItem> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, EquipmentItem.Status.RETIRED.getDbValue());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToItem(rs));
                }
            }
        }
        return list;
    }

    /**
     * Lấy toàn bộ cá thể máy con (bao gồm cả máy đã Retired) và gom nhóm theo FacilityId thành Map.
     * Hàm ủy quyền cho {@link #findAllGroupedByFacility(boolean)} với {@code includeRetired = true}.
     */
    public Map<Integer, List<EquipmentItem>> findAllGroupedByFacility() throws SQLException {
        return findAllGroupedByFacility(true);
    }

    /**
     * Lấy danh sách cá thể máy con gom nhóm theo FacilityId, cho phép chọn giữ hay ẩn máy đã thanh lý ({@code Retired}).
     * Hàm thực thi 1 truy vấn duy nhất (tránh N+1 query), lọc bỏ {@code Retired} khi {@code includeRetired = false},
     * sắp xếp máy {@code Retired} xuống cuối mỗi nhóm rồi dùng {@code computeIfAbsent} gom vào {@link LinkedHashMap}.
     */
    public Map<Integer, List<EquipmentItem>> findAllGroupedByFacility(boolean includeRetired) throws SQLException {
        StringBuilder sql = new StringBuilder("""
            SELECT ei.EquipmentItemId, ei.FacilityId, ei.ItemCode, ei.ItemLabel, ei.PlacementNote, ei.Status,
                   f.FacilityName, f.Location,
                   mr.RequestCode AS ActiveRequestCode, mr.MaintenanceRequestId AS ActiveRequestId
            FROM EquipmentItems ei
            JOIN Facilities f ON f.FacilityId = ei.FacilityId
            LEFT JOIN MaintenanceRequests mr ON mr.EquipmentItemId = ei.EquipmentItemId AND mr.Status IN ('Open', 'InProgress')
            """);
        if (!includeRetired) {
            sql.append(" WHERE ei.Status <> ?\n");
        }
        sql.append("""
            ORDER BY ei.FacilityId, 
                     CASE WHEN ei.Status = 'Retired' THEN 1 ELSE 0 END,
                     ei.ItemCode
            """);

        Map<Integer, List<EquipmentItem>> map = new LinkedHashMap<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            if (!includeRetired) {
                ps.setString(1, EquipmentItem.Status.RETIRED.getDbValue());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    EquipmentItem item = mapResultSetToItem(rs);
                    map.computeIfAbsent(item.getFacilityId(), k -> new ArrayList<>()).add(item);
                }
            }
        }
        return map;
    }

    /**
     * Tra cứu thông tin 1 cá thể máy theo EquipmentItemId trên kết nối có sẵn (dùng trong transaction).
     * Hàm join bảng Facilities và đơn bảo trì đang mở (Open/InProgress) để kiểm tra điều kiện trạng thái trước khi tạo/duyệt/đóng phiếu.
     */
    public EquipmentItem findById(Connection conn, int equipmentItemId) throws SQLException {
        String sql = """
            SELECT ei.EquipmentItemId, ei.FacilityId, ei.ItemCode, ei.ItemLabel, ei.PlacementNote, ei.Status,
                   f.FacilityName, f.Location,
                   mr.RequestCode AS ActiveRequestCode, mr.MaintenanceRequestId AS ActiveRequestId
            FROM EquipmentItems ei
            JOIN Facilities f ON f.FacilityId = ei.FacilityId
            LEFT JOIN MaintenanceRequests mr ON mr.EquipmentItemId = ei.EquipmentItemId AND mr.Status IN ('Open', 'InProgress')
            WHERE ei.EquipmentItemId = ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, equipmentItemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToItem(rs);
                }
            }
        }
        return null;
    }

    /**
     * Thêm mới một cá thể máy vật lý vào dòng máy cha trong CSDL.
     * Hàm chuẩn hóa khoảng trắng mã/nhãn/vị trí, INSERT vào bảng EquipmentItems và trả về EquipmentItemId tự tăng.
     */
    public int create(EquipmentItem item) throws SQLException {
        String sql = """
            INSERT INTO EquipmentItems (FacilityId, ItemCode, ItemLabel, PlacementNote, Status)
            VALUES (?, ?, ?, ?, ?)
            """;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, item.getFacilityId());
            ps.setString(2, item.getItemCode().trim());
            ps.setString(3, item.getItemLabel() != null ? item.getItemLabel().trim() : "");
            ps.setString(4, item.getPlacementNote() != null ? item.getPlacementNote().trim() : null);
            ps.setString(5, item.getStatus() != null ? item.getStatus().getDbValue() : EquipmentItem.Status.AVAILABLE.getDbValue());

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
     * Cập nhật nhãn hiển thị và ghi chú vị trí đặt máy của một cá thể thiết bị.
     * Hàm chỉ UPDATE ItemLabel và PlacementNote theo EquipmentItemId, giữ nguyên mã ItemCode và trạng thái hiện hành.
     */
    public boolean update(EquipmentItem item) throws SQLException {
        String sql = """
            UPDATE EquipmentItems
            SET ItemLabel = ?, PlacementNote = ?
            WHERE EquipmentItemId = ?
            """;

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getItemLabel() != null ? item.getItemLabel().trim() : "");
            ps.setString(2, item.getPlacementNote() != null ? item.getPlacementNote().trim() : null);
            ps.setInt(3, item.getEquipmentItemId());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Thanh lý một cá thể máy bằng cách chuyển Status sang 'Retired'.
     * Hàm cập nhật cột Status trong bảng EquipmentItems theo EquipmentItemId và trả về true nếu thành công.
     */
    public boolean retire(int equipmentItemId) throws SQLException {
        String sql = "UPDATE EquipmentItems SET Status = 'Retired' WHERE EquipmentItemId = ?";
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, equipmentItemId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Kiểm tra mã cá thể máy (ItemCode) đã tồn tại trong CSDL hay chưa để tránh trùng lặp.
     * Hàm đếm số bản ghi có cùng ItemCode (có thể loại trừ excludeItemId khi đang cập nhật) và trả về true nếu > 0.
     */
    public boolean isItemCodeExists(String itemCode, Integer excludeItemId) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM EquipmentItems WHERE ItemCode = ?");
        if (excludeItemId != null) {
            sql.append(" AND EquipmentItemId <> ?");
        }
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, itemCode.trim());
            if (excludeItemId != null) {
                ps.setInt(2, excludeItemId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Kiểm tra nhãn hiển thị (ItemLabel) đã tồn tại trong cùng nhóm máy (FacilityId) hay chưa.
     * Hàm đếm số bản ghi có cùng FacilityId và ItemLabel (có thể loại trừ excludeItemId khi đang cập nhật) và trả về true nếu > 0.
     */
    public boolean isItemLabelExists(int facilityId, String itemLabel, Integer excludeItemId) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT COUNT(*) FROM EquipmentItems WHERE FacilityId = ? AND ItemLabel = ?"
        );
        if (excludeItemId != null) {
            sql.append(" AND EquipmentItemId <> ?");
        }
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setInt(1, facilityId);
            ps.setString(2, itemLabel.trim());
            if (excludeItemId != null) {
                ps.setInt(3, excludeItemId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Lấy danh sách các ghi chú vị trí đặt máy (PlacementNote) không trùng lặp để gợi ý nhập liệu cho Manager.
     * Hàm lọc các giá trị khác null/rỗng trong bảng EquipmentItems và sắp xếp tăng dần.
     */
    public List<String> findDistinctPlacementNotes() throws SQLException {
        String sql = """
            SELECT DISTINCT PlacementNote 
            FROM EquipmentItems 
            WHERE PlacementNote IS NOT NULL AND LTRIM(RTRIM(PlacementNote)) <> ''
            ORDER BY PlacementNote
            """;

        List<String> notes = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                notes.add(rs.getString("PlacementNote"));
            }
        }
        return notes;
    }

    /**
     * Cập nhật trạng thái của một cá thể máy trong phạm vi transaction (khi duyệt sang bảo trì hoặc đóng phiếu khôi phục khả dụng).
     * Hàm chỉ cập nhật khi máy chưa bị Retired (Status <> 'Retired') để bảo vệ dữ liệu thiết bị đã thanh lý.
     */
    public boolean updateStatus(Connection conn, int equipmentItemId, EquipmentItem.Status status) throws SQLException {
        String sql = """
            UPDATE EquipmentItems
            SET Status = ?
            WHERE EquipmentItemId = ? AND Status <> ?
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.getDbValue());
            ps.setInt(2, equipmentItemId);
            ps.setString(3, EquipmentItem.Status.RETIRED.getDbValue());
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Ánh xạ một dòng ResultSet thành đối tượng EquipmentItem kèm thông tin dòng máy cha và đơn bảo trì đang mở nếu có.
     */
    private EquipmentItem mapResultSetToItem(ResultSet rs) throws SQLException {
        EquipmentItem item = new EquipmentItem();
        item.setEquipmentItemId(rs.getInt("EquipmentItemId"));
        item.setFacilityId(rs.getInt("FacilityId"));
        item.setItemCode(rs.getString("ItemCode"));
        item.setItemLabel(rs.getString("ItemLabel"));
        item.setPlacementNote(rs.getString("PlacementNote"));
        item.setStatus(EquipmentItem.Status.fromDbValue(rs.getString("Status")));
        item.setFacilityName(rs.getString("FacilityName"));
        item.setLocation(rs.getString("Location"));
        item.setActiveRequestCode(rs.getString("ActiveRequestCode"));
        int reqId = rs.getInt("ActiveRequestId");
        item.setActiveRequestId(rs.wasNull() ? null : reqId);
        return item;
    }
}