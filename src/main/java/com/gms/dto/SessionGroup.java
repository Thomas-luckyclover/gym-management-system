package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Nhóm các buổi có CÙNG trạng thái + CÙNG lý do (màn Gói của tôi).
 * Thay 11 dòng giống hệt nhau bằng 1 dòng: "11 buổi · lý do" + các ngày dạng chip.
 */
public class SessionGroup {

    private final String statusKey;      // missed | cancelled | warn … → JSP chọn màu nhãn
    private final String statusLabel;    // "Vắng", "Đã hủy", "Cần chú ý"
    private final String explanation;    // lý do chung của cả nhóm
    private final List<MyPtSessionRow> sessions = new ArrayList<>();

    public SessionGroup(String statusKey, String statusLabel, String explanation) {
        this.statusKey = statusKey;
        this.statusLabel = statusLabel;
        this.explanation = explanation;
    }

    public String getStatusKey() { return statusKey; }
    public String getStatusLabel() { return statusLabel; }
    public String getExplanation() { return explanation; }
    public List<MyPtSessionRow> getSessions() { return sessions; }
}
