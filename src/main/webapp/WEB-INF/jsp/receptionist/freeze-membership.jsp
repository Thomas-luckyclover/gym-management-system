<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  freeze-membership.jsp — Màn hình Bảo lưu gói tập của Lễ tân (#34, UC-18, Workflow BF-09).
  Tuân thủ các luật nghiệp vụ:
  - BR-20: Bảo lưu gói của hội viên Active -> hội viên chuyển sang Frozen.
  - BR-21, BR-22, BR-23, BR-24: Chỉ Membership, miễn phí, chưa hết hạn, còn lượt bảo lưu.
  - BR-26, DBR-51: Lễ tân có thể hủy bảo lưu sớm, gia hạn số ngày thực tế, không hoàn phục buổi PT.
  - CBR-08, DBR-13: Ngày bắt đầu sớm nhất ngày mai, kết thúc <= min(startDate + P-02 tháng - 1 ngày, packageEndDate).
  - CBR-62, CBR-67, CBR-68: Liệt kê các buổi PT sẽ chuyển Missed (MembershipFrozen) và nhả slot Free (DBR-36).
  - CBR-115: Nhân viên không tự bảo lưu hồ sơ của chính mình.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
    <title>Bảo lưu gói tập · Super Gym Lễ tân</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/freeze-membership.css?v=20261026">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
    <script src="${pageContext.request.contextPath}/js/pages/receptionist/freeze-membership.js?v=20261026" defer></script>
    <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
</head>

<body class="dock theme-staff">
<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
        <%-- ===== Thanh tiêu đề topbar ===== --%>
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

        <%-- ===== Nội dung chính ===== --%>
        <main class="content" id="main-content" tabindex="-1">
            <div class="freeze-container">

                <%-- ===== KHỐI 1: TÌM KIẾM HỘI VIÊN (query: String) ===== --%>
                <section class="panel" data-reveal>
                    <div class="panel-head">
                        <div>
                            <h2>Tra cứu hội viên</h2>
                            <p>Nhập mã khách hàng (KH-xxxx), số điện thoại hoặc tên để thiết lập hoặc quản lý kỳ bảo lưu.</p>
                        </div>
                    </div>

                    <form id="freezeSearchForm" action="${pageContext.request.contextPath}/receptionist/freeze" method="GET" class="toolbar" novalidate>
                        <div class="search" style="flex:1;">
                            <i class="fa-solid fa-magnifying-glass"></i>
                            <input id="freezeQueryInput" name="q" type="search" value="<c:out value='${query}' />"
                                   placeholder="Nhập mã khách (KH-...), họ tên hoặc 4 số cuối SĐT..."
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

                <%-- Thông báo lỗi tra cứu nếu có --%>
                <c:if test="${not empty searchError}">
                    <div class="alert-banner danger" style="margin-top:var(--space-4);">
                        <i class="fa-solid fa-circle-xmark"></i>
                        <span><b>Không tìm thấy:</b> <c:out value="${searchError}" /></span>
                    </div>
                </c:if>

                <%-- ===== KHỐI 2: KẾT QUẢ TÌM THEO TÊN (searchResultsPage: PageResult<CustomerSearchItem>) ===== --%>
                <c:if test="${not empty searchResults}">
                    <section class="panel search-results-panel" data-reveal>
                        <div class="panel-head">
                            <div>
                                <h2>Kết quả tìm kiếm</h2>
                                <p>Tìm thấy <b><c:out value="${searchResultsPage.totalItems}" /></b> hội viên phù hợp với từ khóa "<c:out value='${query}' />". Chọn khách hàng cần thao tác.</p>
                            </div>
                        </div>

                        <div class="table-responsive">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Mã KH</th>
                                        <th>Họ tên</th>
                                        <th>Số điện thoại</th>
                                        <th>Trạng thái</th>
                                        <th class="text-right">Thao tác</th>
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
                                                    <c:out value="${cust.customerStatus}" />
                                                </span>
                                            </td>
                                            <td class="text-right">
                                                <a href="${pageContext.request.contextPath}/receptionist/freeze?q=${cust.customerCode}" class="btn btn-sm btn-primary">
                                                    Chọn khách
                                                </a>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </tbody>
                            </table>
                        </div>

                        <%-- Phân trang danh sách tìm kiếm --%>
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

                <%-- ===== KHỐI 3: CHI TIẾT HỘI VIÊN & NGHIỆP VỤ BẢO LƯU (freezeCustomer: FreezeCustomerView) ===== --%>
                <c:if test="${not empty freezeCustomer}">
                    <div class="freeze-workspace">

                        <%-- Cột bên trái: Thẻ hồ sơ hội viên & Gói tập hiện tại --%>
                        <div class="freeze-card customer-summary-card">
                            <div class="card-header">
                                <span class="card-tag">Hồ sơ hội viên</span>
                                <span class="badge ${freezeCustomer.customerStatus == 'Active' ? 'badge-ok' : (freezeCustomer.customerStatus == 'Frozen' ? 'badge-warn' : 'badge-ghost')}">
                                    <c:out value="${freezeCustomer.customerStatus}" />
                                </span>
                            </div>

                            <div class="customer-profile-block">
                                <div class="profile-avatar">
                                    <c:choose>
                                        <c:when test="${not empty freezeCustomer.avatar}">
                                            <img src="${pageContext.request.contextPath}${freezeCustomer.avatar}" alt="Avatar">
                                        </c:when>
                                        <c:when test="${not empty freezeCustomer.checkInPhoto}">
                                            <img src="${pageContext.request.contextPath}${freezeCustomer.checkInPhoto}" alt="Ảnh check-in">
                                        </c:when>
                                        <c:otherwise>
                                            <div class="avatar-placeholder"><i class="fa-solid fa-user"></i></div>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                                <div class="profile-meta">
                                    <h3 class="customer-name"><c:out value="${freezeCustomer.fullName}" /></h3>
                                    <div class="meta-row">
                                        <span class="meta-label">Mã KH:</span>
                                        <span class="meta-value"><b><c:out value="${freezeCustomer.customerCode}" /></b></span>
                                    </div>
                                    <div class="meta-row">
                                        <span class="meta-label">Số điện thoại:</span>
                                        <span class="meta-value"><c:out value="${freezeCustomer.phone}" /></span>
                                    </div>
                                </div>
                            </div>

                            <hr class="divider">

                            <%-- Thông tin gói tập Membership hiện tại (SRS 6.3 - Field group: Current package) --%>
                            <div class="current-package-block">
                                <h4 class="sub-title"><i class="fa-solid fa-id-card"></i> Gói tập hiện tại</h4>
                                <c:choose>
                                    <c:when test="${freezeCustomer.hasMembership}">
                                        <div class="package-info-box">
                                            <div class="pkg-header">
                                                <span class="pkg-name"><c:out value="${freezeCustomer.packageName}" /></span>
                                                <span class="badge ${freezeCustomer.subscriptionStatus == 'Active' ? 'badge-ok' : (freezeCustomer.subscriptionStatus == 'Frozen' ? 'badge-warn' : 'badge-ghost')}">
                                                    <c:out value="${freezeCustomer.subscriptionStatus}" />
                                                </span>
                                            </div>
                                            <div class="pkg-grid">
                                                <div class="pkg-item">
                                                    <span class="item-label">Ngày bắt đầu:</span>
                                                    <span class="item-value"><c:out value="${gms:formatDate(freezeCustomer.packageStartDate)}" /></span>
                                                </div>
                                                <div class="pkg-item">
                                                    <span class="item-label">Ngày hết hạn:</span>
                                                    <span class="item-value"><b><c:out value="${gms:formatDate(freezeCustomer.packageEndDate)}" /></b></span>
                                                </div>
                                                <div class="pkg-item">
                                                    <span class="item-label">Lượt bảo lưu:</span>
                                                    <span class="item-value">
                                                        <c:out value="${freezeCustomer.freezeUsed}" /> / <c:out value="${freezeCustomer.maxFreezesSnapshot}" /> lần
                                                        <c:if test="${freezeCustomer.freezeUsed >= freezeCustomer.maxFreezesSnapshot}">
                                                            <small class="text-danger">(Đã hết lượt)</small>
                                                        </c:if>
                                                    </span>
                                                </div>
                                                <div class="pkg-item">
                                                    <span class="item-label">Mã quyền dùng:</span>
                                                    <span class="item-value">#${freezeCustomer.subscriptionId}</span>
                                                </div>
                                            </div>
                                        </div>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="alert-banner warning" style="margin-top:var(--space-2);">
                                            <i class="fa-solid fa-triangle-exclamation"></i>
                                            <span>Hội viên hiện không có gói tập Membership nào đang hoạt động.</span>
                                        </div>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </div>

                        <%-- Cột bên phải: Nghiệp vụ Bảo lưu (Tạo mới hoặc Hủy sớm hoặc Cảnh báo không hợp lệ) --%>
                        <div class="freeze-card action-card">

                            <%-- TÌNH HUỐNG A: GÓI ĐANG BẢO LƯU (CURRENTLY FROZEN) --%>
                            <c:if test="${freezeCustomer.currentlyFrozen}">
                                <div class="card-header">
                                    <span class="card-tag">Trạng thái bảo lưu</span>
                                    <span class="badge badge-warn"><i class="fa-solid fa-snowflake"></i> Đang bảo lưu</span>
                                </div>

                                <div class="frozen-active-banner">
                                    <div class="banner-icon"><i class="fa-solid fa-snowflake"></i></div>
                                    <div class="banner-body">
                                        <h3>Gói tập đang được bảo lưu</h3>
                                        <p>Thời gian bảo lưu dự kiến: từ ngày <b><c:out value="${gms:formatDate(freezeCustomer.currentFreezeStartDate)}" /></b> đến ngày <b><c:out value="${gms:formatDate(freezeCustomer.currentFreezePlannedEndDate)}" /></b>.</p>
                                        <div class="freeze-meta-box">
                                            <p><b>Nhân viên lập:</b> <c:out value="${freezeCustomer.currentFreezeHandledBy}" /></p>
                                            <p><b>Thời điểm lập:</b> <c:out value="${gms:formatDateTime(freezeCustomer.currentFreezeCreatedAt)}" /></p>
                                            <p><b>Lý do:</b> <c:out value="${freezeCustomer.currentFreezeReason}" /></p>
                                        </div>
                                    </div>
                                </div>

                                <%-- Dự kiến hủy bảo lưu sớm (BR-26, DBR-51) --%>
                                <div class="early-cancel-preview-box">
                                    <h4><i class="fa-solid fa-clock-rotate-left"></i> Hủy bảo lưu sớm tại quầy</h4>
                                    <p>Khi hủy bảo lưu sớm, gói tập sẽ mở lại ngay lập tức (trở về Active). Hạn gói tập sẽ được gia hạn thêm đúng số ngày thực tế đã bảo lưu tính đến hôm nay.</p>
                                    <div class="calc-highlight">
                                        <div class="calc-col">
                                            <span>Số ngày đã bảo lưu thực tế:</span>
                                            <b>${freezeCustomer.daysFrozenIfCancelledToday} ngày</b>
                                        </div>
                                        <div class="calc-col">
                                            <span>Hạn gói tập mới sau khi hủy:</span>
                                            <b><c:out value="${gms:formatDate(freezeCustomer.projectedNewEndDateIfCancelledToday)}" /></b>
                                        </div>
                                    </div>
                                    <div class="alert-banner info" style="margin-top:var(--space-3);">
                                        <i class="fa-solid fa-circle-info"></i>
                                        <span>Lưu ý: Lượt bảo lưu của gói vẫn được tính là đã sử dụng (BR-24). Các buổi PT đã chuyển sang Vắng sẽ không được phục hồi (CBR-68).</span>
                                    </div>
                                    <c:choose>
                                        <c:when test="${freezeCustomer.canCancelEarly}">
                                            <button type="button" class="btn btn-warning" id="btnOpenCancelFreezeModal" style="margin-top:var(--space-4);">
                                                <i class="fa-solid fa-unlock"></i> Xác nhận hủy bảo lưu sớm
                                            </button>
                                        </c:when>
                                        <c:otherwise>
                                            <div class="alert-banner danger" style="margin-top:var(--space-4);">
                                                <i class="fa-solid fa-ban"></i>
                                                <span>Bạn không thể tự hủy bảo lưu trên hồ sơ khách của chính mình (CBR-115).</span>
                                            </div>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </c:if>

                            <%-- TÌNH HUỐNG B: ĐỦ ĐIỀU KIỆN TẠO MỚI BẢO LƯU (ELIGIBLE) --%>
                            <c:if test="${not freezeCustomer.currentlyFrozen and freezeCustomer.eligibleToFreeze}">
                                <div class="card-header">
                                    <span class="card-tag">Lập phiếu bảo lưu mới</span>
                                    <span class="badge badge-ok">Đủ điều kiện</span>
                                </div>

                                <p class="form-desc">
                                    Nhập khoảng thời gian bảo lưu và lý do của hội viên. Thời gian bảo lưu tối đa <b>${freezeCustomer.maxFreezeMonths} tháng</b> (P-02) và phải kết thúc trước hoặc đúng ngày hết hạn gói (<b><c:out value="${gms:formatDate(freezeCustomer.packageEndDate)}" /></b>).
                                </p>

                                <form id="formApplyFreeze" method="POST" action="${pageContext.request.contextPath}/receptionist/freeze" novalidate>
                                    <input type="hidden" name="action" value="applyFreeze">
                                    <input type="hidden" name="subscriptionId" value="${freezeCustomer.subscriptionId}">
                                    <input type="hidden" name="q" value="<c:out value='${query}' />">
                                    <input type="hidden" id="customerIdHidden" value="${freezeCustomer.customerId}">
                                    <input type="hidden" id="packageEndDateHidden" value="${freezeCustomer.packageEndDate}">
                                    <input type="hidden" id="maxMonthsHidden" value="${freezeCustomer.maxFreezeMonths}">

                                    <div class="form-row">
                                        <div class="form-group">
                                            <label for="startDate">Ngày bắt đầu bảo lưu: <span class="req">*</span></label>
                                            <input type="date" id="startDate" name="startDate" class="form-control"
                                                   min="${freezeCustomer.minStartDate}"
                                                   value="${freezeCustomer.minStartDate}" required>
                                            <small class="hint">Sớm nhất từ ngày mai (<c:out value="${gms:formatDate(freezeCustomer.minStartDate)}" />) theo DBR-13.</small>
                                        </div>

                                        <div class="form-group">
                                            <label for="plannedEndDate">Ngày kết thúc dự kiến: <span class="req">*</span></label>
                                            <input type="date" id="plannedEndDate" name="plannedEndDate" class="form-control"
                                                   min="${freezeCustomer.minStartDate}"
                                                   max="${freezeCustomer.maxEndDate}"
                                                   value="${freezeCustomer.maxEndDate}" required>
                                            <small class="hint">Tối đa đến ngày <c:out value="${gms:formatDate(freezeCustomer.maxEndDate)}" /> (GBR-03, P-02).</small>
                                        </div>
                                    </div>

                                    <%-- Tính toán tự động số ngày và hạn mới --%>
                                    <div class="freeze-calc-callout" id="freezeCalcCallout">
                                        <div class="calc-line">
                                            <span>Số ngày bảo lưu dự kiến:</span>
                                            <b id="displayDaysCount">-- ngày</b>
                                        </div>
                                        <div class="calc-line">
                                            <span>Hạn gói tập mới sau khi tự động mở lại:</span>
                                            <b id="displayNewExpiryDate">--/--/----</b>
                                        </div>
                                    </div>

                                    <div class="form-group" style="margin-top:var(--space-3);">
                                        <label for="freezeReason">Lý do bảo lưu: <span class="req">*</span></label>
                                        <textarea id="freezeReason" name="reason" class="form-control" rows="3"
                                                  placeholder="Nhập lý do chi tiết (công tác nước ngoài, chấn thương, việc gia đình... tối thiểu 5 ký tự)"
                                                  minlength="5" maxlength="500" required></textarea>
                                        <small class="hint">Tối thiểu 5 ký tự theo quy định hệ thống (CK_FreezeRecords_Reason).</small>
                                    </div>

                                    <%-- KHỐI CẢNH BÁO BUỔI PT (CBR-62) --%>
                                    <div class="pt-impact-section" id="ptImpactSection">
                                        <div class="pt-impact-head">
                                            <h4><i class="fa-solid fa-calendar-xmark"></i> Buổi tập PT trong kỳ bảo lưu (<span id="impactCount">0</span> buổi)</h4>
                                        </div>
                                        <p class="pt-impact-desc">
                                            Theo quy định <b>CBR-62</b>: Khi bảo lưu gói tập, các buổi tập cá nhân (PT) của hội viên trong kỳ bảo lưu sẽ tự động chuyển sang trạng thái <b>Vắng (Missed / MembershipFrozen)</b> — trừ buổi và không hoàn tiền. Khung giờ tập sẽ được giải phóng về trạng thái Trống (Free) để PT nhận khách khác (DBR-36).
                                        </p>

                                        <div id="impactSessionsContainer">
                                            <c:choose>
                                                <c:when test="${not empty freezeCustomer.affectedSessions}">
                                                    <div class="table-responsive">
                                                        <table class="data-table pt-table">
                                                            <thead>
                                                                <tr>
                                                                    <th>Ngày tập</th>
                                                                    <th>Giờ tập</th>
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
                                                        <i class="fa-solid fa-circle-check"></i> Không có buổi tập PT nào bị ảnh hưởng trong khoảng thời gian này.
                                                    </div>
                                                </c:otherwise>
                                            </c:choose>
                                        </div>
                                    </div>

                                    <div class="form-actions" style="margin-top:var(--space-4);">
                                        <button type="submit" class="btn btn-primary" id="btnSubmitFreeze">
                                            <i class="fa-solid fa-snowflake"></i> Xác nhận bảo lưu gói tập
                                        </button>
                                    </div>
                                </form>
                            </c:if>

                            <%-- TÌNH HUỐNG C: KHÔNG ĐỦ ĐIỀU KIỆN BẢO LƯU --%>
                            <c:if test="${not freezeCustomer.currentlyFrozen and not freezeCustomer.eligibleToFreeze}">
                                <div class="card-header">
                                    <span class="card-tag">Không thể bảo lưu</span>
                                    <span class="badge badge-danger">Từ chối</span>
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
                        <section class="panel" style="margin-top:var(--space-5);" data-reveal>
                            <div class="panel-head">
                                <div>
                                    <h3>Lịch sử các lần bảo lưu của <c:out value="${freezeCustomer.fullName}" /></h3>
                                    <p>Danh sách toàn bộ các đợt bảo lưu đã từng thực hiện trên hồ sơ hội viên này.</p>
                                </div>
                            </div>

                            <div class="table-responsive">
                                <table class="data-table">
                                    <thead>
                                        <tr>
                                            <th>Mã phiếu</th>
                                            <th>Gói tập</th>
                                            <th>Bắt đầu</th>
                                            <th>Dự kiến hết</th>
                                            <th>Thực tế hết</th>
                                            <th>Số ngày</th>
                                            <th>Lý do</th>
                                            <th>Lễ tân tiếp nhận</th>
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
                                                            <span class="muted">Đang bảo lưu</span>
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

                <%-- ===== KHỐI 5: DANH SÁCH BẢO LƯU GẦN ĐÂY CỦA HỆ THỐNG ===== --%>
                <section class="panel" style="margin-top:var(--space-5);" data-reveal>
                    <div class="panel-head">
                        <div>
                            <h2>Các đợt bảo lưu gần đây của phòng tập</h2>
                            <p>Theo dõi các phiếu bảo lưu đang mở và lịch sử bảo lưu gần nhất trong hệ thống.</p>
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
                                            <th>Gói tập</th>
                                            <th>Bắt đầu</th>
                                            <th>Dự kiến hết</th>
                                            <th>Thực tế hết</th>
                                            <th>Lý do</th>
                                            <th>Lễ tân lập</th>
                                            <th>Trạng thái</th>
                                            <th class="text-right">Thao tác</th>
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
                                                            <span class="muted">Đang bảo lưu</span>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </td>
                                                <td><c:out value="${freezeItem.reason}" /></td>
                                                <td><c:out value="${freezeItem.handledByEmployeeName}" /></td>
                                                <td>
                                                    <span class="badge ${freezeItem.statusBadgeClass}"><c:out value="${freezeItem.statusLabel}" /></span>
                                                </td>
                                                <td class="text-right">
                                                    <a href="${pageContext.request.contextPath}/receptionist/freeze?q=${freezeItem.customerCode}" class="btn btn-sm btn-ghost" title="Xem chi tiết">
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

<%-- ===== MODAL XÁC NHẬN HỦY BẢO LƯU SỚM (BR-26, DBR-51) ===== --%>
<c:if test="${not empty freezeCustomer and freezeCustomer.currentlyFrozen and freezeCustomer.canCancelEarly}">
    <div class="modal" id="modalCancelFreezeEarly" role="dialog" aria-modal="true" aria-labelledby="modalCancelTitle" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-head">
                    <h2 class="modal-title" id="modalCancelTitle"><i class="fa-solid fa-triangle-exclamation text-warn"></i> Xác nhận hủy bảo lưu sớm</h2>
                    <button class="modal-close" type="button" data-close-modal aria-label="Đóng">&times;</button>
                </div>
                <form method="POST" action="${pageContext.request.contextPath}/receptionist/freeze">
                    <input type="hidden" name="action" value="cancelFreezeEarly">
                    <input type="hidden" name="freezeRecordId" value="${freezeCustomer.openFreezeRecordId}">
                    <input type="hidden" name="q" value="<c:out value='${query}' />">

                    <div class="modal-body">
                        <p>Bạn có chắc chắn muốn hủy bảo lưu sớm cho hội viên <b><c:out value="${freezeCustomer.fullName}" /></b> (Mã: <b><c:out value="${freezeCustomer.customerCode}" /></b>)?</p>
                        <div class="calc-highlight" style="margin: var(--space-3) 0;">
                            <div class="calc-col">
                                <span>Số ngày đã bảo lưu:</span>
                                <b>${freezeCustomer.daysFrozenIfCancelledToday} ngày</b>
                            </div>
                            <div class="calc-col">
                                <span>Hạn gói mới sau khi mở lại:</span>
                                <b><c:out value="${gms:formatDate(freezeCustomer.projectedNewEndDateIfCancelledToday)}" /></b>
                            </div>
                        </div>
                        <div class="alert-banner danger">
                            <i class="fa-solid fa-triangle-exclamation"></i>
                            <span><b>Cảnh báo nghiệp vụ (BR-26, CBR-68):</b> Lượt bảo lưu vẫn bị tính đã dùng. Các buổi PT đã chuyển sang trạng thái Vắng (Missed) trong kỳ bảo lưu sẽ <b>KHÔNG</b> được phục hồi lại.</span>
                        </div>
                    </div>
                    <div class="modal-foot">
                        <button class="btn btn-ghost" type="button" data-close-modal>Bỏ qua</button>
                        <button class="btn btn-warning" type="submit"><i class="fa-solid fa-unlock"></i> Xác nhận hủy bảo lưu</button>
                    </div>
                </form>
            </div>
        </div>
    </div>
</c:if>

</body>
</html>
