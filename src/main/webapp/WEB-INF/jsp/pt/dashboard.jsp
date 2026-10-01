<%--
  PT Dashboard (#49, UC-32) — /pt/dashboard. Chỉ xem, không có form sửa dữ liệu.
  Dữ liệu: PtDashboardServlet → request attribute "dashboard" (PtDashboardView).

  Bố cục theo prototype pt/trang-chu.html, nghiệp vụ theo SRS 9.1.2 + docs rule 05_Dashboard_Rules.md:
    Topbar       : Tổng quan · Điểm danh hôm nay · avatar PT
    Đầu trang    : lời chào + thời tiết (hiệu ứng CSS theo weather.moodCss) + tóm tắt hôm nay
    Cột chính    : Buổi cần điểm danh (P-07) · Hoa hồng theo kỳ · Buổi sắp tới (lọc ngày) · banner thiếu gói tập
    Cột phụ      : Khách phụ trách · Dự kiến còn lại tháng này · Tuần này · Lịch rảnh
  Bộ lọc server (GET): period, date, days. Bộ lọc tại chỗ (JS, không tải lại): tìm khách, gói, trạng thái.
  Dùng được trên điện thoại (GBR-35).
--%>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<c:set var="d" value="${dashboard}" />
<c:set var="w" value="${d.weather}" />
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Tổng quan — Super Gym PT</title>
  <link rel="stylesheet" href="${ctx}/css/pages/pt/dashboard.css?v=9">
  <script src="${ctx}/js/pages/pt/dashboard.js?v=4" defer></script>
</head>
<body class="dock theme-staff">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>

    <div class="main">
      <%-- ================= Topbar (prototype) ================= --%>
      <header class="pt-topbar">
        <h1>Tổng quan</h1>
        <div class="pt-topbar-actions">
          <a class="btn btn-primary btn-sm" href="${ctx}/pt/sessions/attendance">Điểm danh hôm nay</a>
          <a class="pt-user" href="${ctx}/account" title="Cài đặt tài khoản">
            <span class="pt-avatar"><c:out value="${d.initials}" /></span>
            <span class="pt-user-text">
              <b><c:out value="${d.fullName}" /></b>
              <span>PT · <c:out value="${d.employeeCode}" /></span>
            </span>
          </a>
        </div>
      </header>

      <main class="pt-content">
        <%-- ================= Lời chào + thời tiết ================= --%>
        <section class="hero ${empty w ? 'hero--plain' : 'hero--'.concat(w.moodCss)}"
                 aria-label="Lời chào và thời tiết hôm nay">
          <c:if test="${not empty w}">
            <%-- Cảnh động: chỉ trang trí, trình đọc màn hình bỏ qua --%>
            <div class="hero-art" aria-hidden="true">
              <span class="art-glow"></span>
              <span class="art-sun"></span>
              <span class="art-moon"></span>
              <span class="art-cloud art-cloud--back"></span>
              <span class="art-cloud art-cloud--front"></span>
              <span class="art-bolt"></span>
              <span class="art-rain"><c:forEach begin="1" end="12"><i></i></c:forEach></span>
              <span class="art-stars"><c:forEach begin="1" end="9"><i></i></c:forEach></span>
              <span class="art-heat"></span>
              <span class="art-flash"></span>
            </div>
          </c:if>

          <div class="hero-body">
            <p class="hero-kicker"><c:out value="${d.todayLabel}" /></p>
            <h2 class="hero-title"><c:out value="${d.greeting}" />, <c:out value="${d.fullName}" /></h2>

            <c:if test="${not empty w}">
              <div class="hero-weather">
                <span class="hero-temp tabular">${w.temperature}°</span>
                <div class="hero-weather-text">
                  <p class="hero-cond">
                    <i class="fa-solid ${w.iconClass}" aria-hidden="true"></i>
                    <c:out value="${w.conditionLabel}" /> · <c:out value="${w.cityName}" />
                  </p>
                  <p class="hero-headline"><c:out value="${w.title}" /></p>
                  <p class="hero-advice"><c:out value="${w.advice}" /></p>
                </div>
              </div>
              <ul class="hero-chips hero-chips--weather">
                <li><i class="fa-solid fa-temperature-half" aria-hidden="true"></i>Cảm giác ${w.feelsLike}°</li>
                <li><i class="fa-solid fa-arrows-up-down" aria-hidden="true"></i>${w.minTemperature}° – ${w.maxTemperature}°</li>
                <li><i class="fa-solid fa-umbrella" aria-hidden="true"></i>Mưa ${w.rainChance}%</li>
                <li><i class="fa-solid fa-droplet" aria-hidden="true"></i>Ẩm ${w.humidity}%</li>
              </ul>
            </c:if>

            <%-- Tóm tắt hôm nay --%>
            <ul class="hero-chips">
              <li><b>${d.todaySessionCount}</b> buổi hôm nay</li>
              <li class="${empty d.attendanceSessions ? '' : 'is-warn'}"><b>${d.attendanceSessions.size()}</b> cần điểm danh</li>
              <li><b>${d.weekFreeSlots}</b> slot trống tuần này</li>
            </ul>
          </div>
        </section>

        <div class="split">
          <div class="col-main">
            <%-- ================= Buổi cần điểm danh (SRS 9.1.2 · BR-54 · P-07) ================= --%>
            <section class="panel panel--attention">
              <div class="panel-head">
                <div>
                  <h2><i class="fa-solid fa-clipboard-check" aria-hidden="true"></i> Buổi cần điểm danh</h2>
                  <p>Điểm danh được từ giờ bắt đầu tới ${d.attendanceWindowHours} giờ sau khi buổi kết thúc.</p>
                </div>
                <a class="btn btn-ghost btn-sm" href="${ctx}/pt/sessions/attendance">Mở điểm danh <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a>
              </div>
              <c:choose>
                <c:when test="${empty d.attendanceSessions}">
                  <p class="ok-line"><i class="fa-solid fa-circle-check" aria-hidden="true"></i> Không có buổi nào đang chờ điểm danh.</p>
                </c:when>
                <c:otherwise>
                  <div class="data-list">
                    <c:forEach var="s" items="${d.attendanceSessions}">
                      <a class="data-row<c:if test="${s.missingMembership}"> is-alert</c:if>"
                         href="${ctx}/pt/sessions/attendance?sessionId=${s.sessionId}">
                        <div class="row-main">
                          <div class="row-title">
                            <c:out value="${s.dayLabel}" /> · <span class="tabular"><c:out value="${s.timeRange}" /></span>
                            <c:if test="${s.missingMembership}">
                              <span class="badge badge-danger"><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i> Chưa có gói tập</span>
                            </c:if>
                          </div>
                          <div class="row-meta">
                            <span><i class="fa-solid fa-user" aria-hidden="true"></i> <c:out value="${s.customerName}" /><c:if test="${not empty s.partnerName}"> &amp; <c:out value="${s.partnerName}" /></c:if></span>
                            <span><i class="fa-solid fa-layer-group" aria-hidden="true"></i> <c:out value="${s.packageLabel}" /></span>
                          </div>
                        </div>
                        <span class="btn btn-primary btn-sm">Điểm danh</span>
                      </a>
                    </c:forEach>
                  </div>
                </c:otherwise>
              </c:choose>
            </section>

            <%-- ================= Hoa hồng (prototype · DBR-58 · CBR-80/81/82) ================= --%>
            <section class="panel">
              <div class="panel-head">
                <div>
                  <h2>Hoa hồng</h2>
                  <p><c:out value="${d.periodNote}" /></p>
                </div>
                <form class="period-form" method="get" action="${ctx}/pt/dashboard">
                  <input type="hidden" name="date" value="${d.fromDateValue}">
                  <input type="hidden" name="days" value="${d.rangeDays}">
                  <label for="periodPick">Kỳ</label>
                  <select id="periodPick" name="period" data-auto-submit>
                    <c:forEach var="opt" items="${d.periodOptions}">
                      <option value="${opt.value}" <c:if test="${opt.value eq d.periodValue}">selected</c:if>><c:out value="${opt.label}" /></option>
                    </c:forEach>
                  </select>
                  <span class="badge badge-neutral">${d.commissionRate}% đơn giá buổi</span>
                </form>
              </div>

              <div class="commission-top">
                <div>
                  <div class="kpi-value kpi-value--xl tabular"><c:out value="${d.commissionTotalText}" /></div>
                  <ul class="stat-chips">
                    <li class="stat-ok"><b>${d.completedCount}</b> hoàn thành</li>
                    <li class="stat-bad"><b>${d.missedCount}</b> khách vắng</li>
                    <li class="stat-bad"><b>${d.cancelledPaidCount}</b> hủy vẫn tính</li>
                    <c:if test="${d.cancelledUnpaidCount > 0}">
                      <li class="stat-muted"><b>${d.cancelledUnpaidCount}</b> hủy không tính</li>
                    </c:if>
                  </ul>
                </div>
                <figure class="mini-chart" aria-label="Biểu đồ hoa hồng ${d.chartTitle}">
                  <figcaption class="section-label"><c:out value="${d.chartTitle}" /></figcaption>
                  <div class="mini-chart-bars">
                    <c:forEach var="bar" items="${d.chartBars}">
                      <div class="bar" title="${bar.label}: ${bar.amountText}">
                        <span class="bar-fill" style="--h:${bar.heightPercent}%"></span>
                        <span class="bar-label"><c:out value="${bar.label}" /></span>
                      </div>
                    </c:forEach>
                  </div>
                </figure>
              </div>

              <c:choose>
                <c:when test="${empty d.commissionLines}">
                  <div class="empty-state">
                    <i class="fa-solid fa-coins" aria-hidden="true"></i>
                    <p>Kỳ này chưa có buổi nào được tính hoa hồng.</p>
                  </div>
                </c:when>
                <c:otherwise>
                  <table class="tx-table">
                    <thead>
                      <tr><th>Nguồn buổi</th><th>Số buổi</th><th>Hoa hồng / buổi (TB)</th><th>Thành tiền</th></tr>
                    </thead>
                    <tbody>
                      <c:forEach var="line" items="${d.commissionLines}">
                        <tr>
                          <td data-label="Nguồn buổi"><c:out value="${line.label}" /></td>
                          <td data-label="Số buổi" class="tabular">${line.sessionCount}</td>
                          <td data-label="Hoa hồng / buổi" class="tabular"><c:out value="${line.averageText}" /></td>
                          <td data-label="Thành tiền" class="tabular"><c:out value="${line.totalText}" /></td>
                        </tr>
                      </c:forEach>
                    </tbody>
                  </table>
                </c:otherwise>
              </c:choose>
              <p class="muted">
                Buổi khách hủy vẫn tính hoa hồng cho bạn. Buổi bạn chuyển cho PT khác thì hoa hồng thuộc về PT nhận.
                Buổi hủy do phòng tập đóng cửa hoặc gói PT hết hạn không tính.
              </p>
            </section>

            <%-- ================= Buổi sắp tới (prototype + SRS 9.1.2 bộ lọc ngày) ================= --%>
            <section class="panel">
              <div class="panel-head">
                <div>
                  <h2>Buổi sắp tới</h2>
                  <p>Từ <c:out value="${d.fromDateLabel}" /><c:if test="${d.rangeDays > 1}">, ${d.rangeDays} ngày</c:if> · ${d.upcomingSessions.size()} buổi</p>
                </div>
                <a class="btn btn-ghost btn-sm" href="${ctx}/pt/sessions/attendance">Mở điểm danh <i class="fa-solid fa-arrow-right" aria-hidden="true"></i></a>
              </div>

              <%-- Ngày + khoảng gửi lên server; Tìm khách / Gói / Trạng thái lọc ngay trên trang (dashboard.js) --%>
              <form class="toolbar" method="get" action="${ctx}/pt/dashboard" id="upcomingFilter" novalidate>
                <input type="hidden" name="period" value="${d.periodValue}">
                <div class="search">
                  <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
                  <input id="upSearch" type="search" placeholder="Tìm khách" aria-label="Tìm khách">
                </div>
                <%-- Mỗi cặp nhãn + ô nằm chung 1 khối .tb-field để xuống dòng không bị tách nhau --%>
                <span class="tb-field">
                  <label for="datePick">Từ ngày</label>
                  <input type="date" id="datePick" name="date" value="${d.fromDateValue}" required
                         aria-describedby="dateError" <c:if test="${not empty d.dateError}">class="invalid"</c:if>>
                </span>
                <span class="tb-field">
                <label for="upRange">Trong</label>
                <select id="upRange" name="days" data-auto-submit>
                  <option value="1" <c:if test="${d.rangeDays == 1}">selected</c:if>>Chỉ ngày này</option>
                  <option value="3" <c:if test="${d.rangeDays == 3}">selected</c:if>>3 ngày</option>
                  <option value="7" <c:if test="${d.rangeDays == 7}">selected</c:if>>7 ngày</option>
                  <option value="30" <c:if test="${d.rangeDays == 30}">selected</c:if>>30 ngày</option>
                </select>
                </span>
                <span class="tb-field">
                <label for="upPkg">Gói</label>
                <select id="upPkg">
                  <option value="all">Tất cả</option>
                  <option value="PTSingle">Gói PT cá nhân</option>
                  <option value="PTCouple">Gói PT Couple</option>
                  <option value="SingleSession">Buổi lẻ</option>
                </select>
                </span>
                <span class="tb-field">
                <label for="upStatus">Trạng thái</label>
                <select id="upStatus">
                  <option value="all">Tất cả</option>
                  <option value="Scheduled">Đã xếp</option>
                  <option value="Completed">Hoàn thành</option>
                  <option value="Missed">Vắng</option>
                  <option value="Cancelled">Đã hủy</option>
                </select>
                </span>
                <a class="btn btn-ghost btn-sm reset" href="${ctx}/pt/dashboard?period=${d.periodValue}" id="upReset">Xoá lọc</a>
              </form>
              <p class="field-error" id="dateError" <c:if test="${empty d.dateError}">hidden</c:if>><c:out value="${d.dateError}" /></p>

              <div class="data-list" id="upList">
                <c:forEach var="s" items="${d.upcomingSessions}">
                  <div class="data-row<c:if test="${s.missingMembership}"> is-alert</c:if>"
                       data-pkg="${s.sourceType}" data-status="${s.status}"
                       data-q="<c:out value="${s.customerName} ${s.partnerName}" />">
                    <div class="row-main">
                      <div class="row-title">
                        <c:out value="${s.dayLabel}" /> · <span class="tabular"><c:out value="${s.timeRange}" /></span>
                        <c:if test="${not empty s.partnerName}"><span class="badge badge-neutral">Couple</span></c:if>
                        <c:if test="${s.missingMembership}">
                          <span class="badge badge-danger"><i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i> Chưa có gói tập</span>
                        </c:if>
                      </div>
                      <div class="row-meta">
                        <span><i class="fa-solid fa-user" aria-hidden="true"></i> <c:out value="${s.customerName}" /><c:if test="${not empty s.partnerName}"> &amp; <c:out value="${s.partnerName}" /></c:if></span>
                        <span><i class="fa-solid fa-layer-group" aria-hidden="true"></i> <c:out value="${s.packageLabel}" /></span>
                        <c:if test="${not empty s.missedReason}">
                          <span><i class="fa-solid fa-circle-info" aria-hidden="true"></i> <c:out value="${s.missedReason}" /></span>
                        </c:if>
                      </div>
                    </div>
                    <span class="badge ${s.statusBadge}"><c:out value="${s.statusLabel}" /></span>
                  </div>
                </c:forEach>
                <div class="empty-state" data-empty <c:if test="${not empty d.upcomingSessions}">hidden</c:if>>
                  <i class="fa-solid fa-box-open" aria-hidden="true"></i>
                  <p>Không có buổi nào khớp bộ lọc.</p>
                </div>
              </div>
            </section>

            <%-- ================= Banner khách thiếu gói tập (prototype · CBR-66) ================= --%>
            <c:if test="${d.missingMembershipCustomerCount > 0}">
              <div class="alert-banner danger" role="alert">
                <i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i>
                <span><b>${d.missingMembershipCustomerCount} khách</b> có buổi nằm ngoài hạn gói tập. Nhắc khách gia hạn trước buổi ngày <c:out value="${d.missingMembershipFirstDate}" />, nếu không buổi sẽ bị tính Vắng.</span>
                <a class="btn btn-outline btn-sm" href="${ctx}/pt/clients">Xem khách</a>
              </div>
            </c:if>
          </div>

          <%-- ================= Cột phụ ================= --%>
          <aside class="col-side">
            <%-- Khách phụ trách (GBR-10) — cùng ngày với ô "Từ ngày" --%>
            <div class="kpi-card">
              <div class="kpi-top"><i class="fa-solid fa-user-group" aria-hidden="true"></i><span class="section-label">Khách phụ trách</span></div>
              <div class="kpi-value tabular is-key">${d.clients.size()}</div>
              <div class="kpi-note">
                <c:choose>
                  <c:when test="${d.clientsEndingSoon > 0}">${d.clientsEndingSoon} sắp hết hạn gói PT (≤ 7 ngày)</c:when>
                  <c:otherwise>Không có gói PT nào sắp hết hạn</c:otherwise>
                </c:choose>
                <c:if test="${not d.fromToday}"> · tính vào <c:out value="${d.fromDateLabel}" /></c:if>
              </div>
              <c:if test="${not empty d.clients}">
                <ul class="client-list">
                  <c:forEach var="cl" items="${d.clients}" end="3">
                    <li>
                      <span class="client-dot" aria-hidden="true"><i class="fa-solid fa-user"></i></span>
                      <span class="client-text">
                        <b><c:out value="${cl.fullName}" /></b>
                        <span><c:out value="${cl.packageLabel}" /><c:if test="${cl.partner}"> · đi kèm</c:if> · hết hạn <c:out value="${cl.endDateText}" /></span>
                      </span>
                      <c:choose>
                        <c:when test="${cl.frozen}"><span class="badge badge-warning">Bảo lưu</span></c:when>
                        <c:when test="${cl.endingSoon}"><span class="badge badge-warning">Sắp hết</span></c:when>
                      </c:choose>
                    </li>
                  </c:forEach>
                </ul>
              </c:if>
              <a class="btn btn-ghost btn-sm btn-block" href="${ctx}/pt/clients">Khách của tôi</a>
            </div>

            <div class="side-card">
              <span class="section-label">Dự kiến còn lại tháng này</span>
              <h3 class="side-money tabular"><c:out value="${d.expectedAmountText}" /></h3>
              <div class="side-row"><span>Buổi đã xếp</span><b>${d.expectedSessionCount}</b></div>
              <p class="muted">Chốt khi buổi hoàn thành hoặc khách vắng.</p>
            </div>

            <div class="side-card">
              <span class="section-label">Tuần này</span>
              <div class="side-row"><span>Buổi đã xếp</span><b>${d.weekScheduled}</b></div>
              <div class="side-row"><span>Đã dạy</span><b>${d.weekTaught}</b></div>
              <div class="side-row"><span>Khách vắng</span><b>${d.weekMissed}</b></div>
              <div class="side-row"><span>Slot còn trống</span><b>${d.weekFreeSlots}</b></div>
            </div>

            <div class="side-card">
              <span class="section-label">Lịch rảnh</span>
              <p class="muted">Khách chỉ đặt được slot trong tuần hiện tại; từ Thứ 7 mở thêm tuần kế.</p>
              <a class="btn btn-ghost btn-sm btn-block" href="${ctx}/pt/time-slots">Quản lý lịch rảnh</a>
            </div>
          </aside>
        </div>
      </main>
    </div>
  </div>
</body>
</html>
