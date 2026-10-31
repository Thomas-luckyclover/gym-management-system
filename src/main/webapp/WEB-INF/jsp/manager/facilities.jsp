<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Cơ sở vật chất · Super Gym Quản lý</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/facilities.css?v=20261027v1">
</head>

<body class="dock theme-manager">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Cơ sở vật chất & Thiết bị</h1>
          <%-- Câu giải thích chuyển vào hint box chung (.pt-info, common.css / common.js) thay cho dòng chữ phụ dưới tiêu đề. --%>
          <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Hướng dẫn Cơ sở vật chất" aria-describedby="facilities-help"><span aria-hidden="true">i</span></button><span id="facilities-help" class="pt-info-popover" role="tooltip">Quản lý nhóm thiết bị, định danh mã máy vật lý và quy cách vị trí đặt máy.</span></span>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content" id="main-content" tabindex="-1">

        <div class="facilities-layout">
          
          <%-- ==================== CỘT TRÁI: DANH SÁCH NHÓM MÁY ==================== --%>
          <section class="facility-master-panel">
            <div class="panel-header">
              <h2 class="panel-title">Nhóm máy (${empty facilities ? 0 : facilities.size()})</h2>
              <button type="button" class="btn btn-sm btn-primary" onclick="openAddFacilityModal()">
                + Thêm nhóm
              </button>
            </div>

            <div class="facility-group-list">
              <c:choose>
                <c:when test="${empty facilities}">
                  <div class="empty-state">Chưa có nhóm cơ sở vật chất nào.</div>
                </c:when>
                <c:otherwise>
                  <c:forEach var="fac" items="${facilities}">
                    <a href="${pageContext.request.contextPath}/manager/facilities?selectedId=${fac.facilityId}"
                       class="facility-card-item ${fac.facilityId == selectedId ? 'active' : ''} ${!fac.active ? 'inactive' : ''}">
                      <div class="facility-thumb-box">
                        <c:choose>
                          <c:when test="${not empty fac.imageUrl}">
                            <img src="${fac.imageUrl.startsWith('/') ? pageContext.request.contextPath += fac.imageUrl : fac.imageUrl}" 
                                 alt="<c:out value='${fac.facilityName}' />"
                                 onerror="this.onerror=null; this.src='${pageContext.request.contextPath}/images/brand/facility.jpg';">
                          </c:when>
                          <c:otherwise>
                            <img src="${pageContext.request.contextPath}/images/brand/facility.jpg" alt="Gym">
                          </c:otherwise>
                        </c:choose>
                      </div>

                      <div class="facility-info-box">
                        <div class="facility-card-name"><c:out value="${fac.facilityName}" /></div>
                        <div class="facility-meta">
                          <span><c:out value="${fac.category.displayName}" /></span>
                          <span>•</span>
                          <span><strong><c:out value="${fac.availableQuantity}" /></strong>/<c:out value="${fac.totalQuantity}" /> <c:out value="${fac.unitLabel}" /></span>
                        </div>
                      </div>

                      <c:choose>
                        <c:when test="${!fac.active}">
                          <span class="badge badge-neutral" title="Đã ẩn khỏi danh mục lễ tân">Đã ẩn</span>
                        </c:when>
                        <c:when test="${fac.availableQuantity > 0}">
                          <span class="badge badge-success">Hoạt động</span>
                        </c:when>
                        <c:otherwise>
                          <span class="badge badge-warning">Bảo trì</span>
                        </c:otherwise>
                      </c:choose>
                    </a>
                  </c:forEach>
                </c:otherwise>
              </c:choose>
            </div>
          </section>

          <%-- ==================== CỘT PHẢI: CHI TIẾT NHÓM & CÁ THỂ MÁY CON ==================== --%>
          <section class="facility-detail-panel">
            <c:choose>
              <c:when test="${empty selectedFacility}">
                <div class="empty-state">
                  <p>Vui lòng chọn một nhóm thiết bị ở danh sách bên trái hoặc thêm nhóm mới.</p>
                </div>
              </c:when>
              <c:otherwise>
                <%-- Header tóm tắt nhóm máy --%>
                <div class="detail-top-summary">
                  <div class="detail-main-info">
                    <div class="detail-large-thumb">
                      <c:choose>
                        <c:when test="${not empty selectedFacility.imageUrl}">
                          <img src="${selectedFacility.imageUrl.startsWith('/') ? pageContext.request.contextPath += selectedFacility.imageUrl : selectedFacility.imageUrl}" 
                               alt="<c:out value='${selectedFacility.facilityName}' />"
                               onerror="this.onerror=null; this.src='${pageContext.request.contextPath}/images/brand/facility.jpg';">
                        </c:when>
                        <c:otherwise>
                          <img src="${pageContext.request.contextPath}/images/brand/facility.jpg" alt="Gym">
                        </c:otherwise>
                      </c:choose>
                    </div>

                    <div>
                      <div class="detail-title-line">
                        <h2 class="detail-title"><c:out value="${selectedFacility.facilityName}" /></h2>
                        <c:choose>
                          <c:when test="${!selectedFacility.active}">
                            <span class="badge badge-neutral">Đang ẩn</span>
                          </c:when>
                          <c:when test="${selectedFacility.availableQuantity > 0}">
                            <span class="badge badge-success">Đang vận hành</span>
                          </c:when>
                          <c:otherwise>
                            <span class="badge badge-warning">Tất cả đang bảo trì</span>
                          </c:otherwise>
                        </c:choose>
                      </div>
                      <div class="detail-location-line">
                        <span>Phân loại: <b><c:out value="${selectedFacility.category.displayName}" /></b></span>
                        <span>•</span>
                        <span>Khu vực: <b><c:out value="${empty selectedFacility.location ? 'Chưa xếp' : selectedFacility.location}" /></b></span>
                        <span>•</span>
                        <span>Đơn vị: <b><c:out value="${selectedFacility.unitLabel}" /></b></span>
                      </div>
                    </div>
                  </div>

                  <div class="detail-actions">
                    <button type="button" class="btn btn-sm btn-secondary" onclick="openEditFacilityModal()">
                      Sửa nhóm
                    </button>

                    <form method="post" action="${pageContext.request.contextPath}/manager/facilities" style="display:inline;">
                      <input type="hidden" name="csrfToken" value="${csrfToken}">
                      <input type="hidden" name="action" value="toggleFacility">
                      <input type="hidden" name="facilityId" value="${selectedFacility.facilityId}">
                      <input type="hidden" name="isActive" value="${!selectedFacility.active}">
                      <button type="submit" class="btn btn-sm ${selectedFacility.active ? 'btn-ghost' : 'btn-success'}"
                              data-confirm-title="${selectedFacility.active ? 'Ẩn nhóm máy' : 'Hiện nhóm máy'}"
                              data-confirm-message="${selectedFacility.active ? 'Bạn có chắc chắn muốn ẨN nhóm máy này khỏi danh mục báo hỏng?' : 'Bạn muốn HIỆN lại nhóm máy này?'}"
                              data-confirm-text="${selectedFacility.active ? 'Ẩn nhóm' : 'Hiện nhóm'}"
                              data-confirm-danger="${selectedFacility.active ? 'true' : 'false'}">
                        ${selectedFacility.active ? 'Ẩn nhóm' : 'Hiện nhóm'}
                      </button>
                    </form>
                  </div>
                </div>

                <%-- Các chỉ số đếm cá thể --%>
                <div class="group-stats-row">
                  <div class="group-stat-box">
                    <span class="group-stat-label">Tổng số cá thể</span>
                    <span class="group-stat-val"><c:out value="${selectedFacility.totalQuantity}" /></span>
                  </div>
                  <div class="group-stat-box">
                    <span class="group-stat-label">Khả dụng</span>
                    <span class="group-stat-val available"><c:out value="${selectedFacility.availableQuantity}" /></span>
                  </div>
                  <div class="group-stat-box">
                    <span class="group-stat-label">Đang bảo trì</span>
                    <span class="group-stat-val maintenance"><c:out value="${selectedFacility.pendingQuantity}" /></span>
                  </div>
                </div>

                <%-- Danh sách cá thể máy con --%>
                <div class="items-section-header">
                  <h3 class="items-section-title">Danh sách cá thể máy (${empty selectedFacility.items ? 0 : selectedFacility.items.size()})</h3>
                  <button type="button" class="btn btn-sm btn-primary" onclick="openAddItemModal()">
                    + Thêm cá thể máy
                  </button>
                </div>

                <div class="items-table-wrapper">
                  <table class="items-table">
                    <thead>
                      <tr>
                        <th style="width: 140px;">Mã máy</th>
                        <th>Tên hiển thị / Quy cách</th>
                        <th>Vị trí chi tiết</th>
                        <th style="width: 150px;">Trạng thái</th>
                        <th style="width: 140px; text-align: right;">Thao tác</th>
                      </tr>
                    </thead>
                    <tbody>
                      <c:choose>
                        <c:when test="${empty selectedFacility.items}">
                          <tr>
                            <td colspan="5" style="text-align:center; padding: 24px; color: var(--text-secondary);">
                              Chưa có cá thể máy vật lý nào trong nhóm này. Bấm <b>"+ Thêm cá thể máy"</b> để bắt đầu.
                            </td>
                          </tr>
                        </c:when>
                        <c:otherwise>
                          <c:forEach var="item" items="${selectedFacility.items}">
                            <tr class="${item.retired ? 'row-retired' : ''}">
                              <td>
                                <c:choose>
                                  <c:when test="${(item.underMaintenance || not empty item.activeRequestCode) && not empty item.activeRequestId}">
                                    <a href="${pageContext.request.contextPath}/manager/maintenance-requests/review?id=${item.activeRequestId}"
                                       class="code-badge code-badge-maintenance"
                                       title="Máy đang bảo trì - Bấm để chuyển tới trang bảo trì xem đơn ${item.activeRequestCode}">
                                      <c:out value="${item.itemCode}" />
                                    </a>
                                  </c:when>
                                  <c:otherwise>
                                    <span class="code-badge"><c:out value="${item.itemCode}" /></span>
                                  </c:otherwise>
                                </c:choose>
                              </td>
                              <td>
                                <strong><c:out value="${item.itemLabel}" default="-" /></strong>
                              </td>
                              <td>
                                <c:out value="${empty item.placementNote ? 'Chưa đặt' : item.placementNote}" />
                              </td>
                              <td>
                                <c:choose>
                                  <c:when test="${item.retired}">
                                    <span class="badge badge-neutral">Đã thanh lý</span>
                                  </c:when>
                                  <c:when test="${item.underMaintenance || not empty item.activeRequestCode}">
                                    <c:choose>
                                      <c:when test="${not empty item.activeRequestId}">
                                        <a href="${pageContext.request.contextPath}/manager/maintenance-requests/review?id=${item.activeRequestId}"
                                           class="badge badge-warning badge-maintenance-link"
                                           title="Bấm để chuyển tới trang bảo trì xem đơn ${item.activeRequestCode}">
                                          <i class="fa-solid fa-screwdriver-wrench me-1"></i>Đang bảo trì (${item.activeRequestCode})
                                        </a>
                                      </c:when>
                                      <c:otherwise>
                                        <a href="${pageContext.request.contextPath}/manager/maintenance-requests?keyword=${item.itemCode}"
                                           class="badge badge-warning badge-maintenance-link"
                                           title="Bấm để tìm đơn bảo trì của máy ${item.itemCode} trên trang bảo trì">
                                          <i class="fa-solid fa-screwdriver-wrench me-1"></i>Đang bảo trì (${item.activeRequestCode})
                                        </a>
                                      </c:otherwise>
                                    </c:choose>
                                  </c:when>
                                  <c:otherwise>
                                    <span class="badge badge-success">Khả dụng</span>
                                  </c:otherwise>
                                </c:choose>
                              </td>
                              <td style="text-align: right;">
                                <div class="table-actions" style="justify-content: flex-end;">
                                  <c:if test="${!item.retired}">
                                    <button type="button" class="btn btn-xs btn-secondary"
                                            onclick="openEditItemModal('${item.equipmentItemId}', '${item.itemCode}', '<c:out value="${item.itemLabel}" />', '<c:out value="${item.placementNote}" />')">
                                      Sửa
                                    </button>

                                    <form method="post" action="${pageContext.request.contextPath}/manager/facilities" style="display:inline;">
                                      <input type="hidden" name="csrfToken" value="${csrfToken}">
                                      <input type="hidden" name="action" value="retireItem">
                                      <input type="hidden" name="facilityId" value="${selectedFacility.facilityId}">
                                      <input type="hidden" name="equipmentItemId" value="${item.equipmentItemId}">
                                      <button type="submit" class="btn btn-xs btn-danger"
                                              data-confirm-title="Thanh lý máy ${item.itemCode}"
                                              data-confirm-message="Bạn có chắc chắn muốn thanh lý máy ${item.itemCode}? Máy sẽ chuyển sang trạng thái Ngừng sử dụng vĩnh viễn."
                                              data-confirm-text="Thanh lý"
                                              data-confirm-danger="true">Thanh lý</button>
                                    </form>
                                  </c:if>
                                </div>
                              </td>
                            </tr>
                          </c:forEach>
                        </c:otherwise>
                      </c:choose>
                    </tbody>
                  </table>
                </div>

              </c:otherwise>
            </c:choose>
          </section>

        </div>

      </main>
    </div>
  </div>

  <%-- ==================== DATALIST GỢI Ý PLACEMENT ==================== --%>
  <datalist id="placementSuggestionsList">
    <c:forEach var="suggestion" items="${placementSuggestions}">
      <option value="<c:out value='${suggestion}' />"></option>
    </c:forEach>
  </datalist>

  <%-- ==================== MODAL: THÊM / SỬA NHÓM MÁY ==================== --%>
  <div class="modal-overlay" id="facilityModal">
    <div class="modal-card">
      <div class="modal-header">
        <h3 class="modal-title" id="facilityModalTitle">Thêm nhóm máy mới</h3>
        <button type="button" class="modal-close-btn" onclick="closeModal('facilityModal')">&times;</button>
      </div>
      <form method="post" action="${pageContext.request.contextPath}/manager/facilities" enctype="multipart/form-data">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="saveFacility">
        <input type="hidden" name="facilityId" id="modalFacilityId" value="0">
        <input type="hidden" name="imageUrl" id="imageUrlInput" value="">

        <div class="modal-body">
          <div class="form-field">
            <label class="form-label" for="facilityNameInput">Tên nhóm máy *</label>
            <input type="text" id="facilityNameInput" name="facilityName" class="form-input" required minlength="3" maxlength="100" placeholder="VD: Máy chạy bộ Technogym">
          </div>

          <div class="form-field">
            <label class="form-label" for="categorySelect">Phân loại *</label>
            <select id="categorySelect" name="category" class="form-select" required>
              <c:forEach var="cat" items="${categories}">
                <option value="${cat.dbValue}"><c:out value="${cat.displayName}" /></option>
              </c:forEach>
            </select>
          </div>

          <div class="form-field">
            <label class="form-label" for="locationInput">Khu vực đặt</label>
            <input type="text" id="locationInput" name="location" class="form-input" maxlength="100" placeholder="VD: Khu cardio tầng 1, Phòng tạ tầng 2">
          </div>

          <div class="form-field">
            <label class="form-label" for="unitLabelInput">Đơn vị tính *</label>
            <input type="text" id="unitLabelInput" name="unitLabel" class="form-input" required maxlength="15" value="máy" placeholder="máy, bộ, quả, thảm, tủ...">
          </div>

          <div class="form-field">
            <label class="form-label">Ảnh đại diện nhóm máy</label>
            <div class="image-upload-wrapper">
              <div class="image-preview-box" id="facilityImagePreviewBox">
                <img id="facilityImagePreview" src="" alt="Xem trước ảnh" style="display:none; width:100%; height:100%; object-fit:cover; border-radius: 6px;">
                <div id="facilityImagePlaceholder" class="image-preview-placeholder">
                  <i class="fa-solid fa-image" style="font-size: 1.5rem; color: var(--text-secondary, #64748b);"></i>
                  <span style="font-size: 11px; color: var(--text-secondary, #64748b); margin-top: 2px;">Chưa có ảnh</span>
                </div>
              </div>
              <div class="image-upload-controls">
                <label class="btn btn-sm btn-outline btn-upload" for="facilityImageFileInput" style="cursor:pointer; display:inline-flex; align-items:center; gap:6px; width:fit-content;">
                  <i class="fa-solid fa-cloud-arrow-up"></i>
                  <span>Chọn file ảnh</span>
                </label>
                <input type="file" id="facilityImageFileInput" name="imageFile" accept="image/png,image/jpeg,image/webp" style="display:none;" onchange="previewFacilityImage(this)">
                <span id="facilityImageFileName" style="font-size: 12px; color: var(--color-primary, #0284c7); display:none; word-break:break-all;"></span>
                <small class="form-hint" style="display:block; margin-top:2px; color:var(--text-secondary, #64748b); font-size:11px;">
                  Hỗ trợ JPG, PNG, WEBP. Tự lưu vào thư mục và sinh đường dẫn.
                </small>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" onclick="closeModal('facilityModal')">Hủy</button>
          <button type="submit" class="btn btn-primary">Lưu nhóm máy</button>
        </div>
      </form>
    </div>
  </div>

  <%-- ==================== MODAL: THÊM / SỬA CÁ THỂ MÁY ==================== --%>
  <div class="modal-overlay" id="itemModal">
    <div class="modal-card">
      <div class="modal-header">
        <h3 class="modal-title" id="itemModalTitle">Thêm cá thể máy</h3>
        <button type="button" class="modal-close-btn" onclick="closeModal('itemModal')">&times;</button>
      </div>
      <form method="post" action="${pageContext.request.contextPath}/manager/facilities">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="saveItem">
        <input type="hidden" name="facilityId" value="${selectedFacility.facilityId}">
        <input type="hidden" name="equipmentItemId" id="modalEquipmentItemId" value="0">

        <div class="modal-body">
          <div class="form-field">
            <label class="form-label" for="itemCodeInput">Mã máy cá thể * (Duy nhất toàn viện)</label>
            <input type="text" id="itemCodeInput" name="itemCode" class="form-input" required minlength="2" maxlength="20" placeholder="VD: TM-009, DB-021, LK-051" style="text-transform:uppercase;">
          </div>

          <div class="form-field">
            <label class="form-label" for="itemLabelInput">Tên hiển thị / Quy cách *</label>
            <input type="text" id="itemLabelInput" name="itemLabel" class="form-input" required minlength="1" maxlength="50" placeholder="VD: Số 9, Quả 22 kg, Tủ 51...">
          </div>

          <div class="form-field">
            <label class="form-label" for="placementNoteInput">Vị trí chi tiết (Có gợi ý)</label>
            <input type="text" id="placementNoteInput" name="placementNote" class="form-input" list="placementSuggestionsList" maxlength="100" placeholder="Chọn hoặc gõ vị trí: Dãy cửa sổ, Dãy gương, Giá tạ tầng 2...">
          </div>
        </div>

        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" onclick="closeModal('itemModal')">Hủy</button>
          <button type="submit" class="btn btn-primary">Lưu cá thể</button>
        </div>
      </form>
    </div>
  </div>

  <script>
    function closeModal(id) {
      const modal = document.getElementById(id);
      if (modal) modal.classList.remove('show');
    }

    function openAddFacilityModal() {
      document.getElementById('facilityModalTitle').textContent = 'Thêm nhóm máy mới';
      document.getElementById('modalFacilityId').value = '0';
      document.getElementById('facilityNameInput').value = '';
      document.getElementById('categorySelect').selectedIndex = 0;
      document.getElementById('locationInput').value = '';
      document.getElementById('unitLabelInput').value = 'máy';
      document.getElementById('imageUrlInput').value = '';
      document.getElementById('facilityImageFileInput').value = '';
      const nameSpan = document.getElementById('facilityImageFileName');
      if (nameSpan) { nameSpan.style.display = 'none'; nameSpan.textContent = ''; }
      document.getElementById('facilityImagePreview').style.display = 'none';
      document.getElementById('facilityImagePreview').src = '';
      document.getElementById('facilityImagePlaceholder').style.display = 'flex';
      document.getElementById('facilityModal').classList.add('show');
    }

    function openEditFacilityModal() {
      document.getElementById('facilityModalTitle').textContent = 'Chỉnh sửa nhóm máy';
      document.getElementById('modalFacilityId').value = '${selectedFacility.facilityId}';
      document.getElementById('facilityNameInput').value = '<c:out value="${selectedFacility.facilityName}" />';
      document.getElementById('categorySelect').value = '${selectedFacility.category.dbValue}';
      document.getElementById('locationInput').value = '<c:out value="${selectedFacility.location}" />';
      document.getElementById('unitLabelInput').value = '<c:out value="${selectedFacility.unitLabel}" />';
      const currentImg = '<c:out value="${selectedFacility.imageUrl}" />';
      document.getElementById('imageUrlInput').value = currentImg;
      document.getElementById('facilityImageFileInput').value = '';
      const nameSpan = document.getElementById('facilityImageFileName');
      if (nameSpan) { nameSpan.style.display = 'none'; nameSpan.textContent = ''; }
      if (currentImg && currentImg.trim() !== '') {
        const fullImgUrl = currentImg.startsWith('http') || currentImg.startsWith('/')
          ? (currentImg.startsWith('/') ? '${pageContext.request.contextPath}' + currentImg : currentImg)
          : '${pageContext.request.contextPath}/' + currentImg;
        document.getElementById('facilityImagePreview').src = fullImgUrl;
        document.getElementById('facilityImagePreview').style.display = 'block';
        document.getElementById('facilityImagePlaceholder').style.display = 'none';
      } else {
        document.getElementById('facilityImagePreview').style.display = 'none';
        document.getElementById('facilityImagePreview').src = '';
        document.getElementById('facilityImagePlaceholder').style.display = 'flex';
      }
      document.getElementById('facilityModal').classList.add('show');
    }

    function previewFacilityImage(input) {
      if (input.files && input.files[0]) {
        const file = input.files[0];
        const nameSpan = document.getElementById('facilityImageFileName');
        if (nameSpan) {
          nameSpan.textContent = file.name + ' (' + Math.round(file.size / 1024) + ' KB)';
          nameSpan.style.display = 'block';
        }
        const reader = new FileReader();
        reader.onload = function(e) {
          const img = document.getElementById('facilityImagePreview');
          const placeholder = document.getElementById('facilityImagePlaceholder');
          img.src = e.target.result;
          img.style.display = 'block';
          placeholder.style.display = 'none';
        };
        reader.readAsDataURL(file);
      }
    }

    function openAddItemModal() {
      document.getElementById('itemModalTitle').textContent = 'Thêm cá thể máy vào nhóm';
      document.getElementById('modalEquipmentItemId').value = '0';
      document.getElementById('itemCodeInput').value = '';
      document.getElementById('itemCodeInput').readOnly = false;
      document.getElementById('itemLabelInput').value = '';
      document.getElementById('placementNoteInput').value = '';
      document.getElementById('itemModal').classList.add('show');
    }

    function openEditItemModal(id, code, label, placement) {
      document.getElementById('itemModalTitle').textContent = 'Sửa thông tin cá thể ' + code;
      document.getElementById('modalEquipmentItemId').value = id;
      document.getElementById('itemCodeInput').value = code;
      document.getElementById('itemCodeInput').readOnly = true;
      document.getElementById('itemLabelInput').value = label;
      document.getElementById('placementNoteInput').value = placement;
      document.getElementById('itemModal').classList.add('show');
    }

    // Đóng modal khi bấm ra ngoài vùng card
    window.onclick = function(event) {
      if (event.target.classList.contains('modal-overlay')) {
        event.target.classList.remove('show');
      }
    };
  </script>
</body>
</html>
