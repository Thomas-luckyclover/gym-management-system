/*
 * my-pt-sessions.js — JS của màn "Lịch tập của tôi". Chỉ làm 3 việc:
 *   [TAB]       chuyển 3 tab (Sắp tới / Lịch sử / Gói PT) mà KHÔNG tải lại trang
 *   [HUY-BUOI]  mở hộp thoại Hủy buổi
 *   [DOI-BUOI]  mở hộp thoại Đổi buổi + AJAX lấy slot + lọc khung giờ
 *
 * KHÔNG dùng JS cho:
 *   - lọc, phân trang      → form / link GET bình thường
 *   - gửi hủy / đổi        → form POST viết sẵn trong JSP, bấm nút = submit
 *   - hiệu ứng "Đang xử lý…" khi bấm nút → common.js tự làm
 */
document.addEventListener("DOMContentLoaded", function () {
    if (!document.querySelector(".my-pt-sessions")) {
        return;
    }
    setupTabs();
    setupCancelDialog();
    setupSwapDialog();
    setupCloseButtons();
});


// =====================================================================
// [TAB]
// =====================================================================

function setupTabs() {
    const page = document.querySelector(".my-pt-sessions");
    const tabBar = document.querySelector(".my-pt-view-tabs");

    // B1: mở tab ban đầu (JSP tính sẵn ở data-active-view; link phân trang có #... thì ưu tiên)
    let view = page.dataset.activeView;
    if (location.hash === "#history-title") {
        view = "history";
    } else if (location.hash === "#pending-title") {
        view = "upcoming";
    }
    showTab(view);
    tabBar.hidden = false;

    // B2: bấm nút tab → đổi tab + ghi ?view=... lên URL (F5 vẫn mở đúng tab)
    document.querySelectorAll("[data-session-view]").forEach(function (button) {
        button.addEventListener("click", function () {
            const newView = button.dataset.sessionView;
            showTab(newView);

            const url = new URL(location.href);
            url.searchParams.set("view", newView);
            url.hash = "";
            history.replaceState(null, "", url);
        });
    });
}

/** [TAB] Hiện đúng 1 khối có data-session-panel = view, ẩn các khối còn lại. */
function showTab(view) {
    // Nút tab: nút đang chọn có aria-selected="true" (CSS tô đậm)
    document.querySelectorAll("[data-session-view]").forEach(function (button) {
        button.setAttribute("aria-selected", String(button.dataset.sessionView === view));
    });

    // Khối nội dung
    document.querySelectorAll("[data-session-panel]").forEach(function (panel) {
        panel.hidden = panel.dataset.sessionPanel !== view;
    });

    // Tab "Gói PT" không cần bộ lọc
    document.querySelector(".my-pt-filter-panel").hidden = (view === "packages");

    // Bấm Lọc / Xóa lọc xong vẫn quay về đúng tab đang xem
    document.querySelector('#sessionFilterForm input[name="view"]').value = view;
    document.querySelector("[data-clear-session-filters]").href = location.pathname + "?view=" + view;
}


// =====================================================================
// [HUY-BUOI]
// =====================================================================

function setupCancelDialog() {
    const dialog = document.getElementById("cancelDialog");

    document.querySelectorAll("[data-cancel-session-id]").forEach(function (button) {
        button.addEventListener("click", function () {
            // Điền mã buổi + dòng mô tả, xóa lý do cũ, rồi mở hộp thoại
            dialog.querySelector('input[name="sessionId"]').value = button.dataset.cancelSessionId;
            dialog.querySelector("[data-session-text]").textContent = button.dataset.sessionText;
            dialog.querySelector('textarea[name="cancelReason"]').value = "";
            dialog.showModal();
            // "Xác nhận hủy" là nút submit → trình duyệt kiểm tra lý do rồi gửi POST action=cancel
        });
    });
}


// =====================================================================
// [DOI-BUOI]
// =====================================================================

function setupSwapDialog() {
    const dialog = document.getElementById("swapDialog");
    const sessionIdInput = dialog.querySelector('input[name="sessionId"]');
    const originalSlotInput = dialog.querySelector('input[name="originalTimeSlotId"]');
    const bandSelect = dialog.querySelector("[data-swap-band]");
    const bandLabel = dialog.querySelector("[data-swap-band-label]");
    const slotSelect = dialog.querySelector('select[name="targetTimeSlotId"]');
    const slotLabel = dialog.querySelector("[data-swap-slot-label]");
    const status = dialog.querySelector("[data-swap-status]");
    const confirmButton = dialog.querySelector("[data-swap-confirm]");

    let allSlots = [];   // slot server trả về (chưa lọc khung giờ)

    // Bấm "Đổi buổi" ở 1 buổi
    document.querySelectorAll("[data-swap-session-id]").forEach(function (button) {
        button.addEventListener("click", function () {
            openSwapDialog(button.dataset.swapSessionId, button.dataset.sessionText);
        });
    });

    // Đổi khung giờ (Sáng / Chiều / Tối) → vẽ lại danh sách slot
    bandSelect.addEventListener("change", renderSlotOptions);

    // Chọn slot thì mới bật nút "Xác nhận đổi"
    slotSelect.addEventListener("change", function () {
        confirmButton.disabled = (slotSelect.value === "");
    });

    async function openSwapDialog(sessionId, sessionText) {
        // B1: làm sạch hộp thoại rồi mở
        sessionIdInput.value = sessionId;
        originalSlotInput.value = "";
        dialog.querySelector("[data-session-text]").textContent = sessionText;
        allSlots = [];
        bandSelect.value = "";
        bandLabel.hidden = true;
        slotLabel.hidden = true;
        renderSlotOptions();
        status.textContent = "Đang tìm khung giờ phù hợp…";
        dialog.showModal();

        // B2 [DOI-BUOI-GET]: AJAX GET ?action=swapOptions&sessionId=... → Servlet trả JSON
        let result;
        try {
            result = await getJson(location.pathname, {action: "swapOptions", sessionId: sessionId});
        } catch (error) {
            status.textContent = "Không tải được khung giờ. Vui lòng đóng và thử lại.";
            return;
        }

        // Khách đã đóng / mở buổi khác trong lúc chờ → bỏ kết quả cũ
        if (!dialog.open || sessionIdInput.value !== sessionId) {
            return;
        }

        // B3: Server báo lỗi (hết lượt, buổi đã bắt đầu, ...)
        if (!result.success) {
            status.textContent = result.message || "Không tải được khung giờ. Vui lòng thử lại.";
            return;
        }

        // B4: Không có slot nào hợp lệ
        const data = result.data;   // { slots, remainingSwaps, ptName, originalTimeSlotId }
        if (data.slots.length === 0) {
            status.textContent = "Chưa có slot cuối tuần phù hợp. Bạn có thể thử lại khi PT mở thêm lịch hoặc trao đổi trực tiếp với PT.";
            return;
        }

        // B5: Có slot → hiện ô lọc khung giờ + ô chọn slot
        originalSlotInput.value = data.originalTimeSlotId;
        status.textContent = "Còn " + data.remainingSwaps + " lượt đổi trong gói · PT " + data.ptName;
        allSlots = data.slots;
        renderSlotOptions();
        bandLabel.hidden = false;
        slotLabel.hidden = false;
        // "Xác nhận đổi" là nút submit → trình duyệt gửi POST action=swap ([DOI-BUOI-POST])
    }

    /** [DOI-BUOI] Đổ slot vào <select>, chỉ giữ slot đúng khung giờ đang chọn. */
    function renderSlotOptions() {
        const band = bandSelect.value;
        slotSelect.replaceChildren(new Option("Chọn khung giờ", ""));
        confirmButton.disabled = true;

        for (const slot of allSlots) {
            if (band !== "" && timeBandOf(slot.startTime) !== band) {
                continue;
            }
            const date = slot.slotDate.split("-").reverse().join("/");   // 2026-10-18 → 18/10/2026
            const text = date + " · " + slot.startTime + "–" + slot.endTime;
            slotSelect.appendChild(new Option(text, slot.timeSlotId));
        }
    }
}

/** [DOI-BUOI] "09:30" → morning ; "14:00" → afternoon ; "19:00" → evening (giống bộ lọc ở DAO). */
function timeBandOf(startTime) {
    const hour = parseInt(startTime.substring(0, 2), 10);
    if (hour < 12) {
        return "morning";
    }
    if (hour < 18) {
        return "afternoon";
    }
    return "evening";
}


// =====================================================================
// Nút "Giữ buổi" / "Giữ lịch cũ": đóng hộp thoại, không gửi gì
// =====================================================================

function setupCloseButtons() {
    document.querySelectorAll("[data-close-dialog]").forEach(function (button) {
        button.addEventListener("click", function () {
            button.closest("dialog").close();
        });
    });
}
