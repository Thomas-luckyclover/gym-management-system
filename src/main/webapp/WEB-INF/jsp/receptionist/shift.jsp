<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<%--
================================================================================
  shift.jsp — Màn hình Ca làm việc & Đối soát tiền mặt tại quầy Lễ tân (#39, UC-25).

  Luật nghiệp vụ áp dụng:
  - CBR-104, CBR-105: Mỗi ngày có đúng 2 ca (Sáng & Chiều), lưu vết ca tại quầy.
  - DBR-55: ExpectedCash = Tổng tiền mặt các giao dịch thành công của đơn chưa hủy.
  - CBR-78: Chặn chốt ca nếu còn đơn Pending.
  - BR-46, GBR-09: Chốt ca bắt buộc đối soát tiền mặt, nhập lý do khi có chênh lệch.
  - CBR-11, CBR-115: Hủy đơn quầy trong ca mở, chặn tự hủy đơn của chính mình.
  - GBR-01, GBR-28: Định dạng ngày, giờ, tiền và màu sắc trạng thái.

  Dữ liệu từ ShiftServlet (doGet) & kết nối JS:
  - detail (ShiftDetailView): dữ liệu ca đang xem, tab ca hôm nay, KPI, đơn chờ và danh sách giao dịch.
  - errorMessage: thông báo lỗi cấp trang khi không tải được dữ liệu ca làm việc.
  - flash-toast.jspf: hiển thị kết quả thành công/lỗi sau khi POST (openShift / closeShift / cancelOrder).
  - shift.js: mở/đóng 3 modal, tính chênh lệch tiền mặt realtime, validate lý do lệch tiền & hủy đơn.
================================================================================
--%>
<!DOCTYPE html>
<html lang="vi">

<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Ca làm việc · Super Gym Lễ tân</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/shift.css?v=20261113">
  <script src="${pageContext.request.contextPath}/js/pages/receptionist/shift.js?v=20261021b" defer></script>
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
          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">
        <%-- Thông báo lỗi cấp trang khi ShiftServlet.doGet không tải được dữ liệu ca --%>
        <c:if test="${not empty errorMessage}">
          <div class="alert-banner danger">
            <i class="fa-solid fa-circle-exclamation"></i>
            <span><c:out value="${errorMessage}"/></span>
          </div>
        </c:if>

        <c:if test="${not empty detail}">
          <%-- ===== BANNER CẢNH BÁO CA QUÁ KHỨ CHƯA CHỐT SỔ (CBR-123) ===== --%>
          <c:if test="${detail.unclosedPastShift}">
            <div class="alert-banner danger" data-reveal style="margin-bottom: 16px;">
              <div class="alert-icon"><i class="fa-solid fa-triangle-exclamation"></i></div>
              <div class="alert-text">
                <b>CẢNH BÁO: Ca trực ngày <c:out value="${gms:formatDate(detail.shift.workDate)}"/> chưa được chốt ca!</b>
                <div>Ca làm việc này đã bị để quên qua đêm. Vui lòng kiểm kê tiền mặt thực tế trong két và bấm <b>Chốt ca</b> ngay trước khi có thể mở ca ngày mới.</div>
              </div>
            </div>
          </c:if>

          <%-- ===== BANNER CẢNH BÁO CA SÁNG QUÁ GIỜ (13:00 - AFTERNOON_SHIFT_START) ===== --%>
          <c:if test="${detail.overdue}">
            <div class="alert-banner warn" data-reveal style="margin-bottom: 16px;">
              <div class="alert-icon"><i class="fa-solid fa-clock"></i></div>
              <div class="alert-text">
                <b>Đã quá thời gian ca sáng (kết thúc lúc 13:00):</b>
                <div>Vui lòng kiểm kê tiền trong két, xử lý dứt điểm các đơn hàng và bấm <b>Chốt ca</b> để bàn giao cho Ca chiều.</div>
              </div>
            </div>
          </c:if>

          <%-- ===== KHỐI 1: HERO TRẠNG THÁI CA LÀM VIỆC (detail.shift, detail.todayShifts, detail.canOpen) ===== --%>
          <section class="status-hero" data-reveal>
            <%-- Cột trái: nhãn ca, tab chuyển ca hôm nay và trạng thái chi tiết của ca đang xem --%>
            <div class="hero-info">
              <div class="hero-pill-row">
                <span class="section-label">
                  <c:out value="${detail.shift.shiftType.displayName}"/> · <c:out value="${gms:formatDate(detail.shift.workDate)}"/>
                  <%-- vd: Ca chiều · 12/11/2026 --%>
                </span>

                <%-- Tab chuyển nhanh giữa Ca sáng / Ca chiều trong cùng ngày hôm nay --%>
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
                      <%-- vd: Ca sáng | Ca chiều --%>
                    </c:forEach>
                  </div>
                </c:if>
              </div>

              <%-- Nội dung mô tả theo 4 nhánh trạng thái của ca (Open / Closed / NotOpened+pastShift / NotOpened) --%>
              <c:choose>
                <%-- Nhánh 1: Ca đang mở --%>
                <c:when test="${detail.shift.open}">
                  <h2>Đang mở từ <c:out value="${gms:formatDateTime(detail.shift.openedAt)}"/></h2>
                  <p class="muted">
                    Mở bởi <b><c:out value="${detail.openedByEmployeeName}" default="Lễ tân"/></b>.
                    Mọi giao dịch phát sinh tại quầy từ thời điểm này được tính vào ca trực.
                  </p>
                </c:when>

                <%-- Nhánh 2: Ca đã chốt (hiển thị kết quả đối soát tiền mặt & nút quay về hôm nay nếu đang xem ca cũ) --%>
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
                  <%-- detail.pastDate = getter tự tính isPastDate() · dto/ShiftDetailView.java --%>
                  <c:if test="${detail.pastDate}"> <%-- Trường hợp sửa id về ca quá khứ --%>
                    <div style="margin-top: 10px;">
                      <a href="${pageContext.request.contextPath}/receptionist/shift"
                         class="btn btn-primary btn-sm"
                         style="display: inline-flex; align-items: center; gap: 6px;">
                        <i class="fa-solid fa-calendar-day"></i> Chuyển sang ca làm việc hôm nay
                      </a>
                    </div>
                  </c:if>
                </c:when>

                <%-- Nhánh 3: Ca đã qua chưa mở --%>
                <c:when test="${detail.shift.notOpened and detail.pastShift}">
                  <%-- Ca chưa từng mở và đã qua thời gian, không thể mở lại (pastShift = true). Các trường hợp:
                       1. Ca thuộc ngày trong quá khứ (vd: sửa shiftId về ca hôm qua chưa mở)
                       2. Ca sáng hôm nay, nhưng ca chiều hôm nay đã chốt
                       3. Ca sáng hôm nay, nhưng đã quá 13:00 (MSG264)
                       4. Ca chiều hôm nay, nhưng đã quá giờ đóng cửa
                       Lưu ý: nhánh này KHÔNG hiện nút "Đi đến ... để chốt ca", kể cả khi đang có ca khác chưa chốt.
                       Ngoại lệ: nếu ca chiều đã quá giờ đóng cửa mà ca sáng vẫn đang mở thì không vào đây,
                       vì bước kiểm tra ca khác đang mở chạy trước. --%>
                  <h2>Ca trực đã qua</h2>
                  <p class="muted">
                    <c:out value="${detail.shiftNotOpenedReason}"/>
                  </p>
                </c:when>

                <%-- Nhánh 4: Ca chưa mở (có thể mở ngay hoặc đang bị chặn + nút đi tới ca đang mở để chốt trước) --%>
                <c:otherwise>
                  <h2>Ca trực chưa mở</h2>
                  <p class="muted">
                    <c:choose>
                      <c:when test="${not empty detail.shiftNotOpenedReason}">
                        <%-- Trường hợp 1: ca của ngày tương lai, nếu cố tình tạo 1 ca tương lai trong database
                             Trường hợp 2: Hôm nay là ngày nghỉ của phòng tập
                             Trường hợp 3: Mở ca chiều khi ca sáng chưa mở và chưa đến 13:00
                             Trường hợp 4: Ca sáng đã kết thúc (sau 13:00) nhưng chưa mở ca chiều, không được nhảy cóc mở ca chiều
                             Trường hợp 5: Có ca khác đang mở chưa chốt (ca của quá khứ hoặc ca sáng hôm nay chưa chốt)
                        --%>
                        <c:out value="${detail.shiftNotOpenedReason}"/>
                      </c:when>
                      <c:otherwise>
                        Bấm <b>Mở ca</b> để bắt đầu ca làm việc. Màn hình bán hàng tại quầy yêu cầu ca phải mở trước khi thanh toán.
                      </c:otherwise>
                    </c:choose>
                  </p>
                  <c:if test="${not empty detail.blockingOpenShift}">
                    <%-- Hiện nút "Đi đến" khi đang ở ca chiều mà ca sáng hôm nay đang mở, khi có ca quá khứ đang mở --%>
                    <div style="margin-top: 10px;">
                      <a href="${pageContext.request.contextPath}/receptionist/shift?shiftId=${detail.blockingOpenShift.shiftId}"
                         class="btn btn-secondary btn-sm"
                         style="display: inline-flex; align-items: center; gap: 6px;">
                        <i class="fa-solid fa-arrow-right"></i> Đi đến <c:out value="${detail.blockingOpenShift.shiftType.displayName}"/> (<c:out value="${gms:formatDate(detail.blockingOpenShift.workDate)}"/>) để chốt ca
                      </a>
                    </div>
                  </c:if>
                </c:otherwise>
              </c:choose>
            </div>

            <%-- Cột phải: nút hành động chính (Mở ca / Chốt ca / Badge đã chốt) --%>
            <div class="hero-actions">
              <%-- Ca chưa mở: chỉ hiện nút "Mở ca" khi được mở (shift.js gắn click #btnOpenShiftModal mở #modalOpenShift).
                   Trường hợp không mở được thì tiêu đề + lý do bên trái đã nói đủ, không lặp thêm badge ở đây. --%>
              <c:if test="${detail.canOpen}">
                <button class="btn btn-primary" type="button" id="btnOpenShiftModal">
                  <i class="fa-solid fa-door-open"></i> Mở ca
                </button>
              </c:if>

              <%-- Ca đang mở: hiện nút "Chốt ca" (shift.js mở #modalCloseShift); khóa nếu còn đơn Pending (CBR-78) --%>
              <c:if test="${detail.shift.open}">
                <button class="btn btn-primary" type="button" id="btnCloseShiftModal"
                        ${detail.hasPendingOrders() ? 'disabled' : ''}
                        title="${detail.hasPendingOrders() ? 'Không thể chốt ca: còn đơn quầy chờ xử lý' : ''}">
                  <i class="fa-solid fa-cash-register"></i> Chốt ca
                </button>
              </c:if>

              <c:if test="${detail.shift.closed}">
                <span class="badge badge-muted badge-lg">
                  <i class="fa-solid fa-lock"></i> Ca làm việc đã chốt (chỉ xem)
                </span>
              </c:if>
            </div>
          </section>

          <%-- ===== KHỐI 2: CÁC THẺ KPI THỐNG KÊ DOANH THU (detail.metrics: ShiftMetrics, DBR-55) ===== --%>
          <div class="kpi-grid" data-reveal>
            <div class="kpi-card">
              <span class="kpi-label"><i class="fa-solid fa-money-bill-wave"></i> Tiền mặt</span>
              <b class="kpi-value tabular"><c:out value="${gms:formatMoney(detail.metrics.cashTotal)}"/></b>
              <span class="kpi-foot"><c:out value="${detail.metrics.cashCount}"/> giao dịch hợp lệ</span>
            </div>

            <div class="kpi-card">
              <span class="kpi-label"><i class="fa-solid fa-building-columns"></i> Chuyển khoản</span>
              <b class="kpi-value tabular"><c:out value="${gms:formatMoney(detail.metrics.bankTotal)}"/></b>
              <span class="kpi-foot"><c:out value="${detail.metrics.bankCount}"/> giao dịch hợp lệ</span>
            </div>

            <div class="kpi-card">
              <span class="kpi-label"><i class="fa-solid fa-chart-line"></i> Tổng thu</span>
              <b class="kpi-value tabular"><c:out value="${gms:formatMoney(detail.metrics.totalRevenue)}"/></b>
              <span class="kpi-foot"><c:out value="${detail.metrics.totalValidCount}"/> giao dịch hợp lệ</span>
            </div>

            <div class="kpi-card ${detail.metrics.pendingCount > 0 ? 'kpi-warn' : ''}">
              <span class="kpi-label"><i class="fa-solid fa-hourglass-half"></i> Đơn chờ xử lý</span>
              <b class="kpi-value tabular"><c:out value="${detail.metrics.pendingCount}"/></b>
              <span class="kpi-foot">
                <c:choose>
                  <c:when test="${detail.metrics.pendingCount > 0}">Phải xử lý xong trước khi chốt ca</c:when>
                  <c:otherwise>Không có đơn chờ xử lý</c:otherwise>
                </c:choose>
              </span>
            </div>
          </div>

          <%-- ===== KHỐI 3: ALERT BANNER CẢNH BÁO ĐƠN CHỜ XÁC NHẬN CHẶN CHỐT CA (detail.pendingOrders, CBR-78) ===== --%>
          <c:if test="${detail.hasPendingOrders()}">
            <div class="alert-banner warn" id="pendBox" data-reveal>
              <div class="alert-icon"><i class="fa-solid fa-triangle-exclamation"></i></div>
              <div class="alert-text">
                <b>Còn <c:out value="${detail.pendingOrders.size()}"/> đơn hàng đang chờ xác nhận:</b>
                <c:forEach var="p" items="${detail.pendingOrders}" varStatus="status">
                  <a class="pending-code"
                     style="color: inherit; text-decoration: underline;"
                     href="${pageContext.request.contextPath}/receptionist/pt-sales?order=${p.orderId}"><c:out value="${p.orderCode}"/> (<c:out value="${p.customerName}"/>)</a><c:if test="${!status.last}">, </c:if>
                </c:forEach>
                <div>Vui lòng bấm vào đơn để tiếp tục thu tiền hoặc hủy đơn trước khi chốt ca.</div>
              </div>
            </div>
          </c:if>

          <%-- ===== KHỐI 4: DANH SÁCH GIAO DỊCH TRONG CA (detail.transactionsPage: PageResult<ShiftTransactionItem>) ===== --%>
          <section class="panel" data-reveal>
            <div class="panel-head">
              <div>
                <h2>Giao dịch trong ca</h2>
                <p>Danh sách các đơn hàng phát sinh tại quầy thuộc ca trực này. Sắp xếp đơn mới nhất lên đầu.</p>
              </div>
            </div>

            <%-- Thanh tìm kiếm & lọc (GET /receptionist/shift giữ shiftId + q, method, status) --%>
            <form class="toolbar" method="get" action="${pageContext.request.contextPath}/receptionist/shift">
              <input type="hidden" name="shiftId" value="${detail.shift.shiftId}">

              <div class="search-box">
                <i class="fa-solid fa-magnifying-glass search-icon"></i>
                <input id="txSearch"
                       name="q"
                       type="search"
                       value="<c:out value='${detail.searchKeyword}'/>"
                       placeholder="Tìm theo mã đơn hoặc họ tên khách hàng..."
                       aria-label="Tìm giao dịch">
              </div>

              <div class="filter-group">
                <label for="filterMethod">Hình thức:</label>
                <select id="filterMethod" name="method" class="select-input">
                  <option value="all" ${empty detail.methodFilter || detail.methodFilter == 'all' ? 'selected' : ''}>Tất cả</option>
                  <option value="cash" ${detail.methodFilter == 'cash' ? 'selected' : ''}>Tiền mặt</option>
                  <option value="bank" ${detail.methodFilter == 'bank' ? 'selected' : ''}>Chuyển khoản</option>
                  <%-- selected sẽ giúp dropdown hiển thị lại cái mà mình vừa chọn khi submit form --%>
                </select>
              </div>

              <div class="filter-group">
                <label for="filterStatus">Trạng thái:</label>
                <select id="filterStatus" name="status" class="select-input">
                  <option value="all" ${empty detail.statusFilter || detail.statusFilter == 'all' ? 'selected' : ''}>Tất cả</option>
                  <option value="paid" ${detail.statusFilter == 'paid' ? 'selected' : ''}>Đã thanh toán</option>
                  <option value="pending" ${detail.statusFilter == 'pending' ? 'selected' : ''}>Chờ xác nhận</option>
                  <option value="cancelled" ${detail.statusFilter == 'cancelled' ? 'selected' : ''}>Đã hủy</option>
                </select>
              </div>

              <button class="btn btn-primary btn-sm" type="submit">Lọc</button>
              <a class="btn btn-ghost btn-sm reset"
                 href="${pageContext.request.contextPath}/receptionist/shift?shiftId=${detail.shift.shiftId}">Xóa lọc</a>
            </form>

            <%-- Danh sách giao dịch (#txList): trạng thái rỗng hoặc từng dòng giao dịch --%>
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
                      <%-- Thông tin đơn hàng: mã đơn, khách hàng, trạng thái, thời gian, gói, hình thức & lý do hủy --%>
                      <div class="row-main">
                        <div class="row-title">
                          <b><c:out value="${item.orderCode}"/></b>
                          <span class="sep">·</span>
                          <span><c:out value="${item.customerName}"/></span>
                          <c:if test="${not empty item.customerPhone}">
                            <small class="muted">(<c:out value="${item.customerPhone}"/>)</small>
                          </c:if>

                          <%-- Trạng thái đơn --%>
                          <span class="badge ${item.statusBadgeClass}">
                            <c:out value="${item.statusDisplayName}"/>
                          </span>
                        </div>

                        <div class="row-meta">
                          <span><i class="fa-solid fa-clock"></i> <c:out value="${gms:formatDateTime(item.createdAt)}"/></span>
                          <span><i class="fa-solid fa-dumbbell"></i> <c:out value="${item.packageName}"/></span>
                          <span class="badge badge-muted">
                            <i class="fa-solid ${item.cash ? 'fa-money-bill-wave' : (item.transfer ? 'fa-building-columns' : 'fa-clock')}"></i>
                            <c:out value="${item.paymentMethodDisplayName}"/>
                          </span>

                          <c:if test="${item.cancelled and not empty item.cancelReason}">
                            <span class="cancel-reason-text">
                              <i class="fa-solid fa-circle-info"></i> Lý do: <c:out value="${item.cancelReason}"/>
                            </span>
                          </c:if>
                        </div>
                      </div>

                      <%-- Số tiền và các nút thao tác trên dòng (xác nhận chuyển khoản / hủy đơn) --%>
                      <div class="row-end">
                        <b class="tabular ${item.cancelled ? 'is-void' : ''}">
                          <c:out value="${gms:formatMoney(item.amount)}"/>
                        </b>

                        <div class="row-actions">
                          <%-- Nút điều hướng sang trang Bán gói PT để xác nhận thanh toán --%>
                          <c:if test="${item.canConfirmTransfer}">
                            <a class="btn btn-primary btn-sm"
                               href="${pageContext.request.contextPath}/receptionist/pt-sales?order=${item.orderId}">
                              <i class="fa-solid fa-arrow-up-right-from-square"></i> Tiếp tục thu tiền
                            </a>
                          </c:if>

                          <%-- Nút hủy đơn quầy: shift.js gắn click .btn-void-order, đọc data-void-* để mở #modalVoidOrder --%>
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

            <%-- Phân trang: dùng c:url giữ nguyên shiftId và các bộ lọc q, method, status khi chuyển trang --%>
            <c:if test="${detail.transactionsPage.totalPages > 1}">
              <c:url var="basePageUrl" value="/receptionist/shift">
                <c:param name="shiftId" value="${detail.shift.shiftId}"/>
                <c:if test="${not empty detail.searchKeyword}">
                  <c:param name="q" value="${detail.searchKeyword}"/>
                </c:if>
                <c:if test="${not empty detail.methodFilter}">
                  <c:param name="method" value="${detail.methodFilter}"/>
                </c:if>
                <c:if test="${not empty detail.statusFilter}">
                  <c:param name="status" value="${detail.statusFilter}"/>
                </c:if>
              </c:url>

              <div class="pager">
                <div class="pager-info">
                  Trang <c:out value="${detail.transactionsPage.pageNumber}"/> / <c:out value="${detail.transactionsPage.totalPages}"/>
                  (<c:out value="${detail.transactionsPage.totalItems}"/> giao dịch)
                </div>

                <div class="pager-btns">
                  <c:if test="${detail.transactionsPage.pageNumber > 1}">
                    <a class="btn btn-ghost btn-sm"
                       href="${basePageUrl}&page=${detail.transactionsPage.pageNumber - 1}">
                      <i class="fa-solid fa-chevron-left"></i> Trước
                    </a>
                  </c:if>

                  <c:forEach var="p" begin="1" end="${detail.transactionsPage.totalPages}">
                    <c:if test="${p >= detail.transactionsPage.pageNumber - 2 and p <= detail.transactionsPage.pageNumber + 2}">
                      <a class="btn btn-sm ${p == detail.transactionsPage.pageNumber ? 'btn-primary' : 'btn-ghost'}"
                         href="${basePageUrl}&page=${p}">
                        <c:out value="${p}"/>
                      </a>
                    </c:if>
                  </c:forEach>

                  <c:if test="${detail.transactionsPage.pageNumber < detail.transactionsPage.totalPages}">
                    <a class="btn btn-ghost btn-sm"
                       href="${basePageUrl}&page=${detail.transactionsPage.pageNumber + 1}">
                      Sau <i class="fa-solid fa-chevron-right"></i>
                    </a>
                  </c:if>
                </div>
              </div>
            </c:if>
          </section>
        </c:if>
      </main>
    </div>
  </div>

  <%-- ===== MODAL 1: MỞ CA LÀM VIỆC (chỉ render khi detail.canOpen; shift.js mở từ #btnOpenShiftModal) ===== --%>
  <c:if test="${not empty detail and detail.canOpen}">
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
              <button class="btn btn-primary" type="submit">
                <i class="fa-solid fa-door-open"></i> Xác nhận mở ca
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </c:if>

  <%-- ===== CÁC MODAL CHỈ DÙNG KHI CA ĐANG MỞ (Chốt ca & Hủy đơn quầy) ===== --%>
  <c:if test="${not empty detail and detail.shift.open}">
    <%-- ===== MODAL 2: ĐỐI SOÁT & CHỐT CA LÀM VIỆC (shift.js mở từ #btnCloseShiftModal, submit action=closeShift) ===== --%>
    <div class="modal" id="modalCloseShift" role="dialog" aria-modal="true"
         aria-labelledby="modalCloseTitle" aria-describedby="modalCloseDesc">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-head">
            <h2 class="modal-title" id="modalCloseTitle">Chốt <c:out value="${detail.shift.shiftType.displayName}"/></h2>
            <button class="modal-close" type="button" data-close-modal aria-label="Đóng">&times;</button>
          </div>

          <form id="formCloseShift" method="post" action="${pageContext.request.contextPath}/receptionist/shift">
            <input type="hidden" name="action" value="closeShift">
            <input type="hidden" name="shiftId" value="${detail.shift.shiftId}">
            <%-- Giá trị tiền sổ sách thô để shift.js (recalculateVariance) đối chiếu realtime --%>
            <input type="hidden" id="expectedCashRaw" value="${detail.metrics.cashTotal}">

            <div class="modal-body">
              <p class="modal-desc" id="modalCloseDesc">Đếm số tiền mặt thực tế trong két quầy Lễ tân và nhập vào bên dưới. Nếu có chênh lệch so với số liệu sổ sách, bắt buộc phải nêu rõ lý do giải trình.</p>

              <div class="reconcile-card">
                <div class="reconcile-item">
                  <span class="reconcile-label">Tiền mặt theo sổ sách</span>
                  <b class="reconcile-val tabular" id="displayExpectedCash">
                    <c:out value="${gms:formatMoney(detail.metrics.cashTotal)}"/>
                  </b>
                </div>
              </div>

              <div class="field">
                <label class="field-label" for="inputCountedCash">
                  Tiền mặt thực tế đếm được (VND) <span class="required">*</span>
                </label>
                <input id="inputCountedCash"
                       name="countedCash"
                       type="text"
                       inputmode="numeric"
                       class="field-input tabular"
                       placeholder="Ví dụ: 2.600.000"
                       required
                       value="${detail.metrics.cashTotal}">
              </div>

              <%-- Khung hiển thị chênh lệch (shift.js cập nhật #varianceVal, #varianceBadge và bật/tắt #varianceReasonWrapper) --%>
              <div class="variance-result" id="varianceBox" aria-live="polite">
                <span class="variance-label">Chênh lệch:</span>
                <b class="variance-value tabular" id="varianceVal">0đ</b>
                <span class="badge badge-ok" id="varianceBadge">Khớp sổ sách</span>
              </div>

              <%-- Chỉ hiện & bắt buộc nhập (5-500 ký tự) khi tiền đếm thực tế khác sổ sách (BR-46) --%>
              <div class="field" id="varianceReasonWrapper" style="display: none;">
                <label class="field-label" for="inputVarianceReason">
                  Lý do chênh lệch <span class="required">*</span>
                </label>
                <textarea id="inputVarianceReason"
                          name="varianceReason"
                          rows="3"
                          class="field-textarea"
                          placeholder="Nêu rõ lý do thừa/thiếu tiền mặt (tối thiểu 5 ký tự)..."
                          minlength="5"
                          maxlength="500"></textarea>
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

    <%-- ===== MODAL 3: HỦY ĐƠN HÀNG TẠI QUẦY (shift.js điền #voidOrderIdInput, #voidOrderCodeText, submit action=cancelOrder) ===== --%>
    <div class="modal" id="modalVoidOrder" role="dialog" aria-modal="true" aria-labelledby="modalVoidTitle">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-head">
            <h2 class="modal-title" id="modalVoidTitle">
              Hủy đơn hàng <span id="voidOrderCodeText" class="text-primary"></span>
            </h2>
            <button class="modal-close" type="button" data-close-modal aria-label="Đóng">&times;</button><%-- nút x đóng modal --%>
          </div>

          <form id="formVoidOrder" method="post" action="${pageContext.request.contextPath}/receptionist/shift">
            <input type="hidden" name="action" value="cancelOrder">
            <input type="hidden" name="shiftId" value="${detail.shift.shiftId}">
            <input type="hidden" name="orderId" id="voidOrderIdInput" value="">

            <div class="modal-body">
              <div class="field">
                <label class="field-label" for="inputCancelReason">
                  Lý do hủy đơn <span class="required">*</span>
                </label>
                <textarea id="inputCancelReason"
                          name="cancelReason"
                          rows="3"
                          class="field-textarea"
                          placeholder="Ví dụ: Khách đổi ý tại quầy, chọn nhầm gói tập, thanh toán bị lỗi..."
                          minlength="5"
                          maxlength="500"
                          required></textarea>
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
  </c:if>

</body>
</html>
