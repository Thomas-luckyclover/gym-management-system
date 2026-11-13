package com.gms.service;

import com.gms.constant.Messages;
import com.gms.constant.Role;
import com.gms.dao.NotificationDAO;
import com.gms.dto.ServiceResult;
import com.gms.entity.Notification;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tầng Service xử lý nghiệp vụ gửi thông báo hệ thống và đọc/đánh dấu đã đọc thông báo cho người dùng.
 *
 * <p><b>Cách hoạt động:</b> Phối hợp với {@link NotificationDAO} để phát hành thông báo theo vai trò hoặc
 * từng nhân viên trong transaction, đồng thời định dạng dữ liệu chuông thông báo (người gửi, phân loại,
 * liên kết điều hướng nhanh tới màn hình nghiệp vụ tương ứng).</p>
 */
public class NotificationService {

    /**
     * Giới hạn số lượng notification hiển thị trong chuông ({@code 0} = không giới hạn, hiển thị tất cả).
     */
    private static final int BELL_LIMIT = 0;

    /**
     * Tiền tố tiêu đề dùng để nhận diện thông báo thuộc luồng bảo trì BF-06 nhằm tạo liên kết điều hướng.
     */
    private static final String MAINTENANCE_TITLE_PREFIX =
            "Bảo trì ·";

    /**
     * Biểu thức chính quy trích xuất mã phiếu bảo trì ({@code BT-xxxx} hoặc {@code MR-yyyyMMdd-xxxx}) trong nội dung.
     */
    private static final Pattern
            MAINTENANCE_CODE_PATTERN =
            Pattern.compile(
                    "\\b(MR-\\d{8}-\\d{4}|BT-\\d{4})\\b",
                    Pattern.CASE_INSENSITIVE
            );

    private final NotificationDAO notificationDAO;

    /**
     * Khởi tạo Service cùng {@link NotificationDAO} mặc định.
     */
    public NotificationService() {
        notificationDAO =
                new NotificationDAO();
    }

    // ===== Gửi notification từ hệ thống =====

    /**
     * Gửi một thông báo hệ thống tới toàn bộ tài khoản đang hoạt động thuộc một vai trò trong transaction.
     *
     * <p><b>Cách hoạt động:</b> Truy vấn danh sách {@code AccountId} hợp lệ theo {@code role}; nếu có người nhận,
     * chèn một bản ghi {@code Notifications} rồi lần lượt gắn từng {@code AccountId} vào {@code NotificationRecipients}.</p>
     *
     * @param connection kết nối CSDL đang mở transaction
     * @param role       vai trò nhận thông báo (ví dụ {@code Role.MANAGER})
     * @param title      tiêu đề thông báo
     * @param content    nội dung thông báo
     * @throws SQLException khi thao tác CSDL lỗi
     */
    public void notifyRoleInTransaction(
            Connection connection,
            Role role,
            String title,
            String content
    ) throws SQLException {

        List<Integer> accountIds =
                notificationDAO
                        .findActiveAccountIdsByRole(
                                connection,
                                role
                        );

        if (accountIds.isEmpty()) {
            return;
        }

        int notificationId =
                notificationDAO
                        .insertSystemNotification(
                                connection,
                                title,
                                content
                        );

        for (Integer accountId : accountIds) {

            notificationDAO.insertRecipient(
                    connection,
                    notificationId,
                    accountId
            );
        }
    }

    /**
     * Gửi một thông báo hệ thống trực tiếp tới tài khoản của một nhân viên cụ thể trong transaction.
     *
     * <p><b>Cách hoạt động:</b> Tìm {@code AccountId} đang {@code Active} gắn với {@code employeeId}; nếu tồn tại,
     * tạo bản ghi thông báo hệ thống và gán duy nhất người nhận đó vào {@code NotificationRecipients}.</p>
     *
     * @param connection kết nối CSDL đang mở transaction
     * @param employeeId ID nhân viên nhận thông báo
     * @param title      tiêu đề thông báo
     * @param content    nội dung thông báo
     * @throws SQLException khi thao tác CSDL lỗi
     */
    public void notifyEmployeeInTransaction(
            Connection connection,
            int employeeId,
            String title,
            String content
    ) throws SQLException {

        Integer accountId =
                notificationDAO
                        .findActiveAccountIdByEmployeeId(
                                connection,
                                employeeId
                        );

        if (accountId == null) {
            return;
        }

        int notificationId =
                notificationDAO
                        .insertSystemNotification(
                                connection,
                                title,
                                content
                        );

        notificationDAO.insertRecipient(
                connection,
                notificationId,
                accountId
        );
    }

    // ===== Dữ liệu chuông =====

    /**
     * Lấy dữ liệu hiển thị cho chuông thông báo và trang danh sách thông báo của người dùng hiện tại.
     *
     * <p><b>Cách hoạt động:</b> Đếm tổng số thông báo chưa đọc, tải danh sách thông báo mới nhất từ
     * {@link NotificationDAO}, chuyển từng dòng thành {@link BellItem} (kèm tên người gửi, nhãn phân loại,
     * đường dẫn điều hướng theo vai trò {@code currentRole}) rồi trả về trong {@code Map} chứa {@code unreadCount} và {@code items}.</p>
     *
     * @param accountId   ID tài khoản đang đăng nhập
     * @param currentRole vai trò hiện tại để suy ra đường dẫn điều hướng phù hợp
     * @return {@link ServiceResult} chứa dữ liệu chuông dạng {@code Map<String, Object>}
     */
    public ServiceResult<Map<String, Object>>
    getBellData(
            int accountId,
            Role currentRole
    ) {

        try {
            int unreadCount =
                    notificationDAO.countUnread(
                            accountId
                    );

            List<NotificationDAO.NotificationBellRow>
                    rows =
                    notificationDAO
                            .findLatestForAccount(
                                    accountId,
                                    BELL_LIMIT
                            );

            List<BellItem> items =
                    new ArrayList<>();

            for (NotificationDAO.NotificationBellRow
                    row : rows) {

                Notification notification =
                        row.getNotification();

                String actionUrl =
                        buildActionUrl(
                                notification,
                                currentRole
                        );

                items.add(
                        new BellItem(
                                notification
                                        .getNotificationId(),
                                notification
                                        .getTitle(),
                                notification
                                        .getContent(),
                                notification
                                        .getSentAt(),
                                row.getReadAt(),
                                row.isRead(),
                                notification
                                        .isSystemGenerated(),
                                buildSenderName(row),
                                buildSenderSubline(row),
                                buildAudienceLabel(
                                        notification
                                                .getAudience()
                                ),
                                buildCategoryLabel(
                                        notification
                                ),
                                actionUrl,
                                actionUrl != null
                                        ? buildActionLabel(
                                                notification
                                        )
                                        : null
                        )
                );
            }

            Map<String, Object> data =
                    new LinkedHashMap<>();

            data.put(
                    "unreadCount",
                    unreadCount
            );

            data.put(
                    "items",
                    items
            );

            return ServiceResult.ok(data);

        } catch (SQLException e) {
            e.printStackTrace();

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }
    }

    // ===== Đánh dấu đã đọc =====

    /**
     * Đánh dấu một thông báo là đã đọc đối với tài khoản hiện tại.
     *
     * <p><b>Cách hoạt động:</b> Kiểm tra {@code notificationId > 0}, gọi {@code notificationDAO.markRead};
     * nếu không có bản ghi nào được cập nhật thì trả về lỗi không tìm thấy thông báo.</p>
     *
     * @param accountId      ID tài khoản người dùng
     * @param notificationId ID thông báo cần đánh dấu đọc
     * @return {@link ServiceResult} kết quả xử lý
     */
    public ServiceResult<Void> markRead(
            int accountId,
            int notificationId
    ) {

        if (notificationId <= 0) {
            return ServiceResult.fail(
                    String.format(
                            Messages.MSG08_NOT_FOUND,
                            "thông báo"
                    )
            );
        }

        try {
            boolean updated =
                    notificationDAO.markRead(
                            accountId,
                            notificationId
                    );

            if (!updated) {
                return ServiceResult.fail(
                        String.format(
                                Messages.MSG08_NOT_FOUND,
                                "thông báo"
                        )
                );
            }

            return ServiceResult.ok(null);

        } catch (SQLException e) {
            e.printStackTrace();

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }
    }

    /**
     * Đánh dấu toàn bộ thông báo của tài khoản hiện tại là đã đọc.
     *
     * <p><b>Cách hoạt động:</b> Gọi {@code notificationDAO.markAllRead(accountId)} để cập nhật hàng loạt
     * trường {@code ReadAt} cho mọi thông báo chưa đọc của tài khoản.</p>
     *
     * @param accountId ID tài khoản người dùng
     * @return {@link ServiceResult} kết quả xử lý
     */
    public ServiceResult<Void> markAllRead(
            int accountId
    ) {

        try {
            notificationDAO.markAllRead(
                    accountId
            );

            return ServiceResult.ok(null);

        } catch (SQLException e) {
            e.printStackTrace();

            return ServiceResult.fail(
                    Messages.MSG04_SYSTEM_BUSY
            );
        }
    }

    /**
     * Tự động đánh dấu đã đọc các thông báo theo từ khóa chủ đề khi người dùng mở màn hình nghiệp vụ liên quan.
     *
     * <p><b>Cách hoạt động:</b> Gọi {@code notificationDAO.markReadByKeywords(accountId, keywords)} và bọc
     * {@code try/catch} nuốt ngoại lệ SQL (chỉ log stacktrace) để không làm gián đoạn luồng tải trang chính.</p>
     *
     * @param accountId ID tài khoản đang truy cập trang
     * @param keywords  danh sách từ khóa chủ đề cần tự động đánh dấu đã đọc
     */
    public void markTopicNotificationsRead(
            int accountId,
            String... keywords
    ) {
        if (accountId <= 0 || keywords == null || keywords.length == 0) {
            return;
        }
        try {
            notificationDAO.markReadByKeywords(accountId, keywords);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ===== Điều hướng & hiển thị chi tiết notification =====

    /**
     * Xác định tên hiển thị của người gửi thông báo.
     *
     * <p><b>Cách hoạt động:</b> Nếu là thông báo tự động trả về {@code "Hệ thống Super Gym"}; nếu có người gửi
     * cụ thể ưu tiên trả về {@code senderName}, fallback về {@code "Ban quản lý"}.</p>
     */
    private String buildSenderName(
            NotificationDAO.NotificationBellRow row
    ) {
        Notification notification = row.getNotification();
        if (notification == null || notification.isSystemGenerated()) {
            return "Hệ thống Super Gym";
        }
        String senderName = row.getSenderName();
        if (senderName != null && !senderName.isBlank()) {
            return senderName.trim();
        }
        return "Ban quản lý";
    }

    /**
     * Tạo dòng phụ mô tả vai trò và mã nhân viên của người gửi thông báo.
     *
     * <p><b>Cách hoạt động:</b> Với thông báo hệ thống trả về {@code "Tự động từ hệ thống"}; ngược lại
     * định dạng chức vụ tiếng Việt qua {@link #formatPositionLabel} rồi ghép kèm mã nhân viên nếu có.</p>
     */
    private String buildSenderSubline(
            NotificationDAO.NotificationBellRow row
    ) {
        Notification notification = row.getNotification();
        if (notification == null || notification.isSystemGenerated()) {
            return "Tự động từ hệ thống";
        }
        String positionLabel = formatPositionLabel(row.getSenderPosition());
        String senderCode = row.getSenderCode();
        if (senderCode != null && !senderCode.isBlank()) {
            return positionLabel + " · " + senderCode.trim();
        }
        return positionLabel;
    }

    /**
     * Chuyển đổi mã chức vụ trong CSDL sang tên tiếng Việt dễ đọc.
     */
    private String formatPositionLabel(String position) {
        if (position == null) {
            return "Quản lý";
        }
        switch (position.trim()) {
            case "Admin":
                return "Quản trị hệ thống";
            case "Manager":
                return "Quản lý";
            case "PT":
                return "Huấn luyện viên PT";
            case "Receptionist":
                return "Lễ tân";
            default:
                return position.trim();
        }
    }

    /**
     * Chuyển đổi enum đối tượng nhận thông báo ({@code Audience}) sang nhãn tiếng Việt.
     */
    private String buildAudienceLabel(
            Notification.Audience audience
    ) {
        if (audience == null) {
            return "Cá nhân";
        }
        switch (audience) {
            case ALL_CUSTOMERS:
                return "Tất cả khách hàng";
            case ACTIVE_MEMBERS:
                return "Hội viên đang hoạt động";
            case PTS:
                return "Huấn luyện viên (PT)";
            case RECEPTIONISTS:
                return "Lễ tân";
            case ALL_STAFF:
                return "Toàn bộ nhân sự";
            case INDIVIDUAL:
            default:
                return "Cá nhân";
        }
    }

    /**
     * Phân loại chủ đề thông báo dựa trên tiêu đề và nhóm đối tượng nhận để hiển thị badge trên giao diện.
     */
    private String buildCategoryLabel(
            Notification notification
    ) {
        if (notification == null || notification.getTitle() == null) {
            return "Thông báo";
        }
        String title = notification.getTitle().trim();
        String titleLower = title.toLowerCase();

        if (title.startsWith(MAINTENANCE_TITLE_PREFIX) || titleLower.contains("bảo trì")) {
            return "Bảo trì thiết bị";
        }
        if (titleLower.contains("nghỉ phép") || titleLower.contains("ngày nghỉ") || titleLower.contains("đơn nghỉ")) {
            return "Nghỉ phép";
        }
        if (titleLower.contains("xóa hồ sơ")) {
            return "Hồ sơ hội viên";
        }
        if (titleLower.contains("buổi tập") || titleLower.contains("lịch tập") || titleLower.contains("đặt lịch")) {
            return "Lịch tập PT";
        }
        if (notification.isSystemGenerated()) {
            return "Thông báo hệ thống";
        }
        Notification.Audience audience = notification.getAudience();
        if (audience == Notification.Audience.ALL_CUSTOMERS
                || audience == Notification.Audience.ACTIVE_MEMBERS) {
            return "Thông báo phòng tập";
        }
        return "Thông báo nội bộ";
    }

    /**
     * Tạo nhãn nút hành động điều hướng dựa theo chủ đề tiêu đề của thông báo.
     */
    private String buildActionLabel(
            Notification notification
    ) {
        if (notification == null || notification.getTitle() == null) {
            return "Đi tới trang liên quan";
        }
        String title = notification.getTitle().trim();
        String titleLower = title.toLowerCase();

        if (title.startsWith(MAINTENANCE_TITLE_PREFIX) || titleLower.contains("bảo trì")) {
            return "Xem yêu cầu bảo trì";
        }
        if (titleLower.contains("nghỉ phép") || titleLower.contains("ngày nghỉ") || titleLower.contains("đơn nghỉ")) {
            return "Xem đơn nghỉ phép";
        }
        if (titleLower.contains("xóa hồ sơ")) {
            return "Xem yêu cầu xóa hồ sơ";
        }
        if (titleLower.contains("buổi tập") || titleLower.contains("lịch tập") || titleLower.contains("đặt lịch")) {
            return "Xem lịch tập";
        }
        return "Đi tới trang liên quan";
    }

    /**
     * Suy luận đường dẫn điều hướng tương đối cho thông báo hệ thống theo chủ đề và vai trò hiện tại.
     *
     * <p><b>Cách hoạt động:</b> Kiểm tra thông báo có phải do hệ thống sinh ra không, sau đó đối chiếu từ khóa
     * trong tiêu đề với từng luồng nghiệp vụ (bảo trì kèm trích xuất mã phiếu, nghỉ phép, xóa hồ sơ, lịch tập PT)
     * và vai trò {@code currentRole} để trả về URL màn hình đích tương ứng.</p>
     */
    private String buildActionUrl(
            Notification notification,
            Role currentRole
    ) {

        if (notification == null
                || currentRole == null
                || !notification.isSystemGenerated()
                || notification.getTitle() == null) {

            return null;
        }

        String title = notification.getTitle().trim();
        String titleLower = title.toLowerCase();

        // 1. Luồng Bảo trì thiết bị (BF-06)
        if (title.startsWith(MAINTENANCE_TITLE_PREFIX) || titleLower.contains("bảo trì")) {
            String requestCode =
                    extractMaintenanceRequestCode(
                            notification.getContent()
                    );

            if (currentRole == Role.MANAGER) {
                return "/manager/maintenance-requests"
                        + (requestCode != null ? "?keyword=" + requestCode : "");
            }

            if (currentRole == Role.RECEPTIONIST) {
                return "/receptionist/maintenance-requests"
                        + (requestCode != null ? "?keyword=" + requestCode : "");
            }

            return null;
        }

        // 2. Luồng Đơn xin / duyệt nghỉ phép (BF-05)
        if (titleLower.contains("nghỉ phép") || titleLower.contains("ngày nghỉ") || titleLower.contains("đơn nghỉ")) {
            if (currentRole == Role.MANAGER) {
                return "/manager/leave-requests";
            }
            return "/staff/leave-request";
        }

        // 3. Luồng Yêu cầu xóa hồ sơ hội viên (BR-15)
        if (titleLower.contains("xóa hồ sơ")) {
            if (currentRole == Role.MANAGER) {
                return "/manager/member-deletion-review";
            }
            if (currentRole == Role.RECEPTIONIST) {
                return "/receptionist/customer-list";
            }
            return null;
        }

        // 4. Luồng Lịch tập / Đặt lịch PT
        if (titleLower.contains("buổi tập") || titleLower.contains("lịch tập") || titleLower.contains("đặt lịch")) {
            if (currentRole == Role.CUSTOMER) {
                return "/customer/my-bookings";
            }
            if (currentRole == Role.PT) {
                return "/pt/schedule";
            }
            return null;
        }

        return null;
    }

    /**
     * Trích xuất mã yêu cầu bảo trì (ví dụ {@code BT-0001}) từ nội dung thông báo để gắn vào tham số {@code ?keyword=}.
     *
     * <p><b>Cách hoạt động:</b> Áp dụng regex {@link #MAINTENANCE_CODE_PATTERN} lên {@code content},
     * nếu khớp trả về chuỗi in hoa, ngược lại trả về {@code null}.</p>
     */
    private String extractMaintenanceRequestCode(
            String content
    ) {

        if (content == null) {
            return null;
        }

        Matcher matcher =
                MAINTENANCE_CODE_PATTERN
                        .matcher(content);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group()
                .toUpperCase();
    }

    // ===== Dữ liệu trả cho JS =====

    /**
     * DTO biểu diễn một mục thông báo đã định dạng sẵn để serialize JSON trả cho frontend chuông thông báo.
     */
    public static class BellItem {

        private final int notificationId;
        private final String title;
        private final String content;
        private final LocalDateTime sentAt;
        private final LocalDateTime readAt;
        private final boolean read;
        private final boolean systemGenerated;
        private final String senderName;
        private final String senderSubline;
        private final String audienceLabel;
        private final String categoryLabel;
        private final String actionUrl;
        private final String actionLabel;

        /**
         * Khởi tạo {@code BellItem} với đầy đủ thuộc tính hiển thị và liên kết điều hướng.
         */
        public BellItem(
                int notificationId,
                String title,
                String content,
                LocalDateTime sentAt,
                LocalDateTime readAt,
                boolean read,
                boolean systemGenerated,
                String senderName,
                String senderSubline,
                String audienceLabel,
                String categoryLabel,
                String actionUrl,
                String actionLabel
        ) {

            this.notificationId =
                    notificationId;

            this.title = title;
            this.content = content;
            this.sentAt = sentAt;
            this.readAt = readAt;
            this.read = read;

            this.systemGenerated =
                    systemGenerated;

            this.senderName = senderName;
            this.senderSubline = senderSubline;
            this.audienceLabel = audienceLabel;
            this.categoryLabel = categoryLabel;

            this.actionUrl =
                    actionUrl;
            this.actionLabel =
                    actionLabel;
        }

        /** Trả về ID thông báo. */
        public int getNotificationId() {
            return notificationId;
        }

        /** Trả về tiêu đề thông báo. */
        public String getTitle() {
            return title;
        }

        /** Trả về nội dung chi tiết thông báo. */
        public String getContent() {
            return content;
        }

        /** Trả về thời điểm gửi thông báo. */
        public LocalDateTime getSentAt() {
            return sentAt;
        }

        /** Trả về thời điểm người dùng đọc thông báo (hoặc {@code null} nếu chưa đọc). */
        public LocalDateTime getReadAt() {
            return readAt;
        }

        /** Trả về {@code true} nếu thông báo đã được đọc. */
        public boolean isRead() {
            return read;
        }

        /** Trả về {@code true} nếu là thông báo do hệ thống tự sinh. */
        public boolean isSystemGenerated() {
            return systemGenerated;
        }

        /** Trả về tên người gửi (hoặc "Hệ thống Super Gym"). */
        public String getSenderName() {
            return senderName;
        }

        /** Trả về dòng mô tả phụ của người gửi (chức vụ và mã nhân viên). */
        public String getSenderSubline() {
            return senderSubline;
        }

        /** Trả về nhãn đối tượng nhận thông báo bằng tiếng Việt. */
        public String getAudienceLabel() {
            return audienceLabel;
        }

        /** Trả về nhãn chủ đề phân loại thông báo. */
        public String getCategoryLabel() {
            return categoryLabel;
        }

        /** Trả về đường dẫn điều hướng khi bấm vào thông báo (nếu có). */
        public String getActionUrl() {
            return actionUrl;
        }

        /** Trả về nhãn hiển thị cho nút điều hướng (nếu có). */
        public String getActionLabel() {
            return actionLabel;
        }
    }
}
