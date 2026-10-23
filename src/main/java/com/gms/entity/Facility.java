package com.gms.entity;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Entity Layer: Represents a physical gym facility or equipment item in the system.
 *
 * <p><b>Architectural Responsibility:</b></p>
 * In the layered architecture (Servlet -> Service -> DAO -> Entity), the Entity layer
 * serves as the domain model (POJO/JavaBean). It encapsulates state and mirrors the database
 * schema for data transfer between persistence (DAO) and business (Service) layers,
 * without containing business rules or direct persistence operations.
 *
 * <p><b>Persistent vs. Derived (Runtime) Fields:</b></p>
 * <ul>
 *   <li><b>Persistent fields:</b> {@code facilityId}, {@code facilityName}, {@code category},
 *       {@code location}, {@code quantity}, and {@code status} directly map to columns in the
 *       {@code Facilities} table.</li>
 *   <li><b>Derived/Runtime fields:</b> {@code pendingQuantity} and {@code availableQuantity}
 *       are computed dynamically at query time from active maintenance requests
 *       ({@code Status IN ('Open', 'InProgress')}).</li>
 * </ul>
 *
 * <p><b>Why derived fields are not stored in the database:</b></p>
 * Storing calculated quantities in separate database columns would violate relational normalization,
 * introduce synchronization anomalies (e.g., race conditions during concurrent request creation),
 * and require schema modifications which are strictly prohibited. Computing them dynamically
 * guarantees that creating, rejecting, or closing requests immediately reflects across the catalog.
 */
public class Facility {

    // Persistent fields (mapped directly to database table 'Facilities')
    private int facilityId;
    private String facilityName;
    private Category category;
    private String location;
    private int quantity;
    private Status status;

    // Derived/calculated fields (computed at runtime from active MaintenanceRequests & EquipmentItems)
    private int pendingQuantity;
    private int availableQuantity;
    private List<EquipmentItem> items = new ArrayList<>();

    public Facility() {
    }

    /**
     * Constructs a Facility with persistent database fields.
     * Default availableQuantity is set to total quantity if active, or 0 if retired.
     */
    public Facility(
            int facilityId,
            String facilityName,
            Category category,
            String location,
            int quantity,
            Status status
    ) {
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.category = category;
        this.location = location;
        this.quantity = quantity;
        this.status = status;
        this.pendingQuantity = 0;
        this.availableQuantity = (status == Status.RETIRED) ? 0 : Math.max(0, quantity);
    }

    /**
     * Constructs a Facility with both persistent and calculated runtime quantity fields.
     *
     * @param pendingQuantity   number of unfinished maintenance requests (Open + InProgress)
     * @param availableQuantity operational units available for member use: MAX(0, quantity - pendingQuantity),
     *                          enforced to 0 if status is Retired.
     */
    public Facility(
            int facilityId,
            String facilityName,
            Category category,
            String location,
            int quantity,
            Status status,
            int pendingQuantity,
            int availableQuantity
    ) {
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.category = category;
        this.location = location;
        this.quantity = quantity;
        this.status = status;
        this.pendingQuantity = pendingQuantity;
        this.availableQuantity = (status == Status.RETIRED) ? 0 : Math.max(0, availableQuantity);
    }

    public int getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(int facilityId) {
        this.facilityId = facilityId;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getTotalQuantity() {
        return quantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.quantity = totalQuantity;
    }

    public boolean isAvailable() {
        return !isRetired() && availableQuantity > 0;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        if (status == Status.RETIRED) {
            this.availableQuantity = 0;
        }
    }

    /**
     * Gets the count of unfinished maintenance requests (Open + InProgress) for this facility.
     * This represents unresolved maintenance issues reported against this facility type.
     */
    public int getPendingQuantity() {
        return pendingQuantity;
    }

    public void setPendingQuantity(int pendingQuantity) {
        this.pendingQuantity = pendingQuantity;
    }

    /**
     * Gets the calculated operational quantity available for member use.
     * Formula: If Status = Retired -> 0; Otherwise -> MAX(0, totalQuantity - pendingQuantity).
     */
    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        if (this.status == Status.RETIRED) {
            this.availableQuantity = 0;
        } else {
            this.availableQuantity = Math.max(0, availableQuantity);
        }
    }

    public List<EquipmentItem> getItems() {
        return items;
    }

    public void setItems(List<EquipmentItem> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    /**
     * Checks if the facility has been permanently retired / decommissioned.
     */
    public boolean isRetired() {
        return this.status == Status.RETIRED;
    }

    /**
     * Secondary indicator: Returns true if the database status is {@link Status#UNDER_MAINTENANCE}
     * and the facility is not retired.
     *
     * <p><b>Multi-Unit Support Rationale:</b> When a facility has multiple physical units (e.g. Total = 2,
     * Pending = 1, Available = 1), {@link #getAvailabilityLabel()} correctly returns "Có thể sử dụng"
     * because 1 unit is still operational for members. This method allows the UI to display a secondary
     * badge ("Đang bảo trì") to inform staff of ongoing maintenance without falsely implying that
     * ALL units of this equipment are unusable.</p>
     */
    public boolean isMaintenanceOngoing() {
        return this.status == Status.UNDER_MAINTENANCE;
    }

    /**
     * Returns the user-facing availability label derived from actual calculated quantities and lifecycle status.
     *
     * <p><b>Database Status vs. User-Facing Availability:</b></p>
     * <ul>
     *   <li><b>Database Status ({@link #getStatus()}):</b> Stored in {@code Facilities.Status}
     *       ('Available', 'UnderMaintenance', 'Retired'). Governs system workflows, Manager review,
     *       and maintenance operations. It is never modified solely for display.</li>
     *   <li><b>User-Facing Availability (This method):</b> Derived dynamically for receptionists and members:
     *     <ul>
     *       <li>Retired -> {@code "Ngừng sử dụng"}</li>
     *       <li>Non-retired with {@code availableQuantity > 0} -> {@code "Có thể sử dụng"}</li>
     *       <li>Non-retired with {@code availableQuantity == 0} -> {@code "Không có thiết bị khả dụng"}</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @return user-facing availability status string
     */
    public String getAvailabilityLabel() {
        if (isRetired()) {
            return "Ngừng sử dụng";
        }
        if (availableQuantity > 0) {
            return "Có thể sử dụng";
        }
        return "Không có thiết bị khả dụng";
    }

    /**
     * Returns the CSS badge class corresponding to the user-facing availability status.
     * <ul>
     *   <li>Retired ("Ngừng sử dụng") -> {@code "badge-neutral"}</li>
     *   <li>Non-retired with {@code availableQuantity > 0} ("Có thể sử dụng") -> {@code "badge-success"}</li>
     *   <li>Non-retired with {@code availableQuantity == 0} ("Không có thiết bị khả dụng") -> {@code "badge-danger"}</li>
     * </ul>
     *
     * @return CSS class name for styling the status badge
     */
    public String getAvailabilityBadgeClass() {
        if (isRetired()) {
            return "badge-neutral";
        }
        if (availableQuantity > 0) {
            return "badge-success";
        }
        return "badge-danger";
    }

    public enum Category {

        CARDIO("Cardio", "Cardio"),
        FREE_WEIGHTS("FreeWeights", "Tạ tự do"),
        MACHINES("Machines", "Máy tập"),
        AMENITIES("Amenities", "Tiện ích");

        private final String dbValue;
        private final String displayName;

        Category(String dbValue, String displayName) {
            this.dbValue = dbValue;
            this.displayName = displayName;
        }

        public String getDbValue() {
            return dbValue;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Category fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }

            for (Category category : values()) {
                if (category.dbValue.equalsIgnoreCase(dbValue)) {
                    return category;
                }
            }

            return null;
        }

        /**
         * Resolves matching Category enums from a search keyword against both
         * English dbValue and Vietnamese displayName, supporting case-insensitive
         * and accent-insensitive matching.
         *
         * @param keyword search term entered by receptionist
         * @return list of matching Category enums, or empty list if none match
         */
        public static List<Category> matchByKeyword(String keyword) {
            if (keyword == null || keyword.isBlank()) {
                return Collections.emptyList();
            }

            String normalizedQuery = removeAccents(keyword.trim().toLowerCase());
            String queryNoSpace = normalizedQuery.replace(" ", "");

            List<Category> matches = new ArrayList<>();
            for (Category category : values()) {
                String normDisplay = removeAccents(category.displayName.toLowerCase());
                String normDb = removeAccents(category.dbValue.toLowerCase());

                if (normDisplay.contains(normalizedQuery) || normalizedQuery.contains(normDisplay)
                        || normDb.contains(normalizedQuery) || normalizedQuery.contains(normDb)
                        || normDb.contains(queryNoSpace) || queryNoSpace.contains(normDb)) {
                    matches.add(category);
                }
            }
            return matches;
        }

        private static String removeAccents(String text) {
            if (text == null) {
                return "";
            }
            String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
            return normalized.replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
        }
    }

    public enum Status {

        AVAILABLE("Available", "Có thể sử dụng"),
        UNDER_MAINTENANCE("UnderMaintenance", "Đang bảo trì"),
        RETIRED("Retired", "Ngừng sử dụng");

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

        public static Status fromDbValue(String dbValue) {
            if (dbValue == null) {
                return null;
            }

            for (Status status : values()) {
                if (status.dbValue.equalsIgnoreCase(dbValue)) {
                    return status;
                }
            }

            return null;
        }
    }
}