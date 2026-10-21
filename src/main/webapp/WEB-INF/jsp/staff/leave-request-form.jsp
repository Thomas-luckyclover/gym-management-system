<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<c:set var="isReceptionist" value="${sessionScope.role eq 'Receptionist' or formView.position eq 'Receptionist'}" />

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css?v=20261018">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css?v=20261021-readable2">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css?v=20261018">
  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
</head>

<body class="dock ${isReceptionist ? 'theme-staff' : 'theme-pt'}">
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

          <a class="tb-user" href="${pageContext.request.contextPath}/account">
            <span class="avatar">${isReceptionist ? 'LT' : 'PT'}</span>
            <span>
              <b><c:out value="${not empty formView.fullName ? formView.fullName : sessionScope.displayName}" default="Nhân viên" /></b>
              <small>
                ${isReceptionist ? 'Lễ tân' : 'Huấn luyện viên'}
                <c:if test="${not empty formView.employeeCode}">
                  · <c:out value="${formView.employeeCode}" />
                </c:if>
              </small>
            </span>
          </a>
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
            <c:if test="${not isReceptionist}">
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

        <%-- Layout 2 cột cho màn nộp đơn (formView: StaffLeaveFormView do Service tạo) --%>
        <div class="leave-split">
          <div class="leave-col-main">
            <%-- ===== Panel 1: Form nộp đơn xin nghỉ phép ===== --%>
            <section class="panel leave-panel">
              <div class="panel-head">
                <h2>Tạo đơn nghỉ phép</h2>
                <p>Điền thông tin ngày, ca nghỉ và lý do để gửi Quản lý phê duyệt.</p>
              </div>

              <c:set var="submitActionUrl" value="${pageContext.request.contextPath}${isReceptionist ? '/receptionist/leave-requests' : '/pt/leave-requests'}" />
              <form action="${submitActionUrl}" method="post" id="leaveForm" novalidate
                    data-preview-url="${submitActionUrl}/preview">
                <input type="hidden" name="csrfToken" value="${csrfToken}">

                <div class="leave-form-grid">
                  <div class="field">
                    <label class="field-label" for="leaveType">
                      Loại nghỉ <span aria-hidden="true">*</span>
                    </label>
                    <select id="leaveType" name="leaveType" required>
                      <option value="Annual" ${paramLeaveType eq 'Annual' ? 'selected' : ''}>Nghỉ phép năm</option>
                      <option value="Sick" ${paramLeaveType eq 'Sick' ? 'selected' : ''}>Nghỉ ốm</option>
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
                  <span class="field-hint" style="display:block;margin-top:var(--space-1);color:var(--color-text-soft);font-size:var(--font-size-xs)">
                    <i class="fa-solid fa-circle-info" aria-hidden="true"></i>
                    Đơn phải nộp trước giờ chốt (<c:out value="${formView.submissionCutoffTime}" default="—" />) của ngày hôm trước. Nghỉ đột xuất cùng ngày vui lòng báo trực tiếp Quản lý.
                  </span>
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
                  <span class="field-hint">Đơn nhiều ngày chọn Cả ngày, tối đa 31 ngày trong cùng năm. Dự báo quân số bên cạnh chỉ xét ngày bắt đầu.</span>
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

                <button class="btn btn-primary" type="submit" id="leaveSubmit">
                  <i class="fa-solid fa-paper-plane" aria-hidden="true"></i> Nộp đơn nghỉ phép
                </button>
              </form>
            </section>

            <%-- ===== Panel 2: Danh sách đơn đã nộp gần đây (formView.myRequests) ===== --%>
            <section class="panel leave-panel">
              <div class="panel-head" style="display:flex;align-items:center;justify-content:space-between">
                <div>
                  <h2>Đơn đã nộp gần đây</h2>
                  <p>Hiển thị tối đa 10 đơn xin nghỉ phép mới nhất của bạn.</p>
                </div>
              </div>

              <c:choose>
                <c:when test="${empty formView.myRequests}">
                  <div class="leave-empty">Bạn chưa có đơn xin nghỉ phép nào được nộp.</div>
                </c:when>
                <c:otherwise>
                  <div class="leave-history-list">
                    <c:forEach var="req" items="${formView.myRequests}">
                      <div class="leave-history-row">
                        <div style="flex:1">
                          <div style="display:flex;align-items:center;gap:var(--space-2);margin-bottom:var(--space-1);flex-wrap:wrap">
                            <b>${gms:formatDate(req.fromDate)}<c:if test="${req.fromDate != req.toDate}">–${gms:formatDate(req.toDate)}</c:if> · ${req.days} ngày</b>
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
                          <span class="badge ${req.pending ? 'badge-warning' : req.approved ? 'badge-success' : 'badge-danger'}">
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
                </c:otherwise>
              </c:choose>
            </section>
          </div>

          <%-- ===== Cột phải: Staffing preview theo SRS 5.2.1 (Hạn nộp P-12 & Quân số trực ca dự kiến) ===== --%>
          <div class="leave-col-side">
            <div class="leave-side-card">
              <span class="leave-side-title">Hạn nộp đơn</span>
              <div class="leave-side-row">
                <span style="color:var(--color-text-soft)">Ngày nghỉ đang chọn:</span>
                <b id="infoDate">—</b>
              </div>
              <div class="leave-side-row" style="margin-bottom:var(--space-3)">
                <span style="color:var(--color-text-soft)">Hạn nộp trước:</span>
                <b id="infoDeadline"><c:out value="${formView.submissionCutoffTime}" default="—" /> · ngày hôm trước</b>
              </div>
              <p style="margin:0;font-size:var(--font-size-xs);color:var(--color-text-soft);line-height:1.5">
                Quá giờ chốt trên, nếu nghỉ đột xuất vui lòng báo trực tiếp Quản lý.
              </p>
            </div>

            <div class="leave-side-card">
              <span class="leave-side-title">Dự báo nhân sự</span>
              <p id="pvScope" style="margin:0 0 var(--space-2);font-size:var(--font-size-xs);color:var(--color-text-soft)">
                Mặc định tính cho ngày mai, cả ngày. Chọn ngày và ca nghỉ để xem số liệu đúng ca.
              </p>
              <div class="leave-side-row">
                <span style="color:var(--color-text-soft)">PT còn trực (nếu duyệt):</span>
                <b>
                  <span id="pvPt"><c:choose><c:when test="${not empty formView}"><c:out value="${formView.remainingPt}" /></c:when><c:otherwise>—</c:otherwise></c:choose></span> PT
                  <small style="font-weight:400;color:var(--color-text-soft)">(tối thiểu ≥ <span id="pvMinPt"><c:choose><c:when test="${not empty formView}"><c:out value="${formView.minPtPerShift}" /></c:when><c:otherwise>—</c:otherwise></c:choose></span>)</small>
                </b>
              </div>
              <div class="leave-side-row" style="margin-bottom:var(--space-3)">
                <span style="color:var(--color-text-soft)">Lễ tân còn trực (nếu duyệt):</span>
                <b>
                  <span id="pvRecep"><c:choose><c:when test="${not empty formView}"><c:out value="${formView.remainingReceptionist}" /></c:when><c:otherwise>—</c:otherwise></c:choose></span> Lễ tân
                  <small style="font-weight:400;color:var(--color-text-soft)">(tối thiểu ≥ <span id="pvMinRecep"><c:choose><c:when test="${not empty formView}"><c:out value="${formView.minReceptionistPerShift}" /></c:when><c:otherwise>—</c:otherwise></c:choose></span>)</small>
                </b>
              </div>
              <p style="margin:0;font-size:var(--font-size-xs);color:var(--color-text-soft);line-height:1.5">
                Giả định nhân viên đang làm việc làm cả hai ca, chưa có lịch phân ca thực tế. Quản lý kiểm tra lại đủ ngày/ca trước khi duyệt.
              </p>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>

  <script src="${pageContext.request.contextPath}/js/pages/staff/leave-request-form.js?v=20261021-readable2"></script>
</body>
</html>
