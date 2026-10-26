/**
 * Client script cho màn hình Bảo lưu gói tập Lễ tân (UC-18, BF-09, Prototype bao-luu.html).
 */
document.addEventListener('DOMContentLoaded', function () {
    const startInput = document.getElementById('startDate');
    const endInput = document.getElementById('plannedEndDate');
    const customerIdHidden = document.getElementById('customerIdHidden');
    const pkgEndHidden = document.getElementById('packageEndDateHidden');

    // 1. Tự động tính toán số ngày bảo lưu và hạn mới khi chọn ngày
    function calculateFreezeDays() {
        if (!startInput || !endInput || !startInput.value || !endInput.value) return;

        const start = new Date(startInput.value + 'T00:00:00');
        const end = new Date(endInput.value + 'T00:00:00');

        if (end < start) {
            endInput.value = startInput.value;
            return calculateFreezeDays();
        }

        // Số ngày bảo lưu = (end - start) + 1 ngày
        const diffDays = Math.round((end - start) / (1000 * 60 * 60 * 24)) + 1;
        const displayDays = document.getElementById('displayDaysCount');
        if (displayDays) {
            displayDays.textContent = diffDays + ' ngày';
        }

        // Tính ngày hết hạn thẻ mới
        if (pkgEndHidden && pkgEndHidden.value) {
            const pkgEnd = new Date(pkgEndHidden.value + 'T00:00:00');
            const newExpiry = new Date(pkgEnd.getTime() + (diffDays * 24 * 60 * 60 * 1000));
            const displayExpiry = document.getElementById('displayNewExpiryDate');
            if (displayExpiry) {
                displayExpiry.textContent = formatDate(newExpiry);
            }
        }

        // Gửi AJAX xem trước buổi PT
        if (customerIdHidden && customerIdHidden.value) {
            fetchPtSessions(customerIdHidden.value, startInput.value, endInput.value);
        }
    }

    if (startInput && endInput) {
        startInput.addEventListener('change', calculateFreezeDays);
        endInput.addEventListener('change', calculateFreezeDays);
        calculateFreezeDays(); // Tính toán ban đầu
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
        if (!container) return;

        if (!sessions || sessions.length === 0) {
            if (countSpan) countSpan.textContent = '0';
            container.innerHTML = '<div class="empty-pt-note"><i class="fa-solid fa-circle-check"></i> Không có buổi tập PT nào trong khoảng thời gian này.</div>';
            return;
        }

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

    if (window.location.hash) {
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

    // Hàm tiện ích định dạng ngày
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
