/* Ink figure rig for the Super Gym comic scenes.
 * Every person is drawn from joints: thick/thin brush contours are generated around
 * shoulder-elbow-wrist and hip-knee-ankle chains, so limbs stay attached in every frame.
 * Pure string output (no DOM) so the same code pre-renders the static fallback in Node.
 * Local space: feet on y = 0, up is negative y, the figure faces +x. A wrapper flips it. */
(function (root) {
    "use strict";

    var PI = Math.PI;
    function f(n) { return (Math.round(n * 100) / 100).toString(); }
    function pt(p) { return f(p[0]) + " " + f(p[1]); }
    function ease(x) { return x * x * (3 - 2 * x); }
    function clamp(x, a, b) { return Math.max(a, Math.min(b, x)); }

    /* Two-bone solver. bend=+1 puts the middle joint on the +90deg side of a->b (SVG y-down). */
    function ik(a, b, l1, l2, bend) {
        var dx = b[0] - a[0], dy = b[1] - a[1];
        var d = Math.max(0.001, Math.hypot(dx, dy));
        var reach = l1 + l2 - 0.05;
        var dd = Math.min(d, reach);
        var along = (l1 * l1 - l2 * l2 + dd * dd) / (2 * dd);
        var h = Math.sqrt(Math.max(0, l1 * l1 - along * along)) * bend;
        var ux = dx / d, uy = dy / d;
        return [a[0] + ux * along - uy * h, a[1] + uy * along + ux * h];
    }
    /* Clamp a target so the chain never over-stretches. */
    function reachable(a, b, l1, l2) {
        var dx = b[0] - a[0], dy = b[1] - a[1], d = Math.hypot(dx, dy), m = l1 + l2 - 0.2;
        return d > m ? [a[0] + dx / d * m, a[1] + dy / d * m] : b;
    }
    function polar(p, len, ang) { return [p[0] + Math.sin(ang) * len, p[1] + Math.cos(ang) * len]; }

    /* ---------- smooth closed path through points (Catmull-Rom -> cubic) ---------- */
    function smoothClosed(P) {
        var n = P.length, s = "M" + pt(P[0]);
        for (var i = 0; i < n; i++) {
            var p0 = P[(i - 1 + n) % n], p1 = P[i], p2 = P[(i + 1) % n], p3 = P[(i + 2) % n];
            var c1 = [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6];
            var c2 = [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6];
            s += "C" + pt(c1) + " " + pt(c2) + " " + pt(p2);
        }
        return s + "Z";
    }

    /* ---------- limb ribbon ----------
     * pts: joints [[x,y],...]; prof: [[p, wA, wB], ...] where p = segment index + fraction,
     * wA is the half-width on the +normal side (rotate tangent +90deg), wB on the other side. */
    function centerline(pts, prof) {
        var n = pts.length, seg = [], i, out = [];
        for (i = 0; i < n - 1; i++) {
            var dx = pts[i + 1][0] - pts[i][0], dy = pts[i + 1][1] - pts[i][1], L = Math.hypot(dx, dy) || 0.001;
            seg.push({ ux: dx / L, uy: dy / L, L: L });
        }
        var trimIn = [], trimOut = [];
        for (i = 0; i < n - 1; i++) { trimIn.push(0); trimOut.push(0); }
        for (i = 1; i < n - 1; i++) {
            var w = 3; for (var k = 0; k < prof.length; k++) { if (Math.abs(prof[k][0] - i) < 0.6) { w = Math.max(w, prof[k][1] + prof[k][2]); } }
            var c = Math.min(w * 1.1, seg[i - 1].L * 0.42, seg[i].L * 0.42);
            trimOut[i - 1] = c; trimIn[i] = c;
        }
        for (i = 0; i < n - 1; i++) {
            var s0 = trimIn[i], s1 = seg[i].L - trimOut[i];
            var steps = Math.max(1, Math.ceil((s1 - s0) / 4));
            for (var j = 0; j <= steps; j++) {
                var d = s0 + (s1 - s0) * j / steps;
                out.push({ x: pts[i][0] + seg[i].ux * d, y: pts[i][1] + seg[i].uy * d, p: i + d / seg[i].L });
            }
            if (i < n - 2 && trimOut[i] > 0) {
                var A = [pts[i + 1][0] - seg[i].ux * trimOut[i], pts[i + 1][1] - seg[i].uy * trimOut[i]];
                var B = [pts[i + 1][0] + seg[i + 1].ux * trimIn[i + 1], pts[i + 1][1] + seg[i + 1].uy * trimIn[i + 1]];
                for (var q = 1; q < 5; q++) {
                    var t = q / 5, m = 1 - t;
                    out.push({
                        x: m * m * A[0] + 2 * m * t * pts[i + 1][0] + t * t * B[0],
                        y: m * m * A[1] + 2 * m * t * pts[i + 1][1] + t * t * B[1],
                        p: i + 1 + (t - 0.5) * 0.12
                    });
                }
            }
        }
        return out;
    }
    function widthAt(prof, p) {
        if (p <= prof[0][0]) { return [prof[0][1], prof[0][2]]; }
        for (var i = 1; i < prof.length; i++) {
            if (p <= prof[i][0]) {
                var t = ease((p - prof[i - 1][0]) / Math.max(0.0001, prof[i][0] - prof[i - 1][0]));
                return [prof[i - 1][1] + (prof[i][1] - prof[i - 1][1]) * t, prof[i - 1][2] + (prof[i][2] - prof[i - 1][2]) * t];
            }
        }
        var e = prof[prof.length - 1]; return [e[1], e[2]];
    }
    function ribbon(pts, prof, opt) {
        opt = opt || {};
        var C = centerline(pts, prof), n = C.length, L = [], R = [], i;
        var T = [], N = [];
        for (i = 0; i < n; i++) {
            var a = C[Math.max(0, i - 1)], b = C[Math.min(n - 1, i + 1)];
            var tx = b.x - a.x, ty = b.y - a.y, l = Math.hypot(tx, ty) || 1;
            T.push([tx / l, ty / l]); N.push([-ty / l, tx / l]);
        }
        for (i = 0; i < n; i++) {
            var w = widthAt(prof, C[i].p);
            L.push([C[i].x + N[i][0] * w[0], C[i].y + N[i][1] * w[0]]);
            R.push([C[i].x - N[i][0] * w[1], C[i].y - N[i][1] * w[1]]);
        }
        var ring = [], k, ph, w0 = widthAt(prof, C[0].p), w1 = widthAt(prof, C[n - 1].p);
        for (i = 0; i < n; i++) { ring.push(L[i]); }
        var c1 = [C[n - 1].x + N[n - 1][0] * (w1[0] - w1[1]) / 2, C[n - 1].y + N[n - 1][1] * (w1[0] - w1[1]) / 2], r1 = (w1[0] + w1[1]) / 2;
        for (k = 1; k < 4; k++) {
            ph = PI * k / 4;
            ring.push([c1[0] + r1 * (N[n - 1][0] * Math.cos(ph) + T[n - 1][0] * Math.sin(ph)), c1[1] + r1 * (N[n - 1][1] * Math.cos(ph) + T[n - 1][1] * Math.sin(ph))]);
        }
        for (i = n - 1; i >= 0; i--) { ring.push(R[i]); }
        var c0 = [C[0].x + N[0][0] * (w0[0] - w0[1]) / 2, C[0].y + N[0][1] * (w0[0] - w0[1]) / 2], r0 = (w0[0] + w0[1]) / 2;
        for (k = 1; k < 4; k++) {
            ph = PI * k / 4;
            ring.push([c0[0] - r0 * (N[0][0] * Math.cos(ph) + T[0][0] * Math.sin(ph)), c0[1] - r0 * (N[0][1] * Math.cos(ph) + T[0][1] * Math.sin(ph))]);
        }
        var res = { d: smoothClosed(ring), L: L, R: R, C: C };
        /* shade strip: the side away from the light, sliding inward from the contour */
        if (opt.shade) {
            var side = opt.shade === "A" ? L : R, sgn = opt.shade === "A" ? 1 : -1, strip = [], j;
            var from = Math.floor(n * 0.08), to = Math.ceil(n * 0.92);
            for (j = from; j < to; j++) { strip.push(side[j]); }
            for (j = to - 1; j >= from; j--) {
                var ww = widthAt(prof, C[j].p), wx = ww[opt.shade === "A" ? 0 : 1];
                strip.push([C[j].x + N[j][0] * sgn * wx * 0.38, C[j].y + N[j][1] * sgn * wx * 0.38]);
            }
            res.shade = smoothClosed(strip);
        }
        return res;
    }

    /* ---------- torso: spine base -> neck base with different front/back thickness ----------
     * front/back arrays: half-depth at s = 0 (pelvis) ... 1 (neck base), evenly spaced. */
    function sampleArr(arr, s) {
        var x = clamp(s, 0, 1) * (arr.length - 1), i = Math.min(arr.length - 2, Math.floor(x)), t = ease(x - i);
        return arr[i] + (arr[i + 1] - arr[i]) * t;
    }
    function torso(base, top, bow, front, back, s0, s1, round) {
        /* bow bends the spine forward (positive) as a quadratic curve */
        var dx = top[0] - base[0], dy = top[1] - base[1], L = Math.hypot(dx, dy) || 1;
        var nx = -dy / L, ny = dx / L; /* forward normal: +x for a spine pointing up */
        var ctrl = [(base[0] + top[0]) / 2 + nx * bow, (base[1] + top[1]) / 2 + ny * bow];
        var F = [], B = [], i, steps = 14;
        s0 = s0 || 0; s1 = s1 === undefined ? 1 : s1;
        for (i = 0; i <= steps; i++) {
            var s = s0 + (s1 - s0) * i / steps, m = 1 - s;
            var x = m * m * base[0] + 2 * m * s * ctrl[0] + s * s * top[0];
            var y = m * m * base[1] + 2 * m * s * ctrl[1] + s * s * top[1];
            var tx = 2 * m * (ctrl[0] - base[0]) + 2 * s * (top[0] - ctrl[0]);
            var ty = 2 * m * (ctrl[1] - base[1]) + 2 * s * (top[1] - ctrl[1]);
            var tl = Math.hypot(tx, ty) || 1, px = -ty / tl, py = tx / tl;
            F.push([x + px * sampleArr(front, s), y + py * sampleArr(front, s)]);
            B.push([x - px * sampleArr(back, s), y - py * sampleArr(back, s)]);
        }
        var ring = [];
        for (i = 0; i <= steps; i++) { ring.push(F[i]); }
        for (i = steps; i >= 0; i--) { ring.push(B[i]); }
        if (s0 === 0 && !round) {
            /* rounded seat of the pelvis instead of a flat cut */
            var fx = F[0], bx = B[0], mx = (fx[0] + bx[0]) / 2, my = (fx[1] + bx[1]) / 2;
            var ux = (base[0] - top[0]) / L, uy = (base[1] - top[1]) / L, half = Math.hypot(fx[0] - bx[0], fx[1] - bx[1]) / 2;
            ring.push([mx + ux * half * 0.55 + (bx[0] - mx) * 0.55, my + uy * half * 0.55 + (bx[1] - my) * 0.55]);
            ring.push([mx + ux * half * 0.78, my + uy * half * 0.78]);
            ring.push([mx + ux * half * 0.55 + (fx[0] - mx) * 0.55, my + uy * half * 0.55 + (fx[1] - my) * 0.55]);
        }
        return { d: smoothClosed(ring), F: F, B: B, ctrl: ctrl };
    }
    function spinePoint(base, top, bow, s) {
        var dx = top[0] - base[0], dy = top[1] - base[1], L = Math.hypot(dx, dy) || 1;
        var nx = -dy / L, ny = dx / L, m = 1 - s;
        var cx = (base[0] + top[0]) / 2 + nx * bow, cy = (base[1] + top[1]) / 2 + ny * bow;
        return [m * m * base[0] + 2 * m * s * cx + s * s * top[0], m * m * base[1] + 2 * m * s * cy + s * s * top[1]];
    }

    /* ---------- hand-inked parts in local units ---------- */
    var SHAPES = {
        shoe: "M-6-5C-3-7 1-8 4-7L9-4C13-3 15-1 15 1C15 3 13 3.6 10 3.6H-5C-7.5 3.6-8.4 2-8-1Z",
        shoeSole: "M-7.6 2.6H14.2",
        fist: "M-3-3.4C0-5.2 5-5 7.2-2.6C8.8-.8 8.4 2.6 6.2 4C3.4 5.8-1.6 5-3 2.4Z",
        fistLines: "M2.2-3.4L2.6 3.8M5 -2.4L5.4 3M-.6-1.4L-.4 3.2",
        open: "M-2-3.4C1-4.6 5-4.8 9-3.4C10.4-2.6 10.6-1.2 9.2-.8C10.8 0 10.8 1.6 9 2C10 2.8 9.6 4.2 8 4.4C4 5.6-.8 4.8-2.4 2.4Z",
        relaxed: "M-2.5-3C1-4.6 4.4-4 6.6-2C8.2-.4 8.8 2.6 7.8 5.2C7.2 6.6 5.8 6.4 5.6 5C4.4 6.4 2.8 6 3 4.4C1-.2-1 2.2-2.8 2.2Z",
        grip: "M-3.2-3.2C0-5.4 5.4-5.2 7.4-2.4C8.8-.2 8.2 3 5.8 4.4C2.6 6-1.6 5-3.2 2.4Z"
    };
    /* Heads face +x. Origin: under the chin at the neck join; crown near y = -17. */
    var HEADS = {
        male: {
            skin: "M-5.4 2.4C-8.6-1-9.2-8-8.2-12.4C-6.8-17.4-1.6-18.6 3-17.6C6.6-16.8 8.6-14.2 8.6-11.4L9.2-9.6L8.2-8.4L11.8-5.6L9.2-4.6L9.6-3.6L8.6-3L9.2-2L7.6-.8L8.4.8C5.6 2.8 1.4 3.2-1.6 2Z",
            hair: "M-9.1-7.2C-10-12.6-7.4-18.4-1-19.2C4.4-19.8 8.6-16.8 9.2-12.2C7-13.8 5.2-14.2 3.8-13.6C2.8-14.8-1.4-14.8-3.4-13C-5.2-11.6-6.2-9-7.4-6.4Z",
            eye: "M4.8-9.4L6.6-9.2", brow: "M3.4-11L7.4-10.6", ear: "M-2.2-8.6C-.2-9.4 1.2-7.6 .6-5.6C0-4.4-1.2-4.4-2-5.4", mouth: "M6.4-1.8L8.6-1.6",
            extra: "M-1.6.8C2.2 2.6 6 2 8 .2M-.4-2.4C1.2-.6 3.6-.8 5.4-1.8", extraKind: "hatch"
        },
        female: {
            skin: "M-5 2.4C-8.2-.6-8.8-7.6-7.8-12C-6.4-16.8-1.6-18-2.6-17.4L3-17.2C6.4-16.4 8-14.2 8.1-11.6L8.6-10L7.8-9L10.8-6L8.4-5.2L8.8-4.4L7.8-3.8L8.4-2.8L7-1.6L7.4.2C5 2.2 1.2 3 -1.4 2Z",
            hair: "M-9-6C-10.4-12-8-18.4-1.2-19.2C4.6-19.8 9-17 9.1-12.4C6.4-14.2 4.2-14.4 2.6-13.4C.4-14.8-2.6-14-4.2-11.4C-5.4-9.4-6.2-7.2-7.4-5Z",
            eye: "M4.6-9.4L6.3-9.1", brow: "M3.2-11L7-10.7", ear: "M-2-8.4C0-9.2 1.2-7.4.6-5.6C0-4.6-1.2-4.6-2-5.4", mouth: "M6-2L7.8-1.8",
            lash: "M6.3-9.1L7.3-9.8"
        }
    };
    var HAIR = {
        crop: null,
        pony: "M-8.6-14.6C-14-15-17-11-16.4-5.6C-16-2.6-14-.4-11.8.2C-13-2-12.6-4.8-11.4-7.2C-10-9.8-8.6-11.6-7.4-13Z",
        bob: "M-9.4-4.6C-10.6-12-8-18.6-1.2-19.4C5-20 9.4-17 9.2-11.8C6.4-14.2 4-14.2 2.4-13.2C-1-14.6-4.4-12.6-5.6-9C-6.2-6.8-6.4-4-7.6 0.6C-9 0.8-10 -1.6-9.4-4.6Z",
        bun: "M-6.4-18.6C-8-22-4.2-24.4-1.6-22.2C0-20.8-.2-18.8-2-18Z",
        long: "M-9.1-7C-11.8-13-8-19.2-1-19.4C3.6-19.5 7-17.6 8.2-14.6C5-15.6 2.6-14.8 .4-13.6C-2.4-12.4-4-9.6-4.6-6L-4-.4C-6.4 1-9.6 .4-10-3Z"
    };
    var ACC = {
        glasses: "M3.4-9.6C3.4-8 4.4-7 6-7C7.6-7 8.6-8 8.4-9.6C8.2-11.2 6.6-11.8 5.4-11.6C4.2-11.4 3.4-10.6 3.4-9.6ZM-2-9.6H3.4M8.4-9.8L9.6-10.2",
        cap: "M-9.3-11.6C-9.8-17.4-5.4-20.6 .2-20.6C5-20.6 8.6-18.2 9.4-13.6L9.2-12L-8.6-11.2Z",
        capBill: "M-8.6-12.6L-12.6-12.2L-12.8-10.6L-9-10.8Z",
        capFront: "M8.6-13.4L16.2-11.8C17-11.2 16.4-10 15.2-10.2L8.2-11.2Z",
        helmet: "M-9.6-9.4C-10.2-17-5-21.4-.2-21.4C5.8-21.4 10-17.8 10-12.6L9.4-10.4C4-12.6-3-12.6-9.6-9.4Z",
        helmetStripe: "M-4-20.4C-1-14-1-13.6-2-11.2M2-20.8C5-15 5.4-13.6 5-11.6",
        headband: "M-9-12.6C-4-14.4 3-14.6 8.6-13.2L8.5-11.8C3-13.2-4-13-8.8-11.2Z",
        beard: "M-3.8-2.4C-1.2-.6 2.2-1 4.4-3.2L8.6-3.2C9.4-1.6 9-.6 8-.4C7.4 2.4 3.4 3.8-1.4 2.4Z",
        stubble: "M3-1.2L3.4-.6M4.6-.4L5-.2M1.4 0.2L1.8 0.8M5.8-.8L6.4-.4M2.4 1.2L2.8 1.6M4.2 1.2L4.6 1.6"
    };

    /* ---------- person rendering ---------- */
    /* spec: {kind:"male"|"female", hair, acc[], skin:"paper"|"wash", top:"tank"|"tee"|"bra"|"hoodie"|"none",
     *        topFill, bottom:"shorts"|"leggings"|"pants", bottomFill, shoeFill, build:{...}} */
    var BUILD = {
        male: { thighA: 5.2, thighB: 7.4, kneeW: 4.5, calfA: 5.6, calfB: 3.8, ankle: 2.8, uaA: 5.2, uaB: 5.8, elbow: 3.8, faA: 4.2, faB: 4.8, wrist: 2.5,
            front: [8.0, 7.6, 8.0, 9.4, 11.4, 12.0, 10.0, 5.0], back: [10.0, 8.6, 7.6, 8.2, 9.6, 10.6, 9.0, 5.2] },
        female: { thighA: 5.4, thighB: 6.8, kneeW: 4.1, calfA: 4.8, calfB: 3.4, ankle: 2.5, uaA: 3.5, uaB: 3.8, elbow: 3.0, faA: 3.1, faB: 3.5, wrist: 2.1,
            front: [7.2, 5.8, 5.8, 7.0, 9.2, 8.2, 6.8, 4.2], back: [9.6, 7.0, 5.8, 6.0, 6.8, 7.2, 6.4, 4.4] }
    };
    var LEG = { thigh: 34, shin: 29, ankleH: 4 };
    var ARM = { up: 24, fore: 21 };

    function fillClass(name) { return name === "ink" ? "ci" : name === "wash" ? "cw" : "cp"; }

    function leg(spec, hip, knee, ankle, footAngle, far) {
        var b = BUILD[spec.kind], out = "", skinC = far ? "cw" : "cp";
        var pts = [hip, knee, ankle];
        var skinProf = [[0, b.thighA + 1, b.thighB + 0.6], [0.5, b.thighA + 0.2, b.thighB - 0.3], [1, b.kneeW, b.kneeW - 0.2], [1.28, b.calfA, b.calfB], [1.75, b.calfA * 0.62, b.calfB * 0.7], [2, b.ankle, b.ankle]];
        var g = ribbon(pts, skinProf, { shade: spec.flip ? "B" : "A" });
        var bottom = spec.bottom, cls;
        if (bottom === "leggings" || bottom === "pants") {
            cls = far ? "cw" : fillClass(spec.bottomFill || "ink");
            if (far && spec.bottomFill === "ink") { cls = "ci cfar"; }
            out += '<path class="' + cls + ' co" d="' + g.d + '"/>';
            if (g.shade && cls.indexOf("ci") < 0) { out += '<path class="cs" d="' + g.shade + '"/>'; }
            if (bottom === "leggings" && cls.indexOf("ci") >= 0) { out += '<path class="cl" d="M' + pt(g.L[3]) + "L" + pt(g.L[Math.floor(g.L.length / 2)]) + '"/>'; }
        } else {
            out += '<path class="' + skinC + ' co" d="' + g.d + '"/>';
            if (g.shade) { out += '<path class="cs" d="' + g.shade + '"/>'; }
            if (bottom === "shorts") {
                var sp = [hip, [hip[0] + (knee[0] - hip[0]) * 0.55, hip[1] + (knee[1] - hip[1]) * 0.55]];
                var sg = ribbon(sp, [[0, b.thighA + 1.5, b.thighB + 1.2], [1, b.thighA + 0.9, b.thighB]]);
                out += '<path class="' + (far ? "ci cfar" : fillClass(spec.bottomFill || "ink")) + ' co" d="' + sg.d + '"/>';
            }
        }
        out += '<path class="' + fillClass(spec.shoeFill || "ink") + ' co" d="' + SHAPES.shoe + '" transform="translate(' + pt(ankle) + ") rotate(" + f(footAngle || 0) + ') scale(.8)"/>';
        out += '<path class="cl cwhite" d="' + SHAPES.shoeSole + '" transform="translate(' + pt(ankle) + ") rotate(" + f(footAngle || 0) + ') scale(.8)"/>';
        return out;
    }

    function arm(spec, sh, el, wr, hand, far) {
        var b = BUILD[spec.kind], out = "";
        var prof = [[0, b.uaA + 1.8, b.uaB + 2], [0.3, b.uaA + 1.2, b.uaB + 1.8], [0.7, b.uaA + 0.2, b.uaB + 1.1], [1, b.elbow, b.elbow], [1.3, b.faA + 0.9, b.faB + 0.7], [2, b.wrist, b.wrist]];
        var g = ribbon([sh, el, wr], prof, { shade: spec.flip ? "B" : "A" });
        var sleeveCls = far ? "ci cfar" : fillClass(spec.topFill || "ink");
        out += '<path class="' + (far ? "cw" : "cp") + ' co" d="' + g.d + '"/>';
        if (g.shade) { out += '<path class="cs" d="' + g.shade + '"/>'; }
        if (spec.top === "tee" || spec.top === "hoodie") {
            var len = spec.top === "hoodie" ? 1.9 : 0.6;
            var sl = ribbon(len > 1 ? [sh, el, wr] : [sh, [sh[0] + (el[0] - sh[0]) * 0.7, sh[1] + (el[1] - sh[1]) * 0.7]],
                len > 1 ? [[0, b.uaA + 1.6, b.uaB + 1.8], [1, b.elbow + 1.2, b.elbow + 1.2], [1.4, b.faA + 1.4, b.faB + 1.4], [1.9, b.wrist + 2.2, b.wrist + 2.2]]
                    : [[0, b.uaA + 1.6, b.uaB + 1.8], [1, b.uaA + 1, b.uaB + 0.8]]);
            out += '<path class="' + sleeveCls + ' co" d="' + sl.d + '"/>';
        }
        if (hand) {
            var ang = Math.atan2(wr[1] - el[1], wr[0] - el[0]) * 180 / PI + (hand.rot || 0);
            var sh2 = SHAPES[hand.kind || "fist"];
            out += '<path class="' + (far ? "cw" : "cp") + ' co" d="' + sh2 + '" transform="translate(' + pt(wr) + ") rotate(" + f(ang) + ')"/>';
            if ((hand.kind || "fist") === "fist") { out += '<path class="cl cthin" d="' + SHAPES.fistLines + '" transform="translate(' + pt(wr) + ") rotate(" + f(ang) + ')"/>'; }
        }
        return out;
    }

    function head(spec, neckTop, tilt, extra) {
        var H = HEADS[spec.kind], out = "", t = 'transform="translate(' + pt(neckTop) + ") rotate(" + f(tilt || 0) + ')"';
        out += "<g " + t + ">";
        if (spec.hair === "pony") { out += '<g class="hair-pony" transform="rotate(' + f((extra && extra.pony) || 0) + ' -8 -13)"><path class="ci co" d="' + HAIR.pony + '"/></g>'; }
        if (spec.hair === "bun") { out += '<path class="ci co" d="' + HAIR.bun + '"/>'; }
        if (spec.hair === "long") { out += '<path class="ci co" d="' + HAIR.long + '"/>'; }
        out += '<path class="cp co" d="' + H.skin + '"/>';
        if (spec.acc && spec.acc.indexOf("beard") >= 0) { out += '<path class="ci co" d="' + ACC.beard + '"/>'; }
        if (spec.acc && spec.acc.indexOf("stubble") >= 0) { out += '<path class="cl cthin" d="' + ACC.stubble + '"/>'; }
        if (spec.hair === "bob") { out += '<path class="ci co" d="' + HAIR.bob + '"/>'; } else if (spec.hair !== "cap" && !(spec.acc && spec.acc.indexOf("helmet") >= 0)) { out += '<path class="ci co" d="' + H.hair + '"/>'; }
        out += '<path class="cl" d="' + H.ear + '"/>';
        out += '<path class="cl cthick" d="' + H.eye + '"/><path class="cl cthin" d="' + H.brow + '"/><path class="cl cthin" d="' + H.mouth + '"/>';
        if (H.lash) { out += '<path class="cl cthin" d="' + H.lash + '"/>'; }
        if (spec.acc) {
            if (spec.acc.indexOf("glasses") >= 0) { out += '<path class="cl cthin" d="' + ACC.glasses + '"/>'; }
            if (spec.acc.indexOf("headband") >= 0) { out += '<path class="cw co" d="' + ACC.headband + '"/>'; }
            if (spec.acc.indexOf("helmet") >= 0) { out += '<path class="cp co" d="' + ACC.helmet + '"/><path class="cl cthin" d="' + ACC.helmetStripe + '"/>'; }
            if (spec.acc.indexOf("cap") >= 0) { out += '<path class="ci co" d="' + ACC.cap + '"/><path class="ci co" d="' + ACC.capBill + '"/>'; }
            if (spec.acc.indexOf("capFront") >= 0) { out += '<path class="ci co" d="' + ACC.cap + '"/><path class="ci co" d="' + ACC.capFront + '"/>'; }
        }
        return out + "</g>";
    }

    /* pose: {pelvis, neck (neck base), bow, headTilt, nearLeg:{k,a,foot}, farLeg, nearArm:{s? e,w,hand}, farArm, pony} */
    function person(spec, pose) {
        var b = BUILD[spec.kind], out = "";
        if (pose.pre) { out += pose.pre; }
        var base = pose.pelvis, top = pose.neck, bow = pose.bow || 0;
        var tr = torso(base, top, bow, b.front, b.back, 0, 1);
        var shoulder = spinePoint(base, top, bow, 0.9);
        var hipJ = [base[0] + (pose.hipShift || 0), base[1]];
        /* far side */
        if (pose.farArm) {
            var fa = pose.farArm; fa.s = shoulder;
            out += arm(spec, shoulder, fa.e, fa.w, fa.hand, true);
        }
        if (pose.farLeg) { out += leg(spec, hipJ, pose.farLeg.k, pose.farLeg.a, pose.farLeg.foot, true); }
        if (pose.behind) { out += pose.behind; }
        if (spec.hair === "pony" && false) { out += ""; }
        /* neck */
        var nk = ribbon([spinePoint(base, top, bow, 0.97), pose.headBase], [[0, 4.8, 5.2], [1, 4.1, 4.5]]);
        out += '<path class="cp co" d="' + nk.d + '"/>';
        /* torso skin then clothing */
        out += '<path class="cp co" d="' + tr.d + '"/>';
        var cloth = spec.top;
        if (cloth && cloth !== "none") {
            var s0 = cloth === "bra" ? 0.52 : 0.05, s1 = cloth === "bra" ? 0.9 : 0.94;
            var ct = torso(base, top, bow, b.front.map(function (v) { return v + (cloth === "bra" ? 0.2 : 0.7); }), b.back.map(function (v) { return v + 0.5; }), s0, s1);
            out += '<path class="' + fillClass(spec.topFill || "ink") + ' co" d="' + ct.d + '"/>';
            if (cloth === "tank") {
                var neckline = spinePoint(base, top, bow, 0.95);
                out += '<path class="cl cwhite cthin" d="M' + pt(ct.F[2]) + "L" + pt(ct.F[3]) + '"/>';
            }
        }
        if (spec.bottom === "leggings" || spec.bottom === "pants" || spec.bottom === "shorts") {
            var waist = torso(base, top, bow, b.front.map(function (v) { return v + 0.5; }), b.back.map(function (v) { return v + 0.5; }), 0, 0.2);
            out += '<path class="' + fillClass(spec.bottomFill || "ink") + ' co" d="' + waist.d + '"/>';
        }
        /* near side */
        if (pose.nearLeg) { out += leg(spec, hipJ, pose.nearLeg.k, pose.nearLeg.a, pose.nearLeg.foot, false); }
        out += head(spec, pose.headBase, pose.headTilt, pose);
        if (pose.mid) { out += pose.mid; }
        if (pose.nearArm) {
            var na = pose.nearArm; na.s = shoulder;
            out += arm(spec, shoulder, na.e, na.w, na.hand, false);
        }
        if (pose.front) { out += pose.front; }
        return out;
    }

    var api = {
        ik: ik, reachable: reachable, polar: polar, ease: ease, clamp: clamp, ribbon: ribbon, torso: torso, spinePoint: spinePoint,
        person: person, SHAPES: SHAPES, LEG: LEG, ARM: ARM, BUILD: BUILD, f: f, pt: pt, smoothClosed: smoothClosed
    };
    if (typeof module !== "undefined" && module.exports) { module.exports = api; } else { root.ComicRig = api; }
})(typeof window !== "undefined" ? window : this);
