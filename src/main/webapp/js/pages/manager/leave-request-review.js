/* Màn chi tiết BF-05: mở/đóng hộp Duyệt/Từ chối và thu gọn lý do; không gửi AJAX. */
// Dialog của trình duyệt tự giữ focus, xử lý Escape và trả focus khi đóng.
function closeModal(id) {
    document.getElementById(id).close();
}

// Các nút tác vụ chỉ mở hộp thoại; form POST do backend kiểm tra lại.
function openModal(id) {
    const modal = document.getElementById(id);
    modal.showModal();
    const firstControl = modal.querySelector('textarea, select') || modal.querySelector('button');
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
    const failedDialog = document.querySelector('dialog[data-reopen="true"]');
    if (failedDialog) {
        openModal(failedDialog.id);
    }
    const text = document.getElementById('reasonClampText');
    const button = document.getElementById('btnToggleReason');
    if (text && button) {
        if (text.scrollHeight <= text.clientHeight + 2) {
            button.style.display = 'none';
        }
    }
});
