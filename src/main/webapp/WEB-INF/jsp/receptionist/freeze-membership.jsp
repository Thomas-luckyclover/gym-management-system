<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  freeze-membership.jsp — Giao diện Bảo lưu thẻ hội viên của Lễ tân (Màn #34, UC-18, Workflow BF-09).
  Triển khai đầy đủ các nghiệp vụ:
  - Tra cứu thông tin hội viên & gói tập Membership hiện tại.
  - Thiết lập kỳ bảo lưu mới (kiểm tra hạn gói, hạn mức bảo lưu, thời lượng tối đa P-02).
  - Tự động cảnh báo các buổi PT bị chuyển vắng (CBR-62) và giải phóng lịch PT (DBR-36).
  - Hỗ trợ mở lại thẻ tập trước hạn (hủy bảo lưu sớm tại quầy) kèm gia hạn số ngày thực tế (BR-26, DBR-51).
  - Lịch sử bảo lưu của hội viên và danh sách bảo lưu gần đây trong hệ thống.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Bảo lưu thẻ hội viên · Lễ tân</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/freeze-membership.css?v=20261026_v2">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
    <script src="${pageContext.request.contextPath}/js/pages/receptionist/freeze-membership.js?v=20261026_v2" defer></script>
    <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
</head>

<body class="dock theme-staff">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
        <%-- Header topbar --%>
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

        <%-- Nội dung chính --%>
        <main class="content" id="main-content" tabindex="-1">
            <div class="freeze-container">

                <%-- KHỐI 1: TÌM KIẾM HỘI VIÊN --%>
                <section class="panel freeze-search-panel" data-reveal>
                    <div class="panel-head">
                        <div>
                            <h2>Tra cứu hội viên</h2>
                            <p>Nhập mã hội viên (KH-xxxx), số điện thoại hoặc họ tên để thiết lập hoặc kiểm tra bảo lưu.</p>
                        </div>
                    </div>

                    <form id="freezeSearchForm" action="${pageContext.request.contextPath}/receptionist/freeze" method="GET" class="toolbar" novalidate>
                        <div class="search" style="flex:1;">
                            <i class="fa-solid fa-magnifying-glass"></i>
                            <input id="freezeQueryInput" name="q" type="search" value="<c:out value='${query}' />"
                                   placeholder="Nhập mã hội viên (KH-...), họ tên hoặc số điện thoại..."
                                   autocomplete="off" aria-label="Mã khách, số điện thoại hoặc tên" required autofocus>
                        </div>
                        <button class="btn btn-primary" type="submit">
                            <i class="fa-solid fa-magnifying-glass"></i> Tìm kiếm
                        </button>
                        <c:if test="${not empty query}">
                            <a href="${pageContext.request.contextPath}/receptionist/freeze" class="btn btn-ghost" title="Xóa tìm kiếm">Xóa lọc</a>
                        </c:if>
                    </form>
                </section>

                <%-- Thông báo không tìm thấy --%>
                <c:if test="${not empty searchError}">
                    <div class="alert-banner danger" style="margin-top:var(--space-3);">
                        <i class="fa-solid fa-circle-xmark"></i>
                        <span><c:out value="${searchError}" /></span>
                    </div>
                </c:if>

                <%-- KHỐI 2: KẾT QUẢ TÌM THEO TÊN (nhiều kết quả) --%>
                <c:if test="${not empty searchResults}">
                    <section class="panel" style="margin-top:var(--space-4);" data-reveal>
                        <div class="panel-head">
                            <div>
                                <h2>Kết quả tìm kiếm</h2>
                                <p>Tìm thấy <b><c:out value="${searchResultsPage.totalItems}" /></b> hội viên phù hợp. Chọn hội viên cần xử lý.</p>
                            </div>
                        </div>

                        <div class="table-responsive">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Mã hội viên</th>
                                        <th>Họ và tên</th>
                                        <th>Số điện thoại</th>
                                        <th>Trạng thái</th>
                                        <th class="text-right">Hành động</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <c:forEach var="cust" items="${searchResults}">
                                        <tr>
                                            <td><b><c:out value="${cust.customerCode}" /></b></td>
                                            <td><c:out value="${cust.fullName}" /></td>
                                            <td><c:out value="${cust.phone}" /></td>
                                            <td>
                                                <span class="badge ${cust.customerStatus == 'Active' ? 'badge-ok' : (cust.customerStatus == 'Frozen' ? 'badge-warn' : 'badge-ghost')}">
                                                    <c:out value="${cust.customerStatus == 'Active' ? 'Đang hoạt động' : (cust.customerStatus == 'Frozen' ? 'Đang bảo lưu' : cust.customerStatus)}" />
                                                </span>
                                            </td>
                                            <td class="text-right">
                                                <a href="${pageContext.request.contextPath}/receptionist/freeze?q=${cust.customerCode}" class="btn btn-sm btn-primary">
                                                    Chọn hồ sơ
                                                </a>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </div>

                        <%-- Phân trang --%>
                        <c:if test="${searchResultsPage.totalPages > 1}">
                            <div class="pagination">
                                <c:forEach var="p" begin="1" end="${searchResultsPage.totalPages}">
                                    <a class="btn btn-sm ${p == searchResultsPage.pageNumber ? 'btn-primary' : 'btn-ghost'}"
                                       href="${pageContext.request.contextPath}/receptionist/freeze?q=${query}&page=${p}">
                                        ${p}
                                    </a>
                                </c:forEach>
                            </div>
                        </c:if>
                    </section>
                </c:if>

                <%-- KHỐI 3: CHI TIẾT HỒ SƠ & THỰC HIỆN BẢO LƯU --%>
                <c:if test="${not empty freezeCustomer}">
                    <div class="freeze-workspace">

                        <%-- Cột trái: Hồ sơ hội viên & Gói tập hiện tại --%>
                        <div class="freeze-card profile-card">
                            <div class="card-header">
                                <span class="card-tag">Hồ sơ hội viên</span>
                                <span class="badge ${freezeCustomer.customerStatus == 'Active' ? 'badge-ok' : (freezeCustomer.customerStatus == 'Frozen' ? 'badge-warn' : 'badge-ghost')}">
                                    <c:out value="${freezeCustomer.customerStatus == 'Active' ? 'Đang hoạt động' : (freezeCustomer.customerStatus == 'Frozen' ? 'Đang bảo lưu' : freezeCustomer.customerStatus)}" />
                                </span>
                            </div>

                            <div class="profile-info">
                                <div class="profile-avatar">
                                    <c:choose>
                                        <c:when test="${not empty freezeCustomer.avatar}">
                                            <img src="${pageContext.request.contextPath}${freezeCustomer.avatar}" alt="Avatar">
                                        </c:when>
                                        <c:when test="${not empty freezeCustomer.checkInPhoto}">
                                            <img src="${pageContext.request.contextPath}${freezeCustomer.checkInPhoto}" alt="Ảnh hội viên">
                                        </c:when>
                                        <c:otherwise>
                                            <div class="avatar-placeholder"><i class="fa-solid fa-user"></i></div>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                                <div class="profile-meta">
                                    <h3 class="profile-name"><c:out value="${freezeCustomer.fullName}" /></h3>
                                    <div class="meta-row">
                                        <span class="meta-label">Mã hội viên:</span>
                                        <span class="meta-value"><b><c:out value="${freezeCustomer.customerCode}" /></b></span>
                                    </div>
                                    <div class="meta-row">
                                        <span class="meta-label">Số điện thoại:</span>
                                        <span class="meta-value"><c:out value="${freezeCustomer.phone}" /></span>
                                    </div>
                                </div>
                            </div>

                            <hr class="card-divider">

                            <%-- Gói tập Membership hiện tại --%>
                            <div class="package-section">
                                <h4 class="section-title"><i class="fa-solid fa-id-card"></i> Gói thẻ hội viên</h4>
                                <c:choose>
                                    <c:when test="${freezeCustomer.hasMembership}">
                                        <div class="pkg-card">
                                            <div class="pkg-top">
                                                <span class="pkg-title"><c:out value="${freezeCustomer.packageName}" /></span>
                                                <span class="badge ${freezeCustomer.subscriptionStatus == 'Active' ? 'badge-ok' : (freezeCustomer.subscriptionStatus == 'Frozen' ? 'badge-warn' : 'badge-ghost')}">
                                                    <c:out value="${freezeCustomer.subscriptionStatus == 'Active' ? 'Hoạt động' : (freezeCustomer.subscriptionStatus == 'Frozen' ? 'Bảo lưu' : freezeCustomer.subscriptionStatus)}" />
                                                </span>
                                            </div>
                                            <div class="pkg-details">
                                                <div class="detail-item">
                                                    <span class="lbl">Bắt đầu:</span>
                                                    <span class="val"><c:out value="${gms:formatDate(freezeCustomer.packageStartDate)}" /></span>
                                                </div>
                                                <div class="detail-item">
                                                    <span class="lbl">Hết hạn:</span>
                                                    <span class="val"><b><c:out value="${gms:formatDate(freezeCustomer.packageEndDate)}" /></b></span>
                                                </div>
                                                <div class="detail-item">
                                                    <span class="lbl">Lượt bảo lưu:</span>
                                                    <span class="val">
                                                        <c:out value="${freezeCustomer.freezeUsed}" /> / <c:out value="${freezeCustomer.maxFreezesSnapshot}" /> lần
                                                        <c:if test="${freezeCustomer.freezeUsed >= freezeCustomer.maxFreezesSnapshot}">
                                                            <small class="text-danger">(Đã hết)</small>
                                                        </c:if>
                                                    </span>
                                                </div>
                                                <div class="detail-item">
                                                    <span class="lbl">Mã thẻ:</span>
                                                    <span class="val">#${freezeCustomer.subscriptionId}</span>
                                                </div>
                                            </div>
                                        </div>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="alert-banner warning">
                                            <i class="fa-solid fa-triangle-exclamation"></i>
                                            <span>Hội viên hiện không có gói thẻ Membership nào đang hoạt động.</span>
                                        </div>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </div>

                        <%-- Cột phải: Thao tác Nghiệp vụ --%>
                        <div class="freeze-card action-card">

                            <%-- TÌNH HUỐNG 1: THẺ ĐANG TRONG KỲ BẢO LƯU --%>
                            <c:if test="${freezeCustomer.currentlyFrozen}">
                                <div class="card-header">
                                    <span class="card-tag">Trạng thái hiện tại</span>
                                    <span class="badge badge-warn"><i class="fa-solid fa-snowflake"></i> Đang bảo lưu</span>
                                </div>

                                <div class="active-freeze-box">
                                    <div class="freeze-icon"><i class="fa-solid fa-snowflake"></i></div>
                                    <div class="freeze-info">
                                        <h3>Thẻ hội viên đang được bảo lưu</h3>
                                        <p class="freeze-time">
                                            Thời gian: Từ ngày <b><c:out value="${gms:formatDate(freezeCustomer.currentFreezeStartDate)}" /></b> đến ngày <b><c:out value="${gms:formatDate(freezeCustomer.currentFreezePlannedEndDate)}" /></b>.
                                        </p>
                                        <div class="freeze-details-list">
                                            <div><span>Lễ tân tiếp nhận:</span> <b><c:out value="${freezeCustomer.currentFreezeHandledBy}" /></b></div>
                                            <div><span>Thời điểm lập:</span> <b><c:out value="${gms:formatDateTime(freezeCustomer.currentFreezeCreatedAt)}" /></b></div>
                                            <div><span>Lý do:</span> <i><c:out value="${freezeCustomer.currentFreezeReason}" /></i></div>
                                        </div>
                                    </div>
                                </div>

                                <%-- Mở lại thẻ tập trước hạn --%>
                                <div class="early-cancel-box">
                                    <h4 class="early-title"><i class="fa-solid fa-clock-rotate-left"></i> Mở lại thẻ tập trước hạn</h4>
                                    <p class="early-desc">
                                        Khi mở lại thẻ trước hạn, hội viên có thể tiếp tục tập luyện ngay hôm nay. Hạn thẻ sẽ được cộng thêm đúng số ngày thực tế đã bảo lưu tính đến hôm nay.
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
                                    <div class="alert-banner info" style="margin-top:var(--space-3);">
                                        <i class="fa-solid fa-circle-info"></i>
                                        <span>Lưu ý: Lượt bảo lưu của gói đã được tính. Các buổi PT đã chuyển sang trạng thái Vắng trước đó sẽ không tự động hoàn lại.</span>
                                    </div>

                                    <c:choose>
                                        <c:when test="${freezeCustomer.canCancelEarly}">
                                            <button type="button" class="btn btn-warning" id="btnOpenCancelFreezeModal" style="margin-top:var(--space-3); width:100%;">
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
                                <div class="card-header">
                                    <span class="card-tag">Thiết lập bảo lưu</span>
                                    <span class="badge badge-ok"><i class="fa-solid fa-check"></i> Đủ điều kiện</span>
                                </div>

                                <p class="form-desc">
                                    Chọn khoảng thời gian bảo lưu theo nhu cầu của hội viên (tối đa <b>${freezeCustomer.maxFreezeMonths} tháng</b>) và kết thúc trước ngày hết hạn thẻ (<b><c:out value="${gms:formatDate(freezeCustomer.packageEndDate)}" /></b>).
                                </p>

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
                                            <small class="hint">Tối đa đến ngày <c:out value="${gms:formatDate(freezeCustomer.maxEndDate)}" />.</small>
                                        </div>
                                    </div>

                                    <%-- Tóm tắt tính toán tự động --%>
                                    <div class="calc-callout">
                                        <div class="callout-line">
                                            <span>Số ngày bảo lưu dự kiến:</span>
                                            <b id="displayDaysCount" class="text-primary">-- ngày</b>
                                        </div>
                                        <div class="callout-line">
                                            <span>Hạn thẻ mới sau khi mở lại:</span>
                                            <b id="displayNewExpiryDate" class="text-success">--/--/----</b>
                                        </div>
                                    </div>

                                    <div class="form-group" style="margin-top:var(--space-3);">
                                        <label for="freezeReason">Lý do bảo lưu: <span class="req">*</span></label>
                                        <textarea id="freezeReason" name="reason" class="form-control" rows="3"
                                                  placeholder="Nhập lý do bảo lưu (ví dụ: công tác nước ngoài, chấn thương, việc gia đình... tối thiểu 5 ký tự)"
                                                  minlength="5" maxlength="500" required></textarea>
                                        <small class="hint">Tối thiểu 5 ký tự.</small>
                                    </div>

                                    <%-- Cảnh báo buổi tập PT trong kỳ bảo lưu --%>
                                    <div class="pt-warning-box">
                                        <div class="pt-warning-header">
                                            <h4><i class="fa-solid fa-calendar-xmark text-danger"></i> Buổi tập cá nhân (PT) trong kỳ bảo lưu (<span id="impactCount">0</span> buổi)</h4>
                                        </div>
                                        <p class="pt-warning-text">
                                            Lưu ý: Các buổi tập PT đã lên lịch trong thời gian bảo lưu sẽ tự động ghi nhận vắng (không hoàn buổi). Huấn luyện viên sẽ được giải phóng lịch để tiếp nhận học viên khác.
                                        </p>

                                        <div id="impactSessionsContainer">
                                            <c:choose>
                                                <c:when test="${not empty freezeCustomer.affectedSessions}">
                                                    <div class="table-responsive">
                                                        <table class="data-table pt-table">
                                                            <thead>
                                                                <tr>
                                                                    <th>Ngày tập</th>
                                                                    <th>Khung giờ</th>
                                                                    <th>Huấn luyện viên</th>
                                                                    <th>Loại gói</th>
                                                                    <th>Tác động</th>
                                                                </tr>
                                                            </thead>
                                                            <tbody>
                                                                <c:forEach var="sessionItem" items="${freezeCustomer.affectedSessions}">
                                                                    <tr>
                                                                        <td><b><c:out value="${gms:formatDate(sessionItem.slotDate)}" /></b></td>
                                                                        <td><c:out value="${gms:formatTime(sessionItem.startTime)}" /> - <c:out value="${gms:formatTime(sessionItem.endTime)}" /></td>
                                                                        <td><c:out value="${sessionItem.ptName}" /></td>
                                                                        <td><c:out value="${sessionItem.packageType}" /></td>
                                                                        <td>
                                                                            <c:choose>
                                                                                <c:when test="${sessionItem.willBeMissed}">
                                                                                    <span class="badge badge-danger">Vắng (Mất buổi)</span>
                                                                                </c:when>
                                                                                <c:otherwise>
                                                                                    <span class="badge badge-ok">Giữ lịch (Bạn tập có thẻ)</span>
                                                                                </c:otherwise>
                                                                            </c:choose>
                                                                        </td>
                                                                    </tr>
                                                                </c:forEach>
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                </c:when>
                                                <c:otherwise>
                                                    <div class="empty-pt-note" id="noSessionsNotice">
                                                        <i class="fa-solid fa-circle-check"></i> Không có buổi tập PT nào trong khoảng thời gian này.
                                                    </div>
                                                </c:otherwise>
                                            </c:choose>
                                        </div>
                                    </div>

                                    <div class="form-actions" style="margin-top:var(--space-4);">
                                        <button type="submit" class="btn btn-primary" id="btnSubmitFreeze" style="width:100%;">
                                            <i class="fa-solid fa-snowflake"></i> Xác nhận bảo lưu thẻ tập
                                        </button>
                                    </div>
                                </form>
                            </c:if>

                            <%-- TÌNH HUỐNG 3: KHÔNG ĐỦ ĐIỀU KIỆN --%>
                            <c:if test="${not freezeCustomer.currentlyFrozen and not freezeCustomer.eligibleToFreeze}">
                                <div class="card-header">
                                    <span class="card-tag">Không thể bảo lưu</span>
                                    <span class="badge badge-danger"><i class="fa-solid fa-circle-xmark"></i> Không đủ điều kiện</span>
                                </div>

                                <div class="alert-banner danger" style="margin-top:var(--space-4);">
                                    <i class="fa-solid fa-circle-xmark"></i>
                                    <div>
                                        <h4>Không thể tạo bảo lưu cho gói này</h4>
                                        <p><c:out value="${freezeCustomer.ineligibilityReason}" /></p>
                                    </div>
                                </div>
                            </c:if>

                        </div>
                    </div>

                    <%-- KHỐI 4: LỊCH SỬ BẢO LƯU CỦA HỘI VIÊN NÀY --%>
                    <c:if test="${not empty freezeCustomer.freezeHistory}">
                        <section class="panel" style="margin-top:var(--space-4);" data-reveal>
                            <div class="panel-head">
                                <div>
                                    <h3>Lịch sử bảo lưu của <c:out value="${freezeCustomer.fullName}" /></h3>
                                    <p>Toàn bộ các đợt bảo lưu đã từng thực hiện trên hồ sơ hội viên này.</p>
                                </div>
                            </div>

                            <div class="table-responsive">
                                <table class="data-table">
                                    <thead>
                                        <tr>
                                            <th>Mã phiếu</th>
                                            <th>Gói thẻ</th>
                                            <th>Ngày bắt đầu</th>
                                            <th>Dự kiến hết</th>
                                            <th>Thực tế hết</th>
                                            <th>Số ngày</th>
                                            <th>Lý do</th>
                                            <th>Lễ tân lập</th>
                                            <th>Trạng thái</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <c:forEach var="item" items="${freezeCustomer.freezeHistory}">
                                            <tr>
                                                <td>#${item.freezeRecordId}</td>
                                                <td><b><c:out value="${item.packageName}" /></b></td>
                                                <td><c:out value="${gms:formatDate(item.startDate)}" /></td>
                                                <td><c:out value="${gms:formatDate(item.plannedEndDate)}" /></td>
                                                <td>
                                                    <c:choose>
                                                        <c:when test="${not empty item.actualEndDate}">
                                                            <c:out value="${gms:formatDate(item.actualEndDate)}" />
                                                        </c:when>
                                                        <c:otherwise>
                                                            <span class="badge badge-warn">Đang bảo lưu</span>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </td>
                                                <td>${item.daysFrozen} ngày</td>
                                                <td><c:out value="${item.reason}" /></td>
                                                <td><c:out value="${item.handledByEmployeeName}" /></td>
                                                <td>
                                                    <span class="badge ${item.statusBadgeClass}"><c:out value="${item.statusLabel}" /></span>
                                                </td>
                                            </tr>
                                        </c:forEach>
                                    </tbody>
                                </table>
                            </div>
                        </section>
                    </c:if>
                </c:if>

                <%-- KHỐI 5: CÁC ĐỢT BẢO LƯU GẦN ĐÂY CỦA PHÒNG TẬP --%>
                <section class="panel" style="margin-top:var(--space-5);" data-reveal>
                    <div class="panel-head">
                        <div>
                            <h2>Các đợt bảo lưu gần đây của phòng tập</h2>
                            <p>Theo dõi các đợt bảo lưu đang mở và lịch sử tiếp nhận bảo lưu gần nhất tại quầy.</p>
                        </div>
                    </div>

                    <c:choose>
                        <c:when test="${empty recentFreezes}">
                            <div class="empty-state">
                                <i class="fa-solid fa-snowflake empty-icon"></i>
                                <p>Chưa có đợt bảo lưu nào được ghi nhận gần đây.</p>
                            </div>
                        </c:when>
                        <c:otherwise>
                            <div class="table-responsive">
                                <table class="data-table">
                                    <thead>
                                        <tr>
                                            <th>Mã phiếu</th>
                                            <th>Hội viên</th>
                                            <th>Số điện thoại</th>
                                            <th>Gói thẻ</th>
                                            <th>Ngày bắt đầu</th>
                                            <th>Dự kiến hết</th>
                                            <th>Thực tế hết</th>
                                            <th>Lý do</th>
                                            <th>Lễ tân tiếp nhận</th>
                                            <th>Trạng thái</th>
                                            <th class="text-right">Chi tiết</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <c:forEach var="freezeItem" items="${recentFreezes}">
                                            <tr>
                                                <td>#${freezeItem.freezeRecordId}</td>
                                                <td>
                                                    <b><c:out value="${freezeItem.customerName}" /></b>
                                                    <br><small class="muted"><c:out value="${freezeItem.customerCode}" /></small>
                                                </td>
                                                <td><c:out value="${freezeItem.customerPhone}" /></td>
                                                <td><c:out value="${freezeItem.packageName}" /></td>
                                                <td><c:out value="${gms:formatDate(freezeItem.startDate)}" /></td>
                                                <td><c:out value="${gms:formatDate(freezeItem.plannedEndDate)}" /></td>
                                                <td>
                                                    <c:choose>
                                                        <c:when test="${not empty freezeItem.actualEndDate}">
                                                            <c:out value="${gms:formatDate(freezeItem.actualEndDate)}" />
                                                        </c:when>
                                                        <c:otherwise>
                                                            <span class="badge badge-warn">Đang bảo lưu</span>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </td>
                                                <td><c:out value="${freezeItem.reason}" /></td>
                                                <td><c:out value="${freezeItem.handledByEmployeeName}" /></td>
                                                <td>
                                                    <span class="badge ${freezeItem.statusBadgeClass}"><c:out value="${freezeItem.statusLabel}" /></span>
                                                </td>
                                                <td class="text-right">
                                                    <a href="${pageContext.request.contextPath}/receptionist/freeze?q=${freezeItem.customerCode}" class="btn btn-sm btn-ghost" title="Xem chi tiết hội viên">
                                                        <i class="fa-solid fa-eye"></i> Xem
                                                    </a>
                                                </td>
                                            </tr>
                                        </c:forEach>
                                    </tbody>
                                </table>
                            </div>
                        </c:otherwise>
                    </c:choose>
                </section>

            </div>
        </main>
    </div>
</div>

<%-- MODAL XÁC NHẬN MỞ LẠI THẺ TRƯỚC HẠN --%>
<c:if test="${not empty freezeCustomer and freezeCustomer.currentlyFrozen and freezeCustomer.canCancelEarly}">
    <div class="modal" id="modalCancelFreezeEarly" role="dialog" aria-modal="true" aria-labelledby="modalCancelTitle" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-head">
                    <h2 class="modal-title" id="modalCancelTitle"><i class="fa-solid fa-unlock text-warn"></i> Xác nhận mở lại thẻ trước hạn</h2>
                    <button class="modal-close" type="button" data-close-modal aria-label="Đóng">&times;</button>
                </div>
                <form method="POST" action="${pageContext.request.contextPath}/receptionist/freeze">
                    <input type="hidden" name="action" value="cancelFreezeEarly">
                    <input type="hidden" name="freezeRecordId" value="${freezeCustomer.openFreezeRecordId}">
                    <input type="hidden" name="q" value="<c:out value='${query}' />">

                    <div class="modal-body">
                        <p>Xác nhận mở lại thẻ tập cho hội viên <b><c:out value="${freezeCustomer.fullName}" /></b> (Mã: <b><c:out value="${freezeCustomer.customerCode}" /></b>)?</p>
                        
                        <div class="calc-box" style="margin: var(--space-3) 0;">
                            <div class="calc-item">
                                <span class="calc-lbl">Số ngày đã bảo lưu thực tế:</span>
                                <b class="calc-val text-primary">${freezeCustomer.daysFrozenIfCancelledToday} ngày</b>
                            </div>
                            <div class="calc-item">
                                <span class="calc-lbl">Hạn thẻ mới sau khi mở lại:</span>
                                <b class="calc-val text-success"><c:out value="${gms:formatDate(freezeCustomer.projectedNewEndDateIfCancelledToday)}" /></b>
                            </div>
                        </div>

                        <div class="alert-banner warning">
                            <i class="fa-solid fa-triangle-exclamation"></i>
                            <span><b>Lưu ý nghiệp vụ:</b> Lượt bảo lưu của gói vẫn được tính là đã dùng. Các buổi tập PT đã chuyển trạng thái Vắng trước đó sẽ không tự động phục hồi.</span>
                        </div>
                    </div>
                    <div class="modal-foot">
                        <button class="btn btn-ghost" type="button" data-close-modal>Bỏ qua</button>
                        <button class="btn btn-warning" type="submit"><i class="fa-solid fa-unlock"></i> Xác nhận mở lại</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</c:if>

</body>
</html>
