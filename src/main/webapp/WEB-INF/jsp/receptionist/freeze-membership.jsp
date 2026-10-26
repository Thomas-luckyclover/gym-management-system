<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  freeze-membership.jsp — Màn hình Bảo lưu thẻ hội viên tại quầy Lễ tân (Màn #34, UC-18, Workflow BF-09).
  Thiết kế đồng bộ 100% theo phong cách màn Check-in (Bố cục 2 cột split: col-main & col-side).
--%>
<!DOCTYPE html>
<html lang="vi">

<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Bảo lưu thẻ hội viên · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/check-in.css?v=20261026">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/freeze-membership.css?v=20261026_v3">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
    <script src="${pageContext.request.contextPath}/js/pages/receptionist/freeze-membership.js?v=20261026_v3" defer></script>
    <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
</head>

<body class="dock theme-staff">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Bảo lưu thẻ hội viên</h1>
            </div>
            <div class="top-right">
                <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>
                <a class="tb-user" href="${pageContext.request.contextPath}/account" title="Cài đặt tài khoản">
                    <span class="avatar">LT</span>
                    <span class="tb-user-text">
                        <b><c:out value="${sessionScope.displayName}" default="Lễ tân" /></b>
                        <small>Lễ tân quầy</small>
                    </span>
                </a>
            </div>
        </header>

        <main class="content" id="main-content" tabindex="-1">
            <div class="split">

                <%-- CỘT CHÍNH BÊN TRÁI: Tìm khách & Thao tác bảo lưu --%>
                <div class="col-main">

                    <%-- KHỐI 1: TÌM KIẾM HỘI VIÊN --%>
                    <section class="panel" data-reveal>
                        <div class="panel-head">
                            <div>
                                <h2>Tìm khách</h2>
                                <p>Nhập mã khách (KH-xxxx), số điện thoại hoặc tên để thiết lập hoặc kiểm tra bảo lưu.</p>
                            </div>
                        </div>

                        <form id="freezeSearchForm" action="${pageContext.request.contextPath}/receptionist/freeze" method="GET" class="toolbar" novalidate>
                            <div class="search" style="flex:1;">
                                <i class="fa-solid fa-magnifying-glass"></i>
                                <input id="freezeQueryInput" name="q" type="search" value="<c:out value='${query}' />"
                                       placeholder="Nhập mã khách (KH-...), họ tên hoặc 4 số cuối SĐT..."
                                       autocomplete="off" aria-label="Mã khách, số điện thoại hoặc tên" required autofocus>
                            </div>
                            <button class="btn btn-primary" type="submit">Tìm</button>
                            <c:if test="${not empty query}">
                                <a href="${pageContext.request.contextPath}/receptionist/freeze" class="btn btn-ghost" title="Xóa tìm kiếm">Xóa Lọc</a>
                            </c:if>
                        </form>
                    </section>

                    <%-- Thông báo không tìm thấy --%>
                    <c:if test="${not empty searchError}">
                        <div class="alert-banner danger">
                            <i class="fa-solid fa-circle-xmark"></i>
                            <span><b>Không tìm thấy hồ sơ:</b> <c:out value="${searchError}" /></span>
                        </div>
                    </c:if>

                    <%-- KHỐI 2: KẾT QUẢ TÌM THEO TÊN (dạng danh sách giống màn check-in) --%>
                    <c:if test="${not empty searchResults}">
                        <section class="panel ci-search-results" data-reveal>
                            <div class="panel-head">
                                <div>
                                    <h2>Kết quả tìm kiếm</h2>
                                    <p>Tìm thấy <b><c:out value="${searchResultsPage.totalItems}" /></b> khách hàng phù hợp. Chọn khách cần bảo lưu.</p>
                                </div>
                            </div>

                            <div class="search-results-list">
                                <c:forEach var="cust" items="${searchResults}">
                                    <a href="${pageContext.request.contextPath}/receptionist/freeze?q=${cust.customerCode}" class="search-result-row">
                                        <div class="sr-photo">
                                            <i class="fa-solid fa-user"></i>
                                        </div>
                                        <div class="sr-info">
                                            <div class="sr-name"><c:out value="${cust.fullName}" /></div>
                                            <div class="sr-meta">
                                                <span><i class="fa-solid fa-id-card"></i> <c:out value="${cust.customerCode}" /></span>
                                                <span><i class="fa-solid fa-phone"></i> <c:out value="${cust.phone}" /></span>
                                            </div>
                                        </div>
                                        <div class="sr-status">
                                            <span class="badge ${cust.customerStatus == 'Active' ? 'badge-ok' : (cust.customerStatus == 'Frozen' ? 'badge-warn' : 'badge-muted')}">
                                                <c:out value="${cust.customerStatus == 'Active' ? 'Đang hoạt động' : (cust.customerStatus == 'Frozen' ? 'Đang bảo lưu' : cust.customerStatus)}" />
                                            </span>
                                        </div>
                                        <i class="fa-solid fa-chevron-right sr-arrow"></i>
                                    </a>
                                </c:forEach>
                            </div>

                            <%-- Phân trang danh sách tìm kiếm --%>
                            <c:if test="${searchResultsPage.totalPages > 1}">
                                <div class="ci-pagination">
                                    <c:choose>
                                        <c:when test="${searchResultsPage.hasPrevious}">
                                            <a class="btn btn-ghost btn-sm" href="${pageContext.request.contextPath}/receptionist/freeze?q=${query}&page=${searchResultsPage.pageNumber - 1}">
                                                <i class="fa-solid fa-chevron-left"></i> Trang trước
                                            </a>
                                        </c:when>
                                        <c:otherwise>
                                            <button class="btn btn-ghost btn-sm" type="button" disabled><i class="fa-solid fa-chevron-left"></i> Trang trước</button>
                                        </c:otherwise>
                                    </c:choose>

                                    <span class="ci-pagination-info">Trang <b>${searchResultsPage.pageNumber}</b> / <b>${searchResultsPage.totalPages}</b></span>

                                    <c:choose>
                                        <c:when test="${searchResultsPage.hasNext}">
                                            <a class="btn btn-ghost btn-sm" href="${pageContext.request.contextPath}/receptionist/freeze?q=${query}&page=${searchResultsPage.pageNumber + 1}">
                                                Trang sau <i class="fa-solid fa-chevron-right"></i>
                                            </a>
                                        </c:when>
                                        <c:otherwise>
                                            <button class="btn btn-ghost btn-sm" type="button" disabled>Trang sau <i class="fa-solid fa-chevron-right"></i></button>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </c:if>
                        </section>
                    </c:if>

                    <%-- KHỐI 3: CHI TIẾT HỘI VIÊN & NGHIỆP VỤ BẢO LƯU --%>
                    <c:if test="${not empty freezeCustomer}">
                        <section class="panel" data-reveal>
                            <%-- Thông tin hội viên & gói tập --%>
                            <div class="ci-top">
                                <div class="ci-photo<c:if test='${empty freezeCustomer.avatar and empty freezeCustomer.checkInPhoto}'> is-empty</c:if>">
                                    <c:choose>
                                        <c:when test="${not empty freezeCustomer.avatar}">
                                            <img src="${pageContext.request.contextPath}${freezeCustomer.avatar}" alt="Avatar">
                                        </c:when>
                                        <c:when test="${not empty freezeCustomer.checkInPhoto}">
                                            <img src="${pageContext.request.contextPath}${freezeCustomer.checkInPhoto}" alt="Ảnh hội viên">
                                        </c:when>
                                        <c:otherwise>
                                            <span>Chưa có<br>ảnh</span>
                                        </c:otherwise>
                                    </c:choose>
                                </div>

                                <div class="ci-id">
                                    <h2>
                                        <c:out value="${freezeCustomer.fullName}" />
                                        <span class="badge ${freezeCustomer.customerStatus == 'Active' ? 'badge-ok' : (freezeCustomer.customerStatus == 'Frozen' ? 'badge-warn' : 'badge-muted')}">
                                            <c:out value="${freezeCustomer.customerStatus == 'Active' ? 'Đang hoạt động' : (freezeCustomer.customerStatus == 'Frozen' ? 'Đang bảo lưu' : freezeCustomer.customerStatus)}" />
                                        </span>
                                    </h2>
                                    <div class="ci-meta">
                                        <span><i class="fa-solid fa-id-card"></i> <b><c:out value="${freezeCustomer.customerCode}" /></b></span>
                                        <span><i class="fa-solid fa-phone"></i> <c:out value="${freezeCustomer.phone}" /></span>
                                    </div>
                                    <div class="ci-pkg-info">
                                        <c:choose>
                                            <c:when test="${freezeCustomer.hasMembership}">
                                                <span><i class="fa-solid fa-cube"></i> Gói: <b><c:out value="${freezeCustomer.packageName}" /></b></span>
                                                <span><i class="fa-solid fa-calendar"></i> Hạn: <b><c:out value="${gms:formatDate(freezeCustomer.packageEndDate)}" /></b></span>
                                                <span><i class="fa-solid fa-snowflake"></i> Đã bảo lưu: <b><c:out value="${freezeCustomer.freezeUsed}" /> / <c:out value="${freezeCustomer.maxFreezesSnapshot}" /> lần</b></span>
                                            </c:when>
                                            <c:otherwise>
                                                <span class="text-danger"><i class="fa-solid fa-triangle-exclamation"></i> Khách hàng không có gói tập Membership nào đang chạy.</span>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </div>
                            </div>

                            <hr class="card-divider">

                            <%-- TÌNH HUỐNG 1: THẺ ĐANG TRONG KỲ BẢO LƯU --%>
                            <c:if test="${freezeCustomer.currentlyFrozen}">
                                <div class="alert-banner warning">
                                    <i class="fa-solid fa-snowflake"></i>
                                    <div>
                                        <h4>Thẻ hội viên đang trong kỳ bảo lưu</h4>
                                        <p>Thời gian bảo lưu: Từ <b><c:out value="${gms:formatDate(freezeCustomer.currentFreezeStartDate)}" /></b> đến <b><c:out value="${gms:formatDate(freezeCustomer.currentFreezePlannedEndDate)}" /></b>. Lý do: <i><c:out value="${freezeCustomer.currentFreezeReason}" /></i></p>
                                    </div>
                                </div>

                                <div class="early-cancel-box" style="margin-top:var(--space-4);">
                                    <h4 class="early-title"><i class="fa-solid fa-clock-rotate-left"></i> Mở lại thẻ tập trước hạn</h4>
                                    <p class="early-desc">
                                        Khi mở lại thẻ trước hạn, hội viên có thể tiếp tục tập luyện ngay hôm nay. Hạn thẻ được gia hạn thêm đúng số ngày thực tế đã bảo lưu tính đến hôm nay.
                                    </p>
                                    <div class="calc-box">
                                        <div class="calc-item">
                                            <span class="calc-lbl">Số ngày đã bảo lưu thực tế:</span>
                                            <b class="calc-val text-primary">${freezeCustomer.daysFrozenIfCancelledToday} ngày</b>
                                        </div>
                                        <div class="calc-item">
                                            <span class="calc-lbl">Hạn thẻ mới sau khi mở lại:</span>
                                            <b class="calc-val text-success"><c:out value="${gms:formatDate(freezeCustomer.projectedNewEndDateIfCancelledToday)}" /></b>
                                        </div>
                                    </div>

                                    <c:choose>
                                        <c:when test="${freezeCustomer.canCancelEarly}">
                                            <button type="button" class="btn btn-warning" id="btnOpenEarlyModal" style="margin-top:var(--space-3); width:100%;">
                                                <i class="fa-solid fa-unlock"></i> Xác nhận mở lại thẻ trước hạn
                                            </button>
                                        </c:when>
                                        <c:otherwise>
                                            <div class="alert-banner danger" style="margin-top:var(--space-3);">
                                                <i class="fa-solid fa-ban"></i>
                                                <span>Bạn không thể tự thao tác trên hồ sơ của chính mình. Vui lòng nhờ đồng nghiệp khác.</span>
                                            </div>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </c:if>

                            <%-- TÌNH HUỐNG 2: ĐỦ ĐIỀU KIỆN TẠO MỚI BẢO LƯU --%>
                            <c:if test="${not freezeCustomer.currentlyFrozen and freezeCustomer.eligibleToFreeze}">
                                <div class="section-title-bar">
                                    <h3>Thiết lập bảo lưu thẻ tập</h3>
                                    <span class="badge badge-ok"><i class="fa-solid fa-check"></i> Đủ điều kiện</span>
                                </div>

                                <form id="formApplyFreeze" method="POST" action="${pageContext.request.contextPath}/receptionist/freeze" novalidate>
                                    <input type="hidden" name="action" value="applyFreeze">
                                    <input type="hidden" name="subscriptionId" value="${freezeCustomer.subscriptionId}">
                                    <input type="hidden" name="q" value="<c:out value='${query}' />">
                                    <input type="hidden" id="customerIdHidden" value="${freezeCustomer.customerId}">
                                    <input type="hidden" id="packageEndDateHidden" value="${freezeCustomer.packageEndDate}">

                                    <div class="form-grid">
                                        <div class="form-group">
                                            <label for="startDate">Ngày bắt đầu bảo lưu: <span class="req">*</span></label>
                                            <input type="date" id="startDate" name="startDate" class="form-control"
                                                   min="${freezeCustomer.minStartDate}"
                                                   value="${freezeCustomer.minStartDate}" required>
                                            <small class="hint">Áp dụng sớm nhất từ ngày mai (<c:out value="${gms:formatDate(freezeCustomer.minStartDate)}" />).</small>
                                        </div>

                                        <div class="form-group">
                                            <label for="plannedEndDate">Ngày kết thúc dự kiến: <span class="req">*</span></label>
                                            <input type="date" id="plannedEndDate" name="plannedEndDate" class="form-control"
                                                   min="${freezeCustomer.minStartDate}"
                                                   max="${freezeCustomer.maxEndDate}"
                                                   value="${freezeCustomer.maxEndDate}" required>
                                            <small class="hint">Tối đa 1 tháng (đến ngày <c:out value="${gms:formatDate(freezeCustomer.maxEndDate)}" />).</small>
                                        </div>
                                    </div>

                                    <%-- Tóm tắt tính toán tự động --%>
                                    <div class="calc-callout">
                                        <div class="callout-line">
                                            <span>Số ngày bảo lưu dự kiến:</span>
                                            <b id="displayDaysCount" class="text-primary">-- ngày</b>
                                        </div>
                                        <div class="callout-line">
                                            <span>Hạn thẻ mới sau khi tự động mở lại:</span>
                                            <b id="displayNewExpiryDate" class="text-success">--/--/----</b>
                                        </div>
                                    </div>

                                    <div class="form-group" style="margin-top:var(--space-3);">
                                        <label for="freezeReason">Lý do bảo lưu: <span class="req">*</span></label>
                                        <textarea id="freezeReason" name="reason" class="form-control" rows="2"
                                                  placeholder="Nhập lý do bảo lưu (ví dụ: công tác, chấn thương... tối thiểu 5 ký tự)"
                                                  minlength="5" maxlength="500" required></textarea>
                                    </div>

                                    <%-- Cảnh báo buổi tập PT trùng kỳ bảo lưu --%>
                                    <div class="pt-warning-box">
                                        <div class="pt-warning-header">
                                            <h4><i class="fa-solid fa-calendar-xmark text-danger"></i> Buổi tập PT trong thời gian bảo lưu (<span id="impactCount">0</span> buổi)</h4>
                                        </div>
                                        <p class="pt-warning-text">
                                            Lưu ý: Các buổi tập PT đã lên lịch sẽ tự động ghi nhận vắng (không hoàn buổi). Huấn luyện viên sẽ được giải phóng lịch để tiếp nhận học viên khác.
                                        </p>
                                        <div id="impactSessionsContainer">
                                            <div class="empty-pt-note" id="noSessionsNotice">
                                                <i class="fa-solid fa-circle-check"></i> Không có buổi tập PT nào trong khoảng thời gian này.
                                            </div>
                                        </div>
                                    </div>

                                    <button type="submit" class="btn btn-primary" id="btnSubmitFreeze" style="margin-top:var(--space-4); width:100%;">
                                        <i class="fa-solid fa-snowflake"></i> Xác nhận bảo lưu thẻ tập
                                    </button>
                                </form>
                            </c:if>

                            <%-- TÌNH HUỐNG 3: KHÔNG ĐỦ ĐIỀU KIỆN --%>
                            <c:if test="${not freezeCustomer.currentlyFrozen and not freezeCustomer.eligibleToFreeze}">
                                <div class="alert-banner danger" style="margin-top:var(--space-4);">
                                    <i class="fa-solid fa-circle-xmark"></i>
                                    <div>
                                        <h4>Không thể tạo bảo lưu cho gói này</h4>
                                        <p><c:out value="${freezeCustomer.ineligibilityReason}" /></p>
                                    </div>
                                </div>
                            </c:if>
                        </section>

                        <%-- Lịch sử các lần bảo lưu của hội viên này --%>
                        <c:if test="${not empty freezeCustomer.freezeHistory}">
                            <section class="panel" data-reveal>
                                <div class="panel-head">
                                    <div>
                                        <h3>Lịch sử bảo lưu của hội viên</h3>
                                        <p>Các đợt bảo lưu đã từng thực hiện trên hồ sơ này.</p>
                                    </div>
                                </div>
                                <div class="freeze-history-list">
                                    <c:forEach var="item" items="${freezeCustomer.freezeHistory}">
                                        <div class="freeze-history-item">
                                            <div class="item-left">
                                                <div class="history-dates">
                                                    <b><c:out value="${gms:formatDate(item.startDate)}" /></b> đến
                                                    <c:choose>
                                                        <c:when test="${not empty item.actualEndDate}">
                                                            <b><c:out value="${gms:formatDate(item.actualEndDate)}" /></b> (Thực tế)
                                                        </c:when>
                                                        <c:otherwise>
                                                            <b><c:out value="${gms:formatDate(item.plannedEndDate)}" /></b> (Dự kiến)
                                                        </c:otherwise>
                                                    </c:choose>
                                                    · <span class="text-primary">${item.daysFrozen} ngày</span>
                                                </div>
                                                <div class="history-reason">Lý do: <c:out value="${item.reason}" /></div>
                                                <div class="history-staff">Lễ tân lập: <c:out value="${item.handledByEmployeeName}" /></div>
                                            </div>
                                            <div class="item-right">
                                                <span class="badge ${item.statusBadgeClass}"><c:out value="${item.statusLabel}" /></span>
                                            </div>
                                        </div>
                                    </c:forEach>
                                </div>
                            </section>
                        </c:if>
                    </c:if>

                </div>

                <%-- CỘT PHỤ BÊN PHẢI (col-side): Các đợt bảo lưu gần đây (GIỐNG HỆT check-in.jsp) --%>
                <aside class="col-side">
                    <div class="side-card">
                        <span class="section-label">Đợt bảo lưu gần đây</span>
                        <div class="data-list plain" id="freezeRecentList">
                            <c:choose>
                                <c:when test="${not empty recentFreezes}">
                                    <c:forEach var="item" items="${recentFreezes}">
                                        <a href="${pageContext.request.contextPath}/receptionist/freeze?q=${item.customerCode}" class="data-row" style="text-decoration:none; color:inherit;">
                                            <div class="row-main">
                                                <div class="row-title">
                                                    <c:out value="${item.customerName}" />
                                                </div>
                                                <div class="row-sub">
                                                    <c:out value="${item.customerCode}" /> · <c:out value="${item.packageName}" />
                                                </div>
                                                <div class="row-sub" style="font-size:11px; color:var(--color-text-soft);">
                                                    <c:out value="${gms:formatDate(item.startDate)}" /> - <c:out value="${gms:formatDate(item.plannedEndDate)}" />
                                                </div>
                                            </div>
                                            <div style="flex-shrink:0;">
                                                <span class="badge ${item.statusBadgeClass}"><c:out value="${item.statusLabel}" /></span>
                                            </div>
                                        </a>
                                    </c:forEach>
                                </c:when>
                                <c:otherwise>
                                    <div style="font-size:var(--font-size-md);color:var(--color-text-muted);padding:var(--space-2) 0;">
                                        Chưa có đợt bảo lưu nào gần đây.
                                    </div>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </div>
                </aside>

            </div>
        </main>
    </div>
</div>

<%-- MODAL MỞ LẠI THẺ TRƯỚC HẠN (Chuẩn modal-overlay của check-in.jsp) --%>
<c:if test="${not empty freezeCustomer and freezeCustomer.currentlyFrozen and freezeCustomer.canCancelEarly}">
    <div class="modal-overlay" id="modalEarlyCancel" role="dialog" aria-modal="true" aria-labelledby="modalEarlyTitle">
        <div class="modal-box">
            <div class="modal-head">
                <h3 id="modalEarlyTitle"><i class="fa-solid fa-unlock text-warn"></i> Mở lại thẻ trước hạn</h3>
                <button class="btn btn-ghost btn-sm" type="button" data-close-modal aria-label="Đóng">&times;</button>
            </div>
            <form method="POST" action="${pageContext.request.contextPath}/receptionist/freeze">
                <input type="hidden" name="action" value="cancelFreezeEarly">
                <input type="hidden" name="freezeRecordId" value="${freezeCustomer.openFreezeRecordId}">
                <input type="hidden" name="q" value="<c:out value='${query}' />">

                <p style="font-size:var(--font-size-md);color:var(--color-text);margin-bottom:var(--space-3);">
                    Xác nhận mở lại thẻ tập cho hội viên <b><c:out value="${freezeCustomer.fullName}" /></b> (Mã: <b><c:out value="${freezeCustomer.customerCode}" /></b>)?
                </p>

                <div class="calc-box" style="margin-bottom:var(--space-3);">
                    <div class="calc-item">
                        <span class="calc-lbl">Số ngày đã bảo lưu thực tế:</span>
                        <b class="calc-val text-primary">${freezeCustomer.daysFrozenIfCancelledToday} ngày</b>
                    </div>
                    <div class="calc-item">
                        <span class="calc-lbl">Hạn thẻ mới sau khi mở lại:</span>
                        <b class="calc-val text-success"><c:out value="${gms:formatDate(freezeCustomer.projectedNewEndDateIfCancelledToday)}" /></b>
                    </div>
                </div>

                <div class="alert-banner warning" style="margin-bottom:var(--space-4);">
                    <i class="fa-solid fa-triangle-exclamation"></i>
                    <span><b>Lưu ý:</b> Lượt bảo lưu của gói đã được tính. Các buổi PT đã ghi nhận vắng trước đó sẽ không tự động phục hồi.</span>
                </div>

                <div class="btn-row" style="justify-content:flex-end; gap:var(--space-3);">
                    <button class="btn btn-ghost" type="button" data-close-modal>Bỏ qua</button>
                    <button class="btn btn-warning" type="submit"><i class="fa-solid fa-unlock"></i> Xác nhận mở lại</button>
                </div>
            </form>
        </div>
    </div>
</c:if>

</body>
</html>
