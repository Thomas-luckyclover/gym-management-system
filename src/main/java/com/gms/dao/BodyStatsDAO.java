package com.gms.dao;

import com.gms.dto.BodyStatRow;
import com.gms.util.DBContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO MÀN CHỈ SỐ CƠ THỂ (/customer/body-stats) — bảng BodyMeasurements. Chỉ đọc/ghi, không tính BMI.
 *
 *   findCustomerId   tài khoản đăng nhập → mã khách (chỉ khách mới có màn này)
 *   findByCustomer   mọi lần đo của khách, cũ trước
 *   save             ghi 1 lần đo; cùng ngày đã có → sửa dòng đó (mỗi khách 1 dòng / ngày)
 *   delete           xóa 1 lần đo CỦA CHÍNH khách (sai mã khách → không xóa gì)
 */
public class BodyStatsDAO {

    public Integer findCustomerId(int accountId) throws SQLException {
        String sql = """
                SELECT c.CustomerId
                FROM Accounts a
                JOIN Customers c ON c.PersonId = a.PersonId AND c.DeletedAt IS NULL
                WHERE a.AccountId = ?
                """;
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("CustomerId") : null;
            }
        }
    }

    public List<BodyStatRow> findByCustomer(int customerId) throws SQLException {
        String sql = """
                SELECT BodyMeasurementId, MeasuredOn, WeightKg, HeightCm, BodyFatPercent, MusclePercent, WaistCm, Note
                FROM BodyMeasurements
                WHERE CustomerId = ?
                ORDER BY MeasuredOn
                """;
        List<BodyStatRow> rows = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    BodyStatRow row = new BodyStatRow();
                    row.setId(rs.getInt("BodyMeasurementId"));
                    row.setMeasuredOn(rs.getDate("MeasuredOn").toLocalDate());
                    row.setWeightKg(rs.getBigDecimal("WeightKg"));
                    row.setHeightCm(rs.getBigDecimal("HeightCm"));
                    row.setBodyFatPercent(rs.getBigDecimal("BodyFatPercent"));
                    row.setMusclePercent(rs.getBigDecimal("MusclePercent"));
                    row.setWaistCm(rs.getBigDecimal("WaistCm"));
                    row.setNote(rs.getString("Note"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * MERGE: ngày đó chưa có → thêm dòng; đã có → ghi đè số mới (khách sửa lại lần đo trong ngày).
     * HOLDLOCK: 2 lần bấm Lưu cùng lúc không tạo 2 dòng cùng ngày (UQ_BodyMeasurements_Day cũng chặn).
     */
    public void save(int customerId, LocalDate day, BigDecimal weight, BigDecimal height, BigDecimal fat,
            BigDecimal muscle, BigDecimal waist, String note) throws SQLException {
        String sql = """
                MERGE BodyMeasurements WITH (HOLDLOCK) AS target
                USING (SELECT ? AS CustomerId, ? AS MeasuredOn) AS source
                   ON target.CustomerId = source.CustomerId AND target.MeasuredOn = source.MeasuredOn
                WHEN MATCHED THEN
                    UPDATE SET WeightKg = ?, HeightCm = ?, BodyFatPercent = ?, MusclePercent = ?, WaistCm = ?, Note = ?
                WHEN NOT MATCHED THEN
                    INSERT (CustomerId, MeasuredOn, WeightKg, HeightCm, BodyFatPercent, MusclePercent, WaistCm, Note)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                """;
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, customerId);
            statement.setDate(2, Date.valueOf(day));
            setValues(statement, 3, weight, height, fat, muscle, waist, note);      // ? số 3–8 (UPDATE)
            statement.setInt(9, customerId);
            statement.setDate(10, Date.valueOf(day));
            setValues(statement, 11, weight, height, fat, muscle, waist, note);     // ? số 11–16 (INSERT)
            statement.executeUpdate();
        }
    }

    private void setValues(PreparedStatement statement, int from, BigDecimal weight, BigDecimal height,
            BigDecimal fat, BigDecimal muscle, BigDecimal waist, String note) throws SQLException {
        statement.setBigDecimal(from, weight);
        statement.setBigDecimal(from + 1, height);
        statement.setBigDecimal(from + 2, fat);      // null → NULL
        statement.setBigDecimal(from + 3, muscle);
        statement.setBigDecimal(from + 4, waist);
        if (note == null) {
            statement.setNull(from + 5, Types.NVARCHAR);
        } else {
            statement.setNString(from + 5, note);
        }
    }

    /** true = đã xóa; false = không có lần đo đó của khách này. */
    public boolean delete(int customerId, int measurementId) throws SQLException {
        String sql = "DELETE FROM BodyMeasurements WHERE BodyMeasurementId = ? AND CustomerId = ?";
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, measurementId);
            statement.setInt(2, customerId);
            return statement.executeUpdate() == 1;
        }
    }
}
