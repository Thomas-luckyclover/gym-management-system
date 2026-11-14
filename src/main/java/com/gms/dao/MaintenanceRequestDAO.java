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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO quản lý thao tác dữ liệu cho bảng yêu cầu bảo trì ({@code MaintenanceRequests}).
 *
 * <p><b>Cách hoạt động:</b> Đóng gói toàn bộ câu lệnh SQL tạo mã phiếu tuần tự ({@code BT-xxxx}),
 * thêm mới, lọc và phân trang cho Lễ tân / Quản lý, cũng như các bước chuyển trạng thái
 * ({@code Open} -> {@code InProgress} -> {@code Rejected}/{@code Closed}) có sử dụng gợi ý khóa
 * {@code WITH (UPDLOCK, HOLDLOCK)} để đảm bảo an toàn đồng thời trong transaction của tầng Service.</p>
 */
public class MaintenanceRequestDAO {

    // ===== Tạo yêu cầu =====

    /**
     * Sinh mã phiếu bảo trì tuần tự tiếp theo (ví dụ {@code BT-0001}) trong transaction đang mở.
     *
     * <p><b>Cách hoạt động:</b> Thực thi {@code SELECT MAX(...)+1} kèm khóa {@code WITH (UPDLOCK, HOLDLOCK)}
     * trên bảng {@code MaintenanceRequests} để tuần tự hóa các giao dịch tạo phiếu đồng thời (tránh trùng mã),
     * kiểm tra giới hạn tối đa {@code 9999} rồi định dạng thành chuỗi {@code BT-%04d}.</p>
     *
     * @param connection kết nối CSDL đang mở transaction
     * @return mã phiếu bảo trì mới dạng {@code BT-0001}
     * @throws SQLException khi truy vấn lỗi hoặc vượt giới hạn mã {@code 9999}
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
     * Thêm mới một yêu cầu bảo trì vào cơ sở dữ liệu với trạng thái khởi tạo {@code Open}.
     *
     * <p><b>Cách hoạt động:</b> Gán các thông tin thiết bị, người báo cáo, mức độ ưu tiên, loại sự cố,
     * mô tả và ảnh đính kèm vào {@code PreparedStatement} có bật {@code Statement.RETURN_GENERATED_KEYS},
     * thực thi {@code INSERT} rồi lấy ID sinh tự động gán ngược vào {@code request}.</p>
     *
     * @param connection kết nối CSDL đang mở transaction
     * @param request    đối tượng yêu cầu bảo trì cần lưu
     * @return ID yêu cầu bảo trì vừa tạo
     * @throws SQLException khi thêm mới thất bại hoặc không nhận được khóa tự tăng
     */
    public int insert(
            Connection connection,
            MaintenanceRequest request
    ) throws SQLException {

        String sql = "INSERT INTO MaintenanceRequests "
                + "(RequestCode, EquipmentItemId, ReportedByEmployeeId, Priority, "
                + "IncidentType, AffectedItemsNote, WarningTagPlaced, Description, AttachedImages, Status) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
        )) {

            statement.setString(1, request.getRequestCode());
            statement.setInt(2, request.getEquipmentItemId());
            statement.setInt(3, request.getReportedByEmployeeId());
            statement.setString(4, request.getPriority().getDbValue());
            statement.setString(5, request.getIncidentType() != null ? request.getIncidentType().getDbValue() : MaintenanceRequest.IncidentType.NOT_WORKING.getDbValue());
            statement.setString(6, request.getAffectedItemsNote());
            statement.setBoolean(7, request.isWarningTagPlaced());
            statement.setString(8, request.getDescription());
            statement.setString(9, request.getAttachedImages());
            statement.setString(10, MaintenanceRequest.Status.OPEN.getDbValue());

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
     * Tìm và khóa bản ghi yêu cầu bảo trì để cập nhật an toàn trong transaction.
     *
     * <p><b>Cách hoạt động:</b> Truy vấn bảng {@code MaintenanceRequests} với gợi ý khóa
     * {@code WITH (UPDLOCK, HOLDLOCK)} và nối bảng {@code EquipmentItems} để lấy kèm {@code FacilityId},
     * ngăn hai quản lý hoặc lễ tân duyệt/từ chối/đóng cùng một phiếu tại một thời điểm.</p>
     *
     * @param connection kết nối CSDL đang mở transaction
     * @param requestId  ID phiếu bảo trì cần khóa và đọc
     * @return đối tượng {@code MaintenanceRequest} đã khóa, hoặc {@code null} nếu không tồn tại
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public MaintenanceRequest findByIdForUpdate(
            Connection connection,
            int requestId
    ) throws SQLException {

        String sql = "SELECT mr.MaintenanceRequestId, mr.RequestCode, mr.EquipmentItemId, "
                + "mr.ReportedByEmployeeId, mr.ReviewedByEmployeeId, mr.ClosedByEmployeeId, "
                + "mr.Priority, mr.IncidentType, mr.AffectedItemsNote, mr.WarningTagPlaced, mr.Description, "
                + "mr.AttachedImages, mr.Status, mr.SetUnderMaintenance, mr.RejectReason, mr.ManagerNote, mr.AcceptanceNote, "
                + "mr.ReportedAt, mr.ReviewedAt, mr.ClosedAt, ei.FacilityId "
                + "FROM MaintenanceRequests mr WITH (UPDLOCK, HOLDLOCK) "
                + "JOIN EquipmentItems ei ON ei.EquipmentItemId = mr.EquipmentItemId "
                + "WHERE mr.MaintenanceRequestId = ?";

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

    /**
     * Lấy thông tin chi tiết đầy đủ của một yêu cầu bảo trì (kèm tên thiết bị, vị trí, tên nhân viên) để hiển thị.
     *
     * <p><b>Cách hoạt động:</b> Ghép mệnh đề chọn chi tiết từ {@link #buildViewSelect()} với điều kiện lọc
     * theo {@code MaintenanceRequestId}, thực thi truy vấn và ánh xạ dòng kết quả sang {@link MaintenanceRequestView}.</p>
     *
     * @param requestId ID yêu cầu bảo trì cần xem chi tiết
     * @return đối tượng {@link MaintenanceRequestView}, hoặc {@code null} nếu không tìm thấy
     * @throws SQLException khi truy vấn CSDL lỗi
     */
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

    // ===== Danh sách Lễ tân (Xem tất cả yêu cầu theo quy tắc nghiệp vụ) =====

    /**
     * Đếm tổng số yêu cầu bảo trì hiển thị cho Lễ tân theo bộ lọc đa điều kiện.
     *
     * <p><b>Cách hoạt động:</b> Lễ tân được quyền theo dõi toàn bộ phiếu bảo trì trong hệ thống để kiểm tra
     * tình trạng máy phục vụ hội viên, nên hàm này ủy quyền trực tiếp cho {@link #countAll}.</p>
     *
     * @param keyword      từ khóa tìm kiếm (mã phiếu, mã máy, tên thiết bị, mô tả, người báo)
     * @param status       lọc theo trạng thái phiếu (có thể {@code null})
     * @param priority     lọc theo mức độ ưu tiên (có thể {@code null})
     * @param reportedDate lọc theo ngày báo cáo (có thể {@code null})
     * @return tổng số bản ghi khớp điều kiện lọc
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public long countForReceptionist(
            String keyword,
            MaintenanceRequest.Status status,
            MaintenanceRequest.Priority priority,
            LocalDate reportedDate
    ) throws SQLException {
        return countAll(keyword, status, priority, reportedDate);
    }

    /**
     * Truy vấn danh sách yêu cầu bảo trì có phân trang dành cho Lễ tân.
     *
     * <p><b>Cách hoạt động:</b> Nối truy vấn từ {@link #buildViewSelect()}, áp dụng bộ lọc động qua
     * {@link #appendFilters}, sắp xếp theo thứ tự ưu tiên trạng thái
     * ({@code Open -> InProgress -> Rejected -> Closed}) rồi {@code ReportedAt DESC} và cắt trang bằng {@code OFFSET/FETCH}.</p>
     *
     * @param keyword      từ khóa tìm kiếm
     * @param status       trạng thái cần lọc (có thể {@code null})
     * @param priority     mức độ ưu tiên cần lọc (có thể {@code null})
     * @param reportedDate ngày báo cáo cần lọc (có thể {@code null})
     * @param page         số trang hiện tại (bắt đầu từ 1)
     * @param pageSize     số dòng mỗi trang
     * @return danh sách {@link MaintenanceRequestView} thuộc trang yêu cầu
     * @throws SQLException khi truy vấn CSDL lỗi
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

    // ===== Danh sách Quản lý =====

    /**
     * Đếm tổng số yêu cầu bảo trì trong toàn hệ thống theo bộ lọc.
     *
     * <p><b>Cách hoạt động:</b> Thực thi {@code SELECT COUNT(*)} trên {@code MaintenanceRequests} kết nối với
     * thiết bị, dòng thiết bị và nhân viên báo cáo, gắn điều kiện lọc động bằng {@link #appendFilters}.</p>
     *
     * @param keyword      từ khóa tìm kiếm
     * @param status       trạng thái cần lọc (có thể {@code null})
     * @param priority     mức độ ưu tiên cần lọc (có thể {@code null})
     * @param reportedDate ngày báo cáo cần lọc (có thể {@code null})
     * @return tổng số bản ghi khớp bộ lọc
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public long countAll(
            String keyword,
            MaintenanceRequest.Status status,
            MaintenanceRequest.Priority priority,
            LocalDate reportedDate
    ) throws SQLException {

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) "
                        + "FROM MaintenanceRequests mr "
                        + "JOIN EquipmentItems ei ON ei.EquipmentItemId = mr.EquipmentItemId "
                        + "JOIN Facilities f ON f.FacilityId = ei.FacilityId "
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

    /**
     * Truy vấn danh sách yêu cầu bảo trì có phân trang dành cho Quản lý.
     *
     * <p><b>Cách hoạt động:</b> Xây dựng câu SQL chọn đầy đủ thông tin từ {@link #buildViewSelect()},
     * chèn điều kiện lọc qua {@link #appendFilters}, ưu tiên phiếu {@code Open} lên đầu và phân trang
     * bằng {@code OFFSET/FETCH NEXT}.</p>
     *
     * @param keyword      từ khóa tìm kiếm
     * @param status       trạng thái cần lọc (có thể {@code null})
     * @param priority     mức độ ưu tiên cần lọc (có thể {@code null})
     * @param reportedDate ngày báo cáo cần lọc (có thể {@code null})
     * @param page         số trang hiện tại
     * @param pageSize     số dòng mỗi trang
     * @return danh sách {@link MaintenanceRequestView} thuộc trang yêu cầu
     * @throws SQLException khi truy vấn CSDL lỗi
     */
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

    /**
     * Thống kê số lượng yêu cầu bảo trì theo từng trạng thái để hiển thị bộ đếm nhanh trên giao diện.
     *
     * <p><b>Cách hoạt động:</b> Khởi tạo sẵn {@code Map} chứa tất cả giá trị trong {@code Status.values()} với
     * số lượng {@code 0}, rồi chạy {@code GROUP BY Status} để cập nhật số đếm thực tế từ bảng {@code MaintenanceRequests}.</p>
     *
     * @return {@code Map<StatusDbValue, Count>} bảo đảm đủ mọi trạng thái theo thứ tự khai báo enum
     * @throws SQLException khi truy vấn CSDL lỗi
     */
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
     * Duyệt yêu cầu bảo trì từ trạng thái {@code Open} sang {@code InProgress} trong transaction.
     *
     * <p><b>Cách hoạt động:</b> Cập nhật người duyệt, thời điểm duyệt, cờ tạm ngừng máy và ghi chú quản lý
     * với điều kiện ràng buộc {@code WHERE MaintenanceRequestId = ? AND Status = 'Open'} để bảo đảm
     * chuyển trạng thái nguyên tử (chỉ trả về {@code true} khi cập nhật đúng 1 dòng).</p>
     *
     * @param connection          kết nối CSDL đang mở transaction
     * @param requestId           ID yêu cầu bảo trì cần duyệt
     * @param managerEmployeeId   ID nhân viên quản lý thực hiện duyệt
     * @param setUnderMaintenance cờ đánh dấu có đưa cá thể thiết bị sang {@code UnderMaintenance} hay không
     * @param managerNote         ghi chú xử lý của quản lý (tùy chọn)
     * @return {@code true} nếu duyệt thành công, {@code false} nếu trạng thái phiếu không còn là {@code Open}
     * @throws SQLException khi cập nhật CSDL lỗi
     */
    public boolean approve(
            Connection connection,
            int requestId,
            int managerEmployeeId,
            boolean setUnderMaintenance,
            String managerNote
    ) throws SQLException {

        String sql = "UPDATE MaintenanceRequests "
                + "SET Status = ?, "
                + "ReviewedByEmployeeId = ?, "
                + "ReviewedAt = SYSDATETIME(), "
                + "SetUnderMaintenance = ?, "
                + "RejectReason = NULL, "
                + "ManagerNote = ? "
                + "WHERE MaintenanceRequestId = ? "
                + "AND Status = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MaintenanceRequest.Status.IN_PROGRESS.getDbValue());
            statement.setInt(2, managerEmployeeId);
            statement.setBoolean(3, setUnderMaintenance);
            statement.setString(4, managerNote != null && !managerNote.isBlank() ? managerNote.trim() : null);
            statement.setInt(5, requestId);
            statement.setString(6, MaintenanceRequest.Status.OPEN.getDbValue());

            return statement.executeUpdate() == 1;
        }
    }

    /**
     * Từ chối một yêu cầu bảo trì đang ở trạng thái {@code Open} trong transaction.
     *
     * <p><b>Cách hoạt động:</b> Cập nhật trạng thái sang {@code Rejected}, lưu người duyệt, thời gian duyệt
     * và lý do từ chối với điều kiện {@code AND Status = 'Open'} để tránh xung đột duyệt đồng thời.</p>
     *
     * @param connection        kết nối CSDL đang mở transaction
     * @param requestId         ID yêu cầu bảo trì cần từ chối
     * @param managerEmployeeId ID nhân viên quản lý thực hiện từ chối
     * @param rejectReason      lý do từ chối
     * @return {@code true} nếu từ chối thành công đúng 1 dòng, ngược lại {@code false}
     * @throws SQLException khi cập nhật CSDL lỗi
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
     * Đóng (nghiệm thu hoàn tất) một yêu cầu bảo trì đang ở trạng thái {@code InProgress} trong transaction.
     *
     * <p><b>Cách hoạt động:</b> Chuyển trạng thái sang {@code Closed}, lưu ID lễ tân nghiệm thu, thời điểm đóng
     * và ghi chú nghiệm thu với ràng buộc {@code AND Status = 'InProgress'} để đảm bảo chỉ đóng phiếu đang xử lý.</p>
     *
     * @param connection             kết nối CSDL đang mở transaction
     * @param requestId              ID yêu cầu bảo trì cần đóng
     * @param receptionistEmployeeId ID nhân viên lễ tân nghiệm thu
     * @param acceptanceNote         ghi chú nghiệm thu sau bảo trì
     * @return {@code true} nếu đóng phiếu thành công, ngược lại {@code false}
     * @throws SQLException khi cập nhật CSDL lỗi
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
     * Kiểm tra một thiết bị cá thể có còn yêu cầu bảo trì nào khác đang ở trạng thái {@code InProgress} hay không.
     *
     * <p><b>Cách hoạt động:</b> Khi đóng một phiếu, tầng Service gọi hàm này ({@code SELECT TOP 1 1} lọc theo
     * {@code EquipmentItemId} và {@code Status = 'InProgress'}) để quyết định có được khôi phục máy về
     * {@code Available} hay vẫn phải giữ {@code UnderMaintenance}.</p>
     *
     * @param connection      kết nối CSDL đang mở transaction
     * @param equipmentItemId ID cá thể thiết bị cần kiểm tra
     * @return {@code true} nếu vẫn còn phiếu đang xử lý, {@code false} nếu không còn
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public boolean hasInProgressRequest(
            Connection connection,
            int equipmentItemId
    ) throws SQLException {

        String sql = "SELECT TOP 1 1 "
                + "FROM MaintenanceRequests "
                + "WHERE EquipmentItemId = ? "
                + "AND Status = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, equipmentItemId);
            statement.setString(2, MaintenanceRequest.Status.IN_PROGRESS.getDbValue());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * Kiểm tra một thiết bị cá thể có yêu cầu bảo trì nào đang hoạt động ({@code Open} hoặc {@code InProgress}) hay không.
     *
     * <p><b>Cách hoạt động:</b> Truy vấn {@code SELECT TOP 1 1} theo {@code EquipmentItemId} với trạng thái
     * thuộc {@code ('Open', 'InProgress')} trên kết nối transaction truyền vào; dùng để chặn báo hỏng trùng
     * (BR-105) và chặn thanh lý máy đang bảo trì (CBR-99).</p>
     *
     * @param connection      kết nối CSDL đang mở transaction
     * @param equipmentItemId ID cá thể thiết bị cần kiểm tra
     * @return {@code true} nếu đang có yêu cầu chưa đóng, {@code false} nếu không có
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public boolean hasActiveRequest(
            Connection connection,
            int equipmentItemId
    ) throws SQLException {

        String sql = "SELECT TOP 1 1 "
                + "FROM MaintenanceRequests "
                + "WHERE EquipmentItemId = ? "
                + "AND Status IN (?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, equipmentItemId);
            statement.setString(2, MaintenanceRequest.Status.OPEN.getDbValue());
            statement.setString(3, MaintenanceRequest.Status.IN_PROGRESS.getDbValue());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    /**
     * Kiểm tra nhanh (tự mở kết nối) xem một thiết bị cá thể có yêu cầu bảo trì đang hoạt động hay không.
     *
     * <p><b>Cách hoạt động:</b> Mở kết nối mới từ {@code DBContext.getConnection()} rồi ủy quyền sang
     * {@link #hasActiveRequest(Connection, int)} để kiểm tra trạng thái {@code Open}/{@code InProgress}.</p>
     *
     * @param equipmentItemId ID cá thể thiết bị cần kiểm tra
     * @return {@code true} nếu có yêu cầu đang hoạt động, ngược lại {@code false}
     * @throws SQLException khi mở kết nối hoặc truy vấn lỗi
     */
    public boolean hasActiveRequest(int equipmentItemId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return hasActiveRequest(conn, equipmentItemId);
        }
    }

    // ===== Ánh xạ & helper SQL =====

    /**
     * Tạo phần mệnh đề {@code SELECT ... FROM ... JOIN ...} dùng chung khi truy vấn danh sách hoặc chi tiết phiếu.
     *
     * <p><b>Cách hoạt động:</b> Ghép nối {@code MaintenanceRequests} với {@code EquipmentItems}, {@code Facilities}
     * và các bảng {@code Employees}/{@code Persons} (người báo, người duyệt, người đóng) để lấy đủ tên hiển thị.</p>
     */
    private String buildViewSelect() {
        return "SELECT "
                + "mr.MaintenanceRequestId, mr.RequestCode, mr.EquipmentItemId, "
                + "mr.ReportedByEmployeeId, mr.ReviewedByEmployeeId, mr.ClosedByEmployeeId, "
                + "mr.Priority, mr.IncidentType, mr.AffectedItemsNote, mr.WarningTagPlaced, mr.Description, "
                + "mr.AttachedImages, mr.Status, mr.SetUnderMaintenance, mr.RejectReason, mr.ManagerNote, mr.AcceptanceNote, "
                + "mr.ReportedAt, mr.ReviewedAt, mr.ClosedAt, "
                + "ei.ItemCode, ei.ItemLabel, ei.PlacementNote, f.FacilityId, f.FacilityName, f.Location AS FacilityLocation, "
                + "reporter.FullName AS ReporterName, "
                + "reviewer.FullName AS ReviewerName, "
                + "closer.FullName AS CloserName "
                + "FROM MaintenanceRequests mr "
                + "JOIN EquipmentItems ei ON ei.EquipmentItemId = mr.EquipmentItemId "
                + "JOIN Facilities f ON f.FacilityId = ei.FacilityId "
                + "JOIN Employees re ON re.EmployeeId = mr.ReportedByEmployeeId "
                + "JOIN Persons reporter ON reporter.PersonId = re.PersonId "
                + "LEFT JOIN Employees rv ON rv.EmployeeId = mr.ReviewedByEmployeeId "
                + "LEFT JOIN Persons reviewer ON reviewer.PersonId = rv.PersonId "
                + "LEFT JOIN Employees cl ON cl.EmployeeId = mr.ClosedByEmployeeId "
                + "LEFT JOIN Persons closer ON closer.PersonId = cl.PersonId ";
    }

    /**
     * Nối điều kiện lọc động (từ khóa không dấu, trạng thái, mức độ ưu tiên, ngày báo cáo) vào câu SQL.
     *
     * <p><b>Cách hoạt động:</b> Kiểm tra từng tham số khác rỗng/khác {@code null}, thêm mệnh đề {@code AND ...}
     * dùng {@code COLLATE Latin1_General_100_CI_AI} cho từ khóa và đẩy giá trị tương ứng vào danh sách {@code parameters}.</p>
     */
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
                    + "OR ei.ItemCode COLLATE Latin1_General_100_CI_AI LIKE ? "
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

    /**
     * Thực thi câu truy vấn danh sách yêu cầu bảo trì dạng {@link MaintenanceRequestView}.
     *
     * <p><b>Cách hoạt động:</b> Mở kết nối, bind danh sách tham số qua {@link #setParameters}, duyệt {@code ResultSet}
     * và gọi {@link #mapResultSetToView} cho từng bản ghi.</p>
     */
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

    /**
     * Gán danh sách tham số kiểu động vào {@code PreparedStatement} theo đúng thứ tự vị trí {@code ?}.
     *
     * <p><b>Cách hoạt động:</b> Kiểm tra kiểu dữ liệu thực tế của từng phần tử ({@code Integer}, {@code Date},
     * {@code Boolean}, hoặc chuỗi) để gọi setter JDBC tương ứng.</p>
     */
    private void setParameters(
            PreparedStatement statement,
            List<Object> parameters
    ) throws SQLException {

        for (int i = 0; i < parameters.size(); i++) {
            Object value = parameters.get(i);

            // Viết "instanceof rồi ép kiểu" kiểu cũ, KHÔNG dùng "instanceof Integer intValue":
            // Compile on Save của NetBeans 17 không dịch được cú pháp đó → sinh class hỏng → màn báo lỗi 500.
            if (value instanceof Integer) {
                statement.setInt(i + 1, (Integer) value);
            } else if (value instanceof Date) {
                statement.setDate(i + 1, (Date) value);
            } else if (value instanceof Boolean) {
                statement.setBoolean(i + 1, (Boolean) value);
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
    }

    /**
     * Ánh xạ một dòng {@code ResultSet} thành entity {@link MaintenanceRequest}.
     *
     * <p><b>Cách hoạt động:</b> Đọc đầy đủ các cột của bảng {@code MaintenanceRequests} (xử lý giá trị {@code null}
     * ở các trường duyệt/đóng, chuyển enum {@code Priority}/{@code IncidentType}/{@code Status} và cột thời gian).</p>
     */
    private MaintenanceRequest mapResultSetToRequest(ResultSet resultSet)
            throws SQLException {

        MaintenanceRequest request = new MaintenanceRequest();

        request.setMaintenanceRequestId(resultSet.getInt("MaintenanceRequestId"));
        request.setRequestCode(resultSet.getString("RequestCode"));
        request.setEquipmentItemId(resultSet.getInt("EquipmentItemId"));
        if (hasColumn(resultSet, "FacilityId")) {
            request.setFacilityId(resultSet.getInt("FacilityId"));
        }
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

        request.setIncidentType(
                MaintenanceRequest.IncidentType.fromDbValue(
                        resultSet.getString("IncidentType")
                )
        );

        request.setAffectedItemsNote(resultSet.getString("AffectedItemsNote"));
        request.setManagerNote(resultSet.getString("ManagerNote"));

        request.setWarningTagPlaced(resultSet.getBoolean("WarningTagPlaced"));
        request.setDescription(resultSet.getString("Description"));
        request.setAttachedImages(resultSet.getString("AttachedImages"));

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

    /**
     * Kiểm tra cột {@code columnName} có tồn tại trong {@code ResultSet} hiện tại hay không.
     *
     * <p><b>Cách hoạt động:</b> Gọi {@code rs.findColumn(columnName)}, trả về {@code true} nếu có và bắt
     * {@code SQLException} trả về {@code false} khi câu truy vấn không SELECT cột đó.</p>
     */
    private boolean hasColumn(ResultSet rs, String columnName) {
        try {
            rs.findColumn(columnName);
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    /**
     * Tra cứu phiếu bảo trì đã đóng ({@code Closed}) gần nhất của cùng thiết bị cá thể này.
     *
     * <p><b>Cách hoạt động:</b> Lọc các phiếu có {@code EquipmentItemId = ?}, {@code Status = 'Closed'}, khác ID
     * phiếu hiện tại rồi sắp xếp theo {@code ClosedAt DESC} lấy {@code TOP 1} để hiển thị khối "Lần hỏng trước của máy này"
     * trên màn hình Quản lý duyệt.</p>
     *
     * @param equipmentItemId  ID cá thể thiết bị
     * @param excludeRequestId ID phiếu đang xem (cần loại trừ)
     * @return bản ghi {@link MaintenanceRequestView} gần nhất đã đóng, hoặc {@code null} nếu chưa có tiền sử
     * @throws SQLException khi truy vấn CSDL lỗi
     */
    public MaintenanceRequestView findLastClosedRequest(int equipmentItemId, int excludeRequestId)
            throws SQLException {

        String sql = """
            SELECT TOP 1 mr.MaintenanceRequestId, mr.RequestCode, mr.EquipmentItemId,
                   ei.ItemCode, ei.ItemLabel, ei.PlacementNote, f.FacilityId, f.FacilityName, f.Location AS FacilityLocation,
                   mr.ReportedByEmployeeId, mr.ReviewedByEmployeeId, mr.ClosedByEmployeeId,
                   mr.Priority, mr.IncidentType, mr.AffectedItemsNote, mr.WarningTagPlaced,
                   mr.Description, mr.AttachedImages, mr.Status, mr.SetUnderMaintenance,
                   mr.RejectReason, mr.ManagerNote, mr.AcceptanceNote, mr.ReportedAt, mr.ReviewedAt, mr.ClosedAt,
                   reporter.FullName AS ReporterName,
                   reviewer.FullName AS ReviewerName,
                   closer.FullName AS CloserName
            FROM MaintenanceRequests mr
            JOIN EquipmentItems ei ON ei.EquipmentItemId = mr.EquipmentItemId
            JOIN Facilities f ON f.FacilityId = ei.FacilityId
            JOIN Employees re ON re.EmployeeId = mr.ReportedByEmployeeId
            JOIN Persons reporter ON reporter.PersonId = re.PersonId
            LEFT JOIN Employees rv ON rv.EmployeeId = mr.ReviewedByEmployeeId
            LEFT JOIN Persons reviewer ON reviewer.PersonId = rv.PersonId
            LEFT JOIN Employees cl ON cl.EmployeeId = mr.ClosedByEmployeeId
            LEFT JOIN Persons closer ON closer.PersonId = cl.PersonId
            WHERE mr.EquipmentItemId = ? AND mr.Status = 'Closed' AND mr.MaintenanceRequestId <> ?
            ORDER BY mr.ClosedAt DESC, mr.MaintenanceRequestId DESC
            """;

        try (Connection connection = DBContext.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, equipmentItemId);
            statement.setInt(2, excludeRequestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToView(resultSet);
                }
            }
        }
        return null;
    }

    /**
     * Ánh xạ một dòng {@code ResultSet} mở rộng sang DTO {@link MaintenanceRequestView}.
     *
     * <p><b>Cách hoạt động:</b> Kết hợp kết quả từ {@link #mapResultSetToRequest} với các thông tin đã JOIN
     * gồm mã/nhãn cá thể, dòng thiết bị, vị trí và họ tên người báo/duyệt/đóng.</p>
     */
    private MaintenanceRequestView mapResultSetToView(ResultSet resultSet)
            throws SQLException {

        return new MaintenanceRequestView(
                mapResultSetToRequest(resultSet),
                resultSet.getString("ItemCode"),
                resultSet.getString("ItemLabel"),
                resultSet.getString("PlacementNote"),
                resultSet.getInt("FacilityId"),
                resultSet.getString("FacilityName"),
                resultSet.getString("FacilityLocation"),
                resultSet.getString("ReporterName"),
                resultSet.getString("ReviewerName"),
                resultSet.getString("CloserName")
        );
    }

    /**
     * DTO biểu diễn dữ liệu yêu cầu bảo trì đã nối tên thiết bị và nhân viên để hiển thị trên JSP.
     */
    public static class MaintenanceRequestView {

        private final MaintenanceRequest request;
        private final String itemCode;
        private final String itemLabel;
        private final String placementNote;
        private final int facilityId;
        private final String facilityName;
        private final String facilityLocation;
        private final String reporterName;
        private final String reviewerName;
        private final String closerName;

        /**
         * Khởi tạo DTO {@code MaintenanceRequestView} với đầy đủ thông tin phiếu, máy và nhân sự liên quan.
         */
        public MaintenanceRequestView(
                MaintenanceRequest request,
                String itemCode,
                String itemLabel,
                String placementNote,
                int facilityId,
                String facilityName,
                String facilityLocation,
                String reporterName,
                String reviewerName,
                String closerName
        ) {
            this.request = request;
            this.itemCode = itemCode;
            this.itemLabel = itemLabel;
            this.placementNote = placementNote;
            this.facilityId = facilityId;
            this.facilityName = facilityName;
            this.facilityLocation = facilityLocation;
            this.reporterName = reporterName;
            this.reviewerName = reviewerName;
            this.closerName = closerName;
        }

        /** Trả về entity yêu cầu bảo trì gốc. */
        public MaintenanceRequest getRequest() {
            return request;
        }

        /** Trả về mã cá thể thiết bị (ví dụ {@code TM-01}). */
        public String getItemCode() {
            return itemCode;
        }

        /** Trả về nhãn hiển thị của cá thể thiết bị (ví dụ mức tạ). */
        public String getItemLabel() {
            return itemLabel;
        }

        /** Trả về ghi chú vị trí đặt máy. */
        public String getPlacementNote() {
            return placementNote;
        }

        /** Trả về ID dòng thiết bị cha. */
        public int getFacilityId() {
            return facilityId;
        }

        /** Trả về tên dòng thiết bị. */
        public String getFacilityName() {
            return facilityName;
        }

        /** Trả về khu vực đặt dòng thiết bị. */
        public String getFacilityLocation() {
            return facilityLocation;
        }

        /** Trả về họ tên nhân viên báo hỏng. */
        public String getReporterName() {
            return reporterName;
        }

        /** Trả về họ tên quản lý duyệt hoặc từ chối phiếu. */
        public String getReviewerName() {
            return reviewerName;
        }

        /** Trả về họ tên lễ tân nghiệm thu đóng phiếu. */
        public String getCloserName() {
            return closerName;
        }
    }
}
