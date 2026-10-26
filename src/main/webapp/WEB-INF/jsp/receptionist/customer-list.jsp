<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Hồ sơ khách hàng · Super Gym Lễ tân</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/customer-list.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">

  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js" defer></script>
  <script src="${pageContext.request.contextPath}/js/pages/receptionist/customer-list.js" defer></script>
</head>

<body class="dock theme-staff">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Quản lý hồ sơ khách hàng</h1>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content customer-page" id="main-content" tabindex="-1">
        <%-- Tiêu đề và Tabs chuyển đổi --%>
        <div class="page-header-row">
          <div class="page-title-group">
            <h2>Hồ sơ khách hàng</h2>
            <p>Tra cứu thông tin hội viên, quản lý hồ sơ và gửi yêu cầu xóa</p>
          </div>
        </div>

        <%-- Thanh điều hướng Tab --%>
        <nav class="tab-nav" aria-label="Tab hồ sơ">
          <a href="${pageContext.request.contextPath}/receptionist/customers?tab=profiles"
             class="tab-btn<c:if test="${currentTab == 'profiles'}"> active</c:if>">
            <span>Danh sách khách hàng</span>
            <c:if test="${currentTab == 'profiles'}">
              <span class="tab-badge"><c:out value="${totalCount}" /></span>
            </c:if>
          </a>
          <a href="${pageContext.request.contextPath}/receptionist/customers?tab=my-requests"
             class="tab-btn<c:if test="${currentTab == 'my-requests'}"> active</c:if>">
            <span>Yêu cầu xóa của tôi</span>
            <c:if test="${currentTab == 'my-requests'}">
              <span class="tab-badge"><c:out value="${totalCount}" /></span>
            </c:if>
          </a>
        </nav>

        <c:choose>
          <%-- ================= TAB 1: DANH SÁCH HỒ SƠ ================= --%>
          <c:when test="${currentTab == 'profiles'}">
            <%-- Khung tìm kiếm --%>
            <section class="filter-card">
              <form action="${pageContext.request.contextPath}/receptionist/customers" method="get" class="search-form">
                <input type="hidden" name="tab" value="profiles" />
                <div class="search-input-wrap">
                  <svg class="search-icon" viewBox="0 0 24 24">
                    <circle cx="11" cy="11" r="8"></circle>
                    <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                  </svg>
                  <input type="text"
                         name="keyword"
                         class="search-input"
                         placeholder="Tìm theo họ tên, số điện thoại, email, mã KH..."
                         value="<c:out value="${keyword}" />" />
                </div>
                <button type="submit" class="btn-primary">Tìm kiếm</button>
                <c:if test="${not empty keyword}">
                  <a href="${pageContext.request.contextPath}/receptionist/customers?tab=profiles" class="btn-secondary">Xóa bộ lọc</a>
                </c:if>
              </form>
            </section>

            <%-- Bảng danh sách khách hàng --%>
            <section class="table-card">
              <div class="table-responsive">
                <table class="data-table">
                  <thead>
                    <tr>
                      <th>Khách hàng</th>
                      <th>Số điện thoại</th>
                      <th>Email</th>
                      <th>Loại khách</th>
                      <th>Gói tập hiện tại</th>
                      <th>Trạng thái</th>
                      <th style="text-align: right;">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    <c:choose>
                      <c:when test="${empty customers}">
                        <tr>
                          <td colspan="7" style="text-align: center; padding: var(--space-8); color: var(--color-text-muted);">
                            Không tìm thấy hồ sơ khách hàng nào phù hợp.
                          </td>
                        </tr>
                      </c:when>
                      <c:otherwise>
                        <c:forEach var="c" items="${customers}">
                          <tr>
                            <td>
                              <div class="cust-cell">
                                <div class="cust-avatar">
                                  <c:out value="${fn:substring(c.fullName, 0, 1)}" />
                                </div>
                                <div class="cust-info">
                                  <span class="cust-name"><c:out value="${c.fullName}" /></span>
                                  <span class="cust-code"><c:out value="${c.customerCode}" /></span>
                                </div>
                              </div>
                            </td>
                            <td><c:out value="${c.phone}" default="—" /></td>
                            <td><c:out value="${c.email}" default="—" /></td>
                            <td>
                              <c:choose>
                                <c:when test="${c.customerType == 'Dependent'}">Phụ thuộc</c:when>
                                <c:otherwise>Tự đăng ký</c:otherwise>
                              </c:choose>
                            </td>
                            <td>
                              <c:choose>
                                <c:when test="${not empty c.currentPackageName}">
                                  <span class="badge-package"><c:out value="${c.currentPackageName}" /></span>
                                </c:when>
                                <c:otherwise>
                                  <span style="color: var(--color-text-muted);">Chưa có gói</span>
                                </c:otherwise>
                              </c:choose>
                            </td>
                            <td>
                              <c:choose>
                                <c:when test="${c.hasPendingDeletion}">
                                  <span class="badge badge-pending">
                                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                      <circle cx="12" cy="12" r="10"></circle>
                                      <polyline points="12 6 12 12 16 14"></polyline>
                                    </svg>
                                    Chờ xóa (#<c:out value="${c.pendingRequestId}" />)
                                  </span>
                                </c:when>
                                <c:otherwise>
                                  <span class="badge badge-active">Hoạt động</span>
                                </c:otherwise>
                              </c:choose>
                            </td>
                            <td style="text-align: right;">
                              <button type="button"
                                      class="btn-delete btn-open-delete-modal"
                                      data-customer-id="<c:out value="${c.customerId}" />"
                                      data-customer-name="<c:out value="${c.fullName}" />"
                                      data-customer-code="<c:out value="${c.customerCode}" />"
                                      data-has-active="<c:out value="${c.hasActivePackage}" />"
                                      <c:if test="${c.hasPendingDeletion}">
                                        disabled title="Hồ sơ đã có yêu cầu xóa đang chờ duyệt (CBR-16)"
                                      </c:if>>
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                                  <polyline points="3 6 5 6 21 6"></polyline>
                                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                                </svg>
                                Xóa hồ sơ
                              </button>
                            </td>
                          </tr>
                        </c:forEach>
                      </c:otherwise>
                    </c:choose>
                  </tbody>
                </table>
              </div>

              <%-- Phân trang --%>
              <c:if test="${totalPages > 1}">
                <div class="pagination-row">
                  <div>Trang <b><c:out value="${page}" /></b> / <c:out value="${totalPages}" /> (Tổng <c:out value="${totalCount}" /> khách hàng)</div>
                  <div class="pagination-controls">
                    <a href="${pageContext.request.contextPath}/receptionist/customers?tab=profiles&page=${page - 1}&pageSize=${pageSize}&keyword=${keyword}"
                       class="page-link<c:if test="${page <= 1}"> disabled</c:if>">Trước</a>
                    <c:forEach var="p" begin="1" end="${totalPages}">
                      <a href="${pageContext.request.contextPath}/receptionist/customers?tab=profiles&page=${p}&pageSize=${pageSize}&keyword=${keyword}"
                         class="page-link<c:if test="${p == page}"> active</c:if>"><c:out value="${p}" /></a>
                    </c:forEach>
                    <a href="${pageContext.request.contextPath}/receptionist/customers?tab=profiles&page=${page + 1}&pageSize=${pageSize}&keyword=${keyword}"
                       class="page-link<c:if test="${page >= totalPages}"> disabled</c:if>">Sau</a>
                  </div>
                </div>
              </c:if>
            </section>
          </c:when>

          <%-- ================= TAB 2: YÊU CẦU CỦA TÔI (Q8) ================= --%>
          <c:otherwise>
            <section class="table-card">
              <div class="table-responsive">
                <table class="data-table">
                  <thead>
                    <tr>
                      <th>Mã đơn</th>
                      <th>Khách hàng</th>
                      <th>Lý do yêu cầu</th>
                      <th>Thời điểm gửi</th>
                      <th>Trạng thái</th>
                      <th>Phản hồi / Ghi chú</th>
                    </tr>
                  </thead>
                  <tbody>
                    <c:choose>
                      <c:when test="${empty myRequests}">
                        <tr>
                          <td colspan="6" style="text-align: center; padding: var(--space-8); color: var(--color-text-muted);">
                            Bạn chưa gửi yêu cầu xóa hồ sơ nào.
                          </td>
                        </tr>
                      </c:when>
                      <c:otherwise>
                        <c:forEach var="r" items="${myRequests}">
                          <tr>
                            <td><b>#<c:out value="${r.deletionRequest.customerDeletionRequestId}" /></b></td>
                            <td>
                              <div class="cust-info">
                                <span class="cust-name"><c:out value="${r.customerFullName}" /></span>
                                <span class="cust-code"><c:out value="${r.customerCode}" /></span>
                              </div>
                            </td>
                            <td><c:out value="${r.deletionRequest.reason}" /></td>
                            <td><c:out value="${r.deletionRequest.requestedAt}" /></td>
                            <td>
                              <c:choose>
                                <c:when test="${r.deletionRequest.pending}">
                                  <span class="badge badge-pending">Chờ duyệt</span>
                                </c:when>
                                <c:when test="${r.deletionRequest.approved}">
                                  <span class="badge badge-active">Đã duyệt</span>
                                </c:when>
                                <c:otherwise>
                                  <span class="badge badge-rejected">Từ chối</span>
                                </c:otherwise>
                              </c:choose>
                            </td>
                            <td>
                              <c:choose>
                                <c:when test="${not empty r.deletionRequest.rejectReason}">
                                  <span style="color: var(--color-danger);"><c:out value="${r.deletionRequest.rejectReason}" /></span>
                                </c:when>
                                <c:when test="${r.deletionRequest.approved}">
                                  <span style="color: var(--color-success);">Duyệt bởi <c:out value="${r.reviewerName}" default="Quản lý" /></span>
                                </c:when>
                                <c:otherwise>
                                  <span style="color: var(--color-text-muted);">Đang chờ xử lý</span>
                                </c:otherwise>
                              </c:choose>
                            </td>
                          </tr>
                        </c:forEach>
                      </c:otherwise>
                    </c:choose>
                  </tbody>
                </table>
              </div>

              <%-- Phân trang tab Yêu cầu --%>
              <c:if test="${totalPages > 1}">
                <div class="pagination-row">
                  <div>Trang <b><c:out value="${page}" /></b> / <c:out value="${totalPages}" /> (Tổng <c:out value="${totalCount}" /> yêu cầu)</div>
                  <div class="pagination-controls">
                    <a href="${pageContext.request.contextPath}/receptionist/customers?tab=my-requests&page=${page - 1}&pageSize=${pageSize}"
                       class="page-link<c:if test="${page <= 1}"> disabled</c:if>">Trước</a>
                    <c:forEach var="p" begin="1" end="${totalPages}">
                      <a href="${pageContext.request.contextPath}/receptionist/customers?tab=my-requests&page=${p}&pageSize=${pageSize}"
                         class="page-link<c:if test="${p == page}"> active</c:if>"><c:out value="${p}" /></a>
                    </c:forEach>
                    <a href="${pageContext.request.contextPath}/receptionist/customers?tab=my-requests&page=${page + 1}&pageSize=${pageSize}"
                       class="page-link<c:if test="${page >= totalPages}"> disabled</c:if>">Sau</a>
                  </div>
                </div>
              </c:if>
            </section>
          </c:otherwise>
        </c:choose>
      </main>
    </div>
  </div>

  <%-- ================= MODAL XÓA HỒ SƠ KHÁCH HÀNG ================= --%>
  <div class="modal-overlay" id="deleteModal" aria-hidden="true" role="dialog" aria-labelledby="modalTitle">
    <div class="modal-box">
      <div class="modal-head">
        <h3 id="modalTitle">Xác nhận xóa hồ sơ khách hàng</h3>
        <button type="button" class="modal-close-btn btn-close-modal" aria-label="Đóng">&times;</button>
      </div>

      <form id="deleteCustomerForm" action="${pageContext.request.contextPath}/receptionist/customers" method="post">
        <input type="hidden" name="action" value="delete" />
        <input type="hidden" name="customerId" id="modalCustomerId" value="" />

        <div class="modal-body">
          <div>
            Bạn đang yêu cầu xóa hồ sơ cho khách hàng:
            <br />
            <strong id="modalCustomerName">Nguyễn Văn A</strong> (<span id="modalCustomerCode">KH-0001</span>)
          </div>

          <%-- Cảnh báo khi khách còn gói/buổi tập --%>
          <div class="modal-alert modal-alert-warning" id="modalAlertWarning">
            <strong>Lưu ý quan trọng:</strong> Khách hàng này hiện còn <b>gói tập đang hoạt động</b> hoặc <b>buổi tập PT đã xếp lịch</b>.
            Theo quy định hệ thống, yêu cầu này sẽ được gửi tới <b>Quản lý phê duyệt</b> trước khi xóa.
          </div>

          <%-- Cảnh báo khi khách không còn tài sản gì --%>
          <div class="modal-alert modal-alert-info" id="modalAlertInfo">
            Khách hàng không còn gói tập hoạt động hoặc buổi PT nào. Hồ sơ sẽ được <b>ngừng hoạt động ngay lập tức</b>.
          </div>

          <div class="form-group">
            <label class="form-label" for="deletionReason">
              Lý do xóa <span style="color: var(--color-danger);">*</span>
            </label>
            <textarea id="deletionReason"
                      name="reason"
                      class="form-textarea"
                      placeholder="Nhập lý do chi tiết (tối thiểu 5 ký tự, tối đa 500 ký tự)..."
                      required></textarea>
            <div class="char-counter">
              <span id="charCount">0</span>/500 ký tự
            </div>
          </div>
        </div>

        <div class="modal-foot">
          <button type="button" class="btn-secondary btn-close-modal">Hủy bỏ</button>
          <button type="submit" class="btn-primary" id="modalSubmitBtn" style="background-color: var(--color-danger);">
            Xác nhận
          </button>
        </div>
      </form>
    </div>
  </div>
</body>
</html>
