<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Yêu cầu bảo trì · Super Gym Lễ tân</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/receptionist/maintenance-requests.css?v=20261113v1">

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

          <a class="btn btn-outline"
             href="${pageContext.request.contextPath}/receptionist/facilities">
            ‹ Cơ sở vật chất / Báo hỏng mới
          </a>

          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content maintenance-receptionist-content" id="main-content" tabindex="-1">

        <%-- ===== Thống kê trạng thái ===== --%>
        <div class="maintenance-stats">

          <%-- Tất cả --%>
          <c:url var="allStatusUrl"
                 value="/receptionist/maintenance-requests">
            <c:if test="${not empty keyword}">
              <c:param name="keyword" value="${keyword}" />
            </c:if>
            <c:if test="${not empty priorityFilter}">
              <c:param name="priority" value="${priorityFilter}" />
            </c:if>
            <c:if test="${not empty reportedDateFilter}">
              <c:param name="reportedDate" value="${reportedDateFilter}" />
            </c:if>
          </c:url>

          <a href="${allStatusUrl}"
             class="maintenance-stat ${empty statusFilter ? 'active' : ''}">
            <span>Tất cả</span>
            <strong><c:out value="${totalStatusCount}" default="0" /></strong>
          </a>

          <%-- Theo từng trạng thái --%>
          <c:forEach var="status" items="${statusOptions}">
            <c:url var="statusUrl"
                   value="/receptionist/maintenance-requests">
              <c:param name="status" value="${status.dbValue}" />
              <c:if test="${not empty keyword}">
                <c:param name="keyword" value="${keyword}" />
              </c:if>
              <c:if test="${not empty priorityFilter}">
                <c:param name="priority" value="${priorityFilter}" />
              </c:if>
              <c:if test="${not empty reportedDateFilter}">
                <c:param name="reportedDate" value="${reportedDateFilter}" />
              </c:if>
            </c:url>

            <a href="${statusUrl}"
               class="maintenance-stat ${statusFilter == status.dbValue ? 'active' : ''}">
              <span><c:out value="${status.displayName}" /></span>
              <strong><c:out value="${statusCounts[status.dbValue]}" default="0" /></strong>
            </a>
          </c:forEach>

        </div>

        <%-- ===== Danh sách yêu cầu ===== --%>
        <section class="panel maintenance-panel">
          <div class="panel-head">
            <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
              <h2>Danh sách yêu cầu</h2>
              <label class="select-all-in-progress-label" id="labelCheckAllInProgress" style="display: none; align-items: center; gap: 6px; font-size: 13px; cursor: pointer; user-select: none;">
                <input type="checkbox" id="checkAllInProgress" class="bulk-checkbox">
                <span>Chọn tất cả đang xử lý</span>
              </label>
            </div>
          </div>

          <form action="${pageContext.request.contextPath}/receptionist/maintenance-requests"
                method="get"
                class="maintenance-filter">

            <c:if test="${not empty statusFilter}">
              <input type="hidden" name="status" value="<c:out value='${statusFilter}' />">
            </c:if>

            <input type="search"
                   name="keyword"
                   maxlength="100"
                   value="<c:out value='${keyword}' />"
                   placeholder="Mã yêu cầu hoặc tên thiết bị">

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
                   lang="vi"
                   name="reportedDate"
                   value="<c:out value='${reportedDateFilter}' />"
                   title="Lọc theo ngày báo"
                   aria-label="Ngày báo">

            <select name="pageSize">
              <option value="10" ${requestPage.pageSize == 10 ? 'selected' : ''}>10</option>
              <option value="20" ${requestPage.pageSize == 20 ? 'selected' : ''}>20</option>
              <option value="50" ${requestPage.pageSize == 50 ? 'selected' : ''}>50</option>
            </select>
            <button class="btn btn-primary" type="submit">
              Lọc
            </button>
            <c:if test="${not empty keyword || not empty statusFilter || not empty priorityFilter || not empty reportedDateFilter}">
              <a class="btn btn-ghost" href="${pageContext.request.contextPath}/receptionist/maintenance-requests">
                Xóa lọc
              </a>
            </c:if>
          </form>

          <%-- Thanh nghiệm thu hàng loạt cho Lễ tân --%>
          <div id="receptionistBulkBar" class="bulk-action-bar" style="display: none;">
            <div class="bulk-info">
              <span class="bulk-icon"><i class="fa-solid fa-list-check"></i></span>
              <span class="bulk-text">Đã chọn <strong id="receptionistBulkCount">0</strong> yêu cầu đang xử lý</span>
              <span class="bulk-codes text-muted" id="receptionistBulkCodes"></span>
            </div>
            <div class="bulk-buttons">
              <button type="button" class="btn btn-outline btn-sm" id="btnCancelReceptionistBulk">Bỏ chọn</button>
              <button type="button" class="btn btn-primary btn-sm" id="btnTriggerReceptionistBulkClose">
                <i class="fa-solid fa-circle-check"></i> Nghiệm thu hàng loạt (<span id="receptionistBulkBtnCount">0</span>)
              </button>
            </div>
          </div>

          <c:choose>
            <c:when test="${not empty requestPage.items}">
              <div class="maintenance-list">
                <c:forEach var="item" items="${requestPage.items}">
                  <article class="maintenance-card">

                    <div class="maintenance-card-main">
                      <div class="maintenance-card-head">
                        <div class="maintenance-card-title-group">
                          <c:if test="${item.request.inProgress}">
                            <input type="checkbox"
                                   class="bulk-checkbox receptionist-bulk-checkbox"
                                   value="${item.request.maintenanceRequestId}"
                                   data-request-code="<c:out value='${item.request.requestCode}' />"
                                   data-item-code="<c:out value='${item.itemCode}' />"
                                   data-facility-name="<c:out value='${item.facilityName}' />"
                                   title="Chọn để nghiệm thu hàng loạt">
                          </c:if>
                          <strong>
                            <c:out value="${item.request.requestCode}" /> · <c:out value="${item.facilityName}" />
                            <c:choose>
                              <c:when test="${not empty item.request.affectedItemsNote}">
                                · <c:out value="${item.request.affectedItemsNote}" /> · <c:out value="${item.itemCode}" />
                              </c:when>
                              <c:otherwise>
                                · <c:out value="${item.itemLabel}" /><c:if test="${not empty item.placementNote}"> · <c:out value="${item.placementNote}" /></c:if> (<c:out value="${item.itemCode}" />)
                              </c:otherwise>
                            </c:choose>
                          </strong>
                        </div>

                        <div class="maintenance-badges-group">
                          <c:if test="${item.request.warningTagPlaced}">
                            <span class="badge badge-danger">
                              <i class="fa-solid fa-triangle-exclamation"></i> Đã đặt cảnh báo
                            </span>
                          </c:if>
                          <span class="badge
                            ${item.request.open ? 'badge-warning' :
                              item.request.inProgress ? 'badge-info' :
                              item.request.closed ? 'badge-success' : 'badge-neutral'}">
                            <c:out value="${item.request.status.displayName}" />
                          </span>
                        </div>
                      </div>

                      <div class="maintenance-meta">
                        <span>
                          Ngày báo: <b>${gms:formatDate(item.request.reportedAt.toLocalDate())}</b>
                        </span>

                        <span>
                          Ưu tiên: <b class="priority-val priority-${item.request.priority.dbValue.toLowerCase()}"><c:out value="${item.request.priority.displayName}" /></b>
                        </span>

                        <c:if test="${not empty item.facilityLocation}">
                          <span>
                            Khu vực: <b><c:out value="${item.facilityLocation}" /></b>
                          </span>
                        </c:if>

                        <span>
                          Sự cố: <b><c:out value="${item.request.incidentType.displayName}" /></b>
                        </span>

                        <span>
                          Người báo: <b><c:out value="${item.reporterName}" /></b>
                        </span>
                      </div>

                      <div class="maintenance-body-row">
                        <div class="maintenance-desc-col">
                          <p class="maintenance-description">
                            <c:out value="${item.request.description}" />
                          </p>
                        </div>

                        <c:if test="${not empty item.request.attachedImages}">
                          <div class="maintenance-image-col">
                            <a href="${pageContext.request.contextPath}${item.request.attachedImages}"
                               class="maintenance-image-thumb-card image-lightbox-trigger"
                               data-lightbox-image="${pageContext.request.contextPath}${item.request.attachedImages}"
                               data-lightbox-title="Ảnh sự cố máy ${item.itemCode} (${item.request.requestCode})"
                               title="Bấm để xem ảnh phóng to ngay trên trang">
                              <img src="${pageContext.request.contextPath}${item.request.attachedImages}"
                                   alt="Ảnh sự cố thực tế ${item.itemCode}"
                                   class="maintenance-thumbnail-img"
                                   onerror="this.onerror=null; this.closest('.maintenance-image-col').style.display='none';">
                              <span class="maintenance-thumbnail-caption">
                                <i class="fa-solid fa-magnifying-glass-plus" aria-hidden="true"></i> Xem ảnh
                              </span>
                            </a>
                          </div>
                        </c:if>
                      </div>

                      <div class="maintenance-progress">
                        <c:choose>
                          <c:when test="${item.request.open}">
                            Chờ Quản lý duyệt.
                          </c:when>

                          <c:when test="${item.request.inProgress}">
                            <div>Quản lý <b><c:out value="${item.reviewerName}" /></b> đã duyệt.</div>
                            <c:if test="${not empty item.request.managerNote}">
                              <div class="manager-note" style="margin-top: 4px; font-size: 12.5px; color: var(--color-text-subtle);">
                                <i class="fa-solid fa-clipboard-check" aria-hidden="true"></i> Ghi chú của Quản lý: <b><c:out value="${item.request.managerNote}" /></b>
                              </div>
                            </c:if>
                          </c:when>

                          <c:when test="${item.request.rejected}">
                            Bị từ chối:
                            <c:out value="${item.request.rejectReason}" />
                          </c:when>

                          <c:when test="${item.request.closed}">
                            <div>Quản lý <b><c:out value="${item.reviewerName}" /></b> đã duyệt.</div>
                            <c:if test="${not empty item.request.managerNote}">
                              <div class="manager-note" style="margin-top: 2px; font-size: 12px; color: var(--color-text-subtle);">
                                <i class="fa-solid fa-clipboard-check" aria-hidden="true"></i> Ghi chú của Quản lý: <b><c:out value="${item.request.managerNote}" /></b>
                              </div>
                            </c:if>
                            <div style="margin-top: 4px;">
                              Đã nghiệm thu bởi
                              <b><c:out value="${item.closerName}" /></b>.
                              <c:out value="${item.request.acceptanceNote}" />
                            </div>
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
                               name="csrfToken"
                               value="${sessionScope.csrfToken}">

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
          <%-- requestPage.totalPages = getter tự tính getTotalPages() · dto/PageResult.java --%>
          <c:if test="${requestPage.totalPages > 1}">
            <div class="maintenance-pager">

              <c:choose>
                <%-- requestPage.hasPrevious = getter tự tính isHasPrevious() · dto/PageResult.java --%>
                <c:when test="${requestPage.hasPrevious}">
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
                    ‹ Trang trước
                  </a>
                </c:when>
                <c:otherwise>
                  <button type="button" class="btn btn-ghost btn-sm" disabled>
                    ‹ Trang trước
                  </button>
                </c:otherwise>
              </c:choose>

              <span class="pager-info">
                Trang ${requestPage.pageNumber}
                / ${requestPage.totalPages}
              </span>

              <c:choose>
                <%-- requestPage.hasNext = getter tự tính isHasNext() · dto/PageResult.java --%>
                <c:when test="${requestPage.hasNext}">
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
                    Trang sau ›
                  </a>
                </c:when>
                <c:otherwise>
                  <button type="button" class="btn btn-ghost btn-sm" disabled>
                    Trang sau ›
                  </button>
                </c:otherwise>
              </c:choose>

            </div>
          </c:if>

        </section>

      </main>
    </div>
  </div>

  <%-- ===== MODAL NGHIỆM THU HÀNG LOẠT (LỄ TÂN) ===== --%>
  <div id="modalReceptionistBulkClose" class="gms-modal" role="dialog" aria-modal="true" aria-labelledby="receptionistModalTitle">
    <div class="gms-modal-dialog">
      <form action="${pageContext.request.contextPath}/receptionist/maintenance-requests" method="post" id="formReceptionistBulkClose">
        <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
        <input type="hidden" name="action" value="batchClose">
        <input type="hidden" name="requestIds" id="receptionistBulkRequestIdsHidden">

        <div class="gms-modal-header">
          <h3 class="gms-modal-title" id="receptionistModalTitle">Nghiệm thu & Đóng hàng loạt yêu cầu bảo trì</h3>
          <button type="button" class="gms-modal-close" onclick="closeReceptionistBulkModal()">&times;</button>
        </div>
        <div class="gms-modal-body">
          <div class="bulk-modal-summary">
            Xác nhận nghiệm thu đồng loạt <strong id="receptionistModalSummaryCount">0</strong> yêu cầu bảo trì đã chọn:
            <div class="bulk-modal-tags" id="receptionistModalSummaryTags"></div>
          </div>

          <div class="alert-banner info" style="font-size: 12.5px; padding: 8px 12px; margin-top: 4px;">
            <i class="fa-solid fa-circle-info" aria-hidden="true"></i>
            <span>Hệ thống sẽ chuyển các yêu cầu trên sang <b>Đã đóng</b> và tự động khôi phục các thiết bị liên quan về trạng thái <b>Khả dụng</b> để tiếp tục phục vụ hội viên.</span>
          </div>

          <div class="field" style="margin-top: 6px;">
            <label class="field-label" for="bulkAcceptanceNoteInput">
              <span>Ghi chú nghiệm thu chung <span class="required-mark">*</span></span>
              <span class="field-label-meta">(5 - 500 ký tự)</span>
            </label>
            <textarea id="bulkAcceptanceNoteInput"
                      name="acceptanceNote"
                      class="form-input"
                      rows="3"
                      minlength="5"
                      maxlength="500"
                      required
                      placeholder="VD: Đã kiểm tra vận hành thử toàn bộ máy, thiết bị hoạt động bình thường, đã vệ sinh và gỡ biển cảnh báo..."></textarea>
            <small class="field-hint">Ghi chú này sẽ được lưu chung vào tất cả các phiếu được nghiệm thu.</small>
            <div class="field-error" id="bulkAcceptanceNoteError" style="display:none; color:var(--color-danger); font-size:12px; margin-top:4px;"></div>
          </div>
        </div>
        <div class="gms-modal-footer">
          <button type="button" class="btn btn-ghost" onclick="closeReceptionistBulkModal()">Hủy</button>
          <button type="submit" class="btn btn-primary" id="btnSubmitReceptionistBulk">
            <i class="fa-solid fa-check-double"></i> Xác nhận nghiệm thu & Đóng tất cả
          </button>
        </div>
      </form>
    </div>
  </div>

  <script>
    (function () {
      var checkAll = document.getElementById('checkAllInProgress');
      var labelCheckAll = document.getElementById('labelCheckAllInProgress');
      var rowCheckboxes = document.querySelectorAll('.receptionist-bulk-checkbox');
      var bulkBar = document.getElementById('receptionistBulkBar');
      var bulkCount = document.getElementById('receptionistBulkCount');
      var bulkBtnCount = document.getElementById('receptionistBulkBtnCount');
      var bulkCodes = document.getElementById('receptionistBulkCodes');
      var btnCancel = document.getElementById('btnCancelReceptionistBulk');
      var btnTrigger = document.getElementById('btnTriggerReceptionistBulkClose');

      var modal = document.getElementById('modalReceptionistBulkClose');
      var hiddenIds = document.getElementById('receptionistBulkRequestIdsHidden');
      var modalSummaryCount = document.getElementById('receptionistModalSummaryCount');
      var modalSummaryTags = document.getElementById('receptionistModalSummaryTags');
      var noteInput = document.getElementById('bulkAcceptanceNoteInput');
      var noteError = document.getElementById('bulkAcceptanceNoteError');
      var form = document.getElementById('formReceptionistBulkClose');

      if (rowCheckboxes.length > 0 && labelCheckAll) {
        labelCheckAll.style.display = 'inline-flex';
      }

      function getSelectedCheckboxes() {
        return Array.from(rowCheckboxes).filter(function (cb) {
          return cb.checked;
        });
      }

      function updateUI() {
        var selected = getSelectedCheckboxes();
        var count = selected.length;

        rowCheckboxes.forEach(function (cb) {
          var card = cb.closest('.maintenance-card');
          if (card) {
            if (cb.checked) {
              card.classList.add('is-selected');
            } else {
              card.classList.remove('is-selected');
            }
          }
        });

        if (count > 0) {
          bulkBar.style.display = 'flex';
          bulkCount.textContent = count;
          bulkBtnCount.textContent = count;

          var codes = selected.map(function (cb) {
            return cb.getAttribute('data-request-code') || ('#' + cb.value);
          });

          if (codes.length <= 4) {
            bulkCodes.textContent = '(' + codes.join(', ') + ')';
          } else {
            bulkCodes.textContent = '(' + codes.slice(0, 3).join(', ') + '... +' + (codes.length - 3) + ' đơn khác)';
          }
        } else {
          bulkBar.style.display = 'none';
          bulkCodes.textContent = '';
        }

        if (checkAll && rowCheckboxes.length > 0) {
          var allChecked = Array.from(rowCheckboxes).every(function (cb) {
            return cb.checked;
          });
          checkAll.checked = allChecked;
          checkAll.indeterminate = count > 0 && !allChecked;
        }
      }

      if (checkAll) {
        checkAll.addEventListener('change', function () {
          var isChecked = checkAll.checked;
          rowCheckboxes.forEach(function (cb) {
            cb.checked = isChecked;
          });
          updateUI();
        });
      }

      rowCheckboxes.forEach(function (cb) {
        cb.addEventListener('change', updateUI);
      });

      if (btnCancel) {
        btnCancel.addEventListener('click', function () {
          rowCheckboxes.forEach(function (cb) {
            cb.checked = false;
          });
          if (checkAll) {
            checkAll.checked = false;
            checkAll.indeterminate = false;
          }
          updateUI();
        });
      }

      if (btnTrigger) {
        btnTrigger.addEventListener('click', function () {
          var selected = getSelectedCheckboxes();
          if (selected.length === 0) return;

          var ids = selected.map(function (cb) { return cb.value; });
          hiddenIds.value = ids.join(',');
          modalSummaryCount.textContent = selected.length;

          modalSummaryTags.innerHTML = '';
          selected.forEach(function (cb) {
            var code = cb.getAttribute('data-request-code') || ('#' + cb.value);
            var itemCode = cb.getAttribute('data-item-code') || '';
            var facName = cb.getAttribute('data-facility-name') || '';
            var tag = document.createElement('span');
            tag.className = 'bulk-code-pill';
            tag.textContent = code + (itemCode ? ' (' + itemCode + ')' : '');
            if (facName) tag.title = facName;
            modalSummaryTags.appendChild(tag);
          });

          if (noteError) noteError.style.display = 'none';
          openReceptionistBulkModal();
        });
      }

      if (form) {
        form.addEventListener('submit', function (e) {
          var note = noteInput ? noteInput.value.trim() : '';
          if (!note || note.length < 5 || note.length > 500) {
            e.preventDefault();
            if (noteError) {
              noteError.textContent = 'Ghi chú nghiệm thu phải từ 5 đến 500 ký tự.';
              noteError.style.display = 'block';
            }
            if (noteInput) noteInput.focus();
          }
        });
      }

      window.closeReceptionistBulkModal = function () {
        if (modal) {
          modal.classList.remove('show');
        }
      };

      window.openReceptionistBulkModal = function () {
        if (modal) {
          modal.classList.add('show');
          if (noteInput) {
            noteInput.focus();
          }
        }
      };

      window.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && modal && modal.classList.contains('show')) {
          closeReceptionistBulkModal();
        }
      });

      if (modal) {
        modal.addEventListener('click', function (e) {
          if (e.target === modal) {
            closeReceptionistBulkModal();
          }
        });
      }
    })();
  </script>
</body>
</html>
