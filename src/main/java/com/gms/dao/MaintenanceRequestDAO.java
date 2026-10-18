package com.gms.dao;

import com.gms.entity.MaintenanceRequest;
import com.gms.util.DBContext;
import com.gms.util.PaginationUtil;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object (DAO) Layer: Manages persistence and retrieval of MaintenanceRequest records.
 *
 * <p><b>Architectural Responsibility:</b></p>
 * Encapsulates all SQL execution, parameter mapping, and ResultSet extraction for maintenance requests
 * (UC-28 and UC-29). Handles database locking hints (e.g., UPDLOCK, HOLDLOCK) to ensure safe concurrent
 * code generation and atomic state transitions under the control of the Service layer's transaction boundaries.
 */
public class MaintenanceRequestDAO {

    // ===== Tạo yêu cầu =====

    /**
     * Generates the next sequential maintenance request code (e.g. 'BT-0001') within the caller's transaction.
     *
     * <p><b>Concurrency & Lock Handling:</b></p>
     * Uses SQL Server locking hints {@code WITH (UPDLOCK, HOLDLOCK)}:
     * <ul>
     *   <li>{@code UPDLOCK}: Acquires an update lock instead of a shared lock, signaling intent to update.</li>
     *   <li>{@code HOLDLOCK}: Holds the lock until the enclosing transaction commits or rolls back
     *       (equivalent to SERIALIZABLE isolation for this table).</li>
     * </ul>
     * This serializes concurrent request creations without creating new database objects (sequences or triggers),
     * completely preventing duplicate code collisions while honoring the strict zero-database-modification rule.
     *
     * @param connection database connection with an active transaction
     * @return next formatted request code, e.g., "BT-0001"
     * @throws SQLException if a query error occurs or code exceeds 9999 limit
     */
    public String generateNextRequestCode(Connection connection)
            throws SQLException {

        String sql = "SELECT ISNULL(MAX(TRY_CONVERT(INT, SUBSTRING(RequestCode, 4, 4))), 0) + 1 AS NextNumber "
                + "FROM MaintenanceRequests WITH (UPDLOCK, HOLDLOCK)";

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            if (!resultSet.next()) {
                throw new SQLException("Không thể sinh mã yêu cầu bảo trì.");
            }

            int nextNumber = resultSet.getInt("NextNumber");

            if (nextNumber > 9999) {
                throw new SQLException("Đã vượt giới hạn mã yêu cầu bảo trì.");
            }

            return String.format("BT-%04d", nextNumber);
        }
    }

    /**
     * Thêm yêu cầu bảo trì.
     *
     * @param connection connection đang mở transaction
     * @param request yêu cầu cần thêm
     * @return id yêu cầu mới
     * @throws SQLException khi thêm thất bại
     */
    public int insert(
            Connection connection,
            MaintenanceRequest request
    ) throws SQLException {

        String sql = "INSERT INTO MaintenanceRequests "
                + "(RequestCode, FacilityId, ReportedByEmployeeId, Priority, "
                + "WarningTagPlaced, Description, Status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
        )) {

            statement.setString(1, request.getRequestCode());
            statement.setInt(2, request.getFacilityId());
            statement.setInt(3, request.getReportedByEmployeeId());
            statement.setString(4, request.getPriority().getDbValue());
            statement.setBoolean(5, request.isWarningTagPlaced());
            statement.setString(6, request.getDescription());
            statement.setString(7, MaintenanceRequest.Status.OPEN.getDbValue());

            statement.executeUpdate();

            try (ResultSet resultSet = statement.getGeneratedKeys()) {
                if (resultSet.next()) {
                    int requestId = resultSet.getInt(1);
                    request.setMaintenanceRequestId(requestId);
                    return requestId;
                }
            }
        }

        throw new SQLException("Không nhận được MaintenanceRequestId.");
    }

    // ===== Tra cứu chi tiết =====

    /**
     * Finds and locks a maintenance request row for update within an active transaction.
     *
     * <p><b>Concurrency Control:</b> Uses {@code WITH (UPDLOCK, HOLDLOCK)} to obtain an exclusive
     * lock on the specific request row until the transaction completes. This prevents race conditions
     * where two managers might simultaneously review, approve, or reject the same request.</p>
     *
     * @param connection database connection with an active transaction
     * @param requestId  ID of the request to lock and read
     * @return locked MaintenanceRequest entity, or null if not found
     * @throws SQLException if a database access error occurs
     */
    public MaintenanceRequest findByIdForUpdate(
            Connection connection,
            int requestId
    ) throws SQLException {

        String sql = "SELECT MaintenanceRequestId, RequestCode, FacilityId, "
                + "ReportedByEmployeeId, ReviewedByEmployeeId, ClosedByEmployeeId, "
                + "Priority, WarningTagPlaced, Description, Status, "
                + "SetUnderMaintenance, RejectReason, AcceptanceNote, "
                + "ReportedAt, ReviewedAt, ClosedAt "
                + "FROM MaintenanceRequests WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE MaintenanceRequestId = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToRequest(resultSet);
                }
            }
        }

        return null;
    }

    public MaintenanceRequestView findViewById(int requestId)
            throws SQLException {

        String sql = buildViewSelect()
                + " WHERE mr.MaintenanceRequestId = ?";

        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, requestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToView(resultSet);
                }
            }
        }

        return null;
    }

    // ===== Danh sách Receptionist (Xem tất cả yêu cầu theo quy tắc mới) =====

    /**
     * Counts requests visible to Receptionists with optional multi-attribute filters.
     *
     * <p><b>Business Rule (BF-06 Requirement #7):</b> Receptionists now have full visibility of all
     * maintenance requests system-wide (delegates to {@code countAll}) to answer member questions
     * regarding machine availability regardless of which staff member filed the initial report.</p>
     */
    public long countForReceptionist(
            String keyword,
            MaintenanceRequest.Status status,
            MaintenanceRequest.Priority priority,
            LocalDate reportedDate
    ) throws SQLException {
        return countAll(keyword, status, priority, reportedDate);
    }

    public long countForReceptionist(
            int employeeId,
            String keyword,
            MaintenanceRequest.Status status
    ) throws SQLException {
        return countForReceptionist(keyword, status, null, null);
    }

    /**
     * Retrieves paginated maintenance requests for Receptionists.
     *
     * <p><b>Ordering Strategy:</b> Orders by status priority:
     * {@code Open (0) -> InProgress (1) -> Rejected (2) -> Closed (3)}, then {@code ReportedAt DESC}.
     * This ensures urgent actionable requests stay at the top of the receptionist's operational view.</p>
     */
    public List<MaintenanceRequestView> findForReceptionist(
            String keyword,
            MaintenanceRequest.Status status,
            MaintenanceRequest.Priority priority,
            LocalDate reportedDate,
            int page,
            int pageSize
    ) throws SQLException {

        StringBuilder sql = new StringBuilder(
                buildViewSelect() + " WHERE 1 = 1 "
        );

        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, keyword, status, priority, reportedDate);

        sql.append(" ORDER BY "
                + "CASE WHEN mr.Status = 'Open' THEN 0 "
                + "WHEN mr.Status = 'InProgress' THEN 1 "
                + "WHEN mr.Status = 'Rejected' THEN 2 ELSE 3 END, "
                + "mr.ReportedAt DESC "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");

        parameters.add(PaginationUtil.calculateOffset(page, pageSize));
        parameters.add(pageSize);

        return executeViewList(sql.toString(), parameters);
    }

    public List<MaintenanceRequestView> findForReceptionist(
            int employeeId,
            String keyword,
            MaintenanceRequest.Status status,
            int page,
            int pageSize
    ) throws SQLException {
        return findForReceptionist(keyword, status, null, null, page, pageSize);
    }

    // ===== Danh sách Manager =====

    public long countAll(
            String keyword,
            MaintenanceRequest.Status status,
            MaintenanceRequest.Priority priority,
            LocalDate reportedDate
    ) throws SQLException {

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) "
                        + "FROM MaintenanceRequests mr "
                        + "JOIN Facilities f ON f.FacilityId = mr.FacilityId "
                        + "JOIN Employees re ON re.EmployeeId = mr.ReportedByEmployeeId "
                        + "JOIN Persons reporter ON reporter.PersonId = re.PersonId "
                        + "WHERE 1 = 1 "
        );

        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, keyword, status, priority, reportedDate);

        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {

            setParameters(statement, parameters);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        }

        return 0;
    }

    public long countAll(
            String keyword,
            MaintenanceRequest.Status status
    ) throws SQLException {
        return countAll(keyword, status, null, null);
    }

    public List<MaintenanceRequestView> findAll(
            String keyword,
            MaintenanceRequest.Status status,
            MaintenanceRequest.Priority priority,
            LocalDate reportedDate,
            int page,
            int pageSize
    ) throws SQLException {

        StringBuilder sql = new StringBuilder(
                buildViewSelect() + " WHERE 1 = 1 "
        );

        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, keyword, status, priority, reportedDate);

        sql.append(" ORDER BY "
                + "CASE WHEN mr.Status = 'Open' THEN 0 "
                + "WHEN mr.Status = 'InProgress' THEN 1 "
                + "WHEN mr.Status = 'Rejected' THEN 2 ELSE 3 END, "
                + "mr.ReportedAt DESC "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");

        parameters.add(PaginationUtil.calculateOffset(page, pageSize));
        parameters.add(pageSize);

        return executeViewList(sql.toString(), parameters);
    }

    public List<MaintenanceRequestView> findAll(
            String keyword,
            MaintenanceRequest.Status status,
            int page,
            int pageSize
    ) throws SQLException {
        return findAll(keyword, status, null, null, page, pageSize);
    }

    public Map<String, Integer> countByStatus() throws SQLException {
        String sql = "SELECT Status, COUNT(*) AS Total "
                + "FROM MaintenanceRequests "
                + "GROUP BY Status";

        Map<String, Integer> counts = new LinkedHashMap<>();

        for (MaintenanceRequest.Status status : MaintenanceRequest.Status.values()) {
            counts.put(status.getDbValue(), 0);
        }

        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                counts.put(
                        resultSet.getString("Status"),
                        resultSet.getInt("Total")
                );
            }
        }

        return counts;
    }

    // ===== Duyệt / từ chối / đóng =====

    /**
     * Approves an Open request to InProgress within an active transaction.
     *
     * <p><b>State Transition Guard:</b> The condition {@code WHERE MaintenanceRequestId = ? AND Status = 'Open'}
     * ensures atomic state transition: if the request is no longer 'Open' (e.g., cancelled or concurrently
     * reviewed), 0 rows are updated and the method returns false, prompting the caller to roll back.</p>
     *
     * @param connection          transactional connection
     * @param requestId           ID of the request to approve
     * @param managerEmployeeId   employee ID of the reviewing manager
     * @param setUnderMaintenance whether the manager flagged the facility for UnderMaintenance
     * @return true if successfully approved, false otherwise
     */
    public boolean approve(
            Connection connection,
            int requestId,
            int managerEmployeeId,
            boolean setUnderMaintenance
    ) throws SQLException {

        String sql = "UPDATE MaintenanceRequests "
                + "SET Status = ?, "
                + "ReviewedByEmployeeId = ?, "
                + "ReviewedAt = SYSDATETIME(), "
                + "SetUnderMaintenance = ?, "
                + "RejectReason = NULL "
                + "WHERE MaintenanceRequestId = ? "
                + "AND Status = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MaintenanceRequest.Status.IN_PROGRESS.getDbValue());
            statement.setInt(2, managerEmployeeId);
            statement.setBoolean(3, setUnderMaintenance);
            statement.setInt(4, requestId);
            statement.setString(5, MaintenanceRequest.Status.OPEN.getDbValue());

            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Rejects an Open request within an active transaction, saving the rejection reason.
     *
     * <p><b>State Transition Guard:</b> Requires {@code Status = 'Open'}. Once rejected, the request
     * transitions out of active status, immediately releasing the facility's {@code PendingQuantity}.</p>
     */
    public boolean reject(
            Connection connection,
            int requestId,
            int managerEmployeeId,
            String rejectReason
    ) throws SQLException {

        String sql = "UPDATE MaintenanceRequests "
                + "SET Status = ?, "
                + "ReviewedByEmployeeId = ?, "
                + "ReviewedAt = SYSDATETIME(), "
                + "SetUnderMaintenance = NULL, "
                + "RejectReason = ? "
                + "WHERE MaintenanceRequestId = ? "
                + "AND Status = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MaintenanceRequest.Status.REJECTED.getDbValue());
            statement.setInt(2, managerEmployeeId);
            statement.setString(3, rejectReason);
            statement.setInt(4, requestId);
            statement.setString(5, MaintenanceRequest.Status.OPEN.getDbValue());

            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Closes an InProgress request upon post-maintenance inspection and acceptance by a Receptionist.
     *
     * <p><b>State Transition Guard:</b> Requires {@code Status = 'InProgress'}. Closing the request
     * decrements {@code PendingQuantity} and increments {@code AvailableQuantity} on subsequent catalog queries.</p>
     */
    public boolean close(
            Connection connection,
            int requestId,
            int receptionistEmployeeId,
            String acceptanceNote
    ) throws SQLException {

        String sql = "UPDATE MaintenanceRequests "
                + "SET Status = ?, "
                + "ClosedByEmployeeId = ?, "
                + "ClosedAt = SYSDATETIME(), "
                + "AcceptanceNote = ? "
                + "WHERE MaintenanceRequestId = ? "
                + "AND Status = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MaintenanceRequest.Status.CLOSED.getDbValue());
            statement.setInt(2, receptionistEmployeeId);
            statement.setString(3, acceptanceNote);
            statement.setInt(4, requestId);
            statement.setString(5, MaintenanceRequest.Status.IN_PROGRESS.getDbValue());

            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Checks if there are any remaining InProgress maintenance requests for a given facility.
     *
     * <p><b>Facility Recovery Logic:</b> When a receptionist closes an InProgress request, the system must
     * verify whether any OTHER ongoing requests exist for the same facility before restoring the facility's
     * status to 'Available'. If this query returns true, the facility must remain 'UnderMaintenance'.</p>
     */
    public boolean hasInProgressRequest(
            Connection connection,
            int facilityId
    ) throws SQLException {

        String sql = "SELECT TOP 1 1 "
                + "FROM MaintenanceRequests "
                + "WHERE FacilityId = ? "
                + "AND Status = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, facilityId);
            statement.setString(2, MaintenanceRequest.Status.IN_PROGRESS.getDbValue());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    // ===== Mapping =====

    private String buildViewSelect() {
        return "SELECT "
                + "mr.MaintenanceRequestId, mr.RequestCode, mr.FacilityId, "
                + "mr.ReportedByEmployeeId, mr.ReviewedByEmployeeId, mr.ClosedByEmployeeId, "
                + "mr.Priority, mr.WarningTagPlaced, mr.Description, mr.Status, "
                + "mr.SetUnderMaintenance, mr.RejectReason, mr.AcceptanceNote, "
                + "mr.ReportedAt, mr.ReviewedAt, mr.ClosedAt, "
                + "f.FacilityName, f.Location AS FacilityLocation, "
                + "reporter.FullName AS ReporterName, "
                + "reviewer.FullName AS ReviewerName, "
                + "closer.FullName AS CloserName "
                + "FROM MaintenanceRequests mr "
                + "JOIN Facilities f ON f.FacilityId = mr.FacilityId "
                + "JOIN Employees re ON re.EmployeeId = mr.ReportedByEmployeeId "
                + "JOIN Persons reporter ON reporter.PersonId = re.PersonId "
                + "LEFT JOIN Employees rv ON rv.EmployeeId = mr.ReviewedByEmployeeId "
                + "LEFT JOIN Persons reviewer ON reviewer.PersonId = rv.PersonId "
                + "LEFT JOIN Employees cl ON cl.EmployeeId = mr.ClosedByEmployeeId "
                + "LEFT JOIN Persons closer ON closer.PersonId = cl.PersonId ";
    }

    private void appendFilters(
            StringBuilder sql,
            List<Object> parameters,
            String keyword,
            MaintenanceRequest.Status status,
            MaintenanceRequest.Priority priority,
            LocalDate reportedDate
    ) {

        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND ("
                    + "mr.RequestCode COLLATE Latin1_General_100_CI_AI LIKE ? "
                    + "OR f.FacilityName COLLATE Latin1_General_100_CI_AI LIKE ? "
                    + "OR f.Location COLLATE Latin1_General_100_CI_AI LIKE ? "
                    + "OR mr.Description COLLATE Latin1_General_100_CI_AI LIKE ? "
                    + "OR reporter.FullName COLLATE Latin1_General_100_CI_AI LIKE ?"
                    + ") ");

            String likeKeyword = "%" + keyword.trim() + "%";
            parameters.add(likeKeyword);
            parameters.add(likeKeyword);
            parameters.add(likeKeyword);
            parameters.add(likeKeyword);
            parameters.add(likeKeyword);
        }

        if (status != null) {
            sql.append("AND mr.Status = ? ");
            parameters.add(status.getDbValue());
        }

        if (priority != null) {
            sql.append("AND mr.Priority = ? ");
            parameters.add(priority.getDbValue());
        }

        if (reportedDate != null) {
            sql.append("AND CAST(mr.ReportedAt AS DATE) = ? ");
            parameters.add(Date.valueOf(reportedDate));
        }
    }

    private void appendFilters(
            StringBuilder sql,
            List<Object> parameters,
            String keyword,
            MaintenanceRequest.Status status
    ) {
        appendFilters(sql, parameters, keyword, status, null, null);
    }

    private List<MaintenanceRequestView> executeViewList(
            String sql,
            List<Object> parameters
    ) throws SQLException {

        List<MaintenanceRequestView> items = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            setParameters(statement, parameters);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(mapResultSetToView(resultSet));
                }
            }
        }

        return items;
    }

    private void setParameters(
            PreparedStatement statement,
            List<Object> parameters
    ) throws SQLException {

        for (int i = 0; i < parameters.size(); i++) {
            Object value = parameters.get(i);

            if (value instanceof Integer intValue) {
                statement.setInt(i + 1, intValue);
            } else if (value instanceof Date sqlDate) {
                statement.setDate(i + 1, sqlDate);
            } else if (value instanceof Boolean boolValue) {
                statement.setBoolean(i + 1, boolValue);
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
    }

    // ===== Truy vấn yêu cầu chưa hoàn tất (cho cảnh báo) =====

    /**
     * Retrieves all active (unfinished: Open or InProgress) maintenance requests for a specific facility.
     * Used to display warning banners and cross-links when a receptionist creates a new request.
     *
     * @param facilityId ID of the facility
     * @return list of unfinished request summaries sorted with Open requests first
     * @throws SQLException if a query error occurs
     */
    public List<UnfinishedRequestSummary> findUnfinishedByFacilityId(int facilityId) throws SQLException {
        String sql = "SELECT mr.MaintenanceRequestId, mr.RequestCode, mr.FacilityId, "
                + "mr.Status, mr.Priority, mr.Description, mr.ReportedAt, f.FacilityName "
                + "FROM MaintenanceRequests mr "
                + "JOIN Facilities f ON f.FacilityId = mr.FacilityId "
                + "WHERE mr.FacilityId = ? AND mr.Status IN ('Open', 'InProgress') "
                + "ORDER BY CASE mr.Status WHEN 'Open' THEN 0 ELSE 1 END, mr.ReportedAt DESC";

        List<UnfinishedRequestSummary> list = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, facilityId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToUnfinishedSummary(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retrieves all unfinished maintenance requests across all facilities, grouped by FacilityId in a Map.
     *
     * <p><b>Performance Rationale:</b> Fetches all active requests in a single round-trip query and groups them
     * into a JSON-serializable Map. The JSP view embeds this data client-side, allowing instantaneous,
     * zero-latency warning banner updates whenever the user changes the facility dropdown, avoiding AJAX calls.</p>
     *
     * @return Map where key is FacilityId and value is the list of active UnfinishedRequestSummary objects
     * @throws SQLException if a query error occurs
     */
    public Map<Integer, List<UnfinishedRequestSummary>> findUnfinishedRequestsMap() throws SQLException {
        String sql = "SELECT mr.MaintenanceRequestId, mr.RequestCode, mr.FacilityId, "
                + "mr.Status, mr.Priority, mr.Description, mr.ReportedAt, f.FacilityName "
                + "FROM MaintenanceRequests mr "
                + "JOIN Facilities f ON f.FacilityId = mr.FacilityId "
                + "WHERE mr.Status IN ('Open', 'InProgress') "
                + "ORDER BY mr.FacilityId, CASE mr.Status WHEN 'Open' THEN 0 ELSE 1 END, mr.ReportedAt DESC";

        Map<Integer, List<UnfinishedRequestSummary>> map = new LinkedHashMap<>();
        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                UnfinishedRequestSummary summary = mapResultSetToUnfinishedSummary(rs);
                map.computeIfAbsent(summary.getFacilityId(), k -> new ArrayList<>()).add(summary);
            }
        }
        return map;
    }

    /**
     * Counts active unfinished requests for a facility within a transaction connection.
     */
    public int countUnfinishedByFacility(Connection connection, int facilityId) throws SQLException {
        String sql = "SELECT COUNT(*) "
                + "FROM MaintenanceRequests "
                + "WHERE FacilityId = ? AND Status IN ('Open', 'InProgress')";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, facilityId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private UnfinishedRequestSummary mapResultSetToUnfinishedSummary(ResultSet rs) throws SQLException {
        MaintenanceRequest.Status st = MaintenanceRequest.Status.fromDbValue(rs.getString("Status"));
        MaintenanceRequest.Priority pr = MaintenanceRequest.Priority.fromDbValue(rs.getString("Priority"));
        Timestamp ts = rs.getTimestamp("ReportedAt");
        LocalDateTime reportedAt = ts == null ? null : ts.toLocalDateTime();
        return new UnfinishedRequestSummary(
                rs.getInt("MaintenanceRequestId"),
                rs.getString("RequestCode"),
                rs.getInt("FacilityId"),
                rs.getString("FacilityName"),
                rs.getString("Status"),
                st != null ? st.getDisplayName() : rs.getString("Status"),
                rs.getString("Priority"),
                pr != null ? pr.getDisplayName() : rs.getString("Priority"),
                rs.getString("Description"),
                reportedAt
        );
    }

    private MaintenanceRequest mapResultSetToRequest(ResultSet resultSet)
            throws SQLException {

        MaintenanceRequest request = new MaintenanceRequest();

        request.setMaintenanceRequestId(resultSet.getInt("MaintenanceRequestId"));
        request.setRequestCode(resultSet.getString("RequestCode"));
        request.setFacilityId(resultSet.getInt("FacilityId"));
        request.setReportedByEmployeeId(resultSet.getInt("ReportedByEmployeeId"));

        int reviewedBy = resultSet.getInt("ReviewedByEmployeeId");
        request.setReviewedByEmployeeId(resultSet.wasNull() ? null : reviewedBy);

        int closedBy = resultSet.getInt("ClosedByEmployeeId");
        request.setClosedByEmployeeId(resultSet.wasNull() ? null : closedBy);

        request.setPriority(
                MaintenanceRequest.Priority.fromDbValue(
                        resultSet.getString("Priority")
                )
        );

        request.setWarningTagPlaced(resultSet.getBoolean("WarningTagPlaced"));
        request.setDescription(resultSet.getString("Description"));

        request.setStatus(
                MaintenanceRequest.Status.fromDbValue(
                        resultSet.getString("Status")
                )
        );

        Object setUnderMaintenance =
                resultSet.getObject("SetUnderMaintenance");

        request.setSetUnderMaintenance(
                setUnderMaintenance == null
                        ? null
                        : resultSet.getBoolean("SetUnderMaintenance")
        );

        request.setRejectReason(resultSet.getString("RejectReason"));
        request.setAcceptanceNote(resultSet.getString("AcceptanceNote"));

        Timestamp reportedAt = resultSet.getTimestamp("ReportedAt");
        request.setReportedAt(
                reportedAt == null ? null : reportedAt.toLocalDateTime()
        );

        Timestamp reviewedAt = resultSet.getTimestamp("ReviewedAt");
        request.setReviewedAt(
                reviewedAt == null ? null : reviewedAt.toLocalDateTime()
        );

        Timestamp closedAt = resultSet.getTimestamp("ClosedAt");
        request.setClosedAt(
                closedAt == null ? null : closedAt.toLocalDateTime()
        );

        return request;
    }

    private MaintenanceRequestView mapResultSetToView(ResultSet resultSet)
            throws SQLException {

        return new MaintenanceRequestView(
                mapResultSetToRequest(resultSet),
                resultSet.getString("FacilityName"),
                resultSet.getString("FacilityLocation"),
                resultSet.getString("ReporterName"),
                resultSet.getString("ReviewerName"),
                resultSet.getString("CloserName")
        );
    }

    /**
     * Dữ liệu yêu cầu bảo trì đã nối tên để hiển thị trên View.
     */
    public static class MaintenanceRequestView {

        private final MaintenanceRequest request;
        private final String facilityName;
        private final String facilityLocation;
        private final String reporterName;
        private final String reviewerName;
        private final String closerName;

        public MaintenanceRequestView(
                MaintenanceRequest request,
                String facilityName,
                String facilityLocation,
                String reporterName,
                String reviewerName,
                String closerName
        ) {
            this.request = request;
            this.facilityName = facilityName;
            this.facilityLocation = facilityLocation;
            this.reporterName = reporterName;
            this.reviewerName = reviewerName;
            this.closerName = closerName;
        }

        public MaintenanceRequest getRequest() {
            return request;
        }

        public String getFacilityName() {
            return facilityName;
        }

        public String getFacilityLocation() {
            return facilityLocation;
        }

        public String getReporterName() {
            return reporterName;
        }

        public String getReviewerName() {
            return reviewerName;
        }

        public String getCloserName() {
            return closerName;
        }
    }

    /**
     * Data Transfer Object (DTO) / Projection: Represents a lightweight summary of an active
     * (Open or InProgress) maintenance request for UI warning banners and cross-linking.
     *
     * <p><b>Purpose:</b> Instead of sending full entity models with unneeded foreign key associations,
     * this immutable projection encapsulates only the data required to warn the receptionist about existing
     * pending work on a selected facility, including status/priority display names and timestamps.</p>
     */
    public static class UnfinishedRequestSummary {

        private final int maintenanceRequestId;
        private final String requestCode;
        private final int facilityId;
        private final String facilityName;
        private final String status;
        private final String statusDisplayName;
        private final String priority;
        private final String priorityDisplayName;
        private final String description;
        private final LocalDateTime reportedAt;

        public UnfinishedRequestSummary(
                int maintenanceRequestId,
                String requestCode,
                int facilityId,
                String facilityName,
                String status,
                String statusDisplayName,
                String priority,
                String priorityDisplayName,
                String description,
                LocalDateTime reportedAt
        ) {
            this.maintenanceRequestId = maintenanceRequestId;
            this.requestCode = requestCode;
            this.facilityId = facilityId;
            this.facilityName = facilityName;
            this.status = status;
            this.statusDisplayName = statusDisplayName;
            this.priority = priority;
            this.priorityDisplayName = priorityDisplayName;
            this.description = description;
            this.reportedAt = reportedAt;
        }

        public int getMaintenanceRequestId() {
            return maintenanceRequestId;
        }

        public String getRequestCode() {
            return requestCode;
        }

        public int getFacilityId() {
            return facilityId;
        }

        public String getFacilityName() {
            return facilityName;
        }

        public String getStatus() {
            return status;
        }

        public String getStatusDisplayName() {
            return statusDisplayName;
        }

        public String getPriority() {
            return priority;
        }

        public String getPriorityDisplayName() {
            return priorityDisplayName;
        }

        public String getDescription() {
            return description;
        }

        public LocalDateTime getReportedAt() {
            return reportedAt;
        }
    }
}