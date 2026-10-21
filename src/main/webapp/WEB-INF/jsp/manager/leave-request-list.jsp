<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Quản lý đơn nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
</head>

<body class="dock theme-manager">
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

        <%-- ===== Thanh công cụ gom nhóm: Tabs trạng thái bên trái + Bộ lọc bên phải ===== --%>
        <div class="leave-toolbar">

          <%-- 4 Tab trạng thái --%>
          <div class="leave-tabs-nav">
            <c:url var="pendingTabUrl" value="/manager/leave-requests">
              <c:param name="status" value="Pending" />
              <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
              <c:if test="${not empty positionFilter}"><c:param name="position" value="${positionFilter}" /></c:if>
              <c:if test="${not empty leaveTypeFilter}"><c:param name="leaveType" value="${leaveTypeFilter}" /></c:if>
            </c:url>
            <a href="${pendingTabUrl}" class="leave-tab-btn ${statusFilter == 'Pending' ? 'active' : ''}">
              <span>Chờ duyệt</span>
              <span class="leave-tab-count"><c:out value="${statusCounts['Pending'] != null ? statusCounts['Pending'] : 0}" /></span>
            </a>

            <c:url var="approvedTabUrl" value="/manager/leave-requests">
              <c:param name="status" value="Approved" />
              <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
              <c:if test="${not empty positionFilter}"><c:param name="position" value="${positionFilter}" /></c:if>
              <c:if test="${not empty leaveTypeFilter}"><c:param name="leaveType" value="${leaveTypeFilter}" /></c:if>
            </c:url>
            <a href="${approvedTabUrl}" class="leave-tab-btn ${statusFilter == 'Approved' ? 'active' : ''}">
              <span>Đã duyệt</span>
              <span class="leave-tab-count"><c:out value="${statusCounts['Approved'] != null ? statusCounts['Approved'] : 0}" /></span>
            </a>

            <c:url var="rejectedTabUrl" value="/manager/leave-requests">
              <c:param name="status" value="Rejected" />
              <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
              <c:if test="${not empty positionFilter}"><c:param name="position" value="${positionFilter}" /></c:if>
              <c:if test="${not empty leaveTypeFilter}"><c:param name="leaveType" value="${leaveTypeFilter}" /></c:if>
            </c:url>
            <a href="${rejectedTabUrl}" class="leave-tab-btn ${statusFilter == 'Rejected' ? 'active' : ''}">
              <span>Từ chối</span>
              <span class="leave-tab-count"><c:out value="${statusCounts['Rejected'] != null ? statusCounts['Rejected'] : 0}" /></span>
            </a>

            <c:url var="allTabUrl" value="/manager/leave-requests">
              <c:param name="status" value="All" />
              <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
              <c:if test="${not empty positionFilter}"><c:param name="position" value="${positionFilter}" /></c:if>
              <c:if test="${not empty leaveTypeFilter}"><c:param name="leaveType" value="${leaveTypeFilter}" /></c:if>
            </c:url>
            <a href="${allTabUrl}" class="leave-tab-btn ${statusFilter == 'All' ? 'active' : ''}">
              <span>Tất cả</span>
              <span class="leave-tab-count"><c:out value="${statusCounts['All'] != null ? statusCounts['All'] : 0}" /></span>
            </a>
          </div>

          <%-- Form lọc tìm kiếm --%>
          <form action="${pageContext.request.contextPath}/manager/leave-requests" method="get" class="leave-filter-nav">
            <input type="hidden" name="status" value="<c:out value='${statusFilter}' />">

            <select name="position" class="filter-select" aria-label="Lọc theo chức vụ">
              <option value="">Tất cả chức vụ</option>
              <option value="PT" ${positionFilter == 'PT' ? 'selected' : ''}>Huấn luyện viên (PT)</option>
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
                   placeholder="Tìm theo tên / mã nhân viên...">

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

        <p class="leave-help">Ưu tiên hạn xử lý gần nhất. Kiểm tra là snapshot; hệ thống kiểm tra lại trước khi ghi quyết định.</p>
        <%-- ===== Bảng danh sách đơn nghỉ phép ===== --%>
        <section class="panel leave-panel" style="padding:0;overflow:hidden">

          <c:if test="${not empty pageError}">
            <div class="leave-empty" role="alert" style="color:var(--color-danger-ink)"><c:out value="${pageError}" /></div>
          </c:if>

          <c:choose>
            <c:when test="${not empty leavePage.items}">
              <div class="leave-table-wrap">
                <table class="leave-table">
                  <thead>
                    <tr>
                      <th style="width:25%">Nhân viên</th>
                      <th style="width:20%">Thời gian nghỉ</th>
                      <th style="width:13%">Loại nghỉ</th>
                      <th style="width:14%">Trạng thái</th>
                      <th>Kiểm tra</th>
                      <th style="width:14%">Người duyệt</th>
                      <th style="width:14%;text-align:right">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    <c:forEach var="item" items="${leavePage.items}">
                      <tr class="${item.urgent or item.overdue ? 'leave-row-urgent' : ''}">

                        <%-- Cột 1: Nhân viên --%>
                        <td>
                          <div class="leave-employee-cell">
                            <c:choose>
                              <c:when test="${not empty item.avatarUrl}">
                                <img src="${pageContext.request.contextPath}${item.avatarUrl}" alt="Avatar" class="leave-employee-avatar">
                              </c:when>
                              <c:otherwise>
                                <span class="leave-employee-initial">${fn:substring(item.fullName, 0, 1)}</span>
                              </c:otherwise>
                            </c:choose>
                            <div class="leave-employee-meta">
                              <b><c:out value="${item.fullName}" /></b>
                              <small>
                                <c:out value="${item.employeeCode}" /> ·
                                <span class="badge badge-sm ${item.position == 'PT' ? 'badge-info' : 'badge-neutral'}">
                                  ${item.position == 'PT' ? 'PT' : 'Lễ tân'}
                                </span>
                              </small>
                            </div>
                          </div>
                        </td>

                        <%-- Cột 2: Thời gian nghỉ --%>
                        <td>
                          <div style="font-weight:600;font-size:var(--font-size-sm)">
                            ${gms:formatDate(item.fromDate)}
                            <c:if test="${item.fromDate != item.toDate}">
                              → ${gms:formatDate(item.toDate)}
                            </c:if>
                            <span style="font-size:11px;color:var(--color-text-soft);font-weight:normal">(${item.days} ngày)</span>
                          </div>
                          <small style="color:var(--color-text-soft)">
                            <c:out value="${item.shiftType.displayName}" />
                          </small>
                        </td>

                        <%-- Cột 3: Loại nghỉ --%>
                        <td>
                          <span class="badge ${item.leaveType.dbValue == 'Annual' ? 'badge-success' : (item.leaveType.dbValue == 'Sick' ? 'badge-info' : 'badge-neutral')}">
                            <c:out value="${item.leaveType.displayName}" />
                          </span>
                          <c:if test="${not empty item.reason}">
                            <div style="font-size:11px;color:var(--color-text-soft);margin-top:2px;max-width:160px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis" title="<c:out value="${item.reason}" />">
                              <c:out value="${item.reason}" />
                            </div>
                          </c:if>
                        </td>

                        <%-- Cột 4: Trạng thái --%>
                        <td>
                          <span class="badge
                            ${item.status.dbValue == 'Pending' ? 'badge-warning' :
                              item.status.dbValue == 'Approved' ? 'badge-success' : 'badge-danger'}">
                            <c:out value="${item.status.displayName}" />
                          </span>
                          <c:if test="${item.status.dbValue == 'Pending' and (item.urgent or item.overdue)}">
                            <span class="badge badge-danger">${item.overdue ? 'Quá hạn' : 'Sắp quá hạn'}</span>
                          </c:if>
                          <c:if test="${item.autoRejected}">
                            <div style="font-size:10px;color:var(--color-danger-ink);margin-top:2px">Quá hạn duyệt SLA</div>
                          </c:if>
                          <c:if test="${item.status.dbValue == 'Pending' and not empty item.deadlineCountdownText}">
                            <div style="font-size:11px;color:${item.urgent ? 'var(--color-danger-ink);font-weight:600' : 'var(--color-text-soft)'};margin-top:2px">
                              <c:out value="${item.deadlineCountdownText}" />
                            </div>
                          </c:if>
                        </td>

                        <td title="<c:out value='${item.checkMessage}' />">
                          <c:choose>
                            <c:when test="${item.status.dbValue == 'Pending'}">
                              <span class="badge ${item.checkSummary == 'Đạt' ? 'badge-success' : 'badge-warning'}"><c:out value="${item.checkSummary}" /></span>
                            </c:when>
                            <c:otherwise>Đã xử lý</c:otherwise>
                          </c:choose>
                        </td>
                        <%-- Cột 5: Người duyệt / Người được giao --%>
                        <td>
                          <c:choose>
                            <c:when test="${not empty item.reviewerName}">
                              <div style="font-size:var(--font-size-sm);font-weight:500"><c:out value="${item.reviewerName}" /></div>
                              <small style="font-size:11px;color:var(--color-text-soft)">Đã xử lý</small>
                            </c:when>
                            <c:when test="${not empty item.assignedToName}">
                              <div style="font-size:var(--font-size-sm);font-weight:500"><c:out value="${item.assignedToName}" /></div>
                              <small style="font-size:11px;color:var(--color-warning-ink)">Được giao duyệt</small>
                            </c:when>
                            <c:otherwise>
                              <span style="font-size:var(--font-size-xs);color:var(--color-text-soft)">Quản lý chung</span>
                            </c:otherwise>
                          </c:choose>
                        </td>

                        <%-- Cột 6: Thao tác --%>
                        <td style="text-align:right">
                          <a class="btn btn-sm btn-outline"
                             href="${pageContext.request.contextPath}/manager/leave-requests/review?id=${item.leaveRequestId}">
                            Xem chi tiết
                          </a>
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
            <div class="leave-pager" style="padding:var(--space-4)">
              <c:if test="${leavePage.hasPrevious}">
                <c:url var="previousUrl" value="/manager/leave-requests">
                  <c:param name="page" value="${leavePage.pageNumber - 1}" />
                  <c:param name="pageSize" value="${leavePage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:if test="${not empty positionFilter}"><c:param name="position" value="${positionFilter}" /></c:if>
                  <c:if test="${not empty leaveTypeFilter}"><c:param name="leaveType" value="${leaveTypeFilter}" /></c:if>
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
