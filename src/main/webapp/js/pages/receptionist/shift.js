/**
 * Client JavaScript cho màn hình Ca làm việc Lễ tân (Màn #39 - UC-25).
 * Xử lý: Mở/đóng các modal, tính chênh lệch tiền mặt realtime, validate lý do hủy/lệch tiền.
 *
 * Tuân thủ quy tắc codeImplementationFrontend.md:
 * - Code viết từ trên xuống dưới theo thứ tự chạy logic.
 * - Không method chaining dài, mỗi hàm 1 mục đích rõ ràng.
 * - Đồng bộ với shift.jsp và shift.css.
 */
document.addEventListener('DOMContentLoaded', function () {

    // ===== 1. Khai báo các phần tử DOM dùng chung =====
    const modalOpenShift = document.getElementById('modalOpenShift');
    const modalCloseShift = document.getElementById('modalCloseShift');
    const modalVoidOrder = document.getElementById('modalVoidOrder');

    const btnOpenShiftModal = document.getElementById('btnOpenShiftModal');
    const btnCloseShiftModal = document.getElementById('btnCloseShiftModal');
    const voidOrderButtons = document.querySelectorAll('.btn-void-order');
    const closeModalButtons = document.querySelectorAll('[data-close-modal]');

    // Các phần tử trong modal Chốt ca & Đối soát tiền mặt
    const formCloseShift = document.getElementById('formCloseShift');
    const inputCountedCash = document.getElementById('inputCountedCash');
    const expectedCashRaw = document.getElementById('expectedCashRaw');
    const varianceVal = document.getElementById('varianceVal');
    const varianceBadge = document.getElementById('varianceBadge');
    const varianceReasonWrapper = document.getElementById('varianceReasonWrapper');
    const inputVarianceReason = document.getElementById('inputVarianceReason');
    const varianceReasonError = document.getElementById('varianceReasonError');

    // Các phần tử trong modal Hủy đơn hàng quầy
    const formVoidOrder = document.getElementById('formVoidOrder');
    const voidOrderIdInput = document.getElementById('voidOrderIdInput');
    const voidOrderCodeText = document.getElementById('voidOrderCodeText');
    const voidOrderAmountText = document.getElementById('voidOrderAmountText');
    const inputCancelReason = document.getElementById('inputCancelReason');
    const cancelReasonError = document.getElementById('cancelReasonError');

    // ===== 2. Hàm tiện ích định dạng tiền & số =====

    // Hàm chuyển số thành chuỗi tiền tệ VND có phân cách phần nghìn
    function formatVnd(amount) {
        return Math.round(amount).toLocaleString('vi-VN') + 'đ';
    }

    // Hàm chỉ trích xuất số nguyên từ chuỗi (bỏ chữ, dấu chấm, dấu phẩy)
    function parseNumericOnly(str) {
        if (!str) return 0;
        const cleanStr = String(str).replace(/\D/g, '');
        return cleanStr ? parseInt(cleanStr, 10) : 0;
    }

    // ===== 3. Logic mở và đóng Modal =====

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

    // Gắn sự kiện mở modal Mở ca
    if (btnOpenShiftModal) {
        btnOpenShiftModal.addEventListener('click', function () {
            openModal(modalOpenShift);
        });
    }

    // Gắn sự kiện mở modal Chốt ca
    if (btnCloseShiftModal) {
        btnCloseShiftModal.addEventListener('click', function () {
            openModal(modalCloseShift);
            recalculateVariance();
            if (inputCountedCash) {
                inputCountedCash.focus();
                inputCountedCash.select();
            }
        });
    }

    // Gắn sự kiện mở modal Hủy đơn cho từng nút Hủy đơn trên danh sách
    voidOrderButtons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            const orderId = this.getAttribute('data-void-id');
            const orderCode = this.getAttribute('data-void-code');
            const orderAmount = this.getAttribute('data-void-amount');

            if (voidOrderIdInput) voidOrderIdInput.value = orderId;
            if (voidOrderCodeText) voidOrderCodeText.textContent = orderCode;
            if (voidOrderAmountText) voidOrderAmountText.textContent = orderAmount;

            if (inputCancelReason) {
                inputCancelReason.value = '';
                inputCancelReason.classList.remove('is-invalid');
            }
            if (cancelReasonError) {
                cancelReasonError.textContent = '';
                cancelReasonError.hidden = true;
            }

            openModal(modalVoidOrder);
            if (inputCancelReason) {
                inputCancelReason.focus();
            }
        });
    });

    // Gắn sự kiện đóng modal cho mọi nút [data-close-modal]
    closeModalButtons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            const parentModal = this.closest('.modal');
            if (parentModal) {
                closeModal(parentModal);
            }
        });
    });

    // Bấm ra ngoài backdrop để đóng modal
    [modalOpenShift, modalCloseShift, modalVoidOrder].forEach(function (modalEl) {
        if (!modalEl) return;
        modalEl.addEventListener('click', function (e) {
            if (e.target === modalEl) {
                closeModal(modalEl);
            }
        });
    });

    // Bấm phím ESC để đóng modal đang mở
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') {
            [modalOpenShift, modalCloseShift, modalVoidOrder].forEach(function (modalEl) {
                if (modalEl && modalEl.classList.contains('open')) {
                    closeModal(modalEl);
                }
            });
        }
    });

    // ===== 4. Logic tính chênh lệch tiền mặt trong modal Chốt ca =====

    function recalculateVariance() {
        if (!inputCountedCash || !expectedCashRaw) return;

        // B1: Đọc tiền đếm được và tiền kỳ vọng sổ sách
        const counted = parseNumericOnly(inputCountedCash.value);
        const expected = parseNumericOnly(expectedCashRaw.value);
        const diff = counted - expected;

        // B2: Cập nhật hiển thị số tiền chênh lệch
        if (varianceVal) {
            varianceVal.textContent = (diff > 0 ? '+' : '') + formatVnd(diff);
        }

        // B3: Đổi màu badge và toggle ô nhập lý do chênh lệch (BR-46)
        if (diff === 0) {
            if (varianceBadge) {
                varianceBadge.textContent = 'Khớp sổ sách';
                varianceBadge.className = 'badge badge-ok';
            }
            if (varianceReasonWrapper) {
                varianceReasonWrapper.style.display = 'none';
            }
            if (inputVarianceReason) {
                inputVarianceReason.removeAttribute('required');
            }
            if (varianceReasonError) {
                varianceReasonError.hidden = true;
            }
        } else {
            const isSurplus = diff > 0;
            if (varianceBadge) {
                varianceBadge.textContent = isSurplus
                    ? 'Thừa ' + formatVnd(diff)
                    : 'Thiếu ' + formatVnd(Math.abs(diff));
                varianceBadge.className = isSurplus ? 'badge badge-warn' : 'badge badge-danger';
            }
            if (varianceReasonWrapper) {
                varianceReasonWrapper.style.display = 'flex';
            }
            if (inputVarianceReason) {
                inputVarianceReason.setAttribute('required', 'required');
            }
        }
    }

    // Lắng nghe sự kiện người dùng gõ vào ô tiền mặt thực tế
    if (inputCountedCash) {
        inputCountedCash.addEventListener('input', function () {
            // Tự động format phân cách hàng nghìn khi rời hoặc gõ
            recalculateVariance();
        });

        inputCountedCash.addEventListener('blur', function () {
            const val = parseNumericOnly(this.value);
            this.value = val.toLocaleString('vi-VN');
            recalculateVariance();
        });
    }

    // ===== 5. Validate form Chốt ca trước khi gửi server =====
    if (formCloseShift) {
        formCloseShift.addEventListener('submit', function (e) {
            const counted = parseNumericOnly(inputCountedCash ? inputCountedCash.value : '0');
            const expected = parseNumericOnly(expectedCashRaw ? expectedCashRaw.value : '0');
            const diff = counted - expected;

            // Nếu có chênh lệch, bắt buộc lý do ≥ 5 ký tự (GBR-09, BR-46)
            if (diff !== 0) {
                const reason = inputVarianceReason ? inputVarianceReason.value.trim() : '';
                if (reason.length < 5) {
                    e.preventDefault();
                    if (varianceReasonError) {
                        varianceReasonError.textContent = 'Vui lòng giải trình rõ lý do chênh lệch (tối thiểu 5 ký tự).';
                        varianceReasonError.hidden = false;
                    }
                    if (inputVarianceReason) {
                        inputVarianceReason.focus();
                    }
                    return false;
                }
            }
            return true;
        });
    }

    // ===== 6. Validate form Hủy đơn hàng quầy trước khi gửi server =====
    if (formVoidOrder) {
        formVoidOrder.addEventListener('submit', function (e) {
            const reason = inputCancelReason ? inputCancelReason.value.trim() : '';
            if (reason.length < 5) {
                e.preventDefault();
                if (cancelReasonError) {
                    cancelReasonError.textContent = 'Vui lòng nhập lý do hủy cụ thể (tối thiểu 5 ký tự).';
                    cancelReasonError.hidden = false;
                }
                if (inputCancelReason) {
                    inputCancelReason.focus();
                }
                return false;
            }
            return true;
        });
    }
});
