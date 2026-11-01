"""Render a .drawio (straight-segment edges only) to PNG and check geometry:
lines through shapes, crossing lines, overlapping lines, overlapping shapes, text overflow."""
import sys, re, html, math, itertools, xml.etree.ElementTree as ET
import subprocess, json

def sty(s):
    d = {}
    for t in (s or '').split(';'):
        if not t: continue
        k, _, v = t.partition('='); d[k] = v
    return d

def load(path):
    root = ET.parse(path).getroot()
    V, E = {}, []
    for c in root.iter('mxCell'):
        g = c.find('mxGeometry'); s = sty(c.get('style'))
        if c.get('vertex') == '1':
            V[c.get('id')] = dict(x=float(g.get('x', 0)), y=float(g.get('y', 0)), w=float(g.get('width')), h=float(g.get('height')), s=s, v=c.get('value') or '')
        elif c.get('edge') == '1':
            pts = [(float(p.get('x')), float(p.get('y'))) for p in g.iter('mxPoint') if p.get('as') is None]
            E.append(dict(id=c.get('id'), src=c.get('source'), tgt=c.get('target'), s=s, v=c.get('value') or '', pts=pts))
    return V, E

def kind(v):
    s = v['s']
    if 'ellipse' in s: return 'ellipse'
    return s.get('shape', 'rect')

def center(v): return (v['x'] + v['w'] / 2, v['y'] + v['h'] / 2)

def perim(v, toward):
    cx, cy = center(v); dx, dy = toward[0] - cx, toward[1] - cy
    if dx == dy == 0: return cx, cy
    a, b = v['w'] / 2, v['h'] / 2
    if kind(v) == 'ellipse':
        t = 1 / math.sqrt((dx / a) ** 2 + (dy / b) ** 2)
    else:
        t = min(a / abs(dx) if dx else 1e9, b / abs(dy) if dy else 1e9)
    return cx + dx * t, cy + dy * t

def path(V, e):
    s, t = V[e['src']], V[e['tgt']]
    S, T = e['s'], e['s']
    sp = (s['x'] + s['w'] * float(S['exitX']), s['y'] + s['h'] * float(S['exitY'])) if 'exitX' in S else None
    tp = (t['x'] + t['w'] * float(T['entryX']), t['y'] + t['h'] * float(T['entryY'])) if 'entryX' in T else None
    nxt = e['pts'][0] if e['pts'] else (tp or center(t))
    sp = sp or perim(s, nxt)
    prv = e['pts'][-1] if e['pts'] else sp
    tp = tp or perim(t, prv)
    return [sp] + e['pts'] + [tp]

def label_box(v):
    if kind(v) != 'umlActor': return None
    w = max(40, 6.6 * len(html.unescape(re.sub('<[^>]+>', '', v['v']))))
    return (v['x'] + v['w'] / 2 - w / 2, v['y'] + v['h'] + 2, w, 16)

def inside(v, p, m=3):
    x, y = p
    if kind(v) == 'ellipse':
        a, b = v['w'] / 2 - m, v['h'] / 2 - m; cx, cy = center(v)
        return ((x - cx) / a) ** 2 + ((y - cy) / b) ** 2 < 1
    inbox = v['x'] + m < x < v['x'] + v['w'] - m and v['y'] + m < y < v['y'] + v['h'] - m
    if inbox and kind(v) == 'folder':  # the band right of the tab, above the body, is empty space
        tw, th = float(v['s'].get('tabWidth', 60)), float(v['s'].get('tabHeight', 18))
        return x < v['x'] + tw or y > v['y'] + th + m
    return inbox

def in_box(b, p, m=1):
    return b[0] + m < p[0] < b[0] + b[2] - m and b[1] + m < p[1] < b[1] + b[3] - m

def seg_x(p1, p2, p3, p4):
    def o(a, b, c): return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    d1, d2, d3, d4 = o(p3, p4, p1), o(p3, p4, p2), o(p1, p2, p3), o(p1, p2, p4)
    return (d1 * d2 < -1e-6) and (d3 * d4 < -1e-6)

def collinear_overlap(p1, p2, p3, p4):
    def o(a, b, c): return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    if abs(o(p1, p2, p3)) > 1 or abs(o(p1, p2, p4)) > 1: return False
    L = math.dist(p1, p2)
    if L < 1: return False
    u = ((p2[0] - p1[0]) / L, (p2[1] - p1[1]) / L)
    a, b = sorted([(p3[0] - p1[0]) * u[0] + (p3[1] - p1[1]) * u[1], (p4[0] - p1[0]) * u[0] + (p4[1] - p1[1]) * u[1]])
    return min(b, L) - max(a, 0) > 4

def is_frame(v, V):
    return any(o is not v and v['x'] <= o['x'] and v['y'] <= o['y'] and o['x'] + o['w'] <= v['x'] + v['w'] and o['y'] + o['h'] <= v['y'] + v['h'] for o in V.values())

def check(path_):
    V, E = load(path_)
    issues = []
    frames = {k for k, v in V.items() if is_frame(v, V)}
    P = {e['id']: path(V, e) for e in E}
    for e in E:
        pts = P[e['id']]
        for (a, b) in zip(pts, pts[1:]):
            n = max(2, int(math.dist(a, b) / 2))
            for k, v in V.items():
                if k in frames: continue
                lb = label_box(v)
                for i in range(1, n):
                    p = (a[0] + (b[0] - a[0]) * i / n, a[1] + (b[1] - a[1]) * i / n)
                    if inside(v, p) or (lb and k not in (e['src'], e['tgt']) and in_box(lb, p)):
                        issues.append(f"edge {e['id']} ({e['src']}->{e['tgt']}) passes through {k}"); break
                else: continue
                break
    segs = [(e['id'], a, b) for e in E for a, b in zip(P[e['id']], P[e['id']][1:])]
    cross = 0
    for (i1, a, b), (i2, c, d) in itertools.combinations(segs, 2):
        if i1 == i2: continue
        if seg_x(a, b, c, d): cross += 1; issues.append(f"crossing {i1} x {i2}")
        elif collinear_overlap(a, b, c, d): issues.append(f"OVERLAP {i1} / {i2}")
    for (k1, v1), (k2, v2) in itertools.combinations(V.items(), 2):
        if k1 in frames or k2 in frames: continue
        b1 = (v1['x'], v1['y'], v1['w'], v1['h'] + (18 if kind(v1) == 'umlActor' else 0))
        b2 = (v2['x'], v2['y'], v2['w'], v2['h'] + (18 if kind(v2) == 'umlActor' else 0))
        if b1[0] < b2[0] + b2[2] and b2[0] < b1[0] + b1[2] and b1[1] < b2[1] + b2[3] and b2[1] < b1[1] + b1[3]:
            issues.append(f"SHAPE OVERLAP {k1} / {k2}")
    return V, E, P, issues

def svg(V, E, P):
    xs = [v['x'] for v in V.values()] + [p[0] for ps in P.values() for p in ps]
    ys = [v['y'] for v in V.values()] + [p[1] for ps in P.values() for p in ps]
    X2 = max(v['x'] + v['w'] for v in V.values()) + 120; Y2 = max(v['y'] + v['h'] for v in V.values()) + 60
    X1, Y1 = min(xs) - 120, min(ys) - 30
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{X2-X1}" height="{Y2-Y1}" viewBox="{X1} {Y1} {X2-X1} {Y2-Y1}" style="background:#fff;font-family:Helvetica,Arial,sans-serif">',
           '<defs><marker id="open" markerWidth="12" markerHeight="12" refX="11" refY="6" orient="auto" markerUnits="userSpaceOnUse"><path d="M1,1 L11,6 L1,11" fill="none" stroke="#000"/></marker>'
           '<marker id="block" markerWidth="16" markerHeight="16" refX="15" refY="8" orient="auto" markerUnits="userSpaceOnUse"><path d="M1,1 L15,8 L1,15 Z" fill="#fff" stroke="#000"/></marker></defs>']
    order = sorted(V.items(), key=lambda kv: -(kv[1]['w'] * kv[1]['h']))
    def text(v, x, y, w, h, extra=''):
        s = v['s']; fs = s.get('fontSize', '12'); b = 'bold' if int(s.get('fontStyle', '0') or 0) & 1 else 'normal'
        al = s.get('align', 'center'); va = {'top': 'flex-start', 'bottom': 'flex-end'}.get(s.get('verticalAlign', 'middle'), 'center')
        pl, pt = s.get('spacingLeft', '2'), s.get('spacingTop', '0')
        return f'<foreignObject x="{x}" y="{y}" width="{w}" height="{h}"><div xmlns="http://www.w3.org/1999/xhtml" class="lbl" data-h="{h}" style="width:{w}px;height:{h}px;display:flex;align-items:{va};justify-content:{ {"left":"flex-start"}.get(al,"center") };text-align:{al};box-sizing:border-box;padding:{pt}px 4px 0 {pl}px;font-size:{fs}px;font-weight:{b};line-height:1.2;{extra}"><div>{html.unescape(v["v"])}</div></div></foreignObject>'
    for k, v in order:
        x, y, w, h = v['x'], v['y'], v['w'], v['h']; kd = kind(v)
        if kd == 'ellipse':
            out.append(f'<ellipse cx="{x+w/2}" cy="{y+h/2}" rx="{w/2}" ry="{h/2}" fill="#fff" stroke="#000"/>' + text(v, x + 8, y, w - 16, h))
        elif kd == 'umlActor':
            cx = x + w / 2
            out.append(f'<g stroke="#000" fill="#fff"><circle cx="{cx}" cy="{y+h*0.13}" r="{h*0.13}"/><line x1="{cx}" y1="{y+h*0.26}" x2="{cx}" y2="{y+h*0.7}"/><line x1="{x}" y1="{y+h*0.4}" x2="{x+w}" y2="{y+h*0.4}"/><line x1="{cx}" y1="{y+h*0.7}" x2="{x}" y2="{y+h}"/><line x1="{cx}" y1="{y+h*0.7}" x2="{x+w}" y2="{y+h}"/></g>')
            out.append(f'<foreignObject x="{cx-110}" y="{y+h+1}" width="220" height="40"><div xmlns="http://www.w3.org/1999/xhtml" style="text-align:center;font-size:12px">{html.unescape(v["v"])}</div></foreignObject>')
        elif kd == 'note':
            out.append(f'<path d="M{x},{y} H{x+w-14} L{x+w},{y+14} V{y+h} H{x} Z" fill="#fff" stroke="#000"/>' + text(v, x, y, w, h))
        elif kd == 'folder':
            tw, th = float(v['s'].get('tabWidth', 60)), float(v['s'].get('tabHeight', 18))
            out.append(f'<path d="M{x},{y} H{x+tw} V{y+th} H{x+w} V{y+h} H{x} Z" fill="#fff" stroke="#000"/>' + text(v, x, y + th, w, h - th))
        elif v['s'].get('text') is not None or v['s'].get('strokeColor') == 'none':
            out.append(text(v, x, y, w, h))
        else:
            dash = ' stroke-dasharray="6 4"' if v['s'].get('dashed') == '1' else ''
            out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="#fff" stroke="#000"{dash}/>' + text(v, x, y, w, h))
    for e in E:
        pts = P[e['id']]; s = e['s']
        dash = ' stroke-dasharray="2 4"' if s.get('dashPattern') else (' stroke-dasharray="6 4"' if s.get('dashed') == '1' else '')
        mk = {'open': ' marker-end="url(#open)"', 'block': ' marker-end="url(#block)"'}.get(s.get('endArrow', 'classic'), '')
        out.append(f'<polyline points="{" ".join(f"{a},{b}" for a, b in pts)}" fill="none" stroke="#000"{dash}{mk}/>')
        if e['v']:
            L = sum(math.dist(a, b) for a, b in zip(pts, pts[1:])); acc = 0
            for a, b in zip(pts, pts[1:]):
                d = math.dist(a, b)
                if acc + d >= L / 2:
                    t = (L / 2 - acc) / d; mx, my = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t; break
                acc += d
            lab = html.unescape(e['v']); lw = 6.5 * len(lab)
            out.append(f'<rect x="{mx-lw/2}" y="{my-8}" width="{lw}" height="16" fill="#fff"/><text x="{mx}" y="{my+4}" font-size="11" text-anchor="middle">{html.escape(lab)}</text>')
    out.append('</svg>')
    return ''.join(out)

if __name__ == '__main__':
    jobs = []
    for f in sys.argv[1:]:
        V, E, P, issues = check(f)
        name = f.split('/')[-1].rsplit('.', 1)[0]
        open(f'/tmp/{name}.html', 'w').write('<html><body style="margin:0">' + svg(V, E, P) + '</body></html>')
        jobs.append((name, issues))
    js = """const {chromium}=require('playwright');(async()=>{const b=await chromium.launch();const out={};
for(const n of process.argv.slice(1)){const p=await b.newPage();await p.goto('file:///tmp/'+n+'.html');
out[n]=await p.evaluate(()=>[...document.querySelectorAll('.lbl')].filter(d=>d.firstChild.scrollHeight>d.clientHeight-2||d.firstChild.scrollWidth>d.clientWidth+1).map(d=>d.textContent.slice(0,50)));
await p.screenshot({path:'/tmp/'+n+'.png',fullPage:true});}
console.log(JSON.stringify(out));await b.close();})();"""
    res = json.loads(subprocess.run(['node', '-e', js] + [n for n, _ in jobs], capture_output=True, text=True, check=True).stdout)
    for n, issues in jobs:
        issues += [f'TEXT OVERFLOW: {t}' for t in res[n]]
        print(n, '->', len(issues), 'issues'); [print('  ', i) for i in issues]
