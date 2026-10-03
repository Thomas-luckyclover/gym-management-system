package com.gms.listener;

import com.gms.constant.SessionKey;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionAttributeListener;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Theo dõi các phiên đăng nhập đang mở theo từng tài khoản, để Service có thể "đăng xuất mọi phiên khác"
 * khi đổi mật khẩu, đổi email, admin khoá tài khoản, hoặc chính người dùng bấm "đăng xuất khỏi thiết bị khác".
 * Dữ liệu nằm trong RAM — restart Tomcat thì mất theo.
 */
@WebListener
public class SessionRegistryListener implements HttpSessionListener, HttpSessionAttributeListener {

    /** accountId → tập session đang mở của tài khoản đó. */
    private static final Map<Integer, Set<HttpSession>> SESSIONS_BY_ACCOUNT = new ConcurrentHashMap<>();

    // ===== Tomcat tự gọi các method dưới khi session thay đổi =====

    @Override
    public void attributeAdded(HttpSessionBindingEvent event) {
        boolean isAccountKey = SessionKey.ACCOUNT_ID.equals(event.getName());
        if (isAccountKey) {
            Integer accountId = (Integer) event.getValue();
            registerSession(accountId, event.getSession());
        }
    }

    @Override
    public void attributeReplaced(HttpSessionBindingEvent event) {
        boolean isAccountKey = SessionKey.ACCOUNT_ID.equals(event.getName());
        if (isAccountKey) {
            Integer oldAccountId = (Integer) event.getValue();
            unregisterSession(oldAccountId, event.getSession());
            Integer newAccountId = (Integer) event.getSession().getAttribute(SessionKey.ACCOUNT_ID);
            registerSession(newAccountId, event.getSession());
        }
    }

    @Override
    public void attributeRemoved(HttpSessionBindingEvent event) {
        boolean isAccountKey = SessionKey.ACCOUNT_ID.equals(event.getName());
        if (isAccountKey) {
            Integer accountId = (Integer) event.getValue();
            unregisterSession(accountId, event.getSession());
        }
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent event) {
        HttpSession session = event.getSession();
        for (Set<HttpSession> sessions : SESSIONS_BY_ACCOUNT.values()) {
            sessions.remove(session);
        }
    }

    // ===== Method cho Service gọi =====

    /**
     * Huỷ mọi phiên đang mở của 1 tài khoản, có thể chừa lại phiên hiện tại.
     * Truyền keepSessionId = null để huỷ tất cả.
     */
    public static int invalidateSessionsOfAccount(int accountId, String keepSessionId) {
        Set<HttpSession> sessions = SESSIONS_BY_ACCOUNT.get(accountId);
        if (sessions == null) {
            return 0;
        }

        // B1: chép ra danh sách riêng, vì invalidate() sẽ kích hoạt sessionDestroyed() sửa tập gốc
        List<HttpSession> sessionsToCheck = new ArrayList<>(sessions);
        int invalidatedCount = 0;

        // B2: hủy từng phiên, bỏ qua phiên cần giữ
        for (HttpSession session : sessionsToCheck) {
            boolean isKeptSession = session.getId().equals(keepSessionId);
            if (isKeptSession) {
                continue;
            }
            try {
                session.invalidate();
                invalidatedCount++;
            } catch (IllegalStateException e) {
                // Phiên đã tự hết hạn trước đó — chỉ cần bỏ khỏi danh sách
                sessions.remove(session);
            }
        }
        return invalidatedCount;
    }

    // ===== Xử lý nội bộ =====

    private static void registerSession(Integer accountId, HttpSession session) {
        if (accountId == null) {
            return;
        }
        Set<HttpSession> sessions = SESSIONS_BY_ACCOUNT.computeIfAbsent(accountId, key -> ConcurrentHashMap.newKeySet());
        sessions.add(session);
    }

    private static void unregisterSession(Integer accountId, HttpSession session) {
        if (accountId == null) {
            return;
        }
        Set<HttpSession> sessions = SESSIONS_BY_ACCOUNT.get(accountId);
        if (sessions != null) {
            sessions.remove(session);
        }
    }
}
