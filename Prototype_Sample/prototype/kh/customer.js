/* =====================================================================
   Hành vi dùng chung cho khu vực Customer.
   Markup mọi màn nằm trong file .html; file này chỉ xử lý tương tác.
   ===================================================================== */

/* ---------- dock điều hướng ----------
   Rê vào tay cầm là sidebar trượt ra (CSS lo). Bấm để ghim mở, cho thiết
   bị cảm ứng và cho người muốn giữ menu mở khi đang so sánh các trang. */
(function () {
  const dock = document.getElementById('custDock');
  const handle = document.getElementById('dockHandle');
  if (!dock || !handle) return;

  handle.addEventListener('click', () => {
    const open = dock.classList.toggle('is-open');
    handle.setAttribute('aria-expanded', String(open));
    handle.setAttribute('aria-label', open ? 'Đóng menu điều hướng' : 'Mở menu điều hướng');
  });
  addEventListener('keydown', e => {
    if (e.key === 'Escape' && dock.classList.contains('is-open')) handle.click();
  });
})();

/* ---------- tab (9.6.3, 1.4.1) ---------- */
document.querySelectorAll('.tabs[role="tablist"]').forEach(list => {
  const buttons = [...list.querySelectorAll('[data-tab]')];
  buttons.forEach(btn => btn.addEventListener('click', () => {
    buttons.forEach(b => b.setAttribute('aria-selected', String(b === btn)));
    document.querySelectorAll('[data-tab-panel]').forEach(p => {
      p.hidden = p.dataset.tabPanel !== btn.dataset.tab;
    });
  }));
});

/* ---------- modal xác nhận (L5) ---------- */
/* onConfirm() chạy TRƯỚC khi modal đóng, nên đọc được dữ liệu trong modal.
   Trả về false để giữ modal mở khi dữ liệu chưa hợp lệ. */
function openConfirmModal({ title, body, confirmLabel = 'Xác nhận', danger = true, onConfirm }) {
  const root = document.getElementById('modalRoot');
  root.innerHTML = `
    <div class="modal-backdrop" role="dialog" aria-modal="true" aria-label="${title}">
      <div class="modal-box">
        <h3 class="modal-title"><i class="fa-solid fa-circle-question"></i><span>${title}</span></h3>
        ${body}
        <div class="btn-row">
          <button class="btn btn-outline" type="button" data-modal-close>Bỏ qua</button>
          <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" type="button" data-modal-confirm>${confirmLabel}</button>
        </div>
      </div>
    </div>`;
  const close = () => { root.innerHTML = ''; };
  root.querySelector('.modal-backdrop').addEventListener('click', e => {
    if (e.target === e.currentTarget || e.target.closest('[data-modal-close]')) close();
  });
  root.querySelector('[data-modal-confirm]').addEventListener('click', () => {
    if (onConfirm() === false) return;
    close();
  });
  addEventListener('keydown', function esc(e) {
    if (e.key === 'Escape') { close(); removeEventListener('keydown', esc); }
  });
}

/* =====================================================================
   Lưới slot theo tuần (9.3.1 và 9.3.3)
   Quy tắc: chỉ hiện tuần hiện tại; từ Thứ 7 mở thêm tuần kế.
   Ngày đã qua và ngày ngoài hạn gói tập đều khóa.
   ===================================================================== */
const MEMBERSHIP_END = new Date(2026, 10, 14);  // hạn gói tập: 14/11/2026
const DAY_NAMES = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

const dm = d => String(d.getDate()).padStart(2, '0') + '/' + String(d.getMonth() + 1).padStart(2, '0');
const dmy = d => dm(d) + '/' + d.getFullYear();

function buildWeek({ weekendOnly = false } = {}) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const dow = (today.getDay() + 6) % 7;             // 0 = Thứ 2
  const monday = new Date(today);
  monday.setDate(today.getDate() - dow);
  if (dow >= 5) monday.setDate(monday.getDate() + 7); // từ Thứ 7 mở tuần kế

  const days = DAY_NAMES.map((name, i) => {
    const date = new Date(monday);
    date.setDate(monday.getDate() + i);
    let reason = '';
    if (date < today) reason = 'Ngày đã qua';
    else if (date > MEMBERSHIP_END) reason = 'Ngoài hạn gói tập của bạn (hết hạn ' + dm(MEMBERSHIP_END) + ')';
    else if (weekendOnly && i < 5) reason = 'Chỉ đổi được sang Thứ 7 hoặc Chủ Nhật';
    return { i, name, date, blocked: Boolean(reason), reason };
  });

  const last = days[6].date;
  return { days, label: 'Tuần ' + dm(monday) + ' – ' + dmy(last) };
}

/* Gắn ngày thật vào lưới đã có sẵn trong HTML và khóa cột không chọn được. */
function initSlotGrid({ weekendOnly = false, onPick } = {}) {
  const week = buildWeek({ weekendOnly });
  document.querySelectorAll('[data-week-label]').forEach(el => { el.textContent = week.label; });

  week.days.forEach(day => {
    const label = document.querySelector(`[data-day-label="${day.i}"]`);
    if (!label) return;
    label.querySelector('em').textContent = dm(day.date);
    const col = label.closest('.slot-day');
    if (!day.blocked) return;
    col.classList.add('out-of-range');
    col.querySelectorAll('.slot').forEach(btn => { btn.disabled = true; btn.title = day.reason; });
  });

  let picked = null;
  document.querySelectorAll('.slot').forEach(btn => {
    if (btn.disabled) return;
    btn.addEventListener('click', () => {
      if (picked) picked.setAttribute('aria-pressed', 'false');
      picked = btn;
      btn.setAttribute('aria-pressed', 'true');
      const day = week.days[Number(btn.dataset.day)];
      onPick?.({ text: `${day.name} ${dmy(day.date)} · ${btn.dataset.time}` });
    });
  });

  return week;
}

/* =====================================================================
   Danh sách có lọc + phân trang — dùng chung mọi màn L1
   matcher(row) trả về true nếu hàng khớp bộ lọc hiện tại.
   ===================================================================== */
function makeList({ listSel, matcher, perPage = 6, unit = 'mục', onRender }) {
  const list = document.querySelector(listSel);
  if (!list) return null;
  const rows = [...list.querySelectorAll('.data-row')];
  const empty = list.querySelector('[data-empty]');
  const pager = document.querySelector('[data-pager]');
  const info = pager?.querySelector('.pager-info');
  const btns = pager?.querySelector('.pager-btns');
  let page = 1;

  function button(label, target, { current = false, disabled = false } = {}) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'pg';
    b.innerHTML = label;
    b.disabled = disabled;
    if (current) b.setAttribute('aria-current', 'page');
    else b.addEventListener('click', () => { page = target; render(); list.scrollIntoView({ behavior: 'smooth', block: 'start' }); });
    return b;
  }

  function render() {
    const found = rows.filter(matcher);
    const pages = Math.max(1, Math.ceil(found.length / perPage));
    if (page > pages) page = pages;
    const from = (page - 1) * perPage;
    const shown = found.slice(from, from + perPage);

    rows.forEach(r => { r.hidden = !shown.includes(r); });
    if (empty) empty.hidden = found.length > 0;
    onRender?.(found);

    if (!pager) return;
    pager.hidden = found.length === 0;
    if (!found.length) return;
    info.innerHTML = `<b>${from + 1}–${from + shown.length}</b> trên ${found.length} ${unit}`;
    btns.innerHTML = '';
    btns.append(button('<i class="fa-solid fa-chevron-left"></i>', page - 1, { disabled: page <= 1 }));
    for (let i = 1; i <= pages; i++) btns.append(button(String(i), i, { current: i === page }));
    btns.append(button('<i class="fa-solid fa-chevron-right"></i>', page + 1, { disabled: page >= pages }));
  }

  return { render, reset() { page = 1; render(); }, rows };
}

/* lọc slot theo khung giờ sáng / chiều / tối */
function bindSlotBand(selectId, resetId) {
  const sel = document.getElementById(selectId);
  if (!sel) return;
  const apply = () => {
    document.querySelectorAll('.slot').forEach(s => {
      s.hidden = sel.value !== 'all' && s.dataset.band !== sel.value;
    });
  };
  sel.addEventListener('change', apply);
  document.getElementById(resetId)?.addEventListener('click', () => { sel.value = 'all'; apply(); });
  apply();
}

/* =====================================================================
   Mô phỏng cổng thanh toán
   openGateway({ order, amount, forceFail, onDone }) dựng đúng các bước
   của một cổng thật: chọn phương thức → xử lý → trả kết quả về trang gọi.
   onDone('success' | 'failed' | 'cancelled')
   ===================================================================== */
function openGateway({ order, amount, forceFail = false, onDone }) {
  const root = document.getElementById('modalRoot');
  const TTL = 5 * 60;                       // giao dịch hết hạn sau 5 phút
  let left = TTL;
  let timer = null;

  root.innerHTML = `
    <div class="gw" role="dialog" aria-modal="true" aria-label="Cổng thanh toán">
      <div class="gw-box">
        <div class="gw-head">
          <span class="gw-logo">VN<span>PAY</span></span>
          <span class="gw-env">Mô phỏng</span>
        </div>

        <div data-gw="form">
          <div class="gw-body">
            <div class="gw-order">
              <div class="side-row"><span>Nhà cung cấp</span><b>Super Gym</b></div>
              <div class="side-row"><span>Mã đơn</span><b>${order}</b></div>
              <div class="side-row"><span>Số tiền</span><b class="gw-amount">${amount}</b></div>
            </div>

            <div>
              <span class="section-label">Phương thức</span>
              <div class="gw-method is-only" style="margin-top:var(--space-3)">
                <i class="fa-solid fa-qrcode"></i>
                <span><b>Quét mã VNPAY</b><small>Mở ứng dụng ngân hàng và quét mã để thanh toán</small></span>
              </div>
              <div class="gw-qr" aria-hidden="true">
                <svg viewBox="0 0 29 29" role="img" aria-label="Mã QR mô phỏng">
                  <rect width="29" height="29" fill="#fff"/>
                  <path fill="#1A1917" d="M0 0h7v7H0zM2 2h3v3H2zM22 0h7v7h-7zM24 2h3v3h-3zM0 22h7v7H0zM2 24h3v3H2zM9 0h2v2H9zM13 0h2v4h-2zM17 2h2v2h-2zM9 4h4v2H9zM19 5h3v2h-3zM0 9h2v2H0zM4 9h3v2H4zM9 9h2v3H9zM13 10h3v2h-3zM18 9h2v2h-2zM22 9h2v2h-2zM26 10h3v2h-3zM2 13h3v2H2zM7 14h2v2H7zM11 13h2v3h-2zM15 14h2v2h-2zM19 13h2v2h-2zM23 14h3v2h-3zM0 17h2v3H0zM5 18h2v2H5zM9 17h3v2H9zM14 18h2v2h-2zM18 17h2v3h-2zM22 18h4v2h-4zM9 22h2v3H9zM13 22h3v2h-3zM17 23h2v2h-2zM21 22h2v3h-2zM25 23h4v2h-4zM11 26h3v3h-3zM16 26h2v3h-2zM20 27h5v2h-5z"/>
                </svg>
              </div>
            </div>

            <div class="gw-timer">
              <i class="fa-solid fa-clock"></i>
              Giao dịch hết hạn sau <b data-gw-timer>5:00</b>
            </div>
          </div>

          <div class="gw-foot">
            <button class="btn btn-ghost" type="button" data-gw-cancel>Hủy giao dịch</button>
            <button class="btn btn-primary" type="button" data-gw-pay>Thanh toán</button>
          </div>
          <p class="gw-note">Màn mô phỏng phục vụ dựng luồng, không kết nối cổng thanh toán thật.</p>
        </div>

        <div class="gw-state" data-gw="processing" hidden>
          <div class="spin"></div>
          <h3>Đang xử lý giao dịch</h3>
          <p>Vui lòng không đóng cửa sổ này.</p>
        </div>

        <div class="gw-state fail" data-gw="failed" hidden>
          <div class="result-icon"><i class="fa-solid fa-circle-xmark"></i></div>
          <h3>Giao dịch không thành công</h3>
          <p>Ngân hàng từ chối giao dịch. Đơn hàng vẫn được giữ, bạn thử lại được.</p>
          <button class="btn btn-primary" type="button" data-gw-back style="margin-top:var(--space-5)">Quay lại cửa hàng</button>
        </div>

        <div class="gw-state ok" data-gw="success" hidden>
          <div class="result-icon"><i class="fa-solid fa-circle-check"></i></div>
          <h3>Giao dịch thành công</h3>
          <p>Mã giao dịch <b>VNP${Date.now().toString().slice(-8)}</b></p>
          <button class="btn btn-primary" type="button" data-gw-back style="margin-top:var(--space-5)">Quay lại cửa hàng</button>
        </div>
      </div>
    </div>`;

  const step = n => root.querySelectorAll('[data-gw]').forEach(el => { el.hidden = el.dataset.gw !== n; });
  const close = result => { clearInterval(timer); root.innerHTML = ''; onDone(result); };

  const timerEl = root.querySelector('[data-gw-timer]');
  timer = setInterval(() => {
    left--;
    timerEl.textContent = mmss(left);
    if (left <= 0) close('failed');
  }, 1000);

  root.querySelector('[data-gw-cancel]').addEventListener('click', () => close('cancelled'));
  root.querySelector('[data-gw-pay]').addEventListener('click', () => {
    step('processing');
    clearInterval(timer);
    setTimeout(() => step(forceFail ? 'failed' : 'success'), 1600);
  });
  root.querySelectorAll('[data-gw-back]').forEach(b => b.addEventListener('click', () => {
    close(forceFail ? 'failed' : 'success');
  }));
}
