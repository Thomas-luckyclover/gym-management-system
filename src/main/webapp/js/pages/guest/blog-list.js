/**
 * =====================================================================
 * blog-list.js — Script điều khiển tương tác màn hình Blog List (SRS #6 / UC-06)
 *
 * Nhiệm vụ:
 * 1. Bắt sự kiện tìm kiếm ở ô #blogSearch (hỗ trợ phím Enter và debounce khi gõ).
 * 2. Lọc theo chuyên mục khi bấm chip trong #chipFilter (Tất cả, Tập luyện, Dinh dưỡng, Tin tức).
 * 3. Chuyển trang khi bấm các nút phân trang trong #blogPagination.
 * 4. Tự động reset về trang 1 khi thay đổi từ khóa hoặc chuyên mục (Rule 2.7 / GBR-18).
 * 5. Tự cuộn mượt lên đầu trang (.page-head) khi đổi trang hoặc bộ lọc.
 * =====================================================================
 */
(function () {
  'use strict';

  // Thời gian chờ gõ phím trước khi tự động tìm kiếm (ms)
  const SEARCH_DEBOUNCE_MS = 500;

  // Lấy các phần tử DOM chính trên màn hình
  const searchInput = document.getElementById('blogSearch');
  const searchForm = document.getElementById('blogSearchForm');
  const chipContainer = document.getElementById('chipFilter');
  const paginationContainer = document.getElementById('blogPagination');
  const pageHead = document.querySelector('.page-head');

  /**
   * Cập nhật tham số trên URL và tải lại dữ liệu trang từ Server.
   * Ưu tiên dùng hàm changeUrlParams() có sẵn của common.js; nếu chưa nạp kịp thì dùng fallback.
   *
   * @param {Object} params Đối tượng chứa cặp key-value cần đổi (VD: { q: 'squat', page: 1 })
   */
  function navigateWithParams(params) {
    if (typeof changeUrlParams === 'function') {
      changeUrlParams(params);
      return;
    }

    const url = new URL(window.location.href);
    for (const key of Object.keys(params)) {
      const val = params[key];
      if (val === null || val === undefined || val === '') {
        url.searchParams.delete(key);
      } else {
        url.searchParams.set(key, val);
      }
    }
    window.location.assign(url.toString());
  }

  /**
   * Thiết lập sự kiện cho ô tìm kiếm #blogSearch:
   * - Nhấn Enter trong form: tìm ngay lập tức, về trang 1.
   * - Gõ phím: chờ sau SEARCH_DEBOUNCE_MS không gõ tiếp thì tự tìm kiếm, về trang 1.
   */
  function initSearch() {
    if (!searchInput) return;

    let debounceTimer = null;
    let initialValue = searchInput.value;

    // Đặt con trỏ về cuối input nếu đã có sẵn từ khóa tìm kiếm
    if (searchInput.value) {
      const len = searchInput.value.length;
      try {
        searchInput.setSelectionRange(len, len);
      } catch (e) {
        // Một số trình duyệt cũ có thể bỏ qua
      }
    }

    // Sự kiện submit form khi nhấn Enter
    if (searchForm) {
      searchForm.addEventListener('submit', function (event) {
        event.preventDefault();
        clearTimeout(debounceTimer);
        const query = searchInput.value.trim();
        navigateWithParams({ q: query || null, page: 1 });
      });
    }

    // Sự kiện gõ phím với debounce
    searchInput.addEventListener('input', function () {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(function () {
        const query = searchInput.value.trim();
        // Chỉ gửi request nếu giá trị thực sự thay đổi so với giá trị ban đầu lúc tải trang
        if (query !== initialValue.trim()) {
          navigateWithParams({ q: query || null, page: 1 });
        }
      }, SEARCH_DEBOUNCE_MS);
    });
  }

  /**
   * Thiết lập sự kiện cho các nút chip danh mục #chipFilter:
   * Bấm vào nút nào -> lọc theo danh mục đó, luôn đặt lại page = 1.
   */
  function initChipFilter() {
    if (!chipContainer) return;

    const chipButtons = chipContainer.querySelectorAll('button[data-category]');
    chipButtons.forEach(function (button) {
      button.addEventListener('click', function () {
        const selectedCat = button.dataset.category;
        const normalizedParam = (selectedCat === 'Tất cả' || !selectedCat) ? null : selectedCat;
        navigateWithParams({ category: normalizedParam, page: 1 });
      });
    });
  }

  /**
   * Thiết lập sự kiện cho các nút phân trang #blogPagination:
   * Bấm vào trang nào -> chuyển đến trang đó, giữ nguyên bộ lọc q & category.
   */
  function initPagination() {
    if (!paginationContainer) return;

    const pageButtons = paginationContainer.querySelectorAll('.page-btn[data-page]');
    pageButtons.forEach(function (button) {
      button.addEventListener('click', function () {
        if (button.disabled || button.classList.contains('active')) {
          return;
        }

        const targetPage = parseInt(button.dataset.page, 10);
        if (targetPage && targetPage >= 1) {
          navigateWithParams({ page: targetPage });
          if (pageHead) {
            pageHead.scrollIntoView({ behavior: 'smooth' });
          }
        }
      });
    });
  }

  // Khởi động toàn bộ sự kiện khi DOM đã sẵn sàng
  document.addEventListener('DOMContentLoaded', function () {
    initSearch();
    initChipFilter();
    initPagination();
  });
})();
