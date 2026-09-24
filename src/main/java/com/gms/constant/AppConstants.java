package com.gms.constant;

import java.util.List;

/**
 * Hằng số cố định của hệ thống — những giá trị SRS chốt "Fixed by design" hoặc quy ước giao diện chung.
 *
 * KHÔNG đặt ở đây các tham số P-01…P-20 (SRS System Parameters): các giá trị đó Manager sửa được
 * trên màn 4.1.2, nên phải đọc từ bảng SystemConfig trong DB qua Service, không hard-code.
 */
public final class AppConstants {

    // ===== Danh sách & phân trang (GBR-18) =====

    /** Số dòng mặc định mỗi trang. */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** Các lựa chọn số dòng mỗi trang được phép. */
    public static final List<Integer> PAGE_SIZE_OPTIONS = List.of(10, 20, 50);

    // ===== Giá trị cố định theo thiết kế (SRS System Parameters — "Fixed by design") =====

    /** Mỗi đơn hàng chỉ áp được 1 voucher. */
    public static final int MAX_VOUCHER_PER_ORDER = 1;

    /** Mỗi ngày có đúng 2 ca: sáng và chiều. */
    public static final int SHIFTS_PER_DAY = 2;

    // ===== Giá trị cấu hình triển khai, không có màn hình sửa (SRS System Parameters — "Not configurable") =====

    /** Link thanh toán online có hiệu lực 5 phút (không bao giờ vượt hạn thanh toán của đơn). */
    public static final int PAYMENT_LINK_VALID_MINUTES = 5;

    /** Khoảng cách tối thiểu giữa 2 lần yêu cầu gửi mã xác thực. */
    public static final int CODE_RESEND_GAP_SECONDS = 60;

    // ===== Độ dài/định dạng dữ liệu (DBR-01 … DBR-15) =====

    public static final int FULL_NAME_MIN_LENGTH = 2;
    public static final int FULL_NAME_MAX_LENGTH = 50;

    /** DBR-02: 8–64 ký tự (SRS 1.2.2 đang ghi 6–64 — xem docs/notes.md, chờ nhóm chốt). */
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 64;

    public static final int EMAIL_MAX_LENGTH = 100;
    public static final int VERIFICATION_CODE_LENGTH = 6;
    public static final int REASON_MIN_LENGTH = 5;
    public static final int REASON_MAX_LENGTH = 500;
    public static final int MAX_AGE_YEARS = 100;

    /** DBR-08: ảnh JPG/PNG tối đa 5 MB, tối đa 2000 x 2000 px. */
    public static final long IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    public static final int IMAGE_MAX_DIMENSION_PX = 2000;

    private AppConstants() {
       
    }
}
