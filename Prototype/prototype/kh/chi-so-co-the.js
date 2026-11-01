/* #72 — Chỉ số cơ thể: ô số, biểu đồ, form ghi lần đo (cùng ngày thì ghi đè), lịch sử. Chỉ khách xem được. */
(function () {
  const rows = [
    { d: '2026-07-05', w: 63.2, h: 165, fat: 29.4, mus: 36.8, waist: 78, note: 'InBody tại phòng' },
    { d: '2026-08-02', w: 62.1, h: 165, fat: 28.6, mus: 37.3, waist: 77, note: '' },
    { d: '2026-08-30', w: 61.0, h: 165, fat: 27.9, mus: 37.9, waist: 75, note: '' },
    { d: '2026-09-13', w: 60.4, h: 165, fat: 27.2, mus: 38.2, waist: 74, note: 'Sau 3 tuần tập đều' },
  ];
  const METRICS = {
    w:     { label: 'Cân nặng', unit: 'kg', get: r => r.w },
    bmi:   { label: 'BMI', unit: '', get: r => r.w / ((r.h / 100) ** 2) },
    fat:   { label: 'Mỡ', unit: '%', get: r => r.fat },
    mus:   { label: 'Cơ', unit: '%', get: r => r.mus },
    waist: { label: 'Vòng eo', unit: 'cm', get: r => r.waist },
  };
  let metric = 'w';
  const el = id => document.getElementById(id);
  const num = (v, d = 1) => v == null ? '—' : v.toFixed(d).replace('.', ',');
  const vi = s => s.split('-').reverse().join('/');
  const parse = t => { const v = t.trim().replace(',', '.'); return v === '' ? null : Number(v); };

  const sorted = () => [...rows].sort((a, b) => a.d.localeCompare(b.d));

  function tiles() {
    const s = sorted(), last = s[s.length - 1], prev = s[s.length - 2];
    el('stTiles').innerHTML = Object.entries(METRICS).map(([k, m]) => {
      const v = last ? m.get(last) : null;
      const pv = prev ? m.get(prev) : null;
      const change = v != null && pv != null ? (v - pv >= 0 ? '+' : '') + num(v - pv) + ' ' + m.unit : 'Cần ít nhất 2 lần đo';
      return `<div class="kpi-card ${k === metric ? 'highlight' : ''}" data-metric="${k}" style="cursor:pointer" role="button" tabindex="0">
        <div class="kpi-top"><span class="section-label">${m.label}</span></div>
        <div class="kpi-value tabular is-key">${num(v)} <small>${m.unit}</small></div>
        <div class="kpi-note">${change}</div></div>`;
    }).join('');
  }

  function chart() {
    const m = METRICS[metric];
    const pts = sorted().map(r => [r.d, m.get(r)]).filter(p => p[1] != null);
    el('stChartTitle').textContent = m.label;
    el('stChartEmpty').hidden = pts.length >= 2;
    const svg = el('stChart');
    if (pts.length < 2) { svg.innerHTML = ''; return; }
    const vals = pts.map(p => p[1]);
    const lo = Math.min(...vals) - 1, hi = Math.max(...vals) + 1;
    const x = i => 44 + i * (580 / (pts.length - 1));
    const y = v => 200 - (v - lo) / (hi - lo) * 170;
    svg.innerHTML =
      `<polyline fill="none" stroke="var(--primary)" stroke-width="3" points="${pts.map((p, i) => x(i) + ',' + y(p[1])).join(' ')}"/>` +
      pts.map((p, i) => `<circle cx="${x(i)}" cy="${y(p[1])}" r="${i === pts.length - 1 ? 6 : 4}" fill="var(--primary)"><title>${vi(p[0])}: ${num(p[1])} ${m.unit}</title></circle>`).join('') +
      `<text x="44" y="228" font-size="11" fill="currentColor">${vi(pts[0][0])}</text>` +
      `<text x="624" y="228" font-size="11" text-anchor="end" fill="currentColor">${vi(pts[pts.length - 1][0])}</text>`;
  }

  function history() {
    const s = sorted();
    el('stHistory').innerHTML = [...s].reverse().map(r => {
      const i = s.indexOf(r), p = s[i - 1];
      const dw = p ? (r.w - p.w >= 0 ? '+' : '') + num(r.w - p.w) : '—';
      return `<tr><td class="tabular">${vi(r.d)}</td><td class="tabular">${num(r.w)} kg</td><td class="tabular">${dw}</td>
        <td class="tabular">${num(METRICS.bmi.get(r))}</td><td>${r.fat == null ? '—' : num(r.fat) + '%'}</td><td>${r.mus == null ? '—' : num(r.mus) + '%'}</td>
        <td>${r.waist == null ? '—' : num(r.waist, 0) + ' cm'}</td><td>${r.note || '—'}</td>
        <td><button class="btn btn-ghost btn-sm" type="button" data-del="${r.d}" aria-label="Xóa lần đo ${vi(r.d)}">Xóa</button></td></tr>`;
    }).join('');
  }

  const redraw = () => { tiles(); chart(); history(); };

  el('stTiles').addEventListener('click', e => {
    const t = e.target.closest('[data-metric]');
    if (t) { metric = t.dataset.metric; redraw(); }
  });
  el('stHistory').addEventListener('click', e => {
    const b = e.target.closest('[data-del]');
    if (!b) return;
    openConfirmModal({
      title: `Xóa lần đo ${vi(b.dataset.del)}?`,
      body: '<p>Lần đo bị xóa không khôi phục được.</p>',
      confirmLabel: 'Xóa',
      onConfirm() {
        rows.splice(rows.findIndex(r => r.d === b.dataset.del), 1);
        redraw();
        showToast('success', 'Đã xóa lần đo.');
      },
    });
  });

  el('stForm').addEventListener('submit', e => {
    e.preventDefault();
    ['stDateErr', 'stWeightErr', 'stHeightErr'].forEach(clearErr);
    const d = el('stDate').value, w = parse(el('stWeight').value), h = parse(el('stHeight').value);
    let ok = true;
    if (!d || d > '2026-09-20') { fieldError(el('stDate'), 'stDateErr', 'Chọn ngày đo, không muộn hơn hôm nay.'); ok = false; }
    if (!(w >= 20 && w <= 300)) { fieldError(el('stWeight'), 'stWeightErr', 'Cân nặng từ 20 đến 300 kg.'); ok = false; }
    if (!(h >= 100 && h <= 250)) { fieldError(el('stHeight'), 'stHeightErr', 'Chiều cao từ 100 đến 250 cm.'); ok = false; }
    if (!ok) return;
    const rec = { d, w, h, fat: parse(el('stFat').value), mus: parse(el('stMuscle').value), waist: parse(el('stWaist').value), note: el('stNote').value.trim() };
    const i = rows.findIndex(r => r.d === d);
    if (i >= 0) rows[i] = rec; else rows.push(rec);       // cùng ngày thì ghi đè
    ['stWeight', 'stFat', 'stMuscle', 'stWaist', 'stNote'].forEach(id => { el(id).value = ''; });
    redraw();
    showToast('success', 'Đã lưu lần đo.');
  });

  redraw();
})();
