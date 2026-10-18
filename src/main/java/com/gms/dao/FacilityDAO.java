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
 * Data Access Object (DAO) Layer: Manages database interactions for Facility records.
 *
 * <p><b>Architectural Responsibility:</b></p>
 * The DAO layer isolates low-level JDBC operations (connection management, PreparedStatement construction,
 * parameter mapping, and ResultSet extraction) from the Service layer. The DAO contains NO business logic,
 * authorization checks, or transaction boundary commits/rollbacks; it strictly executes queries and updates.
 *
 * <p><b>Quantity Calculation Rationale:</b></p>
 * To satisfy BF-06's strict constraint of zero database modifications, dynamic counts are computed using
 * a SQL subquery with LEFT JOIN on {@code MaintenanceRequests} where {@code Status IN ('Open', 'InProgress')}:
 * <ul>
 *   <li>{@code PendingQuantity}: Count of active, unfinished maintenance requests.</li>
 *   <li>{@code AvailableQuantity}: If {@code Status = 'Retired'} then 0; otherwise {@code MAX(0, Facilities.Quantity - PendingQuantity)}.</li>
 * </ul>
 * The persistent column {@code Facilities.Quantity} is NEVER modified.
 */
public class FacilityDAO {

    // ===== Tra cứu =====

    /**
     * Retrieves all non-retired facilities with calculated pending and available quantities.
     * Used by Receptionists when populating the facility selection dropdown in maintenance request creation.
     *
     * <p><b>SQL Design:</b> Uses a LEFT JOIN to aggregate unfinished maintenance requests per facility.
     * If a facility has no active requests, {@code ISNULL(mr_counts.PendingCount, 0)} returns 0.
     * The CASE expression ensures {@code AvailableQuantity} never drops below 0 even if multiple
     * requests have been logged for the same equipment unit or batch.</p>
     *
     * @return list of selectable facilities with derived runtime quantities
     * @throws SQLException if a database access error occurs
     */
    public List<Facility> findSelectableFacilities() throws SQLException {
        // Subquery aggregates unfinished requests (Open + InProgress) without altering Facilities.Quantity in DB
        String sql = "SELECT f.FacilityId, f.FacilityName, f.Category, f.Location, f.Quantity, f.Status, "
                + "ISNULL(mr_counts.PendingCount, 0) AS PendingQuantity, "
                + "CASE WHEN f.Status = 'Retired' THEN 0 "
                + "WHEN (f.Quantity - ISNULL(mr_counts.PendingCount, 0)) < 0 THEN 0 "
                + "ELSE (f.Quantity - ISNULL(mr_counts.PendingCount, 0)) END AS AvailableQuantity "
                + "FROM Facilities f "
                + "LEFT JOIN ("
                + "    SELECT FacilityId, COUNT(*) AS PendingCount "
                + "    FROM MaintenanceRequests "
                + "    WHERE Status IN ('Open', 'InProgress') "
                + "    GROUP BY FacilityId"
                + ") mr_counts ON mr_counts.FacilityId = f.FacilityId "
                + "WHERE f.Status <> ? "
                + "ORDER BY f.FacilityName, f.FacilityId";

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

    /**
     * Counts facilities matching the keyword search.
     * When keyword is null or blank, counts all facilities in the system.
     *
     * @param keyword search term matching name, category, or location
     * @return total matching count
     * @throws SQLException if a query error occurs
     */
    public long countCatalog(String keyword) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM Facilities f WHERE 1 = 1 "
        );

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

    /**
     * Legacy signature: safely delegates to {@link #countCatalog(String)},
     * ignoring obsolete category and status parameters.
     */
    public long countCatalog(
            String keyword,
            Facility.Category category,
            Facility.Status status
    ) throws SQLException {
        return countCatalog(keyword);
    }

    /**
     * Retrieves a paginated list of facilities for the catalog view using a single unified keyword search.
     *
     * <p><b>Search Scope:</b> Matches across FacilityName, Category (English dbValue & Vietnamese displayName),
     * and Location using case- and accent-insensitive collation.</p>
     *
     * @param keyword  optional search keyword
     * @param page     1-based page index
     * @param pageSize number of records per page
     * @return paginated list of facilities with calculated quantities
     * @throws SQLException if a query execution error occurs
     */
    public List<Facility> findCatalogPage(
            String keyword,
            int page,
            int pageSize
    ) throws SQLException {

        // Dynamically compute PendingQuantity and AvailableQuantity via subquery
        StringBuilder sql = new StringBuilder(
                "SELECT f.FacilityId, f.FacilityName, f.Category, "
                        + "f.Location, f.Quantity, f.Status, "
                        + "ISNULL(mr_counts.PendingCount, 0) AS PendingQuantity, "
                        + "CASE WHEN f.Status = 'Retired' THEN 0 "
                        + "WHEN (f.Quantity - ISNULL(mr_counts.PendingCount, 0)) < 0 THEN 0 "
                        + "ELSE (f.Quantity - ISNULL(mr_counts.PendingCount, 0)) END AS AvailableQuantity "
                        + "FROM Facilities f "
                        + "LEFT JOIN ("
                        + "    SELECT FacilityId, COUNT(*) AS PendingCount "
                        + "    FROM MaintenanceRequests "
                        + "    WHERE Status IN ('Open', 'InProgress') "
                        + "    GROUP BY FacilityId"
                        + ") mr_counts ON mr_counts.FacilityId = f.FacilityId "
                        + "WHERE 1 = 1 "
        );

        List<Object> parameters = new ArrayList<>();
        appendCatalogFilters(sql, parameters, keyword);

        sql.append(
                "ORDER BY "
                        + "CASE f.Status "
                        + "WHEN 'Available' THEN 0 "
                        + "WHEN 'UnderMaintenance' THEN 1 "
                        + "ELSE 2 END, "
                        + "f.FacilityName, f.FacilityId "
                        + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"
        );

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

        return facilities;
    }

    /**
     * Legacy signature: safely delegates to {@link #findCatalogPage(String, int, int)},
     * ignoring obsolete category and status parameters.
     */
    public List<Facility> findCatalogPage(
            String keyword,
            Facility.Category category,
            Facility.Status status,
            int page,
            int pageSize
    ) throws SQLException {
        return findCatalogPage(keyword, page, pageSize);
    }

    /**
     * Appends search filters for the Facility Catalog keyword search.
     *
     * <p><b>Multi-Field Search Design:</b></p>
     * Searches across:
     * <ul>
     *   <li>{@code Facilities.FacilityName} (case- and accent-insensitive)</li>
     *   <li>{@code Facilities.Location} (case- and accent-insensitive)</li>
     *   <li>{@code Facilities.Category} (English database values: 'Cardio', 'FreeWeights', 'Machines', 'Amenities')</li>
     *   <li>Vietnamese Category display names (e.g. 'Tạ tự do', 'Máy tập', 'Tiện ích', 'Cardio') via SQL CASE mapping</li>
     *   <li>Java-level enum resolution: maps Vietnamese keywords (e.g. 'ta', 'may tap') to database enum values</li>
     * </ul>
     * When {@code keyword} is null or blank, no WHERE clause conditions are added, returning all facilities.
     */
    private void appendCatalogFilters(
            StringBuilder sql,
            List<Object> parameters,
            String keyword
    ) {
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            String searchPattern = "%" + trimmed + "%";

            // Resolve matching Category enums from keyword (supporting Vietnamese and unaccented terms)
            List<Facility.Category> matchedCategories = Facility.Category.matchByKeyword(trimmed);

            sql.append("AND (")
                    .append("f.FacilityName COLLATE Latin1_General_100_CI_AI LIKE ? ")
                    .append("OR f.Location COLLATE Latin1_General_100_CI_AI LIKE ? ")
                    .append("OR f.Category COLLATE Latin1_General_100_CI_AI LIKE ? ")
                    .append("OR (CASE f.Category ")
                    .append("WHEN 'Cardio' THEN N'Cardio' ")
                    .append("WHEN 'FreeWeights' THEN N'Tạ tự do' ")
                    .append("WHEN 'Machines' THEN N'Máy tập' ")
                    .append("WHEN 'Amenities' THEN N'Tiện ích' ")
                    .append("ELSE f.Category END) COLLATE Latin1_General_100_CI_AI LIKE ? ");

            parameters.add(searchPattern);
            parameters.add(searchPattern);
            parameters.add(searchPattern);
            parameters.add(searchPattern);

            if (!matchedCategories.isEmpty()) {
                sql.append("OR f.Category IN (");
                for (int i = 0; i < matchedCategories.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    sql.append("?");
                    parameters.add(matchedCategories.get(i).getDbValue());
                }
                sql.append(") ");
            }

            sql.append(") ");
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

    /**
     * Looks up a single facility by its primary key within an existing transaction or connection.
     *
     * <p><b>Performance Design:</b> This point lookup purposely omits the maintenance request aggregation
     * subquery because callers (such as transactional status updates) only require persistent fields,
     * avoiding unnecessary join overhead during high-frequency transaction processing.</p>
     *
     * @param connection database connection (may participate in caller's active transaction)
     * @param facilityId facility ID to look up
     * @return Facility entity, or null if not found
     * @throws SQLException if a database error occurs
     */
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

    /**
     * Updates the status of a facility within the caller's transaction.
     *
     * <p><b>State Machine Guard:</b> The condition {@code AND Status <> 'Retired'} enforces state
     * safety at the persistence level. A permanently decommissioned/retired facility cannot be
     * transitioned back to 'Available' or 'UnderMaintenance' through automated maintenance workflows.</p>
     *
     * @param connection database connection bound to the caller's active transaction
     * @param facilityId ID of the facility to update
     * @param status     new Facility.Status
     * @return true if exactly one row was updated, false otherwise
     * @throws SQLException if an update error occurs
     */
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

    /**
     * Maps a ResultSet row to a Facility domain entity.
     *
     * <p><b>Design Note:</b> Reading {@code PendingQuantity} and {@code AvailableQuantity} is enclosed
     * in a try-catch block for {@code SQLException}. This allows this single mapping method to be reused
     * across both catalog queries (which compute these columns) and simple lookups like {@code findById}
     * (which omit them for performance), defaulting to {@code pendingQuantity = 0} and
     * {@code availableQuantity = (status == Retired ? 0 : quantity)}.</p>
     */
    private Facility mapResultSetToFacility(ResultSet resultSet)
            throws SQLException {

        int quantity = resultSet.getInt("Quantity");
        int pendingQuantity = 0;
        int availableQuantity = quantity;

        try {
            pendingQuantity = resultSet.getInt("PendingQuantity");
            availableQuantity = resultSet.getInt("AvailableQuantity");
        } catch (SQLException ignored) {
            // Calculated columns are absent in simple queries such as findById
        }

        Facility.Status status = Facility.Status.fromDbValue(resultSet.getString("Status"));
        if (status == Facility.Status.RETIRED) {
            availableQuantity = 0;
        }

        return new Facility(
                resultSet.getInt("FacilityId"),
                resultSet.getString("FacilityName"),
                Facility.Category.fromDbValue(resultSet.getString("Category")),
                resultSet.getString("Location"),
                quantity,
                status,
                pendingQuantity,
                availableQuantity
        );
    }
}