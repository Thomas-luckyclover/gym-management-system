const fs = require('fs');
const d = require('docx');
const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, WidthType, ShadingType,
        BorderStyle, AlignmentType, HeadingLevel, PageBreak, Footer, PageNumber, TableLayoutType } = d;
const data = JSON.parse(fs.readFileSync('docs/srs-update/build/data/data.json', 'utf8'));
const out = process.argv[2];
const RED = 'A52A2A', HEAD_FILL = 'E6B8AF', FONT = 'Calibri', SZ = 22; // 11pt
const TW = 9638; // A4 portrait, 2 cm margins
const border = { style: BorderStyle.SINGLE, size: 4, color: '000000' };
const borders = { top: border, bottom: border, left: border, right: border };
const STATUS_FILL = { 'Withdrawn': 'EDEDED', 'Mới': 'E2EFDA', 'Chưa triển khai': 'FFF2CC', 'Sửa theo code': 'FFFFFF', 'New': 'E2EFDA', 'Modified': 'FFF2CC' };

function widths(ratios) { const s = ratios.reduce((a, b) => a + b, 0); const w = ratios.map(r => Math.floor(TW * r / s)); w[w.length - 1] += TW - w.reduce((a, b) => a + b, 0); return w; }
function para(text, opts = {}) {
  return new Paragraph({ spacing: { before: 0, after: opts.after ?? 0, line: 264 }, alignment: opts.align,
    children: [new TextRun({ text: String(text ?? ''), bold: !!opts.bold, color: opts.color, font: FONT, size: opts.size ?? SZ, italics: !!opts.italics })] });
}
function table(rows, ratios, opts = {}) {
  const w = widths(ratios);
  const trs = rows.map((r, i) => new TableRow({ tableHeader: i === 0, cantSplit: false,
    children: r.map((c, j) => new TableCell({ borders, width: { size: w[j], type: WidthType.DXA },
      margins: { top: 40, bottom: 40, left: 100, right: 100 },
      shading: i === 0 ? { fill: HEAD_FILL, type: ShadingType.CLEAR, color: 'auto' }
             : (opts.statusCol !== undefined && STATUS_FILL[r[opts.statusCol]] ? { fill: STATUS_FILL[r[opts.statusCol]], type: ShadingType.CLEAR, color: 'auto' } : undefined),
      children: String(c ?? '').split('\n').map(t => para(t, { bold: i === 0 })) })) }));
  return new Table({ width: { size: TW, type: WidthType.DXA }, columnWidths: w, layout: TableLayoutType.FIXED, rows: trs });
}
const H1 = t => new Paragraph({ heading: HeadingLevel.HEADING_1, spacing: { before: 240, after: 120 }, children: [new TextRun({ text: t, bold: true, color: RED, font: FONT, size: 32 })] });
const H2 = t => new Paragraph({ heading: HeadingLevel.HEADING_2, spacing: { before: 200, after: 100 }, children: [new TextRun({ text: t, bold: true, color: RED, font: FONT, size: 28 })] });
const H3 = t => new Paragraph({ heading: HeadingLevel.HEADING_3, spacing: { before: 160, after: 80 }, children: [new TextRun({ text: t, bold: true, color: RED, font: FONT, size: 24 })] });
const note = t => para(t, { italics: true, after: 120 });
const gap = () => para('', { after: 80 });

const C = [];
C.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 120 }, children: [new TextRun({ text: 'SRS G5 — Gym Management System', bold: true, color: RED, font: FONT, size: 40 })] }));
C.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 240 }, children: [new TextRun({ text: 'Bản cập nhật SRS khớp code nhánh develop — 31/10/2026', font: FONT, size: 26 })] }));
C.push(para('Cách dùng: mỗi bảng ở Mục 2 là bảng FULL, giữ đúng cột của SRS — copy nguyên bảng thay bảng cũ. Không đánh lại số ID; mục bỏ hẳn ghi "Withdrawn — lý do" và vẫn giữ UC gốc để truy vết. Mục II (mô tả từng màn / prototype) giữ nguyên theo yêu cầu.', { after: 80 }));
C.push(para('Nguyên tắc: màn đã code xong → viết theo code (rule, giới hạn, trạng thái, câu thông báo). Màn đang lỗi / chưa xong (ca làm việc, bảo lưu, hồ sơ hội viên, bán buổi PT lẻ tại quầy, các màn chưa có Servlet) → giữ rule SRS. Logic đã có trong code nhưng chưa có màn (chấm điểm PT) viết theo code. Các bảng SRS chỉ mô tả yêu cầu, không ghi phiên bản hay tiến độ; tình trạng triển khai chỉ nằm ở cột Trạng thái của Mục 1 và ở Mục 4.', { after: 80 }));
C.push(para('Trạng thái ở bảng theo dõi: Mới (xanh) · Chưa triển khai (vàng) = SRS ghi đủ yêu cầu, code chưa làm / chưa hiển thị, làm sau — không phải bỏ · Withdrawn (xám) = nhóm đã chốt bỏ · Sửa theo code (trắng).', { after: 200 }));

C.push(H1('1. Bảng theo dõi thay đổi'));
C.push(note('Chỉ liệt kê ID có thay đổi so với SRS hiện tại. ID không có trong bảng = Giữ nguyên. Ô Cũ / Mới được rút gọn; toàn văn ở Mục 2.'));
C.push(table(data.TRACK, [10, 10, 29, 31, 20], { statusCol: 1 }));
C.push(new Paragraph({ children: [new PageBreak()] }));

C.push(H1('2. Các bảng viết lại FULL (copy nguyên bảng)'));
C.push(H2('Change Log — thêm các dòng sau'));
C.push(table(data.LOG, [13, 9, 18, 60])); C.push(para('*A - Added M - Modified D - Deleted', { after: 120 }));
C.push(H2('Definition and Acronyms'));
C.push(table(data.DEF, [6, 22, 72])); C.push(gap());
C.push(H2('I.2 Main Business Flow — sửa từng đoạn'));
C.push(note('Business Flow là đoạn văn + sơ đồ nên ghi theo từng đoạn cần thay. Sơ đồ BF-05, BF-06, BF-08 cần vẽ lại theo cột "Replace with".'));
C.push(table(data.BF, [9, 13, 30, 48])); C.push(gap());
C.push(H2('I.3.2 Entity Descriptions'));
C.push(note('34 thực thể. Nhãn (Modified) / (New) / (Unchanged) màu xám chỉ để đánh dấu, không dán vào SRS.'));
for (const [name, st, rows] of data.ENT) {
  C.push(new Paragraph({ spacing: { before: 160, after: 60 }, children: [
    new TextRun({ text: 'Entity: ' + name, bold: true, font: FONT, size: SZ }),
    new TextRun({ text: '  (' + st + ')', italics: true, color: st === 'Unchanged' ? '808080' : RED, font: FONT, size: 18 })] }));
  C.push(table([['Field', 'Value']].concat(rows), [22, 78]));
}
C.push(H2('I.3.3 Data Business Rules'));
C.push(note('Một bảng gộp DBR-01 → DBR-77 (cùng cột với SRS). Khi dán: DBR-01…19 → 3.3.1, DBR-20…33 → 3.3.2, DBR-34…46 → 3.3.3, DBR-47…60 → 3.3.4, DBR-61…63, DBR-66…77 → 3.3.5, DBR-64…65 → 3.3.6.'));
C.push(table(data.DBR, [9, 16, 55, 20])); C.push(gap());
C.push(H2('I.4.1 Actors'));
C.push(table(data.ACT, [5, 20, 75])); C.push(gap());
C.push(H2('I.4.2 Use Cases (UC)'));
C.push(table(data.UC, [10, 21, 15, 54])); C.push(gap());
C.push(H2('I.5 Screen List / External API / Background Job (tham khảo)'));
C.push(note('Phần System Functionalities được yêu cầu giữ lại; 3 bảng dưới chỉ để đối chiếu, dán nếu nhóm muốn cập nhật danh sách.'));
C.push(H3('5.1.b Screen List')); C.push(table(data.SCR, [5, 23, 19, 53]));
C.push(H3('5.2 External API Inventory')); C.push(table(data.API, [5, 25, 14, 56]));
C.push(H3('5.3 Background Job Inventory')); C.push(table(data.JOB, [5, 25, 13, 57]));
C.push(H2('II.13 Background Job Inventory — các phần viết lại'));
C.push(note('Mục II.13 là đặc tả (không phải prototype) và đang mâu thuẫn với CBR-42 / CBR-85: thay đúng các phần dưới đây, giữ nguyên 13.1–13.3, 13.5, 13.6.'));
for (const j of data.JOBSPEC) {
  C.push(H3(j.title));
  if (j.paras) { j.paras.forEach(t => C.push(para(t, { after: 80 }))); if (j.keep) C.push(note(j.keep)); continue; }
  C.push(new Paragraph({ spacing: { after: 40 }, children: [new TextRun({ text: 'Trigger: ', bold: true, font: FONT, size: SZ }), new TextRun({ text: j.trigger, font: FONT, size: SZ })] }));
  C.push(new Paragraph({ spacing: { after: 40 }, children: [new TextRun({ text: 'Purpose: ', bold: true, font: FONT, size: SZ }), new TextRun({ text: j.purpose, font: FONT, size: SZ })] }));
  C.push(new Paragraph({ spacing: { after: 40 }, children: [new TextRun({ text: 'Related: ', bold: true, font: FONT, size: SZ }), new TextRun({ text: j.related, font: FONT, size: SZ })] }));
  C.push(new Paragraph({ spacing: { after: 80 }, children: [new TextRun({ text: 'Parameters: ', bold: true, font: FONT, size: SZ }), new TextRun({ text: j.params, font: FONT, size: SZ })] }));
  C.push(para('Processing steps:', { after: 40 }));
  let n = 0;
  j.steps.forEach(t => { const sub = t.startsWith('    '); if (!sub) n += 1; C.push(para(sub ? '        ' + t.trim() : n + '. ' + t, { after: 40 })); });
  C.push(para('Failure behavior:', { after: 40 }));
  C.push(table([['Scenario', 'System behavior']].concat(j.fail), [30, 70])); C.push(gap());
}
C.push(new Paragraph({ children: [new PageBreak()] }));
C.push(H2('III.1 General Business Rules')); C.push(table(data.GBR, [9, 37, 15, 21, 18])); C.push(gap());
C.push(H2('III.2 Common Business Rules')); C.push(table(data.CBR, [10, 71, 19])); C.push(gap());
C.push(H2('III.3 Business Rules')); C.push(table(data.BR, [10, 71, 19])); C.push(gap());
C.push(H2('III.4 System Parameters')); C.push(table(data.PARAMS, [7, 36, 14, 17, 11, 15])); C.push(gap());
C.push(H2('III.5 Data Dictionary')); C.push(table(data.DD, [33, 67])); C.push(gap());
C.push(H2('III.6 System Messages'));
C.push(note('Lấy đúng từ constant/Messages.java (MSG01–MSG322). Content giữ tiếng Việt đúng code; %s / %d là giá trị được điền khi hiển thị.'));
C.push(table(data.MSG, [5, 12, 13, 30, 40])); C.push(gap());
C.push(H2('III.7 Other Requirements')); C.push(table(data.NFR, [9, 18, 73]));
C.push(new Paragraph({ children: [new PageBreak()] }));
C.push(H1('3. Mâu thuẫn trong Use Case Specification'));
C.push(note('Sửa trong phần đặc tả UC (luồng, điều kiện, câu thông báo) cho khớp các bảng ở Mục 2.'));
C.push(table(data.UCX, [12, 38, 50])); C.push(gap());
C.push(H2('3b. Mục II — đoạn còn mâu thuẫn (giữ prototype, chỉ sửa / đánh dấu chữ)'));
C.push(table(data.S2, [22, 36, 42])); C.push(gap());
C.push(H1('4. Code cần sửa / làm tiếp (SRS đã đủ, code làm sau)'));
C.push(note('Phần code chưa làm hoặc làm chưa đủ so với SRS. SRS ở Mục 2 đã ghi đủ yêu cầu; danh sách này chỉ để nhóm biết phần code còn lại, không ảnh hưởng tới SRS.'));
C.push(table(data.FIX, [30, 24, 46]));

const doc = new Document({
  styles: { default: { document: { run: { font: FONT, size: SZ } } } },
  sections: [{ properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1134, bottom: 1134, left: 1134, right: 1134 } } },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [
      new TextRun({ text: 'SRS G5 — Update 31/10/2026   Page ', font: FONT, size: 18 }), new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 18 })] })] }) },
    children: C }],
});
Packer.toBuffer(doc).then(b => { fs.writeFileSync(out, b); console.log('written', out, b.length); });
