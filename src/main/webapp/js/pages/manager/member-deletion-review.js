/**
 * member-deletion-review.js — Xử lý giao diện Quản lý duyệt/từ chối yêu cầu xóa (Màn #32, UC-17).
 */
document.addEventListener('DOMContentLoaded', function () {
  const approveForm = document.getElementById('approveForm');
  const rejectModal = document.getElementById('rejectModal');
  const rejectForm = document.getElementById('rejectForm');
  const rejectReasonInput = document.getElementById('rejectReason');
  const rejectCharCount = document.getElementById('rejectCharCount');
  const btnOpenRejectModal = document.getElementById('btnOpenRejectModal');
  const btnSubmitReject = document.getElementById('btnSubmitReject');

  // Xác nhận trước khi phê duyệt xóa
  if (approveForm) {
    approveForm.addEventListener('submit', function (e) {
      const confirmed = confirm(
        'Bạn có chắc chắn muốn PHÊ DUYỆT yêu cầu xóa hồ sơ này?\n' +
        'Hồ sơ khách hàng sẽ chuyển sang Inactive và mọi phiên đăng nhập sẽ bị vô hiệu hóa ngay lập tức.'
      );
      if (!confirmed) {
        e.preventDefault();
      }
    });
  }

  // Mở modal từ chối
  if (btnOpenRejectModal && rejectModal) {
    btnOpenRejectModal.addEventListener('click', function () {
      rejectModal.classList.add('open');
      rejectModal.setAttribute('aria-hidden', 'false');
      if (rejectReasonInput) {
        rejectReasonInput.value = '';
        updateRejectCharCount();
        setTimeout(() => rejectReasonInput.focus(), 100);
      }
    });
  }

  // Đóng modal từ chối
  document.querySelectorAll('.btn-close-reject-modal').forEach(btn => {
    btn.addEventListener('click', closeRejectModal);
  });

  if (rejectModal) {
    rejectModal.addEventListener('click', function (e) {
      if (e.target === rejectModal) {
        closeRejectModal();
      }
    });
  }

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && rejectModal && rejectModal.classList.contains('open')) {
      closeRejectModal();
    }
  });

  function closeRejectModal() {
    if (rejectModal) {
      rejectModal.classList.remove('open');
      rejectModal.setAttribute('aria-hidden', 'true');
    }
  }

  // Bộ đếm ký tự lý do từ chối
  if (rejectReasonInput && rejectCharCount) {
    rejectReasonInput.addEventListener('input', updateRejectCharCount);
  }

  function updateRejectCharCount() {
    if (!rejectReasonInput || !rejectCharCount) return;
    const len = rejectReasonInput.value.trim().length;
    rejectCharCount.textContent = len;
    if (len > 500) {
      rejectCharCount.classList.add('limit');
    } else {
      rejectCharCount.classList.remove('limit');
    }
  }

  // Validate form từ chối
  if (rejectForm) {
    rejectForm.addEventListener('submit', function (e) {
      const reason = rejectReasonInput ? rejectReasonInput.value.trim() : '';
      if (reason.length < 5) {
        e.preventDefault();
        alert('Lý do từ chối phải có tối thiểu 5 ký tự.');
        if (rejectReasonInput) rejectReasonInput.focus();
        return;
      }
      if (reason.length > 500) {
        e.preventDefault();
        alert('Lý do từ chối không được vượt quá 500 ký tự.');
        if (rejectReasonInput) rejectReasonInput.focus();
        return;
      }
      if (btnSubmitReject) {
        btnSubmitReject.disabled = true;
        btnSubmitReject.textContent = 'Đang xử lý...';
      }
    });
  }
});
