/* 6.2.1 — Bảo lưu gói tập.
   Chọn khách trước, các trạng thái không cho bảo lưu bị chặn ngay ở bước đó. */
(function () {
  const DAY = 86400000;
  const MAX_MONTHS = 1;                           // P-02: tối đa 1 tháng mỗi lần, tính theo lịch (GBR-03)

  /* Dữ liệu tra cứu theo mã khách. block = lý do chặn, null là hợp lệ. */
  const DATA = {
    'KH-1001': { pkg: 'Gói 3 Tháng', end: '15/11/2026', left: 56, pt: [] },
    'KH-1002': { pkg: 'Gói Ngày', end: '20/09/2026', left: 0, block: 'Gói Ngày không bảo lưu được.' },
    'KH-1003': { pkg: 'Gói 12 Tháng', end: '01/03/2027', left: 162, block: 'Gói đang bảo lưu đến 05/10/2026 và đã dùng hết lượt bảo lưu. Mỗi gói chỉ bảo lưu một lần.' },
    'KH-1004': { pkg: 'Gói 1 Tháng', end: '10/09/2026', left: -10, block: 'Gói đã hết hạn 10/09/2026. Gia hạn trước rồi mới bảo lưu được.' },
    'KH-1005': { pkg: '', end: '', left: 0, block: 'Khách chỉ có gói PT. Bảo lưu chỉ áp dụng cho gói tập.' },
    'KH-1006': { pkg: 'Gói 1 Tháng', end: '18/10/2026', left: 28, pt: [] },
    'KH-1008': { pkg: 'Gói 9 Tháng', end: '01/03/2027', left: 162, pt: [] },
    'KH-1009': { pkg: 'Gói 3 Tháng', end: '01/12/2026', left: 72, pt: ['24/09 · 19:00 · PT Hoàng Long', '28/09 · 19:00 · PT Hoàng Long'] },
    'KH-1010': { pkg: 'Gói 1 Tháng', end: '05/10/2026', left: 15, pt: [] },
  };

  const cust = document.getElementById('fzCust');
  const start = document.getElementById('fzStart');
  const end = document.getElementById('fzEnd');
  const reason = document.getElementById('fzReason');
  const save = document.getElementById('fzSave');
  const block = document.getElementById('fzBlock');
  const blockText = document.getElementById('fzBlockText');
  const clash = document.getElementById('fzClash');
  const clashList = document.getElementById('fzClashList');
  const info = document.getElementById('fzInfo');
  const days = document.getElementById('fzDays');

  const d = s => new Date(s + 'T00:00:00');
  const span = () => Math.round((d(end.value) - d(start.value)) / DAY) + 1;
  const vi = s => s.split('-').reverse().join('/');

  const iso = t => `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`;
  /* ngày cuối được bảo lưu = bắt đầu + P-02 tháng − 1 ngày; tháng thiếu ngày thì lấy ngày cuối tháng */
  function maxEnd() {
    const t = d(start.value);
    const last = new Date(t.getFullYear(), t.getMonth() + MAX_MONTHS + 1, 0).getDate();
    return new Date(t.getFullYear(), t.getMonth() + MAX_MONTHS, Math.min(t.getDate(), last) - 1);
  }

  function current() { return DATA[cust.value] || null; }

  function renderInfo() {
    const c = current();
    if (!c) { info.innerHTML = '<p class="muted">Chọn khách để xem gói đang chạy.</p>'; return; }
    info.innerHTML = `
      <div class="side-row"><span>Gói</span><b>${c.pkg || 'Không có'}</b></div>
      <div class="side-row"><span>Hết hạn</span><b>${c.end || '—'}</b></div>
      <div class="side-row"><span>Còn lại</span><b>${c.left > 0 ? c.left + ' ngày' : 'Đã hết hạn'}</b></div>
      ${c.block ? '' : `<div class="side-row"><span>Hạn mới</span><b>${newEnd(c)}</b></div>`}`;
  }

  /* hạn gói dời đúng bằng số ngày bảo lưu */
  function newEnd(c) {
    const [dd, mm, yy] = c.end.split('/').map(Number);
    const t = new Date(yy, mm - 1, dd + span());
    return `${String(t.getDate()).padStart(2, '0')}/${String(t.getMonth() + 1).padStart(2, '0')}/${t.getFullYear()}`;
  }

  function validate() {
    const c = current();
    ['fzCustErr', 'fzStartErr', 'fzEndErr', 'fzReasonErr'].forEach(clearErr);
    block.hidden = true;
    clash.hidden = true;

    if (!c) { save.disabled = true; renderInfo(); return; }

    if (c.block) {
      blockText.innerHTML = `<b>Không bảo lưu được.</b> ${c.block}`;
      block.hidden = false;
      save.disabled = true;
      renderInfo();
      return;
    }

    let ok = true;
    const n = span();

    if (!start.value || !end.value || n < 1) {
      fieldError(end, 'fzEndErr', 'Ngày kết thúc phải sau ngày bắt đầu.');
      ok = false;
    } else if (d(end.value) > maxEnd()) {
      fieldError(end, 'fzEndErr', `Bảo lưu tối đa ${MAX_MONTHS} tháng, ngày kết thúc muộn nhất là ${vi(iso(maxEnd()))}.`);
      ok = false;
    } else if (n > c.left) {
      fieldError(end, 'fzEndErr', `Gói chỉ còn ${c.left} ngày, không bảo lưu dài hơn phần còn lại.`);
      ok = false;
    }

    if (d(start.value) <= d('2026-09-20')) {
      fieldError(start, 'fzStartErr', 'Bảo lưu bắt đầu từ ngày mai trở đi, không hồi tố.');
      ok = false;
    }

    days.textContent = n >= 1 ? `${n} ngày${iso(maxEnd()) === end.value ? ' — chạm trần cho phép.' : '.'}` : 'Khoảng ngày chưa hợp lệ.';

    /* CBR-62: buổi PT đã xếp trong khoảng bảo lưu chuyển thành Vắng (gói đang bảo lưu), tính vào số buổi đã dùng, không hoàn */
    const hit = (c.pt || []).filter(s => {
      const [dd, mm] = s.split(' · ')[0].split('/').map(Number);
      const t = new Date(2026, mm - 1, dd);
      return t >= d(start.value) && t <= d(end.value);
    });
    if (hit.length) {
      clashList.innerHTML = hit.map(s => `<span>${s}</span>`).join('<br>');
      clash.hidden = false;
    }

    save.disabled = !ok;
    renderInfo();
  }

  [cust, start, end].forEach(el => el.addEventListener('change', validate));
  reason.addEventListener('input', () => clearErr('fzReasonErr'));

  document.getElementById('fzForm').addEventListener('submit', e => {
    e.preventDefault();
    if (reason.value.trim().length < 5) {
      fieldError(reason, 'fzReasonErr', 'Lý do bảo lưu tối thiểu 5 ký tự để Quản lý đối chiếu khi cần.');
      return;
    }
    const c = current();
    const hit = !clash.hidden;
    openConfirmModal({
      title: 'Xác nhận bảo lưu',
      body: `<p><b>${cust.selectedOptions[0].textContent}</b></p>
             <p>Bảo lưu ${span()} ngày, từ ${vi(start.value)} đến ${vi(end.value)}.
                Hạn gói dời từ ${c.end} sang <b>${newEnd(c)}</b>. Hạn gói PT không đổi.</p>
             ${hit ? '<p>Các buổi PT trong khoảng này chuyển thành Vắng (gói đang bảo lưu), vẫn tính là đã dùng và không hoàn lại.</p>' : ''}
             <p class="muted">Trong thời gian bảo lưu khách không check-in được.</p>`,
      confirmLabel: 'Lưu bảo lưu',
      onConfirm() {
        showToast('success', 'Đã lưu bảo lưu. Hạn gói được dời tương ứng.');
        save.disabled = true;
      },
    });
  });

  validate();

})();
