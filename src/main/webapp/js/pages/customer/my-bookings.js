"use strict";

/** Lọc thẻ gói theo hai dropdown; giữ nguyên dữ liệu gói đang chọn bên phải. */
function filterPackages() {
    const state = document.getElementById("package-state").value;
    const type = document.getElementById("package-type").value;
    const packages = document.querySelectorAll("[data-package-item]");
    let visibleCount = 0;
    // B1: hai điều kiện kết hợp AND.
    for (const item of packages) {
        const matchesState = state === "all" || item.dataset.packageState === state;
        const matchesType = type === "all" || item.dataset.packageType === type;
        item.hidden = !(matchesState && matchesType);
        if (!item.hidden) {
            visibleCount++;
        }
    }
    // B2: ẩn tiêu đề nhóm không có thẻ phù hợp và hiện thông báo rỗng.
    const groups = document.querySelectorAll("[data-package-group]");
    for (const group of groups) {
        group.hidden = !group.querySelector("[data-package-item]:not([hidden])");
    }
    document.getElementById("package-filter-empty").hidden = visibleCount > 0;
}

/** Lọc bảng buổi bằng statusKey của Service; remaining gồm today/upcoming/warn. */
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
    // B2: cập nhật trạng thái chọn và số buổi đang hiển thị cho trình đọc màn hình.
    const buttons = document.querySelectorAll("[data-filter]");
    for (const button of buttons) {
        button.setAttribute("aria-pressed", String(button.dataset.filter === filter));
    }
    document.getElementById("session-filter-empty").hidden = visibleCount > 0;
    document.getElementById("booking-result").textContent = "Đang hiển thị " + visibleCount + " buổi";
    // B3: đổi bộ lọc thì đọc từ đầu bảng, không giữ vị trí cuộn cũ.
    document.querySelector(".booking-table-scroll").scrollTop = 0;
}

/** Nối bộ lọc và chỉ cuộn vùng bảng đến buổi đầu tiên chưa kết thúc. */
function initMyBookings() {
    const packageFilters = document.querySelector("[data-package-filters]");
    const sessionFilters = document.querySelector("[data-session-filters]");
    if (!packageFilters || !sessionFilters) {
        return;
    }
    // B1: chỉ hiện điều khiển khi JS đã sẵn sàng; không có JS vẫn đọc toàn bộ bảng.
    packageFilters.hidden = false;
    sessionFilters.hidden = false;
    document.getElementById("package-state").addEventListener("change", filterPackages);
    document.getElementById("package-type").addEventListener("change", filterPackages);
    filterPackages();
    const buttons = document.querySelectorAll("[data-filter]");
    for (const button of buttons) {
        button.addEventListener("click", filterSessions);
    }
    // B2: cuộn bảng, giữ nguyên đầu trang và sidebar.
    const table = document.getElementById("booking-sessions");
    const focusId = table.dataset.focusSession;
    const rows = table.querySelectorAll("[data-session-row]");
    for (const row of rows) {
        if (row.dataset.sessionId === focusId) {
            const container = document.querySelector(".booking-table-scroll");
            const header = table.querySelector("thead");
            container.scrollTop = row.getBoundingClientRect().top - container.getBoundingClientRect().top + container.scrollTop - header.offsetHeight;
            break;
        }
    }
}

document.addEventListener("DOMContentLoaded", initMyBookings);
