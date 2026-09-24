/* 9.6.2 — Soạn giáo án: nháp → publish. Khách chỉ xem được sau khi publish. */
(function () {
  const workout = document.getElementById('planWorkout');
  const diet = document.getElementById('planDiet');
  const stateBadge = document.getElementById('planState');
  const draftAt = document.getElementById('draftAt');
  const publishAt = document.getElementById('publishAt');

  const stamp = () => {
    const d = new Date();
    const p = n => String(n).padStart(2, '0');
    return `${p(d.getDate())}/${p(d.getMonth() + 1)} · ${p(d.getHours())}:${p(d.getMinutes())}`;
  };

  document.getElementById('saveDraft').addEventListener('click', () => {
    draftAt.textContent = stamp();
    showToast('success', 'Đã lưu nháp. Khách chưa xem được.');
  });

  document.getElementById('publishPlan').addEventListener('click', () => {
    let ok = true;
    if (!workout.value.trim()) { fieldError(workout, 'planWorkoutErr', 'Giáo án tập không được để trống.'); ok = false; }
    if (!diet.value.trim()) { fieldError(diet, 'planDietErr', 'Thực đơn không được để trống.'); ok = false; }
    if (!ok) return showToast('error', 'Điền đủ cả hai tab trước khi publish.');

    openConfirmModal({
      title: 'Xuất bản giáo án?',
      body: '<p>Khách sẽ xem được ngay sau khi bạn xác nhận. Ghi chú riêng của bạn không hiển thị cho khách.</p>',
      confirmLabel: 'Publish',
      danger: false,
      onConfirm() {
        publishAt.textContent = stamp();
        stateBadge.className = 'badge badge-ok';
        stateBadge.textContent = 'Đã publish';
        showToast('success', 'Đã xuất bản giáo án. Khách đã có thể xem.');
      },
    });
  });
})();
