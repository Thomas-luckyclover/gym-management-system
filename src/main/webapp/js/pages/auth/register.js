// ===== Đăng ký tài khoản: validate form phía trình duyệt =====
// File này chỉ xử lý validate client-side và countdown OTP.
// Tất cả kiểm tra quan trọng (trùng SĐT/email, mã OTP) đều do server quyết định.

document.addEventListener("DOMContentLoaded", function () {
    // B1: kích hoạt toggle con mắt mật khẩu (data-password-toggle từ common.js)
    // common.js đã tự bind data-password-toggle, không cần viết lại ở đây

    // B2: gắn validate cho form bước 1 (nhập SĐT)
    setupPhoneStep();

    // B3: gắn validate cho form bước 2 (điền thông tin — đăng ký mới)
    setupFormStep();

    // B4: khởi tạo countdown OTP nếu đang ở màn OTP (cả 2 nhánh)
    setupOtpStep();

    // B5: khởi tạo nút Gửi lại mã nếu đang ở màn OTP (cả 2 nhánh)
    setupResendButton();

    // B6: gắn validate cho form claim-email (nhánh Claim bước 2c)
    setupClaimEmailStep();

    // B7: gắn validate cho form claim-password (nhánh Claim bước 4c)
    setupClaimPasswordStep();
});

// ===== Bước 1: Validate SĐT trước khi submit =====

// Gắn validate client-side cho form nhập số điện thoại bước 1
function setupPhoneStep() {
    const form = document.getElementById("registerPhoneForm");

    if (!form) {
        return;
    }

    const phoneInput = document.getElementById("phone");

    // Chỉ cho nhập chữ số
    phoneInput.addEventListener("input", function () {
        this.value = this.value.replace(/\D/g, "");
    });

    // Kiểm tra khi rời ô (GBR-17)
    phoneInput.addEventListener("blur", function () {
        validatePhoneField(phoneInput);
    });

    // Kiểm tra khi bấm "Tiếp tục"
    form.addEventListener("submit", function (event) {
        const isValid = validatePhoneField(phoneInput);
        if (!isValid) {
            event.preventDefault();
        }
    });
}

// ===== Bước 2: Validate form điền thông tin (đăng ký mới) =====

// Gắn validate client-side cho tất cả ô nhập ở bước 2
function setupFormStep() {
    const form = document.getElementById("registerForm");

    if (!form) {
        return;
    }

    const emailInput = document.getElementById("email");
    const passwordInput = document.getElementById("password");
    const confirmPasswordInput = document.getElementById("confirmPassword");
    const fullNameInput = document.getElementById("fullName");
    const dobInput = document.getElementById("dateOfBirth");

    // Kiểm tra từng ô khi rời ô (GBR-17)
    emailInput.addEventListener("blur", function () {
        validateEmailField(emailInput);
    });

    passwordInput.addEventListener("blur", function () {
        validatePasswordField(passwordInput);
    });

    confirmPasswordInput.addEventListener("blur", function () {
        validateConfirmPasswordField(passwordInput, confirmPasswordInput);
    });

    fullNameInput.addEventListener("blur", function () {
        validateFullNameField(fullNameInput);
    });

    dobInput.addEventListener("blur", function () {
        validateDobField(dobInput);
    });

    // Kiểm tra toàn bộ khi bấm "Tạo tài khoản"
    form.addEventListener("submit", function (event) {
        const isEmailValid = validateEmailField(emailInput);
        const isPasswordValid = validatePasswordField(passwordInput);
        const isConfirmValid = validateConfirmPasswordField(passwordInput, confirmPasswordInput);
        const isNameValid = validateFullNameField(fullNameInput);
        const isDobValid = validateDobField(dobInput);

        const isFormValid =
            isEmailValid
            && isPasswordValid
            && isConfirmValid
            && isNameValid
            && isDobValid;

        if (!isFormValid) {
            event.preventDefault();
        }
    });
}

// ===== Claim bước 2c: Validate form email (chỉ 1 field) =====

function setupClaimEmailStep() {
    const form = document.getElementById("claimEmailForm");

    if (!form) {
        return;
    }

    const emailInput = document.getElementById("claimEmail");

    emailInput.addEventListener("blur", function () {
        validateClaimEmailField(emailInput);
    });

    form.addEventListener("submit", function (event) {
        const isValid = validateClaimEmailField(emailInput);
        if (!isValid) {
            event.preventDefault();
        }
    });
}

// Validate email cho nhánh Claim (dùng error element riêng)
function validateClaimEmailField(input) {
    const value = input.value.trim();

    if (value === "") {
        setFieldError(input, "claimEmailError", "Email là bắt buộc.");
        return false;
    }

    if (!/^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/.test(value)) {
        setFieldError(input, "claimEmailError", "Email không đúng định dạng.");
        return false;
    }

    if (value.length > 100) {
        setFieldError(input, "claimEmailError", "Email không được vượt quá 100 ký tự.");
        return false;
    }

    setFieldError(input, "claimEmailError", "");
    return true;
}

// ===== Claim bước 4c: Validate form đặt mật khẩu =====

function setupClaimPasswordStep() {
    const form = document.getElementById("claimPasswordForm");

    if (!form) {
        return;
    }

    const passwordInput = document.getElementById("claimPassword");
    const confirmPasswordInput = document.getElementById("claimConfirmPassword");

    passwordInput.addEventListener("blur", function () {
        validateClaimPasswordField(passwordInput);
    });

    confirmPasswordInput.addEventListener("blur", function () {
        validateClaimConfirmPasswordField(passwordInput, confirmPasswordInput);
    });

    form.addEventListener("submit", function (event) {
        const isPasswordValid = validateClaimPasswordField(passwordInput);
        const isConfirmValid = validateClaimConfirmPasswordField(passwordInput, confirmPasswordInput);

        if (!isPasswordValid || !isConfirmValid) {
            event.preventDefault();
        }
    });
}

function validateClaimPasswordField(input) {
    const value = input.value;

    if (value === "") {
        setFieldError(input, "claimPasswordError", "Mật khẩu là bắt buộc.");
        return false;
    }

    if (value.length < 8 || value.length > 64) {
        setFieldError(input, "claimPasswordError", "Mật khẩu phải từ 8 đến 64 ký tự.");
        return false;
    }

    const hasLetter = /[a-zA-Z]/.test(value);
    const hasDigit = /\d/.test(value);

    if (!hasLetter || !hasDigit) {
        setFieldError(input, "claimPasswordError", "Mật khẩu phải có ít nhất 1 chữ cái và 1 chữ số.");
        return false;
    }

    setFieldError(input, "claimPasswordError", "");
    return true;
}

function validateClaimConfirmPasswordField(passwordInput, confirmInput) {
    const confirmValue = confirmInput.value;

    if (confirmValue === "") {
        setFieldError(confirmInput, "claimConfirmPasswordError", "Vui lòng nhập lại mật khẩu.");
        return false;
    }

    if (confirmValue !== passwordInput.value) {
        setFieldError(confirmInput, "claimConfirmPasswordError", "Mật khẩu xác nhận không khớp.");
        return false;
    }

    setFieldError(confirmInput, "claimConfirmPasswordError", "");
    return true;
}

// ===== Bước 3: OTP countdown =====

// Khởi tạo đồng hồ đếm ngược thời gian hết hạn mã OTP (15 phút)
function setupOtpStep() {
    const countdownEl = document.getElementById("otpCountdown");

    if (!countdownEl) {
        return;
    }

    // Lấy thời điểm hết hạn từ attribute (ISO 8601, múi giờ server UTC+7)
    const expiresAtStr = countdownEl.getAttribute("data-expires");

    if (!expiresAtStr) {
        countdownEl.textContent = "15:00";
        return;
    }

    // Chuyển chuỗi ISO sang millisecond (server trả về giờ UTC+7, trình duyệt đọc local time)
    const expiresAt = new Date(expiresAtStr.replace("T", " "));

    // Cập nhật countdown mỗi giây
    function updateCountdown() {
        const now = new Date();
        const diffMs = expiresAt - now;

        if (diffMs <= 0) {
            countdownEl.textContent = "00:00 (đã hết hạn)";
            return;
        }

        const totalSeconds = Math.ceil(diffMs / 1000);
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;

        const mm = String(minutes).padStart(2, "0");
        const ss = String(seconds).padStart(2, "0");

        countdownEl.textContent = mm + ":" + ss;

        setTimeout(updateCountdown, 1000);
    }

    updateCountdown();
}

// ===== Nút Gửi lại mã: khóa 60 giây kể từ khi vào trang OTP =====

// Khóa nút "Gửi lại mã" và đếm ngược đến khi được phép bấm lại
// Hỗ trợ cả 2 nhánh: resendBtn (đăng ký mới) và claimResendBtn (Claim)
function setupResendButton() {
    // Tìm nút resend trên trang (có thể là 1 trong 2 nhánh)
    const resendBtn = document.getElementById("resendBtn")
                   || document.getElementById("claimResendBtn");

    if (!resendBtn) {
        return;
    }

    // Lấy thời điểm được phép bấm lại từ attribute (server tính sẵn = now + 60s)
    const unlockAtStr = resendBtn.getAttribute("data-resend-unlock");

    if (!unlockAtStr) {
        return;
    }

    const unlockAt = new Date(unlockAtStr.replace("T", " "));

    // Cập nhật trạng thái nút mỗi giây
    function updateResendButton() {
        const now = new Date();
        const diffMs = unlockAt - now;

        if (diffMs <= 0) {
            // Đã qua thời gian chờ → mở khóa
            resendBtn.disabled = false;
            resendBtn.textContent = "Gửi lại mã";
            return;
        }

        const secondsLeft = Math.ceil(diffMs / 1000);

        resendBtn.disabled = true;
        resendBtn.textContent = "Gửi lại sau " + secondsLeft + "s";

        setTimeout(updateResendButton, 1000);
    }

    updateResendButton();
}

// ===== Hàm validate từng ô =====

// Hiện hoặc xóa lỗi dưới một ô nhập
function setFieldError(input, errorElementId, message) {
    const errorElement = document.getElementById(errorElementId);

    if (!errorElement) {
        return;
    }

    const hasError = Boolean(message);

    input.classList.toggle("invalid", hasError);
    errorElement.textContent = message || "";

    if (hasError) {
        errorElement.removeAttribute("hidden");
    } else {
        errorElement.setAttribute("hidden", "");
    }
}

// Validate định dạng SĐT: 10 chữ số, bắt đầu 0
function validatePhoneField(input) {
    const value = input.value.trim();

    if (value === "") {
        setFieldError(input, "phoneError", "Số điện thoại là bắt buộc.");
        return false;
    }

    if (!/^0\d{9}$/.test(value)) {
        setFieldError(input, "phoneError", "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.");
        return false;
    }

    setFieldError(input, "phoneError", "");
    return true;
}

// Validate email: bắt buộc, đúng định dạng, tối đa 100 ký tự
function validateEmailField(input) {
    const value = input.value.trim();

    if (value === "") {
        setFieldError(input, "emailError", "Email là bắt buộc.");
        return false;
    }

    if (!/^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$/.test(value)) {
        setFieldError(input, "emailError", "Email không đúng định dạng.");
        return false;
    }

    if (value.length > 100) {
        setFieldError(input, "emailError", "Email không được vượt quá 100 ký tự.");
        return false;
    }

    setFieldError(input, "emailError", "");
    return true;
}

// Validate mật khẩu: 8–64 ký tự, có ít nhất 1 chữ cái và 1 chữ số (DBR-02)
function validatePasswordField(input) {
    const value = input.value;

    if (value === "") {
        setFieldError(input, "passwordError", "Mật khẩu là bắt buộc.");
        return false;
    }

    if (value.length < 8 || value.length > 64) {
        setFieldError(input, "passwordError", "Mật khẩu phải từ 8 đến 64 ký tự.");
        return false;
    }

    const hasLetter = /[a-zA-Z]/.test(value);
    const hasDigit = /\d/.test(value);

    if (!hasLetter || !hasDigit) {
        setFieldError(input, "passwordError", "Mật khẩu phải có ít nhất 1 chữ cái và 1 chữ số.");
        return false;
    }

    setFieldError(input, "passwordError", "");
    return true;
}

// Validate xác nhận mật khẩu: phải khớp với ô mật khẩu
function validateConfirmPasswordField(passwordInput, confirmInput) {
    const confirmValue = confirmInput.value;

    if (confirmValue === "") {
        setFieldError(confirmInput, "confirmPasswordError", "Vui lòng nhập lại mật khẩu.");
        return false;
    }

    if (confirmValue !== passwordInput.value) {
        setFieldError(confirmInput, "confirmPasswordError", "Mật khẩu xác nhận không khớp.");
        return false;
    }

    setFieldError(confirmInput, "confirmPasswordError", "");
    return true;
}

// Validate họ tên: 2–50 ký tự, chỉ chữ cái (có dấu tiếng Việt), khoảng trắng, nháy, gạch nối (DBR-01)
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

    if (!/^[a-zA-ZÀ-ỹ\s'-]+$/.test(value)) {
        setFieldError(input, "fullNameError", "Họ tên chỉ gồm chữ cái, khoảng trắng, dấu nháy đơn hoặc gạch nối.");
        return false;
    }

    setFieldError(input, "fullNameError", "");
    return true;
}

// Validate ngày sinh: bắt buộc, không ở tương lai, tuổi không quá 100 (DBR-04)
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

    const ageYears = today.getFullYear() - selectedDate.getFullYear();

    if (ageYears > 100) {
        setFieldError(input, "dateOfBirthError", "Ngày sinh không hợp lệ (tuổi không quá 100).");
        return false;
    }

    setFieldError(input, "dateOfBirthError", "");
    return true;
}