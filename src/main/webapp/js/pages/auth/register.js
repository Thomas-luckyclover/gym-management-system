/**
 * Client JavaScript cho màn hình Đăng ký tài khoản (Màn #2 - UC-05, UC-Claim).
 * Xử lý: Lọc ký tự số cho SĐT & OTP, đếm ngược thời gian hết hạn mã OTP và khóa nút gửi lại mã.
 * Các kiểm tra dữ liệu khác (định dạng, bắt buộc, khớp mật khẩu) do common.js (data-validate) xử lý.
 */
document.addEventListener("DOMContentLoaded", function () {
    initDigitsOnlyInputs();
    initOtpCountdown();
    initResendCountdown();
});

// 1. Chỉ cho nhập chữ số vào các ô SĐT và mã OTP
function initDigitsOnlyInputs() {
    const phoneInput = document.getElementById("phone");
    if (phoneInput) {
        phoneInput.addEventListener("input", function () {
            this.value = this.value.replace(/\D/g, "").slice(0, 10);
        });
    }

    const otpInputs = [document.getElementById("otpCode"), document.getElementById("claimOtpCode")];
    otpInputs.forEach(function (input) {
        if (input) {
            input.addEventListener("input", function () {
                this.value = this.value.replace(/\D/g, "").slice(0, 6);
            });
        }
    });
}

// 2. Đếm ngược thời gian hiệu lực mã OTP (15 phút)
function initOtpCountdown() {
    const countdownEl = document.getElementById("otpCountdown");
    if (!countdownEl) return;

    const expiresAtStr = countdownEl.getAttribute("data-expires");
    if (!expiresAtStr) {
        countdownEl.textContent = "15:00";
        return;
    }

    const expiresAt = new Date(expiresAtStr.replace("T", " "));

    function update() {
        const diffMs = expiresAt - new Date();
        if (diffMs <= 0) {
            countdownEl.textContent = "00:00 (đã hết hạn)";
            return;
        }
        const totalSeconds = Math.ceil(diffMs / 1000);
        const mm = String(Math.floor(totalSeconds / 60)).padStart(2, "0");
        const ss = String(totalSeconds % 60).padStart(2, "0");
        countdownEl.textContent = mm + ":" + ss;
        setTimeout(update, 1000);
    }
    update();
}

// 3. Khóa nút "Gửi lại mã" trong 60 giây
function initResendCountdown() {
    const resendBtn = document.getElementById("resendBtn") || document.getElementById("claimResendBtn");
    if (!resendBtn) return;

    const unlockAtStr = resendBtn.getAttribute("data-resend-unlock");
    if (!unlockAtStr) return;

    const unlockAt = new Date(unlockAtStr.replace("T", " "));

    function update() {
        const diffMs = unlockAt - new Date();
        if (diffMs <= 0) {
            resendBtn.disabled = false;
            resendBtn.textContent = "Gửi lại mã";
            return;
        }
        const secondsLeft = Math.ceil(diffMs / 1000);
        resendBtn.disabled = true;
        resendBtn.textContent = "Gửi lại sau " + secondsLeft + "s";
        setTimeout(update, 1000);
    }
    update();
}
