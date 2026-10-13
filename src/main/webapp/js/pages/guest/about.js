// =====================================================================
// about.js — Trang Về chúng tôi (/about): vòng card xoay 3D
// DOM nối với WEB-INF/jsp/guest/about.jsp · CSS: css/pages/guest/about.css
// Tự xoay tắt khi người dùng bật "Reduce motion".
// =====================================================================

document.addEventListener("DOMContentLoaded", function () {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    initQuoteRing(reduceMotion);
});

// ---------------------------------------------------------------------
// 1. Vòng card danh ngôn: tự xoay chậm, rê chuột thì dừng, kéo để xoay, nút ‹ › nhảy từng thẻ
// DOM: #ringStage, #quoteRing (--n), .cy-card (--i), #ringPrev, #ringNext, #ringCount
// ---------------------------------------------------------------------
function initQuoteRing(reduceMotion) {
    const stage = document.getElementById("ringStage");
    const ring = document.getElementById("quoteRing");
    if (!stage || !ring) {
        return;
    }
    const cards = ring.querySelectorAll(".cy-card");
    const count = cards.length;
    const step = 360 / count;
    const counter = document.getElementById("ringCount");

    let angle = 0;
    let target = null;
    let hovering = false;
    let paused = reduceMotion;
    const pauseButton = document.getElementById("ringPause");
    let dragging = false;
    let dragStartX = 0;
    let dragStartAngle = 0;

    // B1: vẽ góc hiện tại + đánh dấu thẻ đang ở mặt trước
    function render() {
        ring.style.setProperty("--ry", angle + "deg");
        let front = Math.round(-angle / step) % count;
        if (front < 0) {
            front = front + count;
        }
        for (let i = 0; i < cards.length; i++) {
            cards[i].classList.toggle("is-front", i === front);
        }
        if (counter) {
            counter.textContent = "#" + String(front + 1).padStart(2, "0") + " / " + count;
        }
    }

    // B2: vòng lặp — tự xoay hoặc trượt mềm tới thẻ được chọn
    function tick() {
        if (target !== null) {
            angle = angle + (target - angle) * 0.12;
            if (Math.abs(target - angle) < 0.05) {
                angle = target;
                target = null;
            }
        } else if (!hovering && !dragging && !paused && !document.hidden && !stage.matches(":focus-within")) {
            angle = angle - 0.08;
        }
        render();
        requestAnimationFrame(tick);
    }

    function snapTo(delta) {
        const current = Math.round(angle / step) * step;
        target = current - delta * step;
    }

    stage.addEventListener("pointerenter", function () { hovering = true; });
    stage.addEventListener("pointerleave", function () { hovering = false; dragging = false; stage.classList.remove("is-dragging"); });

    // B3: kéo chuột / vuốt để xoay
    stage.addEventListener("pointerdown", function (event) {
        dragging = true;
        target = null;
        dragStartX = event.clientX;
        dragStartAngle = angle;
        stage.classList.add("is-dragging");
        stage.setPointerCapture(event.pointerId);
    });
    stage.addEventListener("pointermove", function (event) {
        if (dragging) {
            angle = dragStartAngle + (event.clientX - dragStartX) * 0.35;
        }
    });
    stage.addEventListener("pointerup", function () {
        dragging = false;
        stage.classList.remove("is-dragging");
        snapTo(0);
    });

    document.getElementById("ringPrev").addEventListener("click", function () { snapTo(-1); });
    document.getElementById("ringNext").addEventListener("click", function () { snapTo(1); });

    // Nút tạm dừng chỉ điều khiển tự xoay; kéo và nút trước/sau vẫn dùng được.
    if (pauseButton) {
        pauseButton.hidden = false;
        pauseButton.setAttribute("aria-pressed", String(paused));
        pauseButton.textContent = paused ? "Tiếp tục xoay" : "Tạm dừng xoay";
        pauseButton.addEventListener("click", function () {
            paused = !paused;
            pauseButton.setAttribute("aria-pressed", String(paused));
            pauseButton.textContent = paused ? "Tiếp tục xoay" : "Tạm dừng xoay";
        });
    }

    // Phím ← → khi đang focus vào khu vòng card
    stage.setAttribute("tabindex", "0");
    stage.setAttribute("aria-label", "Vòng thẻ danh ngôn, dùng phím mũi tên trái phải để xoay");
    stage.addEventListener("keydown", function (event) {
        if (event.key === "ArrowLeft") {
            event.preventDefault();
            snapTo(-1);
        } else if (event.key === "ArrowRight") {
            event.preventDefault();
            snapTo(1);
        }
    });

    render();
    requestAnimationFrame(tick);
}
