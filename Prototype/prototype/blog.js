/* =====================================================================
   2.2.1 — Blog List: lọc, tìm kiếm, phân trang
   Mọi thẻ bài viết đã nằm sẵn trong blog.html; file này chỉ ẩn/hiện.
   ===================================================================== */
(function () {
  const PER_PAGE = 6;

  const cards = [...document.querySelectorAll('#blogGrid .post-card')];
  const search = document.getElementById('blogSearch');
  const chips = [...document.querySelectorAll('#chipFilter button')];
  const count = document.getElementById('resultCount');
  const empty = document.getElementById('blogEmpty');
  const pager = document.getElementById('blogPagination');
  const pageHead = document.querySelector('.page-head');

  let category = 'Tất cả';
  let page = 1;

  function matches(card) {
    const byCat = category === 'Tất cả' || card.dataset.category === category;
    const q = search.value.trim().toLowerCase();
    return byCat && viKey(card.dataset.title).includes(viKey(q));
  }

  function render() {
    const found = cards.filter(matches);
    const totalPages = Math.max(1, Math.ceil(found.length / PER_PAGE));
    if (page > totalPages) page = totalPages;

    const from = (page - 1) * PER_PAGE;
    const shown = found.slice(from, from + PER_PAGE);
    cards.forEach(c => { c.hidden = !shown.includes(c); });

    count.textContent = found.length ? found.length + ' bài viết' : '';
    empty.hidden = found.length > 0;

    pager.innerHTML = '';
    if (!found.length) return;

    pager.append(pageButton('<i class="fa-solid fa-chevron-left"></i>', page - 1, { disabled: page <= 1 }));
    for (let i = 1; i <= totalPages; i++) {
      pager.append(pageButton(String(i), i, { active: i === page }));
    }
    pager.append(pageButton('<i class="fa-solid fa-chevron-right"></i>', page + 1, { disabled: page >= totalPages }));
  }

  function pageButton(label, target, { active = false, disabled = false } = {}) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'page-btn' + (active ? ' active' : '');
    b.innerHTML = label;
    b.disabled = disabled;
    if (active) b.setAttribute('aria-current', 'page');
    b.addEventListener('click', () => {
      page = target;
      render();
      pageHead.scrollIntoView({ behavior: 'smooth' });
    });
    return b;
  }

  search.addEventListener('input', () => { page = 1; render(); });
  chips.forEach(chip => chip.addEventListener('click', () => {
    category = chip.dataset.category;
    page = 1;
    chips.forEach(c => c.classList.toggle('active', c === chip));
    render();
  }));

  render();
})();
