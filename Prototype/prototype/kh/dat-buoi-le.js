/* 9.3.1 — Đặt buổi lẻ
   Mỗi PT có lịch bận riêng, nên lưới giờ vẽ lại theo PT đang chọn.
   Bộ lọc khung giờ lọc luôn cả danh sách PT: chỉ còn PT rảnh khung đó. */
(function () {
  /* slot đã kín của từng PT, theo thứ trong tuần (0 = Thứ 2) */
  const BUSY = {
    'Hoàng Long':     { 0: ['08:00', '19:00'], 2: ['19:00'], 4: ['08:00', '10:00'], 5: ['06:00'] },
    'Phạm Thu Trang': { 1: ['06:00', '18:00'], 3: ['14:00'], 5: ['16:00', '18:00'], 6: ['19:00'] },
    'Đỗ Minh Khoa':   { 0: ['06:00', '14:00'], 2: ['18:00', '20:00'], 4: ['16:00'], 6: ['19:00'] },
    'Vũ Hải Yến':     { 1: ['10:00'], 3: ['20:00'], 5: ['06:00', '08:00'], 6: ['08:00', '14:00'] },
  };
  const BANDS = { sang: ['06:00', '08:00', '10:00'], chieu: ['14:00', '16:00'], toi: ['18:00', '19:00', '20:00'] };

  const search = document.getElementById('ptSearch');
  const spec = document.getElementById('ptSpec');
  const band = document.getElementById('slotBand');
  const count = document.getElementById('ptCount');
  const ptEmpty = document.getElementById('ptEmpty');
  const options = [...document.querySelectorAll('.pt-option')];
  const bookBtn = document.getElementById('bookBtn');

  let picked = null;
  const ptOf = () => document.querySelector('input[name="pt"]:checked').value;

  /* ngày nào chọn được — dùng chung quy tắc tuần của initSlotGrid */
  const week = initSlotGrid({
    onPick({ text }) {
      picked = text;
      document.querySelector('[data-summary-slot]').textContent = text;
      bookBtn.disabled = false;
      bookBtn.textContent = 'Thanh toán 550.000đ';
    },
  });

  /* PT này còn slot nào trống trong khung giờ đang lọc không */
  function hasFreeSlot(pt) {
    const times = band.value === 'all'
      ? Object.values(BANDS).flat()
      : BANDS[band.value];
    return week.days.some(d => !d.blocked && times.some(t => !(BUSY[pt]?.[d.i] || []).includes(t)));
  }

  /* vẽ lại lưới theo PT đang chọn + khung giờ đang lọc */
  function paintGrid() {
    const pt = ptOf();
    document.querySelector('[data-grid-pt]').textContent = pt;
    picked = null;
    document.querySelector('[data-summary-slot]').textContent = 'Chưa chọn';
    bookBtn.disabled = true;
    bookBtn.textContent = 'Chọn khung giờ';

    document.querySelectorAll('.slot').forEach(s => {
      const day = week.days[Number(s.dataset.day)];
      const busy = (BUSY[pt]?.[day.i] || []).includes(s.dataset.time);
      s.setAttribute('aria-pressed', 'false');
      s.hidden = band.value !== 'all' && s.dataset.band !== band.value;
      s.disabled = day.blocked || busy;
      s.title = day.blocked ? day.reason : busy ? pt + ' đã kín giờ này' : '';
    });
  }

  /* lọc danh sách PT */
  function renderPts() {
    const q = search.value.trim().toLowerCase();
    let shown = 0;
    options.forEach(o => {
      const pt = o.querySelector('input').value;
      const ok = (!q || viKey(o.dataset.q).includes(viKey(q))) &&
                 (spec.value === 'all' || o.dataset.spec === spec.value) &&
                 hasFreeSlot(pt);
      o.hidden = !ok;
      if (ok) shown++;
    });
    ptEmpty.hidden = shown > 0;
    count.textContent = shown ? shown + ' PT phù hợp' : '';

    // PT đang chọn bị lọc mất thì nhảy sang PT đầu tiên còn hiện
    const current = document.querySelector('input[name="pt"]:checked');
    if (current.closest('.pt-option').hidden) {
      const next = options.find(o => !o.hidden)?.querySelector('input');
      if (next) next.checked = true;
    }
    paintGrid();
  }

  [search, spec, band].forEach(el => el.addEventListener('input', renderPts));
  document.querySelectorAll('input[name="pt"]').forEach(r => r.addEventListener('change', () => {
    document.querySelector('[data-summary-pt]').textContent = r.value;
    paintGrid();
  }));
  document.getElementById('ptReset').addEventListener('click', () => {
    search.value = ''; spec.value = 'all'; band.value = 'all';
    renderPts();
  });

  /* Slot chỉ được giữ SAU KHI thanh toán thành công — hủy giữa chừng
     thì slot nhả lại cho người khác. */
  bookBtn.addEventListener('click', () => {
    openGateway({
      order: 'DH-2026-1205',
      amount: '550.000đ',
      onDone(result) {
        if (result !== 'success') {
          showToast('warning', 'Chưa thanh toán nên slot chưa được giữ.');
          return;
        }
        document.querySelector('[data-done-slot]').textContent = picked;
        document.querySelector('[data-done-pt]').textContent = ptOf();
        showStep('done');
        showToast('success', 'Thanh toán thành công. Slot đã được giữ.');
      },
    });
  });

  renderPts();
})();
