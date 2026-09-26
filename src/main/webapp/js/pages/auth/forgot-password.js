/**
 * forgot-password.js — bước nhập mã của Quên mật khẩu (GBR-27, CBR-94).
 * Kiểm tra ô nhập, nút con mắt, khóa nút khi gửi: common.js (form[data-validate]).
 * File này chỉ lo: đồng hồ hạn mã, đếm ngược nút "Gửi lại mã", ô mã chỉ nhận số.
 * Số giây do SERVER tính sẵn (data-code-seconds, data-resend-seconds).
 */
document.addEventListener("DOMContentLoaded", function () {
    setupCodeTimer();
    setupResendButton();
    setupCodeInput();
});

// Đồng hồ "Mã hết hạn sau mm:ss" — về 0 thì đổi thành câu MSG44
function setupCodeTimer() {
    const timer = document.getElementById("codeTimer");
    const countdown = document.getElementById("codeCountdown");
    if (!timer || !countdown) {
        return;
    }

    const secondsLeft = Number(timer.dataset.codeSeconds);
    startCountdown(countdown, secondsLeft, function () {
        timer.classList.add("is-expired");
        document.getElementById("codeTimerText").textContent = MESSAGES.MSG44_CODE_EXPIRED;
    });
}

// Nút "Gửi lại mã" bị khóa tới khi đủ 60 giây kể từ lần gửi trước, hiện "(mm:ss)" bên cạnh
function setupResendButton() {
    const button = document.getElementById("resendBtn");
    const waitLabel = document.getElementById("resendWait");
    if (!button || !waitLabel) {
        return;
    }

    const secondsLeft = Number(button.dataset.resendSeconds);
    if (secondsLeft <= 0) {
        return;
    }

    button.disabled = true;
    const countdownText = document.createElement("span");
    waitLabel.textContent = " (";
    waitLabel.appendChild(countdownText);
    waitLabel.append(")");

    startCountdown(countdownText, secondsLeft, function () {
        button.disabled = false;
        waitLabel.textContent = "";
    });
}

// Ô mã: bỏ ký tự không phải số (dán "246 810" vẫn thành "246810")
function setupCodeInput() {
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
