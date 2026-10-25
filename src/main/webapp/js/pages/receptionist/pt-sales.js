"use strict";

/*
 * BÁN GÓI PT — pt-sales.js
 *
 * JS CHỈ LÀM 1 VIỆC: trong form GET các bước (data-auto-submit), đổi radio gói / giờ, ô chọn PT, ô ngày
 * thì tự gửi form để server tính lại bước sau. Ô gõ chữ (tìm khách) KHÔNG tự gửi: Lễ tân bấm "Tìm".
 * Tắt JS vẫn bán được: mỗi khối có nút "Tiếp".
 *
 * Mọi tính toán (khách mua được không, giờ cố định, lịch từng ngày, giá/buổi) nằm ở PtSaleService.
 */

document.addEventListener("DOMContentLoaded", function () {
    const form = document.querySelector("form[data-auto-submit]");
    if (!form) {
        return;
    }
    form.addEventListener("change", function (event) {
        const field = event.target;
        const isChoice = field.matches("input[type=radio], select, input[type=date]");
        if (isChoice && field.value) {
            form.requestSubmit();
        }
    });
});
