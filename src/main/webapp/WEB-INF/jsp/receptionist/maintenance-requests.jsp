<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Yêu cầu bảo trì · Super Gym Lễ tân</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/auth/account-settings.css">
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/maintenance-requests.css">
  <link rel="stylesheet"
        href="${pageContext.request.contextPath}/css/pages/auth/notification-bell.css">

  <script src="${pageContext.request.contextPath}/js/pages/auth/notification-bell.js"
          defer></script>
</head>

<body class="dock theme-staff">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Yêu cầu bảo trì</h1>
        </div>

        <div class="top-right">

          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <a class="tb-user"
             href="${pageContext.request.contextPath}/account">
            <span class="avatar">LT</span>
            <span>
              <b><c:out value="${sessionScope.displayName}" default="Lễ tân" /></b>
              <small>Lễ tân</small>
            </span>
          </a>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">

        <%-- ===== Form gửi yêu cầu ===== --%>
        <section class="panel maintenance-panel">
          <div class="panel-head">
            <div>
              <h2>Gửi yêu cầu mới</h2>
            </div>
          </div>

          <c:if test="${not empty formMessage}">
            <div class="alert-banner danger">
              <c:out value="${formMessage}" />
            </div>
          </c:if>

          <form id="createRequestForm"
                action="${pageContext.request.contextPath}/receptionist/maintenance-requests"
                method="post"
                data-validate
                novalidate>

            <input type="hidden" name="action" value="create">

            <%-- Khung cảnh báo yêu cầu chưa hoàn tất --%>
            <div id="facilityWarningContainer"
                 class="alert-banner warning"
                 style="margin-bottom: 1.25rem; display: none;">
              <div id="facilityWarningText"></div>
              <ul id="facilityWarningList" style="margin: 0.5rem 0 0 1.25rem; padding: 0;"></ul>
            </div>

            <div class="maintenance-form-grid">
              <div class="field">
                <label class="field-label" for="facilityId">Thiết bị *</label>

                <select id="facilityId" name="facilityId" required data-label="Thiết bị">
                  <option value="">Chọn thiết bị...</option>

                  <%-- BF-06: Display ONLY facility name in <option>; attach metadata via data-* attributes for dynamic client-side rendering --%>
                  <c:forEach var="facility" items="${facilities}">
                    <option value="${facility.facilityId}"
                            data-location="<c:out value='${facility.location}' />"
                            data-available="${facility.availableQuantity}"
                            data-total="${facility.quantity}"
                            data-pending="${facility.pendingQuantity}"
                            <c:if test="${formFacilityId == facility.facilityId}">selected</c:if>>
                      <c:out value="${facility.facilityName}" />
                    </option>
                  </c:forEach>
                </select>

                <%-- BF-06: Compact summary shown immediately after selecting a facility --%>
                <div id="facilitySelectedInfo"
                     class="facility-selected-info"
                     style="display: none;"
                     aria-live="polite">
                  <span class="facility-info-item">
                    <span class="facility-info-label">Khu vực:</span>
                    <strong id="facilityInfoLocation" class="facility-info-value"></strong>
                  </span>
                  <span class="facility-info-separator" aria-hidden="true">·</span>
                  <span class="facility-info-item">
                    <span class="facility-info-label">Khả dụng:</span>
                    <strong id="facilityInfoAvailability" class="facility-info-value"></strong>
                  </span>
                  <span class="facility-info-separator" aria-hidden="true">·</span>
                  <span class="facility-info-item">
                    <span class="facility-info-label">Yêu cầu chưa hoàn tất:</span>
                    <strong id="facilityInfoPending" class="facility-info-value"></strong>
                  </span>
                </div>

                <div class="field-error"
                     id="facilityIdError"
                     <c:if test="${empty errors.facilityId}">hidden</c:if>>
                  <c:out value="${errors.facilityId}" />
                </div>
              </div>

              <div class="field">
                <label class="field-label" for="priority">Mức ưu tiên *</label>

                <select id="priority" name="priority" required data-label="Mức ưu tiên">
                  <c:forEach var="priority" items="${priorityOptions}">
                    <option value="${priority.dbValue}"
                            <c:if test="${formPriority == priority.dbValue
                                    or (empty formPriority and priority.dbValue == 'Normal')}">
                              selected
                            </c:if>>
                      <c:out value="${priority.displayName}" />
                    </option>
                  </c:forEach>
                </select>

                <div class="field-error"
                     id="priorityError"
                     <c:if test="${empty errors.priority}">hidden</c:if>>
                  <c:out value="${errors.priority}" />
                </div>
              </div>
            </div>

            <div class="field">
              <label class="field-label" for="description">
                Mô tả hỏng hóc *
              </label>

              <textarea id="description"
                        name="description"
                        rows="4"
                        minlength="10"
                        maxlength="500"
                        data-min-length="10"
                        data-max-length="500"
                        data-label="Mô tả hỏng hóc"
                        required
                        placeholder="Hỏng ở đâu, xảy ra khi nào, đã xử lý tạm thế nào"><c:out value="${formDescription}" /></textarea>

              <div class="field-error"
                   id="descriptionError"
                   <c:if test="${empty errors.description}">hidden</c:if>>
                <c:out value="${errors.description}" />
              </div>
            </div>

            <label class="maintenance-check">
              <input type="checkbox"
                     name="warningTagPlaced"
                     value="true"
                     <c:if test="${formWarningTagPlaced}">checked</c:if>>

              <span>
                <b>Đã dán cảnh báo, ngừng cho khách dùng</b>
                <small>Chọn khi thiết bị không còn an toàn.</small>
              </span>
            </label>

            <div class="btn-row">
              <button class="btn btn-primary" type="submit">
                Gửi yêu cầu
              </button>
            </div>
          </form>
        </section>

        <%-- ===== Danh sách yêu cầu ===== --%>
        <section class="panel maintenance-panel">
          <div class="panel-head">
            <div>
              <h2>Yêu cầu bảo trì</h2>
            </div>
          </div>

          <form action="${pageContext.request.contextPath}/receptionist/maintenance-requests"
                method="get"
                class="maintenance-filter">

            <input type="search"
                   name="keyword"
                   maxlength="100"
                   value="<c:out value='${keyword}' />"
                   placeholder="Tìm mã yêu cầu hoặc tên thiết bị">

            <select name="status">
              <option value="">Tất cả trạng thái</option>

              <c:forEach var="status" items="${statusOptions}">
                <option value="${status.dbValue}"
                        <c:if test="${statusFilter == status.dbValue}">selected</c:if>>
                  <c:out value="${status.displayName}" />
                </option>
              </c:forEach>
            </select>

            <select name="priority">
              <option value="">Tất cả mức ưu tiên</option>

              <c:forEach var="p" items="${priorityOptions}">
                <option value="${p.dbValue}"
                        <c:if test="${priorityFilter == p.dbValue}">selected</c:if>>
                  <c:out value="${p.displayName}" />
                </option>
              </c:forEach>
            </select>

            <input type="date"
                   name="reportedDate"
                   value="<c:out value='${reportedDateFilter}' />"
                   title="Lọc theo ngày báo"
                   aria-label="Ngày báo">

            <select name="pageSize">
              <option value="10" ${requestPage.pageSize == 10 ? 'selected' : ''}>10</option>
              <option value="20" ${requestPage.pageSize == 20 ? 'selected' : ''}>20</option>
              <option value="50" ${requestPage.pageSize == 50 ? 'selected' : ''}>50</option>
            </select>

            <button class="btn btn-primary" type="submit">Lọc</button>

            <a class="btn btn-ghost"
               href="${pageContext.request.contextPath}/receptionist/maintenance-requests">
              Xóa lọc
            </a>
          </form>

          <c:choose>
            <c:when test="${not empty requestPage.items}">
              <div class="maintenance-list">
                <c:forEach var="item" items="${requestPage.items}">
                  <article class="maintenance-card">

                    <div class="maintenance-card-main">
                      <div class="maintenance-card-head">
                        <strong>
                          <c:out value="${item.request.requestCode}" />
                          ·
                          <c:out value="${item.facilityName}" />
                        </strong>

                        <span class="badge
                          ${item.request.open ? 'badge-warning' :
                            item.request.inProgress ? 'badge-info' :
                            item.request.closed ? 'badge-success' : 'badge-neutral'}">
                          <c:out value="${item.request.status.displayName}" />
                        </span>
                      </div>

                      <div class="maintenance-meta">
                        <span>
                          ${gms:formatDate(item.request.reportedAt.toLocalDate())}
                        </span>

                        <span>
                          <c:out value="${item.request.priority.displayName}" />
                        </span>

                        <c:if test="${item.request.warningTagPlaced}">
                          <span class="badge badge-danger">
                            Đã đặt cảnh báo
                          </span>
                        </c:if>
                      </div>

                      <p class="maintenance-description">
                        <c:out value="${item.request.description}" />
                      </p>

                      <div class="maintenance-progress">
                        <c:choose>
                          <c:when test="${item.request.open}">
                            Chờ Quản lý duyệt.
                          </c:when>

                          <c:when test="${item.request.inProgress}">
                            Quản lý
                            <b><c:out value="${item.reviewerName}" /></b>
                            đã duyệt.
                          </c:when>

                          <c:when test="${item.request.rejected}">
                            Bị từ chối:
                            <c:out value="${item.request.rejectReason}" />
                          </c:when>

                          <c:when test="${item.request.closed}">
                            Đã nghiệm thu bởi
                            <b><c:out value="${item.closerName}" /></b>.
                            <c:out value="${item.request.acceptanceNote}" />
                          </c:when>
                        </c:choose>
                      </div>
                    </div>

                    <c:if test="${item.request.inProgress}">
                      <form action="${pageContext.request.contextPath}/receptionist/maintenance-requests"
                            method="post"
                            class="maintenance-close-form"
                            data-validate
                            novalidate>

                        <input type="hidden"
                               name="action"
                               value="close">

                        <input type="hidden"
                               name="requestId"
                               value="${item.request.maintenanceRequestId}">

                        <label class="field-label"
                               for="acceptance-${item.request.maintenanceRequestId}">
                          Ghi chú nghiệm thu
                        </label>

                        <textarea id="acceptance-${item.request.maintenanceRequestId}"
                                  name="acceptanceNote"
                                  minlength="5"
                                  maxlength="500"
                                  data-min-length="5"
                                  data-max-length="500"
                                  data-label="Ghi chú nghiệm thu"
                                  required
                                  placeholder="Đã kiểm tra thiết bị hoạt động bình thường..."></textarea>

                        <div class="field-error"
                             id="acceptance-${item.request.maintenanceRequestId}Error"
                             hidden></div>

                        <button class="btn btn-primary btn-sm"
                                type="submit"
                                data-confirm-title="Đóng yêu cầu"
                                data-confirm-message="Xác nhận thiết bị đã được sửa và có thể sử dụng?">
                          Đóng yêu cầu
                        </button>
                      </form>
                    </c:if>

                  </article>
                </c:forEach>
              </div>
            </c:when>

            <c:otherwise>
              <div class="maintenance-empty">
                Không có yêu cầu nào khớp bộ lọc.
              </div>
            </c:otherwise>
          </c:choose>

          <%-- ===== Phân trang ===== --%>
          <c:if test="${requestPage.totalPages > 1}">
            <div class="maintenance-pager">

              <c:if test="${requestPage.hasPrevious}">
                <c:url var="previousUrl"
                       value="/receptionist/maintenance-requests">
                  <c:param name="page" value="${requestPage.pageNumber - 1}" />
                  <c:param name="pageSize" value="${requestPage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:param name="priority" value="${priorityFilter}" />
                  <c:param name="reportedDate" value="${reportedDateFilter}" />
                </c:url>

                <a class="btn btn-ghost btn-sm"
                   href="${previousUrl}">
                  Trang trước
                </a>
              </c:if>

              <span>
                Trang ${requestPage.pageNumber}
                / ${requestPage.totalPages}
              </span>

              <c:if test="${requestPage.hasNext}">
                <c:url var="nextUrl"
                       value="/receptionist/maintenance-requests">
                  <c:param name="page" value="${requestPage.pageNumber + 1}" />
                  <c:param name="pageSize" value="${requestPage.pageSize}" />
                  <c:param name="keyword" value="${keyword}" />
                  <c:param name="status" value="${statusFilter}" />
                  <c:param name="priority" value="${priorityFilter}" />
                  <c:param name="reportedDate" value="${reportedDateFilter}" />
                </c:url>

                <a class="btn btn-ghost btn-sm"
                   href="${nextUrl}">
                  Trang sau
                </a>
              </c:if>

            </div>
          </c:if>

        </section>

      </main>
    </div>
  </div>

  <script id="unfinishedDataJson" type="application/json">
    <c:out value="${unfinishedJson}" escapeXml="false" />
  </script>
  <script>
    (function () {
      var facilitySelect = document.getElementById('facilityId');
      var warningContainer = document.getElementById('facilityWarningContainer');
      var warningText = document.getElementById('facilityWarningText');
      var warningList = document.getElementById('facilityWarningList');
      var dataElem = document.getElementById('unfinishedDataJson');

      // BF-06: Compact facility summary elements below dropdown
      var infoContainer = document.getElementById('facilitySelectedInfo');
      var infoLocation = document.getElementById('facilityInfoLocation');
      var infoAvailability = document.getElementById('facilityInfoAvailability');
      var infoPending = document.getElementById('facilityInfoPending');

      if (!facilitySelect || !warningContainer || !dataElem) return;

      var unfinishedData = {};
      try {
        unfinishedData = JSON.parse(dataElem.textContent || '{}');
      } catch (e) {
        console.error('Failed to parse unfinishedData', e);
      }

      /**
       * BF-06: Dynamically updates the compact information section and warning banner
       * whenever a facility is selected or preselected (e.g. from Facility Catalog query param).
       * Reuses data-* attributes already baked into the DOM without additional DB/AJAX requests.
       */
      function updateFacilityDetails() {
        var selectedId = facilitySelect.value;
        var selectedOption = facilitySelect.options[facilitySelect.selectedIndex];

        // 1. Update compact info section below selector
        if (infoContainer) {
          if (!selectedId || !selectedOption || selectedOption.value === '') {
            infoContainer.style.display = 'none';
          } else {
            var loc = selectedOption.getAttribute('data-location');
            var available = selectedOption.getAttribute('data-available') || '0';
            var total = selectedOption.getAttribute('data-total') || '0';
            var pending = selectedOption.getAttribute('data-pending') || '0';

            if (infoLocation) {
              infoLocation.textContent = (loc && loc.trim()) ? loc.trim() : 'Chưa cập nhật';
            }
            if (infoAvailability) {
              infoAvailability.textContent = available + '/' + total;
            }
            if (infoPending) {
              infoPending.textContent = pending;
            }
            infoContainer.style.display = 'flex';
          }
        }

        // 2. Update unfinished requests warning banner
        var list = unfinishedData[selectedId];

        if (!list || list.length === 0) {
          warningContainer.style.display = 'none';
          warningText.textContent = '';
          warningList.innerHTML = '';
          return;
        }

        var total = list.length;
        var openCount = 0;
        var inProgressCount = 0;
        list.forEach(function (item) {
          if (item.status === 'Open') openCount++;
          else if (item.status === 'InProgress') inProgressCount++;
        });

        warningText.textContent =
          'Thiết bị này hiện có ' + total + ' yêu cầu bảo trì chưa hoàn tất (' +
          openCount + ' chờ duyệt, ' + inProgressCount + ' đang xử lý). ' +
          'Vui lòng kiểm tra các yêu cầu hiện có trước khi tạo đơn mới.';

        warningList.innerHTML = '';
        var contextPath = '${pageContext.request.contextPath}';
        list.forEach(function (item) {
          var li = document.createElement('li');
          var a = document.createElement('a');
          a.href = contextPath + '/receptionist/maintenance-requests?keyword=' + encodeURIComponent(item.requestCode);
          a.textContent = '[' + item.requestCode + ']';
          a.style.fontWeight = 'bold';
          a.style.marginRight = '6px';
          li.appendChild(a);

          var statusSpan = document.createElement('span');
          statusSpan.textContent = (item.statusDisplayName || item.status) + ' · ';
          li.appendChild(statusSpan);

          var descSpan = document.createElement('span');
          descSpan.textContent = item.description ? (item.description.length > 80 ? item.description.substring(0, 80) + '...' : item.description) : '';
          li.appendChild(descSpan);

          warningList.appendChild(li);
        });

        warningContainer.style.display = 'block';
      }

      facilitySelect.addEventListener('change', updateFacilityDetails);
      // Run once on load to handle automatic preselection (?facilityId=...)
      updateFacilityDetails();
    })();
  </script>
</body>
</html>