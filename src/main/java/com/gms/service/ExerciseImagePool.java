package com.gms.service;

import com.gms.dto.ExerciseItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Kho ẢNH MINH HỌA cho bài tập wger KHÔNG có ảnh (31/10/2026, thay 8 ảnh chung theo nhóm cơ — cũ lặp quá nhiều).
 *
 * NGUỒN: 194 ảnh từ free-exercise-db (public domain), đã chọn tay (ảnh ngang, đủ sáng, phòng tập), thu nhỏ 640px,
 * lưu ở webapp/images/exercises/library/&lt;nhóm&gt;/. Danh sách: src/main/resources/exercise-images.json
 * dạng {"11": [{"file": "/images/exercises/library/chest/...jpg", "name": "Barbell Bench Press - Medium Grip"}, …]}
 * (khóa = mã nhóm cơ wger: 11 Ngực, 12 Lưng, 13 Vai, 8 Tay, 10 Bụng, 9 Chân, 14 Bắp chân, 15 Cardio).
 *
 * AI GỌI: ExerciseLibraryService.getPage, sau fillLabels, cho 12 bài của 1 trang.
 * CÁCH CHỌN mỗi bài thiếu ảnh:
 *   1. Khớp TÊN bài (tiếng Anh, bỏ dấu câu, không phân biệt hoa thường) với tên ảnh trong kho → đúng động tác,
 *      JSP ghi "Ảnh minh họa" (fallbackExact = true).
 *   2. Không khớp → ảnh trong kho của CÙNG nhóm cơ; vị trí bắt đầu tính từ ExerciseId (cùng bài luôn cùng ảnh khi
 *      không bị trùng), gặp ảnh đã dùng trên trang thì lấy ảnh kế tiếp → 12 bài trên 1 trang không trùng ảnh.
 *      JSP ghi "Ảnh minh họa nhóm …".
 */
final class ExerciseImagePool {

    private static final Logger LOGGER = Logger.getLogger(ExerciseImagePool.class.getName());
    private static final String MANIFEST = "/exercise-images.json";

    // Nạp 1 lần khi lớp được dùng lần đầu. Lỗi đọc file → kho rỗng, bài thiếu ảnh hiện "Chưa có hình minh họa" (không chặn trang).
    private static final Map<Integer, List<String>> POOL = new LinkedHashMap<>();
    private static final Map<String, String> BY_NAME = new LinkedHashMap<>();

    static {
        load();
    }

    private ExerciseImagePool() {
    }

    /** 1 dòng trong exercise-images.json. */
    private static final class Entry {
        String file;
        String name;
    }

    private static void load() {
        try (InputStream in = ExerciseImagePool.class.getResourceAsStream(MANIFEST)) {
            if (in == null) {
                LOGGER.warning("Không thấy " + MANIFEST + " — bài thiếu ảnh wger sẽ không có ảnh minh họa");
                return;
            }
            Type type = new TypeToken<Map<String, List<Entry>>>() { }.getType();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                Map<String, List<Entry>> raw = new Gson().fromJson(reader, type);
                for (Map.Entry<String, List<Entry>> group : raw.entrySet()) {
                    List<String> files = new ArrayList<>();
                    for (Entry entry : group.getValue()) {
                        files.add(entry.file);
                        BY_NAME.putIfAbsent(normalize(entry.name), entry.file);
                    }
                    POOL.put(Integer.parseInt(group.getKey()), Collections.unmodifiableList(files));
                }
            }
        } catch (Exception e) {   // file hỏng / sai định dạng: chỉ mất ảnh minh họa, không làm hỏng trang
            LOGGER.log(Level.WARNING, "Đọc " + MANIFEST + " lỗi", e);
        }
    }

    /**
     * Gán ảnh minh họa cho các bài THIẾU ảnh wger trong 1 trang; bài đã có ảnh wger giữ nguyên.
     * @param items 12 bài của trang (đã fillLabels)
     */
    static void assign(List<ExerciseItem> items) {
        Set<String> usedOnPage = new HashSet<>();
        // B1: ưu tiên bài khớp tên trước, để ảnh đúng động tác không bị bài khác (lấy theo nhóm) chiếm mất
        List<ExerciseItem> rest = new ArrayList<>();
        for (ExerciseItem item : items) {
            if (item.getImageUrl() != null) {
                continue;
            }
            String exact = byName(item.getName());
            if (exact != null && usedOnPage.add(exact)) {
                item.setFallbackImage(exact);
                item.setFallbackExact(true);
            } else {
                rest.add(item);
            }
        }
        // B2: còn lại lấy theo nhóm cơ, né ảnh đã dùng trên trang
        for (ExerciseItem item : rest) {
            String file = byCategory(item.getCategoryId(), item.getExerciseId(), usedOnPage);
            if (file != null) {
                usedOnPage.add(file);
                item.setFallbackImage(file);
                item.setFallbackExact(false);
            }
        }
    }

    /** Tên bài wger → ảnh cùng tên trong kho; khớp đúng tên trước, rồi tên này chứa tên kia (dài nhất). Không có → null. */
    static String byName(String exerciseName) {
        String key = normalize(exerciseName);
        if (key.isEmpty()) {
            return null;
        }
        String exact = BY_NAME.get(key);
        if (exact != null) {
            return exact;
        }
        String best = null;
        int bestLength = 0;
        for (Map.Entry<String, String> entry : BY_NAME.entrySet()) {
            String name = entry.getKey();
            // Chỉ nhận tên đủ dài (≥ 2 từ) để "Squat" không khớp nhầm mọi bài có chữ squat.
            boolean contains = name.contains(" ") && (key.contains(name) || name.contains(key) && key.contains(" "));
            if (contains && name.length() > bestLength) {
                best = entry.getValue();
                bestLength = name.length();
            }
        }
        return best;
    }

    /**
     * Ảnh theo nhóm cơ: bắt đầu ở vị trí tính từ exerciseId, bỏ qua ảnh đã dùng trên trang.
     * Nhóm lạ (wger thêm sau) → null. Kho của nhóm đã dùng hết trên trang → chấp nhận trùng (vị trí bắt đầu).
     */
    static String byCategory(int categoryId, int exerciseId, Set<String> usedOnPage) {
        List<String> files = POOL.get(categoryId);
        if (files == null || files.isEmpty()) {
            return null;
        }
        // Nhân số lẻ lớn để 2 bài có mã liền nhau không rơi vào 2 ảnh liền nhau (ảnh liền nhau thường cùng dụng cụ).
        int start = Math.floorMod(exerciseId * 7919, files.size());
        for (int step = 0; step < files.size(); step++) {
            String file = files.get((start + step) % files.size());
            if (!usedOnPage.contains(file)) {
                return file;
            }
        }
        return files.get(start);
    }

    /** "Barbell Bench Press - Medium Grip" → "barbell bench press medium grip". */
    static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Số ảnh của 1 nhóm cơ (để test). */
    static int poolSize(int categoryId) {
        List<String> files = POOL.get(categoryId);
        return files == null ? 0 : files.size();
    }
}
