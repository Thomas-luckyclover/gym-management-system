/**
 * Client script cho màn hình Bảo lưu gói tập Lễ tân (UC-18, BF-09).
 */
document.addEventListener('DOMContentLoaded', function () {
    const startInput = document.getElementById('startDate');
    const endInput = document.getElementById('plannedEndDate');
    const startDisplay = document.getElementById('startDateDisplay');
    const endDisplay = document.getElementById('plannedEndDateDisplay');
    const pkgEndHidden = document.getElementById('packageEndDateHidden');

    // ===== 1. TIỆN ÍCH XỬ LÝ & ĐỊNH DẠNG NGÀY THÁNG =====

    function parseDate(str) {
        if (!str) return null;
        const parts = str.split('-');
        if (parts.length !== 3) return null;
        const d = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
        return isNaN(d.getTime()) ? null : d;
    }

    function toIsoDate(d) {
        if (!d || isNaN(d.getTime())) return '';
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
    }

    function parseVietnameseDate(val) {
        if (!val) return null;
        const match = /^(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{4})$/.exec(val.trim());
        if (!match) return null;
        const d = parseInt(match[1], 10);
        const m = parseInt(match[2], 10);
        const y = parseInt(match[3], 10);
        if (y < 1900 || y > 2100 || m < 1 || m > 12 || d < 1 || d > 31) return null;
        const date = new Date(y, m - 1, d);
        if (date.getFullYear() !== y || date.getMonth() !== m - 1 || date.getDate() !== d) return null;
        return {
            iso: `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`,
            date: date
        };
    }

    function formatVietnameseDate(isoStr) {
        if (!isoStr) return '';
        const parts = isoStr.split('-');
        return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : isoStr;
    }

    function formatDate(d) {
        if (!d || isNaN(d.getTime())) return '--/--/----';
        return String(d.getDate()).padStart(2, '0') + '/' + String(d.getMonth() + 1).padStart(2, '0') + '/' + d.getFullYear();
    }

    function syncDisplayInputs() {
        if (startDisplay && startInput && startInput.value) {
            startDisplay.value = formatVietnameseDate(startInput.value);
        }
        if (endDisplay && endInput && endInput.value) {
            endDisplay.value = formatVietnameseDate(endInput.value);
        }
    }

    function addMonthsAndDays(baseDate, months, days) {
        return new Date(baseDate.getFullYear(), baseDate.getMonth() + months, baseDate.getDate() + days);
    }

    // ===== 2. TÍNH TOÁN RÀNG BUỘC VÀ SỐ NGÀY BẢO LƯU =====

    // Tính ngày kết thúc tối đa (tối đa 1 tháng kể từ ngày bắt đầu nhưng không vượt quá hạn gói tập)
    function calculateMaxAllowedEnd(startIsoStr) {
        if (!startIsoStr) return '';
        const startDate = parseDate(startIsoStr);
        if (!startDate) return '';

        const maxByDurationStr = toIsoDate(addMonthsAndDays(startDate, 1, -1));
        const pkgEndStr = pkgEndHidden ? pkgEndHidden.value : (startInput ? startInput.max || '' : '');
        return (pkgEndStr && pkgEndStr < maxByDurationStr) ? pkgEndStr : maxByDurationStr;
    }

    // Cập nhật min, max và hint cho ô Ngày kết thúc dựa trên Ngày bắt đầu
    function updateEndDateConstraints(isStartChange) {
        if (!startInput || !endInput || !startInput.value) return;

        const maxAllowedEndStr = calculateMaxAllowedEnd(startInput.value);
        if (maxAllowedEndStr) {
            endInput.max = maxAllowedEndStr;
            const hint = document.getElementById('plannedEndDateHint');
            if (hint) {
                hint.textContent = 'Tối đa 1 tháng (đến ngày ' + formatVietnameseDate(maxAllowedEndStr) + ').';
            }
        }
        endInput.min = startInput.value;

        if (isStartChange) {
            const wasCustomModified = endInput.dataset && endInput.dataset.userModified === 'true';
            if (!wasCustomModified || !endInput.value || endInput.value < startInput.value || (endInput.max && endInput.value > endInput.max)) {
                endInput.value = maxAllowedEndStr || startInput.value;
                if (endInput.dataset) {
                    endInput.dataset.userModified = 'false';
                }
            }
        }
    }

    // Xử lý khi Ngày bắt đầu thay đổi
    function handleStartDateChange() {
        if (!startInput) return;
        const pkgEndStr = pkgEndHidden ? pkgEndHidden.value : (startInput.max || '');

        if (startInput.min && startInput.value && startInput.value < startInput.min) {
            startInput.value = startInput.min;
        }
        if (pkgEndStr && startInput.value && startInput.value > pkgEndStr) {
            startInput.value = pkgEndStr;
        }

        updateEndDateConstraints(true);
        syncDisplayInputs();
        calculateFreezeDays();
    }

    // Xử lý khi Ngày kết thúc thay đổi
    function handleEndDateChange() {
        if (!endInput) return;

        if (endInput.dataset) {
            endInput.dataset.userModified = 'true';
        }

        if (startInput && startInput.value && endInput.value && endInput.value < startInput.value) {
            endInput.value = startInput.value;
        }
        if (endInput.max && endInput.value && endInput.value > endInput.max) {
            endInput.value = endInput.max;
        }

        syncDisplayInputs();
        calculateFreezeDays();
    }

    // Xử lý khi đang gõ phím trên ô input (không clamp giữa chừng)
    function handleDateInput() {
        if (!startInput || !endInput || !startInput.value || !endInput.value) return;
        if (startInput.value <= endInput.value) {
            const maxAllowed = calculateMaxAllowedEnd(startInput.value);
            if (!maxAllowed || endInput.value <= maxAllowed) {
                calculateFreezeDays();
            }
        }
    }

    // Lọc danh sách buổi PT và cập nhật số lượng theo khoảng ngày đang chọn (không cần AJAX)
    function updatePtImpactPreview(startIso, endIso) {
        const box = document.getElementById('ptImpactBox');
        const list = document.getElementById('ptImpactList');
        if (!box || !list) return;

        let count = 0;
        list.querySelectorAll('li').forEach(li => {
            const d = (li.dataset && li.dataset.slotDate) || '';
            const inRange = d >= startIso && d <= endIso;
            li.style.display = inRange ? '' : 'none';
            if (inRange) count++;
        });

        const countText = document.getElementById('ptImpactCountText');
        if (countText) {
            countText.textContent = count + ' buổi tập PT';
        }
        box.style.display = count > 0 ? '' : 'none';
    }

    // Tự động tính toán số ngày bảo lưu và hạn mới khi chọn ngày
    function calculateFreezeDays() {
        if (!startInput || !endInput || !startInput.value || !endInput.value) return;

        const start = parseDate(startInput.value);
        const end = parseDate(endInput.value);
        if (!start || !end || end < start) return;

        updatePtImpactPreview(startInput.value, endInput.value);

        // Số ngày bảo lưu = (end - start) + 1 ngày
        const diffDays = Math.max(1, Math.round((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1);
        const displayDays = document.getElementById('displayDaysCount');
        if (displayDays) {
            displayDays.textContent = diffDays + ' ngày';
        }

        // Tính ngày hết hạn thẻ mới
        if (pkgEndHidden && pkgEndHidden.value) {
            const pkgEnd = parseDate(pkgEndHidden.value);
            if (pkgEnd) {
                const newExpiry = new Date(pkgEnd.getFullYear(), pkgEnd.getMonth(), pkgEnd.getDate() + diffDays);
                const displayExpiry = document.getElementById('displayNewExpiryDate');
                if (displayExpiry) {
                    displayExpiry.textContent = formatDate(newExpiry);
                }
            }
        }
    }

    // Đồng bộ 2 chiều giữa ô input text dd/mm/yyyy và ô input date native
    function bindDisplayInput(displayEl, targetInput, changeHandler) {
        if (!displayEl || !targetInput) return;
        function sync() {
            const parsed = parseVietnameseDate(displayEl.value);
            if (parsed) {
                targetInput.value = parsed.iso;
                changeHandler();
            } else if (targetInput.value) {
                displayEl.value = formatVietnameseDate(targetInput.value);
            }
        }
        displayEl.addEventListener('change', sync);
        displayEl.addEventListener('blur', sync);
    }

    if (startInput && endInput) {
        ['change', 'blur'].forEach(ev => {
            startInput.addEventListener(ev, handleStartDateChange);
            endInput.addEventListener(ev, handleEndDateChange);
        });
        startInput.addEventListener('input', handleDateInput);
        endInput.addEventListener('input', handleDateInput);

        bindDisplayInput(startDisplay, startInput, handleStartDateChange);
        bindDisplayInput(endDisplay, endInput, handleEndDateChange);

        updateEndDateConstraints(false);
        syncDisplayInputs();
        calculateFreezeDays();
    }

    // Mở lịch popup khi nhấn nút biểu tượng lịch
    document.querySelectorAll('.freeze-calendar-button').forEach(btn => {
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            const picker = document.getElementById(this.getAttribute('data-picker-target'));
            if (!picker) return;
            try {
                if (typeof picker.showPicker === 'function') {
                    picker.showPicker();
                } else {
                    picker.focus();
                }
            } catch {
                picker.focus();
            }
        });
    });

    // Chặn submit form nếu ngày vượt quá giới hạn
    const formApplyFreeze = document.getElementById('formApplyFreeze');
    if (formApplyFreeze) {
        formApplyFreeze.addEventListener('submit', function (e) {
            const rawStart = startInput ? startInput.value : '';
            const rawEnd = endInput ? endInput.value : '';
            const pkgEndStr = pkgEndHidden ? pkgEndHidden.value : (startInput ? (startInput.max || '') : '');

            let hasError = false;
            if (startInput && startInput.min && rawStart && rawStart < startInput.min) hasError = true;
            if (pkgEndStr && rawStart && rawStart > pkgEndStr) hasError = true;
            if (endInput && endInput.max && rawEnd && rawEnd > endInput.max) hasError = true;
            if (rawStart && rawEnd && rawEnd < rawStart) hasError = true;

            handleStartDateChange();
            handleEndDateChange();

            if (hasError) {
                e.preventDefault();
                return false;
            }
        });
    }

    // ===== 3. QUẢN LÝ MODAL POPUP (MỞ LẠI THẺ & XEM LÝ DO) =====

    function setupModal(modalEl, openTriggers, onOpen) {
        if (!modalEl) return;
        const close = () => modalEl.classList.remove('open');
        modalEl.querySelectorAll('[data-close-modal], [data-close-reason-modal]').forEach(b => b.addEventListener('click', close));
        modalEl.addEventListener('click', e => { if (e.target === modalEl) close(); });
        document.addEventListener('keydown', e => {
            if (e.key === 'Escape' && modalEl.classList.contains('open')) close();
        });
        if (openTriggers) {
            openTriggers.forEach(btn => {
                btn.addEventListener('click', e => {
                    e.preventDefault();
                    e.stopPropagation();
                    if (onOpen) onOpen(btn);
                    modalEl.classList.add('open');
                });
            });
        }
    }

    // Modal Mở lại thẻ trước hạn
    setupModal(
        document.getElementById('modalEarlyCancel'),
        document.querySelectorAll('#btnOpenEarlyModal')
    );

    // Modal Xem chi tiết lý do bảo lưu
    setupModal(
        document.getElementById('modalViewReason'),
        document.querySelectorAll('.btn-view-reason'),
        btn => {
            const textEl = document.getElementById('reasonModalText');
            if (textEl) {
                textEl.textContent = btn.getAttribute('data-reason') || '(Không ghi nhận lý do)';
            }
        }
    );

    // ===== 4. BỘ LỌC VÀ TÌM KIẾM =====

    // Tự động submit khi thay đổi lựa chọn dropdown
    ['freezeFilterSelect', 'historyFilterSelect'].forEach(id => {
        const el = document.getElementById(id);
        if (el && el.form) {
            el.addEventListener('change', () => el.form.submit());
        }
    });

    // Lọc nhanh theo thời gian thực ở Tab 2
    const historyInput = document.getElementById('historyQueryInput');
    if (historyInput) {
        historyInput.addEventListener('input', function () {
            const val = this.value.trim().toLowerCase();
            document.querySelectorAll('.bf07-table tbody tr').forEach(r => {
                if (r.querySelector('td[colspan]')) return;
                r.style.display = (!val || r.textContent.toLowerCase().includes(val)) ? '' : 'none';
            });
        });
    }

    // Export phục vụ kiểm thử đơn vị (Node.js test harness)
    if (typeof window !== 'undefined') {
        window._freezeMembership = {
            parseDate,
            toIsoDate,
            parseVietnameseDate,
            formatVietnameseDate,
            syncDisplayInputs,
            addMonthsAndDays,
            calculateMaxAllowedEnd,
            updateEndDateConstraints,
            handleStartDateChange,
            handleEndDateChange,
            handleDateInput,
            calculateFreezeDays
        };
    }
});
