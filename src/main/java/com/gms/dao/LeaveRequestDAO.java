package com.gms.dao;

import com.gms.dto.EligibleReviewer;
import com.gms.dto.LeaveBalance;
import com.gms.dto.LeaveEmployeeView;
import com.gms.dto.LeaveRequestFilter;
import com.gms.dto.LeaveRequestRow;
import com.gms.dto.LeaveReviewDetail;
import com.gms.dto.SessionConflict;
import com.gms.entity.LeaveRequest;
import com.gms.entity.LeaveRequestTransfer;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.SqlUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Truy vấn bảng LeaveRequests (BF-05, UC-14, UC-15, JOB-07). */
public class LeaveRequestDAO {

    /** Danh sách cột LeaveRequests dùng chung cho mọi câu SELECT có map sang entity. */
    private static final String REQUEST_COLUMNS
            = "LeaveRequestId, EmployeeId, ReviewedByEmployeeId, AssignedToEmployeeId, LeaveType, "
            + "FromDate, ToDate, Days, ShiftType, Reason, Status, RejectReason, "
            + "SubmittedAt, ReviewedAt, ReminderSentAt ";

    private static final String SELECT_COLUMNS = "SELECT " + REQUEST_COLUMNS + "FROM LeaveRequests ";

    /**
     * Tìm một đơn nghỉ phép theo mã đơn.
     *
     * @param leaveRequestId mã đơn LeaveRequests.LeaveRequestId
     * @return entity LeaveRequest, hoặc null nếu không tìm thấy
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public LeaveRequest findById(int leaveRequestId) throws SQLException {
        // B1: câu SQL chỉ lấy đúng 1 đơn theo khóa chính
        String sql = SELECT_COLUMNS
                + "WHERE LeaveRequestId = ?";

        // B2: mở kết nối, truyền tham số và đọc kết quả
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setInt(1, leaveRequestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToLeaveRequest(resultSet);
                }
            }
        }

        // B3: không có dòng nào khớp
        return null;
    }

    /** Đọc lại đơn trong transaction quyết định; giữ khóa đến khi commit/rollback. */
    public LeaveRequest findById(Connection connection, int leaveRequestId) throws SQLException {
        String sql = "SELECT " + REQUEST_COLUMNS + "FROM LeaveRequests WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE LeaveRequestId = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, leaveRequestId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapResultSetToLeaveRequest(resultSet) : null;
            }
        }
    }

    /**
     * Tuần tự hóa quyết định BF-05 trên mọi khoảng ngày và quỹ phép.
     * Khóa bảng phù hợp quy mô đồ án; cả chuyển/rút/nộp đơn đều dùng cùng thứ tự khóa.
     */
    public void lockDecisionScope(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM LeaveRequests WITH (TABLOCKX, HOLDLOCK)");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
        }
    }

    /** Vị trí hiện tại của nhân viên Working; giữ khóa để tránh đổi vị trí giữa lúc quyết định. */
    public String findWorkingPosition(Connection connection, int employeeId) throws SQLException {
        String sql = "SELECT Position FROM Employees WITH (HOLDLOCK) WHERE EmployeeId = ? AND Status = 'Working'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, employeeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("Position") : null;
            }
        }
    }

    private static class FilterSql {
        final String whereClause;
        final List<Object> params;
        FilterSql(String whereClause, List<Object> params) {
            this.whereClause = whereClause;
            this.params = params;
        }
    }

    private FilterSql buildFilterSql(LeaveRequestFilter filter, boolean includeStatus) {
        StringBuilder where = new StringBuilder(" WHERE e.Position IN ('PT', 'Receptionist') ");
        List<Object> params = new ArrayList<>();

        if (filter != null) {
            if (includeStatus && filter.getStatus() != null) {
                where.append(" AND lr.Status = ? ");
                params.add(filter.getStatus().getDbValue());
            }
            if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                where.append(" AND (p.FullName COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\\' OR e.EmployeeCode LIKE ?) ");
                String kw = SqlUtil.toContainsPattern(filter.getKeyword());
                params.add(kw);
                params.add("%" + filter.getKeyword().trim() + "%");
            }
            if (filter.getPosition() != null && !filter.getPosition().isBlank()) {
                where.append(" AND e.Position = ? ");
                params.add(filter.getPosition());
            }
            if (filter.getLeaveType() != null) {
                where.append(" AND lr.LeaveType = ? ");
                params.add(filter.getLeaveType().getDbValue());
            }
            if (filter.getFromDate() != null) {
                where.append(" AND lr.ToDate >= ? ");
                params.add(java.sql.Date.valueOf(filter.getFromDate()));
            }
            if (filter.getToDate() != null) {
                where.append(" AND lr.FromDate <= ? ");
                params.add(java.sql.Date.valueOf(filter.getToDate()));
            }
        }
        return new FilterSql(where.toString(), params);
    }

    /** Chỉ tên/mã và chức vụ lọc nhân viên; bộ lọc đơn không làm mất người chưa có đơn. */
    private FilterSql buildEmployeeFilterSql(LeaveRequestFilter filter) {
        if (filter == null) {
            return buildFilterSql(null, false);
        }
        LeaveRequestFilter employeeFilter = new LeaveRequestFilter(null, filter.getKeyword(), filter.getPosition(),
                null, null, null);
        return buildFilterSql(employeeFilter, false);
    }

    /** Đếm nhân viên PT/Lễ tân, kể cả người chưa có đơn hoặc đã nghỉ việc để giữ lịch sử. */
    public int countEmployees(LeaveRequestFilter filter) throws SQLException {
        FilterSql employeeFilter = buildEmployeeFilterSql(filter);
        String sql = "SELECT COUNT(*) FROM Employees e JOIN Persons p ON p.PersonId = e.PersonId "
                + employeeFilter.whereClause;
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            bindFilterParameters(statement, employeeFilter.params, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    /**
     * B1: LEFT JOIN để đếm toàn bộ đơn mà vẫn giữ nhân viên chưa có đơn.
     * B2: ưu tiên đơn chờ sắp nghỉ, rồi phân trang theo nhân viên ngay trong SQL.
     */
    public List<LeaveEmployeeView> findEmployeePage(LeaveRequestFilter filter, int offset, int pageSize)
            throws SQLException {
        FilterSql employeeFilter = buildEmployeeFilterSql(filter);
        String sql = "WITH EmployeeLeaveCounts AS ("
                + " SELECT e.EmployeeId, e.EmployeeCode, p.FullName, e.Position, p.Avatar, e.Status AS EmploymentStatus, "
                + " COUNT(lr.LeaveRequestId) AS TotalRequestCount, "
                + " SUM(CASE WHEN lr.Status = 'Pending' THEN 1 ELSE 0 END) AS PendingCount, "
                + " MIN(CASE WHEN lr.Status = 'Pending' AND lr.FromDate >= ? THEN lr.FromDate END) AS NextPendingDate "
                + " FROM Employees e JOIN Persons p ON p.PersonId = e.PersonId "
                + " LEFT JOIN LeaveRequests lr ON lr.EmployeeId = e.EmployeeId "
                + employeeFilter.whereClause
                + " GROUP BY e.EmployeeId, e.EmployeeCode, p.FullName, e.Position, p.Avatar, e.Status "
                + ") SELECT * FROM EmployeeLeaveCounts "
                + "ORDER BY CASE WHEN NextPendingDate IS NOT NULL THEN 0 WHEN PendingCount > 0 THEN 1 ELSE 2 END, "
                + " NextPendingDate ASC, FullName ASC, EmployeeId ASC "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        List<LeaveEmployeeView> employees = new ArrayList<>();
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, java.sql.Date.valueOf(DateTimeUtil.today()));
            int paramIndex = bindFilterParameters(statement, employeeFilter.params, 2);
            statement.setInt(paramIndex++, offset);
            statement.setInt(paramIndex, pageSize);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    LeaveEmployeeView employee = new LeaveEmployeeView(resultSet.getInt("EmployeeId"),
                            resultSet.getString("EmployeeCode"), resultSet.getString("FullName"),
                            resultSet.getString("Position"), resultSet.getString("Avatar"), resultSet.getString("EmploymentStatus"));
                    employee.setTotalRequestCount(resultSet.getInt("TotalRequestCount"));
                    employee.setPendingCount(resultSet.getInt("PendingCount"));
                    java.sql.Date nextDate = resultSet.getDate("NextPendingDate");
                    employee.setNextPendingDate(nextDate == null ? null : nextDate.toLocalDate());
                    employees.add(employee);
                }
            }
        }
        return employees;
    }

    /** Truyền các bộ lọc String/Date theo đúng thứ tự dấu ?; trả vị trí tham số tiếp theo. */
    private int bindFilterParameters(PreparedStatement statement, List<Object> parameters, int firstIndex)
            throws SQLException {
        int index = firstIndex;
        for (Object parameter : parameters) {
            if (parameter instanceof java.sql.Date) {
                statement.setDate(index++, (java.sql.Date) parameter);
            } else {
                statement.setString(index++, (String) parameter);
            }
        }
        return index;
    }

    /** Lấy đơn của cả trang nhân viên trong một truy vấn; không phân trang cắt đôi đơn của một người. */
    public List<LeaveRequestRow> findEmployeeRequests(LeaveRequestFilter filter, List<Integer> employeeIds,
            Integer currentManagerEmployeeId) throws SQLException {
        if (employeeIds.isEmpty()) {
            return new ArrayList<>();
        }
        FilterSql fs = buildFilterSql(filter, true);
        StringBuilder employeeIdsSql = new StringBuilder();
        for (int index = 0; index < employeeIds.size(); index++) {
            if (index > 0) {
                employeeIdsSql.append(", ");
            }
            employeeIdsSql.append("?");
        }
        String sql = "SELECT "
                + "  lr.LeaveRequestId, lr.EmployeeId, lr.ReviewedByEmployeeId, lr.AssignedToEmployeeId, "
                + "  lr.LeaveType, lr.FromDate, lr.ToDate, lr.Days, lr.ShiftType, lr.Reason, lr.Status, lr.RejectReason, "
                + "  lr.SubmittedAt, lr.ReviewedAt, lr.ReminderSentAt, "
                + "  e.EmployeeCode, e.Position, "
                + "  p.FullName AS EmployeeName, p.Avatar, "
                + "  rp.FullName AS ReviewerName, "
                + "  ap.FullName AS AssignedToName "
                + "FROM LeaveRequests lr "
                + "  JOIN Employees e ON e.EmployeeId = lr.EmployeeId "
                + "  JOIN Persons p ON p.PersonId = e.PersonId "
                + "  LEFT JOIN Employees re ON re.EmployeeId = lr.ReviewedByEmployeeId "
                + "  LEFT JOIN Persons rp ON rp.PersonId = re.PersonId "
                + "  LEFT JOIN Employees ae ON ae.EmployeeId = lr.AssignedToEmployeeId "
                + "  LEFT JOIN Persons ap ON ap.PersonId = ae.PersonId "
                + fs.whereClause
                + " AND e.EmployeeId IN (" + employeeIdsSql + ") "
                + "ORDER BY "
                + "  CASE WHEN lr.Status = 'Pending' AND lr.FromDate >= ? THEN 0 "
                + "       WHEN lr.Status = 'Pending' THEN 1 ELSE 2 END, "
                + "  CASE WHEN lr.Status = 'Pending' THEN lr.FromDate END ASC, "
                + "  lr.FromDate DESC, lr.SubmittedAt ASC, lr.LeaveRequestId ASC ";

        List<LeaveRequestRow> rows = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            int paramIndex = bindFilterParameters(statement, fs.params, 1);
            for (Integer employeeId : employeeIds) {
                statement.setInt(paramIndex++, employeeId);
            }
            statement.setDate(paramIndex, java.sql.Date.valueOf(DateTimeUtil.today()));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    LeaveRequest lr = mapResultSetToLeaveRequest(resultSet);
                    LeaveRequestRow row = new LeaveRequestRow(
                            lr,
                            resultSet.getString("EmployeeCode"),
                            resultSet.getString("EmployeeName"),
                            resultSet.getString("Position"),
                            resultSet.getString("Avatar"),
                            resultSet.getString("ReviewerName"));

                    row.setAssignedToName(resultSet.getString("AssignedToName"));
                    boolean canAct = false;
                    if (lr.isPending() && currentManagerEmployeeId != null) {
                        if (lr.getAssignedToEmployeeId() == null) {
                            canAct = true;
                        } else if (currentManagerEmployeeId != null && lr.getAssignedToEmployeeId().equals(currentManagerEmployeeId)) {
                            canAct = true;
                        }
                    }
                    row.setCanAct(canAct);
                    rows.add(row);
                }
            }
        }

        return rows;
    }

    public Map<String, Integer> getStatusCounts() throws SQLException {
        return getStatusCounts(null);
    }

    public Map<String, Integer> getStatusCounts(LeaveRequestFilter filter) throws SQLException {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(LeaveRequest.Status.PENDING.getDbValue(), 0);
        counts.put(LeaveRequest.Status.APPROVED.getDbValue(), 0);
        counts.put(LeaveRequest.Status.REJECTED.getDbValue(), 0);

        FilterSql fs = buildFilterSql(filter, false);
        String sql = "SELECT lr.Status, COUNT(*) AS Total "
                + "FROM LeaveRequests lr "
                + "  JOIN Employees e ON e.EmployeeId = lr.EmployeeId "
                + "  JOIN Persons p ON p.PersonId = e.PersonId "
                + fs.whereClause
                + "GROUP BY lr.Status";

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int i = 0; i < fs.params.size(); i++) {
                Object p = fs.params.get(i);
                if (p instanceof java.sql.Date) {
                    statement.setDate(i + 1, (java.sql.Date) p);
                } else {
                    statement.setString(i + 1, (String) p);
                }
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    counts.put(resultSet.getString("Status"),
                            resultSet.getInt("Total"));
                }
            }
        }

        return counts;
    }

    public LeaveReviewDetail findReviewDetail(int leaveRequestId) throws SQLException {
        return findReviewDetail(leaveRequestId, null);
    }

    public LeaveReviewDetail findReviewDetail(int leaveRequestId, Integer currentManagerEmployeeId) throws SQLException {
        String sql = "SELECT lr.LeaveRequestId, lr.EmployeeId, "
                + "lr.ReviewedByEmployeeId, lr.AssignedToEmployeeId, lr.LeaveType, "
                + "lr.FromDate, lr.ToDate, lr.Days, "
                + "lr.ShiftType, lr.Reason, lr.Status, lr.RejectReason, "
                + "lr.SubmittedAt, lr.ReviewedAt, lr.ReminderSentAt, "
                + "e.EmployeeCode, e.Position, "
                + "p.FullName AS EmployeeName, p.Avatar, "
                + "rp.FullName AS ReviewerName, "
                + "ap.FullName AS AssignedToName "
                + "FROM LeaveRequests lr "
                + "JOIN Employees e ON e.EmployeeId = lr.EmployeeId "
                + "JOIN Persons p ON p.PersonId = e.PersonId "
                + "LEFT JOIN Employees re ON re.EmployeeId = lr.ReviewedByEmployeeId "
                + "LEFT JOIN Persons rp ON rp.PersonId = re.PersonId "
                + "LEFT JOIN Employees ae ON ae.EmployeeId = lr.AssignedToEmployeeId "
                + "LEFT JOIN Persons ap ON ap.PersonId = ae.PersonId "
                + "WHERE lr.LeaveRequestId = ?";

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, leaveRequestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    LeaveRequest lr = mapResultSetToLeaveRequest(resultSet);
                    LeaveReviewDetail detail = new LeaveReviewDetail(
                            lr,
                            resultSet.getString("EmployeeCode"),
                            resultSet.getString("EmployeeName"),
                            resultSet.getString("Position"),
                            resultSet.getString("Avatar"),
                            resultSet.getString("ReviewerName"));

                    detail.setAssignedToEmployeeId(lr.getAssignedToEmployeeId());
                    detail.setAssignedToName(resultSet.getString("AssignedToName"));

                    boolean canAct = false;
                    if (lr.isPending() && currentManagerEmployeeId != null) {
                        if (lr.getAssignedToEmployeeId() == null) {
                            canAct = true;
                        } else if (currentManagerEmployeeId != null && lr.getAssignedToEmployeeId().equals(currentManagerEmployeeId)) {
                            canAct = true;
                        }
                    }
                    detail.setCanAct(canAct);
                    return detail;
                }
            }
        }

        return null;
    }

    /**
     * Đếm số nhân viên theo chức vụ còn trực ca trong ngày sau khi trừ các đơn nghỉ Approved.
     * Dùng để kiểm tra min-staffing trước khi Manager duyệt thêm (CBR-88, CBR-90, BR-14).
     *
     * @param leaveDate ngày nghỉ cần kiểm tra
     * @param shiftType ca cần kiểm tra (Morning/Afternoon/FullDay)
     * @param position  chức vụ (PT/Receptionist)
     * @return số nhân viên sẽ còn trực nếu đơn này được duyệt
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public int countOnDutyAfterApproval(
            LocalDate leaveDate,
            String shiftType,
            String position,
            int excludeLeaveRequestId
    ) throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return countOnDutyAfterApproval(connection, leaveDate, shiftType, position, excludeLeaveRequestId);
        }
    }

    /**
     * Đếm số nhân viên theo chức vụ còn trực ca trong ngày sau khi trừ các đơn nghỉ Approved
     * (chạy trong connection/transaction có sẵn).
     */
    public int countOnDutyAfterApproval(
            Connection connection,
            LocalDate leaveDate,
            String shiftType,
            String position,
            int excludeLeaveRequestId
    ) throws SQLException {
        // B1: Neu don xin nghi Ca ngay (FullDay) -> kiem tra doc lap ca Sang va ca Chieu,
        // lay moc quan so thap nhat (Math.min) theo dung DBR-60 & BR-14
        if (LeaveRequest.ShiftType.FULL_DAY.getDbValue().equalsIgnoreCase(shiftType) || "FullDay".equalsIgnoreCase(shiftType)) {
            int morningRem = countOnDutySingleShift(
                    connection, leaveDate, LeaveRequest.ShiftType.MORNING.getDbValue(), position, excludeLeaveRequestId);
            int afternoonRem = countOnDutySingleShift(
                    connection, leaveDate, LeaveRequest.ShiftType.AFTERNOON.getDbValue(), position, excludeLeaveRequestId);
            return Math.min(morningRem, afternoonRem);
        }

        // B2: Neu chi nghi 1 ca (Morning hoac Afternoon) -> dem truc tiep tren ca do
        return countOnDutySingleShift(connection, leaveDate, shiftType, position, excludeLeaveRequestId);
    }

    /**
     * Ham phu tro: Dem so nhan su dang Working con truc cua 1 ca don le (Morning hoac Afternoon)
     * sau khi tru di nhung nguoi da duoc Approved nghi trung ca do va tru them 1 cho don dang xet.
     */
    private int countOnDutySingleShift(
            Connection connection,
            LocalDate leaveDate,
            String targetShift,
            String position,
            int excludeLeaveRequestId) throws SQLException {

        // B1: Giả định đã chốt: mọi nhân viên Working làm cả hai ca; đây không phải lịch phân ca thực tế.
        String sqlTotal = "SELECT COUNT(*) FROM Employees WITH (HOLDLOCK) WHERE Position = ? AND Status = 'Working'";

        // B2: Dem so nhan su (DISTINCT EmployeeId) dang Working da duoc duyet nghi trung vao ca targetShift
        // (Bao gom nguoi nghi dung ca targetShift HOAC nguoi nghi FullDay)
        String sqlApproved = "SELECT COUNT(DISTINCT lr.EmployeeId) "
                + "FROM LeaveRequests lr "
                + "INNER JOIN Employees e ON e.EmployeeId = lr.EmployeeId "
                + "WHERE ? BETWEEN lr.FromDate AND lr.ToDate "
                + "  AND lr.Status = 'Approved' "
                + "  AND e.Position = ? "
                + "  AND e.Status = 'Working' "
                + "  AND lr.LeaveRequestId <> ? "
                + "  AND (lr.ShiftType = 'FullDay' OR lr.ShiftType = ?)";

        int totalWorking = 0;
        try (PreparedStatement ps = connection.prepareStatement(sqlTotal)) {
            ps.setString(1, position);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    totalWorking = rs.getInt(1);
                }
            }
        }

        int approvedSameShift = 0;
        try (PreparedStatement ps = connection.prepareStatement(sqlApproved)) {
            ps.setDate(1, java.sql.Date.valueOf(leaveDate));
            ps.setString(2, position);
            ps.setInt(3, excludeLeaveRequestId);
            ps.setString(4, targetShift);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    approvedSameShift = rs.getInt(1);
                }
            }
        }

        // B3: Quan so con lai neu duyet them don hien tai (-1)
        return Math.max(0, totalWorking - approvedSameShift - 1);
    }

    /**
     * Duyệt đơn nghỉ phép trong transaction: cập nhật Status = 'Approved', ghi người duyệt và thời điểm.
     * Chỉ cập nhật khi đơn còn ở trạng thái Pending.
     *
     * @param connection kết nối DB dùng chung transaction
     * @param leaveRequestId mã đơn
     * @param reviewerEmployeeId mã nhân viên Manager duyệt
     * @return true nếu cập nhật thành công 1 dòng, false nếu đơn không còn ở trạng thái Pending
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public boolean approve(Connection connection, int leaveRequestId, int reviewerEmployeeId) throws SQLException {
        String sql = "UPDATE LeaveRequests "
                + "SET Status = 'Approved', "
                + "ReviewedByEmployeeId = ?, "
                + "ReviewedAt = SYSDATETIME() "
                + "WHERE LeaveRequestId = ? AND Status = 'Pending'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reviewerEmployeeId);
            statement.setInt(2, leaveRequestId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Từ chối đơn nghỉ phép trong transaction: cập nhật Status = 'Rejected', ghi người duyệt, thời điểm và lý do.
     * Chỉ cập nhật khi đơn còn ở trạng thái Pending.
     *
     * @param connection kết nối DB dùng chung transaction
     * @param leaveRequestId mã đơn
     * @param reviewerEmployeeId mã nhân viên Manager từ chối
     * @param rejectReason lý do từ chối, 5-500 ký tự
     * @return true nếu cập nhật thành công 1 dòng, false nếu đơn không còn Pending
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public boolean reject(Connection connection, int leaveRequestId, int reviewerEmployeeId, String rejectReason)
            throws SQLException {
        String sql = "UPDATE LeaveRequests "
                + "SET Status = 'Rejected', "
                + "ReviewedByEmployeeId = ?, "
                + "ReviewedAt = SYSDATETIME(), "
                + "RejectReason = ? "
                + "WHERE LeaveRequestId = ? AND Status = 'Pending'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reviewerEmployeeId);
            statement.setString(2, rejectReason);
            statement.setInt(3, leaveRequestId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Thêm mới một đơn xin nghỉ phép vào database (dùng chung Connection của Transaction).
     *
     * @param connection kết nối Transaction đang mở
     * @param leaveRequest entity đơn xin nghỉ
     * @return mã đơn vừa tạo (LeaveRequestId)
     * @throws SQLException lỗi truy vấn DB
     */
    public int insert(Connection connection, LeaveRequest leaveRequest) throws SQLException {
        // Service đã kiểm tra ngày/ca và tính Days; DAO chỉ lưu các giá trị đó.
        LocalDate fromDate = leaveRequest.getFromDate();
        LocalDate toDate = leaveRequest.getToDate();
        BigDecimal days = leaveRequest.getDays();

        String sql = "INSERT INTO LeaveRequests (EmployeeId, LeaveType, FromDate, ToDate, Days, ShiftType, Reason, Status, SubmittedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'Pending', SYSDATETIME())";

        try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, leaveRequest.getEmployeeId());
            statement.setString(2, leaveRequest.getLeaveType().getDbValue());
            statement.setDate(3, java.sql.Date.valueOf(fromDate));
            statement.setDate(4, java.sql.Date.valueOf(toDate));
            statement.setBigDecimal(5, days);
            statement.setString(6, leaveRequest.getShiftType().getDbValue());
            statement.setString(7, leaveRequest.getReason());

            statement.executeUpdate();

            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    public boolean assign(Connection connection, int leaveRequestId, int toEmployeeId) throws SQLException {
        String sql = "UPDATE LeaveRequests SET AssignedToEmployeeId = ? WHERE LeaveRequestId = ? AND Status = 'Pending'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, toEmployeeId);
            ps.setInt(2, leaveRequestId);
            return ps.executeUpdate() > 0;
        }
    }

    public void insertTransfer(Connection connection, LeaveRequestTransfer transfer) throws SQLException {
        if (transfer != null) {
            insertTransfer(connection, transfer.getLeaveRequestId(), transfer.getFromEmployeeId(), transfer.getToEmployeeId(), transfer.getNote());
        }
    }

    public void insertTransfer(Connection connection, int leaveRequestId, int fromEmployeeId, int toEmployeeId, String note) throws SQLException {
        String sql = "INSERT INTO LeaveRequestTransfers (LeaveRequestId, FromEmployeeId, ToEmployeeId, Note) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, leaveRequestId);
            ps.setInt(2, fromEmployeeId);
            ps.setInt(3, toEmployeeId);
            ps.setString(4, note);
            ps.executeUpdate();
        }
    }

    public List<com.gms.entity.LeaveRequestTransfer> findTransfers(int leaveRequestId) throws SQLException {
        List<com.gms.entity.LeaveRequestTransfer> list = new ArrayList<>();
        String sql = "SELECT t.TransferId, t.LeaveRequestId, t.FromEmployeeId, fp.FullName AS FromName, "
                + "t.ToEmployeeId, tp.FullName AS ToName, t.Note, t.CreatedAt "
                + "FROM LeaveRequestTransfers t "
                + "JOIN Employees fe ON fe.EmployeeId = t.FromEmployeeId "
                + "JOIN Persons fp ON fp.PersonId = fe.PersonId "
                + "JOIN Employees te ON te.EmployeeId = t.ToEmployeeId "
                + "JOIN Persons tp ON tp.PersonId = te.PersonId "
                + "WHERE t.LeaveRequestId = ? "
                + "ORDER BY t.CreatedAt ASC, t.TransferId ASC";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, leaveRequestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new com.gms.entity.LeaveRequestTransfer(
                            rs.getInt("TransferId"),
                            rs.getInt("LeaveRequestId"),
                            rs.getInt("FromEmployeeId"),
                            rs.getString("FromName"),
                            rs.getInt("ToEmployeeId"),
                            rs.getString("ToName"),
                            rs.getString("Note"),
                            rs.getTimestamp("CreatedAt").toLocalDateTime()
                    ));
                }
            }
        }
        return list;
    }

    public List<EligibleReviewer> findEligibleReviewers(int currentEmployeeId) throws SQLException {
        List<EligibleReviewer> list = new ArrayList<>();
        String sql = "SELECT e.EmployeeId, e.EmployeeCode, e.Position, p.FullName, p.Avatar "
                + "FROM Employees e "
                + "JOIN Persons p ON p.PersonId = e.PersonId "
                + "WHERE e.Position = 'Manager' AND e.Status = 'Working' AND e.EmployeeId <> ? "
                + "ORDER BY p.FullName ASC";

        try (Connection conn = DBContext.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, currentEmployeeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new EligibleReviewer(
                            rs.getInt("EmployeeId"),
                            rs.getString("EmployeeCode"),
                            rs.getString("FullName"),
                            rs.getString("Position"),
                            rs.getString("Avatar")));
                }
            }
        }
        return list;
    }

    public LeaveBalance getAnnualBalance(int employeeId, int year, int quota) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return getAnnualBalance(conn, employeeId, year, quota);
        }
    }

    public LeaveBalance getAnnualBalance(Connection connection, int employeeId, int year, int quota) throws SQLException {
        return getAnnualBalance(connection, employeeId, year, quota, 0);
    }

    /** Không giữ chỗ hai lần cho chính đơn đang xét; các đơn Pending khác vẫn giữ chỗ như chính sách hiện tại. */
    public LeaveBalance getAnnualBalance(Connection connection, int employeeId, int year, int quota,
            int excludeRequestId) throws SQLException {
        String sql = "SELECT "
                + "ISNULL(SUM(CASE WHEN Status = 'Approved' THEN Days ELSE 0 END), 0) AS UsedDays, "
                + "ISNULL(SUM(CASE WHEN Status = 'Pending'  THEN Days ELSE 0 END), 0) AS PendingDays "
                + "FROM LeaveRequests "
                + "WHERE EmployeeId = ? AND LeaveType = 'Annual' AND YEAR(FromDate) = ? AND Status IN ('Approved', 'Pending') "
                + "AND LeaveRequestId <> ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setInt(2, year);
            ps.setInt(3, excludeRequestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new LeaveBalance(quota, rs.getBigDecimal("UsedDays"), rs.getBigDecimal("PendingDays"));
                }
            }
        }
        return new LeaveBalance(quota, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Nhân viên tự rút đơn còn Pending của chính mình. Xóa hẳn vì schema không có trạng thái Withdrawn (BF-05).
     *
     * @param connection kết nối trong transaction
     * @param leaveRequestId mã đơn nghỉ phép
     * @param employeeId mã nhân viên người nộp
     * @return true nếu xóa thành công 1 dòng Pending
     * @throws SQLException lỗi truy vấn
     */
    public boolean withdraw(Connection connection, int leaveRequestId, int employeeId) throws SQLException {
        String sql = "DELETE FROM LeaveRequests "
                + "WHERE LeaveRequestId = ? AND EmployeeId = ? AND Status = 'Pending'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, leaveRequestId);
            statement.setInt(2, employeeId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Thêm mới một đơn xin nghỉ phép vào database.
     *
     * @param leaveRequest entity đơn xin nghỉ
     * @return mã đơn vừa tạo (LeaveRequestId)
     * @throws SQLException lỗi kết nối hoặc truy vấn DB
     */
    public int insert(LeaveRequest leaveRequest) throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return insert(connection, leaveRequest);
        }
    }

    /**
     * Kiểm tra nhân viên đã có đơn nghỉ Pending hoặc Approved trùng ngày và ca chưa.
     */
    public boolean hasConflictingLeave(int employeeId, LocalDate leaveDate, String shiftType) throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return hasConflictingLeave(connection, employeeId, leaveDate, shiftType);
        }
    }

    public boolean hasConflictingLeave(Connection connection, int employeeId, LocalDate leaveDate, String shiftType) throws SQLException {
        return hasConflictingLeave(connection, employeeId, leaveDate, leaveDate, shiftType);
    }

    /** Kiểm tra giao khoảng ngày, kể cả khi ngày bắt đầu khác nhau. */
    public boolean hasConflictingLeave(Connection connection, int employeeId, LocalDate fromDate, LocalDate toDate,
            String shiftType) throws SQLException {
        String sql = "SELECT COUNT(*) FROM LeaveRequests "
                + "WHERE EmployeeId = ? AND FromDate <= ? AND ToDate >= ? "
                + "AND Status IN ('Pending', 'Approved') "
                + "AND (ShiftType = 'FullDay' OR ? = 'FullDay' OR ShiftType = ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, employeeId);
            statement.setDate(2, java.sql.Date.valueOf(toDate));
            statement.setDate(3, java.sql.Date.valueOf(fromDate));
            statement.setString(4, shiftType);
            statement.setString(5, shiftType);

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Đếm số buổi dạy Scheduled của PT trong ngày và ca xin nghỉ.
     */
    public int countScheduledSessions(int ptEmployeeId, LocalDate leaveDate, String shiftType) throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return countScheduledSessions(connection, ptEmployeeId, leaveDate, shiftType);
        }
    }

    /**
     * Đếm số buổi dạy Scheduled của PT trong transaction của lượt duyệt đơn (CBR-83).
     */
    public int countScheduledSessions(Connection connection, int ptEmployeeId, LocalDate leaveDate, String shiftType) throws SQLException {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM Sessions s WITH (HOLDLOCK) ")
                .append("JOIN TimeSlots ts WITH (HOLDLOCK) ON s.TimeSlotId = ts.TimeSlotId ")
                .append("WHERE s.PtEmployeeId = ? AND ts.SlotDate = ? AND s.Status = 'Scheduled' ");

        if ("Morning".equalsIgnoreCase(shiftType)) {
            sql.append("AND ts.StartTime < '12:00:00' ");
        } else if ("Afternoon".equalsIgnoreCase(shiftType)) {
            sql.append("AND ts.EndTime > '12:00:00' ");
        }

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setInt(1, ptEmployeeId);
            statement.setDate(2, java.sql.Date.valueOf(leaveDate));

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /** Các buổi Scheduled trùng toàn khoảng nghỉ; dùng giờ từ TimeSlots để hiện trên trang review. */
    public List<SessionConflict> findSessionConflicts(int employeeId, LocalDate fromDate, LocalDate toDate,
            String shiftType) throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return findSessionConflicts(connection, employeeId, fromDate, toDate, shiftType);
        }
    }

    public List<SessionConflict> findSessionConflicts(Connection connection, int employeeId, LocalDate fromDate,
            LocalDate toDate, String shiftType) throws SQLException {
        String sql = "SELECT s.SessionId, ts.SlotDate, ts.StartTime, ts.EndTime "
                + "FROM Sessions s JOIN TimeSlots ts ON ts.TimeSlotId = s.TimeSlotId "
                + "WHERE s.PtEmployeeId = ? AND s.Status = 'Scheduled' AND ts.SlotDate BETWEEN ? AND ? "
                + "AND (? = 'FullDay' OR (? = 'Morning' AND ts.StartTime < '12:00:00') "
                + "OR (? = 'Afternoon' AND ts.EndTime > '12:00:00')) ORDER BY ts.SlotDate, ts.StartTime, s.SessionId";
        List<SessionConflict> conflicts = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, employeeId);
            statement.setDate(2, java.sql.Date.valueOf(fromDate));
            statement.setDate(3, java.sql.Date.valueOf(toDate));
            statement.setString(4, shiftType);
            statement.setString(5, shiftType);
            statement.setString(6, shiftType);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    conflicts.add(new SessionConflict(resultSet.getInt("SessionId"),
                            resultSet.getDate("SlotDate").toLocalDate(),
                            resultSet.getTime("StartTime").toLocalTime(), resultSet.getTime("EndTime").toLocalTime()));
                }
            }
        }
        return conflicts;
    }

    /** Đếm riêng Approved và Pending của đơn khác có ngày/ca giao với đơn đang xem. */
    public Map<String, Integer> countOverlappingRequests(LeaveRequest request) throws SQLException {
        String sql = "SELECT Status, COUNT(*) AS Total FROM LeaveRequests "
                + "WHERE LeaveRequestId <> ? AND FromDate <= ? AND ToDate >= ? AND Status IN ('Pending', 'Approved') "
                + "AND (ShiftType = 'FullDay' OR ? = 'FullDay' OR ShiftType = ?) GROUP BY Status";
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection connection = DBContext.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, request.getLeaveRequestId());
            statement.setDate(2, java.sql.Date.valueOf(request.getToDate()));
            statement.setDate(3, java.sql.Date.valueOf(request.getFromDate()));
            statement.setString(4, request.getShiftType().getDbValue());
            statement.setString(5, request.getShiftType().getDbValue());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    counts.put(resultSet.getString("Status"), resultSet.getInt("Total"));
                }
            }
        }
        return counts;
    }

    /**
     * Lấy danh sách đơn xin nghỉ gần đây của một nhân viên (màn #25, UC-14).
     *
     * @param employeeId mã nhân viên
     * @param limit số đơn tối đa cần lấy
     * @return danh sách entity LeaveRequest xếp theo SubmittedAt giảm dần
     * @throws SQLException lỗi kết nối hoặc truy vấn DB
     */
    public List<LeaveRequest> findByEmployeeId(int employeeId, int limit) throws SQLException {
        // B1: câu SQL lấy danh sách đơn của nhân viên theo ngày nộp mới nhất
        String sql = SELECT_COLUMNS
                + "WHERE EmployeeId = ? "
                + "ORDER BY SubmittedAt DESC "
                + "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";

        List<LeaveRequest> list = new ArrayList<>();

        // B2: thực thi và map từng dòng kết quả
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, employeeId);
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(mapResultSetToLeaveRequest(resultSet));
                }
            }
        }

        return list;
    }

    /**
     * Lấy các đơn còn Pending của một ngày nghỉ mà chưa từng được nhắc Manager (JOB-07, bước nhắc).
     *
     * @param leaveDate ngày nghỉ cần xét, Service truyền vào là ngày mai
     * @return danh sách đơn có ReminderSentAt = NULL, xếp theo giờ nộp; rỗng nếu không có
     * @throws SQLException lỗi kết nối hoặc truy vấn DB
     */
    public List<LeaveRequest> findPendingNeedingReminder(LocalDate leaveDate) throws SQLException {
        // B1: chỉ lấy đơn Pending chưa nhắc, để mỗi đơn chỉ được nhắc đúng một lần
        String sql = SELECT_COLUMNS
                + "WHERE FromDate = ? "
                + "AND Status = 'Pending' "
                + "AND ReminderSentAt IS NULL "
                + "ORDER BY SubmittedAt ASC";

        List<LeaveRequest> list = new ArrayList<>();

        // B2: thực thi và map từng dòng kết quả
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDate(1, java.sql.Date.valueOf(leaveDate));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(mapResultSetToLeaveRequest(resultSet));
                }
            }
        }

        return list;
    }

    /**
     * Đánh dấu đơn đã được gửi thông báo nhắc Manager (JOB-07) trên cùng Connection của Transaction.
     *
     * @param connection     kết nối đang mở transaction
     * @param leaveRequestId mã đơn xin nghỉ
     * @return true nếu cập nhật thành công
     * @throws SQLException lỗi kết nối hoặc truy vấn DB
     */
    public boolean markReminded(Connection connection, int leaveRequestId) throws SQLException {
        // B1: Cập nhật cột ReminderSentAt trên cùng Connection của Transaction
        String sql = "UPDATE LeaveRequests "
                + "SET ReminderSentAt = SYSDATETIME() "
                + "WHERE LeaveRequestId = ? AND Status = 'Pending' AND ReminderSentAt IS NULL";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, leaveRequestId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Đánh dấu đơn đã được gửi thông báo nhắc Manager (bản tự mở kết nối đơn lẻ).
     *
     * @param leaveRequestId mã đơn xin nghỉ
     * @throws SQLException lỗi kết nối hoặc truy vấn DB
     */
    public void markReminded(int leaveRequestId) throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            markReminded(connection, leaveRequestId);
        }
    }

    // ===== Map ResultSet → Entity =====

    /** Đổi 1 dòng ResultSet thành LeaveRequest; cột nullable được đọc riêng để không mất NULL. */
    private LeaveRequest mapResultSetToLeaveRequest(ResultSet resultSet)
            throws SQLException {

        LeaveRequest leaveRequest = new LeaveRequest();

        leaveRequest.setLeaveRequestId(resultSet.getInt("LeaveRequestId"));
        leaveRequest.setEmployeeId(resultSet.getInt("EmployeeId"));

        // ReviewedByEmployeeId NULL khi đơn Pending hoặc hệ thống tự từ chối (CBR-85)
        int reviewerId = resultSet.getInt("ReviewedByEmployeeId");
        leaveRequest.setReviewedByEmployeeId(
                resultSet.wasNull() ? null : reviewerId);

        leaveRequest.setLeaveType(LeaveRequest.LeaveType
                .fromDbValue(resultSet.getString("LeaveType")));
        leaveRequest.setFromDate(resultSet.getDate("FromDate").toLocalDate());
        leaveRequest.setToDate(resultSet.getDate("ToDate").toLocalDate());
        leaveRequest.setDays(resultSet.getBigDecimal("Days"));
        int assigneeId = resultSet.getInt("AssignedToEmployeeId");
        leaveRequest.setAssignedToEmployeeId(resultSet.wasNull() ? null : assigneeId);

        leaveRequest.setShiftType(LeaveRequest.ShiftType
                .fromDbValue(resultSet.getString("ShiftType")));
        leaveRequest.setReason(resultSet.getString("Reason"));
        leaveRequest.setStatus(LeaveRequest.Status
                .fromDbValue(resultSet.getString("Status")));
        leaveRequest.setRejectReason(resultSet.getString("RejectReason"));

        leaveRequest.setSubmittedAt(
                toLocalDateTime(resultSet.getTimestamp("SubmittedAt")));
        leaveRequest.setReviewedAt(
                toLocalDateTime(resultSet.getTimestamp("ReviewedAt")));
        leaveRequest.setReminderSentAt(toLocalDateTime(resultSet.getTimestamp("ReminderSentAt")));
        return leaveRequest;
    }

    /** Timestamp có thể NULL (ReviewedAt, ReminderSentAt) nên đổi có kiểm tra null. */
    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

}
