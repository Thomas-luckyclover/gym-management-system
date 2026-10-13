// ===== Dải 7 ngày dạng tab =====
// JSP render đủ 7 bảng ngày (tắt JS vẫn đọc được); JS chỉ hiện bảng của ngày đang chọn.
// Bàn phím: ←/→ chuyển ngày, Home/End về ngày đầu/cuối (mẫu tab của WAI-ARIA).
document.addEventListener("DOMContentLoaded", function () {
    const strip = document.querySelector("[data-pt-days]");
    if (!strip) {
        return;
    }
    const tabs = Array.from(strip.querySelectorAll('[role="tab"]'));
    if (!tabs.length) {
        return;
    }

    // B1: chọn một ngày — đổi aria-selected, tabindex, ẩn/hiện bảng
    function select(tab, moveFocus) {
        tabs.forEach(function (item) {
            const selected = item === tab;
            item.setAttribute("aria-selected", selected ? "true" : "false");
            item.tabIndex = selected ? 0 : -1;
            const panel = document.getElementById(item.getAttribute("aria-controls"));
            if (panel) {
                panel.hidden = !selected;
            }
        });
        if (moveFocus) {
            tab.focus();
        }
        // Chỉ cuộn dải ngày ngang; không kéo cả trang khi đổi tab bằng bàn phím.
        if (strip.scrollWidth > strip.clientWidth) {
            strip.scrollTo({
                left: Math.max(0, tab.offsetLeft - strip.offsetLeft - (strip.clientWidth - tab.offsetWidth) / 2),
                behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth"
            });
        }
    }

    // B2: mặc định chọn hôm nay (ngày có aria-selected="true" từ JSP), không có thì ngày đầu
    const initial = tabs.find(function (tab) { return tab.getAttribute("aria-selected") === "true"; }) || tabs[0];
    select(initial, false);
    strip.classList.add("is-ready");

    strip.addEventListener("click", function (event) {
        const tab = event.target.closest('[role="tab"]');
        if (tab) {
            select(tab, false);
        }
    });

    strip.addEventListener("keydown", function (event) {
        const index = tabs.indexOf(document.activeElement);
        if (index < 0) {
            return;
        }
        let next = -1;
        if (event.key === "ArrowRight") {
            next = (index + 1) % tabs.length;
        } else if (event.key === "ArrowLeft") {
            next = (index - 1 + tabs.length) % tabs.length;
        } else if (event.key === "Home") {
            next = 0;
        } else if (event.key === "End") {
            next = tabs.length - 1;
        }
        if (next >= 0) {
            event.preventDefault();
            select(tabs[next], true);
        }
    });
});

// ===== Khởi tạo hộp xử lý buổi =====
// Code bên trong chạy khi trình duyệt đã đọc xong HTML.
document.addEventListener("DOMContentLoaded", function () {

    // JS tìm các thẻ JSP đã render
    const sessionDialog = document.getElementById("ptSessionDialog"); // hộp xử lý
    const dialogTitle = document.getElementById("ptSessionDialogTitle"); // tiêu đề hộp
    const closeButton = document.getElementById("ptSessionDialogClose"); // nút close
    const schedule = document.querySelector(".pt-schedule"); // Khối lịch 7 ngày
    let openingButton = null; // Nút cần nhận lại focus sau khi đóng hộp.


    // nếu thiếu 1 phần tử => ko tìm thấy
    if (!sessionDialog || !dialogTitle || !closeButton || !schedule) {
        return;
    }

    // ===== Mở hộp cho buổi được chọn =====
    function openSessionDialog(button) {
        // B1: bỏ qua nút bị khóa hoặc thiếu mã buổi.
        if (button.disabled || !button.dataset.sessionId) {
            return;
        }

        // B2: gắn mã buổi và tiêu đề, không chèn HTML.
        openingButton = button;
        sessionDialog.dataset.sessionId = button.dataset.sessionId;
        dialogTitle.textContent = button.dataset.sessionLabel || "Xử lý buổi tập";

        // B3: mở hộp; trình duyệt giữ focus trong hộp.
        if (!sessionDialog.open) {
            sessionDialog.showModal();
        }
    }

    // ===== Bắt nút xử lý trong lịch =====
    // bắt đầu bấm nút xử lý, even.target = phần tử vừa bấm
    schedule.addEventListener("click", function (event) {
        // closest(...): tìm từ phần tử đó lên các thẻ cha để lấy nút có data-pt-session-action.
        const button = event.target.closest("[data-pt-session-action]");
        // Kiểm tra nút thuộc khối lịch. Đúng nút được click thì open SessionDialogue
        if (button && schedule.contains(button)) {
            openSessionDialog(button);
        }
    });

    // ===== Đóng bằng nút hoặc phím Escape =====
    closeButton.addEventListener("click", function () {
        sessionDialog.close();
    });

    sessionDialog.addEventListener("close", function () {
        // B1: xóa mã buổi để lần mở sau không dùng lại.
        delete sessionDialog.dataset.sessionId;

        // B2: đưa focus về nút đã mở hộp.
        if (openingButton && openingButton.isConnected) {
            openingButton.focus();
        }
        openingButton = null;
    });
});
