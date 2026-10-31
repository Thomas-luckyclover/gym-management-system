/**
 * Client script cho màn hình Bảo lưu gói tập Lễ tân (UC-18, BF-09, Prototype bao-luu.html).
 */
document.addEventListener('DOMContentLoaded', function () {
    const startInput = document.getElementById('startDate');
    const endInput = document.getElementById('plannedEndDate');
    const startDisplay = document.getElementById('startDateDisplay');
    const endDisplay = document.getElementById('plannedEndDateDisplay');
    const customerIdHidden = document.getElementById('customerIdHidden');
    const pkgEndHidden = document.getElementById('packageEndDateHidden');

    function parseDate(str) {
        if (!str) return null;
        const parts = str.split('-');
        if (parts.length !== 3) return null;
        const y = parseInt(parts[0], 10);
        const m = parseInt(parts[1], 10) - 1;
        const d = parseInt(parts[2], 10);
        const date = new Date(y, m, d);
        return isNaN(date.getTime()) ? null : date;
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
        val = val.trim();
        const match = /^(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{4})$/.exec(val);
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
        if (parts.length !== 3) return isoStr;
        return `${parts[2]}/${parts[1]}/${parts[0]}`;
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

    // Tính ngày kết thúc tối đa (tối đa 1 tháng kể từ ngày bắt đầu nhưng không vượt quá hạn gói tập)
    function calculateMaxAllowedEnd(startIsoStr) {
        if (!startIsoStr) return '';
        const startDate = parseDate(startIsoStr);
        if (!startDate) return '';

        const maxByDuration = addMonthsAndDays(startDate, 1, -1);
        const maxByDurationStr = toIsoDate(maxByDuration);

        const pkgEndStr = pkgEndHidden ? pkgEndHidden.value : (startInput.max || '');
        if (pkgEndStr && pkgEndStr < maxByDurationStr) {
            return pkgEndStr;
        }
        return maxByDurationStr;
    }

    // Cập nhật min, max và hint cho ô Ngày kết thúc dựa trên Ngày bắt đầu
    function updateEndDateConstraints(isStartChange) {
        if (!startInput || !endInput || !startInput.value) return;

        const maxAllowedEndStr = calculateMaxAllowedEnd(startInput.value);
        if (maxAllowedEndStr) {
            endInput.max = maxAllowedEndStr;
            const hint = document.getElementById('plannedEndDateHint');
            if (hint) {
                hint.textContent = 'Tối đa 1 tháng (đến ngày ' + formatDateStr(maxAllowedEndStr) + ').';
            }
        }
        endInput.min = startInput.value;

        if (isStartChange) {
            // Khi thay đổi ngày bắt đầu:
            // Nếu ngày kết thúc chưa có, hoặc trước ngày bắt đầu mới, hoặc vượt quá ngày tối đa cho phép,
            // hoặc người dùng chưa từng chỉnh sửa thủ công ngày kết thúc:
            // -> Tự động đưa ngày kết thúc về ngày tối đa 1 tháng (maxAllowedEndStr) thay vì co về 1 ngày!
            const wasCustomModified = endInput.dataset && endInput.dataset.userModified === 'true';
            if (!wasCustomModified || !endInput.value || endInput.value < startInput.value || (endInput.max && endInput.value > endInput.max)) {
                endInput.value = maxAllowedEndStr || startInput.value;
                if (endInput.dataset) {
                    endInput.dataset.userModified = 'false';
                }
            }
        }
    }

    // Xử lý khi Ngày bắt đầu thay đổi (change, blur)
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

    // Xử lý khi Ngày kết thúc thay đổi (change, blur)
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

    // Xử lý khi đang gõ phím trên ô input (input event):
    // KHÔNG can thiệp / clamp giữa chừng để tránh kẹt phím spinbox
    function handleDateInput() {
        if (!startInput || !endInput) return;
        if (!startInput.value || !endInput.value) return;

        // Chỉ tính toán hiển thị nếu cả 2 ngày hợp lệ và trong khoảng hợp lệ
        if (startInput.value <= endInput.value) {
            const maxAllowedEndStr = calculateMaxAllowedEnd(startInput.value);
            if (!maxAllowedEndStr || endInput.value <= maxAllowedEndStr) {
                calculateFreezeDays();
            }
        }
    }

    // 1. Tự động tính toán số ngày bảo lưu và hạn mới khi chọn ngày
    function calculateFreezeDays() {
        if (!startInput || !endInput) return;
        if (!startInput.value || !endInput.value) return;

        const start = parseDate(startInput.value);
        const end = parseDate(endInput.value);
        if (!start || !end) return;

        if (end < start) {
            return;
        }

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

        // Gửi AJAX xem trước buổi PT
        if (customerIdHidden && customerIdHidden.value) {
            fetchPtSessions(customerIdHidden.value, startInput.value, endInput.value);
        }
    }

    if (startInput && endInput) {
        startInput.addEventListener('change', handleStartDateChange);
        startInput.addEventListener('blur', handleStartDateChange);
        startInput.addEventListener('input', handleDateInput);

        endInput.addEventListener('change', handleEndDateChange);
        endInput.addEventListener('blur', handleEndDateChange);
        endInput.addEventListener('input', handleDateInput);

        updateEndDateConstraints(false);
        syncDisplayInputs();
        calculateFreezeDays(); // Tính toán ban đầu
    }

    // Xử lý ô hiển thị Ngày bắt đầu (định dạng Việt Nam dd/mm/yyyy)
    if (startDisplay) {
        startDisplay.addEventListener('change', function () {
            const parsed = parseVietnameseDate(this.value);
            if (parsed && startInput) {
                startInput.value = parsed.iso;
                handleStartDateChange();
            } else if (startInput && startInput.value) {
                this.value = formatVietnameseDate(startInput.value);
            }
        });
        startDisplay.addEventListener('blur', function () {
            const parsed = parseVietnameseDate(this.value);
            if (parsed && startInput) {
                startInput.value = parsed.iso;
                handleStartDateChange();
            } else if (startInput && startInput.value) {
                this.value = formatVietnameseDate(startInput.value);
            }
        });
    }

    // Xử lý ô hiển thị Ngày kết thúc (định dạng Việt Nam dd/mm/yyyy)
    if (endDisplay) {
        endDisplay.addEventListener('change', function () {
            const parsed = parseVietnameseDate(this.value);
            if (parsed && endInput) {
                endInput.value = parsed.iso;
                handleEndDateChange();
            } else if (endInput && endInput.value) {
                this.value = formatVietnameseDate(endInput.value);
            }
        });
        endDisplay.addEventListener('blur', function () {
            const parsed = parseVietnameseDate(this.value);
            if (parsed && endInput) {
                endInput.value = parsed.iso;
                handleEndDateChange();
            } else if (endInput && endInput.value) {
                this.value = formatVietnameseDate(endInput.value);
            }
        });
    }

    // Mở lịch popup khi nhấn nút lịch
    document.querySelectorAll('.freeze-calendar-button').forEach(btn => {
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            const targetId = this.getAttribute('data-picker-target');
            const picker = document.getElementById(targetId);
            if (!picker) return;
            if (typeof picker.showPicker === 'function') {
                try {
                    picker.showPicker();
                } catch (err) {
                    picker.focus();
                }
            } else {
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

    // 2. Gửi AJAX lấy danh sách buổi PT bị ảnh hưởng
    function fetchPtSessions(customerId, startDate, endDate) {
        if (typeof window === 'undefined' || !window.location || typeof fetch !== 'function') return;
        const url = (window.location.pathname || '') + '?action=previewSessions&customerId='
            + encodeURIComponent(customerId)
            + '&startDate=' + encodeURIComponent(startDate)
            + '&plannedEndDate=' + encodeURIComponent(endDate);

        fetch(url, { headers: { 'Accept': 'application/json' } })
            .then(res => res.json())
            .then(result => {
                const sessions = Array.isArray(result) ? result : (result.data || []);
                renderPtSessions(sessions);
            })
            .catch(err => console.warn('Lỗi lấy buổi PT:', err));
    }

    function renderPtSessions(sessions) {
        const container = document.getElementById('impactSessionsContainer');
        const countSpan = document.getElementById('impactCount');
        const ptBox = document.getElementById('ptImpactBox');
        if (!container) return;

        if (!sessions || sessions.length === 0) {
            if (countSpan) countSpan.textContent = '0';
            if (ptBox) ptBox.style.display = 'none';
            container.innerHTML = '<div class="empty-pt-note"><i class="fa-solid fa-circle-check"></i> Không có buổi tập PT nào trong khoảng thời gian này.</div>';
            return;
        }

        if (ptBox) ptBox.style.display = 'block';
        if (countSpan) countSpan.textContent = sessions.length;
        let html = '<div class="table-responsive"><table class="data-table pt-table">';
        html += '<thead><tr><th>Ngày tập</th><th>Giờ tập</th><th>Huấn luyện viên</th><th>Loại gói</th><th>Tác động</th></tr></thead><tbody>';

        sessions.forEach(s => {
            const time = (s.startTime ? s.startTime.substring(0, 5) : '') + ' - ' + (s.endTime ? s.endTime.substring(0, 5) : '');
            html += '<tr>';
            html += '<td><b>' + escapeHtml(formatDateStr(s.slotDate)) + '</b></td>';
            html += '<td>' + escapeHtml(time) + '</td>';
            html += '<td>' + escapeHtml(s.ptName || '--') + '</td>';
            html += '<td>' + escapeHtml(s.packageType || '--') + '</td>';
            if (s.willBeMissed) {
                html += '<td><span class="badge badge-danger">Vắng (Mất buổi)</span></td>';
            } else {
                html += '<td><span class="badge badge-ok">Giữ lịch</span></td>';
            }
            html += '</tr>';
        });

        html += '</tbody></table></div>';
        container.innerHTML = html;
    }

    // 3. Xử lý Modal Mở lại thẻ trước hạn (giống modal check-in)
    const earlyModal = document.getElementById('modalEarlyCancel');
    const openEarlyBtn = document.getElementById('btnOpenEarlyModal');
    const closeBtns = document.querySelectorAll('[data-close-modal]');

    function openModal() {
        if (earlyModal) earlyModal.classList.add('open');
    }

    function closeModal() {
        if (earlyModal) earlyModal.classList.remove('open');
    }

    if (openEarlyBtn) openEarlyBtn.addEventListener('click', openModal);
    closeBtns.forEach(btn => btn.addEventListener('click', closeModal));

    if (earlyModal) {
        earlyModal.addEventListener('click', function (e) {
            if (e.target === earlyModal) closeModal();
        });
    }

    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && earlyModal && earlyModal.classList.contains('open')) {
            closeModal();
        }
    });

    // 4. Quản lý Tabs (Danh sách hội viên & Gói tập vs Lịch sử bảo lưu)
    const tabButtons = document.querySelectorAll('.freeze-tab-btn');
    const tabPanes = document.querySelectorAll('.freeze-tab-pane');

    function switchTab(targetId) {
        if (!targetId) return;
        tabButtons.forEach(b => {
            if (b.dataset.target === targetId || b.getAttribute('data-target') === targetId) {
                b.classList.add('active');
            } else {
                b.classList.remove('active');
            }
        });
        tabPanes.forEach(p => {
            if ('#' + p.id === targetId || p.id === targetId.replace('#', '')) {
                p.classList.add('active');
            } else {
                p.classList.remove('active');
            }
        });
    }

    tabButtons.forEach(btn => {
        btn.addEventListener('click', function (e) {
            e.preventDefault();
            const targetId = this.dataset.target || this.getAttribute('data-target');
            switchTab(targetId);
            if (history.replaceState) {
                history.replaceState(null, null, targetId);
            }
        });
    });

    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('hq') || urlParams.has('hfilter') || window.location.hash === '#tabHistory') {
        switchTab('#tabHistory');
    } else if (window.location.hash) {
        switchTab(window.location.hash);
    }

    // 5. Cho phép click vào cả dòng bảng để chọn khách hàng
    document.querySelectorAll('.gym-table tr[data-href]').forEach(row => {
        row.addEventListener('click', function (e) {
            if (e.target.closest('a') || e.target.closest('button') || e.target.closest('input[type="checkbox"]')) return;
            const href = this.getAttribute('data-href');
            if (href) window.location.href = href;
        });
    });

    // 6. Xử lý mở rộng / thu gọn lý do bảo lưu dài (chống vỡ bảng khi lý do dài tới 400 - 500 ký tự)
    document.querySelectorAll('.btn-toggle-reason').forEach(btn => {
        btn.addEventListener('click', function (e) {
            e.stopPropagation();
            const textSpan = this.parentElement.querySelector('.reason-text');
            if (!textSpan) return;
            const isExpanded = textSpan.classList.toggle('expanded');
            this.textContent = isExpanded ? 'Thu gọn' : 'Xem thêm';
        });
    });

    // 7. Tự động áp dụng bộ lọc khi thay đổi lựa chọn ở Tab 1
    const filterSelect = document.getElementById('freezeFilterSelect');
    if (filterSelect) {
        filterSelect.addEventListener('change', function () {
            const form = document.getElementById('freezeSearchForm');
            if (form) form.submit();
        });
    }

    // 8. Tự động áp dụng bộ lọc trạng thái khi thay đổi lựa chọn ở Tab 2
    const historyFilterSelect = document.getElementById('historyFilterSelect');
    if (historyFilterSelect) {
        historyFilterSelect.addEventListener('change', function () {
            const form = document.getElementById('historySearchForm');
            if (form) form.submit();
        });
    }

    // 9. Tìm kiếm nhanh thời gian thực ở Tab 2 (Lịch sử bảo lưu)
    const historyInput = document.getElementById('historyQueryInput');
    if (historyInput) {
        historyInput.addEventListener('input', function () {
            const val = this.value.trim().toLowerCase();
            const rows = document.querySelectorAll('#tabHistory .gym-table tbody tr');
            rows.forEach(r => {
                if (r.querySelector('td[colspan]')) return;
                if (!val) {
                    r.style.display = '';
                    return;
                }
                const text = r.textContent.toLowerCase();
                r.style.display = text.includes(val) ? '' : 'none';
            });
        });
    }

    function formatDate(d) {
        if (!d || isNaN(d.getTime())) return '--/--/----';
        return String(d.getDate()).padStart(2, '0') + '/' + String(d.getMonth() + 1).padStart(2, '0') + '/' + d.getFullYear();
    }

    function formatDateStr(str) {
        if (!str) return '';
        const p = str.split('-');
        return p.length === 3 ? (p[2] + '/' + p[1] + '/' + p[0]) : str;
    }

    function escapeHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

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
