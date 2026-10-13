<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Đơn nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css">
  <link rel="stylesheet"
        href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">

  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js"
          defer></script>
</head>

<body class="dock theme-manager">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <%-- ===== Thanh trên cùng (sessionScope.displayName do AuthService đặt khi đăng nhập) ===== --%>
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Đơn nghỉ phép</h1>
        </div>

        <div class="top-right">

          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <a class="tb-user"
             href="${pageContext.request.contextPath}/account">
            <span class="avatar">QL</span>
            <span>
              <b><c:out value="${sessionScope.displayName}" default="Quản lý" /></b>
              <small>Quản lý</small>
            </span>
          </a>
        </div>
      </header>

      <main class="content">

        <%-- ===== Các tab trạng thái (statusCounts, statusFilter) — nạp ở commit sau ===== --%>

        <%-- ===== Bộ lọc và danh sách (leavePage, keyword) — nạp ở commit sau ===== --%>

      </main>
    </div>
  </div>
</body>
</html>
