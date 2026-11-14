<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="gms" uri="/WEB-INF/gms-functions.tld" %>

<!DOCTYPE html>
<html lang="vi">
<head>
  <%@ include file="/WEB-INF/jsp/common/head.jspf" %>
  <title>Yêu cầu bảo trì · Super Gym Quản lý</title>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/css/pages/manager/maintenance-requests.css?v=20261114v1">

</head>

<body class="dock theme-manager">
  <%@ include file="/WEB-INF/jsp/common/flash-toast.jspf" %>

  <div class="app">
    <%@ include file="/WEB-INF/jsp/common/layout-manager.jspf" %>

    <div class="main">
      <header class="topbar">
        <div class="tb-left">
          <h1 class="tb-title">Yêu cầu bảo trì</h1>
          <%-- Câu giải thích chuyển vào hint box chung (.pt-info, common.css / common.js) thay cho dòng chữ phụ dưới tiêu đề. --%>
          <span class="pt-info"><button type="button" class="pt-info-trigger" aria-label="Hướng dẫn Yêu cầu bảo trì" aria-describedby="maintenance-help"><span aria-hidden="true">i</span></button><span id="maintenance-help" class="pt-info-popover" role="tooltip">Duyệt hoặc từ chối yêu cầu lễ tân gửi lên; theo dõi đến khi máy sửa xong.</span></span>
        </div>

        <div class="top-right">
          <%@ include file="/WEB-INF/jsp/common/notification-bell.jspf" %>

          <%@ include file="/WEB-INF/jsp/common/topbar-user.jspf" %>
        </div>
      </header>

      <main class="content maintenance-list-content" id="main-content" tabindex="-1">

        <%-- ===== 5 THẺ ĐẾM TRẠNG THÁI ===== --%>
        <div class="maintenance-stats-bar">
          <c:url var="allUrl" value="/manager/maintenance-requests">
            <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            <c:if test="${not empty priorityFilter}"><c:param name="priority" value="${priorityFilter}" /></c:if>
            <c:if test="${not empty reportedDateFilter}"><c:param name="reportedDate" value="${reportedDateFilter}" /></c:if>
          </c:url>
          <a href="${allUrl}" class="stat-card ${empty statusFilter ? 'active' : ''}">
            <span class="stat-name">Tất cả</span>
            <strong class="stat-count"><c:out value="${totalStatusCount}" default="0" /></strong>
          </a>

          <%-- Chờ duyệt (Open) --%>
          <c:url var="openUrl" value="/manager/maintenance-requests">
            <c:param name="status" value="Open" />
            <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            <c:if test="${not empty priorityFilter}"><c:param name="priority" value="${priorityFilter}" /></c:if>
            <c:if test="${not empty reportedDateFilter}"><c:param name="reportedDate" value="${reportedDateFilter}" /></c:if>
          </c:url>
          <a href="${openUrl}" class="stat-card ${statusFilter == 'Open' ? 'active' : ''}">
            <span class="stat-name"><span class="dot dot-open"></span> Chờ duyệt</span>
            <strong class="stat-count"><c:out value="${statusCounts['Open']}" default="0" /></strong>
          </a>

          <%-- Đang xử lý (InProgress) --%>
          <c:url var="progUrl" value="/manager/maintenance-requests">
            <c:param name="status" value="InProgress" />
            <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            <c:if test="${not empty priorityFilter}"><c:param name="priority" value="${priorityFilter}" /></c:if>
            <c:if test="${not empty reportedDateFilter}"><c:param name="reportedDate" value="${reportedDateFilter}" /></c:if>
          </c:url>
          <a href="${progUrl}" class="stat-card ${statusFilter == 'InProgress' ? 'active' : ''}">
            <span class="stat-name"><span class="dot dot-progress"></span> Đang xử lý</span>
            <strong class="stat-count"><c:out value="${statusCounts['InProgress']}" default="0" /></strong>
          </a>

          <%-- Từ chối (Rejected) --%>
          <c:url var="rejUrl" value="/manager/maintenance-requests">
            <c:param name="status" value="Rejected" />
            <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            <c:if test="${not empty priorityFilter}"><c:param name="priority" value="${priorityFilter}" /></c:if>
            <c:if test="${not empty reportedDateFilter}"><c:param name="reportedDate" value="${reportedDateFilter}" /></c:if>
          </c:url>
          <a href="${rejUrl}" class="stat-card ${statusFilter == 'Rejected' ? 'active' : ''}">
            <span class="stat-name"><span class="dot dot-rejected"></span> Từ chối</span>
            <strong class="stat-count"><c:out value="${statusCounts['Rejected']}" default="0" /></strong>
          </a>

          <%-- Đã đóng (Closed) --%>
          <c:url var="closeUrl" value="/manager/maintenance-requests">
            <c:param name="status" value="Closed" />
            <c:if test="${not empty keyword}"><c:param name="keyword" value="${keyword}" /></c:if>
            <c:if test="${not empty priorityFilter}"><c:param name="priority" value="${priorityFilter}" /></c:if>
            <c:if test="${not empty reportedDateFilter}"><c:param name="reportedDate" value="${reportedDateFilter}" /></c:if>
          </c:url>
          <a href="${closeUrl}" class="stat-card ${statusFilter == 'Closed' ? 'active' : ''}">
            <span class="stat-name"><span class="dot dot-closed"></span> Đã đóng</span>
            <strong class="stat-count"><c:out value="${statusCounts['Closed']}" default="0" /></strong>
          </a>
        </div>

        <%-- ===== BẢNG DANH SÁCH & BỘ LỌC ===== --%>
        <section class="maintenance-table-card">
          <div class="card-header-row">
            <h2 class="card-heading">Danh sách yêu cầu</h2>
          </div>

          <%-- Bộ lọc tìm kiếm --%>
          <form method="get" action="${pageContext.request.contextPath}/manager/maintenance-requests" class="filter-controls-row">
            <c:if test="${not empty statusFilter}">
              <input type="hidden" name="status" value="${statusFilter}">
            </c:if>

            <div class="filter-field filter-search">
              <label class="filter-label">Tìm yêu cầu</label>
              <input type="search"
                     name="keyword"
                     maxlength="100"
                     value="<c:out value='${keyword}' />"
                     placeholder="Mã yêu cầu, mã máy hoặc tên thiết bị">
            </div>

            <div class="filter-field filter-priority">
              <label class="filter-label">Mức ưu tiên</label>
              <select name="priority">
                <option value="">Tất cả</option>
                <c:forEach var="p" items="${priorityOptions}">
                  <option value="${p.dbValue}" <c:if test="${priorityFilter == p.dbValue}">selected</c:if>>
                    <c:out value="${p.displayName}" />
                  </option>
                </c:forEach>
              </select>
            </div>

            <div class="filter-field filter-date">
              <label class="filter-label">Ngày báo</label>
              <input type="date" name="reportedDate" value="<c:out value='${reportedDateFilter}' />">
            </div>

            <div class="filter-actions-col">
              <button class="btn btn-primary btn-filter-submit" type="submit">Lọc</button>
              <c:if test="${not empty keyword || not empty statusFilter || not empty priorityFilter || not empty reportedDateFilter}">
                <a class="btn btn-ghost" href="${pageContext.request.contextPath}/manager/maintenance-requests">Xóa lọc</a>
              </c:if>
            </div>
          </form>

          <%-- Bảng dữ liệu --%>
          <%-- Thanh hành động hàng loạt (chỉ hiện khi có chọn checkbox) --%>
          <div id="bulkActionBar" class="bulk-action-bar" style="display: none;">
            <div class="bulk-info">
              <span class="bulk-icon"><i class="fa-solid fa-list-check"></i></span>
              <span class="bulk-text">Đã chọn <strong id="bulkSelectedCount">0</strong> yêu cầu chờ duyệt</span>
              <span class="bulk-codes text-muted" id="bulkCodesPreview"></span>
            </div>
            <div class="bulk-buttons">
              <button type="button" class="btn btn-outline btn-sm" id="btnCancelBulk">Bỏ chọn</button>
              <button type="button" class="btn btn-primary btn-sm" id="btnTriggerBulkApprove">
                <i class="fa-solid fa-clipboard-check"></i> Duyệt hàng loạt (<span id="bulkBtnCount">0</span>)
              </button>
            </div>
          </div>

          <div class="table-responsive-wrapper">
            <table class="maintenance-data-table">
              <thead>
                <tr>
                  <th class="col-check">
                    <input type="checkbox" id="checkAllOpen" class="bulk-checkbox" title="Chọn tất cả đơn chờ duyệt">
                  </th>
                  <th class="col-code">MÃ</th>
                  <th class="col-device">THIẾT BỊ · MÁY</th>
                  <th class="col-incident">SỰ CỐ</th>
                  <th class="col-priority">ƯU TIÊN</th>
                  <th class="col-reporter">NGÀY BÁO · NGƯỜI BÁO</th>
                  <th class="col-status">TRẠNG THÁI</th>
                  <th class="col-actions">THAO TÁC</th>
                </tr>
              </thead>
              <tbody>
                <c:choose>
                  <c:when test="${not empty requestPage.items}">
                    <c:forEach var="item" items="${requestPage.items}">
                      <tr>
                        <%-- Cột Chọn hàng loạt --%>
                        <td class="col-check">
                          <c:choose>
                            <c:when test="${item.request.open}">
                              <input type="checkbox"
                                     class="bulk-checkbox row-select-checkbox"
                                     value="${item.request.maintenanceRequestId}"
                                     data-request-code="<c:out value='${item.request.requestCode}' />"
                                     data-item-code="<c:out value='${item.itemCode}' />">
                            </c:when>
                            <c:otherwise>
                              <span class="checkbox-disabled-mark" title="Chỉ duyệt được đơn chờ duyệt">-</span>
                            </c:otherwise>
                          </c:choose>
                        </td>

                        <%-- Cột MÃ --%>
                        <td class="col-code">
                          <strong class="request-code"><c:out value="${item.request.requestCode}" /></strong>
                        </td>

                        <%-- Cột THIẾT BỊ · MÁY --%>
                        <td class="col-device">
                          <div class="cell-device-info">
                            <strong class="device-name"><c:out value="${item.facilityName}" /></strong>
                            <span class="device-item-code">
                              <c:choose>
                                <c:when test="${not empty item.request.affectedItemsNote}">
                                  <c:out value="${item.request.affectedItemsNote}" /> · <c:out value="${item.itemCode}" />
                                </c:when>
                                <c:otherwise>
                                  <c:out value="${item.itemLabel}" /><c:if test="${not empty item.placementNote}"> · <c:out value="${item.placementNote}" /></c:if> (<c:out value="${item.itemCode}" />)
                                </c:otherwise>
                              </c:choose>
                            </span>
                            <span class="device-location"><c:out value="${item.facilityLocation}" /></span>
                          </div>
                        </td>

                        <%-- Cột SỰ CỐ --%>
                        <td class="col-incident">
                          <div class="cell-incident-info">
                            <strong class="incident-title"><c:out value="${item.request.incidentType.displayName}" /></strong>
                            <div class="incident-desc-wrap">
                              <p class="incident-desc"><c:out value="${item.request.description}" /></p>
                              <button type="button"
                                      class="incident-desc-toggle"
                                      aria-expanded="false"
                                      hidden>... Xem thêm</button>
                            </div>
                            <c:if test="${item.request.warningTagPlaced}">
                              <span class="warning-tag-pill">Đã treo biển ngừng dùng</span>
                            </c:if>
                          </div>
                        </td>

                        <%-- Cột ƯU TIÊN --%>
                        <td class="col-priority">
                          <span class="priority-label priority-${item.request.priority.dbValue.toLowerCase()}">
                            <c:out value="${item.request.priority.displayName}" />
                          </span>
                        </td>

                        <%-- Cột NGÀY BÁO · NGƯỜI BÁO --%>
                        <td class="col-reporter">
                          <div class="cell-reporter-info">
                            <span class="report-date">${gms:formatDate(item.request.reportedAt.toLocalDate())}</span>
                            <span class="reporter-name"><c:out value="${item.reporterName}" /></span>
                          </div>
                        </td>

                        <%-- Cột TRẠNG THÁI --%>
                        <td class="col-status">
                          <c:choose>
                            <c:when test="${item.request.open}">
                              <span class="status-badge badge-open">● Chờ duyệt</span>
                            </c:when>
                            <c:when test="${item.request.inProgress}">
                              <span class="status-badge badge-progress">● Đang xử lý</span>
                            </c:when>
                            <c:when test="${item.request.rejected}">
                              <span class="status-badge badge-rejected">● Từ chối</span>
                            </c:when>
                            <c:when test="${item.request.closed}">
                              <span class="status-badge badge-closed">● Đã đóng</span>
                            </c:when>
                          </c:choose>
                        </td>

                        <%-- Cột THAO TÁC --%>
                        <td class="col-actions">
                          <a class="btn ${item.request.open ? 'btn-primary' : 'btn-outline'} btn-sm"
                             href="${pageContext.request.contextPath}/manager/maintenance-requests/review?id=${item.request.maintenanceRequestId}">
                            ${item.request.open ? 'Xem & duyệt' : 'Xem chi tiết'}
                          </a>
                        </td>
                      </tr>
                    </c:forEach>
                  </c:when>
                  <c:otherwise>
                    <tr>
                      <td colspan="8" class="table-empty-message">
                        Không có yêu cầu bảo trì nào phù hợp với bộ lọc hiện tại.
                      </td>
                    </tr>
                  </c:otherwise>
                </c:choose>
              </tbody>
            </table>
          </div>

          <%-- Phân trang --%>
          <%-- requestPage.totalPages = getter tự tính getTotalPages() · dto/PageResult.java --%>
          <c:if test="${requestPage.totalPages > 1}">
            <div class="table-pagination-row">
              <c:choose>
                <%-- requestPage.hasPrevious = getter tự tính isHasPrevious() · dto/PageResult.java --%>
                <c:when test="${requestPage.hasPrevious}">
                  <c:url var="prevUrl" value="/manager/maintenance-requests">
                    <c:param name="page" value="${requestPage.pageNumber - 1}" />
                    <c:param name="pageSize" value="${requestPage.pageSize}" />
                    <c:param name="keyword" value="${keyword}" />
                    <c:param name="status" value="${statusFilter}" />
                    <c:param name="priority" value="${priorityFilter}" />
                    <c:param name="reportedDate" value="${reportedDateFilter}" />
                  </c:url>
                  <a class="btn btn-outline btn-sm" href="${prevUrl}">‹ Trang trước</a>
                </c:when>
                <c:otherwise>
                  <button type="button" class="btn btn-outline btn-sm" disabled>‹ Trang trước</button>
                </c:otherwise>
              </c:choose>

              <span class="page-counter">Trang ${requestPage.pageNumber} / ${requestPage.totalPages}</span>

              <c:choose>
                <%-- requestPage.hasNext = getter tự tính isHasNext() · dto/PageResult.java --%>
                <c:when test="${requestPage.hasNext}">
                  <c:url var="nextUrl" value="/manager/maintenance-requests">
                    <c:param name="page" value="${requestPage.pageNumber + 1}" />
                    <c:param name="pageSize" value="${requestPage.pageSize}" />
                    <c:param name="keyword" value="${keyword}" />
                    <c:param name="status" value="${statusFilter}" />
                    <c:param name="priority" value="${priorityFilter}" />
                    <c:param name="reportedDate" value="${reportedDateFilter}" />
                  </c:url>
                  <a class="btn btn-outline btn-sm" href="${nextUrl}">Trang sau ›</a>
                </c:when>
                <c:otherwise>
                  <button type="button" class="btn btn-outline btn-sm" disabled>Trang sau ›</button>
                </c:otherwise>
              </c:choose>
            </div>
          </c:if>
        </section>

      </main>
    </div>
  </div>

  <%-- ===== MODAL DUYỆT HÀNG LOẠT ===== --%>
  <div id="modalBulkApprove" class="gms-modal" role="dialog" aria-modal="true" aria-labelledby="modalBulkTitle">
    <div class="gms-modal-dialog">
      <form action="${pageContext.request.contextPath}/manager/maintenance-requests" method="post" id="formBulkApprove">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="batchApprove">
        <input type="hidden" name="requestIds" id="bulkRequestIdsHidden">

        <div class="gms-modal-header">
          <h3 class="gms-modal-title" id="modalBulkTitle">Phê duyệt hàng loạt yêu cầu bảo trì</h3>
          <button type="button" class="gms-modal-close" onclick="closeBulkModal()">&times;</button>
        </div>
        <div class="gms-modal-body">
          <div class="bulk-modal-summary">
            Xác nhận phê duyệt đồng loạt <strong id="modalSummaryCount">0</strong> yêu cầu bảo trì đã chọn:
            <div class="bulk-modal-tags" id="modalSummaryTags"></div>
          </div>

          <label class="modal-checkbox-row" style="margin-top: 14px;">
            <input type="checkbox" name="setUnderMaintenance" value="true" checked>
            <span>Chuyển tất cả thiết bị liên quan sang <b>Đang bảo trì</b> (khách không thể sử dụng)</span>
          </label>

          <div class="field" style="margin-top: var(--space-3);">
            <label class="field-label" for="bulkManagerNoteInput">Ghi chú chỉ đạo chung cho lễ tân / kỹ thuật (không bắt buộc)</label>
            <textarea id="bulkManagerNoteInput"
                      name="managerNote"
                      class="form-input"
                      rows="3"
                      maxlength="500"
                      placeholder="VD: Duyệt bảo trì dàn máy; kiểm tra vệ sinh và bôi trơn định kỳ..."></textarea>
            <small class="field-hint">Ghi chú này sẽ được lưu chung vào tất cả các phiếu được duyệt.</small>
          </div>
        </div>
        <div class="gms-modal-footer">
          <button type="button" class="btn btn-ghost" onclick="closeBulkModal()">Hủy</button>
          <button type="submit" class="btn btn-primary" id="btnSubmitBulkApprove">
            <i class="fa-solid fa-check-double"></i> Xác nhận duyệt tất cả
          </button>
        </div>
      </form>
    </div>
  </div>

  <script>
    (function () {
      var checkAll = document.getElementById('checkAllOpen');
      var rowCheckboxes = document.querySelectorAll('.row-select-checkbox');
      var bulkActionBar = document.getElementById('bulkActionBar');
      var bulkSelectedCount = document.getElementById('bulkSelectedCount');
      var bulkBtnCount = document.getElementById('bulkBtnCount');
      var bulkCodesPreview = document.getElementById('bulkCodesPreview');
      var btnCancelBulk = document.getElementById('btnCancelBulk');
      var btnTriggerBulkApprove = document.getElementById('btnTriggerBulkApprove');

      var modalBulk = document.getElementById('modalBulkApprove');
      var bulkRequestIdsHidden = document.getElementById('bulkRequestIdsHidden');
      var modalSummaryCount = document.getElementById('modalSummaryCount');
      var modalSummaryTags = document.getElementById('modalSummaryTags');

      function getSelectedCheckboxes() {
        return Array.from(rowCheckboxes).filter(function (cb) {
          return cb.checked;
        });
      }

      function updateBulkUI() {
        var selected = getSelectedCheckboxes();
        var count = selected.length;

        if (count > 0) {
          bulkActionBar.style.display = 'flex';
          bulkSelectedCount.textContent = count;
          bulkBtnCount.textContent = count;

          var codes = selected.map(function (cb) {
            return cb.getAttribute('data-request-code') || ('#' + cb.value);
          });

          if (codes.length <= 4) {
            bulkCodesPreview.textContent = '(' + codes.join(', ') + ')';
          } else {
            bulkCodesPreview.textContent = '(' + codes.slice(0, 3).join(', ') + '... +' + (codes.length - 3) + ' đơn khác)';
          }
        } else {
          bulkActionBar.style.display = 'none';
          bulkCodesPreview.textContent = '';
        }

        // Sync checkAll state
        if (rowCheckboxes.length > 0) {
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
          updateBulkUI();
        });
      }

      rowCheckboxes.forEach(function (cb) {
        cb.addEventListener('change', updateBulkUI);
      });

      if (btnCancelBulk) {
        btnCancelBulk.addEventListener('click', function () {
          rowCheckboxes.forEach(function (cb) {
            cb.checked = false;
          });
          if (checkAll) {
            checkAll.checked = false;
            checkAll.indeterminate = false;
          }
          updateBulkUI();
        });
      }

      if (btnTriggerBulkApprove) {
        btnTriggerBulkApprove.addEventListener('click', function () {
          var selected = getSelectedCheckboxes();
          if (selected.length === 0) return;

          var ids = selected.map(function (cb) { return cb.value; });
          bulkRequestIdsHidden.value = ids.join(',');
          modalSummaryCount.textContent = selected.length;

          modalSummaryTags.innerHTML = '';
          selected.forEach(function (cb) {
            var code = cb.getAttribute('data-request-code') || ('#' + cb.value);
            var itemCode = cb.getAttribute('data-item-code') || '';
            var tag = document.createElement('span');
            tag.className = 'bulk-code-pill';
            tag.textContent = code + (itemCode ? ' (' + itemCode + ')' : '');
            modalSummaryTags.appendChild(tag);
          });

          openBulkModal();
        });
      }

      window.closeBulkModal = function () {
        if (modalBulk) {
          modalBulk.classList.remove('show');
        }
      };

      window.openBulkModal = function () {
        if (modalBulk) {
          modalBulk.classList.add('show');
          var noteInput = document.getElementById('bulkManagerNoteInput');
          if (noteInput) {
            noteInput.focus();
          }
        }
      };

      window.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && modalBulk && modalBulk.classList.contains('show')) {
          closeBulkModal();
        }
      });

      if (modalBulk) {
        modalBulk.addEventListener('click', function (e) {
          if (e.target === modalBulk) {
            closeBulkModal();
          }
        });
      }

      // Rút gọn mô tả sự cố dài quá 2 dòng và hiển thị nút "... Xem thêm" để mở toàn bộ nội dung
      var descWraps = document.querySelectorAll('.incident-desc-wrap');

      function refreshIncidentDescTruncations() {
        descWraps.forEach(function (wrap) {
          var desc = wrap.querySelector('.incident-desc');
          var toggleBtn = wrap.querySelector('.incident-desc-toggle');
          if (!desc || !toggleBtn) return;

          var wasExpanded = desc.classList.contains('is-expanded');
          if (wasExpanded) {
            desc.classList.remove('is-expanded');
          }

          var isOverflowing = desc.scrollHeight > desc.clientHeight + 1;

          if (wasExpanded && isOverflowing) {
            desc.classList.add('is-expanded');
          }

          if (isOverflowing) {
            desc.classList.add('is-expandable');
            toggleBtn.hidden = false;
            var isNowExpanded = desc.classList.contains('is-expanded');
            toggleBtn.setAttribute('aria-expanded', isNowExpanded ? 'true' : 'false');
            toggleBtn.textContent = isNowExpanded ? 'Thu gọn' : '... Xem thêm';
          } else {
            desc.classList.remove('is-expandable', 'is-expanded');
            toggleBtn.hidden = true;
            toggleBtn.setAttribute('aria-expanded', 'false');
          }
        });
      }

      function toggleIncidentDesc(wrap) {
        var desc = wrap.querySelector('.incident-desc');
        var toggleBtn = wrap.querySelector('.incident-desc-toggle');
        if (!desc || !toggleBtn || toggleBtn.hidden) return;

        var expanded = desc.classList.toggle('is-expanded');
        toggleBtn.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        toggleBtn.textContent = expanded ? 'Thu gọn' : '... Xem thêm';
      }

      descWraps.forEach(function (wrap) {
        var desc = wrap.querySelector('.incident-desc');
        var toggleBtn = wrap.querySelector('.incident-desc-toggle');

        if (toggleBtn) {
          toggleBtn.addEventListener('click', function () {
            toggleIncidentDesc(wrap);
          });
        }

        if (desc) {
          desc.addEventListener('click', function () {
            if (!desc.classList.contains('is-expandable')) return;
            var selection = window.getSelection ? window.getSelection().toString() : '';
            if (selection) return;
            toggleIncidentDesc(wrap);
          });
        }
      });

      refreshIncidentDescTruncations();
      window.addEventListener('load', refreshIncidentDescTruncations);
      window.addEventListener('resize', refreshIncidentDescTruncations);
    })();
  </script>
</body>
</html>