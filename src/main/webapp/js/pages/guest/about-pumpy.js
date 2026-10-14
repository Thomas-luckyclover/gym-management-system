// =====================================================================
// about-pumpy.js — Pumpy đi thuyền buồm trong hero trang Về chúng tôi (WEB-INF/jsp/guest/_pumpy-sailor.jspf).
// DOM: #pumpySailor (.is-out / .is-docked / .is-static), #sailorTrack (--drift), #sailorBubble, #sailorRescue,
//      #sailorButton, #sailorLive. CSS: css/pages/guest/about.css mục "Pumpy đi thuyền".
//   B1 hành trình: trôi ra phía mặt trời → dừng ngắm → trôi về đúng chỗ cũ → than vòng lặp thời gian → lặp lại
//   B2 thoại: mỗi chặng một câu; về bến thì hiện nút Giải cứu Pumpy (link đăng ký)
//   B3 bấm Pumpy: nói câu kế tiếp, đọc qua vùng aria-live
// Giảm chuyển động / chế độ nhẹ: thuyền đứng yên, chỉ nói khi được bấm. Hero khuất hoặc tab ẩn: tạm dừng.
// =====================================================================
document.addEventListener("DOMContentLoaded", function () {
    const sailor = document.getElementById("pumpySailor");
    const track = document.getElementById("sailorTrack");
    const bubble = document.getElementById("sailorBubble");
    const rescue = document.getElementById("sailorRescue");
    const button = document.getElementById("sailorButton");
    const live = document.getElementById("sailorLive");
    if (!sailor || !track || !bubble || !button) {
        return;
    }

    // Chỉ "giảm chuyển động" của hệ điều hành mới giữ thuyền đứng yên. Chế độ nhẹ (gms-lite, tự bật trên máy
    // ≤ 4 nhân) vẫn cho thuyền trôi vì chỉ là một transform chậm; CSS đã tắt nhấp nhô/cờ bay ở chế độ đó.
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    const OUTBOUND = [
        "Ra khơi tập cardio đây! Máy chèo ở Super Gym tốt hơn, nhưng không có hoàng hôn.",
        "Mặt trời tan chảy kìa. Chắc nó cũng quên khởi động trước khi tập.",
        "Chèo bằng tay ngắn khó lắm. Bạn tay dài thì đi tập giùm mình nha.",
        "Gió ngược y như cảm giác ngày đầu tập squat."
    ];
    const FAR = [
        "Phía trước là… vẫn là biển. Thôi quay về.",
        "Đi xa vậy đủ rồi. Bụng xoáy của mình bắt đầu say sóng.",
        "Nếu mình không về kịp, nhớ gia hạn thẻ tập giùm mình nha."
    ];
    const RETURN = [
        "Về bờ thôi! Ai làm rơi con tạ to dưới nước vậy?",
        "Hoàng hôn đẹp đó, nhưng đèn phòng tập vẫn ấm hơn.",
        "Chèo về mà bắp tay vẫn bằng hạt đậu. Đời buồn ghê."
    ];
    const LOOP = [
        "Khoan… chỗ này quen quen. Cảm giác mình đang kẹt trong vòng lặp thời gian vậy. Giải thoát mình bằng cách đăng ký Super Gym nha!",
        "Lại về đúng chỗ cũ, lần thứ {n} rồi đó. Chỉ một cú bấm Đăng ký mới phá được vòng lặp này thôi.",
        "Hôm qua mình cũng nói câu này. Ngày mai chắc cũng vậy. Cứu Pumpy đi, đăng ký Super Gym!",
        "Vòng lặp thứ {n}. Mình bắt đầu làm bạn với con cá ngoài kia rồi. Đăng ký giúp mình đi mà."
    ];
    const ALL = OUTBOUND.concat(FAR, RETURN);

    const LEG_MS = 9000;
    const PAUSE_MS = 3200;
    const DOCK_MS = 6500;
    const BUBBLE_MS = 4200;

    let loopCount = 0;
    let clickIndex = 0;
    let running = false;
    let heroVisible = true;
    let stepTimer = null;
    let bubbleTimer = null;
    let step = 0;

    function pick(list) {
        return list[Math.floor(Math.random() * list.length)];
    }

    // B2: hiện thoại; showRescue bật nút Giải cứu Pumpy khi về bến
    function say(text, duration, showRescue) {
        clearTimeout(bubbleTimer);
        bubble.textContent = text.replace("{n}", String(loopCount));
        bubble.classList.add("is-on");
        sailor.classList.toggle("is-docked", Boolean(showRescue));
        bubbleTimer = setTimeout(function () {
            bubble.classList.remove("is-on");
            sailor.classList.remove("is-docked");
        }, duration);
    }

    // B1: mỗi bước một chặng; --drift do CSS chuyển mượt bằng transition dài LEG_MS
    function runStep() {
        if (!running) {
            return;
        }
        let wait = 0;
        if (step === 0) {
            sailor.classList.add("is-out");
            say(pick(OUTBOUND), BUBBLE_MS, false);
            wait = LEG_MS;
        } else if (step === 1) {
            say(pick(FAR), PAUSE_MS, false);
            wait = PAUSE_MS;
        } else if (step === 2) {
            sailor.classList.remove("is-out");
            say(pick(RETURN), BUBBLE_MS, false);
            wait = LEG_MS;
        } else {
            loopCount++;
            const line = loopCount === 1 ? LOOP[0] : LOOP[1 + (loopCount % (LOOP.length - 1))];
            say(line, DOCK_MS, true);
            wait = DOCK_MS + 600;
        }
        step = (step + 1) % 4;
        stepTimer = setTimeout(runStep, wait);
    }

    function start() {
        if (running || reduceMotion || !heroVisible || document.hidden) {
            return;
        }
        running = true;
        sailor.classList.remove("is-paused");
        stepTimer = setTimeout(runStep, 1200);
    }

    function stop() {
        running = false;
        clearTimeout(stepTimer);
        sailor.classList.add("is-paused");
    }

    if (reduceMotion) {
        sailor.classList.add("is-static");
    } else {
        if ("IntersectionObserver" in window) {
            const observer = new IntersectionObserver(function (entries) {
                heroVisible = entries[0].isIntersecting;
                if (heroVisible) {
                    start();
                } else {
                    stop();
                }
            }, { threshold: 0.2 });
            observer.observe(sailor);
        }
        document.addEventListener("visibilitychange", function () {
            if (document.hidden) {
                stop();
            } else {
                start();
            }
        });
        start();
    }

    // B3: bấm Pumpy → câu kế tiếp, đọc cho trình đọc màn hình; câu cuối vòng là lời nhờ giải cứu
    button.addEventListener("click", function () {
        const text = clickIndex < ALL.length ? ALL[clickIndex] : LOOP[0];
        const isRescue = clickIndex >= ALL.length;
        clickIndex = isRescue ? 0 : clickIndex + 1;
        say(text, isRescue ? DOCK_MS : BUBBLE_MS, isRescue);
        if (live) {
            live.textContent = text.replace("{n}", String(Math.max(1, loopCount)));
        }
        sailor.classList.remove("is-hop");
        void sailor.offsetWidth;
        sailor.classList.add("is-hop");
    });
});
