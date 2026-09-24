package com.gms.dto;

import java.util.Collections;
import java.util.Map;

/**
 * Khuôn JSON chuẩn mà MỌI Servlet AJAX trả về — js/common.js (hàm sendAjaxRequest) đọc đúng khuôn này.
 *
 * Dạng JSON:
 * {
 *   "success": true | false,
 *   "message": "Đã lưu thay đổi." | null,
 *   "data":    { ... } | [ ... ] | null,
 *   "errors":  { "email": "Email không đúng định dạng." }   (rỗng nếu không có lỗi theo ô)
 * }
 *
 * Cách dùng trong Servlet: JsonUtil.writeJson(response, JsonResponse.from(result));
 */
public class JsonResponse {

    private final boolean success;
    private final String message;
    private final Object data;
    private final Map<String, String> errors;

    private JsonResponse(boolean success, String message, Object data, Map<String, String> errors) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errors = errors;
    }

    /**
     * Đổi kết quả Service thành JSON trả cho trình duyệt.
     * @param result kết quả từ Service
     * @return JsonResponse cùng nội dung
     */
    public static JsonResponse from(ServiceResult<?> result) {
        return new JsonResponse(result.isSuccess(), result.getMessage(), result.getData(), result.getFieldErrors());
    }

    /**
     * JSON thành công, dùng khi Servlet trả dữ liệu tra cứu đơn giản không qua ServiceResult.
     * @param data dữ liệu
     * @return JsonResponse thành công
     */
    public static JsonResponse success(Object data) {
        return new JsonResponse(true, null, data, Collections.emptyMap());
    }

    /**
     * JSON thất bại chung (dùng ở Filter khi hết phiên/không có quyền).
     * @param message thông báo lỗi
     * @return JsonResponse thất bại
     */
    public static JsonResponse error(String message) {
        return new JsonResponse(false, message, null, Collections.emptyMap());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
