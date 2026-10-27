<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<c:set var="isReceptionist" value="${sessionScope.role eq 'Receptionist' or formView.position eq 'Receptionist'}" />

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css?v=20261018">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css?v=20261025-compact">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css?v=20261018">
  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
</head>

<body class="dock staff-leave-page ${isReceptionist ? 'theme-staff' : 'theme-pt'}">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%-- ===== Sidebar dock theo role PT / Lễ tân ===== --%>
    <c:set var="activeNav" value="leaves" />
    <c:choose>
      <c:when test="${isReceptionist}">
        <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
      </c:when>
      <c:otherwise>
        <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
      </c:otherwise>
    </c:choose>

    <div class="main">
      <%-- ===== Topbar đồng bộ chuẩn thiết kế có chuông thông báo ===== --%>
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Đơn nghỉ phép</h1>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">
        <%-- Thông báo lỗi nộp đơn từ server + Nút dẫn sang màn xử lý lịch dạy (BF-08 / CBR-83) --%>
        <c:if test="${not empty submitError}">
          <div class="alert-banner danger" style="margin-bottom:var(--space-4);display:flex;align-items:center;justify-content:space-between;gap:var(--space-3);flex-wrap:wrap">
            <div>
              <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
              <c:out value="${submitError}" />
            </div>
            <c:if test="${not isReceptionist and not empty fieldErrors.sessions}">
              <a class="btn btn-sm btn-outline" href="${pageContext.request.contextPath}/pt/dashboard">
                Xem lịch dạy →
              </a>
            </c:if>
          </div>
        </c:if>

        <c:if test="${not empty formError}">
          <div class="alert-banner danger" style="margin-bottom:var(--space-4)">
            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
            <c:out value="${formError}" />
          </div>
        </c:if>

        <%-- Form và lịch sử một cột (formView: StaffLeaveFormView do Service tạo). --%>
        <div class="leave-staff-content">
            <%-- ===== Panel 1: Form nộp đơn xin nghỉ phép ===== --%>
            <section class="panel leave-panel">
              <div class="panel-head leave-form-heading">
                <div>
                  <h2>Tạo đơn nghỉ phép</h2>
                  <p>Điền thông tin ngày, ca nghỉ và lý do để gửi Quản lý phê duyệt.</p>
                </div>
                <%-- HintBox có sẵn trong common.css/common.js: hover, focus, chạm và Escape. --%>
                <div class="pt-info">
                  <button type="button" class="pt-info-trigger" aria-label="Thông tin gửi đơn nghỉ phép"
                          aria-describedby="leaveFormHelp">
                    <span aria-hidden="true">i</span>
                  </button>
                  <div id="leaveFormHelp" class="pt-info-popover" role="tooltip">
                    <strong class="leave-help-title">Hạn nộp</strong>
                    <p class="leave-help-row"><span>Ngày nghỉ:</span> <b id="infoDate">—</b></p>
                    <p class="leave-help-row"><span>Nộp trước:</span> <b id="infoDeadline"><c:out value="${formView.submissionCutoffTime}" default="—" /> · ngày hôm trước</b></p>
                    <p class="leave-help-note">Quá hạn hoặc nghỉ đột xuất: báo Quản lý.</p>

                    <strong class="leave-help-title">Nhân sự còn lại ít nhất nếu duyệt</strong>
                    <p id="pvScope" class="leave-help-note">Ngày mai · Cả ngày</p>
                    <p class="leave-help-row">
                      <span>PT:</span>
                      <b><span id="pvPt"><c:out value="${formView.staffing.remainingPt}" default="—" /></span> (tối thiểu <span id="pvMinPt"><c:out value="${formView.staffing.minPt}" default="—" /></span>)</b>
                    </p>
                    <p class="leave-help-row">
                      <span>Lễ tân:</span>
                      <b><span id="pvRecep"><c:out value="${formView.staffing.remainingReceptionist}" default="—" /></span> (tối thiểu <span id="pvMinRecep"><c:out value="${formView.staffing.minReceptionist}" default="—" /></span>)</b>
                    </p>
                    <p class="leave-help-note">Xét toàn khoảng nghỉ; tạm tính nhân viên làm cả hai ca.</p>
                  </div>
                </div>
              </div>

              <c:set var="submitActionUrl" value="${pageContext.request.contextPath}${isReceptionist ? '/receptionist/leave-requests' : '/pt/leave-requests'}" />
              <form action="${submitActionUrl}" method="post" id="leaveForm"
                    data-preview-url="${submitActionUrl}/preview">
                <input type="hidden" name="csrfToken" value="${csrfToken}">

                <div class="leave-form-grid">
                  <div class="field">
                    <label class="field-label" for="leaveType">
                      Loại nghỉ <span aria-hidden="true">*</span>
                    </label>
                    <select id="leaveType" name="leaveType" required>
                      <option value="Annual" ${paramLeaveType eq 'Annual' ? 'selected' : ''}>Nghỉ phép năm</option>
                      <option value="Unpaid" ${paramLeaveType eq 'Unpaid' ? 'selected' : ''}>Nghỉ không lương</option>
                    </select>
                    <c:if test="${not empty fieldErrors.leaveType}">
                      <div class="field-error">
                        <c:out value="${fieldErrors.leaveType}" />
                      </div>
                    </c:if>
                  </div>

                  <div class="field">
                    <label class="field-label" for="shiftType">
                      Ca nghỉ <span aria-hidden="true">*</span>
                    </label>
                    <select id="shiftType" name="shiftType" required>
                      <option value="FullDay" ${paramShiftType eq 'FullDay' ? 'selected' : ''}>Cả ngày</option>
                      <option value="Morning" ${paramShiftType eq 'Morning' ? 'selected' : ''}>Ca sáng</option>
                      <option value="Afternoon" ${paramShiftType eq 'Afternoon' ? 'selected' : ''}>Ca chiều</option>
                    </select>
                    <c:if test="${not empty fieldErrors.shiftType}">
                      <div class="field-error">
                        <c:out value="${fieldErrors.shiftType}" />
                      </div>
                    </c:if>
                    <span class="field-hint">Ca sáng, ca chiều hoặc cả ngày đều tính 1 ngày nghỉ.</span>
                  </div>
                </div>

                <div class="field" style="margin-bottom:var(--space-4)">
                  <label class="field-label" for="leaveDate">
                    Từ ngày <span aria-hidden="true">*</span>
                  </label>
                  <input id="leaveDate"
                         name="leaveDate"
                         type="date"
                         data-cutoff="<c:out value='${formView.submissionCutoffTime}' default='' />"
                         value="<c:out value='${paramLeaveDate}' />"
                         required>
                  <div class="field-error" id="leaveDateClientErr" hidden></div>
                  <c:if test="${not empty fieldErrors.leaveDate}">
                    <div class="field-error">
                      <c:out value="${fieldErrors.leaveDate}" />
                    </div>
                  </c:if>
                </div>

                <div class="field">
                  <label class="field-label" for="toDate">Đến ngày (bỏ trống nếu nghỉ một ngày)</label>
                  <input id="toDate" name="toDate" type="date" value="<c:out value='${paramToDate}' />">
                  <span class="field-hint">Đơn nhiều ngày chọn Cả ngày, tối đa 31 ngày trong cùng năm.</span>
                  <c:if test="${not empty fieldErrors.toDate}">
                    <div class="field-error"><c:out value="${fieldErrors.toDate}" /></div>
                  </c:if>
                </div>

                <div class="field" style="margin-bottom:var(--space-5)">
                  <label class="field-label" for="leaveReason">
                    Lý do nghỉ <span aria-hidden="true">*</span>
                  </label>
                  <textarea id="leaveReason"
                            name="reason"
                            rows="4"
                            minlength="5"
                            maxlength="500"
                            placeholder="Nêu ngắn gọn lý do nghỉ (5–500 ký tự)…"
                            required><c:out value="${paramReason}" /></textarea>
                  <c:if test="${not empty fieldErrors.reason}">
                    <div class="field-error">
                      <c:out value="${fieldErrors.reason}" />
                    </div>
                  </c:if>
                </div>

                <%-- JSON preview chỉ dự báo; Service tính lại cùng phương án trong transaction khi POST. --%>
                <div id="leavePlan" class="leave-submission-plan" aria-live="polite" hidden>
                  <b id="leavePlanTitle">Dự kiến gửi</b>
                  <div id="leavePlanParts"></div>
                  <small id="leaveBalanceText"></small>
                  <label id="unpaidConsentRow" class="leave-consent" hidden>
                    <input type="checkbox" id="acceptUnpaid" name="acceptUnpaid" value="true">
                    <span>Tôi đồng ý gửi phần vượt phép thành đơn không lương.</span>
                  </label>
                  <small id="unpaidConsentHint" hidden>Không đồng ý: giảm số ngày nghỉ hoặc đổi loại nghỉ.</small>
                  <input type="hidden" id="expectedAnnualDays" name="expectedAnnualDays" value="-1">
                  <input type="hidden" id="expectedUnpaidDays" name="expectedUnpaidDays" value="-1">
                  <c:if test="${not empty fieldErrors.acceptUnpaid}">
                    <div class="field-error"><c:out value="${fieldErrors.acceptUnpaid}" /></div>
                  </c:if>
                  <p id="leavePlanError" class="field-error" hidden></p>
                  <a id="leaveScheduleLink" class="btn btn-sm btn-outline" href="${pageContext.request.contextPath}/pt/dashboard" hidden>Xem lịch dạy →</a>
                </div>

                <button class="btn btn-primary" type="submit" id="leaveSubmit">
                  <i class="fa-solid fa-paper-plane" aria-hidden="true"></i> Nộp đơn nghỉ phép
                </button>
              </form>
            </section>

            <%-- ===== Panel 2: Lịch sử theo đợt nghỉ (formView.requestGroups do Service tạo) ===== --%>
            <section class="panel leave-panel">
              <div class="panel-head" style="display:flex;align-items:center;justify-content:space-between">
                <div>
                  <h2>Đơn đã nộp</h2>
                  <p>Tất cả đơn đang chờ và 10 đơn đã xử lý gần nhất.</p>
                </div>
              </div>

              <c:choose>
                <c:when test="${empty formView.requestGroups}">
                  <div class="leave-empty">Bạn chưa có đơn xin nghỉ phép nào được nộp.</div>
                </c:when>
                <c:otherwise>
                  <div class="leave-history-list">
                    <c:forEach var="group" items="${formView.requestGroups}">
                      <div class="leave-history-group">
                        <c:if test="${group.linked}">
                          <div class="leave-group-heading">
                            <b>Đợt <c:out value="${group.code}" /></b>
                            <span>Được duyệt <fmt:formatNumber value="${group.approvedDays}" maxFractionDigits="1" />/<fmt:formatNumber value="${group.totalDays}" maxFractionDigits="1" /> ngày</span>
                          </div>
                        </c:if>
                    <c:forEach var="req" items="${group.requests}">
                      <div class="leave-history-row">
                        <div style="flex:1">
                          <div style="display:flex;align-items:center;gap:var(--space-2);margin-bottom:var(--space-1);flex-wrap:wrap">
                            <b>${gms:formatDate(req.fromDate)}<c:if test="${req.fromDate != req.toDate}">–${gms:formatDate(req.toDate)}</c:if> · <fmt:formatNumber value="${req.days}" maxFractionDigits="1" /> ngày</b>
                            <span style="color:var(--color-text-soft);font-size:var(--font-size-xs)">
                              · <c:out value="${req.shiftType.displayName}" />
                            </span>
                            <c:if test="${req.submittedAt != null}">
                              <span style="color:var(--color-text-soft);font-size:var(--font-size-xs)">
                                · Nộp lúc ${gms:formatDateTime(req.submittedAt)}
                              </span>
                            </c:if>
                          </div>
                          <div style="font-size:var(--font-size-xs);color:var(--color-text-soft);display:flex;flex-wrap:wrap;gap:var(--space-3);margin-bottom:var(--space-1)">
                            <span><i class="fa-solid fa-tag" aria-hidden="true"></i> <c:out value="${req.leaveType.displayName}" /></span>
                            <span><i class="fa-solid fa-quote-left" aria-hidden="true"></i> <c:out value="${req.reason}" /></span>
                          </div>
                          <c:if test="${req.rejected and not empty req.rejectReason}">
                            <div style="font-size:var(--font-size-xs);color:var(--color-danger)">
                              <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i> Lý do từ chối: <c:out value="${req.rejectReason}" />
                            </div>
                          </c:if>
                        </div>

                        <div style="white-space:nowrap;display:flex;align-items:center;gap:var(--space-2)">
                          <c:choose>
                              <c:when test="${req.pending}"><c:set var="requestStatusClass" value="badge-warning" /></c:when>
                              <c:when test="${req.approved}"><c:set var="requestStatusClass" value="badge-success" /></c:when>
                              <c:otherwise><c:set var="requestStatusClass" value="badge-danger" /></c:otherwise>
                          </c:choose>
                          <span class="badge ${requestStatusClass}">
                            <c:out value="${req.status.displayName}" />
                          </span>
                          <c:if test="${req.pending}">
                            <form action="${submitActionUrl}" method="post" style="display:inline;margin:0">
                              <input type="hidden" name="csrfToken" value="${csrfToken}">
                              <input type="hidden" name="action" value="withdraw">
                              <input type="hidden" name="leaveRequestId" value="${req.leaveRequestId}">
                              <button type="submit" class="btn btn-sm btn-outline danger"
                                      data-confirm-title="Rút đơn nghỉ phép"
                                      data-confirm-message="Xác nhận rút đơn nghỉ ${gms:formatDate(req.fromDate)}–${gms:formatDate(req.toDate)} (${req.days} ngày)?">
                                <i class="fa-solid fa-rotate-left" aria-hidden="true"></i> Rút đơn
                              </button>
                            </form>
                          </c:if>
                        </div>
                      </div>
                    </c:forEach>
                      </div>
                    </c:forEach>
                  </div>
                </c:otherwise>
              </c:choose>
            </section>
        </div>
      </main>
    </div>
  </div>

  <script src="${pageContext.request.contextPath}/js/pages/staff/leave-request-form.js?v=20261025-readable"></script>
</body>
</html>
