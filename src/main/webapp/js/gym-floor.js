// =====================================================================
// gym-floor.js — Tạm dừng/chạy hai cảnh comic Dashboard (khối gym và khối thời tiết).
// DOM: mọi .comic-scene (dashboard-weather.jspf). Chuyển động là CSS 8 giây trong dashboard-comic.css;
// file này chỉ gắn .comic-scene--sleeping khi tab ẩn, cảnh khuất màn hình, người dùng bấm tạm dừng
// (body.dashboard-motion-paused, sự kiện dashboardmotionchange), bật chế độ nhẹ hoặc giảm chuyển động.
// =====================================================================
(function () {
    "use strict";
    document.addEventListener("DOMContentLoaded", function () {
        const scenes = document.querySelectorAll(".comic-scene");
        const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
        const inView = new WeakMap();

        function sync() {
            scenes.forEach(function (scene) {
                const paused = document.hidden || reduced.matches
                    || document.body.classList.contains("dashboard-motion-paused")
                    || document.documentElement.classList.contains("gms-lite")
                    || inView.get(scene) === false;
                scene.classList.toggle("comic-scene--sleeping", paused);
            });
        }

        if ("IntersectionObserver" in window) {
            const observer = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) { inView.set(entry.target, entry.isIntersecting); });
                sync();
            });
            scenes.forEach(function (scene) { observer.observe(scene); });
        }
        document.addEventListener("visibilitychange", sync);
        document.addEventListener("dashboardmotionchange", sync);
        reduced.addEventListener("change", sync);
        sync();
    });
})();
