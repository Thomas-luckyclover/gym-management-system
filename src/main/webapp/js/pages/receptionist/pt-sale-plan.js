"use strict";

/*
 * BÁN GÓI PT — BƯỚC 3 "LỊCH TẬP" — pt-sale-plan.js
 *
 * Cách làm giống "sự kiện lặp lại" của Google Calendar và đặt lịch PT định kỳ của Mindbody / Glofox:
 *   1. Lịch mẫu hằng tuần   mỗi thứ T2–T6: chọn Nghỉ hoặc 1 giờ.
 *   2. Từng tuần            tab Tuần 1…N, mỗi tuần 5 ô ngày. Ô đỏ = ngày đó giờ mẫu không xếp được (+ lý do).
 *   3. Sửa riêng 1 buổi     bấm 1 ô ngày → chọn giờ khác hoặc Không tập, CHỈ cho ngày đó ("chỉ sự kiện này").
 *
 * DỮ LIỆU: server tính sẵn (PtSaleService.buildPlan → <script id="sale-plan-data">):
 *   days[]  mỗi ngày T2–T6 trong kỳ: {date, weekday 1–5, label, options[{time, end, ok, reason, warning, fresh}]}
 *           fresh = PT chưa mở khung giờ đó ngày đó → giữ lịch sẽ tự tạo khung → phải tích "Đã xác nhận với PT"
 *   times[] giờ của lịch mẫu · pattern[5] lịch mẫu ban đầu · requested[] buổi gửi lên lần trước (khi bị từ chối)
 *   unpublished[] Thứ PT chưa công bố khung nào (1 = Thứ 2 …) → nhãn "PT chưa công bố" trong Lịch mẫu
 * TRẠNG THÁI (chỉ 2 thứ):
 *   pattern[0..4]  giờ của Thứ 2..Thứ 6, "" = Nghỉ
 *   overrides      Map ngày → giờ sửa riêng ("" = bỏ buổi ngày đó)
 *   Giờ của 1 ngày = overrides (nếu có) → không thì pattern của thứ đó.
 * RA: form "Giữ lịch" (cột phải) nhận t1..t5 = pattern và sessions = "2026-10-27@09:30,…" (chỉ buổi xếp được).
 *     Server KIỂM TRA LẠI từng buổi lúc giữ (PtSaleService.checkRequested), JS chỉ để xem nhanh.
 */

const PLAN_DAY_NAMES = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6"];
const PLAN_DAY_SHORT = ["T2", "T3", "T4", "T5", "T6"];
// Lý do (server gửi) → chữ ngắn ghi dưới nút giờ. Lý do đầy đủ vẫn hiện khi rê chuột và ở phần Từng tuần.
const PLAN_SHORT_REASON = {
    "Khách có buổi khác trùng giờ": "Khách trùng giờ",
    "PT đã có khách giờ này": "PT có khách",
    "Đang được giữ cho đơn khác": "Đơn khác đang giữ",
    "PT đã gỡ khung giờ này": "PT đã gỡ khung",
    "PT có khung khác chồng giờ": "PT chồng giờ",
    "Khung cũ khác thời lượng": "Khung khác thời lượng",
    "Đã qua giờ": "Đã qua giờ"
};

document.addEventListener("DOMContentLoaded", function () {
    const dataNode = document.getElementById("sale-plan-data");
    const root = document.querySelector(".sale-plan");
    if (!dataNode || !root) {
        return;
    }
    initPlan(root, JSON.parse(dataNode.textContent));
});

function initPlan(root, data) {
    // ===== Dữ liệu tính 1 lần =====
    const days = data.days;
    const dayByDate = new Map();
    for (const day of days) {
        dayByDate.set(day.date, day);
    }
    const weeks = groupWeeks(days);

    // ===== Trạng thái =====
    const pattern = [];
    for (let i = 0; i < 5; i++) {
        pattern.push(data.pattern[i] || "");
    }
    const overrides = new Map();
    let weekIndex = 0;
    let editingDate = null;
    restoreRequested();

    // ===== Chỗ vẽ =====
    const patternBox = root.querySelector("[data-pattern]");
    const tabsBox = root.querySelector("[data-week-tabs]");
    const weekBox = root.querySelector("[data-week]");
    const editorBox = root.querySelector("[data-editor]");
    const weekLabel = root.querySelector("[data-week-label]");
    const totalBox = root.querySelector("[data-plan-total]");
    const form = document.querySelector("[data-plan-form]");
    const unpublished = data.unpublished || [];
    // Tích / bỏ tích "Đã xác nhận với PT" → tính lại nút Giữ lịch.
    if (form) {
        form.addEventListener("change", function (event) {
            if (event.target.name === "confirmPt") {
                renderSummary();
            }
        });
    }

    // ===== Sự kiện (gắn 1 lần, ủy quyền theo data-*) =====
    root.addEventListener("change", function (event) {
        const input = event.target;
        if (input.name && input.name.startsWith("plan-wd-")) {
            setPatternDay(Number(input.dataset.weekday), input.value);
        } else if (input.name === "plan-edit") {
            setOverride(editingDate, input.value);
        }
    });
    root.addEventListener("click", function (event) {
        const target = event.target.closest("button");
        if (!target || !root.contains(target)) {
            return;
        }
        if (target.dataset.weekStep) {
            showWeek(weekIndex + Number(target.dataset.weekStep));
        } else if (target.dataset.weekTab) {
            showWeek(Number(target.dataset.weekTab));
        } else if (target.dataset.day) {
            editingDate = editingDate === target.dataset.day ? null : target.dataset.day;
            render();
            focusKey(editingDate ? "edit-current" : "day-" + target.dataset.day);
        } else if (target.dataset.reset) {
            overrides.delete(editingDate);
            render();
        } else if (target.dataset.closeEditor !== undefined) {
            const date = editingDate;
            editingDate = null;
            render();
            focusKey("day-" + date);
        } else if (target.dataset.jumpIssue !== undefined) {
            jumpToFirstIssue();
        }
    });
    root.addEventListener("keydown", function (event) {
        // Tab tuần: ← → đổi tuần (mẫu tablist chuẩn), Esc đóng ô sửa riêng.
        const onTab = event.target.dataset && event.target.dataset.weekTab !== undefined;
        if (onTab && (event.key === "ArrowLeft" || event.key === "ArrowRight")) {
            event.preventDefault();
            showWeek(weekIndex + (event.key === "ArrowRight" ? 1 : -1));
            return;
        }
        if (event.key === "Escape" && editingDate) {
            const date = editingDate;
            editingDate = null;
            render();
            focusKey("day-" + date);
        }
    });

    render();


    /* ---------- Trạng thái → thay đổi ---------- */

    function setPatternDay(weekday, time) {
        pattern[weekday - 1] = time;
        // Ngày đã sửa riêng mà nay trùng lịch mẫu → không còn là "sửa riêng".
        for (const day of days) {
            if (day.weekday === weekday && overrides.get(day.date) === time) {
                overrides.delete(day.date);
            }
        }
        render();
    }

    function setOverride(date, time) {
        const day = dayByDate.get(date);
        if (time === pattern[day.weekday - 1]) {
            overrides.delete(date);
        } else {
            overrides.set(date, time);
        }
        render();
        focusKey("edit-current");
    }

    function showWeek(index) {
        weekIndex = Math.max(0, Math.min(weeks.length - 1, index));
        editingDate = null;
        render();
        focusKey("tab-" + weekIndex);
    }

    function jumpToFirstIssue() {
        for (let i = 0; i < weeks.length; i++) {
            const bad = weeks[i].days.find(function (day) { return stateOf(day).kind === "bad"; });
            if (bad) {
                weekIndex = i;
                editingDate = bad.date;
                render();
                focusKey("edit-current");
                return;
            }
        }
    }

    /** Giữ lịch bị server từ chối → trang vẽ lại: khôi phục các buổi đã gửi thành "sửa riêng" so với lịch mẫu. */
    function restoreRequested() {
        if (!data.requested || data.requested.length === 0) {
            return;
        }
        const requested = new Map();
        for (const item of data.requested) {
            const parts = item.split("@");
            requested.set(parts[0], parts[1]);
        }
        for (const day of days) {
            const time = requested.get(day.date) || "";
            if (time !== pattern[day.weekday - 1]) {
                overrides.set(day.date, time);
            }
        }
    }


    /* ---------- Tính (không đụng DOM) ---------- */

    /** Trạng thái 1 ngày: off (Nghỉ theo mẫu) · skipped (bỏ riêng) · ok · warn (có buổi, PT cần dời sau) · bad (không xếp được). */
    function stateOf(day) {
        const edited = overrides.has(day.date);
        const time = edited ? overrides.get(day.date) : pattern[day.weekday - 1];
        if (!time) {
            return { kind: edited ? "skipped" : "off", edited: edited };
        }
        const option = day.options.find(function (item) { return item.time === time; });
        if (!option) {
            return { kind: "bad", time: time, reason: "PT không có khung giờ này", edited: edited };
        }
        if (!option.ok) {
            return { kind: "bad", time: time, reason: option.reason, edited: edited };
        }
        return { kind: option.warning ? "warn" : "ok", time: time, end: option.end, warning: option.warning, edited: edited,
            fresh: option.fresh };
    }

    /**
     * Lịch mẫu "Thứ X lúc giờ Y": trong kỳ có bao nhiêu ngày Thứ X (total), bao nhiêu ngày không xếp được (blocked),
     * và từng ngày đó vì sao (misses). Ví dụ Thứ 3 20:00: total 9, blocked 5 → "Còn 4/9 buổi · Khách trùng giờ".
     */
    function blockedCount(weekday, time) {
        let blocked = 0;
        let total = 0;
        const misses = [];
        for (const day of days) {
            if (day.weekday !== weekday) {
                continue;
            }
            total++;
            const option = day.options.find(function (item) { return item.time === time; });
            if (!option || !option.ok) {
                blocked++;
                misses.push({ date: day.date, reason: option ? option.reason : "PT không có khung giờ này" });
            }
        }
        return { blocked: blocked, total: total, misses: misses };
    }

    /**
     * Chữ ghi dưới 1 nút giờ của lịch mẫu.
     *   meta    "Còn 4/9 buổi" (hết thì "Không còn buổi"); đủ mọi tuần → không ghi gì
     *   sub     lý do gặp nhiều nhất, viết ngắn ("Khách trùng giờ")
     *   detail  câu đầy đủ khi rê chuột: "Không xếp được 5 ngày: 03/11, 10/11 (Khách có buổi khác trùng giờ) …"
     */
    function patternNote(weekday, time) {
        const count = blockedCount(weekday, time);
        const left = count.total - count.blocked;
        if (count.blocked === 0) {
            return { meta: "", sub: "", detail: "Đủ " + count.total + "/" + count.total + " buổi trong kỳ", full: false };
        }
        const byReason = new Map();
        for (const miss of count.misses) {
            if (!byReason.has(miss.reason)) {
                byReason.set(miss.reason, []);
            }
            byReason.get(miss.reason).push(shortDate(miss.date));
        }
        let topReason = "";
        let topCount = 0;
        const parts = [];
        byReason.forEach(function (dates, reason) {
            parts.push(dates.join(", ") + " (" + reason + ")");
            if (dates.length > topCount) {
                topReason = reason;
                topCount = dates.length;
            }
        });
        const sub = (PLAN_SHORT_REASON[topReason] || topReason) + (byReason.size > 1 ? " …" : "");
        return {
            meta: left === 0 ? "Không còn buổi" : "Còn " + left + "/" + count.total + " buổi",
            sub: sub,
            detail: "Còn " + left + "/" + count.total + " buổi. Không xếp được " + count.blocked + " ngày: " + parts.join("; "),
            full: left === 0
        };
    }

    function summary() {
        let sessions = 0;
        let bad = 0;
        let edited = 0;
        let fresh = 0;      // buổi PT chưa mở khung (giữ lịch sẽ tự tạo)
        const picked = [];
        for (const day of days) {
            const state = stateOf(day);
            if (state.kind === "ok" || state.kind === "warn") {
                sessions++;
                picked.push(day.date + "@" + state.time);
                fresh += state.fresh ? 1 : 0;
            }
            if (state.kind === "bad") {
                bad++;
            }
            if (state.edited) {
                edited++;
            }
        }
        const perWeek = pattern.filter(function (time) { return time !== ""; }).length;
        return { sessions: sessions, bad: bad, edited: edited, fresh: fresh, perWeek: perWeek, picked: picked };
    }


    /* ---------- Vẽ ---------- */

    function render() {
        renderPattern();
        renderWeek();
        renderEditor();
        renderSummary();
    }

    function renderPattern() {
        patternBox.replaceChildren();
        for (let weekday = 1; weekday <= 5; weekday++) {
            const row = el("div", "plan-row");
            const head = el("div", "plan-row-day");
            head.append(el("strong", "", PLAN_DAY_NAMES[weekday - 1]));
            head.append(el("small", "", "Tối đa " + countWeekday(weekday) + " buổi"));
            if (unpublished.includes(weekday)) {
                head.append(el("small", "plan-row-flag", "PT chưa công bố"));
            }

            const chips = el("div", "plan-chips");
            chips.setAttribute("role", "radiogroup");
            chips.setAttribute("aria-label", "Giờ tập " + PLAN_DAY_NAMES[weekday - 1]);
            chips.append(chip("plan-wd-" + weekday, "", "Nghỉ", "", pattern[weekday - 1] === "", false,
                    "p-" + weekday + "-off", weekday, "is-rest"));
            for (const time of data.times) {
                const note = patternNote(weekday, time);
                // Giờ lễ tân tự nhập (không nằm trong published) → ghi thêm "PT chưa mở" để biết phải tick "Đã xác nhận với PT".
                const custom = !data.published.includes(time);
                const meta = custom ? (note.meta ? note.meta + " · PT chưa mở" : "PT chưa mở") : note.meta;
                const item = chip("plan-wd-" + weekday, time, time, meta, pattern[weekday - 1] === time,
                        note.full, "p-" + weekday + "-" + time, weekday, meta ? "has-meta" : "");
                if (note.sub) {
                    item.append(el("small", "plan-chip-sub", note.sub));
                }
                // Rê chuột: danh sách ngày; trình đọc màn hình đọc cùng câu đó.
                item.title = note.detail;
                item.append(el("span", "visually-hidden", note.detail));
                chips.append(item);
            }
            // Nút "+ Thêm giờ": lễ tân tự nhập giờ PT chưa công bố → tick "Đã xác nhận với PT" rồi Giữ lịch.
            chips.append(addTimeButton(weekday));
            row.append(head, chips);
            patternBox.append(row);
        }
    }

    /**
     * Nút "+ Thêm giờ" + ô nhập giờ tự do cho 1 thứ (T2..T6). Bấm → hiện ô nhập + nút Xác nhận/Hủy.
     * Xác nhận → addCustomTime (thêm giờ vào data.times + từng ngày) + setPatternDay → vẽ lại NGAY, KHÔNG tải lại trang.
     * Server vẫn kiểm tra lại lúc Giữ lịch (checkRequested) nên không sợ lệch.
     */
    function addTimeButton(weekday) {
        const wrap = el("span", "plan-add-time");

        const button = el("button", "plan-chip plan-chip-add", "+ Thêm giờ");
        button.type = "button";
        button.setAttribute("aria-label", "Thêm giờ tập " + PLAN_DAY_NAMES[weekday - 1]);
        button.setAttribute("aria-expanded", "false");

        const input = document.createElement("input");
        input.type = "time";
        input.className = "plan-time-input";
        input.hidden = true;
        if (data.open) {
            input.min = data.open;
        }
        if (data.close) {
            input.max = data.close;
        }
        input.step = 900;   // mốc 15 phút, cùng lưới khung PT công bố
        input.setAttribute("aria-label", "Giờ tập tự do " + PLAN_DAY_NAMES[weekday - 1]);

        const confirm = el("button", "plan-time-confirm", "Xác nhận");
        confirm.type = "button";
        confirm.hidden = true;
        const cancel = el("button", "plan-time-cancel", "Hủy");
        cancel.type = "button";
        cancel.hidden = true;

        function open() {
            input.hidden = false;
            confirm.hidden = false;
            cancel.hidden = false;
            button.hidden = true;
            button.setAttribute("aria-expanded", "true");
            input.value = pattern[weekday - 1] || "";
            input.focus();
        }
        function close() {
            input.hidden = true;
            confirm.hidden = true;
            cancel.hidden = true;
            button.hidden = false;
            button.setAttribute("aria-expanded", "false");
        }

        button.addEventListener("click", open);
        cancel.addEventListener("click", close);
        confirm.addEventListener("click", function () {
            // Dùng min/max/step của ô giờ để chặn giờ ngoài giờ mở cửa hoặc lệch mốc 15 phút.
            if (!input.reportValidity()) {
                return;
            }
            const time = input.value;
            close();
            if (!time) {
                return;
            }
            addCustomTime(time);            // thêm giờ vào data.times + từng ngày (client-side)
            setPatternDay(weekday, time);   // chọn giờ này cho thứ này rồi vẽ lại
        });

        wrap.append(button, input, confirm, cancel);
        return wrap;
    }

    /**
     * Thêm 1 giờ lễ tân tự nhập vào dữ liệu hiện tại (data.times + mỗi ngày 1 option "create" fresh)
     * để vẽ lại NGAY mà KHÔNG tải lại trang. Server vẫn kiểm tra lại lúc Giữ lịch (checkRequested).
     */
    function addCustomTime(time) {
        if (!data.times.includes(time)) {
            data.times.push(time);
            data.times.sort();
        }
        const end = endTime(time);
        for (const day of days) {
            if (!day.options.some(function (o) { return o.time === time; })) {
                day.options.push({ time: time, end: end, ok: true, reason: "", warning: "", fresh: true });
            }
        }
    }

    /** "17:00" + data.minutes phút → "18:30". */
    function endTime(time) {
        const minutes = data.minutes || 90;
        const parts = time.split(":").map(Number);
        const total = parts[0] * 60 + parts[1] + minutes;
        return String(Math.floor(total / 60)).padStart(2, "0") + ":" + String(total % 60).padStart(2, "0");
    }

    function renderWeek() {
        const week = weeks[weekIndex];
        weekLabel.textContent = "Tuần " + (weekIndex + 1) + "/" + weeks.length + " · " + shortDate(week.days[0].date)
                + " – " + shortDate(week.days[week.days.length - 1].date);
        root.querySelector("[data-week-step='-1']").disabled = weekIndex === 0;
        root.querySelector("[data-week-step='1']").disabled = weekIndex === weeks.length - 1;

        // Tab tuần: chấm đỏ = có ngày chưa xếp được, chấm xanh đĩa = có ngày sửa riêng.
        tabsBox.replaceChildren();
        weeks.forEach(function (item, index) {
            let bad = false;
            let edited = false;
            let count = 0;
            for (const day of item.days) {
                const state = stateOf(day);
                bad = bad || state.kind === "bad";
                edited = edited || state.edited;
                count += state.kind === "ok" || state.kind === "warn" ? 1 : 0;
            }
            const tab = el("button", "plan-tab" + (bad ? " has-bad" : "") + (edited ? " has-edit" : ""));
            tab.type = "button";
            tab.dataset.weekTab = String(index);
            tab.dataset.focusKey = "tab-" + index;
            tab.setAttribute("role", "tab");
            tab.setAttribute("aria-selected", String(index === weekIndex));
            tab.tabIndex = index === weekIndex ? 0 : -1;
            tab.setAttribute("aria-label", "Tuần " + (index + 1) + ", " + count + " buổi"
                    + (bad ? ", có ngày chưa xếp được" : ""));
            tab.append(el("b", "", String(index + 1)), el("small", "", shortDate(item.monday)));
            tabsBox.append(tab);
        });

        // 5 ô ngày T2–T6 của tuần đang xem; ngày ngoài kỳ (tuần đầu / cuối) để trống.
        weekBox.replaceChildren();
        for (let weekday = 1; weekday <= 5; weekday++) {
            const day = week.days.find(function (item) { return item.weekday === weekday; });
            weekBox.append(day ? dayCell(day) : outCell(week.monday, weekday));
        }
    }

    function dayCell(day) {
        const state = stateOf(day);
        const cell = el("button", "plan-day is-" + state.kind + (state.edited ? " is-edited" : ""));
        cell.type = "button";
        cell.dataset.day = day.date;
        cell.dataset.focusKey = "day-" + day.date;
        cell.setAttribute("aria-pressed", String(editingDate === day.date));

        const head = el("span", "plan-day-head");
        head.append(el("b", "", PLAN_DAY_SHORT[day.weekday - 1]), el("span", "", shortDate(day.date)));
        cell.append(head);

        let text = "";
        if (state.kind === "ok" || state.kind === "warn") {
            const time = el("span", "plan-day-time");
            time.append(el("strong", "", state.time), el("small", "", "– " + state.end));
            cell.append(time);
            text = state.time + " – " + state.end;
            if (state.warning) {
                cell.append(el("span", "plan-day-note", state.warning));
                text += ". " + state.warning;
            }
            if (state.fresh) {
                cell.append(el("span", "plan-day-fresh", "PT chưa mở"));
                text += ". PT chưa mở khung giờ này, cần hỏi PT";
            }
        } else if (state.kind === "bad") {
            cell.append(el("span", "plan-day-time", state.time));
            cell.append(el("span", "plan-day-note", state.reason));
            text = "chưa xếp được " + state.time + ": " + state.reason;
        } else {
            cell.append(el("span", "plan-day-rest", state.kind === "skipped" ? "Bỏ buổi này" : "Nghỉ"));
            text = state.kind === "skipped" ? "đã bỏ buổi" : "nghỉ theo lịch mẫu";
        }
        if (state.edited) {
            cell.append(el("span", "plan-day-flag", "Sửa riêng"));
            text += ", sửa riêng";
        }
        cell.setAttribute("aria-label", day.label + ": " + text + ". Bấm để sửa riêng ngày này.");
        return cell;
    }

    function outCell(monday, weekday) {
        const date = addDays(monday, weekday - 1);
        const cell = el("div", "plan-day is-out");
        cell.setAttribute("aria-hidden", "true");
        const head = el("span", "plan-day-head");
        head.append(el("b", "", PLAN_DAY_SHORT[weekday - 1]), el("span", "", shortDate(date)));
        cell.append(head, el("span", "plan-day-rest", "Ngoài kỳ"));
        return cell;
    }

    /** Ô sửa riêng 1 ngày (giống "Chỉ sự kiện này" của Google Calendar). */
    function renderEditor() {
        editorBox.replaceChildren();
        const day = editingDate ? dayByDate.get(editingDate) : null;
        editorBox.hidden = !day;
        if (!day) {
            return;
        }
        const state = stateOf(day);
        const current = state.edited ? overrides.get(day.date) : pattern[day.weekday - 1];

        const head = el("div", "plan-editor-head");
        const title = el("p", "plan-editor-title");
        title.append(el("strong", "", "Sửa riêng " + day.label));
        const close = el("button", "plan-nav");
        close.type = "button";
        close.dataset.closeEditor = "";
        close.setAttribute("aria-label", "Đóng");
        close.innerHTML = '<i class="fa-solid fa-xmark" aria-hidden="true"></i>';
        head.append(title, close);

        const chips = el("div", "plan-chips");
        chips.setAttribute("role", "radiogroup");
        chips.setAttribute("aria-label", "Giờ tập " + day.label);
        chips.append(chip("plan-edit", "", "Không tập", "", current === "", false,
                current === "" ? "edit-current" : "edit-off", 0, "is-rest"));
        for (const option of day.options) {
            const meta = option.ok ? (option.warning ? "PT cần dời" : (option.fresh ? "PT chưa mở" : "")) : option.reason;
            chips.append(chip("plan-edit", option.time, option.time + " – " + option.end, meta, current === option.time,
                    !option.ok, current === option.time ? "edit-current" : "edit-" + option.time, 0,
                    option.ok ? (meta ? "has-meta" : "") : "is-blocked"));
        }
        // Nút "+ Thêm giờ" cho RIÊNG ngày này: lễ tân tự nhập giờ PT chưa công bố → tick "Đã xác nhận với PT".
        chips.append(editorAddTimeButton(day));
        editorBox.append(head, chips);

        if (state.edited) {
            const base = pattern[day.weekday - 1];
            const reset = el("button", "plan-reset", "Về lịch mẫu (" + (base || "Nghỉ") + ")");
            reset.type = "button";
            reset.dataset.reset = "1";
            editorBox.append(reset);
        }
    }

    /**
     * Nút "+ Thêm giờ" trong ô sửa riêng 1 ngày: lễ tân tự nhập giờ PT chưa công bố cho RIÊNG ngày này.
     * Bấm → hiện ô nhập + Xác nhận/Hủy. Xác nhận → addCustomTime + setOverride → vẽ lại NGAY, KHÔNG tải lại trang.
     */
    function editorAddTimeButton(day) {
        const wrap = el("span", "plan-add-time");

        const button = el("button", "plan-chip plan-chip-add", "+ Thêm giờ");
        button.type = "button";
        button.setAttribute("aria-label", "Thêm giờ tập " + day.label);
        button.setAttribute("aria-expanded", "false");

        const input = document.createElement("input");
        input.type = "time";
        input.className = "plan-time-input";
        input.hidden = true;
        if (data.open) {
            input.min = data.open;
        }
        if (data.close) {
            input.max = data.close;
        }
        input.step = 900;   // mốc 15 phút, cùng lưới khung PT công bố
        input.setAttribute("aria-label", "Giờ tập tự do " + day.label);

        const confirm = el("button", "plan-time-confirm", "Xác nhận");
        confirm.type = "button";
        confirm.hidden = true;
        const cancel = el("button", "plan-time-cancel", "Hủy");
        cancel.type = "button";
        cancel.hidden = true;

        function open() {
            input.hidden = false;
            confirm.hidden = false;
            cancel.hidden = false;
            button.hidden = true;
            button.setAttribute("aria-expanded", "true");
            input.value = overrides.has(day.date) ? overrides.get(day.date) : "";
            input.focus();
        }
        function close() {
            input.hidden = true;
            confirm.hidden = true;
            cancel.hidden = true;
            button.hidden = false;
            button.setAttribute("aria-expanded", "false");
        }

        button.addEventListener("click", open);
        cancel.addEventListener("click", close);
        confirm.addEventListener("click", function () {
            const time = input.value;
            close();
            if (!time) {
                return;
            }
            addCustomTime(time);            // thêm giờ vào từng ngày (client-side)
            setOverride(day.date, time);    // sửa riêng ngày này rồi vẽ lại
        });

        wrap.append(button, input, confirm, cancel);
        return wrap;
    }

    function renderSummary() {
        const result = summary();
        totalBox.replaceChildren();
        if (result.sessions > 0) {
            totalBox.append(el("strong", "", result.sessions + " buổi"));
            totalBox.append(document.createTextNode(" · " + result.perWeek + " buổi/tuần"
                    + (result.edited > 0 ? " · " + result.edited + " ngày sửa riêng" : "")));
        } else {
            totalBox.append(document.createTextNode("Chưa có buổi nào"));
        }
        if (result.bad > 0) {
            const jump = el("button", "plan-issue", result.bad + " ngày chưa xếp được — xem");
            jump.type = "button";
            jump.dataset.jumpIssue = "";
            totalBox.append(jump);
        }

        // Cột phải "Đơn bán": số buổi, giá 1 buổi, nút Giữ lịch.
        const count = document.querySelector("[data-plan-count]");
        if (count) {
            count.textContent = result.sessions > 0
                    ? result.sessions + " buổi · " + formatMoney(Math.round(data.price / result.sessions)) + "/buổi"
                    : "—";
        }
        if (!form) {
            return;
        }
        for (let weekday = 1; weekday <= 5; weekday++) {
            form.querySelector("[data-plan-t='" + weekday + "']").value = pattern[weekday - 1];
        }
        form.querySelector("[data-plan-sessions]").value = result.picked.join(",");
        // Có buổi PT chưa mở khung → hiện ô "Đã xác nhận với PT" (server cũng đếm lại lúc giữ).
        const unconfirmed = result.fresh;
        const confirmBox = form.querySelector("[data-plan-confirm]");
        const confirmInput = confirmBox.querySelector("input");
        confirmBox.hidden = unconfirmed === 0;
        confirmInput.required = unconfirmed > 0;
        if (unconfirmed === 0) {
            confirmInput.checked = false;
        }
        form.querySelector("[data-plan-confirm-text]").textContent =
                unconfirmed + " buổi PT chưa mở khung — đã hỏi PT và PT đồng ý dạy.";
        const needConfirm = unconfirmed > 0 && !confirmInput.checked;
        // Giữ lịch cần đủ: ít nhất 1 buổi + khách (server ghi data-customer-ready) + đã xác nhận với PT (nếu cần).
        const customerReady = form.dataset.customerReady === "true";
        form.querySelector("[data-plan-submit]").disabled = result.sessions === 0 || !customerReady || needConfirm;
        const stepPlan = document.querySelector("[data-step-plan]");
        if (stepPlan) {
            stepPlan.classList.toggle("is-done", result.sessions > 0);
            stepPlan.classList.toggle("is-current", result.sessions === 0);
        }
        const note = document.querySelector("[data-plan-note]");
        if (note) {
            let text;
            if (result.sessions === 0) {
                text = "Chưa có buổi nào. Bấm vào lịch bên trái để chọn giờ tập.";
            } else if (!customerReady) {
                text = "Lịch xong rồi. Chọn khách ở cột Đơn bán là giữ được.";
            } else if (needConfirm) {
                text = "Có " + unconfirmed + " buổi PT chưa mở khung giờ này. Hỏi PT rồi tick \"Đã xác nhận với PT\".";
            } else {
                text = result.sessions + " buổi sẽ được giữ 15 phút để thu tiền, không ai giành được."
                        + (result.bad > 0 ? " Còn " + result.bad + " ngày chưa xếp được — bấm ô đỏ ở lịch để đổi giờ." : "");
            }
            note.textContent = text;
        }
        syncUrl();
    }

    /** Lưu lịch mẫu lên URL (t1..t5) để F5 không mất; các buổi sửa riêng không lưu. */
    function syncUrl() {
        const url = new URL(window.location.href);
        for (let weekday = 1; weekday <= 5; weekday++) {
            url.searchParams.set("t" + weekday, pattern[weekday - 1]);
        }
        window.history.replaceState(null, "", url);
    }

    function countWeekday(weekday) {
        return days.filter(function (day) { return day.weekday === weekday; }).length;
    }

    /** Sau khi vẽ lại, đưa con trỏ bàn phím về đúng phần tử (theo data-focus-key). */
    function focusKey(key) {
        const target = key ? root.querySelector("[data-focus-key='" + key + "']") : null;
        if (target) {
            target.focus();
        }
    }
}


/* ---------- Hàm phụ dùng chung (không cần trạng thái) ---------- */

/** 1 nút chọn dạng chip = <label> bọc radio thật (Tab / mũi tên / Space dùng được như radio thường). */
function chip(name, value, text, meta, checked, disabled, focusKey, weekday, extraClass) {
    const label = el("label", "plan-chip" + (extraClass ? " " + extraClass : ""));
    const input = document.createElement("input");
    input.type = "radio";
    input.name = name;
    input.value = value;
    input.checked = checked;
    input.disabled = disabled;
    input.dataset.focusKey = focusKey;
    if (weekday) {
        input.dataset.weekday = String(weekday);
    }
    label.append(input, el("span", "plan-chip-text", text));
    if (meta) {
        label.append(el("small", "plan-chip-meta", meta));
    }
    if (disabled) {
        label.classList.add("is-disabled");
    }
    return label;
}

/** Gom các ngày T2–T6 theo tuần (thứ 2 đầu tuần). */
function groupWeeks(days) {
    const weeks = [];
    for (const day of days) {
        const monday = addDays(day.date, 1 - day.weekday);
        let week = weeks[weeks.length - 1];
        if (!week || week.monday !== monday) {
            week = { monday: monday, days: [] };
            weeks.push(week);
        }
        week.days.push(day);
    }
    return weeks;
}

/** "2026-10-27" + n ngày → "2026-10-27" (tính theo UTC để không lệch múi giờ). */
function addDays(isoDate, count) {
    const date = new Date(isoDate + "T00:00:00Z");
    date.setUTCDate(date.getUTCDate() + count);
    return date.toISOString().slice(0, 10);
}

/** "2026-10-27" → "27/10". */
function shortDate(isoDate) {
    return isoDate.slice(8, 10) + "/" + isoDate.slice(5, 7);
}

function el(tag, className, text) {
    const element = document.createElement(tag);
    if (className) {
        element.className = className;
    }
    if (text !== undefined) {
        element.textContent = text;
    }
    return element;
}
