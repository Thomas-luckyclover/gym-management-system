"use strict";

/*
 * BÁN GÓI TẠI QUẦY — counter-sale.js (SRS #37)
 *
 * File dùng cho 3 màn, mỗi phần tự kiểm tra phần tử của mình có trên trang không:
 *   1. Màn Chọn gói (counter-sale-packages.jsp): ô tìm gói lọc thẻ ngay trên trình duyệt, bấm thẻ để chọn,
 *      thanh "Đang chọn" ở dưới, nút "Tiếp tục".
 *   2. Màn Chọn khách (counter-sale-customers.jsp): ô tìm khách gọi AJAX, vẽ bảng kết quả.
 *   3. Màn Xác nhận & Thu tiền (counter-sale-next.jsp): chọn phương thức thanh toán, chọn ngày bắt đầu real-time.
 *
 * Dùng chung từ common.js: normalizeSearchText, formatMoney, getJson, getContextPath.
 */

const CUSTOMER_SEARCH_URL = "/receptionist/sales/customers";
const VOUCHER_CHECK_URL = "/receptionist/sales/voucher";
const CUSTOMER_SEARCH_DELAY = 250;     // chờ 0,25 giây sau lần gõ cuối rồi mới gọi server
const CUSTOMER_MIN_LENGTH = 2;         // khớp MIN_SEARCH_LENGTH trong CounterSaleService
const CUSTOMER_HINT_TEXT = "Gõ tên, số điện thoại hoặc mã KH để tìm khách.";
const CUSTOMER_HINT_SUB = 'Tên không cần dấu: "nguyen van an", "0901", "KH-0102" đều được.';

// Thẻ gói đang chọn (null = chưa chọn). Đặt ở đầu file vì nhiều hàm của màn Chọn gói cùng đọc.
let selectedPackageCard = null;

// Bộ đếm lần gọi tìm khách: chỉ vẽ kết quả của lần gọi mới nhất, bỏ kết quả về trễ của lần cũ.
let customerRequestNo = 0;
let customerSearchTimer = 0;

document.addEventListener("DOMContentLoaded", function () {
    initPackagePage();
    initCustomerPage();
    initCheckoutPage();
});


/* ==================== 1. MÀN CHỌN GÓI ==================== */

// Gắn sự kiện cho màn Chọn gói. Không có #packageSearch nghĩa là đang ở màn khác, thoát luôn.
// Điểm nối với JSP: #packageSearch (ô tìm), input.counter-card-radio (chọn thẻ), #continueButton (nút Tiếp tục).
function initPackagePage() {
    const packageSearch = document.getElementById("packageSearch");
    if (packageSearch === null) {
        return;
    }
    const continueButton = document.getElementById("continueButton");
    const radios = document.querySelectorAll(".counter-card-radio");

    packageSearch.addEventListener("input", filterPackageCards);
    continueButton.addEventListener("click", handleContinueClick);
    for (const radio of radios) {
        radio.addEventListener("change", handlePackageChange);
    }
}

// Lọc thẻ theo chữ trong ô tìm, không phân biệt hoa thường và dấu, trên cả 3 tab.
// B1: lấy chữ gõ đã chuẩn hóa
// B2: mỗi tab: thẻ nào tên chứa chữ gõ thì hiện, còn lại ẩn
// B3: tab có thẻ nhưng không thẻ nào khớp thì hiện chữ "Không có gói nào khớp"
function filterPackageCards() {
    // B1: lấy chữ gõ
    const packageSearch = document.getElementById("packageSearch");
    const keyword = normalizeSearchText(packageSearch.value);
    const panels = document.querySelectorAll("[data-tab-panel]");

    for (const panel of panels) {
        // B2: lọc thẻ
        const cards = panel.querySelectorAll(".counter-card");
        let visibleCount = 0;
        for (const card of cards) {
            const cardName = normalizeSearchText(card.dataset.packageName);
            const isMatch = cardName.includes(keyword);
            card.hidden = !isMatch;
            if (isMatch) {
                visibleCount = visibleCount + 1;
            }
        }

        // B3: hiện thông báo nếu không khớp
        const hasCards = cards.length > 0;
        const isNoMatch = hasCards && visibleCount === 0;
        showFilterEmpty(panel, isNoMatch, packageSearch.value.trim());
    }
}

// Hiện / ẩn dòng báo "Không có gói nào khớp" trong một tab.
function showFilterEmpty(panel, isNoMatch, keywordText) {
    const emptyBox = panel.querySelector("[data-filter-empty]");
    if (emptyBox === null) {
        return;
    }
    emptyBox.hidden = !isNoMatch;
    if (!isNoMatch) {
        return;
    }

    emptyBox.textContent = "";
    const message = document.createTextNode('Không có gói nào khớp "' + keywordText + '".');
    const hint = document.createElement("span");
    hint.className = "counter-hint";
    hint.textContent = "Thử gõ ngắn hơn, ví dụ \"3 tháng\" hoặc \"vé ngày\".";
    emptyBox.appendChild(message);
    emptyBox.appendChild(hint);
}

// Bấm một thẻ: đánh dấu thẻ đó, bỏ đánh dấu thẻ cũ, cập nhật thanh dưới.
function handlePackageChange(event) {
    const radio = event.target;
    const newCard = radio.closest(".counter-card");

    if (selectedPackageCard !== null) {
        selectedPackageCard.classList.remove("is-selected");
    }
    newCard.classList.add("is-selected");
    selectedPackageCard = newCard;
    updateSelectedBar();
}

// Ghi thanh dưới: "Đang chọn: Gói 3 Tháng · 2.190.000đ" và bật nút Tiếp tục.
function updateSelectedBar() {
    const selectedSummary = document.getElementById("selectedSummary");
    const continueButton = document.getElementById("continueButton");
    if (selectedSummary === null || continueButton === null) {
        return;
    }

    if (selectedPackageCard === null) {
        selectedSummary.textContent = "Chưa chọn gói nào";
        continueButton.disabled = true;
        return;
    }

    const packageName = selectedPackageCard.dataset.packageName;
    const priceText = formatMoney(selectedPackageCard.dataset.packagePrice);
    selectedSummary.textContent = "Đang chọn: " + packageName + " · " + priceText;
    continueButton.textContent = selectedPackageCard.dataset.continueLabel;
    continueButton.disabled = false;
}

// Bấm Tiếp tục: chuyển sang địa chỉ đã gắn sẵn trên thẻ.
function handleContinueClick() {
    if (selectedPackageCard === null) {
        return;
    }
    window.location.href = selectedPackageCard.dataset.continueUrl;
}


/* ==================== 2. MÀN CHỌN KHÁCH (AJAX) ==================== */

// Gắn sự kiện cho ô tìm khách. Không có #customerSearch nghĩa là đang ở màn khác, thoát luôn.
function initCustomerPage() {
    const customerSearch = document.getElementById("customerSearch");
    if (customerSearch === null) {
        return;
    }
    customerSearch.addEventListener("input", handleCustomerInput);

    // Quay lại bằng nút Back, trình duyệt giữ chữ đã gõ: tìm lại luôn.
    const hasText = customerSearch.value.trim() !== "";
    if (hasText) {
        handleCustomerInput();
    }

    initQuickCustomerModal();
}

// Khởi tạo Modal tạo nhanh hồ sơ khách hàng tại Step 2
function initQuickCustomerModal() {
    const btnOpen = document.getElementById("btnOpenQuickCustomerModal");
    const backdrop = document.getElementById("quickCustomerBackdrop");
    const btnClose = document.getElementById("btnCloseQuickModal");
    const btnCancel = document.getElementById("btnCancelQuickModal");
    const form = document.getElementById("formQuickCustomer");
    if (backdrop === null || form === null) {
        return;
    }

    if (btnOpen !== null) {
        btnOpen.addEventListener("click", function () {
            openQuickCustomerModal();
        });
    }

    if (btnClose !== null) {
        btnClose.addEventListener("click", closeQuickCustomerModal);
    }
    if (btnCancel !== null) {
        btnCancel.addEventListener("click", closeQuickCustomerModal);
    }

    backdrop.addEventListener("click", function (e) {
        if (e.target === backdrop) {
            closeQuickCustomerModal();
        }
    });

    // Chuyển đổi giữa loại Self và Dependent
    const radioTypes = form.querySelectorAll('input[name="customerType"]');
    const wrapPhone = document.getElementById("wrapPhone");
    const qcPhone = document.getElementById("qcPhone");
    const wrapGuardian = document.getElementById("wrapGuardian");
    for (const radio of radioTypes) {
        radio.addEventListener("change", function () {
            const isDep = this.value === "Dependent";
            if (wrapGuardian !== null) {
                wrapGuardian.hidden = !isDep;
            }
            if (wrapPhone !== null) {
                wrapPhone.hidden = isDep;
            }
            if (qcPhone !== null) {
                qcPhone.required = !isDep;
            }
        });
    }

    // Submit form tạo nhanh khách hàng
    form.addEventListener("submit", handleQuickCustomerSubmit);
}

function openQuickCustomerModal(prefillPhone) {
    const backdrop = document.getElementById("quickCustomerBackdrop");
    const form = document.getElementById("formQuickCustomer");
    const alertBox = document.getElementById("quickModalAlert");
    if (backdrop === null || form === null) {
        return;
    }

    // Reset lỗi
    clearQuickModalErrors();
    if (alertBox !== null) {
        alertBox.hidden = true;
        alertBox.textContent = "";
    }

    // Prefill số điện thoại nếu được truyền vào hoặc lấy từ ô tìm kiếm (nếu là số)
    const qcPhone = document.getElementById("qcPhone");
    const searchVal = document.getElementById("customerSearch") ? document.getElementById("customerSearch").value.trim() : "";
    let initialPhone = prefillPhone;
    if (!initialPhone && /^0\d{9}$/.test(searchVal.replace(/\D/g, ""))) {
        initialPhone = searchVal.replace(/\D/g, "");
    }
    if (qcPhone !== null && initialPhone) {
        qcPhone.value = initialPhone;
    }

    backdrop.hidden = false;
    const nameInput = document.getElementById("qcFullName");
    if (nameInput !== null) {
        nameInput.focus();
    }
}

function closeQuickCustomerModal() {
    const backdrop = document.getElementById("quickCustomerBackdrop");
    if (backdrop !== null) {
        backdrop.hidden = true;
    }
}

function clearQuickModalErrors() {
    const errorSpans = document.querySelectorAll(".quick-field-error");
    for (const span of errorSpans) {
        span.hidden = true;
        span.textContent = "";
    }
}

async function handleQuickCustomerSubmit(e) {
    e.preventDefault();
    clearQuickModalErrors();
    const alertBox = document.getElementById("quickModalAlert");
    if (alertBox !== null) {
        alertBox.hidden = true;
    }

    const form = document.getElementById("formQuickCustomer");
    const submitBtn = document.getElementById("btnSubmitQuickCustomer");
    if (form === null || submitBtn === null) {
        return;
    }

    const formData = new FormData(form);
    const params = new URLSearchParams();
    for (const pair of formData.entries()) {
        params.append(pair[0], pair[1]);
    }

    submitBtn.disabled = true;
    const originalText = submitBtn.innerHTML;
    submitBtn.innerHTML = '<i class="fa-solid fa-spinner fa-spin" aria-hidden="true"></i> Đang xử lý…';

    try {
        const response = await postData(CUSTOMER_SEARCH_URL, params);
        if (!response.success) {
            if (response.errors && Object.keys(response.errors).length > 0) {
                for (const field in response.errors) {
                    const errEl = document.getElementById("err_" + field);
                    if (errEl !== null) {
                        errEl.textContent = response.errors[field];
                        errEl.hidden = false;
                    }
                }
            }
            if (response.message) {
                if (alertBox !== null) {
                    alertBox.textContent = response.message;
                    alertBox.hidden = false;
                }
            }
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;
            return;
        }

        // Tạo thành công: nhận về CounterCustomerRow, chuyển thẳng sang Bước 3 (Thu tiền)
        const newCustomer = response.data;
        const packageId = document.getElementById("quickPackageId").value;
        const targetUrl = getContextPath() + "/receptionist/sales?package=" + encodeURIComponent(packageId) + "&customer=" + encodeURIComponent(newCustomer.customerId);
        window.location.href = targetUrl;
    } catch (err) {
        console.error("Lỗi khi tạo nhanh khách hàng:", err);
        if (alertBox !== null) {
            alertBox.textContent = "Không thể kết nối đến máy chủ. Vui lòng thử lại.";
            alertBox.hidden = false;
        }
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalText;
    }
}

// Mỗi lần gõ: chờ 0,25 giây không gõ thêm rồi mới gọi server.
function handleCustomerInput() {
    clearTimeout(customerSearchTimer);
    const customerSearch = document.getElementById("customerSearch");
    const keyword = customerSearch.value.trim();

    if (keyword.length < CUSTOMER_MIN_LENGTH) {
        customerRequestNo = customerRequestNo + 1;
        showCustomerState(CUSTOMER_HINT_TEXT, CUSTOMER_HINT_SUB);
        return;
    }

    showCustomerState("Đang tìm…", "");
    customerSearchTimer = setTimeout(function () {
        loadCustomers(keyword);
    }, CUSTOMER_SEARCH_DELAY);
}

// Gọi server tìm khách theo chữ gõ rồi vẽ kết quả.
async function loadCustomers(keyword) {
    // B1: tăng bộ đếm để theo dõi lần gọi mới nhất
    customerRequestNo = customerRequestNo + 1;
    const thisRequestNo = customerRequestNo;

    // B2: gọi API thông qua getJson của common.js
    const result = await getJson(CUSTOMER_SEARCH_URL, { q: keyword });

    // B3: có lần gõ mới hơn thì bỏ qua kết quả cũ
    const isOutdated = thisRequestNo !== customerRequestNo;
    if (isOutdated) {
        return;
    }

    // B4: kiểm tra kết quả trả về từ JsonResponse
    if (!result.success) {
        showCustomerState(result.message, "");
        return;
    }
    const rows = result.data;
    const isEmpty = rows === null || rows.length === 0;
    if (isEmpty) {
        showCustomerState('Không tìm thấy khách nào khớp với "' + keyword + '".',
            'Bấm "+ Tạo hồ sơ" để tạo nhanh hồ sơ và tiếp tục mua gói.');
        return;
    }
    renderCustomerRows(rows);
}

// Hiện một dòng chữ trạng thái và ẩn bảng.
function showCustomerState(message, hint) {
    const customerState = document.getElementById("customerState");
    const customerTableWrap = document.getElementById("customerTableWrap");
    if (customerState === null || customerTableWrap === null) {
        return;
    }

    customerTableWrap.hidden = true;
    customerState.hidden = false;
    customerState.textContent = message;
    if (hint !== "") {
        const hintElement = document.createElement("span");
        hintElement.className = "counter-hint";
        hintElement.textContent = hint;
        customerState.appendChild(hintElement);
    }
}

// Vẽ bảng khách: xóa các dòng cũ rồi thêm mỗi khách một dòng vào #customerTableBody.
function renderCustomerRows(rows) {
    const customerState = document.getElementById("customerState");
    const customerTableWrap = document.getElementById("customerTableWrap");
    const customerTableBody = document.getElementById("customerTableBody");
    const customerSearch = document.getElementById("customerSearch");
    if (customerTableBody === null || customerSearch === null) {
        return;
    }
    const packageId = customerSearch.dataset.packageId;

    customerTableBody.textContent = "";
    for (const row of rows) {
        const tableRow = createCustomerRow(row, packageId);
        customerTableBody.appendChild(tableRow);
    }
    customerState.hidden = true;
    customerTableWrap.hidden = false;
}

// Tạo MỘT dòng <tr> cho một khách. Dữ liệu chèn bằng textContent chống XSS.
function createCustomerRow(row, packageId) {
    const tableRow = document.createElement("tr");

    // Ô 1: tên + nhãn hồ sơ
    const customerCell = document.createElement("td");
    const nameBox = createElementWithText("span", "counter-customer-name", row.fullName);
    if (!row.hasAccount) {
        const noAccountFlag = createElementWithText("span", "counter-flag is-amber", "Chưa có tài khoản");
        nameBox.appendChild(noAccountFlag);
    }
    if (row.isOwnProfile) {
        const ownProfileFlag = createElementWithText("span", "counter-flag", "Hồ sơ của bạn");
        nameBox.appendChild(ownProfileFlag);
    }
    if (row.isDependent) {
        const dependentFlag = createElementWithText("span", "counter-flag", "Hồ sơ phụ thuộc");
        nameBox.appendChild(dependentFlag);
    }
    let phonePrefix = "";
    if (row.isDependent) {
        phonePrefix = "SĐT người giám hộ ";
    }
    const subText = row.customerCode + " · " + phonePrefix + row.phoneText;
    const subBox = createElementWithText("span", "counter-customer-sub", subText);
    customerCell.appendChild(nameBox);
    customerCell.appendChild(subBox);
    tableRow.appendChild(customerCell);

    // Ô 2: thẻ trạng thái
    const statusCell = document.createElement("td");
    const statusBadge = createElementWithText("span", "badge " + row.statusBadgeClass, row.statusText);
    statusCell.appendChild(statusBadge);
    tableRow.appendChild(statusCell);

    // Ô 3: gói tập hiện tại
    const packageCell = document.createElement("td");
    const hasPackage = row.currentPackageName !== "";
    if (hasPackage) {
        const packageName = document.createTextNode(row.currentPackageName);
        const endBox = createElementWithText("span", "counter-customer-sub", "đến " + row.currentPackageEndText);
        packageCell.appendChild(packageName);
        packageCell.appendChild(endBox);
    } else {
        const noPackage = createElementWithText("span", "counter-muted", "Chưa có gói tập");
        packageCell.appendChild(noPackage);
    }
    tableRow.appendChild(packageCell);

    // Ô 4: nút Chọn
    const actionCell = document.createElement("td");
    actionCell.className = "counter-cell-action";
    if (row.isOwnProfile) {
        const blockedButton = createElementWithText("span", "btn btn-outline btn-sm is-blocked", "Chọn");
        blockedButton.title = "Không thể bán cho hồ sơ của chính bạn. Vui lòng nhờ đồng nghiệp khác.";
        blockedButton.setAttribute("aria-disabled", "true");
        actionCell.appendChild(blockedButton);
    } else {
        const chooseLink = createElementWithText("a", "btn btn-primary btn-sm", "Chọn");
        chooseLink.href = getContextPath() + "/receptionist/sales?package=" + packageId + "&customer=" + row.customerId;
        actionCell.appendChild(chooseLink);
    }
    tableRow.appendChild(actionCell);

    return tableRow;
}

function createElementWithText(tagName, className, text) {
    const element = document.createElement(tagName);
    element.className = className;
    element.textContent = text;
    return element;
}


/* ==================== 3. MÀN XÁC NHẬN & THU TIỀN (CHECKOUT) ==================== */

// Điều khiển màn Xác nhận thanh toán (counter-sale-next.jsp).
function initCheckoutPage() {
    const checkoutSection = document.getElementById("checkoutSection");
    if (checkoutSection === null) {
        return;
    }

    initPaymentMethodToggle();
    initValidityPicker();
    initVoucherLogic();
    initCheckoutSubmit();
}

// Khởi tạo xử lý Voucher tại quầy (tự động kiểm tra AJAX, cập nhật bảng tính tiền, nút x xóa mã)
function initVoucherLogic() {
    const checkoutForm = document.getElementById("checkoutForm");
    const voucherInput = document.getElementById("voucherCode");
    const voucherClearBtn = document.getElementById("voucherClearBtn");
    const voucherValidIcon = document.getElementById("voucherValidIcon");
    const voucherStatus = document.getElementById("voucherStatus");
    const discountRow = document.getElementById("discountRow");
    const voucherCodeSummary = document.getElementById("voucherCodeSummary");
    const summaryDiscountAmount = document.getElementById("summaryDiscountAmount");
    const summaryPreTaxAmount = document.getElementById("summaryPreTaxAmount");
    const summaryVatAmount = document.getElementById("summaryVatAmount");
    const summaryTotalAmount = document.getElementById("summaryTotalAmount");
    const confirmPaymentText = document.getElementById("confirmPaymentText");

    if (checkoutForm === null || voucherInput === null) {
        return;
    }

    const packageId = parseInt(checkoutForm.dataset.packageId, 10) || 0;
    const customerId = parseInt(checkoutForm.dataset.customerId, 10) || 0;
    const basePrice = parseInt(checkoutForm.dataset.basePrice, 10) || 0;
    const vatRate = parseInt(checkoutForm.dataset.vatRate, 10) || 0;

    let inFlight = null;
    let checkedCode = "";

    function setVoucherStatus(message, state) {
        if (voucherStatus === null) {
            return;
        }
        if (!message) {
            voucherStatus.hidden = true;
            voucherStatus.textContent = "";
            voucherStatus.removeAttribute("data-state");
            return;
        }
        voucherStatus.hidden = false;
        voucherStatus.textContent = message;
        voucherStatus.setAttribute("data-state", state);
    }

    function resetPricesToOriginal() {
        if (discountRow !== null) {
            discountRow.hidden = true;
        }
        if (voucherValidIcon !== null) {
            voucherValidIcon.hidden = true;
        }

        const vatAmount = Math.round((basePrice * vatRate) / (100 + vatRate));
        const preTaxAmount = basePrice - vatAmount;

        if (summaryPreTaxAmount !== null) {
            summaryPreTaxAmount.textContent = formatMoney(preTaxAmount);
        }
        if (summaryVatAmount !== null) {
            summaryVatAmount.textContent = formatMoney(vatAmount);
        }
        if (summaryTotalAmount !== null) {
            summaryTotalAmount.textContent = formatMoney(basePrice);
        }
        if (confirmPaymentText !== null) {
            confirmPaymentText.textContent = "Xác nhận đã thu " + formatMoney(basePrice);
        }
    }

    function toggleClearBtn() {
        const hasVal = voucherInput.value.trim().length > 0;
        if (voucherClearBtn !== null) {
            voucherClearBtn.hidden = !hasVal;
        }
    }

    voucherInput.addEventListener("input", function () {
        toggleClearBtn();
        if (voucherValidIcon !== null) {
            voucherValidIcon.hidden = true;
        }
        setVoucherStatus("", "");
    });

    voucherInput.addEventListener("keydown", function (e) {
        if (e.key === "Enter") {
            e.preventDefault();
            checkVoucher();
        }
    });

    voucherInput.addEventListener("blur", function () {
        const val = voucherInput.value.trim().toUpperCase();
        if (val !== checkedCode) {
            checkVoucher();
        }
    });

    if (voucherClearBtn !== null) {
        voucherClearBtn.addEventListener("click", function () {
            voucherInput.value = "";
            toggleClearBtn();
            checkedCode = "";
            setVoucherStatus("", "");
            resetPricesToOriginal();
            voucherInput.focus();
        });
    }

    async function checkVoucher() {
        const rawCode = voucherInput.value.trim().toUpperCase();
        voucherInput.value = rawCode;
        toggleClearBtn();

        if (rawCode === "") {
            checkedCode = "";
            resetPricesToOriginal();
            setVoucherStatus("", "");
            return;
        }

        checkedCode = rawCode;
        setVoucherStatus("Đang kiểm tra mã...", "neutral");

        if (inFlight !== null) {
            return inFlight;
        }

        inFlight = (async function () {
            try {
                const result = await getJson(VOUCHER_CHECK_URL, {
                    package: packageId,
                    customer: customerId,
                    code: rawCode
                });

                if (result.success && result.data && result.data.valid) {
                    const data = result.data;
                    if (voucherValidIcon !== null) {
                        voucherValidIcon.hidden = false;
                    }
                    if (discountRow !== null) {
                        discountRow.hidden = false;
                    }
                    if (voucherCodeSummary !== null) {
                        voucherCodeSummary.textContent = data.voucherCode;
                    }
                    if (summaryDiscountAmount !== null) {
                        summaryDiscountAmount.textContent = data.discountText;
                    }
                    if (summaryPreTaxAmount !== null) {
                        summaryPreTaxAmount.textContent = data.preTaxText;
                    }
                    if (summaryVatAmount !== null) {
                        summaryVatAmount.textContent = data.vatText;
                    }
                    if (summaryTotalAmount !== null) {
                        summaryTotalAmount.textContent = data.finalText;
                    }
                    if (confirmPaymentText !== null) {
                        confirmPaymentText.textContent = "Xác nhận đã thu " + data.finalText;
                    }
                    setVoucherStatus(data.message || "Áp dụng mã giảm giá thành công", "success");
                } else {
                    resetPricesToOriginal();
                    const errMsg = (result.errors && result.errors.voucherCode) || result.message || "Mã giảm giá không hợp lệ";
                    setVoucherStatus(errMsg, "error");
                }
            } catch (err) {
                resetPricesToOriginal();
                setVoucherStatus("Lỗi kết nối khi kiểm tra mã. Vui lòng thử lại.", "error");
            } finally {
                inFlight = null;
            }
        })();

        return inFlight;
    }

    // Intercept form submit để đảm bảo mã voucher đã được xác thực trước khi gửi
    checkoutForm.addEventListener("submit", async function (e) {
        const val = voucherInput.value.trim().toUpperCase();
        if (val !== "" && val !== checkedCode) {
            e.preventDefault();
            await checkVoucher();
            if (voucherStatus !== null && voucherStatus.getAttribute("data-state") === "error") {
                return;
            }
            if (typeof checkoutForm.requestSubmit === "function") {
                checkoutForm.requestSubmit();
            } else {
                checkoutForm.submit();
            }
        }
    });

    toggleClearBtn();
}

// B1: chọn hình thức thu tiền (Tiền mặt / Chuyển khoản) — cập nhật class .active cho thẻ.
function initPaymentMethodToggle() {
    const paymentCards = document.querySelectorAll(".counter-payment-card");
    for (const card of paymentCards) {
        const radio = card.querySelector('input[type="radio"]');
        if (radio !== null) {
            radio.addEventListener("change", function () {
                for (const otherCard of paymentCards) {
                    otherCard.classList.remove("active");
                }
                if (radio.checked) {
                    card.classList.add("active");
                }
            });
        }
    }
}

// B2: chọn ngày bắt đầu — chuyển đổi giữa Kích hoạt hôm nay và Hẹn ngày khác (trong 7 ngày tới).
function initValidityPicker() {
    const validityPicker = document.getElementById("validityPickerOptions");
    if (validityPicker === null) {
        return;
    }

    const durationMonths = parseInt(validityPicker.dataset.durationMonths, 10) || 0;
    const todayText = validityPicker.dataset.todayText;
    const defaultEndText = validityPicker.dataset.defaultEndText;

    const modeToday = document.getElementById("modeToday");
    const modeCustom = document.getElementById("modeCustom");
    const customDateWrap = document.getElementById("customDateWrap");
    const customStartDate = document.getElementById("customStartDate");
    const previewStartDate = document.getElementById("previewStartDate");
    const previewEndDate = document.getElementById("previewEndDate");

    if (modeToday !== null && modeCustom !== null) {
        modeToday.addEventListener("change", function () {
            if (modeToday.checked) {
                if (customDateWrap !== null) {
                    customDateWrap.hidden = true;
                }
                if (previewStartDate !== null) {
                    previewStartDate.textContent = todayText;
                }
                if (previewEndDate !== null) {
                    previewEndDate.textContent = defaultEndText;
                }
            }
        });

        modeCustom.addEventListener("change", function () {
            if (modeCustom.checked) {
                if (customDateWrap !== null) {
                    customDateWrap.hidden = false;
                }
                if (customStartDate !== null && customStartDate.value !== "") {
                    calculateAndPreviewEndDate(customStartDate.value, durationMonths, previewStartDate, previewEndDate);
                }
            }
        });
    }

    if (customStartDate !== null) {
        customStartDate.addEventListener("input", function () {
            calculateAndPreviewEndDate(customStartDate.value, durationMonths, previewStartDate, previewEndDate);
        });
        customStartDate.addEventListener("change", function () {
            calculateAndPreviewEndDate(customStartDate.value, durationMonths, previewStartDate, previewEndDate);
        });
    }
}

// B2.1: tính toán lại ngày hết hạn dự kiến theo số tháng gói khi thay đổi ngày bắt đầu.
function calculateAndPreviewEndDate(dateStr, durationMonths, previewStartEl, previewEndEl) {
    if (!dateStr) {
        return;
    }
    const parts = dateStr.split("-");
    if (parts.length !== 3) {
        return;
    }

    const year = parseInt(parts[0], 10);
    const month = parseInt(parts[1], 10) - 1;
    const day = parseInt(parts[2], 10);

    const startDate = new Date(year, month, day);
    const isInvalidDate = isNaN(startDate.getTime());
    if (isInvalidDate) {
        return;
    }

    const formattedStartDay = String(day).padStart(2, "0");
    const formattedStartMonth = String(month + 1).padStart(2, "0");
    const startDayStr = formattedStartDay + "/" + formattedStartMonth + "/" + year;

    if (previewStartEl !== null) {
        previewStartEl.textContent = startDayStr;
    }

    if (durationMonths <= 0) {
        if (previewEndEl !== null) {
            previewEndEl.textContent = startDayStr;
        }
        return;
    }

    const targetMonth = month + durationMonths;
    const endDate = new Date(year, targetMonth, day);

    const expectedMonth = ((targetMonth % 12) + 12) % 12;
    const isOverflowMonth = endDate.getMonth() !== expectedMonth;
    if (isOverflowMonth) {
        endDate.setDate(0);
    }

    const formattedEndDay = String(endDate.getDate()).padStart(2, "0");
    const formattedEndMonth = String(endDate.getMonth() + 1).padStart(2, "0");
    const endDayStr = formattedEndDay + "/" + formattedEndMonth + "/" + endDate.getFullYear();

    if (previewEndEl !== null) {
        previewEndEl.textContent = endDayStr;
    }
}

// B3: chống bấm đúp khi submit form thu tiền tại quầy.
function initCheckoutSubmit() {
    const checkoutForm = document.getElementById("checkoutForm");
    const confirmPaymentBtn = document.getElementById("confirmPaymentBtn");
    if (checkoutForm === null || confirmPaymentBtn === null) {
        return;
    }

    checkoutForm.addEventListener("submit", function () {
        confirmPaymentBtn.disabled = true;
        confirmPaymentBtn.textContent = "Đang xử lý...";
    });
}
