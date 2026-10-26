package com.gms.dto;

/**
 * Mục kiểm tra điều kiện duyệt đơn nghỉ phép (Màn #27, BF-05):
 * 1. Phép năm (Annual Quota)
 * 2. Định biên nhân sự (Min Staffing)
 * 3. Lịch dạy PT (Scheduled Sessions)
 */
public class ReviewCheck {

    private String key;          // REQUEST (dữ liệu lỗi) | ANNUAL_QUOTA | STAFFING | SESSIONS
    private String label;        // "Phép năm", "Định biên", "Lịch dạy"
    private boolean applicable;  // false thì không áp dụng
    private boolean ok;          // true nếu đạt, false nếu vi phạm
    private String message;      // câu tóm tắt điều kiện

    public ReviewCheck() {
    }

    public ReviewCheck(String key, String label, boolean applicable, boolean ok, String message) {
        this.key = key;
        this.label = label;
        this.applicable = applicable;
        this.ok = ok;
        this.message = message;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public boolean isApplicable() {
        return applicable;
    }

    public void setApplicable(boolean applicable) {
        this.applicable = applicable;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

}
