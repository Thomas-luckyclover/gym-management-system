<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  MÀN CHỌN VAI TRÒ — mở bởi SelectRoleServlet.doGet (B6), khi tài khoản có ≥ 2 vai trò.
  Dữ liệu (request.setAttribute ở doGet B5):
    roles             ← SelectRoleServlet.doGet → AuthService.findActiveRoles → AccountDAO  → mỗi vai trò 1 nút
    currentRole       ← session ROLE                                                      → chữ "Đang dùng"
    chooseRoleMessage ← Messages.MSG74                                                    → câu dưới tiêu đề
    returnUrl         ← ?returnUrl= (đã kiểm an toàn)                                      → gửi lại khi bấm nút
--%>

<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Chọn vai trò — Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/login.css?v=20261014b">
        <style>
            .role-list { display: grid; gap: 12px; margin-top: 8px; }
            .role-list form { margin: 0; }
            .role-option { width: 100%; display: flex; align-items: center; gap: 12px; justify-content: flex-start; text-align: left; }
            .role-option i { width: 20px; text-align: center; }
            .role-option small { margin-left: auto; opacity: .75; }
        </style>
    </head>
    <body class="theme-guest-dark auth-dark">

        <%-- Toast từ session flash (VD MSG07 khi chọn vai trò không có quyền). --%>
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="auth-shell">
  <%@ include file="/WEB-INF/jsp/common/auth-artwork.jspf" %>
            <div class="auth-top">
                <span class="auth-brand"><img class="auth-brand__logo" src="${pageContext.request.contextPath}/images/brand/super-gym-wordmark.svg?v=20261014d" alt="Super Gym" width="170" height="44">
                    <%@ include file="/WEB-INF/jsp/common/auth-muse.jspf" %></span>
            </div>

            <div class="auth-card">
                <div class="auth-head">
                    <h1>Chọn vai trò</h1>

                    <%-- Câu MSG74 "Chọn vai trò…". --%>
                    <p><c:out value="${chooseRoleMessage}" /> Bạn có thể đổi vai trò bất cứ lúc nào trên menu tài khoản.</p>
                </div>

                <div class="role-list">

                    <%--
                      MỖI VAI TRÒ = 1 FORM: POST /select-role → SelectRoleServlet.doPost
                        role = r.dbValue ("PT" | "Customer" …) · returnUrl (nếu có) · csrfToken = common.js tự gắn
                    --%>
                    <c:forEach var="r" items="${roles}">
                        <form action="${pageContext.request.contextPath}/select-role" method="post">
                            <input type="hidden" name="role" value="${r.dbValue}">
                            <c:if test="${not empty returnUrl}">
                                <input type="hidden" name="returnUrl" value="<c:out value='${returnUrl}' />">
                            </c:if>

                            <%-- Nút: chữ = r.displayName; Khách = nút viền, nhân viên = nút cam. --%>
                            <button type="submit" class="btn ${r.dbValue eq 'Customer' ? 'btn-outline' : 'btn-primary'} role-option">
                                <i class="fa-solid ${r.dbValue eq 'Customer' ? 'fa-dumbbell' : 'fa-id-badge'}"></i>
                                <span><c:out value="${r.displayName}" /></span>

                                <%-- Vai trò đang dùng → chữ nhỏ "Đang dùng". --%>
                                <c:if test="${currentRole eq r.dbValue}"><small>Đang dùng</small></c:if>
                            </button>
                        </form>
                    </c:forEach>
                </div>

                <p class="auth-switch">

                    <%-- Đăng xuất → LogoutServlet. --%>
                    <a href="${pageContext.request.contextPath}/logout">Đăng xuất</a>
                </p>
            </div>
            <p class="auth-foot-note">© 2026 Super Gym</p>
        </div>
    </body>
</html>
