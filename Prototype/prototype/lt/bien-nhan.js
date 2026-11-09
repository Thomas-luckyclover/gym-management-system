/* 7.1.4 — Phiếu thu dịch vụ tại quầy (UC-20). Dữ liệu mẫu lấy từ sessionStorage do ban-hang.js ghi. */
(function () {
  const vnd = n => Number(n || 0).toLocaleString('vi-VN') + 'đ';
  const $ = id => document.getElementById(id);
  const dmy = iso => { const [y, m, d] = iso.split('-'); return `${d}/${m}/${y}`; };
  const toIso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  const addDays = (iso, n) => { const [y, m, d] = iso.split('-').map(Number); return toIso(new Date(y, m - 1, d + n)); };
  /* GBR-03: ngày cuối hợp lệ = ngày bắt đầu + n tháng - 1 ngày; nếu ngày đích không tồn tại thì lấy ngày cuối tháng */
  function endDate(startIso, months) {
    const [y, m, d] = startIso.split('-').map(Number);
    const first = new Date(y, m - 1 + months, 1);
    const last = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
    const day = Math.min(d, last);
    const e = new Date(first.getFullYear(), first.getMonth(), day);
    return d > last ? toIso(e) : toIso(new Date(e.getFullYear(), e.getMonth(), e.getDate() - 1));
  }
  let o = null;
  try { o = JSON.parse(sessionStorage.getItem('gms_new_order') || 'null'); } catch {}
  if (!o) { $('rcEmpty').hidden = false; return; }
  $('rcWrap').hidden = false;
  $('rcCode').textContent = o.code;
  $('rcTime').textContent = '20/09/2026 ' + (o.time || '16:30');
  $('rcCust').textContent = o.cust;
  $('rcCode2').textContent = (o.custCode || '') + (o.phone ? ' / ' + o.phone : '');
  $('rcPkg').textContent = o.pkg;
  $('rcPay').textContent = o.pay === 'bank' ? 'Chuyển khoản / POS' : 'Tiền mặt';
  if (o.pay === 'bank' && o.ref) { $('rcRefRow').hidden = false; $('rcRef').textContent = o.ref; }
  if (o.voucher && o.discount) { $('rcVoucherRow').hidden = false; $('rcVoucher').textContent = `Giảm giá (${o.voucher}):`; $('rcOff').textContent = '−' + vnd(o.discount); }
  $('rcTotal').textContent = vnd(o.amount);

  const months = (o.pkg.match(/(\d+) Tháng/) || [])[1] ? +o.pkg.match(/(\d+) Tháng/)[1] : 0;   // Gói Ngày / buổi PT: 0
  const isMembership = months > 0;
  let start = o.startIso || '2026-09-20', changed = false;
  const range = () => isMembership ? `${dmy(start)} → ${dmy(endDate(start, months))}` : dmy(start);
  $('rcRange').textContent = range();

  const lo = o.earliestIso || '2026-09-20', hi = addDays(lo, 7);
  /* chỉ gói tập chưa dùng mới đổi được ngày, một lần (CBR-119); khoảng đổi: hôm nay đến hôm nay + 7 ngày */
  const lock = msg => { $('rcChange').hidden = true; $('rcLock').hidden = false; $('rcLock').textContent = msg; };
  if (!isMembership) lock(/PT/.test(o.pkg) ? 'Buổi PT lẻ không có ngày bắt đầu gói để đổi.' : 'Gói Ngày luôn bắt đầu ngay ngày thanh toán, không đổi ngày.');
  $('rcChange').addEventListener('click', () => {
    $('rcStartForm').hidden = false; $('rcChange').hidden = true;
    $('rcDate').min = lo; $('rcDate').max = hi; $('rcDate').value = start;
    $('rcDateHint').textContent = `Chỉ được chọn từ ${dmy(lo)} đến ${dmy(hi)}. Ngày hết hạn sẽ tính lại theo thời hạn gói.`;
  });
  $('rcCancel').addEventListener('click', () => { $('rcStartForm').hidden = true; $('rcChange').hidden = false; $('rcDateErr').hidden = true; });
  $('rcSave').addEventListener('click', () => {
    const v = $('rcDate').value, err = $('rcDateErr');
    if (!v || v < lo || v > hi) { err.hidden = false; err.textContent = `Ngày bắt đầu phải từ ${dmy(lo)} đến ${dmy(hi)}.`; return; }
    err.hidden = true; start = v; changed = true;
    $('rcRange').textContent = range();
    $('rcStartForm').hidden = true;
    lock('Đã đổi ngày bắt đầu một lần, không đổi thêm được.');
    showToast('success', 'Đã đổi ngày bắt đầu sang ' + dmy(v) + '.');
  });
  $('rcPrint').addEventListener('click', () => window.print());
})();
