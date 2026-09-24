/* 1.4.1 — Cài đặt tài khoản (biến thể PT).
   PT tự sửa: avatar · email · SĐT · mật khẩu.
   Họ tên và ngày sinh thuộc hồ sơ nhân viên, do Quản lý cập nhật. */
(function () {
  const USED_EMAILS = ['trang.pham@supergym.vn', 'ketoan@supergym.vn'];
  const USED_PHONES = ['0902222222', '0906222333'];
  const CURRENT = { email: 'long.hoang@supergym.vn', phone: '0908777111' };

  const emailEl = document.getElementById('acctEmail');
  const phoneEl = document.getElementById('acctPhone');
  const verifyPanel = document.getElementById('verifyPanel');
  let pending = null;

  /* ---------- lưu hồ sơ ---------- */
  document.getElementById('profileForm').addEventListener('submit', e => {
    e.preventDefault();
    const email = emailEl.value.trim().toLowerCase();
    const phone = phoneEl.value.trim();
    let ok = true;

    if (!email) { fieldError(emailEl, 'acctEmailErr', 'Email không được để trống.'); ok = false; }
    else if (!EMAIL_RE.test(email)) { fieldError(emailEl, 'acctEmailErr', 'Email không hợp lệ.'); ok = false; }
    else if (USED_EMAILS.includes(email)) { fieldError(emailEl, 'acctEmailErr', 'Email này đã được dùng cho tài khoản khác.'); ok = false; }

    if (!phone) { fieldError(phoneEl, 'acctPhoneErr', 'Số điện thoại không được để trống.'); ok = false; }
    else if (!/^0\d{9}$/.test(phone)) { fieldError(phoneEl, 'acctPhoneErr', 'Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0).'); ok = false; }
    else if (USED_PHONES.includes(phone)) { fieldError(phoneEl, 'acctPhoneErr', 'Số điện thoại này đã tồn tại trên hồ sơ khác.'); ok = false; }

    if (!ok) return;

    const changedEmail = email !== CURRENT.email;
    const changedPhone = phone !== CURRENT.phone;
    if (!changedEmail && !changedPhone) return showToast('info', 'Không có thay đổi nào để lưu.');

    // Đổi email hoặc SĐT đều phải nhập mã gửi tới EMAIL MỚI
    pending = { email, phone, changedEmail };
    document.getElementById('verifyTarget').textContent = maskEmail(email);
    verifyPanel.hidden = false;
    verifyPanel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    showToast('success', 'Đã gửi mã xác thực tới ' + maskEmail(email) + '.');
  });

  /* ---------- xác thực mã ---------- */
  document.getElementById('verifyForm').addEventListener('submit', e => {
    e.preventDefault();
    const input = document.getElementById('verifyCode');
    const v = input.value.trim();
    if (!v) return fieldError(input, 'verifyCodeErr', 'Vui lòng nhập mã xác thực.');
    if (v !== '123456') return fieldError(input, 'verifyCodeErr', 'Mã xác thực không đúng hoặc đã hết hạn.');

    const wasEmail = pending.changedEmail;
    CURRENT.email = pending.email;
    CURRENT.phone = pending.phone;
    pending = null;
    verifyPanel.hidden = true;

    showToast('success', wasEmail
      ? 'Đổi email thành công. Đã gửi thông báo tới email cũ; mọi phiên đăng nhập đã bị đăng xuất.'
      : 'Đã lưu thay đổi.');
  });

  /* ---------- đổi mật khẩu ---------- */
  document.getElementById('passwordForm').addEventListener('submit', e => {
    e.preventDefault();
    const oldP = document.getElementById('oldPass');
    const p1 = document.getElementById('newPass');
    const p2 = document.getElementById('newPass2');
    let ok = true;

    if (!oldP.value) { fieldError(oldP, 'oldPassErr', 'Mật khẩu hiện tại không được để trống.'); ok = false; }
    if (!p1.value) { fieldError(p1, 'newPassErr', 'Mật khẩu mới không được để trống.'); ok = false; }
    else if (p1.value.length < 6) { fieldError(p1, 'newPassErr', 'Mật khẩu tối thiểu 6 ký tự.'); ok = false; }
    if (!p2.value) { fieldError(p2, 'newPass2Err', 'Vui lòng nhập lại mật khẩu mới.'); ok = false; }
    else if (p1.value && p2.value !== p1.value) { fieldError(p2, 'newPass2Err', 'Mật khẩu nhập lại không khớp.'); ok = false; }
    if (!ok) return;

    [oldP, p1, p2].forEach(i => { i.value = ''; });
    showToast('success', 'Đã lưu thay đổi. Mọi phiên đăng nhập cũ đã bị đăng xuất.');
  });

  /* ---------- ảnh đại diện (UC-04, tự đổi, không cần ai duyệt) ---------- */
  document.getElementById('avatarBtn').addEventListener('click', () => {
    openConfirmModal({
      title: 'Đổi ảnh đại diện',
      body: `<p>Ảnh hiện trên thanh trên và trong hồ sơ PT mà khách nhìn thấy.</p>
             <p class="muted">Chọn ảnh rõ mặt, tỉ lệ vuông, dưới 2MB.</p>`,
      confirmLabel: 'Chọn ảnh và lưu',
      danger: false,
      onConfirm() {
        const url = 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=120&h=120&fit=crop&q=80';
        const face = `<img src="${url}" alt="Ảnh đại diện" style="width:100%;height:100%;object-fit:cover;border-radius:50%">`;
        for (const el of [document.getElementById('acAvatar'), document.querySelector('.cust-user .avatar')]) {
          if (!el) continue;
          el.textContent = '';
          el.style.padding = '0';
          el.insertAdjacentHTML('beforeend', face);
        }
        showToast('success', 'Đã đổi ảnh đại diện.');
      },
    });
  });
})();
