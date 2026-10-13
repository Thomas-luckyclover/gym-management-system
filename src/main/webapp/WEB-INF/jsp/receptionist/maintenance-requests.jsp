<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Yêu cầu bảo trì · Super Gym Lễ tân</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/maintenance-requests.css">
  <link rel="stylesheet"
        href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">

  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js"
          defer></script>
</head>

<body class="dock theme-staff">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Yêu cầu bảo trì</h1>
        </div>

        <div class="top-right">

          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <a class="tb-user"
             href="${pageContext.request.contextPath}/account">
            <span class="avatar">LT</span>
            <span>
              <b><c:out value="${sessionScope.displayName}" default="Lễ tân" /></b>
              <small>Lễ tân</small>
            </span>
          </a>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">

        <%-- ===== Form gửi yêu cầu ===== --%>
        <section class="panel maintenance-panel">
          <div class="panel-head">
            <div>
              <h2>Gửi yêu cầu mới</h2>
            </div>
          </div>

          <c:if test="${not empty formMessage}">
            <div class="alert-banner danger">
              <c:out value="${formMessage}" />
            </div>
          </c:if>

          <form action="${pageContext.request.contextPath}/receptionist/maintenance-requests"
                method="post"
                data-validate>

            <input type="hidden" name="action" value="create">

            <div class="maintenance-form-grid">
              <div class="field">
                <label class="field-label" for="facilityId">Thiết bị *</label>

                <select id="facilityId" name="facilityId" required>
                  <option value="">Chọn thiết bị...</option>

                  <c:forEach var="facility" items="${facilities}">
                    <option value="${facility.facilityId}"
                            <c:if test="${formFacilityId == facility.facilityId}">selected</c:if>>
                      <c:out value="${facility.facilityName}" />
                      <c:if test="${not empty facility.location}">
                        · <c:out value="${facility.location}" />
                      </c:if>
                    </option>
                  </c:forEach>
                </select>

                <div class="field-error">
                  <c:out value="${errors.facilityId}" />
                </div>
              </div>

              <div class="field">
                <label class="field-label" for="priority">Mức ưu tiên *</label>

                <select id="priority" name="priority" required>
                  <c:forEach var="priority" items="${priorityOptions}">
                    <option value="${priority.dbValue}"
                            <c:if test="${formPriority == priority.dbValue
                                    or (empty formPriority and priority.dbValue == 'Normal')}">
                              selected
                            </c:if>>
                      <c:out value="${priority.displayName}" />
                    </option>
                  </c:forEach>
                </select>

                <div class="field-error">
                  <c:out value="${errors.priority}" />
                </div>
              </div>
            </div>

            <div class="field">
              <label class="field-label" for="description">
                Mô tả hỏng hóc *
              </label>

              <textarea id="description"
                        name="description"
                        rows="4"
                        minlength="10"
                        maxlength="500"
                        required
                        placeholder="Hỏng ở đâu, xảy ra khi nào, đã xử lý tạm thế nào"><c:out value="${formDescription}" /></textarea>

              <div class="field-error">
                <c:out value="${errors.description}" />
              </div>
            </div>

            <label class="maintenance-check">
              <input type="checkbox"
                     name="warningTagPlaced"
                     value="true"
                     <c:if test="${formWarningTagPlaced}">checked</c:if>>

              <span>
                <b>Đã dán cảnh báo, ngừng cho khách dùng</b>
                <small>Chọn khi thiết bị không còn an toàn.</small>
              </span>
            </label>

            <div class="btn-row">
              <button class="btn btn-primary" type="submit">
                Gửi yêu cầu
              </button>
            </div>
          </form>
        </section>

        <%-- ===== Danh sách yêu cầu ===== --%>
        <section class="panel maintenance-panel">
          <div class="panel-head">
            <div>
              <h2>Yêu cầu bảo trì</h2>
            </div>
          </div>

          <form action="${pageContext.request.contextPath}/receptionist/maintenance-requests"
                method="get"
                class="maintenance-filter">

            <input type="search"
                   name="keyword"
                   maxlength="100"
                   value="<c:out value='${keyword}' />"
                   placeholder="Tìm mã yêu cầu hoặc tên thiết bị">

            <select name="status">
              <option value="">Tất cả trạng thái</option>

              <c:forEach var="status" items="${statusOptions}">
                <option value="${status.dbValue}"
                        <c:if test="${statusFilter == status.dbValue}">selected</c:if>>
                  <c:out value="${status.displayName}" />
                </option>
              </c:forEach>
            </select>

            <select name="pageSize">
              <option value="10" ${requestPage.pageSize == 10 ? 'selected' : ''}>10</option>
              <option value="20" ${requestPage.pageSize == 20 ? 'selected' : ''}>20</option>
              <option value="50" ${requestPage.pageSize == 50 ? 'selected' : ''}>50</option>
            </select>

            <button class="btn btn-primary" type="submit">Lọc</button>

            <a class="btn btn-ghost"
               href="${pageContext.request.contextPath}/receptionist/maintenance-requests">
              Xóa lọc
            </a>
          </form>

          <c:choose>
            <c:when test="${not empty requestPage.items}">
              <div class="maintenance-list">
                <c:forEach var="item" items="${requestPage.items}">
                  <article class="maintenance-card">

                    <div class="maintenance-card-main">
                      <div class="maintenance-card-head">
                        <strong>
                          <c:out value="${item.request.requestCode}" />
                          ·
                          <c:out value="${item.facilityName}" />
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
                          ${gms:formatDate(item.request.reportedAt.toLocalDate())}
                        </span>

                        <span>
                          <c:out value="${item.request.priority.displayName}" />
                        </span>

                        <c:if test="${item.request.warningTagPlaced}">
                          <span class="badge badge-danger">
                            Đã đặt cảnh báo
                          </span>
                        </c:if>
                      </div>

                      <p class="maintenance-description">
                        <c:out value="${item.request.description}" />
                      </p>

                      <div class="maintenance-progress">
                        <c:choose>
                          <c:when test="${item.request.open}">
                            Chờ Quản lý duyệt.
                          </c:when>

                          <c:when test="${item.request.inProgress}">
                            Quản lý
                            <b><c:out value="${item.reviewerName}" /></b>
                            đã duyệt.
                          </c:when>

                          <c:when test="${item.request.rejected}">
                            Bị từ chối:
                            <c:out value="${item.request.rejectReason}" />
                          </c:when>

                          <c:when test="${item.request.closed}">
                            Đã nghiệm thu bởi
                            <b><c:out value="${item.closerName}" /></b>.
                            <c:out value="${item.request.acceptanceNote}" />
                          </c:when>
                        </c:choose>
                      </div>
                    </div>

                    <c:if test="${item.request.inProgress}">
                      <form action="${pageContext.request.contextPath}/receptionist/maintenance-requests"
                            method="post"
                            class="maintenance-close-form">

                        <input type="hidden"
                               name="action"
                               value="close">

                        <input type="hidden"
                               name="requestId"
                               value="${item.request.maintenanceRequestId}">

                        <label class="field-label"
                               for="acceptance-${item.request.maintenanceRequestId}">
                          Ghi chú nghiệm thu
                        </label>

                        <textarea id="acceptance-${item.request.maintenanceRequestId}"
                                  name="acceptanceNote"
                                  minlength="5"
                                  maxlength="500"
                                  required
                                  placeholder="Đã kiểm tra thiết bị hoạt động bình thường..."></textarea>

                        <button class="btn btn-primary btn-sm"
                                type="submit"
                                data-confirm-title="Đóng yêu cầu"
                                data-confirm-message="Xác nhận thiết bị đã được sửa và có thể sử dụng?">
                          Đóng yêu cầu
                        </button>
                      </form>
                    </c:if>

                  </article>
                </c:forEach>
              </div>
            </c:when>

            <c:otherwise>
              <div class="maintenance-empty">
                Không có yêu cầu nào khớp bộ lọc.
              </div>
            </c:otherwise>
          </c:choose>

          <%-- ===== Phân trang ===== --%>
          <c:if test="${requestPage.totalPages > 1}">
            <div class="maintenance-pager">

              <c:if test="${requestPage.hasPrevious}">
                <c:url var="previousUrl"
                       value="/receptionist/maintenance-requests">
                  <c:param name="page" value="${requestPage.pageNumber - 1}" />
                  <c:param name="pageSize" value="${requestPage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
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
                       value="/receptionist/maintenance-requests">
                  <c:param name="page" value="${requestPage.pageNumber + 1}" />
                  <c:param name="pageSize" value="${requestPage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
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