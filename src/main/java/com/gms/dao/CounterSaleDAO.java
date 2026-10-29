package com.gms.dao;

import com.gms.dto.CounterCustomerRow;
import com.gms.entity.Customer;
import com.gms.entity.Package;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO MÀN LỄ TÂN BÁN GÓI TẠI QUẦY (/receptionist/sales), SRS #37, đợt 1: Chọn gói + Chọn khách.
 *
 * Mọi hàm nhận Connection do Service mở. DAO không catch SQLException, Service quyết định xử lý.
 * Kiểm tra "tài khoản này có phải Lễ tân" dùng lại PtSaleDAO.findReceptionist, không viết lại ở đây.
 *
 *   findActivePackages   gói đang bán (IsActive = 1) của cả 5 loại, đúng thứ tự hiển thị
 *   findActivePackage    một gói đang bán theo mã
 *   searchCustomers      ô tìm khách: tên (không dấu) / SĐT / mã KH-nnnn, tối đa 20 người
 *   findCustomer         một khách theo mã (bước tiếp theo cần tên khách)
 */
public class CounterSaleDAO {

    // Cột Subscriptions đọc chung cho searchCustomers và findCustomer: gói tập còn hạn hôm nay (Active hoặc Frozen).
    // Tham số "?" của đoạn này là ngày hôm nay.
    private static final String CURRENT_MEMBERSHIP_APPLY = """
            OUTER APPLY (
                SELECT TOP 1 o.PackageNameSnapshot AS PackageName, s.EndDate
                FROM Subscriptions s
                JOIN Orders o ON o.OrderId = s.OrderId
                WHERE s.CustomerId = c.CustomerId
                  AND s.SubscriptionType = 'Membership'
                  AND s.Status IN ('Active', 'Frozen')
                  AND s.EndDate >= ?
                ORDER BY s.EndDate DESC
            ) m
            """;

    /**
     * Lấy mọi gói đang bán của 5 loại, sắp theo thứ tự hiển thị trên màn:
     * Membership, DayPass, PTSingle, PTCouple, SingleSession; trong mỗi loại thì ngắn đến dài rồi rẻ đến đắt.
     * Gói đã tắt (IsActive = 0) không có trong kết quả. Đơn cũ vẫn giữ tên và giá lúc mua trong Orders.
     * @param connection kết nối do Service mở
     * @return danh sách gói; rỗng nếu chưa có gói nào đang bán
     */
    public List<Package> findActivePackages(Connection connection) throws SQLException {
        String sql = """
                SELECT PackageId, PackageName, Benefits, PackageType, DurationUnit, DurationValue, Price
                FROM Packages
                WHERE IsActive = 1
                  AND PackageType IN ('Membership', 'DayPass', 'PTSingle', 'PTCouple', 'SingleSession')
                ORDER BY CASE PackageType
                             WHEN 'Membership' THEN 1
                             WHEN 'DayPass' THEN 2
                             WHEN 'PTSingle' THEN 3
                             WHEN 'PTCouple' THEN 4
                             ELSE 5 END,
                         CASE DurationUnit WHEN 'Day' THEN 0 ELSE 1 END,
                         DurationValue,
                         Price
                """;
        List<Package> packages = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                // B1: đọc từng cột của dòng hiện tại.
                int packageId = resultSet.getInt("PackageId");
                String packageName = resultSet.getString("PackageName");
                String benefits = resultSet.getString("Benefits");
                Package.Type packageType = Package.Type.fromDbValue(resultSet.getString("PackageType"));
                String durationUnit = resultSet.getString("DurationUnit");
                int durationValue = resultSet.getInt("DurationValue");
                boolean isDurationMissing = resultSet.wasNull();
                int price = resultSet.getInt("Price");

                // B2: gói không có thời hạn (buổi lẻ) thì DurationValue là NULL, giữ null trong entity.
                Package pkg = new Package(packageId);
                pkg.setPackageName(packageName);
                pkg.setBenefits(benefits);
                pkg.setPackageType(packageType);
                pkg.setDurationUnit(durationUnit);
                if (!isDurationMissing) {
                    pkg.setDurationValue(durationValue);
                }
                pkg.setPrice(price);
                pkg.setActive(true);
                packages.add(pkg);
            }
        }
        return packages;
    }

    /**
     * Lấy một gói đang bán theo mã (khi URL có ?package=…).
     * @param connection kết nối do Service mở
     * @param packageId mã gói từ URL (đã kiểm tra là số dương ở Servlet)
     * @return gói, hoặc null nếu không có / đã tắt bán
     */
    public Package findActivePackage(Connection connection, int packageId) throws SQLException {
        List<Package> packages = findActivePackages(connection);
        for (Package pkg : packages) {
            if (pkg.getPackageId() == packageId) {
                return pkg;
            }
        }
        return null;
    }

    /**
     * Ô tìm khách: tối đa 20 khách khớp MỘT trong 3 cách (không phân biệt hoa thường và dấu):
     *   tên chứa các từ gõ theo đúng thứ tự ("do mai" khớp "Đỗ Thị Mai"),
     *   SĐT hoặc SĐT giám hộ chứa dãy số, mã KH chứa chữ gõ.
     * Hồ sơ đã xóa mềm (DeletedAt) và hồ sơ Inactive không hiện. Mọi trạng thái còn lại đều có trong kết quả,
     * vì mua gói tập / vé ngày không bị chặn theo trạng thái khách (chỉ luồng bán PT mới chặn).
     * Collation Latin1_General_CI_AI giống PtSaleDAO: bản Vietnamese coi ă, ê... là chữ riêng nên gõ "van" không ra "Văn".
     * @param connection kết nối do Service mở
     * @param text chữ lễ tân gõ (đã trim, từ 2 ký tự)
     * @param digits chỉ phần chữ số của text ("090 123" thành "090123"); rỗng thì không xét SĐT
     * @param today ngày hôm nay, dùng để biết gói tập nào còn hạn
     * @param receptionistPersonId mã Person của lễ tân đang đăng nhập, để đánh dấu hồ sơ của chính họ (CBR-115)
     * @return danh sách khách, xếp: khớp đúng mã hoặc SĐT trước, rồi tên bắt đầu bằng chữ gõ, rồi theo tên
     */
    public List<CounterCustomerRow> searchCustomers(Connection connection, String text, String digits, LocalDate today,
            int receptionistPersonId) throws SQLException {
        String sql = """
                SELECT TOP 20 c.CustomerId, c.CustomerCode, c.CustomerType, c.Status, c.GuardianPhone,
                       p.FullName, p.Phone,
                       CASE WHEN c.PersonId = ? THEN 1 ELSE 0 END AS IsOwnProfile,
                       CASE WHEN EXISTS (SELECT 1 FROM Accounts a WHERE a.PersonId = c.PersonId)
                            THEN 1 ELSE 0 END AS HasAccount,
                       m.PackageName, m.EndDate
                FROM Customers c
                JOIN Persons p ON p.PersonId = c.PersonId
                """ + CURRENT_MEMBERSHIP_APPLY + """
                WHERE c.DeletedAt IS NULL
                  AND c.Status <> 'Inactive'
                  AND (p.FullName COLLATE Latin1_General_CI_AI LIKE ? COLLATE Latin1_General_CI_AI
                       OR c.CustomerCode LIKE ?
                       OR (? <> '' AND (p.Phone LIKE ? OR c.GuardianPhone LIKE ? OR c.GuardianPhone2 LIKE ?)))
                ORDER BY CASE WHEN c.CustomerCode = ? OR p.Phone = ? THEN 0
                              WHEN p.FullName COLLATE Latin1_General_CI_AI LIKE ? COLLATE Latin1_General_CI_AI THEN 1
                              ELSE 2 END,
                         p.FullName
                """;

        // B1: dựng các mẫu LIKE từ chữ gõ.
        String escapedText = escapeLike(text);
        String nameLike = "%" + String.join("%", escapedText.split("\\s+")) + "%";
        String codeLike = "%" + escapedText + "%";
        String digitsLike = "%" + digits + "%";
        String startsWithLike = escapedText + "%";

        List<CounterCustomerRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            // B2: truyền tham số theo đúng thứ tự dấu "?" trong câu SQL (dấu đầu tiên nằm ở cột IsOwnProfile).
            statement.setInt(1, receptionistPersonId);
            statement.setDate(2, Date.valueOf(today));
            statement.setNString(3, nameLike);
            statement.setString(4, codeLike);
            statement.setString(5, digits);
            statement.setString(6, digitsLike);
            statement.setString(7, digitsLike);
            statement.setString(8, digitsLike);
            statement.setString(9, text.toUpperCase());
            statement.setString(10, digits);
            statement.setNString(11, startsWithLike);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    // B3: đọc từng cột của dòng hiện tại rồi tạo DTO.
                    rows.add(readCustomerRow(resultSet));
                }
            }
        }
        return rows;
    }

    /**
     * Lấy một khách theo mã (khi URL có ?customer=…).
     * @param connection kết nối do Service mở
     * @param customerId mã khách từ URL
     * @param today ngày hôm nay
     * @param receptionistPersonId mã Person của lễ tân đang đăng nhập (CBR-115)
     * @return khách, hoặc null nếu hồ sơ không có / đã xóa / Inactive
     */
    public CounterCustomerRow findCustomer(Connection connection, int customerId, LocalDate today,
            int receptionistPersonId) throws SQLException {
        String sql = """
                SELECT c.CustomerId, c.CustomerCode, c.CustomerType, c.Status, c.GuardianPhone,
                       p.FullName, p.Phone,
                       CASE WHEN c.PersonId = ? THEN 1 ELSE 0 END AS IsOwnProfile,
                       CASE WHEN EXISTS (SELECT 1 FROM Accounts a WHERE a.PersonId = c.PersonId)
                            THEN 1 ELSE 0 END AS HasAccount,
                       m.PackageName, m.EndDate
                FROM Customers c
                JOIN Persons p ON p.PersonId = c.PersonId
                """ + CURRENT_MEMBERSHIP_APPLY + """
                WHERE c.DeletedAt IS NULL
                  AND c.Status <> 'Inactive'
                  AND c.CustomerId = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, receptionistPersonId);
            statement.setDate(2, Date.valueOf(today));
            statement.setInt(3, customerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return readCustomerRow(resultSet);
                }
                return null;
            }
        }
    }

    /**
     * Đọc dòng hiện tại của ResultSet thành CounterCustomerRow (dùng chung cho searchCustomers và findCustomer
     * vì hai câu SQL có cùng danh sách cột).
     */
    private CounterCustomerRow readCustomerRow(ResultSet resultSet) throws SQLException {
        int customerId = resultSet.getInt("CustomerId");
        String customerCode = resultSet.getString("CustomerCode");
        String fullName = resultSet.getString("FullName");
        boolean isDependent = "Dependent".equals(resultSet.getString("CustomerType"));
        boolean hasAccount = resultSet.getInt("HasAccount") == 1;
        boolean isOwnProfile = resultSet.getInt("IsOwnProfile") == 1;
        Customer.Status status = Customer.Status.fromDbValue(resultSet.getString("Status"));

        // Hồ sơ Dependent không có SĐT riêng, hiện SĐT người giám hộ.
        String phone = resultSet.getString("Phone");
        if (isDependent) {
            phone = resultSet.getString("GuardianPhone");
        }

        String currentPackageName = resultSet.getString("PackageName");
        LocalDate currentPackageEnd = null;
        Date endDate = resultSet.getDate("EndDate");
        if (endDate != null) {
            currentPackageEnd = endDate.toLocalDate();
        }

        return new CounterCustomerRow(customerId, customerCode, fullName, phone, isDependent, hasAccount,
                status, currentPackageName, currentPackageEnd, isOwnProfile);
    }

    /** Ký tự đặc biệt của LIKE (% _ [) được hiểu là chữ thường, không phải ký tự đại diện. */
    private static String escapeLike(String text) {
        return text.replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
    }
}