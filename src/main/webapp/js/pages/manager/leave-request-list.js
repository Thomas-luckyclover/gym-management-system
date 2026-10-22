/* BF-05: bấm hàng để xem chi tiết; Duyệt/Từ chối dùng hộp xác nhận và lý do có sẵn trong common.js. */
document.addEventListener("DOMContentLoaded", function () {
    let decisionTrigger = null;
    const rows = document.querySelectorAll(".leave-request-row[data-review-url]");
    for (const row of rows) {
        // B1: bấm phần thông tin của hàng để mở đơn. Bỏ qua form/nút và thao tác bôi đen chữ.
        row.addEventListener("click", function (event) {
            const actionButton = event.target.closest("button[data-confirm-message]");
            if (actionButton) {
                decisionTrigger = actionButton;
                return;
            }
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
