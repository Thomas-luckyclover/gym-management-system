/* 9.6.1 — Ghi chú riêng: không có bước publish, lưu là hiện ngay ở lịch sử. */
(function () {
  const CLIENTS = {"KH-0241": "Nguyễn Minh An", "KH-0318": "Trần Gia Bảo", "KH-1009": "Trịnh Quốc Bảo", "KH-0276": "Lê Thu Hà", "KH-0402": "Phạm Quốc Huy", "KH-0299": "Bùi Khánh Linh", "KH-1005": "Vũ Hoài Nam", "KH-1011": "Đỗ Lan Chi", "KH-0433": "Hoàng Đức Duy"};
  const pick = document.getElementById('clientPick');
  pick.addEventListener('change', () => {
    document.getElementById('clName').textContent = CLIENTS[pick.value];
    document.getElementById('clCode').textContent = pick.value;
  });
  const form = document.getElementById('noteForm');
  const text = document.getElementById('noteText');
  const list = document.getElementById('noteList');
  const count = document.getElementById('noteCount');

  const today = sampleNow();
  const dmy = d => [d.getDate(), d.getMonth() + 1].map(n => String(n).padStart(2, '0')).join('/') + '/' + d.getFullYear();

  form.addEventListener('submit', e => {
    e.preventDefault();
    const v = text.value.trim();
    if (!v || v.length > 2000) return fieldError(text, 'noteTextErr', 'Nội dung ghi chú phải từ 1 đến 2000 ký tự.');

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
