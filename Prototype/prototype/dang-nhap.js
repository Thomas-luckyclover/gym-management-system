/* =====================================================================
   Đăng nhập: email/SĐT + mật khẩu, xử lý tài khoản chưa có mật khẩu
   và khóa 5 lần sai liên tiếp trong 5 phút
   ===================================================================== */

/* --- dữ liệu mẫu --- */
const USER_DB = {
  'giabao.tran@email.com': { phone: '0912345678', password: 'matkhau123', hasPassword: true, role: 'customer', name: 'Trần Gia Bảo' },
  '0912345678': { email: 'giabao.tran@email.com', password: 'matkhau123', hasPassword: true, role: 'customer', name: 'Trần Gia Bảo' },
  '0901111111': { email: 'lan.nguyen@gmail.com', hasPassword: false, role: 'customer', name: 'Nguyễn Thị Lan' },
  'lan.nguyen@gmail.com': { phone: '0901111111', hasPassword: false, role: 'customer', name: 'Nguyễn Thị Lan' },
};

/* số điện thoại dùng chung khách + nhân viên (vô hiệu hóa theo yêu cầu) */
const SHARED_PHONES = [];

const state = {
  step: 'form',
  failedAttempts: 0,
  lockUntil: null,
};

/* ---------- chuyển bước ---------- */
function goStep(step) {
  state.step = step;
  showStep(step);
}

/* ---------- "Thử lại" / "Quay lại" đưa về form ---------- */
document.addEventListener('click', e => {
  const trigger = e.target.closest('[data-back-form]');
  if (!trigger) return;
  e.preventDefault();
  goStep('form');
});

/* ---------- Form đăng nhập ---------- */
const loginForm = document.getElementById('loginForm');
const loginId = document.getElementById('loginId');
const loginPass = document.getElementById('loginPass');
const loginSubmitBtn = document.getElementById('loginSubmitBtn');

loginForm.addEventListener('submit', e => {
  e.preventDefault();
  
  const now = Date.now();
  
  // Check khóa 5 phút
  if (state.lockUntil && now < state.lockUntil) {
    const remaining = Math.ceil((state.lockUntil - now) / 1000);
    const lockEl = document.querySelector('[data-lock-remaining]');
    if (lockEl) lockEl.textContent = mmss(remaining);
    goStep('blocked-lock');
    return;
  }
  
  // Reset khóa nếu đã hết thời gian
  if (state.lockUntil && now >= state.lockUntil) {
    state.lockUntil = null;
    state.failedAttempts = 0;
  }
  
  let ok = true;
  const id = loginId.value.trim();
  
  // Validate ID (email hoặc SĐT)
  if (!id) {
    fieldError(loginId, 'loginIdErr', 'Vui lòng nhập email hoặc số điện thoại.');
    ok = false;
  } else {
    clearErr('loginIdErr');
  }
  
  // Validate mật khẩu
  if (!loginPass.value) {
    fieldError(loginPass, 'loginPassErr', 'Vui lòng nhập mật khẩu.');
    ok = false;
  } else {
    clearErr('loginPassErr');
  }
  
  if (!ok) return;
  
  // Chuẩn hóa input: bỏ khoảng trắng SĐT, lowercase email
  const normalizedId = /^\d/.test(id) ? id.replace(/\s/g, '') : id.toLowerCase();
  
  // Tra user
  const user = USER_DB[normalizedId];
  
  if (!user) {
    fieldError(loginId, 'loginIdErr', 'Tài khoản không tồn tại.');
    return;
  }
  
  // Check tài khoản chưa có mật khẩu (MSG-005)
  if (!user.hasPassword) {
    goStep('blocked-no-password');
    return;
  }
  
  // Check mật khẩu
  if (loginPass.value !== user.password) {
    state.failedAttempts++;
    
    if (state.failedAttempts >= 5) {
      state.lockUntil = now + 5 * 60 * 1000;
      const lockEl = document.querySelector('[data-lock-remaining]');
      if (lockEl) lockEl.textContent = '5:00';
      goStep('blocked-lock');
      startLockCountdown();
      return;
    }
    
    const remaining = 5 - state.failedAttempts;
    fieldError(loginPass, 'loginPassErr', `Mật khẩu không đúng. Còn ${remaining} lần thử.`);
    return;
  }
  
  // Đăng nhập thành công
  state.failedAttempts = 0;
  state.lockUntil = null;
  
  // Chuyển hướng theo role
  const rolePages = {
    customer: 'kh/trang-ca-nhan.html',
    trainer: 'pt/trang-chu.html',
    staff: 'lt/check-in.html',
    manager: 'Manager/gms_manager_mockup_v24.html',
    admin: 'Admin/gms_admin_mockup_v24.html',
  };
  
  const target = rolePages[user.role] || 'kh/trang-ca-nhan.html';
  showToast('success', `Xin chào ${user.name}!`);
  
  setTimeout(() => {
    location.href = target;
  }, 800);
});

/* ---------- đếm ngược khóa ---------- */
function startLockCountdown() {
  const lockEl = document.querySelector('[data-lock-remaining]');
  if (!lockEl) return;
  
  const update = () => {
    const now = Date.now();
    if (!state.lockUntil || now >= state.lockUntil) {
      state.lockUntil = null;
      state.failedAttempts = 0;
      return;
    }
    const remaining = Math.ceil((state.lockUntil - now) / 1000);
    lockEl.textContent = mmss(remaining);
    setTimeout(update, 1000);
  };
  
  update();
}
