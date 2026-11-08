package com.gms.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Xử lý ngày giờ theo múi giờ phòng gym (UTC+7): định dạng dd/MM/yyyy, giờ 24h HH:mm.
 * Quy tắc nhóm: luôn lấy "bây giờ"/"hôm nay" qua now()/today() chứ không gọi LocalDate.now() trực tiếp — server có thể đặt múi giờ khác và lệch ngày lúc 0–7h sáng.
 */
public final class DateTimeUtil {

    /** Múi giờ Việt Nam. SRS ghi "Asia/Ha_Noi" nhưng ID chuẩn của Java/IANA là Asia/Ho_Chi_Minh. */
    public static final ZoneId GYM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Định dạng ô input type="date" của HTML gửi lên (luôn là yyyy-MM-dd, bất kể ngôn ngữ trình duyệt). */
    private static final DateTimeFormatter HTML_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private DateTimeUtil() {
    }

    // ===== Thời điểm hiện tại =====

    /**
     * Ngày hôm nay theo giờ Việt Nam.
     */
    public static LocalDate today() {
        return LocalDate.now(GYM_ZONE);
    }

    /**
     * Thời điểm hiện tại theo giờ Việt Nam.
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(GYM_ZONE);
    }

    // ===== Hiển thị (Java → chuỗi) =====

    /**
     * Định dạng ngày thành chuỗi dd/MM/yyyy (trả chuỗi rỗng nếu null).
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_FORMATTER);
    }

    /**
     * Định dạng giờ thành chuỗi HH:mm (trả chuỗi rỗng nếu null).
     */
    public static String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(TIME_FORMATTER);
    }

    /**
     * Định dạng ngày giờ thành chuỗi dd/MM/yyyy HH:mm (trả chuỗi rỗng nếu null).
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    // ===== Đọc từ request (chuỗi → Java) =====

    /**
     * Đọc ngày từ ô input type="date" (yyyy-MM-dd). Trả null nếu rỗng/sai định dạng.
     */
    public static LocalDate parseHtmlDate(String value) {
        if (ValidationUtil.isNullOrEmpty(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), HTML_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Đọc ngày người dùng gõ tay dạng dd/MM/yyyy. Trả null nếu rỗng/sai định dạng.
     */
    public static LocalDate parseDisplayDate(String value) {
        if (ValidationUtil.isNullOrEmpty(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Đọc giờ từ ô input type="time" (HH:mm). Trả null nếu rỗng/sai định dạng.
     */
    public static LocalTime parseTime(String value) {
        if (ValidationUtil.isNullOrEmpty(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ===== Tính toán theo luật nghiệp vụ =====

    /**
     * Tính ngày kết thúc gói theo số tháng (cộng cả ngày cuối).
     * Nếu tháng đích không có ngày đó (bắt đầu 31/01) thì lấy ngày cuối tháng đích.
     */
    public static LocalDate calculateEndDateByMonths(LocalDate startDate, int months) {
        return startDate.plusMonths(months);
    }

    /**
     * Tính tuổi tròn tại 1 ngày (dùng kiểm tra mốc tuổi tối thiểu khi đặt PT).
     */
    public static int calculateAge(LocalDate dateOfBirth, LocalDate atDate) {
        return Period.between(dateOfBirth, atDate).getYears();
    }
}
