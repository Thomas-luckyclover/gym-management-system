/* =====================================================================
   9.1.1 — Lịch rảnh
   Độ dài khung cố định 90 phút; GIỜ BẮT ĐẦU do PT quyết (bám mốc 15 phút),
   khoảng nghỉ giữa hai ca cũng do PT đặt và được tính khi kiểm tra chồng.

   Mọi thay đổi đi qua HÀNG ĐỢI: khung mới là `draft`, khung gỡ là
   `pending-remove`. Khách chỉ thấy sau khi PT bấm Công bố.
   PT khai báo được vô thời hạn nên đi tuần tuỳ ý; tuần đã qua chỉ xem.
   ===================================================================== */
(function () {
  const VIEW_KEY = 'pt-lich-view';
  const DAY_START = 5 * 60;
  const DAY_END = 22 * 60;
  const OPEN_FROM = 5 * 60 + 30;    // P-19: mở cửa 05:30
  const SPAN = DAY_END - DAY_START;
  const DUR = 90;                 // độ dài một ca, cố định
  const SNAP = 15;                // giờ bắt đầu bám mốc 15 phút

  const cal = document.getElementById('cal');
  const bar = document.getElementById('ttBar');
  const countEl = document.getElementById('ttCount');
  const summaryEl = document.getElementById('ttSummary');
  const labelEl = document.getElementById('weekLabel');
  const readonlyEl = document.getElementById('calReadonly');

  const toStr = m => String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
  const toMin = t => Number(t.slice(0, 2)) * 60 + Number(t.slice(3));
  const pct = m => ((m - DAY_START) / SPAN * 100).toFixed(3);
  const hpct = m => (m / SPAN * 100).toFixed(3);
  const gap = () => 10;            // khoảng nghỉ cố định giữa hai ca (phút)
  const col = d => cal.querySelector(`.cal-col[data-col="${d}"]`);
  const slotsIn = d => [...col(d).querySelectorAll('.cal-slot')];
  const allSlots = () => [...cal.querySelectorAll('.cal-slot')];

  /* =====================================================================
     Trạng thái tuần — WEEKS lưu khung theo offset so với tuần hiện tại
     ===================================================================== */
  let weekOffset = 0;
  const WEEKS = new Map();

  const MONDAY = new Date(2026, 8, 21);      // Thứ 2 của tuần mốc
  const DAY_NAMES = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
  const p2 = n => String(n).padStart(2, '0');
  const dm = d => `${p2(d.getDate())}/${p2(d.getMonth() + 1)}`;

  function mondayOf(off) {
    const d = new Date(MONDAY);
    d.setDate(MONDAY.getDate() + off * 7);
    return d;
  }
  function weekLabel(off) {
    const a = mondayOf(off);
    const b = new Date(a);
    b.setDate(a.getDate() + 6);
    return `Tuần ${dm(a)} – ${dm(b)}/${b.getFullYear()}`;
  }

  /* đọc tuần hiện tại khỏi DOM để lưu vào WEEKS */
  const snapshot = () => allSlots().map(s => ({
    d: Number(s.dataset.d),
    start: Number(s.dataset.start),
    state: s.dataset.state,
    who: s.dataset.who || '',
  }));

  /* =====================================================================
     Dựng khối
     ===================================================================== */
  function buildSlot({ d, start, state, who }) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'cal-slot';
    btn.dataset.d = d;
    btn.dataset.start = start;
    btn.dataset.state = state;
    if (who) { btn.dataset.who = who; btn.title = who; }
    btn.style.top = pct(start) + '%';
    btn.style.height = hpct(DUR) + '%';

    const label = { draft: 'Nháp', open: 'Còn trống', 'pending-remove': 'Sẽ gỡ', booked: who }[state];
    btn.innerHTML = `<b>${toStr(start)} – ${toStr(start + DUR)}</b><span>${label}</span>`
      + (state === 'booked' ? '' : '<span class="slot-x" data-remove title="Gỡ khung này">&times;</span>');
    return btn;
  }

  function buildBuffer(d, after) {
    const el = document.createElement('div');
    el.className = 'cal-buffer';
    el.dataset.d = d;
    el.dataset.after = after;
    el.style.top = pct(after) + '%';
    el.style.height = hpct(gap()) + '%';
    return el;
  }

  function renderWeek() {
    const data = WEEKS.get(weekOffset) || [];
    DAY_NAMES.forEach((_, i) => { col(i).innerHTML = ''; });
    data.forEach(s => {
      col(s.d).append(buildBuffer(s.d, s.start + DUR), buildSlot(s));
    });

    /* nhãn cột theo tuần đang xem */
    const mon = mondayOf(weekOffset);
    cal.querySelectorAll('.cal-head [data-pick-col]').forEach(btn => {
      const i = Number(btn.dataset.pickCol);
      const d = new Date(mon);
      d.setDate(mon.getDate() + i);
      btn.innerHTML = `${DAY_NAMES[i]}<em>${dm(d)}</em>`;
    });

    labelEl.textContent = weekLabel(weekOffset);
    labelEl.dataset.weekOffset = weekOffset;
    document.querySelector('[data-view="cal"] .panel-head h2').textContent = weekLabel(weekOffset);

    const past = weekOffset < 0;
    cal.classList.toggle('is-readonly', past);
    readonlyEl.hidden = !past;
    document.getElementById('weekToday').hidden = weekOffset === 0;
    ['addSlotBtn', 'clearFree', 'copyNextWeek'].forEach(id => {
      document.getElementById(id).disabled = past;
    });
    sync();
  }

  function gotoWeek(off) {
    WEEKS.set(weekOffset, snapshot());
    weekOffset = off;
    renderWeek();
  }

  /* =====================================================================
     Hàng đợi thay đổi
     ===================================================================== */
  const drafts = () => allSlots().filter(s => s.dataset.state === 'draft');
  const removing = () => allSlots().filter(s => s.dataset.state === 'pending-remove');

  function sync() {
    const add = drafts().length;
    const del = removing().length;
    const n = add + del;
    countEl.textContent = n;
    summaryEl.textContent = n
      ? [add ? `+${add} mở mới` : '', del ? `−${del} gỡ` : ''].filter(Boolean).join(' · ')
      : '';
    bar.hidden = n === 0 || weekOffset < 0;
  }

  /* gỡ: khung đã công bố thì xếp hàng, khung nháp thì xoá thẳng */
  cal.addEventListener('click', e => {
    const slot = e.target.closest('.cal-slot');
    if (!slot || weekOffset < 0) return;

    if (slot.dataset.state === 'booked') {
      return showToast('info', 'Khung giờ đã có khách đặt. Xử lý buổi tập trước khi gỡ.');
    }
    if (!e.target.closest('[data-remove]')) return;

    if (slot.dataset.state === 'draft') {
      col(Number(slot.dataset.d))
        .querySelector(`.cal-buffer[data-after="${Number(slot.dataset.start) + DUR}"]`)?.remove();
      slot.remove();
    } else if (slot.dataset.state === 'open') {
      slot.dataset.state = 'pending-remove';
      slot.querySelector('span').textContent = 'Sẽ gỡ';
    } else {
      slot.dataset.state = 'open';                 // bấm lại thì hoàn tác
      slot.querySelector('span').textContent = 'Còn trống';
    }
    sync();
  });

  /* =====================================================================
     Kiểm tra chồng — có tính khoảng nghỉ ở cả hai đầu
     ===================================================================== */
  function clashOf(d, start) {
    const end = start + DUR;
    const g = gap();
    return slotsIn(d).find(s => {
      if (s.dataset.state === 'pending-remove') return false;
      const a = Number(s.dataset.start);
      return start < a + DUR + g && a < end + g;
    });
  }

  function addSlot(d, start, { silent = false } = {}) {
    if (start < OPEN_FROM || start + DUR > DAY_END) {
      if (!silent) showToast('warning', 'Khung giờ nằm ngoài giờ hoạt động (05:30 – 22:00).');
      return false;
    }
    const clash = clashOf(d, start);
    if (clash) {
      if (!silent) {
        const a = Number(clash.dataset.start);
        showToast('warning', gap()
          ? `Trùng hoặc không đủ ${gap()} phút nghỉ so với khung ${toStr(a)} – ${toStr(a + DUR)}.`
          : `Khung này trùng với khung ${toStr(a)} – ${toStr(a + DUR)}.`);
      }
      return false;
    }
    col(d).append(buildBuffer(d, start + DUR), buildSlot({ d, start, state: 'draft' }));
    return true;
  }

  /* =====================================================================
     Kéo trên cột trống để mở khung mới
     ===================================================================== */
  let ghost = null;
  let dragCol = null;

  const minAt = (colEl, clientY) => {
    const r = colEl.getBoundingClientRect();
    const raw = DAY_START + (clientY - r.top) / r.height * SPAN;
    return Math.round(raw / SNAP) * SNAP;
  };
  const clearGhost = () => { ghost?.remove(); ghost = null; dragCol = null; };

  cal.addEventListener('pointerdown', e => {
    if (weekOffset < 0 || e.target.closest('.cal-slot')) return;
    const colEl = e.target.closest('.cal-col');
    if (!colEl) return;
    e.preventDefault();
    colEl.setPointerCapture?.(e.pointerId);
    dragCol = colEl;
    const start = Math.min(minAt(colEl, e.clientY), DAY_END - DUR);
    ghost = document.createElement('div');
    ghost.className = 'cal-ghost';
    ghost.style.top = pct(start) + '%';
    ghost.style.height = hpct(DUR) + '%';
    ghost.textContent = `${toStr(start)} – ${toStr(start + DUR)}`;
    ghost.dataset.start = start;
    colEl.append(ghost);
  });

  cal.addEventListener('pointermove', e => {
    if (!ghost || !dragCol) return;
    const start = Math.min(Math.max(minAt(dragCol, e.clientY), DAY_START), DAY_END - DUR);
    ghost.style.top = pct(start) + '%';
    ghost.textContent = `${toStr(start)} – ${toStr(start + DUR)}`;
    ghost.dataset.start = start;
  });

  function finishDrag() {
    if (!ghost || !dragCol) return clearGhost();
    const d = Number(dragCol.dataset.col);
    const start = Number(ghost.dataset.start);
    clearGhost();
    if (addSlot(d, start)) {
      sync();
      showToast('success', `Đã thêm nháp ${toStr(start)} – ${toStr(start + DUR)}.`);
    }
  }
  cal.addEventListener('pointerup', finishDrag);
  cal.addEventListener('pointercancel', clearGhost);
  addEventListener('blur', clearGhost);

  /* =====================================================================
     Gỡ hết khung trống · Chép sang tuần sau
     ===================================================================== */
  document.getElementById('clearFree').addEventListener('click', () => {
    const free = allSlots().filter(s => s.dataset.state === 'open' || s.dataset.state === 'draft');
    if (!free.length) return showToast('info', 'Tuần này không còn khung trống nào.');
    openConfirmModal({
      title: `Gỡ hết ${free.length} khung trống?`,
      body: '<p>Khung đã công bố sẽ được đánh dấu gỡ, khung nháp bị xoá. Khung đã có khách không bị ảnh hưởng. Bấm Công bố để áp dụng.</p>',
      confirmLabel: 'Gỡ hết',
      onConfirm() {
        free.forEach(s => {
          if (s.dataset.state === 'draft') {
            col(Number(s.dataset.d))
              .querySelector(`.cal-buffer[data-after="${Number(s.dataset.start) + DUR}"]`)?.remove();
            s.remove();
          } else {
            s.dataset.state = 'pending-remove';
            s.querySelector('span').textContent = 'Sẽ gỡ';
          }
        });
        sync();
        showToast('info', `Đã đánh dấu gỡ ${free.length} khung trống. Bấm Công bố để áp dụng.`);
      },
    });
  });

  document.getElementById('copyNextWeek').addEventListener('click', () => {
    const src = snapshot().filter(s => s.state === 'open' || s.state === 'draft');
    if (!src.length) return showToast('warning', 'Tuần này chưa có khung trống nào để chép.');
    openConfirmModal({
      title: `Chép ${src.length} khung sang ${weekLabel(weekOffset + 1)}?`,
      body: '<p>Các khung trống của tuần này được thêm vào tuần sau ở dạng nháp. Khung trùng giờ sẽ bị bỏ qua. Mở tuần sau và bấm Công bố thì khách mới thấy.</p>',
      confirmLabel: 'Chép sang',
      danger: false,
      onConfirm() {
        const off = weekOffset + 1;
        const kept = (WEEKS.get(off) || []).slice();
        let added = 0;
        src.forEach(s => {
          const busy = kept.some(t => t.d === s.d && s.start < t.start + DUR + gap() && t.start < s.start + DUR + gap());
          if (!busy) { kept.push({ d: s.d, start: s.start, state: 'draft', who: '' }); added++; }
        });
        WEEKS.set(off, kept);
        showToast('success', `Đã chép ${added} khung nháp sang tuần sau.`);
      },
    });
  });

  /* =====================================================================
     Công bố / huỷ thay đổi
     ===================================================================== */
  document.getElementById('ttPublish').addEventListener('click', () => {
    const add = drafts();
    const del = removing();
    if (!add.length && !del.length) return;

    openConfirmModal({
      title: 'Công bố lịch rảnh?',
      danger: false,
      confirmLabel: 'Công bố',
      body: `<p>Sau khi công bố, khách đặt được ngay các khung mở mới.</p>
        <div class="side-card" style="margin-top:var(--space-3)">
          <div class="side-row"><span>Mở mới</span><b>+${add.length} khung</b></div>
          <div class="side-row"><span>Gỡ khỏi lịch đặt</span><b>−${del.length} khung</b></div>
          <div class="side-row"><span>Tuần áp dụng</span><b>${weekLabel(weekOffset)}</b></div>
        </div>
        <p class="muted" style="margin-top:var(--space-3)">Khách chỉ đặt được slot trong tuần hiện tại; từ Thứ 7 mở thêm tuần kế.</p>`,
      onConfirm() {
        add.forEach(s => {
          s.dataset.state = 'open';
          s.querySelector('span').textContent = 'Còn trống';
        });
        del.forEach(s => {
          col(Number(s.dataset.d))
            .querySelector(`.cal-buffer[data-after="${Number(s.dataset.start) + DUR}"]`)?.remove();
          s.remove();
        });
        sync();
        showToast('success', `Đã công bố: +${add.length} khung mở mới, −${del.length} khung gỡ.`);
      },
    });
  });

  document.getElementById('ttDiscard').addEventListener('click', () => {
    openConfirmModal({
      title: 'Huỷ mọi thay đổi chưa công bố?',
      body: '<p>Khung nháp sẽ bị xoá, khung đang chờ gỡ trở lại bình thường. Lịch quay về đúng trạng thái khách đang thấy.</p>',
      confirmLabel: 'Bỏ tất cả',
      onConfirm() {
        drafts().forEach(s => {
          col(Number(s.dataset.d))
            .querySelector(`.cal-buffer[data-after="${Number(s.dataset.start) + DUR}"]`)?.remove();
          s.remove();
        });
        removing().forEach(s => {
          s.dataset.state = 'open';
          s.querySelector('span').textContent = 'Còn trống';
        });
        sync();
        showToast('info', 'Đã huỷ thay đổi chưa công bố.');
      },
    });
  });

  addEventListener('beforeunload', e => {
    if (drafts().length + removing().length === 0) return;
    e.preventDefault();
    e.returnValue = '';
  });

  /* chọn cả ngày — dùng để gỡ nhanh cả cột */
  document.querySelectorAll('[data-pick-col]').forEach(btn => btn.addEventListener('click', () => {
    if (weekOffset < 0) return;
    const d = Number(btn.dataset.pickCol);
    const group = slotsIn(d).filter(s => s.dataset.state === 'open');
    if (!group.length) return showToast('info', 'Ngày này chưa có khung nào đã công bố.');
    group.forEach(s => {
      s.dataset.state = 'pending-remove';
      s.querySelector('span').textContent = 'Sẽ gỡ';
    });
    sync();
    showToast('info', `Đã đánh dấu gỡ ${group.length} khung của ngày này. Bấm Công bố để áp dụng.`);
  }));

  /* điều hướng tuần — PT khai báo được vô thời hạn */
  document.getElementById('weekPrev').addEventListener('click', () => gotoWeek(weekOffset - 1));
  document.getElementById('weekNext').addEventListener('click', () => gotoWeek(weekOffset + 1));
  document.getElementById('weekToday').addEventListener('click', () => gotoWeek(0));

  /* =====================================================================
     Chế độ xem
     ===================================================================== */
  function setView(v) {
    document.querySelectorAll('[data-view]').forEach(el => { el.hidden = el.dataset.view !== v; });
    document.querySelectorAll('[data-view-btn]').forEach(b =>
      b.setAttribute('aria-pressed', String(b.dataset.viewBtn === v)));
    document.querySelectorAll('[data-view-only]').forEach(el => {
      el.style.display = el.dataset.viewOnly === v ? '' : 'none';
    });
    try { localStorage.setItem(VIEW_KEY, v); } catch {}
  }
  document.querySelectorAll('[data-view-btn]').forEach(b =>
    b.addEventListener('click', () => setView(b.dataset.viewBtn)));
  let savedView = 'cal';
  try { savedView = localStorage.getItem(VIEW_KEY) || 'cal'; } catch {}
  setView(savedView);

  /* =====================================================================
     Danh sách
     ===================================================================== */
  const search = document.getElementById('slotSearch');
  const state = document.getElementById('slotState');

  const list = makeList({
    listSel: '#slotList',
    perPage: 8,
    unit: 'khung giờ',
    matcher(row) {
      const q = search.value.trim().toLowerCase();
      if (q && !viKey(row.dataset.q).includes(viKey(q))) return false;
      return state.value === 'all' || row.dataset.state === state.value;
    },
  });
  [search, state].forEach(el => el.addEventListener('input', list.reset));
  document.getElementById('slotReset').addEventListener('click', () => {
    search.value = ''; state.value = 'all'; list.reset();
  });
  list.render();

  document.addEventListener('click', e => {
    const btn = e.target.closest('[data-del-slot]');
    if (!btn) return;
    const row = btn.closest('.data-row');
    openConfirmModal({
      title: 'Gỡ khung giờ này?',
      body: '<p>Khung giờ sẽ biến khỏi lịch đặt của khách ngay lập tức.</p>',
      confirmLabel: 'Gỡ công bố',
      onConfirm() {
        row.remove();
        showToast('success', 'Đã gỡ khung giờ.');
        list.render();
      },
    });
  });

  /* ---------- Thêm khung giờ (gộp #55) ---------- */
  const addDay = document.getElementById('addDay');
  const addStart = document.getElementById('addStart');
  const addEnd = document.getElementById('addEnd');
  const addErr = document.getElementById('addErr');
  const addEndSync = () => {
    addEnd.textContent = addStart.value ? toStr(toMin(addStart.value) + DUR) : '--:--';
    addErr.hidden = true;
  };
  addStart.addEventListener('input', addEndSync);
  addEndSync();
  document.getElementById('addSlotBtn').addEventListener('click', () => {
    if (weekOffset < 0) return showToast('info', 'Tuần đã qua chỉ xem, không thêm được khung.');
    if (!addStart.value) { addErr.textContent = 'Vui lòng nhập giờ bắt đầu.'; addErr.hidden = false; return; }
    const m = toMin(addStart.value);
    if (m % SNAP) { addErr.textContent = 'Giờ bắt đầu phải bám mốc 15 phút.'; addErr.hidden = false; return; }
    if (addSlot(Number(addDay.value), m)) {
      sync();
      showToast('success', 'Đã thêm khung vào nháp. Bấm Công bố để khách thấy.');
    }
  });

  /* ---------- Xử lý buổi (BF-08): dời · chuyển PT · huỷ ----------
     Khớp pt/_handle-dialog.jspf: đầu hộp (giờ, khách, gói, hạn gói), 3 thẻ chọn
     phương án, mỗi phương án một khung riêng. PT không điểm danh. */
  const TERM_END = { 'Nguyễn Minh An': '18/11/2026', 'Trần Gia Bảo': '14/10/2026', 'Lê Thu Hà': '12/10/2026',
    'Phạm Quốc Huy': '05/10/2026', 'Trịnh Quốc Bảo': '15/10/2026', 'Vũ Hoài Nam & Đỗ Lan Chi': '22/10/2026' };
  const PKG_OF = { 'Nguyễn Minh An': 'Gói PT 3 Tháng', 'Trần Gia Bảo': 'Gói PT 2 Tháng', 'Lê Thu Hà': 'Gói PT 1 Tháng',
    'Phạm Quốc Huy': 'Gói PT 1 Tháng', 'Trịnh Quốc Bảo': 'Gói PT 1 Tháng', 'Vũ Hoài Nam & Đỗ Lan Chi': 'Gói PT Couple 1 Tháng' };
  const OTHER_PTS = { T2: ['Đỗ Minh Khoa'], T3: [], T4: ['Phạm Thu Trang', 'Vũ Hải Yến'], T5: ['Đỗ Minh Khoa'], T6: ['Phạm Thu Trang'], T7: ['Vũ Hải Yến'], CN: [] };
  const DNAMES = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

  const rowBy = (dayIdx, start) => [...document.querySelectorAll('#slotList .data-row')].find(r => {
    const t = r.querySelector('.row-title').firstChild.textContent;
    const [dow, , from] = t.trim().split(/\s*·\s*|\s+–\s+/);
    return DNAMES.indexOf(dow) === dayIdx && toMin(from) === start;
  });
  function freeSlot(row, slotEl) {
    row.dataset.state = 'open';
    const badge = row.querySelector('.row-title .badge');
    badge.className = 'badge badge-muted';
    badge.textContent = 'Còn trống';
    row.querySelector('.row-meta')?.remove();
    row.querySelector('.row-actions').innerHTML =
      '<button class="btn btn-outline btn-sm" type="button" data-del-slot>Gỡ công bố</button>';
    if (slotEl) {
      slotEl.dataset.state = 'open';
      slotEl.removeAttribute('data-who');
      slotEl.title = '';
      slotEl.querySelector('span').textContent = 'Còn trống';
      if (!slotEl.querySelector('.slot-x')) slotEl.insertAdjacentHTML('beforeend', '<span class="slot-x" data-remove title="Gỡ khung này">&times;</span>');
    }
  }
  function bookSlot(row, slotEl, who) {
    row.dataset.state = 'booked';
    const badge = row.querySelector('.row-title .badge');
    badge.className = 'badge badge-info';
    badge.textContent = 'Đã có khách';
    row.querySelector('.row-main').insertAdjacentHTML('beforeend', `<div class="row-meta"><span><i class="fa-solid fa-user"></i> ${who}</span></div>`);
    row.querySelector('.row-actions').innerHTML =
      '<button class="btn btn-ghost btn-sm" type="button" data-handle-session>Xử lý buổi</button>' +
      '<button class="btn btn-outline btn-sm" type="button" disabled title="Khung giờ đã có khách đặt, không gỡ được">Gỡ công bố</button>';
    if (slotEl) {
      slotEl.dataset.state = 'booked';
      slotEl.dataset.who = who;
      slotEl.title = who;
      slotEl.querySelector('span').textContent = who;
      slotEl.querySelector('.slot-x')?.remove();
    }
  }

  document.addEventListener('click', e => {
    const btn = e.target.closest('[data-handle-session]');
    if (!btn) return;
    const row = btn.closest('.data-row');
    const when = row.querySelector('.row-title').firstChild.textContent.trim();
    const who = row.querySelector('.row-meta span').textContent.trim();
    const [dow, dateStr, from] = when.split(/\s*·\s*|\s+–\s+/);
    const dayIdx = DNAMES.indexOf(dow);
    const start = toMin(from);
    const srcSlot = cal.querySelector(`.cal-slot[data-d="${dayIdx}"][data-start="${start}"]`);
    const isCouple = who.includes('&');

    // khung Free của chính PT, ngày sau buổi hiện tại (đã lọc theo hạn gói)
    const moves = allSlots().filter(x => x.dataset.state === 'open' &&
      (Number(x.dataset.d) > dayIdx || (Number(x.dataset.d) === dayIdx && Number(x.dataset.start) > start)));
    const pts = OTHER_PTS[dow] || [];
    const sessionTime = `${from} – ${toStr(start + DUR)}`;

    const pills = moves.length ? DNAMES.map((dn, di) => {
      const ds = moves.filter(x => Number(x.dataset.d) === di);
      if (!ds.length) return '';
      return `<div class="row-meta"><b>${dn}</b> ${ds.map(x => `<label class="pay-option" style="display:inline-flex"><input type="radio" name="hdSlot" value="${di}|${x.dataset.start}"><span>${toStr(Number(x.dataset.start))}</span></label>`).join(' ')}</div>`;
    }).join('') : '';

    openConfirmModal({
      title: `Xử lý buổi ${dow} · ${dateStr} · ${sessionTime}`,
      body: `<p><b>${who}</b> · ${PKG_OF[who] || 'Gói PT'}</p>
             <p class="row-meta"><span>Hạn gói đến ${TERM_END[who] || '—'}</span>${isCouple ? '<span class="badge badge-muted">Gói đôi</span>' : ''}</p>
             <label class="pay-option"><input type="radio" name="hdAct" value="reschedule" checked>
               <i class="fa-solid fa-calendar-days"></i><span><b>Dời buổi</b><small>Sang khung trống khác của bạn · ${moves.length} khung</small></span></label>
             <label class="pay-option"><input type="radio" name="hdAct" value="transfer">
               <i class="fa-solid fa-user-pen"></i><span><b>Chuyển PT</b><small>Giữ giờ, PT khác dạy thay · ${pts.length} PT trống</small></span></label>
             <label class="pay-option"><input type="radio" name="hdAct" value="cancel">
               <i class="fa-solid fa-ban"></i><span><b>Hủy buổi</b><small>Vẫn tính buổi, hoàn tiền mặt</small></span></label>

             <div data-panel="reschedule">
               ${moves.length ? `<p class="muted">${moves.length} khung hợp lệ · đã lọc theo hạn gói, ngày nghỉ, đơn nghỉ và lịch của khách.</p>${pills}
               <label class="field"><span class="field-label">Ghi chú gửi khách <small>(không bắt buộc)</small></span>
                 <textarea id="hdNote" rows="2" maxlength="500" placeholder="VD: Mình bận việc đột xuất, dời giúp bạn sang giờ này nhé."></textarea></label>
               <p class="muted">Khách nhận thông báo · không tính lượt đổi · khung cũ trở lại Còn trống.</p>`
               : '<p class="muted">Chưa có khung trống phù hợp. Thêm khung rảnh trên lịch rồi Công bố, sau đó mở lại buổi này.</p>'}
             </div>
             <div data-panel="transfer" hidden>
               ${pts.length ? `${pts.map(n => `<label class="pay-option"><input type="radio" name="hdPt" value="${n}"><span><b>${n}</b><small>Trống ${sessionTime}</small></span></label>`).join('')}
               <p class="muted">Giữ nguyên ngày giờ · khách và PT nhận đều được báo · hoa hồng buổi này thuộc PT nhận.</p>`
               : `<p class="muted">Không PT nào đang trống đúng ${sessionTime} ngày này. Nhờ PT khác công bố khung giờ này, hoặc chọn Dời buổi.</p>`}
             </div>
             <div data-panel="cancel" hidden>
               <ul class="muted"><li>Buổi vẫn tính là đã dùng, bạn vẫn nhận hoa hồng.</li><li>Bạn tự hoàn tiền mặt cho khách ngoài hệ thống.</li><li>Không hoàn tác được.</li></ul>
               <label class="field"><span class="field-label">Lý do hủy <small>(5–500 ký tự, khách sẽ đọc)</small></span>
                 <textarea id="hdReason" rows="3" minlength="5" maxlength="500"></textarea>
                 <span class="field-error" id="hdReasonErr" hidden></span></label>
               <label class="pay-option"><input type="checkbox" id="hdRefund"><span>Khách đã đồng ý nhận lại tiền mặt cho buổi này.</span></label>
             </div>`,
      confirmLabel: 'Dời buổi',
      danger: false,
      onConfirm() {
        const act = document.querySelector('input[name="hdAct"]:checked').value;
        let msg;
        if (act === 'reschedule') {
          const pick = document.querySelector('input[name="hdSlot"]:checked');
          if (!pick) { showToast('warning', 'Chọn một khung trống để dời.'); return false; }
          const [td, ts] = pick.value.split('|').map(Number);
          const target = cal.querySelector(`.cal-slot[data-d="${td}"][data-start="${ts}"]`);
          const trow = rowBy(td, ts);
          freeSlot(row, srcSlot);
          if (trow) bookSlot(trow, target, who);
          msg = `Đã dời buổi sang ${DNAMES[td]} ${toStr(ts)}. Khách nhận thông báo, khung cũ trở lại Còn trống.`;
        } else if (act === 'transfer') {
          const pt = document.querySelector('input[name="hdPt"]:checked');
          if (!pt) { showToast('warning', pts.length ? 'Chọn PT nhận buổi.' : 'Không có PT trống để chuyển.'); return false; }
          freeSlot(row, srcSlot);
          msg = `Đã chuyển buổi cho ${pt.value}. Khách và PT nhận đều được báo, hoa hồng thuộc PT nhận.`;
        } else {
          const r = document.getElementById('hdReason');
          if (r.value.trim().length < 5) { fieldError(r, 'hdReasonErr', 'Nêu lý do huỷ, tối thiểu 5 ký tự.'); return false; }
          if (!document.getElementById('hdRefund').checked) { showToast('warning', 'Cần xác nhận khách đồng ý nhận lại tiền mặt.'); return false; }
          freeSlot(row, srcSlot);
          msg = 'Đã hủy buổi. Buổi vẫn tính là đã dùng, khách nhận thông báo.';
        }
        list.render();
        sync();
        showToast('success', msg);
      },
    });

    // đổi khung theo phương án; nút xác nhận đổi chữ theo phương án; "Bỏ qua" thành "Để sau"
    const root = document.getElementById('modalRoot');
    const okBtn = root.querySelector('[data-modal-confirm]');
    root.querySelector('[data-modal-close]').textContent = 'Để sau';
    const LABEL = { reschedule: 'Dời buổi', transfer: 'Chuyển PT', cancel: 'Hủy buổi' };
    root.querySelectorAll('input[name="hdAct"]').forEach(r => r.addEventListener('change', () => {
      const act = root.querySelector('input[name="hdAct"]:checked').value;
      root.querySelectorAll('[data-panel]').forEach(p => { p.hidden = p.dataset.panel !== act; });
      okBtn.textContent = LABEL[act];
      okBtn.className = 'btn ' + (act === 'cancel' ? 'btn-danger' : 'btn-primary');
    }));
  });

  /* nạp tuần mốc từ DOM tĩnh rồi khởi động */
  WEEKS.set(0, snapshot());
  /* tuần trước (đã qua): chỉ còn buổi hôm nay CN 20/09 19:00 */
  WEEKS.set(-1, [{ d: 6, start: 19 * 60, state: 'booked', who: 'Trịnh Quốc Bảo' }]);
  /* tuần 28/09 – 04/10: các buổi đã có khách (khớp lịch khách) */
  WEEKS.set(1, [
    { d: 0, start: 19 * 60, state: 'booked', who: 'Trịnh Quốc Bảo' },
    { d: 2, start: 17 * 60, state: 'booked', who: 'Nguyễn Minh An' },
    { d: 3, start: 17 * 60, state: 'booked', who: 'Bùi Khánh Linh' },
  ]);
  renderWeek();
})();
