package com.gms.servlet.auth;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.AccountSettingsView;
import com.gms.dto.ServiceResult;
import com.gms.service.AccountSettingsService;
import com.gms.util.RequestUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.Part;

import java.io.InputStream;
import java.nio.file.Path;
import java.io.IOException;

/**
 * Controller cho Account Settings dùng chung mọi vai trò.
 */
@WebServlet("/account")
@MultipartConfig
public class AccountSettingsServlet extends HttpServlet {

    private static final String VIEW_NAME =
            "auth/account-settings";

    private static final String ACTION_CHANGE_PASSWORD =
            "changePassword";

    private static final String TAB_PROFILE =
            "profile";

    private static final String TAB_PASSWORD =
            "password";

    private static final String TAB_DEPENDENTS =
            "dependents";
    private static final String ACTION_UPDATE_AVATAR =
            "updateAvatar";
    private final AccountSettingsService accountSettingsService =
            new AccountSettingsService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        renderAccountSettings(
                request,
                response
        );
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        String action =
                RequestUtil.getTrimmedParam(
                        request,
                        "action"
                );

        if (ACTION_CHANGE_PASSWORD.equals(action)) {

            handleChangePassword(
                    request,
                    response
            );

            return;
        }
        if (ACTION_UPDATE_AVATAR.equals(action)) {

            handleUpdateAvatar(
                    request,
                    response
            );

            return;
        }

        RequestUtil.setFlashToast(
                request,
                "error",
                Messages.MSG04_SYSTEM_BUSY
        );

        RequestUtil.redirect(
                request,
                response,
                "/account"
        );
    }

    /**
     * Xử lý đổi mật khẩu.
     *
     * @param request  request hiện tại
     * @param response response hiện tại
     */
    private void handleChangePassword(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        HttpSession session =
                request.getSession(false);

        if (session == null) {

            RequestUtil.redirect(
                    request,
                    response,
                    "/login"
            );

            return;
        }

        Integer accountId =
                (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID
                );

        if (accountId == null) {

            RequestUtil.redirect(
                    request,
                    response,
                    "/login"
            );

            return;
        }

        String currentPassword =
                request.getParameter(
                        "currentPassword"
                );

        String newPassword =
                request.getParameter(
                        "newPassword"
                );

        String confirmPassword =
                request.getParameter(
                        "confirmPassword"
                );

        ServiceResult<Void> result =
                accountSettingsService.changePassword(
                        accountId,
                        currentPassword,
                        newPassword,
                        confirmPassword,
                        session.getId()
                );

        if (!result.isSuccess()) {

            request.setAttribute(
                    "errors",
                    result.getFieldErrors()
            );

            request.setAttribute(
                    "formMessage",
                    result.getMessage()
            );

            request.setAttribute(
                    "activeTab",
                    TAB_PASSWORD
            );

            renderAccountSettings(
                    request,
                    response
            );

            return;
        }

        RequestUtil.setFlashToast(
                request,
                "success",
                Messages.MSG49_PASSWORD_CHANGED
        );

        RequestUtil.redirect(
                request,
                response,
                "/account?tab=password"
        );
    }
    /**
     * Xử lý upload và cập nhật avatar.
     *
     * @param request request hiện tại
     * @param response response hiện tại
     */
    private void handleUpdateAvatar(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        HttpSession session =
                request.getSession(false);

        if (session == null) {

            RequestUtil.redirect(
                    request,
                    response,
                    "/login"
            );

            return;
        }

        Integer accountId =
                (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID
                );

        if (accountId == null) {

            RequestUtil.redirect(
                    request,
                    response,
                    "/login"
            );

            return;
        }

        Part avatarPart =
                request.getPart(
                        "avatarFile"
                );

        if (avatarPart == null
                || avatarPart.getSize() == 0) {

            request.setAttribute(
                    "avatarError",
                    String.format(
                            Messages.MSG09_REQUIRED,
                            "Ảnh đại diện"
                    )
            );

            request.setAttribute(
                    "activeTab",
                    TAB_PROFILE
            );

            renderAccountSettings(
                    request,
                    response
            );

            return;
        }

        String uploadDirectoryValue =
                getServletContext()
                        .getRealPath(
                                "/uploads/avatar"
                        );

        if (uploadDirectoryValue == null) {

            request.setAttribute(
                    "avatarError",
                    Messages.MSG04_SYSTEM_BUSY
            );

            request.setAttribute(
                    "activeTab",
                    TAB_PROFILE
            );

            renderAccountSettings(
                    request,
                    response
            );

            return;
        }

        Path uploadDirectory =
                Path.of(
                        uploadDirectoryValue
                );

        ServiceResult<Void> result;

        try (InputStream inputStream =
                     avatarPart.getInputStream()) {

            result =
                    accountSettingsService.updateAvatar(
                            accountId,
                            avatarPart.getContentType(),
                            avatarPart.getSize(),
                            inputStream,
                            uploadDirectory
                    );
        }

        if (!result.isSuccess()) {

            request.setAttribute(
                    "avatarError",
                    result.getMessage()
            );

            if (result.getFieldErrors() != null
                    && result.getFieldErrors()
                    .get("avatarFile") != null) {

                request.setAttribute(
                        "avatarError",
                        result.getFieldErrors()
                                .get("avatarFile")
                );
            }

            request.setAttribute(
                    "activeTab",
                    TAB_PROFILE
            );

            renderAccountSettings(
                    request,
                    response
            );

            return;
        }

        RequestUtil.setFlashToast(
                request,
                "success",
                Messages.MSG58_AVATAR_CHANGED
        );

        RequestUtil.redirect(
                request,
                response,
                "/account?tab=profile"
        );
    }
    /**
     * Tải dữ liệu và hiển thị Account Settings.
     *
     * @param request  request hiện tại
     * @param response response hiện tại
     */
    private void renderAccountSettings(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws ServletException, IOException {

        HttpSession session =
                request.getSession(false);

        if (session == null) {

            RequestUtil.redirect(
                    request,
                    response,
                    "/login"
            );

            return;
        }

        Integer accountId =
                (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID
                );

        String roleValue =
                (String) session.getAttribute(
                        SessionKey.ROLE
                );

        if (accountId == null
                || roleValue == null) {

            RequestUtil.redirect(
                    request,
                    response,
                    "/login"
            );

            return;
        }

        Role role =
                Role.fromDbValue(roleValue);

        if (role == null) {

            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    Messages.MSG07_NO_PERMISSION
            );

            return;
        }

        configureRoleView(
                request,
                role
        );

        String activeTab =
                resolveActiveTab(
                        request,
                        role
                );

        request.setAttribute(
                "activeTab",
                activeTab
        );

        ServiceResult<AccountSettingsView> result =
                accountSettingsService
                        .getAccountSettings(
                                accountId,
                                role
                        );

        if (!result.isSuccess()) {

            request.setAttribute(
                    "pageError",
                    result.getMessage()
            );

            RequestUtil.forward(
                    request,
                    response,
                    VIEW_NAME
            );

            return;
        }

        request.setAttribute(
                "accountSettings",
                result.getData()
        );

        request.setAttribute(
                "currentRole",
                role
        );

        RequestUtil.forward(
                request,
                response,
                VIEW_NAME
        );
    }

    /**
     * Chọn layout, profile và theme tương ứng với vai trò.
     *
     * @param request request hiện tại
     * @param role    vai trò hiện tại
     */
    private void configureRoleView(
            HttpServletRequest request,
            Role role
    ) {

        String layoutFragment;
        String profileFragment;
        String themeClass;

        switch (role) {

            case ADMIN:

                layoutFragment =
                        "/WEB-INF/jsp/common/layout-admin.jspf";

                profileFragment =
                        "/WEB-INF/jsp/auth/account/profile-admin.jspf";

                themeClass =
                        "theme-admin";

                break;

            case MANAGER:

                layoutFragment =
                        "/WEB-INF/jsp/common/layout-manager.jspf";

                profileFragment =
                        "/WEB-INF/jsp/auth/account/profile-manager.jspf";

                themeClass =
                        "theme-manager";

                break;

            case PT:

                layoutFragment =
                        "/WEB-INF/jsp/common/layout-pt.jspf";

                profileFragment =
                        "/WEB-INF/jsp/auth/account/profile-pt.jspf";

                themeClass =
                        "theme-pt";

                break;

            case RECEPTIONIST:

                layoutFragment =
                        "/WEB-INF/jsp/common/layout-receptionist.jspf";

                profileFragment =
                        "/WEB-INF/jsp/auth/account/profile-receptionist.jspf";

                themeClass =
                        "theme-receptionist";

                break;

            case CUSTOMER:

                layoutFragment =
                        "/WEB-INF/jsp/common/layout-customer.jspf";

                profileFragment =
                        "/WEB-INF/jsp/auth/account/profile-customer.jspf";

                themeClass =
                        "theme-customer";

                break;

            default:

                throw new IllegalStateException(
                        "Vai trò Account Settings không hợp lệ."
                );
        }

        request.setAttribute(
                "layoutFragment",
                layoutFragment
        );

        request.setAttribute(
                "profileFragment",
                profileFragment
        );

        request.setAttribute(
                "themeClass",
                themeClass
        );

        request.setAttribute(
                "hasDependentsTab",
                role == Role.CUSTOMER
        );
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
            Role role
    ) {

        Object forcedTab =
                request.getAttribute(
                        "activeTab"
                );

        if (forcedTab instanceof String) {

            return (String) forcedTab;
        }

        String requestedTab =
                RequestUtil.getTrimmedParam(
                        request,
                        "tab"
                );

        if (TAB_PASSWORD.equals(
                requestedTab
        )) {

            return TAB_PASSWORD;
        }

        if (TAB_DEPENDENTS.equals(
                requestedTab
        )
                && role == Role.CUSTOMER) {

            return TAB_DEPENDENTS;
        }

        return TAB_PROFILE;
    }
}