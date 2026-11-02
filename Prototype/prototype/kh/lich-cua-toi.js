/* #50 — Gói của tôi (chỉ xem): đổi nhóm gói Đang tập / Đã kết thúc / Buổi lẻ. */
(function () {
  const tabs = [...document.querySelectorAll('[role="tab"][data-tab]')];
  const side = [...document.querySelectorAll('[data-tab-panel]')];
  const main = [...document.querySelectorAll('[data-tab-main]')];
  function show(name) {
    tabs.forEach(t => t.setAttribute('aria-selected', String(t.dataset.tab === name)));
    side.forEach(p => { p.hidden = p.dataset.tabPanel !== name; });
    main.forEach(p => { p.hidden = p.dataset.tabMain !== name; });
  }
  tabs.forEach(t => t.addEventListener('click', () => show(t.dataset.tab)));
  show('active');
})();
