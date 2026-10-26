<%@ page contentType="text/html; charset=UTF-8"
         pageEncoding="UTF-8" %>
<%@ taglib prefix="c"
           uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">

<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>

  <title>Cơ sở vật chất · Super Gym Lễ tân</title>

  <link rel="stylesheet"
        href="${pageContext.request.contextPath}/css/pages/receptionist/facility-catalog.css?v=20261023v5">
</head>

<body class="dock theme-staff">

<%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

<div class="app">

  <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

  <div class="main">

    <header class="topbar">

      <div class="tb-left">
        <h1 class="tb-title">
          <i class="fa-solid fa-boxes-stacked" style="color: var(--color-primary); font-size: 1.25rem; margin-right: 8px;"></i> Cơ sở vật chất
        </h1>
      </div>

      <div class="top-right">

        <a class="btn btn-outline"
           href="${pageContext.request.contextPath}/receptionist/maintenance-requests">
          <i class="fa-solid fa-clipboard-list"></i> Danh sách yêu cầu bảo trì
        </a>

        <a class="tb-user"
           href="${pageContext.request.contextPath}/account">

          <span class="avatar"><c:out value="${sessionScope.userInitials}" /></span>

          <span>
            <b>
              <c:out
                      value="${sessionScope.displayName}"
                      default="Lễ tân" />
            </b>

            <%@ include file="/WEB-INF/jsp/common/_user-badge-line.jspf" %>
          </span>

        </a>

      </div>

    </header>

    <main class="content" id="main-content" tabindex="-1">

      <%-- ===== 1. TÍNH TOÁN & HIỂN THỊ KPI THỐNG KÊ NHANH ===== --%>
      <c:set var="calcTotalUnits" value="0" />
      <c:set var="calcAvailUnits" value="0" />
      <c:set var="calcPendingUnits" value="0" />
      <c:forEach var="f" items="${facilityPage.items}">
        <c:set var="calcTotalUnits" value="${calcTotalUnits + f.totalQuantity}" />
        <c:set var="calcAvailUnits" value="${calcAvailUnits + f.availableQuantity}" />
        <c:set var="calcPendingUnits" value="${calcPendingUnits + f.pendingQuantity}" />
      </c:forEach>

      <div class="facility-kpi-banner">
        <div class="kpi-card">
          <div class="kpi-icon kpi-blue">
            <i class="fa-solid fa-layer-group"></i>
          </div>
          <div class="kpi-content">
            <span class="kpi-label">Dòng thiết bị</span>
            <span class="kpi-value"><c:out value="${facilityPage.totalItems}" default="0" /></span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon kpi-green">
            <i class="fa-solid fa-circle-check"></i>
          </div>
          <div class="kpi-content">
            <span class="kpi-label">Máy đang khả dụng</span>
            <span class="kpi-value"><c:out value="${calcAvailUnits}" default="0" /></span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon kpi-warning">
            <i class="fa-solid fa-screwdriver-wrench"></i>
          </div>
          <div class="kpi-content">
            <span class="kpi-label">Yêu cầu bảo trì</span>
            <span class="kpi-value"><c:out value="${calcPendingUnits}" default="0" /></span>
          </div>
        </div>

        <div class="kpi-card">
          <div class="kpi-icon kpi-danger">
            <i class="fa-solid fa-server"></i>
          </div>
          <div class="kpi-content">
            <span class="kpi-label">Tổng cá thể máy</span>
            <span class="kpi-value"><c:out value="${calcTotalUnits}" default="0" /></span>
          </div>
        </div>
      </div>

      <%-- ===== 2. KHUNG TÌM KIẾM & BỘ LỌC ===== --%>
      <section class="facility-panel">
        <div class="facility-search-wrapper">
          <form method="get"
                action="${pageContext.request.contextPath}/receptionist/facilities"
                class="facility-filter">

            <div class="search-input-box">
              <i class="fa-solid fa-magnifying-glass search-icon"></i>
              <input type="search"
                     name="keyword"
                     maxlength="100"
                     value="<c:out value='${keyword}' />"
                     placeholder="Tìm tên thiết bị, mã máy con (DB-001...), danh mục hoặc vị trí...">
            </div>

            <div class="search-actions">
              <button class="btn btn-primary" type="submit">
                <i class="fa-solid fa-magnifying-glass"></i> Tìm kiếm
              </button>

              <c:if test="${not empty keyword}">
                <a class="btn btn-ghost"
                   href="${pageContext.request.contextPath}/receptionist/facilities">
                  <i class="fa-solid fa-xmark"></i> Xóa lọc
                </a>
              </c:if>
            </div>
          </form>

          <div class="catalog-quick-hint">
            <i class="fa-solid fa-circle-info" style="color: var(--color-primary);"></i>
            <span><strong>Mẹo thao tác:</strong> Bấm trực tiếp vào các mã máy con (ví dụ: DB-001, TM-005) để chọn nhiều máy và báo hỏng hàng loạt, hoặc bấm nút <strong>Báo hỏng</strong> ở từng dòng máy.</span>
          </div>
        </div>
      </section>

      <%-- ===== 3. DANH SÁCH CÁC DÒNG THIẾT BỊ ===== --%>
      <c:choose>

        <c:when test="${not empty facilityPage.items}">

          <div class="facility-list">

            <c:forEach var="facility" items="${facilityPage.items}">

              <article class="facility-card" data-facility-row-id="${facility.facilityId}">

                <div class="facility-header">

                  <div class="facility-title-wrap">
                    <div class="facility-icon-badge cat-${facility.category.dbValue}">
                      <c:choose>
                        <c:when test="${facility.category.dbValue == 'Cardio'}">
                          <i class="fa-solid fa-person-running"></i>
                        </c:when>
                        <c:when test="${facility.category.dbValue == 'FreeWeights'}">
                          <i class="fa-solid fa-dumbbell"></i>
                        </c:when>
                        <c:when test="${facility.category.dbValue == 'Machines'}">
                          <i class="fa-solid fa-gears"></i>
                        </c:when>
                        <c:otherwise>
                          <i class="fa-solid fa-box-archive"></i>
                        </c:otherwise>
                      </c:choose>
                    </div>

                    <div>
                      <h3 class="facility-title">
                        <c:out value="${facility.facilityName}" />
                      </h3>

                      <div class="facility-sub-meta">
                        <span class="meta-pill">
                          <i class="fa-solid fa-tag"></i>
                          <c:out value="${facility.category.displayName}" />
                        </span>

                        <c:if test="${not empty facility.location}">
                          <span class="meta-pill">
                            <i class="fa-solid fa-location-dot"></i>
                            <c:out value="${facility.location}" />
                          </span>
                        </c:if>
                      </div>
                    </div>
                  </div>

                  <div class="facility-status-action">
                    <div class="facility-availability-pill">
                      <c:choose>
                        <c:when test="${facility.status.dbValue == 'Retired'}">
                          <span class="badge badge-neutral">
                            <i class="fa-solid fa-ban"></i> Ngừng sử dụng
                          </span>
                        </c:when>
                        <c:when test="${facility.availableQuantity == facility.totalQuantity && facility.totalQuantity > 0}">
                          <span class="badge badge-success">
                            <i class="fa-solid fa-circle-check"></i> Khả dụng: ${facility.availableQuantity}/${facility.totalQuantity} máy
                          </span>
                        </c:when>
                        <c:when test="${facility.availableQuantity > 0}">
                          <span class="badge badge-warning">
                            <i class="fa-solid fa-triangle-exclamation"></i> Khả dụng: ${facility.availableQuantity}/${facility.totalQuantity} (${facility.pendingQuantity} đang sửa)
                          </span>
                        </c:when>
                        <c:otherwise>
                          <span class="badge badge-danger">
                            <i class="fa-solid fa-circle-xmark"></i> Không khả dụng (0/${facility.totalQuantity})
                          </span>
                        </c:otherwise>
                      </c:choose>
                    </div>

                    <c:if test="${facility.status.dbValue != 'Retired'}">
                      <button type="button"
                              class="btn btn-outline btn-report-row"
                              data-facility-id="${facility.facilityId}"
                              data-facility-name="<c:out value='${facility.facilityName}' />">
                        <i class="fa-solid fa-screwdriver-wrench"></i> Báo hỏng
                      </button>
                    </c:if>
                  </div>

                </div>

                <%-- Danh sách chip mã máy con (Multi-select) --%>
                <c:if test="${not empty facility.items}">
                  <div class="facility-chips-section">
                    <div class="chips-label-row">
                      <span class="chips-label">
                        <i class="fa-solid fa-barcode"></i> Mã máy con (${facility.items.size()}):
                      </span>
                      <span class="chips-hint">
                        (Bấm để chọn / bỏ chọn mã máy cần báo hỏng)
                      </span>
                    </div>

                    <div class="equipment-chips"
                         id="chips-group-${facility.facilityId}">
                      <c:forEach var="item" items="${facility.items}" varStatus="loop">
                        <c:set var="isUnderMaint" value="${item.status.dbValue == 'UnderMaintenance' || not empty item.activeRequestCode}" />

                        <button type="button"
                                class="equipment-chip ${item.badgeClass} ${loop.index >= 12 && not isUnderMaint ? 'chip-overflow is-hidden' : ''}"
                                data-item-id="${item.equipmentItemId}"
                                data-item-code="${item.itemCode}"
                                data-facility-id="${facility.facilityId}"
                                data-facility-name="<c:out value='${facility.facilityName}' />"
                                data-location="<c:out value='${facility.location}' />"
                                data-status="${item.status.dbValue}"
                                data-active-request="${item.activeRequestCode}"
                                title="Máy ${item.itemCode} (${item.status.displayName})<c:if test='${not empty item.activeRequestCode}'> · Đơn ${item.activeRequestCode}</c:if> · Bấm để chọn báo hỏng">
                          <span class="chip-status-dot"></span>
                          <span class="chip-text"><c:out value="${item.itemCode}" /></span>
                          <c:if test="${not empty item.activeRequestCode}">
                            <span class="chip-sub-tag">BT</span>
                          </c:if>
                        </button>
                      </c:forEach>

                      <c:if test="${facility.items.size() > 12}">
                        <button type="button"
                                class="btn-toggle-chips"
                                data-target="chips-group-${facility.facilityId}">
                          <i class="fa-solid fa-chevron-down"></i> Xem thêm (+${facility.items.size() - 12})
                        </button>
                      </c:if>
                    </div>
                  </div>
                </c:if>

              </article>

            </c:forEach>

          </div>

        </c:when>

        <c:otherwise>

          <div class="facility-panel">
            <div class="facility-empty">
              <i class="fa-solid fa-box-open" style="font-size: 2.5rem; color: var(--color-text-muted); margin-bottom: 12px; display: block;"></i>
              <p>Không tìm thấy thiết bị nào phù hợp với từ khóa <strong>"<c:out value='${keyword}' />"</strong>.</p>
              <a class="btn btn-outline btn-sm" href="${pageContext.request.contextPath}/receptionist/facilities" style="margin-top: 8px;">
                Xem tất cả thiết bị
              </a>
            </div>
          </div>

        </c:otherwise>

      </c:choose>

      <%-- Phân trang --%>
      <c:if test="${facilityPage.totalPages > 1}">

        <div class="facility-pager">

          <c:if test="${facilityPage.hasPrevious}">

            <c:url var="previousUrl"
                   value="/receptionist/facilities">

              <c:param name="page"
                       value="${facilityPage.pageNumber - 1}" />

              <c:param name="pageSize"
                       value="${facilityPage.pageSize}" />

              <c:if test="${not empty keyword}">
                <c:param name="keyword"
                         value="${keyword}" />
              </c:if>

            </c:url>

            <a class="btn btn-ghost btn-sm"
               href="${previousUrl}">
              <i class="fa-solid fa-arrow-left"></i> Trang trước
            </a>

          </c:if>

          <span style="font-size: var(--font-size-sm); color: var(--color-text-soft);">
            Trang <b>${facilityPage.pageNumber}</b> / <b>${facilityPage.totalPages}</b>
          </span>

          <c:if test="${facilityPage.hasNext}">

            <c:url var="nextUrl"
                   value="/receptionist/facilities">

              <c:param name="page"
                       value="${facilityPage.pageNumber + 1}" />

              <c:param name="pageSize"
                       value="${facilityPage.pageSize}" />

              <c:if test="${not empty keyword}">
                <c:param name="keyword"
                         value="${keyword}" />
              </c:if>

            </c:url>

            <a class="btn btn-ghost btn-sm"
               href="${nextUrl}">
              Trang sau <i class="fa-solid fa-arrow-right"></i>
            </a>

          </c:if>

        </div>

      </c:if>

      <%-- ===== 4. FLOATING STICKY SELECTION BAR ===== --%>
      <div id="stickySelectionBar" class="sticky-selection-bar" style="display: none;">
        <div class="sticky-bar-inner">
          <div class="sticky-bar-info">
            <div class="sticky-bar-badge">
              <i class="fa-solid fa-circle-check" style="color: var(--color-primary);"></i>
              <span>Đã chọn <strong id="selectedCountText">0</strong> máy:</span>
            </div>
            <div id="selectedChipsList" class="selected-chips-list"></div>
          </div>
          <div class="sticky-bar-actions">
            <button type="button" id="btnClearSelection" class="btn btn-ghost btn-sm">
              <i class="fa-solid fa-xmark"></i> Bỏ chọn
            </button>
            <button type="button" id="btnOpenReportModal" class="btn btn-primary">
              <i class="fa-solid fa-screwdriver-wrench"></i> Báo hỏng (<span id="btnReportCount">0</span> máy)
            </button>
          </div>
        </div>
      </div>

    </main>

  </div>

</div>

<%-- ================= 5. MODAL BÁO HỎNG THIẾT BỊ ================= --%>
<div class="modal-overlay" id="reportModal" aria-hidden="true" role="dialog" aria-labelledby="reportModalTitle">
  <form class="modal-box"
        action="${pageContext.request.contextPath}/receptionist/maintenance-requests"
        method="post"
        id="reportMaintenanceForm">
    <input type="hidden" name="action" value="create">
    <input type="hidden" name="csrfToken" value="<c:out value='${sessionScope.csrfToken}' default='${csrfToken}' />">
    <input type="hidden" name="equipmentItemIds" id="modalEquipmentItemIds" value="">

    <div class="modal-head">
      <div class="modal-title-group">
        <div class="modal-title-icon">
          <i class="fa-solid fa-screwdriver-wrench"></i>
        </div>
        <div>
          <h3 id="reportModalTitle">Báo hỏng thiết bị</h3>
          <p class="modal-subtitle">Gửi yêu cầu bảo trì đến Quản lý để duyệt và phân công xử lý</p>
        </div>
      </div>
      <button type="button" class="modal-close-btn" id="btnCloseReportModal" aria-label="Đóng">&times;</button>
    </div>

    <div class="modal-body">
        <%-- Danh sách máy được chọn --%>
        <div class="field">
          <label class="field-label">Mã máy gặp sự cố *</label>
          <div id="modalSelectedChipsContainer" class="modal-chips-container">
            <%-- Rendered by JS --%>
          </div>
          <span class="field-hint">
            <i class="fa-solid fa-circle-info"></i> Bấm vào dấu &times; trên chip nếu muốn bỏ bớt máy khỏi đợt báo hỏng này.
          </span>
        </div>

        <%-- Loại sự cố (IncidentType) --%>
        <div class="field">
          <label class="field-label">Loại sự cố *</label>
          <div class="incident-pills">
            <c:forEach var="inc" items="${incidentTypeOptions}" varStatus="loop">
              <label class="pill-radio">
                <input type="radio" name="incidentType" value="${inc.dbValue}" ${loop.first ? 'checked' : ''} required>
                <span class="pill-label">
                  <c:choose>
                    <c:when test="${inc.dbValue == 'Broken'}"><i class="fa-solid fa-ban"></i></c:when>
                    <c:when test="${inc.dbValue == 'NotWorking'}"><i class="fa-solid fa-power-off"></i></c:when>
                    <c:when test="${inc.dbValue == 'StrangeNoise'}"><i class="fa-solid fa-volume-high"></i></c:when>
                    <c:when test="${inc.dbValue == 'WearAndTear'}"><i class="fa-solid fa-wrench"></i></c:when>
                    <c:when test="${inc.dbValue == 'SafetyHazard'}"><i class="fa-solid fa-triangle-exclamation"></i></c:when>
                    <c:otherwise><i class="fa-solid fa-circle-exclamation"></i></c:otherwise>
                  </c:choose>
                  <c:out value="${inc.displayName}" />
                </span>
              </label>
            </c:forEach>
          </div>
        </div>

        <%-- Mức ưu tiên (Priority) 3 thẻ chọn ngang --%>
        <div class="field">
          <label class="field-label">Mức ưu tiên xử lý *</label>
          <div class="priority-cards">
            <label class="priority-card">
              <input type="radio" name="priority" value="Low">
              <div class="priority-box priority-low">
                <div class="priority-top">
                  <i class="fa-solid fa-clock"></i>
                  <strong>Thấp</strong>
                </div>
                <small>Vẫn dùng được, lỗi nhỏ</small>
              </div>
            </label>
            <label class="priority-card">
              <input type="radio" name="priority" value="Normal" checked>
              <div class="priority-box priority-normal">
                <div class="priority-top">
                  <i class="fa-solid fa-circle-check"></i>
                  <strong>Thường</strong>
                </div>
                <small>Ảnh hưởng trải nghiệm</small>
              </div>
            </label>
            <label class="priority-card">
              <input type="radio" name="priority" value="Urgent">
              <div class="priority-box priority-urgent">
                <div class="priority-top">
                  <i class="fa-solid fa-triangle-exclamation"></i>
                  <strong>Khẩn cấp</strong>
                </div>
                <small>Nguy cơ mất an toàn / hỏng nặng</small>
              </div>
            </label>
          </div>
        </div>

        <%-- Mô tả sự cố (10 - 500 ký tự) --%>
        <div class="field">
          <div class="field-label-row">
            <label class="field-label" for="modalDescription">Mô tả sự cố *</label>
            <span id="descCharCount" class="char-count">0 / 500</span>
          </div>
          <textarea id="modalDescription"
                    name="description"
                    rows="3"
                    minlength="10"
                    maxlength="500"
                    required
                    placeholder="Mô tả cụ thể biểu hiện hư hỏng, vị trí hoặc bộ phận gặp sự cố (tối thiểu 10 ký tự)..."></textarea>
          <span class="field-hint" id="descHint">
            <i class="fa-solid fa-circle-info"></i> Tối thiểu 10 ký tự để mô tả rõ cho Quản lý và thợ sửa chữa.
          </span>
        </div>

        <%-- Ai phát hiện? --%>
        <div class="field">
          <label class="field-label" for="modalDetectedBy">Ai phát hiện sự cố? *</label>
          <select id="modalDetectedBy" name="detectedBy" required>
            <c:forEach var="det" items="${detectedByOptions}">
              <option value="${det.dbValue}" ${det.dbValue == 'Receptionist' ? 'selected' : ''}>
                <c:out value="${det.displayName}" />
              </option>
            </c:forEach>
          </select>
        </div>

        <%-- Checkbox Cảnh báo --%>
        <div class="field warning-tag-field">
          <label class="warning-tag-label">
            <input type="checkbox" name="warningTagPlaced" value="true">
            <div class="warning-tag-content">
              <strong><i class="fa-solid fa-triangle-exclamation"></i> Đã dán cảnh báo / khóa máy, ngừng cho khách dùng</strong>
              <small>Khuyên dùng khi máy hỏng nặng hoặc nguy hiểm để đảm bảo an toàn cho hội viên.</small>
            </div>
          </label>
        </div>
      </div>

      <div class="modal-foot">
        <button type="button" class="btn btn-ghost" id="btnCancelReportModal">
          <i class="fa-solid fa-xmark"></i> Hủy
        </button>
        <button type="submit" class="btn btn-primary" id="btnSubmitReportModal">
          <i class="fa-solid fa-screwdriver-wrench"></i> Gửi báo hỏng (<span id="modalSubmitBtnCount">0</span> máy)
        </button>
      </div>
  </form>
</div>

<%-- Script xử lý Multi-Select, Sticky Bar và Modal --%>
<script>
  document.addEventListener('DOMContentLoaded', function () {
    var selectedItems = new Map();

    var stickyBar = document.getElementById('stickySelectionBar');
    var selectedCountText = document.getElementById('selectedCountText');
    var btnReportCount = document.getElementById('btnReportCount');
    var selectedChipsList = document.getElementById('selectedChipsList');
    var btnClearSelection = document.getElementById('btnClearSelection');
    var btnOpenReportModal = document.getElementById('btnOpenReportModal');

    var reportModal = document.getElementById('reportModal');
    var btnCloseReportModal = document.getElementById('btnCloseReportModal');
    var btnCancelReportModal = document.getElementById('btnCancelReportModal');
    var modalEquipmentItemIds = document.getElementById('modalEquipmentItemIds');
    var modalSelectedChipsContainer = document.getElementById('modalSelectedChipsContainer');
    var modalDescription = document.getElementById('modalDescription');
    var descCharCount = document.getElementById('descCharCount');
    var descHint = document.getElementById('descHint');
    var reportForm = document.getElementById('reportMaintenanceForm');

    // Cập nhật giao diện Sticky Selection Bar
    function updateStickyBar() {
      var count = selectedItems.size;
      if (count > 0) {
        stickyBar.style.display = 'flex';
        selectedCountText.textContent = count;
        btnReportCount.textContent = count;

        selectedChipsList.innerHTML = '';
        selectedItems.forEach(function (item) {
          var span = document.createElement('span');
          span.className = 'sticky-mini-chip';
          span.textContent = item.code;
          selectedChipsList.appendChild(span);
        });
      } else {
        stickyBar.style.display = 'none';
        selectedChipsList.innerHTML = '';
      }
    }

    // Toggle chọn chip
    function toggleChip(btn) {
      var itemId = btn.getAttribute('data-item-id');
      var itemCode = btn.getAttribute('data-item-code');
      var facilityId = btn.getAttribute('data-facility-id');
      var facilityName = btn.getAttribute('data-facility-name');
      var location = btn.getAttribute('data-location');
      var status = btn.getAttribute('data-status');
      var activeReq = btn.getAttribute('data-active-request');

      if (selectedItems.has(itemId)) {
        selectedItems.delete(itemId);
        btn.classList.remove('is-selected');
      } else {
        selectedItems.set(itemId, {
          id: itemId,
          code: itemCode,
          facilityId: facilityId,
          facilityName: facilityName,
          location: location,
          status: status,
          activeReq: activeReq
        });
        btn.classList.add('is-selected');
      }
      updateStickyBar();
    }

    // Gắn sự kiện click vào các chip mã máy
    document.querySelectorAll('.equipment-chip').forEach(function (btn) {
      btn.addEventListener('click', function (e) {
        e.preventDefault();
        toggleChip(this);
      });
    });

    // Nút Xem thêm / Thu gọn các chip ẩn
    document.querySelectorAll('.btn-toggle-chips').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var targetId = this.getAttribute('data-target');
        var container = document.getElementById(targetId);
        if (!container) return;

        var overflows = container.querySelectorAll('.chip-overflow');
        var isCurrentlyHidden = overflows.length > 0 && overflows[0].classList.contains('is-hidden');

        overflows.forEach(function (chip) {
          if (isCurrentlyHidden) {
            chip.classList.remove('is-hidden');
          } else {
            chip.classList.add('is-hidden');
          }
        });

        if (isCurrentlyHidden) {
          this.innerHTML = '<i class="fa-solid fa-chevron-up"></i> Thu gọn';
        } else {
          this.innerHTML = '<i class="fa-solid fa-chevron-down"></i> Xem thêm (+' + overflows.length + ')';
        }
      });
    });

    // Nút Bỏ chọn toàn bộ
    btnClearSelection.addEventListener('click', function () {
      selectedItems.clear();
      document.querySelectorAll('.equipment-chip.is-selected').forEach(function (btn) {
        btn.classList.remove('is-selected');
      });
      updateStickyBar();
    });

    // Mở Modal báo hỏng
    function openReportModal() {
      if (selectedItems.size === 0) {
        alert('Vui lòng chọn ít nhất một máy con cần báo hỏng.');
        return;
      }

      var ids = Array.from(selectedItems.keys()).join(',');
      modalEquipmentItemIds.value = ids;

      var modalSubmitBtnCount = document.getElementById('modalSubmitBtnCount');
      if (modalSubmitBtnCount) {
        modalSubmitBtnCount.textContent = selectedItems.size;
      }

      // Render danh sách chip trong modal
      modalSelectedChipsContainer.innerHTML = '';
      selectedItems.forEach(function (item) {
        var chipEl = document.createElement('div');
        chipEl.className = 'modal-chip-item';
        chipEl.innerHTML = '<span>' + item.code + ' · ' + item.facilityName + '</span>' +
          '<button type="button" class="modal-chip-remove" data-remove-id="' + item.id + '" title="Bỏ chọn">&times;</button>';
        modalSelectedChipsContainer.appendChild(chipEl);
      });

      // Gắn sự kiện xóa chip ngay trong modal
      modalSelectedChipsContainer.querySelectorAll('.modal-chip-remove').forEach(function (btn) {
        btn.addEventListener('click', function () {
          var removeId = this.getAttribute('data-remove-id');
          selectedItems.delete(removeId);

          var chipOnPage = document.querySelector('.equipment-chip[data-item-id="' + removeId + '"]');
          if (chipOnPage) {
            chipOnPage.classList.remove('is-selected');
          }

          updateStickyBar();

          if (selectedItems.size === 0) {
            closeReportModal();
          } else {
            openReportModal();
          }
        });
      });

      reportModal.classList.add('open');
      reportModal.setAttribute('aria-hidden', 'false');
      setTimeout(function () {
        modalDescription.focus();
      }, 100);
    }

    function closeReportModal() {
      reportModal.classList.remove('open');
      reportModal.setAttribute('aria-hidden', 'true');
    }

    // Sự kiện mở modal từ sticky bar
    btnOpenReportModal.addEventListener('click', openReportModal);

    // Nút "Báo hỏng" trên từng dòng thiết bị
    document.querySelectorAll('.btn-report-row').forEach(function (btn) {
      btn.addEventListener('click', function () {
        var facId = this.getAttribute('data-facility-id');
        var row = document.querySelector('.facility-card[data-facility-row-id="' + facId + '"]');
        if (!row) return;

        // Kiểm tra xem dòng này đã có chip nào được chọn chưa
        var selectedInRow = row.querySelectorAll('.equipment-chip.is-selected');
        if (selectedInRow.length === 0) {
          // Chưa có -> tự động chọn chip khả dụng đầu tiên
          var firstAvailable = row.querySelector('.equipment-chip:not(.badge-danger):not(.badge-neutral)') || row.querySelector('.equipment-chip');
          if (firstAvailable) {
            toggleChip(firstAvailable);
          } else {
            alert('Dòng thiết bị này hiện không có máy con nào có thể báo hỏng.');
            return;
          }
        }
        openReportModal();
      });
    });

    // Đóng modal
    btnCloseReportModal.addEventListener('click', closeReportModal);
    btnCancelReportModal.addEventListener('click', closeReportModal);

    reportModal.addEventListener('click', function (e) {
      if (e.target === reportModal) {
        closeReportModal();
      }
    });

    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && reportModal.classList.contains('open')) {
        closeReportModal();
      }
    });

    // Đếm ký tự mô tả
    modalDescription.addEventListener('input', function () {
      var len = this.value.length;
      descCharCount.textContent = len + ' / 500';
      if (len < 10) {
        descCharCount.style.color = 'var(--color-danger)';
        descHint.style.color = 'var(--color-danger)';
      } else {
        descCharCount.style.color = 'var(--color-success)';
        descHint.style.color = 'var(--color-text-soft)';
      }
    });

    // Validate trước khi submit
    reportForm.addEventListener('submit', function (e) {
      if (!modalEquipmentItemIds.value || modalEquipmentItemIds.value.trim() === '') {
        e.preventDefault();
        alert('Vui lòng chọn ít nhất một máy con cần báo hỏng.');
        return;
      }

      if (modalDescription.value.trim().length < 10) {
        e.preventDefault();
        alert('Mô tả hỏng hóc phải có ít nhất 10 ký tự.');
        modalDescription.focus();
        return;
      }
    });
  });
</script>

</body>
</html>