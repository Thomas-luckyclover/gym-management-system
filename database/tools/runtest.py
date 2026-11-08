import sys, importlib, datetime as dt
import harness, seedgen as G, seeddata  # noqa
schema = open('../1_Init_Schema.sql').read()
nows = [dt.datetime(2026,10,3,10,0), dt.datetime(2026,10,4,6,0), dt.datetime(2026,10,5,14,30), dt.datetime(2026,10,6,21,50),
        dt.datetime(2026,10,9,23,59), dt.datetime(2026,10,13,0,5), dt.datetime(2026,10,19,12,0), dt.datetime(2026,11,20,9,0), dt.datetime(2027,1,1,8,0)]
for n in nows:
    con = harness.connect()
    harness.run(con, schema, 'schema')
    sql = G.render(now=n)
    harness.run(con, sql, f'seed@{n}')
    bad = con.execute('PRAGMA foreign_key_check').fetchall()
    assert not bad, bad[:5]
    print('OK', n, con.execute('select count(*) from Sessions').fetchone()[0])
