/* 7.1.2 — Checkout: đồng hồ đếm ngược, voucher, vòng đời giao dịch */
const BASE = 3990000;
const PAY_SECONDS = 20 * 60;   // hạn thanh toán chép vào đơn lúc tạo (P-09 = 20 phút)

/* Mỗi mã minh hoạ một nhánh kiểm tra trong spec */
const VOUCHERS = {
  WELCOME10: { ok: true, percent: 10 },
  HETLUOT:   { msg: ['warning', 'Mã voucher đã hết lượt sử dụng.'] },
  DUNGHET:   { msg: ['warning', 'Bạn đã dùng hết số lần cho phép của mã này (2/2).'] },
  MIN5TR:    { msg: ['warning', 'Đơn hàng chưa đạt giá trị tối thiểu 5.000.000đ để dùng mã này.'] },
  CHIPT:     { msg: ['warning', 'Mã này không áp dụng cho loại gói đang chọn.'] },
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
  el('payBtn').textContent = 'Thanh toán ' + vnd(total);
  el('paidAmount').textContent = vnd(total);
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

  discount = Math.round(BASE * v.percent / 100);
  appliedCode = code;
  renderTotal();
  voucherMsg('success', `Đã áp dụng mã ${code} — giảm ${v.percent}%.`);
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
      showToast('success', 'Thanh toán thành công. Gói đã được kích hoạt.');
    },
  });
}

el('payBtn').addEventListener('click', pay);
el('retryBtn').addEventListener('click', pay);

renderTotal();
