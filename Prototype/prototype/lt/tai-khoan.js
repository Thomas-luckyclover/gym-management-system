/* 1.4.1 — Cài đặt tài khoản (Lễ tân).
   Đổi email phải xác thực bằng mã gửi tới ĐỊA CHỈ MỚI. */
(function () {
  const OTP = '246810';
  const PHONE_RE = /^0\d{9}$/;

  /* ---------- ảnh đại diện (UC-04, tự đổi) ----------
     Khác hoàn toàn ảnh check-in của khách: ảnh này là của nhân viên,
     không ai duyệt, đổi lúc nào cũng được. */
  document.getElementById('avatarBtn').addEventListener('click', () => {
    openConfirmModal({
      title: 'Đổi ảnh đại diện',
      body: `<p>Ảnh hiện trên thanh trên và trong các ghi nhận thao tác của bạn.</p>
             <p class="muted">Chọn ảnh rõ mặt, tỉ lệ vuông, dưới 2MB.</p>`,
      confirmLabel: 'Chọn ảnh và lưu',
      danger: false,
      onConfirm() {
        const url = 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=120&h=120&fit=crop&q=80';
        const face = `<img src="${url}" alt="Ảnh đại diện" style="width:100%;height:100%;object-fit:cover;border-radius:50%">`;
        const big = document.getElementById('acAvatar');
        big.textContent = '';
        big.style.padding = '0';
        big.insertAdjacentHTML('beforeend', face);
        const top = document.querySelector('.cust-user .avatar');
        top.textContent = '';
        top.insertAdjacentHTML('beforeend', face);
        showToast('success', 'Đã đổi ảnh đại diện.');
      },
    });
  });

  /* ---------- email ---------- */
  const emailForm = document.getElementById('emailForm');
  const email = document.getElementById('acEmail');
  const otpBox = document.getElementById('emailOtp');
  const otp = document.getElementById('acOtp');
  const timerEl = document.getElementById('acOtpTimer');
  let timer = null;

  emailForm.addEventListener('submit', e => {
    e.preventDefault();
    clearErr('acEmailErr');
    const v = email.value.trim();

    if (!EMAIL_RE.test(v)) { fieldError(email, 'acEmailErr', 'Email chưa đúng định dạng.'); return; }
    if (v.toLowerCase() === 'hoa.nguyen@supergym.vn') {
      fieldError(email, 'acEmailErr', 'Email mới trùng email đang dùng.');
      return;
    }

    otpBox.querySelector('[data-otp-target]').textContent = maskEmail(v);
    otpBox.hidden = false;
    otp.value = '';
    otp.focus();
    startTimer();
    showToast('info', `Đã gửi mã xác thực tới ${maskEmail(v)}.`);
  });

  function startTimer() {
    clearInterval(timer);
    let left = 300;
    timerEl.textContent = mmss(left);
    timer = setInterval(() => {
      left--;
      timerEl.textContent = mmss(left);
      if (left > 0) return;
      clearInterval(timer);
      otpBox.hidden = true;
      showToast('warning', 'Mã xác thực đã hết hạn. Gửi lại mã mới.');
    }, 1000);
  }

  document.getElementById('acOtpOk').addEventListener('click', () => {
    clearErr('acOtpErr');
    if (otp.value.trim() !== OTP) {
      fieldError(otp, 'acOtpErr', 'Mã xác thực không đúng. Kiểm tra lại hộp thư mới.');
      return;
    }
    clearInterval(timer);
    const v = email.value.trim();
    emailForm.querySelector('input[disabled]').value = v;
    email.value = '';
    otpBox.hidden = true;
    showToast('success', 'Đã đổi email đăng nhập. Email cũ không còn hiệu lực.');
  });

  document.getElementById('acOtpCancel').addEventListener('click', () => {
    clearInterval(timer);
    otpBox.hidden = true;
  });

  /* ---------- số điện thoại ---------- */
  const phoneForm = document.getElementById('phoneForm');
  const phone = document.getElementById('acPhone');

  phoneForm.addEventListener('submit', e => {
    e.preventDefault();
    clearErr('acPhoneErr');
    const v = phone.value.trim();
    if (!PHONE_RE.test(v)) {
      fieldError(phone, 'acPhoneErr', 'Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.');
      return;
    }
    phoneForm.querySelector('input[disabled]').value = formatPhoneDisplay(v);
    phone.value = '';
    showToast('success', 'Đã cập nhật số điện thoại.');
  });

  /* ---------- mật khẩu ---------- */
  const pwForm = document.getElementById('pwForm');
  const pwOld = document.getElementById('pwOld');
  const pwNew = document.getElementById('pwNew');
  const pwNew2 = document.getElementById('pwNew2');

  pwForm.addEventListener('submit', e => {
    e.preventDefault();
    ['pwOldErr', 'pwNewErr', 'pwNew2Err'].forEach(clearErr);
    let ok = true;

    if (!pwOld.value) { fieldError(pwOld, 'pwOldErr', 'Nhập mật khẩu hiện tại.'); ok = false; }
    if (pwNew.value.length < 8) { fieldError(pwNew, 'pwNewErr', 'Mật khẩu mới tối thiểu 8 ký tự.'); ok = false; }
    else if (pwNew.value === pwOld.value) { fieldError(pwNew, 'pwNewErr', 'Mật khẩu mới phải khác mật khẩu cũ.'); ok = false; }
    if (pwNew2.value !== pwNew.value) { fieldError(pwNew2, 'pwNew2Err', 'Hai lần nhập chưa khớp.'); ok = false; }
    if (!ok) return;

    openConfirmModal({
      title: 'Đổi mật khẩu',
      body: '<p>Mọi phiên đăng nhập khác sẽ bị đăng xuất. Bạn vẫn giữ phiên trên máy quầy này.</p>',
      confirmLabel: 'Đổi mật khẩu',
      onConfirm() {
        pwForm.reset();
        showToast('success', 'Đã đổi mật khẩu. Các phiên đăng nhập khác đã bị đăng xuất.');
      },
    });
  });

  /* ---------- đăng xuất thiết bị khác ---------- */
  document.getElementById('logoutAll').addEventListener('click', () => {
    openConfirmModal({
      title: 'Đăng xuất mọi thiết bị khác',
      body: '<p>Dùng khi bạn đăng nhập nhầm trên máy lạ. Phiên trên máy quầy này được giữ nguyên.</p>',
      confirmLabel: 'Đăng xuất',
      onConfirm() { showToast('success', 'Đã đăng xuất mọi thiết bị khác.'); },
    });
  });
})();
