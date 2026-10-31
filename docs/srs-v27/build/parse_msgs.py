import re, json, sys, subprocess, collections
src=open('src/main/java/com/gms/constant/Messages.java',encoding='utf-8').read()
lines=src.split('\n')
msgs=[]; section=''; jd=[]; en=None; injd=False; buf=[]
for i,l in enumerate(lines):
    s=l.strip()
    m=re.match(r'// =====\s*(.*?)\s*=====',s)
    if m: section=m.group(1); jd=[]; en=None; continue
    if s.startswith('/**'):
        injd=True; buf=[s[3:]]
        if s.endswith('*/'): injd=False; jd=[' '.join(buf).replace('*/','').strip()]
        continue
    if injd:
        buf.append(s.lstrip('*').strip())
        if s.endswith('*/'): injd=False; jd=[' '.join(buf).replace('*/','').strip()]
        continue
    m=re.match(r'// EN:\s*(.*)',s)
    if m: en=m.group(1); continue
    m=re.match(r'public static final String (MSG(\d+)\w*)\s*=\s*(.*)',s)
    if m:
        name,num,rest=m.group(1),int(m.group(2)),m.group(3)
        j=i
        while not rest.rstrip().endswith(';'):
            j+=1; rest+=' '+lines[j].strip()
        val=''.join(re.findall(r'"((?:[^"\\]|\\.)*)"',rest))
        msgs.append(dict(num=num,name=name,section=section,doc=(jd[0] if jd else ''),en=en or '',vi=val.replace('\\"','"').replace('\\n',' ')))
        jd=[]; en=None
json.dump(msgs,open('docs/srs-v27/build/data/msgs.json','w'),ensure_ascii=False,indent=0)
print(len(msgs), len({m['num'] for m in msgs}))
dups=[n for n,c in collections.Counter(m['num'] for m in msgs).items() if c>1]
print('dups',dups)
bad=[m['name'] for m in msgs if re.search(r'\b(G|C|D)?BR-\d',m['vi'])]
print('rule codes in content',bad)
