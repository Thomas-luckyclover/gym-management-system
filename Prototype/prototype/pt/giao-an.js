/* 9.6.2 — Soạn giáo án: nháp → publish. Khách chỉ xem được sau khi publish. */
(function () {
  const CLIENTS = {"KH-0241": "Nguyễn Minh An", "KH-0318": "Trần Gia Bảo", "KH-1009": "Trịnh Quốc Bảo", "KH-0276": "Lê Thu Hà", "KH-0402": "Phạm Quốc Huy", "KH-0299": "Bùi Khánh Linh", "KH-1005": "Vũ Hoài Nam", "KH-1011": "Đỗ Lan Chi", "KH-0433": "Hoàng Đức Duy"};
  const pick = document.getElementById('clientPick');
  pick.addEventListener('change', () => {
    document.getElementById('clName').textContent = CLIENTS[pick.value];
    document.getElementById('clCode').textContent = pick.value;
  });
  const workout = document.getElementById('planWorkout');
  const diet = document.getElementById('planDiet');
  const stateBadge = document.getElementById('planState');
  const draftAt = document.getElementById('draftAt');
  const publishAt = document.getElementById('publishAt');

  const stamp = () => {
    const d = sampleNow();
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
    if (!ok) return showToast('error', 'Điền đủ cả hai tab trước khi xuất bản.');

    openConfirmModal({
      title: 'Xuất bản giáo án?',
      body: '<p>Khách sẽ xem được ngay sau khi bạn xác nhận. Ghi chú riêng của bạn không hiển thị cho khách.</p>',
      confirmLabel: 'Xuất bản',
      danger: false,
      onConfirm() {
        publishAt.textContent = stamp();
        stateBadge.className = 'badge badge-ok';
        stateBadge.textContent = 'Đã xuất bản';
        showToast('success', 'Đã xuất bản giáo án. Khách đã có thể xem.');
      },
    });
  });
})();
