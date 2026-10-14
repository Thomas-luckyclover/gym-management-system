/* Drives the two ink scenes (gym interior and street) from one eight-second clock.
 * Pose maths lives in comic-rig.js / comic-poses.js; this file only schedules frames and honours
 * pause, reduced motion, tab visibility and viewport visibility. */
(function () {
    "use strict";
    var CYCLE = 8;
    document.addEventListener("DOMContentLoaded", function () {
        var Poses = window.ComicPoses;
        var scenes = document.querySelectorAll(".comic-scene");
        if (!Poses || !scenes.length) { return; }
        var reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
        var inView = new WeakMap();
        var frame = 0, previous = 0, seconds = 0, started = false;
        var figures = [];

        scenes.forEach(function (scene) {
            scene.querySelectorAll("[data-fig]").forEach(function (node) {
                var id = node.getAttribute("data-fig"), kind = node.getAttribute("data-scene") || "gym";
                var def = (kind === "gym" ? Poses.GYM_FIGS : Poses.STREET_FIGS).filter(function (item) { return item.id === id; })[0];
                if (def) {
                    var card = scene.closest("[data-weather-type]"), type = card ? card.getAttribute("data-weather-type") : "";
                    figures.push({ node: node, def: def, scene: scene, kind: kind, last: "", rain: type === "rain" || type === "storm", shadow: node.querySelector(".comic-castshadow") });
                }
            });
        });
        var belts = document.querySelectorAll(".comic-belt");
        var live = [];
        scenes.forEach(function (scene) { live.push(scene); });

        function render(time, still) {
            figures.forEach(function (fig) {
                var t = still ? (fig.def.still === undefined ? time : fig.def.still) : time;
                var markup = Poses.figureGroup(fig.def, t, { rain: fig.rain });
                if (markup !== fig.last) { fig.node.innerHTML = markup; fig.last = markup; }
                var tf = Poses.figureTransform(fig.def, t);
                if (fig.node.getAttribute("transform") !== tf) { fig.node.setAttribute("transform", tf); }
                var vis = Poses.figureVisibility ? Poses.figureVisibility(fig.def, t) : 1;
                fig.node.style.opacity = vis < 1 ? String(vis) : "";
            });
            belts.forEach(function (belt) { belt.setAttribute("stroke-dashoffset", String(-(still ? 0 : time / CYCLE) * 15 * 8)); });
        }
        function pausedFor(scene) {
            return document.hidden || reduced.matches
                || document.body.classList.contains("dashboard-motion-paused")
                || document.documentElement.classList.contains("gms-lite") || inView.get(scene) === false;
        }
        function allSleeping() { return live.every(function (scene) { return scene.classList.contains("comic-scene--sleeping"); }); }
        function tick(now) {
            frame = 0;
            if (allSleeping()) { previous = 0; return; }
            if (!previous || now - previous >= 1000 / 30) {
                seconds = (seconds + (previous ? Math.min((now - previous) / 1000, 0.1) : 0)) % CYCLE;
                previous = now; started = true; render(seconds, false);
            }
            frame = requestAnimationFrame(tick);
        }
        function sync() {
            live.forEach(function (scene) { scene.classList.toggle("comic-scene--sleeping", pausedFor(scene)); });
            if (allSleeping() && !started) { render(0, true); }
            if (!allSleeping() && !frame) { frame = requestAnimationFrame(tick); }
            else if (allSleeping()) { cancelAnimationFrame(frame); frame = 0; previous = 0; }
        }
        if ("IntersectionObserver" in window) {
            var observer = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) { inView.set(entry.target, entry.isIntersecting); });
                sync();
            });
            scenes.forEach(function (scene) { observer.observe(scene); });
        }
        document.addEventListener("visibilitychange", sync);
        document.addEventListener("dashboardmotionchange", sync);
        reduced.addEventListener("change", sync);
        render(0, true); sync();
    });
})();
