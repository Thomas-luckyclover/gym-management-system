package com.gms.service;

import com.gms.constant.Messages;
import com.gms.dao.PersonDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.Person;
import com.gms.entity.VerificationCode;
import com.gms.integration.EmailGateway;
import com.gms.integration.EmailSendException;
import com.gms.listener.SessionRegistryListener;
import com.gms.util.ValidationUtil;

import java.sql.SQLException;

/**
 * Xử lý nghiệp vụ thay đổi thông tin liên hệ (Email, Số điện thoại) cho người dùng
 * thuộc tất cả các vai trò trong hệ thống.
 */
public class ContactChangeService {

    /**
     * Loại thông tin liên hệ đang được xác thực để đổi.
     */
    public enum ContactField {
        EMAIL, PHONE
    }

    private final PersonDAO personDAO = new PersonDAO();
    private final VerificationCodeService verificationCodeService = new VerificationCodeService();
    private final EmailGateway emailGateway = new EmailGateway();

    // ===== 1. Yêu cầu đổi thông tin (Gửi mã OTP) =====

    /**
     * Yêu cầu đổi email: kiểm tra tính hợp lệ, trùng lặp và gửi mã OTP qua địa chỉ email mới.
     *
     * @param accountId mã tài khoản
     * @param newEmail  địa chỉ email mới cần đổi
     * @return ServiceResult chứa kết quả gửi mã OTP (MSG55 khi thành công)
     */
    public ServiceResult<Void> requestEmailChange(int accountId, String newEmail) {
        // B1: validate dữ liệu đầu vào
        if (ValidationUtil.isNullOrEmpty(newEmail)) {
            return ServiceResult.failField("email", String.format(Messages.MSG09_REQUIRED, "Email"));
        }
        if (!ValidationUtil.isValidEmail(newEmail)) {
            return ServiceResult.failField("email", Messages.MSG11_INVALID_EMAIL);
        }
        String trimmedEmail = newEmail.trim();

        try {
            // B2: kiểm tra trùng lặp email với người khác (DBR-23)
            if (personDAO.existsByEmail(trimmedEmail)) {
                return ServiceResult.failField("email", Messages.MSG12_EMAIL_USED);
            }

            // B3: gửi mã xác thực tới email mới
            ServiceResult<Void> sendResult = verificationCodeService.sendCode(
                    trimmedEmail, VerificationCode.Purpose.EMAIL_CHANGE, accountId);

            if (sendResult.isSuccess()) {
                return ServiceResult.ok(null, Messages.MSG55_EMAIL_CHANGE_CODE_SENT);
            }
            return sendResult;

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    /**
     * Yêu cầu đổi số điện thoại: kiểm tra tính hợp lệ, trùng lặp và gửi mã OTP qua email hiện tại.
     *
     * @param accountId    mã tài khoản
     * @param currentEmail email hiện tại của tài khoản để nhận OTP
     * @param newPhone     số điện thoại mới cần đổi
     * @return ServiceResult chứa kết quả gửi mã OTP
     */
    public ServiceResult<Void> requestPhoneChange(int accountId, String currentEmail, String newPhone) {
        // B1: validate dữ liệu đầu vào
        if (ValidationUtil.isNullOrEmpty(newPhone)) {
            return ServiceResult.failField("phone", String.format(Messages.MSG09_REQUIRED, "Số điện thoại"));
        }
        if (!ValidationUtil.isValidPhone(newPhone)) {
            return ServiceResult.failField("phone", Messages.MSG13_INVALID_PHONE);
        }
        if (ValidationUtil.isNullOrEmpty(currentEmail)) {
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
        String trimmedPhone = newPhone.trim();

        try {
            // B2: kiểm tra trùng lặp số điện thoại với người khác (DBR-22)
            if (personDAO.existsByPhone(trimmedPhone)) {
                return ServiceResult.failField("phone", Messages.MSG14_PHONE_USED);
            }

            // B3: gửi mã xác thực tới email hiện tại
            return verificationCodeService.sendCode(
                    currentEmail.trim(), VerificationCode.Purpose.EMAIL_CHANGE, accountId);

        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 2. Xác thực và cập nhật =====

    /**
     * Xác thực mã OTP và áp dụng cập nhật email hoặc số điện thoại mới vào hệ thống.
     *
     * @param accountId         mã tài khoản
     * @param personId          mã hồ sơ người dùng
     * @param field             loại thông tin cần đổi (EMAIL hoặc PHONE)
     * @param verificationEmail email nhận mã OTP để đối chiếu
     * @param newValue          giá trị mới (email mới hoặc số điện thoại mới)
     * @param code              mã xác thực người dùng nhập
     * @param currentSessionId  session ID hiện tại (dùng để giữ phiên khi đổi email)
     * @return ServiceResult chứa kết quả thành công hoặc thông báo lỗi
     */
    public ServiceResult<Void> verifyAndApply(int accountId, int personId, ContactField field,
            String verificationEmail, String newValue, String code, String currentSessionId) {

        // B1: xác thực mã OTP qua VerificationCodeService
        ServiceResult<VerificationCode> verifyResult = verificationCodeService.verifyCode(
                verificationEmail, VerificationCode.Purpose.EMAIL_CHANGE, code);

        if (!verifyResult.isSuccess()) {
            return verifyResult.hasFieldErrors()
                    ? ServiceResult.failFields(verifyResult.getFieldErrors())
                    : ServiceResult.fail(verifyResult.getMessage());
        }

        // B2: mã hợp lệ → kiểm tra trùng LẦN 2 ngay trước khi lưu (CBR-110): trong 15 phút chờ mã,
        //     người khác có thể đã lấy email / SĐT này. Không kiểm tra thì vỡ unique index → "Hệ thống bận".
        try {
            if (field == ContactField.EMAIL && personDAO.existsByEmail(newValue)) {
                return ServiceResult.failField("email", Messages.MSG12_EMAIL_USED);
            }
            if (field == ContactField.PHONE && personDAO.existsByPhone(newValue)) {
                return ServiceResult.failField("phone", Messages.MSG14_PHONE_USED);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }

        // B3: tiến hành cập nhật vào database (email/SĐT nằm trên Person → áp cho mọi vai trò, DBR-65)
        try {
            if (field == ContactField.EMAIL) {
                return applyEmailChange(accountId, personId, newValue, currentSessionId);
            } else {
                return applyPhoneChange(personId, newValue);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY);
        }
    }

    // ===== 3. Hàm phụ cập nhật dữ liệu =====

    /**
     * Cập nhật email mới, hủy các phiên đăng nhập trên thiết bị khác và gửi thông báo cảnh báo tới email cũ.
     *
     * @param accountId        mã tài khoản
     * @param personId         mã hồ sơ
     * @param newEmail         địa chỉ email mới
     * @param currentSessionId session ID hiện tại
     * @return ServiceResult thông báo thành công (MSG56)
     * @throws SQLException khi truy vấn database gặp lỗi
     */
    private ServiceResult<Void> applyEmailChange(int accountId, int personId, String newEmail,
            String currentSessionId) throws SQLException {

        // B1: lấy email cũ trước khi ghi đè để gửi thông báo cảnh báo
        Person person = personDAO.findById(personId);
        String oldEmail = person == null ? null : person.getEmail();

        // B2: cập nhật email trong database
        personDAO.updateEmail(personId, newEmail);

        // B3: đăng xuất các phiên đăng nhập khác của tài khoản này
        SessionRegistryListener.invalidateSessionsOfAccount(accountId, currentSessionId);

        // B4: gửi email cảnh báo tới email cũ (nếu có)
        if (!ValidationUtil.isNullOrEmpty(oldEmail)) {
            try {
                emailGateway.sendNotice(oldEmail, "Email đăng nhập đã được thay đổi",
                        "Email đăng nhập của tài khoản Super Gym vừa được đổi sang địa chỉ mới. "
                        + "Nếu không phải bạn thực hiện, hãy liên hệ quầy Lễ tân ngay.");
            } catch (EmailSendException e) {
                e.printStackTrace();
            }
        }

        return ServiceResult.ok(null, Messages.MSG56_EMAIL_CHANGED);
    }

    /**
     * Cập nhật số điện thoại mới vào Persons. Không đăng xuất các phiên khác theo quy định MSG57.
     *
     * @param personId mã hồ sơ
     * @param newPhone số điện thoại mới
     * @return ServiceResult thông báo thành công (MSG57)
     * @throws SQLException khi truy vấn database gặp lỗi
     */
    private ServiceResult<Void> applyPhoneChange(int personId, String newPhone) throws SQLException {
        personDAO.updatePhone(personId, newPhone);
        return ServiceResult.ok(null, Messages.MSG57_PHONE_CHANGED);
    }
}
