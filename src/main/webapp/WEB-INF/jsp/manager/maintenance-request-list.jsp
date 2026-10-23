<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Yêu cầu bảo trì · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/maintenance-requests.css?v=20261023fix1">
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
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Yêu cầu bảo trì</h1>
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

      <main class="content" id="main-content" tabindex="-1">

        <%-- ===== Thống kê trạng thái ===== --%>
        <div class="maintenance-stats">

          <%-- Tất cả --%>
          <c:url var="allStatusUrl"
                 value="/manager/maintenance-requests">
            <c:if test="${not empty keyword}">
              <c:param name="keyword" value="${keyword}" />
            </c:if>
            <c:if test="${not empty priorityFilter}">
              <c:param name="priority" value="${priorityFilter}" />
            </c:if>
            <c:if test="${not empty reportedDateFilter}">
              <c:param name="reportedDate" value="${reportedDateFilter}" />
            </c:if>
          </c:url>

          <a href="${allStatusUrl}"
             class="maintenance-stat ${empty statusFilter ? 'active' : ''}">
            <span>Tất cả</span>
            <strong>${totalStatusCount}</strong>
          </a>

          <%-- Theo trạng thái --%>
          <c:forEach var="status" items="${statusOptions}">

            <c:url var="statusUrl"
                   value="/manager/maintenance-requests">

              <c:param name="status"
                       value="${status.dbValue}" />

              <c:if test="${not empty keyword}">
                <c:param name="keyword"
                         value="${keyword}" />
              </c:if>
              <c:if test="${not empty priorityFilter}">
                <c:param name="priority" value="${priorityFilter}" />
              </c:if>
              <c:if test="${not empty reportedDateFilter}">
                <c:param name="reportedDate" value="${reportedDateFilter}" />
              </c:if>

            </c:url>

            <a href="${statusUrl}"
               class="maintenance-stat
               ${statusFilter == status.dbValue ? 'active' : ''}">

              <span>
                <c:out value="${status.displayName}" />
              </span>

              <strong>
                <c:out value="${statusCounts[status.dbValue]}"
                       default="0" />
              </strong>

            </a>

          </c:forEach>

        </div>

        <%-- ===== Danh sách ===== --%>
        <section class="panel maintenance-panel">
          <div class="panel-head">
            <div>
              <h2>Danh sách yêu cầu</h2>
            </div>
          </div>

          <form action="${pageContext.request.contextPath}/manager/maintenance-requests"
                method="get"
                class="maintenance-filter">

            <input type="search"
                   name="keyword"
                   maxlength="100"
                   value="<c:out value='${keyword}' />"
                   placeholder="Mã yêu cầu hoặc tên thiết bị">

            <select name="status">
              <option value="">Tất cả trạng thái</option>

              <c:forEach var="status" items="${statusOptions}">
                <option value="${status.dbValue}"
                        <c:if test="${statusFilter == status.dbValue}">selected</c:if>>
                  <c:out value="${status.displayName}" />
                </option>
              </c:forEach>
            </select>

            <select name="priority">
              <option value="">Tất cả mức ưu tiên</option>

              <c:forEach var="p" items="${priorityOptions}">
                <option value="${p.dbValue}"
                        <c:if test="${priorityFilter == p.dbValue}">selected</c:if>>
                  <c:out value="${p.displayName}" />
                </option>
              </c:forEach>
            </select>

            <input type="date"
                   name="reportedDate"
                   value="<c:out value='${reportedDateFilter}' />"
                   title="Lọc theo ngày báo"
                   aria-label="Ngày báo">

            <select name="pageSize">
              <option value="10" ${requestPage.pageSize == 10 ? 'selected' : ''}>10</option>
              <option value="20" ${requestPage.pageSize == 20 ? 'selected' : ''}>20</option>
              <option value="50" ${requestPage.pageSize == 50 ? 'selected' : ''}>50</option>
            </select>
            <button class="btn btn-primary" type="submit">
              Lọc
            </button>
            <a class="btn btn-ghost"
               href="${pageContext.request.contextPath}/manager/maintenance-requests">
              Xóa lọc
            </a>
          </form>

          <c:choose>
            <c:when test="${not empty requestPage.items}">
              <div class="maintenance-list">

                <c:forEach var="item" items="${requestPage.items}">
                  <article class="maintenance-card">

                    <div>
                      <div class="maintenance-card-head">
                        <strong>
                          <c:out value="${item.request.requestCode}" />
                          ·
                          <c:out value="${item.itemCode}" />
                          (<c:out value="${item.facilityName}" />)
                        </strong>

                        <span class="badge
                          ${item.request.open ? 'badge-warning' :
                            item.request.inProgress ? 'badge-info' :
                            item.request.closed ? 'badge-success' : 'badge-neutral'}">

                          <c:out value="${item.request.status.displayName}" />
                        </span>
                      </div>

                      <div class="maintenance-meta">
                        <span>
                          Ngày báo: <b>${gms:formatDate(item.request.reportedAt.toLocalDate())}</b>
                        </span>

                        <span>
                          Ưu tiên: <b><c:out value="${item.request.priority.displayName}" /></b>
                        </span>

                        <c:if test="${not empty item.facilityLocation}">
                          <span>
                            Khu vực: <b><c:out value="${item.facilityLocation}" /></b>
                          </span>
                        </c:if>

                        <span>
                          Sự cố: <b><c:out value="${item.request.incidentType.displayName}" /></b>
                        </span>

                        <span>
                          Phát hiện: <b><c:out value="${item.request.detectedBy.displayName}" /></b>
                        </span>

                        <span>
                          Người báo: <b><c:out value="${item.reporterName}" /></b>
                        </span>

                        <c:if test="${item.request.warningTagPlaced}">
                          <span class="badge badge-danger">
                            Đã đặt cảnh báo
                          </span>
                        </c:if>
                      </div>

                      <p class="maintenance-description" style="margin-top: var(--space-2);">
                        <c:out value="${item.request.description}" />
                      </p>
                    </div>

                    <a class="btn
                       ${item.request.open ? 'btn-primary' : 'btn-outline'}
                       btn-sm"
                       href="${pageContext.request.contextPath}/manager/maintenance-requests/review?id=${item.request.maintenanceRequestId}">

                      ${item.request.open ? 'Xử lý' : 'Xem'}
                    </a>

                  </article>
                </c:forEach>

              </div>
            </c:when>

            <c:otherwise>
              <div class="maintenance-empty">
                Không có yêu cầu nào.
              </div>
            </c:otherwise>
          </c:choose>

          <c:if test="${requestPage.totalPages > 1}">
            <div class="maintenance-pager">

              <c:if test="${requestPage.hasPrevious}">
                <c:url var="previousUrl"
                       value="/manager/maintenance-requests">
                  <c:param name="page" value="${requestPage.pageNumber - 1}" />
                  <c:param name="pageSize" value="${requestPage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:param name="priority" value="${priorityFilter}" />
                  <c:param name="reportedDate" value="${reportedDateFilter}" />
                </c:url>

                <a class="btn btn-ghost btn-sm"
                   href="${previousUrl}">
                  Trang trước
                </a>
              </c:if>

              <span>
                Trang ${requestPage.pageNumber}
                / ${requestPage.totalPages}
              </span>

              <c:if test="${requestPage.hasNext}">
                <c:url var="nextUrl"
                       value="/manager/maintenance-requests">
                  <c:param name="page" value="${requestPage.pageNumber + 1}" />
                  <c:param name="pageSize" value="${requestPage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:param name="priority" value="${priorityFilter}" />
                  <c:param name="reportedDate" value="${reportedDateFilter}" />
                </c:url>

                <a class="btn btn-ghost btn-sm"
                   href="${nextUrl}">
                  Trang sau
                </a>
              </c:if>

            </div>
          </c:if>

        </section>
      </main>
    </div>
  </div>
</body>
</html>