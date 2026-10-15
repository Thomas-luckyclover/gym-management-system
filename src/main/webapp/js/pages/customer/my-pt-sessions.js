
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
        setButtonLoading(submitButton, true);
    });
    window.addEventListener("pageshow", function () {
        const submitButton = form.querySelector('button[type="submit"]');
        form.removeAttribute("aria-busy");
        setButtonLoading(submitButton, false);
    });

    // B4: HintBox dùng chung common.js (chạm, hover/focus và Escape).

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
    initializeSwapDialog(page);

    let pendingSessionId = null;
    let cancelOpener = null;

    page.addEventListener("click", function (event) {
        const button = event.target.closest("[data-cancel-session-id]");
        if (!button) {
            return;
        }
        pendingSessionId = button.dataset.cancelSessionId; // Quan Trọng: lấy ID buổi
        cancelOpener = button;
        // Hiện đúng ngày, giờ và PT của hàng vừa chọn; không đổi dữ liệu gửi POST.
        const session = button.closest(".my-pt-session");
        const day = button.closest(".my-pt-day");
        const sessionDetails = day.querySelector(".my-pt-day-label").textContent.trim()
                + " · " + session.querySelector(".my-pt-session-hours").textContent.trim()
                + " · " + session.querySelector("h3").textContent.trim();
        cancelDialog.querySelector("#cancelDialogDesc").textContent = sessionDetails;
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

// UC-38: chỉ tải lựa chọn bằng AJAX; xác nhận gửi POST để refresh lịch và quota cùng nhau.
function initializeSwapDialog(page) {
    const dialog = document.createElement("dialog");
    dialog.className = "my-pt-cancel-dialog my-pt-swap-dialog";
    dialog.setAttribute("aria-labelledby", "swapDialogTitle");
    dialog.innerHTML = '<div class="my-pt-cancel-dialog-content">'
            + '<h2 id="swapDialogTitle">Đổi buổi tập</h2>'
            + '<p data-swap-session></p>'
            + '<p class="my-pt-swap-policy">Chọn slot thứ 7 hoặc Chủ nhật của cùng PT, trong hạn gói. Tuần sau mở từ thứ 7. Mỗi lần xác nhận sử dụng một lượt đổi.</p>'
            + '<p data-swap-status role="status" aria-live="polite"></p>'
            + '<label class="my-pt-swap-slot-label" hidden>Khung giờ mới<select data-swap-slots><option value="">Chọn khung giờ</option></select></label>'
            + '<div class="my-pt-dialog-actions">'
            + '<button type="button" class="btn btn-outline" data-swap-close>Giữ lịch cũ</button>'
            + '<button type="button" class="btn btn-primary" data-swap-confirm disabled>Xác nhận đổi</button>'
            + '</div></div>';
    document.body.appendChild(dialog);
    const select = dialog.querySelector("[data-swap-slots]");
    const label = dialog.querySelector("label");
    const status = dialog.querySelector("[data-swap-status]");
    const confirm = dialog.querySelector("[data-swap-confirm]");
    let sessionId = null;
    let originalSlotId = null;
    let opener = null;
    let requestVersion = 0;

    page.addEventListener("click", async function (event) {
        const button = event.target.closest("[data-swap-session-id]");
        if (!button) return;
        opener = button;
        sessionId = button.dataset.swapSessionId;
        originalSlotId = null;
        const version = ++requestVersion;
        confirm.disabled = true;
        label.hidden = true;
        select.replaceChildren(new Option("Chọn khung giờ", ""));
        status.textContent = "Đang tìm khung giờ phù hợp…";
        const row = button.closest(".my-pt-session");
        dialog.querySelector("[data-swap-session]").textContent =
                button.closest(".my-pt-day").querySelector(".my-pt-day-label").textContent.trim()
                + " · " + row.querySelector(".my-pt-session-hours").textContent.trim()
                + " · " + row.querySelector("h3").textContent.trim();
        dialog.showModal();
        dialog.querySelector("[data-swap-close]").focus();
        try {
            const result = await getJson(window.location.pathname, {action: "swapOptions", sessionId: sessionId});
            if (version !== requestVersion || !dialog.open) return;
            if (!result.success) {
                status.textContent = result.message || "Không tải được khung giờ. Vui lòng thử lại.";
                return;
            }
            originalSlotId = result.data.originalTimeSlotId;
            const slots = result.data.slots;
            if (!slots.length) {
                status.textContent = "Chưa có slot cuối tuần phù hợp. Bạn có thể thử lại khi PT mở thêm lịch hoặc trao đổi trực tiếp với PT.";
                return;
            }
            status.textContent = "Còn " + result.data.remainingSwaps + " lượt đổi trong gói · PT " + result.data.ptName;
            for (const slot of slots) {
                const date = slot.slotDate.split("-").reverse().join("/");
                select.appendChild(new Option(date + " · " + slot.startTime + "–" + slot.endTime, slot.timeSlotId));
            }
            label.hidden = false;
        } catch (error) {
            if (version === requestVersion && dialog.open) status.textContent = "Không tải được khung giờ. Vui lòng đóng và thử lại.";
        }
    });
    select.addEventListener("change", function () { confirm.disabled = !select.value; });
    dialog.querySelector("[data-swap-close]").addEventListener("click", function () { dialog.close(); });
    dialog.addEventListener("close", function () {
        requestVersion++;
        sessionId = null;
        opener?.focus();
    });
    confirm.addEventListener("click", function () {
        if (!sessionId || !select.value || !originalSlotId || confirm.disabled) return;
        setButtonLoading(confirm, true);
        dialog.querySelector("[data-swap-close]").disabled = true;
        const form = document.createElement("form");
        form.method = "POST";
        form.action = window.location.pathname;
        const values = {action: "swap", sessionId: sessionId,
            targetTimeSlotId: select.value, originalTimeSlotId: originalSlotId,
            csrfToken: page.dataset.csrfToken};
        for (const name of Object.keys(values)) {
            const input = document.createElement("input");
            input.type = "hidden";
            input.name = name;
            input.value = values[name];
            form.appendChild(input);
        }
        document.body.appendChild(form);
        form.submit();
    });
    window.addEventListener("pageshow", function () {
        setButtonLoading(confirm, false);
        confirm.disabled = !select.value;
        dialog.querySelector("[data-swap-close]").disabled = false;
    });
}
