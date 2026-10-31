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

    // EN: No results found. Try different keywords or clear some filters.
    public static final String MSG01_NO_RESULT = "Không tìm thấy kết quả. Hãy thử từ khóa khác hoặc bỏ bớt bộ lọc.";
    // EN: No %s yet.
    public static final String MSG02_EMPTY_LIST = "Chưa có %s.";
    // EN: No data in the selected time range.
    public static final String MSG03_NO_DATA_IN_RANGE = "Không có dữ liệu trong khoảng thời gian đã chọn.";
    // EN: The system is busy. Please try again in a few minutes.
    public static final String MSG04_SYSTEM_BUSY = "Hệ thống đang bận. Vui lòng thử lại sau ít phút.";
    // EN: Connection lost. Check your network and try again.
    public static final String MSG05_CONNECTION_LOST = "Mất kết nối. Hãy kiểm tra mạng và thử lại.";
    // EN: Your session has expired. Please sign in again.
    public static final String MSG06_SESSION_EXPIRED = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    // EN: You do not have permission to access this function.
    public static final String MSG07_NO_PERMISSION = "Bạn không có quyền truy cập chức năng này.";
    // EN: This %s could not be found.
    public static final String MSG08_NOT_FOUND = "Không tìm thấy %s.";

    // ===== 5.2 Kiểm tra dữ liệu nhập (dùng chung) =====

    // EN: %s is required.
    public static final String MSG09_REQUIRED = "%s là bắt buộc.";
    // EN: Exceed max length of %d.
    public static final String MSG10_MAX_LENGTH = "Vượt quá độ dài tối đa %d ký tự.";
    // EN: Invalid email format.
    public static final String MSG11_INVALID_EMAIL = "Email không đúng định dạng.";
    // EN: This email is already used by another account.
    public static final String MSG12_EMAIL_USED = "Email này đã được tài khoản khác sử dụng.";
    // EN: Phone number must be 10 digits, start with 0 and use a valid Vietnamese carrier prefix.
    public static final String MSG13_INVALID_PHONE = "Số điện thoại phải gồm 10 chữ số, bắt đầu bằng 0 và có đầu số nhà mạng Việt Nam hợp lệ.";
    // EN: This phone number already belongs to another profile.
    public static final String MSG14_PHONE_USED = "Số điện thoại này đã thuộc về một hồ sơ khác.";
    // EN: Date of birth cannot be in the future.
    public static final String MSG15_DOB_IN_FUTURE = "Ngày sinh không được ở tương lai.";
    // EN: The start date must be on or before the end date.
    public static final String MSG16_DATE_RANGE_REVERSED = "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.";
    // EN: Enter a number greater than 0.
    public static final String MSG17_POSITIVE_NUMBER = "Nhập một số lớn hơn 0.";
    // EN: Only dates from %s onwards can be selected.
    public static final String MSG18_DATE_TOO_EARLY = "Chỉ được chọn ngày từ %s trở đi.";
    // EN: Be more specific, at least %d characters.
    public static final String MSG19_TOO_SHORT = "Vui lòng ghi cụ thể hơn, tối thiểu %d ký tự.";
    // EN: Only JPG or PNG images up to %s are accepted.
    public static final String MSG20_INVALID_IMAGE = "Chỉ chấp nhận ảnh JPG hoặc PNG, tối đa %s.";
    // EN: Please select %s.
    public static final String MSG21_SELECT_REQUIRED = "Vui lòng chọn %s.";
    // EN: This record was just updated by someone else. Reload before saving.
    public static final String MSG22_CONCURRENT_EDIT = "Bản ghi này vừa được người khác cập nhật. Hãy tải lại trước khi lưu.";

    // ===== 5.3 Lưu, xóa, thao tác chung =====

    // EN: Changes saved.
    public static final String MSG23_SAVED = "Đã lưu thay đổi.";
    // EN: There is nothing to save.
    public static final String MSG24_NOTHING_TO_SAVE = "Không có thay đổi nào để lưu.";
    // EN: %s added.
    public static final String MSG25_CREATED = "Đã thêm %s.";
    // EN: %s deleted.
    public static final String MSG26_DELETED = "Đã xóa %s.";
    // EN: Delete %s? This action cannot be undone.
    public static final String MSG27_CONFIRM_DELETE = "Xóa %s? Thao tác này không thể hoàn tác.";
    // EN: Your changes have not been saved. Leaving now will discard them.
    public static final String MSG28_UNSAVED_CHANGES = "Thay đổi của bạn chưa được lưu. Rời trang lúc này sẽ mất các thay đổi.";
    // EN: Cannot delete: %s still refers to it. Deactivate it instead.
    public static final String MSG29_STILL_REFERENCED = "Không thể xóa: %s vẫn đang tham chiếu tới mục này. Hãy ngừng hoạt động thay vì xóa.";
    // EN: %s deactivated.
    public static final String MSG30_DEACTIVATED = "Đã ngừng hoạt động %s.";
    // EN: %s reactivated.
    public static final String MSG31_REACTIVATED = "Đã kích hoạt lại %s.";
    // EN: %s downloaded.
    public static final String MSG32_EXPORTED = "Đã tải xuống %s.";

    // ===== 5.4 Đăng nhập, mật khẩu, tài khoản cá nhân =====

    // EN: Incorrect email, phone number or password. You have %d attempts left.
    public static final String MSG33_WRONG_CREDENTIALS_ATTEMPTS_LEFT = "Email, số điện thoại hoặc mật khẩu không đúng. Bạn còn %d lần thử.";
    // EN: Incorrect email, phone number or password. Please check again.
    public static final String MSG34_WRONG_CREDENTIALS_STAFF = "Email, số điện thoại hoặc mật khẩu không đúng. Vui lòng kiểm tra lại.";
    // EN: Account locked for %d minutes after too many failed attempts. Try again in %s.
    public static final String MSG35_ACCOUNT_TEMP_LOCKED = "Tài khoản bị khóa %d phút do nhập sai quá nhiều lần. Thử lại sau %s.";
    // EN: The lock has expired. You can sign in again.
    public static final String MSG36_LOCK_EXPIRED = "Đã hết thời gian khóa. Bạn có thể đăng nhập lại.";
    // EN: This account cannot be used to sign in. Please contact an administrator.
    public static final String MSG37_ACCOUNT_INACTIVE = "Tài khoản này không thể đăng nhập. Vui lòng liên hệ quản trị viên.";
    // EN: Enter your email or phone number.
    public static final String MSG39_IDENTIFIER_REQUIRED = "Nhập email hoặc số điện thoại của bạn.";
    // EN: Signed out.
    public static final String MSG40_SIGNED_OUT = "Đã đăng xuất.";
    // EN: No account found with this email.
    public static final String MSG41_EMAIL_NOT_FOUND = "Không tìm thấy tài khoản với email này.";
    // EN: A code has been sent to %s. It is valid for %d minutes.
    public static final String MSG42_CODE_SENT = "Mã đã được gửi tới %s. Mã có hiệu lực trong %d phút.";
    // EN: Incorrect code. Check again or press Resend code.
    public static final String MSG43_WRONG_CODE = "Mã không đúng. Hãy kiểm tra lại hoặc bấm Gửi lại mã.";
    // EN: This code has expired. Press Resend code to get a new one.
    public static final String MSG44_CODE_EXPIRED = "Mã đã hết hạn. Bấm Gửi lại mã để nhận mã mới.";
    // EN: A new verification code has been sent.
    public static final String MSG45_CODE_RESENT = "Đã gửi mã xác thực mới.";
    // EN: Password must be at least %d characters.
    public static final String MSG46_PASSWORD_TOO_SHORT = "Mật khẩu phải có ít nhất %d ký tự.";
    // EN: The passwords do not match.
    public static final String MSG47_PASSWORD_MISMATCH = "Mật khẩu nhập lại không khớp.";
    // EN: The new password must differ from the current one.
    public static final String MSG48_PASSWORD_SAME_AS_OLD = "Mật khẩu mới phải khác mật khẩu hiện tại.";
    // EN: Password changed. All your other sessions have been signed out.
    public static final String MSG49_PASSWORD_CHANGED = "Đã đổi mật khẩu. Các phiên đăng nhập khác của bạn đã bị đăng xuất.";
    // EN: Phone number accepted. Please fill in your registration details.
    public static final String MSG50_PHONE_ACCEPTED = "Số điện thoại hợp lệ. Vui lòng điền thông tin đăng ký.";
    // EN: This phone number already has an account. Please sign in or use Forgot password.
    public static final String MSG51_PHONE_HAS_ACCOUNT = "Số điện thoại này đã có tài khoản. Vui lòng đăng nhập hoặc dùng Quên mật khẩu.";
    // EN: This phone number is registered with a different email (%s). Use that email, or visit the front desk to have it updated.
    public static final String MSG52_PHONE_DIFFERENT_EMAIL = "Số điện thoại này đã đăng ký với email khác (%s). Hãy dùng email đó, hoặc đến quầy lễ tân để cập nhật.";
    // EN: Your profile has no email on file, so it cannot be activated online. Please visit the front desk to add one.
    public static final String MSG53_PROFILE_NO_EMAIL = "Hồ sơ của bạn chưa có email nên không thể kích hoạt trực tuyến. Vui lòng đến quầy lễ tân để bổ sung.";
    // EN: Your account has been created. Please sign in.
    public static final String MSG54_ACCOUNT_CREATED = "Đã tạo tài khoản. Vui lòng đăng nhập.";
    // EN: A verification code has been sent to the new email address.
    public static final String MSG55_EMAIL_CHANGE_CODE_SENT = "Mã xác thực đã được gửi tới địa chỉ email mới.";
    // EN: Sign-in email updated. We notified the old address and signed out your other sessions.
    public static final String MSG56_EMAIL_CHANGED = "Đã cập nhật email đăng nhập. Chúng tôi đã thông báo tới email cũ và đăng xuất các phiên khác của bạn.";
    // EN: Phone number updated.
    public static final String MSG57_PHONE_CHANGED = "Đã cập nhật số điện thoại.";
    // EN: Profile picture updated.
    public static final String MSG58_AVATAR_CHANGED = "Đã cập nhật ảnh đại diện.";
    // EN: Profile picture removed.
    public static final String MSG58_AVATAR_REMOVED = "Đã xóa ảnh đại diện.";
    // EN: All other devices have been signed out.
    public static final String MSG59_OTHER_DEVICES_SIGNED_OUT = "Đã đăng xuất khỏi tất cả thiết bị khác.";
    // EN: This information is updated by the front desk. Please bring your ID to the counter.
    public static final String MSG60_UPDATED_BY_FRONT_DESK = "Thông tin này do lễ tân cập nhật. Vui lòng mang giấy tờ tùy thân đến quầy.";
    // EN: Too many attempts. Verification for this email is locked for %d minutes. Try again in %s.
    public static final String MSG61_VERIFICATION_LOCKED = "Thử quá nhiều lần. Xác thực cho email này bị khóa %d phút. Thử lại sau %s.";

    // ===== 5.5 Số lần còn lại khi nhập / gửi lại mã xác thực (CBR-94) =====

    // EN: You have %d attempt(s) left to enter the code.
    public static final String MSG62_CODE_ATTEMPTS_LEFT = "Bạn còn %d lần nhập mã.";
    // EN: One more wrong entry will lock verification for this email for %d minutes.
    public static final String MSG63_CODE_LAST_ATTEMPT = "Nhập sai thêm một lần nữa sẽ khóa xác thực cho email này trong %d phút.";
    // EN: You can request %d more code(s) within 60 minutes.
    public static final String MSG64_CODE_RESENDS_LEFT = "Bạn còn có thể yêu cầu %d mã nữa trong 60 phút.";
    // EN: No code requests left. Requesting another code will lock verification for %d minutes.
    public static final String MSG65_CODE_RESEND_WILL_LOCK = "Đã hết lượt yêu cầu mã. Yêu cầu thêm sẽ khóa xác thực trong %d phút.";

    // ===== 5.6 Luật DBR / CBR bổ sung (MSG66–MSG105) =====

    /** DBR-02: mật khẩu phải có cả chữ và số. */
    // EN: Password must be %d–%d characters and contain both letters and digits.
    public static final String MSG66_PASSWORD_RULE = "Mật khẩu phải dài %d–%d ký tự và có cả chữ lẫn số.";

    /** DBR-01: họ tên sai ký tự. */
    // EN: Full name may contain only letters, spaces, apostrophes or hyphens.
    public static final String MSG67_FULL_NAME_RULE = "Họ tên chỉ được chứa chữ cái, khoảng trắng, dấu nháy đơn hoặc dấu gạch nối.";

    /** DBR-03: mã xác thực sai định dạng. */
    // EN: The verification code must be exactly 6 digits.
    public static final String MSG68_CODE_FORMAT = "Mã xác thực phải gồm đúng 6 chữ số.";

    /** DBR-04: tuổi vượt quá 100. */
    // EN: Invalid date of birth (age must not exceed %d).
    public static final String MSG69_DOB_TOO_OLD = "Ngày sinh không hợp lệ (tuổi không được vượt quá %d).";

    /** CBR-94: bấm "Gửi lại mã" khi chưa đủ 60 giây kể từ lần gửi trước. */
    // EN: Please wait %d more seconds before resending the code.
    public static final String MSG70_CODE_RESEND_TOO_SOON = "Vui lòng chờ thêm %d giây trước khi gửi lại mã.";

    /** UC-04: đổi mật khẩu nhưng mật khẩu hiện tại nhập sai. */
    // EN: The current password is incorrect.
    public static final String MSG71_CURRENT_PASSWORD_WRONG = "Mật khẩu hiện tại không đúng.";

    /** DBR-01: họ tên ngoài độ dài cho phép. */
    // EN: Full name must be %d–%d characters.
    public static final String MSG72_FULL_NAME_LENGTH = "Họ tên phải dài %d–%d ký tự.";

    /** CBR-100: bài viết đã gỡ hoặc không tồn tại trên trang công khai. */
    // EN: This post is no longer available.
    public static final String MSG73_POST_UNAVAILABLE = "Bài viết này không còn khả dụng.";

    /** CBR-113: chọn vai trò làm việc khi tài khoản có nhiều vai trò. */
    // EN: Choose the role you want to work in.
    public static final String MSG74_CHOOSE_ROLE = "Chọn vai trò bạn muốn làm việc.";

    /** CBR-113: đổi vai trò thành công. */
    // EN: You are now working as %s.
    public static final String MSG75_ROLE_SWITCHED = "Bạn đang làm việc với vai trò %s.";

    /** CBR-115: nhân viên không được thao tác trên hồ sơ khách hàng của chính mình. */
    // EN: You cannot perform this action on your own customer profile. Please ask another colleague.
    public static final String MSG76_STAFF_OWN_CUSTOMER_PROFILE = "Bạn không thể thao tác trên hồ sơ khách hàng của chính mình. Vui lòng nhờ đồng nghiệp khác.";

    /** CBR-84, P-12: nộp đơn nghỉ phép quá hạn cut-off. */
    // EN: Submission deadline passed. Leave on %s had to be submitted before %s on the previous day.
    public static final String MSG77_LEAVE_SUBMISSION_DEADLINE = "Đã quá hạn nộp. Đơn nghỉ ngày %s phải nộp trước %s của ngày hôm trước.";

    /** CBR-83: nộp đơn nghỉ phép khi vẫn còn lịch tập PT. */
    // EN: You still have %d scheduled session(s) in this period. Reschedule, transfer or cancel them before submitting the request.
    public static final String MSG78_LEAVE_BLOCKED_BY_SESSIONS = "Bạn còn %d buổi dạy trùng lịch nghỉ. Xem lịch và liên hệ quản lý để giải quyết trước khi gửi đơn.";

    /** BR-14, CBR-88, CBR-90: duyệt nghỉ phép làm thiếu nhân sự tối thiểu của ca. */
    // EN: Cannot approve: the %s shift on %s would have fewer than %d %s(s) on duty.
    public static final String MSG79_LEAVE_BREAKS_MIN_STAFFING = "Không thể duyệt: ca %s ngày %s sẽ có ít hơn %d %s trực.";

    /** CBR-59, BR-37: bán gói PT cho khách chưa có gói hội viên còn hạn. */
    // EN: %s needs a valid membership package before a PT package can be sold.
    public static final String MSG81_PT_REQUIRES_MEMBERSHIP = "%s cần có gói hội viên còn hiệu lực trước khi mua gói PT.";

    /** CBR-13, CBR-14, CBR-15, P-05: khách chưa đủ tuổi tối thiểu dùng PT. */
    // EN: PT services are available only to customers aged %d or older.
    public static final String MSG82_PT_MIN_AGE = "Dịch vụ PT chỉ dành cho khách từ %d tuổi trở lên.";

    /** BR-27: check-in thất bại vì không có gói tập hoặc vé ngày hợp lệ hôm nay. */
    // EN: Check-in refused: the customer has no valid membership package or day pass today.
    public static final String MSG83_CHECKIN_NO_PACKAGE = "Từ chối check-in: hôm nay khách không có gói hội viên hoặc vé ngày hợp lệ.";

    /** CBR-36, BR-29: vé ngày đã được sử dụng. */
    // EN: This day pass has already been used.
    public static final String MSG84_DAY_PASS_USED = "Vé ngày này đã được sử dụng.";

    /** CBR-73, CBR-74, CBR-76: voucher không hợp lệ. */
    // EN: This voucher cannot be applied: %s.
    public static final String MSG85_VOUCHER_INVALID = "Không thể áp dụng voucher này: %s.";

    /** CBR-75: thay đổi voucher khi thanh toán đang xử lý. */
    // EN: The voucher cannot be changed while a payment is in progress.
    public static final String MSG86_VOUCHER_PAYMENT_PENDING = "Không thể đổi voucher khi đang xử lý thanh toán.";

    /** CBR-78: đóng ca khi còn đơn hàng quầy đang Pending. */
    // EN: Cannot close the shift: %d counter order(s) are still Pending.
    public static final String MSG87_CLOSE_SHIFT_PENDING_ORDERS = "Không thể đóng ca: còn %d đơn tại quầy đang chờ thanh toán.";

    /** BR-06, BR-07: khóa, hạ quyền hoặc cho thôi việc Admin hoạt động cuối cùng. */
    // EN: This is the last active Admin account. It cannot be locked, demoted or set to Resigned.
    public static final String MSG88_LAST_ACTIVE_ADMIN = "Đây là tài khoản Admin hoạt động cuối cùng. Không thể khóa, hạ quyền hoặc chuyển sang Đã nghỉ việc.";

    /** CBR-87: đổi vị trí công việc khi còn công việc chưa hoàn thành. */
    // EN: The position cannot be changed while unfinished work remains: %s.
    public static final String MSG89_POSITION_CHANGE_UNFINISHED = "Không thể đổi vị trí khi còn công việc chưa hoàn thành: %s.";

    /** BR-73, P-08: gói PT đã dùng hết lượt đổi PT. */
    // EN: This PT package has already used all %d swaps.
    public static final String MSG90_SWAP_LIMIT_REACHED = "Gói PT này đã dùng hết %d lượt đổi.";

    /** BR-70, BR-76: buổi tập đã bắt đầu hoặc không còn ở trạng thái Scheduled. */
    // EN: This session has already started or is no longer scheduled, so it cannot be changed.
    public static final String MSG91_SESSION_CANNOT_CHANGE = "Buổi tập đã bắt đầu hoặc không còn ở trạng thái đã xếp lịch, nên không thể thay đổi.";

    /** CBR-61, CBR-60: ngày tập không nằm trong thời hạn gói hội viên. */
    // EN: The customer has no valid membership package on %s. Please choose another date.
    public static final String MSG92_NO_MEMBERSHIP_ON_DATE = "Khách không có gói hội viên hợp lệ vào ngày %s. Vui lòng chọn ngày khác.";

    /** CBR-108: nhân viên xem trường thông tin nhân sự trong Account Settings. */
    // EN: This information is updated by the Manager.
    public static final String MSG93_UPDATED_BY_MANAGER = "Thông tin này do Quản lý cập nhật.";

    /** CBR-109: nhân viên xem vị trí hoặc trạng thái tài khoản trong Account Settings. */
    // EN: Only the Admin can change this information.
    public static final String MSG94_ONLY_ADMIN_CAN_CHANGE = "Chỉ Admin mới có thể thay đổi thông tin này.";

    /** UC-03, CBR-93: đặt lại mật khẩu qua Quên mật khẩu hoàn tất. */
    // EN: Your password has been reset and all sessions have been signed out. Please sign in.
    public static final String MSG95_PASSWORD_RESET_COMPLETED = "Đã đặt lại mật khẩu và đăng xuất mọi phiên. Vui lòng đăng nhập.";

    /** CBR-16, BR-16: gửi yêu cầu xóa tài khoản cho Quản lý phê duyệt. */
    // EN: Deletion request sent to the Manager for approval.
    public static final String MSG96_DELETION_REQUEST_SENT = "Đã gửi yêu cầu xóa tới Quản lý để duyệt.";

    /** CBR-11: hủy đơn hàng tại quầy ngoài ca mở tạo đơn. */
    // EN: This order can only be cancelled during the open shift that created it.
    public static final String MSG97_CANCEL_ORDER_OUTSIDE_SHIFT = "Chỉ có thể hủy đơn này trong ca đang mở đã tạo đơn.";

    /** CBR-70: thanh toán đơn online quá hạn. */
    // EN: The payment deadline has passed and the order was cancelled. Please create a new order.
    public static final String MSG98_ONLINE_ORDER_DEADLINE_PASSED = "Đã quá hạn thanh toán và đơn đã bị hủy. Vui lòng tạo đơn mới.";

    /** BR-21, BR-23, BR-24: từ chối bảo lưu gói tập. */
    // EN: This package cannot be frozen: %s.
    public static final String MSG99_FREEZE_REFUSED = "Không thể bảo lưu gói này: %s.";

    /** CBR-08, DBR-13: thời hạn bảo lưu vượt quá quy định. */
    // EN: A freeze can last at most %d month(s) and must end by %s.
    public static final String MSG100_FREEZE_LIMIT = "Bảo lưu tối đa %d tháng và phải kết thúc trước ngày %s.";

    /** CBR-16: hồ sơ đã có yêu cầu xóa đang chờ duyệt. */
    // EN: This profile already has a pending deletion request.
    public static final String MSG101_PROFILE_PENDING_DELETION = "Hồ sơ này đã có yêu cầu xóa đang chờ duyệt.";

    /** DBR-65: thêm hồ sơ nhân viên cho người đã có hồ sơ nhân viên đang hoạt động. */
    // EN: This person already has an active staff record.
    public static final String MSG102_STAFF_ALREADY_EXISTS = "Người này đã có hồ sơ nhân viên đang hoạt động.";

    /** CBR-53, DBR-36: slot đã bị người khác chọn trước. */
    // EN: This slot has just been taken. Please choose another slot.
    public static final String MSG103_SLOT_ALREADY_TAKEN = "Khung giờ này vừa được người khác chọn. Vui lòng chọn khung giờ khác.";

    /** DBR-33: slot trùng với slot đã có của PT. */
    // EN: This slot overlaps an existing slot (%s–%s).
    public static final String MSG104_SLOT_OVERLAPS = "Khung giờ này trùng với khung giờ đã có (%s–%s).";

    /** BR-54, BR-69, BR-70: thao tác ngoài khung giờ cho phép. */
    // EN: This action is outside the allowed time window.
    public static final String MSG105_ACTION_OUTSIDE_WINDOW = "Thao tác này nằm ngoài khung thời gian cho phép.";

    /** CBR-116: PT chưa có buổi đã chốt của khách trong tháng. */
    // EN: You cannot rate this PT without a CheckedIn or Missed session this month.
    public static final String MSG106_PT_RATING_NOT_ELIGIBLE = "Bạn chưa thể đánh giá PT này vì tháng này chưa có buổi Đã check-in hoặc Vắng.";

    /** DBR-67: chấm lại cùng PT cùng tháng. */
    // EN: Your PT rating has been updated.
    public static final String MSG107_PT_RATING_UPDATED = "Đã cập nhật đánh giá PT của bạn.";

    /** Đơn giữ chỗ tại quầy không còn hiệu lực (đã thu tiền / hủy / quá hạn giữ). */
    // EN: This order no longer holds a slot (paid, cancelled, or expired after %d minutes).
    public static final String MSG108_HOLD_NO_LONGER_VALID =
            "Đơn này không còn giữ chỗ (đã thu tiền, đã hủy hoặc quá %d phút). Bán lại từ đầu.";

    private Messages() {
    }
}
