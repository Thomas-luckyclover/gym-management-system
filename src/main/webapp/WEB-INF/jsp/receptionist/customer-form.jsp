<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Chi tiết hồ sơ · <c:out value="${customer.customerCode}" /> · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/customer-list.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/customer-form.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">

  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
  <script src="${pageContext.request.contextPath}/js/pages/receptionist/customer-form.js" defer></script>
</head>

<body class="dock theme-staff">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Chi tiết hồ sơ khách hàng</h1>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content customer-form-page" id="main-content" tabindex="-1">
        <%-- Breadcrumb --%>
        <nav class="breadcrumb-nav" aria-label="Đường dẫn">
          <a href="${pageContext.request.contextPath}/receptionist/customers">Hồ sơ khách hàng</a>
          <span>/</span>
          <span><c:out value="${customer.customerCode}" /></span>
        </nav>

        <div class="form-grid">
          <%-- Cột trái: Thông tin hồ sơ & Tài sản hiện tại --%>
          <div style="display: flex; flex-direction: column; gap: var(--space-5);">
            <div class="info-card">
              <div class="info-card-header">
                <h3>Thông tin định danh</h3>
                <span class="badge badge-active"><c:out value="${customer.status}" /></span>
              </div>

              <div class="detail-list">
                <div class="detail-item">
                  <span class="detail-label">Mã khách hàng</span>
                  <span class="detail-value"><b><c:out value="${customer.customerCode}" /></b></span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">Họ và tên</span>
                  <span class="detail-value"><c:out value="${person.fullName}" /></span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">Số điện thoại</span>
                  <span class="detail-value"><c:out value="${person.phone}" default="—" /></span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">Email</span>
                  <span class="detail-value"><c:out value="${person.email}" default="—" /></span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">Loại khách</span>
                  <span class="detail-value">
                    <c:choose>
                      <c:when test="${customer.customerType == 'Dependent'}">Hồ sơ phụ thuộc (Dependent)</c:when>
                      <c:otherwise>Hồ sơ cá nhân (Self)</c:otherwise>
                    </c:choose>
                  </span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">Thời điểm tham gia</span>
                  <span class="detail-value"><c:out value="${customer.joinedAt}" /></span>
                </div>
              </div>
            </div>

            <%-- Thông tin gói tập còn hạn và buổi PT scheduled --%>
            <div class="info-card">
              <div class="info-card-header">
                <h3>Gói tập &amp; Buổi PT còn hiệu lực</h3>
              </div>

              <c:choose>
                <c:when test="${hasActiveAssets}">
                  <c:if test="${not empty activePackages}">
                    <div>
                      <h4 style="margin: 0 0 var(--space-2) 0; font-size: var(--font-size-sm); color: var(--color-text-secondary);">Gói tập đang chạy:</h4>
                      <ul class="active-assets-list">
                        <c:forEach var="pkg" items="${activePackages}">
                          <li>
                            <b><c:out value="${pkg.packageName}" /></b> (<c:out value="${pkg.packageType}" />)
                            — Hạn đến: <c:out value="${pkg.endDate}" />
                            <c:if test="${pkg.partner}"><span class="badge badge-package">Gói đôi</span></c:if>
                          </li>
                        </c:forEach>
                      </ul>
                    </div>
                  </c:if>

                  <c:if test="${not empty scheduledSessions}">
                    <div style="margin-top: var(--space-3);">
                      <h4 style="margin: 0 0 var(--space-2) 0; font-size: var(--font-size-sm); color: var(--color-text-secondary);">Buổi PT đã xếp lịch:</h4>
                      <ul class="active-assets-list">
                        <c:forEach var="sess" items="${scheduledSessions}">
                          <li>
                            Ngày <c:out value="${sess.slotDate}" /> lúc <c:out value="${sess.startTime}" /> — PT: <c:out value="${sess.ptName}" />
                          </li>
                        </c:forEach>
                      </ul>
                    </div>
                  </c:if>
                </c:when>
                <c:otherwise>
                  <p style="color: var(--color-text-muted); margin: 0; font-size: var(--font-size-sm);">
                    Khách hàng hiện không còn gói tập đang hoạt động hoặc buổi PT nào.
                  </p>
                </c:otherwise>
              </c:choose>
            </div>
          </div>

          <%-- Cột phải: Khu vực xóa hồ sơ (Danger Zone) --%>
          <div>
            <div class="danger-card">
              <div class="danger-header">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
                  <line x1="12" y1="9" x2="12" y2="13"></line>
                  <line x1="12" y1="17" x2="12.01" y2="17"></line>
                </svg>
                Xóa hồ sơ khách hàng
              </div>

              <div class="danger-body">
                <p class="danger-desc">
                  Thao tác này sẽ chuyển trạng thái hồ sơ sang <b>Inactive</b>. Tài khoản liên kết sẽ bị đăng xuất ngay lập tức.
                </p>

                <c:choose>
                  <c:when test="${hasActiveAssets}">
                    <div class="asset-warning">
                      <strong>CBR-16:</strong> Khách hàng còn gói tập/buổi PT hoạt động. Yêu cầu sẽ được chuyển đến <b>Quản lý phê duyệt</b>.
                    </div>
                  </c:when>
                  <c:otherwise>
                    <div style="font-size: var(--font-size-xs); color: var(--color-text-secondary); background-color: var(--color-bg-secondary); padding: var(--space-3); border-radius: var(--radius-sm);">
                      Khách hàng không còn gói tập hay buổi PT. Thao tác xóa sẽ được thực hiện <b>ngay lập tức</b>.
                    </div>
                  </c:otherwise>
                </c:choose>

                <form id="deleteFormOnPage" action="${pageContext.request.contextPath}/receptionist/customers/form" method="post">
                  <input type="hidden" name="action" value="delete" />
                  <input type="hidden" name="customerId" value="<c:out value="${customer.customerId}" />" />

                  <div class="form-group">
                    <label class="form-label" for="deletionReasonPage">
                      Lý do xóa <span style="color: var(--color-danger);">*</span>
                    </label>
                    <textarea id="deletionReasonPage"
                              name="reason"
                              class="form-textarea"
                              placeholder="Nhập lý do chi tiết (5 - 500 ký tự)..."
                              required></textarea>
                    <div class="char-counter">
                      <span id="charCountPage">0</span>/500 ký tự
                    </div>
                  </div>

                  <button type="submit"
                          id="btnSubmitDeletion"
                          class="btn-primary"
                          style="background-color: var(--color-danger); width: 100%; justify-content: center; margin-top: var(--space-2);">
                    <c:choose>
                      <c:when test="${hasActiveAssets}">Gửi yêu cầu Quản lý duyệt</c:when>
                      <c:otherwise>Xác nhận xóa hồ sơ</c:otherwise>
                    </c:choose>
                  </button>
                </form>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>
</body>
</html>
