import json,re,os,collections
msgs=json.load(open('docs/srs-v27/build/data/msgs.json'))
files={}
for root,_,fs in os.walk('src/main/java'):
    for f in fs:
        if f.endswith('.java') and f!='Messages.java':
            p=os.path.join(root,f); files[p]=open(p,encoding='utf-8',errors='replace').read().split('\n')
for m in msgs:
    uses=[]
    for p,ls in files.items():
        for i,l in enumerate(ls):
            if m['name'] in l and not re.search(r'\b'+m['name']+r'\w',l):
                ctx=' '.join(ls[max(0,i-2):i+2])
                uses.append((p.split('/')[-1],ctx))
    m['files']=sorted({u[0] for u in uses})
    t=collections.Counter()
    for f,c in uses:
        if re.search(r'setFlashToast\([^,]*,\s*"success"',c) or 'ServiceResult.ok(' in c or '.ok(' in c: t['Toast (success)']+=1
        if re.search(r'setFlashToast\([^,]*,\s*"error"',c): t['Toast (error)']+=1
        if re.search(r'setFlashToast\([^,]*,\s*"warning"',c): t['Toast (warning)']+=1
        if re.search(r'fieldErrors|failFields|putError|addError|FieldError|errors\.put|fail\(\s*"\w+"\s*,',c): t['In red, under the field']+=1
        if re.search(r'ServiceResult\.fail\(|\.fail\(',c): t['fail']+=1
        if re.search(r'setAttribute\(\s*"(empty|notice|warning|info|banner)',c,re.I): t['In line']+=1
    m['typehint']=dict(t)
json.dump(msgs,open('docs/srs-v27/build/data/msgs.json','w'),ensure_ascii=False,indent=0)
unused=[m['name'] for m in msgs if not m['files']]
print('unused',len(unused)); print(unused[:60])
