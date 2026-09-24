/* 9.5.2 — Khách của tôi: tìm, lọc trạng thái gói và loại gói PT, phân trang. */
(function () {
  const search = document.getElementById('cSearch');
  const state = document.getElementById('cState');
  const pkg = document.getElementById('cPkg');

  const list = makeList({
    listSel: '#clientList',
    perPage: 6,
    unit: 'khách',
    matcher(row) {
      const q = search.value.trim().toLowerCase();
      if (q && !viKey(row.dataset.q).includes(viKey(q))) return false;
      if (state.value !== 'all' && row.dataset.state !== state.value) return false;
      return pkg.value === 'all' || row.dataset.pkg === pkg.value;
    },
  });

  [search, state, pkg].forEach(el => el.addEventListener('input', list.reset));
  document.getElementById('cReset').addEventListener('click', () => {
    search.value = ''; state.value = 'all'; pkg.value = 'all'; list.reset();
  });
  list.render();
})();
