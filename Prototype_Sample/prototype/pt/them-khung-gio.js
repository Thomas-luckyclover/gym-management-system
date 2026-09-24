/* 9.1.2 — Khai báo khung giờ: đúng 90 phút, trong giờ hoạt động, không trùng. */
(function () {
  const SLOT_MINUTES = 90;
  /* khung đã công bố, để chặn trùng (thứ trong tuần → danh sách giờ bắt đầu) */
  const PUBLISHED = {
    '2026-09-28': ['06:00', '19:00'],
    '2026-09-29': ['08:00'],
  };
  const OPEN = { weekday: ['06:00', '22:00'], weekend: ['05:00', '22:00'] };

  const dateEl = document.getElementById('slotDate');
  const startEl = document.getElementById('slotStart');
  const endEl = document.getElementById('slotEnd');

  const toMin = t => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));
  const toStr = m => String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');

  /* giờ kết thúc luôn bám theo giờ bắt đầu */
  function syncEnd() {
    if (!startEl.value) return;
    endEl.value = toStr(toMin(startEl.value) + SLOT_MINUTES);
  }
  startEl.addEventListener('input', syncEnd);
  syncEnd();

  document.getElementById('slotForm').addEventListener('submit', e => {
    e.preventDefault();
    const date = dateEl.value;
    if (!date) return fieldError(dateEl, 'slotDateErr', 'Vui lòng chọn ngày.');
    const d = new Date(date + 'T00:00:00');
    const today = new Date(); today.setHours(0, 0, 0, 0);
    if (d < today) return fieldError(dateEl, 'slotDateErr', 'Chỉ khai báo được cho ngày trong tương lai.');

    if (!startEl.value) return fieldError(startEl, 'slotStartErr', 'Vui lòng nhập giờ bắt đầu.');
    const start = toMin(startEl.value);
    const end = toMin(endEl.value);

    if (end - start !== SLOT_MINUTES) {
      return fieldError(endEl, 'slotEndErr', 'Khung giờ phải dài đúng 90 phút.');
    }

    const weekend = [0, 6].includes(d.getDay());
    const [openAt, closeAt] = weekend ? OPEN.weekend : OPEN.weekday;
    if (start < toMin(openAt) || end > toMin(closeAt)) {
      return fieldError(startEl, 'slotStartErr',
        `Giờ đã chọn nằm ngoài giờ hoạt động ngày này (${openAt}–${closeAt}).`);
    }

    const clash = (PUBLISHED[date] || []).find(s => {
      const a = toMin(s);
      return start < a + SLOT_MINUTES && a < end;
    });
    if (clash) {
      return fieldError(startEl, 'slotStartErr',
        `Khung giờ này trùng với khung đã khai báo (${clash}–${toStr(toMin(clash) + SLOT_MINUTES)}).`);
    }

    (PUBLISHED[date] ||= []).push(startEl.value);
    showToast('success', `Đã lưu khung giờ ${startEl.value}–${endEl.value}.`);
    setTimeout(() => { location.href = 'lich-ranh.html'; }, 900);
  });
})();
