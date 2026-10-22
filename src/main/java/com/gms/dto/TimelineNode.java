package com.gms.dto;

import java.time.LocalDateTime;

/**
 * Nút trong timeline quy trình duyệt đơn nghỉ phép (Màn #27, BF-05).
 */
public class TimelineNode {

    private String kind;        // SUBMITTED | TRANSFERRED | PENDING | APPROVED | REJECTED
    private String title;       // "Nộp đơn", "Chuyển người duyệt", "Chờ duyệt", "Đã duyệt", "Từ chối"
    private String actorName;
    private String targetName;  // chỉ dùng khi TRANSFERRED
    private String note;
    private LocalDateTime at;
    private String state;       // DONE | CURRENT | TODO | FAILED

    public TimelineNode() {
    }

    public TimelineNode(String kind, String title, String actorName, String targetName, String note, LocalDateTime at, String state) {
        this.kind = kind;
        this.title = title;
        this.actorName = actorName;
        this.targetName = targetName;
        this.note = note;
        this.at = at;
        this.state = state;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getAt() {
        return at;
    }

    public void setAt(LocalDateTime at) {
        this.at = at;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
