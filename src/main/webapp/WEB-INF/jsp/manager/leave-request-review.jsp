<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Duyệt đơn nghỉ phép · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
</head>

<body class="dock theme-manager">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Duyệt đơn nghỉ phép</h1>
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

        <%-- Nút quay lại danh sách --%>
        <a class="leave-back"
           href="${pageContext.request.contextPath}/manager/leave-requests">
          ← Quay lại danh sách
        </a>

        <section class="panel leave-review">

          <%-- ===== Tiêu đề đơn + badge trạng thái ===== --%>
          <div class="panel-head" style="display:flex;align-items:center;justify-content:space-between;gap:var(--space-4)">
            <div>
              <h2 style="margin:0 0 var(--space-1)">
                <c:out value="${item.fullName}" />
              </h2>
              <p style="margin:0;color:var(--color-text-soft);font-size:var(--font-size-sm)">
                <c:out value="${item.employeeCode}" /> ·
                <c:choose>
                  <c:when test="${item.position eq 'PT'}">Huấn luyện viên PT</c:when>
                  <c:when test="${item.position eq 'Receptionist'}">Lễ tân</c:when>
                  <c:otherwise><c:out value="${item.position}" /></c:otherwise>
                </c:choose>
              </p>
            </div>

            <%-- Badge trạng thái --%>
            <span class="badge
              ${item.leaveRequest.pending   ? 'badge-warning' :
                item.leaveRequest.approved  ? 'badge-success' : 'badge-danger'}">
              <c:out value="${item.leaveRequest.status.displayName}" />
            </span>
          </div>

          <%-- ===== Lưới thông tin đơn ===== --%>
          <div class="leave-info-grid">
            <div>
              <span class="leave-info-label">Ngày nghỉ</span>
              <span class="leave-info-value">
                <b>${gms:formatDate(item.leaveRequest.leaveDate)}</b>
              </span>
            </div>

            <div>
              <span class="leave-info-label">Ca nghỉ</span>
              <span class="leave-info-value">
                <c:out value="${item.leaveRequest.shiftType.displayName}" />
              </span>
            </div>

            <div>
              <span class="leave-info-label">Loại nghỉ</span>
              <span class="leave-info-value">
                <c:out value="${item.leaveRequest.leaveType.displayName}" />
              </span>
            </div>

            <div>
              <span class="leave-info-label">Nộp lúc</span>
              <span class="leave-info-value">
                ${gms:formatDate(item.leaveRequest.submittedAt.toLocalDate())}
                <small style="color:var(--color-text-soft)">
                  <c:out value="${item.leaveRequest.submittedAt.toLocalTime()}" />
                </small>
              </span>
            </div>
          </div>

          <%-- ===== Lý do nghỉ ===== --%>
          <div class="leave-reason-box">
            <span class="leave-info-label">Lý do nghỉ</span>
            <p><c:out value="${item.leaveRequest.reason}" /></p>
          </div>

          <%-- ===== Thông báo lỗi form (approve thất bại) ===== --%>
          <c:if test="${not empty formMessage}">
            <div class="alert-banner danger" style="margin-bottom:var(--space-5)">
              <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
              <c:out value="${formMessage}" />
            </div>
          </c:if>

          <%-- Đơn Pending nhưng đã quá P-20: chỉ báo, không cho thao tác (CBR-85) --%>
          <c:if test="${item.leaveRequest.pending and item.overdue}">
            <div class="alert-banner danger" style="margin-bottom:var(--space-5)">
              <i class="fa-solid fa-clock" aria-hidden="true"></i>
              Đơn đã quá hạn duyệt (${gms:formatDateTime(item.approvalDeadline)}).
              Hệ thống sẽ tự từ chối trong vòng 1 phút.
            </div>
          </c:if>

          <%-- ===== Đơn Pending còn hạn: hiện dự báo quân số ca trực & 2 card Duyệt / Từ chối ===== --%>
          <c:if test="${item.leaveRequest.pending and not item.overdue}">

            <%-- Khung thông tin định biên nhân sự dự kiến nếu duyệt đơn này --%>
            <c:if test="${item.minStaffRequired > 0}">
              <div class="leave-staffing-box ${item.remainingStaff < item.minStaffRequired ? 'danger' : ''}">
                <i class="fa-solid ${item.remainingStaff < item.minStaffRequired ? 'fa-triangle-exclamation' : 'fa-users'}"
                   aria-hidden="true"
                   style="font-size:var(--font-size-lg);margin-top:2px"></i>
                <div style="flex:1">
                  <span class="leave-info-label">
                    Dự báo quân số ${item.position eq 'PT' ? 'HLV (PT)' : 'Lễ tân'} trực ca nếu duyệt đơn này
                  </span>
                  <div class="leave-staffing-numbers">
                    <div class="leave-staffing-item">
                      <strong><c:out value="${item.remainingStaff}" /></strong>
                      <small>${item.position eq 'PT' ? 'PT' : 'Lễ tân'} dự kiến còn lại</small>
                    </div>
                    <div class="leave-staffing-item">
                      <strong><c:out value="${item.minStaffRequired}" /></strong>
                      <small>Định biên tối thiểu (${item.position eq 'PT' ? 'P-13' : 'P-14'})</small>
                    </div>
                  </div>
                  <c:if test="${item.remainingStaff < item.minStaffRequired}">
                    <p style="margin:var(--space-2) 0 0;font-size:var(--font-size-sm);color:var(--color-danger-ink);font-weight:600">
                      Cảnh báo: Nếu duyệt, ca trực sẽ chỉ còn ${item.remainingStaff} người (thấp hơn định biên tối thiểu ${item.minStaffRequired}). Nút duyệt bị vô hiệu hóa.
                    </p>
                  </c:if>
                </div>
              </div>
            </c:if>

            <div class="leave-actions">

              <%-- Card Duyệt --%>
              <form action="${pageContext.request.contextPath}/manager/leave-requests/review"
                    method="post"
                    class="leave-action-card">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="leaveRequestId" value="${item.leaveRequest.leaveRequestId}">
                <input type="hidden" name="action" value="approve">

                <h3><i class="fa-solid fa-circle-check" aria-hidden="true"></i> Duyệt đơn</h3>
                <p>Nhân viên được phép nghỉ đúng ngày và ca đã đăng ký.</p>

                <c:choose>
                  <c:when test="${item.minStaffRequired > 0 and item.remainingStaff < item.minStaffRequired}">
                    <button class="btn btn-primary"
                            type="button"
                            disabled
                            title="Không thể duyệt do vi phạm định biên nhân sự tối thiểu">
                      <i class="fa-solid fa-ban" aria-hidden="true"></i>
                      Không đủ người trực ca
                    </button>
                  </c:when>
                  <c:otherwise>
                    <button class="btn btn-primary"
                            type="submit"
                            id="btnApproveLeave"
                            data-confirm-title="Duyệt đơn nghỉ phép"
                            data-confirm-message="Xác nhận duyệt đơn nghỉ này?">
                      <i class="fa-solid fa-check" aria-hidden="true"></i>
                      Duyệt đơn
                    </button>
                  </c:otherwise>
                </c:choose>
              </form>

              <%-- Card Từ chối --%>
              <form action="${pageContext.request.contextPath}/manager/leave-requests/review"
                    method="post"
                    class="leave-action-card">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="leaveRequestId" value="${item.leaveRequest.leaveRequestId}">
                <input type="hidden" name="action" value="reject">

                <h3><i class="fa-solid fa-circle-xmark" aria-hidden="true"></i> Từ chối đơn</h3>

                <div class="field">
                  <label class="field-label" for="rejectReason">
                    Lý do từ chối <span aria-hidden="true">*</span>
                  </label>

                  <textarea id="rejectReason"
                            name="rejectReason"
                            minlength="5"
                            maxlength="500"
                            placeholder="Nhập lý do từ chối (5–500 ký tự)…"
                            required><c:out value="${rejectReason}" /></textarea>

                  <c:if test="${not empty errors.rejectReason}">
                    <div class="field-error">
                      <c:out value="${errors.rejectReason}" />
                    </div>
                  </c:if>
                </div>

                <button class="btn btn-danger"
                        type="submit"
                        id="btnRejectLeave"
                        data-confirm-title="Từ chối đơn nghỉ phép"
                        data-confirm-message="Xác nhận từ chối đơn nghỉ này? Nhân viên sẽ nhận thông báo kèm lý do.">
                  <i class="fa-solid fa-xmark" aria-hidden="true"></i>
                  Từ chối đơn
                </button>
              </form>

            </div>
          </c:if>

          <%-- ===== Đơn đã xử lý: hiện read-only trạng thái + người duyệt ===== --%>
          <c:if test="${not item.leaveRequest.pending}">
            <div class="leave-reviewed-info">

              <span class="badge
                ${item.leaveRequest.approved ? 'badge-success' : 'badge-danger'}">
                <c:out value="${item.leaveRequest.status.displayName}" />
              </span>

              <p style="margin:var(--space-3) 0 var(--space-1);color:var(--color-text-soft);font-size:var(--font-size-sm)">
                Người xử lý
              </p>
              <c:choose>
                <c:when test="${not empty item.reviewerName}">
                  <b><c:out value="${item.reviewerName}" /></b>
                </c:when>
                <c:otherwise>
                  <b>Hệ thống tự động (Quá hạn duyệt P-20)</b>
                </c:otherwise>
              </c:choose>

              <c:if test="${item.leaveRequest.reviewedAt != null}">
                <p style="margin:var(--space-3) 0 var(--space-1);color:var(--color-text-soft);font-size:var(--font-size-sm)">
                  Thời điểm xử lý
                </p>
                <span>
                  ${gms:formatDate(item.leaveRequest.reviewedAt.toLocalDate())}
                  <small style="color:var(--color-text-soft)">
                    <c:out value="${item.leaveRequest.reviewedAt.toLocalTime()}" />
                  </small>
                </span>
              </c:if>

              <c:if test="${item.leaveRequest.rejected and not empty item.leaveRequest.rejectReason}">
                <p style="margin:var(--space-4) 0 var(--space-1);color:var(--color-text-soft);font-size:var(--font-size-sm)">
                  Lý do từ chối
                </p>
                <p style="margin:0"><c:out value="${item.leaveRequest.rejectReason}" /></p>
              </c:if>

            </div>
          </c:if>

        </section>
      </main>
    </div>
  </div>
</body>
</html>
