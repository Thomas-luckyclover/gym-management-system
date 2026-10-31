<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  freeze-membership.jsp — Màn hình Bảo lưu gói tập của Lễ tân (Màn #34, UC-18, Workflow BF-09).
  Thiết kế tối ưu theo phong cách hệ thống quản lý phòng tập:
  - Khối 1: Thanh tra cứu hội viên & lọc nhanh.
  - Khối 2: Chi tiết thao tác bảo lưu / mở lại thẻ (khi đã chọn hội viên).
  - Khối 3: Bảng dữ liệu quản lý hội viên chuẩn nghiệp vụ phòng gym (5 cột tinh gọn, không cuộn ngang).
--%>
<!DOCTYPE html>
<html lang="vi">

<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Bảo lưu gói tập · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/freeze-membership.css?v=20261027_v8">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
    <script src="${pageContext.request.contextPath}/js/pages/receptionist/freeze-membership.js?v=20261027_v8" defer></script>
    <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
</head>

<body class="dock theme-staff">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
        <header class="topbar">
            <div class="tb-left">
                <h1 class="tb-title">Bảo lưu gói tập</h1>
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

            <%-- KHỐI 1: THANH TRA CỨU HỘI VIÊN & ĐIỀU HƯỚNG NHANH --%>
            <section class="panel freeze-search-panel">
                <div class="search-panel-inner">
                    <div class="search-title-wrap">
                        <h2><i class="fa-solid fa-snowflake text-primary"></i> Quản lý bảo lưu gói tập</h2>
                    </div>

                    <form id="freezeSearchForm" action="${pageContext.request.contextPath}/receptionist/freeze" method="GET" class="toolbar search-toolbar" novalidate>
                        <div class="search" style="flex:1;">
                            <i class="fa-solid fa-magnifying-glass"></i>
                            <input id="freezeQueryInput" name="q" type="search" value="<c:out value='${query}' />"
                                   placeholder="Nhập mã khách (KH-...), họ tên hoặc 4 số cuối SĐT..."
                                   autocomplete="off" aria-label="Mã khách, số điện thoại hoặc tên" autofocus>
                        </div>
                        <div class="search-filter-wrap">
                            <select id="freezeFilterSelect" name="filter" class="form-control freeze-filter-select" aria-label="Lọc theo trạng thái bảo lưu">
                                <option value="ALL" <c:if test="${empty currentFilter or currentFilter == 'ALL'}">selected</c:if>>Tất cả hội viên</option>
                                <option value="FROZEN" <c:if test="${currentFilter == 'FROZEN'}">selected</c:if>>Đang bảo lưu</option>
                                <option value="ELIGIBLE" <c:if test="${currentFilter == 'ELIGIBLE'}">selected</c:if>>Đủ điều kiện bảo lưu</option>
                                <option value="ACTIVE" <c:if test="${currentFilter == 'ACTIVE'}">selected</c:if>>Gói đang hoạt động</option>
                            </select>
                        </div>
                        <button class="btn btn-primary" type="submit"><i class="fa-solid fa-search"></i> Tìm kiếm</button>
                        <c:if test="${not empty query or (not empty currentFilter and currentFilter != 'ALL')}">
                            <a href="${pageContext.request.contextPath}/receptionist/freeze" class="btn btn-ghost" title="Xóa tất cả bộ lọc"><i class="fa-solid fa-xmark"></i> Xóa lọc</a>
                        </c:if>
                    </form>
                </div>

                <%-- Thông báo không tìm thấy --%>
                <c:if test="${not empty searchError}">
                    <div class="alert-banner danger" style="margin-top:var(--space-3);">
                        <i class="fa-solid fa-circle-xmark"></i>
                        <span><b>Không tìm thấy:</b> <c:out value="${searchError}" /></span>
                    </div>
                </c:if>
            </section>

            <%-- KHỐI 2: CHI TIẾT & THAO TÁC NGHIỆP VỤ (HIỂN THỊ KHI ĐÃ CHỌN HỘI VIÊN) --%>
            <c:if test="${not empty freezeCustomer}">
                <div class="split customer-action-split" style="margin-top:var(--space-4);">

                    <%-- CỘT CHÍNH: HỒ SƠ & BIỂU MẪU THAO TÁC --%>
                    <div class="col-main">
                        <section class="panel freeze-panel">
                            <%-- Header hội viên (Dùng hình người chung cho tất cả hội viên) --%>
                            <div class="freeze-cust-header">
                                <div class="common-user-avatar lg">
                                    <i class="fa-solid fa-user"></i>
                                </div>
                                <div class="cust-details">
                                    <div class="cust-name-line">
                                        <h2><c:out value="${freezeCustomer.fullName}" /></h2>
                                        <span class="badge ${freezeCustomer.customerStatus == 'Active' ? 'badge-ok' : (freezeCustomer.customerStatus == 'Frozen' ? 'badge-warn' : 'badge-muted')}">
                                            <c:out value="${freezeCustomer.customerStatus == 'Active' ? 'Đang hoạt động' : (freezeCustomer.customerStatus == 'Frozen' ? 'Đang bảo lưu' : freezeCustomer.customerStatus)}" />
                                        </span>
                                        <a href="${pageContext.request.contextPath}/receptionist/freeze" class="btn btn-ghost btn-sm" style="margin-left:auto;" title="Đóng chi tiết và xem toàn bộ danh sách">
                                            <i class="fa-solid fa-xmark"></i> Đóng chi tiết
                                        </a>
                                    </div>
                                    <div class="cust-meta-line">
                                        <span><i class="fa-solid fa-id-card"></i> Mã: <b><c:out value="${freezeCustomer.customerCode}" /></b></span>
                                        <span><i class="fa-solid fa-phone"></i> SĐT: <c:out value="${freezeCustomer.phone}" /></span>
                                    </div>
                                </div>
                            </div>

                            <hr class="card-divider">

                            <%-- TÌNH HUỐNG A: THẺ ĐANG ĐƯỢC BẢO LƯU --%>
                            <c:if test="${freezeCustomer.currentlyFrozen}">
                                <div class="freeze-status-banner">
                                    <div class="banner-icon"><i class="fa-solid fa-snowflake"></i></div>
                                    <div class="banner-content">
                                        <h3>Gói tập đang trong thời gian bảo lưu</h3>
                                        <p>Từ ngày <b><c:out value="${gms:formatDate(freezeCustomer.currentFreezeStartDate)}" /></b> đến ngày <b><c:out value="${gms:formatDate(freezeCustomer.currentFreezePlannedEndDate)}" /></b>.</p>
                                        <div class="meta-note">
                                            <span>Lễ tân tiếp nhận: <b><c:out value="${freezeCustomer.currentFreezeHandledBy}" /></b></span> · 
                                            <span>Lý do: <i><c:out value="${freezeCustomer.currentFreezeReason}" /></i></span>
                                        </div>
                                    </div>
                                </div>

                                <div class="early-resume-card" style="margin-top:var(--space-4);">
                                    <h4><i class="fa-solid fa-clock-rotate-left"></i> Mở lại thẻ tập trước hạn</h4>
                                    <p class="desc">Khi mở lại thẻ trước hạn, hội viên có thể tiếp tục tập luyện ngay hôm nay. Hạn thẻ được gia hạn thêm đúng số ngày thực tế đã bảo lưu tính đến hôm nay.</p>
                                    
                                    <div class="calc-highlight-box">
                                        <div class="calc-col">
                                            <span class="lbl">Số ngày đã bảo lưu thực tế:</span>
                                            <b class="val text-primary">${freezeCustomer.daysFrozenIfCancelledToday} ngày</b>
                                        </div>
                                        <div class="calc-col">
                                            <span class="lbl">Hạn thẻ mới sau khi mở lại:</span>
                                            <b class="val text-success"><c:out value="${gms:formatDate(freezeCustomer.projectedNewEndDateIfCancelledToday)}" /></b>
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

                            <%-- TÌNH HUỐNG B: ĐỦ ĐIỀU KIỆN LẬP PHIẾU BẢO LƯU --%>
                            <c:if test="${not freezeCustomer.currentlyFrozen and freezeCustomer.eligibleToFreeze}">
                                <div class="freeze-form-header">
                                    <h3>Thiết lập thời gian bảo lưu</h3>
                                    <span class="badge badge-ok"><i class="fa-solid fa-circle-check"></i> Đủ điều kiện</span>
                                </div>

                                <form id="formApplyFreeze" method="POST" action="${pageContext.request.contextPath}/receptionist/freeze" novalidate>
                                    <input type="hidden" name="action" value="applyFreeze">
                                    <input type="hidden" name="subscriptionId" value="${freezeCustomer.subscriptionId}">
                                    <input type="hidden" name="q" value="<c:out value='${query}' />">
                                    <input type="hidden" name="filter" value="<c:out value='${currentFilter}' />">
                                    <input type="hidden" id="customerIdHidden" value="${freezeCustomer.customerId}">
                                    <input type="hidden" id="packageEndDateHidden" value="${freezeCustomer.packageEndDate}">

                                    <div class="freeze-dates-grid">
                                        <div class="form-group">
                                            <label for="startDate">Ngày bắt đầu: <span class="req">*</span></label>
                                            <input type="date" id="startDate" name="startDate" class="form-control"
                                                   min="${freezeCustomer.minStartDate}"
                                                   value="${freezeCustomer.minStartDate}" required>
                                            <small class="hint">Sớm nhất từ ngày mai (<c:out value="${gms:formatDate(freezeCustomer.minStartDate)}" />).</small>
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

                                    <%-- Callout tính toán ngày tự động --%>
                                    <div class="freeze-calc-bar">
                                        <div class="calc-row">
                                            <span>Số ngày bảo lưu dự kiến:</span>
                                            <b id="displayDaysCount" class="text-primary">-- ngày</b>
                                        </div>
                                        <div class="calc-row">
                                            <span>Hạn thẻ mới sau khi mở lại:</span>
                                            <b id="displayNewExpiryDate" class="text-success">--/--/----</b>
                                        </div>
                                    </div>

                                    <div class="form-group" style="margin-top:var(--space-3);">
                                        <label for="freezeReason">Lý do bảo lưu: <span class="req">*</span></label>
                                        <textarea id="freezeReason" name="reason" class="form-control" rows="2"
                                                  placeholder="Nhập lý do bảo lưu (ví dụ: công tác, chấn thương... tối thiểu 5 ký tự)"
                                                  minlength="5" maxlength="500" required></textarea>
                                    </div>

                                    <%-- Cảnh báo buổi tập PT trong kỳ bảo lưu (chỉ hiển thị khi có buổi PT bị ảnh hưởng) --%>
                                    <div class="pt-impact-box" id="ptImpactBox" style="display:none;">
                                        <div class="pt-impact-head">
                                            <h4><i class="fa-solid fa-calendar-xmark text-danger"></i> Buổi tập PT trong thời gian bảo lưu (<span id="impactCount">0</span> buổi)</h4>
                                        </div>
                                        <p class="pt-impact-text">
                                            Lưu ý: Các buổi tập PT đã lên lịch sẽ tự động ghi nhận vắng (không hoàn buổi). Huấn luyện viên sẽ được giải phóng lịch để tiếp nhận học viên khác.
                                        </p>
                                        <div id="impactSessionsContainer">
                                            <div class="empty-pt-note" id="noSessionsNotice">
                                                <i class="fa-solid fa-circle-check"></i> Không có buổi tập PT nào trong khoảng thời gian này.
                                            </div>
                                        </div>
                                    </div>

                                    <button type="submit" class="btn btn-primary" id="btnSubmitFreeze" style="margin-top:var(--space-4); width:100%;">
                                        <i class="fa-solid fa-snowflake"></i> Lưu bảo lưu gói tập
                                    </button>
                                </form>
                            </c:if>

                            <%-- TÌNH HUỐNG C: KHÔNG ĐỦ ĐIỀU KIỆN --%>
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

                        <%-- LỊCH SỬ BẢO LƯU CỦA RIÊNG HỘI VIÊN NÀY --%>
                        <c:if test="${not empty freezeCustomer.freezeHistory}">
                            <section class="panel freeze-panel" style="margin-top:var(--space-4);">
                                <div class="panel-head">
                                    <div>
                                        <h3>Lịch sử bảo lưu của hội viên này</h3>
                                        <p>Các đợt bảo lưu đã từng thực hiện trên hồ sơ này.</p>
                                    </div>
                                </div>
                                <div class="history-card-list">
                                    <c:forEach var="item" items="${freezeCustomer.freezeHistory}">
                                        <div class="history-card-item">
                                            <div class="history-item-body">
                                                <div class="dates-line">
                                                    <b><c:out value="${gms:formatDate(item.startDate)}" /></b> đến
                                                    <c:choose>
                                                        <c:when test="${not empty item.actualEndDate}">
                                                            <b><c:out value="${gms:formatDate(item.actualEndDate)}" /></b> (Thực tế)
                                                        </c:when>
                                                        <c:otherwise>
                                                            <b><c:out value="${gms:formatDate(item.plannedEndDate)}" /></b> (Dự kiến)
                                                        </c:otherwise>
                                                    </c:choose>
                                                    · <span class="badge badge-info">${item.daysFrozen} ngày</span>
                                                </div>
                                                <div class="sub-line">Lý do: <c:out value="${item.reason}" /></div>
                                                <div class="staff-line">Lễ tân tiếp nhận: <c:out value="${item.handledByEmployeeName}" /></div>
                                            </div>
                                            <div class="history-item-status">
                                                <span class="badge ${item.statusBadgeClass}"><c:out value="${item.statusLabel}" /></span>
                                            </div>
                                        </div>
                                    </c:forEach>
                                </div>
                            </section>
                        </c:if>
                    </div>

                    <%-- CỘT PHẢI: GÓI HIỆN TẠI & QUY TẮC NGHIỆP VỤ --%>
                    <aside class="col-side">
                        <div class="side-card">
                            <span class="section-label">Gói hiện tại</span>
                            <c:choose>
                                <c:when test="${not empty freezeCustomer and freezeCustomer.hasMembership}">
                                    <div class="current-pkg-box">
                                        <div class="pkg-top-title">
                                            <h4><c:out value="${freezeCustomer.packageName}" /></h4>
                                            <span class="badge ${freezeCustomer.subscriptionStatus == 'Active' ? 'badge-ok' : (freezeCustomer.subscriptionStatus == 'Frozen' ? 'badge-warn' : 'badge-muted')}">
                                                <c:out value="${freezeCustomer.subscriptionStatus == 'Active' ? 'Hoạt động' : (freezeCustomer.subscriptionStatus == 'Frozen' ? 'Bảo lưu' : freezeCustomer.subscriptionStatus)}" />
                                            </span>
                                        </div>
                                        <div class="pkg-info-rows">
                                            <div class="info-row">
                                                <span class="lbl">Bắt đầu:</span>
                                                <span class="val"><c:out value="${gms:formatDate(freezeCustomer.packageStartDate)}" /></span>
                                            </div>
                                            <div class="info-row">
                                                <span class="lbl">Hết hạn:</span>
                                                <span class="val"><b><c:out value="${gms:formatDate(freezeCustomer.packageEndDate)}" /></b></span>
                                            </div>
                                            <div class="info-row">
                                                <span class="lbl">Lượt bảo lưu:</span>
                                                <span class="val">
                                                    <b><c:out value="${freezeCustomer.freezeUsed}" /> / <c:out value="${freezeCustomer.maxFreezesSnapshot}" /></b> lần
                                                    <c:if test="${freezeCustomer.freezeUsed >= freezeCustomer.maxFreezesSnapshot}">
                                                        <span class="text-danger" style="font-size:11px;">(Đã hết)</span>
                                                    </c:if>
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                </c:when>
                                <c:otherwise>
                                    <p class="muted" style="margin:var(--space-2) 0 0 0; font-size:var(--font-size-sm); color:var(--color-danger);">
                                        <i class="fa-solid fa-triangle-exclamation"></i> Hội viên không có gói tập nào đang chạy.
                                    </p>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </aside>

                </div>
            </c:if>

            <%-- KHỐI 3: TOÀN BỘ BẢNG DỮ LIỆU GYMMASTER & LỊCH SỬ TOÀN HỆ THỐNG (FULL-WIDTH) --%>
            <section class="freeze-tabs-card" style="margin-top:var(--space-4);">
                <div class="freeze-tabs-nav">
                    <button type="button" class="freeze-tab-btn active" data-target="#tabMembers">
                        <i class="fa-solid fa-users"></i> Danh sách hội viên & Gói tập
                        <span class="tab-badge"><c:out value="${membershipsPage.totalItems}" default="0" /></span>
                    </button>
                    <button type="button" class="freeze-tab-btn" data-target="#tabHistory">
                        <i class="fa-solid fa-clock-rotate-left"></i> Lịch sử bảo lưu
                        <span class="tab-badge"><c:out value="${freezeHistoryList.size()}" default="0" /></span>
                    </button>
                </div>

                <%-- TAB 1: DANH SÁCH HỘI VIÊN & GÓI TẬP (5 CỘT CHUẨN NGHIỆP VỤ GYM, KHÔNG CUỘN NGANG) --%>
                <div class="freeze-tab-pane active" id="tabMembers">
                    <div class="gym-table-header-bar">
                        <div class="header-titles">
                            <h3><i class="fa-solid fa-table-list text-primary"></i> Quản lý gói tập hội viên</h3>
                            <p>Nhấp vào bất kỳ dòng nào để chọn hội viên và thực hiện bảo lưu hoặc mở lại thẻ.</p>
                        </div>
                        <c:if test="${not empty query or (not empty currentFilter and currentFilter != 'ALL')}">
                            <div class="filter-indicator">
                                <span>Đang lọc:
                                    <c:if test="${not empty query}"><b>"<c:out value='${query}' />"</b></c:if>
                                    <c:if test="${not empty query and not empty currentFilter and currentFilter != 'ALL'}"> · </c:if>
                                    <c:choose>
                                        <c:when test="${currentFilter == 'FROZEN'}"><b class="text-warn">Đang bảo lưu</b></c:when>
                                        <c:when test="${currentFilter == 'ELIGIBLE'}"><b class="text-success">Đủ điều kiện</b></c:when>
                                        <c:when test="${currentFilter == 'ACTIVE'}"><b class="text-primary">Đang hoạt động</b></c:when>
                                    </c:choose>
                                </span>
                                <a href="${pageContext.request.contextPath}/receptionist/freeze" class="btn btn-ghost btn-xs">Xóa bộ lọc</a>
                            </div>
                        </c:if>
                    </div>

                    <div class="gym-table-wrap">
                        <table class="gym-table">
                            <thead>
                                <tr>
                                    <th style="width: 28%;">
                                        <div class="th-cust-flex">
                                            <span class="th-avatar-space"></span>
                                            <span>Hội viên</span>
                                        </div>
                                    </th>
                                    <th style="width: 22%;">Gói tập & Trạng thái</th>
                                    <th style="width: 19%;">Thời hạn gói tập</th>
                                    <th style="width: 18%;">Tình trạng bảo lưu</th>
                                    <th style="width: 13%; text-align: right;">Thao tác</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:choose>
                                    <c:when test="${not empty customerMemberships}">
                                        <c:forEach var="row" items="${customerMemberships}">
                                            <tr class="row-clickable" data-href="${pageContext.request.contextPath}/receptionist/freeze?q=${row.customerCode}">
                                                <td>
                                                    <div class="gym-cust-cell">
                                                        <div class="common-user-avatar sm">
                                                            <i class="fa-solid fa-user"></i>
                                                        </div>
                                                        <div class="gym-cust-info">
                                                            <span class="cust-name"><c:out value="${row.fullName}" /></span>
                                                            <div class="cust-meta">
                                                                <span class="cust-code"><c:out value="${row.customerCode}" /></span>
                                                                <span class="cust-dot">·</span>
                                                                <span class="cust-phone"><i class="fa-solid fa-phone"></i> <c:out value="${row.phone}" /></span>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td>
                                                    <div class="gym-pkg-cell">
                                                        <c:choose>
                                                            <c:when test="${row.hasMembership()}">
                                                                <span class="pkg-name">
                                                                    <i class="fa-solid fa-box-archive text-warning"></i> <c:out value="${row.packageName}" />
                                                                </span>
                                                                <span class="pkg-sub-status">
                                                                    <c:choose>
                                                                        <c:when test="${row.subscriptionStatus == 'Frozen'}">
                                                                            <span class="status-pill warn"><i class="fa-solid fa-snowflake"></i> Đang bảo lưu</span>
                                                                        </c:when>
                                                                        <c:when test="${row.subscriptionStatus == 'Active'}">
                                                                            <span class="status-pill ok"><i class="fa-solid fa-circle-check"></i> Đang hoạt động</span>
                                                                        </c:when>
                                                                        <c:otherwise>
                                                                            <span class="status-pill">${row.subscriptionStatus}</span>
                                                                        </c:otherwise>
                                                                    </c:choose>
                                                                </span>
                                                            </c:when>
                                                            <c:otherwise>
                                                                <span class="muted" style="font-size:var(--font-size-xs);">Chưa có gói tập</span>
                                                            </c:otherwise>
                                                        </c:choose>
                                                    </div>
                                                </td>
                                                <td>
                                                    <div class="gym-dates-cell">
                                                        <c:choose>
                                                            <c:when test="${row.hasMembership() and not empty row.startDate}">
                                                                <div>Bắt đầu: <b><c:out value="${gms:formatDate(row.startDate)}" /></b></div>
                                                                <div>Hết hạn: <b><c:out value="${gms:formatDate(row.endDate)}" /></b></div>
                                                            </c:when>
                                                            <c:otherwise>
                                                                <span class="muted">--</span>
                                                            </c:otherwise>
                                                        </c:choose>
                                                    </div>
                                                </td>
                                                <td class="gym-status-cell">
                                                    <span class="badge ${row.freezeStatusBadgeClass}">
                                                        <c:out value="${row.freezeStatusLabel}" />
                                                    </span>
                                                </td>
                                                <td class="gym-action-cell" style="text-align: right;">
                                                    <a href="${pageContext.request.contextPath}/receptionist/freeze?q=${row.customerCode}"
                                                       class="btn btn-ghost btn-sm" title="Chọn khách hàng">
                                                        <c:choose>
                                                            <c:when test="${row.isCurrentlyFrozen()}">
                                                                <i class="fa-solid fa-unlock text-warn"></i> Mở lại
                                                            </c:when>
                                                            <c:when test="${row.isEligibleToFreeze()}">
                                                                <i class="fa-solid fa-snowflake text-primary"></i> Bảo lưu
                                                            </c:when>
                                                            <c:otherwise>
                                                                <i class="fa-solid fa-eye"></i> Xem
                                                            </c:otherwise>
                                                        </c:choose>
                                                    </a>
                                                </td>
                                            </tr>
                                        </c:forEach>
                                    </c:when>
                                    <c:otherwise>
                                        <tr>
                                            <td colspan="5" style="text-align:center; padding:var(--space-6); color:var(--color-text-muted);">
                                                <i class="fa-solid fa-circle-info" style="font-size:24px; display:block; margin-bottom:var(--space-2);"></i>
                                                Không tìm thấy hội viên nào phù hợp với từ khóa tra cứu.
                                            </td>
                                        </tr>
                                    </c:otherwise>
                                </c:choose>
                            </tbody>
                        </table>
                    </div>

                    <%-- Phân trang danh sách hội viên --%>
                    <c:if test="${membershipsPage.totalPages > 1}">
                        <div class="freeze-pagination" style="margin-top:var(--space-3);">
                            <c:choose>
                                <c:when test="${membershipsPage.hasPrevious}">
                                    <a class="btn btn-ghost btn-sm" href="${pageContext.request.contextPath}/receptionist/freeze?q=${query}&filter=${currentFilter}&page=${membershipsPage.pageNumber - 1}">
                                        <i class="fa-solid fa-chevron-left"></i> Trang trước
                                    </a>
                                </c:when>
                                <c:otherwise>
                                    <button class="btn btn-ghost btn-sm" type="button" disabled><i class="fa-solid fa-chevron-left"></i> Trang trước</button>
                                </c:otherwise>
                            </c:choose>

                            <span class="freeze-pagination-info">Trang <b>${membershipsPage.pageNumber}</b> / <b>${membershipsPage.totalPages}</b> (Tổng <b>${membershipsPage.totalItems}</b> hội viên)</span>

                            <c:choose>
                                <c:when test="${membershipsPage.hasNext}">
                                    <a class="btn btn-ghost btn-sm" href="${pageContext.request.contextPath}/receptionist/freeze?q=${query}&filter=${currentFilter}&page=${membershipsPage.pageNumber + 1}">
                                        Trang sau <i class="fa-solid fa-chevron-right"></i>
                                    </a>
                                </c:when>
                                <c:otherwise>
                                    <button class="btn btn-ghost btn-sm" type="button" disabled>Trang sau <i class="fa-solid fa-chevron-right"></i></button>
                                </c:otherwise>
                            </c:choose>
                        </div>
                    </c:if>
                </div>

                <%-- TAB 2: LỊCH SỬ BẢO LƯU TOÀN HỆ THỐNG --%>
                <div class="freeze-tab-pane" id="tabHistory">
                    <div class="gym-table-header-bar">
                        <div class="header-titles">
                            <h3><i class="fa-solid fa-clock-rotate-left text-primary"></i> Nhật ký lịch sử bảo lưu phòng tập</h3>
                            <p>Toàn bộ các đợt bảo lưu đã và đang diễn ra tại phòng tập.</p>
                        </div>

                        <div class="history-table-toolbar">
                            <form id="historySearchForm" action="${pageContext.request.contextPath}/receptionist/freeze" method="GET" class="history-search-form" novalidate>
                                <div class="search history-search-wrap">
                                    <i class="fa-solid fa-magnifying-glass"></i>
                                    <input id="historySearchInput" name="q" type="search" value="<c:out value='${query}' />"
                                           placeholder="Tìm theo tên hội viên, mã KH, SĐT..."
                                           autocomplete="off" aria-label="Tìm kiếm lịch sử bảo lưu">
                                </div>
                                <button class="btn btn-primary btn-sm" type="submit"><i class="fa-solid fa-search"></i> Tìm</button>
                                <c:if test="${not empty query}">
                                    <a href="${pageContext.request.contextPath}/receptionist/freeze#tabHistory" class="btn btn-ghost btn-sm" title="Xóa tìm kiếm"><i class="fa-solid fa-xmark"></i> Xóa lọc</a>
                                </c:if>
                            </form>
                            <c:if test="${not empty query}">
                                <div class="filter-indicator">
                                    <span>Đang lọc: <b>"<c:out value='${query}' />"</b></span>
                                    <a href="${pageContext.request.contextPath}/receptionist/freeze#tabHistory" class="btn btn-ghost btn-xs">Xóa bộ lọc</a>
                                </div>
                            </c:if>
                        </div>
                    </div>

                    <div class="gym-table-wrap">
                        <table class="gym-table">
                            <thead>
                                <tr>
                                    <th style="width: 23%;">
                                        <div class="th-cust-flex">
                                            <span class="th-avatar-space"></span>
                                            <span>Hội viên</span>
                                        </div>
                                    </th>
                                    <th style="width: 12%;">Gói tập</th>
                                    <th style="width: 16%;">Thời gian bảo lưu</th>
                                    <th style="width: 7%; text-align: center;">Số ngày</th>
                                    <th style="width: 11%; text-align: center;">Trạng thái</th>
                                    <th style="width: 16%;">Lý do bảo lưu</th>
                                    <th style="width: 15%;">Lễ tân tiếp nhận</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:choose>
                                    <c:when test="${not empty freezeHistoryList}">
                                        <c:forEach var="item" items="${freezeHistoryList}">
                                            <tr class="row-clickable" data-href="${pageContext.request.contextPath}/receptionist/freeze?q=${item.customerCode}">
                                                <td>
                                                    <div class="gym-cust-cell">
                                                        <div class="common-user-avatar sm">
                                                            <i class="fa-solid fa-user"></i>
                                                        </div>
                                                        <div class="gym-cust-info">
                                                            <span class="cust-name"><c:out value="${item.customerName}" /></span>
                                                            <div class="cust-meta">
                                                                <span class="cust-code"><c:out value="${item.customerCode}" /></span>
                                                                <span class="cust-dot">·</span>
                                                                <span class="cust-phone"><i class="fa-solid fa-phone"></i> <c:out value="${item.customerPhone}" /></span>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td>
                                                    <span class="pkg-name">
                                                        <i class="fa-solid fa-box-archive text-warning"></i> <c:out value="${item.packageName}" />
                                                    </span>
                                                </td>
                                                <td>
                                                    <div class="gym-dates-cell">
                                                        <div>Từ: <b><c:out value="${gms:formatDate(item.startDate)}" /></b></div>
                                                        <div>Đến: 
                                                            <c:choose>
                                                                <c:when test="${not empty item.actualEndDate}">
                                                                    <b><c:out value="${gms:formatDate(item.actualEndDate)}" /></b> (Thực tế)
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <b><c:out value="${gms:formatDate(item.plannedEndDate)}" /></b> (Dự kiến)
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </div>
                                                    </div>
                                                </td>
                                                <td style="text-align: center;">
                                                    <span class="badge badge-info" style="font-size:12px;">${item.daysFrozen} ngày</span>
                                                </td>
                                                <td style="text-align: center;">
                                                    <span class="badge ${item.statusBadgeClass}"><c:out value="${item.statusLabel}" /></span>
                                                </td>
                                                <td class="gym-reason-cell">
                                                    <div class="reason-content" title="<c:out value='${item.reason}' />">
                                                        <span class="reason-text"><c:out value="${item.reason}" /></span>
                                                        <c:if test="${not empty item.reason and item.reason.length() > 50}">
                                                            <button type="button" class="btn-toggle-reason" title="Bấm để xem toàn bộ lý do">Xem thêm</button>
                                                        </c:if>
                                                    </div>
                                                </td>
                                                <td class="gym-staff-cell">
                                                    <div class="gym-staff-info">
                                                        <i class="fa-solid fa-user-tie"></i>
                                                        <span><c:out value="${item.handledByEmployeeName}" /></span>
                                                    </div>
                                                </td>
                                            </tr>
                                        </c:forEach>
                                    </c:when>
                                    <c:otherwise>
                                        <tr>
                                            <td colspan="7" style="text-align:center; padding:var(--space-6); color:var(--color-text-muted);">
                                                <i class="fa-solid fa-circle-info" style="font-size:24px; display:block; margin-bottom:var(--space-2);"></i>
                                                Chưa có dữ liệu lịch sử bảo lưu nào trong hệ thống.
                                            </td>
                                        </tr>
                                    </c:otherwise>
                                </c:choose>
                            </tbody>
                        </table>
                    </div>
                </div>

            </section>
        </main>
    </div>
</div>

<%-- MODAL MỞ LẠI THẺ TRƯỚC HẠN --%>
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
                <input type="hidden" name="filter" value="<c:out value='${currentFilter}' />">

                <p style="font-size:var(--font-size-md);color:var(--color-text);margin-bottom:var(--space-3);">
                    Xác nhận mở lại thẻ tập cho hội viên <b><c:out value="${freezeCustomer.fullName}" /></b> (Mã: <b><c:out value="${freezeCustomer.customerCode}" /></b>)?
                </p>

                <div class="calc-highlight-box" style="margin-bottom:var(--space-3);">
                    <div class="calc-col">
                        <span class="lbl">Số ngày đã bảo lưu thực tế:</span>
                        <b class="val text-primary">${freezeCustomer.daysFrozenIfCancelledToday} ngày</b>
                    </div>
                    <div class="calc-col">
                        <span class="lbl">Hạn thẻ mới sau khi mở lại:</span>
                        <b class="val text-success"><c:out value="${gms:formatDate(freezeCustomer.projectedNewEndDateIfCancelledToday)}" /></b>
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
