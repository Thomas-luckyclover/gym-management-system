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
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-request-list.css?v=20261021-near-badge">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
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

          <a class="tb-user" href="${pageContext.request.contextPath}/account">
            <span class="avatar">QL</span>
            <span>
              <b><c:out value="${sessionScope.displayName}" default="Quản lý" /></b>
              <small>Quản lý</small>
            </span>
          </a>
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
                <c:if test="${not empty fromDateFilter}"><c:param name="fromDate" value="${fromDateFilter}" /></c:if>
                <c:if test="${not empty toDateFilter}"><c:param name="toDate" value="${toDateFilter}" /></c:if>
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

          <%-- Chỉ lọc 2 chức vụ và 3 loại nghỉ đang được backend hỗ trợ. --%>
          <form action="${pageContext.request.contextPath}/manager/leave-requests" method="get" class="leave-filter-nav">
            <input type="hidden" name="status" value="<c:out value='${statusFilter}' />">
            <c:if test="${not empty fromDateFilter}"><input type="hidden" name="fromDate" value="<c:out value='${fromDateFilter}' />"></c:if>
            <c:if test="${not empty toDateFilter}"><input type="hidden" name="toDate" value="<c:out value='${toDateFilter}' />"></c:if>

            <select name="position" class="filter-select" aria-label="Lọc theo chức vụ">
              <option value="">Tất cả chức vụ</option>
              <option value="PT" ${positionFilter == 'PT' ? 'selected' : ''}>PT</option>
              <option value="Receptionist" ${positionFilter == 'Receptionist' ? 'selected' : ''}>Lễ tân</option>
            </select>

            <select name="leaveType" class="filter-select" aria-label="Lọc theo loại nghỉ">
              <option value="">Tất cả loại nghỉ</option>
              <option value="Annual" ${leaveTypeFilter == 'Annual' ? 'selected' : ''}>Phép năm</option>
              <option value="Sick" ${leaveTypeFilter == 'Sick' ? 'selected' : ''}>Nghỉ ốm</option>
              <option value="Unpaid" ${leaveTypeFilter == 'Unpaid' ? 'selected' : ''}>Không lương</option>
            </select>

            <input type="search"
                   name="keyword"
                   class="filter-input-search"
                   maxlength="100"
                   value="<c:out value='${keyword}' />"
                   aria-label="Tìm theo tên hoặc mã nhân viên"
                   placeholder="Tên hoặc mã nhân viên">

            <button class="btn btn-sm btn-primary" type="submit">
              <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i> Tìm
            </button>

            <c:if test="${not empty keyword or not empty positionFilter or not empty leaveTypeFilter or not empty fromDateFilter or not empty toDateFilter}">
              <a class="btn btn-sm btn-ghost"
                 href="${pageContext.request.contextPath}/manager/leave-requests<c:if test="${not empty statusFilter and statusFilter != 'Pending'}">?status=<c:out value='${statusFilter}' /></c:if>">
                Xóa lọc
              </a>
            </c:if>
          </form>

        </div>

        <%-- ===== Bảng danh sách đơn nghỉ phép ===== --%>
        <section class="panel leave-panel leave-list-panel" aria-label="Danh sách đơn nghỉ phép">
          <div class="leave-list-caption">
            <span><c:out value="${leavePage.totalItems}" /> đơn</span>
            <c:if test="${not empty fromDateFilter or not empty toDateFilter}">
              <span>Khoảng ngày: <c:out value="${fromDateFilter}" default="—" /> → <c:out value="${toDateFilter}" default="—" /></span>
            </c:if>
            <span><i class="fa-solid fa-arrow-down-short-wide" aria-hidden="true"></i> Ưu tiên đơn sắp nghỉ</span>
          </div>

          <c:if test="${not empty pageError}">
            <div class="leave-empty leave-list-error" role="alert"><c:out value="${pageError}" /></div>
          </c:if>

          <c:choose>
            <c:when test="${not empty leavePage.items}">
              <div class="leave-table-wrap">
                <table class="leave-table">
                  <thead>
                    <tr>
                      <th scope="col">Nhân viên</th>
                      <th scope="col">Ngày nghỉ / Ca</th>
                      <th scope="col">Loại nghỉ</th>
                      <th scope="col">Trạng thái</th>
                      <th scope="col">Kiểm tra</th>
                      <th scope="col">Người xử lý</th>
                      <th scope="col" class="leave-actions-cell">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    <c:forEach var="item" items="${leavePage.items}">
                      <tr class="${item.nearLeaveDate ? 'leave-row-near' : ''}">

                        <%-- Nhân viên: tên, mã và chức vụ lấy từ LeaveRequestItemView. --%>
                        <td>
                          <div class="leave-employee-cell">
                            <c:choose>
                              <c:when test="${not empty item.avatarUrl}">
                                <img src="${pageContext.request.contextPath}<c:out value='${item.avatarUrl}' />" alt="" class="leave-employee-avatar">
                              </c:when>
                              <c:otherwise>
                                <span class="leave-employee-initial" aria-hidden="true"><c:out value="${fn:substring(item.fullName, 0, 1)}" /></span>
                              </c:otherwise>
                            </c:choose>
                            <div class="leave-employee-meta">
                              <b><c:out value="${item.fullName}" /></b>
                              <small><c:out value="${item.employeeCode}" /> · ${item.position == 'PT' ? 'PT' : 'Lễ tân'}</small>
                            </div>
                          </div>
                        </td>

                        <%-- Ngày và ca: không tự bổ sung giờ khi chưa có cấu hình ca. --%>
                        <td>
                          <div class="leave-date-range">
                            <span>${gms:formatDate(item.fromDate)}</span>
                            <c:if test="${item.fromDate != item.toDate}">
                              <span>→ ${gms:formatDate(item.toDate)}</span>
                            </c:if>
                          </div>
                          <small><c:out value="${item.shiftType.displayName}" /> · <fmt:formatNumber value="${item.days}" maxFractionDigits="1" /> ngày</small>
                        </td>

                        <%-- Loại nghỉ chỉ hiện tên; lý do đầy đủ nằm trong trang chi tiết. --%>
                        <td>
                          <span class="leave-type-label">
                            <c:choose>
                              <c:when test="${item.leaveType.dbValue == 'Annual'}">Phép năm</c:when>
                              <c:when test="${item.leaveType.dbValue == 'Sick'}">Nghỉ ốm</c:when>
                              <c:otherwise>Không lương</c:otherwise>
                            </c:choose>
                          </span>
                        </td>

                        <td>
                          <span class="badge ${item.status.dbValue == 'Pending' ? 'badge-warning' : item.status.dbValue == 'Approved' ? 'badge-success' : 'badge-danger'}">
                            <c:out value="${item.status.displayName}" />
                          </span>
                          <c:if test="${item.nearLeaveDate}">
                            <span class="leave-near-label">
                              <i class="fa-solid fa-calendar-days" aria-hidden="true"></i>
                              Sắp đến ngày nghỉ
                            </span>
                          </c:if>
                        </td>

                        <%-- Kiểm tra do Service tính; rê chuột để xem lý do, mở chi tiết để xử lý. --%>
                        <td>
                          <c:choose>
                            <c:when test="${item.status.dbValue == 'Pending'}">
                              <span class="badge ${item.checkSummary == 'Đạt' ? 'badge-success' : 'badge-warning'}" title="<c:out value='${item.checkMessage}' />">
                                <c:out value="${item.checkSummary}" />
                              </span>
                            </c:when>
                            <c:otherwise><span class="leave-text-muted">—</span></c:otherwise>
                          </c:choose>
                        </td>

                        <%-- Pending hiện người được giao; đơn đã xử lý hiện người thực hiện. Không tự tạo tên quản lý. --%>
                        <td>
                          <c:choose>
                            <c:when test="${item.status.dbValue == 'Pending'}">
                              <c:choose>
                                <c:when test="${not empty item.assignedToName}"><span class="leave-handler-name"><c:out value="${item.assignedToName}" /></span></c:when>
                                <c:otherwise><span class="leave-unassigned">Chưa phân công</span></c:otherwise>
                              </c:choose>
                            </c:when>
                            <c:when test="${not empty item.reviewerName}"><span class="leave-handler-name"><c:out value="${item.reviewerName}" /></span></c:when>
                            <c:when test="${item.autoRejected}"><span class="leave-text-muted">Hệ thống</span></c:when>
                            <c:otherwise><span class="leave-text-muted">—</span></c:otherwise>
                          </c:choose>
                        </td>

                        <td class="leave-actions-cell">
                          <a class="btn btn-sm btn-outline" href="${pageContext.request.contextPath}/manager/leave-requests/review?id=${item.leaveRequestId}">Xem chi tiết</a>
                        </td>

                      </tr>
                    </c:forEach>
                  </tbody>
                </table>
              </div>
            </c:when>
            <c:otherwise>
              <div class="leave-empty">Không tìm thấy đơn nghỉ phép nào phù hợp điều kiện lọc.</div>
            </c:otherwise>
          </c:choose>

          <%-- Phân trang --%>
          <c:if test="${leavePage.totalPages > 1}">
            <div class="leave-pager">
              <c:if test="${leavePage.hasPrevious}">
                <c:url var="previousUrl" value="/manager/leave-requests">
                  <c:param name="page" value="${leavePage.pageNumber - 1}" />
                  <c:param name="pageSize" value="${leavePage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:if test="${not empty positionFilter}"><c:param name="position" value="${positionFilter}" /></c:if>
                  <c:if test="${not empty leaveTypeFilter}"><c:param name="leaveType" value="${leaveTypeFilter}" /></c:if>
                  <c:if test="${not empty fromDateFilter}"><c:param name="fromDate" value="${fromDateFilter}" /></c:if>
                  <c:if test="${not empty toDateFilter}"><c:param name="toDate" value="${toDateFilter}" /></c:if>
                </c:url>
                <a class="btn btn-ghost btn-sm" href="${previousUrl}">Trang trước</a>
              </c:if>

              <span>Trang ${leavePage.pageNumber} / ${leavePage.totalPages} (${leavePage.totalItems} đơn)</span>

              <c:if test="${leavePage.hasNext}">
                <c:url var="nextUrl" value="/manager/leave-requests">
                  <c:param name="page" value="${leavePage.pageNumber + 1}" />
                  <c:param name="pageSize" value="${leavePage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:if test="${not empty positionFilter}"><c:param name="position" value="${positionFilter}" /></c:if>
                  <c:if test="${not empty leaveTypeFilter}"><c:param name="leaveType" value="${leaveTypeFilter}" /></c:if>
                  <c:if test="${not empty fromDateFilter}"><c:param name="fromDate" value="${fromDateFilter}" /></c:if>
                  <c:if test="${not empty toDateFilter}"><c:param name="toDate" value="${toDateFilter}" /></c:if>
                </c:url>
                <a class="btn btn-ghost btn-sm" href="${nextUrl}">Trang sau</a>
              </c:if>
            </div>
          </c:if>

        </section>

      </main>
    </div>
  </div>

</body>
</html>
