// Lịch chọn ngày bắt đầu gói. JS chỉ gửi ngày khách bấm; ngày hết hạn và mọi luật do server quyết định.
// Cấu trúc: phần tử [data-sdp] chứa nút [data-sdp-toggle], lịch [data-sdp-cal], các chỗ hiển thị
// [data-sdp-start] / [data-sdp-end] và các thuộc tính data-sdp-url, data-sdp-sub, data-sdp-csrf.
document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("[data-sdp]").forEach(initStartDatePicker);
});

function initStartDatePicker(root) {
    const toggle = root.querySelector("[data-sdp-toggle]");
    const calendar = root.querySelector("[data-sdp-cal]");
    const save = root.querySelector("[data-sdp-save]");
    const error = root.querySelector("[data-sdp-error]");
    const startOut = root.querySelectorAll("[data-sdp-start]");
    const endOut = root.querySelectorAll("[data-sdp-end]");
    if (!toggle || !calendar || !save) {
        return;
    }

    let saved = {start: text(startOut), end: text(endOut)};
    let chosen = null;
    let busy = false;

    function text(nodes) {
        return nodes.length ? nodes[0].textContent : "";
    }

    function show(nodes, value) {
        nodes.forEach(function (node) { node.textContent = value; });
    }

    function setOpen(open) {
        calendar.hidden = !open;
        toggle.setAttribute("aria-expanded", String(open));
        if (!open) {
            // Đóng mà chưa lưu: trả ô ngày về giá trị đã lưu.
            chosen = null;
            save.disabled = true;
            error.textContent = "";
            show(startOut, saved.start);
            show(endOut, saved.end);
            markSelected(calendar.querySelector(".sdp-cal__day[data-start='" + saved.start + "']"));
        }
    }

    function markSelected(cell) {
        calendar.querySelectorAll(".sdp-cal__day").forEach(function (day) {
            day.classList.toggle("is-selected", day === cell);
        });
    }

    toggle.addEventListener("click", function () {
        setOpen(calendar.hidden);
    });

    document.addEventListener("click", function (event) {
        if (!calendar.hidden && !root.contains(event.target)) {
            setOpen(false);
        }
    });

    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && !calendar.hidden) {
            setOpen(false);
            toggle.focus();
        }
    });

    // Bấm một ngày: xem trước ngày bắt đầu/hết hạn (server đã tính sẵn trong data-end), chưa lưu.
    calendar.addEventListener("click", function (event) {
        const cell = event.target.closest(".sdp-cal__day");
        if (!cell || cell.disabled || busy) {
            return;
        }
        chosen = cell.dataset.date;
        markSelected(cell);
        show(startOut, cell.dataset.start);
        if (cell.dataset.end) {
            show(endOut, cell.dataset.end);
        }
        save.disabled = cell.dataset.start === saved.start;
        error.textContent = "";
    });

    save.addEventListener("click", async function () {
        if (!chosen || busy) {
            return;
        }
        busy = true;
        save.disabled = true;
        error.textContent = "";
        try {
            const result = await postData(root.dataset.sdpUrl, {
                csrfToken: root.dataset.sdpCsrf,
                subscriptionId: root.dataset.sdpSub,
                startDate: chosen
            });
            if (result.success && result.data) {
                saved = {start: result.data.startText, end: result.data.endText};
                chosen = null;
                show(startOut, saved.start);
                show(endOut, saved.end);
                // Đã dùng hết lượt chọn: bỏ nút và lịch.
                toggle.remove();
                calendar.remove();
                if (typeof showToast === "function") {
                    showToast("success", result.message || "Đã lưu ngày bắt đầu.");
                }
            } else {
                error.textContent = (result.errors && result.errors.startDate)
                        || result.message || "Chưa lưu được ngày bắt đầu. Vui lòng thử lại.";
                save.disabled = false;
            }
        } catch (e) {
            error.textContent = "Chưa lưu được ngày bắt đầu. Vui lòng thử lại.";
            save.disabled = false;
        } finally {
            busy = false;
        }
    });
}
