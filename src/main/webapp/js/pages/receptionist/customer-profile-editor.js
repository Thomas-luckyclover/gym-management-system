// Ẩn/hiện thông tin giám hộ và xem trước ảnh; form POST do Servlet xử lý.
(function () {
    "use strict";
    const form = document.getElementById("customerProfileEditor");
    if (!form) return;
    const type = document.getElementById("customerType");
    const contact = document.getElementById("contactSection");
    const guardian = document.getElementById("guardianSection");
    function updateType() {
        const dependent = type.value === "Dependent";
        guardian.hidden = !dependent;
        guardian.disabled = !dependent;
        contact.hidden = dependent;
        contact.disabled = dependent;
        const verification = document.getElementById("identityVerification");
        if (verification) {
            verification.hidden = dependent;
        }
    }
    type.addEventListener("change", updateType);
    updateType();
    const birthDate = document.getElementById("dateOfBirth");
    const today = new Date();
    birthDate.max = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
    const photo = document.getElementById("checkInPhoto");
    const preview = document.getElementById("photoPreview");
    const error = document.getElementById("photoError");
    let previewUrl;
    photo.addEventListener("change", function () {
        if (previewUrl) URL.revokeObjectURL(previewUrl);
        preview.hidden = true;
        preview.removeAttribute("src");
        error.textContent = "";
        const file = photo.files[0];
        if (!file) return;
        if (!["image/jpeg", "image/png"].includes(file.type) || file.size > 5 * 1024 * 1024) {
            error.textContent = "Chọn ảnh JPEG hoặc PNG không quá 5 MB.";
            photo.value = "";
            return;
        }
        previewUrl = URL.createObjectURL(file);
        preview.src = previewUrl;
        preview.hidden = false;
    });
})();
