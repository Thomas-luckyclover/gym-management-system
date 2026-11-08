/* #35 — Chọn gói: tìm theo tên, lọc (loại, thời hạn, hình thức mua, khoảng giá), sắp xếp, 8 gói mỗi trang. */
(function () {
  const PER_PAGE = 8;
  const grid = document.getElementById('pkgGrid');
  const cards = [...grid.querySelectorAll('.price-card')];
  const empty = document.getElementById('pkgEmpty');
  const search = document.getElementById('pkgSearch');
  const kind = document.getElementById('pkgKind');
  const dur = document.getElementById('pkgDur');
  const channel = document.getElementById('pkgChannel');
  const min = document.getElementById('pkgMin');
  const max = document.getElementById('pkgMax');
  const sort = document.getElementById('pkgSort');
  const pager = document.querySelector('[data-pager]');
  const info = pager.querySelector('.pager-info');
  const btns = pager.querySelector('.pager-btns');
  let page = 1;

  /* "tiết kiệm" = giảm so với mua từng tháng theo giá Gói 1 Tháng; Gói Ngày và gói PT không có */
  const saving = c => {
    const m = Number(c.dataset.months);
    return c.dataset.kind === 'goi-tap' && m > 1 ? 1 - Number(c.dataset.price) / (m * 799000) : 0;
  };

  function pgBtn(label, target, { current = false, disabled = false } = {}) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'pg';
    b.innerHTML = label;
    b.disabled = disabled;
    if (current) b.setAttribute('aria-current', 'page');
    else b.addEventListener('click', () => { page = target; render(); grid.scrollIntoView({ behavior: 'smooth', block: 'start' }); });
    return b;
  }

  function render() {
    const lo = min.value === '' ? 0 : Number(min.value);
    const hi = max.value === '' ? Infinity : Number(max.value);
    const q = viKey(search.value.trim());
    const found = cards.filter(c =>
      (!q || viKey(c.dataset.name).includes(q)) &&
      (kind.value === 'all' || c.dataset.kind === kind.value) &&
      (dur.value === 'all' || c.dataset.months === dur.value) &&
      (channel.value === 'all' || c.dataset.channel === channel.value) &&
      Number(c.dataset.price) >= lo && Number(c.dataset.price) <= hi);

    const key = sort.value;
    found.sort((a, b) => key === 'price-asc' ? a.dataset.price - b.dataset.price
                       : key === 'price-desc' ? b.dataset.price - a.dataset.price
                       : key === 'saving' ? saving(b) - saving(a)
                       : cards.indexOf(a) - cards.indexOf(b));
    found.forEach(c => grid.insertBefore(c, empty));

    const pages = Math.max(1, Math.ceil(found.length / PER_PAGE));
    if (page > pages) page = pages;
    const from = (page - 1) * PER_PAGE;
    const shown = found.slice(from, from + PER_PAGE);
    cards.forEach(c => { c.hidden = !shown.includes(c); });
    empty.hidden = found.length > 0;

    pager.hidden = found.length === 0;
    if (!found.length) return;
    info.innerHTML = `<b>${from + 1}–${from + shown.length}</b> trên ${found.length} gói`;
    btns.innerHTML = '';
    btns.append(pgBtn('<i class="fa-solid fa-chevron-left"></i>', page - 1, { disabled: page <= 1 }));
    for (let i = 1; i <= pages; i++) btns.append(pgBtn(String(i), i, { current: i === page }));
    btns.append(pgBtn('<i class="fa-solid fa-chevron-right"></i>', page + 1, { disabled: page >= pages }));
  }

  [search, kind, dur, channel, min, max, sort].forEach(el => el.addEventListener('input', () => { page = 1; render(); }));
  document.getElementById('pkgReset').addEventListener('click', () => {
    search.value = ''; kind.value = 'all'; dur.value = 'all'; channel.value = 'all';
    min.value = ''; max.value = ''; sort.value = 'default';
    page = 1; render();
  });
  render();
})();
