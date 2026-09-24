/* =====================================================================
   9.1.1 — Lịch rảnh
   Độ dài khung cố định 90 phút; GIỜ BẮT ĐẦU do PT quyết (bám mốc 15 phút),
   khoảng nghỉ giữa hai ca cũng do PT đặt và được tính khi kiểm tra chồng.

   Mọi thay đổi đi qua HÀNG ĐỢI: khung mới là `draft`, khung gỡ là
   `pending-remove`. Khách chỉ thấy sau khi PT bấm Xác nhận công bố.
   PT khai báo được vô thời hạn nên đi tuần tuỳ ý; tuần đã qua chỉ xem.
   ===================================================================== */
(function () {
  const VIEW_KEY = 'pt-lich-view';
  const DAY_START = 5 * 60;
  const DAY_END = 22 * 60;
  const SPAN = DAY_END - DAY_START;
  const DUR = 90;                 // độ dài một ca, cố định
  const SNAP = 15;                // giờ bắt đầu bám mốc 15 phút

  const cal = document.getElementById('cal');
  const bar = document.getElementById('ttBar');
  const countEl = document.getElementById('ttCount');
  const summaryEl = document.getElementById('ttSummary');
  const gapEl = document.getElementById('bulkGap');
  const labelEl = document.getElementById('weekLabel');
  const readonlyEl = document.getElementById('calReadonly');

  const toStr = m => String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
  const toMin = t => Number(t.slice(0, 2)) * 60 + Number(t.slice(3));
  const pct = m => ((m - DAY_START) / SPAN * 100).toFixed(3);
  const hpct = m => (m / SPAN * 100).toFixed(3);
  const gap = () => Number(gapEl.value);
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
    ['bulkApply', 'copyLastWeek', 'cloneApply'].forEach(id => {
      document.getElementById(id).disabled = past;
    });
    renderPreview?.();
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
    if (start < DAY_START || start + DUR > DAY_END) {
      if (!silent) showToast('warning', 'Khung giờ nằm ngoài giờ hoạt động (05:00 – 22:00).');
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
     Rải ca tự động — chia dải giờ thành các ca 90 phút liên tiếp,
     cách nhau đúng khoảng nghỉ, áp cho mọi ngày đã chọn.
     ===================================================================== */
  const bulkFrom = document.getElementById('bulkFrom');
  const bulkTo = document.getElementById('bulkTo');
  const bulkDays = document.getElementById('bulkDays');
  const bulkPreview = document.getElementById('bulkPreview');
  const bulkBtn = document.getElementById('bulkApply');

  /* các giờ bắt đầu sinh ra từ cấu hình hiện tại */
  function bulkPlan() {
    const days = [...bulkDays.querySelectorAll('input:checked')].map(i => Number(i.value));
    const from = toMin(bulkFrom.value || '00:00');
    const to = toMin(bulkTo.value || '00:00');
    const step = DUR + gap();
    const starts = [];
    for (let s = from; s + DUR <= to; s += step) starts.push(s);
    return { days, starts };
  }

  /* dòng xem trước để người dùng hiểu ngay công cụ này làm gì */
  function renderPreview() {
    const { days, starts } = bulkPlan();
    if (!days.length) {
      bulkPreview.textContent = 'Chọn ít nhất một ngày.';
      bulkBtn.disabled = true;
      return;
    }
    if (!starts.length) {
      bulkPreview.textContent = 'Dải giờ chưa đủ cho một ca 90 phút.';
      bulkBtn.disabled = true;
      return;
    }
    const names = days.map(i => DAY_NAMES[i]).join(', ');
    const list = starts.map(s => `${toStr(s)}–${toStr(s + DUR)}`).join(' · ');
    bulkPreview.textContent =
      `${names} → ${starts.length} ca mỗi ngày (${list}), tổng ${days.length * starts.length} khung.`;
    bulkBtn.disabled = weekOffset < 0;
    bulkBtn.textContent = `Thêm ${days.length * starts.length} khung`;
  }

  [bulkFrom, bulkTo, gapEl].forEach(el => el.addEventListener('input', renderPreview));
  bulkDays.addEventListener('change', renderPreview);

  bulkBtn.addEventListener('click', () => {
    const { days, starts } = bulkPlan();
    if (!days.length || !starts.length) return;

    let added = 0;
    let skipped = 0;
    days.forEach(d => starts.forEach(s => {
      if (addSlot(d, s, { silent: true })) added++;
      else skipped++;
    }));
    sync();

    if (!added) return showToast('warning', 'Không thêm được khung nào — trùng lịch sẵn có.');
    showToast('success', skipped
      ? `Đã thêm ${added} khung nháp, bỏ qua ${skipped} khung trùng lịch.`
      : `Đã thêm ${added} khung nháp, cách nhau ${gap()} phút.`);
  });

  /* =====================================================================
     Sao chép giữa các tuần
     ===================================================================== */
  document.getElementById('copyLastWeek').addEventListener('click', () => {
    const src = WEEKS.get(weekOffset - 1);
    if (!src || !src.length) {
      return showToast('info', `${weekLabel(weekOffset - 1)} chưa có khung nào để lấy.`);
    }
    openConfirmModal({
      title: `Lấy lịch ${weekLabel(weekOffset - 1)}?`,
      body: '<p>Các khung của tuần đó sẽ được thêm vào tuần này ở dạng nháp. Khung trùng lịch sẵn có sẽ bị bỏ qua.</p>',
      confirmLabel: 'Lấy lịch',
      danger: false,
      onConfirm() {
        let added = 0;
        src.filter(s => s.state !== 'pending-remove')
          .forEach(s => { if (addSlot(s.d, s.start, { silent: true })) added++; });
        sync();
        showToast(added ? 'success' : 'info', added
          ? `Đã thêm ${added} khung nháp theo tuần trước.`
          : 'Tuần này đã có đủ các khung của tuần trước.');
      },
    });
  });

  document.getElementById('cloneApply').addEventListener('click', () => {
    const n = Number(document.getElementById('cloneTo').value);
    const src = snapshot().filter(s => s.state === 'open' || s.state === 'draft');
    if (!src.length) return showToast('warning', 'Tuần này chưa có khung nào để nhân bản.');

    openConfirmModal({
      title: `Nhân bản ${src.length} khung sang ${n} tuần kế?`,
      body: `<p>Các tuần kế sẽ nhận bản nháp của lịch tuần này. Bạn vẫn phải mở từng tuần và bấm Xác nhận công bố thì khách mới thấy.</p>`,
      confirmLabel: 'Nhân bản',
      danger: false,
      onConfirm() {
        for (let k = 1; k <= n; k++) {
          const off = weekOffset + k;
          const target = WEEKS.get(off) || [];
          const kept = target.slice();
          src.forEach(s => {
            const busy = kept.some(t => s.start < t.start + DUR + gap() && t.start < s.start + DUR + gap() && t.d === s.d);
            if (!busy) kept.push({ d: s.d, start: s.start, state: 'draft', who: '' });
          });
          WEEKS.set(off, kept);
        }
        showToast('success', `Đã nhân bản sang ${n} tuần kế, tất cả ở dạng nháp.`);
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
      title: 'Xác nhận công bố lịch rảnh?',
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
      confirmLabel: 'Huỷ thay đổi',
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

  /* đổi khoảng nghỉ thì vẽ lại dải nghỉ */
  gapEl.addEventListener('change', () => {
    cal.querySelectorAll('.cal-buffer').forEach(b => { b.style.height = hpct(gap()) + '%'; });
    showToast('info', gap()
      ? `Khoảng nghỉ giữa hai ca: ${gap()} phút.`
      : 'Đã bỏ khoảng nghỉ giữa hai ca.');
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
    showToast('info', `Đã đánh dấu gỡ ${group.length} khung của ngày này. Bấm Xác nhận công bố để áp dụng.`);
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

  /* nạp tuần mốc từ DOM tĩnh rồi khởi động */
  WEEKS.set(0, snapshot());
  renderPreview();
  renderWeek();
})();
