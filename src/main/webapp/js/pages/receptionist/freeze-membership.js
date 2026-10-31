/**
 * Client script cho màn hình Bảo lưu gói tập Lễ tân (UC-18, BF-09, Prototype bao-luu.html).
 */
document.addEventListener('DOMContentLoaded', function () {
    const startInput = document.getElementById('startDate');
    const endInput = document.getElementById('plannedEndDate');
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

    function addMonthsAndDays(baseDate, months, days) {
        return new Date(baseDate.getFullYear(), baseDate.getMonth() + months, baseDate.getDate() + days);
    }

    // Chặn ngày nằm ngoài khoảng min/max cho phép và tự động đưa về ngày hợp lệ
    function enforceDateBounds() {
        if (!startInput || !endInput) return;

        const pkgEndStr = pkgEndHidden ? pkgEndHidden.value : (startInput.max || '');

        // 1. Ngày bắt đầu: tối thiểu là min (ngày mai), tối đa là ngày hết hạn gói tập
        if (startInput.min && startInput.value && startInput.value < startInput.min) {
            startInput.value = startInput.min;
        }
        if (pkgEndStr && startInput.value && startInput.value > pkgEndStr) {
            startInput.value = pkgEndStr;
        }

        // 2. Tính ngày kết thúc tối đa: tối đa 1 tháng kể từ ngày bắt đầu nhưng không vượt quá hạn gói
        let maxAllowedEndStr = '';
        if (startInput.value) {
            const startDate = parseDate(startInput.value);
            if (startDate) {
                const maxByDuration = addMonthsAndDays(startDate, 1, -1);
                const maxByDurationStr = toIsoDate(maxByDuration);

                if (pkgEndStr && pkgEndStr < maxByDurationStr) {
                    maxAllowedEndStr = pkgEndStr;
                } else {
                    maxAllowedEndStr = maxByDurationStr;
                }
            }
        }

        if (maxAllowedEndStr) {
            endInput.max = maxAllowedEndStr;
            const hint = document.getElementById('plannedEndDateHint');
            if (hint) {
                hint.textContent = 'Tối đa 1 tháng (đến ngày ' + formatDateStr(maxAllowedEndStr) + ').';
            }
        }

        if (startInput.value) {
            endInput.min = startInput.value;
        }

        // 3. Ngày kết thúc: không được trước ngày bắt đầu và không vượt quá maxAllowedEndStr
        if (startInput.value && endInput.value && endInput.value < startInput.value) {
            endInput.value = startInput.value;
        }
        if (endInput.max && endInput.value && endInput.value > endInput.max) {
            endInput.value = endInput.max;
        }
    }

    // 1. Tự động tính toán số ngày bảo lưu và hạn mới khi chọn ngày
    function calculateFreezeDays() {
        if (!startInput || !endInput) return;
        enforceDateBounds();
        if (!startInput.value || !endInput.value) return;

        const start = parseDate(startInput.value);
        const end = parseDate(endInput.value);
        if (!start || !end) return;

        if (end < start) {
            endInput.value = startInput.value;
            return calculateFreezeDays();
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
        startInput.addEventListener('change', calculateFreezeDays);
        startInput.addEventListener('input', calculateFreezeDays);
        startInput.addEventListener('blur', calculateFreezeDays);

        endInput.addEventListener('change', calculateFreezeDays);
        endInput.addEventListener('input', calculateFreezeDays);
        endInput.addEventListener('blur', calculateFreezeDays);
        calculateFreezeDays(); // Tính toán ban đầu
    }

    // Chặn submit form nếu ngày vượt quá giới hạn
    const formApplyFreeze = document.getElementById('formApplyFreeze');
    if (formApplyFreeze) {
        formApplyFreeze.addEventListener('submit', function (e) {
            enforceDateBounds();
            calculateFreezeDays();

            if (endInput && endInput.max && endInput.value > endInput.max) {
                e.preventDefault();
                endInput.value = endInput.max;
                calculateFreezeDays();
                return false;
            }
            if (startInput && startInput.min && startInput.value < startInput.min) {
                e.preventDefault();
                startInput.value = startInput.min;
                calculateFreezeDays();
                return false;
            }
            const pkgEndStr = pkgEndHidden ? pkgEndHidden.value : (startInput.max || '');
            if (pkgEndStr && startInput.value && startInput.value > pkgEndStr) {
                e.preventDefault();
                startInput.value = pkgEndStr;
                calculateFreezeDays();
                return false;
            }
            if (startInput.value && endInput.value && endInput.value < startInput.value) {
                e.preventDefault();
                endInput.value = startInput.value;
                calculateFreezeDays();
                return false;
            }
        });
    }

    // 2. Gửi AJAX lấy danh sách buổi PT bị ảnh hưởng
    function fetchPtSessions(customerId, startDate, endDate) {
        const url = window.location.pathname + '?action=previewSessions&customerId='
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
});
