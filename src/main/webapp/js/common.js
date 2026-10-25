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
//   8.  Tab & vùng bật/tắt .......... bindTabs, bindToggleButtons, bindCollapseGroups
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
//   [data-collapse-group]                  nhóm rèm <details data-collapse-item> + nút Mở tất cả / Thu gọn — bindCollapseGroups()
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
    // EN: No results found. Try different keywords or clear some filters.
    MSG01_NO_RESULT: "Không tìm thấy kết quả. Hãy thử từ khóa khác hoặc bỏ bớt bộ lọc.",
    // EN: No %s yet.
    MSG02_EMPTY_LIST: "Chưa có %s.",
    // EN: No data in the selected time range.
    MSG03_NO_DATA_IN_RANGE: "Không có dữ liệu trong khoảng thời gian đã chọn.",
    // EN: The system is busy. Please try again in a few minutes.
    MSG04_SYSTEM_BUSY: "Hệ thống đang bận. Vui lòng thử lại sau ít phút.",
    // EN: Connection lost. Check your network and try again.
    MSG05_CONNECTION_LOST: "Mất kết nối. Hãy kiểm tra mạng và thử lại.",
    // EN: Your session has expired. Please sign in again.
    MSG06_SESSION_EXPIRED: "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
    // EN: You do not have permission to access this function.
    MSG07_NO_PERMISSION: "Bạn không có quyền truy cập chức năng này.",
    // EN: This %s could not be found.
    MSG08_NOT_FOUND: "Không tìm thấy %s.",
    // EN: %s is required.
    MSG09_REQUIRED: "%s là bắt buộc.",
    // EN: Exceed max length of %d.
    MSG10_MAX_LENGTH: "Vượt quá độ dài tối đa %d ký tự.",
    // EN: Invalid email format.
    MSG11_INVALID_EMAIL: "Email không đúng định dạng.",
    // EN: This email is already used by another account.
    MSG12_EMAIL_USED: "Email này đã được tài khoản khác sử dụng.",
    // EN: Phone number must be 10 digits, start with 0 and use a valid Vietnamese carrier prefix.
    MSG13_INVALID_PHONE: "Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0 và có đầu số nhà mạng Việt Nam hợp lệ.",
    // EN: This phone number already belongs to another profile.
    MSG14_PHONE_USED: "Số điện thoại này đã thuộc về một hồ sơ khác.",
    // EN: Date of birth cannot be in the future.
    MSG15_DOB_IN_FUTURE: "Ngày sinh không được ở tương lai.",
    // EN: The start date must be on or before the end date.
    MSG16_DATE_RANGE_REVERSED: "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.",
    // EN: Enter a number greater than 0.
    MSG17_POSITIVE_NUMBER: "Nhập một số lớn hơn 0.",
    // EN: Only dates from %s onwards can be selected.
    MSG18_DATE_TOO_EARLY: "Chỉ được chọn ngày từ %s trở đi.",
    // EN: Be more specific, at least %d characters.
    MSG19_TOO_SHORT: "Vui lòng ghi cụ thể hơn, tối thiểu %d ký tự.",
    // EN: Only JPG or PNG images up to %s are accepted.
    MSG20_INVALID_IMAGE: "Chỉ chấp nhận ảnh JPG hoặc PNG, tối đa %s.",
    // EN: Please select %s.
    MSG21_SELECT_REQUIRED: "Vui lòng chọn %s.",
    // EN: This record was just updated by someone else. Reload before saving.
    MSG22_CONCURRENT_EDIT: "Bản ghi này vừa được người khác cập nhật. Hãy tải lại trước khi lưu.",
    // EN: Changes saved.
    MSG23_SAVED: "Đã lưu thay đổi.",
    // EN: There is nothing to save.
    MSG24_NOTHING_TO_SAVE: "Không có thay đổi nào để lưu.",
    // EN: %s added.
    MSG25_CREATED: "Đã thêm %s.",
    // EN: %s deleted.
    MSG26_DELETED: "Đã xóa %s.",
    // EN: Delete %s? This action cannot be undone.
    MSG27_CONFIRM_DELETE: "Xóa %s? Thao tác này không thể hoàn tác.",
    // EN: Your changes have not been saved. Leaving now will discard them.
    MSG28_UNSAVED_CHANGES: "Thay đổi của bạn chưa được lưu. Rời trang lúc này sẽ mất các thay đổi.",
    // EN: Cannot delete: %s still refers to it. Deactivate it instead.
    MSG29_STILL_REFERENCED: "Không thể xóa: %s vẫn đang tham chiếu tới mục này. Hãy ngừng hoạt động thay vì xóa.",
    // EN: %s deactivated.
    MSG30_DEACTIVATED: "Đã ngừng hoạt động %s.",
    // EN: %s reactivated.
    MSG31_REACTIVATED: "Đã kích hoạt lại %s.",
    // EN: %s downloaded.
    MSG32_EXPORTED: "Đã tải xuống %s.",
    // EN: Incorrect email, phone number or password. You have %d attempts left.
    MSG33_WRONG_CREDENTIALS_ATTEMPTS_LEFT: "Email, số điện thoại hoặc mật khẩu không đúng. Bạn còn %d lần thử.",
    // EN: Incorrect email, phone number or password. Please check again.
    MSG34_WRONG_CREDENTIALS_STAFF: "Email, số điện thoại hoặc mật khẩu không đúng. Vui lòng kiểm tra lại.",
    // EN: Account locked for %d minutes after too many failed attempts. Try again in %s.
    MSG35_ACCOUNT_TEMP_LOCKED: "Tài khoản bị khóa %d phút do nhập sai quá nhiều lần. Thử lại sau %s.",
    // EN: The lock has expired. You can sign in again.
    MSG36_LOCK_EXPIRED: "Đã hết thời gian khóa. Bạn có thể đăng nhập lại.",
    // EN: This account cannot be used to sign in. Please contact an administrator.
    MSG37_ACCOUNT_INACTIVE: "Tài khoản này không thể đăng nhập. Vui lòng liên hệ quản trị viên.",
    // EN: This account has no password yet. Please use Forgot password to set your password
    MSG38_NO_PASSWORD_YET: "Tài khoản này chưa có mật khẩu. Vui lòng dùng Quên mật khẩu để đặt mật khẩu.",
    // EN: Enter your email or phone number.
    MSG39_IDENTIFIER_REQUIRED: "Nhập email hoặc số điện thoại của bạn.",
    // EN: Signed out.
    MSG40_SIGNED_OUT: "Đã đăng xuất.",
    // EN: No account found with this email.
    MSG41_EMAIL_NOT_FOUND: "Không tìm thấy tài khoản với email này.",
    // EN: A code has been sent to %s. It is valid for %d minutes.
    MSG42_CODE_SENT: "Mã đã được gửi tới %s. Mã có hiệu lực trong %d phút.",
    // EN: Incorrect code. Check again or press Resend code.
    MSG43_WRONG_CODE: "Mã không đúng. Hãy kiểm tra lại hoặc bấm Gửi lại mã.",
    // EN: This code has expired. Press Resend code to get a new one.
    MSG44_CODE_EXPIRED: "Mã đã hết hạn. Bấm Gửi lại mã để nhận mã mới.",
    // EN: A new verification code has been sent.
    MSG45_CODE_RESENT: "Đã gửi mã xác thực mới.",
    // EN: Password must be at least %d characters.
    MSG46_PASSWORD_TOO_SHORT: "Mật khẩu phải có ít nhất %d ký tự.",
    // EN: The passwords do not match.
    MSG47_PASSWORD_MISMATCH: "Mật khẩu nhập lại không khớp.",
    // EN: The new password must differ from the current one.
    MSG48_PASSWORD_SAME_AS_OLD: "Mật khẩu mới phải khác mật khẩu hiện tại.",
    // EN: Password changed. All your other sessions have been signed out.
    MSG49_PASSWORD_CHANGED: "Đã đổi mật khẩu. Các phiên đăng nhập khác của bạn đã bị đăng xuất.",
    // EN: Phone number accepted. Please fill in your registration details.
    MSG50_PHONE_ACCEPTED: "Số điện thoại hợp lệ. Vui lòng điền thông tin đăng ký.",
    // EN: This phone number already has an account. Please sign in or use Forgot password.
    MSG51_PHONE_HAS_ACCOUNT: "Số điện thoại này đã có tài khoản. Vui lòng đăng nhập hoặc dùng Quên mật khẩu.",
    // EN: This phone number is registered with a different email (%s). Use that email, or visit the front desk to have it updated.
    MSG52_PHONE_DIFFERENT_EMAIL: "Số điện thoại này đã đăng ký với email khác (%s). Hãy dùng email đó, hoặc đến quầy lễ tân để cập nhật.",
    // EN: Your profile has no email on file, so it cannot be activated online. Please visit the front desk to add one.
    MSG53_PROFILE_NO_EMAIL: "Hồ sơ của bạn chưa có email nên không thể kích hoạt trực tuyến. Vui lòng đến quầy lễ tân để bổ sung.",
    // EN: Your account has been created. Please sign in.
    MSG54_ACCOUNT_CREATED: "Đã tạo tài khoản. Vui lòng đăng nhập.",
    // EN: A verification code has been sent to the new email address.
    MSG55_EMAIL_CHANGE_CODE_SENT: "Mã xác thực đã được gửi tới địa chỉ email mới.",
    // EN: Sign-in email updated. We notified the old address and signed out your other sessions.
    MSG56_EMAIL_CHANGED: "Đã cập nhật email đăng nhập. Chúng tôi đã thông báo tới email cũ và đăng xuất các phiên khác của bạn.",
    // EN: Phone number updated.
    MSG57_PHONE_CHANGED: "Đã cập nhật số điện thoại.",
    // EN: Profile picture updated.
    MSG58_AVATAR_CHANGED: "Đã cập nhật ảnh đại diện.",
    // EN: All other devices have been signed out.
    MSG59_OTHER_DEVICES_SIGNED_OUT: "Đã đăng xuất khỏi tất cả thiết bị khác.",
    // EN: This information is updated by the front desk. Please bring your ID to the counter.
    MSG60_UPDATED_BY_FRONT_DESK: "Thông tin này do lễ tân cập nhật. Vui lòng mang giấy tờ tùy thân đến quầy.",
    // EN: Too many attempts. Verification for this email is locked for %d minutes. Try again in %s.
    MSG61_VERIFICATION_LOCKED: "Thử quá nhiều lần. Xác thực cho email này bị khóa %d phút. Thử lại sau %s.",
    // EN: You have %d attempt(s) left to enter the code.
    MSG62_CODE_ATTEMPTS_LEFT: "Bạn còn %d lần nhập mã.",
    // EN: One more wrong entry will lock verification for this email for %d minutes.
    MSG63_CODE_LAST_ATTEMPT: "Nhập sai thêm một lần nữa sẽ khóa xác thực cho email này trong %d phút.",
    // EN: You can request %d more code(s) within 60 minutes.
    MSG64_CODE_RESENDS_LEFT: "Bạn còn có thể yêu cầu %d mã nữa trong 60 phút.",
    // EN: No code requests left. Requesting another code will lock verification for %d minutes.
    MSG65_CODE_RESEND_WILL_LOCK: "Đã hết lượt yêu cầu mã. Yêu cầu thêm sẽ khóa xác thực trong %d phút.",
    // EN: Password must be %d–%d characters and contain both letters and digits.
    MSG66_PASSWORD_RULE: "Mật khẩu phải dài %d–%d ký tự và có cả chữ lẫn số.",
    // EN: Full name may contain only letters, spaces, apostrophes or hyphens.
    MSG67_FULL_NAME_RULE: "Họ tên chỉ được chứa chữ cái, khoảng trắng, dấu nháy đơn hoặc dấu gạch nối.",
    // EN: The verification code must be exactly 6 digits.
    MSG68_CODE_FORMAT: "Mã xác thực phải gồm đúng 6 chữ số.",
    // EN: Invalid date of birth (age must not exceed %d).
    MSG69_DOB_TOO_OLD: "Ngày sinh không hợp lệ (tuổi không được vượt quá %d).",
    // EN: Please wait %d more seconds before resending the code.
    MSG70_CODE_RESEND_TOO_SOON: "Vui lòng chờ thêm %d giây trước khi gửi lại mã.",
    // EN: The current password is incorrect.
    MSG71_CURRENT_PASSWORD_WRONG: "Mật khẩu hiện tại không đúng.",
    // EN: Full name must be %d–%d characters.
    MSG72_FULL_NAME_LENGTH: "Họ tên phải dài %d–%d ký tự.",
    // EN: This post is no longer available.
    MSG73_POST_UNAVAILABLE: "Bài viết này không còn khả dụng.",
    // EN: Choose the role you want to work in.
    MSG74_CHOOSE_ROLE: "Chọn vai trò bạn muốn làm việc.",
    // EN: You are now working as %s.
    MSG75_ROLE_SWITCHED: "Bạn đang làm việc với vai trò %s.",
    // EN: You cannot perform this action on your own customer profile. Please ask another colleague.
    MSG76_STAFF_OWN_CUSTOMER_PROFILE: "Bạn không thể thao tác trên hồ sơ khách hàng của chính mình. Vui lòng nhờ đồng nghiệp khác.",
    // EN: Submission deadline passed. Leave on %s had to be submitted before %s on the previous day.
    MSG77_LEAVE_SUBMISSION_DEADLINE: "Đã quá hạn nộp. Đơn nghỉ ngày %s phải nộp trước %s của ngày hôm trước.",
    // EN: You still have %d scheduled session(s) in this period. Reschedule, transfer or cancel them before submitting the request.
    MSG78_LEAVE_BLOCKED_BY_SESSIONS: "Bạn còn %d buổi dạy trùng lịch nghỉ. Xem lịch và liên hệ quản lý để giải quyết trước khi gửi đơn.",
    // EN: Cannot approve: the %s shift on %s would have fewer than %d %s(s) on duty.
    MSG79_LEAVE_BREAKS_MIN_STAFFING: "Không thể duyệt: ca %s ngày %s sẽ có ít hơn %d %s trực.",
    // EN: %s needs a valid membership package before a PT package can be sold.
    MSG81_PT_REQUIRES_MEMBERSHIP: "%s cần có gói hội viên còn hiệu lực trước khi mua gói PT.",
    // EN: PT services are available only to customers aged %d or older.
    MSG82_PT_MIN_AGE: "Dịch vụ PT chỉ dành cho khách từ %d tuổi trở lên.",
    // EN: Check-in refused: the customer has no valid membership package or day pass today.
    MSG83_CHECKIN_NO_PACKAGE: "Từ chối check-in: hôm nay khách không có gói hội viên hoặc vé ngày hợp lệ.",
    // EN: This day pass has already been used.
    MSG84_DAY_PASS_USED: "Vé ngày này đã được sử dụng.",
    // EN: This voucher cannot be applied: %s.
    MSG85_VOUCHER_INVALID: "Không thể áp dụng voucher này: %s.",
    // EN: The voucher cannot be changed while a payment is in progress.
    MSG86_VOUCHER_PAYMENT_PENDING: "Không thể đổi voucher khi đang xử lý thanh toán.",
    // EN: Cannot close the shift: %d counter order(s) are still Pending.
    MSG87_CLOSE_SHIFT_PENDING_ORDERS: "Không thể đóng ca: còn %d đơn tại quầy đang chờ thanh toán.",
    // EN: This is the last active Admin account. It cannot be locked, demoted or set to Resigned.
    MSG88_LAST_ACTIVE_ADMIN: "Đây là tài khoản Admin hoạt động cuối cùng. Không thể khóa, hạ quyền hoặc chuyển sang Đã nghỉ việc.",
    // EN: The position cannot be changed while unfinished work remains: %s.
    MSG89_POSITION_CHANGE_UNFINISHED: "Không thể đổi vị trí khi còn công việc chưa hoàn thành: %s.",
    // EN: This PT package has already used all %d swaps.
    MSG90_SWAP_LIMIT_REACHED: "Gói PT này đã dùng hết %d lượt đổi.",
    // EN: This session has already started or is no longer scheduled, so it cannot be changed.
    MSG91_SESSION_CANNOT_CHANGE: "Buổi tập đã bắt đầu hoặc không còn ở trạng thái đã xếp lịch, nên không thể thay đổi.",
    // EN: The customer has no valid membership package on %s. Please choose another date.
    MSG92_NO_MEMBERSHIP_ON_DATE: "Khách không có gói hội viên hợp lệ vào ngày %s. Vui lòng chọn ngày khác.",
    // EN: This information is updated by the Manager.
    MSG93_UPDATED_BY_MANAGER: "Thông tin này do Quản lý cập nhật.",
    // EN: Only the Admin can change this information.
    MSG94_ONLY_ADMIN_CAN_CHANGE: "Chỉ Admin mới có thể thay đổi thông tin này.",
    // EN: Your password has been reset and all sessions have been signed out. Please sign in.
    MSG95_PASSWORD_RESET_COMPLETED: "Đã đặt lại mật khẩu và đăng xuất mọi phiên. Vui lòng đăng nhập.",
    // EN: Deletion request sent to the Manager for approval.
    MSG96_DELETION_REQUEST_SENT: "Đã gửi yêu cầu xóa tới Quản lý để duyệt.",
    // EN: This order can only be cancelled during the open shift that created it.
    MSG97_CANCEL_ORDER_OUTSIDE_SHIFT: "Chỉ có thể hủy đơn này trong ca đang mở đã tạo đơn.",
    // EN: The payment deadline has passed and the order was cancelled. Please create a new order.
    MSG98_ONLINE_ORDER_DEADLINE_PASSED: "Đã quá hạn thanh toán và đơn đã bị hủy. Vui lòng tạo đơn mới.",
    // EN: This package cannot be frozen: %s.
    MSG99_FREEZE_REFUSED: "Không thể bảo lưu gói này: %s.",
    // EN: A freeze can last at most %d month(s) and must end by %s.
    MSG100_FREEZE_LIMIT: "Bảo lưu tối đa %d tháng và phải kết thúc trước ngày %s.",
    // EN: This profile already has a pending deletion request.
    MSG101_PROFILE_PENDING_DELETION: "Hồ sơ này đã có yêu cầu xóa đang chờ duyệt.",
    // EN: This person already has an active staff record.
    MSG102_STAFF_ALREADY_EXISTS: "Người này đã có hồ sơ nhân viên đang hoạt động.",
    // EN: This slot has just been taken. Please choose another slot.
    MSG103_SLOT_ALREADY_TAKEN: "Khung giờ này vừa được người khác chọn. Vui lòng chọn khung giờ khác.",
    // EN: This slot overlaps an existing slot (%s–%s).
    MSG104_SLOT_OVERLAPS: "Khung giờ này trùng với khung giờ đã có (%s–%s).",
    // EN: This action is outside the allowed time window.
    MSG105_ACTION_OUTSIDE_WINDOW: "Thao tác này nằm ngoài khung thời gian cho phép."
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
    bindCollapseGroups();
    bindConfirmButtons();
    bindCountdowns();
    bindArticleImageFallbacks();
    bindHintBoxes();

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

// HintBox chung từ PT Dashboard: chạm để mở/đóng, Escape hoặc bấm ngoài để đóng.
// DOM: .pt-info > .pt-info-trigger + .pt-info-popover, dùng ở mọi zone.
function bindHintBoxes() {
    const hints = document.querySelectorAll(".pt-info");
    // Hộp mở ra dùng position: fixed (toạ độ màn hình) nên KHÔNG bị cột cuộn nào cắt mất;
    // canh phải theo nút i, luôn nằm trong mép màn hình, thiếu chỗ bên dưới thì lật lên trên.
    const EDGE = 12;   // khoảng cách tối thiểu tới mép màn hình (px)
    const GAP = 8;     // khoảng cách giữa nút i và hộp (px)
    function positionHint(hint) {
        const popover = hint.querySelector(".pt-info-popover");
        if (!popover) { return; }
        const compact = hint.closest(".my-bookings-page") !== null;
        for (const prop of ["position", "top", "left", "right", "bottom"]) { popover.style.removeProperty(prop); }
        if (!compact && window.matchMedia("(max-width: 700px)").matches) {
            return;   // mobile: CSS đã cho hộp nằm cố định ở đáy màn hình
        }
        const trigger = hint.querySelector(".pt-info-trigger") || hint;
        const bounds = trigger.getBoundingClientRect();
        const viewportWidth = document.documentElement.clientWidth;
        const viewportHeight = document.documentElement.clientHeight;

        // B1: chuyển sang fixed ở (0,0) rồi đo — bù lệch nếu tổ tiên có transform/backdrop-filter (vd topbar)
        popover.style.position = "fixed";
        popover.style.right = "auto";
        popover.style.bottom = "auto";
        popover.style.left = "0px";
        popover.style.top = "0px";
        const origin = popover.getBoundingClientRect();
        const width = origin.width;
        const height = origin.height;

        // B2: canh phải theo nút i, kẹp trong màn hình
        const left = Math.max(EDGE, Math.min(compact ? bounds.left : bounds.right - width, viewportWidth - width - EDGE));
        // B3: ưu tiên bên dưới nút; không đủ chỗ mà bên trên rộng hơn thì đặt lên trên
        let top = bounds.bottom + GAP;
        if (top + height > viewportHeight - EDGE && bounds.top - GAP - height >= EDGE) {
            top = bounds.top - GAP - height;
        }
        popover.style.left = (left - origin.left) + "px";
        popover.style.top = (Math.max(EDGE, Math.min(top, viewportHeight - height - EDGE)) - origin.top) + "px";
    }
    // Cuộn / đổi cỡ cửa sổ khi hộp đang mở → đặt lại vị trí theo nút i
    function repositionVisibleHints() {
        for (const hint of hints) {
            if (hint.matches(".is-open, :hover, :focus-within")) { positionHint(hint); }
        }
    }
    window.addEventListener("scroll", repositionVisibleHints, { capture: true, passive: true });
    window.addEventListener("resize", repositionVisibleHints);
    function closeHint(hint) {
        hint.classList.remove("is-open");
        hint.classList.add("is-dismissed");
        const trigger = hint.querySelector(".pt-info-trigger");
        if (trigger) { trigger.setAttribute("aria-expanded", "false"); }
    }
    for (const hint of hints) {
        const trigger = hint.querySelector(".pt-info-trigger");
        if (!trigger) { continue; }
        trigger.setAttribute("aria-expanded", "false");
        hint.addEventListener("pointerenter", function () { positionHint(hint); });
        hint.addEventListener("focusin", function () { positionHint(hint); });
        trigger.addEventListener("click", function () {
            const wasOpen = hint.classList.contains("is-open");
            for (const other of hints) { closeHint(other); }
            if (!wasOpen) {
                positionHint(hint);
                hint.classList.remove("is-dismissed");
                hint.classList.add("is-open");
                trigger.setAttribute("aria-expanded", "true");
            }
        });
        hint.addEventListener("pointerleave", function () {
            if (!hint.classList.contains("is-open")) {
                hint.classList.remove("is-dismissed");
            }
        });
        hint.addEventListener("keydown", function (event) {
            if (event.key === "Escape") {
                closeHint(hint);
                trigger.focus();
            }
        });
    }
    document.addEventListener("click", function (event) {
        for (const hint of hints) {
            if (!hint.contains(event.target)) { closeHint(hint); }
        }
    });
}

// Ảnh bài viết ở homepage/blog: dùng ảnh tĩnh thật khi URL bìa lỗi, không lặp error vô hạn.
// JSP nối bằng img[data-image-fallback]; ảnh đã lỗi trước DOMContentLoaded cũng được xử lý.
function bindArticleImageFallbacks() {
    const images = document.querySelectorAll("img[data-image-fallback]");
    for (const image of images) {
        function useFallback() {
            if (image.dataset.fallbackUsed === "true") {
                image.hidden = true;
                return;
            }
            image.dataset.fallbackUsed = "true";
            image.src = image.dataset.imageFallback;
        }
        image.addEventListener("error", useFallback);
        if (image.complete && image.naturalWidth === 0) {
            useFallback();
        }
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

            // UX Guard: If the enclosing form requires validation (data-validate),
            // validate all fields before opening the confirmation dialog.
            // This prevents opening unnecessary confirm modals when required fields
            // (e.g. rejection reasons or acceptance notes) are invalid or empty.
            const form = trigger.closest("form");
            if (form !== null && form.dataset.validate !== undefined) {
                if (!validateForm(form)) {
                    return;
                }
            }

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
    toggleDescribedBy(input, errorElement.id, true);

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
    toggleDescribedBy(input, errorId, false);
    const errorElement = document.getElementById(errorId);
    if (errorElement !== null) {
        errorElement.hidden = true;
        errorElement.textContent = "";
    }
}

// Thêm (on = true) hoặc bỏ 1 id trong aria-describedby, GIỮ các id khác (vd id dòng gợi ý dưới ô)
// → trình đọc màn hình đọc cả gợi ý lẫn lỗi. VD "emailHint" + lỗi → "emailHint emailError".
function toggleDescribedBy(input, id, on) {
    const ids = (input.getAttribute("aria-describedby") || "").split(" ").filter(function (x) {
        return x !== "" && x !== id;
    });
    if (on) {
        ids.push(id);
    }
    if (ids.length > 0) {
        input.setAttribute("aria-describedby", ids.join(" "));
    } else {
        input.removeAttribute("aria-describedby");
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
    // Check both custom dataset attributes (data-min-length / data-max-length) and native HTML attributes.
    // Using dataset attributes allows custom validation and styled error labels without triggering
    // unwanted native browser constraint validation tooltips.
    const maxLength = Number(input.dataset.maxLength) || (input.maxLength > 0 ? input.maxLength : 0);
    if (maxLength > 0 && value.length > maxLength) {
        return formatMessage(MESSAGES.MSG10_MAX_LENGTH, [maxLength]);
    }
    const minLength = Number(input.dataset.minLength) || (input.minLength > 0 ? input.minLength : 0);
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
        return formatMessage(MESSAGES.MSG66_PASSWORD_RULE, [PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH]);
    }
    if (rule === "fullName" && value.length < FULL_NAME_MIN_LENGTH) {
        return formatMessage(MESSAGES.MSG19_TOO_SHORT, [FULL_NAME_MIN_LENGTH]);
    }
    if (rule === "fullName" && !isValidFullName(value)) {
        return MESSAGES.MSG67_FULL_NAME_RULE;
    }
    if (rule === "verificationCode" && !isValidVerificationCode(value)) {
        return MESSAGES.MSG68_CODE_FORMAT;
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
        return formatMessage(MESSAGES.MSG69_DOB_TOO_OLD, [MAX_AGE_YEARS]);
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

    // Ô có lỗi do SERVER in sẵn (ô báo lỗi đang hiện) → đánh dấu "invalid" ngay khi mở trang,
    // để khách sửa ô là lỗi tự mất TRONG LÚC GÕ. Nếu đợi tới lúc rời ô thì dòng lỗi biến mất đúng lúc
    // nhấn chuột vào nút gửi → nút bị đẩy lên → cú bấm trượt khỏi nút, phải bấm lần 2.
    for (const field of getValidatableFields(form)) {
        const serverError = document.getElementById(field.id + "Error");
        if (serverError !== null && !serverError.hidden && serverError.textContent.trim() !== "") {
            field.classList.add("invalid");
            field.setAttribute("aria-invalid", "true");
        }
    }

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

// Hình loading của nút: Pumpy navy, bụng kem và xoáy navy; bàn tay tròn ngắn, đẩy quả tạ to hơn người.
// SVG tĩnh (không chứa dữ liệu người dùng); CSS ở common.css mục "Loading Pumpy đẩy tạ".
const PUMPY_PUSH_SVG =
    '<svg class="btn-pumpy" viewBox="0 0 100 35" aria-hidden="true" focusable="false">'
    + '<g class="bp-dust"><circle cx="5" cy="31" r="1.6"/><circle cx="2" cy="29" r="1.1"/></g>'
    + '<g class="bp-db">'
    + '<rect x="51" y="15" width="28" height="5" rx="1.5" fill="#91A7BE" stroke="#141414" stroke-width="1.4"/>'
    + '<rect x="46" y="4" width="6" height="27" rx="2" fill="#203D60" stroke="#141414" stroke-width="1.4"/>'
    + '<rect x="78" y="4" width="6" height="27" rx="2" fill="#203D60" stroke="#141414" stroke-width="1.4"/>'
    + '<rect x="33" y="1" width="13" height="33" rx="3" fill="#203D60" stroke="#141414" stroke-width="1.6"/>'
    + '<rect x="84" y="1" width="13" height="33" rx="3" fill="#203D60" stroke="#141414" stroke-width="1.6"/>'
    + '<rect x="38" y="5" width="2.2" height="25" rx="1" fill="#294F78"/>'
    + '<rect x="89.5" y="5" width="2.2" height="25" rx="1" fill="#294F78"/>'
    + '</g>'
    + '<g class="bp-pumpy">'
    + '<g class="bp-foot bp-foot--back"><ellipse cx="11" cy="31" rx="4" ry="2.4" fill="#294F78" stroke="#141414" stroke-width="1.4"/></g>'
    + '<path d="M9 12 L9.5 5 L14 9 Z M27 11 L25.5 4.5 L21.5 8.5 Z" fill="#294F78" stroke="#141414" stroke-width="1.3" stroke-linejoin="round"/>'
    + '<ellipse cx="18" cy="19" rx="11" ry="10" fill="#294F78" stroke="#141414" stroke-width="1.6"/>'
    + '<circle cx="29.5" cy="16" r="3.4" fill="#294F78" stroke="#141414" stroke-width="1.2"/>'
    + '<circle cx="30" cy="22" r="3.4" fill="#294F78" stroke="#141414" stroke-width="1.2"/>'
    + '<path d="M17 15 L19.5 16.5 L17 18 M24.5 15 L22 16.5 L24.5 18" fill="none" stroke="#141414" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round"/>'
    + '<path d="M18.5 22 H24" stroke="#141414" stroke-width="1.6" stroke-linecap="round"/>'
    + '<ellipse cx="18" cy="26" rx="4" ry="3.4" fill="#F2FBF8"/>'
    + '<path d="M17.4 25.6 a1 1 0 1 1 1 1 a2 2 0 1 1 -2 -2" fill="none" stroke="#24496E" stroke-width="0.8" stroke-linecap="round"/>'
    + '<g class="bp-foot bp-foot--front"><ellipse cx="20" cy="31" rx="4" ry="2.4" fill="#294F78" stroke="#141414" stroke-width="1.4"/></g>'
    + '</g>'
    + '<g class="bp-sweat"><path d="M9 7 q-2 -3 0 -5 q2 2 0 5z"/><path d="M5 12 q-2 -2.5 0 -4.5 q2 2 0 4.5z"/></g>'
    + '</svg>';

// Bật/tắt trạng thái "đang xử lý" cho 1 nút
// Luồng xử lý:
// B1 (bật): nhớ nội dung gốc của nút (cả icon), khóa nút, thay bằng Pumpy đẩy tạ + "Đang xử lý…", thêm class is-loading
// B2 (tắt): trả lại nội dung gốc, mở khóa nút
const buttonOriginalHtml = new WeakMap();

function setButtonLoading(button, isLoading) {
    if (isLoading) {
        // B1: đang loading rồi thì thôi (tránh 2 nơi cùng bật)
        if (button.classList.contains("is-loading")) {
            return;
        }
        buttonOriginalHtml.set(button, button.innerHTML);
        button.disabled = true;
        button.classList.add("is-loading");
        button.setAttribute("aria-busy", "true");
        // B1.1: hình Pumpy (chuỗi tĩnh) rồi chữ thường qua textContent
        button.innerHTML = '<span class="btn-pumpy-capsule" aria-hidden="true">' + PUMPY_PUSH_SVG + '</span>';
        const label = document.createElement("span");
        label.textContent = "Đang xử lý…";
        button.appendChild(label);
        return;
    }

    // B2
    if (buttonOriginalHtml.has(button)) {
        button.innerHTML = buttonOriginalHtml.get(button);
        buttonOriginalHtml.delete(button);
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
            if (target.id === "mobileNav") {
                button.setAttribute("aria-label", isOpen ? "Đóng menu" : "Mở menu");
            }
        });
        if (button.dataset.toggleTarget === "mobileNav") {
            const mobileNav = document.getElementById("mobileNav");
            if (!mobileNav) { continue; }
            function closeGuestMenu() {
                mobileNav.hidden = true;
                button.setAttribute("aria-expanded", "false");
                button.setAttribute("aria-label", "Mở menu");
            }
            mobileNav.addEventListener("click", function (event) {
                if (event.target.closest("a[href]")) { closeGuestMenu(); }
            });
            document.addEventListener("keydown", function (event) {
                if (event.key === "Escape" && !mobileNav.hidden) {
                    closeGuestMenu();
                    button.focus();
                }
            });
            window.matchMedia("(max-width: 1080px)").addEventListener("change", closeGuestMenu);
        }
    }
}

// Gắn nút Mở tất cả / Thu gọn cho một nhóm rèm <details> (VD: lịch theo ngày) — initCommon tự gọi
// VD HTML:
//   <div data-collapse-group>
//     <div data-collapse-controls hidden>
//       <button type="button" data-collapse-expand>Mở tất cả</button>
//       <button type="button" data-collapse-reset>Thu gọn</button>
//     </div>
//     <details data-collapse-item data-default-open open>Hôm nay…</details>
//     <details data-collapse-item>Ngày khác…</details>
//   </div>
// data-default-open đánh dấu rèm mở khi vào trang (VD hôm nay); Thu gọn quay về đúng trạng thái đó.
// Mỗi rèm vẫn tự mở/đóng bằng <details>, tắt JS vẫn dùng được; JS chỉ thêm hai nút cho cả nhóm.
// Luồng xử lý:
// B1: với mỗi nhóm, lấy các rèm; nhóm chỉ có 1 rèm thì không cần hai nút
// B2: hiện thanh nút; Mở tất cả mở mọi rèm, Thu gọn chỉ giữ rèm có data-default-open
function bindCollapseGroups() {
    const groups = document.querySelectorAll("[data-collapse-group]");

    for (const group of groups) {
        // B1
        const items = group.querySelectorAll("details[data-collapse-item]");
        const controls = group.querySelector("[data-collapse-controls]");
        if (controls === null || items.length < 2) {
            continue;
        }

        // B2
        controls.hidden = false;
        const expandButton = controls.querySelector("[data-collapse-expand]");
        const resetButton = controls.querySelector("[data-collapse-reset]");
        if (expandButton !== null) {
            expandButton.addEventListener("click", function () {
                for (const item of items) {
                    item.open = true;
                }
            });
        }
        if (resetButton !== null) {
            resetButton.addEventListener("click", function () {
                for (const item of items) {
                    item.open = item.hasAttribute("data-default-open");
                }
            });
        }
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

// =====================================================================
// HIỆU ỨNG GIAO DIỆN KHU NỘI BỘ (body.dock) — CSS ở common.css mục 12
// Mọi hiệu ứng tự tắt khi người dùng bật "Reduce motion".
// =====================================================================

function prefersReducedMotion() {
    return window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

// Sidebar thu gọn ở mọi màn hình; mở bằng tay nắm, đóng bằng Escape hoặc nền ngoài.
// DOM: #dock (layout-<vai-trò>.jspf), #dockHandle, .sidebar
function initDock() {
    const dock = document.getElementById("dock");
    const dockHandle = document.getElementById("dockHandle");
    if (!dock || !dockHandle) {
        return;
    }

    const sidebar = dock.querySelector(".sidebar");
    const backdrop = dock.querySelector(".dock-backdrop");
    const main = document.querySelector(".dock .main");

    // B1: đồng bộ trạng thái mở, inert và aria; nội dung không bị menu che khi menu đóng.
    function setDockOpen(isOpen) {
        dock.classList.toggle("is-open", isOpen);
        document.body.classList.toggle("nav-open", isOpen);
        dockHandle.setAttribute("aria-expanded", String(isOpen));
        dockHandle.setAttribute("aria-label", isOpen ? "Đóng menu điều hướng" : "Mở menu điều hướng");
        dockHandle.setAttribute("title", isOpen ? "Đóng menu" : "Mở menu");
        if (sidebar) {
            sidebar.inert = !isOpen;
        }
        if (main) {
            main.inert = isOpen;
        }
    }

    dockHandle.addEventListener("click", function () {
        setDockOpen(!dock.classList.contains("is-open"));
    });
    setDockOpen(false);
    if (backdrop) {
        backdrop.addEventListener("click", function () {
            setDockOpen(false);
            dockHandle.focus();
        });
    }

    // Click ngoài đóng menu ở mọi kích thước màn hình.
    document.addEventListener("pointerdown", function (event) {
        if (dock.classList.contains("is-open") && !dock.contains(event.target)) {
            setDockOpen(false);
            dockHandle.focus();
        }
    });

    // B2: Escape đóng menu và trả focus về tay nắm
    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && dock.classList.contains("is-open")) {
            setDockOpen(false);
            dockHandle.focus();
        }
    });

    // B3: giữ focus trong menu đang mở, kết thúc bằng tay nắm đóng menu.
    dock.addEventListener("keydown", function (event) {
        if (!dock.classList.contains("is-open") || event.key !== "Tab") {
            return;
        }
        const focusable = dock.querySelectorAll('a[href], button:not([disabled]):not([tabindex="-1"]), input:not([disabled])');
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    });
    if (sidebar) {
        sidebar.addEventListener("click", function (event) {
            if (event.target.closest("a[href]")) {
                setDockOpen(false);
            }
        });
        // Chặn cuộn nối sang trang ở hai đầu menu, kể cả menu ngắn không tràn.
        sidebar.addEventListener("wheel", function (event) {
            if (event.ctrlKey || event.deltaY === 0) {
                return;
            }
            const atTop = sidebar.scrollTop <= 0;
            const atBottom = sidebar.scrollTop + sidebar.clientHeight >= sidebar.scrollHeight - 1;
            if ((event.deltaY < 0 && atTop) || (event.deltaY > 0 && atBottom)) {
                event.preventDefault();
            }
        }, { passive: false });
        // B4: ánh sáng nhẹ đi theo con trỏ trên nền menu, không thay đổi bố cục.
        sidebar.addEventListener("pointermove", function (event) {
            if (prefersReducedMotion()) {
                return;
            }
            const rect = sidebar.getBoundingClientRect();
            sidebar.style.setProperty("--mx", (event.clientX - rect.left) + "px");
            sidebar.style.setProperty("--my", (event.clientY - rect.top + sidebar.scrollTop) + "px");
        });
    }
}

// Số lật kiểu bảng điểm: chỉ lật ký tự khác giá trị cũ, hàng đơn vị lật trước.
// Dùng: <span data-flip>1.248</span>; đổi giá trị bằng setFlipValue(el, "1.252").
function setFlipValue(element, text) {
    const oldText = element.dataset.flipValue || "";
    element.dataset.flipValue = text;
    element.setAttribute("aria-label", text);
    element.textContent = "";

    for (let i = 0; i < text.length; i++) {
        const span = document.createElement("span");
        span.className = "flip-char";
        span.setAttribute("aria-hidden", "true");
        span.textContent = text.charAt(i);
        // Ký tự không phải số (., đ, %, khoảng trắng) không đóng ô lật
        if (!/[0-9]/.test(text.charAt(i))) {
            span.classList.add("is-symbol");
        }

        // So từ bên phải để số có độ dài khác nhau vẫn khớp hàng
        const oldChar = oldText.charAt(oldText.length - text.length + i);
        if (oldText !== "" && oldChar !== text.charAt(i) && !prefersReducedMotion()) {
            span.classList.add("is-flipping");
            span.style.animationDelay = ((text.length - i) * 35) + "ms";
        }
        element.appendChild(span);
    }
}

// Khi tải trang: lật nhẹ các chữ số thật, không hiển thị số liệu giả trong lúc chờ.
function initFlipNumbers() {
    const items = document.querySelectorAll(".dock [data-flip]");
    for (let i = 0; i < items.length; i++) {
        const finalText = items[i].textContent.trim();
        if (prefersReducedMotion()) {
            setFlipValue(items[i], finalText);
            continue;
        }
        setFlipValue(items[i], finalText);
        const digits = items[i].querySelectorAll(".flip-char:not(.is-symbol)");
        for (let j = 0; j < digits.length; j++) {
            digits[j].classList.add("is-flipping");
            digits[j].style.animationDelay = Math.min(i * 45 + j * 20, 350) + "ms";
        }
    }
}

// Đếm số từ 0 khi tải trang. Giá trị thật nằm sẵn trong HTML (tắt JS vẫn đúng).
// Dùng: <b data-count>1.248</b> — giữ nguyên dấu chấm/phẩy kiểu Việt Nam.
function initCountUp() {
    if (prefersReducedMotion()) {
        return;
    }
    const items = document.querySelectorAll(".dock [data-count]");
    for (let i = 0; i < items.length; i++) {
        animateCount(items[i]);
    }
}

function animateCount(element) {
    const original = element.textContent.trim();
    // B1: tách phần số (bỏ dấu chấm nghìn, đổi phẩy thập phân) và phần chữ đi kèm
    const match = original.match(/^([\d.]+)(,(\d+))?(.*)$/);
    if (!match) {
        return;
    }
    const integerPart = Number(match[1].replace(/\./g, ""));
    const decimals = match[3] ? match[3].length : 0;
    const target = integerPart + (decimals > 0 ? Number("0." + match[3]) : 0);
    const suffix = match[4] || "";
    if (!Number.isFinite(target) || target === 0) {
        return;
    }

    // B2: chạy từ 0 tới đích trong 800ms, chậm dần ở cuối
    element.setAttribute("aria-label", original);
    const start = performance.now();
    function draw(now) {
        const progress = Math.min((now - start) / 800, 1);
        const eased = 1 - Math.pow(1 - progress, 3);
        const value = target * eased;
        element.textContent = value.toLocaleString("vi-VN", {
            minimumFractionDigits: decimals,
            maximumFractionDigits: decimals
        }) + suffix;
        if (progress < 1) {
            requestAnimationFrame(draw);
        } else {
            element.textContent = original;
        }
    }
    requestAnimationFrame(draw);
}

// Nghiêng 3D nhẹ theo con trỏ (chỉ chuột, không áp cho cảm ứng).
// Dùng: <div class="card" data-tilt>...</div>
function initTilt() {
    if (prefersReducedMotion() || !window.matchMedia("(hover: hover)").matches) {
        return;
    }
    const cards = document.querySelectorAll(".dock [data-tilt]");
    for (let i = 0; i < cards.length; i++) {
        attachTilt(cards[i]);
    }
}

function attachTilt(card) {
    card.addEventListener("pointermove", function (event) {
        const rect = card.getBoundingClientRect();
        const x = (event.clientX - rect.left) / rect.width;
        const y = (event.clientY - rect.top) / rect.height;
        // x,y trong [0,1] → góc trong [-4°, 4°]
        card.style.setProperty("--tilt-y", ((x - 0.5) * 8).toFixed(2) + "deg");
        card.style.setProperty("--tilt-x", ((0.5 - y) * 8).toFixed(2) + "deg");
        card.style.setProperty("--sx", (x * 100).toFixed(1) + "%");
        card.style.setProperty("--sy", (y * 100).toFixed(1) + "%");
        card.classList.add("is-tilting");
    });
    card.addEventListener("pointerleave", function () {
        card.style.setProperty("--tilt-x", "0deg");
        card.style.setProperty("--tilt-y", "0deg");
        card.classList.remove("is-tilting");
    });
}

// Hiện dần các khối [data-reveal] theo thứ tự, mỗi khối cách nhau 70ms.
function initReveal() {
    const blocks = document.querySelectorAll(".dock [data-reveal]");
    if (blocks.length === 0 || prefersReducedMotion()) {
        return;
    }
    document.body.classList.add("has-reveal");
    requestAnimationFrame(function () {
        for (let i = 0; i < blocks.length; i++) {
            setTimeout(function () {
                blocks[i].classList.add("is-revealed");
            }, 60 + i * 70);
        }
    });
}

document.addEventListener("DOMContentLoaded", function () {
    if (!document.body.classList.contains("dock")) {
        return;
    }
    initDock();
    initAccountAvatarStatus();
    initFlipNumbers();
    initCountUp();
    initTilt();
    initReveal();
    initDashboardMotionToggle();
});

// Nút trong .wx-gym-panel tạm dừng gym/canvas/icon; hai script cảnh đọc lớp trên body.
function initDashboardMotionToggle() {
    const button = document.querySelector("[data-dashboard-motion-toggle]");
    if (!button) {
        return;
    }
    const label = button.querySelector("span");
    const icon = button.querySelector("path");
    if (prefersReducedMotion()) {
        document.body.classList.add("dashboard-motion-paused");
        button.setAttribute("aria-pressed", "true");
        button.setAttribute("aria-label", "Hiệu ứng đã tắt theo tùy chọn giảm chuyển động");
        button.disabled = true;
        label.textContent = "Hiệu ứng đã tắt";
        return;
    }
    // Máy yếu đã tự vào chế độ nhẹ (gms-lite): mở trang ở trạng thái tạm dừng luôn
    if (document.documentElement.classList.contains("gms-lite")) {
        document.body.classList.add("dashboard-motion-paused");
        button.setAttribute("aria-pressed", "true");
        label.textContent = "Tiếp tục hiệu ứng";
        button.setAttribute("aria-label", label.textContent);
        icon.setAttribute("d", "m8 5 11 7-11 7z");
    }
    button.addEventListener("click", function () {
        const paused = document.body.classList.toggle("dashboard-motion-paused");
        // Cùng lúc bật/tắt chế độ nhẹ cho cả nền kính thủy cung, và nhớ lựa chọn cho các trang sau
        document.documentElement.classList.toggle("gms-lite", paused);
        try {
            localStorage.setItem("gms-fx", paused ? "lite" : "full");
        } catch (error) {
            // Trình duyệt chặn lưu trữ: chỉ áp cho trang này
        }
        button.setAttribute("aria-pressed", String(paused));
        label.textContent = paused ? "Tiếp tục hiệu ứng" : "Tạm dừng hiệu ứng";
        button.setAttribute("aria-label", label.textContent);
        icon.setAttribute("d", paused ? "m8 5 11 7-11 7z" : "M8 5v14M16 5v14");
        document.dispatchEvent(new Event("dashboardmotionchange"));
    });
}

// Một lớp bọc chung cho cả ảnh và chữ viết tắt; chấm trạng thái không bị ảnh cắt.
function initAccountAvatarStatus() {
    document.querySelectorAll(".dock .tb-user .avatar").forEach(function (avatar) {
        if (avatar.parentElement.classList.contains("tb-avatar")) { return; }
        const wrapper = document.createElement("span");
        wrapper.className = "tb-avatar";
        avatar.before(wrapper);
        wrapper.appendChild(avatar);
    });
}

// Bạt dùng chung PT/Customer Dashboard: cuốn lên khi cuộn xuống, thả xuống khi cuộn lên.
// DOM: .pt-awning (pt/_awning.jspf); CSS: awning.css + base.css đọc class is-retracted trên <body>.
document.addEventListener("DOMContentLoaded", function () {
    const page = document.querySelector(".pt-dashboard-page, .customer-weather-page");
    const awning = document.querySelector(".pt-awning");
    if (!page || !awning) {
        return;
    }
    let lastScrollY = window.scrollY;
    let frameQueued = false;
    function updateAwning() {
        const currentScrollY = window.scrollY;
        if (currentScrollY <= 80 || currentScrollY < lastScrollY) {
            page.classList.remove("is-retracted");
        } else if (currentScrollY > lastScrollY) {
            page.classList.add("is-retracted");
        }
        lastScrollY = currentScrollY;
        frameQueued = false;
    }
    window.addEventListener("scroll", function () {
        if (!frameQueued) {
            frameQueued = true;
            window.requestAnimationFrame(updateAwning);
        }
    }, {passive: true});
});

// Đồng hồ LED trên bảng phòng tập (pt/_awning.jspf): giờ:phút theo máy, cập nhật mỗi giây
document.addEventListener("DOMContentLoaded", function () {
    const clock = document.querySelector("[data-gym-clock]");
    if (!clock) {
        return;
    }
    const parts = clock.childNodes;
    function update() {
        const now = new Date();
        const hours = String(now.getHours()).padStart(2, "0");
        const minutes = String(now.getMinutes()).padStart(2, "0");
        parts[0].nodeValue = hours;
        parts[2].nodeValue = minutes;
        clock.setAttribute("aria-label", "Bây giờ là " + hours + " giờ " + minutes);
    }
    update();
    setInterval(update, 1000);

    // Mẹo tập / câu động lực trên bảng LED: đổi mỗi 8 giây, mờ dần khi chuyển câu
    const tip = document.querySelector("[data-gym-tip]");
    if (!tip || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        return;
    }
    const tips = [
        "Khởi động 5–10 phút trước khi vào mức tạ nặng.",
        "Uống khoảng 500 ml nước trước buổi tập 1–2 tiếng.",
        "Form chuẩn trước, tăng tạ sau.",
        "Nghỉ 60–90 giây giữa các hiệp để tăng cơ.",
        "Ngủ đủ 7–8 tiếng: cơ bắp lớn lên khi bạn nghỉ.",
        "Ăn đủ đạm: khoảng 1,6 g mỗi kg cân nặng mỗi ngày.",
        "Tăng tạ từ từ, khoảng 5–10% mỗi tuần.",
        "Giãn cơ 5 phút sau buổi tập để đỡ đau mỏi.",
        "Kỷ luật đưa bạn đi xa hơn cảm hứng.",
        "Mỗi buổi tập là một bước gần hơn tới mục tiêu.",
        "Không cần hoàn hảo, chỉ cần đều đặn.",
        "Thở ra khi đẩy hoặc kéo tạ, hít vào khi hạ tạ."
    ];
    let tipIndex = 0;
    setInterval(function () {
        if (document.hidden) {
            return;
        }
        tip.classList.add("is-fading");
        setTimeout(function () {
            tipIndex = (tipIndex + 1) % tips.length;
            tip.textContent = tips[tipIndex];
            tip.classList.remove("is-fading");
        }, 400);
    }, 8000);
});

// Nhiệt độ đã có sẵn trong HTML; JS chỉ cho số chạy nhanh khi không bật Reduce motion.
document.addEventListener("DOMContentLoaded", function () {
    const temperature = document.querySelector("[data-weather-temperature]");
    if (!temperature || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        return;
    }
    const target = Number(temperature.dataset.weatherTemperature);
    if (!Number.isFinite(target)) {
        return;
    }
    temperature.setAttribute("aria-label", target + " độ C");
    const start = performance.now();
    function drawTemperature(now) {
        const progress = Math.min((now - start) / 700, 1);
        temperature.textContent = Math.round(target * progress) + "°";
        if (progress < 1) {
            requestAnimationFrame(drawTemperature);
        }
    }
    requestAnimationFrame(drawTemperature);
});

// =====================================================================
// TÌM NHANH (Ctrl/⌘ + K) — khu nội bộ. Danh sách màn lấy từ menu sidebar (.nav-link) của khu đang mở,
// nên mỗi vai trò tự có đúng các màn của mình. Gõ để lọc (không dấu), ↑/↓ chọn, Enter mở, Esc đóng.
// CSS: common.css mục "Tìm nhanh". Dùng <dialog> gốc của trình duyệt (bẫy focus + Esc sẵn có).
// =====================================================================
document.addEventListener("DOMContentLoaded", function () {
    if (!document.body.classList.contains("dock")) {
        return;
    }
    const links = document.querySelectorAll(".dock .sidebar .nav-link[href]");
    const items = [];
    for (let i = 0; i < links.length; i++) {
        const href = links[i].getAttribute("href");
        const label = links[i].textContent.trim();
        // Bỏ mục chưa có màn (href="#") và mục trống
        if (href && href !== "#" && label) {
            items.push({ label: label, href: href, key: normalizeSearchText(label) });
        }
    }
    if (items.length === 0) {
        return;
    }

    // B1: dựng hộp thoại một lần
    const dialog = document.createElement("dialog");
    dialog.className = "cmdk";
    dialog.setAttribute("aria-label", "Tìm nhanh màn hình");
    dialog.innerHTML =
        '<div class="cmdk-box">'
        + '<input class="cmdk-input" type="search" placeholder="Tìm màn hình… (ví dụ: lich, voucher)" aria-label="Tìm màn hình" autocomplete="off">'
        + '<ul class="cmdk-list" role="listbox"></ul>'
        + '<p class="cmdk-foot"><kbd>↑</kbd><kbd>↓</kbd> chọn · <kbd>Enter</kbd> mở · <kbd>Esc</kbd> đóng</p>'
        + '</div>';
    document.body.appendChild(dialog);
    const input = dialog.querySelector(".cmdk-input");
    const list = dialog.querySelector(".cmdk-list");
    let matches = items;
    let active = 0;

    // B2: vẽ danh sách theo từ khóa
    function render() {
        const query = normalizeSearchText(input.value.trim());
        matches = [];
        for (let i = 0; i < items.length; i++) {
            if (query === "" || items[i].key.indexOf(query) !== -1) {
                matches.push(items[i]);
            }
        }
        if (active >= matches.length) {
            active = 0;
        }
        list.textContent = "";
        if (matches.length === 0) {
            const empty = document.createElement("li");
            empty.className = "cmdk-empty";
            empty.textContent = "Không có màn nào khớp.";
            list.appendChild(empty);
            return;
        }
        for (let i = 0; i < matches.length; i++) {
            const li = document.createElement("li");
            li.className = "cmdk-item" + (i === active ? " is-active" : "");
            li.setAttribute("role", "option");
            li.setAttribute("aria-selected", i === active ? "true" : "false");
            li.textContent = matches[i].label;
            li.dataset.index = String(i);
            list.appendChild(li);
        }
    }

    function open() {
        input.value = "";
        active = 0;
        render();
        dialog.showModal();
        input.focus();
    }

    function go(index) {
        if (matches[index]) {
            window.location.href = matches[index].href;
        }
    }

    // B3: phím tắt mở hộp
    document.addEventListener("keydown", function (event) {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
            event.preventDefault();
            if (dialog.open) {
                dialog.close();
            } else {
                open();
            }
        }
    });

    input.addEventListener("input", function () {
        active = 0;
        render();
    });

    // B4: điều hướng bằng bàn phím trong hộp
    input.addEventListener("keydown", function (event) {
        if (event.key === "ArrowDown") {
            event.preventDefault();
            active = (active + 1) % Math.max(matches.length, 1);
            render();
        } else if (event.key === "ArrowUp") {
            event.preventDefault();
            active = (active - 1 + matches.length) % Math.max(matches.length, 1);
            render();
        } else if (event.key === "Enter") {
            event.preventDefault();
            go(active);
        }
    });

    list.addEventListener("click", function (event) {
        const item = event.target.closest(".cmdk-item");
        if (item) {
            go(Number(item.dataset.index));
        }
    });

    // Bấm ra ngoài hộp (vùng nền) thì đóng
    dialog.addEventListener("click", function (event) {
        if (event.target === dialog) {
            dialog.close();
        }
    });
});


// =====================================================================
// LOADING PUMPY CHO MỌI NÚT GỬI FORM — cả trang khách, đăng nhập và khu nội bộ.
// Form nào gửi thật về server (không bị chặn) thì nút vừa bấm hiện Pumpy đẩy tạ cho tới khi trang mới tải xong.
// Bỏ qua: form bị chặn gửi (sai dữ liệu, AJAX tự lo), nút không phải .btn (link-btn), nút có data-no-loading,
// form mở tab mới (target="_blank").
// =====================================================================
document.addEventListener("submit", function (event) {
    // B1: form đã bị chặn (kiểm tra sai / AJAX) → không làm gì
    if (event.defaultPrevented) {
        return;
    }
    const form = event.target;
    if (form.getAttribute("target") === "_blank") {
        return;
    }

    // B2: tìm nút vừa bấm; gửi bằng Enter thì lấy nút submit đầu tiên của form
    let button = event.submitter;
    if (button === null || button === undefined) {
        button = form.querySelector('button[type="submit"], button:not([type])');
    }
    if (button === null || !button.classList.contains("btn") || button.hasAttribute("data-no-loading")) {
        return;
    }

    // B3: bật sau 1 nhịp để trình duyệt kịp gửi name/value của nút (nút disabled sẽ không được gửi)
    setTimeout(function () {
        setButtonLoading(button, true);
    }, 0);
});

// Bấm Quay lại (trình duyệt lấy trang từ bộ nhớ đệm) → trả các nút đang loading về bình thường
window.addEventListener("pageshow", function (event) {
    if (!event.persisted) {
        return;
    }
    const buttons = document.querySelectorAll(".btn.is-loading");
    for (let i = 0; i < buttons.length; i++) {
        setButtonLoading(buttons[i], false);
    }
});


// =====================================================================
// CHỌN NHANH KHOẢNG NGÀY — [data-range-presets data-from="#id" data-to="#id"], nút [data-range="7|30|month"]
// Điền "Từ ngày / Đến ngày" rồi gửi form luôn, đỡ phải chọn lịch 2 lần.
// =====================================================================
document.addEventListener("click", function (event) {
    const chip = event.target.closest("[data-range-presets] [data-range]");
    if (!chip) {
        return;
    }
    const group = chip.closest("[data-range-presets]");
    const fromInput = document.querySelector(group.dataset.from);
    const toInput = document.querySelector(group.dataset.to);
    if (!fromInput || !toInput) {
        return;
    }
    // B1: tính khoảng ngày theo giờ máy
    const today = new Date();
    let start = new Date(today);
    if (chip.dataset.range === "month") {
        start = new Date(today.getFullYear(), today.getMonth(), 1);
    } else {
        start.setDate(today.getDate() - Number(chip.dataset.range) + 1);
    }
    function iso(date) {
        return date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0") + "-" + String(date.getDate()).padStart(2, "0");
    }
    // B2: điền và gửi form (vẫn qua kiểm tra form + loading Pumpy như bấm nút Xem)
    fromInput.value = iso(start);
    toInput.value = iso(today);
    if (fromInput.form && fromInput.form.requestSubmit) {
        fromInput.form.requestSubmit();
    }
});


// =====================================================================
// LỊCH DẠY PT: đánh dấu buổi "Đang diễn ra" và buổi sắp tới "Còn N phút" (trong 60 phút)
// Đọc data-start / data-end (yyyy-MM-ddTHH:mm) trên .pt-schedule-session; cập nhật mỗi 30 giây.
// =====================================================================
document.addEventListener("DOMContentLoaded", function () {
    const sessions = document.querySelectorAll(".pt-schedule-session[data-start]");
    if (!sessions.length) {
        return;
    }
    function update() {
        const now = Date.now();
        for (let i = 0; i < sessions.length; i++) {
            const item = sessions[i];
            const start = new Date(item.dataset.start).getTime();
            const end = new Date(item.dataset.end).getTime();
            let label = "";
            if (now >= start && now < end) {
                label = "Đang diễn ra";
            } else if (start > now && start - now <= 60 * 60 * 1000) {
                label = "Còn " + Math.ceil((start - now) / 60000) + " phút";
            }
            item.classList.toggle("is-live", label === "Đang diễn ra");
            let badge = item.querySelector(".pt-session-when");
            if (!label) {
                if (badge) {
                    badge.remove();
                }
                continue;
            }
            if (!badge) {
                badge = document.createElement("span");
                badge.className = "pt-session-when";
                item.querySelector(".pt-session-time").appendChild(badge); // nằm trong ô giờ, không chiếm một cột của lưới
            }
            badge.textContent = label;
        }
    }
    update();
    setInterval(update, 30000);
});


// =====================================================================
// GIAO DIỆN SÁNG / TỐI (khôi phục 25/10) — công tắc [data-theme-toggle] trên topbar mọi màn nội bộ.
// Tối là mặc định: <html data-theme="dark"> (head.jspf gắn sẵn). Sáng (Arena): <html> không có data-theme.
// Lựa chọn lưu localStorage "gms-theme"; head.jspf đọc lại trước khi vẽ nên không nháy.
// =====================================================================
document.addEventListener("DOMContentLoaded", function () {
    const root = document.documentElement;
    // B0: màn nội bộ chưa có công tắc → tự gắn vào đầu khối bên phải của topbar (trước chuông, tài khoản)
    if (document.body.classList.contains("dock") && !document.querySelector("[data-theme-toggle]")) {
        const topbar = document.querySelector(".topbar");
        if (topbar) {
            const slot = topbar.querySelector(".top-right, .pt-top-actions") || topbar;
            const toggle = document.createElement("button");
            toggle.type = "button";
            toggle.className = "theme-toggle";
            toggle.setAttribute("data-theme-toggle", "");
            toggle.innerHTML = '<span class="theme-toggle__track" aria-hidden="true"><span class="theme-toggle__knob"></span></span>'
                + '<span class="theme-toggle__label">Sáng</span>';
            slot.insertBefore(toggle, slot === topbar ? null : slot.firstChild);
        }
    }
    const buttons = document.querySelectorAll("[data-theme-toggle]");
    if (!buttons.length) {
        return;
    }

    function isDark() {
        return root.getAttribute("data-theme") === "dark";
    }

    // B1: chữ, trạng thái gạt và nhãn đọc màn hình theo chế độ hiện tại (aria-pressed = đang sáng)
    function paint() {
        const dark = isDark();
        buttons.forEach(function (button) {
            button.setAttribute("aria-pressed", dark ? "false" : "true");
            const label = button.querySelector(".theme-toggle__label");
            if (label) {
                label.textContent = dark ? "Tối" : "Sáng";
            }
            button.setAttribute("aria-label", dark ? "Đang ở nền tối. Chuyển sang nền sáng" : "Đang ở nền sáng. Chuyển sang nền tối");
        });
    }

    // B2: bấm → đổi chế độ và ghi nhớ
    buttons.forEach(function (button) {
        button.addEventListener("click", function () {
            const next = isDark() ? "light" : "dark";
            if (next === "dark") {
                root.setAttribute("data-theme", "dark");
            } else {
                root.removeAttribute("data-theme");
            }
            try {
                localStorage.setItem("gms-theme", next);
            } catch (error) {
                // Trình duyệt chặn lưu trữ: vẫn đổi được trong lần xem này
            }
            paint();
        });
    });
    paint();
});


// =====================================================================
// NÂNG CẤP 2026-10-13: biểu đồ màu thương hiệu + đường xu hướng, số đếm lên, Pumpy theo ngữ cảnh
// =====================================================================
document.addEventListener("DOMContentLoaded", function () {
    const calm = prefersReducedMotion() || document.documentElement.classList.contains("gms-lite");

    // B1: biểu đồ cột — đánh dấu cột cao nhất; vẽ đường xu hướng từ các cột vào ô dẫn đầu của bảng điểm
    document.querySelectorAll(".bars").forEach(function (bars) {
        const items = Array.prototype.slice.call(bars.querySelectorAll(".bar"));
        const values = items.map(function (bar) {
            return parseFloat(bar.style.getPropertyValue("--percent")) || 0;
        });
        if (!values.length) {
            return;
        }
        const max = Math.max.apply(null, values);
        if (max > 0) {
            items[values.indexOf(max)].classList.add("is-max");
        }
        const lead = document.querySelector(".scoreboard .stat.is-lead");
        if (!lead || values.length < 2 || lead.querySelector(".stat-spark")) {
            return;
        }
        const width = 200;
        const height = 34;
        const top = Math.max(max, 1);
        const points = values.map(function (value, index) {
            return [index * width / (values.length - 1), height - 3 - (value / top) * (height - 6)];
        });
        const line = points.map(function (point, index) {
            return (index ? "L" : "M") + point[0].toFixed(1) + " " + point[1].toFixed(1);
        }).join(" ");
        const last = points[points.length - 1];
        // Màu vùng dưới đường xu hướng theo chế độ: sáng = xanh cobalt nhạt, tối = aqua như giao diện cũ
        const darkMode = document.documentElement.getAttribute("data-theme") === "dark";
        const sparkColor = darkMode ? "#2BD4DE" : "#2E63B5";
        const sparkOpacity = darkMode ? ".35" : ".18";
        const svg = '<svg class="stat-spark" viewBox="0 0 ' + width + " " + height + '" preserveAspectRatio="none" aria-hidden="true">'
            + '<defs><linearGradient id="gmsSparkFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="' + sparkColor + '" stop-opacity="' + sparkOpacity + '"/><stop offset="1" stop-color="' + sparkColor + '" stop-opacity="0"/></linearGradient></defs>'
            + '<path class="area" d="' + line + " L" + width + " " + height + " L0 " + height + ' Z"/>'
            + '<path class="line" d="' + line + '" vector-effect="non-scaling-stroke"/>'
            + '<circle cx="' + last[0].toFixed(1) + '" cy="' + last[1].toFixed(1) + '" r="3"/></svg>';
        lead.insertAdjacentHTML("beforeend", svg);
    });

    // B2: số liệu đếm lên khi mở trang (giữ nguyên định dạng: dấu chấm hàng nghìn, phần thập phân, đơn vị)
    if (!calm) {
        const targets = document.querySelectorAll(".pt-pulse dd, .pt-summary-stat strong, .pt-summary-money strong, .dock .stat-value:not([data-flip]), .dock .metric-value:not([data-flip])");
        targets.forEach(function (el) {
            if (el.children.length || el.hasAttribute("data-count")) {
                return;
            }
            const text = el.textContent;
            const match = text.match(/\d[\d.]*(,\d+)?/);
            if (!match || /\d:\d/.test(text)) {
                return;
            }
            const raw = match[0];
            const decimals = match[1] ? match[1].length - 1 : 0;
            const target = parseFloat(raw.replace(/\./g, "").replace(",", "."));
            if (!isFinite(target) || target === 0) {
                return;
            }
            const format = function (value) {
                return value.toLocaleString("vi-VN", { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
            };
            const start = performance.now();
            const duration = 900;
            function step(now) {
                const t = Math.min(1, (now - start) / duration);
                const eased = 1 - Math.pow(1 - t, 3);
                el.textContent = text.replace(raw, t < 1 ? format(target * eased) : raw);
                if (t < 1) {
                    requestAnimationFrame(step);
                }
            }
            requestAnimationFrame(step);
        });
    }

    // B3: trạng thái rỗng nói về lịch / buổi tập → Pumpy nằm nghỉ cạnh quả tạ (images/pumpy/relax.svg)
    document.querySelectorAll(".dock .empty-state").forEach(function (box) {
        if (/lịch|buổi|đánh giá|yêu cầu/i.test(box.textContent)) {
            box.classList.add("empty-state--relax");
        }
    });
});
