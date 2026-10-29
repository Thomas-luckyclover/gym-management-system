package com.gms.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO đại diện cho một ca làm việc đã đóng trong tab Lịch sử chốt ca (Màn #41 - UC-26, SRS 7.4.2).
 *
 * <p>Nơi tạo: {@link com.gms.dao.ShiftDAO#findClosedShiftsHistoryPage}.</p>
 * <p>Nơi dùng: Render tab Lịch sử chốt ca tại {@code manager/transaction-list.jsp}.</p>
 *
 * @author Le Cong Minh
 */
public class ClosedShiftHistoryItem {

    // DAO: Shifts.ShiftId
    private final int shiftId;

    // DAO: Shifts.WorkDate, ngày làm việc của ca
    private final LocalDate workDate;

    // DAO: Shifts.ShiftType, Morning | Afternoon
    private final String shiftType;

    // DAO: Persons.FullName của Lễ tân mở ca
    private final String openedByName;

    // DAO: Persons.FullName của Lễ tân đóng ca
    private final String closedByName;

    // DAO: Shifts.OpenedAt, thời điểm mở ca
    private final LocalDateTime openedAt;

    // DAO: Shifts.ClosedAt, thời điểm đóng ca
    private final LocalDateTime closedAt;

    // DAO: Shifts.ExpectedCash, số tiền mặt hệ thống tính toán (DBR-55)
    private final long expectedCash;

    // DAO: Shifts.CountedCash, số tiền mặt thực tế Lễ tân kiểm đếm lúc đóng ca (BR-46)
    private final long countedCash;

    // Service: (countedCash - expectedCash), chênh lệch thừa (+) hoặc thiếu (-)
    private final long variance;

    // DAO: Shifts.VarianceReason, lý do giải trình khi tiền đếm lệch (bắt buộc >= 5 ký tự)
    private final String varianceReason;

    // DAO: Tổng tiền chuyển khoản thu được trong ca từ các đơn không hủy
    private final long totalBankTransfer;

    // DAO: Tổng số hóa đơn / đơn hàng lập trong ca
    private final int totalInvoiceCount;

    public ClosedShiftHistoryItem(int shiftId, LocalDate workDate, String shiftType,
                                 String openedByName, String closedByName,
                                 LocalDateTime openedAt, LocalDateTime closedAt,
                                 long expectedCash, long countedCash, long variance,
                                 String varianceReason, long totalBankTransfer,
                                 int totalInvoiceCount) {
        this.shiftId = shiftId;
        this.workDate = workDate;
        this.shiftType = shiftType;
        this.openedByName = openedByName;
        this.closedByName = closedByName;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.expectedCash = expectedCash;
        this.countedCash = countedCash;
        this.variance = variance;
        this.varianceReason = varianceReason;
        this.totalBankTransfer = totalBankTransfer;
        this.totalInvoiceCount = totalInvoiceCount;
    }

    public int getShiftId() {
        return shiftId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public String getShiftType() {
        return shiftType;
    }

    public String getOpenedByName() {
        return openedByName;
    }

    public String getClosedByName() {
        return closedByName;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public long getExpectedCash() {
        return expectedCash;
    }

    public long getCountedCash() {
        return countedCash;
    }

    public long getVariance() {
        return variance;
    }

    public long getCashVariance() {
        return variance;
    }

    public String getVarianceReason() {
        return varianceReason;
    }

    public long getTotalBankTransfer() {
        return totalBankTransfer;
    }

    public int getTotalInvoiceCount() {
        return totalInvoiceCount;
    }

    /** Tên tiếng Việt của ca làm việc. */
    public String getShiftTypeLabel() {
        if ("Morning".equalsIgnoreCase(shiftType)) {
            return "Ca sáng";
        }
        if ("Afternoon".equalsIgnoreCase(shiftType)) {
            return "Ca chiều";
        }
        return shiftType;
    }

    /** Kiểm tra xem ca có chênh lệch tiền mặt hay không. */
    public boolean hasVariance() {
        return variance != 0;
    }
}
