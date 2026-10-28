package com.gms.integration;

import com.gms.dto.ExerciseItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Gọi API wger.de để lấy Thư viện bài tập (miễn phí, không cần key; dữ liệu giấy phép CC BY-SA 4.0).
 *
 *   GET https://wger.de/api/v2/exerciseinfo/?language__code=en&limit=100&offset=0
 *   → JSON { count, next, previous, results: [ { id, category{id}, muscles[{id}], equipment[{id}],
 *                                                images[{image, is_main}], translations[{name, description, language}] } ] }
 *   Lấy hết các trang theo link "next". Chỉ ExerciseLibraryService gọi, tối đa 1 lần mỗi ngày.
 */
public class WgerClient {

    private static final String FIRST_PAGE_URL =
            "https://wger.de/api/v2/exerciseinfo/?format=json&language__code=en&limit=100&offset=0";
    // wger trả trang 100 bài khá nặng; chờ tối đa 20 giây mỗi trang
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    // Chặn vòng lặp vô hạn nếu API trả "next" lạ: 30 trang × 100 = 3000 bài, đủ dư
    private static final int MAX_PAGES = 30;
    // Mã ngôn ngữ tiếng Anh trong bảng Language của wger
    static final int ENGLISH = 2;
    private static final int MAX_DESCRIPTION = 2000;

    /**
     * Tải toàn bộ bài tập tiếng Anh.
     *
     * @return danh sách bài (không rỗng)
     * @throws WgerUnavailableException mất mạng, quá giờ, HTTP khác 200, JSON sai, hoặc không có bài nào
     */
    public List<ExerciseItem> fetchAll() throws WgerUnavailableException {
        List<ExerciseItem> all = new ArrayList<>();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // B1: đi lần lượt từng trang theo link "next" cho tới khi hết.
        String url = FIRST_PAGE_URL;
        int pages = 0;
        while (url != null && pages < MAX_PAGES) {
            String json = get(httpClient, url);
            url = parsePage(json, all);
            pages++;
        }

        // B2: không có bài nào = API trả dữ liệu lạ → coi như lỗi để Service giữ bản cũ.
        if (all.isEmpty()) {
            throw new WgerUnavailableException("wger không trả bài tập tiếng Anh nào.", null);
        }
        return all;
    }

    /** Gửi 1 GET; mã khác 200 hoặc lỗi mạng đều đổi thành WgerUnavailableException. */
    private String get(HttpClient httpClient, String url) throws WgerUnavailableException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new WgerUnavailableException("wger trả HTTP " + response.statusCode() + ".", null);
            }
            return response.body();
        } catch (IOException exception) {
            throw new WgerUnavailableException("Không gửi hoặc đọc được phản hồi từ wger.", exception);
        } catch (UncheckedIOException | IllegalArgumentException exception) {
            throw new WgerUnavailableException("Không tạo được kết nối tới wger.", exception);
        } catch (InterruptedException exception) {
            // Luồng bị yêu cầu dừng (server đang tắt): giữ dấu "đã bị ngắt" rồi báo lỗi như mọi lỗi API khác
            Thread.currentThread().interrupt();
            throw new WgerUnavailableException("Yêu cầu tới wger bị ngắt.", exception);
        }
    }

    /**
     * Đọc 1 trang JSON, thêm các bài hợp lệ vào {@code into}.
     *
     * @return link trang sau ("next"), hoặc null khi đã là trang cuối
     * @throws WgerUnavailableException JSON thiếu "results" hoặc sai kiểu
     */
    static String parsePage(String json, List<ExerciseItem> into) throws WgerUnavailableException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray results = root.getAsJsonArray("results");
            if (results == null) {
                throw new IllegalStateException("thiếu results");
            }

            // B1: mỗi phần tử → 1 bài; bài không có bản tiếng Anh hoặc không có nhóm cơ thì bỏ qua.
            for (JsonElement element : results) {
                ExerciseItem item = parseExercise(element.getAsJsonObject());
                if (item != null) {
                    into.add(item);
                }
            }

            // B2: link trang sau; null hoặc chuỗi rỗng = hết.
            JsonElement next = root.get("next");
            if (next == null || next.isJsonNull()) {
                return null;
            }
            String nextUrl = next.getAsString();
            return nextUrl.startsWith("https://") ? nextUrl : null;
        } catch (RuntimeException exception) {
            throw new WgerUnavailableException("wger trả JSON bài tập không hợp lệ.", exception);
        }
    }

    /** 1 phần tử results → ExerciseItem; thiếu tên tiếng Anh hoặc nhóm cơ → null (bỏ qua bài đó). */
    static ExerciseItem parseExercise(JsonObject node) {
        // B1: tên + mô tả tiếng Anh (language = 2).
        JsonObject english = null;
        JsonArray translations = node.getAsJsonArray("translations");
        if (translations != null) {
            for (JsonElement element : translations) {
                JsonObject translation = element.getAsJsonObject();
                JsonElement language = translation.get("language");
                boolean isEnglish = language != null && !language.isJsonNull() && language.getAsInt() == ENGLISH;
                if (isEnglish) {
                    english = translation;
                    break;
                }
            }
        }
        if (english == null || english.get("name") == null || english.get("name").isJsonNull()) {
            return null;
        }
        String name = english.get("name").getAsString().trim();
        JsonElement categoryNode = node.get("category");
        if (name.isEmpty() || categoryNode == null || !categoryNode.isJsonObject()) {
            return null;
        }

        // B2: các mã số.
        ExerciseItem item = new ExerciseItem();
        item.setExerciseId(node.get("id").getAsInt());
        item.setName(name.length() > 200 ? name.substring(0, 200) : name);
        item.setCategoryId(categoryNode.getAsJsonObject().get("id").getAsInt());
        item.setMuscleIds(readIds(node.getAsJsonArray("muscles")));
        item.setEquipmentIds(readIds(node.getAsJsonArray("equipment")));

        // B3: ảnh chính (is_main), không có thì ảnh đầu tiên; chỉ nhận link https.
        item.setImageUrl(pickImage(node.getAsJsonArray("images")));

        // B4: mô tả: bỏ thẻ HTML, gom khoảng trắng.
        JsonElement description = english.get("description");
        if (description != null && !description.isJsonNull()) {
            String text = stripHtml(description.getAsString());
            item.setDescription(text.isEmpty() ? null : text);
        }
        return item;
    }

    /** [{id:4},{id:5}] → [4, 5]; mảng null → danh sách rỗng. */
    private static List<Integer> readIds(JsonArray array) {
        List<Integer> ids = new ArrayList<>();
        if (array == null) {
            return ids;
        }
        for (JsonElement element : array) {
            ids.add(element.getAsJsonObject().get("id").getAsInt());
        }
        return ids;
    }

    /** Ảnh is_main = true, không có thì ảnh đầu; link không phải https → không dùng. */
    static String pickImage(JsonArray images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        String first = null;
        for (JsonElement element : images) {
            JsonObject image = element.getAsJsonObject();
            JsonElement link = image.get("image");
            if (link == null || link.isJsonNull()) {
                continue;
            }
            String url = link.getAsString();
            if (!url.startsWith("https://") || url.length() > 500) {
                continue;
            }
            JsonElement isMain = image.get("is_main");
            if (isMain != null && !isMain.isJsonNull() && isMain.getAsBoolean()) {
                return url;
            }
            if (first == null) {
                first = url;
            }
        }
        return first;
    }

    /**
     * "&lt;p&gt;Lie on a &lt;b&gt;flat&lt;/b&gt; bench.&lt;/p&gt;" → "Lie on a flat bench."
     * Chỉ để hiện chữ thường trên trang; JSP vẫn c:out nên dù còn ký tự lạ cũng không chạy thành mã.
     */
    static String stripHtml(String html) {
        String text = html.replaceAll("(?i)<br\\s*/?>|</p>|</li>", " ")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) : text;
    }
}
