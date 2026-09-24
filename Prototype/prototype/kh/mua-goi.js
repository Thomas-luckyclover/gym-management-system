/* 7.1.1 — Mua gói: tìm, lọc theo loại, sắp xếp. */
(function () {
  const grid = document.getElementById('pkgGrid');
  const cards = [...grid.querySelectorAll('.price-card')];
  const empty = document.getElementById('pkgEmpty');
  const search = document.getElementById('pkgSearch');
  const kind = document.getElementById('pkgKind');
  const sort = document.getElementById('pkgSort');

  function render() {
    const q = search.value.trim().toLowerCase();
    let shown = 0;
    cards.forEach(c => {
      const ok = (!q || viKey(c.dataset.name).includes(viKey(q))) &&
                 (kind.value === 'all' || c.dataset.kind === kind.value);
      c.hidden = !ok;
      if (ok) shown++;
    });
    empty.hidden = shown > 0;

    const key = sort.value;
    [...cards]
      .sort((a, b) => key === 'price-asc' ? a.dataset.price - b.dataset.price
                    : key === 'price-desc' ? b.dataset.price - a.dataset.price
                    : a.dataset.months - b.dataset.months)
      .forEach(c => grid.insertBefore(c, empty));
  }

  [search, kind, sort].forEach(el => el.addEventListener('input', render));
  document.getElementById('pkgReset').addEventListener('click', () => {
    search.value = ''; kind.value = 'all'; sort.value = 'months';
    render();
  });
  render();
})();
