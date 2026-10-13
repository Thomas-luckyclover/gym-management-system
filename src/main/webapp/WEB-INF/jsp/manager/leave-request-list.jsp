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

        <%-- ===== Các tab trạng thái (statusCounts: Map số đơn theo All/Pending/Approved/Rejected do Service tạo; statusFilter: tab đang chọn do Servlet đặt) ===== --%>
        <div class="leave-stats">

          <%-- Tab "Tất cả" dùng status=All vì URL không có status nghĩa là tab Chờ duyệt --%>
          <c:url var="allTabUrl" value="/manager/leave-requests">
            <c:param name="status" value="All" />
            <c:if test="${not empty keyword}">
              <c:param name="keyword" value="${keyword}" />
            </c:if>
          </c:url>
          <a href="${allTabUrl}"
             class="leave-stat ${statusFilter == 'All' ? 'active' : ''}">
            <span>Tất cả</span>
            <strong><c:out value="${statusCounts['All']}" default="0" /></strong>
          </a>

          <c:url var="pendingTabUrl" value="/manager/leave-requests">
            <c:param name="status" value="Pending" />
            <c:if test="${not empty keyword}">
              <c:param name="keyword" value="${keyword}" />
            </c:if>
          </c:url>
          <a href="${pendingTabUrl}"
             class="leave-stat ${statusFilter == 'Pending' ? 'active' : ''}">
            <span>Chờ duyệt</span>
            <strong><c:out value="${statusCounts['Pending']}" default="0" /></strong>
          </a>

          <c:url var="approvedTabUrl" value="/manager/leave-requests">
            <c:param name="status" value="Approved" />
            <c:if test="${not empty keyword}">
              <c:param name="keyword" value="${keyword}" />
            </c:if>
          </c:url>
          <a href="${approvedTabUrl}"
             class="leave-stat ${statusFilter == 'Approved' ? 'active' : ''}">
            <span>Đã duyệt</span>
            <strong><c:out value="${statusCounts['Approved']}" default="0" /></strong>
          </a>

          <c:url var="rejectedTabUrl" value="/manager/leave-requests">
            <c:param name="status" value="Rejected" />
            <c:if test="${not empty keyword}">
              <c:param name="keyword" value="${keyword}" />
            </c:if>
          </c:url>
          <a href="${rejectedTabUrl}"
             class="leave-stat ${statusFilter == 'Rejected' ? 'active' : ''}">
            <span>Từ chối</span>
            <strong><c:out value="${statusCounts['Rejected']}" default="0" /></strong>
          </a>

        </div>

        <%-- ===== Bộ lọc và danh sách (leavePage, keyword) — nạp ở commit sau ===== --%>

      </main>
    </div>
  </div>
</body>
</html>
