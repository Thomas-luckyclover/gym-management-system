package com.gms.dao;

import com.gms.entity.MaintenanceRequest;
import com.gms.util.DBContext;
import com.gms.util.PaginationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Truy vấn yêu cầu bảo trì cơ sở vật chất (UC-28, UC-29).
 */
public class MaintenanceRequestDAO {

    // ===== Tạo yêu cầu =====

    /**
     * Sinh mã yêu cầu tiếp theo trong transaction hiện tại.
     *
     * @param connection connection đang mở transaction
     * @return mã dạng BT-0001
     * @throws SQLException khi không sinh được mã
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

    // ===== Danh sách Receptionist =====

    public long countForReceptionist(
            int employeeId,
            String keyword,
            MaintenanceRequest.Status status
    ) throws SQLException {

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) "
                        + "FROM MaintenanceRequests mr "
                        + "JOIN Facilities f ON f.FacilityId = mr.FacilityId "
                        + "WHERE (mr.ReportedByEmployeeId = ? OR mr.Status = ?) "
        );

        List<Object> parameters = new ArrayList<>();
        parameters.add(employeeId);
        parameters.add(MaintenanceRequest.Status.IN_PROGRESS.getDbValue());

        appendFilters(sql, parameters, keyword, status);

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

    public List<MaintenanceRequestView> findForReceptionist(
            int employeeId,
            String keyword,
            MaintenanceRequest.Status status,
            int page,
            int pageSize
    ) throws SQLException {

        StringBuilder sql = new StringBuilder(
                buildViewSelect()
                        + " WHERE (mr.ReportedByEmployeeId = ? OR mr.Status = ?) "
        );

        List<Object> parameters = new ArrayList<>();
        parameters.add(employeeId);
        parameters.add(MaintenanceRequest.Status.IN_PROGRESS.getDbValue());

        appendFilters(sql, parameters, keyword, status);

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

    // ===== Danh sách Manager =====

    public long countAll(
            String keyword,
            MaintenanceRequest.Status status
    ) throws SQLException {

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) "
                        + "FROM MaintenanceRequests mr "
                        + "JOIN Facilities f ON f.FacilityId = mr.FacilityId "
                        + "WHERE 1 = 1 "
        );

        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, keyword, status);

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

    public List<MaintenanceRequestView> findAll(
            String keyword,
            MaintenanceRequest.Status status,
            int page,
            int pageSize
    ) throws SQLException {

        StringBuilder sql = new StringBuilder(
                buildViewSelect() + " WHERE 1 = 1 "
        );

        List<Object> parameters = new ArrayList<>();
        appendFilters(sql, parameters, keyword, status);

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
            MaintenanceRequest.Status status
    ) {

        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND ("
                    + "mr.RequestCode COLLATE Latin1_General_100_CI_AI LIKE ? "
                    + "OR f.FacilityName COLLATE Latin1_General_100_CI_AI LIKE ?"
                    + ") ");

            String likeKeyword = "%" + keyword.trim() + "%";
            parameters.add(likeKeyword);
            parameters.add(likeKeyword);
        }

        if (status != null) {
            sql.append("AND mr.Status = ? ");
            parameters.add(status.getDbValue());
        }
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
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
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
}