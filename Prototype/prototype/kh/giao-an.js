/* 9.6.3 — Giáo án: chỉ đọc, hiển thị đúng nội dung PT soạn ở 9.6.2.
   Ô tìm kiếm lọc theo dòng trong tab đang mở. */
(function () {
  const search = document.getElementById('planSearch');
  const empty = document.getElementById('planEmpty');

  const activePanel = () =>
    [...document.querySelectorAll('[data-tab-panel]')].find(p => !p.hidden);

  function render() {
    const panel = activePanel();
    if (!panel) return;
    const q = search.value.trim().toLowerCase();
    let shown = 0;

    panel.querySelectorAll('.plan-line').forEach(line => {
      const text = line.dataset.raw ?? (line.dataset.raw = line.textContent);
      const ok = !q || viKey(text).includes(viKey(q));
      line.hidden = !ok;
      if (!ok) return;
      shown++;

      /* tô đậm đoạn khớp để mắt bắt ngay, không đổi nội dung gốc */
      if (!q) { line.textContent = text; return; }
      const i = viKey(text).indexOf(viKey(q));
      line.textContent = '';
      line.append(
        text.slice(0, i),
        Object.assign(document.createElement('mark'), { textContent: text.slice(i, i + q.length) }),
        text.slice(i + q.length),
      );
    });

    empty.hidden = shown > 0;
  }

  search.addEventListener('input', render);
  document.getElementById('planReset').addEventListener('click', () => { search.value = ''; render(); });
  document.querySelectorAll('[data-tab]').forEach(b =>
    b.addEventListener('click', () => setTimeout(render, 0)));
  render();
})();
