<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Hồ sơ hội viên · <c:out value="${customer.customerCode}" /> · Super Gym Quản lý</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/member-list.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/member-detail.css">
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
          <h1 class="tb-title">Chi tiết hồ sơ hội viên</h1>
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

      <main class="content member-detail-page" id="main-content" tabindex="-1">
        <%-- Breadcrumb --%>
        <nav class="m-breadcrumb-nav" aria-label="Đường dẫn">
          <a href="${pageContext.request.contextPath}/manager/members">Danh sách hội viên</a>
          <span>/</span>
          <span><c:out value="${customer.customerCode}" /></span>
        </nav>

        <div class="m-detail-grid">
          <%-- Cột trái: Thông tin định danh & Gói tập --%>
          <div style="display: flex; flex-direction: column; gap: var(--space-5);">
            <div class="m-card">
              <div class="m-card-header">
                <h3>Thông tin định danh</h3>
                <c:choose>
                  <c:when test="${customer.status == 'Inactive'}">
                    <span class="m-badge m-badge-inactive">Đã ngừng hoạt động</span>
                  </c:when>
                  <c:otherwise>
                    <span class="m-badge m-badge-active"><c:out value="${customer.status}" /></span>
                  </c:otherwise>
                </c:choose>
              </div>

              <div class="m-info-list">
                <div class="m-info-item">
                  <span class="m-info-label">Mã khách hàng</span>
                  <span class="m-info-value"><b><c:out value="${customer.customerCode}" /></b></span>
                </div>
                <div class="m-info-item">
                  <span class="m-info-label">Họ và tên</span>
                  <span class="m-info-value"><c:out value="${person.fullName}" /></span>
                </div>
                <div class="m-info-item">
                  <span class="m-info-label">Số điện thoại</span>
                  <span class="m-info-value"><c:out value="${person.phone}" default="—" /></span>
                </div>
                <div class="m-info-item">
                  <span class="m-info-label">Email</span>
                  <span class="m-info-value"><c:out value="${person.email}" default="—" /></span>
                </div>
                <div class="m-info-item">
                  <span class="m-info-label">Loại hồ sơ</span>
                  <span class="m-info-value">
                    <c:choose>
                      <c:when test="${customer.customerType == 'Dependent'}">Hồ sơ phụ thuộc (Dependent)</c:when>
                      <c:otherwise>Hồ sơ cá nhân (Self)</c:otherwise>
                    </c:choose>
                  </span>
                </div>
                <div class="m-info-item">
                  <span class="m-info-label">Ngày tham gia</span>
                  <span class="m-info-value"><c:out value="${customer.joinedAt}" /></span>
                </div>
                <c:if test="${customer.status == 'Inactive'}">
                  <div class="m-info-item">
                    <span class="m-info-label">Thời điểm xóa hồ sơ</span>
                    <span class="m-info-value" style="color: var(--color-danger);"><c:out value="${customer.deletedAt}" /></span>
                  </div>
                </c:if>
              </div>
            </div>

            <%-- Gói tập và buổi PT --%>
            <div class="m-card">
              <div class="m-card-header">
                <h3>Gói tập &amp; Buổi PT đang hiệu lực</h3>
              </div>

              <c:choose>
                <c:when test="${not empty activePackages or not empty scheduledSessions}">
                  <c:if test="${not empty activePackages}">
                    <div>
                      <h4 style="margin: 0 0 var(--space-2) 0; font-size: var(--font-size-sm); color: var(--color-text-secondary);">Gói tập đang chạy:</h4>
                      <ul style="font-size: var(--font-size-sm); color: var(--color-text-primary); padding-left: var(--space-4); margin: 0;">
                        <c:forEach var="pkg" items="${activePackages}">
                          <li style="margin-bottom: var(--space-1);">
                            <b><c:out value="${pkg.packageName}" /></b> (<c:out value="${pkg.packageType}" />)
                            — Hạn đến: <c:out value="${pkg.endDate}" />
                            <c:if test="${pkg.partner}"><span class="m-badge m-badge-package">Gói đôi</span></c:if>
                          </li>
                        </c:forEach>
                      </ul>
                    </div>
                  </c:if>

                  <c:if test="${not empty scheduledSessions}">
                    <div style="margin-top: var(--space-3);">
                      <h4 style="margin: 0 0 var(--space-2) 0; font-size: var(--font-size-sm); color: var(--color-text-secondary);">Buổi PT đã xếp lịch:</h4>
                      <ul style="font-size: var(--font-size-sm); color: var(--color-text-primary); padding-left: var(--space-4); margin: 0;">
                        <c:forEach var="sess" items="${scheduledSessions}">
                          <li style="margin-bottom: var(--space-1);">
                            Ngày <c:out value="${sess.slotDate}" /> lúc <c:out value="${sess.startTime}" /> — PT: <c:out value="${sess.ptName}" />
                          </li>
                        </c:forEach>
                      </ul>
                    </div>
                  </c:if>
                </c:when>
                <c:otherwise>
                  <p style="color: var(--color-text-muted); margin: 0; font-size: var(--font-size-sm);">
                    Hội viên không còn gói tập hoạt động hoặc buổi PT nào.
                  </p>
                </c:otherwise>
              </c:choose>
            </div>
          </div>

          <%-- Cột phải: Trạng thái yêu cầu xóa hoặc cảnh báo Inactive --%>
          <div style="display: flex; flex-direction: column; gap: var(--space-4);">
            <c:if test="${not empty pendingRequest}">
              <div class="pending-notice-card">
                <div class="pending-notice-title">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10"></circle>
                    <polyline points="12 6 12 12 16 14"></polyline>
                  </svg>
                  Yêu cầu xóa chờ phê duyệt
                </div>
                <p class="pending-notice-desc">
                  Hồ sơ này đang có đơn yêu cầu xóa <b>#<c:out value="${pendingRequest.customerDeletionRequestId}" /></b> do Lễ tân gửi vào ngày <c:out value="${pendingRequest.requestedAt}" />.
                  <br />
                  <b>Lý do:</b> <c:out value="${pendingRequest.reason}" />
                </p>
                <a href="${pageContext.request.contextPath}/manager/member-deletions/review?id=<c:out value="${pendingRequest.customerDeletionRequestId}" />"
                   class="btn-primary" style="background-color: var(--color-warning, #d97706); text-decoration: none; justify-content: center;">
                  Xem xét &amp; Phê duyệt ngay
                </a>
              </div>
            </c:if>

            <c:if test="${customer.status == 'Inactive'}">
              <div class="inactive-notice-card">
                <strong>Lưu ý:</strong> Hồ sơ này đã ở trạng thái <b>Inactive</b> (bất biến theo quy tắc CBR-04). Mọi dữ liệu chỉ có thể xem, không thể chỉnh sửa hay thêm gói mới.
              </div>
            </c:if>
          </div>
        </div>
      </main>
    </div>
  </div>
</body>
</html>
