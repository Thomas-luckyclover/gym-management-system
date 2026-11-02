/**
 * transaction-list.js — Tương tác màn Giao dịch toàn hệ thống cho Quản lý (/manager/transactions).
 * Màn #43 - UC-26.
 *
 * Gọi máy chủ:
 *   GET  ?action=detail&orderId=        chi tiết đơn (JSON)
 *   GET  ?action=lookup&transactionId=  tra cứu VNPay (API-06, chỉ đọc)
 *   POST action=refund                  xác nhận đã hoàn tiền (BR-81)
 */
document.addEventListener("DOMContentLoaded", function () {
    const contextPath = typeof getContextPath === "function" ? getContextPath() : "";
    const endpoint = contextPath + "/manager/transactions";
    const MAX_NOTE = 480;       // 500 ký tự tối đa, phần đầu dành cho số tiền hoàn
    const MIN_NOTE = 5;

    const detailModal = document.getElementById("orderDetailModal");
    const refundModal = document.getElementById("refundConfirmModal");
    const lookups = {};         // transactionId -> kết quả tra cứu gần nhất
    let currentOrder = null;

    // ===== 1. TIỆN ÍCH =====
    const $ = (id) => document.getElementById(id);

    function toast(type, message) {
        if (typeof showToast === "function") {
            showToast(type, message);
        } else {
            alert(message);
        }
    }

    function money(val) {
        if (typeof formatMoney === "function") {
            return formatMoney(val);
        }
        const n = Number(val);
        return isNaN(n) ? "0đ" : Math.round(n).toLocaleString("vi-VN") + "đ";
    }

    function dateTime(val) {
        if (!val) {
            return "---";
        }
        if (typeof formatDateTime === "function") {
            return formatDateTime(val);
        }
        const p = String(val).split("T");
        if (p.length === 2) {
            const d = p[0].split("-"), t = p[1].split(":");
            if (d.length === 3 && t.length >= 2) {
                return d[2] + "/" + d[1] + "/" + d[0] + " " + t[0] + ":" + t[1];
            }
        }
        return val;
    }

    function dateOnly(val) {
        if (!val) {
            return "---";
        }
        const d = String(val).substring(0, 10).split("-");
        return d.length === 3 ? d[2] + "/" + d[1] + "/" + d[0] : val;
    }

    function timeOnly(val) {
        if (!val) {
            return "---";
        }
        const p = String(val).split("T");
        return p.length === 2 ? p[1].substring(0, 5) : val;
    }

    function esc(str) {
        if (str === null || str === undefined) {
            return "";
        }
        if (typeof escapeHtml === "function") {
            return escapeHtml(String(str));
        }
        return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;")
                .replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    }

    function digits(str) {
        return String(str || "").replace(/[^0-9]/g, "");
    }

    function openModal(modal) {
        if (!modal) {
            return;
        }
        modal.classList.add("open");
        modal.setAttribute("aria-hidden", "false");
        document.body.style.overflow = "hidden";
    }

    function closeModal(modal) {
        if (!modal) {
            return;
        }
        modal.classList.remove("open");
        modal.setAttribute("aria-hidden", "true");
        if (!document.querySelector(".tx-overlay.open")) {
            document.body.style.overflow = "";
        }
    }

    // ===== 2. ĐÓNG POPUP =====
    document.querySelectorAll("[data-modal-close]").forEach(function (btn) {
        btn.addEventListener("click", function () {
            closeModal(btn.closest(".tx-overlay"));
        });
    });
    document.querySelectorAll(".tx-overlay").forEach(function (overlay) {
        overlay.addEventListener("click", function (e) {
            if (e.target === overlay) {
                closeModal(overlay);
            }
        });
    });
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") {
            document.querySelectorAll(".tx-overlay.open").forEach(closeModal);
        }
    });

    // ===== 3. POPUP CHI TIẾT =====

    function loadOrderDetail(orderId) {
        fetch(endpoint + "?action=detail&orderId=" + encodeURIComponent(orderId),
                {headers: {"X-Requested-With": "XMLHttpRequest"}})
                .then(function (res) {
                    return res.json();
                })
                .then(function (res) {
                    if (!res.success || !res.data) {
                        toast("error", res.message || "Không thể tải chi tiết giao dịch.");
                        return;
                    }
                    renderDetail(res.data);
                    openModal(detailModal);
                })
                .catch(function () {
                    toast("error", "Lỗi kết nối máy chủ khi lấy chi tiết giao dịch.");
                });
    }

    function badge(cls, text) {
        return '<span class="badge ' + cls + '">' + esc(text) + "</span>";
    }

    function refundReason(cancelType) {
        switch (cancelType) {
            case "PaymentTimeout":
                return "Khách trả sau khi đơn đã hết hạn.";
            case "ReplacedByNewOrder":
                return "Khách trả vào đơn cũ đã bị thay bằng đơn mới.";
            case "InvalidPayment":
                return "Số tiền VNPay thu không khớp với đơn.";
            default:
                return "Khách đã trả tiền nhưng đơn không hợp lệ.";
        }
    }

    function renderDetail(order) {
        currentOrder = order;
        const attempts = (order.attempts || []).slice().sort(function (a, b) {
            return String(a.createdAt).localeCompare(String(b.createdAt));
        });
        const isOnline = order.channel === "Online";
        const pendingRefund = attempts.find(function (a) {
            return a.needsRefund && !a.refundedAt;
        });
        const doneRefund = attempts.find(function (a) {
            return a.needsRefund && a.refundedAt;
        });
        const lastCash = attempts.find(function (a) {
            return a.paymentMethod === "Cash" || a.paymentMethod === "Transfer";
        });
        const counterMethod = lastCash && lastCash.paymentMethod === "Transfer" ? "Chuyển khoản" : "Tiền mặt";

        // --- Tiêu đề + nhãn tình trạng ---
        $("txdTitle").textContent = order.orderCode || "---";
        let st;
        if (doneRefund) {
            st = badge("badge-success", "Đã hoàn tiền");
        } else if (pendingRefund) {
            st = badge("badge-danger", "Cần hoàn tiền");
        } else if (order.status === "Paid") {
            st = badge("badge-success", "Đã thanh toán");
        } else if (order.status === "Pending") {
            st = badge("badge-warning", "Chờ thanh toán");
        } else if (order.cancelType === "PaymentTimeout") {
            st = badge("badge-neutral", "Hết hạn thanh toán");
        } else if (order.cancelType === "ReplacedByNewOrder") {
            st = badge("badge-neutral", "Đã thay bằng đơn mới");
        } else if (order.cancelType === "InvalidPayment") {
            st = badge("badge-danger", "Thanh toán không hợp lệ");
        } else {
            st = badge("badge-neutral", "Đã hủy tại quầy");
        }
        $("txdBadge").innerHTML = st;

        $("txdSub").textContent = isOnline
                ? "Online · VNPay · Tạo lúc " + dateTime(order.createdAt)
                : "Offline · " + counterMethod + " · Tạo lúc " + dateTime(order.createdAt)
                + (order.shiftId ? " · Ca #" + order.shiftId : "");

        // --- Khung thông báo đầu popup ---
        const callout = $("txdCallout");
        callout.hidden = false;
        if (pendingRefund) {
            callout.className = "tx-callout danger";
            callout.innerHTML = "<b>" + esc(refundReason(order.cancelType)) + "</b> Khách chưa được cấp gói. Cần hoàn lại tiền cho khách.";
        } else if (doneRefund) {
            callout.className = "tx-callout info";
            callout.innerHTML = "<b>" + esc(doneRefund.refundedByName || "Quản lý") + "</b> đã hoàn lúc "
                    + esc(dateTime(doneRefund.refundedAt)) + ". Ghi chú: " + esc(doneRefund.refundNote || "---");
        } else if (!isOnline) {
            callout.className = "tx-callout info";
            callout.innerHTML = "Bán offline tại quầy bởi lễ tân <b>" + esc(order.createdByEmployeeName || "---")
                    + "</b>. Nếu cần trả tiền cho khách thì xử lý ngay tại quầy, không qua màn này.";
        } else if (order.status === "Cancelled") {
            callout.className = "tx-callout info";
            callout.textContent = "Đơn đã hủy, khách không bị trừ tiền.";
        } else {
            callout.hidden = true;
        }

        // --- Khách hàng ---
        $("txdName").textContent = order.customerName || "Khách lẻ";
        $("txdPhone").textContent = order.customerPhone || "---";
        $("txdEmail").textContent = order.customerEmail || "---";
        const zalo = $("txdZalo");
        if (order.customerPhone) {
            zalo.href = "https://zalo.me/" + digits(order.customerPhone);
            zalo.hidden = false;
        } else {
            zalo.hidden = true;
        }

        // --- Gói đã mua (hiệu lực lấy từ Subscriptions, nếu đã cấp) ---
        $("txdPackage").textContent = order.packageName || "---";
        if (order.packageStartDate && order.packageEndDate) {
            const subStatus = {
                Active: ["badge-success", "Đang hiệu lực"],
                Frozen: ["badge-info", "Đang bảo lưu"],
                Used: ["badge-neutral", "Đã dùng hết"],
                Expired: ["badge-neutral", "Hết hạn"],
                Cancelled: ["badge-neutral", "Đã hủy"]
            }[order.packageStatus];
            $("txdPeriod").innerHTML = "<b>" + dateOnly(order.packageStartDate) + " → " + dateOnly(order.packageEndDate) + "</b> "
                    + (subStatus ? badge(subStatus[0], subStatus[1]) : "");
        } else {
            $("txdPeriod").textContent = order.status === "Paid" ? "---" : "Chưa cấp gói";
        }
        $("txdPt").textContent = order.ptName || (order.status === "Paid" ? "Không có" : "Chưa phân công");
        $("txdPrice").textContent = money(order.unitPrice);
        $("txdVoucher").textContent = order.voucherCode
                ? order.voucherCode + " −" + money(order.discountAmount)
                : "Không áp dụng";
        $("txdTotalLabel").textContent = "Tổng (đã gồm VAT " + (order.vatRate || 0) + "%)";
        $("txdTotal").textContent = money(order.totalPayable);

        // --- Lịch sử thanh toán ---
        $("txdRefHead").textContent = isOnline ? "Mã VNPay" : "Người thu";
        const body = $("txdAttempts");
        body.innerHTML = "";
        if (attempts.length === 0) {
            body.innerHTML = '<tr><td colspan="6" style="text-align:center;color:var(--color-text-soft)">Chưa có lần thanh toán nào.</td></tr>';
        }
        attempts.forEach(function (att, i) {
            let result;
            if (att.status === "Success") {
                result = badge("badge-success", "Thành công");
                if (att.needsRefund) {
                    result = '<span class="tx-badges">' + result
                            + (att.refundedAt ? badge("badge-success", "Đã hoàn") : badge("badge-danger", "Cần hoàn")) + "</span>";
                }
            } else if (att.status === "Pending") {
                result = badge("badge-warning", "Đang chờ");
            } else if (att.status === "Failed") {
                result = badge("badge-danger", "Thất bại");
            } else {
                result = badge("badge-neutral", "Hết hạn");
            }

            const isGateway = att.paymentMethod === "OnlineGateway";
            const ref = isGateway
                    ? '<span class="tx-ref">' + esc(att.gatewayReference || "---") + "</span>"
                    : esc(att.receiverName || "---") + (order.shiftId ? " · Ca #" + order.shiftId : "");
            const action = isGateway && att.gatewayReference
                    ? '<button type="button" class="btn btn-outline btn-sm btn-lookup" data-transaction-id="' + att.transactionId + '">Tra cứu</button>'
                    : "";

            const tr = document.createElement("tr");
            tr.innerHTML = "<td>#" + (i + 1) + "</td><td>" + esc(timeOnly(att.createdAt)) + "</td><td>" + result
                    + '</td><td class="r">' + money(att.amount) + "</td><td>" + ref + '</td><td class="r">' + action + "</td>";
            body.appendChild(tr);
        });

        body.querySelectorAll(".btn-lookup").forEach(function (btn) {
            btn.addEventListener("click", function () {
                runLookup(btn.dataset.transactionId, btn).then(renderLookupInDetail);
            });
        });

        // Chỉ hiện kết quả tra cứu cũ nếu đúng giao dịch của đơn này
        $("txdLookup").hidden = true;
        $("txdLookup").innerHTML = "";

        $("txdNote").textContent = isOnline
                ? "Số tiền khách thực trả có thể khác số tiền đơn. Bấm “Tra cứu” để xem số VNPay thực thu."
                : (counterMethod === "Chuyển khoản"
                        ? "Chuyển khoản tại quầy do lễ tân bấm xác nhận đã nhận. Hệ thống không lưu mã giao dịch ngân hàng."
                        : "");

        // --- Nút Hoàn tiền trong popup ---
        const refundBtn = $("txdRefundBtn");
        if (pendingRefund && isOnline) {
            refundBtn.hidden = false;
            refundBtn.onclick = function () {
                closeModal(detailModal);
                openRefund({
                    transactionId: pendingRefund.transactionId,
                    orderCode: order.orderCode,
                    packageName: order.packageName,
                    customer: order.customerName,
                    phone: order.customerPhone,
                    amount: pendingRefund.amount
                });
            };
        } else {
            refundBtn.hidden = true;
            refundBtn.onclick = null;
        }
    }

    // ===== 4. TRA CỨU VNPAY (API-06) =====
    function runLookup(transactionId, button) {
        if (button) {
            button.disabled = true;
        }
        return fetch(endpoint + "?action=lookup&transactionId=" + encodeURIComponent(transactionId),
                {headers: {"X-Requested-With": "XMLHttpRequest"}})
                .then(function (res) {
                    return res.json();
                })
                .then(function (res) {
                    if (button) {
                        button.disabled = false;
                    }
                    if (!res.success || !res.data) {
                        toast("error", res.message || "Không tra cứu được VNPay. Thử lại sau.");
                        return null;
                    }
                    lookups[transactionId] = res.data;
                    return {transactionId: transactionId, data: res.data};
                })
                .catch(function () {
                    if (button) {
                        button.disabled = false;
                    }
                    toast("error", "Không tra cứu được VNPay. Thử lại sau.");
                    return null;
                });
    }

    /**
     * Dữ liệu tra cứu (JSON data): statusLabel, success (bool), amount (VND thực thu),
     * orderAmount, bankCode, payDate (chuỗi dd/MM HH:mm:ss), gatewayReference.
     */
    function renderLookupInDetail(result) {
        if (!result) {
            return;
        }
        const d = result.data;
        const diff = Number(d.amount) - Number(d.orderAmount);
        const diffBadge = diff === 0
                ? badge("badge-success", "Khớp với đơn")
                : badge("badge-danger", (diff > 0 ? "Thừa " : "Lệch ") + money(Math.abs(diff)));
        const box = $("txdLookup");
        box.hidden = false;
        box.innerHTML = "<b>Kết quả tra cứu VNPay · mã " + esc(d.gatewayReference || "") + "</b>"
                + '<div class="tx-kv"><span>Trạng thái tại VNPay</span>' + badge(d.success ? "badge-success" : "badge-neutral", d.statusLabel || "---") + "</div>"
                + '<div class="tx-kv"><span>Số tiền VNPay thực thu</span><b>' + money(d.amount) + "</b></div>"
                + '<div class="tx-kv"><span>So với đơn ' + money(d.orderAmount) + "</span>" + diffBadge + "</div>"
                + '<div class="tx-kv"><span>Ngân hàng · Thời điểm trả</span><b>' + esc((d.bankCode || "---") + " · " + (d.payDate || "---")) + "</b></div>";
    }

    // ===== 5. POPUP XÁC NHẬN HOÀN TIỀN =====

    const refundForm = $("refundConfirmForm");
    const amountInput = $("refundAmountInput");
    const noteInput = $("refundNoteInput");
    let refundCtx = null;

    function showErr(id, message) {
        const el = $(id);
        if (!el) {
            return;
        }
        el.hidden = !message;
        el.textContent = message || "";
    }

    function setAmountText(value) {
        const n = Number(digits(value));
        amountInput.value = n > 0 ? n.toLocaleString("vi-VN") : "";
    }

    function applyLookupToRefund(data) {
        if (data.success && Number(data.amount) > 0) {
            $("txrGatewayAmount").textContent = money(data.amount);
            setAmountText(data.amount);
            $("txrAmountHint").innerHTML = "<span>Tự điền theo số VNPay thực thu. Sửa nếu hoàn khác.</span>";
        } else {
            $("txrGatewayAmount").textContent = data.statusLabel || "Chưa có thông tin";
            $("txrAmountHint").innerHTML = "<span>VNPay chưa có số thực thu. Vui lòng tự nhập số tiền đã hoàn.</span>";
        }
    }

    function openRefund(ctx) {
        refundCtx = ctx;
        $("refundTransactionIdInput").value = ctx.transactionId;
        $("txrOrder").textContent = (ctx.orderCode || "---") + (ctx.packageName ? " · " + ctx.packageName : "");
        $("txrCustomer").textContent = (ctx.customer || "Khách hàng") + (ctx.phone ? " · " + ctx.phone : "");
        $("txrOrderAmount").textContent = money(ctx.amount);
        $("txrGatewayAmount").textContent = "Chưa tra cứu";
        $("txrAmountHint").innerHTML = "<span>Mặc định là số tiền đơn. Tra cứu VNPay để lấy số thực thu.</span>";
        setAmountText(ctx.amount);
        noteInput.value = "";
        $("txrNoteCount").textContent = "0/" + MAX_NOTE;
        showErr("refundAmountError", "");
        showErr("refundNoteError", "");
        $("txrAmountBox").classList.remove("bad");

        const known = lookups[ctx.transactionId];
        if (known) {
            applyLookupToRefund(known);
        }
        openModal(refundModal);
        amountInput.focus();
    }

    $("txrLookupBtn").addEventListener("click", function () {
        if (!refundCtx) {
            return;
        }
        runLookup(refundCtx.transactionId, $("txrLookupBtn")).then(function (result) {
            if (result) {
                applyLookupToRefund(result.data);
            }
        });
    });

    amountInput.addEventListener("input", function () {
        setAmountText(amountInput.value);
        $("txrAmountBox").classList.remove("bad");
        showErr("refundAmountError", "");
    });
    noteInput.addEventListener("input", function () {
        $("txrNoteCount").textContent = noteInput.value.length + "/" + MAX_NOTE;
        showErr("refundNoteError", "");
    });

    refundForm.addEventListener("submit", function (e) {
        e.preventDefault();
        if (!refundCtx) {
            return;
        }

        const amount = Number(digits(amountInput.value));
        const note = (noteInput.value || "").trim();
        const known = lookups[refundCtx.transactionId];
        let ok = true;

        // Số tiền hoàn: > 0, và không vượt số VNPay thực thu nếu đã tra cứu (BR-81)
        if (!(amount > 0) || (known && amount > Number(known.amount))) {
            $("txrAmountBox").classList.add("bad");
            showErr("refundAmountError", "Số tiền hoàn không hợp lệ.");
            ok = false;
        }
        if (note.length < MIN_NOTE || note.length > MAX_NOTE) {
            showErr("refundNoteError", "Ghi chú hoàn tiền phải từ " + MIN_NOTE + " đến " + MAX_NOTE + " ký tự (ghi rõ số TK hoặc mã giao dịch hoàn).");
            ok = false;
        }
        if (!ok) {
            return;
        }

        const submitBtn = refundForm.querySelector("button[type='submit']");
        submitBtn.disabled = true;

        const params = new URLSearchParams();
        params.append("action", "refund");
        params.append("transactionId", refundCtx.transactionId);
        // Số tiền nằm đầu ghi chú (không thêm cột DB): tổng ≤ 500 ký tự
        params.append("refundNote", "Hoàn " + amount.toLocaleString("vi-VN") + "đ: " + note);
        if (typeof getCsrfToken === "function") {
            params.append("csrfToken", getCsrfToken());
        }

        fetch(endpoint, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                "X-Requested-With": "XMLHttpRequest"
            },
            body: params.toString()
        })
                .then(function (res) {
                    return res.json();
                })
                .then(function (res) {
                    submitBtn.disabled = false;
                    if (res.success) {
                        closeModal(refundModal);
                        toast("success", res.data || "Đã xác nhận hoàn tiền thành công.");
                        setTimeout(function () {
                            fetchPage(window.location.href);
                        }, 500);
                    } else {
                        showErr("refundNoteError", res.message || "Lỗi xử lý hoàn tiền.");
                    }
                })
                .catch(function () {
                    submitBtn.disabled = false;
                    showErr("refundNoteError", "Lỗi kết nối máy chủ. Vui lòng thử lại sau.");
                });
    });

    // ===== 6. AJAX FILTERING & LIVE SEARCH (TỰ ĐỘNG LỌC KHÔNG CẦN ENTER HAY BẤM NÚT) =====
    let searchTimer = null;
    let filterAbortCtrl = null;

    function resetForm(form) {
        form.querySelectorAll('input[type="search"], input[type="text"], input[type="date"]').forEach(function (inp) {
            inp.value = "";
        });
        form.querySelectorAll("select").forEach(function (sel) {
            sel.selectedIndex = 0;
            sel.value = "";
        });
    }

    function doAjaxFilter(form, page) {
        if (!form) return;
        const from = form.querySelector("input[name='from']");
        const to = form.querySelector("input[name='to']");
        if (from && to && from.value && to.value && from.value > to.value) {
            toast("error", "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
            return;
        }

        const formData = new FormData(form);
        const params = new URLSearchParams();
        for (const [k, v] of formData.entries()) {
            if (v !== null && v !== undefined) {
                const s = String(v).trim();
                // Giữ tham số state kể cả khi rỗng ("Tất cả tình trạng") để servlet không tự ép về "refund"
                if (s !== "" || k === "state") {
                    params.set(k, s);
                }
            }
        }
        if (page) {
            params.set("page", page);
        } else {
            params.delete("page");
        }
        const actionUrl = form.getAttribute("action") || endpoint;
        const targetUrl = actionUrl + "?" + params.toString();
        fetchPage(targetUrl);
    }

    function fetchPage(url, pushHistory) {
        if (pushHistory === undefined) {
            pushHistory = true;
        }
        if (filterAbortCtrl) {
            filterAbortCtrl.abort();
        }
        filterAbortCtrl = new AbortController();

        const ordersArea = document.getElementById("txOrdersTableArea");
        const shiftsArea = document.getElementById("txShiftsTableArea");
        const activeArea = ordersArea || shiftsArea;

        if (activeArea) {
            activeArea.style.opacity = "0.5";
            activeArea.style.pointerEvents = "none";
            activeArea.style.transition = "opacity 0.2s ease";
        }

        fetch(url, {
            headers: { "X-Requested-With": "XMLHttpRequest" },
            signal: filterAbortCtrl.signal
        })
        .then(function (res) {
            return res.text();
        })
        .then(function (html) {
            const doc = new DOMParser().parseFromString(html, "text/html");

            // 1. Cập nhật bảng và phân trang
            const curOrders = document.getElementById("txOrdersTableArea");
            const newOrders = doc.getElementById("txOrdersTableArea");
            if (curOrders && newOrders) {
                curOrders.innerHTML = newOrders.innerHTML;
            }

            const curShifts = document.getElementById("txShiftsTableArea");
            const newShifts = doc.getElementById("txShiftsTableArea");
            if (curShifts && newShifts) {
                curShifts.innerHTML = newShifts.innerHTML;
            }

            // 2. Cập nhật link xuất Excel theo đúng bộ lọc hiện tại
            const newExcel = doc.querySelector(".tx-toolbar-end a");
            const curExcel = document.querySelector(".tx-toolbar-end a");
            if (newExcel && curExcel) {
                curExcel.href = newExcel.href;
            }

            // 3. Cập nhật số lượng cần hoàn tiền trên badge tab
            const newTab = doc.querySelector('.tx-tabs a[href*="tab=orders"]');
            const curTab = document.querySelector('.tx-tabs a[href*="tab=orders"]');
            if (newTab && curTab) {
                const newCount = newTab.querySelector(".tx-count");
                const curCount = curTab.querySelector(".tx-count");
                if (newCount) {
                    if (curCount) {
                        curCount.textContent = newCount.textContent;
                        curCount.title = newCount.title;
                    } else {
                        curTab.appendChild(newCount.cloneNode(true));
                    }
                } else if (curCount) {
                    curCount.remove();
                }
            }

            // 4. Đồng bộ thanh địa chỉ URL của trình duyệt (không reload trang)
            if (pushHistory) {
                window.history.replaceState({ url: url }, "", url);
            }
        })
        .catch(function (err) {
            if (err.name !== "AbortError") {
                toast("error", "Lỗi kết nối khi tải danh sách giao dịch.");
            }
        })
        .finally(function () {
            if (activeArea) {
                activeArea.style.opacity = "";
                activeArea.style.pointerEvents = "";
            }
        });
    }

    // Debounce tìm kiếm khi gõ (tự động lọc sau 300ms, không cần bấm Enter)
    document.addEventListener("input", function (e) {
        if (e.target.matches('.tx-search input[name="search"]')) {
            clearTimeout(searchTimer);
            const form = e.target.closest(".tx-filter-form");
            searchTimer = setTimeout(function () {
                doAjaxFilter(form, 1);
            }, 300);
        }
    });

    // Thay đổi số dòng/trang (GBR-18), dropdown Tình trạng, Hình thức hoặc Từ ngày, Đến ngày -> tự động lọc ngay
    document.addEventListener("change", function (e) {
        if (e.target.matches("select[data-page-size-select]")) {
            const form = document.querySelector(".tx-panel .tx-tab.active") && document.querySelector(".tx-panel .tx-tab.active").href.includes("tab=shifts")
                    ? document.querySelector('form input[value="shifts"]').closest(".tx-filter-form")
                    : document.querySelector('form input[value="orders"]').closest(".tx-filter-form");
            if (form) {
                const hiddenPageSize = form.querySelector('input[name="pageSize"]');
                if (hiddenPageSize) {
                    hiddenPageSize.value = e.target.value;
                }
                clearTimeout(searchTimer);
                doAjaxFilter(form, 1);
            }
            return;
        }
        if (e.target.closest(".tx-filters")) {
            const form = e.target.closest(".tx-filter-form");
            if (form) {
                clearTimeout(searchTimer);
                doAjaxFilter(form, 1);
            }
        }
    });

    // Chặn submit mặc định khi bấm Enter trong form -> chạy AJAX
    document.addEventListener("keydown", function (e) {
        if (e.key === "Enter") {
            const form = e.target.closest(".tx-filter-form");
            if (form) {
                e.preventDefault();
                clearTimeout(searchTimer);
                doAjaxFilter(form, 1);
            }
        }
    });

    document.querySelectorAll(".tx-filter-form").forEach(function (form) {
        form.addEventListener("submit", function (e) {
            e.preventDefault();
            clearTimeout(searchTimer);
            doAjaxFilter(form, 1);
        });
    });

    // Bắt sự kiện click chung (Event Delegation): Chi tiết, Hoàn tiền, Phân trang, Xóa lọc
    document.addEventListener("click", function (e) {
        // Nút Chi tiết
        const detailBtn = e.target.closest(".btn-view-detail");
        if (detailBtn && detailBtn.dataset.orderId) {
            loadOrderDetail(detailBtn.dataset.orderId);
            return;
        }

        // Nút Hoàn tiền trên bảng
        const refundBtn = e.target.closest(".btn-table-refund");
        if (refundBtn) {
            openRefund({
                transactionId: refundBtn.dataset.transactionId,
                orderCode: refundBtn.dataset.orderCode,
                packageName: refundBtn.dataset.package,
                customer: refundBtn.dataset.customer,
                phone: refundBtn.dataset.phone,
                amount: refundBtn.dataset.amount
            });
            return;
        }

        // Chuyển trang (Phân trang)
        const pageLink = e.target.closest(".tx-foot-nav a");
        if (pageLink && pageLink.href) {
            e.preventDefault();
            fetchPage(pageLink.href);
            return;
        }

        // Nút Xóa lọc
        const clearBtn = e.target.closest(".tx-filters-end a");
        if (clearBtn) {
            const form = clearBtn.closest(".tx-filter-form");
            if (form) {
                e.preventDefault();
                resetForm(form);
                doAjaxFilter(form, 1);
                return;
            }
        }
    });

    // Hỗ trợ nút Back/Forward của trình duyệt
    window.addEventListener("popstate", function () {
        fetchPage(window.location.href, false);
    });
});
