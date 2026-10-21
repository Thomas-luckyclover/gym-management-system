"use strict";

// 3 tab cột trái (Đang tập / Đã kết thúc / Buổi lẻ) dùng bindTabs() chung của common.js
// (nút [data-tab] ↔ khung [data-tab-panel]); JSP đã mở sẵn tab chứa gói đang xem.

// Đang xem "Tất cả" thì buổi đã qua gập hay mở (khách bấm dòng "▸ N buổi đã qua" để đổi).
let pastCollapsed = true;
let currentFilter = "all";

/**
 * Ẩn/hiện các dòng theo chip lọc + trạng thái gập buổi đã qua.
 * Chỉ dùng statusKey Service đã gửi; "remaining" (Sắp tới) = today/upcoming/warn.
 */
function renderRows() {
    const rows = document.querySelectorAll("[data-session-row]");
    const fold = document.querySelector("[data-past-fold]");
    let visibleCount = 0;
    // B1: dòng khớp chip; riêng chip "Tất cả" thì buổi đã qua theo trạng thái gập.
    for (const row of rows) {
        const status = row.dataset.status;
        const remaining = status === "today" || status === "upcoming" || status === "warn";
        const matches = currentFilter === "all" || currentFilter === status
                || (currentFilter === "remaining" && remaining);
        const folded = currentFilter === "all" && fold && pastCollapsed && row.classList.contains("is-ended");
        row.hidden = !matches || folded;
        if (matches) {
            visibleCount++;
        }
    }
    // B2: dòng gập chỉ có nghĩa khi xem "Tất cả".
    if (fold) {
        fold.hidden = currentFilter !== "all";
        fold.classList.toggle("is-open", !pastCollapsed);
        fold.querySelector("[data-toggle-past]").setAttribute("aria-expanded", String(!pastCollapsed));
        fold.querySelector("[data-past-action]").textContent = pastCollapsed ? "Hiện" : "Thu gọn";
    }
    // B3: thông báo rỗng + dòng kết quả (role=status → trình đọc màn hình đọc lên).
    document.getElementById("session-filter-empty").hidden = visibleCount > 0;
    document.getElementById("booking-result").textContent = currentFilter === "all"
            ? rows.length + " buổi · xếp theo ngày tập"
            : "Đang hiện " + visibleCount + "/" + rows.length + " buổi";
}

/** Bấm chip lọc: đổi bộ lọc, đánh dấu chip đang chọn, đọc bảng từ đầu. */
function filterSessions(event) {
    currentFilter = event.currentTarget.dataset.filter;
    for (const button of document.querySelectorAll("[data-filter]")) {
        button.setAttribute("aria-pressed", String(button.dataset.filter === currentFilter));
    }
    renderRows();
    document.querySelector(".booking-table-scroll").scrollTop = 0;
}

/** Dòng gập: điền tóm tắt "16 đã check-in · 1 vắng · …" từ nhãn trạng thái các buổi đã qua. */
function fillPastSummary(fold) {
    // B1: đếm theo nhãn, giữ thứ tự xuất hiện.
    const counts = new Map();
    for (const row of document.querySelectorAll("[data-session-row].is-ended")) {
        const label = row.dataset.statusLabel;
        counts.set(label, (counts.get(label) || 0) + 1);
    }
    // B2: ghép thành 1 dòng; gán bằng textContent nên an toàn.
    const parts = [];
    for (const [label, count] of counts) {
        parts.push(count + " " + label.toLowerCase());
    }
    fold.querySelector("[data-past-summary]").textContent = parts.join(" · ");
}

/** Nối chip lọc + dòng gập buổi đã qua. */
function initMyBookings() {
    const sessionFilters = document.querySelector("[data-session-filters]");
    if (!sessionFilters) {
        return;
    }
    // B1: chip lọc chỉ hiện khi JS chạy; không có JS vẫn xem đủ bảng.
    sessionFilters.hidden = false;
    for (const button of document.querySelectorAll("[data-filter]")) {
        button.addEventListener("click", filterSessions);
    }
    // B2: gập buổi đã qua → mở trang là thấy ngay buổi sắp tới ở đầu bảng.
    const fold = document.querySelector("[data-past-fold]");
    if (fold) {
        fillPastSummary(fold);
        fold.querySelector("[data-toggle-past]").addEventListener("click", function () {
            pastCollapsed = !pastCollapsed;
            renderRows();
        });
    }
    renderRows();
}

document.addEventListener("DOMContentLoaded", initMyBookings);
