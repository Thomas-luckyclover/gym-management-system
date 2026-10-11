/**
 * Customer Dashboard: chuông thông báo và hiệu ứng đếm số chuỗi tập.
 * Không phụ thuộc thư viện; trang vẫn dùng được khi file này lỗi.
 */
(function () {
  'use strict';

  // ===== Chuông thông báo: bấm để mở/đóng, bấm ra ngoài hoặc nhấn Esc để đóng =====
  var bell = document.querySelector('[data-bell]');
  if (bell) {
    var btn = bell.querySelector('[data-bell-btn]');
    var panel = bell.querySelector('[data-bell-panel]');

    var setOpen = function (open) {
      panel.hidden = !open;
      btn.setAttribute('aria-expanded', open ? 'true' : 'false');
    };

    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      setOpen(panel.hidden);
    });
    document.addEventListener('click', function (e) {
      if (!bell.contains(e.target)) setOpen(false);
    });
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !panel.hidden) {
        setOpen(false);
        btn.focus();
      }
    });
  }

  var reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // ===== Đếm số từ 0 lên giá trị thật =====
  function countUp(el, target, duration) {
    if (reduce || !(target > 0)) return;
    var start = null;
    el.textContent = '0';
    var step = function (ts) {
      if (start === null) start = ts;
      var t = Math.min(1, (ts - start) / duration);
      el.textContent = String(Math.round(target * (1 - Math.pow(1 - t, 3))));
      if (t < 1) requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
  }

  function startPageCounter() {
    var counter = document.querySelector('[data-countup]');
    if (counter) countUp(counter, parseInt(counter.getAttribute('data-countup'), 10), 900);
  }

  // ===== Màn chào chuỗi (chỉ có khi Servlet bật cờ showStreakIntro) =====
  var intro = document.querySelector('[data-intro]');
  if (!intro) {
    startPageCounter();
    return;
  }

  // Hiệu ứng chạy xong ở khoảng 2,1 giây; giữ thêm ~1,7 giây cho khách đọc rồi tự vào trang.
  // Không có nút: màn này chỉ tự chạy. Click hoặc Esc vẫn bỏ qua được cho ai muốn bỏ qua sớm.
  var AUTO_CLOSE_MS = reduce ? 1800 : 3800;
  var closed = false;
  var timer = null;

  // Tia lửa bắn ngẫu nhiên quanh ngọn lửa
  var sparks = intro.querySelector('[data-sparks]');
  if (sparks && !reduce) {
    for (var i = 0; i < 18; i++) {
      var s = document.createElement('i');
      var angle = (Math.PI * 2 * i) / 18 + Math.random() * 0.5;
      var dist = 90 + Math.random() * 150;
      s.style.setProperty('--dx', Math.round(Math.cos(angle) * dist) + 'px');
      s.style.setProperty('--dy', Math.round(Math.sin(angle) * dist - 40) + 'px');
      s.style.setProperty('--d', (0.55 + Math.random() * 0.7).toFixed(2) + 's');
      s.style.setProperty('--s', (4 + Math.round(Math.random() * 6)) + 'px');
      sparks.appendChild(s);
    }
  }

  var numEl = intro.querySelector('[data-intro-count]');
  if (numEl) {
    setTimeout(function () {
      countUp(numEl, parseInt(numEl.getAttribute('data-intro-count'), 10), 900);
    }, 1000);
  }

  function closeIntro() {
    if (closed) return;
    closed = true;
    clearTimeout(timer);
    intro.classList.add('is-leaving');
    setTimeout(function () {
      if (intro.parentNode) intro.parentNode.removeChild(intro);
      document.body.classList.remove('cd-intro-active');
      startPageCounter();
    }, reduce ? 0 : 450);
  }

  intro.addEventListener('click', closeIntro);
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') closeIntro();
  });
  intro.focus();
  timer = setTimeout(closeIntro, AUTO_CLOSE_MS);
})();
