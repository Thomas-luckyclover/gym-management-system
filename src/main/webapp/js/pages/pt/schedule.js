"use strict";

// Nháp chỉ ở sessionStorage theo tài khoản/tuần; Công bố mới gửi một POST ghi DB.
const ptSchedule = document.getElementById("pt-schedule");
const ptPreviewForm = document.getElementById("pt-preview-form");
const ptPreviewStatus = document.getElementById("pt-preview-status");
const ptDuration = Number(ptSchedule.dataset.duration);
const ptAxisStart = Number(ptSchedule.dataset.axisStart);
const ptAxisEnd = Number(ptSchedule.dataset.axisEnd);
let ptDrag = null;
let ptDrafts = [];
let ptPublishing = false;
const ptDraftKey = "gms.pt-slots." + ptSchedule.dataset.draftKey;

// ================================================================================================================
// 1. KHỞI TẠO — gắn sự kiện, bật form khi cấu hình hợp lệ; các hàm kéo thả nằm ngay dưới
// ================================================================================================================

/** Bật xem trước theo thời lượng của tuần và giờ mở cửa từng ngày; không ghi dữ liệu. */
function initPtSchedulePreview() {
    document.getElementById("pt-clear-preview").addEventListener("click", clearPtSlotPreview);
    document.getElementById("pt-publish").addEventListener("click", publishPtDrafts);
    document.getElementById("pt-draft-list").addEventListener("click", removePtDraft);
    // B1: thiếu/sai cấu hình hoặc tuần đã qua thì giữ form khóa, không đoán thời lượng.
    if (!Number.isInteger(ptDuration) || ptDuration <= 0 || ptDuration > ptAxisEnd - ptAxisStart) {
        ptPreviewStatus.textContent = "Chưa có cấu hình giờ tập hợp lệ. Tải lại trang để thử lại.";
        return;
    }
    const date = ptPreviewForm.elements.slotDate;
    date.min = date.min < ptSchedule.dataset.today ? ptSchedule.dataset.today : date.min;
    if (date.min > date.max) {
        ptPreviewStatus.textContent = "Tuần đã qua chỉ xem. Chọn tuần hiện tại hoặc tuần sau để chọn khung.";
        restorePtDrafts();
        return;
    }
    if (date.value < date.min) date.value = date.min;
    ptPreviewForm.querySelector("fieldset").disabled = false;
    updatePtEndTime(); // Ngày khác có thể có giờ ngoại lệ hoặc nghỉ cả ngày.

    // B2: cùng một luồng cho chuột/cảm ứng; cancel/Escape/ra khỏi cửa sổ đều bỏ riêng lần kéo.
    ptSchedule.addEventListener("pointerdown", startPtSlotDrag);
    ptSchedule.addEventListener("pointermove", movePtSlotDrag);
    ptSchedule.addEventListener("pointerup", finishPtSlotDrag);
    ptSchedule.addEventListener("pointercancel", abortPtSlotDrag);
    ptSchedule.addEventListener("lostpointercapture", abortPtSlotDrag);
    document.addEventListener("keydown", cancelPtDragByKey);
    window.addEventListener("blur", abortPtSlotDrag);
    ptPreviewForm.addEventListener("submit", previewPtSlotFromForm);
    ptPreviewForm.addEventListener("input", updatePtEndTime);
    // B3: chuyển view/tuần hoặc F5 vẫn giữ nháp đúng tài khoản và đúng tuần này.
    restorePtDrafts();
}

/** Chỉ kéo vùng trống; bấm trên thẻ buổi/khung thật không khởi tạo lần kéo mới. */
function startPtSlotDrag(event) {
    const track = event.target.closest(".pt-day-track");
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
    // B1: lớp preview riêng không tham gia xếp cột của các thẻ thật.
    event.preventDefault();
    const node = newPtSlotPreview();
    node.classList.add("is-dragging");
    node.setAttribute("aria-hidden", "true");
    track.querySelector(".pt-preview-layer").appendChild(node);
    ptSchedule.classList.add("is-dragging");
    ptDrag = { track: track, node: node, pointerId: event.pointerId, start: 0 };
    track.setPointerCapture(event.pointerId);
    movePtSlotDrag(event);
    ptPreviewStatus.textContent = "Đang chọn giờ. Thả để thêm nháp, Escape để hủy kéo.";
}

/** Di chuyển chỉ đổi phút bắt đầu và chữ trên ô; không tính lại toàn bộ lịch. */
function movePtSlotDrag(event) {
    if (!ptDrag || event.pointerId !== ptDrag.pointerId) return;
    ptDrag.start = ptMinuteAtPointer(ptDrag.track, event.clientY);
    paintPtSlotPreview(ptDrag.node, ptDrag.start);
}

/** Thả trong đúng cột mới thêm nháp; thả ra ngoài chỉ hủy lần kéo này. */
function finishPtSlotDrag(event) {
    if (!ptDrag || event.pointerId !== ptDrag.pointerId) return;
    const box = ptDrag.track.getBoundingClientRect();
    if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) {
        abortPtSlotDrag();
        return;
    }
    // B1: đưa lựa chọn vào form để kéo thả và nhập tay cùng đi qua một hàm xem trước.
    ptPreviewForm.elements.slotDate.value = ptDrag.track.dataset.date;
    ptPreviewForm.elements.startTime.value = ptClock(ptMinuteAtPointer(ptDrag.track, event.clientY));
    removePtDragGhost();
    previewPtSlotFromForm();
}

/** Dọn riêng ô đang kéo; các nháp đã chọn vẫn giữ nguyên. */
function abortPtSlotDrag() {
    if (!ptDrag) return;
    removePtDragGhost();
    ptPreviewStatus.textContent = "Đã hủy kéo. Danh sách nháp vẫn được giữ.";
}

/** Escape chỉ hủy lần kéo, không xóa khung đã chọn trước đó. */
function cancelPtDragByKey(event) {
    if (event.key === "Escape") abortPtSlotDrag();
}

/** Bỏ ghost và quyền giữ con trỏ; đặt ptDrag về null trước khi trình duyệt báo mất capture. */
function removePtDragGhost() {
    const drag = ptDrag;
    ptDrag = null;
    drag.node.remove();
    ptSchedule.classList.remove("is-dragging");
    if (drag.track.hasPointerCapture(drag.pointerId)) drag.track.releasePointerCapture(drag.pointerId);
}

// ================================================================================================================
// 2. THÊM NHÁP TỪ FORM — kéo thả dùng cùng hàm; các nháp chưa xuất hiện cho khách
// ================================================================================================================

/** Kiểm tra form và trùng nháp rồi thêm một khung; không gọi server tại bước này. */
function previewPtSlotFromForm(event) {
    if (event) event.preventDefault();
    if (ptPublishing) return;
    updatePtEndTime();
    if (!ptPreviewForm.reportValidity()) return;
    const date = ptPreviewForm.elements.slotDate.value;
    const start = ptMinutes(ptPreviewForm.elements.startTime.value);
    // B1: cùng ngày chỉ được chạm mép, không lặp/giao nhau với nháp đã chọn.
    for (const draft of ptDrafts) {
        const otherStart = ptMinutes(draft.start);
        if (draft.date === date && start < otherStart + ptDuration && otherStart < start + ptDuration) {
            ptPreviewForm.elements.startTime.setCustomValidity("Khung này trùng một khung trong danh sách nháp.");
            ptPreviewForm.reportValidity();
            return;
        }
    }
    // B2: chỉ giữ ngày/giờ bắt đầu; Service sẽ tự tính lại giờ kết thúc khi Công bố.
    ptDrafts.push({ date: date, start: ptClock(start) });
    document.getElementById("pt-publish-error").textContent = "";
    savePtDrafts();
    renderPtDrafts();
    ptPreviewStatus.textContent = "Đã thêm nháp. Kiểm tra danh sách rồi bấm Công bố; chưa lưu vào lịch của khách.";
}

/** Đổi ngày thì đổi min/max giờ theo ngày đó; nghỉ cả ngày không được xem trước khung. */
function updatePtEndTime() {
    const date = ptPreviewForm.elements.slotDate;
    const track = ptSchedule.querySelector('.pt-day-track[data-date="' + date.value + '"]');
    const startInput = ptPreviewForm.elements.startTime;
    const endInput = ptPreviewForm.elements.endTime;
    date.setCustomValidity("");
    startInput.setCustomValidity("");
    endInput.value = "";
    // B1: không có cột ngày tương ứng hoặc ngày đã qua thì không tạo khung.
    if (!track || date.value < ptSchedule.dataset.today) {
        date.setCustomValidity("Chọn ngày hôm nay hoặc ngày sau trong tuần đang xem.");
        return;
    }
    const bounds = ptStartBounds(track);
    if (track.dataset.closed === "true" || bounds.first > bounds.last) {
        date.setCustomValidity("Ngày này phòng nghỉ hoặc không đủ giờ cho một khung. Chọn ngày khác.");
        return;
    }
    // B2: dùng cùng giới hạn với kéo thả; không dùng giờ mặc định để bỏ qua ngoại lệ.
    startInput.min = ptClock(bounds.first);
    startInput.max = ptClock(bounds.last);
    const start = ptMinutes(startInput.value);
    if (Number.isFinite(start) && start >= bounds.first && start <= bounds.last) {
        endInput.value = ptClock(start + ptDuration);
    }
}

/** Bỏ toàn bộ nháp của tuần này; không gỡ TimeSlot hoặc hủy buổi đã lưu. */
function clearPtSlotPreview() {
    if (ptPublishing) return;
    ptDrafts = [];
    savePtDrafts();
    renderPtDrafts();
    document.getElementById("pt-publish-error").textContent = "";
    ptPreviewStatus.textContent = "Đã bỏ nháp của tuần này. Lịch đã công bố không thay đổi.";
    ptPreviewForm.elements.startTime.focus({ preventScroll: true });
}

/** Sao chép HTML tĩnh từ template; không chèn chuỗi người dùng thành HTML. */
function newPtSlotPreview() {
    return document.getElementById("pt-preview-template").content.firstElementChild.cloneNode(true);
}

/** Một hàng Grid tương ứng một phút, tính từ giờ đầu trục; CSS tự vẽ chiều cao khung. */
function paintPtSlotPreview(node, start) {
    node.style.setProperty("--pt-preview-row", start - ptAxisStart + 1);
    node.style.setProperty("--pt-preview-duration", ptDuration);
    node.querySelector("[data-preview-time]").textContent = ptClock(start) + " – " + ptClock(start + ptDuration);
}

// ================================================================================================================
// 3. DANH SÁCH NHÁP / CÔNG BỐ — lỗi giữ nháp, chỉ xóa sau khi server xác nhận thành công
// ================================================================================================================

/** Mỗi nháp có một ô trên lịch và một dòng có nút Bỏ; không dùng innerHTML. */
function renderPtDrafts() {
    for (const node of ptSchedule.querySelectorAll(".pt-preview-layer .pt-slot-preview")) node.remove();
    const list = document.getElementById("pt-draft-list");
    list.replaceChildren();
    for (let i = 0; i < ptDrafts.length; i++) {
        const draft = ptDrafts[i];
        const track = ptSchedule.querySelector('.pt-day-track[data-date="' + draft.date + '"]');
        const start = ptMinutes(draft.start);
        // B1: DOM của từng view dùng cùng ngày, nên không dựng thêm một lịch thứ hai.
        if (track) {
            const node = newPtSlotPreview();
            paintPtSlotPreview(node, start);
            track.querySelector(".pt-preview-layer").appendChild(node);
        }
        const row = document.createElement("li");
        const text = document.createElement("span");
        text.textContent = formatDate(draft.date) + " · " + draft.start + "–" + ptClock(start + ptDuration);
        const button = document.createElement("button");
        button.type = "button";
        button.className = "btn btn-ghost btn-sm";
        button.textContent = "Bỏ";
        button.dataset.removeDraft = i;
        button.disabled = ptPublishing;
        button.setAttribute("aria-label", "Bỏ khung " + text.textContent);
        row.append(text, button);
        list.appendChild(row);
    }
    // B2: khóa gửi khi chưa có nháp hoặc đang gửi; số đếm luôn từ mảng nháp thực tế.
    document.getElementById("pt-preview-summary").textContent = ptDrafts.length
            ? ptDrafts.length + " khung nháp · khách chưa nhìn thấy."
            : "Chưa có nháp. Kéo trên lịch hoặc mở Nhập ngày và giờ.";
    document.getElementById("pt-clear-preview").disabled = ptPublishing || ptDrafts.length === 0;
    document.getElementById("pt-publish").disabled = ptPublishing || ptDrafts.length === 0
            || ptPreviewForm.querySelector("fieldset").disabled;
}

/** Bỏ riêng một nháp bằng nút có chỉ số trong danh sách, không ghi DB. */
function removePtDraft(event) {
    const button = event.target.closest("[data-remove-draft]");
    if (!button || ptPublishing) return;
    ptDrafts.splice(Number(button.dataset.removeDraft), 1);
    savePtDrafts();
    renderPtDrafts();
    document.getElementById("pt-publish-error").textContent = "";
}

/** Giữ nháp khi chuyển view/F5; key có tài khoản và tuần để không lẫn lịch. */
function savePtDrafts() {
    try {
        sessionStorage.setItem(ptDraftKey, JSON.stringify(ptDrafts));
    } catch (error) {
        ptPreviewStatus.textContent = "Không lưu được nháp trong trình duyệt. Giữ trang này mở tới khi công bố.";
    }
}

/** Storage chỉ phục vụ giao diện; dữ liệu lạ bị bỏ qua, backend vẫn kiểm tra lại khi công bố. */
function restorePtDrafts() {
    try {
        const stored = JSON.parse(sessionStorage.getItem(ptDraftKey) || "[]");
        if (!Array.isArray(stored)) throw new Error("Nháp không đúng danh sách");
        for (const draft of stored) {
            if (!draft || !/^\d{4}-\d{2}-\d{2}$/.test(draft.date) || !/^\d{2}:\d{2}$/.test(draft.start)) continue;
            const track = ptSchedule.querySelector('.pt-day-track[data-date="' + draft.date + '"]');
            const start = ptMinutes(draft.start);
            if (track && Number.isFinite(start) && start >= 0 && start + ptDuration < 24 * 60) ptDrafts.push(draft);
        }
    } catch (error) {
        ptPreviewStatus.textContent = "Không đọc được nháp đã lưu. Bạn có thể chọn lại các khung.";
    }
    renderPtDrafts();
}

/** Một POST công bố cả danh sách; backend kiểm tra lại quyền, thời gian và trùng lịch. */
async function publishPtDrafts() {
    if (ptPublishing || ptDrafts.length === 0) return;
    const fields = ptPreviewForm.querySelector("fieldset");
    if (fields.disabled) return;
    const button = document.getElementById("pt-publish");
    const params = new URLSearchParams();
    params.set("csrfToken", ptSchedule.dataset.csrf);
    params.set("duration", ptDuration);
    for (const draft of ptDrafts) {
        params.append("slotDate", draft.date);
        params.append("startTime", draft.start);
    }
    // B1: chặn bấm lặp và sửa nháp trong khi request đang chạy.
    ptPublishing = true;
    fields.disabled = true;
    button.textContent = "Đang công bố…";
    renderPtDrafts();
    try {
        const result = await postData("/pt/schedule", params);
        if (result.success) {
            // B2: chỉ bỏ nháp khi có xác nhận; GET lại lịch để đọc TimeSlots vừa lưu.
            ptDrafts = [];
            savePtDrafts(); // Lỗi storage đã được xử lý bên trong, không che kết quả công bố thành công.
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
        ptPublishing = false;
        fields.disabled = false;
        button.textContent = "Công bố";
        renderPtDrafts();
    }
}

// ================================================================================================================
// 4. HÀM PHỤ — đổi tọa độ / giờ, CSS Grid tự đặt vị trí; không tính quyền ghi DB tại đây
// ================================================================================================================

/** Tọa độ Y → phút bám mốc 15, giới hạn theo giờ mở cửa của cột ngày đang kéo. */
function ptMinuteAtPointer(track, clientY) {
    const box = track.getBoundingClientRect();
    const minute = ptAxisStart + (clientY - box.top) / box.height * (ptAxisEnd - ptAxisStart);
    const bounds = ptStartBounds(track);
    return Math.max(bounds.first, Math.min(bounds.last, Math.round(minute / 15) * 15));
}

/** Giờ đầu/cuối được chọn, làm tròn mốc 15 phút; ngày nghỉ trả khoảng không hợp lệ. */
function ptStartBounds(track) {
    return {
        first: Math.ceil(Number(track.dataset.open) / 15) * 15,
        last: Math.floor((Number(track.dataset.close) - ptDuration) / 15) * 15
    };
}

/** Số phút từ 00:00 → HH:mm cho ô nhập giờ và nhãn khung. */
function ptClock(minutes) {
    return String(Math.floor(minutes / 60)).padStart(2, "0") + ":" + String(minutes % 60).padStart(2, "0");
}

/** HH:mm từ input time → số phút; ô trống trả NaN để không hiện giờ kết thúc giả. */
function ptMinutes(value) {
    if (!value) return NaN;
    const parts = value.split(":");
    return Number(parts[0]) * 60 + Number(parts[1]);
}

document.addEventListener("DOMContentLoaded", initPtSchedulePreview);
