/* =====================================================================
   Đăng ký tài khoản: form gộp tất cả trường, check SĐT + email + OTP
   ===================================================================== */

/* --- dữ liệu mẫu để tra số điện thoại --- */
const PHONE_DB = {
  '0901111111': { type: 'has_account' },
  '0902222222': { type: 'profile_with_email', email: 'lan.tran@gmail.com' },
  '0903333333': { type: 'profile_no_email' },
  '0904444444': { type: 'deleted_profile' },
};
const USED_EMAILS = ['minh.pt@supergym.vn', 'ketoan@supergym.vn'];
const OTP_CODE = '123456';

const state = { 
  step: 'phone', 
  phone: '', 
  email: '',
  dirty: false,
  otpAttempts: 0,
  otpSendCount: 0,
  otpSendTimes: [],
  otpLockUntil: null,
  resendLockUntil: null
};

/* ---------- chuyển bước ---------- */
function goStep(step) {
  state.step = step;
  if (step === 'success') state.dirty = false;
  showStep(step);
}

function setPhone(phone) {
  state.phone = phone;
  document.querySelectorAll('[data-phone-display]')
    .forEach(el => { el.textContent = formatPhoneDisplay(phone); });
}

function setProfileEmail(email) {
  state.profileEmail = email;
  document.querySelectorAll('[data-profile-email]')
    .forEach(el => { el.textContent = maskEmail(email); });
}

function setSuccessMode(mode) {
  document.querySelectorAll('[data-success-mode]')
    .forEach(el => { el.hidden = el.dataset.successMode !== mode; });
}

/* ---------- "Dùng số khác" đưa về form ---------- */
document.addEventListener('click', e => {
  const trigger = e.target.closest('[data-reset-phone]');
  if (!trigger) return;
  e.preventDefault();
  state.dirty = false;
  state.profileEmail = '';
  goStep('phone');
});

const retryBtn = document.getElementById('retryEmailBtn');
if (retryBtn) {
  retryBtn.addEventListener('click', () => goStep('form'));
}

/* ---------- Bước 1: form nhập SĐT, tra trùng trước ---------- */
const phoneForm = document.getElementById('phoneForm');
const regPhone = document.getElementById('regPhone');

phoneForm.addEventListener('submit', e => {
  e.preventDefault();

  const phone = regPhone.value.replace(/\s/g, '');
  if (!phone) { fieldError(regPhone, 'regPhoneErr', 'Vui lòng nhập số điện thoại.'); return; }
  if (!/^0\d{9}$/.test(phone)) {
    fieldError(regPhone, 'regPhoneErr', 'Số điện thoại không hợp lệ (10 số, bắt đầu bằng 0).');
    return;
  }
  clearErr('regPhoneErr');

  state.phone = phone;
  setPhone(phone);

  // Check trùng SĐT
  const info = PHONE_DB[phone];
  if (info) {
    if (info.type === 'has_account') { goStep('blocked-account'); return; }
    if (info.type === 'profile_no_email') { goStep('blocked-no-email'); return; }
    if (info.type === 'profile_with_email') {
      setProfileEmail(info.email);
      goStep('blocked-email-mismatch');
      return;
    }
  }

  goStep('form');
});

/* ---------- Bước 2: form đăng ký đầy đủ ---------- */
const regForm = document.getElementById('regForm');
const regEmail = document.getElementById('regEmail');
const regPass = document.getElementById('regPass');
const regPass2 = document.getElementById('regPass2');
const regName = document.getElementById('regName');
const regDob = document.getElementById('regDob');
const regSubmitBtn = document.getElementById('regSubmitBtn');

regForm.addEventListener('input', () => { state.dirty = true; });

regForm.addEventListener('submit', e => {
  e.preventDefault();
  let ok = true;

  // Nếu hồ sơ có email, phải khớp thì mới cho tạo tài khoản
  const phone = state.phone;
  const info = PHONE_DB[phone];
  if (info && info.type === 'profile_with_email') {
    const inputEmail = regEmail.value.trim().toLowerCase();
    if (inputEmail !== info.email.toLowerCase()) {
      setProfileEmail(info.email);
      goStep('blocked-email-mismatch');
      return;
    }
  }

  // Validate Email
  const email = regEmail.value.trim();
  if (!email) { fieldError(regEmail, 'regEmailErr', 'Email không được để trống.'); ok = false; }
  else if (!EMAIL_RE.test(email)) { fieldError(regEmail, 'regEmailErr', 'Email không hợp lệ.'); ok = false; }
  else if (USED_EMAILS.includes(email.toLowerCase())) {
    fieldError(regEmail, 'regEmailErr', 'Email này đã được dùng cho tài khoản khác.');
    ok = false;
  } else {
    clearErr('regEmailErr');
  }

  // Validate mật khẩu
  if (!regPass.value) { fieldError(regPass, 'regPassErr', 'Mật khẩu không được để trống.'); ok = false; }
  else if (regPass.value.length < 8 || regPass.value.length > 64) {
    fieldError(regPass, 'regPassErr', 'Mật khẩu phải từ 8-64 ký tự.');
    ok = false;
  } else if (!/[a-zA-Z]/.test(regPass.value) || !/\d/.test(regPass.value)) {
    fieldError(regPass, 'regPassErr', 'Mật khẩu phải có ít nhất 1 chữ cái và 1 chữ số.');
    ok = false;
  } else {
    clearErr('regPassErr');
  }

  if (!regPass2.value) { fieldError(regPass2, 'regPass2Err', 'Vui lòng nhập lại mật khẩu.'); ok = false; }
  else if (regPass.value && regPass2.value !== regPass.value) {
    fieldError(regPass2, 'regPass2Err', 'Mật khẩu nhập lại không khớp.');
    ok = false;
  } else {
    clearErr('regPass2Err');
  }

  // Validate họ tên, ngày sinh
  if (!regName.value.trim()) { fieldError(regName, 'regNameErr', 'Họ và tên không được để trống.'); ok = false; }
  else { clearErr('regNameErr'); }
  
  if (!regDob.value) { fieldError(regDob, 'regDobErr', 'Ngày sinh không được để trống.'); ok = false; }
  else { clearErr('regDobErr'); }

  if (!ok) return;

  // Lưu thông tin và gửi OTP
  state.phone = phone;
  state.email = email;
  state.dirty = false;
  setPhone(phone);
  sendOTP();
});

/* ---------- gửi OTP: kiểm tra giới hạn 5 lần/giờ ---------- */
function sendOTP() {
  const now = Date.now();
  
  // Kiểm tra khóa 5 phút
  if (state.otpLockUntil && now < state.otpLockUntil) {
    const remaining = Math.ceil((state.otpLockUntil - now) / 1000);
    return showToast('error', `Đã gửi quá nhiều mã. Vui lòng thử lại sau ${Math.ceil(remaining / 60)} phút.`);
  }

  // Kiểm tra 60 giây giữa các lần gửi
  if (state.resendLockUntil && now < state.resendLockUntil) {
    const remaining = Math.ceil((state.resendLockUntil - now) / 1000);
    return showToast('warning', `Vui lòng chờ ${remaining} giây trước khi gửi lại mã.`);
  }

  // Lọc các lần gửi trong 1 giờ qua
  const oneHourAgo = now - 60 * 60 * 1000;
  state.otpSendTimes = state.otpSendTimes.filter(t => t > oneHourAgo);

  // Kiểm tra giới hạn 5 lần/giờ
  if (state.otpSendTimes.length >= 5) {
    state.otpLockUntil = now + 5 * 60 * 1000;
    return showToast('error', 'Đã gửi mã quá 5 lần trong 1 giờ. Vui lòng thử lại sau 5 phút.');
  }

  // Gửi mã thành công
  state.otpSendTimes.push(now);
  state.resendLockUntil = now + 60 * 1000;
  state.otpAttempts = 0;
  
  document.querySelector('[data-otp-email]').textContent = maskEmail(state.email);
  
  goStep('otp');
  showToast('info', 'Đã gửi mã xác thực tới ' + maskEmail(state.email) + '.');
  
  updateResendButton();
}

/* ---------- nhập mã xác thực ---------- */
const otpForm = document.getElementById('otpForm');
const otpInput = document.getElementById('otpInput');

otpForm.addEventListener('submit', e => {
  e.preventDefault();
  
  const now = Date.now();
  if (state.otpLockUntil && now < state.otpLockUntil) {
    const remaining = Math.ceil((state.otpLockUntil - now) / 1000);
    return fieldError(otpInput, 'otpInputErr', `Đã sai quá 5 lần. Vui lòng thử lại sau ${Math.ceil(remaining / 60)} phút.`);
  }
  
  const v = otpInput.value.trim();
  if (!v) return fieldError(otpInput, 'otpInputErr', 'Vui lòng nhập mã xác thực.');
  
  if (v !== OTP_CODE) {
    state.otpAttempts++;
    
    if (state.otpAttempts >= 5) {
      state.otpLockUntil = now + 5 * 60 * 1000;
      return fieldError(otpInput, 'otpInputErr', 'Đã sai 5 lần. Mã xác thực đã bị hủy. Vui lòng thử lại sau 5 phút.');
    }
    
    const remaining = 5 - state.otpAttempts;
    return fieldError(otpInput, 'otpInputErr', `Mã xác thực không đúng. Còn ${remaining} lần thử.`);
  }
  
  // Đúng mã → thành công
  state.otpAttempts = 0;
  state.otpLockUntil = null;
  
  setSuccessMode('new');
  goStep('success');
  showToast('success', 'Tạo tài khoản thành công!');
});

/* gửi lại mã — khóa nút 60 giây, kiểm tra giới hạn */
const resendBtn = document.getElementById('resendBtn');

function updateResendButton() {
  const now = Date.now();
  
  if (state.otpLockUntil && now < state.otpLockUntil) {
    const remaining = Math.ceil((state.otpLockUntil - now) / 1000);
    resendBtn.disabled = true;
    resendBtn.textContent = `Bị khóa (${Math.ceil(remaining / 60)} phút)`;
    setTimeout(updateResendButton, 1000);
    return;
  }
  
  if (state.resendLockUntil && now < state.resendLockUntil) {
    const remaining = Math.ceil((state.resendLockUntil - now) / 1000);
    resendBtn.disabled = true;
    resendBtn.textContent = `Gửi lại sau ${remaining}s`;
    setTimeout(updateResendButton, 1000);
    return;
  }
  
  resendBtn.disabled = false;
  resendBtn.textContent = 'Gửi lại mã';
}

resendBtn.addEventListener('click', () => {
  if (resendBtn.disabled) return;
  sendOTP();
});

/* ---------- cảnh báo khi rời trang giữa chừng ---------- */
document.addEventListener('click', e => {
  const link = e.target.closest('a[href]');
  if (!link || !state.dirty) return;
  if (link.hasAttribute('data-reset-phone')) return;
  const href = link.getAttribute('href');
  if (!href || href.startsWith('#')) return;

  e.preventDefault();
  openConfirmModal('Rời khỏi trang?', 'Thay đổi chưa lưu sẽ bị mất.', () => {
    state.dirty = false;
    location.href = link.href;
  });
});

addEventListener('beforeunload', e => {
  if (!state.dirty) return;
  e.preventDefault();
  e.returnValue = '';
});

function openConfirmModal(title, msg, onConfirm) {
  const root = document.getElementById('modalRoot');
  root.innerHTML = `
    <div class="modal-backdrop" role="dialog" aria-modal="true" aria-label="${title}">
      <div class="modal-box">
        <h3 class="modal-title"><i class="fa-solid fa-circle-question"></i><span>${title}</span></h3>
        <p>${msg}</p>
        <div class="btn-row">
          <button class="btn btn-outline" type="button" data-modal-close>Ở lại</button>
          <button class="btn btn-danger" type="button" id="modalConfirmBtn">Rời đi</button>
        </div>
      </div>
    </div>`;
  const close = () => { root.innerHTML = ''; };
  root.querySelector('.modal-backdrop').addEventListener('click', e => {
    if (e.target === e.currentTarget || e.target.closest('[data-modal-close]')) close();
  });
  root.querySelector('#modalConfirmBtn').addEventListener('click', () => { close(); onConfirm(); });
}
