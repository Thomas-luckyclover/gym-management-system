/* Màn chi tiết BF-05: mở/đóng ba hộp thoại và thu gọn lý do; không gửi AJAX. */
let modalTrigger = null;

// Đóng hộp thoại và trả focus cho nút đã mở.
function closeModal(id) {
    document.getElementById(id).classList.remove('show');
    if (modalTrigger) {
        modalTrigger.focus();
    }
}

// Các nút tác vụ chỉ mở hộp thoại; form POST do backend kiểm tra lại.
function openModal(id) {
    modalTrigger = document.activeElement;
    const modal = document.getElementById(id);
    modal.classList.add('show');
    const firstControl = modal.querySelector('button, select, textarea');
    if (firstControl) {
        firstControl.focus();
    }
}

function toggleReasonClamp() {
    const text = document.getElementById('reasonClampText');
    const button = document.getElementById('btnToggleReason');
    if (text.classList.contains('expanded')) {
        text.classList.remove('expanded');
        button.textContent = 'Xem thêm';
    } else {
        text.classList.add('expanded');
        button.textContent = 'Thu gọn';
    }
}

// Kiểm tra nếu nội dung lý do quá ngắn thì ẩn nút "Xem thêm"
window.addEventListener('DOMContentLoaded', function() {
    const text = document.getElementById('reasonClampText');
    const button = document.getElementById('btnToggleReason');
    if (text && button) {
        if (text.scrollHeight <= text.clientHeight + 2) {
            button.style.display = 'none';
        }
    }
});

window.addEventListener('keydown', function(event) {
    const modal = document.querySelector('.gms-modal.show');
    if (!modal) {
        return;
    }
    if (event.key === 'Escape') {
        closeModal(modal.id);
    }
    if (event.key === 'Tab') {
        const controls = modal.querySelectorAll('button:not([disabled]), select, textarea');
        const first = controls[0];
        const last = controls[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }
});

window.addEventListener('click', function(event) {
    if (event.target.classList.contains('gms-modal')) {
        closeModal(event.target.id);
    }
});
