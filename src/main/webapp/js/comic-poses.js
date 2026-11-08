/* Eight-second pose clips for the Super Gym comic scenes. Every clip is a pure function of t in [0, 8)
 * and ends exactly where it begins, so the loop never jumps. Angles: 0 = straight down, +90deg = forward. */
(function (root) {
    "use strict";
    var R = (typeof require === "function" && typeof module !== "undefined") ? require("./comic-rig.js") : root.ComicRig;
    var ik = R.ik, polar = R.polar, ease = R.ease, f = R.f;
    var D = Math.PI / 180, TAU = Math.PI * 2;
    var SPINE = 36, NECK = 6;

    function wave(t, cycles, phase) { return Math.sin(TAU * (t / 8 * cycles + (phase || 0))); }
    function tri(t, cycles, phase) { var x = (t / 8 * cycles + (phase || 0)) % 1; if (x < 0) { x += 1; } return 0.5 - 0.5 * Math.cos(TAU * x); }
    function up(theta) { return [Math.sin(theta), -Math.cos(theta)]; }
    function fwd(theta) { return [Math.cos(theta), Math.sin(theta)]; }
    function add(p, v, k) { return [p[0] + v[0] * k, p[1] + v[1] * k]; }
    function headPoint(hb, tiltDeg, lx, ly) {
        var a = tiltDeg * D, c = Math.cos(a), s = Math.sin(a);
        return [hb[0] + lx * c - ly * s, hb[1] + lx * s + ly * c];
    }
    /* common skeleton: returns pelvis/neck/headBase/shoulder for a lean angle (deg) */
    function frame(pelvis, leanDeg, neckLeanDeg, bow) {
        var th = leanDeg * D, neck = add(pelvis, up(th), SPINE);
        var tn = (neckLeanDeg === undefined ? leanDeg * 0.55 : neckLeanDeg) * D;
        var hb = add(neck, up(tn), NECK);
        return { pelvis: pelvis, neck: neck, headBase: hb, th: th, tn: tn, bow: bow || 0,
            shoulder: R.spinePoint(pelvis, neck, bow || 0, 0.9) };
    }
    function legs(hip, nearAnkle, farAnkle, nearFoot, farFoot) {
        var L = R.LEG;
        nearAnkle = R.reachable(hip, nearAnkle, L.thigh, L.shin); farAnkle = R.reachable(hip, farAnkle, L.thigh, L.shin);
        return {
            nearLeg: { k: ik(hip, nearAnkle, L.thigh, L.shin, -1), a: nearAnkle, foot: nearFoot || 0 },
            farLeg: { k: ik(hip, farAnkle, L.thigh, L.shin, -1), a: farAnkle, foot: farFoot || 0 }
        };
    }
    function armByAngles(S, a1, a2, hand) {
        var e = polar(S, R.ARM.up, a1 * D), w = polar(e, R.ARM.fore, a2 * D);
        return { e: e, w: w, hand: hand || { kind: "fist" } };
    }
    function plate(cx, cy, rx, ry, cls) {
        return '<ellipse class="' + (cls || "ci") + ' co" cx="' + f(cx) + '" cy="' + f(cy) + '" rx="' + rx + '" ry="' + ry + '"/>'
            + '<ellipse class="cl cwhite cthin" cx="' + f(cx) + '" cy="' + f(cy) + '" rx="' + f(rx * 0.55) + '" ry="' + f(ry * 0.66) + '"/>';
    }

    var clips = {};

    /* ---- back squat, profile ---- */
    clips.squat = function (t) {
        var u = (t / 4) % 1, d = ease(0.5 - 0.5 * Math.cos(TAU * u));
        var ax = 5, hip = [ax - 17 * d - 1, -66 + 35 * d];
        var fr = frame(hip, 7 + 34 * d, 5 + 20 * d);
        var S = fr.shoulder, lean = fr.th / D;
        var a1 = -30 + lean * 0.5, a2 = 166 - lean * 0.15;
        var na = armByAngles(S, a1, a2, { kind: "grip", rot: -10 });
        var fa = armByAngles(S, a1 + 4, a2 + 3, { kind: "grip", rot: -10 });
        var bar = na.w;
        var pose = {
            pelvis: fr.pelvis, neck: fr.neck, headBase: fr.headBase, bow: 0, headTilt: fr.tn / D - 3, pony: -14 * d - 6,
            nearArm: na, farArm: fa,
            behind: plate(bar[0] + 8, bar[1] - 2, 4, 15, "cw") + '<path class="cl cthick" d="M' + f(bar[0] - 9) + " " + f(bar[1] + 2) + "L" + f(bar[0] + 8) + " " + f(bar[1] - 2) + '"/>',
            mid: '<path class="cl cthick" d="M' + f(bar[0] - 11) + " " + f(bar[1] + 2.6) + "L" + f(bar[0] + 3) + " " + f(bar[1] - 0.2) + '"/>' + plate(bar[0] - 10, bar[1] + 3, 4.6, 15.5, "ci")
        };
        var lg = legs(fr.pelvis, [ax, -4], [ax + 5, -4], 0, 0);
        pose.nearLeg = lg.nearLeg; pose.farLeg = lg.farLeg;
        return pose;
    };

    /* ---- alternating dumbbell curl, profile ---- */
    function dumbbell(w, cls) {
        return '<circle class="' + (cls || "ci") + ' co" cx="' + f(w[0]) + '" cy="' + f(w[1]) + '" r="6.6"/>'
            + '<circle class="cl cwhite cthin" cx="' + f(w[0]) + '" cy="' + f(w[1]) + '" r="3.6"/>'
            + '<path class="cl cwhite cthin" d="M' + f(w[0] - 1.4) + " " + f(w[1]) + "H" + f(w[0] + 1.4) + '"/>';
    }
    clips.curl = function (t) {
        var hip = [0, -66 + 1.5 * wave(t, 2, 0)], fr = frame(hip, 3 + 1.5 * wave(t, 2, .25), 1);
        var S = fr.shoulder;
        var c1 = ease(tri(t, 2, 0)), c2 = ease(tri(t, 2, 0.5));
        var na = armByAngles(S, -4 + 14 * c1, 6 + 150 * c1, { kind: "grip", rot: -90 });
        var fa = armByAngles(S, -4 + 14 * c2, 6 + 150 * c2, { kind: "grip", rot: -90 });
        var pose = {
            pelvis: fr.pelvis, neck: fr.neck, headBase: fr.headBase, headTilt: fr.tn / D - 3 + 2 * Math.sin(TAU * t / 8 * 2),
            nearArm: na, farArm: fa,
            behind: dumbbell(fa.w, "cw"), mid: ""
        };
        pose.front = dumbbell(na.w, "ci");
        var lg = legs(fr.pelvis, [4, -4], [-4, -4], 0, 0);
        pose.nearLeg = lg.nearLeg; pose.farLeg = lg.farLeg;
        return pose;
    };

    /* ---- treadmill jog, profile; y = 0 is the belt top ---- */
    clips.run = function (t) {
        var cyc = t / 8 * 8, p = cyc % 1;
        function foot(ph) {
            var q = ((ph % 1) + 1) % 1, stride = 40, x, y, ang;
            if (q < 0.42) { var s = q / 0.42; x = 14 - stride * s; y = -4; ang = 8 - 22 * s; }
            else { var s2 = (q - 0.42) / 0.58, e = ease(s2); x = 14 - stride + stride * e; y = -4 - 22 * Math.sin(Math.PI * Math.pow(s2, 0.85)); ang = -14 + 34 * Math.sin(Math.PI * s2 * 0.9) - 10 * s2; }
            return { a: [x, y], ang: ang };
        }
        var n = foot(p), fr0 = foot(p + 0.5);
        var bob = 2.6 * (0.5 - 0.5 * Math.cos(4 * Math.PI * (p + 0.04)));
        var hip = [0, -61 - bob], fr = frame(hip, 11, 7);
        var S = fr.shoulder;
        var sw = Math.sin(TAU * p);
        var aN = -30 * sw + 5, aF = 30 * sw + 5;
        var na = armByAngles(S, aN, aN + 88 + 6 * sw, { kind: "fist", rot: 10 });
        var fa = armByAngles(S, aF, aF + 88 - 6 * sw, { kind: "fist", rot: 10 });
        var lg = legs(fr.pelvis, n.a, fr0.a, n.ang, fr0.ang);
        return { pelvis: fr.pelvis, neck: fr.neck, headBase: fr.headBase, headTilt: fr.tn / D + 1, pony: 18 * sw + 8,
            nearArm: na, farArm: fa, nearLeg: lg.nearLeg, farLeg: lg.farLeg };
    };

    /* ---- seated: notebook sketch ---- */
    clips.sketch = function (t) {
        var hip = [0, -34], fr = frame(hip, 17, 26);
        var S = fr.shoulder;
        var w = [27 + 3.2 * wave(t, 16, 0), -47 + 1.8 * wave(t, 32, .2) + 0.6 * wave(t, 2, 0)];
        var e = ik(S, R.reachable(S, w, 24, 21), 24, 21, 1);
        var na = { e: e, w: R.reachable(S, w, 24, 21), hand: { kind: "grip", rot: 20 } };
        var wf = [31, -42], ef = ik(S, R.reachable(S, wf, 24, 21), 24, 21, 1);
        var fa = { e: ef, w: R.reachable(S, wf, 24, 21), hand: { kind: "open", rot: 10 } };
        var pose = { pelvis: fr.pelvis, neck: fr.neck, headBase: fr.headBase, headTilt: 31 + 1.5 * wave(t, 2, .1), bow: 1.5, nearArm: na, farArm: fa };
        var lg = legs(hip, [37, -4], [41, -4], 0, 0);
        lg.nearLeg.k = ik(hip, lg.nearLeg.a, 34, 29, -1); lg.farLeg.k = ik(hip, lg.farLeg.a, 34, 29, -1);
        pose.nearLeg = lg.nearLeg; pose.farLeg = lg.farLeg;
        pose.mid = '<g transform="translate(29 -45) rotate(-62)"><path class="cp co" d="M-12-1H12V17H-12Z"/><path class="cl cthin" d="M-8 3H9M-8 7H7M-8 11H9"/><path class="ci co" d="M-12-1H-9.4V17H-12Z"/></g>';
        pose.front = '<path class="ci co" d="M' + f(w[0] - 0.6) + " " + f(w[1] + 0.4) + "L" + f(w[0] + 5.6) + " " + f(w[1] + 3.6) + "L" + f(w[0] + 5) + " " + f(w[1] + 4.4) + "L" + f(w[0] - 1) + " " + f(w[1] + 1.2) + 'Z"/>';
        return pose;
    };

    /* ---- seated: gum bubble ---- */
    clips.gum = function (t) {
        var hip = [0, -34], fr = frame(hip, 2 + 0.8 * wave(t, 4, 0), 0);
        var S = fr.shoulder;
        var wr = [24, -41], er = ik(S, wr, 24, 21, 1);
        var na = { e: er, w: wr, hand: { kind: "relaxed", rot: 8 } };
        var wf = [19, -39], ef = ik(S, wf, 24, 21, 1);
        var fa = { e: ef, w: wf, hand: { kind: "relaxed", rot: 8 } };
        var r;
        if (t < 1.4) { r = 0; } else if (t < 5.2) { r = 12.5 * ease((t - 1.4) / 3.8); } else if (t < 5.5) { r = 12.5 * (1 + 0.14 * ease((t - 5.2) / 0.3)); }
        else if (t < 5.68) { r = 0; } else { r = 0; }
        var tilt = -3 + 2 * wave(t, 4, 0) * (t > 1 && t < 5.5 ? 0.3 : 1);
        var m = headPoint(fr.headBase, tilt, 8.4, -2.4);
        var cx = m[0] + r * Math.cos(tilt * D) * 0.92 + 0.6, cy = m[1] + r * Math.sin(tilt * D) * 0.92 - r * 0.08;
        var bubble = "";
        if (r > 0.6) {
            bubble = '<path class="cp co" d="M' + f(m[0]) + " " + f(m[1] - 1.2) + "Q" + f(m[0] + r * 0.4) + " " + f(cy - r * 0.9) + " " + f(cx) + " " + f(cy - r) + "A" + f(r) + " " + f(r) + " 0 1 1 " + f(cx) + " " + f(cy + r) + "Q" + f(m[0] + r * 0.4) + " " + f(cy + r * 0.9) + " " + f(m[0]) + " " + f(m[1] + 1) + 'Z"/>'
                + '<path class="cl cthin" d="M' + f(cx - r * 0.2) + " " + f(cy - r * 0.62) + "Q" + f(cx + r * 0.45) + " " + f(cy - r * 0.55) + " " + f(cx + r * 0.62) + " " + f(cy - r * 0.05) + '"/>';
        } else if (t >= 5.5 && t < 6.1) {
            var k = (t - 5.5) / 0.6;
            bubble = '<path class="cl" d="M' + f(m[0] + 1) + " " + f(m[1] - 1) + "q" + f(3 + 3 * k) + " " + f(-5 - 3 * k) + " " + f(7 + 4 * k) + " " + f(-3 - k * 2) + 'M' + f(m[0] + 1.4) + " " + f(m[1] + 1) + "q" + f(3 + 3 * k) + " " + f(5 + 2 * k) + " " + f(7 + 4 * k) + " " + f(4 + k * 2) + '"/>';
        }
        var pose = { pelvis: fr.pelvis, neck: fr.neck, headBase: fr.headBase, headTilt: tilt, nearArm: na, farArm: fa, front: bubble };
        var lg = legs(hip, [34, -4], [30, -4], 0, 0);
        lg.nearLeg.k = ik(hip, lg.nearLeg.a, 34, 29, -1); lg.farLeg.k = ik(hip, lg.farLeg.a, 34, 29, -1);
        pose.nearLeg = lg.nearLeg; pose.farLeg = lg.farLeg;
        return pose;
    };

    /* ---- seated: phone ---- */
    clips.phone = function (t) {
        var hip = [0, -34], fr = frame(hip, 10 + 1.2 * wave(t, 1, 0), 14);
        var S = fr.shoulder, look = ease(tri(t, 1, 0));
        var wn = [22 - 2 * look, -50 + 1.2 * wave(t, 6, 0)], en = ik(S, R.reachable(S, wn, 24, 21), 24, 21, 1);
        var na = { e: en, w: R.reachable(S, wn, 24, 21), hand: { kind: "grip", rot: -68 } };
        var wf = [19, -47], ef = ik(S, wf, 24, 21, 1);
        var fa = { e: ef, w: wf, hand: { kind: "grip", rot: -68 } };
        var pose = { pelvis: fr.pelvis, neck: fr.neck, headBase: fr.headBase, headTilt: 22 + 6 * look, nearArm: na, farArm: fa };
        pose.mid = '<g transform="translate(' + f(wn[0] + 2.4) + " " + f(wn[1] - 2) + ') rotate(-22)"><path class="ci co" d="M-3 -6.6H4.2L4.8 6.6H-2.2Z"/><path class="cw" d="M-1.9 -5H3.4L3.8 5H-1.2Z"/></g>';
        var lg = legs(hip, [38, -4], [33, -4], 0, 0);
        lg.nearLeg.k = ik(hip, lg.nearLeg.a, 34, 29, -1); lg.farLeg.k = ik(hip, lg.farLeg.a, 34, 29, -1);
        pose.nearLeg = lg.nearLeg; pose.farLeg = lg.farLeg;
        return pose;
    };



    /* ================= street clips ================= */
    function footWalk(ph, half) {
        var q = ((ph % 1) + 1) % 1, x, y, ang;
        if (q < 0.6) { var a = q / 0.6; x = half - 2 * half * a; y = -4; ang = 10 - 32 * a * a; }
        else { var b = (q - 0.6) / 0.4, e = ease(b); x = -half + 2 * half * e; y = -4 - 9 * Math.sin(Math.PI * b); ang = -22 + 32 * e; }
        return { a: [x, y], ang: ang };
    }
    function glove(w, ang, cls) {
        return '<g transform="translate(' + f(w[0]) + " " + f(w[1]) + ") rotate(" + f(ang) + ')"><path class="cl cthin" d="M0 2L0 7"/>'
            + '<path class="' + (cls || "ci") + ' co" d="M-5.6 7C-8 12-6.4 19-1.6 20.4C3.6 21.6 8 18 7.6 12C7.2 8 4.4 6.4 1 6.6Z"/>'
            + '<path class="cl cwhite cthin" d="M-4 9.4H5.4M-1.8 12C-1 15 1.4 17 3.8 17"/><path class="cp co" d="M-3.6 4.4H3.4V7.8H-3.6Z"/></g>';
    }
    function umbrellaMark(w, tiltDeg) {
        var cx = w[0] + 2, top = -160, y0 = -136, r = 38;
        return '<g transform="rotate(' + f(tiltDeg || 0) + " " + f(w[0]) + " " + f(w[1]) + ')"><path class="cl cthick" d="M' + f(w[0]) + " " + f(w[1] + 3) + "L" + f(w[0] + 1) + " " + f(top) + '"/>'
            + '<path class="cl cthick" d="M' + f(w[0]) + " " + f(w[1] + 3) + "q0 5 -4 5" + '"/>'
            + '<path class="ci co" d="M' + f(cx - r) + " " + f(y0) + "Q" + f(cx - r * 0.7) + " " + f(top + 2) + " " + f(cx + 1) + " " + f(top - 2) + "Q" + f(cx + r * 0.72) + " " + f(top + 2) + " " + f(cx + r) + " " + f(y0)
            + "Q" + f(cx + r * 0.66) + " " + f(y0 - 7) + " " + f(cx + r * 0.33) + " " + f(y0) + "Q" + f(cx) + " " + f(y0 - 7) + " " + f(cx - r * 0.33) + " " + f(y0) + "Q" + f(cx - r * 0.66) + " " + f(y0 - 7) + " " + f(cx - r) + " " + f(y0) + 'Z"/>'
            + '<path class="cl cwhite cthin" d="M' + f(cx + 1) + " " + f(top - 1) + "Q" + f(cx - r * 0.45) + " " + f(top + 12) + " " + f(cx - r * 0.66) + " " + f(y0 - 4) + "M" + f(cx + 1) + " " + f(top - 1) + "Q" + f(cx + r * 0.42) + " " + f(top + 12) + " " + f(cx + r * 0.66) + " " + f(y0 - 4) + "M" + f(cx + 1) + " " + f(top - 1) + "V" + f(y0 - 3) + '"/></g>';
    }
    function backpack(fr, deep) {
        var p0 = R.spinePoint(fr.pelvis, fr.neck, fr.bow, 0.22), p1 = R.spinePoint(fr.pelvis, fr.neck, fr.bow, 0.88);
        var fw = fwd(fr.th), ba = [-fw[0], -fw[1]];
        function q(p, k) { return [p[0] + ba[0] * k, p[1] + ba[1] * k]; }
        var ring = [q(p0, 5), q(p0, 7 + deep * 0.5), q(p0, 5 + deep), q(R.spinePoint(fr.pelvis, fr.neck, fr.bow, 0.5), 7 + deep), q(p1, 5 + deep * 0.9), q(p1, 8), q(p1, 4)];
        var d = R.smoothClosed(ring), pocket = [q(p0, 5 + deep), q(p0, 9 + deep), q(R.spinePoint(fr.pelvis, fr.neck, fr.bow, 0.46), 9 + deep), q(R.spinePoint(fr.pelvis, fr.neck, fr.bow, 0.46), 5 + deep)];
        return '<path class="ci co" d="' + d + '"/><path class="cl cwhite cthin" d="M' + R.pt(q(R.spinePoint(fr.pelvis, fr.neck, fr.bow, 0.56), 6.4)) + "L" + R.pt(q(R.spinePoint(fr.pelvis, fr.neck, fr.bow, 0.56), 13)) + '"/>';
    }
    function strap(fr) {
        var a = R.spinePoint(fr.pelvis, fr.neck, fr.bow, 0.96), b = R.spinePoint(fr.pelvis, fr.neck, fr.bow, 0.42), fw = fwd(fr.th);
        return '<path class="cl cthick" d="M' + R.pt(add(a, fw, 1.5)) + "Q" + R.pt(add(R.spinePoint(fr.pelvis, fr.neck, fr.bow, 0.7), fw, 11.4)) + " " + R.pt(add(b, fw, 8.6)) + '"/>';
    }
    function duffel(w, ang) {
        return '<g transform="translate(' + f(w[0]) + " " + f(w[1]) + ") rotate(" + f(ang) + ')"><path class="cl" d="M-4 3Q0 -1 4 3"/><path class="ci co" d="M-12 4H12Q15 4 15 8V15Q15 19 11 19H-11Q-15 19 -15 15V8Q-15 4 -12 4Z"/><path class="cl cwhite cthin" d="M-15 9.5H15M-5 4V19"/></g>';
    }
    function cup(w) {
        return '<g transform="translate(' + f(w[0]) + " " + f(w[1]) + ')"><path class="cp co" d="M-3.4 -5L3.4 -5L2.6 5H-2.6Z"/><path class="ci" d="M-3.6 -5.6H3.6V-3.6H-3.6Z"/><path class="cl cthin" d="M-3 -0.6H3"/></g>';
    }

    clips.walk = function (t, def, opts) {
        opts = opts || {};
        var T = def.T || 1, ph = (t - (def.t0 || 0)) / T, p = ((ph % 1) + 1) % 1;
        var bob = 2.4 * (0.5 - 0.5 * Math.cos(4 * Math.PI * (p - 0.05)));
        var hip = [0, -58 - bob], fr = frame(hip, 4, 3);
        var n = footWalk(p, 22), fo = footWalk(p + 0.5, 22), S = fr.shoulder, spec = SPECS[def.spec];
        var sw = Math.cos(TAU * p);
        var aN = -24 * sw, aF = 24 * sw, na, fa, front = "", behind = "", mid = "", rain = opts.rain;
        na = armByAngles(S, aN, aN + 14 + Math.max(0, aN) * 0.6, { kind: "relaxed", rot: 5 });
        fa = armByAngles(S, aF, aF + 14 + Math.max(0, aF) * 0.6, { kind: "relaxed", rot: 5 });
        if (spec.pack) { behind += backpack(fr, 7); mid += strap(fr); }
        if (spec.gloves) {
            na = armByAngles(S, -7 + 4 * sw, -2 + 4 * sw, { kind: "grip", rot: 0 });
            front += glove([na.w[0] + 1, na.w[1] + 3], 8 * Math.sin(TAU * (p + 0.15)), "ci") + glove([na.w[0] + 4, na.w[1] + 3], -10 - 6 * Math.sin(TAU * (p + 0.3)), "cw");
        }
        if (spec.duffel) {
            na = armByAngles(S, -6 + 3 * sw, -3 + 3 * sw, { kind: "grip", rot: 0 });
            front += duffel([na.w[0] + 1, na.w[1] + 1], 6 * Math.sin(TAU * (p + 0.1)));
        }
        if (spec.coffee) {
            na = armByAngles(S, 12 + 2 * sw, 108 + 4 * sw, { kind: "grip", rot: -80 });
            front += cup([na.w[0] + 2, na.w[1] - 3]);
        }
        if (rain && (def.umbrella !== false)) {
            var hold = [S[0] + 17, S[1] + 6];
            var e = ik(S, hold, 24, 21, 1);
            na = { e: e, w: hold, hand: { kind: "grip", rot: -90 } };
            front = umbrellaMark(hold, 0);
        }
        return { pelvis: fr.pelvis, neck: fr.neck, headBase: fr.headBase, headTilt: fr.tn / D + 1, pony: 8 * Math.sin(TAU * p),
            nearArm: na, farArm: fa, nearLeg: { k: ik(fr.pelvis, R.reachable(fr.pelvis, n.a, 34, 29), 34, 29, -1), a: R.reachable(fr.pelvis, n.a, 34, 29), foot: n.ang },
            farLeg: { k: ik(fr.pelvis, R.reachable(fr.pelvis, fo.a, 34, 29), 34, 29, -1), a: R.reachable(fr.pelvis, fo.a, 34, 29), foot: fo.ang },
            behind: behind, mid: mid, front: front };
    };

    /* bicycle: wheel radius 24, rider seated, cadence one turn per second */
    function spokes(cx, cy, ang) {
        var d = "";
        for (var i = 0; i < 6; i++) { var a = ang + i * Math.PI / 3; d += "M" + f(cx - Math.cos(a) * 22) + " " + f(cy - Math.sin(a) * 22) + "L" + f(cx + Math.cos(a) * 22) + " " + f(cy + Math.sin(a) * 22); }
        return d;
    }
    clips.bike = function (t, def) {
        var wheelAng = (t / 8) * 7 * TAU, crank = (t / 8) * 8 * TAU;
        var rear = [-36, -24], front = [38, -24], bb = [-2, -22], head = [28, -62], sad = [-17, -63];
        var pelvis = [-13, -68], fr = frame(pelvis, 46, 36);
        var S = fr.shoulder, bar = [31, -66];
        var pn = [bb[0] + Math.cos(crank) * 17, bb[1] + Math.sin(crank) * 17], pf = [bb[0] - Math.cos(crank) * 17, bb[1] - Math.sin(crank) * 17];
        var an = [pn[0] - 1, pn[1] - 3], af = [pf[0] - 1, pf[1] - 3];
        an = R.reachable(pelvis, an, 34, 29); af = R.reachable(pelvis, af, 34, 29);
        var w = R.reachable(S, bar, 24, 21), e = ik(S, w, 24, 21, 1);
        var bikeBack = '<g class="comic-bike">'
            + '<circle class="cp co" cx="' + f(rear[0]) + '" cy="' + f(rear[1]) + '" r="24" stroke-width="2.2"/><circle class="cp co" cx="' + f(front[0]) + '" cy="' + f(front[1]) + '" r="24" stroke-width="2.2"/>'
            + '<path class="cl cthin" d="' + spokes(rear[0], rear[1], wheelAng) + spokes(front[0], front[1], wheelAng + 0.3) + '"/>'
            + '<path class="cl cthick" d="M' + R.pt(rear) + "L" + R.pt(bb) + "L" + R.pt(sad) + "L" + R.pt(rear) + "M" + R.pt(bb) + "L" + R.pt(head) + "L" + R.pt(sad) + "M" + R.pt(head) + "L" + R.pt(front) + "M" + R.pt([26, -66]) + "L" + R.pt(head) + '"/>'
            + '<path class="cl cthick" d="M' + R.pt([-23, -64]) + "H" + R.pt([-9, -64]).split(" ")[0] + '"/>'
            + '<path class="cl cthick" d="M' + R.pt([26, -67]) + "L" + R.pt([35, -66]) + "L" + R.pt([37, -61]) + '"/>'
            + '<circle class="ci" cx="' + f(bb[0]) + '" cy="' + f(bb[1]) + '" r="4"/>'
            + '</g>';
        var cr = '<path class="cl cthick" d="M' + R.pt(bb) + "L" + R.pt(pn) + '"/><path class="ci co" d="M' + f(pn[0] - 5) + " " + f(pn[1] - 1) + "h10v2.4h-10Z" + '"/>';
        var crf = '<path class="cl cthick" d="M' + R.pt(bb) + "L" + R.pt(pf) + '"/><path class="ci co" d="M' + f(pf[0] - 5) + " " + f(pf[1] - 1) + "h10v2.4h-10Z" + '"/>';
        return { pelvis: pelvis, neck: fr.neck, headBase: fr.headBase, bow: 2, headTilt: 22, pony: -10 + 8 * Math.sin(TAU * t / 8 * 7),
            nearArm: { e: e, w: w, hand: { kind: "grip", rot: -20 } }, farArm: { e: ik(S, w, 24, 21, 1), w: w, hand: { kind: "grip", rot: -20 } },
            nearLeg: { k: ik(pelvis, an, 34, 29, -1), a: an, foot: -8 }, farLeg: { k: ik(pelvis, af, 34, 29, -1), a: af, foot: -8 },
            behind: "", pre: bikeBack + crf, front: cr };
    };

    /* ---------- cast and layout (shared by the runtime and the Node pre-render) ---------- */
    var SPECS = {
        squat: { kind: "female", hair: "pony", acc: ["headband"], top: "bra", topFill: "ink", bottom: "leggings", bottomFill: "ink", shoeFill: "paper" },
        curl: { kind: "male", hair: "crop", acc: ["stubble"], top: "tank", topFill: "ink", bottom: "shorts", bottomFill: "wash", shoeFill: "ink" },
        run: { kind: "female", hair: "pony", acc: [], top: "bra", topFill: "wash", bottom: "shorts", bottomFill: "ink", shoeFill: "ink" },
        sketch: { kind: "male", hair: "bun", acc: ["glasses", "beard"], top: "hoodie", topFill: "wash", bottom: "pants", bottomFill: "ink", shoeFill: "paper" },
        gum: { kind: "female", hair: "bob", acc: [], top: "tee", topFill: "paper", bottom: "leggings", bottomFill: "wash", shoeFill: "ink" },
        phone: { kind: "male", hair: "cap", acc: ["cap"], top: "tee", topFill: "ink", bottom: "shorts", bottomFill: "wash", shoeFill: "paper" }
    };
    var GYM_FIGS = [
        { id: "sketch", still: 0.5, x: 462, y: 277, s: 0.9, flip: false },
        { id: "gum", still: 4.6, x: 566, y: 277, s: 0.9, flip: true },
        { id: "phone", still: 0.4, x: 698, y: 277, s: 0.9, flip: true },
        { id: "squat", still: 1.0, x: 176, y: 326, s: 1.3, flip: false },
        { id: "curl", still: 1.0, x: 330, y: 322, s: 1.3, flip: true },
        { id: "run", still: 0.2, x: 838, y: 302, s: 1.3, flip: false }
    ];
    var STREET_SPECS = {
        walkA: { kind: "male", hair: "crop", acc: ["stubble"], top: "tee", topFill: "wash", bottom: "pants", bottomFill: "ink", shoeFill: "paper", pack: true, gloves: true },
        walkB: { kind: "female", hair: "bob", acc: [], top: "hoodie", topFill: "ink", bottom: "leggings", bottomFill: "wash", shoeFill: "paper", duffel: true },
        walkC: { kind: "male", hair: "cap", acc: ["cap"], top: "hoodie", topFill: "paper", bottom: "pants", bottomFill: "wash", shoeFill: "ink", coffee: true },
        bike: { kind: "female", hair: "pony", acc: ["helmet"], top: "tank", topFill: "paper", bottom: "shorts", bottomFill: "ink", shoeFill: "ink" }
    };
    Object.keys(STREET_SPECS).forEach(function (k) { SPECS[k] = STREET_SPECS[k]; });
    var SIDEWALK = 400;
    function smooth01(x) { return ease(clamp01(x)); }
    function clamp01(x) { return x < 0 ? 0 : x > 1 ? 1 : x; }
    /* walkers: x travels at the speed implied by their stride so the feet never skate */
    function walker(id, spec, o) {
        return { id: id, clip: "walk", spec: spec, T: o.T, t0: o.t0, s: o.s, still: o.still, umbrella: o.umbrella,
            at: function (t) {
                var dir = o.dir, v = o.v, x = o.x0 + dir * v * (t - o.t0), y = o.y, s = o.s, op = 1, vis = true;
                if (t < o.t0) { vis = false; }
                if (o.enter !== undefined && t >= o.enter) {
                    var k = smooth01((t - o.enter) / 0.9);
                    x = o.x0 + dir * v * (o.enter - o.t0) + dir * 5 * k; y = o.y - 20 * k; s = o.s * (1 - 0.14 * k); op = 1 - smooth01((t - o.enter - 0.25) / 0.65);
                }
                if (o.wrap) { x = o.x0 + dir * v * ((t - o.t0 + 8) % 8); }
                return { x: x, y: y, s: s, flip: dir < 0, op: vis ? op : 0 };
            } };
    }
    var STREET_FIGS = [
        walker("enterB", "walkB", { dir: -1, x0: 645, v: 58.4, T: 1, t0: 0, y: SIDEWALK + 5, s: 0.8, enter: 5.35, still: 3.4 }),
        walker("enterA", "walkA", { dir: 1, x0: -44, v: 58.4, T: 1, t0: 0, y: SIDEWALK, s: 0.8, enter: 6.4, still: 4.3 }),
        walker("passer", "walkC", { dir: -1, x0: 704, v: 92.8, T: 8 / 11, t0: 0, y: SIDEWALK + 16, s: 0.92, wrap: true, still: 1.4 }),
        { id: "cyclist", clip: "bike", spec: "bike", s: 0.8, still: 3.2,
          at: function (t) { return { x: -130 + 844 * (t / 8), y: 434, s: 0.8, flip: false, op: 1 }; } }
    ];
    function figureGroup(fig, t, opts) {
        var spec = Object.assign({}, SPECS[fig.spec || fig.id], { flip: !!(fig.at ? fig.at(t).flip : fig.flip) });
        var body = R.person(spec, clips[fig.clip || fig.id](t, fig, opts || {}));
        return fig.at ? '<ellipse class="comic-castshadow" cx="14" cy="1" rx="38" ry="4"/>' + body : body;
    }
    function figureTransform(fig, t) {
        var a = fig.at ? fig.at(t) : { x: fig.x, y: fig.y, s: fig.s, flip: fig.flip };
        return "translate(" + f(a.x) + " " + f(a.y) + ") scale(" + f(a.flip ? -a.s : a.s) + " " + f(a.s) + ")";
    }
    function figureVisibility(fig, t) { return fig.at ? fig.at(t).op : 1; }
    var api = { clips: clips, headPoint: headPoint, SPECS: SPECS, GYM_FIGS: GYM_FIGS, STREET_FIGS: STREET_FIGS, figureGroup: figureGroup, figureTransform: figureTransform, figureVisibility: figureVisibility };
    if (typeof module !== "undefined" && module.exports) { module.exports = api; } else { root.ComicPoses = api; }
})(typeof window !== "undefined" ? window : this);
