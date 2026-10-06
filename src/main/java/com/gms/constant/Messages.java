package com.gms.constant;

/**
 * Toàn bộ thông báo hệ thống theo SRS III.5 "System Messages" (MSG01 … MSG105).
 *
 * Cách dùng:
 * - Thông báo không có tham số: dùng thẳng, VD ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY)
 * - Thông báo có %s / %d: dùng String.format, VD String.format(Messages.MSG09_REQUIRED, "Full name")
 *
 * Bản sao phía trình duyệt nằm trong js/common.js (hằng MESSAGES) — sửa câu chữ ở đây thì sửa luôn bên đó.
 * Không tự viết câu thông báo mới trong Service/Servlet nếu SRS đã có mã tương ứng.
 */
public final class Messages {

    // ===== 5.1 Hệ thống, danh sách, quyền truy cập =====

    public static final String MSG01_NO_RESULT = "No results found. Try different keywords or clear some filters.";
    public static final String MSG02_EMPTY_LIST = "No %s yet.";
    public static final String MSG03_NO_DATA_IN_RANGE = "No data in the selected time range.";
    public static final String MSG04_SYSTEM_BUSY = "The system is busy. Please try again in a few minutes.";
    public static final String MSG05_CONNECTION_LOST = "Connection lost. Check your network and try again.";
    public static final String MSG06_SESSION_EXPIRED = "Your session has expired. Please sign in again.";
    public static final String MSG07_NO_PERMISSION = "You do not have permission to access this function.";
    public static final String MSG08_NOT_FOUND = "This %s could not be found.";

    // ===== 5.2 Kiểm tra dữ liệu nhập (dùng chung) =====

    public static final String MSG09_REQUIRED = "%s is required.";
    public static final String MSG10_MAX_LENGTH = "Exceed max length of %d.";
    public static final String MSG11_INVALID_EMAIL = "Invalid email format.";
    public static final String MSG12_EMAIL_USED = "This email is already used by another account.";
    public static final String MSG13_INVALID_PHONE = "Phone number must be 10 digits, start with 0 and use a valid Vietnamese carrier prefix.";
    public static final String MSG14_PHONE_USED = "This phone number already belongs to another profile.";
    public static final String MSG15_DOB_IN_FUTURE = "Date of birth cannot be in the future.";
    public static final String MSG16_DATE_RANGE_REVERSED = "The start date must be on or before the end date.";
    public static final String MSG17_POSITIVE_NUMBER = "Enter a number greater than 0.";
    public static final String MSG18_DATE_TOO_EARLY = "Only dates from %s onwards can be selected.";
    public static final String MSG19_TOO_SHORT = "Be more specific, at least %d characters.";
    public static final String MSG20_INVALID_IMAGE = "Only JPG or PNG images up to %s are accepted.";
    public static final String MSG21_SELECT_REQUIRED = "Please select %s.";
    public static final String MSG22_CONCURRENT_EDIT = "This record was just updated by someone else. Reload before saving.";

    // ===== 5.3 Lưu, xóa, thao tác chung =====

    public static final String MSG23_SAVED = "Changes saved.";
    public static final String MSG24_NOTHING_TO_SAVE = "There is nothing to save.";
    public static final String MSG25_CREATED = "%s added.";
    public static final String MSG26_DELETED = "%s deleted.";
    public static final String MSG27_CONFIRM_DELETE = "Delete %s? This action cannot be undone.";
    public static final String MSG28_UNSAVED_CHANGES = "Your changes have not been saved. Leaving now will discard them.";
    public static final String MSG29_STILL_REFERENCED = "Cannot delete: %s still refers to it. Deactivate it instead.";
    public static final String MSG30_DEACTIVATED = "%s deactivated.";
    public static final String MSG31_REACTIVATED = "%s reactivated.";
    public static final String MSG32_EXPORTED = "%s downloaded.";

    // ===== 5.4 Đăng nhập, mật khẩu, tài khoản cá nhân =====

    public static final String MSG33_WRONG_CREDENTIALS_ATTEMPTS_LEFT = "Incorrect email, phone number or password. You have %d attempts left.";
    public static final String MSG34_WRONG_CREDENTIALS_STAFF = "Incorrect email, phone number or password. Please check again.";
    public static final String MSG35_ACCOUNT_TEMP_LOCKED = "Account locked for %d minutes after too many failed attempts. Try again in %s.";
    public static final String MSG36_LOCK_EXPIRED = "The lock has expired. You can sign in again.";
    public static final String MSG37_ACCOUNT_INACTIVE = "This account cannot be used to sign in. Please contact an administrator.";
    public static final String MSG38_NO_PASSWORD_YET = "This account has no password yet. Please use Forgot password to set your password";
    public static final String MSG39_IDENTIFIER_REQUIRED = "Enter your email or phone number.";
    public static final String MSG40_SIGNED_OUT = "Signed out.";
    public static final String MSG41_EMAIL_NOT_FOUND = "No account found with this email.";
    public static final String MSG42_CODE_SENT = "A code has been sent to %s. It is valid for %d minutes.";
    public static final String MSG43_WRONG_CODE = "Incorrect code. Check again or press Resend code.";
    public static final String MSG44_CODE_EXPIRED = "This code has expired. Press Resend code to get a new one.";
    public static final String MSG45_CODE_RESENT = "A new verification code has been sent.";
    public static final String MSG46_PASSWORD_TOO_SHORT = "Password must be at least %d characters.";
    public static final String MSG47_PASSWORD_MISMATCH = "The passwords do not match.";
    public static final String MSG48_PASSWORD_SAME_AS_OLD = "The new password must differ from the current one.";
    public static final String MSG49_PASSWORD_CHANGED = "Password changed. All your other sessions have been signed out.";
    public static final String MSG50_PHONE_ACCEPTED = "Phone number accepted. Please fill in your registration details.";
    public static final String MSG51_PHONE_HAS_ACCOUNT = "This phone number already has an account. Please sign in or use Forgot password.";
    public static final String MSG52_PHONE_DIFFERENT_EMAIL = "This phone number is registered with a different email (%s). Use that email, or visit the front desk to have it updated.";
    public static final String MSG53_PROFILE_NO_EMAIL = "Your profile has no email on file, so it cannot be activated online. Please visit the front desk to add one.";
    public static final String MSG54_ACCOUNT_CREATED = "Your account has been created. Please sign in.";
    public static final String MSG55_EMAIL_CHANGE_CODE_SENT = "A verification code has been sent to the new email address.";
    public static final String MSG56_EMAIL_CHANGED = "Sign-in email updated. We notified the old address and signed out your other sessions.";
    public static final String MSG57_PHONE_CHANGED = "Phone number updated.";
    public static final String MSG58_AVATAR_CHANGED = "Profile picture updated.";
    public static final String MSG59_OTHER_DEVICES_SIGNED_OUT = "All other devices have been signed out.";
    public static final String MSG60_UPDATED_BY_FRONT_DESK = "This information is updated by the front desk. Please bring your ID to the counter.";
    public static final String MSG61_VERIFICATION_LOCKED = "Too many attempts. Verification for this email is locked for %d minutes. Try again in %s.";

    // ===== 5.5 Số lần còn lại khi nhập / gửi lại mã xác thực (CBR-94) =====

    public static final String MSG62_CODE_ATTEMPTS_LEFT = "You have %d attempt(s) left to enter the code.";
    public static final String MSG63_CODE_LAST_ATTEMPT = "One more wrong entry will lock verification for this email for %d minutes.";
    public static final String MSG64_CODE_RESENDS_LEFT = "You can request %d more code(s) within 60 minutes.";
    public static final String MSG65_CODE_RESEND_WILL_LOCK = "No code requests left. Requesting another code will lock verification for %d minutes.";

    // ===== 5.6 Luật DBR / CBR bổ sung (MSG66–MSG105) =====

    /** DBR-02: mật khẩu phải có cả chữ và số. */
    public static final String MSG66_PASSWORD_RULE = "Password must be %d–%d characters and contain both letters and digits.";

    /** DBR-01: họ tên sai ký tự. */
    public static final String MSG67_FULL_NAME_RULE = "Full name may contain only letters, spaces, apostrophes or hyphens.";

    /** DBR-03: mã xác thực sai định dạng. */
    public static final String MSG68_CODE_FORMAT = "The verification code must be exactly 6 digits.";

    /** DBR-04: tuổi vượt quá 100. */
    public static final String MSG69_DOB_TOO_OLD = "Invalid date of birth (age must not exceed %d).";

    /** CBR-94: bấm "Gửi lại mã" khi chưa đủ 60 giây kể từ lần gửi trước. */
    public static final String MSG70_CODE_RESEND_TOO_SOON = "Please wait %d more seconds before resending the code.";

    /** UC-04: đổi mật khẩu nhưng mật khẩu hiện tại nhập sai. */
    public static final String MSG71_CURRENT_PASSWORD_WRONG = "The current password is incorrect.";

    /** DBR-01: họ tên ngoài độ dài cho phép. */
    public static final String MSG72_FULL_NAME_LENGTH = "Full name must be %d–%d characters.";

    /** CBR-100: bài viết đã gỡ hoặc không tồn tại trên trang công khai. */
    public static final String MSG73_POST_UNAVAILABLE = "This post is no longer available.";

    /** CBR-113: chọn vai trò làm việc khi tài khoản có nhiều vai trò. */
    public static final String MSG74_CHOOSE_ROLE = "Choose the role you want to work in.";

    /** CBR-113: đổi vai trò thành công. */
    public static final String MSG75_ROLE_SWITCHED = "You are now working as %s.";

    /** CBR-115: nhân viên không được thao tác trên hồ sơ khách hàng của chính mình. */
    public static final String MSG76_STAFF_OWN_CUSTOMER_PROFILE = "You cannot perform this action on your own customer profile. Please ask another colleague.";

    /** CBR-84, P-12: nộp đơn nghỉ phép quá hạn cut-off. */
    public static final String MSG77_LEAVE_SUBMISSION_DEADLINE = "Submission deadline passed. Leave on %s had to be submitted before %s on the previous day.";

    /** CBR-83: nộp đơn nghỉ phép khi vẫn còn lịch tập PT. */
    public static final String MSG78_LEAVE_BLOCKED_BY_SESSIONS = "You still have %d scheduled session(s) in this period. Reschedule, transfer or cancel them before submitting the request.";

    /** BR-14, CBR-88, CBR-90: duyệt nghỉ phép làm thiếu nhân sự tối thiểu của ca. */
    public static final String MSG79_LEAVE_BREAKS_MIN_STAFFING = "Cannot approve: the %s shift on %s would have fewer than %d %s(s) on duty.";

    /** CBR-85, P-20: đơn nghỉ phép tự động bị từ chối do quá hạn duyệt. */
    public static final String MSG80_LEAVE_AUTO_REJECTED = "Your leave request for %s was rejected: Approval deadline passed.";

    /** CBR-59, BR-37: bán gói PT cho khách chưa có gói hội viên còn hạn. */
    public static final String MSG81_PT_REQUIRES_MEMBERSHIP = "%s needs a valid membership package before a PT package can be sold.";

    /** CBR-13, CBR-14, CBR-15, P-05: khách chưa đủ tuổi tối thiểu dùng PT. */
    public static final String MSG82_PT_MIN_AGE = "PT services are available only to customers aged %d or older.";

    /** BR-27: check-in thất bại vì không có gói tập hoặc vé ngày hợp lệ hôm nay. */
    public static final String MSG83_CHECKIN_NO_PACKAGE = "Check-in refused: the customer has no valid membership package or day pass today.";

    /** CBR-36, BR-29: vé ngày đã được sử dụng. */
    public static final String MSG84_DAY_PASS_USED = "This day pass has already been used.";

    /** CBR-73, CBR-74, CBR-76: voucher không hợp lệ. */
    public static final String MSG85_VOUCHER_INVALID = "This voucher cannot be applied: %s.";

    /** CBR-75: thay đổi voucher khi thanh toán đang xử lý. */
    public static final String MSG86_VOUCHER_PAYMENT_PENDING = "The voucher cannot be changed while a payment is in progress.";

    /** CBR-78: đóng ca khi còn đơn hàng quầy đang Pending. */
    public static final String MSG87_CLOSE_SHIFT_PENDING_ORDERS = "Cannot close the shift: %d counter order(s) are still Pending.";

    /** BR-06, BR-07: khóa, hạ quyền hoặc cho thôi việc Admin hoạt động cuối cùng. */
    public static final String MSG88_LAST_ACTIVE_ADMIN = "This is the last active Admin account. It cannot be locked, demoted or set to Resigned.";

    /** CBR-87: đổi vị trí công việc khi còn công việc chưa hoàn thành. */
    public static final String MSG89_POSITION_CHANGE_UNFINISHED = "The position cannot be changed while unfinished work remains: %s.";

    /** BR-73, P-08: gói PT đã dùng hết lượt đổi PT. */
    public static final String MSG90_SWAP_LIMIT_REACHED = "This PT package has already used all %d swaps.";

    /** BR-70, BR-76: buổi tập đã bắt đầu hoặc không còn ở trạng thái Scheduled. */
    public static final String MSG91_SESSION_CANNOT_CHANGE = "This session has already started or is no longer scheduled, so it cannot be changed.";

    /** CBR-61, CBR-60: ngày tập không nằm trong thời hạn gói hội viên. */
    public static final String MSG92_NO_MEMBERSHIP_ON_DATE = "The customer has no valid membership package on %s. Please choose another date.";

    /** CBR-108: nhân viên xem trường thông tin nhân sự trong Account Settings. */
    public static final String MSG93_UPDATED_BY_MANAGER = "This information is updated by the Manager.";

    /** CBR-109: nhân viên xem vị trí hoặc trạng thái tài khoản trong Account Settings. */
    public static final String MSG94_ONLY_ADMIN_CAN_CHANGE = "Only the Admin can change this information.";

    /** UC-03, CBR-93: đặt lại mật khẩu qua Quên mật khẩu hoàn tất. */
    public static final String MSG95_PASSWORD_RESET_COMPLETED = "Your password has been reset and all sessions have been signed out. Please sign in.";

    /** CBR-16, BR-16: gửi yêu cầu xóa tài khoản cho Quản lý phê duyệt. */
    public static final String MSG96_DELETION_REQUEST_SENT = "Deletion request sent to the Manager for approval.";

    /** CBR-11: hủy đơn hàng tại quầy ngoài ca mở tạo đơn. */
    public static final String MSG97_CANCEL_ORDER_OUTSIDE_SHIFT = "This order can only be cancelled during the open shift that created it.";

    /** CBR-70: thanh toán đơn online quá hạn. */
    public static final String MSG98_ONLINE_ORDER_DEADLINE_PASSED = "The payment deadline has passed and the order was cancelled. Please create a new order.";

    /** BR-21, BR-23, BR-24: từ chối bảo lưu gói tập. */
    public static final String MSG99_FREEZE_REFUSED = "This package cannot be frozen: %s.";

    /** CBR-08, DBR-13: thời hạn bảo lưu vượt quá quy định. */
    public static final String MSG100_FREEZE_LIMIT = "A freeze can last at most %d month(s) and must end by %s.";

    /** CBR-16: hồ sơ đã có yêu cầu xóa đang chờ duyệt. */
    public static final String MSG101_PROFILE_PENDING_DELETION = "This profile already has a pending deletion request.";

    /** DBR-65: thêm hồ sơ nhân viên cho người đã có hồ sơ nhân viên đang hoạt động. */
    public static final String MSG102_STAFF_ALREADY_EXISTS = "This person already has an active staff record.";

    /** CBR-53, DBR-36: slot đã bị người khác chọn trước. */
    public static final String MSG103_SLOT_ALREADY_TAKEN = "This slot has just been taken. Please choose another slot.";

    /** DBR-33: slot trùng với slot đã có của PT. */
    public static final String MSG104_SLOT_OVERLAPS = "This slot overlaps an existing slot (%s–%s).";

    /** BR-54, BR-69, BR-70: thao tác ngoài khung giờ cho phép. */
    public static final String MSG105_ACTION_OUTSIDE_WINDOW = "This action is outside the allowed time window.";

    /** CBR-116: PT chưa có buổi đã chốt của khách trong tháng. */
    public static final String MSG106_PT_RATING_NOT_ELIGIBLE = "You cannot rate this PT without a CheckedIn or Missed session this month.";

    /** DBR-67: chấm lại cùng PT cùng tháng. */
    public static final String MSG107_PT_RATING_UPDATED = "Your PT rating has been updated.";

    private Messages() {
    }
}
