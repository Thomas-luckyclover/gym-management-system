package com.gms.service;

import com.gms.dao.SystemConfigDAO;
import com.gms.entity.SystemConfig;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Đọc tham số nghiệp vụ v26 từ bảng SystemConfigs. */
public class SystemConfigService {

    // ===== Mã tham số P-01...P-22 =====

    // P-01: Số lần bảo lưu tối đa cho mỗi gói tập
    public static final String MAX_FREEZE_COUNT = "P-01";

    // P-02: Số tháng bảo lưu tối đa mỗi lần
    public static final String MAX_FREEZE_MONTHS = "P-02";

    // P-03: Phí bảo lưu (VND), 0 = miễn phí
    public static final String FREEZE_FEE = "P-03";

    // P-04: Thời hạn gói tập, VD "1D,1M,3M,9M,12M" (D = ngày, M = tháng)
    public static final String PACKAGE_DURATIONS = "P-04";

    // P-05: Tuổi tối thiểu được dùng dịch vụ PT
    public static final String MIN_AGE_FOR_PT = "P-05";

    // P-06: Độ dài một buổi PT (phút)
    public static final String SESSION_DURATION_MINUTES = "P-06";

    // P-08: Số lượt Swap (đổi buổi) cho MỖI THÁNG của gói PT.
    // Hạn mức của một gói = P-08 × số tháng của gói, chốt vào Subscriptions.MaxSwapsSnapshot lúc bán
    // (mặc định 3 → gói 1/2/3 tháng được 3/6/9 lượt).
    public static final String MAX_SWAP_COUNT = "P-08";

    // P-09: Hạn thanh toán đơn online, cũng là thời gian giữ slot đặt (phút)
    public static final String PAYMENT_DUE_MINUTES = "P-09";

    // P-10: Thuế VAT (%) đã nằm trong giá bán
    public static final String VAT_PERCENT = "P-10";

    // P-11: Tỷ lệ hoa hồng mặc định cho PT (%)
    public static final String DEFAULT_COMMISSION_RATE = "P-11";

    // P-12: Giờ chốt nộp đơn nghỉ của ngày hôm trước (HH:mm)
    public static final String LEAVE_SUBMIT_DEADLINE = "P-12";

    // P-13: Số PT tối thiểu còn lại của mỗi ca
    public static final String MIN_PT_PER_SHIFT = "P-13";

    // P-14: Số lễ tân tối thiểu còn lại của mỗi ca
    public static final String MIN_RECEPTIONIST_PER_SHIFT = "P-14";

    // P-15: Hiệu lực mã xác thực / mã khôi phục (phút)
    public static final String VERIFICATION_VALID_MINUTES = "P-15";

    // P-16: Đầu số di động hợp lệ, phân tách bởi dấu phẩy
    public static final String PHONE_PREFIXES = "P-16";

    // P-17: Số lần sai trước khi tạm khóa (đăng nhập và mã xác thực)
    public static final String MAX_FAILED_ATTEMPTS = "P-17";

    // P-18: Thời gian khóa tạm (phút)
    public static final String LOCK_MINUTES = "P-18";

    // P-19: Giờ mở cửa mặc định hàng ngày (HH:mm-HH:mm)
    public static final String OPENING_HOURS = "P-19";

    // P-20: Giờ chốt duyệt đơn nghỉ của ngày hôm trước (HH:mm)
    public static final String LEAVE_APPROVED_DEADLINE = "P-20";
    // P-21: Thời hạn gói PT, VD "1M,2M,3M"
    public static final String PT_PACKAGE_DURATIONS = "P-21";

    // P-22: Số khách tối thiểu để hiện điểm PT (DBR-69)
    public static final String MIN_PT_RATING_CUSTOMERS = "P-22";

    // DAO dùng chung
    private final SystemConfigDAO systemConfigDAO = new SystemConfigDAO();

    /**
     * Đọc tham số dạng số nguyên (P-05, P-09, P-15, P-17, P-18...).
     * @param configKey mã tham số, VD: SystemConfigService.MAX_FAILED_ATTEMPTS
     * @return giá trị dạng int
     * @throws SQLException lỗi truy vấn
     * @throws IllegalStateException thiếu tham số hoặc giá trị không phải số nguyên (lỗi cấu hình, không phải lỗi người dùng)
     */
    public int getInt(String configKey) throws SQLException {
        // B1: Lấy chuỗi thô từ DB qua getString
        String rawValue = getString(configKey);
        // B2: Chuyển chuỗi ("5") thành số nguyên (5); nếu DB lỡ định dạng sai (như 5 phút) => Integer.parseInt ném NumberFormatException bọc lại thành lỗi rõ nghĩa
        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Tham so " + configKey + " khong phai so nguyen: " + rawValue, e);
        }
    }

    /**
     * Đọc tham số dạng chuỗi nguyên bản (VD: P-12, P-19).
     * @param configKey mã tham số
     * @return giá trị ConfigValue
     * @throws SQLException lỗi truy vấn
     * @throws IllegalStateException thiếu tham số trong bảng SystemConfigs (lỗi cấu hình, không phải lỗi người dùng)
     */
    public String getString(String configKey) throws SQLException {
        // B1: Gọi DAO tra đúng 1 dòng theo ConfigKey
        SystemConfig config = systemConfigDAO.findByKey(configKey);

        // B2: DAO trả null nghĩa là không có dòng nào trong DB khớp configKey
        // Lỗi cấu hình (quên seed data / gõ sai tên tham số), không phải lỗi người dùng nhập
        // => ném IllegalStateException (runtime exception)
        if (config == null) {
            throw new IllegalStateException("Thieu tham so " + configKey + " trong bang SystemConfigs");
        }

        // B3: Có dữ liệu, trả đúng cột ConfigValue (chuỗi thô, chưa parse gì cả)
        return config.getConfigValue();
    }

    /**
     * Đọc tham số dạng danh sách, tách bởi dấu phẩy (VD: P-04, P-16).
     * @param configKey mã tham số
     * @return danh sách giá trị đã trim, giữ nguyên thứ tự trong ConfigValue
     * @throws SQLException lỗi truy vấn
     */
    public List<String> getList(String configKey) throws SQLException {
        // B1: Lấy chuỗi thô, VD: "03,05,07,08,09"
        String rawValue = getString(configKey);
        // B2: Tách theo dấu phẩy và tạo thành mảng
        String[] parts = rawValue.split(",");

        // B3: trim từng phần tử (phòng trường hợp DB lưu có khoảng trắng dư, VD: "03, 05, 07")
        // => rồi gom vào List để trả về
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            values.add(part.trim());
        }
        return values;
    }
}
