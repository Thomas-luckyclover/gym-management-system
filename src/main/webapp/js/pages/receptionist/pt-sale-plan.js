"use strict";

/*
 * BÁN GÓI PT — BƯỚC 3 "LỊCH TẬP" — pt-sale-plan.js
 *
 * Cách làm giống "sự kiện lặp lại" của Google Calendar và đặt lịch PT định kỳ của Mindbody / Glofox:
 *   1. Lịch mẫu hằng tuần   mỗi thứ T2–T6: Nghỉ hoặc 1 giờ. Mẫu nhanh: T2·T4·T6, T3·T5, T2→T6.
 *   2. Từng tuần            tab Tuần 1…N, mỗi tuần 5 ô ngày. Ô đỏ = ngày đó giờ mẫu không xếp được (+ lý do).
 *   3. Sửa riêng 1 buổi     bấm 1 ô ngày → chọn giờ khác hoặc Không tập, CHỈ cho ngày đó ("chỉ sự kiện này").
 *
 * DỮ LIỆU: server tính sẵn (PtSaleService.buildPlan → <script id="sale-plan-data">):
 *   days[]  mỗi ngày T2–T6 trong kỳ: {date, weekday 1–5, label, options[{time, end, ok, reason, warning}]}
 *   times[] giờ của lịch mẫu · pattern[5] lịch mẫu ban đầu · requested[] buổi gửi lên lần trước (khi bị từ chối)
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
        if (target.dataset.preset !== undefined) {
            applyPreset(target.dataset.preset);
        } else if (target.dataset.weekStep) {
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

    /** Mẫu nhanh: thứ trong mẫu giữ giờ đang chọn, chưa có thì lấy giờ xếp được nhiều buổi nhất; thứ khác → Nghỉ. */
    function applyPreset(text) {
        const chosen = text === "" ? [] : text.split(",").map(Number);
        for (let weekday = 1; weekday <= 5; weekday++) {
            if (!chosen.includes(weekday)) {
                pattern[weekday - 1] = "";
            } else if (!pattern[weekday - 1]) {
                pattern[weekday - 1] = bestTime(weekday);
            }
        }
        if (chosen.length === 0) {
            overrides.clear();
            editingDate = null;
        }
        render();
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
        return { kind: option.warning ? "warn" : "ok", time: time, end: option.end, warning: option.warning, edited: edited };
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

    function bestTime(weekday) {
        let best = "";
        let bestBlocked = Infinity;
        for (const time of data.times) {
            const count = blockedCount(weekday, time);
            if (count.blocked < bestBlocked && count.blocked < count.total) {
                best = time;
                bestBlocked = count.blocked;
            }
        }
        return best;
    }

    function summary() {
        let sessions = 0;
        let bad = 0;
        let edited = 0;
        const picked = [];
        for (const day of days) {
            const state = stateOf(day);
            if (state.kind === "ok" || state.kind === "warn") {
                sessions++;
                picked.push(day.date + "@" + state.time);
            }
            if (state.kind === "bad") {
                bad++;
            }
            if (state.edited) {
                edited++;
            }
        }
        const perWeek = pattern.filter(function (time) { return time !== ""; }).length;
        return { sessions: sessions, bad: bad, edited: edited, perWeek: perWeek, picked: picked };
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

            const chips = el("div", "plan-chips");
            chips.setAttribute("role", "radiogroup");
            chips.setAttribute("aria-label", "Giờ tập " + PLAN_DAY_NAMES[weekday - 1]);
            chips.append(chip("plan-wd-" + weekday, "", "Nghỉ", "", pattern[weekday - 1] === "", false,
                    "p-" + weekday + "-off", weekday, "is-rest"));
            for (const time of data.times) {
                const note = patternNote(weekday, time);
                const item = chip("plan-wd-" + weekday, time, time, note.meta, pattern[weekday - 1] === time,
                        note.full, "p-" + weekday + "-" + time, weekday, note.meta ? "has-meta" : "");
                if (note.sub) {
                    item.append(el("small", "plan-chip-sub", note.sub));
                }
                // Rê chuột: danh sách ngày; trình đọc màn hình đọc cùng câu đó.
                item.title = note.detail;
                item.append(el("span", "visually-hidden", note.detail));
                chips.append(item);
            }
            row.append(head, chips);
            patternBox.append(row);
        }
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
        title.append(el("strong", "", day.label), el("span", "", " · chỉ sửa ngày này, các tuần khác giữ lịch mẫu"));
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
            const meta = option.ok ? (option.warning ? "PT cần dời" : "") : option.reason;
            chips.append(chip("plan-edit", option.time, option.time + " – " + option.end, meta, current === option.time,
                    !option.ok, current === option.time ? "edit-current" : "edit-" + option.time, 0,
                    option.ok ? (option.warning ? "has-meta" : "") : "is-blocked"));
        }
        editorBox.append(head, chips);

        if (state.edited) {
            const base = pattern[day.weekday - 1];
            const reset = el("button", "plan-reset", "Về lịch mẫu (" + (base || "Nghỉ") + ")");
            reset.type = "button";
            reset.dataset.reset = "1";
            editorBox.append(reset);
        }
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
        form.querySelector("[data-plan-submit]").disabled = result.sessions === 0;
        const note = document.querySelector("[data-plan-note]");
        if (note) {
            note.textContent = result.sessions === 0
                    ? "Chọn ít nhất 1 buổi ở Lịch tập bên trái."
                    : "Giữ trước khi thu tiền: " + result.sessions + " khung chuyển \"Đang giữ\", lễ tân khác và PT không lấy được."
                        + (result.bad > 0 ? " " + result.bad + " ngày chưa xếp được sẽ không có buổi." : "");
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
