// ===== Khởi tạo trang Admin Dashboard =====

document.addEventListener("DOMContentLoaded", function () {
    initializeAdminSidebar();
    initializeAdminAvatar();
    initializeReportDateValidation();
});

// ===== Điều khiển sidebar Admin =====

/**
 * Gắn sự kiện mở/đóng sidebar Admin.
 * Input: #dock và #dockHandle từ layout-admin.jspf.
 * Output: cập nhật class is-open và thuộc tính aria của nút điều khiển.
 */
function initializeAdminSidebar() {
    const dock = document.getElementById("dock");
    const dockHandle = document.getElementById("dockHandle");

    if (!dock || !dockHandle) {
        return;
    }

    dockHandle.addEventListener("click", function () {
        const isOpen = dock.classList.toggle("is-open");
        dockHandle.setAttribute("aria-expanded", String(isOpen));
        dockHandle.setAttribute(
            "aria-label",
            isOpen ? "Đóng menu điều hướng" : "Mở menu điều hướng"
        );
    });

    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape" || !dock.classList.contains("is-open")) {
            return;
        }

        dock.classList.remove("is-open");
        dockHandle.setAttribute("aria-expanded", "false");
        dockHandle.setAttribute("aria-label", "Mở menu điều hướng");
    });
}

// ===== Avatar trên topbar =====

/**
 * Tạo avatar bằng chữ cái đầu của hai phần cuối trong tên hiển thị.
 */
function initializeAdminAvatar() {
    const avatar = document.querySelector(".tb-user .avatar");
    const displayNameElement = document.querySelector(".tb-user-text b");

    if (!avatar || !displayNameElement) {
        return;
    }

    const displayName = displayNameElement.textContent.trim();
    const nameParts = displayName.split(/\s+/).filter(Boolean);

    if (nameParts.length === 0) {
        return;
    }

    let initials = nameParts[nameParts.length - 1].charAt(0);
    if (nameParts.length >= 2) {
        initials = nameParts[nameParts.length - 2].charAt(0)
            + nameParts[nameParts.length - 1].charAt(0);
    }

    avatar.textContent = initials.toUpperCase();
}

// ===== Validate khoảng ngày ở form =====

/**
 * D7: chặn khoảng ngày ngược ngay trên form; Service vẫn kiểm tra lại ở server.
 */
function initializeReportDateValidation() {
    const form = document.getElementById("reportFilter");
    const fromDateDisplay = document.getElementById("fromDateDisplay");
    const toDateDisplay = document.getElementById("toDateDisplay");
    const fromDate = document.getElementById("fromDate");
    const toDate = document.getElementById("toDate");
    const fromDatePicker = document.getElementById("fromDatePicker");
    const toDatePicker = document.getElementById("toDatePicker");
    const calendarButtons = document.querySelectorAll(".report-calendar-button");

    if (
        !form
        || !fromDateDisplay
        || !toDateDisplay
        || !fromDate
        || !toDate
        || !fromDatePicker
        || !toDatePicker
    ) {
        return;
    }

    function parseVietnameseDate(value) {
        const match = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(value.trim());
        if (!match) {
            return null;
        }

        const day = Number(match[1]);
        const month = Number(match[2]);
        const year = Number(match[3]);
        const date = new Date(Date.UTC(year, month - 1, day));

        if (
            date.getUTCFullYear() !== year
            || date.getUTCMonth() !== month - 1
            || date.getUTCDate() !== day
        ) {
            return null;
        }

        return {
            iso: `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`,
            sortValue: year * 10000 + month * 100 + day
        };
    }
    function formatVietnameseDate(isoDate) {
        if (!isoDate) {
            return "";
        }

        const parts = isoDate.split("-");
        if (parts.length !== 3) {
            return "";
        }

        return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    function syncDateFromPicker(picker, displayInput, hiddenInput) {
        if (!picker.value) {
            return;
        }

        hiddenInput.value = picker.value;
        displayInput.value = formatVietnameseDate(picker.value);
    }
    fromDatePicker.addEventListener("change", function () {
        syncDateFromPicker(fromDatePicker, fromDateDisplay, fromDate);
        validateDateRange();
    });

    toDatePicker.addEventListener("change", function () {
        syncDateFromPicker(toDatePicker, toDateDisplay, toDate);
        validateDateRange();
    });
    calendarButtons.forEach(function (button) {
        button.addEventListener("click", function () {
            const pickerId = button.dataset.pickerTarget;
            const picker = document.getElementById(pickerId);

            if (!picker) {
                return;
            }

            if (typeof picker.showPicker === "function") {
                picker.showPicker();
            } else {
                picker.focus();
                picker.click();
            }
        });
    });
    function validateDateRange() {
        fromDateDisplay.setCustomValidity("");
        toDateDisplay.setCustomValidity("");

        const parsedFrom = parseVietnameseDate(fromDateDisplay.value);
        const parsedTo = parseVietnameseDate(toDateDisplay.value);

        if (!parsedFrom) {
            fromDateDisplay.setCustomValidity("Từ ngày phải đúng định dạng dd/mm/yyyy.");
            return false;
        }

        if (!parsedTo) {
            toDateDisplay.setCustomValidity("Đến ngày phải đúng định dạng dd/mm/yyyy.");
            return false;
        }

        if (parsedFrom.sortValue > parsedTo.sortValue) {
            toDateDisplay.setCustomValidity("Đến ngày phải bằng hoặc sau Từ ngày.");
            return false;
        }

        fromDate.value = parsedFrom.iso;
        toDate.value = parsedTo.iso;
        return true;
    }
    

    form.addEventListener("submit", function (event) {
        if (!validateDateRange()) {
            event.preventDefault();

            if (!fromDateDisplay.checkValidity()) {
                fromDateDisplay.reportValidity();
            } else {
                toDateDisplay.reportValidity();
            }
        }
    });
}
