/* 6.1.1 — Hồ sơ khách: tìm kiếm, lọc theo trạng thái và tình trạng gói, phân trang. */
(function () {
  const q = document.getElementById('clq');
  const st = document.getElementById('clSt');
  const mem = document.getElementById('clMem');

  const list = makeList({
    listSel: '#clList',
    perPage: 6,
    unit: 'hồ sơ',
    matcher(row) {
      return viKey(row.dataset.q).includes(viKey(q.value.trim()))
        && (st.value === 'all' || row.dataset.st === st.value)
        && (mem.value === 'all' || row.dataset.mem === mem.value);
    },
  });

  [q, st, mem].forEach(el => el.addEventListener('input', list.reset));
  document.getElementById('clReset').addEventListener('click', () => {
    q.value = ''; st.value = 'all'; mem.value = 'all';
    list.reset();
  });

  list.render();
})();
