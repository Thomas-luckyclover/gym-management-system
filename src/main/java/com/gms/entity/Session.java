package com.gms.entity;

/** Giá trị trạng thái buổi PT (DBR-37). */
public final class Session {
    private Session() {
    }

    public enum Status {
        SCHEDULED("Scheduled"), CHECKED_IN("CheckedIn"), MISSED("Missed"), CANCELLED("Cancelled");
        private final String dbValue;

        Status(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static Status fromDbValue(String value) {
            for (Status item : values()) {
                if (item.dbValue.equals(value)) {
                    return item;
                }
            }
            return null;
        }
    }

    public enum MissedReason {
        NO_CHECK_IN("NoCheckIn"), NO_MEMBERSHIP("NoMembership"), MEMBERSHIP_FROZEN("MembershipFrozen");
        private final String dbValue;

        MissedReason(String dbValue) {
            this.dbValue = dbValue;
        }

        public String getDbValue() {
            return dbValue;
        }

        public static MissedReason fromDbValue(String value) {
            for (MissedReason item : values()) {
                if (item.dbValue.equals(value)) {
                    return item;
                }
            }
            return null;
        }
    }
}
