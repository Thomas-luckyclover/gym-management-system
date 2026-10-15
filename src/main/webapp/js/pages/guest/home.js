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
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches
        || document.documentElement.classList.contains("gms-lite");
    // Fact về từ nguyên: https://www.etymonline.com/word/gym và /word/gymnasium.
    const lines = [
        { text: "Mình rất muốn đồng hành cùng bạn. Super Gym cũng thế!", mood: "warm" },
        { text: "Bạn có biết? ‘Gym’ viết tắt của ‘gymnasium’, có gốc từ ‘gymnasion’ của Hy Lạp cổ đại.", mood: "curious" },
        { text: "Bạn lại chạm mình rồi! High-five nào… tay mình tròn nên hơi khó một tí.", mood: "wink" },
        { text: "Buổi đầu hơi ngại? Mình ở đây cổ vũ bạn mà. Cứ bắt đầu theo nhịp của bạn nhé.", mood: "warm" },
        { text: "Hôm nay bạn muốn thử gì: gặp huấn luyện viên, khám phá phòng tập hay chọn gói tập?", mood: "curious" },
        { text: "Mình ôm tạ chờ bạn nãy giờ đấy. Hẹn gặp nhau ở Super Gym nhé!", mood: "happy" },
        { text: "Bạn có biết? Gymnasion Hy Lạp cổ đại còn là nơi rèn cả trí tuệ lẫn thể chất.", mood: "curious" },
        { text: "Mình không biết hôm nay bạn nâng được bao nhiêu. Nhưng mình vui vì bạn đã có mặt.", mood: "warm" },
        { text: "Tay mình ngắn, nhưng tinh thần cổ vũ thì dài vô tận. Bạn làm được mà!", mood: "wink" },
        { text: "Bạn có mục tiêu, Super Gym có người đồng hành. Còn mình có hai tay tròn để cổ vũ!", mood: "happy" },
        { text: "Muốn bắt đầu cùng tụi mình? Nút Tạo tài khoản ở ngay cạnh mình đó.", mood: "happy" },
        { text: "Cảm ơn vì ghé nói chuyện với mình. Lần tới gặp ở phòng tập nhé?", mood: "warm" }
    ];
    let lineIndex = 0;
    let bubbleTimer = null;
    let greetingTimer = null;
    let greeted = false;

    // B1: xuất hiện (bật lên + tạ rơi) khi khối kêu gọi cuộn vào màn hình
    if ("IntersectionObserver" in window) {
        if (!reduceMotion) {
            stage.classList.add("js-mascot");
        }
        const observer = new IntersectionObserver(function (entries) {
            for (let i = 0; i < entries.length; i++) {
                if (entries[i].isIntersecting) {
                    stage.classList.add("is-visible");
                    observer.disconnect();
                    // Chào người xem ngay khi vừa xuất hiện
                    greetingTimer = setTimeout(function () {
                        if (!greeted) {
                            greeted = true;
                            say("Chào bạn, mình là Pumpy! Chạm mình nhé, mình có chuyện muốn kể.", "warm");
                        }
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
        greeted = true;
        clearTimeout(greetingTimer);
        if (!reduceMotion) {
            stage.classList.remove("is-jumping");
            void stage.offsetWidth;
            stage.classList.add("is-jumping");
        }
        say(lines[lineIndex].text, lines[lineIndex].mood);
        lineIndex = (lineIndex + 1) % lines.length;
    });

    function say(text, mood) {
        bubble.textContent = text;
        stage.dataset.mood = mood;
        bubble.classList.add("is-on");
        clearTimeout(bubbleTimer);
        bubbleTimer = setTimeout(function () {
            bubble.classList.remove("is-on");
        }, Math.max(5000, Math.min(10000, text.length * 75)));
    }
});
