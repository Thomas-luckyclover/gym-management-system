<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
  shift.jsp — Màn hình Ca làm việc & Đối soát tiền mặt tại quầy Lễ tân (#39, UC-25).
  Tuân thủ các luật:
  - CBR-104, CBR-105: Mỗi ngày có đúng 2 ca (Sáng & Chiều), lưu vết ca tại quầy.
  - DBR-55: ExpectedCash = Tổng tiền mặt các giao dịch thành công của đơn chưa hủy.
  - CBR-78: Chặn chốt ca nếu còn đơn Pending.
  - BR-46, GBR-09: Chốt ca bắt buộc đối soát tiền mặt, nhập lý do khi có chênh lệch.
  - CBR-11, CBR-115: Hủy đơn quầy trong ca mở, chặn tự hủy đơn của chính mình.
  - GBR-01, GBR-28: Định dạng ngày, giờ, tiền và màu sắc trạng thái.
--%>
<!DOCTYPE html>
<html lang="vi">

<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Ca làm việc · Super Gym Lễ tân</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/shift.css?v=20261021b">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
  <script src="${pageContext.request.contextPath}/js/pages/receptionist/shift.js?v=20261021b" defer></script>
  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
</head>

<body class="dock theme-staff">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Ca làm việc</h1>
        </div>
        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <c:choose>
            <c:when test="${detail.shift.open}">
              <span class="badge badge-ok">
                <i class="fa-solid fa-circle-check"></i> <c:out value="${detail.shift.shiftType.displayName}"/> đang mở
              </span>
            </c:when>
            <c:when test="${detail.shift.closed}">
              <span class="badge badge-muted">
                <i class="fa-solid fa-lock"></i> <c:out value="${detail.shift.shiftType.displayName}"/> đã chốt
              </span>
            </c:when>
            <c:otherwise>
              <span class="badge badge-warn">
                <i class="fa-solid fa-hourglass-start"></i> <c:out value="${detail.shift.shiftType.displayName}"/> chưa mở
              </span>
            </c:otherwise>
          </c:choose>

          <a class="tb-user" href="${pageContext.request.contextPath}/account" title="Cài đặt tài khoản">
            <span class="avatar">LT</span>
            <span class="tb-user-text">
              <b><c:out value="${sessionScope.displayName}" default="Lễ tân"/></b>
              <small>Lễ tân quầy</small>
            </span>
          </a>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">
        <c:if test="${not empty errorMessage}">
          <div class="alert-banner danger">
            <i class="fa-solid fa-circle-exclamation"></i>
            <span><c:out value="${errorMessage}"/></span>
          </div>
        </c:if>

        <%-- ===== KHỐI 1: HERO TRẠNG THÁI CA LÀM VIỆC ===== --%>
        <section class="status-hero" data-reveal>
          <div class="hero-info">
            <div class="hero-pill-row">
              <span class="section-label">
                <c:out value="${detail.shift.shiftType.displayName}"/> · <c:out value="${gms:formatDate(detail.shift.workDate)}"/>
              </span>
              <c:if test="${not empty detail.todayShifts}">
                <div class="shift-switch-tabs" role="tablist" aria-label="Chọn ca làm việc">
                  <c:forEach var="s" items="${detail.todayShifts}">
                    <a href="${pageContext.request.contextPath}/receptionist/shift?shiftId=${s.shiftId}"
                       class="shift-tab ${s.shiftId == detail.shift.shiftId ? 'active' : ''}">
                      <c:out value="${s.shiftType.displayName}"/>
                      <c:choose>
                        <c:when test="${s.open}"><span class="tab-dot dot-open" title="Đang mở"></span></c:when>
                        <c:when test="${s.closed}"><span class="tab-dot dot-closed" title="Đã chốt"></span></c:when>
                        <c:otherwise><span class="tab-dot dot-not-opened" title="Chưa mở"></span></c:otherwise>
                      </c:choose>
                    </a>
                  </c:forEach>
                </div>
              </c:if>
            </div>

            <c:choose>
              <c:when test="${detail.shift.open}">
                <h2>Đang mở từ <c:out value="${gms:formatDateTime(detail.shift.openedAt)}"/></h2>
                <p class="muted">
                  Mở bởi <b><c:out value="${detail.openedByEmployeeName}" default="Lễ tân"/></b>.
                  Mọi hóa đơn phát sinh tại quầy từ thời điểm này được tính vào ca trực.
                </p>
              </c:when>
              <c:when test="${detail.shift.closed}">
                <h2>Đã chốt lúc <c:out value="${gms:formatDateTime(detail.shift.closedAt)}"/></h2>
                <p class="muted">
                  Chốt bởi <b><c:out value="${detail.closedByEmployeeName}" default="Lễ tân"/></b>.
                  <c:choose>
                    <c:when test="${detail.shift.discrepancy == 0}">
                      Tiền mặt thực tế khớp 100% với số liệu sổ sách.
                    </c:when>
                    <c:when test="${detail.shift.discrepancy > 0}">
                      Lệch <b>thừa <c:out value="${gms:formatMoney(detail.shift.discrepancy)}"/></b>:
                      <em><c:out value="${detail.shift.varianceReason}"/></em>.
                    </c:when>
                    <c:otherwise>
                      Lệch <b>thiếu <c:out value="${gms:formatMoney(-detail.shift.discrepancy)}"/></b>:
                      <em><c:out value="${detail.shift.varianceReason}"/></em>.
                    </c:otherwise>
                  </c:choose>
                </p>
              </c:when>
              <c:otherwise>
                <h2>Ca trực chưa mở</h2>
                <p class="muted">
                  Bấm <b>Mở ca</b> để bắt đầu ca làm việc. Màn hình bán hàng tại quầy yêu cầu ca phải mở trước khi thanh toán.
                </p>
              </c:otherwise>
            </c:choose>
          </div>

          <div class="hero-actions">
            <c:if test="${detail.shift.notOpened}">
              <button class="btn btn-primary" type="button" id="btnOpenShiftModal">
                <i class="fa-solid fa-door-open"></i> Mở ca
              </button>
            </c:if>
            <c:if test="${detail.shift.open}">
              <button class="btn btn-primary" type="button" id="btnCloseShiftModal">
                <i class="fa-solid fa-cash-register"></i> Chốt ca
              </button>
            </c:if>
            <c:if test="${detail.shift.closed}">
              <span class="badge badge-muted badge-lg">
                <i class="fa-solid fa-circle-check"></i> Sổ ca đã chốt (chỉ xem)
              </span>
            </c:if>
          </div>
        </section>

        <%-- ===== KHỐI 2: CÁC THẺ KPI THỐNG KÊ DOANH THU ===== --%>
        <div class="kpi-grid" data-reveal>
          <div class="kpi-card">
            <span class="kpi-label"><i class="fa-solid fa-money-bill-wave"></i> Tiền mặt</span>
            <b class="kpi-value tabular"><c:out value="${gms:formatMoney(detail.metrics.cashTotal)}"/></b>
            <span class="kpi-foot"><c:out value="${detail.metrics.cashCount}"/> hóa đơn hợp lệ</span>
          </div>

          <div class="kpi-card">
            <span class="kpi-label"><i class="fa-solid fa-building-columns"></i> Chuyển khoản</span>
            <b class="kpi-value tabular"><c:out value="${gms:formatMoney(detail.metrics.bankTotal)}"/></b>
            <span class="kpi-foot"><c:out value="${detail.metrics.bankCount}"/> hóa đơn hợp lệ</span>
          </div>

          <div class="kpi-card">
            <span class="kpi-label"><i class="fa-solid fa-chart-line"></i> Tổng thu</span>
            <b class="kpi-value tabular"><c:out value="${gms:formatMoney(detail.metrics.totalRevenue)}"/></b>
            <span class="kpi-foot"><c:out value="${detail.metrics.totalValidCount}"/> hóa đơn hợp lệ</span>
          </div>

          <div class="kpi-card ${detail.metrics.pendingCount > 0 ? 'kpi-warn' : ''}">
            <span class="kpi-label"><i class="fa-solid fa-hourglass-half"></i> Đơn chờ xử lý</span>
            <b class="kpi-value tabular"><c:out value="${detail.metrics.pendingCount}"/></b>
            <span class="kpi-foot">
              <c:choose>
                <c:when test="${detail.metrics.pendingCount > 0}">Phải xử lý xong trước khi chốt ca</c:when>
                <c:otherwise>Không có đơn treo trong ca</c:otherwise>
              </c:choose>
            </span>
          </div>
        </div>

        <%-- ===== KHỐI 3: ALERT BANNER CẢNH BÁO ĐƠN CHUYỂN KHOẢN CHỜ XÁC NHẬN ===== --%>
        <c:if test="${detail.hasPendingOrders()}">
          <div class="alert-banner warn" id="pendBox" data-reveal>
            <div class="alert-icon"><i class="fa-solid fa-triangle-exclamation"></i></div>
            <div class="alert-text">
              <b>Còn <c:out value="${detail.pendingOrders.size()}"/> đơn hàng đang chờ xác nhận:</b>
              <c:forEach var="p" items="${detail.pendingOrders}" varStatus="status">
                <span class="pending-code"><c:out value="${p.orderCode}"/> (<c:out value="${p.customerName}"/>)</span><c:if test="${!status.last}">, </c:if>
              </c:forEach>
              <div>Vui lòng đối chiếu biến động số dư tài khoản ngân hàng để xác nhận hoặc hủy đơn có lý do trước khi chốt ca.</div>
            </div>
          </div>
        </c:if>

        <%-- ===== KHỐI 4: DANH SÁCH GIAO DỊCH TRONG CA ===== --%>
        <section class="panel" data-reveal>
          <div class="panel-head">
            <div>
              <h2>Giao dịch trong ca</h2>
              <p>Danh sách các đơn hàng phát sinh tại quầy thuộc ca trực này. Sắp xếp đơn mới nhất lên đầu.</p>
            </div>
          </div>

          <%-- Thanh tìm kiếm & lọc --%>
          <form class="toolbar" method="get" action="${pageContext.request.contextPath}/receptionist/shift">
            <input type="hidden" name="shiftId" value="${detail.shift.shiftId}">

            <div class="search-box">
              <i class="fa-solid fa-magnifying-glass search-icon"></i>
              <input id="txSearch" name="q" type="search" value="<c:out value='${detail.searchKeyword}'/>"
                     placeholder="Tìm theo mã đơn hoặc tên khách..." aria-label="Tìm giao dịch">
            </div>

            <div class="filter-group">
              <label for="filterMethod">Hình thức:</label>
              <select id="filterMethod" name="method" class="select-input">
                <option value="all" ${empty detail.methodFilter || detail.methodFilter == 'all' ? 'selected' : ''}>Tất cả</option>
                <option value="cash" ${detail.methodFilter == 'cash' ? 'selected' : ''}>Tiền mặt</option>
                <option value="bank" ${detail.methodFilter == 'bank' ? 'selected' : ''}>Chuyển khoản</option>
              </select>
            </div>

            <div class="filter-group">
              <label for="filterStatus">Trạng thái:</label>
              <select id="filterStatus" name="status" class="select-input">
                <option value="all" ${empty detail.statusFilter || detail.statusFilter == 'all' ? 'selected' : ''}>Tất cả</option>
                <option value="paid" ${detail.statusFilter == 'paid' ? 'selected' : ''}>Đã thanh toán (Hợp lệ)</option>
                <option value="pending" ${detail.statusFilter == 'pending' ? 'selected' : ''}>Chờ xác nhận</option>
                <option value="cancelled" ${detail.statusFilter == 'cancelled' ? 'selected' : ''}>Đã hủy</option>
              </select>
            </div>

            <button class="btn btn-primary btn-sm" type="submit">Lọc</button>
            <a class="btn btn-ghost btn-sm reset" href="${pageContext.request.contextPath}/receptionist/shift?shiftId=${detail.shift.shiftId}">Xóa lọc</a>
          </form>

          <%-- Danh sách giao dịch --%>
          <div class="data-list" id="txList">
            <c:choose>
              <c:when test="${empty detail.transactionsPage.items}">
                <div class="empty-state">
                  <div class="empty-icon"><i class="fa-solid fa-box-open"></i></div>
                  <p>Không có giao dịch nào khớp với bộ lọc.</p>
                </div>
              </c:when>
              <c:otherwise>
                <c:forEach var="item" items="${detail.transactionsPage.items}">
                  <div class="data-row ${item.cancelled ? 'is-void-row' : ''}">
                    <div class="row-main">
                      <div class="row-title">
                        <b><c:out value="${item.orderCode}"/></b>
                        <span class="sep">·</span>
                        <span><c:out value="${item.customerName}"/></span>
                        <c:if test="${not empty item.customerPhone}">
                          <small class="muted">(<c:out value="${item.customerPhone}"/>)</small>
                        </c:if>

                        <%-- Trạng thái đơn --%>
                        <c:choose>
                          <c:when test="${item.paid}">
                            <span class="badge badge-ok">Hợp lệ</span>
                          </c:when>
                          <c:when test="${item.pending}">
                            <span class="badge badge-warn">Chờ xác nhận</span>
                          </c:when>
                          <c:when test="${item.cancelled}">
                            <span class="badge badge-danger">Đã hủy</span>
                          </c:when>
                        </c:choose>
                      </div>

                      <div class="row-meta">
                        <span><i class="fa-solid fa-clock"></i> <c:out value="${gms:formatDateTime(item.createdAt)}"/></span>
                        <span><i class="fa-solid fa-dumbbell"></i> <c:out value="${item.packageName}"/></span>
                        <c:choose>
                          <c:when test="${item.cash}">
                            <span class="badge badge-muted"><i class="fa-solid fa-money-bill-wave"></i> Tiền mặt</span>
                          </c:when>
                          <c:otherwise>
                            <span class="badge badge-muted"><i class="fa-solid fa-building-columns"></i> Chuyển khoản</span>
                          </c:otherwise>
                        </c:choose>

                        <c:if test="${item.cancelled and not empty item.cancelReason}">
                          <span class="cancel-reason-text">
                            <i class="fa-solid fa-circle-info"></i> Lý do: <c:out value="${item.cancelReason}"/>
                          </span>
                        </c:if>
                      </div>
                    </div>

                    <div class="row-end">
                      <b class="tabular ${item.cancelled ? 'is-void' : ''}">
                        <c:out value="${gms:formatMoney(item.amount)}"/>
                      </b>

                      <div class="row-actions">
                        <%-- Nút xác nhận chuyển khoản cho đơn Pending --%>
                        <c:if test="${item.canConfirmTransfer}">
                          <form method="post" action="${pageContext.request.contextPath}/receptionist/shift" style="display:inline;">
                            <input type="hidden" name="action" value="confirmTransfer">
                            <input type="hidden" name="shiftId" value="${detail.shift.shiftId}">
                            <input type="hidden" name="orderId" value="${item.orderId}">
                            <button class="btn btn-outline btn-sm btn-confirm-pay" type="submit"
                                    data-confirm-title="Xác nhận chuyển khoản"
                                    data-confirm-message="Bạn đã kiểm tra biến động số dư và xác nhận nhận đủ tiền cho đơn ${item.orderCode}?">
                              <i class="fa-solid fa-check"></i> Đã nhận tiền
                            </button>
                          </form>
                        </c:if>

                        <%-- Nút hủy đơn quầy --%>
                        <c:if test="${item.canCancel}">
                          <button class="btn btn-ghost btn-sm btn-void-order" type="button"
                                  data-void-id="${item.orderId}"
                                  data-void-code="${item.orderCode}"
                                  data-void-amount="${gms:formatMoney(item.amount)}">
                            <i class="fa-solid fa-ban"></i> Hủy đơn
                          </button>
                        </c:if>
                      </div>
                    </div>
                  </div>
                </c:forEach>
              </c:otherwise>
            </c:choose>
          </div>

          <%-- Phân trang --%>
          <c:if test="${detail.transactionsPage.totalPages > 1}">
            <div class="pager">
              <div class="pager-info">
                Trang <c:out value="${detail.transactionsPage.page}"/> / <c:out value="${detail.transactionsPage.totalPages}"/>
                (<c:out value="${detail.transactionsPage.totalItems}"/> giao dịch)
              </div>
              <div class="pager-btns">
                <c:if test="${detail.transactionsPage.page > 1}">
                  <a class="btn btn-ghost btn-sm"
                     href="${pageContext.request.contextPath}/receptionist/shift?shiftId=${detail.shift.shiftId}&q=${detail.searchKeyword}&method=${detail.methodFilter}&status=${detail.statusFilter}&page=${detail.transactionsPage.page - 1}">
                    <i class="fa-solid fa-chevron-left"></i> Trước
                  </a>
                </c:if>

                <c:forEach var="p" begin="1" end="${detail.transactionsPage.totalPages}">
                  <c:if test="${p == detail.transactionsPage.page or (p >= detail.transactionsPage.page - 2 and p <= detail.transactionsPage.page + 2)}">
                    <a class="btn btn-sm ${p == detail.transactionsPage.page ? 'btn-primary' : 'btn-ghost'}"
                       href="${pageContext.request.contextPath}/receptionist/shift?shiftId=${detail.shift.shiftId}&q=${detail.searchKeyword}&method=${detail.methodFilter}&status=${detail.statusFilter}&page=${p}">
                      <c:out value="${p}"/>
                    </a>
                  </c:if>
                </c:forEach>

                <c:if test="${detail.transactionsPage.page < detail.transactionsPage.totalPages}">
                  <a class="btn btn-ghost btn-sm"
                     href="${pageContext.request.contextPath}/receptionist/shift?shiftId=${detail.shift.shiftId}&q=${detail.searchKeyword}&method=${detail.methodFilter}&status=${detail.statusFilter}&page=${detail.transactionsPage.page + 1}">
                    Sau <i class="fa-solid fa-chevron-right"></i>
                  </a>
                </c:if>
              </div>
            </div>
          </c:if>
        </section>
      </main>
    </div>
  </div>

  <%-- ===== MODAL 1: MỞ CA LÀM VIỆC ===== --%>
  <div class="modal" id="modalOpenShift" role="dialog" aria-modal="true" aria-labelledby="modalOpenTitle">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-head">
          <h2 class="modal-title" id="modalOpenTitle">Mở <c:out value="${detail.shift.shiftType.displayName}"/></h2>
          <button class="modal-close" type="button" data-close-modal aria-label="Đóng">&times;</button>
        </div>
        <form method="post" action="${pageContext.request.contextPath}/receptionist/shift">
          <input type="hidden" name="action" value="openShift">
          <input type="hidden" name="shiftId" value="${detail.shift.shiftId}">

          <div class="modal-body">
            <p>Bắt đầu ca trực vào thời điểm hiện tại. Mọi đơn hàng bán tại quầy từ thời điểm này sẽ được tự động ghi nhận vào ca trực này.</p>
            <div class="alert-banner info">
              <i class="fa-solid fa-circle-info"></i>
              <span>Nhân viên mở ca: <b><c:out value="${sessionScope.displayName}" default="Lễ tân"/></b></span>
            </div>
          </div>
          <div class="modal-foot">
            <button class="btn btn-ghost" type="button" data-close-modal>Bỏ qua</button>
            <button class="btn btn-primary" type="submit"><i class="fa-solid fa-door-open"></i> Xác nhận mở ca</button>
          </div>
        </form>
      </div>
    </div>
  </div>

  <%-- ===== MODAL 2: ĐỐI SOÁT & CHỐT CA LÀM VIỆC ===== --%>
  <div class="modal" id="modalCloseShift" role="dialog" aria-modal="true" aria-labelledby="modalCloseTitle">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-head">
          <h2 class="modal-title" id="modalCloseTitle">Chốt <c:out value="${detail.shift.shiftType.displayName}"/></h2>
          <button class="modal-close" type="button" data-close-modal aria-label="Đóng">&times;</button>
        </div>
        <form id="formCloseShift" method="post" action="${pageContext.request.contextPath}/receptionist/shift">
          <input type="hidden" name="action" value="closeShift">
          <input type="hidden" name="shiftId" value="${detail.shift.shiftId}">
          <input type="hidden" id="expectedCashRaw" value="${detail.metrics.cashTotal}">

          <div class="modal-body">
            <p class="modal-desc">Đếm số tiền mặt thực tế trong két quầy Lễ tân và nhập vào bên dưới. Nếu có chênh lệch so với số liệu sổ sách, bắt buộc phải nêu rõ lý do giải trình.</p>

            <div class="reconcile-card">
              <div class="reconcile-item">
                <span class="reconcile-label">Tiền mặt theo sổ sách</span>
                <b class="reconcile-val tabular" id="displayExpectedCash"><c:out value="${gms:formatMoney(detail.metrics.cashTotal)}"/></b>
              </div>
            </div>

            <div class="field">
              <label class="field-label" for="inputCountedCash">Tiền mặt thực tế đếm được (VND) <span class="required">*</span></label>
              <input id="inputCountedCash" name="countedCash" type="text" inputmode="numeric"
                     class="field-input tabular" placeholder="Ví dụ: 2.600.000" required
                     value="${detail.metrics.cashTotal}">
              <div class="field-error" id="countedCashError" hidden></div>
            </div>

            <%-- Khung hiển thị chênh lệch --%>
            <div class="variance-result" id="varianceBox">
              <span class="variance-label">Chênh lệch:</span>
              <b class="variance-value tabular" id="varianceVal">0đ</b>
              <span class="badge badge-ok" id="varianceBadge">Khớp sổ sách</span>
            </div>

            <div class="field" id="varianceReasonWrapper" style="display: none;">
              <label class="field-label" for="inputVarianceReason">Lý do chênh lệch <span class="required">*</span></label>
              <textarea id="inputVarianceReason" name="varianceReason" rows="3" class="field-textarea"
                        placeholder="Nêu rõ lý do thừa/thiếu tiền mặt (tối thiểu 5 ký tự)..."
                        minlength="5" maxlength="500"></textarea>
              <small class="field-hint">Bắt buộc nhập tối thiểu 5 ký tự để Quản lý đối soát.</small>
              <div class="field-error" id="varianceReasonError" hidden></div>
            </div>
          </div>

          <div class="modal-foot">
            <button class="btn btn-ghost" type="button" data-close-modal>Bỏ qua</button>
            <button class="btn btn-primary" type="submit" id="btnSubmitCloseShift">
              <i class="fa-solid fa-lock"></i> Xác nhận chốt ca
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>

  <%-- ===== MODAL 3: HỦY ĐƠN HÀNG TẠI QUẦY (VOID TRANSACTION) ===== --%>
  <div class="modal" id="modalVoidOrder" role="dialog" aria-modal="true" aria-labelledby="modalVoidTitle">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-head">
          <h2 class="modal-title" id="modalVoidTitle">Hủy đơn hàng <span id="voidOrderCodeText" class="text-primary"></span></h2>
          <button class="modal-close" type="button" data-close-modal aria-label="Đóng">&times;</button>
        </div>
        <form id="formVoidOrder" method="post" action="${pageContext.request.contextPath}/receptionist/shift">
          <input type="hidden" name="action" value="cancelOrder">
          <input type="hidden" name="shiftId" value="${detail.shift.shiftId}">
          <input type="hidden" name="orderId" id="voidOrderIdInput" value="">

          <div class="modal-body">
            <p>
              Đơn bị hủy vẫn được lưu trong sổ ca để đối chiếu nhưng sẽ được <b>loại bỏ khỏi tổng doanh thu</b>.
              Số tiền <b id="voidOrderAmountText"></b> sẽ được hoàn lại trực tiếp cho khách tại quầy.
            </p>

            <div class="field">
              <label class="field-label" for="inputCancelReason">Lý do hủy đơn <span class="required">*</span></label>
              <textarea id="inputCancelReason" name="cancelReason" rows="3" class="field-textarea"
                        placeholder="Ví dụ: Khách đổi ý ngay tại quầy, thu nhầm gói, quẹt lỗi..."
                        minlength="5" maxlength="500" required></textarea>
              <small class="field-hint">Bắt buộc giải trình cụ thể từ 5 đến 500 ký tự.</small>
              <div class="field-error" id="cancelReasonError" hidden></div>
            </div>
          </div>

          <div class="modal-foot">
            <button class="btn btn-ghost" type="button" data-close-modal>Bỏ qua</button>
            <button class="btn btn-danger" type="submit" id="btnSubmitVoidOrder">
              <i class="fa-solid fa-ban"></i> Xác nhận hủy đơn
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>

</body>
</html>
