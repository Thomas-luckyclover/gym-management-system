document.addEventListener("DOMContentLoaded", initCheckout);

// Quay lại từ VNPay bằng Back: đọc lại trạng thái đơn từ server.
window.addEventListener("pageshow", function (event) {
    if (event.persisted) {
        window.location.reload();
    }
});

function initCheckout() {
    const form = document.getElementById("voucherForm");
    if (!form) {
        return;
    }

    const input = document.getElementById("voucherCode");
    const clear = document.getElementById("voucherClear");
    const pay = document.getElementById("payButton");
    const paymentForm = document.getElementById("paymentForm");
    const validIcon = document.getElementById("voucherValidIcon");

    let inFlight = null;
    let navigating = false;
    let confirmed = form.dataset.valid === "true";
    let editable = form.dataset.editable === "true";
    let checkedCode = confirmed ? input.value.trim() : null;

    function updatePayButton() {
        // Mã sai không khóa nút. Submit sẽ chờ request voucher nếu có.
        pay.disabled = navigating || !editable;
    }

    // B1: nhập mã mới thì bỏ dấu hợp lệ cũ trên giao diện.
    input.addEventListener("input", function () {
        confirmed = false;
        validIcon.hidden = true;
        setVoucherStatus("", "neutral");

        clear.hidden = input.value.trim() === ""
                && !form.dataset.applied;

        updatePayButton();
    });

    input.addEventListener("blur", function () {
        if (!confirmed) {
            checkVoucher(false);
        }
    });

    input.addEventListener("keydown", function (event) {
        if (event.key === "Enter") {
            event.preventDefault();
            checkVoucher(false);
        }
    });

    form.addEventListener("submit", function (event) {
        event.preventDefault();
        checkVoucher(false);
    });

    clear.addEventListener("mousedown", function (event) {
        // Tránh blur áp mã trước khi click gỡ mã.
        event.preventDefault();
    });

    clear.addEventListener("click", function () {
        checkVoucher(true);
    });

    // B2: dùng chung request đang chạy, tránh kiểm tra mã hai lần đồng thời.
    function checkVoucher(remove) {
        if (inFlight) {
            return inFlight;
        }

        if (!editable) {
            return Promise.resolve();
        }

        inFlight = performVoucherCheck(remove).finally(function () {
            inFlight = null;
        });

        return inFlight;
    }

    async function performVoucherCheck(remove) {
        const code = input.value.trim();

        // Không nhập mã và chưa áp mã thì không cần gọi server.
        if (!remove && code === "" && !form.dataset.applied) {
            confirmed = true;
            setVoucherStatus("", "neutral");
            return;
        }

        checkedCode = code;
        confirmed = false;

        input.disabled = true;
        clear.disabled = true;
        validIcon.hidden = true;

        setVoucherStatus("Đang kiểm tra mã…", "neutral");

        const action = remove || code === ""
                ? "remove-voucher"
                : "apply-voucher";

        let result;

        try {
            result = await postData("/customer/checkout", {
                order: form.elements.order.value,
                csrfToken: form.elements.csrfToken.value,
                action: action,
                voucherCode: code
            });

            if (result.data) {
                renderCheckoutAmounts(result.data);
                editable = result.data.voucherEditable;
                form.dataset.applied = result.data.voucherCode || "";
            }

            if (result.success && result.data) {
                input.value = result.data.voucherCode || "";
                confirmed = true;

                setVoucherStatus(
                        result.data.voucherMessage || "",
                        "success");
            } else {
                const fieldError = result.errors
                        && result.errors.voucherCode;

                setVoucherStatus(
                        fieldError || result.message
                        || "Chưa kiểm tra được mã. Vui lòng thử lại.",
                        "error");
            }

        } catch (error) {
            setVoucherStatus(
                    "Chưa kiểm tra được mã. Vui lòng thử lại.",
                    "error");

        } finally {
            input.disabled = !editable;
            clear.disabled = !editable;

            clear.hidden = input.value.trim() === ""
                    && !form.dataset.applied;

            validIcon.hidden = !confirmed || input.value === "";

            if (!editable && result && result.data) {
                setVoucherStatus(
                        result.data.voucherLockReason
                        || "Đơn không còn được đổi voucher.",
                        "error");
            }

            updatePayButton();
        }
    }

    // B3: chờ kiểm tra voucher xong rồi gửi POST thanh toán.
    paymentForm.addEventListener("submit", async function (event) {
        event.preventDefault();

        if (navigating || !editable) {
            return;
        }

        navigating = true;
        updatePayButton();

        try {
            if (inFlight) {
                await inFlight;
            } else if (input.value.trim() !== checkedCode) {
                await checkVoucher(false);
            }

            if (!editable) {
                navigating = false;
                updatePayButton();
                return;
            }

            pay.textContent = "Đang mở VNPay…";

            // Gửi form thật, không kích hoạt lại listener submit.
            // Form chỉ chứa order/action/csrfToken, không gửi số tiền.
            HTMLFormElement.prototype.submit.call(paymentForm);

        } catch (error) {
            navigating = false;
            updatePayButton();

            setVoucherStatus(
                    "Chưa gửi được yêu cầu. Vui lòng thử lại.",
                    "error");
        }
    });

    updatePayButton();
}

// JavaScript chỉ hiển thị số tiền do server trả về.
function renderCheckoutAmounts(data) {
    document.getElementById("discountAmount").textContent =
            data.discountText;

    document.getElementById("totalAmount").textContent =
            data.totalText;

    document.getElementById("vatAmount").textContent =
            data.vatText;

    document.getElementById("preTaxAmount").textContent =
            data.preTaxText;

    document.getElementById("discountRow").hidden =
            !data.voucherCode;
}

function setVoucherStatus(message, state) {
    const status = document.getElementById("voucherStatus");
    status.textContent = message;
    status.dataset.state = state;
}