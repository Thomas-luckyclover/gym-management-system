/**
 * BF-07: mở hộp xóa/từ chối, kiểm tra lý do và xác nhận trước khi gửi form.
 * JSP cung cấp data-*; Service kiểm lại quyền và trạng thái khi POST.
 */
document.addEventListener('DOMContentLoaded', function () {
    // B1: nhiều nút trong danh sách cùng mở một hộp xóa.
    const deleteDialog = document.getElementById('deleteDialog');
    const deleteButtons = document.querySelectorAll('[data-delete-open]');
    for (const button of deleteButtons) {
        button.addEventListener('click', function () {
            openDeleteDialog(button, deleteDialog);
        });
    }

    // B2: từ chối chỉ mở hộp nhập lý do, chưa gửi request.
    const rejectDialog = document.getElementById('rejectDialog');
    const rejectButton = document.getElementById('openRejectDialog');
    if (rejectButton && rejectDialog) {
        rejectButton.addEventListener('click', function () {
            rejectDialog.showModal();
        });
    }
    const closeButtons = document.querySelectorAll('[data-dialog-close]');
    for (const button of closeButtons) {
        button.addEventListener('click', function () {
            button.closest('dialog').close();
        });
    }

    // B3: cùng kiểm tra lý do cho xóa từ danh sách, xóa từ chi tiết và từ chối.
    const forms = document.querySelectorAll('.bf07-reason-form');
    for (const form of forms) {
        const input = form.querySelector('textarea');
        updateReasonCounter(form);
        input.addEventListener('input', function () {
            updateReasonCounter(form);
            if (input.getAttribute('aria-invalid') === 'true') {
                validateReason(form);
            }
        });
        input.addEventListener('blur', function () {
            if (input.value.length > 0) {
                validateReason(form);
            }
        });
        form.addEventListener('submit', function (event) {
            handleReasonSubmit(event, form);
        });
    }

    // B4: duyệt phải xác nhận hậu quả, dùng hộp thoại chung của hệ thống.
    const approveForm = document.getElementById('approveForm');
    if (approveForm) {
        approveForm.addEventListener('submit', function (event) {
            if (approveForm.dataset.confirmed === 'true') {
                disableSubmit(approveForm);
                return;
            }
            event.preventDefault();
            confirmSubmission(approveForm, 'Duyệt xóa hồ sơ?',
                'Hồ sơ sẽ ngừng hoạt động và tài khoản bị đăng xuất. Gói, buổi tập và giao dịch được giữ nguyên để tra cứu.');
        });
    }

    // POST thất bại: mở lại hộp và giữ dữ liệu server trả về.
    const dialogs = document.querySelectorAll('dialog[data-reopen="true"]');
    for (const dialog of dialogs) {
        dialog.showModal();
    }
});

/** Điền tên bằng textContent, không biến tên hội viên thành HTML. */
function openDeleteDialog(button, dialog) {
    if (!dialog) {
        return;
    }
    document.getElementById('deleteCustomerId').value = button.dataset.customerId;
    document.getElementById('deleteCustomerName').textContent = button.dataset.customerName;
    document.getElementById('deleteCustomerCode').textContent = button.dataset.customerCode;
    const consequence = document.getElementById('deleteConsequence');
    const submit = document.getElementById('deleteSubmit');
    if (button.dataset.hasAssets === 'true') {
        consequence.textContent = 'Còn gói hoặc buổi PT. Yêu cầu sẽ gửi quản lý duyệt; hồ sơ chưa bị xóa ngay.';
        submit.textContent = 'Gửi yêu cầu xóa';
    } else {
        consequence.textContent = 'Không còn gói hoặc buổi PT. Xác nhận sẽ ngừng hồ sơ ngay; lịch sử vẫn được giữ lại.';
        submit.textContent = 'Xác nhận xóa';
    }
    const form = dialog.querySelector('form');
    const input = form.querySelector('textarea');
    input.value = '';
    input.removeAttribute('aria-invalid');
    form.querySelector('[data-reason-error]').textContent = '';
    updateReasonCounter(form);
    dialog.showModal();
}

/** Bộ đếm bỏ khoảng trắng đầu/cuối giống backend. */
function updateReasonCounter(form) {
    const input = form.querySelector('textarea');
    const counter = form.querySelector('[data-reason-count]');
    counter.textContent = input.value.trim().length;
}

/** Dùng luật lý do trong common.js, lỗi hiện ngay dưới ô. */
function validateReason(form) {
    const input = form.querySelector('textarea');
    const error = form.querySelector('[data-reason-error]');
    const message = getReasonErrorMessage(input.value.trim());
    error.textContent = message;
    input.setAttribute('aria-invalid', String(message !== ''));
    return message === '';
}

/** Hộp xóa/từ chối đã là bước xác nhận; chỉ form trên trang chi tiết cần hỏi thêm. */
function handleReasonSubmit(event, form) {
    if (!validateReason(form)) {
        event.preventDefault();
        form.querySelector('textarea').focus();
        return;
    }
    if (form.hasAttribute('data-confirm-delete') && form.dataset.confirmed !== 'true') {
        event.preventDefault();
        confirmSubmission(form, 'Xác nhận yêu cầu xóa?',
            'Hệ thống kiểm tra lại gói và lịch PT. Hồ sơ còn dịch vụ sẽ gửi quản lý duyệt.');
        return;
    }
    disableSubmit(form);
}

/** Cờ confirmed tránh mở hộp lặp khi requestSubmit() kiểm tra form lần hai. */
function confirmSubmission(form, title, message) {
    openConfirmDialog({
        title: title,
        message: message,
        confirmText: 'Xác nhận',
        cancelText: 'Hủy',
        isDanger: true,
        onConfirm: function () {
            form.dataset.confirmed = 'true';
            form.requestSubmit();
        }
    });
}

/** Khóa nút sau khi form hợp lệ để tránh bấm gửi liên tiếp. */
function disableSubmit(form) {
    const submit = form.querySelector('[type="submit"]');
    if (submit) {
        submit.disabled = true;
        submit.textContent = 'Đang xử lý…';
    }
}
