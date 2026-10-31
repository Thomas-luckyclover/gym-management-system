package com.gms.servlet.auth;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dto.JsonResponse;
import com.gms.dto.ServiceResult;
import com.gms.service.NotificationService;
import com.gms.util.JsonUtil;
import com.gms.util.RequestUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;

/**
 * API cho chuông thông báo trên topbar.
 */
@WebServlet("/api/notifications")
public class NotificationApiServlet
        extends HttpServlet {

    private static final String ACTION_READ =
            "read";

    private static final String ACTION_READ_ALL =
            "readAll";

    private final NotificationService
            notificationService =
            new NotificationService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        SessionContext context =
                getSessionContext(request);

        if (context == null) {

            JsonUtil.writeJson(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    JsonResponse.error(
                            Messages.MSG06_SESSION_EXPIRED
                    )
            );

            return;
        }

        ServiceResult<Map<String, Object>>
                result =
                notificationService.getBellData(
                        context.accountId,
                        context.role
                );

        JsonUtil.writeJson(
                response,
                JsonResponse.from(result)
        );
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        SessionContext context =
                getSessionContext(request);

        if (context == null) {

            JsonUtil.writeJson(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    JsonResponse.error(
                            Messages.MSG06_SESSION_EXPIRED
                    )
            );

            return;
        }

        String action =
                RequestUtil.getTrimmedParam(
                        request,
                        "action"
                );

        ServiceResult<Void> result;

        if (ACTION_READ.equals(action)) {

            int notificationId =
                    RequestUtil.getIntParam(
                            request,
                            "notificationId",
                            0
                    );

            result =
                    notificationService.markRead(
                            context.accountId,
                            notificationId
                    );

        } else if (
                ACTION_READ_ALL.equals(action)
        ) {

            result =
                    notificationService.markAllRead(
                            context.accountId
                    );

        } else {

            result =
                    ServiceResult.fail(
                            Messages.MSG303_NOTIFICATION_ACTION_INVALID
                    );
        }

        JsonUtil.writeJson(
                response,
                JsonResponse.from(result)
        );
    }

    private SessionContext getSessionContext(
            HttpServletRequest request
    ) {

        HttpSession session =
                request.getSession(false);

        if (session == null) {
            return null;
        }

        Integer accountId =
                (Integer) session.getAttribute(
                        SessionKey.ACCOUNT_ID
                );

        String roleValue =
                (String) session.getAttribute(
                        SessionKey.ROLE
                );

        Role role =
                Role.fromDbValue(roleValue);

        if (accountId == null
                || role == null) {
            return null;
        }

        return new SessionContext(
                accountId,
                role
        );
    }

    private static class SessionContext {

        private final int accountId;
        private final Role role;

        private SessionContext(
                int accountId,
                Role role
        ) {
            this.accountId = accountId;
            this.role = role;
        }
    }
}