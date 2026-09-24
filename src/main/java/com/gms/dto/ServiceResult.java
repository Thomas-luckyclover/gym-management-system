package com.gms.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kết quả trả về từ mọi method Service (codeImplementationBackend.md Mục 6, 7, 10).
 *
 * Service KHÔNG ném exception ra Servlet, cũng không trả null để báo lỗi — luôn trả ServiceResult:
 * - Thành công: ServiceResult.ok(data) hoặc ServiceResult.ok(data, "Đã lưu thay đổi.")
 * - Thất bại chung (hiện toast/banner): ServiceResult.fail("Có lỗi hệ thống...")
 * - Thất bại theo từng ô nhập (hiện chữ đỏ dưới ô — GBR-17): ServiceResult.failField("email", Messages.MSG11_INVALID_EMAIL)
 *
 * Servlet chỉ cần: if (result.isSuccess()) {...} else {...}
 *
 * @param <T> kiểu dữ liệu mang theo khi thành công (VD Customer, PageResult&lt;Order&gt;, Void)
 */
public class ServiceResult<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final Map<String, String> fieldErrors;

    private ServiceResult(boolean success, String message, T data, Map<String, String> fieldErrors) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.fieldErrors = fieldErrors;
    }

    // ===== Tạo kết quả thành công =====

    /**
     * Thành công, không kèm thông báo.
     * @param data dữ liệu trả về (có thể null nếu kiểu là Void)
     * @return ServiceResult thành công
     */
    public static <T> ServiceResult<T> ok(T data) {
        return new ServiceResult<>(true, null, data, Collections.emptyMap());
    }

    /**
     * Thành công, kèm thông báo để Servlet hiện toast.
     * @param data dữ liệu trả về
     * @param message thông báo thành công, VD Messages.MSG23_SAVED
     * @return ServiceResult thành công
     */
    public static <T> ServiceResult<T> ok(T data, String message) {
        return new ServiceResult<>(true, message, data, Collections.emptyMap());
    }

    // ===== Tạo kết quả thất bại =====

    /**
     * Thất bại chung, không gắn với ô nhập cụ thể nào.
     * @param message thông báo thân thiện, tiếng Việt, KHÔNG chứa chi tiết kỹ thuật (e.getMessage())
     * @return ServiceResult thất bại
     */
    public static <T> ServiceResult<T> fail(String message) {
        return new ServiceResult<>(false, message, null, Collections.emptyMap());
    }

    /**
     * Thất bại do 1 ô nhập sai — JSP/JS hiện lỗi đỏ ngay dưới ô đó.
     * @param fieldName tên ô (trùng thuộc tính name của input trong form)
     * @param message nội dung lỗi
     * @return ServiceResult thất bại có fieldErrors
     */
    public static <T> ServiceResult<T> failField(String fieldName, String message) {
        Map<String, String> errors = new LinkedHashMap<>();
        errors.put(fieldName, message);
        return new ServiceResult<>(false, message, null, errors);
    }

    /**
     * Thất bại do nhiều ô nhập sai cùng lúc (validate cả form rồi mới trả về).
     * @param fieldErrors map tên ô → nội dung lỗi (dùng LinkedHashMap để giữ thứ tự ô trên form)
     * @return ServiceResult thất bại có fieldErrors
     */
    public static <T> ServiceResult<T> failFields(Map<String, String> fieldErrors) {
        Map<String, String> errors = new LinkedHashMap<>(fieldErrors);
        return new ServiceResult<>(false, null, null, errors);
    }

    // ===== Getter =====

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    /** Map tên ô → lỗi; rỗng (không bao giờ null) nếu không có lỗi theo ô. */
    public Map<String, String> getFieldErrors() {
        return Collections.unmodifiableMap(fieldErrors);
    }

    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }
}
