/**
 * customer-list.js — Xử lý giao diện Danh sách hồ sơ khách hàng (Màn #28, UC-16).
 * Quản lý mở/đóng modal xóa hồ sơ, đếm ký tự lý do và validate form.
 */
document.addEventListener('DOMContentLoaded', function () {
  const modal = document.getElementById('deleteModal');
  if (!modal) return;

  const modalCustomerName = document.getElementById('modalCustomerName');
  const modalCustomerCode = document.getElementById('modalCustomerCode');
  const modalCustomerId = document.getElementById('modalCustomerId');
  const modalAlertWarning = document.getElementById('modalAlertWarning');
  const modalAlertInfo = document.getElementById('modalAlertInfo');
  const modalSubmitBtn = document.getElementById('modalSubmitBtn');
  const reasonInput = document.getElementById('deletionReason');
  const charCount = document.getElementById('charCount');
  const deleteForm = document.getElementById('deleteCustomerForm');

  // Mở modal khi bấm nút "Xóa hồ sơ"
  document.querySelectorAll('.btn-open-delete-modal').forEach(button => {
    button.addEventListener('click', function () {
      const customerId = this.getAttribute('data-customer-id');
      const customerName = this.getAttribute('data-customer-name');
      const customerCode = this.getAttribute('data-customer-code');
      const hasActive = this.getAttribute('data-has-active') === 'true';

      modalCustomerId.value = customerId;
      modalCustomerName.textContent = customerName;
      modalCustomerCode.textContent = customerCode;

      if (hasActive) {
        modalAlertWarning.style.display = 'block';
        modalAlertInfo.style.display = 'none';
        modalSubmitBtn.textContent = 'Gửi yêu cầu Quản lý duyệt';
      } else {
        modalAlertWarning.style.display = 'none';
        modalAlertInfo.style.display = 'block';
        modalSubmitBtn.textContent = 'Xác nhận xóa ngay';
      }

      reasonInput.value = '';
      updateCharCounter();
      openModal();
    });
  });

  // Đóng modal
  document.querySelectorAll('.btn-close-modal').forEach(btn => {
    btn.addEventListener('click', closeModal);
  });

  modal.addEventListener('click', function (e) {
    if (e.target === modal) {
      closeModal();
    }
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && modal.classList.contains('open')) {
      closeModal();
    }
  });

  function openModal() {
    modal.classList.add('open');
    modal.setAttribute('aria-hidden', 'false');
    setTimeout(() => reasonInput.focus(), 100);
  }

  function closeModal() {
    modal.classList.remove('open');
    modal.setAttribute('aria-hidden', 'true');
  }

  // Bộ đếm ký tự
  reasonInput.addEventListener('input', updateCharCounter);

  function updateCharCounter() {
    const len = reasonInput.value.trim().length;
    charCount.textContent = len;
    if (len > 500) {
      charCount.classList.add('limit');
    } else {
      charCount.classList.remove('limit');
    }
  }

  // Validate trước khi submit
  if (deleteForm) {
    deleteForm.addEventListener('submit', function (e) {
      const reason = reasonInput.value.trim();
      if (reason.length < 5) {
        e.preventDefault();
        alert('Lý do xóa phải có tối thiểu 5 ký tự.');
        reasonInput.focus();
        return;
      }
      if (reason.length > 500) {
        e.preventDefault();
        alert('Lý do xóa không được vượt quá 500 ký tự.');
        reasonInput.focus();
        return;
      }
      modalSubmitBtn.disabled = true;
      modalSubmitBtn.textContent = 'Đang xử lý...';
    });
  }
});
