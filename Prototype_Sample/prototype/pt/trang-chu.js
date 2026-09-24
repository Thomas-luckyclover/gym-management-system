/* =====================================================================
   9.5.1 — PT Dashboard
   Hoa hồng đổi theo kỳ đã chọn; danh sách buổi sắp tới lọc được theo
   khoảng thời gian, loại gói và tên khách.
   ===================================================================== */
(function () {
  /* ---------- kỳ hoa hồng ---------- */
  const period = document.getElementById('periodPick');
  const total = document.querySelector('[data-period-total]');
  const note = document.querySelector('[data-period-note]');

  /* ghi chú của từng kỳ, lấy từ chính bảng đang có trong HTML */
  const NOTES = {
    '2026-09': 'Tính tới hết 20/09',
    '2026-08': 'Đã chốt',
    '2026-07': 'Đã chốt',
    q3: 'Tháng 07 – 09',
  };
  const num = t => Number(t.replace(/[^0-9]/g, ''));
  const vnd = n => n.toLocaleString('vi-VN') + 'đ';

  function renderPeriod() {
    const id = period.value;
    let sum = 0;
    let sessions = 0;

    document.querySelectorAll('[data-period]').forEach(body => {
      const on = body.dataset.period === id;
      body.hidden = !on;
      if (!on) return;
      body.querySelectorAll('tr').forEach(tr => {
        const cells = tr.querySelectorAll('td');
        sessions += Number(cells[1].textContent.trim());
        sum += num(cells[3].textContent);
      });
    });

    total.textContent = vnd(sum);
    note.textContent = `${NOTES[id]} · ${sessions} buổi đã dạy`;
  }
  period.addEventListener('change', renderPeriod);
  renderPeriod();

  /* ---------- buổi sắp tới ---------- */
  const rows = [...document.querySelectorAll('#upList .data-row')];
  const empty = document.querySelector('#upList [data-empty]');
  const search = document.getElementById('upSearch');
  const range = document.getElementById('upRange');
  const pkg = document.getElementById('upPkg');

  function renderUpcoming() {
    const q = search.value.trim().toLowerCase();
    const within = Number(range.value);
    let shown = 0;

    rows.forEach(r => {
      const ok = (!q || viKey(r.dataset.q).includes(viKey(q))) &&
                 Number(r.dataset.in) < within &&
                 (pkg.value === 'all' || r.dataset.pkg === pkg.value);
      r.hidden = !ok;
      if (ok) shown++;
    });
    empty.hidden = shown > 0;
  }

  [search, range, pkg].forEach(el => el.addEventListener('input', renderUpcoming));
  document.getElementById('upReset').addEventListener('click', () => {
    search.value = ''; range.value = '7'; pkg.value = 'all';
    renderUpcoming();
  });
  renderUpcoming();
})();
