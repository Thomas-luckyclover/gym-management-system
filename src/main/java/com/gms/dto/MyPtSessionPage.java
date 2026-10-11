package com.gms.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Kết quả một trang của khối Lịch tập sắp tới hoặc Lịch sử buổi tập trên My PT Sessions.
 * Service tạo sau khi DAO lấy dữ liệu theo bộ lọc và phân trang theo ngày có buổi.
 * Mỗi trang tối đa 8 ngày; toàn bộ buổi trong cùng ngày nằm chung một trang.
 * Servlet truyền DTO sang JSP để render nhóm ngày và thanh phân trang.
 * DTO chỉ chứa dữ liệu, không truy vấn DB, kiểm tra số trang hoặc tự tính tổng trang.
 *
 * @param <T> MyPtUpcomingSessionView hoặc MyPtHistorySessionView của khối tương ứng
 */
public class MyPtSessionPage<T> {

    // Service gom các DTO buổi của trang theo TimeSlots.SlotDate, dùng LinkedHashMap giữ thứ tự.
    // JSP lặp từng ngày và toàn bộ buổi trong ngày; không cắt một ngày sang hai trang.
    private final Map<LocalDate, List<T>> days;

    // Service chuẩn hóa số trang yêu cầu theo tổng trang; bắt đầu từ 1.
    // JSP dùng chọn trang hiện tại và tạo link Trước/Sau riêng cho từng khối.
    private final int currentPage;

    // DAO đếm số SlotDate khác nhau sau bộ lọc và điều kiện quyền xem của khối tương ứng.
    // Đây là tổng ngày có buổi trên tất cả trang, không phải tổng buổi hoặc ngày liên tục.
    private final int totalDays;

    // Service tính từ totalDays với 8 ngày/trang; tối thiểu 1 kể cả khi không có dữ liệu.
    // JSP hiện tổng trang và không cho chuyển quá trang cuối; tránh hiển thị trang 1/0.
    private final int totalPages;

    /**
     * Service truyền kết quả đã chuẩn bị; constructor chỉ gán dữ liệu.
     *
     * @param days nhóm ngày của trang theo thứ tự hiển thị; map rỗng khi không có buổi, không null
     * @param currentPage trang đã được Service chuẩn hóa, bắt đầu từ 1
     * @param totalDays tổng ngày có buổi khớp điều kiện của khối, bằng 0 khi rỗng
     * @param totalPages tổng trang do Service tính, tối thiểu 1
     */
    public MyPtSessionPage(Map<LocalDate, List<T>> days, int currentPage,
            int totalDays, int totalPages) {
        // B1: nhận nhóm ngày và các buổi đã được Service chuẩn bị cho trang này.
        this.days = days;

        // B2: nhận thông tin phân trang để JSP hiển thị và tạo link chuyển trang.
        this.currentPage = currentPage;
        this.totalDays = totalDays;
        this.totalPages = totalPages;
    }

    /** Nhóm ngày và các buổi trong trang hiện tại để JSP lặp hiển thị. */
    public Map<LocalDate, List<T>> getDays() {
        return days;
    }

    /** Trang hiện tại đã chuẩn hóa, bắt đầu từ 1. */
    public int getCurrentPage() {
        return currentPage;
    }

    /** Tổng ngày có buổi sau lọc, tính trên tất cả trang của khối tương ứng. */
    public int getTotalDays() {
        return totalDays;
    }

    /** Tổng trang do Service tính, tối thiểu 1 kể cả khi danh sách rỗng. */
    public int getTotalPages() {
        return totalPages;
    }
}
