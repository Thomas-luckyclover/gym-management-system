"use strict";

/*
 * QUÊN MẬT KHẨU — forgot-password.js (chỉ có việc ở BƯỚC 2 "Nhập mã")
 *
 * JS CHỈ LÀM 3 VIỆC:
 *   1. Đồng hồ "Mã hết hạn sau mm:ss" — về 0 thì đổi thành câu MSG44.
 *   2. Nút "Gửi lại mã" bị khóa tới khi đủ 60 giây kể từ lần gửi trước, hiện "(0:59)".
 *   3. Ô mã chỉ giữ chữ số (dán "246 810" vẫn thành "246810").
 *
 * Số giây do SERVER tính sẵn (data-code-seconds, data-resend-seconds) — không tin đồng hồ máy khách.
 * startCountdown / MESSAGES nằm ở common.js. Kiểm tra ô + nút con mắt: common.js (form[data-validate]).
 *
 * MỤC LỤC   initCodeTimer · initResendButton · initCodeInput   (đều là HÀM CHÍNH, gọi 1 lần khi trang tải xong)
 */

document.addEventListener("DOMContentLoaded", function () {
    initCodeTimer();
    initResendButton();
    initCodeInput();
});

// HÀM CHÍNH — 1. Đồng hồ hạn mã. Bước 1 / 3 không có #codeTimer → thoát.
function initCodeTimer() {
    const timer = document.getElementById("codeTimer");
    const countdown = document.getElementById("codeCountdown");
    if (!timer || !countdown) {
        return;
    }

    // B1: đếm lùi từ số giây server đưa xuống. B2: về 0 → tô đỏ + đổi câu.
    startCountdown(countdown, Number(timer.dataset.codeSeconds), function () {
        timer.classList.add("is-expired");
        document.getElementById("codeTimerText").textContent = MESSAGES.MSG44_CODE_EXPIRED;
    });
}

// HÀM CHÍNH — 2. Khóa nút "Gửi lại mã" tới khi được phép gửi.
function initResendButton() {
    const button = document.getElementById("resendBtn");
    const waitLabel = document.getElementById("resendWait");
    if (!button || !waitLabel) {
        return;
    }

    // B1: đã đủ 60 giây → nút dùng được ngay, không làm gì.
    const secondsLeft = Number(button.dataset.resendSeconds);
    if (secondsLeft <= 0) {
        return;
    }

    // B2: khóa nút, hiện " (0:59)" cạnh chữ.
    button.disabled = true;
    const countdownText = document.createElement("span");
    waitLabel.textContent = " (";
    waitLabel.appendChild(countdownText);
    waitLabel.append(")");

    // B3: hết giờ chờ → mở nút, xóa "(…)".
    startCountdown(countdownText, secondsLeft, function () {
        button.disabled = false;
        waitLabel.textContent = "";
    });
}

// HÀM CHÍNH — 3. Ô mã: mỗi lần gõ / dán thì bỏ ký tự không phải số, tối đa 6 số.
function initCodeInput() {
    const codeInput = document.getElementById("code");
    if (!codeInput) {
        return;
    }

    codeInput.addEventListener("input", function () {
        const digitsOnly = codeInput.value.replace(/\D/g, "").slice(0, 6);
        if (codeInput.value !== digitsOnly) {
            codeInput.value = digitsOnly;
        }
    });
}
