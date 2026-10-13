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

    // Giờ chốt nộp P-12 mặc định (15:00)
    const CUTOFF_HOUR = 15;
    const CUTOFF_MINUTE = 0;

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

    // B2: hàm cập nhật hiển thị hạn nộp và kiểm tra hợp lệ
    function checkDeadline() {
        const val = leaveDateInput.value;

        // Nếu chưa chọn ngày thì xóa thông tin và trả về
        if (!val) {
            if (infoDateEl) {
                infoDateEl.textContent = "—";
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

        // Tính mốc deadline: ngày hôm trước lúc 15:00
        const cutoffDate = new Date(leaveYear, leaveMonth, leaveDay - 1, CUTOFF_HOUR, CUTOFF_MINUTE, 0);

        if (infoDeadlineEl) {
            const cutoffDay = padZero(cutoffDate.getDate());
            const cutoffMonth = padZero(cutoffDate.getMonth() + 1);
            infoDeadlineEl.textContent = "15:00 · " + cutoffDay + "/" + cutoffMonth;
        }

        // So sánh thời điểm hiện tại với deadline
        const now = new Date();
        const isPastDeadline = now.getTime() > cutoffDate.getTime();

        if (isPastDeadline) {
            if (clientErrDiv) {
                clientErrDiv.textContent = "Đã quá hạn nộp đơn cho ngày này (hạn chốt 15:00 ngày hôm trước). Nghỉ đột xuất vui lòng báo trực tiếp Quản lý.";
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

    // B3: lắng nghe sự kiện thay đổi ngày
    leaveDateInput.addEventListener("change", checkDeadline);
    leaveDateInput.addEventListener("input", checkDeadline);

    // Kiểm tra ngay khi tải trang (nếu có giá trị sẵn)
    if (leaveDateInput.value) {
        checkDeadline();
    }
});
