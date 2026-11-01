const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");

const source = fs.readFileSync(
    path.resolve(__dirname, "../../main/webapp/js/pages/receptionist/freeze-membership.js"),
    "utf8"
);

function createMockElement(id, initialProps = {}) {
    const listeners = {};
    const elem = {
        id,
        value: initialProps.value || '',
        min: initialProps.min || '',
        max: initialProps.max || '',
        textContent: initialProps.textContent || '',
        dataset: Object.assign({}, initialProps.dataset || {}),
        style: {},
        classList: {
            add() {},
            remove() {},
            toggle() { return false; },
            contains() { return false; }
        },
        addEventListener(event, fn) {
            if (!listeners[event]) listeners[event] = [];
            listeners[event].push(fn);
        },
        dispatchEvent(event) {
            const ev = typeof event === 'string' ? { type: event, defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } } : event;
            const handlers = listeners[ev.type] || [];
            handlers.forEach(h => h.call(elem, ev));
            return !ev.defaultPrevented;
        },
        getAttribute(name) {
            return this[name] || null;
        },
        setAttribute(name, val) {
            this[name] = val;
        },
        querySelectorAll() { return []; },
        querySelector() { return null; }
    };
    return elem;
}

function loadFreezeDOM(options = {}) {
    const elements = new Map();

    const startInput = createMockElement('startDate', {
        min: options.minStartDate || '2026-11-01',
        max: options.packageEndDate || '2027-06-19',
        value: options.startDate || '2026-11-01'
    });
    const endInput = createMockElement('plannedEndDate', {
        min: options.minStartDate || '2026-11-01',
        max: options.maxEndDate || '2026-11-30',
        value: options.plannedEndDate || '2026-11-30'
    });
    const startDisplay = createMockElement('startDateDisplay', {
        value: options.startDateDisplay || ''
    });
    const endDisplay = createMockElement('plannedEndDateDisplay', {
        value: options.plannedEndDateDisplay || ''
    });
    const customerIdHidden = createMockElement('customerIdHidden', { value: options.customerId || '101' });
    const pkgEndHidden = createMockElement('packageEndDateHidden', { value: options.packageEndDate || '2027-06-19' });
    const hint = createMockElement('plannedEndDateHint', { textContent: '' });
    const displayDaysCount = createMockElement('displayDaysCount', { textContent: '' });
    const displayNewExpiryDate = createMockElement('displayNewExpiryDate', { textContent: '' });
    const formApplyFreeze = createMockElement('formApplyFreeze');

    elements.set('startDate', startInput);
    elements.set('plannedEndDate', endInput);
    elements.set('startDateDisplay', startDisplay);
    elements.set('plannedEndDateDisplay', endDisplay);
    elements.set('customerIdHidden', customerIdHidden);
    elements.set('packageEndDateHidden', pkgEndHidden);
    elements.set('plannedEndDateHint', hint);
    elements.set('displayDaysCount', displayDaysCount);
    elements.set('displayNewExpiryDate', displayNewExpiryDate);
    elements.set('formApplyFreeze', formApplyFreeze);

    let domContentLoadedCallback = null;

    const documentMock = {
        addEventListener(event, fn) {
            if (event === 'DOMContentLoaded') {
                domContentLoadedCallback = fn;
            }
        },
        getElementById(id) {
            return elements.get(id) || null;
        },
        querySelectorAll() {
            return [];
        },
        createElement(tagName) {
            return createMockElement(tagName);
        }
    };

    const windowMock = {
        location: { pathname: '/gms/receptionist/freeze', search: '' }
    };

    const context = vm.createContext({
        document: documentMock,
        window: windowMock,
        console: { log() {}, warn() {}, error() {} },
        fetch: () => Promise.resolve({ json: () => Promise.resolve([]) }),
        encodeURIComponent: encodeURIComponent,
        URLSearchParams: URLSearchParams
    });

    vm.runInContext(source, context);

    if (domContentLoadedCallback) {
        domContentLoadedCallback();
    }

    return {
        context,
        startInput,
        endInput,
        startDisplay,
        endDisplay,
        pkgEndHidden,
        hint,
        displayDaysCount,
        displayNewExpiryDate,
        formApplyFreeze
    };
}

test("TC1 - Tải trang ban đầu: Ngày kết thúc mặc định 1 tháng, số ngày bảo lưu được tính đúng, hiển thị chuẩn dd/mm/yyyy", () => {
    const dom = loadFreezeDOM({
        minStartDate: '2026-11-01',
        packageEndDate: '2027-06-19',
        startDate: '2026-11-01',
        plannedEndDate: '2026-11-30'
    });

    assert.equal(dom.endInput.min, '2026-11-01');
    assert.equal(dom.endInput.max, '2026-11-30');
    assert.equal(dom.endInput.value, '2026-11-30');
    // Hiển thị chuẩn ngày trước tháng sau (dd/mm/yyyy): 01/11/2026 và 30/11/2026
    assert.equal(dom.startDisplay.value, '01/11/2026');
    assert.equal(dom.endDisplay.value, '30/11/2026');
    assert.match(dom.hint.textContent, /30\/11\/2026/);
    assert.equal(dom.displayDaysCount.textContent, '30 ngày');
    assert.equal(dom.displayNewExpiryDate.textContent, '19/07/2027');
});

test("TC2 - Sửa lỗi: Đổi ngày bắt đầu sang 19/12/2026 thì ngày kết thúc tự động nhảy đến 18/01/2027 (31 ngày), KHÔNG bị co về 1 ngày", () => {
    const dom = loadFreezeDOM({
        minStartDate: '2026-11-01',
        packageEndDate: '2027-05-21',
        startDate: '2026-11-01',
        plannedEndDate: '2026-11-30'
    });

    // Lễ tân chọn ngày bắt đầu mới: 19/12/2026
    dom.startInput.value = '2026-12-19';
    dom.startInput.dispatchEvent('change');

    // Kiểm tra giới hạn mới: tối đa 1 tháng kể từ 19/12/2026 là 18/01/2027
    assert.equal(dom.endInput.min, '2026-12-19');
    assert.equal(dom.endInput.max, '2027-01-18');
    assert.match(dom.hint.textContent, /18\/01\/2027/);

    // Điểm mấu chốt fix bug: ngày kết thúc tự động chuyển sang 18/01/2027, KHÔNG bị co về 12/19/2026 (1 ngày)
    assert.equal(dom.endInput.value, '2027-01-18');
    assert.equal(dom.startDisplay.value, '19/12/2026');
    assert.equal(dom.endDisplay.value, '18/01/2027');
    assert.equal(dom.displayDaysCount.textContent, '31 ngày');
    // Hạn gói tập 21/05/2027 + 31 ngày = 21/06/2027
    assert.equal(dom.displayNewExpiryDate.textContent, '21/06/2027');
});

test("TC3 - Lễ tân chủ động chọn ngày kết thúc ngắn hơn (ví dụ 18 ngày) thì hệ thống giữ nguyên ngày đó", () => {
    const dom = loadFreezeDOM({
        minStartDate: '2026-11-01',
        packageEndDate: '2027-06-19',
        startDate: '2026-12-19',
        plannedEndDate: '2027-01-18'
    });

    // Lễ tân chỉnh ngày kết thúc thành 05/01/2027
    dom.endInput.value = '2027-01-05';
    dom.endInput.dispatchEvent('change');

    assert.equal(dom.endInput.value, '2027-01-05');
    // Từ 19/12/2026 đến 05/01/2027: 13 ngày tháng 12 + 5 ngày tháng 1 = 18 ngày
    assert.equal(dom.displayDaysCount.textContent, '18 ngày');
});

test("TC4 - Gói tập sắp hết hạn (ví dụ KH-2037 chỉ còn 26 ngày): ngày kết thúc tối đa bị giới hạn bởi hạn gói", () => {
    const dom = loadFreezeDOM({
        minStartDate: '2026-11-01',
        packageEndDate: '2026-11-26', // Hạn gói chỉ còn 26 ngày
        startDate: '2026-11-01',
        plannedEndDate: '2026-11-26'
    });

    assert.equal(dom.endInput.max, '2026-11-26');
    assert.match(dom.hint.textContent, /26\/11\/2026/);
    assert.equal(dom.displayDaysCount.textContent, '26 ngày');

    // Nếu người dùng cố tình nhập quá hạn gói 30/11/2026, khi change/blur sẽ bị kéo về 26/11/2026
    dom.endInput.value = '2026-11-30';
    dom.endInput.dispatchEvent('change');
    assert.equal(dom.endInput.value, '2026-11-26');
    assert.equal(dom.displayDaysCount.textContent, '26 ngày');
});

test("TC5 - Gõ phím trên ô input (input event): không can thiệp clamp giữa chừng khi người dùng đang nhập dở", () => {
    const dom = loadFreezeDOM({
        minStartDate: '2026-11-01',
        packageEndDate: '2027-06-19',
        startDate: '2026-12-19',
        plannedEndDate: '2027-01-18'
    });

    // Người dùng gõ phím vào ô ngày kết thúc, giá trị tạm thời là '2027-01' (đang nhập dở)
    dom.endInput.value = '2027-01';
    dom.endInput.dispatchEvent('input');

    // Giá trị không bị ghi đè hay reset
    assert.equal(dom.endInput.value, '2027-01');

    // Khi người dùng hoàn thành nhập '2027-01-10' và blur khỏi ô
    dom.endInput.value = '2027-01-10';
    dom.endInput.dispatchEvent('blur');
    assert.equal(dom.endInput.value, '2027-01-10');
    assert.equal(dom.displayDaysCount.textContent, '23 ngày');
});

test("TC6 - Chặn nhập ngày ngoài biên: ngày bắt đầu quá hạn gói hoặc trước ngày mai thì bị clamp lại", () => {
    const dom = loadFreezeDOM({
        minStartDate: '2026-11-01',
        packageEndDate: '2027-06-19',
        startDate: '2026-11-01',
        plannedEndDate: '2026-11-30'
    });

    // Nhập ngày bắt đầu trong quá khứ (< min)
    dom.startInput.value = '2026-10-15';
    dom.startInput.dispatchEvent('change');
    assert.equal(dom.startInput.value, '2026-11-01');

    // Nhập ngày bắt đầu vượt quá hạn gói tập
    dom.startInput.value = '2029-06-20';
    dom.startInput.dispatchEvent('change');
    assert.equal(dom.startInput.value, '2027-06-19');
});

test("TC7 - Chặn submit form nếu ngày kết thúc vượt quá maxAllowedEndStr", () => {
    const dom = loadFreezeDOM({
        minStartDate: '2026-11-01',
        packageEndDate: '2027-06-19',
        startDate: '2026-11-01',
        plannedEndDate: '2026-11-30'
    });

    // Người dùng sửa HTML hoặc can thiệp value thành ngày vượt quá max
    dom.endInput.value = '2027-05-01';
    const submitEvent = { type: 'submit', defaultPrevented: false, preventDefault() { this.defaultPrevented = true; } };
    dom.formApplyFreeze.dispatchEvent(submitEvent);

    // Form submit phải bị chặn và giá trị endInput bị clamp về max hợp lệ
    assert.equal(submitEvent.defaultPrevented, true);
    assert.equal(dom.endInput.value, '2026-11-30');
});

test("TC8 - Đảo vị trí Ngày/Tháng: Hiển thị chuẩn dd/mm/yyyy (26/11/2026 thay vì 11/26/2026), gõ dd/mm/yyyy đồng bộ đúng", () => {
    const dom = loadFreezeDOM({
        minStartDate: '2026-11-01',
        packageEndDate: '2026-11-26',
        startDate: '2026-11-01',
        plannedEndDate: '2026-11-26'
    });

    // Ngày kết thúc hiển thị phải là 26/11/2026 (ngày đứng trước, tháng đứng sau), TUYỆT ĐỐI KHÔNG phải 11/26/2026
    assert.equal(dom.endDisplay.value, '26/11/2026');
    assert.notEqual(dom.endDisplay.value, '11/26/2026');
    assert.equal(dom.startDisplay.value, '01/11/2026');

    // Người dùng gõ '20/11/2026' vào ô hiển thị ngày kết thúc
    dom.endDisplay.value = '20/11/2026';
    dom.endDisplay.dispatchEvent('change');

    assert.equal(dom.endInput.value, '2026-11-20');
    assert.equal(dom.endDisplay.value, '20/11/2026');
    assert.equal(dom.displayDaysCount.textContent, '20 ngày');
});
