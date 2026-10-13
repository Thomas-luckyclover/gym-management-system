// =====================================================================
// weather-card.js — Hiệu ứng thật trong thẻ thời tiết (common/dashboard-weather.jspf, .wx-card__canvas)
// Vẽ bằng canvas theo kiểu thời tiết trên <body class="weather-*">: phông trời + đường chân trời thành phố
// (đèn cửa sổ sáng khi trời tối/mưa), rồi hiệu ứng thời tiết phủ lên:
//   mưa      vệt mưa chéo mờ dần + giọt nước bám kính có bóng/điểm sáng, giọt lớn trượt để lại vệt
//   giông    mưa dày hơn + chớp lóe sáng cả thẻ, tia sét phân nhánh
//   tuyết    bông tuyết nhiều lớp (gần to/mờ, xa nhỏ/sắc), tuyết đọng mép dưới
//   nắng/nóng ánh sáng thể tích mềm từ góc, quầng mặt trời, vệt lóe ngang, bóng ma lục giác; nóng có hơi nóng bốc lên
//   mây      khối mây mềm nhiều lớp trôi ngang
//   gió      luồng gió cong lướt nhanh + lá cuốn
//   đêm      trăng có quầng + mây mỏng trôi qua + vài sao nhỏ đứng yên + sao băng hiếm
// Chỉ trang trí (aria-hidden). Reduce motion → vẽ 1 khung tĩnh. Dừng khi tab ẩn hoặc thẻ khuất màn hình.
// =====================================================================
(function () {
    "use strict";

    function rand(min, max) {
        return min + Math.random() * (max - min);
    }

    // ----- Mưa / giông -----
    function createRain(width, height, storm) {
        const streaks = [];
        const beads = [];
        const slides = [];
        const angle = storm ? -0.24 : -0.12;
        for (let i = 0; i < (storm ? 150 : 95); i++) {
            streaks.push({ x: rand(-40, width + 40), y: rand(-height, height), len: rand(12, storm ? 34 : 26),
                speed: rand(storm ? 780 : 520, storm ? 1150 : 860), alpha: rand(0.08, 0.3), width: rand(0.7, 1.4) });
        }
        for (let i = 0; i < 34; i++) {
            beads.push({ x: rand(0, width), y: rand(0, height), r: rand(1.2, 4.2) });
        }
        let flash = 0;
        let bolt = null;
        let nextStrike = rand(2, 5);

        function makeBolt() {
            const points = [[rand(width * 0.35, width * 0.85), -4]];
            let x = points[0][0];
            let y = -4;
            while (y < height * rand(0.55, 0.8)) {
                x += rand(-18, 14);
                y += rand(10, 22);
                points.push([x, y]);
            }
            const branch = [points[Math.floor(points.length / 2)].slice()];
            for (let i = 0; i < 4; i++) {
                const last = branch[branch.length - 1];
                branch.push([last[0] + rand(6, 18), last[1] + rand(8, 16)]);
            }
            return { main: points, branch: branch, life: 0.32 };
        }

        function drawBead(ctx, x, y, r) {
            // Bóng đổ của giọt, thân giọt trong suốt có viền sáng, điểm phản chiếu trên trái
            ctx.fillStyle = "rgba(0, 0, 0, 0.28)";
            ctx.beginPath();
            ctx.arc(x + r * 0.25, y + r * 0.35, r, 0, Math.PI * 2);
            ctx.fill();
            const body = ctx.createRadialGradient(x - r * 0.2, y - r * 0.3, r * 0.1, x, y, r);
            body.addColorStop(0, "rgba(220, 235, 255, 0.05)");
            body.addColorStop(0.75, "rgba(210, 228, 250, 0.12)");
            body.addColorStop(1, "rgba(235, 245, 255, 0.42)");
            ctx.fillStyle = body;
            ctx.beginPath();
            ctx.arc(x, y, r, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = "rgba(255, 255, 255, 0.75)";
            ctx.beginPath();
            ctx.arc(x - r * 0.35, y - r * 0.4, Math.max(0.5, r * 0.28), 0, Math.PI * 2);
            ctx.fill();
        }

        return function draw(ctx, dt) {
            // Vệt mưa phía sau kính
            ctx.lineCap = "round";
            for (let i = 0; i < streaks.length; i++) {
                const s = streaks[i];
                s.y += s.speed * dt;
                s.x += s.speed * dt * angle;
                if (s.y - s.len > height) {
                    s.y = rand(-60, -10);
                    s.x = rand(-20, width + 60);
                }
                const tailX = s.x - s.len * angle;
                const tailY = s.y - s.len;
                const gradient = ctx.createLinearGradient(tailX, tailY, s.x, s.y);
                gradient.addColorStop(0, "rgba(200, 222, 245, 0)");
                gradient.addColorStop(1, "rgba(215, 232, 250, " + s.alpha + ")");
                ctx.strokeStyle = gradient;
                ctx.lineWidth = s.width;
                ctx.beginPath();
                ctx.moveTo(tailX, tailY);
                ctx.lineTo(s.x, s.y);
                ctx.stroke();
            }

            // Sương mờ dưới đáy thẻ
            const fog = ctx.createLinearGradient(0, height * 0.55, 0, height);
            fog.addColorStop(0, "rgba(160, 185, 210, 0)");
            fog.addColorStop(1, "rgba(160, 185, 210, 0.12)");
            ctx.fillStyle = fog;
            ctx.fillRect(0, 0, width, height);

            // Giọt lớn trượt xuống, nuốt giọt nhỏ trên đường đi, để lại vệt ướt
            if (slides.length < 2 && Math.random() < dt * 0.5) {
                slides.push({ x: rand(width * 0.1, width * 0.9), y: rand(-10, height * 0.3), r: rand(3.4, 5.2),
                    speed: rand(26, 60), trail: [], wobble: rand(0, 6) });
            }
            for (let i = slides.length - 1; i >= 0; i--) {
                const d = slides[i];
                d.y += d.speed * dt * (0.6 + Math.random() * 0.8);
                d.x += Math.sin((d.y + d.wobble * 20) / 18) * 0.35;
                d.trail.push([d.x, d.y]);
                if (d.trail.length > 70) {
                    d.trail.shift();
                }
                for (let j = 0; j < beads.length; j++) {
                    const b = beads[j];
                    if (Math.hypot(b.x - d.x, b.y - d.y) < d.r + b.r) {
                        b.x = rand(0, width);
                        b.y = rand(0, height * 0.4);
                    }
                }
                for (let j = 0; j < d.trail.length; j += 6) {
                    ctx.fillStyle = "rgba(225, 240, 255, " + (0.05 + j / d.trail.length * 0.1) + ")";
                    ctx.beginPath();
                    ctx.arc(d.trail[j][0], d.trail[j][1], d.r * 0.38, 0, Math.PI * 2);
                    ctx.fill();
                }
                drawBead(ctx, d.x, d.y, d.r);
                if (d.y - d.r > height) {
                    slides.splice(i, 1);
                }
            }
            for (let i = 0; i < beads.length; i++) {
                drawBead(ctx, beads[i].x, beads[i].y, beads[i].r);
            }

            if (!storm) {
                return;
            }
            // Chớp: lóe trắng tím rồi tắt nhanh, có lúc lóe 2 lần
            nextStrike -= dt;
            if (nextStrike <= 0) {
                bolt = makeBolt();
                flash = 1;
                nextStrike = rand(4, 9);
            }
            if (flash > 0) {
                const pulse = flash > 0.55 ? flash : flash * (0.6 + 0.4 * Math.sin(flash * 40));
                ctx.fillStyle = "rgba(205, 210, 255, " + (0.42 * pulse) + ")";
                ctx.fillRect(0, 0, width, height);
                flash = Math.max(0, flash - dt * 2.4);
            }
            if (bolt && bolt.life > 0) {
                ctx.save();
                ctx.shadowColor = "rgba(190, 200, 255, 0.95)";
                ctx.shadowBlur = 14;
                ctx.strokeStyle = "rgba(245, 247, 255, " + Math.min(1, bolt.life * 4) + ")";
                ctx.lineJoin = "round";
                [bolt.main, bolt.branch].forEach(function (line, index) {
                    ctx.lineWidth = index === 0 ? 2.2 : 1.1;
                    ctx.beginPath();
                    ctx.moveTo(line[0][0], line[0][1]);
                    for (let k = 1; k < line.length; k++) {
                        ctx.lineTo(line[k][0], line[k][1]);
                    }
                    ctx.stroke();
                });
                ctx.restore();
                bolt.life -= dt;
            }
        };
    }

    // ----- Tuyết -----
    function createSnow(width, height) {
        const flakes = [];
        for (let i = 0; i < 80; i++) {
            const z = Math.random();
            flakes.push({ x: rand(0, width), y: rand(-height, height), z: z, r: 0.7 + z * 2.8,
                speed: 14 + z * 46, phase: rand(0, 6.28) });
        }
        let time = 0;
        return function draw(ctx, dt) {
            time += dt;
            for (let i = 0; i < flakes.length; i++) {
                const f = flakes[i];
                f.y += f.speed * dt;
                f.x += Math.sin(time * 0.9 + f.phase) * (6 + f.z * 10) * dt;
                if (f.y - f.r > height) {
                    f.y = -f.r - rand(0, 20);
                    f.x = rand(0, width);
                }
                // Bông gần: to và mềm (ngoài tiêu cự); bông xa: nhỏ và sắc
                const soft = f.r * (1 + f.z * 1.6);
                const flake = ctx.createRadialGradient(f.x, f.y, 0, f.x, f.y, soft);
                flake.addColorStop(0, "rgba(255, 255, 255, " + (0.55 + f.z * 0.35) + ")");
                flake.addColorStop(0.55, "rgba(245, 250, 255, " + (0.25 + f.z * 0.2) + ")");
                flake.addColorStop(1, "rgba(245, 250, 255, 0)");
                ctx.fillStyle = flake;
                ctx.beginPath();
                ctx.arc(f.x, f.y, soft, 0, Math.PI * 2);
                ctx.fill();
            }
            // Tuyết đọng mép dưới, mặt trên gợn
            ctx.fillStyle = "rgba(240, 247, 255, 0.28)";
            ctx.beginPath();
            ctx.moveTo(0, height);
            for (let x = 0; x <= width; x += 8) {
                ctx.lineTo(x, height - 7 - Math.sin(x / 23) * 2.5 - Math.sin(x / 9) * 1.2);
            }
            ctx.lineTo(width, height);
            ctx.closePath();
            ctx.fill();
        };
    }

    // ----- Nắng / nóng -----
    // Ánh sáng thể tích: chùm tia vẽ ở độ phân giải thấp rồi phóng lên → mềm như xuyên qua không khí ẩm,
    // không có hạt bụi bay. Thêm quầng mặt trời, vệt lóe ngang kiểu ống kính anamorphic và vài bóng ma lục giác rất nhạt.
    function createSun(width, height, hot) {
        const sunX = width * 0.9;
        const sunY = -height * 0.06;
        const scale = 0.25;
        const haze = document.createElement("canvas");
        haze.width = Math.max(1, Math.round(width * scale));
        haze.height = Math.max(1, Math.round(height * scale));
        const hazeCtx = haze.getContext("2d");
        const rays = [];
        for (let i = 0; i < 18; i++) {
            rays.push({
                angle: 1.62 + (i / 17) * 1.2 + rand(-0.03, 0.03),
                spread: rand(0.012, 0.045),
                strength: rand(0.35, 1),
                speed: rand(0.15, 0.45),
                phase: rand(0, 6.28)
            });
        }
        const warm = hot ? "255, 168, 96" : "255, 222, 170";
        let time = 0;

        function hexagon(ctx, x, y, r) {
            ctx.beginPath();
            for (let k = 0; k < 6; k++) {
                const a = Math.PI / 6 + k * Math.PI / 3;
                if (k === 0) {
                    ctx.moveTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
                } else {
                    ctx.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
                }
            }
            ctx.closePath();
        }

        return function draw(ctx, dt) {
            time += dt;

            // B1: không khí ấm loang từ góc mặt trời
            const air = ctx.createRadialGradient(sunX, sunY, 0, sunX, sunY, Math.max(width, height) * 1.1);
            air.addColorStop(0, "rgba(" + warm + ", 0.38)");
            air.addColorStop(0.3, "rgba(" + warm + ", 0.12)");
            air.addColorStop(1, "rgba(" + warm + ", 0)");
            ctx.fillStyle = air;
            ctx.fillRect(0, 0, width, height);

            // B2: chùm tia trên lớp nhỏ → phóng to = mềm, thở chậm theo từng tia
            const w = haze.width;
            const h = haze.height;
            const cx = sunX * scale;
            const cy = sunY * scale;
            const length = Math.hypot(w, h) * 1.4;
            hazeCtx.clearRect(0, 0, w, h);
            hazeCtx.globalCompositeOperation = "lighter";
            for (let i = 0; i < rays.length; i++) {
                const ray = rays[i];
                const breathe = 0.55 + 0.45 * Math.sin(time * ray.speed + ray.phase);
                const a = ray.angle + Math.sin(time * 0.05 + ray.phase) * 0.015;
                const g = hazeCtx.createLinearGradient(cx, cy, cx + Math.cos(a) * length, cy + Math.sin(a) * length);
                g.addColorStop(0, "rgba(" + warm + ", " + (0.22 * ray.strength * breathe).toFixed(3) + ")");
                g.addColorStop(0.55, "rgba(" + warm + ", " + (0.06 * ray.strength * breathe).toFixed(3) + ")");
                g.addColorStop(1, "rgba(" + warm + ", 0)");
                hazeCtx.fillStyle = g;
                hazeCtx.beginPath();
                hazeCtx.moveTo(cx, cy);
                hazeCtx.lineTo(cx + Math.cos(a - ray.spread) * length, cy + Math.sin(a - ray.spread) * length);
                hazeCtx.lineTo(cx + Math.cos(a + ray.spread) * length, cy + Math.sin(a + ray.spread) * length);
                hazeCtx.closePath();
                hazeCtx.fill();
            }
            ctx.save();
            ctx.globalCompositeOperation = "screen";
            ctx.imageSmoothingEnabled = true;
            ctx.drawImage(haze, 0, 0, width, height);

            // B3: quầng mặt trời ở mép thẻ
            const corona = ctx.createRadialGradient(sunX, sunY, 0, sunX, sunY, height * 0.55);
            corona.addColorStop(0, "rgba(255, 252, 240, 0.85)");
            corona.addColorStop(0.12, "rgba(255, 236, 200, 0.5)");
            corona.addColorStop(0.4, "rgba(" + warm + ", 0.14)");
            corona.addColorStop(1, "rgba(" + warm + ", 0)");
            ctx.fillStyle = corona;
            ctx.fillRect(0, 0, width, height);

            // B4: vệt lóe ngang mảnh qua tâm mặt trời, sáng nhẹ lên xuống
            const streakAlpha = 0.16 + 0.06 * Math.sin(time * 0.8);
            const streak = ctx.createLinearGradient(sunX - width * 0.9, 0, sunX + width * 0.2, 0);
            streak.addColorStop(0, "rgba(" + warm + ", 0)");
            streak.addColorStop(0.75, "rgba(255, 240, 215, " + streakAlpha.toFixed(3) + ")");
            streak.addColorStop(1, "rgba(" + warm + ", 0)");
            ctx.fillStyle = streak;
            ctx.fillRect(0, Math.max(0, sunY + height * 0.1) - 1, width, 2);

            // B5: bóng ma ống kính lục giác dọc đường chéo đối diện, hơi ánh aqua/cam
            const ghosts = [[0.42, 10, "127, 230, 236", 0.07], [0.62, 18, "255, 190, 140", 0.05], [0.86, 30, "127, 230, 236", 0.04]];
            for (let i = 0; i < ghosts.length; i++) {
                const t = ghosts[i][0];
                const gx = sunX + (width * 0.12 - sunX) * t;
                const gy = sunY + (height * 0.92 - sunY) * t;
                ctx.fillStyle = "rgba(" + ghosts[i][2] + ", " + (ghosts[i][3] * (0.8 + 0.2 * Math.sin(time + i))).toFixed(3) + ")";
                hexagon(ctx, gx, gy, ghosts[i][1]);
                ctx.fill();
            }
            ctx.restore();

            if (!hot) {
                return;
            }
            // B6: trời nóng — hơi nóng gợn sóng bốc lên từ đáy thẻ
            ctx.strokeStyle = "rgba(255, 196, 140, 0.06)";
            ctx.lineWidth = 8;
            for (let band = 0; band < 4; band++) {
                const baseY = height - ((time * 18 + band * height / 4) % height);
                ctx.beginPath();
                for (let x = 0; x <= width; x += 6) {
                    const y = baseY + Math.sin(x / 30 + time * 2.4 + band) * 3;
                    if (x === 0) {
                        ctx.moveTo(x, y);
                    } else {
                        ctx.lineTo(x, y);
                    }
                }
                ctx.stroke();
            }
        };
    }

    // ----- Mây (dùng cho nhiều mây và gió) -----
    function createClouds(width, height, count, alpha, speedScale) {
        const clouds = [];
        for (let i = 0; i < count; i++) {
            const puffs = [];
            const size = rand(46, 90);
            for (let p = 0; p < 9; p++) {
                puffs.push([rand(-size, size), rand(-size * 0.25, size * 0.2), rand(size * 0.35, size * 0.7)]);
            }
            clouds.push({ x: rand(-width * 0.2, width), y: rand(height * 0.05, height * 0.75), puffs: puffs,
                speed: rand(6, 16) * speedScale, depth: rand(0.5, 1) });
        }
        return function draw(ctx, dt) {
            for (let i = 0; i < clouds.length; i++) {
                const c = clouds[i];
                c.x += c.speed * c.depth * dt;
                if (c.x - 140 > width) {
                    c.x = -160;
                    c.y = rand(height * 0.05, height * 0.75);
                }
                for (let p = 0; p < c.puffs.length; p++) {
                    const puff = c.puffs[p];
                    const px = c.x + puff[0];
                    const py = c.y + puff[1];
                    const g = ctx.createRadialGradient(px, py - puff[2] * 0.2, 0, px, py, puff[2]);
                    g.addColorStop(0, "rgba(232, 238, 246, " + alpha * c.depth + ")");
                    g.addColorStop(0.6, "rgba(200, 212, 226, " + alpha * 0.5 * c.depth + ")");
                    g.addColorStop(1, "rgba(190, 204, 220, 0)");
                    ctx.fillStyle = g;
                    ctx.beginPath();
                    ctx.arc(px, py, puff[2], 0, Math.PI * 2);
                    ctx.fill();
                }
            }
        };
    }

    // ----- Gió -----
    function createWind(width, height) {
        const clouds = createClouds(width, height, 3, 0.1, 3);
        const gusts = [];
        for (let i = 0; i < 26; i++) {
            gusts.push({ x: rand(-width, width), y: rand(0, height), len: rand(60, 160), speed: rand(240, 420),
                curve: rand(-14, 14), alpha: rand(0.1, 0.26) });
        }
        const leaves = [];
        for (let i = 0; i < 6; i++) {
            leaves.push({ x: rand(-width, 0), y: rand(0, height), spin: rand(0, 6.28), speed: rand(140, 230), size: rand(3, 5) });
        }
        let time = 0;
        return function draw(ctx, dt) {
            time += dt;
            clouds(ctx, dt);
            ctx.lineCap = "round";
            for (let i = 0; i < gusts.length; i++) {
                const g = gusts[i];
                g.x += g.speed * dt;
                if (g.x - g.len > width) {
                    g.x = rand(-200, -40);
                    g.y = rand(0, height);
                }
                const grad = ctx.createLinearGradient(g.x - g.len, g.y, g.x, g.y);
                grad.addColorStop(0, "rgba(220, 238, 248, 0)");
                grad.addColorStop(0.7, "rgba(220, 238, 248, " + g.alpha + ")");
                grad.addColorStop(1, "rgba(220, 238, 248, 0)");
                ctx.strokeStyle = grad;
                ctx.lineWidth = 1.1;
                ctx.beginPath();
                ctx.moveTo(g.x - g.len, g.y);
                ctx.quadraticCurveTo(g.x - g.len / 2, g.y + g.curve, g.x, g.y);
                ctx.stroke();
            }
            for (let i = 0; i < leaves.length; i++) {
                const l = leaves[i];
                l.x += l.speed * dt;
                l.y += Math.sin(time * 3 + l.spin) * 30 * dt;
                l.spin += dt * 6;
                if (l.x > width + 20) {
                    l.x = rand(-300, -20);
                    l.y = rand(0, height);
                }
                ctx.save();
                ctx.translate(l.x, l.y);
                ctx.rotate(l.spin);
                ctx.scale(1, Math.abs(Math.sin(l.spin)) * 0.8 + 0.2);
                ctx.fillStyle = "rgba(120, 150, 90, 0.7)";
                ctx.beginPath();
                ctx.ellipse(0, 0, l.size * 1.6, l.size * 0.7, 0, 0, Math.PI * 2);
                ctx.fill();
                ctx.restore();
            }
        };
    }

    // ----- Đêm -----
    // Trăng có quầng và vân mặt trăng, mây mỏng trôi ngang qua ánh trăng, vài ngôi sao rất nhỏ đứng yên nhấp nháy,
    // thỉnh thoảng một sao băng. Không có hạt bay.
    function createNight(width, height) {
        const moonX = width * 0.82;
        const moonY = height * 0.2;
        const moonR = Math.min(width, height) * 0.09;
        const stars = [];
        for (let i = 0; i < 26; i++) {
            stars.push({ x: rand(0, width * 0.7), y: rand(0, height * 0.6), r: rand(0.4, 0.9), speed: rand(0.4, 1.4), phase: rand(0, 6.28) });
        }
        const veils = [];
        for (let i = 0; i < 3; i++) {
            veils.push({ x: rand(-width * 0.3, width), y: moonY + rand(-moonR, moonR * 2.2), w: rand(width * 0.35, width * 0.6),
                h: rand(10, 20), speed: rand(4, 9), alpha: rand(0.1, 0.18) });
        }
        let meteor = null;
        let nextMeteor = rand(4, 8);
        let time = 0;
        return function draw(ctx, dt) {
            time += dt;
            // B1: quầng trăng lạnh
            const halo = ctx.createRadialGradient(moonX, moonY, moonR * 0.8, moonX, moonY, moonR * 7);
            halo.addColorStop(0, "rgba(200, 225, 245, 0.28)");
            halo.addColorStop(0.35, "rgba(127, 200, 230, 0.08)");
            halo.addColorStop(1, "rgba(127, 200, 230, 0)");
            ctx.fillStyle = halo;
            ctx.fillRect(0, 0, width, height);

            // B2: sao nhỏ đứng yên, nhấp nháy chậm
            for (let i = 0; i < stars.length; i++) {
                const s = stars[i];
                const a = 0.25 + 0.5 * (0.5 + 0.5 * Math.sin(time * s.speed + s.phase));
                ctx.fillStyle = "rgba(230, 240, 255, " + a.toFixed(3) + ")";
                ctx.fillRect(s.x, s.y, s.r * 2, s.r * 2);
            }

            // B3: mặt trăng — đĩa sáng, mép tối nhẹ, vài vân xám
            const disc = ctx.createRadialGradient(moonX - moonR * 0.3, moonY - moonR * 0.3, moonR * 0.1, moonX, moonY, moonR);
            disc.addColorStop(0, "rgba(250, 252, 255, 0.98)");
            disc.addColorStop(0.75, "rgba(222, 232, 242, 0.95)");
            disc.addColorStop(1, "rgba(185, 202, 220, 0.9)");
            ctx.fillStyle = disc;
            ctx.beginPath();
            ctx.arc(moonX, moonY, moonR, 0, Math.PI * 2);
            ctx.fill();
            ctx.fillStyle = "rgba(150, 168, 190, 0.22)";
            [[-0.3, -0.1, 0.28], [0.25, 0.25, 0.2], [0.05, -0.4, 0.14], [-0.1, 0.42, 0.12]].forEach(function (m) {
                ctx.beginPath();
                ctx.arc(moonX + m[0] * moonR, moonY + m[1] * moonR, m[2] * moonR, 0, Math.PI * 2);
                ctx.fill();
            });

            // B4: mây mỏng trôi qua, được trăng soi sáng mép
            for (let i = 0; i < veils.length; i++) {
                const v = veils[i];
                v.x += v.speed * dt;
                if (v.x > width + v.w * 0.5) {
                    v.x = -v.w;
                }
                const g = ctx.createRadialGradient(v.x, v.y, 0, v.x, v.y, v.w * 0.5);
                g.addColorStop(0, "rgba(190, 210, 230, " + v.alpha + ")");
                g.addColorStop(1, "rgba(190, 210, 230, 0)");
                ctx.save();
                ctx.translate(v.x, v.y);
                ctx.scale(1, v.h / (v.w * 0.5));
                ctx.translate(-v.x, -v.y);
                ctx.fillStyle = g;
                ctx.beginPath();
                ctx.arc(v.x, v.y, v.w * 0.5, 0, Math.PI * 2);
                ctx.fill();
                ctx.restore();
            }

            // B5: sao băng hiếm
            nextMeteor -= dt;
            if (!meteor && nextMeteor <= 0) {
                meteor = { x: rand(width * 0.35, width * 0.9), y: rand(0, height * 0.25), life: 0.8 };
                nextMeteor = rand(7, 14);
            }
            if (meteor) {
                meteor.x -= 300 * dt;
                meteor.y += 110 * dt;
                meteor.life -= dt;
                const tail = ctx.createLinearGradient(meteor.x, meteor.y, meteor.x + 80, meteor.y - 30);
                tail.addColorStop(0, "rgba(245, 248, 255, " + Math.max(0, meteor.life).toFixed(3) + ")");
                tail.addColorStop(1, "rgba(245, 248, 255, 0)");
                ctx.strokeStyle = tail;
                ctx.lineWidth = 1.2;
                ctx.beginPath();
                ctx.moveTo(meteor.x, meteor.y);
                ctx.lineTo(meteor.x + 80, meteor.y - 30);
                ctx.stroke();
                if (meteor.life <= 0) {
                    meteor = null;
                }
            }
        };
    }

    // ----- Phông nền: bầu trời theo thời tiết + đường chân trời thành phố 3 lớp (xa → gần) -----
    // Vẽ sẵn lớp nhà vào canvas phụ một lần mỗi khi đổi cỡ; mỗi khung chỉ chép lại + bật/tắt đèn cửa sổ.
    const SKIES = {
        sunny: ["#0C5466", "#2F8D9A", "#F2B27E"],
        hot: ["#6E2A12", "#D85E28", "#FFC07A"],
        cloudy: ["#1C3B47", "#4C6B75", "#94AAB0"],
        rain: ["#0B212B", "#1F3D48", "#46616B"],
        storm: ["#050D12", "#14222A", "#2A3A42"],
        snow: ["#2C4754", "#7894A0", "#C6D4D9"],
        wind: ["#134150", "#3B7C88", "#B8D3D0"],
        night: ["#01080D", "#052029", "#0D3846"]
    };

    function createBackdrop(type, width, height) {
        const sky = SKIES[type] || SKIES.cloudy;
        const lit = type === "night" || type === "storm" || type === "rain" || type === "cloudy";
        const layer = document.createElement("canvas");
        const ratio = Math.min(window.devicePixelRatio || 1, 2);
        layer.width = Math.round(width * ratio);
        layer.height = Math.round(height * ratio);
        const lctx = layer.getContext("2d");
        lctx.setTransform(ratio, 0, 0, ratio, 0, 0);
        const windows = [];

        // Một dãy nhà: các khối chữ nhật cao thấp xen kẽ, thỉnh thoảng có ăng-ten hoặc mái vát
        function row(baseY, minH, maxH, minW, maxW, fill, collectWindows) {
            lctx.fillStyle = fill;
            let x = -rand(0, 20);
            while (x < width + 10) {
                const w = rand(minW, maxW);
                const h = rand(minH, maxH) * height;
                const top = baseY - h;
                lctx.fillRect(x, top, w, h + 2);
                if (Math.random() < 0.25) {
                    lctx.fillRect(x + w * 0.45, top - h * 0.18, 2, h * 0.18);
                }
                if (Math.random() < 0.2) {
                    lctx.beginPath();
                    lctx.moveTo(x, top);
                    lctx.lineTo(x + w * 0.5, top - h * 0.12);
                    lctx.lineTo(x + w, top);
                    lctx.closePath();
                    lctx.fill();
                }
                if (collectWindows) {
                    for (let wy = top + 6; wy < baseY - 6; wy += 7) {
                        for (let wx = x + 4; wx < x + w - 4; wx += 6) {
                            if (Math.random() < 0.28) {
                                windows.push({ x: wx, y: wy, on: Math.random() < 0.55, phase: rand(0, 6.28) });
                            }
                        }
                    }
                }
                x += w + rand(0, 4);
            }
        }

        const ground = height + 2;
        row(ground, 0.22, 0.42, 18, 40, "rgba(10, 40, 52, 0.55)", false);
        // Tòa tháp điểm nhấn ở xa bên trái
        lctx.fillStyle = "rgba(10, 40, 52, 0.6)";
        lctx.fillRect(width * 0.16, ground - height * 0.58, width * 0.045, height * 0.58);
        lctx.fillRect(width * 0.16 + width * 0.02, ground - height * 0.66, 2, height * 0.08);
        row(ground, 0.14, 0.3, 22, 46, "#072631", true);
        row(ground, 0.05, 0.13, 30, 70, "#03131A", false);

        return function draw(ctx, time) {
            const g = ctx.createLinearGradient(0, 0, 0, height);
            g.addColorStop(0, sky[0]);
            g.addColorStop(0.62, sky[1]);
            g.addColorStop(1, sky[2]);
            ctx.fillStyle = g;
            ctx.fillRect(0, 0, width, height);

            ctx.drawImage(layer, 0, 0, width, height);

            // Đèn cửa sổ: trời tối thì vàng ấm và thỉnh thoảng tắt/bật; trời sáng chỉ là mảng kính phản chiếu
            for (let i = 0; i < windows.length; i++) {
                const win = windows[i];
                if (lit) {
                    if (!win.on) {
                        continue;
                    }
                    const flicker = 0.55 + 0.35 * Math.sin(time * 0.3 + win.phase);
                    ctx.fillStyle = "rgba(255, 186, 112, " + flicker.toFixed(3) + ")";
                } else {
                    ctx.fillStyle = "rgba(191, 243, 245, 0.16)";
                }
                ctx.fillRect(win.x, win.y, 2.5, 3);
            }

            // Lớp tối nhẹ bên trái/trên để chữ trắng luôn đọc rõ
            const veil = ctx.createLinearGradient(0, 0, width * 0.75, 0);
            veil.addColorStop(0, "rgba(2, 10, 14, 0.5)");
            veil.addColorStop(1, "rgba(2, 10, 14, 0)");
            ctx.fillStyle = veil;
            ctx.fillRect(0, 0, width, height);
        };
    }

    function createScene(type, width, height) {
        switch (type) {
            case "rain": return createRain(width, height, false);
            case "storm": return createRain(width, height, true);
            case "snow": return createSnow(width, height);
            case "sunny": return createSun(width, height, false);
            case "hot": return createSun(width, height, true);
            case "wind": return createWind(width, height);
            case "night": return createNight(width, height);
            default: return createClouds(width, height, 5, 0.16, 1);
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        const canvas = document.querySelector(".wx-card__canvas");
        if (!canvas || !canvas.getContext) {
            return;
        }
        const match = document.body.className.match(/\bweather-(\w+)/);
        const type = match ? match[1] : "cloudy";
        const ctx = canvas.getContext("2d");
        if (!ctx) {
            return;
        }
        const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        let width = 0;
        let height = 0;
        let draw = null;
        let backdrop = null;
        let clock = 0;

        // B1: canvas theo đúng cỡ thẻ, giới hạn mật độ điểm ảnh để nhẹ máy
        function resize() {
            const rect = canvas.getBoundingClientRect();
            const ratio = Math.min(window.devicePixelRatio || 1, 2);
            width = Math.max(1, Math.round(rect.width));
            height = Math.max(1, Math.round(rect.height));
            canvas.width = width * ratio;
            canvas.height = height * ratio;
            ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
            backdrop = createBackdrop(type, width, height);
            draw = createScene(type, width, height);
        }

        function frame(dt) {
            clock += dt;
            ctx.clearRect(0, 0, width, height);
            backdrop(ctx, clock);
            draw(ctx, dt);
        }

        resize();
        if (reduceMotion) {
            frame(0.016);
            return;
        }

        // B2: vòng vẽ; dừng khi tab ẩn hoặc thẻ khuất màn hình
        let visible = true;
        let last = 0;
        let handle = 0;
        function tick(now) {
            handle = 0;
            const dt = last ? Math.min((now - last) / 1000, 0.05) : 0.016;
            last = now;
            frame(dt);
            schedule();
        }
        function schedule() {
            if (!handle && visible && !document.hidden && !document.body.classList.contains("dashboard-motion-paused")) {
                handle = requestAnimationFrame(tick);
            }
        }
        document.addEventListener("visibilitychange", function () {
            last = 0;
            schedule();
        });
        document.addEventListener("dashboardmotionchange", function () {
            last = 0;
            schedule();
        });
        if ("IntersectionObserver" in window) {
            new IntersectionObserver(function (entries) {
                visible = entries[0].isIntersecting;
                last = 0;
                schedule();
            }).observe(canvas);
        }
        let resizeTimer = 0;
        window.addEventListener("resize", function () {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(resize, 200);
        });
        // Mở chi tiết thời tiết làm thẻ cao hơn: canvas theo lại kích thước thực.
        const details = document.querySelector(".wx-card__more");
        if (details) {
            details.addEventListener("toggle", function () {
                resize();
                frame(0.016);
            });
        }
        frame(0.016);
        schedule();
    });
})();
