<%@ page contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c"
           uri="jakarta.tags.core" %>
<%@ taglib prefix="gms"
           uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">

<head>

  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>

  <title>Thông báo · Super Gym</title>

  <link rel="stylesheet"
        href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">

</head>

<body class="dock
<c:choose>
  <c:when test="${sessionScope.role == 'Manager'}">theme-manager</c:when>
  <c:when test="${sessionScope.role == 'PT'}">theme-pt</c:when>
  <c:otherwise>theme-staff</c:otherwise>
</c:choose>">

<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">

  <c:choose>

    <c:when test="${sessionScope.role == 'Manager'}">
      <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>
    </c:when>

    <c:when test="${sessionScope.role == 'PT'}">
      <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
    </c:when>

    <c:otherwise>
      <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
    </c:otherwise>

  </c:choose>

  <div class="main">

    <header class="topbar">

      <div class="tb-left">

        <h1 class="tb-title">
          Thông báo
        </h1>

      </div>

      <div class="top-right">

        <a class="tb-user"
           href="${pageContext.request.contextPath}/account">

          <span class="avatar"><i class="fa-solid fa-user" aria-hidden="true"></i></span>

          <span>

            <b>
              <c:out
                      value="${sessionScope.displayName}" />
            </b>

            <small>
              <c:choose>
                <c:when test="${sessionScope.role == 'Manager'}">Quản lý</c:when>
                <c:when test="${sessionScope.role == 'PT'}">Huấn luyện viên PT</c:when>
                <c:otherwise>Lễ tân</c:otherwise>
              </c:choose>
            </small>

          </span>

        </a>

      </div>

    </header>

    <main class="content" id="main-content" tabindex="-1">

      <section class="notification-panel">

        <c:choose>

          <c:when test="${not empty notificationPage.items}">

            <div class="notification-list">

              <c:forEach var="notification"
                         items="${notificationPage.items}">

                <article class="notification-card
                    ${notification.read
                        ? ''
                        : 'notification-unread'}">

                  <div class="notification-icon"><i class="fa-solid fa-bell" aria-hidden="true"></i></div>

                  <div class="notification-body">

                    <div class="notification-head">

                      <strong>
                        <c:out
                                value="${notification.title}" />
                      </strong>

                      <c:if test="${not notification.read}">
                        <span class="badge badge-info">
                          Mới
                        </span>
                      </c:if>

                    </div>

                    <p>
                      <c:out
                              value="${notification.content}" />
                    </p>

                    <small>
                      ${gms:formatDateTime(notification.sentAt)}
                    </small>

                  </div>

                  <c:if test="${not notification.read}">

                    <form method="post"
                          action="${pageContext.request.contextPath}/notifications">

                      <input type="hidden"
                             name="notificationId"
                             value="${notification.notificationId}">

                      <button type="submit"
                              class="btn btn-outline btn-sm">
                        Đánh dấu đã đọc
                      </button>

                    </form>

                  </c:if>

                </article>

              </c:forEach>

            </div>

          </c:when>

          <c:otherwise>

            <div class="notification-empty">
              Chưa có thông báo nào.
            </div>

          </c:otherwise>

        </c:choose>

      </section>

    </main>

  </div>

</div>

</body>
</html>