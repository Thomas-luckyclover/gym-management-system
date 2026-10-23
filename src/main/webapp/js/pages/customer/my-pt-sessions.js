"use strict";
/*
 * BẢN HỌC — viết lại my-pt-sessions.js cho dễ đọc, CHẠY Y HỆT bản gốc.
 * JS ở màn Lịch tập CHỈ LO GIAO DIỆN: không lấy dữ liệu, không gọi server.
 * Dữ liệu chi tiết buổi đã được Service điền + JSP in sẵn (ẩn) trong từng thẻ buổi.
 *
 * 5 NHÓM HÀM:
 *   PHẦN 1. Dialog "Chi tiết buổi tập": bấm thẻ → mở, các cách đóng
 *   PHẦN 2. Nút "Sao chép số" PT
 *   PHẦN 3. Dãy nút chọn ngày trên mobile (ẩn/hiện cột)
 *   PHẦN 4. Mở đúng buổi từ deep link
 *   PHẦN 5. Khởi động: trang tải xong thì gắn sự kiện
 *
 * Các id/thuộc tính JS dựa vào (do JSP in ra):
 *   #session-drawer          dialog dùng chung        (_session-drawer.jspf)
 *   #session-drawer-content  khung rỗng trong dialog  (_session-drawer.jspf)
 *   #session-copy-message    dòng báo đã sao chép     (_session-drawer.jspf)
 *   [data-session-detail]    1 thẻ buổi (details)     (_session-card.jspf)
 *   [data-session-content]   chi tiết ẩn trong thẻ    (_session-card.jspf)
 *   [data-copy-phone]        nút Sao chép số          (_session-detail.jspf)
 *   [data-day-index]         nút ngày mobile          (my-pt-sessions.jsp:94)
 *   [data-week-column]       ô/tiêu đề của 1 cột ngày (my-pt-sessions.jsp:115, :139)
 */

// Nhớ thẻ vừa bấm để khi đóng dialog thì trả focus về đúng thẻ.
let lastClickedCard = null;

// ================================================================================================================
// 1. DIALOG CHI TIẾT — initSessionDetails gắn sự kiện; các hàm mở / đóng nằm ngay dưới
// ================================================================================================================

/** Gắn sự kiện cho dialog: bấm thẻ để mở + 3 cách đóng. */
function initSessionDetails() {
    const drawer = getDrawer();
    if (drawer) {
        // Mở: mọi thẻ buổi trên trang
        const summaries = document.querySelectorAll("[data-session-detail] > summary");
        for (const summary of summaries) {
            summary.setAttribute("aria-haspopup", "dialog");
            summary.addEventListener("click", openSessionDetail);
        }
        // Đóng cách 1: nút "Đóng" (type=button, không submit form)
        drawer.querySelector("[data-close-session]").addEventListener("click", () => drawer.close());
        // Đóng cách 2: bấm ra ngoài
        drawer.addEventListener("click", (event) => closeWhenClickOutside(drawer, event));
        // Đóng cách 3: phím Esc — <dialog> tự xử lý, không cần code
        // Sau khi đóng (cả 3 cách)
        drawer.addEventListener("close", afterDrawerClosed);
    }
    // Nút Sao chép số (Phần 2) — gắn ở document vì nút nằm trong nội dung VỪA chép vào dialog
    document.addEventListener("click", (event) => {
        const button = event.target.closest("[data-copy-phone]");
        if (button) {
            copyPtPhone(button);
        }
    });
}

/** SỰ KIỆN: bấm vào phần nhìn thấy của thẻ buổi (summary). */
function openSessionDetail(event) {
    const drawer = getDrawer();
    if (!drawer) {
        return;                                // không có dialog → để <details> tự xổ như HTML thường
    }
    const summary = event.currentTarget;       // phần vừa bấm
    const card = summary.parentElement;        // cả thẻ buổi <details>

    event.preventDefault();                    // B1: không cho <details> xổ ngay trong ô lịch
    copyDetailIntoDrawer(card);                // B2: đổ chi tiết buổi này vào dialog

    lastClickedCard = summary;                 // B3: nhớ thẻ để trả focus khi đóng
    summary.setAttribute("aria-expanded", "true");

    drawer.showModal();                        // B4: mở dialog
    drawer.querySelector(".session-drawer-body").scrollTop = 0;   // cuộn nội dung lên đầu
    document.body.classList.add("session-detail-open");          // CSS khóa cuộn trang nền
}

/** Lấy dialog dùng chung. Trả null nếu trình duyệt quá cũ không hỗ trợ <dialog>. */
function getDrawer() {
    const drawer = document.getElementById("session-drawer");
    if (!drawer || typeof drawer.showModal !== "function") {
        return null;
    }
    return drawer;
}

/** Chép khối chi tiết (đang ẩn trong thẻ) vào khung rỗng của dialog. */
function copyDetailIntoDrawer(card) {
    const hiddenDetail = card.querySelector("[data-session-content]");   // nguồn: chi tiết ẩn
    const drawerBox = document.getElementById("session-drawer-content"); // đích: khung rỗng
    drawerBox.replaceChildren(hiddenDetail.cloneNode(true));              // xóa cũ, đặt bản sao
    document.getElementById("session-copy-message").textContent = "";    // xóa thông báo cũ
}

/** Bấm ra ngoài khung dialog (vùng nền mờ) thì đóng. Bấm/chọn chữ bên trong thì không. */
function closeWhenClickOutside(drawer, event) {
    const box = drawer.getBoundingClientRect();
    const outside = event.clientX < box.left || event.clientX > box.right
            || event.clientY < box.top || event.clientY > box.bottom;
    if (event.target === drawer && outside) {
        drawer.close();
    }
}

/** Chạy sau MỌI cách đóng (nút Đóng, bấm ngoài, phím Esc): trả lại trang như cũ. */
function afterDrawerClosed() {
    document.body.classList.remove("session-detail-open");       // mở lại cuộn trang
    if (lastClickedCard) {
        lastClickedCard.setAttribute("aria-expanded", "false");
        lastClickedCard.focus({ preventScroll: true });           // con trỏ về thẻ vừa bấm
    }
}

// ================================================================================================================
// 2. SAO CHÉP SỐ PT — copyPtPhone gọi các hàm báo kết quả và cách chép dự phòng ngay dưới
// ================================================================================================================

/** SỰ KIỆN: bấm "Sao chép số". SĐT lấy từ data-copy-phone do JSP in. */
async function copyPtPhone(button) {
    const phone = button.dataset.copyPhone;
    try {
        await navigator.clipboard.writeText(phone);          // cách chính: Clipboard API
        button.textContent = "Đã sao chép";
        showCopyMessage("Đã sao chép số điện thoại PT.");
    } catch (error) {
        showPhoneForManualCopy(button, phone);               // dự phòng: KHÔNG báo thành công giả
        button.textContent = "Chọn số để sao chép";
        showCopyMessage("Trình duyệt chưa cho phép sao chép. Nhấn Ctrl+C hoặc giữ số để sao chép.");
    }
}

/** Ghi 1 dòng thông báo trong dialog (chỉ khi dialog đang mở). */
function showCopyMessage(text) {
    const drawer = document.getElementById("session-drawer");
    if (drawer.open) {
        document.getElementById("session-copy-message").textContent = text;
    }
}

/** Trình duyệt chặn clipboard → hiện ô chứa SĐT, bôi đen sẵn để khách tự Ctrl+C. */
function showPhoneForManualCopy(button, phone) {
    let input = button.parentElement.querySelector("[data-phone-fallback]");
    if (!input) {                               // chưa có ô thì tạo 1 lần
        input = document.createElement("input");
        input.type = "text";
        input.readOnly = true;
        input.dataset.phoneFallback = "true";
        input.setAttribute("aria-label", "Số PT: nhấn Ctrl+C hoặc giữ để sao chép");
        button.parentElement.appendChild(input);
    }
    input.value = phone;
    input.focus();
    input.select();                             // bôi đen số
}

// ================================================================================================================
// 3. CHỌN NGÀY MOBILE — initScheduleDays gọi selectScheduleDay và các hàm đổi cột ngay dưới
// ================================================================================================================

/** Khởi tạo dãy 7 nút ngày (chỉ có ở lịch TUẦN). Cột mặc định: ngày URL chọn → hôm nay → Thứ 2. */
function initScheduleDays() {
    const dayPicker = document.querySelector(".schedule-mobile-days");
    if (!dayPicker) {
        return;                                  // đang xem Tháng → không có dãy nút
    }
    const buttons = dayPicker.querySelectorAll("[data-day-index]");
    const requestedDate = dayPicker.dataset.selectedDate;   // selectedDate do Servlet đặt

    let todayButton = null;
    let requestedButton = null;
    for (const button of buttons) {
        if (button.dataset.today === "true") todayButton = button;
        if (button.dataset.dayDate === requestedDate) requestedButton = button;
        button.addEventListener("click", () => selectScheduleDay(button, true));
    }

    const defaultButton = requestedButton || todayButton || buttons[0];
    selectScheduleDay(defaultButton, Boolean(requestedDate));

    dayPicker.hidden = false;                    // có JS mới hiện dãy nút
    document.querySelector(".pt-schedule").classList.add("has-day-picker");   // CSS: 1 cột trên mobile
}

/**
 * Chọn 1 ngày. highlight = true khi NGƯỜI DÙNG bấm (tô viền "Đang chọn"),
 * false khi chỉ đặt cột mặc định lúc mở trang.
 */
function selectScheduleDay(button, highlight) {
    markDayButton(button, highlight);
    showOnlyColumn(button.dataset.dayIndex, highlight);
    if (highlight) {
        rememberDateInLinks(button.dataset.dayDate);
    }
}

/** Đánh dấu nút ngày được chọn trong dãy 7 nút. */
function markDayButton(chosenButton, highlight) {
    for (const button of document.querySelectorAll("[data-day-index]")) {
        const isChosen = button === chosenButton;
        button.setAttribute("aria-pressed", String(isChosen));
        button.classList.toggle("is-selected", highlight && isChosen);
    }
}

/** Chỉ hiện cột của ngày được chọn (CSS ẩn các cột không có is-mobile-day). */
function showOnlyColumn(dayIndex, highlight) {
    for (const column of document.querySelectorAll("[data-week-column]")) {
        const isThisDay = column.dataset.weekColumn === dayIndex;
        column.classList.toggle("is-mobile-day", isThisDay);
        column.classList.toggle("is-selected", highlight && isThisDay);
    }
}

/** Ghi ngày vừa chọn vào ô "Chọn ngày" và 2 link Tuần/Tháng (để chuyển cách xem giữ đúng ngày). */
function rememberDateInLinks(date) {
    document.getElementById("schedule-date").value = date;
    for (const link of document.querySelectorAll(".schedule-switch a")) {
        const url = new URL(link.href);
        url.searchParams.set("date", date);
        link.href = url.href;
    }
}

// ================================================================================================================
// 4. MỞ ĐÚNG BUỔI TỪ LINK — initFocusSession dùng dialog đã khởi tạo ở nhóm 1
// ================================================================================================================

/**
 * Đến từ "Gói của tôi" (?session=): tô viền buổi đó, cuộn tới và mở sẵn chi tiết.
 * Buổi không có trong tuần đang xem thì bỏ qua (ngày vẫn được tô nhờ ?date=).
 */
function initFocusSession() {
    // B1: mã buổi do JSP gắn trên main; không có thì thôi
    const main = document.getElementById("main-content");
    const sessionId = main ? main.dataset.focusSession : "";
    if (!sessionId) {
        return;
    }
    // B2: tìm thẻ buổi, tô viền, cuộn vào giữa màn hình
    const card = document.querySelector('[data-session-detail][data-session-id="' + CSS.escape(sessionId) + '"]');
    if (!card) {
        return;
    }
    card.classList.add("is-focus");
    card.scrollIntoView({ block: "center", behavior: "auto" });
    // B3: mở chi tiết như khi khách bấm thẻ (openSessionDetail đã được nối ở initSessionDetails)
    card.querySelector("summary").click();
}

// ================================================================================================================
// 5. KHỞI ĐỘNG — giữ thứ tự dialog → chọn ngày → mở buổi từ deep link
// ================================================================================================================

document.addEventListener("DOMContentLoaded", initSessionDetails);
document.addEventListener("DOMContentLoaded", initScheduleDays);
document.addEventListener("DOMContentLoaded", initFocusSession);
