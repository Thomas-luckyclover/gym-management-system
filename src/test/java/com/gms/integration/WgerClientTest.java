package com.gms.integration;

import com.gms.dto.ExerciseItem;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Kiểm tra đọc JSON exerciseinfo của wger (cấu trúc theo ExerciseInfoSerializer, mã nguồn wger 26/10/2026).
 * Không gọi mạng: chỉ đưa chuỗi JSON mẫu vào parsePage.
 */
class WgerClientTest {

    private static final String PAGE = """
            {
              "count": 3,
              "next": "https://wger.de/api/v2/exerciseinfo/?format=json&language__code=en&limit=100&offset=100",
              "previous": null,
              "results": [
                {
                  "id": 73,
                  "category": { "id": 11, "name": "Chest" },
                  "muscles": [ { "id": 4, "name": "Pectoralis major", "name_en": "Chest" } ],
                  "muscles_secondary": [ { "id": 5, "name": "Triceps brachii", "name_en": "Triceps" } ],
                  "equipment": [ { "id": 1, "name": "Barbell" }, { "id": 8, "name": "Bench" } ],
                  "images": [
                    { "image": "https://wger.de/media/exercise-images/73/side.png", "is_main": false },
                    { "image": "https://wger.de/media/exercise-images/73/main.png", "is_main": true }
                  ],
                  "translations": [
                    { "name": "Bankdrücken", "description": "<p>Deutsch</p>", "language": 1 },
                    { "name": " Bench Press ", "description": "<p>Lie on a <b>flat</b> bench.</p><p>Press &amp; hold.</p>", "language": 2 }
                  ]
                },
                {
                  "id": 91,
                  "category": { "id": 9, "name": "Legs" },
                  "muscles": [],
                  "equipment": [],
                  "images": [],
                  "translations": [ { "name": "Kniebeuge", "description": "", "language": 1 } ]
                },
                {
                  "id": 92,
                  "category": { "id": 9, "name": "Legs" },
                  "muscles": [ { "id": 10 } ],
                  "equipment": [ { "id": 7 } ],
                  "images": [ { "image": "http://insecure.example/x.png", "is_main": true } ],
                  "translations": [ { "name": "Squat", "description": null, "language": 2 } ]
                }
              ]
            }
            """;

    @Test
    void readsEnglishNameIdsMainImageAndPlainDescription() throws WgerUnavailableException {
        List<ExerciseItem> items = new ArrayList<>();

        String next = WgerClient.parsePage(PAGE, items);

        // Bài 91 không có bản tiếng Anh → bỏ qua; còn 2 bài
        assertEquals(2, items.size());
        assertEquals("https://wger.de/api/v2/exerciseinfo/?format=json&language__code=en&limit=100&offset=100", next);

        ExerciseItem bench = items.get(0);
        assertEquals(73, bench.getExerciseId());
        assertEquals("Bench Press", bench.getName());
        assertEquals(11, bench.getCategoryId());
        assertEquals(List.of(4), bench.getMuscleIds());
        assertEquals(List.of(1, 8), bench.getEquipmentIds());
        assertEquals("https://wger.de/media/exercise-images/73/main.png", bench.getImageUrl());
        assertEquals("Lie on a flat bench. Press & hold.", bench.getDescription());

        // Ảnh http (không phải https) bị bỏ; mô tả null giữ null
        ExerciseItem squat = items.get(1);
        assertEquals("Squat", squat.getName());
        assertNull(squat.getImageUrl());
        assertNull(squat.getDescription());
    }

    @Test
    void lastPageHasNoNextLink() throws WgerUnavailableException {
        List<ExerciseItem> items = new ArrayList<>();
        String next = WgerClient.parsePage("{\"count\":0,\"next\":null,\"results\":[]}", items);
        assertNull(next);
        assertEquals(0, items.size());
    }

    @Test
    void brokenJsonIsReportedAsWgerUnavailable() {
        List<ExerciseItem> items = new ArrayList<>();
        assertThrows(WgerUnavailableException.class, () -> WgerClient.parsePage("{\"count\":1}", items));
        assertThrows(WgerUnavailableException.class, () -> WgerClient.parsePage("<html>502</html>", items));
        assertThrows(WgerUnavailableException.class,
                () -> WgerClient.parsePage("{\"results\":[{\"id\":\"x\",\"category\":{\"id\":9},"
                        + "\"translations\":[{\"name\":\"A\",\"language\":2}]}]}", items));
    }
}
