import json, re, os, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
C = os.path.join(ROOT, 'docs/srs-v27/content')
G = {}
for f in sorted(os.listdir(C)):
    if f.endswith('.py'):
        exec(open(os.path.join(C, f), encoding='utf-8').read(), G)
SRS = open(os.path.join(ROOT, 'docs/SRS - G5.docx.md'), encoding='utf-8').read().split('\n')

def clean(x):
    x = re.sub(r'\\([=+\-.*>_#<\[\]()])', r'\1', x)
    return re.sub(r'\*\*', '', x).strip()
def old_table(prefix, start, end):
    d = {}
    for l in SRS[start:end]:
        m = re.match(r'\|\s*(%s-[0-9A-Za-z]+)\s*\|(.*)\|\s*$' % prefix, l)
        if m: d[m.group(1)] = [clean(c) for c in m.group(2).split('|')]
    return d
OLD = {
 'GBR': old_table('GBR', 4115, 4161), 'CBR': old_table('CBR', 4161, 4281), 'BR': old_table('BR', 4281, 4368),
 'P': old_table('P', 4368, 4396), 'NFR': old_table('NFR', 4554, 4811), 'DBR': old_table('DBR', 921, 1046),
 'UC': {r[0]: r[1:] for r in json.load(open('docs/srs-v27/build/data/uc_old.json'))['uc']},
}
# --- messages ---
msgs = json.load(open('docs/srs-v27/build/data/msgs.json')); srsm = json.load(open('docs/srs-v27/build/data/srs_msgs.json'))
TYPE = {'S':'Toast (success)','E':'Toast (error)','F':'In red, under the field','L':'In line','N':'Notification','W':'Toast (warning)'}
MSG_ROWS = []; seen = {}
for m in msgs:
    seen.setdefault(m['num'], []).append(m)
n = 0
for num in range(1, 323):
    if num not in seen:
        n += 1
        why = {38: 'Withdrawn in v27 — counter accounts get a temporary password (CBR-31); the "no password yet" message is removed.',
               80: 'Withdrawn in v27 — leave requests are no longer rejected automatically (CBR-85, P-20 withdrawn).',
               237: 'Number not used.'}[num]
        MSG_ROWS.append([str(n), 'MSG%02d' % num if num < 100 else 'MSG%d' % num, '—', why, '—']); continue
    group = seen[num]
    n += 1
    code = 'MSG%02d' % num if num < 100 else 'MSG%d' % num
    if num <= 105 and str(num) in srsm:
        t = srsm[str(num)]['type']; ctx = srsm[str(num)]['ctx']
        ctx = re.sub(r'\\', '', ctx)
    else:
        t, ctx = G['C'][num]; t = TYPE[t]
    content = ' / '.join(g['vi'] for g in group)
    if len(group) > 1: ctx = ctx + ' (two texts: change and removal)'
    MSG_ROWS.append([str(n), code, t, ctx, content])
# context fixes for withdrawn rule references in kept SRS contexts
FIXCTX = {'MSG78':'PT submits leave while still having Scheduled sessions in that shift (CBR-83)',
          'MSG90':'Kept for historical swap data — no screen uses it in v27 (UC-38 withdrawn)',
          'MSG58':'Avatar changed or removed (UC-04)',
          'MSG91':'PT handles a session that has already started or is no longer Scheduled (BR-70)',
          'MSG105':'Action outside its allowed time window, e.g. handling a session after it started (BR-69, BR-70)'}
for r in MSG_ROWS:
    if r[1] in FIXCTX: r[3] = FIXCTX[r[1]]
    if 'Completed' in r[3]: r[3] = r[3].replace('Completed', 'CheckedIn')
# --- tracking table ---
UCFILES = {
 'UC-01':'LoginServlet, AuthService, SelectRoleServlet','UC-02':'LogoutServlet','UC-03':'ForgotPasswordServlet, PasswordRecoveryService, VerificationCodeService',
 'UC-04':'AccountSettingsServlet, AccountSettingsService, ContactChangeService','UC-05':'RegisterServlet, RegistrationService','UC-Claim':'RegistrationService','UC-IDENT':'RegistrationService',
 'UC-06':'HomeService, BlogService, AboutServlet','UC-10':'PackageService, Membership/PtPackage*Servlet','UC-11':'SystemConfigService, PtScheduleService',
 'UC-14':'LeaveRequestFormServlet, StaffLeaveService','UC-15':'LeaveRequestService, LeaveApprovalService, LeaveRequestReminderJob','UC-16':'CounterSaleService, CounterAccountService (group A: CustomerProfile*Servlet)',
 'UC-17':'MemberDeletionReviewServlet (group A)','UC-18':'FreezeService (group A)','UC-19':'CheckInService, CheckInDAO','UC-20':'CounterSaleService, CounterAccountService',
 'UC-21':'PtSaleService, PtSaleDAO','UC-22':'PackageSelectionService, PaymentService, SubscriptionStartDateService','UC-23':'PaymentService, CounterSaleService',
 'UC-PAY':'PaymentCallbackService, VnPayIpnServlet','UC-24':'TransactionHistoryService','UC-25':'ShiftService (group A)','UC-26':'ManagerTransactionService, SystemTransactionServlet',
 'UC-27':'FacilityService, FacilityManagementServlet','UC-28':'MaintenanceRequestService, MaintenanceReportServlet','UC-29':'MaintenanceRequestService','UC-30':'PtScheduleService, PtScheduleServlet',
 'UC-31':'AutoCloseSessionService (JOB-04)','UC-32':'PtDashboardService','UC-35':'PtSessionHandleService','UC-36':'MyBookingService, MyPtSessionService','UC-41':'CustomerDashboardService',
 'UC-42':'FeedbackService, FeedbackDAO','UC-46':'ReportService','UC-47':'SystemReportService','UC-48':'ExerciseLibraryService, WgerClient','UC-49':'BodyStatsService','UC-50':'NotificationService, NotificationApiServlet',
}
def evidence(uc_text, kind):
    if kind in ('DBR', 'DD'): base = ['database/1_Init_Schema.sql']
    elif kind == 'P': base = ['database/2_Seed_Data.sql (SystemConfigs)', 'SystemConfigService']
    else: base = []
    for u in re.findall(r'UC-(?:\d+|PAY|Claim|IDENT)', uc_text or ''):
        if u in UCFILES and UCFILES[u] not in base: base.append(UCFILES[u])
        if len(base) >= 3: break
    return '; '.join(base) or 'docs/notes.md'
def status(new_text, old_text):
    if old_text is None: return 'Mới'
    if new_text.startswith('Withdrawn in v27'): return 'Withdrawn'
    if 'Not implemented' in new_text or 'Not fully implemented' in new_text or 'no screen yet' in new_text:
        return 'Chưa triển khai'
    norm = lambda s: re.sub(r'\W+', '', s or '').lower()
    return 'Giữ' if norm(new_text) == norm(old_text) else 'Sửa theo code'
TRACK = []
def track(kind, rows, idx_text, idx_uc, oldkey, old_idx):
    for r in rows:
        rid = r[0]; new = r[idx_text]; o = OLD[oldkey].get(rid)
        old = o[old_idx] if o else None
        st = status(new, old)
        if st == 'Giữ': continue
        TRACK.append([rid, st, old or '—', new, evidence(r[idx_uc] if idx_uc is not None else '', kind)])
track('GBR', G['GBR'], 1, 2, 'GBR', 0)
track('CBR', G['CBR'], 1, 2, 'CBR', 0)
track('BR', G['BR'], 1, 2, 'BR', 0)
track('DBR', G['DBR'], 2, 3, 'DBR', 1)
track('P', [[p[0], p[1] + ' — default ' + p[3]] + p[1:] for p in G['PARAMS']], 1, None, 'P', None) if False else None
for p in G['PARAMS']:
    o = OLD['P'].get(p[0]); old = (o[0] + ' — default ' + o[2]) if o else None; new = p[1] + ' — default ' + p[3]
    st = status(p[1], o[0] if o else None)
    if o and st == 'Sửa theo code' and re.sub(r'\W','',o[2]) == re.sub(r'\W','',p[3]) and re.sub(r'\W','',o[0]).lower() == re.sub(r'\W','',p[1]).lower(): continue
    if st != 'Giữ': TRACK.append([p[0], st, old or '—', new, evidence('', 'P')])
for r in G['NFR']:
    o = OLD['NFR'].get(r[0]); st = status(r[2], o[1] if o else None)
    if st != 'Giữ': TRACK.append([r[0], st, o[1] if o else '—', r[2], 'pom.xml; CsrfUtil / filters; common.css, common.js'])
for r in G['UC']:
    o = OLD['UC'].get(r[0]); st = status(r[3], o[2] if o else None)
    if st != 'Giữ': TRACK.append([r[0], st, o[2] if o else '—', r[3], evidence(r[0], 'UC')])
for r in MSG_ROWS:
    if r[2] == '—': TRACK.append([r[1], 'Withdrawn', 'See SRS v26', r[3], 'constant/Messages.java'])
TRACK.append(['MSG106–MSG322', 'Mới', '—', '217 messages added to section 6 from Messages.java', 'constant/Messages.java'])
TRACK.append(['Data Dictionary', 'Sửa theo code', 'Sessions.Status Completed; Subscriptions.Status Completed; MissedReason MarkedAbsent; Facilities.Status', 'Values aligned with every CHECK constraint; new attributes EquipmentItems, IncidentType, Exercises, NotificationRecipients.ReadAt, Feedbacks.Rating', 'database/1_Init_Schema.sql'])
TRACK.append(['3.2 Entities', 'Sửa theo code', '30 entities', '34 entities: 13 modified, 4 new (EquipmentItem, BodyMeasurement, Exercise, LeaveRequestTransfer)', 'database/1_Init_Schema.sql'])
ORDER = {'Withdrawn': 1, 'Sửa theo code': 0, 'Mới': 2, 'Chưa triển khai': 3}
out = dict(G=None)
data = {
 'GBR': [G['GBR_COLS']] + G['GBR'], 'CBR': [G['CBR_COLS']] + G['CBR'], 'BR': [G['BR_COLS']] + G['BR'],
 'DBR': [G['DBR_COLS']] + G['DBR'], 'PARAMS': [G['PARAMS_COLS']] + G['PARAMS'], 'DD': [G['DD_COLS']] + G['DD'],
 'MSG': [["#", "Message code", "Message Type", "Context", "Content"]] + MSG_ROWS, 'NFR': [G['NFR_COLS']] + G['NFR'],
 'UC': [G['UC_COLS']] + G['UC'], 'ACT': [G['ACT_COLS']] + G['ACT'], 'SCR': [G['SCR_COLS']] + G['SCR'],
 'API': [G['API_COLS']] + G['API'], 'JOB': [G['JOB_COLS']] + G['JOB'], 'BF': [G['BF_COLS']] + G['BF'],
 'UCX': [G['UCX_COLS']] + G['UCX'], 'FIX': [G['FIX_COLS']] + G['FIX'],
 'TRACK': [["Rule/UC ID", "Trạng thái", "Cũ", "Mới", "Bằng chứng (file)"]] + TRACK,
 'ENT': [[e, st, rows] for e, st, rows in G['ENTITIES']],
}
json.dump(data, open('docs/srs-v27/build/data/data.json', 'w'), ensure_ascii=False)
from collections import Counter
print({k: len(v) - 1 for k, v in data.items() if k != 'ENT'}, 'entities', len(data['ENT']))
print(Counter(r[1] for r in TRACK))
# --- Definitions & change log ---
DEF = []
for l in SRS[268:302]:
    m = re.match(r'\|\s*(\d+)\s*\|(.*)\|(.*)\|\s*$', l)
    if m: DEF.append([m.group(1), clean(m.group(2)), clean(m.group(3))])
for r in DEF:
    if r[1] == 'MSG': r[2] = 'System message code (MSG01–MSG322, section System Messages)'
    if r[1] == 'P-xx': r[2] = 'System parameter configured by the Manager or at deployment (P-01–P-23; P-07 and P-20 withdrawn)'
    if r[1] == 'JOB-xx': r[2] = 'Background job that runs on a schedule without user action (JOB-01–JOB-08)'
    if r[1] == 'SMTP': r[2] = 'Simple Mail Transfer Protocol — used to send verification codes, temporary passwords and alerts by email'
    if r[1] == 'Slot': r[2] = 'A time on a PT\'s calendar that can hold one session (P-06 minutes, default 90)'
    if r[1] == 'Shift': r[2] = 'A half of the opening day split at 13:00 (morning / afternoon); also a Receptionist work shift in which counter payments are recorded'
    if r[1] == 'PT package': r[2] = 'A package of PT sessions sold by term (1–12 months), single or couple, scheduled from a weekly Monday–Friday template'
extra = [
 ['Temporary password','A random 10-character password emailed to a customer whose account the Receptionist created at the counter (CBR-31)'],
 ['Hold','A 15-minute reservation of PT slots made on the PT sale screen before payment (BR-96); an online order also holds a slot until its payment deadline'],
 ['Draft slot / Publish','A slot the PT has prepared on the weekly schedule but not yet saved; Publish saves all drafts and removals at once (BR-97)'],
 ['Can use the gym','Holding a Membership or DayPass that covers the date and not being frozen on it (CBR-117)'],
 ['Exercise Library','Catalogue of exercises from wger, a benefit of qualifying packages (UC-48)'],
 ['wger','Public exercise database (wger.de) used as API-05'],
 ['BMI','Body Mass Index = weight (kg) ÷ height² (m), grouped by the Asian scale (BR-91)'],
 ['Hint box','The (i) button next to a title that shows the screen\'s explanations (GBR-36)'],
 ['CSRF','Cross-Site Request Forgery — every signed-in POST carries a token checked by the server (NFR-05)'],
 ['Zalo','Vietnamese messaging app; customers and PTs contact each other through zalo.me links'],
]
for i, (a, b) in enumerate(extra, start=len(DEF) + 1):
    DEF.append([str(i), a, b])
LOG = [
 ['31/10/2026','M','Le Cong Minh','v27 — align the SRS with the code on develop (67f59e8): GBR, CBR, BR, DBR, Entities, System Parameters, Data Dictionary, System Messages (MSG01–MSG322), Other Requirements, Use Case list, Actors, Business Flows'],
 ['31/10/2026','A','Le Cong Minh','v27 — new UC-48 Exercise Library, UC-49 Body Measurements, UC-50 Notifications; JOB-08; API-05; GBR-36…39; CBR-116…118; BR-81…112; DBR-66…77; NFR-09, NFR-10; parameters P-22, P-23'],
 ['31/10/2026','D','Le Cong Minh','v27 — withdrawn: UC-38, UC-39, screens #53, #56; CBR-44, CBR-45, CBR-57, CBR-82; BR-54…56, BR-62, BR-73…79; P-07, P-20; MSG38, MSG80'],
]
def short(t, n=230):
    t = str(t)
    return t if len(t) <= n else t[:n].rsplit(' ', 1)[0] + ' … (full text in section 2)'
data['TRACK'] = [data['TRACK'][0]] + [[r[0], r[1], short(r[2]), short(r[3]), r[4]] for r in data['TRACK'][1:]]
data['DEF'] = [['#', 'Acronym', 'Definition']] + DEF
data['LOG'] = [['Date', 'A*M, D', 'In charge', 'Change Description']] + LOG
json.dump(data, open('docs/srs-v27/build/data/data.json', 'w'), ensure_ascii=False)
print('DEF', len(DEF))
