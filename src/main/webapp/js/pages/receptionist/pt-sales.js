"use strict";

/*
 * BÁN GÓI PT — pt-sales.js
 *
 * JS LÀM 2 VIỆC, mọi tính toán vẫn ở PtSaleService:
 *   1. Ô tìm khách kiểu Google (data-combobox): gõ ≥ 2 ký tự → chờ 200ms → getJson("/receptionist/pt-sales/customers", {q})
 *      → hiện tối đa 8 gợi ý (tên, mã, SĐT, gói thành viên). ↑ ↓ chọn, Enter mở, Esc đóng.
 *        data-combobox="customer"  chọn → mở ?customer=12
 *        data-combobox="partner"   chọn → ghi mã vào ô ẩn partner rồi gửi form các bước
 *      Tắt JS: Enter gửi ?q=… → server in danh sách kết quả.
 *   2. Form các bước (data-auto-submit): đổi gói / PT / ngày / ô giờ → tự gửi để server tính lại.
 *      Đổi gói, PT hoặc ngày → bỏ giờ đã chọn (giờ PT rảnh đã khác).
 *      Ô ngày: ngoài min–max → báo lỗi dưới ô, không gửi; hợp lệ → chờ 0,7 giây (gõ xong) rồi mới gửi.
 */

const SALE_SUGGEST_URL = "/receptionist/pt-sales/customers";
const SALE_SUGGEST_DELAY = 200;
const SALE_TIME_FIELD = /^t[1-5]$/;
const SALE_DATE_DELAY = 700;   // ô ngày: chờ gõ xong (gõ "1" rồi "5" không gửi nhầm ngày 01)

document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("[data-combobox]").forEach(initSaleCombobox);
    initSaleAutoSubmit(document.querySelector("form[data-auto-submit]"));
});


/* ==================== 1. Ô TÌM KHÁCH CÓ GỢI Ý ==================== */

function initSaleCombobox(box) {
    const input = box.querySelector("[role=combobox]");
    const list = box.querySelector("[role=listbox]");
    const mode = box.dataset.combobox;
    let rows = [];          // gợi ý đang hiện (dữ liệu JSON)
    let active = -1;        // vị trí dòng đang tô bằng phím ↑ ↓
    let timer = 0;
    let requestNo = 0;      // chỉ vẽ kết quả của lần gõ mới nhất

    input.addEventListener("input", function () {
        clearTimeout(timer);
        const query = input.value.trim();
        if (query.length < 2) {
            close();
            return;
        }
        timer = setTimeout(function () { load(query); }, SALE_SUGGEST_DELAY);
    });

    input.addEventListener("keydown", function (event) {
        const isOpen = !list.hidden && rows.length > 0;
        if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            if (!isOpen) {
                return;
            }
            event.preventDefault();
            const step = event.key === "ArrowDown" ? 1 : -1;
            highlight((active + step + rows.length) % rows.length);
        } else if (event.key === "Enter" && isOpen && active >= 0) {
            event.preventDefault();
            choose(rows[active]);
        } else if (event.key === "Enter" && mode === "partner") {
            event.preventDefault();   // ô người tập cùng nằm trong form các bước: Enter không gửi form
        } else if (event.key === "Escape") {
            close();
        }
    });

    input.addEventListener("focus", function () {
        if (rows.length > 0 && input.value.trim().length >= 2) {
            open();
        }
    });
    document.addEventListener("click", function (event) {
        if (!box.contains(event.target)) {
            close();
        }
    });
    // mousedown giữ con trỏ trong ô (không blur) → click vào gợi ý chạy đúng.
    list.addEventListener("mousedown", function (event) { event.preventDefault(); });
    list.addEventListener("click", function (event) {
        const item = event.target.closest("[data-index]");
        if (item) {
            choose(rows[Number(item.dataset.index)]);
        }
    });

    async function load(query) {
        const myNo = ++requestNo;
        box.classList.add("is-loading");
        const result = await getJson(SALE_SUGGEST_URL, { q: query });
        if (myNo !== requestNo) {
            return;
        }
        box.classList.remove("is-loading");
        rows = result.success && Array.isArray(result.data) ? result.data : [];
        if (mode === "partner") {
            // Người tập cùng không thể là chính người đứng tên (ô ẩn customer trong form các bước).
            const holderId = Number(input.closest("form").elements.customer.value);
            rows = rows.filter(function (row) { return row.id !== holderId; });
        }
        render(query, result.success ? "" : result.message);
    }

    function render(query, error) {
        list.replaceChildren();
        active = -1;
        input.removeAttribute("aria-activedescendant");
        if (rows.length === 0) {
            const empty = document.createElement("li");
            empty.className = "sale-suggest-empty";
            empty.textContent = error || "Không tìm thấy khách khớp \"" + query + "\".";
            list.append(empty);
        }
        rows.forEach(function (row, index) {
            list.append(buildOption(row, index));
        });
        open();
    }

    function buildOption(row, index) {
        const item = document.createElement("li");
        item.id = input.id + "-opt-" + index;
        item.dataset.index = String(index);
        item.setAttribute("role", "option");
        item.setAttribute("aria-selected", "false");
        item.className = row.ok ? "is-ok" : "is-blocked";

        const avatar = textElement("span", "sale-avatar", (row.name || "?").charAt(0));
        avatar.setAttribute("aria-hidden", "true");
        const main = textElement("span", "sale-row-main", "");
        main.append(textElement("strong", "", row.name), textElement("small", "", row.code + " · " + row.phone));
        item.append(avatar, main, textElement("em", "", row.note));
        return item;
    }

    function highlight(index) {
        list.querySelectorAll("[role=option]").forEach(function (item, i) {
            item.setAttribute("aria-selected", String(i === index));
        });
        active = index;
        const item = list.querySelector("[data-index=\"" + index + "\"]");
        input.setAttribute("aria-activedescendant", item.id);
        item.scrollIntoView({ block: "nearest" });
    }

    function choose(row) {
        close();
        input.value = row.name;
        if (mode === "customer") {
            window.location.href = buildUrl("/receptionist/pt-sales?customer=" + row.id);
            return;
        }
        const form = input.closest("form");
        form.querySelector("[data-partner-field]").value = row.id;
        form.requestSubmit();
    }

    function open() {
        list.hidden = false;
        input.setAttribute("aria-expanded", "true");
    }

    function close() {
        list.hidden = true;
        input.setAttribute("aria-expanded", "false");
        input.removeAttribute("aria-activedescendant");
    }
}

function textElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) {
        element.className = className;
    }
    element.textContent = text == null ? "" : String(text);
    return element;
}


/* ==================== 2. FORM CÁC BƯỚC TỰ GỬI ==================== */

function initSaleAutoSubmit(form) {
    if (!form) {
        return;
    }
    let dateTimer = 0;
    // Quay lại bằng nút Back (trang lấy từ bộ nhớ đệm) → bỏ trạng thái mờ "đang gửi".
    window.addEventListener("pageshow", function () { form.classList.remove("is-busy"); });
    form.addEventListener("change", function (event) {
        const field = event.target;
        if (!field.matches("input[type=radio], input[type=date]")) {
            return;
        }
        clearTimeout(dateTimer);
        if (field.matches("[data-start-field]")) {
            // Ngày sai (trống, trước hôm nay, quá 30 ngày) → báo dưới ô, KHÔNG gửi, form không bị khóa.
            const isValid = field.value !== "" && field.checkValidity();
            showStartError(field, !isValid);
            if (isValid) {
                dateTimer = setTimeout(function () { submitSteps(form, field); }, SALE_DATE_DELAY);
            }
            return;
        }
        submitSteps(form, field);
    });

    const clearPartner = form.querySelector("[data-clear-partner]");
    if (clearPartner) {
        clearPartner.addEventListener("click", function () {
            form.querySelector("[data-partner-field]").value = "";
            form.requestSubmit();
        });
    }
}

/** Gửi form các bước: đổi gói / PT / ngày thì bỏ giờ đã chọn. Mờ form chỉ khi chắc chắn gửi được. */
function submitSteps(form, field) {
    if (!SALE_TIME_FIELD.test(field.name)) {
        clearTimes(form);
    }
    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }
    form.classList.add("is-busy");
    form.requestSubmit();
}

function showStartError(field, hasError) {
    const message = document.getElementById("sale-start-error");
    field.setAttribute("aria-invalid", String(hasError));
    if (message) {
        message.hidden = !hasError;
    }
}

/** Bỏ giờ đã chọn t1..t5 (radio trong bảng tuần, hoặc ô ẩn khi bảng chưa hiện). */
function clearTimes(form) {
    form.querySelectorAll("input").forEach(function (input) {
        if (!SALE_TIME_FIELD.test(input.name)) {
            return;
        }
        if (input.type === "radio") {
            input.checked = false;
        } else {
            input.value = "";
        }
    });
}
