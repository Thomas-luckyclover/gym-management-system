package com.gms.dao;

import com.gms.dto.LeaveRequestFilter;
import com.gms.entity.LeaveRequest;
import com.gms.util.DBContext;
import com.gms.util.SqlUtil;
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
    private static final String SELECT_COLUMNS
            = "SELECT LeaveRequestId, EmployeeId, ReviewedByEmployeeId, LeaveType, "
            + "LeaveDate, ShiftType, Reason, Status, RejectReason, "
            + "SubmittedAt, ReviewedAt, ReminderSentAt "
            + "FROM LeaveRequests ";

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

    public int countByFilter(LeaveRequestFilter filter) throws SQLException {
        // B1: nối Employees và Persons vì tên nhân viên nằm ở Persons.FullName
        String sql = "SELECT COUNT(*) "
                + "FROM LeaveRequests lr "
                + "JOIN Employees e ON e.EmployeeId = lr.EmployeeId "
                + "JOIN Persons p ON p.PersonId = e.PersonId "
                // status null thì điều kiện đầu luôn đúng (không lọc theo tab)
                + "WHERE (? IS NULL OR lr.Status = ?) "
                + "AND p.FullName COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\\'";

        // B2: đổi status sang giá trị DB; null giữ nguyên để bỏ lọc
        String statusValue = filter.getStatus() == null
                ? null : filter.getStatus().getDbValue();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql)) {

            statement.setString(1, statusValue);
            statement.setString(2, statusValue);
            statement.setString(3,
                    SqlUtil.toContainsPattern(filter.getKeyword()));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }

        return 0;
    }

    /**
     * Lấy danh sách đơn nghỉ phép có phân trang cho màn hình của Manager (màn #26).
     *
     * @param filter   bộ lọc gồm trạng thái tab và từ khóa tìm kiếm tên nhân viên
     * @param offset   vị trí bắt đầu lấy dòng (trang 1 bắt đầu từ 0)
     * @param pageSize số lượng đơn tối đa trên một trang
     * @return danh sách các dòng đơn kèm thông tin nhân viên
     * @throws SQLException khi có lỗi truy vấn cơ sở dữ liệu
     */
    public List<LeaveRequestRow> findManagerPage(
            LeaveRequestFilter filter, int offset, int pageSize)
            throws SQLException {

        // Câu SQL gồm 6 phần rõ ràng:
        // 1. SELECT: lấy đủ thông tin đơn nghỉ, thông tin nhân viên và người duyệt
        // 2. JOIN: nối Employees & Persons để lấy họ tên, mã NV, chức vụ, avatar
        // 3. LEFT JOIN: lấy tên người duyệt (nếu đơn chưa duyệt thì ReviewerName sẽ là NULL)
        // 4. WHERE: lọc theo tab trạng thái (? IS NULL để lấy tất cả) và tìm tên tiếng Việt
        // 5. ORDER BY: ưu tiên đơn 'Pending' lên đầu, sau đó sắp xếp theo ngày nghỉ và giờ nộp
        // 6. OFFSET/FETCH: phân trang của SQL Server
        String sql = "SELECT "
                + "  lr.LeaveRequestId, lr.EmployeeId, lr.ReviewedByEmployeeId, "
                + "  lr.LeaveType, lr.LeaveDate, lr.ShiftType, lr.Reason, lr.Status, lr.RejectReason, "
                + "  lr.SubmittedAt, lr.ReviewedAt, lr.ReminderSentAt, "
                + "  e.EmployeeCode, e.Position, "
                + "  p.FullName AS EmployeeName, p.Avatar, "
                + "  rp.FullName AS ReviewerName "
                + "FROM LeaveRequests lr "
                + "  JOIN Employees e ON e.EmployeeId = lr.EmployeeId "
                + "  JOIN Persons p ON p.PersonId = e.PersonId "
                + "  LEFT JOIN Employees re ON re.EmployeeId = lr.ReviewedByEmployeeId "
                + "  LEFT JOIN Persons rp ON rp.PersonId = re.PersonId "
                + "WHERE (? IS NULL OR lr.Status = ?) "
                + "  AND p.FullName COLLATE Vietnamese_CI_AI LIKE ? ESCAPE '\\' "
                + "ORDER BY "
                + "  CASE WHEN lr.Status = 'Pending' THEN 0 ELSE 1 END, "
                + "  CASE WHEN lr.Status = 'Pending' THEN lr.LeaveDate END ASC, "
                + "  CASE WHEN lr.Status = 'Pending' THEN lr.SubmittedAt END ASC, "
                + "  lr.LeaveDate DESC, "
                + "  lr.SubmittedAt DESC "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        // Giá trị trạng thái lọc: nếu chọn tab "Tất cả" thì statusValue là null
        String statusValue = filter.getStatus() == null
                ? null
                : filter.getStatus().getDbValue();

        List<LeaveRequestRow> rows = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            // B1: Truyền 5 tham số theo đúng thứ tự dấu ?
            // ?1 & ?2: trạng thái tab (null để bỏ qua lọc trạng thái)
            statement.setString(1, statusValue);
            statement.setString(2, statusValue);
            // ?3: từ khóa tìm kiếm họ tên nhân viên
            statement.setString(3, SqlUtil.toContainsPattern(filter.getKeyword()));
            // ?4 & ?5: phân trang (bỏ qua offset dòng, lấy tiếp pageSize dòng)
            statement.setInt(4, offset);
            statement.setInt(5, pageSize);

            // B2: Đọc kết quả và chuyển thành danh sách đối tượng LeaveRequestRow
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new LeaveRequestRow(
                            mapResultSetToLeaveRequest(resultSet),
                            resultSet.getString("EmployeeCode"),
                            resultSet.getString("EmployeeName"),
                            resultSet.getString("Position"),
                            resultSet.getString("Avatar"),
                            resultSet.getString("ReviewerName")));
                }
            }
        }

        return rows;
    }

    /**
     * Đếm số đơn theo từng trạng thái để hiện số trên các tab của màn #26.
     *
     * @return map có đủ 3 khóa Pending, Approved, Rejected (trạng thái không có đơn thì giá trị 0);
     *         số tab "Tất cả" do Service cộng từ 3 giá trị này
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public Map<String, Integer> getStatusCounts() throws SQLException {
        // B1: khởi tạo sẵn 3 trạng thái = 0, vì GROUP BY không trả dòng cho trạng thái rỗng
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(LeaveRequest.Status.PENDING.getDbValue(), 0);
        counts.put(LeaveRequest.Status.APPROVED.getDbValue(), 0);
        counts.put(LeaveRequest.Status.REJECTED.getDbValue(), 0);

        String sql = "SELECT Status, COUNT(*) AS Total "
                + "FROM LeaveRequests "
                + "GROUP BY Status";

        // B2: ghi đè số thật lên các khóa đã khởi tạo
        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement
                = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                counts.put(resultSet.getString("Status"),
                        resultSet.getInt("Total"));
            }
        }

        return counts;
    }

    /**
     * Lấy thông tin đầy đủ của 1 đơn cho màn duyệt của Manager (màn #27, UC-15).
     * JOIN thêm Employees/Persons của người nộp để lấy tên, mã NV, chức vụ.
     *
     * @param leaveRequestId mã đơn
     * @return LeaveReviewDetail, hoặc null nếu không tìm thấy
     * @throws SQLException lỗi kết nối hoặc lỗi câu SQL
     */
    public LeaveReviewDetail findReviewDetail(int leaveRequestId) throws SQLException {
        String sql = "SELECT lr.LeaveRequestId, lr.EmployeeId, "
                + "lr.ReviewedByEmployeeId, lr.LeaveType, lr.LeaveDate, "
                + "lr.ShiftType, lr.Reason, lr.Status, lr.RejectReason, "
                + "lr.SubmittedAt, lr.ReviewedAt, lr.ReminderSentAt, "
                + "e.EmployeeCode, e.Position, "
                + "p.FullName AS EmployeeName, p.Avatar, "
                + "rp.FullName AS ReviewerName "
                + "FROM LeaveRequests lr "
                + "JOIN Employees e ON e.EmployeeId = lr.EmployeeId "
                + "JOIN Persons p ON p.PersonId = e.PersonId "
                + "LEFT JOIN Employees re ON re.EmployeeId = lr.ReviewedByEmployeeId "
                + "LEFT JOIN Persons rp ON rp.PersonId = re.PersonId "
                + "WHERE lr.LeaveRequestId = ?";

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, leaveRequestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return new LeaveReviewDetail(
                            mapResultSetToLeaveRequest(resultSet),
                            resultSet.getString("EmployeeCode"),
                            resultSet.getString("EmployeeName"),
                            resultSet.getString("Position"),
                            resultSet.getString("Avatar"),
                            resultSet.getString("ReviewerName"));
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

        // B1: Dem tong nhan su dang lam viec (Working) cua vi tri do
        String sqlTotal = "SELECT COUNT(*) FROM Employees WHERE Position = ? AND Status = 'Working'";

        // B2: Dem so nhan su (DISTINCT EmployeeId) dang Working da duoc duyet nghi trung vao ca targetShift
        // (Bao gom nguoi nghi dung ca targetShift HOAC nguoi nghi FullDay)
        String sqlApproved = "SELECT COUNT(DISTINCT lr.EmployeeId) "
                + "FROM LeaveRequests lr "
                + "INNER JOIN Employees e ON e.EmployeeId = lr.EmployeeId "
                + "WHERE lr.LeaveDate = ? "
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
     * Duyệt đơn nghỉ phép với connection mới.
     */
    public boolean approve(int leaveRequestId, int reviewerEmployeeId) throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return approve(connection, leaveRequestId, reviewerEmployeeId);
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
     * Từ chối đơn nghỉ phép với connection mới.
     */
    public boolean reject(int leaveRequestId, int reviewerEmployeeId, String rejectReason)
            throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return reject(connection, leaveRequestId, reviewerEmployeeId, rejectReason);
        }
    }

    /**
     * Hệ thống tự động từ chối đơn nghỉ phép quá hạn duyệt trong transaction (JOB-07, CBR-85).
     * ReviewedByEmployeeId được để NULL để thể hiện hệ thống tự từ chối.
     *
     * @param connection kết nối DB dùng chung transaction
     * @param leaveRequestId mã đơn
     * @param reason lý do từ chối ("Quá hạn duyệt")
     * @return true nếu cập nhật thành công 1 dòng, false nếu đơn đã được xử lý trước đó
     * @throws SQLException lỗi kết nối hoặc truy vấn DB
     */
    public boolean autoReject(Connection connection, int leaveRequestId, String reason) throws SQLException {
        String sql = "UPDATE LeaveRequests "
                + "SET Status = 'Rejected', "
                + "ReviewedByEmployeeId = NULL, "
                + "ReviewedAt = SYSDATETIME(), "
                + "RejectReason = ? "
                + "WHERE LeaveRequestId = ? AND Status = 'Pending'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, reason);
            statement.setInt(2, leaveRequestId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * Tự động từ chối đơn với connection mới.
     */
    public boolean autoReject(int leaveRequestId, String reason) throws SQLException {
        try (Connection connection = DBContext.getConnection()) {
            return autoReject(connection, leaveRequestId, reason);
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
        String sql = "INSERT INTO LeaveRequests (EmployeeId, LeaveType, LeaveDate, ShiftType, Reason, Status, SubmittedAt) "
                + "VALUES (?, ?, ?, ?, ?, 'Pending', SYSDATETIME())";

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            statement.setInt(1, leaveRequest.getEmployeeId());
            statement.setString(2, leaveRequest.getLeaveType().getDbValue());
            statement.setDate(3, java.sql.Date.valueOf(leaveRequest.getLeaveDate()));
            statement.setString(4, leaveRequest.getShiftType().getDbValue());
            statement.setString(5, leaveRequest.getReason());

            statement.executeUpdate();

            try (ResultSet rs = statement.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    /**
     * Kiểm tra nhân viên đã có đơn nghỉ Pending hoặc Approved trùng ngày và ca chưa.
     */
    public boolean hasConflictingLeave(int employeeId, LocalDate leaveDate, String shiftType) throws SQLException {
        String sql = "SELECT COUNT(*) FROM LeaveRequests "
                + "WHERE EmployeeId = ? AND LeaveDate = ? "
                + "AND Status IN ('Pending', 'Approved') "
                + "AND (ShiftType = 'FullDay' OR ? = 'FullDay' OR ShiftType = ?)";

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, employeeId);
            statement.setDate(2, java.sql.Date.valueOf(leaveDate));
            statement.setString(3, shiftType);
            statement.setString(4, shiftType);

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
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM Sessions s ")
                .append("JOIN TimeSlots ts ON s.TimeSlotId = ts.TimeSlotId ")
                .append("WHERE s.PtEmployeeId = ? AND ts.SlotDate = ? AND s.Status = 'Scheduled' ");

        if ("Morning".equalsIgnoreCase(shiftType)) {
            sql.append("AND ts.StartTime < '12:00:00' ");
        } else if ("Afternoon".equalsIgnoreCase(shiftType)) {
            sql.append("AND ts.StartTime >= '12:00:00' ");
        }

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {

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
                + "WHERE LeaveDate = ? "
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
                + "WHERE LeaveRequestId = ? AND ReminderSentAt IS NULL";

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

    /**
     * Lấy danh sách các đơn Pending đã quá hạn duyệt P-20 để tự động từ chối (JOB-07).
     *
     * @param maxOverdueLeaveDate ngày nghỉ muộn nhất đã quá hạn duyệt (hôm nay hoặc ngày mai)
     * @return danh sách các đơn Pending có LeaveDate <= maxOverdueLeaveDate
     * @throws SQLException lỗi kết nối hoặc truy vấn DB
     */
    public List<LeaveRequest> findPendingOverdueRequests(LocalDate maxOverdueLeaveDate) throws SQLException {
        String sql = SELECT_COLUMNS
                + "WHERE Status = 'Pending' "
                + "AND LeaveDate <= ? "
                + "ORDER BY SubmittedAt ASC";

        List<LeaveRequest> list = new ArrayList<>();

        try (Connection connection = DBContext.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setDate(1, java.sql.Date.valueOf(maxOverdueLeaveDate));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(mapResultSetToLeaveRequest(resultSet));
                }
            }
        }

        return list;
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
        leaveRequest.setLeaveDate(
                resultSet.getDate("LeaveDate").toLocalDate());
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
        leaveRequest.setReminderSentAt(
                toLocalDateTime(resultSet.getTimestamp("ReminderSentAt")));

        return leaveRequest;
    }

    /** Timestamp có thể NULL (ReviewedAt, ReminderSentAt) nên đổi có kiểm tra null. */
    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * Một dòng danh sách đơn nghỉ phép của Manager do DAO tạo: gồm entity đơn
     * và thông tin nhân viên lấy từ Employees/Persons. Service đổi sang
     * LeaveRequestItemView (thêm hạn duyệt, đếm ngược) trước khi đưa ra JSP.
     */
    /**
     * Thông tin đầy đủ một đơn nghỉ phép cho màn duyệt của Manager (#27).
     * Giống LeaveRequestRow nhưng dành riêng cho trang review, Service thêm trường tính toán.
     */
    public static class LeaveReviewDetail {

        private final LeaveRequest leaveRequest;
        private final String employeeCode;
        private final String fullName;
        private final String position;
        private final String avatarUrl;
        private final String reviewerName;

        // Quân số trực dự kiến nếu duyệt và định biên tối thiểu theo cấu hình
        private int remainingStaff;
        private int minStaffRequired;

        public LeaveReviewDetail(
                LeaveRequest leaveRequest,
                String employeeCode,
                String fullName,
                String position,
                String avatarUrl,
                String reviewerName
        ) {
            this.leaveRequest = leaveRequest;
            this.employeeCode = employeeCode;
            this.fullName = fullName;
            this.position = position;
            this.avatarUrl = avatarUrl;
            this.reviewerName = reviewerName;
        }

        public LeaveRequest getLeaveRequest() { return leaveRequest; }
        public String getEmployeeCode() { return employeeCode; }
        public String getFullName() { return fullName; }
        public String getPosition() { return position; }
        public String getAvatarUrl() { return avatarUrl; }
        public String getReviewerName() { return reviewerName; }

        public int getRemainingStaff() { return remainingStaff; }
        public void setRemainingStaff(int remainingStaff) { this.remainingStaff = remainingStaff; }

        public int getMinStaffRequired() { return minStaffRequired; }
        public void setMinStaffRequired(int minStaffRequired) { this.minStaffRequired = minStaffRequired; }
    }

    public static class LeaveRequestRow {

        // DAO: toàn bộ cột LeaveRequests của đơn này.
        private final LeaveRequest leaveRequest;

        // DAO: Employees.EmployeeCode, ví dụ NV-0005.
        private final String employeeCode;

        // DAO: Persons.FullName của nhân viên nộp đơn.
        private final String fullName;

        // DAO: Employees.Position (PT hoặc Receptionist).
        private final String position;

        // DAO: Persons.Avatar; NULL thì JSP hiện chữ viết tắt.
        private final String avatarUrl;

        // DAO: Persons.FullName của người duyệt; NULL khi Pending hoặc tự từ chối.
        private final String reviewerName;

        public LeaveRequestRow(
                LeaveRequest leaveRequest,
                String employeeCode,
                String fullName,
                String position,
                String avatarUrl,
                String reviewerName
        ) {
            this.leaveRequest = leaveRequest;
            this.employeeCode = employeeCode;
            this.fullName = fullName;
            this.position = position;
            this.avatarUrl = avatarUrl;
            this.reviewerName = reviewerName;
        }

        public LeaveRequest getLeaveRequest() {
            return leaveRequest;
        }

        public String getEmployeeCode() {
            return employeeCode;
        }

        public String getFullName() {
            return fullName;
        }

        public String getPosition() {
            return position;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public String getReviewerName() {
            return reviewerName;
        }
    }
}
