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

  /* bước 1 báo hỏng: chọn thiết bị rồi sang bước 2 (bao-tri.html) */
  const boxes = () => [...document.querySelectorAll('[data-pick]:not(:disabled)')];
  const picked = () => boxes().filter(b => b.checked).map(b => b.dataset.pick);
  const next = document.getElementById('eqNext');
  function syncPick() {
    const p = picked();
    document.getElementById('eqPicked').textContent = p.length ? `Đã chọn ${p.length} thiết bị: ${p.join(', ')}` : 'Chưa chọn thiết bị nào.';
    next.disabled = !p.length;
  }
  document.addEventListener('change', e => { if (e.target.matches('[data-pick]')) syncPick(); });
  document.getElementById('eqAll').addEventListener('click', () => { boxes().forEach(b => { b.checked = true; }); syncPick(); });
  document.getElementById('eqNone').addEventListener('click', () => { boxes().forEach(b => { b.checked = false; }); syncPick(); });
  next.addEventListener('click', () => { location.href = 'bao-tri.html?tb=' + picked().join(','); });
})();
