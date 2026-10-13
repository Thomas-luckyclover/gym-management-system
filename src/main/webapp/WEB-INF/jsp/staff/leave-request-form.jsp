<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css">
</head>

<body class="dock ${formView.position eq 'Receptionist' ? 'theme-staff' : 'theme-staff'}">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%-- ===== Sidebar dock theo role PT / Lễ tân ===== --%>
    <c:set var="activeNav" value="leaves" />
    <c:choose>
      <c:when test="${formView.position eq 'Receptionist'}">
        <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
      </c:when>
      <c:otherwise>
        <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
      </c:otherwise>
    </c:choose>

    <div class="main">
      <%-- ===== Topbar: thông tin nhân viên đang đăng nhập ===== --%>
      <header class="topbar">
        <div class="topbar-title">
          <h1 style="margin:0;font-size:var(--font-size-xl)">Nghỉ phép</h1>
        </div>
        <div class="topbar-actions" style="display:flex;align-items:center;gap:var(--space-3)">
          <span class="badge badge-info">
            <c:choose>
              <c:when test="${formView.position eq 'PT'}">Huấn luyện viên PT</c:when>
              <c:when test="${formView.position eq 'Receptionist'}">Lễ tân</c:when>
              <c:otherwise><c:out value="${formView.position}" /></c:otherwise>
            </c:choose>
            · <c:out value="${formView.employeeCode}" />
          </span>
          <b style="font-size:var(--font-size-sm)"><c:out value="${formView.fullName}" /></b>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">
        <%-- Thông báo lỗi nộp đơn từ server --%>
        <c:if test="${not empty submitError}">
          <div class="alert-banner danger" style="margin-bottom:var(--space-4);padding:var(--space-3) var(--space-4);border-radius:var(--radius-md);background:var(--color-danger-tint);color:var(--color-danger);border:1px solid var(--color-danger-border)">
            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
            <c:out value="${submitError}" />
          </div>
        </c:if>

        <c:if test="${not empty formError}">
          <div class="alert-banner danger" style="margin-bottom:var(--space-4);padding:var(--space-3) var(--space-4);border-radius:var(--radius-md);background:var(--color-danger-tint);color:var(--color-danger);border:1px solid var(--color-danger-border)">
            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
            <c:out value="${formError}" />
          </div>
        </c:if>

        <%-- Layout 2 cột split cho màn nộp đơn --%>
        <div class="leave-split" style="display:grid;grid-template-columns:1fr 320px;gap:var(--space-6);align-items:start">
          <div class="leave-col-main">
            <%-- TODO Bước 2: Form nộp đơn --%>
            <%-- TODO Bước 4: Danh sách đơn đã nộp --%>
          </div>

          <div class="leave-col-side">
            <%-- TODO Bước 3: Side cards thông tin hạn nộp và định biên ca --%>
          </div>
        </div>
      </main>
    </div>
  </div>
</body>
</html>
