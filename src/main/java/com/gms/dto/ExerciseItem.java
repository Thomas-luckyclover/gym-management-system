package com.gms.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * MỘT BÀI TẬP trong Thư viện bài tập (/customer/exercises, /pt/exercises).
 *
 *   Ai tạo?   WgerClient.parsePage (đọc JSON wger.de khi đồng bộ) hoặc ExerciseLibraryDAO.findPage (đọc bảng Exercises).
 *   Ai điền?  ExerciseLibraryService điền 3 nhãn tiếng Việt: categoryLabel, equipmentText, muscleText.
 *   Ai in?    _exercise-library.jspf, mỗi bài là một thẻ trong lưới.
 */
public class ExerciseItem {

    // wger: exerciseinfo.id = bảng Exercises.ExerciseId. Không in ra trang.
    private int exerciseId;

    // wger: translations[language = 2 (tiếng Anh)].name, ví dụ "Bench Press". In làm tiêu đề thẻ.
    private String name;

    // wger: category.id (8 Arms … 15 Cardio). Service đổi sang chữ tiếng Việt ở categoryLabel.
    private int categoryId;

    // wger: muscles[].id (cơ chính). DB lưu dạng ",4,5,". Service đổi sang muscleText.
    private List<Integer> muscleIds = new ArrayList<>();

    // wger: equipment[].id. DB lưu dạng ",1,8,". Service đổi sang equipmentText.
    private List<Integer> equipmentIds = new ArrayList<>();

    // wger: images[] ảnh is_main (không có thì ảnh đầu). null = không có ảnh → JSP vẽ khung trống.
    private String imageUrl;

    // wger: translations[].description đã bỏ thẻ HTML. Hiện trong phần "Cách tập" khi mở thẻ.
    private String description;

    // Service: tên nhóm cơ tiếng Việt, ví dụ 11 → "Ngực".
    private String categoryLabel;

    // Service: dụng cụ tiếng Việt nối bằng ", ", ví dụ "Thanh đòn, Ghế tập". Không có → "Không dụng cụ".
    private String equipmentText;

    // Service: cơ chính tiếng Việt nối bằng ", ", ví dụ "Ngực, Tay sau". Không có → chuỗi rỗng (JSP ẩn dòng).
    private String muscleText;

    // Mã bài tập wger
    public int getExerciseId() {
        return exerciseId;
    }

    // Gán mã bài tập (client hoặc DAO)
    public void setExerciseId(int exerciseId) {
        this.exerciseId = exerciseId;
    }

    // Tên bài tiếng Anh
    public String getName() {
        return name;
    }

    // Gán tên bài
    public void setName(String name) {
        this.name = name;
    }

    // Mã nhóm cơ wger
    public int getCategoryId() {
        return categoryId;
    }

    // Gán mã nhóm cơ
    public void setCategoryId(int categoryId) {
        this.categoryId = categoryId;
    }

    // Danh sách mã cơ chính
    public List<Integer> getMuscleIds() {
        return muscleIds;
    }

    // Gán danh sách mã cơ chính
    public void setMuscleIds(List<Integer> muscleIds) {
        this.muscleIds = muscleIds;
    }

    // Danh sách mã dụng cụ
    public List<Integer> getEquipmentIds() {
        return equipmentIds;
    }

    // Gán danh sách mã dụng cụ
    public void setEquipmentIds(List<Integer> equipmentIds) {
        this.equipmentIds = equipmentIds;
    }

    // Link ảnh minh họa trên wger.de (có thể null)
    public String getImageUrl() {
        return imageUrl;
    }

    // Gán link ảnh
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    // Mô tả cách tập (tiếng Anh, chữ thường, có thể null)
    public String getDescription() {
        return description;
    }

    // Gán mô tả
    public void setDescription(String description) {
        this.description = description;
    }

    // Nhóm cơ tiếng Việt cho thẻ
    public String getCategoryLabel() {
        return categoryLabel;
    }

    // Gán nhóm cơ tiếng Việt (Service)
    public void setCategoryLabel(String categoryLabel) {
        this.categoryLabel = categoryLabel;
    }

    // Dụng cụ tiếng Việt cho thẻ
    public String getEquipmentText() {
        return equipmentText;
    }

    // Gán dụng cụ tiếng Việt (Service)
    public void setEquipmentText(String equipmentText) {
        this.equipmentText = equipmentText;
    }

    // Cơ chính tiếng Việt cho thẻ
    public String getMuscleText() {
        return muscleText;
    }

    // Gán cơ chính tiếng Việt (Service)
    public void setMuscleText(String muscleText) {
        this.muscleText = muscleText;
    }
}
