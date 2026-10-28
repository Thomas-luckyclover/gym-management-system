package com.gms.integration;

/**
 * Lỗi khi lấy thư viện bài tập từ wger.de: mất mạng, quá giờ chờ, mã HTTP khác 200 hoặc JSON sai cấu trúc.
 * ExerciseLibraryService bắt lỗi này để giữ dữ liệu cũ trong bảng Exercises thay vì làm hỏng trang.
 */
public class WgerUnavailableException extends Exception {

    public WgerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
