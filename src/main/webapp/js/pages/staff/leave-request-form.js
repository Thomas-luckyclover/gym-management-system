/** BF-05: xem quỹ/lịch trước khi gửi, xác nhận phần không lương; backend luôn tính lại khi POST. */
document.addEventListener("DOMContentLoaded", function () {
    const form = document.getElementById("leaveForm");
    if (!form) {
        return;
    }
    const fromInput = document.getElementById("leaveDate");
    const toInput = document.getElementById("toDate");
    const typeInput = document.getElementById("leaveType");
    const shiftInput = document.getElementById("shiftType");
    const submitButton = document.getElementById("leaveSubmit");
    const planBox = document.getElementById("leavePlan");
    const planParts = document.getElementById("leavePlanParts");
    const planError = document.getElementById("leavePlanError");
    const consent = document.getElementById("acceptUnpaid");
    const consentRow = document.getElementById("unpaidConsentRow");
    const consentHint = document.getElementById("unpaidConsentHint");
    const scheduleLink = document.getElementById("leaveScheduleLink");
    const dateError = document.getElementById("leaveDateClientErr");
    const planTitle = document.getElementById("leavePlanTitle");
    const balanceText = document.getElementById("leaveBalanceText");
    const expectedAnnualInput = document.getElementById("expectedAnnualDays");
    const expectedUnpaidInput = document.getElementById("expectedUnpaidDays");
    const deadlineText = document.getElementById("infoDeadline");
    const dateText = document.getElementById("infoDate");
    let currentPlan = null;
    // Mỗi lần đổi ngày/ca tăng số này; bỏ phản hồi API cũ nếu người dùng đã đổi lựa chọn.
    let requestVersion = 0;
    let deadlineValid = false;
    let submitting = false;

    // B1: đổi Date thành yyyy-MM-dd. padStart(2, "0") thêm số 0 trước tháng/ngày một chữ số.
    function formatInputDate(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const day = String(date.getDate()).padStart(2, "0");
        return year + "-" + month + "-" + day;
    }

    // Cộng/trừ ngày tại giữa trưa để không lệch ngày khi chuyển đổi múi giờ.
    function addDays(isoDate, days) {
        const date = new Date(isoDate + "T12:00:00");
        date.setDate(date.getDate() + days);
        return formatInputDate(date);
    }

    const todayIso = formatInputDate(new Date());
    fromInput.min = addDays(todayIso, 1);

    // Chỉ mở nút gửi sau khi có dự báo mới nhất và người gửi đã đồng ý nếu cần.
    function updateSubmit() {
        submitButton.disabled = true;
        if (submitting || !deadlineValid || !currentPlan) {
            return;
        }
        if (currentPlan.sessionConflicts > 0) {
            return;
        }
        if (currentPlan.needsUnpaidConsent && !consent.checked) {
            return;
        }
        submitButton.disabled = false;
    }

    function updateDeadline() {
        const cutoff = fromInput.dataset.cutoff;
        deadlineValid = false;
        dateError.hidden = true;
        if (!cutoff) {
            dateError.textContent = "Chưa tải được hạn nộp. Vui lòng tải lại trang.";
            dateError.hidden = false;
            return;
        }
        const cutoffLabel = cutoff.substring(0, 5);
        deadlineText.textContent = cutoffLabel + " · ngày hôm trước";
        dateText.textContent = "—";
        if (fromInput.value) {
            dateText.textContent = formatDate(fromInput.value);
        }
        if (!fromInput.value) {
            return;
        }
        toInput.min = fromInput.value;
        const deadline = new Date(addDays(fromInput.value, -1) + "T" + cutoff);
        deadlineValid = fromInput.value > todayIso && new Date() <= deadline;
        if (!deadlineValid) {
            dateError.textContent = "Đã quá hạn gửi cho ngày này. Nghỉ đột xuất vui lòng báo quản lý.";
            dateError.hidden = false;
        }
    }

    // B2: tạo nội dung bằng textContent; không chèn HTML từ dữ liệu ngày/lý do.
    function addPlanPart(label, days, start, end) {
        if (!days) {
            return;
        }
        const part = document.createElement("div");
        part.className = "leave-plan-part";
        const title = document.createElement("b");
        title.textContent = label + " · " + days + " ngày";
        const period = document.createElement("span");
        period.textContent = formatDate(start) + (start === end ? "" : " → " + formatDate(end));
        part.appendChild(title);
        part.appendChild(period);
        planParts.appendChild(part);
    }

    // B2.1: bỏ dự báo và xác nhận cũ trước khi kiểm tra lựa chọn mới.
    function resetPlan() {
        currentPlan = null;
        consent.checked = false;
        consent.required = false;
        consentRow.hidden = true;
        consentHint.hidden = true;
        scheduleLink.hidden = true;
        planError.hidden = true;
        planParts.textContent = "";
        balanceText.textContent = "";
        expectedAnnualInput.value = "-1";
        expectedUnpaidInput.value = "-1";
    }

    // B2.2: chỉ hiển thị dữ liệu server trả về; hàm này không gọi API hoặc lưu đơn.
    function renderPlan(plan) {
        let partCount = 0;
        if (plan.annualDays > 0) {
            partCount++;
            const lastAnnualDate = addDays(plan.fromDate, plan.annualDays - 1);
            addPlanPart("Phép năm", plan.annualDays, plan.fromDate, lastAnnualDate);
        }
        if (plan.unpaidDays > 0) {
            partCount++;
            const firstUnpaidDate = addDays(plan.fromDate, plan.annualDays);
            addPlanPart("Không lương", plan.unpaidDays, firstUnpaidDate, plan.toDate);
        }
        planTitle.textContent = "Dự kiến gửi " + partCount + " đơn";
        if (partCount === 2) {
            planTitle.textContent += " cùng đợt";
        }
        expectedAnnualInput.value = plan.annualDays;
        expectedUnpaidInput.value = plan.unpaidDays;

        if (typeInput.value === "Annual") {
            balanceText.textContent = "Còn " + plan.remainingAnnualDays + " ngày phép năm (đã tính các đơn đang chờ).";
        } else {
            balanceText.textContent = "Không sử dụng quỹ phép năm.";
        }
        consentRow.hidden = !plan.needsUnpaidConsent;
        consentHint.hidden = !plan.needsUnpaidConsent;
        consent.required = plan.needsUnpaidConsent;
        if (plan.sessionConflicts > 0) {
            planError.textContent = "Trùng " + plan.sessionConflicts + " buổi dạy. Xử lý lịch trước khi gửi đơn.";
            planError.hidden = false;
            scheduleLink.hidden = false;
        }

        // Ô i hiện quân số thấp nhất trong toàn khoảng ngày đang chọn.
        const staffing = plan.staffing;
        document.getElementById("pvPt").textContent = staffing.remainingPt;
        document.getElementById("pvRecep").textContent = staffing.remainingReceptionist;
        document.getElementById("pvMinPt").textContent = staffing.minPt;
        document.getElementById("pvMinRecep").textContent = staffing.minReceptionist;
        document.getElementById("pvScope").textContent = formatDate(plan.fromDate)
                + (plan.fromDate === plan.toDate ? "" : " → " + formatDate(plan.toDate));
    }

    // B2.3: gọi API, bỏ phản hồi cũ rồi chuyển dữ liệu mới cho renderPlan().
    async function refreshPlan() {
        requestVersion++;
        const version = requestVersion;
        resetPlan();
        updateDeadline();
        updateSubmit();
        planBox.hidden = !fromInput.value;
        if (!fromInput.value) {
            return;
        }
        planTitle.textContent = "Đang kiểm tra...";
        let toDate = toInput.value;
        if (!toDate) {
            toDate = fromInput.value;
        }
        const params = {
            date: fromInput.value,
            toDate: toDate,
            shift: shiftInput.value,
            leaveType: typeInput.value
        };
        let result;
        try {
            result = await getJson(form.dataset.previewUrl, params);
        } catch (error) {
            result = {success: false, message: "Không tải được kiểm tra. Vui lòng thử lại."};
        }
        if (version !== requestVersion) {
            return;
        }
        if (!result.success) {
            planTitle.textContent = "Chưa thể gửi";
            planError.textContent = result.message || "Vui lòng kiểm tra ngày và ca nghỉ.";
            planError.hidden = false;
            return;
        }
        currentPlan = result.data;
        renderPlan(currentPlan);
        updateSubmit();
    }

    // B3: chỉ thay đổi nội dung đơn mới xóa xác nhận; gõ lý do không làm mất lựa chọn.
    for (const input of [fromInput, toInput, typeInput, shiftInput]) {
        input.addEventListener("input", function () {
            ++requestVersion;
            currentPlan = null;
            consent.checked = false;
            updateSubmit();
        });
        input.addEventListener("change", refreshPlan);
    }
    consent.addEventListener("change", updateSubmit);
    form.addEventListener("submit", function (event) {
        if (submitButton.disabled) {
            event.preventDefault();
            return;
        }
        submitting = true;
        updateSubmit();
        submitButton.textContent = "Đang gửi...";
    });
    refreshPlan();
});
