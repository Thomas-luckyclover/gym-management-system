<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Báo hỏng thiết bị · Bước 1/2 · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/facility-catalog.css?v=20261028v2">
</head>

<body class="dock theme-staff">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Báo hỏng thiết bị</h1>
          <%-- Câu giải thích chuyển vào hint box chung (.pt-info, common.css / common.js) thay cho dòng chữ phụ dưới tiêu đề. --%>
          <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Hướng dẫn Báo hỏng thiết bị" aria-describedby="report-help"><span aria-hidden="true">i</span></button><span id="report-help" class="pt-info-popover" role="tooltip"><b>Bước 1/2:</b> chọn đúng thiết bị đang hỏng.</span></span>
        </div>

        <div class="top-right">
          <a class="btn btn-outline" href="${pageContext.request.contextPath}/receptionist/maintenance-requests">
            Yêu cầu đã gửi (${openRequestsCount} đang mở)
          </a>

          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content catalog-step1-content" id="main-content" tabindex="-1">

        <%-- ===== KHUNG TÌM KIẾM & BỘ LỌC KHU VỰC ===== --%>
        <section class="catalog-filter-box">
          <form method="get" action="${pageContext.request.contextPath}/receptionist/facilities" class="search-form">
            <label class="filter-label" for="catalogSearchInput">Tìm theo tên máy hoặc số trên tem dán</label>
            <div class="search-bar-row">
              <input type="search"
                     id="catalogSearchInput"
                     name="keyword"
                     maxlength="100"
                     value="<c:out value='${keyword}' />"
                     placeholder="VD: máy chạy, ghế đẩy vai,...."
                     autocomplete="off">
              <button class="btn btn-primary" type="submit">Tìm kiếm</button>
              <c:if test="${not empty keyword || not empty selectedLocation}">
                <a class="btn btn-ghost" href="${pageContext.request.contextPath}/receptionist/facilities">Xóa lọc</a>
              </c:if>
            </div>
          </form>

          <%-- Chip lọc khu vực --%>
          <div class="location-chips-row">
            <c:url var="allLocUrl" value="/receptionist/facilities">
              <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            </c:url>
            <a href="${allLocUrl}" class="loc-chip ${empty selectedLocation ? 'active' : ''}">Tất cả khu vực</a>

            <c:forEach var="loc" items="${locationOptions}">
              <c:url var="locUrl" value="/receptionist/facilities">
                <c:param name="location" value="${loc}" />
                <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
              </c:url>
              <a href="${locUrl}" class="loc-chip ${selectedLocation == loc ? 'active' : ''}">
                <c:out value="${loc}" />
              </a>
            </c:forEach>
          </div>
        </section>

        <%-- ===== LƯỚI THẺ CÁC DÒNG MÁY ===== --%>
        <section class="facility-grid-section">
          <c:choose>
            <c:when test="${not empty facilityPage.items}">
              <div class="facility-cards-grid" id="facilityCardsGrid">
                <c:forEach var="facility" items="${facilityPage.items}" varStatus="status">
                  <div class="facility-card-item ${status.first ? 'selected' : ''}"
                       data-facility-id="${facility.facilityId}"
                       data-facility-name="<c:out value='${facility.facilityName}' />"
                       data-facility-location="<c:out value='${facility.location}' />"
                       data-category="<c:out value='${facility.category.displayName}' />"
                       tabindex="0"
                       role="button">

                    <div class="card-media">
                      <c:choose>
                        <c:when test="${not empty facility.imageUrl}">
                          <img src="${facility.imageUrl.startsWith('/') ? pageContext.request.contextPath += facility.imageUrl : facility.imageUrl}"
                               alt="<c:out value='${facility.facilityName}' />"
                               class="facility-thumb-img"
                               onerror="this.onerror=null; this.src='${pageContext.request.contextPath}/images/brand/facility.jpg';">
                        </c:when>
                        <c:otherwise>
                          <img src="${pageContext.request.contextPath}/images/brand/facility.jpg"
                               alt="<c:out value='${facility.facilityName}' />"
                               class="facility-thumb-img">
                        </c:otherwise>
                      </c:choose>
                    </div>

                    <div class="card-body">
                      <h3 class="card-title"><c:out value="${facility.facilityName}" /></h3>
                      <p class="card-location"><c:out value="${facility.location}" /> · <c:out value="${facility.category.displayName}" /></p>
                      <p class="card-stats">
                        <strong><c:out value="${facility.availableQuantity}" /> / <c:out value="${facility.totalQuantity}" /></strong> <c:out value="${facility.unitLabel}" /> dùng được
                      </p>
                    </div>
                  </div>
                </c:forEach>
              </div>

              <%-- Phân trang danh mục nhóm thiết bị --%>
              <c:if test="${facilityPage.totalPages > 1}">
                <div class="catalog-pager">
                  <c:choose>
                    <c:when test="${facilityPage.hasPrevious}">
                      <c:url var="prevCatalogUrl" value="/receptionist/facilities">
                        <c:param name="page" value="${facilityPage.pageNumber - 1}" />
                        <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
                        <c:if test="${not empty selectedLocation}"><c:param name="location" value="${selectedLocation}" /></c:if>
                      </c:url>
                      <a class="btn btn-ghost btn-sm" href="${prevCatalogUrl}">‹ Trang trước</a>
                    </c:when>
                    <c:otherwise>
                      <button type="button" class="btn btn-ghost btn-sm" disabled>‹ Trang trước</button>
                    </c:otherwise>
                  </c:choose>

                  <span class="catalog-pager-info">
                    Trang ${facilityPage.pageNumber} / ${facilityPage.totalPages}
                  </span>

                  <c:choose>
                    <c:when test="${facilityPage.hasNext}">
                      <c:url var="nextCatalogUrl" value="/receptionist/facilities">
                        <c:param name="page" value="${facilityPage.pageNumber + 1}" />
                        <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
                        <c:if test="${not empty selectedLocation}"><c:param name="location" value="${selectedLocation}" /></c:if>
                      </c:url>
                      <a class="btn btn-ghost btn-sm" href="${nextCatalogUrl}">Trang sau ›</a>
                    </c:when>
                    <c:otherwise>
                      <button type="button" class="btn btn-ghost btn-sm" disabled>Trang sau ›</button>
                    </c:otherwise>
                  </c:choose>
                </div>
              </c:if>
            </c:when>
            <c:otherwise>
              <div class="catalog-empty-box">
                <p>Không tìm thấy thiết bị nào khớp với từ khóa tìm kiếm.</p>
                <a class="btn btn-outline" href="${pageContext.request.contextPath}/receptionist/facilities">Xem tất cả thiết bị</a>
              </div>
            </c:otherwise>
          </c:choose>
        </section>

        <%-- ===== KHỐI CHỌN CÁ THỂ MÁY CON (CHIẾC NÀO BỊ HỎNG?) ===== --%>
        <section class="equipment-items-section" id="equipmentItemsSection">
          <div class="section-header">
            <div class="section-header-left">
              <div class="title-hint-row">
                <h2 class="section-title">Chiếc nào bị hỏng?</h2>
                <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Cách chọn thiết bị hỏng" aria-describedby="pick-help"><span aria-hidden="true">i</span></button><span id="pick-help" class="pt-info-popover" role="tooltip">Có thể chọn một hoặc nhiều thiết bị cùng dòng. Sơ đồ sắp xếp theo vị trí sàn tập.</span></span>
              </div>
            </div>
            <div class="section-header-actions">
              <button type="button" class="btn btn-sm btn-outline" id="btnSelectAllItems">Chọn tất cả</button>
              <button type="button" class="btn btn-sm btn-ghost" id="btnClearAllItems">Bỏ chọn</button>
            </div>
          </div>

          <c:forEach var="facility" items="${facilityPage.items}">
            <div class="items-list-container" id="facilityItems_${facility.facilityId}" style="display: none;">
              <c:choose>
                <c:when test="${not empty facility.items}">
                  <div class="items-buttons-grid">
                    <c:forEach var="item" items="${facility.items}" varStatus="itemStatus">
                      <c:set var="isMaintenance" value="${item.underMaintenance || not empty item.activeRequestCode}" />

                      <button type="button"
                              class="item-select-btn ${isMaintenance ? 'disabled-maintenance' : ''}"
                              data-item-id="${item.equipmentItemId}"
                              data-item-code="<c:out value='${item.itemCode}' />"
                              data-item-label="<c:out value='${empty item.itemLabel ? (\"Số \" += itemStatus.count) : item.itemLabel}' />"
                              data-facility-id="${facility.facilityId}"
                              <c:choose>
                                <c:when test="${isMaintenance}">
                                  data-under-maintenance="true"
                                  data-request-id="${item.activeRequestId}"
                                  data-request-code="<c:out value='${item.activeRequestCode}' />"
                                  title="Đang bảo trì (${item.activeRequestCode}) - Bấm để xem chi tiết đơn"
                                </c:when>
                                <c:otherwise>
                                  title="Chọn máy để báo hỏng"
                                </c:otherwise>
                              </c:choose>>

                        <span class="btn-check-icon"><i class="fa-solid fa-check"></i></span>
                        <span class="btn-main-label"><c:out value="${empty item.itemLabel ? ('Số ' += itemStatus.count) : item.itemLabel}" /></span>
                        <c:choose>
                          <c:when test="${isMaintenance}">
                            <span class="btn-sub-label text-warning">Đang bảo trì</span>
                          </c:when>
                          <c:otherwise>
                            <span class="btn-sub-label"><c:out value="${item.itemCode}" /></span>
                          </c:otherwise>
                        </c:choose>
                        <c:if test="${not empty item.placementNote}">
                          <span class="btn-note-label"><c:out value="${item.placementNote}" /></span>
                        </c:if>
                      </button>
                    </c:forEach>
                  </div>
                </c:when>
                <c:otherwise>
                  <p class="no-items-text">Dòng thiết bị này chưa có cá thể máy nào được gán trong hệ thống.</p>
                </c:otherwise>
              </c:choose>
            </div>
          </c:forEach>
        </section>

      </main>

      <%-- ===== THANH BOTTOM BAR DÍNH ĐÁY ===== --%>
      <footer class="catalog-sticky-bottom">
        <div class="bottom-bar-inner">
          <div class="bottom-info">
            <span>Dòng máy: </span>
            <strong id="stickyFacilityName">Chưa chọn dòng máy</strong>
            <span class="separator"> · </span>
            <span id="stickyItemText" class="text-muted">Chưa chọn chiếc nào</span>
          </div>

          <div class="bottom-action">
            <form id="goToStep2Form" method="get" action="${pageContext.request.contextPath}/receptionist/facilities/report">
              <input type="hidden" name="facilityId" id="selectedFacilityId" value="">
              <input type="hidden" name="equipmentItemIds" id="selectedEquipmentItemIds" value="">
              <button type="submit" id="btnNextStep" class="btn btn-primary btn-next-step" disabled>
                Tiếp: mô tả sự cố <i class="fa-solid fa-arrow-right"></i>
              </button>
            </form>
          </div>
        </div>
      </footer>

    </div>
  </div>

  <%-- Modal xem chi tiết đơn bảo trì khi bấm vào máy đang bảo trì --%>
  <div class="modal-overlay" id="maintenanceDetailModal" role="dialog" aria-modal="true" aria-labelledby="maintDetailTitle" style="display: none;">
    <div class="modal-card maint-detail-modal-card">
      <div class="modal-header">
        <div class="maint-modal-header-info">
          <h3 class="modal-title" id="maintDetailTitle">Chi tiết đơn bảo trì <span id="maintCodeBadge" class="text-primary"></span></h3>
          <span id="maintStatusBadge" class="badge badge-warning">Đang bảo trì</span>
        </div>
        <button type="button" class="modal-close-btn" id="btnCloseMaintModal" aria-label="Đóng">&times;</button>
      </div>

      <div class="modal-body maint-modal-body">
        <div id="maintModalLoading" class="maint-loading-state" style="display: none;">
          <i class="fa-solid fa-spinner fa-spin"></i> Đang tải dữ liệu đơn bảo trì...
        </div>

        <div id="maintModalError" class="alert alert-danger" style="display: none;"></div>

        <div id="maintModalContent" class="maint-content-grid" style="display: none;">
          <%-- Thông tin máy --%>
          <div class="maint-info-banner">
            <div class="maint-facility-info">
              <strong id="maintFacilityName" class="maint-facility-title"></strong>
              <div class="maint-item-tags">
                <span id="maintItemLabelBadge" class="maint-tag-pill"></span>
                <span id="maintItemCodeBadge" class="maint-tag-code"></span>
              </div>
            </div>
            <div id="maintPlacementRow" class="maint-placement-row text-muted">
              <i class="fa-solid fa-location-dot"></i> <span id="maintPlacementText"></span>
            </div>
          </div>

          <%-- Thuộc tính đơn: Người báo, thời gian, mức độ, loại sự cố --%>
          <div class="maint-meta-grid">
            <div class="maint-meta-item">
              <span class="maint-meta-label">Người báo hỏng:</span>
              <strong id="maintReporter" class="maint-meta-val"></strong>
            </div>
            <div class="maint-meta-item">
              <span class="maint-meta-label">Thời gian báo:</span>
              <span id="maintReportedAt" class="maint-meta-val"></span>
            </div>
            <div class="maint-meta-item">
              <span class="maint-meta-label">Mức ưu tiên:</span>
              <span id="maintPriorityBadge" class="badge"></span>
            </div>
            <div class="maint-meta-item">
              <span class="maint-meta-label">Loại sự cố:</span>
              <span id="maintIncidentBadge" class="badge badge-neutral"></span>
            </div>
          </div>

          <%-- Cảnh báo an toàn nếu có đặt biển --%>
          <div id="maintWarningTagNotice" class="maint-warning-notice" style="display: none;">
            <i class="fa-solid fa-triangle-exclamation"></i>
            <span>Đã treo biển <strong>"Ngừng sử dụng"</strong> trực tiếp tại máy.</span>
          </div>

          <%-- Ghi chú mức tạ / cá thể liên quan nếu có --%>
          <div id="maintAffectedRow" class="maint-field-group" style="display: none;">
            <label class="maint-field-label">Mức tạ / chi tiết ảnh hưởng:</label>
            <div id="maintAffectedText" class="maint-field-val note-box"></div>
          </div>

          <%-- Mô tả sự cố --%>
          <div class="maint-field-group">
            <label class="maint-field-label">Mô tả sự cố thực tế:</label>
            <div id="maintDescription" class="maint-field-val maint-desc-text"></div>
          </div>

          <%-- Ảnh đính kèm (nếu có) --%>
          <div id="maintImageSection" class="maint-field-group" style="display: none;">
            <label class="maint-field-label">Ảnh sự cố đính kèm:</label>
            <div class="maint-image-wrapper">
              <a id="maintImageLink" href="#" class="maint-image-preview-card image-lightbox-trigger" data-lightbox-image="" data-lightbox-title="Ảnh sự cố thực tế">
                <img id="maintImageThumb" src="" alt="Ảnh sự cố" class="maint-thumb-img">
                <div class="maint-image-overlay-hint">
                  <i class="fa-solid fa-magnifying-glass-plus"></i> Bấm xem ảnh lớn
                </div>
              </a>
            </div>
          </div>

          <%-- Ghi chú của quản lý (nếu có) --%>
          <div id="maintManagerSection" class="maint-field-group" style="display: none;">
            <label class="maint-field-label">Ghi chú duyệt của Quản lý:</label>
            <div id="maintManagerNote" class="maint-field-val manager-note-box"></div>
          </div>
        </div>
      </div>

      <div class="modal-footer maint-modal-footer">
        <a id="maintViewInListBtn" href="#" class="btn btn-sm btn-outline">
          <i class="fa-solid fa-list-check me-1"></i> Xem trong danh sách yêu cầu
        </a>
        <button type="button" class="btn btn-sm btn-secondary" id="btnDismissMaintModal">Đóng</button>
      </div>
    </div>
  </div>

  <%-- Script tương tác chọn nhiều máy & đồng bộ Bước 1 --%>
  <script>
    document.addEventListener('DOMContentLoaded', function () {
      var facilityCards = document.querySelectorAll('.facility-card-item');
      var itemContainers = document.querySelectorAll('.items-list-container');
      var stickyFacilityName = document.getElementById('stickyFacilityName');
      var stickyItemText = document.getElementById('stickyItemText');
      var selectedFacilityIdInput = document.getElementById('selectedFacilityId');
      var selectedEquipmentItemIdsInput = document.getElementById('selectedEquipmentItemIds');
      var btnNextStep = document.getElementById('btnNextStep');
      var btnSelectAll = document.getElementById('btnSelectAllItems');
      var btnClearAll = document.getElementById('btnClearAllItems');

      var currentFacilityId = null;
      var currentFacilityName = '';

      function updateSelectionState() {
        if (!currentFacilityId) return;
        var activeContainer = document.getElementById('facilityItems_' + currentFacilityId);
        if (!activeContainer) return;

        var selectedButtons = activeContainer.querySelectorAll('.item-select-btn.active');
        var selectedIds = [];
        var selectedLabels = [];

        selectedButtons.forEach(function (btn) {
          selectedIds.push(btn.getAttribute('data-item-id'));
          var label = btn.getAttribute('data-item-label') || btn.getAttribute('data-item-code');
          selectedLabels.push(label);
        });

        selectedEquipmentItemIdsInput.value = selectedIds.join(',');

        if (selectedIds.length === 0) {
          stickyItemText.textContent = 'Chưa chọn chiếc nào';
          stickyItemText.className = 'text-muted';
          btnNextStep.disabled = true;
        } else if (selectedIds.length === 1) {
          stickyItemText.textContent = 'Đã chọn: ' + selectedLabels[0];
          stickyItemText.className = 'text-highlight';
          btnNextStep.disabled = false;
        } else {
          stickyItemText.textContent = 'Đã chọn ' + selectedIds.length + ' chiếc (' + selectedLabels.slice(0, 3).join(', ') + (selectedLabels.length > 3 ? '...' : '') + ')';
          stickyItemText.className = 'text-highlight';
          btnNextStep.disabled = false;
        }
      }

      function selectFacility(card) {
        if (!card) return;
        facilityCards.forEach(function (c) { c.classList.remove('selected'); });
        card.classList.add('selected');

        currentFacilityId = card.getAttribute('data-facility-id');
        currentFacilityName = card.getAttribute('data-facility-name');
        stickyFacilityName.textContent = currentFacilityName;
        selectedFacilityIdInput.value = currentFacilityId;

        // Reset lựa chọn máy con ở tất cả các container
        document.querySelectorAll('.item-select-btn').forEach(function (b) {
          b.classList.remove('active');
        });

        // Ẩn tất cả container item và hiện container của dòng máy này
        itemContainers.forEach(function (cont) {
          cont.style.display = 'none';
        });

        var activeContainer = document.getElementById('facilityItems_' + currentFacilityId);
        if (activeContainer) {
          activeContainer.style.display = 'block';
        }

        updateSelectionState();
      }

      function toggleItem(btn) {
        if (!btn || btn.disabled) return;
        btn.classList.toggle('active');
        updateSelectionState();
      }

      // Gắn sự kiện click card dòng máy
      facilityCards.forEach(function (card) {
        card.addEventListener('click', function () {
          selectFacility(this);
        });
        card.addEventListener('keydown', function (e) {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            selectFacility(this);
          }
        });
      });

      // Nút Chọn tất cả máy trong dòng hiện tại
      if (btnSelectAll) {
        btnSelectAll.addEventListener('click', function () {
          if (!currentFacilityId) return;
          var activeContainer = document.getElementById('facilityItems_' + currentFacilityId);
          if (activeContainer) {
            activeContainer.querySelectorAll('.item-select-btn:not(.disabled-maintenance)').forEach(function (b) {
              b.classList.add('active');
            });
            updateSelectionState();
          }
        });
      }

      // Nút Bỏ chọn tất cả máy trong dòng hiện tại
      if (btnClearAll) {
        btnClearAll.addEventListener('click', function () {
          if (!currentFacilityId) return;
          var activeContainer = document.getElementById('facilityItems_' + currentFacilityId);
          if (activeContainer) {
            activeContainer.querySelectorAll('.item-select-btn').forEach(function (b) {
              b.classList.remove('active');
            });
            updateSelectionState();
          }
        });
      }

      // Modal xem chi tiết đơn bảo trì
      var maintModal = document.getElementById('maintenanceDetailModal');
      var maintModalLoading = document.getElementById('maintModalLoading');
      var maintModalError = document.getElementById('maintModalError');
      var maintModalContent = document.getElementById('maintModalContent');
      var btnCloseMaintModal = document.getElementById('btnCloseMaintModal');
      var btnDismissMaintModal = document.getElementById('btnDismissMaintModal');

      function closeMaintModal() {
        if (maintModal) {
          maintModal.style.display = 'none';
          maintModal.classList.remove('show');
        }
      }

      if (btnCloseMaintModal) btnCloseMaintModal.addEventListener('click', closeMaintModal);
      if (btnDismissMaintModal) btnDismissMaintModal.addEventListener('click', closeMaintModal);
      if (maintModal) {
        maintModal.addEventListener('click', function (e) {
          if (e.target === maintModal) closeMaintModal();
        });
      }

      function openMaintenanceDetailModal(reqId, reqCode, itemId, btnElement) {
        if (!maintModal) return;

        maintModal.style.display = 'flex';
        maintModal.classList.add('show');
        maintModalLoading.style.display = 'block';
        maintModalError.style.display = 'none';
        maintModalContent.style.display = 'none';

        var codeLabel = reqCode || (reqId ? ('#' + reqId) : (itemId ? ('Máy #' + itemId) : ''));
        document.getElementById('maintCodeBadge').textContent = codeLabel ? ('#' + codeLabel.replace('#', '')) : '';

        var url = '${pageContext.request.contextPath}/receptionist/facilities?action=requestDetail';
        if (reqId && reqId !== '0') {
          url += '&requestId=' + encodeURIComponent(reqId);
        } else if (reqCode && reqCode.trim() !== '') {
          url += '&requestCode=' + encodeURIComponent(reqCode);
        } else if (itemId && itemId !== '0') {
          url += '&equipmentItemId=' + encodeURIComponent(itemId);
        }

        fetch(url, {
          headers: { 'X-Requested-With': 'XMLHttpRequest' }
        })
        .then(function (res) {
          return res.json();
        })
        .then(function (json) {
          maintModalLoading.style.display = 'none';
          if (!json || !json.success || !json.data) {
            maintModalError.textContent = (json && json.message) || 'Không tìm thấy thông tin đơn bảo trì.';
            maintModalError.style.display = 'block';
            return;
          }

          var d = json.data;
          maintModalContent.style.display = 'flex';

          document.getElementById('maintCodeBadge').textContent = d.requestCode ? ('#' + d.requestCode) : '';
          
          var statusBadge = document.getElementById('maintStatusBadge');
          statusBadge.textContent = d.statusDisplay || 'Đang bảo trì';
          statusBadge.className = 'badge ' + (d.statusCode === 'Open' ? 'badge-warning' : (d.statusCode === 'InProgress' ? 'badge-info' : 'badge-neutral'));

          document.getElementById('maintFacilityName').textContent = d.facilityName || 'Thiết bị';
          document.getElementById('maintItemLabelBadge').textContent = d.itemLabel || 'Máy';
          document.getElementById('maintItemCodeBadge').textContent = d.itemCode || '';

          var placementRow = document.getElementById('maintPlacementRow');
          var placementParts = [];
          if (d.facilityLocation) placementParts.push(d.facilityLocation);
          if (d.placementNote) placementParts.push(d.placementNote);
          if (placementParts.length > 0) {
            document.getElementById('maintPlacementText').textContent = placementParts.join(' · ');
            placementRow.style.display = 'flex';
          } else {
            placementRow.style.display = 'none';
          }

          document.getElementById('maintReporter').textContent = d.reporterName || 'Nhân viên';
          document.getElementById('maintReportedAt').textContent = d.reportedAtFormatted || '-';

          var priBadge = document.getElementById('maintPriorityBadge');
          priBadge.textContent = d.priorityDisplay || 'Thường';
          priBadge.className = 'badge ' + (d.priorityCode === 'Urgent' ? 'badge-danger' : (d.priorityCode === 'Normal' ? 'badge-warning' : 'badge-neutral'));

          document.getElementById('maintIncidentBadge').textContent = d.incidentTypeDisplay || 'Không hoạt động';

          var warningNotice = document.getElementById('maintWarningTagNotice');
          if (d.warningTagPlaced) {
            warningNotice.style.display = 'flex';
          } else {
            warningNotice.style.display = 'none';
          }

          var affectedRow = document.getElementById('maintAffectedRow');
          if (d.affectedItemsNote) {
            document.getElementById('maintAffectedText').textContent = d.affectedItemsNote;
            affectedRow.style.display = 'flex';
          } else {
            affectedRow.style.display = 'none';
          }

          document.getElementById('maintDescription').textContent = d.description || 'Không có mô tả chi tiết.';

          var imageSection = document.getElementById('maintImageSection');
          if (d.attachedImages) {
            var fullImgUrl = '${pageContext.request.contextPath}' + d.attachedImages;
            var imgLink = document.getElementById('maintImageLink');
            var imgThumb = document.getElementById('maintImageThumb');
            imgLink.href = fullImgUrl;
            imgLink.setAttribute('data-lightbox-image', fullImgUrl);
            imgLink.setAttribute('data-lightbox-title', 'Ảnh sự cố máy ' + (d.itemCode || '') + ' (' + (d.requestCode || '') + ')');
            imgThumb.src = fullImgUrl;
            imageSection.style.display = 'flex';
          } else {
            imageSection.style.display = 'none';
          }

          var mgrSection = document.getElementById('maintManagerSection');
          if (d.managerNote || d.rejectReason) {
            document.getElementById('maintManagerNote').textContent = d.managerNote || d.rejectReason;
            mgrSection.style.display = 'flex';
          } else {
            mgrSection.style.display = 'none';
          }

          var viewListBtn = document.getElementById('maintViewInListBtn');
          if (viewListBtn) {
            viewListBtn.href = '${pageContext.request.contextPath}/receptionist/maintenance-requests?keyword=' + encodeURIComponent(d.requestCode || d.itemCode || '');
          }
        })
        .catch(function (err) {
          maintModalLoading.style.display = 'none';
          maintModalError.textContent = 'Lỗi kết nối khi tải thông tin đơn bảo trì.';
          maintModalError.style.display = 'block';
        });
      }

      // Xử lý click trên cá thể máy (hỗ trợ phân biệt máy bình thường và máy đang bảo trì)
      document.addEventListener('click', function (e) {
        var maintBtn = e.target.closest('.item-select-btn.disabled-maintenance, .item-select-btn[data-under-maintenance="true"]');
        if (maintBtn) {
          e.preventDefault();
          e.stopPropagation();
          var reqId = maintBtn.getAttribute('data-request-id');
          var reqCode = maintBtn.getAttribute('data-request-code');
          var itemId = maintBtn.getAttribute('data-item-id');
          openMaintenanceDetailModal(reqId, reqCode, itemId, maintBtn);
          return;
        }

        var normalBtn = e.target.closest('.item-select-btn');
        if (normalBtn) {
          e.preventDefault();
          toggleItem(normalBtn);
          return;
        }
      });

      // Tự động kích hoạt thẻ đầu tiên khi tải trang
      var firstCard = document.querySelector('.facility-card-item');
      if (firstCard) {
        selectFacility(firstCard);
      }
    });
  </script>
</body>
</html>
