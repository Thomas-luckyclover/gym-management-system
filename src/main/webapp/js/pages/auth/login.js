/**
 * login.js — JavaScript xử lý tương tác cho trang Đăng nhập (#1 Sign In)
 */
document.addEventListener("DOMContentLoaded", function () {
    const togglePasswordBtn = document.getElementById("togglePasswordBtn");
    const passwordInput = document.getElementById("passwordInput");
    const loginForm = document.getElementById("loginForm");
    const identifierInput = document.getElementById("identifierInput");
    const identifierError = document.getElementById("identifierError");
    const passwordError = document.getElementById("passwordError");
    const generalErrorBanner = document.getElementById("generalErrorBanner");
    const generalErrorText = document.getElementById("generalErrorText");

    // Toggle Hiện / Ẩn mật khẩu
    if (togglePasswordBtn && passwordInput) {
        togglePasswordBtn.addEventListener("click", function () {
            const isPassword = passwordInput.getAttribute("type") === "password";
            passwordInput.setAttribute("type", isPassword ? "text" : "password");
            const icon = togglePasswordBtn.querySelector("i");
            if (icon) {
                icon.className = isPassword ? "fa-solid fa-eye-slash eye-icon" : "fa-solid fa-eye eye-icon";
            }
        });
    }

    // Validate form phía client trước khi submit
    if (loginForm) {
        loginForm.addEventListener("submit", function (e) {
            let isValid = true;

            // Xóa các thông báo lỗi cũ
            if (identifierError) identifierError.textContent = "";
            if (passwordError) passwordError.textContent = "";
            if (generalErrorBanner) generalErrorBanner.classList.add("hidden");

            const identifierVal = identifierInput ? identifierInput.value.trim() : "";
            const passwordVal = passwordInput ? passwordInput.value : "";

            if (!identifierVal) {
                if (identifierError) identifierError.textContent = "Nhập email hoặc số điện thoại.";
                if (identifierInput) identifierInput.closest(".form-group").classList.add("has-error");
                isValid = false;
            } else {
                if (identifierInput) identifierInput.closest(".form-group").classList.remove("has-error");
            }

            if (!passwordVal) {
                if (passwordError) passwordError.textContent = "Mật khẩu là bắt buộc.";
                if (passwordInput) passwordInput.closest(".form-group").classList.add("has-error");
                isValid = false;
            } else {
                if (passwordInput) passwordInput.closest(".form-group").classList.remove("has-error");
            }

            if (!isValid) {
                e.preventDefault();
            }
        });
    }
});
