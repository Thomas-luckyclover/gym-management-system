package com.gms.servlet.auth;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.AccountSettingsView;
import com.gms.dto.DependentCustomerView;
import com.gms.dto.ServiceResult;
import com.gms.service.AccountSettingsService;
import com.gms.service.ContactChangeService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.Part;
import com.gms.service.AuditLogService;
import com.gms.service.ContactChangeService.ContactField;
import com.gms.dao.VerificationCodeDAO;
import com.gms.entity.VerificationCode;
import com.gms.util.DateTimeUtil;
import java.io.InputStream;
import java.nio.file.Path;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Servlet Cài đặt tài khoản dùng chung mọi vai trò (URL /account).
 *
 * <p>Cho phép đổi mật khẩu, cập nhật avatar, đổi email/SĐT (kèm xác thực OTP); tab khác nhau theo vai trò.</p>
 */
@WebServlet("/account")
@MultipartConfig
public class AccountSettingsServlet extends HttpServlet {

        private static final String VIEW_NAME = "auth/account-settings";

        private static final String ACTION_CHANGE_PASSWORD = "changePassword";

        private static final String TAB_PROFILE = "profile";

        private static final String TAB_PASSWORD = "password";

        private static final String TAB_DEPENDENTS = "dependents";
        private static final String ACTION_UPDATE_AVATAR = "updateAvatar";
        private static final String ACTION_REQUEST_EMAIL_CHANGE = "requestEmailChange";

        private static final String ACTION_REQUEST_PHONE_CHANGE = "requestPhoneChange";

        private static final String ACTION_VERIFY_CONTACT_CHANGE = "verifyContactChange";
        private final AccountSettingsService accountSettingsService = new AccountSettingsService();
        private final ContactChangeService contactChangeService = new ContactChangeService();

        private final AuditLogService auditLogService = new AuditLogService();
        private final VerificationCodeDAO verificationCodeDAO = new VerificationCodeDAO();

        /**
         * GET /account — hiển thị trang Account Settings ở tab hiện tại (profile / password / dependents).
         */
        @Override
        protected void doGet(
                HttpServletRequest request,
                HttpServletResponse response) throws ServletException, IOException {

                renderAccountSettings(
                        request,
                        response);
        }

        /**
         * POST /account — nhận form theo action: changePassword / updateAvatar / requestEmailChange /
         * requestPhoneChange / verifyContactChange; action lạ thì báo lỗi và redirect.
         */
        @Override
        protected void doPost(
                HttpServletRequest request,
                HttpServletResponse response) throws ServletException, IOException {

                String action = RequestUtil.getTrimmedParam(
                        request,
                        "action");

                if (ACTION_CHANGE_PASSWORD.equals(action)) {

                        handleChangePassword(
                                request,
                                response);

                        return;
                }
                if (ACTION_UPDATE_AVATAR.equals(action)) {

                        handleUpdateAvatar(
                                request,
                                response);

                        return;
                }
                if (ACTION_REQUEST_EMAIL_CHANGE.equals(action)) {

                        handleRequestEmailChange(
                                request,
                                response);

                        return;
                }

                if (ACTION_REQUEST_PHONE_CHANGE.equals(action)) {

                        handleRequestPhoneChange(
                                request,
                                response);

                        return;
                }

                if (ACTION_VERIFY_CONTACT_CHANGE.equals(action)) {

                        handleVerifyContactChange(
                                request,
                                response);

                        return;
                }
                RequestUtil.setFlashToast(
                        request,
                        "error",
                        Messages.MSG04_SYSTEM_BUSY);

                RequestUtil.redirect(
                        request,
                        response,
                        "/account");
        }

        /**
         * Gửi mã xác thực khi Admin yêu cầu đổi email.
         *
         * @param request  request hiện tại
         * @param response response hiện tại
         */
        private void handleRequestEmailChange(
                HttpServletRequest request,
                HttpServletResponse response) throws ServletException, IOException {

                HttpSession session = request.getSession(false);

                if (session == null) {
                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                Integer accountId = (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID);

                if (accountId == null) {
                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                String newEmail = RequestUtil.getTrimmedParam(
                        request,
                        "newEmail");

                request.setAttribute(
                        "newEmail",
                        newEmail);

                ServiceResult<Void> result = contactChangeService.requestEmailChange(
                        accountId,
                        newEmail);

                if (!result.isSuccess()) {

                        request.setAttribute(
                                "errors",
                                result.getFieldErrors());

                        request.setAttribute(
                                "formMessage",
                                result.getMessage());

                        request.setAttribute(
                                "activeTab",
                                TAB_PROFILE);

                        renderAccountSettings(
                                request,
                                response);

                        return;
                }

                rememberPendingContact(
                        session,
                        ContactField.EMAIL,
                        newEmail,
                        newEmail);
                prepareContactVerification(
                        request,
                        ContactField.EMAIL,
                        newEmail,
                        newEmail,
                        result.getMessage());

                renderAccountSettings(
                        request,
                        response);
        }

        /**
         * Gửi mã xác thực khi Admin yêu cầu đổi số điện thoại.
         *
         * @param request  request hiện tại
         * @param response response hiện tại
         */
        private void handleRequestPhoneChange(
                HttpServletRequest request,
                HttpServletResponse response) throws ServletException, IOException {

                HttpSession session = request.getSession(false);

                if (session == null) {
                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                Integer accountId = (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID);

                String roleValue = (String) session.getAttribute(
                        SessionKey.ROLE);

                if (accountId == null
                        || roleValue == null) {

                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                Role role = Role.fromDbValue(
                        roleValue);

                if (role == null) {
                        response.sendError(
                                HttpServletResponse.SC_FORBIDDEN,
                                Messages.MSG07_NO_PERMISSION);

                        return;
                }

                ServiceResult<AccountSettingsView> settingsResult = accountSettingsService.getAccountSettings(
                        accountId,
                        role);

                if (!settingsResult.isSuccess()) {

                        request.setAttribute(
                                "pageError",
                                settingsResult.getMessage());

                        renderAccountSettings(
                                request,
                                response);

                        return;
                }

                String newPhone = RequestUtil.getTrimmedParam(
                        request,
                        "newPhone");

                request.setAttribute(
                        "newPhone",
                        newPhone);

                AccountSettingsView settings = settingsResult.getData();

                ServiceResult<Void> result = contactChangeService.requestPhoneChange(
                        accountId,
                        settings.getEmail(),
                        newPhone);

                if (!result.isSuccess()) {

                        request.setAttribute(
                                "errors",
                                result.getFieldErrors());

                        request.setAttribute(
                                "formMessage",
                                result.getMessage());

                        request.setAttribute(
                                "activeTab",
                                TAB_PROFILE);

                        renderAccountSettings(
                                request,
                                response);

                        return;
                }

                rememberPendingContact(
                        session,
                        ContactField.PHONE,
                        settings.getEmail(),
                        newPhone);
                prepareContactVerification(
                        request,
                        ContactField.PHONE,
                        settings.getEmail(),
                        newPhone,
                        result.getMessage());

                renderAccountSettings(
                        request,
                        response);
        }

        /** Lưu thay đổi email/SĐT đang chờ mã ở phía server (CBR-110, GBR-13). */
        private void rememberPendingContact(
                HttpSession session,
                ContactField field,
                String verificationEmail,
                String newValue) {
                session.setAttribute(SessionKey.PENDING_CONTACT_FIELD, field);
                session.setAttribute(SessionKey.PENDING_CONTACT_VERIFY_EMAIL, verificationEmail);
                session.setAttribute(SessionKey.PENDING_CONTACT_VALUE, newValue);
        }

        private void clearPendingContact(HttpSession session) {
                session.removeAttribute(SessionKey.PENDING_CONTACT_FIELD);
                session.removeAttribute(SessionKey.PENDING_CONTACT_VERIFY_EMAIL);
                session.removeAttribute(SessionKey.PENDING_CONTACT_VALUE);
        }

        private void prepareContactVerification(
                HttpServletRequest request,
                ContactField field,
                String verificationEmail,
                String pendingContactValue,
                String message) {

                request.setAttribute(
                        "contactVerificationPending",
                        true);

                // Receptionist/Manager JSP dùng cờ requireVerification, Admin JSP dùng
                // contactVerificationPending
                request.setAttribute(
                        "requireVerification",
                        true);

                request.setAttribute(
                        "contactField",
                        field.name());

                request.setAttribute(
                        "verificationEmail",
                        verificationEmail);

                request.setAttribute(
                        "pendingContactValue",
                        pendingContactValue);

                request.setAttribute(
                        "verifyTarget",
                        verificationEmail);

                request.setAttribute(
                        "formMessage",
                        message);

                long codeSecondsLeft = 900;
                try {
                        VerificationCode latestCode = verificationCodeDAO.findLatestByEmailAndPurpose(
                                verificationEmail,
                                VerificationCode.Purpose.EMAIL_CHANGE);
                        if (latestCode != null && latestCode.getExpiresAt() != null) {
                                LocalDateTime now = DateTimeUtil.now();
                                codeSecondsLeft = Math.max(0, Duration.between(now, latestCode.getExpiresAt()).getSeconds());
                        }
                } catch (Exception ignored) {
                }

                request.setAttribute(
                        "codeSecondsLeft",
                        codeSecondsLeft);

                request.setAttribute(
                        "activeTab",
                        TAB_PROFILE);
        }

        /**
         * Xác thực OTP và áp dụng thay đổi Email/SĐT.
         *
         * @param request  request hiện tại
         * @param response response hiện tại
         */
        private void handleVerifyContactChange(
                HttpServletRequest request,
                HttpServletResponse response) throws ServletException, IOException {

                HttpSession session = request.getSession(false);

                if (session == null) {
                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                Integer accountId = (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID);

                String roleValue = (String) session.getAttribute(
                        SessionKey.ROLE);

                if (accountId == null
                        || roleValue == null) {

                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                Role role = Role.fromDbValue(
                        roleValue);

                if (role == null) {
                        response.sendError(
                                HttpServletResponse.SC_FORBIDDEN,
                                Messages.MSG07_NO_PERMISSION);

                        return;
                }

                // B1: lấy giá trị chờ đổi từ session, không lấy từ form (CBR-110, GBR-13)
                ContactField contactField = (ContactField) session.getAttribute(
                        SessionKey.PENDING_CONTACT_FIELD);
                String verificationEmail = (String) session.getAttribute(
                        SessionKey.PENDING_CONTACT_VERIFY_EMAIL);
                String pendingContactValue = (String) session.getAttribute(
                        SessionKey.PENDING_CONTACT_VALUE);
                if (contactField == null
                        || verificationEmail == null
                        || pendingContactValue == null) {
                        RequestUtil.setFlashToast(
                                request,
                                "error",
                                Messages.MSG04_SYSTEM_BUSY);
                        RequestUtil.redirect(
                                request,
                                response,
                                "/account?tab=profile");
                        return;
                }
                String verificationCode = RequestUtil.getTrimmedParam(
                        request,
                        "verificationCode");

                ServiceResult<AccountSettingsView> settingsResult = accountSettingsService.getAccountSettings(
                        accountId,
                        role);

                if (!settingsResult.isSuccess()) {

                        request.setAttribute(
                                "pageError",
                                settingsResult.getMessage());

                        renderAccountSettings(
                                request,
                                response);

                        return;
                }

                AccountSettingsView settings = settingsResult.getData();

                String oldValue = contactField == ContactField.EMAIL
                        ? settings.getEmail()
                        : settings.getPhone();

                ServiceResult<Void> result = contactChangeService.verifyAndApply(
                        accountId,
                        settings.getPersonId(),
                        contactField,
                        verificationEmail,
                        pendingContactValue,
                        verificationCode,
                        session.getId());

                if (!result.isSuccess()) {

                        request.setAttribute(
                                "errors",
                                result.getFieldErrors());

                        request.setAttribute(
                                "formMessage",
                                result.getMessage());

                        prepareContactVerification(
                                request,
                                contactField,
                                verificationEmail,
                                pendingContactValue,
                                result.getMessage());

                        renderAccountSettings(
                                request,
                                response);

                        return;
                }

                clearPendingContact(session);

                String action;
                String fieldChanged;

                if (contactField == ContactField.EMAIL) {

                        action = "Đổi email tài khoản cá nhân";

                        fieldChanged = "Email";

                } else {

                        action = "Đổi số điện thoại tài khoản cá nhân";

                        fieldChanged = "Phone";
                }

                ServiceResult<Void> auditResult = auditLogService.log(
                        accountId,
                        action,
                        "Person",
                        settings.getPersonId(),
                        fieldChanged,
                        oldValue,
                        pendingContactValue,
                        null);

                if (!auditResult.isSuccess()) {

                        RequestUtil.setFlashToast(
                                request,
                                "warning",
                                "Thông tin đã được cập nhật nhưng chưa ghi được Audit Log.");

                } else {

                        RequestUtil.setFlashToast(
                                request,
                                "success",
                                result.getMessage());
                }

                RequestUtil.redirect(
                        request,
                        response,
                        "/account?tab=profile");
        }

        /**
         * Xử lý đổi mật khẩu.
         *
         * @param request  request hiện tại
         * @param response response hiện tại
         */
        private void handleChangePassword(
                HttpServletRequest request,
                HttpServletResponse response) throws ServletException, IOException {

                HttpSession session = request.getSession(false);

                if (session == null) {

                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                Integer accountId = (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID);

                if (accountId == null) {

                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                // B0: Kiểm tra CSRF token trước khi xử lý đổi mật khẩu (TC-17A)
                if (!RequestUtil.isValidCsrfToken(request)) {

                        request.setAttribute(
                                "formMessage",
                                Messages.MSG07_NO_PERMISSION);

                        request.setAttribute(
                                "activeTab",
                                TAB_PASSWORD);

                        renderAccountSettings(
                                request,
                                response);

                        return;
                }

                String currentPassword = request.getParameter(
                        "currentPassword");

                String newPassword = request.getParameter(
                        "newPassword");

                String confirmPassword = request.getParameter(
                        "confirmPassword");

                String roleValue = (String) session.getAttribute(SessionKey.ROLE);
                Role actingRole = Role.fromDbValue(roleValue);

                if (actingRole == null) {
                        response.sendError(
                                HttpServletResponse.SC_FORBIDDEN,
                                Messages.MSG07_NO_PERMISSION);
                        return;
                }

                ServiceResult<Void> result = accountSettingsService.changePassword(
                        accountId,
                        currentPassword,
                        newPassword,
                        confirmPassword,
                        session.getId(),
                        actingRole);

                if (!result.isSuccess()) {

                        request.setAttribute(
                                "errors",
                                result.getFieldErrors());

                        request.setAttribute(
                                "formMessage",
                                result.getMessage());

                        request.setAttribute(
                                "activeTab",
                                TAB_PASSWORD);

                        renderAccountSettings(
                                request,
                                response);

                        return;
                }

                RequestUtil.setFlashToast(
                        request,
                        "success",
                        Messages.MSG49_PASSWORD_CHANGED);

                RequestUtil.redirect(
                        request,
                        response,
                        "/account?tab=password");
        }

        /**
         * Xử lý upload và cập nhật avatar.
         *
         * @param request  request hiện tại
         * @param response response hiện tại
         */
        private void handleUpdateAvatar(
                HttpServletRequest request,
                HttpServletResponse response) throws ServletException, IOException {
                HttpSession session = request.getSession(false);

                if (session == null) {

                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                Integer accountId = (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID);

                if (accountId == null) {

                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                Part avatarPart = request.getPart(
                        "avatarFile");

                if (avatarPart == null
                        || avatarPart.getSize() == 0) {

                        request.setAttribute(
                                "avatarError",
                                String.format(
                                        Messages.MSG09_REQUIRED,
                                        "Ảnh đại diện"));

                        request.setAttribute(
                                "activeTab",
                                TAB_PROFILE);

                        renderAccountSettings(
                                request,
                                response);

                        return;
                }

                String uploadDirectoryValue = getServletContext()
                        .getRealPath(
                                "/uploads/avatar");

                if (uploadDirectoryValue == null) {

                        request.setAttribute(
                                "avatarError",
                                Messages.MSG04_SYSTEM_BUSY);

                        request.setAttribute(
                                "activeTab",
                                TAB_PROFILE);

                        renderAccountSettings(
                                request,
                                response);

                        return;
                }

                Path uploadDirectory = Path.of(
                        uploadDirectoryValue);

                ServiceResult<Void> result;

                try (InputStream inputStream = avatarPart.getInputStream()) {

                        result = accountSettingsService.updateAvatar(
                                accountId,
                                avatarPart.getContentType(),
                                avatarPart.getSize(),
                                inputStream,
                                uploadDirectory);
                }

                if (!result.isSuccess()) {

                        request.setAttribute(
                                "avatarError",
                                result.getMessage());

                        if (result.getFieldErrors() != null
                                && result.getFieldErrors()
                                .get("avatarFile") != null) {

                                request.setAttribute(
                                        "avatarError",
                                        result.getFieldErrors()
                                                .get("avatarFile"));
                        }

                        request.setAttribute(
                                "activeTab",
                                TAB_PROFILE);

                        renderAccountSettings(
                                request,
                                response);

                        return;
                }

                RequestUtil.setFlashToast(
                        request,
                        "success",
                        Messages.MSG58_AVATAR_CHANGED);

                RequestUtil.redirect(
                        request,
                        response,
                        "/account?tab=profile");
        }

        /**
         * Tải dữ liệu và hiển thị Account Settings.
         *
         * @param request  request hiện tại
         * @param response response hiện tại
         */
        private void renderAccountSettings(
                HttpServletRequest request,
                HttpServletResponse response) throws ServletException, IOException {

                HttpSession session = request.getSession(false);

                if (session == null) {

                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                Integer accountId = (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID);

                String roleValue = (String) session.getAttribute(
                        SessionKey.ROLE);

                if (accountId == null
                        || roleValue == null) {

                        RequestUtil.redirect(
                                request,
                                response,
                                "/login");

                        return;
                }

                Role role = Role.fromDbValue(roleValue);

                if (role == null) {

                        response.sendError(
                                HttpServletResponse.SC_FORBIDDEN,
                                Messages.MSG07_NO_PERMISSION);

                        return;
                }

                // Đảm bảo session có CSRF token cho các form (TC-17A)
                RequestUtil.getOrCreateCsrfToken(
                        session);

                configureRoleView(
                        request,
                        role);

                String activeTab = resolveActiveTab(
                        request,
                        role);

                request.setAttribute(
                        "activeTab",
                        activeTab);

                ServiceResult<AccountSettingsView> result = accountSettingsService
                        .getAccountSettings(
                                accountId,
                                role);

                if (!result.isSuccess()) {

                        request.setAttribute(
                                "pageError",
                                result.getMessage());

                        RequestUtil.forward(
                                request,
                                response,
                                VIEW_NAME);

                        return;
                }

                request.setAttribute(
                        "accountSettings",
                        result.getData());

                // Tải danh sách Dependent khi tab là dependents
                if (TAB_DEPENDENTS.equals(activeTab)) {

                        ServiceResult<java.util.List<DependentCustomerView>>
                                depResult =
                                accountSettingsService
                                        .getDependents(
                                                accountId,
                                                role);

                        if (depResult.isSuccess()) {

                                request.setAttribute(
                                        "dependents",
                                        depResult.getData());
                        }
                }

                request.setAttribute(
                        "currentRole",
                        role);
                request.setAttribute(
                        "activeSidebar",
                        "account");
                RequestUtil.forward(
                        request,
                        response,
                        VIEW_NAME);
        }

        /**
         * Chọn layout, profile và theme tương ứng với vai trò.
         *
         * @param request request hiện tại
         * @param role    vai trò hiện tại
         */
        private void configureRoleView(
                HttpServletRequest request,
                Role role) {

                String layoutFragment;
                String profileFragment;
                String themeClass;

                switch (role) {

                        case ADMIN:

                                layoutFragment = "/WEB-INF/jsp/common/layout-admin.jspf";

                                profileFragment = "/WEB-INF/jsp/auth/account/profile-admin.jspf";

                                themeClass = "theme-admin";

                                break;

                        case MANAGER:

                                layoutFragment = "/WEB-INF/jsp/common/layout-manager.jspf";

                                profileFragment = "/WEB-INF/jsp/auth/account/profile-manager.jspf";

                                themeClass = "theme-manager";

                                break;

                        case PT:

                                layoutFragment = "/WEB-INF/jsp/common/layout-pt.jspf";

                                profileFragment = "/WEB-INF/jsp/auth/account/profile-pt.jspf";

                                themeClass = "theme-pt";

                                break;

                        case RECEPTIONIST:

                                layoutFragment = "/WEB-INF/jsp/common/layout-receptionist.jspf";

                                profileFragment = "/WEB-INF/jsp/auth/account/profile-receptionist.jspf";

                                themeClass = "theme-receptionist";

                                break;

                        case CUSTOMER:

                                layoutFragment = "/WEB-INF/jsp/common/layout-customer.jspf";

                                profileFragment = "/WEB-INF/jsp/auth/account/profile-customer.jspf";

                                themeClass = "theme-pt customer-dashboard-page account-page";

                                break;

                        default:

                                throw new IllegalStateException(
                                        "Vai trò Account Settings không hợp lệ.");
                }

                request.setAttribute(
                        "layoutFragment",
                        layoutFragment);

                request.setAttribute(
                        "profileFragment",
                        profileFragment);

                request.setAttribute(
                        "themeClass",
                        themeClass);

                request.setAttribute(
                        "hasDependentsTab",
                        role == Role.CUSTOMER);
        }

        /**
         * Xác định tab đang được mở.
         *
         * @param request request hiện tại
         * @param role    vai trò hiện tại
         * @return tên tab hợp lệ
         */
        private String resolveActiveTab(
                HttpServletRequest request,
                Role role) {

                Object forcedTab = request.getAttribute(
                        "activeTab");

                if (forcedTab instanceof String) {

                        return (String) forcedTab;
                }

                String requestedTab = RequestUtil.getTrimmedParam(
                        request,
                        "tab");

                if (TAB_PASSWORD.equals(
                        requestedTab)) {

                        return TAB_PASSWORD;
                }

                if (TAB_DEPENDENTS.equals(
                        requestedTab)
                        && role == Role.CUSTOMER) {

                        return TAB_DEPENDENTS;
                }

                return TAB_PROFILE;
        }
}