<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<c:set var="isReceptionist" value="${sessionScope.role eq 'Receptionist' or formView.position eq 'Receptionist'}" />
<c:set var="historyTab" value="${param.tab eq 'history' and empty submitError and empty fieldErrors}" />
<c:set var="submitActionUrl" value="${pageContext.request.contextPath}${isReceptionist ? '/receptionist/leave-requests' : '/pt/leave-requests'}" />

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css?v=20261018">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css?v=20261109-history-pages">
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
        <c:if test="${not empty submitError and (isReceptionist or empty fieldErrors.sessions)}">
          <div class="alert-banner danger" style="margin-bottom:var(--space-4);display:flex;align-items:center;justify-content:space-between;gap:var(--space-3);flex-wrap:wrap">
            <div>
              <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
              <c:out value="${submitError}" />
            </div>
            <c:if test="${not isReceptionist and not empty fieldErrors.sessions}">
              <a class="btn btn-sm btn-outline" href="${pageContext.request.contextPath}/pt/schedule">
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

        <%-- Hai tab dùng chung Servlet; tham số tab chỉ chọn phần cần hiển thị. --%>
        <div class="leave-staff-content">
            <nav class="leave-staff-tabs" aria-label="Đơn nghỉ phép">
              <a href="${submitActionUrl}" class="leave-staff-tab ${historyTab ? '' : 'is-active'}"
                 aria-current="${historyTab ? 'false' : 'page'}">Tạo đơn nghỉ phép</a>
              <a href="${submitActionUrl}?tab=history" class="leave-staff-tab ${historyTab ? 'is-active' : ''}"
                 aria-current="${historyTab ? 'page' : 'false'}">Đơn đã nộp</a>
            </nav>
            <c:if test="${not historyTab}">
            <%-- ===== Panel 1: Form nộp đơn xin nghỉ phép ===== --%>
            <section class="panel leave-panel">
              <div class="panel-head leave-form-heading">
                <div>
                  <h2>Tạo đơn nghỉ phép</h2>
                </div>
                <%-- HintBox có sẵn trong common.css/common.js: hover, focus, chạm và Escape. --%>
                <div class="pt-info">
                  <button type="button" class="pt-info-trigger" aria-label="Thông tin gửi đơn nghỉ phép"
                          aria-describedby="leaveFormHelp">
                    <span aria-hidden="true">i</span>
                  </button>
                  <div id="leaveFormHelp" class="pt-info-popover" role="tooltip">
                    <p class="leave-help-note">Nghỉ một ngày: để trống Đến ngày.</p>
                    <p class="leave-help-note">Nghỉ nhiều ngày: chọn Cả ngày, tối đa 31 ngày trong cùng năm.</p>
                    <p class="leave-help-note">Ca sáng, ca chiều hoặc cả ngày đều tính 1 ngày nghỉ.</p>
                    <p class="leave-help-note">Gửi đơn trước <c:out value="${formView.submissionCutoffTime}" default="giờ chốt" /> ngày liền trước ngày nghỉ.</p>
                    <p class="leave-help-note">Nghỉ đột xuất hoặc quá hạn: liên hệ quản lý.</p>
                    <p class="leave-help-note">Đơn chỉ có hiệu lực sau khi được duyệt.</p>
                  </div>
                </div>
              </div>

              <form action="${submitActionUrl}" method="post" id="leaveForm">
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
                  </div>
                </div>

                <div class="leave-form-grid leave-date-grid">
                  <div class="field">
                    <label class="field-label" for="leaveDate">
                      Từ ngày <span aria-hidden="true">*</span>
                    </label>
                    <input id="leaveDate"
                           name="leaveDate"
                           type="date"
                           data-cutoff="<c:out value='${formView.submissionCutoffTime}' default='' />"
                           value="<c:out value='${paramLeaveDate}' />"
                           required>
                    <c:if test="${not empty fieldErrors.leaveDate}">
                      <div class="field-error">
                        <c:out value="${fieldErrors.leaveDate}" />
                      </div>
                    </c:if>
                  </div>

                  <div class="field">
                    <label class="field-label" for="toDate">Đến ngày</label>
                    <input id="toDate" name="toDate" type="date" value="<c:out value='${paramToDate}' />">

                    <c:if test="${not empty fieldErrors.toDate}">
                      <div class="field-error"><c:out value="${fieldErrors.toDate}" /></div>
                    </c:if>
                  </div>

                </div>
                <%-- JSP in các buổi dạy trùng do Service trả về. --%>
                <c:if test="${not isReceptionist and (preview.sessionConflicts gt 0 or not empty fieldErrors.sessions)}">
                  <div id="leaveScheduleWarning" class="leave-schedule-warning" role="alert">
                    <span><i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
                      <c:choose>
                        <c:when test="${not empty fieldErrors.sessions}"><c:out value="${fieldErrors.sessions}" /></c:when>
                        <c:otherwise>Trùng ${preview.sessionConflicts} buổi dạy. Xử lý lịch trước khi gửi đơn.</c:otherwise>
                      </c:choose>
                    </span>
                    <a id="leaveScheduleLink" class="btn btn-sm btn-outline" href="${pageContext.request.contextPath}/pt/schedule">Xem lịch dạy →</a>
                    <ul class="leave-conflicting-sessions">
                      <c:forEach var="session" items="${preview.conflictingSessions}">
                        <li>${gms:formatDate(session.slotDate)} · ${session.startTime}–${session.endTime}</li>
                      </c:forEach>
                    </ul>
                  </div>
                </c:if>
                <div class="field leave-reason-field">
                  <label class="field-label" for="leaveReason">
                    Lý do nghỉ <span aria-hidden="true">*</span>
                  </label>
                  <textarea id="leaveReason"
                            name="reason"
                            rows="3"
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

                <%-- Xem dự kiến dùng POST rồi forward về JSP; chỉ nút Nộp đơn mới lưu. --%>
                <c:if test="${not empty preview}">
                <div id="leavePlan" class="leave-submission-plan">
                  <b>Dự kiến gửi ${previewPartCount} đơn<c:if test="${previewPartCount eq 2}"> cùng đợt</c:if></b>
                  <div id="leavePlanParts">
                    <c:if test="${preview.annualDays gt 0}">
                      <div class="leave-plan-part">
                        <b>Phép năm · ${preview.annualDays} ngày</b>
                        <span>${gms:formatDate(preview.fromDate)} → ${gms:formatDate(annualToDate)}</span>
                      </div>
                    </c:if>
                    <c:if test="${preview.unpaidDays gt 0}">
                      <div class="leave-plan-part">
                        <b>Không lương · ${preview.unpaidDays} ngày</b>
                        <span>${gms:formatDate(unpaidFromDate)} → ${gms:formatDate(preview.toDate)}</span>
                      </div>
                    </c:if>
                  </div>
                  <small id="leaveBalanceText">
                    <c:choose>
                      <c:when test="${paramLeaveType eq 'Annual'}">Còn ${preview.remainingAnnualDays} ngày phép năm (đã tính các đơn đang chờ).</c:when>
                      <c:otherwise>Không sử dụng quỹ phép năm.</c:otherwise>
                    </c:choose>
                  </small>
                  <c:if test="${preview.needsUnpaidConsent}">
                    <label class="leave-consent">
                      <input type="checkbox" id="acceptUnpaid" name="acceptUnpaid" value="true">
                      <span>Tôi đồng ý gửi phần vượt phép thành đơn không lương.</span>
                    </label>
                    <small>Không đồng ý: giảm số ngày nghỉ hoặc đổi loại nghỉ.</small>
                  </c:if>
                  <input type="hidden" name="expectedAnnualDays" value="${preview.annualDays}">
                  <input type="hidden" name="expectedUnpaidDays" value="${preview.unpaidDays}">
                  <input type="hidden" name="previewSelection" value="<c:out value='${previewSelection}' />">
                  <c:if test="${not empty fieldErrors.acceptUnpaid}">
                    <div class="field-error"><c:out value="${fieldErrors.acceptUnpaid}" /></div>
                  </c:if>
                </div>
                </c:if>

                <button class="btn btn-outline" type="submit" name="action" value="preview" formnovalidate>
                  Xem dự kiến
                </button>
                <button class="btn btn-primary" type="submit" name="action" value="submit" id="leaveSubmit"
                        ${empty preview or preview.sessionConflicts gt 0 ? 'disabled' : ''}>
                  <i class="fa-solid fa-paper-plane" aria-hidden="true"></i> Nộp đơn nghỉ phép
                </button>
              </form>
            </section>
            </c:if>

            <c:if test="${historyTab}">
            <%-- ===== Panel 2: Lịch sử theo đợt nghỉ (formView.requestGroups do Service tạo) ===== --%>
            <section class="panel leave-panel">
              <div class="panel-head" style="display:flex;align-items:center;justify-content:space-between">
                <div>
                  <h2>Đơn đã nộp</h2>
                </div>
              </div>

              <c:choose>
                <c:when test="${empty formView.requestGroups}">
                  <div class="leave-empty">Bạn chưa có đơn xin nghỉ phép nào được nộp.</div>
                </c:when>
                <c:otherwise>
                  <div class="leave-history-list">
                    <c:forEach var="group" items="${formView.requestGroups}">
                      <div class="leave-history-group ${group.linked ? 'is-linked' : ''}">
                        <%-- group.linked = getter tự tính isLinked() · dto/LeaveRequestGroupView.java (group = LeaveRequestGroupView) --%>
                        <c:if test="${group.linked}">
                          <div class="leave-group-heading">
                            <div>
                              <b>Cùng đợt nghỉ · <fmt:formatNumber value="${group.totalDays}" maxFractionDigits="1" /> ngày</b>
                              <small class="leave-group-code">Mã đợt: <c:out value="${group.code}" /></small>
                            </div>
                            <%-- getter tự tính: group.totalDays = getTotalDays(), group.approvedDays = getApprovedDays() · dto/LeaveRequestGroupView.java --%>
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
                              <input type="hidden" name="page" value="${formView.historyPage.pageNumber}">
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
              <c:if test="${not empty formView}">
                <nav class="leave-history-pagination" aria-label="Phân trang đơn đã nộp">
                  <c:choose>
                    <c:when test="${formView.historyPage.hasPrevious}">
                      <a class="btn btn-sm btn-outline" href="${submitActionUrl}?tab=history&amp;page=${formView.historyPage.pageNumber - 1}">Trang trước</a>
                    </c:when>
                    <c:otherwise><span class="btn btn-sm btn-outline" aria-disabled="true">Trang trước</span></c:otherwise>
                  </c:choose>
                  <span>Trang ${formView.historyPage.pageNumber}/${formView.historyPage.totalPages} · ${formView.historyPage.totalItems} đợt nghỉ / đơn lẻ</span>
                  <c:choose>
                    <c:when test="${formView.historyPage.hasNext}">
                      <a class="btn btn-sm btn-outline" href="${submitActionUrl}?tab=history&amp;page=${formView.historyPage.pageNumber + 1}">Trang sau</a>
                    </c:when>
                    <c:otherwise><span class="btn btn-sm btn-outline" aria-disabled="true">Trang sau</span></c:otherwise>
                  </c:choose>
                </nav>
              </c:if>
            </section>
            </c:if>
        </div>
      </main>
    </div>
  </div>

</body>
</html>
