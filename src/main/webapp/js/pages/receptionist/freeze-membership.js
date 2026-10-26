/**
 * freeze-membership.js — Kịch bản tương tác màn hình Bảo lưu thẻ hội viên (Màn #34, UC-18, BF-09).
 *
 * <p>Quy trình tương tác phía Client:
 * 1. Khi chọn ngày bắt đầu / kết thúc: tự động tính số ngày và hạn mới sau bảo lưu.
 * 2. Gửi AJAX lấy danh sách buổi PT bị ảnh hưởng để cảnh báo trước cho Lễ tân.
 * 3. Quản lý modal xác nhận mở lại thẻ trước hạn (hủy bảo lưu sớm).
 * </p>
 */

document.addEventListener("DOMContentLoaded", function () {
    initDateCalculation();
    initEarlyCancelModal();
});

/**
 * 1. Lắng nghe thay đổi ngày bắt đầu / kết thúc bảo lưu
 */
function initDateCalculation() {
    const startInput = document.getElementById("startDate");
    const endInput = document.getElementById("plannedEndDate");
    if (!startInput || !endInput) return;

    function handleDateChange() {
        if (!startInput.value || !endInput.value) return;

        const start = new Date(startInput.value + "T00:00:00");
        const end = new Date(endInput.value + "T00:00:00");

        // Nếu ngày kết thúc trước ngày bắt đầu, tự chỉnh lại bằng ngày bắt đầu
        if (end < start) {
            endInput.value = startInput.value;
            return handleDateChange();
        }

        // Tính số ngày bảo lưu = (end - start) + 1 ngày
        const diffDays = Math.round((end - start) / (1000 * 60 * 60 * 24)) + 1;
        const displayDays = document.getElementById("displayDaysCount");
        if (displayDays) {
            displayDays.textContent = diffDays + " ngày";
        }

        // Tính ngày hết hạn thẻ mới
        const pkgEndHidden = document.getElementById("packageEndDateHidden");
        const displayExpiry = document.getElementById("displayNewExpiryDate");
        if (pkgEndHidden && pkgEndHidden.value && displayExpiry) {
            const pkgEnd = new Date(pkgEndHidden.value + "T00:00:00");
            const newExpiry = new Date(pkgEnd.getTime() + (diffDays * 24 * 60 * 60 * 1000));
            displayExpiry.textContent = formatDate(newExpiry);
        }

        // Gọi AJAX xem trước buổi PT bị ảnh hưởng
        const customerIdHidden = document.getElementById("customerIdHidden");
        if (customerIdHidden && customerIdHidden.value) {
            fetchAffectedPtSessions(customerIdHidden.value, startInput.value, endInput.value);
        }
    }

    startInput.addEventListener("change", handleDateChange);
    endInput.addEventListener("change", handleDateChange);

    // Tính toán ban đầu khi tải trang
    handleDateChange();
}

/**
 * 2. Gọi AJAX lấy danh sách buổi PT bị ảnh hưởng trong kỳ bảo lưu
 */
function fetchAffectedPtSessions(customerId, startDate, endDate) {
    const url = window.location.pathname + "?action=previewSessions&customerId="
        + encodeURIComponent(customerId)
        + "&startDate=" + encodeURIComponent(startDate)
        + "&plannedEndDate=" + encodeURIComponent(endDate);

    fetch(url, { headers: { "Accept": "application/json" } })
        .then(function (res) { return res.json(); })
        .then(function (result) {
            const sessions = Array.isArray(result) ? result : (result.data || []);
            renderAffectedSessions(sessions);
        })
        .catch(function (err) {
            console.error("Lỗi xem trước buổi PT:", err);
        });
}

/**
 * 3. Hiển thị bảng buổi PT bị ảnh hưởng
 */
function renderAffectedSessions(sessions) {
    const container = document.getElementById("impactSessionsContainer");
    const countBadge = document.getElementById("impactCount");
    if (!container) return;

    if (!sessions || sessions.length === 0) {
        if (countBadge) countBadge.textContent = "0";
        container.innerHTML = '<div class="empty-pt-note"><i class="fa-solid fa-circle-check"></i> Không có buổi tập PT nào trong khoảng thời gian này.</div>';
        return;
    }

    if (countBadge) countBadge.textContent = sessions.length;

    let html = '<div class="table-responsive"><table class="data-table pt-table">';
    html += '<thead><tr><th>Ngày tập</th><th>Khung giờ</th><th>Huấn luyện viên</th><th>Loại gói</th><th>Tác động</th></tr></thead><tbody>';

    for (let i = 0; i < sessions.length; i++) {
        const s = sessions[i];
        const time = (s.startTime ? s.startTime.substring(0, 5) : "") + " - " + (s.endTime ? s.endTime.substring(0, 5) : "");
        html += '<tr>';
        html += '<td><b>' + escapeHtml(formatDateStr(s.slotDate)) + '</b></td>';
        html += '<td>' + escapeHtml(time) + '</td>';
        html += '<td>' + escapeHtml(s.ptName || "--") + '</td>';
        html += '<td>' + escapeHtml(s.packageType || "--") + '</td>';
        if (s.willBeMissed) {
            html += '<td><span class="badge badge-danger">Vắng (Mất buổi)</span></td>';
        } else {
            html += '<td><span class="badge badge-ok">Giữ lịch (Bạn tập có thẻ)</span></td>';
        }
        html += '</tr>';
    }

    html += '</tbody></table></div>';
    container.innerHTML = html;
}

/**
 * 4. Quản lý modal xác nhận mở lại thẻ trước hạn
 */
function initEarlyCancelModal() {
    const btnOpen = document.getElementById("btnOpenCancelFreezeModal");
    const modal = document.getElementById("modalCancelFreezeEarly");
    if (!btnOpen || !modal) return;

    function openModal() {
        modal.classList.add("is-open");
        modal.setAttribute("aria-hidden", "false");
    }

    function closeModal() {
        modal.classList.remove("is-open");
        modal.setAttribute("aria-hidden", "true");
    }

    btnOpen.addEventListener("click", openModal);

    const closeButtons = modal.querySelectorAll("[data-close-modal]");
    closeButtons.forEach(function (btn) {
        btn.addEventListener("click", closeModal);
    });

    modal.addEventListener("click", function (e) {
        if (e.target === modal) closeModal();
    });

    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape" && modal.classList.contains("is-open")) closeModal();
    });
}

function formatDate(d) {
    if (!d || isNaN(d.getTime())) return "--/--/----";
    return String(d.getDate()).padStart(2, '0') + "/" + String(d.getMonth() + 1).padStart(2, '0') + "/" + d.getFullYear();
}

function formatDateStr(str) {
    if (!str) return "";
    const p = str.split("-");
    return p.length === 3 ? (p[2] + "/" + p[1] + "/" + p[0]) : str;
}

function escapeHtml(str) {
    if (!str) return "";
    const div = document.createElement("div");
    div.textContent = str;
    return div.innerHTML;
}
