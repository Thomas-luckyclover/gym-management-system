<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
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
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/customer-list.css?v=20261031-fixed-page">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/freeze-membership.css?v=20261101_v31">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
    <script src="${pageContext.request.contextPath}/js/pages/receptionist/freeze-membership.js?v=20261101_v23" defer></script>
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

            <%-- KHỐI 1: CHI TIẾT & THAO TÁC NGHIỆP VỤ (HIỂN THỊ KHI ĐÃ CHỌN HỘI VIÊN) --%>
            <c:if test="${not empty freezeCustomer}">
                <div class="split customer-action-split">

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
                                            <button type="button" class="btn btn-primary" id="btnOpenEarlyModal" style="margin-top:var(--space-3); width:100%;">
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
                                            <label for="startDateDisplay">Ngày bắt đầu: <span class="req">*</span></label>
                                            <div class="freeze-date-control">
                                                <input type="text" id="startDateDisplay" class="form-control freeze-date-input"
                                                       value="<c:out value='${gms:formatDate(freezeCustomer.minStartDate)}' />"
                                                       placeholder="dd/mm/yyyy" autocomplete="off" required>
                                                <button type="button" class="freeze-calendar-button" data-picker-target="startDate" aria-label="Chọn ngày bắt đầu">
                                                    <i class="fa-regular fa-calendar" aria-hidden="true"></i>
                                                </button>
                                                <input type="date" id="startDate" name="startDate" class="freeze-native-date"
                                                       min="${freezeCustomer.minStartDate}"
                                                       max="${freezeCustomer.packageEndDate}"
                                                       value="${freezeCustomer.minStartDate}" required tabindex="-1">
                                            </div>
                                            <small class="hint">Sớm nhất từ ngày mai (<c:out value="${gms:formatDate(freezeCustomer.minStartDate)}" />).</small>
                                        </div>

                                        <div class="form-group">
                                            <label for="plannedEndDateDisplay">Ngày kết thúc dự kiến: <span class="req">*</span></label>
                                            <div class="freeze-date-control">
                                                <input type="text" id="plannedEndDateDisplay" class="form-control freeze-date-input"
                                                       value="<c:out value='${gms:formatDate(freezeCustomer.maxEndDate)}' />"
                                                       placeholder="dd/mm/yyyy" autocomplete="off" required>
                                                <button type="button" class="freeze-calendar-button" data-picker-target="plannedEndDate" aria-label="Chọn ngày kết thúc dự kiến">
                                                    <i class="fa-regular fa-calendar" aria-hidden="true"></i>
                                                </button>
                                                <input type="date" id="plannedEndDate" name="plannedEndDate" class="freeze-native-date"
                                                       min="${freezeCustomer.minStartDate}"
                                                       max="${freezeCustomer.maxEndDate}"
                                                       value="${freezeCustomer.maxEndDate}" required tabindex="-1">
                                            </div>
                                            <small class="hint" id="plannedEndDateHint">Tối đa 1 tháng (đến ngày <c:out value="${gms:formatDate(freezeCustomer.maxEndDate)}" />).</small>
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
                                <div class="freeze-ineligible-card">
                                    <div class="ineligible-card-top">
                                        <div class="ineligible-icon-wrap" aria-hidden="true">
                                            <i class="fa-solid fa-ban"></i>
                                        </div>
                                        <div class="ineligible-top-text">
                                            <div class="ineligible-headline-row">
                                                <h3 class="ineligible-title">Không đủ điều kiện tạo bảo lưu</h3>
                                                <span class="badge badge-danger">
                                                    <i class="fa-solid fa-lock"></i> Không khả dụng
                                                </span>
                                            </div>
                                            <p class="ineligible-subtitle">Hệ thống đã tự động kiểm tra quy chế và thời hạn áp dụng cho gói tập này.</p>
                                        </div>
                                    </div>

                                    <div class="ineligible-reason-box">
                                        <div class="reason-label">
                                            <i class="fa-solid fa-circle-exclamation"></i> Lý do từ chối
                                        </div>
                                        <div class="reason-text">
                                            <c:out value="${freezeCustomer.ineligibilityReason}" />
                                        </div>
                                    </div>

                                    <div class="ineligible-policy-grid">
                                        <div class="ineligible-policy-item">
                                            <span class="policy-lbl">Gói tập hiện tại:</span>
                                            <span class="policy-val"><c:out value="${freezeCustomer.packageName}" default="Chưa có gói tập" /></span>
                                        </div>
                                        <div class="ineligible-policy-item">
                                            <span class="policy-lbl">Lượt đã bảo lưu:</span>
                                            <span class="policy-val highlight-danger">
                                                <c:choose>
                                                    <c:when test="${freezeCustomer.hasMembership}">
                                                        <b>${freezeCustomer.freezeUsed}</b> / ${freezeCustomer.maxFreezesSnapshot} lần
                                                    </c:when>
                                                    <c:otherwise>0 / 0</c:otherwise>
                                                </c:choose>
                                            </span>
                                        </div>
                                        <div class="ineligible-policy-item">
                                            <span class="policy-lbl">Thời hạn gói:</span>
                                            <span class="policy-val">
                                                <c:choose>
                                                    <c:when test="${not empty freezeCustomer.packageEndDate}">
                                                        <c:out value="${gms:formatDate(freezeCustomer.packageEndDate)}" />
                                                    </c:when>
                                                    <c:otherwise>--</c:otherwise>
                                                </c:choose>
                                            </span>
                                        </div>
                                    </div>

                                    <div class="ineligible-guidance-box">
                                        <div class="guidance-icon"><i class="fa-solid fa-circle-info"></i></div>
                                        <div class="guidance-content">
                                            <strong>Lưu ý nghiệp vụ dành cho Lễ tân:</strong>
                                            <p>Gói tập này không thể tạo bảo lưu thêm theo chính sách hiện hành. Đối với các tình huống bất khả kháng (sự cố y tế, tai nạn có giấy xác nhận điều trị từ bệnh viện, công tác đột xuất), vui lòng liên hệ <b>Quản lý cơ sở (Manager)</b> để được hỗ trợ phê duyệt ngoại lệ.</p>
                                        </div>
                                    </div>

                                    <div class="ineligible-actions">
                                        <c:if test="${not empty freezeCustomer.freezeHistory}">
                                            <a href="#custFreezeHistory" class="btn btn-outline btn-sm">
                                                <i class="fa-solid fa-clock-rotate-left"></i> Xem lịch sử bảo lưu (${fn:length(freezeCustomer.freezeHistory)} đợt)
                                            </a>
                                        </c:if>
                                        <a href="${pageContext.request.contextPath}/receptionist/freeze?tab=members" class="btn btn-ghost btn-sm">
                                            <i class="fa-solid fa-arrow-left"></i> Quay lại danh sách hội viên
                                        </a>
                                    </div>
                                </div>
                            </c:if>
                        </section>

                        <%-- LỊCH SỬ BẢO LƯU CỦA RIÊNG HỘI VIÊN NÀY --%>
                        <c:if test="${not empty freezeCustomer.freezeHistory}">
                            <section class="panel freeze-panel" id="custFreezeHistory" style="margin-top:var(--space-4);">
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

            <%-- KHỐI 2: TOÀN BỘ BẢNG DỮ LIỆU GYMMASTER & LỊCH SỬ TOÀN HỆ THỐNG (CHỈ HIỂN THỊ KHI CHƯA CHỌN HỘI VIÊN) --%>
            <c:if test="${empty freezeCustomer}">
            <div class="freeze-tabs-container">
                <c:set var="isHistoryTab" value="${param.tab == 'history' or currentTab == 'history' or not empty param.hq or not empty param.hfilter}" />

                <%-- Thanh chuyển Tabs chuẩn BF-07 (Tách 2 trang độc lập) --%>
                <nav class="bf07-tabs" aria-label="Các tab quản lý bảo lưu">
                    <a href="${pageContext.request.contextPath}/receptionist/freeze?tab=members"
                       class="bf07-tab <c:if test="${not isHistoryTab}">is-active</c:if>"
                       <c:if test="${not isHistoryTab}">aria-current="page"</c:if>>
                        Danh sách khách hàng
                    </a>
                    <a href="${pageContext.request.contextPath}/receptionist/freeze?tab=history"
                       class="bf07-tab <c:if test="${isHistoryTab}">is-active</c:if>"
                       <c:if test="${isHistoryTab}">aria-current="page"</c:if>>
                        Lịch sử bảo lưu
                    </a>
                </nav>

                <c:choose>
                    <%-- TAB 1: DANH SÁCH HỘI VIÊN & GÓI TẬP --%>
                    <c:when test="${not isHistoryTab}">
                        <div class="freeze-tab-content">
                            <%-- Khối bộ lọc tìm kiếm hồ sơ hội viên --%>
                            <form id="freezeSearchForm" action="${pageContext.request.contextPath}/receptionist/freeze" method="GET" class="bf07-filters bf07-panel" role="search" novalidate>
                                <input type="hidden" name="tab" value="members">
                                <div class="bf07-search-field">
                                    <label for="freezeQueryInput">Tìm hội viên</label>
                                    <input id="freezeQueryInput" name="q" type="search" value="<c:out value='${query}' />"
                                           placeholder="Tên, mã hội viên, số điện thoại hoặc email"
                                           autocomplete="off" maxlength="100">
                                </div>
                                <div>
                                    <label for="freezeFilterSelect">Trạng thái hồ sơ</label>
                                    <select id="freezeFilterSelect" name="filter">
                                        <option value="ALL" <c:if test="${empty currentFilter or currentFilter == 'ALL'}">selected</c:if>>Tất cả trạng thái</option>
                                        <option value="FROZEN" <c:if test="${currentFilter == 'FROZEN'}">selected</c:if>>Đang bảo lưu</option>
                                        <option value="ELIGIBLE" <c:if test="${currentFilter == 'ELIGIBLE'}">selected</c:if>>Đủ điều kiện bảo lưu</option>
                                        <option value="ACTIVE" <c:if test="${currentFilter == 'ACTIVE'}">selected</c:if>>Hoạt động</option>
                                    </select>
                                </div>
                                <div class="bf07-filter-actions">
                                    <button class="btn btn-primary" type="submit">Tìm</button>
                                    <c:if test="${not empty query or (not empty currentFilter and currentFilter != 'ALL')}">
                                        <a href="${pageContext.request.contextPath}/receptionist/freeze?tab=members" class="btn btn-outline" title="Xóa tất cả bộ lọc">Xóa lọc</a>
                                    </c:if>
                                </div>
                            </form>

                            <%-- Thông báo không tìm thấy hội viên --%>
                            <c:if test="${not empty searchError}">
                                <div class="alert-banner danger" style="margin-top:var(--space-1); margin-bottom:var(--space-1);">
                                    <i class="fa-solid fa-circle-xmark"></i>
                                    <span><b>Không tìm thấy:</b> <c:out value="${searchError}" /></span>
                                </div>
                            </c:if>

                            <%-- Bảng danh sách hội viên & kết quả --%>
                            <section class="bf07-panel bf07-list-panel" aria-label="Danh sách hội viên và gói tập">
                                <div class="bf07-section-heading">
                                    <h2>Danh sách hội viên</h2>
                                    <span class="bf07-muted">
                                        <c:out value="${membershipsPage.totalItems}" default="0" /> kết quả
                                    </span>
                                </div>

                                <div class="gym-table-wrap">
                        <table class="bf07-table">
                            <thead>
                                <tr>
                                    <th style="width: 22%;">Hội viên</th>
                                    <th style="width: 20%;">Gói hội viên</th>
                                    <th class="th-dates" style="width: 26%;">Thời hạn gói tập</th>
                                    <th style="width: 18%; text-align: center;">Tình trạng bảo lưu</th>
                                    <th style="width: 14%; text-align: center;">Thao tác</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:choose>
                                    <c:when test="${not empty customerMemberships}">
                                        <c:forEach var="row" items="${customerMemberships}">
                                            <tr>
                                                <td>
                                                    <div class="bf07-person">
                                                        <span class="bf07-avatar" aria-hidden="true">
                                                            <c:out value="${fn:toUpperCase(fn:substring(row.fullName, 0, 1))}" />
                                                        </span>
                                                        <div>
                                                            <span class="bf07-name"><c:out value="${row.fullName}" /></span>
                                                            <span class="bf07-sub"><c:out value="${row.customerCode}" /></span>
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
                                                            </c:when>
                                                            <c:otherwise>
                                                                <span class="muted" style="font-size:var(--font-size-xs);">Chưa có gói tập</span>
                                                            </c:otherwise>
                                                        </c:choose>
                                                    </div>
                                                </td>
                                                <td class="gym-dates-col">
                                                    <div class="gym-dates-cell">
                                                        <c:choose>
                                                            <c:when test="${row.hasMembership() and not empty row.startDate}">
                                                                <div>Bắt đầu: <b><c:out value="${gms:formatDate(row.startDate)}" /></b></div>
                                                                <div>Hết hạn: <b><c:out value="${gms:formatDate(row.endDate)}" /></b></div>
                                                                <c:if test="${not empty row.daysRemainingText}">
                                                                    <div class="gym-days-left ${row.daysRemaining > 0 ? (row.daysRemaining <= 15 ? 'warn' : 'ok') : 'expired'}">
                                                                        <c:out value="${row.daysRemainingText}" />
                                                                    </div>
                                                                </c:if>
                                                            </c:when>
                                                            <c:otherwise>
                                                                <span class="muted">--</span>
                                                            </c:otherwise>
                                                        </c:choose>
                                                    </div>
                                                </td>
                                                <td class="gym-status-cell" style="text-align: center;">
                                                    <span class="badge ${row.freezeStatusBadgeClass}">
                                                        <c:out value="${row.freezeStatusLabel}" />
                                                    </span>
                                                </td>
                                                <td class="gym-action-cell" style="text-align: center;">
                                                    <c:choose>
                                                        <c:when test="${row.isCurrentlyFrozen()}">
                                                            <a href="${pageContext.request.contextPath}/receptionist/freeze?tab=members&q=${row.customerCode}"
                                                               class="btn btn-outline btn-sm" title="Mở lại thẻ tập trước hạn">
                                                                Mở lại
                                                            </a>
                                                        </c:when>
                                                        <c:when test="${row.isEligibleToFreeze()}">
                                                            <a href="${pageContext.request.contextPath}/receptionist/freeze?tab=members&q=${row.customerCode}"
                                                               class="btn btn-outline btn-sm" title="Tạo bảo lưu gói tập">
                                                                Bảo lưu
                                                            </a>
                                                        </c:when>
                                                        <c:otherwise>
                                                            <button type="button" class="btn btn-outline btn-sm btn-disabled" disabled
                                                                    title="Không đủ điều kiện bảo lưu">
                                                                Bảo lưu
                                                            </button>
                                                        </c:otherwise>
                                                    </c:choose>
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

                    

                        

                                <%-- Phân trang danh sách hội viên chuẩn phong cách BF-07 --%>
                    <c:if test="${membershipsPage.totalPages > 1}">
                        <div class="freeze-pagination">
                            <div class="freeze-pagination-info">
                                Trang <b>${membershipsPage.pageNumber}</b> / <b>${membershipsPage.totalPages}</b> 
                                <span class="freeze-total-count">(Tổng <b>${membershipsPage.totalItems}</b> hội viên)</span>
                            </div>

                            <div class="freeze-pagination-controls" aria-label="Điều hướng phân trang">
                                <%-- Nút Trang trước --%>
                                <c:choose>
                                    <c:when test="${membershipsPage.hasPrevious}">
                                        <c:url var="prevPageUrl" value="/receptionist/freeze">
                                            <c:param name="tab" value="members" />
                                            <c:param name="page" value="${membershipsPage.pageNumber - 1}" />
                                            <c:if test="${not empty query}"><c:param name="q" value="${query}" /></c:if>
                                            <c:if test="${not empty currentFilter}"><c:param name="filter" value="${currentFilter}" /></c:if>
                                        </c:url>
                                        <a class="btn btn-outline btn-sm freeze-nav-btn" href="${prevPageUrl}" title="Về trang trước">
                                            <i class="fa-solid fa-chevron-left"></i> Trước
                                        </a>
                                    </c:when>
                                    <c:otherwise>
                                        <button class="btn btn-outline btn-sm freeze-nav-btn is-disabled" type="button" disabled aria-disabled="true">
                                            <i class="fa-solid fa-chevron-left"></i> Trước
                                        </button>
                                    </c:otherwise>
                                </c:choose>

                                <%-- Danh sách số trang --%>
                                <div class="freeze-page-numbers">
                                    <c:choose>
                                        <%-- Khi <= 8 trang: hiển thị toàn bộ số trang --%>
                                        <c:when test="${membershipsPage.totalPages <= 8}">
                                            <c:forEach var="p" begin="1" end="${membershipsPage.totalPages}">
                                                <c:choose>
                                                    <c:when test="${p == membershipsPage.pageNumber}">
                                                        <span class="btn btn-sm btn-primary is-active" aria-current="page">${p}</span>
                                                    </c:when>
                                                    <c:otherwise>
                                                        <c:url var="pUrl" value="/receptionist/freeze">
                                                            <c:param name="tab" value="members" />
                                                            <c:param name="page" value="${p}" />
                                                            <c:if test="${not empty query}"><c:param name="q" value="${query}" /></c:if>
                                                            <c:if test="${not empty currentFilter}"><c:param name="filter" value="${currentFilter}" /></c:if>
                                                        </c:url>
                                                        <a class="btn btn-sm btn-ghost page-num-btn" href="${pUrl}">${p}</a>
                                                    </c:otherwise>
                                                </c:choose>
                                            </c:forEach>
                                        </c:when>

                                        <%-- Khi > 8 trang: hiển thị thông minh kèm dấu rút gọn ... --%>
                                        <c:otherwise>
                                            <%-- Trang đầu (1) --%>
                                            <c:choose>
                                                <c:when test="${membershipsPage.pageNumber == 1}">
                                                    <span class="btn btn-sm btn-primary is-active" aria-current="page">1</span>
                                                </c:when>
                                                <c:otherwise>
                                                    <c:url var="p1Url" value="/receptionist/freeze">
                                                        <c:param name="tab" value="members" />
                                                        <c:param name="page" value="1" />
                                                        <c:if test="${not empty query}"><c:param name="q" value="${query}" /></c:if>
                                                        <c:if test="${not empty currentFilter}"><c:param name="filter" value="${currentFilter}" /></c:if>
                                                    </c:url>
                                                    <a class="btn btn-sm btn-ghost page-num-btn" href="${p1Url}">1</a>
                                                </c:otherwise>
                                            </c:choose>

                                            <%-- Dấu ... trước --%>
                                            <c:if test="${membershipsPage.pageNumber > 3}">
                                                <span class="page-ellipsis">…</span>
                                            </c:if>

                                            <%-- Các trang lân cận --%>
                                            <c:forEach var="p" begin="${membershipsPage.pageNumber - 1}" end="${membershipsPage.pageNumber + 1}">
                                                <c:if test="${p > 1 and p < membershipsPage.totalPages}">
                                                    <c:choose>
                                                        <c:when test="${p == membershipsPage.pageNumber}">
                                                            <span class="btn btn-sm btn-primary is-active" aria-current="page">${p}</span>
                                                        </c:when>
                                                        <c:otherwise>
                                                            <c:url var="pUrl" value="/receptionist/freeze">
                                                                <c:param name="tab" value="members" />
                                                                <c:param name="page" value="${p}" />
                                                                <c:if test="${not empty query}"><c:param name="q" value="${query}" /></c:if>
                                                                <c:if test="${not empty currentFilter}"><c:param name="filter" value="${currentFilter}" /></c:if>
                                                            </c:url>
                                                            <a class="btn btn-sm btn-ghost page-num-btn" href="${pUrl}">${p}</a>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </c:if>
                                            </c:forEach>

                                            <%-- Dấu ... sau --%>
                                            <c:if test="${membershipsPage.pageNumber < membershipsPage.totalPages - 2}">
                                                <span class="page-ellipsis">…</span>
                                            </c:if>

                                            <%-- Trang cuối --%>
                                            <c:choose>
                                                <c:when test="${membershipsPage.pageNumber == membershipsPage.totalPages}">
                                                    <span class="btn btn-sm btn-primary is-active" aria-current="page">${membershipsPage.totalPages}</span>
                                                </c:when>
                                                <c:otherwise>
                                                    <c:url var="pLastUrl" value="/receptionist/freeze">
                                                        <c:param name="tab" value="members" />
                                                        <c:param name="page" value="${membershipsPage.totalPages}" />
                                                        <c:if test="${not empty query}"><c:param name="q" value="${query}" /></c:if>
                                                        <c:if test="${not empty currentFilter}"><c:param name="filter" value="${currentFilter}" /></c:if>
                                                    </c:url>
                                                    <a class="btn btn-sm btn-ghost page-num-btn" href="${pLastUrl}">${membershipsPage.totalPages}</a>
                                                </c:otherwise>
                                            </c:choose>
                                        </c:otherwise>
                                    </c:choose>
                                </div>

                                <%-- Nút Trang sau --%>
                                <c:choose>
                                    <c:when test="${membershipsPage.hasNext}">
                                        <c:url var="nextPageUrl" value="/receptionist/freeze">
                                            <c:param name="tab" value="members" />
                                            <c:param name="page" value="${membershipsPage.pageNumber + 1}" />
                                            <c:if test="${not empty query}"><c:param name="q" value="${query}" /></c:if>
                                            <c:if test="${not empty currentFilter}"><c:param name="filter" value="${currentFilter}" /></c:if>
                                        </c:url>
                                        <a class="btn btn-outline btn-sm freeze-nav-btn" href="${nextPageUrl}" title="Sang trang sau">
                                            Sau <i class="fa-solid fa-chevron-right"></i>
                                        </a>
                                    </c:when>
                                    <c:otherwise>
                                        <button class="btn btn-outline btn-sm freeze-nav-btn is-disabled" type="button" disabled aria-disabled="true">
                                            Sau <i class="fa-solid fa-chevron-right"></i>
                                        </button>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </div>
                    </c:if>
                            </section>
                        </div>
                    </c:when>

                    <%-- TAB 2: LỊCH SỬ BẢO LƯU --%>
                    <c:otherwise>
                        <div class="freeze-tab-content">
                            <%-- Khối bộ lọc tìm kiếm lịch sử --%>
                            <form id="historySearchForm" action="${pageContext.request.contextPath}/receptionist/freeze" method="GET" class="bf07-filters bf07-panel" role="search" novalidate>
                                <input type="hidden" name="tab" value="history">
                                <div class="bf07-search-field">
                                    <label for="historyQueryInput">Tìm hội viên</label>
                                    <input id="historyQueryInput" name="hq" type="search" value="<c:out value='${historyQuery}' />"
                                           placeholder="Tên, mã hội viên, số điện thoại hoặc email"
                                           autocomplete="off" maxlength="100">
                                </div>
                                <div>
                                    <label for="historyFilterSelect">Trạng thái bảo lưu</label>
                                    <select id="historyFilterSelect" name="hfilter">
                                        <option value="ALL" <c:if test="${empty historyFilter or historyFilter == 'ALL'}">selected</c:if>>Tất cả trạng thái</option>
                                        <option value="FROZEN" <c:if test="${historyFilter == 'FROZEN'}">selected</c:if>>Đang bảo lưu</option>
                                        <option value="COMPLETED" <c:if test="${historyFilter == 'COMPLETED'}">selected</c:if>>Đã hoàn thành</option>
                                        <option value="CANCELLED" <c:if test="${historyFilter == 'CANCELLED'}">selected</c:if>>Hủy sớm</option>
                                    </select>
                                </div>
                                <div class="bf07-filter-actions">
                                    <button class="btn btn-primary" type="submit">Tìm</button>
                                    <c:if test="${not empty historyQuery or (not empty historyFilter and historyFilter != 'ALL')}">
                                        <a href="${pageContext.request.contextPath}/receptionist/freeze?tab=history" class="btn btn-outline" title="Xóa tìm kiếm lịch sử">Xóa lọc</a>
                                    </c:if>
                                </div>
                            </form>

                            <%-- Bảng danh sách lịch sử bảo lưu --%>
                            <section class="bf07-panel bf07-list-panel" aria-label="Nhật ký lịch sử bảo lưu">
                                <div class="bf07-section-heading">
                                    <h2>Lịch sử bảo lưu</h2>
                                    <span class="bf07-muted">
                                        <c:out value="${freezeHistoryList.size()}" default="0" /> kết quả
                                    </span>
                                </div>

                                <div class="gym-table-wrap">
                        <table class="bf07-table">
                            <thead>
                                <tr>
                                    <th style="width: 20%;">Hội viên</th>
                                    <th style="width: 12%;">Gói tập</th>
                                    <th style="width: 15%;">Thời gian bảo lưu</th>
                                    <th style="width: 9%; text-align: center;">Số ngày</th>
                                    <th style="width: 13%; text-align: center;">Trạng thái</th>
                                    <th style="width: 12%; text-align: center;">Lý do bảo lưu</th>
                                    <th style="width: 19%;">Lễ tân tiếp nhận</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:choose>
                                    <c:when test="${not empty freezeHistoryList}">
                                        <c:forEach var="item" items="${freezeHistoryList}">
                                            <tr>
                                                <td>
                                                    <div class="bf07-person">
                                                        <span class="bf07-avatar" aria-hidden="true">
                                                            <c:out value="${fn:toUpperCase(fn:substring(item.customerName, 0, 1))}" />
                                                        </span>
                                                        <div>
                                                            <a class="bf07-name" href="${pageContext.request.contextPath}/receptionist/freeze?tab=members&q=${item.customerCode}"><c:out value="${item.customerName}" /></a>
                                                            <span class="bf07-sub"><c:out value="${item.customerCode}" /></span>
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
                                                <td class="gym-days-cell" style="text-align: center;">
                                                    <b>${item.daysFrozen} ngày</b>
                                                </td>
                                                <td class="gym-status-cell" style="text-align: center;">
                                                    <span class="badge ${item.statusBadgeClass}"><c:out value="${item.statusLabel}" /></span>
                                                </td>
                                                <td class="gym-reason-cell" style="text-align: center;">
                                                    <button type="button" class="btn btn-outline btn-sm btn-view-reason" title="Xem chi tiết lý do bảo lưu"
                                                            data-reason="<c:out value='${item.reason}' />">
                                                        <i class="fa-solid fa-eye"></i> Xem
                                                    </button>
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
                                                <c:choose>
                                                    <c:when test="${not empty historyQuery or (not empty historyFilter and historyFilter != 'ALL')}">
                                                        Không tìm thấy lịch sử bảo lưu phù hợp với điều kiện tìm kiếm/lọc đã chọn.
                                                    </c:when>
                                                    <c:otherwise>
                                                        Chưa có dữ liệu lịch sử bảo lưu nào trong hệ thống.
                                                    </c:otherwise>
                                                </c:choose>
                                            </td>
                                        </tr>
                                    </c:otherwise>
                                </c:choose>
                            </tbody>
                        </table>
                    </div>
                            </section>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
            </c:if>
        </main>
    </div>
</div>

<%-- MODAL MỞ LẠI THẺ TRƯỚC HẠN --%>
<c:if test="${not empty freezeCustomer and freezeCustomer.currentlyFrozen and freezeCustomer.canCancelEarly}">
    <div class="modal-overlay" id="modalEarlyCancel" role="dialog" aria-modal="true" aria-labelledby="modalEarlyTitle">
        <div class="modal-box modal-unfreeze-box">
            <%-- Header Modal --%>
            <div class="modal-head modal-unfreeze-head">
                <h3 id="modalEarlyTitle"><i class="fa-solid fa-lock-open text-primary"></i> Mở lại thẻ trước hạn</h3>
                <button class="btn-close-circle" type="button" data-close-modal aria-label="Đóng">
                    <i class="fa-solid fa-xmark"></i>
                </button>
            </div>

            <form method="POST" action="${pageContext.request.contextPath}/receptionist/freeze">
                <input type="hidden" name="action" value="cancelFreezeEarly">
                <input type="hidden" name="freezeRecordId" value="${freezeCustomer.openFreezeRecordId}">
                <input type="hidden" name="q" value="<c:out value='${query}' />">
                <input type="hidden" name="filter" value="<c:out value='${currentFilter}' />">

                <div class="modal-unfreeze-body">
                    <%-- Card định danh hội viên --%>
                    <div class="unfreeze-member-card">
                        <div class="unfreeze-avatar" aria-hidden="true">
                            <c:out value="${fn:toUpperCase(fn:substring(freezeCustomer.fullName, 0, 1))}" />
                        </div>
                        <div class="unfreeze-member-details">
                            <div class="unfreeze-name-row">
                                <h4 class="unfreeze-name"><c:out value="${freezeCustomer.fullName}" /></h4>
                                <span class="unfreeze-code"><c:out value="${freezeCustomer.customerCode}" /></span>
                            </div>
                            <div class="unfreeze-pkg-row">
                                <span class="unfreeze-pkg-tag"><i class="fa-solid fa-box-archive"></i> <c:out value="${freezeCustomer.packageName}" default="Gói tập" /></span>
                                <span class="badge badge-warning unfreeze-status-badge"><i class="fa-solid fa-snowflake"></i> Đang bảo lưu</span>
                            </div>
                        </div>
                    </div>

                    <p class="unfreeze-prompt-text">
                        Bạn có chắc chắn muốn kết thúc đợt bảo lưu này sớm và mở lại quyền tập luyện cho hội viên?
                    </p>

                    <%-- Khối số liệu tính toán bảo lưu --%>
                    <div class="unfreeze-stats-grid">
                        <div class="unfreeze-stat-card">
                            <div class="unfreeze-stat-header">
                                <i class="fa-solid fa-clock-rotate-left"></i>
                                <span>Đã bảo lưu thực tế</span>
                            </div>
                            <div class="unfreeze-stat-val val-days">
                                <b class="text-primary">${freezeCustomer.daysFrozenIfCancelledToday}</b> <small>ngày</small>
                            </div>
                            <div class="unfreeze-stat-sub">Tính từ ngày bắt đầu đến nay</div>
                        </div>

                        <div class="unfreeze-stat-card">
                            <div class="unfreeze-stat-header">
                                <i class="fa-solid fa-calendar-check text-success"></i>
                                <span>Hạn thẻ mới sau khi mở</span>
                            </div>
                            <div class="unfreeze-stat-val val-date text-success">
                                <b><c:out value="${gms:formatDate(freezeCustomer.projectedNewEndDateIfCancelledToday)}" /></b>
                            </div>
                            <div class="unfreeze-stat-sub">Đã gia hạn bù số ngày bảo lưu</div>
                        </div>
                    </div>

                    <%-- Cảnh báo nghiệp vụ --%>
                    <div class="unfreeze-notice">
                        <i class="fa-solid fa-triangle-exclamation"></i>
                        <div class="unfreeze-notice-content">
                            <strong>Lưu ý:</strong> Lượt bảo lưu của gói đã được tính. Các buổi PT đã ghi nhận vắng trước đó sẽ không tự động phục hồi.
                        </div>
                    </div>
                </div>

                <%-- Footer hành động --%>
                <div class="modal-unfreeze-footer">
                    <button class="btn btn-outline" type="button" data-close-modal>Bỏ qua</button>
                    <button class="btn btn-primary" type="submit">
                        <i class="fa-solid fa-lock-open"></i> Xác nhận mở lại
                    </button>
                </div>
            </form>
        </div>
    </div>
</c:if>

<%-- MODAL XEM CHI TIẾT LÝ DO BẢO LƯU --%>
<div class="modal-overlay" id="modalViewReason" role="dialog" aria-modal="true" aria-labelledby="modalReasonTitle">
    <div class="modal-box modal-reason-box">
        <%-- Header Modal --%>
        <div class="modal-head modal-reason-head">
            <h3 id="modalReasonTitle"><i class="fa-solid fa-file-lines text-primary"></i> Lý do bảo lưu</h3>
            <button class="btn-close-circle" type="button" data-close-reason-modal aria-label="Đóng">
                <i class="fa-solid fa-xmark"></i>
            </button>
        </div>

        <%-- Thân Modal: Chỉ hiển thị nội dung lý do --%>
        <div class="modal-reason-body">
            <div class="reason-full-text-wrap">
                <p id="reasonModalText">--</p>
            </div>
        </div>

        <%-- Chân Modal --%>
        <div class="modal-reason-footer">
            <button class="btn btn-primary" type="button" data-close-reason-modal>
                Đóng
            </button>
        </div>
    </div>
</div>
</body>
</html>
