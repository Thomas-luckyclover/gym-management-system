<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Chi tiết đơn nghỉ phép #${item.leaveRequest.leaveRequestId} · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/leave-requests.css?v=20261021-bf05-2">
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
              <button type="button" class="btn btn-sm btn-outline" style="color:var(--color-danger);border-color:var(--color-danger)" onclick="openRejectModal()">
                <i class="fa-solid fa-xmark" aria-hidden="true"></i> Từ chối
              </button>

              <%-- Nút Chuyển người duyệt --%>
              <button type="button" class="btn btn-sm btn-outline" onclick="openTransferModal()">
                <i class="fa-solid fa-share" aria-hidden="true"></i> Chuyển người duyệt
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
                  <button type="button" class="btn btn-sm btn-primary" onclick="openApproveModal()">
                    <i class="fa-solid fa-check" aria-hidden="true"></i> Duyệt đơn
                  </button>
                </c:otherwise>
              </c:choose>
            </c:if>
          </div>
        </div>

        <%-- ===== Banner thông báo / Cảnh báo hệ thống ===== --%>
        <c:if test="${not empty item.banner}">
          <div class="alert-banner ${item.leaveDatePassed ? 'danger' : 'warning'}" style="margin-bottom:var(--space-4);padding:var(--space-3) var(--space-4);border-radius:var(--radius-md);display:flex;align-items:center;gap:var(--space-3)">
            <i class="fa-solid ${item.leaveDatePassed ? 'fa-circle-exclamation' : 'fa-triangle-exclamation'}" aria-hidden="true"></i>
            <span><c:out value="${item.banner}" /></span>
          </div>
        </c:if>

        <c:if test="${not empty formMessage}">
          <div class="alert-banner danger" style="margin-bottom:var(--space-4);padding:var(--space-3) var(--space-4);border-radius:var(--radius-md);display:flex;align-items:center;gap:var(--space-3)">
            <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
            <c:out value="${formMessage}" />
          </div>
        </c:if>

        <%-- ===== Lưới chính 2 cột (Tối ưu 1366x768) ===== --%>
        <div class="review-grid-2col">

          <%-- CỘT TRÁI (60%): Thông tin đơn & Kiểm tra điều kiện --%>
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
                    <span style="font-weight:normal;color:var(--color-text-soft)">(${item.leaveRequest.days} ngày)</span>
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

            <%-- Card 2: Kiểm tra điều kiện duyệt (Review Checks) --%>
            <div class="review-card">
              <div class="review-card-head">
                <h3 class="review-card-title">
                  <i class="fa-solid fa-list-check" aria-hidden="true"></i> Kiểm tra điều kiện phê duyệt
                </h3>
                <span class="leave-help">3 điều kiện · kiểm tra lại khi duyệt</span>
              </div>

              <p class="leave-help">Giả định ca cố định: nhân viên Working làm cả hai ca. Chưa có lịch phân ca thực tế; giờ ca chưa được cấu hình riêng.</p>
              <c:url var="overlapUrl" value="/manager/leave-requests">
                <c:param name="status" value="All" />
                <c:param name="fromDate" value="${item.leaveRequest.fromDate}" />
                <c:param name="toDate" value="${item.leaveRequest.toDate}" />
              </c:url>
              <p class="leave-help">Đơn khác cùng khoảng ngày/ca: ${empty item.overlappingCounts['Approved'] ? 0 : item.overlappingCounts['Approved']} đã duyệt · ${empty item.overlappingCounts['Pending'] ? 0 : item.overlappingCounts['Pending']} chờ xử lý.
                <a href="<c:out value='${overlapUrl}' />">Xem đơn trong khoảng ngày</a>. Đơn chờ chưa được tính là nghỉ chính thức.
              </p>
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
                        <ul class="leave-conflicts">
                          <c:forEach var="session" items="${item.sessionConflicts}">
                            <li>Buổi #${session.sessionId} · ${gms:formatDate(session.date)} · ${gms:formatTime(session.startTime)}–${gms:formatTime(session.endTime)} · Đã lên lịch</li>
                          </c:forEach>
                        </ul>
                        <p class="leave-help">Chức năng PT đổi/hủy/bàn giao buổi chưa được triển khai. Liên hệ PT và xử lý theo quy trình của phòng gym; chỉ duyệt khi hệ thống không còn buổi trùng.</p>
                      </c:if>

                      <%-- Nếu là mục Định biên và có bảng chi tiết từng ca --%>
                      <c:if test="${c.key == 'STAFFING' and not empty item.staffingRows}">
                        <table style="width:100%;margin-top:var(--space-2);border-collapse:collapse;font-size:11px">
                          <thead>
                            <tr style="border-bottom:1px solid var(--color-border);color:var(--color-text-soft);text-align:left">
                              <th style="padding:2px 4px">Ca trực</th>
                              <th style="padding:2px 4px">Trước đơn này (giả định)</th>
                              <th style="padding:2px 4px">Sau duyệt</th>
                              <th style="padding:2px 4px">Tối thiểu</th>
                              <th style="padding:2px 4px">Kết quả</th>
                            </tr>
                          </thead>
                          <tbody>
                            <c:forEach var="row" items="${item.staffingRows}">
                              <tr style="border-bottom:1px solid var(--color-border-subtle)">
                                <td style="padding:2px 4px"><c:out value="${row.shiftLabel}" /></td>
                                <td style="padding:2px 4px">${row.current}</td>
                                <td style="padding:2px 4px"><b>${row.afterApproval}</b></td>
                                <td style="padding:2px 4px">${row.minRequired}</td>
                                <td style="padding:2px 4px">
                                  <c:choose>
                                    <c:when test="${row.belowMin}"><span class="badge badge-sm badge-danger">Thiếu người</span></c:when>
                                    <c:otherwise><span class="badge badge-sm badge-success">Đạt</span></c:otherwise>
                                  </c:choose>
                                </td>
                              </tr>
                            </c:forEach>
                          </tbody>
                        </table>
                      </c:if>
                    </div>
                  </div>
                </c:forEach>
              </div>
            </div>

          </div>

          <%-- CỘT PHẢI (40%): Quy trình duyệt & Người phụ trách --%>
          <div class="review-col-right">

            <%-- Card: Quy trình duyệt (Timeline) --%>
            <div class="review-card">
              <div class="review-card-head">
                <h3 class="review-card-title">
                  <i class="fa-solid fa-clock-rotate-left" aria-hidden="true"></i> Tiến trình xử lý
                </h3>
              </div>

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
            </div>

            <%-- Card: Người duyệt phụ trách --%>
            <div class="review-card">
              <div class="review-card-head">
                <h3 class="review-card-title">
                  <i class="fa-solid fa-user-shield" aria-hidden="true"></i> Phụ trách xử lý
                </h3>
              </div>

              <div style="display:flex;align-items:center;justify-content:space-between">
                <div>
                  <div style="font-weight:600;font-size:var(--font-size-sm)">
                    <c:choose>
                      <c:when test="${not empty item.reviewerName}">
                        <c:out value="${item.reviewerName}" /> (Đã duyệt)
                      </c:when>
                      <c:when test="${not empty item.assignedToName}">
                        <c:out value="${item.assignedToName}" />
                      </c:when>
                      <c:otherwise>
                        Chưa phân công (Quản lý chung)
                      </c:otherwise>
                    </c:choose>
                  </div>
                  <div style="font-size:var(--font-size-xs);color:var(--color-text-soft);margin-top:2px">
                    <c:choose>
                      <c:when test="${item.canAct}">Bạn đang có quyền thao tác trên đơn này</c:when>
                      <c:otherwise>Bạn đang ở chế độ chỉ xem</c:otherwise>
                    </c:choose>
                  </div>
                </div>

                <c:if test="${item.leaveRequest.pending and item.canAct}">
                  <button type="button" class="btn btn-sm btn-ghost" onclick="openTransferModal()" title="Chuyển cho Manager khác">
                    <i class="fa-solid fa-arrow-right-arrow-left"></i> Chuyển
                  </button>
                </c:if>
              </div>
            </div>

          </div>

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
            • Thời gian nghỉ: ${gms:formatDate(item.leaveRequest.fromDate)} - ${gms:formatDate(item.leaveRequest.toDate)} (${item.leaveRequest.days} ngày)<br>
            • Ca làm việc: <c:out value="${item.leaveRequest.shiftType.displayName}" /><br>
            • Loại nghỉ: <c:out value="${item.leaveRequest.leaveType.displayName}" /><br>
            <c:if test="${item.leaveRequest.leaveType.dbValue == 'Annual'}">• Quỹ phép năm sẽ ghi nhận thêm ${item.leaveRequest.days} ngày đã duyệt; không trừ hai lần khi gửi lại.<br></c:if>
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

  <%-- ===== MODAL CHUYỂN NGƯỜI DUYỆT ===== --%>
  <div id="modalTransferDetail" class="gms-modal" role="dialog" aria-modal="true" aria-labelledby="modalTransferTitle">
    <div class="gms-modal-dialog">
      <form action="${pageContext.request.contextPath}/manager/leave-requests/review" method="post">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="transfer">
        <input type="hidden" name="leaveRequestId" value="${item.leaveRequest.leaveRequestId}">

        <div class="gms-modal-header">
          <h3 class="gms-modal-title" id="modalTransferTitle">Chuyển quyền duyệt đơn</h3>
          <button type="button" class="gms-modal-close" onclick="closeModal('modalTransferDetail')">&times;</button>
        </div>
        <div class="gms-modal-body">
          <div class="field">
            <label class="field-label" for="transferToEmployeeSelect">Chọn người tiếp nhận <span aria-hidden="true">*</span></label>
            <select name="toEmployeeId" id="transferToEmployeeSelect" class="field-input" required>
              <option value="">-- Chọn Quản lý --</option>
              <c:forEach var="rev" items="${eligibleReviewers}">
                <option value="${rev.employeeId}">
                  <c:out value="${rev.fullName}" /> (<c:out value="${rev.employeeCode}" />)
                </option>
              </c:forEach>
            </select>
          </div>

          <div class="field" style="margin-top:var(--space-3)">
            <label class="field-label" for="transferNoteDetailInput">Ghi chú chuyển giao (tùy chọn)</label>
            <textarea id="transferNoteDetailInput"
                      name="note"
                      class="field-input"
                      rows="2"
                      maxlength="200"
                      placeholder="Ghi chú cho người nhận (tối đa 200 ký tự)..."></textarea>
          </div>
        </div>
        <div class="gms-modal-footer">
          <button type="button" class="btn btn-ghost" onclick="closeModal('modalTransferDetail')">Hủy</button>
          <button type="submit" class="btn btn-primary">Xác nhận chuyển</button>
        </div>
      </form>
    </div>
  </div>

  <script>
    let modalTrigger = null;

    // Đóng hộp thoại và trả focus cho nút đã mở.
    function closeModal(id) {
      document.getElementById(id).classList.remove('show');
      if (modalTrigger) {
        modalTrigger.focus();
      }
    }

    // Các nút tác vụ chỉ mở hộp thoại; form POST do backend kiểm tra lại.
    function openModal(id) {
      modalTrigger = document.activeElement;
      const modal = document.getElementById(id);
      modal.classList.add('show');
      const firstControl = modal.querySelector('button, select, textarea');
      if (firstControl) {
        firstControl.focus();
      }
    }

    function openApproveModal() {
      openModal('modalApproveDetail');
    }

    function openRejectModal() {
      openModal('modalRejectDetail');
    }

    function openTransferModal() {
      openModal('modalTransferDetail');
    }

    function toggleReasonClamp() {
      const text = document.getElementById('reasonClampText');
      const btn = document.getElementById('btnToggleReason');
      if (text.classList.contains('expanded')) {
        text.classList.remove('expanded');
        btn.innerText = 'Xem thêm';
      } else {
        text.classList.add('expanded');
        btn.innerText = 'Thu gọn';
      }
    }

    // Kiểm tra nếu nội dung lý do quá ngắn thì ẩn nút "Xem thêm"
    window.addEventListener('DOMContentLoaded', function() {
      const text = document.getElementById('reasonClampText');
      const btn = document.getElementById('btnToggleReason');
      if (text && btn) {
        if (text.scrollHeight <= text.clientHeight + 2) {
          btn.style.display = 'none';
        }
      }
    });

    window.addEventListener('keydown', function(e) {
      const modal = document.querySelector('.gms-modal.show');
      if (!modal) {
        return;
      }
      if (e.key === 'Escape') {
        closeModal(modal.id);
      }
      if (e.key === 'Tab') {
        const controls = modal.querySelectorAll('button:not([disabled]), select, textarea');
        const first = controls[0];
        const last = controls[controls.length - 1];
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    });

    window.addEventListener('click', function(e) {
      if (e.target.classList.contains('gms-modal')) {
        closeModal(e.target.id);
      }
    });
  </script>
</body>
</html>
