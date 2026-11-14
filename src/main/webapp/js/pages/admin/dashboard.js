/*
 * dashboard.js — Xử lý bộ lọc khoảng ngày, làm nổi bật nút chọn nhanh, kiểm tra hợp lệ (MSG09/MSG16)
 * và bảo vệ thao tác xuất CSV (#68 System Operational Report, UC-44).
 */
document.addEventListener("DOMContentLoaded", function () {
    "use strict";

    var form = document.getElementById("reportFilterForm");
    if (!form) {
        return;
    }

    var fromInput = document.getElementById("fromDate");
    var toInput = document.getElementById("toDate");
    var exportLink = document.getElementById("exportCsv");
    var presetButtons = form.querySelectorAll("[data-range-presets] .chip[data-range]");
    var submitButton = form.querySelector("button[type=\"submit\"]");

    var initialFrom = fromInput ? (fromInput.getAttribute("data-initial-value") || "") : "";
    var initialTo = toInput ? (toInput.getAttribute("data-initial-value") || "") : "";

    function getMessage(key, fallback) {
        if (typeof MESSAGES !== "undefined" && MESSAGES[key]) {
            return MESSAGES[key];
        }
        if (window.MESSAGES && window.MESSAGES[key]) {
            return window.MESSAGES[key];
        }
        return fallback;
    }

    function applyFieldError(input, message) {
        if (!input) {
            return;
        }
        if (typeof window.showFieldError === "function") {
            window.showFieldError(input, message);
        }
    }

    function resetFieldError(input) {
        if (!input) {
            return;
        }
        if (typeof window.clearFieldError === "function") {
            window.clearFieldError(input);
        }
    }

    function toIsoDate(date) {
        var year = date.getFullYear();
        var month = String(date.getMonth() + 1).padStart(2, "0");
        var day = String(date.getDate()).padStart(2, "0");
        return year + "-" + month + "-" + day;
    }

    /** Tính khoảng ngày chuẩn khớp với handler [data-range-presets] trong common.js. */
    function resolvePresetStart(rangeKey, today) {
        var start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
        if (rangeKey === "month") {
            start.setDate(1);
            return start;
        }
        var days = Number(rangeKey);
        if (!Number.isFinite(days) || days <= 0) {
            return null;
        }
        start.setDate(start.getDate() - days + 1);
        return start;
    }

    /** Đánh dấu nút chọn nhanh đang khớp với Từ ngày / Đến ngày hiện tại trên form. */
    function syncActivePreset() {
        if (!fromInput || !toInput || presetButtons.length === 0) {
            return;
        }
        var today = new Date();
        var todayIso = toIsoDate(today);
        var fromVal = (fromInput.value || "").trim();
        var toVal = (toInput.value || "").trim();

        presetButtons.forEach(function (button) {
            var rangeKey = button.getAttribute("data-range");
            var expectedStart = resolvePresetStart(rangeKey, today);
            var isMatch = expectedStart !== null
                    && fromVal === toIsoDate(expectedStart)
                    && toVal === todayIso;
            button.classList.toggle("is-active", isMatch);
            button.setAttribute("aria-pressed", isMatch ? "true" : "false");
        });
    }

    function isRangeDirty() {
        if (!fromInput || !toInput) {
            return false;
        }
        return (fromInput.value || "") !== initialFrom || (toInput.value || "") !== initialTo;
    }

    function syncExportState(isValidRange) {
        if (!exportLink) {
            return;
        }
        var blocked = !isValidRange;
        exportLink.classList.toggle("is-disabled", blocked);
        exportLink.setAttribute("aria-disabled", blocked ? "true" : "false");
        if (blocked) {
            exportLink.setAttribute("tabindex", "-1");
        } else {
            exportLink.removeAttribute("tabindex");
        }
    }

    /**
     * Kiểm tra Từ ngày / Đến ngày: bắt buộc (MSG09) và Từ ngày <= Đến ngày (MSG16).
     * Khi showErrors = false chỉ đánh giá trạng thái hợp lệ mà không bật thông báo đỏ sớm.
     */
    function validateRange(showErrors) {
        if (!fromInput || !toInput) {
            return true;
        }
        var fromVal = (fromInput.value || "").trim();
        var toVal = (toInput.value || "").trim();
        var requiredTpl = getMessage("MSG09_REQUIRED", "%s là bắt buộc.");
        var reversedMsg = getMessage("MSG16_DATE_RANGE_REVERSED", "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");

        var valid = true;

        if (!fromVal) {
            valid = false;
            if (showErrors) {
                var fromLabel = fromInput.getAttribute("data-label") || "Từ ngày";
                var msgFrom = typeof window.formatMessage === "function"
                        ? window.formatMessage(requiredTpl, [fromLabel])
                        : fromLabel + " là bắt buộc.";
                applyFieldError(fromInput, msgFrom);
            }
        } else if (showErrors) {
            resetFieldError(fromInput);
        }

        if (!toVal) {
            valid = false;
            if (showErrors) {
                var toLabel = toInput.getAttribute("data-label") || "Đến ngày";
                var msgTo = typeof window.formatMessage === "function"
                        ? window.formatMessage(requiredTpl, [toLabel])
                        : toLabel + " là bắt buộc.";
                applyFieldError(toInput, msgTo);
            }
        } else if (fromVal && fromVal > toVal) {
            valid = false;
            if (showErrors) {
                applyFieldError(toInput, reversedMsg);
            }
        } else if (showErrors) {
            resetFieldError(toInput);
        }

        return valid;
    }

    [fromInput, toInput].forEach(function (input) {
        if (!input) {
            return;
        }
        input.addEventListener("blur", function () {
            var isValid = validateRange(true);
            syncActivePreset();
            syncExportState(isValid);
        });
        input.addEventListener("change", function () {
            var isValid = validateRange(true);
            syncActivePreset();
            syncExportState(isValid);
        });
        input.addEventListener("input", function () {
            var isValid = validateRange(false);
            if (isValid) {
                resetFieldError(fromInput);
                resetFieldError(toInput);
            }
            syncActivePreset();
            syncExportState(isValid);
        });
    });

    form.addEventListener("submit", function (event) {
        if (!validateRange(true)) {
            event.preventDefault();
            syncExportState(false);
            var firstInvalid = form.querySelector(".invalid");
            if (firstInvalid) {
                firstInvalid.focus();
            }
        }
    });

    if (exportLink) {
        exportLink.addEventListener("click", function (event) {
            if (exportLink.classList.contains("is-disabled") || exportLink.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
                validateRange(true);
                return;
            }
            if (!validateRange(true)) {
                event.preventDefault();
                syncExportState(false);
                return;
            }
            if (isRangeDirty()) {
                event.preventDefault();
                if (typeof window.showToast === "function") {
                    window.showToast(
                        getMessage(
                            "MSG366_REPORT_RANGE_CHANGED_BEFORE_EXPORT",
                            "Khoảng ngày đã thay đổi. Vui lòng bấm \"Xem báo cáo\" trước khi xuất CSV."
                        ),
                        "info"
                    );
                }
                if (submitButton) {
                    submitButton.focus();
                }
                return;
            }
            var exportedMessage = exportLink.getAttribute("data-exported-message");
            if (exportedMessage && typeof window.showToast === "function") {
                window.showToast(exportedMessage, "success");
            }
        });
    }

    syncActivePreset();
});
