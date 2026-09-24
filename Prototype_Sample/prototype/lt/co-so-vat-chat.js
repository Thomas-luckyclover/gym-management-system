/* 8.1.3 — Danh mục cơ sở vật chất: chỉ đọc, có tìm kiếm + lọc + phân trang. */
(function () {
  const q = document.getElementById('eqq');
  const zone = document.getElementById('eqZone');
  const st = document.getElementById('eqSt');

  const list = makeList({
    listSel: '#eqList',
    perPage: 6,
    unit: 'thiết bị',
    matcher(row) {
      return viKey(row.dataset.q).includes(viKey(q.value.trim()))
        && (zone.value === 'all' || row.dataset.zone === zone.value)
        && (st.value === 'all' || row.dataset.st === st.value);
    },
  });

  [q, zone, st].forEach(el => el.addEventListener('input', list.reset));
  document.getElementById('eqReset').addEventListener('click', () => {
    q.value = ''; zone.value = 'all'; st.value = 'all';
    list.reset();
  });

  list.render();
})();
