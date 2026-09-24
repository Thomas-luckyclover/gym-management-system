/* 9.3.3 — Đổi lịch: chỉ T7/CN tuần hiện tại, tối đa 3 lượt mỗi gói. */
(function () {
  const MAX = 3;
  let picked = null;
  let used = 1;

  initSlotGrid({
    weekendOnly: true,
    onPick({ text }) {
      picked = text;
      document.querySelector('[data-summary-slot]').textContent = text;
      const btn = document.getElementById('swapBtn');
      btn.disabled = false;
      btn.textContent = 'Xác nhận đổi lịch';
    },
  });
  bindSlotBand('slotBand', 'slotReset');

  const quota = document.getElementById('swapQuota');
  const renderQuota = () => { quota.textContent = `Còn ${MAX - used}/${MAX} lượt`; };
  renderQuota();

  document.getElementById('swapBtn').addEventListener('click', () => {
    used++;                                  // lưu thành công thì lượt đổi tăng 1
    renderQuota();
    if (used >= MAX) {
      document.querySelector('[data-done-slot]').textContent = picked;
      showStep('done');
      showToast('success', 'Đã đổi lịch sang ' + picked + '. Đây là lượt đổi cuối của gói.');
      return;
    }
    document.querySelector('[data-done-slot]').textContent = picked;
    showStep('done');
    showToast('success', 'Đã đổi lịch sang ' + picked + '.');
  });
})();
