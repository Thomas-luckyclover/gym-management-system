package com.gms.service;

import com.gms.constant.Role;
import com.gms.dao.*;
import com.gms.dto.*;
import com.gms.entity.*;
import com.gms.util.*;
import java.sql.Connection;
import java.sql.SQLException;

/** Nghiệp vụ sửa hồ sơ tại quầy. Kiểm tra liên hệ, giám hộ và lưu cùng transaction. */
public class CustomerProfileService {
    private final CustomerDAO customers = new CustomerDAO();

    public ServiceResult<Void> update(int accountId, int customerId, CounterCustomerCreateInput input,
            String guardianPhone2, String photoPath) {
        return update(accountId, customerId, input, guardianPhone2, photoPath, false);
    }

    public ServiceResult<Void> update(int accountId, int customerId, CounterCustomerCreateInput input,
            String guardianPhone2, String photoPath, boolean identityVerified) {
        // B1: kiểm tra dữ liệu từ form trước khi mở transaction.
        if (input == null || !ValidationUtil.isValidFullName(input.getFullName()))
            return ServiceResult.failField("fullName", "Họ tên không hợp lệ.");
        if (input.getDateOfBirth() == null || !ValidationUtil.isValidDateOfBirth(input.getDateOfBirth(), DateTimeUtil.today()))
            return ServiceResult.failField("dateOfBirth", "Ngày sinh không hợp lệ.");
        try (Connection conn = DBContext.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // B2: kiểm tra quyền Lễ tân và khóa hồ sơ đang sửa.
                if (new PtSaleDAO().findReceptionist(conn, accountId) == null) {
                    conn.rollback();
                    return ServiceResult.fail("Bạn không có quyền sửa hồ sơ.");
                }
                if (new SelfServiceGuard().isOwnCustomerProfile(accountId, customerId)) {
                    conn.rollback();
                    return ServiceResult.fail("Hãy nhờ nhân viên khác sửa hồ sơ khách hàng của bạn.");
                }
                Customer customer = customers.findById(conn, customerId, true);
                if (customer == null || "Inactive".equals(customer.getStatus())) {
                    conn.rollback();
                    return ServiceResult.fail("Hồ sơ không tồn tại hoặc đã ngừng hoạt động.");
                }
                PersonDAO persons = new PersonDAO();
                Person person = persons.findForProfileUpdate(conn, customer.getPersonId());
                if (person == null) {
                    conn.rollback();
                    return ServiceResult.fail("Không tìm thấy thông tin cá nhân.");
                }
                String oldProfile = personSnapshot(person);
                String oldEmail = person.getEmail();
                String oldGuardian = customerSnapshot(customer);
                String phone = person.getPhone();
                String email = person.getEmail();
                boolean emailChanged = false;
                com.gms.entity.Account affectedAccount = null;
                if ("Self".equals(customer.getCustomerType())) {
                    phone = input.getPhone();
                    email = input.getEmail();
                    if (!ValidationUtil.isValidPhone(phone) || !ValidationUtil.isValidEmail(email)) {
                        conn.rollback();
                        return ServiceResult.fail("Số điện thoại hoặc email không hợp lệ.");
                    }
                    boolean phoneChanged = !java.util.Objects.equals(phone, person.getPhone());
                    emailChanged = person.getEmail() == null || !email.equalsIgnoreCase(person.getEmail());
                    if (phoneChanged || emailChanged) {
                        if (!identityVerified || (customer.getCheckInPhoto() == null && photoPath == null)) {
                            conn.rollback();
                            return ServiceResult.fail("Cần xác minh khách tại quầy bằng ảnh check-in; nếu chưa có ảnh, kiểm tra giấy tờ và bổ sung ảnh.");
                        }
                        Person phoneOwner = persons.findByPhone(phone);
                        Person emailOwner = persons.findByEmail(email);
                        if (phoneOwner != null && phoneOwner.getPersonId() != customer.getPersonId()) {
                            conn.rollback();
                            return ServiceResult.failField("phone", "Số điện thoại đã được sử dụng.");
                        }
                        if (emailOwner != null && emailOwner.getPersonId() != customer.getPersonId()) {
                            conn.rollback();
                            return ServiceResult.failField("email", "Email đã được sử dụng.");
                        }
                        persons.updateCustomerContacts(conn, customer.getPersonId(), phone, email);
                        if (emailChanged) {
                            affectedAccount = new AccountDAO().findByPersonId(conn, customer.getPersonId());
                        }
                    }
                }
                if ("Dependent".equals(customer.getCustomerType())) {
                    if (input.getGuardianName() == null || input.getGuardianName().length() < 2 || input.getGuardianName().length() > 50
                            || input.getGuardianRelationship() == null || input.getGuardianRelationship().isBlank()
                            || input.getGuardianRelationship().length() > 30 || !ValidationUtil.isValidPhone(input.getGuardianPhone())
                            || (guardianPhone2 != null && !guardianPhone2.isBlank()
                            && (!ValidationUtil.isValidPhone(guardianPhone2) || guardianPhone2.equals(input.getGuardianPhone())))) {
                        conn.rollback();
                        return ServiceResult.fail("Thông tin giám hộ chưa hợp lệ; số phụ phải khác số chính.");
                    }
                    customer.setGuardianName(input.getGuardianName());
                    customer.setGuardianRelationship(input.getGuardianRelationship());
                    customer.setGuardianPhone(input.getGuardianPhone());
                    customer.setGuardianPhone2(guardianPhone2);
                }
                // B3: lưu tên/ngày sinh, giám hộ và ảnh trong cùng transaction.
                new PersonDAO().updateCustomerIdentity(conn, customer.getPersonId(), input.getFullName(), input.getDateOfBirth());
                if (photoPath != null) customer.setCheckInPhoto(photoPath);
                customers.updateProfile(conn, customer);
                // B4: ghi nhật ký cùng transaction; chỉ commit khi tất cả thành công.
                new AuditLogService().logInTransaction(conn, accountId, Role.RECEPTIONIST,
                        "Cập nhật hồ sơ hội viên", "Customers", customerId, "Profile", oldProfile, profileSnapshot(person, input, phone, email), input.getFullName());
                new AuditLogService().logInTransaction(conn, accountId, Role.RECEPTIONIST,
                        "Cập nhật ảnh và giám hộ", "Customers", customerId, "Profile",
                        oldGuardian, customerSnapshot(customer), null);
                conn.commit();
                // B5: chỉ đăng xuất và gửi thông báo sau khi lưu thành công.
                String message = "Đã cập nhật hồ sơ hội viên.";
                if (emailChanged) {
                    if (affectedAccount != null) {
                        com.gms.listener.SessionRegistryListener.invalidateSessionsOfAccount(affectedAccount.getAccountId(), null);
                    }
                    if (ValidationUtil.isValidEmail(oldEmail)) {
                        try {
                            new com.gms.integration.EmailGateway().sendNotice(oldEmail, "Email đăng nhập đã được thay đổi",
                                    "Lễ tân đã cập nhật email đăng nhập Super Gym tại quầy. Nếu không phải bạn yêu cầu, hãy liên hệ phòng tập.");
                        } catch (com.gms.integration.EmailSendException e) {
                            message += " Chưa gửi được thông báo đến email cũ.";
                        }
                    }
                }
                return ServiceResult.ok(null, message);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            return ServiceResult.fail("Không thể lưu hồ sơ. Vui lòng thử lại.");
        }
    }
    /** Chỉ ghi các trường hồ sơ vừa lưu, không có thông tin mật khẩu. */
    private String profileSnapshot(Person person, CounterCustomerCreateInput input, String phone, String email) {
        person.setFullName(input.getFullName());
        person.setDateOfBirth(input.getDateOfBirth());
        person.setPhone(phone);
        person.setEmail(email);
        return personSnapshot(person);
    }

    private String personSnapshot(Person person) {
        return new com.google.gson.Gson().toJson(java.util.Arrays.asList(person.getFullName(),
                String.valueOf(person.getDateOfBirth()), person.getPhone(), person.getEmail()));
    }

    private String customerSnapshot(Customer customer) {
        return new com.google.gson.Gson().toJson(java.util.Arrays.asList(customer.getGuardianName(),
                customer.getGuardianRelationship(), customer.getGuardianPhone(), customer.getGuardianPhone2(), customer.getCheckInPhoto()));
    }

}
