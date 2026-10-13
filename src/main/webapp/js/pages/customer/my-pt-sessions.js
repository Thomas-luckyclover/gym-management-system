// Lịch PT: đổi vùng xem ở client; lọc và phân trang vẫn gửi GET tới Servlet.
// JSP nối bằng [data-session-view], [data-session-panel], #sessionFilterForm.
document.addEventListener("DOMContentLoaded", initializeSessionViews);

function initializeSessionViews() {
    const page = document.querySelector(".my-pt-sessions");
    if (!page) {
        return;
    }
    const navigation = page.querySelector(".my-pt-view-tabs");
    const buttons = page.querySelectorAll("[data-session-view]");
    const panels = page.querySelectorAll("[data-session-panel]");
    const filters = page.querySelector(".my-pt-filter-panel");
    const form = document.getElementById("sessionFilterForm");
    const viewInput = form.querySelector('input[name="view"]');
    const clearFilters = form.querySelector("[data-clear-session-filters]");

    // B1: chuyển vùng đọc, giữ URL để tải lại vẫn mở đúng vùng đang xem.
    function selectView(view, updateUrl) {
        for (let index = 0; index < buttons.length; index++) {
            const button = buttons[index];
            const selected = button.dataset.sessionView === view;
            button.setAttribute("aria-selected", String(selected));
            button.tabIndex = selected ? 0 : -1;
        }
        for (let index = 0; index < panels.length; index++) {
            const panel = panels[index];
            panel.hidden = panel.dataset.sessionPanel !== view;
        }
        filters.hidden = view === "packages";
        viewInput.value = view;
        const clearUrl = new URL(clearFilters.href);
        clearUrl.search = "";
        clearUrl.searchParams.set("view", view);
        clearFilters.href = clearUrl;
        if (updateUrl) {
            const url = new URL(window.location.href);
            url.searchParams.set("view", view);
            url.hash = "";
            window.history.replaceState(null, "", url);
        }
    }

    // B2: tạo tab có phím mũi tên/Home/End; không dùng JS cho lọc dữ liệu.
    navigation.setAttribute("role", "tablist");
    for (let index = 0; index < panels.length; index++) {
        const panel = panels[index];
        panel.setAttribute("role", "tabpanel");
        panel.setAttribute("aria-labelledby", "session-tab-" + panel.dataset.sessionPanel);
        panel.tabIndex = 0;
    }
    for (let index = 0; index < buttons.length; index++) {
        const button = buttons[index];
        button.setAttribute("role", "tab");
        button.addEventListener("click", function () {
            selectView(button.dataset.sessionView, true);
        });
        button.addEventListener("keydown", function (event) {
            let nextIndex = index;
            if (event.key === "ArrowRight") {
                nextIndex = (index + 1) % buttons.length;
            } else if (event.key === "ArrowLeft") {
                nextIndex = (index + buttons.length - 1) % buttons.length;
            } else if (event.key === "Home") {
                nextIndex = 0;
            } else if (event.key === "End") {
                nextIndex = buttons.length - 1;
            } else {
                return;
            }
            event.preventDefault();
            const nextButton = buttons[nextIndex];
            selectView(nextButton.dataset.sessionView, true);
            nextButton.focus();
        });
    }
    let initialView = page.dataset.activeView;
    if (window.location.hash === "#history-title") {
        initialView = "history";
    } else if (window.location.hash === "#pending-title") {
        initialView = "upcoming";
    }
    selectView(initialView, false);
    navigation.hidden = false;

    // B3: phản hồi khi rời trang để lọc; không thay thế lỗi kiểm tra từ server.
    form.addEventListener("submit", function () {
        const submitButton = form.querySelector('button[type="submit"]');
        form.setAttribute("aria-busy", "true");
        submitButton.disabled = true;
        submitButton.textContent = "Đang lọc…";
    });
    window.addEventListener("pageshow", function () {
        const submitButton = form.querySelector('button[type="submit"]');
        form.removeAttribute("aria-busy");
        submitButton.disabled = false;
        submitButton.textContent = "Lọc lịch tập";
    });

    // B4: Escape đóng phần giải thích; trả focus về nút mở disclosure.
    page.addEventListener("keydown", function (event) {
        if (event.key !== "Escape") {
            return;
        }
        const openHelp = page.querySelectorAll(".my-pt-info[open]");
        for (let index = 0; index < openHelp.length; index++) {
            const details = openHelp[index];
            details.open = false;
            details.querySelector("summary").focus();
        }
    });
}
