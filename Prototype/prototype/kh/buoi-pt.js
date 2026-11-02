/* #51 — Lịch của tôi (chỉ xem): bảng giờ theo tuần hoặc tháng, trạng thái buổi, lý do, liên hệ PT, link thêm vào lịch. */
(function () {
  /* Buổi PT của KH-0241 — cùng dữ liệu với pt/lich-ranh, pt/trang-chu, Gói của tôi */
  const SESSIONS = [
    { d: '2026-08-19', t: '19:00', pt: 'Hoàng Long', kind: 'Gói PT', st: 'missed', why: 'Không check-in' },
    { d: '2026-08-24', t: '17:00', pt: 'Hoàng Long', kind: 'Buổi lẻ', st: 'done' },
    { d: '2026-08-29', t: '08:00', pt: 'Hoàng Long', kind: 'Gói PT', st: 'done' },
    { d: '2026-09-02', t: '19:00', pt: 'Hoàng Long', kind: 'Gói PT', st: 'cancelled', why: 'PT hủy (vẫn tính là đã dùng)' },
    { d: '2026-09-05', t: '08:00', pt: 'Hoàng Long', kind: 'Gói PT', st: 'done' },
    { d: '2026-09-09', t: '19:00', pt: 'Hoàng Long', kind: 'Gói PT', st: 'missed', why: 'Không check-in' },
    { d: '2026-09-12', t: '18:00', pt: 'Hoàng Long', kind: 'Buổi lẻ', st: 'done' },
    { d: '2026-09-16', t: '19:00', pt: 'Hoàng Long', kind: 'Gói PT', st: 'done' },
    { d: '2026-09-23', t: '19:00', pt: 'Hoàng Long', kind: 'Gói PT', st: 'scheduled' },
    { d: '2026-09-26', t: '08:00', pt: 'Hoàng Long', kind: 'Gói PT', st: 'scheduled' },
    { d: '2026-09-30', t: '17:00', pt: 'Hoàng Long', kind: 'Buổi lẻ', st: 'scheduled' },
    { d: '2026-11-18', t: '19:00', pt: 'Hoàng Long', kind: 'Gói PT', st: 'scheduled', why: 'Chưa có gói tập (gói tập hết hạn 14/11)' },
  ];
  const ST = {
    scheduled: ['Đã xếp', 'badge-info'], done: ['Hoàn thành', 'badge-ok'],
    missed: ['Vắng', 'badge-danger'], cancelled: ['Đã hủy', 'badge-muted'],
  };
  const DOW = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
  const el = id => document.getElementById(id);
  const p2 = n => String(n).padStart(2, '0');
  const dmy = d => `${p2(d.getDate())}/${p2(d.getMonth() + 1)}/${d.getFullYear()}`;
  const dm = d => `${p2(d.getDate())}/${p2(d.getMonth() + 1)}`;
  const iso = d => `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}`;
  const parse = s => new Date(s + 'T00:00:00');
  const monday = d => { const m = new Date(d); m.setDate(d.getDate() - ((d.getDay() + 6) % 7)); return m; };
  const addDays = (d, n) => { const x = new Date(d); x.setDate(d.getDate() + n); return x; };

  let view = 'week';
  let cursor = sampleNow();
  cursor.setHours(0, 0, 0, 0);

  const calLink = s => {
    const [h, m] = s.t.split(':').map(Number);
    const start = parse(s.d); start.setHours(h, m);
    const end = new Date(start.getTime() + 90 * 60000);          // P-06: 90 phút
    const f = x => `${x.getFullYear()}${p2(x.getMonth() + 1)}${p2(x.getDate())}T${p2(x.getHours())}${p2(x.getMinutes())}00`;
    return 'https://calendar.google.com/calendar/render?action=TEMPLATE&text=' +
      encodeURIComponent('Buổi PT với ' + s.pt + ' - Super Gym') + `&dates=${f(start)}/${f(end)}`;
  };

  function cell(s) {
    const [lab, cls] = ST[s.st];
    return `<div><span class="badge ${cls}">${lab}</span> <small>${s.kind}</small>
      <div><b>${s.pt}</b></div>
      ${s.why ? `<small class="muted">${s.why}</small>` : ''}
      ${s.st === 'scheduled' ? `<div><a href="${calLink(s)}" target="_blank" rel="noopener noreferrer">Thêm vào lịch</a></div>` : ''}</div>`;
  }

  function renderWeek() {
    const mon = monday(cursor);
    const days = DOW.map((n, i) => ({ n, date: addDays(mon, i) }));
    el('schTitle').textContent = `${dm(days[0].date)} – ${dmy(days[6].date)}`;
    const inWeek = SESSIONS.filter(s => s.d >= iso(days[0].date) && s.d <= iso(days[6].date));
    el('schWeekHead').innerHTML = '<tr><th>Khung giờ</th>' + days.map(d => `<th>${d.n} ${dm(d.date)}</th>`).join('') + '</tr>';
    const times = [...new Set(inWeek.map(s => s.t))].sort();
    el('schWeekBody').innerHTML = times.map(t => `<tr><th scope="row">${t} – ${p2(Math.floor((+t.slice(0, 2) * 60 + +t.slice(3) + 90) / 60))}:${t.slice(3)}</th>` +
      days.map(d => {
        const s = inWeek.find(x => x.t === t && x.d === iso(d.date));
        return `<td data-label="${d.n}">${s ? cell(s) : '<span class="muted">—</span>'}</td>`;
      }).join('') + '</tr>').join('');
    el('schWeekEmpty').hidden = times.length > 0;
    el('schWeek').querySelector('table').hidden = times.length === 0;
  }

  function renderMonth() {
    const first = new Date(cursor.getFullYear(), cursor.getMonth(), 1);
    el('schTitle').textContent = `Tháng ${p2(first.getMonth() + 1)}/${first.getFullYear()}`;
    const start = monday(first);
    let html = '';
    for (let w = 0; w < 6; w++) {
      const rowStart = addDays(start, w * 7);
      if (w > 0 && rowStart.getMonth() !== first.getMonth()) break;
      html += '<tr>' + [...Array(7).keys()].map(i => {
        const d = addDays(rowStart, i);
        const items = SESSIONS.filter(s => s.d === iso(d));
        const out = d.getMonth() !== first.getMonth();
        return `<td data-label="${DOW[i]}" ${out ? 'class="muted"' : ''}><a href="#" data-day="${iso(d)}"><b>${d.getDate()}</b></a>` +
          items.map(s => `<div><span class="badge ${ST[s.st][1]}">${ST[s.st][0]}</span> <small>${s.t}</small></div>`).join('') + '</td>';
      }).join('') + '</tr>';
    }
    el('schMonthBody').innerHTML = html;
  }

  function render() {
    el('schWeek').hidden = view !== 'week';
    el('schMonth').hidden = view !== 'month';
    document.querySelectorAll('[data-view]').forEach(b => b.setAttribute('aria-selected', String(b.dataset.view === view)));
    el('schPrev').setAttribute('aria-label', view === 'month' ? 'Tháng trước' : 'Tuần trước');
    el('schNext').setAttribute('aria-label', view === 'month' ? 'Tháng sau' : 'Tuần sau');
    el('schDate').value = iso(cursor);
    view === 'week' ? renderWeek() : renderMonth();
  }

  const step = dir => {
    if (view === 'week') cursor = addDays(cursor, 7 * dir);
    else cursor = new Date(cursor.getFullYear(), cursor.getMonth() + dir, 1);
    render();
  };
  el('schPrev').addEventListener('click', () => step(-1));
  el('schNext').addEventListener('click', () => step(1));
  el('schGo').addEventListener('click', () => { if (el('schDate').value) { cursor = parse(el('schDate').value); render(); } });
  document.querySelectorAll('[data-view]').forEach(b => b.addEventListener('click', () => { view = b.dataset.view; render(); }));
  el('schMonthBody').addEventListener('click', e => {
    const a = e.target.closest('[data-day]');
    if (!a) return;
    e.preventDefault();
    cursor = parse(a.dataset.day); view = 'week'; render();
  });
  render();
})();
