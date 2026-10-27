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
        <title>Hồ sơ hội viên · Super Gym</title>
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
                    <h1 class="tb-title">Hồ sơ hội viên</h1>
                    <div class="top-right">
                        <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
                        <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
                    </div>
                </header>
                <main class="content bf07-page" id="main-content" tabindex="-1">

                    <%-- ===== Điều hướng: managerView và currentTab do Servlet đặt ===== --%>
                    <c:url var="listUrl" value="${listPath}" />
                    <nav class="bf07-tabs" aria-label="Hồ sơ và yêu cầu xóa">
                        <c:choose>
                            <c:when test="${managerView}">
                                <a href="${listUrl}?tab=all" class="bf07-tab <c:if test="${currentTab == 'all'}">is-active</c:if>" <c:if test="${currentTab == 'all'}">aria-current="page"</c:if>>
                                    Tất cả hội viên
                                </a>
                                <a href="${listUrl}?tab=pending-deletions" class="bf07-tab <c:if test="${currentTab == 'pending-deletions'}">is-active</c:if>" <c:if test="${currentTab == 'pending-deletions'}">aria-current="page"</c:if>>
                                    Chờ duyệt xóa
                                    <c:if test="${pendingCount != null}">
                                        <span class="bf07-count">
                                            <c:out value="${pendingCount}" />
                                        </span>
                                    </c:if>
                                </a>
                            </c:when>
                            <c:otherwise>
                                <a href="${listUrl}?tab=profiles" class="bf07-tab <c:if test="${currentTab == 'profiles'}">is-active</c:if>" <c:if test="${currentTab == 'profiles'}">aria-current="page"</c:if>>
                                    Danh sách khách hàng
                                </a>
                                <a href="${listUrl}?tab=my-requests" class="bf07-tab <c:if test="${currentTab == 'my-requests'}">is-active</c:if>" <c:if test="${currentTab == 'my-requests'}">aria-current="page"</c:if>>
                                    Yêu cầu xóa của tôi
                                </a>
                            </c:otherwise>
                        </c:choose>
                    </nav>

                    <%-- ===== Bộ lọc hồ sơ: keyword, statusFilter, pageSize ===== --%>
                    <c:if test="${currentTab == 'all' or currentTab == 'profiles'}">
                        <form class="bf07-filters bf07-panel" action="${listUrl}" method="get" role="search">
                            <input type="hidden" name="tab" value="<c:out value="${currentTab}" />">
                            <div class="bf07-search-field">
                                <label for="profileKeyword">Tìm hội viên</label>
                                <input id="profileKeyword" name="keyword" type="search" maxlength="100" placeholder="Tên, mã hội viên, số điện thoại hoặc email" value="<c:out value="${keyword}" />">
                            </div>
                            <div>
                                <label for="profileStatus">Trạng thái hồ sơ</label>
                                <select id="profileStatus" name="status">
                                    <option value="ALL" <c:if test="${statusFilter == 'ALL'}">selected</c:if>>Tất cả trạng thái</option>
                                    <option value="New" <c:if test="${statusFilter == 'New'}">selected</c:if>>Chưa có gói</option>
                                    <option value="Active" <c:if test="${statusFilter == 'Active'}">selected</c:if>>Hoạt động</option>
                                    <option value="Frozen" <c:if test="${statusFilter == 'Frozen'}">selected</c:if>>Đang bảo lưu</option>
                                    <option value="Expired" <c:if test="${statusFilter == 'Expired'}">selected</c:if>>Hết hạn</option>
                                    <c:if test="${managerView}">
                                        <option value="Inactive" <c:if test="${statusFilter == 'Inactive'}">selected</c:if>>Ngừng hoạt động</option>
                                    </c:if>
                                </select>
                            </div>
                            <div class="bf07-page-size">
                                <label for="profilePageSize">Số dòng</label>
                                <select id="profilePageSize" name="pageSize">
                                    <c:forEach var="size" items="${[10,20,50]}">
                                        <option value="${size}" <c:if test="${pageSize == size}">selected</c:if>>
                                            <c:out value="${size}" />
                                        </option>
                                    </c:forEach>
                                </select>
                            </div>
                            <div class="bf07-filter-actions">
                                <button class="btn btn-primary" type="submit">Tìm</button>
                                <c:if test="${not empty keyword or statusFilter != 'ALL'}">
                                    <a class="btn btn-outline" href="${listUrl}">Xóa lọc</a>
                                </c:if>
                            </div>
                        </form>
                    </c:if>
                    <c:if test="${not empty actionError and empty deleteTarget}">
                        <p class="bf07-notice bf07-notice-danger" role="alert">
                            <c:out value="${actionError}" />
                        </p>
                    </c:if>
                    <c:choose>
                        <c:when test="${not empty loadError}">
                            <div class="bf07-panel bf07-empty" role="alert">
                                <strong>Chưa tải được danh sách</strong>
                                <p>
                                    <c:out value="${loadError}" />
                                </p>
                                <a class="btn btn-outline" href="${listUrl}">Thử lại</a>
                            </div>
                        </c:when>
                        <c:otherwise>
                            <section class="bf07-panel bf07-list-panel" aria-label="Danh sách hồ sơ hoặc yêu cầu xóa">
                                <div class="bf07-section-heading">
                                    <h2>
                                        <c:choose>
                                            <c:when test="${currentTab == 'pending-deletions'}">Yêu cầu chờ duyệt</c:when>
                                            <c:when test="${currentTab == 'my-requests'}">Yêu cầu đã gửi</c:when>
                                            <c:otherwise>Danh sách hội viên</c:otherwise>
                                        </c:choose>
                                    </h2>
                                    <span class="bf07-muted">
                                        <c:out value="${pageData.totalItems}" />
                                        kết quả
                                    </span>
                                </div>
                                <c:choose>
                                    <c:when test="${empty pageData.items}">
                                        <div class="bf07-empty">
                                            <strong>
                                                <c:choose>
                                                    <c:when test="${currentTab == 'pending-deletions'}">Không có yêu cầu chờ duyệt</c:when>
                                                    <c:when test="${currentTab == 'my-requests'}">Bạn chưa gửi yêu cầu xóa nào</c:when>
                                                    <c:otherwise>Không tìm thấy hội viên phù hợp</c:otherwise>
                                                </c:choose>
                                            </strong>
                                            <c:if test="${not empty keyword or statusFilter != 'ALL'}">
                                                <p>Thử đổi từ khóa hoặc xóa bộ lọc.</p>
                                            </c:if>
                                        </div>
                                    </c:when>
                                    <c:when test="${currentTab == 'all' or currentTab == 'profiles'}">

                                        <%-- pageData.items: List<CustomerProfileRowView>; Service tính ownProfile. --%>
                                        <table class="bf07-table bf07-profile-table">
                                            <caption class="visually-hidden">Hồ sơ hội viên và thao tác</caption>
                                            <thead>
                                                <tr>
                                                    <th>Hội viên</th>
                                                    <th>Liên hệ</th>
                                                    <th>Gói hiện tại</th>
                                                    <th>Trạng thái</th>
                                                    <th>Thao tác</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <c:forEach var="profile" items="${pageData.items}">
                                                    <c:url var="detailUrl" value="${detailPath}">
                                                        <c:param name="id" value="${profile.customerId}" />
                                                    </c:url>
                                                    <tr>
                                                        <td data-label="Hội viên">
                                                            <div class="bf07-person">
                                                                <span class="bf07-avatar" aria-hidden="true">
                                                                    <c:out value="${fn:substring(profile.fullName, 0, 1)}" />
                                                                </span>
                                                                <div>
                                                                    <a class="bf07-name" href="${detailUrl}">
                                                                        <c:out value="${profile.fullName}" />
                                                                    </a>
                                                                    <span class="bf07-sub">
                                                                        <c:out value="${profile.customerCode}" />
                                                                        ·
                                                                        <c:out value="${profile.customerTypeLabel}" />
                                                                    </span>
                                                                </div>
                                                            </div>
                                                        </td>
                                                        <td data-label="Liên hệ">
                                                            <span>
                                                                <c:out value="${profile.phone}" default="—" />
                                                            </span>
                                                            <span class="bf07-sub bf07-wrap">
                                                                <c:out value="${profile.email}" default="Chưa có email" />
                                                            </span>
                                                        </td>
                                                        <td data-label="Gói hiện tại">
                                                            <c:out value="${profile.currentPackageName}" default="Không có gói còn hạn" />
                                                        </td>
                                                        <td data-label="Trạng thái">
                                                            <span class="badge badge-<c:out value="${profile.statusTone}" />">
                                                                <c:out value="${profile.statusLabel}" />
                                                            </span>
                                                            <c:if test="${profile.hasPendingDeletion}">
                                                                <span class="bf07-sub bf07-warning-text">Có yêu cầu xóa đang chờ</span>
                                                            </c:if>
                                                        </td>
                                                        <td data-label="Thao tác">
                                                            <div class="bf07-row-actions">
                                                                <a class="btn btn-outline" href="${detailUrl}">Chi tiết</a>
                                                                <c:if test="${not managerView}">
                                                                    <c:choose>
                                                                        <c:when test="${profile.ownProfile}">
                                                                            <span class="bf07-sub">Hồ sơ của bạn</span>
                                                                        </c:when>
                                                                        <c:when test="${profile.hasPendingDeletion}">
                                                                            <span class="bf07-sub">Đã gửi yêu cầu</span>
                                                                        </c:when>
                                                                        <c:otherwise>
                                                                            <button type="button" class="btn bf07-delete-button" data-delete-open data-customer-id="${profile.customerId}" data-customer-name="<c:out value="${profile.fullName}" />" data-customer-code="<c:out value="${profile.customerCode}" />" data-has-assets="${profile.hasActivePackage}">
                                                                                Xóa hồ sơ
                                                                            </button>
                                                                        </c:otherwise>
                                                                    </c:choose>
                                                                </c:if>
                                                                <c:if test="${managerView and profile.hasPendingDeletion}">
                                                                    <c:url var="reviewUrl" value="/manager/member-deletions/review">
                                                                        <c:param name="id" value="${profile.pendingRequestId}" />
                                                                    </c:url>
                                                                    <a class="btn btn-primary" href="${reviewUrl}">Xem yêu cầu</a>
                                                                </c:if>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                            </tbody>
                                        </table>
                                    </c:when>
                                    <c:otherwise>

                                        <%-- Danh sách yêu cầu chỉ đọc thông tin tóm tắt, không tải gói/buổi theo từng dòng. --%>
                                        <table class="bf07-table bf07-request-table">
                                            <caption class="visually-hidden">Yêu cầu xóa hồ sơ hội viên</caption>
                                            <thead>
                                                <tr>
                                                    <th>Hội viên / Yêu cầu</th>
                                                    <th>Thời điểm gửi</th>
                                                    <th>Lý do yêu cầu</th>
                                                    <th>
                                                        <c:choose>
                                                            <c:when test="${managerView}">Thao tác</c:when>
                                                            <c:otherwise>Kết quả</c:otherwise>
                                                        </c:choose>
                                                    </th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <c:forEach var="item" items="${pageData.items}">
                                                    <tr>
                                                        <td data-label="Hội viên">
                                                            <strong>
                                                                <c:out value="${item.customerFullName}" />
                                                            </strong>
                                                            <span class="bf07-sub">
                                                                <c:out value="${item.customerCode}" />
                                                                · Yêu cầu #
                                                                <c:out value="${item.deletionRequest.customerDeletionRequestId}" />
                                                            </span>
                                                        </td>
                                                        <td data-label="Gửi lúc">
                                                            <c:out value="${gms:formatDateTime(item.deletionRequest.requestedAt)}" />
                                                            <c:if test="${managerView}">
                                                                <span class="bf07-sub">
                                                                    <c:out value="${item.requesterName}" />
                                                                </span>
                                                            </c:if>
                                                        </td>
                                                        <td data-label="Lý do">
                                                            <c:choose>
                                                                <c:when test="${fn:length(item.deletionRequest.reason) > 120}">
                                                                    <details class="bf07-response">
                                                                        <summary>
                                                                            <c:out value="${fn:substring(item.deletionRequest.reason, 0, 80)}" />
                                                                            …
                                                                        </summary>
                                                                        <p class="bf07-reason"><c:out value="${item.deletionRequest.reason}" /></p>
                                                                    </details>
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <p class="bf07-reason-preview"><c:out value="${item.deletionRequest.reason}" /></p>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </td>
                                                        <td data-label="Kết quả">
                                                            <c:choose>
                                                                <c:when test="${managerView}">
                                                                    <c:url var="reviewUrl" value="/manager/member-deletions/review">
                                                                        <c:param name="id" value="${item.deletionRequest.customerDeletionRequestId}" />
                                                                    </c:url>
                                                                    <a class="btn btn-primary" href="${reviewUrl}">Xem xét</a>
                                                                    <c:if test="${item.ownProfile}">
                                                                        <span class="bf07-sub">Hồ sơ của bạn</span>
                                                                    </c:if>
                                                                </c:when>
                                                                <c:when test="${item.deletionRequest.pending}">
                                                                    <span class="badge badge-warning">Chờ duyệt</span>
                                                                </c:when>
                                                                <c:when test="${item.deletionRequest.approved}">
                                                                    <span class="badge badge-success">Đã duyệt</span>
                                                                    <span class="bf07-sub">
                                                                        <c:out value="${item.reviewerName}" />
                                                                    </span>
                                                                    <span class="bf07-sub">
                                                                        <c:out value="${gms:formatDateTime(item.deletionRequest.reviewedAt)}" />
                                                                    </span>
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <span class="badge badge-danger">Từ chối</span>
                                                                    <details class="bf07-response">
                                                                        <summary>Xem phản hồi</summary>
                                                                        <p>
                                                                            <c:out value="${item.deletionRequest.rejectReason}" />
                                                                        </p>
                                                                        <small>
                                                                            <c:out value="${item.reviewerName}" />
                                                                            ·
                                                                            <c:out value="${gms:formatDateTime(item.deletionRequest.reviewedAt)}" />
                                                                        </small>
                                                                    </details>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                            </tbody>
                                        </table>
                                    </c:otherwise>
                                </c:choose>

                                <%-- ===== Phân trang: c:url encode từ khóa và giữ bộ lọc ===== --%>
                                <c:if test="${pageData.totalItems > 0}">
                                    <div class="bf07-pagination">
                                        <span>
                                            Trang
                                            <b>
                                                <c:out value="${pageData.pageNumber}" />
                                            </b>
                                            /
                                            <c:out value="${pageData.totalPages}" />
                                        </span>
                                        <div class="bf07-row-actions">
                                            <c:if test="${pageData.hasPrevious}">
                                                <c:url var="prevUrl" value="${listPath}">
                                                    <c:param name="tab" value="${currentTab}" />
                                                    <c:param name="page" value="${pageData.pageNumber - 1}" />
                                                    <c:param name="pageSize" value="${pageSize}" />
                                                    <c:param name="keyword" value="${keyword}" />
                                                    <c:param name="status" value="${statusFilter}" />
                                                </c:url>
                                                <a class="btn btn-outline" href="<c:out value="${prevUrl}" />">Trước</a>
                                            </c:if>
                                            <c:if test="${pageData.hasNext}">
                                                <c:url var="nextUrl" value="${listPath}">
                                                    <c:param name="tab" value="${currentTab}" />
                                                    <c:param name="page" value="${pageData.pageNumber + 1}" />
                                                    <c:param name="pageSize" value="${pageSize}" />
                                                    <c:param name="keyword" value="${keyword}" />
                                                    <c:param name="status" value="${statusFilter}" />
                                                </c:url>
                                                <a class="btn btn-outline" href="<c:out value="${nextUrl}" />">Sau</a>
                                            </c:if>
                                        </div>
                                    </div>
                                </c:if>
                            </section>
                        </c:otherwise>
                    </c:choose>

                    <%-- ===== Hộp xóa: POST lỗi giữ lại đúng hội viên và lý do ===== --%>
                    <c:if test="${not managerView}">
                        <dialog class="bf07-dialog" id="deleteDialog" aria-labelledby="deleteDialogTitle" data-reopen="${not empty deleteTarget}">
                            <form class="bf07-reason-form" id="deleteCustomerForm" action="${listUrl}" method="post" novalidate>
                                <div class="bf07-dialog-heading">
                                    <h2 id="deleteDialogTitle">Xóa hồ sơ hội viên</h2>
                                    <button class="bf07-close" type="button" data-dialog-close aria-label="Đóng">×</button>
                                </div>
                                <input type="hidden" name="action" value="delete">
                                <input type="hidden" name="csrfToken" value="<c:out value="${csrfToken}" />">
                                <input type="hidden" name="customerId" id="deleteCustomerId" value="<c:out value="${deleteTarget.profile.customerId}" />">
                                <p>
                                    <strong id="deleteCustomerName">
                                        <c:out value="${deleteTarget.profile.fullName}" />
                                    </strong>
                                    <span class="bf07-sub" id="deleteCustomerCode">
                                        <c:out value="${deleteTarget.profile.customerCode}" />
                                    </span>
                                </p>
                                <p class="bf07-notice" id="deleteConsequence">Hồ sơ sẽ ngừng hoạt động; lịch sử giao dịch được giữ lại.</p>
                                <c:if test="${not empty actionError}">
                                    <p class="bf07-error" role="alert">
                                        <c:out value="${actionError}" />
                                    </p>
                                </c:if>
                                <label for="deleteReason">Lý do xóa *</label>
                                <textarea id="deleteReason" name="reason" autofocus rows="3" maxlength="500" required aria-describedby="deleteReasonError deleteReasonHint"><c:out value="${reason}" /></textarea>
                                <span class="bf07-error" id="deleteReasonError" data-reason-error role="alert">
                                    <c:out value="${errors.reason}" />
                                </span>
                                <small class="bf07-muted" id="deleteReasonHint">
                                    5–500 ký tự ·
                                    <span data-reason-count>0</span>
                                    /500
                                </small>
                                <div class="bf07-dialog-actions">
                                    <button type="button" class="btn btn-outline" data-dialog-close>Hủy</button>
                                    <button type="submit" class="btn btn-danger" id="deleteSubmit">Xác nhận</button>
                                </div>
                            </form>
                        </dialog>
                    </c:if>
                </main>
            </div>
        </div>
    </body>
</html>
