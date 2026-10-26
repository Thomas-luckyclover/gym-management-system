package com.gms.dao;

import com.gms.entity.Order;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Truy cập bảng Orders — 1 lần mua 1 gói (DBR-39, DBR-40).
 *
 * Bốn method cho Bước 1 (tạo đơn Pending): generateNextOrderCode — sinh mã
 * DH-yyyy-nnnn (DBR-09) insert — thêm đơn mới, trả OrderId tự sinh
 * findPendingOnlineByHolderAndPackage — tìm đơn online cũ còn Pending (CBR-71)
 * markCancelled — hủy đơn, có điều kiện trạng thái (DBR-40, DBR-43)
 *
 * Method ghi đều nhận Connection từ Service truyền vào để dùng chung 1
 * transaction (codeImplementationBackend.md Mục 13). Mọi method đổi trạng thái
 * trả về int = số dòng bị ảnh hưởng, KHÔNG trả void: rowsAffected == 0 nghĩa là
 * có request khác đã xử lý đơn này trước (GBR-13), Service phải xử lý trường
 * hợp đó chứ không coi là lỗi.
 *
 * Ba điểm khớp với entity Order: - Channel / Status / CancelType là enum lồng,
 * ghi xuống DB bằng getDbValue(), đọc lên bằng fromDbValue() (GBR-01: enum lưu
 * tiếng Anh). - Tiền là long trong Java dù cột DB là INT (docs/notes.md mục D5)
 * -> setLong / getLong. - orderId là final không có setter, nên insert() CHỈ
 * trả OrderId về cho Service, không gán lại vào entity. Service dùng giá trị
 * trả về đó cho các bước sau.
 *
 * @author Le Cong Minh
 */
public class OrderDAO {

    /**
     * Các cột dùng chung cho mọi câu SELECT của class này.
     */
    private static final String SELECT_COLUMNS = "OrderId, OrderCode, HolderCustomerId, PartnerCustomerId, PackageId, VoucherId, "
            + "CreatedByEmployeeId, ShiftId, Channel, PackageNameSnapshot, PackageDescriptionSnapshot, "
            + "PackageBenefitsSnapshot, UnitPriceSnapshot, DiscountAmount, VatRateSnapshot, VatAmount, "
            + "Status, PaymentDueAt, CreatedAt, PaidAt, CancelType, CancelledAt, CancelReason";

    // ===== 1. Sinh mã đơn =====
    /**
     * Sinh mã đơn kế tiếp của năm hiện tại theo dạng DH-yyyy-nnnn (DBR-09).
     *
     * Số thứ tự reset theo năm, nên KHÔNG dùng MAX(OrderId) như
     * generateNextCustomerCode: phải lọc theo tiền tố của năm rồi lấy 4 ký tự
     * cuối.
     *
     * Chống trùng: bảng có UQ_Orders_OrderCode, nên hai request đồng thời đọc
     * cùng một MAX thì câu insert thứ hai sẽ ném SQLException duplicate key chứ
     * không ghi trùng — Service bắt lỗi đó, gọi lại method này và thử insert
     * lần nữa.
     *
     * @param conn Connection tham gia transaction
     * @return mã đơn mới, ví dụ "DH-2026-0001"
     * @throws SQLException khi truy vấn lỗi, hoặc khi số thứ tự vượt 9999 (vỡ
     * CK_Orders_Code)
     */
    public String generateNextOrderCode(Connection conn) throws SQLException {
        int year = LocalDate.now(DateTimeUtil.GYM_ZONE).getYear();
        String prefix = String.format("DH-%d-", year);

        String sql = "SELECT MAX(OrderCode) FROM Orders WITH (UPDLOCK, HOLDLOCK) WHERE OrderCode LIKE ?";

        // B1: lấy mã lớn nhất của năm nay
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, prefix + "%");

            int nextNumber = 1;
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String maxCode = rs.getString(1);
                    // B2: cắt 4 ký tự cuối "DH-2026-0007" -> "0007" -> 7
                    if (maxCode != null && maxCode.length() == prefix.length() + 4) {
                        nextNumber = Integer.parseInt(maxCode.substring(prefix.length())) + 1;
                    }
                }
            }

            // B3: CK_Orders_Code chỉ cho 4 chữ số -> quá 9999 đơn trong 1 năm là vỡ format
            if (nextNumber > 9999) {
                throw new SQLException("Đã hết dải mã đơn của năm " + year + ", cần mở rộng định dạng OrderCode");
            }

            return prefix + String.format("%04d", nextNumber);
        }
    }

    // ===== 2. Thêm đơn mới =====
    /**
     * Thêm một đơn mới trong transaction dùng chung Connection.
     *
     * KHÔNG ghi PaidAt / CancelType / CancelledAt / CancelReason: đơn mới luôn
     * ở Pending, và CK_Orders_Paid cùng CK_Orders_Cancelled bắt buộc bốn cột đó
     * phải NULL khi Pending.
     *
     * Hai CHECK dễ vướng nhất khi gọi method này: CK_Orders_ChannelFields —
     * ONLINE: createdByEmployeeId và shiftId phải null, paymentDueAt phải có
     * giá trị. COUNTER: ngược lại hoàn toàn. CK_Orders_VoucherDiscount —
     * voucherId null thì discountAmount phải bằng 0.
     *
     * @param conn Connection tham gia transaction
     * @param order đơn cần chèn, đã có orderCode và đã tính sẵn vatAmount
     * (DBR-57)
     * @return OrderId tự sinh trong database (orderId của entity là final nên
     * không gán lại được)
     * @throws SQLException khi truy vấn lỗi hoặc vi phạm CHECK / UNIQUE
     */
    public int insert(Connection conn, Order order) throws SQLException {
        String sql = "INSERT INTO Orders ("
                + "OrderCode, HolderCustomerId, PartnerCustomerId, PackageId, VoucherId, "
                + "CreatedByEmployeeId, ShiftId, Channel, PackageNameSnapshot, PackageDescriptionSnapshot, "
                + "PackageBenefitsSnapshot, UnitPriceSnapshot, DiscountAmount, VatRateSnapshot, VatAmount, "
                + "Status, PaymentDueAt, CreatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        // B1: chuẩn bị PreparedStatement với cờ lấy generated keys
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, order.getOrderCode());
            stmt.setInt(2, order.getHolderCustomerId());
            setNullableInt(stmt, 3, order.getPartnerCustomerId());
            stmt.setInt(4, order.getPackageId());
            setNullableInt(stmt, 5, order.getVoucherId());
            setNullableInt(stmt, 6, order.getCreatedByEmployeeId());
            setNullableInt(stmt, 7, order.getShiftId());

            // Enum -> chuỗi tiếng Anh đúng miền giá trị CHECK (GBR-01)
            stmt.setString(8, order.getChannel() == null ? null : order.getChannel().getDbValue());

            stmt.setString(9, order.getPackageNameSnapshot());
            stmt.setString(10, order.getPackageDescriptionSnapshot());
            stmt.setString(11, order.getPackageBenefitsSnapshot());

            // Tiền là long trong Java, cột DB là INT (notes.md D5)
            stmt.setLong(12, order.getUnitPriceSnapshot());
            stmt.setLong(13, order.getDiscountAmount());
            stmt.setInt(14, order.getVatRateSnapshot());
            stmt.setLong(15, order.getVatAmount());

            Order.Status status = order.getStatus() != null ? order.getStatus() : Order.Status.PENDING;
            stmt.setString(16, status.getDbValue());

            setNullableTimestamp(stmt, 17, order.getPaymentDueAt());

            LocalDateTime createdAt = order.getCreatedAt() != null
                    ? order.getCreatedAt()
                    : LocalDateTime.now(DateTimeUtil.GYM_ZONE);
            stmt.setTimestamp(18, Timestamp.valueOf(createdAt));

            // B2: thực thi câu lệnh SQL
            stmt.executeUpdate();

            // B3: đọc OrderId tự sinh và trả về cho Service
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("Thêm Order thất bại, không nhận được OrderId");
    }

    /**
     * Thêm một đơn mới dùng Connection riêng lẻ ngoài transaction. Chỉ dùng khi
     * thật sự chỉ có 1 câu SQL — luồng mua hàng luôn đi qua bản nhận
     * Connection.
     *
     * @param order đơn cần chèn
     * @return OrderId tự sinh
     * @throws SQLException khi truy vấn lỗi
     */
    public int insert(Order order) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return insert(conn, order);
        }
    }

    // ===== 3. Tìm đơn online còn Pending (CBR-71) =====
    /**
     * Tìm các đơn ONLINE còn Pending của cùng một khách cho cùng một gói
     * (CBR-71).
     *
     * Service gọi trước khi tạo đơn mới: mỗi đơn trả về đều phải markCancelled
     * với CancelType.REPLACED_BY_NEW_ORDER, trả lại lượt voucher (BR-44) và nhả
     * slot đang Held về Free (DBR-36).
     *
     * Trả List chứ không trả 1 đơn: theo luật thì tối đa chỉ có 1, nhưng nếu dữ
     * liệu lỡ có nhiều hơn thì Service vẫn dọn sạch được, thay vì bỏ sót đơn
     * thứ hai.
     *
     * Chỉ lọc Channel = 'Online': đơn tại quầy không áp CBR-71, nó chỉ hủy được
     * theo CBR-11 trong ca đã tạo nó.
     *
     * @param conn Connection tham gia transaction
     * @param holderCustomerId khách đứng tên đơn
     * @param packageId gói đang mua
     * @return danh sách đơn online còn Pending, cũ nhất trước; rỗng nếu không
     * có
     * @throws SQLException khi truy vấn lỗi
     */
    public List<Order> findPendingOnlineByHolderAndPackage(Connection conn, int holderCustomerId, int packageId)
            throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM Orders WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE HolderCustomerId = ? AND PackageId = ? "
                + "AND Channel = 'Online' AND Status = 'Pending' "
                + "ORDER BY CreatedAt ASC";

        List<Order> orders = new ArrayList<>();

        // B1: truy vấn theo khách + gói
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, holderCustomerId);
            stmt.setInt(2, packageId);

            // B2: ánh xạ từng dòng
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapResultSetToOrder(rs));
                }
            }
        }
        return orders;
    }

    /**
     * Bản dùng Connection riêng lẻ, cho chỗ chỉ cần đọc ngoài transaction.
     *
     * @param holderCustomerId khách đứng tên đơn
     * @param packageId gói đang mua
     * @return danh sách đơn online còn Pending
     * @throws SQLException khi truy vấn lỗi
     */
    public List<Order> findPendingOnlineByHolderAndPackage(int holderCustomerId, int packageId) throws SQLException {
        try (Connection conn = DBContext.getConnection()) {
            return findPendingOnlineByHolderAndPackage(conn, holderCustomerId, packageId);
        }
    }

    /**
     * Đọc đơn online thuộc tài khoản hiện tại; không cho xem đơn của khách
     * khác.
     *
     * @param conn kết nối do Service quản lý
     * @param accountId tài khoản từ session
     * @param orderId mã đơn trên URL
     * @return đơn thuộc chủ hoặc null
     */
    public Order findOnlineByIdAndAccount(Connection conn, int accountId, int orderId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM Orders "
                + "WHERE OrderId = ? AND Channel = 'Online' AND HolderCustomerId IN ("
                + "SELECT c.CustomerId FROM Customers c JOIN Accounts a ON a.PersonId = c.PersonId "
                + "WHERE a.AccountId = ? AND a.Status = 'Active' AND c.Status <> 'Inactive')";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            statement.setInt(2, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToOrder(resultSet);
                }
            }
        }
        return null;
    }

    /**
     * Khóa đơn online đúng chủ tới cuối transaction thay voucher.
     */
    public Order findOnlineForUpdate(Connection conn, int accountId, int orderId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM Orders WITH (UPDLOCK, HOLDLOCK) "
                + "WHERE OrderId = ? AND Channel = 'Online' AND HolderCustomerId IN ("
                + "SELECT c.CustomerId FROM Customers c JOIN Accounts a ON a.PersonId = c.PersonId "
                + "WHERE a.AccountId = ? AND a.Status = 'Active' AND c.Status <> 'Inactive')";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            statement.setInt(2, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapResultSetToOrder(resultSet) : null;
            }
        }
    }

    /**
     * Ghi voucher/giảm giá/VAT cùng lúc, chỉ khi đơn còn Pending trong hạn và
     * chưa trả tiền.
     *
     * @param conn transaction giữ lượt voucher và cập nhật đơn
     * @param order đơn do Service đã tính lại giảm giá và VAT
     * @param now mốc giờ VN dùng kiểm hạn; kiểm lại sát lúc ghi
     * @return số đơn cập nhật; 0 thì Service rollback toàn bộ
     */
    public int updateVoucher(Connection conn, Order order, LocalDateTime now) throws SQLException {
        String sql = "UPDATE Orders SET VoucherId = ?, DiscountAmount = ?, VatAmount = ? "
                + "WHERE OrderId = ? AND Channel = 'Online' AND Status = 'Pending' AND PaymentDueAt > ? "
                + "AND NOT EXISTS (SELECT 1 FROM Transactions WHERE OrderId = ? AND Status = 'Pending')";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            setNullableInt(statement, 1, order.getVoucherId());
            statement.setLong(2, order.getDiscountAmount());
            statement.setLong(3, order.getVatAmount());
            statement.setInt(4, order.getOrderId());
            statement.setTimestamp(5, Timestamp.valueOf(now));
            statement.setInt(6, order.getOrderId());
            return statement.executeUpdate();
        }
    }

    // ===== 4. Hủy đơn =====
    /**
     * Hủy một đơn, ghi loại hủy và thời điểm hủy (DBR-43).
     *
     * Điều kiện trạng thái nằm ngay trong WHERE, không kiểm bằng if trước đó:
     * giữa lúc đọc và lúc ghi có thể có JOB-05 hoặc một IPN chen vào, đặt vào
     * WHERE thì database tự phân xử và request sau bị từ chối (GBR-13).
     *
     * Trạng thái được phép hủy, theo DBR-40: CANCELLED_BY_RECEPTIONIST —
     * Pending hoặc Paid (CBR-11, chỉ đơn tại quầy, trong ca đã tạo) ba loại còn
     * lại — chỉ Pending
     *
     * Lý do hủy: CK_Orders_ReceptionistCancel bắt buộc reason ≥ 5 ký tự khi
     * cancelType = CANCELLED_BY_RECEPTIONIST (GBR-09). Service kiểm độ dài
     * trước khi gọi, nếu không database sẽ từ chối với lỗi CHECK khó đọc.
     *
     * @param conn Connection tham gia transaction
     * @param orderId đơn cần hủy
     * @param cancelType loại hủy, không được null
     * @param reason lý do hủy, bắt buộc với CANCELLED_BY_RECEPTIONIST; null với
     * ba loại tự động
     * @return 1 nếu hủy được; 0 nếu đơn không còn ở trạng thái cho phép hủy (đã
     * có người xử lý trước)
     * @throws SQLException khi truy vấn lỗi hoặc vi phạm CHECK
     */
    public int markCancelled(Connection conn, int orderId, Order.CancelType cancelType, String reason)
            throws SQLException {
        // B1: chọn tập trạng thái được phép hủy theo loại hủy (DBR-40)
        boolean byReceptionist = cancelType == Order.CancelType.CANCELLED_BY_RECEPTIONIST;
        String statusCondition = byReceptionist
                ? "Status IN ('Pending', 'Paid')"
                : "Status = 'Pending'";

        String sql = "UPDATE Orders "
                + "SET Status = 'Cancelled', CancelType = ?, CancelledAt = ?, CancelReason = ? "
                + "WHERE OrderId = ? AND " + statusCondition;

        // B2: thực thi và trả về số dòng bị ảnh hưởng
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cancelType == null ? null : cancelType.getDbValue());
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now(DateTimeUtil.GYM_ZONE)));
            stmt.setString(3, reason);
            stmt.setInt(4, orderId);

            return stmt.executeUpdate();
        }
    }

    // ===== Hàm hỗ trợ =====
    /**
     * Gán tham số int cho phép null (partnerCustomerId, voucherId,
     * createdByEmployeeId, shiftId).
     *
     * @param stmt statement đang gán tham số
     * @param index vị trí tham số
     * @param value giá trị, có thể null
     * @throws SQLException khi gán lỗi
     */
    private void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.INTEGER);
        } else {
            stmt.setInt(index, value);
        }
    }

    /**
     * Gán tham số thời điểm cho phép null (paymentDueAt, paidAt, cancelledAt).
     *
     * @param stmt statement đang gán tham số
     * @param index vị trí tham số
     * @param value giá trị, có thể null
     * @throws SQLException khi gán lỗi
     */
    private void setNullableTimestamp(PreparedStatement stmt, int index, LocalDateTime value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.TIMESTAMP);
        } else {
            stmt.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    /**
     * Ánh xạ một dòng ResultSet thành entity Order, dùng constructor đầy đủ 23
     * tham số mà entity đã chuẩn bị sẵn cho DAO.
     *
     * @param rs ResultSet đang trỏ vào dòng cần đọc
     * @return entity Order
     * @throws SQLException khi đọc cột lỗi
     */
    private Order mapResultSetToOrder(ResultSet rs) throws SQLException {
        return new Order(
                rs.getInt("OrderId"),
                rs.getString("OrderCode"),
                rs.getInt("HolderCustomerId"),
                readNullableInt(rs, "PartnerCustomerId"),
                rs.getInt("PackageId"),
                readNullableInt(rs, "VoucherId"),
                readNullableInt(rs, "CreatedByEmployeeId"),
                readNullableInt(rs, "ShiftId"),
                Order.Channel.fromDbValue(rs.getString("Channel")),
                rs.getString("PackageNameSnapshot"),
                rs.getString("PackageDescriptionSnapshot"),
                rs.getString("PackageBenefitsSnapshot"),
                rs.getLong("UnitPriceSnapshot"),
                rs.getLong("DiscountAmount"),
                rs.getInt("VatRateSnapshot"),
                rs.getLong("VatAmount"),
                Order.Status.fromDbValue(rs.getString("Status")),
                readNullableDateTime(rs, "PaymentDueAt"),
                readNullableDateTime(rs, "CreatedAt"),
                readNullableDateTime(rs, "PaidAt"),
                Order.CancelType.fromDbValue(rs.getString("CancelType")),
                readNullableDateTime(rs, "CancelledAt"),
                rs.getString("CancelReason"));
    }

    /**
     * Đọc cột int cho phép null — rs.getInt trả 0 khi NULL nên phải kiểm
     * wasNull.
     *
     * @param rs ResultSet đang trỏ vào dòng cần đọc
     * @param column tên cột
     * @return giá trị, hoặc null nếu cột NULL
     * @throws SQLException khi đọc cột lỗi
     */
    private Integer readNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * Đọc cột thời điểm cho phép null.
     *
     * @param rs ResultSet đang trỏ vào dòng cần đọc
     * @param column tên cột
     * @return giá trị, hoặc null nếu cột NULL
     * @throws SQLException khi đọc cột lỗi
     */
    private LocalDateTime readNullableDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * IPN không có session, nên đọc đơn từ OrderId của Transaction.
     *
     * @param conn kết nối trong transaction của Service
     * @param orderId ID đơn đã đọc từ Transaction
     * @param lock true để giữ khóa đơn đến commit/rollback
     * @return đơn tìm được hoặc null
     */
    public Order findForCallback(
            Connection conn, int orderId, boolean lock) throws SQLException {

        String sql = "SELECT " + SELECT_COLUMNS + " FROM Orders "
                + (lock ? "WITH (UPDLOCK, HOLDLOCK) " : "")
                + "WHERE OrderId = ?";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setInt(1, orderId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return mapResultSetToOrder(resultSet);
                }
            }
        }

        return null;
    }

    /**
     * Chuyển đơn Pending thành Paid sau khi Service xác minh IPN.
     *
     * @param conn kết nối trong transaction của Service
     * @param orderId đơn cần cập nhật
     * @param now thời điểm GMS ghi nhận thanh toán
     * @return số dòng cập nhật; Service yêu cầu đúng 1 dòng
     */
    public int markPaid(
            Connection conn, int orderId, LocalDateTime now)
            throws SQLException {

        String sql = "UPDATE Orders "
                + "SET Status = 'Paid', PaidAt = ? "
                + "WHERE OrderId = ? AND Status = 'Pending'";

        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now));
            statement.setInt(2, orderId);
            return statement.executeUpdate();
        }
    }
       
}
