package com.gms.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Đọc cấu hình triển khai từ file src/main/resources/application.properties.
 *
 * File này chứa thông tin RIÊNG TỪNG MÁY và BÍ MẬT (mật khẩu DB, app password Gmail, khóa VNPay)
 * nên KHÔNG commit lên git — mỗi người copy application.properties.example thành application.properties rồi tự điền.
 *
 * Phân biệt với tham số nghiệp vụ P-01…P-20: các tham số đó nằm trong bảng SystemConfig (Manager sửa được),
 * không đọc qua class này.
 */
public final class AppConfig {

    private static final String CONFIG_FILE = "application.properties";
    private static final Properties PROPERTIES = loadProperties();

    private AppConfig() {
    }

    /**
     * Đọc file cấu hình 1 lần duy nhất khi class được nạp.
     * Thiếu file thì dừng luôn với thông báo rõ ràng, để người mới clone về biết ngay phải làm gì.
     */
    private static Properties loadProperties() {
        Properties properties = new Properties();
        ClassLoader classLoader = AppConfig.class.getClassLoader();

        try (InputStream input = classLoader.getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new IllegalStateException("Thiếu file " + CONFIG_FILE
                        + ". Copy src/main/resources/application.properties.example thành application.properties rồi điền thông tin máy bạn.");
            }
            InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được file " + CONFIG_FILE, e);
        }
        return properties;
    }

    /**
     * Lấy giá trị bắt buộc phải có.
     * @param key tên khóa, VD "db.url"
     * @return giá trị đã trim
     */
    public static String get(String key) {
        String value = PROPERTIES.getProperty(key);
        boolean isMissing = value == null || value.trim().isEmpty();
        if (isMissing) {
            throw new IllegalStateException("Thiếu cấu hình '" + key + "' trong " + CONFIG_FILE);
        }
        return value.trim();
    }

    /**
     * Lấy giá trị không bắt buộc.
     * @param key tên khóa
     * @param defaultValue giá trị dùng khi thiếu khóa
     * @return giá trị đã trim, hoặc defaultValue
     */
    public static String get(String key, String defaultValue) {
        String value = PROPERTIES.getProperty(key);
        boolean isMissing = value == null || value.trim().isEmpty();
        if (isMissing) {
            return defaultValue;
        }
        return value.trim();
    }

    /**
     * Lấy giá trị số nguyên bắt buộc.
     * @param key tên khóa
     * @return giá trị số
     */
    public static int getInt(String key) {
        String value = get(key);
        return Integer.parseInt(value);
    }
}
