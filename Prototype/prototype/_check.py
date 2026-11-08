#!/usr/bin/env python3
"""Kiểm tra prototype theo _spec*.json. Chạy: python _check.py (từ thư mục prototype)."""
import glob, json, os, re, sys

sys.stdout.reconfigure(encoding="utf-8")
ROOT = os.path.dirname(os.path.abspath(__file__))
os.chdir(ROOT)

# ---- gộp spec ----
specs = []
for f in sorted(glob.glob("_spec_*.json")):
    specs += json.load(open(f, encoding="utf-8"))
if os.path.exists("_spec.json") and not specs:
    specs = json.load(open("_spec.json", encoding="utf-8"))
elif os.path.exists("_spec.json"):
    seen = {(s["no"], s["file"]) for s in specs}
    specs += [s for s in json.load(open("_spec.json", encoding="utf-8")) if (s["no"], s["file"]) not in seen]
specs.sort(key=lambda s: (s["no"], s["file"]))
json.dump(specs, open("_spec.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)

def text(path):
    if not os.path.exists(path):
        return None
    t = open(path, encoding="utf-8").read()
    return t, re.sub(r"<[^>]+>", "", t)

rows = []
# ---- 1. nội dung từng màn ----
for sp in specs:
    if sp.get("status") in ("withdrawn", "merged"):
        continue
    f = sp["file"]
    srcs = [text(f)]
    js = os.path.splitext(f)[0] + ".js"
    if os.path.exists(js):
        srcs.append(text(js))
    if srcs[0] is None:
        rows.append((sp["no"], f, "THIẾU FILE"))
        continue
    raw = "\n".join(s[0] for s in srcs if s)
    plain = "\n".join(s[1] for s in srcs if s)
    miss = []
    for k in ("blocks", "fields", "columns", "actions", "states"):
        for item in sp.get(k, []):
            if item not in raw and item not in plain:
                miss.append(f"{k}:{item}")
    if miss:
        rows.append((sp["no"], f, "; ".join(miss)))

# ---- 2. menu theo vai trò ----
KH = ["mua-goi", "lich-su-giao-dich", "lich-cua-toi", "buoi-pt", "dat-buoi-le", "giao-an", "gop-y",
      "thu-vien-bai-tap", "chi-so-co-the", "tai-khoan", "trang-ca-nhan"]
LT = ["check-in", "ban-hang", "ban-goi-pt", "ca-lam-viec", "hoi-vien", "bao-luu", "co-so-vat-chat", "bao-tri",
      "nghi-phep", "tai-khoan"]
PT = ["trang-chu", "lich-ranh", "khach-cua-toi", "thu-vien-bai-tap", "nghi-phep", "tai-khoan"]
BANNED = ["doi-lich.html", "diem-danh.html", "them-khung-gio.html"]
# ghi-chu / giao-an của PT mở từ Khách của tôi nên không bắt buộc có trong menu
ROLES = {"kh": KH, "lt": LT, "pt": PT}
WITHDRAWN = {"kh/doi-lich.html", "pt/diem-danh.html", "pt/them-khung-gio.html"}

pages = []
for role, targets in ROLES.items():
    for f in sorted(glob.glob(f"{role}/*.html")):
        if f.replace("\\", "/") in WITHDRAWN:
            continue
        pages.append((role, f.replace("\\", "/"), targets))

for role, f, targets in pages:
    raw = open(f, encoding="utf-8").read()
    nav = re.findall(r'<nav class="cust-nav">(.*?)</nav>', raw, re.S)
    navtxt = nav[0] if nav else ""
    miss = [f"menu:{t}.html" for t in targets if f"{t}.html" not in navtxt]
    if "chon-vai-tro.html" not in navtxt:
        miss.append("menu:chon-vai-tro.html")
    for b in BANNED:
        if b in raw:
            miss.append(f"link-cấm:{b}")
    if "app.js" not in raw:
        miss.append("bell:thiếu app.js")
    elif role == "kh" and "customer.js" not in raw and "mua-goi" in "":
        pass
    rows.append((0, f, "; ".join(miss))) if miss else None

# index/blog/guest: không được link tới trang đã gộp / rút
for f in glob.glob("*.html") + glob.glob("blog/*.html"):
    raw = open(f, encoding="utf-8").read()
    bad = [b for b in BANNED if b in raw]
    if bad:
        rows.append((0, f, "link-cấm:" + ",".join(bad)))

for f in ("Manager/gms_manager_mockup_v24.html", "Admin/gms_admin_mockup_v24.html"):
    raw = open(f, encoding="utf-8").read()
    miss = []
    if "bellHTML()" not in raw or "function bellHTML" not in raw:
        miss.append("bell")
    if "chon-vai-tro.html" not in raw:
        miss.append("menu:chon-vai-tro.html")
    for b in BANNED:
        if b in raw:
            miss.append(f"link-cấm:{b}")
    if miss:
        rows.append((0, f, "; ".join(miss)))

# app.js phải có chuông
if "BELL_DATA" not in open("app.js", encoding="utf-8").read():
    rows.append((73, "app.js", "chuông thông báo"))

# =====================================================================
# Các kiểm tra bổ sung (trình duyệt, link, buổi PT, bảng giá, đơn hàng)
# =====================================================================
from urllib.parse import unquote
from datetime import date, timedelta

EXTRA = {"Lỗi JS (console / pageerror)": [], "Link nội bộ hỏng": [], "Buổi PT lệch giữa màn khách và màn PT": [],
         "Tên / giá gói lệch": [], "Đơn hàng lệch": []}
SKIP_PAGES = {"kh/doi-lich.html", "pt/diem-danh.html", "pt/them-khung-gio.html"}


def rd(p):
    return open(p, encoding="utf-8").read() if os.path.exists(p) else ""


def strip(t):
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", t))


def all_pages():
    out = []
    for pat in ("*.html", "kh/*.html", "lt/*.html", "pt/*.html", "blog/*.html", "Manager/*.html", "Admin/*.html"):
        for f in sorted(glob.glob(pat)):
            f = f.replace("\\", "/")
            if f in SKIP_PAGES or f.startswith(".kilo"):
                continue
            out.append(f)
    return out


def route_table(f):
    """Route của mockup lấy từ startApp([...]): (tĩnh, động)."""
    raw = rd(f)
    i = raw.rfind("startApp([")
    if i < 0:
        return [], []
    static, dyn = [], []
    for p in re.findall(r"\[/\^(#\\/[^$]*?)\$/", raw[i:]):
        h = p.replace("\\/", "/")
        (dyn if "(" in h else static).append(h)
    return static, dyn


# ---- 1. lỗi JS bằng Playwright ----
def browser_check():
    key = "Lỗi JS (console / pageerror)"
    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        EXTRA[key].append(("-", "playwright", "chưa cài: pip install playwright && python -m playwright install chromium"))
        return
    CAND = ["1", "G-01", "G-02", "P-01", "P-07", "NP-31", "NP-34", "KH-1008", "KH-1001", "BT-0313", "BT-0312", "TB-0101",
            "NV-0118", "NV-0042", "NV-0001", "GYMVUI10", "NGAY15K", "DH-2026-1194", "DH-2026-1199"]
    with sync_playwright() as pw:
        br = pw.chromium.launch()
        ctx = br.new_context()
        ctx.route("**/*", lambda r: r.continue_() if r.request.url.startswith(("file:", "data:")) else r.abort())

        def visit(url):
            page = ctx.new_page()
            errs = []
            page.on("pageerror", lambda e: errs.append("pageerror: " + str(e)[:160]))
            page.on("console", lambda m: errs.append("console: " + m.text[:160])
                    if m.type == "error" and "net::" not in m.text and "Failed to load resource" not in m.text else None)
            try:
                page.goto(url, wait_until="load", timeout=15000)
                page.wait_for_timeout(250)
            except Exception as e:
                errs.append("goto: " + str(e)[:100])
            page.close()
            return sorted(set(errs))

        for f in all_pages():
            url = "file:///" + os.path.abspath(f).replace("\\", "/")
            for e in visit(url):
                EXTRA[key].append(("-", f, e))
            if f.startswith(("Manager/", "Admin/")):
                static, dyn = route_table(f)
                for h in static:
                    for e in visit(url + h):
                        EXTRA[key].append(("-", f + h, e))
                for h in dyn:
                    base = h[:h.index("(")]
                    res = [visit(url + base + c) for c in CAND]
                    if all(res):
                        EXTRA[key].append(("-", f + h, "mọi id mẫu đều lỗi: " + res[0][0]))
        br.close()


if "--no-browser" not in sys.argv:
    browser_check()

# ---- 2. link nội bộ hỏng ----
ATTR = re.compile(r'(?:href|src)\s*=\s*"([^"]*)"', re.I)
JSLINK = re.compile(r"""['"]([\w./-]+\.html)(?:[?#][^'"]*)?['"]""")


def link_ok(page, target, ids, routes):
    t = target.strip()
    if not t or "$" in t or "{" in t or re.match(r"^(https?:|mailto:|tel:|javascript:|data:|//)", t, re.I):
        return True
    if t.startswith("#/"):
        # Manager/Admin là ứng dụng hash-router; các route được render động
        # không tồn tại dưới dạng file nên không được kiểm tra như link tĩnh.
        if page.startswith(("Manager/", "Admin/")):
            return True
        if routes is None:
            return True
        r = t.split("?")[0]
        stat, dyn = routes
        return r in stat or any(r.startswith(d[:d.index("(")]) for d in dyn)
    if t.startswith("#"):
        return t[1:] == "" or t[1:] in ids
    path = unquote(t.split("#")[0].split("?")[0])
    return os.path.exists(os.path.normpath(os.path.join(os.path.dirname(page), path)))


for f in all_pages():
    raw = rd(f)
    ids = set(re.findall(r'\bid="([^"]+)"', raw)) | set(re.findall(r'\bname="([^"]+)"', raw))
    routes = route_table(f) if f.startswith(("Manager/", "Admin/")) else None
    bad = sorted({t for t in ATTR.findall(raw) if not link_ok(f, t, ids, routes)})
    js = os.path.splitext(f)[0] + ".js"
    if os.path.exists(js):
        bad += sorted({t for t in JSLINK.findall(rd(js)) if not link_ok(f, t, ids, routes)})
    for t in bad:
        EXTRA["Link nội bộ hỏng"].append(("-", f, t))

# ---- 3. buổi PT của KH-0241 ----
TODAY = date(2026, 9, 20)
ROWS3 = EXTRA["Buổi PT lệch giữa màn khách và màn PT"]


def dmy(s):
    d, m, y = (int(x) for x in s.split("/"))
    return date(y, m, d)


cust_a = {}  # kh/buoi-pt.js
for m in re.finditer(r"\{\s*d:\s*'(\d{4})-(\d{2})-(\d{2})',\s*t:\s*'(\d{2}:\d{2})',\s*pt:\s*'([^']+)',\s*kind:\s*'([^']+)',\s*st:\s*'(\w+)'",
                     rd("kh/buoi-pt.js")):
    y, mo, d, t, pt, kind, st = m.groups()
    cust_a[(date(int(y), int(mo), int(d)), t)] = (pt, kind, st)
if not cust_a:
    ROWS3.append(("-", "kh/buoi-pt.js", "không đọc được danh sách buổi"))

lc = rd("kh/lich-cua-toi.html") + rd("kh/lich-cua-toi.js")
DOW = ["T2", "T3", "T4", "T5", "T6", "T7", "CN"]
tpl = {}
mt = re.search(r"Mỗi tuần:[^<]*", lc)
if mt:
    for m in re.finditer(r"(T[2-7]|CN)\s+(\d{2}:\d{2})", mt.group(0)):
        tpl[m.group(1)] = m.group(2)
cust_b = {}
pk = re.search(r"Các buổi của gói.*?</p>", lc, re.S)
if pk:
    for dd, mm in re.findall(r">(\d{2})/(\d{2})<", pk.group(0)):
        d0 = date(2026, int(mm), int(dd))
        cust_b[(d0, tpl.get(DOW[d0.weekday()], "?"))] = ("Gói PT", "Hoàng Long")
for blk in re.split(r'(?=<div class="data-row")', lc):
    m = re.search(r"(\d{2}/\d{2}/\d{4}) · (\d{2}:\d{2})", blk)
    if m and "Buổi lẻ" in blk:
        who = re.search(r"fa-user-check\"></i>\s*([^<]+)<", blk)
        cust_b[(dmy(m.group(1)), m.group(2))] = ("Buổi lẻ", who.group(1).strip() if who else "?")

for k, (pt, kind, st) in sorted(cust_a.items()):
    if pt != "Hoàng Long":
        ROWS3.append(("-", "kh/buoi-pt.js", f"{k[0]:%d/%m/%Y} {k[1]}: PT '{pt}' ≠ Hoàng Long"))
    if k not in cust_b:
        ROWS3.append(("-", "kh/lich-cua-toi.*", f"thiếu buổi {k[0]:%d/%m/%Y} {k[1]} ({kind}) có ở kh/buoi-pt.js"))
    elif cust_b[k][1] not in (pt, "Hoàng Long"):
        ROWS3.append(("-", "kh/lich-cua-toi.*", f"buổi {k[0]:%d/%m/%Y} {k[1]}: PT '{cust_b[k][1]}' ≠ '{pt}'"))
for k, (kind, who) in sorted(cust_b.items()):
    if k not in cust_a:
        ROWS3.append(("-", "kh/buoi-pt.js", f"thiếu buổi {k[0]:%d/%m/%Y} {k[1]} ({kind}) có ở kh/lich-cua-toi"))

# phía PT: lich-ranh (tuần 0 trong HTML, các tuần khác trong WEEKS.set) và trang-chu (day-panel)
pt_ranh = {}
for m in re.finditer(r'data-state="booked" data-q="(\d{2}/\d{2}) (\d{2}:\d{2}) ([^"]+)"', rd("pt/lich-ranh.html")):
    pt_ranh[(dmy(m.group(1) + "/2026"), m.group(2))] = m.group(3)
MON0 = date(2026, 9, 21)
lj = rd("pt/lich-ranh.js")
for k, body in re.findall(r"WEEKS\.set\((-?\d+), \[(.*?)\]\);", lj, re.S):
    for m in re.finditer(r"d:\s*(\d),\s*start:\s*(\d+)\s*\*\s*60(?:\s*\+\s*(\d+))?,[^}]*?who:\s*'([^']+)'", body):
        d_, h, mi, who = int(m.group(1)), int(m.group(2)), int(m.group(3) or 0), m.group(4)
        pt_ranh[(MON0 + timedelta(days=7 * int(k) + d_), f"{h:02d}:{mi:02d}")] = who.lower()

pt_home = {}
th = rd("pt/trang-chu.html")
for i, body in re.findall(r'data-day-panel="(\d)"[^>]*>(.*?)(?=data-day-panel="|</section>)', th, re.S):
    d0 = TODAY + timedelta(days=int(i))
    for blk in re.split(r'(?=<div class="data-row")', body):
        mt_ = re.search(r'row-title">\s*(\d{2}:\d{2})', blk)
        mn = re.search(r'fa-user"></i>\s*([^<]+)<', blk)
        if mt_ and mn:
            pt_home[(d0, mt_.group(1))] = mn.group(1).strip().lower()
if not pt_ranh:
    ROWS3.append(("-", "pt/lich-ranh.*", "không đọc được buổi đã có khách"))
if not pt_home:
    ROWS3.append(("-", "pt/trang-chu.html", "không đọc được buổi 7 ngày"))
AN = "nguyễn minh an"
for k, (pt, kind, st) in sorted(cust_a.items()):
    if st != "scheduled" or not (TODAY < k[0] <= TODAY + timedelta(days=30)):
        continue
    if pt_ranh.get(k) != AN:
        ROWS3.append(("-", "pt/lich-ranh.*", f"thiếu buổi {k[0]:%d/%m/%Y} {k[1]} của Nguyễn Minh An"))
    if k[0] <= TODAY + timedelta(days=6) and pt_home.get(k) != AN:
        ROWS3.append(("-", "pt/trang-chu.html", f"thiếu buổi {k[0]:%d/%m/%Y} {k[1]} của Nguyễn Minh An (7 ngày tới)"))
for src, dct in (("pt/lich-ranh.*", pt_ranh), ("pt/trang-chu.html", pt_home)):
    for k, who in sorted(dct.items()):
        if who == AN and k[0] > TODAY and (k not in cust_a or cust_a[k][2] != "scheduled"):
            ROWS3.append(("-", src, f"buổi {k[0]:%d/%m/%Y} {k[1]} của Nguyễn Minh An không có ở lịch khách"))

# ---- 4. tên / giá gói ----
CANON = {"Gói Ngày": 120000, "Gói 1 Tháng": 799000, "Gói 3 Tháng": 2190000, "Gói 9 Tháng": 5490000,
         "Gói 12 Tháng": 6990000, "Gói 6 Tháng": 3990000, "Gói PT 1 Tháng": 8800000, "Gói PT 2 Tháng": 16500000,
         "Gói PT 3 Tháng": 23900000, "Gói PT Couple 1 Tháng": 13200000, "Gói PT Couple 2 Tháng": 24900000,
         "Buổi PT lẻ": 450000}
ROWS4 = EXTRA["Tên / giá gói lệch"]
NAME = re.compile(r"Gói PT Couple \d Tháng|Gói PT \d+ Tháng|Gói \d+ Tháng|Gói Ngày|Buổi PT [Ll]ẻ")
BADNAME = re.compile(r"Gói (?:2|24|36) Tháng|Gói PT (?:6|12) Tháng|Gói PT Couple (?:3|6|12) Tháng")
SALE_PAGES = {"kh/mua-goi.html", "kh/mua-goi.js", "lt/ban-hang.html", "lt/ban-hang.js", "lt/ban-goi-pt.html",
              "lt/ban-goi-pt.js"}


def vnd(v):
    return f"{v:,}".replace(",", ".") + "đ"


def canon_name(n):
    return "Buổi PT lẻ" if n.lower() == "buổi pt lẻ" else n


def price_check(f, text):
    for m in NAME.finditer(text):
        n = canon_name(m.group(0))
        tail = text[m.end(): m.end() + 70]
        nxt = NAME.search(tail)
        if nxt:
            tail = tail[:nxt.start()]
        pm = re.search(r"(?<![\d.])(\d{1,3}(?:\.\d{3})+)\s*đ", tail)
        if not pm:
            continue
        gap = tail[:pm.start()]
        if "iết kiệm" in gap or "iết kiệm" in text[max(0, m.start() - 14): m.start()] or "hoa hồng" in gap.lower():
            continue
        if re.search(r"GYMVUI10|NGAY15K|giảm", gap, re.I):
            continue
        v = int(pm.group(1).replace(".", ""))
        if n in CANON and v != CANON[n]:
            ROWS4.append(("-", f, f"{n}: {pm.group(1)}đ ≠ {vnd(CANON[n])}"))


for f in all_pages():
    if f.startswith(("Manager/", "Admin/", "blog/")):
        continue
    for g in (f, os.path.splitext(f)[0] + ".js"):
        if not os.path.exists(g) or g in SKIP_PAGES:
            continue
        t = rd(g)
        if g.endswith(".html"):
            t = strip(t)
        price_check(g, t)
        for m in BADNAME.finditer(t):
            ROWS4.append(("-", g, f"gói không có trong catalogue: {m.group(0)}"))
        if g in SALE_PAGES and "Gói 6 Tháng" in t:
            ROWS4.append(("-", g, "'Gói 6 Tháng' (ngừng bán) xuất hiện ở màn bán"))
    # thuộc tính data-price / data-name
    for tag in re.findall(r'<[^>]*data-price="\d+"[^>]*>', rd(f)):
        nm = re.search(r'data-name="([^"]+)"', tag)
        pr = re.search(r'data-price="(\d+)"', tag)
        if nm and pr:
            n = nm.group(1)
            n = n[0].upper() + n[1:]
            n = n.replace("tháng", "Tháng").replace("ngày", "Ngày")
            if n in CANON and int(pr.group(1)) != CANON[n]:
                ROWS4.append(("-", f, f"data-price {n}: {pr.group(1)} ≠ {CANON[n]}"))
    for g in (f, os.path.splitext(f)[0] + ".js"):
        for m in re.finditer(r"""name:\s*'(Gói[^']+|Buổi PT lẻ)'[^}]*?price:\s*(\d+)""", rd(g)):
            n, v = m.group(1), int(m.group(2))
            if n in CANON and v != CANON[n]:
                ROWS4.append(("-", g, f"{n}: price {v} ≠ {CANON[n]}"))

# Manager: MPKG / PTPKG
mg = rd("Manager/gms_manager_mockup_v24.html")
for m in re.finditer(r"\{id:'[GP]-\d+',name:'([^']+)',(?:n:\d+,)?price:(\d+)[^}]*?status:'(\w+)'", mg):
    n, v, stt = m.group(1).replace(" (cũ)", ""), int(m.group(2)), m.group(3)
    if n in CANON and v != CANON[n]:
        ROWS4.append(("-", "Manager (MPKG/PTPKG)", f"{n}: {vnd(v)} ≠ {vnd(CANON[n])}"))
    if BADNAME.search(n):
        ROWS4.append(("-", "Manager (MPKG/PTPKG)", f"gói không có trong catalogue: {n}"))
    if n == "Gói 6 Tháng" and stt == "Active":
        ROWS4.append(("-", "Manager (MPKG)", "'Gói 6 Tháng' đang Active (phải ngừng bán)"))
for g in ("Manager/gms_manager_mockup_v24.html", "Admin/gms_admin_mockup_v24.html"):
    for m in BADNAME.finditer(rd(g)):
        ROWS4.append(("-", g, f"gói không có trong catalogue: {m.group(0)}"))

# ---- 5. đơn hàng ----
ROWS5 = EXTRA["Đơn hàng lệch"]
orders = {}
for m in re.finditer(r"\{id:'(DH-\d{4}-\d{4})',cust:'([^']+)',pkg:'([^']+)',amount:(\d+),method:'([^']+)',status:'(\w+)'", mg):
    orders[m.group(1)] = (m.group(2), m.group(3), int(m.group(4)), m.group(5), m.group(6))
if not orders:
    ROWS5.append(("-", "Manager", "không đọc được ORDERS"))


def norm_pkg(x):
    x = re.sub(r"\s*\(.*?\)", "", x)
    x = re.sub(r"\s*·.*", "", x)
    return x.strip().lower()


hist = {}
for blk in re.split(r'(?=<div class="data-row")', rd("kh/lich-su-giao-dich.html")):
    c = re.search(r"DH-\d{4}-\d{4}", blk)
    am = re.search(r'<b class="tabular">([\d.]+)đ</b>', blk)
    ti = re.search(r'row-title">([^<]+)', blk)
    if c and am and ti:
        hist[c.group(0)] = (ti.group(1).strip(), int(am.group(1).replace(".", "")))
if not hist:
    ROWS5.append(("-", "kh/lich-su-giao-dich.html", "không đọc được danh sách đơn"))
for code, (pkgname, amt) in sorted(hist.items()):
    if code not in orders:
        ROWS5.append(("-", "Manager (ORDERS)", f"thiếu đơn {code} của Nguyễn Minh An có ở kh/lich-su-giao-dich"))
        continue
    cu, mp, ma = orders[code][0], orders[code][1], orders[code][2]
    if norm_pkg(mp) != norm_pkg(pkgname):
        ROWS5.append(("-", "Manager vs lịch sử", f"{code}: gói '{mp}' ≠ '{pkgname}'"))
    if ma != amt:
        ROWS5.append(("-", "Manager vs lịch sử", f"{code}: {vnd(ma)} ≠ {vnd(amt)}"))
    if cu != "Nguyễn Minh An":
        ROWS5.append(("-", "Manager vs lịch sử", f"{code}: khách '{cu}' ≠ Nguyễn Minh An"))
for code, v in sorted(orders.items()):
    if v[0] == "Nguyễn Minh An" and code not in hist:
        ROWS5.append(("-", "kh/lich-su-giao-dich.html", f"thiếu đơn {code} của Nguyễn Minh An có ở Manager"))
for blk in re.split(r'(?=<div class="data-row")', rd("lt/ca-lam-viec.html")):
    c = re.search(r"DH-\d{4}-\d{4}", blk)
    if not c:
        continue
    code = c.group(0)
    if code not in orders:
        ROWS5.append(("-", "Manager (ORDERS)", f"thiếu đơn {code} có ở lt/ca-lam-viec"))
        continue
    am = re.search(r'<b class="tabular[^"]*">([\d.]+)đ</b>', blk)
    cu, mp, ma, me, ms = orders[code]
    nm = re.search(r"DH-\d{4}-\d{4} · ([^<]+)", blk)
    if am and int(am.group(1).replace(".", "")) != ma:
        ROWS5.append(("-", "lt/ca-lam-viec vs Manager", f"{code}: {am.group(1)}đ ≠ {vnd(ma)}"))
    if nm and nm.group(1).strip().replace("&amp;", "&") != cu:
        ROWS5.append(("-", "lt/ca-lam-viec vs Manager", f"{code}: khách '{nm.group(1).strip()}' ≠ '{cu}'"))
    pm_ = re.search(r'fa-dumbbell"></i>\s*([^<]+)<', blk)
    if pm_ and norm_pkg(pm_.group(1)) != norm_pkg(mp):
        ROWS5.append(("-", "lt/ca-lam-viec vs Manager", f"{code}: gói '{pm_.group(1).strip()}' ≠ '{mp}'"))

# ---- 6. dữ liệu cá nhân khớp giữa Manager, tai-khoan, quen-mat-khau ----
EXTRA["Thông tin cá nhân lệch"] = []
ROWS6 = EXTRA["Thông tin cá nhân lệch"]


def digits(s):
    return re.sub(r"\D", "", s or "")


def mgr_rec(code):
    m = re.search(r"\{id:'" + re.escape(code) + r"',name:'([^']+)'([^}]*)\}", mg)
    if not m:
        return None
    body = m.group(2)
    g = lambda k: (re.search(k + r":'([^']*)'", body) or [None, ""])[1]
    dob = g("dob")
    if re.match(r"\d{4}-\d{2}-\d{2}", dob):  # ISO -> dd/mm/yyyy
        dob = "/".join(reversed(dob.split("-")))
    return {"name": m.group(1), "phone": digits(g("phone")), "email": g("email"), "dob": dob}


PEOPLE = [("KH-0241", "kh/tai-khoan.html"), ("NV-0042", "lt/tai-khoan.html"), ("NV-0118", "pt/tai-khoan.html")]
reset_js = rd("quen-mat-khau.js") + rd("quen-mat-khau.html")
recs = {c: mgr_rec(c) for c in ("KH-0241", "NV-0042", "KH-0430", "NV-0118")}
for c, r in recs.items():
    if r is None:
        ROWS6.append(("-", "Manager", f"không có bản ghi {c}"))
if recs["NV-0042"] and recs["KH-0430"]:
    for k in ("name", "phone", "email", "dob"):
        if recs["NV-0042"][k] != recs["KH-0430"][k]:
            ROWS6.append(("-", "Manager (NV-0042 vs KH-0430)", f"{k}: '{recs['NV-0042'][k]}' ≠ '{recs['KH-0430'][k]}'"))
for code, page in PEOPLE:
    r = recs.get(code)
    if not r:
        continue
    raw = rd(page)
    plain = strip(raw)
    if code not in raw:
        ROWS6.append(("-", page, f"không hiện mã {code}"))
    if r["name"] not in plain:
        ROWS6.append(("-", page, f"tên '{r['name']}' ({code}) không có trong trang"))
    if r["phone"] not in digits(plain) and r["phone"] not in digits(raw):
        ROWS6.append(("-", page, f"SĐT {r['phone']} ({code}) không có trong trang"))
    if r["email"] not in raw:
        ROWS6.append(("-", page, f"email {r['email']} ({code}) không có trong trang"))
    # ngày sinh: nếu trang có hiện ngày sinh thì phải đúng
    shown = None
    m = re.search(r'id="acctDob"[^>]*value="(\d{4})-(\d{2})-(\d{2})"', raw)
    if m:
        shown = f"{m.group(3)}/{m.group(2)}/{m.group(1)}"
    else:
        m = re.search(r"Ngày sinh[^0-9]{0,80}(\d{2}/\d{2}/\d{4})", plain)
        shown = m.group(1) if m else None
    if shown is None:
        ROWS6.append(("-", page, f"không hiện ngày sinh ({code}, Manager: {r['dob']})"))
    elif shown != r["dob"]:
        ROWS6.append(("-", page, f"ngày sinh {shown} ≠ Manager {r['dob']} ({code})"))
    # quên mật khẩu: email của tài khoản mẫu phải dùng đúng email này
    if r["email"] not in reset_js:
        ROWS6.append(("-", "quen-mat-khau.*", f"thiếu email {r['email']} ({code})"))
# KH-0430 là hồ sơ khách của NV-0042: chon-vai-tro phải ghi đúng mã
if "KH-0430" not in rd("chon-vai-tro.html"):
    ROWS6.append(("-", "chon-vai-tro.html", "thiếu mã KH-0430 (hồ sơ khách của NV-0042)"))
# mọi email/SĐT mẫu của 3 người ở quen-mat-khau không được trỏ sang người khác
for m in re.finditer(r"'([\w.]+@[\w.]+)'\s*:\s*\{[^}]*?name:\s*'([^']+)'", reset_js):
    for code, r in recs.items():
        if r and r["email"] == m.group(1) and r["name"] != m.group(2):
            ROWS6.append(("-", "quen-mat-khau.js", f"email {m.group(1)}: tên '{m.group(2)}' ≠ Manager '{r['name']}'"))

# ---- in bảng ----
rows = sorted(set(rows), key=lambda r: (r[0], r[1]))
print(f"{'#':>3} | {'file':<45} | thiếu gì")
print("-" * 100)
for n, f, m in rows:
    print(f"{n or '-':>3} | {f:<45} | {m}")
print("-" * 100)
print(f"{len(specs)} mục spec, {len(rows)} dòng thiếu")
total = len(rows)
for title, lst in EXTRA.items():
    lst = sorted(set(lst), key=lambda r: (r[1], r[2]))
    print(f"\n## {title}: {len(lst)}")
    if lst:
        print(f"{'#':>3} | {'file':<45} | issue")
        print("-" * 100)
        for n, f, m in lst:
            print(f"{n:>3} | {f:<45} | {m}")
    total += len(lst)
print(f"\nTỔNG: {total} vấn đề")
sys.exit(1 if total else 0)
