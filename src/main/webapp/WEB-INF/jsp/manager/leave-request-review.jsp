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
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css?v=20261022-compact-detail">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">
  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
  <script src="${pageContext.request.contextPath}/js/pages/manager/leave-request-review.js?v=20261021-readable2" defer></script>
</head>

<body class="dock theme-manager leave-review-page">
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
            <span class="avatar"><c:out value="${sessionScope.userInitials}" /></span>
            <span>
              <b><c:out value="${sessionScope.displayName}" default="Quản lý" /></b>
              <%@ include file="/WEB-INF/jsp/common/_user-badge-line.jspf" %>
            </span>
          </a>
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
            <span class="badge ${item.leaveRequest.pending ? 'badge-warning' : (item.leaveRequest.approved ? 'badge-success' : 'badge-danger')}">
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
                <c:when test="${item.leaveDatePassed}">
                  <button type="button" class="btn btn-sm btn-primary" disabled title="Không thể duyệt do ngày nghỉ đã qua">
                    <i class="fa-solid fa-ban" aria-hidden="true"></i> Ngày nghỉ đã qua
                  </button>
                </c:when>
                <c:when test="${not item.canApprove}">
                  <button type="button" class="btn btn-sm btn-primary" disabled title="Xem các điều kiện chưa đạt bên dưới">
                    <i class="fa-solid fa-ban" aria-hidden="true"></i> Chưa đủ điều kiện
                  </button>
                </c:when>
                <c:otherwise>
                  <button type="button" class="btn btn-sm btn-primary" onclick="openModal('modalApproveDetail')">
                    <i class="fa-solid fa-check" aria-hidden="true"></i> Duyệt đơn
                  </button>
                </c:otherwise>
              </c:choose>
            </c:if>
          </div>
        </div>

        <%-- ===== Banner thông báo / Cảnh báo hệ thống ===== --%>
        <c:if test="${not empty item.banner}">
          <div class="alert-banner review-alert ${item.leaveDatePassed ? 'danger' : 'warning'}">
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
                    <span class="badge badge-sm ${item.position == 'PT' ? 'badge-info' : 'badge-neutral'}">
                      ${item.position == 'PT' ? 'Huấn luyện viên PT' : 'Lễ tân'}
                    </span>
                  </small>
                </div>
              </div>

              <div class="detail-prop-grid">
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
                    <span class="badge ${item.leaveRequest.leaveType.dbValue == 'Annual' ? 'badge-success' : (item.leaveRequest.leaveType.dbValue == 'Sick' ? 'badge-info' : 'badge-neutral')}">
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

              <%-- Kết quả xử lý đơn (nếu đã duyệt hoặc từ chối) --%>
              <c:if test="${item.leaveRequest.rejected}">
                <div style="margin-top:var(--space-4);padding:var(--space-3) var(--space-4);border-radius:var(--radius-md);background-color:rgba(239,68,68,0.1);border:1px solid rgba(239,68,68,0.3)">
                  <div style="font-weight:600;color:var(--color-danger);display:flex;align-items:center;gap:var(--space-2)">
                    <i class="fa-solid fa-circle-xmark"></i>
                    <span>Đơn đã bị từ chối</span>
                    <c:if test="${not empty item.reviewerName}">
                      <span style="font-weight:normal;font-size:var(--font-size-xs)">bởi <c:out value="${item.reviewerName}" /></span>
                    </c:if>
                    <c:if test="${not empty item.leaveRequest.reviewedAt}">
                      <span style="font-weight:normal;font-size:var(--font-size-xs)">(${gms:formatDateTime(item.leaveRequest.reviewedAt)})</span>
                    </c:if>
                  </div>
                  <c:if test="${not empty item.leaveRequest.rejectReason}">
                    <div style="margin-top:var(--space-2);font-size:var(--font-size-sm);color:var(--color-text)">
                      <b>Lý do từ chối:</b> <c:out value="${item.leaveRequest.rejectReason}" />
                    </div>
                  </c:if>
                </div>
              </c:if>

              <c:if test="${item.leaveRequest.approved}">
                <div style="margin-top:var(--space-4);padding:var(--space-3) var(--space-4);border-radius:var(--radius-md);background-color:rgba(16,185,129,0.1);border:1px solid rgba(16,185,129,0.3)">
                  <div style="font-weight:600;color:var(--color-success);display:flex;align-items:center;gap:var(--space-2)">
                    <i class="fa-solid fa-circle-check"></i>
                    <span>Đơn đã được phê duyệt</span>
                    <c:if test="${not empty item.reviewerName}">
                      <span style="font-weight:normal;font-size:var(--font-size-xs)">bởi <c:out value="${item.reviewerName}" /></span>
                    </c:if>
                    <c:if test="${not empty item.leaveRequest.reviewedAt}">
                      <span style="font-weight:normal;font-size:var(--font-size-xs)">(${gms:formatDateTime(item.leaveRequest.reviewedAt)})</span>
                    </c:if>
                  </div>
                </div>
              </c:if>
            </div>

          </div>

          <%-- CỘT PHẢI: Kết quả kiểm tra; bảng và danh sách chi tiết mở khi cần. --%>
          <div class="review-col-right">
            <div class="review-card">
              <div class="review-card-head">
                <h3 class="review-card-title">
                  <i class="fa-solid fa-list-check" aria-hidden="true"></i> Điều kiện duyệt
                </h3>
                <c:if test="${item.leaveRequest.pending}">
                  <span class="badge ${item.canApprove ? 'badge-success' : 'badge-warning'}">${item.canApprove ? 'Đủ điều kiện' : 'Cần kiểm tra'}</span>
                </c:if>
              </div>

              <div class="review-check-list">
                <c:forEach var="c" items="${item.checks}">
                  <div class="review-check-item ${c.ok ? 'pass' : 'fail'}">
                    <div class="check-icon-badge">
                      <i class="fa-solid ${c.ok ? 'fa-check' : 'fa-xmark'}" aria-hidden="true"></i>
                    </div>
                    <div class="check-meta-content">
                      <div class="check-meta-title"><c:out value="${c.label}" /></div>
                      <div class="check-meta-msg"><c:out value="${c.message}" /></div>

                      <c:if test="${c.key == 'SESSIONS' and not empty item.sessionConflicts}">
                        <details class="review-extra">
                          <summary><c:out value="${fn:length(item.sessionConflicts)}" /> buổi dạy trùng lịch</summary>
                          <ul class="leave-conflicts">
                            <c:forEach var="session" items="${item.sessionConflicts}">
                              <li>Buổi #${session.sessionId} · ${gms:formatDate(session.date)} · ${gms:formatTime(session.startTime)}–${gms:formatTime(session.endTime)} · Đã lên lịch</li>
                            </c:forEach>
                          </ul>
                          <p class="leave-help">Chức năng PT đổi/hủy/bàn giao buổi chưa được triển khai. Liên hệ PT và xử lý theo quy trình của phòng gym; chỉ duyệt khi hệ thống không còn buổi trùng.</p>
                        </details>
                      </c:if>

                      <%-- Nếu là mục Định biên và có bảng chi tiết từng ca --%>
                      <c:if test="${c.key == 'STAFFING' and not empty item.staffingRows}">
                        <details class="review-extra">
                          <summary>Xem quân số từng ca</summary>
                          <div class="review-table-wrap">
                            <table class="review-staffing-table">
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
                </c:forEach>
              </div>
              <details class="review-extra review-check-notes">
                <summary>Cách kiểm tra</summary>
                <p class="leave-help">Tạm tính nhân viên đang làm việc làm cả hai ca. Hệ thống kiểm tra lại trước khi duyệt.</p>
                <p class="leave-help">Đơn khác cùng ngày/ca: ${empty item.overlappingCounts['Approved'] ? 0 : item.overlappingCounts['Approved']} đã duyệt · ${empty item.overlappingCounts['Pending'] ? 0 : item.overlappingCounts['Pending']} chờ. Đơn chờ chưa được tính là nghỉ chính thức.</p>
              </details>
            </div>

          </div>

          <%-- Lịch sử dưới hai cột, thu gọn mặc định để giữ thông tin chính trong màn hình. --%>
          <details class="review-card review-history">
              <summary class="review-card-title">
                <i class="fa-solid fa-clock-rotate-left" aria-hidden="true"></i> Lịch sử xử lý
                <span class="leave-help"><c:out value="${fn:length(item.timeline)}" /> mốc</span>
              </summary>

              <div class="timeline-list">
                <div class="timeline-track-line"></div>
                <c:forEach var="node" items="${item.timeline}">
                  <div class="timeline-step ${node.state}">
                    <div class="timeline-dot">
                      <c:choose>
                        <c:when test="${node.state == 'DONE'}"><i class="fa-solid fa-check"></i></c:when>
                        <c:when test="${node.state == 'CURRENT'}"><i class="fa-solid fa-clock"></i></c:when>
                        <c:when test="${node.state == 'FAILED'}"><i class="fa-solid fa-xmark"></i></c:when>
                        <c:otherwise><i class="fa-solid fa-circle"></i></c:otherwise>
                      </c:choose>
                    </div>

                    <div class="timeline-step-title">
                      <span><c:out value="${node.title}" /></span>
                      <c:if test="${node.at != null}">
                        <span class="timeline-step-time">${gms:formatDateTime(node.at)}</span>
                      </c:if>
                    </div>

                    <c:if test="${not empty node.actorName}">
                      <div class="timeline-step-actor">
                        <c:choose>
                          <c:when test="${node.kind == 'TRANSFERRED'}">
                            <c:out value="${node.actorName}" /> → <c:out value="${node.targetName}" />
                          </c:when>
                          <c:otherwise>
                            <c:out value="${node.actorName}" />
                          </c:otherwise>
                        </c:choose>
                      </div>
                    </c:if>

                    <c:if test="${not empty node.note}">
                      <div class="timeline-step-sub"><c:out value="${node.note}" /></div>
                    </c:if>
                  </div>
                </c:forEach>
              </div>
          </details>

        </div>

      </main>
    </div>
  </div>

  <%-- ===== MODAL XÁC NHẬN DUYỆT ĐƠN ===== --%>
  <div id="modalApproveDetail" class="gms-modal" role="dialog" aria-modal="true" aria-labelledby="modalApproveTitle">
    <div class="gms-modal-dialog">
      <form action="${pageContext.request.contextPath}/manager/leave-requests/review" method="post">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="approve">
        <input type="hidden" name="leaveRequestId" value="${item.leaveRequest.leaveRequestId}">

        <div class="gms-modal-header">
          <h3 class="gms-modal-title" id="modalApproveTitle">Phê duyệt đơn nghỉ phép</h3>
          <button type="button" class="gms-modal-close" onclick="closeModal('modalApproveDetail')">&times;</button>
        </div>
        <div class="gms-modal-body">
          <p>Xác nhận duyệt đơn nghỉ phép của <b><c:out value="${item.fullName}" /></b>?</p>
          <div style="font-size:var(--font-size-xs);color:var(--color-text-soft);line-height:1.6">
            • Thời gian nghỉ: ${gms:formatDate(item.leaveRequest.fromDate)}<c:if test="${item.leaveRequest.toDate != item.leaveRequest.fromDate}"> → ${gms:formatDate(item.leaveRequest.toDate)}</c:if> (<fmt:formatNumber value="${item.leaveRequest.days}" maxFractionDigits="1" /> ngày)<br>
            • Ca làm việc: <c:out value="${item.leaveRequest.shiftType.displayName}" /><br>
            • Loại nghỉ: <c:out value="${item.leaveRequest.leaveType.displayName}" /><br>
            <c:if test="${item.leaveRequest.leaveType.dbValue == 'Annual'}">• Quỹ phép năm sẽ ghi nhận thêm <fmt:formatNumber value="${item.leaveRequest.days}" maxFractionDigits="1" /> ngày đã duyệt; không trừ hai lần khi gửi lại.<br></c:if>
            Hệ thống sẽ cập nhật trạng thái đơn thành <b>Đã duyệt</b> và gửi thông báo đến chuông của nhân viên.
          </div>
        </div>
        <div class="gms-modal-footer">
          <button type="button" class="btn btn-ghost" onclick="closeModal('modalApproveDetail')">Hủy</button>
          <button type="submit" class="btn btn-primary" ${item.canApprove ? '' : 'disabled'}>Xác nhận duyệt</button>
        </div>
      </form>
    </div>
  </div>

  <%-- ===== MODAL TỪ CHỐI ĐƠN ===== --%>
  <div id="modalRejectDetail" class="gms-modal" role="dialog" aria-modal="true" aria-labelledby="modalRejectTitle">
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
  </div>

</body>
</html>
