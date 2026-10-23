const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const source = fs.readFileSync(path.resolve(__dirname, "../../main/webapp/js/pages/pt/schedule.js"), "utf8");

// Chỉ kiểm tra tính giờ/lưu nháp; thao tác DOM và gửi request được kiểm tra trên trình duyệt.
function loadPreview(duration = "90", draftKey = "15:2026-10-19") {
    const board = {
        dataset: { duration, draftKey, axisStart: "300", axisEnd: "1320", today: "2026-10-23" },
        classList: { contains: () => false }
    };
    const status = { addEventListener() {} };
    const savedDrafts = new Map();
    const links = [{ href: "http://localhost/pt/schedule?date=2026-10-23&view=list" }];
    const context = vm.createContext({
        document: {
            getElementById: id => id === "pt-schedule" ? board : status,
            querySelectorAll: () => links,
            addEventListener() {}
        },
        URL, URLSearchParams,
        location: { href: "http://localhost/pt/schedule?date=2026-10-23&view=week" },
        history: { replaceState: (state, title, url) => { context.savedUrl = String(url); } },
        sessionStorage: { setItem: (key, value) => savedDrafts.set(key, value), getItem: key => savedDrafts.get(key) }
    });
    vm.runInContext(source, context);
    return { context, links, status, savedDrafts };
}

function dayTrack(open = "330", close = "1320", top = 100, height = 1020) {
    return {
        dataset: { open, close, date: "2026-10-23", closed: "false" },
        getBoundingClientRect: () => ({ top, height })
    };
}

test("kéo ngoài hai mép vẫn đủ thời lượng trong giờ mặc định", () => {
    const { context } = loadPreview();
    const track = dayTrack();
    assert.equal(context.ptMinuteAtPointer(track, -20), 330);
    assert.equal(context.ptMinuteAtPointer(track, 2000), 1230);
});

test("đổi chiều cao lưới vẫn trỏ cùng giờ và bám mốc 15 phút", () => {
    const { context } = loadPreview();
    const full = dayTrack();
    const half = dayTrack("330", "1320", 100, 510);
    assert.equal(context.ptMinuteAtPointer(full, 250), 450);
    assert.equal(context.ptMinuteAtPointer(half, 175), 450);
});

test("giờ mở lệch mốc và P-06 khác 90 phút không dùng số cố định", () => {
    const { context } = loadPreview("75");
    const track = dayTrack("340", "1320", 0);
    assert.equal(context.ptMinuteAtPointer(track, 0), 345);
    assert.equal(context.ptMinuteAtPointer(track, 1020), 1245);
});

test("mỗi ngày dùng đúng giờ ngoại lệ thay vì giờ chung của tuần", () => {
    const { context } = loadPreview();
    const shortDay = dayTrack("480", "720"); // Mở 08:00–12:00, khung cuối bắt đầu 10:30.
    assert.equal(context.ptMinuteAtPointer(shortDay, -20), 480);
    assert.equal(context.ptMinuteAtPointer(shortDay, 2000), 630);
    assert.equal(context.ptMinuteAtPointer(dayTrack(), 2000), 1230);
});

test("ngày nghỉ cả ngày không bắt đầu kéo và không tạo ô xem trước", () => {
    const { context, status } = loadPreview();
    const track = dayTrack("", "");
    track.dataset.closed = "true";
    context.startPtSlotDrag({
        button: 0, isPrimary: true,
        target: { closest: selector => selector === ".pt-day-track" ? track : null }
    });
    assert.match(status.textContent, /phòng nghỉ/);
});

test("bấm thẻ buổi hoặc khung thật không bắt đầu kéo vùng trống", () => {
    const { context, status } = loadPreview();
    context.newPtSlotPreview = () => { throw new Error("Không được tạo preview trên thẻ thật"); };
    context.startPtSlotDrag({
        button: 0, isPrimary: true,
        target: { closest: selector => selector === ".pt-day-track" ? dayTrack() : {} }
    });
    assert.equal(status.textContent, undefined);
});

test("ô giờ trống không trở thành 00:00; giờ có số 0 đầu giữ đúng định dạng", () => {
    const { context } = loadPreview();
    assert.ok(Number.isNaN(context.ptMinutes("")));
    assert.equal(context.ptClock(330), "05:30");
    assert.equal(context.ptMinutes("19:45"), 1185);
});

test("cấu hình thời lượng không dương phải dừng trước khi bật tương tác", () => {
    const { context, status } = loadPreview("0");
    context.initPtSchedulePreview();
    assert.match(status.textContent, /cấu hình giờ tập hợp lệ/);
});

test("nháp lưu theo đúng tài khoản và tuần, không ghi EndTime hoặc mã TimeSlot", () => {
    const { context, savedDrafts } = loadPreview();
    vm.runInContext('ptDrafts = [{date:"2026-10-23",start:"15:00"}]', context);
    context.savePtDrafts();
    assert.deepEqual(JSON.parse(savedDrafts.get("gms.pt-slots.15:2026-10-19")), [{date:"2026-10-23",start:"15:00"}]);
    const other = loadPreview("90", "16:2026-10-19");
    other.context.savePtDrafts();
    assert.ok(other.savedDrafts.has("gms.pt-slots.16:2026-10-19"));
    assert.equal(other.savedDrafts.has("gms.pt-slots.15:2026-10-19"), false);
});
