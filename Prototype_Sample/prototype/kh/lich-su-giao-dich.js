/* 7.4.2 — Lịch sử giao dịch: tìm kiếm, lọc, phân trang. Màn chỉ đọc. */
(function () {
  const search = document.getElementById('txSearch');
  const period = document.getElementById('txPeriod');
  const status = document.getElementById('txStatus');
  const TODAY = new Date(2026, 8, 20);              // mốc dữ liệu mẫu
  const parse = s => { const [d, m, y] = s.split('/').map(Number); return new Date(y, m - 1, d); };

  const list = makeList({
    listSel: '#txList',
    perPage: 5,
    unit: 'đơn',
    matcher(row) {
      const q = search.value.trim().toLowerCase();
      if (q && !viKey(row.dataset.q).includes(viKey(q))) return false;
      if (status.value !== 'all' && row.dataset.status !== status.value) return false;

      const d = parse(row.dataset.date);
      if (period.value === 'all') return true;
      if (period.value.length === 4) return d.getFullYear() === Number(period.value);
      return (TODAY - d) / 86400000 <= Number(period.value);
    },
    onRender() {
      // dòng bị ẩn thì thu gọn luôn panel chi tiết của nó
      document.querySelectorAll('.data-row[hidden] .row-detail').forEach(p => { p.hidden = true; });
      document.querySelectorAll('.data-row[hidden] [data-toggle-detail]').forEach(b => {
        b.setAttribute('aria-expanded', 'false');
        b.textContent = 'Chi tiết';
      });
    },
  });

  [search, period, status].forEach(el => el.addEventListener('input', list.reset));
  document.getElementById('txReset').addEventListener('click', () => {
    search.value = ''; period.value = 'all'; status.value = 'all';
    list.reset();
  });

  document.addEventListener('click', e => {
    const btn = e.target.closest('[data-toggle-detail]');
    if (!btn) return;
    const panel = btn.closest('.data-row').querySelector('.row-detail');
    const open = panel.hidden;
    panel.hidden = !open;
    btn.setAttribute('aria-expanded', String(open));
    btn.textContent = open ? 'Thu gọn' : 'Chi tiết';
  });

  list.render();
})();
