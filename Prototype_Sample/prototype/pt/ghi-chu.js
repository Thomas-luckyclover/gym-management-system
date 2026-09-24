/* 9.6.1 — Ghi chú riêng: không có bước publish, lưu là hiện ngay ở lịch sử. */
(function () {
  const form = document.getElementById('noteForm');
  const text = document.getElementById('noteText');
  const list = document.getElementById('noteList');
  const count = document.getElementById('noteCount');

  const today = new Date();
  const dmy = d => [d.getDate(), d.getMonth() + 1].map(n => String(n).padStart(2, '0')).join('/') + '/' + d.getFullYear();

  form.addEventListener('submit', e => {
    e.preventDefault();
    const v = text.value.trim();
    if (!v) return fieldError(text, 'noteTextErr', 'Nội dung ghi chú không được để trống.');

    const row = document.createElement('div');
    row.className = 'data-row';
    row.innerHTML = `<div class="row-main">
        <div class="row-title">${dmy(today)}</div>
        <p class="muted" style="margin-top:var(--space-2)"></p>
      </div>`;
    row.querySelector('p').textContent = v;
    list.prepend(row);

    text.value = '';
    count.textContent = list.querySelectorAll('.data-row').length + ' ghi chú';
    showToast('success', 'Đã lưu ghi chú. Chỉ bạn nhìn thấy nội dung này.');
  });
})();
