/**
 * Client JavaScript cho màn hình Ca làm việc Lễ tân (Màn #39 - UC-25).
 * Xử lý: Mở/đóng modal, tính chênh lệch tiền mặt realtime, kiểm tra lý do lệch tiền / hủy đơn.
 */
document.addEventListener('DOMContentLoaded', function () {

    // ===== 1. Khai báo các phần tử DOM =====
    const modalOpenShift = document.getElementById('modalOpenShift');
    const modalCloseShift = document.getElementById('modalCloseShift');
    const modalVoidOrder = document.getElementById('modalVoidOrder');
    const allModals = [modalOpenShift, modalCloseShift, modalVoidOrder];

    const btnOpenShiftModal = document.getElementById('btnOpenShiftModal');
    const btnCloseShiftModal = document.getElementById('btnCloseShiftModal');
    const voidOrderButtons = document.querySelectorAll('.btn-void-order');
    const closeModalButtons = document.querySelectorAll('[data-close-modal]');

    // Modal Chốt ca & Đối soát tiền mặt
    const formCloseShift = document.getElementById('formCloseShift');
    const inputCountedCash = document.getElementById('inputCountedCash');
    const expectedCashRaw = document.getElementById('expectedCashRaw');
    const varianceVal = document.getElementById('varianceVal');
    const varianceBadge = document.getElementById('varianceBadge');
    const varianceReasonWrapper = document.getElementById('varianceReasonWrapper');
    const inputVarianceReason = document.getElementById('inputVarianceReason');
    const varianceReasonError = document.getElementById('varianceReasonError');

    // Modal Hủy đơn hàng quầy
    const formVoidOrder = document.getElementById('formVoidOrder');
    const voidOrderIdInput = document.getElementById('voidOrderIdInput');
    const voidOrderCodeText = document.getElementById('voidOrderCodeText');
    const voidOrderAmountText = document.getElementById('voidOrderAmountText');
    const inputCancelReason = document.getElementById('inputCancelReason');
    const cancelReasonError = document.getElementById('cancelReasonError');

    // ===== 2. Hàm tiện ích dùng chung =====

    function formatVnd(amount) {
        return Math.round(amount).toLocaleString('vi-VN') + 'đ';
    }

    function parseNumericOnly(str) {
        if (!str) return 0;
        const cleanStr = String(str).replace(/\D/g, '');
        return cleanStr ? parseInt(cleanStr, 10) : 0;
    }

    function showFieldError(inputEl, errorEl, message) {
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.hidden = false;
        }
        if (inputEl) {
            inputEl.focus();
        }
    }

    function clearFieldError(errorEl) {
        if (errorEl) {
            errorEl.textContent = '';
            errorEl.hidden = true;
        }
    }

    // ===== 3. Mở và đóng Modal =====

    function openModal(modalEl) {
        if (!modalEl) return;
        modalEl.classList.add('open');
        document.body.style.overflow = 'hidden';
    }

    function closeModal(modalEl) {
        if (!modalEl) return;
        modalEl.classList.remove('open');
        document.body.style.overflow = '';
    }

    if (btnOpenShiftModal) {
        btnOpenShiftModal.addEventListener('click', function () {
            openModal(modalOpenShift);
        });
    }

    if (btnCloseShiftModal) {
        btnCloseShiftModal.addEventListener('click', function () {
            openModal(modalCloseShift);
            if (inputCountedCash) {
                const val = parseNumericOnly(inputCountedCash.value);
                inputCountedCash.value = val > 0 ? val.toLocaleString('vi-VN') : '0';
                inputCountedCash.focus();
                inputCountedCash.select();
            }
            recalculateVariance();
        });
    }

    voidOrderButtons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            if (voidOrderIdInput) voidOrderIdInput.value = this.getAttribute('data-void-id');
            if (voidOrderCodeText) voidOrderCodeText.textContent = this.getAttribute('data-void-code');
            if (voidOrderAmountText) voidOrderAmountText.textContent = this.getAttribute('data-void-amount');

            if (inputCancelReason) {
                inputCancelReason.value = '';
                inputCancelReason.classList.remove('is-invalid');
            }
            clearFieldError(cancelReasonError);

            openModal(modalVoidOrder);
            if (inputCancelReason) {
                inputCancelReason.focus();
            }
        });
    });

    closeModalButtons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            closeModal(this.closest('.modal'));
        });
    });

    allModals.forEach(function (modalEl) {
        if (!modalEl) return;
        modalEl.addEventListener('click', function (e) {
            if (e.target === modalEl) {
                closeModal(modalEl);
            }
        });
    });

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            allModals.forEach(function (modalEl) {
                if (modalEl && modalEl.classList.contains('open')) {
                    closeModal(modalEl);
                }
            });
        }
    });

    // ===== 4. Tính chênh lệch tiền mặt realtime trong Modal Chốt ca =====

    function recalculateVariance() {
        if (!inputCountedCash || !expectedCashRaw) return;

        const counted = parseNumericOnly(inputCountedCash.value);
        const expected = parseNumericOnly(expectedCashRaw.value);
        const diff = counted - expected;

        if (varianceVal) {
            varianceVal.textContent = (diff > 0 ? '+' : '') + formatVnd(diff);
        }

        if (diff === 0) {
            if (varianceBadge) {
                varianceBadge.textContent = 'Khớp sổ sách';
                varianceBadge.className = 'badge badge-ok';
            }
            if (varianceReasonWrapper) varianceReasonWrapper.style.display = 'none';
            if (inputVarianceReason) inputVarianceReason.removeAttribute('required');
            clearFieldError(varianceReasonError);
        } else {
            const isSurplus = diff > 0;
            if (varianceBadge) {
                varianceBadge.textContent = isSurplus
                    ? 'Thừa ' + formatVnd(diff)
                    : 'Thiếu ' + formatVnd(Math.abs(diff));
                varianceBadge.className = isSurplus ? 'badge badge-warn' : 'badge badge-danger';
            }
            if (varianceReasonWrapper) varianceReasonWrapper.style.display = 'flex';
            if (inputVarianceReason) inputVarianceReason.setAttribute('required', 'required');
        }
    }

    if (inputCountedCash) {
        inputCountedCash.addEventListener('input', recalculateVariance);
        inputCountedCash.addEventListener('blur', function () {
            this.value = parseNumericOnly(this.value).toLocaleString('vi-VN');
            recalculateVariance();
        });
    }

    if (inputVarianceReason) {
        inputVarianceReason.addEventListener('input', function () {
            clearFieldError(varianceReasonError);
        });
    }

    if (inputCancelReason) {
        inputCancelReason.addEventListener('input', function () {
            clearFieldError(cancelReasonError);
        });
    }

    // ===== 5. Kiểm tra dữ liệu trước khi gửi form =====

    if (formCloseShift) {
        formCloseShift.addEventListener('submit', function (e) {
            const counted = parseNumericOnly(inputCountedCash ? inputCountedCash.value : '0');
            const expected = parseNumericOnly(expectedCashRaw ? expectedCashRaw.value : '0');
            if (counted === expected) return true;

            const reason = inputVarianceReason ? inputVarianceReason.value.trim() : '';
            if (reason.length < 5) {
                e.preventDefault();
                showFieldError(inputVarianceReason, varianceReasonError,
                    'Vui lòng giải trình rõ lý do chênh lệch (tối thiểu 5 ký tự).');
                return false;
            }
            if (reason.length > 500) {
                e.preventDefault();
                showFieldError(inputVarianceReason, varianceReasonError,
                    'Lý do chênh lệch không được vượt quá 500 ký tự.');
                return false;
            }
            return true;
        });
    }

    if (formVoidOrder) {
        formVoidOrder.addEventListener('submit', function (e) {
            const reason = inputCancelReason ? inputCancelReason.value.trim() : '';
            if (reason.length < 5) {
                e.preventDefault();
                showFieldError(inputCancelReason, cancelReasonError,
                    'Vui lòng nhập lý do hủy cụ thể (tối thiểu 5 ký tự).');
                return false;
            }
            if (reason.length > 500) {
                e.preventDefault();
                showFieldError(inputCancelReason, cancelReasonError,
                    'Lý do hủy đơn không được vượt quá 500 ký tự.');
                return false;
            }
            return true;
        });
    }
});
