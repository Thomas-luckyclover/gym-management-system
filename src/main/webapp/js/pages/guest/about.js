// =====================================================================
// about.js — Trang Về chúng tôi (/about): vòng card xoay 3D, đếm số, nghiêng, hiện dần, minigame Pumpy
// DOM nối với WEB-INF/jsp/guest/about.jsp · CSS: css/pages/guest/about.css
// Mọi chuyển động tự tắt khi người dùng bật "Reduce motion".
// =====================================================================

document.addEventListener("DOMContentLoaded", function () {
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    initQuoteRing(reduceMotion);
    initCounters(reduceMotion);
    initTilt(reduceMotion);
    initReveal(reduceMotion);
    initPumpyGame(reduceMotion);
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
        } else if (!hovering && !dragging && !reduceMotion) {
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

    // Phím ← → khi đang focus vào khu vòng card
    stage.setAttribute("tabindex", "0");
    stage.setAttribute("aria-label", "Vòng thẻ danh ngôn, dùng phím mũi tên trái phải để xoay");
    stage.addEventListener("keydown", function (event) {
        if (event.key === "ArrowLeft") {
            snapTo(-1);
        } else if (event.key === "ArrowRight") {
            snapTo(1);
        }
    });

    render();
    requestAnimationFrame(tick);
}

// ---------------------------------------------------------------------
// 2. Đếm số khi khối chỉ số cuộn vào màn hình. DOM: [data-cy-count], [data-cy-suffix]
// ---------------------------------------------------------------------
function initCounters(reduceMotion) {
    const items = document.querySelectorAll("[data-cy-count]");
    if (items.length === 0 || reduceMotion || !("IntersectionObserver" in window)) {
        return;
    }
    const observer = new IntersectionObserver(function (entries) {
        for (let i = 0; i < entries.length; i++) {
            if (entries[i].isIntersecting) {
                countUp(entries[i].target);
                observer.unobserve(entries[i].target);
            }
        }
    }, { threshold: 0.6 });
    for (let i = 0; i < items.length; i++) {
        observer.observe(items[i]);
    }
}

function countUp(element) {
    const target = Number(element.dataset.cyCount);
    const suffix = element.dataset.cySuffix || "";
    // Năm (2019) đếm từ 2000 cho tự nhiên, số khác đếm từ 0
    const from = target > 1900 && target < 2100 ? 2000 : 0;
    const start = performance.now();
    function draw(now) {
        const progress = Math.min((now - start) / 1400, 1);
        const eased = 1 - Math.pow(1 - progress, 4);
        const value = Math.round(from + (target - from) * eased);
        element.textContent = (target > 2100 ? value.toLocaleString("vi-VN") : String(value)) + suffix;
        if (progress < 1) {
            requestAnimationFrame(draw);
        }
    }
    requestAnimationFrame(draw);
}

// ---------------------------------------------------------------------
// 3. Nghiêng 3D theo con trỏ cho [data-cy-tilt] (chỉ thiết bị có chuột)
// ---------------------------------------------------------------------
function initTilt(reduceMotion) {
    if (reduceMotion || !window.matchMedia("(hover: hover)").matches) {
        return;
    }
    const items = document.querySelectorAll("[data-cy-tilt]");
    for (let i = 0; i < items.length; i++) {
        attachCyTilt(items[i]);
    }
}

function attachCyTilt(element) {
    element.addEventListener("pointermove", function (event) {
        const rect = element.getBoundingClientRect();
        const x = (event.clientX - rect.left) / rect.width - 0.5;
        const y = (event.clientY - rect.top) / rect.height - 0.5;
        element.style.setProperty("--ry-tilt", (x * 10).toFixed(2) + "deg");
        element.style.setProperty("--rx", (-y * 10).toFixed(2) + "deg");
    });
    element.addEventListener("pointerleave", function () {
        element.style.setProperty("--ry-tilt", "0deg");
        element.style.setProperty("--rx", "0deg");
    });
}

// ---------------------------------------------------------------------
// 4. Các khối hiện dần khi cuộn tới (trừ hero)
// ---------------------------------------------------------------------
function initReveal(reduceMotion) {
    if (reduceMotion || !("IntersectionObserver" in window)) {
        return;
    }
    const sections = document.querySelectorAll(".cy-section");
    const observer = new IntersectionObserver(function (entries) {
        for (let i = 0; i < entries.length; i++) {
            if (entries[i].isIntersecting) {
                entries[i].target.classList.add("is-in");
                observer.unobserve(entries[i].target);
            }
        }
    }, { threshold: 0.15 });
    for (let i = 0; i < sections.length; i++) {
        sections[i].classList.add("js-reveal");
        observer.observe(sections[i]);
    }
}

// ---------------------------------------------------------------------
// 5. Minigame: Pumpy nhảy qua đồ ăn vặt
// DOM: #cyGame, #gamePumpy, #gameObstacles, #foodTemplates, #gameOverlay, #gameMsg, #gameStart, #gameScore, #gameBest
// Điều khiển: Space / ↑ / chạm. Điểm = tổng kcal của món đã né. Kỷ lục lưu localStorage (lỗi thì bỏ qua).
// ---------------------------------------------------------------------
function initPumpyGame(reduceMotion) {
    const game = document.getElementById("cyGame");
    if (!game) {
        return;
    }
    const mochi = document.getElementById("gamePumpy");
    const layer = document.getElementById("gameObstacles");
    const templates = document.getElementById("foodTemplates").content.querySelectorAll(".cy-food");
    const overlay = document.getElementById("gameOverlay");
    const message = document.getElementById("gameMsg");
    const startButton = document.getElementById("gameStart");
    const scoreLabel = document.getElementById("gameScore");
    const bestLabel = document.getElementById("gameBest");

    const GRAVITY = 2600;     // px/giây²
    const JUMP_SPEED = 900;   // px/giây
    let running = false;
    let jumpHeight = 0;
    let jumpVelocity = 0;
    let obstacles = [];
    let speed = 360;          // px/giây, tăng dần
    let spawnTimer = 0;
    let score = 0;
    let best = readBest();
    let lastTime = 0;

    bestLabel.textContent = best.toLocaleString("vi-VN");

    function readBest() {
        try {
            return Number(localStorage.getItem("superGymPumpyBest")) || 0;
        } catch (error) {
            return 0;
        }
    }

    function saveBest(value) {
        try {
            localStorage.setItem("superGymPumpyBest", String(value));
        } catch (error) {
            // Trình duyệt chặn lưu trữ: chỉ giữ kỷ lục trong phiên
        }
    }

    function jump() {
        if (!running) {
            return;
        }
        // Chỉ nhảy khi đang chạm đất
        if (jumpHeight <= 0.5) {
            jumpVelocity = JUMP_SPEED;
        }
    }

    function start() {
        // B1: dọn màn, đặt lại điểm
        for (let i = 0; i < obstacles.length; i++) {
            obstacles[i].element.remove();
        }
        obstacles = [];
        score = 0;
        speed = 360;
        spawnTimer = 0.6;
        jumpHeight = 0;
        jumpVelocity = 0;
        scoreLabel.textContent = "0";
        overlay.hidden = true;
        running = true;
        game.classList.add("is-running");
        game.focus();
        lastTime = performance.now();
        requestAnimationFrame(loop);
    }

    function gameOver() {
        running = false;
        game.classList.remove("is-running");
        if (score > best) {
            best = score;
            saveBest(best);
            bestLabel.textContent = best.toLocaleString("vi-VN");
            message.textContent = "Kỷ lục mới! " + score.toLocaleString("vi-VN") + " kcal";
        } else {
            message.textContent = "Ôi, Pumpy dính đồ ăn vặt rồi!";
        }
        startButton.textContent = "Chơi lại";
        overlay.hidden = false;
        startButton.focus();
    }

    function spawn() {
        const template = templates[Math.floor(Math.random() * templates.length)];
        const element = template.cloneNode(true);
        layer.appendChild(element);
        obstacles.push({ element: element, x: game.clientWidth + 20, kcal: Number(template.dataset.kcal), passed: false });
    }

    // B2: vòng lặp — trọng lực, di chuyển đồ ăn, tính điểm, kiểm tra va chạm
    function loop(now) {
        if (!running) {
            return;
        }
        const seconds = Math.max(0, Math.min((now - lastTime) / 1000, 0.1));
        lastTime = now;

        jumpVelocity = jumpVelocity - GRAVITY * seconds;
        jumpHeight = Math.max(0, jumpHeight + jumpVelocity * seconds);
        if (jumpHeight === 0) {
            jumpVelocity = 0;
        }
        mochi.style.setProperty("--jump", jumpHeight + "px");

        speed = speed + seconds * 8;
        spawnTimer = spawnTimer - seconds;
        if (spawnTimer <= 0) {
            spawn();
            spawnTimer = 0.9 + Math.random() * 1.1;
        }

        const mochiRect = mochi.getBoundingClientRect();
        for (let i = obstacles.length - 1; i >= 0; i--) {
            const item = obstacles[i];
            item.x = item.x - speed * seconds;
            item.element.style.left = item.x + "px";

            // Va chạm: thu nhỏ khung 30% cho cảm giác công bằng
            const foodRect = item.element.getBoundingClientRect();
            const hit = foodRect.left + 10 < mochiRect.right - 18
                && foodRect.right - 10 > mochiRect.left + 18
                && foodRect.top + 10 < mochiRect.bottom - 8;
            if (hit) {
                gameOver();
                return;
            }
            if (!item.passed && foodRect.right < mochiRect.left) {
                item.passed = true;
                score = score + item.kcal;
                scoreLabel.textContent = score.toLocaleString("vi-VN");
            }
            if (item.x < -80) {
                item.element.remove();
                obstacles.splice(i, 1);
            }
        }
        requestAnimationFrame(loop);
    }

    startButton.addEventListener("click", function (event) {
        event.stopPropagation();
        start();
    });
    game.addEventListener("pointerdown", function (event) {
        if (event.target.closest("button")) {
            return;
        }
        jump();
    });
    game.addEventListener("keydown", function (event) {
        if (event.key === " " || event.key === "ArrowUp") {
            event.preventDefault();
            if (running) {
                jump();
            } else {
                start();
            }
        }
    });

    // Bật chế độ giảm chuyển động: vẫn chơi được, chỉ tắt các hiệu ứng trang trí (CSS lo)
    if (reduceMotion) {
        message.textContent = "Sẵn sàng? (chế độ giảm chuyển động)";
    }
}
