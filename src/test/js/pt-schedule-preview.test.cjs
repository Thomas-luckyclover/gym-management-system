const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const source = fs.readFileSync(path.resolve(__dirname, "../../main/webapp/js/pages/pt/schedule.js"), "utf8");

// Chỉ kiểm tra phần tính giờ / kiểm tra chỗ đặt / lưu tạm; giao diện thật được kiểm tra trên trình duyệt.
function loadSchedule() {
    const saved = new Map();
    const plan = {
        dataset: { duration: "90", axisStart: "300", axisEnd: "1320", draftKey: "15:2026-10-26" },
        querySelectorAll: () => [],
        classList: { contains: () => false }
    };
    const context = vm.createContext({
        document: {
            getElementById: id => (id === "main-content" ? plan : { dataset: {} }),
            addEventListener() {}
        },
        window: { matchMedia: () => ({ matches: true }) },
        sessionStorage: { setItem: (key, value) => saved.set(key, value), getItem: key => saved.get(key) },
        CSS: { escape: value => value }
    });
    vm.runInContext(source, context);
    return { context, plan, saved };
}

/** Cột ngày giả: khoảng được thêm 05:30–20:30, có sẵn các thẻ chiếm giờ truyền vào. */
function track(cards = [], first = "330", last = "1230") {
    return {
        dataset: { date: "2026-10-27", first, last },
        querySelectorAll: () => cards,
        getBoundingClientRect: () => ({ top: 100, height: 816 })   // 1020 phút × 0.8px
    };
}

/** Thẻ khung chiếm giờ [start, end); removing = đang được tích "Chờ gỡ". */
function card(start, end, removing = false) {
    return {
        dataset: { start: String(start), end: String(end) },
        querySelector: () => (removing ? {} : null)
    };
}

test("vị trí chuột đổi thành giờ bắt đầu, làm tròn mốc 15 phút", () => {
    const { context } = loadSchedule();
    assert.equal(context.ptMinuteAtPointer(track(), 100), 300);           // đỉnh cột = 05:00
    assert.equal(context.ptMinuteAtPointer(track(), 100 + 360), 750);     // 450 phút sau 05:00 = 12:30
    assert.equal(context.ptMinuteAtPointer(track(), 100 + 365), 750);     // lệch vài px vẫn bám 12:30
});

test("ngày không có data-first (đã qua / phòng nghỉ) không cho thêm", () => {
    const { context } = loadSchedule();
    assert.match(context.ptWhyCannotPlace(track([], ""), 600), /không thêm được/);
    assert.match(context.ptWhyCannotPlace(null, 600), /không thêm được/);
});

test("ngoài khoảng giờ được thêm hoặc lệch mốc 15 phút thì báo lý do", () => {
    const { context } = loadSchedule();
    assert.match(context.ptWhyCannotPlace(track(), 315), /từ 05:30 đến 20:30/);
    assert.match(context.ptWhyCannotPlace(track(), 1245), /từ 05:30 đến 20:30/);
    assert.match(context.ptWhyCannotPlace(track(), 610), /mốc 15 phút/);
    assert.equal(context.ptWhyCannotPlace(track(), 330), "");
    assert.equal(context.ptWhyCannotPlace(track(), 1230), "");
});

test("trùng thẻ đang chiếm giờ thì chặn; chạm mép hoặc khung đang chờ gỡ thì cho", () => {
    const { context } = loadSchedule();
    const busy = [card(660, 750)];                                         // 11:00–12:30
    assert.match(context.ptWhyCannotPlace(track(busy), 600), /Trùng khung 11:00 – 12:30/);
    assert.equal(context.ptWhyCannotPlace(track(busy), 750), "");          // 12:30 chạm mép
    assert.equal(context.ptWhyCannotPlace(track(busy), 570), "");          // 09:30–11:00 chạm mép
    assert.equal(context.ptWhyCannotPlace(track([card(660, 750, true)]), 600), "");
});

test("đổi giờ ↔ phút giữ số 0 đầu; ô trống không thành 00:00", () => {
    const { context } = loadSchedule();
    assert.equal(context.ptClock(330), "05:30");
    assert.equal(context.ptMinutes("19:45"), 1185);
    assert.ok(Number.isNaN(context.ptMinutes("")));
});

test("lưu tạm theo đúng tài khoản + tuần, chỉ ngày/giờ bắt đầu và mã khung gỡ", () => {
    const { context, plan, saved } = loadSchedule();
    const draft = { querySelector: name => ({ value: name.includes("slotDate") ? "2026-10-27" : "12:30" }) };
    const box = { value: "773" };
    plan.querySelectorAll = selector => (selector === ".pt-draft" ? [draft] : [box]);
    context.savePtChanges();
    assert.deepEqual(JSON.parse(saved.get("gms.pt-plan.15:2026-10-26")), {
        drafts: [{ date: "2026-10-27", start: "12:30" }],
        removals: ["773"]
    });
});
