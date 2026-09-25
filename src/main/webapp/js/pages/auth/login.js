/**
 * login.js — tương tác trang Đăng nhập, khớp prototype dang-nhap.js
 * (validate ô nhập, chuyển panel chưa có mật khẩu / tạm khoá).
 */
document.addEventListener("DOMContentLoaded", function () {
    setupLoginFormValidation();
    setupLoginStepBackButtons();
});

// Hiện lại form đăng nhập khi bấm "Thử lại" / "Quay lại" trên panel cảnh báo
function setupLoginStepBackButtons() {
    const backButtons = document.querySelectorAll("[data-back-form]");

    backButtons.forEach(function (button) {
        button.addEventListener("click", function (event) {
            event.preventDefault();
            showLoginStep("form");
        });
    });
}

function showLoginStep(stepName) {
    const panels = document.querySelectorAll(".step-panel");

    panels.forEach(function (panel) {
        const panelStep = panel.getAttribute("data-step");
        const shouldShow = panelStep === stepName;

        if (shouldShow) {
            panel.removeAttribute("hidden");
        } else {
            panel.setAttribute("hidden", "hidden");
        }
    });
}

// Kiểm tra identifier + mật khẩu trước khi gửi form POST /login
// Input: #identifier, #password
// Output: hiện lỗi dưới ô (identifierError / passwordError) hoặc cho submit
function setupLoginFormValidation() {
    const loginForm = document.getElementById("loginForm");

    if (!loginForm) {
        return;
    }

    const identifierInput = document.getElementById("identifier");
    const passwordInput = document.getElementById("password");

    loginForm.addEventListener("submit", function (event) {
        let isValid = true;

        const identifierValue = identifierInput ? identifierInput.value.trim() : "";
        const passwordValue = passwordInput ? passwordInput.value : "";

        clearLoginFieldError(identifierInput, "identifierError");
        clearLoginFieldError(passwordInput, "passwordError");

        if (!identifierValue) {
            showLoginFieldError(
                identifierInput,
                "identifierError",
                MESSAGES.MSG39_IDENTIFIER_REQUIRED
            );
            isValid = false;
        }

        if (!passwordValue) {
            showLoginFieldError(
                passwordInput,
                "passwordError",
                formatMessage(MESSAGES.MSG09_REQUIRED, ["Mật khẩu"])
            );
            isValid = false;
        }

        if (!isValid) {
            event.preventDefault();
        }
    });
}

function showLoginFieldError(inputElement, errorId, message) {
    if (inputElement) {
        inputElement.classList.add("invalid");
    }

    const errorElement = document.getElementById(errorId);

    if (!errorElement) {
        return;
    }

    errorElement.textContent = message;
    errorElement.removeAttribute("hidden");
}

function clearLoginFieldError(inputElement, errorId) {
    if (inputElement) {
        inputElement.classList.remove("invalid");
    }

    const errorElement = document.getElementById(errorId);

    if (!errorElement) {
        return;
    }

    errorElement.textContent = "";
    errorElement.setAttribute("hidden", "hidden");
}
