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
 * Theo dõi các phiên đăng nhập đang mở của từng tài khoản, để đăng xuất "mọi phiên khác" khi:
 * - đổi/khôi phục mật khẩu (UC-03, MSG49),
 * - đổi email đăng nhập (UC-04, MSG56),
 * - người dùng bấm "Đăng xuất khỏi thiết bị khác" (MSG59),
 * - Admin khóa/ngừng hoạt động tài khoản (UC-07).
 *
 * Cách hoạt động: Tomcat tự gọi listener này mỗi khi session gắn/bỏ attribute accountId hoặc bị hủy.
 * Dữ liệu nằm trong RAM — Tomcat khởi động lại thì mọi session cũng mất theo, nên không cần lưu DB
 * (không phải sửa database — agents.md Phần 5).
 *
 * Cách dùng trong Service:
 *   - Giữ lại phiên hiện tại:  SessionRegistryListener.invalidateSessionsOfAccount(accountId, currentSessionId);
 *   - Đăng xuất tất cả:         SessionRegistryListener.invalidateSessionsOfAccount(accountId, null);
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
     * Hủy mọi phiên đang mở của 1 tài khoản, có thể chừa lại phiên hiện tại.
     * @param accountId id tài khoản
     * @param keepSessionId id phiên muốn giữ lại (session.getId()), truyền null để hủy tất cả
     * @return số phiên đã hủy
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
