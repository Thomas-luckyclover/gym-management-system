/* =====================================================================
   1.2.1 — Quên mật khẩu (MSG-006..011)
   Cả 4 bước nằm sẵn trong quen-mat-khau.html; ở đây chỉ điều phối.
   ===================================================================== */

const ACCOUNTS = [
  { email: 'an.nguyen@email.com', name: 'Nguyễn Minh An' },
  { email: 'cu.khach@email.com',  name: 'Trần Văn Cũ' },
  { email: 'thu.ha@email.com',    name: 'Lê Thu Hà' },
];
const RESET_CODE = '123456';
const RESET_TTL = 15 * 60;          // mã khôi phục có hiệu lực 15 phút

let email = '';
let expiresAt = 0;
let tickTimer = null;
let codeAttempts = 0;               // số lần sai mã
let codeSendCount = 0;              // số lần gửi mã trong 1 giờ
let codeSendTimes = [];             // timestamp của các lần gửi
let codeLockUntil = null;           // thời điểm mở khóa
let resendLockUntil = null;         // thời điểm cho phép gửi lại (60s)

const timerEl = document.getElementById('resetTimer');
const codeInput = document.getElementById('fpCode');
const codeBtn = document.getElementById('fpCodeBtn');

/* ---------- thanh tiến trình ---------- */
function markSteps(current) {
  document.querySelectorAll('[data-step-marker]').forEach(el => {
    const n = Number(el.dataset.stepMarker);
    el.classList.toggle('done', current > n);
    el.classList.toggle('current', current === n);
    el.querySelector('i').textContent = current > n ? '✓' : n;
  });
  document.getElementById('fpSteps').hidden = current > 3;
}

function setEmail(value) {
  email = value;
  document.querySelectorAll('[data-fp-email]').forEach(el => { el.textContent = maskEmail(value); });
}

/* ---------- đếm ngược hiệu lực mã ---------- */
const remaining = () => Math.max(0, Math.ceil((expiresAt - Date.now()) / 1000));

function setExpired(expired) {
  document.querySelector('[data-msg="sent"]').hidden = expired;
  document.querySelector('[data-msg="expired"]').hidden = !expired;
  codeInput.disabled = expired;
  codeBtn.disabled = expired;
}

function startCountdown() {
  expiresAt = Date.now() + RESET_TTL * 1000;
  setExpired(false);
  timerEl.textContent = mmss(remaining());
  clearInterval(tickTimer);
  tickTimer = setInterval(() => {
    const left = remaining();
    timerEl.textContent = mmss(left);
    if (left > 0) return;
    clearInterval(tickTimer);
    tickTimer = null;
    setExpired(true);
  }, 1000);
}

/* ---------- bước 1: gửi mã ---------- */
function sendResetCode() {
  const now = Date.now();
  
  // Kiểm tra khóa 5 phút
  if (codeLockUntil && now < codeLockUntil) {
    const remaining = Math.ceil((codeLockUntil - now) / 1000);
    return showToast('error', `Đã gửi quá nhiều mã. Vui lòng thử lại sau ${Math.ceil(remaining / 60)} phút.`);
  }

  // Kiểm tra 60 giây giữa các lần gửi
  if (resendLockUntil && now < resendLockUntil) {
    const remaining = Math.ceil((resendLockUntil - now) / 1000);
    return showToast('warning', `Vui lòng chờ ${remaining} giây trước khi gửi lại mã.`);
  }

  // Lọc các lần gửi trong 1 giờ qua
  const oneHourAgo = now - 60 * 60 * 1000;
  codeSendTimes = codeSendTimes.filter(t => t > oneHourAgo);

  // Kiểm tra giới hạn 5 lần/giờ
  if (codeSendTimes.length >= 5) {
    codeLockUntil = now + 5 * 60 * 1000;  // Khóa 5 phút
    return showToast('error', 'Đã gửi mã quá 5 lần trong 1 giờ. Vui lòng thử lại sau 5 phút.');
  }

  // Gửi mã thành công
  codeSendTimes.push(now);
  resendLockUntil = now + 60 * 1000;  // Khóa gửi lại 60 giây
  codeAttempts = 0;  // Reset số lần sai khi gửi mã mới
  
  markSteps(2);
  showStep('code');
  startCountdown();
  updateResendButton();
  showToast('success', 'Đã gửi mã khôi phục tới ' + maskEmail(email) + '. Mã có hiệu lực trong 15 phút.');
}

document.getElementById('fpEmailForm').addEventListener('submit', e => {
  e.preventDefault();
  const input = document.getElementById('fpEmail');
  const v = input.value.trim().toLowerCase();
  if (!v) return fieldError(input, 'fpEmailErr', 'Vui lòng nhập email.');
  if (!EMAIL_RE.test(v)) return fieldError(input, 'fpEmailErr', 'Email không hợp lệ.');
  if (!ACCOUNTS.some(a => a.email === v)) {
    return fieldError(input, 'fpEmailErr', 'Không tìm thấy tài khoản nào dùng email này.');
  }
  setEmail(v);
  sendResetCode();
});

/* ---------- bước 2: xác nhận mã ---------- */
document.getElementById('fpCodeForm').addEventListener('submit', e => {
  e.preventDefault();
  
  // Kiểm tra khóa 5 phút
  const now = Date.now();
  if (codeLockUntil && now < codeLockUntil) {
    const remaining = Math.ceil((codeLockUntil - now) / 1000);
    return fieldError(codeInput, 'fpCodeErr', `Đã sai quá 5 lần. Vui lòng thử lại sau ${Math.ceil(remaining / 60)} phút.`);
  }
  
  if (remaining() <= 0) return setExpired(true);
  
  const v = codeInput.value.trim();
  if (!v) return fieldError(codeInput, 'fpCodeErr', 'Vui lòng nhập mã khôi phục.');
  
  if (v !== RESET_CODE) {
    codeAttempts++;
    
    // Sai lần thứ 5 → khóa 5 phút
    if (codeAttempts >= 5) {
      codeLockUntil = now + 5 * 60 * 1000;
      return fieldError(codeInput, 'fpCodeErr', 'Đã sai 5 lần. Mã khôi phục đã bị hủy. Vui lòng thử lại sau 5 phút.');
    }
    
    const remainingAttempts = 5 - codeAttempts;
    return fieldError(codeInput, 'fpCodeErr', `Mã không đúng. Còn ${remainingAttempts} lần thử.`);
  }

  // Đúng mã → reset và tiếp tục
  codeAttempts = 0;
  codeLockUntil = null;
  clearInterval(tickTimer);
  tickTimer = null;
  markSteps(3);
  showStep('password');
});

/* ---------- gửi lại mã ---------- */
function updateResendButton() {
  const btn = document.getElementById('fpResendBtn');
  const now = Date.now();
  
  // Kiểm tra khóa 5 phút
  if (codeLockUntil && now < codeLockUntil) {
    const remaining = Math.ceil((codeLockUntil - now) / 1000);
    btn.disabled = true;
    btn.textContent = `Bị khóa (${Math.ceil(remaining / 60)} phút)`;
    setTimeout(updateResendButton, 1000);
    return;
  }
  
  // Kiểm tra khóa 60 giây
  if (resendLockUntil && now < resendLockUntil) {
    const remaining = Math.ceil((resendLockUntil - now) / 1000);
    btn.disabled = true;
    btn.textContent = `Gửi lại sau ${remaining}s`;
    setTimeout(updateResendButton, 1000);
    return;
  }
  
  // Mở khóa
  btn.disabled = false;
  btn.textContent = 'Gửi lại mã';
}

document.getElementById('fpResendBtn').addEventListener('click', () => {
  sendResetCode();
});

document.getElementById('fpChangeEmail').addEventListener('click', e => {
  e.preventDefault();
  clearInterval(tickTimer);
  tickTimer = null;
  markSteps(1);
  showStep('email');
});

/* ---------- bước 3: đặt mật khẩu mới ---------- */
document.getElementById('fpPassForm').addEventListener('submit', e => {
  e.preventDefault();
  const p1 = document.getElementById('fpPass');
  const p2 = document.getElementById('fpPass2');
  let ok = true;

  if (!p1.value) { fieldError(p1, 'fpPassErr', 'Mật khẩu không được để trống.'); ok = false; }
  else if (p1.value.length < 8 || p1.value.length > 64) { fieldError(p1, 'fpPassErr', 'Mật khẩu phải từ 8-64 ký tự.'); ok = false; }
  else if (!/[a-zA-Z]/.test(p1.value) || !/\d/.test(p1.value)) { fieldError(p1, 'fpPassErr', 'Mật khẩu phải có ít nhất 1 chữ cái và 1 chữ số.'); ok = false; }

  if (!p2.value) { fieldError(p2, 'fpPass2Err', 'Vui lòng nhập lại mật khẩu.'); ok = false; }
  else if (p1.value && p2.value !== p1.value) { fieldError(p2, 'fpPass2Err', 'Mật khẩu nhập lại không khớp.'); ok = false; }
  if (!ok) return;

  markSteps(4);
  showStep('done');
  showToast('success', 'Đổi mật khẩu thành công. Mọi phiên đăng nhập cũ đã bị đăng xuất.');
});
