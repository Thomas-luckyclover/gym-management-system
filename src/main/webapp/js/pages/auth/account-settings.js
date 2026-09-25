document.addEventListener("DOMContentLoaded", function () {

    const dock = document.getElementById("dock");
    const dockHandle = document.getElementById("dockHandle");

    if (!dock || !dockHandle) {
        return;
    }

    dockHandle.addEventListener("click", function () {

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
    });

    document.addEventListener("keydown", function (event) {

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
    });
    const avatarFile =
        document.getElementById("avatarFile");

    const avatarPreview =
        document.getElementById("avatarPreview");

    const avatarFallback =
        document.getElementById("avatarFallback");

    if (avatarFile && avatarPreview) {

        avatarFile.addEventListener("change", function () {

            const file = avatarFile.files[0];

            if (!file) {
                return;
            }

            const allowedTypes = [
                "image/jpeg",
                "image/png"
            ];

            if (!allowedTypes.includes(file.type)) {
                avatarFile.value = "";
                return;
            }

            const previewUrl =
                URL.createObjectURL(file);

            avatarPreview.src = previewUrl;

            avatarPreview.classList.remove("is-hidden");

            if (avatarFallback) {
                avatarFallback.classList.add("is-hidden");
            }
        });
    }
});
