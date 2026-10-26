<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Danh sách hội viên · Super Gym Quản lý</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/member-list.css">
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
          <h1 class="tb-title">Quản lý hội viên</h1>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <a class="tb-user" href="${pageContext.request.contextPath}/account">
            <span class="avatar"><c:out value="${sessionScope.userInitials}" /></span>
            <span>
              <b><c:out value="${sessionScope.displayName}" default="Quản lý" /></b>
              <%@ include file="/WEB-INF/jsp/common/_user-badge-line.jspf" %>
            </span>
          </a>
        </div>
      </header>

      <main class="content member-page" id="main-content" tabindex="-1">
        <%-- Header tiêu đề --%>
        <div class="member-header-row">
          <div class="member-title-group">
            <h2>Danh sách hội viên</h2>
            <p>Tra cứu hồ sơ khách hàng, lịch sử tham gia và phê duyệt yêu cầu xóa hồ sơ</p>
          </div>
        </div>

        <%-- Tabs điều hướng --%>
        <nav class="member-tabs-nav" aria-label="Tab hội viên">
          <a href="${pageContext.request.contextPath}/manager/members?tab=all"
             class="member-tab-btn<c:if test="${currentTab == 'all'}"> active</c:if>">
            <span>Tất cả hội viên</span>
            <c:if test="${currentTab == 'all'}">
              <span class="member-tab-badge"><c:out value="${totalCount}" /></span>
            </c:if>
          </a>
          <a href="${pageContext.request.contextPath}/manager/members?tab=pending-deletions"
             class="member-tab-btn<c:if test="${currentTab == 'pending-deletions'}"> active</c:if>">
            <span>Chờ duyệt xóa</span>
            <span class="member-tab-badge<c:if test="${pendingBadgeCount > 0}"> badge-highlight</c:if>">
              <c:out value="${pendingBadgeCount}" />
            </span>
          </a>
        </nav>

        <c:choose>
          <%-- ================= TAB 1: TẤT CẢ HỘI VIÊN ================= --%>
          <c:when test="${currentTab == 'all'}">
            <%-- Bộ lọc --%>
            <section class="member-filter-card">
              <form action="${pageContext.request.contextPath}/manager/members" method="get" class="member-filter-form">
                <input type="hidden" name="tab" value="all" />

                <div class="member-search-wrap">
                  <svg class="member-search-icon" viewBox="0 0 24 24">
                    <circle cx="11" cy="11" r="8"></circle>
                    <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                  </svg>
                  <input type="text"
                         name="keyword"
                         class="member-search-input"
                         placeholder="Tìm theo họ tên, SĐT, email, mã KH..."
                         value="<c:out value="${keyword}" />" />
                </div>

                <select name="status" class="member-select">
                  <option value="ALL" <c:if test="${statusFilter == 'ALL'}">selected</c:if>>Tất cả trạng thái</option>
                  <option value="Active" <c:if test="${statusFilter == 'Active'}">selected</c:if>>Đang hoạt động (Active)</option>
                  <option value="Inactive" <c:if test="${statusFilter == 'Inactive'}">selected</c:if>>Đã ngừng hoạt động (Inactive)</option>
                </select>

                <button type="submit" class="btn-primary">Lọc</button>
                <c:if test="${not empty keyword or (not empty statusFilter and statusFilter != 'ALL')}">
                  <a href="${pageContext.request.contextPath}/manager/members?tab=all" class="btn-secondary">Xóa lọc</a>
                </c:if>
              </form>
            </section>

            <%-- Bảng hội viên --%>
            <section class="member-table-card">
              <div class="member-table-wrap">
                <table class="member-table">
                  <thead>
                    <tr>
                      <th>Hội viên</th>
                      <th>Số điện thoại</th>
                      <th>Email</th>
                      <th>Loại khách</th>
                      <th>Gói tập hiện tại</th>
                      <th>Trạng thái</th>
                      <th style="text-align: right;">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    <c:choose>
                      <c:when test="${empty customers}">
                        <tr>
                          <td colspan="7" style="text-align: center; padding: var(--space-8); color: var(--color-text-muted);">
                            Không tìm thấy hội viên nào phù hợp.
                          </td>
                        </tr>
                      </c:when>
                      <c:otherwise>
                        <c:forEach var="m" items="${customers}">
                          <tr>
                            <td>
                              <div class="member-cust-cell">
                                <div class="member-avatar<c:if test="${m.inactive}"> avatar-inactive</c:if>">
                                  <c:out value="${fn:substring(m.fullName, 0, 1)}" />
                                </div>
                                <div>
                                  <div class="member-name"><c:out value="${m.fullName}" /></div>
                                  <div class="member-code"><c:out value="${m.customerCode}" /></div>
                                </div>
                              </div>
                            </td>
                            <td><c:out value="${m.phone}" default="—" /></td>
                            <td><c:out value="${m.email}" default="—" /></td>
                            <td>
                              <c:choose>
                                <c:when test="${m.customerType == 'Dependent'}">Phụ thuộc</c:when>
                                <c:otherwise>Tự đăng ký</c:otherwise>
                              </c:choose>
                            </td>
                            <td>
                              <c:choose>
                                <c:when test="${not empty m.currentPackageName}">
                                  <span class="m-badge-package"><c:out value="${m.currentPackageName}" /></span>
                                </c:when>
                                <c:otherwise>
                                  <span style="color: var(--color-text-muted);">Chưa có gói</span>
                                </c:otherwise>
                              </c:choose>
                            </td>
                            <td>
                              <c:choose>
                                <c:when test="${m.hasPendingDeletion}">
                                  <span class="m-badge m-badge-pending">
                                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                      <circle cx="12" cy="12" r="10"></circle>
                                      <polyline points="12 6 12 12 16 14"></polyline>
                                    </svg>
                                    Chờ xóa (#<c:out value="${m.pendingRequestId}" />)
                                  </span>
                                </c:when>
                                <c:when test="${m.inactive}">
                                  <span class="m-badge m-badge-inactive">Đã ngừng hoạt động</span>
                                </c:when>
                                <c:otherwise>
                                  <span class="m-badge m-badge-active">Hoạt động</span>
                                </c:otherwise>
                              </c:choose>
                            </td>
                            <td style="text-align: right; white-space: nowrap;">
                              <a href="${pageContext.request.contextPath}/manager/members/detail?id=<c:out value="${m.customerId}" />"
                                 class="btn-table-action btn-detail">
                                Chi tiết
                              </a>
                              <c:if test="${m.hasPendingDeletion}">
                                <a href="${pageContext.request.contextPath}/manager/member-deletions/review?id=<c:out value="${m.pendingRequestId}" />"
                                   class="btn-table-action btn-review-deletion" style="margin-left: var(--space-2);">
                                  Duyệt xóa
                                </a>
                              </c:if>
                            </td>
                          </tr>
                        </c:forEach>
                      </c:otherwise>
                    </c:choose>
                  </tbody>
                </table>
              </div>

              <%-- Phân trang tab All --%>
              <c:if test="${totalPages > 1}">
                <div class="pagination-row">
                  <div>Trang <b><c:out value="${page}" /></b> / <c:out value="${totalPages}" /> (Tổng <c:out value="${totalCount}" /> hội viên)</div>
                  <div class="pagination-controls">
                    <a href="${pageContext.request.contextPath}/manager/members?tab=all&page=${page - 1}&pageSize=${pageSize}&keyword=${keyword}&status=${statusFilter}"
                       class="page-link<c:if test="${page <= 1}"> disabled</c:if>">Trước</a>
                    <c:forEach var="p" begin="1" end="${totalPages}">
                      <a href="${pageContext.request.contextPath}/manager/members?tab=all&page=${p}&pageSize=${pageSize}&keyword=${keyword}&status=${statusFilter}"
                         class="page-link<c:if test="${p == page}"> active</c:if>"><c:out value="${p}" /></a>
                    </c:forEach>
                    <a href="${pageContext.request.contextPath}/manager/members?tab=all&page=${page + 1}&pageSize=${pageSize}&keyword=${keyword}&status=${statusFilter}"
                       class="page-link<c:if test="${page >= totalPages}"> disabled</c:if>">Sau</a>
                  </div>
                </div>
              </c:if>
            </section>
          </c:when>

          <%-- ================= TAB 2: CHỜ DUYỆT XÓA ================= --%>
          <c:otherwise>
            <section class="member-table-card">
              <div class="member-table-wrap">
                <table class="member-table">
                  <thead>
                    <tr>
                      <th>Mã đơn</th>
                      <th>Hội viên</th>
                      <th>Lễ tân gửi</th>
                      <th>Lý do yêu cầu</th>
                      <th>Gói &amp; Buổi còn lại</th>
                      <th>Thời điểm gửi</th>
                      <th style="text-align: right;">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    <c:choose>
                      <c:when test="${empty pendingRequests}">
                        <tr>
                          <td colspan="7" style="text-align: center; padding: var(--space-8); color: var(--color-text-muted);">
                            Hiện không có yêu cầu xóa hồ sơ nào đang chờ duyệt.
                          </td>
                        </tr>
                      </c:when>
                      <c:otherwise>
                        <c:forEach var="pr" items="${pendingRequests}">
                          <tr>
                            <td><b>#<c:out value="${pr.deletionRequest.customerDeletionRequestId}" /></b></td>
                            <td>
                              <div class="member-cust-cell">
                                <div class="member-avatar">
                                  <c:out value="${fn:substring(pr.customerFullName, 0, 1)}" />
                                </div>
                                <div>
                                  <div class="member-name"><c:out value="${pr.customerFullName}" /></div>
                                  <div class="member-code"><c:out value="${pr.customerCode}" /></div>
                                </div>
                              </div>
                            </td>
                            <td>
                              <div><c:out value="${pr.requesterName}" /></div>
                              <small style="color: var(--color-text-muted);"><c:out value="${pr.requesterCode}" /></small>
                            </td>
                            <td><c:out value="${pr.deletionRequest.reason}" /></td>
                            <td>
                              <c:choose>
                                <c:when test="${pr.hasActiveAssets()}">
                                  <span class="m-badge m-badge-pending">
                                    <c:out value="${fn:length(pr.activePackages)}" /> gói · <c:out value="${fn:length(pr.scheduledSessions)}" /> buổi PT
                                  </span>
                                </c:when>
                                <c:otherwise>
                                  <span style="color: var(--color-text-muted);">Không còn gói</span>
                                </c:otherwise>
                              </c:choose>
                            </td>
                            <td><c:out value="${pr.deletionRequest.requestedAt}" /></td>
                            <td style="text-align: right;">
                              <a href="${pageContext.request.contextPath}/manager/member-deletions/review?id=<c:out value="${pr.deletionRequest.customerDeletionRequestId}" />"
                                 class="btn-table-action btn-review-deletion">
                                Xem xét &amp; Duyệt
                              </a>
                            </td>
                          </tr>
                        </c:forEach>
                      </c:otherwise>
                    </c:choose>
                  </tbody>
                </table>
              </div>

              <%-- Phân trang tab Pending --%>
              <c:if test="${totalPages > 1}">
                <div class="pagination-row">
                  <div>Trang <b><c:out value="${page}" /></b> / <c:out value="${totalPages}" /> (Tổng <c:out value="${totalCount}" /> yêu cầu chờ duyệt)</div>
                  <div class="pagination-controls">
                    <a href="${pageContext.request.contextPath}/manager/members?tab=pending-deletions&page=${page - 1}&pageSize=${pageSize}"
                       class="page-link<c:if test="${page <= 1}"> disabled</c:if>">Trước</a>
                    <c:forEach var="p" begin="1" end="${totalPages}">
                      <a href="${pageContext.request.contextPath}/manager/members?tab=pending-deletions&page=${p}&pageSize=${pageSize}"
                         class="page-link<c:if test="${p == page}"> active</c:if>"><c:out value="${p}" /></a>
                    </c:forEach>
                    <a href="${pageContext.request.contextPath}/manager/members?tab=pending-deletions&page=${page + 1}&pageSize=${pageSize}"
                       class="page-link<c:if test="${page >= totalPages}"> disabled</c:if>">Sau</a>
                  </div>
                </div>
              </c:if>
            </section>
          </c:otherwise>
        </c:choose>
      </main>
    </div>
  </div>
</body>
</html>
