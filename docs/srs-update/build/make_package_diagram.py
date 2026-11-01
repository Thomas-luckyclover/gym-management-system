"""Package diagram of com.gms (draw.io). Dependencies come from the import statements of branch develop.
Layered layout, every edge drawn with explicit orthogonal waypoints (no auto-routing)."""
OUT = 'docs/srs-update/diagrams/GMS_Package_Diagram.drawio'
F = "fontFamily=Helvetica;"
PKG = "shape=folder;fontStyle=1;spacingTop=10;tabWidth=44;tabHeight=14;tabPosition=left;html=1;whiteSpace=wrap;fillColor=#FFFFFF;strokeColor=#000000;" + F + "fontSize=13;"
FRAME = "shape=folder;fontStyle=1;tabWidth=60;tabHeight=16;tabPosition=left;html=1;whiteSpace=wrap;fillColor=none;strokeColor=#000000;verticalAlign=top;align=left;spacingLeft=10;spacingTop=16;" + F + "fontSize=12;"
NOTE = "shape=note;size=14;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;align=left;verticalAlign=middle;spacingLeft=8;spacingRight=8;" + F + "fontSize=11;"
TITLE = "text;html=1;strokeColor=none;fillColor=none;align=left;verticalAlign=middle;fontStyle=1;" + F + "fontSize=15;"
DEP = "edgeStyle=none;html=1;rounded=0;strokeColor=#000000;dashed=1;endArrow=open;endFill=0;endSize=10;"
H = 70
P = {  # id: (x, y, w)
    'servlet': (300, 100, 200), 'job': (560, 100, 160), 'filter': (780, 100, 160),
    'service': (300, 260, 560), 'listener': (940, 260, 160),
    'dao': (300, 420, 160), 'dto': (560, 420, 160), 'integration': (780, 420, 160),
    'entity': (300, 580, 160),
    'constant': (320, 790, 160), 'util': (560, 790, 160),
}
# (source, target, [points]) — first/last point lie on the source/target border
E = [
    ('servlet', 'service', [(400, 170), (400, 260)]),
    ('job', 'service', [(640, 170), (640, 260)]),
    ('filter', 'service', [(820, 170), (820, 260)]),
    ('service', 'listener', [(860, 295), (940, 295)]),
    ('service', 'dao', [(380, 330), (380, 420)]),
    ('service', 'dto', [(640, 330), (640, 420)]),
    ('service', 'integration', [(840, 330), (840, 420)]),
    ('service', 'entity', [(300, 310), (270, 310), (270, 600), (300, 600)]),
    ('servlet', 'dao', [(300, 150), (250, 150), (250, 455), (300, 455)]),
    ('servlet', 'entity', [(300, 125), (230, 125), (230, 630), (300, 630)]),
    ('servlet', 'integration', [(480, 100), (480, 75), (1175, 75), (1175, 455), (940, 455)]),
    ('servlet', 'dto', [(450, 100), (450, 60), (1200, 60), (1200, 540), (600, 540), (600, 490)]),
    ('filter', 'dto', [(940, 135), (1150, 135), (1150, 520), (680, 520), (680, 490)]),
    ('integration', 'dto', [(780, 455), (720, 455)]),
    ('dao', 'dto', [(460, 440), (560, 440)]),
    ('dto', 'dao', [(560, 470), (460, 470)]),
    ('dao', 'entity', [(380, 490), (380, 580)]),
    ('dto', 'entity', [(560, 484), (460, 605)]),
    ('util', 'dto', [(575, 790), (575, 490)]),
    ('util', 'constant', [(560, 825), (480, 825)]),
]
cells = ['<mxCell id="title" value="GMS — Package Diagram (com.gms)" style="%s" vertex="1" parent="1"><mxGeometry x="40" y="20" width="400" height="30" as="geometry"/></mxCell>' % TITLE,
         '<mxCell id="shared" value="shared" style="%s" vertex="1" parent="1"><mxGeometry x="290" y="728" width="460" height="150" as="geometry"/></mxCell>' % FRAME]
for k, (x, y, w) in P.items():
    cells.append(f'<mxCell id="{k}" value="{k}" style="{PKG}" vertex="1" parent="1"><mxGeometry x="{x}" y="{y}" width="{w}" height="{H}" as="geometry"/></mxCell>')
for i, (s, t, pts) in enumerate(E):
    (sx, sy, sw), (tx, ty, tw) = P[s], P[t]
    def body(pt, x, y):  # a folder's body starts below its tab: move top-edge points off the tab line
        return (pt[0], y + 14) if pt[1] == y and pt[0] > x + 44 else pt
    pts = [body(pts[0], sx, sy)] + pts[1:-1] + [body(pts[-1], tx, ty)]
    (ax, ay), (bx, by) = pts[0], pts[-1]
    st = DEP + f"exitX={(ax - sx) / sw:.4f};exitY={(ay - sy) / H:.4f};exitDx=0;exitDy=0;entryX={(bx - tx) / tw:.4f};entryY={(by - ty) / H:.4f};entryDx=0;entryDy=0;"
    way = ''.join(f'<mxPoint x="{x}" y="{y}"/>' for x, y in pts[1:-1])
    geo = f'<mxGeometry relative="1" as="geometry"><Array as="points">{way}</Array></mxGeometry>' if way else '<mxGeometry relative="1" as="geometry"/>'
    cells.append(f'<mxCell id="e{i}" value="" style="{st}" edge="1" parent="1" source="{s}" target="{t}">{geo}</mxCell>')
note = ("&lt;b&gt;Notation:&lt;/b&gt; folder = package com.gms.*; dashed open arrow A → B = a class in A imports a class in B.&lt;br&gt;"
        "&lt;b&gt;shared&lt;/b&gt; packages are used by almost every package, so their arrows are not drawn one by one:&lt;br&gt;"
        "• constant ← dao, dto, entity, filter, listener, service, servlet, util&lt;br&gt;"
        "• util ← dao, dto, filter, integration, job, service, servlet")
cells.append(f'<mxCell id="note" value="{note}" style="{NOTE}" vertex="1" parent="1"><mxGeometry x="790" y="728" width="420" height="96" as="geometry"/></mxCell>')
xml = ('<mxfile host="app.diagrams.net"><diagram name="Package Diagram" id="pkg"><mxGraphModel dx="1300" dy="950" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1300" pageHeight="950" background="#FFFFFF" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>%s</root></mxGraphModel></diagram></mxfile>' % ''.join(cells))
open(OUT, 'w', encoding='utf-8').write(xml)
print('ok', len(E), 'edges')
