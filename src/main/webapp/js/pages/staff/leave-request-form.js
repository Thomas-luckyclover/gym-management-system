/**
 * Script xử lý client-side cho màn hình nộp đơn xin nghỉ phép (màn #25, UC-14).
 * Kiểm tra hạn chốt nộp P-12 khi nhân viên thay đổi ngày nghỉ.
 */
document.addEventListener("DOMContentLoaded", function () {
    // B1: lấy các phần tử DOM cần thao tác
    const leaveDateInput = document.getElementById("leaveDate");
    const submitBtn = document.getElementById("leaveSubmit");
    const clientErrDiv = document.getElementById("leaveDateClientErr");
    const infoDateEl = document.getElementById("infoDate");
    const infoDeadlineEl = document.getElementById("infoDeadline");

    if (!leaveDateInput || !submitBtn) {
        return;
    }

    // Helper: thêm số 0 vào đầu số có 1 chữ số
    function padZero(num) {
        return num < 10 ? "0" + num : String(num);
    }

    // Helper: định dạng Date thành chuỗi dd/MM/yyyy
    function formatDateDisplay(d) {
        const day = padZero(d.getDate());
        const month = padZero(d.getMonth() + 1);
        const year = d.getFullYear();
        return day + "/" + month + "/" + year;
    }

    // B1.1: đọc giờ chốt nộp P-12 từ data-cutoff của server (mặc định 15:00 nếu trống)
    const cutoffStr = leaveDateInput.getAttribute("data-cutoff") || "15:00";
    const cutoffParts = cutoffStr.split(":");
    const CUTOFF_HOUR = parseInt(cutoffParts[0], 10) || 15;
    const CUTOFF_MINUTE = parseInt(cutoffParts[1], 10) || 0;
    const cutoffLabel = padZero(CUTOFF_HOUR) + ":" + padZero(CUTOFF_MINUTE);

    // B1.2: đặt thuộc tính min cho ô chọn ngày là ngày mai (GBR-26: chỉ chọn ngày sau hôm nay)
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    leaveDateInput.min = tomorrow.getFullYear() + "-"
            + padZero(tomorrow.getMonth() + 1) + "-"
            + padZero(tomorrow.getDate());

    // B2: hàm cập nhật hiển thị hạn nộp và kiểm tra hợp lệ
    function checkDeadline() {
        const val = leaveDateInput.value;

        // Nếu chưa chọn ngày thì xóa thông tin và trả về
        if (!val) {
            if (infoDateEl) {
                infoDateEl.textContent = "—";
            }
            if (infoDeadlineEl) {
                infoDeadlineEl.textContent = cutoffLabel + " · ngày hôm trước";
            }
            if (clientErrDiv) {
                clientErrDiv.textContent = "";
                clientErrDiv.hidden = true;
            }
            submitBtn.disabled = false;
            return;
        }

        // Tạo mốc ngày nghỉ từ input YYYY-MM-DD
        const parts = val.split("-");
        const leaveYear = parseInt(parts[0], 10);
        const leaveMonth = parseInt(parts[1], 10) - 1;
        const leaveDay = parseInt(parts[2], 10);
        const leaveDate = new Date(leaveYear, leaveMonth, leaveDay);

        // Hiển thị ngày nghỉ lên side card
        if (infoDateEl) {
            infoDateEl.textContent = formatDateDisplay(leaveDate);
        }

        // Tính mốc deadline: ngày hôm trước lúc P-12 (CUTOFF_HOUR:CUTOFF_MINUTE)
        const cutoffDate = new Date(leaveYear, leaveMonth, leaveDay - 1, CUTOFF_HOUR, CUTOFF_MINUTE, 0);

        if (infoDeadlineEl) {
            const cutoffDay = padZero(cutoffDate.getDate());
            const cutoffMonth = padZero(cutoffDate.getMonth() + 1);
            infoDeadlineEl.textContent = cutoffLabel + " · " + cutoffDay + "/" + cutoffMonth;
        }

        // So sánh thời điểm hiện tại với deadline
        const now = new Date();
        const isPastDeadline = now.getTime() > cutoffDate.getTime();

        if (isPastDeadline) {
            if (clientErrDiv) {
                clientErrDiv.textContent = "Đã quá hạn nộp đơn cho ngày này (hạn chốt "
                        + cutoffLabel + " ngày hôm trước). Nghỉ đột xuất vui lòng báo trực tiếp Quản lý.";
                clientErrDiv.hidden = false;
            }
            submitBtn.disabled = true;
        } else {
            if (clientErrDiv) {
                clientErrDiv.textContent = "";
                clientErrDiv.hidden = true;
            }
            submitBtn.disabled = false;
        }
    }

    // B2.1: gọi server lấy dự báo quân số cho ngày + ca đang chọn (SRS 5.2.1)
    const leaveForm = document.getElementById("leaveForm");
    const shiftTypeSelect = document.getElementById("shiftType");
    const pvPt = document.getElementById("pvPt");
    const pvRecep = document.getElementById("pvRecep");
    const pvMinPt = document.getElementById("pvMinPt");
    const pvMinRecep = document.getElementById("pvMinRecep");
    const pvScope = document.getElementById("pvScope");

    function setPreviewText(pt, recep, minPt, minRecep) {
        if (pvPt) {
            pvPt.textContent = pt;
        }
        if (pvRecep) {
            pvRecep.textContent = recep;
        }
        if (pvMinPt) {
            pvMinPt.textContent = minPt;
        }
        if (pvMinRecep) {
            pvMinRecep.textContent = minRecep;
        }
    }

    async function refreshStaffingPreview() {
        const leaveDateValue = leaveDateInput.value;
        const shiftValue = shiftTypeSelect ? shiftTypeSelect.value : "";
        if (!leaveForm || !leaveDateValue || !shiftValue) {
            return;
        }

        const url = leaveForm.dataset.previewUrl
                + "?date=" + encodeURIComponent(leaveDateValue)
                + "&shift=" + encodeURIComponent(shiftValue);

        try {
            const response = await fetch(url, {headers: {"Accept": "application/json"}});
            const body = await response.json();
            if (!body.success) {
                setPreviewText("—", "—", "—", "—");
                return;
            }
            const data = body.data;
            setPreviewText(data.remainingPt, data.remainingReceptionist, data.minPt, data.minReceptionist);
            if (pvScope) {
                pvScope.textContent = "Số liệu cho ngày " + formatDateDisplay(new Date(leaveDateValue + "T00:00:00"))
                        + ", ca đã chọn.";
            }
        } catch (error) {
            setPreviewText("—", "—", "—", "—");
        }
    }

    // B3: lắng nghe sự kiện thay đổi ngày
    leaveDateInput.addEventListener("change", checkDeadline);
    leaveDateInput.addEventListener("input", checkDeadline);
    leaveDateInput.addEventListener("change", refreshStaffingPreview);
    if (shiftTypeSelect) {
        shiftTypeSelect.addEventListener("change", refreshStaffingPreview);
    }

    // B3.1: khóa nút sau lần bấm đầu để không nộp đơn hai lần
    if (leaveForm) {
        leaveForm.addEventListener("submit", function () {
            submitBtn.disabled = true;
            submitBtn.textContent = "Đang gửi...";
        });
    }

    // Kiểm tra ngay khi tải trang (nếu có giá trị sẵn)
    if (leaveDateInput.value) {
        checkDeadline();
        refreshStaffingPreview();
    }
});
