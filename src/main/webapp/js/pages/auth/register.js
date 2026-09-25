document.addEventListener("DOMContentLoaded", function () {
    setupPasswordToggleButtons();
    setupPhoneStep();
    setupInformationStep();
});

function setupPasswordToggleButtons() {
    const toggleButtons = document.querySelectorAll(".pw-toggle");

    toggleButtons.forEach(function (button) {
        button.addEventListener("click", function () {
            const targetId = button.getAttribute("data-toggle-target");
            const targetInput = document.getElementById(targetId);
            const icon = button.querySelector("i");

            if (!targetInput || !icon) {
                return;
            }

            const isPassword = targetInput.type === "password";

            targetInput.type = isPassword ? "text" : "password";

            icon.classList.toggle("fa-eye", !isPassword);
            icon.classList.toggle("fa-eye-slash", isPassword);
        });
    });
}

function setupPhoneStep() {
    const form = document.getElementById("registerPhoneForm");

    if (!form) {
        return;
    }

    const phoneInput = document.getElementById("phone");

    phoneInput.addEventListener("blur", function () {
        validatePhoneField(phoneInput);
    });

    form.addEventListener("submit", function (event) {
        if (!validatePhoneField(phoneInput)) {
            event.preventDefault();
        }
    });
}

function setupInformationStep() {
    const form = document.getElementById("registerForm");

    if (!form) {
        return;
    }

    const fullNameInput = document.getElementById("fullName");
    const dobInput = document.getElementById("dateOfBirth");
    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const confirmPasswordInput = document.getElementById("confirmPassword");

    fullNameInput.addEventListener("blur", function () {
        validateFullNameField(fullNameInput);
    });

    dobInput.addEventListener("blur", function () {
        validateDobField(dobInput);
    });

    emailInput.addEventListener("blur", function () {
        validateEmailField(emailInput);
    });

    passwordInput.addEventListener("blur", function () {
        validatePasswordField(passwordInput);
    });

    confirmPasswordInput.addEventListener("blur", function () {
        validateConfirmPasswordField(
            passwordInput,
            confirmPasswordInput
        );
    });

    form.addEventListener("submit", function (event) {
        const isNameValid = validateFullNameField(fullNameInput);
        const isDobValid = validateDobField(dobInput);
        const isEmailValid = validateEmailField(emailInput);
        const isPasswordValid = validatePasswordField(passwordInput);
        const isConfirmValid = validateConfirmPasswordField(
            passwordInput,
            confirmPasswordInput
        );

        const isFormValid =
            isNameValid
            && isDobValid
            && isEmailValid
            && isPasswordValid
            && isConfirmValid;

        if (!isFormValid) {
            event.preventDefault();
        }
    });
}

function setFieldError(input, errorElementId, message) {
    const errorElement = document.getElementById(errorElementId);

    if (!errorElement) {
        return;
    }

    input.classList.toggle("invalid", Boolean(message));
    errorElement.textContent = message || "";
}

function validatePhoneField(input) {
    const value = input.value.trim();

    if (value === "") {
        setFieldError(
            input,
            "phoneError",
            "Số điện thoại là bắt buộc."
        );
        return false;
    }

    if (!/^0\d{9}$/.test(value)) {
        setFieldError(
            input,
            "phoneError",
            "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0."
        );
        return false;
    }

    setFieldError(input, "phoneError", "");
    return true;
}

function validateFullNameField(input) {
    const value = input.value.trim();

    if (value === "") {
        setFieldError(
            input,
            "fullNameError",
            "Họ và tên là bắt buộc."
        );
        return false;
    }

    if (value.length < 2 || value.length > 50) {
        setFieldError(
            input,
            "fullNameError",
            "Họ và tên phải từ 2 đến 50 ký tự."
        );
        return false;
    }

    if (!/^[a-zA-ZÀ-ỹ\s'-]+$/.test(value)) {
        setFieldError(
            input,
            "fullNameError",
            "Họ tên chỉ gồm chữ cái, khoảng trắng, dấu nháy đơn hoặc gạch nối."
        );
        return false;
    }

    setFieldError(input, "fullNameError", "");
    return true;
}

function validateDobField(input) {
    const value = input.value;

    if (!value) {
        setFieldError(
            input,
            "dateOfBirthError",
            "Ngày sinh là bắt buộc."
        );
        return false;
    }

    const selectedDate = new Date(value);
    const today = new Date();

    today.setHours(0, 0, 0, 0);

    if (selectedDate > today) {
        setFieldError(
            input,
            "dateOfBirthError",
            "Ngày sinh không được ở tương lai."
        );
        return false;
    }

    const age = today.getFullYear() - selectedDate.getFullYear();

    if (age > 100) {
        setFieldError(
            input,
            "dateOfBirthError",
            "Ngày sinh không hợp lệ (tuổi không quá 100)."
        );
        return false;
    }

    setFieldError(input, "dateOfBirthError", "");
    return true;
}

function validateEmailField(input) {
    const value = input.value.trim();

    if (value === "") {
        setFieldError(
            input,
            "emailError",
            "Email là bắt buộc."
        );
        return false;
    }

    if (!/^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/.test(value)) {
        setFieldError(
            input,
            "emailError",
            "Email không đúng định dạng."
        );
        return false;
    }

    setFieldError(input, "emailError", "");
    return true;
}

function validatePasswordField(input) {
    const value = input.value;

    if (value.length < 8 || value.length > 64) {
        setFieldError(
            input,
            "passwordError",
            "Mật khẩu phải từ 8 đến 64 ký tự."
        );
        return false;
    }

    const hasLetter = /[a-zA-Z]/.test(value);
    const hasDigit = /\d/.test(value);

    if (!hasLetter || !hasDigit) {
        setFieldError(
            input,
            "passwordError",
            "Mật khẩu phải gồm cả chữ và số."
        );
        return false;
    }

    setFieldError(input, "passwordError", "");
    return true;
}

function validateConfirmPasswordField(passwordInput, confirmInput) {
    const password = passwordInput.value;
    const confirmPassword = confirmInput.value;

    if (confirmPassword !== password) {
        setFieldError(
            confirmInput,
            "confirmPasswordError",
            "Mật khẩu xác nhận không khớp."
        );
        return false;
    }

    setFieldError(confirmInput, "confirmPasswordError", "");
    return true;
}