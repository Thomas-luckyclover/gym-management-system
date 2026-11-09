/* #28 — Hồ sơ hội viên: tìm kiếm, lọc theo trạng thái hồ sơ và tình trạng gói, phân trang,
   nút Xóa mở hộp "Xóa hồ sơ hội viên" (lý do ≥ 5 ký tự) gửi yêu cầu cho Quản lý duyệt. */
(function () {
  const q = document.getElementById('clq');
  const st = document.getElementById('clSt');
  const mem = document.getElementById('clMem');
  const profileToolbar = document.getElementById('profileToolbar');
  const clList = document.getElementById('clList');
  const reqList = document.getElementById('reqList');
  const reqBadge = document.getElementById('reqBadge');
  const reqBody = document.getElementById('reqBody');
  const custTabs = document.getElementById('custTabs');

  let currentTab = 'profiles';
  let pendingCount = 2;

  custTabs.addEventListener('click', e => {
    const btn = e.target.closest('button[data-tab]');
    if (!btn) return;
    custTabs.querySelectorAll('button').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    currentTab = btn.dataset.tab;
    if (currentTab === 'profiles') {
      profileToolbar.hidden = false;
      clList.hidden = false;
      reqList.hidden = true;
      list.render();
    } else {
      profileToolbar.hidden = true;
      clList.hidden = true;
      reqList.hidden = false;
      pager.hidden = true;
    }
  });

  /* Bảng có hàng <tr class="mem-row">: lọc + phân trang 10 dòng (GBR-18) bằng chính pager của màn. */
  const rows = [...document.querySelectorAll('#clList .mem-row')];
  const empty = document.querySelector('#clList [data-empty]');
  const table = document.querySelector('#clList table');
  const pager = document.querySelector('[data-pager]');
  const PER_PAGE = 10;
  let page = 1;

  const match = row => viKey(row.dataset.q).includes(viKey(q.value.trim()))
    && (st.value === 'all' || row.dataset.st === st.value)
    && (mem.value === 'all' || row.dataset.mem === mem.value);

  function render() {
    const found = rows.filter(match);
    const pages = Math.max(1, Math.ceil(found.length / PER_PAGE));
    if (page > pages) page = pages;
    const from = (page - 1) * PER_PAGE;
    const shown = found.slice(from, from + PER_PAGE);
    rows.forEach(r => { r.hidden = !shown.includes(r); });
    table.hidden = found.length === 0;
    empty.hidden = found.length > 0;
    pager.hidden = found.length === 0;
    if (!found.length) return;
    pager.querySelector('.pager-info').innerHTML = `<b>${from + 1}–${from + shown.length}</b> trên ${found.length} hồ sơ`;
    const btns = pager.querySelector('.pager-btns');
    btns.innerHTML = '';
    const mk = (label, target, opts = {}) => {
      const b = document.createElement('button');
      b.type = 'button'; b.className = 'pg'; b.innerHTML = label; b.disabled = !!opts.disabled;
      if (opts.current) b.setAttribute('aria-current', 'page');
      else b.addEventListener('click', () => { page = target; render(); });
      btns.append(b);
    };
    mk('<i class="fa-solid fa-chevron-left"></i>', page - 1, { disabled: page <= 1 });
    for (let i = 1; i <= pages; i++) mk(String(i), i, { current: i === page });
    mk('<i class="fa-solid fa-chevron-right"></i>', page + 1, { disabled: page >= pages });
  }
  const list = { render, reset() { page = 1; render(); } };

  [q, st, mem].forEach(el => el.addEventListener('input', list.reset));
  document.getElementById('clReset').addEventListener('click', () => {
    q.value = ''; st.value = 'all'; mem.value = 'all';
    list.reset();
  });

  document.addEventListener('click', e => {
    const b = e.target.closest('[data-delete-open]');
    if (!b) return;
    const row = b.closest('tr');
    openConfirmModal({
      title: 'Xóa hồ sơ hội viên',
      body: `<p><b>${b.dataset.name}</b> · ${b.dataset.code}</p>
             <p class="muted">Lễ tân gửi yêu cầu kèm lý do. Quản lý duyệt thì hồ sơ mới chuyển Ngừng hoạt động.</p>
             <label class="field"><span class="field-label">Lý do xóa *</span>
               <textarea id="delReason" rows="3" maxlength="500"></textarea>
               <span class="field-error" id="delReasonErr" hidden></span></label>`,
      confirmLabel: 'Xác nhận',
      onConfirm() {
        const r = document.getElementById('delReason');
        if (r.value.trim().length < 5) {
          fieldError(r, 'delReasonErr', 'Nêu lý do cụ thể, tối thiểu 5 ký tự.');
          return false;
        }
        const reasonVal = r.value.trim();
        row.querySelector('[data-label="Trạng thái"]').insertAdjacentHTML('beforeend', ' <span class="badge badge-danger">Chờ duyệt xoá</span>');
        b.outerHTML = '<a class="btn btn-primary btn-sm" href="ho-so-khach.html">Xem yêu cầu</a>';
        
        pendingCount++;
        reqBadge.textContent = pendingCount;
        
        const now = new Date();
        const dateStr = `${String(now.getDate()).padStart(2, '0')}/${String(now.getMonth() + 1).padStart(2, '0')}/${now.getFullYear()} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
        const newReqHtml = `
          <tr class="req-row">
            <td data-label="Hội viên"><b>${b.dataset.name}</b><br><span class="muted tabular">${b.dataset.code} · Yêu cầu #043</span></td>
            <td data-label="Gửi lúc" class="tabular">${dateStr}</td>
            <td data-label="Lý do">${reasonVal}</td>
            <td data-label="Kết quả"><span class="badge badge-warn">Chờ duyệt</span></td>
          </tr>
        `;
        reqBody.insertAdjacentHTML('afterbegin', newReqHtml);

        showToast('success', 'Đã gửi yêu cầu xóa hồ sơ cho Quản lý.');
      },
    });
  });

  list.render();
})();
