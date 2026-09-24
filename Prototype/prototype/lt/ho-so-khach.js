/* 6.1.2 / 7.3.1 — Tạo và sửa hồ sơ khách.
   Bước 1 tra trùng số điện thoại, bước 2 mới mở form. */
(function () {
  const PHONE_RE = /^0\d{9}$/;
  const EXISTING = { '0902345678': 'Trần Bảo Ngọc · KH-1001', '0913456789': 'Lê Minh Tuấn · KH-1002' };

  /* ---------- bước 1 ---------- */
  const phoneForm = document.getElementById('phoneForm');
  const phone = document.getElementById('cfPhone');
  const dupBox = document.getElementById('dupBox');

  phoneForm.addEventListener('submit', e => {
    e.preventDefault();
    clearErr('cfPhoneErr');
    dupBox.hidden = true;
    const v = phone.value.trim();

    if (!PHONE_RE.test(v)) {
      fieldError(phone, 'cfPhoneErr', 'Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.');
      return;
    }
    if (EXISTING[v]) {
      document.getElementById('dupName').textContent = EXISTING[v];
      dupBox.hidden = false;
      return;
    }
    document.querySelectorAll('[data-cf-phone]').forEach(el => { el.textContent = formatPhoneDisplay(v); });
    showStep('form');
  });

  /* ---------- bước 2 ---------- */
  const form = document.getElementById('custForm');
  const name = document.getElementById('cfName');
  const dob = document.getElementById('cfDob');
  const email = document.getElementById('cfEmail');
  const youngBox = document.getElementById('youngBox');
  const selfOnly = [...document.querySelectorAll('[data-self]')];
  const depOnly = [...document.querySelectorAll('[data-dep]')];

  const isDep = () => document.querySelector('input[name="ctype"]:checked').value === 'dep';

  function applyType() {
    selfOnly.forEach(el => { el.hidden = isDep(); });
    depOnly.forEach(el => { el.hidden = !isDep(); });
  }
  document.querySelectorAll('input[name="ctype"]').forEach(r => r.addEventListener('change', applyType));
  applyType();

  /* mốc 10 tuổi quyết định khách có mua được gói PT hay không */
  function ageOn(dateStr) {
    if (!dateStr) return null;
    const b = new Date(dateStr), today = new Date('2026-09-20');
    let a = today.getFullYear() - b.getFullYear();
    const m = today.getMonth() - b.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < b.getDate())) a--;
    return a;
  }
  const checkAge = () => {
    const a = ageOn(dob.value);
    youngBox.hidden = !(a !== null && a < 10);
  };
  dob.addEventListener('change', checkAge);
  dob.addEventListener('input', checkAge);

  /* ---------- ảnh check-in ---------- */
  let hasPhoto = false;
  document.getElementById('camBtn').addEventListener('click', () => {
    openConfirmModal({
      title: 'Chụp ảnh check-in',
      body: `<p>Camera quầy sẽ chụp và lưu vào hồ sơ. Khách không tự đổi được ảnh này.</p>
             <p class="muted">Bắt buộc với mọi gói trừ vé ngày.</p>`,
      confirmLabel: 'Chụp và lưu',
      danger: false,
      onConfirm() {
        const box = document.getElementById('photoBox');
        box.classList.remove('is-empty');
        box.innerHTML = '<img src="https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=240&h=300&fit=crop&q=80" alt="Ảnh check-in vừa chụp">';
        hasPhoto = true;
        showToast('success', 'Đã lưu ảnh check-in.');
      },
    });
  });

  /* ---------- đề nghị xoá ---------- */
  document.getElementById('delBtn').addEventListener('click', () => {
    openConfirmModal({
      title: 'Đề nghị xoá hồ sơ',
      body: `<p>Hồ sơ vẫn hiển thị cho tới khi Quản lý duyệt. Lý do là bắt buộc.</p>
             <label class="field"><span class="field-label">Lý do xoá</span>
               <textarea id="delReason" rows="3" placeholder="Khách yêu cầu, hồ sơ trùng, sai thông tin…"></textarea>
               <span class="field-error" id="delReasonErr" hidden></span></label>`,
      confirmLabel: 'Gửi đề nghị',
      onConfirm() {
        const r = document.getElementById('delReason');
        if (!r.value.trim()) {
          fieldError(r, 'delReasonErr', 'Nhập lý do để Quản lý có căn cứ duyệt.');
          return false;
        }
        showToast('success', 'Đã gửi đề nghị xoá. Chờ Quản lý duyệt.');
      },
    });
  });

  /* ---------- lưu ---------- */
  form.addEventListener('submit', e => {
    e.preventDefault();
    ['cfNameErr', 'cfDobErr', 'cfEmailErr', 'gNameErr', 'gRelErr', 'gPhoneErr'].forEach(clearErr);
    let ok = true;

    if (name.value.trim().length < 2) { fieldError(name, 'cfNameErr', 'Nhập họ tên đầy đủ như trên giấy tờ.'); ok = false; }
    if (!dob.value) { fieldError(dob, 'cfDobErr', 'Chọn ngày sinh để kiểm tra điều kiện mua gói PT.'); ok = false; }

    if (isDep()) {
      const g = { gName: 'Nhập họ tên người giám hộ.', gRel: 'Nêu quan hệ với khách.' };
      for (const id in g) {
        const el = document.getElementById(id);
        if (!el.value.trim()) { fieldError(el, id + 'Err', g[id]); ok = false; }
      }
      const gp = document.getElementById('gPhone');
      if (!PHONE_RE.test(gp.value.trim())) { fieldError(gp, 'gPhoneErr', 'Số giám hộ phải gồm 10 chữ số và bắt đầu bằng 0.'); ok = false; }
    } else if (email.value.trim() && !EMAIL_RE.test(email.value.trim())) {
      fieldError(email, 'cfEmailErr', 'Email chưa đúng định dạng.');
      ok = false;
    } else if (document.getElementById('cfAcc').checked && !email.value.trim()) {
      fieldError(email, 'cfEmailErr', 'Cần email để gửi link đặt mật khẩu.');
      ok = false;
    }

    if (!ok) return;

    if (!hasPhoto) {
      openConfirmModal({
        title: 'Chưa có ảnh check-in',
        body: `<p>Hồ sơ lưu được, nhưng khách sẽ không vào tập được cho tới khi có ảnh. Vé ngày là ngoại lệ duy nhất.</p>`,
        confirmLabel: 'Vẫn lưu hồ sơ',
        onConfirm: save,
      });
      return;
    }
    save();
  });

  function save() {
    showToast('success', `Đã tạo hồ sơ cho ${name.value.trim()}.`);
    setTimeout(() => { location.href = 'ban-hang.html'; }, 900);
  }
})();
