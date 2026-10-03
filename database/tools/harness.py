"""Nạp T-SQL (schema + seed) vào SQLite để kiểm FK / CHECK / UNIQUE / filtered index.
LIKE được thay bằng ngữ nghĩa T-SQL ([0-9], [^A-Z0-9], không phân biệt hoa thường)."""
import re, sqlite3, sqlglot, functools
from sqlglot import exp

SKIP = re.compile(r"^\s*(IF DB_ID|CREATE DATABASE|USE |PRINT |SET NOCOUNT|SET ANSI_NULLS|SET QUOTED_IDENTIFIER|BEGIN TRANSACTION|COMMIT TRANSACTION|SET IDENTITY_INSERT|SET XACT_ABORT|DECLARE |SET @)", re.I)

@functools.lru_cache(maxsize=4096)
def _like_rx(pat, ci=True):
    out, i = '', 0
    while i < len(pat):
        c = pat[i]
        if c == '%': out += '.*'
        elif c == '_': out += '.'
        elif c == '[':
            j = pat.index(']', i)
            body = pat[i+1:j]
            out += '[' + ('^' + re.escape(body[1:]).replace('\\-', '-') if body.startswith('^') else re.escape(body).replace('\\-', '-')) + ']'
            i = j
        else: out += re.escape(c)
        i += 1
    return re.compile('^' + out + '$', re.S | (re.I if ci else 0))

def tsql_like(pat, val, *esc):
    if pat is None or val is None: return None
    return 1 if _like_rx(pat).match(str(val)) else 0

def tsql_len(v):
    return None if v is None else len(str(v).rstrip(' '))

def datepart_(unit, v):
    if v is None: return None
    v = str(v)
    t = v.split(' ')[-1] if (' ' in v or ':' in v) else None
    if unit in ('MINUTE','SECOND','HOUR'):
        hh, mm, *ss = t.split(':')
        return {'HOUR': int(hh), 'MINUTE': int(mm), 'SECOND': int(float(ss[0])) if ss else 0}[unit]
    y, m, d = v[:10].split('-')
    return {'YEAR': int(y), 'MONTH': int(m), 'DAY': int(d)}[unit]

def connect():
    con = sqlite3.connect(':memory:')
    con.create_function('like', 2, tsql_like)
    con.create_function('like', 3, tsql_like)
    con.create_function('LEN', 1, tsql_len)
    con.create_function('DATEPART_', 2, datepart_)
    con.execute('PRAGMA foreign_keys = ON')
    return con

def batches(sql):
    for b in re.split(r'^\s*GO\s*$', sql, flags=re.M):
        lines = [l for l in b.splitlines() if not SKIP.match(l)]
        b = '\n'.join(lines).strip()
        if b and not re.fullmatch(r'(\s*(--[^\n]*|/\*.*?\*/)\s*)*', b, re.S):
            yield b

def to_sqlite(stmt):
    out = []
    for e in sqlglot.parse(stmt, read='tsql'):
        if e is None: continue
        if isinstance(e, exp.Create):
            # IDENTITY → INTEGER PRIMARY KEY không cần; bỏ thuộc tính identity
            for c in e.find_all(exp.GeneratedAsIdentityColumnConstraint):
                c.parent.pop()
        def fix(n):
            if isinstance(n, exp.National):
                return exp.Literal.string(n.this)
            if isinstance(n, exp.Extract):
                return exp.Anonymous(this='DATEPART_', expressions=[exp.Literal.string(n.this.name.upper()), n.expression])
            return n
        e = e.transform(fix)
        out.append(re.sub(r'\(MAX\)', '', e.sql(dialect='sqlite')))
    return out

def run(con, sql, label=''):
    for b in batches(sql):
        for s in to_sqlite(b):
            try:
                con.execute(s)
            except Exception as ex:
                raise RuntimeError(f'{label}: {ex}\n--- T-SQL ---\n{b[:1500]}\n--- SQLite ---\n{s[:1500]}') from None
