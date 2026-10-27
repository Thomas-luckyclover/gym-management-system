<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Mô tả sự cố báo hỏng · Bước 2/2 · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/facility-catalog.css?v=20261027v3">
</head>

<body class="dock theme-staff">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-receptionist.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Báo hỏng thiết bị</h1>
          <span class="tb-subtitle">Bước 2/2 · Mô tả để Quản lý duyệt mà không cần ra tận nơi</span>
        </div>

        <div class="top-right">
          <a class="btn btn-outline" href="${pageContext.request.contextPath}/receptionist/facilities">
            ← Đổi thiết bị khác
          </a>

          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content report-step2-content" id="main-content" tabindex="-1">
        <div class="report-step2-card">

          <%-- Card thiết bị đang báo (Chuẩn baseline typography, ảnh hiển thị chính xác) --%>
          <div class="selected-equipment-summary">
            <div class="summary-media">
              <c:choose>
                <c:when test="${not empty facility.imageUrl}">
                  <img src="${facility.imageUrl.startsWith('/') ? pageContext.request.contextPath += facility.imageUrl : facility.imageUrl}"
                       alt="<c:out value='${facility.facilityName}' />"
                       class="summary-thumb-img"
                       onerror="this.onerror=null; this.src='${pageContext.request.contextPath}/images/brand/facility.jpg';">
                </c:when>
                <c:otherwise>
                  <img src="${pageContext.request.contextPath}/images/brand/facility.jpg"
                       alt="<c:out value='${facility.facilityName}' />"
                       class="summary-thumb-img">
                </c:otherwise>
              </c:choose>
            </div>

            <div class="summary-info">
              <span class="summary-label">THIẾT BỊ BÁO HỎNG</span>
              <h2 class="summary-device-title">
                <c:out value="${facility.facilityName}" />
              </h2>
              <p class="summary-device-sub">
                <i class="fa-solid fa-location-dot"></i> <c:out value="${facility.location}" /> · <c:out value="${facility.category.displayName}" />
              </p>

              <%-- Danh sách các cá thể máy được chọn dạng chip --%>
              <div class="selected-items-chips-list">
                <span class="chips-list-title">Đã chọn <b>${empty selectedItems ? 1 : selectedItems.size()}</b> thiết bị:</span>
                <c:choose>
                  <c:when test="${not empty selectedItems}">
                    <c:forEach var="item" items="${selectedItems}">
                      <span class="selected-item-badge">
                        <i class="fa-solid fa-check"></i>
                        <c:out value="${empty item.itemLabel ? item.itemCode : item.itemLabel}" />
                        <small>(<c:out value="${item.itemCode}" />)</small>
                      </span>
                    </c:forEach>
                  </c:when>
                  <c:otherwise>
                    <span class="selected-item-badge">
                      <i class="fa-solid fa-check"></i>
                      <c:out value="${empty selectedItem.itemLabel ? selectedItem.itemCode : selectedItem.itemLabel}" />
                      <small>(<c:out value="${selectedItem.itemCode}" />)</small>
                    </span>
                  </c:otherwise>
                </c:choose>
              </div>
            </div>

            <div class="summary-action">
              <a class="btn btn-outline btn-sm" href="${pageContext.request.contextPath}/receptionist/facilities">
                <i class="fa-solid fa-arrow-left"></i> Đổi thiết bị
              </a>
            </div>
          </div>

          <c:if test="${not empty formError}">
            <div class="alert-banner danger" style="margin-bottom: var(--space-3);">
              <c:out value="${formError}" />
            </div>
          </c:if>

          <%-- Form nhập mô tả sự cố --%>
          <form method="post"
                action="${pageContext.request.contextPath}/receptionist/facilities/report"
                enctype="multipart/form-data"
                class="report-step2-form"
                data-validate
                novalidate>

            <input type="hidden" name="csrfToken" value="${csrfToken}">
            <input type="hidden" name="equipmentItemIds" value="${equipmentItemIds}">
            <input type="hidden" name="equipmentItemId" value="${selectedItem.equipmentItemId}">

            <%-- Loại sự cố (5 ô cùng nằm trên 1 hàng gọn gàng) --%>
            <div class="form-group-section">
              <label class="group-title">Loại sự cố *</label>
              <div class="radio-cards-grid incident-type-grid">
                <label class="radio-card">
                  <input type="radio" name="incidentType" value="NotWorking" checked>
                  <span class="radio-card-content">
                    <span class="radio-dot"></span>
                    <span class="radio-text">Không hoạt động</span>
                  </span>
                </label>

                <label class="radio-card">
                  <input type="radio" name="incidentType" value="StrangeNoise">
                  <span class="radio-card-content">
                    <span class="radio-dot"></span>
                    <span class="radio-text">Kêu / rung lạ</span>
                  </span>
                </label>

                <label class="radio-card">
                  <input type="radio" name="incidentType" value="BrokenOrWorn">
                  <span class="radio-card-content">
                    <span class="radio-dot"></span>
                    <span class="radio-text">Gãy vỡ / hao mòn</span>
                  </span>
                </label>

                <label class="radio-card">
                  <input type="radio" name="incidentType" value="SafetyHazard">
                  <span class="radio-card-content">
                    <span class="radio-dot"></span>
                    <span class="radio-text">Mất an toàn</span>
                  </span>
                </label>

                <label class="radio-card">
                  <input type="radio" name="incidentType" value="Hygiene">
                  <span class="radio-card-content">
                    <span class="radio-dot"></span>
                    <span class="radio-text">Vệ sinh</span>
                  </span>
                </label>
              </div>
            </div>

            <%-- Mức ưu tiên (3 ô gọn gàng, tiêu đề cùng hàng với dot) --%>
            <div class="form-group-section">
              <label class="group-title">Mức ưu tiên *</label>
              <div class="radio-cards-grid priority-grid">
                <label class="radio-card priority-card">
                  <input type="radio" name="priority" value="Urgent">
                  <span class="radio-card-content">
                    <div class="radio-title-row">
                      <span class="radio-dot"></span>
                      <span class="radio-main-title">Khẩn</span>
                    </div>
                    <span class="radio-sub-title">Có thể gây chấn thương</span>
                  </span>
                </label>

                <label class="radio-card priority-card">
                  <input type="radio" name="priority" value="Normal" checked>
                  <span class="radio-card-content">
                    <div class="radio-title-row">
                      <span class="radio-dot"></span>
                      <span class="radio-main-title">Thường</span>
                    </div>
                    <span class="radio-sub-title">Không dùng được nhưng không nguy hiểm</span>
                  </span>
                </label>

                <label class="radio-card priority-card">
                  <input type="radio" name="priority" value="Low">
                  <span class="radio-card-content">
                    <div class="radio-title-row">
                      <span class="radio-dot"></span>
                      <span class="radio-main-title">Thấp</span>
                    </div>
                    <span class="radio-sub-title">Vẫn dùng được, lỗi nhỏ</span>
                  </span>
                </label>
              </div>
            </div>

            <%-- Chi tiết máy / mức tạ đã chọn ở Bước 1 (Hiển thị thông tin trực tiếp, không cần gõ lại) --%>
            <div class="form-group-section">
              <label class="group-title">Chi tiết máy / mức tạ báo hỏng</label>
              <div class="selected-items-read-row">
                <c:choose>
                  <c:when test="${not empty selectedItems}">
                    <c:forEach var="item" items="${selectedItems}">
                      <span class="selected-item-tag">
                        <i class="fa-solid fa-check"></i>
                        <strong><c:out value="${empty item.itemLabel ? item.itemCode : item.itemLabel}" /></strong>
                        <small>(<c:out value="${item.itemCode}" />)</small>
                      </span>
                    </c:forEach>
                  </c:when>
                  <c:otherwise>
                    <span class="selected-item-tag">
                      <i class="fa-solid fa-check"></i>
                      <strong><c:out value="${empty selectedItem.itemLabel ? selectedItem.itemCode : selectedItem.itemLabel}" /></strong>
                      <small>(<c:out value="${selectedItem.itemCode}" />)</small>
                    </span>
                  </c:otherwise>
                </c:choose>
              </div>
              <input type="hidden" name="affectedItemsNote" value="<c:out value='${affectedItemsNote}' />">
            </div>

            <%-- Mô tả sự cố (10-500 ký tự) kèm icon chữ i màu đỏ trên cùng dòng label --%>
            <div class="form-group-section">
              <div class="field-label-row">
                <div class="label-with-hint">
                  <label class="group-title" for="descriptionInput">Mô tả sự cố *</label>
                  <span class="hint-icon-trigger" tabindex="0" role="tooltip" aria-label="Hướng dẫn mô tả sự cố">
                    <i class="fa-solid fa-circle-info hint-red-i"></i>
                    <span class="hint-floating-popover">
                      Nhập từ 10 - 500 ký tự. Nên ghi rõ mã lỗi trên màn hình (nếu có).
                    </span>
                  </span>
                </div>
                <span class="char-counter"><span id="charCount">0</span>/500</span>
              </div>

              <%-- Dòng báo lỗi chữ đỏ DUY NHẤT (chỉ hiện khi có lỗi validate) --%>
              <div class="field-error-msg" id="descriptionError" <c:if test="${empty fieldErrors.description}">style="display: none;"</c:if>>
                <svg class="error-icon" width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="15" y1="9" x2="9" y2="15"></line>
                  <line x1="9" y1="9" x2="15" y2="15"></line>
                </svg>
                <span id="descriptionErrorText"><c:out value="${empty fieldErrors.description ? 'Vui lòng mô tả sự cố (tối thiểu 10 ký tự).' : fieldErrors.description}" /></span>
              </div>

              <textarea id="descriptionInput"
                        name="description"
                        rows="2"
                        minlength="10"
                        maxlength="500"
                        class="${not empty fieldErrors.description ? 'is-invalid' : ''}"
                        placeholder="VD: Đang chạy thì dừng đột ngột, báo lỗi E05. Đã rút điện."
                        required><c:out value="${description}" /></textarea>
            </div>

            <%-- Hàng dưới: Ảnh sự cố + Treo biển cảnh báo an toàn (Cân đối đều 2 cột) --%>
            <div class="report-bottom-grid">
              <%-- Cột trái: Đính kèm ảnh sự cố (Upload ảnh) --%>
              <div class="form-group-section bottom-col-item">
                <div class="field-label-row">
                  <label class="group-title" for="imageFileInput">
                    Ảnh sự cố (không bắt buộc) <span class="tag-badge">ĐỀ XUẤT</span>
                  </label>
                </div>
                <div class="upload-box-wrapper">
                  <div class="file-drop-area" id="dropArea">
                    <input type="file" name="imageFile" id="imageFileInput" accept="image/jpeg,image/png,image/webp">
                    <div class="drop-content" id="dropContent">
                      <i class="fa-solid fa-camera upload-icon" aria-hidden="true"></i>
                      <div class="drop-text-group">
                        <span class="drop-main-text">Chụp / tải ảnh sự cố</span>
                        <span class="drop-sub-text">JPG, PNG, WEBP tối đa 10MB</span>
                      </div>
                    </div>
                    <div class="image-preview" id="imagePreview" style="display: none;"></div>
                  </div>
                </div>
              </div>

              <%-- Cột phải: Khung treo biển 'Ngừng sử dụng' tại máy (Chữ i màu đỏ nằm cùng dòng label Biện pháp tại chỗ) --%>
              <div class="form-group-section bottom-col-item">
                <div class="field-label-row">
                  <div class="label-with-hint">
                    <span class="group-title">Biện pháp tại chỗ</span>
                    <span class="hint-icon-trigger" tabindex="0" role="tooltip" aria-label="Lưu ý an toàn">
                      <i class="fa-solid fa-circle-info hint-red-i"></i>
                      <span class="hint-floating-popover">
                        Bắt buộc tích nếu sự cố có thể gây chấn thương cho khách.
                      </span>
                    </span>
                  </div>
                </div>

                <div class="warning-tag-box">
                  <label class="checkbox-tag-label">
                    <input type="checkbox" name="warningTagPlaced" value="true" ${warningTagPlaced ? 'checked' : ''}>
                    <strong class="tag-main-text">Đã treo biển "Ngừng sử dụng" tại máy</strong>
                  </label>
                </div>
              </div>
            </div>

            <%-- Nút thao tác dưới cùng --%>
            <div class="form-actions-row">
              <a class="btn btn-outline" href="${pageContext.request.contextPath}/receptionist/facilities">
                Quay lại
              </a>

              <button class="btn btn-primary btn-submit-report" type="submit" id="btnSubmitReport">
                Gửi báo hỏng
              </button>
            </div>

          </form>
        </div>
      </main>

    </div>
  </div>

  <script>
    document.addEventListener('DOMContentLoaded', function () {
      var form = document.querySelector('.report-step2-form');
      var descInput = document.getElementById('descriptionInput');
      var charCount = document.getElementById('charCount');
      var descError = document.getElementById('descriptionError');
      var descErrorText = document.getElementById('descriptionErrorText');
      var fileInput = document.getElementById('imageFileInput');
      var dropContent = document.getElementById('dropContent');
      var imagePreview = document.getElementById('imagePreview');

      function escapeHtml(str) {
        if (!str) return '';
        var div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
      }

      // Đếm ký tự realtime
      function updateCount() {
        var len = descInput.value.length;
        charCount.textContent = len;
        if (len < 10) {
          charCount.style.color = 'var(--color-danger, #ef4444)';
        } else {
          charCount.style.color = 'var(--color-text-muted, #64748b)';
        }
      }

      // Xử lý validate: hiện duy nhất 1 dòng báo lỗi chữ đỏ khi không đủ 10 ký tự
      function validateDescription() {
        var val = descInput.value.trim();
        if (val.length < 10) {
          if (descError) {
            descError.style.display = 'inline-flex';
            if (descErrorText) descErrorText.textContent = 'Vui lòng mô tả sự cố (tối thiểu 10 ký tự).';
          }
          descInput.classList.add('is-invalid');
          return false;
        } else {
          if (descError) descError.style.display = 'none';
          descInput.classList.remove('is-invalid');
          return true;
        }
      }

      descInput.addEventListener('input', function () {
        updateCount();
        if (descInput.classList.contains('is-invalid') || (descError && descError.style.display !== 'none')) {
          validateDescription();
        }
      });
      updateCount();

      // Kiểm tra khi submit
      if (form) {
        form.addEventListener('submit', function (e) {
          if (!validateDescription()) {
            e.preventDefault();
            descInput.focus();
          }
        });
      }

      // Xem trước ảnh upload (gọn gàng, cân đối trong khung)
      fileInput.addEventListener('change', function () {
        if (this.files && this.files[0]) {
          var file = this.files[0];
          var reader = new FileReader();
          reader.onload = function (e) {
            imagePreview.innerHTML =
              '<div class="preview-inner">' +
                '<img src="' + e.target.result + '" alt="Preview" class="preview-img">' +
                '<div class="preview-meta">' +
                  '<span class="preview-filename">' + escapeHtml(file.name) + '</span>' +
                  '<span class="preview-change"><i class="fa-solid fa-arrows-rotate"></i> Bấm để đổi ảnh</span>' +
                '</div>' +
              '</div>';
            imagePreview.style.display = 'flex';
            dropContent.style.display = 'none';
          };
          reader.readAsDataURL(file);
        } else {
          imagePreview.innerHTML = '';
          imagePreview.style.display = 'none';
          dropContent.style.display = 'flex';
        }
      });
    });
  </script>
</body>
</html>
