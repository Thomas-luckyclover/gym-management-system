/* 7.1.3 — Bán gói tập tại quầy (gồm cả nhánh gia hạn 7.3.2). */
(function () {
  const VAT = 0.08;                              // giá gói ĐÃ GỒM VAT, chỉ tách ra để hiển thị
  const VOUCHERS = {
    CHAOBAN10: { off: 10, min: 500000, desc: 'Giảm 10%, đơn từ 500.000đ' },
    THANG9: { off: 15, min: 1500000, desc: 'Giảm 15%, đơn từ 1.500.000đ' },
    HETHAN: { bad: 'Mã đã hết hạn 31/08/2026.' },
    DUNGROI: { bad: 'Khách đã dùng mã này cho đơn trước.' },
  };

  const vnd = n => Math.round(n).toLocaleString('vi-VN') + 'đ';

  const q = document.getElementById('shq');
  const custRows = [...document.querySelectorAll('#shCust .pick-row')];
  const custEmpty = document.querySelector('#shCust [data-empty]');
  const renewBox = document.getElementById('shRenew');
  const renewText = document.getElementById('shRenewText');
  const photoBox = document.getElementById('shPhoto');
  const payBtn = document.getElementById('shPay');
  const blockNote = document.getElementById('shBlock');

  let voucher = null;
  let photoDone = false;

  /* ---------- tìm khách ---------- */
  function filter() {
    const k = viKey(q.value.trim());
    let shown = 0;
    custRows.forEach(r => {
      const ok = viKey(r.dataset.q).includes(k);
      r.hidden = !ok;
      if (ok) shown++;
    });
    custEmpty.hidden = shown > 0;
  }
  q.addEventListener('input', filter);

  /* ---------- lựa chọn ---------- */
  const pickedCust = () => document.querySelector('input[name="shcust"]:checked');
  const pickedPkg = () => document.querySelector('input[name="shpkg"]:checked');
  const isDayPass = () => pickedPkg()?.closest('.pick-row').dataset.day === '1';

  document.querySelectorAll('input[name="shcust"]').forEach(r =>
    r.addEventListener('change', () => {
      const row = r.closest('.pick-row');
      renewBox.hidden = !row.dataset.renew;
      if (row.dataset.renew) renewText.textContent = `Gói hiện tại: ${row.dataset.renew}.`;
      photoDone = false;
      update();
    }));

  document.querySelectorAll('input[name="shpkg"]').forEach(r => r.addEventListener('change', update));

  document.getElementById('shCam').addEventListener('click', () => {
    openConfirmModal({
      title: 'Chụp ảnh check-in',
      body: '<p>Camera quầy sẽ chụp và lưu vào hồ sơ khách. Bắt buộc với mọi gói trừ vé ngày.</p>',
      confirmLabel: 'Chụp và lưu',
      danger: false,
      onConfirm() {
        photoDone = true;
        pickedCust().closest('.pick-row').removeAttribute('data-nophoto');
        showToast('success', 'Đã lưu ảnh check-in.');
        update();
      },
    });
  });

  /* ---------- voucher ---------- */
  const vcForm = document.getElementById('shVcForm');
  const vcInput = document.getElementById('shVc');
  const vcOk = document.getElementById('shVcOk');

  vcForm.addEventListener('submit', e => {
    e.preventDefault();
    clearErr('shVcErr');
    const code = vcInput.value.trim().toUpperCase();
    const v = VOUCHERS[code];

    if (!code) return;
    if (!v) { fieldError(vcInput, 'shVcErr', 'Mã không tồn tại. Kiểm tra lại với khách.'); return; }
    if (v.bad) { fieldError(vcInput, 'shVcErr', v.bad); return; }

    const sub = subtotal();
    if (sub < v.min) {
      fieldError(vcInput, 'shVcErr', `Mã này áp dụng cho đơn từ ${vnd(v.min)}. Đơn hiện tại ${vnd(sub)}.`);
      return;
    }
    voucher = { code, ...v };
    vcOk.querySelector('[data-vc-code]').textContent = code;
    vcOk.querySelector('[data-vc-desc]').textContent = v.desc;
    vcOk.hidden = false;
    vcInput.value = '';
    update();
  });

  document.getElementById('shVcDrop').addEventListener('click', () => {
    voucher = null;
    vcOk.hidden = true;
    update();
  });

  document.querySelectorAll('[data-vc]').forEach(b =>
    b.addEventListener('click', () => {
      vcInput.value = b.dataset.vc;
      vcForm.requestSubmit();
    }));

  /* ---------- tổng đơn ---------- */
  function subtotal() {
    const p = pickedPkg();
    return p ? Number(p.closest('.pick-row').dataset.price) : 0;
  }

  function totals() {
    const sub = subtotal();
    const off = voucher && sub >= voucher.min ? sub * voucher.off / 100 : 0;
    const total = sub - off;                       // khách trả đúng giá gói sau giảm
    const net = Math.round(total / (1 + VAT));     // tách ngược phần chưa thuế
    return { sub, off, net, vat: total - net, total };
  }

  function update() {
    const c = pickedCust();
    const p = pickedPkg();
    const t = totals();

    /* voucher đang áp mà đơn tụt xuống dưới mức tối thiểu thì gỡ luôn */
    if (voucher && t.sub < voucher.min) {
      showToast('warning', `Đã gỡ mã ${voucher.code}: đơn không còn đạt mức tối thiểu.`);
      voucher = null;
      vcOk.hidden = true;
      return update();
    }

    const set = (k, v) => { document.querySelector(`[data-sum-${k}]`).textContent = v; };
    set('cust', c ? c.dataset.name : '—');
    set('pkg', p ? p.dataset.name : '—');
    set('sub', vnd(t.sub));
    set('net', vnd(t.net));
    set('vat', vnd(t.vat));
    set('total', vnd(t.total));
    document.querySelector('[data-sum-discount-row]').hidden = t.off === 0;
    if (t.off) set('discount', '− ' + vnd(t.off));

    /* vé ngày là ngoại lệ duy nhất không cần ảnh check-in */
    const needPhoto = c && c.closest('.pick-row').hasAttribute('data-nophoto') && !isDayPass();
    photoBox.hidden = !needPhoto;

    let block = '';
    if (!c || !p) block = '';                     // chưa chọn gì thì nút mờ là đủ, không nhắc
    else if (needPhoto) block = 'Chụp ảnh check-in trước khi kết đơn.';

    payBtn.disabled = !c || !p || !!block;
    blockNote.hidden = !c || !p;
    blockNote.textContent = block || 'Kiểm tra lại số tiền với khách trước khi thu.';
  }

  /* ---------- kết đơn ---------- */
  payBtn.addEventListener('click', () => {
    const t = totals();
    const c = pickedCust();
    const p = pickedPkg();
    const pay = document.querySelector('input[name="shpay"]:checked').value;
    const payName = pay === 'cash' ? 'tiền mặt' : 'chuyển khoản';

    openConfirmModal({
      title: 'Xác nhận kết đơn',
      body: `<p><b>${c.dataset.name}</b> · ${p.dataset.name}</p>
             <p>Thu <b>${vnd(t.total)}</b> bằng ${payName}${voucher ? `, đã áp mã ${voucher.code}` : ''}. Giá đã gồm VAT 8% (${vnd(t.vat)}).</p>
             ${pay === 'bank' ? '<p class="muted">Chỉ xác nhận khi đã thấy biến động số dư.</p>'
                              : '<p class="muted">Tiền mặt được cộng vào ca đang mở, đối chiếu lúc chốt ca.</p>'}`,
      confirmLabel: 'Kết đơn và in phiếu',
      danger: false,
      onConfirm() {
        showToast('success', `Đã thu ${vnd(t.total)} của ${c.dataset.name}.`);
        setTimeout(() => { location.href = 'ca-lam-viec.html'; }, 1000);
      },
    });
  });

  filter();
  update();
})();
