/* 7.2.1 — Bán gói PT.
   Cơ chế: Nhập ngày bắt đầu → ngày kết thúc tự tính theo số tháng của gói →
   lưới tuần chạy hết khoảng gói (1 tháng ≈ 5 tuần, 3 tháng ≈ 13 tuần), không fix cứng.
   T7/CN hiện nhưng không chọn được. T2-T6 mỗi ngày chỉ tick 1 buổi. */
(function () {
  const VAT = 0.08;
  const TODAY = new Date(2026, 8, 20); // 20/09/2026 (prototype date)
  const SLOTS = ['06:00', '07:30', '09:00', '15:00', '16:30', '18:00', '19:30'];
  const DAYS = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
  const vnd = n => Math.round(n).toLocaleString('vi-VN') + 'đ';

  /* Tiện ích ngày */
  const p2 = n => String(n).padStart(2, '0');
  const iso = d => `${d.getFullYear()}-${p2(d.getMonth() + 1)}-${p2(d.getDate())}`;
  const dm = d => `${p2(d.getDate())}/${p2(d.getMonth() + 1)}`;
  const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
  const addMonths = (d, n) => {
    const result = new Date(d);
    result.setMonth(result.getMonth() + n);
    return result;
  };
  const fromIso = s => new Date(s + 'T00:00:00');

  /* Layout calendar: trục 05:00–22:00, mỗi buổi 90 phút (giống trang lịch rảnh PT) */
  const DAY_START = 5 * 60;
  const DAY_END = 22 * 60;
  const SPAN = DAY_END - DAY_START;
  const DUR = 90;
  const toMin = t => Number(t.slice(0, 2)) * 60 + Number(t.slice(3));
  const toStr = m => `${p2(Math.floor(m / 60))}:${p2(m % 60)}`;
  const topPct = t => ((toMin(t) - DAY_START) / SPAN * 100).toFixed(3);
  const hPct = m => (m / SPAN * 100).toFixed(3);

  /* Lịch rảnh PT theo thứ hằng tuần */
  const AVAIL = {
    'PT-01': { T2: ['06:00', '09:00', '18:00', '19:30'], T3: ['09:00', '18:00'], T4: ['06:00', '09:00', '18:00', '19:30'], T5: ['09:00', '16:30', '18:00'], T6: ['06:00', '18:00', '19:30'] },
    'PT-02': { T2: ['07:30', '15:00', '16:30'], T3: ['07:30', '09:00', '15:00'], T4: ['07:30', '15:00', '16:30'], T5: ['07:30', '15:00', '16:30'], T6: ['07:30', '15:00', '16:30'] },
    'PT-03': { T2: ['16:30', '18:00', '19:30'], T3: ['16:30', '19:30'], T4: ['16:30', '18:00', '19:30'], T5: ['18:00', '19:30'], T6: ['16:30', '18:00', '19:30'] },
  };

  /* Buổi đã bán cố định hằng tuần (lặp lại mỗi tuần) */
  const WEEKLY_TAKEN = {
    'PT-01': ['T2|18:00', 'T4|18:00', 'T6|19:30'],
    'PT-02': ['T3|07:30'],
    'PT-03': ['T2|18:00', 'T4|19:30', 'T5|18:00'],
  };

  /* Buổi lẻ đã bán vào ngày cụ thể */
  const DATE_TAKEN = new Set([
    'PT-01|2026-10-07|09:00', 'PT-01|2026-10-12|06:00', 'PT-01|2026-10-21|09:00',
    'PT-01|2026-09-28|06:00', 'PT-01|2026-10-15|16:30',
    'PT-02|2026-10-06|15:00', 'PT-02|2026-10-14|07:30',
    'PT-03|2026-10-05|16:30', 'PT-03|2026-10-19|16:30',
  ]);
  
  /* w = 0 là tuần chứa hôm nay; w âm là tuần đã qua, w dương là tuần tới. */
  const mondayOf = w => addDays(TODAY, 7 * w - (TODAY.getDay() || 7) + 1);
  const weekRange = w => {
    const mon = mondayOf(w);
    const sun = addDays(mon, 6);
    return `${dm(mon)} – ${dm(sun)}`;
  };
  /* Chỉ số tuần của một ngày, so với tuần chứa hôm nay. */
  const weekIndexOf = d => Math.round((addDays(d, -((d.getDay() || 7) - 1)) - mondayOf(0)) / 6048e5);

  /* Không còn giới hạn tuần, cho prev/next chạy tự do. */

  /* DOM refs */
  const q = document.getElementById('ptq');
  const custRows = [...document.querySelectorAll('#ptCust .pick-row')];
  const custEmpty = document.querySelector('#ptCust [data-empty]');
  const blockBox = document.getElementById('ptBlock');
  const blockText = document.getElementById('ptBlockText');
  const cal = document.getElementById('ptCal');
  const wkWrap = document.getElementById('wkWrap');
  const wkEmpty = document.getElementById('wkEmpty');
  const wkLead = document.getElementById('wkLead');
  const wkCount = document.getElementById('wkCount');
  const wkLabel = document.getElementById('wkLabel');
  const wkPrev = document.getElementById('wkPrev');
  const wkNext = document.getElementById('wkNext');
  const wkToday = document.getElementById('wkToday');
  const wkRepeat = document.getElementById('wkRepeat');
  const wkClear = document.getElementById('wkClear');
  const planPanel = document.querySelector('[data-plan]');
  const planList = document.getElementById('planList');
  const planBadge = document.getElementById('planBadge');
  const coupleField = document.querySelector('[data-couple]');
  const partner = document.getElementById('ptPartner');
  const payBtn = document.getElementById('ptPay');
  const hint = document.getElementById('ptHint');
  const startDateInput = document.getElementById('ptStartDate');
  const dateRangeBox = document.getElementById('ptDateRange');
  const rangeText = document.getElementById('ptRangeText');

  /* State */
  let week = 0;
  const sel = new Map(); // 'yyyy-mm-dd' → slot (1 slot/ngày)
  let startDate = null;
  let endDate = null;

  const pickedCust = () => document.querySelector('input[name="ptcust"]:checked');
  const pickedCoach = () => document.querySelector('input[name="ptcoach"]:checked');
  const kindEl = () => document.querySelector('input[name="ptkind"]:checked');
  const kind = () => {
    const k = kindEl();
    return {
      id: k.value,
      months: +k.dataset.months,
      price: +k.dataset.price,
      min: +k.dataset.min,
      max: +k.dataset.max,
      couple: k.hasAttribute('data-couple-kind'),
      label: k.closest('.pt-option').querySelector('b').childNodes[0].textContent.trim()
    };
  };

  const countWeek = w => [...sel.keys()].filter(d => {
    const date = fromIso(d);
    const mon = mondayOf(w);
    const sun = addDays(mon, 6);
    return date >= mon && date <= sun;
  }).length;

  function slotState(coach, date, day, slot) {
    const dateStr = iso(date);
    
    // Check range
    if (startDate && date < startDate) return 'out';
    if (endDate && date > endDate) return 'out';
    
    // Check PT availability
    if (!(AVAIL[coach]?.[day] || []).includes(slot)) return 'none';
    
    // Check past
    if (date < TODAY) return 'past';
    
    // Check busy
    if ((WEEKLY_TAKEN[coach] || []).includes(day + '|' + slot)
      || DATE_TAKEN.has(`${coach}|${dateStr}|${slot}`)) return 'busy';
    
    // Check if day already has pick
    const dayPick = sel.get(dateStr);
    if (dayPick && dayPick !== slot) return 'day-full';
    
    return 'free';
  }

  /* ===== Tìm khách ===== */
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

  document.querySelectorAll('input[name="ptcust"]').forEach(r =>
    r.addEventListener('change', () => {
      const b = r.closest('.pick-row').dataset.block;
      blockBox.hidden = !b;
      if (b) blockText.innerHTML = `<b>Không bán được gói PT.</b> ${b}`;
      update();
    }));

  document.querySelectorAll('input[name="ptkind"]').forEach(r =>
    r.addEventListener('change', () => {
      const k = kind();
      coupleField.hidden = !k.couple;
      
      // Tự tính ngày kết thúc từ ngày bắt đầu + số tháng (nếu đã chọn ngày bắt đầu)
      if (startDate && k.months > 0) {
        endDate = addMonths(startDate, k.months);
      } else {
        endDate = null;
      }
      
      week = startDate ? weekIndexOf(startDate) : 0;
      if (sel.size) showToast('info', 'Đã đổi loại gói, các buổi đã chọn được xoá.');
      sel.clear();
      renderAll();
    }));

  partner.addEventListener('change', update);

  document.querySelectorAll('input[name="ptcoach"]').forEach(r =>
    r.addEventListener('change', () => {
      sel.clear();
      renderAll();
    }));

  /* ===== Date input ===== */
  startDateInput.addEventListener('change', () => {
    const val = startDateInput.value;
    startDate = val ? fromIso(val) : null;
    
    const k = kindEl();
    if (startDate && k && k.dataset.months && +k.dataset.months > 0) {
      const months = +k.dataset.months;
      endDate = addMonths(startDate, months);
    } else {
      endDate = null;
    }
    
    // Nhảy về tuần chứa ngày bắt đầu, và bỏ các buổi đã rơi ra ngoài khoảng mới
    week = startDate ? weekIndexOf(startDate) : 0;
    dropOutOfRange();
    renderAll();
  });

  /* Buổi nằm ngoài khoảng gói thì không giữ lại được. */
  function dropOutOfRange() {
    if (!startDate || !endDate) return;
    let dropped = 0;
    [...sel.keys()].forEach(d => {
      const date = fromIso(d);
      if (date < startDate || date > endDate) { sel.delete(d); dropped++; }
    });
    if (dropped) showToast('info', `Đã bỏ ${dropped} buổi nằm ngoài khoảng gói.`);
  }

  /* ===== Render calendar theo tuần (giống trang lịch rảnh PT) ===== */
  function renderGrid() {
    const coach = pickedCoach();
    const k = kind();
    wkWrap.hidden = !coach;
    wkEmpty.hidden = !!coach;
    wkCount.hidden = !coach || k.id === 'one';
    
    // Hiện khoảng ngày gói
    if (startDate && endDate && k.id !== 'one') {
      dateRangeBox.hidden = false;
      rangeText.textContent = `${dm(startDate)} – ${dm(endDate)}`;
    } else {
      dateRangeBox.hidden = true;
    }
    
    if (!coach) return;

    const name = coach.dataset.name;
    wkLead.textContent = k.id === 'one' 
      ? 'Buổi lẻ - PT chốt giờ trực tiếp với khách.'
      : `Chọn ngày bắt đầu gói, hệ thống tự tính khoảng theo loại gói. Tick các buổi rảnh của PT trong range (T2-T6, 1 buổi/ngày).`;
    
    if (k.id === 'one') {
      cal.querySelectorAll('.cal-col').forEach(c => c.innerHTML = '');
      return;
    }

    /* Điều hướng tuần — không giới hạn, prev/next tự do */
    wkLabel.textContent = `${weekRange(week)} · ${countWeek(week)} buổi`;

    /* Nhãn cột ngày */
    DAYS.forEach((d, di) => {
      const date = addDays(mondayOf(week), di);
      document.getElementById(`calHead${di}`).innerHTML = `<b style="display:block">${d}</b><em>${dm(date)}</em>`;
    });

    /* Cột calendar: mỗi buổi rảnh của PT hiển thị như một cal-slot */
    DAYS.forEach((d, di) => {
      const date = addDays(mondayOf(week), di);
      const dateStr = iso(date);
      const isWeekend = di >= 5;
      const col = cal.querySelector(`.cal-col[data-col="${di}"]`);

      if (isWeekend) { col.innerHTML = ''; return; }

      const daySlots = AVAIL[coach.value]?.[d] || [];
      col.innerHTML = daySlots.map(slot => {
        const st = slotState(coach.value, date, d, slot);
        const picked = sel.get(dateStr) === slot;
        const start = toMin(slot);

        const label = st === 'out' ? 'Ngoài khoảng gói'
          : st === 'busy' ? name
          : st === 'past' ? 'Đã qua'
          : st === 'day-full' ? 'Đã chọn buổi khác'
          : picked ? 'Đã chọn' : 'Còn trống';

        const title = st === 'out' ? `Ngoài khoảng ${startDate ? dm(startDate) : '?'} – ${endDate ? dm(endDate) : '?'}`
          : st === 'busy' ? `${name} đã có khách ${d} ${dm(date)} ${slot}`
          : st === 'past' ? 'Buổi đã qua'
          : st === 'day-full' ? `${d} ${dm(date)} đã chọn ${sel.get(dateStr)}, bỏ buổi đó trước`
          : picked ? `Bỏ chọn ${d} ${dm(date)} ${slot}`
          : `Chọn ${d} ${dm(date)} ${slot}`;

        const disabled = st !== 'free' && !picked;
        const state = picked ? 'draft' : (st === 'busy' ? 'booked' : 'open');

        return `<button class="cal-slot" type="button" data-state="${state}"
                  data-date="${dateStr}" data-slot="${slot}" data-day="${d}"
                  aria-pressed="${picked}" title="${title}"
                  style="top:${topPct(slot)}%;height:${hPct(DUR)}%"
                  ${disabled ? 'disabled' : ''}>
                  <b>${slot} – ${toStr(start + DUR)}</b><span>${label}</span>
                </button>`;
      }).join('');
    });
    
    /* Total count badge - không ép đủ số buổi, chỉ hiện đã chọn bao nhiêu */
    wkCount.textContent = `${sel.size} buổi đã chọn`;
    wkCount.className = 'badge ' + (sel.size ? 'badge-ok' : 'badge-muted');
  }

  cal.addEventListener('click', e => {
    const cell = e.target.closest('.cal-slot');
    if (!cell || cell.disabled) return;
    
    const k = kind();
    const dateStr = cell.dataset.date;
    const slot = cell.dataset.slot;
    const picked = sel.get(dateStr);

    if (picked === slot) {
      sel.delete(dateStr);
    } else {
      sel.set(dateStr, slot);
    }
    
    renderAll();
  });

  /* Week navigation */
  wkPrev.addEventListener('click', () => { week--; renderGrid(); });
  wkNext.addEventListener('click', () => { week++; renderGrid(); });
  wkToday.addEventListener('click', () => { week = startDate ? weekIndexOf(startDate) : 0; renderGrid(); });

  /* Week actions */
  wkRepeat.addEventListener('click', () => {
    const coach = pickedCoach();
    const k = kind();
    if (!coach || !endDate) return;
    
    const thisWeek = [...sel.entries()].filter(([d]) => {
      const date = fromIso(d);
      const mon = mondayOf(week);
      const sun = addDays(mon, 6);
      return date >= mon && date <= sun;
    });
    
    if (!thisWeek.length) return showToast('info', 'Tuần này chưa chọn buổi nào.');
    
    let added = 0;
    const lastWeek = weekIndexOf(endDate);
    for (let w = week + 1; w <= lastWeek; w++) {
      thisWeek.forEach(([srcDate, slot]) => {
        const src = fromIso(srcDate);
        const dayOfWeek = (src.getDay() + 6) % 7; // Mon=0...Sun=6
        const target = addDays(mondayOf(w), dayOfWeek);
        const targetStr = iso(target);
        const day = DAYS[dayOfWeek];
        
        if (sel.has(targetStr)) return;
        
        const st = slotState(coach.value, target, day, slot);
        if (st === 'free') {
          sel.set(targetStr, slot);
          added++;
        }
      });
    }
    
    if (added) {
      showToast('success', `Đã thêm ${added} buổi từ tuần này sang các tuần sau.`);
      renderAll();
    } else {
      showToast('info', 'Không tìm thấy buổi trống tương ứng ở các tuần sau.');
    }
  });

  wkClear.addEventListener('click', () => {
    const mon = mondayOf(week);
    const sun = addDays(mon, 6);
    let count = 0;
    [...sel.keys()].forEach(d => {
      const date = fromIso(d);
      if (date >= mon && date <= sun) {
        sel.delete(d);
        count++;
      }
    });
    if (count) {
      showToast('info', `Đã xoá ${count} buổi ở tuần này.`);
      renderAll();
    }
  });

  /* ===== Danh sách buổi đã chọn ===== */
  function renderPlan() {
    const k = kind();
    const list = [...sel.entries()].map(([date, slot]) => ({ date, slot }))
      .sort((a, b) => a.date.localeCompare(b.date));
    
    planPanel.hidden = !list.length || k.id === 'one';
    if (planPanel.hidden) return;

    planList.innerHTML = list.map(({ date, slot }) => {
      const d = fromIso(date);
      const dayOfWeek = (d.getDay() + 6) % 7;
      const dayLabel = DAYS[dayOfWeek];
      return `<div class="plan-row" data-plan-date="${date}">
        <div class="plan-when">
          <b class="tabular">${dm(d)}</b>
          <span>${dayLabel}</span>
          <b class="tabular plan-slot">${slot}</b>
        </div>
        <button class="btn btn-ghost btn-sm" type="button" data-remove>Bỏ buổi</button>
      </div>`;
    }).join('');

    planBadge.className = 'badge ' + (list.length ? 'badge-ok' : 'badge-muted');
    planBadge.textContent = `${list.length} buổi`;
  }

  planList.addEventListener('click', e => {
    const row = e.target.closest('[data-plan-date]');
    if (!row || !e.target.closest('[data-remove]')) return;
    
    const date = row.dataset.planDate;
    const slot = sel.get(date);
    sel.delete(date);
    renderAll();
    
    const d = fromIso(date);
    const dayOfWeek = (d.getDay() + 6) % 7;
    const dayLabel = DAYS[dayOfWeek];
    showToast('info', `Đã bỏ buổi ${dayLabel} ${dm(d)} ${slot}.`);
  });

  /* ===== Tổng đơn ===== */
  function update() {
    const c = pickedCust();
    const coach = pickedCoach();
    const k = kind();
    const list = [...sel.entries()];
    const net = Math.round(k.price / (1 + VAT));
    const vat = k.price - net;

    const set = (n, v) => { document.querySelector(`[data-sum-${n}]`).textContent = v; };
    set('cust', c ? c.dataset.name : '—');
    set('kind', k.label + (k.couple && partner.value ? ` · cùng ${partner.selectedOptions[0].textContent.split(' · ')[1]}` : ''));
    set('coach', coach ? coach.dataset.name : '—');
    set('slots', k.id === 'one' ? 'Chốt giờ trực tiếp' : list.length ? `${list.length} buổi` : '—');
    set('plan', k.id === 'one' ? 'Một buổi hôm nay'
      : startDate && endDate ? `${dm(startDate)} – ${dm(endDate)}` : '—');
    set('sub', vnd(k.price));
    set('net', vnd(net));
    set('vat', vnd(vat));
    set('total', vnd(k.price));

    let block = '';
    if (!c) block = '';
    else if (c.closest('.pick-row').dataset.block) block = 'Khách không đủ điều kiện mua gói PT.';
    else if (k.couple && !partner.value) block = 'Chọn người tập cùng.';
    else if (k.couple && partner.value === c.value) block = 'Người tập cùng phải khác khách đứng tên.';
    else if (!coach) block = 'Chọn huấn luyện viên.';
    else if (k.id !== 'one' && !startDate) block = 'Chọn ngày bắt đầu gói.';
    else if (k.id !== 'one' && !list.length) block = 'Tick ít nhất 1 buổi.';

    payBtn.disabled = !c || !!block;
    hint.hidden = !c;
    hint.textContent = block || (k.id === 'one'
      ? 'Buổi lẻ tính ngay hôm nay, PT chốt giờ với khách.'
      : `Thu tiền là chốt cả ${list.length} buổi.`);
  }

  function renderAll() {
    renderGrid();
    renderPlan();
    update();
  }

  payBtn.addEventListener('click', () => {
    const c = pickedCust();
    const coach = pickedCoach();
    const k = kind();
    const list = [...sel.entries()].sort((a, b) => a[0].localeCompare(b[0]));

    openConfirmModal({
      title: 'Xác nhận đơn gói PT',
      body: `<p><b>${c.dataset.name}</b> · ${k.label}</p>
             <p>Huấn luyện viên ${coach.dataset.name}.</p>
             ${k.id === 'one' ? '<p>Buổi lẻ 90 phút hôm nay.</p>'
             : `<p>${list.length} buổi từ ${dm(startDate)} đến ${dm(endDate)}.</p>`}
             <p>Thu <b>${vnd(k.price)}</b>.</p>`,
      confirmLabel: 'Kết đơn',
      danger: false,
      onConfirm() {
        showToast('success', `Đã bán ${k.label} cho ${c.dataset.name}.`);
        setTimeout(() => { location.href = 'ca-lam-viec.html'; }, 1000);
      },
    });
  });

  filter();
  renderAll();
})();
