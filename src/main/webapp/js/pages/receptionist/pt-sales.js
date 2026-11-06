"use strict";

/*
 * ============================================================================================================
 * BÁN GÓI PT — pt-sales.js   (nạp bởi pt-sales.jsp, thẻ <script … defer>)
 * ============================================================================================================
 *
 * FILE NÀY LÀM 2 VIỆC (vẽ "Lịch tập" là file khác: pt-sale-plan.js). Mọi LUẬT vẫn ở server (PtSaleService):
 *   1. Ô tìm khách có gợi ý — gõ ≥ 1 ký tự → chờ 200 ms → GET /receptionist/pt-sales/customers?q=…
 *      → PtSaleServlet.writeSuggestions trả JSON [{id, name, code, phone, ok, note}] → vẽ mọi dòng (danh sách tự cuộn).
 *      ↑ ↓ chọn, Enter mở, Esc đóng. Chọn 1 khách → mở lại trang với ?customer=<id> (hoặc partner=<id>).
 *   2. Form bước 1 tự gửi — đổi gói / PT / ngày → gửi form GET → server tính lại cả trang.
 *
 * JS TÌM PHẦN TỬ NÀO TRÊN TRANG?  (querySelector("…") = "tìm phần tử HTML khớp mẫu này")
 *   Mẫu tìm                          Phần tử HTML ở đâu                                     Dùng để
 *   [data-combobox]                  _pt-sale-customer-box.jspf: ô "Khách mua gói"           gắn gợi ý (initSaleCombobox)
 *                                    (data-combobox="customer") và ô "Người tập cùng" ("partner")
 *     ↳ [role=combobox]              ô <input> gõ chữ bên trong                              đọc chữ gõ
 *     ↳ [role=listbox]               <ul class="sale-suggest"> rỗng bên trong                JS thêm <li> gợi ý vào đây
 *   form[data-auto-submit]           _pt-sale-step1.jspf: form GET id="sale-form"           tự gửi khi đổi lựa chọn
 *     ↳ [data-start-field]           ô "Ngày bắt đầu kỳ" (input type=date)                   kiểm min/max trước khi gửi
 *   #sale-start-error                _pt-sale-step1.jspf: dòng đỏ "Ngày bắt đầu phải từ…"     ẩn / hiện
 *   a[data-keep-plan]                link "Đổi khách", "Đổi người tập cùng", kết quả tìm      đi link nhưng giữ lịch
 *   [data-plan-sessions]             _pt-sale-actions.jspf: ô ẩn "sessions" của form Giữ lịch (pt-sale-plan.js điền)
 *
 * HÀM LẤY TỪ common.js (nạp trước file này): getJson(url, params) → Promise {success, data, message}; showToast(loại, chữ).
 *
 * BỐ CỤC: HÀM CHÍNH (chạy khi trang tải xong) → PHẦN 1 ô gợi ý (+ hàm phụ) → PHẦN 2 form tự gửi (+ hàm phụ)
 *         → PHẦN 3 khung "Đơn đang giữ chỗ" (gỡ dòng hết giờ) → PHẦN 4 báo lỗi bằng toast (lỗi server, ô "Đã nhận đủ").
 * ============================================================================================================
 */

const SALE_SUGGEST_URL = "/receptionist/pt-sales/customers";   // → PtSaleServlet.doGet → writeSuggestions
const SALE_SUGGEST_DELAY = 200;                                    // ms chờ sau phím cuối rồi mới hỏi server
const SALE_DATE_DELAY = 700;   // ô ngày: chờ gõ xong (gõ "1" rồi "5" không gửi nhầm ngày 01)

/* ==================== HÀM CHÍNH — chạy 1 lần khi HTML đã tải xong ==================== */
document.addEventListener("DOMContentLoaded", function () {
    // 1. Mỗi ô tìm khách (khách đứng tên, người tập cùng) → gắn gợi ý.
    document.querySelectorAll("[data-combobox]").forEach(initSaleCombobox);

    // 2. Form bước 1 "Gói & PT" (không có khi đang giữ chỗ → null, hàm tự bỏ qua).
    initSaleAutoSubmit(document.querySelector("form[data-auto-submit]"));

    // 3. Bấm link có data-keep-plan → chặn đi thẳng, thêm t1..t5 + sessions vào link rồi mới đi (không mất lịch đang xếp).
    document.addEventListener("click", function (event) {
        const link = event.target.closest("a[data-keep-plan]");
        if (link) {
            event.preventDefault();
            window.location.href = withPlan(link.href);
        }
    });

    // 4. Khung "Đơn đang giữ chỗ": đơn nào đếm về 00:00 thì gỡ dòng đó (hết hạn rồi bấm "Tiếp tục thu tiền" chỉ ra MSG108).
    initHoldList(document.querySelector(".sale-holds"));

    // 5. Server vừa từ chối Giữ lịch / Thu tiền → toast đỏ nói rõ là HỆ THỐNG chặn + lý do.
    showServerErrorToast(document.querySelector("[data-server-error]"));

    // 6. Form thu tiền: chưa tích "Đã nhận đủ" → toast, không gửi.
    initPayConfirm(document.querySelector("[data-pay-form]"));
});

/* ==================== PHẦN 4 — BÁO LỖI BẰNG TOAST (06/11) ==================== */

/**
 * HÀM PHỤ — gọi ở bước 5 của HÀM CHÍNH. line = <p data-server-error> do JSP in khi có saleError (null → thôi).
 * Toast đỏ ở lại tới khi bấm đóng (showToast loại "error"); dòng đỏ trong cột vẫn giữ để đọc lại.
 * VD: "Hệ thống chưa giữ được lịch: Ca sáng ngày 05/11/2026 chưa được chốt. Vui lòng chốt ca cũ và mở ca hôm nay trước khi bán."
 */
function showServerErrorToast(line) {
    if (!line) {
        return;
    }
    const isPayStep = document.querySelector("[data-pay-form]") !== null;
    const prefix = isPayStep ? "Hệ thống chưa ghi nhận thu tiền: " : "Hệ thống chưa giữ được lịch: ";
    showToast("error", prefix + line.textContent.trim());
}

/**
 * HÀM PHỤ — gọi ở bước 6 của HÀM CHÍNH. form = form thu tiền (chỉ có ở bước "Đang giữ").
 * Chưa tích "Đã nhận đủ …" → chặn gửi, toast vàng nhắc + viền đỏ ô tích. Tích rồi → gửi bình thường.
 */
function initPayConfirm(form) {
    if (!form) {
        return;
    }
    const box = form.querySelector("[data-pay-confirm]");
    form.addEventListener("submit", function (event) {
        if (box && !box.checked) {
            event.preventDefault();
            box.closest("label").classList.add("is-invalid");
            box.focus();
            showToast("warning", "Đếm tiền / kiểm chuyển khoản xong thì tích ô \"Đã nhận đủ\" rồi mới bấm Xác nhận đã thu tiền.");
        }
    });
    if (box) {
        box.addEventListener("change", function () {
            box.closest("label").classList.remove("is-invalid");
        });
    }
}

/* ==================== PHẦN 3 — KHUNG "ĐƠN ĐANG GIỮ CHỖ" ==================== */

/**
 * HÀM PHỤ (HÀM CHÍNH bước 4) — mỗi dòng có ô đếm data-countdown-seconds (số giây SERVER tính, common.js hiện mm:ss).
 * Hết giờ → gỡ dòng; gỡ hết dòng → ẩn cả khung. Server vẫn là nơi quyết định: đơn hết hạn bị nhả ở lần mở màn kế tiếp
 * (releaseExpired) và mở ?order= của đơn hết hạn vẫn bị từ chối bằng MSG108.
 */
function initHoldList(section) {
    if (!section) {
        return;
    }
    section.querySelectorAll("li").forEach(function (row) {
        const clock = row.querySelector("[data-countdown-seconds]");
        const seconds = clock ? Number(clock.dataset.countdownSeconds) : 0;
        setTimeout(function () {
            row.remove();
            if (!section.querySelector("li")) {
                section.remove();
            }
        }, Math.max(0, seconds) * 1000 + 300);
    });
}


/* ==================== PHẦN 1. Ô TÌM KHÁCH CÓ GỢI Ý ==================== */

/**
 * HÀM CHÍNH PHẦN 1 — gắn gợi ý cho 1 ô tìm. box = <form|div data-combobox="customer|partner">.
 * Các hàm load / render / buildOption / highlight / choose / open / close nằm BÊN TRONG hàm này
 * (dùng chung biến input, list, rows, active của đúng ô này).
 */
function initSaleCombobox(box) {
    const input = box.querySelector("[role=combobox]");   // ô <input> gõ chữ
    const list = box.querySelector("[role=listbox]");     // <ul> rỗng — JS thêm <li> gợi ý
    const mode = box.dataset.combobox;                    // "customer" | "partner" (từ data-combobox trong JSP)
    let rows = [];          // gợi ý đang hiện (dữ liệu JSON)
    let active = -1;        // vị trí dòng đang tô bằng phím ↑ ↓
    let timer = 0;
    let requestNo = 0;      // chỉ vẽ kết quả của lần gõ mới nhất

    // Gõ chữ → chờ 200 ms (gõ tiếp thì hủy hẹn giờ cũ) → load. Chưa gõ gì → đóng danh sách.
    input.addEventListener("input", function () {
        clearTimeout(timer);
        const query = input.value.trim();
        if (query.length === 0) {
            close();
            return;
        }
        timer = setTimeout(function () { load(query); }, SALE_SUGGEST_DELAY);
    });

    // Phím: ↑ ↓ tô dòng, Enter chọn dòng đang tô, Esc đóng.
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
            choose(rows[active]); // Chọn 1 khách
        } else if (event.key === "Enter" && mode === "partner") {
            event.preventDefault();   // ô người tập cùng nằm trong form các bước: Enter không gửi form
        } else if (event.key === "Escape") {
            close();
        }
    });

    // Quay lại ô (focus) mà đã có gợi ý → mở lại. Bấm ra ngoài ô → đóng.
    input.addEventListener("focus", function () {
        if (rows.length > 0 && input.value.trim().length >= 1) {
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

    /** HÀM PHỤ — hỏi server rồi vẽ. requestNo: chỉ vẽ kết quả của lần gõ MỚI NHẤT (bỏ kết quả về muộn). 
     * Hàm getJson đợi kết quả server trả về
     * */
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
            // Người tập cùng không thể là chính người đứng tên (customer trên URL).
            const holderId = Number(new URL(window.location.href).searchParams.get("customer"));
            rows = rows.filter(function (row) { return row.id !== holderId; });
        }
        render(query, result.success ? "" : result.message);
    }

    /** HÀM PHỤ — xóa danh sách cũ, vẽ lại các dòng (hoặc 1 dòng "Không tìm thấy…"). */
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

    /** HÀM PHỤ — 1 dòng gợi ý: vòng tròn chữ đầu · tên · "mã · SĐT" · ghi chú ("Gói tập tới …" hoặc lý do đỏ). */
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

    /** HÀM PHỤ — tô dòng thứ index (phím ↑ ↓) và báo cho trình đọc màn hình (aria-activedescendant). */
    function highlight(index) {
        list.querySelectorAll("[role=option]").forEach(function (item, i) {
            item.setAttribute("aria-selected", String(i === index));
        });
        active = index;
        const item = list.querySelector("[data-index=\"" + index + "\"]");
        input.setAttribute("aria-activedescendant", item.id);
        item.scrollIntoView({ block: "nearest" });
    }

    /** HÀM PHỤ — chọn 1 khách: URL hiện tại + customer (hoặc partner) = id, bỏ q / order, giữ lịch (withPlan) → tải trang. */
function choose(row) {
    close();                                                     // (a) đóng danh sách gợi ý
    input.value = row.name;                                      // (b) hiện tên khách trong ô cho lễ tân thấy
    const url = new URL(window.location.href);                   // (c) lấy URL hiện tại
    url.searchParams.set(mode === "customer" ? "customer" : "partner", String(row.id));   // (d)
    url.searchParams.delete("q");                                // (e) bỏ chữ tìm
    url.searchParams.delete("order");                            // (e) bỏ mã đơn (nếu có)
    window.location.href = withPlan(url.toString());             // (f) gắn lịch rồi chuyển trang
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

/**
 * HÀM PHỤ DÙNG CHUNG (hàm chính click data-keep-plan + choose) — URL mới GIỮ NGUYÊN lịch đang chọn: t1..t5 (pt-sale-plan.js đã ghi lên URL) + sessions (các buổi, kể cả buổi sửa riêng,
 * ô ẩn của form Giữ lịch). Server đọc sessions → pt-sale-plan.js khôi phục đúng các buổi sửa riêng.
 */
function withPlan(href) {
    const url = new URL(href, window.location.href);
    const current = new URL(window.location.href);
    for (let n = 1; n <= 5; n++) {
        const time = current.searchParams.get("t" + n);
        if (time !== null) {
            url.searchParams.set("t" + n, time);
        }
    }
    // sessions lấy từ URL (pt-sale-plan.js ghi MỌI buổi dự định khi có sửa riêng), KHÔNG lấy ô ẩn của form Giữ lịch:
    // ô ẩn chỉ có buổi xếp được → ngày đang đỏ sẽ bị server hiểu thành "Bỏ buổi này" sau khi chọn khách.
    const sessions = current.searchParams.get("sessions");
    if (sessions) {
        url.searchParams.set("sessions", sessions);
    } else {
        url.searchParams.delete("sessions");
    }
    return url.toString();
}

/** HÀM PHỤ — tạo thẻ HTML có chữ (textContent: chữ của khách không bao giờ bị hiểu là HTML). */
function textElement(tag, className, text) {
    const element = document.createElement(tag);
    if (className) {
        element.className = className;
    }
    element.textContent = text == null ? "" : String(text);
    return element;
}


/* ==================== PHẦN 2. FORM BƯỚC 1 TỰ GỬI ==================== */

/**
 * HÀM CHÍNH PHẦN 2 — form = <form id="sale-form" data-auto-submit> (_pt-sale-step1.jspf).
 * Đổi radio gói / PT → gửi ngay. Đổi ô ngày → kiểm min/max, đúng thì chờ 0,7 giây rồi gửi; sai thì hiện dòng đỏ.
 */
function initSaleAutoSubmit(form) {
    if (!form) {
        return;
    }
    let dateTimer = 0;
    // Quay lại bằng nút Back (trang lấy từ bộ nhớ đệm) → bỏ trạng thái mờ "đang gửi".
    window.addEventListener("pageshow", function () { form.classList.remove("is-busy"); });
    form.addEventListener("change", function (event) {
        const field = event.target;
        if (!field.matches("input[type=radio], input[type=date]")) { // chỉ quan tâm type=radio gói + PT + ô ngày 
            return;
        }
        clearTimeout(dateTimer);
        if (field.matches("[data-start-field]")) { // ← ô ngày bắt đầu
            // Ngày sai (trống, trước hôm nay, quá 30 ngày) → báo dưới ô, KHÔNG gửi, form không bị khóa.
            const isValid = field.value !== "" && field.checkValidity(); //check min + max
            showStartError(field, !isValid);
            if (isValid) {
                dateTimer = setTimeout(function () { submitSteps(form, true); }, SALE_DATE_DELAY); // delay tải trang khi chọn ngày bắt đầu 700ms
            }
            return;
        }
        // còn lại = radio gói hoặc radio PT, nếu ko == package thì == "pt"
        submitSteps(form, field.name === "package");
    });

}

/**
submitSteps là hàm gửi form bước 1 (Gói & PT) lên server, có mang theo lịch mẫu khi cần. Ba trường hợp đổi gói, đổi PT, đổi ngày đều gọi hàm này. Nó làm 4 việc theo thứ tự:
 */
function submitSteps(form, keepPattern) {
    // 1. Kiểm form trước khi gửi
    if (!form.checkValidity()) {
        form.reportValidity();
        return;
    }
    
    // 2. Dọn ô ẩn cũ
    form.querySelectorAll("input[data-kept-pattern]").forEach(function (input) { input.remove(); });
    
    // 3. Mang theo lịch mẫu nếu cần, nghĩa là đã chọn 7:00 ngày 23456 thì đổi gói hay đổi ngày vx giữ cái này, còn đổi PT thì ko giữ
    if (keepPattern) {
        const current = new URL(window.location.href);
        for (let n = 1; n <= 5; n++) {
            const time = current.searchParams.get("t" + n);
            if (time) {
                const input = document.createElement("input");
                input.type = "hidden";
                input.name = "t" + n;
                input.value = time;
                input.dataset.keptPattern = "";
                form.append(input);
            }
        }
    }
    
    // 4. Làm mờ form rồi gửi
    form.classList.add("is-busy");
    form.requestSubmit();
}

/** HÀM PHỤ — hiện / ẩn dòng đỏ #sale-start-error dưới ô ngày. */
function showStartError(field, hasError) {
    const message = document.getElementById("sale-start-error");
    field.setAttribute("aria-invalid", String(hasError));
    if (message) {
        message.hidden = !hasError;
    }
}
