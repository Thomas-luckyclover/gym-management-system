// ===== Xử lý giao diện trang Đăng ký tài khoản =====

document.addEventListener("DOMContentLoaded", function () {
    // B1: Khởi tạo các sự kiện cho form và nút toggle mật khẩu
    setupPasswordToggleButtons();
    setupRegisterFormValidation();
});

// ===== Xử lý nút hiện/ẩn mật khẩu =====
function setupPasswordToggleButtons() {
    const toggleButtons = document.querySelectorAll(".pw-toggle");

    toggleButtons.forEach(function (button) {
        button.addEventListener("click", function () {
            const targetId = button.getAttribute("data-toggle-target");
            const targetInput = document.getElementById(targetId);
            const icon = button.querySelector("i");

            if (!targetInput) {
                return;
            }

            const isPassword = targetInput.type === "password";
            if (isPassword) {
                targetInput.type = "text";
                icon.classList.remove("fa-eye");
                icon.classList.add("fa-eye-slash");
            } else {
                targetInput.type = "password";
                icon.classList.remove("fa-eye-slash");
                icon.classList.add("fa-eye");
            }
        });
    });
}

// ===== Xử lý kiểm tra form đăng ký ở client =====
function setupRegisterFormValidation() {
    const form = document.getElementById("registerForm");
    if (!form) {
        return;
    }

    const fullNameInput = document.getElementById("fullName");
    const dobInput = document.getElementById("dateOfBirth");
    const phoneInput = document.getElementById("phone");
    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const confirmPasswordInput = document.getElementById("confirmPassword");

    // Lắng nghe sự kiện blur từng ô để báo lỗi sớm
    fullNameInput.addEventListener("blur", function () {
        validateFullNameField(fullNameInput);
    });

    dobInput.addEventListener("blur", function () {
        validateDobField(dobInput);
    });

    phoneInput.addEventListener("blur", function () {
        validatePhoneField(phoneInput);
    });

    emailInput.addEventListener("blur", function () {
        validateEmailField(emailInput);
    });

    passwordInput.addEventListener("blur", function () {
        validatePasswordField(passwordInput);
    });

    confirmPasswordInput.addEventListener("blur", function () {
        validateConfirmPasswordField(passwordInput, confirmPasswordInput);
    });

    // Lắng nghe submit form
    form.addEventListener("submit", function (event) {
        const isNameValid = validateFullNameField(fullNameInput);
        const isDobValid = validateDobField(dobInput);
        const isPhoneValid = validatePhoneField(phoneInput);
        const isEmailValid = validateEmailField(emailInput);
        const isPassValid = validatePasswordField(passwordInput);
        const isConfirmValid = validateConfirmPasswordField(passwordInput, confirmPasswordInput);

        const isFormValid = isNameValid && isDobValid && isPhoneValid && isEmailValid && isPassValid && isConfirmValid;
        if (!isFormValid) {
            event.preventDefault();
        }
    });
}

// ===== Các hàm validate từng trường =====

function setFieldError(input, errorElementId, message) {
    const errorElement = document.getElementById(errorElementId);
    if (!errorElement) {
        return;
    }

    if (message) {
        input.classList.add("invalid");
        errorElement.textContent = message;
    } else {
        input.classList.remove("invalid");
        errorElement.textContent = "";
    }
}

function validateFullNameField(input) {
    const value = input.value.trim();
    if (value === "") {
        setFieldError(input, "fullNameError", "Họ và tên là bắt buộc.");
        return false;
    }
    if (value.length < 2 || value.length > 50) {
        setFieldError(input, "fullNameError", "Họ và tên phải từ 2 đến 50 ký tự.");
        return false;
    }
    const nameRegex = /^[a-zA-ZÀ-ỹ\s'-]+$/;
    if (!nameRegex.test(value)) {
        setFieldError(input, "fullNameError", "Họ tên chỉ gồm chữ cái, khoảng trắng, dấu nháy đơn hoặc gạch nối.");
        return false;
    }
    setFieldError(input, "fullNameError", "");
    return true;
}

function validateDobField(input) {
    const value = input.value;
    if (!value) {
        setFieldError(input, "dateOfBirthError", "Ngày sinh là bắt buộc.");
        return false;
    }
    const selectedDate = new Date(value);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    if (selectedDate > today) {
        setFieldError(input, "dateOfBirthError", "Ngày sinh không được ở tương lai.");
        return false;
    }

    const age = today.getFullYear() - selectedDate.getFullYear();
    if (age > 100) {
        setFieldError(input, "dateOfBirthError", "Ngày sinh không hợp lệ (tuổi không quá 100).");
        return false;
    }

    setFieldError(input, "dateOfBirthError", "");
    return true;
}

function validatePhoneField(input) {
    const value = input.value.trim();
    if (value === "") {
        setFieldError(input, "phoneError", "Số điện thoại là bắt buộc.");
        return false;
    }
    const phoneRegex = /^0\d{9}$/;
    if (!phoneRegex.test(value)) {
        setFieldError(input, "phoneError", "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.");
        return false;
    }
    setFieldError(input, "phoneError", "");
    return true;
}

function validateEmailField(input) {
    const value = input.value.trim();
    if (value === "") {
        setFieldError(input, "emailError", "Email là bắt buộc.");
        return false;
    }
    const emailRegex = /^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/;
    if (!emailRegex.test(value)) {
        setFieldError(input, "emailError", "Email không đúng định dạng.");
        return false;
    }
    setFieldError(input, "emailError", "");
    return true;
}

function validatePasswordField(input) {
    const value = input.value;
    if (value.length < 8 || value.length > 64) {
        setFieldError(input, "passwordError", "Mật khẩu phải từ 8 đến 64 ký tự.");
        return false;
    }
    const hasLetter = /[a-zA-Z]/.test(value);
    const hasDigit = /\d/.test(value);
    if (!hasLetter || !hasDigit) {
        setFieldError(input, "passwordError", "Mật khẩu phải gồm cả chữ và số.");
        return false;
    }
    setFieldError(input, "passwordError", "");
    return true;
}

function validateConfirmPasswordField(passwordInput, confirmInput) {
    const password = passwordInput.value;
    const confirm = confirmInput.value;
    if (confirm !== password) {
        setFieldError(confirmInput, "confirmPasswordError", "Mật khẩu xác nhận không khớp.");
        return false;
    }
    setFieldError(confirmInput, "confirmPasswordError", "");
    return true;
}