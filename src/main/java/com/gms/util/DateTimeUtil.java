package com.gms.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Xử lý ngày giờ theo GBR-01: hiển thị dd/MM/yyyy, giờ 24h HH:mm, mọi thời điểm theo múi giờ phòng gym (UTC+7).
 *
 * Quy tắc cho cả nhóm:
 * - Lấy "bây giờ"/"hôm nay" LUÔN qua DateTimeUtil.now() / today() — cấm LocalDate.now() trực tiếp
 *   (server Tomcat có thể đặt múi giờ khác, sẽ lệch ngày lúc 0h–7h sáng).
 * - Trong JSP dùng hàm EL: ${gms:formatDate(order.createdAt)} (khai báo ở WEB-INF/gms-functions.tld).
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
     * @return ngày hôm nay theo giờ Việt Nam
     */
    public static LocalDate today() {
        return LocalDate.now(GYM_ZONE);
    }

    /**
     * @return thời điểm hiện tại theo giờ Việt Nam
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(GYM_ZONE);
    }

    // ===== Hiển thị (Java → chuỗi) =====

    /**
     * @param date ngày cần hiển thị
     * @return chuỗi dd/MM/yyyy, hoặc chuỗi rỗng nếu null
     */
    public static String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_FORMATTER);
    }

    /**
     * @param time giờ cần hiển thị
     * @return chuỗi HH:mm, hoặc chuỗi rỗng nếu null
     */
    public static String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(TIME_FORMATTER);
    }

    /**
     * @param dateTime thời điểm cần hiển thị
     * @return chuỗi dd/MM/yyyy HH:mm, hoặc chuỗi rỗng nếu null
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    // ===== Đọc từ request (chuỗi → Java) =====

    /**
     * Đọc ngày từ ô input type="date" (yyyy-MM-dd).
     * @param value chuỗi request gửi lên
     * @return LocalDate, hoặc null nếu rỗng/sai định dạng (Service coi null là "chưa nhập/không hợp lệ")
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
     * Đọc ngày người dùng gõ tay dạng dd/MM/yyyy.
     * @param value chuỗi dd/MM/yyyy
     * @return LocalDate, hoặc null nếu rỗng/sai định dạng
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
     * Đọc giờ từ ô input type="time" (HH:mm).
     * @param value chuỗi HH:mm
     * @return LocalTime, hoặc null nếu rỗng/sai định dạng
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
     * GBR-03 (ngày kết thúc tính cả ngày cuối): gói 1 tháng bắt đầu 05/09 kết thúc 05/10.
     * Nếu tháng đích không có ngày đó (bắt đầu 31/01) thì lấy ngày cuối tháng đích (28 hoặc 29/02) —
     * LocalDate.plusMonths tự làm đúng việc này.
     * @param startDate ngày bắt đầu gói
     * @param months số tháng của gói
     * @return ngày kết thúc (vẫn dùng được trọn ngày này)
     */
    public static LocalDate calculateEndDateByMonths(LocalDate startDate, int months) {
        return startDate.plusMonths(months);
    }

    /**
     * Tính tuổi tròn tại 1 ngày — dùng kiểm tra mốc 10 tuổi khi bán/đặt PT (P-05, CBR-13…15).
     * @param dateOfBirth ngày sinh
     * @param atDate ngày tính tuổi (thường là today())
     * @return số tuổi tròn
     */
    public static int calculateAge(LocalDate dateOfBirth, LocalDate atDate) {
        return Period.between(dateOfBirth, atDate).getYears();
    }
}
