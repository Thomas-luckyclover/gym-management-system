import json, re
d = json.load(open('docs/srs-v27/build/data/data.json'))
ids = {}
for k in ['GBR','CBR','BR','DBR','PARAMS','NFR','UC','JOB','API']:
    col = 0 if k not in ('JOB','API') else 1
    for r in d[k][1:]:
        rid = r[col].split(':')[0].strip()
        if rid in ids: print('DUP', rid)
        ids[rid] = r
msgs = {r[1] for r in d['MSG'][1:]}
withdrawn = {i for i, r in ids.items() if any(isinstance(c, str) and c.startswith('Withdrawn in v2') for c in r)}
pat = re.compile(r'\b(GBR-\d+|CBR-\d+|DBR-\d+|BR-\d+|P-\d+|UC-\d+|NFR-\d+|JOB-\d+|API-\d+|MSG\d+)\b')
problems = 0
def scan(name, rows):
    global problems
    for r in rows:
        txt = ' '.join(c for c in r if isinstance(c, str))
        own = r[0]
        for ref in set(pat.findall(txt)):
            if ref.startswith('MSG'):
                if ref not in msgs: print(name, own, 'missing', ref); problems += 1
                continue
            if ref not in ids and not ref.startswith(('UC-Claim',)):
                print(name, own, 'missing', ref); problems += 1
            elif ref in withdrawn and ref != own and 'ithdrawn' not in txt and 'historical' not in txt:
                print(name, own, '→ withdrawn', ref); problems += 1
scan('MSG', [[r[1], r[3]] for r in d['MSG'][1:]])
print('rule codes in MSG content', [r[1] for r in d['MSG'][1:] if re.search(r'\b(G|C|D)?BR-\d', r[4])])
for k in ['GBR','CBR','BR','DBR','PARAMS','NFR','UC','SCR','API','JOB','DD']:
    scan(k, d[k][1:])
for e, st, rows in d['ENT']:
    scan('ENT', [[e] + [v for _, v in rows]])
print('withdrawn', sorted(withdrawn, key=lambda x: (x.split('-')[0], int(re.sub(r'\D','',x) or 0))))
print('problems', problems)
