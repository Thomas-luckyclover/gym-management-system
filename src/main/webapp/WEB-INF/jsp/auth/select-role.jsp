<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  ============================================================================================
  MÀN CHỌN VAI TRÒ — /select-role  (auth/select-role.jsp)
  ============================================================================================
  AI MỞ MÀN NÀY
    SelectRoleServlet.doGet B6 forward sang đây — CHỈ khi tài khoản có từ 2 vai trò trở lên
    (1 vai trò thì doGet B4 redirect thẳng trang chủ, không tới JSP).

  VÀO MÀN BẰNG 3 CÁCH
    1. Vừa đăng nhập, có ≥ 2 vai trò  → LoginServlet chưa đặt session ROLE → redirect /select-role
    2. Chưa chọn vai trò mà gõ URL khác → AuthFilter B2b đẩy về /select-role
    3. Bấm "Đổi vai trò" trên menu (layout-*.jspf, chỉ hiện khi availableRoles có dấu phẩy)

  DỮ LIỆU SERVLET GỬI SANG (doGet B5: request.setAttribute) — KHÔNG có DTO riêng
    chooseRoleMessage  String      ← Messages.MSG74                              → câu dưới tiêu đề (khối 1)
    roles              List<Role>  ← AuthService.findActiveRoles → AccountDAO     → mỗi vai trò 1 nút (khối 2)
                                     Role là enum: r.dbValue ("PT", "Customer"…) · r.displayName ("Huấn luyện viên"…)
    currentRole        String      ← session ROLE (vừa đăng nhập = null)          → chữ "Đang dùng" (khối 2)
    returnUrl          String      ← ?returnUrl= (chỉ nhận URL nội bộ)            → ô ẩn, chọn xong quay về trang đó

  BỐ CỤC: Toast · Khối 1 Tiêu đề · Khối 2 Danh sách vai trò (mỗi nút = 1 form POST) · Khối 3 Đăng xuất
  Màn này KHÔNG có JavaScript riêng. csrfToken do common.js tự gắn vào mọi form POST.
--%>

<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Chọn vai trò — Super Gym</title>
        <%-- Dùng chung giao diện với màn Đăng nhập (nền tối, thẻ giữa màn). --%>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/login.css?v=20261014b">
        <style>
            /* Mỗi vai trò = 1 ô vuông, xếp ngang; hẹp quá thì tự xuống dòng. */
            .role-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: var(--space-3); margin-top: var(--space-2); }
            .role-list form { margin: 0; }
            .role-tile {
                position: relative;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                gap: var(--space-3);
                width: 100%;
                aspect-ratio: 1;
                padding: var(--space-3);
                border: var(--border-width) solid rgba(255, 255, 255, 0.5);
                border-radius: var(--radius-sm);
                background: rgba(255, 255, 255, 0.06);
                color: inherit;
                font: inherit;
                font-weight: var(--font-weight-semibold);
                text-align: center;
                cursor: pointer;
                transition: transform var(--duration-base) var(--ease-out), border-color var(--duration-base) var(--ease-out),
                        background-color var(--duration-base) var(--ease-out), box-shadow var(--duration-base) var(--ease-out);
            }
            .role-tile i { color: var(--color-lime); font-size: 2rem; }
            .role-tile:hover,
            .role-tile:focus-visible {
                transform: translateY(-4px);
                border-color: var(--color-lime);
                background: rgba(255, 255, 255, 0.08);
                box-shadow: 0 14px 28px -16px rgba(242, 92, 27, 0.7);
            }
            .role-tile:focus-visible { outline: 2px solid var(--color-lime); outline-offset: 2px; }
            .role-tile:active { transform: translateY(-1px); }
            .role-tile.is-current { border-color: var(--color-lime); }
            .role-tile small { position: absolute; top: var(--space-2); right: var(--space-2); font-size: var(--font-size-xs); font-weight: var(--font-weight-regular); opacity: .8; }
            @media (prefers-reduced-motion: reduce) {
                .role-tile { transition: none; }
                .role-tile:hover, .role-tile:focus-visible, .role-tile:active { transform: none; }
            }
        </style>
    </head>
    <body class="theme-guest-dark auth-dark">

        <%--
          TOAST — đọc flash toast trong session (đặt ở doPost rồi redirect về đây):
            MSG07  chọn vai trò mình không có (sửa ô ẩn bằng F12)   ← doPost B5
            MSG214 sai CSRF                                         ← doPost B2
        --%>
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

        <div class="auth-shell">
  <%@ include file="/WEB-INF/jsp/common/auth-artwork.jspf" %>
            <%-- Logo + linh vật — phần trang trí, dùng chung với màn Đăng nhập. --%>
            <div class="auth-top">
                <span class="auth-brand"><img class="auth-brand__logo" src="${pageContext.request.contextPath}/images/brand/super-gym-wordmark.svg?v=20261014d" alt="Super Gym" width="170" height="44">
                    <%@ include file="/WEB-INF/jsp/common/auth-muse.jspf" %></span>
            </div>

            <div class="auth-card">

                <%-- ===== KHỐI 1. TIÊU ĐỀ + CÂU HƯỚNG DẪN =====
                     chooseRoleMessage = MSG74 (doGet B5). Câu "Bạn có thể đổi…" viết cứng. --%>
                <div class="auth-head">
                    <h1>Chọn vai trò</h1>
                    <p><c:out value="${chooseRoleMessage}" /> Bạn có thể đổi vai trò bất cứ lúc nào trên menu tài khoản.</p>
                </div>

                <%-- ===== KHỐI 2. DANH SÁCH VAI TRÒ =====
                     Lặp roles → MỖI VAI TRÒ = 1 FORM RIÊNG → bấm nút nào gửi đúng vai trò nút đó (không cần JS).
                     Bấm → POST /select-role → SelectRoleServlet.doPost, gửi lên:
                       role       = r.dbValue        ("PT" | "Customer" | "Receptionist" …)
                       returnUrl  = trang định vào    (chỉ có khi doGet đặt)
                       csrfToken  = common.js tự gắn
                     Server KHÔNG tin ô "role": doPost B4 đọc lại vai trò từ DB, B5 so khớp. --%>
                <div class="role-list">
                    <c:forEach var="r" items="${roles}">
                        <form action="${pageContext.request.contextPath}/select-role" method="post">

                            <%-- Ô ẩn: vai trò của nút này. --%>
                            <input type="hidden" name="role" value="${r.dbValue}">

                            <%-- Ô ẩn: trang đang định vào trước khi bị đẩy sang đây (rỗng thì không in).
                                 doPost B8: thuộc khu vực vai trò mới → quay về đó, không thì về trang chủ vai trò. --%>
                            <c:if test="${not empty returnUrl}">
                                <input type="hidden" name="returnUrl" value="<c:out value='${returnUrl}' />">
                            </c:if>

                            <%-- Nút = ô vuông: Khách = biểu tượng tạ · Nhân viên = biểu tượng thẻ.
                                 Chữ trên nút = r.displayName. --%>
                            <button type="submit" class="role-tile ${currentRole eq r.dbValue ? 'is-current' : ''}">
                                <i class="fa-solid ${r.dbValue eq 'Customer' ? 'fa-dumbbell' : 'fa-id-badge'}"></i>
                                <span><c:out value="${r.displayName}" /></span>

                                <%-- "Đang dùng": vai trò trùng session ROLE.
                                     Vừa đăng nhập (ROLE = null) → không nút nào có; chỉ hiện khi vào bằng "Đổi vai trò". --%>
                                <c:if test="${currentRole eq r.dbValue}"><small>Đang dùng</small></c:if>
                            </button>
                        </form>
                    </c:forEach>
                </div>

                <%-- ===== KHỐI 3. ĐĂNG XUẤT =====
                     Link GET /logout → LogoutServlet: hủy session → về /login.
                     Chưa chọn vai trò vẫn bấm được vì AuthFilter B2b cho /logout đi qua. --%>
                <p class="auth-switch">
                    <a href="${pageContext.request.contextPath}/logout">Đăng xuất</a>
                </p>
            </div>
            <p class="auth-foot-note">© 2026 Super Gym</p>
        </div>
    </body>
</html>