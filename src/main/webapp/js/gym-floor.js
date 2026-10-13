// =====================================================================
// gym-floor.js — Phòng tập trong khối chào PT/Customer Dashboard (common/dashboard-weather.jspf)
// 4 người tập (2 nữ, 2 nam): squat đòn tạ, kettlebell swing, đẩy tạ đơn qua đầu, thừng chiến.
// Cơ thể vẽ bằng khối thon (đùi to hơn bắp chân, ngực/hông theo giới), không dùng nét que.
// Mỗi người tập theo hiệp: làm đủ số rep rồi nghỉ vài giây (thở, đứng thẳng) rồi tập tiếp.
// Gối và khuỷu tính bằng động học ngược 2 đoạn nên bàn chân luôn bám sàn.
// Chỉ trang trí (aria-hidden); Reduce motion → đứng yên; tạm dừng khi tab ẩn / khuất màn hình.
// CSS: dashboard-weather.css mục "Phòng tập".
// =====================================================================
(function () {
    "use strict";

    const SVG_NS = "http://www.w3.org/2000/svg";
    const THIGH = 24;
    const SHIN = 24;
    const TORSO = 30;
    const UPPER_ARM = 15;
    const FOREARM = 14;
    const FLOOR_Y = 124;
    const BLEND_SECONDS = 0.7;

    // ----- Toán hình học -----

    function lerp(from, to, amount) {
        return from + (to - from) * amount;
    }

    function lerpPoint(a, b, amount) {
        return [lerp(a[0], b[0], amount), lerp(a[1], b[1], amount)];
    }

    function easeInOut(t) {
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }

    // 0 → 1 → 0 trong một nhịp, chậm ở hai đầu
    function swing01(phase) {
        return (1 - Math.cos(phase * 2 * Math.PI)) / 2;
    }

    // Điểm cách (x, y) một đoạn len theo góc deg tính từ phương thẳng đứng hướng lên, dương = ngả về trước (+x)
    function along(x, y, len, deg) {
        const angle = deg * Math.PI / 180;
        return [x + len * Math.sin(angle), y - len * Math.cos(angle)];
    }

    // Khớp giữa (gối/khuỷu) của chuỗi 2 đoạn từ A tới C; bend = +1 / -1 chọn phía gập
    function middleJoint(a, c, len1, len2, bend) {
        const dx = c[0] - a[0];
        const dy = c[1] - a[1];
        const dist = Math.max(0.01, Math.min(Math.hypot(dx, dy), len1 + len2 - 0.01));
        const base = Math.atan2(dy, dx);
        const cosAngle = (len1 * len1 + dist * dist - len2 * len2) / (2 * len1 * dist);
        const angle = base + bend * Math.acos(Math.max(-1, Math.min(1, cosAngle)));
        return [a[0] + len1 * Math.cos(angle), a[1] + len1 * Math.sin(angle)];
    }

    function fmt(value) {
        return value.toFixed(2);
    }

    // Khối thon bo tròn 2 đầu từ p (bán kính r1) tới q (bán kính r2): dùng cho đùi, bắp chân, cánh tay
    function capsule(p, q, r1, r2) {
        const dx = q[0] - p[0];
        const dy = q[1] - p[1];
        const len = Math.max(Math.hypot(dx, dy), 0.001);
        const nx = -dy / len;
        const ny = dx / len;
        return "M" + fmt(p[0] + nx * r1) + " " + fmt(p[1] + ny * r1)
            + " L" + fmt(q[0] + nx * r2) + " " + fmt(q[1] + ny * r2)
            + " A" + r2 + " " + r2 + " 0 0 0 " + fmt(q[0] - nx * r2) + " " + fmt(q[1] - ny * r2)
            + " L" + fmt(p[0] - nx * r1) + " " + fmt(p[1] - ny * r1)
            + " A" + r1 + " " + r1 + " 0 0 0 " + fmt(p[0] + nx * r1) + " " + fmt(p[1] + ny * r1) + " Z";
    }

    // Nội suy độ dày thân theo bảng mốc [vị trí 0..1, độ dày]
    function profileAt(table, s) {
        for (let i = 1; i < table.length; i++) {
            if (s <= table[i][0]) {
                const span = (s - table[i - 1][0]) / (table[i][0] - table[i - 1][0]);
                const smooth = (1 - Math.cos(span * Math.PI)) / 2;
                return lerp(table[i - 1][1], table[i][1], smooth);
            }
        }
        return table[table.length - 1][1];
    }

    // Thân người từ hông tới vai, chỉ lấy đoạn s0..s1 (để tô áo / quần riêng)
    function torsoBand(hip, shoulder, body, s0, s1) {
        const ux = (shoulder[0] - hip[0]) / TORSO;
        const uy = (shoulder[1] - hip[1]) / TORSO;
        const fx = -uy;
        const fy = ux;
        const steps = 8;
        const front = [];
        const back = [];
        for (let i = 0; i <= steps; i++) {
            const s = lerp(s0, s1, i / steps);
            const cx = hip[0] + ux * TORSO * s;
            const cy = hip[1] + uy * TORSO * s;
            const f = profileAt(body.front, s);
            const b = profileAt(body.back, s);
            front.push(fmt(cx + fx * f) + " " + fmt(cy + fy * f));
            back.unshift(fmt(cx - fx * b) + " " + fmt(cy - fy * b));
        }
        return "M" + front.join(" L") + " L" + back.join(" L") + " Z";
    }

    // ----- Vóc dáng nam / nữ -----

    const BODIES = {
        female: {
            front: [[0, 5.2], [0.3, 4.1], [0.55, 4.6], [0.74, 7.2], [0.9, 5.2], [1, 4.4]],
            back: [[0, 7.6], [0.25, 5.4], [0.5, 4.6], [0.8, 5.4], [1, 5.2]],
            thigh: [5.6, 3.9], shin: [3.8, 2.3], upper: [3.1, 2.4], fore: [2.4, 1.9], head: 5.9,
            clothes: [["legwear", 0, 0.3], ["top", 0.6, 0.93]]
        },
        male: {
            front: [[0, 5.4], [0.35, 5.2], [0.7, 7.6], [0.9, 6.4], [1, 5.6]],
            back: [[0, 6.6], [0.35, 5.4], [0.75, 7.2], [1, 6.4]],
            thigh: [6, 4.3], shin: [4.2, 2.6], upper: [4.1, 3], fore: [3.1, 2.3], head: 6.3,
            clothes: [["top", 0.2, 1.02], ["legwear", 0, 0.27]]
        }
    };

    // ----- Dựng hình -----

    function create(parent, tag, className) {
        const node = document.createElementNS(SVG_NS, tag);
        if (className) {
            node.setAttribute("class", className);
        }
        parent.appendChild(node);
        return node;
    }

    function buildRig(svg, gender) {
        const rig = { gender: gender, body: BODIES[gender] };
        rig.shadow = create(svg, "ellipse", "ath-shadow");
        rig.gearBack = create(svg, "g", "ath-gear");
        rig.back = create(svg, "g", "ath-back");
        rig.backShin = create(rig.back, "path", "ath-skin");
        rig.backThigh = create(rig.back, "path", "ath-legwear");
        rig.backShoe = create(rig.back, "path", "ath-shoe");
        rig.backArm = create(rig.back, "path", "ath-skin");
        rig.backFore = create(rig.back, "path", "ath-skin");
        rig.ponytail = create(svg, "path", "ath-hair");
        rig.torso = create(svg, "path", "ath-skin");
        rig.clothes = [];
        for (let i = 0; i < rig.body.clothes.length; i++) {
            rig.clothes.push(create(svg, "path", "ath-" + rig.body.clothes[i][0]));
        }
        rig.neck = create(svg, "path", "ath-skin");
        rig.head = create(svg, "circle", "ath-skin");
        rig.hair = create(svg, "path", "ath-hair");
        rig.eye = create(svg, "circle", "ath-eye");
        rig.frontShin = create(svg, "path", "ath-skin");
        rig.frontThigh = create(svg, "path", "ath-legwear");
        rig.frontShoe = create(svg, "path", "ath-shoe");
        rig.frontArm = create(svg, "path", "ath-skin");
        rig.frontFore = create(svg, "path", "ath-skin");
        rig.gearFront = create(svg, "g", "ath-gear");
        // Nữ mặc legging dài: bắp chân cùng màu quần
        if (gender === "female") {
            rig.backShin.setAttribute("class", "ath-legwear");
            rig.frontShin.setAttribute("class", "ath-legwear");
        }
        rig.lastHead = null;
        rig.hairStyle = svg.parentNode.dataset.hair || (gender === "female" ? "ponytail" : "buzz");
        rig.tailAngle = 30;
        return rig;
    }

    function drawLeg(thighNode, shinNode, shoeNode, hip, foot, body) {
        const knee = middleJoint(hip, foot, THIGH, SHIN, -1);
        thighNode.setAttribute("d", capsule(hip, knee, body.thigh[0], body.thigh[1]));
        shinNode.setAttribute("d", capsule(knee, [foot[0], foot[1] - 2], body.shin[0], body.shin[1]));
        shoeNode.setAttribute("d", capsule([foot[0] - 1.5, foot[1] - 1.6], [foot[0] + 6.5, foot[1] - 1.2], 2.6, 2));
    }

    function drawArm(upperNode, foreNode, shoulder, hand, body, bend) {
        const elbow = middleJoint(shoulder, hand, UPPER_ARM, FOREARM, bend);
        upperNode.setAttribute("d", capsule(shoulder, elbow, body.upper[0], body.upper[1]));
        foreNode.setAttribute("d", capsule(elbow, hand, body.fore[0], body.fore[1] + 0.3));
    }

    function drawPose(rig, pose, dt) {
        const body = rig.body;
        const hip = pose.hip;
        const shoulder = pose.shoulder;
        const top = along(hip[0], hip[1], TORSO + 1.5, pose.torso);
        const headCenter = along(top[0], top[1], body.head + 2.5, pose.torso * 0.5);

        drawLeg(rig.backThigh, rig.backShin, rig.backShoe, hip, pose.backFoot, body);
        drawLeg(rig.frontThigh, rig.frontShin, rig.frontShoe, hip, pose.frontFoot, body);
        drawArm(rig.backArm, rig.backFore, shoulder, pose.backHand, body, pose.elbowBend);
        drawArm(rig.frontArm, rig.frontFore, shoulder, pose.frontHand, body, pose.elbowBend);

        rig.torso.setAttribute("d", torsoBand(hip, top, body, 0, 1));
        for (let i = 0; i < body.clothes.length; i++) {
            rig.clothes[i].setAttribute("d", torsoBand(hip, top, body, body.clothes[i][1], body.clothes[i][2]));
        }
        rig.neck.setAttribute("d", capsule(top, headCenter, 2.4, 2.2));
        rig.headPos = headCenter;
        rig.head.setAttribute("cx", fmt(headCenter[0]));
        rig.head.setAttribute("cy", fmt(headCenter[1]));
        rig.head.setAttribute("r", body.head);

        // Tóc: ôm nửa sau đỉnh đầu; mắt ở phía trước mặt
        const r = body.head + 0.6;
        const tilt = pose.torso * 0.5 * Math.PI / 180;
        const frontTop = [headCenter[0] + r * Math.sin(tilt + 0.5), headCenter[1] - r * Math.cos(tilt + 0.5)];
        const backLow = [headCenter[0] - r * Math.cos(tilt - 0.35), headCenter[1] + r * Math.sin(tilt - 0.35) * -1 + 1];
        rig.hair.setAttribute("d", "M" + fmt(frontTop[0]) + " " + fmt(frontTop[1])
            + " A" + r + " " + r + " 0 0 0 " + fmt(backLow[0]) + " " + fmt(backLow[1])
            + " Q" + fmt(headCenter[0] - 1) + " " + fmt(headCenter[1] - 1) + " " + fmt(frontTop[0]) + " " + fmt(frontTop[1]) + " Z");
        const eye = along(headCenter[0], headCenter[1], body.head * 0.55, pose.torso * 0.5 + 78);
        rig.eye.setAttribute("cx", fmt(eye[0]));
        rig.eye.setAttribute("cy", fmt(eye[1] - 1));
        rig.eye.setAttribute("r", 0.85);

        // Kiểu tóc riêng từng người (data-hair trên .wx-athlete):
        //   ponytail đuôi ngựa lắc theo nhịp · bun búi tó trên đỉnh · buzz đầu đinh sát · cap mũ lưỡi trai quay ngược
        if (rig.hairStyle === "bun") {
            const top = along(headCenter[0], headCenter[1], body.head + 1.2, pose.torso * 0.5 - 25);
            rig.ponytail.setAttribute("d", capsule(top, top, 3.2, 3.2));
        } else if (rig.hairStyle === "buzz") {
            rig.hair.setAttribute("d", "M" + fmt(frontTop[0]) + " " + fmt(frontTop[1])
                + " A" + r + " " + r + " 0 0 0 " + fmt(backLow[0]) + " " + fmt(backLow[1])
                + " Q" + fmt(headCenter[0] - 2.5) + " " + fmt(headCenter[1] - 3.5) + " " + fmt(frontTop[0]) + " " + fmt(frontTop[1]) + " Z");
        } else if (rig.hairStyle === "cap") {
            const brimFrom = along(headCenter[0], headCenter[1], r, pose.torso * 0.5 - 70);
            const brimTo = [brimFrom[0] - 6.5, brimFrom[1] + 1.2];
            rig.ponytail.setAttribute("d", capsule(brimFrom, brimTo, 1.1, 1));
            rig.ponytail.setAttribute("class", "ath-cap");
            rig.hair.setAttribute("class", "ath-cap");
        }
        // Đuôi ngựa: lắc theo chuyển động của đầu
        if (rig.hairStyle === "ponytail") {
            if (rig.lastHead && dt > 0) {
                const vx = (headCenter[0] - rig.lastHead[0]) / dt;
                const vy = (headCenter[1] - rig.lastHead[1]) / dt;
                const target = Math.max(5, Math.min(70, 28 + vx * 0.9 - vy * 0.5));
                rig.tailAngle += (target - rig.tailAngle) * Math.min(1, dt * 6);
            }
            rig.lastHead = headCenter;
            const root = [headCenter[0] - body.head * 0.8, headCenter[1] - body.head * 0.55];
            const angle = (180 + rig.tailAngle) * Math.PI / 180;
            const tip = [root[0] + 11 * Math.sin(angle) * -1, root[1] + 11 * Math.cos(angle) * -1];
            rig.ponytail.setAttribute("d", capsule(root, tip, 2.4, 1.2));
        }

        rig.shadow.setAttribute("cx", fmt((pose.frontFoot[0] + pose.backFoot[0]) / 2 + 2));
        rig.shadow.setAttribute("cy", FLOOR_Y + 1);
        rig.shadow.setAttribute("rx", 21);
        rig.shadow.setAttribute("ry", 2.6);

    }

    function setCircle(node, center, radius) {
        node.setAttribute("cx", fmt(center[0]));
        node.setAttribute("cy", fmt(center[1]));
        node.setAttribute("r", radius);
    }

    // Bánh tạ cao su nhìn chính diện: vành ngoài, vạch màu, lõi thép
    function buildPlate(parent, className) {
        const group = create(parent, "g", className);
        return {
            rim: create(group, "circle", "ath-plate"),
            band: create(group, "circle", "ath-plate-band"),
            hub: create(group, "circle", "ath-hub"),
            sleeve: create(group, "circle", "ath-sleeve")
        };
    }

    function drawPlate(plate, center, radius) {
        setCircle(plate.rim, center, radius);
        setCircle(plate.band, center, radius * 0.72);
        setCircle(plate.hub, center, radius * 0.3);
        setCircle(plate.sleeve, center, radius * 0.16);
    }

    // Lục giác của tạ đơn đầu lục giác
    function hexagon(center, radius) {
        const points = [];
        for (let i = 0; i < 6; i++) {
            const angle = Math.PI / 6 + i * Math.PI / 3;
            points.push(fmt(center[0] + radius * Math.cos(angle)) + "," + fmt(center[1] + radius * Math.sin(angle)));
        }
        return points.join(" ");
    }

    // ----- 4 bài tập -----

    const EXERCISES = {
        // Nữ — squat đòn tạ trên lưng (đòn nhìn chính diện: bánh gần to, bánh xa lệch sau)
        squat: {
            gender: "female",
            period: 3.1,
            reps: 6,
            restSeconds: 4.5,
            label: "rep",
            build: function (rig) {
                rig.farPlate = buildPlate(rig.gearBack, "ath-plate-far");
                rig.nearPlate = buildPlate(rig.gearFront, "");
            },
            pose: function (phase) {
                const depth = swing01(phase);
                return {
                    hip: [58 - 15 * depth, 76 + 21 * depth],
                    torso: 5 + 33 * depth,
                    frontFoot: [60, FLOOR_Y],
                    backFoot: [57, FLOOR_Y],
                    elbowBend: 1
                };
            },
            rest: function (breath) {
                return {
                    hip: [58, 76],
                    torso: 5 + breath,
                    frontFoot: [60, FLOOR_Y],
                    backFoot: [57, FLOOR_Y],
                    elbowBend: 1
                };
            },
            gear: function (rig, pose) {
                // Đòn tựa trên cơ thang (sau vai, thấp hơn gáy) để vẫn thấy rõ đầu
                const bar = along(pose.shoulder[0], pose.shoulder[1], 7, pose.torso - 112);
                pose.frontHand = [bar[0] + 9, bar[1] + 2];
                pose.backHand = [bar[0] + 7, bar[1] + 3];
                drawPlate(rig.farPlate, [bar[0] - 5, bar[1] - 3], 10.5);
                drawPlate(rig.nearPlate, bar, 11.5);
            }
        },
        // Nam — kettlebell swing: gập hông rồi bật hông đưa tạ lên ngang vai
        swing: {
            gender: "male",
            period: 1.8,
            reps: 10,
            restSeconds: 4,
            label: "rep",
            build: function (rig) {
                rig.handle = create(rig.gearFront, "path", "ath-bell-handle");
                rig.bell = create(rig.gearFront, "path", "ath-bell");
            },
            pose: function (phase) {
                const lift = swing01(phase);
                const hip = [50 + 7 * lift, 90 - 13 * lift];
                return {
                    hip: hip,
                    torso: 56 - 52 * lift,
                    frontFoot: [58, FLOOR_Y],
                    backFoot: [54, FLOOR_Y],
                    arm: lerp(-30, 88, lift),
                    elbowBend: -1
                };
            },
            rest: function (breath) {
                return {
                    hip: [57, 77],
                    torso: 3 + breath,
                    frontFoot: [58, FLOOR_Y],
                    backFoot: [54, FLOOR_Y],
                    arm: 4,
                    elbowBend: -1
                };
            },
            gear: function (rig, pose) {
                const arm = pose.arm * Math.PI / 180;
                const dirX = Math.sin(arm);
                const dirY = Math.cos(arm);
                const hand = [pose.shoulder[0] + 27.5 * dirX, pose.shoulder[1] + 27.5 * dirY];
                pose.frontHand = hand;
                pose.backHand = [hand[0] - 1.5, hand[1] + 1];
                const bell = [hand[0] + 9 * dirX, hand[1] + 9 * dirY];
                // Quai cầm hình chữ U + thân tạ tròn đáy phẳng
                rig.handle.setAttribute("d", capsule([hand[0] - 2 * dirY, hand[1] + 2 * dirX], [bell[0] - 3 * dirY, bell[1] + 3 * dirX], 1.2, 1.2)
                    + " " + capsule([hand[0] + 2 * dirY, hand[1] - 2 * dirX], [bell[0] + 3 * dirY, bell[1] - 3 * dirX], 1.2, 1.2));
                rig.bell.setAttribute("d", capsule([bell[0] - 0.5 * dirX, bell[1] - 0.5 * dirY], [bell[0] + 2 * dirX, bell[1] + 2 * dirY], 7, 6.4));
            }
        },
        // Nữ — đẩy tạ đơn lục giác qua đầu, chân hơi nhún lấy lực
        press: {
            gender: "female",
            period: 2.5,
            reps: 8,
            restSeconds: 4,
            label: "rep",
            build: function (rig) {
                rig.bellBack = create(rig.gearBack, "polygon", "ath-hex ath-plate-far");
                rig.bellFront = create(rig.gearFront, "polygon", "ath-hex");
                rig.bellFrontHub = create(rig.gearFront, "circle", "ath-sleeve");
            },
            pose: function (phase) {
                const press = swing01(phase);
                const hand = [lerp(67, 62, press), lerp(37, 18, press)];
                return {
                    hip: [59, 76 + 1.5 * Math.sin(phase * 2 * Math.PI)],
                    torso: 2,
                    frontFoot: [62, FLOOR_Y],
                    backFoot: [56, FLOOR_Y],
                    frontHand: hand,
                    backHand: [hand[0] - 3, hand[1] + 1],
                    elbowBend: 1
                };
            },
            rest: function (breath) {
                return {
                    hip: [59, 76],
                    torso: 2 + breath,
                    frontFoot: [62, FLOOR_Y],
                    backFoot: [56, FLOOR_Y],
                    frontHand: [65, 63],
                    backHand: [62, 64],
                    elbowBend: 1
                };
            },
            gear: function (rig, pose) {
                rig.bellBack.setAttribute("points", hexagon([pose.backHand[0] - 2, pose.backHand[1] - 1.5], 5.6));
                rig.bellFront.setAttribute("points", hexagon(pose.frontHand, 6));
                setCircle(rig.bellFrontHub, pose.frontHand, 1.6);
            }
        },
        // Nam — thừng chiến: nửa ngồi, hai tay quật xen kẽ, sóng chạy dọc dây tới cọc neo
        ropes: {
            gender: "male",
            period: 0.9,
            reps: 20,
            restSeconds: 4,
            label: "time",
            build: function (rig) {
                rig.ropeBack = create(rig.gearBack, "polyline", "ath-rope ath-rope-far");
                rig.ropeFront = create(rig.gearFront, "polyline", "ath-rope");
                const anchor = create(rig.gearBack, "rect", "ath-anchor");
                anchor.setAttribute("x", 158);
                anchor.setAttribute("y", 100);
                anchor.setAttribute("width", 8);
                anchor.setAttribute("height", 24);
                anchor.setAttribute("rx", 1.5);
            },
            pose: function (phase) {
                const beat = Math.sin(phase * 2 * Math.PI);
                return {
                    hip: [34, 95],
                    torso: 28,
                    frontFoot: [46, FLOOR_Y],
                    backFoot: [28, FLOOR_Y],
                    frontHand: [70, 86 + 9 * beat],
                    backHand: [68, 86 - 9 * beat],
                    elbowBend: 1,
                    amplitude: 8,
                    phase: phase
                };
            },
            rest: function (breath) {
                return {
                    hip: [36, 80],
                    torso: 6 + breath,
                    frontFoot: [44, FLOOR_Y],
                    backFoot: [32, FLOOR_Y],
                    frontHand: [45, 98],
                    backHand: [42, 99],
                    elbowBend: 1,
                    amplitude: 0,
                    phase: 0
                };
            },
            gear: function (rig, pose) {
                const anchor = [160, 112];
                const ropes = [[rig.ropeBack, pose.backHand, 0.5], [rig.ropeFront, pose.frontHand, 0]];
                for (let r = 0; r < ropes.length; r++) {
                    const hand = ropes[r][1];
                    const points = [];
                    for (let i = 0; i <= 32; i++) {
                        const u = i / 32;
                        const envelope = (1 - u) * Math.min(1, u / 0.12);
                        // Lúc nghỉ dây buông xuống sàn
                        const slack = (1 - pose.amplitude / 8) * Math.sin(u * Math.PI) * (FLOOR_Y - 2 - lerp(hand[1], anchor[1], u)) * 0.95;
                        const wave = pose.amplitude * envelope * Math.sin(2 * Math.PI * (1.7 * u - pose.phase - ropes[r][2]));
                        points.push(fmt(lerp(hand[0], anchor[0], u)) + "," + fmt(lerp(hand[1], anchor[1], u) + wave + slack));
                    }
                    ropes[r][0].setAttribute("points", points.join(" "));
                }
            }
        }
    };

    // Trộn 2 tư thế (chuyển mượt giữa tập và nghỉ)
    function blendPose(a, b, amount) {
        const out = {};
        for (const key in a) {
            if (Array.isArray(a[key]) && b[key]) {
                out[key] = lerpPoint(a[key], b[key], amount);
            } else if (typeof a[key] === "number" && typeof b[key] === "number") {
                out[key] = lerp(a[key], b[key], amount);
            } else {
                out[key] = a[key];
            }
        }
        return out;
    }

    function pad2(value) {
        return (value < 10 ? "0" : "") + value;
    }

    // ----- Khởi động -----

    document.addEventListener("DOMContentLoaded", function () {
        const scene = document.querySelector("[data-gym]");
        if (!scene) {
            return;
        }
        const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

        // B1: dựng khung cho từng người tập; lệch thời gian để không ai nghỉ cùng lúc
        const crew = [];
        const nodes = scene.querySelectorAll("[data-athlete]");
        for (let i = 0; i < nodes.length; i++) {
            const exercise = EXERCISES[nodes[i].dataset.athlete];
            if (!exercise) {
                continue;
            }
            const rig = buildRig(nodes[i].querySelector("svg"), exercise.gender);
            exercise.build(rig);
            crew.push({ exercise: exercise, rig: rig, offset: i * 3.3, chip: nodes[i].querySelector(".wx-athlete__rep"),
                svg: nodes[i].querySelector("svg") });
        }

        // B2: vẽ toàn bộ tại thời điểm `seconds`
        function render(seconds, dt) {
            for (let i = 0; i < crew.length; i++) {
                const member = crew[i];
                const ex = member.exercise;
                const workTime = ex.reps * ex.period;
                const cycle = workTime + ex.restSeconds;
                const local = (seconds + member.offset) % cycle;
                const breath = Math.sin(seconds * 2.6) * 1.2;
                let pose;
                let chipText;
                if (local < workTime) {
                    const phase = (local / ex.period) % 1;
                    pose = ex.pose(phase);
                    chipText = ex.label === "time"
                        ? "0:" + pad2(Math.floor(local))
                        : "REP " + pad2(Math.floor(local / ex.period) + 1) + "/" + pad2(ex.reps);
                } else {
                    // Nghỉ: chuyển mượt từ cuối hiệp sang tư thế nghỉ và ngược lại trước hiệp sau
                    const restTime = local - workTime;
                    const blend = easeInOut(Math.min(1, restTime / BLEND_SECONDS, (ex.restSeconds - restTime) / BLEND_SECONDS));
                    pose = blendPose(ex.pose(0), ex.rest(breath), blend);
                    chipText = "NGHỈ 0:" + pad2(Math.ceil(ex.restSeconds - restTime));
                }
                pose.shoulder = along(pose.hip[0], pose.hip[1], TORSO, pose.torso);
                ex.gear(member.rig, pose);
                drawPose(member.rig, pose, dt);
                if (member.chip) {
                    member.chip.textContent = chipText;
                }
            }
        }


        // B4: khẩu lệnh ngắn theo nhịp tập, không dùng khẩu hiệu của thương hiệu khác.
        const SPEECH_EVERY = 12000;
        const SPEECH_FOR = 3000;
        const speechLines = [
            "Giữ nhịp. Giữ form.",
            "Tập chắc từng rep.",
            "Đẩy hết nhịp này!",
            "Kiểm soát nhịp thở.",
            "Vai vững. Lưng thẳng.",
            "Thêm một rep chuẩn.",
            "Chân bám sàn. Mắt nhìn trước.",
            "Nâng có kiểm soát.",
            "Nghỉ đủ rồi tiếp tục.",
            "Lực đến từ kỹ thuật."
        ];
        const bubble = document.createElement("span");
        bubble.className = "wx-gym__speech";
        bubble.hidden = true;
        scene.appendChild(bubble);
        let speechPool = [];
        let speaker = null;
        let speechTimer = 0;

        // Đặt bong bóng trên đầu người đang nói (đổi toạ độ viewBox của SVG sang px trong phòng tập)
        function placeBubble() {
            if (!speaker || !speaker.rig.headPos) {
                return;
            }
            const box = speaker.svg.viewBox.baseVal;
            const svgRect = speaker.svg.getBoundingClientRect();
            const sceneRect = scene.getBoundingClientRect();
            const scale = svgRect.height / box.height;
            const x = svgRect.left - sceneRect.left + (speaker.rig.headPos[0] - box.x) * scale;
            const y = svgRect.top - sceneRect.top + (speaker.rig.headPos[1] - box.y - 10) * scale;
            const half = bubble.offsetWidth / 2;
            const left = Math.max(half + 6, Math.min(x, sceneRect.width - half - 6));
            bubble.style.left = left + "px";
            bubble.style.top = Math.max(bubble.offsetHeight + 6, y) + "px";
            bubble.style.setProperty("--tail", (x - left + half) + "px");
        }

        function speak() {
            if (document.hidden || !visible || reduceMotion || document.body.classList.contains("dashboard-motion-paused")) {
                return;
            }
            const shown = crew.filter(function (member) {
                return member.svg.getBoundingClientRect().width > 0;
            });
            if (!shown.length) {
                return;
            }
            if (!speechPool.length) {
                speechPool = speechLines.slice();
            }
            speaker = shown[Math.floor(Math.random() * shown.length)];
            bubble.textContent = speechPool.splice(Math.floor(Math.random() * speechPool.length), 1)[0];
            bubble.hidden = false;
            placeBubble();
            bubble.classList.add("is-on");
            clearTimeout(speechTimer);
            speechTimer = setTimeout(function () {
                bubble.classList.remove("is-on");
                bubble.hidden = true;
                speaker = null;
            }, SPEECH_FOR);
        }

        if (reduceMotion) {
            render(1.1, 0);
            return;
        }

        // B3: chạy theo khung hình; rê chuột vào phòng tập thì tăng nhịp; dừng khi khuất màn hình hoặc tab ẩn
        let visible = true;
        let speed = 1;
        let clock = 0;
        let last = 0;
        let frame = 0;

        function tick(now) {
            frame = 0;
            let dt = 0;
            if (last) {
                dt = Math.min((now - last) / 1000, 0.1) * speed;
                clock += dt;
            }
            last = now;
            render(clock, dt);
            placeBubble();
            schedule();
        }

        function schedule() {
            if (!frame && visible && !document.hidden && !document.body.classList.contains("dashboard-motion-paused")) {
                frame = requestAnimationFrame(tick);
            }
        }

        // Cảnh không bắt pointer để nút tạm dừng luôn dùng được; panel cha điều khiển nhịp hover.
        const panel = scene.closest(".wx-gym-panel") || scene;
        panel.addEventListener("mouseenter", function () { speed = 1.35; });
        panel.addEventListener("mouseleave", function () { speed = 1; });
        document.addEventListener("visibilitychange", function () {
            last = 0;
            schedule();
        });
        document.addEventListener("dashboardmotionchange", function () {
            last = 0;
            if (document.body.classList.contains("dashboard-motion-paused")) {
                bubble.classList.remove("is-on");
                bubble.hidden = true;
                speaker = null;
            }
            schedule();
        });
        if ("IntersectionObserver" in window) {
            new IntersectionObserver(function (entries) {
                visible = entries[0].isIntersecting;
                last = 0;
                schedule();
            }).observe(scene);
        }
        render(0, 0);
        schedule();
        setTimeout(speak, 2500);
        setInterval(speak, SPEECH_EVERY);
    });
})();
