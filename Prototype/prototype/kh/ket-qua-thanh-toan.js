/* #70 — Kết quả thanh toán VNPay: đọc kết quả đã lưu (query string mô phỏng dữ liệu trong DB). */
(function () {
  const q = new URLSearchParams(location.search);
  const RESULTS = {
    success:   { icon: 'fa-circle-check',          cls: 'ok',     head: 'Thanh toán thành công',
                 msg: 'Gói đã được kích hoạt. Lần đầu tới phòng tập, ghé quầy chụp ảnh check-in.' },
    failed:    { icon: 'fa-circle-xmark',          cls: 'danger', head: 'Thanh toán không thành công',
                 msg: 'Ngân hàng từ chối giao dịch. Đơn vẫn được giữ, bạn có thể thanh toán lại trước hạn.', retry: true },
    cancelled: { icon: 'fa-ban',                   cls: 'warn',   head: 'Bạn đã hủy thanh toán',
                 msg: 'Chưa có khoản tiền nào bị trừ. Đơn vẫn được giữ đến hết hạn thanh toán (20 phút).', retry: true },
    waiting:   { icon: 'fa-hourglass-half',        cls: 'info',   head: 'Đang chờ cổng thanh toán xác nhận',
                 msg: 'Hệ thống chưa nhận được kết quả chính thức. Đừng thanh toán lại, hãy tải lại trang sau ít phút.' },
    refund:    { icon: 'fa-triangle-exclamation',  cls: 'warn',   head: 'Cần hoàn tiền',
                 msg: 'Tiền đã bị trừ nhưng đơn đã quá hạn hoặc đã bị hủy. Lễ tân sẽ liên hệ để hoàn tiền ngoài hệ thống.' },
    error:     { icon: 'fa-circle-info',           cls: 'danger', head: 'Chưa xác minh được kết quả',
                 msg: 'Không đọc được kết quả giao dịch. Xem Lịch sử giao dịch để biết trạng thái đơn.' },
  };
  const MONTHS = { 'Gói 1 Tháng': 1, 'Gói 3 Tháng': 3, 'Gói 6 Tháng': 6, 'Gói 9 Tháng': 9, 'Gói 12 Tháng': 12 };
  const ORDER = {
    order: q.get('order') || 'DH-2026-1191',
    pkg: q.get('pkg') || 'Gói 12 Tháng',
    amt: q.get('amt') || '6.990.000đ',
  };
  let start = q.get('start') || '15/11/2026';   // gói cũ hết hạn 14/11 nên gói mới nối tiếp từ 15/11 (DBR-52)

  const parse = s => { const [d, m, y] = s.split('/').map(Number); return new Date(y, m - 1, d); };
  const fmt = d => String(d.getDate()).padStart(2, '0') + '/' + String(d.getMonth() + 1).padStart(2, '0') + '/' + d.getFullYear();
  const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  /* ngày cuối = ngày bắt đầu + n tháng − 1 ngày, tháng thiếu ngày thì lấy ngày cuối tháng (GBR-03) */
  function endOf(startStr, n) {
    const s = parse(startStr);
    const last = new Date(s.getFullYear(), s.getMonth() + n + 1, 0).getDate();
    return new Date(s.getFullYear(), s.getMonth() + n, Math.min(s.getDate(), last) - 1);
  }
  const el = id => document.getElementById(id);

  function render(key) {
    const r = RESULTS[key] || RESULTS.error;
    el('resIcon').innerHTML = `<i class="fa-solid ${r.icon}"></i>`;
    el('resBanner').className = 'alert-banner ' + r.cls;
    el('resHead').textContent = r.head;
    el('resMsg').textContent = r.msg;
    el('resOrder').textContent = ORDER.order;
    el('resPkg').textContent = ORDER.pkg;
    el('resAmt').textContent = ORDER.amt;
    const months = MONTHS[ORDER.pkg];
    const validity = el('resValidity');
    validity.hidden = !(key === 'success' && months);
    if (!validity.hidden) {
      el('resRange').textContent = `${start} – ${fmt(endOf(start, months))}`;
    }
    el('resRetry').hidden = !r.retry;
    el('resPick').value = key;
  }

  el('resPick').addEventListener('change', e => render(e.target.value));

  el('resChange').addEventListener('click', () => {
    openConfirmModal({
      title: 'Đổi ngày bắt đầu',
      body: `<p>Chọn ngày bắt đầu sau khi gói hiện tại hết hạn (14/11/2026). Ngày kết thúc tự tính theo tháng.</p>
             <label class="field"><span class="field-label">Ngày bắt đầu</span>
               <input id="resStartInput" type="date" min="2026-11-15" value="${iso(parse(start))}">
               <span class="field-error" id="resStartErr" hidden></span></label>`,
      confirmLabel: 'Lưu ngày',
      danger: false,
      onConfirm() {
        const v = el('resStartInput').value;
        if (!v || v < '2026-11-15') {
          fieldError(el('resStartInput'), 'resStartErr', 'Ngày bắt đầu phải từ 15/11/2026 trở đi.');
          return false;
        }
        start = fmt(new Date(v + 'T00:00:00'));
        render('success');
        showToast('success', 'Đã đổi ngày bắt đầu gói.');
      },
    });
  });

  render(RESULTS[q.get('r')] ? q.get('r') : 'success');
})();
