/**
 * Client script cho màn hình Check-in Lễ tân (UC-19).
 */
document.addEventListener('DOMContentLoaded', function () {
    const ciInput = document.getElementById('ciq');

    // Xử lý Modal chụp ảnh tại quầy
    const photoModal = document.getElementById('photoModal');
    const openPhotoBtns = document.querySelectorAll('[data-ci-photo]');
    const closePhotoBtns = document.querySelectorAll('[data-close-modal]');
    const captureBtn = document.getElementById('captureBtn');
    const cameraVideo = document.getElementById('cameraVideo');
    const cameraCanvas = document.getElementById('cameraCanvas');
    const photoFileInput = document.getElementById('photoFileInput');
    let videoStream = null;

    function openModal() {
        if (!photoModal) return;
        photoModal.classList.add('open');
        startCamera();
    }

    function closeModal() {
        if (!photoModal) return;
        photoModal.classList.remove('open');
        stopCamera();
    }

    openPhotoBtns.forEach(btn => {
        btn.addEventListener('click', openModal);
    });

    closePhotoBtns.forEach(btn => {
        btn.addEventListener('click', closeModal);
    });

    if (photoModal) {
        photoModal.addEventListener('click', function (e) {
            if (e.target === photoModal) {
                closeModal();
            }
        });
    }

    function startCamera() {
        if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
            navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480 } })
                .then(stream => {
                    videoStream = stream;
                    if (cameraVideo) {
                        cameraVideo.srcObject = stream;
                        cameraVideo.style.display = 'block';
                        if (cameraCanvas) cameraCanvas.style.display = 'none';
                        if (captureBtn) captureBtn.disabled = false;
                    }
                })
                .catch(err => {
                    console.warn('Không thể truy cập camera:', err);
                    if (cameraVideo) cameraVideo.style.display = 'none';
                });
        }
    }

    function stopCamera() {
        if (videoStream) {
            videoStream.getTracks().forEach(track => track.stop());
            videoStream = null;
        }
    }

    if (captureBtn && cameraCanvas && cameraVideo) {
        captureBtn.addEventListener('click', function () {
            if (!videoStream) return;
            const ctx = cameraCanvas.getContext('2d');
            cameraCanvas.width = cameraVideo.videoWidth || 640;
            cameraCanvas.height = cameraVideo.videoHeight || 480;
            ctx.drawImage(cameraVideo, 0, 0, cameraCanvas.width, cameraCanvas.height);

            cameraCanvas.style.display = 'block';
            cameraVideo.style.display = 'none';

            // Chuyển canvas sang Blob rồi gắn vào FileInput để submit form
            cameraCanvas.toBlob(blob => {
                if (blob) {
                    const file = new File([blob], 'captured-checkin.jpg', { type: 'image/jpeg' });
                    const container = new DataTransfer();
                    container.items.add(file);
                    if (photoFileInput) {
                        photoFileInput.files = container.files;
                    }
                }
            }, 'image/jpeg', 0.9);
        });
    }

    if (ciInput && !ciInput.value) {
        ciInput.focus();
    }
});
