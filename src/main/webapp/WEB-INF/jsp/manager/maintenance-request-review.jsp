<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Xử lý yêu cầu bảo trì · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/maintenance-requests.css?v=20261023fix1">
  <link rel="stylesheet"
        href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">

  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js"
          defer></script>
</head>

<body class="dock theme-manager">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <header class="topbar">

        <div class="tb-left">

          <h1 class="tb-title">
            Xử lý yêu cầu
            <c:out value="${item.request.requestCode}" />
          </h1>

        </div>


        <div class="top-right">

          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <a class="tb-user"
             href="${pageContext.request.contextPath}/account">

            <span class="avatar">
              QL
            </span>

            <span>

              <b>
                <c:out
                        value="${sessionScope.displayName}"
                        default="Quản lý" />
              </b>

              <small>Quản lý</small>

            </span>

          </a>

        </div>

      </header>

      <main class="content" id="main-content" tabindex="-1">

        <a class="maintenance-back"
           href="${pageContext.request.contextPath}/manager/maintenance-requests">
          ← Quay lại danh sách
        </a>

        <section class="panel maintenance-review">
          <div class="panel-head">
            <div>
              <h2>
                <c:out value="${item.itemCode}" /> · <c:out value="${item.facilityName}" />
              </h2>

              <p>
                Khu vực: <c:out value="${item.facilityLocation}" />
              </p>
            </div>

            <span class="badge
              ${item.request.open ? 'badge-warning' :
                item.request.inProgress ? 'badge-info' :
                item.request.closed ? 'badge-success' : 'badge-neutral'}">

              <c:out value="${item.request.status.displayName}" />
            </span>
          </div>

          <div class="maintenance-detail-grid">
            <div>
              <span class="maintenance-label">Mã máy cụ thể</span>
              <strong><c:out value="${item.itemCode}" /></strong>
            </div>

            <div>
              <span class="maintenance-label">Người báo</span>
              <strong><c:out value="${item.reporterName}" /></strong>
            </div>

            <div>
              <span class="maintenance-label">Ngày báo</span>
              <strong>
                ${gms:formatDate(item.request.reportedAt.toLocalDate())}
              </strong>
            </div>

            <div>
              <span class="maintenance-label">Mức ưu tiên</span>
              <strong>
                <c:out value="${item.request.priority.displayName}" />
              </strong>
            </div>

            <div>
              <span class="maintenance-label">Loại sự cố</span>
              <strong>
                <c:out value="${item.request.incidentType.displayName}" />
              </strong>
            </div>

            <div>
              <span class="maintenance-label">Nguồn phát hiện</span>
              <strong>
                <c:out value="${item.request.detectedBy.displayName}" />
              </strong>
            </div>

            <div>
              <span class="maintenance-label">Cảnh báo an toàn</span>
              <strong>
                ${item.request.warningTagPlaced ? 'Đã đặt cảnh báo' : 'Chưa đặt'}
              </strong>
            </div>
          </div>

          <div class="maintenance-description-box">
            <span class="maintenance-label">Mô tả hỏng hóc</span>
            <p><c:out value="${item.request.description}" /></p>
          </div>

          <c:if test="${not empty formMessage}">
            <div class="alert-banner danger">
              <c:out value="${formMessage}" />
            </div>
          </c:if>

          <%-- ===== Chỉ Open mới được xử lý ===== --%>
          <c:if test="${item.request.open}">
            <div class="maintenance-actions">

              <form action="${pageContext.request.contextPath}/manager/maintenance-requests/review"
                    method="post"
                    class="maintenance-action-card">

                <input type="hidden"
                       name="csrfToken"
                       value="${sessionScope.csrfToken}">

                <input type="hidden"
                       name="requestId"
                       value="${item.request.maintenanceRequestId}">

                <input type="hidden"
                       name="action"
                       value="approve">

                <h3>Duyệt yêu cầu</h3>

                <label class="maintenance-check">
                  <input type="checkbox"
                         name="setUnderMaintenance"
                         value="true"
                         checked>

                  <span>
                    <b>Chuyển riêng máy <c:out value="${item.itemCode}" /> sang Đang bảo trì</b>
                    <small>
                      Chỉ đổi trạng thái máy con này, các máy khác thuộc dòng <c:out value="${item.facilityName}" /> vẫn sử dụng bình thường.
                    </small>
                  </span>
                </label>

                <button class="btn btn-primary"
                        type="submit"
                        data-confirm-title="Duyệt yêu cầu"
                        data-confirm-message="Xác nhận chuyển yêu cầu sang Đang xử lý?">
                  Duyệt yêu cầu
                </button>
              </form>

              <form action="${pageContext.request.contextPath}/manager/maintenance-requests/review"
                    method="post"
                    class="maintenance-action-card"
                    data-validate
                    novalidate>

                <input type="hidden"
                       name="csrfToken"
                       value="${sessionScope.csrfToken}">

                <input type="hidden"
                       name="requestId"
                       value="${item.request.maintenanceRequestId}">

                <input type="hidden"
                       name="action"
                       value="reject">

                <h3>Từ chối yêu cầu</h3>

                <div class="field">
                  <label class="field-label"
                         for="rejectReason">
                    Lý do từ chối *
                  </label>

                  <textarea id="rejectReason"
                            name="rejectReason"
                            minlength="5"
                            maxlength="500"
                            data-min-length="5"
                            data-max-length="500"
                            data-label="Lý do từ chối"
                            data-rule="reason"
                            required><c:out value="${rejectReason}" /></textarea>

                  <div class="field-error"
                       id="rejectReasonError"
                       <c:if test="${empty errors.rejectReason}">hidden</c:if>>
                    <c:out value="${errors.rejectReason}" />
                  </div>
                </div>

                <button class="btn btn-danger"
                        type="submit"
                        data-confirm-title="Từ chối yêu cầu"
                        data-confirm-message="Xác nhận từ chối yêu cầu bảo trì này?">
                  Từ chối
                </button>
              </form>

            </div>
          </c:if>

          <%-- ===== Yêu cầu đã xử lý ===== --%>
          <c:if test="${not item.request.open}">
            <div class="maintenance-history">

              <c:if test="${not empty item.reviewerName}">
                <p>
                  Người xử lý:
                  <b><c:out value="${item.reviewerName}" /></b>
                </p>
              </c:if>

              <c:if test="${item.request.rejected}">
                <p>
                  Lý do từ chối:
                  <c:out value="${item.request.rejectReason}" />
                </p>
              </c:if>

              <c:if test="${item.request.closed}">
                <p>
                  Người nghiệm thu:
                  <b><c:out value="${item.closerName}" /></b>
                </p>

                <p>
                  Ghi chú nghiệm thu:
                  <c:out value="${item.request.acceptanceNote}" />
                </p>
              </c:if>

            </div>
          </c:if>

        </section>
      </main>
    </div>
  </div>
</body>
</html>