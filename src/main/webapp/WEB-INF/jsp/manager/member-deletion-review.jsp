<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Phê duyệt yêu cầu xóa hồ sơ · #<c:out value="${review.deletionRequest.customerDeletionRequestId}" /> · Super Gym Quản lý</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/customer-list.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/member-list.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/member-deletion-review.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">

  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
  <script src="${pageContext.request.contextPath}/js/pages/manager/member-deletion-review.js" defer></script>
</head>

<body class="dock theme-manager">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Phê duyệt yêu cầu xóa hồ sơ</h1>
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

      <main class="content review-page" id="main-content" tabindex="-1">
        <%-- Breadcrumb --%>
        <nav class="review-breadcrumb" aria-label="Đường dẫn">
          <a href="${pageContext.request.contextPath}/manager/members?tab=pending-deletions">Chờ duyệt xóa</a>
          <span>/</span>
          <span>Yêu cầu #<c:out value="${review.deletionRequest.customerDeletionRequestId}" /></span>
        </nav>

        <%-- Cảnh báo vi phạm CBR-115: Tự duyệt chính mình --%>
        <c:if test="${review.ownProfile}">
          <div class="review-alert review-alert-danger">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
              <line x1="12" y1="9" x2="12" y2="13"></line>
              <line x1="12" y1="17" x2="12.01" y2="17"></line>
            </svg>
            <div>
              <strong>CBR-115:</strong> Hồ sơ khách hàng này thuộc về chính tài khoản của bạn.
              Theo quy định phân quyền, bạn <b>không được phép tự phê duyệt hoặc từ chối</b> yêu cầu xóa hồ sơ của chính mình (MSG76). Vui lòng nhờ một Quản lý khác xử lý.
            </div>
          </div>
        </c:if>

        <%-- Cảnh báo nếu yêu cầu đã được xử lý trước đó --%>
        <c:if test="${!review.deletionRequest.pending}">
          <div class="review-alert review-alert-info">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="16" x2="12" y2="12"></line>
              <line x1="12" y1="8" x2="12.01" y2="8"></line>
            </svg>
            <div>
              Yêu cầu này đã được <b><c:out value="${review.reviewerName}" default="Quản lý" /></b> xử lý vào lúc
              <b><c:out value="${review.deletionRequest.reviewedAt}" /></b> với kết quả:
              <c:choose>
                <c:when test="${review.deletionRequest.approved}">
                  <span class="m-badge m-badge-active" style="margin-left: var(--space-2);">ĐÃ PHÊ DUYỆT</span>
                </c:when>
                <c:otherwise>
                  <span class="m-badge m-badge-inactive" style="margin-left: var(--space-2); color: var(--color-danger);">ĐÃ TỪ CHỐI</span>
                  <c:if test="${not empty review.deletionRequest.rejectReason}">
                    <br /><b>Lý do từ chối:</b> <c:out value="${review.deletionRequest.rejectReason}" />
                  </c:if>
                </c:otherwise>
              </c:choose>
            </div>
          </div>
        </c:if>

        <%-- Cảnh báo khách còn gói hoặc buổi PT Scheduled (CBR-16) --%>
        <c:if test="${review.hasActiveAssets()}">
          <div class="review-alert review-alert-warning">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"></circle>
              <polyline points="12 6 12 12 16 14"></polyline>
            </svg>
            <div>
              <strong>Lưu ý:</strong> Khách hàng hiện còn <b><c:out value="${fn:length(review.activePackages)}" /> gói tập còn hạn</b>
              và <b><c:out value="${fn:length(review.scheduledSessions)}" /> buổi PT đã xếp lịch</b>.
              Nếu bạn phê duyệt xóa, toàn bộ hồ sơ sẽ chuyển sang <b>Inactive</b> và tài khoản khách sẽ bị đăng xuất ngay lập tức.
            </div>
          </div>
        </c:if>

        <div class="review-grid">
          <%-- Cột trái: Chi tiết khách hàng, lý do xóa & danh sách gói --%>
          <div style="display: flex; flex-direction: column; gap: var(--space-5);">
            <%-- Thông tin hội viên --%>
            <div class="review-card">
              <div class="review-card-head">
                <h3>Thông tin hội viên cần xóa</h3>
                <span class="m-badge m-badge-active"><c:out value="${review.customerType}" /></span>
              </div>

              <div class="review-info-list">
                <div class="review-info-item">
                  <span class="review-info-label">Mã khách hàng</span>
                  <span class="review-info-value"><b><c:out value="${review.customerCode}" /></b></span>
                </div>
                <div class="review-info-item">
                  <span class="review-info-label">Họ và tên</span>
                  <span class="review-info-value"><c:out value="${review.customerFullName}" /></span>
                </div>
                <div class="review-info-item">
                  <span class="review-info-label">Số điện thoại</span>
                  <span class="review-info-value"><c:out value="${review.customerPhone}" default="—" /></span>
                </div>
                <div class="review-info-item">
                  <span class="review-info-label">Email</span>
                  <span class="review-info-value"><c:out value="${review.customerEmail}" default="—" /></span>
                </div>
                <div class="review-info-item">
                  <span class="review-info-label">Ngày tham gia</span>
                  <span class="review-info-value"><c:out value="${review.customerJoinedAt}" /></span>
                </div>
              </div>
            </div>

            <%-- Thông tin yêu cầu từ Lễ tân --%>
            <div class="review-card">
              <div class="review-card-head">
                <h3>Yêu cầu xóa từ Lễ tân</h3>
                <span class="review-info-value" style="font-size: var(--font-size-xs); color: var(--color-text-muted);">
                  Gửi lúc: <c:out value="${review.deletionRequest.requestedAt}" />
                </span>
              </div>

              <div>
                <span class="review-info-label">Lễ tân thực hiện</span>
                <div style="margin-top: var(--space-1); font-size: var(--font-size-sm); font-weight: var(--font-weight-medium);">
                  <c:out value="${review.requesterName}" /> (<c:out value="${review.requesterCode}" />)
                </div>
              </div>

              <div>
                <span class="review-info-label">Lý do yêu cầu xóa</span>
                <div class="reason-box" style="margin-top: var(--space-1-5);">
                  "<c:out value="${review.deletionRequest.reason}" />"
                </div>
              </div>
            </div>

            <%-- Danh sách tài sản còn hạn (Gói tập / Buổi PT) --%>
            <div class="review-card">
              <div class="review-card-head">
                <h3>Tài sản đang hoạt động của khách</h3>
              </div>

              <c:choose>
                <c:when test="${review.hasActiveAssets()}">
                  <c:if test="${not empty review.activePackages}">
                    <div>
                      <h4 style="margin: 0 0 var(--space-2) 0; font-size: var(--font-size-sm); color: var(--color-text-secondary);">Gói tập đang chạy:</h4>
                      <ul style="font-size: var(--font-size-sm); padding-left: var(--space-4); margin: 0;">
                        <c:forEach var="pkg" items="${review.activePackages}">
                          <li style="margin-bottom: var(--space-1);">
                            <b><c:out value="${pkg.packageName}" /></b> (<c:out value="${pkg.packageType}" />)
                            — Hạn đến: <c:out value="${pkg.endDate}" />
                            <c:if test="${pkg.partner}"><span class="m-badge m-badge-package">Gói đôi</span></c:if>
                          </li>
                        </c:forEach>
                      </ul>
                    </div>
                  </c:if>

                  <c:if test="${not empty review.scheduledSessions}">
                    <div style="margin-top: var(--space-3);">
                      <h4 style="margin: 0 0 var(--space-2) 0; font-size: var(--font-size-sm); color: var(--color-text-secondary);">Buổi PT đã xếp lịch:</h4>
                      <ul style="font-size: var(--font-size-sm); padding-left: var(--space-4); margin: 0;">
                        <c:forEach var="sess" items="${review.scheduledSessions}">
                          <li style="margin-bottom: var(--space-1);">
                            Ngày <c:out value="${sess.slotDate}" /> lúc <c:out value="${sess.startTime}" /> — PT: <c:out value="${sess.ptName}" />
                          </li>
                        </c:forEach>
                      </ul>
                    </div>
                  </c:if>
                </c:when>
                <c:otherwise>
                  <p style="color: var(--color-text-muted); margin: 0; font-size: var(--font-size-sm);">
                    Khách hàng không còn gói tập hay buổi PT nào đang hoạt động.
                  </p>
                </c:otherwise>
              </c:choose>
            </div>
          </div>

          <%-- Cột phải: Hộp thao tác Quản lý --%>
          <div>
            <div class="action-box">
              <h4>Quyết định của Quản lý</h4>
              <p style="font-size: var(--font-size-xs); color: var(--color-text-secondary); margin: 0; line-height: var(--line-height-relaxed);">
                Kiểm tra kỹ thông tin trước khi thực hiện. Quyết định duyệt sẽ hủy bỏ các gói tập và chuyển hồ sơ sang Inactive.
              </p>

              <div class="action-buttons-stack">
                <%-- Nút Phê duyệt --%>
                <form id="approveForm" action="${pageContext.request.contextPath}/manager/member-deletions/review" method="post">
                  <input type="hidden" name="action" value="approve" />
                  <input type="hidden" name="id" value="<c:out value="${review.deletionRequest.customerDeletionRequestId}" />" />
                  <button type="submit" class="btn-approve" style="width: 100%;" <c:if test="${!review.canAct}">disabled</c:if>>
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="20 6 9 17 4 12"></polyline>
                    </svg>
                    Phê duyệt xóa hồ sơ
                  </button>
                </form>

                <%-- Nút Từ chối (mở modal) --%>
                <button type="button" class="btn-reject" id="btnOpenRejectModal" <c:if test="${!review.canAct}">disabled</c:if>>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"></line>
                    <line x1="6" y1="6" x2="18" y2="18"></line>
                  </svg>
                  Từ chối yêu cầu
                </button>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>

  <%-- ================= MODAL TỪ CHỐI YÊU CẦU XÓA ================= --%>
  <div class="modal-overlay" id="rejectModal" aria-hidden="true" role="dialog" aria-labelledby="rejectModalTitle">
    <div class="modal-box">
      <div class="modal-head">
        <h3 id="rejectModalTitle">Từ chối yêu cầu xóa hồ sơ</h3>
        <button type="button" class="modal-close-btn btn-close-reject-modal" aria-label="Đóng">&times;</button>
      </div>

      <form id="rejectForm" action="${pageContext.request.contextPath}/manager/member-deletions/review" method="post">
        <input type="hidden" name="action" value="reject" />
        <input type="hidden" name="id" value="<c:out value="${review.deletionRequest.customerDeletionRequestId}" />" />

        <div class="modal-body">
          <div>
            Bạn đang từ chối yêu cầu xóa hồ sơ của khách hàng
            <b><c:out value="${review.customerFullName}" /></b> (<c:out value="${review.customerCode}" />).
            <br />
            Hồ sơ khách hàng sẽ được giữ nguyên trạng thái hiện tại.
          </div>

          <div class="form-group">
            <label class="form-label" for="rejectReason">
              Lý do từ chối <span style="color: var(--color-danger);">*</span>
            </label>
            <textarea id="rejectReason"
                      name="rejectReason"
                      class="form-textarea"
                      placeholder="Nhập lý do từ chối gửi tới Lễ tân (tối thiểu 5 ký tự, tối đa 500 ký tự)..."
                      required></textarea>
            <div class="char-counter">
              <span id="rejectCharCount">0</span>/500 ký tự
            </div>
          </div>
        </div>

        <div class="modal-foot">
          <button type="button" class="btn-secondary btn-close-reject-modal">Hủy bỏ</button>
          <button type="submit" class="btn-primary" id="btnSubmitReject" style="background-color: var(--color-danger);">
            Xác nhận từ chối
          </button>
        </div>
      </form>
    </div>
  </div>
</body>
</html>
