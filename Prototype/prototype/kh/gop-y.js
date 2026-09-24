/* 10.2.1 — Góp ý: kênh một chiều, không có luồng trả lời trong ứng dụng. */
(function () {
  const form = document.getElementById('feedbackForm');
  const text = document.getElementById('feedbackText');

  text.addEventListener('input', () => clearErr('feedbackTextErr'));

  form.addEventListener('submit', e => {
    e.preventDefault();
    if (!text.value.trim()) {
      return fieldError(text, 'feedbackTextErr', 'Nội dung góp ý không được để trống.');
    }
    showStep('done');
    showToast('success', 'Cảm ơn bạn đã góp ý. Góp ý được gửi tới Quản lý.');
  });

  document.querySelector('[data-feedback-again]').addEventListener('click', () => {
    text.value = '';
    showStep('form');
  });
})();
