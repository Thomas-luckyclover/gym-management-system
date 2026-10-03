<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  Màn chọn vai trò — CBR-113. SelectRoleServlet set:
    roles       : List<Role> còn hiệu lực (≥ 2)
    currentRole : vai trò đang dùng (null nếu vừa đăng nhập)
    returnUrl   : trang muốn quay lại (tùy chọn)
--%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Chọn vai trò — Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/login.css?v=6">
        <style>
            .role-list { display: grid; gap: 12px; margin-top: 8px; }
            .role-list form { margin: 0; }
            .role-option { width: 100%; display: flex; align-items: center; gap: 12px; justify-content: flex-start; text-align: left; }
            .role-option i { width: 20px; text-align: center; }
            .role-option small { margin-left: auto; opacity: .75; }
        </style>
    </head>
    <body class="theme-guest-dark auth-dark">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="auth-shell">
            <div class="auth-top">
                <span class="auth-brand">Super Gym</span>
            </div>

            <div class="auth-card">
                <div class="auth-head">
                    <h1>Chọn vai trò</h1>
                    <p>Tài khoản của bạn dùng được cho nhiều vai trò. Bạn có thể đổi vai trò bất cứ lúc nào trên menu tài khoản.</p>
                </div>

                <div class="role-list">
                    <c:forEach var="r" items="${roles}">
                        <form action="${pageContext.request.contextPath}/select-role" method="post">
                            <input type="hidden" name="role" value="${r.dbValue}">
                            <c:if test="${not empty returnUrl}">
                                <input type="hidden" name="returnUrl" value="<c:out value='${returnUrl}' />">
                            </c:if>
                            <button type="submit" class="btn ${r.dbValue eq 'Customer' ? 'btn-outline' : 'btn-primary'} role-option">
                                <i class="fa-solid ${r.dbValue eq 'Customer' ? 'fa-dumbbell' : 'fa-id-badge'}"></i>
                                <span><c:out value="${r.displayName}" /></span>
                                <c:if test="${currentRole eq r.dbValue}"><small>Đang dùng</small></c:if>
                            </button>
                        </form>
                    </c:forEach>
                </div>

                <p class="auth-switch">
                    <a href="${pageContext.request.contextPath}/logout">Đăng xuất</a>
                </p>
            </div>
            <p class="auth-foot-note">© 2026 Super Gym</p>
        </div>
    </body>
</html>
