/*
 * my-pt-sessions.js — JS của màn "Lịch tập của tôi". Chỉ làm 1 việc:
 *   [TAB]  chuyển 3 tab (Sắp tới / Lịch sử / Gói PT) mà KHÔNG tải lại trang
 *
 * [CHOT 19/10] Màn chỉ xem: đã bỏ hộp thoại Hủy buổi / Đổi buổi.
 * Phân trang là link GET bình thường, không dùng JS.
 */
document.addEventListener("DOMContentLoaded", function () {
    if (!document.querySelector(".my-pt-sessions")) {
        return;
    }
    setupTabs();
});


// =====================================================================
// [TAB]
// =====================================================================

function setupTabs() {
    const page = document.querySelector(".my-pt-sessions");
    const tabBar = document.querySelector(".my-pt-view-tabs");

    // B1: mở tab ban đầu (JSP tính sẵn ở data-active-view; link phân trang có #... thì ưu tiên)
    let view = page.dataset.activeView;
    if (location.hash === "#history-title") {
        view = "history";
    } else if (location.hash === "#pending-title") {
        view = "upcoming";
    }
    showTab(view);
    tabBar.hidden = false;

    // B2: bấm nút tab → đổi tab + ghi ?view=... lên URL (F5 vẫn mở đúng tab)
    document.querySelectorAll("[data-session-view]").forEach(function (button) {
        button.addEventListener("click", function () {
            const newView = button.dataset.sessionView;
            showTab(newView);

            const url = new URL(location.href);
            url.searchParams.set("view", newView);
            url.hash = "";
            history.replaceState(null, "", url);
        });
    });
}

/** [TAB] Hiện đúng 1 khối có data-session-panel = view, ẩn các khối còn lại. */
function showTab(view) {
    // Nút tab: nút đang chọn có aria-selected="true" (CSS tô đậm)
    document.querySelectorAll("[data-session-view]").forEach(function (button) {
        button.setAttribute("aria-selected", String(button.dataset.sessionView === view));
    });

    // Khối nội dung
    document.querySelectorAll("[data-session-panel]").forEach(function (panel) {
        panel.hidden = panel.dataset.sessionPanel !== view;
    });
}
