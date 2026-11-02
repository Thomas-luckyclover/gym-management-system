/* #25 — Đơn nghỉ phép (Lễ tân nộp): khoảng ngày, ca nghỉ, phép năm / không lương có tách phần vượt phép,
   dự báo nhân sự còn lại, rút đơn khi còn Chờ duyệt. Chốt nộp 15:00 ngày liền trước (P-12). */
(function () {
  const ANNUAL_LEFT = 12.5;                       // phép năm còn lại của NV-0042
  const SHIFTS = { '2026-09-22': 1, '2026-09-24': 2, '2026-09-25': 1, '2026-09-28': 1 };
  const TOTAL_RECEP = 3;                          // lễ tân Working: NV-0042, NV-0057 và 1 người nữa
  const CUTOFF_HOUR = 15;

  const $ = id => document.getElementById(id);
  const typeEl = $('leaveType'), shiftEl = $('leaveShift'), dateEl = $('leaveDate'), toEl = $('toDate');
  const reasonEl = $('leaveReason'), submitBtn = $('leaveSubmit'), banner = $('clashBanner');
  const p = n => String(n).padStart(2, '0');
  const dm = d => `${p(d.getDate())}/${p(d.getMonth() + 1)}`;
  const dmy = d => `${dm(d)}/${d.getFullYear()}`;
  const num = n => String(n).replace('.', ',');
  const SHIFT_NAME = { FullDay: 'Cả ngày', Morning: 'Ca sáng', Afternoon: 'Ca chiều' };
  const keyOf = d => `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;

  function span() {
    const from = new Date(dateEl.value + 'T00:00:00');
    const to = toEl.value ? new Date(toEl.value + 'T00:00:00') : from;
    const days = Math.round((to - from) / 86400000) + 1;
    return { from, to, days, amount: days * (shiftEl.value === 'FullDay' ? 1 : 0.5) };
  }

  function refresh() {
    ['leaveDateErr', 'toDateErr'].forEach(clearErr);
    if (!dateEl.value) return;
    const { from, to, days, amount } = span();
    let ok = true;
    $('infoDate').textContent = days > 1 ? `${dmy(from)} – ${dmy(to)}` : dmy(from);
    $('pvScope').textContent = `${dm(from)}${days > 1 ? ' – ' + dm(to) : ''} · ${SHIFT_NAME[shiftEl.value]}`;

    if (to < from) { fieldError(toEl, 'toDateErr', 'Ngày kết thúc phải sau ngày bắt đầu.'); ok = false; }

    const cutoff = new Date(from); cutoff.setDate(from.getDate() - 1); cutoff.setHours(CUTOFF_HOUR, 0, 0, 0);
    if (sampleNow() > cutoff) {
      fieldError(dateEl, 'leaveDateErr', 'Quá hạn nộp đơn cho ngày đã chọn. Nghỉ đột xuất vui lòng báo trực tiếp Quản lý.');
      ok = false;
    }

    // ca trực đã xếp trong khoảng nghỉ -> cảnh báo; lễ tân còn lại = tổng - 1 (người nộp)
    let clash = 0;
    for (let i = 0; i < days; i++) {
      const d = new Date(from); d.setDate(from.getDate() + i);
      clash += SHIFTS[keyOf(d)] || 0;
    }
    banner.hidden = clash === 0;
    $('clashCount').textContent = clash;
    $('pvRecep').textContent = TOTAL_RECEP - 1;

    // tách phép năm / không lương
    let annual = 0, unpaid = 0;
    if (typeEl.value === 'Annual') { annual = Math.min(amount, ANNUAL_LEFT); unpaid = amount - annual; }
    else unpaid = amount;
    const parts = [];
    if (annual) parts.push(`${num(annual)} ngày nghỉ phép năm`);
    if (unpaid) parts.push(`${num(unpaid)} ngày nghỉ không lương`);
    $('leavePlanParts').textContent = parts.join(' + ');
    $('leaveBalanceText').textContent = `Phép năm còn lại: ${num(ANNUAL_LEFT)} ngày`;
    const needConsent = typeEl.value === 'Annual' && unpaid > 0;
    $('unpaidConsentRow').hidden = !needConsent;
    if (!needConsent) $('acceptUnpaid').checked = false;

    submitBtn.disabled = !ok;
  }

  [typeEl, shiftEl, dateEl, toEl].forEach(el => el.addEventListener('input', refresh));
  reasonEl.addEventListener('input', () => clearErr('leaveReasonErr'));
  refresh();

  $('leaveForm').addEventListener('submit', e => {
    e.preventDefault();
    const n = reasonEl.value.trim().length;
    if (n < 5 || n > 500) return fieldError(reasonEl, 'leaveReasonErr', 'Lý do phải từ 5 đến 500 ký tự.');
    if (!$('unpaidConsentRow').hidden && !$('acceptUnpaid').checked) {
      return showToast('warning', 'Cần đồng ý gửi phần vượt phép thành đơn không lương, hoặc giảm số ngày nghỉ.');
    }
    const { from } = span();
    const notice = new Date(from); notice.setDate(from.getDate() - 1);
    showToast('success', `Đã nộp đơn. Kết quả duyệt sẽ được thông báo trước 20:00 ngày ${dm(notice)}.`);
    reasonEl.value = '';
    submitBtn.disabled = true;
  });

  /* rút đơn khi còn Chờ duyệt */
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-withdraw]');
    if (!b) return;
    const row = b.closest('.data-row');
    openConfirmModal({
      title: 'Rút đơn nghỉ phép',
      body: `<p>Xác nhận rút đơn nghỉ ${row.querySelector('.row-title').firstChild.textContent.trim()}?</p>`,
      confirmLabel: 'Rút đơn',
      onConfirm() { row.remove(); showToast('success', 'Đã rút đơn nghỉ phép.'); },
    });
  });
})();
