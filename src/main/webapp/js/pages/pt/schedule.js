"use strict";

/*
 * Màn Lịch dạy PT (/pt/schedule). JS chỉ lo phần thao tác trên trình duyệt:
 * thêm khung mới (kéo thả hoặc nhập tay) và đánh dấu gỡ khung đã công bố.
 * Bấm Công bố mới gửi một POST → PtScheduleServlet.doPost → PtScheduleService.publish → DAO ghi DB
 * (thêm khung: insertOrReopenSlot; gỡ khung: withdrawFreeSlot, Free → Withdrawn).
 *
 * CÁCH ĐỌC FILE: mỗi nhóm có HÀM CHÍNH (trình duyệt gọi khi có sự kiện) và HÀM PHỤ ngay dưới.
 * Comment trên mỗi hàm phụ ghi rõ nó được gọi ở bước nào của hàm chính nào.
 *
 *   NHÓM 1. Khởi tạo            initPtSchedulePreview
 *   NHÓM 2. Kéo thả             startPtSlotDrag, movePtSlotDrag, finishPtSlotDrag, abortPtSlotDrag, cancelPtDragByKey
 *   NHÓM 3. Thêm nháp từ form   previewPtSlotFromForm, updatePtEndTime
 *   NHÓM 4. Danh sách chờ       removePtDraft, togglePtRemoval, clearPtSlotPreview
 *   NHÓM 5. Công bố             publishPtDrafts
 *   NHÓM 6. Đổi giờ dùng chung  ptClock, ptMinutes
 *
 * Thứ JS đọc từ JSP (pt/schedule.jsp):
 *   #pt-schedule          khung lịch; data-duration, data-axis-start/end, data-today, data-csrf, data-draft-key
 *   .pt-day-track         một cột ngày; data-date, data-open, data-close, data-closed
 *   .pt-slot-frame        một thẻ khung/buổi; data-slot-id = TimeSlotId
 *   [data-remove-slot]    nút "Gỡ khung" (JSP chỉ in khi Service cho canRemove = true)
 *   #pt-preview-form      form "Nhập ngày và giờ"
 *   #pt-draft-list        danh sách "Chờ công bố"
 */

const ptSchedule = document.getElementById("pt-schedule");
const ptPreviewForm = document.getElementById("pt-preview-form");
const ptPreviewStatus = document.getElementById("pt-preview-status"); // dòng hướng dẫn phía trên lịch
const ptDuration = Number(ptSchedule.dataset.duration);   // P-06: số phút mỗi khung, ví dụ 90
const ptAxisStart = Number(ptSchedule.dataset.axisStart); // phút đầu trục giờ, ví dụ 300 = 05:00
const ptAxisEnd = Number(ptSchedule.dataset.axisEnd);     // phút cuối trục giờ, ví dụ 1320 = 22:00
const ptDraftKey = "gms.pt-slots." + ptSchedule.dataset.draftKey; // key sessionStorage theo tài khoản + tuần

let ptDrag = null;          // lần kéo đang diễn ra; null = không kéo
let ptDrafts = [];          // khung MỚI muốn thêm, ví dụ [{ date: "2026-10-29", start: "08:00" }]
let ptRemovals = [];        // TimeSlotId khung ĐÃ CÔNG BỐ muốn gỡ, ví dụ ["57", "63"]
let ptPublishing = false;   // true trong lúc chờ server trả lời Công bố → khóa mọi thao tác

// ================================================================================================================
// NHÓM 1. KHỞI TẠO — initPtSchedulePreview; hàm phụ restorePtDrafts ngay dưới
// ================================================================================================================

/**
 * HÀM CHÍNH — trình duyệt gọi một lần khi trang tải xong (DOMContentLoaded, cuối file).
 * Gắn sự kiện cho nút và lịch; cấu hình sai hoặc tuần đã qua thì chỉ cho xem.
 */
function initPtSchedulePreview() {
    // B1: ba nút của khung "Chờ công bố" luôn được gắn sự kiện.
    document.getElementById("pt-clear-preview").addEventListener("click", clearPtSlotPreview);
    document.getElementById("pt-publish").addEventListener("click", publishPtDrafts);
    document.getElementById("pt-draft-list").addEventListener("click", removePtDraft);

    // B2: P-06 sai → dừng, form giữ khóa. Tuần đã qua → chỉ đọc lại nháp để xem, không cho thao tác.
    if (!Number.isInteger(ptDuration) || ptDuration <= 0 || ptDuration > ptAxisEnd - ptAxisStart) {
        ptPreviewStatus.textContent = "Chưa có cấu hình giờ tập hợp lệ. Tải lại trang để thử lại.";
        return;
    }
    const date = ptPreviewForm.elements.slotDate;
    if (date.min < ptSchedule.dataset.today) date.min = ptSchedule.dataset.today;
    if (date.min > date.max) {
        ptPreviewStatus.textContent = "Tuần đã qua chỉ xem. Chọn tuần hiện tại hoặc tuần sau để chọn khung.";
        restorePtDrafts();
        return;
    }

    // B3: mở khóa form nhập tay, chọn sẵn ngày hợp lệ và tính giờ kết thúc.
    if (date.value < date.min) date.value = date.min;
    ptPreviewForm.querySelector("fieldset").disabled = false;
    updatePtEndTime();

    // B4: gắn sự kiện kéo thả, form và nút Gỡ khung (một listener click cho cả lịch).
    ptSchedule.addEventListener("pointerdown", startPtSlotDrag);
    ptSchedule.addEventListener("pointermove", movePtSlotDrag);
    ptSchedule.addEventListener("pointerup", finishPtSlotDrag);
    ptSchedule.addEventListener("pointercancel", abortPtSlotDrag);
    ptSchedule.addEventListener("lostpointercapture", abortPtSlotDrag);
    document.addEventListener("keydown", cancelPtDragByKey);
    window.addEventListener("blur", abortPtSlotDrag);
    ptPreviewForm.addEventListener("submit", previewPtSlotFromForm);
    ptPreviewForm.addEventListener("input", updatePtEndTime);
    ptSchedule.addEventListener("click", togglePtRemoval);

    // B5: F5 hoặc đổi Tuần/Danh sách vẫn giữ nháp và khung chờ gỡ.
    restorePtDrafts();
}

/**
 * HÀM PHỤ — gọi ở B2 (tuần đã qua) và B5 của initPtSchedulePreview.
 * Đọc lại ptDrafts và ptRemovals từ sessionStorage; dữ liệu sai dạng hoặc không còn hợp lệ thì bỏ qua.
 */
function restorePtDrafts() {
    try {
        // B1: khung mới phải đúng dạng ngày/giờ và thuộc tuần đang xem.
        const drafts = JSON.parse(sessionStorage.getItem(ptDraftKey) || "[]");
        if (!Array.isArray(drafts)) throw new Error("Nháp không đúng danh sách");
        for (const draft of drafts) {
            if (!draft || !/^\d{4}-\d{2}-\d{2}$/.test(draft.date) || !/^\d{2}:\d{2}$/.test(draft.start)) continue;
            const track = ptSchedule.querySelector('.pt-day-track[data-date="' + draft.date + '"]');
            const start = ptMinutes(draft.start);
            if (track && Number.isFinite(start) && start >= 0 && start + ptDuration < 24 * 60) ptDrafts.push(draft);
        }
        // B2: mã chờ gỡ chỉ giữ khi thẻ đó còn nút Gỡ (khách vừa đặt thì Service đã ẩn nút).
        const removals = JSON.parse(sessionStorage.getItem(ptDraftKey + ".remove") || "[]");
        if (Array.isArray(removals)) {
            for (const slotId of removals) {
                if (/^\d+$/.test(slotId)
                        && ptSchedule.querySelector('.pt-slot-frame[data-slot-id="' + slotId + '"] [data-remove-slot]')) {
                    ptRemovals.push(slotId);
                }
            }
        }
    } catch (error) {
        ptPreviewStatus.textContent = "Không đọc được nháp đã lưu. Bạn có thể chọn lại các khung.";
    }
    // B3: vẽ lên màn hình những gì vừa đọc được.
    renderPtDrafts();
}

// ================================================================================================================
// NHÓM 2. KÉO THẢ — 5 hàm chính theo sự kiện chuột/phím; mỗi hàm phụ nằm ngay dưới hàm chính đầu tiên dùng nó
// ================================================================================================================

/**
 * HÀM CHÍNH — trình duyệt gọi khi nhấn chuột xuống lịch (pointerdown).
 * Chỉ bắt đầu kéo trên vùng trống của ngày còn chọn được; bấm lên thẻ khung/buổi thì bỏ qua.
 */
function startPtSlotDrag(event) {
    // B1: lọc các trường hợp không được kéo.
    const track = event.target.closest(".pt-day-track"); // cột ngày đang bấm
    if (event.button !== 0 || !event.isPrimary || ptDrag || ptPublishing || !track
            || ptSchedule.classList.contains("is-list") || event.target.closest(".pt-slot-frame, .pt-slot-preview")) return;
    if (track.dataset.date < ptSchedule.dataset.today) {
        ptPreviewStatus.textContent = "Ngày đã qua chỉ xem. Chọn ngày hôm nay hoặc ngày sau.";
        return;
    }
    const bounds = ptStartBounds(track);
    if (track.dataset.closed === "true" || bounds.first > bounds.last) {
        ptPreviewStatus.textContent = "Ngày này phòng nghỉ hoặc không đủ giờ cho một khung. Chọn ngày khác.";
        return;
    }
    // B2: tạo ô "bóng" đi theo chuột, đặt ở lớp riêng để không đẩy các thẻ thật.
    event.preventDefault();
    const node = newPtSlotPreview();
    node.classList.add("is-dragging");
    node.setAttribute("aria-hidden", "true");
    track.querySelector(".pt-preview-layer").appendChild(node);
    ptSchedule.classList.add("is-dragging");
    // B3: ghi nhớ lần kéo và vẽ ô bóng ở vị trí chuột hiện tại.
    ptDrag = { track: track, node: node, pointerId: event.pointerId, start: 0 };
    track.setPointerCapture(event.pointerId);
    movePtSlotDrag(event);
    ptPreviewStatus.textContent = "Đang chọn giờ. Thả để thêm nháp, Escape để hủy kéo.";
}

/**
 * HÀM PHỤ — gọi ở B1 của startPtSlotDrag; cũng gọi ở B1 của updatePtEndTime và trong ptMinuteAtPointer.
 * Giờ bắt đầu sớm nhất / muộn nhất của một ngày (đủ chỗ cho cả khung). Ngày nghỉ → first > last.
 */
function ptStartBounds(track) {
    return {
        first: Math.ceil(Number(track.dataset.open) / 15) * 15,
        last: Math.floor((Number(track.dataset.close) - ptDuration) / 15) * 15
    };
}

/**
 * HÀM PHỤ — gọi ở B2 của startPtSlotDrag; cũng gọi ở B1 của renderPtDrafts.
 * Tạo một ô nháp bằng cách sao chép <template id="pt-preview-template"> trong JSP (không dùng innerHTML).
 */
function newPtSlotPreview() {
    return document.getElementById("pt-preview-template").content.firstElementChild.cloneNode(true);
}

/**
 * HÀM CHÍNH — trình duyệt gọi khi di chuột trên lịch (pointermove); startPtSlotDrag B3 cũng gọi một lần.
 * Đổi vị trí chuột thành giờ bắt đầu rồi vẽ lại ô bóng.
 */
function movePtSlotDrag(event) {
    if (!ptDrag || event.pointerId !== ptDrag.pointerId) return;
    ptDrag.start = ptMinuteAtPointer(ptDrag.track, event.clientY);
    paintPtSlotPreview(ptDrag.node, ptDrag.start);
}

/**
 * HÀM PHỤ — gọi trong movePtSlotDrag; cũng gọi ở B2 của finishPtSlotDrag.
 * Vị trí chuột (trục Y) → phút bắt đầu, làm tròn mốc 15 và giữ trong giờ mở cửa của ngày đó.
 */
function ptMinuteAtPointer(track, clientY) {
    const box = track.getBoundingClientRect();
    const minute = ptAxisStart + (clientY - box.top) / box.height * (ptAxisEnd - ptAxisStart);
    const bounds = ptStartBounds(track);
    return Math.max(bounds.first, Math.min(bounds.last, Math.round(minute / 15) * 15));
}

/**
 * HÀM PHỤ — gọi trong movePtSlotDrag; cũng gọi ở B1 của renderPtDrafts.
 * Đặt vị trí + chiều cao ô nháp bằng biến CSS (1 hàng Grid = 1 phút) và ghi giờ lên ô.
 */
function paintPtSlotPreview(node, start) {
    node.style.setProperty("--pt-preview-row", start - ptAxisStart + 1);
    node.style.setProperty("--pt-preview-duration", ptDuration);
    node.querySelector("[data-preview-time]").textContent = ptClock(start) + " – " + ptClock(start + ptDuration);
}

/**
 * HÀM CHÍNH — trình duyệt gọi khi thả chuột (pointerup).
 * Thả trong cột → điền ngày/giờ vào form rồi thêm nháp; thả ngoài cột → hủy lần kéo.
 */
function finishPtSlotDrag(event) {
    if (!ptDrag || event.pointerId !== ptDrag.pointerId) return;
    // B1: thả ra ngoài cột ngày → chỉ hủy lần kéo.
    const box = ptDrag.track.getBoundingClientRect();
    if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) {
        abortPtSlotDrag();
        return;
    }
    // B2: đưa ngày/giờ vào form, để kéo thả và nhập tay đi chung một hàm kiểm tra.
    ptPreviewForm.elements.slotDate.value = ptDrag.track.dataset.date;
    ptPreviewForm.elements.startTime.value = ptClock(ptMinuteAtPointer(ptDrag.track, event.clientY));
    // B3: xóa ô bóng rồi thêm nháp.
    removePtDragGhost();
    previewPtSlotFromForm();
}

/**
 * HÀM CHÍNH — trình duyệt gọi khi mất con trỏ (pointercancel, lostpointercapture) hoặc rời cửa sổ (blur);
 * finishPtSlotDrag B1 và cancelPtDragByKey cũng gọi. Hủy lần kéo, các nháp đã có vẫn giữ.
 */
function abortPtSlotDrag() {
    if (!ptDrag) return;
    removePtDragGhost();
    ptPreviewStatus.textContent = "Đã hủy kéo. Danh sách nháp vẫn được giữ.";
}

/** HÀM CHÍNH — trình duyệt gọi khi nhấn phím (keydown). Phím Escape = hủy lần kéo. */
function cancelPtDragByKey(event) {
    if (event.key === "Escape") abortPtSlotDrag();
}

/**
 * HÀM PHỤ — gọi trong abortPtSlotDrag và ở B3 của finishPtSlotDrag.
 * Xóa ô bóng và trả con trỏ; đặt ptDrag = null trước để abort không chạy hai lần.
 */
function removePtDragGhost() {
    const drag = ptDrag;
    ptDrag = null;
    drag.node.remove();
    ptSchedule.classList.remove("is-dragging");
    if (drag.track.hasPointerCapture(drag.pointerId)) drag.track.releasePointerCapture(drag.pointerId);
}

// ================================================================================================================
// NHÓM 3. THÊM NHÁP TỪ FORM — previewPtSlotFromForm thêm vào ptDrafts; updatePtEndTime giữ form hợp lệ
// ================================================================================================================

/**
 * HÀM CHÍNH — trình duyệt gọi khi bấm "Thêm vào nháp" (submit form); finishPtSlotDrag B3 cũng gọi.
 * Kiểm tra form, chặn trùng với nháp khác, rồi thêm vào ptDrafts. Chưa gọi server.
 */
function previewPtSlotFromForm(event) {
    if (event) event.preventDefault();
    if (ptPublishing) return;
    // B1: tính lại giờ kết thúc và giới hạn giờ; ô nào sai thì trình duyệt tự báo.
    updatePtEndTime();
    if (!ptPreviewForm.reportValidity()) return;
    const date = ptPreviewForm.elements.slotDate.value;
    const start = ptMinutes(ptPreviewForm.elements.startTime.value);
    // B2: cùng ngày, hai khung chỉ được chạm mép (09:30 nối 09:30), không được chồng nhau.
    for (const draft of ptDrafts) {
        const otherStart = ptMinutes(draft.start);
        if (draft.date === date && start < otherStart + ptDuration && otherStart < start + ptDuration) {
            ptPreviewForm.elements.startTime.setCustomValidity("Khung này trùng một khung trong danh sách nháp.");
            ptPreviewForm.reportValidity();
            return;
        }
    }
    // B3: chỉ lưu ngày + giờ bắt đầu; giờ kết thúc do Service tự tính khi Công bố. Lưu rồi vẽ lại.
    ptDrafts.push({ date: date, start: ptClock(start) });
    document.getElementById("pt-publish-error").textContent = "";
    savePtDrafts();
    renderPtDrafts();
    ptPreviewStatus.textContent = "Đã thêm nháp. Kiểm tra danh sách rồi bấm Công bố; chưa lưu vào lịch của khách.";
}

/**
 * HÀM CHÍNH — trình duyệt gọi khi gõ/chọn trong form (input); initPtSchedulePreview B3 và
 * previewPtSlotFromForm B1 cũng gọi. Giới hạn giờ bắt đầu trong giờ mở cửa của ngày đang chọn, tự điền giờ kết thúc.
 */
function updatePtEndTime() {
    const date = ptPreviewForm.elements.slotDate;
    const startInput = ptPreviewForm.elements.startTime;
    const endInput = ptPreviewForm.elements.endTime;
    const track = ptSchedule.querySelector('.pt-day-track[data-date="' + date.value + '"]');
    date.setCustomValidity("");
    startInput.setCustomValidity("");
    endInput.value = "";
    // B1: ngày không thuộc tuần đang xem, đã qua, hoặc phòng nghỉ → báo lỗi ngay trên ô ngày.
    if (!track || date.value < ptSchedule.dataset.today) {
        date.setCustomValidity("Chọn ngày hôm nay hoặc ngày sau trong tuần đang xem.");
        return;
    }
    const bounds = ptStartBounds(track);
    if (track.dataset.closed === "true" || bounds.first > bounds.last) {
        date.setCustomValidity("Ngày này phòng nghỉ hoặc không đủ giờ cho một khung. Chọn ngày khác.");
        return;
    }
    // B2: giới hạn giờ theo đúng ngày đó (kể cả giờ ngoại lệ) và điền giờ kết thúc.
    startInput.min = ptClock(bounds.first);
    startInput.max = ptClock(bounds.last);
    const start = ptMinutes(startInput.value);
    if (Number.isFinite(start) && start >= bounds.first && start <= bounds.last) {
        endInput.value = ptClock(start + ptDuration);
    }
}

// ================================================================================================================
// NHÓM 4. DANH SÁCH CHỜ CÔNG BỐ — 3 hàm chính chỉ sửa mảng; 2 hàm phụ dùng chung ngay dưới
// ================================================================================================================

/**
 * HÀM CHÍNH — trình duyệt gọi khi bấm trong danh sách "Chờ công bố" (click #pt-draft-list).
 * Bấm nút "Bỏ" thì xóa một khung mới khỏi ptDrafts.
 */
function removePtDraft(event) {
    // B1: chỉ xử lý khi bấm đúng nút "Bỏ"; data-remove-draft là vị trí trong ptDrafts.
    const button = event.target.closest("[data-remove-draft]");
    if (!button || ptPublishing) return;
    // B2: xóa khỏi mảng, lưu rồi vẽ lại.
    ptDrafts.splice(Number(button.dataset.removeDraft), 1);
    document.getElementById("pt-publish-error").textContent = "";
    savePtDrafts();
    renderPtDrafts();
}

/**
 * HÀM CHÍNH — trình duyệt gọi khi bấm bất kỳ đâu trên lịch (click #pt-schedule).
 * Bấm nút "Gỡ khung"/"Giữ lại": có trong ptRemovals thì bỏ ra, chưa có thì thêm vào. Chưa ghi DB.
 */
function togglePtRemoval(event) {
    // B1: lịch chỉ có một listener nên phải lọc: chỉ xử lý khi bấm đúng nút Gỡ.
    const button = event.target.closest("[data-remove-slot]");
    if (!button || ptPublishing) return;
    // B2: từ nút đi lên thẻ <article> bọc nó, đọc data-slot-id, ví dụ "57".
    const slotId = button.closest(".pt-slot-frame").dataset.slotId;
    // B3: indexOf trả vị trí trong mảng, không có thì -1.
    const index = ptRemovals.indexOf(slotId);
    if (index >= 0) {
        ptRemovals.splice(index, 1); // đã chờ gỡ → Giữ lại
    } else {
        ptRemovals.push(slotId);     // chưa chờ gỡ → Gỡ
    }
    // B4: lưu vào trình duyệt rồi vẽ lại.
    savePtDrafts();
    renderPtDrafts();
}

/**
 * HÀM CHÍNH — trình duyệt gọi khi bấm "Bỏ tất cả nháp" (click #pt-clear-preview).
 * Xóa cả khung mới lẫn khung chờ gỡ; lịch đã công bố trong DB không đổi.
 */
function clearPtSlotPreview() {
    if (ptPublishing) return;
    // B1: làm rỗng hai mảng.
    ptDrafts = [];
    ptRemovals = [];
    // B2: lưu, vẽ lại và báo cho PT.
    document.getElementById("pt-publish-error").textContent = "";
    savePtDrafts();
    renderPtDrafts();
    ptPreviewStatus.textContent = "Đã bỏ nháp của tuần này. Lịch đã công bố không thay đổi.";
    ptPreviewForm.elements.startTime.focus({ preventScroll: true });
}

/**
 * HÀM PHỤ — gọi ở B2 của removePtDraft, B4 của togglePtRemoval, B2 của clearPtSlotPreview;
 * cũng gọi ở B3 của previewPtSlotFromForm và B3 của publishPtDrafts.
 * Ghi hai mảng vào sessionStorage; key theo tài khoản + tuần nên không lẫn lịch.
 */
function savePtDrafts() {
    try {
        sessionStorage.setItem(ptDraftKey, JSON.stringify(ptDrafts));
        sessionStorage.setItem(ptDraftKey + ".remove", JSON.stringify(ptRemovals));
    } catch (error) {
        ptPreviewStatus.textContent = "Không lưu được nháp trong trình duyệt. Giữ trang này mở tới khi công bố.";
    }
}

/**
 * HÀM PHỤ — gọi ngay sau savePtDrafts ở mọi hàm chính trên; cũng gọi ở B3 của restorePtDrafts
 * và B2, B4 của publishPtDrafts. Vẽ lại mọi thứ từ ptDrafts và ptRemovals:
 * đây là chỗ DUY NHẤT đổi giao diện nháp, các hàm khác chỉ sửa mảng.
 */
function renderPtDrafts() {
    const list = document.getElementById("pt-draft-list");
    list.replaceChildren();
    for (const node of ptSchedule.querySelectorAll(".pt-preview-layer .pt-slot-preview")) node.remove();

    // B1: khung mới → một ô nháp trên lịch + một dòng có nút "Bỏ" trong danh sách.
    for (let i = 0; i < ptDrafts.length; i++) {
        const draft = ptDrafts[i];
        const start = ptMinutes(draft.start);
        const track = ptSchedule.querySelector('.pt-day-track[data-date="' + draft.date + '"]');
        if (track) {
            const node = newPtSlotPreview();
            paintPtSlotPreview(node, start);
            track.querySelector(".pt-preview-layer").appendChild(node);
        }
        const text = document.createElement("span");
        text.textContent = formatDate(draft.date) + " · " + draft.start + "–" + ptClock(start + ptDuration);
        const button = document.createElement("button");
        button.type = "button";
        button.className = "btn btn-ghost btn-sm";
        button.textContent = "Bỏ";
        button.dataset.removeDraft = i; // vị trí trong ptDrafts, removePtDraft B1 đọc lại
        button.disabled = ptPublishing;
        button.setAttribute("aria-label", "Bỏ khung " + text.textContent);
        const row = document.createElement("li");
        row.append(text, button);
        list.appendChild(row);
    }

    // B2: khung đã công bố → thẻ đổi kiểu "chờ gỡ", nút đổi chữ; khung chờ gỡ thêm một dòng "Gỡ · ...".
    for (const button of ptSchedule.querySelectorAll("[data-remove-slot]")) {
        const frame = button.closest(".pt-slot-frame");
        const removing = ptRemovals.includes(frame.dataset.slotId);
        frame.classList.toggle("is-removing", removing);
        button.textContent = removing ? "Giữ lại" : "Gỡ khung";
        button.disabled = ptPublishing;
        if (removing) {
            const row = document.createElement("li");
            row.textContent = "Gỡ · " + formatDate(frame.closest(".pt-day-track").dataset.date)
                    + " · " + frame.querySelector(".pt-slot-time").textContent;
            list.appendChild(row);
        }
    }

    // B3: dòng tóm tắt và hai nút; không có thay đổi nào hoặc đang gửi thì khóa.
    const total = ptDrafts.length + ptRemovals.length;
    document.getElementById("pt-preview-summary").textContent = total
            ? ptDrafts.length + " khung thêm · " + ptRemovals.length + " khung gỡ · khách chưa nhìn thấy."
            : "Chưa có nháp. Kéo trên lịch hoặc mở Nhập ngày và giờ.";
    document.getElementById("pt-clear-preview").disabled = ptPublishing || total === 0;
    document.getElementById("pt-publish").disabled = ptPublishing || total === 0
            || ptPreviewForm.querySelector("fieldset").disabled;
}

// ================================================================================================================
// NHÓM 5. CÔNG BỐ — một POST gửi cả danh sách; chỉ xóa nháp khi server báo thành công
// ================================================================================================================

/**
 * HÀM CHÍNH — trình duyệt gọi khi bấm "Công bố" (click #pt-publish).
 * Gửi ptDrafts và ptRemovals lên /pt/schedule (PtScheduleServlet.doPost); lỗi thì giữ nguyên để PT sửa và gửi lại.
 */
async function publishPtDrafts() {
    if (ptPublishing || ptDrafts.length + ptRemovals.length === 0) return;
    const fields = ptPreviewForm.querySelector("fieldset");
    if (fields.disabled) return;
    const button = document.getElementById("pt-publish");

    // B1: gom dữ liệu gửi đi; khung mới là cặp slotDate + startTime, khung gỡ là removeSlotId.
    const params = new URLSearchParams();
    params.set("csrfToken", ptSchedule.dataset.csrf);
    params.set("duration", ptDuration);
    for (const draft of ptDrafts) {
        params.append("slotDate", draft.date);
        params.append("startTime", draft.start);
    }
    for (const slotId of ptRemovals) {
        params.append("removeSlotId", slotId);
    }
    // B2: khóa mọi nút trong lúc chờ để không bấm lặp hoặc sửa giữa chừng.
    ptPublishing = true;
    fields.disabled = true;
    button.textContent = "Đang công bố…";
    renderPtDrafts();
    try {
        const result = await postData("/pt/schedule", params);
        if (result.success) {
            // B3: thành công → xóa nháp và khung chờ gỡ rồi tải lại trang để đọc lịch mới từ DB.
            ptDrafts = [];
            ptRemovals = [];
            savePtDrafts();
            const url = new URL(location.href);
            url.searchParams.delete("previewDate");
            url.searchParams.delete("previewStart");
            location.assign(url.href);
            return;
        }
        document.getElementById("pt-publish-error").textContent = result.message || "Chưa công bố được. Nháp vẫn được giữ.";
    } catch (error) {
        document.getElementById("pt-publish-error").textContent = "Chưa nhận được kết quả. Nháp vẫn được giữ; kiểm tra lịch trước khi thử lại.";
    } finally {
        // B4: thành công hay lỗi đều mở khóa lại.
        ptPublishing = false;
        fields.disabled = false;
        button.textContent = "Công bố";
        renderPtDrafts();
    }
}

// ================================================================================================================
// NHÓM 6. ĐỔI GIỜ DÙNG CHUNG — hàm phụ được gọi ở nhiều nhóm nên để riêng cuối file
// ================================================================================================================

/**
 * HÀM PHỤ — gọi ở finishPtSlotDrag B2, previewPtSlotFromForm B3, updatePtEndTime B2,
 * paintPtSlotPreview và renderPtDrafts B1. Số phút → "HH:mm", ví dụ 330 → "05:30".
 */
function ptClock(minutes) {
    return String(Math.floor(minutes / 60)).padStart(2, "0") + ":" + String(minutes % 60).padStart(2, "0");
}

/**
 * HÀM PHỤ — gọi ở previewPtSlotFromForm B1–B2, updatePtEndTime B2, restorePtDrafts B1 và renderPtDrafts B1.
 * "HH:mm" → số phút, ví dụ "19:45" → 1185; ô trống → NaN để không hiện giờ giả.
 */
function ptMinutes(value) {
    if (!value) return NaN;
    const parts = value.split(":");
    return Number(parts[0]) * 60 + Number(parts[1]);
}

document.addEventListener("DOMContentLoaded", initPtSchedulePreview);
