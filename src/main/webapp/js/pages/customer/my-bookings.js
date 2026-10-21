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
    // B3: vạch "Hôm nay" chỉ có nghĩa khi xem tất cả; đổi bộ lọc thì đọc từ đầu bảng.
    const divider = document.querySelector("[data-divider]");
    if (divider) {
        divider.hidden = filter !== "all";
    }
    document.querySelector(".booking-table-scroll").scrollTop = 0;
    updateJumpButton();
}

/**
 * Nút "↑ Xem N buổi đã qua": hiện khi có buổi bị khuất phía trên khung bảng
 * (VD lúc mở trang, bảng tự cuộn tới buổi sắp tới nên các buổi đã qua nằm khuất).
 */
function updateJumpButton() {
    const button = document.querySelector("[data-jump-past]");
    const container = document.querySelector(".booking-table-scroll");
    const header = document.querySelector("#booking-sessions thead");
    // B1: đếm dòng đang hiện mà nằm trên mép trên vùng nhìn thấy.
    const visibleTop = container.scrollTop + header.offsetHeight;
    let hiddenAbove = 0;
    for (const row of document.querySelectorAll("[data-session-row]:not([hidden])")) {
        if (row.offsetTop + row.offsetHeight <= visibleTop) {
            hiddenAbove++;
        }
    }
    // B2: có dòng khuất → hiện nút kèm số; không có → ẩn.
    button.hidden = hiddenAbove === 0;
    button.textContent = "↑ Xem " + hiddenAbove + " buổi phía trên";
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
    // B2: cuộn trong khung bảng (không cuộn cả trang) tới vạch "Hôm nay" (nếu không có thì tới dòng "Buổi tới").
    const table = document.getElementById("booking-sessions");
    const container = document.querySelector(".booking-table-scroll");
    const target = table.querySelector("[data-divider]")
            || table.querySelector('[data-session-id="' + table.dataset.focusSession + '"]');
    if (target) {
        container.scrollTop = target.offsetTop - table.querySelector("thead").offsetHeight;
    }
    // B3: nút "↑ Xem N buổi phía trên" — cập nhật khi cuộn, bấm thì cuộn mượt lên đầu.
    container.addEventListener("scroll", updateJumpButton, { passive: true });
    document.querySelector("[data-jump-past]").addEventListener("click", function () {
        container.scrollTo({ top: 0, behavior: "smooth" });
    });
    updateJumpButton();
}

document.addEventListener("DOMContentLoaded", initMyBookings);
