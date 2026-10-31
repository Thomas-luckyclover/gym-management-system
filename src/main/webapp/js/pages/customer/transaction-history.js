/* Lịch sử giao dịch: bấm một dòng thì mở popup (<dialog>) của giao dịch đó. */
(function () {
    'use strict';

    document.addEventListener('click', function (event) {
        // Mở popup: nút có data-th-open="id của dialog"
        var opener = event.target.closest('[data-th-open]');
        if (opener) {
            var dialog = document.getElementById(opener.getAttribute('data-th-open'));
            if (dialog && typeof dialog.showModal === 'function') {
                dialog.showModal();
            }
            return;
        }

        // Đóng popup: nút có data-th-close
        var closer = event.target.closest('[data-th-close]');
        if (closer) {
            var owner = closer.closest('dialog');
            if (owner) {
                owner.close();
            }
            return;
        }

        // Bấm ra vùng tối bên ngoài popup cũng đóng
        if (event.target.matches && event.target.matches('dialog.th-dialog')) {
            event.target.close();
        }
    });
})();