// About: ảnh vuông và chữ vào cùng nhịp trong .cy-values; nội dung mặc định luôn hiện.
// DOM: .cy-value-media và .cy-value-copy trong about.jsp. Không thêm vòng lặp chuyển động.
document.addEventListener("DOMContentLoaded", initializeAboutValues);

function initializeAboutValues() {
    const cards = document.querySelectorAll(".cy-value");
    const preference = window.matchMedia("(prefers-reduced-motion: reduce)");
    if (preference.matches || !("IntersectionObserver" in window)) {
        return;
    }
    const animations = [];
    const observer = new IntersectionObserver(function (entries) {
        for (let index = 0; index < entries.length; index++) {
            const entry = entries[index];
            if (!entry.isIntersecting) {
                continue;
            }
            const card = entry.target;
            const image = card.querySelector(".cy-value-media");
            const copy = card.querySelector(".cy-value-copy");
            const reverse = card.matches(":nth-child(even)");
            const distance = reverse ? "32px" : "-32px";
            if (image && image.animate && !preference.matches) {
                animations.push(image.animate([
                    { transform: "translateX(" + distance + ")", opacity: 0.4 },
                    { transform: "translateX(0)", opacity: 1 }
                ], { duration: 700, easing: "cubic-bezier(0.16, 1, 0.3, 1)" }));
                animations.push(copy.animate([
                    { transform: "translateY(16px)", opacity: 0.5 },
                    { transform: "translateY(0)", opacity: 1 }
                ], { duration: 600, delay: 90, easing: "cubic-bezier(0.16, 1, 0.3, 1)" }));
            }
            observer.unobserve(card);
        }
    }, { threshold: 0.2 });
    cards.forEach(function (card) { observer.observe(card); });
    preference.addEventListener("change", function () {
        if (preference.matches) {
            observer.disconnect();
            animations.forEach(function (animation) { animation.cancel(); });
        }
    });
}
