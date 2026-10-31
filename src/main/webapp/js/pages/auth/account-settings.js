/**
 * Account Settings - Profile Management, Avatar & OTP Countdown
 * Supports:
 * - Avatar 3-button controls: Chọn ảnh, Lưu ảnh, Xóa ảnh
 * - Client-side validation: <= 5 MB, JPG/PNG, non-empty (>0 bytes)
 * - Safe preview & cancel/reset on clear selection
 * - Safe confirmation for Remove Avatar
 * - Anti-double submission protection
 * - Live synchronization with topbar avatar & 2-letter fallback
 * - Real-time OTP Countdown (MM:SS) resilient to tab switches, clock skew, and page refreshes
 * - Resend OTP cooldown timer
 */

document.addEventListener("DOMContentLoaded", function () {
    initAvatarManagement();
    initOtpCountdown();
});

function initAvatarManagement() {
    const avatarFile = document.getElementById("avatarFile");
    const avatarPreview = document.getElementById("avatarPreview");
    const avatarFallback = document.getElementById("avatarFallback");
    const avatarError = document.getElementById("avatarClientError");
    const btnSelectAvatar = document.getElementById("btnSelectAvatar");
    const btnSaveAvatar = document.getElementById("btnSaveAvatar");
    const btnRemoveAvatar = document.getElementById("btnRemoveAvatar");
    const avatarForm = document.getElementById("avatarForm");
    const removeAvatarForm = document.getElementById("removeAvatarForm");

    let topbarImg = document.getElementById("topbarAvatarImg");
    const topbarFallback = document.getElementById("topbarAvatarFallback");

    if (!avatarFile || !avatarPreview) {
        return;
    }

    const originalSrc = avatarPreview.getAttribute("data-original-src") || "";
    const originalHasAvatar = Boolean(originalSrc);
    let currentObjectUrl = null;

    function wireImageFallback(imgEl, fallbackEl) {
        if (!imgEl) return;
        imgEl.addEventListener("error", function () {
            imgEl.classList.add("is-hidden");
            imgEl.style.display = "none";
            if (fallbackEl) {
                fallbackEl.classList.remove("is-hidden");
                fallbackEl.style.display = "";
            }
        });
        imgEl.addEventListener("load", function () {
            if (imgEl.getAttribute("src") && !imgEl.classList.contains("is-hidden")) {
                imgEl.classList.remove("is-hidden");
                imgEl.style.display = "";
                if (fallbackEl) {
                    fallbackEl.classList.add("is-hidden");
                    fallbackEl.style.display = "none";
                }
            }
        });
    }

    wireImageFallback(avatarPreview, avatarFallback);
    wireImageFallback(topbarImg, topbarFallback);

    function showAvatarError(message) {
        if (!avatarError) return;
        avatarError.textContent = message || "";
        avatarError.classList.toggle("is-hidden", !message);
    }

    function syncTopbarAvatar(previewSrc) {
        topbarImg = document.getElementById("topbarAvatarImg");
        const tbFallback = document.getElementById("topbarAvatarFallback");

        if (previewSrc) {
            if (!topbarImg && tbFallback && tbFallback.parentNode) {
                topbarImg = document.createElement("img");
                topbarImg.id = "topbarAvatarImg";
                topbarImg.className = "avatar";
                topbarImg.alt = "Ảnh đại diện";
                wireImageFallback(topbarImg, tbFallback);
                tbFallback.parentNode.insertBefore(topbarImg, tbFallback);
            }
            if (topbarImg) {
                topbarImg.src = previewSrc;
                topbarImg.classList.remove("is-hidden");
                topbarImg.style.display = "";
            }
            if (tbFallback) {
                tbFallback.classList.add("is-hidden");
                tbFallback.style.display = "none";
            }
        } else {
            if (topbarImg) {
                topbarImg.classList.add("is-hidden");
                topbarImg.style.display = "none";
                topbarImg.removeAttribute("src");
            }
            if (tbFallback) {
                tbFallback.classList.remove("is-hidden");
                tbFallback.style.display = "";
            }
        }
    }

    function resetToOriginal() {
        if (currentObjectUrl) {
            URL.revokeObjectURL(currentObjectUrl);
            currentObjectUrl = null;
        }
        avatarFile.value = "";
        if (originalHasAvatar) {
            avatarPreview.src = originalSrc;
            avatarPreview.classList.remove("is-hidden");
            avatarPreview.style.display = "";
            if (avatarFallback) {
                avatarFallback.classList.add("is-hidden");
                avatarFallback.style.display = "none";
            }
            syncTopbarAvatar(originalSrc);
        } else {
            avatarPreview.src = "";
            avatarPreview.classList.add("is-hidden");
            avatarPreview.style.display = "none";
            if (avatarFallback) {
                avatarFallback.classList.remove("is-hidden");
                avatarFallback.style.display = "";
            }
            syncTopbarAvatar(null);
        }
        if (btnSaveAvatar) {
            btnSaveAvatar.disabled = true;
        }
    }

    if (btnSelectAvatar) {
        btnSelectAvatar.addEventListener("click", function () {
            avatarFile.click();
        });
    }

    avatarFile.addEventListener("change", function () {
        showAvatarError("");

        const file = avatarFile.files && avatarFile.files[0];
        if (!file) {
            resetToOriginal();
            return;
        }

        // Validate 0 bytes
        if (file.size === 0) {
            showAvatarError("Tệp ảnh không hợp lệ hoặc rỗng (0 bytes).");
            resetToOriginal();
            return;
        }

        // Validate max bytes (unified 5 MB = 5242880 bytes)
        const maxBytes = Number(avatarFile.dataset.maxBytes) || 5 * 1024 * 1024;
        if (file.size > maxBytes) {
            showAvatarError("Ảnh đại diện vượt quá kích thước cho phép (tối đa 5 MB).");
            resetToOriginal();
            return;
        }

        // Validate MIME type & file extension
        const allowedMimes = ["image/jpeg", "image/png"];
        const allowedExts = [".jpg", ".jpeg", ".png"];
        const fileName = (file.name || "").toLowerCase();
        const hasValidExt = allowedExts.some(function (ext) {
            return fileName.endsWith(ext);
        });
        const hasValidMime = allowedMimes.includes(file.type);

        if (!hasValidMime || !hasValidExt) {
            showAvatarError("Chỉ chấp nhận ảnh định dạng JPG hoặc PNG.");
            resetToOriginal();
            return;
        }

        // Valid: Preview image without uploading yet
        if (currentObjectUrl) {
            URL.revokeObjectURL(currentObjectUrl);
        }
        currentObjectUrl = URL.createObjectURL(file);
        avatarPreview.src = currentObjectUrl;
        avatarPreview.classList.remove("is-hidden");
        avatarPreview.style.display = "";
        if (avatarFallback) {
            avatarFallback.classList.add("is-hidden");
            avatarFallback.style.display = "none";
        }

        // Synchronize Topbar avatar with preview
        syncTopbarAvatar(currentObjectUrl);

        // Enable "Lưu ảnh" button
        if (btnSaveAvatar) {
            btnSaveAvatar.disabled = false;
        }
    });

    if (avatarForm) {
        avatarForm.addEventListener("submit", function (e) {
            const file = avatarFile.files && avatarFile.files[0];
            if (!file) {
                e.preventDefault();
                showAvatarError("Vui lòng chọn ảnh trước khi lưu.");
                return;
            }
            if (btnSaveAvatar) {
                btnSaveAvatar.disabled = true;
                btnSaveAvatar.textContent = "Đang lưu...";
            }
            if (btnSelectAvatar) {
                btnSelectAvatar.disabled = true;
            }
        });
    }

    if (btnRemoveAvatar && removeAvatarForm) {
        btnRemoveAvatar.addEventListener("click", function () {
            if (typeof openConfirmDialog === "function") {
                openConfirmDialog({
                    title: "Xóa ảnh đại diện",
                    message: "Bạn có chắc chắn muốn xóa ảnh đại diện hiện tại?",
                    confirmText: "Xóa ảnh",
                    cancelText: "Hủy",
                    isDanger: true,
                    onConfirm: function () {
                        btnRemoveAvatar.disabled = true;
                        btnRemoveAvatar.textContent = "Đang xóa...";
                        removeAvatarForm.submit();
                    }
                });
            } else if (window.confirm("Bạn có chắc chắn muốn xóa ảnh đại diện hiện tại?")) {
                btnRemoveAvatar.disabled = true;
                btnRemoveAvatar.textContent = "Đang xóa...";
                removeAvatarForm.submit();
            }
        });
    }
}

function initOtpCountdown() {
    const otpContainers = document.querySelectorAll(".account-profile-verification[data-otp-container]");
    if (!otpContainers.length) {
        return;
    }

    otpContainers.forEach(function (container) {
        const serverTime = Number(container.dataset.serverTime);
        const expiresAt = Number(container.dataset.expiresAt);
        const resendAllowedAt = Number(container.dataset.resendAllowedAt);

        if (isNaN(expiresAt) || expiresAt <= 0) {
            return;
        }

        // Calculate time skew between server time and client device time
        const clientMountTime = Date.now();
        const skewOffset = !isNaN(serverTime) && serverTime > 0 ? (serverTime - clientMountTime) : 0;

        function getEstimatedServerNow() {
            return Date.now() + skewOffset;
        }

        const timerBadge = container.querySelector(".countdown-badge");
        const expiredAlert = container.querySelector("[id$='ExpiredAlert']");
        const verifyBtn = container.querySelector("button[id^='btnVerify']");
        const resendBtn = container.querySelector("button[id^='btnResend']");
        const verifyForm = container.querySelector("form[id^='verify']");
        const resendForm = container.querySelector("form[id^='resend']");

        function formatMmSs(totalSeconds) {
            const m = Math.floor(totalSeconds / 60);
            const s = totalSeconds % 60;
            return String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0");
        }

        function updateCountdown() {
            const now = getEstimatedServerNow();
            const remainingSec = Math.max(0, Math.floor((expiresAt - now) / 1000));

            if (timerBadge) {
                timerBadge.textContent = formatMmSs(remainingSec);
            }

            if (remainingSec <= 0) {
                if (expiredAlert) {
                    expiredAlert.classList.remove("is-hidden");
                }
                if (verifyBtn && !verifyBtn.dataset.submitting) {
                    verifyBtn.disabled = true;
                    verifyBtn.title = "Mã xác thực đã hết hạn.";
                }
            } else {
                if (expiredAlert) {
                    expiredAlert.classList.add("is-hidden");
                }
                if (verifyBtn && !verifyBtn.dataset.submitting) {
                    verifyBtn.disabled = false;
                    verifyBtn.removeAttribute("title");
                }
            }

            // Resend cooldown handling
            if (resendBtn && !isNaN(resendAllowedAt) && resendAllowedAt > 0) {
                const resendRemainingSec = Math.max(0, Math.floor((resendAllowedAt - now) / 1000));
                if (resendRemainingSec > 0) {
                    resendBtn.disabled = true;
                    resendBtn.textContent = "Gửi lại mã (" + resendRemainingSec + "s)";
                } else {
                    if (!resendBtn.dataset.submitting) {
                        resendBtn.disabled = false;
                        resendBtn.textContent = "Gửi lại mã";
                    }
                }
            }
        }

        // Initial run
        updateCountdown();

        // High precision interval (500ms ensures smooth second transitions without drift)
        const timerId = setInterval(updateCountdown, 500);

        // Instant refresh when user switches tabs or window regains focus
        document.addEventListener("visibilitychange", function () {
            if (document.visibilityState === "visible") {
                updateCountdown();
            }
        });
        window.addEventListener("pageshow", function () {
            updateCountdown();
        });

        // Anti-double submission
        if (verifyForm && verifyBtn) {
            verifyForm.addEventListener("submit", function () {
                verifyBtn.disabled = true;
                verifyBtn.dataset.submitting = "true";
                verifyBtn.textContent = "Đang xác nhận...";
            });
        }

        if (resendForm && resendBtn) {
            resendForm.addEventListener("submit", function () {
                resendBtn.disabled = true;
                resendBtn.dataset.submitting = "true";
                resendBtn.textContent = "Đang gửi mã...";
            });
        }
    });
}