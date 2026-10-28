package com.gms.service;

import com.gms.constant.Role;
import com.gms.constant.SessionKey;
import com.gms.dao.UserBadgeDAO;
import jakarta.servlet.http.HttpSession;
import java.sql.SQLException;

/**
 * THẺ TÀI KHOẢN góc phải mọi màn: "LT  Lê Thị Thanh Tâm / LT-0003 · Lễ tân".
 *
 *   Ai gọi?  AuthFilter, mỗi request đã đăng nhập + đã chọn vai trò → ensure(session).
 *            Chỉ đọc DB khi vai trò trong session khác vai trò đã tính (đăng nhập, chọn / đổi vai trò).
 *   Ra:      3 attribute session (SessionKey.USER_CODE, USER_ROLE_LABEL, USER_INITIALS) → common/_user-badge.jspf.
 *   Mã gốc trong DB GIỮ NGUYÊN (NV-0005, KH-1006); chỉ đổi cách hiện: tiền tố theo vai trò + phần số của mã gốc.
 */
public class UserBadgeService {

    private final UserBadgeDAO badgeDAO = new UserBadgeDAO();

    /** HÀM CHÍNH — AuthFilter gọi. Lỗi DB không chặn trang: thẻ chỉ hiện tên + vai trò (thử lại ở request sau). */
    public void ensure(HttpSession session) {
        Object accountId = session.getAttribute(SessionKey.ACCOUNT_ID);
        String role = (String) session.getAttribute(SessionKey.ROLE);
        if (!(accountId instanceof Integer) || role == null || role.equals(session.getAttribute(SessionKey.USER_BADGE_ROLE))) {
            return;
        }
        session.setAttribute(SessionKey.USER_ROLE_LABEL, roleLabel(role));
        session.setAttribute(SessionKey.USER_INITIALS, initials((String) session.getAttribute(SessionKey.DISPLAY_NAME)));
        try {
            session.setAttribute(SessionKey.USER_CODE, displayCode(role, badgeDAO.findCode((Integer) accountId, role)));
            session.setAttribute(SessionKey.USER_BADGE_ROLE, role);
        } catch (SQLException exception) {
            exception.printStackTrace();
            session.removeAttribute(SessionKey.USER_CODE);
        }
    }

    /**
     * Mã hiển thị = tiền tố vai trò + phần số của mã gốc.
     *   ("PT", "NV-0005") → "PT-0005" · ("Receptionist", "NV-0003") → "LT-0003" · ("Manager", "NV-0002") → "QL-0002"
     *   ("Admin", "NV-0001") → "QT-0001" · ("Customer", "KH-1006") → "KH-1006" · mã gốc null → null.
     */
    static String displayCode(String role, String dbCode) {
        if (dbCode == null || dbCode.isBlank()) {
            return null;
        }
        String number = dbCode.contains("-") ? dbCode.substring(dbCode.indexOf('-') + 1) : dbCode;
        String prefix = switch (role) {
            case "Customer" -> "KH";
            case "PT" -> "PT";
            case "Receptionist" -> "LT";
            case "Manager" -> "QL";
            case "Admin" -> "QT";
            default -> null;
        };
        return prefix == null ? dbCode : prefix + "-" + number;
    }

    /** "Receptionist" → "Lễ tân" (Role.getDisplayName); vai trò lạ → giữ nguyên chữ. */
    static String roleLabel(String role) {
        Role found = Role.fromDbValue(role);
        return found == null ? role : found.getDisplayName();
    }

    /** "Lê Thị Thanh Tâm" → "LT" (chữ đầu họ + chữ đầu tên); 1 chữ → 1 ký tự; trống → "?". */
    static String initials(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        return (parts.length == 1 ? first : first + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
