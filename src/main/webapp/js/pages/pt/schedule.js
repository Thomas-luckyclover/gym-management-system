"use strict";

/*
 * LỊCH DẠY PT — schedule.js
 *
 * JS CHỈ LÀM 4 VIỆC (những việc CSS không tự làm được):
 *   1. Thêm khung nháp: bấm chỗ trống trên lưới tuần, hoặc dùng form "Thêm khung".
 *   2. Bỏ khung nháp (nút ×) và "Bỏ tất cả".
 *   3. Lưu tạm nháp + lựa chọn gỡ vào trình duyệt, để F5 / đổi kiểu xem không mất.
 *   4. Gửi Công bố (AJAX) rồi tải lại trang.
 *   5. Hộp "Xử lý buổi": mở dạng modal; Esc / Đóng thì bỏ ?handle khỏi URL (không tải lại trang).
 *   6. Thao tác nhanh (26/10): Gỡ hết khung trống · Chép sang tuần sau (thành nháp) · chọn ngày để nhảy tới tuần đó.
 *
 * NHỮNG VIỆC CSS ĐÃ LÀM (không có trong file này):
 *   - Gỡ khung: thẻ khung trống chứa ô checkbox; tích ô thì CSS tự đổi kiểu thẻ "Chờ gỡ".
 *   - Đếm "2 khung mới · 1 khung gỡ" và hiện/ẩn thanh Chờ công bố.
 *   - Xếp thẻ theo giờ trong kiểu danh sách; chuyển sang danh sách trên điện thoại.
 *   - Hộp Xử lý buổi: thẻ buổi là link ?handle=, đổi phương án bằng radio + CSS :has, 3 form POST thường.
 *
 * MỤC LỤC
 *   NHÓM 1. Khởi tạo                    initPtSchedule
 *   NHÓM 2. Bấm lên lưới tuần           movePtGhost, hidePtGhost, handlePtBoardClick
 *   NHÓM 3. Form "Thêm khung"           addPtDraftFromForm, showPtEndTime
 *   NHÓM 4. Khung nháp                  addPtDraft, ptWhyCannotPlace, clearPtChanges
 *   NHÓM 5. Lưu tạm trong trình duyệt   savePtChanges, restorePtChanges
 *   NHÓM 6. Công bố                     publishPtChanges
 *   NHÓM 7. Hàm phụ dùng chung          ptMinuteAtPointer, ptClock, ptMinutes, showPtStatus
 *   NHÓM 8. Hộp Xử lý buổi              initPtHandleDialog, initPtHandleForms, ptHandleFormProblem
 *   NHÓM 9. Thao tác nhanh              initPtTools, removeAllPtFree, copyPtWeekToNext, showPtCopyNotice
 *   (HÀM CHÍNH initPtSchedule ở NHÓM 1; các nhóm sau là hàm phụ nó gắn vào sự kiện, xếp theo thứ tự gắn.)
 *
 * JS TÌM PHẦN TỬ NÀO TRÊN TRANG?  (getElementById("x") = tìm thẻ có id="x"; querySelector(".a") = thẻ có class "a")
 *   Mẫu tìm                        Ở file JSP nào (thư mục WEB-INF/jsp/pt/)     Dùng để
 *   #main-content (ptPlan)         schedule.jsp <main data-duration data-axis-start data-axis-end data-draft-key …>
 *                                                                               đọc P-06, trục giờ, khóa lưu nháp
 *   .pt-plan-board                 schedule.jsp <section> panel lịch             gắn rê chuột / bấm / tích ô gỡ
 *   .pt-day-track[data-date]       _schedule-day.jspf thân 1 cột ngày (data-first / data-last = giờ được thêm)
 *   .pt-slot[data-start]           _schedule-day.jspf 1 thẻ khung đang chiếm giờ  kiểm trùng giờ khi thêm nháp
 *   .pt-remove                     _schedule-day.jspf ô tích "Gỡ khung"           gom removeIds khi Công bố
 *   #pt-add-form                   _schedule-toolbar.jspf form "Thêm khung"       addPtDraftFromForm
 *   [data-plan-action], [data-jump-date]  _schedule-toolbar.jspf nút thao tác nhanh / ô nhảy ngày   initPtTools
 *   #pt-ghost                      schedule.jsp bóng khung đi theo chuột          movePtGhost
 *   #pt-draft-template             _schedule-draft-template.jspf <template>       addPtDraft sao chép
 *   .pt-draft                      thẻ nháp JS vừa tạo (từ template)              Công bố / Bỏ / lưu tạm
 *   #pt-publish-form, #pt-publish, #pt-clear, #pt-publish-error   _schedule-pending.jspf thanh Chờ công bố
 *   #pt-plan-status                schedule.jsp <p role="status"> ẩn              báo cho trình đọc màn hình
 *   #pt-handle                     _schedule-handle.jspf <dialog>                initPtHandleDialog
 *
 * HÀM LẤY TỪ common.js (nạp trước): showToast(loại, chữ) — toast góc màn; postData(url, URLSearchParams) — gửi POST AJAX, trả {success, message} (dùng ở publishPtChanges);
 *   setButtonLoading(nút, true) — khóa nút đang gửi; formatDate("2026-11-12") → "12/11/2026".
 */

// ===== Đọc một lần từ JSP =====
const ptPlan = document.getElementById("main-content");          // <main class="pt-plan"> chứa toàn bộ màn
const ptPublishForm = document.getElementById("pt-publish-form");
const ptGhost = document.getElementById("pt-ghost");             // bóng khung đi theo chuột
const ptDuration = Number(ptPlan.dataset.duration);              // P-06, ví dụ 90 phút
const ptAxisStart = Number(ptPlan.dataset.axisStart);            // phút đầu trục, ví dụ 300 = 05:00
const ptAxisEnd = Number(ptPlan.dataset.axisEnd);                // phút cuối trục, ví dụ 1320 = 22:00
const ptStorageKey = "gms.pt-plan." + ptPlan.dataset.draftKey;   // khóa lưu tạm: theo tài khoản + tuần
const ptCopyNoticeKey = "gms.pt-plan.copied";                    // ghi chú "vừa chép sang" để tuần sau báo kết quả


// ================================================================================================================
// NHÓM 1. KHỞI TẠO
// ================================================================================================================

/** HÀM CHÍNH — trình duyệt gọi khi trang tải xong (cuối file). Gắn sự kiện rồi khôi phục nháp đã lưu. */
function initPtSchedule() {
    const board = ptPlan.querySelector(".pt-plan-board");
    const addForm = document.getElementById("pt-add-form");   // không có khi cả tuần chỉ xem

    // B1: lưới lịch: rê chuột hiện bóng khung, bấm để thêm / bỏ nháp, tích ô gỡ thì lưu lại.
    board.addEventListener("pointermove", movePtGhost);
    board.addEventListener("pointerleave", hidePtGhost);
    board.addEventListener("click", handlePtBoardClick);
    board.addEventListener("change", savePtChanges);

    // B2: form Thêm khung.
    if (addForm) {
        addForm.addEventListener("submit", addPtDraftFromForm);
        addForm.addEventListener("input", showPtEndTime);
    }

    // B3: thanh Chờ công bố.
    ptPublishForm.addEventListener("submit", publishPtChanges);
    document.getElementById("pt-clear").addEventListener("click", clearPtChanges);

    // B4: F5 hoặc đổi kiểu xem: lấy lại nháp + lựa chọn gỡ của đúng tuần này.
    restorePtChanges();

    // B5: URL có ?handle= → JSP đã in sẵn hộp Xử lý buổi; JS chỉ đổi sang dạng modal.
    initPtHandleDialog();

    // B6: thao tác nhanh + báo kết quả nếu vừa chép từ tuần trước sang.
    initPtTools();
    showPtCopyNotice();
}


// ================================================================================================================
// NHÓM 2. BẤM LÊN LƯỚI TUẦN — chỉ có ở kiểu Tuần trên màn rộng (danh sách không có trục giờ)
// ================================================================================================================

/**
 * HÀM CHÍNH — trình duyệt gọi khi rê chuột trên lịch.
 * Đặt bóng khung đúng chỗ chuột; chỗ không thêm được thì bóng đổi đỏ kèm lý do.
 */
function movePtGhost(event) {
    // B1: chỉ hiện trên vùng trống của một cột ngày, ở kiểu lưới tuần, khi dùng chuột.
    const track = event.target.closest(".pt-day-track");
    const onCard = event.target.closest(".pt-slot");
    if (!track || onCard || !isPtWeekLayout() || event.pointerType !== "mouse") {
        hidePtGhost();
        return;
    }

    // B2: vị trí chuột → giờ bắt đầu (mốc 15 phút) → kiểm tra có đặt được không.
    const start = ptMinuteAtPointer(track, event.clientY);
    const reason = ptWhyCannotPlace(track, start);

    // B3: vẽ bóng: CSS dùng --start/--length để đặt vị trí và chiều cao.
    ptGhost.style.setProperty("--start", start - ptAxisStart);
    ptGhost.style.setProperty("--length", ptDuration);
    ptGhost.querySelector(".pt-slot-time").textContent = ptClock(start) + " – " + ptClock(start + ptDuration);
    // Lý do đầy đủ được báo bằng toast khi bấm; ô theo chuột chỉ giữ nhãn ngắn để không tràn chiều cao.
    // Trùng khung thì ghi luôn khung bị trùng ("Trùng 17:00 – 18:30") để PT biết vì sao không thêm được.
    let hint = "Bấm để thêm";
    if (reason.startsWith("Trùng khung ")) {
        hint = "Trùng " + reason.slice("Trùng khung ".length).replace(/\.$/, "");
    } else if (reason) {
        hint = "Không thể thêm";
    }
    ptGhost.querySelector(".pt-ghost-hint").textContent = hint;
    ptGhost.classList.toggle("is-blocked", reason !== "");
    if (ptGhost.parentElement !== track) {
        track.appendChild(ptGhost);
    }
    ptGhost.hidden = false;
}

/** HÀM CHÍNH — trình duyệt gọi khi chuột rời lịch; movePtGhost cũng gọi khi chuột không ở chỗ trống. */
function hidePtGhost() {
    ptGhost.hidden = true;
}

/**
 * HÀM CHÍNH — trình duyệt gọi khi bấm bất kỳ đâu trên lịch.
 *   Bấm nút × của khung nháp → bỏ khung nháp đó.
 *   Bấm chỗ trống trên lưới tuần → thêm khung nháp tại giờ đó.
 * (Bấm thẻ khung trống để gỡ: trình duyệt tự tích checkbox, không cần hàm này.)
 */
function handlePtBoardClick(event) {
    // B1: nút × trên khung nháp.
    const removeButton = event.target.closest(".pt-draft-remove");
    if (removeButton) {
        removeButton.closest(".pt-draft").remove();
        savePtChanges();
        showPtStatus("Đã bỏ một khung nháp.");
        return;
    }

    // B2: chỗ trống trên lưới tuần.
    const track = event.target.closest(".pt-day-track");
    const onCard = event.target.closest(".pt-slot");
    if (!track || onCard || !isPtWeekLayout()) {
        return;
    }

    // B3: thêm nháp tại giờ vừa bấm; không được thì báo lý do.
    const start = ptMinuteAtPointer(track, event.clientY);
    const error = addPtDraft(track, start);
    if (error) {
        showPtStatus(error, true);
        return;
    }
    hidePtGhost();
    showPtStatus("Đã thêm nháp " + track.dataset.label + " · " + ptClock(start) + " – " + ptClock(start + ptDuration) + ".");
}

/** HÀM PHỤ — gọi ở B1 của movePtGhost và B2 của handlePtBoardClick. Đang hiện lưới tuần (không phải danh sách)? */
function isPtWeekLayout() {
    const listChosen = ptPlan.classList.contains("is-list");
    const wideScreen = window.matchMedia("(min-width: 701px)").matches;   // khớp mốc 701px trong CSS
    return !listChosen && wideScreen;
}


// ================================================================================================================
// NHÓM 3. FORM "THÊM KHUNG" — cách thêm trên điện thoại và bằng bàn phím
// ================================================================================================================

/** HÀM CHÍNH — trình duyệt gọi khi bấm "Thêm vào nháp". Lấy ngày + giờ trong form rồi thêm như khi bấm lưới. */
function addPtDraftFromForm(event) {
    event.preventDefault(); // chặn trình duyệt gửi form
    const form = event.currentTarget;
    const startInput = form.elements.start;

    // B1: tìm cột của ngày được chọn; đổi giờ chữ sang phút.
    const track = ptPlan.querySelector('.pt-day-track[data-date="' + form.elements.date.value + '"]');
    const start = ptMinutes(startInput.value);

    // B2: thêm nháp; không thêm được (trùng khung, ngoài giờ, chưa chọn giờ…) → TOAST đỏ + viền đỏ ô giờ.
    //     Không dùng bong bóng mặc định của trình duyệt (reportValidity) — trông như trình duyệt chặn chứ không phải hệ thống.
    const error = addPtDraft(track, start);
    startInput.classList.toggle("invalid", error !== "");
    if (error) {
        showPtStatus(error, true);
        startInput.focus();
        return;
    }

    // B3: xong → xóa ô giờ để nhập khung tiếp theo.
    showPtStatus("Đã thêm nháp " + track.dataset.label + " · " + ptClock(start) + " – " + ptClock(start + ptDuration) + ".");
    startInput.value = "";
    showPtEndTime();
}

/** HÀM CHÍNH — trình duyệt gọi khi gõ/chọn trong form. Hiện giờ kết thúc = giờ bắt đầu + P-06. */
function showPtEndTime() {
    const form = document.getElementById("pt-add-form");
    const start = ptMinutes(form.elements.start.value);
    form.elements.start.classList.remove("invalid");   // sửa giờ thì bỏ viền đỏ cũ
    form.elements.end.value = Number.isFinite(start) ? ptClock(start + ptDuration) : "--:--";
}


// ================================================================================================================
// NHÓM 4. KHUNG NHÁP — mỗi khung nháp là một thẻ HTML có 2 ô ẩn slotDate + startTime
// ================================================================================================================

/**
 * HÀM PHỤ — gọi ở B3 của handlePtBoardClick, B2 của addPtDraftFromForm và B1 của restorePtChanges.
 * Kiểm tra rồi thêm một khung nháp vào cột ngày. Trả "" nếu thêm được, hoặc câu lý do nếu không.
 */

//track: cột ngày cần thêm khung.
//start: giờ bắt đầu, tính bằng phút. Ví dụ 09:30 → 570.
function addPtDraft(track, start) {
    // B1: kiểm tra trước, không được thì trả lý do.
    const reason = ptWhyCannotPlace(track, start);
    if (reason) {
        return reason;
    }

    // B2: sao chép thẻ mẫu trong <template>, điền ngày + giờ.
    const draft = document.getElementById("pt-draft-template").content.firstElementChild.cloneNode(true);
    draft.dataset.start = start;
    draft.dataset.end = start + ptDuration;
    draft.style.setProperty("--start", start - ptAxisStart);
    draft.style.setProperty("--length", ptDuration);
    draft.querySelector('[name="slotDate"]').value = track.dataset.date;
    draft.querySelector('[name="startTime"]').value = ptClock(start);
    draft.querySelector(".pt-slot-time").textContent = ptClock(start) + " – " + ptClock(start + ptDuration);

    // B3: gắn vào cột; CSS tự đặt vị trí, tự đếm, tự hiện thanh Công bố. Rồi lưu tạm.
    track.appendChild(draft); //Nó gắn thẻ khung nháp draft vào cột ngày track, nên khung xuất hiện trên lịch. Chưa lưu vào database.
    savePtChanges();
    return "";
}

/**
 * HÀM PHỤ — gọi ở B1 của addPtDraft và B2 của movePtGhost.
 * Lý do KHÔNG đặt được khung bắt đầu lúc `start` (phút trong ngày); đặt được thì trả "".
 * Server vẫn kiểm tra lại toàn bộ khi Công bố; đây chỉ để báo sớm cho PT.
 */
function ptWhyCannotPlace(track, start) {
    // B1: ngày này có cho thêm không? (Service in data-first/data-last; thiếu = không.)
    if (!track || !track.dataset.first) {
        return "Ngày này đã qua hoặc phòng nghỉ, không thêm được khung.";
    }
    if (!Number.isFinite(start)) {
        return "Chọn giờ bắt đầu.";
    }

    // B2: phải trong khoảng giờ được thêm và đúng mốc 15 phút.
    const first = Number(track.dataset.first);
    const last = Number(track.dataset.last);
    if (start < first || start > last) {
        return "Giờ bắt đầu phải từ " + ptClock(first) + " đến " + ptClock(last) + ".";
    }
    if (start % 15 !== 0) {
        return "Giờ bắt đầu theo mốc 15 phút (:00, :15, :30, :45).";
    }

    // B3: không chồng lên thẻ đang chiếm giờ. Khung đang "Chờ gỡ" thì bỏ qua (Service gỡ trước rồi mới thêm).
    for (const card of track.querySelectorAll(".pt-slot[data-start]")) {
        const removing = card.querySelector(".pt-remove:checked");
        const overlap = start < Number(card.dataset.end) && Number(card.dataset.start) < start + ptDuration;
        if (overlap && !removing) {
            return "Trùng khung " + ptClock(Number(card.dataset.start)) + " – " + ptClock(Number(card.dataset.end)) + ".";
        }
    }
    return "";
}

/** HÀM CHÍNH — trình duyệt gọi khi bấm "Bỏ tất cả". Xóa mọi nháp, bỏ tích mọi ô gỡ. DB không đổi. */
function clearPtChanges() {
    for (const draft of ptPlan.querySelectorAll(".pt-draft")) {
        draft.remove();
    }
    for (const box of ptPlan.querySelectorAll(".pt-remove:checked")) {
        box.checked = false;
    }
    document.getElementById("pt-publish-error").textContent = "";
    savePtChanges();
    showPtStatus("Đã bỏ tất cả thay đổi. Lịch đã công bố giữ nguyên.");
}


// ================================================================================================================
// NHÓM 5. LƯU TẠM TRONG TRÌNH DUYỆT — sessionStorage, chỉ phục vụ giao diện
// ================================================================================================================

/**
 * HÀM PHỤ — gọi mỗi khi có thay đổi (thêm/bỏ nháp, tích/bỏ tích ô gỡ, Bỏ tất cả).
 * Đọc thẳng từ trang: các thẻ .pt-draft và các ô .pt-remove đang tích.
 * Ví dụ lưu: {"drafts":[{"date":"2026-10-27","start":"12:30"}],"removals":["773"]}
 */
function savePtChanges() {
    const drafts = [];
    for (const draft of ptPlan.querySelectorAll(".pt-draft")) {
        drafts.push({
            date: draft.querySelector('[name="slotDate"]').value,
            start: draft.querySelector('[name="startTime"]').value
        });
    }

    const removals = [];
    for (const box of ptPlan.querySelectorAll(".pt-remove:checked")) {
        removals.push(box.value);
    }
    // Có gỡ khung thì lưu thay đổi; chỉ thêm khung mới mới gọi là công bố.
    const button = document.getElementById("pt-publish");
    if (!button.disabled) {
        button.textContent = removals.length ? "Lưu thay đổi" : "Công bố";
    }

    try {
        sessionStorage.setItem(ptStorageKey, JSON.stringify({ drafts: drafts, removals: removals }));
    } catch (error) {
        showPtStatus("Trình duyệt không cho lưu tạm. Giữ trang này mở tới khi công bố.", true);
    }
}

/**
 * HÀM PHỤ — gọi ở B4 của initPtSchedule.
 * Đọc lại dữ liệu đã lưu. Khung nào giờ không còn hợp lệ (ví dụ khách vừa đặt) thì bỏ qua.
 */
function restorePtChanges() {
    let saved;
    try {
        saved = JSON.parse(sessionStorage.getItem(ptStorageKey) || "{}");
    } catch (error) {
        return;   // dữ liệu hỏng: coi như chưa có nháp
    }

    // B1: tích lại các ô gỡ còn trên trang (khung không còn gỡ được thì JSP đã không in ô).
    //     Làm TRƯỚC B2: mỗi lần addPtDraft sẽ lưu lại trạng thái trang, phải có sẵn các ô gỡ đã tích.
    for (const slotId of saved.removals || []) {
        const box = ptPlan.querySelector('.pt-remove[value="' + CSS.escape(String(slotId)) + '"]');
        if (box) {
            box.checked = true;
        }
    }

    // B2: tạo lại khung nháp; addPtDraft tự kiểm tra lại từng khung (khung nào giờ bị trùng thì bỏ).
    for (const draft of saved.drafts || []) {
        const track = ptPlan.querySelector('.pt-day-track[data-date="' + CSS.escape(String(draft.date)) + '"]');
        addPtDraft(track, ptMinutes(String(draft.start)));
    }

    // B3: lưu lại đúng những gì còn hợp lệ.
    savePtChanges();
}


// ================================================================================================================
// NHÓM 6. CÔNG BỐ — gửi một POST chứa mọi thay đổi; chỉ xóa nháp khi server báo thành công
// ================================================================================================================

/**
 * HÀM CHÍNH — trình duyệt gọi khi bấm "Công bố".
 * new FormData(form) tự gom: csrfToken, duration, mọi slotDate/startTime của khung nháp
 * và mọi removeSlotId đang tích (các ô này có form="pt-publish-form" nên thuộc form dù nằm ngoài).
 */
async function publishPtChanges(event) {
    event.preventDefault();
    const button = document.getElementById("pt-publish");
    const errorLine = document.getElementById("pt-publish-error");
    if (button.disabled) {
        return;
    }

    // B1: khóa màn trong lúc chờ để không bấm lặp hay sửa giữa chừng (CSS làm mờ theo aria-busy).
    const params = new URLSearchParams(new FormData(ptPublishForm)); // gom những đứa checkbox đang tick - PtPublishForm
    const hasRemovals = params.has("removeSlotId");
    ptPlan.setAttribute("aria-busy", "true");
    button.disabled = true;
    button.textContent = hasRemovals ? "Đang lưu…" : "Đang công bố…";
    errorLine.textContent = "";

    try {
        // B2: gửi. Servlet trả { success, message }.
        const result = await postData("/pt/schedule", params);

        // B3: thành công → xóa lưu tạm, tải lại trang để đọc lịch mới từ DB (toast hiện sau khi tải).
        if (result.success) {
            sessionStorage.removeItem(ptStorageKey); //remove item trong sessionStorage, xóa bản lưu tạm trong kho
            location.reload(); // tải lại trang
            return;
        }
        errorLine.textContent = result.message || "Chưa lưu được thay đổi. Bạn có thể thử lại.";
    } catch (error) {
        errorLine.textContent = "Chưa nhận được kết quả. Thay đổi vẫn được giữ; kiểm tra lịch trước khi thử lại.";
    } finally {
        // B4: mở khóa lại (lỗi thì PT sửa rồi bấm lại).
        ptPlan.removeAttribute("aria-busy");
        button.disabled = false;
        button.textContent = hasRemovals ? "Lưu thay đổi" : "Công bố";
    }
}


// ================================================================================================================
// NHÓM 7. HÀM PHỤ DÙNG CHUNG
// ================================================================================================================

/**
 * HÀM PHỤ — gọi ở B2 của movePtGhost và B3 của handlePtBoardClick.
 * Vị trí chuột (trục dọc) → giờ bắt đầu, làm tròn mốc 15 phút.
 * Ví dụ cột cao 816px cho trục 05:00–22:00 (1020 phút): chuột cách đỉnh 360px → 05:00 + 450 phút = 12:30.
 */
function ptMinuteAtPointer(track, clientY) {
    const box = track.getBoundingClientRect();
    const minute = ptAxisStart + (clientY - box.top) / box.height * (ptAxisEnd - ptAxisStart);
    return Math.round(minute / 15) * 15;
}

/** HÀM PHỤ — số phút trong ngày → "HH:mm". Ví dụ 750 → "12:30". */
function ptClock(minutes) {
    const hour = String(Math.floor(minutes / 60)).padStart(2, "0");
    const minute = String(minutes % 60).padStart(2, "0");
    return hour + ":" + minute;
}

/** HÀM PHỤ — "HH:mm" → số phút trong ngày. Ví dụ "12:30" → 750. Ô trống → NaN. */
function ptMinutes(value) {
    if (!value) {
        return NaN;
    }
    const parts = value.split(":");
    return Number(parts[0]) * 60 + Number(parts[1]);
}

/**
 * HÀM PHỤ — báo kết quả một thao tác.
 * Luôn ghi vào vùng #pt-plan-status (ẩn, cho trình đọc màn hình).
 * Lỗi (isError = true) thì hiện thêm toast đỏ của common.js; thành công thì không, vì thẻ nháp đã hiện ngay trên lịch.
 */
function showPtStatus(text, isError) {
    document.getElementById("pt-plan-status").textContent = text;
    if (isError === true) {
        showToast("error", text);
    }
}


// ================================================================================================================
// NHÓM 8. HỘP XỬ LÝ BUỔI — dữ liệu và form do server in sẵn (_schedule-handle*.jspf), JS không gửi gì
// ================================================================================================================

/**
 * HÀM CHÍNH — gọi ở B5 của initPtSchedule.
 * B1: JSP in <dialog open> (tắt JS vẫn dùng được) → đóng rồi showModal() để có nền mờ, giữ focus, Esc để đóng.
 * B2: nút Đóng / "Để sau" là link về URL không có ?handle → có JS thì chỉ đóng hộp, khỏi tải lại trang.
 * B3: hộp đóng (kể cả Esc) → thay URL bằng data-close-url để F5 không mở lại hộp.
 */
function initPtHandleDialog() {
    const dialog = document.getElementById("pt-handle");
    if (!dialog) {
        return;
    }

    // B1
    dialog.close();
    dialog.showModal();

    // B2
    dialog.addEventListener("click", function (event) {
        if (event.target.closest("[data-handle-close]")) {
            event.preventDefault();
            dialog.close();
        }
    });

    // B3
    dialog.addEventListener("close", function () {
        history.replaceState(null, "", dialog.dataset.closeUrl);
    });

    // B4: 3 form trong hộp tự kiểm trước khi gửi → báo bằng toast (initPtHandleForms)
    initPtHandleForms(dialog);

    // B5: SERVER vừa từ chối (JSP in .pt-handle-error, VD "khung vừa bị lấy", "trùng khung 17:00–18:30")
    //     → toast đỏ nói rõ là hệ thống chặn; dòng đỏ trong hộp vẫn giữ để đọc lại.
    const serverError = dialog.querySelector(".pt-handle-error");
    if (serverError) {
        showToast("error", "Hệ thống chưa lưu: " + serverError.textContent.trim());
    }
}

/**
 * HÀM PHỤ — gọi ở B4 của initPtHandleDialog.
 * 3 form Dời / Chuyển / Hủy có novalidate → trình duyệt KHÔNG hiện bong bóng "Please check this box…".
 * Thay vào đó: thiếu gì thì TOAST đỏ (showToast của common.js) + viền đỏ đúng chỗ, không gửi form.
 * Server (PtSessionHandleService) vẫn kiểm lại y hệt; nếu lọt (VD khung vừa bị lấy) → câu đỏ đầu hộp sau khi tải lại.
 */
function initPtHandleForms(dialog) {
    for (const form of dialog.querySelectorAll(".pt-handle-panel")) {
        form.addEventListener("submit", function (event) {
            const problem = ptHandleFormProblem(form);
            if (problem) {
                event.preventDefault();
                showToast("error", problem.message);
                if (problem.field) {
                    problem.field.classList.add("is-invalid");
                    problem.field.focus({ preventScroll: false });
                }
                return;
            }
            // Hợp lệ → khóa nút để không bấm 2 lần (setButtonLoading ở common.js)
            const button = form.querySelector('button[type="submit"]');
            if (button) {
                setTimeout(function () { setButtonLoading(button, true); }, 0);
            }
        });
        // Sửa / chọn lại thì bỏ viền đỏ
        form.addEventListener("input", function (event) {
            const box = event.target.closest(".is-invalid") || event.target;
            box.classList.remove("is-invalid");
            event.target.closest("label")?.classList.remove("is-invalid");
        });
    }

    // Tích "Giờ khác" → con trỏ nhảy vào ô Ngày để chọn luôn
    const newToggle = dialog.querySelector('.pt-handle-new-toggle input');
    if (newToggle) {
        newToggle.addEventListener("change", function () {
            dialog.querySelector('[name="newSlotDate"]').focus();
        });
    }
}

/**
 * HÀM PHỤ — gọi trong submit của initPtHandleForms.
 * Trả {message, field} nếu form chưa gửi được, null nếu ổn. field = phần tử tô đỏ + focus.
 *   Dời buổi : chưa chọn khung · chọn "Giờ khác" mà thiếu ngày / giờ / giờ lệch mốc 15 phút / ngày ngoài khoảng
 *   Chuyển PT: chưa chọn PT
 *   Hủy buổi : lý do < 5 ký tự · chưa tích "Khách đã đồng ý nhận lại tiền mặt"
 */
function ptHandleFormProblem(form) {
    const panel = form.dataset.panel;
    const chosen = form.querySelector('input[name="timeSlotId"]:checked');

    if (panel === "reschedule") {
        if (!chosen) {
            return { message: "Chọn 1 khung giờ mới (hoặc Giờ khác) trước khi dời.", field: form.querySelector(".pt-handle-days, .pt-handle-new-toggle") };
        }
        if (chosen.value === "new") {
            const dateInput = form.elements.newSlotDate;
            const timeInput = form.elements.newStartTime;
            if (!dateInput.value) {
                return { message: "Chọn ngày cho khung mới.", field: dateInput };
            }
            if ((dateInput.min && dateInput.value < dateInput.min) || (dateInput.max && dateInput.value > dateInput.max)) {
                return { message: "Ngày mới phải từ " + formatDate(dateInput.min) + " đến " + formatDate(dateInput.max) + ".", field: dateInput };
            }
            const start = ptMinutes(timeInput.value);
            if (!Number.isFinite(start)) {
                return { message: "Chọn giờ bắt đầu cho khung mới.", field: timeInput };
            }
            if (start % 15 !== 0) {
                return { message: "Giờ bắt đầu theo mốc 15 phút (:00, :15, :30, :45).", field: timeInput };
            }
        }
        return null;
    }

    if (panel === "transfer") {
        return chosen ? null : { message: "Chọn PT nhận dạy thay trước khi chuyển.", field: form.querySelector(".pt-handle-pts") };
    }

    if (panel === "cancel") {
        const reason = form.elements.reason;
        if (reason.value.trim().length < 5) {
            return { message: "Nhập lý do hủy ít nhất 5 ký tự (khách sẽ đọc lý do này).", field: reason };
        }
        const agreed = form.elements.refundAgreed;
        if (!agreed.checked) {
            return { message: "Hỏi khách trước: khách đồng ý nhận lại tiền mặt thì tích ô xác nhận rồi mới hủy.", field: agreed.closest("label") };
        }
    }
    return null;
}



// ================================================================================================================
// NHÓM 9. THAO TÁC NHANH — chỉ tạo NHÁP; DB chỉ đổi khi PT bấm Công bố (server kiểm tra lại toàn bộ)
// ================================================================================================================

/** HÀM PHỤ — gọi ở B6 của initPtSchedule. Gắn 2 nút thao tác nhanh + ô chọn ngày. */
function initPtTools() {
    const clearButton = ptPlan.querySelector('[data-plan-action="clear-free"]');
    if (clearButton) {
        clearButton.addEventListener("click", removeAllPtFree);
    }
    const copyButton = ptPlan.querySelector('[data-plan-action="copy-next"]');
    if (copyButton) {
        copyButton.addEventListener("click", copyPtWeekToNext);
    }

    // Chọn ngày → mở tuần chứa ngày đó (form GET ?date=…&view=…). Ngày gõ dở / sai thì chưa gửi.
    const jumpInput = ptPlan.querySelector("[data-jump-date]");
    if (jumpInput) {
        jumpInput.addEventListener("change", function () {
            if (jumpInput.value && jumpInput.checkValidity()) {
                jumpInput.form.requestSubmit();
            }
        });
    }
}

/**
 * HÀM CHÍNH — trình duyệt gọi khi bấm "Gỡ hết khung trống".
 * Tích MỌI ô gỡ của tuần (JSP chỉ in ô gỡ cho khung Còn trống, chưa có khách, không bị đơn giữ, chưa qua)
 * và bỏ mọi khung nháp → thanh Chờ công bố hiện "N khung gỡ". Hoàn tác: "Bỏ tất cả" hoặc bấm từng thẻ.
 */
function removeAllPtFree() {
    let count = 0;
    for (const box of ptPlan.querySelectorAll(".pt-remove:not(:checked)")) {
        box.checked = true;
        count++;
    }
    for (const draft of ptPlan.querySelectorAll(".pt-draft")) {
        draft.remove();
    }
    savePtChanges();
    if (count === 0) {
        showPtStatus("Không còn khung trống nào để gỡ trong tuần này.", true);
        return;
    }
    showPtStatus("Đã đánh dấu " + count + " khung trống chờ gỡ. Bấm Công bố để gỡ thật, hoặc Bỏ tất cả để thôi.");
    showToast("success", "Đã đánh dấu " + count + " khung trống chờ gỡ. Kiểm tra rồi bấm Công bố.");
}

/**
 * HÀM CHÍNH — trình duyệt gọi khi bấm "Chép sang tuần sau".
 * Mọi khung đang chiếm giờ của tuần này (còn trống, có khách, đang giữ, tạm khóa, nháp — trừ khung Chờ gỡ)
 * → nháp CÙNG THỨ, CÙNG GIỜ của tuần sau, ghi vào chỗ lưu tạm của tuần sau rồi mở tuần sau.
 * Tuần sau khôi phục nháp bằng addPtDraft nên khung trùng / ngày không thêm được tự bị bỏ (showPtCopyNotice báo).
 * Ví dụ: Thứ 3 27/10 07:00 có khách → nháp Thứ 3 03/11 07:00.
 */
function copyPtWeekToNext() {
    // B1: gom khung của tuần này (bỏ khung đang Chờ gỡ), dời +7 ngày.
    const copies = [];
    for (const track of ptPlan.querySelectorAll(".pt-day-track")) {
        for (const card of track.querySelectorAll(".pt-slot[data-start]")) {
            if (card.querySelector(".pt-remove:checked")) {
                continue;
            }
            copies.push({ date: ptAddDays(track.dataset.date, 7), start: ptClock(Number(card.dataset.start)) });
        }
    }
    if (copies.length === 0) {
        showPtStatus("Tuần này chưa có khung nào để chép.", true);
        return;
    }

    // B2: gộp vào nháp sẵn có của tuần sau (không trùng ngày + giờ).
    const nextKey = "gms.pt-plan." + ptPlan.dataset.nextKey;
    let saved;
    try {
        saved = JSON.parse(sessionStorage.getItem(nextKey) || "{}");
    } catch (error) {
        saved = {};
    }
    const drafts = saved.drafts || [];
    const before = drafts.length;
    for (const copy of copies) {
        const exists = drafts.some(function (draft) { return draft.date === copy.date && draft.start === copy.start; });
        if (!exists) {
            drafts.push(copy);
        }
    }

    // B3: lưu cho tuần sau + ghi chú để tuần sau báo kết quả, rồi mở tuần sau.
    try {
        sessionStorage.setItem(nextKey, JSON.stringify({ drafts: drafts, removals: saved.removals || [] }));
        sessionStorage.setItem(ptCopyNoticeKey, JSON.stringify({ key: ptPlan.dataset.nextKey, before: before, added: drafts.length - before }));
    } catch (error) {
        showPtStatus("Trình duyệt không cho lưu tạm nên chưa chép được.", true);
        return;
    }
    location.href = ptPlan.dataset.nextUrl;
}

/** HÀM PHỤ — gọi ở B6 của initPtSchedule. Vừa chép từ tuần trước sang → báo chép được bao nhiêu, bỏ qua bao nhiêu. */
function showPtCopyNotice() {
    let notice;
    try {
        notice = JSON.parse(sessionStorage.getItem(ptCopyNoticeKey) || "null");
        sessionStorage.removeItem(ptCopyNoticeKey);
    } catch (error) {
        return;
    }
    if (!notice || notice.key !== ptPlan.dataset.draftKey) {
        return;
    }
    const added = Math.max(0, ptPlan.querySelectorAll(".pt-draft").length - notice.before);
    const skipped = Math.max(0, notice.added - added);
    let text = "Đã chép " + added + " khung từ tuần trước sang tuần này (nháp). Kiểm tra rồi bấm Công bố.";
    if (skipped > 0) {
        text += " " + skipped + " khung bỏ qua vì đã có khung trùng giờ hoặc ngày đó không thêm được.";
    }
    showPtStatus(text);
    showToast(added > 0 ? "success" : "warning", text);
}

/** HÀM PHỤ — "2026-10-27" + n ngày → "2026-11-03" (tính theo UTC để không lệch múi giờ). */
function ptAddDays(isoDate, count) {
    const date = new Date(isoDate + "T00:00:00Z");
    date.setUTCDate(date.getUTCDate() + count);
    return date.toISOString().slice(0, 10);
}

document.addEventListener("DOMContentLoaded", initPtSchedule);
