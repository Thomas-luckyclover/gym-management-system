"use strict";

/*
 * BÁN GÓI TẠI QUẦY — counter-sale.js (SRS #37, đợt 1)
 *
 * File dùng cho 2 màn, mỗi phần tự kiểm tra phần tử của mình có trên trang không:
 *   1. Màn Chọn gói (counter-sale-packages.jsp): ô tìm gói lọc thẻ ngay trên trình duyệt, bấm thẻ để chọn,
 *      thanh "Đang chọn" ở dưới, nút "Tiếp tục".
 *   2. Màn Chọn khách (counter-sale-customers.jsp): ô tìm khách gọi AJAX, vẽ bảng kết quả.
 * Mọi tính toán (giá mỗi tháng, % tiết kiệm, tìm khách) nằm ở CounterSaleService; JS chỉ điều khiển giao diện.
 * Dùng chung từ common.js: normalizeSearchText, formatMoney, getJson, getContextPath, MESSAGES.
 */

const CUSTOMER_SEARCH_URL = "/receptionist/sales/customers";
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
    // B1
    const packageSearch = document.getElementById("packageSearch");
    const keyword = normalizeSearchText(packageSearch.value);
    const panels = document.querySelectorAll("[data-tab-panel]");

    for (const panel of panels) {
        // B2
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

        // B3
        const hasCards = cards.length > 0;
        const isNoMatch = hasCards && visibleCount === 0;
        showFilterEmpty(panel, isNoMatch, packageSearch.value.trim());
    }
}

// Hiện / ẩn dòng báo "Không có gói nào khớp" trong một tab.
// Phần tử [data-filter-empty] nằm cuối mỗi tab trong counter-sale-packages.jsp.
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

// Bấm một thẻ (radio đổi trạng thái): đánh dấu thẻ đó, bỏ đánh dấu thẻ cũ, cập nhật thanh dưới.
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
// #selectedSummary và #continueButton nằm trong .counter-bar của counter-sale-packages.jsp.
function updateSelectedBar() {
    const selectedSummary = document.getElementById("selectedSummary");
    const continueButton = document.getElementById("continueButton");

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

// Bấm Tiếp tục: chuyển sang địa chỉ đã gắn sẵn trên thẻ (gói tập sang Chọn khách, gói PT sang màn bán PT).
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
}

// Mỗi lần gõ: chờ 0,25 giây không gõ thêm rồi mới gọi server (gõ nhanh không gửi dồn dập).
// B1: hủy hẹn giờ cũ
// B2: chưa đủ 2 ký tự thì quay về dòng hướng dẫn, không gọi server
// B3: đủ ký tự thì báo "Đang tìm…" và hẹn giờ gọi loadCustomers()
function handleCustomerInput() {
    // B1
    clearTimeout(customerSearchTimer);
    const customerSearch = document.getElementById("customerSearch");
    const keyword = customerSearch.value.trim();

    // B2
    if (keyword.length < CUSTOMER_MIN_LENGTH) {
        customerRequestNo = customerRequestNo + 1;
        showCustomerState(CUSTOMER_HINT_TEXT, CUSTOMER_HINT_SUB);
        return;
    }

    // B3
    showCustomerState("Đang tìm…", "");
    customerSearchTimer = setTimeout(function () {
        loadCustomers(keyword);
    }, CUSTOMER_SEARCH_DELAY);
}

// Gọi server tìm khách theo chữ gõ rồi vẽ kết quả.
// Input: keyword (đã trim, từ 2 ký tự). Server: CounterSaleServlet.writeCustomers.
// B1: tăng bộ đếm, nhớ số của lần gọi này
// B2: gọi getJson
// B3: có lần gõ mới hơn thì bỏ kết quả này
// B4: lỗi / không có ai / có khách thì hiện tương ứng
async function loadCustomers(keyword) {
    // B1
    customerRequestNo = customerRequestNo + 1;
    const thisRequestNo = customerRequestNo;

    // B2
    const result = await getJson(CUSTOMER_SEARCH_URL, { q: keyword });

    // B3
    const isOutdated = thisRequestNo !== customerRequestNo;
    if (isOutdated) {
        return;
    }

    // B4
    if (!result.success) {
        showCustomerState(result.message, "");
        return;
    }
    const rows = result.data;
    if (rows.length === 0) {
        showCustomerState('Không tìm thấy khách khớp "' + keyword + '".', 'Khách mới thì bấm "+ Tạo hồ sơ" bên dưới.');
        return;
    }
    renderCustomerRows(rows);
}

// Hiện một dòng chữ trạng thái (hướng dẫn / đang tìm / không thấy / lỗi) và ẩn bảng.
// #customerState và #customerTableWrap nằm trong counter-sale-customers.jsp.
function showCustomerState(message, hint) {
    const customerState = document.getElementById("customerState");
    const customerTableWrap = document.getElementById("customerTableWrap");

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
    const packageId = customerSearch.dataset.packageId;

    customerTableBody.textContent = "";
    for (const row of rows) {
        const tableRow = createCustomerRow(row, packageId);
        customerTableBody.appendChild(tableRow);
    }
    customerState.hidden = true;
    customerTableWrap.hidden = false;
}

// Tạo MỘT dòng <tr> cho một khách. Dữ liệu chèn bằng textContent nên tên có ký tự lạ cũng không chạy được mã (chống XSS).
// 4 ô: Khách hàng · Trạng thái · Gói tập · nút Chọn (giống bảng trong counter-sale-customers.jsp).
function createCustomerRow(row, packageId) {
    const tableRow = document.createElement("tr");

    // Ô 1: tên + nhãn "Chưa có tài khoản" / "Hồ sơ của bạn" / "Hồ sơ phụ thuộc", dòng phụ "mã · SĐT"
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

    // Ô 2: thẻ trạng thái, màu theo statusBadgeClass server trả về
    const statusCell = document.createElement("td");
    const statusBadge = createElementWithText("span", "badge " + row.statusBadgeClass, row.statusText);
    statusCell.appendChild(statusBadge);
    tableRow.appendChild(statusCell);

    // Ô 3: gói tập hiện tại, hoặc "Chưa có gói tập"
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

    // Ô 4: nút Chọn, sang bước tiếp theo kèm gói và khách
    const actionCell = document.createElement("td");
    actionCell.className = "counter-cell-action";
    if (row.isOwnProfile) {
        // CBR-115: hồ sơ của chính lễ tân, không bán được. Nút mờ, không phải liên kết, có tooltip giải thích.
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
// Tạo một phần tử có class và chữ (chữ gán bằng textContent, an toàn với dữ liệu người dùng).
function createElementWithText(tagName, className, text) {
    const element = document.createElement(tagName);
    element.className = className;
    element.textContent = text;
    return element;
}