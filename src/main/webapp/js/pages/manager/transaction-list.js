/**
 * transaction-list.js — Xử lý tương tác màn hình Giao dịch toàn hệ thống cho Quản lý (/manager/transactions).
 * Màn #41 - UC-26, SRS 7.4.2.
 */
document.addEventListener("DOMContentLoaded", function () {
    const contextPath = typeof getContextPath === "function" ? getContextPath() : "";

    // ===== 1. MODAL CHI TIẾT ĐƠN HÀNG =====
    const detailModal = document.getElementById("orderDetailModal");
    const detailCloseButtons = document.querySelectorAll(".trans-modal-close, [data-modal-close]");

    function openModal(modal) {
        if (!modal) return;
        modal.classList.add("open");
        document.body.style.overflow = "hidden";
    }

    function closeModal(modal) {
        if (!modal) return;
        modal.classList.remove("open");
        document.body.style.overflow = "";
    }

    detailCloseButtons.forEach(btn => {
        btn.addEventListener("click", function () {
            const modal = this.closest(".modal-overlay");
            closeModal(modal);
        });
    });

    // Đóng khi click ra vùng overlay
    document.querySelectorAll(".modal-overlay").forEach(overlay => {
        overlay.addEventListener("click", function (e) {
            if (e.target === this) {
                closeModal(this);
            }
        });
    });

    // Phím Esc đóng modal
    document.addEventListener("keydown", function (e) {
        if (e.key === "Escape") {
            document.querySelectorAll(".modal-overlay.open").forEach(m => closeModal(m));
        }
    });

    // Xem chi tiết đơn hàng
    document.querySelectorAll(".btn-view-detail").forEach(btn => {
        btn.addEventListener("click", function () {
            const orderId = this.dataset.orderId;
            if (!orderId) return;

            loadOrderDetail(orderId);
        });
    });

    function loadOrderDetail(orderId) {
        const url = `${contextPath}/manager/transactions?action=detail&orderId=${orderId}`;

        fetch(url, {
            headers: {
                "X-Requested-With": "XMLHttpRequest"
            }
        })
        .then(res => res.json())
        .then(res => {
            if (!res.success || !res.data) {
                if (typeof showToast === "function") {
                    showToast("error", res.message || "Không thể tải chi tiết đơn hàng.");
                } else {
                    alert(res.message || "Lỗi tải chi tiết đơn hàng.");
                }
                return;
            }

            renderOrderDetail(res.data);
            openModal(detailModal);
        })
        .catch(err => {
            console.error("Lỗi xem chi tiết đơn:", err);
            if (typeof showToast === "function") {
                showToast("error", "Lỗi kết nối máy chủ khi lấy chi tiết đơn.");
            }
        });
    }

    function renderOrderDetail(data) {
        const order = data || {};
        const attempts = order.attempts || [];

        // Tiêu đề & Thông tin cơ bản
        document.getElementById("modalOrderCode").textContent = order.orderCode || "---";

        const channelBadge = document.getElementById("modalChannelBadge");
        if (channelBadge) {
            if (order.channel === "Online") {
                channelBadge.className = "badge-channel badge-channel-online";
                channelBadge.textContent = "Trực tuyến";
            } else {
                channelBadge.className = "badge-channel badge-channel-counter";
                channelBadge.textContent = "Tại quầy";
            }
        }

        const statusBadge = document.getElementById("modalStatusBadge");
        if (statusBadge) {
            if (order.status === "Paid") {
                statusBadge.className = "badge badge-success";
                statusBadge.textContent = "Đã thanh toán";
            } else if (order.status === "Pending") {
                statusBadge.className = "badge badge-warning";
                statusBadge.textContent = "Chờ thanh toán";
            } else {
                statusBadge.className = "badge badge-danger";
                statusBadge.textContent = "Đã hủy";
            }
        }

        document.getElementById("modalCreatedAt").textContent = formatDateTimeVal(order.createdAt);
        document.getElementById("modalCustomerName").textContent = order.customerName || "Khách lẻ / Chưa cập nhật";
        document.getElementById("modalCustomerPhone").textContent = order.customerPhone || "---";
        document.getElementById("modalCustomerEmail").textContent = order.customerEmail || "---";

        // Gói tập & Voucher
        document.getElementById("modalPackageName").textContent = order.packageName || "---";
        document.getElementById("modalVoucherCode").textContent = order.voucherCode ? order.voucherCode : "Không áp dụng";

        // Nhân viên & ca
        const staffEl = document.getElementById("modalStaffInfo");
        if (order.channel === "Online") {
            staffEl.textContent = "Thanh toán tự động qua cổng VNPay";
        } else {
            const emp = order.createdByEmployeeName || "Nhân viên quầy";
            const shift = order.shiftId ? ` (Ca #${order.shiftId})` : "";
            staffEl.textContent = `${emp}${shift}`;
        }

        // Bảng giá chi tiết
        document.getElementById("modalPackagePrice").textContent = formatMoneyVal(order.unitPrice);
        document.getElementById("modalDiscountAmount").textContent = "-" + formatMoneyVal(order.discountAmount);
        document.getElementById("modalPreTaxAmount").textContent = formatMoneyVal(order.preTaxAmount);
        document.getElementById("modalVatAmount").textContent = `${formatMoneyVal(order.vatAmount)} (${order.vatRate}%)`;
        document.getElementById("modalTotalAmount").textContent = formatMoneyVal(order.totalPayable);

        // Lịch sử các lần giao dịch / thanh toán
        const attemptsBody = document.getElementById("modalAttemptsBody");
        attemptsBody.innerHTML = "";

        if (attempts.length === 0) {
            attemptsBody.innerHTML = `<tr><td colspan="7" class="text-center" style="padding: 16px; color: var(--color-text-soft);">Không có giao dịch thanh toán nào được ghi nhận.</td></tr>`;
        } else {
            attempts.forEach(att => {
                const tr = document.createElement("tr");

                let methodBadge = "";
                if (att.paymentMethod === "Cash") {
                    methodBadge = `<span class="badge-method badge-method-cash">Tiền mặt</span>`;
                } else if (att.paymentMethod === "Transfer") {
                    methodBadge = `<span class="badge-method badge-method-transfer">Chuyển khoản</span>`;
                } else {
                    methodBadge = `<span class="badge-method badge-method-vnpay">VNPay</span>`;
                }

                let statusBadge = "";
                if (att.status === "Success") {
                    statusBadge = `<span class="badge badge-success">Thành công</span>`;
                } else if (att.status === "Pending") {
                    statusBadge = `<span class="badge badge-warning">Đang chờ</span>`;
                } else if (att.status === "Failed") {
                    statusBadge = `<span class="badge badge-danger">Thất bại</span>`;
                } else {
                    statusBadge = `<span class="badge badge-neutral">Hết hạn</span>`;
                }

                let refundInfo = "---";
                if (att.needsRefund) {
                    if (att.refundedAt) {
                        const refEmp = att.refundedByEmployeeName || "Quản lý";
                        refundInfo = `<span class="badge-refund-tag completed" title="Ghi chú: ${escapeHtmlVal(att.refundNote || '')}">Đã hoàn bởi ${escapeHtmlVal(refEmp)} (${formatDateTimeVal(att.refundedAt)})</span>`;
                    } else {
                        refundInfo = `<button type="button" class="btn btn-sm btn-danger btn-trigger-refund"
                                        data-transaction-id="${att.transactionId}"
                                        data-order-code="${escapeHtmlVal(order.orderCode)}"
                                        data-amount="${att.amount}"
                                        data-customer="${escapeHtmlVal(order.customerName || '')}">
                                        Xác nhận hoàn
                                      </button>`;
                    }
                }

                tr.innerHTML = `
                    <td>#${att.transactionId}</td>
                    <td>${methodBadge}</td>
                    <td>${statusBadge}</td>
                    <td class="money font-weight-bold">${formatMoneyVal(att.amount)}</td>
                    <td>${escapeHtmlVal(att.responseCode || att.transactionReference || '---')}</td>
                    <td>${formatDateTimeVal(att.createdAt)}</td>
                    <td>${refundInfo}</td>
                `;
                attemptsBody.appendChild(tr);
            });

            // Gán lại sự kiện cho các nút xác nhận hoàn tiền mới sinh
            attemptsBody.querySelectorAll(".btn-trigger-refund").forEach(rBtn => {
                rBtn.addEventListener("click", function () {
                    closeModal(detailModal);
                    openRefundModalFromButton(this);
                });
            });
        }
    }

    // ===== 2. MODAL XÁC NHẬN HOÀN TIỀN NGOÀI HỆ THỐNG (BR-47) =====
    const refundModal = document.getElementById("refundConfirmModal");
    const refundForm = document.getElementById("refundConfirmForm");

    function openRefundModalFromButton(button) {
        const transId = button.dataset.transactionId;
        const orderCode = button.dataset.orderCode || "---";
        const amount = button.dataset.amount || "0";
        const customer = button.dataset.customer || "Khách hàng";

        document.getElementById("refundTransactionIdInput").value = transId;
        document.getElementById("refundOrderCodeText").textContent = orderCode;
        document.getElementById("refundAmountText").textContent = formatMoneyVal(amount);
        document.getElementById("refundCustomerText").textContent = customer;
        document.getElementById("refundNoteInput").value = "";

        const errorEl = document.getElementById("refundNoteError");
        if (errorEl) {
            errorEl.hidden = true;
            errorEl.textContent = "";
        }

        openModal(refundModal);
    }

    // Gán nút hoàn tiền ngoài bảng chính
    document.querySelectorAll(".btn-table-refund").forEach(btn => {
        btn.addEventListener("click", function () {
            openRefundModalFromButton(this);
        });
    });

    if (refundForm) {
        refundForm.addEventListener("submit", function (e) {
            e.preventDefault();

            const transId = document.getElementById("refundTransactionIdInput").value;
            const noteInput = document.getElementById("refundNoteInput");
            const note = (noteInput.value || "").trim();
            const errorEl = document.getElementById("refundNoteError");

            if (!note) {
                if (errorEl) {
                    errorEl.hidden = false;
                    errorEl.textContent = "Vui lòng nhập lý do / ghi chú hoàn tiền (bắt buộc).";
                }
                noteInput.focus();
                return;
            }

            if (note.length > 500) {
                if (errorEl) {
                    errorEl.hidden = false;
                    errorEl.textContent = "Ghi chú không được vượt quá 500 ký tự.";
                }
                noteInput.focus();
                return;
            }

            if (errorEl) errorEl.hidden = true;

            const submitBtn = refundForm.querySelector("button[type='submit']");
            if (submitBtn) submitBtn.disabled = true;

            const params = new URLSearchParams();
            params.append("action", "refund");
            params.append("transactionId", transId);
            params.append("refundNote", note);

            fetch(`${contextPath}/manager/transactions`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                    "X-Requested-With": "XMLHttpRequest"
                },
                body: params.toString()
            })
            .then(res => res.json())
            .then(res => {
                if (submitBtn) submitBtn.disabled = false;
                if (res.success) {
                    closeModal(refundModal);
                    if (typeof showToast === "function") {
                        showToast("success", res.data || "Đã xác nhận hoàn tiền thành công.");
                    }
                    // Tải lại trang sau 800ms để cập nhật bảng và badge counter
                    setTimeout(() => {
                        window.location.reload();
                    }, 800);
                } else {
                    if (errorEl) {
                        errorEl.hidden = false;
                        errorEl.textContent = res.message || "Lỗi xử lý hoàn tiền.";
                    }
                }
            })
            .catch(err => {
                if (submitBtn) submitBtn.disabled = false;
                console.error("Lỗi gửi xác nhận hoàn tiền:", err);
                if (errorEl) {
                    errorEl.hidden = false;
                    errorEl.textContent = "Lỗi kết nối máy chủ. Vui lòng thử lại sau.";
                }
            });
        });
    }

    // ===== 3. TIỆN ÍCH ĐỊNH DẠNG & FORM LỌC =====
    function formatMoneyVal(val) {
        if (typeof formatMoney === "function") {
            return formatMoney(val);
        }
        const num = Number(val);
        if (isNaN(num)) return "0đ";
        return Math.round(num).toLocaleString("vi-VN") + "đ";
    }

    function formatDateTimeVal(val) {
        if (!val) return "---";
        if (typeof formatDateTime === "function") {
            return formatDateTime(val);
        }
        // yyyy-MM-ddTHH:mm:ss -> dd/MM/yyyy HH:mm
        const parts = String(val).split("T");
        if (parts.length === 2) {
            const dateParts = parts[0].split("-");
            const timeParts = parts[1].split(":");
            if (dateParts.length === 3 && timeParts.length >= 2) {
                return `${dateParts[2]}/${dateParts[1]}/${dateParts[0]} ${timeParts[0]}:${timeParts[1]}`;
            }
        }
        return val;
    }

    function escapeHtmlVal(str) {
        if (!str) return "";
        if (typeof escapeHtml === "function") {
            return escapeHtml(str);
        }
        return String(str)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }

    // Kiểm tra khoảng ngày bộ lọc
    const filterForms = document.querySelectorAll(".trans-filter-form");
    filterForms.forEach(form => {
        const fromInput = form.querySelector("input[name='from']");
        const toInput = form.querySelector("input[name='to']");

        if (fromInput && toInput) {
            form.addEventListener("submit", function (e) {
                const fromVal = fromInput.value;
                const toVal = toInput.value;
                if (fromVal && toVal && fromVal > toVal) {
                    e.preventDefault();
                    if (typeof showToast === "function") {
                        showToast("error", "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
                    } else {
                        alert("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.");
                    }
                }
            });
        }
    });

    // Checkbox lọc nhanh hoàn tiền tự submit form
    const needsRefundCb = document.getElementById("filterNeedsRefundCb");
    if (needsRefundCb) {
        needsRefundCb.addEventListener("change", function () {
            this.form.submit();
        });
    }
});
