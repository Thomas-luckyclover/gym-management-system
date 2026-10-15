// Checkout: gọi postData dùng chung; chỉ server tính discount/VAT và giữ lượt voucher.
// ID voucherForm/voucherCode/voucherStatus/voucherClear/payButton nối với checkout.jsp.
document.addEventListener("DOMContentLoaded", initCheckout);

function initCheckout() {
    const form = document.getElementById("voucherForm");
    if (!form) {
        return;
    }
    const input = document.getElementById("voucherCode");
    const clear = document.getElementById("voucherClear");
    const pay = document.getElementById("payButton");
    let processing = false;
    let confirmed = form.dataset.valid === "true";
    let editable = form.dataset.editable === "true";

    function updatePayButton() {
        pay.disabled = processing || !editable;
    }

    // B1: sửa input khóa thanh toán ngay, tránh gửi tiền trước khi mã mới được kiểm tra.
    input.addEventListener("input", function () {
        confirmed = false;
        document.getElementById("voucherValidIcon").hidden = true;
        setVoucherStatus("", "neutral");
        clear.hidden = input.value.trim() === "" && form.dataset.applied === "";
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
        // Không kích hoạt blur trước khi click dấu x, tránh gửi áp mã rồi lại gỡ mã.
        event.preventDefault();
    });
    clear.addEventListener("click", function () {
        checkVoucher(true);
    });

    // B2: mỗi lần chỉ gửi một mutation, khóa input tới lúc có kết quả để tránh request đua.
    async function checkVoucher(remove) {
        if (processing || !editable) {
            return;
        }
        if (!remove && input.value.trim() === "" && !form.dataset.applied) {
            confirmed = true;
            setVoucherStatus("", "neutral");
            updatePayButton();
            return;
        }
        processing = true;
        confirmed = false;
        input.disabled = true;
        clear.disabled = true;
        updatePayButton();
        document.getElementById("voucherValidIcon").hidden = true;
        setVoucherStatus("Đang kiểm tra mã…", "neutral");
        const code = input.value.trim();
        const action = remove || code === "" ? "remove-voucher" : "apply-voucher";
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
                setVoucherStatus(result.data.voucherMessage || "", "success");
            } else {
                const fieldError = result.errors && result.errors.voucherCode;
                setVoucherStatus(fieldError || result.message || "Chưa kiểm tra được mã. Vui lòng thử lại.", "error");
            }
        } catch (error) {
            setVoucherStatus("Chưa kiểm tra được mã. Vui lòng thử lại.", "error");
        } finally {
            processing = false;
            input.disabled = !editable;
            clear.disabled = !editable;
            clear.hidden = input.value.trim() === "" && form.dataset.applied === "";
            const validIcon = document.getElementById("voucherValidIcon");
            validIcon.hidden = !confirmed || input.value === "";
            updatePayButton();
            if (!editable && result && result.data) {
                setVoucherStatus(result.data.voucherLockReason || "Đơn không còn được đổi voucher.", "error");
            }
        }
    }

    // B3: gửi GET tới trang thanh toán tạm, không tạo giao dịch hoặc gọi VNPay.
    document.getElementById("paymentForm").addEventListener("submit", function (event) {
        if (processing || !editable) {
            event.preventDefault();
            return;
        }
        pay.disabled = true;
        pay.textContent = "Đang xử lý…";
    });
    updatePayButton();
}

// Giá chỉ lấy từ snapshot server, không tự tính từ input hay catalogue.
function renderCheckoutAmounts(data) {
    document.getElementById("discountAmount").textContent = data.discountText;
    document.getElementById("totalAmount").textContent = data.totalText;
    document.getElementById("vatAmount").textContent = data.vatText;
}

// textContent tránh XSS khi hiện message hoặc mã voucher trả về.
function setVoucherStatus(message, state) {
    const status = document.getElementById("voucherStatus");
    status.textContent = message;
    status.dataset.state = state;
}
