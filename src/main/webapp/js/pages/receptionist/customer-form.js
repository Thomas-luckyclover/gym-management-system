/**
 * customer-form.js — Xử lý giao diện Chi tiết hồ sơ & Khối xóa hồ sơ (Màn #29, UC-16).
 */
document.addEventListener('DOMContentLoaded', function () {
  const form = document.getElementById('deleteFormOnPage');
  const reasonInput = document.getElementById('deletionReasonPage');
  const charCounter = document.getElementById('charCountPage');
  const submitBtn = document.getElementById('btnSubmitDeletion');

  if (reasonInput && charCounter) {
    reasonInput.addEventListener('input', function () {
      const len = reasonInput.value.trim().length;
      charCounter.textContent = len;
      if (len > 500) {
        charCounter.classList.add('limit');
      } else {
        charCounter.classList.remove('limit');
      }
    });
  }

  if (form) {
    form.addEventListener('submit', function (e) {
      const reason = reasonInput ? reasonInput.value.trim() : '';
      if (reason.length < 5) {
        e.preventDefault();
        alert('Lý do xóa phải có tối thiểu 5 ký tự.');
        if (reasonInput) reasonInput.focus();
        return;
      }
      if (reason.length > 500) {
        e.preventDefault();
        alert('Lý do xóa không được vượt quá 500 ký tự.');
        if (reasonInput) reasonInput.focus();
        return;
      }
      if (!confirm('Bạn có chắc chắn muốn thực hiện thao tác xóa hồ sơ này?')) {
        e.preventDefault();
        return;
      }
      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Đang xử lý...';
      }
    });
  }
});
