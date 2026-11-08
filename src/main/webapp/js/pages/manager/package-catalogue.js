/**
 * package-catalogue.js — Tương tác biểu mẫu và modal xác nhận cho Danh mục gói tập (#17, #18, #19, #20)
 * Arena Theme, tuân thủ nghiêm ngặt chuẩn ECMAScript hiện đại.
 */

(function () {
    'use strict';

    // ===== 1. Khởi tạo khi DOM sẵn sàng =====
    document.addEventListener('DOMContentLoaded', function () {
        initToggleModal();
        initLivePreview();
        initPtFormLogic();
        initAutoFormatCurrency();
    });

    // ===== 2. Modal xác nhận Đổi trạng thái bán (Mở bán lại / Ngừng bán) =====
    function initToggleModal() {
        const toggleButtons = document.querySelectorAll('[data-toggle-package]');
        const modal = document.getElementById('toggleStatusModal');
        if (!modal) return;

        const packageIdInput = document.getElementById('togglePackageId');
        const targetActiveInput = document.getElementById('toggleTargetActive');
        const rowVerInput = document.getElementById('toggleRowVersion');
        const modalTitle = document.getElementById('toggleModalTitle');
        const modalMessage = document.getElementById('toggleModalMessage');
        const warningRunningOrders = document.getElementById('toggleWarningRunningOrders');
        const runningCountSpan = document.getElementById('toggleRunningCount');
        const submitBtn = document.getElementById('toggleSubmitBtn');

        toggleButtons.forEach(btn => {
            btn.addEventListener('click', function () {
                const pkgId = this.dataset.packageId;
                const pkgName = this.dataset.packageName;
                const targetActive = this.dataset.targetActive === 'true';
                const rowVer = this.dataset.rowVersion;
                const runningCount = parseInt(this.dataset.runningCount || '0', 10);

                packageIdInput.value = pkgId;
                targetActiveInput.value = targetActive;
                rowVerInput.value = rowVer;

                if (targetActive) {
                    modalTitle.textContent = 'Mở bán lại gói dịch vụ';
                    modalMessage.innerHTML = `Bạn có chắc chắn muốn mở bán lại gói <strong>${escapeHtml(pkgName)}</strong>? Sau khi mở lại, khách hàng và lễ tân có thể mua gói này.`;
                    submitBtn.textContent = 'Mở bán lại';
                    submitBtn.className = 'btn btn-primary';
                    if (warningRunningOrders) warningRunningOrders.style.display = 'none';
                } else {
                    modalTitle.textContent = 'Xác nhận ngừng bán gói dịch vụ';
                    modalMessage.innerHTML = `Bạn có chắc chắn muốn ngừng bán gói <strong>${escapeHtml(pkgName)}</strong>? Khách hàng sẽ không thể mua mới gói này trên website.`;
                    submitBtn.textContent = 'Ngừng bán';
                    submitBtn.className = 'btn btn-danger';

                    if (warningRunningOrders) {
                        if (runningCount > 0) {
                            runningCountSpan.textContent = runningCount;
                            warningRunningOrders.style.display = 'flex';
                        } else {
                            warningRunningOrders.style.display = 'none';
                        }
                    }
                }

                if (window.openModal) {
                    window.openModal('toggleStatusModal');
                } else {
                    modal.classList.add('show');
                    modal.style.display = 'block';
                }
            });
        });
    }

    // ===== 3. Real-time Live Preview Biểu mẫu (#18, #20) =====
    function initLivePreview() {
        const nameInput = document.getElementById('packageNameInput');
        const priceInput = document.getElementById('packagePriceInput');
        const durationSelect = document.getElementById('packageDurationSelect');
        const durationUnitSelect = document.getElementById('packageDurationUnitSelect');
        const benefitsInput = document.getElementById('packageBenefitsInput');
        const libraryCheckbox = document.getElementById('unlocksLibraryCheckbox');

        const prevTitle = document.getElementById('previewTitle');
        const prevPrice = document.getElementById('previewPrice');
        const prevDuration = document.getElementById('previewDuration');
        const prevBenefitsList = document.getElementById('previewBenefitsList');

        if (!prevTitle) return; // Không ở trang form

        function updatePreview() {
            // Tên gói
            const nameVal = nameInput ? nameInput.value.trim() : '';
            prevTitle.textContent = nameVal || 'Tên gói dịch vụ';

            // Giá gói
            const priceVal = priceInput ? parseCurrency(priceInput.value) : 0;
            if (prevPrice) {
                prevPrice.innerHTML = formatVnd(priceVal) + ' <span>/ gói</span>';
            }

            // Thời hạn
            if (prevDuration) {
                const durationVal = durationSelect ? durationSelect.value : '';
                const unitVal = durationUnitSelect ? durationUnitSelect.value : 'Month';
                if (unitVal === 'Day') {
                    prevDuration.textContent = 'Thời hạn: 1 Ngày';
                } else if (durationVal) {
                    prevDuration.textContent = 'Thời hạn: ' + durationVal + ' Tháng';
                } else {
                    prevDuration.textContent = 'Thời hạn: Theo buổi';
                }
            }

            // Quyền lợi
            if (prevBenefitsList) {
                prevBenefitsList.innerHTML = '';
                const lines = benefitsInput ? benefitsInput.value.split('\n') : [];
                
                // Quyền truy cập thư viện bài tập tự động nếu tích chọn
                if (libraryCheckbox && libraryCheckbox.checked) {
                    addBenefitItem(prevBenefitsList, 'Quyền xem toàn bộ Thư viện bài tập Super Gym', true);
                }

                lines.forEach(line => {
                    const clean = line.trim();
                    if (clean) {
                        addBenefitItem(prevBenefitsList, clean, false);
                    }
                });
            }
        }

        if (nameInput) nameInput.addEventListener('input', updatePreview);
        if (priceInput) priceInput.addEventListener('input', updatePreview);
        if (durationSelect) durationSelect.addEventListener('change', updatePreview);
        if (durationUnitSelect) durationUnitSelect.addEventListener('change', updatePreview);
        if (benefitsInput) benefitsInput.addEventListener('input', updatePreview);
        if (libraryCheckbox) libraryCheckbox.addEventListener('change', updatePreview);

        updatePreview();
    }

    function addBenefitItem(listElement, text, isLibrary) {
        const li = document.createElement('li');
        li.innerHTML = `
            <svg viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
            </svg>
            <span>${escapeHtml(text)} ${isLibrary ? '<em>(Tự động cấp quyền)</em>' : ''}</span>
        `;
        listElement.appendChild(li);
    }

    // ===== 4. Logic riêng cho Form PT: gợi ý số buổi và ẩn/hiện Buổi lẻ (#20) =====
    function initPtFormLogic() {
        const typeSelect = document.getElementById('ptTypeSelect');
        const durationGroup = document.getElementById('ptDurationGroup');
        const sessionGroup = document.getElementById('ptSessionGroup');
        const sessionInput = document.getElementById('ptSessionInput');
        const durationSelect = document.getElementById('packageDurationSelect');

        if (!typeSelect) return;

        function updatePtTypeVisibility() {
            const isSingle = typeSelect.value === 'SingleSession';
            if (durationGroup) durationGroup.style.display = isSingle ? 'none' : 'block';
            if (sessionGroup) sessionGroup.style.display = isSingle ? 'none' : 'block';
        }

        typeSelect.addEventListener('change', updatePtTypeVisibility);
        updatePtTypeVisibility();

        // Gợi ý số buổi ~22 buổi/tháng khi đổi thời hạn nếu chưa gõ
        if (durationSelect && sessionInput) {
            durationSelect.addEventListener('change', function () {
                const months = parseInt(this.value, 10);
                if (!isNaN(months) && months > 0) {
                    let suggested = months * 22;
                    if (months === 3) suggested = 65; // Khớp chuẩn seed data
                    if (months === 6) suggested = 130;
                    if (months === 12) suggested = 255; // Capped max TINYINT 255
                    sessionInput.value = suggested;
                    sessionInput.dispatchEvent(new Event('input'));
                }
            });
        }
    }

    // ===== 5. Tự động định dạng tiền tệ khi nhập =====
    function initAutoFormatCurrency() {
        const priceInput = document.getElementById('packagePriceInput');
        if (!priceInput) return;

        priceInput.addEventListener('blur', function () {
            const val = parseCurrency(this.value);
            if (val > 0) {
                this.value = val.toLocaleString('vi-VN');
            }
        });
    }

    // ===== Tiện ích =====
    function parseCurrency(str) {
        if (!str) return 0;
        const cleaned = str.replace(/[^\d]/g, '');
        return parseInt(cleaned, 10) || 0;
    }

    function formatVnd(val) {
        return (val || 0).toLocaleString('vi-VN') + ' ₫';
    }

    function escapeHtml(text) {
        if (!text) return '';
        return text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

})();
