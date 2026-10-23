package com.gms.dao;

import com.gms.entity.EquipmentItem;
import com.gms.util.DBContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object: Quản lý dữ liệu từng cá thể thiết bị (EquipmentItems).
 */
public class EquipmentItemDAO {

    /**
     * Lấy danh sách cá thể máy theo FacilityId, kèm thông tin đơn bảo trì đang mở nếu có.
     */
    public List<EquipmentItem> findByFacilityId(int facilityId) throws SQLException {
        String sql = """
            SELECT ei.EquipmentItemId, ei.FacilityId, ei.ItemCode, ei.Status,
                   f.FacilityName, f.Location,
                   mr.RequestCode AS ActiveRequestCode, mr.MaintenanceRequestId AS ActiveRequestId
            FROM EquipmentItems ei
            JOIN Facilities f ON f.FacilityId = ei.FacilityId
            LEFT JOIN MaintenanceRequests mr ON mr.EquipmentItemId = ei.EquipmentItemId AND mr.Status IN ('Open', 'InProgress')
            WHERE ei.FacilityId = ? AND ei.Status <> ?
            ORDER BY ei.ItemCode
            """;

        List<EquipmentItem> list = new ArrayList<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            ps.setString(2, EquipmentItem.Status.RETIRED.getDbValue());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToItem(rs));
                }
            }
        }
        return list;
    }

    /**
     * Lấy tất cả cá thể máy có thể báo hỏng (chưa Retired), gom theo thứ tự dòng máy và mã máy.
     */
    public List<EquipmentItem> findAllSelectable() throws SQLException {
        String sql = """
            SELECT ei.EquipmentItemId, ei.FacilityId, ei.ItemCode, ei.Status,
                   f.FacilityName, f.Location,
                   mr.RequestCode AS ActiveRequestCode, mr.MaintenanceRequestId AS ActiveRequestId
            FROM EquipmentItems ei
            JOIN Facilities f ON f.FacilityId = ei.FacilityId
            LEFT JOIN MaintenanceRequests mr ON mr.EquipmentItemId = ei.EquipmentItemId AND mr.Status IN ('Open', 'InProgress')
            WHERE ei.Status <> ?
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
     * Lấy toàn bộ cá thể máy con gom nhóm theo FacilityId (1 query duy nhất tránh N+1).
     */
    public Map<Integer, List<EquipmentItem>> findAllGroupedByFacility() throws SQLException {
        String sql = """
            SELECT ei.EquipmentItemId, ei.FacilityId, ei.ItemCode, ei.Status,
                   f.FacilityName, f.Location,
                   mr.RequestCode AS ActiveRequestCode, mr.MaintenanceRequestId AS ActiveRequestId
            FROM EquipmentItems ei
            JOIN Facilities f ON f.FacilityId = ei.FacilityId
            LEFT JOIN MaintenanceRequests mr ON mr.EquipmentItemId = ei.EquipmentItemId AND mr.Status IN ('Open', 'InProgress')
            ORDER BY ei.FacilityId, ei.ItemCode
            """;

        Map<Integer, List<EquipmentItem>> map = new LinkedHashMap<>();
        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                EquipmentItem item = mapResultSetToItem(rs);
                map.computeIfAbsent(item.getFacilityId(), k -> new ArrayList<>()).add(item);
            }
        }
        return map;
    }

    /**
     * Tìm 1 cá thể máy theo ID trong transaction.
     */
    public EquipmentItem findById(Connection conn, int equipmentItemId) throws SQLException {
        String sql = """
            SELECT ei.EquipmentItemId, ei.FacilityId, ei.ItemCode, ei.Status,
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
     * Cập nhật trạng thái của riêng 1 cá thể máy trong transaction.
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

    private EquipmentItem mapResultSetToItem(ResultSet rs) throws SQLException {
        EquipmentItem item = new EquipmentItem();
        item.setEquipmentItemId(rs.getInt("EquipmentItemId"));
        item.setFacilityId(rs.getInt("FacilityId"));
        item.setItemCode(rs.getString("ItemCode"));
        item.setStatus(EquipmentItem.Status.fromDbValue(rs.getString("Status")));
        item.setFacilityName(rs.getString("FacilityName"));
        item.setLocation(rs.getString("Location"));
        item.setActiveRequestCode(rs.getString("ActiveRequestCode"));
        int reqId = rs.getInt("ActiveRequestId");
        item.setActiveRequestId(rs.wasNull() ? null : reqId);
        return item;
    }
}
