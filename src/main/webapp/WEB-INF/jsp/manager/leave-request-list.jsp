<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
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
            <c:if test="${not empty positionFilter}">
              <c:param name="position" value="${positionFilter}" />
            </c:if>
            <c:if test="${not empty leaveTypeFilter}">
              <c:param name="leaveType" value="${leaveTypeFilter}" />
            </c:if>
            <c:if test="${not empty fromDateFilter}">
              <c:param name="fromDate" value="${fromDateFilter}" />
            </c:if>
            <c:if test="${not empty toDateFilter}">
              <c:param name="toDate" value="${toDateFilter}" />
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
            <c:if test="${not empty positionFilter}">
              <c:param name="position" value="${positionFilter}" />
            </c:if>
            <c:if test="${not empty leaveTypeFilter}">
              <c:param name="leaveType" value="${leaveTypeFilter}" />
            </c:if>
            <c:if test="${not empty fromDateFilter}">
              <c:param name="fromDate" value="${fromDateFilter}" />
            </c:if>
            <c:if test="${not empty toDateFilter}">
              <c:param name="toDate" value="${toDateFilter}" />
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
            <c:if test="${not empty positionFilter}">
              <c:param name="position" value="${positionFilter}" />
            </c:if>
            <c:if test="${not empty leaveTypeFilter}">
              <c:param name="leaveType" value="${leaveTypeFilter}" />
            </c:if>
            <c:if test="${not empty fromDateFilter}">
              <c:param name="fromDate" value="${fromDateFilter}" />
            </c:if>
            <c:if test="${not empty toDateFilter}">
              <c:param name="toDate" value="${toDateFilter}" />
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
            <c:if test="${not empty positionFilter}">
              <c:param name="position" value="${positionFilter}" />
            </c:if>
            <c:if test="${not empty leaveTypeFilter}">
              <c:param name="leaveType" value="${leaveTypeFilter}" />
            </c:if>
            <c:if test="${not empty fromDateFilter}">
              <c:param name="fromDate" value="${fromDateFilter}" />
            </c:if>
            <c:if test="${not empty toDateFilter}">
              <c:param name="toDate" value="${toDateFilter}" />
            </c:if>
          </c:url>
          <a href="${rejectedTabUrl}"
             class="leave-stat ${statusFilter == 'Rejected' ? 'active' : ''}">
            <span>Từ chối</span>
            <strong><c:out value="${statusCounts['Rejected']}" default="0" /></strong>
          </a>

        </div>

        <%-- ===== Bộ lọc và danh sách (leavePage: PageResult<LeaveRequestItemView> do Service tạo; keyword: từ khóa đang nhập) ===== --%>
        <section class="panel leave-panel">
          <div class="panel-head">
            <div>
              <h2>Danh sách đơn nghỉ phép</h2>
              <c:if test="${statusFilter == 'All'}">
                <p>Đơn Chờ duyệt được ưu tiên hiển thị trước.</p>
              </c:if>
            </div>
          </div>

          <%-- Form lọc: status giữ tab đang chọn, keyword tìm theo tên nhân viên, chức vụ, loại nghỉ, khoảng ngày --%>
          <form action="${pageContext.request.contextPath}/manager/leave-requests"
                method="get"
                class="leave-filter">

            <input type="hidden" name="status" value="<c:out value='${statusFilter}' />">

            <input type="search"
                   name="keyword"
                   class="filter-input-search"
                   maxlength="100"
                   value="<c:out value='${keyword}' />"
                   placeholder="Tìm theo tên nhân viên">

            <select name="position" class="filter-select" aria-label="Lọc theo chức vụ">
              <option value="">-- Tất cả chức vụ --</option>
              <option value="PT" ${positionFilter == 'PT' ? 'selected' : ''}>PT (HLV)</option>
              <option value="Receptionist" ${positionFilter == 'Receptionist' ? 'selected' : ''}>Lễ tân</option>
            </select>

            <select name="leaveType" class="filter-select" aria-label="Lọc theo loại nghỉ">
              <option value="">-- Tất cả loại nghỉ --</option>
              <option value="Annual" ${leaveTypeFilter == 'Annual' ? 'selected' : ''}>Nghỉ phép năm</option>
              <option value="Sick" ${leaveTypeFilter == 'Sick' ? 'selected' : ''}>Nghỉ ốm</option>
              <option value="Unpaid" ${leaveTypeFilter == 'Unpaid' ? 'selected' : ''}>Nghỉ không lương</option>
            </select>

            <div class="filter-date-group">
              <input type="date" name="fromDate" value="<c:out value='${fromDateFilter}' />" title="Từ ngày">
              <span>-</span>
              <input type="date" name="toDate" value="<c:out value='${toDateFilter}' />" title="Đến ngày">
            </div>

            <button class="btn btn-primary" type="submit">Tìm</button>
            <a class="btn btn-ghost"
               href="${pageContext.request.contextPath}/manager/leave-requests<c:if test="${not empty statusFilter and statusFilter != 'Pending'}">?status=<c:out value='${statusFilter}' /></c:if>">
              Xóa lọc
            </a>
          </form>

          <%-- ===== Thông báo lỗi tải danh sách (pageError: Servlet đặt khi Service trả lỗi, không có thì không hiện) ===== --%>
          <c:if test="${not empty pageError}">
            <div class="leave-empty" role="alert"><c:out value="${pageError}" /></div>
          </c:if>

          <%-- ===== Bảng danh sách (leavePage.items: List<LeaveRequestItemView>, mỗi dòng 1 đơn) ===== --%>
          <c:choose>
            <c:when test="${not empty leavePage.items}">
              <div class="leave-table-wrap">
                <table class="leave-table">
                  <thead>
                    <tr>
                      <th>Nhân viên</th>
                      <th>Ngày nghỉ</th>
                      <th>Loại nghỉ</th>
                      <th>Trạng thái</th>
                      <th>Hạn duyệt</th>
                      <th></th>
                    </tr>
                  </thead>
                  <tbody>
                    <c:forEach var="item" items="${leavePage.items}">
                      <%-- item.urgent: Service tính, đơn Chờ duyệt sắp quá hạn thì tô nổi dòng --%>
                      <tr class="${item.urgent ? 'leave-row-urgent' : ''}">

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
                                <c:out value="${item.employeeCode}" />
                                ·
                                <span class="leave-text-nowrap">
                                <c:choose>
                                  <c:when test="${item.position == 'PT'}">Huấn luyện viên (PT)</c:when>
                                  <c:when test="${item.position == 'Receptionist'}">Lễ tân</c:when>
                                  <c:otherwise><c:out value="${item.position}" /></c:otherwise>
                                </c:choose>
                                </span>
                              </small>
                            </div>
                          </div>
                        </td>

                        <td>
                          <b>${gms:formatDate(item.leaveDate)}</b>
                          <small><c:out value="${item.shiftType.displayName}" /></small>
                        </td>

                        <td>
                          <b><c:out value="${item.leaveType.displayName}" /></b>
                          <c:if test="${not empty item.reason}">
                            <small><c:out value="${item.reason}" /></small>
                          </c:if>
                        </td>

                        <td>
                          <span class="badge
                            ${item.status.dbValue == 'Pending' ? 'badge-warning' :
                              item.status.dbValue == 'Approved' ? 'badge-success' : 'badge-danger'}">
                            <c:out value="${item.status.displayName}" />
                          </span>
                          <%-- item.autoRejected: Rejected mà không có người duyệt (quá hạn duyệt) --%>
                          <c:if test="${item.autoRejected}">
                            <small class="leave-text-muted leave-text-nowrap">Tự động: quá hạn duyệt</small>
                          </c:if>
                        </td>

                        <td>
                          <%-- deadlineCountdownText chỉ có với đơn Chờ duyệt, đơn đã xử lý để trống --%>
                          <c:choose>
                            <c:when test="${not empty item.deadlineCountdownText}">
                              <b><c:out value="${item.deadlineCountdownText}" /></b>
                              <c:if test="${not empty item.approvalDeadline}">
                                <small class="leave-text-muted">
                                  Trước ${gms:formatDateTime(item.approvalDeadline)}
                                </small>
                              </c:if>
                            </c:when>
                            <c:otherwise>—</c:otherwise>
                          </c:choose>
                        </td>

                        <td>
                          <%-- Chỉ cho xử lý khi đơn còn Pending và chưa quá hạn P-20 --%>
                          <c:set var="canProcess" value="${item.status.dbValue == 'Pending' and not item.overdue}" />
                          <a class="btn btn-sm ${canProcess ? 'btn-primary' : 'btn-outline'}"
                             href="${pageContext.request.contextPath}/manager/leave-requests/review?id=${item.leaveRequestId}">
                            ${canProcess ? 'Xử lý' : 'Xem'}
                          </a>
                        </td>

                      </tr>
                    </c:forEach>
                  </tbody>
                </table>
              </div>
            </c:when>

            <%-- Rỗng: tải được nhưng không có đơn nào khớp bộ lọc --%>
            <c:otherwise>
              <div class="leave-empty">Không có đơn nghỉ phép nào.</div>
            </c:otherwise>
          </c:choose>

          <%-- ===== Phân trang (leavePage.totalPages, hasPrevious, hasNext do PageResult cung cấp; link giữ keyword, status, pageSize) ===== --%>
          <c:if test="${leavePage.totalPages > 1}">
            <div class="leave-pager">

              <c:if test="${leavePage.hasPrevious}">
                <c:url var="previousUrl" value="/manager/leave-requests">
                  <c:param name="page" value="${leavePage.pageNumber - 1}" />
                  <c:param name="pageSize" value="${leavePage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:if test="${not empty positionFilter}">
                    <c:param name="position" value="${positionFilter}" />
                  </c:if>
                  <c:if test="${not empty leaveTypeFilter}">
                    <c:param name="leaveType" value="${leaveTypeFilter}" />
                  </c:if>
                  <c:if test="${not empty fromDateFilter}">
                    <c:param name="fromDate" value="${fromDateFilter}" />
                  </c:if>
                  <c:if test="${not empty toDateFilter}">
                    <c:param name="toDate" value="${toDateFilter}" />
                  </c:if>
                </c:url>
                <a class="btn btn-ghost btn-sm" href="${previousUrl}">Trang trước</a>
              </c:if>

              <span>Trang ${leavePage.pageNumber} / ${leavePage.totalPages}</span>

              <c:if test="${leavePage.hasNext}">
                <c:url var="nextUrl" value="/manager/leave-requests">
                  <c:param name="page" value="${leavePage.pageNumber + 1}" />
                  <c:param name="pageSize" value="${leavePage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:if test="${not empty positionFilter}">
                    <c:param name="position" value="${positionFilter}" />
                  </c:if>
                  <c:if test="${not empty leaveTypeFilter}">
                    <c:param name="leaveType" value="${leaveTypeFilter}" />
                  </c:if>
                  <c:if test="${not empty fromDateFilter}">
                    <c:param name="fromDate" value="${fromDateFilter}" />
                  </c:if>
                  <c:if test="${not empty toDateFilter}">
                    <c:param name="toDate" value="${toDateFilter}" />
                  </c:if>
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
