// ===== PT Dashboard (/pt/dashboard) =====
// Thẻ thời tiết chạy hoàn toàn bằng CSS. File này lo 3 việc:
//   1. Nút mở / đóng sidebar dock (trên điện thoại không có hover)
//   2. Ô "Từ ngày": đổi ngày là tải lại ngay; để trống thì báo lỗi, không gửi (ô bắt buộc — SRS 9.1.2).
//      Server vẫn kiểm lại (GBR-13), đây chỉ là báo sớm cho người dùng.
//   3. Lọc tại chỗ danh sách "Buổi sắp tới" theo tên khách / gói / trạng thái — không tải lại trang (giống prototype)
// Ô "Kỳ" và "Trong" tự gửi form nhờ data-auto-submit (common.js).

document.addEventListener("DOMContentLoaded", function () {
    bindDock();
    bindDateFilter();
    bindUpcomingFilter();
});

// Gắn nút tay nắm và phím Esc cho sidebar dock
function bindDock() {
    const dock = document.getElementById("dock");
    const dockHandle = document.getElementById("dockHandle");
    if (dock === null || dockHandle === null) {
        return;
    }

    // Bấm tay nắm → mở / đóng sidebar
    dockHandle.addEventListener("click", function () {
        setDockOpen(dock, dockHandle, !dock.classList.contains("is-open"));
    });

    // Esc → đóng sidebar nếu đang mở
    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && dock.classList.contains("is-open")) {
            setDockOpen(dock, dockHandle, false);
        }
    });
}

// Mở hoặc đóng sidebar, cập nhật thuộc tính cho trình đọc màn hình
// Input: dock = khối #dock, handle = nút tay nắm, isOpen = true để mở / false để đóng
function setDockOpen(dock, handle, isOpen) {
    dock.classList.toggle("is-open", isOpen);
    handle.setAttribute("aria-expanded", String(isOpen));
    handle.setAttribute("aria-label", isOpen ? "Đóng menu điều hướng" : "Mở menu điều hướng");
}

// Ô "Từ ngày" của khối Buổi sắp tới
// Luồng xử lý:
// B1: đổi ngày (chọn trên lịch hoặc gõ xong) → gửi form ngay
// B2: gửi khi ô trống (VD xóa hết rồi bấm Enter) → chặn gửi, hiện "Ngày là bắt buộc." dưới ô
function bindDateFilter() {
    const form = document.getElementById("upcomingFilter");
    const input = document.getElementById("datePick");
    const error = document.getElementById("dateError");
    if (form === null || input === null || error === null) {
        return;
    }

    // B1
    input.addEventListener("change", function () {
        if (input.value !== "") {
            form.requestSubmit();
        }
    });

    // B2
    form.addEventListener("submit", function (event) {
        if (input.value !== "") {
            return;
        }
        event.preventDefault();
        error.textContent = formatMessage(MESSAGES.MSG09_REQUIRED, ["Ngày"]);
        error.hidden = false;
        input.classList.add("invalid");
        input.focus();
    });
}

// Lọc tại chỗ danh sách Buổi sắp tới (dữ liệu đã có sẵn trong trang, chỉ ẩn / hiện dòng)
// Luồng xử lý:
// B1: đọc 3 ô lọc: tên khách (bỏ dấu, không phân biệt hoa thường), gói, trạng thái
// B2: dòng nào khớp cả 3 thì hiện, còn lại ẩn
// B3: không còn dòng nào → hiện "Không có buổi nào khớp bộ lọc."
function bindUpcomingFilter() {
    const list = document.getElementById("upList");
    const search = document.getElementById("upSearch");
    const pkg = document.getElementById("upPkg");
    const status = document.getElementById("upStatus");
    if (list === null || search === null || pkg === null || status === null) {
        return;
    }
    const rows = list.querySelectorAll(".data-row");
    const empty = list.querySelector("[data-empty]");

    function render() {
        // B1
        const keyword = toSearchKey(search.value);
        let shownCount = 0;

        // B2
        for (const row of rows) {
            const isMatch = toSearchKey(row.dataset.q).includes(keyword)
                    && (pkg.value === "all" || row.dataset.pkg === pkg.value)
                    && (status.value === "all" || row.dataset.status === status.value);
            row.hidden = !isMatch;
            if (isMatch) {
                shownCount++;
            }
        }

        // B3
        empty.hidden = shownCount > 0;
    }

    search.addEventListener("input", render);
    pkg.addEventListener("change", render);
    status.addEventListener("change", render);
}

// Chuẩn hóa chuỗi để tìm kiếm: bỏ dấu tiếng Việt, đổi đ → d, chữ thường, bỏ khoảng trắng 2 đầu
// VD "Nguyễn Thị Hạnh" → "nguyen thi hanh" → gõ "hanh" hay "Hạnh" đều tìm được
function toSearchKey(text) {
    return (text || "")
            .normalize("NFD")
            .replace(/[̀-ͯ]/g, "")
            .replace(/đ/g, "d")
            .replace(/Đ/g, "D")
            .toLowerCase()
            .trim();
}
