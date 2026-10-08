package com.gms.dao;

import com.gms.entity.SystemConfig;
import com.gms.util.DBContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho bảng SystemConfigs — 20 tham số P-01…P-20 (agents.md Phần 13).
 * Chỉ truy vấn, không nghiệp vụ; SystemConfigService gọi DAO này rồi tự parse int/List theo nhu cầu.
 *
 * @author Le Cong Minh
 */
public class SystemConfigDAO {

    /**
     * Tìm 1 tham số theo mã (VD "P-15").
     * @param configKey mã tham số
     * @return SystemConfig tương ứng, null nếu không tìm thấy
     * @throws SQLException lỗi truy vấn
     */
    public SystemConfig findByKey(String configKey) throws SQLException {
        String sql = "SELECT SystemConfigId, ConfigKey, ConfigValue, Description, UpdatedAt, UpdatedByEmployeeId "
                + "FROM SystemConfigs "
                + "WHERE ConfigKey = ?";


        // Cả Connection, PreparedStatement, ResultSet đều mở trong try(...) - try - cacth - resource => tự động đóng khi xong, không cần gọi hàm close()
        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, configKey);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) { //rs.next() == true
                    return mapResultSetToSystemConfig(rs); // trả về Object SystemConfig có configKey tương ứng
                }
                return null; // không tìm thấy => trả về null
            }
        }
    }

    /**
     * Lấy toàn bộ tham số hệ thống (màn Manager 4.1.2 sửa tham số).
     * @return danh sách đủ 20 tham số, sắp theo ConfigKey
     * @throws SQLException lỗi truy vấn
     */
    
    // Lấy đủ danh sách P-01 đến P-20
    public List<SystemConfig> findAll() throws SQLException {
        String sql = "SELECT SystemConfigId, ConfigKey, ConfigValue, Description, UpdatedAt, UpdatedByEmployeeId "
                + "FROM SystemConfigs "
                + "ORDER BY ConfigKey";

        try (Connection conn = DBContext.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            List<SystemConfig> configs = new ArrayList<>();
            while (rs.next()) { // duyệt tất cả các dòng trả về, add vào List configs
                configs.add(mapResultSetToSystemConfig(rs)); // mỗi dòng convert qua mapResultSetToSystemConfig
            }
            return configs; // trả về list kết quả
        }
    }


    
    // ===== Map dữ liệu ======
    // Hàm: Nhận vào 1 ResultSet (con trỏ đang đứng tại 1 dòng dữ liệu), đọc tất cả cột của dòng đó, rồi "gói" lại thành 1 object SystemConfig, trả về Object đó
    private SystemConfig mapResultSetToSystemConfig(ResultSet rs) throws SQLException {
        SystemConfig config = new SystemConfig(rs.getInt("SystemConfigId")); // field SystemConfigId trong SystemConfig được khai báo final, final là bắt buộc gán giá trị ngay lúc khởi tạo object (trong constructor), và không bao giờ được đổi sau đó nữa, nên không có setter setSystemConfigId. Cách duy nhất gán systemConfigId là truyền vào constructor new. Sau khi dòng chạy xong, object config có sẵn systemConfigId, không đổi được nữa
        // Các dòng sau - do các field còn lại không phải final => có setter bình thường, gns sau khi object đã tồn tại => style "constructor tối thiểu + setter"
        config.setConfigKey(rs.getString("ConfigKey"));
        config.setConfigValue(rs.getString("ConfigValue"));
        config.setDescription(rs.getString("Description"));
        
        
        // Thế giới 1: JDBC - ResultSet.getTimestamp(...) chỉ biết trả về java.sql.Timestamp
        // Thế giới 2: code nghiệp vụ: Toàn bộ Entity/Service dùng LocalDateTime, java.sq.Timestamp cũ, thiếu tiện ích và dễ lẫn múi giờ
        
        LocalDateTime updatedAt = rs.getTimestamp("UpdatedAt") == null ? null : rs.getTimestamp("UpdatedAt").toLocalDateTime();
        config.setUpdatedAt(updatedAt);

        int updatedByEmployeeId = rs.getInt("UpdatedByEmployeeId"); // kiểu dữ liệu int để hứng tạm giá trị JDBC trả về, JDBC chỉ làm việc với kiểu dữ liệu nguyên thủy. Ở bên Entity sẽ dùng Integer, không phải int. Đọc giá trị từ DB, gán vào biến updatedByEmployeeId
        config.setUpdatedByEmployeeId(rs.wasNull() ? null : updatedByEmployeeId); 
        // config.setUpdatedByEmployeeId có 2 việc, 2a: Tính giá trị bên trong ngoặc, ra null hoặc con số => 2b: Sau khi có kết quả, gọi hàm config.setUpdatedByEmployeeId => 2c: Nhảy tới hàm setter trong SystemConfig.java (bọc int thành Integer, nếu null vẫn là null), hàm này gán giá trị rồi kết thúc ngay => XOng thì quay trở lại đúng chỗ vừa gọi nó trong mapResultSetToSystemConfig, tiếp tục dòng kế tiếp

        return config; // chạy return config; trả về đối tượng config giờ đã có đủ 6 field, trả ra ngoài
    
                
    }
}



/*
Note:
- int là kiểu nguyên thủy (primitive) — giống như 1 cái hộp nhỏ, cố định chứa đúng 4 byte số, không có "cửa" để chứa null. Ưu điểm: nhanh, nhẹ, tốn ít bộ nhớ.
- Integer là 1 object (giống String, SystemConfig...) — là 1 "tham chiếu" (reference), giống như 1 tờ giấy ghi địa chỉ trỏ tới nơi chứa số đó trong bộ nhớ. Tờ giấy này có thể không trỏ vào đâu cả — đó chính là trạng thái null. Đổi lại: tốn bộ nhớ hơn, chạy chậm hơn chút xíu.
=> Nếu Entity khai báo field là int updatedByEmployeeID sẽ không có cách nào biểu đạt được trạng thái "chưa ai sửa", phải dùng tạm con số 0 hoặc -1 để giả làm NULL, dễ nhầm lẫn, dễ bug. Nếu khai báo updatedByEmployeeId, khi chưa ai sửa, field này thật sự là null, đúng nguyên ý nghĩa của dữ liệu, code sau này chỉ cần if(x == null) là biết chắc chắn
*/