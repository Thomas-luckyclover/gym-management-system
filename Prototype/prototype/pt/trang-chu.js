/* #49 — PT Dashboard: lịch 7 ngày (tab). */
(function () {
  /* ---------- tab 7 ngày ---------- */
  const tabs = [...document.querySelectorAll('[data-day-tab]')];
  const panels = [...document.querySelectorAll('[data-day-panel]')];
  tabs.forEach(t => t.addEventListener('click', () => {
    tabs.forEach(x => x.setAttribute('aria-selected', String(x === t)));
    panels.forEach(p => { p.hidden = p.dataset.dayPanel !== t.dataset.dayTab; });
  }));

})();
