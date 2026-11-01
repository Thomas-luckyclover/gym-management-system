/* #71 — Thư viện bài tập: MỘT bộ dữ liệu và MỘT logic lọc/phân trang dùng chung cho
   kh/thu-vien-bai-tap.html (khách có gói mở khóa) và pt/thu-vien-bai-tap.html (PT, không khóa). */
const EXERCISES = [
  ['Barbell Squat', 'Chân', 'Tạ đòn'], ['Bench Press', 'Ngực', 'Tạ đòn'], ['Deadlift', 'Lưng', 'Tạ đòn'],
  ['Overhead Press', 'Vai', 'Tạ đòn'], ['Lat Pulldown', 'Lưng', 'Máy cáp'], ['Seated Cable Row', 'Lưng', 'Máy cáp'],
  ['Dumbbell Curl', 'Tay trước', 'Tạ đơn'], ['Triceps Pushdown', 'Tay sau', 'Máy cáp'], ['Leg Press', 'Chân', 'Máy tập'],
  ['Incline Dumbbell Press', 'Ngực', 'Tạ đơn'], ['Face Pull', 'Vai', 'Máy cáp'], ['Plank', 'Bụng', 'Không dụng cụ'],
  ['Romanian Deadlift', 'Chân', 'Tạ đòn'], ['Crunch', 'Bụng', 'Không dụng cụ'],
].map(([name, muscle, equip]) => ({ name, muscle, equip }));

function initExerciseLibrary() {
  const EX = EXERCISES;
  const list = document.getElementById('exList');
  const chips = document.getElementById('exChips');
  const equipSel = document.getElementById('exEquip');
  const query = document.getElementById('exQuery');
  const sort = document.getElementById('exSort');
  let muscle = '';

  [...new Set(EX.map(e => e.equip))].sort().forEach(v => equipSel.append(new Option(v, v)));
  document.getElementById('exSummary').textContent = `${EX.length} bài · cập nhật 20/09/2026 03:00`;

  list.innerHTML = [...EX].sort((a, b) => a.name.localeCompare(b.name)).map(e => `
    <div class="data-row" data-q="${e.name.toLowerCase()}" data-muscle="${e.muscle}" data-equip="${e.equip}">
      <div class="row-main">
        <div class="row-title">${e.name}</div>
        <div class="row-meta"><span><i class="fa-solid fa-bullseye"></i> ${e.muscle}</span><span><i class="fa-solid fa-dumbbell"></i> ${e.equip}</span></div>
      </div>
    </div>`).join('') + '<div data-empty hidden></div>';

  function drawChips() {
    const muscles = [...new Set(EX.map(e => e.muscle))];
    chips.innerHTML = [['', 'Tất cả', EX.length], ...muscles.map(m => [m, m, EX.filter(e => e.muscle === m).length])]
      .map(([v, l, n]) => `<button class="btn ${v === muscle ? 'btn-primary' : 'btn-outline'} btn-sm" type="button" data-muscle="${v}">${l} ${n}</button>`).join(' ');
  }
  chips.addEventListener('click', e => {
    const b = e.target.closest('[data-muscle]');
    if (!b) return;
    muscle = b.dataset.muscle; drawChips(); view.reset();
  });

  const view = makeList({
    listSel: '#exList',
    perPage: 8,
    unit: 'bài',
    matcher(row) {
      return (!query.value.trim() || viKey(row.dataset.q).includes(viKey(query.value.trim()))) &&
             (!muscle || row.dataset.muscle === muscle) &&
             (!equipSel.value || row.dataset.equip === equipSel.value);
    },
    onRender(found) {
      document.getElementById('exEmpty').hidden = found.length > 0;
      const dir = sort.value === '-name' ? -1 : 1;
      [...list.querySelectorAll('.data-row')]
        .sort((a, b) => dir * a.dataset.q.localeCompare(b.dataset.q))
        .forEach(r => list.insertBefore(r, list.querySelector('[data-empty]')));
    },
  });
  document.getElementById('exApply').addEventListener('click', view.reset);
  [equipSel, sort].forEach(el => el.addEventListener('input', view.reset));
  query.addEventListener('keydown', e => { if (e.key === 'Enter') view.reset(); });
  document.getElementById('exReset').addEventListener('click', () => {
    query.value = ''; equipSel.value = ''; sort.value = 'name'; muscle = ''; drawChips(); view.reset();
  });
  drawChips();
  view.render();
}
