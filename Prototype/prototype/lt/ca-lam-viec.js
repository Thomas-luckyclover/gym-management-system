/* 7.4.1 — Ca làm việc: theo dõi giao dịch, huỷ đơn có lý do, chốt ca đối chiếu tiền mặt. */
(function () {
  const vnd = n => Math.round(n).toLocaleString('vi-VN') + 'đ';
  const num = s => Number(String(s).replace(/\D/g, ''));

  const hero = document.getElementById('shiftHero');
  const btnClose = document.getElementById('btnClose');
  const btnOpen = document.getElementById('btnOpen');
  const pendBox = document.getElementById('pendBox');
  const kPend = document.getElementById('kPend');
  const kCash = document.getElementById('kCash');
  const kBank = document.getElementById('kBank');
  const kAll = document.getElementById('kAll');

  let pending = 1;
  let open = true;

  /* ---------- bộ lọc danh sách giao dịch ---------- */
  const q = document.getElementById('txq');
  const fPay = document.getElementById('txPay');
  const fSt = document.getElementById('txSt');

  const list = makeList({
    listSel: '#txList',
    perPage: 6,
    unit: 'giao dịch',
    matcher(row) {
      return viKey(row.dataset.q).includes(viKey(q.value.trim()))
        && (fPay.value === 'all' || row.dataset.pay === fPay.value)
        && (fSt.value === 'all' || row.dataset.st === fSt.value);
    },
  });
  [q, fPay, fSt].forEach(el => el.addEventListener('input', list.reset));
  document.getElementById('txReset').addEventListener('click', () => {
    q.value = ''; fPay.value = 'all'; fSt.value = 'all';
    list.reset();
  });
  list.render();
  document.getElementById('txApply')?.addEventListener('click', list.reset);

  /* ---------- tổng lại KPI sau mỗi thay đổi ---------- */
  function recount() {
    let cash = 0, bank = 0, n = 0, nCash = 0, nBank = 0;
    list.rows.forEach(r => {
      if (r.dataset.st !== 'ok') return;          // chỉ đơn Paid mới tính vào ca
      const amt = num(r.querySelector('.row-end b').textContent);
      if (r.dataset.pay === 'cash') { cash += amt; nCash++; } else { bank += amt; nBank++; }
      n++;
    });
    kCash.nextElementSibling.textContent = nCash + ' hoá đơn';
    kBank.nextElementSibling.textContent = nBank + ' hoá đơn';
    kAll.nextElementSibling.textContent = n + ' hoá đơn hợp lệ';
    kCash.textContent = vnd(cash);
    kBank.textContent = vnd(bank);
    kAll.textContent = vnd(cash + bank);
    kPend.textContent = String(pending);
    return cash;
  }

  /* ---------- huỷ đơn: bắt buộc nêu lý do ---------- */
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-void]');
    if (!b) return;
    if (!open) { showToast('warning', 'Ca đã chốt. Đơn cũ chỉ Quản lý mới điều chỉnh được.'); return; }
    const row = b.closest('.data-row');

    openConfirmModal({
      title: `Huỷ ${b.dataset.void}`,
      body: `<p>Đơn bị huỷ vẫn nằm trong sổ ca để đối chiếu, nhưng không tính vào doanh thu.</p>
             <label class="field"><span class="field-label">Lý do huỷ</span>
               <textarea id="voidReason" rows="3" placeholder="Khách đổi ý, thu nhầm gói, quẹt lỗi…"></textarea>
               <span class="field-error" id="voidReasonErr" hidden></span></label>`,
      confirmLabel: 'Huỷ đơn',
      onConfirm() {
        const r = document.getElementById('voidReason');
        if (r.value.trim().length < 5) {
          fieldError(r, 'voidReasonErr', 'Nêu lý do cụ thể, tối thiểu 5 ký tự.');
          return false;
        }
        row.dataset.st = 'void';
        row.querySelector('.row-title').insertAdjacentHTML('beforeend', ' <span class="badge badge-danger">Đã huỷ</span>');
        row.querySelector('.row-meta').insertAdjacentHTML('beforeend',
          ` <span><i class="fa-solid fa-circle-info"></i> ${r.value.trim()}</span>`);
        row.querySelector('.row-end b').classList.add('is-void');
        b.remove();
        recount();
        list.render();
        showToast('success', `Đã huỷ ${row.dataset.q.split(' ')[0]}.`);
      },
    });
  });

  /* ---------- đơn chờ xác nhận chuyển khoản ---------- */
  document.getElementById('pendOk').addEventListener('click', () => {
    pending = 0;
    pendBox.hidden = true;
    const row = list.rows.find(r => r.dataset.st === 'pending');
    if (row) {
      row.dataset.st = 'ok';                      // đơn chuyển Paid, giao dịch Success
      row.querySelector('.badge-warn').outerHTML = '<span class="badge badge-ok">Đã thanh toán</span>';
    }
    recount();
    showToast('success', 'Đã xác nhận DH-2026-1198. Ca không còn đơn treo.');
  });

  /* ---------- chốt ca ---------- */
  btnClose.addEventListener('click', () => {
    if (pending > 0) {
      showToast('error', `Còn ${pending} đơn chờ xử lý. Giải quyết xong mới chốt ca được.`);
      pendBox.scrollIntoView({ behavior: 'smooth', block: 'center' });
      return;
    }
    const expect = recount();

    openConfirmModal({
      title: 'Chốt ca chiều 20/09',
      body: `<p>Đếm tiền mặt trong ngăn kéo và nhập số thực tế. Lệch so với sổ thì phải ghi lý do.</p>
             <dl class="sum-list">
               <div><dt>Tiền mặt theo sổ</dt><dd class="tabular">${vnd(expect)}</dd></div>
             </dl>
             <label class="field"><span class="field-label">Tiền mặt đếm được</span>
               <input id="cashCount" type="text" inputmode="numeric" value="${expect.toLocaleString('vi-VN')}">
               <span class="field-error" id="cashCountErr" hidden></span></label>
             <label class="field"><span class="field-label">Lý do chênh lệch</span>
               <textarea id="cashNote" rows="3" placeholder="Chỉ bắt buộc khi số đếm khác số sổ"></textarea>
               <span class="field-error" id="cashNoteErr" hidden></span></label>`,
      confirmLabel: 'Chốt ca',
      danger: false,
      onConfirm() {
        const got = num(document.getElementById('cashCount').value);
        const note = document.getElementById('cashNote');
        const diff = got - expect;

        if (!got && got !== 0) {
          fieldError(document.getElementById('cashCount'), 'cashCountErr', 'Nhập số tiền mặt đếm được.');
          return false;
        }
        if (diff !== 0 && note.value.trim().length < 5) {
          fieldError(note, 'cashNoteErr',
            `Lệch ${diff > 0 ? 'thừa' : 'thiếu'} ${vnd(Math.abs(diff))}. Ghi rõ lý do để Quản lý đối chiếu.`);
          return false;
        }
        closeShift(diff);
      },
    });
  });

  function closeShift(diff) {
    open = false;
    hero.querySelector('h2').textContent = 'Đã chốt lúc ' + sampleNow().toTimeString().slice(0, 5);
    hero.querySelector('p').textContent = diff === 0
      ? 'Tiền mặt khớp sổ. Sổ ca đã gửi lên Quản lý.'
      : `Lệch ${diff > 0 ? 'thừa' : 'thiếu'} ${vnd(Math.abs(diff))}, đã ghi kèm lý do. Sổ ca đã gửi lên Quản lý.`;
    btnClose.hidden = true;
    btnOpen.hidden = true;                  // chỉ có ca sáng và ca chiều; ca sáng mai mở vào 21/09

    const chip = document.getElementById('shiftChip');
    chip.className = 'badge badge-muted';
    chip.innerHTML = '<i class="fa-solid fa-circle-check"></i> Ca chiều 20/09 đã chốt';

    document.querySelectorAll('[data-void]').forEach(b => b.remove());
    showToast('success', 'Đã chốt ca và gửi sổ lên Quản lý.');
  }

  btnOpen.addEventListener('click', () => {
    openConfirmModal({
      title: 'Mở ca sáng 21/09',
      body: '<p>Ca mới bắt đầu từ bây giờ. Mọi hoá đơn phát sinh sau thời điểm này tính vào ca sáng.</p>',
      confirmLabel: 'Mở ca',
      danger: false,
      onConfirm() { location.reload(); },
    });
  });

  /* đơn vừa kết ở ban-hang.html / ban-goi-pt.html thuộc ca đang mở: thêm lên đầu danh sách */
  try {
    const raw = sessionStorage.getItem('gms_new_order');
    if (raw) {
      sessionStorage.removeItem('gms_new_order');
      const o = JSON.parse(raw);
      const wrap = document.querySelector('#txList');
      const row = document.createElement('div');
      row.className = 'data-row';
      row.dataset.pay = o.pay;
      row.dataset.st = 'ok';
      row.dataset.q = `${o.code} ${o.cust} ${o.pkg}`;
      row.innerHTML = `<div class="row-main"><div class="row-title">${o.code} · ${o.cust} <span class="badge badge-ok">Đã thanh toán</span></div>
        <div class="row-meta"><span><i class="fa-solid fa-clock"></i> ${o.time}</span><span><i class="fa-solid fa-dumbbell"></i> ${o.pkg}</span>
        <span class="badge badge-muted">${o.pay === 'cash' ? 'Tiền mặt' : 'Chuyển khoản'}</span></div></div>
        <div class="row-end"><b class="tabular">${o.amount.toLocaleString('vi-VN')}đ</b>
        <button class="btn btn-ghost btn-sm" type="button" data-void="${o.code}">Huỷ đơn</button></div>`;
      wrap.prepend(row);
      list.rows.unshift(row);
      showToast('success', `${o.code} đã ghi vào ca chiều 20/09.`);
      list.reset();
    }
  } catch {}

  recount();
})();
