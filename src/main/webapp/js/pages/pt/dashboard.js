// ===== Mở tất cả / đóng tất cả ngày trong lịch 7 ngày =====
// Mỗi ngày tự mở bằng <details>; JS chỉ cần cho hai nút điều khiển cả lịch.
document.addEventListener("DOMContentLoaded", function () {
    const schedule = document.querySelector(".pt-schedule");
    if (!schedule) {
        return;
    }
    const controls = schedule.querySelector("[data-schedule-controls]");
    const days = schedule.querySelectorAll("details.pt-schedule-day");
    if (!controls || !days.length) {
        return;
    }
    controls.hidden = false;
    controls.querySelector("[data-schedule-expand]").addEventListener("click", function () {
        days.forEach(function (day) { day.open = true; });
    });
    controls.querySelector("[data-schedule-reset]").addEventListener("click", function () {
        // Đóng mọi ngày, kể cả Hôm nay đang mở sẵn khi vào trang.
        days.forEach(function (day) { day.open = false; });
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

// Tạo hạt cho lớp .pt-weather-effects trong _awning.jspf; CSS weather.css lo chuyển động.
document.addEventListener("DOMContentLoaded", function () {
    const effects = document.querySelector(".pt-weather-effects");
    if (!effects || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        return;
    }

    let count = 0;
    let shortestDuration = 2;
    let durationRange = 3;
    if (document.body.classList.contains("weather-rain")) {
        count = 42;
    } else if (document.body.classList.contains("weather-storm")) {
        count = 60;
        shortestDuration = 1.2;
        durationRange = 1.5;
    } else if (document.body.classList.contains("weather-snow")) {
        count = 38;
        shortestDuration = 8;
        durationRange = 8;
    } else if (document.body.classList.contains("weather-wind")) {
        count = 24;
        shortestDuration = 5;
        durationRange = 5;
    } else if (document.body.classList.contains("weather-night")) {
        count = 36;
        shortestDuration = 2;
        durationRange = 3;
    }

    const particles = document.createDocumentFragment();
    for (let i = 0; i < count; i++) {
        const particle = document.createElement("span");
        particle.className = "weather-particle";
        particle.style.setProperty("--particle-x", Math.random() * 100 + "%");
        particle.style.setProperty("--particle-y", Math.random() * 85 + 10 + "%");
        particle.style.setProperty("--particle-opacity", Math.random() * 0.4 + 0.25);
        particle.style.setProperty("--particle-delay", -Math.random() * 12 + "s");
        particle.style.setProperty("--particle-duration", Math.random() * durationRange + shortestDuration + "s");
        particle.style.setProperty("--particle-scale", (Math.random() * 0.6 + 0.7).toFixed(2));
        particles.appendChild(particle);
    }
    effects.appendChild(particles);
});

// Nhiệt độ đã có sẵn trong HTML; JS chỉ cho số chạy nhanh khi không bật Reduce motion.
document.addEventListener("DOMContentLoaded", function () {
    const temperature = document.querySelector("[data-weather-temperature]");
    if (!temperature || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        return;
    }
    const target = Number(temperature.dataset.weatherTemperature);
    if (!Number.isFinite(target)) {
        return;
    }
    temperature.setAttribute("aria-label", target + " độ C");
    const start = performance.now();
    function drawTemperature(now) {
        const progress = Math.min((now - start) / 700, 1);
        temperature.textContent = Math.round(target * progress) + "°";
        if (progress < 1) {
            requestAnimationFrame(drawTemperature);
        }
    }
    requestAnimationFrame(drawTemperature);
});
