<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<c:choose>
    <c:when test="${managerView}">
        <c:set var="roleTheme" value="theme-manager" />
        <c:set var="roleName" value="Quản lý" />
        <c:set var="roleInitials" value="QL" />
    </c:when>
    <c:otherwise>
        <c:set var="roleTheme" value="theme-staff" />
        <c:set var="roleName" value="Lễ tân" />
        <c:set var="roleInitials" value="LT" />
    </c:otherwise>
</c:choose>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Chi tiết hội viên · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">

        <%-- BF-07: hai vai trò dùng chung bố cục; token màu thay đổi theo theme. --%>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/customer-list.css?v=20261105-detail">

        <script src="${pageContext.request.contextPath}/js/pages/receptionist/customer-list.js?v=20261025-bf07" defer>
        </script>
    </head>
    <body class="dock <c:out value="${roleTheme}" />">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>
        <div class="app">
            <c:choose>
                <c:when test="${managerView}">
                    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>
                </c:when>
                <c:otherwise>
                    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
                </c:otherwise>
            </c:choose>
            <div class="main">
                <header class="topbar">
                    <h1 class="tb-title">Chi tiết hội viên</h1>
                    <div class="top-right">
                        <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
                        <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
                    </div>
                </header>
                <main class="content bf07-page bf07-profile-detail" id="main-content" tabindex="-1">

                    <%-- ===== detail: CustomerProfileDetailView do Service tạo cho cả hai vai trò ===== --%>
                    <c:set var="profile" value="${detail.profile}" />
                    <%-- profile.inactive = getter tự tính isInactive() · dto/CustomerProfileRowView.java (profile = CustomerProfileRowView) --%>
                    <c:set var="showDeletionPanel" value="${not empty detail.pendingRequest or (not managerView and not profile.ownProfile and not profile.inactive)}" />
                    <c:url var="listUrl" value="${listPath}" />
                    <nav class="bf07-breadcrumb" aria-label="Đường dẫn">
                        <a href="${listUrl}">← Danh sách hội viên</a>
                        <span>
                            <c:out value="${profile.customerCode}" />
                        </span>
                    </nav>
                    <%-- Nút mở form #31 chỉ dành cho Lễ tân; Manager tiếp tục chỉ xem. --%>
                    <c:if test="${not managerView}">
                        <div class="bf07-detail-toolbar">
                            <span class="bf07-muted">Thông tin hồ sơ hội viên</span>
                            <div class="bf07-row-actions">
                                <c:if test="${not profile.inactive and not profile.ownProfile}">
                                    <c:url var="editUrl" value="/receptionist/customers/form">
                                        <c:param name="id" value="${profile.customerId}" />
                                        <c:param name="mode" value="edit" />
                                    </c:url>
                                    <a class="btn btn-outline" href="${editUrl}">Sửa hồ sơ</a>
                                </c:if>
                                <a class="btn btn-primary" href="${pageContext.request.contextPath}/receptionist/customers/form?mode=create">＋ Thêm hội viên</a>
                            </div>
                        </div>
                    </c:if>
                    <c:if test="${not empty actionError}">
                        <p class="bf07-notice bf07-notice-danger" role="alert">
                            <c:out value="${actionError}" />
                        </p>
                    </c:if>
                    <div class="bf07-detail-grid">
                        <section class="bf07-panel bf07-detail-card ${showDeletionPanel ? '' : 'bf07-span-all'}">
                            <div class="bf07-person bf07-person-heading">
                                <span class="bf07-avatar" aria-hidden="true">
                                    <c:out value="${fn:substring(profile.fullName, 0, 1)}" />
                                </span>
                                <div>
                                    <h2>
                                        <c:out value="${profile.fullName}" />
                                    </h2>
                                    <span class="bf07-sub">
                                        <c:out value="${profile.customerCode}" />
                                        ·
                                        <%-- profile.customerTypeLabel = getter tự tính getCustomerTypeLabel() · dto/CustomerProfileRowView.java (profile = CustomerProfileRowView) --%>
                                        <c:out value="${profile.customerTypeLabel}" />
                                    </span>
                                </div>
                                <%-- profile.statusTone = getter tự tính getStatusTone() · dto/CustomerProfileRowView.java (profile = CustomerProfileRowView) --%>
                                <span class="badge badge-<c:out value="${profile.statusTone}" />">
                                    <%-- profile.statusLabel = getter tự tính getStatusLabel() · dto/CustomerProfileRowView.java (profile = CustomerProfileRowView) --%>
                                    <c:out value="${profile.statusLabel}" />
                                </span>
                            </div>
                            <c:if test="${not empty detail.customer.checkInPhoto}">
                                <div class="bf07-checkin-photo">
                                    <img src="${pageContext.request.contextPath}<c:out value='${detail.customer.checkInPhoto}'/>" alt="Ảnh check-in hội viên">
                                    <span class="bf07-muted">Ảnh check-in</span>
                                </div>
                            </c:if>
                            <dl class="bf07-metadata">
                                <div>
                                    <dt>Ngày sinh</dt>
                                    <dd><c:out value="${gms:formatDate(detail.person.dateOfBirth)}" default="Chưa cập nhật" /></dd>
                                </div>
                                <div>
                                    <dt>Số điện thoại</dt>
                                    <dd>
                                        <c:out value="${profile.phone}" default="—" />
                                    </dd>
                                </div>
                                <div>
                                    <dt>Email</dt>
                                    <dd class="bf07-wrap">
                                        <c:out value="${profile.email}" default="Chưa có email" />
                                    </dd>
                                </div>
                                <div>
                                    <dt>Ngày tham gia</dt>
                                    <dd>
                                        <c:out value="${gms:formatDateTime(profile.joinedAt)}" />
                                    </dd>
                                </div>
                                <c:if test="${profile.inactive}">
                                    <div>
                                        <dt>Ngừng hoạt động lúc</dt>
                                        <dd>
                                            <c:out value="${gms:formatDateTime(profile.deletedAt)}" />
                                        </dd>
                                    </div>
                                </c:if>
                            </dl>
                            <c:if test="${profile.customerType == 'Dependent'}">
                                <h3 class="bf07-guardian-title">Người giám hộ</h3>
                                <dl class="bf07-metadata">
                                    <div><dt>Họ và tên</dt><dd><c:out value="${detail.customer.guardianName}" default="—" /></dd></div>
                                    <div><dt>Quan hệ</dt><dd><c:out value="${detail.customer.guardianRelationship}" default="—" /></dd></div>
                                    <div><dt>Điện thoại chính</dt><dd><c:out value="${detail.customer.guardianPhone}" default="—" /></dd></div>
                                    <c:if test="${not empty detail.customer.guardianPhone2}"><div><dt>Điện thoại phụ</dt><dd><c:out value="${detail.customer.guardianPhone2}" /></dd></div></c:if>
                                </dl>
                            </c:if>
                            <c:if test="${profile.inactive}">
                                <p class="bf07-notice">Hồ sơ chỉ dùng để tra cứu. Giao dịch và lịch sử được giữ nguyên.</p>
                            </c:if>
                            <c:if test="${profile.ownProfile}">
                                <p class="bf07-notice">Đây là hồ sơ của bạn. Hãy nhờ nhân viên khác xử lý yêu cầu xóa.</p>
                            </c:if>
                        </section>
                        <c:if test="${showDeletionPanel}">
                            <section class="bf07-panel bf07-detail-card">
                                <c:choose>
                                    <c:when test="${not empty detail.pendingRequest}">
                                        <h2>Yêu cầu xóa hồ sơ</h2>
                                        <span class="badge badge-warning">
                                            Yêu cầu #
                                            <c:out value="${detail.pendingRequest.customerDeletionRequestId}" />
                                        </span>
                                        <p class="bf07-muted">
                                            Gửi lúc
                                            <c:out value="${gms:formatDateTime(detail.pendingRequest.requestedAt)}" />
                                        </p>
                                        <div class="bf07-pending-reason">
                                            <span class="bf07-muted">Lý do yêu cầu</span>
                                            <p class="bf07-reason"><c:out value="${detail.pendingRequest.reason}" /></p>
                                        </div>
                                        <c:if test="${not managerView}"><p class="bf07-muted">Đang chờ Quản lý xét duyệt. Hồ sơ vẫn được giữ trong hệ thống.</p></c:if>
                                        <c:if test="${managerView}">
                                            <c:url var="reviewUrl" value="/manager/member-deletions/review">
                                                <c:param name="id" value="${detail.pendingRequest.customerDeletionRequestId}" />
                                            </c:url>
                                            <a href="${reviewUrl}" class="btn btn-primary">Xem yêu cầu xóa</a>
                                        </c:if>
                                    </c:when>
                                    <c:when test="${not managerView and not profile.ownProfile and not profile.inactive}">
                                        <h2>Xóa hồ sơ</h2>
                                        <p class="bf07-notice">
                                            <c:choose>
                                                <%-- detail.hasActiveAssets = getter tự tính isHasActiveAssets() · dto/CustomerProfileDetailView.java --%>
                                                <c:when test="${detail.hasActiveAssets}">Còn gói hoặc buổi PT. Yêu cầu sẽ gửi quản lý duyệt; hồ sơ chưa bị xóa ngay.</c:when>
                                                <c:otherwise>Không còn gói hoặc buổi PT. Xác nhận sẽ ngừng hồ sơ ngay.</c:otherwise>
                                            </c:choose>
                                        </p>
                                        <p class="bf07-muted">Lịch sử giao dịch vẫn được giữ lại.</p>
                                        <form class="bf07-reason-form" action="${pageContext.request.contextPath}/receptionist/customers/form" method="post" data-confirm-delete data-unsaved-warning novalidate>
                                            <input type="hidden" name="csrfToken" value="<c:out value="${csrfToken}" />">
                                            <input type="hidden" name="action" value="delete">
                                            <input type="hidden" name="customerId" value="${profile.customerId}">
                                            <label for="profileDeleteReason">Lý do xóa *</label>
                                            <textarea name="reason" id="profileDeleteReason" rows="3" maxlength="500" required aria-describedby="profileReasonError"><c:out value="${reason}" /></textarea>
                                            <span class="bf07-error" id="profileReasonError" data-reason-error role="alert">
                                                <c:out value="${errors.reason}" />
                                            </span>
                                            <small class="bf07-muted">
                                                5–500 ký tự ·
                                                <span data-reason-count>0</span>
                                                /500
                                            </small>
                                            <button type="submit" class="btn btn-danger">
                                                <c:choose>
                                                    <c:when test="${detail.hasActiveAssets}">Gửi yêu cầu xóa</c:when>
                                                    <c:otherwise>Xác nhận xóa hồ sơ</c:otherwise>
                                                </c:choose>
                                            </button>
                                        </form>
                                    </c:when>
                                </c:choose>
                            </section>
                        </c:if>
                        <section class="bf07-panel bf07-detail-card<c:if test="${showDeletionPanel}"> bf07-span-all</c:if>">
                            <c:set var="packages" value="${detail.activePackages}" />
                            <c:set var="sessions" value="${detail.scheduledSessions}" />
                            <div class="bf07-section-heading">
                                <h2>Gói tập và lịch PT</h2>
                                <span class="bf07-muted">
                                    <c:out value="${fn:length(packages)}" />
                                    gói ·
                                    <c:out value="${fn:length(sessions)}" />
                                    buổi
                                </span>
                            </div>
                            <c:choose>
                                <c:when test="${empty packages and empty sessions}">
                                    <p class="bf07-muted">Không còn gói đang dùng hoặc buổi PT chờ tập.</p>
                                </c:when>
                                <c:otherwise>
                                    <c:if test="${not empty packages}">
                                        <ul class="bf07-package-list">
                                            <c:forEach var="pkg" items="${packages}">
                                                <li>
                                                    <div>
                                                        <strong>
                                                            <c:out value="${pkg.packageName}" />
                                                        </strong>
                                                        <span class="bf07-sub">
                                                            <c:out value="${pkg.packageTypeLabel}" />
                                                            <c:if test="${pkg.partner}">· Người tập cùng</c:if>
                                                            ·
                                                            <c:out value="${pkg.statusLabel}" />
                                                        </span>
                                                    </div>
                                                    <span class="bf07-package-date">
                                                        Đến
                                                        <c:out value="${gms:formatDate(pkg.endDate)}" />
                                                    </span>
                                                </li>
                                            </c:forEach>
                                        </ul>
                                    </c:if>
                                    <c:if test="${not empty sessions}">
                                        <details class="bf07-session-details">
                                            <summary>
                                                Xem
                                                <c:out value="${fn:length(sessions)}" />
                                                buổi PT đang chờ tập
                                            </summary>
                                            <ul class="bf07-session-list">
                                                <c:forEach var="session" items="${sessions}">
                                                    <li>
                                                        <span>
                                                            <c:out value="${gms:formatDate(session.slotDate)}" />
                                                            ·
                                                            <c:out value="${gms:formatTime(session.startTime)}" />
                                                        </span>
                                                        <span class="bf07-muted">
                                                            <c:out value="${session.ptName}" />
                                                        </span>
                                                    </li>
                                                </c:forEach>
                                            </ul>
                                        </details>
                                    </c:if>
                                </c:otherwise>
                            </c:choose>
                        </section>
                    </div>
                </main>
            </div>
        </div>
    </body>
</html>
