package com.gms.constant;

/**
 * Toàn bộ thông báo hệ thống theo SRS III.5 "System Messages" (MSG01 … MSG75), đã dịch sang tiếng Việt (GBR-01).
 *
 * Cách dùng:
 * - Thông báo không có tham số: dùng thẳng, VD ServiceResult.fail(Messages.MSG04_SYSTEM_BUSY)
 * - Thông báo có %s / %d: dùng String.format, VD String.format(Messages.MSG09_REQUIRED, "Họ tên")
 *
 * Bản sao phía trình duyệt nằm trong js/common.js (hằng MESSAGES) — sửa câu chữ ở đây thì sửa luôn bên đó.
 * Không tự viết câu thông báo mới trong Service/Servlet nếu SRS đã có mã tương ứng.
 */
public final class Messages {

    // ===== 5.1 Hệ thống, danh sách, quyền truy cập =====

    public static final String MSG01_NO_RESULT = "Không tìm thấy kết quả. Hãy thử từ khóa khác hoặc bỏ bớt bộ lọc.";
    public static final String MSG02_EMPTY_LIST = "Chưa có %s nào.";
    public static final String MSG03_NO_DATA_IN_RANGE = "Không có dữ liệu trong khoảng thời gian đã chọn.";
    public static final String MSG04_SYSTEM_BUSY = "Hệ thống đang bận. Vui lòng thử lại sau ít phút.";
    public static final String MSG05_CONNECTION_LOST = "Mất kết nối. Hãy kiểm tra mạng và thử lại.";
    public static final String MSG06_SESSION_EXPIRED = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.";
    public static final String MSG07_NO_PERMISSION = "Bạn không có quyền truy cập chức năng này.";
    public static final String MSG08_NOT_FOUND = "Không tìm thấy %s này.";

    // ===== 5.2 Kiểm tra dữ liệu nhập (dùng chung) =====

    public static final String MSG09_REQUIRED = "%s là bắt buộc.";
    public static final String MSG10_MAX_LENGTH = "Vượt quá độ dài tối đa %d ký tự.";
    public static final String MSG11_INVALID_EMAIL = "Email không đúng định dạng.";
    public static final String MSG12_EMAIL_USED = "Email này đã được dùng cho tài khoản khác.";
    public static final String MSG13_INVALID_PHONE = "Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.";
    public static final String MSG14_PHONE_USED = "Số điện thoại này đã thuộc về một hồ sơ khác.";
    public static final String MSG15_DOB_IN_FUTURE = "Ngày sinh không được ở tương lai.";
    public static final String MSG16_DATE_RANGE_REVERSED = "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.";
    public static final String MSG17_POSITIVE_NUMBER = "Nhập một số lớn hơn 0.";
    public static final String MSG18_DATE_TOO_EARLY = "Chỉ được chọn ngày từ %s trở đi.";
    public static final String MSG19_TOO_SHORT = "Vui lòng ghi cụ thể hơn, tối thiểu %d ký tự.";
    public static final String MSG20_INVALID_IMAGE = "Chỉ chấp nhận ảnh JPG hoặc PNG, tối đa %s.";
    public static final String MSG21_SELECT_REQUIRED = "Vui lòng chọn %s.";
    public static final String MSG22_CONCURRENT_EDIT = "Bản ghi này vừa được người khác cập nhật. Hãy tải lại trang trước khi lưu.";

    // ===== 5.3 Lưu, xóa, thao tác chung =====

    public static final String MSG23_SAVED = "Đã lưu thay đổi.";
    public static final String MSG24_NOTHING_TO_SAVE = "Không có thay đổi nào để lưu.";
    public static final String MSG25_CREATED = "Đã thêm %s.";
    public static final String MSG26_DELETED = "Đã xóa %s.";
    public static final String MSG27_CONFIRM_DELETE = "Xóa %s? Thao tác này không thể hoàn tác.";
    public static final String MSG28_UNSAVED_CHANGES = "Thay đổi của bạn chưa được lưu. Rời trang bây giờ sẽ mất các thay đổi này.";
    public static final String MSG29_STILL_REFERENCED = "Không thể xóa: %s vẫn đang tham chiếu tới bản ghi này. Hãy ngừng hoạt động thay vì xóa.";
    public static final String MSG30_DEACTIVATED = "Đã ngừng hoạt động %s.";
    public static final String MSG31_REACTIVATED = "Đã kích hoạt lại %s.";
    public static final String MSG32_EXPORTED = "Đã tải xuống %s.";

    // ===== 5.4 Đăng nhập, mật khẩu, tài khoản cá nhân =====

    public static final String MSG33_WRONG_CREDENTIALS_ATTEMPTS_LEFT = "Email, số điện thoại hoặc mật khẩu không đúng. Bạn còn %d lần thử.";
    public static final String MSG34_WRONG_CREDENTIALS_STAFF = "Email, số điện thoại hoặc mật khẩu không đúng. Vui lòng kiểm tra lại.";
    public static final String MSG35_ACCOUNT_TEMP_LOCKED = "Tài khoản bị khóa %d phút do nhập sai quá nhiều lần. Thử lại sau %s.";
    public static final String MSG36_LOCK_EXPIRED = "Đã hết thời gian khóa. Bạn có thể đăng nhập lại.";
    public static final String MSG37_ACCOUNT_INACTIVE = "Tài khoản này đã ngừng hoạt động. Vui lòng liên hệ quản trị viên.";
    public static final String MSG38_NO_PASSWORD_YET = "Tài khoản này chưa có mật khẩu. Hãy mở email chúng tôi đã gửi để đặt mật khẩu, sau đó đăng nhập lại.";
    public static final String MSG39_IDENTIFIER_REQUIRED = "Nhập email hoặc số điện thoại.";
    public static final String MSG40_SIGNED_OUT = "Đã đăng xuất.";
    public static final String MSG41_EMAIL_NOT_FOUND = "Không tìm thấy tài khoản nào với email này.";
    public static final String MSG42_CODE_SENT = "Mã đã được gửi tới %s. Mã có hiệu lực trong %d phút.";
    public static final String MSG43_WRONG_CODE = "Mã không đúng. Kiểm tra lại hoặc bấm Gửi lại mã.";
    public static final String MSG44_CODE_EXPIRED = "Mã đã hết hạn. Bấm Gửi lại mã để nhận mã mới.";
    public static final String MSG45_CODE_RESENT = "Đã gửi mã xác thực mới.";
    public static final String MSG46_PASSWORD_TOO_SHORT = "Mật khẩu phải có ít nhất %d ký tự.";
    public static final String MSG47_PASSWORD_MISMATCH = "Mật khẩu xác nhận không khớp.";
    public static final String MSG48_PASSWORD_SAME_AS_OLD = "Mật khẩu mới phải khác mật khẩu hiện tại.";
    public static final String MSG49_PASSWORD_CHANGED = "Đã đổi mật khẩu. Tất cả phiên đăng nhập trước đó đã bị đăng xuất.";
    public static final String MSG50_PHONE_ACCEPTED = "Số điện thoại hợp lệ. Vui lòng điền thông tin đăng ký.";
    public static final String MSG51_PHONE_HAS_ACCOUNT = "Số điện thoại này đã có tài khoản. Vui lòng đăng nhập hoặc dùng Quên mật khẩu.";
    public static final String MSG52_PHONE_DIFFERENT_EMAIL = "Số điện thoại này đã đăng ký với email khác (%s). Hãy dùng email đó, hoặc đến quầy lễ tân để cập nhật.";
    public static final String MSG53_PROFILE_NO_EMAIL = "Hồ sơ của bạn chưa có email nên không thể kích hoạt trực tuyến. Vui lòng đến quầy lễ tân để bổ sung.";
    public static final String MSG54_ACCOUNT_CREATED = "Tài khoản đã được tạo. Vui lòng đăng nhập.";
    public static final String MSG55_EMAIL_CHANGE_CODE_SENT = "Mã xác thực đã được gửi tới địa chỉ email mới.";
    public static final String MSG56_EMAIL_CHANGED = "Đã cập nhật email đăng nhập. Chúng tôi đã báo cho email cũ và đăng xuất mọi phiên.";
    public static final String MSG57_PHONE_CHANGED = "Đã cập nhật số điện thoại.";
    public static final String MSG58_AVATAR_CHANGED = "Đã cập nhật ảnh đại diện.";
    public static final String MSG59_OTHER_DEVICES_SIGNED_OUT = "Đã đăng xuất khỏi tất cả thiết bị khác.";
    public static final String MSG60_UPDATED_BY_FRONT_DESK = "Thông tin này do quầy lễ tân cập nhật. Vui lòng mang giấy tờ tùy thân đến quầy.";
    public static final String MSG61_VERIFICATION_LOCKED = "Thử quá nhiều lần. Xác thực cho email này bị khóa %d phút. Thử lại sau %s.";

    // ===== 5.5 Số lần còn lại khi nhập / gửi lại mã xác thực (CBR-94) =====

    public static final String MSG62_CODE_ATTEMPTS_LEFT = "Bạn còn %d lần nhập mã.";
    public static final String MSG63_CODE_LAST_ATTEMPT = "Nhập sai thêm 1 lần, xác thực cho email này sẽ bị khóa %d phút.";
    public static final String MSG64_CODE_RESENDS_LEFT = "Còn %d lần gửi lại mã trong 60 phút.";
    public static final String MSG65_CODE_RESEND_WILL_LOCK = "Đã hết lượt gửi lại. Gửi thêm sẽ khóa xác thực %d phút.";

    // ===== 5.6 Luật DBR / CBR có nhưng trước đây SRS III.5 chưa có mã (MSG66–MSG75 — đã đề xuất bổ sung vào SRS) =====

    /** DBR-02: mật khẩu phải có cả chữ và số. */
    public static final String MSG66_PASSWORD_RULE = "Mật khẩu phải dài %d–%d ký tự, gồm cả chữ và số.";

    /** DBR-01: họ tên sai ký tự. */
    public static final String MSG67_FULL_NAME_RULE = "Họ tên chỉ gồm chữ cái, khoảng trắng, dấu nháy đơn hoặc gạch nối.";

    /** DBR-03: mã xác thực sai định dạng (khác MSG43 — mã đúng định dạng nhưng sai giá trị). */
    public static final String MSG68_CODE_FORMAT = "Mã xác thực gồm đúng 6 chữ số.";

    /** DBR-04: tuổi vượt quá 100. */
    public static final String MSG69_DOB_TOO_OLD = "Ngày sinh không hợp lệ (tuổi không quá %d).";

    /** CBR-94: bấm "Gửi lại mã" khi chưa đủ 60 giây kể từ lần gửi trước. */
    public static final String MSG70_CODE_RESEND_TOO_SOON = "Vui lòng chờ %d giây nữa rồi bấm Gửi lại mã.";

    /** CBR-31: link đặt mật khẩu sai, đã dùng hoặc đã hết hạn. */
    public static final String MSG71_SETUP_LINK_INVALID = "Link đặt mật khẩu không còn hiệu lực. Vui lòng dùng Quên mật khẩu để đặt lại.";

    /** CBR-24: đặt mật khẩu lần đầu qua link email thành công. */
    public static final String MSG72_PASSWORD_SET = "Đã đặt mật khẩu. Vui lòng đăng nhập.";

    /** UC-04: đổi mật khẩu nhưng mật khẩu hiện tại nhập sai. */
    public static final String MSG73_CURRENT_PASSWORD_WRONG = "Mật khẩu hiện tại không đúng.";

    /** DBR-01: họ tên ngoài độ dài cho phép. */
    public static final String MSG74_FULL_NAME_LENGTH = "Họ và tên phải từ %d đến %d ký tự.";

    /** CBR-100: bài viết đã gỡ hoặc không tồn tại trên trang công khai. */
    public static final String MSG75_POST_UNAVAILABLE = "Bài viết này không còn hiển thị.";

    private Messages() {
    }
}