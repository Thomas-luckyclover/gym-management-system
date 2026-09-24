/* =====================================================================
   SUPER GYM — hành vi dùng chung cho mọi trang
   Toàn bộ markup nằm trong file .html; file này chỉ xử lý tương tác.
   ===================================================================== */

/* ---------- menu mobile ---------- */
document.addEventListener('click', e => {
  const toggle = e.target.closest('[data-mobile-nav]');
  if (!toggle) return;
  const nav = document.getElementById('mobileNav');
  if (!nav) return;
  const open = nav.hidden;
  nav.hidden = !open;
  toggle.setAttribute('aria-expanded', String(open));
});

/* ---------- nút hiện/ẩn mật khẩu ---------- */
document.addEventListener('click', e => {
  const btn = e.target.closest('[data-pw-toggle]');
  if (!btn) return;
  const input = document.getElementById(btn.dataset.pwToggle);
  if (!input) return;
  const show = input.type === 'password';
  input.type = show ? 'text' : 'password';
  btn.innerHTML = show
    ? '<i class="fa-solid fa-eye-slash"></i>'
    : '<i class="fa-solid fa-eye"></i>';
  btn.setAttribute('aria-label', show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
});

/* ---------- toast ---------- */
const TOAST_ICONS = {
  success: 'fa-circle-check',
  info: 'fa-circle-info',
  warning: 'fa-triangle-exclamation',
  error: 'fa-circle-xmark',
};
function showToast(type, msg) {
  const root = document.getElementById('toastRoot');
  if (!root) return;
  const el = document.createElement('div');
  el.className = 'toast toast-' + type;
  el.setAttribute('role', type === 'error' || type === 'warning' ? 'alert' : 'status');
  el.innerHTML = `<i class="fa-solid ${TOAST_ICONS[type] || TOAST_ICONS.info}"></i><span>${msg}</span>`;
  root.appendChild(el);
  requestAnimationFrame(() => el.classList.add('show'));
  setTimeout(() => { el.classList.remove('show'); setTimeout(() => el.remove(), 300); }, 3400);
}

/* ---------- toast mang qua trang khác bằng query string ----------
   Điều hướng giờ là chuyển trang thật, không còn router trong JS,
   nên thông báo "gửi kèm" được truyền qua ?msg=<key>. */
const CROSS_PAGE_MESSAGES = {
  'post-removed':     ['warning', 'Bài viết này không còn hiển thị.'],
  'register-success': ['success', 'Tạo tài khoản thành công. Vui lòng đăng nhập.'],
  'password-changed': ['success', 'Đổi mật khẩu thành công. Mọi phiên đăng nhập cũ đã bị đăng xuất.'],
};
(function consumeCrossPageMessage() {
  const key = new URLSearchParams(location.search).get('msg');
  const entry = CROSS_PAGE_MESSAGES[key];
  if (!entry) return;
  // bỏ ?msg khỏi thanh địa chỉ để tải lại trang không hiện lại toast
  history.replaceState(null, '', location.pathname + location.hash);
  addEventListener('DOMContentLoaded', () => showToast(entry[0], entry[1]));
})();

/* ---------- helper form dùng chung ---------- */
function fieldError(input, errId, msg) {
  if (input) input.classList.add('invalid');
  const el = document.getElementById(errId);
  if (!el) return;
  el.innerHTML = `<i class="fa-solid fa-circle-xmark"></i><span>${msg}</span>`;
  el.hidden = false;
}
function clearErr(errId) {
  const el = document.getElementById(errId);
  if (!el) return;
  el.hidden = true;
  const input = el.closest('.field')?.querySelector('input');
  if (input) input.classList.remove('invalid');
}
/* gõ lại vào ô nào thì xoá lỗi của ô đó — quy ước id lỗi = id ô + "Err" */
document.addEventListener('input', e => {
  if (e.target.matches('.field input')) clearErr(e.target.id + 'Err');
});

/* Khoá tìm kiếm tiếng Việt: bỏ dấu và hạ chữ thường, để gõ "thu ha"
   vẫn khớp "Lê Thu Hà". Dùng cho mọi ô tìm kiếm trong hệ thống. */
function viKey(s) {
  return (s || '').toLowerCase()
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd');
}

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
function maskEmail(email) {
  const [u, d] = email.split('@');
  return u[0] + '***@' + d;
}
function mmss(sec) {
  return Math.floor(sec / 60) + ':' + String(sec % 60).padStart(2, '0');
}
function formatPhoneDisplay(p) {
  return p.replace(/(\d{4})(\d{3})(\d{3})/, '$1 $2 $3');
}

/* ---------- panel nhiều bước ----------
   Mọi bước có sẵn trong HTML dưới dạng .step-panel[data-step],
   showStep() chỉ bật đúng một panel. */
function showStep(name) {
  document.querySelectorAll('.step-panel').forEach(p => {
    p.hidden = p.dataset.step !== name;
  });
  document.querySelector(`.step-panel[data-step="${name}"]`)
    ?.querySelector('input:not([disabled])')?.focus({ preventScroll: true });
}
