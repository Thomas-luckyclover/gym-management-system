/**
 * freeze-membership.js — Kịch bản tương tác màn hình Bảo lưu thẻ hội viên Lễ tân (Màn #34, UC-18, BF-09).
 *
 * <p>Nhiệm vụ chính:
 * <ul>
 *   <li>Tính toán thời gian bảo lưu thực tế và hạn gói mới khi thay đổi ngày bắt đầu / kết thúc.</li>
 *   <li>Gửi AJAX xem trước danh sách các buổi PT bị chuyển sang trạng thái Vắng (CBR-62, CBR-67).</li>
 *   <li>Quản lý modal xác nhận hủy bảo lưu sớm với phím tắt Escape và bẫy focus.</li>
 * </ul>
 * </p>
 */

document.addEventListener("DOMContentLoaded", function () {
    initFreezePage();
});

function initFreezePage() {
    // B1: Gắn sự kiện tính toán tự động và gọi AJAX xem trước buổi PT
    const startDateInput = document.getElementById("startDate");
    const endDateInput = document.getElementById("plannedEndDate");
    const customerIdHidden = document.getElementById("customerIdHidden");

    if (startDateInput && endDateInput) {
        startDateInput.addEventListener("change", function () {
            onDatesChanged();
        });

        endDateInput.addEventListener("change", function () {
            onDatesChanged();
        });

        // Tính toán ban đầu ngay khi tải trang
        onDatesChanged();
    }

    // B2: Gắn kiểm tra dữ liệu trước khi gửi form bảo lưu
    const formApply = document.getElementById("formApplyFreeze");
    if (formApply) {
        formApply.addEventListener("submit", function (e) {
            const reasonInput = document.getElementById("freezeReason");
            if (reasonInput && reasonInput.value.trim().length < 5) {
                e.preventDefault();
                alert("Lý do bảo lưu phải có ít nhất 5 ký tự.");
                reasonInput.focus();
            }
        });
    }

    // B3: Gắn sự kiện mở và đóng modal Hủy bảo lưu sớm
    initCancelFreezeModal();
}

/**
 * Xử lý khi ngày bắt đầu hoặc ngày kết thúc bảo lưu thay đổi.
 */
function onDatesChanged() {
    const startDateInput = document.getElementById("startDate");
    const endDateInput = document.getElementById("plannedEndDate");
    const customerIdHidden = document.getElementById("customerIdHidden");
    const pkgEndHidden = document.getElementById("packageEndDateHidden");

    if (!startDateInput || !endDateInput || !startDateInput.value || !endDateInput.value) {
        return;
    }

    const startVal = startDateInput.value;
    const endVal = endDateInput.value;

    const startDate = new Date(startVal + "T00:00:00");
    const endDate = new Date(endVal + "T00:00:00");

    // Nếu ngày kết thúc trước ngày bắt đầu, điều chỉnh lại ngày kết thúc = ngày bắt đầu
    if (endDate < startDate) {
        endDateInput.value = startVal;
        return onDatesChanged();
    }

    // B1: Tính số ngày bảo lưu = (endDate - startDate) + 1 ngày (GBR-03)
    const diffTime = endDate.getTime() - startDate.getTime();
    const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24)) + 1;

    const displayDays = document.getElementById("displayDaysCount");
    if (displayDays) {
        displayDays.textContent = diffDays + " ngày";
    }

    // B2: Tính ngày hết hạn gói tập mới sau khi tự động mở lại (DBR-51)
    if (pkgEndHidden && pkgEndHidden.value) {
        const pkgEndDate = new Date(pkgEndHidden.value + "T00:00:00");
        const newExpiryDate = new Date(pkgEndDate.getTime() + (diffDays * 24 * 60 * 60 * 1000));

        const displayExpiry = document.getElementById("displayNewExpiryDate");
        if (displayExpiry) {
            displayExpiry.textContent = formatDateVietnamese(newExpiryDate);
        }
    }

    // B3: Gửi AJAX cập nhật danh sách các buổi PT bị ảnh hưởng (CBR-62)
    if (customerIdHidden && customerIdHidden.value) {
        fetchAffectedPtSessions(customerIdHidden.value, startVal, endVal);
    }
}

/**
 * Gọi API nội bộ lấy danh sách các buổi PT bị chuyển sang trạng thái Vắng.
 */
function fetchAffectedPtSessions(customerId, startDateStr, endDateStr) {
    const url = window.location.pathname + "?action=previewSessions&customerId="
        + encodeURIComponent(customerId)
        + "&startDate=" + encodeURIComponent(startDateStr)
        + "&plannedEndDate=" + encodeURIComponent(endDateStr);

    fetch(url, {
        headers: {
            "Accept": "application/json"
        }
    })
    .then(function (response) {
        if (!response.ok) {
            throw new Error("Mạng hoặc máy chủ phản hồi lỗi");
        }
        return response.json();
    })
    .then(function (sessions) {
        renderAffectedSessionsTable(sessions);
    })
    .catch(function (error) {
        console.error("Lỗi khi tải buổi PT bị ảnh hưởng:", error);
    });
}

/**
 * Hiển thị các buổi PT lên giao diện kèm escape chống XSS.
 */
function renderAffectedSessionsTable(sessions) {
    const container = document.getElementById("impactSessionsContainer");
    const countSpan = document.getElementById("impactCount");

    if (!container) return;

    if (!sessions || sessions.length === 0) {
        if (countSpan) countSpan.textContent = "0";
        container.innerHTML = '<div class="empty-pt-note"><i class="fa-solid fa-circle-check"></i> Không có buổi tập PT nào bị ảnh hưởng trong khoảng thời gian này.</div>';
        return;
    }

    if (countSpan) countSpan.textContent = sessions.length;

    let html = '<div class="table-responsive">';
    html += '<table class="data-table pt-table">';
    html += '<thead><tr>';
    html += '<th>Ngày tập</th>';
    html += '<th>Giờ tập</th>';
    html += '<th>Huấn luyện viên</th>';
    html += '<th>Loại gói</th>';
    html += '<th>Tác động</th>';
    html += '</tr></thead><tbody>';

    for (let i = 0; i < sessions.length; i++) {
        const s = sessions[i];
        html += '<tr>';
        html += '<td><b>' + escapeHtml(formatDateString(s.slotDate)) + '</b></td>';
        html += '<td>' + escapeHtml(s.startTime ? s.startTime.substring(0, 5) : '') + ' - ' + escapeHtml(s.endTime ? s.endTime.substring(0, 5) : '') + '</td>';
        html += '<td>' + escapeHtml(s.ptName) + '</td>';
        html += '<td>' + escapeHtml(s.packageType) + '</td>';

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
 * Quản lý đóng/mở modal xác nhận Hủy bảo lưu sớm.
 */
function initCancelFreezeModal() {
    const btnOpen = document.getElementById("btnOpenCancelFreezeModal");
    const modal = document.getElementById("modalCancelFreezeEarly");

    if (!btnOpen || !modal) return;

    btnOpen.addEventListener("click", function () {
        openModal(modal);
    });

    // Bắt sự kiện đóng cho tất cả nút có thuộc tính data-close-modal
    const closeButtons = modal.querySelectorAll("[data-close-modal]");
    for (let i = 0; i < closeButtons.length; i++) {
        closeButtons[i].addEventListener("click", function () {
            closeModal(modal);
        });
    }

    // Bấm ra ngoài nền modal để đóng
    modal.addEventListener("click", function (e) {
        if (e.target === modal) {
            closeModal(modal);
        }
    });

    // Phím Escape để đóng
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape" && modal.classList.contains("is-open")) {
            closeModal(modal);
        }
    });
}

function openModal(modal) {
    modal.classList.add("is-open");
    modal.setAttribute("aria-hidden", "false");
    const focusable = modal.querySelector("button, [href], input, select, textarea");
    if (focusable) {
        focusable.focus();
    }
}

function closeModal(modal) {
    modal.classList.remove("is-open");
    modal.setAttribute("aria-hidden", "true");
    const btnOpen = document.getElementById("btnOpenCancelFreezeModal");
    if (btnOpen) {
        btnOpen.focus();
    }
}

/**
 * Định dạng đối tượng Date thành chuỗi dd/MM/yyyy.
 */
function formatDateVietnamese(date) {
    if (!date || isNaN(date.getTime())) return "--/--/----";
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();
    return day + "/" + month + "/" + year;
}

/**
 * Định dạng chuỗi YYYY-MM-DD thành dd/MM/yyyy.
 */
function formatDateString(str) {
    if (!str) return "";
    const parts = str.split("-");
    if (parts.length === 3) {
        return parts[2] + "/" + parts[1] + "/" + parts[0];
    }
    return str;
}

/**
 * Hàm escape HTML chống tấn công XSS trong JavaScript.
 */
function escapeHtml(text) {
    if (text === null || text === undefined) return "";
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}
