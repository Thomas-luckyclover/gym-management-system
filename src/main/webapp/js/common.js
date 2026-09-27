// =====================================================================
// common.js — JavaScript DÙNG CHUNG cho mọi trang của GYM MANAGEMENT SYSTEM
//
// Nạp ở mọi trang qua WEB-INF/jsp/common/head.jspf (có defer), TRƯỚC file JS riêng của trang.
// Chỉ Leader sửa file này; cần thêm hàm dùng chung thì báo Leader.
// Trước khi tự viết hàm trong JS riêng của trang, tìm ở đây xem đã có chưa.
//
// Mỗi phần bám 1 luật chung trong SRS (GBR = General Business Rule, DBR = Data Business Rule):
//   1.  Khởi động trang ............ initCommon (tự chạy khi trang tải xong)
//   2.  Toast (GBR-34) .............. showToast, showFlashToastFromPage
//   3.  Hộp thoại (GBR-23, GBR-09) .. openConfirmDialog, closeConfirmDialog, bindConfirmButtons
//   4.  Form (GBR-17, 22, 24) ....... showFieldError, clearFieldError, clearFormErrors, showServerFieldErrors,
//                                     validateField, validateForm, bindFieldValidation, bindFormValidation,
//                                     setButtonLoading, bindUnsavedChangesWarning, markFormSaved
//   5.  Hiện/ẩn mật khẩu (GBR-30) ... bindPasswordToggles
//   6.  Đếm ngược (GBR-27) .......... startCountdown, stopCountdown, bindCountdowns
//   7.  Danh sách (GBR-18, 20, 21) .. changeUrlParams, bindPageSizeSelects, bindClearFiltersButtons,
//                                     bindAutoSubmitSelects, renderEmptyState
//   8.  Tab & vùng bật/tắt .......... bindTabs, bindToggleButtons
//   9.  AJAX ........................ sendAjaxRequest, getJson, postData, submitFormAjax
//   10. Định dạng (GBR-01, 29) ...... formatMoney, formatDate, formatTime, formatDateTime, formatCountdown,
//                                     maskEmail, formatPhoneDisplay
//   11. Kiểm tra dữ liệu (DBR) ...... isBlank, isValidPhone, isValidEmail, isValidPassword, isValidFullName,
//                                     isValidVerificationCode, isLengthInRange, isValidReason, isPositiveInteger,
//                                     isValidDateOfBirth, isValidDateRange, isValidImageFile
//   12. Tiện ích nền ................ getContextPath, buildUrl, escapeHtml, formatMessage, normalizeSearchText
//
// HỢP ĐỒNG VỚI HTML/JSP (đổi tên ở HTML/CSS thì phải sửa file này, và ngược lại):
//   <meta name="context-path">            đọc bởi getContextPath()
//   #flashToast[data-type][data-message]   đọc bởi showFlashToastFromPage() (in ra từ common/flash-toast.jspf)
//   #toastRoot                             tạo bởi getToastContainer() nếu trang chưa có
//   .field-error#<idÔNhập>Error            ô báo lỗi dưới input — showFieldError()/clearFieldError()
//   input.invalid                          viền đỏ ô nhập sai — showFieldError()/clearFieldError()
//   form[data-validate]                    tự kiểm tra khi rời ô + khi submit — bindFormValidation()
//     input[required][data-label]          tên ô dùng trong câu "… là bắt buộc." (MSG09)
//     input[data-rule]                     phone | email | password | fullName | verificationCode | reason |
//                                          positiveInteger | dateOfBirth
//     input[data-match="idÔKhác"]          ô xác nhận mật khẩu (MSG47)
//   form[data-unsaved-warning]             cảnh báo rời trang khi chưa lưu — bindUnsavedChangesWarning()
//   [data-confirm-message]                 nút cần xác nhận trước khi gửi — bindConfirmButtons()
//   [data-password-toggle="idÔMậtKhẩu"]    nút con mắt — bindPasswordToggles()
//   [data-countdown-seconds]               ô đếm ngược mm:ss — bindCountdowns()
//   select[data-page-size-select]          chọn 10/20/50 dòng — bindPageSizeSelects()
//   [data-clear-filters]                   nút "Xóa bộ lọc" — bindClearFiltersButtons()
//   select[data-auto-submit]               đổi lựa chọn là gửi form lọc — bindAutoSubmitSelects()
//   [role="tablist"] [data-tab] + [data-tab-panel]   tab — bindTabs()
//   [data-toggle-target="idVùng"]          nút mở/đóng 1 vùng (menu mobile, sidebar) — bindToggleButtons()
// =====================================================================

// ===== Hằng số =====

// Toast tự ẩn sau 4 giây (GBR-34: 3–5 giây); toast lỗi KHÔNG tự ẩn, người dùng phải bấm đóng
const TOAST_AUTO_HIDE_MS = 4000;

// Thời gian chạy hiệu ứng ẩn toast, khớp token --duration-slow trong tokens.css
const TOAST_HIDE_ANIMATION_MS = 250;

// Icon Font Awesome cho từng loại toast
const TOAST_ICONS = {
    success: "fa-circle-check",
    info: "fa-circle-info",
    warning: "fa-triangle-exclamation",
    error: "fa-circle-xmark"
};

// Luật dữ liệu — khớp constant/AppConstants.java và util/ValidationUtil.java phía server
const PHONE_PATTERN = /^0\d{9}$/;
const EMAIL_PATTERN = /^[\w.+-]+@[\w-]+(\.[\w-]+)*\.[a-zA-Z]{2,}$/;
const VERIFICATION_CODE_PATTERN = /^\d{6}$/;
// Chữ cái mọi ngôn ngữ (kể cả tiếng Việt có dấu), khoảng trắng, nháy đơn, gạch nối
const FULL_NAME_PATTERN = /^[\p{L} '\-]+$/u;
const HAS_LETTER_PATTERN = /\p{L}/u;
const HAS_DIGIT_PATTERN = /\d/;

const FULL_NAME_MIN_LENGTH = 2;
const FULL_NAME_MAX_LENGTH = 50;
const PASSWORD_MIN_LENGTH = 8;
const PASSWORD_MAX_LENGTH = 64;
const EMAIL_MAX_LENGTH = 100;
const REASON_MIN_LENGTH = 5;
const REASON_MAX_LENGTH = 500;
const MAX_AGE_YEARS = 100;
const IMAGE_MAX_BYTES = 5 * 1024 * 1024;
const IMAGE_MAX_SIZE_LABEL = "5 MB";

// Header đánh dấu request AJAX — Filter phía server dựa vào đây để trả JSON 401/403 thay vì chuyển trang
const AJAX_HEADER_NAME = "X-Requested-With";
const AJAX_HEADER_VALUE = "XMLHttpRequest";

// Câu thông báo — bản sao của constant/Messages.java (SRS III.5). Sửa câu chữ thì sửa cả 2 nơi.
// %s / %d được thay bằng formatMessage(mẫu, [giá trị...])
const MESSAGES = {
    MSG01_NO_RESULT: "Không tìm thấy kết quả. Hãy thử từ khóa khác hoặc bỏ bớt bộ lọc.",
    MSG02_EMPTY_LIST: "Chưa có %s nào.",
    MSG03_NO_DATA_IN_RANGE: "Không có dữ liệu trong khoảng thời gian đã chọn.",
    MSG04_SYSTEM_BUSY: "Hệ thống đang bận. Vui lòng thử lại sau ít phút.",
    MSG05_CONNECTION_LOST: "Mất kết nối. Hãy kiểm tra mạng và thử lại.",
    MSG06_SESSION_EXPIRED: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
    MSG07_NO_PERMISSION: "Bạn không có quyền truy cập chức năng này.",
    MSG09_REQUIRED: "%s là bắt buộc.",
    MSG10_MAX_LENGTH: "Vượt quá độ dài tối đa %d ký tự.",
    MSG11_INVALID_EMAIL: "Email không đúng định dạng.",
    MSG13_INVALID_PHONE: "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.",
    MSG15_DOB_IN_FUTURE: "Ngày sinh không được ở tương lai.",
    MSG16_DATE_RANGE_REVERSED: "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.",
    MSG17_POSITIVE_NUMBER: "Nhập một số lớn hơn 0.",
    MSG18_DATE_TOO_EARLY: "Chỉ được chọn ngày từ %s trở đi.",
    MSG19_TOO_SHORT: "Vui lòng ghi cụ thể hơn, tối thiểu %d ký tự.",
    MSG20_INVALID_IMAGE: "Chỉ chấp nhận ảnh JPG hoặc PNG, tối đa %s.",
    MSG21_SELECT_REQUIRED: "Vui lòng chọn %s.",
    MSG23_SAVED: "Đã lưu thay đổi.",
    MSG24_NOTHING_TO_SAVE: "Không có thay đổi nào để lưu.",
    MSG27_CONFIRM_DELETE: "Xóa %s? Thao tác này không thể hoàn tác.",
    MSG28_UNSAVED_CHANGES: "Thay đổi của bạn chưa được lưu. Rời trang bây giờ sẽ mất các thay đổi này.",
    MSG39_IDENTIFIER_REQUIRED: "Nhập email hoặc số điện thoại.",
    MSG44_CODE_EXPIRED: "Mã đã hết hạn. Bấm Gửi lại mã để nhận mã mới.",
    MSG46_PASSWORD_TOO_SHORT: "Mật khẩu phải có ít nhất %d ký tự.",
    MSG47_PASSWORD_MISMATCH: "Mật khẩu xác nhận không khớp.",
    EXTRA_PASSWORD_RULE: "Mật khẩu phải dài %d–%d ký tự, gồm cả chữ và số.",
    EXTRA_FULL_NAME_RULE: "Họ tên chỉ gồm chữ cái, khoảng trắng, dấu nháy đơn hoặc gạch nối.",
    EXTRA_CODE_FORMAT: "Mã xác thực gồm đúng 6 chữ số.",
    EXTRA_DOB_TOO_OLD: "Ngày sinh không hợp lệ (tuổi không quá %d).",
    EXTRA_CODE_RESEND_TOO_SOON: "Vui lòng chờ %d giây nữa rồi bấm Gửi lại mã.",
    EXTRA_SETUP_LINK_INVALID: "Link đặt mật khẩu không còn hiệu lực. Vui lòng dùng Quên mật khẩu để đặt lại.",
    EXTRA_SETUP_LINK_EXPIRED: "Link đặt mật khẩu đã hết hạn. Hãy bấm Gửi mã để nhận mã xác thực qua email và đặt mật khẩu.",
    EXTRA_SETUP_LINK_USED: "Bạn đã đặt mật khẩu cho tài khoản này. Vui lòng đăng nhập."
};

// Hộp thoại xác nhận đang mở (chỉ mở 1 cái tại 1 thời điểm)
let activeDialog = null;

// Đã gắn sự kiện cảnh báo rời trang cho window/document chưa (chỉ gắn 1 lần dù trang có nhiều form)
let isLeavePageWarningBound = false;

// ===== 1. KHỞI ĐỘNG TRANG =====

// Gắn toàn bộ hành vi dùng chung vào trang, dựa trên các thuộc tính data-* có trong HTML
// Luồng xử lý:
// B1: hiện toast được Servlet gửi kèm qua redirect (nếu có)
// B2: gắn hành vi cho các thành phần tĩnh: nút mật khẩu, tab, nút bật/tắt, nút xác nhận, đếm ngược
// B3: gắn hành vi cho danh sách: chọn số dòng, xóa bộ lọc, select tự gửi form
// B4: gắn kiểm tra dữ liệu và cảnh báo chưa lưu cho các form có đánh dấu
function initCommon() {
    // B1
    showFlashToastFromPage();

    // B2
    bindPasswordToggles();
    bindTabs();
    bindToggleButtons();
    bindConfirmButtons();
    bindCountdowns();

    // B3
    bindPageSizeSelects();
    bindClearFiltersButtons();
    bindAutoSubmitSelects();

    // B4
    const validateForms = document.querySelectorAll("form[data-validate]");
    for (const form of validateForms) {
        bindFormValidation(form);
    }
    const unsavedForms = document.querySelectorAll("form[data-unsaved-warning]");
    for (const form of unsavedForms) {
        bindUnsavedChangesWarning(form);
    }
}

// Chạy initCommon khi HTML đã dựng xong (file nạp bằng defer nên thường vẫn ở trạng thái "loading")
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initCommon);
} else {
    initCommon();
}

// ===== 2. TOAST (GBR-34) =====

// Hiện toast mà Servlet đã gắn trước khi redirect (RequestUtil.setFlashToast phía Java)
// Luồng xử lý:
// B1: tìm thẻ ẩn #flashToast do common/flash-toast.jspf in ra (chỉ có khi session có flashToast)
// B2: đọc data-type, data-message (JSP đã escape bằng c:out) rồi hiện toast
function showFlashToastFromPage() {
    // B1
    const flashElement = document.getElementById("flashToast");
    if (flashElement === null) {
        return;
    }

    // B2
    const toastType = flashElement.dataset.type;
    const toastMessage = flashElement.dataset.message;
    showToast(toastType, toastMessage);
    flashElement.remove();
}

// Hiện 1 thông báo nổi ở góc trên bên phải màn hình
// Input: type = "success" | "info" | "warning" | "error"; message = nội dung (chữ thuần, tự chống XSS)
// Luồng xử lý:
// B1: lấy khung chứa #toastRoot (tạo nếu trang chưa có)
// B2: dựng toast gồm icon + nội dung + nút đóng (nội dung gán bằng textContent nên an toàn)
// B3: thêm vào khung, bật class "show" ở khung hình kế tiếp để chạy hiệu ứng trượt vào
// B4: toast thường tự ẩn sau TOAST_AUTO_HIDE_MS; toast lỗi ở lại tới khi bấm đóng (GBR-34)
function showToast(type, message) {
    // B1
    const container = getToastContainer();

    // B2
    let iconClass = TOAST_ICONS[type];
    if (iconClass === undefined) {
        iconClass = TOAST_ICONS.info;
    }

    const toast = document.createElement("div");
    toast.className = "toast toast-" + type;
    if (type === "error" || type === "warning") {
        toast.setAttribute("role", "alert");
    } else {
        toast.setAttribute("role", "status");
    }

    const icon = document.createElement("i");
    icon.className = "fa-solid " + iconClass + " toast-icon";
    icon.setAttribute("aria-hidden", "true");

    const text = document.createElement("span");
    text.className = "toast-message";
    text.textContent = message;

    const closeButton = document.createElement("button");
    closeButton.type = "button";
    closeButton.className = "toast-close";
    closeButton.setAttribute("aria-label", "Đóng thông báo");
    closeButton.innerHTML = '<i class="fa-solid fa-xmark" aria-hidden="true"></i>';
    closeButton.addEventListener("click", function () {
        removeToast(toast);
    });

    toast.appendChild(icon);
    toast.appendChild(text);
    toast.appendChild(closeButton);

    // B3
    container.appendChild(toast);
    requestAnimationFrame(function () {
        toast.classList.add("show");
    });

    // B4
    if (type !== "error") {
        setTimeout(function () {
            removeToast(toast);
        }, TOAST_AUTO_HIDE_MS);
    }
}

// Lấy khung #toastRoot chứa các toast; trang chưa có thì tạo mới gắn vào cuối <body>
function getToastContainer() {
    let container = document.getElementById("toastRoot");
    if (container !== null) {
        return container;
    }
    container = document.createElement("div");
    container.id = "toastRoot";
    container.setAttribute("aria-live", "polite");
    document.body.appendChild(container);
    return container;
}

// Ẩn toast có hiệu ứng rồi xóa khỏi trang
function removeToast(toast) {
    toast.classList.remove("show");
    setTimeout(function () {
        toast.remove();
    }, TOAST_HIDE_ANIMATION_MS);
}

// ===== 3. HỘP THOẠI XÁC NHẬN (GBR-23, GBR-09, MSG27) =====

// Mở hộp thoại hỏi xác nhận trước thao tác không hoàn tác được (xóa, hủy, từ chối, ngừng hoạt động, xuất bản)
// Input: options gồm
//   title          tiêu đề, VD "Xóa voucher?"
//   message        nội dung giải thích (chữ thuần)
//   confirmText    chữ nút xác nhận (mặc định "Xác nhận")
//   cancelText     chữ nút hủy (mặc định "Bỏ qua")
//   isDanger       true → nút xác nhận màu đỏ (mặc định true)
//   hideCancel     true → chỉ có 1 nút (dùng như hộp thông báo, VD hết phiên MSG06)
//   requireReason  true → bắt nhập lý do 5–500 ký tự (GBR-09: từ chối/hủy/xóa phải có lý do)
//   reasonLabel    nhãn ô lý do (mặc định "Lý do")
//   onConfirm      hàm chạy khi bấm xác nhận, nhận vào lý do (chuỗi rỗng nếu không bắt nhập);
//                  trả về false để giữ hộp thoại mở
// Luồng xử lý:
// B1: đóng hộp thoại cũ nếu đang mở, dựng khung nền mờ + hộp
// B2: dựng tiêu đề, nội dung, ô lý do (nếu cần), 2 nút
// B3: gắn sự kiện: bấm nền mờ / nút hủy / phím Esc → đóng; nút xác nhận → kiểm tra lý do → gọi onConfirm
// B4: gắn vào trang, đặt con trỏ vào ô lý do hoặc nút xác nhận
function openConfirmDialog(options) {
    // B1
    closeConfirmDialog();

    let confirmText = options.confirmText;
    if (confirmText === undefined) {
        confirmText = "Xác nhận";
    }
    let cancelText = options.cancelText;
    if (cancelText === undefined) {
        cancelText = "Bỏ qua";
    }
    let isDanger = options.isDanger;
    if (isDanger === undefined) {
        isDanger = true;
    }
    let reasonLabel = options.reasonLabel;
    if (reasonLabel === undefined) {
        reasonLabel = "Lý do";
    }

    const backdrop = document.createElement("div");
    backdrop.className = "modal-backdrop";

    const dialog = document.createElement("div");
    dialog.className = "modal-box";
    dialog.setAttribute("role", "dialog");
    dialog.setAttribute("aria-modal", "true");
    dialog.setAttribute("aria-labelledby", "confirmDialogTitle");

    // B2
    const title = document.createElement("h3");
    title.className = "modal-title";
    title.id = "confirmDialogTitle";
    title.textContent = options.title;
    dialog.appendChild(title);

    if (options.message) {
        const message = document.createElement("p");
        message.className = "modal-message";
        message.textContent = options.message;
        dialog.appendChild(message);
    }

    let reasonInput = null;
    let reasonError = null;
    if (options.requireReason === true) {
        const reasonField = document.createElement("div");
        reasonField.className = "field";

        const label = document.createElement("label");
        label.className = "field-label";
        label.setAttribute("for", "confirmDialogReason");
        label.textContent = reasonLabel + " *";

        reasonInput = document.createElement("textarea");
        reasonInput.id = "confirmDialogReason";
        reasonInput.rows = 3;
        reasonInput.maxLength = REASON_MAX_LENGTH;

        reasonError = document.createElement("div");
        reasonError.className = "field-error";
        reasonError.id = "confirmDialogReasonError";
        reasonError.hidden = true;

        reasonField.appendChild(label);
        reasonField.appendChild(reasonInput);
        reasonField.appendChild(reasonError);
        dialog.appendChild(reasonField);
    }

    const actions = document.createElement("div");
    actions.className = "btn-row modal-actions";

    const cancelButton = document.createElement("button");
    cancelButton.type = "button";
    cancelButton.className = "btn btn-outline";
    cancelButton.textContent = cancelText;
    if (options.hideCancel !== true) {
        actions.appendChild(cancelButton);
    }

    const confirmButton = document.createElement("button");
    confirmButton.type = "button";
    if (isDanger) {
        confirmButton.className = "btn btn-danger";
    } else {
        confirmButton.className = "btn btn-primary";
    }
    confirmButton.textContent = confirmText;
    actions.appendChild(confirmButton);
    dialog.appendChild(actions);
    backdrop.appendChild(dialog);

    // B3
    backdrop.addEventListener("click", function (event) {
        const isClickOnBackdrop = event.target === backdrop;
        if (isClickOnBackdrop && options.hideCancel !== true) {
            closeConfirmDialog();
        }
    });
    cancelButton.addEventListener("click", closeConfirmDialog);
    confirmButton.addEventListener("click", function () {
        let reason = "";
        if (reasonInput !== null) {
            reason = reasonInput.value.trim();
            const errorMessage = getReasonErrorMessage(reason);
            if (errorMessage !== "") {
                showFieldError(reasonInput, errorMessage);
                reasonInput.focus();
                return;
            }
        }
        let shouldKeepOpen = false;
        if (typeof options.onConfirm === "function") {
            shouldKeepOpen = options.onConfirm(reason) === false;
        }
        if (!shouldKeepOpen) {
            closeConfirmDialog();
        }
    });
    document.addEventListener("keydown", handleDialogEscapeKey);

    // B4
    document.body.appendChild(backdrop);
    activeDialog = {
        backdrop: backdrop,
        canCancel: options.hideCancel !== true
    };
    if (reasonInput !== null) {
        reasonInput.focus();
    } else {
        confirmButton.focus();
    }
}

// Đóng hộp thoại đang mở (nếu có) và gỡ sự kiện phím Esc
function closeConfirmDialog() {
    if (activeDialog === null) {
        return;
    }
    activeDialog.backdrop.remove();
    activeDialog = null;
    document.removeEventListener("keydown", handleDialogEscapeKey);
}

// Bấm phím Esc thì đóng hộp thoại (trừ hộp thông báo chỉ có 1 nút)
function handleDialogEscapeKey(event) {
    const isEscape = event.key === "Escape";
    if (isEscape && activeDialog !== null && activeDialog.canCancel) {
        closeConfirmDialog();
    }
}

// Trả câu lỗi cho ô lý do theo GBR-09/DBR-05, chuỗi rỗng nếu hợp lệ
function getReasonErrorMessage(reason) {
    if (reason.length < REASON_MIN_LENGTH) {
        return formatMessage(MESSAGES.MSG19_TOO_SHORT, [REASON_MIN_LENGTH]);
    }
    if (reason.length > REASON_MAX_LENGTH) {
        return formatMessage(MESSAGES.MSG10_MAX_LENGTH, [REASON_MAX_LENGTH]);
    }
    return "";
}

// Gắn hộp thoại xác nhận cho nút submit/link có thuộc tính data-confirm-message (không cần viết JS riêng)
// Thuộc tính trên nút:
//   data-confirm-message="Hủy đơn DH-2026-0001?"   (bắt buộc) nội dung hỏi
//   data-confirm-title="Hủy đơn hàng"               tiêu đề (mặc định "Xác nhận thao tác")
//   data-confirm-text="Hủy đơn"                     chữ nút xác nhận
//   data-confirm-reason-name="cancelReason"         có thuộc tính này → bắt nhập lý do, lý do được đưa vào
//                                                   input ẩn cùng tên trong form để Servlet đọc getParameter("cancelReason")
// Luồng xử lý:
// B1: tìm mọi phần tử có data-confirm-message
// B2: khi bấm → chặn hành động mặc định, mở hộp thoại
// B3: xác nhận → nếu là nút trong form: ghi lý do vào input ẩn rồi gửi form (giữ đúng nút đã bấm);
//     nếu là link: chuyển tới href
function bindConfirmButtons() {
    // B1
    const triggers = document.querySelectorAll("[data-confirm-message]");

    for (const trigger of triggers) {
        // B2
        trigger.addEventListener("click", function (event) {
            event.preventDefault();

            let dialogTitle = trigger.dataset.confirmTitle;
            if (dialogTitle === undefined) {
                dialogTitle = "Xác nhận thao tác";
            }
            const reasonName = trigger.dataset.confirmReasonName;
            const needsReason = reasonName !== undefined;

            openConfirmDialog({
                title: dialogTitle,
                message: trigger.dataset.confirmMessage,
                confirmText: trigger.dataset.confirmText,
                requireReason: needsReason,
                onConfirm: function (reason) {
                    // B3
                    submitConfirmedAction(trigger, reasonName, reason);
                }
            });
        });
    }
}

// Thực hiện hành động của nút/link sau khi người dùng đã xác nhận (dùng bởi bindConfirmButtons)
function submitConfirmedAction(trigger, reasonName, reason) {
    const form = trigger.closest("form");

    if (form === null) {
        const href = trigger.getAttribute("href");
        if (href !== null) {
            window.location.href = href;
        }
        return;
    }

    if (reasonName !== undefined) {
        let reasonInput = form.querySelector('input[type="hidden"][name="' + reasonName + '"]');
        if (reasonInput === null) {
            reasonInput = document.createElement("input");
            reasonInput.type = "hidden";
            reasonInput.name = reasonName;
            form.appendChild(reasonInput);
        }
        reasonInput.value = reason;
    }

    // requestSubmit(nút) gửi form kèm name/value của đúng nút đã bấm và vẫn chạy kiểm tra dữ liệu;
    // phần tử không phải nút submit (VD thẻ a) thì gửi form bình thường
    const isSubmitButton = trigger.type === "submit";
    if (isSubmitButton) {
        form.requestSubmit(trigger);
    } else {
        form.requestSubmit();
    }
}

// ===== 4. FORM: HIỂN THỊ LỖI, KIỂM TRA DỮ LIỆU, NÚT ĐANG XỬ LÝ, CẢNH BÁO CHƯA LƯU =====

// ----- 4.1 Hiển thị lỗi dưới ô nhập (GBR-17: lỗi màu đỏ ngay dưới ô) -----

// Hiện lỗi màu đỏ dưới 1 ô nhập và tô viền đỏ ô đó
// Luồng xử lý:
// B1: thêm class "invalid" + aria-invalid cho ô nhập (CSS tô viền đỏ)
// B2: lấy ô báo lỗi có id = id ô nhập + "Error" (tạo nếu HTML chưa có)
// B3: ghi icon + nội dung lỗi (bằng textContent, an toàn) và hiện ô báo lỗi
function showFieldError(input, message) {
    // B1
    input.classList.add("invalid");
    input.setAttribute("aria-invalid", "true");

    // B2
    const errorElement = getFieldErrorElement(input);
    input.setAttribute("aria-describedby", errorElement.id);

    // B3
    errorElement.innerHTML = '<i class="fa-solid fa-circle-xmark" aria-hidden="true"></i>';
    const text = document.createElement("span");
    text.textContent = message;
    errorElement.appendChild(text);
    errorElement.hidden = false;
}

// Xóa lỗi của 1 ô nhập (bỏ viền đỏ, ẩn ô báo lỗi)
function clearFieldError(input) {
    input.classList.remove("invalid");
    input.removeAttribute("aria-invalid");

    const errorId = input.id + "Error";
    const errorElement = document.getElementById(errorId);
    if (errorElement !== null) {
        errorElement.hidden = true;
        errorElement.textContent = "";
    }
}

// Xóa lỗi của mọi ô trong 1 form
function clearFormErrors(form) {
    const fields = getValidatableFields(form);
    for (const field of fields) {
        clearFieldError(field);
    }
}

// Hiện lỗi theo từng ô do server trả về (JsonResponse.errors — tên ô → câu lỗi)
// Input: form đang gửi; errors = { "email": "Email này đã được dùng...", ... } (tên ô = thuộc tính name)
// Output: hiện lỗi dưới từng ô, đặt con trỏ vào ô lỗi đầu tiên
function showServerFieldErrors(form, errors) {
    let firstInvalidField = null;
    const fieldNames = Object.keys(errors);

    for (const fieldName of fieldNames) {
        let field = form.elements.namedItem(fieldName);
        if (field === null) {
            continue;
        }
        // Nhóm radio trả về danh sách — lấy ô đầu tiên để hiện lỗi
        if (field instanceof RadioNodeList) {
            field = field[0];
        }
        showFieldError(field, errors[fieldName]);
        if (firstInvalidField === null) {
            firstInvalidField = field;
        }
    }

    if (firstInvalidField !== null) {
        firstInvalidField.focus();
    }
}

// Tìm ô báo lỗi của 1 ô nhập; HTML chưa có thì tạo <div class="field-error"> đặt cuối khối .field chứa ô đó
function getFieldErrorElement(input) {
    const errorId = input.id + "Error";
    let errorElement = document.getElementById(errorId);
    if (errorElement !== null) {
        return errorElement;
    }

    errorElement = document.createElement("div");
    errorElement.className = "field-error";
    errorElement.id = errorId;
    errorElement.hidden = true;

    const fieldWrapper = input.closest(".field");
    if (fieldWrapper !== null) {
        fieldWrapper.appendChild(errorElement);
    } else {
        input.insertAdjacentElement("afterend", errorElement);
    }
    return errorElement;
}

// ----- 4.2 Kiểm tra dữ liệu theo thuộc tính data-* của ô nhập (GBR-17, DBR-01…18) -----

// Kiểm tra 1 ô nhập theo các thuộc tính khai báo trên HTML, trả về câu lỗi hoặc chuỗi rỗng nếu hợp lệ
// Thứ tự kiểm tra: bắt buộc → độ dài → quy tắc định dạng (data-rule) → ngày tối thiểu → khớp ô khác (data-match)
// VD HTML: <input id="phone" name="phone" required data-label="Số điện thoại" data-rule="phone">
function validateField(input) {
    // B1: ô bị khóa thì không kiểm tra (người dùng không sửa được)
    if (input.disabled) {
        return "";
    }

    // B2: lấy giá trị (mật khẩu giữ nguyên khoảng trắng, các ô khác bỏ khoảng trắng 2 đầu — GBR-19)
    let value = input.value;
    if (input.type !== "password") {
        value = value.trim();
    }
    const label = getFieldLabel(input);

    // B3: kiểm tra bắt buộc
    const isCheckbox = input.type === "checkbox";
    if (input.required && isCheckbox && !input.checked) {
        return formatMessage(MESSAGES.MSG09_REQUIRED, [label]);
    }
    if (input.required && isBlank(value)) {
        if (input.tagName === "SELECT") {
            return formatMessage(MESSAGES.MSG21_SELECT_REQUIRED, [label.toLowerCase()]);
        }
        return formatMessage(MESSAGES.MSG09_REQUIRED, [label]);
    }

    // B4: ô không bắt buộc và để trống → hợp lệ
    if (isBlank(value)) {
        return "";
    }

    // B5: kiểm tra độ dài
    const hasMaxLength = input.maxLength > 0;
    if (hasMaxLength && value.length > input.maxLength) {
        return formatMessage(MESSAGES.MSG10_MAX_LENGTH, [input.maxLength]);
    }
    const minLength = Number(input.dataset.minLength);
    if (minLength > 0 && value.length < minLength) {
        return formatMessage(MESSAGES.MSG19_TOO_SHORT, [minLength]);
    }

    // B6: kiểm tra định dạng theo data-rule
    const ruleMessage = getRuleErrorMessage(input.dataset.rule, value);
    if (ruleMessage !== "") {
        return ruleMessage;
    }

    // B7: ô ngày có thuộc tính min (VD chỉ chọn từ ngày mai) → MSG18
    const isDateInput = input.type === "date";
    if (isDateInput && input.min !== "" && value < input.min) {
        return formatMessage(MESSAGES.MSG18_DATE_TOO_EARLY, [formatDate(input.min)]);
    }

    // B8: ô phải khớp ô khác (xác nhận mật khẩu)
    const matchId = input.dataset.match;
    if (matchId !== undefined) {
        const otherInput = document.getElementById(matchId);
        if (otherInput !== null && otherInput.value !== input.value) {
            return MESSAGES.MSG47_PASSWORD_MISMATCH;
        }
    }

    return "";
}

// Trả câu lỗi theo quy tắc định dạng data-rule, chuỗi rỗng nếu hợp lệ hoặc không có quy tắc
function getRuleErrorMessage(rule, value) {
    if (rule === "phone" && !isValidPhone(value)) {
        return MESSAGES.MSG13_INVALID_PHONE;
    }
    if (rule === "email" && !isValidEmail(value)) {
        return MESSAGES.MSG11_INVALID_EMAIL;
    }
    if (rule === "password" && value.length < PASSWORD_MIN_LENGTH) {
        return formatMessage(MESSAGES.MSG46_PASSWORD_TOO_SHORT, [PASSWORD_MIN_LENGTH]);
    }
    if (rule === "password" && !isValidPassword(value)) {
        return formatMessage(MESSAGES.EXTRA_PASSWORD_RULE, [PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH]);
    }
    if (rule === "fullName" && value.length < FULL_NAME_MIN_LENGTH) {
        return formatMessage(MESSAGES.MSG19_TOO_SHORT, [FULL_NAME_MIN_LENGTH]);
    }
    if (rule === "fullName" && !isValidFullName(value)) {
        return MESSAGES.EXTRA_FULL_NAME_RULE;
    }
    if (rule === "verificationCode" && !isValidVerificationCode(value)) {
        return MESSAGES.EXTRA_CODE_FORMAT;
    }
    if (rule === "reason") {
        return getReasonErrorMessage(value);
    }
    if (rule === "positiveInteger" && !isPositiveInteger(value)) {
        return MESSAGES.MSG17_POSITIVE_NUMBER;
    }
    if (rule === "dateOfBirth") {
        return getDateOfBirthErrorMessage(value);
    }
    return "";
}

// Trả câu lỗi cho ngày sinh (DBR-04): không ở tương lai, tuổi không quá 100
function getDateOfBirthErrorMessage(isoDate) {
    const today = getTodayIsoDate();
    if (isoDate > today) {
        return MESSAGES.MSG15_DOB_IN_FUTURE;
    }
    if (!isValidDateOfBirth(isoDate)) {
        return formatMessage(MESSAGES.EXTRA_DOB_TOO_OLD, [MAX_AGE_YEARS]);
    }
    return "";
}

// Lấy tên ô để ghép vào câu lỗi: ưu tiên data-label, rồi chữ trong <label for>, cuối cùng là name
function getFieldLabel(input) {
    if (input.dataset.label !== undefined) {
        return input.dataset.label;
    }
    if (input.id !== "") {
        const labelElement = document.querySelector('label[for="' + input.id + '"]');
        if (labelElement !== null) {
            const labelText = labelElement.textContent.replace("*", "");
            return labelText.trim();
        }
    }
    return input.name;
}

// Kiểm tra 1 ô và hiện/xóa lỗi ngay trên giao diện
// Output: true nếu ô hợp lệ
function checkFieldAndShowError(input) {
    const errorMessage = validateField(input);
    if (errorMessage === "") {
        clearFieldError(input);
        return true;
    }
    showFieldError(input, errorMessage);
    return false;
}

// Kiểm tra toàn bộ form trước khi gửi
// Luồng xử lý:
// B1: lấy mọi ô nhập cần kiểm tra trong form
// B2: kiểm tra từng ô, hiện lỗi dưới từng ô sai (không dừng ở ô sai đầu tiên, để người dùng thấy hết lỗi)
// B3: đặt con trỏ vào ô sai đầu tiên
// Output: true nếu mọi ô hợp lệ
function validateForm(form) {
    // B1
    const fields = getValidatableFields(form);
    let firstInvalidField = null;

    // B2
    for (const field of fields) {
        const isValid = checkFieldAndShowError(field);
        if (!isValid && firstInvalidField === null) {
            firstInvalidField = field;
        }
    }

    // B3
    if (firstInvalidField !== null) {
        firstInvalidField.focus();
        return false;
    }
    return true;
}

// Lấy các ô nhập người dùng điền được trong form (bỏ input ẩn và các nút)
function getValidatableFields(form) {
    const allFields = form.querySelectorAll("input, select, textarea");
    const result = [];
    for (const field of allFields) {
        const isSkippedType = field.type === "hidden" || field.type === "submit" || field.type === "button";
        if (!isSkippedType) {
            result.push(field);
        }
    }
    return result;
}

// Gắn kiểm tra TỪNG Ô cho form: rời ô thì kiểm tra; đang sửa ô đang báo lỗi thì kiểm tra lại để lỗi tự mất
// Dùng trực tiếp cho form gửi bằng AJAX (form thường thì dùng bindFormValidation)
function bindFieldValidation(form) {
    // Tắt bong bóng kiểm tra mặc định của trình duyệt, dùng thông báo tiếng Việt của hệ thống
    form.noValidate = true;

    form.addEventListener("focusout", function (event) {
        const field = event.target;
        const isFormField = field.matches("input, select, textarea");
        if (isFormField) {
            checkFieldAndShowError(field);
        }
    });

    form.addEventListener("input", function (event) {
        const field = event.target;
        if (field.classList.contains("invalid")) {
            checkFieldAndShowError(field);
        }
    });
}

// Gắn kiểm tra cho form gửi THƯỜNG (POST về Servlet, có thuộc tính data-validate) — initCommon tự gọi
// Luồng xử lý:
// B1: gắn kiểm tra từng ô (bindFieldValidation)
// B2: khi submit → kiểm tra cả form; sai thì chặn gửi và chặn luôn các xử lý submit khác của trang
// B3: đúng thì khóa nút vừa bấm để không gửi 2 lần (GBR-22); khóa sau 1 nhịp để trình duyệt kịp
//     lấy name/value của nút đưa vào dữ liệu gửi đi (nút bị disabled sẽ không được gửi)
function bindFormValidation(form) {
    // B1
    bindFieldValidation(form);

    form.addEventListener("submit", function (event) {
        // B2
        const isValid = validateForm(form);
        if (!isValid) {
            event.preventDefault();
            event.stopImmediatePropagation();
            return;
        }

        // B3
        const submitButton = event.submitter;
        if (submitButton !== null && submitButton !== undefined) {
            setTimeout(function () {
                setButtonLoading(submitButton, true);
            }, 0);
        }
    });
}

// ----- 4.3 Nút đang xử lý (GBR-22: khóa nút gửi trong lúc chờ server) -----

// Bật/tắt trạng thái "đang xử lý" cho 1 nút
// Luồng xử lý:
// B1 (bật): nhớ chữ gốc vào data-original-text, khóa nút, đổi chữ thành "Đang xử lý…", thêm class is-loading (CSS vẽ vòng xoay)
// B2 (tắt): trả lại chữ gốc, mở khóa nút
function setButtonLoading(button, isLoading) {
    if (isLoading) {
        // B1
        if (button.dataset.originalText === undefined) {
            button.dataset.originalText = button.textContent.trim();
        }
        button.disabled = true;
        button.classList.add("is-loading");
        button.setAttribute("aria-busy", "true");
        button.textContent = "Đang xử lý…";
        return;
    }

    // B2
    if (button.dataset.originalText !== undefined) {
        button.textContent = button.dataset.originalText;
        delete button.dataset.originalText;
    }
    button.disabled = false;
    button.classList.remove("is-loading");
    button.removeAttribute("aria-busy");
}

// ----- 4.4 Cảnh báo rời trang khi chưa lưu (GBR-24, MSG28) -----

// Đánh dấu form "có thay đổi chưa lưu" khi người dùng sửa bất kỳ ô nào — initCommon tự gọi cho form[data-unsaved-warning]
// Luồng xử lý:
// B1: gắn data-dirty="false" ban đầu; sửa ô nào → "true"; gửi form → về "false" (markFormSaved)
// B2: gắn 1 lần duy nhất cho cả trang:
//     - bấm link nội bộ khi còn form chưa lưu → hộp thoại MSG28 của hệ thống
//     - đóng tab / tải lại trang → hộp cảnh báo mặc định của trình duyệt (trình duyệt không cho đổi câu chữ)
function bindUnsavedChangesWarning(form) {
    // B1
    form.dataset.dirty = "false";
    form.addEventListener("input", function () {
        form.dataset.dirty = "true";
    });
    form.addEventListener("change", function () {
        form.dataset.dirty = "true";
    });
    form.addEventListener("submit", function () {
        markFormSaved(form);
    });

    // B2
    if (isLeavePageWarningBound) {
        return;
    }
    isLeavePageWarningBound = true;
    document.addEventListener("click", handleLeavePageLinkClick);
    window.addEventListener("beforeunload", function (event) {
        if (hasUnsavedForms()) {
            event.preventDefault();
            event.returnValue = "";
        }
    });
}

// Đánh dấu form đã lưu — gọi sau khi lưu AJAX thành công để không còn bị cảnh báo khi rời trang
function markFormSaved(form) {
    form.dataset.dirty = "false";
}

// Trang còn form nào đang có thay đổi chưa lưu không
function hasUnsavedForms() {
    const dirtyForm = document.querySelector('form[data-unsaved-warning][data-dirty="true"]');
    return dirtyForm !== null;
}

// Chặn bấm link nội bộ khi còn thay đổi chưa lưu, hỏi lại bằng hộp thoại MSG28
function handleLeavePageLinkClick(event) {
    const link = event.target.closest("a[href]");
    if (link === null) {
        return;
    }
    const href = link.getAttribute("href");
    const isSamePageAnchor = href.startsWith("#");
    const opensNewTab = link.target === "_blank";
    const hasOwnConfirm = link.dataset.confirmMessage !== undefined;
    if (isSamePageAnchor || opensNewTab || hasOwnConfirm || !hasUnsavedForms()) {
        return;
    }

    event.preventDefault();
    openConfirmDialog({
        title: "Rời trang?",
        message: MESSAGES.MSG28_UNSAVED_CHANGES,
        confirmText: "Rời trang",
        cancelText: "Ở lại",
        onConfirm: function () {
            const dirtyForms = document.querySelectorAll('form[data-unsaved-warning][data-dirty="true"]');
            for (const dirtyForm of dirtyForms) {
                markFormSaved(dirtyForm);
            }
            window.location.href = link.href;
        }
    });
}

// ===== 5. HIỆN/ẨN MẬT KHẨU (GBR-30) =====

// Gắn nút con mắt cho ô mật khẩu — initCommon tự gọi
// VD HTML: <div class="input-wrap">
//            <input id="password" type="password" ...>
//            <button type="button" class="password-toggle" data-password-toggle="password" aria-label="Hiện mật khẩu">
//              <i class="fa-solid fa-eye"></i>
//            </button>
//          </div>
// Luồng xử lý:
// B1: tìm mọi nút có data-password-toggle (giá trị = id của ô mật khẩu)
// B2: bấm → đổi type password ↔ text, đổi icon mắt mở/mắt gạch, cập nhật aria-label cho trình đọc màn hình
function bindPasswordToggles() {
    document.addEventListener("click", function (event) {
        // Không bắt [data-toggle-target]: thuộc tính đó dành cho nút mở/đóng vùng (menu mobile) — bindToggleButtons()
        const button = event.target.closest("[data-password-toggle], [data-pw-toggle]");
        if (!button) {
            return;
        }

        const targetId = button.dataset.passwordToggle || button.dataset.pwToggle;
        const input = document.getElementById(targetId);
        if (!(input instanceof HTMLInputElement)) {
            return;
        }

        event.preventDefault();

        const isHidden = input.type === "password";
        input.type = isHidden ? "text" : "password";

        const icon = button.querySelector("i");
        if (icon) {
            icon.className = isHidden ? "fa-solid fa-eye-slash" : "fa-solid fa-eye";
        } else {
            button.innerHTML = isHidden
                ? '<i class="fa-solid fa-eye-slash" aria-hidden="true"></i>'
                : '<i class="fa-solid fa-eye" aria-hidden="true"></i>';
        }

        button.setAttribute("aria-label", isHidden ? "Ẩn mật khẩu" : "Hiện mật khẩu");
    });
}

// ===== 6. ĐẾM NGƯỢC mm:ss (GBR-27: hạn thanh toán, giữ slot, mã xác thực, khóa tạm) =====

// Tự chạy đếm ngược cho mọi phần tử có data-countdown-seconds — initCommon tự gọi
// VD JSP: <b data-countdown-seconds="${secondsLeft}" data-countdown-reload="true">20:00</b>
//   data-countdown-seconds  số giây còn lại, do SERVER tính (không dựa vào đồng hồ máy khách)
//   data-countdown-reload   "true" → hết giờ thì tải lại trang để lấy trạng thái mới (GBR-27)
function bindCountdowns() {
    const countdownElements = document.querySelectorAll("[data-countdown-seconds]");

    for (const element of countdownElements) {
        const secondsLeft = Number(element.dataset.countdownSeconds);
        const shouldReload = element.dataset.countdownReload === "true";
        startCountdown(element, secondsLeft, function () {
            if (shouldReload) {
                window.location.reload();
            }
        });
    }
}

// Chạy đồng hồ đếm ngược hiển thị mm:ss trong 1 phần tử
// Input: element hiển thị; secondsLeft số giây còn lại; onFinish hàm gọi khi về 0 (có thể bỏ trống)
// Output: mã bộ đếm, truyền vào stopCountdown() để dừng sớm (null nếu đã hết giờ ngay từ đầu)
// Luồng xử lý:
// B1: dừng bộ đếm cũ đang chạy trên cùng phần tử (nếu có)
// B2: đã hết giờ sẵn → hiện 00:00, gọi onFinish, kết thúc
// B3: tính thời điểm kết thúc = bây giờ + số giây (tính theo mốc thời gian nên tab bị ngủ vẫn đúng giờ)
// B4: mỗi giây gọi updateCountdown; lưu mã bộ đếm vào data-countdown-timer-id để updateCountdown tự dừng khi về 0
function startCountdown(element, secondsLeft, onFinish) {
    // B1
    const oldTimerId = element.dataset.countdownTimerId;
    if (oldTimerId !== undefined) {
        stopCountdown(Number(oldTimerId));
    }

    // B2
    if (secondsLeft <= 0) {
        element.textContent = formatCountdown(0);
        if (typeof onFinish === "function") {
            onFinish();
        }
        return null;
    }

    // B3
    const endTime = Date.now() + secondsLeft * 1000;

    // B4
    const timerId = setInterval(updateCountdown, 1000, element, endTime, onFinish);
    element.dataset.countdownTimerId = String(timerId);
    updateCountdown(element, endTime, onFinish);
    return timerId;
}

// Cập nhật 1 lần hiển thị đếm ngược (setInterval gọi mỗi giây)
// Luồng xử lý:
// B1: tính số giây còn lại tới endTime, ghi ra dạng mm:ss
// B2: về 0 → dừng bộ đếm và gọi onFinish
function updateCountdown(element, endTime, onFinish) {
    // B1
    const remainingMs = endTime - Date.now();
    let remainingSeconds = Math.ceil(remainingMs / 1000);
    if (remainingSeconds < 0) {
        remainingSeconds = 0;
    }
    element.textContent = formatCountdown(remainingSeconds);

    // B2
    if (remainingSeconds === 0) {
        stopCountdown(Number(element.dataset.countdownTimerId));
        delete element.dataset.countdownTimerId;
        if (typeof onFinish === "function") {
            onFinish();
        }
    }
}

// Dừng 1 đồng hồ đếm ngược đang chạy
function stopCountdown(timerId) {
    clearInterval(timerId);
}

// ===== 7. DANH SÁCH: PHÂN TRANG, LỌC, TRẠNG THÁI RỖNG (GBR-18, GBR-20, GBR-21) =====
// Danh sách phân trang ở SERVER (OFFSET/FETCH), tham số nằm trên URL: ?page=2&pageSize=20&keyword=...&status=...
// Form lọc dùng method="get" và KHÔNG gửi kèm "page" → đổi bộ lọc là tự về trang 1 (GBR-18).

// Đổi tham số trên URL rồi tải lại trang danh sách
// Input: params = { pageSize: 20, page: 1 } — giá trị rỗng/null thì xóa tham số đó khỏi URL
function changeUrlParams(params) {
    const url = new URL(window.location.href);
    const names = Object.keys(params);
    for (const name of names) {
        const value = params[name];
        const isEmptyValue = value === null || value === undefined || value === "";
        if (isEmptyValue) {
            url.searchParams.delete(name);
        } else {
            url.searchParams.set(name, value);
        }
    }
    window.location.assign(url.toString());
}

// Gắn ô chọn số dòng mỗi trang (10/20/50) — đổi lựa chọn thì về trang 1 — initCommon tự gọi
// VD HTML: <select data-page-size-select> <option>10</option> <option>20</option> <option>50</option> </select>
function bindPageSizeSelects() {
    const selects = document.querySelectorAll("select[data-page-size-select]");
    for (const select of selects) {
        select.addEventListener("change", function () {
            changeUrlParams({ pageSize: select.value, page: 1 });
        });
    }
}

// Gắn nút "Xóa bộ lọc": bỏ mọi tham số trên URL để về bộ lọc mặc định (GBR-20) — initCommon tự gọi
// VD HTML: <button type="button" data-clear-filters>Xóa bộ lọc</button>
function bindClearFiltersButtons() {
    const buttons = document.querySelectorAll("[data-clear-filters]");
    for (const button of buttons) {
        button.addEventListener("click", function () {
            window.location.assign(window.location.pathname);
        });
    }
}

// Gắn select lọc tự gửi form khi đổi lựa chọn (không cần bấm nút Lọc) — initCommon tự gọi
// VD HTML: <select name="status" data-auto-submit> ... </select> (nằm trong form method="get")
function bindAutoSubmitSelects() {
    const selects = document.querySelectorAll("select[data-auto-submit]");
    for (const select of selects) {
        select.addEventListener("change", function () {
            const form = select.closest("form");
            if (form !== null) {
                form.requestSubmit();
            }
        });
    }
}

// Hiện thông báo rỗng trong 1 vùng danh sách được đổ bằng AJAX (GBR-21)
// Input: container vùng danh sách; message = MESSAGES.MSG01_NO_RESULT / MSG02_EMPTY_LIST / MSG03_NO_DATA_IN_RANGE
// Luồng xử lý:
// B1: xóa toàn bộ nội dung cũ trong vùng
// B2: thêm 1 khối .empty-state chứa câu thông báo (textContent, an toàn)
function renderEmptyState(container, message) {
    // B1
    container.innerHTML = "";

    // B2
    const emptyState = document.createElement("div");
    emptyState.className = "empty-state";
    emptyState.textContent = message;
    container.appendChild(emptyState);
}

// ===== 8. TAB & VÙNG BẬT/TẮT =====

// Gắn chuyển tab — initCommon tự gọi
// VD HTML:
//   <div role="tablist">
//     <button type="button" role="tab" data-tab="profile" aria-selected="true">Hồ sơ</button>
//     <button type="button" role="tab" data-tab="password" aria-selected="false">Mật khẩu</button>
//   </div>
//   <section data-tab-panel="profile">...</section>
//   <section data-tab-panel="password" hidden>...</section>
// Luồng xử lý:
// B1: với mỗi nhóm tab, lấy các nút data-tab
// B2: bấm 1 nút → đánh dấu aria-selected cho đúng nút đó, hiện panel cùng tên, ẩn các panel khác của nhóm
function bindTabs() {
    const tabLists = document.querySelectorAll('[role="tablist"]');

    for (const tabList of tabLists) {
        // B1
        const tabButtons = tabList.querySelectorAll("[data-tab]");

        for (const button of tabButtons) {
            // B2
            button.addEventListener("click", function () {
                for (const otherButton of tabButtons) {
                    const isCurrent = otherButton === button;
                    otherButton.setAttribute("aria-selected", String(isCurrent));
                    const panel = document.querySelector('[data-tab-panel="' + otherButton.dataset.tab + '"]');
                    if (panel !== null) {
                        panel.hidden = !isCurrent;
                    }
                }
            });
        }
    }
}

// Gắn nút mở/đóng 1 vùng (menu mobile, sidebar, khối nâng cao...) — initCommon tự gọi
// VD HTML: <button type="button" data-toggle-target="mobileNav" aria-expanded="false">Menu</button>
//          <nav id="mobileNav" hidden>...</nav>
//   Thêm data-toggle-class="is-open" nếu muốn bật/tắt class (có hiệu ứng CSS) thay vì thuộc tính hidden
// Luồng xử lý:
// B1: bấm nút → tìm vùng theo id trong data-toggle-target
// B2: bật/tắt class hoặc thuộc tính hidden, cập nhật aria-expanded cho trình đọc màn hình
function bindToggleButtons() {
    const toggleButtons = document.querySelectorAll("[data-toggle-target]");

    for (const button of toggleButtons) {
        button.addEventListener("click", function () {
            // B1
            const target = document.getElementById(button.dataset.toggleTarget);
            if (target === null) {
                return;
            }

            // B2
            const toggleClass = button.dataset.toggleClass;
            let isOpen = false;
            if (toggleClass !== undefined) {
                isOpen = target.classList.toggle(toggleClass);
            } else {
                target.hidden = !target.hidden;
                isOpen = !target.hidden;
            }
            button.setAttribute("aria-expanded", String(isOpen));
        });
    }
}

// ===== 9. AJAX — gọi Servlet không tải lại trang =====
// Servlet AJAX luôn trả khuôn JSON chuẩn (dto/JsonResponse.java):
//   { "success": true|false, "message": "...", "data": {...}, "errors": { "tênÔ": "câu lỗi" } }
// Dữ liệu gửi lên dạng form (application/x-www-form-urlencoded) → Servlet đọc bằng request.getParameter() như bình thường.
// Gửi kèm file (FormData có ảnh) → dạng multipart, Servlet cần thêm @MultipartConfig.

// Gửi 1 request AJAX tới Servlet và nhận về kết quả theo khuôn JSON chuẩn
// Input:
//   method  "GET" | "POST"
//   url     đường dẫn tính từ context path, VD "/customer/checkout/voucher" (tự ghép context path)
//   data    (không bắt buộc) object { tên: giá trị }, URLSearchParams hoặc FormData
// Output: Promise trả về object { success, message, data, errors } — luôn có đủ 4 trường, không bao giờ throw
// Các lỗi chung được xử lý sẵn tại đây, trang KHÔNG cần tự xử lý lại:
//   mất mạng → toast MSG05 · hết phiên (401) → hộp thoại MSG06 rồi về trang đăng nhập (GBR-31)
//   sai quyền (403) → toast MSG07 · lỗi server (5xx / không phải JSON) → toast MSG04
async function sendAjaxRequest(method, url, data) {
    // B1: chuẩn bị URL, header, nội dung gửi
    const upperMethod = method.toUpperCase();
    let requestUrl = buildUrl(url);
    const requestOptions = {
        method: upperMethod,
        headers: {
            "Accept": "application/json"
        },
        credentials: "same-origin"
    };
    requestOptions.headers[AJAX_HEADER_NAME] = AJAX_HEADER_VALUE;

    const hasData = data !== undefined && data !== null;
    if (hasData && upperMethod === "GET") {
        const queryString = toUrlSearchParams(data).toString();
        if (queryString !== "") {
            requestUrl = requestUrl + "?" + queryString;
        }
    }
    if (hasData && upperMethod !== "GET") {
        if (data instanceof FormData) {
            requestOptions.body = data;
        } else {
            requestOptions.body = toUrlSearchParams(data);
        }
    }

    // B2: gửi request; lỗi mạng (không tới được server) → MSG05
    let response = null;
    try {
        response = await fetch(requestUrl, requestOptions);
    } catch (networkError) {
        console.error(networkError);
        showToast("warning", MESSAGES.MSG05_CONNECTION_LOST);
        return createFailureResult(MESSAGES.MSG05_CONNECTION_LOST);
    }

    // B3: hết phiên đăng nhập → hộp thoại MSG06, chuyển về đăng nhập kèm trang hiện tại
    if (response.status === 401) {
        showSessionExpiredDialog();
        return createFailureResult(MESSAGES.MSG06_SESSION_EXPIRED);
    }

    // B4: sai vai trò → MSG07
    if (response.status === 403) {
        showToast("error", MESSAGES.MSG07_NO_PERMISSION);
        return createFailureResult(MESSAGES.MSG07_NO_PERMISSION);
    }

    // B5: đọc JSON; server lỗi hoặc trả về không phải JSON → MSG04
    let body = null;
    try {
        body = await response.json();
    } catch (parseError) {
        console.error(parseError);
    }
    const isServerError = response.status >= 500;
    if (body === null || isServerError) {
        showToast("error", MESSAGES.MSG04_SYSTEM_BUSY);
        return createFailureResult(MESSAGES.MSG04_SYSTEM_BUSY);
    }

    // B6: chuẩn hóa để trang luôn đọc được đủ 4 trường
    return normalizeAjaxResult(body);
}

// Gọi GET lấy dữ liệu (tra cứu, đổ dropdown, tải danh sách)
// Input: url tính từ context path; params (không bắt buộc) object tham số truy vấn
// Output: Promise { success, message, data, errors }
function getJson(url, params) {
    return sendAjaxRequest("GET", url, params);
}

// Gọi POST gửi dữ liệu lên Servlet
// Input: url tính từ context path; data object / URLSearchParams / FormData
// Output: Promise { success, message, data, errors }
function postData(url, data) {
    return sendAjaxRequest("POST", url, data);
}

// Gửi cả 1 form bằng AJAX, dùng cho form không muốn tải lại trang
// Input: form (thuộc tính action = URL Servlet, method = post)
// Output: Promise { success, message, data, errors } — trang tự quyết định làm gì tiếp khi success
// Luồng xử lý:
// B1: kiểm tra dữ liệu cả form, sai thì dừng luôn (không gọi server)
// B2: khóa nút gửi (GBR-22)
// B3: gom dữ liệu form; có chọn file thì gửi FormData (multipart), không thì gửi dạng form thường
// B4: gửi lên Servlet, mở khóa nút
// B5: thành công → bỏ đánh dấu "chưa lưu", hiện toast nếu server có message
//     thất bại → hiện lỗi dưới từng ô server trả về; không có lỗi theo ô thì hiện toast
//     (dữ liệu người dùng đã nhập được giữ nguyên — GBR-22)
async function submitFormAjax(form) {
    // B1
    const isValid = validateForm(form);
    if (!isValid) {
        return createFailureResult("");
    }

    // B2
    const submitButton = form.querySelector('[type="submit"]');
    if (submitButton !== null) {
        setButtonLoading(submitButton, true);
    }

    // B3
    const formData = new FormData(form);
    let payload = toUrlSearchParams(formData);
    if (formHasSelectedFile(form)) {
        payload = formData;
    }

    // B4: đọc bằng getAttribute — KHÔNG dùng form.action/form.method, vì form có ô/nút name="action"
    //     (rất hay gặp ở Servlet) thì form.action trả về chính ô đó thay vì URL
    let method = form.getAttribute("method");
    if (method === null) {
        method = "POST";
    }
    let actionUrl = form.getAttribute("action");
    if (actionUrl === null || actionUrl === "") {
        actionUrl = window.location.pathname;
    }
    const result = await sendAjaxRequest(method, actionUrl, payload);
    if (submitButton !== null) {
        setButtonLoading(submitButton, false);
    }

    // B5
    if (result.success) {
        markFormSaved(form);
        if (result.message) {
            showToast("success", result.message);
        }
        return result;
    }
    const errorFieldNames = Object.keys(result.errors);
    if (errorFieldNames.length > 0) {
        showServerFieldErrors(form, result.errors);
    } else if (result.message) {
        showToast("error", result.message);
    }
    return result;
}

// Hộp thoại báo hết phiên (MSG06), bấm nút thì về trang đăng nhập kèm returnUrl để đăng nhập xong quay lại (GBR-31)
function showSessionExpiredDialog() {
    const contextPath = getContextPath();
    const currentPath = window.location.pathname.substring(contextPath.length);
    const returnUrl = currentPath + window.location.search;
    const loginUrl = buildUrl("/login") + "?returnUrl=" + encodeURIComponent(returnUrl);

    openConfirmDialog({
        title: "Hết phiên đăng nhập",
        message: MESSAGES.MSG06_SESSION_EXPIRED,
        confirmText: "Đăng nhập lại",
        isDanger: false,
        hideCancel: true,
        onConfirm: function () {
            window.location.href = loginUrl;
        }
    });
}

// Đổi dữ liệu gửi đi (object / FormData / URLSearchParams) thành URLSearchParams
function toUrlSearchParams(data) {
    if (data instanceof URLSearchParams) {
        return data;
    }
    const params = new URLSearchParams();
    if (data instanceof FormData) {
        for (const entry of data.entries()) {
            const isTextValue = typeof entry[1] === "string";
            if (isTextValue) {
                params.append(entry[0], entry[1]);
            }
        }
        return params;
    }
    const names = Object.keys(data);
    for (const name of names) {
        const value = data[name];
        if (value !== null && value !== undefined) {
            params.append(name, value);
        }
    }
    return params;
}

// Form có ô chọn file nào đã chọn file chưa
function formHasSelectedFile(form) {
    const fileInputs = form.querySelectorAll('input[type="file"]');
    for (const fileInput of fileInputs) {
        if (fileInput.files.length > 0) {
            return true;
        }
    }
    return false;
}

// Đảm bảo kết quả AJAX luôn có đủ success / message / data / errors
function normalizeAjaxResult(body) {
    let errors = body.errors;
    if (errors === null || errors === undefined) {
        errors = {};
    }
    let data = body.data;
    if (data === undefined) {
        data = null;
    }
    return {
        success: body.success === true,
        message: body.message,
        data: data,
        errors: errors
    };
}

// Tạo kết quả thất bại theo khuôn chuẩn (dùng khi lỗi xảy ra trước khi có phản hồi JSON từ server)
function createFailureResult(message) {
    return {
        success: false,
        message: message,
        data: null,
        errors: {}
    };
}

// ===== 10. ĐỊNH DẠNG HIỂN THỊ (GBR-01: dd/mm/yyyy, HH:mm · GBR-29: tiền có dấu chấm + "đ") =====
// Server gửi ngày giờ dạng ISO ("2026-09-24", "14:30", "2026-09-24T14:30:00") — xem util/JsonUtil.java.
// Các hàm dưới cắt chuỗi trực tiếp, KHÔNG dùng new Date("2026-09-24") vì trình duyệt hiểu đó là giờ UTC → lệch ngày.

// Định dạng tiền: 1250000 → "1.250.000đ"
function formatMoney(amount) {
    const number = Number(amount);
    if (Number.isNaN(number)) {
        return "";
    }
    const roundedNumber = Math.round(number);
    const groupedText = roundedNumber.toLocaleString("vi-VN");
    return groupedText + "đ";
}

// Định dạng ngày: "2026-09-24" hoặc "2026-09-24T14:30:00" hoặc đối tượng Date → "24/09/2026"
function formatDate(value) {
    if (isBlank(value)) {
        return "";
    }
    if (value instanceof Date) {
        const day = String(value.getDate()).padStart(2, "0");
        const month = String(value.getMonth() + 1).padStart(2, "0");
        return day + "/" + month + "/" + value.getFullYear();
    }
    const datePart = String(value).substring(0, 10);
    const parts = datePart.split("-");
    return parts[2] + "/" + parts[1] + "/" + parts[0];
}

// Định dạng giờ: "14:30", "14:30:00", "2026-09-24T14:30:00" hoặc Date → "14:30"
function formatTime(value) {
    if (isBlank(value)) {
        return "";
    }
    if (value instanceof Date) {
        const hours = String(value.getHours()).padStart(2, "0");
        const minutes = String(value.getMinutes()).padStart(2, "0");
        return hours + ":" + minutes;
    }
    let timePart = String(value);
    const separatorIndex = timePart.indexOf("T");
    if (separatorIndex >= 0) {
        timePart = timePart.substring(separatorIndex + 1);
    }
    return timePart.substring(0, 5);
}

// Định dạng ngày giờ: "2026-09-24T14:30:00" → "24/09/2026 14:30"
function formatDateTime(value) {
    if (isBlank(value)) {
        return "";
    }
    return formatDate(value) + " " + formatTime(value);
}

// Định dạng số giây còn lại thành mm:ss (GBR-27): 1200 → "20:00", 65 → "01:05"
function formatCountdown(totalSeconds) {
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    const minutesText = String(minutes).padStart(2, "0");
    const secondsText = String(seconds).padStart(2, "0");
    return minutesText + ":" + secondsText;
}

// Che email khi hiển thị (MSG52): "an.nguyen@gmail.com" → "a***@gmail.com"
function maskEmail(email) {
    const atIndex = email.indexOf("@");
    if (atIndex <= 0) {
        return email;
    }
    const firstCharacter = email.substring(0, 1);
    const domain = email.substring(atIndex);
    return firstCharacter + "***" + domain;
}

// Hiển thị số điện thoại dễ đọc: "0912345678" → "0912 345 678"
function formatPhoneDisplay(phone) {
    if (!isValidPhone(phone)) {
        return phone;
    }
    return phone.substring(0, 4) + " " + phone.substring(4, 7) + " " + phone.substring(7);
}

// Ngày hôm nay dạng "yyyy-mm-dd" theo đồng hồ máy người dùng (dùng so sánh với giá trị ô input type="date")
function getTodayIsoDate() {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, "0");
    const day = String(today.getDate()).padStart(2, "0");
    return year + "-" + month + "-" + day;
}

// ===== 11. KIỂM TRA DỮ LIỆU (DBR-01 … DBR-18) — hàm thuần, chỉ trả true/false =====
// Phía trình duyệt chỉ để báo lỗi sớm cho người dùng; server LUÔN kiểm tra lại (GBR-13).
// Khớp với util/ValidationUtil.java — sửa luật ở đây thì sửa cả bên Java.

// Chuỗi rỗng / chỉ có khoảng trắng / null / undefined
function isBlank(value) {
    if (value === null || value === undefined) {
        return true;
    }
    return String(value).trim() === "";
}

// DBR-17: đúng 10 chữ số, bắt đầu bằng 0 (đầu số nhà mạng do server kiểm tra)
function isValidPhone(phone) {
    if (isBlank(phone)) {
        return false;
    }
    return PHONE_PATTERN.test(phone.trim());
}

// DBR-18: dạng name@domain, tối đa 100 ký tự
function isValidEmail(email) {
    if (isBlank(email)) {
        return false;
    }
    const trimmedEmail = email.trim();
    if (trimmedEmail.length > EMAIL_MAX_LENGTH) {
        return false;
    }
    return EMAIL_PATTERN.test(trimmedEmail);
}

// DBR-02: 8–64 ký tự, có ít nhất 1 chữ cái và 1 chữ số
function isValidPassword(password) {
    if (password === null || password === undefined) {
        return false;
    }
    const isLengthOk = password.length >= PASSWORD_MIN_LENGTH && password.length <= PASSWORD_MAX_LENGTH;
    const hasLetter = HAS_LETTER_PATTERN.test(password);
    const hasDigit = HAS_DIGIT_PATTERN.test(password);
    return isLengthOk && hasLetter && hasDigit;
}

// DBR-01: 2–50 ký tự, chỉ chữ cái (có dấu), khoảng trắng, nháy đơn, gạch nối, có ít nhất 1 chữ cái
function isValidFullName(fullName) {
    if (!isLengthInRange(fullName, FULL_NAME_MIN_LENGTH, FULL_NAME_MAX_LENGTH)) {
        return false;
    }
    // Chuẩn hóa NFC để chữ + dấu gõ rời được gộp thành 1 ký tự
    const normalizedName = fullName.trim().normalize("NFC");
    const isPatternOk = FULL_NAME_PATTERN.test(normalizedName);
    const hasLetter = HAS_LETTER_PATTERN.test(normalizedName);
    return isPatternOk && hasLetter;
}

// DBR-03: đúng 6 chữ số
function isValidVerificationCode(code) {
    if (isBlank(code)) {
        return false;
    }
    return VERIFICATION_CODE_PATTERN.test(code.trim());
}

// Độ dài (sau khi bỏ khoảng trắng 2 đầu) nằm trong [min, max]
function isLengthInRange(value, min, max) {
    if (isBlank(value)) {
        return false;
    }
    const length = value.trim().length;
    return length >= min && length <= max;
}

// DBR-05, GBR-09: lý do 5–500 ký tự
function isValidReason(reason) {
    return isLengthInRange(reason, REASON_MIN_LENGTH, REASON_MAX_LENGTH);
}

// DBR-06, DBR-14: số nguyên lớn hơn 0 (tiền VND, số lượng)
function isPositiveInteger(value) {
    const text = String(value).trim();
    const isDigitsOnly = /^\d+$/.test(text);
    if (!isDigitsOnly) {
        return false;
    }
    return Number(text) > 0;
}

// DBR-04: ngày sinh ("yyyy-mm-dd") không ở tương lai và tuổi không quá 100
function isValidDateOfBirth(isoDate) {
    if (isBlank(isoDate)) {
        return false;
    }
    const today = getTodayIsoDate();
    if (isoDate > today) {
        return false;
    }
    const birthParts = isoDate.split("-");
    const todayParts = today.split("-");
    let age = Number(todayParts[0]) - Number(birthParts[0]);
    const birthdayThisYear = birthParts[1] + "-" + birthParts[2];
    const todayMonthDay = todayParts[1] + "-" + todayParts[2];
    if (todayMonthDay < birthdayThisYear) {
        age = age - 1;
    }
    return age <= MAX_AGE_YEARS;
}

// DBR-13: khoảng ngày hợp lệ khi Từ ≤ Đến (2 chuỗi "yyyy-mm-dd" so sánh trực tiếp được)
function isValidDateRange(fromIsoDate, toIsoDate) {
    if (isBlank(fromIsoDate) || isBlank(toIsoDate)) {
        return false;
    }
    return fromIsoDate <= toIsoDate;
}

// DBR-08: file ảnh JPG/PNG tối đa 5 MB (kích thước pixel do server kiểm tra)
// Input: đối tượng File lấy từ input.files[0]
// Câu lỗi tương ứng: formatMessage(MESSAGES.MSG20_INVALID_IMAGE, [IMAGE_MAX_SIZE_LABEL])
function isValidImageFile(file) {
    if (file === null || file === undefined) {
        return false;
    }
    const isAllowedType = file.type === "image/jpeg" || file.type === "image/png";
    const isSizeOk = file.size > 0 && file.size <= IMAGE_MAX_BYTES;
    return isAllowedType && isSizeOk;
}

// ===== 12. TIỆN ÍCH NỀN =====

// Lấy context path của ứng dụng (VD "/gms") từ thẻ <meta name="context-path"> trong head.jspf
function getContextPath() {
    const meta = document.querySelector('meta[name="context-path"]');
    if (meta === null) {
        return "";
    }
    return meta.getAttribute("content");
}

// Ghép context path vào đường dẫn nội bộ: "/customer/bookings" → "/gms/customer/bookings"
// URL đầy đủ (bắt đầu bằng http) hoặc đã có sẵn context path thì giữ nguyên
function buildUrl(path) {
    const isAbsoluteUrl = path.startsWith("http://") || path.startsWith("https://");
    if (isAbsoluteUrl) {
        return path;
    }
    const contextPath = getContextPath();
    const hasContextPath = contextPath !== "" && path.startsWith(contextPath + "/");
    if (hasContextPath) {
        return path;
    }
    return contextPath + path;
}

// Escape HTML trước khi chèn dữ liệu động bằng innerHTML, chống XSS (codeImplementationFrontend.md Mục 4)
// Chỉ cần chèn chữ thuần thì dùng element.textContent = ... (an toàn hơn, không cần hàm này)
function escapeHtml(text) {
    if (text === null || text === undefined) {
        return "";
    }
    const div = document.createElement("div");
    div.textContent = String(text);
    return div.innerHTML;
}

// Thay lần lượt các chỗ %s / %d trong câu mẫu bằng giá trị
// VD formatMessage(MESSAGES.MSG09_REQUIRED, ["Họ tên"]) → "Họ tên là bắt buộc."
function formatMessage(template, values) {
    let result = template;
    for (const value of values) {
        result = result.replace(/%[sd]/, String(value));
    }
    return result;
}

// Chuẩn hóa chữ để tìm kiếm không phân biệt hoa thường và dấu (GBR-19): "  Nguyễn  Đức " → "nguyen duc"
// Dùng khi lọc danh sách ngay trên trình duyệt; danh sách lọc ở server dùng COLLATE Vietnamese_CI_AI
function normalizeSearchText(text) {
    if (isBlank(text)) {
        return "";
    }
    const lowerText = String(text).toLowerCase();
    const withoutD = lowerText.replace(/đ/g, "d");
    const decomposed = withoutD.normalize("NFD");
    const withoutMarks = decomposed.replace(/[\u0300-\u036f]/g, "");
    const singleSpaced = withoutMarks.replace(/\s+/g, " ");
    return singleSpaced.trim();
}