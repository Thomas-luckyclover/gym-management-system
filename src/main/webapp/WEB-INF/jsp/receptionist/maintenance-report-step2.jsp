<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Mô tả sự cố báo hỏng · Bước 2/2 · Super Gym</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/facility-catalog.css?v=20261026v1">
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

          <%-- Card thiết bị đang báo (Đồng bộ trực quan, tinh tế) --%>
          <div class="selected-equipment-summary">
            <div class="summary-media">
              <c:choose>
                <c:when test="${not empty facility.imageUrl}">
                  <img src="${pageContext.request.contextPath}${facility.imageUrl}"
                       alt="<c:out value='${facility.facilityName}' />"
                       class="summary-thumb-img">
                </c:when>
                <c:otherwise>
                  <div class="summary-thumb-placeholder">
                    <i class="fa-solid fa-dumbbell" aria-hidden="true"></i>
                  </div>
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
            <div class="alert-banner danger" style="margin-top: var(--space-4);">
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

            <%-- Loại sự cố (Radio Cards) --%>
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

            <%-- Mức ưu tiên (Radio Cards) --%>
            <div class="form-group-section">
              <label class="group-title">Mức ưu tiên *</label>
              <div class="radio-cards-grid priority-grid">
                <label class="radio-card priority-card">
                  <input type="radio" name="priority" value="Urgent">
                  <span class="radio-card-content">
                    <span class="radio-dot"></span>
                    <span class="radio-main-title">Khẩn</span>
                    <span class="radio-sub-title">Có thể gây chấn thương</span>
                  </span>
                </label>

                <label class="radio-card priority-card">
                  <input type="radio" name="priority" value="Normal" checked>
                  <span class="radio-card-content">
                    <span class="radio-dot"></span>
                    <span class="radio-main-title">Thường</span>
                    <span class="radio-sub-title">Không dùng được nhưng không nguy hiểm</span>
                  </span>
                </label>

                <label class="radio-card priority-card">
                  <input type="radio" name="priority" value="Low">
                  <span class="radio-card-content">
                    <span class="radio-dot"></span>
                    <span class="radio-main-title">Thấp</span>
                    <span class="radio-sub-title">Vẫn dùng được, lỗi nhỏ</span>
                  </span>
                </label>
              </div>
            </div>

            <%-- Ghi chú quả tạ / chi tiết cá thể ảnh hưởng --%>
            <div class="form-group-section">
              <label class="group-title" for="affectedItemsNoteInput">
                Ghi chú cá thể / mức tạ ảnh hưởng (không bắt buộc)
              </label>
              <input type="text"
                     id="affectedItemsNoteInput"
                     name="affectedItemsNote"
                     class="form-input"
                     maxlength="255"
                     value="<c:out value='${affectedItemsNote}' />"
                     placeholder="VD: Quả 12kg bị lỏng ốc ren, quả 16kg bị rách cao su bọc...">
              <small class="field-hint">Dùng khi cần ghi chú cụ thể các mức tạ hoặc vị trí hỏng của từng chiếc.</small>
            </div>

            <%-- Mô tả sự cố (10-500 ký tự) --%>
            <div class="form-group-section">
              <div class="field-label-row">
                <label class="group-title" for="descriptionInput">Mô tả sự cố *</label>
                <span class="char-counter"><span id="charCount">0</span>/500</span>
              </div>

              <textarea id="descriptionInput"
                        name="description"
                        rows="4"
                        minlength="10"
                        maxlength="500"
                        placeholder="Hỏng gì, từ lúc nào, đã xử lý tạm thế nào. VD: Băng chạy dừng đột ngột khi chạy 8 km/h, màn hình báo lỗi E05; đã rút điện."
                        required><c:out value="${description}" /></textarea>
              <small class="field-hint">Tối thiểu 10 ký tự, tối đa 500 ký tự.</small>
              <div class="field-error" id="descriptionError" <c:if test="${empty fieldErrors.description}">hidden</c:if>>
                <c:out value="${fieldErrors.description}" />
              </div>
            </div>

            <%-- Đính kèm ảnh sự cố (Upload ảnh) --%>
            <div class="form-group-section">
              <label class="group-title" for="imageFileInput">
                Ảnh sự cố (không bắt buộc) <span class="tag-badge">ĐỀ XUẤT</span>
              </label>

              <div class="upload-box-wrapper">
                <div class="file-drop-area" id="dropArea">
                  <input type="file" name="imageFile" id="imageFileInput" accept="image/jpeg,image/png,image/webp">
                  <div class="drop-content" id="dropContent">
                    <span class="upload-icon">+</span>
                    <span>Chụp / tải ảnh sự cố</span>
                  </div>
                  <div class="image-preview" id="imagePreview" style="display: none;"></div>
                </div>
              </div>
            </div>

            <%-- Khung hồng: Đã treo biển 'Ngừng sử dụng' tại máy --%>
            <div class="warning-tag-box">
              <label class="checkbox-tag-label">
                <input type="checkbox" name="warningTagPlaced" value="true" ${warningTagPlaced ? 'checked' : ''}>
                <div class="tag-label-content">
                  <strong class="tag-main-text">Đã treo biển "Ngừng sử dụng" tại máy</strong>
                  <span class="tag-sub-text">Tích khi máy không còn an toàn và khách phải ngừng dùng ngay.</span>
                </div>
              </label>
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
      var descInput = document.getElementById('descriptionInput');
      var charCount = document.getElementById('charCount');
      var fileInput = document.getElementById('imageFileInput');
      var dropContent = document.getElementById('dropContent');
      var imagePreview = document.getElementById('imagePreview');

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
      descInput.addEventListener('input', updateCount);
      updateCount();

      // Xem trước ảnh upload
      fileInput.addEventListener('change', function () {
        if (this.files && this.files[0]) {
          var reader = new FileReader();
          reader.onload = function (e) {
            imagePreview.innerHTML = '<img src="' + e.target.result + '" alt="Preview" style="max-height: 100px; border-radius: var(--radius-sm);">';
            imagePreview.style.display = 'block';
            dropContent.style.display = 'none';
          };
          reader.readAsDataURL(this.files[0]);
        } else {
          imagePreview.style.display = 'none';
          dropContent.style.display = 'flex';
        }
      });
    });
  </script>
</body>
</html>
