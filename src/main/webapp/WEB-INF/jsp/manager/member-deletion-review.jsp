<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<c:set var="managerView" value="true" />
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
        <title>Duyệt xóa hồ sơ · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">

        <%-- BF-07: hai vai trò dùng chung bố cục; token màu thay đổi theo theme. --%>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/customer-list.css?v=20261025-bf07">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
        <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer>
        </script>
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
                    <h1 class="tb-title">Duyệt xóa hồ sơ</h1>
                    <div class="top-right">
                        <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
                        <a class="tb-user" href="${pageContext.request.contextPath}/account">
                            <span class="avatar">
                                <c:out value="${roleInitials}" />
                            </span>
                            <span>
                                <b>
                                    <c:out value="${sessionScope.displayName}" />
                                </b>
                                <small>
                                    <c:out value="${roleName}" />
                                </small>
                            </span>
                        </a>
                    </div>
                </header>
                <main class="content bf07-page" id="main-content" tabindex="-1">

                    <%-- ===== review: CustomerDeletionReviewView, tải gói/buổi khi mở yêu cầu ===== --%>
                    <nav class="bf07-breadcrumb" aria-label="Đường dẫn">
                        <a href="${pageContext.request.contextPath}/manager/members?tab=pending-deletions">← Chờ duyệt xóa</a>
                        <span>
                            Yêu cầu #
                            <c:out value="${review.deletionRequest.customerDeletionRequestId}" />
                        </span>
                    </nav>
                    <c:if test="${not empty actionError}">
                        <p class="bf07-notice bf07-notice-danger" role="alert">
                            <c:out value="${actionError}" />
                        </p>
                    </c:if>
                    <c:if test="${review.ownProfile}">
                        <p class="bf07-notice bf07-notice-warning">Đây là hồ sơ của bạn. Bạn không được duyệt hoặc từ chối; hãy nhờ quản lý khác xử lý.</p>
                    </c:if>
                    <c:if test="${not review.deletionRequest.pending}">
                        <div class="bf07-notice">
                            <c:choose>
                                <c:when test="${review.deletionRequest.approved}">
                                    <span class="badge badge-success">Đã duyệt</span>
                                </c:when>
                                <c:otherwise>
                                    <span class="badge badge-danger">Đã từ chối</span>
                                </c:otherwise>
                            </c:choose>
                            <c:out value="${review.reviewerName}" />
                            ·
                            <c:out value="${gms:formatDateTime(review.deletionRequest.reviewedAt)}" />
                            <c:if test="${not empty review.deletionRequest.rejectReason}">
                                <p class="bf07-reason"><c:out value="${review.deletionRequest.rejectReason}" /></p>
                            </c:if>
                        </div>
                    </c:if>
                    <div class="bf07-detail-grid">
                        <section class="bf07-panel bf07-detail-card">
                            <div class="bf07-person bf07-person-heading">
                                <span class="bf07-avatar" aria-hidden="true">
                                    <c:out value="${fn:substring(review.customerFullName, 0, 1)}" />
                                </span>
                                <div>
                                    <h2>
                                        <c:out value="${review.customerFullName}" />
                                    </h2>
                                    <span class="bf07-sub">
                                        <c:out value="${review.customerCode}" />
                                        ·
                                        <c:out value="${review.customerTypeLabel}" />
                                    </span>
                                </div>
                            </div>
                            <dl class="bf07-metadata">
                                <div>
                                    <dt>Số điện thoại</dt>
                                    <dd>
                                        <c:out value="${review.customerPhone}" default="—" />
                                    </dd>
                                </div>
                                <div>
                                    <dt>Email</dt>
                                    <dd class="bf07-wrap">
                                        <c:out value="${review.customerEmail}" default="Chưa có email" />
                                    </dd>
                                </div>
                                <div>
                                    <dt>Ngày tham gia</dt>
                                    <dd>
                                        <c:out value="${gms:formatDateTime(review.customerJoinedAt)}" />
                                    </dd>
                                </div>
                            </dl>
                        </section>
                        <section class="bf07-panel bf07-detail-card">
                            <div class="bf07-section-heading">
                                <h2>Yêu cầu xóa hồ sơ</h2>
                                <c:if test="${review.deletionRequest.pending}">
                                    <span class="badge badge-warning">Chờ duyệt</span>
                                </c:if>
                            </div>
                            <p class="bf07-muted">
                                <c:out value="${review.requesterName}" />
                                ·
                                <c:out value="${gms:formatDateTime(review.deletionRequest.requestedAt)}" />
                            </p>
                            <div class="bf07-reason"><c:out value="${review.deletionRequest.reason}" /></div>
                            <c:if test="${review.deletionRequest.pending}">
                                <p class="bf07-notice bf07-notice-warning">
                                    <c:choose>
                                        <c:when test="${review.hasActiveAssets()}">
                                            Còn
                                            <b>
                                                <c:out value="${fn:length(review.activePackages)}" />
                                                gói
                                            </b>
                                            và
                                            <b>
                                                <c:out value="${fn:length(review.scheduledSessions)}" />
                                                buổi PT
                                            </b>
                                            .
                                        </c:when>
                                        <c:otherwise>Hiện không còn gói hoặc buổi PT đang chờ.</c:otherwise>
                                    </c:choose>
                                    Duyệt sẽ ngừng hồ sơ và đăng xuất tài khoản. Gói, buổi tập và giao dịch được giữ nguyên để tra cứu.
                                </p>
                                <c:if test="${review.canAct}">
                                    <div class="bf07-decision-actions">
                                        <form id="approveForm" action="${pageContext.request.contextPath}/manager/member-deletions/review" method="post" data-confirm-approve>
                                            <input type="hidden" name="csrfToken" value="<c:out value="${csrfToken}" />">
                                            <input type="hidden" name="action" value="approve">
                                            <input type="hidden" name="id" value="${review.deletionRequest.customerDeletionRequestId}">
                                            <button class="btn btn-danger" type="submit">Duyệt xóa hồ sơ</button>
                                        </form>
                                        <button class="btn btn-outline" type="button" id="openRejectDialog">Từ chối</button>
                                    </div>
                                </c:if>
                            </c:if>
                        </section>
                    </div>
                    <section class="bf07-panel bf07-detail-card">
                        <c:set var="packages" value="${review.activePackages}" />
                        <c:set var="sessions" value="${review.scheduledSessions}" />
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
                    <c:if test="${review.canAct}">
                        <dialog class="bf07-dialog" id="rejectDialog" aria-labelledby="rejectDialogTitle" data-reopen="${reopenReject}">
                            <form class="bf07-reason-form" action="${pageContext.request.contextPath}/manager/member-deletions/review" method="post" novalidate>
                                <div class="bf07-dialog-heading">
                                    <h2 id="rejectDialogTitle">Từ chối yêu cầu xóa</h2>
                                    <button class="bf07-close" type="button" data-dialog-close aria-label="Đóng">×</button>
                                </div>
                                <p>
                                    Hồ sơ của
                                    <b>
                                        <c:out value="${review.customerFullName}" />
                                    </b>
                                    sẽ được giữ nguyên.
                                </p>
                                <input type="hidden" name="csrfToken" value="<c:out value="${csrfToken}" />">
                                <input type="hidden" name="action" value="reject">
                                <input type="hidden" name="id" value="${review.deletionRequest.customerDeletionRequestId}">
                                <c:if test="${not empty actionError}">
                                    <p class="bf07-error" role="alert"><c:out value="${actionError}" /></p>
                                </c:if>
                                <label for="rejectReason">Lý do từ chối *</label>
                                <textarea name="rejectReason" id="rejectReason" autofocus rows="3" maxlength="500" required aria-describedby="rejectReasonError"><c:out value="${rejectReason}" /></textarea>
                                <span class="bf07-error" id="rejectReasonError" data-reason-error role="alert">
                                    <c:out value="${errors.rejectReason}" />
                                </span>
                                <small class="bf07-muted">
                                    5–500 ký tự ·
                                    <span data-reason-count>0</span>
                                    /500
                                </small>
                                <div class="bf07-dialog-actions">
                                    <button class="btn btn-outline" type="button" data-dialog-close>Hủy</button>
                                    <button type="submit" class="btn btn-primary">Xác nhận từ chối</button>
                                </div>
                            </form>
                        </dialog>
                    </c:if>
                </main>
            </div>
        </div>
    </body>
</html>
