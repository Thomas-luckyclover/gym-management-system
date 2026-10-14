/* Suspend the original eight-second ink timelines without resetting a pose. */
(function () {
    "use strict";
    document.addEventListener("DOMContentLoaded", function () {
        const scenes = document.querySelectorAll(".comic-scene");
        const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
        const inView = new WeakMap();
        const gym = document.querySelector('[data-gym]');
        let frame = 0, previous = 0, seconds = 0;
        // Two-bone IK keeps knees connected to hip and ankle throughout a rep.
        function knee(a, b, upper, lower, bend) {
            const dx = b[0] - a[0], dy = b[1] - a[1];
            const distance = Math.max(.01, Math.hypot(dx, dy));
            const along = Math.min(upper, (upper * upper - lower * lower + distance * distance) / (2 * distance));
            const perpendicular = Math.sqrt(Math.max(0, upper * upper - along * along)) * bend;
            return [a[0] + (dx * along - dy * perpendicular) / distance,
                a[1] + (dy * along + dx * perpendicular) / distance];
        }
        // Variable-width curved contour around three joints, rather than bars.
        function legPath(a, b, c) {
            const normal = function (p, q) { const d = Math.hypot(q[0]-p[0],q[1]-p[1]) || 1; return [-(q[1]-p[1])/d,(q[0]-p[0])/d]; };
            const u=normal(a,b), v=normal(b,c), n=[(u[0]+v[0])/2,(u[1]+v[1])/2];
            const point=function(p,n,w){return (p[0]+n[0]*w).toFixed(2)+' '+(p[1]+n[1]*w).toFixed(2);};
            return 'M'+point(a,u,9)+' Q'+point(b,n,8)+' '+point(c,v,4)
                +' Q'+point(c,v,-4)+' '+point(c,v,-4)+' Q'+point(b,n,-6)+' '+point(a,u,-9)+'Z';
        }
        function pose(time) {
            if (!gym) { return; }
            const beat = time / 8 * Math.PI * 4;
            const squat=gym.querySelector('[data-ink-lifter="squat"]');
            if (squat) {
                const depth=(1-Math.cos(beat))/2, x=-7*depth, y=16*depth;
                squat.querySelector('[data-ink-upper]').setAttribute('transform','translate('+x+' '+y+')');
                [['left',[80+x,143+y],[56,202],1],['right',[108+x,143+y],[144,202],-1]].forEach(function(item){
                    const mid=knee(item[1],item[2],37,37,item[3]);
                    squat.querySelector('[data-ink-leg="'+item[0]+'"]').setAttribute('d',legPath(item[1],mid,item[2]));
                });
            }
            const curl=gym.querySelector('[data-ink-forearm]');
            if (curl) { curl.setAttribute('transform','rotate('+(8-27*(1-Math.cos(beat))/2)+' 75 96)'); }
            const runner=gym.querySelector('[data-ink-runner]');
            if (runner) {
                const run=time/8*Math.PI*8;
                [['back',Math.PI,-1],['front',0,1]].forEach(function(item){
                    const phase=run+item[1], hip=[61,143];
                    const foot=[62+34*Math.sin(phase),210-27*Math.max(0,Math.cos(phase))];
                    const mid=knee(hip,foot,37,38,item[2]);
                    runner.querySelector('[data-ink-run-leg="'+item[0]+'"]').setAttribute('d',legPath(hip,mid,foot));
                    const x=foot[0],y=foot[1];
                    runner.querySelector('[data-ink-run-shoe="'+item[0]+'"]').setAttribute('d','M'+(x-5)+' '+(y-3)+'Q'+(x+3)+' '+(y-2)+' '+(x+12)+' '+(y+3)+'Q'+(x+17)+' '+(y+9)+' '+(x+10)+' '+(y+9)+'L'+(x-7)+' '+(y+7)+'Z');
                });
                runner.querySelector('[data-ink-run-arm]').setAttribute('transform','rotate('+(Math.sin(run)*7)+' 78 82)');
            }
        }
        function tick(now) {
            frame=0;
            if (!gym || gym.classList.contains('comic-scene--sleeping')) { previous=0; return; }
            if (!previous || now-previous>=1000/30) {
                seconds=(seconds+(previous?Math.min((now-previous)/1000,.1):0))%8;
                previous=now; pose(seconds);
            }
            frame=requestAnimationFrame(tick);
        }
        function sync() {
            scenes.forEach(function (scene) {
                const paused = document.hidden || reduced.matches
                    || document.body.classList.contains("dashboard-motion-paused")
                    || document.documentElement.classList.contains("gms-lite") || inView.get(scene) === false;
                scene.classList.toggle("comic-scene--sleeping", paused);
            });
            if (gym && !gym.classList.contains('comic-scene--sleeping') && !frame) { frame=requestAnimationFrame(tick); }
            else if (gym && gym.classList.contains('comic-scene--sleeping')) { cancelAnimationFrame(frame); frame=0; previous=0; }
        }
        if ("IntersectionObserver" in window) {
            const observer = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) { inView.set(entry.target, entry.isIntersecting); });
                sync();
            });
            scenes.forEach(function (scene) { observer.observe(scene); });
        }
        document.addEventListener("visibilitychange", sync);
        document.addEventListener("dashboardmotionchange", sync);
        reduced.addEventListener("change", sync);
        pose(0); sync();
    });
})();
