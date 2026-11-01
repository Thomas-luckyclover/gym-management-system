/* #25 — Đơn nghỉ phép của PT (staff/leave-request-form.jsp).
   Khoảng nghỉ + ca, Annual / Unpaid có tách phần vượt phép, dự báo định biên,
   báo buổi dạy trùng, rút đơn Pending. */
(function () {
  /* buổi đã xếp của PT Hoàng Long, theo ngày (khớp Lịch dạy) */
  const SCHEDULED = { '2026-09-21': 1, '2026-09-22': 1, '2026-09-23': 2, '2026-09-24': 2, '2026-09-25': 1, '2026-09-26': 1, '2026-09-28': 1, '2026-09-30': 1, '2026-10-01': 1 };
  const CUTOFF_HOUR = 15;          // P-12
  const MIN_PT = 2;                // P-13
  const ANNUAL_LEFT = 12;          // định mức 14 ngày, đã dùng 2

  const $ = id => document.getElementById(id);
  const typeEl = $('leaveType'), shiftEl = $('leaveShift'), dateEl = $('leaveDate'), toEl = $('toDate');
  const reasonEl = $('leaveReason'), submitBtn = $('leaveSubmit');
  const p = n => String(n).padStart(2, '0');
  const dmy = d => `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;
  const dm = d => `${p(d.getDate())}/${p(d.getMonth() + 1)}`;
  const iso = d => `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;

  function range() {
    if (!dateEl.value) return null;
    const from = new Date(dateEl.value + 'T00:00:00');
    const to = toEl.value ? new Date(toEl.value + 'T00:00:00') : from;
    return { from, to };
  }
  function dayCount(r) {
    const n = Math.round((r.to - r.from) / 86400000) + 1;
    return shiftEl.value === 'FullDay' ? n : n * 0.5;
  }

  function refresh() {
    ['leaveDateErr', 'toDateErr'].forEach(clearErr);
    const r = range();
    if (!r) return;
    if (r.to < r.from) { fieldError(toEl, 'toDateErr', 'Ngày kết thúc phải sau ngày bắt đầu.'); submitBtn.disabled = true; return; }

    const cutoff = new Date(r.from);
    cutoff.setDate(r.from.getDate() - 1);
    cutoff.setHours(CUTOFF_HOUR, 0, 0, 0);
    $('infoDate').textContent = dmy(r.from);
    $('infoDeadline').textContent = `${CUTOFF_HOUR}:00 · ${dm(cutoff)}`;
    const shiftName = shiftEl.selectedOptions[0].textContent;
    $('pvScope').textContent = `${dm(r.from)}${toEl.value ? '–' + dm(r.to) : ''} · ${shiftName}`;
    $('pvPt').textContent = shiftEl.value === 'FullDay' ? 3 : 3;

    // buổi dạy trùng khoảng nghỉ
    let clash = 0;
    for (let d = new Date(r.from); d <= r.to; d.setDate(d.getDate() + 1)) clash += SCHEDULED[iso(d)] || 0;
    $('clashBanner').hidden = clash === 0;
    $('clashCount').textContent = clash;
    $('leaveScheduleLink').hidden = clash === 0;

    // dự kiến gửi: phép năm trước, phần vượt thành không lương (cần đồng ý)
    const days = dayCount(r);
    let annual = 0, unpaid = 0;
    if (typeEl.value === 'Annual') { annual = Math.min(days, ANNUAL_LEFT); unpaid = days - annual; } else { unpaid = days; }
    const parts = [];
    if (annual) parts.push(`${annual} ngày nghỉ phép năm`);
    if (unpaid) parts.push(`${unpaid} ngày nghỉ không lương`);
    $('leavePlanParts').textContent = parts.join(' + ');
    $('leaveBalanceText').textContent = `Phép năm còn ${ANNUAL_LEFT} ngày` + (typeEl.value === 'Annual' && annual ? `, sau đơn này còn ${ANNUAL_LEFT - annual} ngày.` : '.');
    const needConsent = typeEl.value === 'Annual' && unpaid > 0;
    $('unpaidConsentRow').hidden = !needConsent;
    $('unpaidConsentHint').hidden = !needConsent || $('acceptUnpaid').checked;

    const late = sampleNow() > cutoff;
    submitBtn.disabled = late || clash > 0 || (needConsent && !$('acceptUnpaid').checked);
    if (late) {
      fieldError(dateEl, 'leaveDateErr', 'Quá hạn nộp đơn cho ngày đã chọn. Nghỉ đột xuất vui lòng báo trực tiếp Quản lý.');
    }
  }
  [typeEl, shiftEl, dateEl, toEl, $('acceptUnpaid')].forEach(el => el.addEventListener('input', refresh));
  reasonEl.addEventListener('input', () => clearErr('leaveReasonErr'));

  function syncHistory() {
    const rows = document.querySelectorAll('[data-leave-row]');
    $('leaveEmpty').hidden = rows.length > 0;
    $('leaveCount').textContent = rows.length + ' đơn';
  }

  $('leaveForm').addEventListener('submit', e => {
    e.preventDefault();
    const n = reasonEl.value.trim().length;
    if (n < 5 || n > 500) return fieldError(reasonEl, 'leaveReasonErr', 'Lý do phải từ 5 đến 500 ký tự.');
    const r = range();
    const notice = new Date(r.from);
    notice.setDate(r.from.getDate() - 1);
    const row = document.createElement('div');
    row.className = 'data-row';
    row.dataset.leaveRow = '';
    row.innerHTML = `<div class="row-main">
        <div class="row-title">${dmy(r.from)}${toEl.value ? '–' + dmy(r.to) : ''} · ${dayCount(r)} ngày · ${shiftEl.selectedOptions[0].textContent} <span class="badge badge-warn">Chờ duyệt</span></div>
        <div class="row-meta"><span><i class="fa-solid fa-clock"></i> Nộp lúc ${dmy(sampleNow())} ${p(sampleNow().getHours())}:${p(sampleNow().getMinutes())}</span>
          <span><i class="fa-solid fa-tag"></i> ${typeEl.selectedOptions[0].textContent}</span>
          <span><i class="fa-solid fa-quote-left"></i> ${reasonEl.value.trim()}</span></div></div>
      <button class="btn btn-ghost btn-sm" type="button" data-withdraw="${dmy(r.from)}">Rút đơn</button>`;
    $('leaveHistory').prepend(row);
    syncHistory();
    showToast('success', `Đã nộp đơn. Kết quả duyệt sẽ được thông báo trước 20:00 ngày ${dm(notice)}.`);
    reasonEl.value = '';
  });

  // rút đơn đang chờ duyệt
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-withdraw]');
    if (!b) return;
    const row = b.closest('[data-leave-row]');
    openConfirmModal({
      title: 'Rút đơn nghỉ phép',
      body: `<p>Xác nhận rút đơn nghỉ ${b.dataset.withdraw}?</p>`,
      confirmLabel: 'Rút đơn',
      onConfirm() {
        row.remove();
        syncHistory();
        showToast('success', 'Đã rút đơn nghỉ phép.');
      },
    });
  });

  refresh();
  syncHistory();
})();
