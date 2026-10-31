// Clean SRS-only document: same headings and columns as the SRS, ready to paste. No tracking, no evidence.
const fs = require('fs');
const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, ShadingType, BorderStyle, HeadingLevel, TableLayoutType } = require('docx');
const d = JSON.parse(fs.readFileSync('docs/srs-update/build/data/data.json', 'utf8'));
const sec = JSON.parse(fs.readFileSync('docs/srs-update/build/data/dbr_sec.json', 'utf8'));
const RED = 'A52A2A', HEAD = 'E6B8AF', FONT = 'Calibri', SZ = 22, TW = 9638;
const b = { style: BorderStyle.SINGLE, size: 4, color: '000000' }, borders = { top: b, bottom: b, left: b, right: b };
const P = (t, o = {}) => new Paragraph({ spacing: { after: o.after ?? 0, line: 264 }, children: [new TextRun({ text: String(t ?? ''), bold: !!o.bold, font: FONT, size: SZ })] });
const W = r => { const s = r.reduce((a, x) => a + x, 0); const w = r.map(x => Math.floor(TW * x / s)); w[w.length - 1] += TW - w.reduce((a, x) => a + x, 0); return w; };
const T = (rows, r) => { const w = W(r); return new Table({ width: { size: TW, type: WidthType.DXA }, columnWidths: w, layout: TableLayoutType.FIXED,
  rows: rows.map((row, i) => new TableRow({ tableHeader: i === 0, children: row.map((c, j) => new TableCell({ borders, width: { size: w[j], type: WidthType.DXA },
    margins: { top: 40, bottom: 40, left: 100, right: 100 }, shading: i === 0 ? { fill: HEAD, type: ShadingType.CLEAR, color: 'auto' } : undefined,
    children: [P(c, { bold: i === 0 })] })) })) }); };
const H = (t, lvl, size) => new Paragraph({ heading: lvl, spacing: { before: 240, after: 120 }, children: [new TextRun({ text: t, bold: true, color: RED, font: FONT, size })] });
const H1 = t => H(t, HeadingLevel.HEADING_1, 32), H2 = t => H(t, HeadingLevel.HEADING_2, 28), H3 = t => H(t, HeadingLevel.HEADING_3, 24);
const gap = () => P('', { after: 120 });
const C = [];
C.push(H1('Change Log')); C.push(T(d.LOG, [13, 9, 18, 60])); C.push(P('*A - Added M - Modified D - Deleted', { after: 120 }));
C.push(H1('Definition and Acronyms')); C.push(T(d.DEF, [6, 22, 72])); C.push(gap());
C.push(H1('I. Overall Requirements'));
C.push(H2('2. Main Business Flow'));
for (const r of d.BF.slice(1)) { C.push(new Paragraph({ spacing: { before: 120, after: 40 }, children: [new TextRun({ text: r[0] + ' — ' + r[1] + ':', bold: true, font: FONT, size: SZ })] })); C.push(P(r[3], { after: 80 })); }
C.push(H2('3.2 Entity Descriptions'));
for (const [name, , rows] of d.ENT) { C.push(new Paragraph({ spacing: { before: 160, after: 60 }, children: [new TextRun({ text: 'Entity: ' + name, bold: true, font: FONT, size: SZ })] })); C.push(T([['Field', 'Value']].concat(rows), [22, 78])); }
C.push(H2('3.3 Data Business Rules'));
const groups = {}; const extra = { 'DBR-77': '3.3.4' };
const names = { '3.3.1': 'Value format and ranges', '3.3.2': 'Uniqueness & mandatory data', '3.3.3': 'States and Transitions', '3.3.4': 'Derived and Copied Values', '3.3.5': 'Content Records', '3.3.6': 'Identity And Account' };
for (const r of d.DBR.slice(1)) { const s = (sec[r[0]] && sec[r[0]][0]) || extra[r[0]] || '3.3.5'; (groups[s] = groups[s] || []).push(r); }
for (const s of Object.keys(names)) if (groups[s]) { C.push(H3(s + '  ' + names[s])); C.push(T([d.DBR[0]].concat(groups[s]), [9, 16, 55, 20])); C.push(gap()); }
C.push(H2('4.1 Actors')); C.push(T(d.ACT, [5, 20, 75])); C.push(gap());
C.push(H2('4.2 Use Cases (UC)')); C.push(T(d.UC, [10, 21, 15, 54])); C.push(gap());
C.push(H2('5.1 Screen Inventory — b. Screen List')); C.push(T(d.SCR, [5, 23, 19, 53])); C.push(gap());
C.push(H2('5.2 External API Inventory')); C.push(T(d.API, [5, 25, 14, 56])); C.push(gap());
C.push(H2('5.3 Background Job Inventory')); C.push(T(d.JOB, [5, 25, 13, 57])); C.push(gap());
C.push(H1('II. Functional Requirements')); C.push(H2('13. Background Job Inventory'));
for (const j of d.JOBSPEC) {
  C.push(H3(j.title.split(' — ')[0]));
  if (j.paras) { j.paras.forEach(t => C.push(P(t, { after: 80 }))); continue; }
  for (const [k, v] of [['Trigger', j.trigger], ['Purpose', j.purpose], ['Related', j.related], ['Parameters', j.params]])
    C.push(new Paragraph({ spacing: { after: 40 }, children: [new TextRun({ text: k + ': ', bold: true, font: FONT, size: SZ }), new TextRun({ text: v, font: FONT, size: SZ })] }));
  C.push(P('Processing steps:', { after: 40 })); let n = 0;
  j.steps.forEach(t => { const sub = t.startsWith('    '); if (!sub) n++; C.push(P(sub ? '      ' + t.trim() : n + '. ' + t, { after: 40 })); });
  C.push(P('Failure behavior:', { after: 40 })); C.push(T([['Scenario', 'System behavior']].concat(j.fail), [30, 70])); C.push(gap());
}
C.push(H1('III. Requirement Appendix'));
C.push(H2('1. General Business Rules')); C.push(T(d.GBR, [9, 37, 15, 21, 18])); C.push(gap());
C.push(H2('2. Common Business Rules')); C.push(T(d.CBR, [10, 71, 19])); C.push(gap());
C.push(H2('3. Business Rules')); C.push(T(d.BR, [10, 71, 19])); C.push(gap());
C.push(H2('4. System Parameters')); C.push(T(d.PARAMS, [7, 36, 14, 17, 11, 15])); C.push(gap());
C.push(H2('5. Data Dictionary')); C.push(T(d.DD, [33, 67])); C.push(gap());
C.push(H2('6. System Messages')); C.push(T(d.MSG, [5, 12, 13, 30, 40])); C.push(gap());
C.push(H2('7. Other Requirements')); C.push(T(d.NFR, [9, 18, 73]));
const doc = new Document({ styles: { default: { document: { run: { font: FONT, size: SZ } } } },
  sections: [{ properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1134, bottom: 1134, left: 1134, right: 1134 } } }, children: C }] });
Packer.toBuffer(doc).then(buf => { fs.writeFileSync(process.argv[2], buf); console.log('ok', buf.length); });
