"""Quality gates Q1–Q8 for the SRS update. Exit code 1 if any gate fails."""
import json, re, sys
d = json.load(open('docs/srs-update/build/data/data.json'))
fails = []
def fail(g, msg): fails.append(f'{g}: {msg}')
UCRX = re.compile(r'UC-(?:\d+|PAY|Claim|IDENT)')
ucs_all = {r[0] for r in d['UC'][1:]}
withdrawn_uc = {r[0] for r in d['UC'][1:] if r[3].startswith('Withdrawn')}
# ---------- Q1 UC count per rule ----------
for r in d['CBR'][1:]:
    n = set(UCRX.findall(r[2]))
    if len(n) < 2: fail('Q1', f'{r[0]} has {len(n)} use case(s): {r[2]}')
for r in d['BR'][1:]:
    n = set(UCRX.findall(r[2]))
    if len(n) != 1: fail('Q1', f'{r[0]} must have exactly 1 use case: {r[2]}')
# ---------- collect SRS-facing texts ----------
SRS_TABLES = ['GBR','CBR','BR','DBR','PARAMS','DD','NFR','UC','ACT','SCR','API','JOB','BF','DEF','LOG','UCX']
texts = []
for k in SRS_TABLES:
    for r in d[k][1:]: texts.append((k, r[0], ' | '.join(str(c) for c in r)))
for e, st, rows in d['ENT']: texts.append(('ENT', e, ' | '.join(v for _, v in rows)))
for r in d['MSG'][1:]: texts.append(('MSG', r[1], r[3] + ' | ' + r[4]))
# ---------- Q2 no version / status noise ----------
NOISE = re.compile(r'\bv2\d\b|[Nn]ot (?:fully )?implemented|[Ii]mplemented|Code to fix|[Dd]ecision \d\d/\d\d|no (?:customer )?screen yet|skeleton|\bTODO\b|no longer (?:swap|cancel|mark)|when implemented')
for k, rid, t in texts:
    m = NOISE.search(t)
    if m: fail('Q2', f'{k} {rid}: "{m.group(0)}"')
# ---------- Q3 references exist / not to withdrawn ----------
ids = {}
for k in ['GBR','CBR','BR','DBR','PARAMS','NFR','UC']:
    for r in d[k][1:]: ids[r[0]] = r
for k in ['JOB','API']:
    for r in d[k][1:]: ids[r[1].split(':')[0]] = r
msgs = {r[1] for r in d['MSG'][1:]}
withdrawn = {i for i, r in ids.items() if any(isinstance(c, str) and c.startswith('Withdrawn') for c in r)}
REF = re.compile(r'\b(GBR-\d+|CBR-\d+|DBR-\d+|BR-\d+|P-\d+|UC-\d+|NFR-\d+|JOB-\d+|API-\d+|MSG\d+)\b')
for k, rid, t in texts:
    if k in ('LOG',): continue
    is_w = 'Withdrawn' in t or 'withdrawn' in t
    for ref in set(REF.findall(t)):
        if ref.startswith('MSG'):
            if ref not in msgs: fail('Q3', f'{k} {rid} → missing {ref}')
            continue
        if ref not in ids: fail('Q3', f'{k} {rid} → missing {ref}')
        elif ref in withdrawn and ref != rid and not is_w and k not in ('UCX', 'BF'):
            fail('Q3', f'{k} {rid} → withdrawn {ref}')
# ---------- Q4 no rule codes in message content ----------
for r in d['MSG'][1:]:
    if re.search(r'\b(?:G|C|D)?BR-\d', r[4]): fail('Q4', f'{r[1]} content has a rule code')
# ---------- Q5 UC references valid ----------
for k in ['GBR','CBR','BR','DBR']:
    col = {'GBR': 2, 'CBR': 2, 'BR': 2, 'DBR': 3}[k]
    for r in d[k][1:]:
        for u in UCRX.findall(r[col]):
            if u not in ucs_all: fail('Q5', f'{r[0]} → unknown {u}')
# ---------- Q6 every active UC is covered by a rule ----------
covered = set()
for k, col in [('GBR', 2), ('CBR', 2), ('BR', 2), ('DBR', 3)]:
    for r in d[k][1:]: covered |= set(UCRX.findall(r[col]))
for u in sorted(ucs_all - withdrawn_uc - covered): fail('Q6', f'{u} has no rule (GBR/CBR/BR/DBR) pointing to it')
# ---------- Q7 numeric / Q8 terminology consistency ----------
STALE = [
 (r'\b2 MB\b', 'avatar/image limit is 5 MB'),
 (r'(?i)(fault|maintenance) description[^.|]*10–1000', 'maintenance description is 10–500'),
 (r'\b6–64\b', 'password is 8–64'),
 (r'(?i)\bhold[^.|]{0,40}\b(10|20|30) minutes\b', 'PT-sale hold is 15 minutes'),
 (r'(?i)VNPay[^.|]{0,40}\b(10|15|20) minutes', 'VNPay link is 5 minutes'),
 (r'\b12:00\b|\b14:00\b', 'shift boundary is 13:00'),
 (r'(?i)session[^.|]{0,30}\bCompleted\b|\bCompleted\b[^.|]{0,20}session', 'session state is CheckedIn'),
 (r'MarkedAbsent', 'MissedReason has no MarkedAbsent'),
 (r'(?i)attendance window|24 hours after', 'no attendance window (P-07 withdrawn)'),
 (r'Mark Session No-Show|\bSwap Session\b(?!s)', 'withdrawn functions'),
 (r'(?i)automatically rejected|rejected automatically|auto-reject', 'no automatic leave rejection'),
 (r'(?i)quantity is at least 1|facility quantity', 'facility quantity is computed'),
]
for k, rid, t in texts:
    if k in ('UCX', 'BF') or t.startswith('Withdrawn') or 'Withdrawn —' in t: continue
    for rx, why in STALE:
        m = re.search(rx, t)
        if m: fail('Q7', f'{k} {rid}: "{m.group(0)}" — {why}')
# ---------- report ----------
gates = ['Q1','Q2','Q3','Q4','Q5','Q6','Q7']
for g in gates:
    n = sum(1 for f in fails if f.startswith(g + ':'))
    print(f'{g}: {"PASS" if n == 0 else "FAIL (" + str(n) + ")"}')
for f in fails: print('  ', f)
print('withdrawn IDs:', len(withdrawn), '| UCs:', len(ucs_all), '| CBR:', len(d['CBR']) - 1, '| BR:', len(d['BR']) - 1)
sys.exit(1 if fails else 0)
