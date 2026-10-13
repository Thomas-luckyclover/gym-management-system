<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css">
</head>

<body class="dock ${formView.position eq 'Receptionist' ? 'theme-staff' : 'theme-staff'}">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%-- ===== Sidebar dock theo role PT / Lễ tân ===== --%>
    <c:set var="activeNav" value="leaves" />
    <c:choose>
      <c:when test="${formView.position eq 'Receptionist'}">
        <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>
      </c:when>
      <c:otherwise>
        <%@ include file="/WEB-INF/jsp/common/layout-pt.jspf" %>
      </c:otherwise>
    </c:choose>

    <div class="main">
      <%-- ===== Topbar: thông tin nhân viên đang đăng nhập ===== --%>
      <header class="topbar">
        <div class="topbar-title">
          <h1 style="margin:0;font-size:var(--font-size-xl)">Nghỉ phép</h1>
        </div>
        <div class="topbar-actions" style="display:flex;align-items:center;gap:var(--space-3)">
          <span class="badge badge-info">
            <c:choose>
              <c:when test="${formView.position eq 'PT'}">Huấn luyện viên PT</c:when>
              <c:when test="${formView.position eq 'Receptionist'}">Lễ tân</c:when>
              <c:otherwise><c:out value="${formView.position}" /></c:otherwise>
            </c:choose>
            · <c:out value="${formView.employeeCode}" />
          </span>
          <b style="font-size:var(--font-size-sm)"><c:out value="${formView.fullName}" /></b>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">
        <%-- Thông báo lỗi nộp đơn từ server --%>
        <c:if test="${not empty submitError}">
          <div class="alert-banner danger" style="margin-bottom:var(--space-4);padding:var(--space-3) var(--space-4);border-radius:var(--radius-md);background:var(--color-danger-tint);color:var(--color-danger);border:1px solid var(--color-danger-border)">
            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
            <c:out value="${submitError}" />
          </div>
        </c:if>

        <c:if test="${not empty formError}">
          <div class="alert-banner danger" style="margin-bottom:var(--space-4);padding:var(--space-3) var(--space-4);border-radius:var(--radius-md);background:var(--color-danger-tint);color:var(--color-danger);border:1px solid var(--color-danger-border)">
            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
            <c:out value="${formError}" />
          </div>
        </c:if>

        <%-- Layout 2 cột split cho màn nộp đơn --%>
        <div class="leave-split" style="display:grid;grid-template-columns:1fr 320px;gap:var(--space-6);align-items:start">
          <div class="leave-col-main">
            <%-- ===== Panel form nộp đơn xin nghỉ phép ===== --%>
            <section class="panel" style="margin-bottom:var(--space-6);padding:var(--space-5);border-radius:var(--radius-lg);background:var(--color-surface);border:1px solid var(--color-border-strong);box-shadow:var(--shadow-card)">
              <div class="panel-head" style="margin-bottom:var(--space-4)">
                <h2 style="margin:0;font-size:var(--font-size-lg)">Tạo đơn nghỉ phép</h2>
              </div>

              <form action="${pageContext.request.requestURI}" method="post" id="leaveForm" novalidate>
                <div class="form-grid" style="display:grid;grid-template-columns:1fr 1fr;gap:var(--space-4);margin-bottom:var(--space-4)">
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
                      <div class="field-error" style="color:var(--color-danger);font-size:var(--font-size-xs);margin-top:var(--space-1)">
                        <c:out value="${fieldErrors.leaveType}" />
                      </div>
                    </c:if>
                  </div>

                  <div class="field">
                    <label class="field-label" for="shiftType">
                      Ca nghỉ <span aria-hidden="true">*</span>
                    </label>
                    <select id="shiftType" name="shiftType" required>
                      <option value="FullDay" ${paramLeaveType eq 'FullDay' ? 'selected' : ''}>Cả ngày</option>
                      <option value="Morning" ${paramShiftType eq 'Morning' ? 'selected' : ''}>Ca sáng</option>
                      <option value="Afternoon" ${paramShiftType eq 'Afternoon' ? 'selected' : ''}>Ca chiều</option>
                    </select>
                    <c:if test="${not empty fieldErrors.shiftType}">
                      <div class="field-error" style="color:var(--color-danger);font-size:var(--font-size-xs);margin-top:var(--space-1)">
                        <c:out value="${fieldErrors.shiftType}" />
                      </div>
                    </c:if>
                  </div>
                </div>

                <div class="field" style="margin-bottom:var(--space-4)">
                  <label class="field-label" for="leaveDate">
                    Ngày nghỉ <span aria-hidden="true">*</span>
                  </label>
                  <input id="leaveDate"
                         name="leaveDate"
                         type="date"
                         value="<c:out value='${paramLeaveDate}' />"
                         required>
                  <span class="field-hint" style="display:block;margin-top:var(--space-1);color:var(--color-text-soft);font-size:var(--font-size-xs)">
                    <i class="fa-solid fa-circle-info" aria-hidden="true"></i>
                    Đơn phải nộp trước giờ chốt của ngày hôm trước. Nghỉ cùng ngày vui lòng báo trực tiếp Quản lý.
                  </span>
                  <div class="field-error" id="leaveDateClientErr" style="color:var(--color-danger);font-size:var(--font-size-xs);margin-top:var(--space-1)" hidden></div>
                  <c:if test="${not empty fieldErrors.leaveDate}">
                    <div class="field-error" style="color:var(--color-danger);font-size:var(--font-size-xs);margin-top:var(--space-1)">
                      <c:out value="${fieldErrors.leaveDate}" />
                    </div>
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
                    <div class="field-error" style="color:var(--color-danger);font-size:var(--font-size-xs);margin-top:var(--space-1)">
                      <c:out value="${fieldErrors.reason}" />
                    </div>
                  </c:if>
                </div>

                <button class="btn btn-primary" type="submit" id="leaveSubmit">
                  <i class="fa-solid fa-paper-plane" aria-hidden="true"></i> Nộp đơn
                </button>
              </form>
            </section>

            <%-- TODO Bước 4: Danh sách đơn đã nộp --%>
          </div>

          <div class="leave-col-side">
            <%-- TODO Bước 3: Side cards thông tin hạn nộp và định biên ca --%>
          </div>
        </div>
      </main>
    </div>
  </div>
</body>
</html>
