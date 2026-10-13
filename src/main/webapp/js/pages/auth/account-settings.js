document.addEventListener("DOMContentLoaded", function () {

    // Sidebar dock mở/đóng đã chuyển sang common.js (initDock) — dùng chung mọi khu

    const avatarFile =
        document.getElementById("avatarFile");
    const avatarPreview =
        document.getElementById("avatarPreview");
    const avatarFallback =
        document.getElementById("avatarFallback");
    const avatarError =
        document.getElementById("avatarClientError");

    function showAvatarError(message) {
        if (!avatarError) {
            return;
        }
        avatarError.textContent = message;
        avatarError.classList.toggle("is-hidden", !message);
    }

    if (avatarFile && avatarPreview) {

        function handleAvatarChange() {

            showAvatarError("");

            const file = avatarFile.files[0];

            if (!file) {
                return;
            }

            const allowedTypes = ["image/jpeg", "image/png"];
            const maxBytes =
                Number(avatarFile.dataset.maxBytes) || 2 * 1024 * 1024;

            if (!allowedTypes.includes(file.type)) {
                avatarFile.value = "";
                showAvatarError("Chỉ nhận ảnh JPG hoặc PNG.");
                return;
            }

            if (file.size > maxBytes) {
                avatarFile.value = "";
                showAvatarError("Ảnh tối đa 2 MB.");
                return;
            }

            // Xem trước ngay, rồi tự gửi form (server vẫn kiểm tra lại)
            avatarPreview.src = URL.createObjectURL(file);
            avatarPreview.classList.remove("is-hidden");
            if (avatarFallback) {
                avatarFallback.classList.add("is-hidden");
            }

            avatarFile.form.submit();
        }

        avatarFile.addEventListener("change", handleAvatarChange);
    }
});