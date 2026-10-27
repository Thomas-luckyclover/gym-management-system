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
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/transaction-list.css">

  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
  <script src="${pageContext.request.contextPath}/js/pages/manager/transaction-list.js" defer></script>
</head>

<body class="dock theme-manager">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Giao dịch toàn hệ thống</h1>
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

        <%-- ===== THANH CHUYỂN TAB & CẢNH BÁO HOÀN TIỀN ===== --%>
        <div class="trans-header-bar">
          <div class="trans-tabs-nav" role="tablist">
            <a href="${pageContext.request.contextPath}/manager/transactions?tab=orders"
               class="trans-tab-btn ${currentTab != 'shifts' ? 'active' : ''}">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="2" y="5" width="20" height="14" rx="2"></rect>
                <line x1="2" y1="10" x2="22" y2="10"></line>
              </svg>
              <span>Đơn hàng &amp; Giao dịch</span>
              <c:if test="${pendingRefundCount > 0}">
                <span class="trans-badge-counter badge-danger" title="${pendingRefundCount} giao dịch cần hoàn tiền">
                  ${pendingRefundCount}
                </span>
              </c:if>
            </a>

            <a href="${pageContext.request.contextPath}/manager/transactions?tab=shifts"
               class="trans-tab-btn ${currentTab == 'shifts' ? 'active' : ''}">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"></circle>
                <polyline points="12 6 12 12 16 14"></polyline>
              </svg>
              <span>Lịch sử chốt ca</span>
            </a>
          </div>
        </div>

        <%-- Dải thông báo nổi bật nếu có giao dịch cần hoàn tiền --%>
        <c:if test="${pendingRefundCount > 0}">
          <div class="refund-alert-banner">
            <div>
              <strong>Cần chú ý:</strong> Có <strong>${pendingRefundCount}</strong> giao dịch yêu cầu hoàn tiền ngoài quầy do hủy đơn.
            </div>
            <a href="${pageContext.request.contextPath}/manager/transactions?tab=orders&needsRefund=1"
               class="alert-action">
              Xem danh sách cần hoàn tiền &rarr;
            </a>
          </div>
        </c:if>

        <c:if test="${not empty pageError}">
          <div class="field-error" style="display: block; margin-bottom: var(--space-4);">
            <c:out value="${pageError}" />
          </div>
        </c:if>

        <%-- ===================================================================
             TAB 1: ĐƠN HÀNG & GIAO DỊCH
             =================================================================== --%>
        <c:if test="${currentTab != 'shifts'}">
          <section class="trans-panel">
            <%-- Bộ lọc tìm kiếm & Xuất Excel --%>
            <div class="trans-filter-bar">
              <form action="${pageContext.request.contextPath}/manager/transactions" method="get" class="trans-filter-form">
                <input type="hidden" name="tab" value="orders">

                <%-- Ô tìm kiếm --%>
                <div class="filter-group">
                  <label for="searchKeyword">Tìm kiếm</label>
                  <input type="search"
                         id="searchKeyword"
                         name="search"
                         value="<c:out value='${searchKeyword}' />"
                         placeholder="Mã đơn hoặc tên khách hàng..."
                         maxlength="100">
                </div>

                <%-- Phương thức thanh toán --%>
                <div class="filter-group">
                  <label for="filterMethod">Phương thức</label>
                  <select id="filterMethod" name="method">
                    <option value="" ${empty filterMethod ? 'selected' : ''}>Tất cả phương thức</option>
                    <option value="Cash" ${filterMethod == 'Cash' ? 'selected' : ''}>Tiền mặt</option>
                    <option value="Transfer" ${filterMethod == 'Transfer' ? 'selected' : ''}>Chuyển khoản</option>
                    <option value="OnlineGateway" ${filterMethod == 'OnlineGateway' ? 'selected' : ''}>Cổng VNPay</option>
                  </select>
                </div>

                <%-- Trạng thái đơn --%>
                <div class="filter-group">
                  <label for="filterStatus">Trạng thái đơn</label>
                  <select id="filterStatus" name="status">
                    <option value="" ${empty filterStatus ? 'selected' : ''}>Tất cả trạng thái</option>
                    <option value="Paid" ${filterStatus == 'Paid' ? 'selected' : ''}>Đã thanh toán</option>
                    <option value="Pending" ${filterStatus == 'Pending' ? 'selected' : ''}>Chờ thanh toán</option>
                    <option value="Cancelled" ${filterStatus == 'Cancelled' ? 'selected' : ''}>Đã hủy</option>
                  </select>
                </div>

                <%-- Từ ngày - Đến ngày --%>
                <div class="filter-group">
                  <label for="fromDate">Từ ngày</label>
                  <input type="date" id="fromDate" name="from" value="${fromDate}">
                </div>

                <div class="filter-group">
                  <label for="toDate">Đến ngày</label>
                  <input type="date" id="toDate" name="to" value="${toDate}">
                </div>

                <%-- Checkbox chỉ lọc đơn cần hoàn tiền --%>
                <label class="filter-group-checkbox" for="filterNeedsRefundCb" title="Lọc các giao dịch đơn bị hủy cần hoàn tiền cho khách">
                  <input type="checkbox" id="filterNeedsRefundCb" name="needsRefund" value="1" ${filterNeedsRefund == 1 ? 'checked' : ''}>
                  <span>Cần hoàn tiền</span>
                </label>

                <div class="filter-actions">
                  <button type="submit" class="btn btn-primary btn-sm">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="11" cy="11" r="8"></circle>
                      <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                    </svg>
                    <span>Lọc</span>
                  </button>

                  <a href="${pageContext.request.contextPath}/manager/transactions?tab=orders" class="btn btn-ghost btn-sm">
                    Xóa lọc
                  </a>

                  <%-- Nút Xuất Excel --%>
                  <c:url var="exportUrl" value="/manager/transactions">
                    <c:param name="action" value="export" />
                    <c:if test="${not empty searchKeyword}"><c:param name="search" value="${searchKeyword}" /></c:if>
                    <c:if test="${not empty filterMethod}"><c:param name="method" value="${filterMethod}" /></c:if>
                    <c:if test="${not empty filterStatus}"><c:param name="status" value="${filterStatus}" /></c:if>
                    <c:if test="${filterNeedsRefund == 1}"><c:param name="needsRefund" value="1" /></c:if>
                    <c:if test="${not empty fromDate}"><c:param name="from" value="${fromDate}" /></c:if>
                    <c:if test="${not empty toDate}"><c:param name="to" value="${toDate}" /></c:if>
                  </c:url>

                  <a href="${exportUrl}" class="btn btn-export-excel btn-sm" title="Tải về danh sách giao dịch định dạng Excel (.xlsx)">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                      <polyline points="7 10 12 15 17 10"></polyline>
                      <line x1="12" y1="15" x2="12" y2="3"></line>
                    </svg>
                    <span>Xuất Excel</span>
                  </a>
                </div>
              </form>
            </div>

            <%-- Bảng danh sách Đơn hàng & Giao dịch --%>
            <div class="table-responsive">
              <c:choose>
                <c:when test="${not empty orderPage.items}">
                  <table class="trans-table">
                    <thead>
                      <tr>
                        <th>Mã đơn / Kênh</th>
                        <th>Thời gian</th>
                        <th>Khách hàng</th>
                        <th>Gói tập &amp; Voucher</th>
                        <th class="text-right">Tổng tiền</th>
                        <th>Phương thức</th>
                        <th>Trạng thái đơn</th>
                        <th>Hoàn tiền</th>
                        <th class="text-center">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      <c:forEach var="item" items="${orderPage.items}">
                        <tr>
                          <%-- Mã đơn & Kênh --%>
                          <td>
                            <div class="col-code"><c:out value="${item.orderCode}" /></div>
                            <c:choose>
                              <c:when test="${item.channel == 'Online'}">
                                <span class="badge-channel badge-channel-online">Trực tuyến</span>
                              </c:when>
                              <c:otherwise>
                                <span class="badge-channel badge-channel-counter">Tại quầy</span>
                              </c:otherwise>
                            </c:choose>
                          </td>

                          <%-- Thời gian --%>
                          <td>
                            <div><c:out value="${gms:formatDateTime(item.createdAt)}" /></div>
                          </td>

                          <%-- Khách hàng --%>
                          <td>
                            <div class="col-customer">
                              <span class="customer-name"><c:out value="${item.customerName}" default="Khách lẻ" /></span>
                              <span class="customer-phone"><c:out value="${item.customerPhone}" default="---" /></span>
                            </div>
                          </td>

                          <%-- Gói tập & Voucher --%>
                          <td class="col-package">
                            <span class="package-name" title="<c:out value='${item.packageName}' />">
                              <c:out value="${item.packageName}" default="---" />
                            </span>
                            <c:if test="${not empty item.voucherCode}">
                              <span class="package-voucher">
                                Voucher: <b><c:out value="${item.voucherCode}" /></b>
                              </span>
                            </c:if>
                          </td>

                          <%-- Tổng tiền --%>
                          <td class="money font-weight-bold">
                            <c:out value="${gms:formatMoney(item.totalAmount)}" />
                          </td>

                          <%-- Phương thức --%>
                          <td>
                            <c:choose>
                              <c:when test="${item.paymentMethod == 'Cash'}">
                                <span class="badge-method badge-method-cash">Tiền mặt</span>
                              </c:when>
                              <c:when test="${item.paymentMethod == 'Transfer'}">
                                <span class="badge-method badge-method-transfer">Chuyển khoản</span>
                              </c:when>
                              <c:when test="${item.paymentMethod == 'OnlineGateway'}">
                                <span class="badge-method badge-method-vnpay">VNPay</span>
                              </c:when>
                              <c:otherwise>
                                <span class="badge-method">---</span>
                              </c:otherwise>
                            </c:choose>
                          </td>

                          <%-- Trạng thái đơn --%>
                          <td>
                            <c:choose>
                              <c:when test="${item.orderStatus == 'Paid'}">
                                <span class="badge badge-success">Đã thanh toán</span>
                              </c:when>
                              <c:when test="${item.orderStatus == 'Pending'}">
                                <span class="badge badge-warning">Chờ thanh toán</span>
                              </c:when>
                              <c:when test="${item.orderStatus == 'Cancelled'}">
                                <span class="badge badge-danger">Đã hủy</span>
                              </c:when>
                              <c:otherwise>
                                <span class="badge badge-neutral"><c:out value="${item.orderStatus}" /></span>
                              </c:otherwise>
                            </c:choose>
                          </td>

                          <%-- Trạng thái hoàn tiền (CBR-72) --%>
                          <td>
                            <c:choose>
                              <c:when test="${item.needsRefund and not empty item.refundedAt}">
                                <span class="badge-refund-tag completed" title="Đã hoàn bởi: <c:out value='${item.refundedByName}' /> vào <c:out value='${gms:formatDateTime(item.refundedAt)}' />. Ghi chú: <c:out value='${item.refundNote}' />">
                                  Đã hoàn tiền
                                </span>
                              </c:when>
                              <c:when test="${item.needsRefund}">
                                <span class="badge-refund-tag pending">Cần hoàn tiền</span>
                              </c:when>
                              <c:otherwise>
                                <span style="color: var(--color-text-muted); font-size: var(--font-size-xs);">---</span>
                              </c:otherwise>
                            </c:choose>
                          </td>

                          <%-- Thao tác --%>
                          <td>
                            <div class="action-cell">
                              <button type="button"
                                      class="btn btn-outline btn-sm btn-view-detail"
                                      data-order-id="${item.orderId}"
                                      title="Xem chi tiết đơn hàng & lịch sử giao dịch">
                                Chi tiết
                              </button>

                              <c:if test="${item.needsRefund and empty item.refundedAt}">
                                <button type="button"
                                        class="btn btn-danger btn-sm btn-table-refund"
                                        data-transaction-id="${item.refundableTransactionId}"
                                        data-order-code="<c:out value='${item.orderCode}' />"
                                        data-amount="${item.totalAmount}"
                                        data-customer="<c:out value='${item.customerName}' />"
                                        title="Xác nhận hoàn tiền ngoài quầy cho khách">
                                  Hoàn tiền
                                </button>
                              </c:if>
                            </div>
                          </td>
                        </tr>
                      </c:forEach>
                    </tbody>
                  </table>
                </c:when>
                <c:otherwise>
                  <div class="empty-state">
                    Không tìm thấy đơn hàng hoặc giao dịch nào phù hợp với bộ lọc.
                  </div>
                </c:otherwise>
              </c:choose>
            </div>

            <%-- Phân trang Tab 1 --%>
            <c:if test="${orderPage.totalPages > 1}">
              <div class="trans-pagination">
                <div>
                  Hiển thị trang <b>${orderPage.pageNumber}</b> / <b>${orderPage.totalPages}</b> (Tổng <b>${orderPage.totalItems}</b> giao dịch)
                </div>

                <div class="pagination-controls">
                  <c:if test="${orderPage.hasPrevious}">
                    <c:url var="prevUrl" value="/manager/transactions">
                      <c:param name="tab" value="orders" />
                      <c:param name="page" value="${orderPage.pageNumber - 1}" />
                      <c:param name="pageSize" value="${orderPage.pageSize}" />
                      <c:if test="${not empty searchKeyword}"><c:param name="search" value="${searchKeyword}" /></c:if>
                      <c:if test="${not empty filterMethod}"><c:param name="method" value="${filterMethod}" /></c:if>
                      <c:if test="${not empty filterStatus}"><c:param name="status" value="${filterStatus}" /></c:if>
                      <c:if test="${filterNeedsRefund == 1}"><c:param name="needsRefund" value="1" /></c:if>
                      <c:if test="${not empty fromDate}"><c:param name="from" value="${fromDate}" /></c:if>
                      <c:if test="${not empty toDate}"><c:param name="to" value="${toDate}" /></c:if>
                    </c:url>
                    <a href="${prevUrl}" class="btn btn-ghost btn-sm">&larr; Trang trước</a>
                  </c:if>

                  <c:if test="${orderPage.hasNext}">
                    <c:url var="nextUrl" value="/manager/transactions">
                      <c:param name="tab" value="orders" />
                      <c:param name="page" value="${orderPage.pageNumber + 1}" />
                      <c:param name="pageSize" value="${orderPage.pageSize}" />
                      <c:if test="${not empty searchKeyword}"><c:param name="search" value="${searchKeyword}" /></c:if>
                      <c:if test="${not empty filterMethod}"><c:param name="method" value="${filterMethod}" /></c:if>
                      <c:if test="${not empty filterStatus}"><c:param name="status" value="${filterStatus}" /></c:if>
                      <c:if test="${filterNeedsRefund == 1}"><c:param name="needsRefund" value="1" /></c:if>
                      <c:if test="${not empty fromDate}"><c:param name="from" value="${fromDate}" /></c:if>
                      <c:if test="${not empty toDate}"><c:param name="to" value="${toDate}" /></c:if>
                    </c:url>
                    <a href="${nextUrl}" class="btn btn-ghost btn-sm">Trang sau &rarr;</a>
                  </c:if>
                </div>
              </div>
            </c:if>
          </section>
        </c:if>

        <%-- ===================================================================
             TAB 2: LỊCH SỬ CHỐT CA LÀM VIỆC (Closed Shift History)
             =================================================================== --%>
        <c:if test="${currentTab == 'shifts'}">
          <section class="trans-panel">
            <%-- Bộ lọc khoảng ngày ca --%>
            <div class="trans-filter-bar">
              <form action="${pageContext.request.contextPath}/manager/transactions" method="get" class="trans-filter-form">
                <input type="hidden" name="tab" value="shifts">

                <div class="filter-group">
                  <label for="shiftFromDate">Từ ngày</label>
                  <input type="date" id="shiftFromDate" name="from" value="${shiftFromDate}">
                </div>

                <div class="filter-group">
                  <label for="shiftToDate">Đến ngày</label>
                  <input type="date" id="shiftToDate" name="to" value="${shiftToDate}">
                </div>

                <div class="filter-actions">
                  <button type="submit" class="btn btn-primary btn-sm">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="11" cy="11" r="8"></circle>
                      <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                    </svg>
                    <span>Xem lịch sử ca</span>
                  </button>

                  <a href="${pageContext.request.contextPath}/manager/transactions?tab=shifts" class="btn btn-ghost btn-sm">
                    Xóa lọc
                  </a>
                </div>
              </form>
            </div>

            <%-- Bảng danh sách Ca đã đóng --%>
            <div class="table-responsive">
              <c:choose>
                <c:when test="${not empty shiftPage.items}">
                  <table class="trans-table">
                    <thead>
                      <tr>
                        <th>Ngày ca</th>
                        <th>Loại ca</th>
                        <th>Người mở</th>
                        <th>Người chốt</th>
                        <th>Giờ mở &mdash; đóng</th>
                        <th class="text-right">Tiền mặt kiểm đếm</th>
                        <th class="text-right">Tiền mặt dự tính</th>
                        <th class="text-right">Chênh lệch tiền</th>
                        <th class="text-right">Chuyển khoản</th>
                        <th class="text-center">Số hóa đơn</th>
                      </tr>
                    </thead>
                    <tbody>
                      <c:forEach var="shift" items="${shiftPage.items}">
                        <tr>
                          <%-- Ngày ca --%>
                          <td><b><c:out value="${gms:formatDate(shift.workDate)}" /></b></td>

                          <%-- Loại ca --%>
                          <td>
                            <c:choose>
                              <c:when test="${shift.shiftType == 'Morning'}">Ca sáng</c:when>
                              <c:when test="${shift.shiftType == 'Afternoon'}">Ca chiều</c:when>
                              <c:when test="${shift.shiftType == 'Evening'}">Ca tối</c:when>
                              <c:otherwise><c:out value="${shift.shiftType}" /></c:otherwise>
                            </c:choose>
                          </td>

                          <%-- Người mở ca --%>
                          <td><c:out value="${shift.openedByName}" default="---" /></td>

                          <%-- Người chốt ca --%>
                          <td><c:out value="${shift.closedByName}" default="---" /></td>

                          <%-- Giờ mở - đóng --%>
                          <td style="font-size: var(--font-size-xs); color: var(--color-text-soft);">
                            <c:out value="${gms:formatDateTime(shift.openedAt)}" />
                            <br>&rarr; <c:out value="${gms:formatDateTime(shift.closedAt)}" />
                          </td>

                          <%-- Tiền mặt đếm thực tế (CountedCash) --%>
                          <td class="money font-weight-bold">
                            <c:out value="${gms:formatMoney(shift.countedCash)}" />
                          </td>

                          <%-- Tiền mặt dự tính (ExpectedCash) --%>
                          <td class="money">
                            <c:out value="${gms:formatMoney(shift.expectedCash)}" />
                          </td>

                          <%-- Chênh lệch tiền mặt (Variance) --%>
                          <td class="money">
                            <c:choose>
                              <c:when test="${shift.variance == 0}">
                                <span class="variance-zero">Khớp (0đ)</span>
                              </c:when>
                              <c:when test="${shift.variance < 0}">
                                <span class="variance-negative">Thiếu ${gms:formatMoney(shift.variance)}</span>
                              </c:when>
                              <c:otherwise>
                                <span class="variance-positive">+${gms:formatMoney(shift.variance)}</span>
                              </c:otherwise>
                            </c:choose>

                            <c:if test="${not empty shift.varianceReason}">
                              <div style="font-size: 11px; color: var(--color-text-soft); font-style: italic; max-width: 140px; margin-left: auto;"
                                   title="<c:out value='${shift.varianceReason}' />">
                                Lý do: <c:out value="${shift.varianceReason}" />
                              </div>
                            </c:if>
                          </td>

                          <%-- Chuyển khoản trong ca --%>
                          <td class="money">
                            <c:out value="${gms:formatMoney(shift.totalBankTransfer)}" />
                          </td>

                          <%-- Số hóa đơn --%>
                          <td class="text-center font-weight-bold">
                            <c:out value="${shift.totalInvoiceCount}" />
                          </td>
                        </tr>
                      </c:forEach>
                    </tbody>
                  </table>
                </c:when>
                <c:otherwise>
                  <div class="empty-state">
                    Không có ca làm việc nào đã đóng trong khoảng thời gian đã chọn.
                  </div>
                </c:otherwise>
              </c:choose>
            </div>

            <%-- Phân trang Tab 2 --%>
            <c:if test="${shiftPage.totalPages > 1}">
              <div class="trans-pagination">
                <div>
                  Hiển thị trang <b>${shiftPage.pageNumber}</b> / <b>${shiftPage.totalPages}</b> (Tổng <b>${shiftPage.totalItems}</b> ca làm việc)
                </div>

                <div class="pagination-controls">
                  <c:if test="${shiftPage.hasPrevious}">
                    <c:url var="prevShiftUrl" value="/manager/transactions">
                      <c:param name="tab" value="shifts" />
                      <c:param name="page" value="${shiftPage.pageNumber - 1}" />
                      <c:param name="pageSize" value="${shiftPage.pageSize}" />
                      <c:if test="${not empty shiftFromDate}"><c:param name="from" value="${shiftFromDate}" /></c:if>
                      <c:if test="${not empty shiftToDate}"><c:param name="to" value="${shiftToDate}" /></c:if>
                    </c:url>
                    <a href="${prevShiftUrl}" class="btn btn-ghost btn-sm">&larr; Trang trước</a>
                  </c:if>

                  <c:if test="${shiftPage.hasNext}">
                    <c:url var="nextShiftUrl" value="/manager/transactions">
                      <c:param name="tab" value="shifts" />
                      <c:param name="page" value="${shiftPage.pageNumber + 1}" />
                      <c:param name="pageSize" value="${shiftPage.pageSize}" />
                      <c:if test="${not empty shiftFromDate}"><c:param name="from" value="${shiftFromDate}" /></c:if>
                      <c:if test="${not empty shiftToDate}"><c:param name="to" value="${shiftToDate}" /></c:if>
                    </c:url>
                    <a href="${nextShiftUrl}" class="btn btn-ghost btn-sm">Trang sau &rarr;</a>
                  </c:if>
                </div>
              </div>
            </c:if>
          </section>
        </c:if>

      </main>
    </div>
  </div>

  <%-- =====================================================================
       MODAL 1: CHI TIẾT ĐƠN HÀNG & LỊCH SỬ CÁC LẦN GIAO DỊCH
       ===================================================================== --%>
  <div class="modal-overlay" id="orderDetailModal" aria-hidden="true" role="dialog" aria-labelledby="modalDetailTitle">
    <div class="trans-modal-box">
      <div class="trans-modal-header">
        <div>
          <h3 id="modalDetailTitle">Chi tiết đơn hàng #<span id="modalOrderCode">---</span></h3>
        </div>
        <button type="button" class="trans-modal-close" aria-label="Đóng">&times;</button>
      </div>

      <div class="trans-modal-body">
        <%-- Lưới thông tin đơn hàng --%>
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">Kênh bán hàng</span>
            <span class="detail-value" id="modalChannelBadge">---</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Trạng thái</span>
            <span class="detail-value" id="modalStatusBadge">---</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Thời gian tạo</span>
            <span class="detail-value" id="modalCreatedAt">---</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Nhân viên / Ca</span>
            <span class="detail-value" id="modalStaffInfo">---</span>
          </div>
        </div>

        <hr style="border: 0; border-top: 1px solid var(--color-border); margin: 0;">

        <%-- Thông tin khách hàng & Gói tập --%>
        <div class="detail-grid">
          <div class="detail-item">
            <span class="detail-label">Họ tên khách hàng</span>
            <span class="detail-value" id="modalCustomerName">---</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Số điện thoại</span>
            <span class="detail-value" id="modalCustomerPhone">---</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Gói đăng ký</span>
            <span class="detail-value" id="modalPackageName">---</span>
          </div>
          <div class="detail-item">
            <span class="detail-label">Voucher giảm giá</span>
            <span class="detail-value" id="modalVoucherCode">---</span>
          </div>
        </div>

        <%-- Bảng giá chi tiết --%>
        <div class="price-breakdown-card">
          <div class="price-row">
            <span>Đơn giá gói</span>
            <span id="modalPackagePrice">0đ</span>
          </div>
          <div class="price-row">
            <span>Giảm trừ Voucher</span>
            <span id="modalDiscountAmount" style="color: var(--color-success);">-0đ</span>
          </div>
          <div class="price-row">
            <span>Trước thuế (Pre-tax)</span>
            <span id="modalPreTaxAmount">0đ</span>
          </div>
          <div class="price-row">
            <span>Thuế GTGT (VAT)</span>
            <span id="modalVatAmount">0đ</span>
          </div>
          <div class="price-row total">
            <span>Tổng thanh toán</span>
            <span id="modalTotalAmount">0đ</span>
          </div>
        </div>

        <%-- Danh sách các lần giao dịch / thanh toán --%>
        <div>
          <span class="detail-label" style="display: block; margin-bottom: var(--space-2);">
            Lịch sử các lần giao dịch (Transactions)
          </span>
          <div style="overflow-x: auto; border: 1px solid var(--color-border); border-radius: var(--radius-md);">
            <table class="attempts-table">
              <thead>
                <tr>
                  <th>Mã GD</th>
                  <th>Phương thức</th>
                  <th>Trạng thái</th>
                  <th class="text-right">Số tiền</th>
                  <th>Mã đối soát</th>
                  <th>Thời điểm</th>
                  <th>Hoàn tiền</th>
                </tr>
              </thead>
              <tbody id="modalAttemptsBody">
                <%-- Điền bằng JS --%>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="trans-modal-footer">
        <button type="button" class="btn btn-ghost" data-modal-close>Đóng</button>
      </div>
    </div>
  </div>

  <%-- =====================================================================
       MODAL 2: XÁC NHẬN HOÀN TIỀN NGOÀI HỆ THỐNG (BR-47, CBR-72)
       ===================================================================== --%>
  <div class="modal-overlay" id="refundConfirmModal" aria-hidden="true" role="dialog" aria-labelledby="modalRefundTitle">
    <div class="trans-modal-box" style="max-width: 500px;">
      <form id="refundConfirmForm" method="post">
        <input type="hidden" id="refundTransactionIdInput" name="transactionId" value="">

        <div class="trans-modal-header">
          <h3 id="modalRefundTitle">Xác nhận hoàn tiền ngoài hệ thống</h3>
          <button type="button" class="trans-modal-close" aria-label="Đóng">&times;</button>
        </div>

        <div class="trans-modal-body">
          <p style="font-size: var(--font-size-sm); color: var(--color-text-soft); line-height: 1.5;">
            Thao tác này xác nhận rằng bạn đã thực hiện hoàn trả tiền mặt hoặc chuyển khoản trực tiếp cho khách hàng ngoài quầy do đơn hàng bị hủy (BR-47).
          </p>

          <div class="price-breakdown-card">
            <div class="price-row">
              <span>Mã đơn hàng:</span>
              <b id="refundOrderCodeText">---</b>
            </div>
            <div class="price-row">
              <span>Khách hàng:</span>
              <span id="refundCustomerText">---</span>
            </div>
            <div class="price-row total" style="color: var(--color-danger);">
              <span>Số tiền cần hoàn:</span>
              <span id="refundAmountText">0đ</span>
            </div>
          </div>

          <div class="filter-group">
            <label for="refundNoteInput">Ghi chú xác nhận hoàn tiền <span style="color: var(--color-danger);">*</span></label>
            <textarea id="refundNoteInput"
                      name="refundNote"
                      rows="3"
                      class="form-control"
                      placeholder="Nhập phương thức hoàn trả (tiền mặt/STK người nhận, ngày giờ, số tham chiếu...)"
                      maxlength="500"
                      required></textarea>
            <div id="refundNoteError" class="field-error" hidden></div>
          </div>
        </div>

        <div class="trans-modal-footer">
          <button type="button" class="btn btn-ghost" data-modal-close>Hủy bỏ</button>
          <button type="submit" class="btn btn-danger">Xác nhận đã hoàn tiền</button>
        </div>
      </form>
    </div>
  </div>

</body>
</html>
