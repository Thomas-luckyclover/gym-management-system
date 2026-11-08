// =====================================================================
// home-motion.js — HIỆU ỨNG TRANG CHỦ: chữ hero trồi lên trước, ảnh lấy nét theo sau, khối hiện dần khi cuộn.
// DOM: .cine-hero, .cine-line > span, .cine-hero__photo, .cine-strip [data-card], [data-fade].
// Web Animations API chỉ THÊM chuyển động (fill: "none"): tắt JS hoặc bật "giảm chuyển động"
// thì HTML/CSS vẫn hiện đủ nội dung — không có gì bị ẩn chờ JS.
// =====================================================================

document.addEventListener("DOMContentLoaded", function () {
    const hero = document.querySelector(".cine-hero");
    const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
    const animations = [];
    const media = document.querySelector(".cine-hero__media");
    let heroVisible = true;
    let frameRequested = false;

    if (!hero) {
        return;
    }

    // B1: một nhịp mở màn duy nhất; animation không giữ opacity/transform sau khi xong.
    function play(element, keyframes, duration, delay) {
        if (!element || !element.animate || motionPreference.matches) {
            return;
        }
        const animation = element.animate(keyframes, {
            duration: duration,
            delay: delay,
            easing: "cubic-bezier(0.16, 1, 0.3, 1)",
            fill: "none"
        });
        animations.push(animation);
    }

    if (!motionPreference.matches) {
        const lines = hero.querySelectorAll(".cine-line > span");
        for (let i = 0; i < lines.length; i++) {
            play(lines[i], [
                { transform: "translateY(75%)", clipPath: "inset(0 0 100% 0)" },
                { transform: "translateY(0)", clipPath: "inset(0 0 0 0)" }
            ], 700, i * 80);
        }
        play(hero.querySelector(".cine-hero__photo"), [
            { transform: "scale(1.045)", filter: "blur(4px)" },
            { transform: "scale(1)", filter: "blur(0)" }
        ], 800, 0);
        play(hero.querySelector(".cine-actions"), [
            { opacity: 0.65, transform: "translateY(10px)" },
            { opacity: 1, transform: "translateY(0)" }
        ], 500, 180);
    }

    // B2: ảnh dịch tối đa 20px theo nhịp cuộn, chỉ tính khi hero còn trên màn hình.
    function updateHeroDepth() {
        frameRequested = false;
        if (!media || motionPreference.matches || !heroVisible || document.hidden) {
            return;
        }
        const rect = hero.getBoundingClientRect();
        const progress = Math.max(0, Math.min(1, -rect.top / rect.height));
        media.style.translate = "0 " + (progress * 20).toFixed(1) + "px";
    }

    function requestHeroDepth() {
        if (!frameRequested && heroVisible && !motionPreference.matches) {
            frameRequested = true;
            window.requestAnimationFrame(updateHeroDepth);
        }
    }

    window.addEventListener("scroll", requestHeroDepth, { passive: true });

    // B3: bộ ảnh được mở theo nhóm; các section đọc thông tin giữ nguyên, không chờ reveal.
    if ("IntersectionObserver" in window) {
        const heroObserver = new IntersectionObserver(function (entries) {
            heroVisible = entries[0].isIntersecting;
            if (heroVisible) {
                requestHeroDepth();
            }
        });
        heroObserver.observe(hero);

        const strip = document.querySelector(".cine-strip__cards");
        if (strip && !motionPreference.matches) {
            const stripObserver = new IntersectionObserver(function (entries) {
                if (!entries[0].isIntersecting) {
                    return;
                }
                const cards = strip.querySelectorAll("[data-card]");
                for (let i = 0; i < cards.length; i++) {
                    play(cards[i], [
                        { clipPath: "inset(0 0 12% 0)", opacity: 0.7 },
                        { clipPath: "inset(0 0 0 0)", opacity: 1 }
                    ], 600, i * 55);
                }
                stripObserver.disconnect();
            }, { threshold: 0.18 });
            stripObserver.observe(strip);
        }

        // Pumpy thở/chớp mắt chỉ khi nhìn thấy. CSS đọc .is-motion-paused.
        const mascot = document.getElementById("mascotStage");
        if (mascot) {
            let mascotVisible = false;
            function updateMascotMotion() {
                mascot.classList.toggle("is-motion-paused", !mascotVisible || document.hidden);
            }
            const mascotObserver = new IntersectionObserver(function (entries) {
                mascotVisible = entries[0].isIntersecting;
                updateMascotMotion();
            });
            mascotObserver.observe(mascot);
            document.addEventListener("visibilitychange", updateMascotMotion);
        }
    }

    // B4: thay đổi Reduce motion trong lúc mở trang cũng hủy chuyển động đang chạy.
    motionPreference.addEventListener("change", function () {
        if (!motionPreference.matches) {
            return;
        }
        for (let i = 0; i < animations.length; i++) {
            animations[i].cancel();
        }
        if (media) {
            media.style.removeProperty("translate");
        }
    });
});
