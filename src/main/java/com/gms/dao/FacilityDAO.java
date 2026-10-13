package com.gms.dao;

import com.gms.entity.Facility;
import com.gms.util.DBContext;
import com.gms.util.PaginationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy vấn cơ sở vật chất phục vụ danh mục và quy trình bảo trì.
 */
public class FacilityDAO {

    // ===== Tra cứu =====

    /**
     * Lấy các cơ sở vật chất chưa bị ngừng sử dụng.
     *
     * @return danh sách cơ sở vật chất
     * @throws SQLException khi truy vấn thất bại
     */
    public List<Facility> findSelectableFacilities() throws SQLException {
        String sql = "SELECT FacilityId, FacilityName, Category, Location, Quantity, Status "
                + "FROM Facilities "
                + "WHERE Status <> ? "
                + "ORDER BY FacilityName, FacilityId";

        List<Facility> facilities = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, Facility.Status.RETIRED.getDbValue());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    facilities.add(mapResultSetToFacility(resultSet));
                }
            }
        }

        return facilities;
    }

    public long countCatalog(
            String keyword,
            Facility.Category category,
            Facility.Status status
    ) throws SQLException {

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) "
                        + "FROM Facilities "
                        + "WHERE 1 = 1 "
        );

        List<Object> parameters = new ArrayList<>();

        appendCatalogFilters(
                sql,
                parameters,
                keyword,
                category,
                status
        );

        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {

            setCatalogParameters(
                    statement,
                    parameters
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }

        return 0;
    }

    public List<Facility> findCatalogPage(
            String keyword,
            Facility.Category category,
            Facility.Status status,
            int page,
            int pageSize
    ) throws SQLException {

        StringBuilder sql = new StringBuilder(
                "SELECT FacilityId, FacilityName, Category, "
                        + "Location, Quantity, Status "
                        + "FROM Facilities "
                        + "WHERE 1 = 1 "
        );

        List<Object> parameters = new ArrayList<>();

        appendCatalogFilters(
                sql,
                parameters,
                keyword,
                category,
                status
        );

        sql.append(
                "ORDER BY "
                        + "CASE Status "
                        + "WHEN 'Available' THEN 0 "
                        + "WHEN 'UnderMaintenance' THEN 1 "
                        + "ELSE 2 END, "
                        + "FacilityName, FacilityId "
                        + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
        );

        parameters.add(
                PaginationUtil.calculateOffset(
                        page,
                        pageSize
                )
        );

        parameters.add(pageSize);

        List<Facility> facilities =
                new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql.toString())) {

            setCatalogParameters(
                    statement,
                    parameters
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    facilities.add(
                            mapResultSetToFacility(
                                    resultSet
                            )
                    );
                }
            }
        }

        return facilities;
    }

    private void appendCatalogFilters(
            StringBuilder sql,
            List<Object> parameters,
            String keyword,
            Facility.Category category,
            Facility.Status status
    ) {

        if (keyword != null
                && !keyword.isBlank()) {

            sql.append(
                    "AND ("
                            + "FacilityName COLLATE Latin1_General_100_CI_AI LIKE ? "
                            + "OR Location COLLATE Latin1_General_100_CI_AI LIKE ?"
                            + ") "
            );

            String search =
                    "%" + keyword.trim() + "%";

            parameters.add(search);
            parameters.add(search);
        }

        if (category != null) {
            sql.append("AND Category = ? ");
            parameters.add(category.getDbValue());
        }

        if (status != null) {
            sql.append("AND Status = ? ");
            parameters.add(status.getDbValue());
        }
    }

    private void setCatalogParameters(
            PreparedStatement statement,
            List<Object> parameters
    ) throws SQLException {

        for (int i = 0;
             i < parameters.size();
             i++) {

            Object value = parameters.get(i);

            if (value instanceof Integer) {
                statement.setInt(
                        i + 1,
                        (Integer) value
                );
            } else {
                statement.setString(
                        i + 1,
                        String.valueOf(value)
                );
            }
        }
    }

    public Facility findById(Connection connection, int facilityId)
            throws SQLException {

        String sql = "SELECT FacilityId, FacilityName, Category, Location, Quantity, Status "
                + "FROM Facilities "
                + "WHERE FacilityId = ?";

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

    // ===== Cập nhật trạng thái =====

    public boolean updateStatus(
            Connection connection,
            int facilityId,
            Facility.Status status
    ) throws SQLException {

        String sql = "UPDATE Facilities "
                + "SET Status = ? "
                + "WHERE FacilityId = ? "
                + "AND Status <> ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.getDbValue());
            statement.setInt(2, facilityId);
            statement.setString(3, Facility.Status.RETIRED.getDbValue());

            return statement.executeUpdate() == 1;
        }
    }

    private Facility mapResultSetToFacility(ResultSet resultSet)
            throws SQLException {

        return new Facility(
                resultSet.getInt("FacilityId"),
                resultSet.getString("FacilityName"),
                Facility.Category.fromDbValue(resultSet.getString("Category")),
                resultSet.getString("Location"),
                resultSet.getInt("Quantity"),
                Facility.Status.fromDbValue(resultSet.getString("Status"))
        );
    }
}