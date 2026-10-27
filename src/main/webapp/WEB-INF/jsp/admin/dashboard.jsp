<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>
<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Báo cáo vận hành hệ thống · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/admin/dashboard.css?v=20261013c">
  <script src="${pageContext.request.contextPath}/js/pages/admin/dashboard.js" defer></script>
</head>
<body class="dock theme-admin">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-admin.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Báo cáo vận hành hệ thống</h1>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">
        <%-- UC-47: cả hai ngày bắt buộc, mặc định ngày 1 tháng hiện tại tới hôm nay. --%>
        <section class="report-head" aria-labelledby="reportTitle" data-reveal>
          <div class="report-title-block">
            <h2 id="reportTitle">Báo cáo vận hành</h2>
          </div>

          <form
            class="report-filter"
            id="reportFilter"
            method="get"
            action="${pageContext.request.contextPath}/admin/dashboard"
          >
            <div class="report-filter-field">
              <label for="fromDateDisplay">Từ ngày</label>

              <div class="report-date-control">
                <input
                  class="report-date-input"
                  type="text"
                  id="fromDateDisplay"
                  value="${gms:formatDate(fromDate)}"
                  placeholder="dd/mm/yyyy"
                  autocomplete="off"
                  readonly
                  required
                >

                <button
                  type="button"
                  class="report-calendar-button"
                  data-picker-target="fromDatePicker"
                  aria-label="Chọn từ ngày"
                >
                  <i class="fa-regular fa-calendar" aria-hidden="true"></i>
                </button>

                <input
                  class="report-native-date"
                  type="date"
                  id="fromDatePicker"
                  value="${fromDate}"
                  tabindex="-1"
                >
              </div>

              <input type="hidden" id="fromDate" name="fromDate" value="${fromDate}">
            </div>

            <div class="report-filter-field">
              <label for="toDateDisplay">Đến ngày</label>

              <div class="report-date-control">
                <input
                  class="report-date-input"
                  type="text"
                  id="toDateDisplay"
                  value="${gms:formatDate(toDate)}"
                  placeholder="dd/mm/yyyy"
                  autocomplete="off"
                  readonly
                  required
                >

                <button
                  type="button"
                  class="report-calendar-button"
                  data-picker-target="toDatePicker"
                  aria-label="Chọn đến ngày"
                >
                  <i class="fa-regular fa-calendar" aria-hidden="true"></i>
                </button>

                <input
                  class="report-native-date"
                  type="date"
                  id="toDatePicker"
                  value="${toDate}"
                  tabindex="-1"
                >
              </div>

              <input type="hidden" id="toDate" name="toDate" value="${toDate}">
            </div>

            <div class="report-filter-actions">
              <%-- Chọn nhanh khoảng ngày (common.js "Chọn nhanh khoảng ngày"): điền 2 ô ẩn rồi xem luôn --%>
              <div class="range-presets" data-range-presets data-from="#fromDate" data-to="#toDate" role="group" aria-label="Chọn nhanh khoảng ngày">
                <button type="button" class="chip" data-range="7">7 ngày</button>
                <button type="button" class="chip" data-range="30">30 ngày</button>
                <button type="button" class="chip" data-range="month">Tháng này</button>
              </div>
              <button type="submit" class="btn btn-primary">
                Xem báo cáo
              </button>
              <button type="submit" class="btn btn-outline" name="action" value="exportCsv">
                Xuất CSV
              </button>
            </div>
          </form>
        </section>

        <%-- Lỗi validate khoảng ngày hoặc lỗi truy vấn hệ thống. --%>
        <c:if test="${not empty pageError}">
          <div class="report-error" role="alert">
            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
            <c:out value="${pageError}" />
          </div>
        </c:if>

        <c:if test="${not empty dashboard}">
          <c:if test="${not dashboard.hasAnyData}">
            <div class="empty-state report-empty">
              Không có dữ liệu trong khoảng thời gian đã chọn.
            </div>
          </c:if>

          <%-- Toàn bộ số liệu vận hành/doanh thu mà Admin kế thừa từ báo cáo Manager. --%>
          <section class="dashboard-section" aria-labelledby="operationTitle" data-reveal>
            <div class="section-heading">
              <div>
                <h2 id="operationTitle">Vận hành &amp; doanh thu</h2>
              </div>
            </div>

            <div class="metric-grid">
              <article class="metric-card metric-card-primary">
                <span class="metric-label">Tổng doanh thu</span>
                <strong class="metric-value tabular is-flap" data-flip><c:out value="${gms:formatMoney(dashboard.totalRevenue)}" /></strong>
                <span class="metric-note">Đã gồm VAT theo giá trị lưu trên từng đơn.</span>
              </article>

              <article class="metric-card">
                <span class="metric-label">Lượt check-in</span>
                <strong class="metric-value tabular is-flap" data-flip><c:out value="${dashboard.checkInCount}" /></strong>
                <span class="metric-note">Tổng lượt trong khoảng ngày đã chọn.</span>
              </article>

              <article class="metric-card">
                <span class="metric-label">Lượt vé ngày</span>
                <strong class="metric-value tabular is-flap" data-flip><c:out value="${dashboard.dayPassSummary.count}" /></strong>
                <span class="metric-note">Giao dịch vé ngày thành công.</span>
              </article>

              <article class="metric-card">
                <span class="metric-label">Doanh thu vé ngày</span>
                <strong class="metric-value tabular is-flap" data-flip><c:out value="${gms:formatMoney(dashboard.dayPassSummary.revenue)}" /></strong>
                <span class="metric-note">Giao dịch vé ngày thành công trong kỳ.</span>
              </article>
            </div>

            <div class="report-stack">
              <article class="report-card">
                <div class="card-heading">
                  <div>
                    <h3>Doanh thu theo từng gói</h3>
                  </div>
                </div>

                <c:choose>
                  <c:when test="${not empty dashboard.revenueByPackage}">
                    <div class="table-wrap">
                      <table class="report-table">
                        <thead>
                          <tr>
                            <th>Tên gói</th>
                            <th class="number-cell">Giao dịch</th>
                            <th class="number-cell">Doanh thu</th>
                            <th class="number-cell">Trong đó VAT</th>
                          </tr>
                        </thead>
                        <tbody>
                          <c:forEach var="row" items="${dashboard.revenueByPackage}">
                            <tr>
                              <td><c:out value="${row.packageName}" /></td>
                              <td class="number-cell tabular"><c:out value="${row.transactionCount}" /></td>
                              <td class="number-cell tabular"><c:out value="${gms:formatMoney(row.revenue)}" /></td>
                              <td class="number-cell tabular"><c:out value="${gms:formatMoney(row.vatAmount)}" /></td>
                            </tr>
                          </c:forEach>
                        </tbody>
                      </table>
                    </div>
                  </c:when>
                  <c:otherwise>
                    <div class="empty-state">Không có giao dịch thành công trong khoảng thời gian đã chọn.</div>
                  </c:otherwise>
                </c:choose>
              </article>

              <article class="report-card">
                <div class="card-heading">
                  <div>
                    <h3>Hoa hồng theo PT</h3>
                  </div>
                </div>

                <c:choose>
                  <c:when test="${not empty dashboard.ptCommissions}">
                    <div class="table-wrap">
                      <table class="report-table">
                        <thead>
                          <tr>
                            <th>Mã PT</th>
                            <th>Họ tên</th>
                            <th class="number-cell">Buổi được tính</th>
                            <th class="number-cell">Tổng hoa hồng</th>
                          </tr>
                        </thead>
                        <tbody>
                          <c:forEach var="row" items="${dashboard.ptCommissions}">
                            <tr>
                              <td><c:out value="${row.employeeCode}" /></td>
                              <td><c:out value="${row.fullName}" /></td>
                              <td class="number-cell tabular"><c:out value="${row.sessionCount}" /></td>
                              <td class="number-cell tabular"><c:out value="${gms:formatMoney(row.commissionAmount)}" /></td>
                            </tr>
                          </c:forEach>
                        </tbody>
                      </table>
                    </div>
                  </c:when>
                  <c:otherwise>
                    <div class="empty-state">Không có buổi PT phát sinh hoa hồng trong khoảng thời gian đã chọn.</div>
                  </c:otherwise>
                </c:choose>
              </article>
            </div>
          </section>

          <%-- Audit ở Dashboard chỉ là metrics tổng hợp; tra cứu log chi tiết nằm ở UC-08. --%>
          <section class="dashboard-section audit-section" aria-labelledby="auditTitle" data-reveal>
            <div class="section-heading audit-heading">
              <div>
                <h2 id="auditTitle">
                  <i class="fa-solid fa-shield-halved" aria-hidden="true"></i>
                  Audit &amp; bảo mật
                </h2>
              </div>

              <a class="btn btn-outline" href="${pageContext.request.contextPath}/admin/audit-log">
                Mở Audit Log
              </a>
            </div>

            <div class="audit-summary-grid">
              <article class="report-card compact-card">
                <div class="card-heading">
                  <div>
                    <h3>Hoạt động đăng nhập</h3>
                  </div>
                </div>

                <dl class="stat-list">
                  <div>
                    <dt>Đăng nhập thành công</dt>
                    <dd class="tabular"><c:out value="${dashboard.loginAuditActions.SignIn}" /></dd>
                  </div>
                  <div>
                    <dt>Đăng nhập thất bại</dt>
                    <dd class="tabular"><c:out value="${dashboard.loginAuditActions.SignInFailed}" /></dd>
                  </div>
                  <div>
                    <dt>Khóa tạm do đăng nhập sai</dt>
                    <dd class="tabular"><c:out value="${dashboard.loginAuditActions.AccountLocked}" /></dd>
                  </div>
                </dl>
              </article>

              <article class="metric-card audit-highlight">
                <span class="metric-label">Tài khoản bị khóa</span>
                <strong class="metric-value tabular is-flap" data-flip><c:out value="${dashboard.lockedAccountCount}" /></strong>
                <span class="metric-note">Gồm khóa tạm do sai mật khẩu và tài khoản bị Admin khóa trong kỳ.</span>
              </article>

              <article class="metric-card audit-highlight">
                <span class="metric-label">Thay đổi tài khoản</span>
                <strong class="metric-value tabular is-flap" data-flip><c:out value="${dashboard.accountAuditChangeCount}" /></strong>
                <span class="metric-note">Audit Log có EntityName = Account.</span>
              </article>
            </div>

            <div class="audit-detail-grid">
              <article class="report-card">
                <div class="card-heading">
                  <div>
                    <h3>Thao tác nhạy cảm theo loại</h3>
                  </div>
                </div>

                <c:choose>
                  <c:when test="${not empty dashboard.sensitiveAuditLogsByAction}">
                    <div class="table-wrap">
                      <table class="report-table">
                        <thead>
                          <tr>
                            <th>Action</th>
                            <th class="number-cell">Số lần</th>
                          </tr>
                        </thead>
                        <tbody>
                          <c:forEach var="entry" items="${dashboard.sensitiveAuditLogsByAction}">
                            <tr>
                              <td><c:out value="${entry.key}" /></td>
                              <td class="number-cell tabular"><c:out value="${entry.value}" /></td>
                            </tr>
                          </c:forEach>
                        </tbody>
                      </table>
                    </div>
                  </c:when>
                  <c:otherwise>
                    <div class="empty-state">Không có thao tác nhạy cảm trong khoảng thời gian đã chọn.</div>
                  </c:otherwise>
                </c:choose>
              </article>

              <article class="report-card">
                <div class="card-heading">
                  <div>
                    <h3>Thao tác nhạy cảm theo người thực hiện</h3>
                  </div>
                </div>

                <c:choose>
                 <c:when test="${not empty dashboard.sensitiveAuditActors}">
                    <div class="table-wrap">
                      <table class="report-table">
                        <thead>
                          <tr>
                            <th>Người thực hiện</th>
                            <th>Vai trò</th>
                            <th class="number-cell">Số lần</th>
                          </tr>
                        </thead>
                        <tbody>
                          <c:forEach var="row" items="${dashboard.sensitiveAuditActors}">
                            <tr>
                              <td>
                                <c:out value="${row.actorName}" />
                                <c:if test="${not empty row.accountId}">
                                  <small class="account-id">#<c:out value="${row.accountId}" /></small>
                                </c:if>
                              </td>
                              <td><c:out value="${row.actorRole}" /></td>
                              <td class="number-cell tabular"><c:out value="${row.total}" /></td>
                            </tr>
                          </c:forEach>
                        </tbody>
                      </table>
                    </div>
                  </c:when>
                  <c:otherwise>
                    <div class="empty-state">Chưa có thao tác nhạy cảm trong khoảng thời gian đã chọn.</div>
                  </c:otherwise>
                </c:choose>
              </article>
            </div>
          </section>
        </c:if>
      </main>
    </div>
  </div>
</body>
</html>
