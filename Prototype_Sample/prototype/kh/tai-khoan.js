/* 1.4.1 — Cài đặt tài khoản (biến thể Customer).
   Khách tự sửa: avatar · email · SĐT · ngày sinh · mật khẩu.
   Họ tên, ảnh check-in và người giám hộ vẫn do Lễ tân cập nhật (MSG-012). */
(function () {
  const USED_EMAILS = ['minh.pt@supergym.vn', 'ketoan@supergym.vn'];
  const USED_PHONES = ['0902222222', '0906222333'];
  const CURRENT = { email: 'an.nguyen@email.com', phone: '0905123456', dob: '1998-04-22' };
  const MIN_AGE_YEARS = 10;

  const emailEl = document.getElementById('acctEmail');
  const phoneEl = document.getElementById('acctPhone');
  const dobEl = document.getElementById('acctDob');
  const verifyPanel = document.getElementById('verifyPanel');
  let pending = null;

  /* ---------- lưu hồ sơ ---------- */
  document.getElementById('profileForm').addEventListener('submit', e => {
    e.preventDefault();
    const email = emailEl.value.trim().toLowerCase();
    const phone = phoneEl.value.trim();
    const dob = dobEl.value;
    let ok = true;

    if (!email) { fieldError(emailEl, 'acctEmailErr', 'Email không được để trống.'); ok = false; }
    else if (!EMAIL_RE.test(email)) { fieldError(emailEl, 'acctEmailErr', 'Email không hợp lệ.'); ok = false; }
    else if (USED_EMAILS.includes(email)) { fieldError(emailEl, 'acctEmailErr', 'Email này đã được dùng cho tài khoản khác.'); ok = false; }

    if (!phone) { fieldError(phoneEl, 'acctPhoneErr', 'Số điện thoại không được để trống.'); ok = false; }
    else if (!/^0\d{9}$/.test(phone)) { fieldError(phoneEl, 'acctPhoneErr', 'Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0).'); ok = false; }
    else if (USED_PHONES.includes(phone)) { fieldError(phoneEl, 'acctPhoneErr', 'Số điện thoại này đã tồn tại trên hồ sơ khác.'); ok = false; }

    // Khách không được sửa ngày sinh (MSG-012)
    if (dob !== CURRENT.dob) {
      fieldError(dobEl, 'acctDobErr', 'Ngày sinh chỉ được cập nhật tại quầy Lễ tân.');
      ok = false;
    }

    if (!ok) return;

    const changedEmail = email !== CURRENT.email;
    const changedPhone = phone !== CURRENT.phone;
    if (!changedEmail && !changedPhone) return showToast('info', 'Không có thay đổi nào để lưu.');

    // Đổi email hoặc SĐT đều phải nhập mã gửi tới EMAIL MỚI
    pending = { email, phone, dob, changedEmail };
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
    CURRENT.dob = pending.dob;
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
    else if (p1.value.length < 8 || p1.value.length > 64) { fieldError(p1, 'newPassErr', 'Mật khẩu phải từ 8-64 ký tự.'); ok = false; }
    else if (!/[a-zA-Z]/.test(p1.value) || !/\d/.test(p1.value)) { fieldError(p1, 'newPassErr', 'Mật khẩu phải có ít nhất 1 chữ cái và 1 chữ số.'); ok = false; }
    if (!p2.value) { fieldError(p2, 'newPass2Err', 'Vui lòng nhập lại mật khẩu mới.'); ok = false; }
    else if (p1.value && p2.value !== p1.value) { fieldError(p2, 'newPass2Err', 'Mật khẩu nhập lại không khớp.'); ok = false; }
    if (!ok) return;

    [oldP, p1, p2].forEach(i => { i.value = ''; });
    showToast('success', 'Đã lưu thay đổi. Mọi phiên đăng nhập cũ đã bị đăng xuất.');
  });
})();
