"""Regenerate the 5 use case diagrams (draw.io) from the current UC list."""
import html, os
OUT = 'docs/srs-update/uc-diagrams'
UC = "ellipse;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;fontFamily=Helvetica;fontSize=12;"
ACT = "shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#FFFFFF;strokeColor=#000000;whiteSpace=wrap;fontFamily=Helvetica;fontSize=12;"
NOTE = "shape=note;size=14;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;align=left;spacingLeft=6;spacingRight=4;fontFamily=Helvetica;fontSize=11;"
SYS = "rounded=0;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;verticalAlign=top;align=center;fontStyle=1;spacingTop=8;fontFamily=Helvetica;fontSize=14;"
ASSOC = "html=1;rounded=0;strokeColor=#000000;endArrow=none;"
DEP = "html=1;rounded=0;strokeColor=#000000;endArrow=open;endFill=0;dashed=1;labelBackgroundColor=#FFFFFF;fontFamily=Helvetica;fontSize=11;"
GEN = "html=1;rounded=0;strokeColor=#000000;endArrow=block;endFill=0;endSize=12;"
DOT = "html=1;rounded=0;strokeColor=#000000;endArrow=none;dashed=1;dashPattern=1 3;"

def diagram(name, ucs, actors, links, extra=(), notes=(), gens=(), sys_x=260, col2=None):
    """ucs: [(id, label, col)], col 0 = main column, 1 = second column (included/extended)."""
    cells, y = [], {0: 90, 1: 90}
    pos = {}
    for uid, label, col in ucs:
        x = sys_x + 30 + col * 290
        pos[uid] = (x, y[col]); y[col] += 72
    h = max(y.values()) + 10 - 40
    w = 290 * (1 + any(c == 1 for _, _, c in ucs)) + 60
    cells.append(f'<mxCell id="sys" value="Gym Management System" style="{SYS}" vertex="1" parent="1"><mxGeometry x="{sys_x}" y="40" width="{w}" height="{h}" as="geometry"/></mxCell>')
    for uid, label, col in ucs:
        x, yy = pos[uid]
        cells.append(f'<mxCell id="{uid}" value="{html.escape(label)}" style="{UC}" vertex="1" parent="1"><mxGeometry x="{x}" y="{yy}" width="230" height="52" as="geometry"/></mxCell>')
    for aid, label, x, yy in actors:
        cells.append(f'<mxCell id="{aid}" value="{html.escape(label)}" style="{ACT}" vertex="1" parent="1"><mxGeometry x="{x}" y="{yy}" width="30" height="60" as="geometry"/></mxCell>')
    nid = 0
    for nid_, text, x, yy, hh, target in notes:
        cells.append(f'<mxCell id="{nid_}" value="{html.escape(text)}" style="{NOTE}" vertex="1" parent="1"><mxGeometry x="{x}" y="{yy}" width="260" height="{hh}" as="geometry"/></mxCell>')
        cells.append(f'<mxCell id="d_{nid_}" style="{DOT}" edge="1" parent="1" source="{target}" target="{nid_}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    for i, (a, u) in enumerate(links):
        cells.append(f'<mxCell id="a{i}" style="{ASSOC}" edge="1" parent="1" source="{a}" target="{u}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    for i, (s, t, kind) in enumerate(extra):
        cells.append(f'<mxCell id="x{i}" value="«{kind}»" style="{DEP}" edge="1" parent="1" source="{s}" target="{t}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    for i, (s, t) in enumerate(gens):
        cells.append(f'<mxCell id="g{i}" style="{GEN}" edge="1" parent="1" source="{s}" target="{t}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    xml = ('<mxfile host="app.diagrams.net"><diagram name="%s" id="%s"><mxGraphModel dx="1200" dy="900" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1400" pageHeight="1500" background="#FFFFFF" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>%s</root></mxGraphModel></diagram></mxfile>' % (name, name, ''.join(cells)))
    open(os.path.join(OUT, name + '.drawio'), 'w', encoding='utf-8').write(xml)
    return pos

# ---------- System User ----------
p = diagram('UC_System_Actor',
 [('login','Login',0),('logout','Logout',0),('recover','Recover Password',0),('personal','Manage Personal Account',0),('notif','View In-App Notifications',0)],
 [('su','System User',150,230),('cus','Customer',-60,90),('staff','Staff',-60,190),('mgr','Manager',-60,290),('adm','Admin',-60,390),('pt','PT',-260,150),('rec','Receptionist',-260,250),('mail','Email / OTP Service',640,260)],
 [('su',u) for u in ['login','logout','recover','personal','notif']] + [('mail','recover'),('mail','personal')],
 gens=[('cus','su'),('staff','su'),('mgr','su'),('adm','su'),('pt','staff'),('rec','staff')],
 notes=[('n_login','Login: sign in with email or phone + password, select role, switch role. Accounts created at the counter sign in with the temporary password received by email.',600,60,70,'login'),
        ('n_pers','Manage Personal Account: view profile, upload / remove avatar, change email (code to new email), change phone (code to current email), change password, view Dependent profiles (Customer only).',600,360,85,'personal'),
        ('n_notif','View In-App Notifications: view the 10 latest notifications, mark one / all as read.',600,460,50,'notif')])
# ---------- Guest ----------
diagram('UC_Guest',
 [('explore','Explore Services & Pricing',0),('reg','Register Account',0),('ident','Check Phone/Email Duplication & Identify Profile',1),('claim','Claim An Existing Profile Via Verification Code',1)],
 [('guest','Guest',140,200),('mail','Email / OTP Service',900,180)],
 [('guest','explore'),('guest','reg'),('mail','reg'),('mail','claim')],
 extra=[('reg','ident','include'),('claim','reg','extend')],
 notes=[('n1','After Register Account (or Claim) succeeds, the Guest becomes a Customer.',20,60,50,'guest'),
        ('n_exp','Explore Services & Pricing: home page (packages, PTs, opening hours, latest posts), About, Blog list, Blog detail.',560,320,65,'explore')])
# ---------- Customer ----------
diagram('UC_Customer',
 [('buy','Purchase A Package Via Self-Service',0),('book','Book A Single Session With A PT',0),('hist','Look Up Personal Transaction History',0),('sched','Track Personal Training Schedule',0),('status','Track Membership & Package Status',0),('plan','View Training Plan & Meal Plan From PT',0),('body','Track Body Measurements',0),('lib','Browse Exercise Library',0),('fb','Submit Feedback',0),
  ('voucher','Apply A Voucher To An Order',1),('pay','Process Payment & Record Transaction',1)],
 [('c','Customer',130,380),('vnp','Payment Gateway (VNPay)',900,170),('wger','Exercise Data Provider (wger)',900,600)],
 [('c',u) for u in ['buy','book','hist','sched','status','plan','body','lib','fb']] + [('vnp','pay'),('wger','lib')],
 extra=[('voucher','buy','extend'),('buy','pay','include'),('book','pay','include')],
 notes=[('n_buy','Purchase A Package Via Self-Service: browse & filter packages, create order, pay via VNPay, view payment result, move start date of an unused membership once (within 7 days).',980,40,85,'buy'),
        ('n_sched','Track Personal Training Schedule: My Bookings (packages, next session, weekly template, call to action), My PT Sessions (week / month timetable, session detail). View only.',980,300,85,'sched'),
        ('n_status','Track Membership & Package Status: customer dashboard (gym access today, packages, next sessions, warnings, attendance streak).',980,400,70,'status'),
        ('n_body','Track Body Measurements: record measurement, view BMI / tiles / chart / history, delete measurement.',980,490,60,'body'),
        ('n_fb','Submit Feedback: send feedback, optionally rate a PT (1–5 stars, once per month).',980,700,55,'fb')])
# ---------- Staff (PT + Receptionist) ----------
diagram('UC_Staff',
 [('leave','Submit Leave Request',0),('slots','Declare Available Time Slots',0),('teach','Track Teaching Schedule & Assigned Clients',0),('handle','Handle A Session The PT Cannot Teach',0),('notes','Write Private Notes On Clients',0),('plans','Draft Training Plans & Meal Plans',0),('lib','Browse Exercise Library',0),
  ('prof','Manage Customer Profiles',0),('checkin','Check A Customer In At The Gym',0),('freeze','Freeze A Membership Package',0),('order','Create Order & Collect Payment At Counter',0),('sell','Sell A PT Package',0),('shift','Manage Shift',0),('maint','Create Facility Maintenance Request',0),
  ('ident','Check Phone/Email Duplication & Identify Profile',1),('voucher','Apply A Voucher To An Order',1),('pay','Process Payment & Record Transaction',1)],
 [('staff','Staff',40,90),('pt','PT',150,380),('rec','Receptionist',150,880),('wx','Weather Service (Open-Meteo)',900,230),('wger','Exercise Data Provider (wger)',900,520)],
 [('staff','leave')] + [('pt',u) for u in ['slots','teach','handle','notes','plans','lib']] + [('rec',u) for u in ['prof','checkin','freeze','order','sell','shift','maint']] + [('wx','teach'),('wger','lib')],
 extra=[('prof','ident','include'),('voucher','order','extend'),('order','pay','include'),('sell','pay','include')],
 gens=[('pt','staff'),('rec','staff')],
 notes=[('n_leave','Submit Leave Request: create request (Annual / Unpaid, date range, shift), accept Annual + Unpaid split, withdraw a Pending request.',980,60,70,'leave'),
        ('n_slots','Declare Available Time Slots: add draft slot, withdraw free slot, remove all free slots, copy week to next week, publish changes.',980,140,70,'slots'),
        ('n_handle','Handle A Session The PT Cannot Teach: reschedule (own free slot within 14 days), transfer to another PT, cancel with cash refund.',980,300,70,'handle'),
        ('n_prof','Manage Customer Profiles: view list / detail, create Self profile & account (temporary password by email) or Dependent profile, update profile, capture check-in photo, request deletion.',980,640,85,'prof'),
        ('n_sell','Sell A PT Package: choose package & PT, build weekly schedule, choose customer(s), hold 15 minutes, collect payment, release hold.',980,880,70,'sell'),
        ('n_shift','Manage Shift: open shift, view shift transactions, confirm bank transfer, cancel counter order, count cash & close shift (hand-over).',980,960,70,'shift'),
        ('n_maint','Create Facility Maintenance Request: report broken equipment items (two steps), view own requests, close a repaired request.',980,1040,70,'maint')])
# ---------- Manager ----------
diagram('UC_Manager',
 [('param','Configure System Parameters',0),('pkg','Manage Service Package Catalog',0),('hours','Configure Operating Hours',0),('vou','Manage Vouchers',0),('leave','Process Staff Leave Requests',0),('pers','Manage Personnel Records',0),('del','Process Customer Deletion Requests',0),('fac','Manage Facility/Equipment Catalog',0),('maint','Process Facility Maintenance Requests',0),('tx','Look Up System-Wide Transaction History',0),('fb','View Customer Feedback',0),('blog','Manage Blog Posts',0),('notif','Send Notifications',0),('dash','View Aggregated Dashboard',0)],
 [('m','Manager',90,560)],
 [('m',u) for u in ['param','pkg','hours','vou','leave','pers','del','fac','maint','tx','fb','blog','notif','dash']],
 notes=[('n_pkg','Manage Service Package Catalog: view membership / day-pass and PT package lists, create package, edit package, stop selling / sell again.',620,150,70,'pkg'),
        ('n_vou','Manage Vouchers: create voucher, view list / detail, edit voucher, deactivate voucher.',620,300,55,'vou'),
        ('n_leave','Process Staff Leave Requests: view queue, view detail, approve, bulk approve, approve late, reject with reason.',620,370,70,'leave'),
        ('n_pers','Manage Personnel Records: view staff list / detail, edit personnel details, record resignation.',620,450,55,'pers'),
        ('n_fac','Manage Facility/Equipment Catalog: add / edit facility group, show / hide group, add / edit equipment item, retire item.',620,590,70,'fac'),
        ('n_maint','Process Facility Maintenance Requests: view queue, approve (note, set under maintenance), reject with reason.',620,670,70,'maint'),
        ('n_tx','Look Up System-Wide Transaction History: search & filter, export to Excel, confirm refund.',620,750,55,'tx'),
        ('n_blog','Manage Blog Posts: create post, view list / detail, edit post, publish / unpublish.',620,880,55,'blog'),
        ('n_dash','View Aggregated Dashboard: operational & revenue report for a date range, export CSV.',620,1020,55,'dash')])
print('ok', sorted(os.listdir(OUT)))
# ---------- Admin ----------
diagram('UC_Admin',
 [('acc','Manage System Accounts',0),('audit','View Audit Log',0),('rep','View Operational Reports & Audit Metrics',0)],
 [('a','Admin',90,150)],
 [('a','acc'),('a','audit'),('a','rep')],
 notes=[('n_acc','Manage System Accounts: create staff account (Admin, Manager, PT, Receptionist), view account list / detail, change position, lock / unlock account (reason 5–500), unlock temporarily locked customer accounts.',620,60,85,'acc'),
        ('n_audit','View Audit Log: search and filter sensitive actions, each shown as a readable sentence.',620,170,55,'audit'),
        ('n_rep','View Operational Reports & Audit Metrics: operational & revenue report for a date range plus audit metrics, export CSV.',620,240,70,'rep')])
