"""Use case diagrams (draw.io) in the team's template style.
Fixed rows; every edge pinned to side mid-points; notes = Functions of a UC, linked by a dotted line."""
import html, math, os
OUT = 'docs/srs-update/uc-diagrams'
F = "fontFamily=Helvetica;"
UCS = "ellipse;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;" + F + "fontSize=12;"
ACT = "shape=umlActor;verticalLabelPosition=bottom;verticalAlign=top;html=1;outlineConnect=0;fillColor=#FFFFFF;strokeColor=#000000;whiteSpace=wrap;" + F + "fontSize=12;"
NOTE = "shape=note;size=14;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;align=left;verticalAlign=middle;spacingLeft=6;spacingRight=6;" + F + "fontSize=11;"
SYS = "rounded=0;whiteSpace=wrap;html=1;fillColor=#FFFFFF;strokeColor=#000000;verticalAlign=top;align=center;fontStyle=1;spacingTop=8;" + F + "fontSize=14;"
LINE = "html=1;rounded=0;strokeColor=#000000;endArrow=none;"
DEP = "html=1;rounded=0;strokeColor=#000000;endArrow=open;endFill=0;dashed=1;labelBackgroundColor=#FFFFFF;" + F + "fontSize=11;"
GEN = "html=1;rounded=0;strokeColor=#000000;endArrow=block;endFill=0;endSize=12;"
DOT = "html=1;rounded=0;strokeColor=#000000;endArrow=none;dashed=1;dashPattern=1 3;"
SX, SY, UW, UH, NW = 300, 40, 210, 54, 300

def pin(ex, ey, nx, ny):
    return f"exitX={ex};exitY={ey};exitDx=0;exitDy=0;entryX={nx};entryY={ny};entryDx=0;entryDy=0;"

def note_h(text):
    return 14 + 14 * math.ceil(len(text) / 44)

def build(name, ucs, row=72, left=(), right=(), assoc=(), deps=(), gens=(), notes=None, actor_note=None, notes_near=True, right_far=False, cond_notes=()):
    """ucs: (id, label, row, col). left: (id, label, x, row). right: (id, label, row). notes: {uc: functions}."""
    notes = notes or {}
    cols = 1 + max(c for *_, c in ucs)
    bw = 28 + UW * cols + 84 * (cols - 1) + 28
    ux = lambda c: SX + 28 + c * (UW + 84)
    uy = lambda r: SY + 50 + r * row
    rows = max(r for _, _, r, _ in ucs)
    bh = 50 + rows * row + UH + 28
    C = [f'<mxCell id="sys" value="Gym Management System" style="{SYS}" vertex="1" parent="1"><mxGeometry x="{SX}" y="{SY}" width="{bw}" height="{bh}" as="geometry"/></mxCell>']
    V = lambda i, v, s, x, y, w, h: C.append(f'<mxCell id="{i}" value="{v}" style="{s}" vertex="1" parent="1"><mxGeometry x="{x:.0f}" y="{y:.0f}" width="{w}" height="{h}" as="geometry"/></mxCell>')
    Ed = lambda i, s, a, b, v='': C.append(f'<mxCell id="{i}" value="{v}" style="{s}" edge="1" parent="1" source="{a}" target="{b}"><mxGeometry relative="1" as="geometry"/></mxCell>')
    pos = {}
    for uid, label, r, c in ucs:
        pos[uid] = (ux(c), uy(r)); V(uid, html.escape(label), UCS, ux(c), uy(r), UW, UH)
    side = {}
    for aid, label, x, r in left:
        V(aid, html.escape(label), ACT, x, uy(r) + UH / 2 - 20, 30, 60); side[aid] = 'L'
    ax = SX + bw + (NW + 190 if right_far else 100)
    for aid, label, r in right:
        V(aid, '&lt;i&gt;«system»&lt;/i&gt;&lt;br&gt;' + html.escape(label), ACT, ax, uy(r) + UH / 2 - 20, 30, 60); side[aid] = 'R'
    for i, (a, u) in enumerate(assoc):
        Ed(f"a{i}", LINE + (pin(1, 0.333, 0, 0.5) if side[a] == 'L' else pin(0, 0.333, 1, 0.5)), a, u)
    for i, (s, t, kind) in enumerate(deps):
        right_ = pos[t][0] > pos[s][0]
        Ed(f"d{i}", DEP + pin(1 if right_ else 0, 0.5, 0 if right_ else 1, 0.5), s, t, f"«{kind}»")
    for i, (s, t, st) in enumerate(gens):
        Ed(f"g{i}", GEN + st, s, t)
    nx = (SX + bw + 60) if (notes_near or not right) else (ax + 150)
    prev = -1e9
    for i, uid in enumerate(sorted(notes, key=lambda u: pos[u][1])):
        text = notes[uid]; h = note_h('Functions: ' + text)
        y = max(pos[uid][1] + UH / 2 - h / 2, prev + 12); prev = y + h
        V(f"n{i}", f"&lt;b&gt;Functions:&lt;/b&gt; {html.escape(text)}", NOTE, nx, y, NW, h)
        Ed(f"nl{i}", DOT + pin(1, 0.5, 0, 0.5), uid, f"n{i}")
    for i, (uid, text, x, y, w) in enumerate(cond_notes):
        V(f"cn{i}", text, NOTE, x, y, w, note_h(text))
        Ed(f"cnl{i}", DOT + pin(0.5, 0, 0.5, 1), f"cn{i}", uid)
    if actor_note:
        aid, text, x, y = actor_note
        V("an", html.escape(text), NOTE, x, y, 230, note_h(text))
        Ed("anl", DOT + pin(0.5, 0, 0.5, 1), aid, "an")
    xml = ('<mxfile host="app.diagrams.net"><diagram name="%s" id="%s"><mxGraphModel dx="1400" dy="1000" grid="0" gridSize="10" guides="1" tooltips="1" connect="1" arrows="1" fold="1" page="1" pageScale="1" pageWidth="1600" pageHeight="1400" background="#FFFFFF" math="0" shadow="0"><root><mxCell id="0"/><mxCell id="1" parent="0"/>%s</root></mxGraphModel></diagram></mxfile>' % (name, name, ''.join(C)))
    open(os.path.join(OUT, name + '.drawio'), 'w', encoding='utf-8').write(xml)

# ---------------------------------------------------------------- System User
build('UC_System_Actor',
 [('login', 'Login', 0, 0), ('logout', 'Logout', 1, 0), ('recover', 'Recover Password', 2, 0), ('personal', 'Manage Personal Account', 3, 0), ('notif', 'View In-App Notifications', 4, 0)],
 left=[('su', 'System User', SX - 130, 2), ('cus', 'Customer', SX - 340, 0.2), ('staff', 'Staff', SX - 340, 1.55), ('mgr', 'Manager', SX - 340, 2.9), ('adm', 'Admin', SX - 340, 4.25),
       ('pt', 'PT', SX - 560, 0.75), ('rec', 'Receptionist', SX - 560, 2.35)],
 right=[('mail', 'Email / OTP Service', 2)],
 assoc=[('su', u) for u in ['login', 'logout', 'recover', 'personal', 'notif']] + [('mail', 'recover'), ('mail', 'personal')],
 gens=[(c, 'su', pin(1, 0.333, 0, y)) for c, y in [('cus', 0.1), ('staff', 0.3), ('mgr', 0.5), ('adm', 0.7)]] + [('pt', 'staff', pin(1, 0.333, 0, 0.3)), ('rec', 'staff', pin(1, 0.333, 0, 0.7))],
 notes={'login': 'Sign In With Email Or Phone + Password, Select Role, Switch Role',
        'personal': 'View Profile, Upload / Remove Avatar, Change Email (Code To New Email), Change Phone (Code To Current Email), Change Password, View Dependent Profiles',
        'notif': 'View Latest Notifications, Mark One As Read, Mark All As Read'},
 notes_near=False)
# ---------------------------------------------------------------- Guest
build('UC_Guest',
 [('ident', 'Check Phone/Email Duplication & Identify Profile', 0, 1), ('reg', 'Register Account', 1, 0), ('claim', 'Claim An Existing Profile Via Verification Code', 2, 1), ('explore', 'Explore Services & Pricing', 4, 0)],
 left=[('guest', 'Guest', SX - 130, 2.5)], right=[('mail', 'Email / OTP Service', 1.5)],
 assoc=[('guest', 'reg'), ('guest', 'explore'), ('mail', 'reg'), ('mail', 'claim')],
 deps=[('reg', 'ident', 'include'), ('claim', 'reg', 'extend')],
 notes={'explore': 'View Home Page, View About Page, View Blog List, View Blog Detail'},
 cond_notes=[('claim', '&lt;b&gt;Condition:&lt;/b&gt; the phone number belongs to a Self profile that has no login account&lt;br&gt;&lt;b&gt;Extension point:&lt;/b&gt; Phone Check', SX + 28 + 294, SY + 50 + 3 * 72 + 4, 210)],
 actor_note=('guest', 'After Register Account (or Claim) succeeds and the account is active, the Guest becomes a Customer.', SX - 250, SY + 20))
# ---------------------------------------------------------------- Customer
build('UC_Customer',
 [('book', 'Book A Single Session With A PT', 0, 0), ('buy', 'Purchase A Package Via Self-Service', 1, 0), ('hist', 'Look Up Personal Transaction History', 2, 0),
  ('sched', 'Track Personal Training Schedule', 3, 0), ('status', 'Track Membership & Package Status', 4, 0), ('plan', 'View Training Plan & Meal Plan From PT', 5, 0),
  ('body', 'Track Body Measurements', 6, 0), ('lib', 'Browse Exercise Library', 7, 0), ('fb', 'Submit Feedback', 8, 0),
  ('pay', 'Process Payment & Record Transaction', 0, 1), ('voucher', 'Apply A Voucher To An Order', 2, 1)],
 left=[('c', 'Customer', SX - 130, 4)], right=[('vnp', 'Payment Gateway (VNPay)', 0), ('wger', 'Exercise Data Provider (wger)', 7)],
 assoc=[('c', u) for u in ['book', 'buy', 'hist', 'sched', 'status', 'plan', 'body', 'lib', 'fb']] + [('vnp', 'pay'), ('wger', 'lib')],
 deps=[('book', 'pay', 'include'), ('buy', 'pay', 'include'), ('voucher', 'buy', 'extend')],
 notes={'buy': 'Browse & Filter Packages, Create Order, Pay Via VNPay, View Payment Result, Retry Payment, Move Start Date Once',
        'sched': 'View My Bookings, View My PT Sessions (Week / Month), View Session Detail',
        'status': 'View Gym Access Today, View Packages, View Next Sessions, View Warnings, View Attendance Streak',
        'body': 'Record Measurement, View BMI / Chart / History, Delete Measurement',
        'fb': 'Send Feedback, Rate A PT (1–5 Stars, Once Per Month)'},
 right_far=True)
# ---------------------------------------------------------------- Staff
build('UC_Staff',
 [('leave', 'Submit Leave Request', 0, 0), ('slots', 'Declare Available Time Slots', 1, 0), ('teach', 'Track Teaching Schedule & Assigned Clients', 2, 0),
  ('handle', 'Handle A Session The PT Cannot Teach', 3, 0), ('notes', 'Write Private Notes On Clients', 4, 0), ('plans', 'Draft Training Plans & Meal Plans', 5, 0),
  ('lib', 'Browse Exercise Library', 6, 0), ('prof', 'Manage Customer Profiles', 7, 0), ('checkin', 'Check A Customer In At The Gym', 8, 0),
  ('freeze', 'Freeze A Membership Package', 9, 0), ('order', 'Create Order & Collect Payment At Counter', 10, 0), ('sell', 'Sell A PT Package', 11, 0),
  ('shift', 'Manage Shift', 12, 0), ('maint', 'Create Facility Maintenance Request', 13, 0),
  ('ident', 'Check Phone/Email Duplication & Identify Profile', 8, 1), ('voucher', 'Apply A Voucher To An Order', 9, 1), ('pay', 'Process Payment & Record Transaction', 10, 1)],
 row=80,
 left=[('staff', 'Staff', SX - 270, 1.4), ('pt', 'PT', SX - 130, 3.5), ('rec', 'Receptionist', SX - 130, 10)],
 right=[('wx', 'Weather Service (Open-Meteo)', 2), ('wger', 'Exercise Data Provider (wger)', 5.75)],
 assoc=[('staff', 'leave')] + [('pt', u) for u in ['slots', 'teach', 'handle', 'notes', 'plans', 'lib']] + [('rec', u) for u in ['prof', 'checkin', 'freeze', 'order', 'sell', 'shift', 'maint']] + [('wx', 'teach'), ('wger', 'lib')],
 deps=[('prof', 'ident', 'include'), ('voucher', 'order', 'extend'), ('order', 'pay', 'include'), ('sell', 'pay', 'include')],
 gens=[('pt', 'staff', pin(0.5, 0, 1, 0.55)), ('rec', 'staff', pin(0.5, 0, 1, 0.8))],
 right_far=True,
 notes={'leave': 'Create Request (Annual / Unpaid, Date Range, Shift), Accept Annual + Unpaid Split, Withdraw Pending Request',
        'slots': 'Add Draft Slot, Withdraw Free Slot, Remove All Free Slots, Copy Week To Next Week, Publish Changes',
        'handle': 'Reschedule (Own Free Slot Within 14 Days), Transfer To Another PT, Cancel With Cash Refund',
        'prof': 'View Customer List / Detail, Create Self Profile & Account, Create Dependent Profile, Update Profile, Capture Check-in Photo, Request Deletion',
        'sell': 'Choose Package & PT, Build Weekly Schedule, Choose Customer(s), Hold Slots 15 Minutes, Collect Payment, Release Hold',
        'shift': 'Open Shift, View Shift Transactions, Confirm Bank Transfer, Cancel Counter Order, Count Cash & Close Shift',
        'maint': 'Report Broken Equipment, View Own Requests, Close Repaired Request'})
# ---------------------------------------------------------------- Manager
MG = [('param', 'Configure System Parameters'), ('pkg', 'Manage Service Package Catalog'), ('hours', 'Configure Operating Hours'), ('vou', 'Manage Vouchers'),
      ('leave', 'Process Staff Leave Requests'), ('pers', 'Manage Personnel Records'), ('del', 'Process Customer Deletion Requests'), ('fac', 'Manage Facility/Equipment Catalog'),
      ('maint', 'Process Facility Maintenance Requests'), ('tx', 'Look Up System-Wide Transaction History'), ('fb', 'View Customer Feedback'), ('blog', 'Manage Blog Posts'),
      ('notif', 'Send Notifications'), ('dash', 'View Aggregated Dashboard')]
build('UC_Manager', [(u, l, i, 0) for i, (u, l) in enumerate(MG)],
 left=[('m', 'Manager', SX - 140, 6.5)], assoc=[('m', u) for u, _ in MG],
 notes={'pkg': 'View Package List / Detail, Create Package, Edit Package, Stop Selling / Sell Again',
        'vou': 'Create Voucher, View Voucher List / Detail, Edit Voucher, Deactivate Voucher',
        'leave': 'View Queue, View Detail, Approve, Bulk Approve, Approve Late, Reject With Reason',
        'pers': 'View Staff List / Detail, Edit Personnel Details, Record Resignation',
        'fac': 'Add / Edit Facility Group, Show / Hide Group, Add / Edit Equipment Item, Retire Item',
        'maint': 'View Queue, Approve (Set Under Maintenance), Reject With Reason',
        'tx': 'Search & Filter, Export To Excel, Confirm Refund',
        'blog': 'Create Post, View Post List / Detail, Edit Post, Publish / Unpublish',
        'dash': 'View Report For A Date Range, Export CSV'})
# ---------------------------------------------------------------- Admin
build('UC_Admin', [('acc', 'Manage System Accounts', 0, 0), ('audit', 'View Audit Log', 1, 0), ('rep', 'View Operational Reports & Audit Metrics', 2, 0)],
 row=90, left=[('a', 'Admin', SX - 140, 1)], assoc=[('a', 'acc'), ('a', 'audit'), ('a', 'rep')],
 notes={'acc': 'Create Staff Account (Admin, Manager, PT, Receptionist), View Account List / Detail, Change Position, Lock / Unlock Account, Unlock Locked Customer Account',
        'audit': 'Search & Filter Sensitive Actions, View Action Detail',
        'rep': 'View Report For A Date Range, View Audit Metrics, Export CSV'})
print('ok')
