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

/* ---------- mốc "hiện tại" của dữ liệu mẫu ----------
   Mọi màn dùng chung một mốc: Chủ nhật 20/09/2026 16:30 (ca chiều đang mở),
   để lịch, hạn nộp đơn, ngày khoá… khớp với dữ liệu mẫu ở các màn khác. */
const sampleNow = () => new Date(2026, 8, 20, 16, 30);

/* ---------- #73 Notification Bell ----------
   Chuông thông báo ở header mọi trang đã đăng nhập: 10 thông báo mới nhất,
   bấm để đánh dấu đã đọc, "Đọc tất cả". Dữ liệu mẫu theo vai trò (kh / lt / pt). */
const BELL_DATA = {
  kh: [
    ['Đơn DH-2026-1191 chờ thanh toán, còn 20 phút để hoàn tất.', '20/09/2026 09:12'],
    ['Buổi PT 23/09 19:00 với Hoàng Long đã được xếp.', '19/09/2026 18:30'],
    ['Buổi PT 18/11 nằm ngoài hạn gói tập. Gia hạn để không bị tính vắng.', '19/09/2026 08:00'],
    ['Hoàng Long đã xuất bản giáo án mới cho bạn.', '18/09/2026 14:30'],
    ['Thanh toán DH-2026-1187 (Buổi PT lẻ) thành công.', '18/09/2026 14:02'],
    ['Gói 6 Tháng sắp hết hạn vào 14/11/2026.', '15/09/2026 07:00'],
    ['Buổi PT 09/09 bị ghi nhận Vắng (không check-in).', '09/09/2026 22:00'],
    ['Buổi PT 02/09 được PT huỷ, vẫn tính là đã dùng.', '01/09/2026 10:00'],
    ['Chào mừng bạn đến Super Gym.', '12/05/2026 18:20'],
  ],
  lt: [
    ['Đơn DH-2026-1198 (Mai Anh Thư) đang chờ xác nhận chuyển khoản.', '20/09/2026 16:05'],
    ['Quản lý đã duyệt BT-0312: Máy đẩy ngực Matrix chuyển sang Đang bảo trì.', '19/09/2026 17:10'],
    ['Quản lý đang xem yêu cầu xoá hồ sơ KH-1008 và KH-1009.', '19/09/2026 17:05'],
    ['Đơn nghỉ 25/09 của bạn đang chờ Quản lý duyệt.', '18/09/2026 10:15'],
    ['Ca chiều 20/09 đã mở lúc 14:00.', '20/09/2026 14:00'],
    ['Quản lý từ chối BT-0301: đã có hợp đồng thay toàn bộ dãy A.', '22/08/2026 09:00'],
  ],
  pt: [
    ['Nguyễn Minh An vừa đặt buổi 23/09 19:00.', '19/09/2026 18:30'],
    ['Gói PT Couple của Vũ Hoài Nam &amp; Đỗ Lan Chi bắt đầu 23/09 17:00.', '20/09/2026 15:15'],
    ['Đơn nghỉ 21/09 (Ca sáng) đang chờ Quản lý duyệt. Còn 1 buổi cần xử lý.', '20/09/2026 08:10'],
    ['Lê Thu Hà có buổi 22/09 vào ngày chưa có gói tập.', '19/09/2026 08:00'],
    ['Hoa hồng tháng 08/2026 đã chốt: 12.075.000đ.', '01/09/2026 06:00'],
  ],
};
(function bell() {
  const role = location.pathname.match(/\/(kh|lt|pt)\//)?.[1] || (/chon-vai-tro\.html$/.test(location.pathname) ? 'kh' : null);
  if (!role) return;
  const bar = () => document.querySelector('.cust-topbar-actions');
  addEventListener('DOMContentLoaded', () => {
    const host = bar();
    if (!host) return;
    const key = 'gms_bell_' + role;
    const items = BELL_DATA[role].slice(0, 10);
    let read = [];
    try { read = JSON.parse(localStorage.getItem(key) || '[]'); } catch {}
    const save = () => { try { localStorage.setItem(key, JSON.stringify(read)); } catch {} };

    const wrap = document.createElement('div');
    wrap.className = 'notif-bell';
    wrap.style.position = 'relative';
    wrap.innerHTML = `
      <button class="btn btn-ghost btn-sm" type="button" data-bell aria-label="Thông báo" aria-expanded="false">
        <i class="fa-solid fa-bell"></i> <span class="badge badge-danger" data-bell-count></span>
      </button>
      <div class="panel" data-bell-panel hidden style="position:absolute;right:0;top:100%;width:min(360px,90vw);z-index:50;margin-top:6px">
        <div class="panel-head"><h2>Thông báo</h2>
          <button class="btn btn-ghost btn-sm" type="button" data-bell-all>Đọc tất cả</button></div>
        <div class="data-list" data-bell-list></div>
      </div>`;
    host.prepend(wrap);

    const panel = wrap.querySelector('[data-bell-panel]');
    const list = wrap.querySelector('[data-bell-list]');
    const count = wrap.querySelector('[data-bell-count]');

    function render() {
      const unread = items.filter((_, i) => !read.includes(i)).length;
      count.textContent = unread || '';
      count.hidden = !unread;
      list.innerHTML = items.length ? items.map((n, i) => `
        <button class="data-row" type="button" data-n="${i}" style="text-align:left;width:100%">
          <div class="row-main">
            <div class="row-title">${n[0]} ${read.includes(i) ? '' : '<span class="badge badge-info">Mới</span>'}</div>
            <div class="row-meta"><span><i class="fa-solid fa-clock"></i> ${n[1]}</span></div>
          </div></button>`).join('') : '<p class="muted">Chưa có thông báo nào.</p>';
    }
    wrap.addEventListener('click', e => {
      if (e.target.closest('[data-bell]')) {
        panel.hidden = !panel.hidden;
        wrap.querySelector('[data-bell]').setAttribute('aria-expanded', String(!panel.hidden));
      }
      const n = e.target.closest('[data-n]');
      if (n && !read.includes(+n.dataset.n)) { read.push(+n.dataset.n); save(); render(); }
      if (e.target.closest('[data-bell-all]')) { read = items.map((_, i) => i); save(); render(); }
    });
    document.addEventListener('click', e => { if (!wrap.contains(e.target)) panel.hidden = true; });
    render();
  });
})();
