"use strict";

/*
 * LỊCH TẬP CỦA KHÁCH — my-pt-sessions.js
 *
 * JS CHỈ LÀM 3 VIỆC (không lấy dữ liệu, không gọi server — mọi chữ đã có sẵn trong HTML):
 *   1. Bấm thẻ buổi → mở hộp "Chi tiết buổi tập" (chép phần chi tiết đang ẩn trong thẻ vào hộp).
 *   2. Nút "Sao chép số" PT.
 *   3. Đến từ "Gói của tôi" (?session=…) → mở sẵn chi tiết buổi đó.
 *
 * NHỮNG VIỆC KHÔNG CẦN JS:
 *   - Đổi tuần / tháng, chọn ngày, "Hôm nay": đều là link hoặc form GET.
 *   - Chọn ngày trên điện thoại: mỗi nút là link ?date=…, JSP đánh dấu cột cần hiện, CSS ẩn các cột khác.
 *
 * MỤC LỤC
 *   NHÓM 1. Hộp chi tiết     initSessionDrawer, openSessionDrawer, closeWhenClickOutside, afterDrawerClosed
 *   NHÓM 2. Sao chép số PT   copyPtPhone, showPhoneToCopyByHand
 *   NHÓM 3. Mở sẵn 1 buổi    openFocusSession
 */

document.addEventListener("DOMContentLoaded", function () {
    initSessionDrawer();
    openFocusSession();   // chạy sau initSessionDrawer vì cần hộp đã gắn sự kiện
});


// ================================================================================================================
// NHÓM 1. HỘP CHI TIẾT
// ================================================================================================================

const sessionDrawer = document.getElementById("session-drawer");             // <dialog> dùng chung (_session-drawer.jspf)
const drawerContent = document.getElementById("session-drawer-content");     // vùng rỗng trong hộp
const copyMessage = document.getElementById("session-copy-message");         // dòng báo "Đã sao chép"
let lastCard = null;                                                         // thẻ vừa bấm → đóng hộp thì trả focus về đây

// HÀM CHÍNH — gọi 1 lần khi trang tải xong (DOMContentLoaded ở đầu file).
function initSessionDrawer() {
    if (!sessionDrawer) {
        return;   // lịch lỗi → không có hộp; thẻ <details> vẫn tự xổ như HTML thường
    }

    // B1: mọi thẻ buổi — bấm phần nhìn thấy (summary) thì mở hộp.
    document.querySelectorAll("[data-session-detail] > summary").forEach(function (summary) {
        summary.setAttribute("aria-haspopup", "dialog");
        summary.addEventListener("click", openSessionDrawer);
    });

    // B2: 3 cách đóng — nút Đóng, bấm ra vùng tối bên ngoài, phím Esc (<dialog> tự lo).
    sessionDrawer.querySelector("[data-close-session]").addEventListener("click", function () {
        sessionDrawer.close();
    });
    sessionDrawer.addEventListener("click", closeWhenClickOutside);
    sessionDrawer.addEventListener("close", afterDrawerClosed);

    // B3: nút "Sao chép số" nằm trong nội dung VỪA chép vào hộp → bắt click ở document (lúc này nút chưa tồn tại).
    document.addEventListener("click", function (event) {
        const button = event.target.closest("[data-copy-phone]");
        if (button) {
            copyPtPhone(button);
        }
    });
}

// HÀM PHỤ — gọi ở B1 của initSessionDrawer, mỗi lần khách bấm 1 thẻ buổi.
function openSessionDrawer(event) {
    const summary = event.currentTarget;
    const card = summary.parentElement;                                       // cả thẻ <details>

    // B1: không cho <details> xổ ngay trong ô lịch.
    event.preventDefault();

    // B2: chép bản sao phần chi tiết đang ẩn trong thẻ vào hộp (replaceChildren = xóa nội dung cũ rồi đặt mới).
    drawerContent.replaceChildren(card.querySelector("[data-session-content]").cloneNode(true));
    copyMessage.textContent = "";

    // B3: nhớ thẻ để trả focus, mở hộp, cuộn nội dung lên đầu, khóa cuộn trang phía sau.
    lastCard = summary;
    summary.setAttribute("aria-expanded", "true");
    sessionDrawer.showModal();
    sessionDrawer.querySelector(".session-drawer-body").scrollTop = 0;
    document.body.classList.add("session-detail-open");
}

// HÀM PHỤ — gọi ở B2 của initSessionDrawer. Bấm vào vùng tối ngoài khung thì đóng; bấm / bôi chữ bên trong thì không.
function closeWhenClickOutside(event) {
    const box = sessionDrawer.getBoundingClientRect();
    const outside = event.clientX < box.left || event.clientX > box.right
            || event.clientY < box.top || event.clientY > box.bottom;
    if (event.target === sessionDrawer && outside) {
        sessionDrawer.close();
    }
}

// HÀM PHỤ — gọi ở B2 của initSessionDrawer, sau MỌI cách đóng: trả trang về như cũ.
function afterDrawerClosed() {
    document.body.classList.remove("session-detail-open");
    if (lastCard) {
        lastCard.setAttribute("aria-expanded", "false");
        lastCard.focus({ preventScroll: true });
    }
}


// ================================================================================================================
// NHÓM 2. SAO CHÉP SỐ PT
// ================================================================================================================

// HÀM CHÍNH — gọi ở B3 của initSessionDrawer khi bấm nút có data-copy-phone (SĐT do JSP in vào thuộc tính).
async function copyPtPhone(button) {
    const phone = button.dataset.copyPhone;
    try {
        // B1: cách chính — Clipboard API.
        await navigator.clipboard.writeText(phone);
        button.textContent = "Đã sao chép";
        copyMessage.textContent = "Đã sao chép số điện thoại PT.";
    } catch (error) {
        // B2: trình duyệt chặn → hiện ô chứa số, bôi đen sẵn để khách tự Ctrl+C. Không báo "thành công" giả.
        showPhoneToCopyByHand(button, phone);
        button.textContent = "Chọn số để sao chép";
        copyMessage.textContent = "Trình duyệt chưa cho phép sao chép. Nhấn Ctrl+C hoặc giữ số để sao chép.";
    }
}

// HÀM PHỤ — gọi ở B2 của copyPtPhone. Tạo ô chữ 1 lần, các lần sau dùng lại.
function showPhoneToCopyByHand(button, phone) {
    let input = button.parentElement.querySelector("[data-phone-fallback]");
    if (!input) {
        input = document.createElement("input");
        input.type = "text";
        input.readOnly = true;
        input.dataset.phoneFallback = "true";
        input.setAttribute("aria-label", "Số PT: nhấn Ctrl+C hoặc giữ để sao chép");
        button.parentElement.appendChild(input);
    }
    input.value = phone;
    input.focus();
    input.select();
}


// ================================================================================================================
// NHÓM 3. MỞ SẴN 1 BUỔI (đến từ "Gói của tôi" hoặc bấm 1 buổi ở lịch tháng: URL có ?session=…)
// ================================================================================================================

// HÀM CHÍNH — gọi 1 lần khi trang tải xong, SAU initSessionDrawer.
function openFocusSession() {
    // B1: mã buổi do JSP gắn trên <main data-focus-session="…">; không có thì thôi.
    const sessionId = document.getElementById("main-content").dataset.focusSession;
    if (!sessionId) {
        return;
    }

    // B2: tìm thẻ buổi trong tuần đang xem. Không thấy (buổi ở tuần khác / không phải của mình) → bỏ qua.
    const card = document.querySelector('[data-session-detail][data-session-id="' + CSS.escape(sessionId) + '"]');
    if (!card) {
        return;
    }

    // B3: tô viền thẻ, cuộn tới giữa màn hình, rồi "bấm" giúp để mở hộp chi tiết.
    card.classList.add("is-focus");
    card.scrollIntoView({ block: "center" });
    card.querySelector("summary").click();
}
