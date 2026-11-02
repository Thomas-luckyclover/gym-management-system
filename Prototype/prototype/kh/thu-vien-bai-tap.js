/* #71 — Thư viện bài tập (khách). Chỉ khách có gói thành viên còn hiệu lực mới vào được;
   KH-0241 có Gói 6 Tháng nên mở. Thêm ?locked=1 để xem màn khóa (khách chỉ có Gói Ngày / buổi lẻ).
   Dữ liệu và logic lọc dùng chung với bản PT: ../exercise-data.js */
(function () {
  const UNLOCKED = new URLSearchParams(location.search).get('locked') !== '1';
  document.getElementById('exLocked').hidden = UNLOCKED;
  document.getElementById('exMain').hidden = !UNLOCKED;
  if (UNLOCKED) initExerciseLibrary();
})();
