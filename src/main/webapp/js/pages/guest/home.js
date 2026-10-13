// =====================================================================
// home.js — Linh vật Pumpy ở khối Kêu gọi đăng ký (WEB-INF/jsp/guest/_mascot.jspf)
// DOM: #mascotStage (.js-mascot / .is-visible / .is-jumping), #mascotButton, #mascotBubble, .mochi-eyes
// CSS: css/pages/guest/home.css mục "Linh vật Pumpy"
// =====================================================================

document.addEventListener("DOMContentLoaded", function () {
    const stage = document.getElementById("mascotStage");
    const button = document.getElementById("mascotButton");
    const bubble = document.getElementById("mascotBubble");
    if (!stage || !button || !bubble) {
        return;
    }
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const lines = [
        "Tập thôi!",
        "Thêm 1 rep nữa!",
        "Khởi động kỹ nhé!",
        "Nhớ uống đủ nước!",
        "Hẹn gặp ở phòng tập!",
        "Cố lên, sắp về đích!"
    ];
    let lineIndex = 0;
    let bubbleTimer = null;

    // B1: xuất hiện (bật lên + tạ rơi) khi khối kêu gọi cuộn vào màn hình
    if (!reduceMotion && "IntersectionObserver" in window) {
        stage.classList.add("js-mascot");
        const observer = new IntersectionObserver(function (entries) {
            for (let i = 0; i < entries.length; i++) {
                if (entries[i].isIntersecting) {
                    stage.classList.add("is-visible");
                    observer.disconnect();
                    // Chào người xem ngay khi vừa xuất hiện
                    setTimeout(function () {
                        say("Chào bạn! Mình là Pumpy.");
                    }, 900);
                }
            }
        }, { threshold: 0.35 });
        observer.observe(stage);
    }

    // B2: mắt nhìn theo con trỏ (giới hạn ±5 đơn vị SVG)
    if (!reduceMotion) {
        window.addEventListener("pointermove", function (event) {
            const rect = stage.getBoundingClientRect();
            // Chỉ tính khi con trỏ ở gần khối linh vật để đỡ tốn tài nguyên
            if (event.clientY < rect.top - 400 || event.clientY > rect.bottom + 400) {
                return;
            }
            // Tâm mắt nằm ở khoảng 28% chiều ngang, 40% chiều cao của SVG
            const eyeX = rect.left + rect.width * 0.28;
            const eyeY = rect.top + rect.height * 0.4;
            const dx = Math.max(-1, Math.min(1, (event.clientX - eyeX) / 300));
            const dy = Math.max(-1, Math.min(1, (event.clientY - eyeY) / 300));
            stage.style.setProperty("--ex", (dx * 5).toFixed(1) + "px");
            stage.style.setProperty("--ey", (dy * 4).toFixed(1) + "px");
        }, { passive: true });
    }

    // B3: bấm (chuột, Enter, Space) → nhảy + nói câu tiếp theo
    button.addEventListener("click", function () {
        if (!reduceMotion) {
            stage.classList.remove("is-jumping");
            void stage.offsetWidth;
            stage.classList.add("is-jumping");
        }
        say(lines[lineIndex]);
        lineIndex = (lineIndex + 1) % lines.length;
    });

    function say(text) {
        bubble.textContent = text;
        bubble.classList.add("is-on");
        clearTimeout(bubbleTimer);
        bubbleTimer = setTimeout(function () {
            bubble.classList.remove("is-on");
        }, 2200);
    }
});
