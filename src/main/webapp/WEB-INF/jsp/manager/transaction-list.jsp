<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
    <head>
        <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
        <title>Giao dịch toàn hệ thống · Super Gym</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/transaction-list.css?v=20261102">

        <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
        <script src="${pageContext.request.contextPath}/js/pages/manager/transaction-list.js?v=<%= System.currentTimeMillis() %>" defer></script>
    </head>

    <body class="dock theme-manager">
        <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

        <div class="app">
            <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

            <div class="main">
                <header class="topbar">
                    <div class="tb-left">
                        <h1 class="tb-title">
                            Giao dịch toàn hệ thống
                            <span class="pt-info">
                                <button type="button" class="pt-info-trigger" aria-label="Giải thích màn hình" aria-describedby="txHint">
                                    <span aria-hidden="true">i</span>
                                </button>
                                <span id="txHint" class="pt-info-popover" role="tooltip">
                                    <strong>Giao dịch toàn hệ thống (Màn #43 - UC-26)</strong><br>
                                    • <strong>Giao dịch bán gói:</strong> Tra cứu mọi đơn hàng bán gói tập (Online qua VNPay và Offline tại quầy). Lọc và thực hiện hoàn tiền ngoài hệ thống cho các giao dịch cần đối soát hoàn tiền (CBR-72).<br>
                                    • <strong>Lịch sử chốt ca:</strong> Theo dõi lịch sử giao nhận ca, tiền mặt đếm được và đối soát tiền ca trực của Lễ tân (UC-25, BF-06).
                                </span>
                            </span>
                        </h1>
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

                <main class="content" id="main-content" tabindex="-1">

                    <c:if test="${not empty pageError}">
                        <div class="tx-callout danger"><c:out value="${pageError}" /></div>
                    </c:if>

                    <section class="tx-panel">

                        <%-- ===== TAB ===== --%>
                        <div class="tx-tabs" role="tablist">
                            <a class="tx-tab ${currentTab != 'shifts' ? 'active' : ''}"
                               href="${pageContext.request.contextPath}/manager/transactions?tab=orders">
                                <span>Giao dịch bán gói</span>
                                <c:if test="${pendingRefundCount > 0}">
                                    <span class="tx-count" title="${pendingRefundCount} giao dịch cần hoàn tiền">${pendingRefundCount}</span>
                                </c:if>
                            </a>
                            <a class="tx-tab ${currentTab == 'shifts' ? 'active' : ''}"
                               href="${pageContext.request.contextPath}/manager/transactions?tab=shifts">
                                <span>Lịch sử chốt ca</span>
                            </a>
                        </div>

                        <%-- ===================================================================
                             TAB 1: GIAO DỊCH BÁN GÓI
                             =================================================================== --%>
                        <c:if test="${currentTab != 'shifts'}">

                            <c:url var="exportUrl" value="/manager/transactions">
                                <c:param name="action" value="export" />
                                <c:param name="state" value="${filterState}" />
                                <c:if test="${not empty searchKeyword}"><c:param name="search" value="${searchKeyword}" /></c:if>
                                <c:if test="${not empty filterMethod}"><c:param name="method" value="${filterMethod}" /></c:if>
                                <c:if test="${not empty fromDate}"><c:param name="from" value="${fromDate}" /></c:if>
                                <c:if test="${not empty toDate}"><c:param name="to" value="${toDate}" /></c:if>
                            </c:url>

                            <form action="${pageContext.request.contextPath}/manager/transactions" method="get" class="tx-filter-form">
                                <input type="hidden" name="tab" value="orders">
                                <input type="hidden" name="pageSize" value="${orderPage.pageSize}">

                                <%-- Hàng 1: tìm kiếm + Excel --%>
                                <div class="tx-toolbar">
                                    <div class="tx-search">
                                        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.3-4.3"/></svg>
                                        <input type="search" name="search" maxlength="100"
                                               value="<c:out value='${searchKeyword}' />"
                                               placeholder="Tìm theo SĐT, tên khách hoặc mã giao dịch"
                                               aria-label="Tìm kiếm giao dịch">
                                    </div>
                                    <div class="tx-toolbar-end">
                                        <a class="btn btn-outline btn-sm" href="${exportUrl}" title="Tải danh sách đang lọc (.xlsx)">&#11015; Excel</a>
                                    </div>
                                </div>

                                <%-- Hàng 2: Tình trạng, Hình thức, Từ ngày, Đến ngày --%>
                                <div class="tx-filters">
                                    <label for="fState">Tình trạng</label>
                                    <select class="tx-select" id="fState" name="state">
                                        <option value="" ${empty filterState ? 'selected' : ''}>Tất cả tình trạng</option>
                                        <option value="refund" ${filterState == 'refund' ? 'selected' : ''}>Cần hoàn tiền<c:if test="${pendingRefundCount > 0}"> (${pendingRefundCount})</c:if></option>
                                        <option value="pending" ${filterState == 'pending' ? 'selected' : ''}>Chờ thanh toán</option>
                                        <option value="paid" ${filterState == 'paid' ? 'selected' : ''}>Đã thanh toán</option>
                                        <option value="done" ${filterState == 'done' ? 'selected' : ''}>Đã hoàn tiền</option>
                                        <option value="cancelled" ${filterState == 'cancelled' ? 'selected' : ''}>Đã hủy / hết hạn</option>
                                    </select>

                                    <label for="fMethod">Hình thức</label>
                                    <select class="tx-select" id="fMethod" name="method">
                                        <option value="" ${empty filterMethod ? 'selected' : ''}>Tất cả hình thức</option>
                                        <option value="online" ${filterMethod == 'online' ? 'selected' : ''}>Online</option>
                                        <option value="counter" ${filterMethod == 'counter' ? 'selected' : ''}>Offline (tất cả)</option>
                                        <option value="transfer" ${filterMethod == 'transfer' ? 'selected' : ''}>Offline · Chuyển khoản</option>
                                        <option value="cash" ${filterMethod == 'cash' ? 'selected' : ''}>Offline · Tiền mặt</option>
                                    </select>

                                    <label for="fFrom">Từ ngày</label>
                                    <input class="tx-select" type="date" id="fFrom" name="from" value="${fromDate}">

                                    <label for="fTo">Đến ngày</label>
                                    <input class="tx-select" type="date" id="fTo" name="to" value="${toDate}">

                                    <div class="tx-filters-end">
                                        <a class="btn btn-ghost btn-sm"
                                           href="${pageContext.request.contextPath}/manager/transactions?tab=orders&state=">Xóa lọc</a>
                                    </div>
                                </div>
                            </form>

                            <%-- ===== BẢNG ===== --%>
                            <div id="txOrdersTableArea">
                            <c:choose>
                                <c:when test="${not empty orderPage.items}">
                                    <div class="tx-table-wrap">
                                        <table class="tx-table">
                                            <thead>
                                                <tr>
                                                    <th>Mã giao dịch</th>
                                                    <th>Hình thức</th>
                                                    <th>Khách hàng</th>
                                                    <th>Gói tập</th>
                                                    <th class="r">Tiền</th>
                                                    <th>Tình trạng</th>
                                                    <th class="r">Thao tác</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <c:forEach var="item" items="${orderPage.items}">
                                                    <c:set var="refundOpen" value="${item.needsRefund and empty item.refundedAt}" />
                                                    <tr class="${refundOpen ? 'is-refund' : ''}">

                                                        <%-- Mã giao dịch + ngày giờ --%>
                                                        <td>
                                                            <div class="tx-code"><c:out value="${item.orderCode}" /></div>
                                                            <div class="tx-sub"><c:out value="${gms:formatDateTime(item.createdAt)}" /></div>
                                                        </td>

                                                        <%-- Hình thức --%>
                                                        <td>
                                                            <span class="tx-chan">
                                                                <span class="tx-dot ${item.channel == 'Online' ? 'online' : ''}"></span>
                                                                <c:choose>
                                                                    <c:when test="${item.channel == 'Online'}">Online</c:when>
                                                                    <c:when test="${item.paymentMethod == 'Transfer'}">Offline · Chuyển khoản</c:when>
                                                                    <c:otherwise>Offline · Tiền mặt</c:otherwise>
                                                                </c:choose>
                                                            </span>
                                                        </td>

                                                        <%-- Khách hàng (Zalo chỉ ở dòng cần hoàn) --%>
                                                        <td>
                                                            <div class="tx-name"><c:out value="${item.customerName}" default="Khách lẻ" /></div>
                                                            <div class="tx-phone">
                                                                <span><c:out value="${item.customerPhone}" default="---" /></span>
                                                                <c:if test="${refundOpen and not empty item.customerPhone}">
                                                                    <a class="tx-zalo" target="_blank" rel="noopener"
                                                                       href="https://zalo.me/<c:out value='${item.customerPhone}' />">Zalo &#8599;</a>
                                                                </c:if>
                                                            </div>
                                                        </td>

                                                        <%-- Gói tập (chỉ tên; hiệu lực xem trong popup) --%>
                                                        <td>
                                                            <div class="tx-pkg" title="<c:out value='${item.packageName}' />">
                                                                <c:out value="${item.packageName}" default="---" />
                                                            </div>
                                                        </td>

                                                        <td class="r"><span class="tx-money"><c:out value="${gms:formatMoney(item.totalAmount)}" /></span></td>

                                                        <%-- Tình trạng: nhãn + lý do trong tooltip --%>
                                                        <td>
                                                            <c:choose>
                                                                <c:when test="${item.needsRefund and not empty item.refundedAt}">
                                                                    <c:set var="stCls" value="badge-success" />
                                                                    <c:set var="stTxt" value="Đã hoàn tiền" />
                                                                    <c:set var="stTip" value="${item.refundedByName} đã hoàn lúc ${gms:formatDateTime(item.refundedAt)}. Ghi chú: ${item.refundNote}" />
                                                                </c:when>
                                                                <c:when test="${item.needsRefund}">
                                                                    <c:set var="stCls" value="badge-danger" />
                                                                    <c:set var="stTxt" value="Cần hoàn tiền" />
                                                                    <c:choose>
                                                                        <c:when test="${item.cancelType == 'PaymentTimeout'}"><c:set var="stTip" value="Quá hạn thanh toán, khách vẫn trả. Cần hoàn lại tiền cho khách." /></c:when>
                                                                        <c:when test="${item.cancelType == 'ReplacedByNewOrder'}"><c:set var="stTip" value="Khách trả vào đơn cũ đã bị thay bằng đơn mới. Cần hoàn lại tiền cho khách." /></c:when>
                                                                        <c:when test="${item.cancelType == 'InvalidPayment'}"><c:set var="stTip" value="Số tiền VNPay thu không khớp với đơn. Cần hoàn lại tiền cho khách." /></c:when>
                                                                        <c:otherwise><c:set var="stTip" value="Khách đã trả tiền nhưng đơn không hợp lệ. Cần hoàn lại tiền cho khách." /></c:otherwise>
                                                                    </c:choose>
                                                                </c:when>
                                                                <c:when test="${item.orderStatus == 'Paid'}">
                                                                    <c:set var="stCls" value="badge-success" />
                                                                    <c:set var="stTxt" value="Đã thanh toán" />
                                                                    <c:set var="stTip" value="" />
                                                                </c:when>
                                                                <c:when test="${item.orderStatus == 'Pending'}">
                                                                    <c:set var="stCls" value="badge-warning" />
                                                                    <c:set var="stTxt" value="Chờ thanh toán" />
                                                                    <c:set var="stTip" value="Khách chưa thanh toán xong. Quá hạn thì hệ thống tự hủy." />
                                                                </c:when>
                                                                <c:when test="${item.cancelType == 'PaymentTimeout'}">
                                                                    <c:set var="stCls" value="badge-neutral" />
                                                                    <c:set var="stTxt" value="Hết hạn thanh toán" />
                                                                    <c:set var="stTip" value="Quá hạn thanh toán nên hệ thống tự hủy. Không ghi nhận khoản thanh toán nào." />
                                                                </c:when>
                                                                <c:when test="${item.cancelType == 'ReplacedByNewOrder'}">
                                                                    <c:set var="stCls" value="badge-neutral" />
                                                                    <c:set var="stTxt" value="Đã thay bằng đơn mới" />
                                                                    <c:set var="stTip" value="Khách tạo đơn mới thay cho đơn này. Không ghi nhận khoản thanh toán nào cho đơn này." />
                                                                </c:when>
                                                                <c:when test="${item.cancelType == 'InvalidPayment'}">
                                                                    <c:set var="stCls" value="badge-danger" />
                                                                    <c:set var="stTxt" value="Thanh toán không hợp lệ" />
                                                                    <c:set var="stTip" value="VNPay báo thành công nhưng số tiền không khớp." />
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <c:set var="stCls" value="badge-neutral" />
                                                                    <c:set var="stTxt" value="Đã hủy tại quầy" />
                                                                    <c:set var="stTip" value="Lễ tân hủy tại quầy. Tiền trả lại khách ở quầy, không qua màn này." />
                                                                </c:otherwise>
                                                            </c:choose>

                                                            <div class="tx-status tx-tip"
                                                                 <c:if test="${not empty stTip}">data-tip="<c:out value='${stTip}' />" tabindex="0"</c:if>>
                                                                <span class="badge ${stCls}"><c:out value="${stTxt}" /></span>
                                                            </div>
                                                            <c:if test="${refundOpen and item.waitDays > 0}">
                                                                <div class="tx-wait">Chờ ${item.waitDays} ngày</div>
                                                            </c:if>
                                                        </td>

                                                        <%-- Thao tác --%>
                                                        <td>
                                                            <div class="tx-actions">
                                                                <button type="button" class="btn btn-outline btn-sm btn-view-detail"
                                                                        data-order-id="${item.orderId}">Chi tiết</button>
                                                                <c:if test="${refundOpen and item.channel == 'Online'}">
                                                                    <button type="button" class="btn btn-primary btn-sm btn-table-refund"
                                                                            data-transaction-id="${item.refundableTransactionId}"
                                                                            data-order-code="<c:out value='${item.orderCode}' />"
                                                                            data-package="<c:out value='${item.packageName}' />"
                                                                            data-amount="${item.totalAmount}"
                                                                            data-customer="<c:out value='${item.customerName}' />"
                                                                            data-phone="<c:out value='${item.customerPhone}' />">Hoàn tiền</button>
                                                                </c:if>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                </c:forEach>
                                            </tbody>
                                        </table>
                                    </div>
                                </c:when>
                                <c:otherwise>
                                    <div class="tx-empty">Không có giao dịch nào phù hợp với bộ lọc.</div>
                                </c:otherwise>
                            </c:choose>

                            <%-- ===== PHÂN TRANG ===== --%>
                            <c:url var="pageBase" value="/manager/transactions">
                                <c:param name="tab" value="orders" />
                                <c:param name="pageSize" value="${orderPage.pageSize}" />
                                <c:param name="state" value="${filterState}" />
                                <c:if test="${not empty searchKeyword}"><c:param name="search" value="${searchKeyword}" /></c:if>
                                <c:if test="${not empty filterMethod}"><c:param name="method" value="${filterMethod}" /></c:if>
                                <c:if test="${not empty fromDate}"><c:param name="from" value="${fromDate}" /></c:if>
                                <c:if test="${not empty toDate}"><c:param name="to" value="${toDate}" /></c:if>
                            </c:url>
                            <div class="tx-foot">
                                <div class="tx-foot-meta">
                                    <span>Hiển thị</span>
                                    <select class="tx-select-sm" data-page-size-select aria-label="Số dòng mỗi trang">
                                        <option value="10" ${orderPage.pageSize == 10 ? 'selected' : ''}>10 / trang</option>
                                        <option value="20" ${orderPage.pageSize == 20 ? 'selected' : ''}>20 / trang</option>
                                        <option value="50" ${orderPage.pageSize == 50 ? 'selected' : ''}>50 / trang</option>
                                    </select>
                                    <span>· Trang <b>${orderPage.pageNumber}</b> / <b>${orderPage.totalPages}</b> (${orderPage.totalItems} giao dịch)</span>
                                </div>
                                <span class="tx-foot-nav">
                                    <c:if test="${orderPage.hasPrevious}">
                                        <a class="btn btn-ghost btn-sm" href="${pageBase}&page=${orderPage.pageNumber - 1}">&larr; Trang trước</a>
                                    </c:if>
                                    <c:if test="${orderPage.hasNext}">
                                        <a class="btn btn-ghost btn-sm" href="${pageBase}&page=${orderPage.pageNumber + 1}">Trang sau &rarr;</a>
                                    </c:if>
                                </span>
                            </div>
                            </div>
                        </c:if>

                        <%-- ===================================================================
                             TAB 2: LỊCH SỬ CHỐT CA
                             =================================================================== --%>
                        <c:if test="${currentTab == 'shifts'}">
                            <form action="${pageContext.request.contextPath}/manager/transactions" method="get" class="tx-filter-form">
                                <input type="hidden" name="tab" value="shifts">
                                <input type="hidden" name="pageSize" value="${shiftPage.pageSize}">
                                <div class="tx-filters">
                                    <label for="sFrom">Từ ngày</label>
                                    <input class="tx-select" type="date" id="sFrom" name="from" value="${shiftFromDate}">
                                    <label for="sTo">Đến ngày</label>
                                    <input class="tx-select" type="date" id="sTo" name="to" value="${shiftToDate}">
                                    <div class="tx-filters-end">
                                        <a class="btn btn-ghost btn-sm" href="${pageContext.request.contextPath}/manager/transactions?tab=shifts">Xóa lọc</a>
                                    </div>
                                </div>
                            </form>

                            <div id="txShiftsTableArea">
                            <c:choose>
                                <c:when test="${not empty shiftPage.items}">
                                    <div class="tx-table-wrap">
                                        <table class="tx-table">
                                            <thead>
                                                <tr>
                                                    <th>Ca</th>
                                                    <th>Người mở</th>
                                                    <th>Người chốt</th>
                                                    <th class="r">Tiền mặt dự tính</th>
                                                    <th class="r">Tiền mặt đếm được</th>
                                                    <th>Chênh lệch</th>
                                                    <th class="r">Chuyển khoản</th>
                                                    <th class="r">Số hóa đơn</th>
                                                </tr>
                                            </thead>
                                            <tbody>
                                                <c:forEach var="shift" items="${shiftPage.items}">
                                                    <tr>
                                                        <td>
                                                            <div class="tx-code"><c:out value="${gms:formatDate(shift.workDate)}" /></div>
                                                            <div class="tx-sub">
                                                                <c:choose>
                                                                    <c:when test="${shift.shiftType == 'Morning'}">Ca sáng</c:when>
                                                                    <c:when test="${shift.shiftType == 'Afternoon'}">Ca chiều</c:when>
                                                                    <c:when test="${shift.shiftType == 'Evening'}">Ca tối</c:when>
                                                                    <c:otherwise><c:out value="${shift.shiftType}" /></c:otherwise>
                                                                </c:choose>
                                                                · <c:out value="${gms:formatTime(shift.openedAt)}" /> &rarr; <c:out value="${gms:formatTime(shift.closedAt)}" />
                                                            </div>
                                                        </td>
                                                        <td><span class="tx-name"><c:out value="${shift.openedByName}" default="---" /></span></td>
                                                        <td><span class="tx-name"><c:out value="${shift.closedByName}" default="---" /></span></td>
                                                        <td class="r"><span class="tx-pay"><c:out value="${gms:formatMoney(shift.expectedCash)}" /></span></td>
                                                        <td class="r"><span class="tx-money"><c:out value="${gms:formatMoney(shift.countedCash)}" /></span></td>
                                                        <td>
                                                            <div class="tx-tip"
                                                                 <c:if test="${shift.variance != 0 and not empty shift.varianceReason}">data-tip="Lý do: <c:out value='${shift.varianceReason}' />" tabindex="0"</c:if>>
                                                                <c:choose>
                                                                    <c:when test="${shift.variance == 0}"><span class="badge badge-success">Khớp</span></c:when>
                                                                    <c:when test="${shift.variance < 0}"><span class="badge badge-danger">Thiếu <c:out value="${gms:formatMoney(-shift.variance)}" /></span></c:when>
                                                                    <c:otherwise><span class="badge badge-info">Dư <c:out value="${gms:formatMoney(shift.variance)}" /></span></c:otherwise>
                                                                </c:choose>
                                                            </div>
                                                        </td>
                                                        <td class="r"><span class="tx-pay"><c:out value="${gms:formatMoney(shift.totalBankTransfer)}" /></span></td>
                                                        <td class="r"><span class="tx-pay"><c:out value="${shift.totalInvoiceCount}" /></span></td>
                                                    </tr>
                                                </c:forEach>
                                            </tbody>
                                        </table>
                                    </div>
                                </c:when>
                                <c:otherwise>
                                    <div class="tx-empty">Không có ca nào đã chốt trong khoảng ngày đã chọn.</div>
                                </c:otherwise>
                            </c:choose>

                            <c:url var="shiftBase" value="/manager/transactions">
                                <c:param name="tab" value="shifts" />
                                <c:param name="pageSize" value="${shiftPage.pageSize}" />
                                <c:if test="${not empty shiftFromDate}"><c:param name="from" value="${shiftFromDate}" /></c:if>
                                <c:if test="${not empty shiftToDate}"><c:param name="to" value="${shiftToDate}" /></c:if>
                            </c:url>
                            <div class="tx-foot">
                                <div class="tx-foot-meta">
                                    <span>Hiển thị</span>
                                    <select class="tx-select-sm" data-page-size-select aria-label="Số dòng mỗi trang">
                                        <option value="10" ${shiftPage.pageSize == 10 ? 'selected' : ''}>10 / trang</option>
                                        <option value="20" ${shiftPage.pageSize == 20 ? 'selected' : ''}>20 / trang</option>
                                        <option value="50" ${shiftPage.pageSize == 50 ? 'selected' : ''}>50 / trang</option>
                                    </select>
                                    <span>· Trang <b>${shiftPage.pageNumber}</b> / <b>${shiftPage.totalPages}</b> (${shiftPage.totalItems} ca)</span>
                                </div>
                                <span class="tx-foot-nav">
                                    <c:if test="${shiftPage.hasPrevious}">
                                        <a class="btn btn-ghost btn-sm" href="${shiftBase}&page=${shiftPage.pageNumber - 1}">&larr; Trang trước</a>
                                    </c:if>
                                    <c:if test="${shiftPage.hasNext}">
                                        <a class="btn btn-ghost btn-sm" href="${shiftBase}&page=${shiftPage.pageNumber + 1}">Trang sau &rarr;</a>
                                    </c:if>
                                </span>
                            </div>
                            </div>
                        </c:if>
                    </section>
                </main>
            </div>
        </div>

        <%-- =====================================================================
             POPUP 1: CHI TIẾT GIAO DỊCH
             ===================================================================== --%>
        <div class="tx-overlay" id="orderDetailModal" aria-hidden="true" role="dialog" aria-labelledby="txdTitle">
            <div class="tx-modal">
                <div class="tx-mh">
                    <div>
                        <h3><span id="txdTitle">---</span> <span id="txdBadge"></span></h3>
                        <p id="txdSub"></p>
                    </div>
                    <button type="button" class="tx-x" data-modal-close aria-label="Đóng">&times;</button>
                </div>

                <div class="tx-mb">
                    <div class="tx-callout" id="txdCallout" hidden></div>

                    <div class="tx-cards">
                        <div class="tx-card">
                            <h4 class="tx-h">Khách hàng</h4>
                            <div class="tx-kv"><span>Họ tên</span><b id="txdName">---</b></div>
                            <div class="tx-kv"><span>Điện thoại</span><span><b id="txdPhone">---</b> <a class="tx-zalo" id="txdZalo" target="_blank" rel="noopener" hidden>Zalo &#8599;</a></span></div>
                            <div class="tx-kv"><span>Email</span><b id="txdEmail">---</b></div>
                        </div>
                        <div class="tx-card">
                            <h4 class="tx-h">Gói đã mua</h4>
                            <div class="tx-kv"><span>Gói</span><b id="txdPackage">---</b></div>
                            <div class="tx-kv"><span>Hiệu lực</span><span id="txdPeriod">---</span></div>
                            <div class="tx-kv"><span>PT phụ trách</span><span id="txdPt">---</span></div>
                            <div class="tx-kv"><span>Giá gói</span><b id="txdPrice">---</b></div>
                            <div class="tx-kv"><span>Voucher</span><span id="txdVoucher">---</span></div>
                            <div class="tx-kv tx-total"><span id="txdTotalLabel">Tổng</span><b id="txdTotal">---</b></div>
                        </div>
                    </div>

                    <div>
                        <h4 class="tx-h">Lịch sử thanh toán</h4>
                        <div class="tx-table-wrap">
                            <table class="tx-mini">
                                <thead>
                                    <tr>
                                        <th>Lần</th>
                                        <th>Lúc</th>
                                        <th>Kết quả</th>
                                        <th class="r">Số tiền đơn</th>
                                        <th id="txdRefHead">Mã</th>
                                        <th class="r">Đối chiếu</th>
                                    </tr>
                                </thead>
                                <tbody id="txdAttempts"></tbody>
                            </table>
                        </div>

                        <%-- Kết quả tra cứu VNPay (API-06) --%>
                        <div class="tx-callout info tx-lookup" id="txdLookup" hidden></div>
                        <p class="tx-note" id="txdNote"></p>
                    </div>
                </div>

                <div class="tx-mf">
                    <button type="button" class="btn btn-ghost" data-modal-close>Đóng</button>
                    <button type="button" class="btn btn-primary" id="txdRefundBtn" hidden>Hoàn tiền</button>
                </div>
            </div>
        </div>

        <%-- =====================================================================
             POPUP 2: XÁC NHẬN ĐÃ HOÀN TIỀN (BR-47, BR-81)
             ===================================================================== --%>
        <div class="tx-overlay" id="refundConfirmModal" aria-hidden="true" role="dialog" aria-labelledby="txrTitle">
            <div class="tx-modal sm">
                <form id="refundConfirmForm" method="post" novalidate>
                    <input type="hidden" id="refundTransactionIdInput" name="transactionId" value="">

                    <div class="tx-mh">
                        <div>
                            <h3 id="txrTitle">Xác nhận đã hoàn tiền</h3>
                            <p>Chỉ bấm khi tiền đã chuyển cho khách. Thao tác không hoàn tác và được ghi nhật ký.</p>
                        </div>
                        <button type="button" class="tx-x" data-modal-close aria-label="Đóng">&times;</button>
                    </div>

                    <div class="tx-mb">
                        <div class="tx-kv"><span>Giao dịch</span><b id="txrOrder">---</b></div>
                        <div class="tx-kv"><span>Khách hàng</span><b id="txrCustomer">---</b></div>
                        <div class="tx-kv"><span>Số tiền đơn</span><b id="txrOrderAmount">---</b></div>
                        <div class="tx-kv">
                            <span>VNPay thực thu</span>
                            <span><b id="txrGatewayAmount">Chưa tra cứu</b> <button type="button" class="btn btn-outline btn-sm" id="txrLookupBtn">Tra cứu</button></span>
                        </div>

                        <div>
                            <h4 class="tx-h"><label for="refundAmountInput">Số tiền đã hoàn cho khách *</label></h4>
                            <div class="tx-money-in" id="txrAmountBox">
                                <input type="text" id="refundAmountInput" inputmode="numeric" autocomplete="off">
                                <span>đ</span>
                            </div>
                            <div class="tx-hint" id="txrAmountHint"><span>Mặc định là số tiền đơn. Tra cứu VNPay để lấy số thực thu.</span></div>
                            <div class="tx-err" id="refundAmountError" hidden></div>
                        </div>

                        <div>
                            <h4 class="tx-h"><label for="refundNoteInput">Ghi chú hoàn tiền *</label></h4>
                            <textarea class="tx-textarea" id="refundNoteInput" rows="3" maxlength="480"
                                      placeholder="VD: Đã hoàn qua cổng quản trị VNPay, mã giao dịch hoàn …"></textarea>
                            <div class="tx-hint"><span>Từ 5 đến 480 ký tự (số tiền tự ghi vào đầu ghi chú)</span><span id="txrNoteCount">0/480</span></div>
                            <div class="tx-err" id="refundNoteError" hidden></div>
                        </div>
                    </div>

                    <div class="tx-mf">
                        <button type="button" class="btn btn-ghost" data-modal-close>Hủy</button>
                        <button type="submit" class="btn btn-primary">Xác nhận đã hoàn tiền</button>
                    </div>
                </form>
            </div>
        </div>

    </body>
</html>
