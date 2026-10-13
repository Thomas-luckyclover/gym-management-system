// =====================================================================
// about-sun.js — Nền hero trang Về chúng tôi: mặt trời cam cháy đang tan chảy xuống mặt nước aqua.
// DOM: canvas#aboutSun trong .cy-hero (about.jsp). Chỉ trang trí, không chứa nội dung.
//   B1 trời: đêm đại dương phía trên, ửng cam cháy sát chân trời
//   B2 mặt trời: khối cam cháy chìm một phần dưới chân trời, các dải cắt ngang trôi xuống (cảm giác đang chảy)
//   B3 giọt nắng: nhỏ dài ra từ mép dưới mặt trời, đứt thành giọt, rơi xuống mặt nước tạo gợn tròn aqua
//   B4 mặt nước: gradient aqua → đáy biển, vệt phản chiếu mặt trời lăn tăn theo sóng
// Bật "Reduce motion" hoặc tab ẩn / cuộn khỏi hero: chỉ vẽ một khung tĩnh, không chạy vòng lặp.
// =====================================================================
document.addEventListener("DOMContentLoaded", function () {
    const canvas = document.getElementById("aboutSun");
    if (!canvas || !canvas.getContext) {
        return;
    }
    const ctx = canvas.getContext("2d");
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const SUNSET = "242, 92, 27";
    const AQUA = "43, 212, 222";

    let width = 0;
    let height = 0;
    let horizon = 0;
    let sunX = 0;
    let sunR = 0;
    let drips = [];
    let drops = [];
    let rings = [];
    let running = false;
    let visible = true;
    let lastTime = 0;
    // Lớp riêng cho mặt trời: cắt dải trên lớp này không làm thủng bầu trời phía sau
    const layer = document.createElement("canvas");
    const layerCtx = layer.getContext("2d");

    // Kích thước theo khung hero, nét theo mật độ điểm ảnh của màn hình (tối đa 2x cho nhẹ máy)
    function resize() {
        const rect = canvas.getBoundingClientRect();
        const ratio = Math.min(window.devicePixelRatio || 1, 2);
        width = rect.width;
        height = rect.height;
        canvas.width = Math.round(width * ratio);
        canvas.height = Math.round(height * ratio);
        ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        const narrow = width < 760;
        horizon = height * (narrow ? 0.7 : 0.64);
        sunX = width * (narrow ? 0.5 : 0.7);
        sunR = Math.min(width * (narrow ? 0.3 : 0.135), height * 0.2);
        layer.width = Math.round(sunR * 2.4 * ratio);
        layer.height = Math.round(sunR * 2.4 * ratio);
        layerCtx.setTransform(ratio, 0, 0, ratio, 0, 0);
        drips = [];
        for (let i = 0; i < 7; i++) {
            const drip = newDrip(i < 4 ? 0 : Math.random() * 3);
            drip.length = Math.random() * drip.maxLength;
            drips.push(drip);
        }
    }

    // Giọt chảy: vị trí ngang trong mép dưới mặt trời, dài dần rồi đứt ra rơi xuống
    function newDrip(delay) {
        return {
            offset: (Math.random() * 1.5 - 0.75),
            length: 0,
            maxLength: sunR * (0.18 + Math.random() * 0.22),
            speed: sunR * (0.05 + Math.random() * 0.06),
            thickness: sunR * (0.05 + Math.random() * 0.05),
            wait: delay
        };
    }

    // B1 + B2: trời và mặt trời lơ lửng sát chân trời
    function drawSky(time) {
        const sky = ctx.createLinearGradient(0, 0, 0, horizon);
        sky.addColorStop(0, "#041820");
        sky.addColorStop(0.45, "#0B1E26");
        sky.addColorStop(0.78, "#4A1A0A");
        sky.addColorStop(1, "#C2410C");
        ctx.fillStyle = sky;
        ctx.fillRect(0, 0, width, horizon);

        const glow = ctx.createRadialGradient(sunX, horizon, sunR * 0.4, sunX, horizon, sunR * 4.2);
        glow.addColorStop(0, "rgba(255, 154, 60, 0.55)");
        glow.addColorStop(0.4, "rgba(" + SUNSET + ", 0.22)");
        glow.addColorStop(1, "rgba(" + SUNSET + ", 0)");
        ctx.fillStyle = glow;
        ctx.fillRect(0, 0, width, horizon);

        const sunY = horizon - sunR * 1.32;
        const size = sunR * 2.4;
        const c = size / 2;
        layerCtx.clearRect(0, 0, size, size);
        layerCtx.globalCompositeOperation = "source-over";

        // Thân mặt trời: nửa trên tròn đều, nửa dưới mép gợn như chất lỏng đang chảy
        const body = layerCtx.createLinearGradient(0, c - sunR, 0, c + sunR);
        body.addColorStop(0, "#FFB36B");
        body.addColorStop(0.45, "#FF7A3D");
        body.addColorStop(1, "#C2410C");
        layerCtx.fillStyle = body;
        layerCtx.beginPath();
        for (let i = 0; i <= 120; i++) {
            const angle = (i / 120) * Math.PI * 2;
            const lower = Math.max(0, Math.sin(angle));
            const r = sunR * (1 + lower * 0.035 * Math.sin(angle * 7 + time * 1.6));
            const x = c + Math.cos(angle) * r;
            const y = c + Math.sin(angle) * r * (1 + lower * 0.05);
            if (i === 0) {
                layerCtx.moveTo(x, y);
            } else {
                layerCtx.lineTo(x, y);
            }
        }
        layerCtx.closePath();
        layerCtx.fill();

        // Dải cắt ngang trôi xuống: nửa dưới mặt trời "chảy" thành từng lát, càng xuống càng dày
        layerCtx.globalCompositeOperation = "destination-out";
        const band = sunR * 0.17;
        const shift = (time * sunR * 0.06) % band;
        for (let y = c + sunR * 0.08 + shift; y < c + sunR * 1.1; y += band) {
            const t = (y - c) / sunR;
            layerCtx.fillRect(0, y, size, Math.max(1, band * 0.42 * t));
        }
        ctx.drawImage(layer, sunX - c, sunY - c, size, size);
        return sunY;
    }

    // B3: giọt dài ra từ mép dưới mặt trời, đứt thì thành giọt rơi xuống nước
    function drawDrips(sunY, dt) {
        ctx.fillStyle = "#E2541A";
        drips.forEach(function (drip, index) {
            if (drip.wait > 0) {
                drip.wait -= dt;
                return;
            }
            drip.length += drip.speed * dt;
            const dx = drip.offset * sunR * 0.55;
            const x = sunX + dx;
            const edge = sunY + Math.sqrt(sunR * sunR - dx * dx);
            const top = edge - sunR * 0.12;
            const bottom = Math.min(horizon - 2, edge + drip.length);
            ctx.beginPath();
            ctx.moveTo(x - drip.thickness, top);
            ctx.lineTo(x - drip.thickness * 0.55, bottom - drip.thickness * 0.6);
            ctx.quadraticCurveTo(x, bottom + drip.thickness * 0.8, x + drip.thickness * 0.55, bottom - drip.thickness * 0.6);
            ctx.lineTo(x + drip.thickness, top);
            ctx.closePath();
            ctx.fill();
            if (drip.length > drip.maxLength) {
                drops.push({ x: x, y: bottom, vy: sunR * 0.4, r: drip.thickness * 0.75 });
                drips[index] = newDrip(0.6 + Math.random() * 2.4);
            }
        });
    }

    // Giọt rơi: chạm mặt nước thì tạo gợn tròn aqua
    function drawDrops(dt) {
        ctx.fillStyle = "#FF9A3C";
        drops = drops.filter(function (drop) {
            drop.vy += sunR * 1.6 * dt;
            drop.y += drop.vy * dt;
            if (drop.y >= horizon + 4) {
                rings.push({ x: drop.x, y: horizon + 6 + Math.random() * 10, r: 2, life: 1 });
                return false;
            }
            ctx.beginPath();
            ctx.ellipse(drop.x, drop.y, drop.r, drop.r * 1.5, 0, 0, Math.PI * 2);
            ctx.fill();
            return true;
        });
    }

    // B4: mặt nước + vệt phản chiếu + gợn
    function drawWater(time, dt) {
        const sea = ctx.createLinearGradient(0, horizon, 0, height);
        sea.addColorStop(0, "#0E6E82");
        sea.addColorStop(0.35, "#073847");
        sea.addColorStop(1, "#03121A");
        ctx.fillStyle = sea;
        ctx.fillRect(0, horizon, width, height - horizon);

        // Đường chân trời sáng aqua
        ctx.fillStyle = "rgba(" + AQUA + ", 0.55)";
        ctx.fillRect(0, horizon, width, 1.5);

        // Vệt phản chiếu: nét ngang bo tròn co giãn theo sóng, mờ dần hai đầu; gần chân trời cam, sâu xuống aqua
        const depth = height - horizon;
        const warm = ctx.createLinearGradient(sunX - sunR * 1.2, 0, sunX + sunR * 1.2, 0);
        warm.addColorStop(0, "rgba(255, 122, 61, 0)");
        warm.addColorStop(0.5, "rgba(255, 154, 72, 1)");
        warm.addColorStop(1, "rgba(255, 122, 61, 0)");
        const cool = ctx.createLinearGradient(sunX - sunR * 1.2, 0, sunX + sunR * 1.2, 0);
        cool.addColorStop(0, "rgba(" + AQUA + ", 0)");
        cool.addColorStop(0.5, "rgba(127, 230, 236, 1)");
        cool.addColorStop(1, "rgba(" + AQUA + ", 0)");
        ctx.lineCap = "round";
        for (let y = horizon + 4; y < height; y += 6) {
            const k = (y - horizon) / depth;
            const wave = Math.sin(time * 1.4 + y * 0.09) * 0.5 + Math.sin(time * 0.7 + y * 0.031) * 0.5;
            const half = sunR * (1 - k * 0.5) * (0.7 + 0.3 * wave);
            const sway = Math.sin(time * 0.9 + y * 0.05) * 12 * k;
            ctx.strokeStyle = k < 0.4 ? warm : cool;
            ctx.globalAlpha = Math.max(0, (1 - k) * (k < 0.4 ? 0.85 : 0.5));
            ctx.lineWidth = 1.2 + (1 - k) * 1.8;
            const pieces = 2 + (Math.floor(y / 6) % 2);
            const seg = (half * 2) / pieces;
            for (let piece = 0; piece < pieces; piece++) {
                const gap = seg * (0.16 + 0.14 * Math.sin(time * 2 + y * 0.2 + piece));
                ctx.beginPath();
                ctx.moveTo(sunX - half + sway + piece * seg + gap / 2, y);
                ctx.lineTo(sunX - half + sway + (piece + 1) * seg - gap / 2, y);
                ctx.stroke();
            }
        }
        ctx.globalAlpha = 1;

        // Gợn sóng lăn tăn trên toàn mặt nước
        ctx.strokeStyle = "rgba(" + AQUA + ", 0.14)";
        ctx.lineWidth = 1;
        for (let i = 0; i < 26; i++) {
            const y = horizon + 10 + ((i * 37) % Math.max(1, depth - 12));
            const x = ((i * 173 + time * (12 + (i % 5) * 6)) % (width + 200)) - 100;
            const len = 30 + (i % 4) * 22;
            ctx.beginPath();
            ctx.moveTo(x, y);
            ctx.lineTo(x + len, y);
            ctx.stroke();
        }

        // Gợn tròn nơi giọt nắng rơi xuống
        rings = rings.filter(function (ring) {
            ring.r += 34 * dt;
            ring.life -= dt * 0.7;
            if (ring.life <= 0) {
                return false;
            }
            ctx.strokeStyle = "rgba(" + AQUA + ", " + (ring.life * 0.7).toFixed(3) + ")";
            ctx.lineWidth = 1.4;
            ctx.beginPath();
            ctx.ellipse(ring.x, ring.y, ring.r, ring.r * 0.22, 0, 0, Math.PI * 2);
            ctx.stroke();
            return true;
        });
    }

    function frame(now) {
        const time = now / 1000;
        const dt = Math.min(0.05, lastTime ? time - lastTime : 0.016);
        lastTime = time;
        ctx.clearRect(0, 0, width, height);
        const sunY = drawSky(time);
        drawDrips(sunY, dt);
        drawWater(time, dt);
        drawDrops(dt);
        if (running) {
            window.requestAnimationFrame(frame);
        }
    }

    function start() {
        if (reduceMotion || running || !visible || document.hidden) {
            return;
        }
        running = true;
        lastTime = 0;
        window.requestAnimationFrame(frame);
    }

    function stop() {
        running = false;
    }

    resize();
    frame(4000);
    start();

    window.addEventListener("resize", function () {
        resize();
        if (!running) {
            frame(4000);
        }
    });
    document.addEventListener("visibilitychange", function () {
        if (document.hidden) {
            stop();
        } else {
            start();
        }
    });
    if ("IntersectionObserver" in window) {
        new IntersectionObserver(function (entries) {
            visible = entries[0].isIntersecting;
            if (visible) {
                start();
            } else {
                stop();
            }
        }).observe(canvas);
    }
});
