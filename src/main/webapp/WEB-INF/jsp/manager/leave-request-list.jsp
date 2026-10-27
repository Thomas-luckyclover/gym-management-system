<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN" scope="page" />
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Quản lý đơn nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-request-list.css?v=20261025-compact">

  <script src="${pageContext.request.contextPath}/js/pages/manager/leave-request-list.js?v=20261025-readable" defer></script>
</head>

<body class="dock theme-manager leave-list-page">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Quản lý đơn nghỉ phép</h1>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content">

        <%-- ===== Tabs trạng thái và bộ lọc danh sách ===== --%>
        <div class="leave-toolbar">

          <%-- 4 Tab trạng thái --%>
          <div class="leave-tabs-nav">
            <c:forTokens var="tab" items="Pending,Approved,Rejected,All" delims=",">
              <c:url var="statusTabUrl" value="/manager/leave-requests">
                <c:param name="status" value="${tab}" />
                <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
                <c:if test="${not empty positionFilter}"><c:param name="position" value="${positionFilter}" /></c:if>
                <c:if test="${not empty leaveTypeFilter}"><c:param name="leaveType" value="${leaveTypeFilter}" /></c:if>
                <c:param name="fromDate" value="${fromDateFilter}" />
                <c:param name="toDate" value="${toDateFilter}" />
              </c:url>
              <a href="${statusTabUrl}" class="leave-tab-btn ${statusFilter == tab ? 'active' : ''}">
                <span>
                  <c:choose>
                    <c:when test="${tab == 'Pending'}">Chờ duyệt</c:when>
                    <c:when test="${tab == 'Approved'}">Đã duyệt</c:when>
                    <c:when test="${tab == 'Rejected'}">Từ chối</c:when>
                    <c:otherwise>Tất cả</c:otherwise>
                  </c:choose>
                </span>
                <span class="leave-tab-count"><c:out value="${statusCounts[tab]}" default="0" /></span>
              </a>
            </c:forTokens>
          </div>

          <%-- Bộ lọc GET: ngày gửi đơn, chức vụ, loại nghỉ và tên/mã nhân viên. --%>
          <form action="${pageContext.request.contextPath}/manager/leave-requests" method="get" class="leave-filter-nav">
            <input type="hidden" name="status" value="<c:out value='${statusFilter}' />">

            <div class="leave-search-field">
              <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
              <input type="search" name="keyword" class="filter-input-search" maxlength="100"
                     value="<c:out value='${keyword}' />"
                     aria-label="Tìm theo tên hoặc mã nhân viên" placeholder="Tên hoặc mã nhân viên">
            </div>

            <div class="leave-date-filters" role="group" aria-label="Lọc theo thời gian gửi đơn">
              <label class="leave-date-field" for="fromDate">
                <span>Gửi từ ngày</span>
                <input type="date" id="fromDate" name="fromDate" class="filter-select"
                       value="<c:out value='${fromDateFilter}' />">
              </label>
              <label class="leave-date-field" for="toDate">
                <span>Đến ngày</span>
                <input type="date" id="toDate" name="toDate" class="filter-select"
                       value="<c:out value='${toDateFilter}' />">
              </label>
            </div>

            <details class="leave-more-filters">
              <summary class="btn btn-sm btn-outline">
                <i class="fa-solid fa-sliders" aria-hidden="true"></i> Bộ lọc
                <c:if test="${not empty positionFilter or not empty leaveTypeFilter}">
                  <span class="leave-filter-count"><c:out value="${(not empty positionFilter ? 1 : 0) + (not empty leaveTypeFilter ? 1 : 0)}" /></span>
                </c:if>
              </summary>
              <div class="leave-filter-popover">
                <label for="leave-position">Chức vụ</label>
                <select id="leave-position" name="position" class="filter-select">
                  <option value="">Tất cả chức vụ</option>
                  <option value="PT" ${positionFilter == 'PT' ? 'selected' : ''}>PT</option>
                  <option value="Receptionist" ${positionFilter == 'Receptionist' ? 'selected' : ''}>Lễ tân</option>
                </select>

                <label for="leave-type">Loại nghỉ</label>
                <select id="leave-type" name="leaveType" class="filter-select">
                  <option value="">Tất cả loại nghỉ</option>
                  <option value="Annual" ${leaveTypeFilter == 'Annual' ? 'selected' : ''}>Phép năm</option>
                  <option value="Unpaid" ${leaveTypeFilter == 'Unpaid' ? 'selected' : ''}>Không lương</option>
                </select>
              </div>
            </details>

            <button class="btn btn-sm btn-primary" type="submit">
              <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i> Tìm
            </button>

            <c:if test="${not empty keyword or not empty positionFilter or not empty leaveTypeFilter or not empty fromDateFilter or not empty toDateFilter}">
              <a class="btn btn-sm btn-ghost"
                 href="${pageContext.request.contextPath}/manager/leave-requests?status=<c:out value='${statusFilter}' />">
                Xóa lọc
              </a>
            </c:if>
          </form>

        </div>

        <%-- Kết quả một lần sau POST/redirect; lỗi từng đơn được escape khi hiển thị. --%>
        <c:if test="${not empty bulkResult}">
          <div class="leave-bulk-result" role="status">
            <b>Đã duyệt <c:out value="${bulkResult.approvedCount}" /> đơn.</b>
            <c:if test="${not empty bulkResult.failedRequests}">
              <details>
                <summary><c:out value="${fn:length(bulkResult.failedRequests)}" /> đơn chưa duyệt — xem lý do</summary>
                <ul>
                  <c:forEach var="failure" items="${bulkResult.failedRequests}">
                    <li><a href="${pageContext.request.contextPath}/manager/leave-requests/review?id=<c:out value='${failure.key}' />">Đơn #<c:out value="${failure.key}" /></a>: <c:out value="${failure.value}" /></li>
                  </c:forEach>
                </ul>
              </details>
            </c:if>
          </div>
        </c:if>

        <%-- ===== Bảng danh sách đơn nghỉ phép ===== --%>
        <section class="panel leave-panel leave-list-panel" aria-label="Nhân viên và đơn nghỉ phép">
          <c:if test="${statusFilter == 'Pending' and not empty requestPage.items}">
            <div class="leave-list-caption">
              <form id="leave-bulk-form" class="leave-bulk-actions" action="${pageContext.request.contextPath}/manager/leave-requests/review" method="post">
                <input type="hidden" name="csrfToken" value="<c:out value='${csrfToken}' />">
                <input type="hidden" name="action" value="approveMany">
                <input type="hidden" name="returnUrl" value="<c:out value='${listReturnUrl}' />">
                <span id="leave-selection-count" aria-live="polite" hidden>Đã chọn 0 đơn</span>
                <button id="leave-approve-selected" type="submit" class="btn btn-sm btn-primary" disabled
                        data-confirm-title="Duyệt các đơn đã chọn" data-confirm-text="Duyệt"
                        data-confirm-message="Duyệt các đơn đã chọn?">Duyệt đã chọn</button>
              </form>
            </div>
          </c:if>

          <c:if test="${not empty pageError}">
            <div class="leave-empty leave-list-error" role="alert"><c:out value="${pageError}" /></div>
          </c:if>

          <%-- Tất cả: employeePage mở lịch sử từng người. Các tab trạng thái: requestPage hiện từng đơn. --%>
          <c:choose>
            <c:when test="${employeeView and not empty employeePage.items}">
              <div class="leave-employee-list">
                <c:forEach var="employee" items="${employeePage.items}">
                  <c:choose>
                    <c:when test="${employee.totalRequestCount > 0}">
                      <details class="leave-employee-group ${employee.nearLeaveDate ? 'leave-employee-near' : ''}">
                        <summary class="leave-employee-summary">
                          <%@ include file="/WEB-INF/jsp/manager/leave-employee-header.jspf" %>
                        </summary>
                        <div class="leave-employee-requests">
                          <c:choose>
                            <c:when test="${not empty employee.requests}">
                              <c:set var="requestItems" value="${employee.requests}" />
                              <c:set var="showEmployee" value="${false}" />
                              <%@ include file="/WEB-INF/jsp/manager/leave-request-table.jspf" %>
                            </c:when>
                            <c:otherwise>
                              <div class="leave-group-empty">Không có đơn phù hợp bộ lọc đang chọn. Thay đổi bộ lọc để xem lịch sử.</div>
                            </c:otherwise>
                          </c:choose>
                        </div>
                      </details>
                    </c:when>
                    <c:otherwise>
                      <div class="leave-employee-group leave-employee-no-requests">
                        <div class="leave-employee-summary">
                          <%@ include file="/WEB-INF/jsp/manager/leave-employee-header.jspf" %>
                        </div>
                      </div>
                    </c:otherwise>
                  </c:choose>
                </c:forEach>
              </div>
            </c:when>
            <c:when test="${not employeeView and not empty requestPage.items}">
              <c:set var="requestItems" value="${requestPage.items}" />
              <c:set var="showEmployee" value="${true}" />
              <%@ include file="/WEB-INF/jsp/manager/leave-request-table.jspf" %>
            </c:when>
            <c:otherwise>
              <c:if test="${empty pageError}">
                <div class="leave-empty">Không tìm thấy ${employeeView ? 'nhân viên' : 'đơn nghỉ phép'} phù hợp bộ lọc đã chọn.</div>
              </c:if>
            </c:otherwise>
          </c:choose>

          <%-- Phân trang --%>
          <c:if test="${listPage.totalPages > 1}">
            <div class="leave-pager">
              <c:if test="${listPage.hasPrevious}">
                <c:url var="previousUrl" value="/manager/leave-requests">
                  <c:param name="page" value="${listPage.pageNumber - 1}" />
                  <c:param name="pageSize" value="${listPage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:if test="${not empty positionFilter}"><c:param name="position" value="${positionFilter}" /></c:if>
                  <c:if test="${not empty leaveTypeFilter}"><c:param name="leaveType" value="${leaveTypeFilter}" /></c:if>
                  <c:param name="fromDate" value="${fromDateFilter}" />
                  <c:param name="toDate" value="${toDateFilter}" />
                </c:url>
                <a class="btn btn-ghost btn-sm" href="${previousUrl}">Trang trước</a>
              </c:if>

              <span>Trang ${listPage.pageNumber} / ${listPage.totalPages} (${listPage.totalItems} ${employeeView ? 'nhân viên' : 'đơn'})</span>

              <c:if test="${listPage.hasNext}">
                <c:url var="nextUrl" value="/manager/leave-requests">
                  <c:param name="page" value="${listPage.pageNumber + 1}" />
                  <c:param name="pageSize" value="${listPage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:if test="${not empty positionFilter}"><c:param name="position" value="${positionFilter}" /></c:if>
                  <c:if test="${not empty leaveTypeFilter}"><c:param name="leaveType" value="${leaveTypeFilter}" /></c:if>
                  <c:param name="fromDate" value="${fromDateFilter}" />
                  <c:param name="toDate" value="${toDateFilter}" />
                </c:url>
                <a class="btn btn-ghost btn-sm" href="${nextUrl}">Trang sau</a>
              </c:if>
            </div>
          </c:if>

        </section>

      </main>
    </div>
  </div>

  <%-- Một tooltip dùng chung: JS lấy Reason đã escape của tên đang hover/focus. --%>
  <div id="leave-reason-tooltip" class="leave-reason-tooltip" role="tooltip" hidden>
    <span class="leave-reason-title">Lý do xin nghỉ</span>
    <p id="leave-reason-text"></p>
  </div>
</body>
</html>
