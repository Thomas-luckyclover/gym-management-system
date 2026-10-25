/* BF-05: bấm hàng để xem chi tiết; Duyệt/Từ chối dùng hộp xác nhận và lý do có sẵn trong common.js. */
document.addEventListener("DOMContentLoaded", function () {
    let decisionTrigger = null;
    const bulkForm = document.getElementById("leave-bulk-form");
    const selectAll = document.getElementById("leave-select-all");
    const selectedButton = document.getElementById("leave-approve-selected");
    const choices = document.querySelectorAll('input[name="leaveRequestIds"]:not([disabled])');

    // Checkbox chỉ giữ lựa chọn của trang hiện tại; server vẫn kiểm tra lại từng đơn khi duyệt.
    function updateSelection() {
        let count = 0;
        for (const choice of choices) {
            if (choice.checked) {
                count++;
            }
        }
        selectedButton.disabled = count === 0;
        selectAll.disabled = choices.length === 0;
        selectAll.checked = count > 0 && count === choices.length;
        selectAll.indeterminate = count > 0 && count < choices.length;
        document.getElementById("leave-selection-count").textContent = "Đã chọn " + count + " đơn";
        selectedButton.dataset.confirmMessage = "Duyệt " + count + " đơn đã chọn? Hệ thống kiểm tra lại từng đơn.";
    }

    if (bulkForm) {
        for (const choice of choices) {
            choice.addEventListener("change", updateSelection);
        }
        selectAll.addEventListener("change", function () {
            for (const choice of choices) {
                choice.checked = selectAll.checked;
            }
            updateSelection();
        });
        updateSelection();
    }

    // Ghi nhớ nút mở hộp xác nhận để trả focus khi đóng hộp.
    document.addEventListener("click", function (event) {
        const trigger = event.target.closest("button[data-confirm-message]");
        if (trigger) {
            decisionTrigger = trigger;
        }
    }, true);
    const rows = document.querySelectorAll(".leave-request-row[data-review-url]");
    for (const row of rows) {
        // B1: bấm phần thông tin của hàng để mở đơn. Bỏ qua form/nút và thao tác bôi đen chữ.
        row.addEventListener("click", function (event) {
            if (event.target.closest("form, button, a, input, select, textarea")) {
                return;
            }
            if (window.getSelection().toString()) {
                return;
            }
            window.location.href = row.dataset.reviewUrl;
        });

        // B2: Enter trên hàng mở chi tiết; Enter trên nút vẫn là thao tác của nút đó.
        row.addEventListener("keydown", function (event) {
            if (event.key === "Enter" && event.target === row) {
                event.preventDefault();
                window.location.href = row.dataset.reviewUrl;
            }
        });
    }

    // B3: hộp chung đóng bằng Hủy/Escape/bấm ngoài thì trả focus về đúng nút của đơn.
    function restoreDecisionFocus() {
        if (decisionTrigger && !document.querySelector(".modal-backdrop")) {
            decisionTrigger.focus();
            decisionTrigger = null;
        }
    }

    window.addEventListener("click", restoreDecisionFocus);
    window.addEventListener("keydown", function (event) {
        if (!decisionTrigger) {
            return;
        }
        const dialog = document.querySelector(".modal-box");
        if (!dialog) {
            restoreDecisionFocus();
            return;
        }

        // Tab chỉ đi giữa ô lý do và hai nút trong hộp, không chạy xuống hàng đơn phía sau.
        if (event.key === "Tab") {
            const controls = dialog.querySelectorAll("textarea, button:not([disabled])");
            const first = controls[0];
            const last = controls[controls.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        }
    });
});
