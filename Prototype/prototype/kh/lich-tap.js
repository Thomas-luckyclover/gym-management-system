/* 9.3.2 / 9.7.1 — lọc + phân trang buổi tập, và 9.3.4 modal hủy buổi. */
(function () {
  const search = document.getElementById('sSearch');
  const state = document.getElementById('sState');
  const kind = document.getElementById('sKind');      // chỉ có ở 9.3.2

  const list = makeList({
    listSel: '[data-session-list]',
    perPage: 6,
    unit: 'buổi',
    matcher(row) {
      const q = search.value.trim().toLowerCase();
      if (q && !viKey(row.dataset.q).includes(viKey(q))) return false;
      if (state.value !== 'all' && row.dataset.state !== state.value) return false;
      if (kind && kind.value !== 'all' && row.dataset.kind !== kind.value) return false;
      return true;
    },
  });
  if (!list) return;

  [search, state, kind].filter(Boolean).forEach(el => el.addEventListener('input', list.reset));
  document.getElementById('sReset').addEventListener('click', () => {
    search.value = ''; state.value = 'all';
    if (kind) kind.value = 'all';
    list.reset();
  });
  list.render();

  /* 9.3.4 — hủy lúc nào cũng được, nhưng buổi luôn bị trừ và không hoàn tiền */
  document.addEventListener('click', e => {
    const btn = e.target.closest('[data-cancel-session]');
    if (!btn) return;
    openConfirmModal({
      title: `Hủy buổi ${btn.dataset.when}?`,
      body: '<p><b>Buổi này vẫn bị trừ khỏi gói và không được hoàn tiền.</b></p><p>Trạng thái sau khi hủy không sửa lại được.</p>',
      confirmLabel: 'Hủy buổi',
      onConfirm() {
        const row = btn.closest('.data-row');
        row.dataset.state = 'cancelled';
        const badge = row.querySelector('.badge');
        badge.className = 'badge badge-muted';
        badge.textContent = 'Đã hủy';
        row.querySelector('.row-actions')?.remove();
        showToast('success', 'Đã hủy buổi tập.');
        list.render();
      },
    });
  });
})();
