package com.gms.service;

import com.gms.constant.AppConstants;
import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.AccountDAO;
import com.gms.dao.CustomerDAO;
import com.gms.dao.EmployeeDAO;
import com.gms.dao.PersonDAO;
import com.gms.dto.AccountSettingsView;
import com.gms.dto.DependentCustomerRow;
import com.gms.dto.DependentCustomerView;
import com.gms.dto.ServiceResult;
import com.gms.entity.Account;
import com.gms.entity.Customer;
import com.gms.entity.Employee;
import com.gms.entity.Person;
import com.gms.listener.SessionRegistryListener;
import com.gms.util.DBContext;
import com.gms.util.DateTimeUtil;
import com.gms.util.PasswordUtil;
import com.gms.util.ValidationUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tầng Service xử lý nghiệp vụ Cài đặt tài khoản - UC-04.
 *
 * <p><b>Cách hoạt động:</b> Cung cấp các thao tác xem hồ sơ cá nhân, tra cứu danh sách hồ sơ phụ thuộc
 * được giám hộ, đổi mật khẩu an toàn (có Optimistic Locking và đăng xuất phiên khác), cập nhật/xóa ảnh đại diện.</p>
 */
public class AccountSettingsService {

        private final AccountDAO accountDAO;
        private final CustomerDAO customerDAO;
        private final PersonDAO personDAO;
        private final EmployeeDAO employeeDAO;
        private final AuditLogService auditLogService;
        private final SystemConfigService systemConfigService;

        /**
         * Khởi tạo Service mặc định với đầy đủ các DAO và Service phụ thuộc thực tế.
         */
        public AccountSettingsService() {
                this(new AccountDAO(), new CustomerDAO(), new PersonDAO(), new EmployeeDAO(), new AuditLogService(), new SystemConfigService());
        }

        /**
         * Constructor phạm vi package dùng để tiêm DAO/Service giả lập khi kiểm thử đơn vị.
         */
        AccountSettingsService(AccountDAO accountDAO, CustomerDAO customerDAO, PersonDAO personDAO,
                               EmployeeDAO employeeDAO, AuditLogService auditLogService, SystemConfigService systemConfigService) {
                this.accountDAO = accountDAO;
                this.customerDAO = customerDAO;
                this.personDAO = personDAO;
                this.employeeDAO = employeeDAO;
                this.auditLogService = auditLogService;
                this.systemConfigService = systemConfigService;
        }

        /**
         * Lấy thông tin tài khoản và hồ sơ hiển thị của người dùng hiện tại theo vai trò phiên đăng nhập.
         *
         * <p><b>Cách hoạt động:</b> Tra cứu {@code Account} và {@code Person}; nếu vai trò phiên là
         * {@code CUSTOMER} thì lấy thông tin hội viên (loại bỏ hồ sơ đã xóa mềm), ngược lại lấy thông tin
         * nhân viên (chỉ kèm thông số PT khi {@code sessionRole == PT}) và đóng gói vào {@link AccountSettingsView}.</p>
         *
         * @param accountId   id tài khoản đang đăng nhập
         * @param sessionRole vai trò lưu trong session
         * @return {@link ServiceResult} chứa dữ liệu Account Settings
         */
        public ServiceResult<AccountSettingsView> getAccountSettings(
                int accountId,
                Role sessionRole) {

                try {
                        Account account = accountDAO.findById(accountId);

                        if (account == null) {
                                String message = String.format(
                                        Messages.MSG08_NOT_FOUND,
                                        "tài khoản");

                                return ServiceResult.fail(message);
                        }

                        Person person = personDAO.findById(
                                account.getPersonId());

                        if (person == null) {
                                String message = String.format(
                                        Messages.MSG08_NOT_FOUND,
                                        "hồ sơ");

                                return ServiceResult.fail(message);
                        }

                        Employee employee = null;
                        Customer customer = null;

                        boolean isCustomer = sessionRole == Role.CUSTOMER;

                        if (isCustomer) {
                                // Chỉ tìm theo PersonId của Account, không theo số điện thoại giám hộ.
                                // DAO ưu tiên hồ sơ chưa Inactive; schema chỉ cho một hồ sơ còn hiệu lực.
                                customer = customerDAO.findByPersonId(person.getPersonId());
                                if (customer != null && customer.getDeletedAt() != null) {
                                        // Không đưa hồ sơ cũ đã xóa lên Profile khi không còn hồ sơ hiện hành.
                                        customer = null;
                                }
                        } else {
                                employee = employeeDAO.findByPersonId(
                                        person.getPersonId());
                        }

                        String employeeCode = employee == null
                                ? null
                                : employee.getEmployeeCode();

                        LocalDate hireDate = employee == null
                                ? null
                                : employee.getHireDate();

                        String employmentStatus = employee == null || employee.getStatus() == null
                                ? null
                                : employee.getStatus().getDbValue();

                        AccountSettingsView view = new AccountSettingsView(
                                account.getAccountId(),
                                person.getPersonId(),
                                person.getFullName(),
                                person.getDateOfBirth(),
                                person.getPhone(),
                                person.getEmail(),
                                person.getAvatar(),
                                sessionRole,
                                employeeCode,
                                hireDate,
                                employmentStatus,
                                employee == null ? null : employee.getPosition(),
                                employee == null || sessionRole != Role.PT ? null : employee.getSpecialty(),
                                employee == null || sessionRole != Role.PT ? null : employee.getYearsOfExperience(),
                                employee == null || sessionRole != Role.PT ? null : employee.getCommissionRate(),
                                customer == null ? null : customer.getCustomerCode(),
                                customer == null ? null : customer.getCustomerType(),
                                customer == null ? null : customer.getStatus(),
                                customer == null ? null : customer.getJoinedAt());

                        return ServiceResult.ok(view);

                } catch (SQLException e) {
                        e.printStackTrace();
                        return ServiceResult.fail(
                                Messages.MSG04_SYSTEM_BUSY);
                }
        }

        /**
         * Lấy danh sách hồ sơ phụ thuộc mà người dùng hiện tại đang là người giám hộ.
         *
         * <p><b>Cách hoạt động:</b> Chỉ cho phép vai trò {@code CUSTOMER}, lấy số điện thoại của người dùng để tìm
         * các hồ sơ phụ thuộc trùng SĐT giám hộ qua {@code CustomerDAO}, đọc ngưỡng tuổi PT tối thiểu từ
         * {@code SystemConfigService} rồi chuyển đổi từng dòng bằng {@link #buildDependentView}.</p>
         *
         * @param accountId   id tài khoản đang đăng nhập
         * @param sessionRole vai trò đang dùng trong session
         * @return danh sách hồ sơ phụ thuộc, rỗng nếu không phải Customer
         */
        public ServiceResult<List<DependentCustomerView>> getDependents(
                int accountId,
                Role sessionRole) {

                // B1: chỉ vai trò Customer được xem (CBR-112)
                if (sessionRole != Role.CUSTOMER) {
                        return ServiceResult.ok(
                                new ArrayList<>());
                }

                try {
                        // B2: lấy SĐT người giám hộ từ Person
                        Account account =
                                accountDAO.findById(accountId);

                        if (account == null) {
                                return ServiceResult.fail(
                                        String.format(
                                                Messages.MSG08_NOT_FOUND,
                                                "tài khoản"));
                        }

                        Person person =
                                personDAO.findById(
                                        account.getPersonId());

                        if (person == null) {
                                return ServiceResult.fail(
                                        String.format(
                                                Messages.MSG08_NOT_FOUND,
                                                "hồ sơ"));
                        }

                        String guardianPhone = person.getPhone();

                        if (ValidationUtil.isNullOrEmpty(
                                guardianPhone)) {
                                return ServiceResult.ok(
                                        new ArrayList<>());
                        }

                        // B3: gọi CustomerDAO tìm Dependent
                        //     theo SĐT (CBR-33)
                        List<DependentCustomerRow> rows =
                                customerDAO
                                        .findDependentsByGuardianPhone(
                                                guardianPhone);

                        // B4: đọc ngưỡng tuổi P-05
                        //     từ SystemConfigService
                        int minAgeForPt = systemConfigService.getInt(
                                SystemConfigService.MIN_AGE_FOR_PT);

                        LocalDate today = DateTimeUtil.today();

                        // B5: dựng DependentCustomerView
                        //     cho từng hồ sơ
                        List<DependentCustomerView> views =
                                new ArrayList<>();

                        for (DependentCustomerRow row : rows) {
                                views.add(
                                        buildDependentView(
                                                row,
                                                today,
                                                minAgeForPt));
                        }

                        return ServiceResult.ok(views);

                } catch (SQLException e) {
                        e.printStackTrace();

                        return ServiceResult.fail(
                                Messages.MSG04_SYSTEM_BUSY);
                }
        }

        /**
         * Chuyển đổi {@link DependentCustomerRow} thô thành {@link DependentCustomerView} để hiển thị trên UI.
         *
         * <p><b>Cách hoạt động:</b> Tính tuổi hiện tại so với {@code today}, đối chiếu với ngưỡng {@code minAgeForPt}
         * để gắn cờ dưới tuổi tập PT và tạo nhãn gói tập qua {@link #buildPackageLabel}.</p>
         *
         * @param row         dữ liệu thô từ DAO
         * @param today       ngày hiện tại (theo múi giờ gym)
         * @param minAgeForPt ngưỡng tuổi P-05 từ SystemConfig
         * @return view hiển thị cho JSP
         */
        private DependentCustomerView buildDependentView(
                DependentCustomerRow row,
                LocalDate today,
                int minAgeForPt) {

                int age = DateTimeUtil.calculateAge(
                        row.getDateOfBirth(), today);

                String ageLabel = age + " tuổi";

                boolean underTen = age < minAgeForPt;

                String activePackageLabel =
                        buildPackageLabel(
                                row.getPackageName(),
                                row.getPackageEndDate());

                return new DependentCustomerView(
                        row.getFullName(),
                        row.getCustomerCode(),
                        ageLabel,
                        underTen,
                        activePackageLabel);
        }

        /**
         * Tạo nhãn hiển thị gói tập hiện tại của hồ sơ phụ thuộc.
         *
         * <p><b>Cách hoạt động:</b> Trả về {@code "Chưa có gói tập"} nếu thiếu tên gói hoặc ngày hết hạn;
         * ngược lại ghép tên gói kèm ngày hết hạn đã định dạng.</p>
         *
         * @param packageName    tên gói, null nếu không có
         * @param packageEndDate ngày hết hạn gói, null nếu không có
         * @return nhãn hiển thị, VD: "Gói Bạc (đến 05/10/2026)" hoặc "Chưa có gói tập"
         */
        private String buildPackageLabel(
                String packageName,
                LocalDate packageEndDate) {

                if (ValidationUtil.isNullOrEmpty(packageName)
                        || packageEndDate == null) {
                        return "Chưa có gói tập";
                }

                return packageName
                        + " (đến "
                        + DateTimeUtil.formatDate(
                        packageEndDate)
                        + ")";
        }

        /**
         * Đổi mật khẩu cho chính tài khoản đang đăng nhập.
         *
         * <p><b>Cách hoạt động:</b> Kiểm tra tuần tự: mật khẩu hiện tại phải nhập và khớp DB, mật khẩu mới hợp
         * quy tắc và khác mật khẩu cũ, xác nhận khớp; băm mật khẩu mới bằng BCrypt, mở transaction cập nhật theo
         * Optimistic Locking ({@code oldPasswordHash}) kèm ghi {@code AuditLog}, rồi vô hiệu hóa toàn bộ phiên khác.</p>
         *
         * @param accountId        id tài khoản hiện tại
         * @param currentPassword  mật khẩu hiện tại
         * @param newPassword      mật khẩu mới
         * @param confirmPassword  xác nhận mật khẩu mới
         * @param currentSessionId id phiên hiện tại cần giữ lại
         * @param actingRole       vai trò đang dùng khi đổi mật khẩu
         * @return kết quả đổi mật khẩu
         */
        public ServiceResult<Void> changePassword(
                int accountId,
                String currentPassword,
                String newPassword,
                String confirmPassword,
                String currentSessionId,
                Role actingRole) {

                // B1: Bắt buộc nhập mật khẩu hiện tại
                if (ValidationUtil.isNullOrEmpty(
                        currentPassword)) {
                        return ServiceResult.failField(
                                "currentPassword",
                                String.format(Messages.MSG09_REQUIRED, "Current password"));
                }

                try {
                        Account account = accountDAO.findById(accountId);
                        if (account == null) {
                                // Dừng hàm đổi mật khẩu và trả về kết quả thất bại, kèm thông báo không tìm thấy tài khoản.
                                return ServiceResult.fail(
                                        String.format(Messages.MSG08_NOT_FOUND, "tài khoản"));
                        }

                        // B2: Kiểm tra mật khẩu hiện tại trước tiên
                        boolean isCurrentPasswordCorrect = PasswordUtil.checkPassword(
                                currentPassword,
                                account.getPasswordHash());

                        if (!isCurrentPasswordCorrect) {
                                return ServiceResult.failField(
                                        "currentPassword",
                                        Messages.MSG71_CURRENT_PASSWORD_WRONG);
                        }

                        // B3: Chỉ kiểm tra mật khẩu mới sau khi mật khẩu hiện tại đã đúng
                        if (ValidationUtil.isNullOrEmpty(
                                newPassword)) {
                                return ServiceResult.failField(
                                        "newPassword",
                                        String.format(Messages.MSG09_REQUIRED, "New password"));
                        }

                        boolean isNewPasswordValid = ValidationUtil.isValidPassword(
                                newPassword);

                        if (!isNewPasswordValid) {
                                return ServiceResult.failField(
                                        "newPassword",
                                        String.format(Messages.MSG66_PASSWORD_RULE,
                                                AppConstants.PASSWORD_MIN_LENGTH,
                                                AppConstants.PASSWORD_MAX_LENGTH));
                        }

                        // B4: Mật khẩu mới không được giống mật khẩu cũ
                        boolean isSameAsCurrentPassword = PasswordUtil.checkPassword(
                                newPassword,
                                account.getPasswordHash());

                        if (isSameAsCurrentPassword) {
                                return ServiceResult.failField(
                                        "newPassword",
                                        Messages.MSG48_PASSWORD_SAME_AS_OLD);
                        }

                        // B5: Bắt buộc nhập xác nhận mật khẩu
                        if (ValidationUtil.isNullOrEmpty(
                                confirmPassword)) {
                                return ServiceResult.failField(
                                        "confirmPassword",
                                        String.format(Messages.MSG09_REQUIRED, "Confirm new password"));
                        }

                        // B6: Xác nhận phải khớp mật khẩu mới
                        boolean isPasswordMatched = newPassword.equals(
                                confirmPassword);

                        if (!isPasswordMatched) {
                                return ServiceResult.failField(
                                        "confirmPassword",
                                        Messages.MSG47_PASSWORD_MISMATCH);
                        }

                        // B7: Băm mật khẩu mới
                        String newPasswordHash = PasswordUtil.hashPassword(
                                newPassword);

                        // B8: Cập nhật mật khẩu và ghi AuditLog trong cùng một transaction
                        try (Connection conn = DBContext.getConnection()) {
                                // Tắt chế độ tự động chốt thay đổi vào database
                                conn.setAutoCommit(false);

                                try {
                                        boolean isUpdated = accountDAO.updatePasswordHash(
                                                conn,
                                                accountId,
                                                newPasswordHash,
                                                account.getPasswordHash());

                                        if (!isUpdated) {
                                                conn.rollback();

                                                return ServiceResult.fail(
                                                        Messages.MSG22_CONCURRENT_EDIT);
                                        }
                                        auditLogService.logInTransaction(
                                                conn,
                                                accountId,
                                                actingRole,
                                                "Người dùng đã thay đổi mật khẩu của tài khoản cá nhân.",
                                                "Account",
                                                accountId,
                                                "PasswordHash",
                                                null,
                                                null,
                                                null);

                                        conn.commit();

                                } catch (SQLException e) {
                                        conn.rollback();
                                        e.printStackTrace();

                                        return ServiceResult.fail(
                                                Messages.MSG04_SYSTEM_BUSY);
                                }
                        }

                        // B9: Đăng xuất tất cả phiên khác, giữ lại phiên hiện tại
                        SessionRegistryListener
                                .invalidateSessionsOfAccount(
                                        accountId,
                                        currentSessionId);

                        return ServiceResult.ok(null);

                } catch (SQLException e) {
                        e.printStackTrace();

                        return ServiceResult.fail(
                                Messages.MSG04_SYSTEM_BUSY);
                }
        }

        /**
         * Cập nhật ảnh đại diện (JPG/PNG) cho tài khoản đang đăng nhập.
         *
         * <p><b>Cách hoạt động:</b> Validate định dạng và dung lượng file ảnh, lưu file mới vào
         * {@code uploadDirectory} bằng UUID chống trùng tên và kiểm tra path-traversal, cập nhật đường dẫn mới
         * vào bảng {@code Persons} (xóa file mới nếu update DB thất bại), sau đó dọn ảnh cũ và ghi {@code AuditLog}.</p>
         *
         * @param accountId       id tài khoản hiện tại
         * @param contentType     MIME type của file
         * @param sizeInBytes     kích thước file
         * @param inputStream     luồng dữ liệu ảnh
         * @param uploadDirectory thư mục lưu avatar vật lý
         * @return kết quả cập nhật
         */
        public ServiceResult<Void> updateAvatar(
                int accountId,
                String contentType,
                long sizeInBytes,
                InputStream inputStream,
                Path uploadDirectory) {

                boolean isValidImage = ValidationUtil.isValidImageFile(
                        contentType,
                        sizeInBytes);

                if (!isValidImage) {

                        long maxSizeMb = AppConstants.IMAGE_MAX_BYTES
                                / (1024L * 1024L);

                        String message = String.format(
                                Messages.MSG20_INVALID_IMAGE,
                                maxSizeMb + " MB");

                        return ServiceResult.failField(
                                "avatarFile",
                                message);
                }

                if (inputStream == null
                        || uploadDirectory == null) {

                        return ServiceResult.fail(
                                Messages.MSG04_SYSTEM_BUSY);
                }

                Path newFilePath = null;

                try {

                        Account account = accountDAO.findById(accountId);

                        if (account == null) {

                                return ServiceResult.fail(
                                        String.format(
                                                Messages.MSG08_NOT_FOUND,
                                                "tài khoản"));
                        }

                        Person person = personDAO.findById(
                                account.getPersonId());

                        if (person == null) {

                                return ServiceResult.fail(
                                        String.format(
                                                Messages.MSG08_NOT_FOUND,
                                                "hồ sơ"));
                        }

                        Files.createDirectories(
                                uploadDirectory);

                        String extension = "image/png".equals(contentType)
                                ? ".png"
                                : ".jpg";

                        String fileName = "avatar-"
                                + person.getPersonId()
                                + "-"
                                + UUID.randomUUID()
                                + extension;

                        newFilePath = uploadDirectory
                                .resolve(fileName)
                                .normalize();

                        Path normalizedDirectory = uploadDirectory
                                .toAbsolutePath()
                                .normalize();

                        Path normalizedFile = newFilePath
                                .toAbsolutePath()
                                .normalize();

                        if (!normalizedFile.startsWith(
                                normalizedDirectory)) {

                                return ServiceResult.fail(
                                        Messages.MSG04_SYSTEM_BUSY);
                        }

                        Files.copy(
                                inputStream,
                                newFilePath,
                                StandardCopyOption.REPLACE_EXISTING);

                        String avatarPath = "/uploads/avatar/"
                                + fileName;

                        String oldAvatar = person.getAvatar();
                        boolean isUpdated = personDAO.updateAvatar(
                                person.getPersonId(),
                                avatarPath);

                        if (!isUpdated) {

                                Files.deleteIfExists(
                                        newFilePath);

                                return ServiceResult.fail(
                                        Messages.MSG04_SYSTEM_BUSY);
                        }

                        deleteOldAvatarFile(
                                oldAvatar,
                                uploadDirectory);

                        auditLogService.log(
                                accountId,
                                "Cập nhật ảnh đại diện cá nhân",
                                "Person",
                                person.getPersonId(),
                                "Avatar",
                                oldAvatar,
                                avatarPath,
                                null);

                        return ServiceResult.ok(null);

                } catch (SQLException | IOException e) {

                        if (newFilePath != null) {

                                try {

                                        Files.deleteIfExists(
                                                newFilePath);

                                } catch (IOException ignored) {
                                        // Không làm thay đổi kết quả lỗi chính.
                                }
                        }

                        e.printStackTrace();

                        return ServiceResult.fail(
                                Messages.MSG04_SYSTEM_BUSY);
                }
        }

        /**
         * Xóa ảnh đại diện của tài khoản hiện tại, đưa {@code Persons.Avatar} về {@code null} và xóa file cũ an toàn.
         *
         * <p><b>Cách hoạt động:</b> Tra cứu tài khoản và hồ sơ kiểm tra có ảnh hay không, cập nhật cột {@code Avatar}
         * về {@code null} trong CSDL trước rồi gọi {@link #deleteOldAvatarFile} xóa tệp vật lý và ghi {@code AuditLog}.</p>
         *
         * @param accountId       id tài khoản hiện tại
         * @param uploadDirectory thư mục chứa avatar vật lý
         * @return kết quả xóa ảnh đại diện
         */
        public ServiceResult<Void> removeAvatar(
                int accountId,
                Path uploadDirectory) {

                try {
                        Account account = accountDAO.findById(accountId);
                        if (account == null) {
                                return ServiceResult.fail(
                                        String.format(Messages.MSG08_NOT_FOUND, "tài khoản"));
                        }

                        Person person = personDAO.findById(account.getPersonId());
                        if (person == null) {
                                return ServiceResult.fail(
                                        String.format(Messages.MSG08_NOT_FOUND, "hồ sơ"));
                        }

                        String currentAvatar = person.getAvatar();
                        if (ValidationUtil.isNullOrEmpty(currentAvatar)) {
                                return ServiceResult.fail(Messages.MSG319_AVATAR_EMPTY);
                        }

                        boolean isUpdated = personDAO.updateAvatar(person.getPersonId(), null);
                        if (!isUpdated) {
                                return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                        }

                        if (uploadDirectory != null) {
                                deleteOldAvatarFile(currentAvatar, uploadDirectory);
                        }

                        auditLogService.log(
                                accountId,
                                "Xóa ảnh đại diện cá nhân",
                                "Person",
                                person.getPersonId(),
                                "Avatar",
                                currentAvatar,
                                null,
                                null);

                        return ServiceResult.ok(null, Messages.MSG58_AVATAR_REMOVED);

                } catch (SQLException e) {
                        e.printStackTrace();
                        return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
                }
        }

        /**
         * Xóa tệp avatar vật lý cũ do chức năng Account Settings tạo ra (không đụng tới ảnh check-in hay loại khác).
         *
         * <p><b>Cách hoạt động:</b> Kiểm tra tiền tố {@code /uploads/avatar/} và xác minh đường dẫn chuẩn hóa
         * nằm bên trong {@code uploadDirectory} để phòng tránh path-traversal trước khi gọi {@code Files.deleteIfExists}.</p>
         *
         * @param oldAvatarPath   đường dẫn avatar cũ trong DB
         * @param uploadDirectory thư mục avatar vật lý
         */
        private void deleteOldAvatarFile(
                String oldAvatarPath,
                Path uploadDirectory) {

                if (ValidationUtil.isNullOrEmpty(
                        oldAvatarPath)) {
                        return;
                }

                String avatarPrefix = "/uploads/avatar/";

                if (!oldAvatarPath.startsWith(
                        avatarPrefix)) {
                        return;
                }

                String oldFileName = oldAvatarPath.substring(
                        avatarPrefix.length());

                Path normalizedDirectory = uploadDirectory
                        .toAbsolutePath()
                        .normalize();

                Path oldFile = uploadDirectory
                        .resolve(oldFileName)
                        .toAbsolutePath()
                        .normalize();

                if (!oldFile.startsWith(
                        normalizedDirectory)) {
                        return;
                }

                try {

                        Files.deleteIfExists(
                                oldFile);

                } catch (IOException ignored) {
                        // Avatar mới đã cập nhật thành công; lỗi dọn file cũ không làm rollback nghiệp vụ.
                }
        }
}
