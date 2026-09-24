package com.gms.util;

import com.gms.dto.JsonResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Ghi JSON trả về cho các Servlet AJAX, dùng Gson.
 *
 * Ngày giờ gửi xuống trình duyệt ở dạng ISO ("2026-09-24", "14:30", "2026-09-24T14:30:00"),
 * js/common.js tự đổi sang dd/mm/yyyy khi hiển thị (formatDate, formatDateTime).
 */
public final class JsonUtil {

    /**
     * Gson dùng chung (thread-safe). Phải tự đăng ký adapter cho java.time vì Gson mặc định
     * không đọc được LocalDate trên Java 17 (lỗi InaccessibleObjectException).
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
            .registerTypeAdapter(LocalTime.class, new LocalTimeAdapter())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .serializeNulls()
            .create();

    private JsonUtil() {
    }

    /**
     * Ghi JsonResponse ra response với mã HTTP 200.
     * @param response HttpServletResponse của Servlet
     * @param body nội dung JSON chuẩn
     * @throws IOException lỗi ghi response
     */
    public static void writeJson(HttpServletResponse response, JsonResponse body) throws IOException {
        writeJson(response, HttpServletResponse.SC_OK, body);
    }

    /**
     * Ghi JsonResponse ra response với mã HTTP tùy chọn (401 hết phiên, 403 không có quyền...).
     * @param response HttpServletResponse
     * @param httpStatus mã HTTP
     * @param body nội dung JSON chuẩn
     * @throws IOException lỗi ghi response
     */
    public static void writeJson(HttpServletResponse response, int httpStatus, JsonResponse body) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write(GSON.toJson(body));
        writer.flush();
    }

    /**
     * Đổi object bất kỳ thành chuỗi JSON (VD nhúng dữ liệu ban đầu vào thuộc tính data-* của JSP).
     * @param value object cần đổi
     * @return chuỗi JSON
     */
    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    /**
     * Đọc chuỗi JSON (body AJAX gửi lên) thành object Java.
     * @param json chuỗi JSON
     * @param type class đích
     * @return object Java
     */
    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    // ===== Adapter cho java.time =====

    private static class LocalDateAdapter extends TypeAdapter<LocalDate> {

        @Override
        public void write(JsonWriter out, LocalDate value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(value.toString());
        }

        @Override
        public LocalDate read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDate.parse(in.nextString());
        }
    }

    private static class LocalTimeAdapter extends TypeAdapter<LocalTime> {

        @Override
        public void write(JsonWriter out, LocalTime value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(DateTimeUtil.formatTime(value));
        }

        @Override
        public LocalTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalTime.parse(in.nextString());
        }
    }

    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(value.toString());
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            return LocalDateTime.parse(in.nextString());
        }
    }
}
