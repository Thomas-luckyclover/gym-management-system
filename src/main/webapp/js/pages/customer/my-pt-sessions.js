
/*
 * Mục đích JS:
 * Chuyển giữa 3 tab mà không tải lại trang
 * Chống bấm lọc 2 lần
 *
 */
// // Lịch PT: đổi vùng xem ở client; lọc và phân trang vẫn gửi GET tới Servlet.
// JSP nối bằng [data-session-view], [data-session-panel], #sessionFilterForm.
document.addEventListener("DOMContentLoaded", initializeSessionViews); // Nghĩa là: “khi trình duyệt dựng xong HTML, hãy chạy hàm initializeSessionViews”. Vì vậy JSP không cần viết initializeSessionViews() ở đâu cả.
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

    // B1: Hàm chuyển tab, Tab được chọn thì aria-selected = true, các tab khác false.
    //  chuyển vùng đọc, giữ URL để tải lại vẫn mở đúng vùng đang xem.
    function selectView(view, updateUrl) {
        for (let index = 0; index < buttons.length; index++) {
            const button = buttons[index];
            const selected = button.dataset.sessionView === view;
            button.setAttribute("aria-selected", String(selected));
            button.tabIndex = selected ? 0 : -1;
        }
        for (let index = 0; index < panels.length; index++) {
            const panel = panels[index];
            panel.hidden = panel.dataset.sessionPanel !== view; // Panel nào không khớp tên thì ẩn (panel.hidden = true)
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
            window.history.replaceState(null, "", url); // cập nhật view=?
        }
    }

    // B2: Gắn sự kiện cho tab + giúp tab dễ đọc
    // tạo tab có phím mũi tên/Home/End; không dùng JS cho lọc dữ liệu.
    // Thêm các thuộc tính role="tab", role="tabpanel" cho người dùng đọc màn hình (accessibility).
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

    // B3:
    // phản hồi khi rời trang để lọc; không thay thế lỗi kiểm tra từ server.
    // pageShow: khi khách bấm Back và trình duyệt khôi phục trang cũ, nút được trả về “Lọc lịch tập” và bấm lại được, tránh bị kẹt ở “Đang lọc…”.
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
    // Đóng phần giải thích i
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

    // B5: Chỉ gửi yêu cầu khi khách xác nhận hủy; đóng hộp thoại không gửi form.
    const cancelDialog = document.createElement("dialog");
    cancelDialog.className = "my-pt-cancel-dialog";
    cancelDialog.setAttribute("aria-labelledby", "cancelDialogTitle");
    cancelDialog.setAttribute("aria-describedby", "cancelDialogDesc");
    cancelDialog.innerHTML =
        '<div class="my-pt-cancel-dialog-content">' +
        '<h2 id="cancelDialogTitle">Hủy buổi tập này?</h2>' +
        '<p id="cancelDialogDesc" class="my-pt-cancel-dialog-intro">Buổi tập sẽ chuyển sang Lịch sử sau khi hủy.</p>' +
        '<p class="my-pt-cancel-dialog-warning"><strong>Vẫn trừ một buổi trong gói.</strong> Không hoàn tiền và không thể hoàn tác.</p>' +
        '<div class="my-pt-dialog-actions">' +
        '<button type="button" data-cancel-choice="keep" class="btn">Giữ buổi</button>' +
        '<button type="button" data-cancel-choice="confirm" class="btn">Xác nhận hủy</button>' +
        '</div></div>';
    document.body.appendChild(cancelDialog);

    let pendingSessionId = null;
    let cancelOpener = null;

    page.addEventListener("click", function (event) {
        const button = event.target.closest("[data-cancel-session-id]");
        if (!button) {
            return;
        }
        pendingSessionId = button.dataset.cancelSessionId; // Quan Trọng: lấy ID buổi
        cancelOpener = button;
        cancelDialog.returnValue = "";                      // xóa kết quả của lần mở trước
        cancelDialog.showModal();
        // Giữ buổi là lựa chọn an toàn khi dùng bàn phím.
        setTimeout(() => {
            cancelDialog.querySelector('[data-cancel-choice="keep"]')?.focus();
        }, 0);
    });

    cancelDialog.addEventListener("click", function (event) {
        const choice = event.target.closest("[data-cancel-choice]");
        if (choice && cancelDialog.contains(choice)) {
            cancelDialog.close(choice.dataset.cancelChoice);
        }
    });

    cancelDialog.addEventListener("close", function () {
        if (cancelDialog.returnValue === "confirm" && pendingSessionId) {
            // Gửi POST để hủy buổi
            submitCancelRequest(pendingSessionId);
        }
        pendingSessionId = null;
        cancelOpener?.focus(); // trả focus về nút Hủy, giống B4
    });

    // B6: Gửi POST hủy buổi tập lên Servlet
    function submitCancelRequest(sessionId) {
        const form = document.createElement("form");
        form.method = "POST";
        form.action = window.location.pathname; // POST về chính trang hiện tại

        const actionInput = document.createElement("input");
        actionInput.type = "hidden";
        actionInput.name = "action";
        actionInput.value = "cancel";
        form.appendChild(actionInput);

        const sessionIdInput = document.createElement("input");
        sessionIdInput.type = "hidden";
        sessionIdInput.name = "sessionId";
        sessionIdInput.value = sessionId;
        form.appendChild(sessionIdInput);

        document.body.appendChild(form);
        form.submit();
    }
}
