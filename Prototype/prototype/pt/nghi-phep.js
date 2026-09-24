/* 5.2.1 — Nghỉ phép (PT nộp)
   Chặn khi quá giờ chốt (15:00 ngày liền trước) hoặc còn buổi đã xếp trùng ngày nghỉ. */
(function () {
  /* buổi đã xếp của PT, để đối chiếu với ngày nghỉ */
  const SCHEDULED = { '2026-09-22': 1, '2026-09-23': 1, '2026-09-25': 2, '2026-09-26': 1 };
  const CUTOFF_HOUR = 15;

  const dateEl = document.getElementById('leaveDate');
  const reasonEl = document.getElementById('leaveReason');
  const submitBtn = document.getElementById('leaveSubmit');
  const banner = document.getElementById('clashBanner');
  const clashCount = document.getElementById('clashCount');
  const infoDate = document.getElementById('infoDate');
  const infoDeadline = document.getElementById('infoDeadline');

  const p = n => String(n).padStart(2, '0');
  const dmy = d => `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;

  function refresh() {
    clearErr('leaveDateErr');
    const v = dateEl.value;
    if (!v) return;

    const day = new Date(v + 'T00:00:00');
    const cutoff = new Date(day);
    cutoff.setDate(day.getDate() - 1);
    cutoff.setHours(CUTOFF_HOUR, 0, 0, 0);

    infoDate.textContent = dmy(day);
    infoDeadline.textContent = `${CUTOFF_HOUR}:00 · ${p(cutoff.getDate())}/${p(cutoff.getMonth() + 1)}`;

    const clash = SCHEDULED[v] || 0;
    banner.hidden = clash === 0;
    clashCount.textContent = clash;

    const late = new Date() > cutoff;
    submitBtn.disabled = late || clash > 0;
    submitBtn.textContent = late ? 'Quá hạn nộp đơn' : 'Nộp đơn';
    if (late) {
      fieldError(dateEl, 'leaveDateErr',
        'Quá hạn nộp đơn cho ngày đã chọn. Nghỉ đột xuất vui lòng báo trực tiếp Quản lý.');
    }
  }

  dateEl.addEventListener('input', refresh);
  refresh();

  document.getElementById('leaveForm').addEventListener('submit', e => {
    e.preventDefault();
    if (!reasonEl.value.trim()) return fieldError(reasonEl, 'leaveReasonErr', 'Lý do không được để trống.');

    const day = new Date(dateEl.value + 'T00:00:00');
    const notice = new Date(day);
    notice.setDate(day.getDate() - 1);

    showToast('success',
      `Đã nộp đơn. Kết quả duyệt sẽ được thông báo trước 20:00 ngày ${p(notice.getDate())}/${p(notice.getMonth() + 1)}.`);
    reasonEl.value = '';
    submitBtn.disabled = true;
    submitBtn.textContent = 'Đã nộp đơn';
  });
})();
