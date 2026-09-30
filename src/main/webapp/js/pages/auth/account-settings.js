document.addEventListener("DOMContentLoaded", function () {

    const dock = document.getElementById("dock");
    const dockHandle = document.getElementById("dockHandle");

    if (!dock || !dockHandle) {
        return;
    }

    function handleDockToggle() {

        const isOpen =
            dock.classList.toggle("is-open");

        dockHandle.setAttribute(
            "aria-expanded",
            String(isOpen)
        );

        dockHandle.setAttribute(
            "aria-label",
            isOpen
                ? "Đóng menu điều hướng"
                : "Mở menu điều hướng"
        );
    }

    dockHandle.addEventListener("click", handleDockToggle);

    function handleEscapeKeydown(event) {

        if (event.key !== "Escape") {
            return;
        }

        if (!dock.classList.contains("is-open")) {
            return;
        }

        dock.classList.remove("is-open");

        dockHandle.setAttribute(
            "aria-expanded",
            "false"
        );

        dockHandle.setAttribute(
            "aria-label",
            "Mở menu điều hướng"
        );
    }

    document.addEventListener("keydown", handleEscapeKeydown);

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