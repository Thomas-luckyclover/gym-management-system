// manager-dashboard.js — Báo cáo vận hành & doanh thu (#66)
// Luồng: kiểm tra From/To khi đổi ngày hoặc bấm Xem → sai thì hiện lỗi, khóa nút Xuất CSV, không gửi form.
document.addEventListener("DOMContentLoaded", function () {

    const form = document.getElementById("reportFilter");
    const fromInput = document.getElementById("reportFrom");
    const toInput = document.getElementById("reportTo");
    const errorBox = document.getElementById("reportRangeError");
    const exportLink = document.getElementById("exportCsv");

    if (!form || !fromInput || !toInput) {
        return;
    }

    // Trả về true nếu khoảng ngày hợp lệ (cả hai bắt buộc, From <= To); cập nhật giao diện theo kết quả
    function isRangeValid() {
        const from = fromInput.value;
        const to = toInput.value;
        const isValid = from !== "" && to !== "" && from <= to;   // chuỗi yyyy-mm-dd so sánh được trực tiếp

        if (errorBox) {
            errorBox.hidden = isValid;
            errorBox.textContent = (from === "" || to === "")
                ? "Vui lòng chọn cả ngày bắt đầu và ngày kết thúc."
                : "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.";
        }
        if (exportLink) {
            if (!isValid) {
                exportLink.setAttribute("aria-disabled", "true");
            }
        }
        return isValid;
    }

    function handleRangeChange() {
        isRangeValid();
    }

    fromInput.addEventListener("change", handleRangeChange);
    toInput.addEventListener("change", handleRangeChange);

    function handleFilterSubmit(event) {
        if (!isRangeValid()) {
            event.preventDefault();
        }
    }

    form.addEventListener("submit", handleFilterSubmit);

    // Xuất CSV: link luôn trỏ tới báo cáo ĐANG HIỂN THỊ (server đã gắn from/to). Nếu người dùng vừa sửa ngày
    // mà chưa bấm Xem thì nhắc bấm Xem trước, tránh xuất số khác với màn hình.
    // File tải về không làm tải lại trang → hiện toast MSG32 bằng JS (nội dung do server dựng sẵn).
    if (exportLink) {
        const shownFrom = fromInput.defaultValue;
        const shownTo = toInput.defaultValue;

        function handleExportClick(event) {
            if (exportLink.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
                return;
            }
            if (fromInput.value !== shownFrom || toInput.value !== shownTo) {
                event.preventDefault();
                if (typeof showToast === "function") {
                    showToast("error", "Bạn đã đổi khoảng ngày. Bấm Xem trước khi xuất CSV.");
                }
                return;
            }
            const message = exportLink.dataset.exportedMessage;
            if (message && typeof showToast === "function") {
                showToast("success", message);
            }
        }

        exportLink.addEventListener("click", handleExportClick);
    }
});