"""Use case diagrams (draw.io), fixed grid layout so no line crosses a use case."""
import html, os
OUT = 'docs/srs-update/uc-diagrams'
UCS = "ellipse;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;fontFamily=Helvetica;fontSize=12;"
ACT = "shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#FFFFFF;strokeColor=#000000;whiteSpace=wrap;fontFamily=Helvetica;fontSize=12;"
NOTE = "shape=note;size=14;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;align=left;verticalAlign=top;spacingLeft=6;spacingRight=6;spacingTop=4;fontFamily=Helvetica;fontSize=11;"
SYS = "rounded=0;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;verticalAlign=top;align=center;fontStyle=1;spacingTop=8;fontFamily=Helvetica;fontSize=14;"
L2R = "html=1;rounded=0;strokeColor=#000000;endArrow=none;exitX=1;exitY=0.5;exitDx=0;exitDy=0;entryX=0;entryY=0.5;entryDx=0;entryDy=0;"
R2L = "html=1;rounded=0;strokeColor=#000000;endArrow=none;exitX=0;exitY=0.5;exitDx=0;exitDy=0;entryX=1;entryY=0.5;entryDx=0;entryDy=0;"
DEP = "html=1;rounded=0;strokeColor=#000000;endArrow=open;endFill=0;dashed=1;labelBackgroundColor=#FFFFFF;fontFamily=Helvetica;fontSize=11;"
GEN = "html=1;rounded=0;strokeColor=#000000;endArrow=block;endFill=0;endSize=12;"
DOT = "html=1;rounded=0;strokeColor=#000000;endArrow=none;dashed=1;dashPattern=1 3;"
SX, SY, ROW, UW, UH = 300, 60, 85, 250, 60

def build(name, ucs, left, right, assoc, deps=(), gens=(), notes=(), side_actors=(), actor_note=None):
    """ucs: (id, label, row, col). left/right: (id, label, row) main actor rows (float ok).
    assoc: (actor, uc). notes: (uc label, text). side_actors: (id, label, x, y) for generalisation parents."""
    two = any(c == 1 for *_, c in ucs)
    rows = max(r for _, _, r, _ in ucs) + 1
    sw = (UW + 50) * (2 if two else 1) + 10
    sh = 60 + rows * ROW
    cells = [f'<mxCell id="sys" value="Gym Management System" style="{SYS}" vertex="1" parent="1"><mxGeometry x="{SX}" y="{SY}" width="{sw}" height="{sh}" as="geometry"/></mxCell>']
    pos = {}
    for uid, label, r, c in ucs:
        x, y = SX + 30 + c * (UW + 50), SY + 55 + r * ROW
        pos[uid] = (x, y)
        cells.append(f'<mxCell id="{uid}" value="{html.escape(label)}" style="{UCS}" vertex="1" parent="1"><mxGeometry x="{x}" y="{y}" width="{UW}" height="{UH}" as="geometry"/></mxCell>')
    side = {}
    for aid, label, r in left:
        y = SY + 55 + r * ROW + UH / 2 - 30
        cells.append(f'<mxCell id="{aid}" value="{html.escape(label)}" style="{ACT}" vertex="1" parent="1"><mxGeometry x="{SX - 150}" y="{y}" width="30" height="60" as="geometry"/></mxCell>'); side[aid] = 'L'
    rx = SX + sw + 120
    for aid, label, r in right:
        y = SY + 55 + r * ROW + UH / 2 - 30
        cells.append(f'<mxCell id="{aid}" value="{html.escape(label)}" style="{ACT}" vertex="1" parent="1"><mxGeometry x="{rx}" y="{y}" width="30" height="60" as="geometry"/></mxCell>'); side[aid] = 'R'
    for aid, label, x, y in side_actors:
        cells.append(f'<mxCell id="{aid}" value="{html.escape(label)}" style="{ACT}" vertex="1" parent="1"><mxGeometry x="{x}" y="{y}" width="30" height="60" as="geometry"/></mxCell>')
    for i, (a, u) in enumerate(assoc):
        cells.append(f'<mxCell id="a{i}" style="{L2R if side[a] == "L" else R2L}" edge="1" parent="1" source="{a}" target="{u}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    for i, (s, t, kind) in enumerate(deps):
        (sx, sy), (tx, ty) = pos[s], pos[t]
        if sy == ty:
            st = f"exitX={1 if tx > sx else 0};exitY=0.5;exitDx=0;exitDy=0;entryX={0 if tx > sx else 1};entryY=0.5;entryDx=0;entryDy=0;"
        else:
            st = f"exitX={1 if tx > sx else 0};exitY={1 if ty > sy else 0};exitDx=0;exitDy=0;entryX={0 if tx > sx else 1};entryY=0.5;entryDx=0;entryDy=0;" if tx != sx else ""
        cells.append(f'<mxCell id="d{i}" value="«{kind}»" style="{DEP}{st}" edge="1" parent="1" source="{s}" target="{t}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    for i, (s, t) in enumerate(gens):
        cells.append(f'<mxCell id="g{i}" style="{GEN}" edge="1" parent="1" source="{s}" target="{t}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    nx, ny = rx + (170 if right else 40), SY
    for i, (uc, text) in enumerate(notes):
        h = 26 + 15 * (len(uc) + len(text) + 2) // 46
        cells.append(f'<mxCell id="n{i}" value="&lt;b&gt;{html.escape(uc)}:&lt;/b&gt; {html.escape(text)}" style="{NOTE}" vertex="1" parent="1"><mxGeometry x="{nx}" y="{ny}" width="300" height="{h}" as="geometry"/></mxCell>')
        ny += h + 14
    if actor_note:
        aid, text = actor_note
        cells.append(f'<mxCell id="an" value="{html.escape(text)}" style="{NOTE}" vertex="1" parent="1"><mxGeometry x="{SX - 290}" y="{SY}" width="230" height="55" as="geometry"/></mxCell>')
        cells.append(f'<mxCell id="and" style="{DOT}" edge="1" parent="1" source="an" target="{aid}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    xml = f'<mxfile host="app.diagrams.net"><diagram name="{name}" id="{name}"><mxGraphModel dx="1400" dy="1000" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1700" pageHeight="1500" background="#FFFFFF" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>{"".join(cells)}</root></mxGraphModel></diagram></mxfile>'
    open(os.path.join(OUT, name + '.drawio'), 'w', encoding='utf-8').write(xml)

build('UC_System_Actor',
 [('login','Login',0,0),('logout','Logout',1,0),('recover','Recover Password',2,0),('personal','Manage Personal Account',3,0),('notif','View In-App Notifications',4,0)],
 [('su','System User',2)], [('mail','Email / OTP Service',2.5)],
 [('su',u) for u in ['login','logout','recover','personal','notif']] + [('mail','recover'),('mail','personal')],
 gens=[('cus','su'),('staff','su'),('mgr','su'),('adm','su'),('pt','staff'),('rec','staff')],
 side_actors=[('cus','Customer',30,80),('staff','Staff',30,190),('mgr','Manager',30,300),('adm','Admin',30,410),('pt','PT',-130,140),('rec','Receptionist',-130,250)],
 notes=[('Login','sign in with email or phone + password, select role, switch role; accounts created at the counter use the temporary password sent by email'),
        ('Manage Personal Account','view profile, upload / remove avatar, change email (code to new email), change phone (code to current email), change password, view Dependent profiles (Customer only)'),
        ('View In-App Notifications','view the 10 latest notifications, mark one / all as read')])
build('UC_Guest',
 [('explore','Explore Services & Pricing',0,0),('reg','Register Account',1,0),('ident','Check Phone/Email Duplication & Identify Profile',0,1),('claim','Claim An Existing Profile Via Verification Code',2,1)],
 [('guest','Guest',1)], [('mail','Email / OTP Service',1.5)],
 [('guest','explore'),('guest','reg'),('mail','reg'),('mail','claim')],
 deps=[('reg','ident','include'),('claim','reg','extend')],
 notes=[('Explore Services & Pricing','home page (packages, PTs, opening hours, latest posts), About, Blog list, Blog detail')],
 actor_note=('guest','After Register Account (or Claim) succeeds, the Guest becomes a Customer.'))
build('UC_Customer',
 [('buy','Purchase A Package Via Self-Service',0,0),('book','Book A Single Session With A PT',1,0),('hist','Look Up Personal Transaction History',2,0),('sched','Track Personal Training Schedule',3,0),('status','Track Membership & Package Status',4,0),('plan','View Training Plan & Meal Plan From PT',5,0),('body','Track Body Measurements',6,0),('lib','Browse Exercise Library',7,0),('fb','Submit Feedback',8,0),
  ('voucher','Apply A Voucher To An Order',0,1),('pay','Process Payment & Record Transaction',1,1)],
 [('c','Customer',4)], [('vnp','Payment Gateway (VNPay)',1),('wger','Exercise Data Provider (wger)',7)],
 [('c',u) for u in ['buy','book','hist','sched','status','plan','body','lib','fb']] + [('vnp','pay'),('wger','lib')],
 deps=[('voucher','buy','extend'),('buy','pay','include'),('book','pay','include')],
 notes=[('Purchase A Package Via Self-Service','browse & filter packages, create order, pay via VNPay, view payment result, move the start date of an unused membership once (within 7 days)'),
        ('Track Personal Training Schedule','My Bookings (packages, next session, weekly template, call to action), My PT Sessions (week / month timetable, session detail); view only'),
        ('Track Membership & Package Status','customer dashboard: gym access today, packages, next sessions, warnings, attendance streak'),
        ('Track Body Measurements','record a measurement, view BMI / tiles / chart / history, delete a measurement'),
        ('Submit Feedback','send feedback, optionally rate a PT (1–5 stars, once per month)')])
build('UC_Staff',
 [('leave','Submit Leave Request',0,0),('slots','Declare Available Time Slots',1,0),('teach','Track Teaching Schedule & Assigned Clients',2,0),('handle','Handle A Session The PT Cannot Teach',3,0),('notes','Write Private Notes On Clients',4,0),('plans','Draft Training Plans & Meal Plans',5,0),('lib','Browse Exercise Library',6,0),
  ('prof','Manage Customer Profiles',7,0),('checkin','Check A Customer In At The Gym',8,0),('freeze','Freeze A Membership Package',9,0),('order','Create Order & Collect Payment At Counter',10,0),('sell','Sell A PT Package',11,0),('shift','Manage Shift',12,0),('maint','Create Facility Maintenance Request',13,0),
  ('ident','Check Phone/Email Duplication & Identify Profile',7,1),('voucher','Apply A Voucher To An Order',10,1),('pay','Process Payment & Record Transaction',11,1)],
 [('staff','Staff',0),('pt','PT',3.5),('rec','Receptionist',10)], [('wx','Weather Service (Open-Meteo)',2),('wger','Exercise Data Provider (wger)',6)],
 [('staff','leave')] + [('pt',u) for u in ['slots','teach','handle','notes','plans','lib']] + [('rec',u) for u in ['prof','checkin','freeze','order','sell','shift','maint']] + [('wx','teach'),('wger','lib')],
 deps=[('prof','ident','include'),('voucher','order','extend'),('order','pay','include'),('sell','pay','include')],
 gens=[('pt','staff'),('rec','staff')],
 notes=[('Submit Leave Request','create a request (Annual / Unpaid, date range, shift), accept the Annual + Unpaid split, withdraw a Pending request'),
        ('Declare Available Time Slots','add draft slot, withdraw free slot, remove all free slots, copy week to next week, publish changes'),
        ('Handle A Session The PT Cannot Teach','reschedule (own free slot within 14 days), transfer to another PT, cancel with cash refund'),
        ('Manage Customer Profiles','view list / detail, create Self profile & account (temporary password by email) or Dependent profile, update profile, capture check-in photo, request deletion'),
        ('Sell A PT Package','choose package & PT, build the weekly schedule, choose customer(s), hold 15 minutes, collect payment, release hold'),
        ('Manage Shift','open shift, view shift transactions, confirm bank transfer, cancel counter order, count cash & close shift (hand-over)'),
        ('Create Facility Maintenance Request','report broken equipment items (two steps), view own requests, close a repaired request')])
build('UC_Manager',
 [(u,l,i,0) for i,(u,l) in enumerate([('param','Configure System Parameters'),('pkg','Manage Service Package Catalog'),('hours','Configure Operating Hours'),('vou','Manage Vouchers'),('leave','Process Staff Leave Requests'),('pers','Manage Personnel Records'),('del','Process Customer Deletion Requests'),('fac','Manage Facility/Equipment Catalog'),('maint','Process Facility Maintenance Requests'),('tx','Look Up System-Wide Transaction History'),('fb','View Customer Feedback'),('blog','Manage Blog Posts'),('notif','Send Notifications'),('dash','View Aggregated Dashboard')])],
 [('m','Manager',6.5)], [],
 [('m',u) for u in ['param','pkg','hours','vou','leave','pers','del','fac','maint','tx','fb','blog','notif','dash']],
 notes=[('Manage Service Package Catalog','view membership / day-pass and PT package lists, create package, edit package, stop selling / sell again'),
        ('Manage Vouchers','create voucher, view list / detail, edit voucher, deactivate voucher'),
        ('Process Staff Leave Requests','view queue, view detail, approve, bulk approve, approve late, reject with reason'),
        ('Manage Personnel Records','view staff list / detail, edit personnel details, record resignation'),
        ('Manage Facility/Equipment Catalog','add / edit facility group, show / hide group, add / edit equipment item, retire item'),
        ('Process Facility Maintenance Requests','view queue, approve (note, set under maintenance), reject with reason'),
        ('Look Up System-Wide Transaction History','search & filter, export to Excel, confirm refund'),
        ('Manage Blog Posts','create post, view list / detail, edit post, publish / unpublish'),
        ('View Aggregated Dashboard','operational & revenue report for a date range, export CSV')])
build('UC_Admin',
 [('acc','Manage System Accounts',0,0),('audit','View Audit Log',1,0),('rep','View Operational Reports & Audit Metrics',2,0)],
 [('a','Admin',1)], [],
 [('a','acc'),('a','audit'),('a','rep')],
 notes=[('Manage System Accounts','create staff account (Admin, Manager, PT, Receptionist), view account list / detail, change position, lock / unlock account (reason 5–500), unlock temporarily locked customer accounts'),
        ('View Audit Log','search and filter sensitive actions, each shown as a readable sentence'),
        ('View Operational Reports & Audit Metrics','operational & revenue report for a date range plus audit metrics, export CSV')])
print('ok')
