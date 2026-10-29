# -*- coding: utf-8 -*-
"""
fetch_exercise_images.py — đề xuất ảnh free-exercise-db cho các bài wger đang thiếu ảnh,
rồi in SQL ứng viên để kiểm tra thủ công; script không tự kết nối hoặc sửa database.
Khớp tên gần giống có thể nhầm động tác, dụng cụ hoặc một/hai tay: cần xem ảnh trước khi áp dụng.

NGUỒN ẢNH: free-exercise-db (github.com/yuhonas/free-exercise-db) — public domain, ~1000+ ảnh bài tập.
Mỗi bài có 1 tên + danh sách ảnh (0.jpg, 1.jpg). Mình lấy ảnh đầu (0.jpg).

CÁCH DÙNG (3 bước):
  1) Xuất danh sách bài thiếu ảnh từ DB ra CSV (mỗi dòng: <ExerciseId>,<Name>):
       sqlcmd -S localhost -E -d GMS -W -h -1 -Q "SET NOCOUNT ON; SELECT CAST(ExerciseId AS varchar)+','+Name FROM Exercises WHERE ImageUrl IS NULL" -o exercises_no_image.csv
  2) Chạy script, xuất ra file SQL:
       python fetch_exercise_images.py exercises_no_image.csv > update_images.sql
  3) Kiểm tra từng cặp bài/ảnh, bỏ các dòng không đúng động tác, rồi mới chạy SQL đã duyệt:
       sqlcmd -S localhost -E -d GMS -i update_images.sql

CƠ CHẾ KHỚP TÊN (tránh trùng lặp):
  - Chuẩn hóa tên: chữ thường, bỏ dấu câu (VD "Bench Press" → "bench press").
  - Thử 3 mức: (1) khớp ĐÚNG hệt → (2) tên này chứa tên kia → (3) trùng nhiều từ nhất.
  - Mỗi ảnh free-exercise-db CHỈ DÙNG 1 LẦN (tập 'used') → 2 bài wger không bao giờ lấy cùng 1 ảnh.
"""
import csv
import difflib
import json
import re
import sys
import urllib.request

# URL danh sách bài tập của free-exercise-db (JSON).
FREE_DB_JSON = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/dist/exercises.json"
# Gốc URL tải ảnh (ảnh nằm trong thư mục exercises/ của repo).
FREE_DB_RAW = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"


def normalize(name):
    """Chuẩn hóa tên để so khớp: chữ thường, chỉ giữ chữ cái + số + khoảng trắng, gộp khoảng trắng thừa."""
    text = name.lower()
    text = re.sub(r"[^a-z0-9\s]", " ", text)   # bỏ dấu câu (đổi thành khoảng trắng)
    return re.sub(r"\s+", " ", text).strip()


def load_free_db():
    """Tải exercises.json của free-exercise-db → dict {tên_chuẩn_hóa: (tên_gốc, đường_dẫn_ảnh_đầu)}."""
    with urllib.request.urlopen(FREE_DB_JSON, timeout=60) as response:
        data = json.loads(response.read().decode("utf-8"))

    result = {}
    for item in data:
        images = item.get("images") or []
        if not images:
            continue                              # bài không có ảnh → bỏ qua
        key = normalize(item["name"])
        if key and key not in result:             # trùng tên (sau chuẩn hóa) → giữ bài đầu tiên
            result[key] = (item["name"], images[0])
    return result


def read_wger_csv(path):
    """Đọc CSV 'ExerciseId,Name' → danh sách (id, name).
    Dùng latin-1 (đọc lossless, không bao giờ lỗi) — file xuất từ sqlcmd mã hóa theo codepage Windows.
    Ký tự lạ sẽ bị normalize() bỏ đi sau đó."""
    rows = []
    with open(path, encoding="latin-1") as file:
        for line in file:
            line = line.strip()
            if not line or "," not in line:
                continue
            exercise_id, name = line.split(",", 1)
            rows.append((exercise_id.strip(), name.strip()))
    return rows


def words(name):
    """(không còn dùng) Tách tên thành tập hợp các từ."""
    return set(name.split())


def best_match(wger_name, free_map, used):
    """Tìm ảnh free-exercise-db khớp nhất với tên wger. Trả (tên_gốc, đường_dẫn_ảnh) hoặc None."""
    key = normalize(wger_name)
    if not key:
        return None

    # Mức 1: khớp đúng hệt (chuẩn hóa xong giống nhau).
    if key in free_map and key not in used:
        return free_map[key]

    # Mức 2: tên này chứa tên kia (VD wger "Bench Press" ⊂ free "Barbell Bench Press - Medium Grip").
    for free_key, (free_name, img) in free_map.items():
        if free_key in used:
            continue
        if key in free_key or free_key in key:
            return (free_name, img)

    # Mức 3: đo độ tương đồng chuỗi (difflib.SequenceMatcher). Chỉ nhận khi đủ giống (>= 0.6)
    # Ngưỡng chuỗi không xác nhận đúng động tác; kết quả chỉ là ứng viên cần xem lại bằng mắt.
    best, best_ratio = None, 0.0
    for free_key, (free_name, img) in free_map.items():
        if free_key in used:
            continue
        ratio = difflib.SequenceMatcher(None, key, free_key).ratio()
        if ratio > best_ratio:
            best, best_ratio = (free_name, img), ratio
    return best if best_ratio >= 0.6 else None


def main():
    if len(sys.argv) < 2:
        print("Cách dùng: python fetch_exercise_images.py <exercises_no_image.csv>")
        sys.exit(1)

    # In UTF-8 ra console/pipe (mặc định Windows dùng codepage khác → in ký tự lạ dễ lỗi).
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

    print("-- Đang tải danh sách bài tập free-exercise-db ...", file=sys.stderr)
    free_map = load_free_db()
    print(f"-- free-exercise-db có {len(free_map)} bài có ảnh.", file=sys.stderr)

    wger_rows = read_wger_csv(sys.argv[1])
    print(f"-- {len(wger_rows)} bài wger đang thiếu ảnh.", file=sys.stderr)

    used = set()          # tên free-exercise-db đã dùng (chống trùng 1-1)
    matched = 0
    for exercise_id, name in wger_rows:
        hit = best_match(name, free_map, used)
        if not hit:
            continue
        free_name, img_path = hit
        used.add(normalize(free_name))
        matched += 1
        # Ảnh lấy từ GitHub raw (public domain). Ảnh đầu = 0.jpg (đã là images[0]).
        url = FREE_DB_RAW + img_path
        # SQL: gắn ảnh vào bài. N'(tên gốc)' để không lỗi ký tự lạ.
        print(f"UPDATE Exercises SET ImageUrl = '{url}' WHERE ExerciseId = {exercise_id}; -- {name} -> {free_name}")

    print(f"-- Khớp được {matched}/{len(wger_rows)} bài. Còn lại giữ ảnh fallback theo nhóm cơ.", file=sys.stderr)


if __name__ == "__main__":
    main()
