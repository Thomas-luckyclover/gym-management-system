<%@ page contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c"
           uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">

<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>

  <title>Cơ sở vật chất · Super Gym</title>

  <link rel="stylesheet"
        href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">

  <link rel="stylesheet"
        href="${pageContext.request.contextPath}/css/pages/receptionist/facility-catalog.css">
</head>

<body class="dock theme-staff">

<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">

  <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

  <div class="main">

    <header class="topbar">

      <div class="tb-left">
        <h1 class="tb-title">
          Cơ sở vật chất
        </h1>
      </div>

      <div class="top-right">

        <a class="btn btn-primary"
           href="${pageContext.request.contextPath}/receptionist/maintenance-requests">
          Báo hỏng
        </a>

        <a class="tb-user"
           href="${pageContext.request.contextPath}/account">

          <span class="avatar">
            LT
          </span>

          <span>
            <b>
              <c:out
                      value="${sessionScope.displayName}"
                      default="Lễ tân" />
            </b>

            <small>Lễ tân</small>
          </span>

        </a>

      </div>

    </header>

    <main class="content">

      <section class="facility-panel">

        <form method="get"
              action="${pageContext.request.contextPath}/receptionist/facilities"
              class="facility-filter">

          <input type="search"
                 name="keyword"
                 maxlength="100"
                 value="<c:out value='${keyword}' />"
                 placeholder="Tìm tên thiết bị hoặc khu vực">

          <select name="category">

            <option value="">
              Tất cả khu vực
            </option>

            <c:forEach var="category"
                       items="${categoryOptions}">

              <option value="${category.dbValue}"
                      ${categoryFilter == category.dbValue
                              ? 'selected' : ''}>

                <c:out
                        value="${category.displayName}" />

              </option>

            </c:forEach>

          </select>

          <select name="status">

            <option value="">
              Tất cả tình trạng
            </option>

            <c:forEach var="status"
                       items="${statusOptions}">

              <option value="${status.dbValue}"
                      ${statusFilter == status.dbValue
                              ? 'selected' : ''}>

                <c:out
                        value="${status.displayName}" />

              </option>

            </c:forEach>

          </select>

          <button class="btn btn-primary"
                  type="submit">
            Lọc
          </button>

          <a class="btn btn-ghost"
             href="${pageContext.request.contextPath}/receptionist/facilities">
            Xóa lọc
          </a>

        </form>

      </section>

      <section class="facility-panel">

        <c:choose>

          <c:when test="${not empty facilityPage.items}">

            <div class="facility-list">

              <c:forEach var="facility"
                         items="${facilityPage.items}">

                <article class="facility-row">

                  <div class="facility-main">

                    <div class="facility-heading">

                      <strong>
                        <c:out
                                value="${facility.facilityName}" />
                      </strong>

                      <span class="badge
                        ${facility.status.dbValue == 'Available'
                          ? 'badge-success'
                          : facility.status.dbValue == 'UnderMaintenance'
                          ? 'badge-warning'
                          : 'badge-neutral'}">

                        <c:out
                                value="${facility.status.displayName}" />

                      </span>

                    </div>

                    <div class="facility-meta">

                      <span>
                        Danh mục:
                        <b>
                          <c:out
                                  value="${facility.category.displayName}" />
                        </b>
                      </span>

                      <c:if test="${not empty facility.location}">

                        <span>
                          Khu vực:
                          <b>
                            <c:out
                                    value="${facility.location}" />
                          </b>
                        </span>

                      </c:if>

                      <span>
                        Số lượng:
                        <b>
                          <c:out
                                  value="${facility.quantity}" />
                        </b>
                      </span>

                    </div>

                  </div>

                  <c:if test="${facility.status.dbValue != 'Retired'}">

                    <c:url var="reportUrl"
                           value="/receptionist/maintenance-requests">

                      <c:param name="facilityId"
                               value="${facility.facilityId}" />

                    </c:url>

                    <a class="btn btn-outline"
                       href="${reportUrl}">
                      Báo hỏng
                    </a>

                  </c:if>

                </article>

              </c:forEach>

            </div>

          </c:when>

          <c:otherwise>

            <div class="facility-empty">
              Không tìm thấy thiết bị phù hợp.
            </div>

          </c:otherwise>

        </c:choose>

        <c:if test="${facilityPage.totalPages > 1}">

          <div class="facility-pager">

            <c:if test="${facilityPage.hasPrevious}">

              <c:url var="previousUrl"
                     value="/receptionist/facilities">

                <c:param name="page"
                         value="${facilityPage.pageNumber - 1}" />

                <c:param name="pageSize"
                         value="${facilityPage.pageSize}" />

                <c:param name="keyword"
                         value="${keyword}" />

                <c:param name="category"
                         value="${categoryFilter}" />

                <c:param name="status"
                         value="${statusFilter}" />

              </c:url>

              <a class="btn btn-ghost btn-sm"
                 href="${previousUrl}">
                Trang trước
              </a>

            </c:if>

            <span>
              Trang ${facilityPage.pageNumber}
              / ${facilityPage.totalPages}
            </span>

            <c:if test="${facilityPage.hasNext}">

              <c:url var="nextUrl"
                     value="/receptionist/facilities">

                <c:param name="page"
                         value="${facilityPage.pageNumber + 1}" />

                <c:param name="pageSize"
                         value="${facilityPage.pageSize}" />

                <c:param name="keyword"
                         value="${keyword}" />

                <c:param name="category"
                         value="${categoryFilter}" />

                <c:param name="status"
                         value="${statusFilter}" />

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