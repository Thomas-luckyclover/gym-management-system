// =====================================================================
// blog-detail.js — Kịch bản tương tác cho trang Chi tiết bài viết Blog (SRS #7 / UC-06)
// Tuân thủ nghiêm ngặt codeImplementationFrontend.md:
// - 4 spaces, K&R style, 1 dòng = 1 logic.
// - Viết từ trên xuống dưới theo thứ tự thực thi.
// - Khung chương trình chính và khung chương trình từng bước (// B1, // B2...).
// - Không method chaining dài, cache phần tử DOM vào biến.
// =====================================================================

// ===== 1. Cấu hình & Biến toàn cục (đặt ở đầu file) =====
// Ngưỡng cuộn tối thiểu (pixel) để bắt đầu tính toán thanh tiến trình đọc
const SCROLL_THRESHOLD_PX = 10;

// ===== 2. Xử lý thanh tiến trình đọc bài viết (Reading Progress) =====

/**
 * Cập nhật thanh tiến trình đọc bài viết dựa trên vị trí cuộn trang.
 * Luồng xử lý:
 * B1: lấy phần tử thanh tiến trình #readingProgressBar
 * B2: tính toán khoảng cách đã cuộn và tổng chiều cao có thể cuộn của trang
 * B3: tính tỉ lệ phần trăm đọc (0% đến 100%)
 * B4: gán giá trị độ rộng CSS width vào #readingProgressBar
 */
function updateReadingProgress() {
    // B1: lấy phần tử thanh tiến trình
    const progressBar = document.getElementById("readingProgressBar");
    if (!progressBar) {
        return;
    }

    // B2: tính khoảng cách cuộn hiện tại và chiều cao cuộn khả dụng
    const scrollTop = window.scrollY || document.documentElement.scrollTop;
    const documentHeight = document.documentElement.scrollHeight;
    const clientHeight = document.documentElement.clientHeight;
    const scrollableDistance = documentHeight - clientHeight;

    // B3: tính tỉ lệ đọc phần trăm
    let progressPercent = 0;
    if (scrollableDistance > 0) {
        progressPercent = Math.min(100, Math.max(0, (scrollTop / scrollableDistance) * 100));
    }

    // B4: cập nhật độ rộng thanh tiến trình
    progressBar.style.width = progressPercent + "%";
}

// ===== 3. Xử lý sao chép liên kết chia sẻ bài viết (Share Link) =====

/**
 * Xử lý sự kiện bấm nút sao chép liên kết bài viết #btnShareArticle.
 * Luồng xử lý:
 * B1: lấy URL hiện tại của bài viết
 * B2: sao chép vào bộ nhớ tạm (Clipboard API)
 * B3: hiển thị thông báo toast thành công (sử dụng showToast từ common.js)
 */
async function handleShareArticle() {
    // B1: lấy đường dẫn liên kết bài viết hiện tại
    const currentUrl = window.location.href;

    try {
        // B2: sao chép vào clipboard
        if (navigator.clipboard && navigator.clipboard.writeText) {
            await navigator.clipboard.writeText(currentUrl);
        } else {
            // Fallback cho trình duyệt cũ
            const tempInput = document.createElement("textarea");
            tempInput.value = currentUrl;
            tempInput.style.position = "fixed";
            tempInput.style.left = "-9999px";
            document.body.appendChild(tempInput);
            tempInput.select();
            document.execCommand("copy");
            document.body.removeChild(tempInput);
        }

        // B3: hiển thị toast thông báo thành công
        if (typeof showToast === "function") {
            showToast("success", "Đã sao chép liên kết bài viết vào bộ nhớ tạm!");
        } else {
            alert("Đã sao chép liên kết bài viết!");
        }
    } catch (err) {
        if (typeof showToast === "function") {
            showToast("error", "Không thể sao chép liên kết, vui lòng thử lại.");
        }
    }
}

// ===== 4. Xử lý nút cuộn lên đầu trang (Scroll To Top) =====

/**
 * Cuộn trang mượt mà lên đầu khi bấm nút #btnScrollToTop.
 * Luồng xử lý:
 * B1: gọi window.scrollTo với behavior: 'smooth'
 */
function handleScrollToTop() {
    // B1: cuộn mượt về tọa độ y = 0
    window.scrollTo({
        top: 0,
        behavior: "smooth"
    });
}

// ===== 5. Khởi tạo & Gắn sự kiện khi DOM sẵn sàng =====

/**
 * Gắn toàn bộ sự kiện cho trang chi tiết bài viết Blog.
 * Luồng xử lý:
 * B1: lắng nghe sự kiện scroll để cập nhật thanh tiến trình đọc
 * B2: gắn sự kiện click cho nút sao chép chia sẻ #btnShareArticle
 * B3: gắn sự kiện click cho nút cuộn lên đầu #btnScrollToTop
 */
function initBlogDetailPage() {
    // B1: lắng nghe sự kiện cuộn trang
    window.addEventListener("scroll", updateReadingProgress, { passive: true });
    updateReadingProgress();

    // B2: gắn sự kiện cho nút chia sẻ liên kết
    const btnShare = document.getElementById("btnShareArticle");
    if (btnShare) {
        btnShare.addEventListener("click", handleShareArticle);
    }

    // B3: gắn sự kiện cho nút cuộn lên đầu trang
    const btnScrollTop = document.getElementById("btnScrollToTop");
    if (btnScrollTop) {
        btnScrollTop.addEventListener("click", handleScrollToTop);
    }
}

// Đảm bảo chạy khởi tạo sau khi DOM đã được nạp xong
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initBlogDetailPage);
} else {
    initBlogDetailPage();
}
