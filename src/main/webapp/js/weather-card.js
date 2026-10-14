/* Original ink weather marks in a deterministic eight-second loop.
 * The Service still owns the city, condition and all weather measurements. */
(function () {
    "use strict";
    document.addEventListener("DOMContentLoaded", function () {
        const canvas = document.querySelector(".wx-card__canvas");
        if (!canvas) { return; }
        const ctx = canvas.getContext("2d");
        if (!ctx) { return; }
        const card = canvas.closest("[data-weather-type]");
        const type = card ? card.dataset.weatherType : "cloudy";
        const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
        const CYCLE = 8;
        let width = 0, height = 0, seconds = 0, previous = 0, handle = 0, visible = true;
        let ink = "#151515";
        function resize() {
            const rect = canvas.getBoundingClientRect();
            const ratio = Math.min(window.devicePixelRatio || 1, 2);
            width = Math.max(1, rect.width); height = Math.max(1, rect.height);
            canvas.width = Math.round(width * ratio); canvas.height = Math.round(height * ratio);
            ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
            ink = getComputedStyle(card).getPropertyValue("--comic-ink").trim() || ink;
            draw();
        }
        function draw() {
            ctx.clearRect(0, 0, width, height);
            ctx.strokeStyle = ink; ctx.fillStyle = ink; ctx.lineWidth = 1; ctx.lineCap = "round";
            const phase = seconds / CYCLE;
            if (type === "rain" || type === "storm") {
                ctx.globalAlpha = .38;
                for (let i = 0; i < 70; i++) {
                    const x = ((i * 73.7 - phase * width * 2) % width + width) % width;
                    const y = (i * 49.3 + phase * height * 8) % height;
                    ctx.beginPath(); ctx.moveTo(x, y); ctx.lineTo(x - 4, y + 12); ctx.stroke();
                }
            } else if (type === "snow") {
                ctx.globalAlpha = .7;
                for (let i = 0; i < 36; i++) {
                    const x = (i * 61.7 + Math.sin(phase * Math.PI * 2 + i) * 8 + width) % width;
                    const y = (i * 39.1 + phase * height * 2) % height;
                    ctx.beginPath(); ctx.arc(x, y, 1.5, 0, Math.PI * 2); ctx.stroke();
                }
            } else if (type === "night") {
                for (let i = 0; i < 12; i++) {
                    const x = width * (.66 + (i % 4) * .075), y = 12 + Math.floor(i / 4) * 23;
                    ctx.globalAlpha = .3 + .35 * (1 + Math.sin(phase * Math.PI * 2 + i)) / 2;
                    ctx.beginPath(); ctx.moveTo(x - 2, y); ctx.lineTo(x + 2, y);
                    ctx.moveTo(x, y - 2); ctx.lineTo(x, y + 2); ctx.stroke();
                }
            } else {
                const count = type === "wind" ? 7 : 3;
                ctx.globalAlpha = .75;
                for (let i = 0; i < count; i++) {
                    const travel = (phase + i / count) % 1;
                    const x = width * (1.1 - travel * 1.2);
                    const y = height * (.68 + i % 3 * .08) + Math.sin(travel * Math.PI * 4 + i) * 10;
                    ctx.save(); ctx.translate(x, y); ctx.rotate(travel * Math.PI * 2);
                    ctx.beginPath(); ctx.moveTo(-5, 0); ctx.quadraticCurveTo(0, -6, 6, 0);
                    ctx.quadraticCurveTo(0, 5, -5, 0); ctx.moveTo(-5, 0); ctx.lineTo(8, 0); ctx.stroke(); ctx.restore();
                }
                ctx.globalAlpha = .25;
                for (let i = 0; i < 2; i++) {
                    const travel = (phase + i * .5) % 1;
                    const x = width * (1.2 - travel * 1.5), y = height * (.65 + i * .1);
                    ctx.beginPath(); ctx.moveTo(x, y); ctx.bezierCurveTo(x + 15, y - 5, x + 25, y + 5, x + 45, y); ctx.stroke();
                }
            }
            ctx.globalAlpha = 1;
        }
        function paused() {
            return !visible || document.hidden || reduced.matches
                || document.body.classList.contains("dashboard-motion-paused")
                || document.documentElement.classList.contains("gms-lite");
        }
        function tick(now) {
            handle = 0;
            if (paused()) { previous = 0; return; }
            if (!previous || now - previous >= 1000 / 30) {
                const elapsed = previous ? Math.min((now - previous) / 1000, .1) : 0;
                seconds = (seconds + elapsed) % CYCLE; previous = now; draw();
            }
            handle = requestAnimationFrame(tick);
        }
        function sync() {
            if (paused()) { if (handle) { cancelAnimationFrame(handle); } handle = 0; previous = 0; }
            else if (!handle) { handle = requestAnimationFrame(tick); }
        }
        document.addEventListener("visibilitychange", sync);
        document.addEventListener("dashboardmotionchange", sync);
        reduced.addEventListener("change", sync);
        if ("IntersectionObserver" in window) {
            new IntersectionObserver(function (entries) { visible = entries[0].isIntersecting; sync(); }).observe(canvas);
        }
        if ("ResizeObserver" in window) { new ResizeObserver(resize).observe(card); }
        else { window.addEventListener("resize", resize); }
        resize(); sync();
    });
})();
