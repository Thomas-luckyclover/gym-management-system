"use strict";

/*
 * TỔNG QUAN PT — dashboard.js
 *
 * JS CHỈ LÀM 2 VIỆC:
 *   1. Dải 7 ngày dạng tab: bấm ngày nào thì hiện bảng buổi của ngày đó.
 *   2. Hộp "Tùy chọn lịch buổi tập": mở khi bấm "Xử lý buổi" (lịch) hoặc "Dời buổi" (thẻ khách).
 *
 * NHỮNG VIỆC KHÔNG NẰM Ở ĐÂY:
 *   - Nhãn "Đang diễn ra" / "Còn 20 phút": common.js (đọc data-start / data-end của từng buổi).
 *   - Khối chào + thời tiết: gym-floor.js, weather-card.js.
 *   - Nhắc khách thiếu membership: không còn nút, JOB-08 trên server tự gửi.
 *
 * MỤC LỤC
 *   NHÓM 1. Dải 7 ngày        initPtDayTabs, selectPtDay, handlePtDayKey
 *   NHÓM 2. Hộp xử lý buổi    initPtSessionDialog, openPtSessionDialog
 */

document.addEventListener("DOMContentLoaded", function () {
    initPtDayTabs();
    initPtSessionDialog();
});


// ================================================================================================================
// NHÓM 1. DẢI 7 NGÀY
// JSP đã vẽ đủ 7 bảng (tắt JS vẫn đọc được). JS chỉ ẩn 6 bảng, hiện bảng của ngày đang chọn.
// ================================================================================================================

const ptDayStrip = document.querySelector("[data-pt-days]");                      // <div role="tablist">
const ptDayTabs = ptDayStrip ? Array.from(ptDayStrip.querySelectorAll('[role="tab"]')) : [];

// HÀM CHÍNH — gọi 1 lần khi trang tải xong (DOMContentLoaded ở đầu file).
function initPtDayTabs() {
    if (!ptDayTabs.length) {
        return;   // lịch lỗi / không có dữ liệu → không có dải ngày
    }

    // B1: mở sẵn ngày JSP đánh dấu aria-selected="true" (hôm nay); không có thì ngày đầu tiên.
    const today = ptDayTabs.find(function (tab) { return tab.getAttribute("aria-selected") === "true"; });
    selectPtDay(today || ptDayTabs[0], false);

    // B2: báo CSS "JS đã chạy" → CSS mới tô nền xanh cho ngày đang chọn.
    ptDayStrip.classList.add("is-ready");

    // B3: bấm chuột vào 1 ngày.
    ptDayStrip.addEventListener("click", function (event) {
        const tab = event.target.closest('[role="tab"]');
        if (tab) {
            selectPtDay(tab, false);
        }
    });

    // B4: phím mũi tên / Home / End (chuẩn tab của WAI-ARIA).
    ptDayStrip.addEventListener("keydown", handlePtDayKey);
}

// HÀM PHỤ — gọi ở B1, B3 của initPtDayTabs và trong handlePtDayKey.
// Chọn 1 ngày: đổi aria-selected + tabindex, ẩn/hiện bảng, cuộn dải ngày để ô đó nằm giữa (điện thoại).
function selectPtDay(tab, moveFocus) {
    // B1: mỗi ô ngày — ô được chọn thì hiện bảng, các ô khác ẩn bảng.
    ptDayTabs.forEach(function (item) {
        const selected = item === tab;
        item.setAttribute("aria-selected", selected ? "true" : "false");
        item.tabIndex = selected ? 0 : -1;   // chỉ ô đang chọn nằm trong vòng Tab
        document.getElementById(item.getAttribute("aria-controls")).hidden = !selected;
    });

    // B2: điều khiển bằng bàn phím thì đưa focus theo.
    if (moveFocus) {
        tab.focus();
    }

    // B3: dải ngày đang cuộn ngang (điện thoại) → cuộn ô được chọn vào giữa. Chỉ cuộn dải, không kéo cả trang.
    if (ptDayStrip.scrollWidth > ptDayStrip.clientWidth) {
        const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        ptDayStrip.scrollTo({
            left: tab.offsetLeft - ptDayStrip.offsetLeft - (ptDayStrip.clientWidth - tab.offsetWidth) / 2,
            behavior: reduceMotion ? "instant" : "smooth"
        });
    }
}

// HÀM PHỤ — gọi ở B4 của initPtDayTabs. ← → đổi ngày (vòng tròn), Home / End về ngày đầu / cuối.
function handlePtDayKey(event) {
    const index = ptDayTabs.indexOf(document.activeElement);
    if (index < 0) {
        return;
    }

    const last = ptDayTabs.length - 1;
    let next = -1;
    if (event.key === "ArrowRight") next = index === last ? 0 : index + 1;
    if (event.key === "ArrowLeft") next = index === 0 ? last : index - 1;
    if (event.key === "Home") next = 0;
    if (event.key === "End") next = last;

    if (next >= 0) {
        event.preventDefault();   // không để trang cuộn theo phím
        selectPtDay(ptDayTabs[next], true);
    }
}


// ================================================================================================================
// NHÓM 2. HỘP XỬ LÝ BUỔI
// Nút "Xử lý buổi" / "Dời buổi" có data-pt-session-action, data-session-id, data-session-label.
// Hộp hiện 4 phương án (đang khóa vì chưa có nghiệp vụ). Esc hoặc nút Đóng để đóng.
// ================================================================================================================

const ptSessionDialog = document.getElementById("ptSessionDialog");
const ptSessionDialogTitle = document.getElementById("ptSessionDialogTitle");
let ptOpeningButton = null;   // nút đã mở hộp → đóng hộp thì trả focus về đây

// HÀM CHÍNH — gọi 1 lần khi trang tải xong (DOMContentLoaded ở đầu file).
function initPtSessionDialog() {
    if (!ptSessionDialog) {
        return;
    }

    // B1: bấm bất kỳ nút nào có data-pt-session-action (trong lịch hoặc thẻ khách) → mở hộp.
    document.addEventListener("click", function (event) {
        const button = event.target.closest("[data-pt-session-action]");
        if (button) {
            openPtSessionDialog(button);
        }
    });

    // B2: nút Đóng.
    document.getElementById("ptSessionDialogClose").addEventListener("click", function () {
        ptSessionDialog.close();
    });

    // B3: hộp đóng (nút Đóng hoặc phím Esc) → xóa mã buổi, trả focus về nút đã mở.
    ptSessionDialog.addEventListener("close", function () {
        delete ptSessionDialog.dataset.sessionId;
        if (ptOpeningButton && ptOpeningButton.isConnected) {
            ptOpeningButton.focus();
        }
        ptOpeningButton = null;
    });
}

// HÀM PHỤ — gọi ở B1 của initPtSessionDialog.
function openPtSessionDialog(button) {
    // B1: nút bị khóa hoặc thiếu mã buổi → bỏ qua.
    if (button.disabled || !button.dataset.sessionId) {
        return;
    }

    // B2: nhớ nút + mã buổi, đặt tiêu đề. textContent (không phải innerHTML) → không chèn HTML lạ.
    ptOpeningButton = button;
    ptSessionDialog.dataset.sessionId = button.dataset.sessionId;
    ptSessionDialogTitle.textContent = button.dataset.sessionLabel || "Xử lý buổi tập";

    // B3: mở hộp dạng modal — trình duyệt tự giữ focus trong hộp, Esc để đóng.
    if (!ptSessionDialog.open) {
        ptSessionDialog.showModal();
    }
}
