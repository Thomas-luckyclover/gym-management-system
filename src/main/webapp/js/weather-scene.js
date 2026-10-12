// =====================================================================
// weather-scene.js — Hiệu ứng thời tiết toàn trang (PT/Customer Dashboard)
// DOM: #weatherScene (common/weather-scene.jspf), class weather-<kiểu> trên <body>
// Vẽ hạt mưa/tuyết/gió/sao bằng canvas 2D; con trỏ làm "cái ô" đẩy hạt ra xa.
// Tầng mây/nắng lệch theo con trỏ (--px, --py). Giông có chớp ngẫu nhiên + bấm vào khối chào.
// Tạm dừng khi tab ẩn; tắt chuyển động khi người dùng bật "Reduce motion".
// =====================================================================

document.addEventListener("DOMContentLoaded", function () {
    const scene = document.getElementById("weatherScene");
    if (!scene) {
        return;
    }
    const canvas = scene.querySelector("canvas");
    const context = canvas.getContext("2d");
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    // B1: đọc kiểu thời tiết và màu hạt từ token CSS
    const mood = readMood();
    const particleColor = getComputedStyle(document.body).getPropertyValue("--scene-particle").trim() || "rgba(255,255,255,0.8)";
    const settings = getMoodSettings(mood);

    let width = 0;
    let height = 0;
    let particles = [];
    let pointerX = -9999;
    let pointerY = -9999;
    let lastTime = 0;
    let running = true;

    function readMood() {
        const moods = ["storm", "rain", "snow", "wind", "hot", "cloudy", "sunny", "night"];
        for (let i = 0; i < moods.length; i++) {
            if (document.body.classList.contains("weather-" + moods[i])) {
                return moods[i];
            }
        }
        return "sunny";
    }

    // Mỗi kiểu: số hạt trên 100.000 px², kiểu vẽ, tốc độ (px/giây)
    function getMoodSettings(name) {
        if (name === "rain") {
            return { density: 7, kind: "drop", speed: 210, slant: 0.08, length: 8 };
        }
        if (name === "storm") {
            return { density: 15, kind: "drop", speed: 380, slant: 0.3, length: 13 };
        }
        if (name === "snow") {
            return { density: 9, kind: "flake", speed: 55, slant: 0, length: 0 };
        }
        if (name === "wind") {
            return { density: 2.4, kind: "streak", speed: 620, slant: 0, length: 90 };
        }
        if (name === "night") {
            // Sao nền rất nhỏ, mờ (chòm sao vẽ riêng ở drawConstellations)
            return { density: 9, kind: "star", speed: 0, slant: 0, length: 0 };
        }
        // sunny, hot, cloudy: không vẽ hạt (CSS lo mặt trời, mây) — tránh đốm tròn
        return { density: 0, kind: "none", speed: 0, slant: 0, length: 0 };
    }

    // B2: tạo hạt theo diện tích màn hình (giới hạn để máy yếu không giật)
    function createParticle(randomY) {
        return {
            x: Math.random() * width,
            y: randomY ? Math.random() * height : -20,
            z: 0.4 + Math.random() * 0.6,
            phase: Math.random() * Math.PI * 2,
            pushX: 0
        };
    }

    function resize() {
        const ratio = Math.min(window.devicePixelRatio || 1, 1.5);
        width = window.innerWidth;
        height = window.innerHeight;
        canvas.width = Math.round(width * ratio);
        canvas.height = Math.round(height * ratio);
        context.setTransform(ratio, 0, 0, ratio, 0, 0);

        const count = Math.min(Math.round(width * height / 100000 * settings.density), 320);
        particles = [];
        for (let i = 0; i < count; i++) {
            particles.push(createParticle(true));
        }
        // Giọt trên kính (mưa/giông) và lá bay (gió) tạo lại theo kích thước mới
        createGlassDrops();
        createLeaves();
    }

    // B3: cập nhật vị trí + đẩy hạt khỏi con trỏ (bán kính 110px)
    function update(particle, seconds) {
        const dx = particle.x - pointerX;
        const dy = particle.y - pointerY;
        const distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < 110 && settings.kind !== "star") {
            const force = (110 - distance) / 110;
            particle.pushX += (dx >= 0 ? 1 : -1) * force * 260 * seconds;
        }
        particle.pushX *= 0.92;

        if (settings.kind === "drop") {
            const gust = mood === "storm" ? 1 + 0.5 * Math.sin(lastTime / 2100) : 1;
            particle.y += settings.speed * particle.z * seconds;
            particle.x += settings.speed * settings.slant * gust * particle.z * seconds + particle.pushX;
        } else if (settings.kind === "flake") {
            particle.y += settings.speed * particle.z * seconds;
            particle.x += Math.sin(particle.phase + particle.y / 60) * 0.6 + particle.pushX;
        } else if (settings.kind === "streak") {
            particle.x += settings.speed * particle.z * seconds;
            particle.y += Math.sin(particle.phase + particle.x / 120) * 0.4;
        } else if (settings.kind === "mote") {
            particle.y -= settings.speed * particle.z * seconds;
            particle.x += Math.sin(particle.phase + particle.y / 80) * 0.3 + particle.pushX;
        } else {
            particle.phase += seconds * (0.8 + particle.z);
        }

        // Ra khỏi màn hình → quay lại mép đối diện
        if (particle.y > height + 30) {
            particle.y = -20;
            particle.x = Math.random() * width;
        }
        if (particle.y < -30) {
            particle.y = height + 20;
        }
        if (particle.x > width + 120) {
            particle.x = -120;
        }
        if (particle.x < -120) {
            particle.x = width + 100;
        }
    }

    function draw(particle) {
        context.globalAlpha = 0.35 + particle.z * 0.6;
        if (settings.kind === "drop") {
            context.globalAlpha = (mood === "storm" ? 0.16 : 0.1) + particle.z * 0.16;
            context.lineWidth = particle.z * (mood === "storm" ? 1.7 : 1.1);
            context.beginPath();
            context.moveTo(particle.x, particle.y);
            const slant = settings.slant * (mood === "storm" ? 1 + 0.5 * Math.sin(lastTime / 2100) : 1);
            context.lineTo(particle.x - slant * settings.length * particle.z, particle.y - settings.length * particle.z);
            context.stroke();
        } else if (settings.kind === "streak") {
            context.lineWidth = particle.z * 1.4;
            context.beginPath();
            context.moveTo(particle.x, particle.y);
            context.lineTo(particle.x - settings.length * particle.z, particle.y);
            context.stroke();
        } else if (settings.kind === "star") {
            // Sao nền: chấm 0.5–1px, mờ, lấp lánh chậm
            context.globalAlpha = 0.15 + 0.45 * Math.abs(Math.sin(particle.phase));
            context.fillRect(particle.x, particle.y * 0.75, particle.z * 1.1, particle.z * 1.1);
        } else if (settings.kind === "flake") {
            // Tinh thể tuyết 6 cánh
            const size = particle.z * 4.5;
            context.lineWidth = 1.1;
            context.beginPath();
            for (let k = 0; k < 3; k++) {
                const angle = particle.phase + k * Math.PI / 3 + particle.y / 200;
                context.moveTo(particle.x - Math.cos(angle) * size, particle.y - Math.sin(angle) * size);
                context.lineTo(particle.x + Math.cos(angle) * size, particle.y + Math.sin(angle) * size);
            }
            context.stroke();
        }
    }

    // Sao băng thỉnh thoảng vào ban đêm
    let meteor = null;
    function updateMeteor(seconds) {
        if (mood !== "night") {
            return;
        }
        if (!meteor && Math.random() < seconds * 0.12) {
            meteor = { x: Math.random() * width * 0.7 + width * 0.2, y: Math.random() * height * 0.25, life: 1 };
        }
        if (meteor) {
            meteor.x -= 700 * seconds;
            meteor.y += 260 * seconds;
            meteor.life -= seconds * 1.4;
            context.globalAlpha = Math.max(meteor.life, 0);
            context.lineWidth = 1.6;
            context.beginPath();
            context.moveTo(meteor.x, meteor.y);
            context.lineTo(meteor.x + 90, meteor.y - 34);
            context.stroke();
            if (meteor.life <= 0) {
                meteor = null;
            }
        }
    }

    // Chòm sao (đêm): Bắc Đẩu, Thiên Hậu (Cassiopeia), Lạp Hộ (Orion) — tọa độ theo tỉ lệ màn hình
    const constellations = [
        { stars: [[0.08, 0.16], [0.13, 0.13], [0.18, 0.15], [0.22, 0.19], [0.27, 0.17], [0.29, 0.25], [0.24, 0.27]], lines: [[0, 1], [1, 2], [2, 3], [3, 4], [4, 5], [5, 6], [6, 3]] },
        { stars: [[0.62, 0.08], [0.66, 0.14], [0.70, 0.09], [0.74, 0.15], [0.78, 0.10]], lines: [[0, 1], [1, 2], [2, 3], [3, 4]] },
        { stars: [[0.84, 0.30], [0.92, 0.28], [0.86, 0.40], [0.88, 0.405], [0.90, 0.41], [0.83, 0.52], [0.93, 0.50]], lines: [[0, 2], [1, 4], [2, 3], [3, 4], [2, 5], [4, 6]] }
    ];

    function drawSparkle(x, y, size) {
        context.beginPath();
        context.moveTo(x, y - size);
        context.lineTo(x + size * 0.22, y - size * 0.22);
        context.lineTo(x + size, y);
        context.lineTo(x + size * 0.22, y + size * 0.22);
        context.lineTo(x, y + size);
        context.lineTo(x - size * 0.22, y + size * 0.22);
        context.lineTo(x - size, y);
        context.lineTo(x - size * 0.22, y - size * 0.22);
        context.closePath();
        context.fill();
    }

    function drawConstellations(time) {
        if (mood !== "night") {
            return;
        }
        for (let c = 0; c < constellations.length; c++) {
            const group = constellations[c];
            // Đường nối mảnh, sáng dần theo nhịp thở
            context.globalAlpha = 0.22 + 0.12 * Math.sin(time / 1600 + c);
            context.lineWidth = 0.8;
            context.beginPath();
            for (let l = 0; l < group.lines.length; l++) {
                const from = group.stars[group.lines[l][0]];
                const to = group.stars[group.lines[l][1]];
                context.moveTo(from[0] * width, from[1] * height);
                context.lineTo(to[0] * width, to[1] * height);
            }
            context.stroke();
            // Sao 4 cánh lấp lánh lệch pha
            for (let k = 0; k < group.stars.length; k++) {
                const star = group.stars[k];
                const twinkle = 0.6 + 0.4 * Math.sin(time / 500 + k * 1.7 + c);
                context.globalAlpha = twinkle;
                drawSparkle(star[0] * width, star[1] * height, 3 + twinkle * 2.5);
            }
        }
    }

    // Kính mưa (mưa, giông): giọt đọng trên kính, thỉnh thoảng một giọt trượt xuống để lại vệt
    let glassDrops = [];
    function createGlassDrops() {
        glassDrops = [];
        if (mood !== "rain" && mood !== "storm") {
            return;
        }
        const count = Math.min(Math.round(width * height / 18000), 90);
        for (let i = 0; i < count; i++) {
            glassDrops.push({ x: Math.random() * width, y: Math.random() * height, r: 1.5 + Math.random() * 3.5, speed: 0 });
        }
    }

    function updateGlassDrops(seconds) {
        for (let i = 0; i < glassDrops.length; i++) {
            const drop = glassDrops[i];
            // Giọt to dễ trượt hơn
            if (drop.speed === 0 && drop.r > 3.2 && Math.random() < seconds * 0.06) {
                drop.speed = (mood === "storm" ? 22 : 12) + Math.random() * 24;
            }
            if (drop.speed > 0) {
                drop.y += drop.speed * seconds;
                context.globalAlpha = 0.08;
                context.lineWidth = drop.r * 0.9;
                context.beginPath();
                context.moveTo(drop.x, drop.y - drop.r * 6);
                context.lineTo(drop.x, drop.y);
                context.stroke();
                if (drop.y > height + 10) {
                    drop.y = -10;
                    drop.x = Math.random() * width;
                    drop.speed = 0;
                }
            }
            // Viền khúc xạ và lòng trong, thay cho chấm trắng đặc.
            context.save();
            context.globalAlpha = 1;
            const water = context.createRadialGradient(drop.x - drop.r * 0.3, drop.y - drop.r * 0.4, 0, drop.x, drop.y, drop.r * 1.25);
            water.addColorStop(0, "rgba(222,241,251,0.26)");
            water.addColorStop(0.3, "rgba(167,204,225,0.04)");
            water.addColorStop(0.75, "rgba(12,28,43,0.12)");
            water.addColorStop(1, "rgba(193,224,242,0.2)");
            context.fillStyle = water;
            context.beginPath();
            context.ellipse(drop.x, drop.y, drop.r * 0.8, drop.r * 1.2, -0.12, 0, Math.PI * 2);
            context.fill();
            context.strokeStyle = "rgba(218,239,252,0.32)";
            context.lineWidth = 0.65;
            context.beginPath();
            context.ellipse(drop.x, drop.y, drop.r * 0.65, drop.r, -0.12, Math.PI, Math.PI * 1.55);
            context.stroke();
            context.restore();
        }
    }

    // Lá bay khi trời gió
    let leaves = [];
    const leafColors = ["#F59E0B", "#84CC16", "#EF4444", "#FDE68A"];
    function createLeaves() {
        leaves = [];
        if (mood !== "wind") {
            return;
        }
        for (let i = 0; i < 16; i++) {
            leaves.push({ x: Math.random() * width, y: Math.random() * height, angle: Math.random() * 6.28, spin: 1 + Math.random() * 3, speed: 160 + Math.random() * 220, color: leafColors[i % leafColors.length] });
        }
    }

    function updateLeaves(seconds) {
        for (let i = 0; i < leaves.length; i++) {
            const leaf = leaves[i];
            leaf.x += leaf.speed * seconds;
            leaf.y += Math.sin(leaf.angle) * 40 * seconds;
            leaf.angle += leaf.spin * seconds;
            if (leaf.x > width + 30) {
                leaf.x = -30;
                leaf.y = Math.random() * height;
            }
            context.save();
            context.globalAlpha = 0.9;
            context.fillStyle = leaf.color;
            context.translate(leaf.x, leaf.y);
            context.rotate(leaf.angle);
            context.beginPath();
            context.ellipse(0, 0, 9, 4, 0, 0, Math.PI * 2);
            context.fill();
            context.restore();
        }
    }

    // Sấm tổng hợp âm trầm; chỉ bật âm thanh khi người dùng chọn.
    let thunderAudio = null;
    let thunderEnabled = false;
    const soundHost = document.querySelector(".wx-card__top");
    if (mood === "storm" && soundHost && !reduceMotion && (window.AudioContext || window.webkitAudioContext)) {
        const soundButton = document.createElement("button");
        soundButton.type = "button";
        soundButton.className = "wx-storm-sound";
        soundButton.textContent = "Bật tiếng sấm";
        soundButton.setAttribute("aria-pressed", "false");
        soundButton.addEventListener("click", function (event) {
            event.stopPropagation();
            thunderEnabled = !thunderEnabled;
            if (thunderEnabled) {
                thunderAudio = thunderAudio || new (window.AudioContext || window.webkitAudioContext)();
                thunderAudio.resume().catch(function () {});
            } else if (thunderAudio) {
                thunderAudio.suspend().catch(function () {});
            }
            soundButton.setAttribute("aria-pressed", String(thunderEnabled));
            soundButton.textContent = thunderEnabled ? "Tắt tiếng sấm" : "Bật tiếng sấm";
        });
        soundHost.appendChild(soundButton);
    }

    function playThunder() {
        if (!thunderEnabled || !thunderAudio || document.hidden) { return; }
        const audio = thunderAudio;
        const buffer = audio.createBuffer(1, audio.sampleRate * 3, audio.sampleRate);
        const samples = buffer.getChannelData(0);
        let rumble = 0;
        for (let i = 0; i < samples.length; i++) {
            rumble = (rumble + (Math.random() * 2 - 1) * 0.025) / 1.025;
            samples[i] = rumble * 5;
        }
        const source = audio.createBufferSource();
        source.buffer = buffer;
        const filter = audio.createBiquadFilter();
        filter.type = "lowpass";
        filter.frequency.value = 240;
        const gain = audio.createGain();
        const start = audio.currentTime + 0.55;
        gain.gain.setValueAtTime(0, audio.currentTime);
        gain.gain.linearRampToValueAtTime(0.28, start + 0.25);
        gain.gain.exponentialRampToValueAtTime(0.001, start + 3);
        source.connect(filter).connect(gain).connect(audio.destination);
        source.start(start);
        source.stop(start + 3);
        source.onended = function () { source.disconnect(); filter.disconnect(); gain.disconnect(); };
    }

    // Chớp xa, không nhấp nháy gắt trên nội dung.
    function flash() {
        if (reduceMotion || document.hidden) { return; }
        scene.classList.remove("is-flashing");
        void scene.offsetWidth;
        scene.classList.add("is-flashing");
        playThunder();
    }

    function scheduleLightning() {
        if (mood !== "storm" || reduceMotion) {
            return;
        }
        setTimeout(function () {
            if (running) {
                flash();
            }
            scheduleLightning();
        }, 10000 + Math.random() * 10000);
    }

    // B4: vòng lặp vẽ
    function frame(time) {
        if (!running) {
            return;
        }
        const seconds = Math.min((time - lastTime) / 1000 || 0, 0.05);
        lastTime = time;
        context.clearRect(0, 0, width, height);
        context.strokeStyle = particleColor;
        context.fillStyle = particleColor;
        for (let i = 0; i < particles.length; i++) {
            update(particles[i], seconds);
            draw(particles[i]);
        }
        drawConstellations(time);
        updateMeteor(seconds);
        updateGlassDrops(seconds);
        updateLeaves(seconds);
        requestAnimationFrame(frame);
    }

    resize();
    window.addEventListener("resize", resize);

    if (reduceMotion) {
        // Chỉ vẽ một khung tĩnh
        context.strokeStyle = particleColor;
        context.fillStyle = particleColor;
        for (let i = 0; i < particles.length; i++) {
            draw(particles[i]);
        }
        drawConstellations(0);
        updateGlassDrops(0);
        return;
    }

    // Parallax + "cái ô" theo con trỏ
    window.addEventListener("pointermove", function (event) {
        pointerX = event.clientX;
        pointerY = event.clientY;
        scene.style.setProperty("--px", ((event.clientX / width) - 0.5).toFixed(3));
        scene.style.setProperty("--py", ((event.clientY / height) - 0.5).toFixed(3));
    }, { passive: true });

    document.addEventListener("visibilitychange", function () {
        running = !document.hidden;
        if (running) {
            lastTime = performance.now();
            requestAnimationFrame(frame);
        }
    });

    // Bấm vào khối chào khi giông → chớp
    const hero = document.querySelector(".wx-hero");
    if (hero && mood === "storm") {
        hero.addEventListener("click", flash);
    }

    scheduleLightning();
    requestAnimationFrame(frame);
});
