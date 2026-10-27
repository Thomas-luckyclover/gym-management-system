<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN" scope="page" />
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Chi tiết đơn nghỉ phép #${item.leaveRequest.leaveRequestId} · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css?v=20261025-compact">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
  <script src="${pageContext.request.contextPath}/js/pages/manager/leave-request-review.js?v=20261024-late" defer></script>
</head>

<body class="dock theme-manager leave-review-page">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">${item.leaveRequest.pending ? 'Duyệt đơn nghỉ phép' : 'Chi tiết đơn nghỉ phép'}</h1>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">

        <%-- ===== Thanh trên cùng: Nút quay lại, Tiêu đề đơn, Trạng thái & Nút tác vụ ===== --%>
        <div class="review-top-bar">
          <div class="review-top-left">
            <a class="btn btn-sm btn-ghost" href="${pageContext.request.contextPath}/manager/leave-requests" title="Quay lại danh sách">
              <i class="fa-solid fa-arrow-left" aria-hidden="true"></i> Danh sách
            </a>
            <h2 class="review-top-title">Đơn nghỉ phép #${item.leaveRequest.leaveRequestId}</h2>
            <c:choose>
                <c:when test="${item.leaveRequest.pending}"><c:set var="statusClass" value="badge-warning" /></c:when>
                <c:when test="${item.leaveRequest.approved}"><c:set var="statusClass" value="badge-success" /></c:when>
                <c:otherwise><c:set var="statusClass" value="badge-danger" /></c:otherwise>
            </c:choose>
            <span class="badge ${statusClass}">
              <c:out value="${item.leaveRequest.status.displayName}" />
            </span>
          </div>

          <div class="review-top-actions">
            <c:if test="${item.leaveRequest.pending and item.canAct}">
              <%-- Nút Từ chối --%>
              <button type="button" class="btn btn-sm btn-outline" style="color:var(--color-danger);border-color:var(--color-danger)" onclick="openModal('modalRejectDetail')">
                <i class="fa-solid fa-xmark" aria-hidden="true"></i> Từ chối
              </button>

              <%-- Nút Duyệt --%>
              <c:choose>
                <c:when test="${not item.canApprove}">
                  <button type="button" class="btn btn-sm btn-primary" disabled title="<c:out value='${item.approvalBlockMessage}' />">
                    <i class="fa-solid fa-ban" aria-hidden="true"></i> Chưa đủ điều kiện
                  </button>
                </c:when>
                <c:otherwise>
                  <button type="button" class="btn btn-sm btn-primary" onclick="openModal('modalApproveDetail')">
                    <i class="fa-solid fa-check" aria-hidden="true"></i> ${item.leaveDatePassed ? 'Duyệt muộn' : 'Duyệt đơn'}
                  </button>
                </c:otherwise>
              </c:choose>
            </c:if>
          </div>
        </div>

        <%-- ===== Banner thông báo / Cảnh báo hệ thống ===== --%>
        <c:if test="${not empty item.banner}">
          <div class="alert-banner review-alert warning">
            <i class="fa-solid ${item.leaveDatePassed ? 'fa-circle-exclamation' : 'fa-triangle-exclamation'}" aria-hidden="true"></i>
            <span><c:out value="${item.banner}" /></span>
          </div>
        </c:if>

        <c:if test="${not empty formMessage}">
          <div class="alert-banner review-alert danger">
            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
            <c:out value="${formMessage}" />
          </div>
        </c:if>

        <%-- ===== Lưới chính 2 cột (Tối ưu 1366x768) ===== --%>
        <div class="review-grid-2col">

          <%-- CỘT TRÁI: Thông tin đơn và lý do nghỉ. --%>
          <div class="review-col-left">

            <%-- Card 1: Thông tin nhân viên & Chi tiết đơn --%>
            <div class="review-card">
              <div class="user-info-row">
                <c:choose>
                  <c:when test="${not empty item.avatarUrl}">
                    <img src="${pageContext.request.contextPath}${item.avatarUrl}" alt="Avatar" class="user-avatar-circle">
                  </c:when>
                  <c:otherwise>
                    <div class="user-avatar-circle">${fn:substring(item.fullName, 0, 1)}</div>
                  </c:otherwise>
                </c:choose>
                <div class="user-name-meta">
                  <b><c:out value="${item.fullName}" /></b>
                  <small>
                    <c:out value="${item.employeeCode}" /> ·
                    <span class="badge badge-sm badge-neutral">
                      ${item.position == 'PT' ? 'Huấn luyện viên PT' : 'Lễ tân'}
                    </span>
                  </small>
                </div>
              </div>

              <div class="detail-prop-grid">
                <%-- item.leaveGroup: Service đã đọc đủ các phần của cùng nhân viên, không phụ thuộc bộ lọc hiện tại. --%>
                <c:if test="${not empty item.leaveGroup}">
                    <details class="leave-related" open>
                        <summary>Các đơn cùng đợt nghỉ</summary>
                        <div class="leave-related-parts">
                            <small>Được duyệt <fmt:formatNumber value="${item.leaveGroup.approvedDays}" maxFractionDigits="1" />/<fmt:formatNumber value="${item.leaveGroup.totalDays}" maxFractionDigits="1" /> ngày</small>
                            <c:forEach var="part" items="${item.leaveGroup.requests}">
                                <a class="leave-related-part" href="${pageContext.request.contextPath}/manager/leave-requests/review?id=${part.leaveRequestId}">
                                    <span><strong>${part.leaveType.dbValue == 'Annual' ? 'Phép năm' : 'Không lương'}</strong> · <fmt:formatNumber value="${part.days}" maxFractionDigits="1" /> ngày</span>
                                    <small>${gms:formatDate(part.fromDate)}<c:if test="${part.fromDate != part.toDate}"> → ${gms:formatDate(part.toDate)}</c:if></small>
                                    <c:choose>
                                        <c:when test="${part.approved}"><c:set var="partStatusClass" value="badge-success" /></c:when>
                                        <c:when test="${part.pending}"><c:set var="partStatusClass" value="badge-warning" /></c:when>
                                        <c:otherwise><c:set var="partStatusClass" value="badge-danger" /></c:otherwise>
                                    </c:choose>
                                    <span class="badge badge-sm ${partStatusClass}"><c:out value="${part.status.displayName}" /></span>
                                </a>
                            </c:forEach>
                        </div>
                    </details>
                </c:if>
                <div class="detail-prop-item">
                  <span class="detail-prop-label">Thời gian nghỉ</span>
                  <span class="detail-prop-val">
                    ${gms:formatDate(item.leaveRequest.fromDate)}
                    <c:if test="${item.leaveRequest.fromDate != item.leaveRequest.toDate}">
                      → ${gms:formatDate(item.leaveRequest.toDate)}
                    </c:if>
                    <span style="font-weight:normal;color:var(--color-text-soft)">(<fmt:formatNumber value="${item.leaveRequest.days}" maxFractionDigits="1" /> ngày)</span>
                  </span>
                </div>

                <div class="detail-prop-item">
                  <span class="detail-prop-label">Ca làm việc</span>
                  <span class="detail-prop-val"><c:out value="${item.leaveRequest.shiftType.displayName}" /></span>
                </div>

                <div class="detail-prop-item">
                  <span class="detail-prop-label">Loại nghỉ</span>
                  <span class="detail-prop-val">
                    <c:choose>
                        <c:when test="${item.leaveRequest.leaveType.dbValue == 'Annual'}"><c:set var="leaveTypeClass" value="badge-success" /></c:when>
                        <c:when test="${item.leaveRequest.leaveType.dbValue == 'Sick'}"><c:set var="leaveTypeClass" value="badge-info" /></c:when>
                        <c:otherwise><c:set var="leaveTypeClass" value="badge-neutral" /></c:otherwise>
                    </c:choose>
                    <span class="badge ${leaveTypeClass}">
                      <c:out value="${item.leaveRequest.leaveType.displayName}" />
                    </span>
                  </span>
                </div>

                <div class="detail-prop-item">
                  <span class="detail-prop-label">Thời điểm gửi đơn</span>
                  <span class="detail-prop-val">
                    ${gms:formatDate(item.leaveRequest.submittedAt.toLocalDate())}
                    <small style="color:var(--color-text-soft);font-weight:normal">${gms:formatTime(item.leaveRequest.submittedAt.toLocalTime())}</small>
                  </span>
                </div>
              </div>

              <%-- Lý do nghỉ (clamp-3 có toggle) --%>
              <div class="reason-box-clamp">
                <span class="detail-prop-label">Lý do xin nghỉ</span>
                <p id="reasonClampText" class="reason-clamp-text">
                  <c:out value="${item.leaveRequest.reason}" />
                </p>
                <button type="button" id="btnToggleReason" class="btn-toggle-reason" onclick="toggleReasonClamp()">Xem thêm</button>
              </div>

            </div>

          </div>

          <%-- CỘT PHẢI: Kết quả kiểm tra; bảng và danh sách chi tiết mở khi cần. --%>
          <div class="review-col-right">
            <c:choose>
              <c:when test="${item.leaveRequest.pending}">
                <div class="review-card">
                  <div class="review-card-head">
                    <h3 class="review-card-title">
                      <i class="fa-solid fa-list-check" aria-hidden="true"></i> Điều kiện duyệt
                    </h3>
                    <span class="badge ${item.canApprove and not item.leaveDatePassed ? 'badge-success' : 'badge-warning'}"><c:choose>
                        <c:when test="${not item.canApprove}">Cần kiểm tra</c:when>
                        <c:when test="${item.leaveDatePassed}">Cần xác nhận</c:when>
                        <c:otherwise>Đủ điều kiện</c:otherwise>
                    </c:choose></span>
                  </div>

                  <c:if test="${item.leaveDatePassed}">
                    <p class="review-result-reason">Xác nhận nghỉ thực tế và nhập lý do khi bấm Duyệt muộn.</p>
                  </c:if>

                  <div class="review-check-list">
                    <c:forEach var="c" items="${item.checks}">
                      <%-- Manager xem quỹ phép và nhân sự; lịch PT được xử lý ở màn gửi đơn. --%>
                      <c:if test="${c.applicable and c.key != 'SESSIONS'}">
                        <div class="review-check-item ${c.ok ? 'pass' : 'fail'}">
                          <div class="check-icon-badge">
                            <i class="fa-solid ${c.ok ? 'fa-check' : 'fa-xmark'}" aria-hidden="true"></i>
                          </div>
                          <div class="check-meta-content">
                            <div class="check-meta-title"><c:out value="${c.label}" /></div>
                            <div class="check-meta-msg"><c:out value="${c.message}" /></div>

                            <%-- Nếu là mục Định biên và có bảng chi tiết từng ca --%>
                            <c:if test="${c.key == 'STAFFING' and not empty item.staffingRows}">
                              <details class="review-extra">
                                <summary>Xem quân số từng ca</summary>
                                <div class="review-table-wrap">
                                  <table class="review-staffing-table no-gms-table">
                                    <thead>
                                      <tr>
                                        <th scope="col">Ca trực</th>
                                        <th scope="col">Trước đơn</th>
                                        <th scope="col">Sau duyệt</th>
                                        <th scope="col">Tối thiểu</th>
                                        <th scope="col">Kết quả</th>
                                      </tr>
                                    </thead>
                                    <tbody>
                                      <c:forEach var="row" items="${item.staffingRows}">
                                        <tr>
                                          <td><c:out value="${row.shiftLabel}" /></td>
                                          <td>${row.current}</td>
                                          <td><b>${row.afterApproval}</b></td>
                                          <td>${row.minRequired}</td>
                                          <td>
                                            <c:choose>
                                              <c:when test="${row.belowMin}"><span class="badge badge-sm badge-danger">Thiếu người</span></c:when>
                                              <c:otherwise><span class="badge badge-sm badge-success">Đạt</span></c:otherwise>
                                            </c:choose>
                                          </td>
                                        </tr>
                                      </c:forEach>
                                    </tbody>
                                  </table>
                                </div>
                              </details>
                            </c:if>
                          </div>
                        </div>
                      </c:if>
                    </c:forEach>
                  </div>

                </div>
              </c:when>
              <c:otherwise>
                <%-- Đơn đã xử lý chỉ hiện quyết định đã lưu, không kiểm tra điều kiện duyệt hiện tại. --%>
                <div class="review-card">
                  <h3 class="review-card-title">Kết quả xử lý</h3>
                  <div class="detail-prop-grid">
                    <div class="detail-prop-item">
                      <span class="detail-prop-label">Người xử lý</span>
                      <span class="detail-prop-val"><c:out value="${item.reviewerName}" default="${item.autoRejected ? 'Hệ thống' : 'Chưa có thông tin'}" /></span>
                    </div>
                    <div class="detail-prop-item">
                      <span class="detail-prop-label">Thời gian xử lý</span>
                      <span class="detail-prop-val">${gms:formatDateTime(item.leaveRequest.reviewedAt)}</span>
                    </div>
                  </div>
                  <c:if test="${item.leaveRequest.rejected}">
                    <span class="detail-prop-label">Lý do từ chối</span>
                    <p class="review-result-reason"><c:out value="${item.leaveRequest.rejectReason}" /></p>
                  </c:if>
                </div>
              </c:otherwise>
            </c:choose>

          </div>

        </div>

      </main>
    </div>
  </div>

  <%-- ===== MODAL XÁC NHẬN DUYỆT ĐƠN ===== --%>
  <dialog id="modalApproveDetail" class="gms-modal" aria-labelledby="modalApproveTitle"
          data-reopen="${not empty errors.lateApprovalReason}">
    <div class="gms-modal-dialog">
      <form action="${pageContext.request.contextPath}/manager/leave-requests/review" method="post">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="approve">
        <input type="hidden" name="leaveRequestId" value="${item.leaveRequest.leaveRequestId}">

        <div class="gms-modal-header">
          <h3 class="gms-modal-title" id="modalApproveTitle">${item.leaveDatePassed ? 'Xác nhận duyệt muộn' : 'Phê duyệt đơn nghỉ phép'}</h3>
          <button type="button" class="gms-modal-close" onclick="closeModal('modalApproveDetail')">&times;</button>
        </div>
        <div class="gms-modal-body">
          <p>Xác nhận duyệt đơn nghỉ phép của <b><c:out value="${item.fullName}" /></b>?</p>
          <div style="font-size:var(--font-size-xs);color:var(--color-text-soft);line-height:1.6">
            • Thời gian nghỉ: ${gms:formatDate(item.leaveRequest.fromDate)}<c:if test="${item.leaveRequest.toDate != item.leaveRequest.fromDate}"> → ${gms:formatDate(item.leaveRequest.toDate)}</c:if> (<fmt:formatNumber value="${item.leaveRequest.days}" maxFractionDigits="1" /> ngày)<br>
            • Ca làm việc: <c:out value="${item.leaveRequest.shiftType.displayName}" /><br>
            • Loại nghỉ: <c:out value="${item.leaveRequest.leaveType.displayName}" /><br>
            <c:if test="${item.leaveRequest.leaveType.dbValue == 'Annual'}">• Sử dụng <fmt:formatNumber value="${item.leaveRequest.days}" maxFractionDigits="1" /> ngày phép năm.<br></c:if>
          </div>
          <c:if test="${item.leaveDatePassed and item.leaveRequest.pending}">
            <div class="field">
              <label class="field-label" for="lateApprovalReasonInput">Lý do xác nhận nghỉ thực tế *</label>
              <p>Tôi xác nhận phần thời gian đã qua nhân viên đã nghỉ đúng ngày và ca trong đơn.</p>
              <textarea id="lateApprovalReasonInput" name="lateApprovalReason" class="field-input"
                        rows="3" minlength="5" maxlength="500" required
                        placeholder="Ví dụ: Đã trao đổi và xác nhận với nhân viên."><c:out value="${lateApprovalReason}" /></textarea>
              <c:if test="${not empty errors.lateApprovalReason}">
                <div class="field-error"><c:out value="${errors.lateApprovalReason}" /></div>
              </c:if>
            </div>
          </c:if>
        </div>
        <div class="gms-modal-footer">
          <button type="button" class="btn btn-ghost" onclick="closeModal('modalApproveDetail')">Hủy</button>
          <button type="submit" class="btn btn-primary" ${item.canApprove ? '' : 'disabled'}>${item.leaveDatePassed ? 'Xác nhận duyệt muộn' : 'Xác nhận duyệt'}</button>
        </div>
      </form>
    </div>
  </dialog>

  <%-- ===== MODAL TỪ CHỐI ĐƠN ===== --%>
  <dialog id="modalRejectDetail" class="gms-modal" aria-labelledby="modalRejectTitle"
          data-reopen="${not empty errors.rejectReason}">
    <div class="gms-modal-dialog">
      <form action="${pageContext.request.contextPath}/manager/leave-requests/review" method="post">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="reject">
        <input type="hidden" name="leaveRequestId" value="${item.leaveRequest.leaveRequestId}">

        <div class="gms-modal-header">
          <h3 class="gms-modal-title" id="modalRejectTitle">Từ chối đơn nghỉ phép</h3>
          <button type="button" class="gms-modal-close" onclick="closeModal('modalRejectDetail')">&times;</button>
        </div>
        <div class="gms-modal-body">
          <div class="field">
            <label class="field-label" for="rejectReasonDetailInput">
              Lý do từ chối <span aria-hidden="true">*</span>
            </label>
            <textarea id="rejectReasonDetailInput"
                      name="rejectReason"
                      class="field-input"
                      rows="3"
                      minlength="5"
                      maxlength="500"
                      placeholder="Nhập lý do từ chối (5–500 ký tự)..."
                      required><c:out value="${rejectReason}" /></textarea>
            <c:if test="${not empty errors.rejectReason}">
              <div class="field-error"><c:out value="${errors.rejectReason}" /></div>
            </c:if>
          </div>
        </div>
        <div class="gms-modal-footer">
          <button type="button" class="btn btn-ghost" onclick="closeModal('modalRejectDetail')">Hủy</button>
          <button type="submit" class="btn btn-danger">Từ chối đơn</button>
        </div>
      </form>
    </div>
  </dialog>

</body>
</html>
