/* BF-05: bấm hàng để xem chi tiết; Duyệt/Từ chối dùng hộp xác nhận và lý do có sẵn trong common.js. */
document.addEventListener("DOMContentLoaded", function () {
    let decisionTrigger = null;
    const bulkForm = document.getElementById("leave-bulk-form");
    const selectAll = document.getElementById("leave-select-all");
    const selectedButton = document.getElementById("leave-approve-selected");
    const choices = document.querySelectorAll('input[name="leaveRequestIds"]:not([disabled])');
    const reasonTooltip = document.getElementById("leave-reason-tooltip");
    const reasonText = document.getElementById("leave-reason-text");
    let reasonTrigger = null;
    let reasonHideTimer = null;

    // Tooltip nằm ngoài bảng để không bị khung cuộn cắt mất. Nội dung chỉ dùng textContent.
    function hideReason() {
        clearTimeout(reasonHideTimer);
        reasonTooltip.hidden = true;
        if (reasonTrigger) {
            reasonTrigger.removeAttribute("aria-describedby");
            reasonTrigger = null;
        }
    }

    function showReason(trigger) {
        clearTimeout(reasonHideTimer);
        if (reasonTrigger === trigger) {
            return;
        }
        hideReason();
        reasonTrigger = trigger;
        const row = trigger.closest(".leave-request-row");
        let reason = row.dataset.leaveReason.trim();
        if (!reason) {
            reason = "Chưa có lý do.";
        }
        reasonText.textContent = reason;
        reasonTooltip.hidden = false;
        trigger.setAttribute("aria-describedby", reasonTooltip.id);

        // Căn trái ngay dưới tên; giới hạn trong màn hình và tránh cột nút thao tác.
        const anchorBox = trigger.getBoundingClientRect();
        const actionCell = row.querySelector(".leave-actions-cell");
        let rightLimit = window.innerWidth;
        if (actionCell) {
            const actionBox = actionCell.getBoundingClientRect();
            rightLimit = actionBox.left;
        }
        const gap = parseFloat(getComputedStyle(reasonTooltip).getPropertyValue("--space-2"));
        const width = reasonTooltip.offsetWidth;
        const height = reasonTooltip.offsetHeight;
        let left = anchorBox.left;
        if (left + width > rightLimit - gap) {
            left = rightLimit - width - gap;
        }
        if (left + width > window.innerWidth - gap) {
            left = window.innerWidth - width - gap;
        }
        if (left < gap) {
            left = gap;
        }
        let top = anchorBox.bottom + gap;
        reasonTooltip.dataset.placement = "below";
        if (top + height > window.innerHeight - gap) {
            top = anchorBox.top - height - gap;
            reasonTooltip.dataset.placement = "above";
        }
        reasonTooltip.style.left = left + "px";
        reasonTooltip.style.top = Math.max(gap, top) + "px";
    }

    // Chờ ngắn để người dùng có thể di chuột từ tên sang ô lý do mà ô không biến mất.
    function scheduleHideReason() {
        reasonHideTimer = setTimeout(hideReason, 150);
    }

    reasonTooltip.addEventListener("mouseenter", function () {
        clearTimeout(reasonHideTimer);
    });
    reasonTooltip.addEventListener("mouseleave", hideReason);
    document.addEventListener("click", hideReason, true);
    window.addEventListener("scroll", function () {
        // Tab có thể tự cuộn đến tên: đặt lại tooltip sau cuộn để người dùng bàn phím vẫn đọc được.
        let focusedTrigger = null;
        if (reasonTrigger === document.activeElement) {
            focusedTrigger = reasonTrigger;
        }
        hideReason();
        if (focusedTrigger) {
            const box = focusedTrigger.getBoundingClientRect();
            if (box.bottom > 0 && box.top < window.innerHeight) {
                showReason(focusedTrigger);
            }
        }
    }, true);
    window.addEventListener("resize", hideReason);
    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape") {
            hideReason();
        }
    });

    // Chỉ tên có gạch chân mới mở lý do; các phần khác của hàng không bật tooltip.
    for (const trigger of document.querySelectorAll(".leave-reason-trigger")) {
        trigger.addEventListener("mouseenter", function () {
            showReason(trigger);
        });
        trigger.addEventListener("mouseleave", scheduleHideReason);
        trigger.addEventListener("focus", function () {
            showReason(trigger);
        });
        trigger.addEventListener("blur", hideReason);
    }

    // Checkbox chỉ giữ lựa chọn của trang hiện tại; server vẫn kiểm tra lại từng đơn khi duyệt.
    function updateSelection() {
        let count = 0;
        for (const choice of choices) {
            if (choice.checked) {
                count++;
            }
        }
        selectedButton.disabled = count === 0;
        selectAll.disabled = choices.length === 0;
        selectAll.checked = count > 0 && count === choices.length;
        selectAll.indeterminate = count > 0 && count < choices.length;
        document.getElementById("leave-selection-count").textContent = "Đã chọn " + count + " đơn";
        document.getElementById("leave-selection-count").hidden = count === 0;
        selectedButton.dataset.confirmMessage = "Duyệt " + count + " đơn đã chọn? Hệ thống kiểm tra lại từng đơn.";
    }

    if (bulkForm) {
        for (const choice of choices) {
            choice.addEventListener("change", updateSelection);
        }
        selectAll.addEventListener("change", function () {
            for (const choice of choices) {
                choice.checked = selectAll.checked;
            }
            updateSelection();
        });
        updateSelection();
    }

    // Dropdown hai đơn: dữ liệu đã có trong JSP, chỉ đổi hidden và nhãn nút.
    for (const toggle of document.querySelectorAll(".leave-group-toggle")) {
        toggle.addEventListener("click", function () {
            const detailsRow = document.getElementById(toggle.dataset.groupTarget);
            const open = detailsRow.hidden;
            detailsRow.hidden = !open;
            toggle.setAttribute("aria-expanded", String(open));
            toggle.textContent = open ? "Thu gọn ▴" : "Xem 2 đơn ▾";
            toggle.closest(".leave-request-row").classList.toggle("leave-row-selected", open);
        });
    }

    // Ghi nhớ nút mở hộp xác nhận để trả focus khi đóng hộp.
    document.addEventListener("click", function (event) {
        const trigger = event.target.closest("button[data-confirm-message]");
        if (trigger) {
            decisionTrigger = trigger;
        }
    }, true);
    function openRequestRow(row) {
        const groupToggle = row.querySelector(".leave-group-toggle");
        if (groupToggle) {
            groupToggle.click();
        } else {
            window.location.href = row.dataset.reviewUrl;
        }
    }

    const rows = document.querySelectorAll(".leave-request-row");
    for (const row of rows) {
        // B1: bấm phần thông tin của hàng để mở đơn. Bỏ qua form/nút và thao tác bôi đen chữ.
        row.addEventListener("click", function (event) {
            if (event.target.closest("form, button, a, input, select, textarea, details, summary")) {
                return;
            }
            if (window.getSelection().toString()) {
                return;
            }
            openRequestRow(row);
        });

        // B2: Enter trên hàng mở chi tiết; Enter trên nút vẫn là thao tác của nút đó.
        row.addEventListener("keydown", function (event) {
            if (event.key === "Enter" && event.target === row) {
                event.preventDefault();
                openRequestRow(row);
            }
        });
    }

    // B3: hộp chung đóng bằng Hủy/Escape/bấm ngoài thì trả focus về đúng nút của đơn.
    function restoreDecisionFocus() {
        if (decisionTrigger && !document.querySelector(".modal-backdrop")) {
            decisionTrigger.focus();
            decisionTrigger = null;
        }
    }

    window.addEventListener("click", restoreDecisionFocus);
    window.addEventListener("keydown", function (event) {
        if (!decisionTrigger) {
            return;
        }
        const dialog = document.querySelector(".modal-box");
        if (!dialog) {
            restoreDecisionFocus();
            return;
        }

        // Tab chỉ đi giữa ô lý do và hai nút trong hộp, không chạy xuống hàng đơn phía sau.
        if (event.key === "Tab") {
            const controls = dialog.querySelectorAll("textarea, button:not([disabled])");
            const first = controls[0];
            const last = controls[controls.length - 1];
            if (event.shiftKey && document.activeElement === first) {
                event.preventDefault();
                last.focus();
            } else if (!event.shiftKey && document.activeElement === last) {
                event.preventDefault();
                first.focus();
            }
        }
    });
});
