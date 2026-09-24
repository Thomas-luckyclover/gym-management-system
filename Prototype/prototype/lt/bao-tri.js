/* 8.2.1 — Yêu cầu bảo trì: gửi mới, theo dõi, và đóng yêu cầu sau khi Quản lý duyệt. */
(function () {
  /* ---------- gửi yêu cầu ---------- */
  const form = document.getElementById('mtForm');
  const eq = document.getElementById('mtEq');
  const pri = document.getElementById('mtPri');
  const desc = document.getElementById('mtDesc');

  /* mở từ nút "Báo hỏng" ở danh mục thì chọn sẵn thiết bị */
  const preset = new URLSearchParams(location.search).get('tb');
  if (preset && [...eq.options].some(o => o.value === preset)) eq.value = preset;

  form.addEventListener('submit', e => {
    e.preventDefault();
    ['mtEqErr', 'mtDescErr'].forEach(clearErr);
    let ok = true;

    if (!eq.value) { fieldError(eq, 'mtEqErr', 'Chọn thiết bị cần bảo trì.'); ok = false; }
    if (desc.value.trim().length < 10) {
      fieldError(desc, 'mtDescErr', 'Mô tả cụ thể hơn để thợ biết mang đồ gì, tối thiểu 10 ký tự.');
      ok = false;
    }
    if (!ok) return;

    const stopped = document.getElementById('mtStop').checked;
    openConfirmModal({
      title: 'Gửi yêu cầu bảo trì',
      body: `<p><b>${eq.selectedOptions[0].textContent}</b> · mức ${pri.value.toLowerCase()}</p>
             <p>${desc.value.trim()}</p>
             ${stopped ? '<p>Thiết bị đã dán cảnh báo và ngừng cho khách dùng.</p>'
                       : '<p class="muted">Thiết bị vẫn đang cho khách dùng.</p>'}`,
      confirmLabel: 'Gửi cho Quản lý',
      danger: false,
      onConfirm() {
        showToast('success', 'Đã gửi yêu cầu. Quản lý sẽ duyệt và xếp thợ.');
        form.reset();
      },
    });
  });

  /* ---------- đóng yêu cầu: chỉ Lễ tân làm, sau khi kiểm tra tại chỗ ---------- */
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-close]');
    if (!b) return;
    const row = b.closest('.data-row');

    openConfirmModal({
      title: `Đóng ${b.dataset.close}`,
      body: `<p>Chỉ đóng khi đã tự kiểm tra thiết bị tại chỗ và thấy chạy bình thường.</p>
             <label class="field"><span class="field-label">Ghi chú nghiệm thu</span>
               <textarea id="mtClose" rows="3" placeholder="Đã chạy thử 5 phút, không còn tiếng kêu…"></textarea>
               <span class="field-error" id="mtCloseErr" hidden></span></label>`,
      confirmLabel: 'Đóng yêu cầu',
      danger: false,
      onConfirm() {
        const n = document.getElementById('mtClose');
        if (n.value.trim().length < 5) {
          fieldError(n, 'mtCloseErr', 'Ghi lại đã kiểm tra thế nào, tối thiểu 5 ký tự.');
          return false;
        }
        row.dataset.st = 'done';
        row.querySelector('.badge').className = 'badge badge-ok';
        row.querySelector('.badge').textContent = 'Đã đóng';
        row.querySelectorAll('.row-meta')[1].innerHTML =
          `<span><i class="fa-solid fa-circle-check"></i> Lễ tân đóng hôm nay · ${n.value.trim()}</span>`;
        b.remove();
        list.render();
        showToast('success', `Đã đóng ${b.dataset.close}.`);
      },
    });
  });

  /* ---------- bộ lọc ---------- */
  const q = document.getElementById('mtq');
  const st = document.getElementById('mtSt');

  const list = makeList({
    listSel: '#mtList',
    perPage: 5,
    unit: 'yêu cầu',
    matcher(row) {
      return viKey(row.dataset.q).includes(viKey(q.value.trim()))
        && (st.value === 'all' || row.dataset.st === st.value);
    },
  });
  [q, st].forEach(el => el.addEventListener('input', list.reset));
  document.getElementById('mtReset').addEventListener('click', () => {
    q.value = ''; st.value = 'all';
    list.reset();
  });
  list.render();
})();
