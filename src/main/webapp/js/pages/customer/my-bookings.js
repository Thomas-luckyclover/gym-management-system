"use strict";

// 3 tab cột trái (Đang tập / Đã kết thúc / Buổi lẻ) dùng bindTabs() chung của common.js
// (nút [data-tab] ↔ khung [data-tab-panel]); JSP đã mở sẵn tab chứa gói đang xem.

/** Lọc bảng buổi bằng statusKey của Service; remaining ("Sắp tới") gồm today/upcoming/warn. */
function filterSessions(event) {
    const filter = event.currentTarget.dataset.filter;
    const rows = document.querySelectorAll("[data-session-row]");
    let visibleCount = 0;
    // B1: không tính trạng thái lại trong JS; dùng đúng mã Service đã gửi.
    for (const row of rows) {
        const status = row.dataset.status;
        const remaining = status === "today" || status === "upcoming" || status === "warn";
        row.hidden = !(filter === "all" || filter === status || (filter === "remaining" && remaining));
        if (!row.hidden) {
            visibleCount++;
        }
    }
    // B2: cập nhật chip đang chọn và dòng kết quả (role=status → trình đọc màn hình đọc lên).
    const buttons = document.querySelectorAll("[data-filter]");
    for (const button of buttons) {
        button.setAttribute("aria-pressed", String(button.dataset.filter === filter));
    }
    document.getElementById("session-filter-empty").hidden = visibleCount > 0;
    document.getElementById("booking-result").textContent = filter === "all"
            ? rows.length + " buổi · xếp theo ngày tập"
            : "Đang hiện " + visibleCount + "/" + rows.length + " buổi";
    // B3: đổi bộ lọc thì đọc từ đầu bảng.
    document.querySelector(".booking-table-scroll").scrollTop = 0;
}

/** Nối chip lọc, rồi cuộn bảng tới buổi sắp tới. */
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
    // B2: cuộn trong khung bảng (không cuộn cả trang) tới dòng "Buổi tới".
    const table = document.getElementById("booking-sessions");
    const row = table.querySelector('[data-session-id="' + table.dataset.focusSession + '"]');
    if (row) {
        const container = document.querySelector(".booking-table-scroll");
        const header = table.querySelector("thead");
        container.scrollTop = row.offsetTop - header.offsetHeight;
    }
}

document.addEventListener("DOMContentLoaded", initMyBookings);
