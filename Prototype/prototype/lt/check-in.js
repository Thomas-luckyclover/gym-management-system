/* 6.3.1 — Check-in.
   Mọi thẻ kết quả đã nằm sẵn trong check-in.html; file này chỉ chọn thẻ
   nào hiện ra và ghi lượt vào nhật ký ca. */
(function () {
  const form = document.getElementById('ciForm');
  const q = document.getElementById('ciq');
  const outside = document.getElementById('ciOutside');
  const cards = [...document.querySelectorAll('.ci-card')];
  const log = document.getElementById('ciLog');

  /* vé ngày đã quét trong ca này — quét lần hai là từ chối */
  const usedToday = new Set();

  const card = key => cards.find(c => c.dataset.ci === key);

  function show(key) {
    cards.forEach(c => { c.hidden = c.dataset.ci !== key; });
    card(key)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  function lookup() {
    const raw = q.value.trim();
    if (!raw) return;

    if (outside.checked) { show('__closed'); return; }

    const key = viKey(raw);
    const hit = cards.find(c =>
      c.dataset.ci && c.dataset.ci[0] !== '_' &&
      (viKey(c.dataset.ci) === key || c.dataset.phone === raw));

    if (!hit) { show('__none'); return; }
    if (usedToday.has(hit.dataset.ci)) { show('__used'); return; }
    show(hit.dataset.ci);
  }

  form.addEventListener('submit', e => { e.preventDefault(); lookup(); });

  document.querySelectorAll('[data-try]').forEach(b =>
    b.addEventListener('click', () => {
      outside.checked = false;
      q.value = b.dataset.try;
      lookup();
    }));

  outside.addEventListener('change', () => { if (q.value.trim()) lookup(); });

  /* ---- xác nhận cho vào ---- */
  document.addEventListener('click', e => {
    const go = e.target.closest('[data-ci-go]');
    if (go) {
      const c = go.closest('.ci-card');
      const name = c.querySelector('.ci-id h2').childNodes[0].textContent.trim();
      if (c.hasAttribute('data-once')) usedToday.add(c.dataset.ci);
      logEntry(name);
      showToast('success', `${name} đã vào tập.`);
      c.hidden = true;
      q.value = '';
      q.focus();
      return;
    }

    /* thiếu ảnh: chụp tại quầy rồi mới mở nút cho vào */
    const cam = e.target.closest('[data-ci-photo]');
    if (!cam) return;
    const c = cam.closest('.ci-card');
    openConfirmModal({
      title: 'Chụp ảnh check-in',
      body: `<p>Camera quầy sẽ chụp và lưu vào hồ sơ. Ảnh chỉ dùng để đối chiếu khi khách vào tập.</p>
             <p class="muted">Đề nghị khách bỏ mũ, nhìn thẳng vào camera.</p>`,
      confirmLabel: 'Chụp và lưu',
      danger: false,
      onConfirm() {
        const box = c.querySelector('[data-ci-face]');
        box.classList.remove('is-empty');
        box.innerHTML = '<img src="https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=240&h=300&fit=crop&q=80" alt="Ảnh check-in vừa chụp">';
        const banner = c.querySelector('.alert-banner');
        banner.className = 'alert-banner ok';
        banner.innerHTML = '<i class="fa-solid fa-circle-check"></i><span><b>Đã lưu ảnh check-in.</b> Khách đủ điều kiện vào tập.</span>';
        cam.outerHTML = '<button class="btn btn-primary" type="button" data-ci-go><i class="fa-solid fa-door-open"></i> Xác nhận vào tập</button>';
        showToast('success', 'Đã lưu ảnh check-in vào hồ sơ.');
      },
    });
  });

  function logEntry(name) {
    const now = new Date();
    const row = document.createElement('div');
    row.className = 'data-row';
    row.innerHTML = `<div class="row-main"><div class="row-title">${name}</div></div>
      <b class="tabular">${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}</b>`;
    log.prepend(row);
  }

  q.focus();
})();
