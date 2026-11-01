/* 7.1.2 — Checkout: đồng hồ đếm ngược, voucher, vòng đời giao dịch */
/* Bảng giá chuẩn (_glossary.md) — chọn gói qua ?pkg=<mã> từ mua-goi.html */
const PACKAGES = {
  ngay: { name: 'Gói Ngày',     price: 120000,  term: '1 ngày, hết hạn 23:59 cùng ngày nếu chưa check-in', day: true },
  m1:   { name: 'Gói 1 Tháng',  price: 799000,  term: '1 tháng' },
  m3:   { name: 'Gói 3 Tháng',  price: 2190000, term: '3 tháng' },
  m9:   { name: 'Gói 9 Tháng',  price: 5490000, term: '9 tháng' },
  m12:  { name: 'Gói 12 Tháng', price: 6990000, term: '12 tháng' },
};
const PKG = PACKAGES[new URLSearchParams(location.search).get('pkg')] || PACKAGES.m12;
const BASE = PKG.price;
const VAT_PERCENT = 8;        // P-10, đã nằm trong giá bán
// DBR-52: hội viên đang có gói còn hạn (đến 14/11/2026) thì gói mới nối tiếp từ ngày kế tiếp
const TERM_TEXT = PKG.day ? PKG.term : PKG.term + ', bắt đầu 15/11/2026 (nối tiếp gói hiện tại)';
const PAY_SECONDS = 20 * 60;   // hạn thanh toán chép vào đơn lúc tạo (P-09 = 20 phút)

/* Mỗi mã minh hoạ một nhánh kiểm tra trong spec */
const VOUCHERS = {
  GYMVUI10:  { percent: 10, min: 500000 },
  NGAY15K: { amount: 15000, min: 100000 },
  HETLUOT25: { msg: ['warning', 'Mã voucher đã hết lượt sử dụng.'] },
  HE2026: { msg: ['warning', 'Mã voucher đã hết hạn.'] },
  TET2027X:    { msg: ['warning', 'Mã voucher chưa đến ngày áp dụng.'] },
};

let discount = 0;
let appliedCode = '';
let waitingGateway = false;
let attempt = 0;
let deadline = Date.now() + PAY_SECONDS * 1000;
let timer = null;

const vnd = n => n.toLocaleString('vi-VN') + 'đ';
const el = id => document.getElementById(id);

/* ---------- đồng hồ đếm ngược ---------- */
function remaining() { return Math.max(0, Math.ceil((deadline - Date.now()) / 1000)); }

function tick() {
  const left = remaining();
  el('payCountdown').textContent = mmss(left);
  if (left > 0) return;
  clearInterval(timer);
  el('countdownBar').hidden = true;
  showStep('expired');                                  // đơn Cancelled
  showToast('warning', 'Đơn hàng đã hết hạn thanh toán và bị hủy. Bấm Mua lại để tạo đơn mới.');
}
timer = setInterval(tick, 1000);
tick();

/* ---------- voucher ---------- */
function renderTotal() {
  const total = BASE - discount;
  el('grandTotal').textContent = vnd(total);
  el('basePrice').textContent = vnd(BASE);
  el('voucherClear').hidden = discount === 0;
  el('paidAmount').textContent = vnd(total);
  el('paidSnap').textContent = vnd(total);
  const vat = Math.round(total * VAT_PERCENT / (100 + VAT_PERCENT));
  el('vatAmount').textContent = vnd(vat);
  el('netAmount').textContent = vnd(total - vat);
  el('discountRow').hidden = discount === 0;
  if (discount) {
    el('discountCode').textContent = appliedCode;
    el('discountAmount').textContent = '−' + vnd(discount);
  }
}

function voucherMsg(type, text) {
  const map = { error: 'circle-xmark', warning: 'triangle-exclamation', success: 'circle-check', info: 'circle-info' };
  el('voucherMsg').innerHTML =
    `<div class="msg-banner ${type}" style="margin:0"><i class="fa-solid fa-${map[type]}"></i><span>${text}</span></div>`;
}

el('voucherForm').addEventListener('submit', e => {
  e.preventDefault();
  // Không gỡ hay đổi voucher khi đang chờ kết quả từ cổng thanh toán
  if (waitingGateway) return voucherMsg('warning', 'Đang chờ kết quả thanh toán, không thể thay đổi voucher lúc này.');

  const code = el('voucherInput').value.trim().toUpperCase();
  if (!code) return voucherMsg('error', 'Vui lòng nhập mã voucher.');
  if (discount) return voucherMsg('warning', 'Mỗi đơn chỉ áp dụng được 1 voucher.');

  const v = VOUCHERS[code];
  if (!v) return voucherMsg('error', 'Mã voucher không tồn tại hoặc đã ngừng hoạt động.');
  if (v.msg) return voucherMsg(v.msg[0], v.msg[1]);

  if (BASE < v.min) return voucherMsg('warning', `Đơn hàng chưa đạt giá trị tối thiểu ${vnd(v.min)} để dùng mã này.`);

  discount = v.percent ? Math.round(BASE * v.percent / 100) : v.amount;
  appliedCode = code;
  renderTotal();
  voucherMsg('success', `Đã áp dụng mã ${code} — giảm ${v.percent ? v.percent + '%' : vnd(v.amount)}.`);
  showToast('success', 'Đã áp dụng mã giảm giá.');
});

/* ---------- thanh toán ----------
   Mỗi lần bấm là một giao dịch mới, đi qua cổng rồi mới có kết quả.
   Giao dịch lỗi KHÔNG làm đơn chết — đơn vẫn Pending để trả lại tiền. */
function pay() {
  if (remaining() <= 0) return;
  waitingGateway = true;
  el('voucherInput').disabled = true;
  el('voucherBtn').disabled = true;

  openGateway({
    order: 'DH-2026-1191',
    amount: vnd(BASE - discount),
    forceFail: attempt === 0,               // lần đầu mô phỏng ngân hàng từ chối
    onDone(result) {
      waitingGateway = false;
      el('voucherInput').disabled = false;
      el('voucherBtn').disabled = false;

      if (result === 'cancelled') {
        showToast('info', 'Đã hủy giao dịch. Đơn hàng vẫn được giữ.');
        return;
      }
      attempt++;

      if (result === 'failed') {
        const until = new Date(deadline);
        el('retryDeadline').textContent =
          String(until.getHours()).padStart(2, '0') + ':' + String(until.getMinutes()).padStart(2, '0');
        showStep('failed');
        showToast('error', 'Thanh toán không thành công.');
        return;
      }

      clearInterval(timer);                 // đơn chuyển Paid
      el('countdownBar').hidden = true;
      showStep('paid');
      const total = vnd(BASE - discount);
      const target = 'ket-qua-thanh-toan.html?r=success&order=DH-2026-1191&pkg=' + encodeURIComponent(PKG.name) +
        '&amt=' + encodeURIComponent(total) + (PKG.day ? '' : '&start=15/11/2026');
      el('resultLink').href = target;
      setTimeout(() => { location.href = target; }, 2500);   // chuyển sang #70 Payment Result
      showToast('success', 'Thanh toán thành công. Gói đã được kích hoạt.');
    },
  });
}

el('payBtn').addEventListener('click', pay);
el('retryBtn').addEventListener('click', pay);

el('pkgName').textContent = PKG.name;
el('paidPkg').textContent = PKG.name;
el('pkgTerm').textContent = TERM_TEXT;
el('pkgPrice').textContent = vnd(BASE);
renderTotal();

/* Gỡ mã giảm giá: chỉ được khi chưa chờ kết quả cổng thanh toán */
el('voucherClear').addEventListener('click', () => {
  if (waitingGateway) return voucherMsg('warning', 'Đang chờ kết quả thanh toán, không thể thay đổi voucher lúc này.');
  discount = 0;
  appliedCode = '';
  el('voucherInput').value = '';
  el('voucherMsg').innerHTML = '';
  renderTotal();
});
// Gói Ngày không có gói nối tiếp
el('chainNote').hidden = !!PKG.day;
