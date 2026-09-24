package com.gms.util;

import com.gms.constant.AppConstants;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Các hàm kiểm tra dữ liệu dùng chung (codeImplementationBackend.md Mục 6), bám theo DBR-01 … DBR-18 của SRS.
 *
 * Quy tắc:
 * - Chỉ gọi từ Service (không gọi trong DAO, hạn chế gọi trong Servlet).
 * - Hàm thuần: chỉ nhận input, trả true/false — không đọc DB, không đọc file cấu hình, không in lỗi.
 * - Câu thông báo lỗi lấy từ constant/Messages.java, KHÔNG viết trong class này.
 */
public final class ValidationUtil {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)*\\.[a-zA-Z]{2,}$");

    /** DBR-17: đúng 10 chữ số, bắt đầu bằng 0. */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^0\\d{9}$");

    /** DBR-03: đúng 6 chữ số. */
    private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile("^\\d{6}$");

    /** DBR-01: chữ cái (kể cả tiếng Việt có dấu), khoảng trắng, dấu nháy đơn, gạch nối. */
    private static final Pattern FULL_NAME_PATTERN = Pattern.compile("^[\\p{L} '\\-]+$");

    /** DBR-10: 4–20 ký tự, chỉ chữ in hoa và chữ số. */
    private static final Pattern VOUCHER_CODE_PATTERN = Pattern.compile("^[A-Z0-9]{4,20}$");

    private static final Pattern HAS_LETTER_PATTERN = Pattern.compile(".*\\p{L}.*");
    private static final Pattern HAS_DIGIT_PATTERN = Pattern.compile(".*\\d.*");

    private ValidationUtil() {
    }

    // ===== Kiểm tra chung =====

    /**
     * Kiểm tra chuỗi có null hoặc rỗng (sau khi trim) không.
     * @param value chuỗi cần kiểm tra
     * @return true nếu null hoặc rỗng
     */
    public static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Kiểm tra độ dài chuỗi (sau khi trim) nằm trong khoảng cho phép.
     * @param value chuỗi cần kiểm tra
     * @param min độ dài tối thiểu
     * @param max độ dài tối đa
     * @return true nếu độ dài hợp lệ
     */
    public static boolean isLengthValid(String value, int min, int max) {
        if (isNullOrEmpty(value)) {
            return false;
        }
        int length = value.trim().length();
        return length >= min && length <= max;
    }

    /**
     * Kiểm tra chuỗi có phải số nguyên hợp lệ không.
     * @param value chuỗi cần kiểm tra
     * @return true nếu parse được thành int
     */
    public static boolean isValidInteger(String value) {
        if (isNullOrEmpty(value)) {
            return false;
        }
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Kiểm tra số nguyên nằm trong khoảng [min, max] — dùng cho % VAT 0–20, hoa hồng 0–100, giảm giá 1–100 (DBR-07).
     * @param value giá trị cần kiểm tra
     * @param min cận dưới (bao gồm)
     * @param max cận trên (bao gồm)
     * @return true nếu nằm trong khoảng
     */
    public static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * DBR-06: số tiền là số nguyên VND lớn hơn 0.
     * @param amount số tiền
     * @return true nếu hợp lệ
     */
    public static boolean isValidMoney(long amount) {
        return amount > 0;
    }

    // ===== Thông tin cá nhân =====

    /**
     * DBR-18: email đúng định dạng name@domain, tối đa 100 ký tự.
     * @param email chuỗi email
     * @return true nếu hợp lệ
     */
    public static boolean isValidEmail(String email) {
        if (isNullOrEmpty(email)) {
            return false;
        }
        String trimmedEmail = email.trim();
        boolean isTooLong = trimmedEmail.length() > AppConstants.EMAIL_MAX_LENGTH;
        if (isTooLong) {
            return false;
        }
        return EMAIL_PATTERN.matcher(trimmedEmail).matches();
    }

    /**
     * DBR-17 (phần định dạng): đúng 10 chữ số, bắt đầu bằng 0.
     * @param phone số điện thoại
     * @return true nếu đúng định dạng
     */
    public static boolean isValidPhone(String phone) {
        if (isNullOrEmpty(phone)) {
            return false;
        }
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    /**
     * DBR-17 (phần đầu số nhà mạng — P-16): 3 số đầu nằm trong danh sách đầu số hợp lệ.
     * Danh sách do Service đọc từ cấu hình rồi truyền vào, để hàm này vẫn là hàm thuần.
     * @param phone số điện thoại đã qua isValidPhone
     * @param validPrefixes danh sách đầu số, VD ["032", "090", "091"]
     * @return true nếu đầu số hợp lệ
     */
    public static boolean hasValidPhonePrefix(String phone, List<String> validPrefixes) {
        if (!isValidPhone(phone)) {
            return false;
        }
        String prefix = phone.trim().substring(0, 3);
        return validPrefixes.contains(prefix);
    }

    /**
     * DBR-01: họ tên 2–50 ký tự, chỉ gồm chữ (có dấu), khoảng trắng, dấu nháy đơn, gạch nối, có ít nhất 1 chữ cái.
     * @param fullName họ tên
     * @return true nếu hợp lệ
     */
    public static boolean isValidFullName(String fullName) {
        boolean isLengthOk = isLengthValid(fullName, AppConstants.FULL_NAME_MIN_LENGTH, AppConstants.FULL_NAME_MAX_LENGTH);
        if (!isLengthOk) {
            return false;
        }
        // Chuẩn hóa NFC: gộp chữ + dấu rời (gõ từ một số bộ gõ) thành 1 ký tự để regex nhận đúng là chữ cái
        String normalizedName = Normalizer.normalize(fullName.trim(), Normalizer.Form.NFC);
        boolean isPatternOk = FULL_NAME_PATTERN.matcher(normalizedName).matches();
        boolean hasLetter = HAS_LETTER_PATTERN.matcher(normalizedName).matches();
        return isPatternOk && hasLetter;
    }

    /**
     * DBR-04: ngày sinh không ở tương lai và tuổi không quá 100.
     * @param dateOfBirth ngày sinh
     * @param today ngày hôm nay (truyền DateTimeUtil.today() vào — để hàm thuần và test được)
     * @return true nếu hợp lệ
     */
    public static boolean isValidDateOfBirth(LocalDate dateOfBirth, LocalDate today) {
        if (dateOfBirth == null) {
            return false;
        }
        boolean isInFuture = dateOfBirth.isAfter(today);
        if (isInFuture) {
            return false;
        }
        int age = Period.between(dateOfBirth, today).getYears();
        return age <= AppConstants.MAX_AGE_YEARS;
    }

    // ===== Tài khoản & xác thực =====

    /**
     * DBR-02: mật khẩu 8–64 ký tự, có ít nhất 1 chữ cái và 1 chữ số.
     * (Không kiểm tra "khác mật khẩu cũ" ở đây — việc đó cần hash trong DB, Service tự so bằng PasswordUtil.)
     * @param password mật khẩu thô
     * @return true nếu hợp lệ
     */
    public static boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }
        int length = password.length();
        boolean isLengthOk = length >= AppConstants.PASSWORD_MIN_LENGTH && length <= AppConstants.PASSWORD_MAX_LENGTH;
        boolean hasLetter = HAS_LETTER_PATTERN.matcher(password).matches();
        boolean hasDigit = HAS_DIGIT_PATTERN.matcher(password).matches();
        return isLengthOk && hasLetter && hasDigit;
    }

    /**
     * DBR-03: mã xác thực đúng 6 chữ số.
     * @param code mã người dùng nhập
     * @return true nếu đúng định dạng
     */
    public static boolean isValidVerificationCode(String code) {
        if (isNullOrEmpty(code)) {
            return false;
        }
        return VERIFICATION_CODE_PATTERN.matcher(code.trim()).matches();
    }

    // ===== Nghiệp vụ =====

    /**
     * DBR-05, GBR-09: lý do (từ chối, hủy, xóa, lệch tiền...) dài 5–500 ký tự.
     * @param reason nội dung lý do
     * @return true nếu hợp lệ
     */
    public static boolean isValidReason(String reason) {
        return isLengthValid(reason, AppConstants.REASON_MIN_LENGTH, AppConstants.REASON_MAX_LENGTH);
    }

    /**
     * DBR-13: khoảng ngày hợp lệ khi From ≤ To.
     * @param fromDate ngày bắt đầu
     * @param toDate ngày kết thúc
     * @return true nếu hợp lệ
     */
    public static boolean isValidDateRange(LocalDate fromDate, LocalDate toDate) {
        boolean isMissingDate = fromDate == null || toDate == null;
        if (isMissingDate) {
            return false;
        }
        return !fromDate.isAfter(toDate);
    }

    /**
     * DBR-10: mã voucher 4–20 ký tự, chỉ chữ in hoa và chữ số (Service nên toUpperCase trước khi gọi).
     * @param voucherCode mã voucher
     * @return true nếu hợp lệ
     */
    public static boolean isValidVoucherCode(String voucherCode) {
        if (isNullOrEmpty(voucherCode)) {
            return false;
        }
        return VOUCHER_CODE_PATTERN.matcher(voucherCode.trim()).matches();
    }

    /**
     * DBR-08: ảnh JPG hoặc PNG, tối đa 5 MB (kích thước pixel Service tự kiểm tra sau khi đọc ảnh).
     * @param contentType Part.getContentType() của file upload
     * @param sizeInBytes Part.getSize()
     * @return true nếu đúng loại và dung lượng
     */
    public static boolean isValidImageFile(String contentType, long sizeInBytes) {
        if (contentType == null) {
            return false;
        }
        boolean isJpeg = contentType.equals("image/jpeg");
        boolean isPng = contentType.equals("image/png");
        boolean isAllowedType = isJpeg || isPng;
        boolean isSizeOk = sizeInBytes > 0 && sizeInBytes <= AppConstants.IMAGE_MAX_BYTES;
        return isAllowedType && isSizeOk;
    }
}
