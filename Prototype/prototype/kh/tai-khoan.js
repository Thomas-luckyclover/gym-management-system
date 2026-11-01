/* #4 Cài đặt tài khoản — một màn cho mọi vai trò (hồ sơ · mật khẩu · hồ sơ phụ thuộc).
   Họ tên, ngày sinh và dữ liệu nhân sự chỉ xem. Đổi email: mã gửi tới email MỚI.
   Đổi số điện thoại: mã gửi tới email HIỆN TẠI. Mã 6 chữ số, hiệu lực 15 phút (P-15). */
(function () {
  const USED_EMAILS = ['an.nguyen@email.com', 'hoa.nt@gms.vn', 'long.h@gms.vn', 'trang.pt@gms.vn'];
  const USED_PHONES = ['0905123456', '0356789012', '0978123456', '0934567890'];
  const CURRENT = { email: 'an.nguyen@email.com', phone: '0905123456' };
  const CODE_TTL = 15 * 60;

  const emailEl = document.getElementById('acctEmail');
  const phoneEl = document.getElementById('acctPhone');
  const verifyPanel = document.getElementById('verifyPanel');
  const verifyBtn = document.getElementById('verifyBtn');
  const verifyCode = document.getElementById('verifyCode');
  const countdown = document.getElementById('verifyCountdown');
  let pending = null;
  let left = 0;
  let timer = null;

  function startCountdown() {
    clearInterval(timer);
    left = CODE_TTL;
    const tick = () => {
      countdown.textContent = mmss(left);
      if (left <= 0) {
        clearInterval(timer);
        fieldError(verifyCode, 'verifyCodeErr', 'Mã xác thực đã hết hạn. Vui lòng gửi lại mã mới.');
      }
      left--;
    };
    tick();
    timer = setInterval(tick, 1000);
  }

  function sendCode(kind, to, value) {
    pending = { kind, value };
    clearErr('verifyCodeErr');
    verifyCode.value = '123456';
    document.getElementById('verifyTarget').textContent = maskEmail(to);
    verifyBtn.textContent = kind === 'email' ? 'Xác nhận email' : 'Xác nhận số điện thoại';
    verifyPanel.hidden = false;
    verifyPanel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    startCountdown();
    showToast('success', 'Đã gửi mã xác thực tới ' + maskEmail(to) + '.');
  }

  /* ---------- đổi email ---------- */
  document.getElementById('emailForm').addEventListener('submit', e => {
    e.preventDefault();
    const email = emailEl.value.trim().toLowerCase();
    if (!email) return fieldError(emailEl, 'acctEmailErr', 'Email không được để trống.');
    if (!EMAIL_RE.test(email)) return fieldError(emailEl, 'acctEmailErr', 'Email không hợp lệ.');
    if (USED_EMAILS.includes(email)) return fieldError(emailEl, 'acctEmailErr', 'Email này đã được dùng cho tài khoản khác.');
    if (email === CURRENT.email) return showToast('info', 'Không có thay đổi nào để lưu.');
    clearErr('acctEmailErr');
    sendCode('email', email, email);
  });

  /* ---------- đổi số điện thoại ---------- */
  document.getElementById('phoneForm').addEventListener('submit', e => {
    e.preventDefault();
    const phone = phoneEl.value.trim();
    if (!phone) return fieldError(phoneEl, 'acctPhoneErr', 'Số điện thoại không được để trống.');
    if (!/^0\d{9}$/.test(phone)) return fieldError(phoneEl, 'acctPhoneErr', 'Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0).');
    if (USED_PHONES.includes(phone)) return fieldError(phoneEl, 'acctPhoneErr', 'Số điện thoại này đã tồn tại trên hồ sơ khác.');
    if (phone === CURRENT.phone) return showToast('info', 'Không có thay đổi nào để lưu.');
    clearErr('acctPhoneErr');
    sendCode('phone', CURRENT.email, phone);
  });

  /* ---------- xác thực mã ---------- */
  document.getElementById('verifyForm').addEventListener('submit', e => {
    e.preventDefault();
    const v = verifyCode.value.trim();
    if (!v) return fieldError(verifyCode, 'verifyCodeErr', 'Vui lòng nhập mã xác thực.');
    if (left < 0) return fieldError(verifyCode, 'verifyCodeErr', 'Mã xác thực đã hết hạn. Vui lòng gửi lại mã mới.');
    if (!/^\d{6}$/.test(v) || v !== '123456') return fieldError(verifyCode, 'verifyCodeErr', 'Mã xác thực không đúng hoặc đã hết hạn.');
    clearInterval(timer);
    verifyPanel.hidden = true;
    if (pending.kind === 'email') {
      CURRENT.email = pending.value;
      showToast('success', 'Đổi email thành công. Đã gửi thông báo tới email cũ; mọi phiên đăng nhập đã bị đăng xuất.');
    } else {
      CURRENT.phone = pending.value;
      showToast('success', 'Đổi số điện thoại thành công.');
    }
    pending = null;
  });
  document.getElementById('verifyResend').addEventListener('click', () => {
    if (!pending) return;
    sendCode(pending.kind, pending.kind === 'email' ? pending.value : CURRENT.email, pending.value);
  });
  document.getElementById('verifyCancel').addEventListener('click', () => {
    clearInterval(timer);
    pending = null;
    verifyPanel.hidden = true;
    emailEl.value = CURRENT.email;
    phoneEl.value = CURRENT.phone;
  });

  /* ---------- ảnh đại diện ---------- */
  const file = document.getElementById('avatarFile');
  const saveBtn = document.getElementById('btnSaveAvatar');
  document.getElementById('btnSelectAvatar').addEventListener('click', () => file.click());
  file.addEventListener('change', () => {
    const f = file.files[0];
    if (!f) return;
    if (!/^image\/(jpeg|png)$/.test(f.type)) { file.value = ''; return showToast('error', 'Chỉ nhận ảnh JPG hoặc PNG.'); }
    if (f.size > 2 * 1024 * 1024) { file.value = ''; return showToast('error', 'Ảnh vượt quá 2 MB.'); }
    saveBtn.disabled = false;
  });
  saveBtn.addEventListener('click', () => { saveBtn.disabled = true; showToast('success', 'Đã lưu ảnh đại diện.'); });
  document.getElementById('btnRemoveAvatar').addEventListener('click', () => {
    openConfirmModal({
      title: 'Xóa ảnh đại diện?',
      body: '<p>Ảnh sẽ được thay bằng chữ cái đầu tên.</p>',
      confirmLabel: 'Xóa ảnh',
      onConfirm() { file.value = ''; saveBtn.disabled = true; showToast('success', 'Đã xóa ảnh đại diện.'); },
    });
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
