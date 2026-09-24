/* =====================================================================
   9.4.1 — Điểm danh & xử lý buổi không dạy được
   Mặc định là Có mặt; PT chỉ tick khi khách vắng.
   "Không dạy được" mở bảng 4 lựa chọn, mỗi lựa chọn là một modal riêng.
   ===================================================================== */
(function () {
  const cards = [...document.querySelectorAll('.att-card')];
  const empty = document.querySelector('[data-empty]');
  const search = document.getElementById('attSearch');
  const shift = document.getElementById('attShift');
  const countEl = document.getElementById('attCount');
  const confirmBtn = document.getElementById('attConfirm');

  /* slot rảnh của chính PT, dùng cho Dời buổi và Đổi lịch theo yêu cầu khách */
  const MY_SLOTS = [
    { label: 'T2 21/09 · 06:00', ok: true },
    { label: 'T3 22/09 · 08:00', ok: true },
    { label: 'T4 23/09 · 19:00', ok: true },
    { label: 'T5 24/09 · 06:00', ok: true },
    { label: 'T6 25/09 · 08:00', ok: true },
    { label: 'T7 26/09 · 10:00', ok: true },
    { label: 'CN 27/09 · 08:00', ok: true },
    { label: 'T2 28/09 · 19:00', ok: false, why: 'Khách không còn gói tập từ ngày này' },
  ];
  const OTHER_PTS = [
    { name: 'Phạm Thu Trang', spec: 'Giảm mỡ · Thể hình nữ', free: true },
    { name: 'Đỗ Minh Khoa', spec: 'Tăng cơ · Powerlifting', free: true },
    { name: 'Vũ Hải Yến', spec: 'Yoga & Phục hồi', free: false },
  ];
  const GUARDIAN_PHONE = '0912345678';
  let swapsUsed = 1;                       // gói đã dùng 1/3 lượt đổi

  /* ---------- lọc ---------- */
  function render() {
    const q = search.value.trim().toLowerCase();
    let shown = 0;
    cards.forEach(c => {
      const ok = (!q || viKey(c.dataset.q).includes(viKey(q))) &&
                 (shift.value === 'all' || c.dataset.shift === shift.value);
      c.hidden = !ok;
      if (ok) shown++;
    });
    empty.hidden = shown > 0;
    syncPicked();
  }
  [search, shift].forEach(el => el.addEventListener('input', render));
  document.getElementById('attReset').addEventListener('click', () => {
    search.value = ''; shift.value = 'all'; render();
  });

  /* ---------- chọn nhiều buổi để đánh vắng một lần ---------- */
  const picked = () => [...document.querySelectorAll('[data-att-pick]:checked')]
    .filter(i => !i.closest('.att-card').hidden);

  function syncPicked() {
    document.querySelectorAll('[data-att-pick]').forEach(i =>
      i.closest('.att-card').classList.toggle('is-picked', i.checked));
    const n = picked().length;
    countEl.textContent = n + ' buổi';
    confirmBtn.disabled = n === 0;
  }
  document.addEventListener('change', e => {
    if (e.target.matches('[data-att-pick]')) syncPicked();
  });

  confirmBtn.addEventListener('click', () => {
    const list = picked();
    openConfirmModal({
      title: `Đánh vắng ${list.length} buổi?`,
      body: '<p><b>Các buổi này vẫn bị trừ khỏi gói và không được hoàn tiền.</b></p>'
          + '<p>Buổi đã chốt thì không sửa lại được.</p>',
      confirmLabel: 'Đánh vắng',
      onConfirm() {
        list.forEach(input => {
          const card = input.closest('.att-card');
          card.classList.remove('is-picked');
          card.classList.add('is-done');
          const badge = card.querySelector('.badge');
          badge.className = 'badge badge-danger';
          badge.textContent = 'Vắng';
          card.querySelector('.att-foot')?.remove();
          input.remove();
        });
        syncPicked();
        showToast('success', `Đã đánh vắng ${list.length} buổi.`);
      },
    });
  });

  /* ---------- "Không dạy được": bảng 4 lựa chọn ---------- */
  document.addEventListener('click', e => {
    const btn = e.target.closest('[data-cannot-teach]');
    if (!btn) return;
    const card = btn.closest('.att-card');
    const ctx = {
      card,
      when: btn.dataset.when,
      who: btn.dataset.who,
      dependent: btn.hasAttribute('data-dependent'),
    };

    openConfirmModal({
      title: `Buổi ${ctx.when} · ${ctx.who}`,
      confirmLabel: 'Đóng',
      danger: false,
      body: `<p class="muted">Chọn cách xử lý buổi này.</p>
        <div class="opt-list">
          <button class="opt-item" type="button" data-opt="move">
            <i class="fa-solid fa-calendar-day"></i>
            <span><b>Dời buổi</b><small>Chuyển sang slot rảnh khác của bạn, không trừ buổi và không tính lượt đổi của khách</small></span>
          </button>
          <button class="opt-item" type="button" data-opt="transfer">
            <i class="fa-solid fa-user-pen"></i>
            <span><b>Chuyển PT</b><small>Giao buổi cho PT còn rảnh khung giờ này, hoa hồng chuyển theo</small></span>
          </button>
          <button class="opt-item" type="button" data-opt="cancel">
            <i class="fa-solid fa-ban"></i>
            <span><b>Hủy buổi</b><small>Vẫn trừ buổi của khách, bạn tự hoàn tiền mặt ngoài hệ thống</small></span>
          </button>
          <button class="opt-item" type="button" data-opt="guardian"${ctx.dependent ? '' : ' disabled title="Chỉ dùng cho buổi của hồ sơ phụ thuộc"'}>
            <i class="fa-solid fa-user-shield"></i>
            <span><b>Đổi lịch theo yêu cầu khách</b><small>Dành cho hồ sơ phụ thuộc; có tính vào 3 lượt đổi của gói</small></span>
          </button>
        </div>`,
      onConfirm() {},
    });

    document.getElementById('modalRoot').addEventListener('click', ev => {
      const opt = ev.target.closest('[data-opt]');
      if (!opt || opt.disabled) return;
      document.getElementById('modalRoot').innerHTML = '';
      ({ move: moveSession, transfer: transferPt, cancel: cancelSession, guardian: guardianSwap })[opt.dataset.opt](ctx);
    }, { once: true });
  });

  /* ---------- slot picker dùng chung cho Dời và Đổi theo yêu cầu ---------- */
  function slotPicker(id) {
    return `<div class="slot-grid" id="${id}" style="margin-top:var(--space-3)">
${MY_SLOTS.map((s, i) => `      <button class="slot" type="button" aria-pressed="false" data-i="${i}"${s.ok ? '' : ` disabled title="${s.why}"`}>${s.label}</button>`).join('\n')}
    </div>`;
  }
  function bindSlotPick(id) {
    let chosen = null;
    document.getElementById(id).addEventListener('click', e => {
      const b = e.target.closest('.slot');
      if (!b || b.disabled) return;
      document.querySelectorAll(`#${id} .slot`).forEach(s => s.setAttribute('aria-pressed', 'false'));
      b.setAttribute('aria-pressed', 'true');
      chosen = MY_SLOTS[Number(b.dataset.i)].label;
    });
    return () => chosen;
  }

  function markMoved(card, label) {
    card.querySelector('.att-time').textContent = label;
    card.classList.add('is-done');
    card.querySelector('.att-foot')?.remove();
    card.querySelector('[data-att-pick]')?.remove();
    syncPicked();
  }

  /* --- Dời buổi: vẫn Scheduled, không trừ buổi, không tính lượt đổi --- */
  function moveSession(ctx) {
    openConfirmModal({
      title: 'Dời buổi sang slot khác',
      confirmLabel: 'Dời buổi',
      danger: false,
      body: `<p class="muted">Chỉ hiện slot rảnh của bạn. Ngày khách không còn gói tập hiện mờ, không chọn được.</p>
        ${slotPicker('moveGrid')}`,
      onConfirm() {
        const slot = getMove();
        if (!slot) { showToast('warning', 'Chưa chọn slot đích.'); return false; }
        markMoved(ctx.card, slot);
        showToast('success', `Đã chuyển buổi sang ${slot}. Đã thông báo cho khách.`);
      },
    });
    var getMove = bindSlotPick('moveGrid');
  }

  /* --- Chuyển PT: hoa hồng theo PT mới --- */
  function transferPt(ctx) {
    openConfirmModal({
      title: 'Chuyển buổi cho PT khác',
      confirmLabel: 'Chuyển buổi',
      danger: false,
      body: `<p class="muted">Khách là người chọn PT mới — hỏi khách trước khi chuyển. Hoa hồng buổi này thuộc về PT mới.</p>
        <div class="opt-list">
${OTHER_PTS.map((p, i) => `          <label class="opt-item"${p.free ? '' : ' style="opacity:.5"'}>
            <input type="radio" name="newPt" value="${p.name}"${p.free ? '' : ' disabled'}>
            <span><b>${p.name}</b><small>${p.spec}${p.free ? '' : ' · bận khung giờ này'}</small></span>
          </label>`).join('\n')}
        </div>`,
      onConfirm() {
        const chosen = document.querySelector('input[name="newPt"]:checked');
        if (!chosen) { showToast('warning', 'Chưa chọn PT nhận buổi.'); return false; }
        ctx.card.classList.add('is-done');
        ctx.card.querySelector('.att-foot')?.remove();
        ctx.card.querySelector('[data-att-pick]')?.remove();
        syncPicked();
        const name = chosen.value;
        showToast('success', `Đã chuyển buổi cho PT ${name}. Hoa hồng buổi này thuộc về PT mới.`);
      },
    });
  }

  /* --- Hủy buổi: vẫn trừ buổi, PT vẫn có hoa hồng, không sinh giao dịch hoàn --- */
  function cancelSession(ctx) {
    openConfirmModal({
      title: `Hủy buổi ${ctx.when}?`,
      confirmLabel: 'Hủy buổi',
      body: `<div class="alert-banner warn" style="margin-bottom:var(--space-4)">
          <i class="fa-solid fa-triangle-exclamation"></i>
          <span>Hủy buổi vẫn trừ 1 buổi của khách và bạn vẫn nhận hoa hồng. Tiền hoàn cho khách do bạn tự trả ngoài hệ thống.</span>
        </div>
        <label class="field">
          <span class="field-label">Lý do hủy</span>
          <textarea id="cancelReason" rows="3" placeholder="Nêu rõ lý do"></textarea>
          <span class="field-error" id="cancelReasonErr" hidden></span>
        </label>
        <label class="pay-option" style="border-radius:var(--radius-md)">
          <input type="checkbox" id="cashAck">
          <span><b>Khách đã đồng ý nhận tiền mặt</b></span>
        </label>`,
      onConfirm() {
        const reason = document.getElementById('cancelReason');
        if (!reason.value.trim()) {
          fieldError(reason, 'cancelReasonErr', 'Vui lòng nhập lý do hủy buổi.');
          return false;
        }
        if (!document.getElementById('cashAck').checked) {
          showToast('warning', 'Bạn phải xác nhận khách đã đồng ý nhận tiền mặt trước khi hủy.');
          return false;
        }
        ctx.card.classList.add('is-done');
        const badge = ctx.card.querySelector('.badge');
        badge.className = 'badge badge-muted';
        badge.textContent = 'Đã hủy';
        ctx.card.querySelector('.att-foot')?.remove();
        ctx.card.querySelector('[data-att-pick]')?.remove();
        syncPicked();
        showToast('success', 'Đã hủy buổi. Buổi vẫn bị trừ khỏi gói của khách.');
      },
    });

  }

  /* --- Đổi lịch theo yêu cầu khách: chỉ hồ sơ phụ thuộc, CÓ tính lượt đổi --- */
  function guardianSwap(ctx) {
    if (swapsUsed >= 3) return showToast('warning', 'Gói đã dùng hết 3 lần đổi lịch.');
    openConfirmModal({
      title: 'Đổi lịch theo yêu cầu khách',
      confirmLabel: 'Xác nhận đổi',
      danger: false,
      body: `<div class="alert-banner info" style="margin-bottom:var(--space-4)">
          <i class="fa-solid fa-circle-info"></i>
          <span>Gói này đã đổi lịch <b>${swapsUsed}/3</b> lần. Thao tác này dùng thêm 1 lần.</span>
        </div>
        <label class="field">
          <span class="field-label">Số điện thoại người giám hộ</span>
          <input id="guardPhone" type="tel" inputmode="numeric" placeholder="09xx xxx xxx">
          <span class="field-error" id="guardPhoneErr" hidden></span>
        </label>
        <p class="muted">PT chọn được bất kỳ ngày nào trong slot rảnh của mình, không giới hạn cuối tuần.</p>
        ${slotPicker('guardGrid')}`,
      onConfirm() {
        const phone = document.getElementById('guardPhone');
        if (phone.value.trim() !== GUARDIAN_PHONE) {
          fieldError(phone, 'guardPhoneErr', 'Số điện thoại người giám hộ không khớp hồ sơ.');
          return false;
        }
        const slot = getGuard();
        if (!slot) { showToast('warning', 'Chưa chọn slot đích.'); return false; }
        swapsUsed++;
        markMoved(ctx.card, slot);
        showToast('success', `Đã chuyển buổi sang ${slot}.`);
        setTimeout(() => showToast('info',
          'Hồ sơ phụ thuộc không nhận thông báo trong hệ thống. Vui lòng báo trực tiếp người giám hộ.'), 600);
      },
    });
    var getGuard = bindSlotPick('guardGrid');
  }

  render();
})();
