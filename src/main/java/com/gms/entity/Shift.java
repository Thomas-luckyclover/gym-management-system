package com.gms.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ca làm việc của nhân viên Lễ tân quầy, ánh xạ bảng Shifts trong cơ sở dữ liệu (Cluster B).
 *
 * <p>Quy tắc nghiệp vụ:</p>
 * <ul>
 *   <li>Mỗi ngày mở cửa có đúng 2 ca trực: Sáng (Morning) và Chiều (Afternoon) (CBR-104).</li>
 *   <li>Ca làm việc dùng để đối soát doanh thu tiền mặt và quản lý các giao dịch tại quầy (UC-25).</li>
 *   <li>Vòng đời trạng thái: NotOpened → Open → Closed (bất biến sau khi Closed).</li>
 *   <li>Mọi hóa đơn lập tại quầy trong thời gian ca mở đều được gắn với ShiftId này (CBR-105).</li>
 *   <li>Chốt ca: Tiền mặt thực tế đếm được (CountedCash) đối chiếu với tiền mặt sổ sách (ExpectedCash).
 *       Nếu có chênh lệch, bắt buộc nhập lý do (VarianceReason) tối thiểu 5 ký tự (BR-46, GBR-09).</li>
 * </ul>
 *
 * @author Antigravity
 */
public class Shift {

    /** Khóa chính tự tăng, bất biến → final, không có setter. */
    private final int shiftId;

    /** Ngày làm việc của ca (WorkDate), ánh xạ cột WorkDate kiểu DATE. */
    private LocalDate workDate;

    /** Loại ca làm việc: Morning (Ca sáng) hoặc Afternoon (Ca chiều). */
    private ShiftType shiftType;

    /** Nhân viên Lễ tân mở ca (FK -> Employees.EmployeeId); null khi ca NotOpened. */
    private Integer openedByEmployeeId;

    /** Nhân viên Lễ tân chốt ca (FK -> Employees.EmployeeId); null khi ca chưa Closed. */
    private Integer closedByEmployeeId;

    /** Trạng thái ca: NotOpened, Open, Closed. */
    private Status status;

    /** Thời điểm mở ca thực tế; null khi chưa mở. */
    private LocalDateTime openedAt;

    /** Thời điểm chốt ca thực tế; null khi chưa chốt. */
    private LocalDateTime closedAt;

    /** Doanh thu tiền mặt kỳ vọng theo hệ thống (VND, DBR-55); chốt khi đóng ca. */
    private Long expectedCash;

    /** Tiền mặt thực tế đếm được trong két tại thời điểm chốt ca (VND). */
    private Long countedCash;

    /** Lý do chênh lệch giữa tiền đếm được và tiền kỳ vọng (bắt buộc khi lệch). */
    private String varianceReason;

    // ===== Enum lồng =====

    /**
     * Loại ca làm việc: Morning (Ca sáng) hoặc Afternoon (Ca chiều).
     * Khớp CHECK constraint CK_Shifts_Type trong database.
     */
    public enum ShiftType {
        MORNING("Morning", "Ca sáng"),
        AFTERNOON("Afternoon", "Ca chiều");

        private final String dbValue;
        private final String displayName;

        ShiftType(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static ShiftType fromDbValue(String value) {
            if (value == null) {
                return null;
            }
            for (ShiftType item : values()) {
                if (item.dbValue.equalsIgnoreCase(value.trim())) {
                    return item;
                }
            }
            return null;
        }
    }

    /**
     * Trạng thái ca làm việc: NotOpened (Chưa mở), Open (Đang mở), Closed (Đã chốt).
     * Khớp CHECK constraint CK_Shifts_Status trong database.
     */
    public enum Status {
        NOT_OPENED("NotOpened", "Chưa mở"),
        OPEN("Open", "Đang mở"),
        CLOSED("Closed", "Đã chốt");

        private final String dbValue;
        private final String displayName;

        Status(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Status fromDbValue(String value) {
            if (value == null) {
                return null;
            }
            for (Status item : values()) {
                if (item.dbValue.equalsIgnoreCase(value.trim())) {
                    return item;
                }
            }
            return null;
        }
    }

    // ===== Constructors =====

    /**
     * Constructor tối thiểu chỉ nhận khóa chính shiftId (bất biến).
     *
     * @param shiftId ID định danh ca làm việc
     */
    public Shift(int shiftId) {
        this.shiftId = shiftId;
        this.status = Status.NOT_OPENED;
    }

    /**
     * Constructor đầy đủ các trường, dùng khi DAO nạp dữ liệu từ bảng Shifts.
     */
    public Shift(int shiftId, LocalDate workDate, ShiftType shiftType,
                 Integer openedByEmployeeId, Integer closedByEmployeeId,
                 Status status, LocalDateTime openedAt, LocalDateTime closedAt,
                 Long expectedCash, Long countedCash, String varianceReason) {
        this.shiftId = shiftId;
        this.workDate = workDate;
        this.shiftType = shiftType;
        this.openedByEmployeeId = openedByEmployeeId;
        this.closedByEmployeeId = closedByEmployeeId;
        this.status = status;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.expectedCash = expectedCash;
        this.countedCash = countedCash;
        this.varianceReason = varianceReason;
    }

    // ===== Getters and Setters =====

    public int getShiftId() {
        return shiftId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public ShiftType getShiftType() {
        return shiftType;
    }

    public void setShiftType(ShiftType shiftType) {
        this.shiftType = shiftType;
    }

    public Integer getOpenedByEmployeeId() {
        return openedByEmployeeId;
    }

    public void setOpenedByEmployeeId(Integer openedByEmployeeId) {
        this.openedByEmployeeId = openedByEmployeeId;
    }

    public Integer getClosedByEmployeeId() {
        return closedByEmployeeId;
    }

    public void setClosedByEmployeeId(Integer closedByEmployeeId) {
        this.closedByEmployeeId = closedByEmployeeId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public Long getExpectedCash() {
        return expectedCash;
    }

    public void setExpectedCash(Long expectedCash) {
        this.expectedCash = expectedCash;
    }

    public Long getCountedCash() {
        return countedCash;
    }

    public void setCountedCash(Long countedCash) {
        this.countedCash = countedCash;
    }

    public String getVarianceReason() {
        return varianceReason;
    }

    public void setVarianceReason(String varianceReason) {
        this.varianceReason = varianceReason;
    }

    // ===== Helper getters cho JSP / EL =====

    public boolean isNotOpened() {
        return status == Status.NOT_OPENED;
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public boolean isClosed() {
        return status == Status.CLOSED;
    }

    /**
     * Tính độ chênh lệch tiền mặt: CountedCash - ExpectedCash.
     * Số âm = thiếu hụt, số dương = dư thừa, 0 = khớp.
     */
    public Long getDiscrepancy() {
        if (countedCash == null || expectedCash == null) {
            return null;
        }
        return countedCash - expectedCash;
    }
}
