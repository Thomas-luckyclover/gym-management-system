"use strict";

/*
 * TỔNG QUAN PT — dashboard.js
 *
 * JS CHỈ LÀM 1 VIỆC:
 *   Dải 7 ngày dạng tab: bấm ngày nào thì hiện bảng buổi của ngày đó.
 *
 * NHỮNG VIỆC KHÔNG NẰM Ở ĐÂY:
 *   - Nhãn "Đang diễn ra" / "Còn 20 phút": common.js (đọc data-start / data-end của từng buổi).
 *   - Khối chào + thời tiết: gym-floor.js, weather-card.js.
 *   - Nhắc khách thiếu membership: không còn nút, JOB-08 trên server tự gửi.
 *   - "Xử lý buổi" / "Dời buổi": là link sang Lịch dạy (?handle=), hộp xử lý nằm ở màn đó.
 *
 * MỤC LỤC
 *   NHÓM 1. Dải 7 ngày        initPtDayTabs, selectPtDay, handlePtDayKey
 */

document.addEventListener("DOMContentLoaded", function () {
    initPtDayTabs();
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
