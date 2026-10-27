<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Duyệt yêu cầu ${item.request.requestCode} · Super Gym Quản lý</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/maintenance-requests.css?v=20261026v3">
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
          <h1 class="tb-title">Yêu cầu bảo trì</h1>
          <span class="tb-subtitle">Duyệt hoặc từ chối yêu cầu lễ tân gửi lên; theo dõi đến khi máy sửa xong.</span>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">

        <%-- ===== THANH ĐIỀU HƯỚNG TRÊN CÙNG: QUAY LẠI, TIÊU ĐỀ & NÚT THAO TÁC ===== --%>
        <div class="review-top-bar">
          <div class="review-top-left">
            <a class="btn btn-sm btn-ghost" href="${pageContext.request.contextPath}/manager/maintenance-requests" title="Quay lại danh sách">
              <i class="fa-solid fa-arrow-left" aria-hidden="true"></i> Danh sách
            </a>
            <h2 class="review-top-title">Yêu cầu #${item.request.requestCode}</h2>
            <span class="badge ${item.request.open ? 'badge-warning' : (item.request.inProgress ? 'badge-info' : (item.request.closed ? 'badge-success' : 'badge-danger'))}">
              <c:out value="${item.request.status.displayName}" />
            </span>
          </div>

          <div class="review-top-actions">
            <c:if test="${item.request.open}">
              <button type="button" class="btn btn-sm btn-outline" style="color:var(--color-danger);border-color:var(--color-danger)" onclick="openModal('modalRejectDetail')">
                <i class="fa-solid fa-xmark" aria-hidden="true"></i> Từ chối
              </button>
              <button type="button" class="btn btn-sm btn-primary" onclick="openModal('modalApproveDetail')">
                <i class="fa-solid fa-check" aria-hidden="true"></i> Duyệt yêu cầu
              </button>
            </c:if>
          </div>
        </div>

        <c:if test="${not empty formMessage}">
          <div class="alert-banner review-alert danger" style="margin-bottom: var(--space-4);">
            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
            <c:out value="${formMessage}" />
          </div>
        </c:if>

        <%-- ===== BỐ CỤC 2 CỘT CHI TIẾT ===== --%>
        <div class="review-two-cols-layout">

          <%-- CỘT TRÁI (65%): CHI TIẾT SỰ CỐ, ẢNH & THIẾT BỊ --%>
          <div class="review-col-main">

            <%-- Card 1: Thông tin sự cố --%>
            <section class="review-card">
              <div class="review-card-head">
                <h3 class="review-card-title">Mô tả sự cố</h3>
                <div class="badges-group">
                  <c:if test="${item.request.warningTagPlaced}">
                    <span class="warning-tag-pill">
                      <i class="fa-solid fa-triangle-exclamation"></i> Đã treo biển ngừng dùng
                    </span>
                  </c:if>
                  <span class="info-pill">Ưu tiên: <c:out value="${item.request.priority.displayName}" /></span>
                  <span class="info-pill"><c:out value="${item.request.incidentType.displayName}" /></span>
                </div>
              </div>

              <div class="review-card-body">
                <div class="incident-desc-box">
                  <p class="desc-text"><c:out value="${item.request.description}" /></p>
                </div>

                <c:if test="${not empty item.request.affectedItemsNote}">
                  <div class="affected-items-box">
                    <span class="detail-label">Ghi chú cá thể / mức tạ ảnh hưởng:</span>
                    <p class="affected-note-text"><c:out value="${item.request.affectedItemsNote}" /></p>
                  </div>
                </c:if>
              </div>
            </section>

            <%-- Card 2: Ảnh sự cố --%>
            <section class="review-card">
              <div class="review-card-head">
                <h3 class="review-card-title">Ảnh sự cố thực tế</h3>
              </div>
              <div class="review-card-body">
                <c:choose>
                  <c:when test="${not empty item.request.attachedImages}">
                    <div class="incident-image-container">
                      <img src="${pageContext.request.contextPath}${item.request.attachedImages}"
                           alt="Ảnh sự cố ${item.itemCode}"
                           class="incident-image-preview">
                    </div>
                  </c:when>
                  <c:otherwise>
                    <div class="no-image-placeholder">
                      <i class="fa-regular fa-image" aria-hidden="true"></i>
                      <span>Lễ tân không đính kèm ảnh</span>
                    </div>
                  </c:otherwise>
                </c:choose>
              </div>
            </section>

            <%-- Card 3: Thông tin thiết bị & Lịch sử hỏng --%>
            <section class="review-card">
              <div class="review-card-head">
                <h3 class="review-card-title">Thông tin thiết bị</h3>
              </div>
              <div class="review-card-body">
                <div class="device-summary-grid">
                  <div class="grid-item">
                    <span class="grid-label">Dòng thiết bị</span>
                    <strong class="grid-val"><c:out value="${item.facilityName}" /></strong>
                  </div>
                  <div class="grid-item">
                    <span class="grid-label">Mã máy</span>
                    <strong class="grid-val"><c:out value="${item.itemCode}" /></strong>
                  </div>
                  <div class="grid-item">
                    <span class="grid-label">Vị trí</span>
                    <strong class="grid-val"><c:out value="${item.facilityLocation}" /></strong>
                  </div>
                  <div class="grid-item">
                    <span class="grid-label">Khả dụng trong phòng</span>
                    <strong class="grid-val">
                      <c:out value="${facility.availableQuantity}" default="?" /> / <c:out value="${facility.totalQuantity}" default="?" /> máy
                    </strong>
                  </div>
                </div>

                <%-- Lần hỏng trước --%>
                <div class="history-block">
                  <span class="history-title">Lịch sử hỏng trước đó:</span>
                  <c:choose>
                    <c:when test="${not empty lastClosedRequest}">
                      <div class="history-item">
                        <span class="history-code">#<c:out value="${lastClosedRequest.request.requestCode}" /></span>
                        <span>Ngày báo: ${gms:formatDate(lastClosedRequest.request.reportedAt.toLocalDate())}</span>
                        <span>Loại: <c:out value="${lastClosedRequest.request.incidentType.displayName}" /></span>
                        <span class="badge badge-success">Đã hoàn thành</span>
                      </div>
                    </c:when>
                    <c:otherwise>
                      <p class="text-subtle">Chưa có lịch sử hỏng trước đó của máy này.</p>
                    </c:otherwise>
                  </c:choose>
                </div>
              </div>
            </section>

          </div>

          <%-- CỘT PHẢI (35%): THÔNG TIN NGƯỜI BÁO & TIẾN TRÌNH XỬ LÝ --%>
          <div class="review-col-side">

            <%-- Card Người báo cáo --%>
            <section class="review-card">
              <div class="review-card-head">
                <h3 class="review-card-title">Người gửi yêu cầu</h3>
              </div>
              <div class="review-card-body">
                <div class="reporter-info-row">
                  <span class="reporter-avatar">LT</span>
                  <div class="reporter-meta">
                    <b><c:out value="${item.reporterName}" default="Lễ tân" /></b>
                    <small>Lễ tân phòng tập</small>
                  </div>
                </div>

                <div class="side-info-item">
                  <span class="side-label">Thời gian báo</span>
                  <span class="side-val">
                    ${gms:formatDate(item.request.reportedAt.toLocalDate())} ${item.request.reportedAt.toLocalTime().toString().substring(0, 5)}
                  </span>
                </div>
              </div>
            </section>

            <%-- Card Tiến trình & Quyết định --%>
            <section class="review-card">
              <div class="review-card-head">
                <h3 class="review-card-title">Tiến trình xử lý</h3>
              </div>
              <div class="review-card-body">
                <c:choose>
                  <c:when test="${item.request.open}">
                    <div class="pending-notice-box">
                      <i class="fa-solid fa-clock-rotate-left"></i>
                      <p>Yêu cầu đang <b>Chờ duyệt</b>.<br>Vui lòng xem xét sự cố và bấm <b>Duyệt yêu cầu</b> hoặc <b>Từ chối</b> ở thanh trên cùng.</p>
                    </div>
                  </c:when>

                  <c:when test="${item.request.inProgress}">
                    <div class="process-status-box success">
                      <span class="status-title"><i class="fa-solid fa-circle-check"></i> Đã phê duyệt</span>
                      <p class="status-meta">Người duyệt: <b><c:out value="${item.reviewerName}" /></b></p>
                      <c:if test="${not empty item.request.managerNote}">
                        <div class="manager-note-callout">
                          <small>Ghi chú của Quản lý:</small>
                          <p><c:out value="${item.request.managerNote}" /></p>
                        </div>
                      </c:if>
                    </div>
                  </c:when>

                  <c:when test="${item.request.rejected}">
                    <div class="process-status-box danger">
                      <span class="status-title"><i class="fa-solid fa-ban"></i> Đã từ chối</span>
                      <p class="status-meta">Người từ chối: <b><c:out value="${item.reviewerName}" /></b></p>
                      <c:if test="${not empty item.request.rejectReason}">
                        <div class="manager-note-callout">
                          <small>Lý do từ chối:</small>
                          <p><c:out value="${item.request.rejectReason}" /></p>
                        </div>
                      </c:if>
                    </div>
                  </c:when>

                  <c:when test="${item.request.closed}">
                    <div class="process-status-box closed">
                      <span class="status-title"><i class="fa-solid fa-flag-checkered"></i> Đã đóng nghiệm thu</span>
                      <p class="status-meta">Người nghiệm thu: <b><c:out value="${item.closerName}" /></b></p>
                      <c:if test="${not empty item.request.acceptanceNote}">
                        <div class="manager-note-callout">
                          <small>Ghi chú nghiệm thu:</small>
                          <p><c:out value="${item.request.acceptanceNote}" /></p>
                        </div>
                      </c:if>
                    </div>
                  </c:when>
                </c:choose>
              </div>
            </section>

          </div>

        </div>

      </main>
    </div>
  </div>

  <%-- ===== MODAL XÁC NHẬN DUYỆT YÊU CẦU ===== --%>
  <div id="modalApproveDetail" class="gms-modal" role="dialog" aria-modal="true" aria-labelledby="modalApproveTitle">
    <div class="gms-modal-dialog">
      <form action="${pageContext.request.contextPath}/manager/maintenance-requests/review" method="post">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="approve">
        <input type="hidden" name="requestId" value="${item.request.maintenanceRequestId}">

        <div class="gms-modal-header">
          <h3 class="gms-modal-title" id="modalApproveTitle">Phê duyệt yêu cầu bảo trì</h3>
          <button type="button" class="gms-modal-close" onclick="closeModal('modalApproveDetail')">&times;</button>
        </div>
        <div class="gms-modal-body">
          <p>Xác nhận duyệt yêu cầu bảo trì thiết bị <b><c:out value="${item.facilityName}" /> (<c:out value="${item.itemCode}" />)</b>?</p>

          <label class="modal-checkbox-row">
            <input type="checkbox" name="setUnderMaintenance" value="true" checked>
            <span>Chuyển trạng thái máy sang <b>Đang bảo trì</b> (khách không thể sử dụng)</span>
          </label>

          <div class="field" style="margin-top: var(--space-3);">
            <label class="field-label" for="managerNoteInput">Ghi chú cho lễ tân (không bắt buộc)</label>
            <textarea id="managerNoteInput"
                      name="managerNote"
                      class="form-input"
                      rows="3"
                      maxlength="500"
                      placeholder="VD: Đã liên hệ bên kỹ thuật bảo hành, sẽ đến kiểm tra vào sáng mai..."></textarea>
          </div>
        </div>
        <div class="gms-modal-footer">
          <button type="button" class="btn btn-ghost" onclick="closeModal('modalApproveDetail')">Hủy</button>
          <button type="submit" class="btn btn-primary">Xác nhận duyệt</button>
        </div>
      </form>
    </div>
  </div>

  <%-- ===== MODAL TỪ CHỐI YÊU CẦU ===== --%>
  <div id="modalRejectDetail" class="gms-modal" role="dialog" aria-modal="true" aria-labelledby="modalRejectTitle">
    <div class="gms-modal-dialog">
      <form action="${pageContext.request.contextPath}/manager/maintenance-requests/review" method="post">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="reject">
        <input type="hidden" name="requestId" value="${item.request.maintenanceRequestId}">

        <div class="gms-modal-header">
          <h3 class="gms-modal-title" id="modalRejectTitle">Từ chối yêu cầu bảo trì</h3>
          <button type="button" class="gms-modal-close" onclick="closeModal('modalRejectDetail')">&times;</button>
        </div>
        <div class="gms-modal-body">
          <div class="field">
            <label class="field-label" for="rejectReasonDetailInput">
              Lý do từ chối <span aria-hidden="true">*</span>
            </label>
            <textarea id="rejectReasonDetailInput"
                      name="rejectReason"
                      class="form-input"
                      rows="3"
                      minlength="5"
                      maxlength="500"
                      placeholder="Nhập lý do từ chối gửi thông báo cho lễ tân..."
                      required></textarea>
            <small class="field-hint">Tối thiểu 5 ký tự, tối đa 500 ký tự.</small>
          </div>
        </div>
        <div class="gms-modal-footer">
          <button type="button" class="btn btn-ghost" onclick="closeModal('modalRejectDetail')">Hủy</button>
          <button type="submit" class="btn btn-danger">Từ chối yêu cầu</button>
        </div>
      </form>
    </div>
  </div>

  <%-- Script đóng mở Modal chuẩn Super Gym --%>
  <script>
    var modalTrigger = null;

    function closeModal(id) {
      var modal = document.getElementById(id);
      if (modal) {
        modal.classList.remove('show');
      }
      if (modalTrigger) {
        modalTrigger.focus();
      }
    }

    function openModal(id) {
      modalTrigger = document.activeElement;
      var modal = document.getElementById(id);
      if (modal) {
        modal.classList.add('show');
        var firstControl = modal.querySelector('button, textarea, input');
        if (firstControl) {
          firstControl.focus();
        }
      }
    }

    window.addEventListener('keydown', function(event) {
      if (event.key === 'Escape') {
        var openModalEl = document.querySelector('.gms-modal.show');
        if (openModalEl) {
          closeModal(openModalEl.id);
        }
      }
    });

    window.addEventListener('click', function(event) {
      if (event.target.classList && event.target.classList.contains('gms-modal')) {
        closeModal(event.target.id);
      }
    });

    // Đảm bảo mở/đóng Sidebar mượt mà và an toàn
    document.addEventListener('DOMContentLoaded', function () {
      var dock = document.getElementById('dock');
      var dockHandle = document.getElementById('dockHandle');
      if (dock && dockHandle) {
        dockHandle.addEventListener('click', function (e) {
          e.stopPropagation();
          var wasOpen = dock.classList.contains('is-open');
          setTimeout(function () {
            if (dock.classList.contains('is-open') === wasOpen) {
              dock.classList.toggle('is-open', !wasOpen);
              document.body.classList.toggle('nav-open', !wasOpen);
              dockHandle.setAttribute('aria-expanded', String(!wasOpen));
              var sidebar = dock.querySelector('.sidebar');
              if (sidebar) {
                sidebar.inert = wasOpen;
              }
            }
          }, 10);
        });
      }
    });
  </script>
</body>
</html>