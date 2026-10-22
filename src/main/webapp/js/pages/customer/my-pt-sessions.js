"use strict";

// Khối chi tiết bên phải: nhớ thẻ buổi đã bấm để trả focus khi đóng dialog.
let sessionDetailTrigger = null;

/**
 * Bấm thẻ buổi trong lịch tuần → mở “Chi tiết buổi tập” ở #session-drawer.
 * Nội dung đã có trong JSP; chỉ chép đúng buổi sang dialog, không gọi lại server.
 */
function openSessionDetail(event) {
    // B1: từ summary được bấm, tìm thẻ details của buổi và khung dialog dùng chung.
    const summary = event.currentTarget;
    const detail = summary.parentElement;
    const drawer = document.getElementById("session-drawer");
    if (!drawer || typeof drawer.showModal !== "function") {
        return;
    }
    // B2: chặn details mở ngay trong ô lịch; chép phần chi tiết đã được JSP escape.
    event.preventDefault();
    const source = detail.querySelector("[data-session-content]");
    const destination = document.getElementById("session-drawer-content");
    destination.replaceChildren(source.cloneNode(true));
    document.getElementById("session-copy-message").textContent = "";
    // B3: ghi nhớ nút mở và báo cho trình đọc màn hình rằng chi tiết đang mở.
    sessionDetailTrigger = summary;
    summary.setAttribute("aria-expanded", "true");
    // B4: mở dialog, đưa nội dung về đầu và khóa cuộn trang nền; dialog quản lý Tab/Escape.
    drawer.showModal();
    drawer.querySelector(".session-drawer-body").scrollTop = 0;
    document.body.classList.add("session-detail-open");
}
// ===== KẾT THÚC openSessionDetail: mở chi tiết buổi tập =====

/**
 * Nút “Sao chép số” trong khối liên hệ PT đọc SĐT từ data-copy-phone.
 * Báo kết quả ở #session-copy-message; nếu bị chặn thì chọn sẵn số để tự sao chép.
 */
async function copyPtPhone(button) {
    const phone = button.dataset.copyPhone;
    const drawer = document.getElementById("session-drawer");
    const message = document.getElementById("session-copy-message");
    // B1: dùng Clipboard API nếu trình duyệt cho phép trên HTTPS/localhost.
    try {
        await navigator.clipboard.writeText(phone);
        button.textContent = "Đã sao chép";
        if (drawer.open) {
            message.textContent = "Đã sao chép số điện thoại PT.";
        }
    } catch (error) {
        // B2: Clipboard bị chặn → tìm hoặc tạo ô SĐT chỉ đọc cạnh nút; không báo thành công giả.
        let fallback = button.parentElement.querySelector("[data-phone-fallback]");
        if (!fallback) {
            fallback = document.createElement("input");
            fallback.type = "text";
            fallback.readOnly = true;
            fallback.dataset.phoneFallback = "true";
            fallback.setAttribute("aria-label", "Số PT: nhấn Ctrl+C hoặc giữ để sao chép");
            button.parentElement.appendChild(fallback);
        }
        // B3: chọn sẵn SĐT và hướng dẫn Ctrl+C; thông báo trong dialog dùng role=status.
        fallback.value = phone;
        fallback.focus();
        fallback.select();
        button.textContent = "Chọn số để sao chép";
        if (drawer.open) {
            message.textContent = "Trình duyệt chưa cho phép sao chép. Nhấn Ctrl+C hoặc giữ số để sao chép.";
        }
    }
}

// ===== KẾT THÚC copyPtPhone: sao chép SĐT PT =====

/**
 * Nối các thẻ [data-session-detail] với dialog, nút Đóng, bấm ngoài và sao chép SĐT.
 * Dùng chung cho Lịch tập và Gói của tôi vì cả hai đều include các fragment chi tiết.
 */
function initSessionDetails() {
    // B1: nối mỗi summary với openSessionDetail nếu hỗ trợ dialog; không hỗ trợ vẫn dùng details gốc.
    const drawer = document.getElementById("session-drawer");
    if (drawer && typeof drawer.showModal === "function") {
        const summaries = document.querySelectorAll("[data-session-detail] > summary");
        for (const summary of summaries) {
            summary.setAttribute("aria-haspopup", "dialog");
            summary.addEventListener("click", openSessionDetail);
        }
        // B2: nút “Đóng” ở góc trên dialog chỉ đóng khung, không submit form.
        drawer.querySelector("[data-close-session]").addEventListener("click", function () {
            drawer.close();
        });
        // B3: chỉ đóng khi bấm ngoài đường biên dialog; chọn chữ/bấm bên trong vẫn giữ chi tiết.
        drawer.addEventListener("click", function (event) {
            const bounds = drawer.getBoundingClientRect();
            const outside = event.clientX < bounds.left || event.clientX > bounds.right
                    || event.clientY < bounds.top || event.clientY > bounds.bottom;
            if (event.target === drawer && outside) {
                drawer.close();
            }
        });
        // B4: mọi cách đóng (nút, bấm ngoài, Escape) đều mở lại cuộn nền và trả focus về thẻ buổi.
        drawer.addEventListener("close", function () {
            document.body.classList.remove("session-detail-open");
            if (sessionDetailTrigger) {
                sessionDetailTrigger.setAttribute("aria-expanded", "false");
                sessionDetailTrigger.focus({ preventScroll: true });
            }
        });
    }
    // B5: nghe click ở document để nút copy trong nội dung vừa chép vào dialog cũng hoạt động.
    document.addEventListener("click", function (event) {
        const button = event.target.closest("[data-copy-phone]");
        if (button) {
            copyPtPhone(button);
        }
    });
}

// ===== KẾT THÚC initSessionDetails: nối sự kiện cho khối chi tiết =====

/**
 * Dãy nút ngày trên mobile: chọn một ngày và hiện cột lịch tương ứng; desktop vẫn đủ 7 cột.
 * highlightSelection=true khi người dùng chọn ngày; false khi chỉ đặt cột mặc định lúc mở trang.
 */
function selectScheduleDay(button, highlightSelection) {
    // B1: data-day-index nối nút ngày với cột bảng; cập nhật nút đang chọn và aria-pressed.
    const selectedIndex = button.dataset.dayIndex;
    const dayButtons = document.querySelectorAll("[data-day-index]");
    for (const dayButton of dayButtons) {
        dayButton.setAttribute("aria-pressed", String(dayButton === button));
        dayButton.classList.toggle("is-selected", highlightSelection && dayButton === button);
    }
    // B2: đánh dấu cột cần hiện trên mobile và viền ngày chọn; giữ nguyên class is-today.
    const columns = document.querySelectorAll("[data-week-column]");
    for (const column of columns) {
        const matchesDay = column.dataset.weekColumn === selectedIndex;
        column.classList.toggle("is-mobile-day", matchesDay);
        column.classList.toggle("is-selected", highlightSelection && matchesDay);
    }
    // B3: giữ ngày vừa chọn trong ô “Chọn ngày” và link Tuần/Tháng, không gửi request mới.
    if (highlightSelection) {
        document.getElementById("schedule-date").value = button.dataset.dayDate;
        const links = document.querySelectorAll(".schedule-switch a");
        for (const link of links) {
            const url = new URL(link.href);
            url.searchParams.set("date", button.dataset.dayDate);
            link.href = url.href;
        }
    }
}

// ===== KẾT THÚC selectScheduleDay: đổi ngày đang xem trên mobile =====

/**
 * Khởi tạo dãy 7 nút ngày của lịch tuần (.schedule-mobile-days); lịch tháng không có khối này.
 * Cột mặc định: ngày URL đang chọn → hôm nay nếu nằm trong tuần → Thứ 2.
 */
function initScheduleDays() {
    // B1: tìm khối nút do JSP dựng từ timetable.days; không có thì kết thúc.
    const dayPicker = document.querySelector(".schedule-mobile-days");
    if (!dayPicker) {
        return;
    }
    const buttons = dayPicker.querySelectorAll("[data-day-index]");
    const requestedDate = dayPicker.dataset.selectedDate;
    let todayButton = null;
    let requestedButton = null;
    // B2: tìm nút khớp selectedDate/hôm nay và gắn thao tác bấm từng ngày.
    for (const button of buttons) {
        if (button.dataset.today === "true") {
            todayButton = button;
        }
        if (button.dataset.dayDate === requestedDate) {
            requestedButton = button;
        }
        button.addEventListener("click", function () {
            selectScheduleDay(button, true);
        });
    }
    // B3: chọn cột mặc định; chỉ tô viền “Đang chọn” nếu JSP có selectedDate.
    const selectedButton = requestedButton || todayButton || buttons[0];
    selectScheduleDay(selectedButton, Boolean(requestedDate));
    // B4: hiện dãy nút và bật CSS một cột trên mobile; không có JS vẫn cuộn bảng tuần được.
    dayPicker.hidden = false;
    document.querySelector(".pt-schedule").classList.add("has-day-picker");
}
// ===== KẾT THÚC initScheduleDays: khởi tạo bộ chọn ngày mobile =====

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

// JSP đã tạo xong HTML → nối sự kiện chi tiết và bộ chọn ngày; không tải thêm dữ liệu.
document.addEventListener("DOMContentLoaded", initSessionDetails);
document.addEventListener("DOMContentLoaded", initScheduleDays);
document.addEventListener("DOMContentLoaded", initFocusSession);
// ===== KẾT THÚC đăng ký khởi tạo sau khi JSP tải xong =====
