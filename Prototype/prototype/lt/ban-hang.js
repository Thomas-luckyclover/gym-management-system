/* 7.1.3 — Bán gói tập tại quầy (gồm cả nhánh gia hạn 7.3.2). */
(function () {
  const VAT = 0.08;                              // giá gói ĐÃ GỒM VAT, chỉ tách ra để hiển thị
  const VOUCHERS = {
    GYMVUI10: { off: 10, min: 500000, desc: 'Giảm 10%, đơn từ 500.000đ' },
    NGAY15K: { amount: 15000, min: 100000, desc: 'Giảm 15.000đ, đơn từ 100.000đ' },
    HE2026: { bad: 'Mã đã hết hạn 31/08/2026.' },
    HETLUOT25: { bad: 'Mã đã hết lượt sử dụng.' },
  };

  const vnd = n => Math.round(n).toLocaleString('vi-VN') + 'đ';

  const q = document.getElementById('shq');
  let custRows = [...document.querySelectorAll('#shCust .pick-row')];
  const pkgRows = [...document.querySelectorAll('#shPkg .pick-row')];
  const custEmpty = document.querySelector('#shCust [data-empty]');
  const renewBox = document.getElementById('shRenew');
  const renewText = document.getElementById('shRenewText');
  const photoBox = document.getElementById('shPhoto');
  const payBtn = document.getElementById('shPay');
  const blockNote = document.getElementById('shBlock');
  const singlePanel = document.getElementById('singlePanel');
  const singleCoach = document.getElementById('singleCoach');
  const singleTime = document.getElementById('singleTime');
  const singleHold = document.getElementById('singleHold');
  const singleHoldClock = document.getElementById('singleHoldClock');
  const singleCollect = document.getElementById('singleCollect');
  const singleRelease = document.getElementById('singleRelease');
  let singleHoldLeft = 0, singleHoldTimer = null;

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

  /* tìm gói theo tên */
  const pq = document.getElementById('shpq');
  pq.addEventListener('input', () => {
    const k = viKey(pq.value.trim());
    pkgRows.forEach(r => { r.hidden = !viKey(r.querySelector('b').textContent).includes(k); });
  });

  /* ngày bắt đầu: tự động (nối tiếp nếu là gia hạn) hoặc chọn tay */
  const startDate = document.getElementById('shStartDate');
  const dmy = d => `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
  document.querySelectorAll('input[name="shstart"]').forEach(r => r.addEventListener('change', () => {
    startDate.hidden = document.querySelector('input[name="shstart"]:checked').value !== 'custom';
    update();
  }));
  startDate.addEventListener('input', update);
  const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
  const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  /* ngày kết thúc gói hiện tại của khách (nếu còn hạn và là gói tập) → gói mới nối tiếp */
  function chainedFrom() {
    const c = pickedCust();
    const rm = c && c.closest('.pick-row').dataset.renew;
    const m = rm && /Gói \d+ Tháng/.test(rm) && rm.match(/(\d{2})\/(\d{2})\/(\d{4})/);
    if (!m) return null;
    const end = new Date(+m[3], +m[2] - 1, +m[1]);
    return end >= sampleNow() ? end : null;
  }
  /* CBR-119: Gói Ngày bắt đầu ngay hôm nay; gói tập nối tiếp gói đang dùng (ngày sớm nhất = hôm sau ngày hết hạn)
     hoặc bắt đầu hôm nay; tại quầy có thể hẹn ngày khác trong 7 ngày kể từ ngày sớm nhất */
  function earliest() { const e = chainedFrom(); return e ? addDays(e, 1) : sampleNow(); }
  function applyStartRules() {
    const customOpt = document.querySelector('input[name="shstart"][value="custom"]');
    const autoOpt = document.querySelector('input[name="shstart"][value="auto"]');
    if (!customOpt) return;
    const e = earliest(), chained = !!chainedFrom();
    const dayPass = isDayPass();
    document.getElementById('shStartAuto').textContent = chained ? 'Kích hoạt nối tiếp sau gói hiện tại' : 'Kích hoạt ngay hôm nay';
    document.getElementById('shStartAutoHint').textContent = dmy(e);
    const hint = document.getElementById('shStartHint');
    hint.textContent = dayPass ? 'Gói Ngày luôn bắt đầu ngay ngày thanh toán' : `Chọn từ ${dmy(e)} đến ${dmy(addDays(e, 7))} (trong 7 ngày)`;
    customOpt.disabled = dayPass;
    if (dayPass) { autoOpt.checked = true; startDate.hidden = true; }
    startDate.min = iso(e); startDate.max = iso(addDays(e, 7));
    if (!startDate.value || startDate.value < startDate.min || startDate.value > startDate.max) startDate.value = startDate.min;
  }
  function startIso() {
    if (isDayPass()) return iso(sampleNow());
    if (document.querySelector('input[name="shstart"]:checked').value === 'custom' && startDate.value) return startDate.value;
    return iso(earliest());
  }
  function startText() {
    if (isDayPass()) return 'Hôm nay ' + dmy(sampleNow());
    const [y, m, d] = startIso().split('-').map(Number), dt = new Date(y, m - 1, d);
    if (document.querySelector('input[name="shstart"]:checked').value === 'custom') return dmy(dt);
    return chainedFrom() ? dmy(dt) + ' (nối tiếp gói hiện tại)' : 'Hôm nay ' + dmy(dt);
  }

  document.addEventListener('change', e => {
    if (e.target.name === 'qcType') {
      const dep = e.target.value === 'dep';
      document.getElementById('qcEmailWrap').hidden = dep;
      document.getElementById('qcGuardWrap').hidden = !dep;
    }
  });

  /* tạo hồ sơ nhanh kèm tài khoản, chọn luôn khách vừa tạo */
  document.getElementById('shQuick').addEventListener('click', () => {
    openConfirmModal({
      title: 'Tạo hồ sơ',
      body: `<fieldset class="field" style="border:0;padding:0"><span class="field-label">Loại hồ sơ *</span>
               <label class="pt-option"><input type="radio" name="qcType" value="self" checked><span class="pt-body"><b>Tự sở hữu (Độc lập)</b><small>Có tài khoản đăng nhập, mật khẩu tạm gửi qua email</small></span></label>
               <label class="pt-option"><input type="radio" name="qcType" value="dep"><span class="pt-body"><b>Phụ thuộc (Trẻ em/Người thân)</b><small>Không có tài khoản, cần thông tin người giám hộ</small></span></label>
               <small class="muted">Loại hồ sơ không thể thay đổi sau khi tạo.</small></fieldset>
             <label class="field"><span class="field-label">Họ và tên *</span>
               <input id="qcName" type="text" maxlength="50" placeholder="Ví dụ: Nguyễn Văn An">
               <span class="field-error" id="qcNameErr" hidden></span></label>
             <label class="field"><span class="field-label">Số điện thoại *</span>
               <input id="qcPhone" type="tel" maxlength="10" placeholder="0901234567">
               <span class="field-error" id="qcPhoneErr" hidden></span></label>
             <label class="field"><span class="field-label">Ngày sinh *</span>
               <input id="qcDob" type="date" max="2026-09-20">
               <span class="field-error" id="qcDobErr" hidden></span></label>
             <label class="field" id="qcEmailWrap"><span class="field-label">Email *</span>
               <input id="qcEmail" type="email" maxlength="100" placeholder="ten@gmail.com" autocomplete="off">
               <span class="field-hint">Hệ thống tạo tài khoản và gửi mật khẩu tạm tới email này. Nhắc khách kiểm tra cả mục Spam.</span>
               <span class="field-error" id="qcEmailErr" hidden></span></label>
             <div id="qcGuardWrap" hidden>
               <label class="field"><span class="field-label">Tên người giám hộ *</span><input id="qcGName" type="text" maxlength="50"><span class="field-error" id="qcGNameErr" hidden></span></label>
               <label class="field"><span class="field-label">Mối quan hệ *</span><input id="qcGRel" type="text" maxlength="30" placeholder="Bố, mẹ, anh, chị…"><span class="field-error" id="qcGRelErr" hidden></span></label>
               <label class="field"><span class="field-label">SĐT người giám hộ *</span><input id="qcGPhone" type="tel" maxlength="10"><span class="field-error" id="qcGPhoneErr" hidden></span></label>
             </div>`,
      confirmLabel: 'Tạo hồ sơ',
      danger: false,
      onConfirm() {
        const n = document.getElementById('qcName'), ph = document.getElementById('qcPhone'), em = document.getElementById('qcEmail');
        const dep = document.querySelector('input[name="qcType"]:checked').value === 'dep';
        ['qcNameErr', 'qcPhoneErr', 'qcEmailErr', 'qcDobErr', 'qcGNameErr', 'qcGRelErr', 'qcGPhoneErr'].forEach(clearErr);
        let ok = true;
        if (n.value.trim().length < 2) { fieldError(n, 'qcNameErr', 'Nhập họ tên đầy đủ.'); ok = false; }
        if (!/^0\d{9}$/.test(ph.value.trim())) { fieldError(ph, 'qcPhoneErr', 'Số điện thoại phải gồm 10 chữ số và bắt đầu bằng 0.'); ok = false; }
        else if (custRows.some(r => r.dataset.q.includes(ph.value.trim()))) { fieldError(ph, 'qcPhoneErr', 'Số điện thoại này đã có hồ sơ.'); ok = false; }
        if (!document.getElementById('qcDob').value) { fieldError(document.getElementById('qcDob'), 'qcDobErr', 'Chọn ngày sinh.'); ok = false; }
        if (!dep && !/^\S+@\S+\.\S+$/.test(em.value.trim())) { fieldError(em, 'qcEmailErr', 'Email không hợp lệ.'); ok = false; }
        if (dep) {
          const gn = document.getElementById('qcGName'), gr = document.getElementById('qcGRel'), gp = document.getElementById('qcGPhone');
          if (gn.value.trim().length < 2) { fieldError(gn, 'qcGNameErr', 'Nhập tên người giám hộ.'); ok = false; }
          if (!gr.value.trim()) { fieldError(gr, 'qcGRelErr', 'Nhập mối quan hệ.'); ok = false; }
          if (!/^0\d{9}$/.test(gp.value.trim())) { fieldError(gp, 'qcGPhoneErr', 'Số điện thoại người giám hộ không hợp lệ.'); ok = false; }
        }
        if (!ok) return false;
        const name = n.value.trim(), phone = ph.value.trim();
        const row = document.createElement('label');
        row.className = 'pick-row';
        row.dataset.q = `${name} ${phone} KH-1012`;
        row.dataset.nophoto = '';
        row.innerHTML = `<input type="radio" name="shcust" value="KH-1012" data-name="${name}">
          <span class="pick-body"><b>${name}</b><small>KH-1012 · ${formatPhoneDisplay(phone)} · Chưa có gói tập</small></span>
          <span class="badge badge-info">Mới tạo</span>`;
        document.getElementById('shCust').prepend(row);
        custRows = [row, ...custRows];
        row.querySelector('input').addEventListener('change', () => {
          renewBox.hidden = true; photoDone = false; update();
        });
        row.querySelector('input').checked = true;
        renewBox.hidden = true; photoDone = false;
        update();
        showToast('success', 'Đã tạo hồ sơ KH-1012. Chụp ảnh check-in trước khi kết đơn.');
      },
    });
  });

  /* ---------- lựa chọn ---------- */
  const pickedCust = () => document.querySelector('input[name="shcust"]:checked');
  const pickedPkg = () => document.querySelector('input[name="shpkg"]:checked');
  const isDayPass = () => pickedPkg()?.closest('.pick-row').dataset.day === '1';
  const isSingle = () => pickedPkg()?.closest('.pick-row').dataset.single === '1';

  document.querySelectorAll('input[name="shcust"]').forEach(r =>
    r.addEventListener('change', () => {
      const row = r.closest('.pick-row');
      renewBox.hidden = !row.dataset.renew;
      if (row.dataset.renew) renewText.textContent = `Gói hiện tại: ${row.dataset.renew}.`;
      photoDone = false;
      update();
    }));

  document.querySelectorAll('input[name="shpkg"]').forEach(r => r.addEventListener('change', update));
  [singleCoach, singleTime].forEach(el => el?.addEventListener('change', update));

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
    const off = voucher && sub >= voucher.min ? (voucher.amount || Math.round(sub * voucher.off / 100)) : 0;
    const total = sub - off;                       // khách trả đúng giá gói sau giảm
    const net = Math.round(total / (1 + VAT));     // tách ngược phần chưa thuế
    return { sub, off, net, vat: total - net, total };
  }

  function update() {
    const c = pickedCust();
    const p = pickedPkg();
    /* CBR-76: Buổi PT lẻ không áp voucher */
    const single = isSingle();
    if (singlePanel) singlePanel.hidden = !single;
    vcInput.disabled = single;
    vcForm.querySelectorAll('button').forEach(b => { b.disabled = single; });
    document.querySelectorAll('[data-vc]').forEach(b => { b.disabled = single; });
    if (single) {
      voucher = null;
      vcOk.hidden = true;
      fieldError(vcInput, 'shVcErr', 'Voucher không áp dụng cho Buổi PT lẻ.');
    } else if (vcInput.classList.contains('invalid') && !vcInput.value) {
      clearErr('shVcErr');
    }
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
    applyStartRules();
    const startBox = document.getElementById('shStartBox');
    if (startBox) startBox.hidden = single;
    set('start', !p ? '—' : single
      ? (singleTime?.value ? `Hôm nay ${singleTime.value}${singleCoach?.selectedOptions[0]?.value ? ' · PT ' + singleCoach.selectedOptions[0].textContent : ''}` : 'Hôm nay')
      : startText());
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
    else if (single && (!singleCoach?.value || !singleTime?.value)) block = 'Chọn PT và khung giờ hôm nay.';
    else if (needPhoto) block = 'Chụp ảnh check-in trước khi kết đơn.';
    else if (isBank() && !bankOk.checked) block = 'Vui lòng tick xác nhận đã nhận đủ tiền trong tài khoản trước khi hoàn tất.';  // MSG326

    payBtn.disabled = !c || !p || !!block;
    blockNote.hidden = !c || !p;
    blockNote.textContent = block || 'Kiểm tra lại số tiền với khách trước khi thu.';
  }

  /* chuyển khoản: cần mã tham chiếu và xác nhận đã nhận đủ tiền */
  const bankBox = document.getElementById('shBankBox');
  const bankRef = document.getElementById('shBankRef');
  const bankOk = document.getElementById('shBankOk');
  const isBank = () => document.querySelector('input[name="shpay"]:checked').value === 'bank';
  document.querySelectorAll('input[name="shpay"]').forEach(r => r.addEventListener('change', () => { bankBox.hidden = !isBank(); update(); }));
  bankRef.addEventListener('input', update);
  bankOk.addEventListener('change', update);

  /* ---------- kết đơn ---------- */
  function confirmOrder() {
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
        try {
          sessionStorage.setItem('gms_new_order', JSON.stringify({ code: 'DH-2026-1200', cust: c.dataset.name, custCode: c.value, pkg: p.dataset.name, pkgCode: p.value, pay, ref: pay === 'bank' ? bankRef.value.trim() : '', voucher: voucher ? voucher.code : '', sub: t.sub, discount: t.off, vat: t.vat, amount: t.total, start: startText(), startIso: startIso(), earliestIso: iso(earliest()), phone: (c.closest('.pick-row').dataset.q.match(/0\d{9}/) || [''])[0], pkgPrice: t.sub, time: '16:30' }));
        } catch {}
        showToast('success', `Đã thu ${vnd(t.total)} của ${c.dataset.name}.`);
        setTimeout(() => { location.href = 'bien-nhan.html'; }, 800);
      },
    });
  }

  payBtn.addEventListener('click', () => {
    if (!isSingle()) return confirmOrder();
    singleHoldLeft = 20 * 60 + 1;   // P-09: 20 phút
    payBtn.hidden = true;
    singleHold.hidden = false;
    clearInterval(singleHoldTimer);
    const tick = () => {
      singleHoldLeft--;
      singleHoldClock.textContent = `${String(Math.floor(singleHoldLeft / 60)).padStart(2, '0')}:${String(singleHoldLeft % 60).padStart(2, '0')}`;
      if (singleHoldLeft <= 0) { clearInterval(singleHoldTimer); singleHold.hidden = true; payBtn.hidden = false; showToast('warning', 'Hết 20 phút giữ chỗ, khung giờ đã trả lại cho PT.'); }
    };
    tick(); singleHoldTimer = setInterval(tick, 1000);
    showToast('info', 'Đã giữ khung PT 20 phút. Thu tiền trước khi hết giờ.');
  });
  singleCollect?.addEventListener('click', () => { clearInterval(singleHoldTimer); singleHold.hidden = true; confirmOrder(); });
  singleRelease?.addEventListener('click', () => { clearInterval(singleHoldTimer); singleHold.hidden = true; payBtn.hidden = false; showToast('info', 'Đã trả lại khung giờ.'); });

  /* ---------- 3 bước giống hệ thống: chọn gói → chọn khách → xác nhận & thu tiền ---------- */
  let step = 1;
  const STEP_TITLES = { 1: 'Chọn gói cho khách', 2: 'Chọn khách', 3: 'Xác nhận thanh toán' };
  const STEP_LABELS = { 1: 'Bước 1 / 3: Chọn gói', 2: 'Bước 2 / 3: Chọn khách', 3: 'Bước 3 / 3: Xác nhận đơn hàng & Thu tiền' };
  const backBtn = document.getElementById('shBack');
  const nextBtn = document.getElementById('shNext');
  function syncStep() {
    document.querySelectorAll('[data-step]').forEach(sec => {
      let show = Number(sec.dataset.step) === step;
      if (sec.id === 'singlePanel') show = show && isSingle();
      sec.hidden = !show;
    });
    document.getElementById('shTitle').textContent = STEP_TITLES[step];
    document.getElementById('shStepNo').textContent = STEP_LABELS[step];
    backBtn.hidden = step === 1;
    nextBtn.hidden = step === 3;
    nextBtn.textContent = step === 1 ? 'Tiếp tục: tìm khách →' : 'Tiếp tục: xác nhận & thu tiền →';
    nextBtn.disabled = step === 1 ? !pickedPkg() : !pickedCust();
    payBtn.closest('.side-card').querySelector('#shPay').hidden = step !== 3 || !!singleHold && !singleHold.hidden;
  }
  nextBtn.addEventListener('click', () => { step = Math.min(3, step + 1); syncStep(); update(); window.scrollTo(0, 0); });
  backBtn.addEventListener('click', () => { step = Math.max(1, step - 1); syncStep(); window.scrollTo(0, 0); });
  document.addEventListener('change', () => setTimeout(syncStep, 0));
  document.addEventListener('click', () => setTimeout(syncStep, 0));
  document.addEventListener('input', () => setTimeout(syncStep, 0));

  filter();
  update();
  syncStep();
})();
